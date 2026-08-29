// Interop-fact locks for the clean-room compat surface (doc 23 §6).
// These pin the binary-compatibility facts plugins rely on: enum value sets,
// quality ints, Score math, URL joining, base64 behavior.
package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class TestProvider : MainAPI() {
    override var name = "TestProvider"
    override var mainUrl = "https://example.com"
}

class CompatSurfaceTest {

    // ── Enums: names + order are interop facts ──────────────────────────────

    @Test
    fun tvType_hasExactly18ValuesInOrder() {
        val expected = listOf(
            "Movie", "AnimeMovie", "TvSeries", "Cartoon", "Anime", "OVA", "Torrent",
            "Documentary", "AsianDrama", "Live", "NSFW", "Others", "Music", "AudioBook",
            "CustomMedia", "Audio", "Podcast", "Video",
        )
        assertEquals(expected, TvType.entries.map { it.name })
    }

    @Test
    fun dubStatus_ids() {
        assertEquals(-1, DubStatus.None.id)
        assertEquals(1, DubStatus.Dubbed.id)
        assertEquals(0, DubStatus.Subbed.id)
    }

    @Test
    fun showStatus_values() {
        assertEquals(listOf("Completed", "Ongoing"), ShowStatus.entries.map { it.name })
    }

    // ── Qualities: pixel-height ints ────────────────────────────────────────

    @Test
    fun qualities_values() {
        assertEquals(400, Qualities.Unknown.value)
        assertEquals(144, Qualities.P144.value)
        assertEquals(2160, Qualities.P2160.value)
    }

    @Test
    fun qualities_getStringByInt() {
        assertEquals("", Qualities.getStringByInt(null))
        assertEquals("4K", Qualities.getStringByInt(2160))
        assertEquals("720p", Qualities.getStringByInt(720))
    }

    // ── Score: fixed-point math ─────────────────────────────────────────────

    @Test
    fun score_from10_conversions() {
        val score = Score.from10(8.5)!!
        assertEquals(8, score.toInt(10))
        assertEquals("8.5", score.toString(10))
    }

    @Test
    fun score_outOfRangeReturnsNull() {
        assertNull(Score.from10(11.0))
        assertNull(Score.from10(-1.0))
        assertNull(Score.from10("garbage"))
        assertNull(Score.from10(null as Double?))
    }

    @Test
    fun score_toStringNull_belowMinimum() {
        val score = Score.from10(0.2)!!
        assertNull(score.toStringNull(1.0, 10))
        assertEquals("0.2", score.toStringNull(0.0, 10))
    }

    // ── fixUrl: relative→absolute joining contract ──────────────────────────

    @Test
    fun fixUrl_joinsRelativePaths() {
        val provider = TestProvider()
        assertEquals("https://example.com/path/page.html", provider.fixUrl("/path/page.html"))
        assertEquals("https://example.com/path/page.html", provider.fixUrl("path/page.html"))
        assertEquals("https://example.com/search?q=1", provider.fixUrl("search?q=1"))
    }

    @Test
    fun fixUrl_leavesAbsoluteAlone() {
        val provider = TestProvider()
        assertEquals("https://other.com/x", provider.fixUrl("https://other.com/x"))
        assertEquals("http://other.com/x", provider.fixUrl("http://other.com/x"))
        // Task 48: protocol-relative is https-ified (upstream MainAPI.kt:753-755),
        // NOT passed through untouched.
        assertEquals("https://cdn.example.com/x", provider.fixUrl("//cdn.example.com/x"))
        assertEquals("magnet:?xt=urn:btih:abc", provider.fixUrl("magnet:?xt=urn:btih:abc"))
    }

    @Test
    fun fixUrl_leavesJsonPayloadsAlone() {
        val provider = TestProvider()
        assertEquals("""{"id": 1}""", provider.fixUrl("""{"id": 1}"""))
        assertEquals("""[1,2]""", provider.fixUrl("""[1,2]"""))
    }

    // Task 48 (device round 7): opaque episode-data handles get the mainUrl
    // prefix — upstream fixUrl semantics that providers' loadLinks PARSE
    // (AniKoto: startsWith("$mainUrl/anikoto|"); MovieBox: substringAfterLast('/')).
    @Test
    fun fixUrl_prefixesOpaqueHandles() {
        val provider = TestProvider()
        assertEquals(
            "https://example.com/anikoto|https://anikototv.to/watch/x|token|sub",
            provider.fixUrl("anikoto|https://anikototv.to/watch/x|token|sub"),
        )
        assertEquals(
            "https://example.com/4977819452529168144|2|16",
            provider.fixUrl("4977819452529168144|2|16"),
        )
    }

    // Task 48 (device round 7): THE playback root cause — the generic
    // newEpisode must route String data through the url overload (upstream
    // "just in case java is wack" branch), NOT JSON-encode it. The
    // JSON-quoted handle made every provider's loadLinks fail to parse its
    // own data (AniKoto instant "no links"; MovieBox subjectId=%22… → 400).
    @Test
    fun newEpisode_genericStringData_isNotJsonQuoted() {
        val provider = TestProvider()
        val data: Any = "anikoto|https://anikototv.to/watch/x|token|sub"
        val episode = with(provider) { newEpisode(data) }
        assertEquals("https://example.com/anikoto|https://anikototv.to/watch/x|token|sub", episode.data)
    }

    @Test
    fun newEpisode_genericObjectData_isJsonEncoded() {
        val provider = TestProvider()
        data class Payload(val id: String)
        val episode = with(provider) { newEpisode(Payload("xyz") as Any) }
        assertEquals("""{"id":"xyz"}""", episode.data)
    }

    // ── base64 ──────────────────────────────────────────────────────────────

    @Test
    fun base64_roundTrip() {
        val original = "hello cloudstream"
        val encoded = base64Encode(original.toByteArray())
        assertEquals(original, base64Decode(encoded))
        assertEquals(original, String(base64DecodeArray(encoded)))
    }

    // ── quality label parsing ───────────────────────────────────────────────

    @Test
    fun getQualityFromName_parsesPixelHeight() {
        assertEquals(1080, getQualityFromName("1080p"))
        assertEquals(720, getQualityFromName("720p"))
        assertEquals(400, getQualityFromName(null))
        assertEquals(400, getQualityFromName("HD"))
    }

    // ── TvType helpers ──────────────────────────────────────────────────────

    @Test
    fun tvType_groupingHelpers() {
        assertTrue(TvType.Movie.isMovieType())
        assertTrue(TvType.Video.isMovieType())
        assertFalse(TvType.TvSeries.isMovieType())
        assertTrue(TvType.TvSeries.isEpisodeBased())
        assertTrue(TvType.Anime.isAnimeOp())
        assertTrue(TvType.Music.isAudioType())
        assertTrue(TvType.Live.isLiveStream())
    }

    // ── amap: concurrent map semantics ──────────────────────────────────────

    @Test
    fun amap_mapsConcurrently() = runBlocking {
        val input = listOf(1, 2, 3)
        val result = input.amap { it * 2 }
        assertEquals(listOf(2, 4, 6), result)
    }

    // ── The `json` global: encodeDefaults/ignoreUnknownKeys contract ────────

    @Serializable
    data class JsonContractModel(val name: String)

    @Test
    fun json_globalParsesUnknownKeys() {
        val parsed = json.decodeFromString(JsonContractModel.serializer(), """{"name":"x","unknown":123}""")
        assertEquals("x", parsed.name)
    }
}
