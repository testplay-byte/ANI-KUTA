package eu.kanade.tachiyomi.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * OkHttp [CookieJar] backed by the system WebView [CookieManager] singleton.
 *
 * Ported from the AniMiru/Aniyomi reference
 * (`core/common/.../network/AndroidCookieJar.kt`).
 *
 * **Why CookieManager (and not a custom file-based store):**
 *  - `CookieManager.getInstance()` is the system WebView's persistent cookie
 *    store. Cookies written to it (e.g. `cf_clearance` captured by a WebView
 *    Cloudflare solve) are persisted to disk automatically by the platform +
 *    survive app restarts with zero extra code.
 *  - It is keyed by URL/host (the natural cookie scope), so cookies captured
 *    for site A do not leak to site B. This gives us per-extension isolation
 *    implicitly — each extension's Cloudflare challenge lives under its own host.
 *  - OkHttp automatically calls [loadForRequest] before every request + attaches
 *    a `Cookie:` header, and [saveFromResponse] after every response. So once
 *    this jar is installed on the extension-facing `OkHttpClient`, the
 *    `cf_clearance` cookie is replayed on every subsequent request to that host.
 *
 * **Threading:** all `CookieManager` methods used here (`getInstance`,
 * `setCookie`, `getCookie`, `removeAllCookies`) are safe to call from any
 * thread on API 21+ (per Android docs).
 *
 * CORE_RULES: lives in `:core:source-api` so the extension-facing
 * [NetworkHelper] can install it on its OkHttpClient. The app-facing
 * `:core:network` `HttpClientFactory` does NOT install it (tracker / app-update
 * calls must not pay the WebView coupling cost).
 */
class AndroidCookieJar : CookieJar {

    private val manager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        cookies.forEach { manager.setCookie(urlString, it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = get(url)

    /** @return all cookies the CookieManager currently holds for [url]. */
    fun get(url: HttpUrl): List<Cookie> {
        val cookies = manager.getCookie(url.toString())
        return if (cookies != null && cookies.isNotEmpty()) {
            // CookieManager returns cookies as a single "; "-joined string.
            cookies.split(";").mapNotNull { Cookie.parse(url, it) }
        } else {
            emptyList()
        }
    }

    /**
     * Expires (or removes) cookies for [url].
     *
     * @param cookieNames if non-null, only cookies whose name is in this list
     *   are affected (e.g. `listOf("cf_clearance")`). If null, ALL cookies for
     *   the URL are expired.
     * @param maxAge `0` = expire immediately, `-1` = remove (session cookie).
     * @return the number of cookies affected.
     */
    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int {
        val urlString = url.toString()
        val cookies = manager.getCookie(urlString) ?: return 0

        fun List<String>.filterNames(): List<String> {
            return if (cookieNames != null) {
                this.filter { it in cookieNames }
            } else {
                this
            }
        }

        return cookies.split(";")
            // D-209 FIX: trim leading whitespace — CookieManager.getCookie returns
            // "name1=value1; name2=value2" (semicolon-SPACE separator), so items 2+
            // have a leading space. Without trim, ` cf_clearance` != `cf_clearance`
            // and the filterNames() check silently fails to expire the cookie.
            .map { it.trim().substringBefore("=") }
            .filterNames()
            .onEach { manager.setCookie(urlString, "$it=;Max-Age=$maxAge") }
            .count()
    }

    /** Removes all cookies from the CookieManager (the "Clear cookies" UI action). */
    fun removeAll() {
        manager.removeAllCookies {}
    }
}
