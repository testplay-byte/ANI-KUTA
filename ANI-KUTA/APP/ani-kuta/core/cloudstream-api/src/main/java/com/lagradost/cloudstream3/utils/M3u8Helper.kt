// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// Task 47 (playback session): the REAL m3u8 helper. 20/80 census plugins (incl.
// AllMovieLandProvider + AniKoto, the user's test set) call
// `M3u8Helper.generateM3u8(...)` from loadLinks to fan out one ExtractorLink
// per quality variant of a master playlist. Behavior (from the documented HLS
// spec + the documented CloudStream contract):
//   • fetch the playlist with the caller's headers (referer = the URL itself);
//   • anything not starting with #EXTM3U is not a playlist → ErrorLoadingException;
//   • MASTER playlists (#EXT-X-STREAM-INF) → one M3u8Stream per variant, quality
//     = the RESOLUTION height, variant URIs absolutized against the playlist URL;
//   • MEDIA playlists (no variants) → the input stream as-is when returnThis,
//     else nothing;
//   • duplicate qualities collapse to the first occurrence.
package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.app

/** Backwards api surface. */
class M3u8Helper {
    companion object {
        /** RESOLUTION=<width>x<height> inside an #EXT-X-STREAM-INF tag. */
        private val RESOLUTION_REGEX = Regex("""RESOLUTION=(\d+)x(\d+)""")

        /** Resolves a (possibly relative) playlist URI against its base URL. */
        private fun absolutize(uri: String, baseUrl: String): String = runCatching {
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
     */
    suspend fun m3u8Generation(m3u8: M3u8Stream, returnThis: Boolean? = true): List<M3u8Stream> {
        val response = try {
            app.get(m3u8.streamUrl, referer = m3u8.streamUrl, headers = m3u8.headers)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ErrorLoadingException("m3u8 request failed: ${e.message}")
        }
        val text = response.text
        if (!text.startsWith("#EXTM3U")) {
            throw ErrorLoadingException("Not m3u8")
        }

        val lines = text.lines()
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
                    val quality = height ?: m3u8.quality
                    // Collapse duplicate qualities (CDNs sometimes list the
                    // same resolution twice with different bandwidths).
                    if (quality == null || seenQualities.add(quality)) {
                        streams.add(
                            M3u8Stream(
                                streamUrl = absolutize(variantUri, m3u8.streamUrl),
                                quality = quality,
                                headers = m3u8.headers,
                            ),
                        )
                    }
                }
                index = uriIndex + 1
            } else {
                index++
            }
        }

        if (streams.isEmpty()) {
            // Media playlist (or an unparseable master) — the input IS the stream.
            return if (returnThis == true) listOf(m3u8) else emptyList()
        }
        return streams
    }
}
