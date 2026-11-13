# Record 126 — Round 147: the wizard rework, the extensions-restore honesty, and the v1.1.88 ship

> The user's device report on v1.1.87 ordered: the welcome polish + the REDDISH default
> accent; the theme step with ALL accents + light/dark/AMOLED properly; the progress bar
> treating the personalize block as ONE segment when skipped / split when walked; back-
> from-Storage after skipping → the gate; the PERSONALIZE gate as a list (theming
> included); the details config 4 layouts × 4 sub-presets (Classic Focus + Grid Minimal
> removed) + the sort; the player config parity (the sort in, the gestures/auto-play
> toggles out); the notifications 3-way OFF/TEXT/BANNER with the grayed preview, the
> options colored only for BANNER, DIFFERENT images per role, the animations, and no
> "change later in Settings" lines; ALL permissions in ONE wizard screen with Continue
> ABOVE Skip-for-now; the FINISH as a completion screen; the extensions restore fixed
> (the trust, the summary, the CS plugins, the repos) + the linked-only backup option;
> the Permissions screen reworked (icons, tap-to-manage); the More page + sub-pages
> scroll-to-top; the support-popup-after-restore bug; the first-screen notification ask.
> Plus: "do the debug release of it too."

## 1. The bug fixes (the report's crash/behavior class)

- **The first-screen notification ask**: `MainActivity.onCreate` asked POST_NOTIFICATIONS
  unconditionally BEFORE the wizard composed — a fresh install saw the system dialog over
  the welcome art. FIXED: gated on `onboardingCompleted` (the wizard's permissions step
  owns the fresh-install grant; the onCreate ask serves the post-onboarding cohorts only);
  the launcher registers unconditionally (the activity-result contract requires it before
  RESUMED); the prefs via the Koin `GlobalContext`.
- **The support popup after clear-data + restore (M-6)**: the donor's
  `ads_always_sponsor=true` rode the prefs restore and re-enabled the debug sponsor
  bypass over the recipient's fresh 3-day grace. FIXED: the four ad-system keys
  (ads_always_sponsor, ads_first_open_timestamp, ads_last_shown_timestamp,
  ads_first_open_grace_consumed) joined `PREFS_RESTORE_EXCLUDED_KEYS` — the recipient
  keeps their own ad state.
- **The fresh-install default**: ThemePreferences defaults DARK + RED (the ember look —
  the user's "reddish special separate color" order; LIME keeps the midnight mapping for
  existing states).

## 2. The wizard rework

- **THE MERGED PERMISSIONS STEP**: NOTIFICATIONS + BATTERY + OVERLAY became ONE
  `PERMISSIONS` step (the enum is 10 steps now). Each row keeps its REAL verified state
  (re-verified on ON_RESUME) + its own Allow affordance; the bottom stacks Continue
  (primary, always) ABOVE "Skip for now" (secondary) — the user's explicit order.
- **THE BLOCK-SEGMENTED PROGRESS**: `OnboardingStep.visibleSteps(personalizeWalked)` —
  skipped = the gate is ONE segment (6 to Finish); walked = the block splits (9). The
  back-walk fix: STORAGE.onBack is conditional (`personalizeEntered ? stepIndex-- :
  the gate`) — back from Storage after skipping landed INSIDE the block's skipped steps.
- **THE GATE LIST**: the personalize gate is 4 rows (Theme / Details page / Player page /
  Notifications — icon tile + one-line description + chevron/check), every row
  individually openable, theming included.
- **THE THEME STEP**: the carousel is now the FULL ACCENT PALETTE — generated from the
  AccentPreset enum (RED first, CUSTOM excluded) for BOTH mode buckets; a dedicated
  AMOLED toggle row (dark only); picking a card applies ONLY the accent
  (`applyOnboardingAccent`), the mode toggle applies ONLY the mode, the AMOLED row owns
  amoled. The old 8-preset cards + the id→triple mapping are deleted.
- **THE FINISH**: a completion screen — the animated check (Animatable scale-in),
  "Setup complete", the fully-filled progress bar, NO back button, NO summary grid
  (SummaryCard deleted).
- **THE WELCOME ART**: the 900ms reveal (the canvas breathes in) + the warm-shifted base
  (0xFF120B0D; was the purple-leaning 0xFF0D0A14).
- **THE CONFIG STEPS (subagent B)**: details = 4 layouts (Classic/Grid/Timeline/Cinema) ×
  4 sub-presets each applied through the real EpisodeListPreferences levers
  (`WIZARD_DETAILS_LAYOUTS` catalog; rowStyle/synopsis/date/audio/progress/download +
  the grid + cinema knobs) + the SegmentedToggle asc/desc sort; the player keeps its 4
  styles + gains the sort + LOSES the swipe-gestures/auto-play toggles (and the
  PlayerPreferences param); the notifications step is the 3-way OFF/TEXT/BANNER
  SegmentedToggle (OFF dims the preview to α0.35, TEXT grays the art options to α0.38,
  the art options are inert outside BANNER), the AnimatedContent re-layout, and THREE
  DISTINCT demo images — the new `ep_preview_cover.jpg` (a 275×366 portrait crop of
  still_2's left third) joins the two stills (background=still_1, artwork=the cover
  crop, thumbnail=still_2). Every "change later in Settings" line deleted. The host
  call sites stayed parameterless (all new params default).

## 3. The extensions restore (subagent C1)

- **THE TRUST FIX (the report's "restored but untrusted")**: `applyAniyomiState` now
  ends with `extensionManager.loadAll()` — the scan-time Untrusted cache (the loader
  reads trust at scan time; the install broadcast's re-scan could beat the synchronous
  trust write) can no longer hide a just-trusted extension. One change covers the
  already-installed and the post-install paths (both funnel through applyAniyomiState).
- **THE REPOS COUPLING**: `ownsPrefsFile` for anikuta_extension_repos +
  anikuta_cloudstream_repos now rides `extensionRepos || extensionAniyomi ||
  extensionCloudstream` — extensions are useless without their repos (the report's
  "repos empty").
- **THE LINKED-ONLY OPTION**: `BackupOptions.extensionsLinkedOnly` (default false; key
  `pref_backup_ext_linked_only`) + the IncludeSelectionPopup row. The collection filters:
  Aniyomi APKs whose installed sources don't intersect the library's used source ids are
  dropped; CS plugins whose providers' synthetic ids (`CsSourceIds.idFor` hash — the
  same bridge used at play time) aren't used are dropped. Degrades gracefully (an
  untrusted extension, an empty source list, a failed query — kept, logged).
- **THE SUMMARY HONESTY**: RestoreSummary gained `aniyomiDeclined: Int` +
  `csPluginsFailed: List<String>`; BackupManager tracks the declined count and BOTH cs
  failure paths (the missing archive entry + the failed write). The result card + the
  toast + the wizard's restore step report "N of M extensions installed" (missing/
  declined/failed) and "N of M CloudStream plugins restored" (failed).

## 4. The Permissions screen + the scroll-to-top

- **PermissionsScreen**: every row wears the icon tile (SettingsIconTile) + the
  ExtraBold title + the one-line description + the compact chip; the ROW is tappable —
  ungranted runs the Allow flow, granted opens the system's manage page for THAT
  permission (the app-notification settings, the battery page, the overlay page,
  re-picking the folder). The old Allow/App-info text buttons are gone.
- **THE SCROLL-TO-TOP**: More, Settings, Appearance, Appearance General, Permissions,
  Backup & restore, Restore — `LaunchedEffect(Unit) { scrollToItem(0) }` on entry (the
  host's SaveableStateProvider resurrected the old scroll position; screens landing
  "wherever they were left" was the complaint).

## 5. The verification + the ledger

- CI: run 43485099789 GREEN (4bce4244 — the bug batch), 43487593297 RED (82aeac29 — the
  animateFloatAsState unresolved-import class; fixed with the module-proven Animatable
  pattern), 43488230489 RED (34b04723 — named arguments on function-typed values + the
  icons artifact carrying the classic ChevronRight, not the automirrored one; dedupe of
  a stray import followed), **43489196750 GREEN (5085bd18 — the FULL round-147 tree)**.
- **The release (D-875)**: the user explicitly ordered it — Routine A on the green head:
  release/1.1.88 + ONE bump commit a260de90 (10120/1.1.20 → **10188/1.1.88**) + the
  annotated tag v1.1.88 ON THE BUMP COMMIT (first try) → Release APK run
  **43490588481 GREEN**; verified live: stable + latest, ani-kuta-v1.1.88-debug-
  arm64-v8a.apk (76,121,868 bytes) + SHA256SUMS.txt; the downloaded SHA (3573bf78…)
  matches. v1.1.87 installs get the in-app offer (10188 > 10187).
- **The CI ledger**: 4 Build runs (2 green, 2 red diagnostics — distinct roots each,
  the D-866 batch lessons held) + the Release run = 5 total, past D-472's ≤2 (disclosed;
  the no-local-Gradle boundary).
- The credential lesson: pushes must OVERRIDE the credential chain (`-c credential.helper=
  -c credential.helper='!f(){ cat credentials/github-credentials; }; f'`) — the system
  `manager` helper answers with the release account (403 as Confused-Creature-180).
- The register: M-3/M-4/M-6/M-7 closed (the last two this round; M-3/M-4 in round 146
  — the register caught up); H-4/M-5/M-8..M-13 + the P/L lists stay for triage.
- Next decision: D-875; next record: 127.
