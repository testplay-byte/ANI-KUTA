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
import java.util.concurrent.ConcurrentHashMap
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
) {
    companion object {
        internal const val TAG = "Anikuta:CS:Resolver"

        /** How long to wait for the FIRST playable link before failing honestly. */
        internal const val FIRST_LINK_TIMEOUT_MS = 30_000L

        /** Fresh link-cache window (upstream RepoLinkGenerator's 20 minutes). */
        internal const val CACHE_TTL_MS = 20 * 60 * 1000L
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
            "resolve: START '$providerName' data=${data.take(80)}${if (data.length > 80) "…" else ""}"
        }

        val seenLinkUrls = ConcurrentHashMap.newKeySet<String>()
        val seenSubUrls = ConcurrentHashMap.newKeySet<String>()
        val subNameCounts = ConcurrentHashMap<String, AtomicInteger>()

        val links = mutableListOf<CsVideoLink>()
        val subtitles = mutableListOf<CsSubtitle>()
        var hiddenTorrent = 0
        var unsupportedDrm = 0

        /** Thread-safe append + snapshot emit. */
        fun onLink(link: ExtractorLink) {
            if (link.url.isBlank() || !seenLinkUrls.add(link.url)) {
                Logger.d(TAG) { "link skipped (blank or duplicate): ${link.url.take(64)}" }
                return
            }
            when {
                link is DrmExtractorLink -> {
                    unsupportedDrm++
                    Logger.w(TAG) { "DRM link hidden (unsupported): ${link.name} ${link.url.take(64)}" }
                    return
                }
                link.type == ExtractorLinkType.TORRENT || link.type == ExtractorLinkType.MAGNET -> {
                    hiddenTorrent++
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
                    "headers=${mapped.headers.keys} url=${mapped.url.take(96)}"
            }
            // Snapshot emission keeps Compose simple (state = latest list).
            trySend(
                CsResolveEvent.LinksSnapshot(
                    synchronized(links) { links.toList() },
                    hiddenTorrent,
                    unsupportedDrm,
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
            val mapped = CsSubtitle(
                name = name,
                url = fixedUrl,
                headers = file.headers ?: emptyMap(),
                mimeType = CsMediaTypes.subtitleMime(fixedUrl),
                languageTag = langTag,
                id = "$fixedUrl|$name",
            )
            synchronized(subtitles) { subtitles += mapped }
            Logger.i(TAG) { "subtitle #${subtitles.size}: ${mapped.name} mime=${mapped.mimeType} url=${mapped.url.take(80)}" }
            trySend(CsResolveEvent.SubtitlesSnapshot(synchronized(subtitles) { subtitles.toList() }))
        }

        val providerJob = launch(Dispatchers.IO) {
            val returned = try {
                provider.loadLinks(
                    data,
                    isCasting = false,
                    subtitleCallback = { onSubtitle(it) },
                    callback = { onLink(it) },
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (cf: CloudflareBlockedException) {
                Logger.w(TAG) { "resolve: Cloudflare blocked '$providerName'" }
                trySend(CsResolveEvent.Failed("Cloudflare blocked '$providerName' — open the site once in the WebView, then retry", synchronized(links) { links.size }, hiddenTorrent))
                return@launch
            } catch (t: Throwable) {
                Logger.e(TAG, t) {
                    "resolve: provider '$providerName' loadLinks failed: ${t::class.java.simpleName}: ${t.message}"
                }
                trySend(
                    CsResolveEvent.Failed(
                        "CloudStream provider '$providerName' error: ${t::class.java.simpleName}: ${t.message}",
                        synchronized(links) { links.size },
                        hiddenTorrent,
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
                    hiddenTorrentCount = hiddenTorrent,
                    unsupportedDrmCount = unsupportedDrm,
                    atMillis = System.currentTimeMillis(),
                )
            }
            Logger.i(TAG) {
                "resolve: DONE '$providerName' providerReturned=$returned links=$linkCount " +
                    "subs=$subCount hiddenTorrent=$hiddenTorrent drm=$unsupportedDrm " +
                    "in ${System.currentTimeMillis() - startedAt}ms"
            }
            trySend(
                CsResolveEvent.Completed(
                    providerSucceeded = returned,
                    linkCount = linkCount,
                    subtitleCount = subCount,
                    hiddenTorrentCount = hiddenTorrent,
                    unsupportedDrmCount = unsupportedDrm,
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
                        hiddenTorrent,
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
