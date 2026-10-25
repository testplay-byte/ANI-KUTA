package com.confused.anikuta.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ROUND 127 (D-798): the schema-MIGRATION lock tests. The user's requirement:
 * "if we update the database, then it should properly be able to convert the
 * old database to the new with the update." These tests run the REAL versioned
 * migration (1.sqm, v1 → v2) against a faithful v1-shaped database and assert
 * the two things that matter: the new column exists, and the old DATA SURVIVES.
 *
 * The v1 shape here is the exact pre-migration watch_progress: the 6 original
 * columns + the 5 Phase-WP columns the on-open probes had already added (every
 * install that ran the current app has them). 1.sqm's ALTER must apply cleanly
 * on top of exactly this.
 *
 * Mechanics: JdbcSqliteDriver("jdbc:sqlite::memory:") (the bare ":memory:" is
 * not a JDBC URL), raw v1 DDL + a data row, then
 * `AnikutaDatabase.Schema.migrate(driver, 1, 2)` — the same call the
 * AndroidSqliteDriver.Callback makes on an upgrading install.
 */
class SchemaMigrationTest {

    private lateinit var driver: SqlDriver

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver("jdbc:sqlite::memory:")
        // The v1 watch_progress — byte-for-byte the shape every existing
        // install has (created by the old .sq + the on-open guarded ALTERs).
        driver.execute(
            null,
            """
            CREATE TABLE watch_progress (
                episode_key TEXT NOT NULL PRIMARY KEY,
                position INTEGER NOT NULL,
                duration INTEGER NOT NULL,
                completed INTEGER NOT NULL DEFAULT 0,
                completed_at INTEGER,
                last_watched_at INTEGER NOT NULL,
                main_id TEXT,
                watch_count INTEGER NOT NULL DEFAULT 0,
                first_watched_at INTEGER,
                auto_mark_suppressed INTEGER NOT NULL DEFAULT 0,
                user_marked_watched INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (main_id) REFERENCES main_entry(main_id) ON DELETE CASCADE
            )
            """.trimMargin(),
            0,
        )
        // Real data a user would have on their device.
        driver.execute(
            null,
            """
            INSERT INTO watch_progress(
                episode_key, position, duration, completed, completed_at, last_watched_at,
                main_id, watch_count, first_watched_at, auto_mark_suppressed, user_marked_watched
            ) VALUES ('main-1|00001', 600, 1440, 0, NULL, 1729800000000, 'main-1', 0, NULL, 0, 0)
            """.trimMargin(),
            0,
        )
        // main_entry too — the FK target (foreign_keys is off by default in
        // SQLite, but a faithful v1 also had the table).
        driver.execute(
            null,
            """
            CREATE TABLE main_entry (
                main_id TEXT NOT NULL PRIMARY KEY,
                content_id TEXT NOT NULL,
                title TEXT NOT NULL,
                content_type TEXT NOT NULL DEFAULT 'anime',
                content_format TEXT NOT NULL DEFAULT 'video',
                data_source_id INTEGER,
                system_id INTEGER,
                extension_repo_id INTEGER,
                extension_id INTEGER,
                source_id INTEGER,
                anime_url TEXT,
                display_source TEXT NOT NULL DEFAULT 'extension',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            "INSERT INTO main_entry(main_id, content_id, title, created_at, updated_at) " +
                "VALUES ('main-1', 'cid-1', 'Test Anime', 1, 1)",
            0,
        )
    }

    @After
    fun tearDown() {
        driver.close()
    }

    private fun columnNames(table: String): List<String> {
        val names = mutableListOf<String>()
        driver.executeQuery(null, "PRAGMA table_info($table)", { cursor ->
            while (cursor.next().value) {
                names.add(cursor.getString(1)!!)
            }
            app.cash.sqldelight.db.QueryResult.Value(Unit)
        }, 0)
        return names
    }

    @Test
    fun `migration 1 to 2 adds played_time_ms to watch_progress`() {
        assertTrue("precondition: v1 has no played_time_ms", "played_time_ms" !in columnNames("watch_progress"))

        AnikutaDatabase.Schema.migrate(driver, 1, 2)

        assertTrue(
            "the migration must add played_time_ms",
            "played_time_ms" in columnNames("watch_progress"),
        )
    }

    @Test
    fun `migration 1 to 2 preserves existing watch progress data`() {
        AnikutaDatabase.Schema.migrate(driver, 1, 2)

        val database = AnikutaDatabase(driver)
        val row = database.watchQueries.getWatchProgress("main-1|00001").executeAsOneOrNull()
        assertNotNull("the pre-migration row must survive the migration", row)
        assertEquals(600L, row!!.position)
        assertEquals(1440L, row.duration)
        assertEquals(0L, row.completed)
        assertEquals(1729800000000L, row.last_watched_at)
        assertEquals("main-1", row.main_id)
        assertEquals("the new column starts at 0 for pre-existing rows", 0L, row.played_time_ms)
    }

    @Test
    fun `migrated database is fully usable by the generated code`() {
        AnikutaDatabase.Schema.migrate(driver, 1, 2)

        val database = AnikutaDatabase(driver)
        // A save through the typed API on the MIGRATED database must work —
        // the generated INSERT lists every v2 column.
        database.watchQueries.upsertWatchProgress(
            episode_key = "main-1|00002",
            position = 120L,
            duration = 1440L,
            completed = 0L,
            completed_at = null,
            last_watched_at = 1729800001000L,
            main_id = "main-1",
            watch_count = 0L,
            first_watched_at = null,
            auto_mark_suppressed = 0L,
            user_marked_watched = 0L,
            played_time_ms = 61_000L,
        )
        val row = database.watchQueries.getWatchProgress("main-1|00002").executeAsOneOrNull()
        assertNotNull(row)
        assertEquals(61_000L, row!!.played_time_ms)
    }

    @Test
    fun `fresh install creates the v2 schema directly`() {
        // A fresh JdbcSqliteDriver + Schema.create = a brand-new install: the
        // column is there without any migration.
        val fresh = JdbcSqliteDriver("jdbc:sqlite::memory:")
        AnikutaDatabase.Schema.create(fresh)
        val database = AnikutaDatabase(fresh)
        assertEquals(null, database.watchQueries.getWatchProgress("nope").executeAsOneOrNull())
        val names = mutableListOf<String>()
        fresh.executeQuery(null, "PRAGMA table_info(watch_progress)", { cursor ->
            while (cursor.next().value) names.add(cursor.getString(1)!!)
            app.cash.sqldelight.db.QueryResult.Value(Unit)
        }, 0)
        assertTrue("a fresh install has played_time_ms from the .sq", "played_time_ms" in names)
        fresh.close()
    }
}
