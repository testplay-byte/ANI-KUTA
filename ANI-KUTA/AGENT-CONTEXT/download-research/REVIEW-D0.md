# REVIEW-D0 — Phase D.0 (Foundations) Implementation Review

> **Task ID:** DL-D0-REVIEW
> **Reviewer:** senior-code-review-agent
> **Scope:** Phase D.0 of the download system implementation (commit 5849e13 on branch `download-system-plan`).
> **Method:** Every claim verified against the actual source files (PreferenceStore.kt, downloadQueue.sq, downloadedEpisode.sq, HttpClientFactory.kt, build.gradle.kts, DownloadException.kt, HttpException.kt, AndroidManifest.xml, ic_pause.xml, ic_cancel.xml, AnikutaApp.kt). Cross-referenced against `13-implementation-plan.md` Phase D.0, `11-db-schema.md` §3.2, and `REVIEW-1-storage-db.md` C1–C5 + I1–I9.
> **Build:** CI passed per user claim (no local build artifacts to inspect — `app/build/outputs/apk/` is empty; CI ran `./gradlew assembleDebug` on the branch).
> **Verdict:** **APPROVED WITH CONCERNS** — D.0 is structurally correct, builds clean, and the plan-compliance scorecard is ~85%. However, the `downloaded_episode.sq` schema deviates from `11-db-schema.md` §3.2 in 5 missing columns + 1 type mismatch + 1 retained-redundant-index — these will force a schema-wipe-and-re-edit in D.1 unless reconciled before D.1 starts. The PreferenceStore Flow API has a tiny but real race window between `onStart`'s initial emit and the listener registration.

---

## §1. Per-checklist verdict

| # | Checklist item | Verdict | Notes |
|---|---|---|---|
| 1 | PreferenceStore Flow API correctness | **PASS WITH CONCERN** | `callbackFlow` + listener + `awaitClose` unregister is correct. **Concern:** `onStart { emit(getValue()) }` runs BEFORE the listener is registered inside `callbackFlow`'s producer — a write in that ~microsecond window is silently dropped (see §3.1). `trySendBlocking` is acceptable since the listener fires on the writing thread (low frequency). `distinctUntilChanged` correctly dedupes the onStart emit against the first listener emit. |
| 2 | SQLDelight schema correctness | **PASS WITH CONCERNS** | Named-parameter syntax (`:param_name`) is valid SQLDelight. `PRIMARY KEY (main_id, episode_key)` for `downloaded_episode` is correct. `AUTOINCREMENT id` for `download_queue` is correct. State comment lists all 7 states (M8 ✓). `resetDownloadingToQueued` resets BOTH DOWNLOADING + RETRYING (M6 ✓). `getDownloadedMainIds` uses `MAX()` not `DISTINCT+GROUP BY` (M3 ✓). `updateDownloadContentId` present (M7 ✓). **But:** 5 columns missing from `downloaded_episode` + `cover_color` type mismatch + `total_bytes` default mismatch + redundant index retained (see §3.2). |
| 3 | Download OkHttpClient correctness | **PASS** | `HttpClientFactory.DOWNLOAD = named("download")` qualifier is accessible from `:core:download` (which depends on `:core:network` — verified). `OkHttpClient.Builder().build()` creates a NEW `ConnectionPool` by default — verified (no `.connectionPool(...)` call). 60s read/write timeouts ✓. 30s connect ✓. No logging interceptor ✓. Bound in `AnikutaApp.kt:132` as `single<OkHttpClient>(HttpClientFactory.DOWNLOAD) { HttpClientFactory().createDownloadClient() }`. ✓ |
| 4 | Dependencies resolve | **PASS** | `libs.androidx.documentfile = { group = "androidx.documentfile", name = "documentfile", version = "1.0.1" }` ✓. `libs.kotlinx.serialization.json` ✓ (version 1.7.3). `libs.plugins.kotlin.serialization` ✓ (Kotlin 2.2.0 compiler plugin). `:core:content` exists (verified in `settings.gradle.kts:44`). |
| 5 | Exceptions | **PASS** | `HttpException(val code: Int, message: String, cause: Throwable? = null) : DownloadException(message, cause)` — matches plan §13 task #8 verbatim. `code` is `val` (public). `DownloadException` is `open class` (can be subclassed for future `HlsException`). No `:core:source-api` dependency added (M49 ✓). |
| 6 | Manifest | **PASS** | `:core:download/src/main/AndroidManifest.xml` declares `ACCESS_NETWORK_STATE` (M23 ✓) + `<service android:name="...DownloadService" android:exported="false" android:foregroundServiceType="dataSync" />` (M63 ✓). Manifest merger: `:app` already declares `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` + `POST_NOTIFICATIONS` (verified in `app/src/main/AndroidManifest.xml:11-13`); `:core:download` adds `ACCESS_NETWORK_STATE` + the new service. No conflicts — different `android:name` for the two services (`ExtensionInstallService` vs `DownloadService`). |
| 7 | Drawables | **PASS** | `ic_pause.xml` (24dp viewport, valid `<vector>` XML, standard Material pause path `M6,19h4V5H6v14zM14,5v14h4V5h-4z`) + `ic_cancel.xml` (24dp viewport, valid Material close-circle path). Both have `android:tint="?attr/colorControlNormal"` (themeable). 24dp is the standard notification-action icon size — renders correctly at 24dp display. |
| 8 | build.gradle.kts correctness | **PASS** | `alias(libs.plugins.kotlin.serialization)` is the correct Gradle 8+ syntax. `implementation` configuration is correct (these deps don't need to leak to consumers — only `:core:download` itself needs them). `:core:content` dep is `implementation` (not `api`) — correct, since `:core:download` consumers don't need `:core:content` types transitively (they get them via their own `:core:content` dep). |
| 9 | Backward compatibility | **PASS** | Verified via `grep -rn 'DownloadManager\|DownloadState'` across `ANI-KUTA/APP/ani-kuta/` — only references are: (a) DOCUMENTATION/*.md historical docs (not code), (b) SQL query name `updateDownloadState:` (unrelated), (c) comment in `DownloadModule.kt` mentioning the deletion, (d) KDoc reference in `DownloadException.kt` to future `DownloadManager`. No code imports the deleted classes. ✓ |
| 10 | Plan compliance | **PASS WITH CONCERNS** | 9 of 11 D.0 tasks fully compliant (D.0.1, D.0.4, D.0.5, D.0.6, D.0.7, D.0.8, D.0.9, D.0.10, D.0.11, D.0.12). D.0.2 (`downloadQueue.sq`) is largely compliant but deviates in 4 sub-items. D.0.3 (`downloadedEpisode.sq`) deviates in 6 sub-items (5 missing columns + 1 type mismatch). D.0 task #1 called for a unit test of the Flow API — **NO test was added** (the project has zero `src/test/` directories). |

---

## §2. Critical / Important / Minor issues

### CRITICAL issues (must fix before D.1)

| # | Issue | Where | Fix |
|---|---|---|---|
| **C1** | `downloaded_episode` is missing 5 columns that `11-db-schema.md` §3.2 mandates + that D.1 (Engine + Storage) will need. | `downloadedEpisode.sq` | Add the missing columns: `content_format TEXT NOT NULL DEFAULT 'video'` (for storage system to know which format folder), `content_type TEXT NOT NULL DEFAULT 'anime'`, `video_file_name TEXT NOT NULL` (scan-on-startup needs this to match files on disk), `verified_at INTEGER` (Q4 download verification), `content_folder_uri TEXT NOT NULL` (for cleanup, .nomedia management, re-publishing). Without these, D.1 must either add them (forcing another app-data wipe per M1) or work around them with fragile string parsing. |
| **C2** | `getDownloadedMainIds` is DUPLICATED in both `downloadQueue.sq:165` and `downloadedEpisode.sq:71`. Both query the `downloaded_episode` table. Both generate a method with the same name in their respective `Queries` classes (`DownloadQueueQueries.getDownloadedMainIds()` + `DownloadedEpisodeQueries.getDownloadedMainIds()`) — no compile conflict, but the duplicate is misleading + the one in `downloadQueue.sq` is misplaced (it queries a different table than the one its sibling queries operate on). | `downloadQueue.sq:163-170` | Delete `getDownloadedMainIds` from `downloadQueue.sq`. Keep only the one in `downloadedEpisode.sq` (it's the canonical location per plan §3.2). |

### IMPORTANT issues (should fix before D.1)

| # | Issue | Where | Fix |
|---|---|---|---|
| **I1** | `cover_color TEXT` in both .sq files, but `11-db-schema.md` §3.2 specifies `cover_color INTEGER` (ARGB color value). The `MAX(cover_color) AS cover_color` aggregate in `getDownloadedMainIds` behaves differently for TEXT (alphabetical max — meaningless for colors) vs INTEGER (numeric max — also somewhat meaningless, but at least deterministic). | `downloadedEpisode.sq:25`, `downloadQueue.sq:25` | Either (a) change column type to `INTEGER` to match plan (and store Color.toInt() / 0xAARRGGBB), OR (b) update the plan + document that `cover_color` is stored as a hex string "#AARRGGBB" (and replace `MAX()` with a deterministic aggregate). Without a decision, D.1 implementers will guess + the `MAX()` aggregate will produce wrong UI tinting. |
| **I2** | `total_bytes INTEGER NOT NULL DEFAULT 0` in `downloadQueue.sq:45`, but `11-db-schema.md` §3.2 specifies `DEFAULT -1` (sentinel for "unknown total bytes"). With `0`, the implementation can't distinguish "0 bytes total" from "unknown total" — the DynamicProgressTracker needs this signal to render an indeterminate progress bar vs a determinate one. | `downloadQueue.sq:45` | Change `DEFAULT 0` to `DEFAULT -1`. Update `updateDownloadProgress` to write `-1` (not `0`) when total is unknown. The D.3 progress tracking will need this. |
| **I3** | `idx_downloaded_episode_main` index is RETAINED, but REVIEW-1 I3 (consolidated as part of REVIEW-5) explicitly said REMOVE it because `main_id` is the leftmost column of the composite PRIMARY KEY — SQLite already uses the PK index for `main_id`-only queries. This is a regression of a Review-1 finding. | `downloadedEpisode.sq:45-46` | Delete `CREATE INDEX IF NOT EXISTS idx_downloaded_episode_main ON downloaded_episode(main_id);`. The PK `(main_id, episode_key)` already covers `WHERE main_id = ?` lookups. |
| **I4** | `PreferenceStore.preferenceFlow` has a race window: `onStart { emit(getValue()) }` runs BEFORE `callbackFlow`'s producer body (which registers the listener). A write during that ~microsecond gap is silently dropped — the collector sees the pre-write value until the NEXT write. | `PreferenceStore.kt:158-169` | Restructure so the listener is registered FIRST, then the current value is sent: `callbackFlow { val listener = ...; prefs.registerOnSharedPreferenceChangeListener(listener); trySendBlocking(getValue()); awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) } }.distinctUntilChanged()` (no `onStart`). The duplicate initial-vs-current emission is deduped by `distinctUntilChanged`. |
| **I5** | `getDownloadQueue` filters by `WHERE state IN ('QUEUED', 'DOWNLOADING', 'RETRYING', 'PAUSED')` — excludes `ERROR`. The plan §3.2 specifies `WHERE state IN ('QUEUED', 'DOWNLOADING', 'PAUSED', 'ERROR')` — includes `ERROR`, excludes `RETRYING`. The two filter sets are opposite on the ERROR-vs-RETRYING axis. The plan's choice means the queue view shows failed downloads (user can retry); the implementation's choice means failed downloads are hidden (only visible via a separate filter). | `downloadQueue.sq:133-136` | Either (a) match the plan exactly (`'QUEUED', 'DOWNLOADING', 'PAUSED', 'ERROR'`), OR (b) update the plan to match the implementation + document the UX rationale. Without a decision, D.6 (Downloads page UI) will be ambiguous about whether failed downloads appear in the main queue list. |

### MINOR issues (nice to fix; defer to D.1 polish)

| # | Issue | Where | Fix |
|---|---|---|---|
| **M1** | `subtitle_uris TEXT` in `downloadedEpisode.sq:32` is nullable with no default. Plan §3.2 specifies `subtitle_uris TEXT NOT NULL DEFAULT '[]'` for safe JSON parsing. | `downloadedEpisode.sq:32` | Add `NOT NULL DEFAULT '[]'` so consumers can `Json.decodeFromString(raw)` without null-checking. |
| **M2** | `idx_downloaded_episode_downloaded_at` index from plan §3.2 is missing. Doesn't affect current `ORDER BY MAX(content_title)` (which doesn't benefit from it), but if D.6 changes the ORDER BY to `MAX(downloaded_at) DESC` (the plan's original spec), the index would be needed. | `downloadedEpisode.sq` | Add `CREATE INDEX IF NOT EXISTS idx_downloaded_episode_downloaded_at ON downloaded_episode(downloaded_at);` to match plan §3.2. |
| **M3** | `getDownloadedMainIds ORDER BY MAX(content_title) ASC` (alphabetical) vs plan's `ORDER BY MAX(downloaded_at) DESC` (most-recently-downloaded first). The implementation's alphabetical choice is a defensible UX decision (matches library sort), but it's an undocumented deviation. | `downloadedEpisode.sq:76`, `downloadQueue.sq:170` | Either match the plan (recency sort) or update the plan to document the alphabetical choice. |
| **M4** | `updateDownloadContentId` in `downloadQueue.sq:128-131` lacks the `AND content_id != ?` guard from plan §3.2. Updates all rows for a `main_id` even if the `content_id` is already correct — idempotent but slightly wasteful. | `downloadQueue.sq:128-131` | Add `AND content_id != :content_id` to skip the no-op UPDATE (minor optimization). |
| **M5** | Missing queries that plan §3.2 specifies but D.0 didn't add (deferred to D.1, but worth tracking): `updateDownloadResolveContext` (for HttpDownloader re-resolve), `updateDownloadResult` (combined COMPLETED transition — actual has `updateDownloadVideoUri` which is less complete), `markEpisodeMissing` (for DownloadScanner orphan-cleanup), `getDownloadedVideoUri` + `getDownloadedSubtitleUris` (for player integration). | `downloadQueue.sq`, `downloadedEpisode.sq` | Add these in D.1 when their consumers are implemented. Track in D.1 task list. |
| **M6** | Plan D.0 task #1 explicitly called for a unit test ("Verify with a small unit test that a write in one place emits to a Flow collector in another"). No test was added. The project has zero `src/test/` directories. | N/A | Add a `core/preferences/src/test/java/.../PreferenceStoreFlowTest.kt` that: (1) creates a PreferenceStore, (2) collects `stringFlow("key", "default")`, (3) writes via `putString("key", "new")`, (4) asserts the collector received "default" then "new". Use `runTest` + `turbine` Flow test library. |
| **M7** | `Preference.kt:182` KDoc example uses `val concurrentDownloads by store.preference(...).changes.collectAsState(initial = 1)` — the `by` keyword is wrong here (`collectAsState` returns `State<T>`, not a delegate). Should be `val concurrentDownloads by store.preference(...).changes.collectAsStateWithLifecycle(initialValue = 1)` OR `val concurrentDownloads = store.preference(...).changes.collectAsState(initial = 1).value`. | `PreferenceStore.kt:182` | Fix the KDoc example. Not a code issue — purely documentation. |
| **M8** | `trySendBlocking` inside the listener can theoretically block the calling thread if the channel buffer fills. For `callbackFlow` the default capacity is `Channel.BUFFERED` (64), and pref writes are low-frequency, so this won't trigger in practice. But `trySend` (non-blocking, drops on overflow) would be safer. | `PreferenceStore.kt:162` | Optional: switch to `trySend(getValue())` and log a warning on failure. Not a blocker. |

---

## §3. Detailed findings

### §3.1 PreferenceStore Flow API — race condition analysis

The implementation:

```kotlin
private fun <T> preferenceFlow(key: String, getValue: () -> T): Flow<T> =
    callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changedKey ->
            if (changedKey == key) {
                trySendBlocking(getValue())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
        .onStart { emit(getValue()) }
        .distinctUntilChanged()
```

**Execution order when a collector collects:**
1. `distinctUntilChanged` calls `collect` on `onStart`
2. `onStart` runs its block → `emit(getValue())` — collector sees current value
3. After `onStart`'s block returns, `onStart` calls `collect` on `callbackFlow`
4. `callbackFlow` launches its producer coroutine: creates listener, registers it, awaits close
5. Subsequent writes fire the listener → `trySendBlocking` → collector sees new value

**Race window: steps 2 → 4.** A write during this window:
- Is NOT observed by the listener (not yet registered)
- The collector has the stale pre-write value from step 2
- The NEXT write (after step 4) will be observed, but the missed change is lost

**Consequence:** For UI collectors, this is invisible (microsecond window, and the next user-driven write self-corrects). For correctness-critical collectors (e.g. a download-engine setting that gates behavior), a missed emission could mean stale config for the lifetime of the collector.

**Recommended fix:**

```kotlin
private fun <T> preferenceFlow(key: String, getValue: () -> T): Flow<T> =
    callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                trySendBlocking(getValue())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySendBlocking(getValue())  // emit current AFTER registering — no missed writes
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
```

This way, the listener is registered FIRST. Any write that happens between registration and the initial `trySendBlocking` will be observed by the listener (sending the new value), then the initial `trySendBlocking` sends the (possibly now-stale) current value. `distinctUntilChanged` dedupes — the collector sees only the latest value.

### §3.2 SQLDelight schema — deviation matrix

Comparing `downloadedEpisode.sq` against `11-db-schema.md` §3.2 line-by-line:

| Plan §3.2 column | Actual implementation | Verdict |
|---|---|---|
| `main_id TEXT NOT NULL` | `main_id TEXT NOT NULL` | ✓ |
| `episode_key TEXT NOT NULL` | `episode_key TEXT NOT NULL` | ✓ |
| `content_folder_uri TEXT NOT NULL` | **MISSING** | ❌ C1 |
| `video_uri TEXT NOT NULL` | `video_uri TEXT` (nullable) | Minor |
| `subtitle_uris TEXT NOT NULL DEFAULT '[]'` | `subtitle_uris TEXT` (nullable, no default) | ❌ M1 |
| `content_title TEXT NOT NULL` | `content_title TEXT NOT NULL` | ✓ |
| `content_format TEXT NOT NULL DEFAULT 'video'` | **MISSING** | ❌ C1 |
| `content_type TEXT NOT NULL DEFAULT 'anime'` | **MISSING** | ❌ C1 |
| `cover_url TEXT` | `cover_url TEXT` | ✓ |
| `cover_color INTEGER` | `cover_color TEXT` | ❌ I1 |
| `episode_number REAL NOT NULL` | `episode_number REAL NOT NULL` | ✓ |
| `episode_name TEXT NOT NULL DEFAULT ''` | `episode_name TEXT NOT NULL` (no default) | Minor |
| `video_file_name TEXT NOT NULL` | **MISSING** | ❌ C1 |
| `quality TEXT` | `quality TEXT` | ✓ |
| `server TEXT` | `video_server TEXT` | Different name, same purpose — OK |
| `audio TEXT` | `video_audio TEXT` | Different name, same purpose — OK |
| `source_id INTEGER` | `source_id INTEGER` | ✓ |
| `file_size INTEGER NOT NULL` | `file_size INTEGER NOT NULL` | ✓ |
| `downloaded_at INTEGER NOT NULL` | `downloaded_at INTEGER NOT NULL` | ✓ |
| `verified_at INTEGER` | **MISSING** | ❌ C1 |
| `PRIMARY KEY (main_id, episode_key)` | `PRIMARY KEY (main_id, episode_key)` | ✓ |
| `idx_downloaded_episode_downloaded_at` | **MISSING** | M2 |
| (no `idx_downloaded_episode_main`) | `idx_downloaded_episode_main` RETAINED | ❌ I3 |
| `markEpisodeMissing` query | **MISSING** | M5 |
| `getDownloadedVideoUri` query | **MISSING** | M5 |
| `getDownloadedSubtitleUris` query | **MISSING** | M5 |

**Extra in actual vs plan:**
- `content_id TEXT NOT NULL` (plan says `downloaded_episode` doesn't have `content_id`; actual adds it for source-switch sync — defensible extension of M7)
- `file_path TEXT NOT NULL` (the plan uses `video_uri` only; actual adds `file_path` as "content:// URI to the published video file" — comments say "same as video_uri — kept for clarity" — redundant but harmless)
- `updateDownloadedContentId` query (extends M7 to the `downloaded_episode` table too — useful)
- `isContentDownloaded`, `getDownloadedEpisodeCount` queries (useful extras)

Comparing `downloadQueue.sq` against plan §3.2:

| Plan §3.2 column | Actual implementation | Verdict |
|---|---|---|
| `id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT` | ✓ | ✓ |
| `main_id TEXT NOT NULL` | ✓ | ✓ |
| `episode_key TEXT NOT NULL` | ✓ | ✓ |
| `content_id TEXT NOT NULL` | ✓ | ✓ |
| `content_title TEXT NOT NULL` | ✓ | ✓ |
| `content_format TEXT NOT NULL DEFAULT 'video'` | **MISSING** | ❌ (same as C1) |
| `content_type TEXT NOT NULL DEFAULT 'anime'` | **MISSING** | ❌ (same as C1) |
| `cover_url TEXT` | ✓ | ✓ |
| `cover_color INTEGER` | `cover_color TEXT` | ❌ I1 |
| `episode_number REAL NOT NULL` | ✓ | ✓ |
| `episode_name TEXT NOT NULL DEFAULT ''` | `episode_name TEXT NOT NULL` (no default) | Minor |
| `episode_url TEXT` | **MISSING** | Minor — needed by ReResolver (M17) for re-resolve |
| `source_id INTEGER` | ✓ | ✓ |
| `video_url TEXT NOT NULL` | ✓ | ✓ |
| `video_headers TEXT` | ✓ | ✓ |
| `subtitle_tracks TEXT` | ✓ | ✓ |
| `audio_tracks TEXT` | ✓ | ✓ |
| `video_server TEXT NOT NULL DEFAULT ''` | `video_server TEXT` (nullable) | Minor |
| `video_quality TEXT NOT NULL DEFAULT ''` | `video_quality TEXT` (nullable) | Minor |
| `video_audio TEXT NOT NULL DEFAULT ''` | `video_audio TEXT` (nullable) | Minor |
| `resolve_context TEXT` | ✓ | ✓ |
| `state TEXT NOT NULL` (with 7-state comment) | ✓ | ✓ M8 |
| `progress INTEGER NOT NULL DEFAULT 0` | ✓ | ✓ |
| `downloaded_bytes INTEGER NOT NULL DEFAULT 0` | ✓ | ✓ |
| `total_bytes INTEGER NOT NULL DEFAULT -1` | `total_bytes INTEGER NOT NULL DEFAULT 0` | ❌ I2 |
| `error_message TEXT` | ✓ | ✓ |
| `video_uri TEXT` | ✓ | ✓ |
| `subtitle_uris TEXT` | ✓ | ✓ |
| `queued_at INTEGER NOT NULL` | ✓ | ✓ |
| `started_at INTEGER` | ✓ | ✓ |
| `completed_at INTEGER` | ✓ | ✓ |
| `updated_at INTEGER NOT NULL` | ✓ | ✓ |
| `prev_total_bytes` / `prev_estimate_bytes` / `recent_ratios_json` | ✓ (M31/M38) | ✓ |
| `retry_attempt` / `retry_max_attempts` / `last_error` | ✓ (M9/M11) | ✓ |

**Missing columns in `download_queue`:**
- `content_format` — same gap as `downloaded_episode`
- `content_type` — same gap
- `episode_url` — the plan §3.2 has `episode_url TEXT` (the extension's episode URL for re-resolving). The actual relies on `resolve_context` JSON to carry this. If `ResolveContext.episodeUrl` is the source of truth, this is fine — but the plan calls for a dedicated column.

**Missing queries (deferred to D.1):**
- `updateDownloadResolveContext` — needed by HttpDownloader's re-resolve-on-IOException
- `updateDownloadResult` — combined COMPLETED transition (actual has `updateDownloadVideoUri` which is less complete; D.1 can use `updateDownloadState` + `updateDownloadVideoUri` in sequence)

### §3.3 Build verification

- CI ran `./gradlew assembleDebug` on branch `download-system-plan` per user claim. No local build artifacts to inspect (`app/build/outputs/apk/` is empty, no `:core:download/build/` dir).
- Static analysis of the code suggests the build will pass:
  - All Kotlin imports resolve (verified against actual file paths).
  - `libs.plugins.kotlin.serialization` is defined in `libs.versions.toml:111`.
  - `libs.androidx.documentfile`, `libs.kotlinx.serialization.json` defined.
  - `:core:content` exists in `settings.gradle.kts:44`.
  - `:core:download` already had `:core:network` + `:core:database` deps (pre-D.0); only `:core:content` was added.
  - Manifest merger: no conflicts (different `android:name` for the two services; permissions are unioned).
  - Vector drawables are valid XML (standard Material paths).
  - SQLDelight named-parameter syntax is valid (`.sq` files compile to query interfaces).

### §3.4 Backward compatibility

- `grep -rn 'DownloadManager\|DownloadState'` against `ANI-KUTA/APP/ani-kuta/` confirms NO code references the deleted stubs.
- Only references are: (a) historical DOCUMENTATION/*.md files (not code), (b) SQL query name `updateDownloadState:` (unrelated — query name, not class), (c) `DownloadModule.kt` comment mentioning the deletion, (d) `DownloadException.kt` KDoc reference to future `DownloadManager` (forward reference, not an import).
- The existing `downloadModule` import in `AnikutaApp.kt:16` continues to resolve because `DownloadModule.kt` still exists (now as an empty Koin module placeholder).

---

## §4. Plan-compliance scorecard

| D.0 task | Plan spec | Implementation | Compliance |
|---|---|---|---|
| D.0.1 — PreferenceStore Flow API | Reactive `Flow<T>` + `Preference<T>` + 4 serializers | `stringFlow`/`booleanFlow`/`intFlow`/`floatFlow`/`longFlow`/`stringSetFlow`/`stringListFlow` + `Preference<T : Any>` + `IntSerializer`/`BooleanSerializer`/`StringSerializer`/`StringListSerializer` | ✓ PASS (race-condition concern noted in I4) |
| D.0.2 — `downloadQueue.sq` rewrite | Re-key by mainId + episodeKey, 7-state enum, all new columns, M6/M3/M7/M8 | Re-keyed ✓, 7-state comment ✓, M6 ✓, M3 ✓, M7 ✓, M8 ✓. Missing `content_format`/`content_type`/`episode_url` columns. `total_bytes` default 0 not -1. `cover_color` TEXT not INTEGER. | ⚠️ PARTIAL |
| D.0.3 — `downloadedEpisode.sq` rewrite | Re-key by mainId + episodeKey, full content metadata, M7 sync query | Re-keyed ✓, M7 added `updateDownloadedContentId` ✓. Missing 5 columns (C1) + cover_color type mismatch (I1) + redundant index retained (I3). | ⚠️ PARTIAL |
| D.0.4 — Download OkHttpClient | 60s read/write, separate pool, no logging, DOWNLOAD qualifier, bound in appModule | All present ✓. `OkHttpClient.Builder().build()` creates a new pool by default ✓. Bound at `AnikutaApp.kt:132` ✓. | ✓ PASS |
| D.0.5 — documentfile dep | `implementation("androidx.documentfile:documentfile:1.0.1")` | `implementation(libs.androidx.documentfile)` with version 1.0.1 in catalog ✓. | ✓ PASS |
| D.0.6 — kotlinx-serialization-json | Add to `:core:download` for `ContentDataJson`/`DownloadTrack`/`ResumeMetadata` | `implementation(libs.kotlinx.serialization.json)` ✓. Plugin `alias(libs.plugins.kotlin.serialization)` ✓. | ✓ PASS |
| D.0.7 — `:core:content` dep | Add to `:core:download` for `mainId`/`contentId`/`ContentRecord` types | `implementation(project(":core:content"))` ✓. `ContentRecord` exposes the needed fields ✓. | ✓ PASS |
| D.0.8 — Delete stubs | Remove `DownloadManager.kt` + `DownloadState.kt`; `DownloadModule.kt` becomes empty placeholder | `git diff 8cb8177 5849e13` confirms `D DownloadManager.kt` + `D DownloadState.kt` + `M DownloadModule.kt` (now empty) ✓. | ✓ PASS |
| D.0.9 — `DownloadException` | Base open class for download errors | `open class DownloadException(message, cause) : Exception(message, cause)` ✓. | ✓ PASS |
| D.0.10 — `HttpException` (M49) | Local in `:core:download`, `val code: Int`, extends `DownloadException` | `class HttpException(val code: Int, message: String, cause: Throwable? = null) : DownloadException(message, cause)` ✓. No `:core:source-api` dep added ✓. | ✓ PASS |
| D.0.11 — Manifest (M23/M63) | `ACCESS_NETWORK_STATE` + `DownloadService` with `foregroundServiceType="dataSync"` | Both present ✓. Merger-friendly (no conflicts with `:app` manifest) ✓. | ✓ PASS |
| D.0.12 — Drawables (M26) | `ic_pause.xml` + `ic_cancel.xml` vector drawables for notification actions | Both present, valid XML, 24dp standard ✓. | ✓ PASS |

**Compliance scorecard: 9 of 11 tasks fully PASS; 2 tasks PARTIAL (D.0.2 + D.0.3).**

---

## §5. Overall verdict

**APPROVED WITH CONCERNS.**

The D.0 implementation is structurally sound, builds clean (per CI), and the architectural decisions (dual-OkHttp-client, Flow-based reactive prefs, separate `DownloadException`/`HttpException` hierarchy, manifest-per-module, vector drawables) are all correct and well-aligned with the plan.

The two PARTIAL tasks (D.0.2 + D.0.3) are the SQLDelight schema rewrites. They get the *intent* right (re-keyed by mainId+episodeKey, 7-state enum, MAX-based aggregates, RETRYING-inclusive reset) but deviate from `11-db-schema.md` §3.2 in several specific places that will cause friction in D.1:

1. **5 missing columns in `downloaded_episode`** (C1) — `content_format`, `content_type`, `video_file_name`, `verified_at`, `content_folder_uri`. The D.1 Engine + Storage layer needs ALL of these (storage system needs `content_format` to know which format folder; scanner needs `video_file_name` to match files on disk; Q4 verification needs `verified_at`; cleanup needs `content_folder_uri`).
2. **`cover_color` type mismatch** (I1) — TEXT vs INTEGER. The `MAX(cover_color)` aggregate behaves differently for each.
3. **`total_bytes` default mismatch** (I2) — 0 vs -1 sentinel. DynamicProgressTracker needs the -1 sentinel to render indeterminate progress.
4. **Retained redundant index** (I3) — `idx_downloaded_episode_main` was supposed to be REMOVED per REVIEW-1 I3 (regression).
5. **`PreferenceStore` race condition** (I4) — small window between `onStart` emit and listener registration where writes are missed.

**Recommended action BEFORE starting D.1:**

1. Fix C1 + C2 (add 5 missing columns + remove duplicate `getDownloadedMainIds`).
2. Fix I1 + I2 + I3 (cover_color type, total_bytes default, drop redundant index).
3. Fix I4 (restructure `preferenceFlow` to register-then-emit).
4. Decide I5 (ERROR vs RETRYING in `getDownloadQueue` filter) — document the UX choice.
5. Add the missing D.1-needed queries (M5): `updateDownloadResolveContext`, `updateDownloadResult`, `markEpisodeMissing`, `getDownloadedVideoUri`, `getDownloadedSubtitleUris`. Adding them now (before D.1 starts) avoids a mid-D.1 schema wipe.
6. Add a small unit test for `PreferenceStore.preferenceFlow` (M6) — the plan explicitly called for one.

After these fixes, D.1 can proceed without re-touching the schema. Without them, D.1 will accumulate workarounds + a likely schema-wipe-and-re-edit.

**Estimated fix time:** 1-2 hours (schema column additions + type changes + PreferenceStore restructure + small unit test). No code in D.1 depends on the D.0 output yet (D.1 starts fresh), so the fix window is cheap.

**Branch state:** `download-system-plan` @ 5849e13. Recommend a follow-up commit `DL-D0-FIX` addressing C1-C2 + I1-I5 before the D.1 implementation commit lands.

---

## §6. Cross-references

- Plan: `13-implementation-plan.md` Phase D.0 (lines starting "### Phase D.0 — Foundations")
- DB schema spec: `11-db-schema.md` §3.2 (the canonical schema)
- Review history: `REVIEW-1-storage-db.md` C1-C5 + I1-I9 (D.0 was supposed to fix all of these — verified C3/C4/C5 fixed, I2/I3 regressed or unaddressed in `downloadedEpisode.sq`).
- REVIEW-5 consolidated fixes: M1, M2, M3, M6, M7, M8, M9, M11, M12, M23, M26, M49, M63, M64 — all of the D.0-applicable M-items.
- Implementation commit: 5849e13 on `download-system-plan` branch.
- Plan-compliance scorecard: §4 above (9/11 PASS, 2/11 PARTIAL).
