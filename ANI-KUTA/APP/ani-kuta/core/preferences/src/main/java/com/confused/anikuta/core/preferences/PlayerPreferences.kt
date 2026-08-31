package com.confused.anikuta.core.preferences

/**
 * Player-specific preferences.
 *
 * Ported from the old project's PlayerPreferences — modernized for ANI-KUTA.
 * I5 fix: lives in :core:preferences (not :core:player) per architecture plan.
 *
 * These control playback behavior, subtitle styling, and player UI.
 *
 * Subtitle preferences (subtitleFont through subtitlesDelay) are used by both
 * [AnikutaMPVView.applySubtitlePreferencesInit] (init-time setOptionString) and
 * [AnikutaMPVView.applySubtitlePreferences] (runtime setPropertyInt/Double/String).
 * The SubtitleSettingsSheet reads/writes these live.
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

    /** Whether to try hardware decoding (MPV hwdec=auto vs no). Default: true. */
    var tryHwDecoding: Boolean
        get() = store.getBoolean(KEY_TRY_HWDEC, true)
        set(value) = store.putBoolean(KEY_TRY_HWDEC, value)

    /** Whether to use gpu-next VO (experimental; default false = gpu). */
    var gpuNext: Boolean
        get() = store.getBoolean(KEY_GPU_NEXT, false)
        set(value) = store.putBoolean(KEY_GPU_NEXT, value)

    /** Volume boost cap above 100 (0 = no boost, max 100 = up to 200%). */
    var volumeBoostCap: Int
        get() = store.getInt(KEY_VOLUME_BOOST_CAP, 0)
        set(value) = store.putInt(KEY_VOLUME_BOOST_CAP, value)

    /** Preferred audio languages (MPV alang). Default: "jpn,eng". */
    var preferredAudioLanguages: String
        get() = store.getString(KEY_ALANG, "jpn,eng")
        set(value) = store.putString(KEY_ALANG, value)

    // ── Subtitles — full set (ported from old project) ────────────────────────

    /** Subtitle font family. Default: "Sans Serif". */
    var subtitleFont: String
        get() = store.getString(KEY_SUB_FONT, "Sans Serif")
        set(value) = store.putString(KEY_SUB_FONT, value)

    /** Subtitle font size (MPV sub-font-size). Default: 55. Range: 20..100. */
    var subtitleFontSize: Int
        get() = store.getInt(KEY_SUB_FONT_SIZE, 55)
        set(value) = store.putInt(KEY_SUB_FONT_SIZE, value)

    /** Subtitle font scale multiplier. Default: 1.0. Range: 0.5..3.0. */
    var subtitleFontScale: Float
        get() = store.getFloat(KEY_SUB_SCALE, 1.0f)
        set(value) = store.putFloat(KEY_SUB_SCALE, value)

    /** Subtitle border/outline size. Default: 3. Range: 0..10. */
    var subtitleBorderSize: Int
        get() = store.getInt(KEY_SUB_BORDER_SIZE, 3)
        set(value) = store.putInt(KEY_SUB_BORDER_SIZE, value)

    /** Bold subtitles. Default: false. */
    var boldSubtitles: Boolean
        get() = store.getBoolean(KEY_BOLD_SUBS, false)
        set(value) = store.putBoolean(KEY_BOLD_SUBS, value)

    /** Italic subtitles. Default: false. */
    var italicSubtitles: Boolean
        get() = store.getBoolean(KEY_ITALIC_SUBS, false)
        set(value) = store.putBoolean(KEY_ITALIC_SUBS, value)

    /** Subtitle text color (ARGB int). Default: White (0xFFFFFFFF). */
    var textColorSubtitles: Int
        get() = store.getInt(KEY_SUB_TEXT_COLOR, 0xFFFFFFFF.toInt())
        set(value) = store.putInt(KEY_SUB_TEXT_COLOR, value)

    /** Subtitle border/outline color (ARGB int). Default: Black (0xFF000000). */
    var borderColorSubtitles: Int
        get() = store.getInt(KEY_SUB_BORDER_COLOR, 0xFF000000.toInt())
        set(value) = store.putInt(KEY_SUB_BORDER_COLOR, value)

    /** Subtitle background color (ARGB int). Default: transparent (0x00000000). */
    var backgroundColorSubtitles: Int
        get() = store.getInt(KEY_SUB_BG_COLOR, 0x00000000)
        set(value) = store.putInt(KEY_SUB_BG_COLOR, value)

    /** Subtitle vertical position (0-100, 100 = bottom). Default: 100. */
    var subtitlePosition: Int
        get() = store.getInt(KEY_SUB_POS, 100)
        set(value) = store.putInt(KEY_SUB_POS, value)

    /** Subtitle shadow offset. Default: 0. Range: 0..10. */
    var subtitleShadowOffset: Int
        get() = store.getInt(KEY_SUB_SHADOW_OFFSET, 0)
        set(value) = store.putInt(KEY_SUB_SHADOW_OFFSET, value)

    /** Override ASS/SSA subtitle styling. Default: false. */
    var overrideSubsAss: Boolean
        get() = store.getBoolean(KEY_OVERRIDE_ASS, false)
        set(value) = store.putBoolean(KEY_OVERRIDE_ASS, value)

    /** Subtitle delay in milliseconds (-5000..5000). Default: 0. */
    var subtitlesDelay: Int
        get() = store.getInt(KEY_SUB_DELAY, 0)
        set(value) = store.putInt(KEY_SUB_DELAY, value)

    // ── Task 55 (round 15): source-list formatting + CS subtitle language ────
    //
    // The shared "formatting" toggle for the source-picking sheets (resolve
    // sheets at the details entry + the in-player Qualities-and-Servers
    // sheets), used by BOTH stacks:
    //  - ON  (default): the aniyomi ResolverSheet design — collapsible server
    //    cards, audio-version chips, quality chips.
    //  - OFF: a raw flat list — one row per resolved stream, unformatted
    //    labels (server name + quality), tap = play directly. No expanding.
    // Non-reactive by design: the sheets are recreated on every open, so a
    // read-at-open + write-on-toggle is sufficient.
    var resolveSheetFormatted: Boolean
        get() = store.getBoolean(KEY_RESOLVE_SHEET_FORMATTED, true)
        set(value) = store.putBoolean(KEY_RESOLVE_SHEET_FORMATTED, value)

    /**
     * Preferred subtitle languages (comma-separated codes/names). Task 55:
     * the CS (Media3) player auto-selects a matching sidecar/embedded track
     * on first READY — the behavioral mirror of MPV's `slang` default (which
     * picks the OS-language track). NEW key: only the CS engine reads it; the
     * MPV stack is untouched.
     */
    var preferredSubtitleLanguages: String
        get() = store.getString(KEY_PREF_SUB_LANGS, "en,eng,english")
        set(value) = store.putString(KEY_PREF_SUB_LANGS, value)

    // ── Phase 2: Auto-select video (mirror of DownloadPreferences) ───────────
    //
    // When autoSelectVideo is ON, the player auto-resolves the best video using
    // these preferences (server, audio, quality, fallback) instead of showing
    // the manual ResolverSheet. Mirrors the auto-download engine — uses the same
    // AutoDownloadEngine.selectBestVideo() pure function.
    //
    // Difference from download: no DO_NOT_DOWNLOAD global fallback (playback must
    // always pick something). Only BEST_EFFORT or ASK.

    /** Whether to auto-select the video for playback (default: false = manual pick). */
    val autoSelectVideo = store.preference(
        "pref_player_auto_select", false, BooleanSerializer,
    )

    /** Ordered list of preferred qualities for auto-select (drag-reorderable). */
    val preferredQualities = store.preference(
        "pref_player_qualities", listOf("1080p", "720p", "480p", "360p"), StringListSerializer,
    )

    /** Fallback strategy when the preferred quality is unavailable. */
    val qualityFallback = store.preference(
        "pref_player_quality_fallback", "TRY_NEXT", StringSerializer,
    )

    /** Ordered list of preferred audio versions for auto-select (drag-reorderable). */
    val preferredAudio = store.preference(
        "pref_player_audio", listOf("SUB", "DUB", "HSUB"), StringListSerializer,
    )

    /** Fallback strategy when the preferred audio is unavailable. */
    val audioFallback = store.preference(
        "pref_player_audio_fallback", "TRY_NEXT", StringSerializer,
    )

    /** Ordered list of preferred servers for auto-select (drag-reorderable). */
    val preferredServers = store.preference(
        "pref_player_servers", listOf("Streamtape", "Vidstreaming", "Mp4Upload"), StringListSerializer,
    )

    /** Fallback strategy when the preferred server is unavailable. */
    val serverFallback = store.preference(
        "pref_player_server_fallback", "TRY_NEXT", StringSerializer,
    )

    /** The priority order of the 3 preference dimensions for auto-select. */
    val dimensionPriority = store.preference(
        "pref_player_dimension_priority",
        listOf("AUDIO", "QUALITY", "SERVER"),
        StringListSerializer,
    )

    /**
     * What to do when NO candidate matches ANY preference:
     *  - "BEST_EFFORT" — play the least-bad option (default).
     *  - "ASK" — show the video picker sheet.
     *
     * (No DO_NOT_DOWNLOAD option — playback must always pick something.)
     */
    val globalFallback = store.preference(
        "pref_player_global_fallback", "BEST_EFFORT", StringSerializer,
    )

    // ── Player UI ──────────────────────────────────────────────────────────────

    /** Whether to use gesture controls (swipe to seek, brightness, volume) */
    var gestureControlsEnabled: Boolean
        get() = store.getBoolean(KEY_GESTURES, true)
        set(value) = store.putBoolean(KEY_GESTURES, value)

    /** Whether to use hardware decoding (legacy field, superseded by tryHwDecoding) */
    var hardwareDecoding: Boolean
        get() = store.getBoolean(KEY_HW_DECODE, true)
        set(value) = store.putBoolean(KEY_HW_DECODE, value)

    companion object {
        private const val KEY_SPEED = "player_speed"
        private const val KEY_AUTOPLAY_NEXT = "player_autoplay_next"
        private const val KEY_RESUME = "player_resume"
        private const val KEY_SKIP_OPENING = "player_skip_opening"
        private const val KEY_SKIP_ENDING = "player_skip_ending"
        private const val KEY_TRY_HWDEC = "player_try_hwdec"
        private const val KEY_GPU_NEXT = "player_gpu_next"
        private const val KEY_VOLUME_BOOST_CAP = "player_volume_boost_cap"
        private const val KEY_ALANG = "player_alang"
        private const val KEY_SUB_FONT = "pref_subtitle_font"
        private const val KEY_SUB_FONT_SIZE = "pref_subtitle_font_size"
        private const val KEY_SUB_SCALE = "pref_sub_scale"
        private const val KEY_SUB_BORDER_SIZE = "pref_sub_border_size"
        private const val KEY_BOLD_SUBS = "pref_bold_subtitles"
        private const val KEY_ITALIC_SUBS = "pref_italic_subtitles"
        private const val KEY_SUB_TEXT_COLOR = "pref_text_color_subtitles"
        private const val KEY_SUB_BORDER_COLOR = "pref_border_color_subtitles"
        private const val KEY_SUB_BG_COLOR = "pref_background_color_subtitles"
        private const val KEY_SUB_POS = "pref_sub_pos"
        private const val KEY_SUB_SHADOW_OFFSET = "pref_sub_shadow_offset"
        private const val KEY_OVERRIDE_ASS = "pref_override_subtitles_ass"
        private const val KEY_SUB_DELAY = "pref_subtitles_delay"
        private const val KEY_RESOLVE_SHEET_FORMATTED = "pref_resolve_sheet_formatted"
        private const val KEY_PREF_SUB_LANGS = "pref_preferred_subtitle_languages"
        private const val KEY_GESTURES = "player_gestures"
        private const val KEY_HW_DECODE = "player_hw_decode"
    }
}
