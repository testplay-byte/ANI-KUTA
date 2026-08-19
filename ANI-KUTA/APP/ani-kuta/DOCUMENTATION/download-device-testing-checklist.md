# Download System — Device Testing Checklist

> **Purpose:** Verify the download system (Phase DL.0-DL.8) + the D-FIX-SUB subtitle
> fixes on a real device. Built via CI (CORE_RULES §8 — no local builds). Install
> the latest CI APK from the `download-system-plan` branch, then work through this
> checklist. Report back with ✅/❌/⚠️ per item + any logs.
>
> **Logcat filter (Android Studio Logcat panel — paste directly):**
> ```
> tag:Anikuta:Core:Download | tag:Anikuta:Core:Download:Storage | tag:Anikuta:Core:Player:Subtitles | tag:Anikuta:MainActivity
> ```
> (Add `| message~:(?i)(subtitle|download|proxy|fd://|content://)` to broaden.)
>
> **Before you start:** Install the CI APK. Pick a source that has multi-language
> subtitles (e.g. AniKotoS) so the subtitle tests are meaningful. Have a second
> anime from the SAME source ready for the proxy-churn test.

---

## A. Enqueue + Basic Download

- [ ] **A1 — Download from details page (manual pick).** Open an anime's details
  page → tap the download button on an episode → the **ResolverSheet** appears
  (same as play) → pick a quality → the sheet dismisses + a toast/log confirms
  "Download enqueued".
  - **Expected:** ResolverSheet appears, pick works, episode shows a "Downloading"
    state in the episode list.
  - **Logcat:** `Download enqueued: taskId=...` (tag:MainActivity).

- [ ] **A2 — Download appears in the Downloads page.** Open the Downloads page
  (bottom nav → More → Downloads, or the downloads tab).
  - **Expected:** A card for the anime with the episode listed, progress %
    updating live.

- [ ] **A3 — Download completes.** Wait for the download to finish.
  - **Expected:** Progress reaches 100%, status flips to "Completed", the card
    auto-clears after ~10s (or stays if you have "keep completed" on).
  - **Logcat:** `publishVideoFile(...) — published <name> (<bytes>) to <folder> + N subtitle(s)`.

- [ ] **A4 — Episode shows as "Downloaded" on the details page.** Go back to the
  anime's details page.
  - **Expected:** The episode row shows a "Downloaded" indicator (filled icon /
    "Downloaded" label). The download button is replaced or supplemented with a
    "Play downloaded" / delete option.

---

## B. Offline Playback

- [ ] **B1 — Play a downloaded episode (online).** On the details page, tap the
  downloaded episode → it should play from the local file (not re-fetch).
  - **Expected:** Player opens, video plays. No network indicator / loading
    spinner for the video itself.
  - **Logcat:** `Downloads→Watch: passing N local subtitle track(s)` (tag:MainActivity).
    `fd://` appears in player logs (the content:// → fd:// conversion).

- [ ] **B2 — Play a downloaded episode (OFFLINE).** Turn on airplane mode. Open
  the Downloads page → tap the downloaded episode → Play.
  - **Expected:** Video plays from local storage. No network error.
  - **If fail:** check logcat for `fd://` + the 500ms surface-readiness delay
    (DL-CRITICAL-FIX3). An MPV SIGABRT here means the fd:// timing regressed.

- [ ] **B3 — Episode switching in downloaded mode.** While playing a downloaded
  episode, switch to the next episode (which is also downloaded).
  - **Expected:** Switches cleanly to the next downloaded episode, plays from
    local. No re-resolve / network call.

- [ ] **B4 — Switch from downloaded to non-downloaded episode.** While playing a
  downloaded episode, switch to an episode that is NOT downloaded (online only).
  - **Expected:** The player re-resolves + streams the online episode. May show
    a brief loading state.
  - **If fail:** check logcat for `sourceId=...` — if it's 0, the sourceId
    lookup from the content DB failed (D.FIX in MainActivity).

---

## C. Subtitles (the D-FIX-SUB fixes — MOST IMPORTANT)

> These verify the 5 subtitle fixes. **Pre-fix behavior:** downloaded episodes
> had NO working offline subtitles (the files existed on disk but their URIs were
> never stored in the DB). **Post-fix:** subtitles download WITH headers, are
> named with the language, URIs are stored, and survive reinstalls.

- [ ] **C1 — Subtitles download with the video.** Download an episode that has
  multiple subtitle tracks (e.g. English + Japanese).
  - **Expected:** Download completes. The publish log shows `+ N subtitle(s)`.
  - **Logcat:** `publishVideoFile(...) + 2 subtitle(s)` (or however many).
  - **If fail (0 subtitles):** the subtitle fetch is 403'ing. Check logcat for
    `Subtitle N fetch failed (403) — skipping`. This means the headers aren't
    being applied — the D-FIX-SUB header fix may need adjustment for this source.

- [ ] **C2 — Subtitles are named with the language.** Use a file manager (or
  `adb shell` if available) to inspect the download folder:
  `<SAF-root>/video/<anime-title>/`.
  - **Expected:** Subtitle files named `.subtitle_E00001_english_0.srt`,
    `.subtitle_E00001_japanese_1.srt` (language in the filename).
  - **Pre-fix:** they were `.subtitle_E00001_0.srt`, `.subtitle_E00001_1.srt`
    (no language).
  - **Note:** "unknown" in place of the language means the track had no `lang`
    label from the source — not a bug, just unlabeled.

- [ ] **C3 — Subtitles appear in the offline subtitle picker.** Play the
  downloaded episode (online or offline) → open the subtitle sheet (caption icon).
  - **Expected:** The picker shows **"English"** / **"Japanese"** (the actual
    language labels), NOT "Subtitle 1" / "Subtitle 2".
  - **Pre-fix:** showed "Subtitle 1" / "Subtitle 2" (or nothing at all, because
    `subtitleUris` was null).
  - **If shows "Subtitle N":** either the filename is legacy (pre-fix download —
    re-download to get the new naming), or the lang parse failed (check the
    filename in the folder).

- [ ] **C4 — Subtitles actually display.** In the subtitle picker, select a
  language.
  - **Expected:** Subtitles render on the video. Switch to the other language →
    it switches.
  - **Logcat:** `sub-add <local-path>` (tag:Anikuta:Core:Player:Subtitles or
    PlayerObserver).

- [ ] **C5 — Subtitles survive an app reinstall (the scanner fix).** This is the
  big one. After confirming C1-C4 work:
  1. Uninstall the app (or clear app data).
  2. Reinstall the CI APK.
  3. Open the app, grant the SAF permission again (first-run setup).
  4. Go to the Downloads page.
  - **Expected:** The previously-downloaded episodes re-appear (the
    `DownloadScanner` re-discovers them from the SAF folder). The subtitles are
    STILL listed (the scanner re-finds the `.subtitle_E*` files + repopulates
    `subtitleUris`).
  - **Pre-fix:** episodes re-appeared but subtitles were GONE (scanner hard-coded
    `subtitleUris = emptyList()`).
  - **Verify:** play a re-scanned episode offline → subtitles still work (C4).
  - **Logcat on scan:** look for the scan report (`scannedAt=...,
    episodesRegistered=N`).

---

## D. Pause / Resume / Cancel

- [ ] **D1 — Pause a downloading episode.** While a download is in progress, tap
  the pause button on the download card.
  - **Expected:** Status flips to "Paused", progress freezes.

- [ ] **D2 — Resume a paused download.** Tap resume.
  - **Expected:** Download resumes from where it paused (Range-resume — not from
    0%). Progress continues.

- [ ] **D3 — Cancel + delete a download.** While downloading or after complete,
  tap delete on the download card (or the episode's delete in details).
  - **Expected:** The download is removed from the queue, the file is deleted
    from the SAF folder, the episode shows as "Not downloaded" again.

- [ ] **D4 — Pause all / Resume all.** Use the bulk action on the Downloads page.
  - **Expected:** All active downloads pause/resume.

---

## E. Foreground Service + Notifications

- [ ] **E1 — Foreground notification appears during download.** While a download
  is active, check the system notification shade.
  - **Expected:** A persistent notification (the foreground service) with the
    anime title + progress. The notification can't be swiped away while
    downloading.

- [ ] **E2 — Download complete notification.** When a download finishes.
  - **Expected:** A separate "Download complete" notification (second channel).

- [ ] **E3 — Auto-pause on network loss.** While downloading, turn on airplane
  mode (or disconnect Wi-Fi).
  - **Expected:** The download auto-pauses (NetworkCallback). No crash. When
    network returns, it auto-resumes.

- [ ] **E4 — Service survives screen-off / app backgrounding.** Start a download,
  then lock the screen / background the app for 30s.
  - **Expected:** Download continues (foreground service keeps it alive). No
    "download stalled" / no ANR.

- [ ] **E5 — Service restarts on task-removal (swipe-away).** Start a download,
  then swipe the app away from recents.
  - **Expected:** The service restarts + the download continues (onTaskRemoved
    restart logic). Check logcat for the restart.

---

## F. Auto-Download Engine

> Only if auto-download is enabled in Settings → Download settings.

- [ ] **F1 — Auto-download triggers.** Open an anime with a linked source +
  auto-download ON. New episodes (or the configured count) should auto-download.
  - **Expected:** Episodes start downloading automatically (the 5-step engine:
    flatten → rank → applyFallbacks → pick → globalFallback).

- [ ] **F2 — Auto-download respects preferences.** In Download settings, set a
  preferred quality (e.g. 360p) + audio (e.g. HSUB). Trigger an auto-download.
  - **Expected:** The picked video matches the preferences (360p + HSUB).

---

## G. Settings UI

- [ ] **G1 — Download settings page.** More → Settings → Download settings.
  - **Expected:** 7 sections render. Drag-reorderable lists for priority /
    quality / audio / server work (drag an item, it moves).

- [ ] **G2 — SAF folder picker (first-run).** If not already set, the first-run
  setup dialog prompts for a SAF folder.
  - **Expected:** Picker opens, folder is selected + remembered. Downloads go
    to the chosen folder.

---

## H. Edge Cases + Error Handling

- [ ] **H1 — Download an episode whose video URL is a localhost proxy.** (AniKotoS
  source.) Download should still work (the direct CDN URL is preferred via
  `directUrl`; if only the proxy URL is available, the download works but is
  vulnerable to proxy churn — see H2).
  - **Expected:** Download completes.
  - **Logcat:** if URL starts with `http://localhost`/`127.0.0.1`, a warning:
    "Download depends on extension proxy server — may fail if the proxy is killed."

- [ ] **H2 — Proxy churn (KNOWN GAP — not a fail).** Start downloading a
  localhost-proxy episode, then open ANOTHER anime from the SAME source (this
  triggers a second `getHosterList` which kills the first proxy).
  - **Expected (current):** the in-flight download fails with a "Proxy URL died"
    error → status ERROR. Manual retry (tap retry) recovers it.
  - **NOT expected (future fix):** automatic re-resolve + retry. That's the
    deferred proxy-churn wiring (D-149, D-151) — NOT implemented yet. If the
    download recovers automatically, that's a surprise (report it). If it errors,
    that's the known behavior — retry manually.

- [ ] **H3 — Download fails (network error / 5xx).** Simulate by turning off
  network mid-download (different from E3 — here we test the ERROR state).
  - **Expected:** Status flips to ERROR with the error message. Manual retry
    works. (Automatic retry is NOT implemented — D-151 future phase.)

- [ ] **H4 — Re-download the same episode.** After a download completes (or is
  deleted), download the same episode again.
  - **Expected:** The old file is replaced (no duplicate). Subtitle files with
    the same name are deleted + re-written.

- [ ] **H5 — Download an episode with NO subtitles.** Some episodes have no
  subtitle tracks.
  - **Expected:** Download completes, `+ 0 subtitle(s)` in the log. The offline
    subtitle picker shows nothing (or just "Off"). No crash.

---

## I. Crash Handling

- [ ] **I1 — No crashes during any of the above.** If ANYTHING crashes, you'll
  see the `ErrorActivity` (CORE_RULES §29) with a copyable crash log. Copy it
  + report back.
  - **Common crash causes (should NOT happen):** DB schema migration on upgrade
    (DL-CRASH-FIX), Toast on main thread (DL-CRASH-FIX3), MPV SIGABRT on fd://
    (DL-CRITICAL-FIX3), metadata disappearing (METADATA-FIX-v2).

---

## How to report back

For each section (A-I), give a one-line summary: e.g. "A: all ✅" or "C: C1 ✅,
C2 ❌ (subtitles named without language)". For any ❌, grab the logcat lines
matching the filter above + note the exact step + what you saw vs expected.

**Priority if something fails:**
1. **C (subtitles)** — this is the focus of D-FIX-SUB. If C1 fails (0 subtitles
   download), that's the most important to report (the header fix may need
   adjustment for your specific source).
2. **B (offline playback)** — core functionality.
3. **E (foreground service)** — affects reliability.
4. Everything else.

**Known gaps (NOT failures if they don't work):**
- H2 proxy-churn auto-recovery (deferred, D-149/D-151).
- H3 automatic retry loop (deferred, D-151).
- The `DownloadVideoPickerSheet` for the ASK-fallback case (rarely hit; the
  ResolverSheet path covers normal downloads).
