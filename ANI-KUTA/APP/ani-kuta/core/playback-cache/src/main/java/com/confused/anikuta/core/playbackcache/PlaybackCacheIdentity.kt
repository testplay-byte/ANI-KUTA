package com.confused.anikuta.core.playbackcache

import java.security.MessageDigest

/**
 * D-764 (round 128 — the CloudStream/playback round): the cache IDENTITY
 * fingerprint.
 *
 * ## The hole it closes
 *
 * The cache key is a hash of the identity tuple (content + episode + source +
 * server). The upstream URL is deliberately NOT part of it — a resolve hands
 * out a FRESH signed URL every time, so including the raw URL would make
 * every play a cache miss (the cache would never hit at all).
 *
 * But that leaves a hole: the same identity can later point at DIFFERENT BYTES
 * (the extension re-encoded the file, the CDN moved it, the path changed). The
 * cache then served the OLD ranges for the NEW file — a video that plays the
 * first ten seconds and then glitches into the old encode, or a file that is
 * silently a mixture of two versions.
 *
 * ## The fingerprint
 *
 * A stable hash of the URL's PATH plus the query parameters that are NOT part
 * of a signature. A re-resolve with a new token/signature keeps the same
 * fingerprint (the cache still hits ✓); a genuinely different file gets a
 * different fingerprint (a different entry ✓ — the old bytes are never mixed
 * with the new ones).
 *
 * Pure JVM: the fingerprint is unit-tested.
 */
object PlaybackCacheIdentity {

    /**
     * The query parameters that belong to a SIGNATURE (they change on every
     * resolve and say nothing about WHICH file the URL points at).
     */
    private val VOLATILE_QUERY_KEYS = setOf(
        "sig", "signature", "token", "expires", "expire", "e", "t", "ts", "timestamp",
        "policy", "key-pair-id", "se", "sp", "sv", "kr", "ip", "fm", "md", "hdnts", "_",
    )

    /**
     * A stable fingerprint of [url]: the path + the non-signature query
     * parameters, hashed.
     *
     * @param url the upstream URL (blank = no fingerprint)
     * @return a short hex fingerprint ("" for a blank URL)
     */
    fun urlFingerprint(url: String): String {
        if (url.isBlank()) return ""
        val withoutScheme = url.substringAfter("://", url)
        val path = withoutScheme.substringBefore('?').substringBefore('#')
        val query = withoutScheme.substringAfter('?', "")
            .substringBefore('#')
            .split('&')
            .filter { it.isNotBlank() }
            .mapNotNull { pair ->
                val name = pair.substringBefore('=').trim()
                if (name.lowercase() in VOLATILE_QUERY_KEYS) null else name
            }
            .sorted()
            .joinToString(",")
        val raw = "$path?$query"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
