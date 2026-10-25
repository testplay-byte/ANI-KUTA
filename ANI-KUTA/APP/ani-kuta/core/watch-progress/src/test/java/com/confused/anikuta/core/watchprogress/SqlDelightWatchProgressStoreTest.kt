package com.confused.anikuta.core.watchprogress

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.confused.anikuta.core.database.AnikutaDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ROUND 127 (D-789): the CUMULATIVE played-time lock tests — the device
 * round's spec, executed against the REAL store on a REAL in-memory SQLite:
 *
 *   watched = fraction > threshold  AND  >= 1 minute ACTUALLY played,
 *   TOTALLED over all sessions (not per-session).
 *
 * The user is not device-testing this round, so these locks ARE the device
 * verification: every scenario from the round-126 report is replayed here.
 */
class SqlDelightWatchProgressStoreTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: AnikutaDatabase
    private lateinit var store: SqlDelightWatchProgressStore

    /** The threshold seam — the tests pin it at the default 0.85. */
    private val prefs = object : WatchPreferences {
        override var autoMarkThreshold: Float = 0.85f
    }

    private val key = "main-1|00001"

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver("jdbc:sqlite::memory:")
        AnikutaDatabase.Schema.create(driver)
        database = AnikutaDatabase(driver)
        store = SqlDelightWatchProgressStore(database, prefs)
    }

    @After
    fun tearDown() {
        driver.close()
    }

    private fun progress(position: Long, duration: Long, playedMs: Long) = WatchProgress(
        episodeKey = key,
        mainId = "main-1",
        position = position,
        duration = duration,
        completed = false,
        completedAt = null,
        lastWatchedAt = System.currentTimeMillis(),
        playedTimeMs = playedMs,
    )

    // ── the round-126 device scenarios, replayed against the cumulative gate ──

    @Test
    fun `a fresh failed stream never marks watched - 2s played, pinned at end`() = runTest {
        // The failure pins position at the end → fraction 1.0, but only ~2s of
        // real playback. Round-126 device scenario #1.
        store.save(key, progress(position = 1440, duration = 1440, playedMs = 2_000))
        assertFalse(store.get(key)!!.isWatched)
        assertFalse(store.get(key)!!.completed)
    }

    @Test
    fun `under one minute of playback never marks watched even at 100 percent`() = runTest {
        // 45 real seconds, then a seek to the very end: fraction 1.0 but the
        // cumulative total is under the floor.
        store.save(key, progress(position = 1440, duration = 1440, playedMs = 45_000))
        assertFalse(store.get(key)!!.isWatched)
    }

    @Test
    fun `one minute plus of real playback at over threshold marks watched`() = runTest {
        // 80s played in one session, position past the threshold. Round-126
        // device scenario #2 (the "about one minute twenty" case).
        store.save(key, progress(position = 1250, duration = 1440, playedMs = 80_000))
        assertTrue(store.get(key)!!.isWatched)
    }

    // ── THE ROUND-127 FIX: the minute is CUMULATIVE across sessions ──

    @Test
    fun `a previously watched episode completing by seek later IS marked watched`() = runTest {
        // Session 1: the user genuinely watches 70s, exits mid-episode.
        store.save(key, progress(position = 700, duration = 1440, playedMs = 70_000))
        assertFalse("mid-episode exit is not a completion", store.get(key)!!.isWatched)
        assertEquals(70_000L, store.get(key)!!.playedTimeMs)

        // Session 2 (a later open): the user seeks straight to the end and lets
        // it EOF. The store's auto-mark reads the PERSISTED total.
        store.save(key, progress(position = 1440, duration = 1440, playedMs = 70_000))
        assertTrue(
            "the device round's exact report: previously watched >1 min → later seek-to-end marks it",
            store.get(key)!!.isWatched,
        )
    }

    @Test
    fun `session accumulation across saves does not double count`() = runTest {
        // A periodic-save pattern: the same session total re-passed every 10s
        // must be idempotent (max of stored vs incoming), never additive.
        store.save(key, progress(position = 100, duration = 1440, playedMs = 10_000))
        store.save(key, progress(position = 200, duration = 1440, playedMs = 20_000))
        store.save(key, progress(position = 300, duration = 1440, playedMs = 20_000))
        store.save(key, progress(position = 400, duration = 1440, playedMs = 30_000))
        assertEquals(30_000L, store.get(key)!!.playedTimeMs)
    }

    @Test
    fun `a caller that does not track played time cannot lower the stored total`() = runTest {
        store.save(key, progress(position = 500, duration = 1440, playedMs = 90_000))
        // The CS-style caller (playedTimeMs defaults to 0) must not reset it.
        store.save(
            key,
            WatchProgress(
                episodeKey = key,
                mainId = "main-1",
                position = 600,
                duration = 1440,
                completed = false,
                completedAt = null,
                lastWatchedAt = System.currentTimeMillis(),
            ),
        )
        assertEquals(90_000L, store.get(key)!!.playedTimeMs)
    }

    @Test
    fun `auto mark still respects user un-mark suppression`() = runTest {
        // Watch past both conditions → completed; un-mark → suppressed stays false-completed.
        store.save(key, progress(position = 1300, duration = 1440, playedMs = 120_000))
        assertTrue(store.get(key)!!.isWatched)
        store.setAutoMarkSuppressed(key)
        val suppressed = store.get(key)!!
        assertTrue(suppressed.autoMarkSuppressed)
        assertFalse("the user's un-mark wins over any fraction", suppressed.isWatched)
        // Even a fresh 100%-fraction save with plenty of played time stays suppressed.
        store.resetAutoMarkSuppressed(key) // FILE_LOADED re-arm
        store.save(key, progress(position = 1440, duration = 1440, playedMs = 120_000))
        assertTrue("after re-arm, the fraction+played auto-mark fires again", store.get(key)!!.isWatched)
    }

    @Test
    fun `manual mark watched works regardless of played time - sticky`() = runTest {
        store.setUserMarkedWatched(key)
        val row = store.get(key)!!
        assertTrue(row.isWatched)
        assertTrue(row.userMarkedWatched)
        // A later low-fraction save keeps the sticky mark.
        store.save(key, progress(position = 10, duration = 1440, playedMs = 0))
        assertTrue(store.get(key)!!.isWatched)
    }

    @Test
    fun `un-marking deletes the whole row including the played total`() = runTest {
        store.save(key, progress(position = 1300, duration = 1440, playedMs = 120_000))
        assertFalse("un-marking a watched episode returns false", store.toggleWatched(key))
        assertEquals(null, store.get(key)) // reset to never-opened — the user's standing order
        // Re-marking after the wipe is the sticky path again.
        assertTrue(store.toggleWatched(key))
        assertTrue(store.get(key)!!.userMarkedWatched)
    }
}
