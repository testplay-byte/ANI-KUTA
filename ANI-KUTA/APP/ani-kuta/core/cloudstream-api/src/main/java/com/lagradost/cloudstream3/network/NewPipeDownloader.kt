// CLEAN-ROOM: the NewPipe Downloader contract (org.schabi.newpipe.extractor.downloader)
// is an interop fact of the bundled NewPipeExtractor library — the adapter below is
// entirely original ANI-KUTA code over our own OkHttp stack. No NewPipe source was
// copied. See DOCUMENTATION/cloudstream/23-*.md §3 for the clean-room protocol.
//
// ROUND 98 (D-671) — THE NEWPIPE RUNTIME BRIDGE.
//
// NewPipeExtractor NEVER performs HTTP itself: every network call its extractors
// make funnels through the abstract Downloader the HOST installs via
// NewPipe.init(...). Without an installed downloader, ServiceList.YouTube's
// extractors throw before yielding a single result — and the YoutubeProvider
// plugin family (recloudstream's YoutubeProvider + the loadExtractor-based
// built-in YouTube extractor) would load but resolve nothing.
//
// Upstream recloudstream installs theirs in CommonActivity.init; we install
// ours from [ensureNewPipeInitialized], called by registerBuiltinExtractors()
// (which the CloudStream plugin manager invokes BEFORE any plugin loads —
// the natural ordering point: the runtime exists by the time a provider
// constructs its ServiceList.YouTube reference).
//
// The client is DEDICATED (not the shared pluginHttpClient): YouTube's
// InnerTube API wants a clean cookie jar (consent + visitor data handled by
// the extractor itself), no Cloudflare-bypass machinery, and timeouts tuned
// for metadata fetches rather than page scrapes.
package com.lagradost.cloudstream3.network

import java.util.concurrent.TimeUnit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * The Downloader adapter (see the file header): maps NewPipe's [Request] onto
 * OkHttp and OkHttp's result back into NewPipe's [Response]. Deliberately
 * synchronous — NewPipe's extraction API is blocking by design and the callers
 * (our extractor's getUrl runs on the resolver's IO dispatcher; the plugin's
 * suspend functions wrap it) are already off the main thread.
 */
class NewPipeDownloader(
    private val client: OkHttpClient,
) : Downloader() {

    override fun execute(request: Request): Response {
        val builder = OkHttpRequest.Builder()
            .url(request.url())
            .apply {
                request.headers().forEach { (name, values) ->
                    values.forEach { value -> header(name, value) }
                }
                // NewPipe sends the extractor's own User-Agent per request; a
                // request without one gets the host default so YouTube's API
                // never sees a bare client.
                if (request.headers().keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                    header("User-Agent", com.lagradost.cloudstream3.USER_AGENT)
                }
            }
            .method(
                request.httpMethod(),
                request.dataToSend()?.toRequestBody(null),
            )

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                // NewPipe's contract: a rate-limit page must surface as
                // ReCaptchaException so the extractor layer reports it as the
                // site blocking us, not a parse failure.
                throw ReCaptchaException("Rate limited by ${request.url()}", request.url())
            }
            val body = response.body?.bytes()?.decodeToString() ?: ""
            val headers = response.headers.toMultimap()
                .mapValues { (_, values) -> values.toList() }
            // Java constructor — positional arguments (Kotlin prohibits
            // named args for non-Kotlin functions; run-1 CI lesson).
            return Response(
                response.code,
                response.message,
                headers,
                body,
                response.request.url.toString(),
            )
        }
    }

    companion object {
        /**
         * The shared downloader instance — one OkHttp client, one in-memory
         * cookie jar for the whole process (YouTube sets consent cookies the
         * extractor relies on across calls).
         */
        @Volatile
        private var instance: NewPipeDownloader? = null

        fun getInstance(): NewPipeDownloader =
            instance ?: synchronized(this) {
                instance ?: NewPipeDownloader(buildClient()).also { instance = it }
            }

        private fun buildClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .cookieJar(
                object : CookieJar {
                    private val store = HashMap<String, List<Cookie>>()

                    @Synchronized
                    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                        store[url.host] = (store[url.host].orEmpty() + cookies)
                            .distinctBy { it.name }
                    }

                    @Synchronized
                    override fun loadForRequest(url: HttpUrl): List<Cookie> =
                        store[url.host].orEmpty()
                },
            )
            .build()
    }
}

/** Marks the failure NewPipe throws when it is used before [ensureNewPipeInitialized]. */
private val newPipeInitialized = java.util.concurrent.atomic.AtomicBoolean(false)

/**
 * Installs our [NewPipeDownloader] into the NewPipe runtime — idempotent, and
 * safe to call from any thread (the install itself is a couple of static
 * assignments inside NewPipe; the heavy ServiceList classes load lazily when
 * a provider first touches them, on the load worker).
 */
fun ensureNewPipeInitialized() {
    if (newPipeInitialized.getAndSet(true)) return
    NewPipe.init(NewPipeDownloader.getInstance())
    com.lagradost.api.Log.i(
        "Anikuta:NewPipe",
        "NewPipe runtime initialized (downloader installed; extractor v0.26.3 bundled)",
    )
}
