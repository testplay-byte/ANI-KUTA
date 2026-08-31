# 08 — Round 17: Progress Linking, Overlay Subtitles, Debug Copy (plan)

> Task 57 / round 17, planned 2026-08-31 after the v0.4.4 device round.
> Working branch `streaming/CLOUDSTREAM-V2`. Target version **0.4.5/70**.

## 0. The v0.4.4 device round — user verdict

**Confirmed working (must not regress):**
- Sub/Dub switching + per-flavor numbering (the round-16 ordinal work) ✅
- (Sub)/(Dub) name-tag stripping in episode lists ✅
- Auto-open removal from the resolve sheet ✅ (explicitly praised)
- Formatted/unformatted resolve-list toggle ✅
- COMBINED dual-resolve fundamentally works (streams present + selectable) ✅
- Old (aniyomi) system untouched ✅

**Findings to fix this round:**

| # | Finding | Root cause (verified in code) |
|---|---------|-------------------------------|
| P1 | Watching sub-EP-5 at ~80% shows no progress on dub-EP-5 — sub/dub of the SAME episode must be ONE progress identity | progress keys are `mainId\|%05d(rawNumber)`; the normalizer's unique numbers make dub raw ≠ sub raw (`CsWatchViewModel.episodeKey`, `DetailsScreen:1153`, `DetailsViewModel` key builders) |
| P2 | COMBINED rows lost the sub/dub tags entirely | `mergeSubDubEpisodeRows` sets `scanlator = null` → the details row's audio pills (parsed from scanlator+name) render nothing; the CS watch page + episodes sheet merged rows have no flavor signal either |
| P3 | COMBINED resolve "occasionally" misses one flavor's streams | the dual-handle merge dedups by URL only — same-URL links from the 2nd flavor (sub+dub share the encode) are dropped, taking that flavor's whole section with them (`CsWatchViewModel` + `CsResolveSheet`) |
| P4 | Debug tooling wanted: a COPY button on resolved-video rows (copies full names + details), gated by settings, OFF by default; Settings → bottom Debug section → dedicated Debug page (bubble / sources / copy options) | new feature |
| P5 | Formatting engine should get smarter at server/audio/resolution detection; the copy button feeds real provider data back for the NEXT tuning round | `CsAudioTag.parse` + `serverNameOf` vocabulary |
| P6a | Subtitle bottom sheet too tall | `csSheetMaxHeight()` = 70% screen for the subs sheet |
| P6b | "Embedded in video" sub click → player error → retry plays but 0 embedded subs | engine text-override has no error guard; after error/retry the new link may carry no embedded tracks |
| P6c | "From provider (needs reload)" subs reload the video and then DON'T show | the whole reload/reattach path is upstream's; user wants OUR renderer: fetch → parse → overlay, no reload |
| P6d | CS subtitle settings look nothing like the aniyomi extension's sheet | `CsSubtitleSettingsSheet` lacks Scale/Delay/Font rows, tap-to-edit keypads, sticky header |
| P7 | Player seeking back "doesn't load back data" | default ExoPlayer back-buffer is 0 — every backward seek refetches; plus seekRelative reads the laggy composed state |
| P8 | General: player needs proper safe checks/optimization | audit pass |

## 1. P1 — linked sub/dub progress identity (the "one episode" contract)

**Design:** for sub/dub-tagged lists the PROGRESS identity is the flavor ORDINAL
(sub-5 ↔ dub-5 both identity 5). Untagged lists keep raw numbers (ordinal map
is empty there — sub-only lists already have ordinal == raw, so nothing moves).

Sites (all CS-gated, aniyomi lists guaranteed byte-identical):
1. `CsWatchViewModel`: `saveProgress` + `lookupResumePositionMs` key via
   `progressEpisodeNumber()` = `flavorOrdinals[episodeData] ?: rawNumber`;
   rating key exposure for the watch page; expose `episodeProgress: Map<String,
   WatchProgress>` (from `observeByMainId`) so watch-page rows can render
   watched/progress.
2. `CsWatchPage`: `CsEpisodeListRow` gains watched dimming + thin progress bar
   (the details `EpisodeRow` language); rating uses the linked key.
3. `DetailsScreen` (line ~1153): row lookup key + `onToggleWatched` key use the
   ordinal when `subDubTagged`.
4. `DetailsViewModel`: `markAllWatched` / markPrevious / markSeries /
   `syncLocalProgressFromTracker` key lists built through ONE new module-local
   helper `csProgressKeys(mid, episodes)` (CS-bridged gate + ordinals).

**Why safe:** `%05d` of the ordinal is still unique per mainId; the DB has no
FK on the number; history/continue-watching read via `observeByMainId` +
highest-watched stays ≤ N.

## 2. P2 — COMBINED flavor tags

- Details: merged row's `scanlator = "Sub/Dub"` → `parseAudioAvailability`
  finds both → the existing SUB·DUB pill renders (zero EpisodeRow changes —
  the aniyomi component stays byte-untouched; the field is CS-gated data).
- CS watch page + episodes sheet: `CsSimpleEpisode` gains a render-only
  `flavors: List<String>` (default empty); `mergeSiblings` fills
  `["SUB","DUB"]`; rows render small SUB/DUB pills next to the EP tag.

## 3. P3 — dual-resolve merge fix

Dedup key becomes `url + '\u0000' + audioLabel` in BOTH merge sites (VM +
resolve sheet). Same-URL different-flavor links (dual-audio encode shared by
both handles) now BOTH survive and land in their flavor groups. Also
`retryResolution` invalidates the sibling handle's cache too.

## 4. P4 — Debug page + copy button

- `core/preferences/DebugPreferences` (both flags default **false** — user
  spec "by default it will be turned off").
- `DebugSettingsKey` nav + `DebugSettingsScreen` (app/settings): Show debug
  bubble toggle + "Show sources" + "Copy button" rows, aniyomi settings-row
  language.
- Settings screen: the old inline debug row becomes a "Debug options" row
  leading to the page (bottom of the list).
- Resolve lists (`CsServerAccordion` cards' chips + `CsRawLinkList` rows):
  when the copy flag is ON, a copy icon per row copies that link's full
  details; a header-level copy action copies the WHOLE list report; the
  sources flag appends the raw URL/type line to rows.
- Report format (`CsResolveDebugReport`): provider, anime, episode + one line
  per link: name / derived server / audio label / quality(+rank) / type /
  referer / header keys / url. Plain text, clipboard + toast.

## 5. P6 — the overlay subtitle system (the big one)

**Architecture (user directive: "use our own subtitle system… overlay… like
the old one"):**

```
CsSubtitle (url) ──fetch──> bytes ──parse──> List<CsCue> ──render──> Compose overlay
      │                        │                   │                     │
 CsSubtitleFetcher        (headers, UA,       CsSubtitleParser      CsSubtitleOverlay
 (cs-watch:impl)           bounded, sniff)    (core:cs-player,       (positionMs from engine
                                                              pure + tested)        state, style+delay)
```

- **Engine drops sidecar merging entirely** (`SingleSampleMediaSource` path
  removed; external audio merge stays). Text tracks = embedded-only from now
  on — one renderer per subtitle class, no re-prepare, no reload, no crash
  path from subtitle attachment.
- **`CsSubtitleParser`** (core:cs-player, pure): SRT, WebVTT, basic
  ASS/SSA Dialogue events (tag-stripped). Cue model
  `CsCue(startMs, endMs, lines)`.
- **`CsSubtitleFetcher`**: OkHttp GET with the sub's headers, 15 s budget,
  sniffed-mime-aware parser pick, runCatching → typed failure surfaced in the
  sheet row.
- **`CsSubtitleOverlay`**: renders the active cue (first match at
  positionMs + delayMs) with the full `CsSubtitleStyle` mapping (size ×
  scale, border/shadow edge, colors, bold/italic, position fraction) in
  Compose on top of the PlayerView, below the controls. 100 ms ticker gives
  smooth-enough cue flips.
- **Sheet:** "From provider" rows = ALL `uiState.subtitles` (select = overlay
  on; loading + error states on the row); "Embedded in video" rows unchanged
  (engine tracks); "Off" clears BOTH; the "needs reload" section + the
  reattach flow are REMOVED. Sheet height cap 0.55.
- **Auto-select (MPV slang parity):** on play start, if no selection exists,
  pick the preferred-language provider sub and fetch it in the background.
- **Settings parity:** `CsSubtitleSettingsSheet` reworked to the aniyomi
  sheet's structure — sticky header, Typography (font row [Android
  typefaces], font size, scale, border size, bold, italic), Colors,
  Position & Misc (position, shadow offset, delay stepper) + tap-to-edit
  numeric keypads. All values write the SAME `PlayerPreferences` (one
  setting set, both players react); the overlay + Media3 view both re-style
  live. `subtitleFontScale`, `subtitlesDelay` now have REAL effects in the
  CS stack (overlay scale/cue shift).

## 6. P7/P8 — player hardening

- `DefaultLoadControl` with a **30 s retained back-buffer** (seek-back within
  30 s serves from memory — the "back data not loaded" fix) + sane buffer
  durations.
- `seekRelative` reads `engine.state.value` (live) not the composed snapshot.
- Embedded text/audio overrides: try-guard + revert-on-playback-error +
  single clean retry (the P6b "Couldn't play the video" path).
- `seekTo`: duration guard already present; add seekable-position clamp for
  live streams; guard negative positions.
- Audit: ticker cadence, release path, reset path, error diagnostics.

## 7. Test plan (pure logic, CI-run)

- `CsSubtitleParserTest` — SRT/VTT/ASS shapes, timestamps, bounds, broken
  input resilience.
- `CsSubDubSiblingsTest` — flavors on merged rows; progress-key linking
  (ordinal == raw fallback).
- `CsSourceListUiTest` — (url,audioLabel) merge dedup; serverNameOf bracket
  vocabulary; report builder format.
- `CsAudioTagTest` — bracketed tags, new vocabulary.

## 8. Out of scope (next session)

Download functionality + polish (user deferred), DASH exposure, plugin
metadata surface.

---

## As-built notes (fill after implementation)

- (to be written at ship time)

## As-built notes (Task 57 / round 17 — shipped as v0.4.5/70)

**P1 — linked progress identity:** `CsWatchViewModel` computes
`flavorOrdinals` per episode list and writes/reads progress through
`progressEpisodeKey()` (ordinal, raw fallback); a new `episodeProgress`
StateFlow (observeByMainId) feeds the watch page's rows (watched dimming +
thin progress bar, `CsEpisodeListRow.isWatched/progressFraction`). The watch
page rating read/write uses the ordinal key (`ratingEpisodeKey`). DetailsScreen
row lookup + `onToggleWatched` embed the ordinal (`identityNum`). DetailsViewModel
routes updateTrackProgress (mark+unmark), markAllPreviousWatched,
markSeriesAsWatched, syncLocalProgressFromTracker through module-local
`csEpisodeIdentities`/`csProgressKeys` (`.distinct()` collapses sub+dub pairs;
`isLinkedSourceCloudStream()` gate keeps aniyomi lists byte-identical).

**P2 — COMBINED flavor tags:** `CsSimpleEpisode.flavors` (render-only);
`mergeSiblings` fills the pair's actual two tags (primary-first). Watch page
rows + episodes-sheet rows render SUB/DUB pills; details merged rows carry
`scanlator = "Sub/Dub"` → the existing SUB·DUB audio pill (EpisodeRow consumes
scanlator ONLY through parseAudioAvailability — verified, no literal chip).

**P3 — dual-resolve reliability:** both merge sites (VM + resolve sheet) dedup
by `url + '\u0000' + audioLabel`; `retryResolution` invalidates BOTH handles'
caches. Same-URL links from the 2nd flavor now survive into their own groups.

**P4/P5 — debug toolkit + smarter parsing:** `DebugPreferences` (both flags
default OFF) + the dedicated Debug settings page (Settings → Debug options;
bubble row debug-build-gated so release shows no dangling header). Resolve
lists: per-chip/row copy icons + raw URL/type lines + header-level report
action, all live-gated. `serverNameOf` gained the bracket-vocabulary pass
(bracketed audio + quality tokens, standalone quality words; blank guard
preserved). `CsAudioTag.parse` vocabulary widened (brackets + underscores,
once-compiled regexes). Report format: `buildResolveDebugReport` (provider /
anime / episode / per-link name·server·audio·quality·type·referer·header-KEYS·url).

**P6 — the overlay subtitle system:** engine drops sidecar merging entirely
(`SingleSampleMediaSource` + `forSubtitle` removed; embedded-only text tracks).
`CsSubtitleParser` (pure, SRT/VTT/ASS, 14 tests) + `CsSubtitleFetcher` (OkHttp,
15 s budget, 4 MB cap, typed failures) + `CsSubtitleOverlay` (Compose, full
style mapping incl. fontScale/family/delay; shadow via the stacked-stroke
technique — NOT TextStyle.textShadow: zero repo precedent under the pinned
Compose, CI-is-compiler risk). The sheet: provider rows with
loading/failed(retry-on-tap)/selected states, 0.55 height cap, "Off" = both
domains; the needs-reload section + reattach flow deleted. Embedded-track
picks arm a crash guard (error within 8 s → revert override + retry once).
Provider auto-select (MPV slang parity) lives in the screen and disables
engine text first; the engine's auto-select skips when text is disabled (no
double-subtitle stacking). Settings sheet reworked to aniyomi structural
parity (sticky header, Typography/Colors/Position & Misc, font row, delay
stepper, tap-to-edit keypads on all 6 numeric rows, live writes).

**P7/P8 — player hardening:** `DefaultLoadControl` 30 s retained back-buffer
(+50–90 s forward, 2.5/5 s start/rebuffer thresholds), `seekRelative` reads
`engine.state.value` live, `seekTo` guards negatives + clamps finite
durations, ticker 100 ms (smooth cue flips).

**Verification:** 70/70 pure-logic tests GREEN offline under the project's
Kotlin 2.2.0 (CsSubDubSiblings 20, CsSourceListUi 17, CsLanguageNames +
CsAudioTag + CsSubtitleParser 33); CsSubtitleFetcher compiled CLEAN against
real deps (okhttp/coroutines + model sources); all 27 touched files
brace-balanced + parse-clean vs HEAD error-class histograms (count shifts
only); zero trailing whitespace/tabs; no Int?:Float elvis in the diff.
Aniyomi stack: zero code changes (read-only references).
