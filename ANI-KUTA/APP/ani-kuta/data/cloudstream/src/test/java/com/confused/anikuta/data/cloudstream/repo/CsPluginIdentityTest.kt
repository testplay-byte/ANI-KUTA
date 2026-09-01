package com.confused.anikuta.data.cloudstream.repo

import com.lagradost.cloudstream3.plugins.SitePlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 62 (round 22): locks the plugin ↔ repository identity ladder — the
 * resolver that decides whether an INSTALLED [CsPluginRecord] and an ONLINE
 * [SitePlugin] catalog entry are the SAME plugin (the round-22 device
 * report: a manually installed .cs3 and the repository's copy rendered as
 * TWO rows; the user asked that the app "properly recognize the cloud stream
 * extensions and their repositories even after the repository was added later
 * on").
 *
 * The ladder, in order (see [CsPluginIdentity]):
 *  1. exact internalName;
 *  2. the repo's internalName captured at link time (repoInternalName);
 *  3. download URL equality;
 *  4. fileHash equality ("sha256-<hex>");
 *  5. NORMALIZED internalName (lowercase letters/digits only);
 *  6. NORMALIZED display-name.
 */
class CsPluginIdentityTest {

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun plugin(
        internalName: String,
        name: String = internalName,
        url: String = "https://repo.example/$internalName.cs3",
        fileHash: String? = null,
    ) = SitePlugin(
        url = url,
        status = 1,
        version = 10,
        apiVersion = 1,
        name = name,
        internalName = internalName,
        authors = listOf("someone"),
        fileHash = fileHash,
    )

    private fun record(
        internalName: String,
        name: String = internalName,
        url: String? = null,
        repoInternalName: String? = null,
        fileHash: String? = null,
    ) = CsPluginRecord(
        internalName = internalName,
        name = name,
        url = url,
        filePath = "/data/CloudstreamExtensions/x/$internalName.cs3",
        version = 9,
        repoUrl = null,
        repoInternalName = repoInternalName,
        fileHash = fileHash,
    )

    // ── matches(): the ordered ladder ───────────────────────────────────────

    @Test
    fun `rung 1 - exact internalName matches`() {
        assertTrue(
            CsPluginIdentity.matches(
                record(internalName = "MovieBoxProvider"),
                plugin(internalName = "MovieBoxProvider"),
            ),
        )
    }

    @Test
    fun `rung 2 - repoInternalName matches after the linkage back-fill`() {
        // The manual import stored "MovieBox" (manifest-derived), the linkage
        // captured the repo's catalog name — the exact match still fires.
        assertTrue(
            CsPluginIdentity.matches(
                record(internalName = "MovieBox", repoInternalName = "MovieBoxProvider"),
                plugin(internalName = "MovieBoxProvider"),
            ),
        )
    }

    @Test
    fun `rung 3 - download URL matches a repo-renamed file`() {
        assertTrue(
            CsPluginIdentity.matches(
                record(internalName = "moviebox_1", url = "https://repo.example/ExampleProvider.cs3"),
                plugin(internalName = "ExampleProvider", url = "https://repo.example/ExampleProvider.cs3"),
            ),
        )
    }

    @Test
    fun `rung 4 - fileHash matches identical bytes`() {
        val hash = "sha256-" + "a".repeat(64)
        assertTrue(
            CsPluginIdentity.matches(
                record(internalName = "chat-download", fileHash = hash),
                plugin(internalName = "RepoStem", fileHash = hash),
            ),
        )
    }

    @Test
    fun `rung 4 - blank hashes never match`() {
        val hash = "sha256-" + "a".repeat(64)
        assertFalse(
            CsPluginIdentity.matches(
                record(internalName = "first", fileHash = null),
                plugin(internalName = "second", fileHash = hash),
            ),
        )
    }

    @Test
    fun `rung 5 - normalized internalName survives case plus separators`() {
        for (drift in listOf("Movie_Box", "movie-box", "MovieBox", "movie box", "MovieBox.")) {
            assertTrue(
                "expected a match for drifted name '$drift'",
                CsPluginIdentity.matches(
                    record(internalName = drift),
                    plugin(internalName = "MovieBox"),
                ),
            )
        }
    }

    @Test
    fun `rung 6 - normalized display names match a manifest-derived import`() {
        // The manual import's internalName came from sanitizeFilename(manifest.name);
        // the repo's stem genuinely diverges — the display names agree.
        assertTrue(
            CsPluginIdentity.matches(
                record(internalName = "MovieBox", name = "MovieBox"),
                plugin(internalName = "MovieBoxProvider", name = "MovieBox"),
            ),
        )
    }

    @Test
    fun `different plugins never match`() {
        assertFalse(
            CsPluginIdentity.matches(
                record(internalName = "AlphaProvider", name = "Alpha", url = "https://a/alpha.cs3"),
                plugin(internalName = "BetaProvider", name = "Beta", url = "https://b/beta.cs3"),
            ),
        )
    }

    @Test
    fun `blank display names never trigger the display-name rung`() {
        // name = "" on both sides normalizes to "" — empty must NOT match.
        assertFalse(
            CsPluginIdentity.matches(
                record(internalName = "first", name = ""),
                plugin(internalName = "second", name = ""),
            ),
        )
    }

    // ── normalize() ─────────────────────────────────────────────────────────

    @Test
    fun `normalize lowercases and keeps only letters and digits`() {
        assertEquals("moviebox", CsPluginIdentity.normalize("Movie_Box"))
        assertEquals("moviebox", CsPluginIdentity.normalize("movie-box.cs3"))
        assertEquals("moviebox", CsPluginIdentity.normalize("  Movie Box  "))
        assertEquals("", CsPluginIdentity.normalize("___-."))
        assertEquals("moviebox2", CsPluginIdentity.normalize("MovieBox (2)"))
    }

    // ── matchesImport(): the import-side variant ────────────────────────────

    @Test
    fun `matchesImport - exact internalName`() {
        assertTrue(
            CsPluginIdentity.matchesImport(
                record(internalName = "MovieBox"),
                importInternalName = "MovieBox",
                importDisplayName = null,
                importFileHash = null,
            ),
        )
    }

    @Test
    fun `matchesImport - normalized drift`() {
        assertTrue(
            CsPluginIdentity.matchesImport(
                record(internalName = "Movie_Box"),
                importInternalName = "moviebox",
                importDisplayName = null,
                importFileHash = null,
            ),
        )
    }

    @Test
    fun `matchesImport - linked repo name matches a re-import under the repo identity`() {
        // The record was linked (repoInternalName set); the re-import arrives
        // named after the repo's stem.
        assertTrue(
            CsPluginIdentity.matchesImport(
                record(internalName = "MovieBox", repoInternalName = "MovieBoxProvider"),
                importInternalName = "MovieBoxProvider",
                importDisplayName = null,
                importFileHash = null,
            ),
        )
    }

    @Test
    fun `matchesImport - file hash`() {
        val hash = "sha256-" + "b".repeat(64)
        assertTrue(
            CsPluginIdentity.matchesImport(
                record(internalName = "a", fileHash = hash),
                importInternalName = "b",
                importDisplayName = null,
                importFileHash = hash,
            ),
        )
    }

    @Test
    fun `matchesImport - display names`() {
        assertTrue(
            CsPluginIdentity.matchesImport(
                record(internalName = "drifted-name", name = "MovieBox"),
                importInternalName = "something-else",
                importDisplayName = "MovieBox",
                importFileHash = null,
            ),
        )
    }

    @Test
    fun `matchesImport - different plugin does not match`() {
        assertFalse(
            CsPluginIdentity.matchesImport(
                record(internalName = "Alpha", name = "Alpha", fileHash = "sha256-x"),
                importInternalName = "Beta",
                importDisplayName = "Beta",
                importFileHash = "sha256-y",
            ),
        )
    }
}
