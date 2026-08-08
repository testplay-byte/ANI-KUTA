package com.confused.anikuta.core.schedule

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * SQLDelight wrapper for the episode_schedule table (Phase SC — PLAN §1.5).
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Schedule:Store".
 */
class ScheduleStore(
    private val database: AnikutaDatabase,
) : com.confused.anikuta.core.updates.ActualReleaseUpdater {
    companion object {
        private const val TAG = "Anikuta:Core:Schedule:Store"
    }

    /** Upsert a schedule entry. */
    fun upsertScheduleEntry(
        mainId: String,
        anilistId: Long?,
        episodeNumber: Long,
        scheduledAt: Long,
        actualAt: Long?,
        audioVariant: String,
        source: String,
        fetchedAt: Long,
    ) {
        database.episodeScheduleQueries.upsertEpisodeSchedule(
            main_id = mainId,
            anilist_id = anilistId,
            episode_number = episodeNumber,
            scheduled_at = scheduledAt,
            actual_at = actualAt,
            audio_variant = audioVariant,
            source = source,
            fetched_at = fetchedAt,
        )
    }

    /** Observe upcoming schedule entries (reactive — for the list view). */
    fun observeUpcoming(from: Long, to: Long): Flow<List<ScheduleEntry>> =
        database.episodeScheduleQueries.getUpcomingSchedule(from, to)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toScheduleEntry() } }

    /** Get schedule entries for a specific day (for the calendar day-detail sheet). */
    fun getScheduleForDay(dayStart: Long, dayEnd: Long): List<ScheduleEntry> =
        database.episodeScheduleQueries.getScheduleForDay(dayStart, dayEnd)
            .executeAsList().map { it.toScheduleEntry() }

    /** Update actual_at (Phase SC-2 — when UpdateEngine finds the episode). Overrides ActualReleaseUpdater. */
    override fun updateActualAt(mainId: String, episodeNumber: Long, actualAt: Long) {
        database.episodeScheduleQueries.updateActualAt(actualAt, mainId, episodeNumber)
        Logger.d(TAG) { "updateActualAt: mainId=$mainId ep=$episodeNumber actualAt=$actualAt" }
    }

    /** Delete old schedule entries (cleanup). */
    fun deleteOldSchedule(cutoff: Long) {
        database.episodeScheduleQueries.deleteOldSchedule(cutoff)
    }

    private fun com.confused.anikuta.core.database.Episode_schedule.toScheduleEntry() = ScheduleEntry(
        id = id,
        mainId = main_id,
        anilistId = anilist_id,
        episodeNumber = episode_number,
        scheduledAt = scheduled_at,
        actualAt = actual_at,
        audioVariant = audio_variant,
        source = source,
        fetchedAt = fetched_at,
    )
}

data class ScheduleEntry(
    val id: Long,
    val mainId: String,
    val anilistId: Long?,
    val episodeNumber: Long,
    val scheduledAt: Long,
    val actualAt: Long?,
    val audioVariant: String,
    val source: String,
    val fetchedAt: Long,
)
