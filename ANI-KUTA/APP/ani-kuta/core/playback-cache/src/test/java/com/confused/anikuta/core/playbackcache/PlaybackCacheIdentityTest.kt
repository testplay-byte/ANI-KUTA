package com.confused.anikuta.core.playbackcache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-764 (round 128 — the CloudStream/playback round): the cache identity
 * fingerprint's locks.
 *
 * The fingerprint must tell a fresh SIGNATURE (the same file — the cache has
 * to keep hitting) apart from a DIFFERENT FILE (a re-encode, a moved path —
 * the cached ranges must be dropped).
 */
class PlaybackCacheIdentityTest {

    @Test
    fun `a blank url has no fingerprint`() {
        assertEquals("", PlaybackCacheIdentity.urlFingerprint(""))
    }

    @Test
    fun `the same url has the same fingerprint`() {
        val url = "https://cdn.example.com/series/ep1.mp4"
        assertEquals(PlaybackCacheIdentity.urlFingerprint(url), PlaybackCacheIdentity.urlFingerprint(url))
    }

    @Test
    fun `a fresh signature keeps the fingerprint`() {
        // The same file with a new signed URL — the cache MUST keep hitting.
        val a = PlaybackCacheIdentity.urlFingerprint("https://cdn.example.com/ep1.mp4?Policy=abc&Signature=one&Key-Pair-Id=K1")
        val b = PlaybackCacheIdentity.urlFingerprint("https://cdn.example.com/ep1.mp4?Policy=xyz&Signature=two&Key-Pair-Id=K1")
        assertEquals(a, b)
    }

    @Test
    fun `a different path is a different fingerprint`() {
        // A re-encode or a moved file — the cached ranges belong to other bytes.
        val a = PlaybackCacheIdentity.urlFingerprint("https://cdn.example.com/ep1.mp4")
        val b = PlaybackCacheIdentity.urlFingerprint("https://cdn.example.com/ep1-v2.mp4")
        assertNotEquals(a, b)
    }

    @Test
    fun `a different host is a different fingerprint`() {
        val a = PlaybackCacheIdentity.urlFingerprint("https://cdn-a.example.com/ep1.mp4")
        val b = PlaybackCacheIdentity.urlFingerprint("https://cdn-b.example.com/ep1.mp4")
        assertNotEquals(a, b)
    }

    @Test
    fun `a stable query parameter is part of the identity`() {
        // A parameter that is NOT a signature says something about the file.
        val a = PlaybackCacheIdentity.urlFingerprint("https://cdn.example.com/ep1.mp4?quality=1080")
        val b = PlaybackCacheIdentity.urlFingerprint("https://cdn.example.com/ep1.mp4?quality=720")
        assertNotEquals(a, b)
    }

    @Test
    fun `the parameter order does not matter`() {
        val a = PlaybackCacheIdentity.urlFingerprint("https://cdn.example.com/ep1.mp4?a=1&b=2")
        val b = PlaybackCacheIdentity.urlFingerprint("https://cdn.example.com/ep1.mp4?b=2&a=1")
        assertEquals(a, b)
    }

    @Test
    fun `the fingerprint is short and hex`() {
        val fp = PlaybackCacheIdentity.urlFingerprint("https://cdn.example.com/ep1.mp4")
        assertEquals(16, fp.length)
        assertTrue(fp.all { it in "0123456789abcdef" })
    }
}
