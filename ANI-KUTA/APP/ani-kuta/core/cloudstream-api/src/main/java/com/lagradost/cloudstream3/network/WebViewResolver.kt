// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// Task 44: CloudflareKiller is now REAL. The device round-3 report showed browse
// and search returning 0 items against Cloudflare-fronted providers (the site
// serves a challenge interstitial to plain OkHttp clients on some networks).
// This interceptor detects challenge responses, solves them in a headless
// WebView (executing the challenge JS like a browser), caches the clearance
// cookies per host, and replays the original request. See doc 06 §3.2 for the
// upstream behavior this mirrors (interceptor + WebView fallback).
@file:Suppress("ktlint")

package com.lagradost.cloudstream3.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.USER_AGENT
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Thrown when a Cloudflare challenge could not be bypassed (WebView unavailable,
 * solve timeout, or the retry was challenged again). Honest-error contract
 * (D-295/D-296): the caller surfaces this instead of silently parsing a
 * challenge page into 0 results.
 */
class CloudflareBlockedException(
    val host: String,
    detail: String,
) : Exception("Cloudflare challenge on $host could not be solved ($detail)") {

    companion object {
        private const val serialVersionUID = 1L
    }

    /** A short, user-facing sentence for error cards (no host jargon overload). */
    val userMessage: String =
        "$host is blocking the app with Cloudflare. Tap Refresh to retry, or " +
            "Open in WebView to solve it in a real browser, then Refresh."
}

/**
 * Resolves WebView-gated requests. Upstream is an expect/actual class; ours is a
 * plain class with the same constructor surface so plugins referencing it load.
 * (Plugins that construct their own WebViewResolver pass it to `app` calls as an
 * interceptor — the pass-through behavior below is correct for that contract:
 * the app-level CloudflareKiller handles challenge solving globally.)
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

/**
 * The app-level Cloudflare bypass interceptor wired into the plugin `app` /
 * `insecureApp` HTTP clients (Task 44).
 *
 * Contract per request:
 *  1. Attach any cached clearance cookies for the host (forcing the solving
 *     User-Agent — Cloudflare binds a clearance cookie to the UA that earned it).
 *  2. Proceed. A clean response passes through untouched.
 *  3. On a challenge response: solve in a headless WebView (the challenge JS
 *     runs, sets `cf_clearance`), cache the domain cookies, replay the request.
 *  4. If solving is impossible (no Context, WebView error) or the replay is
 *     still challenged → [CloudflareBlockedException] (never silently return a
 *     challenge page for a scraper to parse into 0 results).
 *
 * A failed solve is remembered per host for [FAILED_SOLVE_COOLDOWN_MS] so an
 * un-bypassable site fails FAST on subsequent calls instead of hanging for the
 * full WebView timeout on every card.
 *
 * Logging: everything lands under the `Anikuta:Data:Cloudstream:Net` tag with a
 * `cf:` prefix — one logcat filter shows the whole bypass pipeline.
 */
class CloudflareKiller : Interceptor {

    companion object {
        const val TAG = "Anikuta:Data:Cloudstream:Net"

        fun parseCookieMap(cookie: String): Map<String, String> =
            cookie.split(";").mapNotNull {
                val pair = it.split("=", limit = 2)
                if (pair.size == 2) pair[0].trim() to pair[1].trim() else null
            }.toMap()

        /** Headless solve budget — invisible challenges clear in 2–6s; 20s is the slow tail. */
        const val SOLVE_TIMEOUT_MS = 20_000L

        /** How long a FAILED solve is remembered (fast-fail window). */
        const val FAILED_SOLVE_COOLDOWN_MS = 60_000L

        /** Challenge-page body markers (checked in the first chunk of HTML bodies). */
        private val CHALLENGE_MARKERS = listOf(
            "Just a moment",
            "cf-chl",
            "cf_chl", // underscore variant (script vars like cf_chl_opt)
            "challenge-platform",
            "cdn-cgi/challenge-platform",
            "challenges.cloudflare.com",
            "_cf_chl_opt",
            "Checking your browser",
            "cf-browser-verification",
            // JS-disabled interstitials some CF modes serve with HTTP 200:
            "Enable JavaScript and cookies to continue",
            "Please turn JavaScript on",
            "Attention Required! | Cloudflare",
            "Please stand by, while we are checking your browser",
        )

        /**
         * True when the response is a Cloudflare challenge interstitial. Only
         * challenge-shaped statuses are inspected (403/429/503 always; 200 HTML
         * gets a cheap first-chunk scan because some CF modes interstitial with
         * 200). A plain origin 403 that contains no CF markers is NOT a challenge.
         */
        internal fun isChallengeResponse(response: Response): Boolean {
            val code = response.code
            val mitigated = response.header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true
            if (mitigated) return true
            if (code != 403 && code != 429 && code != 503) {
                // Cheap 200-interstitial scan: only HTML, only the first chunk.
                if (code == 200 && response.header("content-type")?.contains("html", ignoreCase = true) == true) {
                    return bodyHasChallengeMarkers(response)
                }
                return false
            }
            // 403/429/503: the header alone is not conclusive — confirm via body.
            return bodyHasChallengeMarkers(response)
        }

        private fun bodyHasChallengeMarkers(response: Response): Boolean {
            return runCatching {
                // 16KB is plenty — challenge markers (<title>Just a moment…,
                // cf-chl scripts) live in <head>, well inside the first chunk.
                val peek = response.peekBody(16 * 1024).string()
                CHALLENGE_MARKERS.any { peek.contains(it, ignoreCase = true) }
            }.getOrDefault(false)
        }
    }

    /** host → clearance cookies earned by the WebView (bound to [USER_AGENT]). */
    private val savedCookies = ConcurrentHashMap<String, Map<String, String>>()

    /** host → last failed-solve epoch ms (fast-fail cooldown). */
    private val failedSolves = ConcurrentHashMap<String, Long>()

    /**
     * host → solve monitor. Sectioned browse fires SEVERAL parallel requests at
     * one provider (one per shelf) — without this lock a challenged host would
     * spawn one headless WebView per request. Solving is serialized per host;
     * later callers reuse the cookies the first solve earned.
     */
    private val solveLocks = ConcurrentHashMap<String, Any>()

    fun getCookieHeaders(url: String): okhttp3.Headers {
        val host = runCatching { url.toHttpUrl().host }.getOrNull() ?: return okhttp3.Headers.Builder().build()
        val cookies = savedCookies[host] ?: emptyMap()
        val builder = okhttp3.Headers.Builder()
        cookies.forEach { (k, v) -> builder.add("cookie", "$k=$v") }
        return builder.build()
    }

    /**
     * Task 45: cookies the SYSTEM WebView CookieManager holds for the host.
     * The manual solver (CloudflareWebViewActivity — "Open in WebView") writes
     * its clearance cookies THERE; without reading them back the plugin client
     * would never see a manual solve. Returns an empty map when WebView is
     * unavailable (some emulator images) — never throws.
     */
    internal fun webViewCookies(host: String): Map<String, String> = runCatching {
        val cm = android.webkit.CookieManager.getInstance()
        val raw = cm.getCookie("https://$host") ?: return@runCatching emptyMap()
        parseCookieMap(raw)
    }.getOrDefault(emptyMap())

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        // 1. Attach cached clearance cookies (with the UA they are bound to).
        //    Task 45: also merge cookies earned by a MANUAL WebView solve (the
        //    system CookieManager) — headless solve + manual solve share one jar.
        val saved = savedCookies[host]
        val manual = webViewCookies(host)
        val merged = (saved ?: emptyMap()) + manual
        val initial = if (merged.isNotEmpty()) withClearance(request, merged) else request
        if (manual.isNotEmpty() && saved == null) {
            android.util.Log.i(TAG, "cf: attaching ${manual.size} manual-WebView cookie(s) for $host")
        }

        val response = chain.proceed(initial)
        if (!isChallengeResponse(response)) return response
        response.close()

        // 2. Challenge. Fast-fail if a solve just failed for this host.
        val lastFail = failedSolves[host]
        if (lastFail != null && System.currentTimeMillis() - lastFail < FAILED_SOLVE_COOLDOWN_MS) {
            android.util.Log.w(TAG, "cf: challenge on $host — solve on cooldown (recent failure), failing fast")
            throw CloudflareBlockedException(host, "recent solve failed, retry later")
        }

        android.util.Log.w(
            TAG,
            "cf: challenge detected on $host (code=${response.code} path=${request.url.encodedPath}) — solving via WebView",
        )

        // Serialize solves per host (parallel shelf fetches reuse the winner).
        val lock = solveLocks.computeIfAbsent(host) { Any() }
        synchronized(lock) {
            // Another request's solve may have landed while this one waited.
            val nowSaved = savedCookies[host]
            if (nowSaved != null) {
                val quickRetry = chain.proceed(withClearance(request, nowSaved))
                if (!isChallengeResponse(quickRetry)) {
                    android.util.Log.i(TAG, "cf: reused concurrent solve on $host")
                    return quickRetry
                }
                quickRetry.close()
            }

            val solved = runCatching { solveViaWebView(request.url) }.getOrElse { t ->
                android.util.Log.w(
                    TAG,
                    "cf: WebView solve error on $host: ${t::class.java.simpleName}: ${t.message}",
                )
                null
            }

            if (solved == null || solved.isEmpty()) {
                failedSolves[host] = System.currentTimeMillis()
                throw CloudflareBlockedException(host, "WebView solve failed")
            }

            // 3. Cache + replay with the fresh cookies.
            savedCookies[host] = solved
            val replayed = chain.proceed(withClearance(request, solved))
            if (isChallengeResponse(replayed)) {
                replayed.close()
                failedSolves[host] = System.currentTimeMillis()
                android.util.Log.w(TAG, "cf: replay still challenged on $host — giving up this round")
                throw CloudflareBlockedException(host, "replay still challenged")
            }

            android.util.Log.i(TAG, "cf: bypassed challenge on $host (${solved.size} cookies cached)")
            return replayed
        }
    }

    /** Request + cached cookies merged over any existing Cookie header, UA pinned to [USER_AGENT]. */
    private fun withClearance(request: Request, cookies: Map<String, String>): Request {
        val existing = request.header("Cookie")?.let { parseCookieMap(it) } ?: emptyMap()
        val merged = (existing + cookies).entries.joinToString("; ") { "${it.key}=${it.value}" }
        return request.newBuilder()
            .header("Cookie", merged)
            .header("User-Agent", USER_AGENT)
            .build()
    }

    // ── Headless WebView solver ────────────────────────────────────────────────

    /**
     * Loads [url] in a headless WebView on the MAIN thread and waits for the
     * challenge to clear (the `cf_clearance` cookie to appear) or the timeout.
     * Returns ALL cookies the WebView holds for the domain — even a partial set
     * can unblock the replay (the replay itself is the final judge).
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun solveViaWebView(url: HttpUrl): Map<String, String>? {
        val context: Context = CloudStreamApp.context
            ?: CommonActivity.activity?.applicationContext
            ?: run {
                android.util.Log.w(TAG, "cf: no Context available for WebView solve")
                return null
            }

        val mainHandler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val cookieUrl = "https://${url.host}"
        var result: Map<String, String>? = null

        val posted = mainHandler.post {
            var webView: WebView? = null
            try {
                // CookieManager.getInstance() lazily initializes the WebView
                // provider on first use — safe to obtain before creating the view.
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)

                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = USER_AGENT // must match the OkHttp UA exactly
                    settings.blockNetworkImage = true
                    settings.loadsImagesAutomatically = false
                    settings.mediaPlaybackRequiresUserGesture = true

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, finishedUrl: String) {
                            val raw = cookieManager.getCookie(cookieUrl)
                            android.util.Log.d(
                                TAG,
                                "cf: WebView page finished ($finishedUrl) cookies=${raw != null}",
                            )
                            if (raw != null && raw.contains("cf_clearance")) {
                                result = parseCookieMap(raw)
                                latch.countDown()
                            }
                            // Without cf_clearance we keep waiting — the challenge
                            // may redirect once more before it lands.
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            // The MAIN frame failing to load means no challenge JS
                            // will ever run — stop early instead of burning the
                            // full timeout.
                            if (request.isForMainFrame) {
                                android.util.Log.w(TAG, "cf: WebView main-frame error: ${error.description}")
                                latch.countDown()
                            }
                        }
                    }
                }

                android.util.Log.i(TAG, "cf: WebView loading $cookieUrl (ua=${USER_AGENT.take(40)}…)")
                webView?.loadUrl(cookieUrl)

                // Watchdog: capture whatever cookies exist at timeout (partial
                // sets sometimes unblock the replay) and release the latch.
                mainHandler.postDelayed(
                    {
                        val raw = runCatching { cookieManager.getCookie(cookieUrl) }.getOrNull()
                        if (result == null && raw != null) result = parseCookieMap(raw)
                        latch.countDown()
                    },
                    SOLVE_TIMEOUT_MS,
                )
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "cf: WebView setup failed: ${t::class.java.simpleName}: ${t.message}")
                latch.countDown()
            } finally {
                // WebView teardown must also happen on the main thread, after the
                // latch released — schedule it generously behind the watchdog.
                mainHandler.postDelayed(
                    {
                        runCatching { webView?.stopLoading() }
                        runCatching { webView?.destroy() }
                    },
                    SOLVE_TIMEOUT_MS + 2_000L,
                )
            }
        }

        if (!posted) {
            android.util.Log.w(TAG, "cf: main-thread post failed")
            return null
        }

        latch.await(SOLVE_TIMEOUT_MS + 5_000L, TimeUnit.MILLISECONDS)
        return result
    }
}
