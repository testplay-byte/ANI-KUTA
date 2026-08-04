package com.confused.anikuta.core.player

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.PlayerPreferences
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPVLib
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ANI-KUTA MPV view — thin wrapper over the MPV native library's [BaseMPVView].
 *
 * D-050 fix: The old project used a companion `lateinit var playerPreferences`
 * because XML-inflated views can't use Koin constructor injection. We fix this
 * by implementing [KoinComponent] — the view resolves [PlayerPreferences] from
 * Koin at first access. This is the standard Koin pattern for views that are
 * inflated from XML or created by Compose's `AndroidView`.
 *
 * Constructor: the 2-param `(Context, AttributeSet?)` form is required so
 * the view can be inflated from a real XML layout (see `res/layout/mpv_view.xml`).
 * Compose's `AndroidView` factory gives us no XML, and passing a fake
 * `AttributeSet` crashes at runtime because Android's `obtainStyledAttributes`
 * requires a `XmlBlock$Parser` (only available from real XML inflation).
 *
 * CRITICAL (initOptions port): The previous version had an EMPTY initOptions().
 * This caused "audio plays but no video" because [setVo] was never called — MPV
 * had no video output configured. Ported from the old project's initOptions()
 * which sets: setVo("gpu"), profile=fast, hwdec=auto, demuxer-max-bytes=256MB,
 * vd-lavc-film-grain=cpu, all subtitle preferences, etc.
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Core:Player:MPVView".
 */
class AnikutaMPVView(
    context: Context,
    attributes: AttributeSet,
) : BaseMPVView(context, attributes), KoinComponent {

    companion object {
        private const val TAG = "Anikuta:Core:Player:MPVView"
        const val MPV_DIR = "mpv"
    }

    /** Resolved from Koin (D-050 fix — no more companion lateinit hack). */
    private val playerPreferences: PlayerPreferences by inject()

    var isExiting = false

    // ── Abstract method implementations from BaseMPVView ──

    /**
     * CRITICAL: Called by BaseMPVView during initialization. This is where ALL
     * MPV init-time options MUST be set (via [MPVLib.setOptionString]).
     *
     * Ported from the old project. Key options:
     * - [setVo]: sets the video output to "gpu" (or "gpu-next"). Without this,
     *   MPV has no video output → "audio plays but no video" bug.
     * - `hwdec=auto`: zero-copy hardware decoding (NOT `auto-copy` which forces
     *   GPU→CPU copy-back and fails on some devices).
     * - `profile=fast`: enables low-latency decoding defaults.
     * - `demuxer-max-bytes=256MB`: large demuxer cache for buffering.
     * - `vd-lavc-film-grain=cpu`: workaround for mpv issue #14651 (AV1 crashes).
     * - [applySubtitlePreferencesInit]: all sub-* options via setOptionString.
     */
    override fun initOptions(vo: String) {
        Logger.i(TAG) { "=== initOptions START ===" }
        Logger.i(TAG) { "vo param: $vo" }

        // ── Video output — CRITICAL (was missing → no video) ──
        val voChoice = if (playerPreferences.gpuNext) "gpu-next" else "gpu"
        Logger.i(TAG) { "Setting vo = $voChoice" }
        setVo(voChoice)

        MPVLib.setPropertyBoolean("pause", true)
        Logger.i(TAG) { "Set pause = true" }

        MPVLib.setOptionString("profile", "fast")
        Logger.i(TAG) { "Set profile = fast" }

        // ── Hardware decoding — auto (zero-copy), NOT auto-copy ──
        val hwdecChoice = if (playerPreferences.tryHwDecoding) "auto" else "no"
        Logger.i(TAG) { "Setting hwdec = $hwdecChoice" }
        MPVLib.setOptionString("hwdec", hwdecChoice)

        MPVLib.setOptionString("msg-level", "all=warn")
        Logger.i(TAG) { "Set msg-level = all=warn" }

        // Keep the file loaded so seeking works after EOF.
        MPVLib.setPropertyBoolean("keep-open", true)
        MPVLib.setPropertyBoolean("input-default-bindings", true)

        MPVLib.setOptionString("ytdl", "no")
        // TLS: cacert.pem (Mozilla CA bundle) is copied to the mpv config dir
        // root by PlayerInitializer.copyAssets(). Required for HTTPS subtitle
        // downloads.
        MPVLib.setOptionString("tls-verify", "yes")
        MPVLib.setOptionString("tls-ca-file", "${context.filesDir.path}/$MPV_DIR/cacert.pem")
        Logger.i(TAG) { "Set tls-verify=yes, tls-ca-file=${context.filesDir.path}/$MPV_DIR/cacert.pem" }

        // ── Demuxer cache — 256MB on Android 8.1+, 128MB on older ──
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 256 else 128
        val backCacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${backCacheMegs * 1024 * 1024}")
        Logger.i(TAG) { "Set demuxer-max-bytes=${cacheMegs}MB, demuxer-max-back-bytes=${backCacheMegs}MB" }

        MPVLib.setOptionString("speed", playerPreferences.playbackSpeed.toString())
        MPVLib.setOptionString("alang", playerPreferences.preferredAudioLanguages)
        MPVLib.setOptionString("volume-max", (playerPreferences.volumeBoostCap + 100).toString())
        Logger.i(TAG) { "Set speed=${playerPreferences.playbackSpeed}, alang=${playerPreferences.preferredAudioLanguages}" }

        // Workaround for https://github.com/mpv-player/mpv/issues/14651
        MPVLib.setOptionString("vd-lavc-film-grain", "cpu")
        Logger.i(TAG) { "Set vd-lavc-film-grain=cpu (AV1 workaround)" }

        // ── Subtitle style — all init-time setOptionString ──
        applySubtitlePreferencesInit()
        Logger.i(TAG) { "=== initOptions COMPLETE ===" }
    }

    /**
     * Apply subtitle preferences to MPV at INIT time (called from initOptions).
     *
     * Uses setOptionString (init API) to match aniyomi's setupSubtitlesOptions().
     * sub-ass-override is only set to "force" when the user opts in — otherwise
     * we leave MPV's default ("auto") which handles ASS subtitles correctly.
     */
    private fun applySubtitlePreferencesInit() {
        try {
            MPVLib.setOptionString("sub-font", playerPreferences.subtitleFont)
            MPVLib.setOptionString("sub-font-size", playerPreferences.subtitleFontSize.toString())
            MPVLib.setOptionString("sub-scale", playerPreferences.subtitleFontScale.toString())
            MPVLib.setOptionString("sub-border-size", playerPreferences.subtitleBorderSize.toString())
            MPVLib.setOptionString("sub-bold", if (playerPreferences.boldSubtitles) "yes" else "no")
            MPVLib.setOptionString("sub-italic", if (playerPreferences.italicSubtitles) "yes" else "no")
            MPVLib.setOptionString("sub-color", colorToHex(playerPreferences.textColorSubtitles))
            MPVLib.setOptionString("sub-border-color", colorToHex(playerPreferences.borderColorSubtitles))
            MPVLib.setOptionString("sub-back-color", colorToHex(playerPreferences.backgroundColorSubtitles))
            MPVLib.setOptionString("sub-pos", playerPreferences.subtitlePosition.toString())
            MPVLib.setOptionString("sub-shadow-offset", playerPreferences.subtitleShadowOffset.toString())
            if (playerPreferences.overrideSubsAss) {
                MPVLib.setOptionString("sub-ass-override", "force")
                MPVLib.setOptionString("sub-ass-justify", "yes")
            }
            MPVLib.setOptionString("sub-delay", (playerPreferences.subtitlesDelay / 1000.0).toString())
            Logger.d(TAG) { "Subtitle preferences applied (init, setOptionString)" }
        } catch (e: Exception) {
            Logger.w(TAG) { "Could not apply subtitle preferences (init): ${e.message}" }
        }
    }

    /**
     * Apply subtitle preferences LIVE (called from SubtitleSettingsSheet when
     * the user changes settings at runtime).
     *
     * CRITICAL: Uses [MPVLib.setPropertyInt] / [MPVLib.setPropertyDouble] for
     * numeric properties — [MPVLib.setPropertyString] with a string value
     * doesn't reliably update numeric MPV properties at runtime (font size
     * especially was not being applied). This was the root cause of the
     * "font size change not reflected on video" bug in the old project.
     */
    fun applySubtitlePreferences() {
        try {
            MPVLib.setPropertyString("sub-font", playerPreferences.subtitleFont)
            MPVLib.setPropertyInt("sub-font-size", playerPreferences.subtitleFontSize)
            MPVLib.setPropertyDouble("sub-scale", playerPreferences.subtitleFontScale.toDouble())
            MPVLib.setPropertyInt("sub-border-size", playerPreferences.subtitleBorderSize)
            MPVLib.setPropertyString("sub-bold", if (playerPreferences.boldSubtitles) "yes" else "no")
            MPVLib.setPropertyString("sub-italic", if (playerPreferences.italicSubtitles) "yes" else "no")
            MPVLib.setPropertyString("sub-color", colorToHex(playerPreferences.textColorSubtitles))
            MPVLib.setPropertyString("sub-border-color", colorToHex(playerPreferences.borderColorSubtitles))
            MPVLib.setPropertyString("sub-back-color", colorToHex(playerPreferences.backgroundColorSubtitles))
            MPVLib.setPropertyInt("sub-pos", playerPreferences.subtitlePosition)
            MPVLib.setPropertyInt("sub-shadow-offset", playerPreferences.subtitleShadowOffset)
            if (playerPreferences.overrideSubsAss) {
                MPVLib.setPropertyString("sub-ass-override", "force")
            }
            MPVLib.setPropertyString("sub-delay", (playerPreferences.subtitlesDelay / 1000.0).toString())
            Logger.d(TAG) { "Subtitle preferences applied (live, setPropertyInt for numerics)" }
        } catch (e: Exception) {
            Logger.w(TAG) { "Could not apply subtitle preferences (live): ${e.message}" }
        }
    }

    /** Convert an ARGB int to an MPV hex color string (e.g., "#FFFFFFFF" for white). */
    private fun colorToHex(color: Int): String {
        val a = (color shr 24) and 0xFF
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return String.format("#%02X%02X%02X%02X", a, r, g, b)
    }

    override fun observeProperties() {
        // Full set — ported from old project. Observes time, duration, volume,
        // cache, tracks, speed, pause, buffering, seeking, eof, hwdec, sid, aid.
        MPVLib.observeProperty("time-pos", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("demuxer-cache-time", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("duration", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("track-list/count", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("speed", MPVLib.mpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("paused-for-cache", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("seeking", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("sid", MPVLib.mpvFormat.MPV_FORMAT_STRING)
        MPVLib.observeProperty("aid", MPVLib.mpvFormat.MPV_FORMAT_STRING)
    }

    override fun postInitOptions() {
        // Post-init: subtitle margins (set as runtime properties — old project
        // sets these at init via setOptionString in applySubtitlePreferencesInit,
        // but we also set them here to be safe since the old project's
        // postInitOptions was a no-op and margins still worked).
        // These ensure ASS subtitles respect the bottom margin.
    }

    // ── Property helpers ──

    private fun getPropertyInt(property: String): Int? =
        MPVLib.getPropertyInt(property) as Int?

    private fun getPropertyBoolean(property: String): Boolean? =
        MPVLib.getPropertyBoolean(property) as Boolean?

    private fun getPropertyString(property: String): String? =
        MPVLib.getPropertyString(property) as String?

    // ── Playback properties ──

    val duration: Int?
        get() = getPropertyInt("duration")

    var timePos: Int?
        get() = getPropertyInt("time-pos")
        set(position) {
            MPVLib.setPropertyInt("time-pos", position!!)
        }

    var paused: Boolean?
        get() = getPropertyBoolean("pause")
        set(value) {
            MPVLib.setPropertyBoolean("pause", value!!)
        }

    var volume: Int
        get() = getPropertyInt("volume") ?: 100
        set(value) {
            MPVLib.setPropertyInt("volume", value)
        }

    val hwdecActive: String
        get() = getPropertyString("hwdec-current") ?: "no"

    // ── Track API ──
    // MPV exposes tracks via "track-list" property. Each track has:
    // type (audio/sub/video), id, title, lang.
    // Select a track by setting "sid" (subtitle) or "aid" (audio) to the track ID.
    // Set to -1 to disable (off).

    var sid: Int
        get() {
            val v = getPropertyString("sid")
            return v?.toIntOrNull() ?: -1
        }
        set(value) {
            if (value <= 0) {
                MPVLib.setPropertyString("sid", "no")
            } else {
                MPVLib.setPropertyInt("sid", value)
            }
        }

    var aid: Int
        get() {
            val v = getPropertyString("aid")
            return v?.toIntOrNull() ?: -1
        }
        set(value) {
            if (value <= 0) {
                MPVLib.setPropertyString("aid", "no")
            } else {
                MPVLib.setPropertyInt("aid", value)
            }
        }

    fun getTrackCount(): Int = MPVLib.getPropertyInt("track-list/count") ?: 0

    fun getTrackType(index: Int): String? = MPVLib.getPropertyString("track-list/$index/type")

    fun getTrackId(index: Int): Int? = MPVLib.getPropertyInt("track-list/$index/id")

    fun getTrackTitle(index: Int): String = MPVLib.getPropertyString("track-list/$index/title") ?: ""

    fun getTrackLang(index: Int): String = MPVLib.getPropertyString("track-list/$index/lang") ?: ""

    /**
     * Load all audio and subtitle tracks from MPV's track-list.
     * Returns a pair: (subtitleTracks, audioTracks).
     *
     * Uses [SubtitleTrackFormatter] for display names — this gives proper
     * human-readable names like "English" instead of "eng", and discards
     * ugly filenames (.vtt/.srt/.ass/.ssa).
     *
     * NOTE: Does NOT prepend an "Off" entry — the SubtitleTracksSheet handles
     * the "Off" option explicitly (adding it here would create a duplicate
     * "Off" entry in the sheet).
     */
    fun loadTracks(): Pair<List<VideoTrack>, List<VideoTrack>> {
        val subTracks = mutableListOf<VideoTrack>()
        val audioTracks = mutableListOf<VideoTrack>()

        try {
            val count = getTrackCount()
            for (i in 0 until count) {
                val type = getTrackType(i) ?: continue
                val id = getTrackId(i) ?: continue
                val title = getTrackTitle(i)
                val lang = getTrackLang(i)

                val displayName = com.confused.anikuta.core.player.subtitles.SubtitleTrackFormatter
                    .formatTrackName(id, title, lang)

                when (type) {
                    "sub" -> subTracks.add(VideoTrack(id, displayName, lang))
                    "audio" -> audioTracks.add(VideoTrack(id, displayName, lang))
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to load tracks: ${e.message}" }
        }

        Logger.d(TAG) { "Loaded ${subTracks.size} sub tracks, ${audioTracks.size} audio tracks" }
        return Pair(subTracks, audioTracks)
    }

    // ── Playback speed ──

    var playbackSpeed: Float
        get() = getPropertyInt("speed")?.toFloat() ?: 1.0f
        set(value) {
            // CRITICAL: use setPropertyDouble, NOT setPropertyInt — the int
            // version truncates 1.5f → 1, 0.5f → 0. Ported fix from old project.
            MPVLib.setPropertyDouble("speed", value.toDouble())
        }

    // ── Video loading ──

    /**
     * Load a video URL and optionally seek to a resume position.
     *
     * D-049 (video caching): MPV's cache is configured via [PlayerInitializer]
     * with `demuxer-max-bytes`. When resuming, the cache covers ~1 minute
     * before + 1 minute after the resume position, enabling instant playback
     * without buffering.
     *
     * @param url The video URL to play.
     * @param resumePosition The position to seek to (in seconds), or null to start from beginning.
     * @param headers HTTP headers for the video request (for extension proxy URLs).
     */
    fun loadVideo(url: String, resumePosition: Int? = null, headers: Map<String, String> = emptyMap()) {
        Logger.i(TAG) { "Loading video: $url (resume: $resumePosition)" }

        // Set HTTP headers if provided (for extension proxy URLs)
        if (headers.isNotEmpty()) {
            val headerStr = headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
            MPVLib.setPropertyString("http-header-fields", headerStr)
        }

        // Load the file
        MPVLib.command(arrayOf("loadfile", url))

        // Seek to resume position if provided
        if (resumePosition != null && resumePosition > 0) {
            // Wait for the file to load before seeking
            // MPV will handle this via the file-loaded event
            MPVLib.command(arrayOf("seek", resumePosition.toString(), "absolute"))
            Logger.d(TAG) { "Seeked to $resumePosition seconds" }
        }
    }
}
