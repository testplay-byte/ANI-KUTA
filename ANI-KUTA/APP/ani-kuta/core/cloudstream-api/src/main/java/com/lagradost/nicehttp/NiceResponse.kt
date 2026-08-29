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
    }

    /** Reads (once) and closes the body, enforcing [MAX_TEXT_SIZE] unless [large]. */
    private fun readBody(large: Boolean): String {
        val body = okhttpResponse.body ?: return ""
        return try {
            if (large) {
                body.string()
            } else {
                // okio Buffer.read reads AT MOST byteCount (unlike readByteString which
                // requires exactly) — the correct capped-read primitive.
                val buffer = okio.Buffer()
                body.source().read(buffer, MAX_TEXT_SIZE)
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
}
