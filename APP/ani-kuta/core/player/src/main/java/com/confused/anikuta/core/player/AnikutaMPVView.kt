package com.confused.anikuta.core.player

import android.content.Context
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
 * CORE_RULES §20: All operations logged with tag "Anikuta:Core:Player:MPVView".
 */
class AnikutaMPVView(
    context: Context,
    attributes: AttributeSet,
) : BaseMPVView(context, attributes), KoinComponent {

    companion object {
        private const val TAG = "Anikuta:Core:Player:MPVView"
    }

    /** Resolved from Koin (D-050 fix — no more companion lateinit hack). */
    private val playerPreferences: PlayerPreferences by inject()

    var isExiting = false

    // ── Abstract method implementations from BaseMPVView ──

    override fun initOptions(vo: String) {
        // No custom init options needed — configured via PlayerInitializer.writeConfig()
    }

    override fun observeProperties() {
        // Register property observers — MPV will call back when these change
        MPVLib.observeProperty("time-pos", "Integer")
        MPVLib.observeProperty("duration", "Integer")
        MPVLib.observeProperty("pause", "Boolean")
        MPVLib.observeProperty("paused-for-cache", "Boolean")
        MPVLib.observeProperty("speed", "Double")
        MPVLib.observeProperty("sid", "String")
        MPVLib.observeProperty("aid", "String")
        MPVLib.observeProperty("track-list/count", "Integer")
    }

    override fun postInitOptions() {
        // Post-init configuration — applied after MPV is initialized
        // Subtitle margins (from old project — subtitle rendering fix)
        MPVLib.setPropertyString("sub-ass-force-margins", "yes")
        MPVLib.setPropertyString("sub-use-margins", "yes")
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
     * Each list includes an "Off" entry (id = -1) at the start.
     */
    fun loadTracks(): Pair<List<VideoTrack>, List<VideoTrack>> {
        val subTracks = mutableListOf(VideoTrack(-1, "Off", null))
        val audioTracks = mutableListOf(VideoTrack(-1, "Off", null))

        try {
            val count = getTrackCount()
            for (i in 0 until count) {
                val type = getTrackType(i) ?: continue
                val id = getTrackId(i) ?: continue
                val title = getTrackTitle(i)
                val lang = getTrackLang(i)

                val displayName = buildDisplayName(title, lang)

                when (type) {
                    "sub" -> subTracks.add(VideoTrack(id, displayName, lang))
                    "audio" -> audioTracks.add(VideoTrack(id, displayName, lang))
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to load tracks: ${e.message}" }
        }

        Logger.d(TAG) { "Loaded ${subTracks.size - 1} sub tracks, ${audioTracks.size - 1} audio tracks" }
        return Pair(subTracks, audioTracks)
    }

    private fun buildDisplayName(title: String, lang: String?): String {
        // Prefer language; only fall back to title if it looks human-readable
        if (lang != null && lang.isNotEmpty()) {
            return if (title.isNotEmpty() && !title.contains(".vtt") && !title.contains(".srt")) {
                "$lang ($title)"
            } else {
                lang.uppercase()
            }
        }
        return if (title.isNotEmpty()) title else "Track"
    }

    // ── Playback speed ──

    var playbackSpeed: Float
        get() = getPropertyInt("speed")?.toFloat() ?: 1.0f
        set(value) {
            MPVLib.setPropertyInt("speed", value.toInt())
        }

    // ── Video loading ──

    /**
     * Load a video URL and optionally seek to a resume position.
     *
     * D-049 (video caching): MPV's cache is configured via [PlayerInitializer]
     * with `cache-secs` and `stream-cache-dir`. When resuming, the cache covers
     * ~1 minute before + 1 minute after the resume position, enabling instant
     * playback without buffering.
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
