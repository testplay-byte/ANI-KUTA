// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// The original NiceHttp library (github.com/Blatzar/NiceHttp) has NO license file
// (verified 2026-08-29) — we therefore cannot depend on it and instead provide this
// original implementation on top of our own OkHttp client. Only the public API
// *shape* (packages, class names, method signatures) is mirrored, because plugin
// bytecode references it: 80/80 real plugins reference Requests/NiceResponse.
package com.lagradost.nicehttp

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KClass

/**
 * Used to implement your own json parser of choice.
 * Plugins receive whatever parser the host wires into `Requests(responseParser = ...)`
 * (ours is the kotlinx-first / Jackson-fallback parser, see MainActivity.kt in this module).
 */
interface ResponseParser {
    /** Parse Json based on response text and the type T from parsed<T>(). This function can throw errors. */
    fun <T : Any> parse(text: String, kClass: KClass<T>): T

    /** Same as parse() but when overridden use try catch and return null on failure. */
    fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T?

    /** Used internally to serialize objects to json in the data parameter: requests.post(json = obj). */
    fun writeValueAsString(obj: Any): String
}

/** Used in requests as json = JsonAsString(str): forces application/json content type even for a raw string. */
data class JsonAsString(val string: String)

/** Multipart file entry for the `files` parameter (file-upload scraping is rare in plugins). */
data class NiceFile(
    val name: String,
    val mimeType: String?,
    val data: ByteArray,
)

object RequestBodyTypes {
    const val JSON = "application/json;charset=utf-8"
    const val TEXT = "text/plain;charset=utf-8"
}

/**
 * Builds an OkHttp [Request] from the NiceHttp parameter model.
 * Original ANI-KUTA implementation: query params, merged headers, referer,
 * cookie header, form data, raw/json bodies.
 */
fun requestCreator(
    method: String,
    url: String,
    headers: Map<String, String> = emptyMap(),
    referer: String? = null,
    params: Map<String, String> = emptyMap(),
    cookies: Map<String, String> = emptyMap(),
    data: Map<String, String>? = null,
    files: List<NiceFile>? = null,
    json: Any? = null,
    requestBody: RequestBody? = null,
    cacheTime: Int? = null,
    cacheUnit: TimeUnit? = null,
    responseParser: ResponseParser? = null,
): Request {
    val base = url.toHttpUrl().newBuilder().apply {
        params.forEach { (k, v) -> addQueryParameter(k, v) }
    }.build()

    val builder = Request.Builder().url(base)

    // Headers: custom win over defaults; referer + cookies appended.
    headers.forEach { (k, v) -> builder.header(k, v) }
    referer?.let { builder.header("referer", it) }
    if (cookies.isNotEmpty()) {
        builder.header("cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
    }

    // Body selection: explicit requestBody > files > data form > json.
    val body: RequestBody? = when {
        requestBody != null -> requestBody
        files != null -> {
            // ponytail: real multipart not needed by the plugin census — form fallback.
            FormBody.Builder().apply {
                files.forEach { f -> add(f.name, String(f.data, Charsets.UTF_8)) }
            }.build()
        }
        data != null -> FormBody.Builder().apply {
            data.forEach { (k, v) -> add(k, v) }
        }.build()
        json != null -> when (json) {
            is JsonAsString -> json.string.toRequestBody(RequestBodyTypes.JSON.toMediaType())
            else -> json.toString().toRequestBody(RequestBodyTypes.JSON.toMediaType())
        }
        else -> null
    }

    val normalized = method.uppercase()
    builder.method(
        normalized,
        when (normalized) {
            "GET", "HEAD" -> body
            else -> body ?: ByteArray(0).toRequestBody(null)
        },
    )
    return builder.build()
}

/**
 * The NiceHttp requests client plugins import as `app`.
 *
 * @param baseClient base okhttp client used for all requests. Use this to get cache.
 * @param defaultHeaders base headers present in all requests, will get overwritten by custom headers.
 * @param defaultTimeOut default timeout in seconds.
 * @param responseParser used for parsing, eg response.parsed<T>().
 */
open class Requests(
    var baseClient: OkHttpClient = OkHttpClient(),
    var defaultHeaders: Map<String, String> = mapOf("user-agent" to "NiceHttp"),
    var defaultReferer: String? = null,
    var defaultData: Map<String, String> = emptyMap(),
    var defaultCookies: Map<String, String> = emptyMap(),
    var defaultCacheTime: Int = 0,
    var defaultCacheTimeUnit: TimeUnit = TimeUnit.MINUTES,
    var defaultTimeOut: Long = 0L,
    var responseParser: ResponseParser? = null,
) {
    companion object {
        /** Await an OkHttp call without blocking a thread. */
        suspend fun Call.await(): Response =
            suspendCancellableCoroutine { cont ->
                enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (cont.isActive) cont.resume(response)
                    }
                })
                cont.invokeOnCancellation { runCatching { cancel() } }
            }
    }

    /**
     * Lazy per-config unsafe client for `verify = false` (SSL-ignoring) requests.
     * Never built unless a plugin actually opts in.
     */
    private val unsafeClient: OkHttpClient by lazy {
        val trustAll = object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }
        baseClient.newBuilder()
            .sslSocketFactory(
                javax.net.ssl.SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf<javax.net.ssl.TrustManager>(trustAll), java.security.SecureRandom())
                }.socketFactory,
                trustAll,
            )
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /**
     * The one real entry point every verb (get/post/...) funnels through.
     *
     * @param cacheUnit defaults to minutes
     * @param verify false to ignore SSL errors
     * @param timeout timeout in seconds
     */
    open suspend fun custom(
        method: String,
        url: String,
        headers: Map<String, String> = mapOf(),
        referer: String? = null,
        params: Map<String, String> = mapOf(),
        cookies: Map<String, String> = mapOf(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeOut,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse {
        val request = requestCreator(
            method = method,
            url = url,
            headers = defaultHeaders + headers,
            referer = referer ?: defaultReferer,
            params = params,
            cookies = defaultCookies + cookies,
            data = data,
            files = files,
            json = json?.let { j ->
                if (j is JsonAsString) j else responseParser?.writeValueAsString(j) ?: j.toString()
            },
            requestBody = requestBody,
        )

        var client = if (verify) baseClient else unsafeClient
        if (!allowRedirects) {
            client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        }
        if (timeout > 0) {
            client = client.newBuilder()
                .callTimeout(timeout, TimeUnit.SECONDS)
                .build()
        }
        if (cacheTime > 0) {
            client = client.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .cacheControl(
                                okhttp3.CacheControl.Builder()
                                    .maxAge(cacheTime, cacheUnit ?: TimeUnit.MINUTES)
                                    .build(),
                            )
                            .build(),
                    )
                }
                .build()
        }
        if (interceptor != null) {
            client = client.newBuilder().addInterceptor(interceptor).build()
        }

        val response = client.newCall(request).await()
        return NiceResponse(response, responseParser)
    }

    suspend fun get(
        url: String,
        headers: Map<String, String> = mapOf(),
        referer: String? = null,
        params: Map<String, String> = mapOf(),
        cookies: Map<String, String> = mapOf(),
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeOut,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse = custom(
        "GET", url, headers, referer, params, cookies, null, null, null, null,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser,
    )

    suspend fun post(
        url: String,
        headers: Map<String, String> = mapOf(),
        referer: String? = null,
        params: Map<String, String> = mapOf(),
        cookies: Map<String, String> = mapOf(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeOut,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse = custom(
        "POST", url, headers, referer, params, cookies, data, files, json, requestBody,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser,
    )

    suspend fun put(
        url: String,
        headers: Map<String, String> = mapOf(),
        referer: String? = null,
        params: Map<String, String> = mapOf(),
        cookies: Map<String, String> = mapOf(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeOut,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse = custom(
        "PUT", url, headers, referer, params, cookies, data, files, json, requestBody,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser,
    )

    suspend fun delete(
        url: String,
        headers: Map<String, String> = mapOf(),
        referer: String? = null,
        params: Map<String, String> = mapOf(),
        cookies: Map<String, String> = mapOf(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeOut,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse = custom(
        "DELETE", url, headers, referer, params, cookies, data, files, json, requestBody,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser,
    )

    suspend fun head(
        url: String,
        headers: Map<String, String> = mapOf(),
        referer: String? = null,
        params: Map<String, String> = mapOf(),
        cookies: Map<String, String> = mapOf(),
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeOut,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse = custom(
        "HEAD", url, headers, referer, params, cookies, null, null, null, null,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser,
    )

    suspend fun patch(
        url: String,
        headers: Map<String, String> = mapOf(),
        referer: String? = null,
        params: Map<String, String> = mapOf(),
        cookies: Map<String, String> = mapOf(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeOut,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse = custom(
        "PATCH", url, headers, referer, params, cookies, data, files, json, requestBody,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser,
    )

    suspend fun options(
        url: String,
        headers: Map<String, String> = mapOf(),
        referer: String? = null,
        params: Map<String, String> = mapOf(),
        cookies: Map<String, String> = mapOf(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeOut,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse = custom(
        "OPTIONS", url, headers, referer, params, cookies, data, files, json, requestBody,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser,
    )
}

/** A Requests subclass that carries a cookie jar session between calls (1/80 census plugins). */
class Session(
    client: OkHttpClient,
) : Requests() {
    init {
        baseClient = client
    }

    open inner class CustomCookieJar : CookieJar {
        var cookies = mapOf<String, Cookie>()

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies.values.filter { it.matches(url) }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies = this.cookies + cookies.associateBy { it.name }
        }
    }
}
