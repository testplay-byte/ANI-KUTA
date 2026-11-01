# 117 — ROUND 135: the OSD pills, the magnetic zoom, the granular backup with the extension payload, and the update UX

The round-135 device report on v1.1.78 opened with praise (the gesture
exclusivity, the next-episode pill, "the overall handling") and then set six
workstreams, with the standing instruction: *"Nothing should be rushed,
everything needs to be done with proper care, planning, understanding,
management, and proper testing."* Mid-round the user added the extension
orders (lib 14–17 acceptance, the per-entry update check, the pin-to-top
rule for both ecosystems) **and the release order** ("I would like you to do
a proper release so that I can easily install it. From the application
itself"). The round ran per the planning file (`planning/2026-11-02-round-135-workflow.md`),
with two research agents up front and two adversarial verification passes
behind.

## 1. The player (W1 + W2)

**The vertical pill OSD (volume/brightness).** The round-134 floating-glyph
OSD is gone. Each side-swipe now renders a PILL — a 58×190dp rounded
container (black @ 60%) on the side opposite the finger — with the live
percentage at the TOP, a THICK (12dp) fill bar in the MIDDLE that grows and
shrinks with the level (theme-colored fill on a translucent track, animated
on the same 110ms tween that killed the round-134 "20→70" jump), and the
icon at the VERY BOTTOM. The muted state still swaps in the speaker-slash
icon + "Muted"; the cap still reads "MAX". The pill's solid background is
itself the legibility story: the round-134 stacked-shadow glyphs retired
with the backgroundless design (`GlyphShadow`, `ShadowedIcon`,
`VerticalLevelBar` all deleted).

**The seek pill.** The seek OSD speaks the same language as a HORIZONTAL
pill: `[delta] [arrows] [target]` in bold, compressed WHITE text — the
theme-colored ± died with the report ("looks bad and ugly with the dark
background"; the "compressed" ask ships as a −0.2sp tracking tighten). In
fullscreen the pill aligns TOP CENTER (`GestureOsd(alignTop = true)` from
`FullscreenControls`; the vertical-middle default stays for portrait/cinema),
28dp below the top edge.

**The magnetic zoom.** `VideoZoomState` gained a raw/locked split:
`rawZoom` accumulates the pinch, `freeZoom` is its DETENT-LOCKED view. While
zooming, the factor STICKS at every detent — the nine snap steps **plus the
computed fit-height and fit-width free factors** (relative to the ACTIVE
preset, so the marks are correct under Fit/Fill W/Fill H/Crop) — until the
pinch travels past the detent's hold band (0.06 > the 0.04 capture radius,
so a just-escaped detent cannot re-grab); between detents the factor
free-follows the finger; on release it snaps to the nearest detent. The
zoom chip got its solid background (the report's ask). Out-of-range fit
factors are filtered; near-duplicates collapse (a fit scale that lands on
1.25 IS the 1.25 step).

## 2. The granular backup/restore (W3)

**The selection model.** `BackupOptions` became 13 persisted switches under
four categories — **Library** (entries & categories / history, progress &
ratings / tracking / downloads, schedules & updates), **Images** (cover
files / search & browse covers / episode thumbnails), **Settings** (app /
extension & source / security & login tokens), **Extensions** (repos /
Aniyomi / CloudStream). The mapping is central: `tableFor` picks the DB
tables (FK-safe order preserved), `ownsPrefsFile` + `includePrefsKey` pick
the prefs payload (the security keys are an `anilist_*` prefix family; the
extension trust/enable keys ride the Extensions bucket). The manual backup
AND the scheduled worker read the same `BackupPreferences.options()`.

**The page.** The bento top is exactly TWO tiles — BACKUP (left, runs with
the current selection; the first tap offers the folder picker when no folder
is set) and RESTORE (right, navigates to the restore page) — one-line
descriptions, per the report. Results report as bottom-up `AppToast`s
(SUCCESS/ERROR), never inline text. The existing-backups list stays.

**The restore page.** Restore moved OUT of the settings page into its own
screen (`RestoreBackupKey`): pick a file → the manifest is read IO-side and
the DETAILS render (created when, from which app version, row count, covers,
extension counts) → confirm → restore → the result panel with the counts and
a **Restart now** button (relaunch intent NEW_TASK|CLEAR_TASK, then exit).

**The onboarding restore.** The wizard gained a RESTORE step (3 of 7, right
after the storage folder is verified): pick → preview line → "Restore
everything" → done; fully skippable, everything-on semantics.

**The extension payload.** A new `ExtensionBackupBridge` interface
(:core:backup) is implemented in the app layer (`ExtensionBackupBridgeImpl`)
— the only place that can see both ecosystems and the engine. On BACKUP it
collects the installed Aniyomi APKs (read from each package's `sourceDir`,
installed + untrusted) and the CloudStream .cs3 files (records whose file is
missing are skipped — carrying one would resurrect a ghost the loader
deletes). The archive grows `ext/state.json` (the inventory + trust state),
`ext/apk/<pkg>.apk`, `ext/cs/<dense-index>.cs3`. On RESTORE: DB → prefs →
.cs3 files + records (the bridge re-derives the DEVICE-LOCAL repo-salted
path; donor absolute paths are never reused) → images → Aniyomi installs
(one system prompt per APK, sequential, a cancel moves on) + ADD-only
trust/enable re-application (after a `TrustService.refresh()` — the prefs
pipeline wrote the trust keys behind the service's back). The manifest
gains `includesExtensions`/`aniyomiApkCount`/`csPluginCount`;
`formatVersion` is 2 (v1 archives restore fine).

**The cover-count fix.** `collectCoverImages`/`restoreImages` accept BOTH
cover file names (`.cover.jpg` and `cover.jpg`), the bytes are read and
verified BEFORE the entry + count (no zero-byte inflation), and the restore
writes the canonical dot-prefixed name.

**The verification saves.** Pass 1's P0: the raw `plugins_json` prefs
payload would have clobbered the bridge-restored records with donor paths
and the loader would have deleted every file-less record on next load — a
cross-device CS restore that silently self-destructs. Fixed twice over: the
key is filtered from the dump (state.json + the bridge are the record
authority), AND the restore was reordered so the bridge's upserts land
AFTER the prefs application. Pass 1's P1 (the onboarding test asserted the
pre-135 step order and referenced the removed `wizardIndex`) was rewritten;
the P2s (dense cs3 index, IO-side `listBackups`, the trust refresh, the
caching-switch initial value) all fixed and re-verified.

## 3. The extension update UX (W4 + the mid-round addendum)

**Why updates were never detected.** Three stacked defects: `fetchExtensions`
fetched ONLY `<repo>/index.json` while the Aniyomi-repo standard (and the
verify path!) prefers `index.min.json` — min-only repos 404'd every check;
`LIB_MAX = 16.0` silently DROPPED the lib-17 entries the user's repo serves;
the 30-minute throttle stamped BEFORE the fetch, so a failed check bought
half an hour of silence. Fixed: min-first fetch, `LIB_MAX = 17.0` (both
parse sites), the stamp AFTER the fetch + an `AtomicBoolean` single-flight
guard + a defensive catch (the check now fires on every page entry).

**The acceptance window.** Lib 17/16/15/14 "and all of those": the index
window is 12..17 (matching the loader's documented known-good range), the
loader has no hard gate (out-of-range versions are ATTEMPTED — a failure
surfaces as a visible Errored row, never a silent vanish), the install path
has no lib check, and the parent-first classloader treats every generation
alike. Verified by pass 2 across the whole chain.

**The entry check + the pin.** Entering the Extensions page now forces an
update check on BOTH ecosystems (the D-301 throttle no longer gates the
entry; the single-flight guards still stop overlaps) and the flows re-emit
live. Trusted extensions with an available update PIN to the very top of
the Installed list regardless of the alphabetical order (aniyomi:
`hasUpdate`; CloudStream: `availableUpdateVersion != null`; alphabetical
tiebreak below; the Available/Untrusted/Errored sections unchanged) — the
user's rule, on both tabs.

## 4. The profile fix + the caching default (W5 + W6)

**The top glitch.** The collapse threshold was a hardcoded 56dp while the
tab band is ~42dp — the `collapsingTabBand` fixed point sat at
f* = H/(H+T) ≈ 0.43, BELOW the 0.5 snap boundary, so the half-collapsed
state never snapped away ("way too much empty space" on first entry). The
threshold is now the band's MEASURED height (the toggle is the measured
node — the band itself collapses and would feedback) minus a 6dp margin,
which structurally puts f* ≈ 0.54 — the snap always wins. The stale
`derivedStateOf` capture (it read the FIRST list forever after a tab
switch) is keyed on `(activeListState, bandHeightPx)`; the chrome shave
(spacedBy 8→6dp, ProfileHeader 16→10dp vertical) trims the entry view.

**Video caching** defaults OFF (getter + flow + the ViewModel's StateFlow
initial — the hardcoded `true` initial made the toggle flash ON-then-OFF on
every fresh ViewModel).

## 5. The verification

Pass 1 (two parallel agents): W1/W2/W5/W6 all PASS (5 P2 notes); W3/W4 →
1×P0 (the plugins_json clobber), 1×P1 (the onboarding test), 3×P2 + 1 note.
Every finding fixed. Pass 2 (one agent, the fixes + W7/W8): ALL PASS — the
P0 fix verified both halves (the dump filter + the reorder + the v1-archive
trace + the empty-dump edge), W7's chain swept (no remaining gates), W8's
pinning/ghost interaction confirmed cosmetic-only. One P2 hardening note
(the defensive catch) applied. The onboarding unit test suite runs green.
The debug APK assembles.

CI ledger this cycle: **2 runs** — the Build APK for the round commit
(41327820771) and the Release APK for v1.1.79 (41327985958). Within the
D-472 budget.

## 6. The v1.1.79 release (D-823)

The user ordered the release mid-round ("so that I can easily install it.
From the application itself"). Routine A on the round-135 main head
(5e5d4f74): release branch `release/1.1.79`, ONE bump commit (10120/1.1.20 →
**10179 / 1.1.79**), branch + annotated tag `v1.1.79` → the Release APK
workflow (run 41327985958, GREEN). Verified live: the release
"ANI-KUTA v1.1.79 (round 135)" is stable-latest with
`ani-kuta-v1.1.79-debug-arm64-v8a.apk` (63.8 MB, 15 arm64 libs) +
`SHA256SUMS.txt`; the downloaded APK's SHA256
(`ae0a4879fcc10d099b34a030519ff1cb7139af2cc150c6ea32cca2afa4c336bc`) matches
the checksum file, and the workflow's tag↔versionName gate passed (10179 /
1.1.79). v1.1.78 installs get the in-app update offer carrying round 135.
