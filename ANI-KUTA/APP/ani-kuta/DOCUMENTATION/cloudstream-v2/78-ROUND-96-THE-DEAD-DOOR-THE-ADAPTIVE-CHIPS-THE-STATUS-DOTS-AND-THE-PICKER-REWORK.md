# Doc 78 — Round 96: The v1.1.52 device round — the dead door, the adaptive duration chips, the test-status dots, and the source-picker rework

> Date: 2026-09-27 · Round 96 · Decisions D-659..D-661 · Shipped as **v1.1.53/10153**
> Previous: doc 77 (round 95). Next: doc 79.

## 1. The device report (v1.1.52)

The round-95 fixes ALL confirmed on the device:

- **The still-lift long-press** — "if I long press and then leave, it properly stays selected and everything is handled properly and exactly like how it should be."
- **The testing gate** — "by default now there is no extension testing option, which is good. And when I go to the debug options and long press on it, it opens up the debug options properly after long pressing for 10 seconds… I can turn on extension testing there."
- **The extensions-page door** — "going to the extensions and tapping the option there, it opens it up properly there without any problems."
- **The results redesign** — "the test results show properly and exactly like how I hoped for them to be this time. UI of all of them is proper. Everything looks clean, good, and proper."

Three asks came back:

1. **THE DEAD DOOR** — "I click it, but apparently nothing happens. It does not redirect me to the appropriate page" (the hidden debug page's "Open Extension Testing" row).
2. **THE GLITCHED DURATION CHIPS** — the full-details stage bars are wider and fine, but "the duration does not show properly. Like it is glitched out. It is cut off at the bottom, and there is a lot of empty space above, but it is also aligned to the bottom of it, which is not good. And also there is a shadow showing behind the duration."
3. **TWO FEATURES** — (a) a "Show Status on Extensions" option on the testing home's foot: color dots on the Extensions page's rows ("two colors for pass, two colors for fail, two colors for new… the dots will be shown on the very right side of them, just on the right side of the delete button"); (b) the Search page's "Pick a source" sheet: a system selector (Aniyomi / CloudStream), a search bar, alphabetical order.

## 2. D-659 — the two defect fixes

### 2.1 The dead door

**Root cause:** `SponsorDebugScreen.DebugDoorRow` took an `onClick` parameter and never wired ANY clickable to it — the Surface/Row body had no `clickable` modifier and no clickable-Surface overload. The parameter was dead code; the row rendered perfectly and responded to nothing. (The nav wiring was CORRECT all along — `SponsorDebugKey → onOpenExtensionTesting = { backstack.add(ExtensionTestingKey) }` in MainActivity — which is why the extensions-page door worked while this one sat inert.)

**Fix:** the row now carries the app-standard press feedback (CORE_RULES §22: `MutableInteractionSource` + scale 0.97 via `animateFloatAsState`, NO ripple — the exact `MoreListRow` treatment) and a `clickable(interactionSource, indication = null, onClick)` on the Surface modifier.

### 2.2 The glitched duration chips

**Root cause (two parts):**
- The chip is **sp-driven** (9sp text grows with the system font scale) while the bar was a **fixed 15dp track that clips its children** (`.clip(RoundedCornerShape(50))` on the outer Box). At any font scale above ~1.05 the chip grew taller than the track and the stadium clip sliced it; Roboto's top leading inside the pill pushed the glyphs low, so the visible remnant read exactly as the user described: bottom-aligned, empty space above, cut at the bottom.
- The chip's background was `Color.Black.copy(alpha = 0.40f)` — a translucent black pill that reads as a **drop shadow** over the light kind colors and the bare track.

**Fix:**
- **The adaptive bar height** — `barHeight = maxOf(15.dp, 9.sp.toDp() * 1.6f + 5.dp)` with `LocalDensity`: 19.4dp at font scale 1.0, growing WITH the user's font scale, never below the round-95 15dp minimum. The 1.6 multiplier always clears the ~1.31 line-height factor + the 2×2dp padding, so the centered pill can never be sliced again.
- **The opaque theme badge** — the chip is now a solid `surface` stadium with `onSurface` text: a deliberate tag pinned on the bar's right end, crisp over ANY kind color, the dimmed failed/skipped fills, the bare track, and in BOTH themes — never a shadow.
- **The centered line-height style** — `TextStyle(lineHeight = 12.sp, lineHeightStyle = LineHeightStyle(Center, Trim.None))` kills Roboto's bottom-heavy glyph offset inside the pill.

## 3. D-660 — Show Status on Extensions (the dots)

**The option:** the testing home gained the **Options section** at its very foot ("at the very bottom, just below the details and everything like that, there will be some spacing, and after that spacing the new section will start") — an extra 10dp spacer + the "Options" label + the first option row: **"Show Status on Extensions"** (quiet surface + title + one-line subtitle + Switch, the debug page's toggle anatomy). Backed by `AppPreferences.extensionsShowTestStatus` (persisted, default FALSE, Flow-backed) so the Switch never lies.

**The dots:** while ON, every **TRUSTED** row on BOTH extensions tabs carries a quiet 9dp circle at the very right of its delete button — hidden with the actions while selecting (the bottom bar owns the row then). The colors are the testing palette's existing six (D-594): Aniyomi pass = emerald `PassA`, CloudStream pass = sky `PassB`; Aniyomi fail = red `FailA`, CloudStream fail = orange `FailB`; never-tested = the two grays `NewA`/`NewB` — "the same hues the suite-health ring teaches." NO text anywhere.

**The verdict rules** (`ExtensionTestStatus.kt`, the new shared file): the store keeps ONE latest run per target id. A run is PASS when finished && !aborted && zero failures && ≥1 pass; FAIL when any result holds FAILED; everything else (never tested, aborted mid-run, nothing decided) reads as the neutral gray — an aborted run is not a failure and not a pass.

**The row aggregation:** aniyomi rows aggregate over `extension.sources.map { it.id }`; CloudStream rows over each provider's stable synthetic id re-derived through `CsSourceIds.idFor(provider.name)` — the SAME deterministic mint the bridge registers (`CsProviderSource.providerName` IS `CsProviderInfo.name`), so the ids match the stored runs bit-for-bit. Any failing source fails the row; otherwise any healthy source passes it; otherwise gray.

**Scope decisions (disclosed):**
- Only TRUSTED installed rows render dots. Untrusted/errored aniyomi models carry no `sources` list (no identity to look up), and CS untrusted/errored models carry no `providers` — those states are untestable anyway. Available/repo rows are not installed — nothing to test.
- A DISABLED extension keeps its ids and shows its LAST verdict (the store may hold runs from when it was enabled) — honest.
- The verdict map loads ONLY while the toggle is ON (zero cost otherwise) and reloads on every re-entry to the extensions page (leaving for the testing pages disposes that composition; returning rebuilds it — the run results land in between). Both passes memoized on their inputs (the D-658 rule).

## 4. D-661 — the source-picker rework

The Search page's Extensions-button sheet ("Pick a source"), three improvements per the report:

1. **THE SYSTEM SELECTOR** — when BOTH ecosystems have sources, a two-chip segmented row (Aniyomi · CloudStream — the `SourceTabChip` anatomy with the testing page's Tv/Cloud glyphs) swaps which list shows. It OPENS on the ecosystem of the currently selected source. One side empty → no selector, the single list renders exactly as before.
2. **THE SEARCH BAR** — the Settings search bar's anatomy at compact height (quiet rounded surface + magnifier + single-line field + the invariant-height 24dp clear Box, the D-559 rule), filtering the visible list by name, live; the query survives a system switch. Empty results get the quiet "No sources match your search." line.
3. **THE ALPHABETICAL ORDER** — both lists sort case-insensitively by name (the repository/map arrival orders were arbitrary); memoized on their inputs.

Also: the sheet's height cap rose 0.70 → 0.78 of the screen (the selector + the search bar now sit above the list, and the list should still show a real page of rows), and the CS-only case no longer renders its lone "CloudStream" header (consistency with the aniyomi-only case, which never had one).

## 5. Files touched (commit 612e3563)

| File | Change |
|---|---|
| `app/…/settings/SponsorDebugScreen.kt` | the dead-door fix (clickable + press scale) |
| `core/preferences/…/AppPreferences.kt` | `extensionsShowTestStatus` + flow + key |
| `feature/anime-search/…/ExtensionSourcePickerSheet.kt` | the full rework (selector + search + alphabetical) |
| `feature/extensions-settings/…/CloudstreamExtensionsSection.kt` | `testVerdicts` param + the CS dot |
| `feature/extensions-settings/…/ExtensionsSettingsScreen.kt` | the verdict maps + threading + the aniyomi dot |
| `feature/extensions-settings/…/testing/ExtensionTestStatus.kt` | NEW — verdict enum, aggregation, `TestStatusDot` |
| `feature/extensions-settings/…/testing/TestingHomeScreen.kt` | the Options section + the toggle |
| `feature/extensions-settings/…/testing/TestingTargetDetailScreen.kt` | the adaptive bar height + the opaque chip |

8 files, +664/−55.

## 6. CI

Run **36345112122** (612e3563) FAILED on the picker's imports — `animateColorAsState` was imported from `androidx.compose.animation.core` (it lives in `androidx.compose.animation` — the ROUND-92 LESSSON, repeated) and `LocalFocusManager` from `androidx.compose.ui.focus` (it lives in `androidx.compose.ui.platform`); the "Cannot infer type" was the unresolved-delegate cascade. Run **36345425001** (ebd1d480 — the two-line import fix) — inside the D-472 ≤2 budget.

## 7. Release

Per the standing D-565 loop: `release/1.1.53` cut from the round-96 ledger head; the bump (10120→10153, 1.1.20→1.1.53) rides the branch per D-430; the annotated tag `v1.1.53` with the what-you'll-see body; Release APK run → the debug release publishes (arm64-v8a + SHA256SUMS.txt) → LIVE-verified via the API.

## 8. The v1.1.53 device checklist

1. **The door:** Settings → HOLD "Debug options" 10s → toggle Extension Testing ON → tap "Open Extension Testing" → it NAVIGATES to the testing home now (press-scale feedback on the tap).
2. **The chips:** a target's full-details page → the stage bars: the duration badges fully visible (nothing cut), optically centered, NO dark shadow behind them, in both light + dark themes. If your system font scale is above 1.0 the bars grow WITH it and stay clean.
3. **The dots:** testing home → scroll to the bottom → the new "Options" section → toggle "Show Status on Extensions" → Extensions page: every trusted row on BOTH tabs ends in a small color dot (emerald/sky = pass, red/orange = fail, gray = new) just right of the delete button; toggle it off → the dots vanish.
4. **The dots' freshness:** run/re-run some tests → back to the Extensions page → the dots reflect the latest verdicts.
5. **The picker:** Search → the Extensions button → the "Pick a source" sheet: the Aniyomi/CloudStream chips (when both sides have sources), the search bar filtering live, and the lists in alphabetical order.
6. **The picker's selection:** picking from either side still browses that source; the checkmark still lands on the right row.

## 9. Follow-ups (ordered, NOT yet)

- FUTURE (carried): the Run-all target picker.
- Possible next round: dots on more row states if the user asks (untrusted/errored rows carry no source identity today — see §3's scope decisions).
