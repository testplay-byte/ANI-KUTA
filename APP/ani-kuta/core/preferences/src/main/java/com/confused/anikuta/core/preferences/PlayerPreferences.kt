package com.confused.anikuta.core.preferences

/**
 * Player-specific preferences.
 *
 * Ported from the old project's PlayerPreferences — modernized for ANI-KUTA.
 * I5 fix: lives in :core:preferences (not :core:player) per architecture plan.
 *
 * These control playback behavior, subtitle styling, and player UI.
 */
class PlayerPreferences(private val store: PreferenceStore) {

    // ── Playback ──────────────────────────────────────────────────────────────

    /** Playback speed (0.25 to 4.0, default 1.0) */
    var playbackSpeed: Float
        get() = store.getFloat(KEY_SPEED, 1.0f)
        set(value) = store.putFloat(KEY_SPEED, value)

    /** Whether to auto-play the next episode */
    var autoPlayNext: Boolean
        get() = store.getBoolean(KEY_AUTOPLAY_NEXT, true)
        set(value) = store.putBoolean(KEY_AUTOPLAY_NEXT, value)

    /** Whether to resume from last position */
    var resumeFromLastPosition: Boolean
        get() = store.getBoolean(KEY_RESUME, true)
        set(value) = store.putBoolean(KEY_RESUME, value)

    /** Skip opening duration in seconds (0 = disabled) */
    var skipOpeningDuration: Int
        get() = store.getInt(KEY_SKIP_OPENING, 0)
        set(value) = store.putInt(KEY_SKIP_OPENING, value)

    /** Skip ending duration in seconds (0 = disabled) */
    var skipEndingDuration: Int
        get() = store.getInt(KEY_SKIP_ENDING, 0)
        set(value) = store.putInt(KEY_SKIP_ENDING, value)

    // ── Subtitles ──────────────────────────────────────────────────────────────

    /** Subtitle font size (12 to 48, default 16) */
    var subtitleFontSize: Int
        get() = store.getInt(KEY_SUB_FONT_SIZE, 16)
        set(value) = store.putInt(KEY_SUB_FONT_SIZE, value)

    /** Subtitle color (hex string, default white) */
    var subtitleColor: String
        get() = store.getString(KEY_SUB_COLOR, "#FFFFFF")
        set(value) = store.putString(KEY_SUB_COLOR, value)

    /** Subtitle background color (hex string, default semi-transparent black) */
    var subtitleBackgroundColor: String
        get() = store.getString(KEY_SUB_BG_COLOR, "#80000000")
        set(value) = store.putString(KEY_SUB_BG_COLOR, value)

    // ── Player UI ──────────────────────────────────────────────────────────────

    /** Whether to use gesture controls (swipe to seek, brightness, volume) */
    var gestureControlsEnabled: Boolean
        get() = store.getBoolean(KEY_GESTURES, true)
        set(value) = store.putBoolean(KEY_GESTURES, value)

    /** Whether to use hardware decoding */
    var hardwareDecoding: Boolean
        get() = store.getBoolean(KEY_HW_DECODE, true)
        set(value) = store.putBoolean(KEY_HW_DECODE, value)

    companion object {
        private const val KEY_SPEED = "player_speed"
        private const val KEY_AUTOPLAY_NEXT = "player_autoplay_next"
        private const val KEY_RESUME = "player_resume"
        private const val KEY_SKIP_OPENING = "player_skip_opening"
        private const val KEY_SKIP_ENDING = "player_skip_ending"
        private const val KEY_SUB_FONT_SIZE = "player_sub_font_size"
        private const val KEY_SUB_COLOR = "player_sub_color"
        private const val KEY_SUB_BG_COLOR = "player_sub_bg_color"
        private const val KEY_GESTURES = "player_gestures"
        private const val KEY_HW_DECODE = "player_hw_decode"
    }
}
