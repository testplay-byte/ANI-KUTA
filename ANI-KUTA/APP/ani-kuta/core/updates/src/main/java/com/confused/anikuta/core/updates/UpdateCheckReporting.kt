package com.confused.anikuta.core.updates

import kotlinx.serialization.Serializable

/**
 * Task 64 (round 24 — the update-check LIVE status notification):
 * progress callbacks fired by [UpdateEngine.checkDueAnime] while a check runs,
 * implemented in `:app` (UpdateProgressNotifierImpl) as a single ongoing
 * notification that updates IN REAL TIME per content item ("which content it
 * checked out, the names of them").
 *
 * D-388 (round 25 — the FULL update-notifications module rework): the
 * finish callback now carries a full [UpdateCheckSummary] (per-anime lines,
 * next-check info) so the results notification can name the anime, show
 * per-item outcomes, and state what happens next — the round-25 device
 * report: "It did not tell me the name of the anime. It did not tell me what
 * it was searching for and it did not tell me the next details, like what it
 * will do next and such."
 *
 * The engine calls these on its IO dispatcher — implementations must be
 * cheap + never throw (best-effort).
 */
interface UpdateProgressNotifier {

    /** A check is starting — [totalDue] anime are queued. */
    fun onCheckStart(trigger: String, totalDue: Int)

    /** About to check item [current] of [total] — [title] is its name. */
    fun onProgress(current: Int, total: Int, title: String)

    /** The check finished — [summary] carries everything the results
     *  notification + the history need (per-item outcomes, next check). */
    fun onFinish(summary: UpdateCheckSummary)

    /** The check failed mid-run (an exception escaped the engine). */
    fun onFailed(error: String)
}

/**
 * D-388 (round 25): the finish payload — one object the notifier turns into
 * the rich results notification (per-anime lines + the next-check line) and
 * the engine ALSO hands to [UpdateCheckLogger] (as [UpdateCheckLogEntry]).
 */
@Serializable
data class UpdateCheckSummary(
    /** What started the check: "periodic" | "manual". */
    val trigger: String,
    /** How many anime were checked. */
    val totalChecked: Int,
    /** New episodes found across all items. */
    val totalNewEpisodes: Int,
    /** Per-anime outcomes in check order (title, cover, outcome, next action). */
    val items: List<UpdateCheckItemLog>,
    /** When the NEXT check will run (epoch ms) — null when manual/off/unknown.
     *  The notifier renders "Next check: in Xh · day time". */
    val nextCheckAt: Long? = null,
    /** The scheduled interval in hours (rendered as "every 24h"). */
    val intervalHours: Long? = null,
    /** When this session started (epoch ms). */
    val startedAt: Long,
    /** When this session finished (epoch ms). */
    val finishedAt: Long,
)

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
 * How many per-anime lines the results notification shows (BigTextStyle —
 * the rest are summarized as "+N more").
 */
const val MAX_NOTIFICATION_ITEM_LINES = 5

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
    /** D-388 (round 25): when the NEXT check is expected (epoch ms; null =
     *  manual/off) — the history's pinned next-check card uses the LATEST
     *  session's value as its fallback when WorkManager reports nothing. */
    val nextCheckAt: Long? = null,
)

/**
 * One content item's check outcome — the "debug kind of detail" the user
 * asked for: what was checked, what happened, what the engine decided next.
 *
 * D-388 (round 25): [coverUrl] so the history rows + the notification's
 * large icon can show the anime's cover (null on legacy JSON entries —
 * `ignoreUnknownKeys` + a default keep old files decodable).
 *
 * D-396 (round 27): the SMART-SCHEDULE math captured at check time, so the
 * history can show per-series "what it CALCULATED, what it LANDED (the
 * WorkManager one-shot's real fire time, resolved live by the history
 * screen), and the delay" — the round-27 report: "it would show me some
 * info of the next times expected ones too… so that I know that it is
 * working properly". All nullable-with-default → old JSON stays decodable.
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
    /** The anime's cover URL (AniList axis first, extension thumbnail as
     *  fallback) — captured at check time for the history rows + the
     *  notification's large icon. */
    val coverUrl: String? = null,
    // ── D-396 (round 27): the smart-schedule record ──
    /** The episode number AniList says airs next (null when unknown). */
    val nextAiringEpisode: Long? = null,
    /** When that episode airs (epoch ms; null when unknown). May be in the
     *  past by the time the history is read — the record of what the engine
     *  KNEW at check time. */
    val nextAiringAt: Long? = null,
    /** The per-anime LEARNED release delay applied at check time (ms; null =
     *  not learned yet — the +10min default was used). */
    val learnedOffsetMs: Long? = null,
    /**
     * What the engine CALCULATED as the next check target —
     * `nextAiringAt + learnedOffsetMs` (epoch ms; null when no FUTURE airing
     * was known at check time). The history screen contrasts this with the
     * LANDED WorkManager one-shot (queried live) to expose any drift.
     */
    val expectedCheckAt: Long? = null,
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
