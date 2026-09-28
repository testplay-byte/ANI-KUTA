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

## 4. EXECUTION RECORD (appended as work lands)
*(to be filled during execution)*
