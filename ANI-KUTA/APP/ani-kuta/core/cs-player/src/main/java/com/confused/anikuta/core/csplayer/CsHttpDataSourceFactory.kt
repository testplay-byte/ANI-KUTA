package com.confused.anikuta.core.csplayer

import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Per-link HTTP DataSource factories — the heart of why CS playback does NOT
 * need a proxy (task 52 / round 12; research R12-A §5, upstream
 * CS3IPlayer.createVideoSource):
 *
 *  - the OkHttp client is the CS runtime's base client (injected), so any
 *    provider interceptors/cookies (CloudflareKiller etc.) stay active;
 *  - the User-Agent comes from the LINK headers when set, else the app default;
 *  - referer + headers ride `setDefaultRequestProperties` — OkHttp follows
 *    redirects natively with the headers intact (the old PlaybackCache
 *    header-forwarding 428s cannot happen on this path by construction);
 *  - an optional per-link [Interceptor] (the provider `getVideoInterceptor`
 *    hook) is baked into a derived client.
 */
class CsHttpDataSourceFactory(
    private val baseClient: OkHttpClient,
    private val defaultUserAgent: String,
) {

    /** Factory for a video link's own requests. */
    fun forLink(link: CsVideoLink): HttpDataSource.Factory {
        val client = link.requestInterceptor?.let { interceptor ->
            baseClient.newBuilder().addInterceptor(interceptor).build()
        } ?: baseClient

        return OkHttpDataSource.Factory(client)
            .setUserAgent(link.userAgent ?: defaultUserAgent)
            .setDefaultRequestProperties(link.allHeaders)
    }

    /** Factory for one sidecar subtitle file (its headers may differ from the video's). */
    fun forSubtitle(sub: CsSubtitle): HttpDataSource.Factory =
        OkHttpDataSource.Factory(baseClient)
            .setUserAgent(defaultUserAgent)
            .setDefaultRequestProperties(sub.headers)

    /** Factory for one external audio track. */
    fun forAudioTrack(audio: CsAudioTrack): HttpDataSource.Factory =
        OkHttpDataSource.Factory(baseClient)
            .setUserAgent(defaultUserAgent)
            .setDefaultRequestProperties(audio.headers)
}
