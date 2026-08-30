// Task 45 (device round 4): the CloudStream → Aniyomi SOURCE BRIDGE.
//
// The user's directive: CloudStream results must open the EXACT SAME details
// screen as aniyomi extensions — "no custom new details page". The standard
// details screen is source-interface driven (DetailsViewModel resolves the
// source by Long id from ExtensionManager and calls getAnimeDetails /
// getEpisodeList), so instead of a parallel UI we bridge every trusted
// CloudStream provider as an [AnimeHttpSource] and register it into the
// ExtensionManager source map under a STABLE synthetic id. Details, episode
// lists, the save button, back stack, tags, background image, AniList
// auto-linking — everything flows through the untouched aniyomi code paths.
//
// Identity contract: id = CS_SOURCE_ID_FLAG | (hash(providerName) & 0xffffffff).
// The flag bit is never set by aniyomi's MD5 ids (sign bit cleared, high bits
// effectively random — the flag makes collisions impossible in practice) and
// the id is deterministic across restarts, so library saves, watch history and
// AniList links keyed on (sourceId, animeUrl) survive app restarts.
package com.confused.anikuta.data.cloudstream.content

import com.confused.anikuta.core.common.Logger
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.MpdParser
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentLinkedQueue

/** Stable synthetic source ids for bridged CloudStream providers. */
object CsSourceIds {

    /** Bit 62 — never set by aniyomi's MD5 ids, marks a bridged CS source. */
    internal const val CS_SOURCE_ID_FLAG: Long = 0x4000_0000_0000_0000L

    /** True when [id] was minted by [idFor] (a bridged CloudStream source). */
    fun isCloudstreamId(id: Long): Boolean = id and CS_SOURCE_ID_FLAG != 0L

    /** Deterministic id for a provider name — stable across restarts/installs. */
    fun idFor(providerName: String): Long =
        CS_SOURCE_ID_FLAG or (providerName.hashCode().toLong() and 0xFFFF_FFFFL)
}

/**
 * One CloudStream [MainAPI] provider exposed as an aniyomi [AnimeHttpSource].
 *
 * The provider is resolved BY NAME on every call (`APIHolder.getApiFromNameNull`)
 * — a bridge instance survives plugin updates/reloads because it never caches
 * the live provider object. All suspend entry points map CloudStream responses
 * into the SAnime/SEpisode models the details screen consumes.
 *
 * HTTP plumbing (client/headers/request builders) is never touched — the CS
 * plugin's own networking (nicehttp `app` + CloudflareKiller) does the actual
 * fetching; the AnimeHttpSource surface exists so every aniyomi code path that
 * casts to AnimeHttpSource (video resolver, WebView episode URL, …) resolves.
 */
class CloudstreamAnimeSourceBridge(
    val providerName: String,
) : AnimeHttpSource() {

    private fun liveProvider(): MainAPI = APIHolder.getApiFromNameNull(providerName)
        ?: throw IllegalStateException(
            "CloudStream provider '$providerName' is not loaded — check Settings → Extensions",
        )

    /**
     * Plugin bytecode can throw ANYTHING (binary-compat NoClassDefFoundError,
     * NoSuchMethodError, …). The aniyomi flow's catch sites catch Exception —
     * so Errors are converted here into a descriptive IllegalStateException
     * (cause chained). Cancellation + Cloudflare blocks pass through untouched.
     */
    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (ce: kotlinx.coroutines.CancellationException) {
        throw ce
    } catch (cf: com.lagradost.cloudstream3.network.CloudflareBlockedException) {
        throw cf
    } catch (t: Throwable) {
        Logger.e(TAG, t) { "bridge: '$providerName' provider call failed" }
        throw IllegalStateException(
            "CloudStream provider '$providerName' error: ${t::class.java.simpleName}: ${t.message}",
            t,
        )
    }

    // ── Identity ─────────────────────────────────────────────────────────────

    override val id: Long get() = CsSourceIds.idFor(providerName)
    override val name: String get() = providerName
    override val lang: String get() = liveProviderOrNull()?.lang ?: "en"
    override val baseUrl: String get() = liveProviderOrNull()?.mainUrl ?: "https://localhost"
    override val supportsLatest: Boolean = true

    private fun liveProviderOrNull(): MainAPI? = APIHolder.getApiFromNameNull(providerName)

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    // ── Catalogue (browse/search) ────────────────────────────────────────────

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val provider = liveProvider()
        if (!provider.hasMainPage) return AnimesPage(emptyList(), false)
        Logger.i(TAG) { "bridge: getPopularAnime '$providerName' page=$page (first shelf)" }
        val firstShelf = provider.mainPage.firstOrNull()
            ?: return AnimesPage(emptyList(), false)
        val response = guard {
            provider.getMainPage(
                page,
                MainPageRequest(
                    name = firstShelf.name,
                    data = firstShelf.data,
                    horizontalImages = firstShelf.horizontalImages,
                ),
            )
        } ?: return AnimesPage(emptyList(), false)
        val items = response.items.flatMap { it.list }.distinctBy { it.url }
        return AnimesPage(items.map { it.toSAnime() }, response.hasNext)
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val provider = liveProvider()
        Logger.i(TAG) { "bridge: getSearchAnime '$providerName' query='$query' page=$page" }
        val result = guard { provider.search(query, page) } ?: return AnimesPage(emptyList(), false)
        val items = result.items.distinctBy { it.url }
        return AnimesPage(items.map { it.toSAnime() }, result.hasNext)
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage = getPopularAnime(page)

    // ── Details ──────────────────────────────────────────────────────────────

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        Logger.i(TAG) { "bridge: getAnimeDetails '$providerName' url=${anime.url.take(120)}" }
        val response = guard { liveProvider().load(anime.url) }
            ?: return anime.apply { initialized = true }
        return response.applyOnto(anime)
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        Logger.i(TAG) { "bridge: getEpisodeList '$providerName' url=${anime.url.take(120)}" }
        val response = guard { liveProvider().load(anime.url) }
            ?: throw IllegalStateException("Provider '$providerName' returned no content for this URL")
        return response.toEpisodes()
    }

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()

    // ── Playback (Task 47 — the loadLinks/extractor session) ──────────────

    /**
     * The resolver tries getHosterList FIRST (ext-lib 16 contract). The
     * inherited AnimeHttpSource impl would fire a REAL `GET(baseUrl +
     * episode.url)` against the provider site — the CS episode URL is an
     * opaque data handle, not a page — so fail fast instead: the resolver
     * catches IllegalStateException and falls straight through to
     * [getVideoList].
     */
    override suspend fun getHosterList(episode: SEpisode): List<Hoster> =
        throw IllegalStateException("CloudStream bridge does not use the hoster API")

    /**
     * The provider's own link-resolution budget (MainAPI.loadLinksTimeoutMs,
     * CS3 clamp 5 s – 8 min, default 120 s) — multi-extractor resolution
     * genuinely takes longer than the resolver's old fixed 30 s.
     */
    override val videoListTimeoutMs: Long
        get() = liveProviderOrNull()?.loadLinksTimeoutMs?.coerceIn(5_000L, 480_000L) ?: 120_000L

    /**
     * loadLinks → the aniyomi [Video] list the whole downstream stack
     * (VideoResolver → ResolverSheet → WatchKey → MPV) consumes unchanged.
     *
     * Semantics (doc 19 §3.2, ported from the documented CS3 contract):
     * • the CS callbacks are streaming + may fire from any thread → queues;
     * • links already emitted are KEPT when loadLinks throws midway or
     *   returns false (streamed links are never rolled back);
     * • dedup by URL; DRM/DASH/TORRENT/MAGNET links are filtered (MPV cannot
     *   play them — doc 19 §2.5) and counted for the log;
     * • SEpisode.url carries the provider's opaque Episode.data handle — the
     *   exact `data` string loadLinks expects.
     */
    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val provider = liveProvider()
        // Task 48 (defensive heal): episodes cached by v0.2.68 carry the
        // JSON-quoted data handle (the newEpisode toJson bug — see MainAPI.kt).
        // Strip a surrounding quote pair so a stale cached row still resolves;
        // real handles never start AND end with a quote (JSON payloads start
        // with '{' or '['), so this can never corrupt fresh data.
        val data = episode.url.removeSurrounding("\"")
        if (data.isBlank() || data == "[]" || data == "about:blank") {
            throw IllegalStateException(
                "CloudStream '$providerName': this episode has no playable data",
            )
        }

        val links = ConcurrentLinkedQueue<ExtractorLink>()
        val subtitles = ConcurrentLinkedQueue<SubtitleFile>()
        val startedAt = System.currentTimeMillis()
        Logger.i(TAG) {
            "links: '$providerName' loadLinks start (episode='${episode.name.take(60)}' timeout=${videoListTimeoutMs}ms)"
        }

        var success = false
        val failure: Throwable? = try {
            success = provider.loadLinks(
                data = data,
                isCasting = false,
                subtitleCallback = { subtitles.add(it) },
                callback = { links.add(it) },
            )
            null
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (cf: com.lagradost.cloudstream3.network.CloudflareBlockedException) {
            throw cf
        } catch (t: Throwable) {
            t
        }

        // Total failure only when NOTHING was streamed; partial results win.
        if (failure != null && links.isEmpty()) {
            throw IllegalStateException(
                "CloudStream '$providerName' failed to load links: " +
                    "${failure::class.java.simpleName}: ${failure.message}",
                failure,
            )
        }
        if (failure != null) {
            Logger.w(TAG, failure) {
                "links: '$providerName' loadLinks failed midway — keeping ${links.size} partial link(s)"
            }
        }
        if (!success && links.isEmpty()) {
            throw IllegalStateException(
                "CloudStream '$providerName' returned no links for this episode (the provider's hosts may all be down)",
            )
        }

        // ── Task 49: HLS/DASH expansion pass ──
        // Master .m3u8 links become one Video PER QUALITY VARIANT (the original
        // app's picker behavior); static single-file .mpd DASH links become
        // directly playable progressive URLs. Both are bounded, fault-isolated
        // and FAIL-OPEN (any error keeps the original link / hides only the
        // DASH link, never aborts the whole resolution).
        val expandedLinks = expandHlsAndDashLinks(links.toList())

        // ── Mapping: ExtractorLink → Video ──
        var hiddenCount = 0
        var droppedCount = 0
        val seenUrls = HashSet<String>()
        val mirrorIndex = HashMap<String, Int>()
        val collectedSubtitles = subtitles.toList()
        val videos = expandedLinks.mapNotNull { link ->
            when {
                link is DrmExtractorLink -> {
                    hiddenCount++
                    null
                }
                link.type == ExtractorLinkType.TORRENT ||
                    link.type == ExtractorLinkType.MAGNET -> {
                    hiddenCount++
                    null
                }
                link.url.isBlank() -> {
                    droppedCount++
                    null
                }
                !seenUrls.add(link.url) -> {
                    droppedCount++
                    null
                }
                else -> link.toVideo(collectedSubtitles, mirrorIndex)
            }
        }

        Logger.i(TAG) {
            val byType = links.groupBy { it.type }.entries
                .joinToString(", ") { (type, list) -> "${type.name}=${list.size}" }
            "links: '$providerName' finished in ${System.currentTimeMillis() - startedAt}ms — " +
                "${videos.size} playable video(s) [$byType hidden=$hiddenCount dropped=$droppedCount] " +
                "subs=${collectedSubtitles.size} audio=${links.sumOf { it.audioTracks.size }} " +
                "sample=[${videos.take(3).joinToString { it.videoTitle.take(40) }}]"
        }
        if (videos.isEmpty()) {
            val reasons = buildList {
                if (hiddenCount > 0) add("$hiddenCount DRM/DASH/torrent link(s) hidden")
                if (droppedCount > 0) add("$droppedCount blank/duplicate link(s) dropped")
            }
            throw IllegalStateException(
                "CloudStream '$providerName': no playable links for this episode" +
                    (if (reasons.isNotEmpty()) " (${reasons.joinToString(", ")})" else ""),
            )
        }
        return videos
    }

    /** A `\d{3,4}p` token inside a label — the resolver's quality parser key. */
    private val qualityTokenRegex = Regex("""\d{3,4}p""")

    /**
     * ExtractorLink → Video. The videoTitle is crafted so the shared
     * VideoResolver parses it into its 3 tiers: server = the first
     * " - " segment (the hoster), audio = sub/dub keywords in the label,
     * quality = the `(\d{3,4})p` token (falling back to the whole title for
     * unlabeled mirrors — hence the per-source "Mirror N" numbering).
     */
    private fun ExtractorLink.toVideo(
        collectedSubtitles: List<SubtitleFile>,
        mirrorIndex: MutableMap<String, Int>,
    ): Video {
        val sourceLabel = source.ifBlank { providerName }
        // CS3 header fold rule (createVideoSource): referer merges into the
        // headers unless a referer key already exists; UA defaults to the CS
        // client UA when absent.
        val allHeaders = getAllHeaders().let { base ->
            if (base.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                base + ("User-Agent" to USER_AGENT)
            } else {
                base
            }
        }

        val label = name.trim()
        val qualityToken = when {
            qualityTokenRegex.containsMatchIn(label) -> null // label already carries it
            quality in 100..2160 && quality != 400 -> "${quality}p"
            else -> null
        }
        val display = when {
            qualityToken != null && label.isNotBlank() -> "$label $qualityToken"
            qualityToken != null -> qualityToken
            label.isNotBlank() -> label
            else -> {
                // Unlabeled link — number it per source so multiple mirrors
                // stay distinguishable in the quality picker.
                val index = (mirrorIndex[sourceLabel] ?: 0) + 1
                mirrorIndex[sourceLabel] = index
                "Mirror $index"
            }
        }

        return Video(
            videoUrl = url,
            videoTitle = "$sourceLabel - $display",
            headers = okhttp3.Headers.headersOf(
                *allHeaders.flatMap { listOf(it.key, it.value) }.toTypedArray(),
            ),
            // CS subs are episode-level (not per-link) — attach to every link.
            // Task 48 (per-track subtitle headers): SubtitleFile.headers (some
            // hosts 403 subtitle fetches without the right Referer/UA) are
            // forwarded on the Track — previously dropped at this fold.
            subtitleTracks = collectedSubtitles.map { Track(it.url, it.lang, it.headers) },
            audioTracks = audioTracks.mapIndexed { index, audio ->
                Track(audio.url, "Audio ${index + 1}")
            },
        )
    }

    // ── Task 49: HLS master expansion + DASH surfacing ─────────────────────

    /**
     * Round-9 general-compatibility pass over the raw [ExtractorLink]s before
     * Video mapping (R9-C recommendations b + a):
     *
     * • **HLS quality selection** — an M3U8 link the plugin did NOT label with
     *   a quality (raw master playlists, e.g. MovieBox's play-info streams) is
     *   fetched once and expanded into one link per quality variant, mirroring
     *   the original app's per-quality picker. Media playlists / fetch
     *   failures → the original link survives untouched (fail-open).
     *
     * • **DASH surfacing** — a .mpd link is fetched and parsed ([MpdParser]):
     *   a STATIC manifest whose representations are complete single files
     *   becomes directly playable VIDEO links (the BaseURL is the whole file —
     *   MPV plays it progressively; separate audio rides the audioTracks
     *   plumbing as mpv `audio-add`). Dynamic / multi-segment manifests stay
     *   hidden but are LOGGED so the next device round knows exactly what
     *   MovieBox serves.
     *
     * Bounds: ≤[MAX_HLS_EXPANSION_FETCHES] master fetches and ≤[MAX_MPD_SNIFFS]
     * manifest fetches per resolution, [EXPANSION_FETCH_TIMEOUT_MS] each — the
     * pass can add at most a few seconds to the worst case and nothing to the
     * providers that pre-expand (AniKoto/AllMovieLand label their variants, so
     * their links skip expansion entirely).
     */
    private suspend fun expandHlsAndDashLinks(links: List<ExtractorLink>): List<ExtractorLink> {
        if (links.isEmpty()) return links
        val out = mutableListOf<ExtractorLink>()
        var hlsFetches = 0
        var mpdFetches = 0
        for (link in links) {
            when {
                link.type == ExtractorLinkType.M3U8 &&
                    hlsFetches < MAX_HLS_EXPANSION_FETCHES &&
                    needsHlsExpansion(link) -> {
                    hlsFetches++
                    out += expandMasterPlaylist(link)
                }
                link.type == ExtractorLinkType.DASH &&
                    mpdFetches < MAX_MPD_SNIFFS &&
                    link.url.isNotBlank() -> {
                    mpdFetches++
                    out += surfaceDashManifest(link)
                }
                else -> out += link
            }
        }
        return out
    }

    /**
     * A master needs expansion when neither the label nor the quality carries
     * a height. Qualities.Unknown (400) is NOT a height (the same convention
     * [toVideo] uses) — raw masters arrive with Unknown quality.
     */
    private fun needsHlsExpansion(link: ExtractorLink): Boolean =
        !qualityTokenRegex.containsMatchIn(link.name) &&
            (link.quality !in 100..2160 || link.quality == 400)

    /** Fetch the playlist and fan out variants; ANY failure keeps the original link. */
    private suspend fun expandMasterPlaylist(link: ExtractorLink): List<ExtractorLink> {
        val variants = try {
            kotlinx.coroutines.withTimeoutOrNull(EXPANSION_FETCH_TIMEOUT_MS) {
                val response = com.lagradost.cloudstream3.app.get(
                    link.url,
                    referer = link.referer,
                    headers = link.getAllHeaders(),
                )
                val text = response.text
                if (!text.startsWith("#EXTM3U")) null
                else M3u8Helper.parseMasterPlaylist(text, link.url)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            Logger.d(TAG) { "hls: master expansion skipped for ${link.url.take(60)} — ${t.message}" }
            null
        }
        if (variants == null || variants.size <= 1) {
            // Media playlist, unparseable, or a single variant — nothing gained.
            return listOf(link)
        }
        Logger.i(TAG) {
            "hls: expanded master ${link.url.take(70)} → ${variants.size} variants " +
                "(${variants.mapNotNull { it.quality }.joinToString("/") { "$it" }}p)"
        }
        return variants.take(MAX_VARIANTS_PER_MASTER).map { variant ->
            ExtractorLink(
                source = link.source,
                name = link.name,
                url = variant.streamUrl,
                referer = link.referer,
                quality = variant.quality ?: link.quality,
                headers = link.headers,
                extractorData = link.extractorData,
                type = ExtractorLinkType.M3U8,
                audioTracks = link.audioTracks,
            )
        }
    }

    /**
     * Sniff a .mpd manifest. Returns playable VIDEO links (possibly with an
     * attached audio track) or NOTHING (the link stays hidden — but the reason
     * is logged for device diagnostics).
     */
    private suspend fun surfaceDashManifest(link: ExtractorLink): List<ExtractorLink> {
        val headers = link.getAllHeaders()
        val manifestText = try {
            kotlinx.coroutines.withTimeoutOrNull(EXPANSION_FETCH_TIMEOUT_MS) {
                com.lagradost.cloudstream3.app.get(link.url, referer = link.referer, headers = headers).text
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            Logger.w(TAG) { "mpd: manifest fetch failed for ${link.url.take(60)} — ${t.message} (staying hidden)" }
            null
        }
        if (manifestText == null) return emptyList()

        val info = MpdParser.parse(manifestText, link.url)
        return when {
            info.dynamic -> {
                Logger.i(TAG) {
                    "mpd: hidden DYNAMIC manifest ${link.url.take(60)} " +
                        "(video=${info.videoReps.size} audio=${info.audioReps.size}) — live manifests need a DASH client"
                }
                emptyList()
            }
            info.videoReps.none { it.singleFile && it.url.isNotBlank() } -> {
                Logger.i(TAG) {
                    "mpd: hidden multi-segment static manifest ${link.url.take(60)} " +
                        "(video=${info.videoReps.size} audio=${info.audioReps.size}, " +
                        "segmentTemplate=${info.videoReps.count { !it.singleFile }}) — segment-based DASH needs a DASH client"
                }
                emptyList()
            }
            else -> {
                val playableVideoReps = info.videoReps
                    .filter { it.singleFile && it.url.isNotBlank() }
                    .sortedByDescending { it.height ?: 0 }
                    .take(MAX_DASH_REPS_PER_MANIFEST)
                val audioRep = info.audioReps.firstOrNull { it.singleFile && it.url.isNotBlank() }
                Logger.i(TAG) {
                    "mpd: surfaced ${playableVideoReps.size} static DASH rendition(s) " +
                        "(${playableVideoReps.mapNotNull { it.height }.joinToString("/") { "$it" }}p" +
                        (if (audioRep != null) " + audio" else "") + ") from ${link.url.take(60)}"
                }
                playableVideoReps.map { rep ->
                    val audioTracks = if (audioRep != null) {
                        listOf(
                            com.lagradost.cloudstream3.newAudioFile(audioRep.url).apply {
                                this.headers = headers
                            },
                        )
                    } else {
                        emptyList()
                    }
                    ExtractorLink(
                        source = link.source,
                        name = link.name,
                        url = rep.url,
                        referer = link.referer,
                        quality = rep.height ?: link.quality,
                        headers = link.headers,
                        extractorData = link.extractorData,
                        type = ExtractorLinkType.VIDEO,
                        audioTracks = audioTracks,
                    )
                }
            }
        }
    }

    // ── AnimeHttpSource request/parse plumbing — NEVER called (all suspend
    //    entry points above are overridden); stubbed to fail loudly. ─────────

    override fun popularAnimeRequest(page: Int): Request = unsupported()
    override fun popularAnimeParse(response: Response): AnimesPage = unsupported()
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = unsupported()
    override fun searchAnimeParse(response: Response): AnimesPage = unsupported()
    override fun latestUpdatesRequest(page: Int): Request = unsupported()
    override fun latestUpdatesParse(response: Response): AnimesPage = unsupported()
    override fun animeDetailsParse(response: Response): SAnime = unsupported()
    override fun episodeListParse(response: Response): List<SEpisode> = unsupported()
    override fun episodeVideoParse(response: Response): SEpisode = unsupported()
    override fun seasonListParse(response: Response): List<SAnime> = unsupported()
    override fun hosterListParse(response: Response): List<eu.kanade.tachiyomi.animesource.model.Hoster> = unsupported()
    override fun videoListParse(response: Response, hoster: eu.kanade.tachiyomi.animesource.model.Hoster): List<Video> = unsupported()
    override fun videoListParse(response: Response): List<Video> = unsupported()
    override fun videoUrlParse(response: Response): String = unsupported()

    private fun unsupported(): Nothing = throw NotImplementedError(
        "CloudStream bridge does not use AnimeHttpSource HTTP plumbing (provider networking runs inside the plugin)",
    )

    // ── Mapping (CloudStream models → aniyomi models) ────────────────────────

    /**
     * Task 46 (device round 5, broken details thumbnails): MANY CloudStream
     * providers return RELATIVE image paths ("/poster/xyz.jpg") or
     * protocol-relative ones ("//cdn.example/x.jpg") — Coil silently fails on
     * those, which is why details pages showed placeholders even though the
     * extension "provided" the thumbnail. Everything image-shaped is now
     * absolutized against the provider's mainUrl before it reaches the UI.
     * Returns null for blank input so callers keep their fallbacks intact.
     */
    private fun resolveImageUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> {
                val base = liveProviderOrNull()?.mainUrl?.trimEnd('/')
                    ?: return trimmed // no base to resolve against — pass through
                "$base$trimmed"
            }
            else -> trimmed // relative without leading slash or a data/blob URI — pass through
        }
    }

    private fun SearchResponse.toSAnime(): SAnime = SAnime.create().apply {
        url = this@toSAnime.url
        title = this@toSAnime.name
        thumbnail_url = resolveImageUrl(posterUrl)
        initialized = false
    }

    private fun LoadResponse.applyOnto(anime: SAnime): SAnime {
        val resolvedPoster = resolveImageUrl(posterUrl)
        val resolvedBackground = resolveImageUrl(backgroundPosterUrl)
        val resolvedScore = score?.toDouble(10)

        // Task 46: the full diagnostic line the round-5 report asked for — one
        // filterable log line per details fetch showing EVERY field the
        // provider supplied and what the bridge did with it.
        Logger.i(TAG) {
            "details: '$providerName' " +
                "type=${this::class.simpleName} year=$year score=$resolvedScore " +
                "status=${showStatusOf()?.name ?: "-"} tags=${tags?.size ?: 0} " +
                "plot=${plot?.length ?: 0}ch " +
                "poster=" + when {
                    posterUrl.isNullOrBlank() -> "none"
                    resolvedPoster != posterUrl -> "resolved('$posterUrl' -> '$resolvedPoster')"
                    else -> "absolute"
                } +
                " background=" + if (backgroundPosterUrl.isNullOrBlank()) "none" else "present"
        }

        return anime.apply {
            title = name.ifBlank { anime.title }
            url = this@applyOnto.url
            description = plot
            genre = tags?.joinToString(", ")
            status = when (showStatusOf()) {
                ShowStatus.Completed -> SAnime.COMPLETED
                ShowStatus.Ongoing -> SAnime.ONGOING
                null -> SAnime.UNKNOWN
            }
            // Only overwrite the (often working) search-grid thumbnail with a
            // resolvable absolute URL — a poster we could NOT absolutize would
            // replace a good image with a broken one.
            if (!resolvedPoster.isNullOrBlank()) thumbnail_url = resolvedPoster
            if (!resolvedBackground.isNullOrBlank()) background_url = resolvedBackground
            // Task 46: year + score finally reach the details page (SAnime
            // enrichment channel — see SAnime.year / SAnime.score).
            // Task 47 (device round 6, "rating shows but year doesn't"): many
            // providers set `year` on SEARCH responses but omit it on load() —
            // the details flow now seeds the stub SAnime with the search-time
            // year, and load()'s year only WINS (never erases the seed).
            year = this@applyOnto.year ?: anime.year
            score = resolvedScore
            initialized = true
        }
    }

    private fun LoadResponse.showStatusOf(): ShowStatus? = when (this) {
        is TvSeriesLoadResponse -> showStatus
        is AnimeLoadResponse -> showStatus
        else -> null
    }

    private fun LoadResponse.toEpisodes(): List<SEpisode> {
        val raw: List<Pair<Episode, String?>> = when (this) {
            is TvSeriesLoadResponse -> episodes.map { it to null }
            is AnimeLoadResponse -> episodes.entries
                .sortedBy { it.key.id }
                .flatMap { entry ->
                    val label = when (entry.key) {
                        DubStatus.Dubbed -> "Dub"
                        DubStatus.Subbed -> "Sub"
                        else -> null
                    }
                    entry.value.map { it to label }
                }
            is MovieLoadResponse -> listOf(Episode(data = dataUrl, name = "Movie") to null)
            is LiveStreamLoadResponse -> listOf(Episode(data = dataUrl, name = "Live Stream") to null)
            else -> emptyList()
        }

        // Task 46 (device round 5, "all episodes land in one season"): the CS
        // Episode.season field was previously DROPPED, so a 2-season series
        // rendered as one flat list. The aniyomi season UI is NAME-TAG driven
        // (SeasonDetector patterns → season selector + per-season numbers), so
        // the season is now encoded INTO the episode name as a leading
        // "Season N - Episode M" tag — the exact pattern the detector +
        // EpisodeTitleParser + the details screen's season grouping understand.
        // Per-season fallback numbering keeps the tag complete when the
        // provider omits Episode.episode.
        val perSeasonCounters = HashMap<Int, Int>()
        val mapped = raw.mapIndexed { index, (ep, dubLabel) ->
            val season = ep.season?.takeIf { it > 0 }
            val episodeInSeason = ep.episode ?: season?.let { s ->
                perSeasonCounters[s] = (perSeasonCounters[s] ?: 0) + 1
                perSeasonCounters[s]
            } ?: (index + 1)

            SEpisode.create().apply {
                // The episode's stable identity for the whole app (downloads,
                // history, future loadLinks): the provider's opaque data handle.
                url = ep.data
                name = buildString {
                    if (season != null) {
                        append("Season $season - Episode $episodeInSeason")
                        if (!ep.name.isNullOrBlank()) append(" - ${ep.name}")
                    } else {
                        append(ep.name ?: "Episode ${ep.episode ?: index + 1}")
                    }
                    dubLabel?.let { append(" ($it)") }
                }
                episode_number = ep.episode?.toFloat()
                    ?: (index + 1).toFloat()
                scanlator = dubLabel
                summary = ep.description
                date_upload = ep.date ?: 0L
                // Task 46: CS episode posters (Episode.posterUrl) previously
                // vanished — the episode rows fell back to the cover for every
                // row. Resolved + forwarded so episode-level thumbnails work.
                preview_url = resolveImageUrl(ep.posterUrl)
            }
        }.distinctBy { it.url }

        // Task 46: the season-structure diagnostic — what the provider's
        // season data actually looked like, and what the bridge encoded.
        val seasonHistogram = raw.groupingBy { (ep, _) -> ep.season }.eachCount()
        Logger.i(TAG) {
            val seasonsDesc = seasonHistogram.entries
                .sortedWith(compareByDescending { it.key ?: Int.MIN_VALUE })
                .joinToString(", ") { (s, c) -> "S${s ?: "?"}=$c" }
            "episodes: '$providerName' -> ${mapped.size} episode(s) " +
                "[provider seasons: $seasonsDesc] " +
                "sample=[${mapped.take(3).joinToString { it.name.take(40) }}]"
        }

        return mapped
    }

    companion object {
        internal const val TAG = "Anikuta:Data:Cloudstream:Bridge"

        // ── Task 49: HLS/DASH expansion bounds (R9-PLAN reviewed) ──
        /** Master-playlist fetches per resolution (labeled variants skip expansion). */
        internal const val MAX_HLS_EXPANSION_FETCHES = 4

        /** Manifest fetches per resolution (MovieBox serves 5 .mpd — we sniff 2). */
        internal const val MAX_MPD_SNIFFS = 2

        /** Per-fetch budget — fail-open keeps the original link on expiry. */
        internal const val EXPANSION_FETCH_TIMEOUT_MS = 5_000L

        /** Quality variants surfaced per master (CDNs listing 15 variants stay sane). */
        internal const val MAX_VARIANTS_PER_MASTER = 8

        /** DASH renditions surfaced per static single-file manifest. */
        internal const val MAX_DASH_REPS_PER_MANIFEST = 4
    }
}

/**
 * Observes the TRUSTED CloudStream plugins and publishes every provider as a
 * bridged [AnimeHttpSource] under its stable synthetic id. The app wires this
 * into [com.confused.anikuta.data.extension.manager.ExtensionManager] so the
 * standard details screen resolves CS sources exactly like aniyomi ones.
 * Untrusting a plugin removes its providers from `installed` → the bridges
 * disappear on the next emission (the details screen then reports the source
 * as unavailable — the same behavior as an uninstalled aniyomi extension).
 */
class CloudstreamSourceRegistry(
    providerSources: StateFlow<List<CsProviderSource>>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _sources = MutableStateFlow<Map<Long, AnimeHttpSource>>(emptyMap())
    val sources: StateFlow<Map<Long, AnimeHttpSource>> = _sources.asStateFlow()

    init {
        scope.launch {
            providerSources.collect { csSources ->
                val bridged = csSources.associate { cs ->
                    CsSourceIds.idFor(cs.providerName) to CloudstreamAnimeSourceBridge(cs.providerName)
                }
                _sources.value = bridged
                Logger.i(TAG) {
                    "bridge: ${bridged.size} CloudStream source(s) bridged into the extension source map " +
                        "[${bridged.values.joinToString { it.name }}]"
                }
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    companion object {
        internal const val TAG = "Anikuta:Data:Cloudstream:Bridge"
    }
}
