// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// THIS FILE'S NAME MATTERS: every top-level function/property here compiles into the
// `MainAPIKt` facade class that plugin bytecode references. All CS3 MainAPI.kt
// top-level declarations must live in this file (classes may live elsewhere — they
// get their own class files). The data models are in their own files.
@file:Suppress("DEPRECATION_ERROR", "ktlint")

package com.lagradost.cloudstream3

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.AtomicMutableList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.atomicListOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Opt-in annotations (declared in MainAPI.kt upstream; part of the source surface)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * API available only on prerelease builds. Using it will cause stable to crash
 * with `NoSuchMethodException`.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(
    message = "This API is only available on prerelease builds. Using it will cause CloudStream stable to crash.",
    level = RequiresOptIn.Level.ERROR,
)
annotation class Prerelease

@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(
    message = "This API is marked as internal and should not be used by extensions. " +
        "Using it could cause catastrophic build or runtime errors and may be changed or removed at any time.",
    level = RequiresOptIn.Level.ERROR,
)
annotation class InternalAPI

@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(
    message = "Only use this if you know what you are doing and you need to bypass the SSL certificate checks. " +
        "Never use this for sensitive network requests such as logins.",
    level = RequiresOptIn.Level.WARNING,
)
annotation class UnsafeSSL

/** Temporary; will be removed when the Jackson -> Kotlinx serialization migration is completed. */
@InternalAPI
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SkipSerializationTest

// ─────────────────────────────────────────────────────────────────────────────
// Top-level constants, globals and exceptions
// ─────────────────────────────────────────────────────────────────────────────

/** Constant for the all-languages preference — the equivalent of all languages being set. */
const val AllLanguagesName = "universal"

const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"

/** The standard "provider failed" exception providers throw (22/80 census plugins). */
class ErrorLoadingException(message: String? = null) : Exception(message)

/** The kotlinx JSON instance of the dual JSON stack (kotlinx preferred, Jackson fallback). */
val json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

/** The Jackson Kotlin fallback of the dual JSON stack. STRICTLY 2.13.1 (minSdk 24 constraint). */
val mapper = JsonMapper.builder().addModule(kotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .build()!!

// Provider status codes.
const val PROVIDER_STATUS_KEY = "PROVIDER_STATUS_KEY"
const val PROVIDER_STATUS_BETA_ONLY = 3
const val PROVIDER_STATUS_SLOW = 2
const val PROVIDER_STATUS_OK = 1
const val PROVIDER_STATUS_DOWN = 0

// ─────────────────────────────────────────────────────────────────────────────
// Provider configuration JSON models
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class ProvidersInfoJson(
    @JsonProperty("name") @SerialName("name") var name: String,
    @JsonProperty("url") @SerialName("url") var url: String,
    @JsonProperty("credentials") @SerialName("credentials") var credentials: String? = null,
    @JsonProperty("status") @SerialName("status") var status: Int,
)

@Serializable
data class SettingsJson(
    @JsonProperty("enableAdult") @SerialName("enableAdult") var enableAdult: Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────────
// APIHolder — the runtime provider registry our loader registers into
// ─────────────────────────────────────────────────────────────────────────────

object APIHolder {
    val unixTimeMS: Long
        get() = System.currentTimeMillis()

    val unixTime: Long
        get() = unixTimeMS / 1000L

    val allProviders = atomicListOf<MainAPI>()

    fun initAll() {
        allProviders.withLock {
            for (api in allProviders) {
                api.init()
            }
        }
    }

    /** String extension function to Capitalize first char of string. */
    fun String.capitalize(): String =
        if (isEmpty()) this else this[0].uppercaseChar() + substring(1)

    var apis: AtomicMutableList<MainAPI> = atomicListOf()

    var apiMap: Map<String, Int>? = null

    fun addPluginMapping(plugin: MainAPI) {
        synchronized(this) {
            if (!allProviders.contains(plugin)) {
                allProviders.add(plugin)
            }
            apis.add(plugin)
            apiMap = null // invalidate
        }
    }

    fun removePluginMapping(plugin: MainAPI) {
        synchronized(this) {
            allProviders.remove(plugin)
            apis.remove(plugin)
            apiMap = null // invalidate
        }
    }

    private fun initMap(forcedUpdate: Boolean = false) {
        synchronized(this) {
            if (apiMap == null || forcedUpdate) {
                val map = HashMap<String, Int>()
                apis.withLock {
                    for (i in apis.indices) {
                        map[apis[i].name] = i
                    }
                }
                apiMap = map
            }
        }
    }

    fun getApiFromNameNull(apiName: String?): MainAPI? {
        if (apiName == null) return null
        initMap()
        val index = apiMap?.get(apiName) ?: return null
        return apis.getOrNull(index)
    }

    fun getApiFromUrlNull(url: String?): MainAPI? {
        if (url == null) return null
        return allProviders.firstOrNull { url.startsWith(it.mainUrl) }
    }

    /** Gets the website captcha token. */
    suspend fun getCaptchaToken(url: String, key: String, referer: String? = null): String? = null

    // AniList tracker enrichment is a content-phase feature; providers still load without it.
    suspend fun getTracker(
        titles: List<String>,
        types: Set<TrackerType>?,
        year: Int?,
    ): Tracker? = getTracker(titles, types, year, false)

    suspend fun getTracker(
        titles: List<String>,
        types: Set<TrackerType>?,
        year: Int?,
        lessAccurate: Boolean,
    ): Tracker? = null
}

// ─────────────────────────────────────────────────────────────────────────────
// MainAPI — the abstract class every provider extends
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Every provider will **not** have try catch built in, so handle exceptions when
 * calling these functions.
 */
abstract class MainAPI {
    companion object {
        var overrideData: HashMap<String, ProvidersInfoJson>? = null
        var settingsForProvider: SettingsJson = SettingsJson()
    }

    fun init() {
        overrideData?.get(this::class.simpleName)?.let { data ->
            overrideWithNewData(data)
        }
    }

    fun overrideWithNewData(data: ProvidersInfoJson) {
        if (!canBeOverridden) return
        this.name = data.name
        if (data.url.isNotBlank() && data.url != "NONE") {
            this.mainUrl = data.url
        }
        this.storedCredentials = data.credentials
    }

    /** Name of the plugin that will be used in UI. */
    open var name = "NONE"

    /** Main Url of the plugin, can be used directly in code or replaced by the clone-site feature. */
    open var mainUrl = "NONE"

    open var storedCredentials: String? = null
    open var canBeOverridden: Boolean = true

    /** Request the homepage one after the other — for sites that block parallel requests. */
    open var sequentialMainPage: Boolean = false

    /** Milliseconds of extra delay between homepage requests on first load (sequential mode). */
    open var sequentialMainPageDelay: Long = 0L

    /** Milliseconds of extra delay between homepage requests when scrolling (sequential mode). */
    open var sequentialMainPageScrollDelay: Long = 0L

    /** Tracks when the last homepage request was, in unixtime ms. */
    var lastHomepageRequest: Long = 0L

    /** The language as an IETF BCP 47 conformant tag. */
    open var lang = "en"

    /** If link is stored in the "data" string, so links can be instantly loaded. */
    open val instantLinkLoading = false

    /** Set false if links require referer or for some reason can't be played on a chromecast. */
    open val hasChromecastSupport = true

    /** If all links are encrypted then set this to false. */
    open val hasDownloadSupport = true

    /** Used for testing and can be used to disable the providers if WebView is not available. */
    open val usesWebView = false

    /** Determines which plugin a given provider is from. This is the full path to the plugin. */
    var sourcePlugin: String? = null

    open val hasMainPage = false
    open val hasQuickSearch = false

    /** The timeout on the `loadLinks` functions in milliseconds. (hint only) */
    open val loadLinksTimeoutMs: Long? = null

    /** The timeout on the `getMainPage` functions in milliseconds. (hint only) */
    open val getMainPageTimeoutMs: Long? = null

    /** The timeout on the `search` functions in milliseconds. (hint only) */
    open val searchTimeoutMs: Long? = null

    /** The timeout on the `quickSearch` functions in milliseconds. (hint only) */
    open val quickSearchTimeoutMs: Long? = null

    /** The timeout on the `load` functions in milliseconds. (hint only) */
    open val loadTimeoutMs: Long? = null

    /** A set of which ids the provider can open with getLoadUrl(). */
    open val supportedSyncNames = setOf<SyncIdName>()

    open val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Cartoon,
        TvType.Anime,
        TvType.OVA,
    )

    open val vpnStatus = VPNStatus.None
    open val providerType = ProviderType.DirectProvider

    open val mainPage = listOf(MainPageData("", "", false))

    open suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse? {
        throw NotImplementedError()
    }

    /** Paginated search, starts with page: 1. */
    open suspend fun search(query: String, page: Int): SearchResponseList? {
        val searchResults = search(query) ?: return null
        return newSearchResponseList(searchResults, false)
    }

    open suspend fun search(query: String): List<SearchResponse>? {
        throw NotImplementedError()
    }

    open suspend fun quickSearch(query: String): List<SearchResponse>? {
        throw NotImplementedError()
    }

    open suspend fun load(url: String): LoadResponse? {
        throw NotImplementedError()
    }

    open suspend fun extractorVerifierJob(extractorData: String?) {
        throw NotImplementedError()
    }

    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        throw NotImplementedError()
    }

    /** An okhttp interceptor for use in OkHttpDataSource. */
    open fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? {
        return null
    }

    /** Get the load() url based on a sync ID like IMDb or MAL. Only contains SyncIds based on supportedSyncUrls. */
    open suspend fun getLoadUrl(name: SyncIdName, id: String): String? {
        return null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main-page models + builders
// ─────────────────────────────────────────────────────────────────────────────

fun mainPage(url: String, name: String, horizontalImages: Boolean = false): MainPageData =
    MainPageData(name = name, data = url, horizontalImages = horizontalImages)

fun mainPageOf(vararg elements: MainPageData): List<MainPageData> = elements.toList()

fun mainPageOf(vararg elements: Pair<String, String>): List<MainPageData> =
    elements.map { (url, name) -> MainPageData(name = name, data = url) }

fun newHomePageResponse(
    name: String,
    list: List<SearchResponse>,
    hasNext: Boolean? = null,
): HomePageResponse = newHomePageResponse(
    list = listOf(HomePageList(name, list)),
    hasNext = hasNext ?: list.isNotEmpty(),
)

fun newHomePageResponse(
    data: MainPageRequest,
    list: List<SearchResponse>,
    hasNext: Boolean? = null,
): HomePageResponse = newHomePageResponse(
    list = listOf(HomePageList(data.name, list, data.horizontalImages)),
    hasNext = hasNext ?: list.isNotEmpty(),
)

fun newHomePageResponse(list: HomePageList, hasNext: Boolean? = null): HomePageResponse =
    newHomePageResponse(list = listOf(list), hasNext = hasNext ?: list.list.isNotEmpty())

fun newHomePageResponse(list: List<HomePageList>, hasNext: Boolean? = null): HomePageResponse =
    HomePageResponse(list, hasNext ?: list.any { it.list.isNotEmpty() })

fun newSearchResponseList(
    list: List<SearchResponse>,
    hasNext: Boolean? = null,
): SearchResponseList = SearchResponseList(list, hasNext ?: list.isNotEmpty())

fun List<SearchResponse>.toNewSearchResponseList(hasNext: Boolean? = null): SearchResponseList =
    newSearchResponseList(this, hasNext)

// ─────────────────────────────────────────────────────────────────────────────
// Search response builders
// ─────────────────────────────────────────────────────────────────────────────

fun MainAPI.newTorrentSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Torrent,
    fix: Boolean = true,
    initializer: TorrentSearchResponse.() -> Unit = { },
): TorrentSearchResponse {
    val builder = TorrentSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
        posterUrl = null,
    )
    builder.initializer()
    return builder
}

fun MainAPI.newMovieSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    fix: Boolean = true,
    initializer: MovieSearchResponse.() -> Unit = { },
): MovieSearchResponse {
    val builder = MovieSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    builder.initializer()
    return builder
}

fun MainAPI.newLiveSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Live,
    fix: Boolean = true,
    initializer: LiveSearchResponse.() -> Unit = { },
): LiveSearchResponse {
    val builder = LiveSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    builder.initializer()
    return builder
}

fun MainAPI.newTvSeriesSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    fix: Boolean = true,
    initializer: TvSeriesSearchResponse.() -> Unit = { },
): TvSeriesSearchResponse {
    val builder = TvSeriesSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    builder.initializer()
    return builder
}

fun MainAPI.newAnimeSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    fix: Boolean = true,
    initializer: AnimeSearchResponse.() -> Unit = { },
): AnimeSearchResponse {
    val builder = AnimeSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    builder.initializer()
    return builder
}

/** Adds a quality badge to a [SearchResponse]. */
fun SearchResponse.addQuality(quality: String) {
    this.quality = getQualityFromString(quality)
}

/** Adds a poster to a [SearchResponse]. */
fun SearchResponse.addPoster(url: String?, headers: Map<String, String>? = null) {
    if (url != null) {
        this.posterUrl = url
        if (headers != null) this.posterHeaders = headers
    }
}

/** Adds a poster to a [LoadResponse]. */
fun LoadResponse.addPoster(url: String?, headers: Map<String, String>? = null) {
    if (url != null) {
        this.posterUrl = url
        if (headers != null) this.posterHeaders = headers
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Load response builders
// ─────────────────────────────────────────────────────────────────────────────

suspend fun MainAPI.newTorrentLoadResponse(
    name: String,
    url: String,
    magnet: String? = null,
    torrent: String? = null,
    initializer: suspend TorrentLoadResponse.() -> Unit = { },
): TorrentLoadResponse {
    val builder = TorrentLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        magnet = magnet,
        torrent = torrent,
        plot = null,
    )
    builder.initializer()
    if (builder.magnet.isNullOrBlank() && builder.torrent.isNullOrBlank()) {
        builder.comingSoon = true
    }
    return builder
}

suspend fun MainAPI.newAnimeLoadResponse(
    name: String,
    url: String,
    type: TvType,
    comingSoonIfNone: Boolean = true,
    initializer: suspend AnimeLoadResponse.() -> Unit = { },
): AnimeLoadResponse {
    val builder = AnimeLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
    )
    builder.initializer()
    if (comingSoonIfNone && builder.episodes.values.all { it.isEmpty() }) {
        builder.comingSoon = true
    }
    return builder
}

suspend fun MainAPI.newLiveStreamLoadResponse(
    name: String,
    url: String,
    dataUrl: String,
    initializer: suspend LiveStreamLoadResponse.() -> Unit = { },
): LiveStreamLoadResponse {
    val builder = LiveStreamLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        dataUrl = dataUrl,
    )
    builder.initializer()
    if (builder.dataUrl.isBlank()) {
        builder.comingSoon = true
    }
    return builder
}

suspend fun <T> MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType,
    data: T?,
    initializer: suspend MovieLoadResponse.() -> Unit = { },
): MovieLoadResponse {
    // String short-circuits; anything else serializes via the dual JSON stack.
    val dataUrl = when (data) {
        null -> ""
        is String -> data
        else -> with(com.lagradost.cloudstream3.utils.AppUtils) { data.toJson() }
    }
    return newMovieLoadResponse(name, url, type, dataUrl, initializer)
}

suspend fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType,
    dataUrl: String,
    initializer: suspend MovieLoadResponse.() -> Unit = { },
): MovieLoadResponse {
    val builder = MovieLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
        dataUrl = dataUrl,
    )
    builder.initializer()
    if (builder.dataUrl.isBlank()) {
        builder.comingSoon = true
    }
    return builder
}

suspend fun MainAPI.newTvSeriesLoadResponse(
    name: String,
    url: String,
    type: TvType,
    episodes: List<Episode>,
    initializer: suspend TvSeriesLoadResponse.() -> Unit = { },
): TvSeriesLoadResponse {
    val builder = TvSeriesLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
        episodes = episodes,
    )
    builder.initializer()
    if (builder.episodes.isEmpty()) {
        builder.comingSoon = true
    }
    return builder
}

// ─────────────────────────────────────────────────────────────────────────────
// Episode builders + helpers
// ─────────────────────────────────────────────────────────────────────────────

fun MainAPI.newEpisode(
    url: String,
    initializer: Episode.() -> Unit = { },
    fix: Boolean = true,
): Episode {
    val builder = Episode(data = if (fix) fixUrl(url) else url)
    builder.initializer()
    return builder
}

fun <T> MainAPI.newEpisode(
    data: T,
    initializer: Episode.() -> Unit = { },
): Episode {
    val builder = Episode(data = with(com.lagradost.cloudstream3.utils.AppUtils) { data.toJson() })
    builder.initializer()
    return builder
}

fun AnimeLoadResponse.addEpisodes(status: DubStatus, episodes: List<Episode>?) {
    if (episodes.isNullOrEmpty()) return
    this.episodes[status] = (this.episodes[status] ?: emptyList()) + episodes
}

fun AnimeSearchResponse.addDubStatus(status: DubStatus, episodes: Int? = null) {
    this.dubStatus = (this.dubStatus ?: mutableSetOf()).also { it.add(status) }
    episodes?.let { this.episodes[status] = it }
}

fun AnimeSearchResponse.addDubStatus(isDub: Boolean, episodes: Int? = null) {
    addDubStatus(if (isDub) DubStatus.Dubbed else DubStatus.Subbed, episodes)
}

fun AnimeSearchResponse.addDub(episodes: Int?) = addDubStatus(DubStatus.Dubbed, episodes)

fun AnimeSearchResponse.addSub(episodes: Int?) = addDubStatus(DubStatus.Subbed, episodes)

fun AnimeSearchResponse.addDubStatus(
    dubExist: Boolean,
    subExist: Boolean,
    dubEpisodes: Int? = null,
    subEpisodes: Int? = null,
) {
    if (dubExist) addDubStatus(DubStatus.Dubbed, dubEpisodes)
    if (subExist) addDubStatus(DubStatus.Subbed, subEpisodes)
}

fun AnimeSearchResponse.addDubStatus(status: String, episodes: Int? = null) {
    when (status.lowercase()) {
        "dub", "dubbed" -> addDubStatus(DubStatus.Dubbed, episodes)
        "sub", "subbed" -> addDubStatus(DubStatus.Subbed, episodes)
        else -> addDubStatus(DubStatus.None, episodes)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Season helpers
// ─────────────────────────────────────────────────────────────────────────────

@JvmName("addSeasonNamesString")
fun EpisodeResponse.addSeasonNames(names: List<String>) {
    this.seasonNames = names.mapIndexed { index, s -> SeasonData(index + 1, s) }
}

@JvmName("addSeasonNamesSeasonData")
fun EpisodeResponse.addSeasonNames(names: List<SeasonData>) {
    this.seasonNames = names
}

// ─────────────────────────────────────────────────────────────────────────────
// Top-level utility functions
// ─────────────────────────────────────────────────────────────────────────────

fun base64Decode(string: String): String = String(base64DecodeArray(string))

fun base64DecodeArray(string: String): ByteArray {
    val cleaned = string.trim().replace("-", "+").replace("_", "/")
    return try {
        Base64.getDecoder().decode(cleaned)
    } catch (e: IllegalArgumentException) {
        Base64.getMimeDecoder().decode(cleaned)
    }
}

fun base64Encode(array: ByteArray): String = Base64.getEncoder().encodeToString(array)

fun MainAPI.fixUrlNull(url: String?): String? {
    if (url == null) return null
    return fixUrl(url)
}

/**
 * Joins a possibly-relative URL onto [MainAPI.mainUrl]. Absolute URLs, protocol-relative
 * URLs, and JSON payloads are passed through unchanged.
 */
fun MainAPI.fixUrl(url: String): String {
    if (url.isBlank()) return url
    // Absolute (any scheme), protocol-relative, or embedded JSON payloads stay untouched.
    if (url.contains("://") || url.startsWith("//") || url.startsWith("{\"") || url.startsWith("[") ||
        url.startsWith("magnet:") || url.startsWith("data:")
    ) {
        return url
    }
    if (url.startsWith("#") || url.startsWith("?")) return mainUrl.trimEnd('/') + url
    return mainUrl.trimEnd('/') + "/" + url.trimStart('/')
}

/** Sort the urls based on quality (descending). */
fun sortUrls(urls: Set<ExtractorLink>): List<ExtractorLink> = urls.sortedByDescending { it.quality }

fun capitalizeString(str: String): String {
    if (str.isEmpty()) return str
    return str.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}

fun capitalizeStringNullable(str: String?): String? = str?.let { capitalizeString(it) }

fun fixTitle(str: String): String = str.split(" ").joinToString(" ") { word ->
    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}

fun imdbUrlToId(url: String): String? {
    val match = Regex("""/title/(tt\d+)""").find(url) ?: return null
    return match.groups[1]?.value
}

fun imdbUrlToIdNullable(url: String?): String? {
    if (url == null) return null
    return imdbUrlToId(url)
}

/**
 * Swaps the scheme/host/port of a previously stored link onto the provider's
 * CURRENT mainUrl (used after clone-site URL changes).
 */
fun MainAPI.updateUrl(url: String): String {
    if (url.isBlank() || mainUrl.isBlank() || mainUrl == "NONE") return url
    return try {
        val old = URI(url)
        val new = URI(mainUrl)
        val reconstructed = URI(
            new.scheme ?: old.scheme,
            new.rawUserInfo ?: old.rawUserInfo,
            new.host ?: old.host,
            if (new.port != -1) new.port else old.port,
            old.rawPath,
            old.rawQuery,
            old.rawFragment,
        )
        reconstructed.toString()
    } catch (e: Exception) {
        url
    }
}

/** Parses "1h 22m"-style durations into minutes. */
fun getDurationFromString(input: String?): Int? {
    if (input == null) return null
    val hours = Regex("""(\d+)\s*(?:h|hr|hour|hours)""", RegexOption.IGNORE_CASE)
        .find(input)?.groups?.get(1)?.value?.toIntOrNull() ?: 0
    val minutes = Regex("""(\d+)\s*(?:m|min|minute|minutes)""", RegexOption.IGNORE_CASE)
        .find(input)?.groups?.get(1)?.value?.toIntOrNull() ?: 0
    if (hours == 0 && minutes == 0) return null
    return hours * 60 + minutes
}

/** Extracts every http(s) URL found in a text blob. */
fun fetchUrls(text: String?): List<String> {
    if (text == null) return emptyList()
    return Regex("""https?://[^\s"'<>\\]+""")
        .findAll(text)
        .map { it.value.trimEnd(',', ')', ']', '}') }
        .distinct()
        .toList()
}

/** True if the date (yyyy-MM-dd or similar) is in the future. */
fun isUpcoming(dateString: String?): Boolean {
    if (dateString == null) return false
    val formats = listOf("yyyy-MM-dd", "yyyy-MM-dd'T'HH:mm:ss", "dd-MM-yyyy", "MM/dd/yyyy", "dd MMM yyyy")
    for (format in formats) {
        try {
            val date = SimpleDateFormat(format, Locale.ROOT).parse(dateString) ?: continue
            return date.after(Date())
        } catch (e: Exception) {
            // try next format
        }
    }
    return false
}

@Deprecated(
    "toRatingInt() is deprecated. Use new score API instead.",
    level = DeprecationLevel.ERROR,
)
fun String?.toRatingInt(): Int? {
    val score = Score.from10(this) ?: return null
    return score.toInt(10)
}

// ─────────────────────────────────────────────────────────────────────────────
// TvType helpers
// ─────────────────────────────────────────────────────────────────────────────

fun TvType.isMovieType(): Boolean =
    this == TvType.AnimeMovie || this == TvType.Live || this == TvType.Movie ||
        this == TvType.Torrent || this == TvType.Video

fun TvType.isAudioType(): Boolean =
    this == TvType.Audio || this == TvType.AudioBook || this == TvType.Music || this == TvType.Podcast

fun TvType.isLiveStream(): Boolean = this == TvType.Live

fun TvType.isAnimeOp(): Boolean = this == TvType.Anime || this == TvType.OVA

fun TvType?.isEpisodeBased(): Boolean =
    this == TvType.Anime || this == TvType.AsianDrama || this == TvType.Cartoon || this == TvType.TvSeries

fun TvType.getFolderPrefix(): String = when (this) {
    TvType.Movie -> "Movies"
    TvType.AnimeMovie -> "Movies"
    TvType.TvSeries -> "TV Series"
    TvType.Cartoon -> "Cartoons"
    TvType.Anime -> "Anime"
    TvType.OVA -> "Anime"
    TvType.Torrent -> "Torrents"
    TvType.Documentary -> "Documentaries"
    TvType.AsianDrama -> "AsianDramas"
    TvType.Live -> "Live"
    TvType.NSFW -> "NSFW"
    TvType.Others -> "Others"
    TvType.Music -> "Music"
    TvType.AudioBook -> "AudioBooks"
    TvType.CustomMedia -> "CustomMedia"
    TvType.Audio -> "Audio"
    TvType.Podcast -> "Podcasts"
    TvType.Video -> "Videos"
}

fun LoadResponse?.isEpisodeBased(): Boolean = this?.type.isEpisodeBased()

fun LoadResponse?.isAnimeBased(): Boolean = this?.type == TvType.Anime || this?.type == TvType.OVA

// ─────────────────────────────────────────────────────────────────────────────
// getQualityFromString (SearchQuality badge from a free-text label)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Maps a free-text release label to a [SearchQuality] badge.
 * Our own lookup covering the standard release-type spellings.
 */
fun getQualityFromString(string: String?): SearchQuality? {
    if (string == null) return null
    val s = string.trim().lowercase().replace(" ", "").replace(".", "")
    return when {
        s.contains("camrip") -> SearchQuality.CamRip
        s.contains("hdcam") || s.contains("hdtc") -> SearchQuality.HdCam
        s.contains("cam") || s.contains("ts") && s.contains("hd") -> SearchQuality.Cam
        s == "ts" || s.contains("telesync") -> SearchQuality.Telesync
        s.contains("tc") || s.contains("telecine") -> SearchQuality.Telecine
        s.contains("workprint") || s == "wp" -> SearchQuality.WorkPrint
        s.contains("hdrip") || s.contains("hdr") -> SearchQuality.HDR
        s.contains("webdl") || s.contains("webdl") || s.contains("webrip") || s.contains("web") ->
            SearchQuality.WebRip

        s.contains("bluray") || s.contains("blueray") || s == "br" || s == "brrip" -> SearchQuality.BlueRay
        s.contains("dvdrip") || s.contains("dvdscr") || s == "dvd" -> SearchQuality.DVD
        s.contains("sd") -> SearchQuality.SD
        s.contains("2160") || s.contains("4k") || s.contains("uhd") ->
            if (s.contains("uhd")) SearchQuality.UHD else SearchQuality.FourK

        s.contains("1080") || s.contains("720") || s.contains("480") || s.contains("360") ||
            s.contains("hd") || s.contains("hq") || s.contains("fhd") ->
            if (s.contains("hq")) SearchQuality.HQ else SearchQuality.HD

        else -> null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Subtitle/audio file builders
// ─────────────────────────────────────────────────────────────────────────────

// No `MainAPI.` receiver so extractors can use these too.
suspend fun newSubtitleFile(
    lang: String,
    url: String,
    initializer: suspend SubtitleFile.() -> Unit = { },
): SubtitleFile {
    val builder = SubtitleFile(lang = lang, url = url, headers = null)
    builder.initializer()
    return builder
}

suspend fun newAudioFile(
    url: String,
    initializer: suspend AudioFile.() -> Unit = { },
): AudioFile {
    val builder = AudioFile(url = url)
    builder.initializer()
    return builder
}

// ─────────────────────────────────────────────────────────────────────────────
// Episode.addDate overloads
// ─────────────────────────────────────────────────────────────────────────────

@Suppress("FormatStringsInDatetimeFormats")
fun Episode.addDate(date: String?, format: String = "yyyy-MM-dd") {
    if (date == null) return
    val parsed = runCatching {
        SimpleDateFormat(format, Locale.ROOT).parse(date)?.time
    }.getOrNull() ?: runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).parse(date)?.time
    }.getOrNull() ?: runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(date)?.time
    }.getOrNull()
    if (parsed != null) this.date = parsed
}

fun Episode.addDate(date: kotlinx.datetime.LocalDate?) {
    this.date = date?.toEpochDays()?.let { days ->
        // LocalDate epoch-days → unix ms (our own conversion; datetime lib keeps us honest).
        days.toLong() * 24L * 60L * 60L * 1000L
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
fun Episode.addDate(date: kotlin.time.Instant?) {
    this.date = date?.toEpochMilliseconds()
}

@Deprecated(
    message = "Use addDate with LocalDate, Instant, or String instead.",
    level = DeprecationLevel.WARNING,
)
fun Episode.addDate(date: Date?) {
    this.date = date?.time
}
