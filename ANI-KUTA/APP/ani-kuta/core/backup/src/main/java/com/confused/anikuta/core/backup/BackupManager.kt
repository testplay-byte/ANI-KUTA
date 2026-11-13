package com.confused.anikuta.core.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.cash.sqldelight.db.SqlDriver
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.download.DownloadPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The backup & restore engine (round 134, D-816; round 135 granular rework).
 *
 * Creates and restores `.anikuta` ZIP archives (see [BackupFormat]) in the
 * app's selected SAF folder under `backup/`, keeping the newest N files. The
 * DB payload is dumped/restored GENERICALLY through the raw SqlDriver
 * (columns read from PRAGMA at runtime) so the format tracks the schema
 * without hand-written codecs; the preferences payload carries the full
 * SharedPreferences layer (settings + the AniList token + the extension /
 * CloudStream repo + plugin setup); the optional images payload carries the
 * downloaded series' cover files; the optional extension payload (round 135)
 * carries the installed Aniyomi APKs + CloudStream .cs3 plugins + their trust
 * state through the app-layer [ExtensionBackupBridge] (restore auto-installs
 * and re-trusts).
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
    private val extensionBridge: ExtensionBackupBridge? = null,
    private val offlineImageStore: OfflineImageStore? = null,
) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    /**
     * The fetch client for the offline-image payload (round 136) — modest
     * timeouts; every failure just skips that image (the backup still
     * carries the rest).
     */
    private val fetchClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            // ROUND 136 (the verifier's F4): the WHOLE-call budget — a slow
            // drip server can never hold a fetch slot past this.
            .callTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

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
        // ROUND 139 (D-841): EVERYTHING in the backup folder is listed — the
        // native .anikuta archives AND the interop exports (.tachibk/.json/
        // .txt) that live beside them. The prune (createBackup) still only
        // counts NATIVE archives against the keep-count.
        return dir.listFiles()
            .filter { it.name != null && (it.isFile || it.length() > 0) }
            .map { BackupFileInfo(it.name!!, it.uri.toString(), it.lastModified(), it.length()) }
            .sortedByDescending { it.createdAt }
    }

    /**
     * ROUND 140 (D-848): deletes one backup file from the folder by name —
     * the long-press Delete action on the existing-backups list. Returns
     * false when the file is gone already or the folder refuses.
     */
    fun deleteBackup(name: String): Boolean {
        val dir = backupFolder() ?: return false
        val doc = dir.findFile(name) ?: return false
        val ok = doc.delete()
        if (ok) Logger.i(TAG) { "deleteBackup: $name deleted" }
        return ok
    }

    // ── Inspect ──────────────────────────────────────────────────────────────

    /**
     * Reads ONLY the manifest of a picked `.anikuta` archive (the restore
     * page's pre-confirm preview). Null when the file is not a backup.
     */
    fun readManifest(sourceUri: Uri): BackupManifest? = runCatching {
        context.contentResolver.openInputStream(sourceUri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == BackupFormat.MANIFEST_ENTRY) {
                        val manifest = json.decodeFromString(
                            BackupManifest.serializer(),
                            zip.readBytes().toString(Charsets.UTF_8),
                        )
                        zip.closeEntry()
                        return@runCatching manifest
                    }
                    zip.closeEntry()
                }
            }
        }
        null
    }.onFailure { e ->
        Logger.w(TAG, e) { "readManifest: failed for $sourceUri" }
    }.getOrNull()

    // ── Create ───────────────────────────────────────────────────────────────

    /**
     * Creates a backup in the backup folder, carrying exactly the slices the
     * [options] turn on. Returns the manifest on success.
     */
    suspend fun createBackup(
        options: BackupOptions,
        onProgress: ((BackupProgress) -> Unit)? = null,
    ): Result<BackupManifest> = withContext(Dispatchers.IO) {
        runCatching {
            require(options.anythingEnabled) { "Nothing is selected to back up" }
            val dir = backupFolder()
                ?: throw BackupException("No storage folder is set (or it is not writable)")
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
            } catch (_: Exception) { "?" }
            val createdAt = System.currentTimeMillis()
            onProgress?.invoke(BackupProgress("Preparing the archive", 0, 0))

            // ── Assemble the archive in a cacheDir temp first (the SAF write
            // is a single truncating copy afterwards). ──
            val temp = java.io.File(context.cacheDir, "backup-${System.currentTimeMillis()}.anikuta.tmp")
            try {
                val manifest = BufferedOutputStream(FileOutputStream(temp)).use { raw ->
                    ZipOutputStream(raw).use { zip ->
                        dumpDatabase(zip, options, createdAt, appVersion, onProgress)
                    }
                }
                Logger.i(TAG) {
                    "createBackup: archive ready (${temp.length()} bytes, images=${manifest.includesImages}, " +
                        "ext=${manifest.includesExtensions} apk=${manifest.aniyomiApkCount} cs=${manifest.csPluginCount})"
                }

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

                // ── Prune: keep the newest N NATIVE archives (the interop
                // exports beside them are never auto-deleted — D-841). ──
                val keep = backupPreferences.keepCount.get().coerceAtLeast(1)
                val stale = listBackups()
                    .filter { it.name.startsWith(BackupFormat.FILE_PREFIX) && it.name.endsWith(BackupFormat.FILE_EXTENSION) }
                    .drop(keep)
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

    /** The D-840 progress feed's friendly per-table label. */
    private fun tableLabel(table: String): String = when (table) {
        "main_entry" -> "Library entries"
        "content_details" -> "Entry details"
        "library_item", "library_category" -> "Library"
        "watch_progress" -> "Watch history"
        "watch_session" -> "Watch sessions"
        "user_rating", "user_episode_rating" -> "Ratings"
        "track_entry", "content_tracking_state" -> "Tracking"
        "app_settings" -> "App settings"
        "downloaded_episode" -> "Downloads"
        "notification_config" -> "Notifications"
        "anime_update_state", "episode_schedule", "episode_update" -> "Schedules"
        "data_cache_episode" -> "Episode details"
        "browse_cache" -> "Browse cache"
        "data_source", "system", "genre", "content_genre" -> "Catalog"
        else -> table
    }

    /** Dumps the selected DB tables + prefs + extension payload into the zip. */
    private fun dumpDatabase(
        zip: ZipOutputStream,
        options: BackupOptions,
        createdAt: Long,
        appVersion: String,
        onProgress: ((BackupProgress) -> Unit)? = null,
    ): BackupManifest {
        val counts = mutableMapOf<String, Int>()
        val mainIds = mutableSetOf<String>()
        val tables = options.tables

        // A read transaction across the whole dump: without it, a row written
        // between two table dumps would orphan its (already-dumped) parent -
        // the archive would fail FK enforcement on restore.
        driver.execute(null, "BEGIN DEFERRED", 0)
        try {
            tables.forEachIndexed { index, table ->
                // ROUND 139 (D-840): the live "what is being backed up" feed.
                onProgress?.invoke(
                    BackupProgress(tableLabel(table), index + 1, tables.size),
                )
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

        val ownedPrefs = BACKUP_PREFS_FILES.filter { options.ownsPrefsFile(it) }
        val prefFiles = linkedSetOf<String>()
        ownedPrefs.forEachIndexed { index, file ->
            onProgress?.invoke(BackupProgress("Settings", index + 1, ownedPrefs.size + 1))
            val data = dumpPrefsFile(file) { key -> options.includePrefsKey(file, key) }
            if (data.isNotEmpty()) {
                prefFiles.add(file)
                writePrefsEntry(zip, file, data)
            }
        }
        // The dynamic per-extension-source files (source_<id>) — the source
        // settings bucket.
        if (options.settingsSources) {
            sharedPrefsDir()?.listFiles { f -> f.name.startsWith("source_") && f.name.endsWith(".xml") }
                ?.forEach { f ->
                    val name = f.name.removeSuffix(".xml")
                    val data = dumpPrefsFile(name)
                    if (data.isNotEmpty()) {
                        prefFiles.add(name)
                        writePrefsEntry(zip, name, data)
                    }
                }
        }

        val imageCount = if (options.imageCovers) {
            onProgress?.invoke(BackupProgress("Cover images", 0, 0))
            collectCoverImages(mainIds, zip)
        } else 0

        // ── The extension payload (round 135): APKs + .cs3 files + state.json.
        // The repo LISTS ride in the prefs files (owned by the repos switches).
        var aniyomiCount = 0
        var csCount = 0
        val extensionsRequested = options.extensionRepos || options.extensionAniyomi ||
            options.extensionCloudstream
        val bridge = extensionBridge
        // ROUND 136: declared at FUNCTION scope — the offline-image collection
        // below needs the icon URLs from both written lists.
        val writtenAniyomi = mutableListOf<AniyomiApkInfo>()
        val writtenCs = mutableListOf<CloudstreamPluginInfo>()
        if (extensionsRequested && bridge != null) {
            onProgress?.invoke(BackupProgress("Extensions", 0, 0))
            val aniyomi = if (options.extensionAniyomi) {
                runCatching { bridge.collectAniyomiApks() }
                    .onFailure { Logger.w(TAG, it) { "collectAniyomiApks failed" } }
                    .getOrDefault(emptyList())
            } else emptyList()
            val cs = if (options.extensionCloudstream) {
                runCatching { bridge.collectCloudstreamPlugins() }
                    .onFailure { Logger.w(TAG, it) { "collectCloudstreamPlugins failed" } }
                    .getOrDefault(emptyList())
            } else emptyList()

            aniyomi.forEach { apk ->
                try {
                    zip.putNextEntry(ZipEntry("${BackupFormat.EXT_APK_DIR}/${apk.info.pkgName}.apk"))
                    zip.write(apk.bytes)
                    zip.closeEntry()
                    writtenAniyomi += apk.info
                } catch (e: Exception) {
                    Logger.w(TAG, e) { "createBackup: failed to carry APK for ${apk.info.pkgName}" }
                }
            }            // The .cs3 entries are INDEX-keyed into the state list — the
            // restore maps them back by position (deterministic, no name
            // sanitization edge cases). ROUND 135 (the verifier's F3): the
            // key is the DENSE written index — naming by the collection
            // index would misalign every later plugin after one failed write.
            cs.forEach { plugin ->
                try {
                    zip.putNextEntry(ZipEntry("${BackupFormat.EXT_CS_DIR}/${writtenCs.size}.cs3"))
                    zip.write(plugin.bytes)
                    zip.closeEntry()
                    writtenCs += plugin.info
                } catch (e: Exception) {
                    Logger.w(TAG, e) { "createBackup: failed to carry plugin ${plugin.info.internalName}" }
                }
            }

            val state = ExtensionBackupState(
                aniyomi = writtenAniyomi,
                cloudstream = writtenCs,
            )
            zip.putNextEntry(ZipEntry(BackupFormat.EXT_STATE_ENTRY))
            zip.write(json.encodeToString(ExtensionBackupState.serializer(), state).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            aniyomiCount = writtenAniyomi.size
            csCount = writtenCs.size
            Logger.i(TAG) { "createBackup: extension payload — $aniyomiCount apk(s), $csCount plugin(s)" }
        } else if (extensionsRequested) {
            Logger.w(TAG) { "createBackup: extensions selected but no bridge is registered — payload skipped" }
        }

        // ── The OFFLINE-IMAGE payload (round 136, the device report's
        // headline: a restored library showed NO covers offline). Carried
        // under the "Library cover images" switch: every library entry's
        // cover/banner URLs (both URL columns — the rendered pick is
        // preference-dependent) plus the extension icons, fetched at backup
        // time and keyed by the URL's SHA-256; the app's Coil interceptor
        // serves them back offline after a restore. ──
        val offlineCount = if (options.imageCovers && offlineImageStore != null) {
            onProgress?.invoke(BackupProgress("Offline covers", 0, 0))
            collectOfflineImages(
                zip = zip,
                // Both placeholders the UI resolves to 64 (the verifier's F5 — a literal
                // %exact_size% URL would 404 AND hash differently from what the UI renders).
                csIcons = writtenCs.mapNotNull {
                    it.iconUrl?.replace("%size%", "64")?.replace("%exact_size%", "64")
                },
                aniyomiIcons = writtenAniyomi.map { it.iconUrl }.filter { it.isNotBlank() },
            )
        } else 0

        // ── The PROFILE AVATAR (round 137, the full-capability audit): the
        // crop editor writes `filesDir/avatar_<timestamp>.jpg` and the pref
        // carries its absolute path — a restored prefs payload would dangle.
        // The file rides as `profile/avatar.jpg`; restore writes a fresh
        // device-local file and re-points the pref at it. ──
        var avatarIncluded = false
        runCatching {
            val avatarPref = backupPreferencesAvatarPath()
            if (avatarPref != null) {
                val file = java.io.File(avatarPref)
                if (file.isFile && file.length() > 0 && file.length() < 8 * 1024 * 1024) {
                    zip.putNextEntry(ZipEntry(BackupFormat.PROFILE_AVATAR_ENTRY))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    avatarIncluded = true
                }
            }
        }

        val manifest = BackupManifest(
            appVersion = appVersion,
            createdAt = createdAt,
            includesImages = options.imageCovers && imageCount > 0,
            includesAvatar = avatarIncluded,
            tables = tables,
            prefsFiles = prefFiles.toList(),
            rowCount = counts,
            imageCount = imageCount,
            offlineImageCount = offlineCount,
            includesExtensions = extensionsRequested && bridge != null &&
                (aniyomiCount > 0 || csCount > 0),
            aniyomiApkCount = aniyomiCount,
            csPluginCount = csCount,
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

    /**
     * The profile avatar's absolute path from `anikuta_prefs` (round 137) —
     * non-blank + file-shaped only; a remote URL avatar needs no file payload.
     */
    private fun backupPreferencesAvatarPath(): String? = runCatching {
        val raw = context.getSharedPreferences("anikuta_prefs", Context.MODE_PRIVATE)
            .getString("profile_avatar_url", null) ?: return null
        raw.takeIf { it.startsWith("/") || it.startsWith("file://") }?.removePrefix("file://")
    }.getOrNull()

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

    private fun dumpPrefsFile(name: String, keyFilter: (String) -> Boolean = { true }): JsonObject {
        val prefs: SharedPreferences = try {
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            return buildJsonObject { }
        }
        return buildJsonObject {
            prefs.all.forEach { (key, value) ->
                if (!keyFilter(key)) return@forEach
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
            if (key in PREFS_RESTORE_EXCLUDED_KEYS) return@forEach
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
     * Copies the downloaded series' cover files into the zip. Only the
     * folders whose `.data.json` mainId is among the backed-up content are
     * read (a quick .data.json parse per folder — the trees are small).
     *
     * ROUND 135: BOTH cover names are accepted — the writer creates
     * `.cover.jpg` (dot-prefixed) while some paths read `cover.jpg` without
     * the dot; checking only one name under-counted the payload (the
     * round-135 "4 covers" report).
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
            val cover = contentDir.findFile(".cover.jpg")
                ?: contentDir.findFile("cover.jpg")
                ?: return@forEach
            // ROUND 135 (the verifier's F5): the bytes are read and VERIFIED
            // before the entry + the count — an unreadable or empty cover
            // must not inflate imageCount with a zero-byte zip entry.
            val bytes = try {
                context.contentResolver.openInputStream(cover.uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                Logger.w(TAG, e) { "collectCoverImages: failed to read cover for $mainId" }
                null
            }
            if (bytes == null || bytes.isEmpty()) return@forEach
            try {
                zip.putNextEntry(ZipEntry("${BackupFormat.IMAGES_DIR}/$mainId.jpg"))
                zip.write(bytes)
                zip.closeEntry()
                count++
            } catch (e: Exception) {
                Logger.w(TAG, e) { "collectCoverImages: failed to write cover for $mainId" }
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
                // Write back as the canonical dot-prefixed name; clear the
                // dot-less variant so a stale copy never shadows the restored one.
                dir.findFile(".cover.jpg")?.delete()
                dir.findFile("cover.jpg")?.delete()
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

    // ── Offline images (round 136) ───────────────────────────────────────────

    /**
     * Collects the library's cover/banner URLs + the extension icons, FETCHES
     * each (capped, 8-wide parallel; a failure skips that image), and writes
     * them into the zip as `offline_images/<sha256(url)>.img`. On restore the
     * bytes land in [OfflineImageStore] and the app's Coil interceptor serves
     * them for their original URLs — the grids paint with the network OFF.
     */
    private fun collectOfflineImages(
        zip: ZipOutputStream,
        csIcons: List<String>,
        aniyomiIcons: List<String>,
    ): Int {
        val store = offlineImageStore ?: return 0
        // The URL set: content_details' THREE image columns per row (the
        // library grid renders ext_thumbnail_url ?: data_cover_url depending
        // on the display preference — carry BOTH; the details header adds the
        // banner), plus the extension icons.
        // ROUND 136 (the verifier's F6): the DB URLs come FIRST (the library
        // covers are the priority; the extension icons ride last), and the
        // raw query is guarded — a future schema change must never fail the
        // whole backup.
        val urls = LinkedHashSet<String>()
        runCatching {
        driver.executeQuery(
            null,
            "SELECT data_cover_url, ext_thumbnail_url, data_banner_url FROM content_details",
            { cursor ->
                while (cursor.next().value) {
                    for (col in 0..2) {
                        val url = cursor.getString(col)
                        if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                            urls.add(url)
                        }
                    }
                }
                app.cash.sqldelight.db.QueryResult.Value(Unit)
            },
            0,
            null,
        )
        }.onFailure { Logger.w(TAG, it) { "collectOfflineImages: url query failed" } }
        urls += csIcons
        urls += aniyomiIcons

        // ROUND 136 (the verifier's F4): no network = skip the whole payload
        // instead of burning 400 x 10s connect timeouts (the periodic
        // BackupWorker runs WITHOUT a network constraint).
        if (!isNetworkAvailable()) {
            Logger.i(TAG) { "collectOfflineImages: no network — payload skipped" }
            return 0
        }
        val capped = urls.take(OFFLINE_IMAGE_CAP)
        Logger.i(TAG) { "collectOfflineImages: ${urls.size} url(s) found — carrying ${capped.size}" }

        // The fetched (hash, bytes) pairs — zip writes are SERIALIZED (a
        // ZipOutputStream is not thread-safe), so the parallel phase only
        // fetches into memory and the write phase runs single-threaded.
        // ROUND 136 (the verifier's F3/F4): a TOTAL payload budget caps the
        // resident set, and a wall-clock deadline stops the fetch phase so a
        // flaky network can never stretch the backup by minutes.
        val fetched = java.util.Collections.synchronizedMap(LinkedHashMap<String, ByteArray>())
        val totalBytes = java.util.concurrent.atomic.AtomicLong(0)
        val deadlineAt = System.nanoTime() + OFFLINE_FETCH_DEADLINE_NS
        kotlinx.coroutines.runBlocking {
            val permits = Semaphore(OFFLINE_FETCH_CONCURRENCY)
            capped.map { url ->
                async(Dispatchers.IO) {
                    permits.withPermit {
                        if (System.nanoTime() > deadlineAt ||
                            totalBytes.get() >= OFFLINE_TOTAL_BUDGET_BYTES
                        ) return@withPermit
                        val bytes = fetchImage(url) ?: return@withPermit
                        if (totalBytes.addAndGet(bytes.size.toLong()) > OFFLINE_TOTAL_BUDGET_BYTES) {
                            // Over budget — drop THIS image (an honest cap).
                            totalBytes.addAndGet(-bytes.size.toLong())
                            return@withPermit
                        }
                        fetched[store.urlHash(url)] = bytes
                    }
                }
            }.forEach { it.await() }
        }
        var count = 0
        fetched.forEach { (hash, bytes) ->
            try {
                zip.putNextEntry(ZipEntry("${BackupFormat.OFFLINE_IMAGES_DIR}/$hash.img"))
                zip.write(bytes)
                zip.closeEntry()
                count++
            } catch (e: Exception) {
                Logger.w(TAG, e) { "collectOfflineImages: zip write failed for $hash" }
            }
        }
        Logger.i(TAG) { "collectOfflineImages: $count image(s) carried" }
        return count
    }

    /**
     * One URL fetch — null on any failure or an oversized body.
     * ROUND 136 (the verifier's F3): STREAMED with a hard stop at the per-
     * image cap — a chunked response without Content-Length can no longer
     * buffer unbounded bytes.
     */
    private fun fetchImage(url: String): ByteArray? = try {
        val request = okhttp3.Request.Builder().url(url).build()
        fetchClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Logger.d(TAG) { "fetchImage: HTTP ${response.code} for $url" }
                return null
            }
            val body = response.body ?: return null
            val input = body.byteStream()
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read == -1) break
                if (buffer.size() + read > OFFLINE_IMAGE_MAX_BYTES) {
                    Logger.d(TAG) { "fetchImage: over the per-image cap for $url" }
                    return null
                }
                buffer.write(chunk, 0, read)
            }
            val bytes = buffer.toByteArray()
            if (bytes.isEmpty()) null else bytes
        }
    } catch (e: Exception) {
        Logger.d(TAG) { "fetchImage: failed for $url (${e.message})" }
        null
    }

    /** The fail-fast connectivity probe (the verifier's F4). */
    private fun isNetworkAvailable(): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) {
        false
    }

    // ── Restore ──────────────────────────────────────────────────────────────

    /**
     * Restores a `.anikuta` archive picked by the user. The DB payload is
     * applied as a MERGE inside ONE transaction (INSERT OR IGNORE by PK —
     * merge semantics: rows newer than the backup are never lost). The prefs
     * layer is applied after the DB succeeds; the CloudStream .cs3 files +
     * records are re-registered AFTER the prefs (the bridge's upserts are
     * the record authority — a carried donor plugins_json can never clobber
     * the device-local paths); covers are copied back into their existing
     * content folders; the Aniyomi APKs auto-install last (the user confirms
     * each system prompt) and the backed-up trust state is re-applied.
     */
    suspend fun restoreBackup(
        sourceUri: Uri,
        /**
         * ROUND 139 (D-844): the BATCH-INSTALL picker — called once with the
         * missing extensions (already filtered to NOT-installed + APK-carrying)
         * BEFORE any install prompt; returns the confirmed subset (empty = the
         * user declined them all). Null = the legacy install-all flow.
         */
        extensionInstallPicker: (suspend (List<AniyomiApkInfo>) -> List<AniyomiApkInfo>)? = null,
    ): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val bridge = extensionBridge
            val entries = readArchive(sourceUri)
            val manifest = entries.manifest
                ?: throw BackupException("The file is not an ANI-KUTA backup (no manifest)")
            if (manifest.format != BackupFormat.FORMAT_ID) {
                throw BackupException("Unrecognized backup format: ${manifest.format}")
            }
            Logger.i(TAG) {
                "restoreBackup: manifest v${manifest.formatVersion} from ${manifest.appVersion} " +
                    "(${manifest.tables.size} tables, apk=${manifest.aniyomiApkCount}, cs=${manifest.csPluginCount})"
            }

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

            // ── The prefs payload (AFTER the DB, BEFORE the extension
            // re-registration). ROUND 135 (the verifier's P0): the bridge's
            // record upserts must land AFTER the prefs application — a
            // carried donor `plugins_json` (v1-format archives) would
            // otherwise overwrite the freshly-registered device-local records
            // with the donor's absolute filePaths, and the plugin loader
            // would delete every file-less record on the next load. With the
            // bridge LAST, its upserts are authoritative; nothing triggers a
            // plugin load mid-restore, so the files are still on disk before
            // any read. ──
            val prefsApplied = mutableListOf<String>()
            entries.prefs.forEach { (file, data) ->
                applyPrefsFile(file, data)
                prefsApplied.add(file)
            }
            Logger.i(TAG) { "restoreBackup: ${prefsApplied.size} prefs files applied" }
            // ROUND 136 (the device report: "the repositories were not
            // added"): the prefs pipeline writes BEHIND the extension
            // systems' backs — refresh the in-memory repo stores + the trust
            // set so the Extensions page reflects the restore immediately.
            bridge?.runCatching { onPrefsRestored() }
                ?.onFailure { Logger.w(TAG, it) { "restoreBackup: onPrefsRestored failed" } }

            // ── The CloudStream .cs3 FILES + records (after prefs — see the
            // ordering note above; the files land BEFORE any loader read). ──
            var csRestored = 0
            // ROUND 148: the failed plugins' names — the summary surfaces them
            // beside the restored/carried counts (mirrors aniyomiFailed).
            val csPluginsFailed = mutableListOf<String>()
            val extState = entries.extState
            if (extState != null && bridge != null) {
                extState.cloudstream.forEachIndexed { index, info ->
                    val bytes = entries.csPlugins[index]
                    if (bytes == null) {
                        Logger.w(TAG) { "restoreBackup: plugin #$index (${info.internalName}) has no file in the archive" }
                        csPluginsFailed += info.name.ifBlank { info.internalName }
                        return@forEachIndexed
                    }
                    val ok = runCatching { bridge.restoreCloudstreamPlugin(info, bytes) }
                        .onFailure { Logger.w(TAG, it) { "restoreBackup: plugin ${info.internalName} failed" } }
                        .getOrDefault(false)
                    if (ok) {
                        csRestored++
                    } else {
                        csPluginsFailed += info.name.ifBlank { info.internalName }
                    }
                }
                Logger.i(TAG) {
                    "restoreBackup: $csRestored/${extState.cloudstream.size} cs3 plugins written, " +
                        "${csPluginsFailed.size} failed"
                }
                // ROUND 136: reload the plugin manager — the restored plugins
                // appear in the Extensions page LIVE (all records have files).
                bridge.runCatching { onCloudstreamRestored() }
                    .onFailure { Logger.w(TAG, it) { "restoreBackup: onCloudstreamRestored failed" } }
            }

            // ── The images payload (best-effort). ──
            val imagesRestored = restoreImages(entries.images)

            // ── The OFFLINE-IMAGE payload (round 136): the URL-hash-keyed
            // covers/banners/icons land in the store, where the app's Coil
            // interceptor serves them for their original URLs — the restored
            // library paints with the network OFF (the device report's
            // headline fix). ──
            val offlineRestored = offlineImageStore?.let { store ->
                entries.offlineImages.forEach { (hash, bytes) ->
                    store.storeHash(hash, bytes)
                }
                entries.offlineImages.size
            } ?: 0
            if (offlineRestored > 0) {
                Logger.i(TAG) { "restoreBackup: $offlineRestored offline image(s) written to the store" }
            }

            // ── The PROFILE AVATAR (round 137): AFTER the prefs payload — the
            // restored anikuta_prefs carries the DONOR device's absolute path,
            // which dangles here. A fresh device-local file is written and the
            // pref re-pointed at it. ──
            val avatarRestored = entries.avatarBytes?.let { bytes ->
                runCatching {
                    val dest = java.io.File(context.filesDir, "avatar_${System.currentTimeMillis()}.jpg")
                    dest.writeBytes(bytes)
                    context.getSharedPreferences("anikuta_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("profile_avatar_url", dest.absolutePath)
                        .apply()
                    Logger.i(TAG) { "restoreBackup: avatar restored → ${dest.name}" }
                    true
                }.getOrElse { e ->
                    Logger.w(TAG, e) { "restoreBackup: avatar write failed" }
                    false
                }
            } ?: false

            // ── The Aniyomi extension payload (after prefs — the trust keys
            // may ride in anikuta_prefs; the bridge ADDs on top). One system
            // prompt per APK, sequential; a cancel moves on. ──
            var aniyomiInstalled = 0
            var aniyomiSkipped = 0
            var aniyomiDeclined = 0
            val aniyomiFailed = mutableListOf<String>()
            if (extState != null && bridge != null && extState.aniyomi.isNotEmpty()) {
                // ── ROUND 139 (D-844): the BATCH-INSTALL PICKER — the missing
                // extensions are collected FIRST and offered to the user as a
                // checkbox selection (default all); only the confirmed subset
                // is installed (each still gets its own Android install
                // prompt). Without a picker the old install-all flow runs.
                val missing = extState.aniyomi.mapNotNull { info ->
                    val bytes = entries.aniyomiApks[info.pkgName]
                    if (bytes == null) {
                        Logger.w(TAG) { "restoreBackup: ${info.pkgName} has no APK in the archive" }
                        aniyomiFailed += info.name
                        return@mapNotNull null
                    }
                    if (bridge.isPackageInstalled(info.pkgName)) {
                        // Already on the device — just align the trust/enable state.
                        aniyomiSkipped++
                        runCatching {
                            bridge.applyAniyomiState(info.pkgName, info.trusted, info.enabled)
                        }
                        return@mapNotNull null
                    }
                    info to bytes
                }
                val toInstall = if (missing.isNotEmpty() && extensionInstallPicker != null) {
                    val picked = runCatching {
                        extensionInstallPicker!!.invoke(missing.map { it.first })
                    }.getOrElse { emptyList() }.toSet()
                    missing.filter { it.first in picked }
                } else {
                    missing
                }
                aniyomiDeclined = missing.size - toInstall.size
                toInstall.forEach { (info, bytes) ->
                    val ok = bridge.installAniyomiApk(info.pkgName, bytes)
                    if (ok) {
                        aniyomiInstalled++
                        runCatching {
                            bridge.applyAniyomiState(info.pkgName, info.trusted, info.enabled)
                        }
                    } else {
                        aniyomiFailed += info.name
                    }
                }
                Logger.i(TAG) {
                    "restoreBackup: aniyomi — $aniyomiInstalled installed, $aniyomiSkipped already present, " +
                        "$aniyomiDeclined declined by the user, ${aniyomiFailed.size} failed"
                }
            }

            RestoreSummary(
                manifest = manifest,
                tableRowCount = tableCounts,
                prefsFilesApplied = prefsApplied,
                imagesRestored = imagesRestored,
                offlineImagesRestored = offlineRestored,
                aniyomiInstalled = aniyomiInstalled,
                aniyomiFailed = aniyomiFailed,
                aniyomiAlreadyInstalled = aniyomiSkipped,
                aniyomiDeclined = aniyomiDeclined,
                csPluginsRestored = csRestored,
                csPluginsFailed = csPluginsFailed,
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
        var extState: ExtensionBackupState? = null
        val aniyomiApks = mutableMapOf<String, ByteArray>()
        /** Index-keyed into [ExtensionBackupState.cloudstream]. */
        val csPlugins = mutableMapOf<Int, ByteArray>()
        /** ROUND 136: URL-hash-keyed offline images. */
        val offlineImages = mutableMapOf<String, ByteArray>()
        /** ROUND 137: the profile avatar file bytes. */
        var avatarBytes: ByteArray? = null
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
                        name == BackupFormat.EXT_STATE_ENTRY -> {
                            contents.extState = try {
                                json.decodeFromString(
                                    ExtensionBackupState.serializer(),
                                    zip.readBytes().toString(Charsets.UTF_8),
                                )
                            } catch (e: Exception) {
                                Logger.w(TAG, e) { "readArchive: unreadable ext/state.json — payload skipped" }
                                null
                            }
                        }
                        name.startsWith("${BackupFormat.EXT_APK_DIR}/") && name.endsWith(".apk") -> {
                            val pkg = name.removePrefix("${BackupFormat.EXT_APK_DIR}/").removeSuffix(".apk")
                            if (pkg.isNotBlank()) contents.aniyomiApks[pkg] = zip.readBytes()
                        }
                        name.startsWith("${BackupFormat.EXT_CS_DIR}/") && name.endsWith(".cs3") -> {
                            val index = name.removePrefix("${BackupFormat.EXT_CS_DIR}/")
                                .removeSuffix(".cs3").toIntOrNull()
                            if (index != null) contents.csPlugins[index] = zip.readBytes()
                        }
                        name.startsWith("${BackupFormat.OFFLINE_IMAGES_DIR}/") && name.endsWith(".img") -> {
                            val hash = name.removePrefix("${BackupFormat.OFFLINE_IMAGES_DIR}/")
                                .removeSuffix(".img")
                            if (hash.length == 64) contents.offlineImages[hash] = zip.readBytes()
                        }
                        name == BackupFormat.PROFILE_AVATAR_ENTRY -> {
                            contents.avatarBytes = zip.readBytes()
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

        /** The carried-images cap — the library set is the natural boundary. */
        private const val OFFLINE_IMAGE_CAP = 400

        /** A single carried image larger than this is skipped (posters, not panos). */
        private const val OFFLINE_IMAGE_MAX_BYTES = 2 * 1024 * 1024

        /** The parallel fetch width. */
        private const val OFFLINE_FETCH_CONCURRENCY = 8

        /** The TOTAL resident budget for the payload (the verifier's F3b). */
        private const val OFFLINE_TOTAL_BUDGET_BYTES = 128L * 1024 * 1024

        /** The wall-clock cap on the whole fetch phase (the verifier's F4). */
        private val OFFLINE_FETCH_DEADLINE_NS: Long = 60_000_000_000L
    }
}

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
