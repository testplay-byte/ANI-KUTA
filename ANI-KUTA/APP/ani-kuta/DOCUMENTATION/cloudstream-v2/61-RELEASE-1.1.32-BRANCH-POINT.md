# 61 — RELEASE 1.1.32 BRANCH POINT (round 70)

`release/1.1.32` cut from the round-70 feature head
(`feature/round-57-cloudstream-downloads`; CI: 35755853677 RED —
rememberSplineBasedDecay unresolved on the 1.10.4 animation-core line;
35756306745 RED — rememberDecayAnimationSpec ALSO unresolved there + the
AnimatedVisibility-in-item{} receiver quirk; 35756816182 GREEN — both repairs
in one commit: exponentialDecay + the repo's own Column-wrapper pattern; the
honest 3-run ledger disclosed). Version bump rides THIS branch as its first
commit: **1.1.32 / 10132** (the feature line stays 1.1.20/10120 per D-430).

## What v1.1.32 carries (the v1.1.31 device round — D-558)

| Stream | What shipped |
| --- | --- |
| **D-558-A (the deterministic collapse)** | The NestedScrollConnection is SPLIT BY DIRECTION: DOWN-while-open consumes EVERYTHING until the preview snaps collapsed (however fast the gesture), then RELEASES the same gesture into the list; a consumed down-fling hands its momentum to the list through an exponential-decay scroll (fast swipes collapse AND keep scrolling); UP never consumes in pre-scroll — the list scrolls to the very top FIRST and only the post-scroll leftover (or the finished fling's leftover) expands the preview. |
| **D-558-B (the timeline thin gap)** | TimelineBlobCardShape keeps ONE outline with DISJOINT sub-shapes — the stadium hugs the node + date, the card starts 8dp to its right: a thin background-colored breath, "an actual blob kind of feel", with no seam possible. |
| **D-558-C (the CINEMA section)** | A dedicated LazyColumn section between Layout and Elements that exists ONLY under CINEMA (AnimatedVisibility expand/fade in, shrink/fade out): Number position (Top left/Top right), Number style (Solid/Frosted glass — the number between a translucent plate and a frost veil ON TOP), and the Watched check mark (default OFF; the dim/grayscale stays). Plus the page-bottom caption "Can be further customized by clicking episodes on the page". |
| **D-558-D (the settings search)** | Five new files (Models/Engine/Index/Navigator/Highlight): every-token-must-match relevance scoring with a synonym map (theme ⇄ ui ⇄ accent), ~45 curated entries in ONE index file, a consume-once pending-anchor navigator, and the landing animation — navigate (chained pushes for sub-pages), scroll to the anchor's item, pulse it three times. The hub gains a search bar + breadcrumb results. |
| **D-558-E (the heading is the back button)** | CollapsingHeader gains onBack: a compact 30dp arrow + 8dp breath + the whole title tappable back; ~24 call sites converted app-wide (every settings screen, History, Updates, Downloads family, Trackers, About, the Extensions family, Profile); the top-right BackAction retires; UpdatesSettings gained its first back affordance. |

Full record: `30-CS-SETTINGS-SEARCH-AND-BACK-REDESIGN.md`.

## The device-round checklist (on v1.1.32)

1. Scroll: a FAST fling must collapse the preview first and the options list
   keeps coasting afterwards; scroll up from the very bottom — the list
   reaches the top BEFORE the preview re-opens.
2. TIMELINE: the thin gap between the date blob and the card.
3. CINEMA: switch layouts — the Cinema section appears/disappears smoothly;
   the corner + frosted/solid + check-mark options reshape the live preview
   AND the details page; the check mark is OFF by default.
4. Search (Settings): "theme"/"ui"/"accent" surface the appearance results
   sorted by relevance; tapping "Palettes" lands on General, scrolls and
   pulses; tapping an Episode-list item chains Appearance → Episode list.
5. Back: every settings/more screen shows the compact arrow LEFT of the
   heading and the heading itself goes back.
6. Regression sweep (the preview interactions, the details list in all four
   layouts, downloads, resolve flow, the 0.67 sheets).
