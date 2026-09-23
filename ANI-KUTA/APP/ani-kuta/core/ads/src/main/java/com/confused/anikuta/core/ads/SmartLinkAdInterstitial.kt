package com.confused.anikuta.core.ads

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Layers
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
 * - D-560: a second observer re-reads the overlay consent on every ON_RESUME
 *   — the user can leave the popup for the system's "Display over other
 *   apps" screen and the option row disappears the moment they come back
 *   with consent granted (their explicit spec: granted = "it won't even
 *   show anything").
 *
 * # The D-560 redesign (the round-72 device feedback: "the sponsor pop-up
 *   is not that good. It is a bit more cramped… make it fun… clean…
 *   minimal… simple")
 *
 * The card grew air instead of furniture: a tinted hero bubble, ONE short
 * line of copy per state, a clear primary action + a quiet escape, and —
 * only while the overlay consent is missing — one compact row offering
 * "draw over other apps" (the return pill's permission, D-443/D-449).
 *
 * # The D-561 rework (the round-73 device feedback)
 *
 * The word "sponsor" is GONE from the card — every trace of it (the
 * SPONSORED eyebrow, "a quick visit to our sponsor…", "stay with our
 * sponsor…") deleted: "it should never mention sponsored… make sure that
 * it does not mention sponsor anywhere." The Pending card now says exactly
 * three things — "Support AniKuta", "(It just takes a few seconds)" in
 * rounded brackets, and its two buttons; the approved overlay row gains ONE
 * description line ("Makes things easier for you") and nothing else. The
 * TryAgain/InProgress states adopt the same parenthetical quietness. Every
 * state keeps its original MEANING and the coordinator contract is
 * untouched: the same Crossfade, the same back-cancels escape, the same
 * pill show-in-tap-handler wiring (a state-effect-driven show could be
 * deferred past the app-backgrounding, D-443).
 *
 * # The D-562 rework (the v1.1.35 device feedback: "the pop-up does not
 *   look good. I think we should only… show the heading Support AniKuta")
 *
 * The hero bubbles are GONE from every state — the heading is the card's
 * top element, nothing decorates it. Pending = "Support AniKuta" → "(It
 * just takes a few seconds)" → Continue → Not now (+ the frozen overlay
 * row while the consent is missing); Waiting keeps its spinner (the state
 * needs SOMETHING moving) but loses its bubble; TryAgain keeps its real
 * threshold line. The approved overlay row ("shows properly, and it works
 * properly too") is untouched — title + ONE description, granted = gone.
 *
 * # The D-563 rework (the v1.1.36 device feedback: "previously at the top
 *   of the pop-up it had an SVG logo, but now it does not. So I would like
 *   you to add that SVG logo with a proper animation")
 *
 * The hero is BACK — the D-560 tinted circle returns to the top of every
 * state carrying the same vector glyphs it always had (OpenInNew / the
 * spinner / Refresh), now ANIMATED: a spring entrance pop (scale + fade,
 * replayed per state — each Crossfade swap composes a fresh hero) and a
 * quiet breathing loop so the mark feels alive. The heading is still the
 * first TEXT element; the D-562 minimal card shape stands.
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
    // D-443: the floating return pill (over the browser) — controller + the
    // theme colors it renders with, captured from THIS composition so the
    // pill always matches the user's active palette.
    val pillController = koinInject<SmartLinkReturnPillController>()
    val pillColors = ReturnPillColors(
        container = MaterialTheme.colorScheme.surface.toArgb(),
        content = MaterialTheme.colorScheme.onSurface.toArgb(),
        accent = MaterialTheme.colorScheme.primary.toArgb(),
        onAccent = MaterialTheme.colorScheme.onPrimary.toArgb(),
        error = MaterialTheme.colorScheme.error.toArgb(),
    )
    val pillDurationMs = repository.config.smartLink.minTimeOutsideMs

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

    // D-443/D-454: the floating return pill lives over the BROWSER exactly
    // while the ad is in progress. The SHOW is invoked directly in the
    // Continue / Try-again tap handlers (NOT from a state-driven effect):
    // the state flip happens in the same tap, but a recomposition could be
    // deferred until after the browser covers the screen. Every state
    // CHANGE here hides the pill — and D-454 passes the verdict through:
    // AdTryAgain (returned before the timer) pops the RED X; every other
    // exit (completed / cancelled) pops the green check. The controller is
    // a no-op when the overlay permission isn't granted.
    LaunchedEffect(state) {
        if (state !is AdGateState.AdInProgress) {
            pillController.hide(returnedTooEarly = state is AdGateState.AdTryAgain)
        }
    }

    // D-560: the overlay consent, re-read on every ON_RESUME. The user leaves
    // for the system's toggle screen from the option row, flips it, comes
    // back — and the row is gone before the card settles. Remember (not
    // Saveable): the consent is a DEVICE fact, re-read fresh each time the
    // interstitial composes.
    var overlayGranted by remember { mutableStateOf(OverlayPermissions.hasAccess(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = OverlayPermissions.hasAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                shape = RoundedCornerShape(28.dp),
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
                            .padding(start = 28.dp, end = 28.dp, top = 32.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (current) {
                            is AdGateState.AdPending -> AdPendingContent(
                                config = repository.config,
                                overlayGranted = overlayGranted,
                                // The show is IN the tap handler (before
                                // onUserContinue opens the browser) — a
                                // state-effect-driven show could be deferred
                                // past the app-backgrounding (D-443).
                                onContinue = {
                                    pillController.show(context, pillColors, pillDurationMs)
                                    coordinator.onUserContinue(context)
                                },
                                onCancel = { coordinator.cancel() },
                                onEnableOverlay = { OverlayPermissions.openSettings(context) },
                            )
                            is AdGateState.AdInProgress -> AdInProgressContent()
                            is AdGateState.AdTryAgain -> AdTryAgainContent(
                                config = repository.config,
                                overlayGranted = overlayGranted,
                                onTryAgain = {
                                    pillController.show(context, pillColors, pillDurationMs)
                                    coordinator.onTryAgain(context)
                                },
                                onCancel = { coordinator.cancel() },
                                onEnableOverlay = { OverlayPermissions.openSettings(context) },
                            )
                            else -> { /* Idle — but we returned early above; defensive. */ }
                        }
                    }
                }
            }
        }
    }
}

// ── Shared shells ─────────────────────────────────────────────────────────────

/**
 * D-563: the hero logo — back by the user's v1.1.36 request ("previously at
 * the top of the pop-up it had an SVG logo, but now it does not. So I would
 * like you to add that SVG logo with a proper animation"). The D-560 shape
 * (a soft primary wash behind one glyph — no borders, no gradients, no
 * second color) with TWO layers of animation:
 *
 *  1. ENTRANCE — a one-shot spring pop: scale 0.6 → 1 with a MediumBouncy
 *     overshoot + a fade-in. [LaunchedEffect] fires it the moment the hero
 *     composes, and because the [Crossfade] swaps compose a FRESH hero per
 *     state, the pop replays on every state transition — the card always
 *     lands alive.
 *  2. BREATHING — a slow ±4.5% reverse loop (1.2s each way,
 *     FastOutSlowInEasing) multiplied into the settled scale, so the mark
 *     gently breathes while the card waits. Quiet by design: the D-560
 *     "no furniture" rule holds — it is motion, not decoration.
 *
 * Both animations are read inside the draw-phase [graphicsLayer] lambda —
 * the scale/alpha updates never trigger recompositions.
 */
@Composable
private fun HeroBubble(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 1. The entrance: 0 → 1 on a bouncy spring, once per composition.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }
    // 2. The breathing: an infinite reverse loop, always running (cheap —
    // draw-phase only), multiplied into the entrance-settled scale.
    val breath by rememberInfiniteTransition(label = "hero-breath").animateFloat(
        initialValue = 1f,
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hero-breath-scale",
    )
    Box(
        modifier = modifier
            .size(64.dp)
            .graphicsLayer {
                val enter = 0.6f + 0.4f * entrance.value
                scaleX = enter * breath
                scaleY = enter * breath
                alpha = entrance.value.coerceIn(0f, 1f)
            }
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * D-560: the one-row "draw over other apps" offer. Renders ONLY while the
 * consent is missing (the caller guards with `if (!overlayGranted)` —
 * granted = nothing at all, the user's exact words). One tappable line that
 * opens the system's per-app overlay toggle. D-561 adds the ONE description
 * line the user asked for under the title ("Makes things easier for you")
 * and NOTHING else — the wizard (D-449) already told the story, this is
 * just the missing switch.
 */
@Composable
private fun OverlayPermissionRow(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Layers,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable draw over other apps",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // D-561: the ONE description the user asked for under the
                // row's title — "it could show a short description that it
                // makes things easier for you. And that's it. Besides that,
                // it won't show any other things."
                Text(
                    text = "Makes things easier for you",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Per-state content ──────────────────────────────────────────────────────────

/** The "Continue" state — shown when the ad first appears. */
@Composable
private fun AdPendingContent(
    config: AdsConfig,
    overlayGranted: Boolean,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    onEnableOverlay: () -> Unit,
) {
    // D-563: the animated hero logo is BACK (the user asked for it by
    // name after v1.1.36 shipped without it) — the heading stays the first
    // TEXT element, exactly as the D-561 rule demands.
    HeroBubble {
        Icon(
            imageVector = Icons.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
    }
    Spacer(Modifier.height(14.dp))
    // D-561: the ONLY heading the card carries — "support AniKuta is the
    // only thing which it should show there". No eyebrow, no disclosure,
    // no sponsor word anywhere on the card.
    Text(
        text = "Support AniKuta",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(6.dp))
    // D-561: the ONE quiet line, in rounded brackets — the user dictated
    // it "in rounded brackets" and nothing more: "below it, it should just
    // say that it just takes a few seconds".
    Text(
        text = "(It just takes a few seconds)",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Continue")
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onCancel) {
        Text("Not now")
    }
    // The overlay offer — ONLY while the consent is missing (granted = the
    // row does not exist at all).
    if (!overlayGranted) {
        Spacer(Modifier.height(6.dp))
        OverlayPermissionRow(onClick = onEnableOverlay)
    }
}

/** The "waiting for return" state — spinner while the user is in the browser. */
@Composable
private fun AdInProgressContent() {
    // D-563: the spinner rides inside the animated hero bubble again —
    // the state keeps its motion (the spinner) AND the logo the user
    // asked back, breathing with the same entrance as the other states.
    HeroBubble {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(26.dp),
        )
    }
    Spacer(Modifier.height(14.dp))
    Text(
        text = "See you in a moment",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(6.dp))
    // D-561: the parenthetical quietness matches the other states.
    Text(
        text = "(Come back to AniKuta when you're ready)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** The "Try again" state — shown when the user returned too quickly. */
@Composable
private fun AdTryAgainContent(
    config: AdsConfig,
    overlayGranted: Boolean,
    onTryAgain: () -> Unit,
    onCancel: () -> Unit,
    onEnableOverlay: () -> Unit,
) {
    // D-563: the animated hero logo is back here too — same entrance,
    // same breathing, the Refresh glyph it always carried.
    HeroBubble {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
    }
    Spacer(Modifier.height(14.dp))
    Text(
        text = "That was too quick",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(6.dp))
    // ONE line, with the real threshold (the pill counts the same number),
    // in the D-561 parenthetical style — and the sponsor word is gone.
    val seconds = (config.smartLink.minTimeOutsideMs / 1000).coerceAtLeast(1)
    Text(
        text = "(Stay for ${seconds}s, then come back)",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onTryAgain,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Try again")
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onCancel) {
        Text("Not now")
    }
    // The overlay offer — ONLY while the consent is missing (granted = the
    // row does not exist at all).
    if (!overlayGranted) {
        Spacer(Modifier.height(6.dp))
        OverlayPermissionRow(onClick = onEnableOverlay)
    }
}
