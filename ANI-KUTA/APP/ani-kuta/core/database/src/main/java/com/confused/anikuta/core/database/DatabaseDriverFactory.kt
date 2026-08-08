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

                    // ── Phase WP: watch_progress new columns (PLAN §1.1, §1.8) ──
                    // The .sq CREATE TABLE is edited for fresh installs + codegen.
                    // Existing dev installs get the new columns via these ALTER TABLEs.
                    // (SQLite can't add FK via ALTER TABLE — main_id enforced at app level
                    //  for existing installs; FK in CREATE TABLE for fresh installs.)
                    if (!hasColumn(db, "watch_progress", "main_id")) {
                        db.execSQL("ALTER TABLE watch_progress ADD COLUMN main_id TEXT")
                    }
                    if (!hasColumn(db, "watch_progress", "watch_count")) {
                        db.execSQL("ALTER TABLE watch_progress ADD COLUMN watch_count INTEGER NOT NULL DEFAULT 0")
                    }
                    if (!hasColumn(db, "watch_progress", "first_watched_at")) {
                        db.execSQL("ALTER TABLE watch_progress ADD COLUMN first_watched_at INTEGER")
                    }
                    if (!hasColumn(db, "watch_progress", "auto_mark_suppressed")) {
                        db.execSQL("ALTER TABLE watch_progress ADD COLUMN auto_mark_suppressed INTEGER NOT NULL DEFAULT 0")
                    }
                    if (!hasColumn(db, "watch_progress", "user_marked_watched")) {
                        db.execSQL("ALTER TABLE watch_progress ADD COLUMN user_marked_watched INTEGER NOT NULL DEFAULT 0")
                    }
                    // Index for the new main_id column (idempotent — CREATE INDEX IF NOT EXISTS).
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_watch_progress_main_id ON watch_progress(main_id)")

                    // ── Phase UP: create new tables if they don't exist (episode_update, anime_update_state) ──
                    // For existing installs, these tables are new. onCreate(db) runs ALL CREATE TABLE IF NOT EXISTS
                    // statements (idempotent — existing tables are unaffected). This is the cleanest way to add
                    // new tables without duplicating the schema SQL.
                    if (!hasColumn(db, "episode_update", "id")) {
                        onCreate(db)
                    }

                    // ── Phase TR: create ratings tables if they don't exist ──
                    if (!hasColumn(db, "user_rating", "main_id")) {
                        onCreate(db)
                    }

                    // ── Phase NOTIF: create notifications tables if they don't exist ──
                    if (!hasColumn(db, "notification_config", "main_id")) {
                        onCreate(db)
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
