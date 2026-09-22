# 58 — RELEASE 1.1.29 BRANCH POINT (round 67)

`release/1.1.29` cut from the round-67 feature head `b71e8345`
(`feature/round-57-cloudstream-downloads`; CI: run 35679780981 **RED** — the
`rememberEpisodeDisplayData` extraction renamed the display-value set but forgot
the sixth local `thumbnailUrl`, two "Unresolved reference" lines one root cause —
and run 35680163304 **GREEN** on the one-commit repair; the moved-code
LOCAL-completeness audit joins the D-554 import-completeness audit). Version
bump rides THIS branch as its first commit: **1.1.29 / 10129**.

## What v1.1.29 carries (the v1.1.28 device round — D-555)

The v1.1.28 device round rejected the D-554 first draft on BOTH fronts — and the
episode-list rejection was a DESIGN LESSON, not a bug report.

| Fix / redesign | What shipped |
| --- | --- |
| **D-555-A (the sheet height finds the middle)** | 0.60 was "way too smaller"; "a bit more taller percentage but smaller than the previous one" → both CS quality surfaces (`CsLinksSheet` + `CsResolveSheet`, the D-554-A parity pair) ride the MIDDLE **0.65** — between the v1.1.27 complaint (0.70) and the over-correction (0.60). `CsEpisodesSheet` stays explicitly 0.70 (never complained about). The DESIGN-SYSTEM sheets doc carries the full 0.70→0.60→0.65 history. |
| **D-555-B (FOUR completely different layouts)** | The user's verdict: "switching between the three available layouts is definitely not good … a complete UI redesign on switching between each and every single one of them … a completely different layout, a completely different management system, a completely different look and feel … rather than just hiding some features like synopsis or maybe adjusting the shape a little bit". The D-554 row variations (DETAILED/COMPACT/MINIMAL) DIE; `EpisodeListRowStyle` becomes **CLASSIC / GRID / TIMELINE / CINEMA** dispatched through the NEW `EpisodeListEntry` (the ONE entry point both the details list and the settings preview call — the D-481 doctrine at the list level). **CLASSIC** = the unchanged user-verified `EpisodeRow`. **GRID** = a two-column poster wall (full-bleed 16:9 cells, the EP badge + the compact `EpisodeDownloadBadge` overlaid, watch-progress on the image edge, title + date/audio chips BELOW the image, watched = grayscale + dim + a centered check, LONG-PRESS toggles watched — a half-width cell cannot host the horizontal swipe). **TIMELINE** = an air-date schedule spine (a continuous rail — the call site drops the per-item vertical gap for this layout; node labels carry the release date as the PRIMARY element with an "EP n" fallback; nodes fill when watched; a compact card with a trailing thumbnail hangs off every node). **CINEMA** = full-bleed banner cards (the image IS the card, bottom gradient scrim, a HUGE ghost episode number top-end via `ghostEpisodeNumber`, overlaid title + ONE translucent date/audio/WATCHED chips pill, the translucent download badge bottom-end). |
| **The shared machinery** | `EpisodeDownloadBadge` mirrors the full control's 8-state contract in ONE 32dp circle (tap = download/pause/resume/retry/play; in-flight = cancel; delete stays on the downloads page — no gesture room). `SwipeToToggleWatched` carries the Phase WP gesture VERBATIM (background icon + threshold + haptics unchanged; CLASSIC/TIMELINE/CINEMA share it). `rememberEpisodeDisplayData` consolidates the display resolution (D-306 extension-first, the D-230 cover fallback, BOTH date label sizes from one epoch, the HSUB-distinct audio parse) into ONE pass every layout consumes. |
| **The preview (the user's spec: EXACTLY two)** | The settings page's stationary live preview shows exactly TWO samples (fresh with a 40% progress bar + watched with the dim treatment) through the same dispatcher — GRID pairs them side-by-side like the real wall — both carrying REAL thumbnail imagery: two GENERATED anime stills embedded as PROPER `data:image/jpeg;base64,` URIs (448×252 q64, ~41KB total). THE ROOT CAUSE of the user's "the thumbnail images remain empty": the D-554 draft's constant was a BARE base64 payload WITHOUT the `data:` scheme prefix — Coil's DataUriFetcher never matched and AsyncImage silently drew nothing. Release dates render on both samples, and the four-way Layout toggle states each option's identity line under the selector. |
| **The plumbing + migration** | The details-list GRID branch chunks `subDubDisplayEpisodes` into paired rows INSIDE the existing LazyColumn (the outer list remains THE virtualizer — no nested grid); the shared per-episode body extracts into one local `renderEpisode` composable lambda both branches call. Legacy stored keys (DETAILED/COMPACT/MINIMAL) fall back to CLASSIC via `fromKey`. |
| **The tests** | `EpisodeListStyleTest` re-anchored + 5 new locks (the legacy migration, `formatShortDate`, `timelineNodeLabel`, `ghostEpisodeNumber`) — **13/13 EXECUTED green** in the sandbox against the byte-exact extracted production declarations. |

## The lessons (both recorded in memory)

1. **[MISTAKE] A data URI without its scheme prefix is not a data URI** — Coil
   renders nothing, silently. The FULL `data:<mime>;base64,<payload>` string IS
   the contract.
2. **[INSIGHT] "Customizable" means different LIVES for the data, not different
   HIDINGS of it** — layout options must be genuinely different compositions (a
   list, a wall, a schedule, a stage); a shared renderer with visibility flags
   cannot deliver that.

## The device-round checklist (on v1.1.29)

1. The CS qualities/servers sheet → taller than v1.1.28 (65%), shorter than
   v1.1.27 (70%) — the middle ground.
2. Settings → Appearance → "Episode list" → the preview shows TWO episodes with
   REAL imagery; flip the four layouts and watch the preview TRANSFORM (2 rows /
   2 wall cells / 2 spine entries / 2 banners).
3. The details-page list: switch layouts there too — GRID = two columns,
   TIMELINE = one continuous spine with dates, CINEMA = full-bleed banners;
   check the watched states and long-press-to-toggle on GRID.
4. The download badge in GRID/TIMELINE/CINEMA: tap → spinner → ring % → check →
   tap plays.

Untouched by design: the prefs keys, the CLASSIC row's visuals, the swipe
algebra, the season/organize systems, the download pipeline, the resolve flow,
the other sheets' heights. Record: download-research/27.
