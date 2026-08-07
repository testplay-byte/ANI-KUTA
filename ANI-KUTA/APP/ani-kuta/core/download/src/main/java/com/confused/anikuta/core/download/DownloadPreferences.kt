package com.confused.anikuta.core.download

import com.confused.anikuta.core.preferences.BooleanSerializer
import com.confused.anikuta.core.preferences.IntSerializer
import com.confused.anikuta.core.preferences.Preference
import com.confused.anikuta.core.preferences.PreferenceStore
import com.confused.anikuta.core.preferences.StringListSerializer
import com.confused.anikuta.core.preferences.StringSerializer
import com.confused.anikuta.core.preferences.preference

/**
 * All download-related preferences (17 settings — the old 15 + 2 NEW).
 *
 * D.1.6: uses the reactive [PreferenceStore] API ([Preference.changes] returns
 * a [Flow] for Compose `collectAsState`). The drag-reorder settings UI (D.5)
 * depends on this reactivity.
 *
 * The NEW settings (D.1.6 + REVIEW-5 M44/M45):
 *  - [dimensionPriority] — the user-reorderable priority list [AUDIO, QUALITY, SERVER].
 *  - [globalFallback] — what to do when no candidate matches any preference.
 */
class DownloadPreferences(private val store: PreferenceStore) {

    // ── Concurrency ──────────────────────────────────────────────────────────

    /** Number of concurrent downloads (1..5, default 1). */
    val concurrentDownloads = store.preference(
        "pref_dl_concurrent", 1, IntSerializer,
    )

    // ── Network ──────────────────────────────────────────────────────────────

    /** Only download over Wi-Fi (auto-pause on metered network). */
    val wifiOnly = store.preference(
        "pref_dl_wifi_only", false, BooleanSerializer,
    )

    // ── Auto-download ────────────────────────────────────────────────────────

    /** Automatically download new episodes when they're detected. */
    val autoDownload = store.preference(
        "pref_dl_auto", false, BooleanSerializer,
    )

    /** How many new episodes to auto-download at once (1..10, default 3). */
    val autoDownloadNew = store.preference(
        "pref_dl_auto_new", 3, IntSerializer,
    )

    // ── Preferred qualities (drag-reorderable list) ──────────────────────────

    /** Ordered list of preferred qualities (e.g. ["1080p", "720p", "480p", "360p"]). */
    val preferredQualities = store.preference(
        "pref_dl_qualities", listOf("1080p", "720p", "480p", "360p"), StringListSerializer,
    )

    /** Fallback strategy when the preferred quality is unavailable. */
    val qualityFallback = store.preference(
        "pref_dl_quality_fallback", "TRY_NEXT", StringSerializer,
    )

    // ── Preferred audio (drag-reorderable list) ──────────────────────────────

    /** Ordered list of preferred audio versions (e.g. ["SUB", "DUB", "HSUB"]). */
    val preferredAudio = store.preference(
        "pref_dl_audio", listOf("SUB", "DUB", "HSUB"), StringListSerializer,
    )

    /** Fallback strategy when the preferred audio is unavailable. */
    val audioFallback = store.preference(
        "pref_dl_audio_fallback", "TRY_NEXT", StringSerializer,
    )

    // ── Preferred servers (drag-reorderable list, per-extension) ─────────────

    /** Ordered list of preferred servers (e.g. ["Streamtape", "Vidstreaming"]). */
    val preferredServers = store.preference(
        "pref_dl_servers", listOf("Streamtape", "Vidstreaming", "Mp4Upload"), StringListSerializer,
    )

    /** Fallback strategy when the preferred server is unavailable. */
    val serverFallback = store.preference(
        "pref_dl_server_fallback", "TRY_NEXT", StringSerializer,
    )

    // ── NEW: Dimension priority (D.1.6 + REVIEW-5 M44/M45) ───────────────────
    //
    // The user-reorderable priority list [AUDIO, QUALITY, SERVER]. This is the
    // KEY new feature — the old project hardcoded the priority order (inconsistently
    // — see 14-auto-download-engine.md §4). The NEW engine uses this list to
    // determine which dimension matters most when resolving conflicts.
    //
    // Default: ["AUDIO", "QUALITY", "SERVER"] — audio is most important to most
    // users (dub vs sub is a hard preference; quality/server are softer).

    /** The priority order of the 3 preference dimensions. */
    val dimensionPriority = store.preference(
        "pref_dl_dimension_priority",
        listOf("AUDIO", "QUALITY", "SERVER"),
        StringListSerializer,
    )

    /**
     * What to do when NO candidate matches ANY preference (all preferred values
     * are unavailable across all dimensions):
     *  - "BEST_EFFORT" — download the least-bad option (default).
     *  - "ASK" — show the video picker sheet (let the user choose).
     *  - "DO_NOT_DOWNLOAD" — don't download.
     *
     * REVIEW-D0 I2 (REVIEW-2 C2): fires based on the picked candidate's MATCH
     * QUALITY (not on isEmpty) — if the best candidate has at least one
     * non-preferred value, ASK/DO_NOT_DOWNLOAD fires.
     */
    val globalFallback = store.preference(
        "pref_dl_global_fallback", "BEST_EFFORT", StringSerializer,
    )

    // ── Advanced downloader ──────────────────────────────────────────────────

    /** Use the multi-threaded advanced downloader for faster downloads. */
    val advancedDownloader = store.preference(
        "pref_dl_adv_enabled", false, BooleanSerializer,
    )

    /** Number of parallel connections for the advanced downloader (1..8, default 4). */
    val advancedThreads = store.preference(
        "pref_dl_adv_threads", 4, IntSerializer,
    )

    /**
     * Max retry attempts for the advanced downloader (0..10, default 10).
     * REVIEW-5 M52 (fixes the old project's default mismatch: code=25, UI=0..10).
     */
    val advancedMaxRetries = store.preference(
        "pref_dl_adv_retries", 10, IntSerializer,
    )

    // ── Storage ──────────────────────────────────────────────────────────────

    /** The SAF tree URI of the user-selected download folder (null = not set). */
    val downloadFolderUri = store.preference(
        "pref_dl_folder_uri", "", StringSerializer,
    )
}
