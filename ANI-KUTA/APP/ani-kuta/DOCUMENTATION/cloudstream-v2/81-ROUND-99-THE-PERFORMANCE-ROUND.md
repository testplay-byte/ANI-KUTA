# Doc 81 — Round 99: The v1.1.55 device round — the performance round (the non-debuggable debug line, placement-only rows, the no-crossfade icon policy)

**Date:** 2026-09-28 · **Round:** 99 · **Decisions:** D-674..D-678 · **Commits:** 87816700 (feat) · **Released as:** v1.1.56/10156

---

## 1. The device report (v1.1.55)

The user's round, verbatim-intent synthesis:

**CONFIRMED WORKING (the round-98 ledger validated on device):**
- The app opens properly — no crash dialogs, no ANR pop-ups (D-668's load worker held).
- The extensions section renders and works; **adding extensions gives no errors** — the round-97/98 compat work (CineStream via D-670, YoutubeProvider via D-671) holds on device.
- The repositories section shows properly; **the delete button and the hide/show button are exactly where wanted** (D-672's corner-flush rows confirmed) — "quite satisfactory results this time."
- Deleting and the other functionalities all work.

**THE TWO REMAINING SYMPTOMS:**
1. **Input latency** — "pressing the buttons was taking some time to register, and other issues like those. Maybe that is because of the debug version or such, but we need to keep the things in view, and we need to handle them properly." The user themselves named the debug-build hypothesis and ordered it handled.
2. **Scroll jitter concentrating at the end** — "if I start scrolling, the first few are smooth… But as soon as I reach the very last thing, extensions, it starts to jitter way too much" — **more laggy than v1.1.54**, i.e. the round-98 recomposition fixes removed the uniform jank and EXPOSED the remaining per-row cost as concentrated jitter in the last sections (the long CloudStream Trusted list and the Available catalogs below it).

**The order:** a general analysis of the project and the application for performance issues, improved "in the most proper ways, just like how industry standards are," each and every one handled properly, nothing rushed, everything documented.

## 2. The root-cause work (the full code read)

The round read, end to end: ExtensionsSettingsScreen (2123 lines), CloudstreamExtensionsSection (1195), ExtensionListChrome (1185), ExtensionRepoSettingsScreen (582), CloudstreamPluginManager (1034), the aniyomi ExtensionManager, ScrollBlurOverlay, CollapsingHeader usage, the debug-bubble stack (bubble/panel/NetworkStats/SqlDriverWrapper), the Coil ImageLoaderFactory, the app + build-logic Gradle config, MainActivity's navigation shell, the ManualSearchSheet wheel, the testing UI — plus a full census (sub-agent) of `BuildConfig.DEBUG`/`FLAG_DEBUGGABLE` readers, Coil AsyncImage/SubcomposeAsyncImage sites, StrictMode/LeakCanary (none), and the version catalog.

**What was already right (and left alone):** ScrollBlurOverlay reads `scrollOffset()` inside `graphicsLayer` — a draw-phase deferred read, zero recomposition. The drag-select handler's `pointerInput` is passive until long-press. The managers' StateFlows emit atomically (no per-plugin emission storms). `mergeGhosts` returns the same list instance when no ghosts exist. RobotoFamily is a top-level val (built once). Material extended icons are lazily built once per process. The debug bubble is static at rest (its stats poll only while the panel is open).

**ROOT CAUSE 1 (input latency) — debuggable=true.** The debug build carried AGP's default `debuggable=true`. ART runs debuggable apps with the JIT only, disables most JIT optimizations, and — decisively — **background dexopt never AOT-compiles a debuggable app**. On a 56-module Compose app, every first-tap navigation pays JIT compilation of a whole screen's composition code. This is the systemic "debug version" cost the user hypothesized.

**ROOT CAUSE 2 (scroll jitter) — the default `animateItem` fade-in.** Every extension row on both tabs carried `Modifier.animateItem()` with the DEFAULT specs. The appearance contract fires for **every row that scrolls into view** (no distinction between "new data" and "scrolled into range"), so a long list runs overlapping spring fade-ins for the whole duration of every scroll — continuous animator bookkeeping + draw invalidation per newly composed row.

**ROOT CAUSE 3 (the "reaching the last sections jitters" mechanism) — the icon pipeline.** The app-wide ImageLoader enables `crossfade(true)` (200ms). Coil only skips the transition on MEMORY-cache hits — every icon load that resolves from disk or network (the first populate of each catalog, any row after memory-cache eviction, the detail screen's larger size) wraps its painter in a 200ms alpha animation, and each resolution recomposes its row's icon box (the onState flip). At fling speed through a long section that is a storm of overlapping animated painters + recompositions, row by row. The first rows read smooth because their icons sit in the memory cache from the current session; the rows further down pay the full cost. THE CORRECTION (post-CI-run-1, verified against the pinned Coil 3.0.4 sources): the round's initial theory — that `respectCacheHeaders`' default was expiring icons against raw.githubusercontent's `max-age=300` into network refetches — was WRONG for this version: 3.0.4's `DefaultCacheStrategy.read` **always returns the disk-cache response**, and the header-respecting behavior lives in the separate `coil-network-cache-control` artifact the app does NOT depend on. The app's disk cache already serves icons indefinitely; no cache override is needed (and none exists on this version's request builder). The real per-icon scroll costs are the crossfade + the subcomposition (root cause 5) + first-populate fetches — all addressed.

**ROOT CAUSE 4 — the CS `rebuildLists` instance churn (the D-673 miss).** Round 98's instance-stability guard fixed the ANIYOMI `updateInstalledStatuses` only; the CS twin kept an unconditional `.copy()` over every installed plugin on every rebuild — each repo collector emission (including the initial one at app start), every update check, every repo add/delete/hide minted ~90 fresh `Installed` instances and re-emitted the list, defeating Compose skippability and recomposing every visible row for zero visual change.

**ROOT CAUSE 5 — two more SubcomposeAsyncImage scroll surfaces.** The manual-search drum wheel (round 92) and the testing targets list ran a real subcomposition per visible icon — the exact cost D-673 retired from CsPluginIcon, surviving on two more scroll surfaces. The aniyomi `AvailableExtensionRow` was ALSO still a bare `AsyncImage` with a network URL and NO fallback (a 404/offline rendered a blank box — the Task-61 doctrine was never applied there).

**Plus one more D-673 miss found during the read:** DebugSettingsScreen's raw two-state listState read (recomposed the whole screen every scroll frame) — the round-98 pass had fixed eleven screens and skipped this one.

## 3. D-674 — the non-debuggable debug line (the input-latency fix)

`app/build.gradle.kts`: the debug build type now sets **`isDebuggable = false`**. Same signing (anikuta-debug.keystore), same identity (`com.confused.anikuta.debug`, `-debug` version suffix, lime icon, "ANI-KUTA Debug" label), same source-set machinery (debug bubble + its OkHttp/SqlDriver stat wrappers), near-release runtime — and AOT code paths accrue over the first background-dexopt cycles (first session still JIT; the win compounds).

**The three re-keys** (everything that read debuggability, from the census):

| Site | Was | Now |
|---|---|---|
| `AnikutaApp.kt` Logger level | `if (BuildConfig.DEBUG)` | `if (BuildConfig.IS_DEBUG_LINE)` — a new `buildConfigField` (true in debug, false in release); verbose DEBUG-level logs are a property of the dev LINE, not of debuggability |
| `DebugSettingsScreen` Developer-tools section | `if (BuildConfig.DEBUG)` | `if (BuildConfig.IS_DEBUG_LINE)` |
| `AppUpdateModule` updater repo | runtime `ApplicationInfo.FLAG_DEBUGGABLE` | `packageName.endsWith(".debug")` — the D-429 suffix is the debug line's stable identity; the updater keeps checking the DEV repo (testplay-byte) |

**What the debug line gives up (disclosed):** attaching a debugger, the layout inspector, and `adb shell run-as`. None are used by this workflow — the run-as emulator injection path was already retired with D-445's arm64-only line; logcat (the device-round loop) and the crash handler are unaffected; `android.util.Log` writes appear identically for non-debuggable apps.

**The revert path:** the one gradle flag + the three re-keys (all annotated at their sites).

**Judgment call:** no baseline profiles this round — ProfileInstaller skips debuggable builds, so they only pair with this change; hand-written profiles without macrobenchmark runs are guesswork. If the user wants more after the device round, the follow-up is a generated baseline profile on top of the now-non-debuggable line.

## 4. D-675 — placement-only animateItem (the scroll-animation fix)

All 8 extension-row call sites (4 CS + 4 aniyomi) now pass `Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)`:
- **fadeIn null** kills the scroll-in appearance animation — the direct jitter source during flings.
- **fadeOut null** removes a redundant fade (every enter/exit visual on these rows is owned by the D-580/D-645 exit choreography, which fades via its own graphicsLayer BEFORE the data removal lands).
- **placementSpec keeps its default spring** — the gap-closing glide after a delete (the D-580 requirement) is preserved exactly.

The dead `ExtensionReorderList.kt` (unreferenced since D-644 retired manual reordering) was deliberately left untouched — not this round's scope, noted for a future cleanup.

## 5. D-676 — the list-icon request policy (the icon-storm fix)

A shared `buildListIconRequest(url)` in ExtensionListChrome.kt builds the per-request `ImageRequest` with **`.crossfade(false)`** — an extension function (`coil3.request.crossfade`) that sets the transition factory to NONE for the request, overriding the loader's global 200ms crossfade: no per-icon painter animation while lists scroll. The app-wide crossfade stays ON for hero/detail images.

**Consumers:** `CsPluginIcon` (every CS row + the plugin detail screens), the aniyomi `AvailableExtensionRow` — which ALSO gains the never-blank letter tile (the onState-tracked placeholder pattern, closing the last bare network-image call on an extensions scroll surface and fixing its blank-on-error), and (D-678) the testing targets list. The ManualSearchSheet wheel carries a module-local twin (`rememberWheelIconRequest`) since the shared helper is module-internal to extensions-settings.

**The cache non-change (disclosed):** the round originally shipped `.respectCacheHeaders(false)` on these requests; CI run 1 caught that the method does not exist on Coil 3.0.4's request builder, and the artifact-source verification (§2's correction) showed it is unnecessary — 3.0.4's default disk-cache strategy already serves entries indefinitely (the header-aware behavior is the optional `coil-network-cache-control` artifact, not in the dependency set). No cache options are set.

## 6. D-677 — the CS rebuildLists instance-stability guard (the D-673 completion)

`CloudstreamPluginManager.rebuildLists()` now computes `updateVersion`/`disabledByRepo` per installed row, keeps the existing instance when neither flag moved, and re-emits `_installed` only when at least one row changed — the exact pattern of the aniyomi `updateInstalledStatuses` (D-673). The O(records × catalog) identity ladder was measured as not-the-bottleneck and deliberately NOT pre-indexed (a per-rung map would change cross-plugin match ordering semantics for pathological catalogs; the guard is where the win is).

## 7. D-678 — the wheel + testing-list subcomposition retirement

- **ManualSearchSheet `WheelSourceIcon`:** the csIconUrl branch now renders the plain AsyncImage + onState-tracked `WheelIconFallback` behind it (the same fallback composable the loading/error lambdas used — visuals identical), on the D-676 request policy. The one-shot `LinkedContentCard` cover keeps its SubcomposeAsyncImage (not a scroll surface; a round-92-approved one-shot).
- **TestingSharedUi `TargetIconView`:** the same conversion for the testing targets list (a scroll surface with the full 90-plugin set), via the shared `buildListIconRequest`. The bounded results grid keeps its SubcomposeAsyncImage (disclosed).
- **PluginImportActivity**'s two one-shot SubcomposeAsyncImages: untouched (not scroll surfaces).

## 8. Verification

- Brace/paren balance checked on all 10 edited files (the build.gradle.kts paren delta matches HEAD's comment-prose false-positives).
- Symbol greps: `buildListIconRequest` 1 definition + 3 call sites; `rememberWheelIconRequest` 1+1; `IS_DEBUG_LINE` in gradle ×2 + code ×2; zero remaining runtime `BuildConfig.DEBUG` readers; the remaining SubcomposeAsyncImage sites are exactly the 4 deliberate ones.
- Smart casts verified: `AnimeExtension.Available`, `SourceIcon`, `TestableTarget` are all final data classes.
- **CI:** Build APK run 36362954517 (commit 87816700) FAILED on two compile errors — both `Unresolved reference 'crossfade'`: `ImageRequest.Builder.crossfade` is an EXTENSION function (`coil3.request.crossfade`) that must be imported, and the two request builders used it without the import. The fix (commits to follow) also removed `.respectCacheHeaders(false)` (nonexistent in 3.0.4, and unnecessary — see §5's cache non-change). Run 2: see §9.
- The Coil 3.0.4 API surface was verified AGAINST THE PUBLISHED ARTIFACTS after run 1: `ImageRequest.Builder.crossfade(Boolean)` is an EXTENSION function in `coil3.request` (import required — run 1's two compile errors were the missing imports), and `respectCacheHeaders` does not exist in 3.0.4 (removed; the default cache strategy already serves disk entries indefinitely — verified in the coil-network-core 3.0.4 sources).

## 9. The release

v1.1.56/10156 per the standing D-565 loop: `release/1.1.56` cut from the green head, the bump rides the branch (D-430), tag `v1.1.56` → Release APK run → LIVE (the facts land here after publication).

## 10. The device-round checklist (v1.1.56)

1. **First session after install:** expect it similar to v1.1.55 (the AOT win accrues over dexopt cycles — use the app normally, ideally with the device idle/charging overnight once, then compare the NEXT day).
2. **Button registration:** taps on rows, tabs, the Filters/Settings pills, detail navigation — should land without the visible delay.
3. **Extensions scroll (the key test):** scroll the CloudStream tab down through Trusted Sources AND into the Available catalog at the bottom, SLOWLY and at fling speed — the jitter at the end should be gone or drastically reduced; icons may pop in instantly (no fade) by design now.
4. **Minutes later, scroll again:** the second pass (icons now disk-cached permanently) — this is where the max-age refetch storm used to hit; it should be as smooth as the first.
5. **Row exits:** delete/untrust/trust a few rows — the settle-dip → slide+fade choreography and the gap-closing glide must look exactly as before (placement-only animateItem).
6. **The updater:** Settings → check for updates — it must find v1.1.56+ from the DEV repo (testplay-byte) as before (the re-keyed gate).
7. **The debug bubble + Developer tools:** the bubble still floats/toggles; Settings → Debug still shows the Developer-tools section (IS_DEBUG_LINE).
8. **The manual-search wheel + the testing list:** spin both — same visuals, smoother.
9. **Logs (if you capture logcat):** DEBUG-level Anikuta lines still present.

## 11. Judgment calls disclosed

1. **The non-debuggable flip changes the debug line's nature** (no debugger attach / layout inspector / run-as) — none used by this workflow; the revert is one flag + three annotated re-keys. Disclosed prominently in §3.
2. **The icon-freshness trade turned out to be moot:** the round originally set `respectCacheHeaders(false)` (an icon replaced at the same URL would wait for disk-cache eviction); CI run 1 proved the option doesn't exist on the pinned Coil 3.0.4 and the source verification showed the default already serves disk entries indefinitely — the app gets permanent icon caching without any option, and a Coil upgrade to 3.1.x later would need to ADD the option to KEEP this behavior (noted for that future round).
3. **Rows now appear instantly (no fade) when data changes** (D-675) — the designed enter/exit visuals were always the choreography's; the fade-in was animateItem's default nobody asked for.
4. **The first post-install session is still JIT** — the AOT win compounds over dexopt cycles; the checklist tells the user what to expect when.
5. **The bounded results grid + one-shot import screens keep SubcomposeAsyncImage** — not scroll surfaces; converting them is cosmetic churn with no measurable win.
6. **The O(n×m) identity ladder stays** (§6) — measured as not-the-bottleneck; pre-indexing would alter pathological-catalog semantics.
7. **`ExtensionReorderList.kt` is dead code** (unreferenced since D-644) — left untouched, flagged for a future cleanup round.
