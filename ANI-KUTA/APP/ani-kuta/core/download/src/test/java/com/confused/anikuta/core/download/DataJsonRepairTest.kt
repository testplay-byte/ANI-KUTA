package com.confused.anikuta.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-404 (round 29): unit tests for the pure `.data.json` integrity toolkit —
 * [DataJsonRepair.salvageCompleteJsonHead], [DataJsonRepair
 * .rebuildEpisodesAfterDelete], [DataJsonRepair.episodesEqual].
 *
 * The test tables encode the EXACT device scenarios from the round-29
 * postmortem (v0.4.16, OnePlus KB2001 / Android 14):
 *
 *  - the non-truncating `"w"` write that left `new-json-head + old-json-tail`
 *    behind after a shrinking delete (the "data.json was corrupted" report);
 *  - the DB-truth rebuild that replaces match-and-pray removal (key drift,
 *    ghost entries, corrupted/absent existing file);
 *  - the strict set-equality verification.
 */
class DataJsonRepairTest {

    // ── salvageCompleteJsonHead ───────────────────────────────────────────────

    @Test
    fun `clean json returns whole text`() {
        val clean = """{"schemaVersion":1,"episodes":[]}"""
        assertEquals(clean, DataJsonRepair.salvageCompleteJsonHead(clean))
    }

    @Test
    fun `clean pretty json with whitespace returns the complete object`() {
        val clean = "\n  {\n    \"a\": 1\n  }\n"
        assertEquals("{\n    \"a\": 1\n  }", DataJsonRepair.salvageCompleteJsonHead(clean))
    }

    @Test
    fun `json head plus garbage tail returns the head`() {
        // The EXACT shape the non-truncating write leaves: a complete 1-entry
        // document followed by the leftover tail of the old 2-entry document.
        val head = """{"schemaVersion":1,"episodes":[{"episodeKey":"k2"}]}"""
        val corrupted = head + """ty":480,"fileSize":123}],"createdAt":1}"""
        assertEquals(head, DataJsonRepair.salvageCompleteJsonHead(corrupted))
    }

    @Test
    fun `braces inside strings do not count`() {
        val json = """{"note":"a } b { c","n":2}""" + "GARBAGE{"
        assertEquals("""{"note":"a } b { c","n":2}""",
            DataJsonRepair.salvageCompleteJsonHead(json))
    }

    @Test
    fun `escaped quotes inside strings do not terminate them`() {
        val json = """{"note":"she said \"hi\" }","n":2}""" + "tail"
        assertEquals("""{"note":"she said \"hi\" }","n":2}""",
            DataJsonRepair.salvageCompleteJsonHead(json))
    }

    @Test
    fun `nested objects and arrays are walked to the true end`() {
        val json = """{"a":{"b":[1,2,{"c":3}]},"d":true}""" + "}{]["
        assertEquals("""{"a":{"b":[1,2,{"c":3}]},"d":true}""",
            DataJsonRepair.salvageCompleteJsonHead(json))
    }

    @Test
    fun `truncated head returns null`() {
        assertNull(DataJsonRepair.salvageCompleteJsonHead("""{"a":1,"b":[2,3"""))
    }

    @Test
    fun `empty and pure garbage return null`() {
        assertNull(DataJsonRepair.salvageCompleteJsonHead(""))
        assertNull(DataJsonRepair.salvageCompleteJsonHead("   "))
        assertNull(DataJsonRepair.salvageCompleteJsonHead("}{]["))
        assertNull(DataJsonRepair.salvageCompleteJsonHead("not json at all"))
    }

    @Test
    fun `round trip through the real serializer survives salvage`() {
        // End-to-end sanity with the REAL ContentDataJson codec: shrink-write
        // corruption (head = the new doc, tail = part of the old doc) must
        // salvage to a document that parses to the NEW content.
        val doc2 = ContentDataJson(
            mainId = "m1", contentId = "c1", title = "Series",
            episodes = listOf(
                info("k1", 1.0),
                info("k2", 2.0),
            ),
            createdAt = 1L, updatedAt = 1L,
        )
        val doc1 = doc2.copy(episodes = listOf(info("k2", 2.0)))
        val newJson = ContentDataJson.stringify(doc1)
        val oldJson = ContentDataJson.stringify(doc2)
        val corrupted = newJson + oldJson.substring(newJson.length)
        val salvaged = DataJsonRepair.salvageCompleteJsonHead(corrupted)
        assertNotNull(salvaged)
        val parsed = ContentDataJson.parse(salvaged!!)
        assertNotNull(parsed)
        assertEquals(listOf("k2"), parsed!!.episodes.map { it.episodeKey })
    }

    // ── rebuildEpisodesAfterDelete ────────────────────────────────────────────

    private fun info(key: String, number: Double): DownloadedEpisodeInfo =
        DownloadedEpisodeInfo(
            episodeKey = key,
            episodeNumber = number,
            episodeUrl = "https://example.com/$key",
            videoUri = "content://old/$key",
            quality = "1080p",
            videoServer = "ServerX",
            downloadedAt = 1_700_000_000_000L,
            fileSize = 1_000L,
        )

    private fun row(key: String, number: Float): DownloadedEpisode = DownloadedEpisode(
        content = DownloadContentInfo(mainId = "m1", contentId = "c1", title = "Series"),
        episode = DownloadEpisodeInfo(episodeKey = key, episodeNumber = number, name = "Ep $number"),
        videoUri = "content://new/$key",
        subtitleUris = listOf("content://new/${key}_en"),
        sizeBytes = 2_000L,
        quality = "720p",
        completedAt = 1_800_000_000_000L,
    )

    @Test
    fun `normal delete - surviving row keeps DB identity and enriched metadata`() {
        // Delete k1 of [k1, k2]: the DB (minus k1) says k2 survives → the
        // rebuilt list is exactly [k2] with the DB row's URIs + the json
        // entry's DB-missing metadata (videoServer from the json survives).
        val existing = listOf(info("k1", 1.0), info("k2", 2.0))
        val rebuilt = DataJsonRepair.rebuildEpisodesAfterDelete(
            existingEpisodes = existing,
            survivingDbRows = listOf(row("k2", 2f)),
            deletedEpisodeKey = "k1",
            deletedEpisodeNumber = 1.0,
        )
        assertEquals(listOf("k2"), rebuilt.map { it.episodeKey })
        val e = rebuilt.single()
        assertEquals(2.0, e.episodeNumber, 0.0)
        assertEquals("content://new/k2", e.videoUri) // DB wins
        assertEquals(listOf("content://new/k2_en"), e.subtitleUris) // DB wins
        assertEquals(2_000L, e.fileSize) // DB wins
        assertEquals(1_800_000_000_000L, e.downloadedAt) // DB wins
        assertEquals("ServerX", e.videoServer) // enrichment survives (DB lacks it)
        assertEquals("Ep 2.0", e.episodeName) // DB name
    }

    @Test
    fun `last episode delete yields empty list`() {
        val rebuilt = DataJsonRepair.rebuildEpisodesAfterDelete(
            existingEpisodes = listOf(info("k1", 1.0)),
            survivingDbRows = emptyList(),
            deletedEpisodeKey = "k1",
            deletedEpisodeNumber = 1.0,
        )
        assertTrue(rebuilt.isEmpty())
    }

    @Test
    fun `corrupted or missing existing file heals from DB rows alone`() {
        // The v0.4.16 device state: data.json unparseable → existing == null.
        // The rebuild still produces a valid fresh entry for every DB row.
        val rebuilt = DataJsonRepair.rebuildEpisodesAfterDelete(
            existingEpisodes = null,
            survivingDbRows = listOf(row("k2", 2f)),
            deletedEpisodeKey = "k1",
            deletedEpisodeNumber = 1.0,
        )
        assertEquals(1, rebuilt.size)
        val e = rebuilt.single()
        assertEquals("k2", e.episodeKey)
        assertEquals("content://new/k2", e.videoUri)
        assertEquals("https://example.com/k2", e.episodeUrl) // fresh fallback = the key
        assertEquals("720p", e.quality)
    }

    @Test
    fun `key drift - the DB row's key wins over the stored json key`() {
        // A scanner rebuild once stored the ep-2 entry under a different key.
        // The runtime lookup uses the DB key — the json must follow the DB.
        val existing = listOf(info("k1", 1.0), info("rebuilt-key-2", 2.0))
        val rebuilt = DataJsonRepair.rebuildEpisodesAfterDelete(
            existingEpisodes = existing,
            survivingDbRows = listOf(row("k2", 2f)),
            deletedEpisodeKey = "k1",
            deletedEpisodeNumber = 1.0,
        )
        val e = rebuilt.single()
        assertEquals("k2", e.episodeKey) // DB key, not "rebuilt-key-2"
        assertEquals(2.0, e.episodeNumber, 0.0)
        assertEquals("ServerX", e.videoServer) // matched by NUMBER → enriched
    }

    @Test
    fun `ghost entries with no surviving DB row are dropped`() {
        // data.json lists three episodes, the DB only has one survivor — the
        // file must shrink to DB truth (ghosts from an older desync die).
        val existing = listOf(info("k1", 1.0), info("k2", 2.0), info("k3", 3.0))
        val rebuilt = DataJsonRepair.rebuildEpisodesAfterDelete(
            existingEpisodes = existing,
            survivingDbRows = listOf(row("k2", 2f)),
            deletedEpisodeKey = "k1",
            deletedEpisodeNumber = 1.0,
        )
        assertEquals(listOf("k2"), rebuilt.map { it.episodeKey })
    }

    @Test
    fun `the in-flight deleted row is defensively filtered even if the caller passes it`() {
        val rebuilt = DataJsonRepair.rebuildEpisodesAfterDelete(
            existingEpisodes = listOf(info("k1", 1.0), info("k2", 2.0)),
            survivingDbRows = listOf(row("k1", 1f), row("k2", 2f)), // k1 NOT yet excluded
            deletedEpisodeKey = "k1",
            deletedEpisodeNumber = 1.0,
        )
        assertEquals(listOf("k2"), rebuilt.map { it.episodeKey })
    }

    @Test
    fun `a number twin of the deleted episode is also defensively filtered`() {
        // Key drifted between the DB row and the delete request: the row the
        // user deleted is present under a different key but the SAME number.
        val rebuilt = DataJsonRepair.rebuildEpisodesAfterDelete(
            existingEpisodes = listOf(info("old-key", 1.0), info("k2", 2.0)),
            survivingDbRows = listOf(row("old-key", 1f), row("k2", 2f)),
            deletedEpisodeKey = "old-key",
            deletedEpisodeNumber = 1.0,
        )
        assertEquals(listOf("k2"), rebuilt.map { it.episodeKey })
    }

    @Test
    fun `result is sorted by episodeNumber then key`() {
        val rebuilt = DataJsonRepair.rebuildEpisodesAfterDelete(
            existingEpisodes = null,
            survivingDbRows = listOf(row("k5", 5f), row("k1", 1f), row("k1b", 1.5f)),
            deletedEpisodeKey = "kX",
            deletedEpisodeNumber = null,
        )
        assertEquals(listOf("k1", "k1b", "k5"), rebuilt.map { it.episodeKey })
    }

    // ── episodesEqual ─────────────────────────────────────────────────────────

    @Test
    fun `identical lists compare equal`() {
        val a = listOf(info("k1", 1.0), info("k2", 2.0))
        assertTrue(DataJsonRepair.episodesEqual(a, a.reversed()))
    }

    @Test
    fun `different sizes compare unequal`() {
        assertFalse(DataJsonRepair.episodesEqual(
            listOf(info("k1", 1.0), info("k2", 2.0)),
            listOf(info("k1", 1.0)),
        ))
    }

    @Test
    fun `same keys different numbers compare unequal`() {
        // A corrupted re-read (key survived, number mangled) must FAIL the
        // strict verification.
        assertFalse(DataJsonRepair.episodesEqual(
            listOf(info("k1", 1.0)),
            listOf(info("k1", 2.0)),
        ))
    }

    @Test
    fun `duplicate keys fail even when the sizes match`() {
        assertFalse(DataJsonRepair.episodesEqual(
            listOf(info("k1", 1.0), info("k2", 2.0)),
            listOf(info("k1", 1.0), info("k1", 1.0)),
        ))
    }
}
