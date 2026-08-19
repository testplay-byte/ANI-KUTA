package eu.kanade.tachiyomi.network

import java.io.IOException

/**
 * Thrown by [eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor] when
 * the headless WebView Cloudflare bypass fails (timeout, outdated WebView, or
 * an interactive "Turnstile" challenge that needs a human tap).
 *
 * **Why a dedicated exception type:**
 * Extension HTTP calls surface as raw `IOException`s — without a dedicated type,
 * the UI cannot distinguish "Cloudflare blocked the request" from "network error
 * / generic 403". The user explicitly wants the app to detect Cloudflare, show a
 * button, and let them solve it manually in a WebView.
 *
 * This exception carries the URL that was blocked so the UI / ViewModel that catches
 * it can launch [com.confused.anikuta.webview.CloudflareWebViewActivity] pointed at
 * that URL. The source's `baseUrl` is preferred (set by the caller) — fall back to the
 * exact blocked request URL.
 *
 * It extends [IOException] so the existing `UncaughtExceptionInterceptor` /
 * RxJava `awaitSingle()` plumbing still propagates it unchanged to the ViewModel
 * `catch (e: Throwable)` blocks.
 *
 * @param url the URL that Cloudflare blocked (the request URL the interceptor saw).
 * @param reason a short human-readable reason for the failure (e.g. "timeout",
 *   "webview outdated", "interactive challenge not solved").
 */
class CloudflareException(
    val url: String,
    val reason: String,
) : IOException("Cloudflare bypass failed for $url: $reason") {

    companion object {
        private const val serialVersionUID = 1L
    }
}
