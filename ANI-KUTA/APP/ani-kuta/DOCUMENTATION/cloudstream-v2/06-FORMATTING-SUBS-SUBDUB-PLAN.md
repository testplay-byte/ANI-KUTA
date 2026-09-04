# 06 — Round 15 (Task 55): Source Formatting, Subtitles, Sub/Dub Display

> The v0.4.2/67 device round confirmed playback + watch-page parity. This round
> fixes what the user flagged NEXT: resolve-sheet noise + missing aniyomi audio
> formatting, a formatting on/off toggle for BOTH stacks, the CS subtitle
> pipeline (URL names, selection not applying, no customization), and sub/dub
> episode display modes.

## 0. Device-round feedback (v0.4.2, user quotes condensed)

1. ✅ Streams resolve + present properly; episode tap → resolve sheet works;
   watch page (episodes + description) works; playback smooth. NO playback work.
2. ❌ Resolve sheet shows "via {extension} — tap a server to expand, then pick a
   quality · N subtitle track(s)" at top and "N source(s) · N subtitle track(s)"
   at the bottom — remove both.
3. ❌ Formatting has no servers/audio-version/resolution structure like the
   aniyomi extensions (audio chips missing).
4. ➕ NEW: formatting toggle — tapping the "Episode N" header (top-left) opens a
   SMALL popup menu ABOVE it (not a bottom sheet) with formatting on/off
   (default ON). OFF = raw flat list of unformatted entries, tap = play
   directly. BOTH stacks (aniyomi + CloudStream).
5. ❌ Subtitles: track rows show URLs (not languages); clicking an embedded sub
   "reloaded the video and nothing applied"; clicking a needs-reload sub
   reloaded but never attached; no high customization (style settings) like the
   aniyomi side.
6. ➕ NEW: sub/dub episode display modes (some CS extensions emit "(Sub)"/"(Dub)"
   rows): COMBINED (one row; click resolves BOTH variants into the resolve
   sheet, audio chips let the user pick) or SEPARATE (Sub/Dub switcher chips at
   the top of the list). Configured in Episode list settings → Display.

## 1. Scope and invariants (same as 05 §1)

- **Aniyomi stack untouched** except ADDITIVE UI in three files:
  `ResolverSheet.kt` + `QualitySheet` (feature/watch PlayerSheets.kt) gain the
  formatting popup + raw mode (new code paths only, existing accordion path
  byte-identical); `EpisodeListSettingsSheet.kt` Display tab gains the Sub/Dub
  section (pure addition). The MPV player, WatchScreen logic, resolver engine:
  zero diff.
- **No plugin classes** beyond `data:cloudstream`.
- ExoPlayer stays for CS; one WatchProgressStore; CI is the compiler.

## 2. Root causes (subtitles)

| Symptom | Root cause |
|---|---|
| Track rows show URLs | `SubtitleConfiguration` sets `id` but never `label` → `CsTextTrack.name = format.label ?: id` → id is `"$url\|$name"` |
| Embedded click "reloads + nothing applied" | `selectTextTrack` builds the override from the SHEET SNAPSHOT's group/track indices; after any re-prepare indices shift → override lands on the wrong/absent group. Fix: resolve the indices LIVE by `format.id` |
| Needs-reload sub never attaches | Same stale-index path + extension-only mime guessing (VTT served without `.vtt` → parsed as SubRip → zero cues) + no verification after the reload |
| No customization | Media3 side never consumed the PlayerPreferences subtitle style values |

## 3. Phases

### Phase A — prefs + models + resolver (core:preferences, core:cs-player, data:cloudstream)
- `PlayerPreferences`: + `resolveSheetFormatted` (default TRUE — the shared
  toggle for all four sheets), + `preferredSubtitleLanguages` (default
  "en,eng,english" — CS auto-select, mirrors MPV slang behavior; new key, MPV
  side does not read it → zero aniyomi impact).
- `EpisodeListPreferences`: + `subDubMode` ("SEPARATE" default = current
  behavior + switcher, "COMBINED").
- `CsVideoLink`: + `audioTag: String?` (null = none).
- NEW `CsLanguageNames` (core:cs-player): BCP-47/language-string → display name
  ("en"→English, "eng"→English), filename-derived fallback.
- NEW `CsAudioTag` (core:cs-player): aniyomi `parseAudioVersion` port —
  SUB/DUB/HSUB/RAW/MIX/Default from free text (word-boundary, case-insensitive).
- `CsMediaTypes`: + `sniffSubtitleMime(url, firstBytes)` — WEBVTT/TTML/SRT
  content detection (fixes lying extensions); `CsSubtitle.displayName`.
- Resolver: friendly sub display names + content-sniffed mime (fetch first 512
  bytes per sub — tiny files, IO dispatcher, failure keeps the extension guess).

### Phase B — engine fixes (core:cs-player CsPlayerEngine)
- `SubtitleConfiguration`: `.setLabel(displayName)` + `.setLanguage(tag)`;
  selection still keyed by the stable `id`.
- `selectTextTrack(track)`: re-resolve the group/track indices from LIVE
  `player.currentTracks` by `format.id == track.id` (fallback: snapshot indices
  when id null). Kills the stale-index class.
- READY hook: one-shot auto-select of a sidecar/embedded track matching
  `preferredSubtitleLanguages` (never force when no match).
- NEW `applySubtitleStyle(playerView)` — maps PlayerPreferences to Media3
  `SubtitleView`: `CaptionStyleCompat` (text color, bg color, bold/italic
  typeface, EDGE_TYPE_OUTLINE / DROP_SHADOW from border/shadow settings, edge
  color) + `setFractionalTextSize(fontSize)` + `setApplyEmbeddedStyles(false)`
  + bottom-padding from position. Called at surface creation + live on settings
  change.
- `textTracks()` name: label → language display name → "Track N" (never the id).

### Phase C — CsResolveSheet rework (feature/cs-watch)
- DELETE the "via …" provider line + the counts footer. Keep the progressive
  "Scanning for more sources…" indicator (disappears at completion).
- Server cards gain the aniyomi audio-version layer: group by server name →
  audio label (`link.audioTag ?: CsAudioTag.parse(link.name)`) → quality chips;
  header shows audio chips (SUB/DUB, right side, reversed order); >1 audio
  version renders a per-version label row above its chips.
- Header "Episode N" gains the formatting popup (DropdownMenu offset ABOVE) →
  writes `resolveSheetFormatted`; OFF renders the raw flat list (one row per
  link: `displayLabel` + audio tag when present, TrackRow style, direct pick).
- Sub/dub COMBINED: when `subDubMode == "COMBINED"` and the tapped episode has
  a sibling row (same episode number, other tag) in `episodeListSerialized`,
  resolve BOTH handles in parallel; tag links with the row's audio label; the
  full merged list seeds the VM (pick engine unchanged).

### Phase D — watch screen + sheets (feature/cs-watch)
- `CsLinksSheet`: exact aniyomi QualitySheet parity — single hint line, audio
  chips in cards, NO counts footer / still-resolving / subtitle-count text;
  failed chips stay (visual only); formatting popup + raw mode with
  current/failed highlights.
- `CsSubtitlesSheet`: "Subtitle Settings" navigation row (aniyomi
  SubtitleTracksSheet pattern) → NEW `CsSubtitleSettingsSheet`; track rows show
  language display names; pending section relabeled "From provider (needs
  reload)" with ↻ badge; selection state honored for pending rows.
- `CsSubtitleSettingsSheet` (NEW file, cs-watch-local replica of the aniyomi
  sheet's sections — typography / colors / position — writing the SAME
  PlayerPreferences, applied live to the CS PlayerView).
- Watch page + CsEpisodesSheet: sub/dub display modes — SEPARATE: Sub | Dub
  chips row above episodes (only when both flavors exist); COMBINED: sibling
  rows merged (tag stripped from the name). VM `selectEpisode` resolves all
  sibling handles when COMBINED (links tagged → links sheet shows audio chips).

### Phase E — aniyomi sheets, additive only (feature/anime-details + feature/watch)
- `ResolverSheet`: formatting popup on the "Episode N"/"Download EP N" header +
  raw flat mode (iterates servers×audio×videos; same onPickVideo callback).
- `QualitySheet`: same popup on "Qualities and Servers" + raw flat rows with
  the current-video highlight.
- Both read/write `PlayerPreferences.resolveSheetFormatted` (shared pref,
  sheets are recreated per open so a non-reactive read is enough).

### Phase F — details screen sub/dub modes (feature/anime-details)
- `EpisodeListSettingsSheet` Display tab: "Sub/Dub episodes" segmented selector
  (Combined / Separate) + hint (only affects lists with (Sub)/(Dub) rows).
- `DetailsScreen`: SEPARATE → `SubDubSelectorRow` (SeasonSelectorRow visual
  language) above the episode rows, filters `episodesToShow` by the selected
  flavor; COMBINED → sibling rows merged before rendering (name minus tag,
  click passes the primary episode — the sheet finds the sibling).
- CS-bridged lists only (aniyomi lists never carry CS sub/dub rows; the filter
  is a no-op there).

### Phase G — tests + docs + version + release
- Unit tests: CsLanguageNames, CsAudioTag, mime sniffing, CsResolveSheet
  sibling detection (pure funcs), VM merged-resolution tagging.
- Docs: this file (as-built deltas), 03-PLAYBACK round-15 section, SESSION.md
  header, decisions D-379+ changelog/lessons/progress, AndroidConfig 0.4.3/68.
- Commit per phase → push → CI green → tag v0.4.3 → release → ntfy.

## 4. File map (what to touch when)

| Concern | File |
|---|---|
| Formatting pref / preferred subs | `core/preferences/.../PlayerPreferences.kt` |
| Sub/Dub display pref | `core/preferences/.../EpisodeListPreferences.kt` |
| Language names / audio tags / mime sniff | `core/cs-player/.../CsLanguageNames.kt`, `CsAudioTag.kt`, `CsMediaTypes.kt` |
| Link model (audioTag) | `core/cs-player/.../CsLinkModels.kt` |
| Sub name + mime at resolve time | `data/cloudstream/.../CloudstreamLinkResolver.kt` |
| Track selection / labels / styling / auto-select | `core/cs-player/.../CsPlayerEngine.kt` |
| Resolve sheet (entry UX) | `feature/cs-watch/impl/.../CsResolveSheet.kt` |
| In-player sheets | `feature/cs-watch/impl/.../CsPlayerSheets.kt` |
| Subtitle settings sheet (CS) | `feature/cs-watch/impl/.../CsSubtitleSettingsSheet.kt` (NEW) |
| Watch page / episodes sub-dub | `CsWatchPage.kt`, `CsWatchViewModel.kt`, `CsWatchScreen.kt` |
| Aniyomi resolve sheet toggle | `feature/anime-details/impl/.../ResolverSheet.kt` |
| Aniyomi quality sheet toggle | `feature/watch/impl/.../sheets/PlayerSheets.kt` |
| Display settings + details list | `EpisodeListSettingsSheet.kt`, `DetailsScreen.kt` |

---

## 5. As-built deltas (post-implementation)

- **Sniffed mime rides `CsSubtitle.sniffedMime`** (not a mime override) — the
  engine prefers it when building the SubtitleConfiguration. The resolver
  sniffs 256 bytes with a 4 s bound, silent-fail.
- **Subtitle settings sheet** (`CsSubtitleSettingsSheet`) offers the honest
  Media3 subset: font size, border size, bold, italic, 3 colors, position,
  shadow — MPV-only options (font family, scale, override-ASS, delay) are NOT
  offered (no Media3 equivalent; lying about them would be worse).
- **The formatting popup** = `CsFormattingHeader` (cs) / inline header popups
  (aniyomi sheets) — a DropdownMenu with `offset(0, -72.dp)` so it opens ABOVE
  the anchor, one item "Formatted sources" with a check when ON. The pref is
  `PlayerPreferences.resolveSheetFormatted` (default ON, shared by all four
  sheets).
- **Audio chips** in CS cards: `link.audioLabel` = explicit `audioTag` (combined
  sub/dub resolution) else `CsAudioTag.parse(link.name)` — the aniyomi
  parseAudioVersion port. Order: SUB, DUB, other flavors, "Default" last.
- **COMBINED resolution** happens in BOTH the resolve sheet (details entry)
  AND the watch VM's `startResolution` (episode switching / auto-advance) —
  one shared pairing helper (`CsSubDubSiblings` in :feature:cs-watch:api).
- **Sub/dub display** applies to FOUR surfaces: details episode list, CS watch
  page, episodes sheet, and (SEPARATE) the audio-filter's chip switcher stays
  untouched — the display setting is orthogonal to the Filter tab.
- **Aniyomi stack diff** = ADDITIVE ONLY in `ResolverSheet.kt` (header popup +
  raw list branch), `PlayerSheets.kt` QualitySheet (same), and
  `EpisodeListSettingsSheet.kt` (Display tab section). The formatted paths are
  behaviorally identical to v0.4.2.

## As-built addendum — the CI-failure completion round (2026-08-31, D-380)

The first v0.4.3 push failed CI before any downstream module compiled. The
completion round (see D-380 + the changelog entry) fixed:

1. `CloudstreamLinkResolver.sniffSubtitleMime` — moved from a channelFlow
   LOCAL (illegal `private` modifier, 139 cascade errors) to a private CLASS
   member; `SNIFF_HEAD_BYTES` (256L) in the companion.
2. `CsMediaTypesTest.kt` — the sniff tests now live in
   `class CsSubtitleSniffTest` (top-level @Test functions = JUnit4 runs the
   file-facade class → InvalidTestClassError).
3. Aniyomi `ResolverSheet` — the raw branch shares ONE extracted `pickVideo`
   adapter with the accordion branch (the raw branch previously passed the
   `(ResolvedVideo, …) -> Unit` callback into `RawVideoList`'s
   `(ResolverVideo, …) -> Unit` slot — a function-type contravariance
   compile error the aborted CI never reached).
4. `DetailsScreen` — single-flavor sub/dub lists render UNFILTERED (DUB-only
   + default SUB + no switcher was a blank list); the sub/dub display is
   GATED on `viewModel.isLinkedSourceCloudStream()` and the scanlator match
   narrowed to the bridge's exact "Sub"/"Dub" mirror (the aniyomi
   ADDITIVE-ONLY invariant is now structural, not conventional); merged
   rows copy `fillermark`.
5. `CsPlayerSheets` — 11 extraction-leftover unused imports removed.

Everything else in this plan shipped as described in the as-built above.
