package com.confused.anikuta.core.torrent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder

/**
 * THE TORRENT ENGINE'S WIRE CONTRACT (round 143 — D-858).
 *
 * These mirror the embedded TorrServer-lineage Go server's REST API
 * (`Diegopyl1209/torrentserver-aniyomi`, the engine the whole Aniyomi-fork
 * ecosystem — Animetail, Anikku, Kuukiyomi, upstream CloudStream — embeds).
 * Field names are the SERVER's JSON contract; do not rename.
 *
 * Everything here lives in core:torrent by design — see TORRENT-REMOVAL.md.
 */

/** POST /torrents body — one request object per action (add/list/get/drop/rem). */
@Serializable
data class TorrentRequest(
    @SerialName("action") val action: String,
    @SerialName("hash") val hash: String = "",
    @SerialName("link") val link: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("poster") val poster: String = "",
    @SerialName("data") val data: String = "",
    @SerialName("save_to_db") val saveToDb: Boolean = false,
)

/** One file inside a torrent (from the server's file_stats list). */
@Serializable
data class TorrentFileStat(
    @SerialName("id") val id: Int? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("length") val length: Long? = null,
)

/**
 * The server's torrent record — carries the streaming stats the player's
 * status chip shows (peers, seeders, speeds, preloaded bytes) plus the
 * file list the stream URL is built from.
 */
@Serializable
data class TorrentStatus(
    @SerialName("title") val title: String,
    @SerialName("poster") val poster: String,
    @SerialName("data") val data: String? = null,
    @SerialName("timestamp") val timestamp: Long = 0L,
    @SerialName("name") val name: String? = null,
    @SerialName("hash") val hash: String? = null,
    @SerialName("stat") val stat: Int = 0,
    @SerialName("stat_string") val statString: String = "",
    @SerialName("loaded_size") val loadedSize: Long? = null,
    @SerialName("torrent_size") val torrentSize: Long? = null,
    @SerialName("preloaded_bytes") val preloadedBytes: Long? = null,
    @SerialName("preload_size") val preloadSize: Long? = null,
    @SerialName("download_speed") val downloadSpeed: Double? = null,
    @SerialName("upload_speed") val uploadSpeed: Double? = null,
    @SerialName("total_peers") val totalPeers: Int? = null,
    @SerialName("pending_peers") val pendingPeers: Int? = null,
    @SerialName("active_peers") val activePeers: Int? = null,
    @SerialName("connected_seeders") val connectedSeeders: Int? = null,
    @SerialName("bytes_read") val bytesRead: Long? = null,
    @SerialName("bytes_read_useful_data") val bytesReadUsefulData: Long? = null,
    @SerialName("file_stats") val fileStats: List<TorrentFileStat>? = null,
    @SerialName("trackers") val trackers: List<String>? = null,
) {
    /**
     * The playback URL: the server's sequential-streaming endpoint, which
     * serves the file with full Range support (MPV seeks escalate piece
     * priorities server-side). Mirrors upstream CloudStream's Torrent.kt
     * streamUrl() byte-for-byte:
     *   /stream/<encodedFileName>?link=<hash>&index=<n>&play
     * The file index may be encoded by the extension INTO the raw link
     * ("...&index=2") — honored here; 0 = the server's default pick.
     */
    fun streamUrl(baseUrl: String, rawLink: String): String {
        val fileName = fileStats?.firstOrNull { !it.path.isNullOrBlank() }?.path
            ?: throw TorrentException("The torrent has no file list yet")
        val index = rawLink.substringAfter("index=").substringBefore("&").toIntOrNull() ?: 0
        return "$baseUrl/stream/${URLEncoder.encode(fileName, "utf-8")}" +
            "?link=$hash&index=$index&play"
    }

    /** A compact one-line status for the player chip ("12 peers · 2.4 MB/s · 45%"). */
    fun chipLine(): String = buildString {
        append("${connectedSeeders ?: 0}★ ${activePeers ?: 0}/${totalPeers ?: 0} peers")
        downloadSpeed?.takeIf { it > 0 }?.let {
            append(" · ${formatSpeed(it)}")
        }
        preloadSize?.takeIf { it > 0 }?.let { size ->
            preloadedBytes?.let { loaded ->
                append(" · ${(loaded * 100 / size).coerceIn(0, 100)}%")
            }
        }
    }

    private fun formatSpeed(bytesPerSecond: Double): String = when {
        bytesPerSecond >= 1_048_576.0 -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSecond / 1_048_576.0)
        bytesPerSecond >= 1024.0 -> String.format(java.util.Locale.US, "%.0f KB/s", bytesPerSecond / 1024.0)
        else -> String.format(java.util.Locale.US, "%.0f B/s", bytesPerSecond)
    }
}

/** The transform result — everything the watch VM needs to play + poll. */
data class TorrentPrepared(
    /** The localhost stream URL MPV plays (a plain progressive HTTP stream). */
    val streamUrl: String,
    /** The torrent's info hash — the status-polling key. */
    val hash: String,
    /** The server's display title (the torrent's name). */
    val title: String,
    /** The full status snapshot at prepare time. */
    val status: TorrentStatus,
)

/** One error type for every torrent failure path (friendly messages live at the UI seam). */
class TorrentException(message: String, cause: Throwable? = null) : Exception(message, cause)
