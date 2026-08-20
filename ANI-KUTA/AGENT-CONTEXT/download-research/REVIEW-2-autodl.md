# REVIEW-2 — Auto-Download Engine + Settings + Proxy-Churn Bug Fix

> **Task ID:** DL-REVIEW-2
> **Reviewer:** senior-review-agent
> **Scope:** Review Round 2 of 5 — focus on the auto-download priority engine + settings + the download-fails-when-playing bug fix.
> **Files reviewed:**
> - `14-auto-download-engine.md` (1034 lines) — the 5-step priority pipeline + dimensionPriority + globalFallback design.
> - `07-settings-preferences.md` (587 lines) — the 17 settings + reactive PreferenceStore.
> - `10-player-integration.md` (494 lines) — the proxy-churn bug fix (4 layers).
> - `15-ui-and-bug-analysis.md` Part B (lines 734-989) — the bug root cause analysis.
> - `05-downloaders.md` §11.3 (the HttpDownloader implementation with the re-resolve catch block).
> - `16-quality-of-life.md` §1 (the RetryPolicy + RETRYING state — interacts with the re-resolve).
>
> **Cross-referenced against:**
> - OLD `DownloadOrchestrator.kt` (400 lines) — verified the OLD `selectBestVideo` algorithm + the `serverFallback` dead-code bug.
> - OLD `DownloadPreferences.kt` (205 lines) — verified all 15 settings + the `FallbackStrategy` enum.
> - OLD `Preference.kt` (62 lines) — verified the OLD `Preference<T>` interface (7 methods, NOT 3).
> - OLD `DownloadSettingsScreen.kt` (527 lines) — verified the 8 private composables + the server merge logic.
> - OLD `DragReorderableList.kt` (192 lines) — verified the `List<String>`-only signature.
> - NEW `core/preferences/PreferenceStore.kt` (49 lines) — verified it's currently non-reactive.
> - NEW `core/video-resolver/ResolverTypes.kt` (73 lines) — verified `directUrl` is NOT yet on `ResolverVideo`; `videoTitle` IS (with a comment about re-resolution matching).
> - NEW `core/video-resolver/ResolvedVideosRegistry.kt` (80 lines) — verified an in-memory registry already exists (for screen-to-screen passing, not proxy-churn prevention).

---

## Work Log

- Read worklog.md (prior 6 DL tasks: DL-RESEARCH, DL-WEBPAGE, DL-AUTODL-RESEARCH, DL-UI-BUG-RESEARCH, DL-PLAN-REWRITE, DL-REVIEW-1) for context.
- Read in full: `14-auto-download-engine.md`, `07-settings-preferences.md`, `10-player-integration.md`, `15-ui-and-bug-analysis.md` Part B, `05-downloaders.md` §11.3, `16-quality-of-life.md` §1.
- Verified every claim against source files (file:line references inline below).
- Traced the 5-step pipeline with the doc's worked example + 2 additional edge cases.
- Traced the proxy-churn fix end-to-end + identified a CRITICAL unbounded-recursion bug in the `HttpDownloader.downloadNormal` catch block.
- Did NOT modify any plan docs (only READ + wrote this review). Did NOT modify source files. Did NOT build the project.

---

## Per-checklist findings

### 1. The 5-step pipeline (flatten → rank → applyFallbacks → pick → globalFallback) — **PASS with CONCERNS**

**What's correct:**

- **Step 1 (Flatten)** — converts `List<ResolverServer>` to `List<Candidate>` with per-dimension `*Rank` fields. Pure function. Correct.
- **Step 2 (Rank)** — sorts by the rank tuple in dimension-priority order. Lexicographic ordering. Correct.
- **Step 3 (applyFallbacks)** — generalizes the OLD project's hardcoded Steps 1+2 (which only checked AUDIO + QUALITY in a fixed order) to ALL 3 dimensions in the user-defined order. **This DOES fix the `serverFallback` dead-code bug** (see checklist #4).
- **Step 4 (Pick)** — return the first sorted candidate. Trivial. Correct.
- The pipeline is pure functions over data classes → trivially unit-testable. ✓
- The `Candidate` data class is well-designed — carries the video + context + ranks + `is*Preferred` flags.

**Worked example trace (the user's question: "if dub is priority but preferred quality+server unavailable on dub, what downloads?"):**

Per the doc's §6.3, with `dimensionPriority = [AUDIO, QUALITY, SERVER]` + `audioPrefs = ["DUB", "SUB"]` + `qualityPrefs = ["1080p", "720p"]` + `serverPrefs = ["Streamtape", "Vidstreaming"]` + all fallbacks = TRY_NEXT:

| Candidate | server | audio | quality | serverRank | audioRank | qualityRank |
|---|---|---|---|---|---|---|
| A | Streamtape | SUB | 1080p | 0 | 1 | 0 |
| B | Streamtape | SUB | 720p  | 0 | 1 | 1 |
| C | Vidstreaming | SUB | 720p  | 1 | 1 | 1 |
| D | Vidstreaming | DUB | 1080p | 1 | 0 | 0 |

Sort by `(audioRank, qualityRank, serverRank)`:
1. **D** — `(0, 0, 1)` ← best (top audio DUB, top quality 1080p, #2 server Vidstreaming)
2. A — `(1, 0, 0)` (SUB, 1080p, Streamtape)
3. B — `(1, 1, 0)` (SUB, 720p, Streamtape)
4. C — `(1, 1, 1)` (SUB, 720p, Vidstreaming)

Step 3 — per-dim fallback checks (all `hasTopPref = true`). Step 4 — pick first: **D = Vidstreaming/DUB/1080p.** ✓ Audio wins over server.

**Edge case I traced (modified scenario — DUB available only on Vidstreaming/720p, NOT 1080p; preferred quality 1080p only on Streamtape/SUB):**

| Candidate | server | audio | quality | serverRank | audioRank | qualityRank |
|---|---|---|---|---|---|---|
| A | Streamtape | SUB | 1080p | 0 | 1 | 0 |
| B | Streamtape | SUB | 720p  | 0 | 1 | 1 |
| C | Vidstreaming | SUB | 720p  | 1 | 1 | 1 |
| D | Vidstreaming | DUB | 720p | 1 | 0 | 1 |

Sort by `(audioRank, qualityRank, serverRank)` with `[AUDIO, QUALITY, SERVER]`:
1. **D** — `(0, 1, 1)` ← best (DUB, 720p, Vidstreaming) — preferred quality NOT on DUB
2. A — `(1, 0, 0)` (SUB, 1080p, Streamtape)
3. B — `(1, 1, 0)` (SUB, 720p, Streamtape)
4. C — `(1, 1, 1)` (SUB, 720p, Vidstreaming)

Step 3 — all `hasTopPref = true` (DUB ✓, 1080p ✓ via A, Streamtape ✓ via A/B). Step 4 — pick **D = Vidstreaming/DUB/720p.**

→ Audio wins (DUB on Vidstreaming/720p) even though preferred quality (1080p) + preferred server (Streamtape) are NOT available on DUB. **This matches the user's intent** ("audio is most important"). ✓

**CONCERNS:**

- **Step 5 (globalFallback) is poorly designed.** See **CRITICAL C2** below. The `globalFallback = ASK/DO_NOT_DOWNLOAD` only fires when `sortedCandidates.isEmpty()` — at which point showing the picker is useless (no servers to pick from).
- **Step 5's "unreachable" claim is wrong.** The doc says "Step 5 is the 'the resolver returned servers but none of them had ANY video' case — already covered by `ResolverResult.NoSources` upstream." But `ResolverResult.NoSources` is when `result.servers.isEmpty()`. If `result.servers = [server1]` but `server1.audioVersions = []` (or all audioVersions have empty videos), the resolver returns `Success` but `flatten` produces zero candidates → `sortedCandidates.isEmpty()` → Step 5 IS reachable. **MINOR M1.**
- **`applyFallbacks`'s TOP-preference-only check is inherited from the OLD project** (lines 224-246 of `DownloadOrchestrator.kt` — only checks `audioPrefs.firstOrNull()`, not all prefs). With `audioPrefs = ["DUB", "SUB"]` + `audioFallback = DO_NOT_DOWNLOAD` + DUB unavailable → engine fails even though SUB (the #2 pref) is available. This is intentional (DO_NOT_DOWNLOAD means "don't try next"), but should be documented more explicitly because users may expect "fail only if NO preferred value is available". **MINOR M7.**

### 2. The `dimensionPriority` pref — **PASS with CONCERN**

**What's correct:**

- `List<PreferenceDimension>` with `enum class PreferenceDimension { AUDIO, QUALITY, SERVER }` is the right model — three discrete dimensions, all reorderable.
- The default `[AUDIO, QUALITY, SERVER]` is a reasonable starting point for most users (audio is usually the most important for sub/dub preferences).
- The UI CAN reorder all 3 dimensions via the new "Priority order" section's `DragReorderableList` (§6.5 + §8.5 of `07-settings-preferences.md`). The `DragReorderableList` component takes `List<String>`, so the dimensions are mapped to display strings (`["Audio", "Quality", "Server"]`) and back to enum values on persist. Verified the existing `DragReorderableList.kt:70-73` signature — yes, `List<String>` only. The mapping is a minor friction but works.
- The JSON serialization via `ListSerializer(PreferenceDimension.serializer())` is standard kotlinx.serialization. ✓

**CONCERN:**

- **The default `[AUDIO, QUALITY, SERVER]` does NOT preserve old behaviour.** The doc's §6.6 summary table claims "Default `[AUDIO, QUALITY, SERVER]` (preserves old semantics as the starting point)." This is **FALSE** per the doc's OWN worked example trace in §3.5 vs §6.3:
  - OLD engine (§3.5): picks `Streamtape/SUB/1080p` (server outermost loop wins).
  - NEW engine with `[AUDIO, QUALITY, SERVER]` (§6.3): picks `Vidstreaming/DUB/1080p` (audio wins).
  - These are DIFFERENT results for the same inputs.
- The OLD project's effective priority was INCONSISTENT (check-layer: AUDIO > QUALITY; iteration-layer: SERVER > AUDIO > QUALITY). Neither matches `[AUDIO, QUALITY, SERVER]`. The new default is a DELIBERATE behavioural change, NOT a preservation of old semantics. **IMPORTANT I1.**

### 3. The `globalFallback` strategy — **FAIL (design flaw)**

The three options `BEST_EFFORT / ASK / DO_NOT_DOWNLOAD`:

- **`BEST_EFFORT`** (default): per the doc's §6.2.5, this fires when `sortedCandidates.isEmpty()`. But the rank tuple in Step 2 already considers unlisted values (with `Int.MAX_VALUE` rank) — so the engine ALWAYS picks the best-ranked candidate, even if all dimensions are non-preferred. `BEST_EFFORT` is therefore the natural behavior of the rank tuple, NOT a distinct strategy. It's effectively a no-op enum value. **MINOR M2.**
- **`ASK`**: per the doc's §6.2.5, this fires when `sortedCandidates.isEmpty()` → `return ShowPicker(...)`. But if there are zero candidates, there are also zero servers to show in the picker — the picker would display an empty list. This is useless UX. The proper semantic for `globalFallback = ASK` should be: "after Step 4, if the picked candidate has ANY non-preferred value (i.e. it's a 'best-effort' pick, not a perfect match), show the picker." But the doc's pseudocode doesn't implement this. **CRITICAL C2.**
- **`DO_NOT_DOWNLOAD`**: same issue — only fires when `sortedCandidates.isEmpty()`. The proper semantic should be "if the picked candidate is not a perfect match, fail." **CRITICAL C2.**

**Who asks?** The doc's pseudocode says `ASK → return ShowPicker(...)` — so the engine returns an `EnqueueResult.ShowPicker` (or equivalent) and the host UI shows the `DownloadVideoPickerSheet`. This is consistent with the OLD project's `EnqueueResult.ShowPicker` pattern (verified in `DownloadOrchestrator.kt:88-93` + `:118`). The callback mechanism is sound — but the TRIGGER condition is broken.

### 4. The `serverFallback` dead-code bug — **PASS (fixed)**

**Verified the bug exists in the OLD project:**

- `DownloadPreferences.kt:137-138` — `serverFallback()` is declared.
- `DownloadSettingsScreen.kt:97-98` — read reactively via `preferences.serverFallback().changes().collectAsState(...)`.
- `DownloadSettingsScreen.kt:305-306` — written by `FallbackToggle`.
- `DownloadOrchestrator.kt:211-311` (`selectBestVideo`) — only reads `audioFallback` (line 215) + `qualityFallback` (line 216). **`serverFallback` is NEVER READ.** ✓ Confirmed via Grep across the entire OLD project (4 references — all in `DownloadPreferences.kt` + `DownloadSettingsScreen.kt`; zero in `DownloadOrchestrator.kt`).

**Verified the NEW design fixes it:**

The NEW Step 3 (`applyFallbacks`) iterates ALL 3 dimensions IN the user-defined dimension-priority order, consulting the per-dim fallback for each:

```
for (dim in dimensionPriority):
    topPref = prefsFor(dim).firstOrNull()
    if (topPref == null) continue
    hasTopPref = candidates.any { it.matchesTopPref(dim) }
    if (!hasTopPref):
        when (fallbackFor(dim)):  // ← serverFallback IS consulted here when dim == SERVER
            ASK → return ShowPicker(...)
            DO_NOT_DOWNLOAD → return Error(...)
            TRY_NEXT → continue
```

So `serverFallback` is now consulted in the SERVER iteration of the loop. ✓ The dead-code bug is fixed.

### 5. The settings UI replication — **PASS**

**Verified against the OLD `DownloadSettingsScreen.kt` (527 lines):**

- The 8 private composables documented in §5.3 are all present at the documented line numbers:
  - `SectionContainer` — line 320 (doc says 320-336 ✓)
  - `CollapsibleSection` — line 339 (doc says 339-372 ✓)
  - `CollapsibleExtensionSection` — line 375 (doc says 375-431 ✓)
  - `SettingsRow` — line 434 (doc says 434-444 ✓)
  - `ToggleRow` — line 447 (doc says 447-459 ✓)
  - `SliderRow` — line 463 (doc says 463-477 ✓)
  - `FallbackToggle` — line 484 (doc says 484-501 ✓)
  - `SegmentedRowLocal` — line 504 (doc says 504-527 ✓)
- The server merge logic at `DownloadSettingsScreen.kt:384-386`:
  ```kotlin
  val merged = (userOrder.filter { it in discovered } + discovered.filter { it !in userOrder }).distinct()
  ```
  matches the doc's §2.4 verbatim. ✓
- The `DragReorderableList` component (192 lines) signature `DragReorderableList(items: List<String>, onReorder: (List<String>) -> Unit, modifier: Modifier)` matches the doc's §2.1. ✓
- The new "Priority order" section is added BETWEEN `AUTO-DOWNLOAD` and `PREFERRED QUALITY` (per §6.5 ASCII mockup). This is additive — doesn't disrupt the existing 3 sections. ✓

**CONCERN (MINOR M3):** The doc's §8.6 says "Replicate EXACTLY (no deviations)" but ALSO "Fix the OLD project's bugs while replicating" (concurrentDownloads reactivity, advancedMaxRetries default=10, serverFallback dead-code). These are CHANGES, not exact replication. The intent is clear, but the language is contradictory — should say "Replicate the LAYOUT + visual design exactly; fix the BEHAVIOURAL bugs while replicating."

### 6. The reactive `PreferenceStore` — **PASS with CONCERNS**

**What's correct:**

- The `Preference<T>` interface with `get()`/`set()`/`changes(): Flow<T>` is the right abstraction for reactive prefs.
- The `OnSharedPreferenceChangeListener` + `MutableSharedFlow<String>` design is lightweight + works. Verified `SharedPreferences.OnSharedPreferenceChangeListener` is the standard Android pattern.
- The convenience builders (`stringPref`/`booleanPref`/`intPref`/`enumPref`/`jsonListPref`/`jsonMapPref`) cover all the needed types. ✓
- The `PreferenceImpl.changes()` implementation: `store.changes.filter { it == key }.map { get() }.distinctUntilChanged().onStart { emit(get()) }` is correct — filters by key, maps to current value, dedupes, emits initial value.
- It IS needed for the drag-reorder UI — `DragReorderableList.onReorder` → `prefs.X().set(newOrder)` → the reactive Flow emits → other parts of the UI re-render. Without reactivity, the list wouldn't update visually. ✓
- It WILL work with the new project's existing `core/preferences` — verified `PreferenceStore.kt` (49 lines) is a simple class with `getString`/`putString`/etc. The proposed design EXTENDS it (adds the `Preference<T>` interface + the `MutableSharedFlow` + the builders) without breaking existing direct-`getString` callers (like `AutoLinkPreferences.kt`). ✓ Backward compatible.

**CONCERNS:**

- **The proposed `Preference<T>` interface is a REGRESSION from the OLD project's interface.** Verified `app.confused.anikuta.core.preferences.Preference` (OLD) has 7 methods: `key()`, `get()`, `set()`, `isSet()`, `delete()`, `defaultValue()`, `changes()`, `stateIn(scope)`. The NEW proposed interface has only 3: `get()`, `set()`, `changes()`. The OLD project's `stateIn(scope)` returns a `StateFlow<T>` — useful for `collectAsStateWithLifecycle` patterns. The OLD project's `key()` is useful for debugging + bulk operations. The OLD project's `isSet()` is useful for "has the user configured this?" detection. Dropping these methods limits future flexibility. **IMPORTANT I4.**
- **`onStart { emit(get()) }` is redundant with `collectAsState(initial = prefs.x().get())`.** The doc's §1.6 (in `14-auto-download-engine.md`) shows the OLD pattern: `val serverFallback by preferences.serverFallback().changes().collectAsState(initial = preferences.serverFallback().get())`. The `initial` provides the first value synchronously; then `onStart` emits the same value again. Harmless but wasteful (one duplicate emission per setting per collection). **MINOR M4.**
- **`MutableSharedFlow(extraBufferCapacity = 16)` can drop emissions if consumers are slow.** With `tryEmit` (non-suspending), if the buffer fills, emissions are silently dropped. For prefs UI, this is usually fine (the next emission arrives soon), but a burst of pref changes (e.g. drag-reorder firing rapidly) could miss updates. **MINOR M5.**

### 7. The proxy-churn bug fix (4 layers) — **FAIL (CRITICAL implementation bug)**

**Fix 1 (PRIMARY) — `directUrl` on `ResolverVideo`:**

- The architectural idea is correct: bypass the proxy entirely by using the underlying CDN URL. No proxy dependency → no churn. ✓
- Verified the NEW project's `ResolverTypes.kt:56-63` — `ResolverVideo` does NOT yet have a `directUrl` field. The doc correctly identifies this as a new addition.
- **CONCERN:** The doc says "The resolver strategy extracts the direct URL by calling a new `Video.directVideoUrl` extension hook (similar to how the existing `videoUrl` is exposed). Extensions that proxy can override this to return the underlying CDN URL." This means **Fix 1 depends on extension cooperation** — existing extensions (AniKotoS et al.) don't expose `directVideoUrl`, so Fix 1 is a NO-OP for them. The doc should clarify that Fix 2 is the actual fix that works for existing extensions. **IMPORTANT I2.**
- Note: the NEW project's `ResolverVideo` already has a `videoTitle: String` field with KDoc "A stable identifier used to match the currently-playing video across re-resolutions. Proxied URLs change between resolutions, so we match by title instead." This is a DIFFERENT approach to the proxy-churn problem (match by title for player-side continuity). The doc doesn't reference this existing field — should be cross-referenced.

**Fix 2 (SECONDARY) — Re-resolve-on-IOException:**

- The architectural idea is correct: catch IOException, re-resolve via `ReResolver`, retry with fresh URL. ✓
- **CRITICAL BUG in the implementation:** `05-downloaders.md` §11.3 shows:
  ```kotlin
  catch (e: IOException) {
      if (url.startsWith("http://localhost") && resolveContext != null && reResolver != null) {
          val fresh = reResolver.reResolve(resolveContext)
          if (fresh != null) {
              store.updateResolveContext(taskId, fresh.url, resolveContext)
              return downloadNormal(fresh.url, fresh.headers, tempFile, taskId, resolveContext, onProgress)
          }
      }
      throw DownloadException("Video download failed: ${e.message ?: e.javaClass.simpleName}", e)
  }
  ```
  The `return downloadNormal(fresh.url, ...)` is a RECURSIVE call. If the recursive call ALSO throws `IOException` (because the fresh proxy URL was also killed — e.g. the user keeps playing new episodes, each killing the previous proxy), the recursive call's catch block fires AGAIN — calling `reResolver.reResolve` AGAIN — getting ANOTHER fresh URL — and recursing AGAIN. **This is UNBOUNDED RECURSION** — it only stops when `reResolver.reResolve` returns null (re-resolve fails) or when the stack overflows.
- The doc's §14.1 says "Cap re-resolve attempts at 2 (one initial + one re-resolve) to avoid infinite loops" — but the implementation in `05-downloaders.md` §11.3 has NO cap. The cap is not enforced anywhere.
- **CRITICAL C1.** The implementation needs either:
  - A counter parameter (`reResolveAttempts: Int = 0`) that's incremented + checked before recursing.
  - Or a `while` loop with a counter (instead of recursion).
- **Additional CONCERN:** The doc's §14.1 says "The re-resolve uses the SAME `AutoDownloadEngine` (the new 5-step priority pipeline from `14-auto-download-engine.md` §6.2), but pins the SAME (server, audio, quality) combination via the `resolveContext`." But §14.3's `ReResolver.reResolve` implementation does NOT use the `AutoDownloadEngine` — it does a direct lookup by pinned (server, audio, quality):
  ```kotlin
  val server = result.servers.firstOrNull { it.name == context.serverName } ?: return null
  val audio = server.audioVersions.firstOrNull { it.label == context.audioLabel } ?: return null
  val video = audio.videos.firstOrNull { it.quality == context.quality } ?: return null
  return FreshVideo(url = video.directUrl ?: video.url, headers = video.videoHeaders)
  ```
  §14.3 is CORRECT (simpler + correct — we don't want the engine to pick a DIFFERENT (server, audio, quality) on re-resolve; we want the SAME one with a fresh URL). §14.1's description is misleading. **IMPORTANT I3.**

**Fix 3 (TERTIARY) — `ProxyLeaseCoordinator`:**

- The doc says "Implement Fix 1 + Fix 2 in Phase D.2 (required). Defer Fix 3 to a later phase." So it's deferred. ✓ Not a blocker for the initial implementation.
- **CONCERN (design incomplete):** The `ProxyKey` is defined as `(sourceId, serverName)` — per-source-per-server. But the proxy is created per-`getHosterList(episode)` call — i.e. per-source-per-EPISODE (or even per-source-per-call). A proxy created for episode A's `getHosterList` serves episode A's video URLs; it does NOT serve episode B's URLs. So a lease keyed by `(sourceId, serverName)` would reuse a proxy that can't serve the new episode's URLs. The design needs to either:
  - Key the lease by `(sourceId, episodeUrl)` — but then the lease doesn't prevent the bug (each episode gets its own proxy + lease).
  - Key the lease by `sourceId` only — but then the coordinator must re-call `getHosterList` when the episode changes, which kills the old proxy anyway.
  - Cache the resolved videos per `(sourceId, episodeUrl)` + reuse them while a lease is held — but this requires the resolver to be episode-aware caching, not just lease-tracking.
- The doc's design doesn't address this. **IMPORTANT I5.** Since Fix 3 is deferred, this isn't a blocker — but the design needs work before implementation.

**Fix 4 (QUATERNARY) — Foreground service:**

- The doc correctly acknowledges "Independent of the proxy-churn bug... a SEPARATE failure mode." The user's bug happens while the user is IN the app (playing another episode) — the foreground service doesn't address this. ✓ Correctly scoped.
- The foreground service IS needed for Android 14+ background download durability (a separate concern), so adding it in the same pass is reasonable. ✓

### 8. The bug root cause — **PASS (correctly identified + addressed)**

**Verified the root cause analysis:**

- The doc correctly identifies the root cause as "extension local-proxy-server churn" — extensions like AniKotoS create/rotate a `NanoHTTPD`-based `HttpServer` inside `getHosterList` (random port via `NanoHTTPD(0)`). The OLD download captures the proxy URL at enqueue time with no re-resolve path. Any subsequent `getHosterList` call (on the SAME `AnimeSource` instance) kills the in-flight download's proxy → `IOException` → ERROR.
- The end-to-end trace (§B.4) with concrete port numbers (39369 → 39073) from `lessons-learned.md:89` is accurate.
- The 8 ruled-out non-causes (shared scope, shared OkHttp, shared temp cache, connectivityCheck, auto-cancel, observeJob, ResolverService state, auto-clear) are all verified with file:line references. ✓

**Does the fix address the root cause?**

- **Fix 1 (`directUrl`):** YES — architecturally avoids the proxy dependency. No proxy → no churn. This is the ROOT CAUSE fix (for extensions that expose `directUrl`).
- **Fix 2 (re-resolve-on-IOException):** PARTIALLY — it doesn't PREVENT the proxy death, but it RECOVERS from it. This is symptom-mitigation, not root-cause-fixing. But for extensions that don't expose `directUrl`, this is the best available fix. ✓
- **Fix 3 (ProxyLeaseCoordinator):** YES — prevents the proxy death by suppressing duplicate `getHosterList` calls. Root cause addressed. (But design incomplete — see I5.)
- **Fix 4 (foreground service):** NO — addresses a separate failure mode (acknowledged in doc).

The layered design is sound: Fix 1 is the architectural fix (root cause), Fix 2 is the safety net (symptom recovery), Fix 3 is the strong fix (root cause for non-cooperating extensions, deferred), Fix 4 is unrelated (background durability). ✓

**BUT — the CRITICAL implementation bug (C1) in Fix 2 means the safety net doesn't actually work as documented.** The unbounded recursion could stack-overflow the download coroutine, which is WORSE than the original bug (which at least failed cleanly with ERROR status). C1 must be fixed before Fix 2 is shipped.

### 9. Customizability — **PASS**

The user said "highly customizable so that in the future we can change this logic easily". The pipeline IS extensible:

- **Add a 4th dimension** (e.g. `SUBTITLES`): add `PreferenceDimension.SUBTITLES` enum value + a `subtitlesPreferences: List<String>` pref + `subtitlesRank` on `Candidate`. The pipeline doesn't change — `flatten` adds the rank, `rank` includes it in the tuple, `applyFallbacks` checks it. ✓ Trivially extensible.
- **Weighted scoring instead of lexicographic:** swap `rank(candidates, dimensionPriority)` for `rank(candidates, weights: Map<PreferenceDimension, Double>)`. The pipeline shape doesn't change — only the `rank` function's internals. ✓
- **Per-source dimension priority:** lift `dimensionPriority` from a single global pref to `Map<sourceId, List<PreferenceDimension>>` (same shape as `serverPreferences`). The engine doesn't care where the priority comes from. ✓
- **Conflict-surfacing UI:** `applyFallbacks` could return a `List<Conflict>` instead of a single `FallbackDecision`. The engine already has the data (the `Candidate` list + the ranks). ✓
- **"Strict mode"** (must match ALL preferred dimensions, no TRY_NEXT): add a per-dimension "strict" flag (or a `STRICT` enum value alongside `TRY_NEXT`/`ASK`/`DO_NOT_DOWNLOAD`). ✓

The pipeline's purity makes it trivially unit-testable — `flatten` + `rank` + `applyFallbacks` are pure functions over data classes. The OLD `selectBestVideo` is a 100-line method with interleaved reads + branching — much harder to test. ✓

**MINOR CONCERN:** The `applyFallbacks` return type (`FallbackDecision`) is not fully specified in the doc — it's mentioned in §6.4 but not defined. The implementation team would need to design this. Not a blocker, but worth noting.

---

## Worked example — full trace

**User's exact question:** "if dub is priority but preferred quality+server unavailable on dub, what downloads?"

**Interpretation:** `dimensionPriority = [AUDIO, QUALITY, SERVER]` (audio is the top priority dimension). The preferred quality + preferred server are NOT available on any DUB candidate.

**User settings:**
- `dimensionPriority = [AUDIO, QUALITY, SERVER]`
- `audioPrefs = ["DUB", "SUB"]`
- `qualityPrefs = ["1080p", "720p"]`
- `serverPrefs = ["Streamtape", "Vidstreaming"]`
- `audioFallback = TRY_NEXT`, `qualityFallback = TRY_NEXT`, `serverFallback = TRY_NEXT`
- `globalFallback = BEST_EFFORT`

**Resolved video tree (modified from §6.3 so DUB doesn't have the preferred quality):**
```
Streamtape (user's #1 server):
  SUB:  1080p, 720p       (no DUB)
Vidstreaming (user's #2 server):
  SUB:  720p
  DUB:  720p              ← preferred quality 1080p NOT available on DUB
```

**Step 1 — Flatten:**

| Candidate | server | audio | quality | serverRank | audioRank | qualityRank |
|---|---|---|---|---|---|---|
| A | Streamtape | SUB | 1080p | 0 | 1 | 0 |
| B | Streamtape | SUB | 720p  | 0 | 1 | 1 |
| C | Vidstreaming | SUB | 720p  | 1 | 1 | 1 |
| D | Vidstreaming | DUB | 720p | 1 | 0 | 1 |

(Ranks: Streamtape=0, Vidstreaming=1 per serverPrefs; SUB=1, DUB=0 per audioPrefs; 1080p=0, 720p=1 per qualityPrefs. Unlisted = `Int.MAX_VALUE` — none here.)

**Step 2 — Sort by `(audioRank, qualityRank, serverRank)` with `[AUDIO, QUALITY, SERVER]`:**

1. **D** — `(0, 1, 1)` ← best (DUB, 720p, Vidstreaming)
2. A — `(1, 0, 0)` (SUB, 1080p, Streamtape)
3. B — `(1, 1, 0)` (SUB, 720p, Streamtape)
4. C — `(1, 1, 1)` (SUB, 720p, Vidstreaming)

**Step 3 — `applyFallbacks` (in `[AUDIO, QUALITY, SERVER]` order):**

- AUDIO: `topPref = "DUB"`. `hasTopPref = true` (candidate D). ✓ continue.
- QUALITY: `topPref = "1080p"`. `hasTopPref = true` (candidate A). ✓ continue.
- SERVER: `topPref = "Streamtape"`. `hasTopPref = true` (candidates A, B). ✓ continue.

All per-dim fallbacks pass. → `FallbackDecision.Continue`.

**Step 4 — Pick first sorted candidate:**

→ **Candidate D = Vidstreaming / DUB / 720p.**

**Step 5 — Not reached** (`sortedCandidates` is non-empty).

**Result:** `Selected(video=D, serverName="Vidstreaming", audioLabel="DUB")` → the orchestrator builds a `DownloadRequest` with `videoUrl = D.video.url` (or `D.video.directUrl` if Fix 1 is implemented) + enqueues.

**Interpretation:** The engine picks **DUB on Vidstreaming at 720p** — accepting the lower quality (720p instead of preferred 1080p) + the non-preferred server (Vidstreaming instead of preferred Streamtape) — because AUDIO is the top-priority dimension and DUB is only available on Vidstreaming/720p. **This matches the user's intent** ("audio is the most important").

**Comparison to OLD engine:** The OLD engine (per §3.5 trace) would pick `Streamtape/SUB/1080p` — because the server is the outermost loop + the audio+quality hard filters find a match on the #1 server (Streamtape) before reaching the #2 server (Vidstreaming). The NEW engine with `[AUDIO, QUALITY, SERVER]` correctly prioritizes audio. ✓

**If the user wanted the OLD behaviour:** they would set `dimensionPriority = [SERVER, QUALITY, AUDIO]` (server first). Sort by `(serverRank, qualityRank, audioRank)`:
1. A — `(0, 0, 1)` ← best (Streamtape, 1080p, SUB)
2. B — `(0, 1, 1)` (Streamtape, 720p, SUB)
3. D — `(1, 1, 0)` (Vidstreaming, 720p, DUB)
4. C — `(1, 1, 1)` (Vidstreaming, 720p, SUB)

→ Pick A = `Streamtape/SUB/1080p`. Matches OLD behaviour. ✓ The new design is CONFIGURABLE — the user can choose either behaviour.

---

## Issues

### CRITICAL

**C1 — `HttpDownloader.downloadNormal` re-resolve catch block has UNBOUNDED RECURSION.**

Location: `05-downloaders.md` §11.3 (the `catch (e: IOException)` block).

The catch block recursively calls `downloadNormal(fresh.url, ...)` with no cap on re-resolve attempts. If the recursive call ALSO throws `IOException` (because the fresh proxy URL was also killed — e.g. the user keeps playing new episodes), the recursive call's catch block fires AGAIN, calling `reResolver.reResolve` AGAIN, getting ANOTHER fresh URL, and recursing AGAIN. This is unbounded recursion — it only stops when `reResolver.reResolve` returns null (re-resolve fails) or when the stack overflows.

The doc's `10-player-integration.md` §14.1 says "Cap re-resolve attempts at 2 (one initial + one re-resolve) to avoid infinite loops" — but the implementation in `05-downloaders.md` §11.3 does NOT enforce this cap. The recursive call has no counter parameter.

**Fix:** Add a `reResolveAttempts: Int = 0` parameter to `downloadNormal`. In the catch block, only recurse if `reResolveAttempts < MAX_RE_RESOLVE_ATTEMPTS` (e.g. 1):
```kotlin
catch (e: IOException) {
    if (url.startsWith("http://localhost") && resolveContext != null && reResolver != null
        && reResolveAttempts < MAX_RE_RESOLVE_ATTEMPTS) {
        val fresh = reResolver.reResolve(resolveContext)
        if (fresh != null) {
            store.updateResolveContext(taskId, fresh.url, resolveContext)
            return downloadNormal(fresh.url, fresh.headers, tempFile, taskId,
                resolveContext, onProgress, reResolveAttempts + 1)
        }
    }
    throw DownloadException("Video download failed: ${e.message ?: e.javaClass.simpleName}", e)
}
```

This bug would manifest as a `StackOverflowError` (or silent coroutine cancellation) when the proxy keeps dying — WORSE than the original bug (which at least failed cleanly with ERROR status). **Must be fixed before Fix 2 is shipped.**

**C2 — `globalFallback = ASK / DO_NOT_DOWNLOAD` only fires when `sortedCandidates.isEmpty()` — useless UX.**

Location: `14-auto-download-engine.md` §6.2.5 (Step 5).

The doc's pseudocode:
```
if (sortedCandidates.isEmpty()):
    when (globalFallback):
        BEST_EFFORT → ... (unreachable if resolver returned any servers)
        ASK → return ShowPicker(...)
        DO_NOT_DOWNLOAD → return Error("No video sources available at all")
```

`ASK` only fires when there are ZERO candidates — at which point the picker would display an empty server list (useless). `DO_NOT_DOWNLOAD` only fires in the same zero-candidate case — the error message "No video sources available at all" doesn't reflect the user's intent ("don't download if my preferences aren't met").

The proper semantic for `globalFallback = ASK` should be: **"after Step 4, if the picked candidate has ANY non-preferred value (i.e. it's a 'best-effort' pick, not a perfect match), show the picker."** Similarly for `DO_NOT_DOWNLOAD`: "if the picked candidate is not a perfect match, fail."

**Fix:** Redefine Step 5 to fire based on the picked candidate's match quality, not on `sortedCandidates.isEmpty()`:
```kotlin
val best = sortedCandidates.firstOrNull() ?: return globalFallbackNoCandidates(globalFallback)
val isPerfectMatch = best.isServerPreferred && best.isAudioPreferred && best.isQualityPreferred
if (!isPerfectMatch) {
    return when (globalFallback) {
        BEST_EFFORT -> Selected(best.video, best.serverName, best.audioLabel)  // accept the best-effort pick
        ASK -> ShowPicker(...)
        DO_NOT_DOWNLOAD -> Error("No perfect match for your preferences...")
    }
}
return Selected(best.video, best.serverName, best.audioLabel)
```

This makes `globalFallback` meaningful: `BEST_EFFORT` accepts any pick, `ASK` surfaces the picker for non-perfect matches, `DO_NOT_DOWNLOAD` fails for non-perfect matches. The current design makes `ASK`/`DO_NOT_DOWNLOAD` functionally identical to `BEST_EFFORT` for the common case (zero candidates).

### IMPORTANT

**I1 — `dimensionPriority` default `[AUDIO, QUALITY, SERVER]` does NOT preserve old behaviour.**

Location: `14-auto-download-engine.md` §6.6 (summary table) + §6.1.1 (DEFAULT_DIMENSION_PRIORITY).

The doc claims the default "preserves old semantics as the starting point." This is FALSE per the doc's OWN trace:
- OLD engine (§3.5): picks `Streamtape/SUB/1080p`.
- NEW engine with `[AUDIO, QUALITY, SERVER]` (§6.3): picks `Vidstreaming/DUB/1080p`.
- These are DIFFERENT results for the same inputs.

The OLD project's effective priority was INCONSISTENT (check-layer: AUDIO > QUALITY; iteration-layer: SERVER > AUDIO > QUALITY). The new default is a DELIBERATE behavioural change — it chooses AUDIO as the top priority, which the OLD engine did only at the check-layer (not the iteration-layer).

**Fix:** Either:
- (a) Acknowledge this is a deliberate change in the doc (e.g. "Default `[AUDIO, QUALITY, SERVER]` — a deliberate choice reflecting typical user intent; the OLD project's behaviour was inconsistent + is not preserved."). OR
- (b) Use `[SERVER, AUDIO, QUALITY]` as the default to match the OLD iteration-layer behaviour (server outermost).

Option (a) is preferred — the OLD behaviour was a bug (the user's complaint), so preserving it would defeat the purpose. But the doc should be honest about the change.

**I2 — Fix 1 (`directUrl`) depends on extension cooperation — existing extensions don't expose it.**

Location: `10-player-integration.md` §14.1 (Fix 1) + §14.5 (end-to-end fixed trace).

The doc says "The resolver strategy extracts the direct URL by calling a new `Video.directVideoUrl` extension hook... Extensions that proxy can override this to return the underlying CDN URL." This means Fix 1 only works for extensions that have been UPDATED to expose `directVideoUrl`. Existing extensions (AniKotoS et al. — the ones that CAUSE the bug) don't expose it, so Fix 1 is a NO-OP for them.

The doc's §14.5 trace shows the "with `directUrl`" path as the primary happy path — but for existing extensions, the actual path is the "without `directUrl`" re-resolve path (Fix 2). The doc should clarify this upfront — implementers might assume Fix 1 solves the bug for existing extensions, when it actually doesn't.

**Fix:** Add a note to §14.1: "Fix 1 requires extension cooperation — existing extensions that don't expose `directVideoUrl` will fall through to Fix 2. The AniKutoS-style extensions that CAUSE this bug (per `lessons-learned.md:89`) do NOT currently expose `directVideoUrl`, so Fix 2 is the effective fix for them. Fix 1 is forward-looking — new extensions + updated extensions can opt in."

**I3 — §14.1 says "Re-resolve uses the SAME `AutoDownloadEngine`" but §14.3's implementation doesn't.**

Location: `10-player-integration.md` §14.1 (description) vs §14.3 (`ReResolver` implementation).

§14.1 says: "The re-resolve uses the SAME `AutoDownloadEngine` (the new 5-step priority pipeline from `14-auto-download-engine.md` §6.2), but pins the SAME (server, audio, quality) combination via the `resolveContext`."

§14.3's `ReResolver.reResolve` does NOT use the `AutoDownloadEngine` — it does a direct lookup:
```kotlin
val server = result.servers.firstOrNull { it.name == context.serverName } ?: return null
val audio = server.audioVersions.firstOrNull { it.label == context.audioLabel } ?: return null
val video = audio.videos.firstOrNull { it.quality == context.quality } ?: return null
return FreshVideo(url = video.directUrl ?: video.url, headers = video.videoHeaders)
```

§14.3 is CORRECT — we don't want the engine to pick a DIFFERENT (server, audio, quality) on re-resolve; we want the SAME one with a fresh URL. §14.1's description is misleading (suggests the engine is involved when it isn't).

**Fix:** Update §14.1 to say: "The re-resolve does a DIRECT lookup by pinned (server, audio, quality) — it does NOT re-run the `AutoDownloadEngine` (we want the SAME (server, audio, quality) with a fresh URL, not a new pick). See §14.3 for the `ReResolver` implementation."

**I4 — Proposed `Preference<T>` interface is a regression from the OLD project's interface.**

Location: `07-settings-preferences.md` §8.4 (the proposed `Preference<T>` interface).

Verified the OLD project's `Preference<T>` (`app.confused.anikuta.core.preferences.Preference`, 62 lines) has 7 methods: `key()`, `get()`, `set()`, `isSet()`, `delete()`, `defaultValue()`, `changes()`, `stateIn(scope)`. The NEW proposed interface has only 3: `get()`, `set()`, `changes()`.

The OLD project's `stateIn(scope)` returns a `StateFlow<T>` — useful for `collectAsStateWithLifecycle` patterns (more efficient than `collectAsState`). The OLD project's `key()` is useful for debugging + bulk operations (e.g. "reset all download prefs"). The OLD project's `isSet()` is useful for "has the user configured this?" detection (e.g. only show a setup wizard if `downloadFolderUri().isSet() == false`). Dropping these methods limits future flexibility.

**Fix:** Add at least `key(): String`, `defaultValue(): T`, and `isSet(): Boolean` to the proposed `Preference<T>` interface. `stateIn(scope)` can be added later if needed (it's a convenience extension on `changes()`).

**I5 — `ProxyLeaseCoordinator` (Fix 3) design is incomplete — `ProxyKey` + cross-episode semantics.**

Location: `10-player-integration.md` §14.1 (Fix 3) + `15-ui-and-bug-analysis.md` §B.6 (Fix 3).

The `ProxyKey` is defined as `(sourceId, serverName)` — per-source-per-server. But the proxy is created per-`getHosterList(episode)` call — i.e. per-source-per-EPISODE (or even per-source-per-call). A proxy created for episode A's `getHosterList` serves episode A's video URLs; it does NOT serve episode B's URLs (the proxy's URL routing is based on what was passed to `getHosterList`).

So a lease keyed by `(sourceId, serverName)` would reuse a proxy that can't serve the new episode's URLs. The design needs to either:
- Key the lease by `(sourceId, episodeUrl)` — but then the lease doesn't prevent the bug (each episode gets its own proxy + lease; a new episode's `getHosterList` still kills the old episode's proxy).
- Key the lease by `sourceId` only — but then the coordinator must re-call `getHosterList` when the episode changes, which kills the old proxy anyway.
- Cache the resolved videos per `(sourceId, episodeUrl)` + reuse them while a lease is held — but this requires the resolver to be episode-aware caching, not just lease-tracking.

Since Fix 3 is deferred, this isn't a blocker — but the design needs work before implementation. The doc should note this as "design incomplete — needs refinement before implementation".

**I6 — `RetryPolicy.forException` uses fragile string matching on exception messages.**

Location: `16-quality-of-life.md` §1.2 (the `RetryPolicy.forException` implementation).

The policy checks `e.message?.contains("Connection refused")` to detect proxy-churn IOExceptions. This is brittle — exception messages vary across Android versions, OkHttp versions, and extension implementations. A `ConnectException` from OkHttp might say "failed to connect to localhost/127.0.0.1 (port 39369)" or "Connection refused" or just "connect failed". If the message doesn't contain "Connection refused", the policy falls through to the generic `IOException` branch (3 retries instead of 2) — which is fine functionally but inconsistent.

**Fix:** Use exception TYPE checking instead of string matching:
```kotlin
e is ConnectException || e is SocketException -> Policy(true, 2, { 0 })  // proxy-churn
e is IOException -> Policy(true, 3, { attempt -> (1000L * (1 shl (attempt - 1))) })
```
Or wrap the IOException in a custom `ProxyChurnException` at the catch site (in `HttpDownloader.downloadNormal`) so the policy can match by type.

### MINOR

**M1 — Step 5's "unreachable" claim is wrong.**

Location: `14-auto-download-engine.md` §6.2.5.

The doc says "this is unreachable if the resolver returned any servers — but keep as a safety net". Actually, it IS reachable when the resolver returns servers but all servers have empty `audioVersions` (or all audioVersions have empty `videos`). `ResolverResult.NoSources` only fires when `result.servers.isEmpty()` — not when the servers are structurally empty. Fix the comment.

**M2 — `BEST_EFFORT` globalFallback is redundant with the rank tuple's natural behavior.**

Location: `14-auto-download-engine.md` §6.1.2 + §6.2.5.

The rank tuple in Step 2 already considers unlisted values (with `Int.MAX_VALUE` rank) — so the engine ALWAYS picks the best-ranked candidate, even if all dimensions are non-preferred. `BEST_EFFORT` is therefore the natural behavior of the rank tuple, NOT a distinct strategy. It's effectively a no-op enum value (the only alternative behaviours are `ASK` + `DO_NOT_DOWNLOAD`, and both are broken per C2). Consider whether `BEST_EFFORT` even needs to exist as a user-facing option, or whether it should just be the implicit default (with `ASK`/`DO_NOT_DOWNLOAD` as the only explicit choices).

**M3 — "Replicate EXACTLY" + "fix while replicating" language is contradictory.**

Location: `07-settings-preferences.md` §8.6.

The doc says "Replicate EXACTLY (no deviations)" but also "Fix the OLD project's bugs while replicating" (concurrentDownloads reactivity, advancedMaxRetries default=10, serverFallback dead-code). These are CHANGES, not exact replication. Should say "Replicate the LAYOUT + visual design exactly; fix the BEHAVIOURAL bugs while replicating."

**M4 — `onStart { emit(get()) }` in `changes()` Flow is redundant with `collectAsState(initial = ...)`.**

Location: `07-settings-preferences.md` §8.4 (the `PreferenceImpl.changes()` implementation).

The doc's §1.6 (in `14-auto-download-engine.md`) shows the OLD usage pattern: `val serverFallback by preferences.serverFallback().changes().collectAsState(initial = preferences.serverFallback().get())`. The `initial` provides the first value synchronously; then `onStart` emits the same value again. Harmless but wasteful (one duplicate emission per setting per collection). Either remove `onStart` (rely on `collectAsState(initial = ...)`) or remove the `initial` parameter (rely on `onStart`). The OLD project's `Preference.changes()` does NOT have `onStart` — it relies on `collectAsState(initial = ...)`. Match the OLD pattern for consistency.

**M5 — `MutableSharedFlow(extraBufferCapacity = 16)` can drop emissions if consumers are slow.**

Location: `07-settings-preferences.md` §8.4 (the `_changes` flow).

With `tryEmit` (non-suspending) + `BufferOverflow.SUSPEND` (default for `MutableSharedFlow`), if the buffer fills, `tryEmit` returns `false` and the emission is silently dropped. For prefs UI, this is usually fine (the next emission arrives soon), but a burst of pref changes (e.g. drag-reorder firing rapidly, or a slider being dragged) could miss intermediate values. Consider `BufferOverflow.DROP_OLDEST` (explicit) or a larger buffer. Alternatively, use `MutableStateFlow<String?>` (conflates — only the latest matters for prefs).

**M6 — "Cap re-resolve attempts at 2" wording is confusing.**

Location: `10-player-integration.md` §14.1.

The doc says "Cap re-resolve attempts at 2 (one initial + one re-resolve) to avoid infinite loops." This means "1 initial attempt + 1 re-resolve = 2 total attempts". The implementation (per C1) needs to enforce this — but the wording is ambiguous (does "2" mean "2 re-resolves" or "2 total attempts"?). Clarify: "Cap at 1 re-resolve attempt (2 total download attempts: 1 initial + 1 re-resolve)."

**M7 — `applyFallbacks`'s TOP-preference-only check is inherited from OLD + may surprise users.**

Location: `14-auto-download-engine.md` §6.2.3 (Step 3) + OLD `DownloadOrchestrator.kt:224-246`.

Both the OLD + NEW engines only check the TOP preference (`audioPrefs.firstOrNull()`) for the ASK/DO_NOT_DOWNLOAD fallback. With `audioPrefs = ["DUB", "SUB"]` + `audioFallback = DO_NOT_DOWNLOAD` + DUB unavailable → engine fails even though SUB (the #2 pref) is available. This is intentional (DO_NOT_DOWNLOAD means "don't try next"), but the `FallbackStrategy.DO_NOT_DOWNLOAD` KDoc says "don't download; show an error" — doesn't clarify "fail if the TOP pref is unavailable, even if lower prefs are available". Should be documented more explicitly.

---

## Overall verdict

**APPROVED WITH CHANGES.**

The plan is fundamentally sound:
- The 5-step pipeline (`flatten → rank → applyFallbacks → pick → globalFallback`) correctly addresses the user's gap (no way to configure dimension priority). The worked example traces confirm the engine picks the right video for both `[AUDIO, QUALITY, SERVER]` + `[SERVER, QUALITY, AUDIO]` priority orders.
- The `serverFallback` dead-code bug IS fixed (Step 3 consults all 3 per-dim fallbacks in user-defined order).
- The proxy-churn bug root cause is correctly identified (extension local-proxy-server port churn). The layered fix (Fix 1 architecturally avoids; Fix 2 recovers; Fix 3 prevents; Fix 4 unrelated) is the right approach.
- The pipeline IS highly extensible (4th dimension, weighted scoring, per-source priority, conflict-surfacing UI — all incremental).
- The settings UI replication spec is accurate (verified all 8 composables + line numbers + server merge logic against the OLD `DownloadSettingsScreen.kt`).
- The reactive `PreferenceStore` design works with the new project's existing `core/preferences` (backward compatible).

**BUT — 2 CRITICAL issues block implementation as-written:**

- **C1 (HttpDownloader unbounded recursion):** The re-resolve catch block in `05-downloaders.md` §11.3 recursively calls `downloadNormal` with no cap. This would cause `StackOverflowError` when the proxy keeps dying — WORSE than the original bug. Must add a `reResolveAttempts` counter parameter.
- **C2 (globalFallback ASK/DO_NOT_DOWNLOAD useless):** The `globalFallback` only fires when `sortedCandidates.isEmpty()` — at which point showing the picker is useless. Must redefine Step 5 to fire based on the picked candidate's match quality (perfect match vs. best-effort).

**Plus 6 IMPORTANT issues** (I1-I6) that should be addressed before implementation:
- I1: Default `[AUDIO, QUALITY, SERVER]` doesn't preserve old behaviour — acknowledge the deliberate change.
- I2: Fix 1 depends on extension cooperation — clarify that Fix 2 is the actual fix for existing extensions.
- I3: §14.1 description contradicts §14.3 implementation — §14.3 is correct, fix §14.1.
- I4: Proposed `Preference<T>` interface is a regression — add `key()`/`defaultValue()`/`isSet()`.
- I5: `ProxyLeaseCoordinator` design is incomplete (ProxyKey + cross-episode semantics) — note as "design needs refinement".
- I6: `RetryPolicy.forException` uses fragile string matching — use exception types.

**Plus 7 MINOR issues** (M1-M7) — language clarifications + small inefficiencies, not blockers.

**Next actions for the plan author:**
1. Fix C1: add `reResolveAttempts` counter to `HttpDownloader.downloadNormal` (in `05-downloaders.md` §11.3) + update the catch block to check the cap before recursing.
2. Fix C2: redefine Step 5 in `14-auto-download-engine.md` §6.2.5 to fire based on the picked candidate's match quality, not on `sortedCandidates.isEmpty()`.
3. Address I1-I6 per the fixes above.
4. Then proceed to Phase D.2 (download engine implementation) + Phase D.5 (settings UI implementation).

**Next review round (DL-REVIEW-3) should focus on:** the queue management + state machine + downloaders (`02-queue-management.md`, `03-state-machine.md`, `05-downloaders.md`) — specifically the `DownloadQueue.launchDownload` retry loop interaction with the `HttpDownloader.downloadNormal` re-resolve (per C1), the `RETRYING` state machine transitions, + the `DynamicProgressTracker` smoothing math.
