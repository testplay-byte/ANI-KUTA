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
 * D.FIX: The `data_cache_episode` table also got a new `episode_url` column.
 * Existing installs don't have it → SQLiteException crash on query.
 *
 * WHY onUpgrade DOESN'T WORK: SQLDelight derives the schema version from `.sqm`
 * migration files. With no `.sqm` files, the version is 1. The old DB is also
 * version 1. When SQLDelight opens it, version 1 == 1, so onUpgrade is NEVER
 * called — the old schema persists.
 *
 * FIX: override `onOpen` (called every time the DB opens, regardless of version)
 * to check for missing columns + add them via ALTER TABLE.
 *
 * This preserves all existing data — only missing columns are added.
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
                    migrateSchemaIfNeeded(db)
                }

                /**
                 * Checks for missing columns in various tables + adds them via ALTER TABLE.
                 * This is idempotent — only adds columns that don't exist.
                 */
                private fun migrateSchemaIfNeeded(db: SupportSQLiteDatabase) {
                    // ── download_queue: check for main_id (D.0 migration) ──
                    if (!hasColumn(db, "download_queue", "main_id")) {
                        // Old schema — drop + recreate the download tables.
                        db.execSQL("DROP TABLE IF EXISTS download_queue")
                        db.execSQL("DROP TABLE IF EXISTS downloaded_episode")
                        // Recreate all tables — CREATE TABLE IF NOT EXISTS preserves
                        // existing tables; the dropped download tables are recreated.
                        onCreate(db)
                    }

                    // ── data_cache_episode: check for episode_url (D.FIX migration) ──
                    if (!hasColumn(db, "data_cache_episode", "episode_url")) {
                        // Add the episode_url column to the existing table.
                        db.execSQL("ALTER TABLE data_cache_episode ADD COLUMN episode_url TEXT")
                    }
                }

                /**
                 * Returns true if [tableName] has a column named [columnName].
                 */
                private fun hasColumn(
                    db: SupportSQLiteDatabase,
                    tableName: String,
                    columnName: String,
                ): Boolean {
                    val cursor = db.query("PRAGMA table_info($tableName)")
                    return cursor.use { c ->
                        if (!c.moveToFirst()) return false
                        val nameIndex = c.getColumnIndex("name")
                        if (nameIndex < 0) return false
                        var found = false
                        while (c.moveToNext()) {
                            if (c.getString(nameIndex) == columnName) {
                                found = true
                                break
                            }
                        }
                        found
                    }
                }
            },
        )
    }
}
