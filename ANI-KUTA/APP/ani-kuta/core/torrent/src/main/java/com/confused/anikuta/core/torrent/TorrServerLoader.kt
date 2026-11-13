package com.confused.anikuta.core.torrent

import android.content.Context
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * THE ENGINE LOADER — the ONE file in the whole repo that touches the
 * gomobile binding (`torrServer.TorrServer`). Start the embedded Go server,
 * probe it, append the tracker list, and clean stale caches.
 *
 * Design (mirrors upstream CloudStream's Torrent.kt, the working reference):
 *  - the server runs IN-PROCESS (a native library, not a separate app) on
 *    127.0.0.1:<port> — no manifest permissions, no foreground service for
 *    streaming (the app is foreground while playing; the FGS arrives with
 *    torrent DOWNLOADS in T4);
 *  - `go.Seq.load()` initializes the gomobile runtime before the first call;
 *  - port 0 = let the server pick a free port (upstream does the same);
 *  - upstream's own `/shutdown` HTTP path is broken (their comment: "should
 *    not be used because I am unable to start it again") — there is NO stop
 *    call; the server lives with the process and the cache is cleaned at the
 *    next app start ([cleanupStaleCache], called from Application.onCreate).
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Torrent".
 */
class TorrServerLoader(private val context: Context) {

    companion object {
        private const val TAG = "Anikuta:Core:Torrent"

        /** The in-cache working dir (self-cleaning at every app start — upstream CS does the same). */
        private const val SERVER_DIR = "torrent_tmp"

        /** The Go server's default bind port (TorrServer lineage — not caller-chosen in this artifact). */
        private const val DEFAULT_PORT = 8090

        /** The bounded /echo readiness poll (a dead start must never hang the play path). */
        private const val START_PROBE_ATTEMPTS = 20

        /**
         * The curated tracker list — copied from upstream CloudStream's
         * Torrent.kt (the ecosystem's shared list; the server appends these
         * to every torrent's announce set, which is what makes thin public
         * swarms findable).
         */
        private val TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.tracker.cl:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker-udp.gbitt.info:80/announce",
            "udp://explodie.org:6969/announce",
            "udp://tracker.tiny-vps.com:6969/announce",
            "udp://tracker.theoks.net:6969/announce",
            "udp://tracker.dump.cl:1337/announce",
            "udp://tracker.bittor.pw:1337/announce",
            "udp://tracker1.bt.moack.co.kr:80/announce",
            "udp://uploads.gamecoast.net:6969/announce",
            "http://tracker.openbittorrent.com:80/announce",
            "https://tracker.gbitt.info:443/announce",
            "https://tracker1.520.jp:443/announce",
        )
    }

    private val startMutex = Mutex()

    /** The server's base URL once running ("http://127.0.0.1:<port>"); null = not started. */
    @Volatile
    var baseUrl: String? = null
        private set

    fun isRunning(): Boolean = baseUrl != null

    /**
     * Deletes the LAST session's torrent cache (leftovers after process
     * death — the server never stops cleanly). Called ONCE from
     * Application.onCreate, before anything can start the server. Removing
     * torrent support = removing this one call + this module (see
     * TORRENT-REMOVAL.md).
     */
    fun cleanupStaleCache() {
        runCatching {
            val dir = File(context.cacheDir, SERVER_DIR)
            if (dir.exists()) {
                val freed = dir.deleteRecursively()
                Logger.i(TAG) { "stale torrent cache cleared ($freed): ${dir.path}" }
            }
        }.onFailure { t ->
            Logger.w(TAG, t) { "stale torrent cache cleanup failed (continuing)" }
        }
    }

    /**
     * ROUND 145 (H-3b): the ASYNC entry point for Application.onCreate — the
     * recursive delete can span thousands of files after a heavy streaming
     * session; on the main thread that was an ANR at startup. Dispatches to IO.
     */
    fun cleanupStaleCacheAsync() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            cleanupStaleCache()
        }
    }

    /**
     * Starts the server if it is not up yet (idempotent, single-flight).
     * Returns the base URL, or null when the engine failed to load
     * (unsupported device / native load error) — callers degrade to the
     * plain no-torrent behavior.
     */
    suspend fun ensureStarted(): String? = startMutex.withLock {
        baseUrl?.let { return it }
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, SERVER_DIR).apply { mkdirs() }
                // The PINNED artifact's API (com.github.Diegopyl1209:torrentserver-aniyomi
                // c18f58e51b — the commit the ecosystem pins): touch() initializes the
                // gomobile runtime, startTorrentServer(path) launches the Go server
                // (void — it binds its DEFAULT port, TorrServer's 8090), and readiness
                // is probed via /echo (waitTorrentServer() blocks forever on a dead
                // start, so a bounded echo poll is the safe waiter). The ONLY engine
                // imports in the repo live in this block.
                torrServer.TorrServer.touch()
                torrServer.TorrServer.startTorrentServer(dir.absolutePath)
                // ROUND 145 (H-3): the echo poll now demands OUR OWN start — a
                // foreign TorrServer already bound to 8090 would answer /echo and
                // be silently adopted (its cache dir, its lifecycle). The guard:
                // the server must answer AND reflect OUR working directory via
                // the settings echo round-trip? The pinned artifact exposes no
                // such probe — so the adoption guard is the START ORDER: baseUrl
                // is only set when OUR startTorrentServer call preceded the echo
                // (a foreign listener still answers, but our start into the SAME
                // port would have failed to bind and the process would log its
                // own bind failure; the honest mitigation is the runtime check
                // below + the documented port-conflict limitation).
                val base = "http://127.0.0.1:$DEFAULT_PORT"
                var ready = false
                repeat(START_PROBE_ATTEMPTS) {
                    if (echoProbe("$base/echo")) {
                        ready = true
                        return@repeat
                    }
                    kotlinx.coroutines.delay(1_000L)
                }
                if (!ready) {
                    Logger.e(TAG) { "torrent server did not answer /echo within ${START_PROBE_ATTEMPTS}s (a foreign app may own port $DEFAULT_PORT)" }
                    return@withContext null
                }
                // ROUND 145 (H-3): the IDENTITY GUARD — our server creates its
                // settings/DB files in the dir WE passed; a foreign TorrServer
                // already bound to 8090 answers the echo but never writes into
                // OUR dir. An empty dir + a live echo = a foreign listener:
                // refuse (the torrent links stay hidden rather than riding
                // another app's server).
                if (dir.listFiles().isNullOrEmpty()) {
                    Logger.e(TAG) { "the echo answered but OUR working dir stayed empty — refusing to adopt a foreign server on port $DEFAULT_PORT" }
                    return@withContext null
                }
                torrServer.TorrServer.addTrackers(TRACKERS.joinToString(separator = ",\n"))
                baseUrl = base
                Logger.i(TAG) { "torrent server RUNNING: $base (dir=${dir.path})" }
                base
            }.onFailure { t ->
                Logger.e(TAG, t) { "torrent server crashed on start — torrent links stay hidden" }
            }.getOrNull()
        }
    }

    /** The liveness probe the loader owns (a plain connection — no client dependency). */
    private fun echoProbe(url: String): Boolean = runCatching {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        val answer = connection.inputStream.use { stream ->
            stream.bufferedReader().readText()
        }
        connection.disconnect()
        answer.isNotEmpty()
    }.getOrDefault(false)
}
