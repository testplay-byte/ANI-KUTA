# 12 — Real Plugin Examples: A Field Guide to Actual Provider Implementations

> **Scope**: how *real* plugin authors write CloudStream (CS3) providers — six full
> implementation walkthroughs across content categories (movie/series HTML scraper, Asian drama
> hybrid, REST-JSON movie API, GraphQL drama API, anime + custom-extractor swarm, official
> live-video provider), plus a census of 58 provider classes and a pattern synthesis ("the field
> guide") for implementers.
> **Sources**: `research/CakesTwix-ext` (Ukrainian community repo), `research/storm-ext`
> (Spanish/Latino community repo), `research/extensions` (CloudStream re-build "official-quality"
> repo), `research/TestPlugins` (official template). All read-only.
> **Related docs**: API mechanics live in **03** (contract) and **05** (data models); mainPage/search
> flow in **06**; the `load()` → result-UI pipeline and posterHeaders in **07**;
> extractor registry mechanics in **08 §6**. This doc covers the *authoring patterns and quality
> spectrum* on top of those contracts — mainPage mechanics are only re-explained where a provider
> does something 06 didn't cover.

---

## Table of contents

0. [Method & source map](#0-method--source-map)
1. [Census: 58 providers examined](#1-census-58-providers-examined)
2. [Deep-dive: UakinoProvider — movie/series, DLE HTML scraper (CakesTwix)](#2-deep-dive-uakinoprovider--movieseries-dle-html-scraper-cakestwix)
3. [Deep-dive: DoramyWorldProvider — Asian drama, HTML + embedded-JSON hybrid (CakesTwix)](#3-deep-dive-doramyworldprovider--asian-drama-hybrid-html--embedded-json-cakestwix)
4. [Deep-dive: AllCalidadProvider + in-plugin extractors — movies, REST JSON (storm-ext)](#4-deep-dive-allcalidadprovider--in-plugin-extractors--movies-rest-json-storm-ext)
5. [Deep-dive: DoramasFlixProvider — Asian drama, GraphQL JSON (storm-ext)](#5-deep-dive-doramasflixprovider--asian-drama-graphql-json-storm-ext)
6. [Deep-dive: AnimeJlProvider + its 57 custom extractors — anime (storm-ext)](#6-deep-dive-animejlprovider--its-57-custom-extractors--anime-storm-ext)
7. [Deep-dive: TwitchProvider — the official-quality bar (recloudstream)](#7-deep-dive-twitchprovider--the-official-quality-bar-recloudstream)
8. [The template baseline: ExampleProvider](#8-the-template-baseline-exampleprovider)
9. [Pattern synthesis — the field guide](#9-pattern-synthesis--the-field-guide)
10. [What ANI-KUTA's Cloud Screen must support](#10-what-ani-kutas-cloud-screen-must-support)
11. [Could not verify](#11-could-not-verify)

---

## 0. Method & source map

Six providers were read **line-by-line** (provider + plugin class + helpers + extractors + tests):

| Key | File | Lines | Category |
|---|---|---|---|
| `UAK` | `CakesTwix-ext/UakinoProvider/src/main/kotlin/com/lagradost/UakinoProvider.kt` | 430 | movie/series HTML |
| `UAK-PARS` | `…/UakinoProvider/…/UakinoParsing.kt` | 70 | (helpers) |
| `UAK-T` | `…/UakinoProvider/src/test/kotlin/com/lagradost/UakinoParsingTest.kt` (+`UakinoTrailerParsingTest.kt`, 71) | 130 | (unit tests) |
| `DW` | `CakesTwix-ext/DoramyWorldProvider/src/main/kotlin/com/lagradost/DoramyWorldProvider.kt` | 204 | drama hybrid |
| `AC` | `storm-ext/AllCalidadProvider/src/main/kotlin/com/stormunblessed/AllCalidadProvider.kt` | 307 | movie JSON |
| `AC-E` | `…/AllCalidadProvider/…/Extractor.kt` | 73 | (2 extractors) |
| `DFX` | `storm-ext/DoramasFlixProvider/src/main/kotlin/com/stormunblessed/DoramasFlixProvider.kt` | 356 | drama GraphQL |
| `AJL` | `storm-ext/AnimeJlProvider/src/main/kotlin/com/stormunblessed/AnimeJlProvider.kt` | 220 | anime HTML |
| `AJL-P` | `…/AnimeJlProvider/…/AnimeJlProviderPlugin.kt` | 76 | (57 extractor registrations) |
| `AJL-EX` | `…/AnimeJlProvider/…/extractors/{StreamWishExtractor,ByseSX,VidHidePro,Filesim,VidStack}.kt` | 591 | (5 families) |
| `TW` | `extensions/TwitchProvider/src/main/kotlin/recloudstream/TwitchProvider.kt` | 179 | live video-site |
| `DM` | `extensions/DailymotionProvider/src/main/kotlin/recloudstream/DailymotionProvider.kt` | 114 | video-site (secondary) |
| `EX`/`EX-P` | `TestPlugins/ExampleProvider/src/main/kotlin/com/example/{ExampleProvider,ExamplePlugin}.kt` | 20/24 | template |

`MA` = CloudStream `library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt` (same
key used by docs 03/05/06). All 58 provider *class declarations* were additionally skimmed for the
census (§1). `[verified]` = read directly in source; `[inferred]` = reasoned, not directly provable
from source.

**Headline findings up front** (details in sections):

- **All 58 examined providers set `hasMainPage = true`** — the main page is not optional in
  practice [verified, §1].
- **Zero of the 58 providers ship user settings** — `openSettings`/`registerSettingsAPI` appear
  only in the *template* (`ExamplePlugin.kt:18-23`) and in CakesTwix's `SyncPlugin` (an
  account/backup plugin, not a content provider) [verified, grep across all four repos].
- **None of the six deep-dive providers implements the paginated `search(query, page)` overload**
  — all use the legacy `search(query)` [verified]; only `Dailymotion` (official repo) paginates
  (`DM:65-71`).
- **Two quality tiers are clearly visible**: community providers rely on the app's defensive
  defaults; the official-repo Twitch provider is the only one that deliberately throttles
  (`TW:82`), sets explicit `hasNext=false` (`TW:45`), and throws a *user-readable* error
  (`TW:102-104`).

---

## 1. Census: 58 providers examined

Columns: **MP rows** = `mainPageOf` row count (0 = no `mainPage` override → default single empty
row, `MA:630`); **QS** = `hasQuickSearch = true` (⚠ = declared but `quickSearch` NOT overridden →
default throws `NotImplementedError`, `MA:656-658` — hazard documented in 03 §2.7); **CE** =
count of `registerExtractorAPI` calls in the plugin class; **Style** = dominant fetch strategy
(`.document`/Jsoup vs `parsed<>`/JSON; hybrid = both). Style for non-deep-dive rows is classified
by grep density and marked `[inferred]`.

### 1.1 storm-ext (35 providers, Spanish/Latino)

| Provider | Category | lang | supportedTypes | MP rows | QS | CE | Style |
|---|---|---|---|---|---|---|---|
| AllCalidad **†** | movie+series+anime | mx | Movie, TvSeries, Anime | 18 | – | 2 | JSON API [verified] |
| AnimeAV1 | anime | mx | AnimeMovie, OVA, Anime | 0 | ✓ | – | hybrid |
| AnimeJl **†** | anime | mx | Anime | 4 | – | 57 | HTML (Cloudflare) [verified] |
| Animeflv.net | anime | mx | AnimeMovie, OVA, Anime | 0 | ✓ | – | hybrid |
| Area Documental | documentary | mx | Documentary, Movie, TvSeries | 5 | ✓ | – | HTML |
| CablevisionHd | live IPTV | mx | Live | 0 | ✓ | – | HTML |
| Catálogo General | catalog (TMDb) | mx | Movie, TvSeries | 0 | – | – | JSON (extends `TmdbProvider`) |
| Catálogo HBO Max | catalog | mx | Movie, TvSeries | 0 | – | – | JSON |
| Catálogo Infantil | catalog/kids | mx | Movie, TvSeries, Cartoon | 0 | – | – | JSON |
| Catálogo Netflix | catalog | mx | Movie, TvSeries | 0 | – | – | JSON |
| CineHdPlus | movie+series | mx | Movie, TvSeries | 4 | ✓ | 1 | HTML |
| Cinecalidad | movie+series | mx | Movie, TvSeries | 3 | – | 2 | HTML; `vpnStatus = MightBeNeeded` |
| Cuevana | movie+series | mx | Movie, TvSeries | 4 | – | – | HTML |
| DeporTV | live sports | mx | Live | 1 | ✓ | – | hybrid (17×doc/10×json) |
| DoramasFlix **†** | drama | mx | AsianDrama | 0 ⚠ | ✓⚠ | – | GraphQL JSON [verified] |
| DoramasYT | drama | mx | AsianDrama | 0 | ✓ | – | HTML |
| EntrePeliculasySeries | movie+series | mx | Movie, TvSeries | 3 | – | – | HTML; `vpnStatus` |
| HDFull | movie+series | es | Movie, TvSeries | 0 | – | 2 | HTML; companion-object hardcoded login cookie (`HDFullProvider.kt:22-24`) |
| JKAnime | anime | es | AnimeMovie, OVA, Anime | 0 | – | – | hybrid |
| LACartoons | cartoon | mx | Cartoon, TvSeries | 8 | – | 2 | HTML |
| LaMovie | movie | mx | Movie, TvSeries, Anime | 5 | ✓ | – | JSON (wp-api) |
| LatAnime | anime | mx | AnimeMovie, OVA, Anime | 0 | ✓ | – | HTML |
| Monoschinos | anime | mx | Anime, AnimeMovie, OVA | 2 | ✓ | – | HTML |
| MundoDonghua | anime (donghua) | es | Anime, AnimeMovie, OVA | 0 | ✓ | – | HTML |
| PeliculasFlix | movie | es | Movie | 0 | – | – | hybrid |
| Pelispedia | movie+series | mx | Movie, TvSeries | 0 | – | – | HTML |
| Pelisplus4K | movie+series | mx | Movie, TvSeries, AsianDrama, Anime | 4 | – | 57 | HTML |
| PelisplusHD | movie+series | mx | Movie, TvSeries | 0 | – | – | HTML |
| ReyDonghua | anime (donghua) | mx | Anime | 2 | – | – | HTML |
| SeriesMetro | series | mx | TvSeries | 0 | ✗ | – | HTML |
| Seriesflix | series | es | Movie, TvSeries | 0 | – | – | HTML |
| SoloLatino | multi | mx | Movie, TvSeries, Anime, Cartoon | 4 | – | – | HTML |
| Streamed Sports | live sports | en | Live | 17 | ✓ | 1 | JSON |
| TioAnime | anime | es | AnimeMovie, OVA, Anime | 0 | ✓ | – | HTML |

**†** = deep-dive in this doc. Column data [verified] for † rows; others [inferred] from
class-declaration greps (`override var lang`, `supportedTypes`, `mainPageOf` row counting via
script). 27/35 declare `lang = "mx"` — a country code where the language is Spanish; 7 use `es`,
1 `en` [verified count via grep; consistent with doc 10 §4's "mx" finding].

### 1.2 CakesTwix-ext (20 providers + SyncPlugin, all Ukrainian `lang = "uk"`)

| Provider | Category | supportedTypes | MP rows | QS | CE | Style |
|---|---|---|---|---|---|---|
| AnimeON | anime | Anime, AnimeMovie, OVA | 2 | ✓ | – | JSON (`$mainUrl/api/anime`) |
| AnimeUA | anime | Anime, AnimeMovie, OVA | 5 | – | – | hybrid |
| Anitubeinua | anime | AnimeMovie, Anime | 1 | ✓ | – | hybrid |
| BambooUA | anime+drama | Anime, AsianDrama | 9 | ✓ | – | HTML |
| CikavaIdeya | movie | Movie, TvSeries, Cartoon | 4 | ✓ | – | HTML (DLE + `dle_login_hash`) |
| Coaninet | anime | **TvSeries only** | 2 | – | – | HTML |
| DoramyWorld **†** | drama+movie | AsianDrama, Movie | 3 | ✓ | – | hybrid [verified] |
| Eneyida | movie | Movie, TvSeries, Anime | 5 | ✓ | – | hybrid |
| HentaiUkr | NSFW | **NSFW only** | 1 | ✓ | – | JSON (`search/objects.json`) |
| KinoTron | movie | Movie, Cartoon, TvSeries, Anime | 5 | ✓ | – | hybrid |
| KinoVezha | movie | Movie, Cartoon, TvSeries | 4 | ✓ | – | hybrid |
| Kinostrain | movie | TvSeries, Cartoon, Movie, Anime | 12 | – | – | HTML |
| KlonTV | multi | Anime, TvSeries, Cartoon, Movie | 5 | ✓ | – | hybrid |
| Serialno | movie | Movie, Cartoon, TvSeries, Anime | 3 | ✓ | – | hybrid |
| SimpsonsUA | cartoon (single show!) | Cartoon, TvSeries | 2 | ✓ | – | HTML |
| UAFlix | movie | TvSeries, Cartoon, Movie, Anime | 0 | ✓ | – | hybrid |
| UASerialsPro | movie | TvSeries, Cartoon, Movie, Anime | 6 | ✓ | – | HTML |
| UFDub | multi (6 types) | AnimeMovie, Anime, AsianDrama, Movie, Cartoon, TvSeries | 6 | ✓ | – | HTML |
| Uakino **†** | movie | Movie, TvSeries, Anime | 6 | ✓ | – | HTML (DLE) [verified] |
| Unimay | anime | Anime, AnimeMovie | 2 | ✓ | – | JSON (`api.unimay.media`) |

*(SyncPlugin = account/backup plugin with `openSettings` + registerSettingsAPI — the repo's only
settings surface; not a content provider.)*

### 1.3 extensions (recloudstream rebuild) + template

| Provider | Category | lang | supportedTypes | MP rows | QS | CE | Style |
|---|---|---|---|---|---|---|---|
| Twitch **†** | live video-site | uni | Live | 2 | – | 1 | HTML + external link API [verified] |
| Dailymotion | video-site | en | Others | 0* | – | – | JSON API [verified] |
| ExampleProvider (template) | – | en | Movie | 0** | – | – | none (stub) |

\* overrides `getMainPage` but not `mainPage` → runs on the default single empty row (`DM:50`,
`MA:630`). \** `hasMainPage = true` with **no** `mainPage`/`getMainPage` at all
(`EX:15-19`) — as shipped, would throw `NotImplementedError` if browsed (anti-pattern, §8).

### 1.4 Census-level observations

1. **`supportedTypes` clusters**: anime providers use `{Anime, AnimeMovie, OVA}` (9 providers);
   movie/series use `{Movie, TvSeries}` ± `Cartoon`/`Anime` (the default-ish set); AsianDrama
   providers declare it *alone* (DoramasFlix, DoramasYT) or with Movie (DoramyWorld). UFDub is the
   widest (6 types) [verified].
2. **Declared-vs-runtime mismatch exists**: Uakino declares `Movie, TvSeries, Anime` (`UAK:23`)
   but at *runtime* infers `AnimeMovie`, `Cartoon`, `AsianDrama`, `OVA`→else in `load()`
   (`UAK:160-177`) — types not in its declared set. Coaninet does the reverse (anime site
   declaring only `TvSeries`, `CoaninetProvider.kt:53-57`). This confirms doc 10's three-layer
   finding: nothing cross-validates layers.
3. **Custom extractors are the minority**: 9 of 58 plugins register extractor APIs, and 2 of those
   (AnimeJl, Pelisplus4K) register **57 each** — a long-tail distribution [verified, §1 counts].
4. **QuickSearch**: 32/58 declare `hasQuickSearch = true`; most simply alias
   `quickSearch = search` (`UAK:97`, `DW:95`) [verified for deep-dives, inferred for census].

---

## 2. Deep-dive: UakinoProvider — movie/series, DLE HTML scraper (CakesTwix)

The richest *HTML* provider in the sample: 430 lines of provider + 70 lines of extraction helpers
**+ 130 lines of JVM unit tests** — the only tested provider in the census [verified].

### 2.1 Class declaration & capability flags

```kotlin
class UakinoProvider : MainAPI() {
    override var mainUrl = "https://uakino.best"
    override var name = "Uakino"
    override val hasMainPage = true
    override var lang = "uk"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
```
`UAK:14-23` [verified]. Note `hasDownloadSupport = true` is the default anyway (`MA` §2.1);
stating it is documentation, not behavior. The site is a **DLE (DataLife Engine)** site — search
goes through DLE's `do=search` POST contract, and the episode playlist comes from DLE's
`engine/ajax/playlists.php` endpoint (§2.5).

### 2.2 Browsing: 6 URL-prefix rows + name-based client-side filtering

```kotlin
override val mainPage =
    mainPageOf(
        "$mainUrl/filmy/page/" to "Фільми",
        "$mainUrl/seriesss/page/" to "Серіали",
        "$mainUrl/seriesss/doramy/page/" to "Дорами",
        "$mainUrl/cartoon/page/" to "Мультфільми",
        "$mainUrl/cartoon/cartoonseries/page/" to "Мультсеріали",
        "$mainUrl/animeukr/page/" to "Аніме",
    )
```
`UAK:26-34` [verified]. `data` is a **URL prefix**, and `page` is string-appended:
`app.get(request.data + page, headers = headers()).document` (`UAK:60`). Because the site's URL
prefixes *overlap* (series list also contains doramas/cartoons), the provider filters cards
client-side by **row name**:

```kotlin
.filterNot { el ->
    val href = el.select("a.movie-title, a.full-movie").attr("href")
    val genre = el.select(".fi-label:contains(Жанр:) + .deck-value").text()
    href.contains(Regex(blackUrls)) ||
        (request.name == "Серіали" && (genre.contains("Дорами") || genre.contains("Мультсеріали"))) ||
        (request.name == "Мультфільми" && href.contains("/cartoonseries/"))
}
```
`UAK:64-70` [verified], with `blackUrls = "(/news/)|(/franchise/)"` (`UAK:36`) — a
deny-list regex for non-content pages. `hasNext` is left at the default
(`list.isNotEmpty()`, MA §2.3 via doc 06 §1.2) — pagination stops when a page comes back empty
[verified]. This is the "URL-prefix + page-append" family doc 06 §5.3 catalogued; what 06 didn't
show is the **deny-list + name-based row disambiguation**, unique to Uakino in this sample.

### 2.3 Search

```kotlin
override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

override suspend fun search(query: String): List<SearchResponse> {
    val document = app.post(
        url = "$mainUrl/ua/",
        data = mapOf(
            "do" to "search",
            "subaction" to "search",
            "story" to query.replace(" ", "+")
        ),
        headers = headers()
    ).document
    return document.select("div.movie-item.short-item")
        .filterNot { el -> …blackUrls… }
        .map { it.toSearchResponse() }
}
```
`UAK:97-118` [verified]. Legacy **non-paginated** overload; `quickSearch` is a one-line delegate
— the canonical shape (same at `DW:95`). Cards reuse the *same* `Element.toSearchResponse()`
parser as the home page (`UAK:78-84`), the code-sharing pattern the official tutorial recommends.

### 2.4 `load()` — field coverage

Metadata comes from a DLE "film info" table, walked generically:

```kotlin
document.select(".fi-item-s, .fi-item").forEach { metadata ->
    with(metadata.select(".fi-label").text()) {
        when {
            contains("Рік виходу:") -> year = parseUakinoYear(metadata.select(".fi-desc").text(), year)
            contains("Жанр:") -> tags = metadata.select(".fi-desc").text().split(" , ")
            contains("Актори:") -> actors = metadata.select(".fi-desc").text().split(", ")
            contains("Вік. рейтинг:") -> contentRating = …
            contains("Країна:") -> countries = …
            contains("") -> { /* rating row: label contains an <img> */ }
        }
    }
}
```
`UAK:138-155` [verified]. Coverage vs the full LoadResponse surface (MA §5, doc 05 §3.1):

| LoadResponse field | Uakino | Where |
|---|---|---|
| name / posterUrl | ✅ | `UAK:127-129` |
| year | ⚠ fallback **2023 hardcoded** | `UAK:132`, `parseUakinoYear(raw, 2023)` `UAK-PARS:33-34` |
| plot | ✅ (country prepended as HTML `<b>`) | `UAK:179-180` |
| tags (genres) | ✅ | `UAK:144` |
| actors | ✅ **names only** (no images/roles) via `addActors(List<String>)` | `UAK:145, 225` — maps to `ActorData(Actor(it))`, `MA:1880-1882` |
| score | ✅ `Score.from10(rating)`, default `"0"` | `UAK:134, 223` |
| contentRating | ✅ | `UAK:146` |
| recommendations | ✅ **seasons + related** (see below) | `UAK:184-187` |
| trailers | ✅ YouTube-preferred, 3-stage fallback | `UAK:181`, `extractUakinoTrailer` `UAK:350-416` |
| engName | ⚠ set = title (same selector — effectively dead) | `UAK:127-128` |
| backgroundPosterUrl / logoUrl | ❌ | — |
| duration / nextAiring / showStatus / seasonNames / syncData / posterHeaders | ❌ | — |

**The "seasons as recommendations" trick**: each season of a multi-season show is its *own* detail
page on this site, so the provider injects sibling seasons into `recommendations` *plus* the
site's "related" cards:

```kotlin
val recommendations =
    document.select(".seasons li a").map { it.getSeasonInfo() }.toMutableList()
recommendations += document.select(".related-item").map { it.toSearchResponse() }
```
`UAK:184-187` [verified] — `getSeasonInfo()` (`UAK:86-95`) fetches each season page to get its
title/poster (one HTTP round-trip *per season*, at `load()` time — a latency cost worth noting
[inferred]).

**Type inference from tags**: `tvType` is derived from the genre list (`Мультсеріали`→Cartoon,
`Дорами`→AsianDrama, `Повнометражне аніме`→AnimeMovie, …) with a URL-based fallback to
`TvSeries`/`Movie` when tags are inconclusive (`UAK:160-177`) [verified]. This is the
*runtime* type — richer than the declared `supportedTypes` (§1.4 obs 2).

### 2.5 Episode/season modeling — a comma-packed custom data protocol

For series, episodes are fetched from the DLE AJAX playlist endpoint:

```kotlin
val id = document.selectFirst("div.playlists-ajax")?.attr("data-news_id")
    ?: url.split("/").last().split("-").first()          // "35377-toni-10.html" → "35377"
val episodes = app.get(
    "$mainUrl/engine/ajax/playlists.php?news_id=$id&xfield=playlist&time=${Date().time}",
    headers = ajaxHeaders
).parsedSafe<Responses>()?.response.let {
    Jsoup.parse(it.toString()).select("div.playlists-videos li").mapNotNull { eps ->
        val href = "$mainUrl/engine/ajax/playlists.php?news_id=$id&xfield=playlist&time=${Date().time}"
        val name = eps.text().trim() // Серія 1
        newEpisode("$href,$name") { this.name = name; this.data = "$href,$name" }
    }
}
newAnimeLoadResponse(title, url, tvType) {
    …
    addEpisodes(DubStatus.None, episodes.distinctBy { it.name })
    …
}
```
`UAK:192-229` [verified]. Three notable choices:

1. **Every episode's `data` is the AJAX endpoint URL + `,` + episode name** — a comma-packed
   protocol that `loadLinks` later unpacks (`parseUakinoEpisodeData`, `UAK-PARS:17-25`). The app
   treats `data` as an opaque string (`MA:2541-2542` "string used as main LoadLinks fun
   parameter"), so this is legal, but it's a hand-rolled encoding — the parsed helper is
   unit-tested precisely because a raw comma split is fragile (`UAK-T:14-19` tests that episode
   names *containing* commas survive).
2. **`DubStatus.None`** — episodes go under the `None` key (id −1, `MA:905-909`), i.e. this
   "anime-shaped" response deliberately does **not** split dub/sub; the voiceover track is instead
   a link label in `loadLinks` (§2.6).
3. **`episodes.distinctBy { it.name }`** — de-dup against repeated playlist rows [verified].

### 2.6 `loadLinks` — dual-path (series AJAX vs movie iframe) + in-provider player crack

```kotlin
override suspend fun loadLinks(data, isCasting, subtitleCallback, callback): Boolean {
    val parsedData = parseUakinoEpisodeData(data)
    val (requestUrl, targetEpisode) = if (parsedData.episodeName == null) { … } else { … }
    val responseGet = app.get(requestUrl, headers = ajaxHeaders).parsedSafe<Responses>()
    if (responseGet?.success == true) {
        // series: parse playlist HTML, per-li:
        val href = normalizeUakinoPlayerUrl(eps.attr("data-file").trim())
        val dub = eps.attr("data-voice")
        extractPlayerJs(href, dub, callback, subtitleCallback)
    } else {
        // movie (or AJAX failed): re-open the detail page, take iframe#pre
        val filmDoc = fetchDetail(resolveUakinoDetailUrl(data, targetEpisode, requestUrl))
        val iframeUrl = filmDoc?.selectFirst("iframe#pre")?.attr("src")
        extractPlayerJs(iframeUrl, title, callback, subtitleCallback)
    }
    return true
}
```
`UAK:246-300` (trimmed) [verified]. The episode-name branch targets
`div.playlists-videos li:contains($targetEpisode)` with an exact-text guard (`UAK:269-277`).
`extractPlayerJs` (`UAK:302-337`) is a **hand-rolled extractor inside the provider** (no
`ExtractorApi` subclass): fetch the player page, concatenate all `<script>` bodies, regex for
`file: '…'` preferring `.m3u8`, then:

```kotlin
val m3uLink = resolveUakinoStreamUrl(rawFile)          // may be Tortuga-encrypted, see below
val streams = M3u8Helper.generateM3u8(source = sourceName, streamUrl = m3uLink,
                                      referer = playerReferer)   // protocol://host/ of the player
val filtered = streams.dropLast(1)                      // drop last variant (quirk)
(if (filtered.isNotEmpty()) filtered else streams).forEach(callback)
```
`UAK:315-326` [verified]. `dropLast(1)` silently discards the last quality variant — rationale
not stated in code; likely junk/auto-generated entry [inferred]. Subtitles: a `subtitle:` regex in
the same script, with the label parsed out of a `[Lang]url` suffix and forwarded through
`subtitleCallback(newSubtitleFile(label, url))` (`UAK:328-336`) — one of only two providers in
the sample that emits subtitles at all (the other is none of the deep-dives; extractor-side subs
exist in AJL's Streamwish path).

**Tortuga decoding** (in `UakinoParsing.kt`): if `file:` is not an http(s) URL, it's
Base64+XOR encrypted by the site's "Tortuga" player:

```kotlin
internal fun decodeUakinoTortuga(encoded: String): String? {
    val decoded = Base64.decode(padded)
    val salt = decoded[0].toInt() and 0xFF
    for (i in 1 until decoded.size) {
        val key = (salt + 7 * (i - 1) + 13) % 256
        result[i - 1] = ((decoded[i].toInt() and 0xFF) xor key).toByte()
    }
    return String(result, Charsets.UTF_8).takeIf { it.startsWith("http://") || it.startsWith("https://") }
}
```
`UAK-PARS:41-63` (trimmed) [verified] — first byte is the salt, rest XOR'd with
`(salt + 7i + 13) mod 256`; a fixed-vectors unit test pins the algorithm
(`UAK-T:45-53`: ciphertext → `https://calypso.tortuga.wtf/hls/.../index.m3u8`).
`normalizeUakinoPlayerUrl` additionally upgrades `//`→`https:` and `http:`→`https:`
(`UAK-PARS:11-15`) — mixed-scheme hygiene before handing URLs to the app [verified].

### 2.7 Headers, plugin class, settings

A **frozen mobile UA + referer + Accept-Language** header set is built for normal fetches
(`headers()`, `UAK:43-49`) and a separate `ajaxHeaders` with `X-Requested-With: XMLHttpRequest`
for the playlist endpoint (`UAK:50-54`) — mimicking the site's own XHRs [verified]. The plugin
class is the minimal form:

```kotlin
@CloudstreamPlugin
class UakinoProviderPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(UakinoProvider())
    }
}
```
`UAK-P:8-13` [verified]. **No settings**, no `openSettings`. The manifest-level plugin block
(`UakinoProvider/build.gradle.kts`) declares `version = 27`, `status = 1`, `tvTypes =
[Anime, TvSeries, Movie, AsianDrama]`, `language = "uk"`, and a favicon-URL icon
[verified] — note the build.gradle `tvTypes` list *disagrees* with the runtime `supportedTypes`
(no AsianDrama at runtime declaration; the build list is used only for store filtering — doc 10
§1's layer split).

### 2.8 Quality assessment — Uakino

**Good**: shared card parser; deny-list filtering; tested parsing helpers (2 test classes, 130
lines — rare discipline); Tortuga decode + URL-scheme normalization centralized in
`UakinoParsing.kt`; trailer extraction with schema.org-first strategy and a YouTube-only last
resort (`UAK:350-416`, tested in `UakinoTrailerParsingTest.kt:8-30`); subtitles surfaced;
rich metadata (actors, contentRating, score, tags, recommendations).

**Missing / risky**: hardcoded `year = 2023` fallback (wrong-year pollution of library/resume
data); `engName` dead field; `dropLast(1)` quality amputation; per-season HTTP fan-out inside
`load()`; no `posterHeaders` despite site images (worked here because the CDN is
hotlink-tolerant [inferred]); no duration/nextAiring/showStatus; recommendations conflate seasons
with similar titles (semantically surprising in the UI); the comma-packed episode `data` protocol
is opaque to any tooling except its own parser.

---

## 3. Deep-dive: DoramyWorldProvider — Asian drama, hybrid (HTML + embedded JSON) (CakesTwix)

204 lines; the *smallest* fully-functional provider in the deep-dive set, and the only one that
splits episodes into a real **DubStatus map** [verified].

### 3.1 Declaration & structure

```kotlin
class DoramyWorldProvider : MainAPI() {
    override var mainUrl = "https://doramy.world"
    override var name = "DoramyWorld"
    override val hasMainPage = true
    override var lang = "uk"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.Movie)
```
`DW:27-39` [verified]. Selectors are hoisted into constants — the file reads like a template
("one row per site category"):

```kotlin
private val cardSelector = "article.type-dorama, article.type-film, article.type-show"
private val titleSelector = "h1.project-title"
private val genresSelector = "a[href*=/genre/]"
private val infoRowSelector = "li.item"
private val descriptionSelector = "div.about-text-holder p"
```
`DW:49-55` [verified].

### 3.2 Browsing, search, cards

Three URL-prefix rows (`$mainUrl/film|dorama|show/page/` → Ukrainian names, `DW:42-46`);
`getMainPage` is the 4-line canonical form `app.get(request.data + page + "/")` →
`select(cardSelector).mapNotNull { it.toSearchResponse() }` (`DW:67-74`) [verified]. Search is a
GET `?s=` query reusing the same card selector (`DW:97-100`). Card typing is **URL-driven**:
`/film/` → `newMovieSearchResponse(TvType.Movie)`, else → `newAnimeSearchResponse(TvType.AsianDrama)`
(`DW:84-92`) — an AsianDrama typed as an *Anime*SearchResponse because that's the card type that
carries dub-status chips (`MA:1492`; doc 05 §2.2) [verified + inferred rationale].

### 3.3 `load()` — how the "JSON" provider actually works

The site HTML is only the entry point; the real structure is a JSON playlist inside the third-party
`ashdi.vip` player iframe:

```kotlin
val iframe = document.selectFirst("iframe[src*=ashdi]")?.attr("src")
if (iframe != null && iframe.contains("/serial/")) {
    val ashdiHtml = app.get(iframe, referer = "$mainUrl/").text
    val json = serialPlaylistRegex.find(ashdiHtml)?.groupValues?.get(1)   // Regex("""file:'(\[.*])'""")
    val groups = Gson().fromJson(json, Array<AshdiItem>::class.java)
    groups.forEach { group ->
        val isSub = group.title.contains("Суб", ignoreCase = true)
        val target = if (isSub) subEpisodes else dubEpisodes
        val seasons = group.folder ?: emptyList()
        seasons.forEachIndexed { seasonIndex, season ->
            val episodes = season.folder ?: listOf(season)   // fallback: season IS an episode
            episodes.forEach { ep ->
                val file = ep.file ?: return@forEach
                target.add(newEpisode(file) {
                    this.name = ep.title
                    this.season = seasonIndex + 1
                    this.episode = ep.title.filter { it.isDigit() }.toIntOrNull()
                    this.data = file
                })
            }
        }
    }
}
```
`DW:118-163` (trimmed) [verified]. The **recursive node model** is the key idea:

```kotlin
data class AshdiItem(
    val title: String = "",
    val file: String? = null,
    val folder: List<AshdiItem>? = null
)
```
`DW:200-204` [verified] — a group, a season, or an episode is the *same* type; `folder` presence
distinguishes containers from leaves, and a degenerate season (no folder) is reinterpreted as an
episode (`DW:148`). This is how a JSON-API provider degrades parsing fragility: no CSS selectors,
stable keys, one `Gson().fromJson`. Note it uses **Gson directly** while AllCalidad uses Jackson's
`tryParseJson` — both JSON stacks are usable from plugins [verified].

**Dub/Sub split**: playlist group titles containing "Суб" route to `DubStatus.Subbed`, everything
else to `DubStatus.Dubbed` (`DW:142-143`, `171-172`) — the app then shows dub/sub chips and the
episodes map drives subscriptions' "latest episode" logic (doc 05 §4, doc 10 §5) [verified].

**Movies resolve the stream at `load()` time**: `extractMovieStream(iframe)` regexes the first
`.m3u8` out of the `/vod/` page and bakes it into the response's `dataUrl`
(`DW:121-129`, `177-180`) [verified]. Field coverage: name/poster (`og:image` meta)/year (digit
filter over the "Рік" row)/plot/tags/recommendations ✅; actors/score/trailer/contentRating/
backgroundPoster/seasonNames ❌ (`DW:106-129, 165-173`). Year has no fallback — a missing row
yields `null` (better than Uakino's fake 2023) [verified].

### 3.4 `loadLinks` — direct-m3u8 one-liner

```kotlin
override suspend fun loadLinks(data, …): Boolean {
    if (data.isBlank()) return false
    M3u8Helper.generateM3u8(source = name, streamUrl = data, referer = ashdiReferer)
        .forEach(callback)
    return true
}
```
`DW:183-196` [verified]. Because `data` is *already a direct .m3u8* (resolved at load time for
movies, taken from the playlist JSON for episodes), no extractor is needed at all — just quality
expansion via `M3u8Helper` with the player's referer (`https://ashdi.vip/`, `DW:58`). This is the
**simplest working loadLinks in the entire sample** and shows the "resolve early, play dumb"
pattern.

### 3.5 Quality assessment — DoramyWorld

**Good**: smallest complete surface; constants-hoisted selectors; recursive JSON model handles
group/season/episode uniformly; real DubStatus map; year nullable-not-faked; zero-extractor
loadLinks; no custom protocols.

**Missing / oddities**: recommendations are *all cards on the page* (`document.select(cardSelector)`
on a detail page, `DW:115`) — likely "other doramas" sidebar, but unchecked [inferred];
`JSONModel.kt` (43 lines of schema.org JSON-LD models) is **dead code** — not imported by the
provider [verified via imports]; no actors/score/trailer; movie streams resolved at load() can go
stale (tokens) before playback [inferred]; poster from `og:image` only (no fallback).

---

## 4. Deep-dive: AllCalidadProvider + in-plugin extractors — movies, REST JSON (storm-ext)

307 provider lines + 73 extractor lines; the cleanest **REST-JSON** provider in the sample, and
the exemplar of the **custom in-plugin extractor** pattern [verified].

### 4.1 Declaration & the data-as-JSON pattern

```kotlin
class AllCalidadProvider : MainAPI() {
    override var mainUrl = "https://allcalidad.re"
    override var name = "AllCalidad"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
```
`AC:18-29` [verified]. The whole provider talks to a WordPress-ish REST API at
`$mainUrl/api/rest` (`AC:31`); images resolve through a 3-way router — absolute URLs pass,
`/thumbs/|/backdrops/|/logos/` prefix to the site's own uploads CDN, everything else is treated as
a TMDb path (`https://image.tmdb.org/t/p/w500$path`, `AC:35-40`) [verified] — sites re-hosting
TMDb artwork are common, and this router absorbs both layouts transparently.

**Search results carry their entire API object as `url`:**

```kotlin
private fun ApiPost.toSearchResult(): SearchResponse? {
    val name = title ?: return null
    return newMovieSearchResponse(name, this.toJson(), tvType, fix = false) {
        this.posterUrl = resolveImage(images?.poster)
    }
}
```
`AC:94-104` [verified]. `load()` then re-hydrates the object instead of fetching anything:

```kotlin
override suspend fun load(url: String): LoadResponse? {
    val post = tryParseJson<ApiPost>(url) ?: return null
    …
    return if (tvType == TvType.Movie) {
        newMovieLoadResponse(title, url, tvType, post.id.toString()) { … }
    } else {
        val episodes = getEpisodes(post.id)
        newTvSeriesLoadResponse(title, url, tvType, episodes) { … }
    }
}
```
`AC:106-139` (trimmed) [verified]. The framework explicitly blesses this:
`fixUrl` does not touch strings starting with `{"` or `[` ("Do not fix JSON objects and arrays
when passed as urls", `MA:742-748`), and the generic `newMovieLoadResponse(name, url, type, data: T?)`
serializes non-String data to `dataUrl` JSON (`MA:2491-2519`). Trade-off: zero extra fetch at
load-time vs. stale data if the post changed since search — and the JSON rides through the app's
result-URL cache verbatim [inferred].

### 4.2 Browsing & search (recap + what's new vs doc 06)

Doc 06 §5.1 covers the 18-row `"postType:genreId"` genre-token table and
`hasNext = page < lastPage` from API pagination (`AC:42-84`). What's new here: the **models are
private file-level data classes with Jackson `@JsonProperty` mappings and total default values**
(`AC:240-307`) — e.g. `ApiPost(_id, title, overview, slug, images, rating, type, release_date,
runtime)` — so a malformed field degrades to null instead of crashing [verified]. Search uses the
legacy overload with one page of 24 mixed-type results (`AC:86-92`).

### 4.3 `load()` field coverage & episodes

Movies populate poster, **backgroundPosterUrl (backdrop)**, plot, year
(`releaseDate?.substringBefore("-")`), `addScore(rating, 10)`, **`addDuration("$runtime min")`**,
and recommendations via a dedicated `/related` endpoint (16 items, `AC:156-164`) [verified].
Series populate the same minus duration, and episodes come from `/episodes?post_id=`:

```kotlin
newEpisode("$mainUrl/episodio/${ep.id}") {
    this.name = "S${ep.seasonNumber}E${ep.episodeNumber}"
    this.season = ep.seasonNumber
    this.episode = ep.episodeNumber
    this.posterUrl = resolveImage(ep.stillPath)
}
```
`AC:146-153` [verified] — **per-episode stills** (the only deep-dive provider with episode
thumbnails) and a flat episode list with season/episode numbers, which the app groups via the
SeasonData overlay (doc 05 §4.2). `data` is a synthetic `episodio/{id}` URL resolved at
loadLinks time.

### 4.4 `loadLinks` — the player endpoint, labels, magnets, and a curious coroutine

```kotlin
override suspend fun loadLinks(data, …): Boolean {
    val postId = data.substringAfterLast('/').toIntOrNull() ?: return false
    val player = runCatching {
        tryParseJson<PlayerResponse>(app.get("$apiUrl/player?post_id=$postId&_any=1").text)
    }.getOrNull()?.data ?: return false

    player.embeds.forEach { embed ->
        val url = embed.url ?: return@forEach
        runCatching {
            loadExtractor(url, mainUrl, subtitleCallback) { link ->
                CoroutineScope(Dispatchers.IO).launch {
                    callback(newExtractorLink(link.source,
                        "${embed.lang ?: "Server"} · ${embed.quality ?: "HD"} · ${link.name}",
                        link.url, link.type) {
                        this.quality = link.referer… // quality/referer/headers/extractorData copied
                    })
                }
            }
        }
    }
    player.downloads.forEach { dl -> … magnet branch … }
    return true
}
```
`AC:166-237` (trimmed) [verified]. Three patterns worth naming:

1. **Label enrichment**: the API gives `lang` + `quality` per embed; the provider rebuilds each
   link's label as `"${lang} · ${quality} · ${link.name}"` (`AC:186`) so the mirror dialog reads
   "Latino · HD · Streamwish" instead of a bare host name — the *only* deep-dive provider that
   enriches labels.
2. **Magnet passthrough**: `url.startsWith("magnet:")` → direct
   `newExtractorLink("Torrent", label, url, ExtractorLinkType.MAGNET)` with no extractor
   (`AC:204-214`) — magnets are first-class link types (doc 05 §6.6, doc 09 §1 torrent path).
3. **`CoroutineScope(Dispatchers.IO).launch { callback(...) }`** wraps *every* callback
   (`AC:182-196, 205-214`) — launching an unstructured coroutine per link. Since `loadLinks`
   returns immediately while those coroutines may still be running, links can arrive after the
   provider call completes; the app tolerates this (links are collected via the callback channel
   until the generator is satisfied — doc 08 §4), but it's fire-and-forget scheduling the caller
   didn't ask for, and unstructured scopes leak if the user backs out [inferred; the pattern
   recurs in DoramasFlix §5.4].

### 4.5 The custom in-plugin extractors (Extractor.kt)

```kotlin
class Vimeos : ExtractorApi() {
    override val name = "Vimeos"
    override val mainUrl = "https://vimeos.net"
    override val requiresReferer = true

    override suspend fun getUrl(url, referer, subtitleCallback, callback) {
        val doc = app.get(getEmbedUrl(url), referer = referer).document
        val unpackedJs = unpackJs(doc).toString()
        val videoUrl = Regex("""file:\s*"([^"]+\.m3u8[^"]*)"""").find(unpackedJs)?.groupValues?.get(1)
        if (videoUrl != null) {
            M3u8Helper.generateM3u8(this.name, fixUrl(videoUrl), "$mainUrl/").forEach(callback)
        }
    }
    private fun unpackJs(script: Element): String? =
        script.select("script").find { it.data().contains("eval(function(p,a,c,k,e,d)") }
            ?.data()?.let { getAndUnpack(it) }
    private fun getEmbedUrl(url: String): String =
        if (!url.contains("/embed-")) "$mainUrl/embed-${url.substringAfter("$mainUrl/")}" else url
}
```
`AC-E:12-47` (trimmed; `GoodstreamExtractor` follows the same shape without packing,
`AC-E:49-73`) [verified]. Both are registered next to the provider:

```kotlin
@CloudstreamPlugin
class AllCalidadProviderPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AllCalidadProvider())
        registerExtractorAPI(Vimeos())
        registerExtractorAPI(GoodstreamExtractor())
    }
}
```
`AC-P:9-16` [verified]. Why custom instead of in-provider code (like Uakino's
`extractPlayerJs`)? Because **`loadExtractor` dispatch is by URL domain** across the *global*
registry (doc 08 §2.3): by registering an `ExtractorApi` with `mainUrl = "https://vimeos.net"`,
any provider's link to that host resolves through this code, and — crucially — the registration
**shadows built-ins** registered earlier (registry is appended and searched newest-first; doc 08
§6.1). The `getEmbedUrl` `/embed-` rewrite normalizes download-page URLs to embed pages before
scraping — a per-family quirk built into the extractor. `unpackJs` handles `p,a,c,k,e,d`-packed
JS via the framework's `getAndUnpack` [verified].

### 4.6 Quality assessment — AllCalidad

**Good**: full-JSON pipeline with defensive models; genre-token browsing with *true* pagination;
richest `load()` metadata of the deep-dives (backdrop, duration, score, episode stills, related
endpoint); label-enriched mirrors; magnet passthrough; extractor registration done "properly".

**Missing / risky**: non-paginated search (page=1 forever, `AC:88`); `hasChromecastSupport = true`
declared but headers-bearing extractors can't actually cast (doc 09 §6) [inferred];
unstructured-coroutine callbacks; no actors; no showStatus/nextAiring for ongoing series; the
whole-API-object-as-URL means library entries store large opaque JSON strings in watch-state
keys [inferred].

---

## 5. Deep-dive: DoramasFlixProvider — Asian drama, GraphQL JSON (storm-ext)

356 lines; the *worst-quality* provider in the deep-dive set — and therefore maximally instructive
as an anti-pattern catalog. The site (doramasflix.co) is a GraphQL SPA; the provider POSTs
hand-written query bodies to a *different* host, `https://doraflix.fluxcedene.net/api/gql`
(`DFX:19-22`) [verified].

### 5.1 Declaration & the "one bag" response model

```kotlin
class DoramasFlixProvider : MainAPI() {
    override var mainUrl = "https://doramasflix.co"
    override var name = "Doramasflix"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)
```
`DFX:18-33` [verified]. All GraphQL operations share ONE response wrapper with 12 nullable
collections, because every query reuses the same parsed type:

```kotlin
data class DataDoramas (
    @JsonProperty("listDoramas" ) var listDoramas : ArrayList<ListDoramas>? = arrayListOf(),
    @JsonProperty("searchDorama" ) var searchDorama : ArrayList<ListDoramas>? = arrayListOf(),
    @JsonProperty("searchMovie"  ) var searchMovie  : ArrayList<ListDoramas>?  = arrayListOf(),
    @JsonProperty("listSeasons" ) var listSeasons : ArrayList<ListDoramas>? = arrayListOf(),
    @JsonProperty("detailDorama" ) var detailDorama : DetailDoramaandDoramaMeta? = …,
    … // 12 fields total, incl. paginationEpisode, carrouselMovies, paginationDorama, paginationMovie
)
```
`DFX:38-50` (trimmed) [verified] — and `DetailDoramaandDoramaMeta` is a 25-field union of dorama
*and* movie *and* episode fields (`DFX:65-94`). Pragmatic, unreadable, and typical of
hand-rolled GraphQL clients [verified].

### 5.2 The triple-fetch-on-empty-mainPage behavior

Doc 06 §5.2 (B2-a) documented this; the mechanics, verified in source:

1. The provider **does not override `mainPage`**, so the app sees the default single row
   `listOf(MainPageData("", "", false))` (`MA:630`) — yet `hasMainPage = true` (`DFX:27`).
2. On home load, `APIRepository.getMainPage(1, null)` iterates that one row and calls
   `getMainPage` once (`ui/APIRepository.kt:156-182`).
3. That one call **ignores both `page` and `request`** and fires **three GraphQL POSTs**
   (listDoramasMobile, paginationMovie, paginationDorama) with hardcoded bodies
   (`DFX:130-135`), returning three hard-named sections:

```kotlin
items.add(HomePageList("Doramas", home1!!))
items.add(HomePageList("Peliculas", home2!!))
items.add(HomePageList("Doramas 2", home3!!))
if (items.size <= 0) throw ErrorLoadingException()
return newHomePageResponse(items)
```
`DFX:149-153` [verified] — "3 near-identical HTTP batches on first load" (doc 06 §5.2). The
`home1!!`/`home2!!` non-null assertions make an empty API response crash into the generic error
state rather than rendering empty rows [verified]. Pagination is broken twice over: `page` never
reaches the queries (all bodies hardcode `"page":1`), and `newHomePageResponse(items)` defaults
`hasNext = items.any { it.list.isNotEmpty() }` = **always true** → scroll re-fetches page 1
forever (doc 06 §5.2 [verified], MA:473-475 per doc 06 §1.2).

### 5.3 Search & load

`search` POSTs a `searchAll` query (limit 5 per sub-type) and concatenates dorama+movie results
(`DFX:177-192`); every card's `data` is a **hand-built JSON string**
`{"id":"…","slug":"…","type":"…","isTV":bool}` (`DFX:166`) [verified]. `load` then:

```kotlin
val fixed = url.substringAfter("https://www.comamosramen.com/")
val parse = parseJson<DoramasInfo>(fixed)
```
`DFX:196-197` [verified] — a strip of a domain that is *neither* the current `mainUrl` nor the
API host. `substringAfter` returns the input unchanged when the separator is absent, so today
this is a no-op; the plausible history is that an older `fixUrl` (pre-JSON-guard, `MA:742-748`)
prefixed the then-mainUrl onto the raw JSON data and load stripped it — the guard made both sides
dead code [inferred]. Keep this in mind for ANI-KUTA: **data protocols inside `url` survive
domain migrations only by accident.**

Episodes: per season, a `listEpisodesPagination` query with `perPage: 1000` (all episodes in one
page, `DFX:226`), building flat episodes with `season`/`episode`/`name`/`posterUrl`
(`DFX:228-244`) — no seasonNames, no dub maps (all episodes in one TvSeriesLoadResponse list,
`DFX:250-258`). **Movies bake `links_online` JSON into `dataUrl`** (`DFX:246-248`) — the
AllCalidad data-as-URL pattern, minus the framework's `toJson` (hand-built here).

### 5.4 `loadLinks`, language maps, and mirror-domain rewriting

```kotlin
override suspend fun loadLinks(data, …): Boolean {
    if (data.contains("link")) {                     // movie: data = [ {page,server,link,lang} ]
        val parse = parseJson<List<LinksOnline>>(data)
        parse.map {
            loadSourceNameExtractor(getLangById(it.lang ?: ""), fixHostsLinks(link!!), data, subtitleCallback, callback)
        }
    } else {                                          // episode: data = slug → GetEpisodeLinks query
        … app.post(doraflixapi, requestBody = episodeslinkRequestbody).parsedSafe<MainDoramas>() …
        request?.data?.detailEpisode?.linksOnline?.map {
            val link = it.link?.replace("https://swdyu.com","https://streamwish.to")?.replace("https://uqload.to","https://uqload.co")
            loadSourceNameExtractor(getLangById(it.lang ?: ""), fixHostsLinks(link!!), …)
        }
    }
    return true
}
```
`DFX:287-311` (trimmed) [verified]. Three field-guide-worthy pieces:

- **`getLangById`** (`DFX:273-284`): the API returns numeric language IDs ("13109"→Coreano,
  "37"→Castellano, "192"→Subtitulado …); the provider maps them to human labels that become the
  link's `source`/name — same intent as AllCalidad's label enrichment, different mechanism.
- **`fixHostsLinks`** (`DFX:340-355`): 13 `replaceFirst` rewrites of mirror domains to canonical
  host domains (`hglink.to`→`streamwish.to`, `mivalyo.com`→`vidhidepro.com`, `dood`-family …).
  The video host ecosystem rotates domains constantly; providers cope by *rewriting URLs to the
  domain their extractor knows* before calling `loadExtractor`. Identical function duplicated
  verbatim in AnimeJl (`AJL:207-221`) [verified] — copy-paste taxonomy between providers in the
  same repo.
- **`loadSourceNameExtractor`** (`DFX:314-338`): a top-level suspend helper wrapping
  `loadExtractor` + the same unstructured `CoroutineScope(Dispatchers.IO).launch { callback(...) }`
  per link as AllCalidad (§4.4 obs 3), relabeling links `"${lang}[${link.source}]"`.

### 5.5 Quality assessment — DoramasFlix

**Good**: shows the GraphQL-SPA adaptation (query bodies as string constants, one response
model); numeric-lang → label mapping; mirror-domain table; movie links baked at load.

**Anti-patterns (catalogued)**: `hasMainPage` without `mainPage`; `request`/`page` ignored;
`hasNext` always true; **`hasQuickSearch = true` with no `quickSearch` override** — if the user
reaches the single-provider quick-search path the default throws `NotImplementedError`
(`DFX:28`; doc 06 §5.2 [verified]) — the single most dangerous flag mismatch in the sample;
`!!` assertions on API data; dead domain-strip in load; **no year anywhere** (both branches omit
it — library sorting/resume UI loses a dimension, doc 07 §2.1); no actors/recommendations/
trailers; duplicate hand-built JSON of the same shape in two places (`tasa` `DFX:166` vs
`datatwo` `DFX:219`); unused imports (`android.R`, `android.util.Log` `DFX:3-4`) [verified].

---

## 6. Deep-dive: AnimeJlProvider + its 57 custom extractors — anime (storm-ext)

220 provider lines + 591 extractor lines; the **anime** representative and the flagship of the
"provider ships a whole extractor family" pattern [verified].

### 6.1 Declaration, Cloudflare, and query-string rows

```kotlin
class AnimeJlProvider : MainAPI() {
    private val cloudflareKiller = CloudflareKiller()
    private suspend fun appGetCf(url: String): NiceResponse =
        app.get(url, interceptor = cloudflareKiller)
    …
    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "animes?genre[]=46&order=updated" to "Latino",
        "animes?" to "Animes",
        "animes?tipo[]=7&order=updated" to "Donghuas",
        "animes?tipo[]=3&order=updated" to "Peliculas",
    )
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = appGetCf("$mainUrl/${request.data}&page=$page").document
        …
        return newHomePageResponse(list = HomePageList(name = request.name, list = home,
            isHorizontalImages = false), hasNext = true)
    }
```
`AJL:13-49` (trimmed) [verified]. Three things: (1) **Cloudflare bypass via interceptor** —
`CloudflareKiller` is passed to every request (`AJL:16-18`); (2) genre rows are encoded as
**query-string fragments** appended after `$mainUrl/` (`genre[]=46` = dubbed anime, `tipo[]=7` =
donghua) — a third genre-encoding style beyond AllCalidad's tokens and Uakino's URL prefixes;
(3) `hasNext = true` **hardcoded** — same infinite-scroll hazard as DoramasFlix, minus the
default excuse [verified].

### 6.2 Search cards: dub status *before* load, and posterHeaders

```kotlin
private fun Element.toSearchResult(): SearchResponse {
    val title = this.select("article.Anime h3.Title").text()
    val href = this.select("article.Anime a").attr("href")
    val posterUrl = fixUrlNull(this.select("article.Anime a div.Image figure img").attr("src"))
        ?.replaceFirst("^/".toRegex(), "$mainUrl/")
    return newAnimeSearchResponse(title, href, TvType.Movie) {
        this.posterUrl = posterUrl
        this.posterHeaders = if (posterUrl?.contains(mainUrl) == true)
            cloudflareKiller.getCookieHeaders(mainUrl).toMap() else emptyMap()
        addDubStatus(getDubStatus(href))
    }
}
private fun getDubStatus(title: String): DubStatus =
    if (title.contains("-latino") || title.contains("-castellano")) DubStatus.Dubbed else DubStatus.Subbed
```
`AJL:51-68` [verified]. Two anime-specific moves:

- **`addDubStatus` on the search card** (`MA:1576-1584`): announces Dubbed/Subbed (with optional
  episode count) so the app can show dub/sub chips *in search results* and pre-route the library
  filter (doc 05 §6.2, doc 10 §5). Dubness here is inferred from the **URL slug** (`-latino`,
  `-castellano`), not from page content.
- **`posterHeaders`** — the ONLY provider in the census that sets it. Because the site sits
  behind Cloudflare, images served from the same origin need the clearance cookies too;
  `cloudflareKiller.getCookieHeaders(mainUrl)` is attached conditionally (only when the poster is
  same-origin) (`AJL:59`) [verified]. This is the hotlink/anti-bot mechanism doc 07 §3.3
  describes, in its real-world form.

Also note the type quirk: `newAnimeSearchResponse(…, TvType.Movie)` — an *anime search response*
carrying `TvType.Movie` as its card type (`AJL:57`); harmless for rendering but a declared-vs-
runtime mismatch again (§1.4 obs 2) [verified].

### 6.3 `load()` — JS-array episodes, TvSeries-shaped anime

```kotlin
val script = doc.select("script").firstOrNull { it.html().contains("var episodes =") }?.html()
if (!script.isNullOrEmpty()) {
    val jsonscript = script.substringAfter("episodes = ").substringBefore(";").replace(",]", "]")
    val json = parseJson<List<List<String>>>(jsonscript)
    json.map { list ->
        var epNum = 0; var epTitle = ""; var epurl = ""; var realimg = ""
        list.forEachIndexed { idx, it ->
            if (idx == 0) epNum = it.toIntOrNull() ?: 0
            else if (idx == 1) epurl = "$url/$it"
            else if (idx == 2) realimg = "$mainUrl/storage/$it"
            else if (idx == 3) epTitle = it.ifEmpty { "Episodio $epNum" }
        }
        episodes.add(newEpisode(epurl) { this.name = epTitle; this.season = 0
                                        this.episode = epNum; this.posterUrl = realimg })
    }
}
return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
    this.posterUrl = poster
    this.backgroundPosterUrl = backimage
    this.posterHeaders = if (poster.contains(mainUrl)) cloudflareKiller.getCookieHeaders(mainUrl).toMap() else emptyMap()
    this.plot = description
    this.tags = tags
}
```
`AJL:88-133` (trimmed) [verified]. Parsing notes: episodes live in a `var episodes = [...]`
JS array of **positional quadruples** `[number, slug, image, title]` (trailing-comma repair
`.replace(",]", "]")` for IE-style JS); `season = 0` explicitly means "no season". The response
uses **`newTvSeriesLoadResponse`, not `newAnimeLoadResponse`** — so this anime provider has *no
DubStatus episode map at load time* (dubness was a search-card affordance only); the app treats it
as a flat series (doc 05 §4.4: TvSeriesLoadResponse vs AnimeLoadResponse). Background poster is
scraped out of an inline `style` attribute (`background-image:url(…)` `AJL:80-81`) [verified].
No year, no actors, no score, no recommendations, no nextAiring.

### 6.4 `loadLinks` — one special-cased linker + everything else through extractors

```kotlin
appGetCf(data).document.select("script")
    .firstOrNull { it.html().contains("var video = [];") }?.let { frameUrl ->
        fetchUrls(frameUrl.html()).amap {
            if (it.startsWith("https://holuagency.top/load.php?")) {
                val doc = followRedirectsJS(it)                       // recursive window.location
                val form = doc.selectFirst("form#link")
                … token/back/sh hidden inputs …
                val doc = app.post(url, data = mapOf("token" to …, "back" to …, "sh" to …)).document
                val containerFrameUrl = doc.selectFirst("a.cs-share__copy-link")?.attr("href")
                … app.get(containerFrameUrl, cookies = mapOf("t" to token, "b" to back, "s" to sh)) …
                doc.selectFirst("div#player iframe")?.attr("src")?.let {
                    loadExtractor(fixHostsLinks(it), data, subtitleCallback, callback)
                }
            } else {
                loadExtractor(fixHostsLinks(it), data, subtitleCallback, callback)
            }
        }
    }
return true
```
`AJL:146-203` (trimmed) [verified]. The pattern: scrape **all URLs** out of the player script via
the framework's `fetchUrls` (`MA` — regex over text), then for each URL either (a) hand-roll a
**multi-step link-shortener dance** (JS redirect → hidden form POST with token/back/sh → cookie'd
GET → iframe src — `followRedirectsJS` at `AJL:135-144` recursively follows
`window.location.href="…"` redirects), or (b) delegate to the extractor registry after
`fixHostsLinks` mirror-rewriting. `amap` runs the branches concurrently.

### 6.5 The 57 custom extractors — why and how

`AJL-P:14-74` registers 57 `ExtractorApi` instances in 5 families (counts verified):

| Family (base class) | Registered | Mirrors | Technique |
|---|---|---|---|
| `StreamWishExtractor` (`StreamWishExtractor.kt:154`) | 29 | savefiles.com, mwish.pro, dwish.pro, embedwish.com, wishembed.pro, kswplayer.info, wishfast.top, streamwish.site, sfastwish.com, strwish.xyz/.com, flaswish.com, awish.pro, obeywish.com, jodwish.com, swhoi.com, multimovies.cloud, uqloads.xyz, dooporn.xyz, cdnwish.com, asnwish.com, nekowish.my.id, neko-stream.click, swdyu.com, wishonly.site, playerwish.com, streamhls.to, hlswish.com, streamwish.to | packed-JS unpack → `file:"…m3u8"` regex → M3u8Helper; **WebViewResolver fallback** (`SW:199-210`) |
| `VidHidePro` (`VidHidePro.kt:73`) | 15 | dhtpre.com, dingtezuni.com, minochinos.com, ryderjet.com, vidhidehub.com, filelions.live/.online/.to, kinoger.be, vidhidevip.com, vidhidepre.com, smoothpre.com, peytonepre.com, vidhidepro.com | embed-URL rewrite `/d//download//file//f/`→`/v/`; packed or `sources:` script; regex `:\s*"(.*?m3u8.*?)"` catching `hls2:`/`hls4:` prefixes (`VH:104`); M3u8Helper**2** |
| `ByseSX` (`ByseSX.kt:42`) | 6 | byse.sx + 5 byse* domains | **API + AES-GCM**: `/api/videos/{code}/embed/details` → `embedFrameUrl` → `/embed/playback` returns `keyParts[2]+iv+payload` → AES/GCM decrypt (`BS:91-113`) → sources[0].url |
| `VidStack` (built-in, `extractors/VidStack.kt`) | 6 | pelisplus.upns.pro, pelisplus.strp2p.com, pelisplusto.4meplayer.pro, anime.4meplayer.com, anime.upns.pro, anime.p2pstream.vip | **extends a BUILT-IN extractor**, domain-only override |
| `Filesim` (built-in) | 1 | emturbovid.com | same: `class EmturbovidCom : Filesim()` (`FS:5-8`) |

**Why custom, in order of prevalence:**

1. **Mirror-domain registration** — the dominant reason. The *same* host software runs on dozens
   of rotating domains; each domain needs its own `mainUrl` in the registry for `loadExtractor`'s
   prefix-match to route to it (doc 08 §2.3). The idiom is a one-line subclass per mirror:

   ```kotlin
   class Mwish : StreamWishExtractor() {
       override val name = "Mwish"
       override val mainUrl = "https://mwish.pro"
   }
   ```
   `SW:20-23` [verified] — 54 of the 57 registrations are pure domain overrides; only 3
   registrations (one per logic-bearing base: StreamWish, VidHidePro, ByseSX) carry actual code.

2. **Built-in shadowing** — `Filesim`/`VidStack` subclasses *extend the framework's built-in
   extractors* purely to add domains (`FS:5-8`, `VS:5-33`) — and because plugin extractors
   register after built-ins, they shadow same-domain built-ins if behavior needs overriding
   (doc 08 §6.1 [verified mechanics]).

3. **Genuinely new logic** — only `ByseSX` (AES-GCM API playback) and `StreamWishExtractor`'s
   WebView fallback are non-trivial new code. StreamWish's ladder is worth quoting:

   ```kotlin
   val playerScriptData = when {
       !getPacked(pageResponse.text).isNullOrEmpty() -> getAndUnpack(pageResponse.text)
       pageResponse.document.select("script").any { it.html().contains("jwplayer(\"vplayer\").setup(") } -> …
       else -> pageResponse.document.selectFirst("script:containsData(sources:)")?.data()
   }
   …
   val webViewM3u8Resolver = WebViewResolver(
       interceptUrl = Regex("""txt|m3u8"""), additionalUrls = listOf(Regex("""txt|m3u8""")),
       useOkhttp = false, timeout = 15_000L)
   val interceptedStreamUrl = app.get(url, referer = referer, interceptor = webViewM3u8Resolver).url
   ```
   `SW:178-210` (trimmed) [verified] — packed JS → jwplayer setup → `sources:` script → and if
   all static parsing fails, spin a **headless WebView** that loads the page until a `.m3u8`/`.txt`
   request is intercepted. This escalation ladder (regex → unpack → WebView) is the standard
   response to host-side obfuscation, and it's why ANI-KUTA's Cloud Screen can't assume
   `loadLinks` is cheap or offline-safe (§10).

### 6.6 Quality assessment — AnimeJl

**Good**: posterHeaders done correctly (the census's only example); dub status on cards; a
pragmatic 3-tier genre row encoding; the full extractor-family pattern demonstrated end-to-end;
WebView fallback kept *inside* the extractor where it belongs.

**Missing / risky**: `hasNext = true` hardcoded; `TvType.Movie` in an anime search response; no
year/actors/score/recommendations; positional-array episode parsing (fragile to site JS edits);
`season = 0` on every episode; special-cased holuagency flow will silently break when the domain
changes; no quickSearch despite hasMainPage cards implying it (search-only is at least safe).

---

## 7. Deep-dive: TwitchProvider — the official-quality bar (recloudstream)

179 lines in the `extensions/` rebuild repo (a re-packaged CloudStream "official" set — repo.json
+ clean manifests). Compared against the community providers above, the deltas are not volume but
**deliberateness** [verified throughout].

### 7.1 Declaration — scrape the *tracker*, play the *real* site

```kotlin
class TwitchProvider : MainAPI() {
    override var mainUrl = "https://twitchtracker.com" // Easiest to scrape
    override var name = "Twitch"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "uni"
    override val hasMainPage = true
```
`TW:27-34` [verified]. The comment says the architecture out loud: **mainUrl is the *metadata*
source (twitchtracker.com is scrapeable), while playback URLs are real twitch.tv links** built in
`load()` (`TW:118`). Decoupling "where metadata comes from" from "where video comes from" is the
single biggest structural difference from the community providers, which entangle both in one
domain. `lang = "uni"` — the universal sentinel (doc 10 §4).

### 7.2 Browsing: name-token rows, explicit hasNext, deliberate throttling

```kotlin
override val mainPage = mainPageOf(
    "$mainUrl/channels/live" to "Top global live streams",
    "$mainUrl/games" to gamesName            // "games" — a name-only token
)
override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    return when (request.name) {
        gamesName -> newHomePageResponse(parseGames(), hasNext = false)   // finite section
        else -> {
            val doc = app.get(request.data, params = mapOf("page" to page.toString())).document
            …
            newHomePageResponse(listOf(HomePageList(request.name, channels,
                isHorizontalImages = isHorizontal)), hasNext = true)
        }
    }
}
```
`TW:37-63` (trimmed) [verified]. Row dispatch is **by name token** (`when (request.name)`), the
games row is *finite* (`hasNext = false` — the only explicit `false` in the whole sample), and
pagination goes through **`params`** (query parameter map) rather than string concatenation.
The throttling comment, verbatim:

```kotlin
return doc.select("div.ranked-item")
    .take(5)
    .mapNotNull { element -> // No amap to prevent getting 503 by cloudflare
```
`TW:80-82` [verified] — **sequential `take(5)` instead of concurrent `amap`**, with the reason in
a comment: the scraper site rate-limits. Community providers (AnimeJl §6.4) use `amap`
unconditionally; the official one opts *out* of concurrency knowingly.

### 7.3 Cards, load, and a user-facing error

```kotlin
private fun Element.toLiveSearchResponse(): LiveSearchResponse {
    …
    return newLiveSearchResponse(name ?: "", linkName, TvType.Live, fix = false) { posterUrl = image }
}
```
`TW:65-76` [verified] — `LiveSearchResponse` (doc 05 §2.2), `fix = false` because the data is a
bare channel slug, not a path. `load()`:

```kotlin
val name = doc.select("div#app-title").text()
if (name.isBlank()) {
    throw RuntimeException("Could not load page, please try again.\n")
}
val rank = doc.select("div.rank-badge > span").last()?.text()?.toIntOrNull()
val isLive = doc.select("div.live-indicator-container").isNotEmpty()
val tags = listOfNotNull(
    isLive.let { if (it) "Live" else "Offline" },
    language,
    rank?.let { "Rank: $it" },
)
val twitchUrl = "https://twitch.tv/$realUrl"
return newLiveStreamLoadResponse(name, twitchUrl, twitchUrl) {
    plot = description; posterUrl = image; backgroundPosterUrl = poster; tags = tags
}
```
`TW:98-128` (trimmed) [verified]. The **blank-guard with a human-readable message** is unique in
the sample — community providers either `!!`-crash (DoramasFlix) or return null/empty silently.
Tags are synthesized from *scraped state* (Live/Offline, language, rank) — a nice illustration
that `tags` is free-form UI metadata, not just genres. `LiveStreamLoadResponse` sets
`comingSoon = dataUrl.isBlank()` (MA:2442-2458) so an offline channel renders as coming-soon
rather than unplayable [verified via MA].

### 7.4 `loadLinks` — nested extractor + external resolver API

```kotlin
override suspend fun loadLinks(data, …): Boolean =
    loadExtractor(data, subtitleCallback, callback)     // data = https://twitch.tv/<channel>

class TwitchExtractor : ExtractorApi() {
    override val mainUrl = "https://twitch.tv/"
    override val name = "Twitch"
    override val requiresReferer = false
    override suspend fun getUrl(url, referer, subtitleCallback, callback) {
        val response = app.get("https://pwn.sh/tools/streamapi.py?url=$url").parsed<ApiResponse>()
        response.urls?.forEach { (name, url) ->
            val quality = getQualityFromName(name.substringBefore("p"))
            callback(newExtractorLink(this.name, "${this.name} ${name.replace("${quality}p", "")}", url) {
                this.type = ExtractorLinkType.M3U8
                this.quality = quality
                this.referer = ""
            })
        }
    }
}
```
`TW:136-178` (trimmed) [verified]. The extractor is a **nested class of the provider** (registered
by the plugin, `TW-P:10-11`), delegates the actual HLS derivation to a third-party resolver API
(pwn.sh), maps `"720p"`-style keys through `getQualityFromName`, and sets the link **type** and
quality explicitly rather than letting them default. `referer = ""` (blank) is honored by the
player's data-source builder (doc 09 §1 — blank referers are omitted from request properties).

### 7.5 The plugin class: `BasePlugin` vs `Plugin`

```kotlin
@CloudstreamPlugin
class TwitchPlugin : BasePlugin() {
    override fun load() {                     // no Context parameter
        registerMainAPI(TwitchProvider())
        registerExtractorAPI(TwitchProvider.TwitchExtractor())
    }
}
```
`TW-P:7-12` [verified]. Community plugins subclass `Plugin` and override `load(context: Context)`
(UAK-P, DW-P, AC-P, AJL-P, EX-P all do); Twitch uses `BasePlugin` with a **context-less
`load()`** — the simpler entry point when no Android context is needed (doc 03 §5.1 documents
both). Dailymotion (`DM`) is the same shape but additionally demonstrates: paginated
`search(query, page): SearchResponseList` (`DM:65-71`), `TvType.Others` for a plain video site
(`DM:44`), and extension-function style mapping with the provider passed as receiver
(`toSearchResponse(provider)` `DM:80-88`) [verified].

### 7.6 What "official quality" means — the checklist delta

| Dimension | Community norm (§2-6) | Twitch/official |
|---|---|---|
| Error handling | `!!`, silent null, default-throw | guard + `RuntimeException("…try again")` (`TW:102-104`) |
| Concurrency | `amap` everywhere | sequential by design, reason commented (`TW:82`) |
| `hasNext` | default or hardcoded true | explicit `false` for finite rows (`TW:45`) |
| Pagination | string-concatenated URLs | `params = mapOf(...)` (`TW:47`) |
| Link construction | labels/quality as available | explicit `type`, `quality`, blank referer (`TW:170-174`) |
| Metadata/video split | same domain | tracker for metadata, twitch.tv for playback (`TW:28` vs `TW:118`) |
| Plugin entry | `Plugin` + context | `BasePlugin` minimal (`TW-P:7`) |

None of these require more code — they require *intent*. That's the bar.

---

## 8. The template baseline: ExampleProvider

The official skeleton a new author copies (`TestPlugins/ExampleProvider`), 20 lines:

```kotlin
class ExampleProvider : MainAPI() { // All providers must be an instance of MainAPI
    override var mainUrl = "https://example.com/"
    override var name = "Example provider"
    override val supportedTypes = setOf(TvType.Movie)

    override var lang = "en"

    // Enable this when your provider has a main page
    override val hasMainPage = true

    // This function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        return listOf()
    }
}
```
`EX:7-20` [verified]. Annotated, as our implementers will see it:

1. **`mainUrl` + `name`** — the two genuinely required members (03 §9).
2. **`supportedTypes`/`lang`** — recommended identity metadata.
3. **`hasMainPage = true` is enabled in the template while neither `mainPage` nor `getMainPage`
   is implemented** — as shipped, browsing this provider would hit the default
   `getMainPage` throw (`MA:632-637`; the comment says "Enable this when your provider has a main
   page", i.e. the flag is aspirational scaffolding) [verified + doc 06 §1.3's garbage-row
   consequence]. Minimal *viable* provider per 03 §9 = name + mainUrl + search (+load+loadLinks
   to be useful) — the template shows exactly that.
4. **`ExamplePlugin.kt`** carries the other half of the template: the settings hook and the
   activity grab:

```kotlin
@CloudstreamPlugin
class ExamplePlugin : Plugin() {
    private var activity: AppCompatActivity? = null
    override fun load(context: Context) {
        activity = context as? AppCompatActivity
        registerMainAPI(ExampleProvider())
        openSettings = {
            val frag = BlankFragment(this)
            activity?.let { frag.show(it.supportFragmentManager, "Frag") }
        }
    }
}
```
`EX-P:8-24` [verified] — `openSettings` is a **plugin-level lambda** (not a provider member);
it receives no arguments and must capture the activity itself. The `cloudstream {}` gradle block
completes the baseline: `version`, `status` (0 Down/1 Ok/2 Slow/3 Beta), `tvTypes`,
`language`, `iconUrl`, `requiresResources` (`ExampleProvider/build.gradle.kts` [verified]).

Template vs reality: **no real provider in the census implements `openSettings`** (§0 headline);
the template's settings example is aspirational for content providers, real only for utility
plugins (SyncPlugin).

---

## 9. Pattern synthesis — the field guide

### 9.1 The minimal viable provider checklist

Everything below is the *floor* observed across working providers:

- [ ] `class X : MainAPI()` + `override var mainUrl` + `override var name` (EX:7-9)
- [ ] `override var lang` (all 58 declare it; "mx"/"uk"/"es"/"en"/"uni" in the wild)
- [ ] `override val supportedTypes` — pick from the 18-value enum; the store filter and home
      chips read this (doc 10 §1-2)
- [ ] `search(query)` (legacy overload is the community norm; paginated overload is the
      "modern/official" form — DM:65)
- [ ] `load(url)` returning one of the five concrete LoadResponses (movie: `newMovieLoadResponse`
      with a `dataUrl`; series/anime: `newTvSeriesLoadResponse`/`newAnimeLoadResponse` with
      episodes)
- [ ] `loadLinks(data, isCasting, subtitleCallback, callback)` — either direct
      `M3u8Helper.generateM3u8(...).forEach(callback)` (DW:190-194) or
      `loadExtractor(url, referer, subtitleCallback, callback)` (AC:181, TW:142)
- [ ] `@CloudstreamPlugin class XPlugin : Plugin() { override fun load(ctx) {
      registerMainAPI(X()) } }` (UAK-P:8-13)
- [ ] `cloudstream { language; tvTypes; status; version }` gradle block (build.gradle.kts)

Optional but near-universal: `hasMainPage = true` + `mainPageOf(...)` + `getMainPage` (58/58 in
census — in practice the main page *is* the product surface), `hasQuickSearch = true` +
`quickSearch = search` delegate (UAK:97).

### 9.2 The rich provider checklist — everything CS3 can express

The union of LoadResponse/Episode surface (MA §5, doc 05 §3-4) vs who actually uses it in this
sample:

| Capability | API surface | Used by (sample) |
|---|---|---|
| poster + **backgroundPoster** | `posterUrl`, `backgroundPosterUrl` | AC:121/132, DFX:255, AJL:128, TW:125 |
| **logo** | `logoUrl` | none in sample |
| **actors** (names / roles / images) | `addActors(List<String>/Pair<Actor,Role>>)` (MA:1880-1912) | Uakino (names only, UAK:225) |
| **recommendations** | `recommendations: List<SearchResponse>` | UAK:184-187, DW:115, AC:116/126 (cross-provider legal — doc 07 §6.1) |
| **score** | `Score.from10`/`addScore(text, max)` (MA:2064-2069) | UAK:223, AC:124 |
| **duration** | `addDuration("N min")` → seconds (MA:2091) | AC:125 only |
| **year** | `year: Int?` | UAK (faked fallback), DW/AC/DFX(no) — the most-skipped vital field |
| **tags/genres** | `tags` | all deep-dives |
| **contentRating** | `contentRating` | UAK only |
| **trailers** | `addTrailer(url, referer, addRaw)` (MA:1960) | UAK only (3-stage extraction, UAK:350) |
| **showStatus** (Ongoing/Completed) | `showStatus` | none in sample (subtle: ongoing-series UX loses) |
| **nextAiring** | `nextAiring: NextAiring` (MA §4.3/doc 05) | none in sample |
| **seasonNames / SeasonData** | `addSeasonNames` (MA:2250-2262) | none — all use raw `season` ints on episodes |
| **dub maps** | `AnimeLoadResponse.episodes: Map<DubStatus, List<Episode>>` + `addEpisodes` (MA:2385-2388) | DW:171-172 (Dubbed+Subbed); UAK:226 (None); AJL: none (TvSeries-shaped) |
| **dub chips in search** | `AnimeSearchResponse.addDubStatus` (MA:1576-1584) | AJL:60 only |
| **posterHeaders** | `posterHeaders: Map<String,String>?` (doc 07 §3.3) | AJL:59/129 only |
| **syncData IDs** (MAL/IMDb/TMDb/AniList…) | `syncData` map + `addXId` helpers | none in sample (metaproviders' job — doc 07 §4) |
| **subtitleCallback** | emit `SubtitleFile`s during loadLinks | UAK:328-336 only |
| **extractorData tokens** | `ExtractorLink.extractorData` → `extractorVerifierJob` keep-alive (03 §2.10) | copied through blindly (AC:193, DFX:333) — nobody generates their own |
| **per-episode thumbnails** | `Episode.posterUrl` | AC:151, DFX:241, AJL:117 |
| **episode air dates** | `Episode.date` + `addDate` (MA:2578-2606) | none in sample |
| **synonyms** (anime alt titles) | `AnimeLoadResponse.synonyms` | none |
| **comingSoon** | auto-set by empty episodes/dataUrl (MA:2400-2407) | implicit everywhere |
| **uniqueUrl** | dedup identity override (MA §5) | none |
| **magnet/torrent links** | `newExtractorLink(..., ExtractorLinkType.MAGNET)` | AC:204-214 |
| **live responses** | `newLiveSearchResponse`/`newLiveStreamLoadResponse` | TW only |

ANI-KUTA note: the *median real provider uses maybe 30% of this table* — the rich end (AllCalidad
+ Uakino) reaches ~50%. A consumer UI must treat most of it as optional garnish (§10).

### 9.3 Common anti-patterns seen (with citations)

1. **`hasNext` lying**: hardcoded `true` (AJL:47) or default-always-true via
   `newHomePageResponse(items)` (DFX:153) → infinite page-1 re-fetch (doc 06 §5.2).
2. **`hasQuickSearch = true` without overriding `quickSearch`** → `NotImplementedError` when the
   single-provider quick-search path is hit (DFX:28; hazard class documented 03 §2.7).
3. **`hasMainPage` without `mainPage`/`getMainPage`** → default-row garbage or throw
   (EX:15 template itself; DFX:27 with default row — doc 06 §1.3).
4. **Missing or faked year** — no year at all (DFX both branches), or hardcoded fallback 2023
   (UAK:132). Year drives library sort, resume cards, and subtitle-search year filters (doc 09
   §3) — bad year data leaks into features the provider author never saw.
5. **Hardcoded URLs & domains**: mirror tables as `replaceFirst` chains (DFX:340-355, AJL:207-221
   — duplicated verbatim across two providers); dead-domain strips surviving in load
   (DFX:196); special-cased linker domains (AJL:156).
6. **Missing `posterHeaders`** on Cloudflare-protected sites — works only while the image CDN is
   permissive (only AJL does it right; §6.2).
7. **Declared-type vs runtime-type drift**: UAK declares 3 types but infers 6 (§2.4); Coaninet
   anime-as-TvSeries; AJL's `TvType.Movie` anime cards (§6.2). Harmless to CS3 (nothing
   validates — doc 10 §1) but a taxonomy headache for consumers.
8. **`!!` and silent `?:` on live-site data** — DFX:149-151 crashes on empty API arrays; UAK's
   `toString()` on nullable selectors fabricates `"null"` strings (UAK:81/89 [verified — e.g.
   `attr("src").toString()` on absent element yields "null" literal]) — compare TW's guard+message
   (§7.3).
9. **Unstructured `CoroutineScope(Dispatchers.IO).launch { callback(...) }` per link**
   (AC:182-196, DFX:322-336) — links can outlive the call; unstructured work leaks on cancel
   [inferred; tolerated by the app's generator, doc 08 §4].
10. **`dropLast(1)` style magic** — silently dropping stream variants (UAK:324) with no comment.
11. **Hand-rolled data protocols in `url`/`data`**: comma-packed episode names (UAK), hand-built
    JSON (DFX:166) vs the blessed `toJson` path (AC:101, MA:2491-2519). The framework supports
    JSON-in-URL explicitly (`MA:742-748`) — the anti-pattern is *bespoke* encodings that only the
    author's parser understands.

### 9.4 Category-specific patterns

- **Movie providers**: resolve the playable as early as possible — `dataUrl` baked at `load()`
  (DW:122-123 direct m3u8; DFX:246-248 links JSON) or a movie-id token resolved in `loadLinks`
  (AC:119 `post.id.toString()`; UAK passes the page URL itself). `newMovieLoadResponse(name, url,
  TvType.Movie, dataUrl)`; enrich with backdrop/duration/score when the API has them (AC:118-127).
- **Series providers**: flat `List<Episode>` with `season`/`episode` ints; the app's SeasonData
  overlay does grouping (doc 05 §4.2). Nobody in the sample uses `addSeasonNames`; episode `name`
  is either `"S{n}E{m}"` (AC:148) or the site's title (DW:153, DFX:238).
- **Drama providers**: the distinguishing question is *where dub/sub lives*. DoramyWorld splits
  episodes into `DubStatus.Dubbed/Subbed` maps keyed off playlist group titles ("Суб" test,
  DW:142); DoramasFlix flattens everything and moves language into **link labels** via
  `getLangById` (DFX:273); episode naming follows the site's localized ordinals ("Серія 1").
  Both use `TvType.AsianDrama` for the series branch and reuse Anime-shaped search cards for the
  dub-chip affordance (DW:89).
- **Anime providers**: three dub/sub strategies observed — (a) search-card `addDubStatus` only,
  TvSeries-shaped load (AJL); (b) `DubStatus.None` single map (UAK — voiceover as link label);
  (c) real Dubbed/Subbed episode maps (DW — the framework-intended shape). Episode numbers are
  positional (JS arrays, AJL:93-109) or playlist-indexed; `season = 0` means "no season" (AJL:115).
  Cloudflare protection → interceptor + posterHeaders (AJL:14-18, 59).
- **Video-site/live providers**: `TvType.Live` + `LiveSearchResponse`/`LiveStreamLoadResponse`
  (TW); `TvType.Others` for non-catalog video (DM:44); `isHorizontalImages = true` rows for
  channel tiles (TW:41,56); offline channels → `comingSoon` via blank dataUrl (MA:2454).

### 9.5 How providers encode genres as mainPage rows (summary of styles)

| Style | `data` content | Example |
|---|---|---|
| URL prefix + appended page | `$mainUrl/<cat>/page/` | UAK:26-34, DW:42-46, BambooUA, AnimeUA |
| Site path fragment | `movies`, `tvshows`, `series/?sort=popular` | CineHdPlus, Cuevana, LaMovie |
| Composite token `"type:id"` | `movies:26` (postType:genreId) | AC:42-61 |
| Query-string fragment | `animes?genre[]=46&order=updated` | AJL:30-35 |
| Category id in path/query | `/?Categoria_id=1` | LACartoons |
| Name-only token (dispatch in code) | `games` | TW:37-39 |
| Ignored (hardcoded sections) | – | DFX:128-154 |

There is no filter API (`//TODO genre selection or smth`, MA:420 per doc 06 §5.5) — every genre
the provider exposes is a row, and rows are the *only* browse taxonomy. Row counts in the census
range 0–18; median ~4 among the 36 that declare rows: AllCalidad's 18 = 3 content types + 15
genres; Streamed's 17 = sports competitions; Uakino's 6 = site categories.

---

## 10. What ANI-KUTA's Cloud Screen must support

Bullets derived from the field guide — what our UI/data layer must *tolerate*, because real
providers won't be uniform:

- **Tolerate the sparse end of the quality spectrum.** Providers ship with no year (DFX), faked
  year (UAK), no backdrop/logo/actors/score, no recommendations. Result cards, library entries,
  and the details header must render correctly with only `name + posterUrl + type` (the DoramyWorld/
  AnimeJl floor). Never make a missing field a crash or a blank screen; consider "unknown year"
  placeholders.
- **Treat `hasNext` as untrusted.** Two of six deep-dives lie (always-true). ANI-KUTA should cap
  consecutive empty/duplicate pages per row (dedupe by item URL before append) so an always-true
  provider degrades to "no more content" instead of looping [recommendation].
- **Expect `supportedTypes` drift.** Runtime `TvType` may exceed or contradict the declared set
  (§1.4 obs 2, §9.3 #7). Filter *discovery* on declared types but render whatever `load()`
  returns; compare by ordinal across the plugin boundary like CS3 does (doc 10 §2).
- **Support the settings *variations* — or their absence.** In the wild: no provider settings at
  all (58/58), plugin-level `openSettings` lambda (template, SyncPlugin). If ANI-KUTA's provider
  interface includes settings, they will be rarely used — but the *plugin* level (repo/account
  management) is where a settings UI still earns its place [recommendation].
- **Assume extractor dependence and its cost profile.** Half the deep-dive providers resolve
  streams through `loadExtractor` against third-party hosts, with packed-JS unpacking,
  AES-GCM decryption, and **WebView fallbacks** (SW:199) that can take seconds and can fail
  wholesale. Consequences: (a) loadLinks must be async with per-mirror failure isolation; (b) the
  mirror list is dynamic and labeled with provider-custom strings ("Latino · HD · Streamwish",
  "Coreano[VidHide]") — render labels as opaque strings; (c) mirror-domain rotation is endemic
  (fixHostsLinks tables) — a provider update can change available mirrors between sessions.
- **Episode identity is provider-proprietary.** `data` strings are opaque (URLs, JSON blobs,
  comma-packed `ajaxUrl,Episode 5`, slug fragments). Persist them verbatim as the playback key
  (CS3 keys caches by `apiName+data`, doc 08 §4) and never parse them in UI code.
- **Dub/sub handling must work at three levels** — search-card chips (AJL), load-time DubStatus
  maps (DW), and per-link language labels (DFX/AC). Our anime-first UI should map all three onto
  one language-track concept, defaulting gracefully when only labels exist [recommendation].
- **Per-episode thumbnails are a bonus, not a baseline** (AC/AJL have them; UAK/DW don't) — the
  episode list needs a poster-fallback chain (episode still → show poster → placeholder).
- **Live/NSFW/Others types appear outside "streaming catalogs"** (TW live, HentaiUkr NSFW-only,
  DM Others) — the Cloud Screen's type system should reserve renderers for Live (no resume, no
  episode list — doc 10 §5) and Others (flat video cards) even if we don't ship them day one.
- **The official-quality behaviors are cheap wins**: user-readable load errors, explicit finite
  rows, deliberate throttling, params-based pagination (§7.6 table). Bake them into ANI-KUTA's
  provider *template* from day one so our own Cloud providers start at the Twitch bar, not the
  DoramasFlix bar [recommendation].

---

## 11. Could not verify

- **Runtime behavior of any provider** — no site was contacted; all analysis is static. Whether
  uakino.best / allcalidad.re / doramasflix.co etc. still respond, and whether selectors still
  match, is unverifiable from the snapshot.
- **`dropLast(1)` rationale in Uakino** (`UAK:324`) — plausibly dropping a junk/trailer variant;
  no comment, no test [inferred].
- **`comamosramen.com` prefix theory** for DFX:196 — the substringAfter-no-op reading is certain
  from the code; the *history* (old mainUrl + pre-guard fixUrl prefixing JSON) is a plausible
  reconstruction only [inferred].
- **Unstructured-callback race effects** (§4.4 #3 / §9.3 #9) — reasoned from coroutine scoping;
  no runtime reproduction. The app's generator does keep accepting late links (doc 08 §4) so the
  practical effect is likely benign [inferred].
- **Whether `JSONModel.kt` (DoramyWorld) was ever used** — current provider has no import
  [verified as dead today]; git history not consulted.
- **storm-ext `assets/nopekey`** — unknown purpose, unread binary.
- **QuickSearch alias counts in the census** (§1.4 #4) — verified only for deep-dive providers;
  census rows are grep-level [inferred].
- **Pelisplus4KProvider's 57 registrations vs AnimeJl's** — same extractor families copied
  between the two plugins (files are byte-similar, `Pelisplus4KProvider/…/extractors/` vs
  `AJL-EX`) [verified at directory level, not line-diffed].

---

*End of doc 12. Census: 58 provider classes (35 storm-ext + 20 CakesTwix-ext + Twitch +
Dailymotion + ExampleProvider), 6 deep-dives, ~2,980 source lines read in full.*
