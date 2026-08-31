package com.confused.anikuta.feature.cswatch.api

import com.confused.anikuta.core.common.EpisodeTitleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 54 (round 14): the CsWatchKey episode-metadata parser — same wire
 * format as the aniyomi WatchKey field ("epNum␟title␟thumb␟date␟desc␟scanlator"
 * per line). Guards: lenient blanks → null, bad lines dropped, blank → empty,
 * and the serialized round-trip (what the details screen builds parses back).
 */
class CsWatchKeyTest {

    private val delim = EpisodeTitleParser.EPISODE_FIELD_DELIMITER

    @Test
    fun `blank metadata string parses to an empty map`() {
        val key = CsWatchKey(providerName = "p", animeTitle = "a", episodeData = "d", episodeNumber = 1f)
        assertTrue(key.parseEpisodeMetadata().isEmpty())
    }

    @Test
    fun `full line parses every field`() {
        val key = CsWatchKey(
            providerName = "p", animeTitle = "a", episodeData = "d", episodeNumber = 1f,
            episodeMetadataSerialized = "5${delim}The Title${delim}https://x/img.jpg${delim}1700000000000${delim}An episode synopsis.${delim}Sub",
        )
        val meta = key.parseEpisodeMetadata()[5]
        assertEquals(5, meta?.episodeNumber)
        assertEquals("The Title", meta?.title)
        assertEquals("https://x/img.jpg", meta?.thumbnailUrl)
        assertEquals(1700000000000L, meta?.airDateMillis)
        assertEquals("An episode synopsis.", meta?.description)
        assertEquals("Sub", meta?.scanlator)
    }

    @Test
    fun `blank fields become null and missing scanlator defaults empty`() {
        val key = CsWatchKey(
            providerName = "p", animeTitle = "a", episodeData = "d", episodeNumber = 1f,
            episodeMetadataSerialized = "3${delim}${delim}${delim}${delim}desc only",
        )
        val meta = key.parseEpisodeMetadata()[3]
        assertNull(meta?.title)
        assertNull(meta?.thumbnailUrl)
        assertEquals(0L, meta?.airDateMillis)
        assertEquals("desc only", meta?.description)
        assertEquals("", meta?.scanlator)
    }

    @Test
    fun `malformed lines are dropped without failing the parse`() {
        val key = CsWatchKey(
            providerName = "p", animeTitle = "a", episodeData = "d", episodeNumber = 1f,
            episodeMetadataSerialized = "not-a-number${delim}t${delim}th${delim}0${delim}d\n2${delim}t2${delim}th2${delim}1${delim}d2${delim}Sub",
        )
        val map = key.parseEpisodeMetadata()
        assertEquals(1, map.size)
        assertEquals("t2", map[2]?.title)
    }

    @Test
    fun `late metadata lines override earlier ones via associateBy`() {
        val key = CsWatchKey(
            providerName = "p", animeTitle = "a", episodeData = "d", episodeNumber = 1f,
            episodeMetadataSerialized =
                "1${delim}first${delim}${delim}0${delim}d\n1${delim}second${delim}${delim}0${delim}d",
        )
        assertEquals("second", key.parseEpisodeMetadata()[1]?.title)
    }

    @Test
    fun `episode list parsing is unaffected by the new field`() {
        val key = CsWatchKey(
            providerName = "p", animeTitle = "a", episodeData = "d", episodeNumber = 1f,
            episodeListSerialized = "data-1${delim}1${delim}First\ndata-2${delim}2${delim}Second",
        )
        assertEquals(2, key.parseEpisodeList().size)
        assertEquals("Second", key.parseEpisodeList()[1].name)
    }
}
