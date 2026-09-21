# 26 — Round 66 / D-554: the CS quality sheets get a calmer max height + the episode-list customization FIRST DRAFT (the dedicated settings page with the live preview)

**Round:** 66 (the v1.1.27 device round's verdict + the next feature request)
**Status:** PLAN → IMPLEMENTED in the same round (the user asked for "proper planning, then implementation, no skipping").

---

## 1. The user's verdict on v1.1.27 (D-553) — what is now CONFIRMED WORKING

The user's device round on v1.1.27:

- The resolve sheet's top area ("resolve till this properly at the top") — good.
- The audio versions in the minimized/short-form view — "proper and clean" (D-553's
  `shortAudioLabel` landed as designed).
- **The 480p pick NOW PLAYS AT 480p and the UI says so** — "after selecting 480p
  version, the video was apparently playing in 480p and it was properly showing me
  the video as being played in 480p". This CLOSES the round-65 issue registry:
  ISSUE-65-B was the unobservable-truth problem (the D-553 height memory + honest
  markers fixed the actual defect; the logcat digest's "second READY" anomaly never
  materialized as a user-visible defect — CLOSED as observed-but-not-reproduced).
- The in-player qualities/servers sheet — "it properly showed the appropriate audio
  version as selected and the appropriate currently playing resolution as selected"
  (the D-553 Now-playing line + the ONE lit chip work as designed). ISSUE-65-C CLOSED.
- The collapsed short names — no longer truncating. ISSUE-65-A CLOSED.

**ALL THREE round-65 issues are CLOSED by the user's own report.**

## 2. The ONE remaining complaint — the bottom sheet is too tall

> "the bottom up menu was a little bit taller in terms of its height. So I would like
> you to adjust it dynamically in such a way that Its maximum height gets reduced
> properly and smoothly for this kind of experience. Like when it is in cloud stream
> and shows the qualities and servers section."

### Diagnosis (code-verified this round)

Both CS sheets are already content-fitting with a **screen-fraction cap**:

- `CsPlayerSheets.kt` — `csSheetMaxHeight(fraction = 0.70f)` (line ~94) applied via
  `Modifier.heightIn(max = csSheetMaxHeight())` on the sheet's scrollable body
  (line ~239). Used by `CsLinksSheet` (0.70) AND `CsEpisodesSheet` (0.70, shared
  default).
- `CsResolveSheet.kt` — duplicated local math `screenHeight * 0.70f` (line ~366)
  → `heightIn(max = maxSheetHeight)` (line ~389).

material3 is deliberately pinned at 1.3.1 (D-322) — `ModalBottomSheet` there has NO
`sheetMaxHeight` parameter (that only exists in the 1.4.x rework), so the cap must
stay content-side — which is exactly the app-wide convention
(`DOCUMENTATION/DESIGN-SYSTEM/01-navigation-and-sheets.md` §4: "all bottom-up sheets
cap at 70%"; the subs sheet already dropped to 0.55 by user complaint — Task 57 P6a,
and `CsSubtitleSettingsSheet` sits at 0.65). **The fix is a fraction change, not new
machinery.** "Dynamically" is already true (the sheet is only as tall as its content,
the cap binds only on overflow); "smoothly" is already true (the accordion's
`expandVertically` animates growth under the cap; the scroll absorbs the rest).

### THE FIX (D-554-A)

- `CsLinksSheet` → `heightIn(max = csSheetMaxHeight(0.60f))` — the reported sheet.
- `CsResolveSheet` → `0.70f → 0.60f` — the SAME "qualities and servers" experience
  (the user's phrase covers both surfaces; parity is the point).
- `CsEpisodesSheet` gets an EXPLICIT `csSheetMaxHeight(0.70f)` so the shared default
  change cannot silently drag it down (the episodes list is a browsing surface, not
  the complained-about one).
- The `csSheetMaxHeight` default itself stays 0.70 (the app-wide standard; only the
  CS quality surfaces opt into 0.60).
- `DESIGN-SYSTEM/01-navigation-and-sheets.md` gains the exception note (0.60 for the
  two CS quality sheets — user spec round 66).

## 3. The FEATURE — episode-list customization, first draft

> "the next improvements … in the detail page and that is going to be the UI of the
> episodes list … giving the user customise ability giving him some option to select
> between what he want and various other option … implement the first draft please
> with an actual live preview at the top in the settings … a dedicated page just like
> how there is for the poster banner notification."

### The pattern to follow (research-verified)

`NotificationPosterSettingsScreen.kt` (app/settings, D-477/D-481/D-525) is THE
exemplar: a dedicated full-screen page whose top region is a **stationary live
preview** rendering the SAME production renderer the real feature uses ("what you
tune is exactly what you'll get" — D-481), with the options in a `LazyColumn` of
cards below (8dp single gutter, one-line descriptions D-532, `SegmentedToggle` blocks
+ switch rows). Persistence = the Koin-singleton prefs class written straight from
the screen; consumption = reactive `Preference.changes` → `collectAsState`.

The plug-in point ALREADY EXISTS: `EpisodeSettingsKey` (MainActivity.kt:337) renders
a `PlaceholderScreen` ("More episode options in a future phase") and is linked from
Appearance → "Episode List" → "Episode settings" row (subtitle "Display, layout, and
metadata"). The new page replaces the placeholder — navigation wiring is a swap, not
new plumbing.

The persistence layer ALREADY EXISTS: `EpisodeListPreferences` (D-230, reactive
`Preference<T>` over `pref_episode_list_*` keys, Koin-registered). It extends.

### First-draft SCOPE (deliberately curated — D-523 lesson: "we should not give the users that much customizability")

**A. Layout style (3-way segmented — the Library `displayMode` prior art):**

| Style | Thumbnail | Synopsis | Date pill | Audio pills | Download control |
|---|---|---|---|---|---|
| `DETAILED` (default = today) | 120×68dp (when available) | up to 2 lines (when present) | yes | yes | yes (synopsis bottom-right / pills row) |
| `COMPACT` | 84×48dp | **never** | yes | yes | yes (pills row) |
| `MINIMAL` | **never** (number disc path) | **never** | **never** | yes | yes (pills row) |

**B. Element toggles (all default ON = today's behavior):**

- Show synopsis (honored in DETAILED only — the other styles are defined by its absence)
- Show date pill (honored in DETAILED + COMPACT)
- Show audio pills (SUB · DUB · HSUB)
- Show watch-progress bar (the thumbnail's bottom bar; the download bar overlay is
  transient state feedback, not decoration — stays)
- Dim watched episodes (the alpha 0.5 + grayscale treatment)
- Show download buttons (the per-row `EpisodeDownloadControl`)

NOT in the first draft (recorded for later rounds): grid layouts, title line counts,
per-show overrides, applying to the Watch-screen clone (D-119) or the CS episodes
sheet, gesture customization (swipe threshold), filler/score/runtime badges
(model fields exist but never rendered — a later round's feature).

### Architecture (the D-481 doctrine applied to rows)

1. **`EpisodeListPreferences` += 7 reactive prefs** (`rowStyle` string
   DETAILED/COMPACT/MINIMAL + 6 booleans) — plain-string convention of the file.
2. **`EpisodeRow` MOVES out of DetailsScreen.kt** (4,243 lines → the row + its
   exclusive helpers live in `feature/anime-details/impl/.../EpisodeRow.kt`, same
   package, PUBLIC so the :app preview can call it). Moved: `EpisodeRow` (with the
   new `style` param), `EpisodeTag`, `AudioAvailability`,
   `parseAudioAvailability`, `formatEpisodeNumber`, `formatDate`. The dead
   `DownloadEpisodeButton` (zero callers, superseded by `EpisodeDownloadControl`)
   is deleted, not carried. `DetailsScreen` keeps `subDubEpisodeTag` +
   `mergeSubDubEpisodeRows` (used by list shaping, not the row).
3. **`EpisodeListDisplayStyle`** data class (in the new file): the 7 knobs with
   defaults == today's behavior. `EpisodeRow` computes effective flags from
   (style × content) at the top — the body stays the familiar shape with gates.
4. **`DetailsScreen`** collects the prefs ONCE at screen level (the established
   pattern at 280-317 — ONE subscription set for the whole list, not 7×N rows) and
   passes `style =` to each `EpisodeRow` call.
5. **`EpisodeListSettingsScreen`** (app/settings, new file): the poster-page
   scaffold — stationary preview top (3 static sample rows rendered through the
   SAME public `EpisodeRow`), options `LazyColumn` below (Layout card =
   `SegmentedOptionBlock` + 3-way `SegmentedToggle`; Elements card = switch rows;
   bottom hint pointing to the Episodes sheet for sort/filter/grouping). All
   toggles write STRAIGHT to `EpisodeListPreferences`; the preview's style is
   collected from the SAME prefs (`collectAsState`) — one source of truth, zero
   drift, D-481 honored literally.
6. **Preview samples** carry a tiny bundled data-URI PNG (generated at build time
   of this round, embedded as a const) so the thumbnail slot shows real pixels
   offline — the grayscale/dim/compact-size effects are visible without network.
   Callbacks are no-ops; download state = `NotDownloaded`; the rows are inert
   samples.
7. **Nav swap**: `EpisodeSettingsKey` case → `EpisodeListSettingsScreen(onBack)`;
   the Appearance row becomes title "Episode list" / subtitle "Layout, elements,
   and live preview" (the poster-row parallel: "Notification poster" / "Templates
   + live preview"). The key object name stays (zero churn).

### Tests

`feature/anime-details/impl` gains its FIRST test source set (one-line
`testImplementation(libs.junit)` mirroring cs-watch/impl) + `EpisodeListStyleTest`:
the lenient `fromKey` (null/unknown/case → DETAILED — the D-529 seeding lesson),
the display-style defaults, and the pure pills-row visibility algebra extracted as
a small function so the (style × content) gating is locked without Compose.

### Blast radius

- `CsPlayerSheets.kt` + `CsResolveSheet.kt` (fractions only)
- `EpisodeListPreferences.kt` (+7 prefs)
- NEW `EpisodeRow.kt` (moved code + style param + the style types)
- `DetailsScreen.kt` (block removal + prefs collection + style pass-through)
- NEW `EpisodeListSettingsScreen.kt`
- `MainActivity.kt` (one case swap) + `AppearanceScreen.kt` (row text)
- `feature/anime-details/impl/build.gradle.kts` (+test deps) + NEW test file
- Docs: DESIGN-SYSTEM sheets page, handoff §22, decisions D-554, changelog,
  progress, knowledge/ui-customization.md
- UNTOUCHED: RAW mode, the resolve/play/download pipelines, the watch-screen
  clone (D-119), the CS episodes sheet, the EpisodeListSettingsSheet (sort/filter/
  grouping — it coexists; the new page is row APPEARANCE, the sheet is list
  SHAPING), every other sheet's height.

### CI / release discipline

Feature push → build run 1 → RELEASE-FIRST `release/1.1.28` (bump 1.1.28/10128,
feature line stays 1.1.20/10120 per D-430) → tag v1.1.28 (SUBJECT ≤256 chars — the
round-65 lesson) → release run 2 (≤2 budget). Verify via API, delete
`release/1.1.27` after its post-tag delta is verified docs-only, worklog + ntfy.

---

## 4. Implementation completion record (the resumed session)

The context window interrupted mid-implementation; the resumed session verified every carried piece against this plan BEFORE building on it (line-by-line diff reads), then completed the remaining blast radius:

**Carried from the interrupted session (verified, kept):**
- `EpisodeListPreferences.kt` — the 7 prefs + keys (§3.1).
- `EpisodeRow.kt` (NEW, 709 lines) — the extracted row + `EpisodeListRowStyle` + `EpisodeListDisplayStyle` + `pillsRowVisible` (§3.2/3.3). Dependencies verified same-package public (`EpisodeDownloadControl`, `EpisodeDownloadState`, `EpisodeDisplayResolver`).
- `DetailsScreen.kt` — the block removed (−549 lines), the prefs collected ONCE at screen level, `style =` passed at the row call site; zero leftover references to the moved/deleted privates.
- `CsPlayerSheets.kt` / `CsResolveSheet.kt` — the D-554-A fractions (0.60 / 0.60 / explicit 0.70).

**Completed in the resumed session:**
- `EpisodeListSettingsScreen.kt` (NEW, app/settings) — the D-477/D-481/D-525 scaffold (§3.5): CollapsingHeader + stationary "Live preview" card (rows capped at 320dp with an internal scroll so the options stay reachable on small screens — internal scrolling is not page scrolling; the preview never leaves the stage) over the options LazyColumn (Layout 3-way toggle / Elements 6 switch rows / the honest "More" pointer). The preview's style is collected from the SAME prefs the toggles write (`.set()` — see the API lesson below); `fromKey` seeds the toggle segment.
- The preview samples (§3.6): three remembered `SEpisode.create()` rows — full (thumbnail + synopsis + SUB/DUB pills + 40% watch-progress), watched (HSUB pills, the dim+grayscale treatment), bare (no thumbnail → number disc, no scanlator → no pills). Thumbnail = a generated 120×68 sunset PNG (3.5 KB) embedded as a data-URI const — Coil 3's data-URI fetcher renders it offline.
- `MainActivity.kt`: `EpisodeSettingsKey` → `EpisodeListSettingsScreen(onBack = pop)`; the `PlaceholderScreen` composable DELETED (zero callers = dead code).
- `AppearanceScreen.kt`: the row → "Episode list" / "Layout, elements, and live preview".
- `feature/anime-details/impl/build.gradle.kts`: `testImplementation(libs.junit)` (the module's FIRST test source set) + `EpisodeListStyleTest` (11 locks: fromKey exact/lenient/fallback, the defaults, the pillsRowVisible algebra) — **11/11 EXECUTED green in the sandbox** (kotlinc 2.0.20 + junit 4.13.2 on the extracted pure declarations + the test file; kotlin-stdlib required on the runtime classpath).
- Pre-push verification: byte-level corruption scan (0×U+FFFD) + brace/paren balance on all 7 touched files; the review caught the `Preference.set()` vs `=` API-shape error before push (see the round-66 lesson).
- Docs: this record's §4; the DESIGN-SYSTEM sheets page (the 0.60 exception); knowledge/ui-customization.md layer 6; decisions D-554; changelog/progress round-66 sections; handoff §22; 2 lessons.
