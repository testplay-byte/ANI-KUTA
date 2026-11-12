# 10 — TESTING GUIDE (the device checklist — v1.1.86 + the round-145 fixes)

> Order the tests by the risk: the WIZARD first (the C-1 hotfix — a fresh install could
> never finish it on v1.1.86), then the tracking/watch-flow, then the open-by-mainId path,
> then the carried systems.

## 1. THE WIZARD (the C-1 hotfix — THE critical test)

Fresh install (or clear the app data):
1. Launch → Welcome → Theme → the "Make it yours" gate.
2. **"Lets check them out"** → Details page (cycle the 6 presets — the preview repaints) →
   Continue → Player page (4 styles + the toggles) → Continue → Notifications (the master
   toggle → the poster layouts + artwork + the live preview) → Continue.
3. Storage (pick a folder) → **Continue → you must land on RESTORE** (on v1.1.86 this
   looped back into the personalize block — THE regression).
4. Restore (skip or restore) → Notifications → Battery → Overlay → **Finish → "Start
   watching" must land on Browse/Library and NEVER show the wizard again on relaunch**.
5. Rotation mid-wizard: returns to Welcome (KNOWN — M-7 in the register; not a blocker).

## 2. TRACKING SURVIVAL (the v4 migration)

1. Watch an episode a little → remove the show from the library → check History + the
   Profile's recently-watched: the entries REMAIN.
2. Uninstall/disable an extension → the history rows REMAIN (the data survives; opening
   them toasts "Extension X not available" — test 3.4).
3. Upgrade-install sanity: install v1.1.87 over v1.1.86 → the watch history + the profile
   charts survive (the v3→v4 migration for existing v3 installs).

## 3. OPEN-BY-MAINID + THE EPISODE SCROLL

1. History: tap a watched episode of a show STILL in the library → the details page opens
   **scrolled to that episode with the highlight border** (~2.6s).
2. History: tap an entry of a REMOVED show → the details page still opens.
3. Profile → Recently watched / the day-sheet rows / the timeline / the genre sheet: all
   open (extension-only content included).
4. Uninstalled extension → tap → "Extension X not available" toast, no crash, no blank.

## 4. WATCH FLOW (the day-sheet integrity)

1. Watch parts of 2-3 episodes across a day → Profile → tap that day's bar → the sheet
   lists EVERY episode touched with its real minutes (the phantom/missing episodes fixed).
2. A day with minutes but no episode rows (pre-upgrade legacy days) shows the legacy
   fallback.

## 5. TORRENT (carried — re-test on the fresh install path)

1. Settings → Torrent → the disclaimer → enable.
2. A Nyaa-based CS source → the resolve sheet shows "Torrent" rows → pick one →
   "Preparing torrent…" → playback + the status line (seeders/peers/speed/%).
3. Close the watch screen → the torrent is forgotten (no continued bandwidth).
4. Torrent OFF → the torrent links hide again (byte-identical old behavior).

## 6. THE CARRIED SYSTEMS (the regression sweep)

- **Player**: gestures (volume/brightness/seek), pinch zoom, cinema mode, Up Next (the last
  10s), AniSkip, resume (15s rewind), the failed-episode rules.
- **CS player**: the resolve sheet, the sub/dub switcher, the quality drawer, the torrent
  seam.
- **Downloads**: start/pause/resume/cancel/delete, offline playback, the reorder drag.
- **Backup/restore**: create → wipe → restore (the merge-only contract; the covers paint
  offline; the extensions reinstall trusted).
- **Notifications**: the banner (the poster layout), the check live card + the minimal
  result ("N new episodes found!" + the cover; the tap → Updates).
- **Ads**: no sponsor popup for the first 3 days of a fresh install (set the clock +4 days
  to verify the expiry); the popup appears on the detail-page entry afterwards.
- **Permissions hub**: every row's live state + Allow + App-info.

## 7. The rule of the round

Anything that fails: note the STEP + the exact behavior (a screenshot helps) — the register
(09-KNOWN-ISSUES.md) is the intake.
