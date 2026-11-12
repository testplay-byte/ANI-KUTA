# 06 — BACKUP & RESTORE + THE PROFILE

## 1. The .anikuta backup system (rounds 126-137)

- **Format**: a ZIP of the whole SQLDelight dump (per-table JSON rows) + the full settings
  layer (the prefs file) + the optional cover/banner images + the extension APKs + the CS
  plugins + the avatar.
- **BackupManager.createBackup(options, onProgress)** — the what-to-include selector
  (Library/History/Tracking/Downloads/Settings/Extensions buckets), the offline-image
  budgets (per-image + total + wall-clock), the SAF target + the newest-3 prune.
- **restoreBackup(uri, extensionInstallPicker)** — MERGE-ONLY (INSERT OR IGNORE, nothing on
  the device is overwritten or deleted), ONE transaction for the DB, the parents-before-
  children order, the extension batch-install picker (D-844), the trust re-application, the
  avatar re-pointed to a device-local file.
- **The cross-app interop** (round 137): import FROM Aniyomi/Anikku (.tachibk/.anikku proto),
  AniList (the GraphQL + the GDPR data.json), CloudStream (the JSON backup) — additive-only,
  the enrichment fetches titles/covers; export TO those formats + a .txt list.
- **The known sharp edges** (09-KNOWN-ISSUES): the whole archive materializes in RAM
  (twice) — a large library can OOM on low-RAM devices; the ad-system prefs ride the backup
  (a donor's alwaysSponsor flag can cross over); the category id-collision mis-attaches
  restored items to a same-id category.

## 2. The Profile's tracking surfaces

- **Watch flow**: the LAST 7 DAYS, today rightmost — minutes from the SESSION LEDGER; the
  day sheet lists the day's EPISODES derived from the same ledger (D-862 — bar and list
  agree by construction; the pre-137 legacy days fall back to the progress bucketing).
- **Time DNA**: the morning/afternoon/evening/night split + the peak period label.
- **Streak**: honest local-day math (today-pending doesn't reset it).
- **Heatmap**: the minutes per local-midnight day.
- **Recently watched + the timeline + the genre sheet**: ALL rows carry mainId
  (D-863) — they open through MainActivity.openDetailsByMainId (removed-from-library and
  extension-only content open; the unresolvable extension toasts). The recently-watched +
  the day-sheet rows also pass the episode number → the details page SCROLLS TO and
  HIGHLIGHTS it (targetEpisode).
- **The data sources**: watch_progress (the episode rows) + watch_session (the day ledger) +
  the content tables (the titles/covers via mainId) — the enrichment tolerates a missing
  main_entry (the fallback title).

## 3. The tracking-survival contract (D-862, schema v4)

- watch_progress + watch_session have NO FK to main_entry (3.sqm rebuilt them) — the rows
  survive content removal AND extension deletion.
- Library removal deletes ONLY library_item (verified — the cleanup writers
  clearByMainId/deleteMainEntry/deleteContentDetails are dead code, zero callers).
- The History "Clear all" is the ONLY live wipe (progress + sessions together).
- The user-marked-watched (CF1): sticky, suppresses the auto-mark; the bulk mark-all
  never counts as "organic" for the episode counts.
