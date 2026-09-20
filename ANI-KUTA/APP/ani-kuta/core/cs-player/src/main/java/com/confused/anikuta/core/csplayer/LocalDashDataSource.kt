// CLEAN-ROOM: original ANI-KUTA code.
//
// D-548: the LOCAL side of offline DASH playback. A D-548 episode's media
// lives in the user's SAF folder as real files (`<title> - E00001.mp4` +
// `<title> - E00001.audio<N>.mp4` + `<title> - E00001.mp4.dashmeta`), and
// CsPlayerEngine.startOfflineDashLocal plays it as a SIDeloaded
// DashMediaSource whose every segment request is served by this DataSource
// from the local file ranges recorded in the sidecar.
//
// WHY a sideloaded manifest (and not a plain ProgressiveMediaSource over the
// concatenated file): an fMP4 built by concatenating init + media segments
// has NO index (no sidx), and ExoPlayer CANNOT seek an unindexed fragmented
// MP4 (the Android media troubleshooting docs' own item). The manifest IS
// the index — its SegmentTimeline gives media3 exact seek points and the
// duration, and the segment URLs it produces (identical substitution to the
// planner's — same template, same inputs) are exactly the keys the
// downloader recorded in the sidecar's range index.
//
// Wire format contract: the sidecar is WRITTEN by `:core:download`
// (DashOfflineMetaFile — kotlinx) and READ here with org.json (this module
// cannot depend on `:core:download`). The JSON keys are the cross-module
// contract — rename with both readers in mind.
package com.confused.anikuta.core.csplayer

import android.content.ContentResolver
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.confused.anikuta.core.common.Logger
import java.io.IOException
import java.io.InputStream

/** One planned part's home in the published media files (the sidecar's `ranges` entries). */
internal data class DashLocalSegmentRange(
    val url: String,
    val file: Int,
    val offset: Long,
    val length: Long,
    val partPosition: Long,
)

/**
 * The parsed `.mp4.dashmeta` sidecar — the manifest bytes + the URL→
 * (file, offset, length) index the [LocalDashDataSource] serves from.
 */
internal class DashLocalIndex(
    val manifestUrl: String,
    val manifestBytes: ByteArray,
    /** Document uris: index 0 = the video file, 1..n = the audio-set files. */
    val fileUris: List<String>,
    val ranges: List<DashLocalSegmentRange>,
) {
    /**
     * Finds the range a segment request resolves to, or null (honest miss).
     *
     * D-548 (review major 3): media3's RangedUri requests carry
     * [DataSpec.position] = the range's ABSOLUTE remote start — for
     * SegmentList byte-range parts that is exactly the placement's
     * [DashLocalSegmentRange.partPosition] (the exact-match path); for
     * SegmentBase whole-file placements media3 first requests the index
     * range, then media ranges — all inside the ONE placement (the
     * single-candidate fallback serves any position within it).
     */
    fun lookup(uri: Uri, position: Long): DashLocalSegmentRange? {
        val key = uri.toString()
        val candidates = ranges.filter { it.url == key }
        if (candidates.isEmpty()) return null
        candidates.firstOrNull { it.partPosition == position }?.let { return it }
        if (candidates.size == 1) {
            val only = candidates[0]
            val inPart = position - only.partPosition
            if (inPart in 0 until only.length) return only
        }
        return null
    }

    companion object {
        private const val TAG = "Anikuta:CS:Player"

        /**
         * Reads + parses the sidecar document (IO — call off the main
         * thread). Throws [IOException] with a diagnosable message for every
         * malformed shape.
         */
        internal fun read(resolver: ContentResolver, metaUri: Uri): DashLocalIndex {
            val bytes = resolver.openInputStream(metaUri)?.use { it.readBytes() }
                ?: throw IOException("The DASH offline sidecar could not be opened: $metaUri")
            val json = runCatching { org.json.JSONObject(String(bytes, Charsets.UTF_8)) }
                .getOrElse { throw IOException("The DASH offline sidecar is not valid JSON: ${it.message}") }
            val version = json.optInt("schemaVersion", -1)
            if (version != 1) {
                throw IOException("The DASH offline sidecar has an unsupported schemaVersion=$version")
            }
            val manifestUrl = json.optString("manifestUrl", "")
            if (manifestUrl.isBlank()) throw IOException("The DASH offline sidecar has no manifestUrl")
            val manifestBase64 = json.optString("manifestBase64", "")
            if (manifestBase64.isBlank()) throw IOException("The DASH offline sidecar has no manifest bytes")
            val manifestBytes = runCatching {
                android.util.Base64.decode(manifestBase64, android.util.Base64.NO_WRAP)
            }.getOrElse { throw IOException("The DASH offline sidecar manifest bytes are corrupt: ${it.message}") }

            val filesJson = json.optJSONArray("files")
                ?: throw IOException("The DASH offline sidecar has no media files")
            val fileUris = buildList {
                for (i in 0 until filesJson.length()) add(filesJson.optString(i))
            }
            if (fileUris.isEmpty() || fileUris.any { it.isBlank() }) {
                throw IOException("The DASH offline sidecar media file list is empty or malformed")
            }

            val rangesJson = json.optJSONArray("ranges")
                ?: throw IOException("The DASH offline sidecar has no range index")
            val ranges = buildList {
                for (i in 0 until rangesJson.length()) {
                    val entry = rangesJson.optJSONObject(i)
                        ?: throw IOException("The DASH offline sidecar range #$i is not an object")
                    val file = entry.optInt("file", -1)
                    if (file < 0 || file >= fileUris.size) {
                        throw IOException("The DASH offline sidecar range #$i points at file $file of ${fileUris.size}")
                    }
                    add(
                        DashLocalSegmentRange(
                            url = entry.optString("url"),
                            file = file,
                            offset = entry.optLong("offset", -1L),
                            length = entry.optLong("length", -1L),
                            partPosition = entry.optLong("partPosition", 0L),
                        ),
                    )
                }
            }
            if (ranges.isEmpty() || ranges.any { it.url.isBlank() || it.offset < 0L || it.length <= 0L }) {
                throw IOException("The DASH offline sidecar range index is empty or malformed")
            }
            Logger.i(TAG) {
                "DashLocalIndex — read $metaUri: ${fileUris.size} file(s), ${ranges.size} range(s), " +
                    "manifest=${manifestBytes.size}B"
            }
            return DashLocalIndex(manifestUrl, manifestBytes, fileUris, ranges)
        }
    }
}

/**
 * The DataSource that makes downloaded DASH episodes play from the SAF
 * folder: every segment request the sideloaded manifest produces is looked
 * up in the sidecar's range index and served from the local file's bytes at
 * the recorded (offset, length). A miss is an HONEST IOException (an
 * unpinned representation slipped through, or the index and the file
 * disagree) — never a silent network fetch, so an "offline" episode can
 * never secretly stream.
 */
@OptIn(UnstableApi::class)
internal class LocalDashDataSource(
    private val resolver: ContentResolver,
    private val index: DashLocalIndex,
) : BaseDataSource(/* isNetwork = */ false) {

    private var stream: InputStream? = null
    private var openedUri: Uri? = null
    private var remaining: Long = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val range = index.lookup(dataSpec.uri, dataSpec.position)
            ?: throw IOException(
                "DASH segment not in the local offline index " +
                    "(an unpinned representation, or the index and the manifest disagree): ${dataSpec.uri}",
            )
        val fileUri = android.net.Uri.parse(index.fileUris[range.file]) // CI round 3: the sidecar stores uri STRINGS — openInputStream needs the parsed Uri
        val input = resolver.openInputStream(fileUri)
            ?: throw IOException("The local DASH media file could not be opened: $fileUri")
        stream = input
        openedUri = dataSpec.uri
        // D-548 (review major 3): [DataSpec.position] for a ranged request is
        // the range's ABSOLUTE remote start (= partPosition for an exact
        // match) — the offset INSIDE the part is the difference, not the raw
        // position (adding it would overshoot by partPosition bytes).
        val inPart = (dataSpec.position - range.partPosition).coerceAtLeast(0L)
        // Skip to the part's bytes, then to the request's position inside it.
        var toSkip = range.offset + inPart
        while (toSkip > 0L) {
            val skipped = input.skip(toSkip)
            if (skipped > 0L) {
                toSkip -= skipped
                continue
            }
            // InputStream.skip may no-op near EOF — read one byte instead.
            if (input.read() < 0) {
                close()
                throw IOException("The local DASH media file is shorter than the index claims: $fileUri")
            }
            toSkip -= 1L
        }
        remaining = range.length - inPart
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            remaining = minOf(remaining, dataSpec.length)
        }
        if (remaining <= 0L) {
            close()
            throw IOException("The local DASH range is exhausted before the request: $fileUri")
        }
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = minOf(readLength.toLong(), remaining).toInt()
        val n = checkNotNull(stream).read(buffer, offset, toRead)
        if (n < 0) {
            throw IOException("The local DASH media file ended before the indexed range did")
        }
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        val input = stream
        stream = null
        openedUri = null
        if (opened) {
            opened = false
            transferEnded()
        }
        input?.close()
    }
}
