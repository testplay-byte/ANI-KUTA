// Task 50 (round 10, Fix E): unit tests for the bridge's episode mapping —
// toEpisodes / episodesOrComingSoon. These are PURE functions (no live
// provider needed): the bridge resolves its provider by NAME on every call,
// and with nothing registered in APIHolder the image absolutizer falls back
// to pass-through, which is exactly what these tests assert through.
//
// Providers are NOT instantiated through the bridge's getEpisodeList — that
// path needs a registered MainAPI (APIHolder.getApiFromNameNull would return
// null → liveProvider throws); the LoadResponse types are built with the real
// MainAPI.kt factories instead (newTvSeriesLoadResponse / newAnimeLoadResponse
// / newTorrentLoadResponse), matching how plugin code constructs them.
package com.confused.anikuta.data.cloudstream

import com.confused.anikuta.data.cloudstream.content.CloudstreamAnimeSourceBridge
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newTorrentLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudstreamBridgeEpisodesTest {

    private val bridge = CloudstreamAnimeSourceBridge("TestProvider")

    /** Minimal provider — MainAPI is abstract with no abstract members. */
    private val provider = object : MainAPI() {}

    // ── Fix E mapping basics ─────────────────────────────────────────────────

    @Test
    fun tvSeries_twoEpisodes_becomeTwoSEpisodesWithUrlHandles() = runBlocking {
        val response = provider.newTvSeriesLoadResponse(
            name = "Some Series",
            url = "https://example.com/series",
            type = TvType.TvSeries,
            episodes = listOf(
                Episode(data = "series|ep1", episode = 1),
                Episode(data = "series|ep2", episode = 2),
            ),
        )
        val episodes = with(bridge) { response.toEpisodes() }

        assertEquals(2, episodes.size)
        // SEpisode.url IS the provider's opaque data handle (the loadLinks key).
        assertEquals(listOf("series|ep1", "series|ep2"), episodes.map { it.url })
        assertEquals(1f, episodes[0].episode_number, 0.001f)
        assertEquals(2f, episodes[1].episode_number, 0.001f)
    }

    @Test
    fun torrentLoadResponse_singleTorrentEpisodeWithTorrentHandle() = runBlocking {
        val response = provider.newTorrentLoadResponse(
            name = "Some Torrent",
            url = "https://example.com/torrent",
            torrent = "https://example.com/file.torrent",
        )
        val episodes = with(bridge) { response.toEpisodes() }

        assertEquals(1, episodes.size)
        assertEquals("https://example.com/file.torrent", episodes[0].url)
        assertEquals("Torrent", episodes[0].name)
    }

    @Test
    fun torrentLoadResponse_fallsBackToMagnetHandle() = runBlocking {
        val response = provider.newTorrentLoadResponse(
            name = "Some Torrent",
            url = "https://example.com/torrent",
            magnet = "magnet:?xt=urn:btih:abc",
        )
        val episodes = with(bridge) { response.toEpisodes() }

        assertEquals(1, episodes.size)
        assertEquals("magnet:?xt=urn:btih:abc", episodes[0].url)
    }

    // ── Fix E-1 (amended): shared dub data handles are label-neutral ─────────

    @Test
    fun anime_sharedDataHandleAcrossDubTracks_singleLabelNeutralRow() = runBlocking {
        // The provider points Sub AND Dub at the SAME data handle (dual-audio
        // stream) — one honest, label-free row must survive the mapping.
        val response = provider.newAnimeLoadResponse(
            name = "Some Anime",
            url = "https://example.com/anime",
            type = TvType.Anime,
        ) {
            episodes = mutableMapOf(
                DubStatus.Subbed to listOf(Episode(data = "anime|shared", episode = 1)),
                DubStatus.Dubbed to listOf(Episode(data = "anime|shared", episode = 1)),
            )
        }
        val episodes = with(bridge) { response.toEpisodes() }

        assertEquals(1, episodes.size)
        assertEquals("anime|shared", episodes[0].url)
        // Label-neutral: no scanlator, no (Sub)/(Dub) name suffix.
        assertNull(episodes[0].scanlator)
        assertFalse(episodes[0].name.contains("(Sub)"))
        assertFalse(episodes[0].name.contains("(Dub)"))
    }

    @Test
    fun anime_distinctDataHandles_subAndDubLabelsKept() = runBlocking {
        val response = provider.newAnimeLoadResponse(
            name = "Some Anime",
            url = "https://example.com/anime",
            type = TvType.Anime,
        ) {
            episodes = mutableMapOf(
                DubStatus.Subbed to listOf(Episode(data = "anime|sub", episode = 1)),
                DubStatus.Dubbed to listOf(Episode(data = "anime|dub", episode = 1)),
            )
        }
        val episodes = with(bridge) { response.toEpisodes() }

        assertEquals(2, episodes.size)
        assertEquals(listOf("anime|sub", "anime|dub"), episodes.map { it.url })
        assertEquals("Sub", episodes[0].scanlator)
        assertEquals("Dub", episodes[1].scanlator)
        assertTrue(episodes[0].name.endsWith("(Sub)"))
        assertTrue(episodes[1].name.endsWith("(Dub)"))
    }

    // ── Fix E-2: comingSoon is an honest error, never a silent empty list ────

    @Test
    fun comingSoon_withNoEpisodes_throwsHonestError() = runBlocking {
        // The factory flags comingSoon automatically when the episode map is empty.
        val response = provider.newAnimeLoadResponse(
            name = "Not Yet Aired",
            url = "https://example.com/unaired",
            type = TvType.Anime,
        ) {}
        assertTrue(response.comingSoon)

        val error = runCatching {
            with(bridge) { response.episodesOrComingSoon() }
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("coming soon"))
    }

    @Test
    fun comingSoon_withEpisodes_listsNormally() = runBlocking {
        // The flag is advisory — if the provider DID publish episodes, they list.
        val response = provider.newAnimeLoadResponse(
            name = "Some Anime",
            url = "https://example.com/anime",
            type = TvType.Anime,
        ) {
            comingSoon = true
            episodes = mutableMapOf(
                DubStatus.Subbed to listOf(Episode(data = "anime|ep1", episode = 1)),
            )
        }
        val episodes = with(bridge) { response.episodesOrComingSoon() }

        assertEquals(1, episodes.size)
        assertEquals("anime|ep1", episodes[0].url)
    }

    @Test
    fun notComingSoon_emptyList_passesThroughUnchanged() = runBlocking {
        // The helper ONLY interprets comingSoon — a plain empty mapping (e.g. an
        // unmapped LoadResponse type) is not its business.
        val response = provider.newAnimeLoadResponse(
            name = "Some Anime",
            url = "https://example.com/anime",
            type = TvType.Anime,
            comingSoonIfNone = false,
        ) {}
        assertFalse(response.comingSoon)

        val episodes = with(bridge) { response.episodesOrComingSoon() }
        assertTrue(episodes.isEmpty())
    }
}
