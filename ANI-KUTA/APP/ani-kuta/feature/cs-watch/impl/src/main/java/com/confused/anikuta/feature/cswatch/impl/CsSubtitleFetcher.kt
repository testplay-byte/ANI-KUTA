package com.confused.anikuta.feature.cswatch.impl

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.csplayer.CsCue
import com.confused.anikuta.core.csplayer.CsPlayerDefaults
import com.confused.anikuta.core.csplayer.CsSubtitle
import com.confused.anikuta.core.csplayer.CsSubtitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Task 57 (round 17 — the overlay subtitle system): fetches ONE provider
 * subtitle file over OkHttp and parses it into renderable cues.
 *
 * The v0.4.4 device round rejected the engine-side subtitle attachment ("there
 * is actually no need for the whole video to reload after adding subtitles…
 * use our own subtitle system, overlay on top"): provider subs now fetch+parse
 * HERE (bounded, headers-aware, silent-fail typed) and render through the
 * Compose overlay — the engine never re-prepares for a subtitle again.
 *
 * Design notes:
 *  - the playback OkHttp client (the CS runtime's plugin client) is injected so
 *    provider interceptors/cookies stay active, with PER-FETCH call timeouts
 *    derived from it (the shared client's own timeouts may be unbounded);
 *  - the sub's own headers ride the request (+ a desktop-Chrome UA when the
 *    provider did not set one — the same default the video requests use);
 *  - the resolver's 256-byte content sniff ([CsSubtitle.sniffedMime]) wins over
 *    the extension guess when picking the parser;
 *  - a 4 MB body cap guards against misbehaving CDNs serving video bytes;
 *  - every failure is a TYPED [FetchOutcome.Failed] with a short human reason —
 *    the subtitles sheet shows it on the row, the log carries the details.
 */
internal class CsSubtitleFetcher(
    private val client: OkHttpClient,
    private val defaultUserAgent: String = CsPlayerDefaults.USER_AGENT,
) {

    sealed interface FetchOutcome {
        /** The cues, ready to render (empty when the file legitimately has none). */
        data class Ready(val cues: List<CsCue>) : FetchOutcome

        /** Fetch or parse failed — [reason] is a short, sheet-displayable line. */
        data class Failed(val reason: String) : FetchOutcome
    }

    /** Fetches + parses [sub]; never throws (all failures map to [FetchOutcome.Failed]). */
    suspend fun fetch(sub: CsSubtitle): FetchOutcome = withContext(Dispatchers.IO) {
        runCatching {
            val timedClient = client.newBuilder()
                .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()

            val request = Request.Builder().url(sub.url).apply {
                sub.headers.forEach { (k, v) -> header(k, v) }
                if (sub.headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                    header("User-Agent", defaultUserAgent)
                }
            }.build()

            timedClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.w(SUBS_TAG) {
                        "subtitle fetch failed: HTTP ${response.code} for '${sub.displayName}' (${sub.url.take(72)})"
                    }
                    return@runCatching FetchOutcome.Failed("HTTP ${response.code}")
                }
                val body = response.body ?: return@runCatching FetchOutcome.Failed("empty response")
                val declared = body.contentLength()
                if (declared > MAX_BODY_BYTES) {
                    Logger.w(SUBS_TAG) { "subtitle skipped: content-length $declared exceeds cap (${sub.displayName})" }
                    return@runCatching FetchOutcome.Failed("file too large")
                }
                val text = body.string()
                if (text.toByteArray().size > MAX_BODY_BYTES) {
                    return@runCatching FetchOutcome.Failed("file too large")
                }
                when (val parsed = CsSubtitleParser.parse(sub.sniffedMime ?: sub.mimeType, text)) {
                    is CsSubtitleParser.ParseOutcome.Ok -> {
                        Logger.i(SUBS_TAG) {
                            "subtitle loaded: '${sub.displayName}' cues=${parsed.cues.size} " +
                                "mime=${(sub.sniffedMime ?: sub.mimeType).substringAfterLast('/')} bytes=${text.length}"
                        }
                        FetchOutcome.Ready(parsed.cues)
                    }
                    is CsSubtitleParser.ParseOutcome.Unsupported -> {
                        Logger.w(SUBS_TAG) {
                            "subtitle unparsable: '${sub.displayName}' — ${parsed.reason} " +
                                "(mime=${sub.sniffedMime ?: sub.mimeType}, head=${text.take(48).replace('\n', ' ')})"
                        }
                        FetchOutcome.Failed(parsed.reason)
                    }
                }
            }
        }.getOrElse { t ->
            Logger.w(SUBS_TAG, t) { "subtitle fetch error: '${t.message}' (${sub.displayName})" }
            FetchOutcome.Failed(t.message?.take(64) ?: t::class.java.simpleName)
        }
    }

    companion object {
        private const val SUBS_TAG = "Anikuta:CS:Subs"

        /** The whole fetch (connect + read + body) may never exceed this. */
        private const val CALL_TIMEOUT_MS = 15_000L

        private const val CONNECT_TIMEOUT_MS = 8_000L

        private const val READ_TIMEOUT_MS = 15_000L

        /** Guard against CDNs serving video bytes where a subtitle file belongs. */
        private const val MAX_BODY_BYTES = 4L * 1024L * 1024L
    }
}
