// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// THIS FILE'S NAME MATTERS: the `app` / `insecureApp` top-level properties compile
// into the `MainActivityKt` facade referenced by 78/80 real plugins (upstream the
// declaration lives in a library file literally named MainActivity.kt).
@file:Suppress("ktlint")

package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.network.CsNetLoggingInterceptor
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import kotlin.reflect.KClass

/**
 * The dual-stack JSON parser handed to `Requests` — kotlinx-serialization first
 * (reflection serializer lookup), Jackson Kotlin fallback (mirrors the documented
 * dual-stack contract, doc 05 §10.1).
 */
@OptIn(InternalAPI::class)
private val jsonResponseParser = object : ResponseParser {
    override fun <T : Any> parse(text: String, kClass: KClass<T>): T =
        with(AppUtils) { parseJson(text, kClass) }

    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? =
        try {
            parse(text, kClass)
        } catch (e: Exception) {
            null
        }

    override fun writeValueAsString(obj: Any): String = with(AppUtils) { obj.toJson() }
}

/**
 * The shared OkHttp base for both plugin clients — the Cloudflare bypass
 * interceptor (Task 44) rides on every plugin request. Solving only happens
 * when a response is an actual challenge page; everything else passes through
 * untouched. `insecureApp` derives its SSL-ignoring client from this same base
 * so the bypass applies there too.
 */
private val pluginHttpClient = okhttp3.OkHttpClient.Builder()
    .addInterceptor(CsNetLoggingInterceptor()) // Task 45: http:/body: diagnostic lines
    .addInterceptor(CloudflareKiller())
    .build()

/**
 * The default networking helper. This helper performs SSL checks.
 * If you need to make requests to websites with invalid SSL certificates use
 * [insecureApp] instead.
 */
var app = Requests(
    baseClient = pluginHttpClient,
    responseParser = jsonResponseParser,
).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

/**
 * Same as the default [app] networking helper, but this instance ignores SSL
 * certificates. This should NEVER be used for sensitive networking operations such
 * as logins. Only use this when required.
 */
@UnsafeSSL
var insecureApp = Requests(
    baseClient = pluginHttpClient,
    responseParser = jsonResponseParser,
).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}
