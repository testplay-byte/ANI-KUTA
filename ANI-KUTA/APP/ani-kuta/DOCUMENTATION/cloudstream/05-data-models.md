# 05 — CloudStream Data-Model Catalog (Complete)

> **Scope**: every data class / interface / enum that crosses the CloudStream plugin boundary — the
> `SearchResponse` family, the `LoadResponse` family, `Episode` & season structure, people, all enums,
> links & playback models (`ExtractorLink` etc.), home/mainpage models, the (WIP) filter models,
> serialization behavior, and a first mapping pass against ANI-KUTA's `provider-api` models.
> Companion doc to **03-mainapi-reference.md** (the *functions*); this is the *payloads*.
>
> **Primary source**: `research/cloudstream/library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt`
> (2861 lines, master @ efc1915, 2026-08-28 — same snapshot all B1 docs cite).
>
> **Citation shorthand** (all paths under `research/cloudstream/` unless noted):
> - `MA:<lines>` = `library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt`
> - `EA:<lines>` = `library/src/commonMain/kotlin/com/lagradost/cloudstream3/utils/ExtractorApi.kt`
> - `app/<path>` = `app/src/main/java/com/lagradost/cloudstream3/<path>`
> - `AU:<lines>` = `library/src/commonMain/kotlin/com/lagradost/cloudstream3/utils/AppUtils.kt`
> - `provider-api/<File>.kt` = `ANI-KUTA/APP/ani-kuta/core/provider-api/src/main/java/com/confused/anikuta/core/providerapi/<File>.kt`
>
> **Markers**: `[verified]` = read in source · `[docs]` = from recloudstream/csdocs · `[inferred]` = reasoned, needs verification.
> Every declaration below was copied from source (bodies trimmed only where marked `// …`); every class/field carries a citation.

---

## Table of contents

1. [Model family tree](#1-model-family-tree)
2. [Search-side models](#2-search-side-models)
3. [Details / load-side models](#3-details--load-side-models)
4. [TV / series structure](#4-tv--series-structure)
5. [People (Actor / ActorData)](#5-people-actor--actordata)
6. [Enums](#6-enums)
7. [Links & playback-side models](#7-links--playback-side-models)
8. [Home / mainpage models](#8-home--mainpage-models)
9. [Filter models](#9-filter-models)
10. [Serialization notes](#10-serialization-notes)
11. [Model map vs ANI-KUTA provider-api](#11-model-map-vs-ani-kuta-provider-api)
12. [Could not verify / confirmed absent](#12-could-not-verify--confirmed-absent)

---

## 1. Model family tree

Everything a plugin produces or consumes, in one compact list (`MA` = MainAPI.kt, `EA` = ExtractorApi.kt):

```
Search-side (plugin → app)
├─ interface SearchResponse                       MA:1406
├─ MovieSearchResponse / TvSeriesSearchResponse   MA:1655 / MA:1727
├─ AnimeSearchResponse / LiveSearchResponse       MA:1555 / MA:1691
├─ TorrentSearchResponse                          MA:1621
├─ SearchResponseList (items + hasNext)           MA:1292
└─ Score (rating value type, private ctor)        MA:919

Load-side (plugin → app)
├─ interface LoadResponse                         MA:1814
├─ MovieLoadResponse / TvSeriesLoadResponse       MA:2463 / MA:2675
├─ AnimeLoadResponse / LiveStreamLoadResponse     MA:2320 / MA:2414
├─ TorrentLoadResponse                            MA:2268
├─ interface EpisodeResponse (season/show airing) MA:2234
├─ Episode / SeasonData / NextAiring              MA:2552 / MA:2227 / MA:2215
└─ TrailerData                                    MA:1779

People
├─ Actor / ActorData / ActorRole                  MA:1534 / MA:1545 / MA:1524

Playback-side (plugin → app)
├─ SubtitleFile / AudioFile                       MA:1205 / MA:1247
├─ open class ExtractorLink (+DrmExtractorLink)   EA:700 / EA:588
├─ ExtractorLinkPlayList / PlayListItem           EA:369 / EA:352
├─ abstract class ExtractorApi                    EA:1419
└─ (app bridge) ResultEpisode / VideoLink / VideoState /
   SubtitleData / ExtractorUri / Cache            app/ui/result/ResultFragment.kt:40 …

Home/mainpage
├─ MainPageData / MainPageRequest                 MA:410 / MA:416
└─ HomePageResponse / HomePageList                MA:1270 / MA:1282

Enums (library)
├─ TvType / DubStatus / ShowStatus                MA:1120 / MA:905 / MA:900
├─ SearchQuality / Qualities / ExtractorLinkType  MA:1303 / EA:849 / EA:414
├─ ActorRole / SimklSyncServices / TrackerType    MA:1524 / MA:2664 / MA:2838
├─ ProviderType / VPNStatus / AutoDownloadMode    MA:887 / MA:893 / MA:1144
└─ SyncIdName (library syncproviders)             library/…/syncproviders/SyncAPI.kt:3

Enums (app-side consumers of the models)
├─ WatchType / SyncWatchType                      app/ui/WatchType.kt:7 / :20
├─ VideoWatchState                                app/ui/result/ResultFragment.kt:33
└─ SubtitleOrigin                                 app/ui/player/PlayerSubtitleHelper.kt:26
```

Notable **absences** in current master (verified by repo-wide grep, see §12):
`VideoExtractor` ❌ · top-level `Video` class ❌ · app-side `ResultResolution` ❌ · `Genre` enum ❌ ·
active filter system ❌ (only a commented-out WIP block, §9).

---

## 2. Search-side models

### 2.1 `SearchResponse` — the base interface

```kotlin
/** Abstract interface of SearchResponse. */
interface SearchResponse {
    val name: String
    val url: String
    val apiName: String
    var type: TvType?
    var posterUrl: String?
    var posterHeaders: Map<String, String>?
    var id: Int?
    var quality: SearchQuality?
    var score: Score?
}
```
`MA:1405-1416` [verified]

| Field | Type | Meaning / notes |
|---|---|---|
| `name` | `String` | Display title on cards & result rows. |
| `url` | `String` | Provider-relative or absolute page URL; passed back into `MainAPI.load(url)` (`MA:665`). The `new*SearchResponse` builders run it through `fixUrl()` first (`MA:1418-1496`). |
| `apiName` | `String` | **Provider identity string** — filled by the builder from `MainAPI.name`. Weak identity (names can collide; app dedupes providers by lang+name+url+class — see doc 03 §3). |
| `type` | `TvType?` | Content kind; nullable on the search side (concrete builders default to the family default, e.g. `TvType.Movie`). |
| `posterUrl` | `String?` | Card poster. |
| `posterHeaders` | `Map<String, String>?` | Headers required to fetch the poster image (hotlinking protection). |
| `id` | `Int?` | Provider-settable but almost never set by providers; the app derives its own stable int id from url+apiName (§10.4). |
| `quality` | `SearchQuality?` | Piracy-release-quality badge (see §6.4). |
| `score` | `Score?` | Rating shown on the card (see §2.6). |

Extension helpers that operate on any `SearchResponse`:
`addQuality(quality: String)` (`MA:1501-1503`, delegates to `getQualityFromString` `MA:1325-1368`) and
`addPoster(url: String?, headers: Map<String, String>? = null)` (`MA:1509-1512`). [verified]

### 2.2 The five concrete search responses — REAL list

Exactly **five** `SearchResponse` implementations ship in the library (grep `) : SearchResponse` across
`library/src` → 5 hits; several app-side classes also implement it for persistence/sync — §10.3). Each has its primary constructor poisoned with
`@Deprecated(..., level = DeprecationLevel.ERROR)` so plugins must use the `MainAPI.new*SearchResponse`
builder + lambda initializer:

```kotlin
data class MovieSearchResponse
@Deprecated("Use newMovieSearchResponse", level = DeprecationLevel.ERROR)
constructor(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,

    override var posterUrl: String? = null,
    var year: Int? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var score: Score? = null,
) : SearchResponse { /* + one extra deprecated secondary ctor */ }
```
`MA:1652-1686` [verified]

Field tables (only fields **beyond** the §2.1 base are listed):

| Class | Extra fields | Type | Notes | Citation |
|---|---|---|---|---|
| `MovieSearchResponse` | `year` | `Int?` | Release year shown on card. | `MA:1664` |
| `TvSeriesSearchResponse` | `year`, `episodes` | `Int?`, `Int?` | `episodes` = total episode count (UI badge "x episodes"). | `MA:1736-1737` |
| `AnimeSearchResponse` | `year`, `dubStatus`, `otherName`, `episodes` | `Int?`, `MutableSet<DubStatus>?`, `String?`, `MutableMap<DubStatus, Int>` | `episodes` maps DubStatus→count (e.g. `{Subbed: 12, Dubbed: 6}`); `otherName` = alternative/romaji title. Helpers: `addDubStatus` ×6, `addDub`, `addSub` (`MA:1576-1616`). | `MA:1564-1568` |
| `LiveSearchResponse` | `lang` | `String?` | Stream language tag for live TV cards. | `MA:1703` |
| `TorrentSearchResponse` | — | — | No extra fields; its own class only so `newTorrentSearchResponse` defaults `type = TvType.Torrent` (`MA:1418-1436`). | `MA:1621-1650` |

Builder signatures (one per class, all `fun MainAPI.` extensions, all default `fix = true` → `fixUrl(url)`):

```kotlin
fun MainAPI.newMovieSearchResponse(name, url, type: TvType = TvType.Movie, fix: Boolean = true,
    initializer: MovieSearchResponse.() -> Unit = { }): MovieSearchResponse            // MA:1438-1450
fun MainAPI.newTvSeriesSearchResponse(name, url, type: TvType = TvType.TvSeries, …)   // MA:1470-1482
fun MainAPI.newAnimeSearchResponse(name, url, type: TvType = TvType.Anime, …)         // MA:1484-1496
fun MainAPI.newLiveSearchResponse(name, url, type: TvType = TvType.Live, …)           // MA:1452-1468
fun MainAPI.newTorrentSearchResponse(name, url, type: TvType = TvType.Torrent, …)     // MA:1418-1436
```
`MA:1418-1496` [verified]

> ⚠️ **Type-independence**: the search class and the `TvType` are only loosely coupled. A provider may
> return `newAnimeSearchResponse(..., type = TvType.AnimeMovie)` — the app trusts `type` for UI routing,
> not the wrapper class. `[verified]` (defaults shown above; no runtime assertion exists anywhere in `app/`).

### 2.3 `SearchResponseList` — paginated wrapper

```kotlin
data class SearchResponseList
@Deprecated("Use newSearchResponseList method", level = DeprecationLevel.ERROR)
constructor(
    val items: List<SearchResponse>,
    val hasNext: Boolean = false
)
```
`MA:1288-1297` [verified]

Produced via `newSearchResponseList(list, hasNext = null)` — `hasNext` defaults to `list.isNotEmpty()`
(`MA:478-487`), plus `List<SearchResponse>.toNewSearchResponseList(hasNext)` convenience (`MA:489-491`).
The paginated `search(query, page)` returns it (`MA:641-648`); non-paginated `search(query)` returns a
plain `List<SearchResponse>` (`MA:651-653`); `quickSearch` too (`MA:656-658`).

### 2.4 `Score` — the rating value type (not an enum, but belongs here)

A fixed-point decimal for ratings. Private constructor; factory-only creation:

```kotlin
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Serializable
class Score private constructor(
    /** Decimal between [0, 10^9] representing the min score and max score respectively */
    @JsonProperty("data") @SerialName("data") private val data: Int,
)
```
`MA:917-922` [verified]

- Internally stores `data ∈ [0, 1_000_000_000]` (`MAX`, `MA:1023`); converts to any scale via
  `toInt(maxScore=10)`, `toFloat`, `toDouble`, `toLong` (`MA:932-942`).
- Factories: `from(value, maxScore)` for Int/Double/Float/String (out-of-range → logged warning + `null`,
  `MA:1042-1079`) and shorthands `from5`/`from10`/`from100` per type (`MA:1082-1115`).
- Formatting: `toString(maxScore, decimals, removeTrailingZeros, decimalChar)` (`MA:965-1020`) and
  `toStringNull(minScore, maxScore, …)` which returns **null below the minimum** "to avoid 0.0/10.0 in case
  of default = 0" (`MA:952-961`).
- Legacy bridges, all `DeprecationLevel.ERROR`: `fromOld(value ∉[0,10000])` (`MA:1028-1039`),
  `toOld(): Int` (`MA:926-930`), plus `LoadResponse.rating`/`Episode.rating` property shims (§3.2, §4.1).
- Serialized shape: `{"data": 850000000}` — a **single int field** with both Jackson and kotlinx annotations
  (`MA:921`).

### 2.5 Where search models surface (app side)

- Search results & home rows render through adapters that switch on the concrete class to show
  `year`/`episodes`/`lang` badges (`app/ui/search/SearchAdaptor.kt` + `SearchResultBuilder.kt` [inferred
  from field usage; rendering details are B2-a scope]).
- Clicking a card posts `url + apiName + name` into the Result screen bundle
  (`app/ui/result/ResultFragment.kt:147-159` [verified]).
- Library/favorites persist **dedicated** `SearchResponse` subclasses, not the provider classes (§10.3).

---

## 3. Details / load-side models

### 3.1 `LoadResponse` — the base interface

Full field set (all `var`, all overridable by providers via the builder lambda):

```kotlin
interface LoadResponse {
    var name: String
    var url: String
    var apiName: String
    var type: TvType
    var posterUrl: String?
    var year: Int?
    var plot: String?

    var score: Score?
    var tags: List<String>?
    var duration: Int? // in minutes
    var trailers: MutableList<TrailerData>

    var recommendations: List<SearchResponse>?
    var actors: List<ActorData>?
    var comingSoon: Boolean
    var syncData: MutableMap<String, String>
    var posterHeaders: Map<String, String>?
    var backgroundPosterUrl: String?

    var logoUrl: String?
    var contentRating: String?

    var uniqueUrl: String

    @Deprecated("`rating` is the old scoring system, use score instead", /* ERROR level */)
    var rating: Int?   // get/set bridged to Score.fromOld/toOld
    // + companion object with id/score/trailer/duration helpers — see below
}
```
`MA:1788-1851` [verified]

Field-by-field (KDoc from `MA:1788-1813` [verified]):

| Field | Type | Meaning / consumer |
|---|---|---|
| `name` | `String` | Title on the result page. |
| `url` | `String` | Page URL (input to `load()` round-trips; also used for the id hash). |
| `apiName` | `String` | Provider name. |
| `type` | `TvType` (non-null here, unlike SearchResponse) | Drives result-page layout: movie button vs episode list vs live/torrent (e.g. `app/ui/result/ResultViewModel2.kt:1918-1928`). |
| `posterUrl` / `posterHeaders` | `String?` / `Map<String,String>?` | Poster + fetch headers. |
| `year` | `Int?` | Release year. |
| `plot` | `String?` | Synopsis. |
| `score` | `Score?` | Rating (see §2.4). |
| `tags` | `List<String>?` | Genre chips; the app strips blank tags immediately after `load()` (`app/ui/APIRepository.kt:107`). |
| `duration` | `Int?` | **Minutes** (`MA:1799`, `MA:1825`). Helper `addDuration(input: String?)` parses "1h 22m"-style text (`MA:2091-2093`, regexes at `MA:2097-2139`). |
| `trailers` | `MutableList<TrailerData>` | See §3.3. |
| `recommendations` | `List<SearchResponse>?` | "More like this" row — recursion is possible (recs can contain more SearchResponses but not LoadResponses; depth is one level). |
| `actors` | `List<ActorData>?` | Cast (§5). |
| `comingSoon` | `Boolean` | True = no playable data yet. The `new*LoadResponse` builders auto-set it when data/episodes are empty (`MA:2400-2407`, `MA:2515`, `MA:2535`, `MA:2742`). |
| `syncData` | `MutableMap<String, String>` | Cross-service IDs (MAL/Kitsu/AniList/Simkl-JSON) — §3.4. |
| `backgroundPosterUrl` | `String?` | Backdrop image. |
| `logoUrl` | `String?` | "Image URL used as a visual title replacement. If the logo loads successfully, it is shown instead of the text title." (`MA:1807`) |
| `contentRating` | `String?` | E.g. "PG-13". |
| `uniqueUrl` | `String` | "The key used for storing the persistent data about an entry. On older versions `url` was used instead, but this was added to support JSON that can change as the url parameter. If you have JSON that can change you can set `url = jsonObject.toJson()` and `uniqueId = jsonObject.id.toString()`" (`MA:1809-1812`). Defaults to `url` in every concrete class. |

Companion-object helpers on `LoadResponse` (`MA:1853-2094` [verified]) — the meta/enrichment API:
`addIdToString`/`readIdFromString` (Simkl multi-id JSON, `MA:1865-1873`), `isMovie()` (`MA:1875-1877`),
four `addActors` overloads (names / `Pair<Actor,String?>` / `Pair<Actor,ActorRole?>` / `Actor` list,
`MA:1879-1911`), `getMalId`/`getKitsuId`/`getAniListId`/`getImdbId`/`getTMDbId` (`MA:1913-1934`),
`addMalId`/`addKitsuId`/`addAniListId`/`addSimklId`/`addImdbUrl`/`addImdbId`/`addTMDbId`/`addTraktId`
(`MA:1936-2062` — several are TODO stubs), `addTrailer` ×3 (`MA:1958-2042`), `addScore` ×2
(`MA:2064-2070`), deprecated `addRating` ×2 (`MA:2072-2089`), `addDuration` (`MA:2091-2093`).
`isTrailersEnabled` is a global gate defaulting to `true` (`MA:1859`).

### 3.2 The five concrete load responses

Same pattern as search: deprecated-error constructors + `suspend fun MainAPI.new*LoadResponse` builders
with `suspend … .() -> Unit` initializers.

**`MovieLoadResponse`** `MA:2460-2489` [verified] — implements `LoadResponse` only.

```kotlin
data class MovieLoadResponse
@Deprecated("Use newMovieLoadResponse method", level = DeprecationLevel.ERROR)
constructor(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,
    var dataUrl: String,          // ← the ONE extra field
    // … all §3.1 fields with defaults …
    override var uniqueUrl: String = url
) : LoadResponse
```

`dataUrl: String` — "string used as main LoadLinks fun parameter" — **arbitrary payload**; typically a
page URL, but the generic builder serializes *any* type T to JSON into it (`MA:2491-2519`):
`newMovieLoadResponse(name, url, type, data: T?, initializer)` does `data?.toJson() ?: ""`
(`MA:2506`) — and a `String` arg short-circuits to itself (`MA:2499-2505`). Empty `dataUrl` ⇒
`comingSoon = true` (`MA:2515`). The load→loadLinks channel is thus **untyped JSON in a String**
(doc 03 §2.9 makes the same point).

**`TvSeriesLoadResponse`** `MA:2672-2726` [verified] — implements `LoadResponse, EpisodeResponse`:

| Extra field | Type | Notes |
|---|---|---|
| `episodes` | `List<Episode>` | **Flat list** — seasons come from `Episode.season` + `seasonNames` (§4). |
| (from `EpisodeResponse`) `showStatus`, `nextAiring`, `seasonNames` | — | §4. |

Overrides `getLatestEpisodes()` → `mapOf(DubStatus.None to maxEpisode)` (`MA:2705-2713`) and
`getTotalEpisodeIndex(episode, season)` counting via display-season mapping (`MA:2715-2725`).
Builder: `newTvSeriesLoadResponse(name, url, type, episodes, initializer)` — `comingSoon = episodes.isEmpty()`
(`MA:2728-2746`).

**`AnimeLoadResponse`** `MA:2317-2380` [verified] — implements `LoadResponse, EpisodeResponse`:

| Extra field | Type | Notes |
|---|---|---|
| `engName` / `japName` | `String?` | English / Japanese titles (used for tracker lookup, `app/…/ResultViewModel2.kt:1716-1727`). |
| `episodes` | `MutableMap<DubStatus, List<Episode>>` | **Dub-split** episode lists; `addEpisodes(status, episodes)` appends (`MA:2385-2388`). |
| `synonyms` | `List<String>?` | Alternative titles. |

`getLatestEpisodes()` returns per-DubStatus max episode of max season (`MA:2356-2365`); builder
`newAnimeLoadResponse(name, url, type, comingSoonIfNone = true, …)` flips `comingSoon` off if *any*
dub-list is non-empty (`MA:2390-2409`).

**`LiveStreamLoadResponse`** `MA:2411-2440` [verified] — `LoadResponse` only; single extra field
`dataUrl: String` (same semantics as the movie one; blank ⇒ `comingSoon`, `MA:2454`). Default
`type = TvType.Live`.

**`TorrentLoadResponse`** `MA:2265-2293` [verified] — `LoadResponse` only; extra fields
`magnet: String?` and `torrent: String?` (magnet URI / .torrent URL). Builder default:
`comingSoon = magnet.isNullOrBlank() && torrent.isNullOrBlank()` (`MA:2311`). Default `type = TvType.Torrent`.

> There is **no** generic "series" base class besides `EpisodeResponse` (an interface, §4.4), and **no**
> `EpisodeLoadResponse`. `TvSeriesLoadResponse` and `AnimeLoadResponse` are parallel siblings. [verified]

### 3.3 `TrailerData`

```kotlin
data class TrailerData(
    val extractorUrl: String,
    val referer: String?,
    val raw: Boolean,
    val headers: Map<String, String> = mapOf(),
    // var mirrors: List<ExtractorLink>,        ← commented out
    // var subtitles: List<SubtitleFile> = …,   ← commented out
)
```
`MA:1774-1786` [verified]

- `extractorUrl` — trailer page/video URL; `raw = true` means "use as direct video link instead of
  extracting it" (`MA:1777`).
- Older revisions carried resolved `ExtractorLink` lists; the current shape defers extraction to the app
  (commented-out field declarations visible at `MA:1784-1785`; trailer extraction happens in
  `app/…/ResultViewModel2.kt:451-454` `ExtractedTrailerData(mirros, subtitles)` [verified]).
- Added via `LoadResponse.addTrailer(trailerUrl, referer = null, addRaw = false, headers = mapOf())`
  (`MA:2000-2008`) or list variant (`MA:2011-2042`); gated on `isTrailersEnabled` (`MA:1859`).

### 3.4 `syncData` — the cross-service ID map

`MutableMap<String, String>` on every `LoadResponse` (`MA:1831`). Keys are per-provider prefixes held in
the companion: `malIdPrefix`, `kitsuIdPrefix`, `aniListIdPrefix`, `simklIdPrefix` — all default `""` and
are **assigned by the app at startup** (comment `//malApi.idPrefix` at `MA:1854-1858`; the app's
`AccountManager` owns the real prefixes — see `app/…/syncproviders/AccountManager.kt` [inferred]).
The Simkl entry is special: a JSON map of `SimklSyncServices → id` (§6.7) built by
`addIdToString`/`readIdFromString` (`MA:1865-1873`).

---

## 4. TV / series structure

### 4.1 `Episode` — ALL fields

```kotlin
/** Episode information that will be passed to LoadLinks function & showed on UI */
data class Episode
@Deprecated("Use newEpisode method", level = DeprecationLevel.ERROR)
constructor(
    var data: String,
    var name: String? = null,
    var season: Int? = null,
    var episode: Int? = null,
    var posterUrl: String? = null,
    var score: Score? = null,
    var description: String? = null,
    var date: Long? = null,
    var runTime: Int? = null,
) {
    @Deprecated("`rating` is the old scoring system, use score instead", /* ERROR level */)
    var rating: Int?
        set(value) { this.score = Score.from(value, 100) }
        get() = score?.toInt(100)
}
```
`MA:2541-2575` [verified]

| Field | Type | Notes |
|---|---|---|
| `data` | `String` | "string used as main LoadLinks fun parameter" — the same arbitrary-JSON channel as `dataUrl` (§3.2). Builders: `newEpisode(url, initializer, fix = true)` (String) or `newEpisode(data: T, …)` → `data?.toJson() ?: throw ErrorLoadingException("invalid newEpisode")` (`MA:2624-2652`). |
| `name` | `String?` | Episode title. |
| `season` | `Int?` | Season number — **nullable**: null episodes are grouped as season 0/unnumbered by the app (`app/…/ResultViewModel2.kt:2225` defaults `seasonIndex ?: 0`). |
| `episode` | `Int?` | Episode number; app falls back to list position +1 (`app/…/ResultViewModel2.kt:2186, 2243`). |
| `posterUrl` | `String?` | Per-episode thumbnail. |
| `score` | `Score?` | Per-episode rating (enables rating sort, `app/…/ResultViewModel2.kt:1982-1983`). |
| `description` | `String?` | Episode synopsis (filled from Kitsu when missing, `app/…/ResultViewModel2.kt:1788`). |
| `date` | `Long?` | Air date **epoch milliseconds**; set via `addDate(String, format = "yyyy-MM-dd")` with ISO-8601 → custom-format → date-only fallback chain (`MA:2578-2606`), or `addDate(LocalDate?)`/`addDate(Instant?)` (`MA:2608-2614`). |
| `runTime` | `Int?` | "Episode runtime in seconds" (`MA:2549`) — NOTE the contrast with `LoadResponse.duration` which is **minutes** (`MA:1799`). |

### 4.2 How seasons map — VERIFIED: flat episode lists + `SeasonData` overlay

There is **no** nested Season/Episode tree. The structure is:

1. `TvSeriesLoadResponse.episodes: List<Episode>` — one flat list; each `Episode.season: Int?` carries the
   season (`MA:2682`). `AnimeLoadResponse.episodes: MutableMap<DubStatus, List<Episode>>` — same flat lists,
   additionally keyed by dub status (`MA:2333`).
2. `EpisodeResponse.seasonNames: List<SeasonData>?` — an optional parallel overlay that names/renumbers
   seasons (`MA:2237`).

```kotlin
@Serializable
data class SeasonData(
    @JsonProperty("season") @SerialName("season") val season: Int,
    @JsonProperty("name") @SerialName("name") val name: String? = null,
    @JsonProperty("displaySeason") @SerialName("displaySeason") val displaySeason: Int? = null, // will use season if null
)
```
`MA:2221-2231` [verified] — KDoc: "To be mapped with episode season, not shown in UI if displaySeason is
defined"; name renders as `"Season $displaySeason $name"` or just `"$name"`
(`app/…/ResultViewModel2.kt:587-601` `seasonToTxt` [verified]).

`addSeasonNames(List<String>)` auto-numbers `season = index + 1` (`MA:2250-2258`);
`addSeasonNames(List<SeasonData>)` passes through (`MA:2260-2263`).

App-side flattening (the authoritative display algorithm):
- Sorts episodes by `(season ?: 0) * 10_000 + (episode ?: 0)` (`app/…/ResultViewModel2.kt:2240-2242`).
- Stable per-episode ids: anime `mainId + episode + dubStatusId * 1_000_000 + season * 10_000`
  (`:2187-2189`); tv `mainId + (season ?: 0) * 100_000 + episode + 1` (`:2244-2245`). [verified]
- Display season = `seasonData.displaySeason ?: episode.season` (`:2201-2209`), and
  `getTotalEpisodeIndex` prefers display seasons "as actual season may be something random to fit multiple
  seasons into one" (`MA:2370-2378`).

### 4.3 `NextAiring`

```kotlin
data class NextAiring(
    val episode: Int,
    val unixTime: Long,
    val season: Int? = null,
)
```
`MA:2210-2219` [verified] — next episode number + **unix seconds** timestamp + optional season.
Surfaces on the result page as a countdown ("next episode in x days/hours", `app/…/ResultViewModel2.kt:255-276`).

### 4.4 `EpisodeResponse` & `ShowStatus`

```kotlin
/** Abstract interface of EpisodeResponse */
interface EpisodeResponse {
    var showStatus: ShowStatus?
    var nextAiring: NextAiring?
    var seasonNames: List<SeasonData>?
    fun getLatestEpisodes(): Map<DubStatus, Int?>
    fun getTotalEpisodeIndex(episode: Int, season: Int): Int
}
```
`MA:2233-2248` [verified]

`ShowStatus` — exactly two values: `Completed`, `Ongoing` (`MA:899-903` [verified]; KDoc "enum class
determines Show status (Completed or Ongoing)"). This is the *only* status model on the library side —
no "hiatus"/"cancelled" granularity. Richer status lives per-sync-service in `SyncAPI.SyncResult.airStatus:
ShowStatus?` (same enum, `app/…/syncproviders/SyncAPI.kt:113` [verified]).

`getTotalEpisodeIndex` KDoc example (`MA:2240-2247`): "Season 1: 10 episodes. Season 2: 6 episodes.
`getTotalEpisodeIndex(episode = 3, season = 2) -> 10 + 3 = 13`".

Convenience predicates over `LoadResponse`/`TvType`: `LoadResponse?.isEpisodeBased()` (`MA:2144-2147`),
`LoadResponse?.isAnimeBased()` (`MA:2153-2156`), `TvType?.isEpisodeBased()` — true for
`Anime, AsianDrama, Cartoon, TvSeries` (`MA:2165-2174`), plus `TvType.getFolderPrefix()` mapping every
type to a downloads folder name (`MA:2187-2208`).

---

## 5. People (Actor / ActorData)

```kotlin
/** enum class of Actor roles (Main, Supporting, Background).*/
enum class ActorRole {
    Main,
    Supporting,
    Background,
}

/** Data class hold Actor personal information */
data class Actor(
    val name: String,
    val image: String? = null,
)

/** Data class hold Actor information */
data class ActorData(
    val actor: Actor,
    val role: ActorRole? = null,
    val roleString: String? = null,
    val voiceActor: Actor? = null,
)
```
`MA:1523-1550` [verified]

| Model | Fields | Notes |
|---|---|---|
| `Actor` | `name: String`, `image: String?` | `image` = headshot URL. |
| `ActorData` | `actor: Actor`, `role: ActorRole?`, `roleString: String?`, `voiceActor: Actor?` | `roleString` is free text ("Sherlock Holmes"); `voiceActor` is "used in case of Animation for voice actors" (`MA:1543`). |

Where they surface:
- `LoadResponse.actors: List<ActorData>?` (§3.1) — populated by provider or by `applyMeta` enrichment
  from tracker metadata (`app/…/ResultViewModel2.kt:1682`).
- Set through the 4 `addActors` companion overloads (`MA:1879-1911`).
- UI degrades to a text list when no actor has an image (`app/…/ResultViewModel2.kt:235, 297-300`):
  `hasActorImages = actors?.firstOrNull()?.actor?.image?.isNotBlank() == true`.
- Sync services also return them: `SyncAPI.SyncResult.actors: List<ActorData>?`
  (`app/…/syncproviders/SyncAPI.kt:130`).
- Enrichment: `applyMeta` merges tracker cast into a provider response
  (`actors = actors ?: meta.actors`, `app/…/ResultViewModel2.kt:1666-1704`).
- No people model exists on the **search** side (no cast on cards). [verified]

---

## 6. Enums

Complete sweep: every `enum class` in `library/src` is listed here (grep result set reproduced in §12.1);
app-side model-adjacent enums follow.

### 6.1 `TvType` — ALL 18 values

```kotlin
@Suppress("UNUSED_PARAMETER")
enum class TvType(value: Int?) {
    Movie(1),
    AnimeMovie(2),
    TvSeries(3),
    Cartoon(4),
    Anime(5),
    OVA(6),
    Torrent(7),
    Documentary(8),
    AsianDrama(9),
    Live(10),
    NSFW(11),
    Others(12),
    Music(13),
    AudioBook(14),

    /** Won't load the built in player, make your own interaction */
    CustomMedia(15),

    Audio(16),
    Podcast(17),
    Video(18),
}
```
`MA:1119-1142` [verified]

One-line semantics (KDoc is absent for individual values — semantics compiled from extension functions
and app usage, all cited):

| Value | Semantics |
|---|---|
| `Movie` | Standalone film — movie button, no episode list. |
| `AnimeMovie` | Anime film — treated as movie (`isMovieType()` true, `MA:1159-1169`). |
| `TvSeries` | Episode-based live-action/animation series (`isEpisodeBased` true, `MA:2165`). |
| `Cartoon` | Episode-based western animation (`isEpisodeBased` true). |
| `Anime` | Episode-based anime (`isEpisodeBased` + `isAnimeOp` true, `MA:1195-1197`). |
| `OVA` | Anime OVA/ONA/special — anime-side extras (`isAnimeOp` true). |
| `Torrent` | Magnet/.torrent payload (`TorrentLoadResponse`); counts as movie-type for playback. |
| `Documentary` | Standalone/series documentary. |
| `AsianDrama` | Episode-based drama (K-drama/C-drama etc., `isEpisodeBased` true; folder "AsianDramas"). |
| `Live` | Live stream — `isLiveStream()` true (`MA:1188-1190`); no resume/progress. |
| `NSFW` | Adult content (gated by `SettingsJson.enableAdult`, `MA:405-408`). |
| `Others` | Fallback bucket. |
| `Music` | Audio-only music (`isAudioType` true, `MA:1174-1183`). |
| `AudioBook` | Audio-only audiobook (`isAudioType` true). |
| `CustomMedia` | "Won't load the built in player, make your own interaction" (`MA:1136-1137`) — provider-defined interaction. |
| `Audio` | Generic audio (`isAudioType` true). |
| `Podcast` | Podcast feed (`isAudioType` true). |
| `Video` | Misc single video — movie-like playback without cinema semantics. |

The constructor int is **unused** (`@Suppress("UNUSED_PARAMETER")`, `MA:1119`) — pure ordinal-ish tag, kept
for source stability. Grouping helpers: `isMovieType()` = `AnimeMovie, Live, Movie, Torrent, Video`
(`MA:1159-1169`); `isAudioType()` = `Audio, AudioBook, Music, Podcast` (`MA:1174-1183`);
`isLiveStream()` (`MA:1188-1190`); `isAnimeOp()` = `Anime, OVA` (`MA:1195-1197`); `isEpisodeBased()` =
`Anime, AsianDrama, Cartoon, TvSeries` (`MA:2165-2174`). [verified]

### 6.2 `DubStatus`

```kotlin
enum class DubStatus(val id: Int) {
    None(-1),
    Dubbed(1),
    Subbed(0),
}
```
`MA:905-909` [verified]

Used as (a) the key of `AnimeLoadResponse.episodes` and `AnimeSearchResponse.episodes` maps, (b) the
dub/sub toggle state stored per-show in DataStore (`DataStoreHelper.setResultDub` →
`setKey(RESULT_DUB, id, status.ordinal)`, `app/…/utils/DataStoreHelper.kt:779` [verified] — note the app
persists **ordinal**, not `id`; the `id` ints feed the episode-id hash `MA`-style formula in
`ResultViewModel2.kt:2188`). `None` is the "not anime / no dub split" placeholder returned by
`TvSeriesLoadResponse.getLatestEpisodes()` (`MA:2712`).

### 6.3 `ShowStatus` — `Completed | Ongoing` (`MA:899-903`, §4.4)

### 6.4 `SearchQuality` — release-quality badge on cards

```kotlin
@Suppress("UNUSED_PARAMETER")
enum class SearchQuality(value: Int?) {
    Cam(1), CamRip(2), HdCam(3), Telesync(4), // TS
    WorkPrint(5), Telecine(6), // TC
    HQ(7), HD(8), HDR(9), // high dynamic range
    BlueRay(10), DVD(11), SD(12), FourK(13), UHD(14), SDR(15), // standard dynamic range
    WebRip(16)
}
```
`MA:1299-1320` [verified] — values follow Wikipedia "Pirated movie release types" (KDoc `MA:1301`).
Almost always assigned via `SearchResponse.addQuality(string)` → `getQualityFromString(string)` whose
lookup table maps ~35 spellings ("hdtc", "hdts", "fhd", "br", "webdl", …) to these enums
(`MA:1322-1368` [verified]).

### 6.5 `Qualities` — link-height in pixels (playback side)

```kotlin
enum class Qualities(var value: Int, val defaultPriority: Int) {
    Unknown(400, 4),
    P144(144, 0), // 144p
    P240(240, 2), // 240p
    P360(360, 3), // 360p
    P480(480, 4), // 480p
    P720(720, 5), // 720p
    P1080(1080, 6), // 1080p
    P1440(1440, 7), // 1440p
    P2160(2160, 8); // 4k or 2160p
    // companion: getStringByInt / getStringByIntFull ("Auto", "", "4K", "1080p", "Unknown")
}
```
`EA:849-880` [verified] — **NOT** the same axis as `SearchQuality`: `Qualities` describes the resolution
of a playable link (stored on `ExtractorLink.quality` as a raw `Int`, almost always
`Qualities.*.value`); `SearchQuality` describes the release provenance of a search result.
`Unknown = 400` is the fallback default (`EA:766`). String→int helper `getQualityFromName` at `EA:882-891`.

### 6.6 `ExtractorLinkType` — container/stream format

```kotlin
enum class ExtractorLinkType {
    /** Single stream of bytes no matter the actual file type */
    VIDEO,
    /** Split into several .ts files, has support for encrypted m3u8s */
    M3U8,
    /** Like m3u8 but uses xml, currently no download support */
    DASH,
    /** No support at the moment */
    TORRENT,
    /** No support at the moment */
    MAGNET;
    // getMimeType(): video/mp4 | application/x-mpegURL | application/dash+xml | application/x-bittorrent ×2
}
```
`EA:412-441` [verified] — "Metadata about the file type used for downloads and exoplayer hint, if you
respond with the wrong one the file will fail to download or be played" (`EA:412-413`).
Auto-inference from URL extension: `inferTypeFromUrl` (.m3u8/.mpd/.torrent/magnet: prefix else VIDEO,
`EA:443-457`); `INFER_TYPE = null` sentinel (`EA:459`).

### 6.7 Library enums, quick table

| Enum | Values | Where | Citation |
|---|---|---|---|
| `ActorRole` | `Main, Supporting, Background` | `ActorData.role` | `MA:1524-1528` |
| `SimklSyncServices(originalName)` | `Simkl("simkl"), Imdb("imdb"), Tmdb("tmdb"), AniList("anilist"), Mal("mal")` | Simkl id-map keys in `syncData` | `MA:2660-2670` |
| `TrackerType` | `MOVIE, TV, TV_SHORT, ONA, OVA, SPECIAL, MUSIC` (+ `getTypes(TvType)` mapping) | AniList GraphQL tracker filter | `MA:2838-2859` |
| `ProviderType` | `MetaProvider` (data from 3rd-party site like imdb), `DirectProvider` (all data from the site) | `MainAPI.providerType` | `MA:881-890` |
| `VPNStatus` | `None, MightBeNeeded, Torrent` | `MainAPI.vpnStatus` — torrent warning UI | `MA:892-897` |
| `AutoDownloadMode(value)` | `Disable(0), FilterByLang(1), All(2), NsfwOnly(3)` | app plugin auto-download pref (not a payload model; listed for completeness) | `MA:1144-1154` |
| `SyncIdName` | `Anilist, MyAnimeList, Kitsu, Trakt, Imdb, Simkl, LocalList` | library-side sync service identity (imported by app syncproviders) | `library/…/syncproviders/SyncAPI.kt:3-11` |

### 6.8 App-side enums that consume the models

```kotlin
enum class WatchType(val internalId: Int, @StringRes val stringRes: Int, @DrawableRes val iconRes: Int) {
    WATCHING(0, …), COMPLETED(1, …), ONHOLD(2, …), DROPPED(3, …), PLANTOWATCH(4, …), NONE(5, …);
    companion object { fun fromInternalId(id: Int?) = … ?: NONE }
}
enum class SyncWatchType(val internalId: Int, …) {
    NONE(-1, …), WATCHING(0, …), COMPLETED(1, …), ONHOLD(2, …), DROPPED(3, …), PLANTOWATCH(4, …), REWATCHING(5, …);
}
```
`app/ui/WatchType.kt:7-32` [verified]

- `WatchType` = local watch-state for the result screen bookmark button (persisted via
  `DataStoreHelper.setResultWatchState` → `setKey(RESULT_WATCH_STATE, id, status)` at
  `app/…/utils/DataStoreHelper.kt:787` [verified]).
- `SyncWatchType` = the same axis **extended for sync services** (`NONE = -1`, plus `REWATCHING`) — used
  by `SyncAPI.AbstractSyncStatus.status` (`app/…/syncproviders/SyncAPI.kt:87`).
- `VideoWatchState { None, Watched }` — per-episode watched flag, "Future proofed way to mark episodes as
  watched" (`app/ui/result/ResultFragment.kt:30-37` [verified]).
- `SubtitleOrigin { URL, DOWNLOADED_FILE, EMBEDDED_IN_VIDEO }` (`app/ui/player/PlayerSubtitleHelper.kt:26-30`).

---

## 7. Links & playback-side models

### 7.1 `SubtitleFile`

```kotlin
@ConsistentCopyVisibility
data class SubtitleFile private constructor(
    var lang: String,
    var url: String,
    var headers: Map<String, String>?
) {
    @Deprecated("Use newSubtitleFile method", level = DeprecationLevel.WARNING)
    constructor(lang: String, url: String) : this(lang = lang, url = url, headers = null)

    /** Language code to properly filter auto select / download subtitles */
    val langTag: String?
        get() = fromCodeToLangTagIETF(lang) ?: fromLanguageToTagIETF(lang, true)

    /** Backwards compatible copy */
    fun copy(lang: String = this.lang, url: String = this.url): SubtitleFile = …
}
```
`MA:1199-1221` [verified] — created only via `newSubtitleFile(lang, url, initializer)` (`MA:1224-1236`).
There is **no** `label`/`source` field (assignment brief guessed "label/lang/url/source" — real shape is
lang/url/headers, with `langTag` derived). The app wraps it into `SubtitleData` for the player (§7.8).

### 7.2 `AudioFile`

```kotlin
@ConsistentCopyVisibility
@Serializable
data class AudioFile internal constructor(
    @JsonProperty("url") @SerialName("url") var url: String,
    @JsonProperty("headers") @SerialName("headers") var headers: Map<String, String>? = null,
)
```
`MA:1238-1250` [verified] — separate audio track that can be merged with a video link
(`ExtractorLink.audioTracks`). Created via `newAudioFile(url, initializer)` (`MA:1252-1264`).
⚠️ Doc-rot note: the KDoc at `MA:1241-1242` documents `@property lang` and `@property label`
("e.g., 'English 5.1', 'Japanese Stereo'") **which do not exist** in the actual constructor. [verified]

### 7.3 `ExtractorLink` — ALL fields

```kotlin
@Serializable
open class ExtractorLink
@Deprecated("Use newExtractorLink", level = DeprecationLevel.WARNING)
constructor(
    @SerialName("source") open val source: String,
    @SerialName("name") open val name: String,
    @SerialName("url") override val url: String,
    @SerialName("referer") override var referer: String,
    @SerialName("quality") open var quality: Int,
    @SerialName("headers") override var headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob() */
    @SerialName("extractorData") open var extractorData: String? = null,
    @SerialName("type") open var type: ExtractorLinkType,
    /** List of separate audio tracks that can be merged with this video */
    @SerialName("audioTracks") open var audioTracks: List<AudioFile> = emptyList(),
) : IDownloadableMinimum {
    @get:JsonIgnore val isM3u8: Boolean get() = type == ExtractorLinkType.M3U8
    @get:JsonIgnore val isDash: Boolean get() = type == ExtractorLinkType.DASH
    // + @Transient videoSize cache & suspend getVideoSize(timeoutSeconds = 3L) head-request helper (VIDEO only)
    // + getAllHeaders(): merges referer into headers unless a referer key exists
    // + 4 deprecated secondary constructors (old positional/isM3u8/isDash shapes)
}
```
`EA:687-841` [verified]

| Field | Type | Notes |
|---|---|---|
| `source` | `String` | Hosting-site label ("DoodStream") shown in the source picker. |
| `name` | `String` | Quality/entry label shown in the player row ("DoodStream 720p"). |
| `url` | `String` | The playable URL (page-embed resolution already done). |
| `referer` | `String` | Referer header for the media request (also merged into `getAllHeaders()`, `EA:738-746`). |
| `quality` | `Int` | Height in px — a `Qualities.*.value`, NOT a `SearchQuality` (§6.5). |
| `headers` | `Map<String, String>` | Extra request headers (UA, cookies). |
| `extractorData` | `String?` | Token passed to `MainAPI.extractorVerifierJob(data)` — background keep-alive polling while playing (KDoc `MA:669-678`, `EA:578`). |
| `type` | `ExtractorLinkType` | Container hint (§6.6). |
| `audioTracks` | `List<AudioFile>` | Muxable audio tracks (§7.2). |

Builder: `newExtractorLink(source, name, url, type: ExtractorLinkType? = null, initializer)` —
`type ?: INFER_TYPE` → auto-inference from URL (`EA:500-519`). The class is `@Serializable`
(kotlinx `@SerialName` on every field) so links can be persisted (download queue / preview cache).

### 7.4 `DrmExtractorLink`

`open class DrmExtractorLink private constructor(…)` — extends `ExtractorLink` with DRM fields:
`kid: String?`, `key: String?` (both Base64), `uuid: Uuid` (default `CLEARKEY_DRM_UUID`; Widevine/
PlayReady/ClearKey constants at `EA:467-498`), `kty: String? = "oct"`, `keyRequestParameters:
HashMap<String, String>`, `licenseUrl: String?` (`EA:588-604` [verified]; KDoc field list `EA:571-586`).
Built via `newDrmExtractorLink` (Java-UUID variant `EA:526-546`; Kotlin `Uuid` variant is `@Prerelease`
`EA:548-569`). `@Prerelease` ⇒ crashes stable builds by design (doc 03 §8).

### 7.5 `ExtractorLinkPlayList` + `PlayListItem`

```kotlin
data class PlayListItem(
    val url: String,
    val durationUs: Long,   // use Long.toUs() (seconds → microseconds)
)

data class ExtractorLinkPlayList(
    override val source: String,
    override val name: String,
    val playlist: List<PlayListItem>,
    override var referer: String,
    override var quality: Int,
    override var headers: Map<String, String> = mapOf(),
    override var extractorData: String? = null,
    override var type: ExtractorLinkType,
    override var audioTracks: List<AudioFile> = emptyList(),
) : ExtractorLink(source, name, url = "", …)
```
`EA:347-410` [verified] — "If your site has an unorthodox m3u8-like system where there are multiple
smaller videos concatenated use this" (`EA:364-366`); secondary constructor keeps the old
`isM3u8: Boolean = false` shape (`EA:391-409`).

### 7.6 `ExtractorApi` — the extractor base class

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
    ) { getUrl(url, referer)?.forEach(callback) }

    suspend fun getSafeUrl(url, referer, subtitleCallback, callback)  // swallows errors

    @Throws
    open suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? {
        return emptyList()
    }

    open fun getExtractorUrl(id: String): String = id
}
```
`EA:1419-1466` [verified]

- **There is no `VideoExtractor` class** in current master (repo-wide grep, §12) — the assignment brief's
  name is from the old CS3 API; the only extractor contract is `ExtractorApi` above, and there is no
  separate `Video` wrapper either (links are `ExtractorLink` directly).
- Dispatch: `loadExtractor(url, referer, subtitleCallback, callback)` matches `url` against
  `extractor.mainUrl` (schema/www-stripped prefix), iterating `extractorApis` in **reverse registration
  order** ("so the new registered ExtractorApi takes priority" — plugin extractors shadow built-ins),
  then a second fuzzy pass with `Levenshtein.partialRatio > 80` for mirror domains (`EA:914-983`).
- The built-in registry `extractorApis` instantiates ~250 extractor objects at library init
  (`EA:985-1343`) [verified].
- Real example (typical new-style override):

```kotlin
open class DoodLaExtractor : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://dood.la"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String, referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) { // …scrape…
        callback.invoke(
            newExtractorLink(this.name, this.name, trueUrl) {
                this.referer = "$mainUrl/"
                this.quality = getQualityFromName(quality)
            }
        )
    }
}
```
`library/…/extractors/DoodExtractor.kt:95-129` [verified]

### 7.7 `IDownloadableMinimum` — the download contract

```kotlin
interface IDownloadableMinimum {
    val url: String
    val referer: String
    val headers: Map<String, String>
}
```
`MA:2654-2658` [verified] — implemented by `ExtractorLink` (`EA:714`) and by the app-side
`ExtractorSubtitleLink(name, url, referer, headers)` (`app/…/result/ResultViewModel2.kt:363-368`).

### 7.8 App-side bridge models (extractor output → player)

The assignment brief expected `ResultResolution` "likely ui/player or utils" — **it does not exist in
current master** (repo-wide grep, §12; it was the old-API player-bridge). The *actual* bridge chain is:

1. **`ResultEpisode`** — flattened, id-stable episode for UI + player input:

```kotlin
@Serializable
data class ResultEpisode(
    @SerialName("headerName") val headerName: String,
    @SerialName("name") val name: String?,
    @SerialName("poster") val poster: String?,
    @SerialName("episode") val episode: Int,
    @SerialName("seasonIndex") val seasonIndex: Int?, // the "season" index used season names
    @SerialName("season") val season: Int?,            // this is the display
    @SerialName("data") val data: String,
    @SerialName("apiName") val apiName: String,
    @SerialName("id") val id: Int,
    @SerialName("index") val index: Int,
    @SerialName("position") val position: Long,        // time in MS
    @SerialName("duration") val duration: Long,        // duration in MS
    @SerialName("score") val score: Score?,
    @SerialName("description") val description: String?,
    @SerialName("isFiller") val isFiller: Boolean?,
    @SerialName("tvType") val tvType: TvType,
    @SerialName("parentId") val parentId: Int,
    @SerialName("videoWatchState") val videoWatchState: VideoWatchState,
    @SerialName("totalEpisodeIndex") val totalEpisodeIndex: Int? = null,
    @SerialName("airDate") val airDate: Long? = null,
    @SerialName("runTime") val runTime: Int? = null,
    @SerialName("seasonData") val seasonData: SeasonData? = null,
)
```
`app/ui/result/ResultFragment.kt:39-65` [verified] (built via `buildResultEpisode(...)` `:83-130` which
injects stored `position`/`duration`/`videoWatchState` from DataStore).

2. **`RepoLinkGenerator`** — turns `ResultEpisode.data` into a stream of `VideoLink`s by calling
   `APIRepository.loadLinks(...)` (which wraps `MainAPI.loadLinks`) and caching per `(apiName, id)` in
   `data class Cache(linkCache: MutableSet<ExtractorLink>, subtitleCache: MutableSet<SubtitleData>,
   lastCachedTimestamp: Long, saturated: Boolean)` — 20-minute TTL (`app/ui/player/RepoLinkGenerator.kt:15-32, 71-105` [verified]).

3. **`VideoLink`** — `typealias VideoLink = Pair<ExtractorLink?, ExtractorUri?>`
   (`app/ui/player/PlayerGeneratorViewModel.kt:34` [verified]). `ExtractorUri` wraps an Android `Uri`
   plus name/paths/id/parentId/episode/season/headerName/tvType for downloads & TV UI
   (`app/ui/player/LinkGenerator.kt:15-29` [verified]).

4. **`VideoState`** — immutable player state: `subtitles: PersistentSet<SubtitleData>`,
   `links: PersistentSet<VideoLink>`, `erroredLinks`, `stamps: PersistentList<VideoSkipStamp>`,
   `loading: Resource<Unit>`, `generatorState`, `instance`; plus `DisplayLink(link, shouldUseLink,
   priority)` and `GeneratorState(meta, nextMeta, allMeta, response: LoadResponse?, index, id)`
   (`app/ui/player/PlayerGeneratorViewModel.kt:36-63` [verified]).

5. **`SubtitleData`** — player-side subtitle with dedup + mime handling:
   `originalName, nameSuffix, url, origin: SubtitleOrigin, mimeType, headers, languageCode: String?`
   (derived `name = "$originalName $nameSuffix"`) (`app/ui/player/PlayerSubtitleHelper.kt:39-80`
   [verified]). Converted from the library's `SubtitleFile` via `PlayerSubtitleHelper.getSubtitleData`
   (`:111-120`): `originalName = file.lang`, `languageCode = file.langTag ?: file.lang`, mime from URL
   suffix (`.vtt`/`.srt`/`.ttml`, `:101-109`).

Also app-side but link-adjacent: `BasicLink(url, name)` for raw URLs (`LinkGenerator.kt:34-37`),
`ExtractedTrailerData(mirros: List<Pair<ExtractorLink, String>>, subtitles: List<SubtitleFile>)`
(`ResultViewModel2.kt:451-454` — typo "mirros" is upstream's), `LinkLoadingResult(links, subs, syncData)`
(`ResultViewModel2.kt:398-402`). [verified]

---

## 8. Home / mainpage models

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

/** Data class for the Homepage response info. */
data class HomePageResponse
@Deprecated("Use newHomePageResponse method", level = DeprecationLevel.ERROR)
constructor(
    val items: List<HomePageList>,
    val hasNext: Boolean = false
)

/** Data class for the Homepage list info. */
data class HomePageList(
    val name: String,
    var list: List<SearchResponse>,
    val isHorizontalImages: Boolean = false
)
```
`MA:410-421`, `MA:1266-1286` [verified]

- `MainPageData` is what the provider *declares* (property `MainAPI.mainPage: List<MainPageData>`,
  default `listOf(MainPageData("", "", false))` `MA:630`); the DSL helpers `mainPage(url, name,
  horizontalImages)` and two `mainPageOf(...)` overloads (vararg `MainPageData` / `Pair<String,String>`)
  build it (`MA:423-442`).
- The app converts each `MainPageData` → `MainPageRequest` when driving `getMainPage(page, request)`
  (`app/ui/APIRepository.kt:156-196` [verified]) — same three fields, plus the leftover TODO comment at
  `MA:420`.
- The **response** is `HomePageResponse`: a list of named sections (`HomePageList.name`), each carrying
  `list: List<SearchResponse>` + per-section card layout flag `isHorizontalImages`; `hasNext` at the
  top level controls "load more" pagination for the page as a whole.
- Builders (all cap at `MA:444-476`): `newHomePageResponse(name, list, hasNext = null)` (single section),
  `newHomePageResponse(data: MainPageRequest, list, hasNext)` (propagates `horizontalImages`),
  `newHomePageResponse(list: HomePageList, hasNext)`, `newHomePageResponse(list: List<HomePageList>,
  hasNext)` — every overload defaults `hasNext` to "list non-empty"/"any section non-empty".
- A provider may return **multiple sections per request** (that's why `items` is a list) — e.g. returning
  both "Trending" and "New" from one page fetch. The app's `HomeViewModel` flattens; per-section
  pagination then relies on `hasNext` + the request's `data` discriminator. [inferred from APIRepository
  shape; UI behavior is B2-a scope]

---

## 9. Filter models

**There is NO active filter system in current master.** Verified two ways:

1. The entire selector/filter model block sits inside a `/* … */` comment titled
   "// THIS IS WORK IN PROGRESS API" (`MA:333-383` [verified]). It contains — all commented out:

```kotlin
// interface ITag { val name: UiText }
// data class SimpleTag(override val name: UiText, val data: String) : ITag
// enum class SelectType { SingleSelect, MultiSelect, MultiSelectAndExclude }
// enum class SelectValue { Selected, Excluded }
// interface GenreSelector { val title: UiText; val id: Int }
// data class TagSelector(title, id, tags: Set<ITag>, defaultTags: Set<ITag> = setOf(),
//     selectType: SelectType = SelectType.SingleSelect) : GenreSelector
// data class BoolSelector(title, id, defaultValue: Boolean = false) : GenreSelector
// data class InputField(title, id, hint: UiText? = null) : GenreSelector
// data class GenreResponse(searchSelectors: List<GenreSelector>,
//     filterSelectors: List<GenreSelector> = searchSelectors)
```
`MA:333-383` [verified — as comments]

2. Agent 40-B1-c's greps confirmed `getFilterList`/`FilterList` appear **nowhere** in current MainAPI
   (doc 03 §"absences"); a `//TODO genre selection or smth` note survives on `MainPageRequest` (`MA:420`).
   Note `UiText` (the WIP block's string wrapper) meanwhile lives only app-side
   (`app/…/utils/TextUtil.kt:12`, sealed class with `DynamicString`/`StringResource` + `txt()` builders
   `:76-94`) [verified].

For ANI-KUTA: **do not design our Cloud Screen filter layer against CS3's** — nothing here is load-bearing.
The only real "filters" today are app-side: TvType chips + provider language + NSFW gate in the extensions
browser (doc 04 §browsing) and search is a plain query string.

---

## 10. Serialization notes

### 10.1 Dual JSON stack (library-global)

```kotlin
val json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

val mapper = JsonMapper.builder().addModule(kotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()!!
```
`MA:100-107` [verified] — kotlinx.serialization **preferred**, Jackson Kotlin as fallback.

The canonical helpers (used by plugins AND app) live in `AppUtils`:
- `Any.toJson()` / `Any.toJsonLiteral()` — picks the kotlinx serializer when the class is
  `@Serializable`/contextual, else falls back to `mapper.writeValueAsString` (`AU:19-42`).
- `parseJson<T>(value)` — kotlinx first, then `mapper.readValue` (`AU:60-79`); `parseJson(value, kClass)`
  (`AU:45-56`); null-tolerant `tryParseJson` (`AU:92-98`).
- A comment warns: "This is inlined code and can easily cause breakage in extensions! Watch out when
  editing this to make sure stable also supports all inlined code!" (`AU:58-59`) — **plugin ABI
  sensitivity**: inline functions from the library are compiled INTO plugins, so their bytecode must keep
  working against old plugins. [verified]

### 10.2 Which payload models are `@Serializable`

Library payloads annotated `@Serializable` (kotlinx) + dual Jackson/kotlinx property annotations:
`ProvidersInfoJson` (`MA:397-403`), `SettingsJson` (`MA:405-408`), `Score` (`MA:917-922`),
`AudioFile` (`MA:1245-1250`), `SeasonData` (`MA:2226-2231`), `ExtractorLink` (`EA:699-714`),
`AniSearch` + nested (`MA:2792-2833`).
**NOT** `@Serializable` (Jackson-only if serialized at all): every `SearchResponse`/`LoadResponse`
implementation, `Episode`, `SubtitleFile`, `HomePageList`, `TrailerData`, `NextAiring`,
`Actor`/`ActorData` — these serialize via the Jackson fallback path (or get re-wrapped by the app into
its own `@Serializable` persistence models, §10.3). [verified — annotation grep]

### 10.3 Persistence of favorites / watched / resume (app → DataStore/SharedPreferences)

The app never persists provider response classes directly; it maps them into dedicated
`@Serializable` `LibrarySearchResponse` subclasses in `DataStoreHelper`:

| Model | Persisted fields beyond `SearchResponse` base | Citation |
|---|---|---|
| `abstract class LibrarySearchResponse` | `latestUpdatedTime: Long`, `year`, `syncData`, `plot`, `tags` (+ write-only legacy `rating` bridge) — base fields `@Transient` because "this class is only ever serialized through its subclasses, which redeclare each property with their own @SerialName" | `app/…/utils/DataStoreHelper.kt:263-305` |
| `SubscribedData` | `subscribedTime: Long`, `lastSeenEpisodeCount: Map<DubStatus, Int?>` | `:310-369` |
| `BookmarkedData` | `bookmarkedTime: Long` | `:373-430` |
| `FavoritesData` | `favoritesTime: Long` | `:435-491` |
| `ResumeWatchingResult` | `watchPos: PosDur?`, `parentId: Int?`, `episode: Int?`, `season: Int?`, `isFromDownload: Boolean` | `:494-509` |
| `PosDur` | `position: Long`, `duration: Long` (ms) | `:244-247` |

Each subclass uses `@KeepGeneratedSerializer` + a custom `WriteOnlySerializer` that drops the legacy
`rating` key on write (`setOf("rating")`, e.g. `:343-346`) — that's the `Score` migration mechanism.
Storage keys (account-namespaced): `RESULT_WATCH_STATE`, `RESULT_WATCH_STATE_DATA`,
`RESULT_SUBSCRIBED_STATE_DATA`, `RESULT_FAVORITES_STATE_DATA`, `RESULT_RESUME_WATCHING`,
`VIDEO_POS_DUR`, `VIDEO_WATCH_STATE`, `RESULT_DUB`, `RESULT_SEASON`, `RESULT_EPISODE`
(`DataStoreHelper.kt:516-818` passim [verified]). All of it lands in **SharedPreferences via
`setKey` → `value?.toJsonLiteral()`** (`app/…/utils/DataStore.kt:173-181`) and reads back via
`getKey` → `parseJson(json, kClass)` (`:183-190`) — i.e. the §10.1 dual stack over plain prefs strings.

### 10.4 In-memory caches of provider models

- `APIRepository.Companion` keeps a **rolling cache of 20 `SavedLoadResponse(unixTime, response:
  LoadResponse, hash: Pair<String,String>)`** with a 10-minute freshness window
  (`app/ui/APIRepository.kt:52-60, 92-118` [verified]).
- `RepoLinkGenerator` keeps `Cache(linkCache: MutableSet<ExtractorLink>, subtitleCache:
  MutableSet<SubtitleData>, …)` per `(apiName, episodeId)` with a 20-minute TTL
  (`app/ui/player/RepoLinkGenerator.kt:15-32, 71-105` [verified]).
- Load-response **identity** for both caches and DataStore is `LoadResponse.getId()` =
  `getLoadResponseIdFromUrl(uniqueUrl, apiName)` = `(url minus provider mainUrl).hashCode()`
  (`app/ui/result/ResultViewModel2.kt:370-379` [verified]) — a 32-bit hash, not the provider's `id` field.

### 10.5 The `dataUrl`/`Episode.data` JSON blobs

`MovieLoadResponse.dataUrl`, `LiveStreamLoadResponse.dataUrl`, `TorrentLoadResponse.magnet/torrent` and
`Episode.data` routinely carry **arbitrary JSON serialized with `toJson()`** (generic builders `MA:2491-2519`,
`MA:2637-2652`). These blobs are opaque to the app; only the originating provider's `loadLinks` re-parses
them. Round-trip uses the §10.1 stack ⇒ whatever a plugin serializes must re-parse across the same
dual stack — stick to plain data classes. [verified]

### 10.6 Tracker JSON (`AniSearch`) & `syncData`

`APIHolder.getTracker` hits AniList GraphQL and parses into the `@Serializable` nested
`AniSearch.Data.Page.Media{title{romaji,english}, id, idMal, seasonYear, format, coverImage{extraLarge,
large}, bannerImage}` model (`MA:2792-2833` [verified]) — this is the enrichment source behind
`applyMeta` (`app/…/ResultViewModel2.kt:1716-1758`: writes mal/anilist/kitsu ids into `syncData` and
back-fills `posterUrl`/`backgroundPosterUrl`). `syncData` itself is only ever string→string.

---

## 11. Model map vs ANI-KUTA provider-api

Our models (read in full, `core/provider-api/.../providerapi/`):
`Source(ecosystemId, sourceId, name, lang, isNsfw)` (`Source.kt:12-24`),
`SourceContent(sourceKey, externalId, title, thumbnailUrl?, url?)` (`SourceContent.kt:12-24`),
`SourceContentDetails(sourceKey, externalId, title, description?, genres?, status?, thumbnailUrl?,
bannerUrl?, year?, author?, artist?, episodes)` (`SourceContentDetails.kt:6-21`),
`SourceEpisode(contentKey, externalId, number: Double, name, url?, thumbnailUrl?, dateUpload?)`
(`SourceEpisode.kt:14-28`), `SourceVideo(url, quality: String = "Default", videoUrl?)`
(`SourceVideo.kt:10-14`), plus provider interfaces (`VideoExtensionProvider.kt:22-73`,
`FutureProviders.kt:14-47`) and `ContentType { VIDEO, IMAGE, TEXT }`
(`core/common/.../ContentType.kt:11-15`). All [verified].

### 11.1 Mapping table (CS3 → ANI-KUTA)

| CS3 model | ANI-KUTA model | Fit / notes |
|---|---|---|
| `MainAPI` (name/mainUrl/lang/supportedTypes) | `Source(ecosystemId, sourceId, name, lang, isNsfw)` + `ExtensionProvider` | We separate identity (`Source`) from contract (interface); CS3 fuses both. `isNsfw` ≈ CS3 `TvType.NSFW` in supportedTypes + `SettingsJson.enableAdult`. |
| `TvType` (18 values) | `ContentType` (3 values) + implicit "anime" specialization | `[gap]` Coarse-grained: CS3's movie/live/drama/documentary/audio taxonomy has no home. Need a Cloud-Screen-specific `ContentKind` layer above `ContentType`. |
| `SearchResponse` (interface) | `SourceContent` | Direct. CS3 `apiName` → implied by `sourceKey`; CS3 `type`/`quality`/`score`/`posterHeaders`/`id` → `[gap]` (see below). |
| `MovieSearchResponse.year` / `TvSeriesSearchResponse.{year,episodes}` / `AnimeSearchResponse.{year,dubStatus,otherName,episodes}` / `LiveSearchResponse.lang` | — | `[gap]` `SourceContent` has no year/count/lang fields; Cloud Screen cards will lose badges unless we add a `SearchExtras`-style bag or extend `SourceContent`. |
| `LoadResponse` (interface) | `SourceContentDetails` | Partial — see field rows. |
| `LoadResponse.name/title/plot/year` | `SourceContentDetails.title/description/year` | ✅ direct. |
| `LoadResponse.posterUrl`/`posterHeaders` | `SourceContentDetails.thumbnailUrl` | ⚠️ poster has no headers field in ours → `[gap]` (hotlink-protected CS3 posters will fail to load without it). |
| `LoadResponse.backgroundPosterUrl` | `SourceContentDetails.bannerUrl` | ✅ near-direct. |
| `LoadResponse.logoUrl` | — | `[gap]` no logo/title-image concept. |
| `LoadResponse.score: Score?` | — | `[gap]` no rating field at all. CS3 `Score` (fixed-point, scale-free) is a design worth stealing verbatim. |
| `LoadResponse.tags: List<String>?` | `SourceContentDetails.genres: List<String>?` | ✅ same shape. |
| `LoadResponse.duration` (minutes) | — | `[gap]`. |
| `LoadResponse.contentRating` | — | `[gap]`. |
| `LoadResponse.comingSoon` | — | `[gap]` (our details model can't express "not yet playable"; CS3 auto-derives it). |
| `LoadResponse.syncData: MutableMap<String,String>` | — | `[gap]` no cross-service id map (our identity is `contentKey` string concat only). |
| `LoadResponse.uniqueUrl` | `SourceContent.externalId` + `SourceContent.url` | ⚠️ semantic near-match: ours = "<sourceKey>:<externalId>" key; CS3 separates the *storage key* (`uniqueUrl`) from the *mutable url* (`url`) — worth mirroring if a provider's urls rotate. |
| `LoadResponse.recommendations: List<SearchResponse>?` | — | `[gap]` (one level of recursion in CS3). |
| `LoadResponse.trailers: MutableList<TrailerData>` | — | `[gap]`. |
| `LoadResponse.actors: List<ActorData>?` + `Actor`/`ActorRole` | — | `[gap]` whole people model (also `author`/`artist` in ours have no CS3 counterpart on the *provider* side — they come from manga metadata). |
| `EpisodeResponse.showStatus: ShowStatus?` | `SourceContentDetails.status: String?` | ⚠️ ours is free text ("Ongoing"/"Completed" by convention); CS3 is a 2-value enum. Map `ShowStatus.name` into the string, but our Cloud Screen should keep the enum. |
| `EpisodeResponse.nextAiring: NextAiring?` | — | `[gap]`. |
| `EpisodeResponse.seasonNames: List<SeasonData>?` | — | `[gap]` (see seasons row). |
| `Episode` (data,name,season,episode,posterUrl,score,description,date,runTime) | `SourceEpisode(contentKey, externalId, number: Double, name, url?, thumbnailUrl?, dateUpload?)` | ⚠️ ours keys episodes by `externalId` + fractional `number`; CS3 keys by opaque `data` string + optional season/episode ints. CS3 `score`/`description`/`runTime` → `[gap]`. Episode *identity strategy differs fundamentally* — the CS3 `data` blob must be persisted as our `externalId` (it's the only stable handle for loadLinks). |
| Seasons: flat `List<Episode>` + `seasonNames` overlay + `DubStatus` map split | flat `List<SourceEpisode>` with `number` only | `[gap]` no season concept, no dub/sub split. CS3's `SeasonData{season,name,displaySeason}` + `getTotalEpisodeIndex` display logic must be reimplemented in the Cloud Screen layer (NOT pushed into provider-api — it's CS3-specific). |
| `SubtitleFile(lang, url, headers)` | — | `[gap]` no subtitle model in provider-api (our current aniyomi path doesn't surface subs either — player-level only). |
| `AudioFile(url, headers)` | — | `[gap]`. |
| `ExtractorLink(source,name,url,referer,quality:Int,headers,extractorData,type,audioTracks)` | `SourceVideo(url, quality: String = "Default", videoUrl?)` | ⚠️ our model is 3 fields; CS3's is 9. Biggest single mapping loss — see §11.2. |
| `DrmExtractorLink` / `ExtractorLinkPlayList` | — | `[gap]` (DRM + concatenated-playlist links have no representation). |
| `ExtractorApi` (abstract class) | — (extractors hidden behind `fetchVideoList`) | `[gap]` our provider-api has no extractor concept; CS3 extractors are independently registerable. Doc 16 must decide: wrap `ExtractorApi` inside the Cloud Screen provider, or extend provider-api. |
| `MainPageData`/`MainPageRequest`/`HomePageResponse`/`HomePageList` (named sections + hasNext + horizontalImages) | `fetchContentList(source, page, query): Flow<List<SourceContent>>` | `[gap]` ours is one flat page of items; no named sections, no per-section layout flag, no hasNext (our paging is "empty page = end" style). |
| `SearchResponseList(items, hasNext)` | implicit (Flow list) | ⚠️ our Flow needs a terminal/hasNext signal for CS3 — either a wrapper or "empty = done" convention. |
| `Score` value type | — | `[gap]` (recommended adopt-as-is). |
| `SearchQuality` (release badges) | — | `[gap]` optional. |
| `Qualities` (resolution ints) | `SourceVideo.quality: String` | ⚠️ string vs int; map via `Qualities.getStringByInt` / `getQualityFromName`. |
| `WatchType`/`SyncWatchType` (app-side) | (our watch-state lives in Room, out of provider-api scope — doc 15) | n/a for provider-api, relevant for docs 15/17. |

### 11.2 The biggest gaps, ranked

1. **`SourceVideo` vs `ExtractorLink`** — 9 fields → 3. Referer/headers (hotlink protection), type hint
   (M3U8/DASH), extractorData (keep-alive tokens), audioTracks, and source/label split are all lost.
   `SourceVideo.videoUrl` can carry the resolved URL, but a faithful Cloud Screen needs either a richer
   provider-api video model or a CS3-scoped wrapper type.
2. **People** — `Actor`/`ActorData`/`ActorRole` have zero equivalents.
3. **Result-page metadata** — `backgroundPosterUrl` (≈bannerUrl ✅) but `logoUrl`, `score`, `duration`,
   `contentRating`, `nextAiring`, `trailers`, `recommendations`, `comingSoon`, `syncData` are all missing
   from `SourceContentDetails`.
4. **Season/dub structure** — flat-with-`SeasonData`-overlay + `DubStatus` split vs our number-only
   episodes.
5. **Named home sections + hasNext pagination** vs our flat `Flow<List<…>>`.
6. **Poster request headers** (`posterHeaders`) — without it, protected CS3 images 403.
7. **Episode description/score/runtime** on `SourceEpisode` (we keep only name/thumb/date).

### 11.3 What we already do differently (keep, don't port)

- Our `ecosystemId`/`sourceKey`/`contentKey`/`episodeKey` string identity is *stronger* than CS3's
  name-based `apiName` + 32-bit url-hash ids (doc 03 §identity flagged the same weakness).
- Our `Flow`-based provider contract vs their suspend-throw contract — an impedance difference the
  Cloud Screen adapter layer (doc 16) must bridge, not provider-api.

---

## 12. Could not verify / confirmed absent

### 12.1 Confirmed absent in current master (repo-wide grep, `library/src` + `app/src/main`)

| Searched | Result |
|---|---|
| `ResultResolution` | **0 hits** anywhere (kotlin/md/json). The assignment's expected bridge model does not exist; §7.8 documents the actual chain (`ResultEpisode` → `RepoLinkGenerator` → `VideoLink`/`VideoState`). [verified absence] |
| `VideoExtractor` | **0 hits** — old-API name; the only extractor contract is `ExtractorApi` (`EA:1419`). [verified absence] |
| Top-level `Video` class | **0 hits** (only `VideoSource`/`VideoInfo`/`VideoResponse` etc. private extractor-internal JSON DTOs). [verified absence] |
| `Genre` enum / `GenreData` / active `GenreSelector` | Only inside the WIP comment block `MA:333-383`. [verified absence] |
| `EpisodeLoadResponse` / generic `SeriesResponse` | Do not exist; only the five §3.2 classes. [verified absence] |
| `label`/`source` fields on `SubtitleFile` | Real shape is lang/url/headers (§7.1). [verified absence] |
| `lang`/`label` on `AudioFile` | KDoc claims them (`MA:1241-1242`) but constructor has only url/headers — doc rot upstream. [verified] |

Complete `enum class` census of `library/src` (17 hits): `SelectType`*, `SelectValue`*,
`ProviderType`, `VPNStatus`, `ShowStatus`, `DubStatus`, `TvType`, `AutoDownloadMode`, `SearchQuality`,
`ActorRole`, `SimklSyncServices`, `TrackerType` (all MainAPI.kt), `SyncIdName` (syncproviders),
`ExtractorLinkType`, `Qualities` (ExtractorApi.kt) — `*` = commented-out WIP only; plus two `private`
internal enums (`JsInterpreter.kt:212 TT`, `Levenshtein.kt:82 EditType`) that are not API. [verified]

### 12.2 Unverifiable / left to other docs

- **SearchAdapter/卡片 badge rendering details** — which concrete search class shows which badge in the
  UI: fields verified, adapter layout inspection deferred to B2-a (doc 06).
- **`HomeViewModel` section-flattening/pagination behavior** — §8 documents the model shapes and the
  APIRepository driver; full UI loop is doc 06 scope.
- **App-side `AccountManager` idPrefix values** — `syncData` keys are assigned at app startup; exact
  prefix strings not read here (doc 13 scope). `[inferred]` from `MA:1854-1858` comments.
- **`DownloadObjects.DownloadHeaderCached`** & download queue models — cited in passing
  (`ResultViewModel2.kt:2681-2689`); full catalog is doc 09 scope.
- **Exact jar/ABI serialization compat** for inline `parseJson` across plugin versions — mechanism
  verified (`AU:58-59` warning), real-world breakage cases not studied (doc 03 §8 covers the ABI model).

### 12.3 Line-number caveat

All citations reference master @ efc1915 (clone 2026-08-29). `MainAPI.kt` moves frequently upstream;
re-grep by symbol name if lines drift.
