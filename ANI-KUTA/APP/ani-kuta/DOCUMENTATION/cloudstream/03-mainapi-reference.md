# 03 — MainAPI Reference (The Complete Provider Contract)

> **Scope**: THE central reference for `com.lagradost.cloudstream3.MainAPI` — the abstract class every
> CloudStream provider extends. Every property and method a plugin can override, with exact signatures,
> call-flow semantics, real-world examples from providers in our research workspace, and pitfalls.
> Data classes (`SearchResponse` family, `LoadResponse` family, `Episode`, `ExtractorLink`, …) get their
> own catalog in **05-data-models.md**; here they appear only as needed to explain signatures.
>
> **Primary source**: `research/cloudstream/library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt`
> (2861 lines, master @ efc1915, 2026-08-28 — the same snapshot all B1 docs cite).
> Line numbers below refer to that file unless another path is given.
>
> **Markers**: `[verified]` = read in source · `[docs]` = from recloudstream/csdocs · `[inferred]` = reasoned, needs verification.

---

## Table of contents

1. [Class declaration & constructor](#1-class-declaration--constructor)
2. [Every overridable member](#2-every-overridable-member)
   - 2.1 [Identity & capability properties](#21-identity--capability-properties-open-valvar)
   - 2.2 [Timing / pacing properties](#22-timing--pacing-properties)
   - 2.3 [`mainPage` + the `mainPageOf` DSL](#23-mainpage--the-mainpageof-dsl)
   - 2.4 [`getMainPage`](#24-getmainpage)
   - 2.5 [`search(query, page)` — paginated](#25-searchquery-page--paginated)
   - 2.6 [`search(query)` — non-paginated](#26-searchquery--non-paginated)
   - 2.7 [`quickSearch`](#27-quicksearch)
   - 2.8 [`load`](#28-load)
   - 2.9 [`loadLinks`](#29-loadlinks)
   - 2.10 [`extractorVerifierJob`](#210-extractorverifierjob)
   - 2.11 [`getVideoInterceptor`](#211-getvideointerceptor)
   - 2.12 [`getLoadUrl`](#212-getloadurl)
3. [Provider identity & metadata fields](#3-provider-identity--metadata-fields)
4. [Rank & sorting](#4-rank--sorting)
5. [Helpers available inside a plugin](#5-helpers-available-inside-a-plugin)
6. [Extractor-side API (`ExtractorApi`)](#6-extractor-side-api-extractorapi)
7. [Extension points NOT in MainAPI.kt (metaproviders)](#7-extension-points-not-in-mainapikt-metaproviders)
8. [API versioning & compatibility](#8-api-versioning--compatibility)
9. [Method-by-method cheat table](#9-method-by-method-cheat-table)
10. [ANI-KUTA takeaways](#10-ani-kuta-takeaways)
11. [Could not verify](#11-could-not-verify)

---

## 1. Class declaration & constructor

**There are NO constructor parameters.** The entire class is a property-override surface — providers
configure themselves by overriding `open var`/`open val` fields with initializers:

```kotlin
/**Every provider will **not** have try catch built in, so handle exceptions when calling these functions*/
abstract class MainAPI {
    companion object {
        var overrideData: HashMap<String, ProvidersInfoJson>? = null
        var settingsForProvider: SettingsJson = SettingsJson()
    }
```
`MainAPI.kt:493-498` [verified]

The KDoc on the class is a contract in itself: **callers (the app) must wrap every call in their own
try/catch — providers are allowed to throw.** The app does this in `APIRepository` via
`safeApiCall` + `withTimeout` (`app/.../ui/APIRepository.kt:85-219` [verified]).

Two final (non-overridable) lifecycle methods exist on the class itself:

```kotlin
fun init() {
    overrideData?.get(this::class.simpleName)?.let { data ->
        overrideWithNewData(data)
    }
}

fun overrideWithNewData(data: ProvidersInfoJson) {
    if (!canBeOverridden) return
    this.name = data.name
    if (data.url.isNotBlank() && data.url != "NONE")
        this.mainUrl = data.url
    this.storedCredentials = data.credentials
}
```
`MainAPI.kt:500-512` [verified]

`init()` is called for all providers in `APIHolder.initAll()` (`MainAPI.kt:117-124` [verified]).
In the current app `overrideData` is **never populated** (only declared+read — repo-wide grep finds no
writer outside this file), so this mechanism is dormant legacy from the old "provider overrides"
settings screen. `[verified: no usages]` The *live* equivalent is the **clone-site** feature (§3).

Minimal real provider (official template):

```kotlin
class ExampleProvider : MainAPI() { // All providers must be an instance of MainAPI
    override var mainUrl = "https://example.com/"
    override var name = "Example provider"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"
    override val hasMainPage = true
    override suspend fun search(query: String): List<SearchResponse> {
        return listOf()
    }
}
```
`research/TestPlugins/ExampleProvider/src/main/kotlin/com/example/ExampleProvider.kt:7-21` [verified]

**Registering a provider** happens from the plugin entry class, not from MainAPI itself:
`registerMainAPI(ExampleProvider())` (`TestPlugins/.../ExamplePlugin.kt:16` [verified]) — see §5.

---

## 2. Every overridable member

Full inventory of `open` members in `MainAPI` (24 properties + 9 functions; every signature below is
copied verbatim from source):

### 2.1 Identity & capability properties (`open val`/`open var`)

#### `name`
```kotlin
/** Name of the plugin that will used in UI */
open var name = "NONE"
```
`MainAPI.kt:514-515` [verified]

- The user-facing provider name AND the internal lookup key: `APIHolder.getApiFromNameNull(apiName)`
  resolves `SearchResponse.apiName` → provider by string equality on `name`
  (`MainAPI.kt:155-165`, used at `app/.../ui/result/ResultViewModel2.kt:2629` [verified]).
- Duplicate names are a real hazard: the active list is de-duplicated with
  `distinctBy { it.lang + it.name + it.mainUrl + it::class.qualifiedName }`
  (`app/.../MainActivity.kt:834-836` [verified]) — two providers with same name+lang+url+class silently drop one.
- Example: `override var name = "Dailymotion"` (`extensions/DailymotionProvider/src/main/kotlin/recloudstream/DailymotionProvider.kt:43` [verified]).

#### `mainUrl`
```kotlin
/** Main Url of the plugin that can be used directly in code or to be replaced using Clone site feature in settings */
open var mainUrl = "NONE"
```
`MainAPI.kt:517-518` [verified]

- Base URL of the site. `fixUrl()` joins relative paths onto it (`MainAPI.kt:742-762` [verified]).
- Also the URL-ownership key: `getApiFromUrlNull(url)` finds the provider whose `mainUrl` is a prefix
  of a URL (`MainAPI.kt:167-172` [verified]) — used when the app opens an external/deep-link URL.
- The app's `load()` driver fixes the incoming url against it before calling the provider
  (`app/.../ui/APIRepository.kt:89` [verified]).
- Example: `override var mainUrl = "https://archive.org"` (`extensions/InternetArchiveProvider/.../InternetArchiveProvider.kt:41` [verified]).

#### `lang`
```kotlin
/**
 * The language as an IETF BCP 47 conformant tag.
 * ...
 */
open var lang = "en"
```
`MainAPI.kt:536-546` [verified]

- IETF BCP 47 tag (e.g. `"en"`, `"uk"`, or `"uni"` for language-agnostic — the Twitch provider uses
  `"uni"`, `extensions/TwitchProvider/.../TwitchProvider.kt:32` [verified]).
- Special constant `AllLanguagesName = "universal"` exists for the "all languages" preference
  (`MainAPI.kt:91` [verified]).
- Drives the language filter on the home screen:
  `apis.filter { api -> (hasUniversal || langs.contains(api.lang)) && ... }`
  (`app/.../ui/utils/AppContextUtils.kt:472` [verified]).
- plugins.json's `language` field is build metadata only; at runtime this property is what the app
  reads. `[inferred from usage]`

#### `storedCredentials` / `canBeOverridden`
```kotlin
open var storedCredentials: String? = null
open var canBeOverridden: Boolean = true
```
`MainAPI.kt:519-520` [verified]

- Legacy of the removed **login/credentials** system. `storedCredentials` has no reader in the app
  today (repo-wide grep: only writer is `overrideWithNewData`, `MainAPI.kt:511`) `[verified]`.
- `canBeOverridden = false` is set by the app on **cloned providers** (§3) so a clone keeps its
  user-chosen name/url (`app/.../MainActivity.kt:827` [verified]).
- **No `getLoginInfo()`/`login()` exist in current MainAPI** — that part of the old API was removed;
  auth now lives only in the app's internal sync-provider `AuthAPI` (`app/.../syncproviders/AuthAPI.kt:197-218` [verified]),
  which plugins do NOT extend. `[verified]` This is a major difference from what older CS3 docs/blog posts describe.

#### Capability flags (all `open val`, all default to the "safe" value)
```kotlin
/**If link is stored in the "data" string, so links can be instantly loaded*/
open val instantLinkLoading = false

/**Set false if links require referer or for some reason cant be played on a chromecast*/
open val hasChromecastSupport = true

/**If all links are encrypted then set this to false*/
open val hasDownloadSupport = true

/**Used for testing and can be used to disable the providers if WebView is not available*/
open val usesWebView = false

open val hasMainPage = false
open val hasQuickSearch = false
```
`MainAPI.kt:548-564` [verified]

- `hasMainPage` — gate for the provider appearing on the home page:
  home list filters `it.hasMainPage && ...` (`app/.../ui/home/HomeFragment.kt:494` [verified]);
  `HomeViewModel` requires it (`app/.../ui/home/HomeViewModel.kt:329` [verified]). If you set it
  `true` without implementing `getMainPage`, the app shows an error — and the built-in test suite
  explicitly fails: `fail("Provider marked as hasMainPage, while in reality is has not been implemented")`
  (`app/.../utils/TestingUtils.kt:94` [verified]).
- `hasQuickSearch` — gate for the lightweight as-you-type search:
  provider filter `(!isQuickSearch || a.hasQuickSearch)` then calls `a.quickSearch(query)`
  (`app/.../ui/search/SearchViewModel.kt:233-235` [verified]).
- `hasChromecastSupport` — read on the result page to decide cast options
  (`app/.../ui/result/ResultFragmentPhone.kt:664` [verified]).
- `hasDownloadSupport` — controls download buttons in the result page/episode list
  (`app/.../ui/result/ResultFragmentPhone.kt:534`, `app/.../ui/result/EpisodeAdapter.kt:159` [verified]).
- `usesWebView` — in current app code only referenced by an (commented-out) instrumented test
  filter (`app/src/androidTest/.../ExampleInstrumentedTest.kt:55` [verified]) and set `true` by
  `CrossTmdbProvider` (`metaproviders/CrossTmdbProvider.kt:27` [verified]). It is advisory metadata
  today; the doc comment ("disable if WebView not available") describes intent, not wired-up
  behavior. `[verified — no runtime consumer found]`
- `instantLinkLoading` — **declared but completely unused** in app+library (single repo-wide hit is
  the declaration itself, `MainAPI.kt:549`) `[verified]`. Do not bother setting it.

#### Content-type & sync metadata
```kotlin
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
```
`MainAPI.kt:604-627` [verified]

- `supportedTypes` — which `TvType`s the provider offers; used for home-screen category filtering
  (`app/.../ui/home/HomeFragment.kt:494-495`: `it.supportedTypes.any { type -> ... }` [verified]).
  Default covers movie/series/cartoon/anime/OVA; video-site providers narrow it, e.g.
  `override val supportedTypes = setOf(TvType.Live)` (`extensions/TwitchProvider/.../TwitchProvider.kt:30` [verified]).
- `vpnStatus` — `VPNStatus.None | MightBeNeeded | Torrent` (enum at `MainAPI.kt:892-897` [verified]);
  the result page shows a warning banner accordingly
  (`app/.../ui/result/ResultViewModel2.kt:336-340` [verified]).
- `providerType` — `ProviderType.MetaProvider | DirectProvider` (enum at `MainAPI.kt:881-890` [verified]).
  MetaProviders (TMDb/Trakt/MDL wrappers) get a "meta provider" label on the result page
  (`app/.../ui/result/ResultViewModel2.kt:342-343` [verified]) and are kept out of the playable
  provider list — they only resolve metadata and delegate playback to real providers.
- `supportedSyncNames` — set of `SyncIdName` (MAL/Anilist/…) IDs the provider can turn into a load
  URL via `getLoadUrl()` (§2.12). Consumed by `SyncRedirector` (§7) and the library list
  (`app/.../ui/library/LibraryFragment.kt:218` [verified]).

### 2.2 Timing / pacing properties

```kotlin
/** if this is turned on then it will request the homepage one after the other,
used to delay if they block many request at the same time*/
open var sequentialMainPage: Boolean = false

/** in milliseconds, this can be used to add more delay between homepage requests
 *  on first load if sequentialMainPage is turned on */
open var sequentialMainPageDelay: Long = 0L

/** in milliseconds, this can be used to add more delay between homepage requests when scrolling */
open var sequentialMainPageScrollDelay: Long = 0L

/** used to keep track when last homepage request was in unixtime ms */
var lastHomepageRequest: Long = 0L
```
`MainAPI.kt:522-534` [verified]

- Anti-rate-limit knobs. If `sequentialMainPage = true` the app calls `getMainPage` for each
  `mainPage` entry one-by-one, sleeping `sequentialMainPageDelay` between them; otherwise all rows
  are fetched concurrently (`app/.../ui/APIRepository.kt:169-191` [verified]).
- `sequentialMainPageScrollDelay` is honored between scroll-triggered page loads via
  `waitForHomeDelay()` which delays `lastHomepageRequest + scrollDelay - now`
  (`app/.../ui/APIRepository.kt:150-154` [verified]).
- `lastHomepageRequest` is **not open** — app-internal bookkeeping, don't touch.

Per-method timeout hints (all `open val Long? = null`, all only hints):

```kotlin
open val loadLinksTimeoutMs: Long? = null     // MainAPI.kt:566-573
open val getMainPageTimeoutMs: Long? = null   // MainAPI.kt:575-580
open val searchTimeoutMs: Long? = null        // MainAPI.kt:582-587
open val quickSearchTimeoutMs: Long? = null   // MainAPI.kt:589-594
open val loadTimeoutMs: Long? = null          // MainAPI.kt:596-601
```
`MainAPI.kt:566-601` [verified]

- The app coerces every hint into `[5 s, 8 min]` around a 2-minute default:
  `DEFAULT_TIMEOUT = 120_000L`, `MAX_TIMEOUT = 4 * DEFAULT_TIMEOUT`, `MIN_TIMEOUT = 5_000L`,
  `getTimeout() = (desired ?: DEFAULT).coerceIn(MIN, MAX)` (`app/.../ui/APIRepository.kt:28-64` [verified]).
- "Note that this is only a hint, and may not get respected if you request something too long"
  (KDoc, `MainAPI.kt:571-572` [verified]) — the hard cap is 8 minutes, whatever you ask.
- This is a **provider-declared timeout** model — nothing like it exists in aniyomi's AnimeSource. `[inferred comparison]`

### 2.3 `mainPage` + the `mainPageOf` DSL

```kotlin
open val mainPage = listOf(MainPageData("", "", false))
```
`MainAPI.kt:629-630` [verified]

The property declares the **rows of the provider's home page**. Each entry becomes one horizontal
shelf / tab in the home UI and one `MainPageRequest` passed to `getMainPage()`. `MainPageData` is:

```kotlin
data class MainPageData(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false
)

data class MainPageRequest(
    val name: String,
    val data: String,
    val horizontalImages: Boolean,
    //TODO genre selection or smth
)
```
`MainAPI.kt:410-421` [verified]

- `name` — row label shown in UI; `data` — opaque provider-defined token (a URL, a category id, …);
  `horizontalImages` — use wide landscape cards for this row.
- The `//TODO genre selection or smth` comment is the only trace of a filter/genre system — see
  Pitfalls below.

Two top-level DSL helpers build the list (not members — free functions in the same package):

```kotlin
fun mainPage(url: String, name: String, horizontalImages: Boolean = false): MainPageData

fun mainPageOf(vararg elements: MainPageData): List<MainPageData>
fun mainPageOf(vararg elements: Pair<String, String>): List<MainPageData>   // url to name pairs
```
`MainAPI.kt:423-442` [verified]

Real example (URL→name pairs):
```kotlin
override val mainPage = mainPageOf(
    "https://twitchtracker.com/games/509658" to "Just Chatting",
    "https://twitchtracker.com/games/21779" to "League of Legends",
    ...
)
```
`extensions/TwitchProvider/.../TwitchProvider.kt:37-42` [verified]

**Pitfalls**
- Default `mainPage` is one empty entry — with `hasMainPage = true` but default `mainPage` the app
  calls `getMainPage` with a garbage empty request. Always set both together. `[inferred]`
- The old csdocs tutorial shows `mainPageOf(Pair("1", "Recent Release - Sub"), …)` — string ids,
  not URLs — which is legal; `data` is whatever you want (`csdocs/devs/create-your-own-providers.md:82-87` [docs]).
- **No filter system exists** (no `getFilterList`/`AnimeFilterList` analog — repo-wide grep for
  `getFilterList|FilterList|class Filter` in library commonMain: zero hits [verified]). A
  `GenreSelector`/`TagSelector` DSL exists only as a commented-out WIP block
  (`MainAPI.kt:333-383` [verified]). Providers that need genre browsing encode genres into
  `mainPage` rows. Big difference from aniyomi.

### 2.4 `getMainPage`

```kotlin
// @WorkerThread
open suspend fun getMainPage(
    page: Int,
    request: MainPageRequest,
): HomePageResponse? {
    throw NotImplementedError()
}
```
`MainAPI.kt:632-638` [verified]

- **Purpose**: fill one home-page row. The app iterates your `mainPage` entries and calls this once
  per (page, entry). The `@WorkerThread` comment says "runs on a background worker" `[docs-ish, comment verified]`.
- **When called**: on home refresh / infinite scroll — `APIRepository.getMainPage(page, nameIndex)`
  builds `MainPageRequest(data.name, data.data, data.horizontalImages)` from your `mainPage` entry
  and invokes this (`app/.../ui/APIRepository.kt:156-196` [verified]). With `sequentialMainPage`
  entries run serially, else concurrently in `async` blocks (same file [verified]).
- **Parameters**: `page: Int` starts at 1 and increments as the user scrolls
  (`csdocs/devs/create-your-own-providers.md:99` [docs]); `request` mirrors the `mainPage` entry.
- **Returns**: `HomePageResponse?` — list of `HomePageList(name, list, isHorizontalImages)` +
  `hasNext` pagination flag. Build it with the free functions `newHomePageResponse(...)`
  (`MainAPI.kt:444-476` [verified]) — the raw constructor is `@Deprecated(level = ERROR)`.
- `hasNext` defaults to `list.isNotEmpty()` in the builders (`MainAPI.kt:452,464` [verified]) —
  returning an empty page naturally stops pagination.
- Default implementation **throws** — override when `hasMainPage = true`.

Real example:
```kotlin
override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val response = app.get("$mainUrl/videos?fields=id,title,thumbnail_360_url&limit=26&page=$page").text
    val popular = tryParseJson<VideoSearchResponse>(response)?.list ?: emptyList()
    return newHomePageResponse(
        listOf(HomePageList("Popular", popular.map { it.toSearchResponse(this) }, true))
    )
}
```
`extensions/DailymotionProvider/.../DailymotionProvider.kt:50-63` [verified]

**Pitfalls**
- Returning `null` vs throwing: the app wraps in `safeApiCall` so exceptions become error
  `Resource`s shown in UI — prefer `throw ErrorLoadingException("...")` with a message over `null`
  for user-visible failures (tutorial explicitly blesses throwing:
  `csdocs/devs/create-your-own-providers.md:164-165` [docs]).
- One call per row per page — a provider with 6 rows and page=2 triggers up to 6 requests;
  rate-limit-sensitive sites must use `sequentialMainPage` + delays (§2.2). `[inferred]`

### 2.5 `search(query, page)` — paginated

```kotlin
/** Paginated search, starts with page: 1 */
open suspend fun search(query: String, page: Int): SearchResponseList? {
    val searchResults = search(query) ?: return null
    return newSearchResponseList(searchResults, false)
}
```
`MainAPI.kt:640-648` [verified]

- **Purpose**: the *modern* search entry point with pagination. **Has a real default implementation**
  that delegates to the non-paginated `search(query)` and reports `hasNext = false`.
- **When called**: main search flow — `api.search(query, page)` inside a
  `withTimeout(getTimeout(api.searchTimeoutMs))` (`app/.../ui/APIRepository.kt:123-134` [verified]);
  the search view always calls this overload: `a.search(query, 1)`
  (`app/.../ui/search/SearchViewModel.kt:235` [verified]).
- **Returns**: `SearchResponseList?` (items + `hasNext`) — build via `newSearchResponseList(list, hasNext)`
  or `list.toNewSearchResponseList(hasNext)` (`MainAPI.kt:478-491` [verified]); raw constructor
  is `@Deprecated(ERROR)`.

Real example:
```kotlin
override suspend fun search(query: String, page: Int): SearchResponseList? {
    val response = app.get("$mainUrl/advancedsearch.php?q=${query.encodeUri()}+mediatype:(movies OR audio)&...&page=$page&output=json").text
    val res = tryParseJson<SearchResult>(response)
    res?.response?.docs?.map { it.toSearchResponse(this) }?.toNewSearchResponseList()
}
```
`extensions/InternetArchiveProvider/.../InternetArchiveProvider.kt:74-85` [verified]

**Pitfalls**
- If you only override the old `search(query)`, pagination silently doesn't work (default wraps
  with `hasNext = false`) — fine for small sites, surprising for big ones. `[inferred]`
- Empty query never reaches the provider: the app short-circuits
  (`APIRepository.kt:124-125` returns empty success [verified]).

### 2.6 `search(query)` — non-paginated

```kotlin
// @WorkerThread
open suspend fun search(query: String): List<SearchResponse>? {
    throw NotImplementedError()
}
```
`MainAPI.kt:650-653` [verified]

- Legacy single-page search. Called directly only by the old code paths; today it's reached through
  the paginated default (§2.5) unless you override the paginated one. Still the only search
  override in several community providers. `[verified usage pattern]`
- **Returns**: `List<SearchResponse>?` — plain list, no pagination info.

Real example (template default shape):
```kotlin
override suspend fun search(query: String): List<SearchResponse>? {
    val document = app.get("$mainUrl/search", params = mapOf("q" to query), referer = mainUrl).document
    return document.select("table.tops tr").map { it.toLiveSearchResponse() }
}
```
`extensions/TwitchProvider/.../TwitchProvider.kt:130-134` [verified]

### 2.7 `quickSearch`

```kotlin
// @WorkerThread
open suspend fun quickSearch(query: String): List<SearchResponse>? {
    throw NotImplementedError()
}
```
`MainAPI.kt:655-658` [verified]

- **Purpose**: fast, lightweight search for the as-you-type quick-search overlay — typically fewer
  results, cheaper request than full `search`.
- **When called**: only if `hasQuickSearch = true` (§2.1); the app wraps it and converts the list
  into a non-paginated `SearchResponseList` (`app/.../ui/APIRepository.kt:136-148` [verified]).
- **Returns**: `List<SearchResponse>?` (never paginated).

Real example — the common community pattern of delegating to full search:
```kotlin
override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)
```
`research/CakesTwix-ext/BambooUAProvider/src/main/kotlin/com/lagradost/BambooUAProvider.kt:100` [verified]

**Pitfalls**
- The official YouTube provider sets `override val hasQuickSearch = true`
  (`extensions/YoutubeProvider/.../YoutubeProvider.kt:16` [verified]) but **does not override
  `quickSearch`** anywhere in that file [verified] — with the default throwing
  `NotImplementedError`, quick search on that provider can only fail. Setting the flag without the
  override is evidently easy to do wrong even upstream. `[inferred hazard]`
- csdocs describes quick search as "largely redundant"; many providers just alias it to `search`.

### 2.8 `load`

```kotlin
// @WorkerThread
/**
 * Based on data from search() or getMainPage() it generates a LoadResponse,
 * basically opening the info page from a link.
 * */
open suspend fun load(url: String): LoadResponse? {
    throw NotImplementedError()
}
```
`MainAPI.kt:660-667` [verified]

- **Purpose**: the detail/info page. Given the `url` you put in a `SearchResponse`, produce the full
  `LoadResponse` (metadata, episodes, and the opaque `dataUrl` payload later fed to `loadLinks`).
- **When called**: user opens any search/home result. The app first runs `api.fixUrl(url)`,
  checks a 10-minute rolling cache (20 entries) keyed `(api.name, fixedUrl)`, then calls under
  `withTimeout(api.loadTimeoutMs)`; a `null` return is converted to `ErrorLoadingException`
  (`app/.../ui/APIRepository.kt:85-121` [verified]). Blank data (`""`, `"[]"`, `"about:blank"`)
  never reaches the provider (`APIRepository.kt:48-50` [verified]).
- **Returns**: `LoadResponse?` — one of `MovieLoadResponse` / `TvSeriesLoadResponse` /
  `AnimeLoadResponse` / `LiveStreamLoadResponse` / `TorrentLoadResponse`, built with the
  `newMovieLoadResponse`/`newTvSeriesLoadResponse`/… builders (§5.4; details in doc 05).

Real example:
```kotlin
override suspend fun load(url: String): LoadResponse? {
    val videoId = Regex("dailymotion.com/video/([a-zA-Z0-9]+)").find(url)?.groups?.get(1)?.value
    val response = app.get("$mainUrl/video/$videoId?fields=id,title,description,thumbnail_720_url").text
    val videoDetail = tryParseJson<VideoDetailResponse>(response) ?: return null
    return videoDetail.toLoadResponse(this)   // → newMovieLoadResponse(title, url, TvType.Movie, id)
}
```
`extensions/DailymotionProvider/.../DailymotionProvider.kt:73-78, 90-100` [verified]

**Pitfalls**
- "Episodes in CloudStream are not paginated, meaning that if you have a show with 21 seasons, all
  on different website pages you will need to parse them all." (`csdocs/devs/create-your-own-providers.md:149` [docs])
- The 10-min cache means a provider bug can appear "sticky" during debugging. `[inferred]`
- The app strips blank tags from the response after you return (`response.tags?.filter { it.isNotBlank() }`, `APIRepository.kt:106-107` [verified]).

### 2.9 `loadLinks`

```kotlin
/**Callback is fired once a link is found, will return true if method is executed successfully
 * @param data dataUrl string returned from [load] function.
 * @see newMovieLoadResponse
 * @see newTvSeriesLoadResponse
 * @see newLiveStreamLoadResponse
 * @see newAnimeLoadResponse
 * @see newTorrentLoadResponse
 * */
// @WorkerThread
open suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    throw NotImplementedError()
}
```
`MainAPI.kt:683-699` [verified]

- **Purpose**: resolve playable streams. This is the heart of the system — the tutorial's rule #0:
  "it is important that you are confident you can scrape the video links first! … if you cannot
  scrape them then the provider is useless." (`csdocs/devs/create-your-own-providers.md:16-17` [docs])
- **When called**: when the user presses play (or starts a download/preview). The player's
  link generator calls `APIRepository.loadLinks(data, isCasting, subtitleCallback, callback)`
  (`app/.../ui/player/RepoLinkGenerator.kt:107-134` [verified]) which wraps your call in
  `withTimeout(getTimeout(api.loadLinksTimeoutMs))` and swallows all throwables into `false`
  (`app/.../ui/APIRepository.kt:204-219` [verified]).
- **Parameters**:
  - `data` — the `dataUrl` you attached in `load()` (e.g. `MovieLoadResponse.dataUrl`). Because
    `newMovieLoadResponse(name, url, type, data: T?)` JSON-serializes arbitrary objects
    (`MainAPI.kt:2491-2519` [verified]), `data` is a provider-private channel from `load` to
    `loadLinks` — commonly a JSON payload of embed URLs. The app rejects blank data before calling
    (`APIRepository.kt:210` [verified]).
  - `isCasting` — `true` when playing via Chromecast: hide links that need headers/referers the
    cast receiver can't send (this is what `hasChromecastSupport` advertises).
  - `subtitleCallback` — call for every subtitle track (`SubtitleFile(lang, url, headers)`,
    `MainAPI.kt:1199-1236` [verified]; build via `newSubtitleFile`).
  - `callback` — call for every resolved stream (`ExtractorLink`, §6.3).
- **Returns**: `Boolean` — whether the method "executed successfully" (i.e. produced anything);
  used by `RepoLinkGenerator` as a completion signal. Links already streamed through the callbacks
  remain usable even if you ultimately return `false`/throw. `[inferred from RepoLinkGenerator behavior]`
- **Streaming model**: callbacks fire as links are discovered — the player shows sources while
  extraction continues. Nothing like aniyomi's "return List<Video>". `[inferred comparison]`

Real examples — both canonical patterns:

(a) delegate to a registered extractor:
```kotlin
override suspend fun loadLinks(data: String, isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
    loadExtractor("https://www.dailymotion.com/embed/video/$data", subtitleCallback, callback)
    return true
}
```
`extensions/DailymotionProvider/.../DailymotionProvider.kt:102-114` [verified]

(b) emit direct links from a JSON payload prepared in `load()`:
```kotlin
override suspend fun loadLinks(data: String, isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
    val load = tryParseJson<LoadData>(data)
    if (load?.type == "video-playlist") {
        distinctURLData.sortedByDescending { it.size }.forEach { urlData ->
            callback(newExtractorLink(this.name, getName(urlData.format), urlData.url) {
                quality = urlData.quality
                referer = ""
            })
        }
    } else { loadExtractor("https://archive.org/details/$data", subtitleCallback, callback) }
    ...
}
```
`extensions/InternetArchiveProvider/.../InternetArchiveProvider.kt:418-454` [verified]

**Pitfalls**
- Timeout default is 120 s (hard cap 480 s) — long-running extraction chains must be split or must
  raise `loadLinksTimeoutMs` (§2.2). `[verified constants]`
- Every callback emission is deduplicated upstream by URL (`currentLinksUrls` set,
  `RepoLinkGenerator.kt:66-88` [verified]) — duplicates are safe but wasted.
- Extractor output is cached ~20 min per (apiName, id) and playback resumes from cache
  (`RepoLinkGenerator.kt:71-105` [verified]).

### 2.10 `extractorVerifierJob`

```kotlin
/**
 * Largely redundant feature for most providers.
 *
 * This job runs in the background when a link is playing in exoplayer.
 * First implemented to do polling for sflix to keep the link from getting expired.
 *
 * This function might be updated to include exoplayer timestamps etc in the future
 * if the need arises.
 * */
// @WorkerThread
open suspend fun extractorVerifierJob(extractorData: String?) {
    throw NotImplementedError()
}
```
`MainAPI.kt:669-681` [verified]

- **Purpose**: keep-alive polling while a stream plays (e.g. re-send heartbeat so the site doesn't
  expire the URL).
- **When called**: during playback for links that carry an `extractorData` payload:
  `getApiFromNameNull(link.source)?.extractorVerifierJob(link.extractorData)`
  (`app/.../ui/player/GeneratorPlayer.kt:264` [verified]); also by the download manager
  (`app/.../utils/downloader/DownloadManager.kt:1493` [verified]). The `extractorData` string is a
  field you set on `ExtractorLink` precisely for this job (§6.3).
- The app side wraps in `safeApiCall` — throwing is contained (`APIRepository.kt:198-202` [verified]).
- No workspace provider overrides it (grep across `research/` = zero plugin hits) `[verified]`.
  Default throws, so don't set `extractorData` unless you implement it. `[inferred]`

### 2.11 `getVideoInterceptor`

```kotlin
/** An okhttp interceptor for used in OkHttpDataSource */
open fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? {
    return null
}
```
`MainAPI.kt:701-704` [verified]

- **Purpose**: attach an OkHttp `Interceptor` to the player's `OkHttpDataSource` for this provider's
  stream (e.g. token refresh, header signing per request).
- **When called**: when building the playback data source —
  `val interceptor: Interceptor? = provider?.getVideoInterceptor(link)`
  (`app/.../ui/player/CS3IPlayer.kt:1941` [verified]).
- **Not suspend** — the only non-suspend overridable function besides nothing else (all others are
  suspend or properties). Return `null` (default) for no interceptor.
- No workspace plugin overrides it (grep = zero hits in `research/`) `[verified]`. The pattern is
  known from the built-in `MyDramaList` interceptor *style* (a private `Interceptor` used on `app.get`
  instead — `metaproviders/MyDramaList.kt:57-68` [verified]).

### 2.12 `getLoadUrl`

```kotlin
/**
 * Get the load() url based on a sync ID like IMDb or MAL.
 * Only contains SyncIds based on supportedSyncUrls.
 **/
open suspend fun getLoadUrl(name: SyncIdName, id: String): String? {
    return null
}
```
`MainAPI.kt:706-712` [verified]

- **Purpose**: map a 3rd-party ID (MAL id, Anilist id) to a page URL on your site, so the app can
  deep-launch your provider from a library/sync entry.
  KDoc example flow: `"tt6723592" -> getLoadUrl(ImdbSyncId("tt6723592")) -> "mainUrl/imdb/tt6723592" -> load(...)`
  (`MainAPI.kt:604-615` [verified]).
- **When called**: by `SyncRedirector.redirect(url, providerApi)` when a MAL/Anilist URL is opened
  and `providerApi.supportedSyncNames` contains the matching `SyncIdName`
  (`metaproviders/SyncRedirector.kt:44-52` [verified]); also from the library list flow
  (`app/.../ui/library/LibraryFragment.kt:218` [verified]).
- **Returns**: URL string or `null` (default) — a null/absent mapping just means "not supported".
- No workspace plugin overrides it (grep = zero plugin hits) `[verified]`. Effectively a metaprovider
  facility; ANI-KUTA can ignore it initially. `[inferred]`

---

## 3. Provider identity & metadata fields

Runtime identity of a provider = the **`name` string** (not class, not plugin):

- `APIHolder.apis` + `apiMap: Map<String, Int>` (name→index) is the lookup:
  `getApiFromNameNull(apiName)` (`MainAPI.kt:131-165` [verified]).
- Every `SearchResponse`/`LoadResponse` carries `apiName` so results can be routed back to the
  provider after process death / from persisted watch data.
- `mainUrl` is a secondary identity for URL routing (`getApiFromUrlNull`, `MainAPI.kt:167-172` [verified]).
- `sourcePlugin: String?` — **not open**; set by the loader at registration to the plugin file path
  (`plugins/BasePlugin.kt:22`: `element.sourcePlugin = this.filename` [verified]) so the app can
  unload/disable a plugin's providers. A plugin-identity back-reference, not provider identity.

**Clone sites** (the live replacement for the dead `overrideData` system): after all plugins load,
the app reads `Array<SettingsGeneral.CustomSite>` from storage and instantiates *additional* provider
copies via reflection:

```kotlin
allProviders.firstOrNull { it::class.simpleName == custom.parentClassName }?.let {
    allProviders.add(
        it::class.createInstance().apply {
            name = custom.name
            lang = custom.lang
            mainUrl = custom.url.trimEnd('/')
            canBeOverridden = false
        }
    )
}
// it.hashCode() is not enough to make sure they are distinct
apis = allProviders.distinctBy {
    it.lang + it.name + it.mainUrl + it::class.qualifiedName
}
```
`app/.../MainActivity.kt:810-839` [verified]

Implications: (a) providers must be instantiable with a **no-arg constructor** (same requirement as
the plugin entry class); (b) property-override style is what makes cloning work — the constructor-less
design is deliberate. `[inferred]`

Other metadata the class carries: `providerType`, `vpnStatus`, `supportedTypes`, `lang` (all §2.1).
There is **no** `mainImageUrl`, `posterHeaders`, `headers` property in current MainAPI (the old
`headers`/`mainImageUrl` members of pre-2023 CloudStream are gone; per-request headers are passed to
`app.get(...)` directly, and poster headers ride on the response objects:
`SearchResponse.posterHeaders` at `MainAPI.kt:1412`, `LoadResponse.posterHeaders` at
`MainAPI.kt:1832` [verified]).

---

## 4. Rank & sorting

**There is no `rank` / `rankPriority` field.** Repo-wide grep for `rankPriority|rank` as a provider
property: zero hits in `library/` (the only `rank` variable in a provider is a local Twitch
rank-badge scrape, `extensions/TwitchProvider/.../TwitchProvider.kt:105` [verified]). `[verified]`

Ordering is purely:

1. **Registration order** — `APIHolder.allProviders` is an append-only atomic list;
   `registerMainAPI` adds (`MainAPI.kt:115`, `plugins/BasePlugin.kt:20-25` [verified]). Plugin load
   order is repo-order then sideloads (`app/.../plugins/PluginManager.kt`, doc 13 covers this).
2. **User pinning/filtering** in the home UI (pinned providers first, then language/type filters —
   `app/.../ui/home/HomeFragment.kt:494` [verified]).
3. **Extractor priority is REVERSED registration**: `loadExtractor` iterates
   `for (index in extractorApis.lastIndex downTo 0)` — "Iterate in reverse order so the new
   registered ExtractorApi takes priority" (`library/.../utils/ExtractorApi.kt:944-947` [verified]).
   Plugin-registered extractors are appended after the 97 built-ins, so a plugin's custom extractor
   shadows a built-in with the same `mainUrl`. Important for us. `[verified]`
4. Link *quality* ordering is separate: `sortUrls` sorts `ExtractorLink`s by descending `quality`
   (`MainAPI.kt:769-771` [verified]) and `Qualities.defaultPriority` exists for tie-breaking
   (`ExtractorApi.kt:849-858` [verified]).

For ANI-KUTA: we should NOT copy a rank concept from aniyomi expectations — CS3 simply doesn't have
provider ranks; if we need deterministic ordering we must impose it ourselves (e.g. repo order +
name). `[inferred]`

---

## 5. Helpers available inside a plugin

### 5.1 The plugin entry classes (`BasePlugin` / app-side `Plugin`)

```kotlin
abstract class BasePlugin {
    fun registerMainAPI(element: MainAPI)            // BasePlugin.kt:20-25
    fun registerExtractorAPI(element: ExtractorApi)  // BasePlugin.kt:31-35
    @Throws(Throwable::class) open fun beforeUnload() {}   // BasePlugin.kt:40-42
    @Throws(Throwable::class) open fun load() {}            // BasePlugin.kt:47-49
    var filename: String? = null                     // BasePlugin.kt:62
    class Manifest { name; pluginClassName; requiresResources; version }  // BasePlugin.kt:64-77
}
```
`library/src/commonMain/kotlin/com/lagradost/cloudstream3/plugins/BasePlugin.kt:14-78` [verified]

```kotlin
abstract class Plugin : BasePlugin() {
    open fun load(context: Context) {   // If not overridden then try the cross-platform load()
        load()
    }
    fun registerVideoClickAction(element: VideoClickAction) { ... }  // Plugin.kt:24-29
    var resources: Resources? = null    // from requiresResources, Plugin.kt:34
    var openSettings: ((context: Context) -> Unit)? = null  // Plugin.kt:38-39
}
```
`app/src/main/java/com/lagradost/cloudstream3/plugins/Plugin.kt:10-40` [verified]

- `registerMainAPI` also stamps `element.sourcePlugin = this.filename` and updates the name→api map
  (`BasePlugin.kt:21-24` + `MainAPI.kt:134-139` [verified]).
- `registerVideoClickAction` registers long-press video actions (an app-side extension point;
  `app/.../actions/VideoClickAction.kt` — beyond MainAPI, noted for completeness). `[verified]`
- `openSettings` adds a settings button rendered by the app; the plugin shows its own Fragment
  (see `TestPlugins/.../ExamplePlugin.kt:18-23` [verified]).
- **There is NO preferences API in the current library** — no `PluginPreferences`/`ProviderSettings`
  class exists (repo-wide grep = zero hits) `[verified]`. Plugins that need settings use
  `openSettings` + their own storage. (Doc 11 covers settings; the old preference DSL known from
  CS3 4.0-era docs is gone at this commit.)

### 5.2 How the plugin class is instantiated [verified]

`PluginManager.loadPlugin` (`app/.../plugins/PluginManager.kt:593-676`):

1. `PathClassLoader(filePath, context.classLoader)` — parent-first (line 611).
2. `manifest.json` read as a **classloader resource**; parsed into `BasePlugin.Manifest` (612-621).
3. `loader.loadClass(manifest.pluginClassName)` → `pluginClass.getDeclaredConstructor().newInstance()` —
   **no-arg constructor**, no args passed (630-634).
4. `pluginInstance.filename = file.absolutePath` (644); if `requiresResources`, an `AssetManager`
   is bolted on via reflection and `resources` set (645-659).
5. `pluginInstance.load(context)` for `Plugin` subclasses, else `pluginInstance.load()` (669-673).

The `@CloudstreamPlugin` annotation is field-less and only a build-time marker
(`library/.../plugins/CloudstreamPlugin.kt:3-5` [verified]); the loader never reads it.

### 5.3 Networking: the global `app` (NOT a plugin member)

Plugins do HTTP through the library-global NiceHttp client — an ordinary top-level property they
import (`import com.lagradost.cloudstream3.app`):

```kotlin
/** The default networking helper. This helper performs SSL checks.
 * If you need to make requests to websites with invalid SSL certificates use insecureApp instead. */
var app = Requests(responseParser = jsonResponseParser).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

/** Same as the default app networking helper, but this instance ignores SSL certificates.
 * This should NEVER be used for sensitive networking operations such as logins. */
@UnsafeSSL
var insecureApp = Requests(responseParser = jsonResponseParser).apply { ... }
```
`library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainActivity.kt:28-39` [verified]
(yes — the *library* file is named MainActivity.kt; there is no Activity in it)

`app` is `com.lagradost.nicehttp.Requests` with a Jackson-backed `ResponseParser`. Surface as used
throughout the library and providers (cited call sites): `app.get(url, params=, headers=, referer=,
cacheTime=, interceptor=)`, `app.post(url, data=, requestBody=)`, `app.head(url, headers=, referer=,
timeout=)`, and response conveniences `.text`, `.url`, `.document` (jsoup), `.parsed<T>()`,
`.parsedSafe<T>()` (e.g. `MainAPI.kt:195-218, 326-329`; `metaproviders/MyDramaList.kt:80-83`;
`extensions/TwitchProvider/.../TwitchProvider.kt:100-132, 162` [verified]). Default UA is a desktop
Chrome string constant `USER_AGENT` (`MainAPI.kt:93-94` [verified]).
`insecureApp` requires opting into the `@UnsafeSSL` annotation (`MainAPI.kt:74-79` [verified]).

### 5.4 The `new*` builder functions (the only legal way to construct results)

All result data classes have `@Deprecated(level = ERROR)` constructors; providers use these
extension/library functions instead (each runs your initializer lambda then post-processes):

| Builder | Builds | Where |
|---|---|---|
| `newMovieSearchResponse(name, url, type, fix, initializer)` | `MovieSearchResponse` | MainAPI.kt:1438-1450 |
| `newTvSeriesSearchResponse(...)` | `TvSeriesSearchResponse` | MainAPI.kt:1470-1482 |
| `newAnimeSearchResponse(...)` | `AnimeSearchResponse` | MainAPI.kt:1484-1496 |
| `newLiveSearchResponse(...)` | `LiveSearchResponse` | MainAPI.kt:1452-1468 |
| `newTorrentSearchResponse(...)` | `TorrentSearchResponse` | MainAPI.kt:1418-1436 |
| `newMovieLoadResponse(name, url, type, data: T?/String, initializer)` | `MovieLoadResponse` | MainAPI.kt:2491-2539 |
| `newTvSeriesLoadResponse(name, url, type, episodes, initializer)` | `TvSeriesLoadResponse` | MainAPI.kt:2728-2746 |
| `newAnimeLoadResponse(name, url, type, comingSoonIfNone, initializer)` | `AnimeLoadResponse` | MainAPI.kt:2390-2409 |
| `newLiveStreamLoadResponse(name, url, dataUrl, initializer)` | `LiveStreamLoadResponse` | MainAPI.kt:2442-2458 |
| `newTorrentLoadResponse(name, url, magnet, torrent, initializer)` | `TorrentLoadResponse` | MainAPI.kt:2295-2315 |
| `newEpisode(url/data: T, initializer, fix)` | `Episode` | MainAPI.kt:2624-2652 |
| `newSubtitleFile(lang, url, initializer)` | `SubtitleFile` | MainAPI.kt:1224-1236 |
| `newHomePageResponse(name/data/list/lists, list, hasNext)` | `HomePageResponse` | MainAPI.kt:444-476 |
| `newSearchResponseList(list, hasNext)` / `List.toNewSearchResponseList()` | `SearchResponseList` | MainAPI.kt:478-491 |
| `newExtractorLink(source, name, url, type, initializer)` | `ExtractorLink` | utils/ExtractorApi.kt:500-519 |
| `newDrmExtractorLink(...)` | `DrmExtractorLink` | utils/ExtractorApi.kt:526-569 |

All `[verified]` at cited lines. Notes:
- The search builders auto-fill `apiName = this.name` and run `fixUrl(url)` unless `fix = false`
  (e.g. `MainAPI.kt:1446` [verified]).
- `newMovieLoadResponse` with non-String `data` JSON-serializes it into `dataUrl`
  (`MainAPI.kt:2499-2506` [verified]) — that's the `loadLinks` payload channel.
- `newAnimeLoadResponse(comingSoonIfNone = true)` auto-sets `comingSoon` if no episodes were added
  (`MainAPI.kt:2400-2407` [verified]); movie/series builders auto-set `comingSoon` on blank
  dataUrl/empty episodes (`MainAPI.kt:2515, 2742, 2454` [verified]).
- Enrichment extension functions: `addPoster`, `addQuality` on `SearchResponse`
  (`MainAPI.kt:1498-1512`); the big `LoadResponse.Companion` helper family — `addActors`,
  `addTrailer`, `addScore`, `addDuration`, `addMalId`/`addAniListId`/`addImdbId`/`addTMDbId`/
  `addKitsuId`, `addSeasonNames` (`MainAPI.kt:1853-2094, 2250-2263` [verified]); `Episode.addDate`
  with smart string parsing (`MainAPI.kt:2577-2622` [verified]).

### 5.5 URL & misc utilities [verified]

- `MainAPI.fixUrl(url)` / `fixUrlNull(url)` — join relative→absolute against `mainUrl`; leaves
  `http*`, `//…`, and JSON (`{"`/`[`) untouched (`MainAPI.kt:735-762`).
- `MainAPI.updateUrl(url)` — swap scheme/host/port of an old stored link onto the current `mainUrl`
  (used after clone-site URL changes) (`MainAPI.kt:1371-1403`).
- `imdbUrlToId(url)` — `https://www.imdb.com/title/tt2861424/ → tt2861424` (`MainAPI.kt:861-879`).
- `getDurationFromString`, `getQualityFromString`, `capitalizeString`, `fixTitle`,
  `base64Encode/Decode/DecodeArray`, `fetchUrls`, `isUpcoming` (`MainAPI.kt:815-846, 1322-1368,
  2748-2775`).
- JSON: `AppUtils.toJson/parseJson/tryParseJson` + response `.parsed<T>()/.parsedSafe<T>()`
  (Jackson-based; used at `MainAPI.kt:319-330` and in every provider above).
- JS machinery for obfuscated hosts: `JsUnpacker`/`getAndUnpack` (`utils/JsUnpacker.kt:7`,
  `utils/ExtractorApi.kt:893-901`), `newJsContext`/`evalJs` (Rhino interpreter,
  `utils/JsInterpreter.kt:161, 189`; old `getRhinoContext` deprecated at `MainAPI.kt:848-859`).
- `APIHolder.getTracker(titles, types, year, lessAccurate)` + `getCaptchaToken(url, key, referer)`
  — free Anilist matching and reCAPTCHA-token solving for providers
  (`MainAPI.kt:174-285, 187-226`).

---

## 6. Extractor-side API (`ExtractorApi`)

Extractors are declared via a **different base class** and registered separately. The base lives in
`library/src/commonMain/kotlin/com/lagradost/cloudstream3/utils/ExtractorApi.kt` (the file also hosts
`ExtractorLink`, `Qualities`, `loadExtractor`, and the 97 built-in extractor registrations):

```kotlin
abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    /** Determines which plugin a given provider is from. This is the full path to the plugin. */
    var sourcePlugin: String? = null

    // this is the new extractorapi, override to add subtitles and stuff
    @Throws
    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        getUrl(url, referer)?.forEach(callback)
    }

    suspend fun getSafeUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            getUrl(url, referer, subtitleCallback, callback)
        } catch (e: Exception) {
            logError(e)
        }
    }

    /**
     * Will throw errors, use getSafeUrl if you don't want to handle the exception yourself
     */
    @Throws
    open suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? {
        return emptyList()
    }

    open fun getExtractorUrl(id: String): String {
        return id
    }
}
```
`utils/ExtractorApi.kt:1419-1466` [verified]

Members:
- **`name`, `mainUrl`, `requiresReferer`** — abstract, must be overridden. `mainUrl` is the routing
  key (see dispatch below). `requiresReferer` feeds `requireReferer(name)`
  (`ExtractorApi.kt:1353-1355` [verified]).
- **`getUrl(url, referer, subtitleCallback, callback)`** — the *new* form: override this for
  subtitle+link streaming (its default bridges to the old form by looping the returned list).
- **`getUrl(url, referer): List<ExtractorLink>?`** — the *old* form; default returns empty (not a
  throw, unlike MainAPI).
- **`getSafeUrl(...)`** — final exception-swallowing wrapper (not open, providers never override).
- **`getExtractorUrl(id)`** — build a canonical embed URL from an id (default = id).
- **`sourcePlugin`** — same plugin back-reference as MainAPI, set by
  `registerExtractorAPI` (`plugins/BasePlugin.kt:31-35` [verified]).
- **`ExtractorApi.fixUrl`** — extractor-relative version of the URL fixer
  (`ExtractorApi.kt:1397-1417` [verified]).

**Dispatch** — `loadExtractor(url, referer?, subtitleCallback, callback)`: unshortens the URL, then
iterates the global `extractorApis` list **in reverse registration order** matching
`compareUrl.startsWith(extractor.mainUrl)`; on no exact hit does a fuzzy second pass with
`Levenshtein.partialRatio(extractor.mainUrl, currentUrl) > 80` for mirror domains; returns `true` if
any extractor matched (`ExtractorApi.kt:914-979` [verified]). This is what a provider's `loadLinks`
normally calls (§2.9a). Registration list: `val extractorApis: AtomicMutableList<ExtractorApi> =
atomicListOf( …built-in instances… )` (`ExtractorApi.kt:985` [verified]; the ~97-class count is from
doc 01/B1-a's extractor census).

Real extractor example (built-in, open class so mirror domains subclass cheaply):

```kotlin
open class DoodLaExtractor : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://dood.la"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String, referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = url.replace("/d/", "/e/")
        ...
        callback.invoke(
            newExtractorLink(this.name, this.name, trueUrl) {
                this.referer = "$mainUrl/"
                this.quality = getQualityFromName(quality)
            }
        )
    }
}
class DoodstreamCom : DoodLaExtractor() { override var mainUrl = "https://doodstream.com" }
```
`library/.../extractors/DoodExtractor.kt:95-142, 28-30` [verified]

Plugin-defined extractor example (same .cs3 registers provider + extractor):

```kotlin
class TwitchExtractor : ExtractorApi() {
    override val mainUrl = "https://twitch.tv/"
    override val name = "Twitch"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?, ...) {
        val response = app.get("https://pwn.sh/tools/streamapi.py?url=$url").parsed<ApiResponse>()
        response.urls?.forEach { (name, url) -> ... callback(newExtractorLink(...)) }
    }
}
```
`extensions/TwitchProvider/.../TwitchProvider.kt:145-169` [verified] — registered in
`TwitchPlugin.load()` via `registerExtractorAPI(TwitchExtractor())` (`TwitchPlugin.kt:8+` [verified]).

### 6.3 `ExtractorLink` (the output coin) — brief

```kotlin
open class ExtractorLink(
    open val source: String,        // provider/extractor name — routes extractorVerifierJob
    open val name: String,          // user-facing label
    override val url: String,
    override var referer: String,
    open var quality: Int,          // e.g. Qualities.P1080.value
    override var headers: Map<String, String> = mapOf(),
    open var extractorData: String? = null,  // payload for extractorVerifierJob
    open var type: ExtractorLinkType,        // VIDEO, M3U8, DASH, TORRENT, MAGNET
    open var audioTracks: List<AudioFile> = emptyList(),
) : IDownloadableMinimum
```
`utils/ExtractorApi.kt:700-714` [verified] (constructors deprecated ERROR — use `newExtractorLink`,
`ExtractorApi.kt:500-519` [verified]). `quality` uses the `Qualities` enum ints
(`Unknown=400, P144…P2160`, `ExtractorApi.kt:849-880` [verified]); `getQualityFromName("1080p")`
parses labels (`ExtractorApi.kt:882-891` [verified]). DRM variant: `DrmExtractorLink` +
`newDrmExtractorLink` (`ExtractorApi.kt:526-569, 588+` [verified]). Full field notes → doc 05/08.

---

## 7. Extension points NOT in MainAPI.kt (metaproviders)

Providers may instead extend ready-made subclasses in
`library/.../metaproviders/` (each is itself a MainAPI — registered like any provider when used):

- **`TmdbProvider`** — `open class TmdbProvider : MainAPI()` (`metaproviders/TmdbProvider.kt:53` [verified]).
  A full TMDb metadata provider: `hasMainPage = true`, `providerType = MetaProvider`, built-in API
  key/url (lines 64-68), configurable `includeAdult`, `useMetaLoadResponse`, `apiName`, and
  `disableSeasonZero` (54-62). Plugins subclass it and implement `loadLinks` (search/load/mainPage
  come free) — the standard way to make a "TMDb-powered aggregator" provider. Details in doc 07.
- **`CrossTmdbProvider`** — `class CrossTmdbProvider : TmdbProvider()` (`metaproviders/CrossTmdbProvider.kt:22` [verified]).
  A concrete multi-provider demo ("MultiMovie"): TMDb front-end whose `loadLinks` fans a JSON payload
  out to every other same-language provider's `loadLinks` (lines 44-65). Shows the meta pattern in
  miniature.
- **`TraktProvider`** — `open class TraktProvider : MainAPI()` (`metaproviders/TraktProvider.kt:42-60` [verified]).
  Trakt.tv trending/popular lists via client-id auth from `BuildConfig`; meta provider with its own
  `mainPage = mainPageOf("$traktApiUrl/movies/trending" to "Trending Movies", …)`.
- **`MyDramaListAPI`** — `abstract class MyDramaListAPI : MainAPI()` (`metaproviders/MyDramaList.kt:39-55` [verified]).
  MDL-powered Asian-drama metadata; adds an OkHttp `interceptor` injecting the MDL API key on every
  request (57-68). Abstract — plugins must implement playback.
- **`SyncRedirector`** — `object SyncRedirector` (NOT a provider; a helper object,
  `metaproviders/SyncRedirector.kt:7` [verified]) that turns MAL/Anilist URLs into provider URLs by
  regex + `supportedSyncNames`/`getLoadUrl` (§2.12).

Also outside MainAPI.kt: **`VideoClickAction`** (long-press player actions, registered via
`Plugin.registerVideoClickAction`, `app/.../actions/` [verified]) — plugin-reachable but unrelated
to providers; and the **sync providers** (`SyncRepository`, AuthAPI — app-internal, NOT extendable
by plugins at this commit; plugin-facing sync registration does not exist). `[verified]`

---

## 8. API versioning & compatibility

Two disconnected mechanisms:

1. **Runtime `apiVersion` — dead.** `plugins.json`'s `apiVersion` field is parsed by the app but
   ignored:
   ```kotlin
   // Unused currently, used to make the api backwards compatible?
   // Set to 1
   @JsonProperty("apiVersion") @SerialName("apiVersion") val apiVersion: Int,
   ```
   `app/.../plugins/RepositoryManager.kt:57-59` [verified]. No runtime gate anywhere reads it.

2. **Build-time ABI validation — real.** The library module enables Kotlin binary-compatibility
   validation with an exclusion filter:
   ```kotlin
   @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
   // https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html
   abiValidation {
       filters {
           exclude {
               annotatedWith.add("com.lagradost.cloudstream3.Prerelease")
               annotatedWith.add("com.lagradost.cloudstream3.InternalAPI")
           }
       }
   }
   ```
   `library/build.gradle.kts:88-97` [verified], with the golden ABI dump committed at
   `library/api/jvm/library.api` [verified — file exists, e.g. `public abstract class
   com/lagradost/cloudstream3/MainAPI …` entries]. CI breaks if the public ABI changes incompatibly.

   The opt-out annotations are declared at the top of MainAPI.kt:
   - `@Prerelease` — "API available only on prerelease builds. Using it will cause stable to crash
     with `NoSuchMethodException`" (`MainAPI.kt:52-63` [verified]) — prerelease-only APIs are
     *excluded from the ABI dump*, so stable never sees them (e.g. `newDrmExtractorLink(Uuid)`
     overload is `@Prerelease`, `ExtractorApi.kt:548` [verified]).
   - `@InternalAPI` — "should not be used by extensions… may be changed or removed at any time"
     (`MainAPI.kt:65-72` [verified]).
   - `@UnsafeSSL` — WARNING-level opt-in for `insecureApp` (`MainAPI.kt:74-79` [verified]).

   Additionally the gradle plugin's CI runs `ensureJarCompatibility` when building extension repos
   (found in `extensions/.github/workflows/build.yml` — see doc 02). Library maven version is
   `1.0.1` (`library/build.gradle.kts:21` [verified]).

**Bottom line for ANI-KUTA**: compatibility is enforced *upstream at compile time* (plugins compile
against a specific library snapshot; ABI changes break builds, not installs) — there is **no
analog to aniyomi's `libVersion`/extVersion runtime gate**. If we host CS3 plugins we inherit
"compile-time-only" compat and must decide ourselves whether to add a runtime check. `[inferred]`

---

## 9. Method-by-method cheat table

All signatures from `MainAPI.kt` @ efc1915. "Required" = needed for a functional provider
(nothing is *syntactically* abstract — see §1).

| Member | Kind | Override required? | App calls it when | Returns (default) |
|---|---|---|---|---|
| `name` | `open var` | **YES** | UI, provider lookup by name | `"NONE"` |
| `mainUrl` | `open var` | **YES** | fixUrl, URL routing, clone-site | `"NONE"` |
| `lang` | `open var` | recommended | language filters, clone-site | `"en"` |
| `supportedTypes` | `open val` | recommended | home/category filters | Movie, TvSeries, Cartoon, Anime, OVA |
| `hasMainPage` | `open val` | if browsing | home-screen inclusion | `false` |
| `mainPage` | `open val` | with hasMainPage | builds MainPageRequests | 1 empty entry |
| `getMainPage(page, request)` | `open suspend fun` | with hasMainPage | home refresh / scroll | throws `NotImplementedError` |
| `search(query, page)` | `open suspend fun` | **YES** (one of the two) | search box | delegates to `search(query)` |
| `search(query)` | `open suspend fun` | legacy alternative | via paginated default | throws |
| `hasQuickSearch` | `open val` | optional | quick-search gate | `false` |
| `quickSearch(query)` | `open suspend fun` | with hasQuickSearch | as-you-type search | throws |
| `load(url)` | `open suspend fun` | **YES** | opening any result | throws |
| `loadLinks(data, isCasting, subCb, cb)` | `open suspend fun` | **YES** (to play anything) | play/download/preview | throws |
| `getVideoInterceptor(link)` | `open fun` | optional | building player data source | `null` |
| `extractorVerifierJob(extractorData)` | `open suspend fun` | optional | link playing (keep-alive) | throws |
| `getLoadUrl(name, id)` | `open suspend fun` | optional | sync-ID deep launch | `null` |
| `supportedSyncNames` | `open val` | with getLoadUrl | SyncRedirector routing | empty set |
| `providerType` | `open val` | metaproviders only | result-page label, provider grouping | `DirectProvider` |
| `vpnStatus` | `open val` | optional | result-page VPN warning | `None` |
| `hasChromecastSupport` | `open val` | optional | cast availability on result page | `true` |
| `hasDownloadSupport` | `open val` | optional | download buttons | `true` |
| `instantLinkLoading` | `open val` | never | — (unused) | `false` |
| `usesWebView` | `open val` | optional | advisory only today | `false` |
| `sequentialMainPage` | `open var` | rate-limited sites | homepage fetch strategy | `false` |
| `sequentialMainPageDelay` | `open var` | with above | delay between rows (first load) | `0L` |
| `sequentialMainPageScrollDelay` | `open var` | with above | delay between scroll pages | `0L` |
| `loadLinksTimeoutMs` … `loadTimeoutMs` (×5) | `open val` | slow sites | withTimeout coercion 5 s–8 min (default 120 s) | `null` |
| `storedCredentials` | `open var` | never | — (legacy, unread) | `null` |
| `canBeOverridden` | `open var` | never | clone-site protection | `true` |

*(non-overridable state on the class: `lastHomepageRequest`, `sourcePlugin`, `init()`,
`overrideWithNewData()`)*

Minimal viable provider = `name` + `mainUrl` + `search` (+`load` + `loadLinks` to be useful).
Exactly what the template's ExampleProvider shows (`TestPlugins/.../ExampleProvider.kt:7-21`).

---

## 10. ANI-KUTA takeaways

1. **The whole contract is suspend + callback-based** — aniyomi's `AnimeSource` is blocking-with-
   wrappers; CloudStream is coroutine-native. Our "Cloud Screen" provider interface should mirror
   the suspend shape directly (we're coroutine-native too). `[inferred]`
2. **No filters, no login, no ranks** — three aniyomi concepts with no CS3 counterpart. Don't design
   them into the shared layer expecting CS3 plugins to populate them.
3. **Provider identity = `name` string** with weak uniqueness (`distinctBy(lang+name+url+class)`).
   For our DB we should mint our own internal IDs instead of trusting `name`.
4. **`load()`→`loadLinks()` payload channel is arbitrary JSON (`dataUrl`)** — richer than aniyomi's
   episode video list; our data layer needs a place to stash this blob between detail-load and
   playback (like `Episode.data`).
5. **Provider-declared timeout hints with app-enforced clamp (5 s–8 min)** is a cheap, effective
   anti-hang pattern worth copying for BOTH extension systems.
6. **Extractor dispatch = reversed-registration `mainUrl` prefix match + Levenshtein fallback** —
   plugin extractors legitimately shadow built-ins. Our loader must preserve append-order semantics
   to keep that property.
7. **Compat = build-time ABI dump only**; runtime `apiVersion` is dead. Decide explicitly whether
   ANI-KUTA adds a runtime gate or trusts compile-time (doc 16 decision).
8. **Clone-site pattern** (reflective no-arg re-instantiation + property rewrite) is a neat
   user-facing mirror feature enabled purely by the property-override design — cheap to replicate
   if our provider classes stay constructor-less.
9. Old CS3 tutorials (and the `headers`/`getLoginInfo`/`mainImageUrl` names from them) do not match
   the current API — always trust this doc's signatures over older blog posts.

---

## 11. Could not verify

- `MainAPI.kt` history: *when* `login`/`getLoginInfo`/`headers`/`mainImageUrl`/`rank` were removed
  (they exist in old CS3 docs/releases but not at master@efc1915). Requires git history — the
  research clone is shallow. `[noted as fact-of-absence at pinned commit]`
- Whether `usesWebView` is consumed anywhere outside the repo snapshot we have (e.g. forks/tests
  CI) — no consumer found in-app at this commit.
- NiceHttp `Requests` full API surface is taken from call-site usage, not from reading the
  dependency source (nicehttp is an external artifact; not vendored in the clone).
- `newJsContext`/`evalJs` full semantics (Rhino sandbox details) — only signatures/locations
  verified; doc 08 should exercise them.
- The exact UI flow that surfaces `getLoadUrl` for end users (Library list → provider open) was
  traced only via `LibraryFragment.kt:218` + `SyncRedirector`; a full UX trace belongs to doc 13.
- `ensureJarCompatibility` internals in the recloudstream gradle plugin (open item inherited from
  doc 02 / B1-d).
