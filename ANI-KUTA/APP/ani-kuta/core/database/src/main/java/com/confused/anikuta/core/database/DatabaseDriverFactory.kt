package com.confused.anikuta.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Creates the SQLDelight driver for ANI-KUTA's database.
 * The database file is "anikuta.db".
 *
 * D.0 schema migration fix: the `download_queue` + `downloaded_episode` tables
 * were re-keyed by `mainId` + `episodeKey` (was `episode_key` only). The old
 * tables on existing dev installs don't have the new columns.
 *
 * WHY onUpgrade DOESN'T WORK: SQLDelight derives the schema version from `.sqm`
 * migration files. With no `.sqm` files, the version is 1. The old DB is also
 * version 1. When SQLDelight opens it, version 1 == 1, so onUpgrade is NEVER
 * called — the old schema persists.
 *
 * FIX: override `onOpen` (called every time the DB opens, regardless of version)
 * to check if `download_queue` has the `main_id` column. If not, drop the
 * download tables + call `onCreate(db)` to recreate them with the new schema.
 *
 * This preserves all other data (content, library, anilist details, etc.) —
 * only the download tables are wiped (they had no production data).
 */
class DatabaseDriverFactory(private val context: Context) {
    fun create(): SqlDriver {
        return AndroidSqliteDriver(
            schema = AnikutaDatabase.Schema,
            context = context,
            name = "anikuta.db",
            callback = object : AndroidSqliteDriver.Callback(AnikutaDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    migrateDownloadTablesIfNeeded(db)
                }

                /**
                 * Checks if `download_queue` has the `main_id` column (the NEW schema).
                 * If not (old schema), drops `download_queue` + `downloaded_episode`
                 * + calls `onCreate(db)` to recreate them with the new schema.
                 *
                 * `onCreate` runs `CREATE TABLE IF NOT EXISTS` for ALL tables —
                 * existing tables (content, library, etc.) are untouched; the
                 * dropped download tables are recreated.
                 */
                private fun migrateDownloadTablesIfNeeded(db: SupportSQLiteDatabase) {
                    // Check if download_queue exists + has main_id.
                    val cursor = db.query("PRAGMA table_info(download_queue)")
                    val hasMainId = cursor.use { c ->
                        if (!c.moveToFirst()) {
                            // Table doesn't exist — onCreate will create it. Nothing to do.
                            return
                        }
                        val nameIndex = c.getColumnIndex("name")
                        if (nameIndex < 0) return
                        var found = false
                        while (c.moveToNext()) {
                            if (c.getString(nameIndex) == "main_id") {
                                found = true
                                break
                            }
                        }
                        found
                    }

                    if (!hasMainId) {
                        // Old schema detected — drop + recreate the download tables.
                        db.execSQL("DROP TABLE IF EXISTS download_queue")
                        db.execSQL("DROP TABLE IF EXISTS downloaded_episode")
                        // Recreate all tables — CREATE TABLE IF NOT EXISTS preserves
                        // existing tables; the dropped download tables are recreated.
                        onCreate(db)
                    }
                }
            },
        )
    }
}
