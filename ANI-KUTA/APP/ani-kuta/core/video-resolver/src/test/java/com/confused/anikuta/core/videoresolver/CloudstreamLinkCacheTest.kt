// Task 50 (round 10, Fix F — resolver side): pins the CloudStream 20-minute
// link cache — roundtrip, per-(source, episode) key scoping, TTL expiry with
// lazy removal, empty-put no-op, and invalidate. Pure JVM.
package com.confused.anikuta.core.videoresolver

import eu.kanade.tachiyomi.animesource.model.Video
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CloudstreamLinkCacheTest {

    @Before
    fun setUp() {
        CloudstreamLinkCache.clear()
    }

    @After
    fun tearDown() {
        CloudstreamLinkCache.clear()
    }

    private fun entry(url: String): VideoEntry = VideoEntry(Video(videoUrl = url), null)

    @Test
    fun `put then get roundtrips the cached entries`() {
        val entries = listOf(
            entry("https://a.example/one.m3u8"),
            entry("https://a.example/two.m3u8"),
        )
        CloudstreamLinkCache.put(1L, "/watch/some-anime/ep-1", entries)

        val got = CloudstreamLinkCache.get(1L, "/watch/some-anime/ep-1")
        assertNotNull(got)
        assertEquals(entries, got)
    }

    @Test
    fun `keys are scoped per source id and episode url`() {
        CloudstreamLinkCache.put(1L, "ep-a", listOf(entry("https://a/1")))

        // Same episode, different source → miss.
        assertNull(CloudstreamLinkCache.get(2L, "ep-a"))
        // Same source, different episode → miss.
        assertNull(CloudstreamLinkCache.get(1L, "ep-b"))
        // The right key still hits.
        assertNotNull(CloudstreamLinkCache.get(1L, "ep-a"))
    }

    @Test
    fun `entries expire after the TTL`() {
        CloudstreamLinkCache.put(1L, "ep-a", listOf(entry("https://a/1")))

        // Read with a clock past the TTL (margin absorbs the two real clock
        // reads between put and here).
        val expired = System.currentTimeMillis() + CloudstreamLinkCache.TTL_MS + 5_000
        assertNull(CloudstreamLinkCache.getWithNow(1L, "ep-a", expired))

        // Expired entries are removed on read — a fresh clock misses too.
        assertNull(CloudstreamLinkCache.get(1L, "ep-a"))
    }

    @Test
    fun `entries are still fresh inside the TTL`() {
        CloudstreamLinkCache.put(1L, "ep-a", listOf(entry("https://a/1")))

        // Read with a clock just short of the TTL.
        val inside = System.currentTimeMillis() + CloudstreamLinkCache.TTL_MS - 5_000
        assertNotNull(CloudstreamLinkCache.getWithNow(1L, "ep-a", inside))
    }

    @Test
    fun `putting an empty list is a no-op`() {
        // Upstream parity: the cache is only saturated when ≥ 1 link arrived.
        CloudstreamLinkCache.put(1L, "ep-a", emptyList())
        assertNull(CloudstreamLinkCache.get(1L, "ep-a"))
    }

    @Test
    fun `invalidate drops exactly the targeted entry`() {
        CloudstreamLinkCache.put(1L, "ep-a", listOf(entry("https://a/1")))
        CloudstreamLinkCache.put(1L, "ep-b", listOf(entry("https://b/1")))

        CloudstreamLinkCache.invalidate(1L, "ep-a")

        assertNull(CloudstreamLinkCache.get(1L, "ep-a"))
        assertNotNull(CloudstreamLinkCache.get(1L, "ep-b"))
    }
}
