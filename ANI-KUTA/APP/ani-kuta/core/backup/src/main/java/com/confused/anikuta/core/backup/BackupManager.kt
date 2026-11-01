package com.confused.anikuta.core.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.cash.sqldelight.db.SqlDriver
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.download.DownloadPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The backup & restore engine (round 134, D-816).
 *
 * Creates and restores `.anikuta` ZIP archives (see [BackupFormat]) in the
 * app's selected SAF folder under `backup/`, keeping the newest N files. The
 * DB payload is dumped/restored GENERICALLY through the raw SqlDriver
 * (columns read from PRAGMA at runtime) so the format tracks the schema
 * without hand-written codecs; the preferences payload carries the full
 * SharedPreferences layer (settings + the AniList token + the extension /
 * CloudStream repo + plugin setup); the optional images payload carries the
 * downloaded series' cover files.
 *
 * SAF contract (D-404): every write into the tree is a single truncating
 * `openOutputStream(uri, "wt")` — the archive itself is assembled in a
 * cacheDir temp first and copied in one shot.
 */
class BackupManager(
    private val context: Context,
    private val driver: SqlDriver,
    private val downloadPreferences: DownloadPreferences,
    private val backupPreferences: BackupPreferences,
) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    // ── The backup folder ────────────────────────────────────────────────────

    /** `<selected folder>/backup` — null when no folder is set or unwritable. */
    fun backupFolder(): DocumentFile? {
        val uriStr = downloadPreferences.downloadFolderUri.get()
        if (uriStr.isBlank()) return null
        val root = try {
            DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
        } catch (e: Exception) {
            Logger.w(TAG, e) { "backupFolder: unparsable tree URI" }
            null
        } ?: return null
        if (!root.exists() || !root.canWrite()) return null
        val dir = root.findFile(BackupFormat.BACKUP_DIR_NAME)
            ?: root.createDirectory(BackupFormat.BACKUP_DIR_NAME)
            ?: return null
        return dir
    }

    /** The existing backups, newest first. */
    fun listBackups(): List<BackupFileInfo> {
        val dir = backupFolder() ?: return emptyList()
        return dir.listFiles()
            .filter { it.name != null && it.name!!.startsWith(BackupFormat.FILE_PREFIX) && it.name!!.endsWith(BackupFormat.FILE_EXTENSION) }
            .map { BackupFileInfo(it.name!!, it.uri.toString(), it.lastModified(), it.length()) }
            .sortedByDescending { it.name }
    }

    // ── Create ───────────────────────────────────────────────────────────────

    /**
     * Creates a backup in the backup folder. Returns the manifest on success.
     */
    suspend fun createBackup(options: BackupOptions): Result<BackupManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = backupFolder()
                ?: throw BackupException("No storage folder is set (or it is not writable)")
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
            } catch (_: Exception) { "?" }
            val createdAt = System.currentTimeMillis()

            // ── Assemble the archive in a cacheDir temp first (the SAF write
            // is a single truncating copy afterwards). ──
            val temp = java.io.File(context.cacheDir, "backup-${System.currentTimeMillis()}.anikuta.tmp")
            try {
                val manifest = BufferedOutputStream(FileOutputStream(temp)).use { raw ->
                    ZipOutputStream(raw).use { zip ->
                        dumpDatabase(zip, options, createdAt, appVersion, collectImages = { mainIds ->
                            if (options.includeImages) collectCoverImages(mainIds, zip) else 0
                        })
                    }
                }
                Logger.i(TAG) { "createBackup: archive ready (${temp.length()} bytes, images=${manifest.includesImages})" }

                // ── One-shot copy into the tree (delete-then-create for replace
                // semantics — the .data.json pattern). ──
                val name = backupFileName(createdAt)
                dir.findFile(name)?.delete()
                val target = dir.createFile("application/octet-stream", name)
                    ?: throw BackupException("Could not create the backup file in the storage folder")
                try {
                    context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                        temp.inputStream().use { it.copyTo(out) }
                    } ?: throw BackupException("Could not open the backup file for writing")
                } catch (e: Exception) {
                    // A failed copy must not leave a truncated archive that
                    // would then list + participate in the prune.
                    runCatching { target.delete() }
                    throw e
                }

                // ── Prune: keep the newest N. ──
                val keep = backupPreferences.keepCount.get().coerceAtLeast(1)
                val stale = listBackups().drop(keep)
                stale.forEach { old ->
                    val doc = dir.findFile(old.name)
                    if (doc != null && doc.delete()) {
                        Logger.i(TAG) { "createBackup: pruned old backup ${old.name}" }
                    }
                }
                manifest
            } finally {
                temp.delete()
            }
        }.onFailure { e ->
            Logger.e(TAG, e) { "createBackup: failed" }
        }
    }

    private fun backupFileName(createdAt: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        return BackupFormat.FILE_PREFIX + fmt.format(java.util.Date(createdAt)) + BackupFormat.FILE_EXTENSION
    }

    /** Dumps the DB tables + prefs into the zip; the callback returns the image count. */
    private fun dumpDatabase(
        zip: ZipOutputStream,
        options: BackupOptions,
        createdAt: Long,
        appVersion: String,
        collectImages: (mainIds: Set<String>) -> Int,
    ): BackupManifest {
        val counts = mutableMapOf<String, Int>()
        val mainIds = mutableSetOf<String>()

        // A read transaction across the whole dump: without it, a row written
        // between two table dumps would orphan its (already-dumped) parent -
        // the archive would fail FK enforcement on restore.
        driver.execute(null, "BEGIN DEFERRED", 0)
        try {
        BACKUP_TABLES.forEach { table ->
            val rows = dumpTable(table)
            counts[table] = rows.size
            if (table == "main_entry") {
                rows.forEach { row ->
                    val id = row["main_id"]
                    if (id is JsonPrimitive && id.content.isNotBlank()) mainIds.add(id.content)
                }
            }
            val entry = ZipEntry("${BackupFormat.DB_DIR}/$table.json")
            zip.putNextEntry(entry)
            zip.write(json.encodeToString(JsonArray.serializer(), JsonArray(rows)).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            Logger.d(TAG) { "dumpDatabase: $table → ${rows.size} rows" }
        }
        driver.execute(null, "COMMIT", 0)
        } catch (e: Exception) {
            runCatching { driver.execute(null, "ROLLBACK", 0) }
            throw e
        }

        val prefFiles = linkedSetOf<String>()
        BACKUP_PREFS_FILES.forEach { file ->
            val data = dumpPrefsFile(file)
            if (data.isNotEmpty()) {
                prefFiles.add(file)
                writePrefsEntry(zip, file, data)
            }
        }
        // The dynamic per-extension-source files (source_<id>).
        sharedPrefsDir()?.listFiles { f -> f.name.startsWith("source_") && f.name.endsWith(".xml") }
            ?.forEach { f ->
                val name = f.name.removeSuffix(".xml")
                val data = dumpPrefsFile(name)
                if (data.isNotEmpty()) {
                    prefFiles.add(name)
                    writePrefsEntry(zip, name, data)
                }
            }

        val imageCount = collectImages(mainIds)

        val manifest = BackupManifest(
            appVersion = appVersion,
            createdAt = createdAt,
            includesImages = options.includeImages && imageCount > 0,
            tables = BACKUP_TABLES,
            prefsFiles = prefFiles.toList(),
            rowCount = counts,
            imageCount = imageCount,
        )
        zip.putNextEntry(ZipEntry(BackupFormat.MANIFEST_ENTRY))
        zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        return manifest
    }

    private fun writePrefsEntry(zip: ZipOutputStream, file: String, data: JsonObject) {
        zip.putNextEntry(ZipEntry("${BackupFormat.PREFS_DIR}/$file.json"))
        zip.write(json.encodeToString(JsonObject.serializer(), data).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    // ── Generic DB dump/restore (raw driver SQL; columns from PRAGMA) ────────

    private data class Column(val name: String, val type: String)

    private fun tableColumns(table: String): List<Column> =
        driver.executeQuery(
            null,
            "PRAGMA table_info(\"$table\")",
            { cursor ->
                val rows = mutableListOf<Column>()
                while (cursor.next().value) {
                    val name = cursor.getString(1) ?: continue
                    rows.add(Column(name, (cursor.getString(2) ?: "").uppercase()))
                }
                app.cash.sqldelight.db.QueryResult.Value(rows)
            },
            0,
            null,
        ).value

    private fun dumpTable(table: String): List<JsonObject> {
        val columns = tableColumns(table)
        if (columns.isEmpty()) return emptyList()
        val rows = mutableListOf<JsonObject>()
        driver.executeQuery(
            null,
            "SELECT * FROM \"$table\"",
            { cursor ->
                while (cursor.next().value) {
                    val obj = buildJsonObject {
                        columns.forEachIndexed { index, col ->
                            val value: JsonElement = when (col.type) {
                                "INTEGER" -> cursor.getLong(index)?.let { JsonPrimitive(it) } ?: JsonNull
                                "REAL" -> cursor.getDouble(index)?.let { JsonPrimitive(it) } ?: JsonNull
                                else -> cursor.getString(index)?.let { JsonPrimitive(it) } ?: JsonNull
                            }
                            put(col.name, value)
                        }
                    }
                    rows.add(obj)
                }
                app.cash.sqldelight.db.QueryResult.Value(Unit)
            },
            0,
            null,
        )
        return rows
    }

    private fun restoreTable(table: String, rows: JsonArray): Int {
        val columns = tableColumns(table)
        if (columns.isEmpty()) return 0
        val colByName = columns.associateBy { it.name }
        var count = 0
        rows.forEach { element ->
            val row = element as? JsonObject ?: return@forEach
            // ROUND 134 verifier fixes, three in one:
            //  1. INSERT OR IGNORE (was OR REPLACE): REPLACE DELETES the
            //     conflicting row first - with foreign_keys ON, replacing a
            //     main_entry/library_category CASCADE-DELETED local rows newer
            //     than the backup. IGNORE keeps the existing row: a restore
            //     only ADDS what is missing (honest merge semantics).
            //  2. The column list is PER-ROW: only keys present in the JSON
            //     are inserted - an older backup missing a column that is
            //     NOT NULL DEFAULT in the current schema restores cleanly
            //     (the DEFAULT applies), instead of binding an explicit NULL
            //     and failing the constraint.
            //  3. The binder is typed EXPLICITLY (the raw-driver API's
            //     receiver lambda lost its inference inside the loop).
            val presentCols = columns.filter { row.containsKey(it.name) }
            if (presentCols.isEmpty()) return@forEach
            val colList = presentCols.joinToString(", ") { "\"${it.name}\"" }
            val placeholders = presentCols.joinToString(", ") { "?" }
            val sql = "INSERT OR IGNORE INTO \"$table\" ($colList) VALUES ($placeholders)"
            val binder: app.cash.sqldelight.db.SqlPreparedStatement.() -> Unit = {
                presentCols.forEachIndexed { index, col ->
                    val value = row[col.name] ?: JsonNull
                    when (colByName[col.name]?.type) {
                        // The bind overloads are NULLABLE - a JSON null binds
                        // as a SQL NULL by passing the literal null.
                        "INTEGER" -> bindLong(index, if (value is JsonNull) null else primitiveToLong(value))
                        "REAL" -> bindDouble(index, if (value is JsonNull) null else primitiveToDouble(value))
                        else -> bindString(index, if (value is JsonNull) null else primitiveToString(value))
                    }
                }
            }
            driver.execute(null, sql, presentCols.size, binder)
            count++
        }
        return count
    }

    private fun primitiveToLong(v: JsonElement): Long = when (v) {
        is JsonPrimitive -> when {
            v.isString -> v.content.toDoubleOrNull()?.toLong() ?: 0L
            v.content == "true" -> 1L
            v.content == "false" -> 0L
            else -> v.content.toLongOrNull() ?: v.content.toDoubleOrNull()?.toLong() ?: 0L
        }
        else -> 0L
    }

    private fun primitiveToDouble(v: JsonElement): Double = when (v) {
        is JsonPrimitive -> v.content.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    private fun primitiveToString(v: JsonElement): String = when (v) {
        is JsonPrimitive -> v.content
        else -> v.toString()
    }

    // ── Preferences dump/restore ─────────────────────────────────────────────

    private fun sharedPrefsDir(): java.io.File? =
        context.filesDir.parentFile?.let { java.io.File(it, "shared_prefs") }

    private fun dumpPrefsFile(name: String): JsonObject {
        val prefs: SharedPreferences = try {
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            return buildJsonObject { }
        }
        return buildJsonObject {
            prefs.all.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, buildJsonObject { put("t", "s"); put("v", value) })
                    is Int -> put(key, buildJsonObject { put("t", "i"); put("v", value.toLong()) })
                    is Long -> put(key, buildJsonObject { put("t", "l"); put("v", value) })
                    is Boolean -> put(key, buildJsonObject { put("t", "b"); put("v", if (value) 1L else 0L) })
                    is Float -> put(key, buildJsonObject { put("t", "f"); put("v", value.toDouble()) })
                    is Set<*> -> put(key, buildJsonObject {
                        put("t", "ss")
                        put("v", JsonArray(value.map { JsonPrimitive(it.toString()) }))
                    })
                    else -> Logger.w(TAG) { "dumpPrefsFile: skipping $name/$key (${value?.javaClass?.name})" }
                }
            }
        }
    }

    private fun applyPrefsFile(name: String, data: JsonObject): Int {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        var applied = 0
        val editor = prefs.edit()
        data.forEach { (key, element) ->
            if (name == "anikuta_prefs" && key in PREFS_RESTORE_EXCLUDED_KEYS) return@forEach
            val obj = element as? JsonObject ?: return@forEach
            val type = (obj["t"] as? JsonPrimitive)?.content ?: return@forEach
            val value = obj["v"] ?: return@forEach
            when (type) {
                "s" -> editor.putString(key, (value as? JsonPrimitive)?.content ?: "")
                "i" -> editor.putInt(key, primitiveToLong(value).toInt())
                "l" -> editor.putLong(key, primitiveToLong(value))
                "b" -> editor.putBoolean(key, primitiveToLong(value) != 0L)
                "f" -> editor.putFloat(key, primitiveToDouble(value).toFloat())
                "ss" -> {
                    val arr = value as? JsonArray ?: return@forEach
                    editor.putStringSet(key, arr.mapNotNull { (it as? JsonPrimitive)?.content }.toCollection(LinkedHashSet()))
                }
            }
            applied++
        }
        editor.apply()
        return applied
    }

    // ── Images ───────────────────────────────────────────────────────────────

    /**
     * Copies the downloaded series' `.cover.jpg` files into the zip. Only the
     * folders whose `.data.json` mainId is among the backed-up content are
     * read (a quick .data.json parse per folder — the trees are small).
     */
    private fun collectCoverImages(mainIds: Set<String>, zip: ZipOutputStream): Int {
        if (mainIds.isEmpty()) return 0
        val root = safRoot() ?: return 0
        val videoDir = root.findFile("video") ?: return 0
        var count = 0
        videoDir.listFiles().forEach { contentDir ->
            if (!contentDir.isDirectory) return@forEach
            val dataJson = contentDir.findFile(".data.json") ?: return@forEach
            val mainId = try {
                context.contentResolver.openInputStream(dataJson.uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    (json.parseToJsonElement(reader.readText()) as? JsonObject)?.get("mainId")
                        ?.let { (it as? JsonPrimitive)?.content }
                }
            } catch (e: Exception) {
                Logger.w(TAG, e) { "collectCoverImages: unreadable .data.json in ${contentDir.name}" }
                null
            } ?: return@forEach
            if (mainId !in mainIds) return@forEach
            val cover = contentDir.findFile(".cover.jpg") ?: return@forEach
            try {
                zip.putNextEntry(ZipEntry("${BackupFormat.IMAGES_DIR}/$mainId.jpg"))
                context.contentResolver.openInputStream(cover.uri)?.use { it.copyTo(zip) }
                zip.closeEntry()
                count++
            } catch (e: Exception) {
                Logger.w(TAG, e) { "collectCoverImages: failed to copy cover for $mainId" }
            }
        }
        Logger.i(TAG) { "collectCoverImages: $count covers carried" }
        return count
    }

    /** Copies restored cover images back into their content folders. */
    private fun restoreImages(entries: Map<String, ByteArray>): Int {
        if (entries.isEmpty()) return 0
        val root = safRoot() ?: return 0
        val videoDir = root.findFile("video") ?: return 0
        // The folder lookup: one .data.json parse per folder, matched by mainId.
        val folders = mutableMapOf<String, DocumentFile>()
        videoDir.listFiles().forEach { contentDir ->
            if (!contentDir.isDirectory) return@forEach
            val dataJson = contentDir.findFile(".data.json") ?: return@forEach
            val mainId = try {
                context.contentResolver.openInputStream(dataJson.uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    (json.parseToJsonElement(reader.readText()) as? JsonObject)?.get("mainId")
                        ?.let { (it as? JsonPrimitive)?.content }
                }
            } catch (_: Exception) { null } ?: return@forEach
            folders[mainId] = contentDir
        }
        var count = 0
        entries.forEach { (mainId, bytes) ->
            val dir = folders[mainId] ?: return@forEach
            try {
                dir.findFile(".cover.jpg")?.delete()
                val target = dir.createFile("image/jpeg", ".cover.jpg") ?: return@forEach
                context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                    out.write(bytes)
                } ?: return@forEach
                count++
            } catch (e: Exception) {
                Logger.w(TAG, e) { "restoreImages: failed for $mainId" }
            }
        }
        Logger.i(TAG) { "restoreImages: $count covers restored" }
        return count
    }

    private fun safRoot(): DocumentFile? {
        val uriStr = downloadPreferences.downloadFolderUri.get()
        if (uriStr.isBlank()) return null
        return try {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
            if (root != null && root.exists() && root.canWrite()) root else null
        } catch (e: Exception) {
            Logger.w(TAG, e) { "safRoot: unparsable tree URI" }
            null
        }
    }

    // ── Restore ──────────────────────────────────────────────────────────────

    /**
     * Restores a `.anikuta` archive picked by the user. The DB payload is
     * applied as an UPSERT inside ONE transaction (INSERT OR REPLACE by PK —
     * merge semantics: rows newer than the backup are never lost; the prefs
     * layer is applied after the DB succeeds; covers are copied back into
     * their existing content folders).
     */
    suspend fun restoreBackup(sourceUri: Uri): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = readArchive(sourceUri)
            val manifest = entries.manifest
                ?: throw BackupException("The file is not an ANI-KUTA backup (no manifest)")
            if (manifest.format != BackupFormat.FORMAT_ID) {
                throw BackupException("Unrecognized backup format: ${manifest.format}")
            }
            Logger.i(TAG) { "restoreBackup: manifest v${manifest.formatVersion} from ${manifest.appVersion} (${manifest.tables.size} tables)" }

            // ── The DB payload: one transaction, parents before children. ──
            val tableCounts = mutableMapOf<String, Int>()
            driver.execute(null, "BEGIN IMMEDIATE", 0)
            try {
                manifest.tables.forEach { table ->
                    val rows = entries.db[table] ?: return@forEach
                    val count = restoreTable(table, rows)
                    tableCounts[table] = count
                }
                driver.execute(null, "COMMIT", 0)
            } catch (e: Exception) {
                runCatching { driver.execute(null, "ROLLBACK", 0) }
                throw BackupException("Database restore failed: ${e.message}", e)
            }
            Logger.i(TAG) { "restoreBackup: DB restored (${tableCounts.values.sum()} rows)" }

            // ── The prefs payload (after the DB succeeded). ──
            val prefsApplied = mutableListOf<String>()
            entries.prefs.forEach { (file, data) ->
                applyPrefsFile(file, data)
                prefsApplied.add(file)
            }
            Logger.i(TAG) { "restoreBackup: ${prefsApplied.size} prefs files applied" }

            // ── The images payload (best-effort). ──
            val imagesRestored = restoreImages(entries.images)

            RestoreSummary(
                manifest = manifest,
                tableRowCount = tableCounts,
                prefsFilesApplied = prefsApplied,
                imagesRestored = imagesRestored,
            )
        }.onFailure { e ->
            Logger.e(TAG, e) { "restoreBackup: failed" }
        }
    }

    private class ArchiveContents {
        var manifest: BackupManifest? = null
        val db = mutableMapOf<String, JsonArray>()
        val prefs = mutableMapOf<String, JsonObject>()
        val images = mutableMapOf<String, ByteArray>()
    }

    private fun readArchive(sourceUri: Uri): ArchiveContents {
        val contents = ArchiveContents()
        context.contentResolver.openInputStream(sourceUri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        name == BackupFormat.MANIFEST_ENTRY -> {
                            contents.manifest = json.decodeFromString(
                                BackupManifest.serializer(),
                                zip.readBytes().toString(Charsets.UTF_8),
                            )
                        }
                        name.startsWith("${BackupFormat.DB_DIR}/") && name.endsWith(".json") -> {
                            val table = name.removePrefix("${BackupFormat.DB_DIR}/").removeSuffix(".json")
                            contents.db[table] = json.parseToJsonElement(
                                zip.readBytes().toString(Charsets.UTF_8),
                            ) as? JsonArray ?: JsonArray(emptyList())
                        }
                        name.startsWith("${BackupFormat.PREFS_DIR}/") && name.endsWith(".json") -> {
                            val file = name.removePrefix("${BackupFormat.PREFS_DIR}/").removeSuffix(".json")
                            (json.parseToJsonElement(zip.readBytes().toString(Charsets.UTF_8)) as? JsonObject)
                                ?.let { contents.prefs[file] = it }
                        }
                        name.startsWith("${BackupFormat.IMAGES_DIR}/") -> {
                            val mainId = name.removePrefix("${BackupFormat.IMAGES_DIR}/").removeSuffix(".jpg")
                            if (mainId.isNotBlank()) contents.images[mainId] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                }
            }
        } ?: throw BackupException("Could not open the selected backup file")
        return contents
    }

    companion object {
        private const val TAG = "Anikuta:Core:Backup"
    }
}

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
