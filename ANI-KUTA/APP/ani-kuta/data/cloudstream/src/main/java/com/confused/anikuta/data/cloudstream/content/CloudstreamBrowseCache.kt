package com.confused.anikuta.data.cloudstream.content

import android.content.Context
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /**
     * Task 62 (round 22 — the stable randomized browse): the LAST display
     * arrangement (row order + per-row item order) the user saw on the search
     * page, so a cold app reopen renders the EXACT same arrangement instead
     * of re-shuffling. Null until the first shuffle lands. `ignoreUnknownKeys`
     * decodes old snapshot files with this null (they shuffle fresh once, then
     * persist).
     */
    val display: CsBrowseDisplay? = null,
)

/**
 * Task 62: the persisted display arrangement — one row per displayed section,
 * in DISPLAY order. [CsBrowseDisplayRow.shelfIndex] is the shelf's ORIGINAL
 * index in the provider's mainPage (the category subpages resolve by it);
 * [CsBrowseDisplayRow.itemUrls] is the row's item order (urls are the only
 * stable cross-session item identity).
 */
@Serializable
data class CsBrowseDisplay(
    val rows: List<CsBrowseDisplayRow>,
)

@Serializable
data class CsBrowseDisplayRow(
    val shelfIndex: Int,
    val itemUrls: List<String>,
)

class CloudstreamBrowseCache(
    context: Context,
) {
    private val memory = ConcurrentHashMap<String, CsBrowseSnapshot>()
    private val dir = File(context.filesDir, "cloudstream/browse")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Task 62: serializes the ASYNC disk writes (put + saveDisplay) so a
    // display update written right after a fresh browse can never land
    // BEFORE the browse's own write and get overwritten by the older bytes.
    private val diskMutex = Mutex()

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
                // Task 64 (round 24 — F): a snapshot whose sections carry no
                // shelfIndex (< 0) is a LEGACY pre-field snapshot — its
                // positional indexes can drive the category subpages onto the
                // WRONG shelves. Treat it as stale: delete + refetch (one-time
                // cost on the first open after the update; every put since
                // writes real indexes).
                val legacy = snapshot.sections.any { it.shelfIndex < 0 }
                if (snapshot.providerName != providerName ||
                    snapshot.sections.isEmpty() ||
                    legacy
                ) {
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
     *
     * D-387 (round 25 — the background-refresh arrangement jump): the NEW
     * snapshot CARRIES OVER the previous one's display. A stale-cache
     * background refresh re-put()s fresh sections mid-session; with display
     * defaulting to null, the new snapshot lost the persisted arrangement →
     * the ViewModel's re-read of `cachedBrowseDisplay()` returned null → the
     * restore validation failed → a FRESH shuffle re-arranged the rows WHILE
     * the user was looking at the cached ones (categories appeared to swap
     * content — the round-25 "bleeding into each other's categories" report).
     * The carry-over keeps the arrangement the user sees; saveDisplay()
     * re-persists it whenever the shuffle actually runs.
     */
    fun put(providerName: String, sections: List<CsBrowseSection>) {
        if (sections.isEmpty()) return
        val key = memKey(providerName)
        val snapshot = CsBrowseSnapshot(
            providerName = providerName,
            sections = sections,
            fetchedAtMs = System.currentTimeMillis(),
            display = memory[key]?.display,
        )
        memory[key] = snapshot
        scope.launch {
            diskMutex.withLock {
                runCatching {
                    if (!dir.exists()) dir.mkdirs()
                    snapshotFile(providerName).writeText(json.encodeToString(snapshot))
                    Logger.d(TAG) { "cache: stored ${sections.size} section(s) for '$providerName'" }
                }.onFailure { t ->
                    Logger.w(TAG, t) { "cache: disk write failed for '$providerName' (memory copy kept)" }
                }
            }
        }
    }

    /**
     * Task 62 (round 22): attaches the display arrangement to the CURRENT
     * snapshot (memory synchronously, disk asynchronously — serialized with
     * [diskMutex] against [put]'s write). No-op when no snapshot exists yet
     * (the arrangement is meaningless without the sections) or when it is
     * already the persisted one (the frequent tab-return reshuffles stay
     * cheap: an identical order writes nothing).
     */
    fun saveDisplay(providerName: String, display: CsBrowseDisplay) {
        val key = memKey(providerName)
        val current = memory[key] ?: return
        if (current.display == display) return
        val updated = current.copy(display = display)
        memory[key] = updated
        scope.launch {
            diskMutex.withLock {
                runCatching {
                    if (!dir.exists()) dir.mkdirs()
                    snapshotFile(providerName).writeText(json.encodeToString(updated))
                    Logger.d(TAG) { "cache: display order saved for '$providerName'" }
                }.onFailure { t ->
                    Logger.w(TAG, t) { "cache: display disk write failed for '$providerName'" }
                }
            }
        }
    }

    /** Drops one provider's memory + disk snapshot (provider uninstalled/untrusted). */
    fun invalidate(providerName: String) {
        memory.remove(memKey(providerName))
        scope.launch {
            // Task 62: under the SAME disk mutex — a pending put/saveDisplay
            // write must not resurrect the file after this delete (the
            // pull-to-refresh invalidate → fresh browse → save sequence stays
            // strictly ordered).
            diskMutex.withLock {
                runCatching { snapshotFile(providerName).delete() }
            }
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
