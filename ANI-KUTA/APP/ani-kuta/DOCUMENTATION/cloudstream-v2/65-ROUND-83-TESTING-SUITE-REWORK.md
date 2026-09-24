# 65 — ROUND 83: THE TESTING SUITE FIXED + REWORKED, the icon-pill header, the polished filters, and the linked-source-aware sheet

> Round 83 (D-577..D-579). The v1.1.39 device round: the uninstall prompt was
> STILL never appearing (a one-line permission was the whole story), the
> testing suite flagged KNOWN-WORKING anime extensions as broken in 19 ms (a
> dispatcher bug with an educational anatomy), and the source sheet / testing
> screen / filters got their second device-feedback pass.
>
> Code: `:feature:extensions-settings:impl` → `…/extensionssettings/testing/`
> (reworked + 6 new files), `ExtensionsSettingsScreen.kt` (header + filters),
> `ManualSearchSheet.kt` (linked-source awareness + the section-card sheet),
> `app/…/AndroidManifest.xml` (the D-577 permission).

---

## 1. D-577 — the uninstall fix: `REQUEST_DELETE_PACKAGES`

### 1.1 The anatomy

The user's round-83 log is a textbook signature:

```
12:18:14.463  Anikuta:Da...:Installer   I  Uninstalling: eu.kanade…animesrbija
12:18:14.536  SceneSDK…                 D  resumingActivityName=UninstallerActivity
12:18:14.587  OplusHansManager          I  front pkg: Anikuta, prev pkg: packageinstaller   ← 70 ms later
12:18:14.611  OplusTrans…ionManager     D  UninstallerActivity FINISHING
```

`startActivity()` SUCCEEDED (the packageinstaller came to front), then the
system's own `UninstallerActivity` finished silently ~70 ms in — no dialog,
no error, and the D-571 three-rung intent ladder never saw a failure because
no rung ever threw. **The platform uninstaller requires the CALLING app to
hold `android.permission.REQUEST_DELETE_PACKAGES`; without it the activity
starts and immediately finishes.** The manifest carried only
`REQUEST_INSTALL_PACKAGES` (installs are a different gate).

### 1.2 The fix

One permission declaration in `app/src/main/AndroidManifest.xml` + the
comment explaining the log signature. The D-571 flow (no in-app
confirmation — the system prompt IS the confirmation) now works as designed.

---

## 2. D-578 — the 19 ms bug + the smart search ladder

### 2.1 Why anime extensions "failed" in 19 ms while CloudStream passed

The round-82 test files called the source methods directly on the SCREEN's
coroutine scope — the MAIN thread:

| Test | Round-82 call | Failure mode |
|------|---------------|--------------|
| Search | `source.getSearchAnime(1, q, filters)` | Real aniyomi extensions do the network exchange at Rx-subscription time on the CALLING thread → instant `NetworkOnMainThreadException` (~19 ms) |
| Home page | `source.getPopularAnime(1)` | same, ~7 ms |
| Details / Episode list | `getAnimeDetails` / `getEpisodeList` | same (never reached — search failed first) |
| Video resolve (aniyomi) | `httpSource.getVideoList(episode)` | same |
| CloudStream resolve | `csResolver.resolve(...)` flow | PASSED — the bridge dispatches internally |

Production search never hits this: `SearchViewModel` wraps every source call
in `withContext(Dispatchers.IO)` (the D-304 era pattern). **All five test
files now do the same.** The CS bridge never needed the wrapper (its suspend
overrides dispatch internally — the same reason it passed from day one).

### 2.2 The smart phrase ladder (`TestingSearchPhrases.kt` + `SearchTest.kt`)

One query proving nothing means nothing (the user: "it should search for a
well-known phrase… a total of three or four well-known phrases, ranging from
various categories, like anime, movies, series"). The Search test now:

1. Builds the attempt list: the user's custom query FIRST (when typed, shown
   as "Custom query"), then the well-known set — **One Piece** (Anime),
   **Naruto** (Anime), **Breaking Bad** (Series), **Interstellar** (Movie).
2. Runs the ladder in order; the FIRST attempt returning ≥1 result WINS and
   its result becomes the chain's test anime (unchanged waterfall).
3. Each attempt is independently caught (a phrase erroring/timeout moves the
   ladder on; per-attempt inner bound 12 s) — the engine's kind timeout now
   caps the WHOLE ladder at **60 s** (was 20 s).
4. Only when EVERY attempt failed does the test fail — and the message says
   exactly what happened ("No results from any of the 5 test phrases",
   detail: "3 of 5 attempts errored — last: …" / "Every phrase returned an
   empty result list").

A blank custom query field = the smart ladder only (the field's hint says
so). The message carries the winning phrase + category: "47 results for
"One Piece" (Anime)".

### 2.3 Human time everywhere (`TestTimeFormat.kt`)

The user: "instead of 1,000 milliseconds it should show it in seconds…
instead of 1,000 seconds, minutes and seconds." One formatter for every test
message + UI label: `<1 s → "820 ms"`, `<60 s → "1.2 s" / "12 s"`,
`≥60 s → "2m 05s"`. Ping ("Responded HTTP 200 in 1.2 s") and Stream play
("delivered 64.0 KB in 0.8 s") use it in their messages too.

---

## 3. D-578 — the testing screen rework (the modular file split)

The device report, point by point, and where it landed:

| Report | Implementation |
|--------|----------------|
| "not a button itself… at the very top, just right of the extension text, alongside filters and settings… just show the icons, pill-shaped" | The header is three ICON-ONLY pills — **Testing (Science) · Filters · Settings** — and the round-82 banner is gone |
| "not given the option to individually test both the systems separately" | **All / Aniyomi / CloudStream** chips + per-section headers (title · count · select-all · run-section); each ecosystem is its own section |
| "no long press gesture to select… cannot multi-select without the checkbox" | **Tap AND long-press** toggle selection (haptic on long-press); the checkbox mirrors; the selection ring animates in |
| "when I tap… it expands their details… not great" | Expansion moved to the card's **chevron** — the only expand affordance |
| "a proper, beautiful, clean testing UI… the extension currently being tested… the details of it" | `TestingBatchOverlay`: slide-up card with "Testing 3 of 7", an animated progress bar, the current target's icon/name/ecosystem, its LIVE per-test rows, and Stop |
| "a proper summary of all the things" | `TestingSummaryCard`: counts, an animated **pass/fail/untested proportion bar** (the "graphs" ask), and the precision re-run buttons |
| "instead of relying too much on MS… seconds… minutes and seconds" | `TestTimeFormat` in every row/summary/message |
| "perform the tests on only the ones which were not working / working" | **"Failed (n)" / "Passed (n)"** re-run buttons (enabled exactly when their subset is non-empty) + "Clear results" |

### 3.1 D-579 — the memory (`ExtensionTestResultStore.kt`)

The user: "properly save the details of which were working, which were not…
the next time the user opens up the extension testing, he can properly see
them… perform the tests on only the ones which were not working."

- Storage: per-target JSON documents under the `extension_test_results`
  SharedPreferences file (`run-<targetId>` → { targetName, ecosystem,
  finished, testedAtMs, results{kind → status/duration/message/detail} }).
- Write: `saveTarget` fires the moment each target's run completes — a crash
  or a screen close mid-batch never loses completed verdicts.
- Read: the screen restores every stored run on first composition
  (targets show their last "Passed 5/7" / "2 failed" chips immediately);
  uninstalled targets are **pruned** on open.
- Clear: the summary card's "Clear results" wipes the store.
- WHY SharedPreferences + org.json (not SQLDelight/DataStore): tiny data,
  schema-free (absorbs new tests/renamed kinds with zero migration), and
  self-contained to the testing package — the doc-64 roadmap (statistics,
  automated runs) reads this same store.

---

## 4. D-578 — the extensions header + filters polish

- **Header pills icon-only** (the user: "no need to show the text with the
  filters and settings"): `HeaderPillButton(icon, contentDescription)` —
  pill-shaped, 9dp padding, active tint preserved.
- **Language menu**: styled container (`surfaceContainerHigh`, 16dp rounded,
  3dp elevation), a "Filter by language" header row, and the active language
  tinted + bold + checked (the "All languages" row included). Cap raised to
  360dp.
- **Sort menu**: the same container treatment, a live direction header
  ("Sort by — ascending ↑"), and the active mode bold + arrowed. The
  tap-active-flips-direction behavior is unchanged.
- **Dedicated search field**: a real border (outline pill), a boxed clear
  button on a surfaceVariant disc, a taller touch field, and the back arrow
  outside the field.

---

## 5. D-578 — the Link Source sheet: linked-source awareness + section cards

- **Pre-selection (the swapped-highlight report):** the sheet now receives
  `linkedSourceId` + `linkedSourceName` (the details screen's
  `effectiveLinkedSource` — covers BOTH AniList entries and extension
  entries). A wheel pre-centers on the linked source (id match, name
  fallback) and the linked row carries a persistent **✓ marker** wherever it
  sits — previously both wheels opened at index 0, so the "highlighted"
  entries were just the first of each list, unrelated to the actual link.
  The ACTIVE side defaults to the linked source's ecosystem.
- **Dedicated section cards:** each wheel lives in its own rounded card —
  Aniyomi on `surfaceVariant` (45%), CloudStream on `secondaryContainer`
  (30%) — with the label pill inside and rim fades matched to the CARD color
  (the round-82 rims dissolved into the sheet background and smeared the
  panels together).
- **Center highlight:** the centered row of the active wheel gets the band
  PLUS a full rounded row highlight (primary 16%) + bold text; the inactive
  wheel's center gets the muted variant. Falloff softened (alpha 0.62,
  scale 0.16) so the non-centered rows stay readable.
- **The hint tail is gone:** "Scroll or tap a source, then search below."
  (the "The centered source is the one that gets searched." sentence was
  never useful).
- **Compact sheet:** the sheet WRAPS its content (the round-82 `0.85f`
  screen fill wasted half the panel); only the results view is bounded
  (42% of screen height).
- **The search bar (the exact spec):** directly under the two cards; the
  clear X at the VERY RIGHT edge (the text field carries the weight); the
  leading magnifier vanishes the moment there is text or focus; the circular
  Search button appears ONLY while the bar is in use (idle = just the bar);
  a real border; bottom padding. The IME Search key performs the same
  action.

---

## 6. Verification ledger (round 83)

- Nesting-aware tokenizer balance on **all 18 touched files** — ALL BALANCED.
- `AndroidManifest.xml` minidom-validated — 11 permissions,
  `REQUEST_DELETE_PACKAGES` present.
- Full self re-reads of every rewritten file (one compile-class defect found
  and fixed pre-push: the nullable `currentState` non-smart-cast in the
  batch overlay).
- CI: implementation run on the mainline (see the progress.md ledger for the
  final count).

---

## 7. Device-round checklist (v1.1.40)

1. **Uninstall (aniyomi)** — trash icon → the SYSTEM prompt FINALLY appears
   (app name on top, "Do you want to uninstall this app?") → OK removes the
   extension; Cancel returns.
2. **Extension testing entry** — the Extensions header shows three icon
   pills (Science · Filter · Gear); the Science pill opens the suite; no
   banner on the list.
3. **Testing — smart search** — with the query field EMPTY, run a KNOWN
   WORKING anime extension: Search should now PASS ("results for "One
   Piece" (Anime)") instead of failing in 19 ms; the downstream tests
   (details/episodes/resolve/play) actually RUN now.
4. **Testing — sections + multi-select** — the All/Aniyomi/CloudStream chips
   switch sections; tap or long-press a card → the selection ring + checkbox;
   the chevron expands the test rows; "Failed (n)" re-runs only broken ones.
5. **Testing — memory** — run a batch, leave the screen, come back: the
   verdict chips are still there; "Clear results" wipes them; uninstall an
   extension and reopen → its card is gone (pruned).
6. **Testing — batch overlay** — select several → Test selected: the slide-up
   card shows "Testing 1 of N", the progress bar moves, live rows fill in,
   Stop aborts cleanly.
7. **Link Source sheet** — open it on a LINKED series: the linked source's
   wheel is pre-centered with a ✓; the two panels have distinct backgrounds;
   the hint is the short line; the search bar's X sits at the far right, the
   magnifier hides on focus, the circular button appears only when typing.
8. **Filters** — the Language/Sort menus look styled (header rows, active
   tint); the search field has a border and a boxed clear.
