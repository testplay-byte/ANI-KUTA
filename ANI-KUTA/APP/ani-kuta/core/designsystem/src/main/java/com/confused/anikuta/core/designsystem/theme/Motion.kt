package com.confused.anikuta.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Motion tokens — animation durations + easings.
 *
 * From DESIGN-LANGUAGE.md §7:
 * - 300ms FastOutSlowInEasing is the heartbeat.
 * - 400ms for theme-switch cross-fade.
 */
object Motion {
    const val DurationInstant = 100
    const val DurationShort = 150
    const val DurationStandard = 300
    const val DurationLong = 400

    val EasingStandard: Easing = FastOutSlowInEasing
    val EasingEmphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
}
