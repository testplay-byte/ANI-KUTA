package com.confused.anikuta.core.updates

import kotlinx.serialization.Serializable

/**
 * Task 64 (round 24 — the update-check LIVE status notification):
 * progress callbacks fired by [UpdateEngine.checkDueAnime] while a check runs,
 * implemented in `:app` (UpdateProgressNotifierImpl) as a single ongoing
 * notification that updates IN REAL TIME per content item ("which content it
 * checked out, the names of them").
 *
 * The round-23 attempt was reverted with its branch; this is the clean
 * re-implementation. The engine calls these on its IO dispatcher —
 * implementations must be cheap + never throw (best-effort).
 */
interface UpdateProgressNotifier {

    /** A check is starting — [totalDue] anime are queued. */
    fun onCheckStart(trigger: String, totalDue: Int)

    /** About to check item [current] of [total] — [title] is its name. */
    fun onProgress(current: Int, total: Int, title: String)

    /** The check finished — [totalChecked] items, [totalNew] new episodes. */
    fun onFinish(totalChecked: Int, totalNew: Int)

    /** The check failed mid-run (an exception escaped the engine). */
    fun onFailed(error: String)
}

/**
 * Task 64 (round 24 — the content-update HISTORY): one completed check
 * session, handed to [UpdateCheckLogger] by the engine. The user's spec:
 * "keep track of when the app actually checked for updates… a content update
 * history… based on which content it started the search, on which content,
 * whether it was successful or not, what was the next probable action which
 * it thought of taking, and all other stuff like that."
 *
 * Persisted by the :app implementation as a JSON FILE (NOT the database —
 * this round's constraint), capped at [MAX_SESSIONS] sessions.
 */
interface UpdateCheckLogger {
    /** Append one completed session (called once per check run). */
    fun logSession(entry: UpdateCheckLogEntry)
}

/** How many sessions the file keeps (oldest dropped first). */
const val MAX_CHECK_LOG_SESSIONS = 30

/** Safety cap for per-session item records. */
const val MAX_CHECK_LOG_ITEMS = 200

/**
 * One full check session.
 */
@Serializable
data class UpdateCheckLogEntry(
    /** Unique id (UUID) for stable list keys. */
    val id: String,
    /** When the session started (epoch ms). */
    val startedAt: Long,
    /** When it finished (epoch ms). */
    val finishedAt: Long,
    /** What started it: "periodic" (the WorkManager run) | "manual". */
    val trigger: String,
    /** How many anime were due/checked. */
    val totalChecked: Int,
    /** New episodes found across all items. */
    val totalNewEpisodes: Int,
    /** False when the run died mid-way ([error] carries the reason). */
    val success: Boolean,
    /** The failure reason (null on success). */
    val error: String? = null,
    /** Per-content outcomes, in check order. */
    val items: List<UpdateCheckItemLog> = emptyList(),
)

/**
 * One content item's check outcome — the "debug kind of detail" the user
 * asked for: what was checked, what happened, what the engine decided next.
 */
@Serializable
data class UpdateCheckItemLog(
    val title: String,
    val mainId: String,
    /** "new-episodes" | "no-new-episodes" | "failed" | "source-unavailable" | "skipped". */
    val outcome: String,
    /** New episodes found for THIS item. */
    val newEpisodes: Int,
    /** Human-readable detail — e.g. "SUB 5 → 7, DUB 3 → 3" or the error message. */
    val detail: String,
    /** The engine's next action — e.g. "re-check in 8h (backoff step 3)",
     *  "re-check 1h after next airing", "auto-update disabled (3 failures)". */
    val nextAction: String,
)

/**
 * The internal per-item result of a single anime check — the engine's own
 * bookkeeping (mapped into [UpdateCheckItemLog] for the history).
 */
internal data class CheckItemResult(
    val newEpisodes: Int,
    val outcome: String,
    val detail: String,
    val nextAction: String,
)
