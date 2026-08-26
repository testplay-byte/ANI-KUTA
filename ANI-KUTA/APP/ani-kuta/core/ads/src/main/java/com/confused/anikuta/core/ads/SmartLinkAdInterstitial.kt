package com.confused.anikuta.core.ads

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confused.anikuta.core.common.Logger
import org.koin.compose.koinInject

/**
 * The full-screen smart-link ad interstitial overlay.
 *
 * Rendered ONCE from `:app`'s AppRoot (sibling of the existing
 * `UpdateBottomSheet` overlay pattern) — it observes [AdsCoordinator.state] +
 * shows nothing when idle. When active, it's a centered Material3 card on top
 * of the Dialog's dim scrim.
 *
 * # Lifecycle wiring
 *
 * - [DisposableEffect] registers [AppLifecycleObserver] on
 *   [androidx.lifecycle.ProcessLifecycleOwner] while the interstitial is
 *   composed (so ON_STOP/ON_START fire reliably while an ad is in flight),
 *   + unregisters on dispose (so we don't hold the observer forever).
 * - [LaunchedEffect] collects [AppLifecycleObserver.onReturnToForeground]
 *   while [AdGateState.AdInProgress] is the current state + advances the
 *   coordinator when the app returns from the browser.
 *
 * # Why a Dialog (not a screen pushed onto the backstack)
 *
 * The interstitial is NOT a navigation destination — it floats ABOVE whatever
 * screen the user is on (Browse/Library/Search/More/etc.). When the ad
 * completes, the coordinator invokes the held proceed-callback which pushes
 * the AnimeDetailsKey onto the backstack → the Details screen renders under
 * the interstitial → the interstitial dismisses (state → Idle). The user
 * perceives "ad completed → details page appeared." No backstack pollution.
 *
 * # Back = cancel (non-intrusive escape hatch)
 *
 * `onDismissRequest` (device back) calls [AdsCoordinator.cancel] → the held
 * proceed-callback is dropped, navigation aborted, no cooldown set. The user
 * stays on the previous screen + can re-tap the entry later. Per the user's
 * "make sure that the ad system is robust and it is not that intrusive."
 */
@Composable
fun SmartLinkAdInterstitial() {
    val coordinator = koinInject<AdsCoordinator>()
    val repository = koinInject<AdsRepository>()
    val lifecycleObserver = koinInject<AppLifecycleObserver>()
    val state by coordinator.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Register the ProcessLifecycleObserver while the interstitial is active.
    // Unregister on dispose so we don't hold the observer when no ad is in flight.
    DisposableEffect(lifecycleObserver) {
        lifecycleObserver.register()
        onDispose { lifecycleObserver.unregister() }
    }

    // While waiting for the user to return from the browser, listen for the
    // app's return-to-foreground event + advance the state machine.
    LaunchedEffect(state) {
        if (state is AdGateState.AdInProgress) {
            Logger.d("Anikuta:Core:Ads:Interstitial") { "AdInProgress — listening for foreground return" }
            lifecycleObserver.onReturnToForeground.collect {
                Logger.d("Anikuta:Core:Ads:Interstitial") { "foreground return received → onAppReturnedToForeground" }
                coordinator.onAppReturnedToForeground()
            }
        }
    }

    // Idle = no interstitial to render.
    if (state is AdGateState.Idle) return

    Dialog(
        onDismissRequest = { coordinator.cancel() },  // back = cancel (non-intrusive escape)
        properties = DialogProperties(
            usePlatformDefaultWidth = false,   // let our Surface control the width
            dismissOnBackPress = true,         // back cancels the ad-gated navigation
            dismissOnClickOutside = false,     // the user can't tap-out of an ad
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            ) {
                // Crossfade for buttery-smooth state transitions (§22).
                Crossfade(
                    targetState = state,
                    label = "ad-interstitial-state",
                ) { current ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (current) {
                            is AdGateState.AdPending -> AdPendingContent(
                                config = repository.config,
                                onContinue = { coordinator.onUserContinue(context) },
                                onCancel = { coordinator.cancel() },
                            )
                            is AdGateState.AdInProgress -> AdInProgressContent()
                            is AdGateState.AdTryAgain -> AdTryAgainContent(
                                state = current,
                                config = repository.config,
                                onTryAgain = { coordinator.onTryAgain(context) },
                                onCancel = { coordinator.cancel() },
                            )
                            else -> { /* Idle — but we returned early above; defensive. */ }
                        }
                    }
                }
            }
        }
    }
}

// ── Per-state content ──────────────────────────────────────────────────────────

/** The "Continue" state — shown when the ad first appears. */
@Composable
private fun AdPendingContent(
    config: AdsConfig,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    Icon(
        imageVector = Icons.Filled.OpenInNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(40.dp),
    )
    Text(
        text = "Sponsored",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = "Tap continue to support ANI-KUTA. You'll be redirected to our sponsor — come back after a moment to open the details.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Continue")
    }
    TextButton(onClick = onCancel) {
        Text("Not now")
    }
}

/** The "waiting for return" state — spinner while the user is in the browser. */
@Composable
private fun AdInProgressContent() {
    CircularProgressIndicator(
        color = MaterialTheme.colorScheme.primary,
        strokeWidth = 3.dp,
        modifier = Modifier.size(40.dp),
    )
    Text(
        text = "Waiting for you to come back",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = "Browse the sponsor for a moment, then return to ANI-KUTA to continue.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** The "Try again" state — shown when the user returned too quickly. */
@Composable
private fun AdTryAgainContent(
    state: AdGateState.AdTryAgain,
    config: AdsConfig,
    onTryAgain: () -> Unit,
    onCancel: () -> Unit,
) {
    Icon(
        imageVector = Icons.Filled.Refresh,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(40.dp),
    )
    Text(
        text = "Try again",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val secondsSpent = (state.lastElapsedMs / 1000).coerceAtLeast(0)
    Text(
        text = "You came back after ${secondsSpent}s — please stay on the sponsor a little longer. The ad needs at least ${config.smartLink.minTimeOutsideMs / 1000}s outside.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Button(
        onClick = onTryAgain,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Try again")
    }
    TextButton(onClick = onCancel) {
        Text("Cancel")
    }
}
