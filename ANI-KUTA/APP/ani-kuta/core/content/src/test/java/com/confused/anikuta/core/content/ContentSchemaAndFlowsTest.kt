package com.confused.anikuta.core.content

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.confused.anikuta.core.database.AnikutaDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * ROUND 126 (D-785): the schema-behavior LOCK tests — the REAL generated
 * SQLDelight queries run against an in-memory SQLite (JVM driver, no
 * Robolectric). These pin the CURRENT database behaviors, including the
 * fragile ones documented in DOCUMENTATION/database/FLAWS.md, so any future
 * change (the B2 COALESCE defense, the B1 scanner fix) lands as a visible,
 * test-updated decision rather than an accident.
 */
class ContentSchemaAndFlowsTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: AnikutaDatabase
    private lateinit var repo: ContentRepository
    private lateinit var resolver: ContentResolver

    @Before
    fun setUp() {
        // A FRESH in-memory database per test — the schema created the same
        // way DatabaseDriverFactory creates it on device.
        driver = JdbcSqliteDriver("jdbc:sqlite::memory:")
        AnikutaDatabase.Schema.create(driver)
        database = AnikutaDatabase(driver)
        repo = ContentRepository(database)
        resolver = ContentResolver(repo, database, identitySync = null)
        repo.seedDefaults()
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun seedAniListContent(
        mainId: String,
        anilistId: Int,
        title: String,
        displaySource: String = "data_source",
    ): ContentRecord {
        val record = ContentRecord(
            mainId = mainId,
            contentId = "test-$mainId",
            title = title,
            displaySource = displaySource,
            createdAt = 1_000L,
            updatedAt = 1_000L,
        )
        repo.insertMainEntry(record)
        return record
    }

    private fun seedBothAxes(mainId: String) {
        repo.upsertContentDetails(
            ContentDetails(
                mainId = mainId,
                dataSourceType = "anilist",
                dataSourceRefId = "171018",
                dataSynopsis = "AniList synopsis",
                dataGenres = "Action, Comedy",
                dataStatus = "RELEASING",
                dataCoverUrl = "anilist-cover",
                extensionType = "aniyomi",
                extensionId = "12345",
                extDescription = "Extension synopsis",
                extGenres = "Action",
                extStatus = "RELEASING",
                extThumbnailUrl = "ext-cover",
            ),
        )
    }

    // ── the two-axis discipline ────────────────────────────────────────────

    @Test
    fun `extension axis write leaves the data axis untouched`() {
        seedAniListContent("m1", 171018, "Dandadan")
        seedBothAxes("m1")

        repo.updateExtensionAxis(
            ContentDetails(
                mainId = "m1",
                extDescription = "New ext synopsis",
                extGenres = "New genres",
            ),
        )

        val after = repo.getContentDetails("m1")!!
        assertEquals("New ext synopsis", after.extDescription)
        // The data axis survives a pure ext write.
        assertEquals("AniList synopsis", after.dataSynopsis)
        assertEquals("Action, Comedy", after.dataGenres)
        assertEquals("anilist-cover", after.dataCoverUrl)
    }

    @Test
    fun `data axis write leaves the extension axis untouched`() {
        seedAniListContent("m1", 171018, "Dandadan")
        seedBothAxes("m1")

        repo.updateDataSourceAxis(
            ContentDetails(
                mainId = "m1",
                dataSynopsis = "Refreshed AniList synopsis",
                dataCoverUrl = "new-cover",
            ),
        )

        val after = repo.getContentDetails("m1")!!
        assertEquals("Refreshed AniList synopsis", after.dataSynopsis)
        // The ext axis survives a pure data write.
        assertEquals("Extension synopsis", after.extDescription)
        assertEquals("ext-cover", after.extThumbnailUrl)
    }

    @Test
    fun `a partial axis write with nulls PRESERVES the stored values (B2 COALESCE defense)`() {
        // THE B2 LOCK (FLIPPED in round 127, D-792): the axis updates are
        // COALESCE MERGES now — a caller that passes null for fields it didn't
        // fetch PRESERVES the stored values. The "refresh replaces good data
        // with a parsed stub" wipe class is impossible at the storage layer.
        seedAniListContent("m1", 171018, "Dandadan")
        seedBothAxes("m1")

        repo.updateExtensionAxis(
            ContentDetails(mainId = "m1", extDescription = null, extGenres = null),
        )

        val after = repo.getContentDetails("m1")!!
        assertEquals("Extension synopsis", after.extDescription)
        assertEquals("Action", after.extGenres)
        assertEquals("ext-cover", after.extThumbnailUrl)
        // The data axis STILL survives (axis isolation is structural).
        assertEquals("AniList synopsis", after.dataSynopsis)
    }

    @Test
    fun `the data-axis merge preserves stored fields a partial fetch does not carry`() {
        // The user's "it should update the data smartly" — a refresh that only
        // re-fetched the synopsis must not blank the cover/genres/status.
        seedAniListContent("m1", 171018, "Dandadan")
        seedBothAxes("m1")

        repo.updateDataSourceAxis(
            ContentDetails(mainId = "m1", dataSynopsis = "Refreshed synopsis"),
        )

        val after = repo.getContentDetails("m1")!!
        assertEquals("Refreshed synopsis", after.dataSynopsis)
        assertEquals("Action, Comedy", after.dataGenres)
        assertEquals("RELEASING", after.dataStatus)
        assertEquals("anilist-cover", after.dataCoverUrl)
        assertEquals("anilist", after.dataSourceType)
    }

    @Test
    fun `the intentional axis replace overwrites everything (the switch path)`() {
        // B2's other half: a SWITCH to a different data source must NOT leave
        // the previous source's fields behind — replaceDataSourceAxis exists
        // for exactly that and is the only data-axis write allowed to erase.
        seedAniListContent("m1", 171018, "Dandadan")
        seedBothAxes("m1")

        repo.replaceDataSourceAxis(
            ContentDetails(
                mainId = "m1",
                dataSourceType = "mal",
                dataSourceRefId = "999",
                dataStatus = "FINISHED",
            ),
        )

        val after = repo.getContentDetails("m1")!!
        assertEquals("mal", after.dataSourceType)
        assertEquals("999", after.dataSourceRefId)
        assertEquals("FINISHED", after.dataStatus)
        assertNull("stale fields from the previous source are gone", after.dataGenres)
        assertNull(after.dataCoverUrl)
        // The ext axis is untouched (axis isolation holds for replace too).
        assertEquals("Extension synopsis", after.extDescription)
    }

    @Test
    fun `the extension-id pair cannot drift - source_id derives from extension_id (B7)`() {
        seedAniListContent("m1", 171018, "Dandadan")
        seedBothAxes("m1")

        repo.updateExtensionAxis(
            ContentDetails(mainId = "m1", extensionType = "aniyomi", extensionId = "12345"),
        )

        val after = repo.getContentDetails("m1")!!
        assertEquals("12345", after.extensionId)
        assertEquals("the INTEGER twin is derived, always agreeing", 12345L, after.sourceId)
    }

    @Test
    fun `a non-numeric extension id derives a null source_id (B7, CloudStream names)`() {
        seedAniListContent("m1", 171018, "Dandadan")
        seedBothAxes("m1")

        repo.replaceExtensionAxis(
            ContentDetails(
                mainId = "m1",
                extensionType = "cloudstream",
                extensionId = "com.lagradost.cloudstream3",
            ),
        )

        val after = repo.getContentDetails("m1")!!
        assertEquals("com.lagradost.cloudstream3", after.extensionId)
        assertNull("a source-name string has no INTEGER twin", after.sourceId)
    }

    @Test
    fun `reconcileExtensionIds realigns drifted rows`() {
        seedAniListContent("m1", 171018, "Dandadan")
        seedBothAxes("m1")
        // Simulate legacy drift: extension_id numeric, source_id stale/absent.
        driver.execute(
            null,
            "UPDATE content_details SET source_id = NULL, extension_id = '777' WHERE main_id = 'm1'",
            0,
        )
        assertNull(repo.getContentDetails("m1")!!.sourceId)

        repo.reconcileExtensionIds()

        assertEquals(777L, repo.getContentDetails("m1")!!.sourceId)
    }

    // ── B1: the scanner's insert can no longer clobber (D-791) ────────────

    @Test
    fun `insertMainEntryIfMissing never overwrites an existing row`() {
        seedAniListContent("m1", 171018, "The Current Title")
        val detailsBefore = repo.getMainEntryByMainId("m1")!!

        // The scanner's snapshot — STALE data from download time.
        repo.insertMainEntryIfMissing(
            ContentRecord(
                mainId = "m1",
                contentId = "stale-content-id",
                title = "The Stale Download-Time Title",
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        val after = repo.getMainEntryByMainId("m1")!!
        assertEquals("The Current Title", after.title)
        assertEquals("the DB row is canonical — nothing rolled back", detailsBefore.contentId, after.contentId)
        assertEquals(detailsBefore.createdAt, after.createdAt)
    }

    @Test
    fun `insertMainEntryIfMissing restores a genuinely missing row`() {
        val restored = ContentRecord(
            mainId = "restored-1",
            contentId = "restored-cid",
            title = "Restored Anime",
            createdAt = 5_000L,
            updatedAt = 6_000L,
        )
        repo.insertMainEntryIfMissing(restored)
        assertEquals("Restored Anime", repo.getMainEntryByMainId("restored-1")!!.title)
    }

    // ── B3: the display_source flip is validated (D-794) ──────────────────

    @Test
    fun `a flip to an unlinked axis is refused`() {
        seedAniListContent("m1", 171018, "Dandadan", displaySource = "data_source")
        // The row has a details row with ONLY the data axis linked.
        repo.upsertContentDetails(
            ContentDetails(mainId = "m1", dataSourceType = "anilist", dataSourceRefId = "171018"),
        )

        repo.updateMainEntryDisplaySource("m1", "extension")

        assertEquals(
            "no ext axis → the flip is refused, the row keeps its honest value",
            "data_source",
            repo.getMainEntryByMainId("m1")!!.displaySource,
        )
    }

    @Test
    fun `an invalid display_source value is refused outright`() {
        seedAniListContent("m1", 171018, "Dandadan")
        seedBothAxes("m1")

        repo.updateMainEntryDisplaySource("m1", "anilist") // legacy source-name value

        assertEquals("data_source", repo.getMainEntryByMainId("m1")!!.displaySource)
    }

    @Test
    fun `a flip to a linked axis still works`() {
        seedAniListContent("m1", 171018, "Dandadan", displaySource = "data_source")
        seedBothAxes("m1")

        repo.updateMainEntryDisplaySource("m1", "extension")
        assertEquals("extension", repo.getMainEntryByMainId("m1")!!.displaySource)
    }

    // ── B5: main_entry.title is the single display truth (D-797) ──────────

    @Test
    fun `a blank title candidate can never blank the truth`() {
        seedAniListContent("m1", 171018, "The Real Title")

        repo.updateMainEntryTitle("m1", "   ")

        assertEquals("The Real Title", repo.getMainEntryByMainId("m1")!!.title)
    }

    @Test
    fun `a real title write trims and lands`() {
        seedAniListContent("m1", 171018, "The Real Title")

        repo.updateMainEntryTitle("m1", "  Renamed Anime  ")

        assertEquals("Renamed Anime", repo.getMainEntryByMainId("m1")!!.title)
    }

    // ── B6: the ecosystem derivation is shared and honest (D-796) ─────────

    @Test
    fun `ecosystemNameForEntry resolves the row's real system`() {
        val record = seedAniListContent("m1", 171018, "Dandadan")
        val aniyomiId = repo.getSystemByName("aniyomi")!!.id
        val withSystem = record.copy(systemId = aniyomiId)
        repo.insertMainEntry(withSystem)

        assertEquals("aniyomi", ecosystemNameForEntry(repo, repo.getMainEntryByMainId("m1")!!))
    }

    @Test
    fun `ecosystemNameForEntry keeps null for an unlinked row (legacy id shape)`() {
        seedAniListContent("m1", 171018, "Dandadan") // no system FK
        assertNull(ecosystemNameForEntry(repo, repo.getMainEntryByMainId("m1")!!))
    }

    @Test
    fun `ecosystemNameForEntry trusts the bridged id over a stale system label`() {
        val record = seedAniListContent("m1", 171018, "Dandadan")
        val aniyomiId = repo.getSystemByName("aniyomi")!!.id
        // A CS-bridged extension id mislabelled with the aniyomi system row —
        // exactly what healCloudstreamEcosystem exists to fix.
        repo.insertMainEntry(record.copy(systemId = aniyomiId, extensionId = 0x4000_0000_0000_0001L))

        assertEquals("cloudstream", ecosystemNameForEntry(repo, repo.getMainEntryByMainId("m1")!!))
    }

    // ── the link/title-sync semantics ──────────────────────────────────────

    @Test
    fun `linking an extension syncs main_entry title when the extension axis is displayed`() {
        seedAniListContent("m1", 171018, "The AniList Name", displaySource = "extension")

        resolver.linkExtensionToExisting(
            mainId = "m1",
            extensionId = 42L,
            sourceId = 42L,
            animeUrl = "https://example.test/dandadan",
            title = "The Extension Name",
        )

        assertEquals("The Extension Name", repo.getMainEntryByMainId("m1")!!.title)
    }

    @Test
    fun `linking an extension does NOT touch the title when the data axis is displayed`() {
        seedAniListContent("m1", 171018, "The AniList Name", displaySource = "data_source")

        resolver.linkExtensionToExisting(
            mainId = "m1",
            extensionId = 42L,
            sourceId = 42L,
            animeUrl = "https://example.test/dandadan",
            title = "The Extension Name",
        )

        assertEquals("The AniList Name", repo.getMainEntryByMainId("m1")!!.title)
        // But the ext identity IS written.
        val record = repo.getMainEntryByMainId("m1")!!
        assertEquals(42L, record.extensionId)
    }

    @Test
    fun `resolveOrCreateForAniList is idempotent per anilist id`() {
        val first = resolver.resolveOrCreateForAniList(171018, "Dandadan")
        val second = resolver.resolveOrCreateForAniList(171018, "Dandadan")
        assertEquals(first, second)
        assertNotNull(repo.getMainEntryByAniListId(171018))
    }

    // ── the display-source + unlink semantics ──────────────────────────────

    @Test
    fun `display source persists across a read`() {
        seedAniListContent("m1", 171018, "Dandadan", displaySource = "data_source")
        seedBothAxes("m1") // both axes linked — the flips are legal under B3
        repo.updateMainEntryDisplaySource("m1", "extension")
        assertEquals("extension", repo.getMainEntryByMainId("m1")!!.displaySource)
        repo.updateMainEntryDisplaySource("m1", "data_source")
        assertEquals("data_source", repo.getMainEntryByMainId("m1")!!.displaySource)
    }

    @Test
    fun `clearing the data axis keeps the extension axis and vice versa`() {
        seedAniListContent("m1", 171018, "Dandadan")
        seedBothAxes("m1")

        repo.clearDataSourceAxis("m1")
        val afterDataClear = repo.getContentDetails("m1")!!
        assertNull(afterDataClear.dataSynopsis)
        assertEquals("Extension synopsis", afterDataClear.extDescription)

        repo.clearExtensionAxis("m1")
        val afterExtClear = repo.getContentDetails("m1")!!
        assertNull(afterExtClear.extDescription)
        assertNull(afterExtClear.dataSynopsis)
    }

    @Test
    fun `unlinkAniList keeps the extension axis`() {
        seedAniListContent("m1", 171018, "Dandadan")
        seedBothAxes("m1")

        resolver.unlinkAniList("m1")

        val record = repo.getMainEntryByMainId("m1")!!
        assertNull(record.dataSourceId)
        val details = repo.getContentDetails("m1")!!
        // The departed axis is cleared; the surviving axis intact.
        assertNull(details.dataSynopsis)
        assertEquals("Extension synopsis", details.extDescription)
    }

    @Test
    fun `the seeded lookups resolve by name`() {
        assertNotNull(repo.getSystemByName("cloudstream"))
        assertEquals("CloudStream", repo.getSystemByName("cloudstream")!!.displayName)
    }
}
