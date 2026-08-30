package com.confused.anikuta.data.cloudstream.content

import android.content.Context
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Task 48 (device round 7): the search page's CloudStream browse cache.
 *
 * The user's report: "it should keep the whole page cached so that it is
 * instantaneous and it does not load every single time. Next time when I open
 * up the app and go to the search page directly, it would not load the data.
 * Instead it will use the cached data properly."
 *
 * Design (stale-while-revalidate, doc 19 §8):
 * • **Memory layer** — a [ConcurrentHashMap] keyed by provider name. Zero IO
 *   on the hot path; the peek is a map lookup once the feed has been shown
 *   once in this process.
 * • **Disk layer** — one JSON snapshot per provider under
 *   `files/cloudstream/browse/<md5(providerName)>.json`, written async after
 *   every successful network browse. Read once per cold start (peek loads it
 *   into the memory layer), so re-entering the search page after a full app
 *   restart renders the feed IMMEDIATELY — before the plugin manager has
 *   even finished its (activity-gated) load pass.
 * • **Freshness** — [isFresh] compares the snapshot age against [FRESH_TTL_MS]
 *   (10 minutes). Fresh snapshots SKIP the network refresh entirely; stale
 *   ones render instantly and refresh in the background (the ViewModel keeps
 *   the cached state visible if that refresh fails — a network hiccup must
 *   never blank a page we can already show).
 *
 * Only NON-EMPTY browse results are cached — an empty browse is usually a
 * transient provider/shelf failure, and overwriting a good cached feed with
 * it would poison the cache.
 *
 * The snapshot models ([CsBrowseSection]/[CsContentCard]) are plain UI data
 * (no live plugin references), so kotlinx.serialization round-trips them
 * losslessly.
 */
@Serializable
data class CsBrowseSnapshot(
    val providerName: String,
    val sections: List<CsBrowseSection>,
    val fetchedAtMs: Long,
)

class CloudstreamBrowseCache(
    context: Context,
) {
    private val memory = ConcurrentHashMap<String, CsBrowseSnapshot>()
    private val dir = File(context.filesDir, "cloudstream/browse")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Memory-first peek, falling back to ONE disk read per cold start (the
     * loaded snapshot is promoted into the memory layer). Returns null when
     * this provider has never been browsed successfully.
     */
    suspend fun peek(providerName: String): CsBrowseSnapshot? {
        val key = memKey(providerName)
        memory[key]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = snapshotFile(providerName)
                if (!file.exists()) return@withContext null
                val snapshot = json.decodeFromString<CsBrowseSnapshot>(file.readText())
                // Guard against a corrupted/hand-edited file.
                if (snapshot.providerName != providerName || snapshot.sections.isEmpty()) {
                    file.delete()
                    null
                } else {
                    memory[key] = snapshot
                    Logger.d(TAG) {
                        "cache: disk hit for '$providerName' — ${snapshot.sections.size} " +
                            "section(s), age=${ageMs(snapshot)}ms"
                    }
                    snapshot
                }
            }.getOrElse { t ->
                Logger.w(TAG, t) { "cache: failed to read snapshot for '$providerName'" }
                null
            }
        }
    }

    /** True when the newest snapshot is younger than [FRESH_TTL_MS]. */
    fun isFresh(providerName: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val snapshot = memory[memKey(providerName)] ?: return false
        return nowMs - snapshot.fetchedAtMs < FRESH_TTL_MS
    }

    /**
     * Stores a successful browse. Memory synchronously (the NEXT peek must
     * see it), disk asynchronously (never blocks the browse result path).
     * Empty sections are ignored (see class doc).
     */
    fun put(providerName: String, sections: List<CsBrowseSection>) {
        if (sections.isEmpty()) return
        val snapshot = CsBrowseSnapshot(
            providerName = providerName,
            sections = sections,
            fetchedAtMs = System.currentTimeMillis(),
        )
        val key = memKey(providerName)
        memory[key] = snapshot
        scope.launch {
            runCatching {
                if (!dir.exists()) dir.mkdirs()
                snapshotFile(providerName).writeText(json.encodeToString(snapshot))
                Logger.d(TAG) { "cache: stored ${sections.size} section(s) for '$providerName'" }
            }.onFailure { t ->
                Logger.w(TAG, t) { "cache: disk write failed for '$providerName' (memory copy kept)" }
            }
        }
    }

    /** Drops one provider's memory + disk snapshot (provider uninstalled/untrusted). */
    fun invalidate(providerName: String) {
        memory.remove(memKey(providerName))
        scope.launch {
            runCatching { snapshotFile(providerName).delete() }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private fun ageMs(snapshot: CsBrowseSnapshot): Long =
        System.currentTimeMillis() - snapshot.fetchedAtMs

    /** Provider name → memory key (provider names are unique across plugins). */
    private fun memKey(providerName: String): String = providerName

    /** Provider name → stable, filesystem-safe snapshot file. */
    private fun snapshotFile(providerName: String): File {
        val digest = MessageDigest.getInstance("MD5").digest(providerName.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }
        return File(dir, "$hex.json")
    }

    companion object {
        private const val TAG = "Anikuta:Data:Cloudstream:BrowseCache"

        /** A snapshot younger than this skips the background refresh entirely. */
        const val FRESH_TTL_MS: Long = 10 * 60_000L
    }
}
