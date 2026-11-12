package com.confused.anikuta.core.torrent

import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * The embedded server's REST client (all traffic stays on 127.0.0.1).
 * Endpoints mirror upstream CloudStream's Torrent.kt / Animetail's
 * TorrentServerApi: POST /torrents {action: add|list|get|drop|rem} and
 * GET /echo for the liveness probe.
 *
 * Deliberately its OWN OkHttpClient (short timeouts, no app interceptors):
 * the torrent module stays independent of the app's network stack so it can
 * be removed without touching anything else (TORRENT-REMOVAL.md).
 */
class TorrentServerApi(private val loader: TorrServerLoader) {

    companion object {
        private const val TAG = "Anikuta:Core:Torrent"
        private val JSON_MEDIA = "application/json".toMediaTypeOrNull()
        private val JSON = Json { ignoreUnknownKeys = true }
        private const val TIMEOUT_S = 5L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    private fun post(body: TorrentRequest): Request =
        Request.Builder()
            .url(requireNotNull(loader.baseUrl) { "torrent server not started" } + "/torrents")
            .post(JSON.encodeToString(TorrentRequest.serializer(), body).toRequestBody(JSON_MEDIA))
            .build()

    private inline fun <reified T> call(body: TorrentRequest, label: String): T {
        val base = loader.baseUrl ?: throw TorrentException("The torrent server is not running")
        val request = Request.Builder()
            .url("$base/torrents")
            .post(JSON.encodeToString(TorrentRequest.serializer(), body).toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw TorrentException("Torrent $label failed: HTTP ${response.code}")
            }
            return JSON.decodeFromString(text)
        }
    }

    /** GET /echo — the liveness probe (any non-empty answer means the server is up). */
    fun echo(): Boolean {
        val base = loader.baseUrl ?: return false
        return runCatching {
            client.newCall(Request.Builder().url("$base/echo").build()).execute().use { response ->
                response.isSuccessful && response.body?.string().orEmpty().isNotEmpty()
            }
        }.getOrDefault(false)
    }

    /**
     * Adds a magnet/.torrent link to the server and returns its status —
     * the server resolves metadata (name, hash, file list) from the swarm.
     * Blocks while the metadata resolves (bounded by OkHttp's timeout);
     * thin swarms surface as a timeout → DeadTorrentException at the caller.
     */
    suspend fun add(rawLink: String, title: String): TorrentStatus = withContext(Dispatchers.IO) {
        call<TorrentStatus>(TorrentRequest(action = "add", link = rawLink, title = title), "add")
    }

    /** The full status snapshot for one hash (the player chip's poll). */
    suspend fun get(hash: String): TorrentStatus = withContext(Dispatchers.IO) {
        call<TorrentStatus>(TorrentRequest(action = "get", hash = hash), "get")
    }

    /** Removes one torrent from the server's registry (drop = close stream, rem = forget). */
    suspend fun remove(hash: String) = withContext(Dispatchers.IO) {
        runCatching { call<TorrentStatus>(TorrentRequest(action = "drop", hash = hash), "drop") }
            .onFailure { Logger.d(TAG) { "drop $hash: ${it.message}" } }
        runCatching { call<TorrentStatus>(TorrentRequest(action = "rem", hash = hash), "rem") }
            .onFailure { Logger.d(TAG) { "rem $hash: ${it.message}" } }
        Logger.i(TAG) { "torrent removed from server: $hash" }
    }
}
