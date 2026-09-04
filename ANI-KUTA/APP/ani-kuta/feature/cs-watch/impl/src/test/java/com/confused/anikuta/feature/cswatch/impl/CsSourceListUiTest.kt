package com.confused.anikuta.feature.cswatch.impl

import com.confused.anikuta.core.csplayer.CsLinkType
import com.confused.anikuta.core.csplayer.CsVideoLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 55 (round 15) — pure-JVM locks for the source-list grouping (the
 * aniyomi 3-tier Server → AudioVersion → Quality replicated for CS links).
 *
 * Task 57 (round 17 — P5/P4): the serverNameOf bracket vocabulary + the
 * resolve debug report builder's format.
 */
class CsSourceListUiTest {

    private fun link(
        name: String,
        quality: Int,
        url: String,
        type: CsLinkType = CsLinkType.M3U8,
        audioTag: String? = null,
    ) = CsVideoLink(
        name = name, url = url, quality = quality, type = type,
        referer = "", headers = emptyMap(), source = "provider",
        audioTag = audioTag,
    )

    @Test
    fun `links group by server then audio version`() {
        val servers = groupServers(
            listOf(
                link("Mirror", 1080, "u1", audioTag = "SUB"),
                link("Mirror", 720, "u2", audioTag = "DUB"),
            ),
        )
        assertEquals(1, servers.size)
        assertEquals("Mirror", servers[0].name)
        assertEquals(2, servers[0].audioVersions.size)
        assertEquals("SUB", servers[0].audioVersions[0].label)
        assertEquals("DUB", servers[0].audioVersions[1].label)
    }

    @Test
    fun `explicit tags beat name parsing`() {
        val servers = groupServers(
            listOf(
                // Name says SUB but the episode handle tagged DUB — the tag wins.
                link("SUB-ish", 1080, "u1", audioTag = "DUB"),
            ),
        )
        assertEquals("DUB", servers[0].audioVersions[0].label)
    }

    @Test
    fun `name-parsed audio versions when no tag`() {
        val servers = groupServers(
            listOf(
                link("HD-1 - Sub - 1080p", 1080, "u1"),
                link("HD-1 - Dub - 720p", 720, "u2"),
            ),
        )
        assertEquals(1, servers.size)
        assertEquals(listOf("SUB", "DUB"), servers[0].audioVersions.map { it.label })
    }

    @Test
    fun `sub orders before dub and default goes last`() {
        val servers = groupServers(
            listOf(
                link("A", 1080, "u1"),
                link("A", 720, "u2", audioTag = "DUB"),
                link("A", 480, "u3", audioTag = "SUB"),
            ),
        )
        assertEquals(listOf("SUB", "DUB", "Default"), servers[0].audioVersions.map { it.label })
    }

    @Test
    fun `qualities sort descending within a version`() {
        val servers = groupServers(
            listOf(
                link("A", 480, "u1"),
                link("A", 1080, "u2"),
                link("A", 720, "u3"),
            ),
        )
        assertEquals(listOf(1080, 720, 480), servers[0].audioVersions[0].links.map { it.quality })
    }

    // ── Task 56 (round 16 — device feedback F2): highest quality LEFTMOST,
    //    "any other options" (Unknown/Auto) at the far RIGHT. ────────────────

    @Test
    fun `unknown and auto rank after every real height`() {
        val servers = groupServers(
            listOf(
                link("A", 0, "u-auto"), // Auto
                link("A", 144, "u-144"),
                link("A", 400, "u-unknown"), // Unknown
                link("A", 1080, "u-1080"),
                link("A", 480, "u-480"),
            ),
        )
        assertEquals(
            listOf(1080, 480, 144, 400, 0),
            servers[0].audioVersions[0].links.map { it.quality },
        )
    }

    @Test
    fun `quality rank mapping`() {
        assertEquals(-2, qualityRank(0)) // Auto
        assertEquals(-1, qualityRank(400)) // Unknown
        assertEquals(2160, qualityRank(2160))
        assertEquals(144, qualityRank(144))
    }

    @Test
    fun `duplicate quality labels set disambiguateType`() {
        val servers = groupServers(
            listOf(
                link("A", 400, "u1", type = CsLinkType.M3U8), // Unknown label
                link("A", 400, "u2", type = CsLinkType.DASH), // Unknown label again
            ),
        )
        assertTrue(servers[0].audioVersions[0].disambiguateType)
    }

    @Test
    fun `server names strip audio and quality segments`() {
        assertEquals("HD-1", serverNameOf("HD-1 - Sub - 1080p"))
        assertEquals("Vidstream-2", serverNameOf("Vidstream-2 - Dub - 720p"))
        assertEquals("Mirror", serverNameOf("Mirror - 1080p"))
        assertEquals("Mirror", serverNameOf("Mirror"))
    }

    @Test
    fun `token-only names keep their full form`() {
        assertEquals("HSUB - 360p", serverNameOf("HSUB - 360p"))
    }

    @Test
    fun `hyphenated words are not over-stripped`() {
        // "SUB-ish" is ONE word — the segment stripper must not touch it.
        assertEquals("SUB-ish", serverNameOf("SUB-ish"))
    }

    @Test
    fun `servers sort by name`() {
        val servers = groupServers(
            listOf(
                link("Zeta", 1080, "u1"),
                link("Alpha", 1080, "u2"),
            ),
        )
        assertEquals(listOf("Alpha", "Zeta"), servers.map { it.name })
    }

    // ── Task 57 (round 17 — P5): serverNameOf bracket vocabulary. ────────────

    @Test
    fun `server names strip bracketed audio and quality tokens`() {
        assertEquals("Mirror", serverNameOf("Mirror [SUB] 1080p"))
        assertEquals("Streamtape", serverNameOf("Streamtape (Dub) 720p"))
        assertEquals("Server", serverNameOf("Server [1080p]"))
        assertEquals("Mirror", serverNameOf("Mirror [Multi Audio] 1080p"))
    }

    @Test
    fun `bracket stripping keeps the segment rule and blank guard`() {
        // Regression locks: the pre-Task-57 whole-segment rule + blank guard.
        assertEquals("HD-1", serverNameOf("HD-1 - Sub - 1080p"))
        assertEquals("HSUB - 360p", serverNameOf("HSUB - 360p"))
        assertEquals("Vidstream-2", serverNameOf("Vidstream-2"))
    }

    // ── Task 57 (round 17 — P4): the resolve debug report. ──────────────────

    @Test
    fun `resolve debug report formats every link deterministically`() {
        val links = listOf(
            link("Mirror [SUB] 1080p", 1080, "https://a.example/master.m3u8", audioTag = "SUB"),
            link("Streamtape (Dub) 720p", 720, "https://b.example/video", type = CsLinkType.VIDEO, audioTag = "DUB"),
            link("Gogo", 400, "https://c.example/unknown"),
        )
        val report = buildResolveDebugReport("ProviderX", "Solo Leveling", 5f, links)
        val lines = report.split('\n')
        // Header block.
        assertEquals("ANI-KUTA resolve report (v2 debug)", lines.first())
        assertTrue(lines.contains("provider: ProviderX"))
        assertTrue(lines.contains("anime: Solo Leveling"))
        assertTrue(lines.contains("episode: 5.0"))
        assertTrue(lines.contains("links: 3"))
        assertTrue(lines.contains("---"))
        // Per-link blocks: numbering + every field line.
        assertTrue(lines.contains("1. name: Mirror [SUB] 1080p"))
        assertTrue(lines.contains("2. name: Streamtape (Dub) 720p"))
        assertTrue(lines.contains("3. name: Gogo"))
        assertTrue(lines.contains("   server: Mirror"))
        assertTrue(lines.contains("   server: Streamtape"))
        assertTrue(lines.contains("   server: Gogo"))
        assertTrue(lines.contains("   audio: SUB"))
        assertTrue(lines.contains("   audio: DUB"))
        assertTrue(lines.contains("   audio: Default"))
        assertTrue(lines.contains("   quality: 1080p (1080)"))
        assertTrue(lines.contains("   quality: 720p (720)"))
        assertTrue(lines.contains("   quality: Unknown (-1)"))
        assertTrue(lines.contains("   type: M3U8"))
        assertTrue(lines.contains("   type: VIDEO"))
        assertTrue(lines.contains("   referer: "))
        assertTrue(lines.contains("   headers: []"))
        assertTrue(lines.contains("   url: https://a.example/master.m3u8"))
        assertTrue(lines.contains("   url: https://b.example/video"))
        // Deterministic: same input → byte-identical report (no timestamps).
        assertEquals(report, buildResolveDebugReport("ProviderX", "Solo Leveling", 5f, links))
    }

    @Test
    fun `resolve debug report sorts header keys and keeps values out`() {
        val link = CsVideoLink(
            name = "Mirror",
            url = "https://a.example/m.m3u8",
            quality = 1080,
            type = CsLinkType.M3U8,
            referer = "https://ref.example/",
            headers = mapOf("User-Agent" to "secretUA", "Accept" to "*/*"),
            source = "ProviderX",
        )
        val report = buildResolveDebugReport("ProviderX", "Anime", 1f, listOf(link))
        // Keys only (values never leave the device), sorted, referer merged in.
        assertTrue(report.contains("   headers: [Accept, User-Agent, referer]"))
        assertFalse(report.contains("secretUA"))
        assertFalse(report.contains("*/*"))
    }

    @Test
    fun `resolve debug report with zero links has header only`() {
        val report = buildResolveDebugReport("ProviderX", "Solo Leveling", 1f, emptyList())
        val lines = report.split('\n')
        assertTrue(lines.contains("links: 0"))
        assertFalse(lines.contains("---"))
        assertFalse(report.contains("1. name:"))
        assertEquals(5, lines.filter { it.isNotBlank() }.size)
    }
}
