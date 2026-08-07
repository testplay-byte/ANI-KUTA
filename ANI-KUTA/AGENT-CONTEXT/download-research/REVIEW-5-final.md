# REVIEW-5 (FINAL) — Cross-doc consistency + Implementation plan coherence + Proxy-churn fix integration + Future-proofing

> **Task ID:** DL-REVIEW-5
> **Reviewer:** senior-review-agent
> **Scope:** Review Round 5 of 5 (FINAL) — `13-implementation-plan.md` (master plan) + `10-player-integration.md` §14 (ReResolver) + `12-di-wiring.md` (Koin) + cross-doc consistency audit across ALL plan docs (00–16) + ALL prior review findings (REVIEW-1 through REVIEW-4).
> **Method:** Re-read every prior review's CRITICAL + IMPORTANT issues → verified each against the current state of the plan docs → flag carry-overs as either FIXED or NOT-FIXED → audited every name/term/state/pref-key/class for cross-doc consistency → assessed the implementation plan's coverage of the critical issues → assessed future-proofing per the user's explicit requirements → assessed the proxy-churn fix end-to-end.
> **Verdict:** **NEEDS MAJOR REWORK** — the design is fundamentally sound, but Reviews 1–4 collectively flagged 22 CRITICAL issues, of which **18 are STILL NOT FIXED** in the docs. The implementation plan (`13-implementation-plan.md`) does not reflect most of these as action items. Shipping D.0 as-written will result in (a) a non-compiling build (Coil 2 vs Coil 3, `HttpException` unresolved, `notificationManager` undefined, `KoinComponent` missing, `downloadVideoToCache` arity mismatch in §14), (b) a `StackOverflowError` (unbounded re-resolve recursion), (c) a `ForegroundServiceDidNotStartInTimeException` crash on Android 12+, (d) corrupt HLS output on flaky CDNs, (e) tasks stuck in RETRYING forever after a crash, (f) the user's "progress bar jumps to 100%" complaint NOT actually fixed, and (g) a `NoBeanDefFoundException` for `DownloadStorageProvider` if anything triggers the migration binding.

---

## 1. Methodology recap

Read in full:
- `worklog.md` (733 lines) — for context on DL-RESEARCH → DL-PLAN-REWRITE → DL-REVIEW-1 through DL-REVIEW-4.
- `REVIEW-1-storage-db.md` (258 lines) — 5 CRITICAL (C1–C5) + 9 IMPORTANT (I1–I9) + 10 MINOR.
- `REVIEW-2-autodl.md` (572 lines) — 2 CRITICAL (C1–C2) + 6 IMPORTANT (I1–I6) + 7 MINOR.
- `REVIEW-3-queue-downloaders.md` (438 lines) — 5 CRITICAL (C1–C5) + 15 IMPORTANT (I1–I15) + 10 MINOR.
- `REVIEW-4-notifications-ui.md` (564 lines) — 8 CRITICAL (C1–C8) + 12 IMPORTANT (I1–I12) + 12 MINOR.
- `13-implementation-plan.md` (537 lines) — the master plan.
- `10-player-integration.md` (494 lines) — §14 ReResolver + §14.1–14.5 proxy-churn fix.
- `12-di-wiring.md` (556 lines) — Koin wiring + the post-rewrite §11.
- All other plan docs (00–16, ~10,000 lines total) — for the cross-doc consistency matrix.

Total CRITICAL issues across Reviews 1–4: **5 + 2 + 5 + 8 = 20** (some are carry-overs — counted once, the actual unique count is **18**; Review 3 C1 = Review 2 C1, Review 4 C5 = Review 3 C5, Review 4 C6 = Review 3 I7, Review 4 C7 = Review 3 I2).

---

## 2. Cross-doc consistency matrix

### 2.1 State names — **FAIL (CRITICAL)**

| Term / name | `03-state-machine.md` | `02-queue-management.md` | `16-quality-of-life.md` | `09-details-page-download-ui.md` | `08-downloads-page-ui.md` | `13-implementation-plan.md` | `11-db-schema.md` | Verdict |
|---|---|---|---|---|---|---|---|---|
| Type name | `DownloadStatus` (enum, §1) + `DownloadState` (sealed interface, §9) — doc is **internally inconsistent** | `DownloadStatus` | `DownloadStatus` (sealed interface, §1.3) | `DownloadStatus` | `DownloadStatus` | `DownloadState.kt` stub (line 16) + `DownloadStatus.kt` to-create (line 216) — **internally inconsistent** | `state TEXT` column ("QUEUED"/"DOWNLOADING"/"PAUSED"/"COMPLETED"/"ERROR"/"CANCELLED") | **3 different definitions** (enum, sealed interface #1, sealed interface #2) + the implementation plan can't decide whether to keep the stub name or rename it. |
| `RETRYING` state | ❌ not in §1 enum, §2 diagram, §3 transition table, §9 stub — only mentioned in §9 recommendation prose | ❌ not mentioned | ✅ §1.3 introduces `data class RETRYING(attempt, maxAttempts, lastError)` | ❌ 7-variant `EpisodeDownloadState` has no `Retrying` | ❌ bulk "Retry all" only iterates `ERROR` | ❌ not mentioned anywhere in the master plan | ❌ comment at line 154 lists 6 states, no RETRYING; `resetDownloadingToQueued` only resets `DOWNLOADING` | **Carry-over Review 3 I1 + Review 4 M8 + C7. NOT FIXED.** RETRYING exists in exactly one doc and isn't propagated. |
| `ERROR` vs `Failed` | §1 uses `ERROR`; §9 stub uses `Failed(message)` | `ERROR` | `ERROR(message)` (as `data class ERROR` — unusual Kotlin style) | `Error` (PascalCase, in `EpisodeDownloadState`) | `ERROR` | line 16 uses `Failed`; line 216 implies `ERROR` (matches OLD enum) | `ERROR` (string) | **`Failed` (PascalCase, sealed-interface variant) vs `ERROR` (UPPERCASE, enum constant) coexist.** Review 3 M1 — NOT FIXED. |
| `CANCELLED` | ✅ §1 + §3 transition table | ✅ used in `purgeCancelled` | ❌ NOT in §1.3's sealed interface (dropped) | ❌ 7-variant list doesn't mention it as distinct | ❌ not addressed | line 246 references `'CANCELLED'` SQL value | ✅ in column comment + line 219 `WHERE state IN (..., 'ERROR')` doesn't include CANCELLED — but line 154 comment does | **Inconsistent.** QoL §1.3 silently drops CANCELLED; SQL + 03-state-machine.md keep it. |
| `state` SQL column values | 6 (per §1) | (uses DownloadStatus constants) | 7 (adds RETRYING) | (UI-side) | (UI-side) | 6 implied (reset DOWNLOADING→QUEUED only) | 6 (line 154 — no RETRYING) | **DB will not store RETRYING.** A task set to RETRYING in memory will be persisted as… what? Either the DB write fails (CHECK constraint, if added) or the column comment lies. |

**Required fix:** Pick ONE canonical type (`enum class DownloadStatus` per Review 3 M1's recommendation — keeps `isTerminal`/`isActive` helpers + matches the OLD project + the implementation plan's line 216 intent). Update ALL docs to:
1. Add `RETRYING` to `03-state-machine.md` §1 enum + §2 diagram + §3 transition table + §9 stub-removal note.
2. Add `RETRYING` to `02-queue-management.md` §13's `pause`/`cancel`/`retry` allowed-state sets (Review 3 M6).
3. Add `data class Retrying(attempt: Int, maxAttempts: Int, lastError: String)` to `09-details-page-download-ui.md`'s 8-variant `EpisodeDownloadState` (Review 4 M8).
4. Update `08-downloads-page-ui.md`'s bulk "Retry all" to handle RETRYING (skip — already retrying) (Review 4 M9).
5. Update `11-db-schema.md` §3 line 154 comment to include RETRYING + update `resetDownloadingToQueued` (line 251) to `WHERE state IN ('DOWNLOADING', 'RETRYING')` (Review 3 I2 / Review 4 C7).
6. Update `13-implementation-plan.md` Phase D.0/D.1 to mention RETRYING as a new state.

### 2.2 Class names — **PASS (mostly consistent)**

| Class | Docs that reference it | Verdict |
|---|---|---|
| `DownloadQueue` | 02, 03, 05, 12, 13, 16 | ✅ Consistent |
| `DownloadManager` / `DefaultDownloadManager` | 02, 12, 13 | ✅ Consistent |
| `AutoDownloadEngine` | 10, 12, 13, 14 | ✅ Consistent (no `AutoDownloadResolver` alias found anywhere) |
| `ReResolver` | 05, 10, 12, 13 | ✅ Consistent |
| `ResolveContext` | 05, 10, 13 | ⚠️ `13-implementation-plan.md` line 314 says it captures `(sourceId, episodeUrl, serverName, audioLabel, quality)` — but `10-player-integration.md` §14.2 also has `mainId` + `episodeKey`. The plan's summary omits 2 fields. Minor. |
| `DownloadScanner` | 04, 12, 13 | ✅ Consistent |
| `DownloadStorageProvider` | 04, 12, 13 | ✅ Consistent |
| `DownloadOrchestrator` | 12, 13, 15 | ✅ Consistent |
| `DownloadService` | 06, 12, 13 | ✅ Consistent |
| `DownloadNotificationManager` | 06, 12, 13 | ✅ Consistent |
| `HttpDownloader` / `HlsDownloader` / `AdvancedHttpDownloader` | 05, 12, 13 | ✅ Consistent |
| `DynamicProgressTracker` | 02, 05, 13 | ✅ Consistent |
| `TempDownloadCache` | 04, 05, 12, 13 | ✅ Consistent |
| `ContentDataJson` | 04, 13 | ✅ Consistent |
| `ProxyLeaseCoordinator` | 10, 13, 15 | ✅ Consistent (deferred) |
| `ServerDiscoveryStore` | 12, 13 | ✅ Consistent |
| `Downloader` (interface) | 05, 13 | ✅ Consistent |

### 2.3 Pref keys — **PASS (consistent)**

| Pref | Kotlin accessor | Storage key | Docs | Verdict |
|---|---|---|---|---|
| Dimension priority | `dimensionPriority()` | `pref_dl_dimension_priority` | 07, 13, 14 | ✅ Consistent |
| Global fallback | `globalFallback()` | `pref_dl_global_fallback` | 07, 13, 14 | ✅ Consistent |
| All 15 existing prefs | camelCase accessors | `pref_dl_*` snake_case | 07 | ✅ Consistent |

No `dimension_priority` (snake_case accessor) or `globalFallback` (camelCase storage key) confusion found.

### 2.4 Implementation phases — **PASS WITH ONE BUG**

| Phase | Plan section reference | Cross-reference target | Verdict |
|---|---|---|---|
| D.0 Foundations | 13 §5 D.0 | 11 §3 (schema) + 07 §8.4 (prefs) | ✅ Correct (but D.0 task #2 still says `3.sqm` — Review 1 C3 NOT FIXED). |
| D.1 Engine + Storage | 13 §5 D.1 | 04 (storage) + 05 (downloaders) + 11 (DB) | ✅ Correct. |
| D.2 Orchestrator + Auto-download engine + proxy-churn fix | 13 §5 D.2 | 14 §6.2 (engine) + 10 §14 (proxy-churn) | ✅ Correct (but the proxy-churn spec is broken per §3 below). |
| D.3 Queue management + Dynamic progress | 13 §5 D.3 | 02 §13 (queue) + 05 §11.2 (progress) | ✅ Correct (but the progress spec is broken — `recentRatios` not threaded per Review 3 C2). |
| D.4 Foreground service + Notifications | 13 §5 D.4 | 06 §13 (service + notifications) | ✅ Correct (but the service spec is broken — Review 4 C1–C4). |
| D.5 Settings UI | 13 §5 D.5 | 07 §8 + 14 §6.5 | ✅ Correct. |
| D.6 Downloads page UI + Episode controls + Player integration | 13 §5 D.6 | 08 + 09 + 10 + 15 | ⚠️ Line 434 says "**Player integration (D.14)**" — `D.14` doesn't exist. Should be `D.6`. Typo. |
| D.7 QoL features | 13 §5 D.7 | 16 §1–§6 | ✅ Correct (but QoL §1 is broken — Review 4 C5–C8). |
| D.8 Polish + testing | 13 §5 D.8 | (cross-cuts) | ✅ Correct. |

### 2.5 Other cross-doc inconsistencies found in this round

| # | Topic | Where | Verdict |
|---|---|---|---|
| **N1** | `onNetworkChanged` body | `02-queue-management.md` §13.3 line 519–527 (NOT mutex-wrapped) vs `16-quality-of-life.md` §2.2 line 149–167 (mutex-wrapped, calls `pause` inside lock) | **DIVERGENT.** Carry-over Review 4 C8 + M5. NOT FIXED. |
| **N2** | The catch-block re-resolve call | `10-player-integration.md` §14.1 calls `downloadVideoToCache(fresh.url, fresh.headers, tempFile, taskId, onProgress)` (5 args). `05-downloaders.md` §11.3's `downloadVideoToCache` signature requires 6 args (incl. `resolveContext`). §11.3's catch block calls `downloadNormal(fresh.url, fresh.headers, tempFile, taskId, resolveContext, onProgress)` (6 args, recurses on `downloadNormal` not `downloadVideoToCache`). | **DIVERGENT.** §14 won't compile as written. §11.3 has unbounded recursion (Review 2 C1 / Review 3 C1 — NOT FIXED). |
| **N3** | "Re-resolve uses the SAME `AutoDownloadEngine`" claim | `10-player-integration.md` §14.1 line 376 says YES; §14.3's `ReResolver.reResolve` does a direct lookup, never calls `AutoDownloadEngine`. The `ReResolver` constructor takes `autoDownloadEngine: AutoDownloadEngine` but never uses it (dead DI param). | **DIVERGENT.** Carry-over Review 2 I3. NOT FIXED. |
| **N4** | `dimensionPriority` default `[AUDIO, QUALITY, SERVER]` "preserves old behaviour" claim | `14-auto-download-engine.md` line 740 + line 1007 + `13-implementation-plan.md` line 518 — all repeat the false claim. Review 2 I1's trace proves the OLD engine picks `Streamtape/SUB/1080p` while the NEW with `[AUDIO, QUALITY, SERVER]` picks `Vidstreaming/DUB/1080p`. | **FALSE.** Carry-over Review 2 I1. NOT FIXED. |
| **N5** | `Preference<T>` interface — 3 methods vs 7 | `07-settings-preferences.md` §8.4 line 481–485 has only `get`/`set`/`changes`. OLD project's `Preference<T>` has 7 (`key`/`get`/`set`/`isSet`/`delete`/`defaultValue`/`changes`/`stateIn`). | **REGRESSION.** Carry-over Review 2 I4. NOT FIXED. |
| **N6** | `onStart { emit(get()) }` in `changes()` Flow | `07-settings-preferences.md` §8.4 line 503 — redundant with `collectAsState(initial = ...)`. OLD project's `Preference.changes()` does NOT have `onStart`. | **Inefficient.** Carry-over Review 2 M4. NOT FIXED. |
| **N7** | `DownloadStorageProvider` is sometimes a Koin binding, sometimes not | `12-di-wiring.md` §11.1 line 352 says `single { DownloadStorageProvider(androidContext(), get()) }` (explicit binding). §10 (OLD project state) line 82 + line 143 says it's internal to `DefaultDownloadManager` (not a Koin binding). §11.6 line 534 acknowledges the change is intentional. | **Pass** — the inconsistency is between OLD and NEW, and the NEW is the intended design. But §10's prose (which describes the OLD project) is misleading without a callout. Minor. |
| **N8** | `DownloadScanner` constructor params | `12-di-wiring.md` §11.1 line 353: `DownloadScanner(androidContext(), get(), get(), get())` — 4 params (Context + 3 deps). §11.5 line 510 says: `DownloadScanner(Context, DownloadStorageProvider, DownloadStore, ContentRepository)`. | ⚠️ The `ContentRepository` dep is mentioned in §11.5 but NOT in the implementation plan's D.1 file list (line 226: "DownloadScanner.kt | (NEW, see 04-storage-paths.md §7) | The scan-on-startup engine. Walks video/images/text/, reads each data.json, UPSERTs to DB by mainId."). The plan doesn't list `ContentRepository` as a constructor dep — implementers may miss it. Minor. |
| **N9** | "Cap re-resolve attempts at 2" wording | `10-player-integration.md` §14.1 line 377 + `13-implementation-plan.md` line 315 — both say "caps at 2 (one initial + one re-resolve)". But neither enforces it in code. Review 2 M6 — wording is also confusing. | **NOT ENFORCED.** Carry-over Review 2 C1 + Review 3 C1. NOT FIXED. |
| **N10** | `resetDownloadingToQueued` SQL | `11-db-schema.md` §3 line 248–251 only resets `WHERE state = 'DOWNLOADING'`. `16-quality-of-life.md` §1.3 line 101 claims it "also resets RETRYING → QUEUED". `13-implementation-plan.md` line 249 + line 457 only mention DOWNLOADING. | **FALSE claim in QoL doc.** Carry-over Review 3 I2 + Review 4 C7. NOT FIXED. |

---

## 3. Critical issues still open (from Reviews 1–4 that the plan hasn't addressed)

> Verified against the current state of every plan doc. Each row is the EXACT issue from the prior review, with a "Status" column showing FIXED / NOT FIXED / PARTIAL.

### 3.1 From REVIEW-1 (storage + DB)

| # | Issue | Status | Where it's still broken |
|---|---|---|---|
| **R1-C1** | `data.json` example `"contentId": "anilist:101522"` is wrong (real format is 6-section per `ContentIdGenerator.kt`). | **NOT FIXED** | `04-storage-paths.md` §5.2 line 359 + §5.1 line 289–290 (both example strings are wrong). |
| **R1-C2** | `data.json` doesn't store `dataSourceId/systemId/extensionRepoId/extensionId/displaySource`. The scan's `upsertFromDataJson` can't restore the `content` table's FK columns. | **NOT FIXED** | `04-storage-paths.md` §5.1 (ContentDataJson schema, line 283–310 — no FK fields). |
| **R1-C3** | Migration plan claims `1.sqm` + `2.sqm` exist — they don't. Project has ZERO `.sqm` files. Proposed `3.sqm` will fail (SQLDelight 2.x expects sequential migrations from `1.sqm`). | **NOT FIXED** | `11-db-schema.md` §3.3 line 351 ("the existing migration chain — 1.sqm, 2.sqm") + line 357 (`3.sqm`). Also `13-implementation-plan.md` Phase D.0 task #2 line 197 still says `3.sqm`. |
| **R1-C4** | `DatabaseDriverFactory.create()` doesn't pass `migrations = ...` to `AndroidSqliteDriver`. Schema version change crashes existing installs at startup. | **NOT FIXED** | `11-db-schema.md` §3.3 has no mention of updating `DatabaseDriverFactory`. `13-implementation-plan.md` doesn't list it as a D.0 task. |
| **R1-C5** | `getDownloadedMainIds` query has both `DISTINCT` and `GROUP BY` (redundant) + non-deterministic bare-column values. | **NOT FIXED** | `11-db-schema.md` §3.2 line 322–326 (identical to what Review 1 flagged). |
| **R1-I1** | Same-title collision algorithm is unimplemented. `ensureContentDir` is called but not specified. | **NOT FIXED** | `04-storage-paths.md` §4.1 + §6.3 + line 835 ("Recommendation: option (a) — keep folder names human-readable") — recommendation only, no spec. |
| **R1-I4** | Stale `content_id` in `download_queue` after source switch. `ContentRepository.updateContentSources` updates `content.content_id` but nothing updates `download_queue.content_id`. | **NOT FIXED** | `11-db-schema.md` §3.2 has no `updateDownloadContentId` query. |
| **R1-I5** | `.nomedia` file not created in content folders. Downloaded `.mp4` files will appear in gallery apps. | **NOT FIXED** | `04-storage-paths.md` has zero mentions of `.nomedia` (verified by grep). |
| **R1-I6** | 999-open-files limit + `DocumentFile.findFile()` O(N) — not mentioned. Scan performance on large libraries will be poor. | **NOT FIXED** | `04-storage-paths.md` §7.1 + §7.3 don't mention the limit or the listFiles-once optimization. |
| **R1-I9** | Fractional episode format `%.1f` rounds `12.25` → `12.3`. | **NOT FIXED** | `04-storage-paths.md` §4.2 still uses `%.1f` (per `13-implementation-plan.md` line 458 which says "N/A — we use 5-digit padded `E00001.5` for fractional specials" — but the doc itself wasn't updated). |
| **R1-M2** | `audio/` format folder mentioned in §3.2 but not in the scan's `listOf("video", "images", "text")`. | **NOT FIXED** | `04-storage-paths.md` line 149 + lines 613, 744 — still inconsistent. |

### 3.2 From REVIEW-2 (auto-download + proxy-churn fix)

| # | Issue | Status | Where it's still broken |
|---|---|---|---|
| **R2-C1** | `HttpDownloader.downloadNormal` re-resolve catch block has UNBOUNDED RECURSION (no `reResolveAttempts` counter). | **NOT FIXED** | `05-downloaders.md` §11.3 line 664: `return downloadNormal(fresh.url, fresh.headers, tempFile, taskId, resolveContext, onProgress)` — recursive call passes the SAME `resolveContext`, no counter. `10-player-integration.md` §14.1 line 369: `return downloadVideoToCache(fresh.url, fresh.headers, tempFile, taskId, onProgress)` — also recursive, also no counter, ALSO missing the `resolveContext` arg (won't compile). |
| **R2-C2** | `globalFallback = ASK / DO_NOT_DOWNLOAD` only fires when `sortedCandidates.isEmpty()` — useless UX. | **NOT FIXED** | `14-auto-download-engine.md` §6.2.5 line 848 (`if (sortedCandidates.isEmpty()):`) — unchanged. |
| **R2-I1** | `dimensionPriority` default `[AUDIO, QUALITY, SERVER]` does NOT preserve old behaviour. | **NOT FIXED** | `14-auto-download-engine.md` line 740 + line 1007 + `13-implementation-plan.md` line 518 — all repeat the false "preserves old behaviour" claim. |
| **R2-I2** | Fix 1 (`directUrl`) depends on extension cooperation — existing extensions don't expose it. | **NOT FIXED** | `10-player-integration.md` §14.1 doesn't add the clarifying note. §14.5's "with `directUrl`" trace is presented as the primary path, but for existing extensions the actual path is Fix 2. |
| **R2-I3** | §14.1 says "Re-resolve uses the SAME `AutoDownloadEngine`" but §14.3's implementation doesn't. | **NOT FIXED** | `10-player-integration.md` §14.1 line 376 still says "uses the SAME `AutoDownloadEngine`"; §14.3 still does direct lookup. |
| **R2-I4** | Proposed `Preference<T>` interface is a regression (3 methods vs 7). | **NOT FIXED** | `07-settings-preferences.md` §8.4 line 481–485 — still 3 methods. |
| **R2-I5** | `ProxyLeaseCoordinator` design is incomplete (`ProxyKey` + cross-episode semantics). | **NOT FIXED** (deferred) | `10-player-integration.md` §14.1 Fix 3 — design unchanged. Acceptable as deferred, but should be noted. |
| **R2-I6** | `RetryPolicy.forException` uses fragile string matching on exception messages. | **NOT FIXED** | `16-quality-of-life.md` §1.2 line 68: `e.message?.contains("Connection refused")` — string matching still there. |
| **R2-M4** | `onStart { emit(get()) }` in `changes()` Flow is redundant with `collectAsState(initial = ...)`. | **NOT FIXED** | `07-settings-preferences.md` §8.4 line 503 — `onStart` still there. |

### 3.3 From REVIEW-3 (queue + state machine + downloaders)

| # | Issue | Status | Where it's still broken |
|---|---|---|---|
| **R3-C1** | `HttpDownloader.downloadNormal` re-resolve catch block STILL has unbounded recursion. (Carry-over from R2-C1.) | **NOT FIXED** | Same as R2-C1 above. |
| **R3-C2** | `recentRatios` parameter of `DynamicProgressTracker.compute` is NOT threaded through by the queue. | **NOT FIXED** | `05-downloaders.md` §11.2 line 496–502: `compute(...)` requires `recentRatios: List<Float>`. `02-queue-management.md` §13.3 line 427–433: queue's `launchDownload` calls `compute(downloaded, total, prevTotal, prevEstimate)` — **only 4 args, missing `recentRatios`**. Won't compile OR silently drops the moving-average feature. |
| **R3-C3** | HLS `estimatedTotal` is computed ONCE + never refined. | **NOT FIXED** | `05-downloaders.md` §11.4 line 713–719: `var estimatedTotal = -1L; if (segments.isNotEmpty()) { ... estimatedTotal = firstSegmentSize * segments.size }` — computed once. Line 731–733's loop calls `onProgress(tempFile.length(), estimatedTotal)` with the SAME `estimatedTotal` every iteration. The doc's claim at line 771 ("The estimated total converges to the real total as more segments download") is FALSE. |
| **R3-C4** | HLS per-segment retry writes to the SAME FileOutputStream → corrupt output on partial-then-retry. | **NOT FIXED** | `05-downloaders.md` §11.4 line 740–757: `downloadSegmentWithRetry` calls `downloadSegment(segUrl, headers, out)` — `out` is the SAME FileOutputStream shared across retries. No `truncate(posBefore)` on failure. |
| **R3-C5** | `RetryPolicy.forException` uses `e is HttpException` — but no `HttpException` class exists in `:core:download`. | **NOT FIXED** | `16-quality-of-life.md` §1.2 lines 73–75: `e is HttpException && e.code in 500..599 -> ...` — still there. `:core:download/build.gradle.kts` doesn't depend on `:core:source-api` (where `HttpException` actually lives). Carry-over Review 4 C5. |
| **R3-I1** | `RETRYING` state is NOT in `03-state-machine.md` diagram or transition table. | **NOT FIXED** | See §2.1 above. |
| **R3-I2** | `resetDownloadingToQueued` does NOT reset RETRYING → QUEUED on restart. | **NOT FIXED** | See §2.1 + N10 above. |
| **R3-I3** | Per-tick `scope.launch { mutex.withLock { … } }` is a severe performance + correctness flaw. | **NOT FIXED** | `02-queue-management.md` §13.3 line 436–449: still wraps every progress tick in `scope.launch { mutex.withLock { mutateTask(...) } }`. For 5 concurrent downloads × 12,500 ticks = 60,000+ pending coroutines. |
| **R3-I4** | The 95% cap doesn't actually smooth the final jump (the user's complaint is NOT fixed). | **NOT FIXED** | `05-downloaders.md` §11.3 line 597–613 (HttpDownloader.download) — no `onProgress` calls during validation/subtitle/metadata/publish. Bar still jumps 95→100. |
| **R3-I5** | `DynamicProgressTracker.complete()` is dead code. | **NOT FIXED** | `05-downloaders.md` §11.2 line 537 — function exists, never called. |
| **R3-I6** | `HttpDownloader.download`'s `finally { tempCache.cleanupTask(task.id) }` deletes Advanced downloader's resume metadata. | **NOT FIXED** | `05-downloaders.md` §11.3 — no `finally` shown, but no spec for distinguishing CancellationException (preserve resume metadata) from completion/error (delete everything). |
| **R3-I7** | `setRetryingStatus` + `setErrorStatus` are called but never defined. | **NOT FIXED** | `16-quality-of-life.md` §1.2 line 49 + line 54 — call sites shown, function bodies not. Carry-over Review 4 C6. |
| **R3-I10** | Pause/resume resets prevTotal + prevEstimate → bar jumps backward on resume. | **NOT FIXED** | `02-queue-management.md` §13.3 line 424–425: `var prevTotal = 0L; var prevEstimate = 0L` — closure vars, GC'd on pause. No persistence in DB or `resume.json`. |
| **R3-I11** | `probeSegmentSize` uses HEAD — many anti-scraping CDNs reject HEAD or return wrong Content-Length. | **NOT FIXED** | `05-downloaders.md` §11.4 line 759–765: still uses `Request.Builder().url(segUrl).head()`. |
| **R3-I12** | The "sanity check" if-branch in NEW DynamicProgressTracker is a no-op. | **NOT FIXED** | `05-downloaders.md` §11.2 line 513–517: both branches return `computeUnknownTotal(...)`. |
| **R3-I15** | `mutateTask` doesn't acquire the mutex itself — API is fragile. | **NOT FIXED** | `02-queue-management.md` §13.3 — `mutateTask` called inside `mutex.withLock` blocks at lines 418, 438, 454, 469 but not self-protected. |

### 3.4 From REVIEW-4 (notifications + UI + foreground service)

| # | Issue | Status | Where it's still broken |
|---|---|---|---|
| **R4-C1** | `DownloadService.queueCollector` may call `stopSelf()` without ever calling `startForeground()` → `ForegroundServiceDidNotStartInTimeException` crash on Android 12+. | **NOT FIXED** | `06-notifications-foreground-service.md` §13.7 line 602–617: `queueCollector` is a coroutine launched at construction; `startForeground` only fires inside the `else` branch when `active.isNotEmpty()`. If the queue is empty on service start (which is the COMMON case — `DownloadService.start()` is called BEFORE `manager.enqueueDownload` returns), the system kills the service after 5s. The existing `ExtensionInstallService` pattern (synchronous `startForeground` in `onStartCommand`) is NOT copied. |
| **R4-C2** | `downloadCover` uses Coil 2 API, but the NEW project uses Coil 3 — won't compile. | **NOT FIXED** | `06-notifications-foreground-service.md` §13.2 line 466–467: `runBlocking { Coil.imageLoader(context).execute(...) }` — Coil 2 API. NEW project's `ImageLoaderFactory.kt` uses Coil 3 (`coil3.*`). |
| **R4-C3** | `runBlocking { Coil.imageLoader(context).execute(...) }` on `Dispatchers.Main` → ANR. | **NOT FIXED** | Same line 466–467. Combined with §13.7 line 600 (`Dispatchers.Main`), the entire thumbnail-load path is on the main thread. |
| **R4-C4** | `ACCESS_NETWORK_STATE` permission is MISSING from the NEW project — `registerNetworkCallback` will SecurityException-crash. | **NOT FIXED** | `13-implementation-plan.md` line 25 says `ACCESS_NETWORK_STATE` is "implicit" in the manifest (incorrect — Review 4 verified it's NOT declared). `06-notifications-foreground-service.md` §13.8 (the manifest entry) only adds the `<service>` element, not the permission. `:core:download` has no manifest at all. |
| **R4-C5** | (Carry-over from R3-C5) `HttpException` invisible to `:core:download`. | **NOT FIXED** | See R3-C5 above. |
| **R4-C6** | (Carry-over from R3-I7) `setRetryingStatus` called but not defined. | **NOT FIXED** | See R3-I7 above. |
| **R4-C7** | (Carry-over from R3-I2) `resetDownloadingToQueued` does NOT reset RETRYING. | **NOT FIXED** | See R3-I2 above. |
| **R4-C8** | `onNetworkChanged` calls `pause(it.id)` inside `mutex.withLock` → non-reentrant Mutex DEADLOCK. | **NOT FIXED** | `16-quality-of-life.md` §2.2 line 149–167: `mutex.withLock { ... pause(it.id) ... }` — `pause` (if mutex-protected per Review 3 I15's recommendation) deadlocks. `02-queue-management.md` §13.3 has a DIFFERENT (non-mutex-wrapped) version. **DIVERGENT — pick one.** |
| **R4-I1** | `DownloadService` references `notificationManager` that is never declared. | **NOT FIXED** | `06-notifications-foreground-service.md` §13.7 line 613. |
| **R4-I2** | `DownloadService` uses Koin `inject<>()` delegate but doesn't implement `KoinComponent`. | **NOT FIXED** | `06-notifications-foreground-service.md` §13.7 line 597: `class DownloadService : Service()` — no `KoinComponent`. Won't compile. |
| **R4-I3** | `loadThumbnail` does synchronous SAF I/O on `Dispatchers.Main` → ANR. | **NOT FIXED** | `06-notifications-foreground-service.md` §13.2 line 452–461 + §13.7 line 600. |
| **R4-I4** | `DownloadService.queueCollector` runs on `Dispatchers.Main` — wrong dispatcher for notification building. | **NOT FIXED** | `06-notifications-foreground-service.md` §13.7 line 600. |
| **R4-I5** | `R.drawable.ic_pause` and `R.drawable.ic_cancel` are referenced but not declared as resources to create. | **NOT FIXED** | `06-notifications-foreground-service.md` §13.2 line 429–430. |
| **R4-I6** | Android 14+ `dataSync` foreground service has a 6-hour daily runtime cap — not mentioned. | **NOT FIXED** | `06-notifications-foreground-service.md` §13 + §10 don't mention the cap or `onTimeout`. |
| **R4-I9** | Workflow doc traces the OLD 3-step `selectBestVideo` without mentioning the NEW 5-step `AutoDownloadEngine`. | **NOT FIXED** | `01-workflow-click-to-queue.md` §7 still traces the OLD algorithm. |
| **R4-I10** | `scheduleAutoClear`'s `autoClearScheduled.add(taskId)` is OUTSIDE `mutex.withLock` — race on the Set. | **NOT FIXED** | `16-quality-of-life.md` §6.1 + `02-queue-management.md` §13.3 line 488–499: `if (taskId in autoClearScheduled) return; autoClearScheduled.add(taskId)` — outside mutex. |
| **R4-M8** | 09 doc lists 7 `EpisodeDownloadState` variants but doesn't cover the NEW RETRYING state. | **NOT FIXED** | See §2.1 above. |
| **R4-M9** | 08 doc's bulk "Retry all" iterates `queue.filter { it.status == ERROR }` — what about RETRYING? | **NOT FIXED** | See §2.1 above. |

### 3.5 Summary of carry-overs

| Review | CRITICAL flagged | CRITICAL fixed | CRITICAL NOT FIXED | Net fix rate |
|---|---|---|---|---|
| Review 1 (storage/DB) | 5 | 0 | 5 | 0% |
| Review 2 (autodl/proxy-churn) | 2 | 0 | 2 | 0% |
| Review 3 (queue/state/downloaders) | 5 | 0 | 5 | 0% |
| Review 4 (notifications/UI) | 8 (3 carry-overs from Review 3) | 0 | 8 | 0% |
| **Total unique CRITICALs** | **18** | **0** | **18** | **0%** |

**Result:** **NOT A SINGLE CRITICAL issue from Reviews 1–4 has been fixed in the plan docs.** This is the single most damning finding of this final review round.

---

## 4. New issues found in this round (Round 5)

### 4.1 NEW CRITICAL — Phase D.0 task #2 will crash existing dev installs

`13-implementation-plan.md` Phase D.0 task #2 (line 197) still says: "Add a migration file (`3.sqm` — verify the current migration version)." This is the SAME broken migration plan from Review 1 C3.

**Verified:** The project has ZERO `.sqm` files (per Review 1's grep). SQLDelight 2.x expects migrations to start at `1.sqm` and be sequential. A `3.sqm` with no `1.sqm` or `2.sqm` will be silently ignored (or worse — the schema will jump from v1 to v4 with no migration path, crashing existing dev installs at startup).

**Required fix:** Either (a) edit the `.sq` files directly (v1 → new v1; dev installs wipe app data — `13-implementation-plan.md` should say this explicitly), OR (b) add a `1.sqm` (not `3.sqm`) that DROPs + CREATEs. Update `11-db-schema.md` §3.3 + `13-implementation-plan.md` Phase D.0 task #2.

### 4.2 NEW CRITICAL — `13-implementation-plan.md` is internally inconsistent about the canonical state type

- Line 16 (status table): `core/download/.../DownloadState.kt | ⚠️ STUB — sealed interface with Queued / Downloading(progress) / Paused / Completed / Failed(msg)`.
- Line 201 (D.0 task #6): "Delete the stub `DownloadManager.kt` + `DownloadState.kt`".
- Line 216 (D.1 files-to-create): `| DownloadStatus.kt | DownloadStatus.kt | Same enum + isTerminal / isActive. |`.

So the plan says: delete `DownloadState.kt` (sealed interface, PascalCase variants) → create `DownloadStatus.kt` (enum, UPPERCASE constants). But this contradicts `16-quality-of-life.md` §1.3 line 86 which proposes `sealed interface DownloadStatus` (with `data class RETRYING(...)` — a sealed-interface variant that can carry data, which an enum CANNOT).

**Required fix:** Pick ONE definition. The canonical intent (per OLD project + Review 3 M1 recommendation + `13-implementation-plan.md` line 216) is `enum class DownloadStatus`. But then `RETRYING` cannot carry `(attempt, maxAttempts, lastError)` — these must become separate fields on `DownloadTask` (e.g. `retryAttempt: Int = 0`, `retryMaxAttempts: Int = 3`, `lastError: String? = null`). Alternatively, adopt `sealed interface DownloadStatus` (per QoL §1.3) and update line 216 + every `status == DownloadStatus.ERROR` check to `status is DownloadStatus.ERROR`. The plan author must decide and propagate the decision.

### 4.3 NEW CRITICAL — §14 of `10-player-integration.md` won't compile + §11.3 of `05-downloaders.md` has unbounded recursion (and they're inconsistent with each other)

§14.1's catch block (line 369):
```kotlin
return downloadVideoToCache(fresh.url, fresh.headers, tempFile, taskId, onProgress)
```
— 5 args.

§11.3's `downloadVideoToCache` signature (line 616–620):
```kotlin
private suspend fun downloadVideoToCache(
    url: String, headers: String?, tempFile: File, taskId: Long,
    resolveContext: ResolveContext?,
    onProgress: (Long, Long) -> Unit,
): Long
```
— 6 args (requires `resolveContext`).

The §14 call site OMITS `resolveContext`. **Won't compile.**

Even if `resolveContext` were passed, the recursive `downloadVideoToCache` → `downloadNormal` → catch block → `downloadVideoToCache` loop has NO `reResolveAttempts` counter. **Unbounded recursion** (Review 2 C1 / Review 3 C1 — STILL not fixed in BOTH places).

§11.3's catch block (line 664) recurses on `downloadNormal` directly (not `downloadVideoToCache`). This is INCONSISTENT with §14.1 which recurses on `downloadVideoToCache`.

The two docs disagree on:
1. Which function to recurse on (`downloadVideoToCache` vs `downloadNormal`).
2. Whether to pass `resolveContext` (§14 omits it — won't compile; §11.3 passes the SAME `resolveContext` — unbounded).
3. Whether to enforce a cap (neither does).

**Required fix:**
- §14.1 + §11.3 must agree on the catch-block body.
- Add `reResolveAttempts: Int = 0` parameter to `downloadNormal` (and to `downloadVideoToCache` if recursion goes through it).
- Increment on each recursive call; fail with `DownloadException("Proxy URL died after $N re-resolve attempts")` when `reResolveAttempts >= MAX_RE_RESOLVE_ATTEMPTS` (= 1, since "1 initial + 1 re-resolve = 2 total attempts").
- Update the §14.5 end-to-end trace step 11 to reflect the counter.

### 4.4 NEW IMPORTANT — `ReResolver` constructor takes `AutoDownloadEngine` but never uses it

`10-player-integration.md` §14.3 line 420–424:
```kotlin
class ReResolver(
    private val videoResolver: VideoResolver,
    private val autoDownloadEngine: AutoDownloadEngine,  // ← unused
    private val preferences: DownloadPreferences,
)
```
The `reResolve` method (line 432–444) does a direct lookup — never calls `autoDownloadEngine.selectBestVideo` or any of its 5 steps. So the `AutoDownloadEngine` DI param is dead.

This is the SAME issue as Review 2 I3 — the description in §14.1 ("uses the SAME `AutoDownloadEngine`") contradicts §14.3's implementation. Either:
- (a) Remove `autoDownloadEngine` from the constructor + update §14.1 to say "does a DIRECT lookup by pinned (server, audio, quality) — does NOT re-run the `AutoDownloadEngine`", OR
- (b) Actually use the engine (but then it might pick a DIFFERENT (server, audio, quality) on re-resolve — defeats the purpose).

§14.3 is CORRECT (option a). §14.1 is WRONG. The DI graph in `12-di-wiring.md` §11.2 line 422 also passes `AutoDownloadEngine` to `ReResolver` — should be removed if (a).

### 4.5 NEW IMPORTANT — DI graph in `12-di-wiring.md` registers `DownloadScanner` with `ContentRepository` dep, but the plan doesn't list `ContentRepository` as a `:core:download` dependency

`12-di-wiring.md` §11.5 line 510: `DownloadScanner(Context, DownloadStorageProvider, DownloadStore, ContentRepository)`. But `13-implementation-plan.md` Phase D.0 (the dependency-setup phase) doesn't add `:core:content` as a `:core:download` dependency. Currently `:core:download` depends on `:core:common`, `:core:database`, `:core:preferences`, `:core:network` (per `06-notifications-foreground-service.md` Review 4 §0 line 27). `ContentRepository` lives in `:core:content`.

**Required fix:** Add to Phase D.0: "Add `:core:content` dependency to `:core:download` (for `ContentRepository`, `ContentRecord`, `ContentIdGenerator` — used by `DownloadScanner` + the `mainId`-keyed identity system)."

Actually, line 202 of the plan says: "Add `core/content` dependency to `:core:download` (for the `mainId`/`contentId`/`ContentRecord` types)." — so the dep IS added. But the `DownloadScanner` constructor spec in `12-di-wiring.md` §11.5 line 510 says `ContentRepository` is a dep — the `13-implementation-plan.md` Phase D.1 line 226 only says "DownloadScanner.kt | (NEW, see `04-storage-paths.md` §7) | The scan-on-startup engine. Walks `video/`/`images/`/`text/`, reads each `data.json`, UPSERTs to DB by `mainId`." It doesn't mention `ContentRepository` as a dep. The implementation team needs to know that the scanner calls `ContentRepository.upsertFromDataJson(...)` (or whatever the UPSERT method is).

### 4.6 NEW IMPORTANT — `13-implementation-plan.md` line 434 references "Phase D.14" which doesn't exist

> **Player integration (D.14):**

There is no Phase D.14 — there are only D.0 through D.8. The correct reference is D.6 (since player integration is part of Phase D.6 per the heading at line 404 + the §6 total estimate table at line 475). Typo.

### 4.7 NEW IMPORTANT — The `13-implementation-plan.md` does NOT list any of the 18 carry-over CRITICALs as action items

The plan's Phase D.0–D.8 task lists do NOT include:
- "Add `reResolveAttempts` counter to `HttpDownloader.downloadNormal`" (R2-C1 / R3-C1).
- "Update `resetDownloadingToQueued` SQL to also reset RETRYING" (R3-I2 / R4-C7).
- "Introduce `HttpException` class in `:core:download`" (R3-C5 / R4-C5).
- "Define `setRetryingStatus` + `setErrorStatus`" (R3-I7 / R4-C6).
- "Replace `runBlocking { Coil.execute(...) }` with Coil 3 async API on `Dispatchers.IO`" (R4-C2 + C3).
- "Add `KoinComponent` to `DownloadService`" (R4-I2).
- "Declare `notificationManager` field" (R4-I1).
- "Add `ACCESS_NETWORK_STATE` permission to `:core:download`'s manifest" (R4-C4).
- "Create `ic_pause.xml` + `ic_cancel.xml` drawables" (R4-I5).
- "Reconcile `onNetworkChanged` between 02-queue-management.md + 16-quality-of-life.md" (R4-C8 + N1).
- "Add `recentRatios: ArrayDeque<Float>(5)` to queue's `launchDownload` + pass to `compute`" (R3-C2).
- "Refine `estimatedTotal` after each HLS segment" (R3-C3).
- "Truncate FileOutputStream on segment retry failure" (R3-C4).
- "Replace per-tick `scope.launch` with inline `_tasks.value =` + Channel-based DB writes" (R3-I3).
- "Add intermediate `onProgress` calls during validation/publish" (R3-I4).
- "Persist `prevTotal`/`prevEstimate`/`recentRatios` across pause/resume" (R3-I10).
- "Add `.nomedia` to content folders" (R1-I5).
- "Spec `ensureContentDir`'s same-title collision algorithm" (R1-I1).
- "Add FK columns to `ContentDataJson` OR document the relink strategy" (R1-C2).
- "Fix the `3.sqm` migration plan (use `1.sqm` or edit `.sq` directly)" (R1-C3 + §4.1 above).
- "Update `DatabaseDriverFactory` to pass migrations" (R1-C4).
- "Fix `getDownloadedMainIds` query (remove `DISTINCT`, use `MAX(...)` for bare columns)" (R1-C5).
- "Add `updateDownloadContentId` query for source-switch sync" (R1-I4).
- "Redefine `globalFallback` Step 5 to fire on non-perfect matches, not on empty candidates" (R2-C2).
- "Add `key()`/`defaultValue()`/`isSet()` to `Preference<T>` interface" (R2-I4).
- "Acknowledge that `[AUDIO, QUALITY, SERVER]` is a deliberate change, not 'preserves old behaviour'" (R2-I1).
- "Update §14.1 to say the ReResolver does NOT use the AutoDownloadEngine" (R2-I3 + §4.4 above).
- "Add `Retrying(attempt, maxAttempts)` to `EpisodeDownloadState`" (R4-M8).
- "Update bulk 'Retry all' to handle RETRYING" (R4-M9).
- "Mention 6-hour `dataSync` cap + `onTimeout` handler" (R4-I6).
- "Update workflow doc §7 to mention the NEW 5-step `AutoDownloadEngine`" (R4-I9).
- "Wrap `scheduleAutoClear`'s `autoClearScheduled.add` in `mutex.withLock`" (R4-I10).
- "Add `RETRYING` to `03-state-machine.md` diagram + transition table" (R3-I1).
- "Add `audio` to scan list or remove `audio/` folder mention" (R1-M2).
- "Use exception TYPE matching instead of string matching in `RetryPolicy.forException`" (R2-I6).

**Result:** An implementer following `13-implementation-plan.md` verbatim will NOT fix any of the 18 carry-over CRITICALs. The plan is a "happy path" plan that ignores the review findings.

### 4.8 NEW MINOR — `13-implementation-plan.md` line 25 incorrectly states `ACCESS_NETWORK_STATE` is "implicit"

> `app/src/main/AndroidManifest.xml` | ✅ has `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `ACCESS_NETWORK_STATE` (implicit), `VIBRATE` permissions declared

Review 4 C4 verified `ACCESS_NETWORK_STATE` is NOT in the new project's manifest. The plan's "(implicit)" parenthetical is misleading — there's no such thing as an "implicit" `<uses-permission>` declaration. Either it's declared or it isn't.

---

## 5. Future-proofing assessment

> The user said: "we need to make sure that everything is being handled properly and it is properly future-proof and is compatible with future updates, changes, other file structures and file formats".

| Future-proofing concern | Verdict | Where assessed | Notes |
|---|---|---|---|
| **Adding manga/novels/movies later (video/images/text format folders)** | **PASS** | `04-storage-paths.md` §3.1, §3.2, line 149 | The format-based folder split (`video/images/text`) is genuinely future-proof. Anime, movies, live-action series all → `video/`. Manga + art books → `images/`. Novels → `text/`. Adding a new content type later doesn't restructure the tree. ✅ |
| **Adding a 4th priority dimension later (e.g. subtitles language)** | **PASS** | `14-auto-download-engine.md` §6.4 line 963, §6.7 line 1011 | Explicitly addressed: "Add a 4th dimension (e.g. 'subtitles language'): add a `PreferenceDimension.SUBTITLES` enum value + a corresponding prefs list + the rank tuple naturally extends. No algorithm change." ✅ |
| **Changing the DB schema later (migrations)** | **FAIL** | `11-db-schema.md` §3.3 + `13-implementation-plan.md` Phase D.0 task #2 | The migration plan is built on a non-existent migration chain (`1.sqm` + `2.sqm` don't exist; proposed `3.sqm` won't work in SQLDelight 2.x). `DatabaseDriverFactory.create()` doesn't pass `migrations = ...` to `AndroidSqliteDriver`. ANY future schema change will crash existing installs at startup. The `data.json` system means the DB is technically a cache (rebuildable from disk), but the schema-VERSION bump still crashes the driver. **Carry-over Review 1 C3 + C4 — NOT FIXED.** |
| **Adding new download engines later (e.g. a DASH downloader)** | **PASS** | `05-downloaders.md` §11.1 (the `Downloader` interface) + line 427 | Explicitly addressed: "Modular architecture — the 3 engines share a common `Downloader` interface, each in its own file. Easy to add a 4th engine (e.g. DASH via ffmpeg) later." ✅ The `Downloader` interface (`suspend fun download(task, onProgress): DownloadTask`) is correctly minimal. |
| **Changing the storage location later (user moves the folder)** | **PASS** | `04-storage-paths.md` §7.1 line 680, §6.x (findContentDir fallback) | Explicitly addressed: "The new folder has ANI-KUTA contents in it (e.g. the user moved their library to a new SD card). The scan re-discovers everything; the DB is updated to reflect the new URIs. Old URIs from the previous folder are invalidated. No content is 'lost' — it's just re-recognized." The `data.json` per content folder is the source of truth. ✅ |
| **`mainId` as the durable key + `contentId` as source-switchable** | **PASS** | `04-storage-paths.md` §5.1, §6.x, line 756, 800; `11-db-schema.md` §3 line 126, 129; `13-implementation-plan.md` line 99, 127 | Consistently applied. The `mainId` is the durable UUID (survives source switches + AniList unlinking); the `contentId` is the source-switchable structured string (6-section per `ContentIdGenerator.kt`). The `episode_key` is `"$mainId|$episodeNumber"` — source-independent. ✅ The `data.json`-based scan-on-startup correctly re-keys by `mainId`. ✅ |
| **Source switch for an already-downloaded content** | **PARTIAL — CONCERN** | `04-storage-paths.md` §6.x (findContentDir fallback); `11-db-schema.md` §3.2 (no `updateDownloadContentId`); `10-player-integration.md` §13 (offline lookup by `mainId`) | The `mainId`-keyed identity + filesystem fallback correctly handle the playback case: a user switches source → the new source has a different `episodeUrl` but the same `mainId` → `isEpisodeDownloaded(mainId, episodeNumber)` still returns true → the local file plays. ✅ BUT: (a) the `download_queue.content_id` column is NOT updated on source switch (R1-I4 — NOT FIXED), so debug/log UI showing `content_id` would be misleading; (b) the `download_queue.video_url` is also stale after a source switch (R1-M7 — NOT FIXED), so a QUEUED task with a stale URL will fail-then-retry instead of pre-resolving. The reactive re-resolve-on-IOException path (Fix 2) handles this, but proactive re-resolve on app start would be better. **CONCERN — partial fix.** |
| **File-format compatibility (new video codecs, new subtitle formats)** | **PASS** | `04-storage-paths.md` §3.2 (file extensions), §5.1 (`contentFormat` field) | The `data.json` schema has a `contentFormat` field ("video"/"images"/"text"/"audio") + a `schemaVersion: Int = 1` field for forward compatibility. The `ContentDataJson` parser is documented as "Schema-versioned + `ignoreUnknownKeys = true`" (`13-implementation-plan.md` line 225). New file extensions are inferred by `extractExtension(task.request.videoUrl)` (`05-downloaders.md` §11.3 line 565). ✅ |
| **Backward-compatibility of `data.json` schema** | **PASS WITH CONCERN** | `04-storage-paths.md` §5.1 (`schemaVersion: Int = 1`) | The `schemaVersion` field is the right idea. BUT: there's no spec for what to do when an OLD `data.json` (schemaVersion=1) is read by a NEW app (schemaVersion=2). The `ignoreUnknownKeys = true` handles NEW fields in OLD files (forward compat), but OLD fields that are REMOVED or RENAMED in a NEW schema need explicit migration logic. The scan should branch on `schemaVersion` + call a migration function. **Not specified.** |
| **Adding a 4th notification channel (e.g. for "downloads paused on metered")** | **PASS** | `06-notifications-foreground-service.md` §10 (two channels) + `16-quality-of-life.md` §3.2 (the "Downloads paused" notification uses the existing `anikuta_downloads_progress` channel) | The channel design is extensible. ✅ |
| **Adding new pref types (e.g. `Map<sourceId, List<PreferenceDimension>>` for per-source dimension priority)** | **PASS** | `14-auto-download-engine.md` §6.4 line 965, `07-settings-preferences.md` §8.4 (`jsonListPref` + `jsonMapPref` helpers) | Explicitly future-proofed: "Per-source dimension priority: lift `dimensionPriority` from a single global pref to `Map<sourceId, List<PreferenceDimension>>` (same shape as `serverPreferences`). The engine doesn't care where the priority comes from." ✅ |
| **Adding a 4th download method (e.g. torrent)** | **PASS** | `05-downloaders.md` §11.1 (the `Downloader` interface), `13-implementation-plan.md` Phase D.1.5 (deferred Advanced) | The `Downloader` interface + the URL-routing `VideoTypeDetector` make adding a new method a matter of adding a new class + a new branch in `downloadVideoToCache`. ✅ |
| **Multi-user / multi-profile support** | **NOT ASSESSED** | (not in scope) | Not mentioned. Out of scope for now. |
| **Cross-device sync (e.g. SyncThing integration)** | **PARTIAL — CONCERN** | `04-storage-paths.md` §5 (`data.json` is durable), line 680 (re-scan on folder move) | The `data.json` per content folder is the right model for cross-device sync — the user can sync the SAF folder via SyncThing/Dropbox/etc., and the app re-scans on startup. ✅ BUT: the `data.json` `updatedAt` field is bumped on every download (Review 1 M9 — NOT FIXED), which would cause noisy sync conflicts. Should use a separate `lastEpisodeAddedAt` field. |

**Future-proofing overall verdict: 9 PASS + 1 FAIL + 2 PARTIAL/CONCERN + 1 NOT ASSESSED.** The architectural future-proofing is genuinely strong (the user's main concern is addressed at the design level), but the DB migration FAIL blocks ANY future schema change without crashing existing installs. This is the single most urgent future-proofing gap.

---

## 6. Proxy-churn fix integration assessment

### 6.1 Is the re-resolve recursion bounded?

**NO.** Verified in two places:

**Place 1 — `05-downloaders.md` §11.3 line 664:**
```kotlin
catch (e: IOException) {
    if (url.startsWith("http://localhost") && resolveContext != null && reResolver != null) {
        Logger.w(TAG) { "IOException on localhost URL — attempting re-resolve: ${e.message}" }
        val fresh = reResolver.reResolve(resolveContext)
        if (fresh != null) {
            store.updateResolveContext(taskId, fresh.url, resolveContext)
            return downloadNormal(fresh.url, fresh.headers, tempFile, taskId, resolveContext, onProgress)
        }
    }
    throw DownloadException("Video download failed: ${e.message ?: e.javaClass.simpleName}", e)
}
```
- The recursive `downloadNormal(fresh.url, ..., resolveContext, ...)` call passes the SAME `resolveContext` (not a fresh one — `fresh` only has `url` + `headers` per `FreshVideo`'s 2-field data class).
- The recursive call's catch block will fire AGAIN if the fresh URL also dies, calling `reResolver.reResolve(resolveContext)` AGAIN, getting ANOTHER fresh URL, and recursing AGAIN.
- **No `reResolveAttempts` counter.** UNBOUNDED RECURSION → `StackOverflowError` → worse than the original bug (which at least failed cleanly with ERROR status).

**Place 2 — `10-player-integration.md` §14.1 line 369:**
```kotlin
return downloadVideoToCache(fresh.url, fresh.headers, tempFile, taskId, onProgress)
```
- 5 args; `downloadVideoToCache` signature requires 6 (incl. `resolveContext`). **Won't compile.**
- Even if `resolveContext` were passed, the recursive `downloadVideoToCache` → `downloadNormal` → catch block → `downloadVideoToCache` loop has NO counter. UNBOUNDED.

**Carry-over Review 2 C1 + Review 3 C1 — STILL NOT FIXED (in BOTH places).**

### 6.2 Is the retry loop connected to the state machine?

**NO.** The state machine has a `RETRYING` state (per `16-quality-of-life.md` §1.3), but:

1. **`RETRYING` is NOT in `03-state-machine.md`'s diagram or transition table** (R3-I1 — NOT FIXED). An implementer reading the state-machine doc doesn't know RETRYING exists.

2. **`setRetryingStatus(task.id, attempt, policy.maxAttempts, e.message)` is called at `16-quality-of-life.md` §1.2 line 54 but never defined** (R3-I7 / R4-C6 — NOT FIXED). The function presumably does `mutex.withLock { mutateTask(taskId) { it.copy(status = DownloadStatus.RETRYING, ... ) } }` — but the body isn't shown.

3. **The retry loop in `16-quality-of-life.md` §1.2 line 32–61 (`launchDownload` with the `while (true) { attempt++; try { ... } catch (e) { ...; setRetryingStatus(...); delay(...); } }`) is INDEPENDENT of the re-resolve catch block in `05-downloaders.md` §11.3.** They live in different docs, different functions, different layers:
   - The retry loop wraps the entire `downloader.download(task, onProgress)` call.
   - The re-resolve catch block lives INSIDE `HttpDownloader.downloadNormal`, which is called BY `downloader.download`.
   - If the re-resolve catch block recursively calls `downloadNormal` (or `downloadVideoToCache`) and the recursive call ALSO fails, the `DownloadException` eventually thrown is caught by the OUTER retry loop — which then increments `attempt`, sets RETRYING, delays, and calls `downloader.download(task)` AGAIN. This restarts from scratch — calls `downloadVideoToCache` → `downloadNormal` → which can AGAIN re-resolve, AGAIN recurse unboundedly.

4. **So the user-visible RETRYING state (`"Retrying (2/3)..."`) and the proxy-churn re-resolve are NOT coherently connected.** A single proxy-churn event can cause:
   - Unbounded recursion inside `downloadNormal` (R2-C1 / R3-C1).
   - PLUS the outer retry loop's 3 attempts (per `RetryPolicy.forException`'s `IOException` branch).
   - PLUS the inner re-resolve's "1 re-resolve" cap (which is NOT enforced).
   - Net: a single proxy-churn event could trigger 3 × ∞ = ∞ download attempts before the task finally errors out. The RETRYING UI would show "Retrying (2/3)..." but the actual attempt count is much higher.

5. **The retry-loop code in `16-quality-of-life.md` §1.2 is ALSO inconsistent with `02-queue-management.md` §13.3's `launchDownload`** — the QoL version has the retry loop; the queue-management version does NOT (it has a single try/catch that flips to ERROR on any failure). The implementation team will not know which version is canonical.

### 6.3 Is there a max-retry cap that actually works?

**NO.** Three layers of caps are CLAIMED but NONE enforced:

1. **Inner cap (re-resolve):** §14.1 line 377 says "Cap re-resolve attempts at 2" — but no `reResolveAttempts` counter in the code.
2. **Middle cap (retry policy):** §1.2 line 25 says "Max attempts = 2 (1 initial + 1 re-resolve) for proxy-churn" — but this is the OUTER retry loop's cap, which doesn't know about the INNER re-resolve's recursion.
3. **Outer cap (retry policy):** §1.2 line 35 says `maxAttempts = 3` for retryable errors — also outer.

The caps don't compose. A single proxy-churn event could trigger:
- Inner re-resolve: ∞ attempts (unbounded).
- Outer retry: 3 attempts (each of which triggers a new inner re-resolve sequence).
- Total: ∞ × 3 = ∞.

### 6.4 End-to-end trace assessment

The §14.5 end-to-end fixed trace (lines 463–485) is correct CONCEPTUALLY but assumes the re-resolve is single-attempt (which the code doesn't enforce). Step 11 says "store.updateResolveContext + retry downloadVideoToCache(fresh.url, ...) — Resume from current bytes if possible" — but:
- "Resume from current bytes if possible" is hand-waved. The fresh URL is a NEW proxy on a different port. The CDN may not support Range requests for the new URL. The temp file's existing bytes may not be reusable.
- The trace stops at step 12 ("Download completes") — but if the fresh URL ALSO dies (which is the whole reason Review 2 C1 was filed), the trace doesn't say what happens. The implicit answer: unbounded recursion.

### 6.5 Verdict on proxy-churn fix integration

**FAIL.** The fix is architecturally sound (Fix 1 `directUrl` avoids the bug; Fix 2 re-resolve recovers from it) but the implementation is broken:
- Unbounded recursion in BOTH §14.1 and §11.3 (R2-C1 / R3-C1 — NOT FIXED).
- §14.1's recursive call won't compile (missing `resolveContext` arg — NEW §4.3).
- §14.1 description contradicts §14.3 implementation (R2-I3 — NOT FIXED).
- The retry loop and the re-resolve catch block are NOT coherently connected — they live in different docs with different caps that don't compose.
- `RETRYING` state exists in exactly one doc and isn't propagated to the state machine, the UI, the DB schema, or the implementation plan.

**Required fix (consolidated):**
1. Add `reResolveAttempts: Int = 0` parameter to `downloadNormal` (and to `downloadVideoToCache` if recursion goes through it).
2. Increment on each recursive call; fail with `DownloadException("Proxy URL died after $N re-resolve attempts")` when `reResolveAttempts >= 1` (= 2 total attempts).
3. Reconcile §14.1 and §11.3's catch-block bodies — pick ONE function to recurse on, pass `resolveContext`, enforce the cap.
4. Remove the unused `autoDownloadEngine: AutoDownloadEngine` param from `ReResolver`'s constructor (and from `12-di-wiring.md` §11.2 line 422's DI binding). Update §14.1 to say "does a DIRECT lookup by pinned (server, audio, quality) — does NOT re-run the `AutoDownloadEngine`".
5. Add `RETRYING` to `03-state-machine.md` §1, §2, §3, §9.
6. Define `setRetryingStatus(taskId, attempt, maxAttempts, lastError)` + `setErrorStatus(taskId, message)` in `02-queue-management.md` §13 (as private methods on `DownloadQueue`).
7. Update `11-db-schema.md` §3 line 154 comment to include RETRYING + update `resetDownloadingToQueued` (line 251) to `WHERE state IN ('DOWNLOADING', 'RETRYING')`.
8. Reconcile the retry-loop version in `16-quality-of-life.md` §1.2 with `02-queue-management.md` §13.3's `launchDownload` — pick ONE canonical version. The QoL version (with the `while (true) { attempt++; try/catch/delay }` loop) is more complete; update `02-queue-management.md` §13.3 to use it.
9. Document the cap composition: "the inner re-resolve caps at 1 attempt (2 total download attempts); the outer retry loop caps at 3 attempts; total = 3 outer × 2 inner = 6 download attempts maximum before ERROR."
10. Update §14.5 trace step 11 to show the `reResolveAttempts` counter + the cap enforcement.

---

## 7. Overall verdict

### **NEEDS MAJOR REWORK**

The design is fundamentally sound — the architectural decisions (SAF + `data.json` storage, `mainId`-keyed identity, 5-step `AutoDownloadEngine`, layered proxy-churn fix, foreground service, dual notification channels, QoL feature set) are correct and well-reasoned. The future-proofing (format folders, `Downloader` interface, dimension-priority abstraction, `data.json` schema versioning) is genuinely strong.

**BUT** the plan docs have 18 carry-over CRITICAL issues from Reviews 1–4, NONE of which have been fixed. The implementation plan (`13-implementation-plan.md`) does NOT list any of them as action items. An implementer following the plan verbatim will ship:
- A non-compiling build (Coil 2 on Coil 3, `HttpException` unresolved, `notificationManager` undefined, `KoinComponent` missing, `downloadVideoToCache` arity mismatch).
- A `StackOverflowError` (unbounded re-resolve recursion).
- A `ForegroundServiceDidNotStartInTimeException` crash on Android 12+.
- A `SecurityException` crash on `registerNetworkCallback` (missing `ACCESS_NETWORK_STATE`).
- Corrupt HLS output on flaky CDNs (no truncate-on-retry).
- Tasks stuck in RETRYING forever after a crash mid-retry (SQL doesn't reset RETRYING).
- The user's "progress bar jumps to 100%" complaint NOT actually fixed (no intermediate `onProgress` calls during publish).
- A `NoBeanDefFoundException` for `DownloadStorageProvider` if the migration binding is ever resolved (which it shouldn't be — but the OLD project's binding is still documented as a known issue).
- A broken `globalFallback` UX (ASK shows empty picker, DO_NOT_DOWNLOAD only fires when zero candidates).
- A broken migration plan (`3.sqm` with no `1.sqm` or `2.sqm`).
- A regression in the `Preference<T>` interface (3 methods vs 7).
- A false claim about `dimensionPriority` default preserving old behavior.
- A false claim about `resetDownloadingToQueued` resetting RETRYING.
- An inconsistent state-machine (RETRYING exists in 1 doc, ERROR/Failed/CANCELLED coexist across 3 definitions).
- An inconsistent `onNetworkChanged` (2 different definitions, one of which deadlocks).
- An inconsistent re-resolve catch block (§14 vs §11.3 disagree on function + args + cap).

The plan cannot be implemented as-written. The 18 carry-over CRITICALs + the 3 NEW CRITICALs found in this round (§4.1, §4.2, §4.3) must be addressed in a rewrite pass before Phase D.0 starts.

**Recommended path forward:**
1. The plan author performs a consolidation pass:
   - Update `13-implementation-plan.md` Phase D.0–D.8 to include EVERY item in the consolidated MUST-FIX list (§8 below) as an explicit task.
   - Update each affected plan doc (`03-state-machine.md`, `02-queue-management.md`, `04-storage-paths.md`, `05-downloaders.md`, `06-notifications-foreground-service.md`, `07-settings-preferences.md`, `08-downloads-page-ui.md`, `09-details-page-download-ui.md`, `10-player-integration.md`, `11-db-schema.md`, `12-di-wiring.md`, `14-auto-download-engine.md`, `16-quality-of-life.md`) to fix the carry-over CRITICALs + this round's NEW CRITICALs.
2. Then a Round 6 review (or a re-review of just the changed sections) verifies the fixes.
3. Only then does Phase D.0 start.

---

## 8. Consolidated "MUST FIX BEFORE IMPLEMENTATION" list — the single source of truth

> Every CRITICAL issue from all 5 review rounds. Items marked **[NEW-R5]** are new in this round. Items marked **[CARRY-RN]** are carry-overs from Review N. Items marked **[REGRESSION]** were FIXED in a prior round but regressed (none in this case — all carry-overs are unfixed).

### A. Migration / DB schema (blocks Phase D.0)

| # | Issue | Source | Fix |
|---|---|---|---|
| **M1** | Migration plan claims `1.sqm` + `2.sqm` exist — they don't. Proposed `3.sqm` will fail. | R1-C3 + [NEW-R5] §4.1 | Either (a) edit the `.sq` files directly + document "dev installs must wipe app data", OR (b) add a `1.sqm` (not `3.sqm`) that DROPs + CREATEs. Update `11-db-schema.md` §3.3 + `13-implementation-plan.md` Phase D.0 task #2. |
| **M2** | `DatabaseDriverFactory.create()` doesn't pass `migrations = ...` to `AndroidSqliteDriver`. | R1-C4 | If option (a) above: call out "dev installs must wipe app data". If option (b): update `DatabaseDriverFactory` to pass `migrations = arrayOf(MyMigration)`. Add a §3.4 to `11-db-schema.md` showing the `DatabaseDriverFactory` change. |
| **M3** | `getDownloadedMainIds` query has `DISTINCT` + `GROUP BY` redundancy + non-deterministic bare columns. | R1-C5 | Rewrite as `SELECT main_id, MAX(content_title) AS content_title, MAX(content_format) AS content_format, MAX(cover_url) AS cover_url, MAX(cover_color) AS cover_color FROM downloaded_episode GROUP BY main_id ORDER BY MAX(downloaded_at) DESC;` |
| **M4** | `data.json` example `"contentId": "anilist:101522"` is wrong (real format is 6-section per `ContentIdGenerator.kt`). | R1-C1 | Fix the example in `04-storage-paths.md` §5.2 to a real 6-section value + add a comment that the value MUST be produced by `ContentIdGenerator.generate(...)`. |
| **M5** | `data.json` doesn't store `dataSourceId/systemId/extensionRepoId/extensionId/displaySource`. Scan's `upsertFromDataJson` can't restore `content` table FK columns. | R1-C2 | Either (a) add the FK columns to `ContentDataJson`, OR (b) document the reverse-lookup strategy (scan → look up extension by `sourceId` + `animeUrl` → restore FKs), OR (c) explicitly downgrade UX ("user must re-link source after reinstall"). Pick one + document in `04-storage-paths.md` §5.1 + §7.1. |
| **M6** | `resetDownloadingToQueued` SQL only resets `WHERE state = 'DOWNLOADING'` — does NOT reset RETRYING. | R3-I2 / R4-C7 | Update SQL to `WHERE state IN ('DOWNLOADING', 'RETRYING')` in `11-db-schema.md` §3 line 251. Update `13-implementation-plan.md` Phase D.1 sub-task #3 + Phase D.8 to mention RETRYING. |
| **M7** | Stale `content_id` in `download_queue` after source switch. | R1-I4 | Either (a) drop `content_id` from `download_queue` (denormalized), OR (b) add `updateDownloadContentId(mainId, newContentId)` query + call from `ContentRepository.updateContentSources`. |
| **M8** | State column comment in `11-db-schema.md` §3 line 154 lists 6 states, no RETRYING. | [NEW-R5] §4.2 | Update comment to include `"RETRYING"`. |

### B. State machine + RETRYING propagation (blocks Phase D.1/D.2/D.7)

| # | Issue | Source | Fix |
|---|---|---|---|
| **M9** | `RETRYING` state is NOT in `03-state-machine.md` §1 enum, §2 diagram, §3 transition table. | R3-I1 | Add `RETRYING` to all three. Add transitions: DOWNLOADING→RETRYING, RETRYING→DOWNLOADING (retry starts), RETRYING→ERROR (max attempts exceeded), RETRYING→PAUSED (user pause), RETRYING→QUEUED (restart). |
| **M10** | `RETRYING` is NOT in `02-queue-management.md` §9 transition table; `pause`/`cancel`/`retry` allowed-state sets don't include it. | R3-I1 + R3-M6 | Update `pause` to accept RETRYING (cancel the retry loop's delay + transition to PAUSED). Update `cancel`/`retry` similarly. |
| **M11** | `setRetryingStatus` + `setErrorStatus` are called but never defined. | R3-I7 / R4-C6 | Define both as private methods on `DownloadQueue` in `02-queue-management.md` §13: `private suspend fun setRetryingStatus(taskId, attempt, maxAttempts, lastError) = mutex.withLock { mutateTask(taskId) { it.copy(status = DownloadStatus.RETRYING, ... ) }; store.updateState(taskId, "RETRYING", ...) }`. |
| **M12** | Canonical state type inconsistency: `enum class DownloadStatus` (OLD + 13's line 216) vs `sealed interface DownloadState` (NEW stub + 03 §9) vs `sealed interface DownloadStatus` (QoL §1.3). | R3-M1 + [NEW-R5] §4.2 | Pick ONE. Recommend `enum class DownloadStatus` (matches OLD + 13's intent). Then `RETRYING` cannot carry `(attempt, maxAttempts, lastError)` — add these as fields on `DownloadTask` (`retryAttempt: Int = 0`, `retryMaxAttempts: Int = 3`, `lastError: String? = null`). Update `13-implementation-plan.md` line 16 + line 216 to be consistent. Update `16-quality-of-life.md` §1.3 to use the enum. |
| **M13** | `09-details-page-download-ui.md` 7-variant `EpisodeDownloadState` doesn't cover RETRYING. | R4-M8 | Add 8th variant `data class Retrying(val attempt: Int, val maxAttempts: Int, val lastError: String)`. Specify the rendering: spinner + `"Retrying (2/3)..."` pill + Cancel button. |
| **M14** | `08-downloads-page-ui.md`'s bulk "Retry all" iterates `queue.filter { it.status == ERROR }` — doesn't address RETRYING. | R4-M9 | Update to skip RETRYING tasks (already being retried by the engine). Optionally show a "Retrying (N)" count chip in the bulk action bar. |

### C. Proxy-churn fix (blocks Phase D.2)

| # | Issue | Source | Fix |
|---|---|---|---|
| **M15** | `HttpDownloader.downloadNormal` re-resolve catch block has UNBOUNDED RECURSION (no `reResolveAttempts` counter). | R2-C1 / R3-C1 + [NEW-R5] §4.3 | Add `reResolveAttempts: Int = 0` parameter to `downloadNormal`. In the catch block, only recurse if `reResolveAttempts < MAX_RE_RESOLVE_ATTEMPTS` (= 1). Pass `reResolveAttempts + 1` on the recursive call. Fail with `DownloadException("Proxy URL died after $N re-resolve attempts")` when the cap is exceeded. Update `05-downloaders.md` §11.3 + `10-player-integration.md` §14.1. |
| **M16** | §14.1's recursive call `downloadVideoToCache(fresh.url, fresh.headers, tempFile, taskId, onProgress)` is missing the `resolveContext` arg (won't compile). | [NEW-R5] §4.3 | Either pass `resolveContext`, OR align §14.1 with §11.3 (recurse on `downloadNormal` instead of `downloadVideoToCache`). Pick ONE function + propagate to both docs. |
| **M17** | §14.1 says "Re-resolve uses the SAME `AutoDownloadEngine`" but §14.3 doesn't. `ReResolver` constructor takes `autoDownloadEngine` but never uses it. | R2-I3 + [NEW-R5] §4.4 | Update §14.1 to say "does a DIRECT lookup by pinned (server, audio, quality) — does NOT re-run the `AutoDownloadEngine`". Remove `autoDownloadEngine` from `ReResolver`'s constructor in §14.3 + from `12-di-wiring.md` §11.2 line 422's DI binding. |
| **M18** | "Cap re-resolve attempts at 2" wording is confusing + not enforced. | R2-M6 + R2-C1 | Clarify: "Cap at 1 re-resolve attempt (2 total download attempts: 1 initial + 1 re-resolve)." Enforce via M15. |
| **M19** | Retry loop in `16-quality-of-life.md` §1.2 is INDEPENDENT of the re-resolve catch block in `05-downloaders.md` §11.3. Caps don't compose. | [NEW-R5] §6.2 | Document the cap composition explicitly: "inner re-resolve caps at 1 attempt; outer retry loop caps at 3 attempts; total = 3 outer × 2 inner = 6 download attempts maximum before ERROR." Reconcile `16-quality-of-life.md` §1.2's retry loop with `02-queue-management.md` §13.3's `launchDownload` — pick ONE canonical version (recommend the QoL version with the `while (true) { attempt++; try/catch/delay }` loop). |

### D. Foreground service + notifications (blocks Phase D.4)

| # | Issue | Source | Fix |
|---|---|---|---|
| **M20** | `DownloadService.queueCollector` may call `stopSelf()` without ever calling `startForeground()` → `ForegroundServiceDidNotStartInTimeException` on Android 12+. | R4-C1 | Copy the existing `ExtensionInstallService` pattern: call `startForeground(SUMMARY_ID, notification)` SYNCHRONOUSLY in `onStartCommand` (before returning `START_STICKY`). The `queueCollector` then only UPDATES the notification (via `notificationManager.notify`), doesn't call `startForeground`. Update `06-notifications-foreground-service.md` §13.7. |
| **M21** | `downloadCover` uses Coil 2 API (`Coil.imageLoader(context).execute(...)`) but the NEW project uses Coil 3. | R4-C2 | Rewrite `downloadCover` against Coil 3 (`coil3.ImageLoader`, `coil3.request.ImageRequest`, `coil3.imageLoader(context)`). Mirror the existing `ImageLoaderFactory.kt`. |
| **M22** | `runBlocking { Coil.execute(...) }` + `BitmapFactory.decodeStream` + SAF I/O on `Dispatchers.Main` → ANR. | R4-C3 + R4-I3 + R4-I4 | Move the thumbnail-load path to `Dispatchers.IO`. Use Coil 3's suspend `ImageLoader.execute(request)` API (no `runBlocking`). Only `startForeground` / `NotificationManager.notify` on `Dispatchers.Main` (via `withContext(Dispatchers.Main)`). |
| **M23** | `ACCESS_NETWORK_STATE` permission is MISSING from `:core:download`'s manifest. `registerNetworkCallback` will SecurityException-crash. | R4-C4 | Add `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />` to `:core:download`'s manifest (CREATE the manifest if it doesn't exist). Update `13-implementation-plan.md` line 25 to remove "(implicit)" + Phase D.4 task list to mention creating the manifest. |
| **M24** | `DownloadService` references `notificationManager` that is never declared. | R4-I1 | Either use `NotificationManagerCompat.from(this).notify(...)` or delegate to `notifier.postSummaryUpdate(notification)`. Update `06-notifications-foreground-service.md` §13.7 line 613. |
| **M25** | `DownloadService` uses Koin `inject<>()` delegate but doesn't implement `KoinComponent`. | R4-I2 | `class DownloadService : Service(), KoinComponent { ... }` + import `org.koin.core.component.inject`. Update §13.7 line 597. |
| **M26** | `R.drawable.ic_pause` + `R.drawable.ic_cancel` are referenced but not declared. | R4-I5 | Add to Phase D.4 task list: "Create `ic_pause.xml` + `ic_cancel.xml` vector drawables in `:core:download/src/main/res/drawable/`". OR use framework drawables. |
| **M27** | Android 14+ `dataSync` foreground service has a 6-hour daily runtime cap — not mentioned. | R4-I6 | Add a §13.13 to `06-notifications-foreground-service.md`: document the 6-hour cap, specify an `onTimeout(startId, foregroundServiceType)` handler (API 35+) that gracefully pauses the queue + posts a "Downloads paused (time limit reached)" notification. Note the cap is per-app-per-day (shared with `ExtensionInstallService`). |
| **M28** | `DownloadService` has no `onTaskRemoved` override (OEMs may kill on swipe-from-recents). | R4-M11 | Add an `onTaskRemoved` override that re-launches the service via `startForegroundService` (for aggressive OEMs like Xiaomi/Huawei). Document in §13.13. |
| **M29** | `pauseAllIntent()` / `cancelAllIntent()` use hardcoded request codes 1 and 2 (collision risk). | R4-M10 | Use a unique prefix like `1001` + `1002`, or `hashCode()` of the action string. |
| **M30** | Lock-screen visibility not configured (`setVisibility(VISIBILITY_PUBLIC)` missing). | R4 §5 | Add `.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)` to the summary notification builder so the Pause/Cancel actions are visible on the lock screen. |

### E. Queue management + progress tracking (blocks Phase D.3)

| # | Issue | Source | Fix |
|---|---|---|---|
| **M31** | `recentRatios` parameter of `DynamicProgressTracker.compute` is NOT threaded through by the queue's `launchDownload`. | R3-C2 | Update `02-queue-management.md` §13.3's `launchDownload` to maintain `val recentRatios = ArrayDeque<Float>(5)` per-task in closure vars. On each tick: compute the current ratio, add to deque (evict oldest if size > 5), pass to `compute(...)`. |
| **M32** | HLS `estimatedTotal` is computed ONCE + never refined. | R3-C3 | Update `05-downloaders.md` §11.4 to refine `estimatedTotal` after each segment: `estimatedTotal = (totalDownloadedBytes / segmentsDownloadedSoFar) * segments.size`. The doc's claim at line 771 ("converges to the real total") must become TRUE. |
| **M33** | HLS per-segment retry writes to the SAME FileOutputStream → corrupt output on partial-then-retry. | R3-C4 | In `downloadSegmentWithRetry`, capture `val posBefore = (out as FileOutputStream).channel.position()` before each attempt; on failure, truncate back: `(out as FileOutputStream).channel.truncate(posBefore)`. OR: download each segment to a `ByteArrayOutputStream` first, write to `out` only on success. |
| **M34** | Per-tick `scope.launch { mutex.withLock { … } }` is a severe performance + correctness flaw. | R3-I3 | Either (a) update `_tasks.value` inline (no launch, no mutex — `MutableStateFlow.value = …` is atomic), and write to the DB via a Channel<ProgressUpdate> consumed by a single coroutine, OR (b) throttle the onProgress callback itself to once per 100ms inside the downloader. |
| **M35** | The 95% cap doesn't actually smooth the final jump — no `onProgress` calls during validation/publish. | R3-I4 | The downloader must call `onProgress(downloaded, total, 96/97/98/99)` at meaningful intermediate points (after validation, after subtitles, after metadata, after publish). OR: return `progress = 99` from the downloader, let the queue bump to 100 only after `onTaskCompleted` returns. |
| **M36** | `DynamicProgressTracker.complete()` is dead code. | R3-I5 | Either remove it, or wire it up in the queue's COMPLETED mutation path. |
| **M37** | `HttpDownloader.download`'s `finally { tempCache.cleanupTask(task.id) }` deletes Advanced downloader's resume metadata. | R3-I6 | The HttpDownloader's finally must distinguish CancellationException (preserve resume metadata for Advanced) from completion/error (delete everything). OR: the queue's `pause` should call a method that preserves resume metadata, and the cleanup should happen only on `cancel`/completion/error. |
| **M38** | Pause/resume resets `prevTotal` + `prevEstimate` → bar jumps backward on resume. | R3-I10 | Persist `prevTotal` + `prevEstimate` + `recentRatios` in the DB row (or `resume.json` for Advanced), restore on resume. |
| **M39** | `probeSegmentSize` uses HEAD — many anti-scraping CDNs reject HEAD or return wrong Content-Length. | R3-I11 | Use a 1-byte Range GET (like the Advanced downloader's `probeServer`) instead of HEAD. OR: download the first segment, get its size from the response, multiply by segment count, then re-download the first segment for the actual concat (wasteful but reliable). |
| **M40** | The "sanity check" if-branch in NEW DynamicProgressTracker is a no-op. | R3-I12 | Restore the OLD logic — compute `effectiveReportedTotal = -1L` in the if-branch and `= reportedTotal` in the else-branch, then use it in the `if (effectiveReportedTotal >= MIN_VALID_TOTAL_BYTES)` check below. |
| **M41** | `mutateTask` doesn't acquire the mutex itself — API is fragile. | R3-I15 | Make `mutateTask` a `suspend fun` that acquires the mutex internally. OR: rename to `mutateTaskLocked` + document that the caller MUST hold the mutex. |
| **M42** | `onNetworkChanged` is defined differently in `02-queue-management.md` §13.3 (no mutex) vs `16-quality-of-life.md` §2.2 (mutex-wrapped, calls `pause` inside lock). | R4-C8 + R4-M5 + [NEW-R5] N1 | Reconcile the two docs — pick ONE definition. Extract a `pauseInternal(taskId)` that ASSUMES the mutex is held (no `mutex.withLock` inside). Both `pause` (public) and `onNetworkChanged` (mutex-holding caller) use the right variant. |
| **M43** | `scheduleAutoClear`'s `autoClearScheduled.add(taskId)` is OUTSIDE `mutex.withLock` — race on the Set. | R4-I10 | Either wrap `scheduleAutoClear`'s `in`/`add` in `mutex.withLock`, or use `java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()` (thread-safe Set). |

### F. Auto-download engine + settings (blocks Phase D.2/D.5)

| # | Issue | Source | Fix |
|---|---|---|---|
| **M44** | `globalFallback = ASK / DO_NOT_DOWNLOAD` only fires when `sortedCandidates.isEmpty()` — useless UX. | R2-C2 | Redefine Step 5 in `14-auto-download-engine.md` §6.2.5 to fire based on the picked candidate's match quality (perfect match vs. best-effort), not on `sortedCandidates.isEmpty()`. See Review 2 C2's fix code. |
| **M45** | `dimensionPriority` default `[AUDIO, QUALITY, SERVER]` does NOT preserve old behaviour (claim is FALSE). | R2-I1 | Update `14-auto-download-engine.md` line 740 + line 1007 + `13-implementation-plan.md` line 518 to acknowledge the deliberate change: "Default `[AUDIO, QUALITY, SERVER]` — a deliberate choice reflecting typical user intent; the OLD project's behaviour was inconsistent + is not preserved." |
| **M46** | `Preference<T>` interface is a regression (3 methods vs 7). | R2-I4 | Add `key(): String`, `defaultValue(): T`, `isSet(): Boolean` to the proposed `Preference<T>` interface in `07-settings-preferences.md` §8.4. Optionally add `stateIn(scope): StateFlow<T>`. |
| **M47** | `onStart { emit(get()) }` in `changes()` Flow is redundant with `collectAsState(initial = ...)`. | R2-M4 | Either remove `onStart` (rely on `collectAsState(initial = ...)`) or remove the `initial` parameter (rely on `onStart`). Match the OLD project's pattern (no `onStart`). |
| **M48** | `RetryPolicy.forException` uses fragile string matching on exception messages. | R2-I6 | Use exception TYPE checking: `e is ConnectException || e is SocketException -> Policy(true, 2, { 0 })` (proxy-churn); `e is IOException -> Policy(true, 3, { ... })`. OR wrap the IOException in a custom `ProxyChurnException` at the catch site. |
| **M49** | `RetryPolicy.forException` uses `e is HttpException` — no `HttpException` class in `:core:download`. | R3-C5 / R4-C5 | Introduce `class HttpException(val code: Int, message: String) : DownloadException(message)` in `:core:download`. Throw it in `downloadNormal`, `HlsDownloader.fetchText`, `downloadSegment`, `probeServer`. Update `RetryPolicy` to match on `HttpException`. |
| **M50** | `RetryPolicy.forException`'s `CancellationException` branch is unreachable (catch site re-throws before reaching `forException`). | R4-I12 | Remove the dead branch for clarity. |
| **M51** | `e is DownloadException && e.cause is IOException` doesn't catch HTTP errors (which set NO cause). | R4-I7 | Fixed by M49 (HttpException is a DownloadException subclass with a `code` field — RetryPolicy matches on type, not on `cause`). |
| **M52** | Workflow doc traces the OLD 3-step `selectBestVideo` without mentioning the NEW 5-step `AutoDownloadEngine`. | R4-I9 | Add a §7.5 to `01-workflow-click-to-queue.md` noting: "The NEW project replaces `DownloadOrchestrator.selectBestVideo` with `AutoDownloadEngine.selectBestVideo` per `14-auto-download-engine.md` §6. The 5-step pipeline preserves the same API contract (`Selection.Selected`/`Selection.NoMatch`) so the rest of the trace (buildRequest → manager.enqueueDownload) is unchanged." |

### G. Storage (blocks Phase D.1)

| # | Issue | Source | Fix |
|---|---|---|---|
| **M53** | Same-title collision algorithm is unimplemented. `ensureContentDir` is called but not specified. | R1-I1 | Spec `ensureContentDir` in `04-storage-paths.md` §4.1: (1) check if folder exists, (2) if yes, read its `data.json` and check `mainId`, (3) if `mainId` matches → reuse, (4) if `mainId` differs → append ` (2)`, ` (3)`, etc. until a free slot is found. |
| **M54** | `.nomedia` file not created in content folders. Downloaded `.mp4` files will appear in gallery apps. | R1-I5 | Add `contentDir.createFile("application/octet-stream", ".nomedia")` to `publishToUserFolder` in `04-storage-paths.md` §6.3. |
| **M55** | 999-open-files limit + `DocumentFile.findFile()` O(N) — scan performance on large libraries will be poor. | R1-I6 | (a) Ensure every SAF stream is wrapped in `use { }`. (b) In the scan, call `contentDir.listFiles()` ONCE per folder, build a `Map<String, DocumentFile>`, then look up by name. (c) Add a note about the open-files limit. |
| **M56** | Fractional episode format `%.1f` rounds `12.25` → `12.3`. | R1-I9 | Replace `%.1f` with a non-rounding formatter: split on `.`, pad the integer part to 5 digits, append `.<fraction>` if non-zero. |
| **M57** | `audio/` format folder mentioned in §3.2 but not in the scan's `listOf("video", "images", "text")`. | R1-M2 | Either add `"audio"` to the scan list (lines 613 + 744) or remove the `audio/` mention from §3.2 (line 149). |
| **M58** | `DocumentFile.lastModified()` is unreliable on many SAF providers — incremental scan optimization may never trigger. | R1-I8 | Fall back to "always scan" if `lastModified()` returns 0 or a sentinel. Document this. |
| **M59** | No size cap on the temp cache. A 4 GB movie download fills 4 GB of internal cache. | R1-M10 | `tryStartNext` should check `cacheDir.usableSpace` against `totalBytes` before starting. |
| **M60** | Stale `video_url` in `download_queue` after source switch — re-resolve-on-IOException is reactive, not proactive. | R1-M7 | Add a note in `11-db-schema.md` §3.2: "on app start, any QUEUED task whose `content_id` differs from `content.content_id` (for the same `main_id`) should be proactively re-resolved via `ResolveContext`." |

### H. Implementation plan coherence (blocks EVERYTHING)

| # | Issue | Source | Fix |
|---|---|---|---|
| **M61** | `13-implementation-plan.md` does NOT list ANY of the 18 carry-over CRITICALs as action items. | [NEW-R5] §4.7 | Update Phase D.0–D.8 task lists to include EVERY item in this consolidated MUST-FIX list (M1–M60) as an explicit task with a doc cross-reference. |
| **M62** | `13-implementation-plan.md` line 434 references "Phase D.14" which doesn't exist. | [NEW-R5] §4.6 | Change "D.14" → "D.6". |
| **M63** | `13-implementation-plan.md` line 25 says `ACCESS_NETWORK_STATE` is "(implicit)" — incorrect. | [NEW-R5] §4.8 | Remove "(implicit)"; add a Phase D.4 task to declare the permission in `:core:download`'s manifest. (Same as M23.) |
| **M64** | `13-implementation-plan.md` line 314 says `ResolveContext` captures `(sourceId, episodeUrl, serverName, audioLabel, quality)` — but §14.2 also has `mainId` + `episodeKey`. | [NEW-R5] N/A | Update line 314 to include `mainId` + `episodeKey`. |
| **M65** | `13-implementation-plan.md` Phase D.1 line 226 (`DownloadScanner.kt`) doesn't mention `ContentRepository` as a constructor dep. | [NEW-R5] §4.5 | Update to: "DownloadScanner.kt | (NEW, see `04-storage-paths.md` §7) | The scan-on-startup engine. Walks `video/`/`images/`/`text/`, reads each `data.json`, UPSERTs to `content` + `downloaded_episode` tables by `mainId`. Constructor deps: Context, DownloadStorageProvider, DownloadStore, ContentRepository." |

### I. Cross-doc consistency cleanup (blocks understanding)

| # | Issue | Source | Fix |
|---|---|---|---|
| **M66** | State name inconsistency: `Failed` (PascalCase, sealed-interface variant) vs `ERROR` (UPPERCASE, enum constant). | R3-M1 + [NEW-R5] §2.1 | Resolved by M12 (pick `enum class DownloadStatus` with UPPERCASE constants). |
| **M67** | `onNetworkChanged` defined differently in 02-queue-management.md §13.3 vs 16-quality-of-life.md §2.2. | [NEW-R5] N1 | Resolved by M42. |
| **M68** | §14.1 description contradicts §14.3 implementation (ReResolver uses AutoDownloadEngine vs doesn't). | R2-I3 | Resolved by M17. |
| **M69** | §14.1's catch block calls `downloadVideoToCache` (5 args, missing `resolveContext`) — §11.3's calls `downloadNormal` (6 args). | [NEW-R5] §4.3 | Resolved by M16. |
| **M70** | `dimensionPriority` default "preserves old behaviour" claim repeated in 3 docs. | R2-I1 + [NEW-R5] N4 | Resolved by M45. |
| **M71** | `Preference<T>` interface regression + redundant `onStart`. | R2-I4 + R2-M4 | Resolved by M46 + M47. |
| **M72** | `resetDownloadingToQueued` claim in QoL §1.3 is FALSE (SQL only resets DOWNLOADING). | R3-I2 + [NEW-R5] N10 | Resolved by M6. |

---

## 9. Final recommendation

**Do NOT start Phase D.0.** Perform the consolidation pass first:
1. Fix M1–M72 (the consolidated MUST-FIX list above).
2. Re-review the changed sections (a Round 6 review, or a focused re-review of just the affected docs).
3. Then start Phase D.0.

The user's stated goal — "make sure that everything is being handled properly and it is properly future-proof and is compatible with future updates, changes, other file structures and file formats" — is **partially met** at the design level (the `data.json` + format-folder + `mainId`-keyed + `Downloader`-interface + dimension-priority-abstraction architecture is genuinely future-proof), but **NOT met** at the implementation-spec level (18 carry-over CRITICALs unfixed, the proxy-churn fix has unbounded recursion, the foreground service crashes on Android 12+, the migration plan is broken, the state machine is inconsistent across docs).

The 23–30 day estimate in `13-implementation-plan.md` §6 is **OPTIMISTIC** — it assumes a clean implementation with no rework. With the 72 MUST-FIX items, the realistic estimate is **30–40 days** (the additional 7–10 days are for the consolidation pass + the re-review + the inevitable mid-implementation discoveries).

---

## 10. Cross-references

- `worklog.md` — `DL-REVIEW-1` (line 569), `DL-REVIEW-2` (line 606), `DL-REVIEW-3` (line 647), `DL-REVIEW-4` (line 685) — the 4 prior review entries.
- `REVIEW-1-storage-db.md` — 5 CRITICALs (C1–C5) + 9 IMPORTANTs.
- `REVIEW-2-autodl.md` — 2 CRITICALs (C1–C2) + 6 IMPORTANTs.
- `REVIEW-3-queue-downloaders.md` — 5 CRITICALs (C1–C5) + 15 IMPORTANTs.
- `REVIEW-4-notifications-ui.md` — 8 CRITICALs (C1–C8) + 12 IMPORTANTs.
- `13-implementation-plan.md` — the master plan (needs the M61 update to incorporate this list).
- `10-player-integration.md` §14 — the proxy-churn fix (needs M15–M19).
- `12-di-wiring.md` §11 — the DI wiring (needs M17 + M65).
- All other plan docs (00–16) — for the cross-doc consistency fixes (M9–M14, M67–M72).
