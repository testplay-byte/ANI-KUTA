package com.confused.anikuta.feature.extensionssettings.testing

import android.content.Context
import android.content.SharedPreferences
import com.confused.anikuta.core.common.Logger
import org.json.JSONObject

/**
 * The testing suite's MEMORY (round 83, D-579): persists per-target run
 * results so the NEXT time the user opens Extension Testing they see which
 * extensions worked, which didn't, and why — and can re-run precisely the
 * failing ones (or the passing ones) without starting from zero.
 *
 * STORAGE SHAPE: one JSON document per target under the
 * "extension_test_results" SharedPreferences file —
 *   key   = "run-<targetId>"
 *   value = { targetId, targetName, ecosystem, finished, abortedByUser,
 *             testedAtMs, results: { "<KindName>": { status, durationMs,
 *             message, detail } } }
 * plus ONE history document under the "history" key (NOT "run-" prefixed —
 * [loadAll]/[prune] filter by the prefix and stay untouched):
 *   { entries: [ { startedAtMs, finishedAtMs, scope, label, totalTargets,
 *                 passed, failed, aborted,
 *                 kinds: { "<KindName>": { avgMs, failCount, runCount } } } ] }
 * newest first, capped at [HISTORY_CAP].
 *
 * WHY SharedPreferences + org.json (and NOT SQLDelight/DataStore): the data
 * is tiny (dozens of targets × 7 tests), self-contained to this feature, and
 * schema-free — a per-kind JSON blob absorbs new tests / renamed kinds
 * without any migration. The store owns ZERO business logic: it maps models
 * in and out and nothing else. The engine/screen contracts
 * ([TargetRunState] / [TestResult]) are untouched — persistence hangs off
 * them (the doc-64 future roadmap: statistics + automated runs read this
 * same store).
 *
 * MODULARITY: swapping this file for a SQLDelight table later changes ONE
 * file — the screen only ever calls [loadAll] / [saveTarget] / [prune] /
 * [clear].
 *
 * CORE_RULES §20: logged with tag "Anikuta:Feature:ExtensionsTesting".
 */
class ExtensionTestResultStore(context: Context) {

    companion object {
        private const val TAG = "Anikuta:Feature:ExtensionsTesting"
        private const val PREFS_NAME = "extension_test_results"
        private const val KEY_PREFIX = "run-"
        private const val KEY_HISTORY = "history"
        private const val HISTORY_CAP = 40
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** One persisted target run — the round-trip model of [TargetRunState]. */
    data class StoredTargetRun(
        val targetId: Long,
        val targetName: String,
        val ecosystem: String,
        val finished: Boolean,
        val testedAtMs: Long,
        val abortedByUser: Boolean = false,
        val results: Map<ExtensionTestKind, TestResult> = emptyMap(),
    )

    /** One per-kind aggregate for the run history (computed by the controller). */
    data class StoredKindStat(
        val avgMs: Long,
        val failCount: Int,
        val runCount: Int,
    )

    /** One persisted RUN summary (the stats page's history feed). */
    data class StoredRunSummary(
        val startedAtMs: Long,
        val finishedAtMs: Long,
        val scope: String,
        val label: String,
        val totalTargets: Int,
        val passed: Int,
        val failed: Int,
        val aborted: Int,
        val kinds: Map<String, StoredKindStat>,
    )

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * Loads every persisted run. Entries that fail to parse (a schema drift,
     * a partial write) are dropped individually — one bad row never blanks
     * the others. Unknown test-kind names (a test removed in a later version)
     * are skipped the same way.
     */
    fun loadAll(): Map<Long, StoredTargetRun> {
        val out = mutableMapOf<Long, StoredTargetRun>()
        prefs.all.forEach { (key, value) ->
            if (!key.startsWith(KEY_PREFIX)) return@forEach
            val id = key.removePrefix(KEY_PREFIX).toLongOrNull() ?: return@forEach
            val json = value as? String ?: return@forEach
            try {
                val parsed = parseRun(id, JSONObject(json)) ?: return@forEach
                out[id] = parsed
            } catch (e: Exception) {
                Logger.w(TAG) { "Result store: dropping unparseable entry $key" }
            }
        }
        return out
    }

    private fun parseRun(id: Long, root: JSONObject): StoredTargetRun? {
        val resultsJson = root.optJSONObject("results") ?: JSONObject()
        val results = mutableMapOf<ExtensionTestKind, TestResult>()
        resultsJson.keys().forEach { kindName ->
            val kind = ExtensionTestKind.entries.firstOrNull { it.name == kindName }
                ?: return@forEach
            val r = resultsJson.getJSONObject(kindName)
            val status = runCatching {
                TestStatus.valueOf(r.getString("status"))
            }.getOrNull() ?: return@forEach
            results[kind] = TestResult(
                kind = kind,
                status = status,
                durationMs = r.optLong("durationMs", 0L),
                message = r.optString("message", ""),
                detail = r.optString("detail", "").takeIf { it.isNotEmpty() },
                payload = r.optJSONObject("payload")?.let { runCatching { payloadFromJson(it) }.getOrNull() },
            )
        }
        if (results.isEmpty()) return null
        return StoredTargetRun(
            targetId = id,
            targetName = root.optString("targetName", ""),
            ecosystem = root.optString("ecosystem", ""),
            finished = root.optBoolean("finished", false),
            testedAtMs = root.optLong("testedAtMs", 0L),
            abortedByUser = root.optBoolean("abortedByUser", false),
            results = results,
        )
    }

    // ── Write ────────────────────────────────────────────────────────────────

    /** Persists ONE target's finished run (called as each target completes). */
    fun saveTarget(target: TestableTarget, state: TargetRunState) {
        val root = JSONObject()
        root.put("targetId", target.id)
        root.put("targetName", target.name)
        root.put("ecosystem", target.ecosystem.name)
        root.put("finished", state.finished)
        root.put("abortedByUser", state.abortedByUser)
        root.put("testedAtMs", System.currentTimeMillis())
        val resultsJson = JSONObject()
        state.results.forEach { (kind, result) ->
            val r = JSONObject()
            r.put("status", result.status.name)
            r.put("durationMs", result.durationMs)
            r.put("message", result.message)
            r.put("detail", result.detail ?: "")
            result.payload?.let { payload ->
                runCatching { r.put("payload", payloadToJson(payload)) }
            }
            resultsJson.put(kind.name, r)
        }
        root.put("results", resultsJson)
        prefs.edit().putString(KEY_PREFIX + target.id, root.toString()).apply()
    }

    /**
     * Removes stored runs whose target ids are NOT in [keepIds] — called on
     * screen open so uninstalled extensions don't linger forever.
     */
    fun prune(keepIds: Set<Long>) {
        val staleKeys = prefs.all.keys.filter { key ->
            key.startsWith(KEY_PREFIX) &&
                key.removePrefix(KEY_PREFIX).toLongOrNull()?.let { it !in keepIds } == true
        }
        if (staleKeys.isEmpty()) return
        prefs.edit().apply {
            staleKeys.forEach { remove(it) }
        }.apply()
        Logger.i(TAG) { "Result store: pruned ${staleKeys.size} stale entr${if (staleKeys.size == 1) "y" else "ies"}" }
    }

    /** Wipes all stored results (the screen's "Clear results" action). */
    fun clear() {
        val keys = prefs.all.keys.filter { it.startsWith(KEY_PREFIX) } + KEY_HISTORY
        prefs.edit().apply {
            keys.forEach { remove(it) }
        }.apply()
    }

    // ── Run history (round 84, D-583 — the stats page's feed) ───────────────

    /**
     * Appends one run summary (newest first, capped at [HISTORY_CAP]). A
     * parse failure anywhere never throws to the caller — a broken blob is
     * replaced by a fresh history rather than blocking the write.
     */
    fun appendHistory(entry: StoredRunSummary) {
        val entries = try {
            readHistoryJson().toMutableList()
        } catch (e: Exception) {
            Logger.w(TAG) { "Result store: history unreadable, starting fresh" }
            mutableListOf()
        }
        entries.add(0, entry)
        val capped = entries.take(HISTORY_CAP)
        prefs.edit()
            .putString(KEY_HISTORY, historyToJson(capped).toString())
            .apply()
    }

    /** The persisted run history, newest first (empty on any parse trouble). */
    fun loadHistory(): List<StoredRunSummary> = try {
        readHistoryJson()
    } catch (e: Exception) {
        Logger.w(TAG) { "Result store: history unreadable, returning empty" }
        emptyList()
    }

    private fun readHistoryJson(): List<StoredRunSummary> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val root = JSONObject(raw)
        val arr = root.optJSONArray("entries") ?: return emptyList()
        val out = mutableListOf<StoredRunSummary>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val kindsJson = o.optJSONObject("kinds") ?: JSONObject()
            val kinds = mutableMapOf<String, StoredKindStat>()
            kindsJson.keys().forEach { kindName ->
                val k = kindsJson.getJSONObject(kindName)
                kinds[kindName] = StoredKindStat(
                    avgMs = k.optLong("avgMs", 0L),
                    failCount = k.optInt("failCount", 0),
                    runCount = k.optInt("runCount", 0),
                )
            }
            out.add(
                StoredRunSummary(
                    startedAtMs = o.optLong("startedAtMs", 0L),
                    finishedAtMs = o.optLong("finishedAtMs", 0L),
                    scope = o.optString("scope", "all"),
                    label = o.optString("label", ""),
                    totalTargets = o.optInt("totalTargets", 0),
                    passed = o.optInt("passed", 0),
                    failed = o.optInt("failed", 0),
                    aborted = o.optInt("aborted", 0),
                    kinds = kinds,
                ),
            )
        }
        return out
    }

    private fun historyToJson(entries: List<StoredRunSummary>): JSONObject {
        val arr = org.json.JSONArray()
        entries.forEach { e ->
            val o = JSONObject()
            o.put("startedAtMs", e.startedAtMs)
            o.put("finishedAtMs", e.finishedAtMs)
            o.put("scope", e.scope)
            o.put("label", e.label)
            o.put("totalTargets", e.totalTargets)
            o.put("passed", e.passed)
            o.put("failed", e.failed)
            o.put("aborted", e.aborted)
            val kinds = JSONObject()
            e.kinds.forEach { (name, stat) ->
                val k = JSONObject()
                k.put("avgMs", stat.avgMs)
                k.put("failCount", stat.failCount)
                k.put("runCount", stat.runCount)
                kinds.put(name, k)
            }
            o.put("kinds", kinds)
            arr.put(o)
        }
        return JSONObject().put("entries", arr)
    }

    // ── The payload JSON projection (round 85) ──────────────────────────────
    // Every field is opt-read on the way back — an older blob without a
    // payload (or a future one with extra fields) round-trips safely.

    private fun payloadToJson(p: TestPayload): JSONObject {
        val o = JSONObject()
        p.httpCode?.let { o.put("httpCode", it) }
        p.rttMs?.let { o.put("rttMs", it) }
        p.entries?.let { list ->
            val arr = org.json.JSONArray()
            list.forEach { e ->
                arr.put(JSONObject().put("title", e.title).put("thumb", e.thumbnailUrl ?: ""))
            }
            o.put("entries", arr)
        }
        p.detailsTitle?.let { o.put("detailsTitle", it) }
        p.detailsGenres?.let { list -> o.put("detailsGenres", org.json.JSONArray(list)) }
        p.detailsStatus?.let { o.put("detailsStatus", it) }
        p.detailsSynopsis?.let { o.put("detailsSynopsis", it) }
        p.detailsThumbnailUrl?.let { o.put("detailsThumbnailUrl", it) }
        p.episodeCount?.let { o.put("episodeCount", it) }
        p.episodes?.let { list ->
            val arr = org.json.JSONArray()
            list.forEach { e ->
                arr.put(JSONObject().put("number", e.number).put("name", e.name))
            }
            o.put("episodes", arr)
        }
        p.videos?.let { list ->
            val arr = org.json.JSONArray()
            list.forEach { v ->
                arr.put(JSONObject().put("label", v.label).put("quality", v.quality ?: ""))
            }
            o.put("videos", arr)
        }
        p.streamBytesLabel?.let { o.put("streamBytesLabel", it) }
        p.streamHttpCode?.let { o.put("streamHttpCode", it) }
        return o
    }

    private fun payloadFromJson(o: JSONObject): TestPayload = TestPayload(
        httpCode = if (o.has("httpCode")) o.optInt("httpCode") else null,
        rttMs = if (o.has("rttMs")) o.optLong("rttMs") else null,
        entries = o.optJSONArray("entries")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let {
                    TestPayloadEntry(
                        title = it.optString("title"),
                        thumbnailUrl = it.optString("thumb").takeIf { t -> t.isNotEmpty() },
                    )
                }
            }.takeIf { it.isNotEmpty() }
        },
        detailsTitle = o.optString("detailsTitle").takeIf { it.isNotEmpty() },
        detailsGenres = o.optJSONArray("detailsGenres")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optString(i).takeIf { s -> s.isNotEmpty() }
            }.takeIf { it.isNotEmpty() }
        },
        detailsStatus = o.optString("detailsStatus").takeIf { it.isNotEmpty() },
        detailsSynopsis = o.optString("detailsSynopsis").takeIf { it.isNotEmpty() },
        detailsThumbnailUrl = o.optString("detailsThumbnailUrl").takeIf { it.isNotEmpty() },
        episodeCount = if (o.has("episodeCount")) o.optInt("episodeCount") else null,
        episodes = o.optJSONArray("episodes")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let {
                    TestPayloadEpisode(number = it.optInt("number"), name = it.optString("name"))
                }
            }.takeIf { it.isNotEmpty() }
        },
        videos = o.optJSONArray("videos")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let {
                    TestPayloadVideo(
                        label = it.optString("label"),
                        quality = it.optString("quality").takeIf { q -> q.isNotEmpty() },
                    )
                }
            }.takeIf { it.isNotEmpty() }
        },
        streamBytesLabel = o.optString("streamBytesLabel").takeIf { it.isNotEmpty() },
        streamHttpCode = if (o.has("streamHttpCode")) o.optInt("streamHttpCode") else null,
    )
}
