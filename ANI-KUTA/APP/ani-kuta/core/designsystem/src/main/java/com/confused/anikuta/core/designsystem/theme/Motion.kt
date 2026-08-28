package com.confused.anikuta.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Motion tokens — animation durations + easings.
 *
 * From DESIGN-LANGUAGE.md §6:
 * - 300ms FastOutSlowInEasing is the heartbeat.
 * - 400ms for theme-switch cross-fade.
 * - Nav shell + shared-element morph run the SAME easing curve
 *   (EasingEmphasized — mismatched curves at equal duration were the D-324
 *   device-reported "jitter"), but their DURATIONS are deliberately
 *   DECOUPLED (D-327): the page crossfade settles at [DurationContainer]
 *   (450ms) while the flying cover keeps gliding until
 *   [DurationSharedFlight] (600ms) — "the details page can open up early but
 *   the image will move slowly" (user spec, v0.2.62 device round).
 */
object Motion {
    const val DurationInstant = 100
    const val DurationShort = 150
    const val DurationStandard = 300
    const val DurationLong = 400

    /** D-324: nav container crossfade when Details is involved (page-level). */
    const val DurationContainer = 450

    /**
     * D-327: the shared-element cover's OWN bounds morph (card → details
     * banner flight). Longer than [DurationContainer] on purpose so the
     * cover's glide reads as calm and deliberate instead of rushed; both
     * animations still share [EasingEmphasized], so they never fight.
     */
    const val DurationSharedFlight = 600

    val EasingStandard: Easing = FastOutSlowInEasing
    val EasingEmphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
}
