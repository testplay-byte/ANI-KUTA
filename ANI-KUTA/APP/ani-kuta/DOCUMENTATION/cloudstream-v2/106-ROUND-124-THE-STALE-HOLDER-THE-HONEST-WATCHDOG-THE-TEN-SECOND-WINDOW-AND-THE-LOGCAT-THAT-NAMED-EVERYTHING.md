# Round 124 — The Stale Holder, the Honest Watchdog, the Ten-Second Window, and the Logcat That Named Everything

> THE ORDER (the user's round-123 device report + the first-ever logcat capture): downloaded resume CONFIRMED fixed and the drawer look CONFIRMED perfect — but a failing episode STILL pinned the bar at the end, showed the Up Next card mid-failure and never showed a proper error; a second new bug showed a FALSE error screen over a successfully-loaded-but-paused video (the error cleared and returned in a loop); Up Next must appear 10 seconds before the end (not 5%) and must REAPPEAR after dismiss→seek-back→finish; AniSkip still showed nothing at all; the drawer needs its current episode CENTERED and its "Skip timestamps" entry REMOVED outright, with qualities/servers and subtitles converted to right-side drawers; the fullscreen darkening stays after the controls fade; the drag is smoother but still glitchy ("take the download-settings priority order list as the reference"); paused downloads show no Paused tag; the details page shows a WRONG title after kill+reopen; link-to-AniList should auto-switch the data source; notifications never fire while the app is closed; and "make sure the logcat is proper for future debugging."

## 1. The logcat delivered (and how it was mined)

The capture (~694KB, 3245 lines, three empty-line-separated sections) was parsed by a script (sections split, `Anikuta:*` + player lines extracted per section) rather than read raw — the user explicitly asked for exactly that discipline, and the parser is now a permanent tool (`planning/tools/parse_logcat.py <FILE> [OUT]`). Three sections told three stories, and every root cause below came out of them with timestamps.

## 2. The stale holder (D-770)

Section 1: at 00:33:16.860 a failing load's FILE_LOADED fired, and 2ms later the EOF hook and the Up Next poll ran with `pos=1437 dur=1437 reached=true` — the PREVIOUS playback's pinned end-of-file metrics. The initial open never reset the holder (every switch path did since D-749; the init path was missed), and the old file's reached-playback gate was still armed. The card "on failure" and the end-pin are the same bug: the periodic save then wrote `pos=1437` under the new key (auto-mark). Fixes: the initial load calls `resetPlaybackMetrics()` like every switch; and `episodeReachedPlayback` now arms ONLY on a non-EOF position (`pos < dur-1` with dur>0) — a dead stream's demuxer EOF pins time-pos at the full duration and that pin must never read as "played". The EOF offer also demands `dur > 0`.

## 3. The honest watchdog (D-771)

Section 2: the video loaded fine (FILE_LOADED, duration 1420) but sat paused at 0 — and 15s later the log showed `Fatal-error watchdog: stuck at start for 15s (not user-paused)` → auto-retry → the same again: the error-clears-and-returns loop, and the false error screen the user had to tap through. Root cause: the watchdog checked the APP's `userPaused` flag, and the quality-switch loadfile never forced `pause=no` (the init and switch paths did) — mpv was paused, the app flag said otherwise, mpv's truth was unread. Now the watchdog asks MPV ITSELF (`MPVLib.getPropertyBoolean("pause")`): a paused-at-start file gets UNPAUSED (every loadfile in this app means autoplay), and only a genuinely-unpaused stuck file errors. The quality-switch loadfile also forces `pause=no` + clears the flag like every other path.

## 4. The ten-second window + the per-approach dismissal (D-772)

The user's order retired the 95%-fraction window: "it should only appear 10 seconds before the episode is actually ending" (the outro-based trigger returns once AniSkip carries real data — then the card rides the outro start). The offer is now `duration - position <= 10`; the countdown ticks in the same window. And the dismissal became per-APPROACH: seeking back out of the end zone (>15s left) re-arms both latches, so the dismiss→seek-back→finish flow offers the card again — the device round's exact complaint.

## 5. AniSkip: the two silent killers (D-773)

Section 1's fetch line said everything: `AniSkip fetch: … malId=null anilistId=171018 durSec=0 (latched=false)` → `intro=null outro=null (miss=true)`. Two killers: (a) the fetch ran ONCE at episode open while duration was still 0 — the API got `episodeLength=0` (a guaranteed miss) and the miss LATCHED permanently; (b) the MAL id was null even though the episode URL literally carried `&mal=57334&`. Fixed: the fetch effect re-keys on duration availability (it waits for the file's real length; the cached-read path is unaffected), and the MAL id falls back to the episode URL's `mal=` parameter when the DB row has none.

## 6. The drawer set (D-774)

The episodes drawer now CENTERS on the current episode (the shared `animateScrollToItemCentered` — instant prime jump first, then the centered settle; edge-clamped so the first/last episode rests at its edge instead of glitching). The "Skip timestamps" entry is REMOVED OUTRIGHT (the user's exact words) — its editor sheet, its state, and the whole manual-stamp write path die with it (the stored stamps still feed the skip fallback). And the user's structural ask: qualities/servers + subtitles are RIGHT-SIDE DRAWERS now — a shared `RightDrawerShell` (the episodes drawer's exact scrim + slide-in + ≤40% panel) hosting the unchanged selection logic.

## 7. The scrim rides the controls (D-775)

The unlocked-mode top/bottom darkening rendered unconditionally as a sibling of the controls — it stayed after they faded. It's wrapped in the same `AnimatedVisibility(controlsVisible)` fade now (the locked-mode scrim is intentionally permanent and untouched).

## 8. The drag on the reference pattern (D-776)

"Smoother, but still glitchy… take the download-settings priority order list as the reference" — so the rebuild copies `DragReorderableList` (the exact list the user praised): a plain Column of fixed-height rows (not a LazyColumn — no `animateItem()` placement springs fighting the finger-drawn translation, the round-123 jank's remaining source), the dragged row translated in the DRAW phase only, swaps by pure integer division over the row height with an offset rebase, displaced neighbors SNAP, the gesture starts immediately on the handle (no long-press wait), commit-on-drop + rollback-on-cancel. Pinned DOWNLOADING/RETRYING rows still can't be dragged or passed (target coerce). `QueueDragDropState` is deleted. Paused rows got their own visible `Paused — n%` tag (they rendered a bare percentage exactly like an active download).

## 9. The title that wasn't the title (D-777)

Section 3's dumps showed it: the manual refresh wrote `anime="Dandadan"` while the background reopen wrote `anime="dandadan lzcmw"` — the reopen restore derives the extension base's title from the URL SLUG (`substringAfterLast("/").replace("-", " ")`) when the DB row has no ext axis populated at that moment. `ExtensionExtras` gains a persisted `title` field (additive JSON); all six ext-axis write sites store it; the reopen restore prefers it, then `main_entry.title`, and only then the slug. And linking to AniList now AUTO-SWITCHES the data source (`switchDataSource(ANILIST)` after the merge — the full round-102 persist contract), per the user's ask.

## 10. The notifications that waited for the app (D-778)

The checks are WorkManager-driven (the periodic 24h worker + per-episode smart-release one-shots), and WorkManager persists across reboots — but the one-shot chains were only ever armed while the app ran: after a reboot or app update, nothing re-armed until the next app open. A `BootCompletedReceiver` (BOOT_COMPLETED + MY_PACKAGE_REPLACED) now runs the same two calls the app startup makes (`UpdateScheduler.reschedule()` + `SmartReleaseScheduler.scheduleUpcomingChecks()`); `RECEIVE_BOOT_COMPLETED` joins the manifest. And the POST_NOTIFICATIONS runtime request — previously onboarding-only, so any install that skipped onboarding had notifications silently off forever on Android 13+ — is asked at first launch from MainActivity.

## 11. The logcat, tamed (R12)

The episode-list dump truncated URLs at 200 chars — the round's capture was ~90% opaque base64. `MAX_URL` is 48 now. The parse tool is permanent (`planning/tools/parse_logcat.py <FILE> [OUT]` — sections → per-section Anikuta + player files + a summary), and every fix in this round logs its decision line, so the next capture reads itself.

## 12. The verification

The pre-CI verifier read all 12 files against the round checklist (including javap on the mpv-lib and compose artifacts for the API questions, and a byte-level check of the regex escaping) and caught ONE compile error pre-push (`MutableTransitionState` lives in `animation.core`, not `runtime` — the wrong-package import would have failed the first CI run). Fixed pre-push.

## 13. The CI ledger

Commit `0807c98d` (fix, 12 files, +479/−417, one file deleted, one added) → run **GREEN** (the `anikuta-apk` artifact, versionCode 10120; the mainline stays 1.1.20/10120 per the standing rule). ONE run — inside the D-472 budget. The docs push is paths-ignored.

## 14. The device checklist

Lives OUTSIDE the repo per the workspace convention: `planning/2026-10-25-round-124-device-test.html` (+ the .md twin) — with the logcat-capture guide again (the tool now ships in `planning/tools/`).
