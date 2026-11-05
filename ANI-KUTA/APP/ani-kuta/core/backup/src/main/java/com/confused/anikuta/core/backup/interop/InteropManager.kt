package com.confused.anikuta.core.backup.interop

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.cash.sqldelight.db.SqlDriver
import com.confused.anikuta.core.backup.BackupFormat
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.download.DownloadPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ROUND 137 (D-828): the interop orchestrator — the UI's single entry point
 * for writing foreign-format backups and restoring them.
 *
 * Export: collects the library snapshot from the driver, asks the
 * [InteropExporter] for the payload, and writes it into the SAME `backup/`
 * SAF folder the native `.anikuta` archives live in (one folder, every
 * format). The local database is never modified by an export.
 *
 * Restore: reads the picked file, detects + parses the format
 * ([InteropImporter]), then applies the plan through the app-layer
 * [InteropRestoreBridge] (identity resolution → library → progress →
 * tracking). Everything is ADDITIVE — an interop restore never deletes or
 * overwrites local rows.
 */
class InteropManager(
    private val context: Context,
    private val driver: SqlDriver,
    private val downloadPreferences: DownloadPreferences,
    private val restoreBridge: InteropRestoreBridge?,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Backup:Interop"
    }

    private val exporter = InteropExporter(driver)
    private val importer = InteropImporter()

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Writes [format] into the backup folder. [sourceNameResolver] /
     * [providerResolver] come from the app layer (the extension catalogs).
     */
    suspend fun exportTo(
        format: InteropFormat,
        sourceNameResolver: (Long) -> String = { "" },
        providerResolver: (Long) -> Pair<String, String?>? = { null },
    ): Result<InteropExportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            require(format != InteropFormat.ANIKUTA) {
                "ANI-KUTA backups go through the native backup, not the interop exporter"
            }
            val dir = backupFolder()
                ?: throw IllegalStateException("No storage folder is set (or it is not writable)")

            val result = exporter.export(
                format = format,
                sourceNameResolver = sourceNameResolver,
                providerResolver = providerResolver,
            )
            val created = dir.createFile(format.mime, result.suggestedFileName)
                ?: throw IllegalStateException("The storage folder refused a new file")
            // SAF createFile can append a numeric suffix when the name collides —
            // report the REAL file name.
            val realName = created.name ?: result.suggestedFileName
            context.contentResolver.openOutputStream(created.uri)?.use { out ->
                out.write(result.bytes)
                out.flush()
            } ?: throw IllegalStateException("Could not open the new file for writing")

            Logger.i(TAG) {
                "interop export: format=$format file=$realName entries=${result.entriesWritten} " +
                    "episodes=${result.episodesWritten} tracks=${result.trackingRows} skipped=${result.entriesSkipped}"
            }
            result.toSummary(format, realName)
        }.onFailure { e ->
            Logger.w(TAG, e) { "interop export failed: ${e.message}" }
        }
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    /**
     * Restores ANY supported foreign backup from [sourceUri]. Additive —
     * existing rows are never clobbered (the bridge resolves identities and
     * dedupes). Requires the restore bridge (a test context without it fails).
     */
    suspend fun restoreFrom(sourceUri: Uri, fileNameHint: String?): Result<InteropRestoreSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bridge = restoreBridge
                    ?: throw IllegalStateException("The restore bridge is not available")
                val bytes = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    input.readBytes()
                } ?: throw InteropImporter.InteropParseException("Could not read the selected file")

                val plan = importer.parse(bytes, fileNameHint)
                applyPlan(plan, bridge)
            }.onFailure { e ->
                Logger.w(TAG, e) { "interop restore failed: ${e.message}" }
            }
        }

    private fun applyPlan(plan: InteropRestorePlan, bridge: InteropRestoreBridge): InteropRestoreSummary {
        // The honest "categories added" count — the bridge dedupes internally,
        // so the manager measures the table before/after instead of trusting
        // the plan's declared list.
        val categoriesBefore = countRows("library_category")
        val categoryIdByName = linkedMapOf<String, Long>()
        for (name in plan.categories) {
            if (name.isBlank()) continue
            val id = bridge.ensureCategory(name)
            if (id != null) categoryIdByName[name] = id
        }
        val categoriesAdded = (countRows("library_category") - categoriesBefore).coerceAtLeast(0)

        var entriesCreated = 0
        var entriesMatched = 0
        var libraryAdded = 0
        var progressRows = 0
        var trackingRows = 0
        var pendingSources = 0
        val warnings = plan.warnings.toMutableList()

        for (entry in plan.entries) {
            val resolved = bridge.resolveEntry(entry)
            if (resolved == null) {
                pendingSources++
                continue
            }
            if (resolved.created) entriesCreated++ else entriesMatched++

            if (entry.favorite) {
                val categoryIds = entry.categoryNames.mapNotNull { categoryIdByName[it] }
                val resolvedCategoryIds = if (categoryIds.isEmpty()) {
                    // An entry with no known category (aniyomi's virtual Default,
                    // the verifier's P3-11) lands in the app's seeded PERMANENT
                    // Default — never in whatever category sorts first in the
                    // donor's display order. Skip when no Default exists.
                    permanentDefaultCategoryId()?.let { listOf(it) } ?: emptyList()
                } else {
                    categoryIds
                }
                for (categoryId in resolvedCategoryIds) {
                    if (bridge.addToLibrary(resolved.mainId, categoryId)) libraryAdded++
                }
            }

            for (episode in entry.episodes) {
                val history = if (episode.url.isNotBlank()) {
                    entry.history.firstOrNull { it.url == episode.url }
                } else {
                    null
                }
                bridge.upsertProgress(resolved.mainId, episode, history)
                progressRows++
            }

            for (tracking in entry.tracking) {
                bridge.upsertTracking(resolved.mainId, tracking)
                trackingRows++
            }
        }

        return InteropRestoreSummary(
            format = plan.format,
            entriesRead = plan.entries.size,
            entriesCreated = entriesCreated,
            entriesMatched = entriesMatched,
            libraryAdded = libraryAdded,
            categoriesAdded = categoriesAdded,
            progressRows = progressRows,
            trackingRows = trackingRows,
            skippedManga = plan.skippedManga,
            pendingSources = pendingSources,
            warnings = warnings,
        ).also {
            Logger.i(TAG) {
                "interop restore: format=${plan.format} entries=${it.entriesRead} " +
                    "created=${it.entriesCreated} matched=${it.entriesMatched} library+=${it.libraryAdded} " +
                    "progress=${it.progressRows} tracking=${it.trackingRows} manga=${it.skippedManga}"
            }
        }
    }

    // ── The backup folder (mirrors BackupManager) ─────────────────────────────

    /**
     * The app's seeded permanent Default category id (is_permanent = 1) — the
     * fallback home for imported entries with no category.
     */
    private fun permanentDefaultCategoryId(): Long? = driver.executeQuery(
        null,
        "SELECT id FROM library_category WHERE is_permanent = 1 ORDER BY display_order LIMIT 1",
        { cursor ->
            val id = if (cursor.next().value) cursor.getLong(0) else null
            app.cash.sqldelight.db.QueryResult.Value(id)
        },
        0,
        null,
    ).value

    /** A one-cell table count through the raw driver (the honest delta counter). */
    private fun countRows(table: String): Int = driver.executeQuery(
        null,
        "SELECT COUNT(*) FROM \"$table\"",
        { cursor ->
            val n = if (cursor.next().value) (cursor.getLong(0) ?: 0).toInt() else 0
            app.cash.sqldelight.db.QueryResult.Value(n)
        },
        0,
        null,
    ).value

    private fun backupFolder(): DocumentFile? {
        val uriStr = downloadPreferences.downloadFolderUri.get()
        if (uriStr.isBlank()) return null
        val root = try {
            DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
        } catch (e: Exception) {
            Logger.w(TAG, e) { "backupFolder: unparsable tree URI" }
            null
        } ?: return null
        if (!root.exists() || !root.canWrite()) return null
        return root.findFile(BackupFormat.BACKUP_DIR_NAME)
            ?: root.createDirectory(BackupFormat.BACKUP_DIR_NAME)
            ?: return null
    }
}
