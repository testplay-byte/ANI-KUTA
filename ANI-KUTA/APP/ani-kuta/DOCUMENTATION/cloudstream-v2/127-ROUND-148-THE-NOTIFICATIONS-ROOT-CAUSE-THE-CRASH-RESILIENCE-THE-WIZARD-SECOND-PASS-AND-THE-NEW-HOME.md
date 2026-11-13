# Record 127 — Round 148: the notifications root cause, the crash resilience, the wizard second pass, and THE NEW HOME

> The user's v1.1.88 device report (twice-crashing restores, notifications never arriving,
> the frozen live previews, the scroll-on-back regression) + the order: fix everything,
> plan/document/build the NEW HOME ("My screen") from the taste-skill's design vocabulary,
> install the taste-skill, and REPLACE the current home. No release ordered (D-738) —
> the round ships at CI green.

## 1. THE NOTIFICATIONS ROOT CAUSE (the round's centerpiece)

The user's report: entries update silently, notifications NEVER arrive, every setting
looks right. The traced truth: **the episode path posted BLIND** — no POST_NOTIFICATIONS
check anywhere in `NotificationManager.postNotification`; on Android 13+ with the grant
absent, `notify()` is a silent OS drop, and `recordSent` then consumed the episode into
the dedup log FOREVER (the engine's last-known count had already advanced, so the send
was never re-attempted). Every sibling path (the tester, the progress card, the app
updates) guarded the grant — only the real episode path didn't. The user's clear-data
cycles reset the runtime permission; every episode detected in those windows was eaten.
FIXED with three coordinated changes:

- **The grant gate** at the post point: permission absent → return FALSE *without*
  recordSent (the episode stays re-notifiable).
- **The semantics sharpened**: permanent suppressions (master off, per-anime off, trigger
  off, variant off, dedup hit) return TRUE = consumed (no backlog flood on re-enable);
  FALSE = retry next check. The NotificationSender contract documents it.
- **The engine holds the count**: UpdateEngine advances last-known only to the highest
  NOTIFIED (or watched) episode — an ungranted send re-attempts on the next check. The
  M-4 tenths math preserved (`Math.round(Float)` is Int — the widening lesson).

## 2. THE CRASH RESILIENCE (DeadSystemRuntimeException ×2)

The restore's extension phase stacked up to **2N overlapping PackageManager scans**
(per-install `loadAll` from the round-148 trust fix + one per PACKAGE_ADDED broadcast)
while system_server was mid-install; one transient DeadSystemException escaped the
unguarded `loader.loadAll()` into a handler-less scope → process death. FIXED:

- `ExtensionManager.loadAll`: runCatching + ONE timed retry (750ms) + a soft final
  failure (keep the previous state) — inside the coroutine, so EVERY caller is covered.
- The per-state reload REMOVED from `applyAniyomiState`; the bridge gains
  `reloadAniyomiExtensions()` and BackupManager calls it ONCE after the whole install
  loop (the trust fix's intent preserved — the fresh scan reads the persisted trust set).
- The CS `$stable` NoSuchFieldError: the loader's diagnosis names it (the plugin was
  compiled against upstream's Compose-processed Plugin; this host's clean-room API has
  no Compose marker) — extension-side build gap, the Errored row now says so.

## 3. THE WIZARD SECOND PASS (the report's UI class)

- **THE LIVE PREVIEW BUG**: the details layout toggle NEVER wrote `rowStyle` — the style
  key was decorative, so the preview (and the details page) never left Classic. FIXED
  (the style pins first, then the preset's levers).
- The gate comes right after the welcome; the theme step is INSIDE the block, walked
  first ("he should be met with this make it yours screen"); the Theme row loses the
  pre-done check.
- The presets: ONE ROW of icon tiles (no names) + the selected one-line description
  below; the player step gains per-style presets (4 styles × 4, through the real levers)
  and its preview reads the levers reactively (it built defaults before).
- The notifications step: TEXT renders an honest text-notification mock at FULL alpha
  (it stayed faded before); the art-tiles legend REMOVED; the composer gains
  `overrideCoverUrl` — the Cover artwork source gets its own portrait crop, so Banner
  and Cover render different stages.
- The permissions step: granted rows show ONLY a bigger check (28dp, no "Allowed" text);
  "Skip for now" disappears once everything is granted.
- The completion screen: the breathing glow + the staggered title reveal; the description
  shortened. The restore step shows the FULL backup story (date, donor version, entries,
  covers, extensions, pref files) in a glass details card.
- The welcome: the blobs are OUT — a quiet two-glow drifting backdrop (26s/19s orbits)
  + the app details as a LIST (the gate-list vocabulary) + the approved wordmark/tagline.

## 4. THE SHELL (the report's navigation/backup class)

- **Scroll-reset-on-FORWARD-ONLY**: the host gains `pushKey` — it wipes the target key's
  SavedStateHolder state before EVERY forward push (97 call sites) and the tab switch;
  going BACK from a child restores the position (the round-147 per-screen
  `LaunchedEffect scrollToItem(0)` resets also fired on back and are REMOVED).
- The Permissions screen: one-line titles, 2-line descriptions, a big check or X instead
  of the Allowed/Set/Exempt tags.
- The backup option's description removed (the row title carries it).
- `onboarding_completed` joins `PREFS_RESTORE_EXCLUDED_KEYS` — a backup from a finished
  install can no longer skip the recipient past their own wizard (the report's
  restart-into-library-ask sequence).

## 5. THE NEW HOME (the "My screen")

The spec lives at `DOCUMENTATION/PROJECT-MAP/11-HOME-MY-SCREEN-SPEC.md` (the taste-skill
rules: one hero fills the first screen, equal 12dp edges, hairline cards with no
rest-shadow, quiet one-time reveals, the hero as the only moving thing). Built:

- **HomeViewModel**: cache-first discovery rails under NEW `home_*` browse_cache keys
  (Search's co-owned trending key untouched — the D-278 trap); Continue watching from
  the orphaned v4 `observeContinueWatching` query; the derived What-to-watch-next
  (highest-watched+1 + the RELEASING next-airing cards); Releasing soon from the
  schedule store; Recently released from the update feed; partial-success everywhere.
- **HomeScreen**: greeting + the BrowseHero carousel; the spec's section stack
  (Continue / Next up / Releasing soon / Recently released / Trending / Popular /
  Top rated); the re-used Browse card anatomy; the personal rails carry 3dp progress
  bars; empty rails skip their section; skeleton loading; retry only on a fully-empty
  state.
- **The swap**: `AnimeBrowseKey` dispatches HomeScreen (the key object stays — the tab
  IS Home; the bottom-bar label becomes "Home"); the D-272 ad gate preserved verbatim;
  the removed-content open path (`openDetailsByMainId`) wired; BrowsePreloader warms the
  home keys at the exact render sizes. ZERO schema change (browse_cache + the derived
  queries carry it all; no new FKs — the live-CASCADE trap respected).
- **The taste-skill** installed at `.zcode/skills/design-taste-frontend/` (gitignored;
  the user's order) — its layout/card/motion vocabulary drove the spec.

## 6. The verification + the ledger

- CI: 43505228020 RED (the Math.round(Float)=Int widening), 43506822844 RED (the
  InfiniteTransition.animateFloat extension import in the onboarding module), 43508083544
  RED (deriveNextUp suspend), **43509003602 GREEN on fb5a14ac — the FULL round-148 tree**.
  4 runs total, past D-472's ≤2, disclosed (the no-local-Gradle boundary; distinct roots).
- The home degradations (documented in the spec): the airing-next card scopes to
  recently-watched mainIds (a full-library scan needs a new anime_update_state SELECT —
  deferred, no schema change this round); next-up's existence guard uses data_episodes.
- **The release (D-881)**: the user's explicit order ('complete everything and do a
  proper release so i can update the debug application') — Routine A on the round-148
  head 3e467663 (the code tree of CI-green fb5a14ac + the docs commit): release/1.1.89
  + ONE bump commit c66318f4 (10120/1.1.20 -> **10189/1.1.89**) + the annotated tag
  v1.1.89 ON THE BUMP COMMIT (first try) -> Release APK run **43514915195 GREEN**;
  verified live: stable + latest, ani-kuta-v1.1.89-debug-arm64-v8a.apk (76,187,408
  bytes) + SHA256SUMS.txt; the downloaded SHA (57d810ef…) matches. v1.1.88 installs
  get the in-app offer (10189 > 10188) — the release CARRIES the notifications
  root-cause fix + the restore-crash resilience + the wizard second pass + THE NEW
  HOME, on top of round 147.
- Next decision: D-882; next record: 128.
