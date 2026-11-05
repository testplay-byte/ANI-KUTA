package com.confused.anikuta.core.watchprogress

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ROUND 137 (D-830): the per-day watch-session ledger writer.
 *
 * `watch_progress.played_time_ms` is a CUMULATIVE counter — it answers "how
 * much in total" but never "how much on Tuesday". This recorder feeds the
 * `watch_session` table (one row per CONTINUOUS watching streak) that the
 * Profile page buckets by LOCAL calendar day.
 *
 * The players call [onPlaybackTick] at their existing save points (the 10s
 * tick, dispose, EOF, episode switch) passing the CURRENT SESSION's played
 * milliseconds — `PlayerStateHolder.playbackTimeMs` (MPV) or the CS engine's
 * per-session accumulator. The recorder derives the DELTA itself:
 *
 *  - The first tick after an episode switch only BASELINES (the holder resets
 *    `playbackTimeMs` to 0 on a switch, and the caller's base capture races
 *    the reset — recording a delta across that boundary would either drop a
 *    few seconds or double-count the previous episode's tail).
 *  - A delta ≤ 0 (rewind-ish) or > [MAX_TICK_DELTA_MS] (a seek-leak) is
 *    dropped, mirroring the holder's own forward-only 1–5s accumulation.
 *
 * Session continuity: a tick extends the most recent session of the same
 * episode when that session's `ended_at` is within [SESSION_GAP_MS] of now;
 * otherwise a new session row is opened (its `started_at` back-dated by the
 * delta so the wall-clock span stays honest). Extends and opens are
 * serialized through a Mutex — the two players can tick concurrently.
 *
 * The store-side cumulative counter and this ledger never disagree for the
 * windows they cover: both accumulate the same forward deltas.
 */
class WatchSessionRecorder(private val database: AnikutaDatabase) {

    companion object {
        private const val TAG = "Anikuta:Core:WatchSession"

        /** A pause/stop longer than this closes the session (the next tick opens one). */
        const val SESSION_GAP_MS: Long = 5 * 60_000L

        /** A single tick can never honestly carry more than this (a 10s tick + slop). */
        const val MAX_TICK_DELTA_MS: Long = 60_000L
    }

    private val mutex = Mutex()

    // The per-caller baseline (one player screen per recorder instance).
    private var lastEpisodeKey: String? = null
    private var lastSessionPlayedMs: Long = 0L

    /**
     * Records the playback delta of the current tick.
     *
     * @param mainId the content's main id (blank → the row still records with an
     *   empty main_id; the profile's day buckets key off the timestamps).
     * @param episodeKey the standardized `mainId|%05d` episode key.
     * @param sessionPlayedMs the CURRENT SESSION's cumulative played ms for THIS
     *   episode (NOT the persisted cumulative total — the recorder derives deltas).
     */
    suspend fun onPlaybackTick(
        mainId: String?,
        episodeKey: String,
        sessionPlayedMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (episodeKey.isBlank()) return
        mutex.withLock {
            // Episode switch (or first tick): baseline only — see the KDoc.
            if (episodeKey != lastEpisodeKey) {
                lastEpisodeKey = episodeKey
                lastSessionPlayedMs = sessionPlayedMs
                return@withLock
            }
            // The caller's accumulator can RESET without the episode key
            // changing — the CS stack zeroes playedSessionMs on every episode
            // OPEN (including re-entering the SAME episode, and sub↔dub
            // switches, whose ordinal-aware key is deliberately flavor-shared).
            // A regression is that reset — re-baseline so the post-reset deltas
            // record instead of being eaten until the accumulator passed the
            // old total (the verifier's P2-3: a 20-minute session re-opened
            // recorded NOTHING before this guard).
            if (sessionPlayedMs < lastSessionPlayedMs) {
                lastSessionPlayedMs = sessionPlayedMs
                return@withLock
            }
            val delta = sessionPlayedMs - lastSessionPlayedMs
            lastSessionPlayedMs = sessionPlayedMs
            if (delta <= 0 || delta > MAX_TICK_DELTA_MS) return@withLock

            runCatching {
                val last = database.watchQueries.lastWatchSessionForEpisode(episodeKey)
                    .executeAsOneOrNull()
                if (last != null && nowMs - last.ended_at <= SESSION_GAP_MS && last.ended_at <= nowMs) {
                    // Extend the open session (started_at keeps the original open
                    // time). The increment is DB-SIDE (played_ms = played_ms +
                    // :delta) so two recorder instances can never lose a delta
                    // through a read-modify-write race (the verifier's P3-16).
                    database.watchQueries.extendWatchSession(
                        id = last.id,
                        ended_at = nowMs,
                        delta_ms = delta,
                    )
                } else {
                    // New session — back-date the open by the delta so the span
                    // covers the watching that already happened in this tick.
                    database.watchQueries.insertWatchSession(
                        main_id = mainId.orEmpty(),
                        episode_key = episodeKey,
                        started_at = nowMs - delta,
                        ended_at = nowMs,
                        played_ms = delta,
                    )
                }
            }.onFailure { e ->
                Logger.w(TAG, e) { "session record failed for $episodeKey: ${e.message}" }
            }
        }
    }
}
