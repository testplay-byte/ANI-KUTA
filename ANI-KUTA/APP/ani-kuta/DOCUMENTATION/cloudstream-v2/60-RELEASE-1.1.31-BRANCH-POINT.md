# 60 — RELEASE 1.1.31 BRANCH POINT (round 69)

`release/1.1.31` cut from the round-69 feature head
(`feature/round-57-cloudstream-downloads`; CI: 35739592844 RED —
LayoutDirection from the wrong ui namespace; 35739951550 RED — the Float
coercion's Int literal; 35740400460 GREEN — both single-cause, log-diagnosed;
the honest 3-run ledger disclosed). Version bump rides THIS branch as its
first commit: **1.1.31 / 10131**.

## What v1.1.31 carries (the v1.1.30 device round — D-557)

| Fix | What shipped |
| --- | --- |
| **D-557-A (the second swipe works)** | The swipe gesture's stale pointerInput closure (it held the FIRST onToggleWatched forever; the preview's lambda captured the watched VALUE) — fixed with rememberUpdatedState in the gesture + a value-independent toggle at the preview site. |
| **D-557-B (every tap advances + the LIVE demo)** | Resolving's bare spinner is tappable→onCancel (the production §23 fix); the opt-in previewTapAll turns the whole control/badge into one tap target for the preview; the Downloading step animates +10%/s, auto-advances to Downloaded at 100%, and a tap skips it (the progress disappears). |
| **D-557-C (the two-phase snap collapse)** | The first gesture is consumed entirely by the collapse (the list never moves in it); crossing halfway snaps open↔collapsed with an animated settle; the crossed-latch guarantees one flip per gesture; the leftover fling is swallowed; GRID exempt; a layout switch re-opens. |
| **D-557-D (the numbers left the imagery)** | The classic + grid on-image EP pills are gone; the shared EpisodeNumberLabel renders the number above the title (compound S-n/E-m two-shade preserved). |
| **D-557-E (GRID redesign 2)** | Borderless pure image plate + EP label/title/capsule chips below; quiet translucent watched bubble; the scrim title and ringed badge are gone. |
| **D-557-F (the blob is ONE shape)** | TimelineBlobCardShape: the card body UNION a fully-rounded symmetric stadium bump in a single outline — no seam by construction; the spine passes behind it in two segments. |
| **D-557-G (the ghost number returns)** | The user liked the original huge numbering and asked only to theme + make it more visible — the 56sp ghost is back in the details page's own primary with a soft dark shadow; the badge experiment is gone. |
| **D-557-H (the progress glitch)** | The square-capped full-width indicator overflowed the rounded thumbnail corners; the shared EpisodeWatchProgressBar (rounded inset pill) serves all four surfaces. |

Full record: `29-CS-PREVIEW-INTERACTIONS-AND-LAYOUT-POLISH.md`.

## The device-round checklist (on v1.1.31)

1. The preview: swipe right/left repeatedly — EVERY swipe toggles.
2. The preview download: tap → spinner → tap again → advances; let the
   downloading step run — +10%/s, auto-completes to Downloaded; tap mid-flight
   — the progress vanishes and the state advances.
3. Scroll the options: the FIRST gesture only collapses the preview (the list
   holds); midway it snaps to the pinned second episode; scroll up — midway it
   snaps back open; THEN the list scrolls.
4. CLASSIC/GRID: no EP pill on the imagery; the number sits above the title.
5. GRID: the clean borderless plate + text block. TIMELINE: the symmetric
   round blob fully merged into the card. CINEMA: the themed huge number is
   back. All four: the rounded inset progress pill, no corner glitch.
6. Regression sweep (the details-page list in all four layouts, downloads,
   resolve flow, the 0.67 sheets).
