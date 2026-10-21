# Round 131 - The device round: waiting downloads, the relink refresh, and the honest player (D-767..D-769)

> **Status:** LANDED on `main` and CI GREEN (2026-10-21). Commit **030cb1de**; Build APK run **39570066068** (`workflow_dispatch`, `run_unit_tests=true`). No version bump, no tag, no release (D-738).
> **The input:** the user's device round on **v1.1.74 / 10174**.
> **The order, in one line:** downloads must show a real "waiting for network" state and resume themselves, a relink must actually refresh the content, and the player must resume finished episodes at 0, advance E10 -> E11, and show a compact dismissible banner.
> **Deliverables:** `ROUND-131.html` / `.md` (the report + device checklist) and `NAVIGATION-TEST-GUIDE.html` / `.md` (the navigation guide) at the workspace root.

---

## 1 - Downloads wait for the network (D-767)

The report: a network loss left the downloads in the **paused** state, requiring a manual resume; the persisted queue could come back paused after the process died.

The fix has three parts.

1. **The state is real.** `DownloadStatus.WAITING_FOR_NETWORK` is the eighth status. It is persisted in the existing `state` column (no schema change), is pausable and resumable, consumes no download slot and is not terminal. The details page and both player lists render it as Paused; the downloads screen shows an honest "Waiting for network" pill and the summary notification says "Resumes automatically when connectivity returns".
2. **The callback watches the right network.** `DownloadService` registers `registerDefaultNetworkCallback` instead of `registerNetworkCallback(NetworkRequest)`, so a non-default (possibly non-validated) network can no longer pause downloads that are actually online. The callback trusts its OWN `NetworkCapabilities` (the `activeNetwork` read can still point at the old network during `onAvailable`), requires both `NET_CAPABILITY_INTERNET` and `NET_CAPABILITY_VALIDATED`, re-evaluates the CURRENT default on `onLost` (Wi-Fi -> mobile is not "offline"), and seeds the queue with the current connectivity at registration.
3. **The queue promotes persisted work.** `DownloadQueue.tryStartNext` moves QUEUED tasks to WAITING when the Wi-Fi-only policy or the real reachability probe fails, and promotes every persisted WAITING row back to QUEUED when connectivity returns. That second half is what covers a process restart, where the in-memory network-pause registry is empty. `hasNetworkPausedTasks` includes waiting rows, and `AnikutaApp` starts the service for them at process start, so the foreground service and its callback stay alive for the auto-resume.

User intent still wins: pause/resume/retry/cancel (single or all) clear the network marker and transition the task. `DownloadStatusTest` locks the capabilities.

## 2 - The relink replaces the identity (D-768)

The report: after switching from source A to source B, the episode list changed but the cover, title and description stayed on A, and a refresh re-fetched A.

There were two causes.

- **The in-memory base survived.** `DetailsViewModel.linkSource` only created an `extensionBase` when it was null. It now replaces the base whenever the source id or the anime url differs, clears `_episodeMetadata` so A's thumbnails/synopses cannot leak into B, and re-merges. `loadLinkedSource` mirrors the rule: the persisted DB base is restored only when the in-memory base is absent OR belongs to a different source/url.
- **The enrichment was never persisted.** The background fetch updated only the in-memory base, while the DB kept the sparse `SAnime` from search (often no cover or description), so the next open showed old/blank data. The new `persistEnrichedExtensionBase` upserts the `ContentDetails` row when it is missing and writes the FULL extension axis through `updateExtensionAxis`; null fields preserve the stored values. It runs after both the link and the reopen enrichment.

Both paths now capture the generation BEFORE launching the coroutine, so a late enrichment from an old screen cannot overwrite a newer relink.

## 3 - The player resumes and advances honestly (D-769)

The report: a finished episode started at its end (or offered a resume the user did not want), E10 skipped to E9, and the banner's Dismiss was hard to use.

- **Resume:** a COMPLETED episode starts at 0 with NO "Resume from mm:ss" offer; a partially watched episode resumes at its stored position; a never-watched episode starts at 0. The duration/threshold branch is gone: `PlaybackResumePolicy.plan(storedPositionMs, isCompleted)` has the only two inputs it needs. This supersedes D-761's offer clause; D-761's end-of-episode action and the `autoPlayNext` preference stand.
- **Advance:** `List<SimpleEpisode>.nextEpisodeAfter` picks the smallest episode number greater than the current one, independent of list order, with specials (number <= 0) falling back to raw order. A descending E12..E1 list now goes E10 -> E11.
- **No cascade:** auto-advance is armed only after the player has seen the episode below the 95 % threshold once, so the stale high position left over from an episode switch cannot run through the list.
- **The banner:** one per episode, auto-clears after 5 s (`ACTION_BANNER_MS`), and is now a compact bottom-right pill with a real 28 dp X dismiss target. The old text "Dismiss" button was the unresponsive control.

Tests: `PlaybackResumePolicyTest` (10, rewritten) and `NextEpisodeTest` (5).

## 4 - CI and verification

| | |
|---|---|
| Commit | `030cb1de` |
| Run | **39570066068** - GREEN, first try |
| Trigger | `workflow_dispatch`, `run_unit_tests=true` |
| Unit tests | every test source set compiles; the touched suites run (`:core:download`, `:core:player`, `:feature:watch:api`) |
| Build | `assembleDebug` GREEN |
| ABI gate | arm64-v8a only, GREEN |
| Artifact | `anikuta-apk`, 45,471,970 bytes, artifact id 12332082066 |
| Release | none (D-738); mainline stays 1.1.20/10120; latest debug release v1.1.74/10174 |

The push run was superseded by the dispatch run under the workflow's `cancel-in-progress` concurrency rule; the dispatch run is the gate of record.

## 5 - The device checklist

1. **Offline wait:** start 2-3 downloads, then turn off Wi-Fi and mobile data. The queue must show "Waiting for network" (not a user Paused) and the notification must say it resumes automatically. Swipe the app away, restore connectivity, reopen: the downloads must resume without a manual tap, including the rows that were waiting across the process restart.
2. **Wi-Fi-only:** with the Wi-Fi-only preference ON, switch to mobile: the queue waits. Switch back: it resumes.
3. **User intent:** pause a waiting task -> it becomes Paused; resume it while online -> it downloads; cancel -> it disappears.
4. **Relink:** open a content linked to source A, unlink, link source B. The cover, title, description and episodes must all change to B. Pull to refresh and reopen the content: it must still be B (not A).
5. **Resume:** watch an episode to completion, reopen it -> it starts at 0:00 and no resume banner appears. Stop another episode at ~5 minutes, reopen -> it resumes near 5:00. A never-watched episode starts at 0.
6. **Next episode:** on a descending list (E12..E1), press skip at E10 -> E11 (not E9). With autoPlayNext ON, the 95 % auto-advance goes to E11. The last episode switches nowhere.
7. **Banner:** with autoPlayNext OFF, cross 95 % -> the compact bottom-right pill appears. Play switches; the X dismisses immediately; the pill hides itself after ~5 s and does not reappear for the same episode.

## 6 - The ledger

| Run | What | Verdict |
|-----|------|---------|
| 39570066068 | the Build APK dispatch with the unit-test gate | GREEN |
| - | the APK artifact (arm64-v8a, ABI-gated) | UPLOADED |

Next decision: **D-770**. Next record: **107**. Next round in the backlog: **M8 - the extension trust work**, unless the device round adds findings.
