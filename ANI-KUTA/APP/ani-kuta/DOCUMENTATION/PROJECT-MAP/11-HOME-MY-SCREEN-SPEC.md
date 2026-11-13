# 11 — THE HOME SCREEN SPEC ("My screen") — round 148

> The user's order: plan the new home properly, document everything, keep the database
> storing what it should, and REPLACE the current Browse home with it. Manga/novels join
> alongside anime in the future — the design must not preclude it. The design taste comes
> from the installed taste-skill (`.zcode/skills/design-taste-frontend/`): one hero fills
> the first screen, equal left/right edges, cards that are barely there (one step off the
> page, a hairline, no rest-shadow), real imagery as the content, quiet micro-interactions,
> section headers level across a row.

## 1. The section stack (top → bottom)

| # | Section | Data source | Format |
|---|---------|-------------|--------|
| 0 | **The greeting + THE HERO** | AniList `TRENDING_DESC` (fallback `POPULARITY_DESC`), cached | The full-width banner pager (the BrowseHero pattern: 148dp cards, 20dp corners, veil + palette splash, 12s auto-advance, tap → details via the ad gate). The hero fills the first screen's body — nothing of the next section peeks at load. |
| 1 | **Continue watching** | `WatchProgressStore.observeContinueWatching(20)` — the round-144 v4 query, ORPHANED until now (in-progress, not suppressed, `last_watched_at DESC`) | A poster rail; the progress fraction under the title; tap → details at that episode (`openDetailsByMainId` semantics — works even if the content left the library). |
| 2 | **What to watch next** | Derived: per in-progress/finished item, the next unwatched episode (highest-watched + 1 vs the metadata episode list); plus the library's `RELEASING` items' `anime_update_state.next_airing_episode/at` ("airing next") | A rail of "next-up" cards — poster + the target episode number/date. |
| 3 | **Releasing soon** | `episode_schedule.getUpcomingSchedule(now, +7d)` (library semi-join) + the schedule store's reactive feed | A rail ordered by `scheduled_at`; the subtitle = the weekday + time. |
| 4 | **Recently released** | `episode_update` (the detected feed, `discovered_at DESC`, deduped per mainId to the latest) — local truth first; the discovery rail enriches covers via main_entry/content_details | A rail of "just dropped" cards. |
| 5 | **Trending now** | AniList `TRENDING_DESC` cached (`home_trending` key) | A poster rail. |
| 6 | **Popular** | AniList `POPULARITY_DESC` cached (`home_popular`) | A poster rail. |
| 7 | **Top rated** | AniList `SCORE_DESC` cached (`home_top_rated`) | A poster rail (the tail section). |

## 2. The data & persistence design (zero schema change — by design)

- **Discovery sections** persist in the EXISTING `browse_cache` table (`section_key PK,
  data_json, fetched_at, expires_at`, the 6h TTL) under NEW keys `home_*` — the codec's
  9 fields carry everything a discovery rail needs (id/title/cover/banner/score/episodes/
  year/genres/status). The OLD "trending" key stays untouched — the Search tab's offline
  fallback co-owns it (the D-278 trap).
- **The personal rails** (continue/next/releasing/released) are DERIVED at render time
  from the tables that already own that truth (`watch_progress` v4, `anime_update_state`
  TENTHS counts — divide by 10, `episode_schedule`, `episode_update`). No new tables, NO
  new FKs to `main_entry` (the `foreign_keys=ON` CASCADE trap — the exact bug 3.sqm fixed).
- **The home preferences** (`HomePreferences`, SharedPreferences): the section order
  (a stable id list) + the per-section visibility, future-proof for manga/novels (the
  sections repeat per `content_type`; `main_entry.content_type` already carries
  'anime' — manga/novels slot in without a schema change).
- **Rate-limit discipline**: the three discovery fetches run through the SAME
  cache-first `loadSection` pattern Browse uses (cache → expired → one fetch → upsert),
  executed in parallel with partial-success fallback. No new AniList queries are required
  for round 148 (the hero + the three rails reuse `fetchBrowseSection`).

## 3. The layout contract (the taste rules, applied)

1. **Equal edges**: one 12dp horizontal gutter for every section, rail contentPadding
   aligned to it — the header text, the cards and the hero all share the edge.
2. **One hero per screen**: the hero card (148dp, 20dp corners, 1dp 10-14% ink border,
   NO rest-shadow) + the greeting own the first screen.
3. **Cards barely there**: the poster card is the art itself — 1dp hairline at
   `outlineVariant@60%`, 12dp radius, no shadow at rest, press-scale 0.95 (the existing
   Browse card anatomy, reused verbatim — the home is a SIBLING of Browse, not a new
   species).
4. **Section headers level across a row**: 14sp ExtraBold primary + a light count line;
   16dp vertical rhythm between sections (a quarter-screen of air).
5. **Quiet motion**: each section fades/expands in ONCE (the BrowseSection pattern);
   the hero auto-advances (the one moving thing); no per-card auto-motion.
6. **Loading = skeleton** (the Browse skeleton rhythm); **error = the retry state**;
   offline = cache-first with the stale-acceptable stamp.

## 4. The replacement surface (exact)

- `MainActivity` dispatch: `is AnimeBrowseKey -> HomeScreen(...)` (the key object STAYS —
  the tab is "Home"; the bottom-bar label becomes "Home"). The `navigateToDetails` ad-gate
  branch is preserved verbatim (D-272).
- `BrowsePreloader` warms the NEW home sections at the NEW render sizes (the
  cache-first semantics shared with the home VM).
- `BrowseScreen` remains in the module (its hero/card composables are REUSED by the home);
  the tab no longer serves it.
- The wizard `onFinished` + the start-tab logic are untouched (they reference the key,
  not the composable).

## 5. The future (documented, not built)

- **Manga/novels**: a `HomePreferences.contentFilter` gates which types' sections render;
  the rails read `main_entry.content_type`; the discovery sections repeat per type
  (AniList has the manga queries; the extension ecosystem follows the same shape).
- **The suggestions engine**: "What to watch next" graduates from the deterministic
  next-episode rule to a scored list (genre affinity from the library, AniList
  relations/sequels — the missing relations query is a known gap, to add then).
