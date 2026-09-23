# Round 80 — The notification-system rework: immediate audible "searching", live per-item progress, honest endings + the release-system playbook (D-566..D-569)

Date: 2026-09-23. Branch: `feature/round-57-cloudstream-downloads` (the mainline). Sandbox was RESET on arrival (the app clone + `/home/z/.secrets/*` gone) — re-cloned, the official-repo token restored from the user's handover; the dev PAT is LOST (push pending the user's re-provision).

## 0. The orders (verbatim intent)
1. "good work the things have been handled properly now properly document this release system and all so we properly handle the releases in the future."
2. The notification system: "currently we utilize have a system which properly detects and handles the notifications as needed. But apparently that system is not as well as optimized as it needs to be… sometimes it shows me that it is scanning for new episodes, but that apparently is not proper… the UI of it is not proper, and also it does not show the live progress of the episode's release which it is checking for currently. What it does instead is that it first of all checks, and after checking it then shows the notification… it should first of all play sound and notify me that it is searching for new episode releases… make the whole overall system much more robust… proper thorough testing… utilize subagents properly… well documented, well managed, well commented… modular, easy to maintain."

## 1. The audit (Explore sub-agent, research-only — 20 flaws)
Full map: engine (`UpdateEngine.checkDueAnime/runCheck/checkSingleAnime`), the worker chain (`UpdateCheckWorker` → schedule refresh → check → retention → smart re-aim), the scheduler (`UpdateScheduler`), the notifier impl (`app/.../UpdateProgressNotifierImpl`), the episode-banner manager (`core/notifications/NotificationManager`), prefs (`UpdatePreferences`, `NotificationPreferences`), the UI surfaces (`UpdatesSettingsScreen`, `NotificationsSettingsScreen`, `UpdateCheckLogScreen`, `UpdatesScreen`/`UpdatesViewModel`), DI (Koin seams incl. the cycle-breaking `NotificationSender`), manifest (no update receivers; WorkManager initializer removed, `Configuration.Provider`).

The decisive flaws:
| # | Flaw | Consequence |
|---|---|---|
| 1 | Live card id 2001 posted `setOngoing(true)`, NO cancel call anywhere (grep-proven) | the stuck "scanning" card the user sees |
| 2 | CancellationException rethrown bare, zero cleanup | leaving the settings screen during a manual check → stuck card |
| 3 | `onCheckStart` fires only after the AniList schedule refresh + due query | the "checks first, then shows" complaint |
| 4 | Progress channel IMPORTANCE_LOW (immutable), no builder sound | no sound possible at all |
| 5 | `onProgress` = started-counter, 250 ms throttle, `android.R.drawable.stat_notify_sync`, no contentIntent | the "UI is not proper" + no live progress |
| 6 | `ExistingPeriodicWorkPolicy.REPLACE` in `reschedule()` on EVERY app open | the periodic anchor resets by a full interval — the timing drift |
| 7 | Interval row MANUAL-only (where the worker is cancelled); AUTO hides it | the interval setting is a no-op |
| 8 | No mutex across manual/periodic/smart triggers | interleaved runs, last-writer-wins `next_check_at` |
| 9 | `onFinish` early-returns on totalChecked==0 | manual "check now" with nothing due → silence |
| 10 | `canPost()` ignores the notifications master toggle (default FALSE) | notifications "off" still produced check cards |
| 11 | Result double-post (bare + async-cover re-post, alert-once false) | double sound |
| 12 | Banner id `30000 + (hash & 0x3FFF) + ep.toInt()` | cross-anime collisions; ep 12 overwrites 12.5 |
| 13 | `cleanupOldSent` zero callers | `notification_sent` grows forever |
| 14 | `concurrencySemaphore` declared, never wired | unbounded parallel source fetches ("3 parallel" KDoc false) |
| 15–20 | runBlocking in the history logger; in-app banner wiped on screen entry; smart one-shots bypass history; OFF mode not enforced for manual triggers; stale KDocs (KEEP/1-hour, AUTO-or-MANUAL); `onProgress` count semantics | assorted honesty/robustness debt |

## 2. The design (what "proper" means here)
- **Contract v2** (`UpdateCheckReporting.kt`): `onScanStarted(trigger)` → `onCheckStart(trigger, totalDue)` → per item {`onItemStarted(current, total, title)` → work → `onItemCompleted(current, total, title, newEpisodes)`} → `onFinish(summary)` | `onFailed(error: String)` | `onCancelled()`. KDoc pins who/when + the implementation duties (alert once, silent cancel cleanup, tolerate the periodic double-fire).
- **Impl** (`UpdateProgressNotifierImpl`): one channel `anikuta_update_activity` (IMPORTANCE_DEFAULT, sound+vibration, badge off; legacy `anikuta_update_progress`/`anikuta_update_results` deleted on ensure); 2001 live card = `setOnlyAlertOnce(true)` (one start sound), `setOngoing(false)`, `setTimeoutAfter(15 min)` re-armed per post (self-healing), `CATEGORY_PROGRESS`, history deep-link (requestCode 3002), `ic_notification`, completions-driven determinate bar, 250 ms throttle bypassed by all terminal transitions; 2002 results = single alert (cover re-post alert-once), MANUAL empty-run "No new episodes — you're all caught up" card, background empty runs silent (D-426 preserved); cancels never gated; `canPost()` = runtime permission AND master toggle.
- **Engine**: `checkMutex` single-flight; `onScanStarted` first-in-lock; cancellation → `onCancelled()` → bare rethrow; `checkActive: StateFlow<Boolean>` (finally-cleared); per-item signals around `checkSingleAnime` (title resolved from local DB first, network under the now-wired `concurrencySemaphore.withPermit`).
- **Worker**: `onScanStarted("periodic")` before `fetchSchedule()`; stale KDoc/constants fixed.
- **Scheduler**: `ExistingPeriodicWorkPolicy.UPDATE` (WorkManager 2.10.0) — the anchor survives app opens; interval edits land at the next boundary.
- **UI honesty**: interval row in AUTO+MANUAL (hidden only in OFF); check-now disabled in OFF; MANUAL check-now builds the Updates-tab's exact category filter; Updates tab refuses OFF via a `checkMessage` banner and gates `clearProgress()` on `!checkActive`.

## 3. The execution (sub-agents + main)
- **80-a** (general-purpose): core/updates per the design. Findings: `onFailed` already takes String; `checkSingleAnime` already returns a result holder with `newEpisodes`; `UpdateCheckSummary.trigger` already existed — zero call-site edits.
- **80-b** (general-purpose): the app layer per the design (the repo compiled-conceptually only after this — 80-a's contract had exactly one implementor). The 80-b self-review caught its own smart-cast bug pre-review.
- **80-c** (independent review): **GREEN** — full re-reads of all 10 files, a real tokenizer brace/paren/bracket balance pass (all balanced), interface conformance (7 methods, `onFailed(String)`), Koin wiring (the impl's new ctor arg matches its single call site), no-deadlock audit (no suspend inside `synchronized`), UPDATE-policy availability on 2.10.0, changed-file set exact, zero "sponsor" hits. Four non-blocking recommendations → applied by the main agent: the completed-bar must count completions (`completedCount += 1`, the started-counter overcounts under parallelism), the `onScanStarted` double-fire doc, the T7 semaphore wiring (`withPermit` around `checkSingleAnime`), the stale `AUTO or MANUAL` KDoc.

## 4. The release-system documentation (order 1)
- `ANI-KUTA-RELEASE-PLAYBOOK.md` — repo-external (my-project upload/ + download/): the TL;DR checklist, the two-repo roles + iron rules (never mention the build repo inside the official repo; never move app code), credential layout, artifact provenance (release-build-once.yml, no local builds), the name mapping, the ZIP recipe (`zip -9`, inner-hash equality, the v1.1.3 size table), the draft→verify→publish procedure, tag/versionCode rules, body rules, the post-publish checklist, the releases-list flake incident playbook, the never-do list, the API quick reference.
- `RELEASES.md` — committed to the OFFICIAL repo's main: the sanitized subset (zero build-repo mentions).

## 5. State at round-close
- Implementation complete + review-GREEN + docs written on the mainline; UNCOMMITTED-then-committed locally; **UNPUSHED** — the dev PAT was lost with the sandbox (SESSION.md's documented recovery: ask the user). CI ledger: 0/2 runs this cycle.
- Next session (after the PAT): push → implementation CI run (1/2) → cut release/1.1.38 from the green head → bump commit (1.1.38/10138 + the 48-RELEASE record) → tag v1.1.38 → the Release APK run (2/2) → LIVE-mirror docs → ntfy → the user's device round on the release APK (the checklist: start sound at tap; live "Checking x of y · Title" with a moving bar; no ghost card after finish/cancel/leave; caught-up card on an empty manual check; the interval row in AUTO; OFF refusal; swipe-ability; the 15-min self-clean).

## 6. Machine checks
- Tokenized brace/paren/bracket balance ×10 changed files: ALL BALANCED (review agent's state-machine script).
- `git diff | grep -ci sponsor` → 0. Changed-file set = exactly the 10 planned files. No manifest/Gradle/test/docs changes. No local builds (CI is the compiler of record).
