# 09 — Round 18: Both-Stacks Debug, Subtitle Live-View, Downloads, Plugin Share (plan)

> Task 58 / round 18, planned 2026-08-31 after the v0.4.5 device round.
> Working branch `streaming/CLOUDSTREAM-V2`. Target version **0.4.6/71**.

## 0. The v0.4.5 device round — user verdict

**Confirmed working (must not regress):**
- Sub/dub progress linking (the round-17 ordinal identity) ✅ — "proper… exactly like how everything else was working"
- COMBINED variant tags/pills ✅ — "stored and proper… look beautiful"
- CS subtitle settings structure + customizability ✅
- Subtitles render + are customizable while PLAYING ✅
- Italic / colors / border color ✅

**Findings + requests this round:**

| # | Finding / request | Root cause (verified in code) |
|---|-------------------|-------------------------------|
| A | The debug toolkit exists ONLY for the CloudStream resolve lists — the user wants it for the ANIYOMI extensions too (copy resolved rows, raw stream URLs, "everything like that") | round 17's P4 wired `DebugPreferences` into cs-watch only; the aniyomi `ResolverSheet` + in-player `QualitySheet` had zero affordances |
| B1 | Subtitle settings changes DON'T apply in live view while the video is PAUSED (only while playing) | the overlay's style was a NON-reactive `currentSubtitleStyle(playerPreferences)` read inside the composition; the overlay only recomposed when `engineState` emitted a DISTINCT value — the 100 ms ticker's `positionMs` is constant while paused → `MutableStateFlow` equality-dedup suppresses emission → no recomposition → no fresh style read |
| B2 | Border size "not shown properly"; background/border "not in correct positions" | border width `fontSizePx × (0.035 × borderSize)` ≈ 1.9× MPV at the default 3 AND clamped at a 0.15 fraction (every setting ≥ 5 saturates); the background is ONE box with fixed 6.dp/2.dp padding + 1.25× lineHeight half-leading (doesn't scale, doesn't hug glyphs, lets the outline poke out) |
| C | Download functionality for CloudStream extensions: "not working… isn't even implemented" — implement it, prefer OUR own advanced downloader when compatible, with proper fallbacks | the engine is source-agnostic (a `DownloadRequest` keyed `mainId|episodeKey`) — only the resolve step + a CS→request translation were missing; `DetailsViewModel.resolveEpisode` returned the honest "downloads arrive with the downloads port" error |
| D | Plugin share + manual install: a Share option on plugin detail pages exports the .cs3 under a custom extension (`moviebox.WHITECAT`); opening such a file offers ANI-KUTA, shows details + ONE confirm (Add/Cancel), adds regardless of repositories, links to a matching added repository | new feature: needs an export path (FileProvider), an imported receiver activity (MIME-based filters — Android can't match content:// by extension), a manager import path + a hand-off into the main app's navigation |

## 1. A — the both-stacks debug toolkit

**Design:** ONE pair of `DebugPreferences` flags (the round-17 keys, defaults OFF) now
gates BOTH stacks. The aniyomi side follows the replication rule: the Compose row
chrome is duplicated per sheet file; the PURE report builder is shared through
`:core:video-resolver` (both aniyomi sheets already depend on it and consume
`ResolverServer` from it).

- **NEW `core/video-resolver/ResolverDebugReport.kt`** — `buildReport(sourceName, animeTitle,
  episodeNumber, servers)` (deterministic numbered blocks: server / audio / quality /
  videoTitle / header KEYS only / url — the CS report's privacy rule: header VALUES never
  ride the clipboard) + `buildVideoDetail(server, audio, video)` (per-row form) +
  `extractHeaderKeys` (comma-safe MPV-format parsing — the DownloadHeaderParser rule,
  re-implemented pure).
- **`ResolverSheet.kt`** (details entry sheet): `sourceName`/`animeTitle`/`debugPreferences`
  params (defaults — call sites unchanged except DetailsScreen passing context) +
  the gated header copy-report button (32 dp circle, live `collectAsState` flow) +
  `QualityChip` grows `onCopyDetails` (16 dp copy icon right of the label) +
  `sourceDetail` (10 sp raw URL line) + `RawVideoList` trailing copy icon + raw URL line.
- **`PlayerSheets.kt` `QualitySheet`** (in-player): the same gates + a header report
  action + `QualityChip`/`RawVideoRows` affordances (file-local
  `rememberQualityCopyFeedback` twin of ResolverSheet's helper).
- **`DebugSettingsScreen.kt`**: section retitled "Resolve lists (all extensions)",
  subtitles mention both stacks.

**As-built:** 8 unit locks (`ResolverDebugReportTest` — deterministic format,
blank-context omission, comma-safety through UA commas, values never leak).

## 2. B — subtitle live view + ASS-accurate formatting

**B1 (paused live view)** — hoist the style into Compose state:
`var liveSubtitleStyle by remember { mutableStateOf(currentSubtitleStyle(...)) }`;
the settings sheet's `onApplySettings` (fires on EVERY slider change) refreshes it
FIRST, then re-applies to the Media3 view; a `LaunchedEffect(showSubsSettingsSheet)`
re-syncs on sheet open (external writers). The overlay consumes `liveSubtitleStyle`
→ slider changes recompose the overlay immediately, paused or not.

**B2 (formatting)** — all geometry extracted to **`core/cs-player/CsSubtitleGeometry`**
(pure, unit-tested):
- border = `borderSize / 55` of the font height (MPV unit parity — LINEAR across the
  sheet's 0..10 range, generous 0.30 ceiling);
- per-line rendering: each cue line gets its OWN box (ASS `BorderStyle=3` /
  `sub-back-color` semantics — short lines get short boxes, no full-width slab);
  the box hugs the line's glyph bounds (no lineHeight multiplier artifacts) and is
  padded by the BORDER width (scales with the font — the fixed 6.dp/2.dp is gone);
- shadow drawn IN ADDITION to the border (MPV behavior; draw order shadow → border
  → fill);
- `maxLines = 4` truncation removed;
- `fontScale` now also scales the Media3 view
  (`applySubtitleStyle` rides `CsSubtitleGeometry.fontFraction` — embedded + overlay
  cues scale identically).

**As-built:** 9 unit locks (`CsSubtitleGeometryTest` — the default-3 fraction,
linearity, no early saturation, clamps, sub-pos mapping, font-fraction math).

## 3. C — the CloudStream downloads port

**Design (the user's directive: use our own system when compatible):** the engine
IS compatible — everything downstream of a `DownloadRequest` (queue, foreground
service, HTTP/HLS fetchers, SAF storage, notifications, the downloads screen, the
`downloaded_episode` DB, offline playback lookup) keys on `(mainId, episodeKey)` and
never touches extension code. CS episodes already set `SEpisode.url = data handle`,
so identity is byte-identical with zero schema change.

- **`CsResolveSheet.kt`**: `onDownload` param (non-null ⇒ download mode) — the title
  reads "Download EP N", DASH links filtered from the pickable list (the engine has
  no DASH path) + counted with an explicit note (the all-DASH case lands in the empty
  card, not a blank accordion), picks hand the RESOLVED link + provider subtitles to
  the caller (no seeding, no navigation).
- **`DetailsScreen.kt`**: `onDownloadCsEpisode` (the same 9-arg context the play path
  builds — `routeToCsDownload`) + the download button's CS branch; the classic
  resolver's CS error message updated (defense in depth stays).
- **NEW `app/download/CsDownloadRequestBuilder.kt`**: `CsVideoLink` → `DownloadRequest`
  (`allHeaders` → the MPV header string the engine's comma-safe parser reads; provider
  `CsSubtitle`s → `DownloadTrack`s with the same-source header fallback; queue-UI
  labels; `resolveContext = null` — CS links re-resolve through the sheet, not the
  aniyomi ReResolver).
- **`MainActivity`**: `csResolveDownloadMode` state + wiring at both details call
  sites + `handleCsDownloadPick` (content lookup keyed on the watch key's own mainId
  — the D-210-style sourceId authority, enqueue + toasts) + the classic auto path
  guarded for CS-bridged sources.
- **Playback of downloaded CS episodes**: unchanged by design — the details page's
  downloaded-branch runs BEFORE the CS branch, so offline playback rides the MPV
  watch path (local `content://` + on-disk sidecars), exactly like aniyomi downloads.

**As-built:** app module gains the `:core:cs-player` dependency (the link models —
cs-watch declares it `implementation`, not transitive).

## 4. D — the .moviebox.WHITECAT plugin share + import

- **Format (`data/cloudstream/.../CsSharedPluginFormat.kt`)**: the bytes are the
  untouched .cs3 zip; the FILENAME carries `<internalName>.moviebox.WHITECAT` (the
  stem round-trips the identity — repo .cs3 files are named the same way). Manifest
  read WITHOUT a classloader (ZipFile + kotlinx — same entry, same model, same
  lenient parsing as the loader).
- **Share (outgoing)**: a `Share` row on every plugin detail page state with a file
  on disk (trusted, errored AND untrusted — sharing needs only the file). Handler =
  the ConsoleLogsScreen pattern: fresh copy in `cacheDir/exports/` (the FileProvider
  already exposes the whole cacheDir) named by the shared format, `ACTION_SEND`
  `application/octet-stream` + granted read URI + chooser.
- **Import (incoming)**: exported `PluginImportActivity` (ComponentActivity +
  Compose + AnikutaTheme). Android can't match content:// URIs on file EXTENSIONS,
  so the manifest filters are MIME-based (VIEW content/file × octet-stream + zip;
  SEND octet-stream) and the ACTIVITY gates on the display name's custom extension +
  the zip's manifest.json — non-plugin files are rejected gracefully ("not a plugin
  file"). ONE confirm dialog (Add / Cancel) — nothing installs outright. Add →
  `CloudstreamPluginManager.importSharedPlugin`:
  - already-installed (same internalName) → `AlreadyInstalled`, no side effects;
  - an added repository cataloging the same plugin LINKS the record to it (repoUrl
    set — updates then flow); otherwise repo-less (path salted "shared-file",
    detail page shows "Shared file (no repository)");
  - atomic place (temp next to the target + `Files.move`), record lands UNTRUSTED
    (the session-3 trust model: the confirm adds the FILE, trusting gates the CODE).
- **Hand-off into the app**: `PendingCsPluginNav` (SharedPreferences — survives
  process death) consumed by AppRoot on cold start AND `ON_RESUME` → pushes
  `CloudstreamPluginDetailKey(internalName)`.

**As-built:** 7 unit locks (`CsSharedPluginFormatTest` — naming, case-insensitive
stem extraction, manifest parse incl. unknown-keys + non-zip/malformed rejection,
internalNameFor fallbacks; compiled against the REAL kotlinx-serialization compiler
plugin + runtime).

## 5. Verification

- 24/24 new pure tests GREEN offline (kotlinc 2.2.0: `ResolverDebugReportTest` 8,
  `CsSubtitleGeometryTest` 9, `CsSharedPluginFormatTest` 7).
- Brace/paren balance + error-HISTOGRAM count parity vs HEAD on all 9 modified
  Kotlin files (the cascade noise is dependency-missing, unchanged in count).
- AndroidManifest XML re-validated; FileProvider coverage confirmed (cache-path
  root already shared — no file_paths change needed).
- CI is the compiler of record for the Compose surface (D-281).
