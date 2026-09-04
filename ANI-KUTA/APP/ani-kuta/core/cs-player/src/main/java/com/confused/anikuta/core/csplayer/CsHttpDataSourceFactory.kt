package com.confused.anikuta.core.csplayer

import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Per-link HTTP DataSource factories (task 52 / round 12; upstream
 * CS3IPlayer.createVideoSource semantics, verified again in task 53):
 *
 *  - the OkHttp client is the CS runtime's base client (injected), so any
 *    provider interceptors/cookies (CloudflareKiller etc.) stay active;
 *  - ATTEMPT 1 (upstream parity): the User-Agent comes from the LINK headers
 *    when the provider set one, else the injected default (desktop Chrome —
 *    upstream's USER_AGENT); referer + headers ride
 *    `setDefaultRequestProperties`; OkHttp follows redirects natively;
 *  - ATTEMPT 2 ("clean", task 53 / RC-2): some CDNs (empirically
 *    hcdn3.hakunaymatata.com: any browser UA → 428, any Referer → 429,
 *    plain client UA + no referer → 206) reject the upstream profile. The
 *    engine retries once with [forLinkClean]: NO User-Agent override (OkHttp
 *    default) and the Referer dropped, other link headers kept;
 *  - an optional per-link [Interceptor] (the provider `getVideoInterceptor`
 *    hook) is baked into a derived client.
 */
class CsHttpDataSourceFactory(
    private val baseClient: OkHttpClient,
    private val defaultUserAgent: String,
) {

    /** Factory for a video link's own requests — upstream header semantics. */
    fun forLink(link: CsVideoLink): HttpDataSource.Factory {
        val client = link.requestInterceptor?.let { interceptor ->
            baseClient.newBuilder().addInterceptor(interceptor).build()
        } ?: baseClient

        return OkHttpDataSource.Factory(client)
            .setUserAgent(link.userAgent ?: defaultUserAgent)
            .setDefaultRequestProperties(link.allHeaders)
    }

    /**
     * Retry profile for CDNs that reject browser UAs / referers (RC-2):
     * no User-Agent override (the client's own default rides) and the
     * Referer dropped; other provider headers (cookies etc.) survive.
     */
    fun forLinkClean(link: CsVideoLink): HttpDataSource.Factory {
        val client = link.requestInterceptor?.let { interceptor ->
            baseClient.newBuilder().addInterceptor(interceptor).build()
        } ?: baseClient

        val cleanedHeaders = link.allHeaders.filterKeys { key ->
            !key.equals("referer", ignoreCase = true)
        }.toMap()
        return OkHttpDataSource.Factory(client)
            .setDefaultRequestProperties(cleanedHeaders)
    }

    /** Factory for one external audio track.
     *  (Task 57: sidecar subtitles left the engine — they render through the
     *  screen's overlay pipeline now, so there is no subtitle DataSource here.) */
    fun forAudioTrack(audio: CsAudioTrack): HttpDataSource.Factory =
        OkHttpDataSource.Factory(baseClient)
            .setUserAgent(defaultUserAgent)
            .setDefaultRequestProperties(audio.headers)
}
