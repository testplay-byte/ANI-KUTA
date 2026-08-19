package com.confused.anikuta.core.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Centralized haptic feedback helper.
 *
 * Uses the [Vibrator] system service directly (via [VibratorManager] on API 31+)
 * rather than [android.view.View.performHapticFeedback]. This is more reliable
 * because:
 *  - It does NOT depend on the View's haptic-feedback enabled setting.
 *  - It is NOT silenced by some OEM "touch feedback" toggles.
 *  - It works in battery-saver mode (which can disable performHapticFeedback).
 *  - It gives precise control over the vibration waveform/feel.
 *
 * Requires the VIBRATE permission (declared in the app manifest).
 *
 * API level handling:
 *  - API 29+ (Android 10): [VibrationEffect.createPredefined] — rich predefined
 *    effects (EFFECT_CLICK, EFFECT_HEAVY_CLICK, EFFECT_DOUBLE_CLICK, EFFECT_TICK).
 *  - API 26-28 (Android 8-9): [VibrationEffect.createOneShot] — single pulse
 *    with controllable amplitude.
 *  - API 24-25 (Android 7): deprecated [Vibrator.vibrate] overload — duration
 *    only, no amplitude control.
 */
object HapticHelper {

    /**
     * Light, short haptic for stage-threshold crossing.
     *
     * On API 29+ uses EFFECT_TICK (the lightest predefined effect — a tiny,
     * crisp "tap" that feels like a notch detent). On older APIs, a 10ms one-shot.
     */
    fun lightTick(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

    /**
     * Medium haptic for a confirmed stage threshold (e.g. "you've crossed into
     * a new refresh tier"). Slightly stronger than [lightTick].
     *
     * On API 29+ uses EFFECT_CLICK. On older APIs, a 20ms one-shot.
     */
    fun stageCross(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }

    /**
     * Strong confirmation haptic for when the user releases and an action fires
     * (e.g. the refresh is triggered). Heavier than [stageCross].
     *
     * On API 29+ uses EFFECT_HEAVY_CLICK. On older APIs, a 30ms one-shot.
     */
    fun releaseConfirm(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }

    /**
     * Resolve the [Vibrator] service. On API 31+ (Android 12), the
     * [VibratorManager] is the canonical entry point (the standalone
     * VIBRATOR_SERVICE is deprecated but still works).
     */
    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
