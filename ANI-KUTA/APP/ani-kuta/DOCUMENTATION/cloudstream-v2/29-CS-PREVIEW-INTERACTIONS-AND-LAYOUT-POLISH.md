# 29 — CS: THE PREVIEW WORKS TWICE + THE SNAP COLLAPSE + THE LAYOUT POLISH (round 69 / D-557)

Round 69 implements the v1.1.30 device round. The verdict split cleanly: the
0.67 sheets are "100% perfect", the real-library preview works ("the actual
live previews are shown with the proper details … it switches to a random live
preview from the library, which is good"), and the layout switching is "quite
satisfactory and quite to my taste" — and then a punch list whose unifying
theme was **interactions that worked exactly once**. Shipped as `3af7e99d` +
two single-cause CI repairs (`cc9ebc00`, `58473444`).

## D-557-A — the second swipe was dead (the stale-closure bug)

The user: "I swiped right on a live preview which was marked as watched. It
got marked as unwatched. Then I swiped right on it again and it was not being
marked as watched again."

ROOT CAUSE, code-verified: `SwipeToToggleWatched`'s gesture lives in
`pointerInput(Unit)` — it captures the FIRST composition's
`onToggleWatched` lambda and NEVER recaptures. The preview's lambda was
`{ watchedOverride[url] = !isWatched }` — it captured the watched VALUE, so
swipe 2 executed the stale lambda and wrote the SAME value back. (The details
page never showed this because its callback calls the store's toggle —
value-independent.)

THE FIX, both layers: the gesture reads the freshest callback through
`rememberUpdatedState` (protects every caller forever), and the preview's
toggle reads the map at execution time
(`watchedOverride[url] = !(watchedOverride[url] ?: defaultWatched)`).

## D-557-B — the second tap was dead + the LIVE download demo

The user: "clicking the download button properly changes the state to the
spinning downloading one, and if I tap that exact same spinning one again, it
does not change to the next state." Plus the spec: a state where the progress
jumps **+10% every second**, auto-advancing at 100% "without me even
pressing", and a tap before completion advancing immediately ("the progress
will disappear automatically").

- THE DEAD STATE: `EpisodeDownloadControl`'s Resolving was a BARE spinner —
  not clickable, violating its own CORE_RULES §23 ("every state is
  interactive") while the badge's contract already cancelled in-flight
  states. Now tappable → onCancel (visually identical). Production §23 fix.
- THE DEMO CHANNEL: an opt-in `previewTapAll: (() -> Unit)?` on the control,
  the badge, the `EpisodeRowActions` bag, and `EpisodeListEntry` — when set,
  the WHOLE widget becomes one tap target (the Downloading/Downloaded
  menu-openers route through it too), so the preview walks the full cycle on
  the PRODUCTION widgets' own visuals. Production call sites pass null →
  byte-identical behavior.
- THE CYCLE: NotDownloaded → Resolving → Queued → **Downloading (LIVE)** →
  Paused → Error → Retrying → Downloaded → wraps. The Downloading step is a
  `LaunchedEffect` animation keyed on the step: +10%/s, auto-advance to
  Downloaded at 100%, cancelled mid-flight by a tap (the progress
  disappears).

## D-557-C — the collapse became a TWO-PHASE SNAP

The user: "the very first scroll should not scroll the bottom section, but it
should only scroll the live preview. And if the live preview has been
scrolled midway … it will automatically snap to the next state where only one
live preview shows … If the user scrolls midway past the live preview, then
it will automatically snap to the full view … And the bottom scroll will
happen afterwards."

The D-556 continuous fraction (tracking the list's own scroll) is replaced by
a `NestedScrollConnection` between the options list and the screen:

- While a phase change is possible (open + down-drag, or collapsed +
  up-drag) the connection consumes the WHOLE drag — the list does not move.
- Crossing the halfway point of the collapse distance (first episode height
  + gap, measured) snaps to the other phase with an animated settle — no
  in-between rest state.
- A **crossed latch** guarantees one drag flips exactly once (an early draft
  reset the accumulator on crossing, so a long drag crossed twice: collapse →
  snap → animated-reopen bounce — caught in review).
- The gesture's leftover fling is swallowed (`onPreFling` → Zero while
  engaged); a 180ms silence window (the settle job) ends the gesture and
  re-arms the connection.
- GRID never engages; a layout switch always re-opens (a stale collapsed
  state under GRID would clip forever).

## D-557-D — the numbers left the imagery

The user: the tags "are shown on the top left corner of the image … the image
gets covered way too much, the UI looks bad" (both CLASSIC and GRID).

The shared **`EpisodeNumberLabel`** renders the number in the TEXT BLOCK
above the title — a quiet themed mini-label ("EP 5" in the details page's
primary), with the D-317 compound "S-n/E-m" two-shade rendering preserved
verbatim. The on-thumbnail pills are gone from both layouts; the thumbnails
show imagery only.

## D-557-E — GRID redesign 2

"Recreate it … much more clean … much more beautiful." The cell is now
BORDERLESS and QUIET: a pure 16:9 image plate (16dp radius, no border, no
background box, no scrim, no pill), the small translucent download badge
top-end, the watched treatment = grayscale + dim + ONE small translucent
check bubble bottom-start, and the text block below (EP label → title (dimmed
when watched) → the capsule chips). The ringed watched badge and the
title-over-image scrim are gone.

## D-557-F — the TIMELINE blob, rebuilt as ONE shape

The user: "it is not symmetric, like at the bottom there is a bit more blob
than at the top, and also the blob is not feeling like a blob … the blob does
not merge smoothly with the right side. There is a complete cutout."

The D-556 neck stopped short of the card's left edge (the 8dp spacer + the
card's rounded corner left the gap). The fix removes the seam BY
CONSTRUCTION: the card spans the FULL row width and its background is ONE
custom `TimelineBlobCardShape` — the rounded card body UNION a fully-rounded
stadium bump around the node + date in a single `Outline.Generic` path. There
is no junction to see. The bump is symmetric around the node/label group
("around the actual date"), the spine draws in TWO segments passing behind
the blob, the content padding clears the bump, and the bump bottom clamps to
the shape height for short cards.

## D-557-G — the CINEMA ghost number is BACK

"I liked the previous numbering on it … the style of numbering which it had,
the proper one. I liked it. But what I told you to do was to just theme it
and make it a bit more visible. But apparently you did not do that, and you
added the bad tag kind of numbering."

The D-556 badge experiment is GONE. The huge top-end number returns — 56sp
zero-padded (`ghostEpisodeNumber`, its unit lock intact) — now **themed**:
the details page's own primary instead of flat white-alpha, at near-full
alpha with a soft dark shadow (18f blur) so it reads over any imagery.

## D-557-H — the watch-progress glitch, fixed everywhere

"There is a little bit glitch on the classic one." ROOT CAUSE: the square
caps of the full-width M3 `LinearProgressIndicator` drew over the thumbnail's
rounded bottom corners (the wrapper Box is not clipped). The shared
**`EpisodeWatchProgressBar`** — a rounded inset pill on a translucent track —
now serves all four surfaces (classic thumbnail, grid plate, timeline card,
cinema banner), inset from the edges via caller padding.

## The CI ledger (disclosed)

3 implementation runs vs the ≤2 budget: 35739592844 RED (`LayoutDirection`
imported from `ui.graphics` — it lives in `ui.unit`; three errors, one root
cause), 35739951550 RED (`(Int+Float).coerceAtLeast(1)` — the generic fixates
T=Float from the receiver, the Int literal mismatches), 35740400460 GREEN.
Both repairs were single-cause, diagnosed from the compile log. New lessons:
the Shape interface's parameter packages split across two ui namespaces; a
generic coercion's literal must be the receiver's type.

## Untouched BY DESIGN

The 0.67 sheets, the library-data preview loader, the layout-switch
animation, the prefs keys, the swipe algebra otherwise, the download
pipeline, the resolve flow, the season/organize systems, the details-page
list structure.
