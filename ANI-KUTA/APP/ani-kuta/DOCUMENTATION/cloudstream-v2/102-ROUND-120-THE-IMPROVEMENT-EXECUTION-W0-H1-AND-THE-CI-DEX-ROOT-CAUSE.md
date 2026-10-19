# Round 120 — The Improvement Execution (W0 + H1) and the CI dex root cause

> **Status:** IMPLEMENTED + CI GREEN (2026-10-19). Decisions: **D-746** (the plan is authoritative), **D-747** (the download service owns its lifecycle), **D-757** (the CI dex root cause: AGP 8.10.1 + the 8 GB daemon heap). Round record: doc 102. Plan: `PLAN.md` / `PLAN.html` (workspace root). Backlog evidence: `IMPROVEMENTS.md` / `IMPROVEMENTS.html`.
> **The order (the user, 2026-10-19):** the whole 65-item improvement backlog was reviewed and **approved**, together with the order it runs in. "Everything is clear you can continue — everything is approved." The round therefore starts the program: the plan is written and made authoritative, the reporting convention is fixed, the first behavior round (H1, the download lifecycle) is implemented, and the CI failure that surfaced during the round is root-caused and fixed properly rather than worked around.
> **CI ledger:** 39299023955 **RED** (the OOM) → 39304046376 **GREEN** (the heap) → 39305662332 **GREEN** (AGP 8.10.1) → 39307462826 **GREEN** (the H1 follow-ups) → 39308252031 **GREEN** (the D-757 id correction) → 39309189781 (cancelled by the dispatch) → **39309242655 GREEN — the opt-in test gate dispatched (`run_unit_tests=true`): every test source set compiles, every test task runs, and the same run built + ABI-gated + uploaded the debug APK.** No version change, no tag, no release (D-738: a release only on the user's order).

---

## 1. The plan (D-746) — order, scope, verification, deferral

`PLAN.md` (mirrored by `PLAN.html`) is the execution roadmap. It does not re-argue the findings — `IMPROVEMENTS.md` holds the evidence — it decides **order, scope, verification, and what is deliberately deferred**.

- **Ground rules (non-negotiable):** CI-only builds (CI is the compiler of record, CORE_RULES §8); the debug line only (no version bump, tag, or release without the user's explicit order); the device verification is the user's; **one round = one coherent change set** (commit per phase → push → poll CI → read the logs → fix → repeat, no unreviewed bundles); documentation is part of the change; no silent behavior changes; future-proof by construction.
- **Reserved decision IDs D-746..D-756** so that code comments can cite stable numbers before the rounds land. **D-757 is this round's build fix** — a decision that arises mid-plan takes the *next* free id rather than renumbering a reserved one.
- **Waves:** W0 foundations → W1 (H1 downloads, H2 navigation, H3 MPV watchdog) → W2 (M1..M8 reliability, playback, cache, DB, extensions) → W3 (the large deferrals: backup/restore, the central navigator, the batch docs sweep).
- **The reporting convention (the same session):** every substantial report the user reads ships as a self-contained neumorphic **HTML** file beside its Markdown source — `AGENT-CONTEXT/knowledge/reporting-format.md` (pairing, severity vocabulary, chart rules, checklist) + `AGENT-CONTEXT/templates/html-report-template.html`, wired into CORE_RULES, SESSION, workflow and navigation.

## 2. H1 (D-747) — the download service owns its lifecycle

The three backlog items, and the truth behind each:

| Item | The problem | The change |
|------|-------------|------------|
| **DL-1** | The manager stopped `DownloadService` whenever no task was active. That stop killed the service's `NetworkCallback` — the exact thing that fires the D-246 auto-resume when connectivity returns — so network-paused downloads never restarted. | The manager-side `DownloadService.stop()` is **deleted** (the companion `stop()` too). The service stops **itself**, and only when the queue is empty **and** no network-paused task exists (`queue.hasNetworkPausedTasks()` is the guard the D-246 design already had). |
| **R-9** | A user-initiated resume/retry/pause/cancel left the network-pause marker behind, so the service kept waiting for a network event for a task the user had already restarted. | `resume` / `resumeAll` / `retry` / `pause` / `cancel` clear the marker — **user intent wins**. The marker set moved into `NetworkPauseRegistry` (a synchronized wrapper whose `snapshotAndClear` is atomic) with a unit test. |
| **R-1** | Queued work was not restarted after a process restart: the queue resets stale DOWNLOADING/RETRYING rows to QUEUED in its init, but nothing scheduled them. | `DownloadService.onStartCommand` kicks `queue.tryStartNext()`; `AnikutaApp` starts the service at **process start** when persisted QUEUED rows exist (guarded; the failure is logged, never a crash). |

### 2.1 What the review pass then hardened (the same round)

A review of the H1 diff found a residual stale-marker window and three accuracy problems. All were fixed before the round closed:

1. **The marker could outlive its task.** `pauseForNetwork` added the marker *unconditionally and outside* the mutex, so a task cancelled in between kept a marker — which pinned the foreground service forever and later "resumed" a deleted task. Now: `pauseInternal` returns whether it really paused, the marker is added **inside** the mutex **only** for a really-paused task, and **every** marker mutation (pause/resume/retry/cancel/resumeAll/pauseAll/cancelAll) happens **under the queue mutex** — the registry mirrors the state it describes, so it cannot drift.
2. **"Boot recovery" was not true.** There is no `BOOT_COMPLETED` receiver anywhere in the app: the recovery runs when the **app process** starts (a user tap, or a background worker waking the process). The comments now say exactly that; the queue rows are persisted, so nothing is ever lost.
3. **The background-start restriction was swallowed.** `runCatching` around the foreground-service start hid `ForegroundServiceStartNotAllowedException` (API 31+; a background-only process start is refused). It is now caught explicitly and logged with the honest consequence: the queue stays persisted and the next user-visible start resumes it.
4. **The scheduler kick ran on the main thread.** `onStartCommand` resolved the `DownloadQueue` singleton (whose init does a DB write) inside a service callback. The call now runs on the service's IO scope.
5. **The offline case died instead of waiting.** `connectivityCheck` is the *Wi-Fi-only policy* check and fails **open** when that policy is off, so it could not tell "the device is offline" from "any network is allowed": an offline transport error burned all three retry attempts and ended in a dead ERROR. A real `hasInternet` probe now exists beside it, and an offline error **pauses the task for auto-resume** instead (the service then stays alive on the marker and the network callback resumes it).

## 3. D-757 — the CI dex failure, root-caused (the round's real find)

The H1 push went **RED**: Build APK run **39299023955** (commit 7ecc8d2e). The annotations said nothing; the step log was 225 MB and the per-job download kept truncating. Reading the per-step log zip gave the truth:

```
FAILURE: Build failed with an exception.
* What went wrong:
Java heap space
```

— raised at `:app:mergeExtDexDebug`, after every module had compiled. Two independent, verified causes:

**(1) The D8 metadata flood.** Google's own compatibility table (`developer.android.com/build/kotlin-support`) says **Kotlin 2.2 requires R8 ≥ 8.10.21**. AGP bundles D8/R8, and **AGP 8.9.1 bundles R8 8.9.32** — too old. D8 therefore could not parse the Kotlin 2.2.0 metadata and, for every affected class, logged `WARNING: D8: An error occurred when parsing kotlin metadata` plus a ~40-line stack trace: **8,281** of them in the failing run and **11,450** in the *green* Oct-06 run. The flood pre-dated the failure; it was the memory pressure and the log bloat, not the trigger. (The bundled R8 version was read straight out of the AGP builder jar — the `com/android/tools/r8/**` classes carry the `R8_<version>` constant — not guessed from a changelog.)

**(2) The heap ceiling.** The app-level dex merge (one D8 pass over every external jar on the runtime classpath, plus the core-library desugaring of NewPipe's `java.time` usage, D-671) runs *inside* the Gradle daemon, whose heap was pinned at `-Xmx4g`. The Oct-06 green run only squeaked under that ceiling; this run tipped over it.

**The fix, both halves** (the repo is **public**, so the CI runner is `ubuntu-latest` = 4 vCPU / 16 GB):

- **AGP 8.9.1 → 8.10.1** (`gradle/libs.versions.toml`). It bundles **R8 8.10.24** (≥ the 8.10.21 Kotlin 2.2 demands), it needs Gradle **8.11.1** — its minimum *and* default, which is exactly the wrapper already in the repo (**no wrapper change**), and it officially supports **API level 36** (our compileSdk/targetSdk; AGP 8.9 topped out at 35). No removed API is used anywhere: the convention plugins and the app module use only stable DSL — no `finalizeDsl` (the one AGP 8.10 source-level break), no variant APIs, no Transform API. D-436 stands: both lines ship **unminified**; this bump changes the *dexer*, not the shrinker policy.
- **The daemon heap 4g → 8g** (`gradle.properties`, with `-XX:MaxMetaspaceSize=1g` so the daemon's RSS stays inside the runner's RAM).

**The result:** the D8 warnings fell 8,281 → **2,866**, the job log 225 MB → **73 MB**, the build **green in 9m10s**. The remaining 2,866 warnings come from *third-party* libraries compiled with a newer Kotlin than any current R8 parses (`com.fleeksoft.ksoup`, `org.koin.*`, `io.ktor.*`, `kotlinx.serialization.*`, `kotlinx.io.*`). They are warnings; they are harmless for us (no minification → no metadata remapping is ever needed); and they were present in every build since the project started. Closing them completely would need AGP 8.13+ (R8 8.13.19+) and a Gradle wrapper bump — a toolchain move with no user-visible gain, so it is deliberately **not** done in this round.

## 4. The opt-in test gate, and the broken test it exposed

W0-2 added the `workflow_dispatch` boolean input `run_unit_tests` (default false) so a round that needs the suite can dispatch it. The round **dispatched it** — and the first dispatch (run 39308771194) **failed**, on something that had nothing to do with the round's own code:

```
e: .../core/video-resolver/src/test/java/.../ResolverDebugReportTest.kt:3:12 Unresolved reference 'junit'.
> Task :core:video-resolver:compileDebugUnitTestKotlin
* What went wrong: Execution failed for task ':core:video-resolver:compileDebugUnitTestKotlin'.
```

`:core:video-resolver`'s test file imports `org.junit.*`, but the module never declared the dependency. Nothing had caught it: `assembleDebug` builds the **main** source sets only, and the unit-test step is opt-in (D-465) — so the file sat broken, uncompiled, for as long as it existed. The fix is one line (`testImplementation(libs.junit)`), and a repo-wide sweep confirmed that **all eleven** modules with a `src/test` source set now declare it.

The re-dispatch (run 39309242655, `run_unit_tests=true`) is **green**: every test source set compiles, every test task runs (`BUILD SUCCESSFUL` in 36 s), and the same run still built, ABI-gated and uploaded the debug APK. The standing rule: **a test source set that no CI step compiles can rot for months** — the opt-in gate proves something only if a round actually dispatches it, and the gate's own health is part of a round's definition of done.

## 5. Deliberately out of scope

- **AGP 8.13+ / R8 8.13.19+** (the last 2,866 third-party warnings) — no behavioral gain, a wrapper bump, more risk.
- **A `BOOT_COMPLETED` receiver** — a device-boot download restart is a *feature* (permissions, the Android 12+ foreground rules, a receiver in every build type), not a bug fix. The process-start recovery is what the backlog asked for.
- **Routing the recovery through WorkManager** — the honest fix for the background-start case, but a new worker + its scheduling policy; the explicit catch and the honest log are enough for now.
- **The W0-2 test gate stays opt-in** (D-465: the user found routine unit tests wasteful). The suite is validated by a `workflow_dispatch` with `run_unit_tests=true`.

## 6. The device checklist (the user's device — CI cannot prove behavior)

**Download lifecycle (H1)**
- [ ] Start a download on Wi-Fi, then turn Wi-Fi off: the task goes **PAUSED** (not ERROR) and the "Preparing downloads…" notification stays.
- [ ] Turn Wi-Fi back on: the task **auto-resumes** without any user action.
- [ ] Force-stop the app with a task QUEUED, reopen it: the queued download **starts**.
- [ ] Pause a task manually, then resume it: it runs, and the service does not sit in the foreground waiting for a network event.
- [ ] Cancel a task: the notification disappears and the service stops.

**Offline behavior (the D-757 hardening)**
- [ ] With the device offline and a download queued, open the app: the task **waits** (PAUSED) instead of failing with an error after three retries.
- [ ] Restore the network: the task resumes by itself.

## 7. Next

**H2 — navigation identity (D-748)**: `SaveableStateProvider` is keyed by the route class's simple name (two same-key providers during a replacement), and deep-link equality uses full data-class equality. The fix is one stable identity function per key type, used for the provider key and for the replace comparison — with unit tests per key type, per the plan's risk note.
