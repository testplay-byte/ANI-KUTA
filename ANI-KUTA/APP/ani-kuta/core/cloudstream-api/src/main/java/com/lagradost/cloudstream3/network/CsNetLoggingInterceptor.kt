// CLEAN-ROOM: original ANI-KUTA implementation (no CloudStream code copied).
//
// Task 45 (device round 4): per-request diagnostic logging for the plugin HTTP
// client. The round-4 report showed search returning "0 item(s)" with NO way to
// tell what the network layer actually delivered — this interceptor puts one
// request line + one response line (status, content-type, content-length,
// duration) under the `Anikuta:Data:Cloudstream:Net` tag, alongside the
// CloudflareKiller's `cf:` lines and NiceResponse's `body:` lines. One logcat
// filter on the tag now shows the entire plugin network pipeline:
//
//   http: → GET https://anikototv.to/?s=oshi
//   http: ← 200 text/html; charset=UTF-8 len=64231 (218ms) host=anikototv.to
//   body: read 63980 chars (large=false) code=200 url=… first="<!DOCTYPE html>…"
//   search: AniKoto query='oshi' -> 10 item(s) …
package com.lagradost.cloudstream3.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Application interceptor on the shared plugin client (`app` / `insecureApp`):
 * logs every request + response. Non-2xx responses also get a WARN line with a
 * short body snippet — an error page (Cloudflare interstitial, 403 HTML, JSON
 * error payload) is the FIRST thing needed when a provider misbehaves.
 */
class CsNetLoggingInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val started = System.currentTimeMillis()
        com.lagradost.api.Log.i(TAG, "http: → ${request.method} ${request.url}")
        return try {
            val response = chain.proceed(request)
            val took = System.currentTimeMillis() - started
            val contentType = response.header("content-type") ?: "?"
            val declaredLen = response.header("content-length") ?: "?"
            com.lagradost.api.Log.i(
                TAG,
                "http: ← ${response.code} $contentType len=$declaredLen (${took}ms) " +
                    "host=${response.request.url.host}",
            )
            if (!response.isSuccessful) {
                // Error bodies are the #1 diagnostic — peek (does NOT consume).
                runCatching {
                    val snippet = response.peekBody(2048).string()
                        .replace('\n', ' ')
                        .replace('\r', ' ')
                        .take(160)
                    com.lagradost.api.Log.w(
                        TAG,
                        "http: error body snippet code=${response.code}: \"$snippet\"",
                    )
                }
            }
            response
        } catch (t: Throwable) {
            com.lagradost.api.Log.w(
                TAG,
                "http: ✗ ${request.method} ${request.url} FAILED " +
                    "in ${System.currentTimeMillis() - started}ms: " +
                    "${t::class.java.simpleName}: ${t.message}",
            )
            throw t
        }
    }

    companion object {
        internal const val TAG = "Anikuta:Data:Cloudstream:Net"
    }
}
