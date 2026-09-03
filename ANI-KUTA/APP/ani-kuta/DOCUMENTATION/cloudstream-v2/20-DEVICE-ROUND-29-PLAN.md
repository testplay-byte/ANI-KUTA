# DEVICE ROUND 29 — PLAN (v0.4.16 device report → v0.4.17)

The v0.4.16 device report, decomposed. Every item below was investigated from the
pinned sources before a line was written; root causes first, fixes second.

---

## Item A (D-404) — THE data.json DELETION INTEGRITY SYSTEM (critical)

### The report
- Download 2 fresh episodes → folder, `episodes/` files, well-formatted data.json — all correct.
- Delete episode 1 of 2 → the episode FILE dies correctly, but **data.json is "corrupted"
  / not updated** — the deleted episode's entry survives.
- Delete the last remaining episode → **nothing** dies: folder stays, data.json stays,
  the episode file stays.
- Historical invariant: the 1-episode case has been perfect in EVERY round; the 2+ case
  has failed in EVERY round.

### Root cause (finally isolated — one primitive, three symptoms)
`DownloadStorageProvider.copyFile` opens the SAF target with
`contentResolver.openOutputStream(uri, "w")`. On AOSP `ExternalStorageProvider`
(`FileSystemProvider.openDocument` → `ParcelFileDescriptor.parseMode`) mode `"w"` =
`MODE_WRITE_ONLY` **without `MODE_TRUNCATE`** — only `"wt"` truncates. Every
`.data.json` write that SHRINKS the content therefore lands as
`new-json-head + old-json-tail` on a provider that honors raw modes:

1. **Downloads never showed the bug** — an upsert (1→2 entries) makes the file LONGER;
   the write fully covers the old bytes. The user's after-download inspection was
   correct — as reported.
2. **Delete 1-of-2 corrupts the file** — 2 entries → 1 entry is SHORTER; the write
   leaves the old tail behind → invalid JSON (`"corrupted"`, the user's word). The
   episode file still dies (the manager proceeds past a failed `.data.json` phase by
   design) and the DB row dies → the UI looks right while the durable file is now
   garbage.
3. **The retry ladder can never repair it** — every attempt (including the round-28
   "nuclear" delete+recreate) starts with `readDataJsonIndexed`, and a corrupted file
   parses to `null` → early `return false` BEFORE the nuclear branch. The corruption
   is permanent.
4. **Delete-last-of-2 does nothing on disk** — `findContentFolder` skips folders whose
   data.json does not parse; with the file corrupted the folder is INVISIBLE →
   `contentDir == null` → Phases 2–4 all skipped → only the DB row dies. The user
   sees: folder kept, data.json kept, file kept.
5. **Why 1-episode deletes always looked perfect** — the last-episode folder cleanup
   (`deleteContentFolder` Safety 4) proceeds even when data.json is unreadable, so the
   whole folder — corrupted json included — is removed. A false positive that masked
   the primitive for four rounds (D-241 → D-242 → D-392 → D-393 → D-401 all patched
   AROUND the write mode).

### The fix (user's spec verbatim: "update the data.json file of that specific content
and then it will delete that content properly afterwards")

**A1 — the write primitive.** `copyFile` → mode `"wt"` (truncate). Same for the cover
write. After every `.data.json` write, verify the target's byte length equals the
payload length (a loud ERROR if any provider ever disagrees again).

**A2 — salvage repair (self-heal).** New pure `DataJsonRepair.salvageCompleteJsonHead`:
a balanced-brace scanner (string/escape aware) extracts the first COMPLETE top-level
object from a corrupted file. `readDataJsonIndexed` falls back to it on parse failure
+ logs WARN. This single hook heals the user's CURRENT on-disk corruption at every
entry point (startup scan, folder locate, delete) — the exact file their v0.4.16 test
left behind (head = the 1-entry json the delete wrote, tail = old garbage).

**A3 — DB-truth rewrite (no more match-and-pray).** The delete flow no longer does a
read-modify-write keyed on episodeKey matching. It REBUILDS the episodes list from the
DB (rows-for-anime minus the row being deleted — metadata enriched from the existing
json entries when readable) and writes it via a new verified ladder
`rewriteDataJsonEpisodes`: `"wt"` write → strict re-read verify (parse + episodes set
equality by key) → settle → retry → nuclear delete+recreate → verify. Kills the entire
class of key-drift / ghost-entry / idempotent-no-op bugs: the file's episodes list
becomes exactly what the DB says should survive.

**A4 — folder locate fallback.** `findContentFolderByTitle` (sanitized title match) as
the delete flow's fallback when the mainId walk finds nothing (a folder whose json is
DESTROYED, not salvageable) — the rebuild then recreates a fresh valid json.

**A5 — manager Phase 2** reordered to: DB capture (row + all rows for the anime) →
locate (mainId → title fallback) → lenient read + entry capture (for URI deletes,
key→number drift via `DeletionMatching.matchRemoval`) → **REBUILD + VERIFY the json
FIRST** → URI deletes → disk sweep → folder cleanup (last episode) → DB row LAST.

**A6 — tests.** `DataJsonRepairTest` (salvage table + rebuild table, incl. the exact
device scenarios: shrink-write corruption, ghost entries, key drift, heal-from-DB).
`DeletionMatching` keeps `matchRemoval` (+ tests); `removalVerified` is removed
(superseded by the exact-set verification — dead code out).

---

## Item B (D-405) — Onboarding wizard v2

The v0.4.16 wizard structure is kept (step machine, ON_RESUME re-verification, skip
contract, no-folder gate at download time). Redesigned per the report:

- **B1 Welcome background** — REPLACE the 6 radial-gradient glow blobs + 28 rising
  bubble-particles with large ANIMATED MORPHING BLOB SHAPES (blob-path geometry with
  animated control points, drifting + morphing continuously) in a richer, deeper color
  set. Still one `drawBehind` pass, zero recomposition per frame.
- **B2** — remove the bottom "offline-first anime streaming" line entirely.
- **B3 Tagline rotator** — the tagline smoothly rotates (fade + slide) through:
  "Your anime, your rules." / "Your content, your rules." / "I don't make any
  promises." / "Don't expect anything." / "It is what it is."
- **B4 Theme step** — a horizontal SNAP CAROUSEL of large live-preview theme cards
  (scroll left/right; the theme applies LIVE as the snap settles; tap also snaps +
  applies) + a System/Light/Dark mode row + the note "You can further customize in
  Settings later."
- **B5 Folder step** — a big folder icon top-center; ONE combined bottom button that
  reads "Skip for now" (nothing selected) / "Continue" (folder verified). Same
  combined-button pattern on the notification + battery steps.
- **B6/B7** — once granted, the Allow button is REPLACED by a granted state (no
  dead buttons).
- **B8 Finish step** — a cleaner granted/skipped summary.
- **B9 Browse preload DURING the wizard** — while the user works through the steps,
  the Browse data (trending/popular/top-rated sections, cache-first exactly as
  `BrowseViewModel` will read them) is fetched + every cover is prefetched into Coil
  at the exact render sizes → "Start watching" lands on a fully materialized Browse.

## Item C (D-406) — smoothness audit
Wizard step transitions + Browse entry path reviewed; findings + polish applied
(no blanket rewrites of screens outside the report's scope).

## Release
0.4.17 / build 82, CI green, tag + Release APK, docs/progress/SESSION, ntfy.

---

## AS-BUILT (delivered in this round)

- **A → commit 135241df (D-404):** exactly per plan (A1–A6). The "wt" fix covers copyFile + the cover write; the
  byte-length check lives in writeDataJsonRaw; salvage is wired into readDataJsonIndexed with a `salvage: Boolean`
  default-true param (the verified ladder's re-read passes false — the file itself must be CLEAN); the manager's
  Phase 2b rebuilds from DB rows and writes via `rewriteDataJsonEpisodes` (the new 3-attempt ladder replacing
  `removeEpisodeFromDataJson`, which is deleted along with `DeletionMatching.removalVerified` — superseded by
  `DataJsonRepair.episodesEqual`); `findContentFolderByTitle` guards against same-title-different-mainId folders and
  is wired into BOTH the per-episode and the delete-all locate (delete-all's rows hoisted above the locate for the
  title). DataJsonRepairTest: 17 tests (salvage, rebuild incl. the exact device scenarios, strict equality);
  DeletionMatchingTest slimmed to matchRemoval. CI round 1 caught exactly one bug — a test expectation that encoded
  the author's own misunderstanding (the fresh entry's episodeUrl fallback is the KEY itself, not a fixture URL);
  fixed.
- **B → commit fdfcf0bc (D-405):** the welcome is rebuilt on `OnboardingBlobBackground` (5 morphing blobs, 8 wobble
  points each, closed Catmull-style cubic paths, Lissajous center drifts, radial-gradient fills + 3 rotating outline
  shapes — one drawBehind pass); `RotatingTagline` (AnimatedContent fade+slide, 3.6s cadence); the footer line is
  removed (version moved to the finish step); the theme step is a `LazyRow` + `rememberSnapFlingBehavior` carousel
  (centered-card derivedStateOf, 220ms collectLatest debounce on the live apply, tap-to-snap, initial scroll to the
  current theme) + the System/Light/Dark `ThemeModeRow` + the settings note; the permission steps use a 92dp centered
  icon, a granted state replacing the action button (folder keeps "Change folder"), and ONE combined bottom button
  (neutral "Skip for now" → accent "Continue", pinned by a weighted column); the finish step is a 2×2 SummaryCard
  grid. MainActivity: the "system" card is absorbed into the mode row (7 cards), `currentOnboardingThemeMode` added,
  the screen call passes mode + callback. **B9:** the new `BrowsePreloader` (app module) mirrors BrowseViewModel's
  cache-first section loads + enqueues every cover at 128×192dp / hero posters at 84×126dp (SectionPreloader's
  memory-cache contract); AppRoot launches it under a once-captured `shouldPreloadBrowse` remember flag (finishing
  the wizard mid-warmup never cancels it).
- **C → D-406:** the wizard⇄app handoff crossfades (250ms/180ms emphasized) — a third branch in the appNav
  transitionSpec; everything else stays instant exactly as before.
