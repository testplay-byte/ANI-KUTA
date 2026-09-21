# 57 — RELEASE 1.1.28 BRANCH POINT (round 66)

`release/1.1.28` cut from the round-66 feature head `bf436e73`
(`feature/round-57-cloudstream-downloads`; CI: run 35644331755 **RED** on the
D-554 first push, run 35645124770 **GREEN** on the two-repair fix — the
honest ledger rides the cycle's closing record). Version bump rides THIS
branch as its first commit: **1.1.28 / 10128**.

## What v1.1.28 carries (the v1.1.27 device round — D-554)

The v1.1.27 device round verified ALL THREE round-65 fixes in the user's own
words (the 480p pick "was apparently playing in 480p and it was properly
showing me the video as being played in 480p"; the qualities/servers sheet
"properly showed the appropriate audio version as selected and the appropriate
currently playing resolution as selected") — the round-65 issue registry is
CLOSED. Two new items:

| Fix / feature | What shipped |
| --- | --- |
| **D-554-A (the sheet height)** | "The bottom up menu was a little bit taller in terms of its height … adjust it dynamically in such a way that its maximum height gets reduced properly and smoothly." Both CS quality surfaces were ALREADY content-fitting under a 0.70 screen-fraction cap (material3 stays pinned at 1.3.1 — no sheetMaxHeight parameter; the cap must stay content-side, the app-wide convention). The fix is a FRACTION change: `CsLinksSheet` → 0.60 (the complained-about sheet), `CsResolveSheet` → 0.60 (parity — the user's phrase covers both quality surfaces), `CsEpisodesSheet` pinned at an EXPLICIT 0.70 (a browsing surface), the app-wide 0.70 default untouched. "Dynamically" = still content-fitting (the cap binds only on overflow); "smoothly" = the accordion's expandVertically + the D-553 one-body scroll. |
| **D-554-B (the episode-list customization FIRST DRAFT)** | The dedicated settings page with the live preview, exactly like the poster page. `EpisodeListPreferences` grows 7 reactive prefs (rowStyle DETAILED/COMPACT/MINIMAL + 6 element toggles — every default equals the pre-D-554 look, so the zero-prefs experience is byte-identical). `EpisodeRow` MOVES out of the 4,243-line `DetailsScreen.kt` into its own PUBLIC file (with the style types + the pure `pillsRowVisible` algebra; the dead `DownloadEpisodeButton` deleted) so the app-module page can render the SAME renderer. `DetailsScreen` collects the prefs ONCE and rides one `EpisodeListDisplayStyle` per row. `EpisodeListSettingsScreen` replaces the `EpisodeSettingsKey` placeholder: the STATIONARY live preview (three inert sample rows through the real renderer — full/watched/bare shapes; the thumbnail a generated 120×68 PNG embedded as a data URI so the dim/grayscale/compact effects show real offline pixels) over the options (the Layout 3-way toggle, the six-element switch card, the honest "More" pointer to the details-page sheet). The Appearance row retitles "Episode list / Layout, elements, and live preview". |
| **The tests** | `feature/anime-details/impl` gains its FIRST test source set: `EpisodeListStyleTest` — 11 locks on the lenient `fromKey` (D-529), the defaults, and the `pillsRowVisible` algebra — 11/11 EXECUTED green in the sandbox (the execution CORRECTED two under-specified expectations: the relocated download control keeps the pills row alive). |
| **The CI round-2 lesson (moved code)** | The first push RED'd twice over: the extraction dropped `import androidx.compose.ui.Modifier` (~40 unresolved refs, one root), and publishing the row's `parseAudioAvailability` collided with `EpisodeListProcessor.kt`'s file-private twin (identical parameter list — same-package top-level duplicates are a declaration-site "Conflicting overloads" error, NOT shadowing; the destructuring call site then hits the componentN ambiguity between Pair and the data class). Fixed by restoring file-privacy + adding the import. LESSON: moved code needs its import list AND visibility re-derived, not assumed. |

## Scope discipline (the D-523 lesson, recorded for later rounds)

NOT in the first draft (deliberately): grid layouts, title line counts,
per-show overrides, applying to the Watch-screen clone (D-119) or the CS
episodes sheet, gesture customization, filler/score/runtime badges. The
details-page list-settings SHEET is untouched and coexists: the sheet is list
SHAPING (sort/filter/grouping), the page is row APPEARANCE.

## The device-round checklist (on v1.1.28)

1. The CS qualities/servers sheet on a 4-version episode → the sheet caps at
   ~60% of the screen, everything reachable by the one-body scroll, the
   accordion growth smooth under the cap.
2. Appearance → "Episode list" → the page opens with the LIVE preview at the
   top (three sample rows through the real renderer).
3. Flip **Compact** → the preview rows shrink + lose the synopsis; flip
   **Minimal** → number discs only; flip back **Detailed**.
4. Toggle each element (synopsis / date pill / audio pills / watch progress /
   dim watched / download buttons) and watch the preview respond live.
5. Back to any details page → the list matches the preview EXACTLY (same
   renderer, same prefs, one source of truth).
6. The list-settings SHEET still opens from the details page (sort/filter/
   grouping untouched).
7. Regression sweep: the subs sheet stays 0.55, the CS episodes sheet stays
   0.70, RAW mode unchanged, downloads unchanged, legacy episodes play.

## The CI ledger (this cycle, disclosed)

- Run 35644331755 **RED** (the D-554 first push: the missing Modifier import +
  the published parseAudioAvailability collision — two mechanical single-cause
  repairs, both diagnosed from the compile log).
- Run 35645124770 **GREEN** (bf436e73, the fix).
- The release run rides the tag (3 runs vs the ≤2 budget — 1 RED, disclosed;
  the round-63 honest-ledger pattern).

Record: AGENT-CONTEXT/download-research/26 · decisions D-554 · handoff §22.
