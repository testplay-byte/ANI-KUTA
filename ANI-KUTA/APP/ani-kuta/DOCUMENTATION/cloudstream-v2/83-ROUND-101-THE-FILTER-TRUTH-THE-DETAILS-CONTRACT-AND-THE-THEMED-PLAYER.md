# DOC 83 — ROUND 101 — THE FILTER TRUTH, THE DETAILS CONTRACT, AND THE THEMED PLAYER

> **Status: PLANNING (in flight).** This document is the round's plan + record.
> The v1.1.57 device report confirmed round 100 ("almost everything is working
> properly… quite satisfied") and brought the round-101 order. Next doc: 84.

## 0. The user's order (verbatim intent, grouped)

The user tested v1.1.57 and is satisfied with the extension/testing/playback state.
The round-101 order has SIX work streams:

1. **WS-A — Extensions filters truth.** The language filter appears to "only get
   applied to the trusted/available sources, never the untrusted ones" (search DOES
   apply). PLUS: when filters are active and everything is filtered out, show
   **"No results. Try removing the filters."** The NSFW filter itself stays
   EXACTLY as-is (user: "Keep it as it is. Don't mess it up… You don't need to
   change anything" — it was their misunderstanding, resolved).
2. **WS-B — Details data contract.** (a) With data source = Extension on an
   AniList-linked entry, cover/name switch but the description/details never load —
   even after Refresh; unlinking AniList makes them load. (b) Unlinking AniList is
   not permanent: reopening the content re-links automatically. Manual unlink must
   be FINAL until the user manually links again. Database care: no table churn.
3. **WS-C — The three-dot menu + Share + WebView.** (a) The menu UI is "not that
   good" — redesign. (b) A REAL share system as its own module with THREE targets:
   the extension's content URL (opens the site in a browser), the data-source link
   (AniList today; future providers), and the app's OWN deep link that opens the
   app on the exact content. (c) A "View in WebView" menu action that opens the
   current content in the app's internal WebView (works for BOTH AniYomi and
   CloudStream extensions).
4. **WS-D — Adaptive-accent completeness.** The resolved-episode-streams sheet and
   the link-sources sheet are NOT accent-themed (default app accent). The player
   page does NOT inherit the details page's accent — it must, EVERYWHERE: the
   player itself, fullscreen, the subtitles sheet, the qualities/servers sheets.
5. **WS-E — Player episode-list customization.** A DEDICATED settings section for
   the player page's episode list (the details page has one; the player page has
   none) — "all the relevant options", including search.
6. **WS-F — Downloads on the details page.** Downloaded / downloading states not
   shown properly; downloading FROM the details page doesn't work; watched /
   unwatched display must be verified. At least TWO sub-agents must run guided
   logical testing; their findings verified, not trusted blindly.

Standing user directives: don't rush; root-cause fixes; modular + documented +
commented; no DB table churn without deliberation; to-do list discipline.

## 1. RESEARCH FINDINGS (root causes, with evidence)

### WS-A root cause — CONFIRMED
`AnimeExtension.Untrusted` is constructed in `ExtensionLoader.loadExtensionInternal`
(ExtensionLoader.kt L157-167) BEFORE the metadata block (L182-184) and WITHOUT
`lang`/`isNsfw`/`isTorrent` → the model defaults (`lang = null`, `isNsfw = false`)
apply. The screen's untrusted filter (`ExtensionsSettingsScreen` L496-502) DOES
apply `langFilter`, so with `lang = null` EVERY untrusted row fails
`ext.lang == langFilter` the moment a language is picked — the section empties
instead of filtering correctly ("the filter never gets applied to the untrusted
ones"). Search still works because it matches name+version independent of lang.
The CS twin (`CloudstreamExtensionsSection` L181-189) filters correctly but
sideloaded `.cs3` records can carry `language = null` (repo-less install) — same
symptom. Fixes: (1) loader reads `tachiyomi.animeextension.nsfw`/`torrent` meta +
parses `lang` from the aniyomi package-name convention
`eu.kanade.tachiyomi.animeextension.<lang>.<id>` BEFORE the trust gate;
(2) screen-level catalog enrichment: when the untrusted pkg (aniyomi) or
internalName (CS) exists in a configured repo's catalog, prefer the catalog's
lang/nsfw (authoritative, matches what Installed/Available rows show).

### WS-B root causes — CONFIRMED (three independent defects)
1. **`refreshMetadataNow()` (DetailsViewModel L1582)**: `if (anime.anilistId != null)`
   → refreshes ONLY the AniList axis when linked; the `else if` extension branch
   never runs for a linked entry. The extension base keeps whatever the cache-first
   open put there (`extDescription` from DB — often null for link-time-created
   rows). Refresh therefore can NEVER repair extension details while linked.
   Unlinking drops `anilistId` → the extension branch finally runs → "details
   loaded properly". EXACTLY the user's report.
2. **`loadFromExtension`'s silent background refresh (L1912-1914)** re-merges with
   hardcoded `EXTENSION` priority — ignores the user's selected priority.
3. **`unlinkAniList()` (L2854-2879)**: calls `autoLinkService.clearUserSkipped`
   (the D-538 decision "unlink re-enables auto-linking") + `clearCachedLink`.
   On reopen, `performAutoLink` runs unconditionally (called from both load paths)
   → re-matches → re-links. The D-238 reverse-side blacklist exists
   (`markUserUnlinked(anilistId)`) but the FORWARD side (extension→AniList) has no
   unlink memory at all. Fix: `unlinkAniList` marks the entry user-skipped on the
   forward axis (supersedes D-538 for the unlink case; skip-button semantics
   unchanged); the manual-link path (`linkAniListEntry`) clears the flag.

### WS-C findings
- The menu is a text-only M3 `DropdownMenu` (DetailsScreen L2078-2131) with a dead
  Share item (`onClick = onDismissMenu`). The app's design language for action
  surfaces is the ModalBottomSheet (EpisodeListSettingsSheet pattern: skipPartially
  expanded, 0.55 screen cap, D-230 tabs).
- `CloudflareWebViewActivity` (D-209) already provides the internal WebView with
  an `EXTRA_URL` intent API + cookie persistence shared with OkHttp — reusable
  for "View in WebView" with the content's own URL (extension `animeUrl`, CS
  provider `mainUrl + relative`).
- The deep-link entry pattern exists (notification_main_id extra → MainActivity
  L767-809 resolves content → `AnimeDetailsKey.AniList/Extension`). The share
  module v1 mirrors it: `anikuta://content/{mainId}` intent-filter → same resolve
  → same navigation. No DB changes anywhere in WS-C.

### WS-D findings
- The adaptive accent exists ONLY around the details body (L691-699) and the
  EpisodeListSettingsSheet (D-231, L1737-1748) — two copy-pasted schemes.
- ResolverSheet, ManualLinkSheet, ManualSearchSheet, TrackSheet: NOT wrapped
  (verified — no `AccentColors` reference in any of them).
- The player receives everything via the serializable `WatchKey`/`CsWatchKey`
  (MainActivity L1017-1018/1026+). Adding a defaulted `coverAccentArgb: Long = 0L`
  field is backward-compatible (kotlinx.serialization defaults) — the accent can
  ride the existing navigation without new infrastructure.

### WS-E findings
- `WatchScreen`'s episode list (L1939-1993) renders bare `EpisodeListRow`s — no
  style/sort/filters/search. `CsWatchPage` reads only `subDubMode`.
- `EpisodeListPreferences` (D-230/D-554/D-558) is the details page's system: row
  style, synopsis/date/audio pills, watch-progress, dim-watched, download control,
  sort, filters, grouping, seasons. The user wants the PLAYER list separately
  customizable → a NEW parallel preference set (`PlayerEpisodeListPreferences`,
  own keys) + a `PlayerEpisodeListSettingsSheet` modeled on the details sheet,
  applied in both player surfaces (WatchScreen + CsWatchScreen page list).

### WS-F findings
- `DetailsViewModel.downloadStates` (L241-291) maps the manager's global map with
  key `"$mainId|…"` — prefix filtering by mainId. `EpisodeDownloadControl` renders
  8 states. `EpisodeRow` consumes `downloadStates[key]`. Needs an end-to-end audit
  of the key-format agreement (row key vs manager key) + the download initiation
  path for BOTH ecosystems + watched/unwatched wiring; sub-agents will pressure-
  test the logic (user's explicit order: ≥2 guided sub-agents, findings verified).

## 2. THE PLAN (phases → commits)

| Phase | Content | Commit |
|-------|---------|--------|
| P1 | WS-A loader + enrichment + empty state (+ unit tests where the module has them) | `feat(round-101): the untrusted filter truth…` |
| P2 | WS-B three details-contract fixes | `fix(round-101): the details contract…` |
| P3 | WS-C menu redesign + share module + webview action | `feat(round-101): the three-dot sheet, the share system…` |
| P4 | WS-D shared accent helper + sheet wraps + player accent | `feat(round-101): the themed player…` |
| P5 | WS-E player episode-list preferences + sheet + application | `feat(round-101): the player episode list…` |
| P6 | WS-F download/watches audit fixes | `fix(round-101): the details download row…` |
| P7 | Sub-agent logical testing ×2 + verified fixes | folded into the above / fixups |
| P8 | CI green → ledger (this doc grows the record sections) → release v1.1.58/10158 | `docs(round-101): the ledger` |

## 3. SAFETY / NON-GOALS
- **NSFW filter: byte-untouched** (user order). Only the untrusted rows' `isNsfw`
  DATA gets populated (so the EXISTING filter behaves consistently) — the filter
  itself, its modes, and its defaults do not change.
- **No DB schema changes** (user order: "not supposed to mess with the tables").
  All persistence rides existing tables (content_details axes) or preference keys.
- **D-425 discipline:** no version bump without the release cut; bump rides the
  release branch (D-430).
- Existing surfaces not named by the user stay byte-identical.

## 4. EXECUTION RECORD

**Commits (mainline `feature/round-57-cloudstream-downloads`):**
- `df14fbe7` — WS-A: the untrusted filter truth + the no-results state.
- `e2b78e0b` — WS-B: the details contract (both-axes refresh, priority respect, permanent unlink).
- `277c1cfe` — WS-C: the three-dot sheet, the share system, View in WebView.
- `56ed9f49` — WS-D: the themed player + the accent-complete details page.
- `c3502b41` — WS-E: the player episode-list customization (both stacks).
- `08814cc8` — P7: the verified sub-agent findings (F1 compile break, B5 ordinal bug, F2/F3 hardening, M1-M4).
- `86cfe5eb` — the CI run-1 fixes (the ColorScheme package, the :core:share Koin dep).

**Files touched:** 25 (4 new: `:core:share` ×3 + AdaptiveAccent.kt; 2 new sheets in features; PlayerEpisodeListPreferences.kt; plus the edits across ExtensionLoader, ExtensionsSettingsScreen ×2, ExtensionListChrome, DetailsViewModel, DetailsScreen, EpisodeListProcessor, MainActivity, AnikutaApp, AndroidManifest, WatchKey, CsWatchKey, WatchScreen, CsWatchPage, and the two build files + settings.gradle.kts).

**The sub-agent testing (the user's explicit order, ≥2 guided agents, findings lead-verified):**
- SUB-AGENT 1 (P7-G1 — filters + downloads): 17 scenarios. Verdicts: all PASS except B5 — a PRE-EXISTING (round-16/17) CS sub/dub ordinal divergence: the details rows computed their flavor ordinals over the FILTERED/sliced view (`episodesToShow`) while the player computes them over the FULL list — any active view filter (watched/downloaded/audio, season, group) made the row's progress key point at the WRONG episode; a toggle OVERWROTE another episode's progress. This is the user's "watched and unwatched details not shown properly" symptom class. VERIFIED by the lead (line-level) and FIXED: ONE raw-list ordinal map now feeds both the rows and the watched filter (a new `watchIdentityNumberOf` resolver on `applyEpisodeListPreferences`).
- SUB-AGENT 2 (P7-G2 — contract + share + accent): 15 scenarios. Verdicts: PASS except **F1 — a hard COMPILE BREAK** in `rememberAdaptiveColorScheme`: `MaterialTheme.colorScheme` (a @Composable getter) read inside `remember`'s `@DisallowComposableCalls` calculation lambda. VERIFIED + FIXED (the base scheme hoisted into the composable body, keyed into the remember). Also F2 (the per-axis persist read `currentMainId` AFTER the fetch — cross-content row corruption window, doubled by the both-axes rework; FIXED: capture at axis start) and F3 (the CS bridge's `"https://localhost"` dead-provider sentinel leaked into share/WebView URLs; FIXED: rejected). NOT applied (documented): F4 (deep-link singleTask hardening — acceptable v1), F5 (the dead `AutoLinkPopup.kt` file — zero call sites; a future cleanup round).
- Findings NOT trusted blindly — every one re-verified against the code before applying; the M-series minors (M1 case nit, M2 CS catalog dedupe, M3 ghost-zombie, M4 CS-untrusted dropdown gap) all verified real and fixed in `08814cc8`.

**CI:** run 1 (`36441126198` on `08814cc8`) FAILED on exactly 4 errors — both in never-compiled NEW code: `AdaptiveAccent.kt`'s return type referenced `androidx.compose.ui.graphics.ColorScheme` (the WRONG package — ColorScheme is material3's), and `:core:share` was missing its Koin dependency. Fixed in `86cfe5eb` (run 2 = `36441788817`) — after a dependent-surface re-review (Surface-onClick precedent, imports, the ordinal wiring, every fully-qualified reference). The F1 remember-composable error the sub-agent caught would have been a THIRD error class had it not been fixed pre-push.

## 5. WHAT SHIPS (the user-visible round-101 changes)
1. **Extensions:** the language filter now works on UNTRUSTED rows (both tabs; loader-parsed + catalog-enriched data); filters-that-match-nothing show "No results. Try removing the filters." + a Clear-filters pill.
2. **Details:** Refresh now actually refreshes the EXTENSION side of linked entries (descriptions load under data source = Extension); a manual AniList unlink STAYS unlinked (no silent auto-relink, either direction); the priority you picked survives background refreshes.
3. **Three-dot menu:** a proper bottom sheet (icon rows, sections) with a REAL share system (extension URL / AniList / ANI-KUTA deep link — copy or OS-chooser) and "View in WebView" (the internal WebView on the content's page, both ecosystems).
4. **Theming:** the resolved-streams sheet, the link-sources sheets, the tracking sheet, and the WHOLE player page (both stacks — controls, fullscreen, subtitles/qualities/speed sheets, episode lists) now carry the details page's per-content accent.
5. **Player lists:** a dedicated customization section per player stack (gear on the Episodes header): row style Detailed/Compact/Minimal, synopsis/date toggles, dim-watched, watched filter, sort — plus in-player episode search (magnifier). The CS watched filter is now ordinal-correct.
6. **Fixed en route:** the round-16/17 watched-status corruption under active view filters on CS sub/dub lists (B5), the cross-content persist window (F2), the localhost share URL (F3).

## 9. JUDGMENT CALLS (disclosed)
1. **The NSFW tri-state is byte-untouched** (the user's explicit order — the "missing extensions" report was their NSFW-filter misunderstanding). Only the untrusted rows' DATA quality changed; the filter's modes/defaults/persistence are identical.
2. **No DB schema changes** (the user's "don't mess with the tables" order). The share/deep-link/accent features ride preference keys + existing columns; the unlink permanence rides the existing AutoLinkPreferences skip flag.
3. **Downloaded-episode plays don't carry the accent** (the accent rides the details→player navigation; download-originated keys keep the global theme) — chosen over persisting an accent column per the no-table-churn order. A future round can derive it from the cover at play time if the user wants.
4. **The deep link is v1-limited** (exactly the user's spec): `anikuta://content/{mainId}` opens the app on the content via the notification-tap resolver; no web fallback page, no singleTask re-parenting (a second activity instance stacks — standard Android behavior, documented as F4).
5. **The share sheet's links are computed once per open** — a refresh landing mid-sheet won't update the rows (acceptable v1; reopening recomputes).
6. **The dead `AutoLinkPopup.kt`** (zero call sites — the banner spinner is the live forward UX) is DOCUMENTED, not deleted (out of round scope; flagged for a cleanup round alongside ExtensionReorderList).
7. **The player-stack twin doctrine held**: the two episode-list settings sheets are deliberately separate files (zero code coupling) backed by ONE shared preferences class — consistent with every other sheet pair in the two player stacks.
8. **Run-1 honesty:** the round burned its first CI run on two new-code compile errors (a wrong package reference + a missing module dependency) — inside the D-472 2-run budget only after the sub-agent-caught F1 fix saved a third error class from ever reaching CI.

## 10. THE DEVICE CHECKLIST (round 102's input)
1. **Extensions → filters:** pick a language with untrusted extensions installed → the untrusted section FILTERS (not empties). Search "en" → untrusted rows match too. Pick filters matching nothing → "No results. Try removing the filters." + Clear filters works on BOTH tabs.
2. **NSFW filter:** verify behavior is EXACTLY as before (off by default, cycles the same).
3. **Details → data source = Extension on an AniList-linked entry:** the description/genres/status now come from the extension; tap Refresh — it stays correct. Switch to AniList — AniList data. Switch back — extension data.
4. **Unlink AniList → leave → reopen:** NOT re-linked; no auto-link spinner. "Link to AniList" → pick → linked (and future auto-links re-enabled). Same for unlinking the SOURCE from the AniList side.
5. **Three-dot menu:** the new sheet (icons, sections). Share → the three targets (extension link opens the site; AniList link opens anilist.co; ANI-KUTA link copied → opened → lands on the content). View in WebView → the app's WebView on the content's page (test one aniyomi + one CS source).
6. **Theming:** open a content with a strong cover color → resolve an episode (the streams sheet is themed), open link-sources (themed), tracking (themed) → play → the PLAYER page carries the accent (controls, subtitles sheet, qualities sheet, episode list) — on BOTH the aniyomi and CS players.
7. **Player list customization:** the gear on the Episodes header (both players) → Style/Sort/Filter tabs apply live; the magnifier searches by number/title; watched filter + dim work; the details page's OWN episode settings remain independent.
8. **Watched status on CS sub/dub lists WITH filters active:** the watched badges/progress bars show on the RIGHT rows now (the B5 fix) — verify with the watched filter on + off, a season selected, and a number group active.
