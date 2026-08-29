// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// SKELETONS (doc 23 §4): WebView-based challenge solving executes only during
// extraction (5/80 and 3/80 census plugins). The playback session implements the
// real headless-WebView resolver (doc 19 §3.2).
@file:Suppress("ktlint")

package com.lagradost.cloudstream3.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import com.lagradost.cloudstream3.USER_AGENT

/**
 * Resolves WebView-gated requests. Upstream is an expect/actual class; ours is a
 * plain class with the same constructor surface so plugins referencing it load.
 */
class WebViewResolver(
    interceptUrl: Regex,
    additionalUrls: List<Regex> = emptyList(),
    userAgent: String? = USER_AGENT,
    useOkhttp: Boolean = true,
    script: String? = null,
    scriptCallback: ((String) -> Unit)? = null,
    timeout: Long = DEFAULT_TIMEOUT,
) : Interceptor {

    companion object {
        val DEFAULT_TIMEOUT: Long = 60_000L
        var webViewUserAgent: String? = null
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        // ponytail: headless-WebView solving arrives with the playback session.
        return chain.proceed(chain.request())
    }

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        method: String = "GET",
        requestCallBack: (Request) -> Boolean = { false },
    ): Pair<Request?, List<Request>> {
        throw NotImplementedError("WebViewResolver is not implemented in this build yet (playback session)")
    }

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
        method: String = "GET",
        requestCallBack: (Request) -> Boolean = { false },
    ): Pair<Request?, List<Request>> {
        throw NotImplementedError("WebViewResolver is not implemented in this build yet (playback session)")
    }

    suspend fun resolveUsingWebView(
        request: Request,
        requestCallBack: (Request) -> Boolean = { false },
    ): Pair<Request?, List<Request>> {
        throw NotImplementedError("WebViewResolver is not implemented in this build yet (playback session)")
    }
}

/** Cloudflare bypass interceptor (skeleton — playback session implements). */
class CloudflareKiller : Interceptor {

    companion object {
        const val TAG = "CloudflareKiller"

        fun parseCookieMap(cookie: String): Map<String, String> =
            cookie.split(";").mapNotNull {
                val pair = it.split("=", limit = 2)
                if (pair.size == 2) pair[0].trim() to pair[1].trim() else null
            }.toMap()
    }

    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    fun getCookieHeaders(url: String): okhttp3.Headers {
        val cookies = savedCookies[url] ?: emptyMap()
        val builder = okhttp3.Headers.Builder()
        cookies.forEach { (k, v) -> builder.add("cookie", "$k=$v") }
        return builder.build()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        // ponytail: CF bypass arrives with the playback session; pass through for now.
        return chain.proceed(chain.request())
    }
}
