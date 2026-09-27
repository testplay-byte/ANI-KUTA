// CLEAN-ROOM: the NewPipe-backed YouTube extraction below is original ANI-KUTA
// code written against the bundled NewPipeExtractor's public API (the interop
// facts every host must implement). The behavior mirrors what the recloudstream
// host ships (their library/src/jvmCommonMain YoutubeExtractor) because the
// plugin we must serve — recloudstream's YoutubeProvider — delegates its
// loadLinks to loadExtractor("https://youtube.com/watch?v=…"), which only
// reaches a REGISTERED ExtractorApi. No upstream source was copied.
//
// ROUND 98 (D-671) — THE YOUTUBE DISPATCH TARGET.
//
// The chain: YoutubeProvider.loadLinks(data) → loadExtractor(youtube URL) →
// this extractor → NewPipe StreamInfo → ExtractorLinks (+ audio tracks +
// subtitles). Without it the plugin would LOAD (the ServiceList classes exist)
// but every episode resolve would end in "no extractor for youtube.com".
//
// Live streams publish the HLS manifest; regular videos publish the ADAPTIVE
// video-only streams each carrying the full audio list as mergeable audio
// tracks (the CS player engine already builds a merged MediaSource from
// ExtractorLink.audioTracks — CsPlayerEngine.kt's audioSources pass), falling
// back to the muxed progressive streams when adaptive ones are absent
// (age-restricted / fallback clients).
package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.newAudioFile
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.network.ensureNewPipeInitialized
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

/** The NewPipe-powered YouTube link resolver (see the file header). */
open class YoutubeExtractor : ExtractorApi() {
    override val mainUrl = "https://www.youtube.com"
    override val name = "YouTube"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        // The runtime must exist before the first extraction (idempotent).
        ensureNewPipeInitialized()

        val videoId = extractYouTubeId(url)
        val watchUrl = "$mainUrl/watch?v=$videoId"

        val info = StreamInfo.getInfo(watchUrl)
        val isLive = info.streamType == StreamType.LIVE_STREAM ||
            info.streamType == StreamType.AUDIO_LIVE_STREAM ||
            info.streamType == StreamType.POST_LIVE_STREAM ||
            info.streamType == StreamType.POST_LIVE_AUDIO_STREAM

        if (isLive && info.hlsUrl != null) {
            callback(
                newExtractorLink(
                    source = name,
                    name = "YouTube Live",
                    url = info.hlsUrl,
                    type = ExtractorLinkType.M3U8,
                ),
            )
        } else {
            resolveRegularVideo(info, subtitleCallback, callback)
        }
    }

    /**
     * Adaptive (video-only) streams first — each carries the audio list as
     * mergeable tracks; the muxed progressive set is the fallback for clients
     * that only answer combined streams.
     */
    private suspend fun resolveRegularVideo(
        info: StreamInfo,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val audioStreams = info.audioStreams.orEmpty()
        val adaptive = info.videoOnlyStreams.orEmpty()

        if (adaptive.isNotEmpty()) {
            adaptive.forEach { video ->
                callback(
                    newExtractorLink(
                        source = name,
                        name = "YouTube ${readableCodec(video.codec)}",
                        url = video.content,
                    ) {
                        quality = video.height.takeIf { it > 0 } ?: Qualities.Unknown.value
                        headers = mapOf("User-Agent" to USER_AGENT)
                        audioTracks = audioStreams.map { newAudioFile(it.content) }
                    },
                )
            }
        } else {
            info.videoStreams.orEmpty().forEach { video ->
                callback(
                    newExtractorLink(
                        source = name,
                        name = "YouTube ${readableCodec(video.codec)}",
                        url = video.content,
                    ) {
                        quality = video.height.takeIf { it > 0 } ?: Qualities.Unknown.value
                        headers = mapOf("User-Agent" to USER_AGENT)
                    },
                )
            }
        }

        info.subtitles.orEmpty().forEach { subtitle ->
            subtitleCallback(
                newSubtitleFile(
                    lang = subtitle.displayLanguageName
                        ?: subtitle.languageTag
                        ?: "Unknown",
                    url = subtitle.content,
                ),
            )
        }
    }

    /** Pulls the 11-character video id out of any of YouTube's URL shapes. */
    private fun extractYouTubeId(url: String): String {
        val regex = Regex(
            "(?:youtu\\.be/|youtube(?:-nocookie)?\\.com/" +
                "(?:.*v=|v/|u/\\w/|embed/|shorts/|live/))([\\w-]{11})",
        )
        return regex.find(url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("Invalid YouTube URL: $url")
    }

    /** av01 → AV1, vp9 → VP9, avc1 → H264 — the codec families YouTube serves. */
    private fun readableCodec(codec: String?): String {
        if (codec.isNullOrBlank()) return ""
        return when {
            codec.startsWith("av01", ignoreCase = true) -> "AV1"
            codec.startsWith("vp9", ignoreCase = true) -> "VP9"
            codec.startsWith("vp09", ignoreCase = true) -> "VP9"
            codec.startsWith("avc1", ignoreCase = true) ||
                codec.startsWith("h264", ignoreCase = true) -> "H264"
            codec.startsWith("hev1", ignoreCase = true) ||
                codec.startsWith("hvc1", ignoreCase = true) ||
                codec.startsWith("hevc", ignoreCase = true) -> "H265"
            else -> codec.substringBefore('.').uppercase()
        }
    }
}

/** The short-link mirror (youtu.be/…). */
class YoutubeShortLinkExtractor : YoutubeExtractor() {
    override val mainUrl = "https://youtu.be"
}

/** The mobile-web mirror (m.youtube.com/…). */
class YoutubeMobileExtractor : YoutubeExtractor() {
    override val mainUrl = "https://m.youtube.com"
}

/** The embed/nocookie mirror (www.youtube-nocookie.com/…). */
class YoutubeNoCookieExtractor : YoutubeExtractor() {
    override val mainUrl = "https://www.youtube-nocookie.com"
}
