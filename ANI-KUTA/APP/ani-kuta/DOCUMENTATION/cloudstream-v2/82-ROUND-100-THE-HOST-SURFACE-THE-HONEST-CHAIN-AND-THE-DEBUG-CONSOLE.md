# Doc 82 — Round 100: The v1.1.56 device round — the host-surface fix, the honest test chain, and the debug console

**Date:** 2026-09-28 · **Round:** 100 · **Decisions:** D-679..D-683 · **Commits:** 53236141 (feat) · **Released as:** v1.1.57/10157

---

## 1. The device report (v1.1.56)

The user's round, verbatim-intent synthesis:

**CONFIRMED WORKING (the round-99 performance work validated on device):**
- "This time the performance improvements are real. The performance has been improved in the extensions page. The scrolling is much more smoother. It is much more well handled and proper." — D-674..D-678 held.

**THE NEW REPORT (the extension TESTING system, plus a main-app playback failure):**
1. **The stats numbers line-break** — "the main issue which I saw at first sight was the stats, and just right of the stats it shows the details, the numbers, but those numbers apparently have line breaking in them, which is not a good experience."
2. **False negatives in the tester** — extensions that WORK fail the tests: YouTube failed HOME_PAGE ("Popular / home page returned no entries") and the failure cascaded through DETAILS ("Not run — Home page failed"), EPISODE_LIST, VIDEO_RESOLVE, STREAM_PLAY — *even though SEARCH PASSED with 6 results in the same run*. Cinefreak failed VIDEO_RESOLVE with `NoClassDefFoundError: Failed resolution of: Lio/ktor/http/URLUtilsKt;`.
3. **The SAME failure in the MAIN APP** — real playback of Cinefreak content (Death of a Unicorn) dies at resolve time with the identical `NoClassDefFoundError: io/ktor/http/URLUtilsKt` — "even though it has working streams available… it was giving me errors like these." The extension works in the real CloudStream app.
4. **Debug logging** — "improve the debug logging currently, because we are in the debug version. We should have proper console logging for it and such… improve the logcat logging tool for the debug version and make it much more proper and much more better."

**The order:** make the tests robust ("they do not give false negatives or false positives"), fix everything from the ROOT CAUSE rather than patching, nothing rushed, everything planned/documented.

## 2. The root-cause work (hard evidence, not theories)

### 2.1 The Cinefreak `NoClassDefFoundError` — a HOST-SURFACE gap (the main-app crash)

**The dex forensics.** The user's logcat showed `com.cinefreak.UtilsKt.getBaseUrl(Utils.kt:62)` → `io.ktor.http.URLUtilsKt` missing. We downloaded the user's actual `.cs3` builds and parsed the dex class tables directly:

| Build | Defines `com.cinefreak.*` | References `io/ktor/http/*` | Bundles ktor? |
|---|---|---|---|
| xr3ed `Cinefreak.cs3` v16 (119 KB — the user's) | 88 classes | `URLUtilsKt`, `Url`, `URLProtocol` | **NO** — ktor is compileOnly against the HOST |
| mahmood `Cinefreak.cs3` v9 (68 KB, current source uses `java.net.URI`) | — | none | n/a (rewritten upstream) |

**The host comparison.** The real recloudstream prerelease APK (75.5 MB, 8 dex files) **ships ktor** — `io/ktor/http/URLUtilsKt` lives in its classes6.dex (pulled in via `coil-network-ktor3` + the explicit `ktor = 3.5.0` catalog pins). That is why the same extension works there and dies here: the plugin's classloader is PARENT-FIRST; the parent (our app) simply did not carry the classes the plugin was compiled to expect from its host.

**The corpus scan (the industry-standard way to size a plugin host's provided-API surface).** We scanned **309 `.cs3` files** across the user's five extension repos (xr3ed-Repo, SaurabhKaperwan CSX + TestPlugins + HindiProviders, mahmood's phisher mirror), parsing every dex's type table for referenced-but-not-defined classes outside the host's own packages:

| Host-provided library | Corpus plugins referencing it | Our app before this round |
|---|---|---|
| Jackson (`com.fasterxml.*`) | 196 | ✅ already `api` in :core:cloudstream-api |
| Gson | ~16% | ✅ already `api` |
| jsoup / NewPipe / okhttp / nicehttp | many | ✅ already shipped |
| **ktor-http (`io/ktor/http/*`)** | **21** (Cinefreak, StreamPlay, AnimePahe, Anichi, Desicinemas…) | ❌ **MISSING — the crash** |
| ksoup (`com.fleeksoft.ksoup`) | 7 | ❌ missing |
| fuzzywuzzy (`me.xdrop`) | 1 | ❌ missing |

All 21 ktor users reference ONLY `io/ktor/http/*` (none touch ktor-client/ktor-utils directly) — ktor-http alone (+ transitives) is the correct minimal bundle. → **D-679**.

### 2.2 The YouTube cascade — TWO stacked bugs

**Bug A — the engine's prerequisite gate had inverted anyOf semantics.** The gate read:

```kotlin
val missing = test.requiresAnyOf.filter { results[it]?.status != PASSED }
if (requiresAnyOf.isNotEmpty() && missing.isNotEmpty()) { FAIL("Not run — …") }
```

`missing.isNotEmpty()` fails the stage unless **EVERY** feeder passed — allOf under an anyOf name. The YouTube logcat is the proof: SEARCH PASSED (6 results) yet DETAILS failed with "Not run — Home page failed". The chain had a perfectly good search pool to walk (the candidate-walk machinery already exists) and reported 5 failures for a working extension. → **D-680**.

**Bug B — the home-page probe judged the provider by its FIRST shelf only.** The bridge's `getPopularAnime` fetched shelf #1 and equated empty-with-broken. The YouTube provider's seeds: shelf #1 ("CERITA KAMAI") is a **channel-mode** category whose NewPipe fetch fails silently inside the plugin (`runCatching → emptyList`); shelves #4+ are search-mode queries — the same machinery that PASSED search in the same run. CsBrowseLoader (the browse UI) has always walked every shelf; the tester and the aniyomi Popular/Latest paths never did. → **D-681**.

### 2.3 The stats line-breaking — fixed-dp number columns vs sp-driven digits

`HealthLegendRow` (the legend table beside the suite-health donut) sized its number columns in fixed dp (28dp for the 13sp total, 22dp for the 11sp splits). Digits are sp — any font scale above ~1.0 grew the glyphs past the column and Compose wrapped the number mid-digit ("1" over "0"). The stats page's `CountUpText` numbers had the same latent risk (width-constrained by `weight(1f)` cells). → **D-682** (the D-659 bar-height pattern applied to columns: sp-derived widths + `maxLines=1` + `softWrap=false`).

### 2.4 The debug console — what exists vs what was asked

Round 24 removed the in-app console capture ("remove the console logs only"); the Logger has been logcat-only since. The user now asks for proper console logging on the debug version + a better logcat tool. The honest scope: nothing should log MORE — the tool should make the lines the app ALREADY emits browsable on-device. → **D-683**: a bounded ring buffer inside the Logger (debug-line-gated) + a debug-bubble Console tab with an app-console source AND a live `logcat --pid` feed (Android lets an app read only its own process's logs — no READ_LOGS needed), with level filter, search, follow/pause, copy/share/clear.

## 3. D-679 — the host-provided plugin dependency surface (the playback root cause)

`:core:cloudstream-api` gains three `implementation` deps (parent-first runtime resolution — the same exposure pattern as NewPipeExtractor, D-671):

| Library | Version | Why this version |
|---|---|---|
| `io.ktor:ktor-http` | **3.5.0** | The exact pin the current recloudstream host ships (their `libs.versions.toml`) — the ABI the working-on-upstream plugins were compiled against. Transitive ktor-utils/-io ride along. |
| `com.fleeksoft.ksoup:ksoup` | **0.2.6** | Upstream's pin. |
| `me.xdrop:fuzzywuzzy` | **1.4.0** | Upstream's pin. |

All Apache-2.0 (the round-97 GPL doctrine is untouched — no license disclosure needed beyond the existing one). The APK grows by the ktor-http/ktor-utils/ktor-io + ksoup + fuzzywuzzy dex (expected ~3-4 MB — the price every CloudStream-compatible host pays; upstream's APK carries the same set).

**The loader's diagnosis hint (the debug-log improvement that makes the NEXT gap a one-glance diagnosis):** a plugin load failing with `NoClassDefFoundError` / `NoSuchMethodError` now logs a targeted WARN naming the missing symbol and the host-surface concept — the v1.1.56 round needed a full research session to distinguish "broken plugin" from "host gap"; the next one reads one log line.

## 4. D-680 — the anyOf gate fix

```kotlin
if (test.requiresAnyOf.isNotEmpty() &&
    test.requiresAnyOf.none { results[it]?.status == TestStatus.PASSED }
) { FAIL("Not run — ${labels} failed") }
```

A stage runs when AT LEAST ONE feeder passed; the "Not run" verdict fires only when NONE did (then the label list is by definition the full set — the D-590 message contract holds). Single-element sets (PING gating HOME_PAGE/SEARCH — the D-646 hard-prerequisite rule) are semantically unchanged: `none{passed}` ≡ `missing.isNotEmpty()` when the set has one element.

**The YouTube chain after the fix:** HOME_PAGE still fails (honestly — see D-681 for the shelf walk that will usually make it pass) but SEARCH passed → DETAILS walks the search pool → EPISODE_LIST → VIDEO_RESOLVE → STREAM_PLAY all RUN. A working extension can finally read HEALTHY with one broken shelf.

## 5. D-681 — the shelf walk (browse parity for the popular probe)

`CloudstreamAnimeSourceBridge.getPopularAnime`:
- **page 1** walks the provider's shelves IN ORDER, capped at 10 (a pathological 20-shelf provider answers "empty" after 10 honest tries, not 20 requests). The first shelf whose response yields ≥1 entry wins; the winning shelf index is remembered for pagination. Per-shelf failures are TOLERATED and logged (CsBrowseLoader's D-390 rules); CancellationException propagates.
- **Cloudflare**: a block on one shelf continues the walk; if EVERY tried shelf blocked, the block rethrows (a challenge page is never "no results" — the browse contract).
- **The all-empty walk**: if at least one shelf ERRORED and none answered OK, the last error rethrows (the honest provider failure — the pre-round single-shelf behavior); a clean-but-empty walk returns the empty page.
- **page > 1** paginates the REMEMBERED winning shelf (legacy shelf-#1 fallback for a fresh instance).

This fixes the tester's HOME_PAGE false negative AND the aniyomi-side Popular/Latest tabs (they rode the same first-shelf-only bridge).

## 6. D-682 — the never-wrapping number columns

`HealthLegendRow`: `totalColumnWidth = 13.sp.toDp() * 2.4f`, `splitColumnWidth = 11.sp.toDp() * 2.4f` (fits 4 digits at Roboto's ~0.56em digit width, growing with font scale exactly like the glyphs), every number `maxLines=1 + softWrap=false`. `CountUpText` (the stats page's big numbers) gets the same two flags. The donut-center percentages were audited (4 glyphs × 22sp ≤ 98dp at fontScale 2 vs the 128dp ring; 4 × 14sp ≤ 62dp vs 72dp) — they fit; left alone.

## 7. D-683 — the debug console

**The Logger (:core:common)** gains a bounded in-memory ring buffer: 2,500 entries, `ArrayDeque` under a lock, a monotonic `consoleRevision` for cheap polling, and `ConsoleEntry(atMs, level, tag, message, errorDigest)` — the digest carrying the throwable's class + message + first 3 stack frames. `setConsoleCaptureEnabled(true)` is called ONLY on `IS_DEBUG_LINE` (AnikutaApp.onCreate, right after the existing Logger setup) — release builds never allocate a single entry. The round-24 removal stays honored in spirit: no plugin-log forwarding, no extra logging — only our own Logger lines are captured.

**The Console tab (debug-bubble)** — a new `DebugTab.CONSOLE` (Terminal icon) wired into BOTH the expanded panel and the minimized mini-window:
- **Two sources**: "App console" (the ring buffer, live-polled at 400 ms on revision change) and "Logcat" (a live `logcat --pid=<pid> -v time -T 1` stream — everything THIS process prints, including OkHttp/ART lines that never pass our Logger; the subprocess is `destroyForcibly()`'d the moment the tab leaves composition).
- **The tooling**: level filter chips (All/D/I/W/E), a search box (tag+message), follow/pause auto-scroll, copy-all / share / clear, tap-any-line-to-copy.
- **The look**: fixed dark-terminal palette (the debug-panel family's constant-colors rule — `#16110E` warm near-black, the family's cream/amber/golden accents, per-level semantic colors).
- Release builds: the bubble itself is a no-op in the release source set — the console is debug-only by construction.

## 8. Safety, CI, and the honest history

- Brace/paren balance verified on all 13 touched files (the checker's two flagged deltas both matched HEAD's pre-existing char-literal/string-template false positives — re-verified with a char-literal-aware pass).
- The `checkDependencyAlignment` guard (D-322) only covers compose/lifecycle groups — the new libs pass untouched.
- Build APK run **36405719954** on commit 53236141 — see §10 for the result and the release.

## 9. Judgment calls (§11 doctrine)

1. **ktor 3.5.0, not 2.x**: the corpus plugins work on the CURRENT upstream prerelease (which ships 3.5.0) — that is the compatibility target the user actually uses. `URLUtilsKt.takeFrom`'s signatures are stable across the 2.x/3.x boundary anyway, but we pin what the working host pins.
2. **The shelf-walk cap is 10, not ∞**: CsBrowseLoader walks ALL shelves for the browse page (different surface, different budget); a TEST probe and the Popular tab need the first shelf WITH content, and 10 covers every real provider we scanned (max seen: 13 shelves, 3 channel-mode first).
3. **The all-errored walk rethrows instead of returning empty**: the pre-round behavior for a single erroring shelf was an honest exception (the Cinefreak VIDEO_RESOLVE log showed exactly that shape); keeping it means a genuinely-broken home page still reads as a failure, not a quiet "no entries."
4. **The console captures ONLY our Logger lines** (no `com.lagradost.api.Log` sink forwarding — that was part of what round 24 removed); the logcat source covers everything else on demand.
5. **No `repo.json`/catalog-side changes** for ktor/ksoup/fuzzywuzzy — they are host-provided (like NewPipe), not plugin-visible API (unlike jsoup/Jackson which are `api` because plugin-visible signatures expose them).
6. **APK size grows ~3-4 MB** (the three libs + transitives). Disclosed: this is the compatibility price; upstream pays it too. If the user objects, ktor-http alone (1 plugin family fewer covered per dropped lib) can be re-scoped.

## 10. The device-round checklist (v1.1.57)

1. **Cinefreak playback in the MAIN APP** — open a Cinefreak title → episode → resolve → play. The `NoClassDefFoundError` is gone; streams should resolve (the same content that failed in v1.1.56).
2. **The YouTube test run** — expect HOME_PAGE to PASS via the shelf walk (a search-mode shelf answers), SEARCH to pass, and DETAILS/EPISODE_LIST/VIDEO_RESOLVE/STREAM_PLAY to RUN (no more "Not run — Home page failed" cascade). A dead first shelf no longer kills the chain.
3. **The Cinefreak test run** — VIDEO_RESOLVE should now resolve links (ktor-http present); STREAM_PLAY should range-fetch real bytes.
4. **The stats numbers** — Settings → the testing system: the legend's numbers (total + the A/B split) render on ONE line at your font scale (bump the font scale to be sure — they can no longer break mid-number by construction).
5. **The debug console** — the debug bubble's Console tab: switch between App console and Logcat; try the level chips, the filter box, follow/pause, copy-all, share, clear; tap a line to copy it. While an extension test runs, watch the live tail.
6. **The loader diagnosis** (only if some OTHER extension still fails to load): the logcat now says exactly which host class is missing — copy that line into the next report.

## 11. Files touched

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | ktorHttp/ksoup/fuzzywuzzy versions + library entries (D-679) |
| `core/cloudstream-api/build.gradle.kts` | the three `implementation` deps (D-679) |
| `data/cloudstream/.../loader/CloudstreamPluginLoader.kt` | the missing-class diagnosis hint (D-679) |
| `feature/extensions-settings/.../testing/ExtensionTestEngine.kt` | the anyOf gate fix (D-680) |
| `data/cloudstream/.../content/CloudstreamAnimeSourceBridge.kt` | the shelf walk + pagination memory (D-681) |
| `feature/extensions-settings/.../testing/TestingHomeScreen.kt` | never-wrapping legend columns (D-682) |
| `feature/extensions-settings/.../testing/TestingCharts.kt` | CountUpText never-wraps (D-682) |
| `core/common/.../Logger.kt` | the console ring buffer (D-683) |
| `app/.../AnikutaApp.kt` | capture enabled on IS_DEBUG_LINE (D-683) |
| `feature/debug-bubble/.../data/DebugLogcatReader.kt` | NEW — the live process logcat source (D-683) |
| `feature/debug-bubble/.../panel/ConsoleTab.kt` | NEW — the console UI (D-683) |
| `feature/debug-bubble/.../DebugTab.kt` | the CONSOLE entry (D-683) |
| `feature/debug-bubble/.../DebugPanel.kt` | the tab wiring, expanded + mini (D-683) |
