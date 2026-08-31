package com.confused.anikuta.data.cloudstream.playback

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.csplayer.CsAudioTrack
import com.confused.anikuta.core.csplayer.CsLinkType
import com.confused.anikuta.core.csplayer.CsMediaTypes
import com.confused.anikuta.core.csplayer.CsSubtitle
import com.confused.anikuta.core.csplayer.CsVideoLink
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.network.CloudflareBlockedException
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The CloudStream link resolver (task 52 / round 12) — the loadLinks
 * orchestration, ported from upstream's RepoLinkGenerator + APIRepository
 * (research R12-A §3–§4) and mapped onto the app-side player models.
 *
 * Pipeline: provider.loadLinks(data, isCasting=false, subtitleCallback,
 * linkCallback) → URL-dedup (concurrent set) → TORRENT/MAGNET hidden+counted
 * → DRM flagged unsupported → [CsVideoLink]/[CsSubtitle] snapshots emitted
 * PROGRESSIVELY (the screen starts playback on the first link and keeps
 * collecting for the links sheet — the upstream streaming-into-player UX).
 *
 * Callback contract quirks this encodes (all learned upstream):
 *  - callbacks fire DURING resolution, possibly from multiple coroutines →
 *    thread-safe dedup + snapshot lists;
 *  - duplicate subtitle names get numeric suffixes so the picker stays honest;
 *  - plugin bytecode can throw ANYTHING (NoClassDefFoundError included) →
 *    descriptive error event (the bridge guard() pattern);
 *  - Cancellation + Cloudflare blocks pass through with their own event.
 */
class CloudstreamLinkResolver(
    /** Injectable for tests (the honest 30 s default stays in production). */
    private val firstLinkTimeoutMs: Long = FIRST_LINK_TIMEOUT_MS,
    /**
     * Task 55: HTTP client for subtitle content sniffing (the CS runtime's
     * plugin client by default so provider interceptors/cookies stay active).
     * Never blocks resolution: a failed sniff keeps the extension-based mime.
     */
    private val subSniffClient: () -> OkHttpClient = { com.lagradost.cloudstream3.app.baseClient },
) {
    companion object {
        internal const val TAG = "Anikuta:CS:Resolver"

        /** How long to wait for the FIRST playable link before failing honestly. */
        internal const val FIRST_LINK_TIMEOUT_MS = 30_000L

        /** Total loadLinks budget (upstream APIRepository: 120 s default). */
        internal const val DEFAULT_TOTAL_TIMEOUT_MS = 120_000L

        /** Upstream clamp: 5 s – 480 s. */
        internal const val MIN_TOTAL_TIMEOUT_MS = 5_000L
        internal const val MAX_TOTAL_TIMEOUT_MS = 480_000L

        /** Fresh link-cache window (upstream RepoLinkGenerator's 20 minutes). */
        internal const val CACHE_TTL_MS = 20 * 60 * 1000L

        /** Header values are logged truncated — enough to diagnose, not a URL dump. */
        internal fun formatHeaders(headers: Map<String, String>): String =
            headers.entries.joinToString(",", "\u007b", "\u007d") { "${it.key}=${it.value.take(32)}" }

        /** The provider's total-budget override (upstream MainAPI.loadLinksTimeoutMs), clamped. */
        internal fun totalTimeoutMs(provider: MainAPI): Long {
            val raw = runCatching { provider.loadLinksTimeoutMs }.getOrNull() ?: DEFAULT_TOTAL_TIMEOUT_MS
            return raw.coerceIn(MIN_TOTAL_TIMEOUT_MS, MAX_TOTAL_TIMEOUT_MS)
        }
    }

    /** Progressive resolution snapshots — the screen renders the latest of each. */
    sealed interface CsResolveEvent {
        data class LinksSnapshot(
            val links: List<CsVideoLink>,
            val hiddenTorrentCount: Int,
            val unsupportedDrmCount: Int,
        ) : CsResolveEvent

        data class SubtitlesSnapshot(val subtitles: List<CsSubtitle>) : CsResolveEvent

        /** The provider call returned — resolution saturated. */
        data class Completed(
            val providerSucceeded: Boolean,
            val linkCount: Int,
            val subtitleCount: Int,
            val hiddenTorrentCount: Int,
            val unsupportedDrmCount: Int,
            val durationMs: Long,
        ) : CsResolveEvent

        /** The provider call failed or timed out. [linksSoFar] may still be > 0. */
        data class Failed(
            val message: String,
            val linksSoFar: Int,
            val hiddenTorrentCount: Int,
            val timedOut: Boolean = false,
        ) : CsResolveEvent
    }

    private data class CachedResolution(
        val links: List<CsVideoLink>,
        val subtitles: List<CsSubtitle>,
        val hiddenTorrentCount: Int,
        val unsupportedDrmCount: Int,
        val atMillis: Long,
    )

    /** (providerName, data) → last saturated resolution. \u0000 separator: data strings are opaque. */
    private val cache = ConcurrentHashMap<String, CachedResolution>()

    /**
     * Resolves the playable streams for [data] (the CS episode data handle —
     * SEpisode.url for bridged content). Cold flow; a cache hit completes
     * immediately with the saturated snapshots.
     */
    fun resolve(providerName: String, data: String): Flow<CsResolveEvent> = channelFlow {
        val startedAt = System.currentTimeMillis()

        // ── Cache fast-path (upstream: "Resumed previous loading from Ns ago") ──
        val cacheKey = "$providerName\u0000$data"
        cache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.atMillis < CACHE_TTL_MS) {
                Logger.i(TAG) {
                    "resolve: cache HIT for '$providerName' (${cached.links.size} links, " +
                        "${cached.subtitles.size} subs, age=${(System.currentTimeMillis() - cached.atMillis) / 1000}s)"
                }
                send(CsResolveEvent.LinksSnapshot(cached.links, cached.hiddenTorrentCount, cached.unsupportedDrmCount))
                send(CsResolveEvent.SubtitlesSnapshot(cached.subtitles))
                send(
                    CsResolveEvent.Completed(
                        providerSucceeded = true,
                        linkCount = cached.links.size,
                        subtitleCount = cached.subtitles.size,
                        hiddenTorrentCount = cached.hiddenTorrentCount,
                        unsupportedDrmCount = cached.unsupportedDrmCount,
                        durationMs = System.currentTimeMillis() - startedAt,
                    ),
                )
                return@channelFlow
            }
            Logger.d(TAG) { "resolve: cache stale for '$providerName' — re-resolving" }
        }

        // ── Provider lookup (the bridge/content-repo discipline) ──────────────
        val provider = APIHolder.getApiFromNameNull(providerName)
        if (provider == null) {
            Logger.w(TAG) { "resolve: '$providerName' is not loaded (untrusted, uninstalled, or failed)" }
            send(CsResolveEvent.Failed("CloudStream provider '$providerName' is not loaded — check its plugin in Settings → Extensions", 0, 0))
            return@channelFlow
        }

        Logger.i(TAG) {
            "resolve: START '$providerName' data=${data.take(80)}${if (data.length > 80) "…" else ""} " +
                "timeout=${totalTimeoutMs(provider) / 1000}s"
        }

        val seenLinkUrls = ConcurrentHashMap.newKeySet<String>()
        val seenSubUrls = ConcurrentHashMap.newKeySet<String>()
        val subNameCounts = ConcurrentHashMap<String, AtomicInteger>()

        val links = mutableListOf<CsVideoLink>()
        val subtitles = mutableListOf<CsSubtitle>()
        // R12-REVIEW F6: callbacks can fire from parallel extractor coroutines —
        // plain Ints would lose increments; the sheet footer counts must be exact.
        val hiddenTorrent = AtomicInteger(0)
        val unsupportedDrm = AtomicInteger(0)

        /** Thread-safe append + snapshot emit. */
        fun onLink(link: ExtractorLink) {
            if (link.url.isBlank() || !seenLinkUrls.add(link.url)) {
                Logger.d(TAG) { "link skipped (blank or duplicate): ${link.url.take(64)}" }
                return
            }
            when {
                link is DrmExtractorLink -> {
                    unsupportedDrm.incrementAndGet()
                    Logger.w(TAG) { "DRM link hidden (unsupported): ${link.name} ${link.url.take(64)}" }
                    return
                }
                link.type == ExtractorLinkType.TORRENT || link.type == ExtractorLinkType.MAGNET -> {
                    hiddenTorrent.incrementAndGet()
                    Logger.i(TAG) { "torrent/magnet link hidden: ${link.name} (${link.type})" }
                    return
                }
                else -> Unit
            }
            val type = when (link.type) {
                ExtractorLinkType.VIDEO -> CsLinkType.VIDEO
                ExtractorLinkType.M3U8 -> CsLinkType.M3U8
                ExtractorLinkType.DASH -> CsLinkType.DASH
                else -> return // unreachable after the guard above; keeps the when exhaustive
            }
            // The provider's optional per-link OkHttp interceptor (plugin code —
            // a crash here must never kill resolution).
            val interceptor = runCatching { provider.getVideoInterceptor(link) }.onFailure {
                Logger.w(TAG, it) { "getVideoInterceptor crashed — continuing without it" }
            }.getOrNull()

            val audioTracks = link.audioTracks.map { CsAudioTrack(it.url, it.headers ?: emptyMap()) }
            val mapped = CsVideoLink(
                name = link.name,
                url = link.url,
                quality = link.quality,
                type = type,
                referer = link.referer,
                headers = link.headers,
                source = providerName,
                audioTracks = audioTracks,
                requestInterceptor = interceptor,
            )
            synchronized(links) { links += mapped }
            Logger.i(TAG) {
                "link #${links.size}: ${mapped.displayLabel} type=${mapped.type} " +
                    "referer=${if (mapped.referer.isBlank()) "none" else mapped.referer.take(48)} " +
                    "headers=${formatHeaders(mapped.headers)} url=${mapped.url.take(96)}"
            }
            // Snapshot emission keeps Compose simple (state = latest list).
            trySend(
                CsResolveEvent.LinksSnapshot(
                    synchronized(links) { links.toList() },
                    hiddenTorrent.get(),
                    unsupportedDrm.get(),
                ),
            )
        }

        fun onSubtitle(file: SubtitleFile) {
            val fixedUrl = CsMediaTypes.fixSubtitleUrl(file.url)
            if (fixedUrl.isBlank() || !seenSubUrls.add(fixedUrl)) {
                Logger.d(TAG) { "subtitle skipped (blank or duplicate): ${file.lang} ${fixedUrl.take(64)}" }
                return
            }
            // Upstream RepoLinkGenerator: unique-ify duplicate display names.
            val count = subNameCounts.getOrPut(file.lang) { AtomicInteger(0) }.incrementAndGet()
            val name = if (count > 1) "${file.lang} ($count)" else file.lang
            val langTag = runCatching { file.langTag }.getOrNull()
            // Task 55: content-sniff the real format — extension-based guessing
            // misses extension-less URLs (a VTT parsed as SubRip = zero cues).
            // The callback already runs off the main thread; the sniff is a
            // bounded (4 s, 256-byte) request that NEVER fails the resolution.
            val sniffed = sniffSubtitleMime(fixedUrl, file.headers ?: emptyMap())
            val mapped = CsSubtitle(
                name = name,
                url = fixedUrl,
                headers = file.headers ?: emptyMap(),
                mimeType = CsMediaTypes.subtitleMime(fixedUrl),
                languageTag = langTag,
                id = "$fixedUrl|$name",
                sniffedMime = sniffed,
            )
            synchronized(subtitles) { subtitles += mapped }
            Logger.i(TAG) {
                "subtitle #${subtitles.size}: ${mapped.displayName} mime=${mapped.mimeType}" +
                    (sniffed?.let { " (sniffed: ${it.substringAfterLast('/')})" } ?: "") +
                    " headers=${formatHeaders(mapped.headers)} url=${mapped.url.take(80)}"
            }
            trySend(CsResolveEvent.SubtitlesSnapshot(synchronized(subtitles) { subtitles.toList() }))
        }

        /**
         * Task 55: fetches the first bytes of a subtitle file and returns the
         * CONTENT-detected mime (null = undetectable / fetch failed → keep the
         * extension guess). Bounded: 4 s timeout, 256 bytes read, silent fail.
         */
        private fun sniffSubtitleMime(url: String, headers: Map<String, String>): String? {
            return runCatching {
                val request = Request.Builder().url(url).apply {
                    headers.forEach { (k, v) -> header(k, v) }
                }.build()
                val client = subSniffClient().newBuilder()
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(4, TimeUnit.SECONDS)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body ?: return@runCatching null
                    val source = body.source()
                    val head = source.readUtf8(Math.min(256, body.contentLength().takeIf { it > 0 }?.toInt() ?: 256))
                    CsMediaTypes.sniffSubtitleMime(head)
                }
            }.onFailure {
                Logger.d(TAG) { "sub sniff failed (keeping extension guess): ${url.take(64)} — ${it::class.java.simpleName}" }
            }.getOrNull()
        }

        val providerJob = launch(Dispatchers.IO) {
            val timeoutMs = totalTimeoutMs(provider)
            val returned = try {
                // Task 53 / RC-4: upstream APIRepository wraps loadLinks in
                // withTimeout(api.loadLinksTimeoutMs ?: 120s, clamped 5–480s).
                kotlinx.coroutines.withTimeout(timeoutMs) {
                    provider.loadLinks(
                        data,
                        isCasting = false,
                        subtitleCallback = { onSubtitle(it) },
                        callback = { onLink(it) },
                    )
                }
            } catch (te: kotlinx.coroutines.TimeoutCancellationException) {
                // Total-budget exhaustion — partial links stay usable.
                Logger.w(TAG) {
                    "resolve: TOTAL TIMEOUT after ${timeoutMs / 1000}s — '$providerName' " +
                        "links=${synchronized(links) { links.size }} subs=${synchronized(subtitles) { subtitles.size }}"
                }
                trySend(
                    CsResolveEvent.Failed(
                        "'$providerName' took longer than ${timeoutMs / 1000}s to resolve — " +
                            "try again or pick another source",
                        synchronized(links) { links.size },
                        hiddenTorrent.get(),
                        timedOut = true,
                    ),
                )
                return@launch
            } catch (ce: CancellationException) {
                throw ce
            } catch (cf: CloudflareBlockedException) {
                Logger.w(TAG) { "resolve: Cloudflare blocked '$providerName'" }
                trySend(CsResolveEvent.Failed("Cloudflare blocked '$providerName' — open the site once in the WebView, then retry", synchronized(links) { links.size }, hiddenTorrent.get()))
                return@launch
            } catch (t: Throwable) {
                Logger.e(TAG, t) {
                    "resolve: provider '$providerName' loadLinks failed: ${t::class.java.simpleName}: ${t.message}"
                }
                trySend(
                    CsResolveEvent.Failed(
                        "CloudStream provider '$providerName' error: ${t::class.java.simpleName}: ${t.message}",
                        synchronized(links) { links.size },
                        hiddenTorrent.get(),
                    ),
                )
                return@launch
            }

            val linkCount = synchronized(links) { links.size }
            val subCount = synchronized(subtitles) { subtitles.size }
            if (linkCount > 0) {
                cache[cacheKey] = CachedResolution(
                    links = synchronized(links) { links.toList() },
                    subtitles = synchronized(subtitles) { subtitles.toList() },
                    hiddenTorrentCount = hiddenTorrent.get(),
                    unsupportedDrmCount = unsupportedDrm.get(),
                    atMillis = System.currentTimeMillis(),
                )
            }
            Logger.i(TAG) {
                "resolve: DONE '$providerName' providerReturned=$returned links=$linkCount " +
                    "subs=$subCount hiddenTorrent=${hiddenTorrent.get()} drm=${unsupportedDrm.get()} " +
                    "in ${System.currentTimeMillis() - startedAt}ms (cache=miss)"
            }
            trySend(
                CsResolveEvent.Completed(
                    providerSucceeded = returned,
                    linkCount = linkCount,
                    subtitleCount = subCount,
                    hiddenTorrentCount = hiddenTorrent.get(),
                    unsupportedDrmCount = unsupportedDrm.get(),
                    durationMs = System.currentTimeMillis() - startedAt,
                ),
            )
        }

        // First-link watchdog: honest timeout instead of an eternal spinner.
        val watchdog = launch {
            val timedOut = withTimeoutOrNull(firstLinkTimeoutMs) {
                while (currentCoroutineContext().isActive) {
                    if (synchronized(links) { links.isNotEmpty() }) return@withTimeoutOrNull false
                    delay(250)
                }
                false
            } == null
            if (timedOut && providerJob.isActive && synchronized(links) { links.isEmpty() }) {
                Logger.w(TAG) { "resolve: TIMEOUT after ${firstLinkTimeoutMs / 1000}s — no links from '$providerName'" }
                trySend(
                    CsResolveEvent.Failed(
                        "No playable streams arrived from '$providerName' within ${firstLinkTimeoutMs / 1000}s",
                        0,
                        hiddenTorrent.get(),
                        timedOut = true,
                    ),
                )
                providerJob.cancel()
            }
        }

        // Keep the producer scope alive until the provider call finishes (or the
        // watchdog cancels it), then end the flow cleanly — the screen's
        // Completed/Failed event is the saturation signal.
        providerJob.join()
        watchdog.cancel()
    }.flowOn(Dispatchers.Default)

    /** Drops the cached resolution (the upstream forceClearCache — called when every link failed). */
    fun invalidate(providerName: String, data: String) {
        cache.remove("$providerName\u0000$data")
        Logger.i(TAG) { "cache invalidated for '$providerName'" }
    }
}
