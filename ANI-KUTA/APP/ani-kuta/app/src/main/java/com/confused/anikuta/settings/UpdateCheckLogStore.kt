package com.confused.anikuta.settings

import android.content.Context
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.updates.MAX_CHECK_LOG_SESSIONS
import com.confused.anikuta.core.updates.UpdateCheckLogEntry
import com.confused.anikuta.core.updates.UpdateCheckLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Task 64 (round 24 — the content-update HISTORY): the :app implementation of
 * [UpdateCheckLogger] — a JSON FILE in filesDir (deliberately NOT the
 * database; this round's constraint), capped at [MAX_CHECK_LOG_SESSIONS]
 * sessions (oldest dropped).
 *
 * The user's spec: "keep track of when the app actually checked for updates…
 * a dedicated option to check out the updates log, like a content update
 * history… based on which content it started the search, on which content,
 * whether it was successful or not, what was the next probable action which
 * it thought of taking, and all other stuff like that."
 *
 * Writes are serialized (a mutex) + best-effort: a failing history write can
 * NEVER fail the update check itself. Reads power [UpdateCheckLogScreen].
 */
class UpdateCheckLogStore(
    context: Context,
) : UpdateCheckLogger {

    companion object {
        private const val TAG = "Anikuta:Settings:UpdateCheckLog"
        private const val FILE_NAME = "update_check_history.json"
    }

    private val file = File(context.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val diskMutex = Mutex()

    /** Appends one completed session, drops the oldest past the cap, saves. */
    override fun logSession(entry: UpdateCheckLogEntry) {
        // The engine calls this on its IO dispatcher, but the interface is
        // synchronous — hop once here so the (small) file work is off-caller.
        kotlinx.coroutines.runBlocking {
            diskMutex.withLock {
                runCatching {
                    val sessions = (loadLocked() + entry)
                        .takeLast(MAX_CHECK_LOG_SESSIONS)
                    file.parentFile?.mkdirs()
                    file.writeText(json.encodeToString(sessions))
                    Logger.i(TAG) {
                        "history: logged session ${entry.id} (${entry.items.size} items, " +
                            "${entry.totalNewEpisodes} new) — ${sessions.size} session(s) kept"
                    }
                }.onFailure { t ->
                    Logger.w(TAG, t) { "history: failed to persist a check session (non-fatal)" }
                }
            }
        }
    }

    /** All sessions, NEWEST FIRST, for the history screen. */
    suspend fun sessions(): List<UpdateCheckLogEntry> = withContext(Dispatchers.IO) {
        diskMutex.withLock { loadLocked().asReversed() }
    }

    private fun loadLocked(): List<UpdateCheckLogEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<UpdateCheckLogEntry>>(file.readText())
        }.getOrElse { t ->
            Logger.w(TAG, t) { "history: unreadable log file — starting fresh" }
            emptyList()
        }
    }
}
