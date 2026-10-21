package com.confused.anikuta.feature.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeTest {

    private fun episode(number: Float) = SimpleEpisode(
        url = "https://example.test/episode-$number",
        episodeNumber = number,
        name = "Episode $number",
    )

    @Test
    fun `a descending list still advances from ten to eleven`() {
        val list = listOf(
            episode(12f),
            episode(11f),
            episode(10f),
            episode(9f),
        )

        val next = list.nextEpisodeAfter("https://example.test/episode-10.0")

        assertEquals(11f, next?.episodeNumber)
    }

    @Test
    fun `an ascending list advances to the next number`() {
        val list = listOf(episode(9f), episode(10f), episode(11f))

        val next = list.nextEpisodeAfter("https://example.test/episode-10.0")

        assertEquals(11f, next?.episodeNumber)
    }

    @Test
    fun `the last episode has no successor`() {
        val list = listOf(episode(11f), episode(12f))

        assertNull(list.nextEpisodeAfter("https://example.test/episode-12.0"))
    }

    @Test
    fun `an unknown current episode has no successor`() {
        assertNull(listOf(episode(1f)).nextEpisodeAfter("https://example.test/missing"))
    }

    @Test
    fun `specials fall back to raw list order`() {
        val list = listOf(episode(0f), episode(0.5f))

        val next = list.nextEpisodeAfter("https://example.test/episode-0.0")

        assertEquals(0.5f, next?.episodeNumber)
    }
}
