# 116 · ROUND 134 — THE PLAYER POLISH (GESTURES/OSD/FLIP) AND THE BACKUP & RESTORE SYSTEM

> 2026-11-01. The round-133 device report — satisfaction + a fix list for the
> player, and then the big new order: **backup & restore** ("a very key
> essential part of the application"), with automatic backups, a own format,
> and careful planning. Two phases, two commits. Decisions D-816..D-819 —
> the user then ordered the **v1.1.78 debug release** (§4), which carries
> both phases.

## 1. Phase A — the player polish (D-818, commit `bda4f046`)

Every item from the report:

- **Gesture exclusivity.** The axis is decided ONCE at touch-slop lock and
  every later event routes by the LOCKED axis — drifting to the other side
  or axis can never start a second gesture ("if the user firstly started
  with the brightness gesture, then even if the user goes to the right side…
  it would only do the gesture which was initiated"). The old code
  re-evaluated the axis per event and literally ran seek math under a live
  volume gesture.
- **The OSD redesign — no background panels.** The icon and the percentage
  float on the video with glyph drop-shadows (a stacked dark copy behind the
  icon, a text shadow style) so they read on any frame, white included.
  Volume shows [%][icon]; brightness shows [icon][%]; the seek OSD reads
  left→right as [delta][arrows][target]. The level bar + the label are
  ANIMATED (tween 110) — the "20 to 70" jump is gone — and MAX now means the
  gesture's real cap (volume-max aware, not a hardcoded 100).
- **The sensitivity fix.** The vertical gestures' full range is a fixed
  ~600dp of travel — the short portrait 16:9 box no longer makes a tiny
  swipe swing the brightness wildly.
- **The seek-suppression made reactive.** `activeKind` is a Compose state —
  the center play/±10s buttons hide the MOMENT a seek starts (the plain var
  never recomposed), and a `try/finally` cancels the state if the detector
  node dies mid-drag (the verifier's P2).
- **Cinema.** The SYSTEM status bar hides (InsetsController, restored on
  exit — the swipe still transiently reveals it); the non-OLED background is
  the player page's NORMAL background color (not the accent).
- **The flip transition.** Portrait↔landscape plays a black dip + expand
  (a SurfaceView can't fade itself — the scrim masks the relayout cut while
  the player box expands from ~0.93 to full over ~340ms). No flash on first
  open; silent for cinema toggles.
- **Zoom.** Reaches 5x, zooms OUT to 0.5x, magnet steps
  [0.5, 0.75, 1, 1.25, 1.5, 2, 3, 4, 5] — 4x no longer snaps back to 3x —
  and the zoom chip's auto-hide runs off a tick counter so it always
  dismisses (the old boolean key never restarted its timer).
- **The episode pill.** The fullscreen top-left details follow the LIVE
  episode number (prev/next switches update them; watchKey was frozen at
  navigation).

## 2. Phase B — the backup & restore system (D-816, D-817, commit `cd2ca8c3`)

**The format (D-816).** A `.anikuta` backup is a ZIP:
`manifest.json` + `db/<table>.json` + `prefs/<file>.json` +
optional `images/<mainId>.jpg`. The DB payload is dumped/restored
GENERICALLY through the raw SqlDriver (columns read from PRAGMA at runtime)
— no hand-written codecs, and the format tracks schema changes in both
directions. 20 tables ride in restore order (parents before children —
`foreign_keys` is ON): the library, watch history + progress, ratings,
tracking (bindings + the opt-in state), the settings mirror, the downloaded
episodes' metadata, genres, per-content notification configs, the update
engine state, the airing schedule, and the episode-details/browse caches —
the text stuff (synopses, episode details, the AniList link, the search/browse
artwork links) is ALWAYS carried, exactly as ordered. Deliberately out:
`download_queue` (transient), `notification_sent` (the dedup log),
`activity_event` (analytics), the playback byte-cache index.

**The prefs payload (D-816).** The FULL SharedPreferences layer:
`anikuta_prefs` (every setting + the AniList OAuth token), the
extension/CloudStream repo + plugin registries, the CS source memory, the
plugin key/value store, and the dynamic per-source `source_<id>` files.

**The images payload (optional).** The downloaded series' `.cover.jpg`
files — matched via each folder's `.data.json` mainId, copied back on
restore when the folder still exists. (Episode thumbnails are URL strings in
the DB — they ride automatically; the settings text says so.)

**The semantics — the verifier's P0 save.** The first restore design used
`INSERT OR REPLACE` — with `foreign_keys = ON`, replacing a `main_entry` or
`library_category` CASCADE-DELETES local rows newer than the backup: a
"restore" would have silently destroyed everything added since the backup
while the UI promised the opposite. The shipped semantics are an honest
merge: **`INSERT OR IGNORE` with per-row column lists** — existing data is
never overwritten or deleted; a restore only ADDS what is missing; and a
column absent from an older backup falls back to its schema DEFAULT (old
backups restore on newer schemas). The DB payload lands in ONE
`BEGIN IMMEDIATE` transaction, parents before children.

**The mechanics.** The archive is assembled in a cacheDir temp and copied in
one truncating `openOutputStream(uri, "wt")` (the D-404 contract) into
`<selected folder>/backup/anikuta-backup-<UTC-timestamp>.anikuta`; the prune
keeps the newest 3 (the filename's timestamp is the order key — SAF
lastModified is unreliable); a failed copy deletes its target. The dump runs
inside a read transaction (a mid-backup write cannot orphan a parent). The
SAF folder URI + the app-update bookkeeping are excluded from restores.

**The automatic backup (D-817).** A WorkManager periodic worker — 6h / 12h /
daily / weekly, battery-not-low, `ExistingPeriodicWorkPolicy.UPDATE` (the
anchor never resets on app open) — rescheduled from `AnikutaApp.onCreate`
and on any setting change; disabled or folder-less cancels it.

**The page (D-817).** Settings → **Backup & restore**: manual "Back up now"
(with the live row/image counts), the "include cover images" switch, the
automatic-backup switch + interval picker, the restore picker with a
confirmation dialog and a result summary (restart recommended), and the
existing-backups list with sizes and dates.

## 3. The verification

Two adversarial passes. The player pass accepted 1 P2 + 5 P3s (all fixed:
the mid-drag state leak, the OSD return after the timeout, the dp-scaled
icon shadow, the measure-order before the flip transform, the MAX-at-cap).
The backup pass accepted **8 findings — the P0 restore-cascade first among
them** — plus the read-snapshot, the folder-URI exclusion, the name-ordered
prune, the failed-copy cleanup, and the honest merge copy. One disclosed
limit: the restore holds the DB's single connection in a transaction for its
duration — a concurrent writer on another thread may throw and retry later
(merge semantics make the consequence benign); a true writer-quiesce window
is future work. CI ledger this cycle: 2 runs (the Build APK for each phase);
no release ordered.

## 4. The v1.1.78 release (D-819)

The user ordered the release right after the round-134 read-out. Routine A
on the round-134 green head (`1ffd2a95`): release branch `release/1.1.78`,
ONE bump commit (10120/1.1.20 -> **10178 / 1.1.78**), branch + annotated tag
`v1.1.78` -> the Release APK workflow (run 41305214485, green first-try).
Verified live: the APK's SHA256 matches the release's SHA256SUMS.txt and the
manifest carries 10178/1.1.78 with the arm64 libs. v1.1.77 installs get the
in-app update offer carrying round 134. CI ledger: 3 runs this cycle (2x
Build APK for the two phases + 1x Release APK) — over the D-472 2-run
budget, disclosed: the two-phase scope + the release order.
