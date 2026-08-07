package com.confused.anikuta.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Creates the SQLDelight driver for ANI-KUTA's database.
 * The database file is "anikuta.db".
 *
 * D.0 schema migration: the `download_queue` + `downloaded_episode` tables were
 * re-keyed by `mainId` + `episodeKey` (was `episode_key` only). The old tables
 * on existing dev installs don't have the new columns. This factory overrides
 * `onUpgrade` to drop + recreate those tables when the schema version changes.
 *
 * This preserves all other data (content, library, anilist details, etc.) —
 * only the download tables are wiped (they had no production data; the old
 * stub DownloadManager was never used in production).
 */
class DatabaseDriverFactory(private val context: Context) {
    fun create(): SqlDriver {
        return AndroidSqliteDriver(
            schema = AnikutaDatabase.Schema,
            context = context,
            name = "anikuta.db",
            callback = object : AndroidSqliteDriver.Callback(AnikutaDatabase.Schema) {
                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) {
                    // Drop the old download tables — they were re-keyed in D.0
                    // and the old schema is incompatible (no main_id column, etc.).
                    // SQLDelight's onCreate will recreate them with the new schema.
                    db.execSQL("DROP TABLE IF EXISTS download_queue")
                    db.execSQL("DROP TABLE IF EXISTS downloaded_episode")
                    // Recreate all tables — CREATE TABLE IF NOT EXISTS means
                    // existing tables (content, library, etc.) are untouched,
                    // and the dropped download tables are recreated.
                    onCreate(db)
                }
            },
        )
    }
}
