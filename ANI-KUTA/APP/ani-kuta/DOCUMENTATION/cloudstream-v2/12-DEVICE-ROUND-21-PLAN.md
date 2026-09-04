# Round 21 (Task 61) — Device-Round Plan: QoL Improvements

The v0.4.8 device round results: download-page scroll + server-name truncation + CS-tab
landing + reset confirmation + line spacing + bold default ALL confirmed working. The
remaining findings + the new QoL batch, one plan:

## Confirmed-good invariants (DO NOT TOUCH)
- The whole download pipeline (resolve → pick → queue → download → play).
- `.bin`/`.WHITECAT` import flow, the strict-format rejection, the 1.5s added hand-off.
- Subtitle overlay geometry (line-height ratio), bold default, reset confirmations.
- The debug toolkit. Aniyomi stack: additive-only changes.

## Fix areas

### A. Plugin icons — never render "nothing" (3 surfaces)
- **Root cause** (extensions page): `CsPluginIcon` renders `AsyncImage(iconUrl)` with NO
  error fallback — when the URL fails to load (404 / a `file://` URI exported from the
  SENDER's device / network), a blank 40dp box renders. The user: "not being shown
  anywhere at all".
- **A1** `ExtensionListChrome.CsPluginIcon`: AsyncImage → SubcomposeAsyncImage —
  `error` (and `loading`) fall back to `ExtensionIconPlaceholder` (the colorful letter
  tile = the "default SVG" glyph). Applies to the extensions list AND the plugin detail
  page (shared component).
- **A2** Import `ConfirmCard`: the iconUrl branch gets the same error fallback (generic
  extension glyph in the badge) — embedded bytes stay the primary source.
- **A3** `DoneCard.Added`: the plugin icon now renders in the 72dp badge (iconUrl rides
  `PluginImportOutcome.Added` from the import result record) with the same fallback.
- **A4** Share-side hardening: `export.json.iconUrl` must NEVER carry a device-local
  `file://` URI (meaningless on the receiver, guaranteed AsyncImage failure). Only
  http(s) URLs ride the metadata.

### B. "Format sources" menu polish (3 local copies: CsFormattingTitle,
###    ResolverFormattingTitle, WatchFormattingTitle)
- The menu opens ABOVE the heading now (a `Popup(Alignment.BottomStart, focusable)`
  with the same bordered-surface look — deterministic above-anchoring vs. the fragile
  DropdownMenu negative-offset).
- Label: exactly **"Format sources"** (not "Formatted sources").
- Guaranteed spacing between the label and the toggle: a fixed 24dp spacer + a 220dp
  minimum menu width (the row still fills; the switch trails).

### C. Search pagination (both ecosystems)
- ViewModel: `SearchUiState.Success` / `ExtensionSuccess` gain `hasMore` + `loadingMore`
  + the paging context (query / page / source identity); `loadMore()` appends page+1:
  - AniList: `searchAnime(page+1)` (stop on empty page).
  - aniyomi extension: `getSearchAnime(page+1, q)` / `getPopularAnime(page+1)` (blank
    query) with `AnimesPage.hasNextPage`.
  - CloudStream: `repository.search(provider, q, page+1)` with `hasNext`.
  - Append path: dedupe by the grid's key identity, generation-guarded.
- UI: `shouldLoadMore(gridState)` derived state (approach-bottom: last visible index ≥
  total − 6) fires `viewModel.loadMore()`; a full-span footer item shows the
  "loading more" spinner while a load is in flight (visible if the user beats the
  pre-fetch).

### D. Randomized CloudStream section order
- Every time the user ENTERS the search page (ON_RESUME with a blank query +
  ExtensionBrowseSuccess, and every fresh load), the sections list is shuffled
  (Collections.shuffle, a fresh random per shuffle). The shelf CONTENTS stay intact;
  only the row order changes.

### E. Category subpages (the CS shelves)
- New `CsCategoryKey(providerName, sectionTitle, sectionIndex)` NavKey (anime-search
  api) + `CsCategoryScreen` (impl): heading = the category title, results in a grid,
  infinite scroll (the same approach-bottom + footer pattern as C).
- Repository: `browseShelf(providerName, shelfIndex, page): CsContentPage` — resolves
  the shelf from `provider.mainPage`, calls `getMainPage(page, request)`, maps +
  dedupes cards, carries `hasNext`.
- The section titles on the search page become clickable → navigate to the subpage
  (SearchScreen gains `onNavigateToCategory`; MainActivity wires it to the backstack).

### F. Search image-loading performance
- A DEDICATED OkHttp client for Coil (a `newBuilder()` clone of the app client) with
  `Dispatcher(maxRequests = 2)`: at most 2 concurrent cover fetches app-wide; queued
  requests are FIFO (finish-current → in-view → offscreen; Lazy composition naturally
  enqueues the visible cards first). The app's own client is untouched.
- Card-level loading placeholders (a dim surface while loading) on the search result
  cards — kills the pop-in flash jank.
- Caching: already the 500MB disk cache + 25% memory cache (ImageLoaderFactory) — now
  actually hit consistently by the bounded loader.

### G. Pull-to-refresh on the search page
- `PullToRefreshBox` (the same m3 component as the Library) around the results area —
  only active when content is showing (the inner list is at the top).
- `viewModel.refreshCurrent()`: the current mode's page-1 reload; for CS browse the
  cached feed is invalidated FIRST (old cache deleted), then reloaded fresh — the new
  sections arrive randomized (D).

### H. Library category chips
- Auto-scroll the selected category into view when the page opens (LaunchedEffect →
  scrollToItem of the selected chip's index).
- The underline bar now matches the TEXT width (the Column wraps the text; the bar
  fills it), 3dp thick, 2dp below the text (closer).
- The chip row's vertical paddings shrink (2dp/4dp → 0dp/2dp, tab inner 4dp → 2dp) — a
  tighter section.
- An 8dp spacer between the divider and the content below.

### I. Downloaded-episodes UI
- Cards COLLAPSED by default; `animateContentSize` expand/collapse.
- Separator lines between episode rows.
- Two-step delete (episodes + delete-all): tap delete → the button morphs (error tint
  + `DeleteForever`); tapping it again deletes; tapping ANYTHING else (the row, the
  card, another control) reverts to the default state.
- Expand/collapse button moves LEFT of the delete button (currently delete-all is
  left of the chevron).
- The episode count renders as a highlighted tag (primary-container pill).

### J. Ads system
- URL → `https://www.profitableratecpmnetwork.com/tfi8yqn4w?key=7378f4443c4c59466d19573ced0ef844`.
- `minTimeOutsideMs`: 15s → **5s**.
- Offline gate: when the ad is DUE but there is no usable network, the interstitial is
  NOT shown — the navigation proceeds immediately WITHOUT recording the ad (no
  cooldown), so the ad stays due and fires the next time the user is online. The
  coordinator gets the app context via Koin (`androidContext()`) + a
  ConnectivityManager check.

## Execution order (one item at a time, verified)
B → A → H → I → J → C → D → E → F → G → version 0.4.9/74 + docs + syntax-check sweep
+ commit/push/CI/release.

---

## AS-BUILT (round 21 / Task 61 — v0.4.9/74)

All ten fix areas implemented exactly per plan, plus two execution notes:

- **B (×3 copies)**: the menu is a `Popup(Alignment.BottomStart, focusable=true)`
  — the menu's bottom edge sits ON the heading's bottom edge, so it ALWAYS grows
  upward (deterministic; no DropdownMenu negative-offset guessing). 220dp min
  width + a FIXED 24dp spacer between the "Format sources" label and the Switch
  (the weight-only spacer collapsed to 0 in a wrap-content menu — that was the
  "add some spacing" complaint). `shadowElevation=8dp` keeps the dropdown feel.
- **A**: `CsPluginIcon` + both import-flow badges are SubcomposeAsyncImage now —
  the loading AND error slots render fallbacks (the letter tile / the glyph), so
  a failed iconUrl (404 / a `file://` URI pointing at the SENDER's device) can
  never render a blank box. The Added card renders the plugin's OWN icon with a
  compact check badge. The share side only writes http(s) iconUrls into
  export.json (a device-local `file://` URI is meaningless cross-device — THE
  root cause of "no icon anywhere").
- **C**: `SearchUiState.Success/ExtensionSuccess` carry `hasMore`+`loadingMore`;
  the ViewModel tracks `PagingMode` (5 loader identities) + `lastLoadedPage`;
  `loadMore()` appends with id-dedupe (AniList) / sourceKey:url-dedupe
  (extensions) — the same D-304 crash-guard the first pages use. The UI's
  `ApproachBottomEffect` reads `layoutInfo` in composition (re-keys on every
  append → continuous infinite scroll) with a 6-item (~2 rows) threshold; the
  full-span "Loading more…" footer shows while a page is in flight. AniList
  pages by the full-page heuristic (its API hides pageInfo); aniyomi uses
  `AnimesPage.hasNextPage`; CS uses the repository's `hasNext`.
- **D**: the sections shuffle at BOTH ViewModel landing sites (mapIndexed BEFORE
  shuffle so each row keeps its ORIGINAL provider shelf index) + on
  `onScreenResume` (activity-level entry) + `onPageEntered` (composition-level
  entry — tab switches, subpage returns). Blank query only; search results are
  not sectioned.
- **E**: `CsCategoryKey(providerName, sectionTitle, shelfIndex)` → the new
  `CsCategoryScreen` (CollapsingHeader + 3-column grid + the same
  approach-bottom/footer pattern) backed by `CsCategoryViewModel` (Koin
  viewModelOf) + the repository's new `browseShelf(provider, shelfIndex, page)`
  (getMainPage paged, no section cap, honest errors). The section TITLES on the
  search page are clickable (the title row gets a rounded ripple + the shelf
  index rides the navigation).
- **F**: `ImageLoaderFactory` builds a `newBuilder()` CLONE of the app's OkHttp
  client with `Dispatcher(maxRequests=2)` — the 2-concurrent-fetch cap is real
  and image-scoped (the app client untouched; FIFO order = finish-current →
  in-view → offscreen, since Lazy composition enqueues visible cards first).
  The search/category cards render over a dim `surfaceVariant(0.4)` placeholder
  (no white pop-in) + the existing 500MB disk / 25% memory caches.
- **G**: `PullToRefreshBox` (the Library's m3 component) wraps the results area;
  `refreshCurrent()` reloads the current mode's page 1 — the CS browse cache is
  invalidated FIRST (the "old cache deleted" spec), so the fresh randomized
  sections land via the full load path.
- **H**: `CategoryTabsRow` gets its own `rememberLazyListState` + a
  `LaunchedEffect` `scrollToItem` of the selected chip (first/last categories
  land in view). The underline is `fillMaxWidth()` inside the text-wrapping
  Column (width = the text), 3dp thick, 2dp gap; the row/tab paddings shrank;
  an 8dp spacer follows the divider.
- **I**: the downloaded cards start COLLAPSED, expand via `animateContentSize`
  (+ a rotating chevron), render separator lines between episodes, the EP count
  as a primary-tinted tag, the expand button LEFT of the delete button, and a
  TWO-STEP delete everywhere (tap → error-tinted DeleteForever; tap again →
  delete; any other interaction disarms). The round-20 chip rendering inside
  the episode rows is byte-identical.
- **J**: the real sponsor URL, 5s min-time, and the offline gate —
  `requestNavigation` proceeds WITHOUT the popup and WITHOUT recording the ad
  when ConnectivityManager reports no INTERNET+VALIDATED network (the ad stays
  due; fires on the next online navigation).

Execution notes (post-plan catches):
1. `mapTo(mutableSetOf(keyOf))` — a missing-transform compile blocker in the two
   new append helpers — caught in the static sweep, fixed to
   `mapTo(mutableSetOf()) { keyOf(it) }`.
2. The shelf index must be captured BEFORE the shuffle (the subpage's
   `browseShelf` resolves `provider.mainPage[shelfIndex]`) — `ExtensionBrowseSection`
   gained `shelfIndex`, assigned pre-shuffle.

Files touched (21): CsSourceListUi, ResolverSheet, PlayerSheets (the ×3 menu
copies), ExtensionListChrome, PluginImportActivity, CloudstreamPluginDetailScreen,
LibraryScreen, DownloadedFilesScreen, AdsConfig, AdsCoordinator, AdsModule,
SearchViewModel, SearchScreen, CloudstreamContentRepository, CsCategoryKey (new),
CsCategoryViewModel (new), CsCategoryScreen (new), SearchModule, MainActivity,
ImageLoaderFactory, AndroidConfig.

Execution note 3 (post-CI): the first push FAILED on one conflict —
ExtensionReorderList.kt carried its own PRIVATE 2-param ExtensionIconPlaceholder
(identical signature to the newly size-parameterized shared one — "Conflicting
overloads"); the private copy is deleted, the shared tile renders there now.
CI is the compiler of record: the ONLY two `e:` lines in the whole log were this
one conflict.
