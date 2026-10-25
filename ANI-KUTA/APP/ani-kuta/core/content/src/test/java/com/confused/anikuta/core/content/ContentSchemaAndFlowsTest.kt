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
    fun `a RAW axis write with nulls nulls that axis columns (flaw B2 documented)`() {
        // THE B2 LOCK: updateExtensionAxis SETs every column of its axis —
        // a caller that does not null-merge first ERASES stored values. This
        // test documents the CURRENT raw behavior; if B2's COALESCE defense
        // lands, this test MUST be updated in the same commit (the erase
        // then becomes impossible at the storage layer).
        seedAniListContent("m1", 171018, "Dandadan")
        seedBothAxes("m1")

        repo.updateExtensionAxis(
            ContentDetails(mainId = "m1", extDescription = null, extGenres = null),
        )

        val after = repo.getContentDetails("m1")!!
        assertNull(after.extDescription)
        assertNull(after.extGenres)
        // The data axis STILL survives (axis isolation is structural).
        assertEquals("AniList synopsis", after.dataSynopsis)
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
