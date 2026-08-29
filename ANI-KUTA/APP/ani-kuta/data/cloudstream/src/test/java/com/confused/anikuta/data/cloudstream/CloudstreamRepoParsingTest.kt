// :data:cloudstream unit tests — repo protocol parsing against REAL ecosystem
// fixtures (doc 23 §6). The fixtures are verbatim copies from the research clones:
// official recloudstream/extensions repo.json, phisher repo.json + plugins.json
// (trimmed to 3 entries), the AllMovieLandProvider manifest.json, and an aniyomi
// index.json sample for the detector negative test.
@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class)

package com.confused.anikuta.data.cloudstream

import com.confused.anikuta.data.cloudstream.installer.CloudstreamPluginInstaller
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoApi
import com.confused.anikuta.data.cloudstream.repo.CsPluginRecord
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.utils.AppUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CloudstreamRepoParsingTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes().decodeToString()

    // ── repo.json parsing (real files) ──────────────────────────────────────

    @Test
    fun parsesOfficialRepoJson() {
        val repo = CloudstreamRepoApi.parseRepositoryOrNull(fixture("repo_official.json"))
        assertNotNull(repo)
        repo!!
        assertEquals("Cloudstream providers repository", repo.name)
        assertEquals(1, repo.manifestVersion)
        assertEquals(1, repo.pluginLists.size)
        assertTrue(repo.pluginLists[0].endsWith("/builds/plugins.json"))
    }

    @Test
    fun parsesPhisherRepoJson_withIconUrl() {
        val repo = CloudstreamRepoApi.parseRepositoryOrNull(fixture("repo_phisher.json"))
        assertNotNull(repo)
        repo!!
        assertEquals("Phisher Repo", repo.name)
        assertNotNull(repo.iconUrl)
        assertEquals(1, repo.pluginLists.size)
    }

    // ── plugins.json parsing (real entries) ─────────────────────────────────

    @Test
    fun parsesRealPluginsJson() {
        val plugins = Json { ignoreUnknownKeys = true }
            .decodeFromString<List<com.lagradost.cloudstream3.plugins.SitePlugin>>(
                fixture("plugins_sample.json"),
            )
        assertEquals(3, plugins.size)

        val first = plugins[0]
        assertEquals("AllMovieLandProvider", first.internalName)
        assertEquals("AllMovieLandProvider", first.name)
        assertEquals(23, first.version)
        assertEquals(1, first.status)
        assertEquals(listOf("Phisher98"), first.authors)
        assertEquals(57618L, first.fileSize)
        assertNotNull(first.fileHash)
        assertTrue(first.fileHash!!.startsWith("sha256-"))
        assertEquals(64, first.fileHash!!.removePrefix("sha256-").length)
        assertTrue(first.tvTypes!!.contains("Movie"))
    }

    // ── The repo-type detector (add-dialog auto-detection) ──────────────────

    @Test
    fun detector_acceptsCloudstreamRepoJson() {
        assertNotNull(CloudstreamRepoApi.parseRepositoryOrNull(fixture("repo_official.json")))
        assertNotNull(CloudstreamRepoApi.parseRepositoryOrNull(fixture("repo_phisher.json")))
    }

    @Test
    fun detector_rejectsAniyomiIndexJson() {
        // An aniyomi index.json is an ARRAY of extension entries — structurally not a repo.json.
        assertNull(CloudstreamRepoApi.parseRepositoryOrNull(fixture("aniyomi_index_sample.json")))
    }

    @Test
    fun detector_rejectsGarbageAndAniyomiMetaShape() {
        assertNull(CloudstreamRepoApi.parseRepositoryOrNull("not json at all"))
        assertNull(CloudstreamRepoApi.parseRepositoryOrNull("""{"meta":{"name":"x","website":"y"}}"""))
        // Missing pluginLists → not a CS repo.
        assertNull(CloudstreamRepoApi.parseRepositoryOrNull("""{"name":"x","manifestVersion":1}"""))
    }

    // ── manifest.json (real plugin manifest) ────────────────────────────────

    @Test
    fun parsesRealPluginManifest() {
        val manifest = with(AppUtils) {
            parseJson(fixture("manifest_allmovieland.json"), BasePlugin.Manifest::class)
        }
        assertEquals("com.phisher98.AllMovieLandProviderPlugin", manifest.pluginClassName)
        assertEquals("AllMovieLandProvider", manifest.name)
        assertEquals(23, manifest.version)
        assertFalse(manifest.requiresResources)
    }

    // ── Install-path salting (doc 02 §5.1) ──────────────────────────────────

    @Test
    fun pluginPath_isRepoSalted() {
        val a = CloudstreamPluginInstaller.pluginPath(
            File("/tmp"),
            "AllMovieLandProvider",
            "https://example.com/repoA/repo.json",
        )
        val b = CloudstreamPluginInstaller.pluginPath(
            File("/tmp"),
            "AllMovieLandProvider",
            "https://example.com/repoB/repo.json",
        )
        // Same plugin name from two repos → two distinct install paths.
        assertFalse(a.absolutePath == b.absolutePath)
        assertTrue(a.parentFile!!.name.contains("."))
        assertTrue(a.name.startsWith("AllMovieLandProvider."))
        assertTrue(a.name.endsWith(".cs3"))
    }

    @Test
    fun sanitize_stripsHostileCharacters() {
        assertEquals("a_b_c", CloudstreamPluginInstaller.sanitizeFilename("a/b\\c"))
        assertEquals("repo", CloudstreamPluginInstaller.sanitizeFilename("   "))
    }

    // ── Update predicate (doc 04 §4.5) ──────────────────────────────────────

    @Test
    fun updatePredicate_biggerIntOrAlwaysFlag() {
        assertTrue(isCsUpdate(24, 23)) // bigger = newer
        assertFalse(isCsUpdate(23, 23)) // equal = no update
        assertFalse(isCsUpdate(22, 23)) // lower = ignored (no rollback)
        assertTrue(isCsUpdate(-1, 99999)) // -1 = always update
    }

    // ── Plugin record round-trip ────────────────────────────────────────────

    @Test
    fun pluginRecord_jsonRoundTrip() {
        val record = CsPluginRecord(
            internalName = "AllMovieLandProvider",
            name = "AllMovieLandProvider",
            url = "https://example.com/x.cs3",
            filePath = "/data/files/CloudstreamExtensions/repo.x/AllMovieLandProvider.123.cs3",
            version = 23,
            repoUrl = "https://example.com/repo.json",
            fileHash = "sha256-abc",
            language = "en",
            iconUrl = "https://example.com/icon_%size%.png",
            isNsfw = false,
        )
        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(record)
        val decoded = json.decodeFromString<CsPluginRecord>(encoded)
        assertEquals(record, decoded)
        assertEquals("en", decoded.language)
        assertEquals("https://example.com/icon_%size%.png", decoded.iconUrl)
        assertEquals(false, decoded.isNsfw)
    }

    @Test
    fun pluginRecord_legacyJson_isTolerated() {
        // Session 1 persisted an `isEnabled` field that session 2 removed, and
        // pre-enrichment records lack language/iconUrl/isNsfw entirely — devices
        // upgrading across the boundary must decode cleanly via defaults.
        val legacy = """{
            "internalName": "AllMovieLandProvider",
            "name": "AllMovieLandProvider",
            "url": "https://example.com/x.cs3",
            "filePath": "/data/files/CloudstreamExtensions/repo.x/AllMovieLandProvider.123.cs3",
            "version": 23,
            "repoUrl": "https://example.com/repo.json",
            "fileHash": "sha256-abc",
            "isEnabled": false
        }"""
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<CsPluginRecord>(legacy)
        assertEquals("AllMovieLandProvider", decoded.internalName)
        assertEquals(23, decoded.version)
        assertNull(decoded.language)
        assertNull(decoded.iconUrl)
        assertEquals(false, decoded.isNsfw)
    }
}
