# 10 — Round 19: Device-Round Fixes (subtitle precision, downloads wiring, share/import polish)

> Task 59 / round 19, planned after the v0.4.6 device round.
> Working branch `streaming/CLOUDSTREAM-V2`. Target version **0.4.7/72**.

## 0. The v0.4.6 device round — user verdict

**Confirmed working (must not regress):**
- The BOTH-STACKS debug toolkit (aniyomi ResolverSheet + QualitySheet, CS sheets) — "perfect… I don't have any issues with that."
- Subtitle LIVE updating while paused (the round-18 hoisted-style fix) — "handled properly".
- Plugin share/import round-trip + repository linkage ("recognizes it was installed using the file but it is from this repository") — the flow works.
- Subtitles show and are customizable (colors, italic).

**Findings this round** (root causes verified in code below):

| # | Finding | Root cause (verified) |
|---|---------|----------------------|
| A | Subtitle formatting: too much line spacing at scale 0.5×/font max; lines "overlapping" (no spacing) at bigger scale; border showing displaced ("subtitle at top, border at bottom") | the overlay renders each cue line as a SEPARATE `Text` in a `Column` — each line carries its own full line box (ascent+descent+leading), so stacked boxes DOUBLE the inter-line leading (huge gap at small sizes) while the stroke passes (border/shadow — which extend beyond glyph bounds) poke into the next line's box unopposed at large sizes; the SHADOW pass is border-COLORED and offset downward (`offset(y=shadowDp)`), reading as "a border showing somewhere else from the font" |
| B | No reset; new defaults wanted: font size MAX (100), scale 0.5×, border 5 | PlayerPreferences defaults are 55 / 1.0 / 3; no reset affordance on either sheet |
| C | CS download button → instant classic error "Failed to resolve videos…" — never shows the CS source picker | round 18 wired the CS-download gate into `EpisodesSection`'s `onDownloadEpisode` param — which is DEAD CODE (the D-228 refactor renders episode rows in the OUTER LazyColumn; the row's download button uses a hardcoded classic lambda at `DetailsScreen.kt:1231` that was never updated) |
| D | Shared plugin file: extension should be just `.WHITECAT` (not `.moviebox.WHITECAT`); icon + source repo URL not preserved | `CsSharedPluginFormat.SHARED_EXTENSION = "moviebox.WHITECAT"`; the export is a byte-for-byte .cs3 copy carrying NO metadata — the receiving side's record gets `iconUrl/repoUrl = null` when no added repo catalogs the plugin |
| E | Import page: title should be the plugin's name (not "Add CloudStream plugin"); remove the bottom description; after Add show "Plugin added" 1.5 s → extensions page (currently the app just finishes back to the sender) | strings in `PluginImportActivity` (title :372, caption :397-405); the Add path finishes back to the sender app; the pending-nav note pushes the plugin DETAIL page on next cold-start/ON_RESUME instead of leading the user to the extensions page |
| F | Plugin detail page: over-verbose descriptions ("installed but not trusted — code has never run", "Exports this plugin as a … daughter file…") | strings at `CloudstreamPluginDetailScreen.kt` :219-228 and :775-782 |
| G | `.bin` files (extensions rewritten by share targets) should still be detected as plugins | the import gate checks the DISPLAY NAME's extension first and rejects before content analysis; the intent filters already receive octet-stream/.bin VIEWs |
| H | Resolve sheet: the whole upper section opens the "Formatted sources" menu (empty-area taps trigger it); the toggle should be a distinct bordered element ABOVE the episode number | `CsFormattingHeader`'s title `Text` is `.fillMaxWidth().clickable` (the entire header width opens the menu) and the DropdownMenu anchors over the episode number; the aniyomi `ResolverSheet` has the same local copy |

## 1. A — the overlay subtitle renderer rewrite (accuracy round 2)

**Design:** ONE multi-line `Text` per cue (not one Text per line), with ALL
decoration passes drawn from the SAME `TextLayoutResult` via
`DrawScope.drawText` — the fill, the border stroke, the shadow stroke and the
per-line background boxes are mathematically incapable of detaching (they share
one layout object).

- **Line spacing becomes explicit**: `lineHeight = fontSize × 1.12` +
  `lineHeightStyle = LineHeightStyle(Center, Trim.Both)` (the tight, MPV-like
  constant; `Trim.Both` removes first/last-line padding so the box hugs the
  glyphs). The inter-line gap is now a CONSTANT fraction of the font at every
  scale — kills both "way too much spacing" (small) and "overlapping" (large).
- **Draw order per pass (not per line)**: background rects → shadow stroke →
  border stroke → fill. Later lines' fills cover earlier lines' stroke bleed —
  the visual gap is the explicit lineHeight, never the strokes.
- **Background = per-line rects from the layout** (`getLineTop/Bottom/Left/
  Right` padded by the border width — ASS BorderStyle=3 semantics preserved,
  positions guaranteed correct).
- **Shadow**: same-layout stroke pass offset by exactly `shadowPx` DOWN,
  border-colored at 75% alpha (MPV sub-shadow drawn in addition to the border).
- **Horizontal inset**: the text block wraps at 4% inset from each side
  (`CsSubtitleGeometry.horizontalInsetFraction`) so long lines never touch the
  screen edge.
- Geometry lives in `CsSubtitleGeometry` (pure, unit-tested): the line-height
  factor + inset join the existing fractions.

The Media3 `SubtitleView` path (embedded tracks) is unchanged — it is correct.

## 2. B — new defaults + the reset button (BOTH sheets)

- `PlayerPreferences`: `subtitleFontSize` 55 → **100**, `subtitleFontScale`
  1.0 → **0.5f**, `subtitleBorderSize` 3 → **5** (the user's spec: "font size
  to max, scale set to 0.5x, border set to a comfortable 5"). `CsSubtitleStyle`
  mirrors the same three defaults. MPV parity on the aniyomi stack:
  sub-font-size 100 × sub-scale 0.5 ≈ the old 55 default — the same rendered
  size, now composed of the user's preferred units.
- A **Reset** icon button in BOTH sheets' sticky headers (left of close):
  writes every subtitle pref back to its default (font "Sans Serif", 100,
  0.5×, border 5, bold/italic off, text white, border black, bg transparent,
  position 100, shadow 0, delay 0, overrideASS false), then re-keys the
  panel's local state (a `resetTick` counter — `remember(resetTick)` re-reads
  from the freshly-written prefs) and fires `onApplySettings` so the live
  preview + engine update immediately.
- The aniyomi sheet's change is purely additive (a header button) — the
  settings rows themselves are byte-untouched.

## 3. C — the downloads dead-callback fix

Replace the row-level download lambda at `DetailsScreen.kt:1231` with the SAME
gated branch round 18 wrote into the (dead) `EpisodesSection.onDownloadEpisode`
param:

```
onDownload = {
    currentEpisode = episode
    if (viewModel.isLinkedSourceCloudStream()) {
        resolverDownloadMode = false
        routeToCsDownload(episode)          // → CsResolveSheet DOWNLOAD mode
    } else {
        resolverDownloadMode = true
        viewModel.resolveEpisode(episode)
        showResolverSheet = true
    }
}
```

The rest of the chain was verified healthy in research: MainActivity wiring
(:748-761 AniList / :816-829 Extension), `handleCsDownloadPick` (:1655-1739),
`CsDownloadRequestBuilder`, the sheet's download mode (DASH filter + "Download
EP N" title), the classic path's CS guard, and offline playback (downloaded
branch runs before the CS branch). No other CS download trigger exists (no
bulk path; the auto-download engine is only reachable through the guarded
`handleDownloadEpisode`).

## 4. D — the shared-plugin format v2 (.WHITECAT + metadata)

- `CsSharedPluginFormat.SHARED_EXTENSION` → `"WHITECAT"`
  (`<internalName>.WHITECAT`). **Backward compatibility**: the import side
  still ACCEPTS the round-18 `.moviebox.WHITECAT` name (both extensions match;
  the stem strips whichever tail is present).
- **Export metadata**: the export is no longer a byte-for-byte copy — the
  share rewrites the zip: every original entry + a new
  `anikuta/export.json` (`CsSharedPluginFormat.ExportInfo`: repoUrl, iconUrl,
  name, authors, description, language, version, tvTypes). Extra entries are
  ignored by `PathClassLoader`/the loader (verified — no signature/CRC gate),
  so the file stays loadable.
- **Icon preservation**: the export embeds `anikuta/icon.png` — the icon
  bytes fetched at share time via Coil's ImageLoader (2.5 s timeout, best
  effort; the URL rides export.json as the fallback). The import writes
  embedded bytes to `filesDir/plugin_icons/<internalName>.png` and the record
  points at the local file (`CsPluginIcon` gains an explicit local-file model
  branch). When only the URL is available it is stored as-is (Coil fetches).
- **Repo URL preservation**: `importSharedPlugin` reads export.json — for the
  repo-less case (no added repo catalogs the plugin) the record now carries
  the source repoUrl + iconUrl + authors + description from the export info
  (displayed on the detail page; update flow still keys on ADDED repos). The
  install-path salt stays `shared-file` (stable identity, no path surprises).

## 5. E — the import UX

- Title: `"Add <plugin name>"` (the manifest name — :372).
- The caption under the info card (:397-405) is REMOVED.
- The **Added** outcome: a clean centered confirmation (check-circle + "Plugin
  added" + the plugin name), NO body text, NO Done button — auto-advances after
  **1.5 s** → launches `MainActivity` and finishes. The pending-nav note now
  routes to the **extensions page** (`ExtensionsSettingsKey`) instead of the
  plugin detail page (both consumers — cold-start LaunchedEffect + ON_RESUME —
  already exist in MainActivity).
- AlreadyInstalled/Failed keep their cards (the user closes deliberately).

## 6. F — the plugin detail page copy

- The trust-note (:219-228) is removed — the page's status rows already carry
  the state.
- The Share row's caption (:775-782) is removed (the "Share" action is
  self-describing).

## 7. G — .bin + content-first import

The gate order inverts: CONTENT is the source of truth.
1. Copy the incoming URI to the temp file (already done).
2. `readManifest(temp)` — a zip with a readable manifest.json +
   non-blank pluginClassName = a plugin (regardless of display name).
3. `internalNameFor` prefers the display-name stem when it carries the custom
   extension, else the manifest name (sanitized) — already the case.
4. Non-plugins reject with: `"<displayName>" is not an ANI-KUTA plugin file.`
The `.bin`/`.cs3`/renamed-file cases all resolve through content analysis.

## 8. H — the resolve-sheet formatting toggle (BOTH stacks)

- The "Episode N" title becomes PLAIN text (no click target, no fill-width
  clickable).
- A **distinct bordered toggle** sits ABOVE the title row: an outlined pill
  (`border(1.dp, outlineVariant)`, rounded) — leading format-list icon, the
  label "Formatted sources", a check icon in the primary color when ON. Tap
  toggles formatted/raw directly (no dropdown menu). Placed at the sheet's
  top, before the header row.
- `CsFormattingHeader` (CS + the in-player links sheet via the same
  composable) and the aniyomi `ResolverSheet`'s local copy get the same
  treatment; the aniyomi in-player `QualitySheet` copy follows if it shares
  the pattern.
- Empty-area taps in the header no longer trigger anything (the only
  interactive elements are the bordered toggle, the copy button, close).

## 9. Verification

- Unit locks: `CsSharedPluginFormatTest` (extension rename + legacy-name
  compat + export-info round trip + icon entry read), `CsSubtitleGeometryTest`
  (line-height factor + inset), plus the existing suites stay green.
- Brace/paren balance + import sweep on every modified Kotlin file.
- AndroidManifest re-validated (filters unchanged — content analysis makes
  them sufficient).
- CI is the compiler of record (D-281); release v0.4.7/72 after green.

---

## 10. As-built (implementation notes)

- **§1 deviation — no explicit lineHeight/`LineHeightStyle`:** the ONE-`Text`
  design uses the platform's NATURAL line spacing (what Media3's SubtitleView
  and every normal text renderer produce — one leading per line break). This
  kills the double-leading without any experimental text API and keeps
  embedded + overlay rendering visually identical.
- **§2 as-built:** the reset button is a header `Refresh` icon on both
  sheets; `PlayerPreferences.resetSubtitleSettings()` is the shared write;
  the panels re-key on a `resetTick` counter (`remember(resetTick)` re-reads
  the freshly-written prefs). `CsSubtitleStyle` mirrors the three defaults.
- **§4 as-built:** the icon fetch is stdlib `HttpURLConnection` (2.5 s
  timeouts, 512 KB cap, file:// local read) — no new deps; the embedded icon
  materializes at `filesDir/plugin_icons/<internalName>.png` and the record
  carries a `file://` URI (Coil's AsyncImage loads it as-is — no
  CsPluginIcon change needed). `repoUrl` from the export rides the repo-less
  record (display + future linkage); the install-path salt stays
  `shared-file`.
- **§7 as-built:** the import gate inverted exactly as planned — copy →
  `readManifest` → confirm/reject; `.bin`/`.cs3`/renamed files all resolve
  through content analysis; the reject message no longer expects an
  extension.
- **§8 as-built:** the aniyomi in-player `QualitySheet` had the same
  click-anywhere header — it got the same bordered-pill treatment
  (`WatchFormattingToggle`), so all FOUR sheets (CS resolve, CS in-player
  links, aniyomi resolver, aniyomi in-player qualities) are consistent.
- **Tests:** `CsSharedPluginFormatTest` rewritten (naming + legacy
  acceptance + writeSharedFile/readExportInfo/readExportIcon round trips +
  stale-metadata replacement — 10 locks); `CsSubtitleGeometryTest` gains the
  inset lock.
