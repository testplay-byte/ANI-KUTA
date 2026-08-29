# 06 — Discovery in CloudStream: the Main-Page System & the Search System

> **Scope**: how content *discovery* works through CS3 extensions — the `mainPage`/`getMainPage`
> home-page contract and the `search`/`quickSearch` contract, **provider-side AND app-side**.
> Who calls `getMainPage`, how pagination (`page` param, `hasNext`) is driven, caching, how the home
> UI is sectioned, how search fans out across providers (parallelism, staleness, merge order),
> where quickSearch is actually reachable, plus 4 real provider patterns and an ANI-KUTA mapping preview.
> Companion docs: **03-mainapi-reference.md** (per-member contract) and **05-data-models.md** (payload
> shapes — `MainPageData`, `HomePageResponse`, `SearchResponseList`); this doc covers the *flow*.
>
> **Primary source**: `research/cloudstream/` (master @ efc1915, 2026-08-28 — same snapshot all docs cite).
>
> **Citation shorthand** (paths under `research/cloudstream/` unless noted):
> - `MA:<lines>` = `library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt`
> - `PC:<lines>` = `library/src/commonMain/kotlin/com/lagradost/cloudstream3/ParCollections.kt`
> - `AR:<lines>` = `app/src/main/java/com/lagradost/cloudstream3/ui/APIRepository.kt`
> - `HVM:<lines>` = `app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeViewModel.kt`
> - `HF:<lines>` = `app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeFragment.kt`
> - `HPIA:<lines>` = `app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeParentItemAdapter.kt`
> - `HPIP:<lines>` = `app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeParentItemAdapterPreview.kt`
> - `SVM:<lines>` = `app/src/main/java/com/lagradost/cloudstream3/ui/search/SearchViewModel.kt`
> - `SF:<lines>` = `app/src/main/java/com/lagradost/cloudstream3/ui/search/SearchFragment.kt`
> - `SRB:<lines>` = `app/src/main/java/com/lagradost/cloudstream3/ui/search/SearchResultBuilder.kt`
> - `QSF:<lines>` = `app/src/main/java/com/lagradost/cloudstream3/ui/quicksearch/QuickSearchFragment.kt`
> - `AU:<lines>` = `library/src/commonMain/kotlin/com/lagradost/cloudstream3/utils/AppContextUtils.kt`
>   (also `app/.../utils/AppContextUtils.kt` — the filter helpers live in the app copy; cited where read)
> - storm-ext / CakesTwix-ext paths are written out in full.
> - ANI-KUTA paths under `ANI-KUTA/APP/ani-kuta/` are written out in full.
>
> **Markers**: `[verified]` = read in source · `[docs]` = from recloudstream/csdocs · `[inferred]` = reasoned, needs verification.

---

## Table of contents

1. [The main-page system (provider contract)](#1-the-main-page-system-provider-contract)
2. [How the app drives getMainPage](#2-how-the-app-drives-getmainpage)
3. [Search flow](#3-search-flow)
4. [quickSearch](#4-quicksearch)
5. [Real provider patterns](#5-real-provider-patterns)
6. [ANI-KUTA mapping preview](#6-ani-kuta-mapping-preview)
7. [Could not verify / surprises](#7-could-not-verify--surprises)

---

## 1. The main-page system (provider contract)

### 1.1 `mainPage` property vs `mainPageOf` helper

The provider declares its home-page rows as a **list of `MainPageData`** on the `mainPage` property;
the default is a single *empty* entry (a documented pitfall — see 03 §2.3):

```kotlin
//emptyList<MainPageData>() //
open val mainPage = listOf(MainPageData("", "", false))
```
`MA:629-630` [verified]

Each entry becomes one horizontal shelf on the home screen and one `MainPageRequest` passed to
`getMainPage()`. `MainPageData` / `MainPageRequest` are byte-for-byte the same three fields (the
request just drops the defaults and carries the app's `//TODO genre selection or smth` — the only
trace of a filter system that was never finished, MA:420 [verified]):

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
`MA:410-421` [verified]

- `name` — row label shown in the UI (`homeChildMoreInfo.text = info.name`, HPIA:128 [verified]).
- `data` — **opaque provider-defined token**: a URL prefix (Uakino), a `"postType:genreId"` pair
  (AllCalidad), a numeric id (`"1"`/`"2"`/`"3"` in the official tutorial), anything (§5).
- `horizontalImages` — wide landscape cards for that row (HPIA:108 `isHorizontal = info.isHorizontal` [verified]).

**`mainPageOf` is NOT a member** — it's a pair of free functions next to the models. Two shapes exist
and both build the same list:

```kotlin
fun mainPage(url: String, name: String, horizontalImages: Boolean = false): MainPageData {
    return MainPageData(name = name, data = url, horizontalImages = horizontalImages)
}

/** return list of MainPageData with url to name, make for more readable code
 * @param elements parameter of [MainPageData] class of data*/
fun mainPageOf(vararg elements: MainPageData): List<MainPageData> {
    return elements.toList()
}

/** return list of MainPageData with url to name, make for more readable code
 * @param elements parameter of <String, String> map of url and name */
fun mainPageOf(vararg elements: Pair<String, String>): List<MainPageData> {
    return elements.map { (url, name) -> MainPageData(name = name, data = url) }
}
```
`MA:423-442` [verified]

**Exact difference**: `mainPage` (the property) is *what the app reads* to enumerate rows;
`mainPageOf(...)` (the helper) is *how providers write that list* — either from `MainPageData`
elements (use `mainPage(url, name, horizontal)` for the rare horizontal row) or, most commonly, from
`Pair<String, String>` where the first component is the row token (`data`) and the second the display
name. The Pair overload always produces `horizontalImages = false` (MA:441 [verified]) — a horizontal
hero-style row requires the `MainPageData` overload.

### 1.2 The response: `HomePageResponse` / `HomePageList` / `hasNext`

```kotlin
data class HomePageResponse
@Deprecated("Use newHomePageResponse method", level = DeprecationLevel.ERROR)
constructor(
    val items: List<HomePageList>,
    val hasNext: Boolean = false
)

data class HomePageList(
    val name: String,
    var list: List<SearchResponse>,
    val isHorizontalImages: Boolean = false
)
```
`MA:1270-1286` [verified]

One `getMainPage` call may return **multiple `HomePageList` sections** (not just the one matching
`request.name` — see DoramasFlix, §5.2). Build responses with the free-function builders (raw
constructors are `@Deprecated(ERROR)`):

```kotlin
fun newHomePageResponse(name: String, list: List<SearchResponse>, hasNext: Boolean? = null): HomePageResponse
fun newHomePageResponse(data: MainPageRequest, list: List<SearchResponse>, hasNext: Boolean? = null): HomePageResponse
fun newHomePageResponse(list: HomePageList, hasNext: Boolean? = null): HomePageResponse
fun newHomePageResponse(list: List<HomePageList>, hasNext: Boolean? = null): HomePageResponse
```
`MA:444-476` [verified] — note `hasNext` **defaults to `list.isNotEmpty()`** (single/`HomePageList`
overloads, MA:452/464/470) or `list.any { it.list.isNotEmpty() }` (list-of-lists overload, MA:475),
so returning an empty page naturally terminates pagination.

`getMainPage` itself:

```kotlin
// @WorkerThread
open suspend fun getMainPage(
    page: Int,
    request: MainPageRequest,
): HomePageResponse? {
    throw NotImplementedError()
}
```
`MA:632-638` [verified] — default **throws**; `page` starts at 1 and increments as the user scrolls
(`csdocs/devs/create-your-own-providers.md:99` [docs]).

### 1.3 `hasMainPage` gating

```kotlin
open val hasMainPage = false
```
`MA:563` [verified]

The app checks this in three places, all before any row is fetched:

- `HomeViewModel.load` short-circuits with `Resource.Success(emptyMap())` + `_preview Failure("No homepage")`
  when `repo?.hasMainPage != true` (HVM:329-333 [verified]).
- The provider-picker bottom sheet only lists providers with `it.hasMainPage` (HF:494, inside
  `selectHomepage`'s `updateList()` filter [verified]).
- `Context.filterProviderByPreferredMedia(hasHomePageIsRequired = Boolean = true)` filters the global
  `apis` list by `(hasUniversal || langs.contains(api.lang)) && (api.hasMainPage || !hasHomePageIsRequired)`
  (app `utils/AppContextUtils.kt:447-479` [verified]) — the home picker uses the default `true`, the
  *search* provider picker passes `hasHomePageIsRequired = false` (SF:286 [verified]).

Consequence: `hasMainPage = true` + a forgotten `mainPage`/`getMainPage` override yields garbage
requests (the single empty default entry) or a `NotImplementedError` surfaced as an error `Resource`
— 03 §2.3/§2.4 document the same pitfall from the contract side.

### 1.4 `sequentialMainPage` & the delay knobs

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
`MA:522-534` [verified]

Where consumed (all in `APIRepository.getMainPage`, see §2.1):

- `sequentialMainPage = true` → the provider's `mainPage` entries are fetched **serially**, sleeping
  `sequentialMainPageDelay` ms *between* entries (skipped before the first) — `AR:169-180` [verified].
- `sequentialMainPage = false` (default) → all entries fetched concurrently in `async` blocks — `AR:181-192` [verified].
- `sequentialMainPageScrollDelay` → honored on *scroll-triggered* (pagination) loads through
  `waitForHomeDelay()`, which delays `lastHomepageRequest + scrollDelay - now` if positive — `AR:150-154` [verified],
  called from `HomeViewModel.expandAndReturn` before every page-2+ fetch (HVM:239 [verified]).
- `lastHomepageRequest` is written by the app on every `getMainPage` call (`AR:159` [verified]) —
  it is `var`, **not `open`**; providers must not touch it.

These are anti-rate-limit knobs: a provider with N rows and `sequentialMainPage = false` fires N
concurrent HTTP requests on every home refresh; rate-limit-sensitive sites flip it on with delays
(the KDoc says exactly this, MA:522-523 [verified]).

### 1.5 Per-method timeout hints & the app-side clamp

```kotlin
open val getMainPageTimeoutMs: Long? = null    // MA:575-580
open val searchTimeoutMs: Long? = null         // MA:582-587
open val quickSearchTimeoutMs: Long? = null    // MA:589-594
open val loadLinksTimeoutMs: Long? = null      // MA:566-573
open val loadTimeoutMs: Long? = null           // MA:596-601
```
`MA:566-601` [verified] — all `open val Long? = null`, all KDoc'd as *"only a hint, and may not get
respected if you request something too long"* (e.g. MA:578 [verified]).

The app clamps every hint into **[5 s, 8 min]** around a 2-minute default:

```kotlin
// 2 minute timeout to prevent bad extensions/extractors from hogging the resources
// No real provider should take longer, so we hard kill them.
private const val DEFAULT_TIMEOUT = 120_000L
private const val MAX_TIMEOUT = 4 * DEFAULT_TIMEOUT
private const val MIN_TIMEOUT = 5_000L

fun getTimeout(desired: Long?): Long {
    return (desired ?: DEFAULT_TIMEOUT).coerceIn(MIN_TIMEOUT, MAX_TIMEOUT)
}
```
`AR:28-64` [verified] — so the assignment's guess "5s–8min?" is **confirmed** (MIN 5 000 ms, MAX
480 000 ms = 8 min). Each call site wraps the provider call in `withTimeout(getTimeout(api.<hint>))`:
`getMainPage` AR:158, `search` AR:128, `quickSearch` AR:141, `load` AR:87, `loadLinks` AR:212 [verified].

Note a subtlety: for `getMainPage` the timeout wraps the **whole batch** (one row or all rows —
sequential delay time counts against the same budget), because the `withTimeout` is outside the
row loop (AR:156-194 [verified]).

---

## 2. How the app drives getMainPage

### 2.1 `APIRepository.getMainPage(page, nameIndex)` — the single funnel

Every provider call goes through one wrapper per provider instance:

```kotlin
suspend fun getMainPage(page: Int, nameIndex: Int? = null): Resource<List<HomePageResponse?>> {
    return safeApiCall {
        withTimeout(getTimeout(api.getMainPageTimeoutMs)) {
            api.lastHomepageRequest = unixTimeMS

            nameIndex?.let { api.mainPage.getOrNull(it) }?.let { data ->
                listOf(
                    api.getMainPage(
                        page,
                        MainPageRequest(data.name, data.data, data.horizontalImages)
                    )
                )
            } ?: run {
                if (api.sequentialMainPage) {
                    var first = true
                    api.mainPage.map { data ->
                        if (!first) // dont want to sleep on first request
                            delay(api.sequentialMainPageDelay)
                        first = false
                        api.getMainPage(page, MainPageRequest(data.name, data.data, data.horizontalImages))
                    }
                } else {
                    with(CoroutineScope(coroutineContext)) {
                        api.mainPage.map { data ->
                            async {
                                api.getMainPage(page, MainPageRequest(data.name, data.data, data.horizontalImages))
                            }
                        }.map { it.await() }
                    }
                }
            }
        }
    }
}
```
`AR:156-196` [verified] (bodies above kept verbatim; only line-wrapping collapsed)

Three modes:

1. **`nameIndex != null`** (scroll pagination): fetch exactly ONE row — the `mainPage[nameIndex]`
   entry — for `page` (AR:161-167).
2. **`nameIndex == null`, `sequentialMainPage = true`**: fetch ALL rows serially with the delay
   between them (AR:169-180).
3. **`nameIndex == null`, default**: fetch ALL rows concurrently (`async`/`await` per entry, AR:181-192).

Everything is inside `safeApiCall` → exceptions/timeout become `Resource.Failure` (shown as the
"connection error" home state, HF:875-916 [verified]). Caching: **there is no disk or memory cache
for main-page responses** — `safeApiCall`+`withTimeout` is the whole wrapper (the only provider-data
cache in `APIRepository` is the 20-slot rolling 10-min `SavedLoadResponse` cache for `load()` results,
AR:52-121 [verified]; the home screen *shells* are re-fetched every provider switch / force reload).

### 2.2 `HomeViewModel` — the actual driver

`HomeViewModel` owns ONE active provider (`repo: APIRepository?`, HVM:122) and exposes:

- `page: LiveData<Resource<Map<String, ExpandableHomepageList>>>` — the rows, keyed **by row name**
  (HVM:228-230). `ExpandableHomepageList` is the app's pagination wrapper:

```kotlin
data class ExpandableHomepageList(
    var list: HomePageList,
    var currentPage: Int,
    var hasNext: Boolean,
)
```
`HVM:221-225` [verified]

**Initial load** (page 1, all rows) happens in `load(api)`:

```kotlin
when (val data = repo?.getMainPage(1, null)) {
    is Resource.Success -> {
        expandable.clear()
        data.value.forEach { home ->
            home?.items?.forEach { list ->
                val filteredList =
                    context?.filterHomePageListByFilmQuality(list) ?: list
                expandable[list.name] =
                    ExpandableHomepageList(
                        filteredList.copy(
                            list = CopyOnWriteArrayList(
                                filteredList.list
                            )
                        ), 1, home.hasNext
                    )
            }
        }
        ...
```
`HVM:341-358` [verified] — page 1, `nameIndex = null` (all rows at once, concurrent unless
`sequentialMainPage`), each returned `HomePageList` re-keyed by its `name`, NSFW/quality prefs applied
via `filterHomePageListByFilmQuality` (app `utils/AppContextUtils.kt:500-519` — drops items whose
`SearchQuality` ordinal is in the user's blocklist [verified]). Note `currentPage = 1` and
`hasNext = home.hasNext` seed the pagination state **per provider**, not per row (a known upstream
limitation: if one getMainPage batch returns sections with different continuation state, the last
`hasNext` wins per row since each `HomePageList` name maps to one entry — rows returned by later
calls overwrite `expandable[list.name]`, HVM:349-356 [verified]).

**Pagination (scroll)** is per-row, in `expandAndReturn(name)`:

```kotlin
suspend fun expandAndReturn(name: String): ExpandableHomepageList? {
    if (lock.contains(name)) return null
    lock += name

    repo?.apply {
        waitForHomeDelay()                       // sequentialMainPageScrollDelay (AR:150-154)

        expandable[name]?.let { current ->
            val nextPage = current.currentPage + 1
            val next = getMainPage(nextPage, mainPage.indexOfFirst { it.name == name })
            if (next is Resource.Success) {
                next.value.filterNotNull().forEach { main ->
                    main.items.forEach { newList ->
                        expandable[newList.name]?.apply {
                            hasNext = main.hasNext
                            currentPage = nextPage
                            // … debugWarning on duplicate urls (HVM:256-258) …
                            this.list.list += newList.list
                            this.list.list.distinctBy { it.url } // just to be sure we are not adding the same shit for some reason
                        } // ?: debugWarning "Expanded an item not in main load named …" (HVM:262-264)
                    }
                }
            } else {
                current.hasNext = false          // failure stops pagination for this row
            }
        }
        _page.postValue(Resource.Success(expandable))
    }

    lock -= name
    return expandable[name]
}
```
`HVM:234-277` [verified] (elided lines marked `// …`)

Mechanics worth copying (and one bug worth not copying):

- `page` increments from the stored `currentPage` (starts at 1 → first expand fetches page 2).
- The row is located by **name**: `mainPage.indexOfFirst { it.name == name }` → that index becomes
  `nameIndex` in the single-row `APIRepository.getMainPage` call (HVM:247, AR:161-167).
- `lock: MutableSet<String>` prevents double-fires for the same row (HVM:232, 235-236).
- **Bug**: `this.list.list.distinctBy { it.url }` (HVM:261) discards its result — the dedup is a
  no-op and duplicates DO get appended (only the `debugWarning` above it notices, HVM:256-258).
  The search-side twin does it correctly: `this.list = (this.list + nextValue.items).distinctBy { it.url }`
  (SVM:150 [verified]). Home-row dedup only happens visually via the diff-based adapter.
- On `Resource.Failure` the row's `hasNext` flips to `false` → infinite scroll stops silently (HVM:267-269).

**Provider switching** — the home screen shows exactly ONE provider at a time (there is no
cross-provider interleaving of rows; see §2.4). `loadAndCancel(preferredApiName, forceReload, fromUI)`
resolves the name and delegates:

```kotlin
fun loadAndCancel(preferredApiName: String?, forceReload: Boolean = true, fromUI: Boolean = false) =
    ioSafe {
        val currentPage = page.value
        // if we don't need to reload and we have a valid homepage or currently loading the same thing then return
        val currentLoading = isCurrentlyLoadingName
        if (!forceReload && (currentPage is Resource.Success && currentPage.value.isNotEmpty() || (currentLoading != null && currentLoading == preferredApiName))) {
            return@ioSafe
        }
        val api = getApiFromNameNull(preferredApiName)
        if (preferredApiName == noneApi.name) { /* just set to random */ … }
        else if (preferredApiName == randomApi.name) {
            // randomize the api, if none exist like if not loaded or not installed
            // then use nothing
            val validAPIs = context?.filterProviderByPreferredMedia()
            … loadAndCancel(apiRandom); if (fromUI) DataStoreHelper.currentHomePage = apiRandom.name
        } else if (api == null) { … } else {
            if (fromUI) DataStoreHelper.currentHomePage = api.name
            loadAndCancel(api)
        }
    }
```
`HVM:501-551` [verified] (branches elided with `…`) — the selected provider is persisted as
`DataStoreHelper.currentHomePage`; special pseudo-providers `noneApi` ("None") and `randomApi`
("Random" → picks a random provider from the preferred-media-filtered list each time, HVM:524-534
[verified]) sit at the top of the picker. The inner two-arg `loadAndCancel(api: MainAPI)` (HVM:214-219)
**cancels the in-flight load job** before starting a new one (`onGoingLoad?.cancel()`), so switching
providers mid-load aborts the old fan-out [verified].

**When does it load?** Not lazily per-row — the whole page-1 batch fires when:

- the fragment binds: `homeViewModel.loadAndCancel(DataStoreHelper.currentHomePage, false)` (HF:956 [verified]) —
  `forceReload = false` so the ViewModel's stale-guard skips if a valid page is already loaded (HVM:513-517);
- plugins finish loading: `MainActivity.afterPluginsLoadedEvent` / `mainPluginsLoadedEvent` /
  `reloadHomeEvent` all re-trigger `loadAndCancel(DataStoreHelper.currentHomePage, …)` (HVM:438-448, 456-461 [verified]);
- the user switches provider via the picker, or long-presses the provider FAB (force reload):
  `homeViewModel.loadAndCancel(api, forceReload = true, fromUI = true)` (HF:623, HF:684-689, HF:703-710 [verified]).

### 2.3 The UI layer: rows, auto-expand, expanded sheet, hero preview

- `HomeFragment` uses `HomeParentItemAdapterPreview` (wraps `ParentItemAdapter`, itself a
  `BaseAdapter<ExpandableHomepageList, Bundle>`) on `homeMasterRecycler` (HF:695-699 [verified]).
  Phone and TV share the adapter; only layouts differ (HPIA:170-175 [verified]).
- `ParentItemAdapter.submitList` **sorts empty rows to the bottom** (`list?.sortedBy { it.list.list.isEmpty() }`,
  HPIA:71-76 [verified]); row identity is the row `name` (HPIA:44) — the same key the ViewModel's
  `expandable` map uses.
- Each row binds a horizontal `HomeChildItemAdapter` with `hasNext` propagated from the
  `ExpandableHomepageList` (HPIA:99-121 [verified]).
- **Infinite scroll on a row** is scroll-state-based, not position-based — when the row's RecyclerView
  can no longer scroll (`!recyclerView.isRecyclerScrollable()`) and `hasNext` is still true, it calls
  `expandCallback(name)` (= `viewModel.expand(name)`, HVM:280-282), guarded by `expandCount != count`
  to avoid re-fires while items haven't changed (HPIA:130-159 [verified]).

```kotlin
override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
    ...
    if (!recyclerView.isRecyclerScrollable() && hasNext && expandCount != count) {
        expandCount = count
        expandCallback?.invoke(name)
    }
}
```
`HPIA:135-158` [verified]

- **"See all" on a row** (phone: tapping the row label) opens a full-screen `BottomSheetDialog`
  (`Activity.loadHomepageList`, HF:136-278) that reuses `SearchAdapter` in a grid and re-implements
  the same auto-expand scroll listener against `homeViewModel.expandAndReturn(it)` (HF:231-257, 947-952 [verified]).
- **Hero preview**: `HomeViewModel.load` also flattens all rows, `shuffled()`s them, dedups by URL,
  and loads **3 full `LoadResponse`s** via `repo.load(url)` for the preview pager; the pager
  lazy-loads one more as the user swipes (`updatePreviewResponses`, HVM:285-309; `loadMoreHomeScrollResponses`,
  HVM:311-317; wired in the preview `ViewPager2.OnPageChangeCallback`, HPIP:479-494 [verified]).
  This is the only place the home screen performs extra `load()` calls purely for UI richness.
- **TV**: `saveHomepageToTV` pushes the *first* section's items into the Android TV channel
  ("Add programs … to TV", HF:642-660 [verified]) — the home rows also feed the system "continue
  watching" channel, not just in-app UI.

### 2.4 Do rows from ALL enabled providers interleave? — No.

The home screen is **single-provider**: `DataStoreHelper.currentHomePage` names exactly one provider
(or None/Random), `HomeViewModel.repo` wraps exactly that `MainAPI`, and the rows shown are that
provider's `mainPage` entries (HVM:122, 136, 341-358; HF:819-845 [verified]). Multi-provider surface
exists only through:

- the provider picker (`selectHomepage` bottom sheet: `filterProviderByPreferredMedia()` + `noneApi`
  + `randomApi` prepended, pinned providers sorted first, TvType chips filtering — HF:383-552 [verified]);
- "Random" mode, which picks one provider per load (HVM:524-534 [verified]);
- the optional Random *button*, which picks a random item from the current provider's flattened rows
  (HF:855-872 [verified]).

By contrast, **search** fans out across all enabled providers (§3). If ANI-KUTA expects a
multi-source browse/home (like aniyomi's source list), CS3's home model does not provide it — that's
a deliberate design difference to note in the mapping (§6).

---

## 3. Search flow

### 3.1 The two `search` overloads (provider side)

```kotlin
/** Paginated search, starts with page: 1 */
open suspend fun search(query: String, page: Int): SearchResponseList? {
    val searchResults = search(query) ?: return null

    return newSearchResponseList(
        searchResults,
        false
    )
}

// @WorkerThread
open suspend fun search(query: String): List<SearchResponse>? {
    throw NotImplementedError()
}
```
`MA:640-653` [verified]

- The **paginated overload is the one the app calls** (`a.search(query, 1)` in the fan-out, SVM:235 [verified]).
  It is the only MainAPI method with a real default body: delegates to the legacy overload and reports
  `hasNext = false` (MA:642-647).
- Providers that only override `search(query)` therefore get non-paginated search automatically;
  big sites should override the paginated one (03 §2.5/§2.6 document the same from the contract side).
- `SearchResponseList` is `{ items: List<SearchResponse>, hasNext: Boolean }` with the raw constructor
  deprecated-ERROR; build with `newSearchResponseList(list, hasNext)` / `list.toNewSearchResponseList(hasNext)`
  (MA:478-491, 1292-1297 [verified]).

`APIRepository` wraps both search entry points:

```kotlin
suspend fun search(query: String, page: Int): Resource<SearchResponseList> {
    if (query.isEmpty())
        return Resource.Success(newSearchResponseList(emptyList()))
    return safeApiCall {
        withTimeout(getTimeout(api.searchTimeoutMs)) {
            (api.search(query, page)
                ?: throw ErrorLoadingException())
        }
    }
}
```
`AR:123-134` [verified] — empty query never reaches the provider; `null` results become
`Resource.Failure`. There is **no result cache** on the search path (unlike `load()`) [verified].

### 3.2 Fan-out: `SearchViewModel.search` — parallel across ALL enabled providers

`SearchViewModel` pre-builds an `APIRepository` for **every** registered provider:

```kotlin
private var repos = apis.withLock { apis.map { APIRepository(it) } }
```
`SVM:52` [verified] — rebuilt on plugin load (`reloadRepos()`, SVM:70-72; triggered from
`SearchFragment.onResume`'s `afterPluginsLoadedEvent` registration, SF:150-158, 202-218 [verified]).

A search is triggered by `searchAndCancel`, which is the cancellation entry point:

```kotlin
fun searchAndCancel(
    query: String,
    providersActive: Set<String> = setOf(),
    ignoreSettings: Boolean = false,
    isQuickSearch: Boolean = false,
) {
    currentSearchIndex++
    onGoingSearch?.cancel()
    onGoingSearch = search(query, providersActive, ignoreSettings, isQuickSearch)
}
```
`SVM:74-83` [verified]

The body:

```kotlin
viewModelScope.launchSafe {
    val currentIndex = currentSearchIndex
    if (query.length <= 1) {
        clearSearch()
        return@launchSafe
    }

    if (!isQuickSearch) {
        // … write SearchHistoryItem to "$currentAccount/$SEARCH_HISTORY_KEY" (SVM:212-222) …
    }

    _searchResponse.postValue(Resource.Loading())
    _currentSearch.postValue(emptyMap())
    expandableSearches.clear()
    lastQuery = query

    withContext(Dispatchers.IO) { // This interrupts UI otherwise
        repos.filter { a ->
            (ignoreSettings || (providersActive.isEmpty() || providersActive.contains(a.name))) && (!isQuickSearch || a.hasQuickSearch)
        }.amap { a -> // Parallel
            val search = if (isQuickSearch) a.quickSearch(query) else a.search(query, 1)
            if (currentSearchIndex != currentIndex) return@amap
            if (search is Resource.Success) {
                val searchValue = search.value
                expandableSearches[a.name] =
                    ExpandableSearchList(searchValue.items, 1, searchValue.hasNext)
            }
            _currentSearch.postValue(expandableSearches)
        }

        if (currentSearchIndex != currentIndex) return@withContext // this should prevent rewrite of existing data bug

        _currentSearch.postValue(expandableSearches)
        val list = bundleSearch(expandableSearches)
        _searchResponse.postValue(Resource.Success(list))
    }
}
```
`SVM:198-253` [verified] (history block elided)

The facts, point by point:

- **Parallel**: `amap` = "Asynchronous Map" — `coroutineScope { map { async { f(it) } }.map { it.await() } }`
  (`PC:36-40` [verified]) — one `async` per provider, all awaited. Order of the *repo list* is the
  registration order of `apis` (the `APIHolder.apis` observable list), but completion order is
  whatever the network does; nothing is serialized and there is no per-provider scheduling.
- **Provider set**: filtered by (a) `providersActive` (empty = all, see §3.3 for what the fragment
  passes) and (b) for quickSearch, `a.hasQuickSearch` (SVM:232-234 [verified]). NOT filtered by
  `supportedTypes` here — type filtering happens in the fragment before the call (SF:182-194 [verified]).
- **Live incremental UI**: each provider's success immediately posts `_currentSearch`
  (the per-provider map) — the UI updates as providers stream in (SVM:239-243 [verified]).
- **Staleness/cancellation, two layers**:
  1. Job-level: `onGoingSearch?.cancel()` in `searchAndCancel` (SVM:81 [verified]) — a new search
     cancels the whole coroutine (and with it all in-flight `async` children, since `amap`'s
     `coroutineScope` is inside the cancelled job).
  2. Generation guard: `currentSearchIndex != currentIndex → return` per provider *and* before the
     final aggregate post (SVM:236, 246 [verified]) — protects against results posted between
     cancellation and re-launch (e.g. from quickSearch paths that don't bump the index).
  There is **no debounce on the search query itself** — search fires on submit only (§3.3); the only
  debounced thing on the search screen is the *suggestion* fetch (300 ms, SVM:98-111 [verified]).
- **History**: written for regular search, skipped for quickSearch (SVM:211-223 [verified]).

### 3.3 What the fragment passes & how results render

`SearchFragment.search(query)` computes the active provider set through a 3-stage filter chain:

```kotlin
val notFilteredBySelectedTypes = selectedApis.filter { name ->
    settings.contains(name)            // ctx.getApiSettings() — enabled providers
}.map { name ->
    name to getApiFromNameNull(name)?.supportedTypes
}.filter { (_, types) ->
    types?.any { preferredTypes.contains(it.ordinal) } == true   // user's preferred media types
}

searchViewModel.searchAndCancel(
    query = query,
    providersActive = notFilteredBySelectedTypes.filter { (_, types) ->
        types?.any { selectedSearchTypes.contains(it) } == true  // active TvType chips (fallback to all)
    }.ifEmpty { notFilteredBySelectedTypes }.map { it.first }.toSet()
)
```
`SF:168-197` [verified] — `selectedApis` comes from the provider-picker bottom sheet
(`DataStoreHelper.searchPreferenceProviders`, SF:282, 395-401 [verified]).

Rendering has **two modes** (setting `advanced_search`, default **true**, SF:409 [verified]):

- **Advanced (default)**: `searchMasterRecycler` + `ParentItemAdapter` — the *home row adapter reused*
  (SVM:123 comment "ExpandableHomepageList because the home adapter is reused in the search fragment",
  SVM:124 [verified]). Each provider becomes one section `HomePageList(providerName, dataListFiltered)`
  wrapped in `ExpandableHomepageList`, **sorted by pinned-provider order** (`DataStoreHelper.pinnedProviders.reversedArray()`,
  SF:494-525 [verified]). Rows expand with the same auto-expand pattern via
  `searchViewModel.expandAndReturn(name)` (SF:551-555 [verified]).
- **Non-advanced**: `searchAutofitResults` + `SearchAdapter` grid fed by the *merged* list from
  `searchResponse` (SF:466-479 [verified]).

The merged list is built by **round-robin interleave** across providers:

```kotlin
private fun bundleSearch(lists: MutableMap<String, ExpandableSearchList>): ExpandableSearchList {
    if (lists.size == 1) {
        return lists.values.first()
    }

    val list = ArrayList<SearchResponse>()
    val nestedList = lists.map { it.value.list }

    // I do it this way to move the relevant search results to the top
    var index = 0
    while (true) {
        var added = 0
        for (sublist in nestedList) {
            if (sublist.size > index) {
                list.add(sublist[index])
                added++
            }
        }
        if (added == 0) break
        index++
    }

    return ExpandableSearchList(list, 1, false)
}
```
`SVM:172-196` [verified]

- Take each provider's result #0, then each provider's #1, etc. — "to move the relevant search
  results to the top" (SVM:181 [verified]). Single-provider searches skip the interleave entirely.
- **No cross-provider dedup** in `bundleSearch` — the same title from 5 providers appears 5 times
  [verified]. Dedup exists only *within* a provider, on pagination: `this.list = (this.list + nextValue.items).distinctBy { it.url }`
  (SVM:150 [verified]). (ANI-KUTA's search dedups by `sourceId:url` grid key — §6.)
- Provider order in the interleave = `expandableSearches` insertion order = provider completion order
  (each success adds to the map, SVM:239) — so the merge order is effectively *network race order*,
  then round-robin within it [inferred from SVM:177-193 + 239-243].

**Search pagination** (load-more within one provider) mirrors home-row pagination:
`SearchViewModel.expandAndReturn(name)` (SVM:124-170 [verified]) — `repo.search(query, nextPage)`
with the SAME `lastQuery`, per-provider `currentPage`/`hasNext`, in-provider URL dedup, and it maps
the result back into an `ExpandableHomepageList` so the shared row adapter can render it
(SVM:164-169 [verified]).

### 3.4 Search-result badge rendering (where 05's models surface)

`SearchResultBuilder.bind` renders every card (used by `SearchAdapter`, the home child rows, the
expanded sheets, resume/bookmark rows — SRB:33-329 [verified]):

| Model field (doc 05) | Renders as | Where |
|---|---|---|
| `SearchResponse.quality: SearchQuality` | quality chip (`text_quality`), 17 mappings (BlueRay/Cam/DVD/HD/4K/…) | SRB:107-130 [verified] |
| `SearchResponse.score: Score` | rating box (`text_rating`, `score.toStringNull(0.1, 10, 1)`) | SRB:96-103 [verified] |
| `AnimeSearchResponse.dubStatus` + `episodes[DubStatus]` | DUB/SUB badges with episode counts (`text_is_dub`/`text_is_sub`, `"DUB · 12"` style) | SRB:270-303 [verified] |
| `LiveSearchResponse.lang` | flag emoji (`text_flag`) | SRB:244-252 [verified] |
| `SearchResponse.posterUrl` + `posterHeaders` | poster image (`loadImage(card.posterUrl, card.posterHeaders)`) | SRB:135-139 [verified] |
| `SearchResponse.name` | title overlay (`imageText`), toggleable | SRB:132-133 [verified] |
| `SearchResponse.year` | **NOT rendered on search cards** — `year` surfaces on the home *hero* preview (`homePreviewYear`, HPIP:364-367, that's `LoadResponse.year`) and on the result detail page (`ResultViewModel2` `yearText`) | [verified] |
| `AnimeSearchResponse.otherName` | **zero render sites in the app** (grep over `app/src/main` — no UI usage) | [verified absence] |

Dub/sub badges and rating are stacked with rounded-corner backgrounds (SRB:311-328 [verified]);
badge visibility is user-configurable (`show_sub_key`, `show_dub_key`, `show_hd_key`, … SRB:82-88 [verified]).
Separately, results are filtered by the user's quality blocklist before display
(`filterSearchResultByFilmQuality`, app `utils/AppContextUtils.kt:481-498` [verified]) and by active
dub status for anime (`SearchFragment.filterSearchResponse` — drops `AnimeSearchResponse` whose
`dubStatus` has no active entry, SF:91-102 [verified]).

---

## 4. quickSearch

### 4.1 Contract & gating

```kotlin
// @WorkerThread
open suspend fun quickSearch(query: String): List<SearchResponse>? {
    throw NotImplementedError()
}
```
`MA:655-658` [verified] — no pagination, plain list. Gated by:

```kotlin
open val hasQuickSearch = false
```
`MA:564` [verified]

The app-side wrapper:

```kotlin
suspend fun quickSearch(query: String): Resource<SearchResponseList> {
    if (query.isEmpty())
        return Resource.Success(newSearchResponseList(emptyList()))
    return safeApiCall {
        withTimeout(getTimeout(api.quickSearchTimeoutMs)) {
            newSearchResponseList(
                api.quickSearch(query) ?: throw ErrorLoadingException(),
                false
            )
        }
    }
}
```
`AR:136-148` [verified] — results are always wrapped `hasNext = false` (quickSearch cannot paginate,
by construction).

### 4.2 Where the app uses it — exactly one UI path

The **main search screen does NOT call quickSearch** — live-as-you-type search there is commented out:

```kotlin
override fun onQueryTextChange(newText: String): Boolean {
    //searchViewModel.quickSearch(newText)
    val showHistory = newText.isBlank()
    ...
```
`SF:441-442` [verified] — replaced by a web-suggestion service (`SearchSuggestionApi`, 300 ms debounce,
SVM:98-111 [verified]).

quickSearch is reachable **only through `QuickSearchFragment`**, and only when it was pushed with a
**single provider** that has quickSearch enabled:

```kotlin
val isSingleProvider = providers?.size == 1
val isSingleProviderQuickSearch = if (isSingleProvider) {
    getApiFromNameNull(providers?.first())?.hasQuickSearch ?: false
} else false
...
binding.quickSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
    override fun onQueryTextSubmit(query: String): Boolean {
        if (search(context, query, false))   // full search on submit
            hideKeyboard(binding.quickSearch)
        return true
    }

    override fun onQueryTextChange(newText: String): Boolean {
        if (isSingleProviderQuickSearch)
            search(context, newText, true)   // quickSearch on every keystroke
        return true
    }
})
```
`QSF:142-145, 253-265` [verified] — `search(context, query, isQuickSearch)` funnels into
`searchViewModel.searchAndCancel(..., isQuickSearch = isQuickSearch)` (QSF:123-135 [verified]),
which (§3.2) filters providers to `a.hasQuickSearch` and calls `a.quickSearch(query)` instead of
`a.search(query, 1)` (SVM:233-235 [verified]).

Who pushes `QuickSearchFragment` (`pushSearch(autoSearch, providers)`, QSF:59-86 [verified] — the
autoSearch string is trimmed of `(DUB)`/`(SUB)` suffixes, QSF:79-83):

| Caller | Purpose | Citation |
|---|---|---|
| `HomeViewModel.queryTextSubmit` | home screen search bar → quick-search **within the current provider only** (`repo?.name?.let { arrayOf(it) }`) | HVM:473-477 [verified] |
| `ResultFragmentPhone` (×2) & `ResultFragmentTv` | "search this title in other providers" button on the result page (`pushSearch(activity, d.title)` — no provider restriction) | ResultFragmentPhone.kt:490, 1019; ResultFragmentTv.kt:944 [verified] |
| `MainActivity` | external `search://` URI deep-link (comment: "It might be better to use the QuickSearch." — it navigates to the main search fragment instead) | MainActivity.kt:340-355 [verified] |
| Home preview search button | opens blank QuickSearch | HF:712-715 [verified] |

Inside `QuickSearchFragment`: single provider → `SearchAdapter` grid with row auto-expand against
`searchViewModel.expandAndReturn(firstProvider)` (QSF:148-178 [verified]); multiple providers → the
same `ParentItemAdapter` per-provider sections as the main search (QSF:186-211 [verified]). So
quickSearch results render through the exact same pipeline as `search` results — the only differences
are the entry method on the provider, no pagination, and no history entry (SVM:211-223 [verified]).

**Difference vs `search`, summarized**: (1) no `page`/`hasNext` — wrapped `hasNext = false` (AR:142-145);
(2) meant for keystroke-latency paths, providers opt in via `hasQuickSearch` (MA:564); (3) gated to a
single provider in practice (QSF:143-145); (4) skips search-history writes (SVM:211); (5) providers
implementing it usually delegate — `quickSearch(query) = search(query)` in Uakino/DoramyWorld (§5.3, §5.4).

---

## 5. Real provider patterns

Four implementations read in full (2 from storm-ext, 2 from CakesTwix-ext). The recurring theme:
**with no filter system, genre/category browsing is encoded as `mainPage` rows** (03 §2.3 pitfall;
`//TODO genre selection or smth` MA:420).

### 5.1 AllCalidadProvider (storm-ext) — genre rows as `"type:genreId"` tokens, JSON API, real hasNext

```kotlin
override val mainPage = mainPageOf(
    "movies" to "Películas",
    "tvshows" to "Series",
    "animes" to "Animes",
    "movies:26" to "Acción",
    "movies:51" to "Animación",
    ...
    "movies:182" to "Western",
)
```
`research/storm-ext/AllCalidadProvider/src/main/kotlin/com/stormunblessed/AllCalidadProvider.kt:42-61` [verified]

18 rows: 3 content-type rows + 15 genre rows, where `data` is `"postType:genreId"`. `getMainPage`
parses the token back out and drives pagination off the API's own page count:

```kotlin
override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val parts = request.data.split(":")
    val postType = parts[0]
    val genreId = parts.getOrNull(1)?.toIntOrNull()

    val url = "$apiUrl/listing?page=$page&post_type=$postType&posts_per_page=24" +
        (genreId?.let { "&genres=$it" } ?: "")

    val listing = runCatching { tryParseJson<ListingResponse>(app.get(url).text) }.getOrNull()
    val items = listing?.data?.posts?.mapNotNull { it.toSearchResult() } ?: emptyList()
    val lastPage = listing?.data?.pagination?.lastPage ?: 1

    return newHomePageResponse(
        list = HomePageList(name = request.name, list = items, isHorizontalImages = false),
        hasNext = page < lastPage
    )
}
```
`AllCalidadProvider.kt:63-84` [verified] — pattern: `page` goes straight into the URL, `hasNext =
page < lastPage` computed from API pagination metadata. Search is the legacy non-paginated overload
hitting `/api/search?query=...&page=1` (lines 86-92 [verified]) — so its search never paginates
(the paginated default wraps it with `hasNext = false`, MA:641-648).

### 5.2 DoramasFlixProvider (storm-ext) — manual multi-section response, request ignored

```kotlin
override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
    val items = ArrayList<HomePageList>()
    val doramasBody = "{...listDoramasMobile...}"
    val peliculasBody = "{...paginationMovie...}"
    val variedadesBody = "{...paginationDorama...}"
    val doraresponse = app.post(doraflixapi, requestBody = doramasBody.toRequestBody(mediaType)).parsed<MainDoramas>()
    val pelisrresponse = app.post(doraflixapi, requestBody = peliculasBody.toRequestBody(mediaType)).parsed<MainDoramas>()
    val variedadesresponse = app.post(doraflixapi, requestBody = variedadesBody.toRequestBody(mediaType)).parsed<MainDoramas>()
    ...
    items.add(HomePageList("Doramas", home1!!))
    items.add(HomePageList("Peliculas", home2!!))
    items.add(HomePageList("Doramas 2", home3!!))
    if (items.size <= 0) throw ErrorLoadingException()
    return newHomePageResponse(items)
}
```
`research/storm-ext/DoramasFlixProvider/src/main/kotlin/com/stormunblessed/DoramasFlixProvider.kt:128-154` [verified]

Pattern notes: this provider **ignores `request` entirely** and returns 3 hard-coded sections per
call — demonstrating that one `getMainPage` call may fill multiple `HomePageList`s (the app keys
them by name, HVM:345-357). It also declares no `mainPage` override, so the app calls it once per
*empty default row* (the `listOf(MainPageData("", "", false))` default, MA:630) — 3 near-identical
HTTP batches on first load, all rows named by the response, not by mainPage [inferred from MA:630 +
HVM:341-358]. Two more quirks: pagination is broken (`page` never used, `newHomePageResponse(items)`
defaults `hasNext = items.any { it.list.isNotEmpty() }` = always true → endless re-fetch of page 1 on
scroll [verified, MA:473-475]); and it sets `hasQuickSearch = true` (line 28) **without overriding
`quickSearch`** (grep: zero `quickSearch` in file) — if the user reaches the single-provider
quickSearch path, the default throws `NotImplementedError` (MA:656-658) and the app shows an error
resource. Same hazard documented for YoutubeProvider in 03 §2.7.

### 5.3 UakinoProvider (CakesTwix-ext) — URL-prefix rows, page appended, quickSearch = search

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

override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val document = app.get(request.data + page, headers = headers()).document
    val home =
        document
            .select("div.owl-item, div.movie-item")
            .filterNot { el ->
                val href = el.select("a.movie-title, a.full-movie").attr("href")
                val genre = el.select(".fi-label:contains(Жанр:) + .deck-value").text()
                href.contains(Regex(blackUrls)) ||
                        (request.name == "Серіали" && (genre.contains("Дорами") || genre.contains("Мультсеріали"))) ||
                        (request.name == "Мультфільми" && href.contains("/cartoonseries/"))
            }
            .map { it.toSearchResponse() }
    return newHomePageResponse(request.name, home)
}
```
`research/CakesTwix-ext/UakinoProvider/src/main/kotlin/com/lagradost/UakinoProvider.kt:26-76` [verified]

Pattern: `data` is a URL **prefix**, `page` is appended directly (`request.data + page`, line 60);
rows are disambiguated by *name-based client-side filtering* (`request.name == "Серіали"`) because
the site's URL prefixes overlap. Search is a DLE-engine POST + Jsoup:

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

    return document
        .select("div.movie-item.short-item")
        .filterNot { el ->
            el.select("a.movie-title, a.full-movie").attr("href").contains(Regex(blackUrls))
        }
        .map { it.toSearchResponse() }
}
```
`UakinoProvider.kt:97-118` [verified] — `quickSearch` is a trivial delegation (the common shape);
`Element.toSearchResponse()` (lines 78-84) reuses the same card parser as the home page, exactly the
code-sharing the tutorial recommends (`csdocs .../create-your-own-providers.md:74` [docs]).

### 5.4 DoramyWorldProvider (CakesTwix-ext) — same family, selector constants

```kotlin
override val mainPage = mainPageOf(
    "$mainUrl/film/page/" to "Фільми",
    "$mainUrl/dorama/page/" to "Дорами",
    "$mainUrl/show/page/" to "Розважальні шоу",
)

override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val document = app.get(request.data + page + "/").document
    val home = document.select(cardSelector).mapNotNull { it.toSearchResponse() }
    return newHomePageResponse(request.name, home)
}
```
`research/CakesTwix-ext/DoramyWorldProvider/src/main/kotlin/com/lagradost/DoramyWorldProvider.kt:42-73` [verified]
(with `override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)` at line 95 [verified]).

Identical shape to Uakino (URL-prefix + page-append) but with selectors hoisted into constants
(lines 48-51), making the provider ~a template of "one row per site category".

### 5.5 Pattern summary (for doc 05's "no filters" gap)

| Pattern | Example | `data` semantics | Pagination |
|---|---|---|---|
| Genre rows via token | AllCalidad | `"postType:genreId"` | `page` in URL, `hasNext = page < lastPage` |
| Hard-coded sections | DoramasFlix | ignored | broken (hasNext always true) |
| URL-prefix + page-append | Uakino, DoramyWorld | URL prefix | `request.data + page`, hasNext default |

Since there is no `getFilterList` analog (repo-wide grep, 03 §2.3 [verified]), **all genre/category
browsing in CS3 = many `mainPage` rows**. The official tutorial says it plainly: *"Getting the
homepage is essentially the same as getting search results but with a twist: you define the queries
in a variable"* and *"TLDR: Exactly like searching but you defined your own queries"*
(`csdocs/devs/create-your-own-providers.md:78, 141` [docs]), with the pagination rationale *"this
system is to allow 'infinite' loading"* (ibid. 138-139 [docs]).

---

## 6. ANI-KUTA mapping preview

Our side (read for this doc): `feature/anime-browse/impl/` (BrowseViewModel.kt, BrowseCards.kt),
`feature/anime-search/impl/` (SearchViewModel.kt, ExtensionSourcePickerSheet.kt, FilterSheet.kt),
`core/provider-api/` (VideoExtensionProvider.kt, Source.kt).

### Home / browse

- **CS3**: one active provider, its `mainPage` rows → `ExpandableHomepageList` map, per-row infinite
  scroll via `page`+`hasNext`, no home cache, hero = extra `load()` calls (HVM:341-416, HPIA:130-159).
- **Ours**: `BrowseViewModel` serves **AniList-only** fixed sections — Trending / Popular / Top Rated
  (`SECTION_TRENDING/POPULAR/TOP_RATED`, BrowseViewModel.kt:45-49 [verified]), each cached 6 h
  cache-first (loadSection, BrowseViewModel.kt:108-134 [verified]), hero = first 5 trending items
  with banners (BrowseViewModel.kt:73-82 [verified]), rendered as `BrowseSectionHeader` + `LazyRow`
  carousels (BrowseCards.kt:64-93 [verified]). No pagination — one fetch per section; no extension
  sources on Browse at all.
- Mapping: CS3's `HomePageList(name, list, isHorizontalImages)` ≈ our (section title + card list);
  CS3's `hasNext` per row has **no equivalent** — our sections are single-shot.
- `[gap]` our Browse has no extension-sourced sections and no per-section pagination; adopting CS3
  providers means either mapping each `MainPageData` row → one section (natural fit) and deciding
  where per-row `hasNext`/page-increment state lives (our `StateFlow` UI vs their LiveData map).

### Search

- **CS3**: parallel fan-out over all enabled providers via `amap` (SVM:231-244), live per-provider
  sections + round-robin merged flat list (SVM:172-196), per-provider load-more (SVM:124-170),
  submit-triggered (no debounce), generation+job cancellation (SVM:74-83).
- **Ours**: `SearchViewModel` has two modes — ANILIST (default) and EXTENSION — and in EXTENSION mode
  searches **exactly one selected source**: `source.getSearchAnime(1, q, AnimeFilterList())`
  (SearchViewModel.kt:585-587 [verified]), selected via `ExtensionSourcePickerSheet` (ModalBottomSheet
  listing trusted `AnimeCatalogueSource`s, ExtensionSourcePickerSheet.kt:54-89 [verified]), persisted
  (`KEY_SELECTED_SOURCE_ID`, SearchViewModel.kt:316-324 [verified]). Query flow is debounced 350 ms
  (`DEBOUNCE_MS`, SearchViewModel.kt:58, 326-345 [verified]) with generation-based superseding +
  Job cancel (D-305, `beginRequest`, SearchViewModel.kt:101-113 [verified]) — the same two-layer
  staleness pattern CS3 uses, arrived at independently.
- Differences worth recording: we dedup by URL per source (D-304, SearchViewModel.kt:588-593 [verified])
  where CS3 dedups only on search-pagination; we have a `FilterSheet` + aniyomi `AnimeFilterList`
  (SearchViewModel.kt:13, 586 [verified]) where CS3 has **no filters at all**; CS3 merges
  multi-provider results (round-robin), we show single-source grids.
- `[gap]` for CS3 support: our search UI has no multi-source merge/interleave; if we keep
  single-source semantics, CS3's `search(query, page)` maps cleanly onto
  `getSearchAnime(page, query)`-style calls, and their `hasNext` → our "has more" state.

### Provider API surface

- **CS3**: `fetchContent`-equivalent returns `HomePageResponse` with **named sections + hasNext**
  (MA:1270-1286); search returns `SearchResponseList(items, hasNext)` (MA:1292-1297).
- **Ours**: `VideoExtensionProvider.fetchContentList(source, page, query): Flow<List<SourceContent>>`
  — flat list, no section names, **no hasNext signal** (provider-api `VideoExtensionProvider.kt`
  [verified]); pagination inferred from empty page.
- `[gap]` flat `Flow<List<SourceContent>>` cannot express CS3 named-sections + hasNext; wrapping CS3
  providers will need either (a) a new sectioned API for the CS3 ecosystem, (b) flattening each
  `HomePageList` into a synthetic "section = query" per `MainPageData` (page pairs), or (c) exposing
  one section per `fetchContentList` call keyed by `MainPageData.data` as the `query` parameter
  (closest to CS3's own model — `data` already *is* an opaque query token).
- `[gap]` no quickSearch analog in provider-api; CS3 `hasQuickSearch` gating would be lost (probably
  acceptable — see §4: even CS3 only uses it on one screen).

---

## 7. Could not verify / surprises

### 7.1 Surprises

1. **The home screen is single-provider.** Despite `mainPage` being per-provider and dozens of
   providers installed, only ONE provider's rows ever show (persisted `currentHomePage`; None/Random
   pseudo-providers) — no cross-provider home aggregation exists anywhere in the app (HVM:122-137,
   HF:383-552 [verified]). Random mode re-picks a provider per load (HVM:524-534).
2. **quickSearch is nearly dead UI.** The main search screen's live quickSearch call is commented
   out (SF:442) — replaced by a *web-wide* suggestion API with 300 ms debounce (SVM:98-111). The only
   live path is QuickSearchFragment *restricted to a single provider with `hasQuickSearch`*
   (QSF:143-145, 260-263), reachable from the home search bar, the result page "search elsewhere"
   button, and deep links (§4.2).
3. **A real dedup bug in home pagination**: `this.list.list.distinctBy { it.url }` discards its
   result (HVM:261) — the comment says "just to be sure", but it is a no-op; duplicates accumulate
   until the adapter diff or a reload hides them. The search-side twin assigns the result
   (SVM:150). Worth an ANI-KUTA regression-test note.
4. **Row auto-expand is scroll-*ability*-based, not scroll-position-based**: `!recyclerView.isRecyclerScrollable()`
   — if a page returns fewer items than fill the row, the app immediately fetches the next page
   (HPIA:154, HF:245, QSF:171 [verified]); a provider whose `hasNext` never turns false becomes an
   infinite fetch loop (DoramasFlix §5.2 is exactly this).
5. **No cache anywhere on discovery paths** — neither home rows nor search results are cached
   (only `load()` gets the 20-slot/10-min rolling cache, AR:52-121 [verified]); every provider
   switch / re-search refetches everything.
6. **The home hero does hidden `load()` calls**: 3 extra full-detail fetches on first load + 1 per
   swipe, over shuffled flattened home items (HVM:285-317, HPIP:479-494 [verified]) — a latency and
   rate-limit cost invisible in the provider contract.
7. **Search fan-out is all-or-nothing concurrent** — no per-provider stagger, no failure isolation
   beyond `safeApiCall`, and `bundleSearch` has **no cross-provider dedup** (SVM:172-196): the same
   show from 10 providers = 10 adjacent-ish cards (round-robin interleaves them near the top).
8. **Provider order in merged search = completion order** (results are inserted into
   `expandableSearches` as they arrive, SVM:239), so the merge order is a network race — not
   registration order, not pinned order (pinning only sorts the *sectioned* view, SF:500-505).

### 7.2 Could not verify / left open

- **Why `onQueryTextChange` quickSearch was disabled** (SF:442) — no comment, no commit history in
  the snapshot; upstream likely found per-keystroke fan-out too heavy [inferred].
- **`amap` concurrency limits** — `async` per provider with no semaphore; with 50+ installed
  providers a blank search fires 50+ concurrent requests (only providers *enabled in settings* and
  matching chips are included, SF:182-194, which mitigates it) [inferred].
- **`ignoreSettings` parameter of `searchAndCancel`** has no caller passing `true` in the app UI
  (SVM:74-83; all call sites pass `false` or default — SF:190-195, QSF:126-131 [verified]) — appears
  to exist for tests/tools [inferred].
- **DoramasFlix's triple-fetch-on-empty-mainPage behavior** (§5.2) is inferred from the code path
  (default `mainPage` = 1 empty entry → one call per entry), not observed at runtime.
- **TV-specific home behaviors** (channel publishing, focus handling) were read at code level only
  (HF:603-660, 978-1016 [verified]); no emulator verification was done.
- Cross-check with doc 03 §2.3-2.7 and doc 05 §8 — the *contract-side* facts there were re-verified
  against the same source lines for this doc; no contradictions found.

---
## ✔ B5-a Verification Note (2026-08-29)
Checked: 34 claims sampled → 34 verified, 0 corrected, 0 flagged-stale.
Corrections: none.
Confirmed (incl. all high-value targets): **home = single-provider with expandable rows** (HomeViewModel holds one `repo`, rows keyed by name in `ExpandableHomepageList` map, `currentHomePage` persisted, None/Random pseudo-providers — HVM:122/221-231/341-358/501-551 all re-read); **search = parallel fan-out + round-robin merge** (`repos.amap` per provider, `bundleSearch` interleave "to move the relevant search results to the top" — SVM:52/198-253/172-196, amap impl at PC:36-40; merge order = completion order; no cross-provider dedup); **quickSearch commented out at SF:442** (`//searchViewModel.quickSearch(newText)` — exact line confirmed; replaced by SearchSuggestionApi with 300 ms debounce at SVM:98-111; only live path = QuickSearchFragment gated to single provider with `hasQuickSearch` at QSF:142-145). Also confirmed: timeout clamp [5s, 8min] (AR:28-64); `APIRepository.getMainPage` 3 modes quote verbatim (AR:156-196); no cache on discovery paths; the HVM:261 home-dedup no-op bug is genuine (`this.list.list.distinctBy { it.url }` result discarded, search twin at SVM:148-150 assigns it); scroll-*ability*-based row auto-expand (HPIA); `filterProviderByPreferredMedia` exact predicate (AppContextUtils.kt:447-479); search 3-stage filter chain + advanced_search dual rendering; all four provider pattern examples re-read (AllCalidad `"postType:genreId"` tokens :42+/:63+; DoramasFlix 3 hard-coded GraphQL sections + hasQuickSearch-without-override at :28/:128; Uakino URL-prefix rows + `quickSearch = search` at :26/:97; DoramyWorld same family); ANI-KUTA-side citations spot-checked (DEBOUNCE_MS=350 at SearchViewModel.kt:58, SECTION_TRENDING/POPULAR/TOP_RATED at BrowseViewModel.kt:47-49, `fetchContentList` Flow signature in provider-api).
