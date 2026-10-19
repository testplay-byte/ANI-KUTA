package com.confused.anikuta.feature.animedetails

import com.confused.anikuta.core.navigation.NavKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-748 (round 121 — the navigation identity): the details page's route
 * identity locks.
 *
 * The details page is the live case: the nav shell used to key its
 * `SaveableStateProvider` by the class simple name, so replacing one
 * `AnimeDetailsKey.AniList` with another composed two providers under the key
 * "AniList" (the "Key AniList was used multiple times" crash), and the
 * deep-link guard compared whole data classes — so a deep link to the content
 * the user was already on replaced the page.
 */
class AnimeDetailsKeyIdentityTest {

    @Test
    fun `an AniList page is identified by its anime id`() {
        assertEquals("anilist:4242", AnimeDetailsKey.AniList(4242).routeIdentity)
    }

    @Test
    fun `an AniList page ignores its presentation fields`() {
        val plain = AnimeDetailsKey.AniList(4242)
        val withCover = AnimeDetailsKey.AniList(4242, coverUrl = "https://cdn/cover.jpg")
        val withTitle = AnimeDetailsKey.AniList(4242, title = "Some Title")
        val withTransition = AnimeDetailsKey.AniList(4242, transitionKey = "cover:browse:1")
        val withAutoPlay = AnimeDetailsKey.AniList(4242, autoPlayEpisode = 3)

        assertEquals(plain.routeIdentity, withCover.routeIdentity)
        assertEquals(plain.routeIdentity, withTitle.routeIdentity)
        assertEquals(plain.routeIdentity, withTransition.routeIdentity)
        assertEquals(plain.routeIdentity, withAutoPlay.routeIdentity)
    }

    @Test
    fun `two AniList pages of the same anime are the same route`() {
        assertTrue(
            NavKeys.isSameRoute(
                AnimeDetailsKey.AniList(4242),
                AnimeDetailsKey.AniList(4242, coverUrl = "https://cdn/other.jpg"),
            ),
        )
    }

    @Test
    fun `two AniList pages of different anime are different routes`() {
        assertFalse(NavKeys.isSameRoute(AnimeDetailsKey.AniList(4242), AnimeDetailsKey.AniList(4243)))
    }

    @Test
    fun `an extension page is identified by its source and url`() {
        val key = AnimeDetailsKey.Extension(
            sourceId = 7L,
            animeUrl = "https://ext/series/abc",
            title = "Some Title",
        )
        assertEquals("extension:7:https://ext/series/abc", key.routeIdentity)
    }

    @Test
    fun `an extension page ignores its presentation fields`() {
        val plain = AnimeDetailsKey.Extension(sourceId = 7L, animeUrl = "https://ext/series/abc", title = "T")
        val withThumb = plain.copy(thumbnailUrl = "https://cdn/t.jpg", year = 2019)
        assertEquals(plain.routeIdentity, withThumb.routeIdentity)
        assertTrue(NavKeys.isSameRoute(plain, withThumb))
    }

    @Test
    fun `the same url under two sources is two different routes`() {
        val a = AnimeDetailsKey.Extension(sourceId = 7L, animeUrl = "https://ext/series/abc", title = "T")
        val b = AnimeDetailsKey.Extension(sourceId = 8L, animeUrl = "https://ext/series/abc", title = "T")
        assertFalse(NavKeys.isSameRoute(a, b))
    }

    @Test
    fun `an AniList page and an extension page are different routes`() {
        assertFalse(
            NavKeys.isSameRoute(
                AnimeDetailsKey.AniList(4242),
                AnimeDetailsKey.Extension(sourceId = 7L, animeUrl = "https://ext/series/abc", title = "T"),
            ),
        )
    }

    @Test
    fun `two different details pages get different provider keys`() {
        // The crash guard: the details replacement composes both pages during
        // the transition, so their provider keys must differ.
        assertNotEquals(
            NavKeys.stateKeyOf(AnimeDetailsKey.AniList(4242)),
            NavKeys.stateKeyOf(AnimeDetailsKey.AniList(4243)),
        )
    }

    @Test
    fun `the same details page keeps its provider key`() {
        // The state guard: coming back to the same page restores its state.
        assertEquals(
            NavKeys.stateKeyOf(AnimeDetailsKey.AniList(4242, coverUrl = "https://cdn/cover.jpg")),
            NavKeys.stateKeyOf(AnimeDetailsKey.AniList(4242, coverUrl = "https://cdn/cover.jpg")),
        )
    }
}
