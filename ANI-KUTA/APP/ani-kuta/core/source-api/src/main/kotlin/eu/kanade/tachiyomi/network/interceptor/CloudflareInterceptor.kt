package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.network.CloudflareException
import eu.kanade.tachiyomi.util.system.isOutdated
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.util.concurrent.CountDownLatch

/**
 * OkHttp interceptor that auto-solves Cloudflare "managed challenge" pages by
 * spinning up a headless [WebView] on the main executor + waiting (up to 30s)
 * for the `cf_clearance` cookie to appear in the [AndroidCookieJar].
 *
 * Ported from the AniMiru/Aniyomi reference
 * (`core/common/.../network/interceptor/CloudflareInterceptor.kt`), with
 * D-209 EXPANDED detection criteria.
 *
 * **Detection** ([shouldIntercept]) — D-209 expanded beyond AniMiru's 403/503-only:
 * AniMiru checks: 403/503 + `Server: cloudflare` + body has `#challenge-error-title`/`#challenge-error-text`.
 * The user's logs showed `anilist-res 200 cf_server=Y` — Cloudflare returning 200
 * with a challenge body. AniMiru would MISS this. D-209 adds:
 *  - Status `200` is ALSO checked (Cloudflare "Just a moment" interstitial CAN return 200).
 *  - `cf-mitigated` response header (Cloudflare sets this when it intervenes, even on 200).
 *  - Body markers expanded: `Just a moment`, `cf-turnstile`, `challenge-platform`,
 *    `window._cf_chl_opt` (the JS challenge opt marker — same one AniMiru's
 *    interactive WebViewScreenContent checks).
 *  - `Server` header check broadened to include `cloudflare` (case-insensitive contains).
 *
 * **Solve** ([resolveWithWebView]):
 *  1. Expire the old `cf_clearance` cookie for the request URL.
 *  2. Create a WebView with the request's User-Agent (so cf_clearance stays valid).
 *  3. Load the request URL. The WebView runs Cloudflare's JS challenge.
 *  4. [WebViewClient.onPageFinished] polls the cookie jar — when a NEW
 *     `cf_clearance` appears, the latch is released.
 *  5. After 30s (or success), the WebView is destroyed on the main executor.
 *
 * **On failure:** throws [CloudflareException] carrying the blocked URL. The
 * UI catches this + shows an "Open in WebView" button so the user can solve an
 * interactive (Turnstile checkbox) challenge manually.
 */
class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    private val executor = ContextCompat.getMainExecutor(context)

    override fun shouldIntercept(response: Response): Boolean {
        val code = response.code
        val server = response.header("Server")
        // D-209: check cf-mitigated header — Cloudflare sets this when it
        // intervenes, even on HTTP 200. This is the STRONGEST signal.
        val cfMitigated = response.header("cf-mitigated")
        if (cfMitigated != null) return true

        val isCfServer = server != null && SERVER_CHECK.any { server.equals(it, ignoreCase = true) }

        // D-209: expanded status codes — 200, 403, 503 (NOT 429/502 — those are
        // usually real rate-limits/errors, not CF challenges).
        if (code !in ERROR_CODES || !isCfServer) return false

        // Peek the body (up to 1 MiB) to check for challenge markers.
        return hasChallengeBody(response)
    }

    /**
     * D-209: checks the response body for Cloudflare challenge markers.
     * Uses Jsoup to parse + checks for multiple markers (AniMiru only checked
     * `challenge-error-title` + `challenge-error-text`).
     */
    private fun hasChallengeBody(response: Response): Boolean {
        return try {
            val body = response.peekBody(1024 * 1024).string()
            // Jsoup-parse for element-ID checks (AniMiru's approach).
            val document = Jsoup.parse(body, response.request.url.toString())
            if (document.getElementById("challenge-error-title") != null ||
                document.getElementById("challenge-error-text") != null
            ) {
                return true
            }
            // D-209: string-contains checks for newer CF challenge markers.
            // These catch the "Just a moment" interstitial + Turnstile + the
            // JS challenge opt variable that AniMiru's interactive WebView checks.
            BODY_MARKERS.any { marker -> body.contains(marker, ignoreCase = true) }
        } catch (e: Exception) {
            // Can't read the body → don't intercept (let the caller handle it).
            false
        }
    }

    override fun intercept(
        chain: Interceptor.Chain,
        request: Request,
        response: Response,
    ): Response {
        try {
            response.close()
            // Clear the stale cf_clearance so onPageFinished's "new cookie" check
            // fires on the fresh solve (not the old one).
            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            val oldCookie = cookieManager.get(request.url)
                .firstOrNull { it.name == "cf_clearance" }

            resolveWithWebView(request, oldCookie)

            // Success — retry the original request. The CookieJar (installed on
            // the same OkHttpClient) now attaches the fresh cf_clearance.
            return chain.proceed(request)
        } catch (e: CloudflareBypassException) {
            throw CloudflareException(
                url = request.url.toString(),
                reason = e.reason,
            )
        } catch (e: Exception) {
            // Wrap unexpected exceptions (don't let them crash the OkHttp dispatcher).
            throw CloudflareException(
                url = request.url.toString(),
                reason = "${e::class.java.simpleName}: ${e.message ?: "unknown"}",
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request, oldCookie: Cookie?) {
        val latch = CountDownLatch(1)

        var webview: WebView? = null
        var challengeFound = false
        var cloudflareBypassed = false
        var isWebViewOutdated = false

        val origRequestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)

        executor.execute {
            webview = createWebView(originalRequest)

            webview!!.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    fun isCloudFlareBypassed(): Boolean {
                        return cookieManager.get(origRequestUrl.toHttpUrl())
                            .firstOrNull { it.name == "cf_clearance" }
                            .let { it != null && it != oldCookie }
                    }

                    if (isCloudFlareBypassed()) {
                        cloudflareBypassed = true
                        latch.countDown()
                    }

                    if (url == origRequestUrl && !challengeFound) {
                        // The first request didn't return a challenge — abort.
                        latch.countDown()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true) {
                        // D-209 FIX: explicit null guard — errorResponse?.statusCode
                        // is Int? (safe-call on platform type), but `in List<Int>`
                        // requires non-null Int. Smart-cast via `?.let`.
                        val code = errorResponse?.statusCode
                        if (code != null && code in ERROR_CODES) {
                            challengeFound = true
                        } else {
                            latch.countDown()
                        }
                    }
                }
            }

            webview!!.loadUrl(origRequestUrl, headers)
        }

        latch.awaitFor30Seconds()

        executor.execute {
            if (!cloudflareBypassed) {
                isWebViewOutdated = webview?.isOutdated() == true
            }
            webview?.run {
                stopLoading()
                destroy()
            }
        }

        if (!cloudflareBypassed) {
            val reason = when {
                isWebViewOutdated -> "webview outdated (update Chrome/System WebView to v${MIN_VERSION}+)"
                !challengeFound -> "no challenge detected on page"
                else -> "timeout — interactive challenge may need manual solve"
            }
            throw CloudflareBypassException(reason)
        }
    }

    companion object {
        private const val MIN_VERSION = 118
    }
}

// D-209: expanded — includes 200 (Cloudflare "Just a moment" can return 200 OK).
private val ERROR_CODES = listOf(200, 403, 503)
private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val COOKIE_NAMES = listOf("cf_clearance")

/**
 * D-209: expanded body markers — catches newer Cloudflare challenge types
 * that AniMiru's `challenge-error-title`/`challenge-error-text` check misses.
 */
private val BODY_MARKERS = listOf(
    "Just a moment",      // The "Checking your browser" interstitial title.
    "cf-turnstile",       // Cloudflare Turnstile widget class.
    "challenge-platform", // Challenge script src path.
    "_cf_chl_opt",        // JS challenge opt variable (AniMiru's interactive check).
    "cf_chl_opt",         // Variant without leading underscore.
)

/** Internal signal — translated to a public [CloudflareException] by [CloudflareInterceptor.intercept]. */
private class CloudflareBypassException(val reason: String = "bypass failed") : Exception()
