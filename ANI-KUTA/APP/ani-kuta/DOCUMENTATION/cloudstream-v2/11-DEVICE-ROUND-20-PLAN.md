# CloudStream V2 — Round 20 (Task 60): the v0.4.7 device-round fixes

**State:** plan (this document) → as-built addendum at the bottom.
**Input:** the user's v0.4.7 device round (2026-09-01, the feedback message + the
Downloaded-page crash report).
**Output:** v0.4.8/73.

## The findings (as reported)

| # | Area | The user's finding |
|---|------|--------------------|
| A | CS subtitle overlay — line gaps | font size MAX + scale 0.5 → the gap between the two cue lines is "way too much"; scale 2× → "so minimal they overlap"; scale MAX → "the whole text was overlapping, distorted". The gap must be consistent at EVERY scale. |
| B | Subtitle bold default | bold must be ON by default, on BOTH stacks, "as accurate to each other as possible". |
| C | Subtitle reset | the header Reset button must ASK FOR CONFIRMATION before resetting (both sheets). |
| D | Formatting toggle design | the round-19 standalone "Formatted sources" pill is NOT what the user wants. The EPISODE HEADING (the sheet title text itself — only the text, not the surrounding area) must open a SMALL menu with a DISTINCT BORDER containing the formatted-sources on/off toggle. |
| E | Plugin share/import | (1) NO legacy compatibility — the old `.moviebox.WHITECAT` name must not be recognized; (2) the import CONFIRM page shows the plugin's icon/logo at the top (currently the generic extension badge). |
| F | Post-Add redirect | after adding a CloudStream plugin the extensions page opened on the ANIYOMI tab; a CS plugin import must land on the CLOUDSTREAM tab. |
| G | Download rows — server names | long server names eat the row and push the progress percentage out of view; the server name must shorten with a trailing "…" and the percentage must always be visible — for BOTH the CS downloads and the aniyomi downloads. |
| H | Downloaded page crash | `IllegalArgumentException: Key "downloaded_<contentId>" was already used` when scrolling to the bottom of the Downloaded page (full stack trace supplied). |

Confirmed GOOD (do not regress): the .WHITECAT share carrying icon+repo (the
receiving detail page shows both), the .bin content-first import, CS downloads
end-to-end (resolve → pick → queue → progress → complete → offline playback),
the overlay's border/background alignment at every size, MPV subtitle
rendering, the 1.5s "Plugin added" flow itself.

## Root causes

### A — the line gaps: a FIXED 24sp lineHeight leaks in from the ambient style

`CsSubtitleOverlay`'s fill `Text` sets `fontSize = fontSizeSp` but NEVER
`lineHeight`. Material3's `MaterialTheme` provides `typography.bodyLarge` as
the ambient `LocalTextStyle` (and the app's `AnikutaTypography.bodyLarge` =
fontSize 16sp / **lineHeight 24.sp**, Type.kt:97). `Text(fontSize = X)` merges
over it: **fontSize is overridden, lineHeight (24sp, a CONSTANT) is inherited.**

Every device observation falls out of one fixed L = 24sp against a varying
effective font F (16:9 box, font 100):

| Setting | F (approx) | F vs 24sp | Rendered |
|---|---|---|---|
| scale 0.5 | ~11sp | F ≪ 24 | line boxes ~2.2× the glyphs → "way too much gap" |
| scale 2× | ~21sp | F < 24 | gap ≈ 0, glyphs touch/overlap |
| scale MAX (4×) | ~43sp | F ≫ 24 | line boxes smaller than the glyphs → "overlapping, distorted" |

(Fullscreen math would flip the signs; the reported ordering matches the 16:9
embedded box — the mode the user tests in.)

**Fix:** the overlay's Text gets an EXPLICIT `lineHeight = fontSizeSp ×
LINE_HEIGHT_RATIO (1.2f)` + `lineHeightStyle = LineHeightStyle(Proportional,
Trim.None)`. The line gap becomes a CONSTANT fraction of the effective font at
every size and scale (20% — slightly above Roboto's natural 1.18 line box, so
glyphs never touch), and no ambient style can ever influence the overlay's
line metric again. The ratio lives in `CsSubtitleGeometry` (pure, testable);
`lineHeightSp(fontSizeSp)` is the helper the overlay calls.

### B — bold default

`PlayerPreferences.boldSubtitles` defaults `false` (getter + reset). Flip both
to `true`. One preference drives BOTH stacks (the CS `CsSubtitleStyle.bold` →
overlay `FontWeight.Bold`/`Typeface.BOLD`, and MPV `sub-bold=yes` —
AnikutaMPVView:150/:184), so both sheets + the reset all land bold-on
out of the box.

### C — reset confirmation

Both sheets' header Reset currently fires `resetSubtitleSettings()` on tap.
Wrap with a small `AlertDialog` (title/body/Reset/Cancel) — identical copy on
both sheets; the reset logic itself is untouched.

### D — the formatting menu on the heading

Remove the round-19 pills (`CsFormattingToggle` + the two aniyomi local
copies + all call sites). Replace with a heading component:

```
Box(anchor = weight(1f)) {
    Text(title)                       // intrinsic width, maxLines=1, ellipsis,
        .clickable { expanded = true } // ONLY the text is the touch target
    DropdownMenu(                     // anchored UNDER the title
        shape = RoundedCornerShape(14.dp),
        containerColor = surface,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, outline, same shape),  // the distinct border
    ) {
        Row(fillMaxWidth.clickable { onToggle(!formatted) }.padding(…)) {
            Text("Formatted sources") ; Spacer(weight) ; Switch(formatted, onToggle)
        }
    }
}
```

The menu STAYS OPEN while toggling (the user flips on AND off inside it;
outside-tap/back dismisses). `Modifier.border` (not DropdownMenu's `border`
param — version-proof) + `shape`/`containerColor`/`tonalElevation` (already
used app-wide, CI-proven on m3 1.3.1). Shared `CsFormattingTitle` for the two
CS sheets (CsSourceListUi.kt); local copies in ResolverSheet.kt +
PlayerSheets.kt (the replication rule — the stacks share the preference, not
code). Four call sites: CsResolveSheet ("Episode N"/"Download EP N"),
CsLinksSheet ("Qualities and Servers"), ResolverSheet ("Episode N"/"Download
EP N"), QualitySheet ("Qualities and Servers").

### E — share/import

1. **No legacy compat:** delete `LEGACY_SHARED_EXTENSION` and its branch from
   `sharedExtensionOf` — `.WHITECAT` is the ONLY recognized extension.
   `"X.moviebox.WHITECAT"` is now "just a renamed file": not a shared-plugin
   name (`isSharedPluginFile` false), and its stem identity via the
   content-first path keeps the `.moviebox` infix (no special handling). Tests
   updated accordingly.
2. **The confirm page's logo:** `parseIntent` additionally reads
   `readExportIcon(temp)` + `readExportInfo(temp)`; the `Confirm` stage carries
   `iconBytes`/`iconUrl`. The badge renders: embedded bytes →
   `BitmapFactory.decodeByteArray` → `Image` (72dp, CircleShape clip); else
   the export's iconUrl → coil3 `AsyncImage`; else the generic extension icon.

### F — the CloudStream tab landing

`ExtensionsSettingsKey` object → `data class (initialTab: String = "aniyomi")`
(Serializable with a default — old persisted payloads still decode).
`checkPendingCsPluginNav` pushes `ExtensionsSettingsKey("cloudstream")`;
`ExtensionsSettingsScreen` gains `initialTab` and seeds
`rememberSaveable(initialTab)` with it. Works for both the same-process resume
and the cold-start path (the manager's flows already contain the plugin when
the screen composes — StateFlow current values).

### G — server-name ellipsis, percentage always visible

The queue row's info row restructures into a weighted inner Row: server pill
gets `weight(1f, fill = false)` + `overflow = Ellipsis` (shrinks with "…");
audio/quality/size stay intrinsic; the percentage/status pill sits OUTSIDE the
weighted section (guaranteed space). The Downloaded page's server chip gets
the same treatment. The row components are shared by both stacks (the same
`EpisodeRow`/`DownloadedAnimeCard` render CS and aniyomi queue rows) — one
fix covers both.

### H — the crash: grouping by the WRONG key

`DownloadViewModel` groups with `groupBy { it.content }` — the full
denormalized content object. `downloaded_episode` DENORMALIZES the content
metadata per row (title/cover/coverColor…), so two rows of the SAME anime
with different metadata (one cover null, one set — different write paths
across versions) split into TWO groups whose `DownloadedAnimeKey.contentId`
is the SAME STRING → two LazyColumn items with the key
`"downloaded_<contentId>"` → the crash the moment the second composes
(scrolling to the bottom).

**Fix:** group by the STABLE `content.contentId`; inside a group, prefer the
richest record's metadata (first non-blank cover, else the first record). One
group per contentId by construction → unique list keys; the screen code is
untouched.

## Execution order

1. Plan doc (this) + worklog.
2. E1: `CsSharedPluginFormat` legacy removal + `CsSharedPluginFormatTest`.
3. E2+F: `PluginImportActivity` (icon) + `ExtensionsSettingsKey`/screen + `MainActivity`.
4. A: `CsSubtitleGeometry` + `CsSubtitleOverlay` + `CsSubtitleGeometryTest`.
5. B: `PlayerPreferences` bold default.
6. C: reset confirmations on both sheets.
7. D: `CsFormattingTitle` + 4 sheet headers + the 2 local copies + import sweeps.
8. G+H: `DownloadsScreen` + `DownloadedFilesScreen` + `DownloadViewModel`.
9. Version 0.4.8/73 + docs + offline test runs + static verification
   (brace balance, import sweep, call-site cross-check) + commit/push/CI/tag/release.

## Invariants

- The debug toolkit + every confirmed-good surface untouched.
- Aniyomi changes stay additive/display-layer only (reset dialog, heading
  menu, shared download rows).
- CI is the compiler of record; offline kotlinc covers the pure-JVM tests.

---

## As-built (Task 60 / round 20 — v0.4.8/73)

All eight fix areas implemented as planned; two pre-push catchers fired:

- **E1 refinement:** the plain "remove the legacy branch" was NOT enough —
  `"X.moviebox.WHITECAT"` also ends with `.WHITECAT`, so the
  current-extension check still matched the old name (the offline
  CsSharedPluginFormatTest run caught it, same as round 19's suffix
  shadowing). The legacy tail is now an explicit **REJECTION marker**
  (`REJECTED_LEGACY_TAIL`, checked FIRST → null): the old name is not a
  shared-plugin NAME (identity falls to the manifest on the content-first
  path), which is the faithful reading of "I don't want it to be compatible
  with the old one."
- **Static-review catchers (2 compile blockers fixed pre-push):** the
  overlay's `lineHeight` needs `.sp` (TextUnit, not Float — Kotlin has no
  implicit conversion), and MainActivity's screen switch uses `currentKey`
  (the when-subject), not `key`.

Per-fix as-built notes:

| Fix | As built |
|---|---|
| A | `CsSubtitleGeometry.LINE_HEIGHT_RATIO = 1.2f` + `lineHeightSp(fontSizeSp)`; the overlay Text passes `lineHeight = lineHeightSp(fontSizeSp.value).sp` + `style = TextStyle(lineHeightStyle = Proportional/None)` (a BARE style — no LocalTextStyle merge, fully self-contained). 3 new geometry test locks (12→13 tests). |
| B | `PlayerPreferences.boldSubtitles` default true (getter + reset) — one pref drives MPV `sub-bold` + the CS overlay. |
| C | Identical `AlertDialog`s on both sheets (title/body/Reset/Cancel, `showResetConfirm` state); the header icon opens the dialog, only its Reset button writes. |
| D | Shared `CsFormattingTitle` (CsSourceListUi) + local `ResolverFormattingTitle`/`WatchFormattingTitle`; the round-19 pills deleted everywhere; menu = 14dp-cornered flat surface + 1dp outline (Modifier.border — version-proof) + one "Formatted sources" row with a trailing Switch; toggling keeps the menu open. |
| E | REJECTED_LEGACY_TAIL marker (above); the Confirm stage carries `iconBytes`/`iconUrl` (parseIntent reads export.json + icon.png); the badge renders embedded bytes (BitmapFactory→ImageBitmap→Image), else the exported iconUrl (coil3 AsyncImage), else the generic glyph. |
| F | `ExtensionsSettingsKey` object→`data class(initialTab = "aniyomi")`; `checkPendingCsPluginNav` pushes `initialTab = "cloudstream"`; the screen seeds `rememberSaveable(initialTab)`. |
| G | EpisodeRow Row 2 = inner weighted Row [ServerInfoPill(weight 1f, fill false, Ellipsis) + audio/quality/size] + the status/percentage pill OUTSIDE it; the Downloaded page's server chip gets the same flex+ellipsis. |
| H | `DownloadViewModel` groups by `content.contentId`, display metadata from the richest record (first non-blank cover, else first). |

Verification: 12/12 (CsSharedPluginFormatTest) + 13/13 (CsSubtitleGeometryTest)
GREEN offline with the real compiler chain (kotlinc 2.2.0 + serialization
plugin); brace/paren balance + import sweeps clean on all 21 changed files;
full-diff static review (2 blockers fixed); CI is the compiler of record.
