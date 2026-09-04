# Task 62 (round 22) — the v0.4.9 device round: stability, linkage, and the library performance pass

Device reports this round fixes (user's v0.4.9 test):

1. **CRASH after a manual plugin import** — `IllegalArgumentException: Key
   ExtensionsSettingsKey was used multiple times` the moment the post-Add
   hand-off landed on the extensions page.
2. **Manually installed CloudStream plugins not linked to repositories** —
   the manual install and the repo's catalog entry rendered as TWO rows ("the
   only difference was the internal name"); the user asked that the app
   "properly recognize the cloud stream extensions and their repositories even
   after the repository was added later on".
3. **Format-sources menu position** — rendered ON TOP of the episode-number
   heading and INSIDE the bottom sheet; the spec: above the heading, outside
   the sheet.
4. **Randomization triggers** — re-shuffled on subpage return and on app
   reopen; the spec: only on a true search-tab exit + return, or a
   pull-to-refresh. Plus the SMART randomization: "the first four of any of
   the categories will not be the same as any other one of them" — and a
   category that cannot satisfy that simply stays unrandomized.
5. **Library chips underline invisible** + the library PERFORMANCE audit
   (H1/H2/M1/M2/M3/M4 from the user's report).
6. **Downloads tag** — "(5 Episodes Downloaded)" instead of "5 EP".
7. **Cover zoom** — the pinch pivots on the center, not the fingers ("if I
   try to zoom in on the top right corner, it zooms in on the center"); pan
   while zoomed stays.

## A — The crash (the stale-closure double push)

`MainActivity.checkPendingCsPluginNav` is a LOCAL function captured by the
ON_RESUME `DisposableEffect` (keyed on the LifecycleOwner — never re-keyed).
The captured closure's `currentKey` val goes STALE after navigation. When the
user returned from `PluginImportActivity` while ALREADY sitting on the
extensions page, the stale guard (`currentKey !is ExtensionsSettingsKey`)
passed and a SECOND `ExtensionsSettingsKey` was pushed on top of the first.
`AnimatedContent` then composed BOTH the outgoing and the incoming content
under the SAME `SaveableStateProvider(currentKey::class.simpleName)` key →
the crash.

**Fix** (`MainActivity.kt`): the function reads the LIVE backstack INSIDE
itself (`indexOfLast` — the list reference is stable across recompositions;
only the captured `currentKey` val was stale) and NEVER stacks a duplicate: an
existing `ExtensionsSettingsKey` anywhere in the backstack is REVEALED
(everything above it is popped) instead of pushed.

## B — The plugin ↔ repository linkage (the identity ladder)

New `CsPluginIdentity` (data/cloudstream/repo): an ORDERED matcher —

1. exact `internalName`;
2. `repoInternalName` (the repo's name captured at link time — new
   `CsPluginRecord` field);
3. download URL equality;
4. fileHash equality (`"sha256-<hex>"`, computed at manual import now);
5. NORMALIZED internalName (lowercase letters/digits);
6. NORMALIZED display-name (the manifest-name imports whose stems genuinely
   diverge).

`CloudstreamPluginManager.rebuildLists()` now: back-fills
`repoUrl/repoInternalName/url/fileHash` on every matched record (idempotent —
writes only on change), filters the Available list through the ladder (the
duplicate row disappears), and keys the update pills through it.
`importSharedPlugin` checks "already installed" + links the repo through the
same ladder (probe record + computed file hash). `installPlugin` is
linkage-aware: an identity-matched existing record updates IN PLACE (same
record name, same file path, trust preserved) — no second record/file. The
extensions section's Update pill resolves its target via the new
`availableUpdateTarget()` (the old exact-name lookup was a no-op for linked
manual imports).

## C — The Format-sources menu (×3 copies: ResolverSheet, PlayerSheets, CsSourceListUi)

A custom `PopupPositionProvider` places the menu's BOTTOM edge an 8dp gap
ABOVE the heading's TOP edge. The ModalBottomSheet's dialog window is
full-screen MATCH_PARENT and a Popup is a real TYPE_APPLICATION_SUB_PANEL
sub-window of it — the menu floats fully OUTSIDE the sheet, over the scrim,
never covering the heading (the round-21 BottomStart alignment sat the menu's
bottom ON the heading's bottom — it covered the "Episode N" text).

## D — The randomization triggers

- `SearchTabExitSignal` (search api module): MainActivity's bottom-nav
  `onSelect` marks the tab EXIT when leaving the search root tab; the search
  screen's fresh-composition entry reshuffles ONLY when
  `lastTabExit > lastShuffle`. In-memory by design — process death zeroes
  both stamps (no re-shuffle on cold reopen).
- The ON_RESUME reshuffle branch in `onScreenResume` is REMOVED (an
  activity-level resume is not "leaving the search page").
- The display arrangement (row shelf indexes + per-row item urls) is
  PERSISTED onto the browse snapshot (`CsBrowseDisplay`; a diskMutex
  serializes the cache's async writes) and RESTORED exactly on the
  cache-first path and on background refreshes (content swaps in place, the
  rows do not jump). Pull-to-refresh still invalidates → a genuinely fresh
  random arrangement.

## E — The smart shuffle

`smartShuffleSections` randomizes BOTH the row order and the item order,
under the cross-section constraint: a randomized section claims 4 urls (by
item url) for its top-4 that no earlier section claimed. A section that
cannot claim 4 unclaimed items (too few, or heavy overlap) is NOT randomized
at all — but its ORIGINAL top-4 still counts as claimed. `shelfIndex` rides
each row so the category subpages keep resolving.

## F — The chips underline

`fillMaxWidth()` inside a LazyRow item is a NO-OP (unbounded main-axis
width) → the 3dp underline collapsed to a 0-width sliver. The CategoryTab
Column now carries `.width(IntrinsicSize.Min)` — the bar resolves to the text
width — with the gap tightened to 1dp ("very close to the bottom of the
text").

## G — The library performance pass

- **H1**: the 8 bulk-mutation paths (category delete/rename/create/move,
  add/remove selected, delete selected, getCategoriesForSelected) run under
  `viewModelScope.launch(dispatchers.io)` — no more synchronous SQLite loops
  in click handlers.
- **H2**: `setSearchQuery`/`setSort*` only write StateFlows; ONE
  combined+debounced(200ms) collector in init runs `filterAndSort` on the
  Default dispatcher with a stale-emission guard (`runFiltersOffMain`).
- **M1**: the PTR threshold reads via `snapshotFlow { distanceFraction >= 1f
  }.distinctUntilChanged()` — no more whole-root recomposition per drag frame.
- **M2**: the shared-element gate is hoisted to the grid/list level (ONE
  prefs read per layout recomposition — was: per-cell koinInject + prefs
  read) and gated OFF while the layout scrolls (fling-recycled cells skip the
  SharedTransition registry churn).
- **M3**: the root's 34 `collectAsState()` calls split into leaf owners:
  the search-bar section, `LibraryCategorySection`, `LibraryCustomizeSheetHost`
  (19 sheet states — only composed while the sheet is open), the
  grid/list internals, `LibraryPullRefreshArea`, `LibraryDialogsHost`. The
  root keeps 6 structural states.
- **M4**: `loadPreferences()` (23 sync prefs reads) runs on Default BEFORE
  the first library load.

## H — The downloads tag

`(N Episodes Downloaded)` (singular-aware), same pill styling, ellipsis
guard for narrow rows.

## I — The cover focal zoom

A NON-CONSUMING centroid observer (`pointerInput` before `.transformable`)
tracks the fingers' average position; the transform callback folds it into
the pan: `t' = (c − C)·(1 − s'/s) + t·(s'/s)` — the image point under the
fingers stays under the fingers (top-right pinches zoom the top-right). Pan
while zoomed + the auto-reset on lift are unchanged; the clamp still keeps
the image covering the viewport.

## Execution order + verification

A → H → F → C → I → B → D+E → G → version 0.4.10/75 + the identity-ladder
unit tests (CsPluginIdentityTest) → brace-balance + import sweeps →
static review sub-agent → CI (the compiler of record) → tag + release.

Invariants: the aniyomi stack stays byte-untouched (the watch-sheet menu copy
is the shared preference's existing replication rule); the trust flow is
unchanged (updates preserve trust); the subpages' shelfIndex contract holds;
D-286/D-290/D-291/D-292 scroll fixes preserved.
