package com.confused.anikuta.core.player

import android.content.Context
import com.confused.anikuta.core.common.HapticHelper

/**
 * Task 48 (device round 7 — playback haptics): the player's haptic vocabulary.
 *
 * The user's request: "adding the device feedback on playback and other stuff
 * like that properly". Discrete, meaningful feedback on the player's core
 * interactions — not on every touch:
 *
 * - [SEEK_TICK] — a skip actually fired (double-tap zones, ±10s buttons):
 *   the lightest notch (EFFECT_TICK).
 * - [PLAY_PAUSE_TOGGLE] — play/pause state change: a medium click.
 * - [SEEK_RELEASE] — a scrub ends and the seek commits (EFFECT_HEAVY_CLICK).
 *
 * Every call site is gated by [com.confused.anikuta.core.preferences.PlayerPreferences.hapticFeedback]
 * (default ON) so users can turn it off.
 */
enum class PlayerHaptic {
    SEEK_TICK,
    PLAY_PAUSE_TOGGLE,
    SEEK_RELEASE,
}

/** Performs this haptic (a no-op when the device has no vibrator). */
fun PlayerHaptic.perform(context: Context) {
    when (this) {
        PlayerHaptic.SEEK_TICK -> HapticHelper.lightTick(context)
        PlayerHaptic.PLAY_PAUSE_TOGGLE -> HapticHelper.stageCross(context)
        PlayerHaptic.SEEK_RELEASE -> HapticHelper.releaseConfirm(context)
    }
}
