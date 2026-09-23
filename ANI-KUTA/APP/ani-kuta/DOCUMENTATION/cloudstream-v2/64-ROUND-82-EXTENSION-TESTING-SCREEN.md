# 64 — ROUND 82: THE EXTENSION TESTING SCREEN + the extensions/settings polish pass

> Round 82 (D-571..D-576). The v1.1.38 device round: the uninstall flow fixed
> (the SYSTEM prompt is the confirmation), the Link Source sheet rebuilt as a
> two-column alarm-clock source wheel, the search/filters QoL pass, repo
> long-press copy — and the headline feature: the **Extension Testing screen**.
>
> This doc is the testing system's architecture record + its future roadmap.
> Code lives in `:feature:extensions-settings:impl` → `…/extensionssettings/testing/`.

---

## 1. The Extension Testing screen (D-576)

### 1.1 What it is

A first-class screen (Settings → Extensions → the **Extension testing** banner
at the very top) where the user can see WHICH of their installed sources —
aniyomi extensions AND CloudStream plugins — actually work, and WHY the others
don't. Minimal, clean, feature-rich: a suite summary, per-source cards with
expandable per-test rows, per-card runs, and batch runs with a sequential
queue and a Stop button.

### 1.2 The test chain (the waterfall)

| # | Test | What it does | Pass condition | Timeout |
|---|------|--------------|----------------|---------|
| 1 | **Ping** | HEAD (GET fallback) against the source's `baseUrl` | ANY completed HTTP exchange (a 403 wall = the site is reachable) | 15 s |
| 2 | **Search** | `getSearchAnime(1, query)` with the screen's configurable query | ≥1 result; the first result becomes the chain's test anime | 20 s |
| 3 | **Home page** | `getPopularAnime(1)` (CS bridge: the provider's first shelf) | ≥1 entry; fallback anime provider if Search failed | 20 s |
| 4 | **Details page** | `getAnimeDetails(anime)` | the source answers without throwing | 25 s |
| 5 | **Episode list** | `getEpisodeList(anime)` | ≥1 episode; the list feeds the resolve test | 25 s |
| 6 | **Video resolve** | aniyomi: suspend `getVideoList(episode)`; CS: `CloudstreamLinkResolver.resolve(providerName, episodeUrl)` | ≥1 usable `http` stream URL lands in the context | 45 s |
| 7 | **Stream play** | Range-GET (`bytes=0-65535`) on the resolved URL with the stream's own headers | HTTP 200/206 AND ≥1 byte actually delivered | 20 s |

The chain is a **waterfall** (`ExtensionTestContext`): Search/Home write the
test anime; Details/Episodes consume it; Resolve consumes the first episode;
Stream consumes the resolved URL. A test whose prerequisite data is missing is
**SKIPPED with the reason** ("Needs a passing Search/Home page test") — never
a confusing failure.

Both ecosystems route through the SAME chain because the CS providers are
bridged as `AnimeHttpSource`s — only the **Video resolve** step branches:
aniyomi sources resolve via their own suspend `getVideoList` (judging THE
SOURCE, not our resolver layer), CloudStream providers via the dedicated CS
link resolver (the exact path the CS watch screen plays through). The CS link
is captured field-by-field (url/name/referer/headers) so the testing module
never needs `:core:cs-player` on its classpath.

### 1.3 The modularity contract (the user's requirement)

> "If one test is failing, then we can easily modify it without affecting the
> files and parts of others."

- **One file per test** (`tests/PingTest.kt`, `tests/SearchTest.kt`, …) — each
  implements `ExtensionTest` (kind + `requiresAnyOf` + `run(context)`).
- `ExtensionTestEngine` knows nothing about the individual tests — it walks
  whatever list `ExtensionTestChain.build(...)` gives it, enforcing per-kind
  timeouts (`withTimeout`), catching ANY Throwable (plugin bytecode lesson:
  `NoClassDefFoundError` etc. become honest FAILED results), and emitting every
  state change live (Pending → Running → terminal).
- **Adding a test = 3 steps, nothing else changes:** (1) add an
  `ExtensionTestKind` entry (label/description/timeout), (2) write its file,
  (3) append it to `ExtensionTestChain`.
- Zero DI changes: the engine + tests are constructed inside the screen from
  the already-injected managers/resolver + a self-contained OkHttpClient.

### 1.4 The screen

- **Targets** are built from `extensionManager.sources` split by
  `isCloudStreamBridged`: aniyomi extensions first (name-sorted; the parent
  extension's Drawable icon), then CloudStream providers (the parent plugin's
  `iconUrl`; the provider name is the resolver key). Letter-tile fallbacks
  everywhere.
- **Cards**: checkbox (batch selection), icon, name + `ecosystem · lang`,
  a status chip (Testing… / Untested / Passed n/7 / "N failed" / Incomplete —
  a cancelled run is NEVER marked finished or healthy), an expand chevron, and
  a per-card Run button.
- **Expanded rows**: one row per test — status icon (dot / spinner / ✓ / ✕ /
  skip), the test label, its message ("24 results for \u201Ctest\u201D",
  "12 episodes", "Stream answered HTTP 206 and delivered 64.0 KB in 340 ms"),
  and the duration in ms.
- **Batch**: "Run all tests" in the summary card, or select N and use the
  bottom bar's "Test selected". Runs are SEQUENTIAL (one network hammer at a
  time). Stop cancels the job; killed targets settle to "Incomplete".
- **Test query**: a user-editable field (default "test") used by the Search
  test; later tests reuse its first result (falling back to the home page).
- **Navigation**: `ExtensionTestingKey` (@Serializable, api module) + the
  MainActivity dispatch branch + the entry banner. The key is deliberately NOT
  in `allowedUpdateSheetKeys` — a batch run should not be interrupted by the
  update sheet.

### 1.5 The future roadmap (the door the user asked to keep open)

Everything hangs off the same models — the screen is the first consumer, not
the last:

1. **Automated testing** — a WorkManager-scheduled runner calling
   `ExtensionTestChain.build(...)` per installed source on a cadence, storing
   the emitted `TestResult`s (the chain is already UI-free).
2. **Statistics** — a persisted per-target history table (pass rate, latency
   trends, flakiness) rendered on the card + a stats page.
3. **Extension detail integration** — the detail screens can deep-link into
   the testing screen pre-filtered to one target.
4. ** richer stream checks** — an optional real-playback smoke test via the
   player stack (out of scope for round 82 by design).

---

## 2. The rest of the round (the extensions/settings polish)

| Decision | Change |
|----------|--------|
| **D-571** | The Aniyomi uninstall: the three in-app dialogs REMOVED — the trash icon fires the system uninstaller directly (Android's own "Do you want to uninstall this app?" prompt IS the confirmation, exactly as the user specified). `ExtensionInstaller.uninstallApk` = a logged 3-rung ladder (ACTION_DELETE → legacy UNINSTALL_PACKAGE → App-info) with a toast on every fallback — a silent no-op is impossible. The REAL silent bug fixed on the CS side: `uninstallPlugin`'s unguarded `loader.unloadPlugin` throw now caught, the reason published via `uninstallError: StateFlow<String?>` and toasted by the Extensions screen, `refreshLocked()` in `finally`. The CS tab keeps its in-app confirm (plugins are files — no system uninstaller exists). |
| **D-572** | The Extensions header's Filters/Settings = labeled pills with a gap; the filters bar = a pill row (Search / Language / Sort / NSFW); the SEARCH pill expands into a dedicated auto-focused search view (AnimatedContent, query survives both ways); the language menu capped at 320dp (scrollable — no more full-screen lists); tapping the ACTIVE sort flips ↑/↓ (arrow + "Sort: Name ↑" label; threaded through BOTH tabs via the `ascending` flag); NSFW is its own toggle pill. |
| **D-573** | The main SearchBar: the left icon is decorative; a filled primary circle at the RIGHT edge is the submit trigger (the user's placement), full + compact sizes. |
| **D-574** | Repository rows: long-press copies the URL (haptic + clipboard + toast) on both repo types + a discoverability hint line. |
| **D-575** | The Link Source sheet REWRITTEN: two snap-fling wheel columns (Aniyomi left / CloudStream right) — `rememberSnapFlingBehavior`, a center highlight band, distance-driven alpha/scale falloff, rim gradient fades, tap-to-center; per-source icons both ecosystems; the ACTIVE wheel's centered source is the search target (its label pill lights up, the field's placeholder names it); the pill search bar with the RIGHT circular button; a results view with a change-source chip (local state — nothing is lost when swapping). `:data:cloudstream` added to `:feature:anime-details:impl`. |

---

## 3. Review trail

- Self-review: the repo's nesting-aware tokenizer balance ×22 touched files —
  ALL BALANCED.
- Independent review agent (round 2): verified every referenced symbol against
  the real APIs; found **3 compile blockers** (the missing `rememberScrollState`
  import; `Alignment.TopCenter` through `ColumnScope.align` in the new screen;
  the `CsVideoLink` classpath trap) and **1 behavior defect** (cancel marked
  killed targets finished/healthy) — all fixed pre-push, plus 3 nits applied
  (dead state, unused imports, the `uninstallError` overwrite race).

---

## 4. Device-round checklist (v1.1.39)

1. **Aniyomi uninstall** — trash icon → the SYSTEM prompt (app name on top,
   "Do you want to uninstall this app?", Cancel / OK) → OK removes the
   extension and the row disappears.
2. **CloudStream uninstall** — the confirm stays; a failing uninstall now
   toasts the reason instead of doing nothing.
3. **Link Source sheet** (details page → the source pill) — the two wheels
   snap like an alarm clock, icons render, tap-to-center works, the search
   bar's button sits on the right, results show with a Change chip.
4. **Search screens** — the right-edge circular search button submits.
5. **Extensions settings** — the pills, the dedicated search view, the capped
   language menu, the asc/desc flip, the NSFW pill.
6. **Repositories** — long-press copies the URL (toast confirms).
7. **Extension testing** — the banner opens the screen; "Run all tests" walks
   every source sequentially with live rows; batch + Stop behave; CS targets
   resolve through the CS path.
