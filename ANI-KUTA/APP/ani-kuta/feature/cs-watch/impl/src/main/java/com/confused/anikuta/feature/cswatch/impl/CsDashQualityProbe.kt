package com.confused.anikuta.feature.cswatch.impl

import com.confused.anikuta.core.common.DashManifestHeights
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * D-551 — the resolve sheet's DASH quality probe: the NETWORK half of the
 * "show every available resolution" feature (the pure parse half is
 * :core:common's [DashManifestHeights]).
 *
 * For every DASH link the sheet displays, this fetches the manifest ONCE with
 * the LINK'S OWN merged headers and lists the video-representation heights.
 * The link headers are non-negotiable: the MovieBox manifest answers only
 * with the extension's CloudFront-Policy cookie + User-Agent (+ referer) —
 * a generic header-less fetch gets a 403 and the feature silently degrades
 * to the declared-quality-only row (the pre-D-551 presentation).
 *
 * CONTRACT (why this can never hurt the resolve):
 *  - best-effort, silent: ANY failure (transport, non-200, oversized body,
 *    parse, zero heights) returns null and nothing renders;
 *  - bounded: 5 s connect/read, 8 s total, 2 MiB body cap (a manifest is
 *    ~1–2 KiB; the cap only fences a hostile/server-broken response);
 *  - off the resolve path: the sheet calls this from its own IO scope AFTER
 *    the resolver's snapshots are in — the resolver flow itself stays
 *    byte-identical, and a probe can never delay a link's first render.
 *
 * The client is shared across probes (one object-level client, per-request
 * headers) — the sheet probes a handful of manifests per resolve, never a
 * fan-out large enough to need pooling changes.
 */
internal object CsDashQualityProbe {

    /** Body cap — a manifest is tiny; this only fences broken/hostile servers. */
    private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * The video heights [url]'s DASH manifest lists (descending), fetched with
     * [headers] (the link's [CsVideoLink.allHeaders] — cookie/UA/referer).
     * Null on ANY failure (silent by contract).
     */
    fun probe(url: String, headers: Map<String, String>): List<Int>? {
        val request = runCatching {
            Request.Builder()
                .url(url)
                .apply { headers.forEach { (key, value) -> header(key, value) } }
                .build()
        }.getOrNull() ?: return null
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val bytes = body.byteStream().readBounded(MAX_MANIFEST_BYTES) ?: return null
                DashManifestHeights.parse(bytes)
            }
        }.getOrNull()
    }

    /** Bounded read — up to [max] bytes, null when the stream exceeds the cap.
     *  (The response's `use{}` closes the stream — this helper never owns it.) */
    private fun InputStream.readBounded(max: Int): ByteArray? {
        val out = ByteArrayOutputStream(minOf(max, 64 * 1024))
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (total < max) {
            val n = read(chunk, 0, minOf(chunk.size, max - total))
            if (n < 0) return out.toByteArray()
            out.write(chunk, 0, n)
            total += n
        }
        // The cap is hit and the stream still has data — not a manifest.
        return if (read() == -1) out.toByteArray() else null
    }
}
