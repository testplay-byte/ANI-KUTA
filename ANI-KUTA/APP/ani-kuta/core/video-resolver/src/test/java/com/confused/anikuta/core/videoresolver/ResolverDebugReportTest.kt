package com.confused.anikuta.core.videoresolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 58 (round 18): locks the ANIYOMI-side resolve debug report builders —
 * the mirror of the CS stack's CsSourceListUiTest report locks.
 *
 * Deterministic format (byte-identical output), header KEYS ONLY (values
 * never ride the clipboard), comma-safe MPV-format parsing.
 */
class ResolverDebugReportTest {

    private fun video(
        quality: String = "1080p",
        url: String = "https://cdn.example.com/file.mp4",
        title: String = "",
        headers: String? = null,
    ) = ResolverVideo(quality = quality, url = url, videoTitle = title, videoHeaders = headers)

    private fun servers(): List<ResolverServer> = listOf(
        ResolverServer(
            name = "Vidstream",
            audioVersions = listOf(
                ResolverAudioVersion(
                    label = "SUB",
                    videos = listOf(
                        video(
                            quality = "1080p",
                            title = "Vidstream 1080p SUB",
                            headers = "Referer: https://vid.example.com/,User-Agent: Mozilla/5.0 (Linux; Android 14) Mobile Safari/537.36",
                        ),
                        video(quality = "720p", headers = "Referer: https://vid.example.com/"),
                    ),
                ),
            ),
        ),
        ResolverServer(
            name = "Doodstream",
            audioVersions = listOf(
                ResolverAudioVersion(
                    label = "Default",
                    videos = listOf(video(quality = "Default")),
                ),
            ),
        ),
    )

    @Test
    fun `report is deterministic with numbered blocks in display order`() {
        val first = ResolverDebugReport.buildReport(
            sourceName = "AniKoto",
            animeTitle = "Frieren",
            episodeNumber = 5f,
            servers = servers(),
        )
        val second = ResolverDebugReport.buildReport(
            sourceName = "AniKoto",
            animeTitle = "Frieren",
            episodeNumber = 5f,
            servers = servers(),
        )
        assertEquals(first, second)

        val expected = buildString {
            appendLine("ANI-KUTA resolve report (aniyomi extensions)")
            appendLine("source: AniKoto")
            appendLine("anime: Frieren")
            appendLine("episode: 5.0")
            appendLine("videos: 3")
            appendLine("---")
            appendLine("1. server: Vidstream")
            appendLine("   audio: SUB")
            appendLine("   quality: 1080p")
            appendLine("   title: Vidstream 1080p SUB")
            appendLine("   headers: [Referer, User-Agent]")
            appendLine("   url: https://cdn.example.com/file.mp4")
            appendLine("2. server: Vidstream")
            appendLine("   audio: SUB")
            appendLine("   quality: 720p")
            appendLine("   headers: [Referer]")
            appendLine("   url: https://cdn.example.com/file.mp4")
            appendLine("3. server: Doodstream")
            appendLine("   audio: Default")
            appendLine("   quality: Default")
            appendLine("   headers: []")
            appendLine("   url: https://cdn.example.com/file.mp4")
        }
        assertEquals(expected, first)
    }

    @Test
    fun `blank context lines are omitted`() {
        val report = ResolverDebugReport.buildReport(
            sourceName = "",
            animeTitle = "",
            episodeNumber = 0f,
            servers = listOf(
                ResolverServer(
                    name = "S",
                    audioVersions = listOf(
                        ResolverAudioVersion(label = "SUB", videos = listOf(video())),
                    ),
                ),
            ),
        )
        assertTrue(!report.contains("source:"))
        assertTrue(!report.contains("anime:"))
        assertTrue(report.contains("episode: 0.0"))
        assertTrue(report.contains("videos: 1"))
    }

    @Test
    fun `empty servers produce header with no separator`() {
        val report = ResolverDebugReport.buildReport("S", "A", 1f, emptyList())
        assertTrue(report.endsWith("videos: 0\n"))
        assertTrue(!report.contains("---"))
    }

    @Test
    fun `one-video form carries the single block`() {
        val detail = ResolverDebugReport.buildVideoDetail(
            server = servers()[0],
            audio = servers()[0].audioVersions[0],
            video = servers()[0].audioVersions[0].videos[0],
        )
        assertTrue(detail.startsWith("1. server: Vidstream\n"))
        assertTrue(detail.contains("   quality: 1080p\n"))
        assertTrue(detail.endsWith("   url: https://cdn.example.com/file.mp4\n"))
    }

    @Test
    fun `header keys are comma-safe through UA commas`() {
        val headers = "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36,Referer: https://vid.example.com/"
        val keys = ResolverDebugReport.extractHeaderKeys(headers)
        assertEquals(listOf("User-Agent", "Referer"), keys)
    }

    @Test
    fun `header values never leak into the key list`() {
        val headers = "Referer: https://vid.example.com/watch,episode=5,season=2"
        // "episode=5" and "season=2" do NOT look like header names → they stay
        // part of the Referer VALUE, and only the key survives.
        val keys = ResolverDebugReport.extractHeaderKeys(headers)
        assertEquals(listOf("Referer"), keys)
    }

    @Test
    fun `null and blank headers yield no keys`() {
        assertEquals(emptyList<String>(), ResolverDebugReport.extractHeaderKeys(null))
        assertEquals(emptyList<String>(), ResolverDebugReport.extractHeaderKeys(""))
        assertEquals(emptyList<String>(), ResolverDebugReport.extractHeaderKeys("   "))
    }

    @Test
    fun `duplicate header names are de-duplicated`() {
        val keysExact = ResolverDebugReport.extractHeaderKeys("Referer: https://a.com/,Referer: https://b.com/")
        assertEquals(listOf("Referer"), keysExact)
        val keysCase = ResolverDebugReport.extractHeaderKeys("Referer: https://a.com/,referer: https://b.com/")
        assertEquals(listOf("Referer", "referer"), keysCase)
    }
}
