package com.confused.anikuta.core.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client for all ANI-KUTA network operations.
 *
 * Includes a User-Agent interceptor — AniList (and many anime sources) reject
 * requests without a proper browser-like User-Agent header (403 Forbidden).
 * The old project uses the same approach (NetworkHelper.defaultUserAgent).
 */
class HttpClientFactory {

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }

    fun create(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val userAgentInterceptor = Interceptor { chain ->
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

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }
}
