# Round 122 — The Device Round Two: the Trusted FILE_LOADED, the Number Successor, the AniSkip POST, and the Continuity Pin

> THE ORDER (the user's round-121 device report, "most definitely not that much satisfied"): the downloaded-episode resume said "Resumed 12:00" but played from the very START (streaming was fine); a FAILED episode pinned the seek bar at the very end, refused seeking back, and got MARKED WATCHED; the Up Next card after EP 2 offered EPISODE 1 (both stacks, both triggers); the card resurrected after a cancel when switching orientation, and the portrait card was ugly with no Play button; AniSkip "not working at all" — and regardless of settings the intro/outro ranges should show ON the progress bar itself; the episodes sheet must become a RIGHT-SIDE drawer ≤40% of the screen; episode switching must pick the SAME server, resolution and audio language as the episode being left; downloads: the top bar lacked Pause while tasks waited, resuming offline STILL errored ("unable to resolve host") and those false failures never auto-recovered; reorder must be LONG-PRESS DRAG; relinked details lost tags/status on reopen until a manual refresh. Take your time, plan properly. ALL ON `main`.

## 1. The trusted FILE_LOADED + the reached-playback gate (D-756)

**Downloaded resume at 0:00** — the resume seek keyed on `loadingState == READY`, but READY is the holder's DEFAULT (and the PLAYBACK_RESTART fallback's value): the downloaded (fd://) path posts its loadfile 500 ms late, so the effect ran before mpv had a file, the seek hit an uninitialized core (mpv silently drops pre-init seeks), and the per-episode latch made the loss permanent — the pill still showed because nothing reported the failure. Streaming masked it by loading fast. The effect now keys on **`fileLoadedGeneration`** (a PlayerStateHolder counter bumped ONLY by a real `MPV_EVENT_FILE_LOADED`), seeks immediately (no blind 300 ms delay), and **verifies**: 600 ms later the live position must be within 5 s of the target or the seek re-issues once.

**The failed-episode end-pin + watched-mark** — no position reset happened at loadfile start, so the OLD file's pinned end-of-file position (keep-open) was saved against the NEW episode's key by the periodic/exit saves, tripping the store's 85 % auto-mark. The fixes: (a) `episodeReachedPlayback` — a @Volatile gate armed only by a real time-pos event while NOT switching, cleared on every switch/metrics-reset, gating the periodic save, the exit save, the pre-switch save, AND the EOF completion write (plus `loadingState != ERROR` on the first two); (b) `resetPlaybackMetrics()` after `setSwitching(true)` in the retry and quality-switch handlers (the auto-retry deliberately does NOT reset — the same episode's position there is real).

## 2. The number-based successor + the Up Next UX (D-757)

**"Next after EP 2 was EP 1"** — the successor was POSITIONAL (`idx + 1`) over the WatchKey's serialized list, which the details page writes in the SOURCE's raw order — commonly DESCENDING. After EP 2 in [3,2,1], positional next = EP 1; the skip buttons inherited the same inversion (the "back button goes to the next episode" observation). Fixed at three layers: the list is ascending-sorted ONCE at parse (stable — sub/dub duplicates keep source order); `nextEpisodeInList` is now NUMBER-based (URL → number locate of the current row; successor = the MINIMUM of the strictly-HIGHER numbers — never positional, never wrapping; `prevEpisodeInList` mirrors); and the CS stack's `nextInFlavor` got the identical rule over its flavor-filtered candidates (Task 56's flavor containment preserved).

**The resurrection** — the dismissal flag was shared, but the EOF hook's `force` flag bypassed the once-per-episode latch (MPV), and the CS 95 % ticker re-invoked `offerUpNextCard()` every tick. A per-episode `dismissedNextKeys` latch (MPV) / `dismissedEpisodeData` set (CS) now guards EVERY offer path before the once-latch; both latches prune to the current episode on change.

**The portrait card** — the fullscreen-tuned 252 dp card clipped against the 16:9 player box (the Play button out of reach). The card is `fillMaxWidth` now (the HOST caps it: fullscreen `widthIn(300.dp)` + the 84 dp upward nudge the user asked for; portrait renders it as a full-width inset between the player and the episode list — same content, never clipped), and the old 232 dp skip-pill stack offset died with the clipped variant.

## 3. AniSkip: the POST, the honest latch, and the ribbon (D-758)

**Why nothing showed** — the AniList→MAL lookup used GET query parameters, and AniList REJECTS GET on graphql.anilist.co with HTTP 404 (verified live: POST 200, GET 404 "Use POST request to access graphql subdomain") — every mapping failed and was NEGATIVELY CACHED, and the fetch effect's negative latch marked every episode "asked" forever, even with no ids at all. The client now POSTs the JSON body; only definitive answers (HTTP 200, no mapping) are negatively cached; the caller latches only when the attempt actually had an id. `episodeLength` is now sent in SECONDS (the API contract; the round-121 milliseconds value was masked only because duration is 0 at fetch time). Extension-only content with neither id still can't be mapped (noted follow-up: an AniList title-search fallback).

**The ribbon** — the effective ranges (AniSkip wins per side, manual stamps the fallback) draw ON both seek bars regardless of Off/Button/Auto: an amber band for the intro, red for the outro (a manual outro runs to EOF), under the progress fill. Fullscreen draws them in the seekbar's Canvas; portrait as positioned Boxes.

## 4. The continuity pin + the right-side drawer (D-759)

**Switching now keeps your server** — at switch start (before anything clears the current episode's registry) the pin is captured: server name + audio flavor + quality, located in the live ResolvedVideosRegistry (process-death fallback: parse the video title; terminal fallback: quality-only). After the resolve, `pickPinnedVideo` mirrors the downloads' pinned re-resolve with a logged ladder: L1 exact server+audio+quality → L2 same server+audio, nearest resolution → L3 same server → L4 same audio (language kept, hoster changed) → L5 fuzzy server (renames) → L6 first-of-list (the old behavior, logged). The pick can degrade but never fail; prev/next/Up Next inherit it for free.

**The drawer** — the episodes browser is a right-side panel now: scrim tap to close, BackHandler, slide-in from the right, capped at 40 % of the screen width (hard cap 340 dp), opening ON the current episode, with the skip-timestamps entry in its header.

## 5. Downloads: the honest check, the healing sweep, and the drag (D-760)

**Offline resume STILL errored** — the injected `connectivityCheck` was a Wi-Fi-only TRANSPORT check with three `return true` escapes: with wifiOnly OFF (the default) it returned TRUE while fully offline → resume went QUEUED → launched → `UnknownHostException` wrapped in DownloadException ("Video download failed: Unable to resolve host…") → non-retryable → ERROR. Fixed: `networkAllowedCheck` fails CLOSED on a missing/un-capable network; the fetchers' wrapped failures walk their CAUSE CHAIN into the wait path; and on network return `resumeNetworkPaused` now also HEALS the false ERRORs whose lastError matches network-class markers (via `resume()` — partial bytes kept, unlike `retry()`), with the service keep-alive filter counting them so the callback survives an ERROR-only queue. The top bar counts waiting tasks as active — the Pause button the user expected now shows.

**Drag-to-reorder** — the arrows are gone: long-press a row (or its handle) and drag; the official Compose LazyColumn drag-drop pattern with midpoint swaps over real item bands (variable heights work), edge auto-scroll, a rebased drag delta, and ONE commit of the full order on drop (`commitReorder`, pruned to live ids). The pinned DOWNLOADING/RETRYING rows stay pinned, non-draggable, un-passable — the drag arranges the start order of everything else, which is exactly what the scheduler consumes.

## 6. The relink that remembers (D-761)

`linkSource` stored the SPARSE search SAnime and the seconds-later enrichment lived in memory only — reopening read blank tags/status until a manual refresh. The enrichment now persists its ext_* axis (`updateExtensionAxis`, the same field mapping as the proven refresh path, guarded by the relink generation) — and the reopen path's legacy no-axis branch got the same persist.

## 7. Deliberately out of scope

AniList title-search fallback for extension-only content (no ids to map), a durable (across-process) Up Next dismissal, and the database round (still gated on §30/Q2).

## 8. The verification

Five read-only research agents produced the root causes (one decompiled the material3 AAR and fetched the OkHttp artifact to settle API questions — two of whose conclusions CI then corrected, see the ledger). The implementation ran in-file; the pre-CI verifier read the whole 17-file diff against the R1–R11 checklist and caught two compile errors pre-push (`state.videos` is `List<ResolvedVideo>`, not `ResolverVideo` — the pick now maps back to the flat twin by URL; a missing drag-gesture import).

## 9. The CI ledger (D-472 — THREE over budget, disclosed)

Commit `090dcdfe` (fix, 17 files, +1182/−275) → run **40085798285** RED (one error: `MediaType.parse` is deprecated at ERROR level in the OkHttp 5 alpha — the verifier's artifact check saw the method but not its deprecation level) → fix `c11970ac` (`toMediaType`/`toRequestBody`, the repo's proven pattern) → run **40085968216** RED (two missing imports: `gestures.scrollBy`, `fillMaxHeight`) → fix `18f8c4ba` → run **40086206221** RED (one error: `CsWatchUiState` is NESTED in the ViewModel — a fully-qualified guess by me broke the reference) → fix `db640fa8` → run **40086430506 GREEN** (head `db640fa8`; the `anikuta-apk` artifact, versionCode 10120). FOUR Build APK runs — three over the D-472 budget; the classes: deprecated-at-ERROR APIs invisible to artifact-presence checks, missing imports on new files, and a wrong qualified-name guess — all banked in lessons-learned. The docs push is paths-ignored.

## 10. The device checklist

Lives OUTSIDE the repo per the workspace convention: `planning/2026-10-24-round-122-device-test.html` (+ the .md twin) — the grouped regression guide for every fix in this report.
