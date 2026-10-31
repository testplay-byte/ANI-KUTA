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

    /**
     * Subtitle font size (MPV sub-font-size). Default: **100 (max)** — Task 59
     * (round 19, the user's spec: the default subtitle look is font size MAX,
     * scale 0.5×, border 5; 100 × 0.5 renders ≈ the old 55 default). Range:
     * 20..100.
     */
    var subtitleFontSize: Int
        get() = store.getInt(KEY_SUB_FONT_SIZE, 100)
        set(value) = store.putInt(KEY_SUB_FONT_SIZE, value)

    /**
     * Subtitle font scale multiplier. Default: **0.5×** — Task 59 (the user's
     * spec; see [subtitleFontSize]). Range: 0.5..3.0.
     */
    var subtitleFontScale: Float
        get() = store.getFloat(KEY_SUB_SCALE, 0.5f)
        set(value) = store.putFloat(KEY_SUB_SCALE, value)

    /**
     * Subtitle border/outline size. Default: **5** — Task 59 ("a comfortable
     * 5", the user's spec). Range: 0..10.
     */
    var subtitleBorderSize: Int
        get() = store.getInt(KEY_SUB_BORDER_SIZE, 5)
        set(value) = store.putInt(KEY_SUB_BORDER_SIZE, value)

    /**
     * Bold subtitles. Default: **true** — Task 60 (round 20, the user's
     * spec: "by default the bold should be turned on… make both of them as
     * accurate to each other as possible"). One preference drives BOTH
     * stacks (MPV `sub-bold` and the CS overlay's FontWeight), so the sheets
     * and the engine land bold-on out of the box.
     */
    var boldSubtitles: Boolean
        get() = store.getBoolean(KEY_BOLD_SUBS, true)
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

    /**
     * Task 59 (round 19) — resets EVERY subtitle preference to its default in
     * one write (the shared backing for BOTH subtitle settings sheets' Reset
     * buttons — the aniyomi sheet and the CS sheet). Defaults per the user's
     * spec: font size MAX (100), scale 0.5×, border 5; everything else back to
     * its out-of-box value (Sans Serif, **bold ON** — Task 60, italic off,
     * white text, black border, transparent background, position 100,
     * shadow 0, delay 0, ASS-override off).
     *
     * Callers fire their `onApplySettings` after this so the live preview and
     * the engine (MPV / Media3) pick the reset state up immediately.
     */
    fun resetSubtitleSettings() {
        subtitleFont = "Sans Serif"
        subtitleFontSize = 100
        subtitleFontScale = 0.5f
        subtitleBorderSize = 5
        boldSubtitles = true
        italicSubtitles = false
        textColorSubtitles = 0xFFFFFFFF.toInt()
        borderColorSubtitles = 0xFF000000.toInt()
        backgroundColorSubtitles = 0x00000000
        subtitlePosition = 100
        subtitleShadowOffset = 0
        subtitlesDelay = 0
        overrideSubsAss = false
    }

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

    /**
     * Whether player gestures are enabled — the D-809 master gate. Was an
     * unread `var` (key `player_gestures`) until round 131 gave it consumers;
     * same key, so any stored value survives.
     */
    val gestureControlsEnabled = store.preference(
        KEY_GESTURES, true, BooleanSerializer,
    )

    /** Whether to use hardware decoding (legacy field, superseded by tryHwDecoding) */
    var hardwareDecoding: Boolean
        get() = store.getBoolean(KEY_HW_DECODE, true)
        set(value) = store.putBoolean(KEY_HW_DECODE, value)

    // ── D-408 (round 32): the per-series selected-subtitle memory ─────────────
    //
    // The report: "make it remember the location of the selected subtitle
    // files. If the user manually selects a subtitle, then that subtitle file
    // will be remembered for that specific episode… that subtitle will be
    // selected and be pre-applied on that." Persisted on every sheet track
    // selection (incl. Off), every manual import, and every storage-row load;
    // pre-applied by the player after every track-list reload (the label is
    // matched against the live tracks; "off" pre-applies sid=no).
    //
    // Keyed by mainId (one small string per series ever watched — negligible;
    // a future cap can LRU-evict if it ever matters).

    /**
     * The remembered selected subtitle track for a series.
     * @return "" = nothing remembered; `"off"` = the user explicitly chose
     *   Off; anything else = the track's display label ("English", "My
     *   Subs") to pre-select on open.
     */
    fun getPreferredSubtitleTrack(mainId: String): String =
        if (mainId.isBlank()) "" else store.getString(KEY_PREFIX_SUB_TRACK_MEMORY + mainId, "")

    /**
     * Remembers the selected subtitle track for a series. [label] = the
     * track's display label, `"off"` for an explicit Off, or blank to CLEAR
     * the memory.
     */
    fun setPreferredSubtitleTrack(mainId: String, label: String) {
        if (mainId.isBlank()) return
        val key = KEY_PREFIX_SUB_TRACK_MEMORY + mainId
        if (label.isBlank()) store.delete(key) else store.putString(key, label)
    }

    // ── Round 120 (D-747): playback behavior (the Player settings hub) ───────
    //
    // The round-120 order: the player needed REAL settings beyond auto-select
    // video — end-of-episode behavior, the next-episode card, and intro/outro
    // skipping. These are the shared knobs both player stacks read.

    /** Whether the next-episode card appears near the end of an episode. */
    val nextUpCardEnabled = store.preference(
        "pref_player_next_up_card", true, BooleanSerializer,
    )

    /**
     * Intro-skip mode: `"OFF"` (never), `"BUTTON"` (show the skip pill during
     * the intro — default), `"AUTO"` (silently seek past the intro).
     */
    val skipIntroMode = store.preference(
        "pref_player_skip_intro_mode", "BUTTON", StringSerializer,
    )

    /** Outro-skip mode — same vocabulary as [skipIntroMode]. */
    val skipOutroMode = store.preference(
        "pref_player_skip_outro_mode", "BUTTON", StringSerializer,
    )

    // ── Round 131 (D-809): touch gestures ─────────────────────────────────────
    //
    // Vertical side-swipes map to user-chosen actions (right defaults to
    // VOLUME, left to BRIGHTNESS); the horizontal swipe live-seeks with the
    // video visibly moving while the finger drags. [gestureControlsEnabled]
    // (the legacy `player_gestures` flag, so far unread) stays the master
    // gate — every gesture checks it before acting.

    /** Right-side vertical swipe action: `"VOLUME"` (default) / `"BRIGHTNESS"` / `"NONE"`. */
    val gestureRightSideAction = store.preference(
        "pref_player_gesture_right_side", "VOLUME", StringSerializer,
    )

    /** Left-side vertical swipe action: `"BRIGHTNESS"` (default) / `"VOLUME"` / `"NONE"`. */
    val gestureLeftSideAction = store.preference(
        "pref_player_gesture_left_side", "BRIGHTNESS", StringSerializer,
    )

    /** Whether the horizontal swipe live-seeks (video moves while dragging). Default on. */
    val gestureHorizontalSeekEnabled = store.preference(
        "pref_player_gesture_horizontal_seek", true, BooleanSerializer,
    )

    // ── Round 120 (D-748): per-series intro/outro timestamps ─────────────────
    //
    // The user marks where a series' intro/outro starts; the player remembers
    // it for every episode of that series (openings share a length within a
    // season — one pair of marks covers the whole rewatch). Stored per mainId
    // as a compact "introStart|introEnd|outroStart" seconds string (unset
    // segments blank), following the per-series subtitle-track-memory pattern
    // (D-408). Preference-backed by design: round 120 explicitly scoped the
    // database OUT, so no schema change rides along.

    /**
     * The remembered skip timestamps for a series (seconds). Null fields = not
     * marked. The intro is the [introStartSec, introEndSec) span; the outro is
     * everything from [outroStartSec] to the episode's end.
     */
    data class SkipTimestamps(
        val introStartSec: Int? = null,
        val introEndSec: Int? = null,
        val outroStartSec: Int? = null,
    ) {
        val hasIntro: Boolean get() = introStartSec != null && introEndSec != null && introEndSec > introStartSec
        val hasOutro: Boolean get() = outroStartSec != null && outroStartSec > 0
    }

    /** Reads the series' skip timestamps (all-unset when nothing is marked). */
    fun getSkipTimestamps(mainId: String): SkipTimestamps {
        if (mainId.isBlank()) return SkipTimestamps()
        val raw = store.getString(KEY_PREFIX_SKIP_TIMESTAMPS + mainId, "") ?: ""
        if (raw.isBlank()) return SkipTimestamps()
        val parts = raw.split('|')
        fun seg(i: Int): Int? = parts.getOrNull(i)?.toIntOrNull()?.takeIf { it > 0 }
        return SkipTimestamps(
            introStartSec = seg(0),
            introEndSec = seg(1),
            outroStartSec = seg(2),
        )
    }

    /** Persists (or — all-null [timestamps] — clears) the series' skip timestamps. */
    fun setSkipTimestamps(mainId: String, timestamps: SkipTimestamps) {
        if (mainId.isBlank()) return
        val key = KEY_PREFIX_SKIP_TIMESTAMPS + mainId
        val any = timestamps.hasIntro || timestamps.hasOutro
        if (!any) {
            store.delete(key)
        } else {
            val encoded = "${timestamps.introStartSec ?: ""}|${timestamps.introEndSec ?: ""}|${timestamps.outroStartSec ?: ""}"
            store.putString(key, encoded)
        }
    }

    // ── ROUND 121: per-EPISODE AniSkip data (the service-fed skip system) ────
    //
    // The round-121 device order replaced hand-marked stamps as the PRIMARY
    // skip source with the AniSkip service, whose data is PER EPISODE — so
    // the cache is keyed (mainId, episodeNumber). Encoded compactly as
    // "opStart|opEnd|edStart|edEnd" seconds (blank segments = that side has
    // no data). The per-series manual stamps (above) become the FALLBACK —
    // the editor sheet still writes them for sources AniSkip doesn't cover.

    /** The per-episode AniSkip intervals (seconds); either side may be null. */
    data class EpisodeSkipTimes(
        val introStartSec: Int? = null,
        val introEndSec: Int? = null,
        val outroStartSec: Int? = null,
        val outroEndSec: Int? = null,
    ) {
        val hasIntro: Boolean get() = introStartSec != null && introEndSec != null && introEndSec > introStartSec
        val hasOutro: Boolean get() = outroStartSec != null && outroEndSec != null && outroEndSec > outroStartSec
        val hasAny: Boolean get() = hasIntro || hasOutro
    }

    /** Reads the cached AniSkip intervals for one episode (null = not cached). */
    fun getEpisodeSkipTimes(mainId: String, episodeNumber: Int): EpisodeSkipTimes? {
        if (mainId.isBlank()) return null
        val raw = store.getString(KEY_PREFIX_EPISODE_SKIP + mainId + "_" + episodeNumber, "") ?: ""
        if (raw.isBlank()) return null
        val parts = raw.split('|')
        fun seg(i: Int): Int? = parts.getOrNull(i)?.toIntOrNull()?.takeIf { it > 0 }
        return EpisodeSkipTimes(
            introStartSec = seg(0),
            introEndSec = seg(1),
            outroStartSec = seg(2),
            outroEndSec = seg(3),
        )
    }

    /**
     * Persists the AniSkip intervals for one episode (null/no-data [times]
     * clears the cache entry — the negative latch below prevents refetch
     * loops for anime the service doesn't cover).
     */
    fun setEpisodeSkipTimes(mainId: String, episodeNumber: Int, times: EpisodeSkipTimes?) {
        if (mainId.isBlank()) return
        val key = KEY_PREFIX_EPISODE_SKIP + mainId + "_" + episodeNumber
        if (times == null || !times.hasAny) {
            store.delete(key)
        } else {
            val encoded = "${times.introStartSec ?: ""}|${times.introEndSec ?: ""}|${times.outroStartSec ?: ""}|${times.outroEndSec ?: ""}"
            store.putString(key, encoded)
        }
    }

    /**
     * The negative latch: AniSkip was asked about this episode and answered
     * (hit OR miss) — prevents re-asking on every open of an uncovered anime.
     */
    fun isEpisodeSkipFetched(mainId: String, episodeNumber: Int): Boolean {
        if (mainId.isBlank()) return false
        return store.getBoolean(KEY_PREFIX_EPISODE_SKIP_ASKED + mainId + "_" + episodeNumber, false)
    }

    /** Marks an episode as asked (true on ANY completed fetch, hit or miss). */
    fun markEpisodeSkipFetched(mainId: String, episodeNumber: Int) {
        if (mainId.isBlank()) return
        store.putBoolean(KEY_PREFIX_EPISODE_SKIP_ASKED + mainId + "_" + episodeNumber, true)
    }

    // ── ROUND 130 (D-804): the DURABLE Up Next dismissals ────────────────────
    // The in-session dismissal map in WatchScreen dies with the process. This
    // ordered string list (one episode key per line, newest last, capped)
    // survives process death so a dismissed card stays dismissed after the
    // app is killed mid-episode and reopened at the resume position. The
    // re-arm semantics live at the call sites: a seek back out of the end
    // zone REMOVES the key (also durably), and a genuine fresh replay (the
    // resume decision says "start at beginning") clears it too — a new
    // playback attempt from zero is a fresh chance, exactly like in-session.
    fun getDurableUpNextDismissals(): Set<String> {
        val raw = store.getString(KEY_UPNEXT_DISMISSED, "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }.toSet()
    }

    fun addDurableUpNextDismissal(key: String) {
        if (key.isBlank()) return
        val current = (store.getString(KEY_UPNEXT_DISMISSED, "") ?: "")
            .split("\n").filter { it.isNotBlank() }
        val updated = (current - key) + key
        store.putString(KEY_UPNEXT_DISMISSED, updated.takeLast(UPNEXT_DISMISS_MAX).joinToString("\n"))
    }

    fun removeDurableUpNextDismissal(key: String) {
        val current = (store.getString(KEY_UPNEXT_DISMISSED, "") ?: "")
            .split("\n").filter { it.isNotBlank() }
        if (key !in current) return
        store.putString(KEY_UPNEXT_DISMISSED, (current - key).joinToString("\n"))
    }

    companion object {
        private const val KEY_UPNEXT_DISMISSED = "pref_upnext_dismissed_episodes"
        private const val UPNEXT_DISMISS_MAX = 256
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
        private const val KEY_PREFIX_SUB_TRACK_MEMORY = "pref_sub_track_memory_"
        private const val KEY_PREFIX_SKIP_TIMESTAMPS = "pref_skip_timestamps_"
        private const val KEY_PREFIX_EPISODE_SKIP = "pref_episode_skip_"
        // ROUND 123 (R5): the "_v2" suffix ONE-TIME invalidates every latch the
        // broken GET-based client (rounds 121-122) wrote — those episodes are
        // permanently marked "asked" with no data, so the fixed POST client
        // never got a chance to ask again (the "AniSkip still not working"
        // device report: the same anime stayed silent across the fix).
        // ROUND 130: the "_v3" suffix — the same rescue for the latches the
        // round-123 client wrote on TRANSIENT failures (the client collapsed
        // network errors into the same null as a definitive "no data", and the
        // caller latched both). Every poisoned v2 latch expires on update; the
        // new client only latches definitive answers, so this is the last key.
        private const val KEY_PREFIX_EPISODE_SKIP_ASKED = "pref_episode_skip_asked_v3_"
    }
}
