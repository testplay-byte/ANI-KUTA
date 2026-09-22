# 27 — Round 67 / D-555: the CS sheets find the middle height + the episode list becomes FOUR completely different layouts (the first draft's redesign)

**Round:** 67 (the v1.1.28 device round's verdict: the 0.60 sheet over-corrected, and the
first draft's three minor row variations are NOT what "customizable" meant)
**Status:** PLAN → IMPLEMENTED in the same round (the user asked for "proper planning,
proper understanding, thorough handling"). CI ledger: 2 Build runs (35679780981 RED — the
extraction forgot the sixth local `thumbnailUrl` while renaming the display-value set;
35680163304 the one-root-cause fix; the moved-code LOCAL-completeness audit joins the
D-554 import-completeness audit), release run follows the tag. The moved-code lesson
gains its sibling in lessons-learned.

---

## 1. The user's verdict on v1.1.28 — two workstreams

### 1a. The CS qualities/servers sheet height (D-554-A follow-up)

> "the cloud stream qualities and servers sheet is now not proper. It should not be
> 60% because it is way too smaller. I think we should go with a bit more taller
> percentage but smaller than the previous one."

History: v1.1.27 shipped 0.70 ("a little bit taller"); D-554-A dropped BOTH CS quality
surfaces to 0.60; the user says 0.60 is "way too smaller". The verdict points at the
MIDDLE: **0.65** — taller than the over-correction, still below the original 0.70
complaint. Applied to `CsLinksSheet` AND `CsResolveSheet` (parity, D-554-A's rule).
`CsEpisodesSheet` stays explicitly 0.70 (never complained about).

### 1b. The episode-list customization (D-554-B redesign)

> "the episode list, that is most definitely not good. It needs quite a lot of
> improvement … at the very top, I see the live preview, but I feel like in the live
> preview, it should only show a total of two episodes … the episodes should be given
> the proper thumbnail images too … the thumbnail images remain empty … the release
> date and all other stuff like that needs to be shown properly … switching between
> the three available layouts is definitely not good … I was hoping for a complete UI
> redesign on switching between each and every single one of them … provide me a total
> of four options and switching between each one of them will give a completely
> different UI design, a completely different layout, a completely different management
> system for them, a completely different look and feel to it, rather than just hiding
> some features like synopsis or maybe adjusting the shape a little bit."

Four concrete defects, four concrete directives:

| # | Defect in the first draft | The directive |
|---|---------------------------|---------------|
| 1 | Preview shows 3 sample rows | **Exactly 2** episodes in the live preview |
| 2 | Thumbnails render EMPTY | Root cause: `PREVIEW_THUMB_URI` is a **bare base64 payload with no `data:image/…;base64,` prefix** — Coil's DataUriFetcher never matches, `AsyncImage` shows nothing. Fix + REAL imagery (two generated anime stills, embedded as PROPER data URIs) |
| 3 | Release date + metadata "not shown properly" | Date gets real treatment in EVERY layout (spine label / chip / pill), samples always carry dates |
| 4 | DETAILED/COMPACT/MINIMAL are one row with sections hidden/shrunk | **FOUR completely different layouts** — different structure, different management, different look & feel |

## 2. The four layouts (completely different paradigms)

| Layout | Paradigm | Structure |
|--------|----------|-----------|
| **CLASSIC** | The familiar detailed rows (today's look, default) | Thumbnail left · title · date/audio pills · synopsis · download control. Swipe-to-toggle watched. All element toggles apply |
| **GRID** | Two-column poster wall (Netflix-style) | Cells: full-bleed 16:9 thumbnail, EP badge overlay, watched = grayscale + centered check badge + dim, watch-progress bar on the image's bottom edge, title below (2 lines), date + audio chips, compact state-icon download badge overlaid TopEnd. **Long-press toggles watched** (half-width cells can't swipe). No synopsis — the wall's identity |
| **TIMELINE** | Air-date schedule spine | Left rail (~64dp): vertical spine line + node (filled = watched, ring = unwatched) with the DATE stacked beneath it as the primary element (falls back to "EP n" when the date is absent/toggle-off); right: a compact card — title + EP tag + audio pills + small trailing thumbnail + download icon. Swipe-to-toggle kept (full width). No synopsis — the schedule's identity. Per-item vertical padding drops to 0 so the spine reads as ONE continuous line down the list |
| **CINEMA** | Full-bleed banner cards | Full-width 16:9 banner: thumbnail as the background, bottom gradient scrim, HUGE ghost episode number top-end (alpha ≈ 0.28), overlaid bottom-start: title (bold white) + translucent date/audio chips, watch-progress bar on the banner's bottom edge, watched = grayscale + dim + "WATCHED" chip, download control overlaid bottom-end as a translucent circle. Swipe-to-toggle kept. No synopsis |

Element-toggle semantics per layout (a layout that never renders a section cannot be
talked into rendering it — the D-554 truthfulness rule, now per-layout):

- `showSynopsis` → CLASSIC only (the other three never render it).
- `showDatePill` → "Release date": CLASSIC pill; GRID short date under the title;
  TIMELINE the node label (off → "EP n" labels); CINEMA the scrim chip.
- `showAudioPills` → CLASSIC pills; GRID/TIMELINE compact chips; CINEMA scrim chips.
- `showWatchProgress` → CLASSIC/GRID/CINEMA bars; TIMELINE keeps the bar on its card.
- `dimWatched` → every layout (grayscale + alpha on the imagery; TIMELINE also fills
  the node).
- `showDownloadControl` → CLASSIC full control; GRID/TIMELINE the compact badge;
  CINEMA the overlay control.

## 3. Migration: the old three styles die

`EpisodeListRowStyle` becomes `CLASSIC, GRID, TIMELINE, CINEMA`. `fromKey` maps the
legacy `DETAILED`/`COMPACT`/`MINIMAL` keys → `CLASSIC` (the redesign REPLACES the
variations — the user's own words). Unknown/null/blank → `CLASSIC`. The zero-prefs
experience stays byte-identical to pre-D-554 (CLASSIC renders the unchanged row).

## 4. The dispatcher + the list plumbing

- New file **`EpisodeLayouts.kt`** (same package): `EpisodeGridCell`,
  `EpisodeTimelineRow`, `EpisodeCinemaCard`, the compact `EpisodeDownloadBadge`
  (state-icon: arrow / progress ring+% / check / retry / pause / play / delete-dot),
  the `WatchedCheckBadge`, and the pure helpers (`timelineNodeLabel`,
  `ghostEpisodeNumber`, `formatShortDate`).
- **`EpisodeListEntry`** — the ONE dispatcher composable (same params as
  `EpisodeRow`); CLASSIC delegates to the unchanged `EpisodeRow` body. BOTH call
  sites (the details list AND the settings preview) go through it — one dispatch,
  zero drift.
- **DetailsScreen**: the episode `items` block branches — GRID iterates
  `chunked(2)` rendering a Row of two weighted cells (the outer LazyColumn stays
  the virtualizer — no nested grid), every other layout iterates per-episode; the
  per-episode Box padding drops to 0dp vertical for TIMELINE (spine continuity).
  The per-episode body (tag/downloadState/progress computation + the call) extracts
  into a local composable lambda shared by both branches — no duplication.

## 5. The settings page rework

- **Preview: EXACTLY 2 samples** (user spec): EP 1 fresh (progress 0.4) + EP 2
  watched (the dim/grayscale treatment) — both WITH proper thumbnails (the two
  generated stills), dates, and audio vocabulary. Rendered through
  `EpisodeListEntry` — flipping the layout visibly TRANSFORMS the preview
  (2 rows / 2 wall cells / 2 spine entries / 2 banners).
- The two embedded JPEG data URIs replace the broken constant (448×252 q64 —
  ~41KB total in the APK; the preview's CINEMA banners render near-full width so
  the imagery must survive ~340dp).
- **Layout section**: the 4-way segmented toggle, each option carrying its identity
  line ("Classic — the familiar detailed rows", "Grid — a two-column poster wall",
  "Timeline — a schedule spine by air date", "Cinema — full-bleed banner cards").
- **Elements section**: descriptions updated to the per-layout semantics
  (Synopsis → "Classic rows only", Date → "Shown in every layout where it fits").

## 6. Tests (EpisodeListStyleTest updated)

- fromKey: the 4 exact keys; DETAILED/COMPACT/MINIMAL → CLASSIC (migration);
  null/unknown/blank → CLASSIC.
- Defaults: `rowStyle == CLASSIC`.
- `pillsRowVisible` stays the CLASSIC-path algebra (tests re-anchored to CLASSIC).
- NEW pure locks: `ghostEpisodeNumber(1f) == "01"` (and ≥10 no-pad),
  `timelineNodeLabel` date-first fallback ("Jan 1" / "EP 3" / the toggle-off path),
  `formatShortDate(0) == ""` (the absent-date guard).

## 7. Blast radius

- `CsPlayerSheets.kt` / `CsResolveSheet.kt` — one fraction each (0.60 → 0.65).
- `EpisodeRow.kt` — enum redesign + the dispatcher (the CLASSIC body unchanged).
- `EpisodeLayouts.kt` — NEW.
- `DetailsScreen.kt` — the episode items block only (lines ~1306–1419).
- `EpisodeListSettingsScreen.kt` — preview + selector rework.
- `EpisodeListStyleTest.kt` — re-anchored + new locks.
- Untouched: prefs keys (the stored values migrate through fromKey), the download
  control's 7-state contract, the swipe algebra (reused), season/organize systems,
  the sheets' scroll structure.


---

## §8 Completion record (round 67)

Implemented EXACTLY as planned in §1–§7, with one compile repair en route:

- The extraction of `rememberEpisodeDisplayData` renamed the display-value set but the
  classic row's thumbnail block still read the old `thumbnailUrl` local — the CI log's
  two "Unresolved reference" lines, one root cause, fixed in one commit. LESSON: the
  moved-code audit must diff the CONSUMED local names against the RE-DECLARED ones
  (imports alone don't catch a forgotten local).
- The sandbox test suite grew to 13 locks and executed GREEN against the byte-exact
  extracted production declarations (kotlinc 2.0.20 + junit, re-downloaded — /tmp had
  been cleaned between rounds).
- The two preview thumbnails were GENERATED (image-generation, 1344×768), cropped to
  16:9, downscaled to 448×252 q64 JPEG, and embedded as PROPER `data:image/jpeg;base64,`
  URIs (~41KB total; the CINEMA preview renders near-full width so 320px was rejected).
- The GRID preview pairs the two samples side-by-side (mirroring the real wall's shape);
  the other three layouts stack them; the preview cap rose 320→380dp for the two CINEMA
  banners (the options below stay reachable — internal scroll, D-525 stationary rule).
- The element-toggle descriptions on the settings page now state the per-layout
  semantics ("Classic rows only" for the synopsis, "Shown in every layout where it
  fits" for the release date).
