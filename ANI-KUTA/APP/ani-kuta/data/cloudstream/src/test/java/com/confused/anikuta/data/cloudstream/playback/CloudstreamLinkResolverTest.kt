package com.confused.anikuta.data.cloudstream.playback

import com.confused.anikuta.core.csplayer.CsLinkType
import java.io.IOException
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM locks for the loadLinks orchestration (task 52 / Phase C). A fake
 * provider is registered straight into APIHolder (its public mapping API),
 * the resolver's event stream is collected to completion, and the
 * upstream-learned behaviors (progressive snapshots, dedup, torrent hiding,
 * subtitle unique-ifying, cache, timeout) are asserted end-to-end.
 */
class CloudstreamLinkResolverTest {

    /**
     * Task 55: the resolver now content-sniffs subtitles over HTTP; tests get
     * a client whose every call fails IMMEDIATELY (no network, no latency) —
     * the sniff's silent-failure path keeps the extension-based mime.
     */
    private fun noNetworkSniffClient(): okhttp3.OkHttpClient =
        okhttp3.OkHttpClient.Builder()
            .addInterceptor { _ -> throw IOException("no network in tests") }
            .build()

    private class FakeProvider(
        override var name: String,
        override var mainUrl: String = "https://fake.example",
        val behavior: suspend MainAPI.(data: String, links: (ExtractorLink) -> Unit, subs: (SubtitleFile) -> Unit) -> Boolean,
    ) : MainAPI() {
        override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit,
        ): Boolean = behavior(data, callback, subtitleCallback)
    }

    private fun link(
        name: String,
        url: String,
        type: ExtractorLinkType,
        quality: Int = 1080,
        referer: String = "https://fake.example/",
    ) = ExtractorLink(
        source = "FakeProv",
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        headers = emptyMap(),
        extractorData = null,
        type = type,
    )

    private var registered: FakeProvider? = null

    @Before
    fun register() {
        // Ensure clean state even if a previous test leaked a mapping.
        APIHolder.getApiFromNameNull(TEST_NAME)?.let { APIHolder.removePluginMapping(it) }
    }

    @After
    fun unregister() {
        registered?.let { APIHolder.removePluginMapping(it) }
        registered = null
    }

    private fun install(behavior: suspend MainAPI.(String, (ExtractorLink) -> Unit, (SubtitleFile) -> Unit) -> Boolean): FakeProvider =
        FakeProvider(TEST_NAME, behavior = behavior).also {
            APIHolder.addPluginMapping(it)
            registered = it
        }

    private fun collectResolved(resolver: CloudstreamLinkResolver, data: String): List<CloudstreamLinkResolver.CsResolveEvent> =
        runBlocking {
            withTimeout(10_000) { resolver.resolve(TEST_NAME, data).toList() }
        }

    @Test
    fun `progressive snapshots and completion for a normal provider`() {
        install { _, links, subs ->
            links(link("Mirror", "https://fake.example/v.mp4", ExtractorLinkType.VIDEO))
            subs(SubtitleFile("English", "https://fake.example/en.vtt"))
            links(link("HLS", "https://fake.example/s.m3u8", ExtractorLinkType.M3U8, quality = 720))
            subs(SubtitleFile("Spanish", "https://fake.example/es.srt"))
            true
        }
        val resolver = CloudstreamLinkResolver(subSniffClient = ::noNetworkSniffClient)
        val events = collectResolved(resolver, "data-1")

        val linkSnaps = events.filterIsInstance<CloudstreamLinkResolver.CsResolveEvent.LinksSnapshot>()
        val subSnaps = events.filterIsInstance<CloudstreamLinkResolver.CsResolveEvent.SubtitlesSnapshot>()
        val completed = events.filterIsInstance<CloudstreamLinkResolver.CsResolveEvent.Completed>()

        assertEquals(2, linkSnaps.size)
        assertEquals(2, subSnaps.size)
        assertEquals(1, completed.size)
        assertEquals(1, linkSnaps[0].links.size)
        assertEquals(2, linkSnaps[1].links.size)
        // Mapping checks: type + quality + referer survive the trip.
        val hls = linkSnaps[1].links.first { it.type == CsLinkType.M3U8 }
        assertEquals(720, hls.quality)
        assertEquals("https://fake.example/", hls.referer)
        assertEquals("HLS 720p", hls.displayLabel)
        assertEquals(2, subSnaps[1].subtitles.size)
        assertTrue(completed[0].providerSucceeded)
        assertEquals(2, completed[0].linkCount)
    }

    @Test
    fun `duplicate urls are deduped`() {
        install { _, links, _ ->
            links(link("A", "https://fake.example/same.mp4", ExtractorLinkType.VIDEO))
            links(link("B", "https://fake.example/same.mp4", ExtractorLinkType.VIDEO))
            links(link("C", "https://fake.example/other.mp4", ExtractorLinkType.VIDEO))
            true
        }
        val events = collectResolved(CloudstreamLinkResolver(subSniffClient = ::noNetworkSniffClient), "data-2")
        val final = events.filterIsInstance<CloudstreamLinkResolver.CsResolveEvent.LinksSnapshot>().last()
        assertEquals(2, final.links.size)
    }

    @Test
    fun `torrent and magnet links are hidden and counted`() {
        install { _, links, _ ->
            links(link("T1", "https://fake.example/a.torrent", ExtractorLinkType.TORRENT))
            links(link("M1", "magnet:?xt=urn:btih:x", ExtractorLinkType.MAGNET))
            links(link("OK", "https://fake.example/ok.mp4", ExtractorLinkType.VIDEO))
            true
        }
        val events = collectResolved(CloudstreamLinkResolver(subSniffClient = ::noNetworkSniffClient), "data-3")
        val final = events.filterIsInstance<CloudstreamLinkResolver.CsResolveEvent.LinksSnapshot>().last()
        assertEquals(1, final.links.size)
        assertEquals(2, final.hiddenTorrentCount)
    }

    @Test
    fun `duplicate subtitle names get suffixes`() {
        install { _, _, subs ->
            subs(SubtitleFile("English", "https://fake.example/a.vtt"))
            subs(SubtitleFile("English", "https://fake.example/b.vtt"))
            subs(SubtitleFile("English", "https://fake.example/c.vtt"))
            true
        }
        val events = collectResolved(CloudstreamLinkResolver(subSniffClient = ::noNetworkSniffClient), "data-4")
        val final = events.filterIsInstance<CloudstreamLinkResolver.CsResolveEvent.SubtitlesSnapshot>().last()
        assertEquals(listOf("English", "English (2)", "English (3)"), final.subtitles.map { it.name })
        // Mime mapping on the way through.
        assertEquals("text/vtt", final.subtitles[0].mimeType)
    }

    @Test
    fun `dash links are first class citizens`() {
        install { _, links, _ ->
            links(link("DASH", "https://fake.example/s.mpd", ExtractorLinkType.DASH))
            true
        }
        val events = collectResolved(CloudstreamLinkResolver(subSniffClient = ::noNetworkSniffClient), "data-5")
        val final = events.filterIsInstance<CloudstreamLinkResolver.CsResolveEvent.LinksSnapshot>().last()
        assertEquals(1, final.links.size)
        assertEquals(CsLinkType.DASH, final.links[0].type)
    }

    @Test
    fun `missing provider fails honestly`() {
        // No install — TEST_NAME resolves to nothing.
        val events = collectResolved(CloudstreamLinkResolver(subSniffClient = ::noNetworkSniffClient), "data-6")
        val failed = events.filterIsInstance<CloudstreamLinkResolver.CsResolveEvent.Failed>()
        assertEquals(1, failed.size)
        assertTrue(failed[0].message.contains("is not loaded"))
    }

    @Test
    fun `provider crash becomes a descriptive failure`() {
        install { _, _, _ -> throw IllegalStateException("boom inside plugin") }
        val events = collectResolved(CloudstreamLinkResolver(subSniffClient = ::noNetworkSniffClient), "data-7")
        val failed = events.filterIsInstance<CloudstreamLinkResolver.CsResolveEvent.Failed>()
        assertEquals(1, failed.size)
        assertTrue(failed[0].message.contains("IllegalStateException"))
        assertTrue(failed[0].message.contains("boom inside plugin"))
    }

    @Test
    fun `first link timeout fires for a stalled provider`() {
        install { _, _, _ ->
            kotlinx.coroutines.delay(60_000) // stalled beyond the test timeout
            true
        }
        val resolver = CloudstreamLinkResolver(firstLinkTimeoutMs = 400, subSniffClient = ::noNetworkSniffClient)
        val events = collectResolved(resolver, "data-8")
        val failed = events.filterIsInstance<CloudstreamLinkResolver.CsResolveEvent.Failed>()
        assertEquals(1, failed.size)
        assertTrue(failed[0].timedOut)
        assertEquals(0, failed[0].linksSoFar)
    }

    @Test
    fun `second resolve hits the cache`() {
        install { _, links, _ ->
            links(link("Mirror", "https://fake.example/v.mp4", ExtractorLinkType.VIDEO))
            true
        }
        val resolver = CloudstreamLinkResolver(subSniffClient = ::noNetworkSniffClient)
        val first = collectResolved(resolver, "data-9")
        assertEquals(1, first.filterIsInstance<CloudstreamLinkResolver.CsResolveEvent.Completed>().size)

        // Unregister the provider: a cache hit must NOT need it.
        registered?.let { APIHolder.removePluginMapping(it) }
        registered = null
        val second = collectResolved(resolver, "data-9")
        val completed = second.filterIsInstance<CloudstreamLinkResolver.CsResolveEvent.Completed>()
        assertEquals(1, completed.size)
        assertEquals(1, completed[0].linkCount)
        val links = second.filterIsInstance<CloudstreamLinkResolver.CsResolveEvent.LinksSnapshot>()
        assertTrue(links.isNotEmpty())
        assertNotNull(links.first().links.firstOrNull { it.type == CsLinkType.VIDEO })
    }

    companion object {
        private const val TEST_NAME = "FakeResolverProv"
    }
}
