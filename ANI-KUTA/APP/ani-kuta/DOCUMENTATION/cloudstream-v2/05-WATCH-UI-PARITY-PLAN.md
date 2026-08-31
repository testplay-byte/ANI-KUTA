# 05 — CloudStream Watch-Page UI Parity Plan (Task 54 / Round 14)

## 0. Context — what the device round told us

v0.4.1/66 device feedback (user):

1. **Streams now resolve and present properly** — the round-13 playback fixes
   hold (AniKoto 6 sources, MovieBox DASH, auto-fallback). ✅ no playback work.
2. **The stream-picker UI does not match the aniyomi extensions' UI** — the CS
   resolve sheet (flat list, default Material styling) looks nothing like the
   aniyomi `ResolverSheet` (server accordion + quality chips + RobotoFamily
   typography) the user sees for every other extension. ❌ fix.
3. **The watch page is not integrated for CloudStream** — the CS "watch screen"
   is a bare fullscreen player; the aniyomi watch page is a real PAGE (player +
   currently-playing description + episodes list below). The user wants the CS
   watch page to show episodes + description + details "properly". ❌ fix.
4. **The player should be proper / clean / beautiful** + **subtitles need
   focus**. ❌ fix (visual + interaction parity).

## 1. Scope and invariants (carry over from 02-PLAYBACK-PLAN §2)

- **Aniyomi is byte-untouched.** `feature:watch` (WatchScreen/WatchKey/
  PlayerSheets), `core:player` (MPV stack incl. controls), `core:video-resolver`
  — zero diff. Parity is achieved by REPLICATING the visual design with
  cs-watch-local composables (same design tokens: RobotoFamily, MaterialTheme
  colors, identical paddings/typography), NOT by importing aniyomi code.
  `core:designsystem` (RobotoFamily, ScrollBlurOverlay) and coil are shared
  infra — allowed.
- **No plugin classes** beyond the `data:cloudstream` resolver boundary.
- **No MPV** for CS links (ExoPlayer stays); **no PlaybackCache** for CS.
- **One progress store** — `WatchProgressStore` contract unchanged.
- **CI is the compiler** — phase commits → build-apk.yml green.

## 2. The three deliverables

### D1 — Resolve sheet = `ResolverSheet` visual parity (entry UX)

`CsResolveSheet.kt` gets the aniyomi `ResolverSheet` design, byte-for-byte
styling (sheet shape 20dp top radius, surface container, no drag handle,
"Episode N" 18sp ExtraBold header + 32dp circle close, RobotoFamily):

- **Server accordion** (one open at a time): CS links group by `link.name`
  (the provider's server label, e.g. "Mirror", "HD-1"). Card = 12dp rounded
  surfaceVariant 0.3 → expanded primaryContainer 0.3; server name 14sp
  ExtraBold primary; chevron ExpandLess/More.
- **Quality chips**: FlowRow, 8dp rounded primaryContainer, PlayArrow 14dp +
  `qualityLabel` 12sp ExtraBold (the aniyomi QualityChip layout). A link's
  type (HLS/DASH) rides the chip label when quality is Unknown/Auto so the
  row is never blank.
- **CS behaviors preserved**: progressive streaming (cards appear as links
  arrive), remembered-server auto-select, single-link auto-select,
  cancel-on-dismiss, retry clears remnants (R13-REVIEW F5).
- **States**: Loading = spinner + "Resolving video sources…" (ResolverSheet's
  wording); error/empty = the ResolverSheet error card + Retry.
- Grouping is presentation-only: `pick(link)` still hands off the FLAT link
  list (seed contract unchanged).

### D2 — The CS watch page (the real gap)

`CsWatchScreen` becomes a two-mode watch PAGE mirroring the aniyomi
WatchScreen layout (task 52's "visual parity via the same design tokens",
now actually realized):

```
MINIMIZED (portrait, default)                 FULLSCREEN (landscape)
┌───────────────────────────────┐             ┌──────────────────────────────┐
│  ◁  ANI-KUTA    (pill bar)   │  collapses   │ 🔒 Title        [CC][HD][♪][⋮]│
├───────────────────────────────┤   on scroll  │        -10s  ▶  +10s         │
│  ┌─────────────────────────┐  │             │ ═════════seek═══════════════ │
│  │   16:9 player (14dp)    │  │             │ 0:30 [1.0x][↻][⏭][⤢]  24:00 │
│  │   CsMinimizedControls   │  │             └──────────────────────────────┘
│  └─────────────────────────┘  │
│  Currently playing episode 5  │
│  Episode title (20sp XB)      │
│  [provider] [quality] pills   │
│  Synopsis (show more/less)    │
│  Episodes (12)          ┌──┐  │
│  ┌──┐ EP 1 · Title      │44│  │  EpisodeListRow design: 12dp rounded
│  └──┘                   └──┘  │  card, current = primary border, EP tag,
└───────────────────────────────┘  title surface, sub/dub pills, synopsis.
```

- **Key extension**: `CsWatchKey.episodeMetadataSerialized` (same
  `epNum␟title␟thumb␟date␟desc␟scanlator` format as WatchKey) + parser +
  `CsWatchEpisodeMeta`. Built at the DetailsScreen CS click-site (the bridge
  already maps CS `Episode.description/posterUrl/scanlator` onto SEpisode
  `summary/preview_url/scanlator` — task 46/50 work pays off here).
- **VM**: uiState gains `episodeMetadata` + a current-quality label; episode
  switching already re-keys episodes (kept).
- **Window choreography** copies the aniyomi screen exactly: portrait in
  minimized, sensor-landscape in fullscreen, bars visible/hidden per mode,
  restore on dispose (the double-top-padding lesson is baked into the
  aniyomi pattern — replicated, not shared).
- **RESOLVING / FAILED / NO_LINKS** render INSIDE the 16:9 player box (the
  page below stays visible) — the resolving overlay shrinks to the player
  area; errors show in the box with Retry (matching how the aniyomi page
  keeps its content while the player shows states).

### D3 — Player + sheets polish (incl. subtitles)

- `CsMinimizedControls`: gradient scrim, time top-left, transparent
  Subtitles/Quality icon buttons top-right, center play/pause themed glass
  (56dp, 12dp radius), seekbar + fullscreen button bottom; single-tap toggles
  controls, double-tap zones (-10s/+10s/play).
- `CsFullscreenControls`: lock, title + EP/quality pills, frosted button row
  (subs / quality / audio / more), -10s/play/+10s, canvas seekbar with
  buffer-ahead + scrub tooltip, speed/skip/exit row, 4s auto-hide, 200ms
  slide/fade animations. Speed opens a SpeedSheet-style picker (presets +
  slider, aniyomi design).
- `CsLinksSheet` → "Qualities and Servers" (QualitySheet parity): the SAME
  accordion as the resolve sheet, failed chips struck-through + reason,
  hidden-count footer, "Quality for this stream" (HLS/DASH variant TrackRows)
  when the engine exposes >1 video track, long-press copy URL kept.
- `CsSubtitlesSheet` → "Subtitles" (SubtitleTracksSheet parity): 22sp header,
  close, divider, Off-first TrackRows (10dp rounded, selected = primary
  0.15 + 2dp border + check), sections (provider sidecars / embedded /
  needs-reload) + embedded-audio section when >1.
- `CsEpisodesSheet`: header + rows restyled to the TrackRow card pattern
  with EP number + current highlight.

## 3. File plan (feature/cs-watch stays zero-aniyomi-imports)

| File | Action |
|---|---|
| `api/CsWatchKey.kt` | + episodeMetadataSerialized + parseEpisodeMetadata() + CsWatchEpisodeMeta |
| `impl/CsWatchScreen.kt` | REBUILD — page scaffolding: window effects, play trigger (unchanged logic), engine events, two-mode layout switch |
| `impl/CsWatchPage.kt` | NEW — the minimized page (pill bar, player box, currently-playing, episodes list) |
| `impl/CsPlayerControls.kt` | NEW (replaces CsControlsOverlay.kt) — CsMinimizedControls + CsFullscreenControls + SpeedSheet-style picker |
| `impl/CsPlayerSheets.kt` | REDESIGN — Qualities-and-Servers / Subtitles / Episodes in aniyomi sheet language |
| `impl/CsResolveSheet.kt` | REDESIGN — D1 |
| `impl/CsOverlays.kt` | KEEP + adjust (player-box-sized resolving/error states) |
| `impl/CsWatchViewModel.kt` | + episodeMetadata/quality-label state (logic untouched) |
| `feature/anime-details/.../DetailsScreen.kt` | CS click-site builds + passes epMetaStr (aniyomi path byte-untouched) |
| `app/.../MainActivity.kt` | onNavigateToCsWatch signature + param pass-through |
| `impl/src/test/.../CsWatchKeyTest.kt` | NEW — metadata parser tests (round's CI test gate) |

## 4. Phases

- **A** Key + seam (api, DetailsScreen, MainActivity, VM state) + tests.
- **B** CsResolveSheet redesign.
- **C** CsWatchScreen + CsWatchPage rebuild.
- **D** Player controls (minimized + fullscreen + speed sheet).
- **E** Player sheets redesign.
- **F** Docs (03-PLAYBACK as-built round-14 section), AndroidConfig 0.4.2/67.
- **G** Adversarial review (aniyomi byte-safety + diff audit) → CI → tag
  v0.4.2 → release APK → ntfy → worklog.

## 5. Explicitly NOT in this round (deferred)

MPV-for-CS, plugin metadata covers, sub/dub list merging, CS downloads,
removing the debug loop, DASH exposure beyond variant tracks, i18n of sheet
copy, Aniyomi code sharing (the point is parity WITHOUT coupling).
