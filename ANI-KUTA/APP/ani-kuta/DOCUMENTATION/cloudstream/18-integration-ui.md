# 18 — The Cloud Screen: UI/UX Plan (CS3 content in ANI-KUTA's shell)

> **Mission (B4-c)**: the UI/UX design for the "Cloud Screen" — the user-facing surface for
> CloudStream (CS3) extension content (movies, TV series, Asian dramas, live/other video) inside
> ANI-KUTA's Compose shell. Where CS3 content lives in the nav, how Cloud Browse/Search/Details/
> Watch look, how library & extension management evolve, and the phased UI build order.
>
> **Ground truth inputs**: doc 16 (architecture — §5.4's "separate Cloud Screen v1" is the working
> IA assumption; §4.3's `SourceSection`/`SourcePage` is the browse data contract), doc 06 (CS3
> home/search UX), doc 07 (CS3 details UX), doc 10 (categorization/filter model), doc 12 (provider
> quality spectrum + §10 "what the Cloud Screen must support"), doc 11 (plugin settings), doc 04
> (repo/plugins.json fields), doc 05 (models), doc 17 (data layer — §7 favorites is normative),
> doc 19 (playback — §3.3 owns the link-picker mechanics; this doc owns the sheet's UX shape and
> stays consistent). Our side: `MainActivity.kt` nav shell, `BrowseScreen.kt`/`BrowseCards.kt`,
> `SearchScreen.kt`/`ExtensionSourcePickerSheet.kt`, `DetailsScreen.kt` (structure),
> `LibraryScreen.kt`, `SettingsScreen.kt`, `MoreScreen.kt`, `SharedTransitionLocals.kt`,
> `Motion.kt`, and `DOCUMENTATION/DESIGN-SYSTEM/` (README + 01–04).
>
> **Markers**: **[recommendation]** = chosen option · **[design]** = proposed UI/structure (sketches
> are PLANS, not code) · **[open-question]** = needs the user · doc citations `doc N §…` = the
> numbered CloudStream research/integration docs in this folder; our-code citations `File.kt:lines`.
> **The IA decision (§1) and the v1 scope (§10) are working assumptions — the user has NOT decided
> doc 16 §11-1 yet; every section notes its fallback if "unified" wins later.**

---

## Table of contents

1. [IA decision — where CS3 content lives](#1-ia-decision)
2. [Cloud Browse (home)](#2-cloud-browse)
3. [Cloud Search](#3-cloud-search)
4. [Cloud Details](#4-cloud-details)
5. [Cloud Watch flow](#5-cloud-watch)
6. [Library & favorites for Cloud content](#6-library--favorites)
7. [Extensions screen evolution](#7-extensions-screen)
8. [Settings](#8-settings)
9. [Design-language compliance](#9-design-language)
10. [Phased UI rollout](#10-phased-rollout)
11. [Open questions for the user](#11-open-questions)
12. [Verification status](#12-verification)

---

## 1. IA decision — where CS3 content lives in our nav

Doc 16 §5.4 (the registry-first path) recommends the **Cloud Screen as a separate flow**: CS3
content is movies/series/dramas whose browse model is sectioned shelves (`mainPage` rows, doc 03
§2.3), not our anime-shaped Browse; unified search would force the doc 16 §5.3 step-6 migration
*and* AniList-vs-CS3 result mixing our search screen deliberately avoids (doc 14 §7.1 "alternative
modes, never merged"). That is this doc's working assumption; the user hasn't confirmed (doc 16
§11-1 `[open-question]`, restated in §11 below).

### 1.1 Options

| | **A. 5th bottom-nav tab "Cloud"** | **B. Section inside Browse** | **C. More-screen entry** |
|---|---|---|---|
| What | new `NavItem` beside Browse/Library (Search/More stay) — a top-level world | a "Cloud" header entry/chip inside `BrowseScreen` that swaps sections | a `MoreListRow` in More → full-screen Cloud flow |
| Nav-shell cost | `navItems` (MainActivity.kt:372-378) + `rootTabKeys` (MainActivity.kt:304-309) + `startTab` sanitize (MainActivity.kt:386-388, "cloud" becomes a valid restore) + `allowedUpdateSheetKeys` (MainActivity.kt:325-352) | none (Browse already a tab) | one row + one key |
| Anime-flow risk | zero (new screen; `BrowseViewModel` untouched) | **high** — Browse is AniList-only by construction (`BrowseViewModel.kt:40-43`, doc 14 §7.2); mixing means either two ViewModels in one screen or a mode toggle exactly like Search's dual-mode, the pattern doc 14 §7.1 explicitly scoped to search | zero |
| Daily-use cost | one tap from anywhere (bottom bar is always up on root tabs, MainActivity.kt:1017-1019) | fine | two taps + scroll — Cloud becomes a settings-ish destination, not a surface |
| Fit with doc 16 §5.4 | exact match ("own tab/browse surface") | partial (would be step-6-flavored mixing) | partial (own flow, weakest placement) |
| Bottom bar fit | 4→5 pills; our bar is a floating 28dp pill with labels **only on the active item** (DESIGN-SYSTEM 01 §1), so a 5th icon-only pill does not crowd the label row | n/a | n/a |

### 1.2 `[recommendation]` — Option A, dynamically shown

**A 5th "Cloud" tab that appears only when ≥1 CS3 provider is installed+enabled** (empty otherwise:
the tab hides and the feature is reachable from Settings → Extensions until a provider exists —
so the bar stays 4 pills for users who never adopt Cloud).

- Rationale: doc 16 §5.4's own framing ("own tab/browse surface, own search-over-providers");
  zero blast radius on anime flows (the Cloud Screen consumes `ExtensionProviderRegistry` as a new
  `:feature:cloud-screen` module depending on `:core:provider-api` ONLY — doc 16 §2.2/§5.3 step 4);
  the bottom bar is the cheapest cross-world movement there is; and the dynamic pill keeps the
  "anime-first app" identity for non-adopters. `navItems` becomes a derived list over the registry
  state (small, contained change in `AppRoot`, MainActivity.kt:372-389).
- The Cloud tab's cold-start restore joins Browse/Library in the D-282 sanitize set
  (MainActivity.kt:386-388) — restoring onto Cloud is fine *only if* the tab is currently shown.

**How a user moves between worlds** (v1):

1. **Bottom bar** — the only explicit anime⇄cloud switch. No cross-linking inside screens:
   cloud details pages never link into anime details and vice versa (no auto-linking for CS3
   content — the Track sheet and AniList link actions are hidden, doc 17 §5.4; §4.2 below).
2. **Shared surfaces** — Library (§6), Downloads (our `DownloadOrchestrator` queue is shared, doc
   19 §6), and History are ecosystem-agnostic and will show cloud entries **mixed with anime**;
   each cloud entry carries a small provider badge ("Cloud · Uakino") so the two worlds stay
   legible inside shared lists `[design]`.
3. v2 idea (not v1): a "Search in Cloud" action on an anime details page (title → cloud search)
   — deferred `[open-question]` Q8.

**Fallback if the user later chooses unified**: doc 16 §5.3 step 6 (search-picker pref
Long→sourceKey, merged results) becomes the plan of record; UI-wise the Cloud Browse rows become a
"Cloud" section group inside `BrowseScreen`'s LazyColumn and Cloud Search becomes a third
`SearchSource` mode. To keep that door cheap, §2–§4 below design every Cloud composable as a
**plain, nav-agnostic component** (section rows, card grids, details sections) — placement is a
wrapper decision, not a rewrite `[design]`.

---

## 2. Cloud Browse (home)

### 2.1 The CS3 model we are rendering (facts)

- One provider at a time: CS3's home shows exactly ONE provider's rows (persisted
  `currentHomePage`; no cross-provider interleave — doc 06 §2.4, surprise #1). Rows come from the
  provider's `mainPage` list of `MainPageData(name, data, horizontalImages)` (doc 06 §1.1).
- Each row paginates with `page`/`hasNext` (doc 06 §1.2); CS3 fetches **all page-1 rows at once**
  (concurrently, or serially when `sequentialMainPage = true` — an anti-rate-limit knob, doc 06
  §1.4/§2.1) and then per-row on scroll (`ExpandableHomepageList`, doc 06 §2.2).
- Genre browsing = rows: with no filter API (`//TODO genre selection or smth`, doc 10 §6), every
  genre/category a provider exposes is a `mainPage` row (doc 12 §9.5 — 0–18 rows, median ~4;
  AllCalidad's 18 = 3 content types + 15 genres).
- `hasNext` lies (2/6 deep-dives — doc 12 §9.3 #1) and CS3's own home-row dedup is a no-op bug
  (doc 06 §7.1 #3). Our UI must dedupe by URL and cap runaway pagination (doc 12 §10).
- There is **no discovery cache** in CS3 (doc 06 §7.1 #5) — every provider switch refetches
  everything; doc 17 §9.1 optionally adds a `cs3_result_cache` for our SQL-first browse (v1
  optional — treat as memory-only in the UI plan).
- Provider-level gating: `hasMainPage` must be true to appear in the picker (doc 06 §1.3).

### 2.2 `[design]` CloudBrowseScreen

Structure reuses our Browse skeleton wholesale (CollapsingHeader → PullToRefreshBox → LazyColumn
of section header + carousel items, BrowseScreen.kt:135-256; sections fade+expand in, never pop —
`BrowseSection`, BrowseScreen.kt:309-317):

```
┌──────────────────────────────────────────────────────────────┐
│  Cloud                                    [🔍]  ⛅ Uakino ▾   │ ← CollapsingHeader ("Cloud");
│                                                              │   provider chip + search icon
│  ┌─ TvType chips: (Movies)(Series)(Anime)(Dramas)(Cartoons)… │ ← scrollable chip row, doc 10 §8.3
│                                                              │   grouping map; hidden if 1 provider
│  ┌────────────────────────────────────────────────────────┐  │
│  │ Фільми  ─────────────────────────────────────────── ▸ │  │ ← BrowseSectionHeader + "see all"
│  │ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌ ⟳ ┐              │  │   (BrowseCards.kt:64-75 pattern)
│  │ │2:3 │ │2:3 │ │2:3 │ │2:3 │ │2:3 │ │more│             │  │
│  │ │card│ │card│ │card│ │card│ │card│ │    │             │  │   portrait cards = BrowseAnimeCard
│  │ └────┘ └────┘ └────┘ └────┘ └────┘ └────┘             │  │   language (2:3, 12dp, 1dp border,
│  │  Title Title Title …                                   │  │   amber score tag, press 0.95)
│  └────────────────────────────────────────────────────────┘  │
│  ┌ Серіали ──────────────────────────────────────────── ▸ ┐  │
│  │ … (row renders only when scrolled near — lazy load) …  │  │
│  └─────────────────────────────────────────────────────────┘  │
│  ┌ Дорами (16:9 horizontal row — horizontalImages=true) ── ▸┐  │ ← 16:9 variant card
│  │ ┌────────┐ ┌────────┐ ┌────────┐                         │  │
│  └─────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

Decisions, each mapped to a fact:

1. **Provider picker chip in the header** (`⛅ Uakino ▾`) opens a `CloudProviderPickerSheet` —
   our `ExtensionSourcePickerSheet` pattern (ModalBottomSheet, `dragHandle = null`,
   `skipPartiallyExpanded = true`, 70% height cap, 20sp ExtraBold title —
   ExtensionSourcePickerSheet.kt:54-89; DESIGN-SYSTEM 01 §4). Contents, per doc 10 §2.3's
   insight (provider picker + category chips are one mental operation): the same TvType chip row
   lives *inside* the sheet and live-filters the provider list (language ∩ `hasMainPage` ∩
   type-intersection — the `filterProviderByPreferredMedia` shape, doc 10 §2.6/§8.15). Selected
   provider persists (our `PreferenceStore`, key `cloud_home_provider` — CS3's `currentHomePage`
   analog, doc 06 §2.2). **No "None"/"Random" pseudo-providers v1** (CS3 has them, doc 06 §2.2 —
   low value for us, extra states) `[design]`.
2. **Rows render lazily as they approach the viewport** — `LazyColumn` items keyed by section,
   each row's page-1 fetch fired on first composition (LaunchedEffect), NOT CS3's fetch-all-
   concurrently batch (doc 06 §2.1 mode 3 fires N simultaneous requests; with 18-row providers
   like AllCalidad that's 18 hits at once). Honor `sequentialMainPage`/delay knobs by serializing
   row loads for providers that declare them (the bridge reads the flags — doc 06 §1.4)
   `[design]`. Per-row state is a sealed `CloudRowState { Loading, Loaded(items, hasNext, page),
   Error(reason), Empty }` — a row's error NEVER blanks the screen (contrast CS3's single
   connection-error home state, doc 06 §2.1/HF:875-916); the row header shows a compact inline
   retry pill instead `[design]`.
3. **Per-row infinite scroll** via a trailing sentinel item in each `LazyRow` (end-cap shows a
   spinner while fetching page+1, or a "▸" see-all tile); append dedupes by URL (the D-304 analog;
   fixes CS3's no-op dedup bug, doc 06 §7.1 #3) and **caps at 3 consecutive pages that return
   zero new items** — the always-`true` `hasNext` liars (doc 12 §9.3 #1) degrade to "no more
   content" instead of looping `[design]` (doc 12 §10 bullet 2).
4. **See-all ▸** opens a full-screen `CloudSectionGridKey` (3-col poster grid, infinite scroll,
   same pagination state machine) — CS3's see-all bottom sheet (doc 06 §2.3) adapted to our nav
   model (full sub-screen, no bottom bar — matches how every non-root key behaves,
   MainActivity.kt:300-309) `[design]`.
5. **Horizontal rows** (`horizontalImages = true`, doc 06 §1.1) render 16:9 landscape cards —
   our `BrowseHero` is the 16:9 precedent (BrowseScreen.kt:184-192); used for channel/live tiles
   (doc 12 §9.4).
6. **Pull-to-refresh** = force reload of the current provider (all rows, page 1) — the existing
   `PullToRefreshBox` wiring (BrowseScreen.kt:139-144). No cache in v1 → refresh is the only
   staleness escape (doc 06 §7.1 #5 fact, accepted).
7. **Skeletons**: `BrowseSkeleton`-style shimmer (BrowseScreen.kt:146) for the screen-level first
   load only; rows skeleton individually as they scroll in (3 shimmer cards per row).
8. **NSFW gating UI**: NSFW-only providers are excluded from the picker sheet unless the master
   toggle is ON (§8); the picker shows a lock-row "N NSFW providers hidden — enable in Settings"
   when hidden ones exist `[design]`. Items *inside* mixed-type providers get a subtle "18+"
   corner badge instead of filtering (cheap, honest) `[open-question]` Q5.
9. **Empty states**: no providers installed → onboarding EmptyState ("Install a Cloud extension
   to browse movies, series & dramas" + button → Extensions screen, cloud section §7); provider
   with zero rows → EmptyState + Retry (the `BrowseErrorState` pattern, BrowseScreen.kt:322-362).

### 2.3 Compose sketch (structure only)

```kotlin
// [design sketch] — placement-agnostic composables (§1.2 fallback requirement)
@Composable fun CloudBrowseContent(registry: ExtensionProviderRegistry, vm: CloudBrowseViewModel) {
    // CollapsingHeader("Cloud") + provider chip + search icon
    // PullToRefreshBox { LazyColumn {
    //   item { CloudTypeChipRow(vm.selectedTypes, vm.availableTypes) }      // doc 10 §8.3 groups
    //   items(vm.sections, key = { it.key }) { row ->                       // SourceSection (doc 16 §4.3)
    //     CloudSectionRow(row) // header + LazyRow cards + end-cap sentinel
    //   }
    // } }
}
```

---

## 3. Cloud Search

### 3.1 The CS3 facts

- Search fans out **in parallel over all enabled providers** (`amap`, one async per provider —
  doc 06 §3.2), results stream in per-provider as they land (live incremental UI, doc 06 §3.2),
  and render in two modes: sectioned-per-provider (CS3's `advanced_search`, default) or a merged
  flat grid built by **round-robin interleave** (doc 06 §3.3). **No cross-provider dedup** — the
  same title from 5 providers appears 5 times (doc 06 §7.1 #7); within-provider dedup happens on
  load-more only (doc 06 §3.3).
- Provider set is filtered by: enabled-providers setting, user's preferred media types, and
  active TvType chips — with a **graceful fallback**: if chips would empty the set, chips are
  ignored (doc 06 §3.3, doc 10 §8.4).
- Badges on result cards (doc 06 §3.4, the `SearchResultBuilder` table): quality chip
  (`SearchQuality` — 17 mappings), score box, DUB/SUB badges with episode counts
  (anime responses), flag emoji (live responses), poster via `posterHeaders`. `year` is NOT
  rendered on CS3 search cards (doc 06 §3.4) — but see below, we render it.
- quickSearch is nearly dead upstream (one single-provider path — doc 06 §4/§7.1 #2); we skip it.

### 3.2 `[design]` CloudSearchScreen

Entry: the 🔍 icon in the Cloud browse header pushes `CloudSearchKey` (a sub-screen; our Search
tab stays anime-only — **v1: anime search NEVER includes CS3 results**, per doc 14 §7.1's
never-merged principle + doc 16 §5.3 step 6 deferral `[recommendation]`).

```
┌──────────────────────────────────────────────────────────────┐
│  ← Cloud search                                              │
│  ┌───────────────────────────────────┐ ┌──[ Providers (3) ]┐│ ← subset picker chip
│  │ 🔍 query…                         │ └──────────────────┘│
│  ┌ (Movies)(Series)(Dramas)(Anime)… chips, persisted ──────┐│
│  ┌────────────────────────────────────────────────────────┐ │
│  │ Uakino ─────────────────────────────────── 12 results ▸│ │ ← per-provider section,
│  │ ┌────┐ ┌────┐ ┌────┐   cards carry: provider badge      │ │   live-fill as results
│  │ │2:3 │ │2:3 │ │2:3 │   (only when >1 provider), quality │ │   stream in (doc 06 §3.2)
│  │ └────┘ └────┘ └────┘   chip, score, year, DUB/SUB, 18+  │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌ DoramyWorld ────────────────────────────── 5 results ▸ ┐ │
│  │ …(skeleton rows while pending; per-provider retry)…    │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

1. **Fan-out scope**: search ALL enabled CS3 providers by default (doc 06 §3.2's model) with
   per-provider sections (the `advanced_search` default rendering, doc 06 §3.3); a **"Providers"
   filter chip** opens a multi-select subset sheet (CS3's `searchPreferenceProviders` analog,
   doc 10 §2.6) `[design]`. TvType chips filter which providers participate, with the empty-set
   fallback (ignore chips) copied verbatim (doc 06 §3.3) `[recommendation]`.
2. **Sectioned, not merged, in v1** — the round-robin merge (doc 06 §3.3) adds a dedup UX
   question we'd rather not answer v1 (see 5). Sections sort by completion order (live), matching
   CS3's behavior (doc 06 §7.1 #8); a small "pin to top" long-press action re-orders sections
   (pinned-first, CS3's pinnedProviders pattern doc 10 §2.3) `[design]`.
3. **Dedup UX = none (visible duplicates, by design)** — CS3 doesn't dedup cross-provider (doc 06
   §7.1 #7) and titles/years are too dirty to dedup on client-side (doc 12 §9.3 #4). Instead each
   card carries the **provider name as a pill badge** (shown only when >1 provider searched) so
   duplicates read as "same title, different source" `[design]` + `[open-question]` Q7 (try a
   soft "grouped by title" v2?).
4. **Badges** (our card = `ExtensionResultCard` pattern, SearchScreen.kt:638-707, extended):
   quality chip + score (amber pointed tag, BrowseCards.kt:174-195 language) + **year** (unlike
   CS3 — we have the field via doc 05 §2.1 and it disambiguates duplicates) + DUB/SUB counts for
   anime responses + 18+ badge + TvType mini-badge (Movie/Series, from `SearchResponse.type?`,
   doc 10 §1.3). All optional; card must render correctly with only title+poster (doc 12 §10).
5. **Search mechanics**: submit-triggered (our 350ms debounce stays for the query text,
   SearchViewModel.kt:326-345 / `DEBOUNCE_MS` — doc 06 §6's verified map); two-layer staleness =
   job cancel + request generation (D-305 — SearchViewModel.kt:101-113 precedent; CS3 arrived at
   the same pattern, doc 06 §3.2).
   Per-provider failures show as a one-line section error row ("DoramyWorld failed — Retry"),
   never abort the fan-out `[design]` (CS3 has no per-provider failure isolation — doc 06 §7.1
   #7 — we do better).
6. **Cloudflare** (D-209/D-210 precedent): if a provider's search fails with a CF-shaped error,
   the section error row offers "Open in WebView" (SearchScreen.kt:128-141 pattern, launches
   `CloudflareWebViewActivity`).
7. Result tap → `CloudDetailsKey(sourceKey, externalUrl, title, posterUrl, posterHeaders,
   transitionKey)` — nav key carries the card snapshot + shared-element key (§4.2, the
   `AnimeDetailsKey.transitionKey` precedent, MainActivity.kt:745-758).

---

## 4. Cloud Details

### 4.1 What we're rendering (facts)

CS3 fuses details+episodes in one `load()` (doc 03 §2.8; doc 16 §4.2) and the details page renders
from `LoadResponse`: poster + backgroundPoster fallbacks, optional logo (replaces the text title
when it loads), plot (expandable), tags→chips, score/year/duration/type/apiName/contentRating
meta row, `comingSoon` banner (hides everything else), actors row **only when actor images exist**
(else "Cast: …" text), nextAiring live countdown, showStatus, VPN badge, meta-provider badge, and
"no episodes found" for empty lists (doc 07 §2.1 — the full field table). Seasons = flat episode
list + `SeasonData` overlay + a season picker; anime adds a dub/sub picker; ~30-episode range
chunking exists for huge lists (doc 07 §2.3). Episode rows: "N. Title", filler badge, watch-state
check/progress bar, 16:9 still, score, expandable description, upcoming-date handling, download
button (doc 07 §2.4). **Images**: `posterHeaders` must ride every poster/background/logo load
(doc 07 §3.3) — and CS3's own episode-thumbs/actor-portrait loads DON'T pass headers (an upstream
blind spot; doc 07 §3.3 table) — we thread headers everywhere (doc 07 §3.5's ANI-KUTA note).
Recommendations = plain search cards, possibly cross-provider, with a provider filter (doc 07
§6.1). Trailers resolve through the extractor pipeline in-app (doc 07 §6.2) — **deferred v1**
(doc 19 §9 skips; a YouTube link chip can open externally instead) `[recommendation]`.

Our DetailsScreen is AniList-driven: `DetailBanner` (blurred banner + gradient + cover + title +
meta + action row, DetailsScreen.kt:1409-1732), `GenresRow` (1765), `SynopsisSection` (1796),
`EpisodesSection` with D-308 season selector + D-228 flattened virtualized rows (1862+, 803-854),
`EpisodeRow` with swipe-to-watched + thumbnail + EP tag + date/audio pills + download (2793+),
`ResolverSheet` (1121), ManualLinkSheet (1218), category picker (1230) — doc 14 §7.3.

### 4.2 `[design]` CloudDetailsScreen — a VARIANT of our details, sharing the layout language

**Shared with our DetailsScreen** (the layout language, not the ViewModel):

- `DetailBanner` visual: blurred backdrop + gradient + cover + title + meta row + circular action
  buttons (DetailsScreen.kt:1485-1644 pattern; DESIGN-SYSTEM 04 §4) — Cloud variant swaps the
  backdrop source to `backgroundPosterUrl ?: posterUrl` (doc 07 §2.1 fallback) and passes
  `posterHeaders` into every `ImageRequest` (Coil `httpHeaders(NetworkHeaders...)` — doc 07 §3.5's
  1:1 mapping).
- **Shared-element cover morph**: the banner cover uses `coverSharedElement(transitionKey)` with
  the key the *source card* built (the `AnimeDetailsKey.transitionKey` mechanism,
  DetailsScreen.kt:753-755) — Cloud cards build `cloudCoverKey(...)` keys (§9.2).
- `GenresRow` for `tags` (plain pills; tags are NOT our AniList genre vocabulary — doc 17 §5.1
  keeps them ext-axis-only).
- `SynopsisSection` (plot, expandable).
- **Episode list components**: `EpisodesSection` header row + `EpisodeRow` shape (thumbnail +
  "EP n" tag + title + pills + watch-progress overlay + download button — DetailsScreen.kt:2793+)
  reused with a `CloudEpisode` model. Season selector = the D-308 `SeasonSelectorRow` chips
  (DetailsScreen.kt:2560+) driven by CS3 `SeasonData`/`season` ints instead of name-sniffed
  groups.
- Bottom sheets: download picker / link picker / category picker follow §5 + §6.

**Different from our DetailsScreen** (cloud specifics):

1. **No AniList actions**: Track button, "Link AniList"/ManualLinkSheet, auto-link popup, and the
   D-134 data-source selector are all **hidden** (no tracker row by construction, doc 17 §5.4;
   auto-linking is anime-flow machinery, doc 14 §7.3). The action row becomes: Back ·
   Bookmark (library §6) · More (refresh / "search in cloud" / share url). `syncData` ids are
   stored (doc 17 §5.1) as the *future* hook for linking — no UI v1.
2. **Provider metadata row** replaces AniList meta: `score · year · duration · contentRating ·
   TvType-label · providerName` (doc 07 §2.1) — each segment renders only when present (the
   width-0-when-null trick CS3 uses for contentRating, doc 07 §2.1). Provider name doubles as
   the "which world am I in" cue.
3. **Actors row** (when images exist): horizontal round portraits + name + role, our card
   press-scale language; no images → single-line "Cast: A, B, C" text (doc 07 §2.1/§2.2); hidden
   entirely behind an appearance pref (§8). No voice-actor flip v1 (anime-specific).
4. **nextAiring countdown** ("Next episode in 2d 4h") as a small highlighted card under the
   banner when present (doc 07 §2.1; 0/58 providers in the census — doc 12 §9.2 — so it must
   never reserve layout space) + `comingSoon` banner replaces the play area (doc 07 §2.1).
5. **Recommendations grid**: 3-col grid of our standard cards (doc 07 §6.1) — cards may be
   *other providers'*; if the list spans >1 apiName, show a provider filter chip row (doc 07
   §6.1's dirty-but-working pattern, cleaned: chips not a button). Tap routes through the same
   `CloudDetailsKey` with that provider's sourceKey.
6. **Seasons picker** for TvSeries: chips from `seasonNames`/`SeasonData` (display names, doc 05
   §4.2; stored as `ext_extra_json.seasonNames`, doc 17 §5.1) + "No season" for season 0 (doc 07
   §2.3). Anime-type responses add a DUB/SUB chip pair when both maps exist (doc 07 §2.3; DW is
   the real shape, doc 12 §9.4).
7. **Movie Play flow**: `isMovieType` → NO episode list; a single large Play FAB under the
   synopsis that resolves `dataUrl` directly through the §5 flow (doc 07 §1.1; doc 10 §5
   movie-like behavior; CS3's button-label switch, doc 10 §5). Play-label variants ("Play
   movie") not needed — one label.
8. **Drama episode naming**: episode names render **verbatim** (`"Серія 1"`, localized ordinals —
   doc 12 §9.4) — never parse/reformat names; the "EP n" tag uses the `episode` int when present,
   else hides (doc 12 §10: `Episode.data`/names are opaque).
9. **posterHeaders everywhere** — poster, background, logo, episode stills, actor portraits:
   headers thread into every Coil request (fixing CS3's own episode/actor omission, doc 07
   §3.3) `[design]`. Logo (`logoUrl`) overlays the title when it loads, reverting to text on
   error (doc 07 §2.1 bindLogo behavior) — nice-to-have, behind the same pref gate as actors.

### 4.3 Wireframes

**(a) Movie details** (sparse-provider fields shown as absent):

```
┌──────────────────────────────────────────────────────┐
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │ ← blurred backgroundPoster
│ ▓▓▓           ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓        ▓▓ │   (falls back to poster,
│ ▓▓▓  ┌────┐  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓        ▓▓ │   headers-aware) + gradient
│ ▓▓▓  │2:3 │      Dune: Part Two                  ▓▓ │
│ ▓▓▓  │cvr │   8.3 · 2024 · 166 min · R · Movie   ▓▓ │ ← provider meta row (segments
│ ▓▓▓  └────┘   by Uakino                    ⟲  ☆  ⋮ ▓▓ │   drop when null)
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ (Sci-Fi)(Adventure)(Drama)          ← tags pills   │
│ Paul Atreides unites with…  ▾       ← synopsis     │
│  ┌────────────────────────────────────────────┐    │
│  │            ▶  Play movie                   │    │ ← single CTA; no episode list
│  └────────────────────────────────────────────┘    │
│ Cast   ◉ Timothée ◉ Zendaya ○ Rebecca …            │ ← actor portraits (images exist)
│ Recommended  (grid of 2:3 cards, provider chips)   │
└──────────────────────────────────────────────────────┘
```

**(b) Series details (seasons)**:

```
┌──────────────────────────────────────────────────────┐
│ ▓▓▓ blurred backdrop + gradient ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ ┌────┐  Breaking Bad                   ⟲  ☆  ⋮     │
│ │cvr │  9.5 · 2008 · 49 min · TV-MA · TvSeries      │
│ └────┘     by AllCalidad                              │
│ (Crime)(Drama)(Thriller)                              │
│ A chemistry teacher… ▾        Next ep in 2d 4h       │ ← nextAiring card (rare)
│ Episodes  (All)(S1)(S2)(S3)(S4)(S5)   [range ▾]      │ ← D-308 SeasonSelectorRow +
│ ┌──────┬───────────────────────────────┬────┐        │   ~30-ep range chunks (doc 07 §2.3)
│ │[img] │ EP 1  Pilot      2008-01-20   │ ⬇  │        │
│ │ 16:9 │ ██████░░░░ (resume 40%)       │    │        │ ← watch-progress overlay
│ ├──────┼───────────────────────────────┼────┤        │
│ │[img] │ EP 2  Cat's in the Bag…  ✓    │ ⬇  │        │ ← watched styling (alpha/grayscale,
│ └──────┴───────────────────────────────┴────┘        │   DetailsScreen.kt:2868 pattern)
│ Recommended                                           │
└──────────────────────────────────────────────────────┘
```

**(c) Sparse-provider fallback** (the DoramyWorld floor: name + poster + type only — doc 12 §10;
missing year/score/backdrop/actors all degrade, nothing crashes):

```
┌──────────────────────────────────────────────────────┐
│ ░░░░ poster-as-backdrop, blurred ░░░░░░░░░░░░░░░░░░░ │ ← no backgroundPoster → poster
│ ┌────┐  Серіал «Тіні»                       ⟲  ☆  ⋮ │   (both fall back to each other,
│ │cvr │  (no score) · (no year) · TvSeries · Uakino   │   doc 07 §2.1)
│ │⚠403│  ← if poster 403s WITHOUT headers: placeholder│ ← headers-aware load is the fix
│ └────┘     cover + "image unavailable" tone          │   (doc 07 §3.3)
│ (no tags row — hidden, not empty gap)                │
│ (no synopsis — hidden)                               │
│ Episodes                                             │
│ ┌ [poster→show-poster →placeholder fallback chain] ┐ │ ← per-ep thumbs are a bonus
│ │ EP 1  Серія 1   ⬇  │ EP 2  Серія 2  ⬇  …          │ │   (doc 12 §10); names verbatim
│ └───────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────┘
```

---

## 5. Cloud Watch flow

Doc 19 §3 owns the pipeline mechanics (callbackFlow adaptation, timeout clamp 5 s–8 min default
120 s, `isInvalidData` guard, cancellation-keeps-emitted-links, `WebViewResolverHost`, WatchKey
additive fields, 20-min saturated link cache, retry ladder §8). **This section owns only the
sheet UX** and stays consistent with it.

### 5.1 Flow (UI-facing)

Episode row tap (or movie Play) → `CloudLinkPickerSheet` (below) → link tap → `WatchKey` (doc 19
§3.4 fields: `sourceKey`, `mpvHeaders`, per-track-header subtitle lines, `episodeKey`,
`linkSource`) → **our MPV WatchScreen unchanged** (headers via
`setOptionString("http-header-fields")` before `loadfile` — doc 19 §2.1; subtitles downloaded to
temp files by our headers-aware `SubtitleEngine` — doc 19 §5).

### 5.2 `[design]` CloudLinkPickerSheet — the sheet UX

Base = `DownloadVideoPickerSheet`'s accordion (ModalBottomSheet, `dragHandle = null`,
single-expand server cards, quality FlowRow chips — DownloadVideoPickerSheet.kt:63-127, doc 19
§3.3) with the streaming behavior doc 19 §3.3 specifies; the UX shape:

```
┌──────────────────────────────────────────────────────┐
│  Links for EP 5                Loading… 3 links  [✕] │ ← live count while resolving;
│                                                      │   [✕] = skip-loading (appears
│ ▼ Uakino · HD                (server = ExtractorLink.source)  after 1st link)
│   (1080p M3U8)(720p M3U8)(480p MP4)   ← chips:      │
│ ▶ StreamWish · Latino                    quality +   │   type glyph per chip
│   (720p)(360p)  ⟲ mirror-retry          type glyph  │
│ ▶ DoodStream                            (collapsed)  │
│                                                      │
│ ▸ Subtitles (4)          ← collapsed; names uniquified│   ("English", "English 2" —
│                                                        │    doc 19 §1.1 rule 2)
│  3 links hidden — unsupported in v1                  │ ← DASH/torrent footer (doc 19 §1.3)
│  [Try another mirror]  (only in failure state)       │   rule 3 + D-295/D-296 visibility
└──────────────────────────────────────────────────────┘
```

UX decisions `[design]`:

1. **Rows appear as emissions land** (doc 19 D5 — streaming `Flow<SourceVideo>`): the accordion
   re-sorts servers by best quality as new links arrive (stable sort; row keys = server name so
   Compose diffs cleanly); a subtle shimmer on the newest row. Header shows "Loading… N links"
   with a small indeterminate progress dot until the terminal event.
2. **Skip-loading** (✕ / "Play what's here") appears after the first link — cancels resolution,
   keeps arrived links (doc 19 §3.2 #3). Label it "Skip loading" (CS3's skip, doc 09 §1.6 via
   doc 19 §3.3) — clearer than ✕ alone; keep both.
3. **Server/quality tiers**: server card label = `ExtractorLink.source` ("StreamWish"), chips =
   quality labels ("720p" from `Qualities` Int mapping) + a format glyph (M3U8/MP4) from
   `SourceVideoType` (doc 19 §1.3's 3-tier synthesis: server / "Default" audio / quality). The
   raw `label` string ("Latino · HD · Streamwish" — provider-custom, doc 12 §10) renders as the
   row's subtitle verbatim — opaque, never parsed.
4. **Subtitles preview**: collapsed "Subtitles (N)" header listing uniquified langs (doc 19 §3.3
   #3); they ride the picked video (no per-subtitle action needed in the sheet; selection happens
   in-player via our existing subtitle UI).
5. **Loading failure**: empty-at-timeout → inline EmptyState ("No links found — the source may
   be down") + actions: [Retry] (re-resolve) · [Try another provider] (only when other providers
   have this title — via `content_source_link`, doc 17 §2) · [Report] (copy diagnostics).
   **Mirror-retry** on a *playing* failure follows doc 19 §8's 6-step ladder (auto-retry same URL
   → cache bypass → pinned re-resolve → next link position-preserved → other providers → real-
   reason error state per D-295/D-296) — the sheet's per-chip ⟲ mirrors that ladder's manual
   variant `[design]`.
6. **Auto-select policy**: unlike aniyomi's `tryAutoSelect` (DetailsScreen.kt:398-456 precedent),
   cloud v1 **always shows the picker** when >1 link (labels are quality-of-experience hints, not
   truth — doc 12's mirror rotation warning), and auto-plays instantly when exactly 1 link
   arrives (the common DoramyWorld direct-m3u8 case — doc 12 §3) `[recommendation]`. Re-open
   within 20 min hits the saturated link cache → sheet appears fully populated instantly (doc 19
   §3.5).
7. **Download mode**: the same sheet serves the download-mirror pick (doc 19 §3.3 "one picker,
   two call sites") — a "Download" toggle in the sheet header switches the pick action to the
   download enqueue path (doc 17 §8.1).

---

## 6. Library & favorites for Cloud content

Data side is decided (doc 17 §7): CS3 content enters `library_item` via `main_entry`
(`content_type ∈ {movie, series, drama, anime, other}`), with a **[recommendation] auto-created
"Cloud" `library_category`** as the default landing category. UI:

1. **Where cloud favorites appear**: the Library tab, mixed with anime, **filtered by the
   existing category tabs** (D-138/D-140 — `LibraryScreen.kt:155-160`, null = "All"). The "Cloud"
   category is just another tab; "All" shows everything mixed `[design]` per doc 17 §7's
   recommendation. Cloud cards carry the provider pill badge (§3.2 #3) so mixed views stay
   legible. `[open-question]` Q9 (separate category vs mixed default) is doc 17 §7's own open
   question — restated below.
2. **Progress display for non-anime**: watch-progress is content-agnostic (doc 17 §7 — the data
   side works), but the *display* differs: anime rows show "EP 12/24"; cloud **series/drama rows
   show "S02E05 · 14/38"** (season-qualified canonical episode keys exist — doc 17 §3.3), cloud
   **movie rows show a watched check** (single unit; position/percent if partially watched)
   `[design]`. The Library's per-collection Continue-Watching toggle (LibraryScreen.kt:284)
   applies unchanged — resume rows render the same info ("Continue · S02E05 · 42%").
3. **Resume rows / History**: cloud entries in History and Continue Watching use the same row
   components with the provider badge + season-qualified label; tapping resumes through the §5
   flow (re-resolve via the 20-min cache or fresh `loadLinks`, doc 19 §3.5).
4. **What cloud library entries DON'T get**: no Track status pill, no AniList score column, no
   airing/next-episode schedule chips (AniList-only — doc 17 §5.4); no year-based sort guarantee
   (years are missing/faked — doc 12 §9.3 #4; doc 17 §5.1 leaves year-sort open). Sort options
   that depend on missing fields degrade gracefully (nulls last) `[design]`.
5. **Library add action**: the details-screen ☆ (bookmark) writes the `main_entry` +
   `content_source_link` and (first time) creates the "Cloud" category; long-press opens our
   category picker sheet (the anime precedent — DetailsScreen.kt:757-758, 1230) so users can file
   a drama anywhere from day one `[design]`.
6. **Downloads screen**: cloud downloads flow through the shared queue (doc 17 §8.1; doc 19 §6)
   — the Downloads screen needs zero new layout, only the cloud badge + `SxxEyy` filenames (doc
   17 §8.4) `[design]`.

```
Library (All) ─ per-card cloud variant:
┌────────┐  ┌────────┐  ┌────────┐
│ 2:3    │  │ 2:3    │  │ 2:3    │   ← same Library grid card (gradient title,
│ cover  │  │ cover  │  │ cover  │     DESIGN-SYSTEM 02 §5); cloud cards add:
│ ██████ │  │        │  │ ██████ │     • "Cloud · Uakino" mini-pill under title
└────────┘  └────────┘  └────────┘     • progress line "S02E05 · 14/38" (series)
 Frieren     Squid      Strong Girl    • movies: ✓ watched
 (EP 12/24)  Game       Bong-soon
             (Cloud·    (Cloud·
              Uakino)    AllCalidad)
```

---

## 7. Extensions screen evolution

Current state (doc 14 §7.4, D-294..D-303 inventory): `ExtensionsSettingsScreen` is a flat
aniyomi-only list with four sections — Trusted/Installed (reorder + enable toggles + D-301
update pill), Failed to Load (D-296 Errored rows with reason + Retry/Untrust/Uninstall),
Untrusted, Available — plus global search/sort/NSFW/language filters (D-298), full virtualization
(D-299), and supporting screens `ExtensionRepoSettingsScreen` (add/verify/delete repos),
`ExtensionDetailScreen` (per-source enable), `SourcePreferencesScreen` (tree-walks aniyomi
preference trees). Doc 16 §5.3 step 3 says CS3 joins this screen with ecosystem sections/tabs;
doc 16 §6 says separate repo managers under one settings surface.

### 7.1 `[design]` Ecosystem organization

**[recommendation] A 2-way segmented toggle (Aniyomi | CloudStream) at the top** of
`ExtensionsSettingsScreen` (our `SegmentedToggle` component — DESIGN-SYSTEM 03 §3), persisting
the last choice; each ecosystem keeps its own section stack. Rationale vs a unified list with
ecosystem badges: the two ecosystems have different trust models (per-package trust gate vs
repo-consent + sha256 — doc 16 §3.1), different row affordances (settings gear hidden for CS3,
§7.4), and different repos screens — one list would be a minefield of "why does this row have a
Trust button and that one doesn't". The toggle keeps each list honest; a small count badge
("CloudStream · 12") prevents the second ecosystem from being forgotten `[design]`.

```
┌──────────────────────────────────────────────────────┐
│  Extensions                                          │
│  ┌───────────────┬──────────────────┐  🔍  🌐  ⋮     │
│  │ Aniyomi (8)   │ CloudStream (12) │                 │ ← SegmentedToggle
│  └───────────────┴──────────────────┘                 │
│  INSTALLED                            [Check updates] │
│  ┌────────────────────────────────────────────────┐  │
│  │ ◉ Uakino        🇺🇦  v14   ⟳Update?           ⋮ │  │ ← row: icon · name (suffix
│  │   Movies, Series, Anime · 2.1 MB · Uakino team  │  │   "Provider" stripped — doc 04
│  │   [Movie][TvSeries][Anime]          [toggle ◉]  │  │   §3.2) · flag · version ·
│  └────────────────────────────────────────────────┘  │   tvTypes chips · size · authors
│  ┌────────────────────────────────────────────────┐  │
│  │ ◉ HentaiUkr      🇺🇦  v3   18+               ⋮ │  │ ← NSFW badge; hidden entirely
│  └────────────────────────────────────────────────┘  │   when master toggle OFF (§8)
│  ERRORED                                              │
│  │ ⚠ DoramasFlix — okio.TimeoutException …          │  │ ← D-296-style: real reason +
│  │   [Retry] [Uninstall]                            │  │   Retry/Uninstall (no Untrust —
│  AVAILABLE (from 3 repos)                            │   nothing to re-verify, doc 16
│  │ + AllCalidad  🇲🇽  v22  Movies, Series   [Install]│   §3.1)
│  └──────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────┘
```

### 7.2 Rows, install/update/uninstall `[design]`

- **Row data** = `plugins.json` fields (doc 04 §3.1): icon (`iconUrl` with `%size%`/`%exact_size%`
  placeholders resolved to our row's pixel size — doc 04 §3.3; placeholder fallback = monogram
  tile like our aniyomi rows), display name, authors, description line, `tvTypes` chips (string
  values, doc 10 §1.1 — rendered as-is; unknown values like `"All"` simply don't match filters —
  doc 10 §1.1), language flag emoji (dirty tags tolerated: raw string fallback — doc 10 §4),
  `fileSize`, version. **Status badge** for `status 2/3` ("Slow"/"Beta" — doc 04 §3.2);
  `status == 0` (Down) drives the kill-switch: on next update check the row flags
  "Disabled by repository" and offers Uninstall (doc 16 §3.2) `[design]`.
- **Install** = the `InstallStep` UX reused (Downloading(progress) → Installing → Installed |
  Error — our D-300/D-309/D-311 pipeline shape, doc 16 §3.1 "pattern reuse"): download `.cs3`
  stream → sha256 vs `fileHash` (`"sha256-<hex>"`, only-if-non-null — doc 04 §3.2/§4) → load →
   register. Error surfaces the real reason (D-295 discipline — NOT CS3's toast-and-forget, doc
   16 §3.1). The "arbitrary code in-app" consent text lives at **repo-add** time (doc 16 §6), not
   per-install.
- **Update** = integer version compare (`online.version > installed.version`, `-1` = always —
  doc 04 §3.2/doc 16 §3.2) shown as the D-301-style Update pill, batch "Check updates" button on
  the 30-min entry throttle pattern (doc 16 §6).
- **Uninstall** = delete file + deregister + drop record (doc 16 §3.2) — instant, no
  confirmation beyond a standard dialog.
- **Enable/disable per provider** = OUR addition (CS3 has none — doc 16 §3.4
  `REGISTERED ⇄ DISABLED`): the same per-row toggle aniyomi rows have (doc 14 §2.5 per-package
  isEnabled precedent). Disabled providers vanish from Cloud browse/search pickers but stay
  installed.

### 7.3 Repos `[design]`

`ExtensionRepoSettingsScreen` gains the same segmented toggle; the CloudStream side lists
`anikuta_cs3_repos` (separate store — doc 16 §6), add-flow = paste repo.json URL →
fetch repo.json + follow `pluginLists[]` + require ≥1 parseable entry (CS3's own bar, doc 04 §8)
**plus our consent dialog** ("this repository can run arbitrary code in-app" — the CS3 community
registry's own warning, doc 04 §5.2 via doc 16 §6) → verified badge. Delete cascades to its
plugins (with a warning). **Zero default repos** (doc 16 §6 recommendation; `[open-question]` Q3).

### 7.4 Per-plugin settings entry

**Hidden in v1** per doc 16 §7's recommendation: 58/58 census providers expose no settings (doc
11 §6, doc 12 §9.2); the gear is not rendered (CS3 hides it on TV the same way — doc 11 §4.3 via
doc 16 §7). The `hostcompat` DataStore shim keeps the 7/80 DataStore-using plugins *browsing*
fine (doc 16 §7). If/when demanded: Fragment-host option (c) with doc 11 §8's theming +
`requiresResources` checklist — a v2+ design task, out of scope here `[open-question]` Q10.

---

## 8. Settings

CS3's own provider-settings surface (doc 10 §2.1) is five rows; doc 10 §8 distills what to
replicate. **[recommendation] ONE new screen "Cloud sources" under Settings → Extensions**
(SettingsScreen.kt:83-92 gains a second row in the Extensions section, subtitle "NSFW, languages,
content types"):

| Row (ours) | CS3 analog (doc 10 §2.1/§2.6) | Type / **recommended default** |
|---|---|---|
| Show NSFW extensions & content | `enable_nsfw_on_providers_key` + preferred-media NSFW entry — **merged into ONE switch** (doc 10 §8.5: CS3's two-switch accident is the anti-pattern) | Switch, default **OFF** — gates: extension-store visibility, provider pickers, browse/search results, and (per doc 10 §8.5) keep the "don't save NSFW watch progress" player rule (doc 10 §3 #3) |
| Provider languages | `provider_lang_key` (StringSet + "universal" sentinel) | Multi-select chips over distinct provider `lang` values, default **All** (sentinel) — dirty tags shown raw (doc 10 §4) |
| Preferred content types | `prefer_media_type_key_2` (TvType ordinals, default all-minus-NSFW) | Multi-select chips over the doc 10 §8.3 groupings (Movies/Series/Anime/Dramas/Cartoons/Documentaries/Live/Torrents/Others), default **all minus NSFW** (doc 10 §2.6 `ACU:454-457` analog) |
| Show cast in details | CS3's `show_cast_in_details_key` (doc 07 §2.1) | Switch, default ON |

Storage: our `PreferenceStore` (new keys `cs3_nsfw_enabled`, `cs3_provider_langs`,
`cs3_preferred_types`, `cs3_show_cast`) — CS3's key inventory (doc 10 §2.6) is the naming
inspiration, not the storage (doc 16 §6 keeps ecosystems' stores separate). **Persist the browse
chips + search chips + provider selections in DataStore** (doc 10 §8.6 — CS3's
`home_pref_homepage`/`search_pref_tags` pattern) instead of `remember {}` so users don't
re-select on every entry. Type/language changes reset the Cloud home provider only if it no
longer passes the filter (doc 10 §2.1's `currentHomePage = null` behavior — gentler: pick the
first still-valid provider and toast) `[design]`.

Not user-facing (internal prefs): `details_source_link:<mainId>` re-keying (doc 17 §5.4),
`cloud_home_provider`, search provider subset — all PreferenceStore, no UI.

---

## 9. Design-language compliance

### 9.1 Token usage (all mandatory for Cloud screens)

- **Motion**: press feedback `tween(Motion.DurationShort=150, FastOutSlowInEasing)` +
  `graphicsLayer` scale 0.95 (BrowseCards.kt:119-123); section fade+expand `tween(300)`
  (BrowseScreen.kt:309-317); details entry = the D-324 450ms emphasized container crossfade with
  the **D-327 600ms `DurationSharedFlight` cover flight on the same `EasingEmphasized` curve**
  (Motion.kt:22-36; MainActivity.kt:554-610; SharedTransitionLocals.kt:55-68). No new durations.
- **Sheets**: every cloud sheet (`CloudProviderPickerSheet`, providers subset, link picker,
  category picker) = ModalBottomSheet with `dragHandle = null`, `skipPartiallyExpanded = true`,
  70% height cap, surface containerColor (DESIGN-SYSTEM 01 §4; ExtensionSourcePickerSheet.kt:61-70).
- **Headers/scroll**: `CollapsingHeader` + `ScrollBlurOverlay` on all three top-level cloud
  screens (23-screen precedent, DESIGN-SYSTEM README cross-cutting table).
- **Cards**: portrait 2:3, 12dp corners, 1dp outlineVariant@60% border, surfaceVariant@40%
  placeholder tone, 12sp Bold title + 10sp Medium subtitle, amber pointed score tag
  (BrowseCards.kt:111-225 — the D-252/D-257 language); horizontal rows use a 16:9 variant
  (BrowseHero precedent, BrowseScreen.kt:184-192). Grid = `GridCells.Fixed(3)` (search/results;
  DESIGN-SYSTEM 04 §5). Pill badges = the unified pill system (outlineVariant background,
  DESIGN-SYSTEM 02 §1) for type/quality/provider/year pills.
- **Empty/error**: `EmptyState` + Retry action, never dead-end text (BrowseScreen.kt:322-362);
  per-row inline errors for sectioned surfaces (§2.2 #2).

### 9.2 Shared-element keys — the D-328 rule applied to Cloud `[design]`

D-328's lesson (Library⇄Search ghost morph, v0.2.62 — SharedTransitionLocals.kt:34-68): keys are
**namespaced per screen** because both screens compose simultaneously during switches. Cloud
screens MUST use their own namespace — add canonical builders next to the existing three
(SharedTransitionLocals.kt:91-100):

```kotlin
// [design sketch] — extend SharedTransitionLocals.kt canonical builders
/** Cloud browse card cover key (cover:cloud:browse:<provider>:<url>). */
fun cloudBrowseCoverKey(provider: String, section: String, url: String?): String? = ...
/** Cloud search card cover key (cover:cloud:search:<url>). */
fun cloudSearchCoverKey(url: String?): String? = ...
/** Cloud library card cover key (cover:cloud:library:<url>). */
fun cloudLibraryCoverKey(url: String?): String? = ...
```

Rules: (a) browse keys are **provider+section-qualified** (same URL can sit in two of one
provider's rows — the browseCoverKey(sectionKey,…) lesson, BrowseCards.kt:126-134); (b) cloud
keys never collide with anime keys (`cover:library:` vs `cover:cloud:library:` — distinct
formats, the exact D-328 guarantee); (c) the Cloud details side never constructs keys — it
carries the source card's key through `CloudDetailsKey.transitionKey` (the
`AnimeDetailsKey.transitionKey` mechanism, SharedTransitionLocals.kt:48-51); (d) `null` disables
the element (prefs gate `coverTransitionEnabled` + missing poster — BrowseCards.kt:131-134).
Note: a cloud poster URL is not globally unique across providers (hotlink hosts reuse paths) —
provider-qualification inside the key is what keeps the flight honest.

### 9.3 TvType color coding & card/list shapes

- **[recommendation] No new color coding v1**: TvType renders as a text pill (existing pill
  system) — doc 10's taxonomy is three layers of inconsistent metadata (manifest strings vs
  declared vs runtime types — doc 10 §0/§1.4 drift); color-coding would advertise a precision
  the data doesn't have. Revisit if the user wants it `[open-question]` Q11.
- **Card shape per coarse type** (runtime `LoadResponse.type`/card `type?`, doc 10 §1.3):
  Movie/Series/Drama/Anime/Cartoon → 2:3 poster cards; Live → 16:9 horizontal cards (doc 12
  §9.4 channel tiles); Others → 2:3 cards with a type pill (flat video, doc 12 §9.4). Grids stay
  3-col; rows stay LazyRow.
- **Sparse tolerance is a design-language rule**: every cloud component renders correctly at the
  `name + posterUrl + type` floor (doc 12 §10); missing fields hide rows/pills — never reserve
  space, never crash, never "null".

---

## 10. Phased UI rollout

Aligned with doc 16 §5.3's step order and doc 20's roadmap phases (doc 20 pending — this section
is the UI-side preview doc 20 will reference):

| Phase | UI scope | Ships with (backend) | Exit criteria |
|---|---|---|---|
| **0. Bootstrap (no UI beyond extensions)** | Extensions screen CS3 toggle + repo add + install/errored rows (§7) — the *crude* version (no filters, no badges beyond version) | doc 16 §5.3 steps 1-2 (registry, provider-api extensions) + §3 loader/installer | one .cs3 installs, loads, lists in the registry; errored rows show real reasons |
| **1. Minimal Cloud Screen** | Cloud tab (dynamic 5th pill) with ONE provider end-to-end: provider chip (single entry), section rows + per-row pagination, Cloud Details (movie + series variants, sparse fallback), CloudLinkPickerSheet + WatchKey handoff (§2/§4/§5) | doc 16 §5.3 step 4 + doc 19 §3 pipeline | Uakino fixture: browse → details → picker → player on device (doc 19 §10 two-tier plan) |
| **2. Search + filtering** | CloudSearchScreen fan-out + per-provider sections + badges + subset picker + TvType/language chips + persisted filters (§3); NSFW gating UI (§2/§8) | `fetchContentListPaged` search path (doc 16 §4.3) | search across ≥3 providers with live incremental fill |
| **3. Library + shared surfaces** | Cloud library category + badges + season-qualified progress + resume rows + downloads badges (§6); details bookmark/category actions | doc 17 P1-P5 schema (content_source_link, canonical keys, ext axis) | a drama favorited, partially watched, resumed, downloaded |
| **4. Management & polish** | Extensions polish (§7 full: chips, status badges, update pills, kill-switch states), Settings "Cloud sources" screen (§8), onboarding empty states, safe-mode banner (doc 16 §3.5), actor/recommendation/nextAiring richness in details (§4.2) | cs3_result_cache (optional), subscription poll table (doc 17 §5.3) if updates-feed UI included | full §7/§8 surfaces; empty app → installed → browsing path with zero dead ends |

UI build order rationale: the Cloud Screen is deliberately **registry-first** (doc 16 §5.3 step
4 — the seam's first load-bearing consumer); anime screens are untouched until Phase 3 touches
shared Library/Downloads rows (badge + label changes only). Phase 1 uses Uakino as the fixture
provider (6 rows, movie+series, direct m3u8 — doc 12 §2); Phase 2 stress fixtures: DoramyWorld
(dub maps) + AnimeJl (57 extractors) per doc 19 §10.

---

## 11. Open questions for the user

1. **`[open-question]` IA confirmation** (doc 16 §11-1 restated): separate Cloud tab (this doc's
   working assumption, §1) or unified-into-anime-flows? If unified: which parts first (browse
   sections vs search results)?
2. **`[open-question]` Cloud tab naming + icon**: "Cloud" (assumed throughout)? Alternatives:
   "Stream", "Discover", "Video". Icon: cloud vs play-vs-anime-style glyph. Affects the bottom
   bar pill + header title.
3. **`[open-question]` 5th-tab placement**: dynamic (appears only with ≥1 provider — §1.2
   recommendation) vs always-5-pills? And should "cloud" be a valid cold-start restore tab
   (D-282 set, MainActivity.kt:386-388)?
4. **`[open-question]` Light vs full v1** (§10): is Phase 1+2 (browse/search/details/watch) the
   right v1 cut, with library/management polish landing after? Or is library-integration
   (Phase 3) mandatory for v1 because favorites are core to how the user uses the app?
5. **`[open-question]` NSFW default** (doc 16 §11-4, doc 10 §8.5): master toggle default OFF
   confirmed? And should NSFW items inside *mixed* providers be badge-only (§2.2 #8) or also
   filtered when OFF?
6. **`[open-question]` Default repos** (doc 16 §11-3): zero defaults (recommended — matches
   D-043 + CS3 posture) or pre-seed the official `recloudstream/extensions` repo? If pre-seeded:
   which community repo(s) for movies/dramas (phisher? — unlicensed, GPL-mixed)?
7. **`[open-question]` Search duplicates**: per-provider sections with visible duplicates (§3.2,
   recommended) vs a "grouped by title" merged view v2? CS3's round-robin flat merge is NOT
   recommended (dedup impossible on dirty data — doc 12 §9.3 #4).
8. **`[open-question]` Cross-world actions**: want the v2 "Search this anime in Cloud" action on
   anime details (§1.2)? Any v1 cross-links at all?
9. **`[open-question]` Library placement** (doc 17 §7): auto-created "Cloud" category (default
   landing) confirmed vs mixed-into-Default?
10. **`[open-question]` Plugin settings UI** (doc 16 §11-6): v1 skip (gear hidden) confirmed —
    revisit only if a provider the user cares about ships settings?
11. **`[open-question]` TvType color coding** (§9.3): none in v1 — want type-colored accents
    later once runtime-type drift is measured?
12. **`[open-question]` Drama season grouping default**: flat list (CS3 default — most dramas
    are single-"season") vs always group by season when `season` ints exist? (D-308 chip selector
    already handles both; this is just the default state.)

---

## 12. Verification status

- CS3 facts inherit docs 03-13 verification (cited per claim; the load-bearing UX facts are doc
  06 §1-§3 + §7.1 [home/search/pagination/badges], doc 07 §2-§3 + §6 [details rendering, image
  pipeline, recommendations], doc 10 §0-§3 + §8 [layers, filters, NSFW, key inventory], doc 12
  §9-§10 [quality spectrum + Cloud Screen requirements], doc 04 §3 [plugins.json fields]).
- Integration facts: doc 16 §2/§3/§4.3/§5/§6/§7/§8 (module + registry + IA recommendation +
  settings/repo decisions), doc 17 §3/§5/§6/§7/§8 (canonical keys, ext-axis metadata, favorites,
  downloads), doc 19 §1.3/§3/§5/§8 (picker synthesis, resolve flow, subtitles, retry ladder) —
  all read in full for this doc.
- Our-pattern claims verified fresh in-source for this doc: `MainActivity.kt` (navItems :372-378,
  rootTabKeys :304-309, startTab :386-388, allowedUpdateSheetKeys :325-352, shared-element
  transition block :554-610, showBottomNav :1017-1019), `BrowseScreen.kt` (full), `BrowseCards.kt`
  (full), `SearchScreen.kt` (:60-179 + outline), `ExtensionSourcePickerSheet.kt` (:54-103),
  `DetailsScreen.kt` (structure + :740-854, :1121-1160, :2793+ sections), `LibraryScreen.kt`
  (:155-160, :284, :309-313), `SettingsScreen.kt` (:72-150), `MoreScreen.kt` (:40-156),
  `SharedTransitionLocals.kt` (:28-100), `Motion.kt` (:22-36), DESIGN-SYSTEM README (full).
- Everything in this doc is **[design]/[recommendation]** — no code exists; nothing here has
  been rendered or device-tested. The wireframes are proportion sketches, not pixel specs.
- Not verified: whether 5 pills fit our floating bottom bar on 360dp-width devices at our
  current pill metrics (needs a quick device check before Phase 1 — noted as a Phase-0 UI spike);
  Coil 3 header-passing exact API shape for `NetworkHeaders` on our version (doc 07 §3.5 says
  1:1; the port spike confirms).

*End of doc 18. Companions: doc 16 (architecture — normative for the registry/modules),
doc 17 (data layer — normative for schema/keys/favorites), doc 19 (playback — normative for the
resolve pipeline + picker mechanics), doc 20 (roadmap — will sequence §10).*

---
## ✔ B5-b Verification Note (2026-08-29)
Checked: 18 claims sampled → 18 verified, 0 corrected, 0 flagged-stale (two ±1-2 line drifts noted, trivial). Consistency: ok — §10's phase table matches doc 20's mapping (UI Phase 0→eng Phase 1, 1→2, 2+3→3, 4→5; doc 20 §0 states the same mapping).
Corrections: none.
Samples re-verified in our source: **5th-tab feasibility** — `navItems` exactly 4 items (MainActivity.kt:372-378), `rootTabKeys` :304-309, startTab sanitize `"browse"||"library"` :386-388 (D-282), `allowedUpdateSheetKeys` :325+, `showBottomNav` :1017-1019; **shared-element lesson** — SharedTransitionLocals.kt:34-48 D-328 namespacing rules incl. the v0.2.62 Library⇄Search ghost-morph, canonical builders :91-100 (`cover:library:`/`cover:search:`/`cover:browse:<section>:`), Details-never-constructs rule :48-51; Motion.kt:22-36 (DurationContainer=450 D-324, DurationSharedFlight=600 D-327); MainActivity.kt:554-560 transition block; BrowseScreen.kt CollapsingHeader+PullToRefreshBox :135-144, BrowseSkeleton :146, BrowseHero 16:9 :184-192, BrowseSection fade+expand tween(300) :309-317, BrowseErrorState/EmptyState :322+; BrowseCards.kt BrowseSectionHeader :64-75, press-scale 0.95 + Motion.DurationShort :119-123, D-320/D-328 namespaced+section-qualified key :126-134, amber pointed score tag :174+; SearchScreen.kt D-210 WebView auto-refresh :128-141, ExtensionResultCard :638+; SearchViewModel DEBOUNCE_MS=350 (:58, debounce :329) + D-305 generation :101-113; ExtensionSourcePickerSheet.kt skipPartiallyExpanded/70%/dragHandle=null/surface :61-70, icon rows :96-107; LibraryScreen.kt category tabs D-138/D-140 :155-160, showContinueWatching :284; DownloadVideoPickerSheet.kt accordion :63+; DetailsScreen.kt sharedCoverKey/toggleLibrary/openCategorySheet :753-758, SeasonSelectorRow :2560+, EpisodeRow :2793+, auto-resolve/tryAutoSelect :391-413 (doc's 398-456 block), ManualLinkSheet at :1219-1220 (doc says 1218 — ±1, trivial), CategoryPickerSheet :1232 (doc says 1230 — ±2, trivial). CS3-side UX facts inherit docs 06/07/10/12 (not re-derived here; doc 12's census frame annotated in its own B5-b note).
