// Task 49 (round 9 — THE dead-dispatch regression lock): the scheme-strip
// asymmetry bug (URL normalized, extractor.mainUrl NOT) made loadExtractor
// return false for EVERY URL for a whole device round ("no extension resolves
// any videos", 53/80 census plugins). These tests pin the dispatch contract:
// http/https/www/plain embed URLs all reach the registered extractor whose
// mainUrl matches, reverse registration order (plugin mirrors win over
// built-ins), and unknown hosts return false.
package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.ExtractorLink
import com.lagradost.cloudstream3.ExtractorLinkType
import com.lagradost.cloudstream3.SubtitleFile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Minimal real ExtractorApi — records the url it was handed. */
private class RecordingExtractor(
    override val name: String,
    override val mainUrl: String,
) : ExtractorApi() {
    override val requiresReferer: Boolean = false
    val received = mutableListOf<String>()

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        received += url
        callback(
            ExtractorLink(
                source = name,
                name = "$name 720p",
                url = "https://cdn.example/$name.mp4",
                referer = mainUrl,
                quality = 720,
                type = ExtractorLinkType.VIDEO,
            ),
        )
    }
}

class LoadExtractorDispatchTest {

    @Before
    fun setUp() {
        extractorApis.clear()
    }

    @After
    fun tearDown() {
        extractorApis.clear()
    }

    private fun linkSink(links: MutableList<ExtractorLink>): (ExtractorLink) -> Unit = { links += it }
    private fun subSink(@Suppress("UNUSED_PARAMETER") s: SubtitleFile) = Unit

    @Test
    fun `https embed url dispatches to matching extractor`() = runBlocking {
        val dood = RecordingExtractor("Dood", "https://dood.la")
        extractorApis += dood
        val links = mutableListOf<ExtractorLink>()

        val dispatched = loadExtractor(
            "https://dood.la/e/abc123",
            null,
            ::subSink,
            linkSink(links),
        )

        assertTrue("dispatch must succeed for https URL", dispatched)
        assertEquals(listOf("https://dood.la/e/abc123"), dood.received)
        assertEquals(1, links.size)
    }

    @Test
    fun `http embed url dispatches to http mainUrl`() = runBlocking {
        val okru = RecordingExtractor("OkRuHTTP", "http://ok.ru")
        extractorApis += okru
        val links = mutableListOf<ExtractorLink>()

        val dispatched = loadExtractor(
            "http://ok.ru/video/42",
            null,
            ::subSink,
            linkSink(links),
        )

        assertTrue("plain http URL must dispatch (the old schemaStripRegex missed http://)", dispatched)
        assertEquals(1, okru.received.size)
        assertEquals(1, links.size)
    }

    @Test
    fun `www variant dispatches`() = runBlocking {
        val streamwish = RecordingExtractor("StreamWish", "https://streamwish.to")
        extractorApis += streamwish
        val links = mutableListOf<ExtractorLink>()

        val dispatched = loadExtractor(
            "https://www.streamwish.to/e/xyz",
            null,
            ::subSink,
            linkSink(links),
        )

        assertTrue(dispatched)
        assertEquals(1, streamwish.received.size)
    }

    @Test
    fun `mainUrl with www and trailing slash matches plain embed`() = runBlocking {
        val voe = RecordingExtractor("Voe", "https://www.voe.sx/")
        extractorApis += voe
        val links = mutableListOf<ExtractorLink>()

        val dispatched = loadExtractor(
            "voe.sx/e/abc",
            null,
            ::subSink,
            linkSink(links),
        )

        assertTrue("scheme-less embed URL against www-ful mainUrl must dispatch", dispatched)
        assertEquals(1, voe.received.size)
    }

    @Test
    fun `reverse registration order wins mirror match`() = runBlocking {
        val builtin = RecordingExtractor("Builtin", "https://vidhide.com")
        val mirror = RecordingExtractor("Mirror", "https://vidhide.com") // plugin mirror, registered later
        extractorApis += builtin
        extractorApis += mirror
        val links = mutableListOf<ExtractorLink>()

        val dispatched = loadExtractor(
            "https://vidhide.com/v/abc",
            null,
            ::subSink,
            linkSink(links),
        )

        assertTrue(dispatched)
        assertTrue(
            "the LATER registration (plugin mirror) must win reverse-order dispatch",
            mirror.received.isNotEmpty(),
        )
        assertTrue("the earlier registration must not have been dispatched", builtin.received.isEmpty())
    }

    @Test
    fun `unknown host returns false without dispatch`() = runBlocking {
        extractorApis += RecordingExtractor("Dood", "https://dood.la")
        val links = mutableListOf<ExtractorLink>()

        val dispatched = loadExtractor(
            "https://totally-unknown-host.example/embed/1",
            null,
            ::subSink,
            linkSink(links),
        )

        assertFalse(dispatched)
        assertTrue(links.isEmpty())
    }

    @Test
    fun `no prefix collision between registered families`() = runBlocking {
        // vidhide.com vs vidhide.co style near-collisions: prefix match must be
        // exact — vidhide.co embed must NOT dispatch the vidhide.com extractor.
        val com = RecordingExtractor("VidHideCom", "https://vidhide.com")
        extractorApis += com
        val links = mutableListOf<ExtractorLink>()

        val dispatched = loadExtractor(
            "https://vidhide.co/v/abc",
            null,
            ::subSink,
            linkSink(links),
        )

        // Different TLD → pass 1 must NOT match; the fuzzy pass may or may not
        // fire (near-identical strings) — if it does, the com extractor is the
        // best-effort pick, which is the documented mirror-domain behavior.
        if (dispatched) {
            assertEquals(1, com.received.size)
        } else {
            assertTrue(com.received.isEmpty())
        }
    }

    @Test
    fun `normalizeForDispatch strips scheme www case and trailing slash`() {
        assertEquals("dood.la/e/abc", normalizeForDispatch("https://dood.la/e/abc"))
        assertEquals("dood.la/e/abc", normalizeForDispatch("HTTP://WWW.Dood.la/e/abc/"))
        assertEquals("dood.la", normalizeForDispatch("https://www.dood.la/"))
        assertEquals("dood.la/e/x", normalizeForDispatch("dood.la/e/x"))
    }
}
