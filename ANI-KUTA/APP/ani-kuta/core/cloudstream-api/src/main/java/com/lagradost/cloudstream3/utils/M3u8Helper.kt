// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// Task 47 (playback session): the REAL m3u8 helper. 20/80 census plugins (incl.
// AllMovieLandProvider + AniKoto, the user's test set) call
// `M3u8Helper.generateM3u8(...)` from loadLinks to fan out one ExtractorLink
// per quality variant of a master playlist. Behavior (from the documented HLS
// spec + the documented CloudStream contract):
//   • fetch the playlist with the caller's headers AS-IS — NO referer param
//     (Task 53 / RC-1: passing referer = the stream URL made nicehttp REPLACE
//     the caller's Referer header; CDNs like cdn.kryntal.top then 403 the
//     request and the plugin silently resolves 0 links. Upstream passes only
//     `headers = m3u8.headers, verify = false` — we now match that exactly);
//   • anything not starting with #EXTM3U is not a playlist → ErrorLoadingException;
//   • MASTER playlists (#EXT-X-STREAM-INF) → one M3u8Stream per variant, quality
//     = the RESOLUTION height, variant URIs absolutized against the playlist URL;
//   • MEDIA playlists (no variants) → the input stream as-is when returnThis,
//     else nothing;
//   • duplicate qualities collapse to the first occurrence.
//
// Task 49 (round 9 — HLS quality selection): the variant-parsing loop is
// extracted into the PURE [parseMasterPlaylist] so the BRIDGE can reuse it
// (master→variant expansion for providers that hand back an unexpanded master
// link) and unit-test it without HTTP. The plugin-facing surface
// (generateM3u8 / m3u8Generation / M3u8Stream) is byte-for-byte compatible —
// plugin dexes call exactly those members.
package com.lagradost.cloudstream3.utils

import com.lagradost.api.Log
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.app

/** Backwards api surface. */
class M3u8Helper {
    companion object {
        /** One-filter logcat recipe member: Anikuta:CS:*. */
        internal const val M3U8_TAG = "Anikuta:CS:M3u8"

        /** RESOLUTION=<width>x<height> inside an #EXT-X-STREAM-INF tag. */
        private val RESOLUTION_REGEX = Regex("""RESOLUTION=(\d+)x(\d+)""")

        /** Resolves a (possibly relative) playlist URI against its base URL. */
        internal fun absolutize(uri: String, baseUrl: String): String = runCatching {
            java.net.URI(baseUrl).resolve(uri).toString()
        }.getOrDefault(uri)

        suspend fun generateM3u8(
            source: String,
            streamUrl: String,
            referer: String,
            quality: Int? = null,
            headers: Map<String, String> = mapOf(),
            name: String = source,
        ): List<ExtractorLink> {
            val streams = M3u8Helper().m3u8Generation(
                M3u8Stream(streamUrl = streamUrl, quality = quality, headers = headers),
            )
            return streams.map { stream ->
                ExtractorLink(
                    source = source,
                    name = "$name ${Qualities.getStringByInt(stream.quality)}".trim(),
                    url = stream.streamUrl,
                    referer = referer,
                    quality = stream.quality ?: Qualities.Unknown.value,
                    headers = headers,
                    type = ExtractorLinkType.M3U8,
                )
            }
        }

        /**
         * Task 49: PURE master-playlist parser (no HTTP — testable). Returns the
         * quality variants of a MASTER playlist with absolutized URIs, or an
         * EMPTY list for media playlists / anything without variants.
         */
        fun parseMasterPlaylist(
            playlistText: String,
            playlistUrl: String,
            fallbackQuality: Int? = null,
        ): List<M3u8Stream> {
            val lines = playlistText.lines()
            val streams = mutableListOf<M3u8Stream>()
            val seenQualities = mutableSetOf<Int>()
            var index = 0
            while (index < lines.size) {
                val line = lines[index].trim()
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    // The next non-comment, non-blank line is the variant URI.
                    var uriIndex = index + 1
                    while (uriIndex < lines.size &&
                        (lines[uriIndex].isBlank() || lines[uriIndex].startsWith("#"))
                    ) {
                        uriIndex++
                    }
                    if (uriIndex < lines.size) {
                        val variantUri = lines[uriIndex].trim()
                        val height = RESOLUTION_REGEX.find(line)
                            ?.groupValues?.get(2)?.toIntOrNull()
                        val quality = height ?: fallbackQuality
                        // Collapse duplicate qualities (CDNs sometimes list the
                        // same resolution twice with different bandwidths).
                        if (quality == null || seenQualities.add(quality)) {
                            streams.add(
                                M3u8Stream(
                                    streamUrl = absolutize(variantUri, playlistUrl),
                                    quality = quality,
                                ),
                            )
                        }
                    }
                    index = uriIndex + 1
                } else {
                    index++
                }
            }
            return streams
        }
    }

    data class M3u8Stream(
        val streamUrl: String,
        val quality: Int? = null,
        val headers: Map<String, String> = mapOf(),
    )

    /**
     * Resolves [m3u8] into its list of quality streams. A master playlist
     * yields every variant; a media playlist yields the input itself (when
     * [returnThis]) because it is already the playable stream.
     *
     * Task 53 / RC-1: the request carries ONLY the caller's headers (upstream
     * parity — no referer param, verify=false for lenient SSL). Every outcome
     * is logged under Anikuta:CS:M3u8 so a CDN rejection is diagnosable from
     * logcat instead of silently zeroing a provider's links.
     */
    suspend fun m3u8Generation(m3u8: M3u8Stream, returnThis: Boolean? = true): List<M3u8Stream> {
        val response = try {
            app.get(m3u8.streamUrl, headers = m3u8.headers, verify = false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(
                M3U8_TAG,
                "m3u8 request FAILED ${m3u8.streamUrl.take(96)} " +
                    "headers=${m3u8.headers.keys}: ${e::class.java.simpleName}: ${e.message}",
            )
            throw ErrorLoadingException("m3u8 request failed: ${e.message}")
        }
        val text = response.text
        if (!text.startsWith("#EXTM3U")) {
            Log.w(
                M3U8_TAG,
                "not an m3u8 body (http=${response.code}) ${m3u8.streamUrl.take(96)} " +
                    "headers=${m3u8.headers.keys} body=" +
                    text.take(90).replace('\n', ' ').replace('\r', ' '),
            )
            throw ErrorLoadingException("Not m3u8")
        }
        val variants = parseMasterPlaylist(text, m3u8.streamUrl, m3u8.quality)
        val kind = if (variants.isNotEmpty()) "master" else "media playlist"
        Log.i(M3U8_TAG, "m3u8 ok ${m3u8.streamUrl.take(72)} variants=${variants.size} ($kind)")
        if (variants.isEmpty()) {
            // Media playlist (or an unparseable master) — the input IS the stream.
            return if (returnThis == true) listOf(m3u8) else emptyList()
        }
        return variants
    }
}
