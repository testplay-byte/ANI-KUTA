# 07 — Device-Round Fixes Plan (Task 56 / round 16)

The v0.4.3 device-round feedback — five findings, each root-caused in code,
each with a bounded fix. Invariants from round 15 carry over unchanged:
**aniyomi stack = additive-only**, **CI is the compiler of record**, **no
plugin classes past `data:cloudstream`**.

## The findings (user, 2026-08-31)

| # | Finding | Root cause |
|---|---------|-----------|
| F1 | Clicking a CS episode resolves, then **auto-opens the video** for some providers | `CsResolveSheet` has two auto-select paths that call `pick()` → `onPlay()`: a remembered-server auto-select (fires while streams arrive, once any server was remembered) and a single-link auto-select (fires on `Completed` with exactly 1 link). "Some plugins" = exactly those two conditions. |
| F2 | Formatted quality chips are not highest-left | CS `groupServers` already sorts `sortedByDescending{quality}`, but `Unknown(400)` lands left of 144p and `Auto(0)` placement is value-driven; the **aniyomi** accordion (ResolverSheet + watch QualitySheet) has **no sort at all** — chips render in extension emission order. |
| F3a | Episode names keep the "(Sub)"/"(Dub)" suffix when the flavors are already separated | The bridge appends the tag to `SEpisode.name` (round-12 contract); round 15 added the switcher but never stripped the display names. |
| F3b | Switching to Dub continues the numbering (13–24) instead of restarting at 1 | **`EpisodeListNormalizer` guarantees globally-unique numbers**: per-flavor numbering (sub 1-12 + dub 1-12) fails the allDistinct check → the whole list renumbers 1..24 in raw order (sub first) — the second flavor ALWAYS continues. This is by design for the cache/metadata/progress keys (all keyed on episode_number), so the fix must be **display-layer ordinals**, not identity renumbering. |
| F4 | COMBINED mode shows both rows (12+10 = 22) instead of merged pairs | `mergeSubDubEpisodeRows` / `CsSubDubSiblings.mergeSiblings` / `handlesFor` all pair siblings **by episode_number equality** — with globally-continuing numbers, dub #5 (stored as 17) never matches sub #5. The round-15 merge logic was correct; the pairing key was wrong. |
| F5 | Crash: `IllegalArgumentException: Key "Default|Default|https://…mpd" was already used` | The aniyomi RAW lists key rows by `"${server.name}|${av.label}|${it.url}"` (ResolverSheet.kt:553, PlayerSheets.kt:488). An extension emitted multiple videos with the same URL under one server+audio group (a multi-quality DASH manifest) → duplicate LazyColumn keys → crash. The CS raw list (`key = { it.url }`) has the same latent risk. |

## The fixes (modular, independently revertable)

### M1 — `CsSubDubSiblings` gains flavor ordinals (API, pure)
- `flavorOrdinals(episodes): Map<String, Int>` — per-tag 1..N (ordered by
  episode_number, then list position; keyed by the data handle). Untagged
  rows are absent (callers fall back to the raw number).
- `mergeSiblings` + `handlesFor` re-pair via ordinals (sub-ordinal i ↔
  dub-ordinal i) instead of episode_number equality.
- Unit tests: ordinal derivation, merge under global numbering, combined
  handle pairing, untagged passthrough (existing tests updated).

### M2 — `CsResolveSheet`: no auto-open (F1)
- Both auto-select paths REMOVED (remembered-server + single-link). The sheet
  always presents the list; the user picks; `pick()` stays the single entry.
- The remembered server still auto-EXPANDS its accordion (a hint, not a
  decision) — `preferredServer` wiring unchanged.
- The in-player `CsWatchViewModel.autoStart` (streaming-into-player on
  unseeded entry / episode switch) stays: that is in-player continuity, not
  the entry the user described. The sheet is the only unseeded entry point,
  so entry auto-open is dead by construction.

### M3 — `CsSourceListUi`: chip order + raw keys (F2/F5, CS side)
- `groupServers`: quality rank sort — real heights descending (2160 → 144),
  then `Unknown(400)`, then `Auto(0)` at the far right ("any other options"),
  stable within ties.
- `CsRawLinkList`: `itemsIndexed` keys `"${url}#${index}"` (the resolver
  dedups by URL, the merged list dedups by URL — the suffix is defense in
  depth, zero cost).

### M4 — CS episode surfaces: ordinals + stripped names (F3/F4)
- `CsWatchPage`: `episodeRows` → display rows carry the per-flavor ordinal +
  tag-stripped name; the "Episodes" badge counts the displayed list.
- `CsEpisodeListRow` + `CsEpisodesSheet` rows: display-number override
  ("EP 5" shows the ordinal for tagged rows), stripped names.
- `CsWatchPage` "Currently playing episode N": ordinal for tagged episodes.

### M5 — `DetailsScreen` (SEpisode twin, gated on `isLinkedSourceCloudStream()`)
- `subDubFlavorOrdinals()` — the SEpisode twin of M1 (anime-details cannot
  import `feature:cs-watch:api`; the replication rule).
- `mergeSubDubEpisodeRows` pairs via ordinals; the merged row's name is
  already stripped; the display rows in ALL subDub modes get tag-stripped
  names (the switcher + the audio pill carry the flavor) and the row's
  `episodeTag` number shows the ordinal.
- Identity (`episode_number`, `url`, `scanlator`) NEVER changes — watch
  progress, the episode cache, metadata maps and the CS hand-off all keep
  their keys. Only the rendered badge + name change.

### M6 — aniyomi sheets: chip order + raw keys (F2/F5, additive display-layer)
- `ResolverSheet` (anime-details) + `PlayerSheets` (watch): sort each audio
  version's videos by parsed height descending, non-numeric labels ("Default")
  last — a display-layer sort inside files round 15 already owns; the
  resolver core + picking flow untouched.
- Both raw lists: `itemsIndexed` with index-suffixed keys — the F5 crash fix.
- Aniyomi SEPARATE/COMBINED behavior untouched (there is no sub/dub switcher
  on the aniyomi stack — that's CS-scoped).

### M7 — ship
- Tests (M1 + groupServers order + aniyomi sort helper), docs
  (decisions/lessons/changelog/progress/SESSION), version 0.4.4/69, CI to
  green, tag + release.

## What deliberately does NOT change
- `EpisodeListNormalizer` (global uniqueness = the identity contract for
  cache/metadata/progress; the display layer compensates).
- The bridge's episode list contract (names still tagged + scanlator mirror —
  the tag IS the flavor signal for every layer; only RENDERED text strips it).
- `CsWatchViewModel.autoStart` (in-player continuity) and the aniyomi
  auto-play pipeline (old system, user-confirmed working).
- The aniyomi `VideoResolver` core (no sorting/dedup changes — display only).

---

## As-built notes (Task 56, v0.4.4/69)

All seven modules implemented exactly as planned, with four refinements the
implementation surfaced:

1. **Flavor continuity beyond the fix scope (M2 note):** `CsWatchViewModel`
   `nextEpisode`/`prevEpisode` walk WITHIN the current flavor (the current
   LINK's `audioTag` first — a COMBINED-mode dub pick, since the merged row is
   always the Sub handle; the row's tag second — SEPARATE mode), and
   `autoStart` prefers the target row's flavor pool before falling back to
   best-quality. Auto-advance from sub-12 no longer jumps into dub-13.
2. **The CS metadata hand-off (M5):** the `csMetaStr` build now feeds
   tag-stripped episode copies so the CS watch page's currently-playing TITLE
   never carries the flavor tag (the scanlator field still rides the raw
   episodes — it drives the page's Sub/Dub pill).
3. **Display copies keep identity (M5):** the details rows render
   `SEpisode.create().copyFrom(row)` copies with only `name` stripped —
   url/episode_number/scanlator ride untouched, so clicks, downloads,
   watched-state keys and the CS hand-off are byte-identical to the
   pre-display rows.
4. **M7 ship:** v0.4.4/69; tests: +5 ordinal locks (CsSubDubSiblingsTest),
   +2 rank locks (CsSourceListUiTest); the pure logic machine-verified via a
   Python twin before push; the aniyomi changes stay display-layer-only
   (chip sort + raw-list keys) inside the two files round 15 already owned.
