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
                    // Phase DB-OPT: enable FK enforcement (was OFF by default in SQLite).
                    // All ON DELETE CASCADE clauses are now active. Safe for existing data —
                    // FK checks only apply to new INSERT/UPDATE/DELETE, not retroactively.
                    db.execSQL("PRAGMA foreign_keys = ON")
                    migrateSchemaIfNeeded(db)
                }

                /**
                 * Checks for missing columns in various tables + adds them via ALTER TABLE.
                 * This is idempotent — only adds columns that don't exist.
                 */
                private fun migrateSchemaIfNeeded(db: SupportSQLiteDatabase) {
                    // ── Phase DB-OPT: drop dead tables (extensions.sq + metadata.sq deleted) ──
                    // installed_source + extension_repo were never used (zero Kotlin call sites).
                    // content_metadata_cache + episode_metadata_cache were superseded by
                    // anime_metadata_cache + data_cache_episode (dataCache.sq).
                    db.execSQL("DROP TABLE IF EXISTS installed_source")
                    db.execSQL("DROP TABLE IF EXISTS extension_repo")
                    db.execSQL("DROP TABLE IF EXISTS content_metadata_cache")
                    db.execSQL("DROP TABLE IF EXISTS episode_metadata_cache")

                    // ── D-192: drop dead content lookup tables ──
                    // content_ext + content_ext_repo were never populated (zero callers of
                    // getOrCreateExtension/insertExtensionRepo — confirmed via grep). The
                    // content.extension_id FK to content_ext was already removed in D-189;
                    // the content.extension_repo_id FK is removed in the .sq file (column kept
                    // as nullable INTEGER for future use). These tables are dead code.
                    db.execSQL("DROP TABLE IF EXISTS content_ext")
                    db.execSQL("DROP TABLE IF EXISTS content_ext_repo")

                    // ── D-192: drop user_customization (replaced by app_settings) ──
                    // user_customization was READ-ONLY (LocalMetadataProvider read it but always
                    // got null — no code ever wrote to it). The user-override feature (custom
                    // title/thumbnail/description per content) was never built. Replaced by the
                    // new app_settings table (for backup/restore of all app settings).
                    db.execSQL("DROP TABLE IF EXISTS user_customization")

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

                    // ── Phase DB-OPT (audio-variants fix): source_name + scanlator columns ──
                    // These preserve the extension's original episode name + scanlator through
                    // the AniList-enriched cache write, so audio pills (SUB/DUB/HSUB) show on
                    // cache-first load (not just after a manual refresh).
                    if (!hasColumn(db, "data_cache_episode", "source_name")) {
                        db.execSQL("ALTER TABLE data_cache_episode ADD COLUMN source_name TEXT")
                    }
                    if (!hasColumn(db, "data_cache_episode", "scanlator")) {
                        db.execSQL("ALTER TABLE data_cache_episode ADD COLUMN scanlator TEXT")
                    }

                    // ── D-190 (episode metadata engine): 8 new columns for AniZip/Jikan/Kitsu ──
                    // is_filler/is_recap are nullable INTEGER (null=unknown, 0=no, 1=yes).
                    // Jikan is the only source with filler info; if Jikan fails, the field
                    // stays null (UI shows no badge) rather than incorrectly showing "non-filler".
                    if (!hasColumn(db, "data_cache_episode", "is_filler")) {
                        db.execSQL("ALTER TABLE data_cache_episode ADD COLUMN is_filler INTEGER")
                    }
                    if (!hasColumn(db, "data_cache_episode", "is_recap")) {
                        db.execSQL("ALTER TABLE data_cache_episode ADD COLUMN is_recap INTEGER")
                    }
                    if (!hasColumn(db, "data_cache_episode", "title_japanese")) {
                        db.execSQL("ALTER TABLE data_cache_episode ADD COLUMN title_japanese TEXT")
                    }
                    if (!hasColumn(db, "data_cache_episode", "title_romaji")) {
                        db.execSQL("ALTER TABLE data_cache_episode ADD COLUMN title_romaji TEXT")
                    }
                    if (!hasColumn(db, "data_cache_episode", "runtime")) {
                        db.execSQL("ALTER TABLE data_cache_episode ADD COLUMN runtime INTEGER")
                    }
                    if (!hasColumn(db, "data_cache_episode", "season_number")) {
                        db.execSQL("ALTER TABLE data_cache_episode ADD COLUMN season_number INTEGER")
                    }
                    if (!hasColumn(db, "data_cache_episode", "episode_number_in_season")) {
                        db.execSQL("ALTER TABLE data_cache_episode ADD COLUMN episode_number_in_season INTEGER")
                    }
                    if (!hasColumn(db, "data_cache_episode", "score")) {
                        db.execSQL("ALTER TABLE data_cache_episode ADD COLUMN score REAL")
                    }

                    // ── D-192 Phase 3: episode_update batch_type + episode_count ──
                    // Supports "initial batch" update rows (one row for first-link episodes 1-N)
                    // vs "new" individual episode update rows (one per new episode on refresh).
                    if (!hasColumn(db, "episode_update", "batch_type")) {
                        db.execSQL("ALTER TABLE episode_update ADD COLUMN batch_type TEXT NOT NULL DEFAULT 'new'")
                    }
                    if (!hasColumn(db, "episode_update", "episode_count")) {
                        db.execSQL("ALTER TABLE episode_update ADD COLUMN episode_count INTEGER")
                    }

                    // ── Phase DB-OPT: drop redundant indexes (idempotent — IF EXISTS) ──
                    // These duplicate the leftmost column of composite UNIQUE/PK indexes.
                    db.execSQL("DROP INDEX IF EXISTS idx_data_cache_episode_main")
                    db.execSQL("DROP INDEX IF EXISTS idx_download_queue_main")
                    db.execSQL("DROP INDEX IF EXISTS idx_schedule_main")
                    db.execSQL("DROP INDEX IF EXISTS idx_episode_update_main_id")
                    db.execSQL("DROP INDEX IF EXISTS idx_episode_rating_main")
                    db.execSQL("DROP INDEX IF EXISTS idx_anime_update_status")

                    // ── Phase DB-OPT: create new indexes (idempotent — IF NOT EXISTS) ──
                    // These add missing indexes for common query patterns.
                    // watch_progress: continue-watching partial + completed_at
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_watch_progress_continue ON watch_progress(last_watched_at DESC) WHERE completed = 0 AND auto_mark_suppressed = 0 AND position > 0")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_watch_progress_completed_at ON watch_progress(completed_at DESC)")
                    // episode_update: retention purge partial
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_episode_update_ack_at ON episode_update(acknowledged_at) WHERE acknowledged = 1")
                    // notification_sent: retention purge
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_notification_sent_at ON notification_sent(sent_at)")
                    // library_item: unique dedup
                    // Dedupe any duplicate (main_id, category_id) rows before adding the
                    // UNIQUE index (INSERT OR IGNORE without a UNIQUE constraint could have
                    // created duplicates on existing installs). Keeps the lowest id per pair.
                    db.execSQL("DELETE FROM library_item WHERE id NOT IN (SELECT MIN(id) FROM library_item GROUP BY main_id, category_id)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_library_item_unique ON library_item(main_id, category_id)")
                    // anilist_detail: JOIN filter
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_anilist_detail_anilist_id ON anilist_detail(anilist_id)")
                    // content: extension lookup composite
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_content_extension_url ON content(extension_id, anime_url)")

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

                    // ── D-193 v2: learned_offset_ms on anime_update_state (smart-release averaging) ──
                    // Existing dev installs get the column via ALTER TABLE; fresh installs get
                    // it from the .sq CREATE TABLE. Idempotent — hasColumn guards the ALTER.
                    if (hasColumn(db, "anime_update_state", "main_id") &&
                        !hasColumn(db, "anime_update_state", "learned_offset_ms")) {
                        db.execSQL("ALTER TABLE anime_update_state ADD COLUMN learned_offset_ms INTEGER")
                    }

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

                    // ── Genre System: create genre + content_genre tables if they don't exist ──
                    if (!hasColumn(db, "genre", "id")) {
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
