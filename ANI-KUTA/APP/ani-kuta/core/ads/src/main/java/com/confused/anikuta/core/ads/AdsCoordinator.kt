package com.confused.anikuta.core.ads

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The ad-gate state machine — the brain of the ad system.
 *
 * # Lifecycle of one ad-gated navigation
 *
 * 1. Caller calls [requestNavigation] with a `proceed` lambda (e.g. `backstack.add(key)`).
 *    - If ads disabled ([AdsConfig.enabled] = false) OR in cooldown → `proceed()` runs
 *      immediately; the user never sees the interstitial. Done.
 *    - Otherwise → the proceed lambda is held, [state] flips to [AdGateState.AdPending],
 *      and the interstitial renders (collected by the AppRoot overlay).
 * 2. The user taps "Continue" in the interstitial → the UI calls [onUserContinue]
 *    with a Context. The coordinator:
 *    - [AppLifecycleObserver.reset]s the stop-timestamp (so a stale earlier ON_STOP
 *      doesn't inflate the elapsed time).
 *    - Fires `Intent.ACTION_VIEW` on the smart-link URL → the browser opens →
 *      our app goes to background → ProcessLifecycleOwner fires ON_STOP → the
 *      observer records the stop timestamp.
 *    - [state] → [AdGateState.AdInProgress] (retryCount = 0).
 * 3. The user browses + returns to our app → ProcessLifecycleOwner fires ON_START
 *    → [AppLifecycleObserver.onReturnToForeground] emits → the interstitial
 *    collects it → calls [onAppReturnedToForeground]. The coordinator:
 *    - Reads [AppLifecycleObserver.elapsedOutsideMs].
 *    - If `>= [SmartLinkConfig.minTimeOutsideMs]` → [AdsRepository.recordAdShown]
 *      (starts the cooldown) + the held `proceed()` runs (navigation fires) +
 *      [state] → [AdGateState.Idle]. Done — the user is on the details page.
 *    - If `< minTimeOutsideMs` AND `retryCount < [SmartLinkConfig.maxRetries]` →
 *      [state] → [AdGateState.AdTryAgain] (lastElapsedMs, retryCount + 1).
 *      The interstitial shows the "Try again" UI.
 *    - If `retryCount >= maxRetries` (safety cap — don't trap the user forever) →
 *      recordAdShown + proceed + Idle (treat as completed).
 * 4. From [AdGateState.AdTryAgain], the user taps "Try again" → [onTryAgain]
 *    (re-opens the URL → back to step 2's AdInProgress). Loop until success or cap.
 *
 * # Back / cancel
 *
 * The interstitial's Dialog `onDismissRequest` (device back gesture) calls
 * [cancel] → the held `proceed()` is DROPPED (not invoked) + [state] → Idle.
 * The navigation is aborted — the user stays on the previous screen, no ad
 * was counted, no cooldown was set. The user can re-tap the entry later. This
 * is the non-intrusive escape hatch (the user said "make sure that the ad
 * system is robust and it is not that intrusive").
 *
 * # Concurrency
 *
 * The coordinator is a Koin `single` — one instance per process. [state] is a
 * [MutableStateFlow] (thread-safe). All mutations happen on the main thread
 * (Compose recomposition + lifecycle callbacks). Multiple rapid taps: the
 * first sets AdPending; subsequent `requestNavigation` calls while AdPending/
 * AdInProgress would overwrite `pendingProceed` — the coordinator deliberately
 * REJECTS a new request while an ad is active (the interstitial is already on
 * screen; a second tap can't happen visually). [requestNavigation] returns
 * false in that case so the caller can drop the event.
 *
 * # Isolation from the rest of the app
 *
 * The coordinator knows NOTHING about navigation, NavKey, AnimeDetailsKey, the
 * backstack, or any feature module. It only holds a `() -> Unit` proceed
 * callback. The caller (AppRoot) decides what "proceed" means. This keeps
 * `:core:ads` fully decoupled (CORE_RULES §5/§7 + the user's explicit request
 * to keep it separate).
 */
class AdsCoordinator(
    private val repository: AdsRepository,
    private val lifecycleObserver: AppLifecycleObserver,
    /**
     * Task 61 (round 21): the APPLICATION context — for the offline gate
     * (ConnectivityManager). Injected via Koin's `androidContext()`; never
     * used for UI (the interstitial gets an Activity context from its host).
     */
    private val appContext: Context,
) {

    private val _state = MutableStateFlow<AdGateState>(AdGateState.Idle)
    val state: StateFlow<AdGateState> = _state.asStateFlow()

    /** The held proceed-callback for the in-flight navigation. Null when idle. */
    private var pendingProceed: (() -> Unit)? = null

    /**
     * Called by the AppRoot's `navigateToDetails` helper.
     * @param proceed the navigation to gate (e.g. `backstack.add(key)`).
     * @return true if the request was accepted (proceed was invoked OR interstitial
     *   shown); false if rejected (an ad is already in flight — caller should drop
     *   the event so the user isn't double-navigated).
     */
    fun requestNavigation(proceed: () -> Unit): Boolean {
        // An ad is already in flight → reject the new request (the interstitial
        // is already on screen; the user can't tap through it visually).
        if (_state.value !is AdGateState.Idle) {
            Logger.w(TAG) { "requestNavigation rejected — an ad is already in flight (${_state.value::class.simpleName})" }
            return false
        }

        // Ads disabled → proceed immediately, no ad, no cooldown.
        if (!repository.config.enabled) {
            Logger.d(TAG) { "ads disabled — proceeding immediately" }
            proceed()
            return true
        }

        // In cooldown → proceed immediately, no ad shown.
        if (repository.isInCooldown()) {
            Logger.i(TAG) {
                "in cooldown — proceeding without ad (${repository.remainingCooldownMs()}ms remaining)"
            }
            proceed()
            return true
        }

        // Task 61 (round 21 — the offline gate): the ad is DUE but there is no
        // usable network — the smart link NEEDS a browser + the internet, so
        // the popup would only strand the user (a browser error page, then the
        // "Try again" loop). Per the user's spec: "When the user is not
        // connected to the internet, it will not show this pop-up even though
        // the ad time is there… it will wait for the next time the user has
        // internet." So: proceed immediately, and CRUCIALLY do NOT record the
        // ad (no cooldown starts) — it stays due and fires on the next
        // ONLINE gated navigation.
        if (!isOnline()) {
            Logger.i(TAG) { "offline — ad deferred (no usable network); proceeding without ad, no cooldown recorded" }
            proceed()
            return true
        }

        // Need to show an ad → hold the proceed callback + show the interstitial.
        pendingProceed = proceed
        _state.value = AdGateState.AdPending
        Logger.i(TAG) { "ad requested — interstitial shown (AdPending)" }
        return true
    }

    /**
     * User tapped "Continue" in the interstitial. Opens the smart-link URL in
     * the browser + flips to [AdGateState.AdInProgress].
     */
    fun onUserContinue(context: Context) {
        val current = _state.value
        if (current !is AdGateState.AdPending && current !is AdGateState.AdTryAgain) {
            Logger.w(TAG) { "onUserContinue ignored — state is ${current::class.simpleName} (expected AdPending/AdTryAgain)" }
            return
        }
        openSmartLinkUrl(context, retryCount = if (current is AdGateState.AdTryAgain) current.retryCount else 0)
    }

    /**
     * Re-opens the smart-link URL after a "Try again". Same as [onUserContinue]
     * but preserves the incremented retry count.
     */
    fun onTryAgain(context: Context) {
        val current = _state.value
        if (current !is AdGateState.AdTryAgain) {
            Logger.w(TAG) { "onTryAgain ignored — state is ${current::class.simpleName} (expected AdTryAgain)" }
            return
        }
        openSmartLinkUrl(context, retryCount = current.retryCount)
    }

    /**
     * Called by the interstitial when [AppLifecycleObserver.onReturnToForeground]
     * emits (app returned from the browser). Decides ad-completion vs Try-again.
     */
    fun onAppReturnedToForeground() {
        val current = _state.value
        if (current !is AdGateState.AdInProgress) {
            // Not waiting for a return — ignore (e.g. an app background/foreground
            // that wasn't triggered by the ad's browser-open).
            Logger.d(TAG) { "onAppReturnedToForeground ignored — state is ${current::class.simpleName}" }
            return
        }
        val elapsed = lifecycleObserver.elapsedOutsideMs()
        val minTime = repository.config.smartLink.minTimeOutsideMs
        Logger.i(TAG) { "app returned — elapsed outside: ${elapsed}ms (min: ${minTime}ms, attempt ${current.retryCount + 1})" }

        if (elapsed >= minTime) {
            // Ad completed → record + proceed + idle.
            completeAd()
        } else {
            // Too quick → try again (unless over the cap).
            val nextRetry = current.retryCount + 1
            if (nextRetry > repository.config.smartLink.maxRetries) {
                Logger.w(TAG) {
                    "max retries ($nextRetry > ${repository.config.smartLink.maxRetries}) — treating as completed to avoid trapping the user"
                }
                completeAd()
            } else {
                _state.value = AdGateState.AdTryAgain(lastElapsedMs = elapsed, retryCount = nextRetry)
                Logger.i(TAG) { "too quick — show Try Again (attempt $nextRetry of ${repository.config.smartLink.maxRetries})" }
            }
        }
    }

    /**
     * User cancelled (device back on the interstitial). Drops the held
     * proceed-callback → navigation aborted, no ad counted, no cooldown set.
     */
    fun cancel() {
        if (_state.value is AdGateState.Idle) return
        Logger.i(TAG) { "ad cancelled by user — navigation aborted, no cooldown set" }
        pendingProceed = null
        _state.value = AdGateState.Idle
    }

    // ── internals ─────────────────────────────────────────────────────────────

    /**
     * Task 61 (round 21): the honest "has internet" check — an active network
     * with INTERNET capability that the system has VALIDATED (a probe
     * succeeded). A network with INTERNET but no VALIDATION is typically a
     * captive portal / not actually connected — treated as OFFLINE (the smart
     * link would fail there). Any ConnectivityManager lookup failure also
     * reads as offline (never show the popup on a broken system state).
     */
    private fun isOnline(): Boolean {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Opens the smart-link URL in the user's browser + flips to [AdGateState.AdInProgress].
     * Shared by [onUserContinue] (first attempt) + [onTryAgain] (retries).
     */
    private fun openSmartLinkUrl(context: Context, retryCount: Int) {
        val url = repository.config.smartLink.url
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            // Reset the stop-timestamp FIRST so a stale earlier ON_STOP (e.g. the
            // user had backgrounded the app before tapping Continue) doesn't
            // inflate the elapsed time. The browser-open will fire a fresh ON_STOP.
            lifecycleObserver.reset()
            context.startActivity(intent)
            _state.value = AdGateState.AdInProgress(
                startedAt = System.currentTimeMillis(),
                retryCount = retryCount,
            )
            Logger.i(TAG) { "opened smart-link URL (attempt ${retryCount + 1}): $url" }
        } catch (e: ActivityNotFoundException) {
            // No browser app installed — don't trap the user. Record a completion
            // (treat as ad shown so the cooldown still applies — the URL was
            // "presented" even if no browser consumed it) + proceed. Non-intrusive.
            Logger.e(TAG, e) { "no browser app found to open $url — proceeding without ad" }
            completeAd()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "failed to open smart-link URL — proceeding without ad" }
            completeAd()
        }
    }

    /** Records the ad as completed + runs the held proceed-callback + returns to Idle. */
    private fun completeAd() {
        repository.recordAdShown()
        val proceed = pendingProceed
        pendingProceed = null
        _state.value = AdGateState.Idle
        Logger.i(TAG) { "ad completed — proceeding to destination" }
        proceed?.invoke()
    }

    private companion object {
        private const val TAG = "Anikuta:Core:Ads:Coordinator"
    }
}

/**
 * The coordinator's UI-driving state. Observed by the AppRoot interstitial overlay.
 *
 * The flow is: Idle → AdPending → AdInProgress → (AdTryAgain → AdInProgress)* → Idle.
 */
sealed interface AdGateState {
    /** No ad in flight. The interstitial is NOT rendered. */
    data object Idle : AdGateState

    /** The interstitial is shown with a "Continue" button. Waiting for the user. */
    data object AdPending : AdGateState

    /**
     * The user tapped Continue + the browser opened. The app is (or was) in the
     * background. The interstitial shows a "waiting" spinner. The coordinator
     * listens for [AppLifecycleObserver.onReturnToForeground] to advance.
     */
    data class AdInProgress(val startedAt: Long, val retryCount: Int) : AdGateState

    /**
     * The user returned too quickly. The interstitial shows a "Try again" button.
     * [lastElapsedMs] is how long they spent outside (for the "you came back
     * after Xs" message); [retryCount] is the attempt number (1 = first retry).
     */
    data class AdTryAgain(val lastElapsedMs: Long, val retryCount: Int) : AdGateState
}
