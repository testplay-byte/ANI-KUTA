package com.confused.anikuta.core.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.qualifier.named
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client factory for all ANI-KUTA network operations.
 *
 * Includes a User-Agent interceptor — AniList (and many anime sources) reject
 * requests without a proper browser-like User-Agent header (403 Forbidden).
 * The old project uses the same approach (NetworkHelper.defaultUserAgent).
 *
 * D.0.4: Two factory methods — [create] (default, 30s timeouts, with logging)
 * + [createDownloadClient] (60s read/write, no logging, separate pool).
 * The download client prevents a stuck download from starving extension HTTP
 * calls (each OkHttpClient has its own connection pool + dispatcher).
 */
class HttpClientFactory {

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        /**
         * Koin qualifier for the download HTTP client.
         * Usage: `single<OkHttpClient>(HttpClientFactory.DOWNLOAD) { ... }`
         * + `get<OkHttpClient>(HttpClientFactory.DOWNLOAD)`.
         */
        val DOWNLOAD = named("download")
    }

    /** The default client — extension + AniList calls. 30s timeouts, with logging. */
    fun create(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor())
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * The download client — long timeouts (60s read/write for large files on
     * slow CDNs), separate connection pool, no logging interceptor.
     */
    fun createDownloadClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor())
            // No logging interceptor — downloads are high-volume.
            .build()
    }

    private fun userAgentInterceptor(): Interceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val request = if (originalRequest.header("User-Agent").isNullOrEmpty()) {
            originalRequest.newBuilder()
                .removeHeader("User-Agent")
                .addHeader("User-Agent", DEFAULT_USER_AGENT)
                .build()
        } else {
            originalRequest
        }
        chain.proceed(request)
    }
}
