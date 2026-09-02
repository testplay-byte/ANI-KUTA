# 17 — DEVICE ROUND 26 (Task 66): THE FOUR FINDINGS OF THE v0.4.13 ROUND

> Plan doc. Branch: `streaming/CLOUDSTREAM-V2`. Baseline: v0.4.13/78
> (round 25 shipped, CI green 33657424783, Release live). The round-25
> device round CONFIRMED the crash fix, the library performance
> improvement, the delete animation ("exactly like how I wanted it…
> 100% to my likings"), the heatmap weekday labels, the details metadata
> stack, and the CS browse's general phased look. This round fixes the
> four findings — root-caused, each item its own commit, CI-verified.

## The device report (v0.4.13)

1. **Delete button direction**: the two-step delete icon became SMALLER
   on arm — "it was supposed to become three times bigger". The delete
   animation + exit choreography themselves are confirmed perfect.
2. **Delete logic robustness**: the episode's video file was deleted on
   disk + the app showed it deleted, but the `.data.json` still listed
   the episode afterwards; and deleting the very last (or only)
   downloaded episode left the series folder husk behind (the whole
   folder should go). Wants: safe checks, proper logic, documentation,
   console logging.
3. **CS browse bleeding, take two + the loading discipline**: the
   round-25 fix "was not fixed properly — apparently made a bit worse in
   some areas". Wants the whole loading system as a proper separate
   module: load all the categories first (with loading animation) →
   load the contents incrementally, nothing rushed, nothing parallel →
   show the results → load the covers afterwards.
4. **Update check next-check accuracy**: the countdown said 23h 21m
   while the next actual release was 18h 33m away; the "next check"
   listed 3+ anime (releases 3–7 days out) instead of only the one(s)
   really releasing; wants the next check based on the NEXT PROBABLE
   RELEASE of the content + a "smart" system that notifies exactly at
   the scheduled release and confirms the content is watchable.

## Item 1 — the delete button GROWS (D-389)

**Root cause (one line):** `TwoStepDeleteIcon` line 688 —
`Modifier.scale(if (isArmed) 0.65f else 1f)`. The round-25 "0.65x
normalization" (compensating DeleteForever's edge-to-edge glyph so both
states occupy the same footprint) was the EXACT INVERSE of what the
user wanted: the confirm state must be a BIG, unmissable danger glyph.

**Fix:** `animateFloatAsState(1f → ARMED_ICON_SCALE = 3f)` over 220ms
FastOutSlowIn, still draw-phase `Modifier.scale` — the row geometry and
the 32/36dp tap targets stay perfectly stable while the armed glyph
visually dominates (a 48dp glyph inside the 32dp frame simply draws
beyond its box; the row's trailing padding absorbs the overflow). The
150ms `AnimatedContent` fade+scale glyph morph composes multiplicatively
with the grow (incoming DeleteForever fades in at ~0.6x and settles at
3x — one continuous pop), and the error tint + DeleteForever identity
keep the danger signal.

## Item 2 — the robust delete ladder (D-392)

**Root causes:**
- `.data.json` update: `removeEpisodeFromDataJson` was a single-shot
  write+verify with NO recovery path — a transient SAF failure or a
  stale document URI silently won while the DB row still vanished
  (exactly the reported state: file gone, DB gone, `.data.json`
  unchanged).
- Series folder: `removeEpisodeFromDataJson`'s KDoc explicitly said the
  folder is KEPT with an empty episodes list ("delete entire content
  TODO"). No code path ever removed the folder.

**Fix — the five-phase orchestration** (`deleteDownloadedEpisode`, every
phase logged under `Anikuta:Core:Download`):
1. **Locate** — `findContentFolder(mainId)` (unchanged).
2. **Files** — video + subtitles via `DocumentsContract.deleteDocument`
   (now read on Dispatchers.IO — the old call blocked Main).
3. **`.data.json`** — the 3-attempt ladder: normal write → fresh-index
   retry after a 250ms settle pause (stale-URI recovery — a sibling
   deletion can invalidate previously-resolved DocumentFile URIs) →
   NUCLEAR delete-recreate (removes the old document, writes through
   the create path — sidesteps any stream-write weirdness on the
   existing document). Verify-by-re-read on every attempt.
4. **Series cleanup (NEW)** — `maybeDeleteSeriesFolder`: re-read the
   `.data.json` AFTER the removal; if it lists ZERO episodes (the
   canonical last-episode signal) OR it's unreadable AND the DB has no
   remaining rows (the orphan case) → `deleteContentFolder` +
   `deleteDownloadedEpisodesByMainId` (the sweep — survivors would point
   into the deleted folder).
   `deleteContentFolder`'s safety ladder: must exist + be a directory;
   never the SAF root; never a `video/images/text/audio` format folder;
   `mainId` identity re-confirmed by re-reading the `.data.json` RIGHT
   BEFORE the deletion. Children deleted recursively bottom-up (some
   SAF providers refuse `delete()` on non-empty dirs), each logged.
5. **DB + cache** — row delete + cache refresh (always; a no-op after a
   sweep).

**Delete-all** is now ONE atomic operation (`deleteDownloadedAnime`):
locate the folder once → delete it whole → sweep the rows. The old
ViewModel loop did N `findContentFolder` walks (each reads EVERY
`.data.json` in the tree) and left the husk. Fallback: the per-episode
loop when the folder can't be located/deleted — the DB always ends
consistent.

## Item 3 — the dedicated CsBrowseLoader module (D-390)

**Root causes (why round 25 failed):**
- The bleeding HOLE never closed: `shelfLists`'s final fallback
  (`else -> lists`) returned EVERY list when nothing matched the shelf
  name. Name-ignoring providers return the WHOLE home per request —
  every category row got the same mixed first-20. The round-25 "name
  matching" only worked when response list names matched EXACTLY.
- The rushing: Phase 2 fired ALL shelf requests in parallel
  (`shelves.mapIndexed { async { … } }.awaitAll()`) — for a
  name-ignoring provider, N identical full-home downloads at once.

**Fix — one self-contained module** (`CsBrowseLoader.kt`,
`data/cloudstream/`):
```
Phase A — the PLAN (zero network): read provider.mainPage, emit the
          category skeleton (merged row structure) — the full category
          layout renders with shimmer immediately.
Phase B — the CONTENT, STRICTLY SEQUENTIAL (one shelf at a time, in
          the provider's own order — nothing rushed ahead):
          • CsShelfMatcher: exact (normalized) name match → fuzzy
            (containment, ≥4 chars) → EMPTY. There is deliberately NO
            all-lists fallback: an honest empty row beats bleeding.
          • a single unmatched list that belongs to ANOTHER shelf →
            skipped (that shelf's own request delivers it);
          • static-home detection: a response whose lists answer OTHER
            shelves too is captured ONCE as a snapshot — every later
            shelf is SLICED from it, zero network. ONE fetch replaces N.
Phase C — the CANONICAL result: same-title merge + cap + the
          duplicate-content safety net (sections with identical
          first-5-URL sets collapse to one — the last possible bleeding
          vector: a provider answering every shelf with the same list).
```
Covers stay with Coil — they load per card AFTER the row's content is
on screen (the "results first, covers after" order).

The category SUBPAGES (`browseShelf`) share `CsShelfMatcher` — a
name-ignoring provider's subpage now shows its own shelf's slice (or an
honest empty) instead of the whole home. The shared
`toCsCard`/`absolutize`/`mergeSameTitleSections` helpers moved next to
the loader; the repository keeps only provider resolution + the cache
write. The event contract (`Categories`/`Section`/`Complete`) is
UNCHANGED — the ViewModel is a drop-in consumer (the cache-first
instant-open + silent background refresh flow is untouched).

## Item 4 — the completed smart-update system (D-391)

**What the Smart Update System is (now actually complete):**
- The PERIODIC worker (default 24h, network + battery-not-low) is the
  safety net — it re-checks everything due and refreshes the AniList
  schedule data.
- The SMART RELEASE one-shots are the precision instrument: for every
  anime with a known future airing, a `SmartReleaseCheckWorker` fires
  at `airingAt + learned offset` — the per-anime LEARNED delay (how
  long after the AniList airing the episode actually appears on the
  source; learned as 70/30 weighted average from every confirmed find;
  +10min default for first-time anime). The worker fetches the source's
  episode list, CONFIRMS the episode is really there ("watchable"),
  fires the "watchable" notification (the user's setting), records the
  actual release time, and updates the learned offset. Progressive
  retries: +10min → +20min → +1h → +2h.

**Root cause of the inaccuracy:** the one-shots were only scheduled
when the periodic worker HAPPENED to run within ±1h of an airing
(WINDOW_MS=1h, max 5). A release 18h away was never pre-scheduled, so
the history page's countdown targeted the periodic fire time (23h) and
the "will check" preview used the whole 24h backoff horizon (3+ anime).

**Fix:**
- `SmartReleaseScheduler.scheduleUpcomingChecks()` — 7-day horizon,
  every future airing (cap 40, soonest first), each at `airingAt +
  learned offset`; requests tagged `smart_release` in WorkManager.
- THREE triggers now (re-)aim the one-shots (unique-name + REPLACE =
  idempotent): `ScheduleEngine.fetchSchedule()` — THE authoritative one
  (the moment a new airing time is discovered, its smart check exists;
  nullable ctor seam, `core:schedule` already depends on
  `core:updates`), the periodic worker after each sweep, and the manual
  Check Now.
- The history page's pinned card: `nextCheckAt = min(the earliest
  ENQUEUED smart one-shot (queried by tag), the periodic fire time)`;
  the "how" text explains the smart check when it's the earliest; the
  due list is filtered by `next_airing_at <= nextCheckAt` (+1h grace)
  — "only the one(s) really releasing", each row showing
  `EP n · <device time> · in Xh Ym`.
- `UpdateEngine.buildSummary` (the notification's next-check line):
  `min(earliest release check, interval)` — the line also LABELS the
  smart case ("at the next episode's expected release").

## Verification

- Item A: CI GREEN 33668498398 (45a4d5b7).
- Items B+C: CI on 2dcabb3f/12253691 (cumulative).
- Version 0.4.14/79; no DB schema changes; the aniyomi stack untouched;
  the browse event contract unchanged.
