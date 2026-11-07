# Round 139 — The Profile-Top Root Cause, The Backup/Restore UX Round, And The AniList GDPR Import

**Date:** 2026-11-07 · **Commit:** 215172de · **CI:** Build APK 42348945771

The v1.1.82 full-fledged device report landed with precise, actionable detail — the most important
being the shared `Downloads/gdpr_data.json` (AniList's GDPR export) that exposed why our AniList
import "was not handled that well".

## 1. The profile top — THE root cause (D-838)
The report finally pinned the symptom: title+gear NOT at the very top, tabs NOT right below the
title, "a lot of padding" — while the scroll behavior itself was already right. A dedicated
investigation (insets, edge-to-edge, every padding in the chain) found the actual bug:

**The header's mini Stats/Timeline pill, at its at-rest 0-dp width, wrapped its text ONE GRAPHEME
PER LINE at the inherited 24sp body line height — a ~204dp-tall invisible column.** The header Row
(centered) inflated to ~204dp: title+gear sat ~90dp below the status bar ("not showing on the very
top part"), the same explosion pushed the list down ("the stats and timeline options were not just
below the My Profile — a lot of padding"), and mid-scroll the row SNAPPED 204→36dp the moment the
animating width first fit "Timeline" — the "moves up when I scroll slightly" jump. This is why
rounds 128/129/135/138 (each shaving 2-10dp) never cured it.

**Fix (design untouched):** `maxLines = 1`, `softWrap = false`, explicit `lineHeight = 12.sp` on the
pill texts + `heightIn(max = 26.dp)` on the pill Surface. The header row is now 36dp at every width;
the pill's slide is smooth (no mid-scroll snap); title+gear sit flush at the top; the tabs band is
0dp below the header. The window was confirmed edge-to-edge with `statusBarsPadding()` the sole
inset consumer (no double-inset).

## 2. QuickStats (D-839)
The four stat cards were `weight(1f)` equal-width — "Score" got the same space as a wide "3d 12h"
Time value, which line-broke (day on line 1, hours on line 2). Now: `SpaceEvenly` + wrap-content
cards (dynamic widths) + `maxLines=1, softWrap=false` on both value and label — a line break is
structurally impossible.

## 3. The backup page (D-840 + the popup cleanup)
- The "Episode thumbnail images" description line in the what-to-include popup is REMOVED.
- **The live backup progress:** `createBackup` carries an `onProgress(BackupProgress)` feed —
  friendly per-table stages ("Library entries 3/20", "Watch history", "Settings", "Cover images",
  "Extensions", "Offline covers") via a new `tableLabel` map. The UI shows a non-cancellable
  "Backing up…" dialog with a determinate bar (indeterminate for unbounded stages) — the report's
  "what is currently being backed up, what is currently being processed".

## 4. Existing backups — tap → details → restore (D-841)
`listBackups()` now lists EVERYTHING in the backup folder (the native `.anikuta` archives AND the
interop exports beside them), newest first. Every row is tappable → a popup that reads the REAL
file: a `.anikuta` shows its manifest stats (created, app version, entries/categories/history/
tracking/downloads, covers, extensions) + a Restore button (native restore, no detour to the
restore page); a foreign file shows a confirm and restores through the interop path. The prune only
counts NATIVE archives against the keep-count (foreign exports are never auto-deleted). The listing
is live from the folder — a fresh install sees every backup already there.

## 5. The restore page top (D-842)
The big hero block is gone. The top is now a compact card: **"Supported backups"** + ONE line —
"Supports a wide variety of backups — .anikuta, .tachibk, .json" (extensions only, no app names, no
.duplicate .anikku mention, no manga note) — followed by a proper full-width **"Restore from backup
file"** button (spinner states inline). The detection/preview/confirm flows below are unchanged.

## 6. The AniList GDPR import (D-843)
The shared `gdpr_data.json` is AniList's GDPR export: `{"user": {...}, "lists": [FLAT entries]}` —
each entry has `series_id` (the media id), `series_type` (0=ANIME, 1=MANGA), int `status`
(1=CURRENT 2=COMPLETED 3=PAUSED 4=DROPPED 5=PLANNING 6=REPEATING), raw `score` (the file's
score_type=4 = POINT_100), `progress`, `started_on`/`finished_on` (Int YYYYMMDD) — and NO media
objects at all. The old parser (MediaListCollection-shaped) silently skipped every entry to zero —
exactly the "not implemented properly" the user hit.
- **Detection:** `user` object + a flat `lists` array whose first element carries `series_id` —
  checked BEFORE the collection branches.
- **Import:** anime entries (manga skipped + counted) → resolveOrCreateForAniList with a
  "AniList <id>" placeholder title; tracking rows with wire-int statuses (1→11, 5→15, 6→16 …),
  RAW scores (no heuristic scaling — the GDPR score is already 0-100), start/finish dates.
- **Enrichment:** the bridge fetches titles + covers from the PUBLIC AniList GraphQL (50-id
  batches, one shared client) FIRE-AND-FORGET — the restore result returns immediately; offline it
  gives up silently (the plan's warning says "titles and covers are fetched from AniList — go
  online for them to appear").

## 7. The extension batch-install picker (D-844)
`restoreBackup` now takes a suspend `extensionInstallPicker`: the missing extensions (not
installed + APK-carrying) are collected BEFORE any prompt and offered to the user as a checkbox
dialog — all checked by default, name + version rows, **Install** (the confirmed subset, each still
getting its own Android install prompt) or **Skip** (declined count logged). Already-installed
extensions still just re-align trust. Without a picker the legacy install-all flow runs.

## 8. The Export-as popup (D-845)
Exactly the three options the report listed — **Aniyomi, CloudStream, and a NEW .txt** — nothing
more (Anikku/AniList hidden). The picker itself was rebuilt: icon-chip cards with name + half-line
description + chevron. The **.txt** format is the names-only list: `ANI-KUTA Library — <date>` /
`N titles` / `== Watching (n) ==` / `== Completed (n) ==` / `== Plan to watch (n) ==`, names
alphabetical within each watch-state group (completed episode → Completed; any playback → Watching;
else Plan to watch). Export-only.

## Verification + CI
Local `assembleDebug` GREEN before push; three incremental compile fixes during the round
(a deprecated `MutableList.sort` → `sortWith`, the private-in-file `DetailRow` shared as
`internal`, the missing format imports). CI: Build APK **42348945771 — GREEN**.

## The release (D-846)
The user's explicit order ("ok do the debug release too"). Routine A on fed4fc4d:
release/1.1.83, ONE bump commit (10120/1.1.20 → 10183/1.1.83, 4507e760), branch + annotated
tag v1.1.83 (the full user-facing bullet body) → **Release APK run 42374686371 — GREEN**.
Verified live: stable-latest "ANI-KUTA v1.1.83 (round 139)",
ani-kuta-v1.1.83-debug-arm64-v8a.apk (64,017,337 bytes), the downloaded SHA256
(a26b8ca699417a88afdbbe36069fc5fab5300ae5ec87e9b286134a647eb45e08) matches SHA256SUMS.txt.
v1.1.82 installs get the in-app offer carrying round 139.

## Documented observations
- The GDPR enrichment is one-shot per import; a future "refresh metadata from AniList" action on
  placeholder entries would heal offline imports without re-importing.
- The interop exports share the backup folder with native archives; if the folder grows messy, a
  per-format filter row on the existing-backups card would help.

---

# Round 140 (continuation) — The Profile Polish, The UX Fixes, And The Downloads-Extension Reconciliation

**Date:** 2026-11-07 · **Commit:** 244ce4e8 · **CI:** Build APK 42391962062 GREEN + Release APK 42392689188 GREEN (v1.1.84)

The v1.1.83 device report: the profile is *almost* there (spacing between the heading and the tabs, a bigger title, a cleaner band exit); the unrecognized-file message must name extensions only; the foreign-restore result should show the full breakdown; long-press on an existing backup should offer DELETE; and the big one — downloaded episodes lose their state on the details page after the auto-link and play the network instead of the local file.

**Profile (D-847):** title 28sp + a 6dp heading bottom pad + the band exit as a pure fade + layout collapse.

**Messages + result card:** both unrecognized rejects now say "the supported extensions are .anikuta, .tachibk, .json" (no app names); the foreign-restore card ALWAYS shows "Added to library" and "Categories created" (0 is an honest value).

**Long-press delete (D-848):** combinedClickable on the existing-backups rows → the confirm dialog → BackupManager.deleteBackup → the list refreshes.

**The downloads-extension reconciliation (D-849)** — a full investigation (the Downloads→Details hand-off, the linking paths, the state matching, the play gate, the D-540 identity sync, the episode cache) found FOUR root causes, all fixed:
1. **RC1 the key-namespace change** — the download state and the local-file lookup are exact-matches on the download-time source's episode URLs; after a link they miss. Fix: NUMBER-ALIAS keys (`$mainId|#<number>`) published by DefaultDownloadManager + number fallbacks in downloadStateFor, downloadedEpisodeUriFor (the play gate) and the player's episode switch (SQL epsilon ±0.01).
2. **RC2 the identity hand-off** — `sourceId ?: 0L` could mint a NEW mainId and orphan the downloads. Fix: fall back to main_entry's own extension identity.
3. **RC3 the D-238 cache wipes** — removed on link AND unlink (the metadata-preservation rule; the COALESCE merges were already 'keep old until the new source provides it').
4. **RC4 the episode_url nulling** — all three provider-metadata write sites preserve the stored URL (an existingUrls map).

Local assembleDebug GREEN before push. CI ledger: 2/2 (Build 42391962062 + Release 42392689188 — v1.1.84 verified live, checksum 18363ea5…).