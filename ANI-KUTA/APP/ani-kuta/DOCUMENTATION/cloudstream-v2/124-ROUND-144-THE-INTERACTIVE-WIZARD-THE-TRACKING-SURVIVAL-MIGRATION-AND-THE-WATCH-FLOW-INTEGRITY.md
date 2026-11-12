# Record 124 — Round 144: the interactive wizard, the tracking-survival migration, and the watch-flow integrity

> The user's order: the highly-interactive setup wizard (live previews, presets, "check them
> out / later in the app", one-line small talk, the optional-permissions messaging, a better
> summary, default preview images); the library display defaults; the details episode sort
> default (descending); the watch-flow day-sheet integrity (the user's three device
> symptoms); open-removed-content + the extension-unavailable toast; the history/profile
> click → scroll-to + highlight the episode; tracking that survives removal AND extension
> deletion with the DB migration properly tested; the stale-files sweep — then THE DEBUG
> RELEASE (explicitly ordered this round).

## 1. The recon (3 read-only scouts, sequential within the concurrency window)

- **The wizard + settings inventory**: the 8-step enum + the hardcoded-index dispatch; the
  theme cards' id→prefs mapping (buildOnboardingThemeChoices/applyOnboardingThemeById); the
  11 accent presets; the details episode-list prefs (rowStyle CLASSIC|GRID|TIMELINE|CINEMA +
  the element toggles + the sort pair); the player's parallel vocabulary; the poster's 5
  templates + the REAL-composer preview; the live-preview machinery that already exists on
  the episode-list/player settings pages (fed by the bundled ep_preview_still_1/2.jpg);
  BackupManager's signatures; NO preset-bundle concept anywhere (built fresh).
- **The removal lifecycle**: library removal deletes ONLY library_item (clearByMainId/
  deleteMainEntry/deleteContentDetails are DEAD CODE — zero callers); extension uninstall/
  untrust deletes NOTHING (in-memory maps only); the only live session-delete is the History
  "Clear all". The Profile clicks were the bug: `anilistId?.let { navigate(it) }` —
  extension-only content (anilistId = null) SILENTLY NO-OPPED on every Profile surface.
- **The details scroll machinery**: the autoPlayEpisode key param = the exact additive
  pattern for targetEpisode; SmoothCenterScroll + the WatchScreen offset-counting lesson;
  the arrivalPulse highlight language; AppToast.show from anywhere.

## 2. The watch-flow integrity — ROOT CAUSE + the fix (D-862)

The user's device report: a 47-minute bar listing ONE episode (21m); a 41-minute bar with
"no episodes recorded"; a 5-minute bar with no records. **ROOT CAUSE**: the day BAR summed
`watch_session.played_ms` bucketed by `session.started_at` while the day's EPISODE LIST
bucketed `watch_progress` rows by `progress.lastWatchedAt` — two sources with different day
keys and different existence conditions (sessions always write; progress saves depend on the
save points): any episode whose progress row was stale or missing made the bar and the list
disagree. **THE FIX**: the day-detail's episode list derives from the SESSION LEDGER ITSELF
(the day's rows grouped by episode_key — main_id + the "|<ep>" suffix carry the attribution;
bar and list agree BY CONSTRUCTION), with the pre-137 legacy days falling back to the old
progress bucketing. The 12-item cap is gone for the session-driven days; the rows sort by
that day's minutes.

## 3. The tracking-survival migration — schema v3 → v4 (D-862)

**The discovery**: `PRAGMA foreign_keys = ON` (DatabaseDriverFactory.onOpen) makes the
`ON DELETE CASCADE` clauses LIVE — both watch_progress and watch_session referenced
main_entry, so any main_entry deletion would silently wipe the user's history + ledger.
Today nothing deletes main_entry (the uninstalls don't touch the DB), but the user's
requirement ("keep track of it even after removal and even after extension deletion")
demands the decoupling: **3.sqm** rebuilds both tables WITHOUT the FK (the .sq updated for
fresh installs) + the indexes recreated byte-identical. **SchemaMigrationTest +2**: the FK
strip (PRAGMA foreign_key_list empty on both) and the BEHAVIORAL proof (foreign_keys ON →
migrate → DELETE main_entry → the progress row AND the session row SURVIVE via the typed
API). Also confirmed: `clearByMainId` (which would have deleted progress + sessions) has
ZERO callers — removal is clean; the dead cleanup code stays dead on purpose.

## 4. The open-by-mainId path + the scroll-to/highlight (D-863)

- **AnimeDetailsKey gains `targetEpisode: Int?`** on both variants (the autoPlayEpisode
  additive pattern — old persisted backstacks deserialize).
- **MainActivity.openDetailsByMainId(mainId, episodeNumber)** — the ONE unified path: the
  Profile's recently-watched / day-sheet / timeline / genre rows and the History screen all
  funnel here. Removed-from-library content OPENS (identity + progress survive removal);
  extension-only content OPENS (the old anilistId-only clicks no-opped); the ONLY blocked
  case — the owning extension uninstalled/untrusted/disabled (`extensionManager.getSource`
  null) — toasts **"Extension <name> not available"** (the name via main_entry.systemId →
  getSystemById). The D-849 0L-poison caveat honored (an unresolvable identity toasts
  instead of minting).
- **The details arrival**: the LaunchedEffect waits for the loaded display list, computes
  the lazy index (EpisodesSection at 3 + the next-episode card + the sub/dub switcher when
  present + the row index — GRID pairs count /2), `animateScrollToItem`, then flashes the
  highlight: **EpisodeListEntry gains `isHighlighted`** (a 1.5dp primary border + a 10% wash
  for ~2.6s, the body extracted into EpisodeListEntryBody). History passes the entry's
  episode number; the Profile rows pass theirs.
- **History's enrichment keeps rows whose main_entry is gone** (the fallback title) — the
  silent skip is gone. The genre sheet stops filtering extension-only content.

## 5. The defaults (the user's spec)

- **Library**: the episode badge default OFF → **RELEASED** (audio BOTH + unwatched-only
  false were already the defaults); every other requested default ALREADY matched the code
  defaults (COMPACT_GRID, 3 columns, 1 title line, caught-up OFF, score OFF, total entries
  ON) — verified, unchanged.
- **Details episodes**: `sortDescending` default false → **true** (latest at the top);
  configurable in the wizard's details step + the episode-list settings sheet.

## 6. The interactive wizard (D-864)

- **The PERSONALIZE gate** after THEME: "Make it yours" + the one-line ask + two CTAs —
  **"Lets check them out"** / **"Later in the app"**. Nothing is shown by default.
- **Three config steps** (DETAILS_UI → PLAYER_UI → NOTIF_UI) hosting app-provided composable
  SLOTS (the module rules keep feature/onboarding from importing the renderers — the wizard
  orchestrates, the app builds the UIs):
  - **Details page**: 6 presets (Classic / Classic Focus / Grid / Grid Minimal / Timeline /
    Cinema — named bundles of EpisodeListPreferences writes) + the LIVE preview rendering
    the REAL EpisodeListEntry over the bundled stills (works on a fresh install with no
    network art) + the fine-tune pointer to Settings.
  - **Player page**: the 4 episode-list styles + the swipe-gestures + auto-play-next
    toggles + the live PlayerEpisodeListEntry preview.
  - **Notifications**: the master toggle → the poster-banners toggle → the 5 poster
    templates + the banner/cover/episode artwork picker + the LIVE preview through the REAL
    EpisodeBannerComposer (the bundled still as the override art).
- **One-line small talk** per config step ("Pick the look you like — change it anytime.").
- **The permission steps** now say: **"All permissions are optional — skip any you don't
  want."**
- **The FINISH summary** gains the Details-page card ("Personalized" vs "Defaults").
- **The dispatch**: step navigation via `steps.indexOf(...)` (the hardcoded indices are
  gone — future insertions are one-line), totalSteps derived from the enum.
- The existing THEME / STORAGE / RESTORE / permission steps are UNCHANGED (the restore step
  already wires BackupManager).

## 7. The verification + the ledger

- Local sanity: brace/paren balance + NUL-scan on all 11 heavily-edited files — clean; the
  `isHighlighted`/`EpisodeListEntryBody` landing verified by grep after a silent
  script-write anomaly (the Edit-tool recovery).
- **THE DISCLOSURE**: no local Gradle run (the machine boundary from round 143 stands);
  CI (run 43293609267) is the authoritative build. The migration tests ride CI-external
  (the test wall runs locally only when the user's machine allows — the migration logic is
  test-pinned for the next full local pass).
- **CI ledger (D-472)**: run 43293609267 (Build APK for bb13753c) — the round's 1 run; the
  docs push after green triggers no CI.
- **The RELEASE (D-867)**: the user explicitly ordered it — Routine A on the round-144
  head (c1da00d6, the CI-green 0bce3339 + the docs commit): release/1.1.86 + ONE bump
  commit b9a46110 (10120/1.1.20 -> **10186/1.1.86**) + the annotated tag v1.1.86 (the
  full user-facing body). TWO tag mishaps disclosed: the first tag landed on MAIN's
  head (the 1.1.20 tree — the workflow's tag-versionName gate correctly FAILED it,
  run 43299255714; the mistagged ref deleted via the API and re-tagged on the bump
  commit), and ntfy was throttled at the finish. The corrected Release APK run
  **43299505509 — GREEN**; verified live: stable + latest, ani-kuta-v1.1.86-debug-
  arm64-v8a.apk (76,076,032 bytes — the torrent engine's native lib, the disclosed
  growth) + SHA256SUMS.txt; the downloaded SHA (42bee0fa…) matches. v1.1.85 installs
  (10185) get the in-app offer (10186 > 10185).
- **THE CI LEDGER DISCLOSED**: the round ran SEVEN Build APK runs before the release
  run (43293609267, 43294266071, 43295012459, 43295755399, 43296913534, 43297699005,
  43298231904-GREEN) + the Release APK run = **8 total, far past D-472's <=2** — the
  direct consequence of the no-local-Gradle boundary (CI was the only compile loop,
  each red run exposing the next module's errors one layer at a time). Each failure
  was a distinct compile error, fixed immediately; the lesson banked: without a local
  compiler, batch fixes ONLY after the full error list lands.

- ntfy: the round's notices delivered on ANIKUTAPROGRESS.
- Next decision: **D-867**; next record: **125**.
