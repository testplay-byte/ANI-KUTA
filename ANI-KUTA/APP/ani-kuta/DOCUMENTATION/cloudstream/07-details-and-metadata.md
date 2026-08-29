# 07 — Details & Metadata: `load()`, the Result UI, the image pipeline & metaproviders

> **Scope**: how CloudStream fetches everything you see on a details page — the `load()` contract, what
> the app renders from the returned `LoadResponse`, how posters/thumbnails actually get fetched
> (headers!), and the **metaprovider subsystem** (TMDb / CrossTmdb / Trakt / MyDramaList / SyncRedirector).
>
> **Not duplicated here** (see earlier docs): the full field tables for
> `LoadResponse`/`SearchResponse`/`Episode`/`SeasonData` live in **05-data-models.md §3-§4**; the
> method-by-method `MainAPI` contract lives in **03-mainapi-reference.md §2**; `iconUrl` for plugins
> lives in **04-extension-repositories.md**. This doc is the *flow* + the *app-side consumption* + the
> metaproviders.
>
> Source snapshot: `recloudstream/cloudstream` master @ `efc1915` (2026-08-28). File shorthand used in
> citations:
> - `MA` = `research/cloudstream/library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt`
> - `META` = `.../cloudstream3/metaproviders/` (e.g. `META/TmdbProvider.kt`)
> - `RVM2` = `research/cloudstream/app/src/main/java/com/lagradost/cloudstream3/ui/result/ResultViewModel2.kt`
> - `RFP` = `.../ui/result/ResultFragmentPhone.kt`, `RF` = `.../ui/result/ResultFragment.kt`,
>   `EA` = `.../ui/result/EpisodeAdapter.kt`, `AA` = `.../ui/result/ActorAdaptor.kt`
> - `APIR` = `.../ui/APIRepository.kt`, `IMG` = `.../utils/ImageModuleCoil.kt`,
>   `DSH` = `.../utils/DataStoreHelper.kt`, `ACU` = `.../utils/AppContextUtils.kt`,
>   `SWM` = `.../services/SubscriptionWorkManager.kt`, `MACT` = `.../MainActivity.kt`
> - `csdocs` = `research/csdocs/devs/create-your-own-providers.md`

---

## 1. The `load()` contract

### 1.1 Signature & semantics [verified]

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
`MA:660-667`. The base class *throws* — every real provider overrides it. The returned object is one
of the five `LoadResponse` impls (movie/TV/anime/live/torrent — 05 §3.2), built with the
`newMovieLoadResponse`/`newTvSeriesLoadResponse`/… builders (03 §5.4).

The official tutorial frames the job as "parse the page into a lot of metadata" — poster, title, year,
duration, tags, cast, trailer, plot, recommendations, and (the tricky part) episodes, with the explicit
warning *"Episodes in CloudStream are not paginated, meaning that if you have a show with 21 seasons …
you will need to parse them all"* `csdocs:144-149,151-269` [docs].

### 1.2 What `url` means — provider-relative, with two special cases

- **Normal case**: the exact string the provider itself put in `SearchResponse.url` during
  `search()`/`getMainPage()`. Nothing is normalized by the plugin author — but the *app* runs it
  through `MainAPI.fixUrl()` before calling `load()`:
  ```kotlin
  suspend fun load(url: String): Resource<LoadResponse> {
      return safeApiCall {
          withTimeout(getTimeout(api.loadTimeoutMs)) {
              if (isInvalidData(url)) throw ErrorLoadingException()
              val fixedUrl = api.fixUrl(url)
              ...
              api.load(fixedUrl) ...
  ```
  `APIR:85-89,105`. `fixUrl` prepends `mainUrl` for paths starting `/`, upgrades `//…` to `https:…`,
  and leaves full URLs untouched (`MA:742-762`). (Providers can also call `fixUrlNull` themselves when
  building links, `MA:735-740`.)
- **JSON-blob case**: two metaproviders serialize their own data class into `url` and *parse it back
  out in `load()`* — `TraktProvider` does `val data = parseJson<Data>(url)` (`META/TraktProvider.kt:115-116`)
  with `url = Data(type, mediaDetails).toJson()` at search time (`META/TraktProvider.kt:84-87`);
  `MyDramaListAPI` does the same (`META/MyDramaList.kt:128-129`). `fixUrl` explicitly tolerates this:
  ```kotlin
  if (url.startsWith("http") ||
      // Do not fix JSON objects and arrays when passed as urls.
      url.startsWith("{\"") || url.startsWith("[")
  ) {
      return url
  }
  ```
  `MA:743-748` [verified]. So "url" is really an *opaque provider key*, only *usually* a URL.
- **Sync-ID case**: entries arriving from personal lists (MAL/AniList) may carry a foreign URL; see
  `SyncRedirector` (§4.5) which runs *before* `load()`.

### 1.3 Who calls it, and when [verified]

| Caller | Trigger | Code |
|---|---|---|
| `ResultViewModel2.load(...)` | opening the Result page — `ResultFragmentPhone.onBindingCreated` (first open or restart) and `reloadViewModel(forceReload)` on `onResume`/plugins-reloaded | `RFP:459-467`, `RFP:393-405` |
| Same, retry button | "connection error" view | `RFP:494-503` |
| `MainActivity.loadPopup(result, load)` | long-press **preview popup** on a search/home card — full `load()` if `load=true`, else `loadSmall()` (see below) | `MACT:446-471` |
| `SubscriptionWorkManager` | periodic subscription worker — re-runs `api.load(savedData.url)` per subscribed item to diff episode counts | `SWM:134-142` |
| `CrossTmdbProvider.load` | the meta provider itself calls other providers' `load()` to aggregate | `META/CrossTmdbProvider.kt:99` |

`loadSmall(searchResponse)` is the "no provider hit" path: it fabricates a
`LoadResponseFromSearch` **directly from the `SearchResponse`** (name/url/type/posterUrl, plus
plot/score/tags if the card was a `SyncAPI.LibraryItem` or `DataStoreHelper.BookmarkedData`) and never
touches the provider — used so the preview popup can render *something* offline:
`RVM2:2549-2610` [verified]. It even keeps the old `id` so watch-state lookups still work
(`RVM2:2570`).

### 1.4 Timeout, errors, null & cache [verified]

- **Timeout**: `loadTimeoutMs: Long? = null` is a *hint* (`MA:596-601`). The app clamps it:
  ```kotlin
  private const val DEFAULT_TIMEOUT = 120_000L
  private const val MAX_TIMEOUT = 4 * DEFAULT_TIMEOUT
  private const val MIN_TIMEOUT = 5_000L
  fun getTimeout(desired: Long?): Long = (desired ?: DEFAULT_TIMEOUT).coerceIn(MIN_TIMEOUT, MAX_TIMEOUT)
  ```
  `APIR:31-33,62-64`. So `load()` gets 120 s by default, 5 s…480 s if the provider asks.
- **Null = failure**: `api.load(fixedUrl) ?: throw ErrorLoadingException()` (`APIR:105-118`) — a
  provider returning `null` becomes `Resource.Failure` via `safeApiCall`, and the ViewModel posts it to
  the page LiveData: `when (val data = repo.load(validUrl)) { is Resource.Failure -> _page.postValue(data) … }`
  (`RVM2:2661-2664`). Throw `ErrorLoadingException("message")` for a user-visible message
  (`csdocs:164-165` [docs]).
- **Garbage in**: `isInvalidData(url)` rejects `""`, `"[]"`, `"about:blank"` before the call
  (`APIR:48-50,88`).
- **Cache**: results are cached in-memory per `(api.name, fixedUrl)` for **10 minutes**, ring buffer of
  20 (`APIR:60,90-117`). Cache is cleared when plugins reload (`APIR:67-71`).
- **Post-processing on the way in**: blank tags are stripped immediately (`APIR:105-107`).

### 1.5 After `load()` returns — the `load()` → UI pipeline [verified]

`ResultViewModel2.load` (`RVM2:2612-2708`) does, in order:

1. Resolve provider by name-or-URL (`APIHolder.getApiFromNameNull`/`getApiFromUrlNull`,
   `MA:155-172`; fallback `APIRepository.noneApi`, `APIR:37-41`).
2. `SyncRedirector.redirect(url, api)` — translate MAL/AniList links if the provider supports them
   (§4.5), `RVM2:2642-2647`.
3. `repo.load(validUrl)` (§1.4).
4. `applyMeta(data.value, currentMeta, currentSync)` — merge **sync-account metadata** into the
   response (§4.6 — this is the closest thing CS3 has to "auto-enrichment").
5. `getId()` — stable identity = `(uniqueUrl minus mainUrl).hashCode()`, or the saved search id
   (`RVM2:370-379`).
6. Persist a `DownloadHeaderCached` (name/poster/url/type for the downloader),
   `RVM2:2678-2690`.
7. `loadTrailers(data.value)` (§6.2) and `postSuccessful(...)` → `postPage` (header data) +
   `postSubscription`/`postFavorites` (state checks) + `postEpisodes` (episode list),
   `RVM2:2149-2165,2502-2506`.
8. `handleAutoStart` — if opened with a "resume"/"play episode N" action, jump straight into the
   player (`RVM2:2510-2547`).

---

## 2. What the details screen renders

The whole details header is driven by one mapping function, `LoadResponse.toResultData(repo)`
(`RVM2:230-361`), producing the `ResultData` view-state (`RVM2:167-197`). Then
`ResultFragmentPhone` binds it view-by-view in `observe(viewModel.page)` (`RFP:930-1048`).

### 2.1 Field-by-field walkthrough (header) [verified]

| `LoadResponse` field | View-state field | UI element | Citation |
|---|---|---|---|
| `name` | `titleText` | `resultTitle` (also replaced by logo, §3) | `RVM2:293`, `RFP:940` |
| `posterUrl ?: backgroundPosterUrl` | `posterImage` | `resultPoster` (portrait poster, left of header) | `RVM2:290`, `RFP:951` |
| `backgroundPosterUrl ?: posterUrl` | `posterBackgroundImage` | `resultPosterBackground` (hero backdrop) | `RVM2:292`, `RFP:959-969` |
| `logoUrl` | `logoUrl` | `backgroundPosterWatermarkBadge` via `bindLogo` — *replaces the text title if it loads; falls back to text on error* | `RVM2:306`, `RFP:971-976`, `RF:243-279` |
| `plot` | `plotText` (+`plotHeaderText` "Plot"/"Torrent plot") | `resultDescription` (HTML, expandable to max lines 10→∞) | `RVM2:282-287,301-304`, `RFP:978-987` |
| `tags` | `tags` | `populateChips(resultTag, d.tags)` chip row | `RVM2:295`, `RFP:989` |
| `score` | `ratingText` = `score.toStringNull(0.1, 10, 1, false, '.')` | `resultMetaRating` ("8.3" style) | `RVM2:332-333`, `RFP:945` |
| `year` | `yearText` | `resultMetaYear` | `RVM2:330`, `RFP:943` |
| `duration` (minutes) | `durationText` (`secondsToReadable(dur*60)`) | `resultMetaDuration` | `RVM2:279,344-346`, `RFP:944` |
| `type` (TvType) | `typeText` (18-way when) | `resultMetaType` | `RVM2:308-329`, `RFP:942` |
| `apiName` | `apiName` | `resultMetaSite` | `RVM2:331`, `RFP:941` |
| `contentRating` | `contentRatingText` | `resultMetaContentRating` (width=0 when null to avoid gap) | `RVM2:334`, `RFP:947,1004-1007` |
| `comingSoon` | `comingSoon` | `resultComingSoon` banner; hides the whole `resultDataHolder` | `RVM2:296`, `RFP:991-992` |
| `actors` | `actors`/`actorsText` | horizontal `resultCastItems` (ActorAdaptor) **only if actor images exist** — else a plain "Cast: A, B, C" text line; hidden entirely behind the `show_cast_in_details_key` pref | `RVM2:235,297-300`, `RFP:994-1002` |
| `syncData` | `syncData` | feeds the sync UI (`syncModel.addSyncs`) | `RVM2:281`, `RFP:1009-1014` |
| `posterHeaders` | `posterHeaders` | passed to every poster/background/logo load (§3) | `RVM2:291`, `RFP:951-975` |
| `nextAiring` (EpisodeResponse) | `nextAiringEpisode`/`nextAiringDate` | `resultNextAiring` + `resultNextAiringTime` — live countdown "next episode in Xd Yh Zm" computed from `unixTime` | `RVM2:240-278,288-289`, `RFP:949-950` |
| `showStatus` (EpisodeResponse) | `onGoingText` (Ongoing/Completed) | `resultMetaStatus` | `RVM2:347-355`, `RFP:946` |
| provider `vpnStatus` | `vpnText` | `resultVpn` | `RVM2:335-341`, `RFP:937` |
| `providerType == MetaProvider` | `metaText` | `resultInfo` badge (this is *all* the app does with the meta flag) | `RVM2:342-343`, `RFP:938` |
| empty episode list | `noEpisodesFoundText` | `resultNoEpisodes` | `RVM2:356-359`, `RFP:939` |
| `url` | `url` | deep-link share (`recloudstream.github.io/csredirect`), "open in browser" | `RVM2:294`, `RFP:1022-1042` |
| `trailers` | (separate LiveData) | embedded trailer player (§6.2) | — |
| `recommendations` | (separate LiveData) | recommendation panel (§6.1) | — |

The **nextAiring countdown** is worth quoting because it shows the unit contract (`unixTime` is
seconds, `date` is ms):

```kotlin
if (this is EpisodeResponse) {
    val airing = this.nextAiring
    if (airing != null && airing.unixTime > unixTime) {
        val seconds = airing.unixTime - unixTime
        val days = TimeUnit.SECONDS.toDays(seconds)
        ...
        nextAiringEpisode = when (airing.season) {
            null -> txt(R.string.next_episode_format, airing.episode)
            else -> txt(R.string.next_season_episode_format, airing.season, airing.episode)
        }
```
`RVM2:240-277` [verified].

### 2.2 Actors row [verified]

`ActorAdaptor` (item = `ActorData`): round portrait `actorImage.loadImage(item.actor.image)` (no
headers), name, role badge (`ActorRole.Main/Supporting/Background` → localized string), and — for
anime — a **voice-actor flip card**: clicking swaps `actor.image` ↔ `voiceActor.image`
(`AA:70-121`). Long-press launches a web search for the actor's name (`AA:103-116`).

### 2.3 Seasons UI (seasonNames / SeasonData → picker) [verified]

There is no season tree — flat episode lists + a `SeasonData` overlay (05 §4.2). The ViewModel groups
episodes into a `Map<EpisodeIndexer, MutableList<ResultEpisode>>` where
`EpisodeIndexer = (dubStatus, season)` (`RVM2:2225-2229,2283`). The phone UI exposes **three
selectors**:

- **Season button** `resultSeasonButton` — label from `seasonToTxt(seasonData, season)`:
  `SeasonData.name` alone if `displaySeason == null`, else "Season {displaySeason} {name}"; season 0
  renders "No season" (`RVM2:587-604`). Options come from `_seasonSelections`, click →
  `viewModel.changeSeason(...)` → `postEpisodeRange` (`RFP:1402-1428`, `RVM2:1852-1858`).
- **Dub/Sub button** `resultDubSelect` — popup menu of `DubStatus` entries present in the response
  (anime only), `RFP:1342-1380`.
- **Episode-range button** `resultEpisodeSelect` — chunks of ~30 episodes built by `getRanges`
  (`RVM2:626-685`), label "Episodes start–end" (`RVM2:2015-2018`, `RFP:1382-1400`).

Selections are persisted per show so reopening restores them (`setResultSeason`/`setDub`/
`setResultEpisode`, keys in §5) — `RVM2:2066-2074`.

### 2.4 Episode list rendering [verified]

`postEpisodes` flattens the response into `ResultEpisode`s (id formulas & the full flattening
algorithm are in 05 §4.2; cited here: anime `RVM2:2178-2234`, tv `RVM2:2236-2291`, movie
`RVM2:2293-2298`). `EpisodeAdapter` then renders, per episode:

| `Episode`/`ResultEpisode` field | UI element | Citation |
|---|---|---|
| `episode` + `name` | "N. Title" (`"Episode N"` when name null; `filterName` strips "Episode N" prefixes at `RVM2:610-617`) | `EA:205-210` |
| `isFiller` (computed from filler-check, not provider data) | `episodeFiller` badge | `EA:207`, fillers at `RVM2:1834-1838` |
| `videoWatchState` / position | check icon + `episodeProgress` bar (watched ⇒ full-check; resume ⇒ partial bar) | `EA:212-235` |
| `posterUrl` | `episodePoster` (16:9 still) — **loaded without posterHeaders**; hidden entirely when blank | `EA:237-258` |
| `score` | `episodeRating` ("Rated x.x") | `EA:260-268` |
| `description` | `episodeDescript` (HTML, expand 4→∞ lines; TV opens a dialog instead) | `EA:270-291` |
| `date` (airDate) | "upcoming" icon + hides progress/play when in the future | `EA:239-251,293-299` |
| `runTime` | carried on `ResultEpisode` (used by sort/labels) | `RVM2:2221` |
| download status | `downloadButton` keyed by episode id | `EA:200-203` |

Sorting options (number asc/desc, rating high/low, date newest/oldest) operate on these same fields —
`RVM2:1892-1904`, enum `RVM2:211-218`.

---

## 3. The image pipeline (posters, backgrounds, logos, thumbnails)

### 3.1 Who loads images: **Coil 3**, not Glide [verified]

Current master uses **coil3** with OkHttp as the network fetcher, wrapped in the app's
`ImageLoader` object (`utils/ImageModuleCoil.kt`):

```kotlin
object ImageLoader {
    internal fun buildImageLoader(context: PlatformContext): ImageLoader {
        ...
        return ImageLoader.Builder(context)
            .crossfade(200)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.1) ... }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("cs3_image_cache").toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024) // 512 MB
                    ...
            }
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { buildDefaultClient(context) })) }
            .build()
    }
```
`IMG:37-73` [verified] (crossfade 200 ms, 10 % heap memory cache, 512 MB/4 % disk cache at
`cache/cs3_image_cache`). The comment above the OkHttp factory is telling: *"Pass interceptors with
care, unnecessary passing tokens to servers or image hosting services causes unauthorized exceptions"*
(`IMG:58-59`).

### 3.2 Where headers get injected [verified]

Every load funnels through `loadImageInternal`, which builds `NetworkHeaders` per request —
**default `User-Agent: USER_AGENT`, then the caller's header map layered on top**:

```kotlin
this.load(imageData, SingletonImageLoader.get(context)) {
    this.httpHeaders(NetworkHeaders.Builder().also { headerBuilder ->
        headerBuilder["User-Agent"] = USER_AGENT
        headers?.forEach { (key, value) ->
            headerBuilder[key] = value
        }
    }.build())
    builder() // if passed
}
```
`IMG:94-116` [verified] — comment: *"headers can be overridden by extensions"* (`IMG:106`).

### 3.3 `posterHeaders` — the hotlink-protection mechanism [verified]

`posterHeaders: Map<String, String>?` exists on **both** `SearchResponse` (05 §2.1) and
`LoadResponse` (`MA:1832`, KDoc at `MA:1805`: *"headers map used by network request to get the
poster"*). Many source sites 403 hotlinked images unless requests carry a `Referer`/`User-Agent`/cookie;
the provider therefore ships the required headers *with the URL* — the same trick ANI-KUTA/aniyomi
providers use, made a first-class model field.

Consumers (where the map physically lands in an image request):

| Surface | Code |
|---|---|
| Details poster (`resultPoster`) | `RFP:951-958` |
| Details background (`resultPosterBackground`) | `RFP:959-969` |
| Logo overlay via `bindLogo` | `RFP:971-976` → `RF:243-279` |
| Search/home card covers | `SearchResultBuilder.kt:135-139` (`cardView.loadImage(card.posterUrl, card.posterHeaders)`) |
| Home preview hero (`HomeScrollAdapter`) | `HomeScrollAdapter.kt:61,71,82` |
| MainActivity long-press preview popup poster | `MACT:1520-1530` |
| Subscription notification large icon (bitmap via Coil `ImageRequest` extras) | `SWM:191-198` + `utils/downloader/DownloadUtils.kt:28-44` |
| **Not** episode thumbnails | `EA:240` calls `loadImage(item.poster)` with no headers [verified] |
| **Not** actor portraits | `AA:119` calls `loadImage(mainImg)` with no headers [verified] |

(The episode/actor omissions look like an upstream blind spot — protected-host stills/portraits will
403 there even when the main poster works. Worth remembering when we port: thread headers *everywhere*
or normalize at the model level. `[inferred]` as a porting decision; the omissions themselves are
`[verified]`.)

`ResultData` carries the map through the view layer (`RVM2:196,291`), and it is **persisted** with
library entries (§5) so lists render correctly offline. **`mainImageUrl` does not exist** anywhere in
`library/src` or `app/src/main` (repo-wide grep, 0 hits) — confirms B1-c's finding [verified].

### 3.4 The four image roles [verified]

- **`posterUrl`** — portrait cover. Search cards, details poster, library notifications.
- **`backgroundPosterUrl`** — hero backdrop; falls back to poster both ways
  (`posterImage = posterUrl ?: backgroundPosterUrl`, `posterBackgroundImage = backgroundPosterUrl ?: posterUrl`, `RVM2:290-292`). Trakt is the richest source (fanart + logo + clearart lists,
  `META/TraktProvider.kt:371-380`); TMDb doesn't populate it at all (only `poster_path` w500,
  `META/TmdbProvider.kt:236-239,360`).
- **`logoUrl`** — clear-art title logo; `bindLogo` shows it *instead of* the text title when it loads,
  reverts to text on error, and is force-hidden once the trailer player takes over the header
  (`RFP:971-976,280-290`).
- **Episode `posterUrl`** — 16:9 stills (TMDb `still_path`, Trakt `screenshot`), §2.4.

For plugin *icons* (`iconUrl` in `plugins.json`) see 04 — different pipeline, not provider data.

### 3.5 Notes for ANI-KUTA (Coil) `[inferred]`

Our app already uses Coil (`AsyncImage` in `DetailsScreen.kt:88`), so the mapping is 1:1: build an
`ImageRequest` with `httpHeaders(NetworkHeaders...)` from a `headers` map on our
`SourceContentDetails`/`SourceEpisode`, keep a single shared `ImageLoader` with disk cache, and
default `User-Agent` per request exactly like `IMG:107-115`. The gap: our `SourceContentDetails` has
`thumbnailUrl`/`bannerUrl` but **no headers field, no logoUrl, no per-episode headers** (doc 05 §11
gaps — reconfirmed by reading `core/provider-api/SourceContentDetails.kt:6-20`).

---

## 4. Metadata providers (`library/.../metaproviders/`)

### 4.0 What a "metaprovider" is — and what it is NOT [verified]

```kotlin
/** enum class determines provider type:
 * MetaProvider: When data is fetched from a 3rd party site like imdb
 * DirectProvider: When all data is from the site
 * */
enum class ProviderType {
    MetaProvider,
    DirectProvider,
}
```
`MA:881-890`. A metaprovider is simply **a provider whose data comes from an aggregator API instead
of a streaming site**. It is registered, searched, and rendered exactly like any provider (search
results cards, details pages, library entries); the app-side special-casing is limited to:

1. a small "meta" badge on the details page (`RVM2:342-343` → `RFP:938`), and
2. nothing else — **there is no automatic app-side TMDb enrichment of other providers' results, and no
   user-facing "metadata provider" setting in master @ efc1915** (repo-wide grep for
   `MetaProvider`/`metadata` preferences found only the badge; 03 §7 concurs) [verified].

**Crucially, none of these classes is instantiated in the app or library.** Grep for
`TmdbProvider()`/`TraktProvider()`/`MyDramaListAPI()`/`CrossTmdbProvider()` returns only the class
declarations themselves [verified]. They are *base classes for plugins*: a plugin registers one via
`registerMainAPI(...)` (`library/.../plugins/BasePlugin.kt:20-23` → `APIHolder.allProviders.add`),
gets search/homepage/load for free, and implements `loadLinks` (playback) itself. The only metaprovider
object the *app* references directly is `SyncRedirector` (`RVM2:58,2643`). So "is TMDb a built-in
provider?" — **No: it's a shipped base class that plugin repos subclass.**

### 4.1 `TmdbProvider` — the TMDb front-end base class [verified]

```kotlin
open class TmdbProvider : MainAPI() {
    // This should always be false, but might as well make it easier for forks
    open val includeAdult = false
    // Use the LoadResponse from the metadata provider
    open val useMetaLoadResponse = false
    open val apiName = "TMDB"
    // As some sites don't support s0
    open val disableSeasonZero = true
    override val hasMainPage = true
    override val providerType = ProviderType.MetaProvider
    private val tmdbApiKey = "e6333b32409e02a4a6eba6fb7ff866bb"
    private val tmdbApiUrl = "https://api.themoviedb.org/3"
```
`META/TmdbProvider.kt:53-68`.

- **API**: TMDb v3 with a **hardcoded public key** (`…:67`) passed as `api_key` query param
  (`getApi`, `META/TmdbProvider.kt:246-255`). No OAuth, no user key.
- **Two modes**:
  - `useMetaLoadResponse = false` (default) — the *redirector* mode: `load()` parses
    `themoviedb.org/(tv|movie)/<id>` out of the url (`META/TmdbProvider.kt:450-458`), then calls the
    subclass's overridable hooks `loadFromTmdb(id)` / `loadFromImdb(imdb[, seasons])`
    (`META/TmdbProvider.kt:445-448,484-503`) — i.e. **subclass plays content by IMDb id**, and TMDb
    only supplies the discovery/identity layer (it fetches `/tv/$id/external_ids` to translate TMDb→IMDb).
  - `useMetaLoadResponse = true` — TMDb *is* the details source: one
    `append_to_response=external_ids,videos,credits,recommendations,similar,content_ratings` call per
    item (`META/TmdbProvider.kt:460-483`) mapped into a full `LoadResponse`.
- **What it produces** (tv mode, `TmdbTvDetail.toLoadResponse`, `META/TmdbProvider.kt:322-374`):
  poster (w500), year, plot, `addImdbId`, genres→tags, avg episode runtime, `Score.from10(voteAverage)`,
  trailers (`videos` filtered — drops "Opening Credits"/"Featurette", YouTube links only,
  `META/TmdbProvider.kt:293-304`), recommendations (`recommendations ?: similar` → its own
  `SearchResponse`s), cast→`ActorData` pairs (character name as role), US content rating with a
  lazy `fetchContentRating` fallback (`META/TmdbProvider.kt:306-320,371-372`). Movies analogous
  (`META/TmdbProvider.kt:376-405`).
- **Episodes**: per-season `/tv/$id/season/$n?append_to_response=external_ids` walk (skipping season 0
  by default), each episode a `newEpisode(TmdbLink(...).toJson())` with name/season/episode/score/
  description/still/airDate (`META/TmdbProvider.kt:322-352`). The **`TmdbLink` JSON blob**
  (`imdbID/tmdbID/episode/season/movieName`, `META/TmdbProvider.kt:44-51`) is the `dataUrl` handed to
  the subclass's `loadLinks` — the standard "metadata provider defines identity, subclass resolves
  streams" split.
- **Mainpage/search**: discover+top-rated movies/series (`META/TmdbProvider.kt:407-443`) and
  `/search/multi` (persons filtered out) → `newMovieSearchResponse`/`newTvSeriesSearchResponse` with
  `Score.from10` (`META/TmdbProvider.kt:506-520,257-281`).

### 4.2 `CrossTmdbProvider` — "MultiMovie", the aggregator demo [verified]

```kotlin
class CrossTmdbProvider : TmdbProvider() {
    override var name = "MultiMovie"
    override val apiName = "MultiMovie"
    override var lang = "en"
    override val useMetaLoadResponse = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Movie)
```
`META/CrossTmdbProvider.kt:22-28`. The one *concrete* metaprovider — and the clearest expression of
the enrichment idea:

- `load()` calls `super.load(url)` (TMDb metadata, mode=true), then **fans out**: for every installed
  provider with the same language and movie support (`validApis`, `META/CrossTmdbProvider.kt:34-35`), it
  runs that provider's `search(name)`, matches on normalized title (+year check), calls that
  provider's `load(search.url)`, and collects the resulting `MovieLoadResponse`s
  (`META/CrossTmdbProvider.kt:75-123`).
- The final `dataUrl` is `CrossMetaData(isSuccess, movies = [{apiName, dataUrl}…]).toJson()`
  (`META/CrossTmdbProvider.kt:38-42,113-114`) — so one details page *is* TMDb metadata, and its
  `loadLinks` simply replays each captured provider's `loadLinks` with the captured data
  (`META/CrossTmdbProvider.kt:44-65`).
- Limitations baked in: movies only (`ErrorLoadingException("Nothing besides movies are implemented…")`,
  `META/CrossTmdbProvider.kt:117-119`), TODO comments on filtering (`META/CrossTmdbProvider.kt:68,78`).

This is the pattern to steal for "one details page, many sources" — CS3 does it *provider-side*, not
app-side.

### 4.3 `TraktProvider` [verified]

```kotlin
open class TraktProvider : MainAPI() {
    override var name = "Trakt"
    override val hasMainPage = true
    override val providerType = ProviderType.MetaProvider
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    private val traktApiUrl = "https://api.trakt.tv"
    private val traktClientId: String = BuildConfig.TRAKT_CLIENT_ID
```
`META/TraktProvider.kt:42-53`.

- **Auth**: Trakt API v2 header auth on every request — `trakt-api-version: 2` +
  `trakt-api-key: <client id>` (`META/TraktProvider.kt:283-292`). The client id comes from
  **build config**, i.e. environment/`local.properties` at build time
  (`library/build.gradle.kts:119-122`: `System.getenv("TRAKT_CLIENT_ID") ?: localProperties["trakt.id"]`).
- **URLs are JSON**: search cards embed `Data(type, mediaDetails).toJson()` as the url
  (`META/TraktProvider.kt:71-106`); `load()` parses it back and then makes 3–4 API calls:
  people (cast), related (recommendations), seasons+episodes (`META/TraktProvider.kt:115-197`).
- **Richest image payload of the five**: `Images(poster/fanart/logo/clearart/banner/thumb/screenshot/
  headshot)` lists (`META/TraktProvider.kt:370-380`) → poster, `backgroundPosterUrl` (fanart),
  `logoUrl` (`META/TraktProvider.kt:120-122,176,186,273-274`). URLs arrive scheme-less and are fixed
  with `fixPath` (`https://$url`, `META/TraktProvider.kt:302-305`).
- **Smart typing**: infers `Anime`/`AnimeMovie` from animation genre + ja/zh language, Asian/Bollywood
  flags from language/country (`META/TraktProvider.kt:138-143,171,175,254,259`) — TvType decided by
  the *provider*, not the user.
- **nextAiring** is computed from the first future-dated episode (season≠0), ms→s conversion
  (`META/TraktProvider.kt:198,239-245,272`).
- **The playback blob** is a `LinkData` JSON (~25 fields: trakt/tmdb/imdb/tvdb/tvrage ids, type,
  season/episode, titles, isAnime/isAsian/isBollywood/isCartoon, dates — `META/TraktProvider.kt:440-466`)
  — again for a subclass's `loadLinks`.
- Also demonstrates `uniqueUrl = ids.trakt?.toJson()` to keep a stable storage key despite the
  volatile JSON url (`META/TraktProvider.kt:144,173,257`).

### 4.4 `MyDramaListAPI` (MDL) [verified]

```kotlin
// Reference: https://mydramalist.github.io/MDL-API/
abstract class MyDramaListAPI : MainAPI() {
    override var name = "MyDramaList"
    override val hasMainPage = true
    override val providerType = ProviderType.MetaProvider
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)
    companion object {
        val API_KEY: String = BuildConfig.MDL_API_KEY
        const val API_HOST = "https://api.mydramalist.com/v1"
        const val SITE_HOST = "https://mydramalist.com"
        private val headerInterceptor = MyDramaListInterceptor()
    }
```
`META/MyDramaList.kt:38-55`. **Abstract** — unusable as-is; a plugin must at least implement
`loadLinks`.

- **Key handling via OkHttp interceptor**, the cleanest of the three: every request goes out with
  `user-agent: Dart/3.6 (dart:io)` + `mdl-api-key: <key>` (`META/MyDramaList.kt:57-68`); key from
  build config (`library/build.gradle.kts:113-117`, `mdl.key` in local.properties).
- **Endpoints**: `/titles/trending|top_airing|upcoming?type=shows|movies` (mainpage,
  `META/MyDramaList.kt:70-77`), `POST /search/titles` (search, `META/MyDramaList.kt:89-97`),
  `/titles/{id}` (details, `META/MyDramaList.kt:128-135`), plus lazy sub-fetches during load:
  `/titles/{id}/credits` (actors), `/titles/{id}/recommendations`, and — fun detail — the **trailer is
  scraped from the site** `mydramalist.com/v1/trailers/{id}` because the API doesn't expose it
  (`META/MyDramaList.kt:280-308`).
- **`applyMedia`** is its post-load merge step (`META/MyDramaList.kt:174-195`): name/poster/year/
  plot/`Score.from10`/genres/runtime/recommendations/actors/`comingSoon` (from `isUpcoming(airedStart)`)
  /`backgroundPosterUrl` (reuses poster — MDL has no fanart)/certification normalization
  ("18+ Restricted…"→"18+", `META/MyDramaList.kt:205-214`). Trailers are added with
  `addRaw = true` **and explicit request headers** (`addTrailer(..., addRaw = true, headers =
  mapOf("User-Agent" to "Dart/3.6 (dart:io)"))`, `META/MyDramaList.kt:187-193`) — showing
  `TrailerData.headers` in action.
- **Drama structure**: episodes are flat with `season = null`, `TvType.AsianDrama` on the response
  (`META/MyDramaList.kt:161-170,321-352`) — a live example of "no seasons, just a flat list".

### 4.5 `SyncRedirector` — the (only) app-side metaprovider glue [verified]

```kotlin
object SyncRedirector {
    private val syncIds =
        listOf(
            SyncIdName.MyAnimeList to Regex("""myanimelist\.net/anime/(\d+)"""),
            SyncIdName.Anilist to Regex("""anilist\.co/anime/(\d+)""")
        )

    suspend fun redirect(url: String, providerApi: MainAPI): String {
        // Deprecated since providers should do this instead!
        ...
        return syncIds.firstNotNullOfOrNull { (syncName, syncRegex) ->
            if (providerApi.supportedSyncNames.contains(syncName)) {
                syncRegex.find(url)?.value?.let {
                    safeAsync { providerApi.getLoadUrl(syncName, it) }
                }
            } else null
        } ?: url
    }
}
```
`META/SyncRedirector.kt:7-53` (some commented-out legacy code elided). Called from
`ResultViewModel2.load` before `repo.load` (`RVM2:2642-2647`): if the user opened a **MAL/AniList
link** (e.g. from their sync-account list) and the target provider declares that id in
`supportedSyncNames` (`MA:616`), the provider's `getLoadUrl(name, id)` (`MA:710-712`) translates it to
a native url. The KDoc on `supportedSyncNames` spells out the chain:
`"tt6723592" -> getLoadUrl(ImdbSyncId(...)) -> "mainUrl/imdb/tt6723592" -> load(...)` — *"This is used
to launch pages from personal lists or recommendations using IDs"* (`MA:604-615`). Note in current
master **no in-repo provider overrides `supportedSyncNames`** (grep: only the base declaration) — it's
a plugin-facing feature [verified].

### 4.6 The app's real "auto-enrichment": `applyMeta` (sync APIs, not TMDb) [verified]

The only place CS3 merges *someone else's* metadata into a provider's `LoadResponse` is
`ResultViewModel2.applyMeta(resp, meta, syncs)` (`RVM2:1666-1804`), where `meta` is a
`SyncAPI.SyncResult` from the user's **logged-in sync account** (MAL/AniList/Kitsu/Simkl — app-side
`syncproviders/`, outside the plugin API). Merge is strictly **fill-if-absent**:

```kotlin
if (meta != null) {
    duration = duration ?: meta.duration
    score = score ?: meta.publicScore
    tags = tags ?: meta.genres
    plot = if (plot.isNullOrBlank()) meta.synopsis else plot
    posterUrl = posterUrl ?: meta.posterUrl ?: meta.backgroundPosterUrl
    actors = actors ?: meta.actors
    if (this is EpisodeResponse) { nextAiring = nextAiring ?: meta.nextAiring }
    ...
    addTrailer(meta.trailers)
}
```
`RVM2:1676-1704,1761-1764` [verified]. Plus, for `AnimeLoadResponse`s specifically:

- a **tracker lookup** (`APIHolder.getTracker` by cleaned titles → MAL/AniList ids, conflict-checked,
  `RVM2:1710-1754`) that also backfills `posterUrl`/`backgroundPosterUrl` from the tracker result
  (`RVM2:1756-1758`); and
- a **Kitsu episode-detail merge** that fills per-episode `description`/`name`/`posterUrl` (episode
  thumbnails!) when the provider left them null (`RVM2:1765-1801`).

So: metadata enrichment exists, but it's *account-driven and anime-tracker-shaped* — TMDb enrichment
only ever happens *inside* a provider that subclasses `TmdbProvider`. There is no cross-cutting
"metadata service" in the app. (One wart worth noting: sync recommendations are duplicated to any
provider whose name contains "gogoanime" or "9anime" — hardcoded at `RVM2:1688-1700` [verified].)

---

## 5. Where metadata is persisted (brief — deep dive is doc 13)

All persistence is app-side `DataStoreHelper` (SharedPreferences-backed `setKey`/`getKey`,
`utils/DataStoreHelper.kt`), namespaced by account (`"$currentAccount/$KEY"`). **Provider
`LoadResponse`s are never persisted** — only the app's own `@Serializable` `SearchResponse`
subclasses, which redeclare every field (incl. `posterHeaders`) with `@SerialName` and drop the legacy
`rating` via `WriteOnlySerializer` (05 §10.3):

| What | Class | Key constant | Citation |
|---|---|---|---|
| Favorites | `FavoritesData` (favoritesTime + 14 SearchResponse fields) | `RESULT_FAVORITES_STATE_DATA` | `DSH:52,434-441`, write `RVM2:971-990`, store `DSH:680-689` |
| Bookmarks (watch-status lists) | `BookmarkedData` | `RESULT_WATCH_STATE_DATA` / `RESULT_WATCH_STATE` | `DSH:49-51,374-389` |
| Subscriptions | `SubscribedData` (lastSeenEpisodeCount: Map<DubStatus,Int?>) | `RESULT_SUBSCRIBED_STATE_DATA` | `DSH:51,310-327`, refresh `DSH:648-661` |
| Resume watching | `ResumeWatchingResult` | `RESULT_RESUME_WATCHING` ("_2" after id migration) | `DSH:53-55` |
| Watched-state per episode/movie | `VideoWatchState` | `VIDEO_WATCH_STATE` | `DSH:48,760-769` |
| Playback positions | `PosDur(pos, dur)` | `VIDEO_POS_DUR` | `DSH:47,691-695` |
| Last-selected season/dub/episode per show | ints | `RESULT_SEASON` / `RESULT_DUB` / `RESULT_EPISODE` | `DSH:56-58`, `RVM2:2066-2074` |
| Download header cache (poster/name) | `DownloadHeaderCached` | `DOWNLOAD_HEADER_CACHE` | `RVM2:2678-2690` |

Two behavioral notes verified while reading:

- The details-screen favorite toggle writes a `FavoritesData` **without** `posterHeaders`
  (`RVM2:973-990` — the constructor call omits it, so it serializes as null) even though the field
  exists; the subscription path *does* round-trip headers (`SWM:191-198` uses
  `savedData.posterHeaders`). So protected-poster favorites may degrade to placeholder art in lists.
  `[verified]`
- Duplicate detection on add-to-favorites/subscriptions matches by **IMDb/TMDb/MAL/AniList ids from
  `syncData`** (Simkl-encoded), or normalized title+year — `RVM2:1000-1040` — the practical payoff of
  providers calling `addImdbId`/`addTMDbId`.

Identity: favorites/bookmarks are keyed by `LoadResponse.getId()` = `(uniqueUrl minus mainUrl).
hashCode()` (`RVM2:370-379`) — which is why `uniqueUrl` exists (05 §3.1) and why Trakt sets it
explicitly.

---

## 6. Recommendations & trailers

### 6.1 Recommendations — `SearchResponse` reuse, cross-provider by design [verified]

`LoadResponse.recommendations: List<SearchResponse>?` (`MA:1828`) is just a list of ordinary search
cards — *usually built by the same provider* (e.g. TMDb maps its own `recommendations ?: similar`
payload, `META/TmdbProvider.kt:368-369`), but nothing enforces that: sync enrichment injects cards
with **other providers' apiNames** (`RVM2:1688-1703`), and a provider may point anywhere.

App-side flow:

1. `postPage` posts them to `_recommendations` (`RVM2:2502-2506`).
2. `setRecommendations(rec, validApiName)` renders a grid (3 columns) with a **provider filter
   button** when the list spans multiple apiNames — *"very dirty selection"* per the comment — letting
   the user switch which provider's slice is shown (`RFP:1450-1501`, adapter wired at `RFP:703-714`).
   Default filter = the current page's provider, else first card's (`RFP:1452`).
3. Cards are `SearchAdapter`s whose click goes through the same `SearchHelper.handleSearchClickCallback`
   as normal search → `loadSearchResult(card)` → `ResultFragment.newInstance(card, …)` navigation
   (`RFP:708-712`, `ACU:744-765`, `SearchHelper.kt:17-21`). I.e. **recommendations are normal
   navigation into any provider's details page** — including one you don't currently have enabled only
   if the apiName resolves (`getApiFromNameNull` falls back to `noneApi`, `RVM2:2577-2581`).

### 6.2 Trailers — extract then play in-app [verified]

`LoadResponse.trailers: MutableList<TrailerData>` (`MA:1826`), where:

```kotlin
data class TrailerData(
    val extractorUrl: String,
    val referer: String?,
    val raw: Boolean,
    val headers: Map<String, String> = mapOf(),
)
```
`MA:1779-1786` (a half-finished `mirrors`/`subtitles` pair sits commented out below it — doc rot
echoing 05 §12). `raw = true` means "play `extractorUrl` directly", otherwise it's run through the
**same `loadExtractor` pipeline as episode streams**:

```kotlin
if (!loadExtractor(trailerData.extractorUrl, trailerData.referer, { subs.add(it) }, {
        links.add(Pair(it, trailerData.extractorUrl))
    }) && trailerData.raw
) {
    // fall back to a direct newExtractorLink(...) with trailerData.referer/headers
```
`RVM2:2447-2499` — capped at 3 trailers per page (`loadTrailers(response, 3)`, `RVM2:2438-2445`).
Playback happens **in-app**: `ResultFragmentPhone` hosts a Media3 player view in the header
(`RFP:441-451`), auto-plays the highest-quality trailer once extracted, treats a trailer player error
as "switch mirror" (`nextMirror()` walks `currentTrailers`, `RFP:229-247,249-266`), and `ResultTrailerPlayer`
(`ui/result/ResultTrailerPlayer.kt:26`) is the fullscreen-capable subclass. A YouTube trailer link
therefore resolves via CS3's YouTube extractor — no external player needed. The whole feature is
user-toggleable: pref `show_trailers_key` gates a static `LoadResponse.isTrailersEnabled` flag
(`ACU:430-437`, `MA:1859`) which `addTrailer` respects (`MA:2000-2007`). When a trailer plays, the
logo overlay is force-collapsed back to the text title (`RFP:280-290`).

---

## 7. ANI-KUTA mapping preview

Our current details screen (`feature/anime-details/impl/.../DetailsScreen.kt`, 3634 lines):

- **Data sources**: metadata is **AniList-first** — `AniListAnime` model (banner/cover/title/genres/
  status/format/episodes/score, `DetailsScreen.kt:92,123`), episodes come from the provider layer as
  `SourceEpisode`s via `EpisodeState` (`DetailsScreen.kt:276,420-427,532-534`), grouped/filtered
  client-side (`DetailsScreen.kt:808-862`). Provider-supplied metadata is only
  `SourceContentDetails` (title/description/genres/status/thumbnailUrl/bannerUrl/year/author/artist,
  `core/provider-api/SourceContentDetails.kt:6-20`) — used as *supplement*, not source of truth.
- **Our image pipeline**: plain Coil `AsyncImage` (`DetailsScreen.kt:88`), banner = blurred cover +
  gradient (layout doc at `DetailsScreen.kt:118-120`), plus **CoverAccentColor**: accent extracted from
  the cover bitmap drives an adaptive Material color scheme per show (`DetailsScreen.kt:192,578-588`,
  `AccentColors.from(accent)`). No per-request headers anywhere today — every image loads with Coil
  defaults.
- **What CS3 gives us free**:
  - **TMDb-class metadata for movies/series/dramas** — today we have *none* for non-anime: no
    plot/actors/score/trailers/recommendations/content-rating/nextAiring outside AniList. Subclassing
    (or reimplementing) `TmdbProvider`/`TraktProvider`/`MyDramaListAPI` gives a ready details page for
    the 3 non-anime content categories the user cares about (movies, TV, Asian drama) with images,
    cast, recommendations, and countdowns — keys included (TMDb's key is literally hardcoded
    `META/TmdbProvider.kt:67`; Trakt/MDL need build-time keys, `library/build.gradle.kts:113-122`).
  - **posterHeaders** — protected-host covers will 403 in *any* CS3-content port unless we add a
    headers field to `SourceContent`/`SourceContentDetails`/`SourceEpisode` (already flagged in 05
    §11.2; reconfirmed — our models have none).
  - **The `applyMeta` fill-if-absent pattern** — a clean recipe for merging our AniList metadata with
    provider-supplied details for anime, and later TMDb for movies: provider wins, tracker fills gaps
    (`RVM2:1676-1686`).
  - **Recommendation tiles as plain search cards + provider filter** (`RFP:1450-1501`) — maps neatly
    onto our `SourceContent` cards; our nav can reuse the existing `contentKey` routing.
  - **Trailer handling without YouTube SDK**: `TrailerData{raw, headers, referer}` + extractor
    pipeline (`MA:1779-1786`, `RVM2:2447-2499`) — if we adopt CS3 extractors for playback we get
    in-app trailers for free.
- **Gaps `[gap]`**:
  - `SourceContentDetails` lacks: score, actors, duration, contentRating, nextAiring, showStatus,
    trailers, recommendations, comingSoon, syncData, posterHeaders, logoUrl (full table in 05 §11.1) —
    the details *UI* cannot render CS3-equivalent richness without extending the model.
  - No season/dub model on `SourceEpisode` (only `number`, `SourceContentDetails.kt:14-27` /
    `SourceEpisode.kt:14-27`) — CS3's `season`+`SeasonData.displaySeason` overlay and the
    `(dubStatus, season)` indexer (`RVM2:2225-2229`) need a counterpart or our flattening will lose
    ordering for multi-season shows.
  - Identity: we key by `contentKey = "$sourceKey:$externalId"`; CS3 keys by
    `uniqueUrl`-hash + syncData ids for duplicate detection (`RVM2:370-379,1000-1040`). Adopting an
    `syncData`-like external-id map would give us the same cross-source dedup for mixed
    AniList/TMDb libraries.
  - Our CoverAccentColor pipeline consumes the *cover* only; CS3's `backgroundPosterUrl`+`logoUrl`
    would let us accent from fanart and render title logos — no current equivalent.

---

## 8. Could not verify / out of scope

- **Plugin-repo usage of metaproviders**: whether the official `recloudstream/extensions` repo ships a
  TMDb-based provider — the 5 extension sources in our workspace (Dailymotion, InternetArchive,
  Invidious, Twitch, Youtube) don't subclass them; phisher/CakesTwix/storm repos weren't re-grepped for
  this doc (doc 12 will cover real plugin patterns). The *design intent* (subclass + implement
  `loadLinks`) is stated by the class shape itself (`open`/`abstract`, `loadFromTmdb` hooks).
- **Whether `TmdbProvider.apiName = "TMDB"` is user-visible** anywhere as a "provider" you can filter
  by — depends on which plugins instantiate it; no in-app instance exists (§4.0).
- **CS3 Git history**: an older app version reportedly had a user-facing "metadata provider"
  preference (pre-extensions). Not present at master @ efc1915; not researched further here.
- **ResultFragmentTv.kt** was only skimmed for `bindLogo` symmetry (`ResultFragmentTv.kt:916`) — TV
  layout differences (focus handling, dialogs) are doc 13 territory.
