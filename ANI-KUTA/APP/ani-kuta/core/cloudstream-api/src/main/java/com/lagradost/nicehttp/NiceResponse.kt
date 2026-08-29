// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
package com.lagradost.nicehttp

import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.reflect.KClass

/** Cookies from a response's set-cookie headers. */
val Response.cookies: Map<String, String>
    get() {
        val out = mutableMapOf<String, String>()
        headers("set-cookie").forEach { cookieHeader ->
            val pair = cookieHeader.substringBefore(';').split('=', limit = 2)
            if (pair.size == 2) out[pair[0].trim()] = pair[1].trim()
        }
        return out
    }

/** Cookies from a request's Cookie header. */
val Request.cookies: Map<String, String>
    get() {
        val header = header("Cookie") ?: return emptyMap()
        return header.split(';').mapNotNull {
            val pair = it.split('=', limit = 2)
            if (pair.size == 2) pair[0].trim() to pair[1].trim() else null
        }.toMap()
    }

/**
 * Wrapped OkHttp response with the conveniences plugins scrape with:
 * .text / .document (jsoup) / .parsed<T>() / .code / .headers / .cookies / .url.
 */
class NiceResponse(
    val okhttpResponse: Response,
    val parser: ResponseParser?,
) {
    companion object {
        const val MAX_TEXT_SIZE: Long = 5_000_000 // 5 mb

        /** Task 45: the shared CloudStream network-diagnostic logcat tag. */
        internal const val NET_LOG_TAG = "Anikuta:Data:Cloudstream:Net"
    }

    /**
     * Reads (once) and closes the body, enforcing [MAX_TEXT_SIZE] unless [large].
     *
     * TASK 45 ROOT-CAUSE FIX: the previous implementation called
     * `body.source().read(buffer, MAX_TEXT_SIZE)` ONCE — but okio's
     * `Source.read(sink, byteCount)` performs a SINGLE underlying read that
     * returns at most ONE 8KB segment (okio SEGMENT_SIZE). Every body larger
     * than 8KB was silently truncated to its first ~8192 bytes:
     * - HTML pages parsed by jsoup lost everything after <head> → providers
     *   "found" 0 items with no error (AniKoto search/browse, all shelves);
     * - JSON APIs cut mid-token → Jackson JsonEOFException (Anikage, col 8083).
     * The fix loops until the source is exhausted (or the cap is hit).
     */
    private fun readBody(large: Boolean): String {
        val body = okhttpResponse.body ?: return ""
        return try {
            if (large) {
                body.string()
            } else {
                val source = body.source()
                val buffer = okio.Buffer()
                var total = 0L
                var truncatedAtCap = false
                while (total < MAX_TEXT_SIZE) {
                    val read = source.read(buffer, MAX_TEXT_SIZE - total)
                    if (read <= 0L) break // source exhausted (0 or -1)
                    total += read
                    if (total >= MAX_TEXT_SIZE) truncatedAtCap = true
                }
                if (truncatedAtCap) {
                    android.util.Log.w(
                        NET_LOG_TAG,
                        "body: capped read at ${MAX_TEXT_SIZE} bytes for ${okhttpResponse.request.url} " +
                            "(body is larger — use .textLarge/.documentLarge if the plugin needs it all)",
                    )
                }
                buffer.readUtf8()
            }
        } finally {
            body.close()
        }
    }

    private var consumedLarge = false
    private var consumedNormal = false
    private var cachedText: String? = null
    private var cachedTextLarge: String? = null

    /** Lazy, initialized on use. Returns empty string on null. Automatically closes the body! */
    val text: String
        get() {
            if (consumedLarge) return cachedTextLarge ?: ""
            if (!consumedNormal) {
                cachedText = readBody(large = false)
                consumedNormal = true
                logBodyRead(cachedText ?: "", large = false)
            }
            return cachedText ?: ""
        }

    /** Same as .text, but without the MAX_TEXT_SIZE limit. */
    val textLarge: String
        get() {
            if (consumedNormal && !consumedLarge) {
                // Normal read already consumed (possibly truncated) — can't re-read; return what we have.
                return cachedText ?: ""
            }
            if (!consumedLarge) {
                cachedTextLarge = readBody(large = true)
                consumedLarge = true
                logBodyRead(cachedTextLarge ?: "", large = true)
            }
            return cachedTextLarge ?: ""
        }

    val url: String by lazy { okhttpResponse.request.url.toString() }

    val cookies: Map<String, String> by lazy { okhttpResponse.cookies }

    /** Remember to close the body! */
    val body: okhttp3.ResponseBody? by lazy { okhttpResponse.body }

    /** Return code. */
    val code: Int = okhttpResponse.code

    val headers: okhttp3.Headers = okhttpResponse.headers

    /** Size, as reported by Content-Length. */
    val size: Long by lazy { okhttpResponse.headers["content-length"]?.toLongOrNull() ?: -1L }

    val isSuccessful: Boolean = okhttpResponse.isSuccessful

    /** As parsed by Jsoup.parse(text). */
    val document: Document by lazy { Jsoup.parse(text) }

    /** Same as .document, but without the MAX_TEXT_SIZE limit. */
    val documentLarge: Document by lazy { Jsoup.parse(textLarge) }

    /** Same as using parser.parse(text, T::class). */
    inline fun <reified T : Any> parsed(): T {
        val p = parser ?: throw IllegalStateException("No responseParser configured on Requests")
        return p.parse(text, T::class)
    }

    /** Same as using try { parser.parse(text, T::class) }. */
    inline fun <reified T : Any> parsedSafe(): T? {
        val p = parser ?: return null
        return p.parseSafe(text, T::class)
    }

    /** Same as parsed<T>(), but without the MAX_TEXT_SIZE limit. */
    inline fun <reified T : Any> parsedLarge(): T {
        val p = parser ?: throw IllegalStateException("No responseParser configured on Requests")
        return p.parse(textLarge, T::class)
    }

    /** Same as parsedSafe<T>(), but without the MAX_TEXT_SIZE limit. */
    inline fun <reified T : Any> parsedSafeLarge(): T? {
        val p = parser ?: return null
        return p.parseSafe(textLarge, T::class)
    }

    /** Only prints the return body. */
    override fun toString(): String = "NiceResponse(code=$code, url=$url)"

    /**
     * Task 45: one INFO line per body read — the ACTUAL bytes the plugin's
     * scraper/parser will see (status + length + first 90 chars). This is the
     * diagnostic that was missing when providers parsed 0 items: it makes a
     * Cloudflare interstitial, an empty error body, or a truncated payload
     * immediately visible under the `Anikuta:Data:Cloudstream:Net` filter.
     */
    private fun logBodyRead(text: String, large: Boolean) {
        val snippet = text
            .replace('\n', ' ')
            .replace('\r', ' ')
            .take(90)
        android.util.Log.i(
            NET_LOG_TAG,
            "body: read ${text.length} chars (large=$large) code=${okhttpResponse.code} " +
                "url=${okhttpResponse.request.url} first=\"$snippet\"",
        )
    }
}
