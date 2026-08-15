package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Base class for OkHttp interceptors that solve a server challenge by spinning
 * up a headless [WebView] on the main executor + blocking the OkHttp calling
 * thread on a [CountDownLatch] until the challenge is solved (or 30s elapses).
 *
 * Ported from the AniMiru/Aniyomi reference
 * (`core/common/.../network/interceptor/WebViewInterceptor.kt`).
 *
 * Adapted for ANI-KUTA:
 *  - Removed `tachiyomi.core.common.util.lang.launchUI` + the Moko
 *    `context.toast(MR.strings.*)` call (we don't have Moko resources).
 *  - Removed the `DeviceUtil.isMiui` / `isSamsung` skip in [initWebView]
 *    (DeviceUtil not ported). Replaced with a simple manufacturer string check.
 *  - The [defaultUserAgentProvider] is taken from the [NetworkHelper] that
 *    constructs the interceptor, ensuring the WebView UA matches the OkHttp UA
 *    (cf_clearance is bound to the UA that solved it).
 *
 * Subclasses (e.g. [CloudflareInterceptor]) implement [shouldIntercept] + the
 * 3-arg [intercept] that actually runs the WebView solver.
 */
abstract class WebViewInterceptor(
    private val context: Context,
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {

    /**
     * Lazily touches WebSettings.getDefaultUserAgentString() to pre-warm the
     * WebView provider on first challenge. Wrapped in a try/catch because some
     * devices crash here while Chrome/WebView is mid-update (rare).
     */
    private val initWebView by lazy {
        val manufacturer = Build.MANUFACTURER?.lowercase(Locale.ENGLISH) ?: ""
        val isMiui = manufacturer == "xiaomi"
        val isSamsung = manufacturer == "samsung"
        if (isMiui || (Build.VERSION.SDK_INT == Build.VERSION_CODES.S && isSamsung)) {
            return@lazy
        }
        try {
            @Suppress("DEPRECATION")
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Exception) {
            // Chrome/WebView being updated — ignore.
        }
    }

    /** @return true if [response] is the challenge the subclass can solve. */
    abstract fun shouldIntercept(response: Response): Boolean

    /** Solve the challenge for [request] (which produced [response]) + return a fresh response. */
    abstract fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!shouldIntercept(response)) {
            return response
        }
        // No WebView on the device → can't solve. Return the original (blocked)
        // response; the subclass's intercept() will then throw a CloudflareException
        // that surfaces to the UI as the "Open in WebView" error card.
        if (!WebViewUtil.supportsWebView(context)) {
            return response
        }
        initWebView
        return intercept(chain, request, response)
    }

    /**
     * Filters OkHttp request headers to the subset Chromium WebView will accept.
     * Forbidden headers (Host, Cookie, Content-Length, Transfer-Encoding, ...)
     * make WebView throw `net::ERR_INVALID_ARGUMENT` — strip them before passing
     * the header map to `WebView.loadUrl(url, headers)`.
     */
    fun parseHeaders(headers: Headers): Map<String, String> {
        return headers
            .filter { (name, value) -> isRequestHeaderSafe(name, value) }
            .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
            .mapValues { it.value.getOrNull(0).orEmpty() }
    }

    /** Blocks the calling (OkHttp dispatcher) thread for up to 30 seconds. */
    fun CountDownLatch.awaitFor30Seconds() {
        await(30, TimeUnit.SECONDS)
    }

    /** Constructs a configured [WebView] whose UA matches the request's User-Agent. */
    fun createWebView(request: Request): WebView {
        return WebView(context).apply {
            setDefaultSettings()
            // Align the WebView UA with the OkHttp request UA so cf_clearance
            // (which is bound to the UA that solved the challenge) stays valid
            // for the retry request.
            settings.userAgentString = request.header("User-Agent") ?: defaultUserAgentProvider()
        }
    }
}

// Based on [IsRequestHeaderSafe] in
// https://source.chromium.org/chromium/chromium/src/+/main:services/network/public/cpp/header_util.cc
private fun isRequestHeaderSafe(_name: String, _value: String): Boolean {
    val name = _name.lowercase(Locale.ENGLISH)
    val value = _value.lowercase(Locale.ENGLISH)
    if (name in unsafeHeaderNames || name.startsWith("proxy-")) return false
    if (name == "connection" && value == "upgrade") return false
    return true
}

private val unsafeHeaderNames = listOf(
    "content-length", "host", "trailer", "te", "upgrade", "cookie2", "keep-alive",
    "transfer-encoding", "set-cookie",
)
