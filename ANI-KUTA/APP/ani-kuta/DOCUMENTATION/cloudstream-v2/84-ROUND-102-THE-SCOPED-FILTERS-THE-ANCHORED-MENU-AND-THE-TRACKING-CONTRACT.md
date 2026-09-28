# DOC 84 — ROUND 102 — THE SCOPED FILTERS, THE ANCHORED MENU, AND THE TRACKING CONTRACT

> **Status: PLANNING (in flight).** This document is the round's plan + record.
> The v1.1.58 device report (the user's round-102 order). Next doc: 85.

## 0. The user's order (verbatim intent, grouped)

The user tested v1.1.58. Satisfied: untrusted filters work, WebView works, the
theming everywhere, the unlink permanence, the episode-status display. The
round-102 order has SEVEN work streams:

1. **WS-A — Filter scope.** The extensions filters "are being applied to the
   other screens too, like for example in the search page, there, Pick a
   Source" — filters must apply ONLY to the extensions page. Audit other pages.
2. **WS-B — The three-dot menu form.** NOT a bottom-up sheet — "a menu which
   would smoothly appear from the three dots buttons… just like how it was
   previously implemented but in a better UI." KEEP the icon style. REMOVE the
   per-item descriptions. REMOVE the Refresh item entirely (auto-refresh
   replaces it — see WS-C). The bottom sheet also broke the hidden-grab-area
   rule; a dropdown has no grab area at all.
3. **WS-C — Data-source switch UX.** Switching to Extension required a manual
   Refresh. Make the switch AUTO-refresh the newly-selected axis. The refresh
   must be SMART: always try; on failure fall back to the saved local data
   (no false "data available" states). PLUS the choice must PERSIST: exit →
   reopen → still Extension (today it resets to AniList), and the LIBRARY page
   must reflect it (cover + name).
4. **WS-D — Share + deep links.** The share options should NOT be a bottom
   sheet: no descriptions, no copy button — only the share targets, and picking
   one opens the DEVICE's share sheet directly. AND the app "did not even
   detect its own links" — the anikuta:// deep link must actually work.
5. **WS-E — The tracking contract (the big one).** (a) Tracking must be
   OPT-IN: "Even if the user has connected and linked an anime to any list,
   then the tracking should not happen" — only explicit "Track this" counts.
   (b) The three-dot menu's tracking entry shows TWO states: "Tracking now" /
   "Not tracking". (c) The TrackSheet gets TWO buttons: LEFT "Remove from
   Tracking" (unlinks tracking between AniList and the app — NOT a remote
   delete), RIGHT "Save" (theme-colored; the ONLY way changes persist —
   closing without saving discards). (d) The top-right X becomes a TRASH CAN:
   "Do you want to delete it from AniList?" — the REAL remote deletion.
   (e) Proper error messages on failures. (f) The database handled with care —
   change only what needs changing. (g) Fully-watched episodes sometimes never
   reached AniList (updated in-app only) — fix the sync path; future trackers
   must ride the same contract.
6. **WS-F — The player episode-list header.** REMOVE the search and the
   settings gear from the player's Episodes header (both stacks). The right
   side gets a TEXT "Scroll to Current" that scrolls the list to the currently
   playing episode.
7. **WS-G — The player episode-list settings PAGE.** "Just like how there is
   the option to customize the episodes list of the details page" — a proper
   dedicated settings page (the same style + experience) for the PLAYER page's
   episode list.

Standing directives: no rushing, root-cause fixes, modular + commented, DB
care, ≥2 guided sub-agent logic tests (findings lead-verified, not blindly
trusted), the detailed todo list.

## 1. RESEARCH FINDINGS (root causes, with evidence)

### WS-A root cause — CONFIRMED (one leak, one only)
`SearchViewModel.csSources` (L287-295) filters the CloudStream provider list
by the PERSISTED `AppPreferences.extensionsNsfwMode` — the Extensions page's
NSFW tri-state. Set it there (e.g. ONLY) → the Search page's "Pick a Source"
CS list filters too. The language filter is screen-local (`remember` in
ExtensionsSettingsScreen L237) — it CANNOT leak. No other consumer reads
`extensionsNsfwMode` outside extensions-settings (grep-verified). The aniyomi
trusted list was never NSFW-filtered (an inconsistency: the two picker lists
applied different rules). Fix: the picker shows EVERY trusted source of both
ecosystems; the extensions-page filters stay extensions-page-only.

### WS-B/WS-D findings
- The menu is `DetailsActionSheet` (a ModalBottomSheet WITH a visible grab
  handle — the rule violation) + `ShareContentSheet` (another bottom sheet
  with per-row copy buttons). Both live in DetailsActionSheet.kt.
- The three-dots is an `ActionButton` in DetailBanner's top action row
  (DetailsScreen L2085-2089) — a DropdownMenu composed in a Box around that
  button anchors exactly there. The details body (incl. DetailBanner) renders
  inside the AdaptiveAccentTheme scope → the dropdown inherits the per-content
  accent (M3 popup content composes within the themed hierarchy).
- `buildShareContent()` (VM) + `ContentShareLinkFactory.build()` already
  produce the three ShareLinks — the submenu reuses them; the OS chooser
  Intent pattern already exists in ShareContentSheet's onShare.
- The deep-link intake (MainActivity L819-850) is a `remember {}` read of the
  ACTIVITY INTENT — it runs ONCE per composition. A WARM link tap
  (app already running) never re-reads the intent → nothing happens ("the app
  did not even detect its own links"). MainActivity has no launchMode →
  `standard` → warm taps would stack a duplicate activity even if parsed.
  The notification intake (`notification_main_id`, L770-810) has the SAME
  remember-once limitation. Fix: `singleTask` + a unified REACTIVE pending-
  navigation channel (onCreate AND onNewIntent both feed it; AppRoot collects).

### WS-C/WS-D findings
- `switchDataSource` (VM L2810-2822) ONLY remerges the in-memory bases —
  nothing persists. On reopen the load paths remerge with hardcoded defaults
  (loadFromAniList → ANILIST at L1234/1260; loadFromExtension → EXTENSION at
  L2026/2110) → "reopens showing AniList".
- The DB ALREADY has the persistence slot: `main_entry.display_source`
  ('data_source' | 'extension') + `ContentRepository.updateMainEntryDisplaySource()`
  + `updateMainEntryTitle()` — both EXIST, neither is read for merge priority.
  ZERO schema change needed for the whole WS-C persistence.
- The D-685 both-axes refresh (`refreshAniListAxis`/`refreshExtensionAxis`)
  exists — the switch can fire the TARGET axis's refresh after the remerge.
- The Library cover logic (LibraryViewModel L487): `dataCoverUrl ?:
  extThumbnailUrl` — always AniList-first; ignores display_source. The title
  is `main_entry.title` — switching never updated it. Fix: the switch updates
  BOTH display_source + title; the Library cover honors display_source.
- The Library PTR spinner-stuck: `refreshLibrary`'s finally-block IS correct
  (it always sets false) — but the load is DB-only (tens of ms), and M3's
  PullToRefreshBox indicator has a known race when `isRefreshing` flips
  true→false within the same frame window as the gesture end (the dismiss
  transition gets lost → the spinner stays). Fix: a minimum spinner duration
  (~800ms) in refreshLibrary so the true→false transition always lands after
  the indicator settles (the standard workaround; disclosed as such).

### WS-E findings (the tracking contract)
- **No opt-in exists**: `relayWatchEvent`/`relayRating` (TrackSyncManager) fire
  for EVERY linked + logged-in content. No "tracked" flag anywhere.
- **The player→AniList gap (CONFIRMED root cause of the missed updates):**
  the player's 10s progress saves + the 85% auto-mark write ONLY to
  `watch_progress`; the ONLY relay call sites are DetailsViewModel's
  `toggleWatched`/`markAllPreviousWatched`/`markSeriesAsWatched` — watching an
  episode IN THE PLAYER marks it locally and NEVER tells AniList. Exactly the
  user's "it would update in our application, but it would not update in the
  AniList tracking."
- `removeTrackEntry` (VM L807+) also CLEARS local watch progress + rating —
  wrong under the new contract (Remove = unlink sync only).
- `AniListTracker.deleteEntry` (DeleteMediaListEntry) exists — the trash-can
  action maps to it. `syncEntry`/`fetchEntry` exist for Save/refresh.
- The TrackSheet saves EVERY picker tap immediately (no draft/save semantics);
  one destructive "Remove from Tracking" button; a plain X close.
- DB: a new small table is the deliberate choice — `track_entry` is a CACHE
  (presence ≠ intent; the relay would auto-create rows). A separate
  `content_tracking_state(main_id, tracker_type, tracked)` table expresses the
  USER'S INTENT cleanly, observably (SQLDelight flows), and extensibly
  (tracker_type — MAL etc. later). Debug line = schema freedom (§30).

### WS-F/WS-G findings
- WatchScreen's Episodes header (L2006-2114): search pill + gear + in-list
  search field; the CS twin in CsWatchPage (L384-430). Both to be replaced by
  the "Scroll to Current" text action (LazyColumn state is available in both).
- The player customization rides `PlayerEpisodeListPreferences` (D-688) — the
  dedicated PAGE (Settings → Appearance → "Player episode list", mirroring
  `EpisodeListSettingsScreen`'s live-preview architecture) replaces the two
  in-player sheets (which get deleted — the gear is gone).

## 2. THE PLAN (phases → commits)

| Phase | Content | Commit |
|-------|---------|--------|
| P1 | WS-A: the picker filter scope fix | `fix(round-102): the scoped filters…` |
| P2 | WS-B+WS-D-UI: the anchored three-dot menu + the direct-share submenu (sheets deleted) | `feat(round-102): the anchored menu…` |
| P3 | WS-C: switch auto-refresh + persistence + library reflection + PTR min-duration | `fix(round-102): the details switch contract…` |
| P4 | WS-D-deeplink: singleTask + the unified reactive intake | `fix(round-102): the detected deep links…` |
| P5 | WS-E: the tracking contract (table + repo + gating + bridge + sheet redesign + menu states) | `feat(round-102): the tracking contract…` |
| P6 | WS-F+WS-G: the player header + the dedicated settings page (sheets deleted) | `feat(round-102): the player list header…` |
| P7 | Sub-agent guided testing ×2 + lead-verified fixes | folded / fixups |
| P8 | CI green → ledger → release v1.1.59/10159 | `docs(round-102): the ledger` |

## 3. SAFETY / NON-GOALS
- **Extensions page filters: byte-untouched** (they now work correctly there —
  user-confirmed). Only OTHER pages' independence is restored.
- **NSFW policy unchanged**: the search picker simply stops mirroring the
  extensions page's tri-state. No new NSFW gating anywhere.
- **DB changes (deliberate, disclosed):** ONE new table
  (`content_tracking_state`) + ONE new watch.sq batch query. The WS-C
  persistence rides the EXISTING display_source column (zero churn).
  Debug line = schema freedom (§30, no migrations).
- **The old removeTrackEntry's watch-progress/rating clearing is REMOVED**
  (per the user's new semantics: Remove = unlink sync only; local data kept).
- **markSeriesAsWatched keeps its explicit COMPLETED relay** (an explicit user
  intent) — now tracked-gated like every other relay.
- D-425/D-430/D-472 discipline unchanged; existing surfaces not named by the
  user stay byte-identical.

## 4. EXECUTION RECORD

**Commits (mainline `feature/round-57-cloudstream-downloads`):**
- `3a046329` — WS-A: the scoped filters (the search picker independent again).
- `a9904145` — WS-B+WS-D-UI: the anchored menu, the direct share, the tracking-state
  table/repo/isTracked (the menu label's dependency).
- `4267889d` — WS-C: the details switch contract (persistence, auto-refresh, library
  reflection, the PTR min-duration).
- `bd00d208` — WS-D-deeplink: singleTask + the unified reactive intake.
- `10484a04` — WS-E: the tracking contract (opt-in gating, the bridge, the sheet).
- `c516aee7` — WS-F+WS-G: the player header + the dedicated settings page.
- `f1f60a56` — the self-review hardening (4 findings).
- `177d685f` — the verified sub-agent findings (1 compile break + 4 hardening fixes).
- `fa5c9e72` / `9d4b7302` / `d827f268` — the CI fixes (see §4.1).

**Files touched:** 27 (5 new: DetailsActionMenu.kt, PendingNavigation.kt,
TrackingStateRepository.kt, TrackingWatchSyncBridge.kt,
PlayerEpisodeListSettingsScreen.kt; 3 deleted: DetailsActionSheet.kt,
PlayerEpisodeListSettingsSheet.kt, CsPlayerEpisodeListSettingsSheet.kt; plus
SearchViewModel, DetailsViewModel, DetailsScreen, TrackSheet, DetailBanner(in
DetailsScreen), LibraryViewModel, WatchScreen, CsWatchPage, MainActivity,
AnikutaApp, AppearanceScreen, AndroidManifest, AppPreferences, WatchProgressStore,
SqlDelightWatchProgressStore, TrackerAniListModule, TrackSyncManager, DetailsModule,
track.sq, watch.sq, DatabaseDriverFactory, and the tracker-anilist build file).

**The sub-agent testing (the user's standing order, ≥2 guided agents, findings
lead-verified):**
- SUB-AGENT 1 (R102-SA1 — filters/switch/library/PTR/deep links): 12 scenarios, ALL
  PASS. Four lead-attention items: the transient single-base priority label
  (pre-existing shape — NOT applied, documented), the stale AppPreferences KDoc
  (APPLIED), the details-on-top swallowing of deliveries (APPLIED — the
  replace-vs-skip rework), the same-frame last-wins conflation (inherent, harmless).
- SUB-AGENT 2 (R102-SA2 — the tracking contract): 14 scenarios, 11 PASS. **F1
  (FAIL-MAJOR): `_trackSheet.value` — an undeclared-symbol typo (a hard compile
  error) in saveTrackEntry** — VERIFIED + FIXED. **F2 (MAJOR): the Save path closed
  the sheet before the async sync resolved, leaving a failed sync's error message
  with no surface** — VERIFIED + FIXED (the Saving… state; success closes, failure
  renders inline). **F3 (MINOR): both player stacks' Scroll to Current offsets
  forgot the always-composed Currently-Playing card at LazyColumn index 0** —
  VERIFIED + FIXED (MPV: 2; CS: 2 + switcher). The minor edge notes (delete-failure
  leaves tracking off — deliberate; the draft re-seed clobbering ultra-fast edits
  during the open fetch; the pre-existing episodes.size progress overshoot on CS
  sub/dub lists) — documented, not applied.

### 4.1 CI history (the honest count: 4 runs — 2 over the D-472 budget, disclosed)
- Run 1 (`36460088747` on `177d685f`): FAILED — `:core:tracker-anilist` could not
  resolve `WatchProgressStore`: the module lacked the `:core:watch-progress`
  dependency (the bridge's new import). Fixed in `fa5c9e72`.
- Run 2 (`36460561300`): FAILED — the rewritten TrackSheet's snackbar helpers
  referenced `Icons.Filled.Close` fully-qualified, but extension properties resolve
  through IMPORTS, not receiver qualification — the import was missing. Fixed in
  `9d4b7302`.
- Run 3 (`36460980218`): FAILED — :app only (every other module green): the unified
  collector's `collectAsState` (MainActivity's first StateFlow collectAsState —
  never imported before) + the new `PlayerEpisodeListSettingsScreen` import. Fixed
  in `d827f268`.
- Run 4 (`36461645067` on `d827f268`): **GREEN**.

## 5. WHAT SHIPS (the user-visible round-102 changes)
1. **Filters scoped:** the extensions page's filters (incl. the NSFW tri-state)
   apply ONLY there — the Search page's "Pick a Source" lists every trusted source
   of both ecosystems, unfiltered and consistent.
2. **The three-dot menu:** an anchored DropdownMenu expanding smoothly from the
   button itself — icon rows, no descriptions, no Refresh item, no grab area.
   Share is a submenu at the same anchor whose targets fire the DEVICE's share
   chooser directly (no bottom sheet, no copy buttons). The Tracking row reads
   "Tracking now" / "Not tracking" live.
3. **The switch contract:** picking a data source persists (main_entry.
   display_source + the title) — reopening respects it, the LIBRARY's cover and
   name follow it, and the switch auto-refreshes the target axis (failure keeps
   the local data — no false availability). The library's stuck pull-to-refresh
   spinner is fixed (the M3 indicator race workaround).
4. **Deep links detected:** anikuta:// links (and notification taps) work cold AND
   warm — singleTask + the unified reactive intake; navigating from anywhere,
   replacing a different content's details page when one is on top.
5. **The tracking contract:** tracking is OPT-IN (linking alone never syncs); the
   TrackSheet is a draft + Save/Remove bar (Save = the only persistence path AND
   the opt-in; Remove = unlink the sync, keeping everything; the trash can =
   "Do you want to delete it from AniList?" — the real remote deletion) with
   inline errors; and the player's episode completions finally reach AniList (the
   reactive watch_progress bridge with the startup self-heal sweep).
6. **The player list:** search + the settings gear retired; "Scroll to Current"
   scrolls to the playing episode (hidden when filtered out). The customization
   moved to a DEDICATED settings page — Settings → Appearance → "Player episode
   list" (the details page's live-preview architecture), driving both player
   stacks.

## 9. JUDGMENT CALLS (disclosed)
1. **The DB changes are two, both deliberate:** the `content_tracking_state`
   table (the opt-in intent — deliberately separate from the track_entry cache,
   whose row presence has never meant user intent) + the
   `getAllHighestWatchedNumbers` batch query. The switch persistence rides the
   EXISTING display_source column (zero churn). Debug line = schema freedom (§30).
2. **The old removeTrackEntry's local watch-progress/rating clearing is REMOVED**
   — under the new contract Remove = unlink the sync only; the destructive delete
   moved to the trash can; local data is the app's own and always kept.
3. **Derived-state reconciliation instead of a retry queue** for the bridge: the
   computed-vs-confirmed-cache comparison self-heals on the next write AND at
   startup — no queue table, no replay complexity. Disclosed limitation: a failed
   score/status sync needs a re-Save (the bridge only reconciles progress).
4. **Save keeps the sheet open until the sync resolves** (the SA2-F2 fix) — the
   error must have a surface to render on. The double-tap is guarded by the
   saving flag.
5. **The PTR min-duration (800ms) is a workaround, not a cure** — the M3
   PullToRefreshBox indicator race is upstream; the floor guarantees the dismiss
   transition lands. Disclosed as such in the code.
6. **The player settings page approximates the details page's collapse** (the
   scroll-linked slide, not the D-557 two-phase gesture machinery) — the player's
   options list is four cards; the full machinery is disproportionate there.
7. **Run-count honesty:** 4 CI runs (2 over the D-472 budget of 2/cycle) — one
   missing module dependency, one extension-import trap, one first-ever-import
   gap. All three were new-code-only errors; the sub-agent-caught F1 typo would
   have been a FOURTH error class had it reached CI.
8. **The dead AutoLinkPopup.kt** remains (zero call sites — the standing cleanup
   candidate, unchanged from round 101).

## 10. THE DEVICE CHECKLIST (round 103's input)
1. **Filters:** set the NSFW pill (and any language filter) on the Extensions
   page → go to Search → "Pick a Source" — the lists show EVERY trusted source
   regardless of the extensions filters. The extensions page itself still filters.
2. **Three-dot menu:** tap the three dots — the menu expands FROM the button.
   Icons, no descriptions, no Refresh. Share → the three targets → picking one
   opens the DEVICE share sheet directly.
3. **Switch persistence:** on a linked entry switch to Extension → exit → reopen
   — still Extension. The Library cover + name follow the choice. Switching
   auto-refreshes (no manual Refresh needed).
4. **Library pull-to-refresh:** pull — the spinner shows briefly and ALWAYS
   dismisses.
5. **Deep links:** share an ANI-KUTA link → open it from the other app with
   ANI-KUTA already running (warm) AND not running (cold) — the app opens on the
   content both ways. Tapping it while on a DIFFERENT content's details page
   switches to the linked one.
6. **Tracking:** a linked-but-never-tracked content — watch episodes → AniList
   does NOT change (the menu says "Not tracking"). Open Tracking → the menu shows
   the state; edit fields → close without saving → nothing changed. Save →
   "Saving…" → tracked ("Tracking now") + AniList updated. Remove from Tracking →
   the app stops syncing; the AniList entry stays. The trash can → the confirm →
   the entry is gone from AniList (local progress kept). Kill the app mid-play →
   reopen → the startup sweep syncs the watched episodes.
7. **Player list:** no search/gear in the Episodes header; "Scroll to Current"
   jumps to the playing episode (both stacks). Settings → Appearance → "Player
   episode list" — the live preview + options apply to both players live.
