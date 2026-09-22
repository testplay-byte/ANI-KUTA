# 59 — RELEASE 1.1.30 BRANCH POINT (round 68)

`release/1.1.30` cut from the round-68 feature head
(`feature/round-57-cloudstream-downloads`; CI: the implementation run
35724365577 **GREEN on the FIRST run** — one commit, one run, no repair
rounds). Version bump rides THIS branch as its first commit:
**1.1.30 / 10130**.

## What v1.1.30 carries (the v1.1.29 device round — D-556)

| Fix | What shipped |
| --- | --- |
| **D-556-A (the sheet settles at 0.67)** | The v1.1.29 round ruled 0.65 "proper … exactly like how I wanted" and asked for "just slightly" more ("maybe 66 or 67. Yes, I think 67 would be a great option") — `CsLinksSheet` + `CsResolveSheet` (the parity pair) ride **0.67**; `CsEpisodesSheet` stays 0.70. History: 0.70 → 0.60 → 0.65 → 0.67. |
| **D-556-B (THE ROOT CAUSE + the real preview)** | Coil 3.0.4 has **NO DataUriFetcher** (proven via the AAR's fetcher registry) — D-554's bare base64 AND D-555's `data:` scheme could never match a fetcher; the thumbnails were ALWAYS going to be empty. The demo stills are now real JPEG drawable resources (`ep_preview_still_1/2.jpg`) via `android.resource://` URIs. And the preview shows the user's LIBRARY: a RANDOM qualifying series (≥2 cached episodes, imagery preferred) loaded through the SAME stores the library/details screens read, reconstructed field-for-field like the details cache-restore (SEpisode + the full D-190 EpisodeMetadata), covers as fallback, audio pills from the app's own aggregates; demo samples only when nothing qualifies. |
| **D-556-C (the live preview is LIVE)** | Slot 1 boots fresh (40% progress), slot 2 boots watched; swipe/long-press toggles watched; every download tap cycles ALL EIGHT EpisodeDownloadState variants (nothing → resolving → queued → downloading 35 → paused → downloading 72 → error → retrying → downloaded → wraps). |
| **D-556-D (the scroll collapse)** | Scrolling the options slides the preview up under a clip exactly one episode height — the first episode hides, the second stays pinned; scroll back → both return. GRID exempt; a short options list completes the collapse at its end but never collapses on entry. Layout switches glide via animateContentSize (360ms). |
| **D-556-E (the selector)** | The "Four completely different designs" line + the per-option identity line GONE; the bottom "More" pointer card DELETED; `SegmentedToggle`'s selection pill SLIDES on a spring (all 8 call sites inherit). |
| **D-556-F (the layouts)** | CINEMA: the ghost number → a themed EP badge (the details page's own primary/onPrimary, shadowed top-end). TIMELINE: the date node melts into the card via the same-color BLOB merge (the offset stays — incorporated, not corrected). GRID: recreated (title over the image on a scrim, ringed watched check, hairline border, capsule-chip meta line). TAGS (CLASSIC+GRID): type-coded capsule chips (`EpisodeDateChip`/`EpisodeAudioChip` — SUB primary, DUB tertiary, HSUB neutral). |

Full record: `28-CS-PREVIEW-LIBRARY-DATA-AND-LAYOUT-REFINEMENTS.md`.

## The round's headline lesson (recorded in memory)

A data URI without a fetcher is just a string: BOTH empty-thumbnail "fixes"
failed because neither was checked against Coil 3's actual fetcher registry.
Verify library capabilities against the artifact (`unzip -l` the AAR), not the
migration notes.

## The device-round checklist (on v1.1.30)

1. The CS qualities/servers sheet → 67%.
2. The episode-list settings page → the preview shows a REAL library series
   (or the demo stills, actually VISIBLE now).
3. Swipe toggles watched; the download tap walks the 8-state cycle.
4. Scrolling collapses the preview to the pinned second episode; back up
   restores both; GRID never collapses.
5. The four-way switch glides; the selector pill slides; no description text.
6. CINEMA badge / TIMELINE blob / GRID recreation / capsule chips.
7. Regression sweep.
