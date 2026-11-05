# Round 137 — The Cross-App Backup Interop, The Real Watch Time, And The Profile Rebuild

**Date:** 2026-11-05 · **Commit:** 22fed1e0 · **CI:** Build APK run 42071878651 (see the ledger note at the bottom)

The v1.1.80 device report opened with "quite satisfied and quite happy" — the offline covers, the live
restore, the checkmark popup and the player OSD all landed as requested. The round-137 order was then a
one-sweep mega-round: (1) make the backup system carry everything it possibly can, (2) build REAL
cross-app backup interop — restore FROM Aniyomi / Anikku / AniList / CloudStream and export TO them,
with the local data never restructured, and (3) rebuild the tracking + My Profile page: real watch time,
a true last-7-days watch flow, an honest streak, a de-duplicated time DNA, and player-side session
recording. "Take your time… never rush anything, never skip anything."

---

## 1. The full-capability audit (the order's "first and foremost")

Two subagents mapped the whole surface first. The audit found the backup system near-complete: all 20
carried tables exist, all 6 prefs files are real, trust state + extension payloads + offline images ride
along. The genuine gaps:

- **`episode_update`** — the "New episodes" feed (with its SUB/DUB audio-variant evidence) was silently
  dropped with NO documented exclusion → added to `BACKUP_TABLES` (rides the Downloads bucket).
- **`watch_session`** — the new round-137 ledger (below) is backed up with `watch_progress`.
- **The profile avatar** — the crop editor writes `filesDir/avatar_<timestamp>.jpg` and the pref carries
  its absolute path; a restored prefs payload dangled. The file now rides as `profile/avatar.jpg` and
  restore writes a FRESH device-local file and re-points `profile_avatar_url` (after prefs apply, so the
  pref overwrite wins). `BackupManifest.includesAvatar` records it.
- The AniList OAuth token already rode the Security bucket (D-817) — confirmed, no change.

## 2. The interop engine (D-828) — new `core/backup/interop/` package

**Research (all verified against upstream sources, 2026-11-04/05, credited in the KDocs):**

| Format | File | Payload |
|---|---|---|
| **Aniyomi** | `.tachibk` | GZIP'd kotlinx-serialization **protobuf** — the upstream schema is `@Serializable` classes with `@ProtoNumber` (no .proto files; `BackupDecoder.kt` + `BackupDetector.kt`). Anime at fields 501-503, `isLegacy` at 500; legacy 0.x anime at 3/4/103. |
| **Anikku** | `.anikku` | The anime app rides the **Mihon manga wire shape** — `BackupManga`/`BackupChapter`, watch state in `seen`+`lastPageRead`, `isLegacy` 500 always written false. |
| **AniList** | `.json` | The `MediaListCollection` list-import document (wrapper, bare, and `data`-wrapped all accepted). |
| **CloudStream** | `.json` | The CS3 backup — `{datastore:{_Bool,_Int,_String,_Float,_Long,_StringSet}, settings:{…}}`; bookmarks at `result_watch_state_data/<id>`, watch state = `WatchType.internalId` INTs, resume at `result_resume_watching_2`. |

**The wire gotcha that shaped the whole writer:** kotlinx-protobuf OMITS fields equal to their declared
default. Upstream's detector reads `isLegacy=true AND field-103 non-empty` as a LEGACY file — so our
models declare `isLegacy` with default **true** and pass **false**, forcing the field to be emitted.
A false-default would have produced files Aniyomi reads as empty.

**Components:** `InteropProtoModels.kt` (the exact upstream field numbers, provenance-commented),
`InteropJsonModels.kt` (AniList + CS shapes), `InteropExporter.kt` (one library snapshot from the raw
driver → four writers), `InteropImporter.kt` (detection: JSON sniff → Aniyomi probe → Anikku probe with
the always-present `isLegacy=false` discriminator; Mihon MANGA files rejected with a specific "manga is
a later round" message), `InteropManager.kt` (SAF orchestration, honest category-delta counting),
`InteropData.kt` (the format-neutral plan + summaries + the shared tracker status maps).

**Restore semantics:** additive-only, never clobbering. Identity resolution through the app-layer
`InteropRestoreBridgeImpl`: AniList entries → `resolveOrCreateForAniList` (the detail MUST carry
`data_source_ref_id` or the AniList lookup never matches); aniyomi/anikku entries →
`resolveOrCreateForExtension` with the backup's source id (D-189 semantics); CloudStream bookmarks → the
CS-bridged id minted from `apiName` with the bridge's exact formula; an aniyomi entry carrying an AniList
tracker row gets the AniList axis LINKED onto the same mainId. Progress writes are raw full-row upserts
(the store's save() is player-shaped — completed=false + the 1-minute gate — which would mangle imported
WATCHED rows); imported seen rows stay organic (userMarked=false) so the D-248 statistics count them.
Tracking applies AniList rows (status ints 11/15/2/3/4/16 + legacy 1/5/6) and restores the tracking
INTENT (`content_tracking_state.tracked=1`).

**Export coverage (faithful, per format):** Aniyomi/Anikku carry entries with a real extension source id
(+ episodes ONLY with real cached URLs — a foreign reader would turn synthetic URLs into junk rows;
entries without cache export as uninitialized favorites, exactly how Aniyomi itself exports unfetched
entries), history (`lastRead` + `readDuration` = played ms) and AniList tracking. AniList carries
AniList-linked entries with list status/score/progress (untracked entries computed from watch state).
CloudStream carries CS-bridged entries as bookmarks (apiName reverse-hashed from the installed plugins,
mainUrl-stripped ids like upstream's minting), COMPLETED watch-state ints when every known episode is
done, and the latest-episode resume pointer. Skipped entries are counted and reported in the toast.

**UI:** the Backup page gained an "Export format" card (radio popup: ANI-KUTA / Aniyomi / Anikku /
AniList / CloudStream, persisted via `pref_backup_export_format`); the Restore page gained a "Restore
from other apps" card with an any-file picker, auto-detection, a real result panel (read/created/
matched/library/progress/tracking/pending-sources) and per-format toasts. Pickers key on GENERATION
counters (re-picking the same document can no longer stick the busy spinner — the verifier's P2-7).

## 3. The watch-session ledger (D-830)

`watch_progress.played_time_ms` (round 127) answers "how much in total" but never "how much on
Tuesday". Schema **v3** (`2.sqm`) adds **`watch_session`** — one row per CONTINUOUS watching streak:
`WatchSessionRecorder` receives every player save point's current session-played ms and derives the
delta itself (first tick after a switch baselines; a regression re-baselines — the CS stack resets its
accumulator on same-episode re-entry and sub↔dub switches; deltas ≤0 or >60s dropped), extending the
open session within a 5-minute gap (DB-side `played_ms = played_ms + :delta_ms` — no read-modify-write
race) or opening a new one. Recorders are FACTORY-scoped (per-player baselines; a shared singleton
would rebaseline across players). MPV hooks: the 10s tick (deliberately NOT gated by the 1-minute
watch-mark gate — a 40-second watch still lands on the chart) + the NonCancellable dispose flush. CS
hook: `saveProgress`'s forward delta. Both stacks feed the same ledger; the store's `clearByMainId` /
`deleteAll` now wipe it in step.

## 4. The profile rebuild (D-831)

- **Real total time** — `played_time_ms` sums (legacy completed rows fall back to durations); the
  header shows "142h watched · since Mar 3, 2026" (the report's "from where to where").
- **Watch flow = the LAST 7 DAYS**, oldest left → **today rightmost** (the report's exact order), bar
  height ∝ REAL minutes (sessions; pre-ledger days fall back to completed-episode durations until the
  ledger covers them). The meaningless y-axis numbers are GONE; the duplicate in-bar day-label row is
  GONE (one label row below the bars); "N this week" header; tapping a bar or label opens a proper
  **ModalBottomSheet** day detail (the floating sidebar clipped, jittered and trapped taps) with the
  day's minutes, count and every organic touch (per-EPISODE minutes watched THAT DAY).
- **Streak** — local-day math (the old UTC buckets), surviving "today not watched yet".
- **Time DNA** — computed ONCE in the VM from the session ledger in real minutes (fallback: organic
  episode hours); the donut center shows the user's PEAK period with its share (the old UI showed the
  wall-clock period and duplicated the bucketing).
- **Heatmap** — minutes per LOCAL-midnight day (the old map keyed UTC epoch-days against a local grid —
  off by hours); intensity thresholds in minutes; "Avg" divides by the whole watching span, not the
  active days (the old number was inflated).
- **Reactive-ish** — `refresh()` on every screen entry (the one-shot init left stale numbers after a
  watching session); batch identity maps kill the N+1 title/cover lookups; day arithmetic is
  Calendar-based end to end (fixed-24h strides drift across DST and would gray out bars/heatmap cells).

## 5. Verification

Two adversarial passes. Pass 1: 16 findings (2 P1 — CS resume pointers collected but never written;
Anikku backups misdetected as Mihon manga — 6 P2 — the CS accumulator reset eating deltas, DST day
walks, CS bookmark JSON null-omission, watch-state wire type, stuck busy flags, dead resume key — 8 P3).
All fixed; pass 2 re-verified 15/16 OK + caught the one unapplied one-liner (HIATUS wire value) and two
residuals — all three applied. Local `assembleDebug` BUILD SUCCESSFUL (13m40s) + `:core:content` /
`:core:watch-progress` unit tests GREEN before any CI run. A disk glitch NUL'd `ProfileSections.kt`
mid-round — caught by content probes, restored from git, re-applied, and every round-137 file scanned
for NUL bytes (zero).

## 6. Files

New: `core/backup/interop/` (7 files), `app/backup/InteropRestoreBridgeImpl.kt`,
`core/watch-progress/.../WatchSessionRecorder.kt`, `core/database/.../2.sqm`.
Modified: watch.sq, BackupManager/Models/Preferences/Module, ExtensionBackupBridgeImpl (+ the interop
binding), both player stacks, the backup/restore screens, the profile trio, libs.versions.toml
(+ `kotlinx-serialization-proto` 1.7.3), core/backup/build.gradle.kts.

## CI ledger

- **Run 1/2 (this cycle):** Build APK **42071878651 — GREEN** (commit 22fed1e0). Local assembleDebug
  + unit tests were GREEN first; the docs commit rides after it.
- No release was ordered this round (D-738) — mainline stays 10120/1.1.20.
