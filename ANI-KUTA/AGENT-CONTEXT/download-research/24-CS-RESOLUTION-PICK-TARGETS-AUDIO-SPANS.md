# 24 — CS RESOLUTION PICK TARGETS + THE AUDIO SNIPPET FIX

**Round 64 · D-552 · the v1.1.25 device round's verdict + its three demands.**

## 1. What the v1.1.25 device round validated (DO NOT TOUCH)

The D-551 grouping works on-device — the user's own words:

> "Now when I clicked on an episode, it did show me everything as one single section
> and it showed me the two audio versions and such."

ONE "MovieBox" card, Hindi + Original version rows inside it. The accordion, the
vocabulary, the sibling-audio download engagement (both audio versions downloaded for
the first time on this episode) — all CONFIRMED. Untouched this round: RAW mode, the
sub/dub grouping, the resolve flow, the publish layout (episodes/ + audio/ + .dashmeta),
data.json's schema, the queue, the scanner.

## 2. The three new demands

1. **The probed resolutions are display-only.** "It did say that the original version had
   720p resolution available and 480p resolution available but it just said that those
   resolutions were available. I was not able to click on them. I was not able to
   directly download them or anything like that." — The D-551 "Available:" line is a
   plain Text; the only pick target is the whole link.
2. **The declared quality is a lie.** "It mentioned that 720p was original and that 1080p
   was Hindi but apparently it was not like that. The 720p and 1080p were just normal
   ones and the actual thing was that 1080p resolution was available for the original
   audio too." — The provider stamps each variant's manifest with its MAX rep height and
   the sheet rendered that as the version's quality chip: "Hindi 1080p" / "Original
   720p" implied resolution is a property of the audio version. It is not — every
   variant's manifest lists the same spread (1080/720/480 on this shape).
3. **audio1 was a snippet.** "This time it downloaded a total of two audio versions. One
   was marked as audio 1 and the other one was marked as audio 2 but apparently the
   audio 1 was not a proper one. It was just a random small snippet of it." — The
   sibling pipeline engaged (audio2 exists) but the PRIMARY audio's plan collapsed.

The target UX (the user's spec): "It should give proper options exactly like how it
would for the other extensions. After resolving the videos in the other extensions, it
appropriately shows all the available resolutions and I can click on any of the
resolutions. It will directly start to play that resolution or directly start to
download that resolution with the appropriate audio version alongside it." — and the
default download shape stays: "In general both audio versions will be downloaded with
the resolution of the main one."

## 3. The audio snippet — root cause (confirmed by code + elimination)

`DashManifestPlanner.planFromTemplate` (:core:download), the timeline branch:

```kotlin
val periodTicks = if (periodDurationSec > 0) (periodDurationSec * timescale).toLong() else -1L
…
var r = s.getAttribute("r").toLongOrNull() ?: 0L
if (r < 0) {
    r = if (periodTicks > 0) (periodTicks - t) / d - 1 else 0L   // ← periodTicks = -1 → r = 0
    if (r < 0) r = 0L
}
```

`periodDurationSec` = `Period@duration ?: MPD@mediaPresentationDuration` — when the MPD
carries NEITHER (or an unparseable form), `periodTicks = -1` and EVERY `r="-1"` timeline
entry collapses to exactly ONE segment. Audio packagers commonly emit the whole track as
a single `<S t="0" d="…" r="-1"/>`: the audio plan becomes init + 1 segment ≈ a
few-second "random small snippet", while the video timeline (explicit S entries / r
values) stays complete — the exact asymmetry the user saw. The timeline-LESS branch
(`SegmentTemplate@duration`) FAILS LOUDLY in the same situation ("Manifest declares no
segments and no period duration") — the timeline branch was the only silent one. And
nothing downstream checks: no plan-level span comparison, no per-group sanity, `estimated
Bytes` is a hint only. Whatever the plan enumerated and the CDN answered, publish shipped.

## 4. The fix — cross-derivation + validation

### 4.0 Two latent bugs the verification round exposed (fixed in passing)

The sandbox gained a JVM Kotlin toolchain this round, which let the planner's pure
logic be COMPILED AND EXECUTED for the first time (see §7). Running the D-551 test
set immediately surfaced two pre-existing test-infrastructure bugs that CI never
caught (CI runs `assembleDebug` only — test sources never compile):

1. **`$Number%05d$` is a Kotlin string template.** Inside a raw string,
   `$Number` resolves as an expression — `classifier 'class Number' does not have
   a companion object, so it cannot be used as an expression` — a COMPILE ERROR
   in both `DashManifestHeightsTest` (D-551) and the new planner test. Fixed with
   the `${'$'}` escape idiom.
2. **The fixture prolog was not at document start.** The D-551 `mpd()` helper
   inserted column-0 set fragments into an indent-then-trim template — the common
   indent froze at 0, whitespace stayed BEFORE `<?xml?>` (spec-forbidden), and
   every parse fatally errored → the parser's tests would have failed 2/6 had
   they ever run. Rebuilt with `buildString` (prolog appended first).

Both are fixed in this round; the D-551 test set is now actually green (6/6).

### 4.0b The timeline-less branch's spec deviation (fixed)

`SegmentTemplate@duration` is ALREADY in timescale ticks (DASH ISO 23009-1
§5.3.9.2), but the planner computed `duration * timescale` — double-scaling every
spec-correct manifest (`duration="96000" timescale="48000"` = 2s became a 96,000s
"segment"). Real timeline-less manifests failed loudly pre-D-552; post-cross-
derivation the wrong math would have masked a 1-segment snippet behind a huge
span. Fixed to `segmentTicks = duration`; the timeline-less audio set now expands
against the derived span exactly like the timeline shapes.

### 4.1 The planner derives the period span from the video timeline

`DashManifestPlanner.parseDocument` restructures into TWO passes per Period:

- **Pass 1 (video)**: plan every video AdaptationSet exactly as today. Each planned rep
  records `spanSec: Double?` — the EXACT media span of its timeline (Σ (r+1)·d / timescale)
  when every entry resolved to a concrete count, null when a negative-r entry collapsed
  (unknown periodTicks) or the addressing has no timeline (SegmentList/BaseURL).
- **Pass 2 (audio)**: for each audio AdaptationSet,
  `periodDurationSec = explicit ?: videoSpanSec ?: 0.0` — the video timeline IS the
  period's clock when the manifest forgot to declare it. An audio `r="-1"` now expands
  against the derived ticks → the audio plan covers the whole episode.

Side effects on the plan (all additive):
- `DashSegmentPlan.videoSpanSec: Double?` + `audioGroupSpansSec: List<Double?>` (one per
  audio group) — the validation input.
- `estimatedBytes` improves for exactly the affected shape: the duration used for the
  bandwidth estimate is `mediaPresentationDuration ?: derived video span` (was 0 →
  indeterminate progress on these manifests; now a real hint).

Deliberately NOT changed: the audio rep selection (`maxByOrNull { bandwidth }` — the
snippet is a segment-count collapse, not a rep pick; changing the pick on a hypothesis
could regress working downloads), `videoRepIds`/`audioRepIds` semantics, the composer,
the pruner, the publish layout.

### 4.2 The downloader validates spans (the pipeline never downloads garbage)

`DashDownloader` after planning (and after each sibling plan):

- log line: `videoSpan=Xs audioSpans=[Y1s, Y2s…]` (null = "unknown").
- **Sibling variants**: a variant whose audio span is known AND < 50% of its video span
  is SKIPPED with a warning (D-550 best-effort semantics — a snippet variant never lands
  as audio2; the picked variant never pays).
- **The primary**: the same check failing is a hard `DownloadException("The manifest's
  audio track plans only Xs of media for a Ys episode — the audio timeline is truncated;
  try again later or pick another source")`. An honest, retryable failure beats a
  published snippet. With 4.1 in place this path should never fire; it exists so NO
  future provider shape can silently ship a truncated audio track again.

Threshold 0.5 = a detector with huge margin: the floor-division tail loss is one segment
(~1% of an episode), legitimate audio matches video length; a real snippet is orders of
magnitude below.

## 5. The resolutions become pick targets

### 5.1 The sheet (formatted mode)

`CsServerCard` — a version with `availableQualities != null` (the groupServers gate:
ONE DASH link, probed, 2+ heights) now renders ONE CLICKABLE `CsQualityChip` per probed
height (descending), replacing BOTH the declared-quality chip and the "Available:" text:

```
Hindi
[▶ 1080p] [▶ 720p] [▶ 480p]     ← each chip picks (link, height)
Original
[▶ 1080p] [▶ 720p] [▶ 480p]
```

- click → `onPickVideo(link, height)` — play pins the height, download takes it.
- long-press/copy/debug affordances ride the same single link as today.
- no isSelected marking on height chips (all chips of the current link would light up —
  misleading; the player's "Quality for this stream" section remains the live truth).
- versions without probed heights keep the EXACT current rendering (declared chips,
  no line) — probe failure = pre-D-551 behavior, never worse.

`onPickVideo` signature: `(CsVideoLink) -> Unit` → `(CsVideoLink, Int?) -> Unit`
(`CsServerAccordion`, `CsServerCard`, `CsRawLinkList` — raw passes null; both sheets'
call sites updated). The misleading declared chip disappears wherever the truth is
known — demand 2 answered by construction.

### 5.2 Play: the one-shot height pin (the engine's own override mechanism)

The engine's existing "Quality for this stream" pick is `selectVideoTrack` — a
`TrackSelectionOverride` on a live track group (post-prepare; `setMaxVideoHeight` is a
CAP, not a selector — the round-62 lesson). The sheet pick rides the SAME mechanism:

- `CsPlayerEngine.start(link, startPositionMs = 0L, initialVideoHeight: Int? = null)` and
  `switchLink(link, initialVideoHeight: Int? = null)` set a one-shot
  `pendingVideoHeight`; at READY (the existing track-census block, tracks live) 
  `maybeApplyPendingVideoHeight()` finds the video track whose height matches and applies
  `selectVideoTrack` (the user's own later picks replace it; not found → cleared, ABR
  continues — never worse). `reset()` clears it. startInternal's constraint-clear runs
  before prepare; the apply lands at READY — ordering safe.
- `CsWatchViewModel`: `PreResolvedSeed.initialVideoHeight`, `CsWatchUiState.
  playInitialVideoHeight`, `requestPlay(..., initialHeight: Int? = null)`,
  `selectLink(link, initialHeight: Int? = null)` — every other requestPlay caller
  (autoStart, error-advance, offline) defaults null: zero behavior change.
- `CsResolveSheet.pick(link, height)` seeds with the height; `CsLinksSheet.onLinkSelect`
  carries it (in-player sheet chips pin the same way on a keep-position switch).

### 5.3 Download: the chosen height rides `videoQuality` (zero schema change)

- `CsResolveSheet.onDownload` gains the height: `((CsWatchKey, CsVideoLink,
  List<CsSubtitle>, List<CsVideoLink>, Int?) -> Unit)?`.
- `CsDownloadRequestBuilder.build(..., chosenHeight: Int? = null)`:
  `videoQuality = chosenHeight?.takeIf { it in 100..4320 }?.let { "${it}p" } ?: link.
  qualityLabel`. That string is ALREADY the pipeline's quality carrier (queue UI,
  DB column, data.json `quality`, `preferredHeightOf` → the planner's closest-rep pick)
  — the chips feed it a better value; no field, no migration, honest display.
- Sibling audio variants ride unchanged (audio groups are resolution-independent) —
  "both audio versions at the resolution of the main one" is the existing D-550/D-551
  shape; the chips add precision without touching it.

## 6. Failure matrix

| failure | behavior |
| --- | --- |
| probe failed / not probed / multi-link version | declared chips exactly as pre-D-551 (no chips-per-height, no line) |
| picked height missing from the manifest (stale probe) | planner takes the closest rep (existing metric) |
| playback stream offers no matching height at READY | pending cleared, ABR continues (logged) |
| primary audio span known AND < 50% video span | hard DownloadException — the queue shows the honest message; retryable |
| sibling audio span known AND < 50% | variant skipped (warning) — the picked variant never pays |
| span unknowable (no video timeline, SegmentList) | no validation (cannot know) — today's behavior preserved |
| sheet dismissed mid-probe / mid-pick | unchanged (probe scope dies with the sheet) |

## 7. Tests — EXECUTED IN THE SANDBOX, not just written

The planner is pure org.w3c.dom + JVM (no Android), so this round built a real JVM
verification loop for the first time in the project's history: kotlinc + junit-4.13.2
on the raw sources. `DashManifestPlannerTest` (NEW, :core:download) — **8/8 green
executed**:

1. the snippet lock (duration-less MPD + audio r="-1" → expands to the video span,
   1801 parts, spans 3600s/3600s);
2. the explicit-duration regression lock (mediaPresentationDuration still drives it);
3. the validation input lock (a genuinely short audio timeline reports its small span);
4. the timeline-less @duration expansion lock (the §4.0b spec fix);
5. the loud timeline-less-without-duration lock (SegmentList video, no clock to borrow);
6. the SegmentList unknown-span lock;
7. the preferredHeight closest-rep lock (D-539 behavior untouched);
8. the multi-period validation-skip lock.

`DashManifestHeightsTest` (D-551, repaired §4.0) — **6/6 green executed** for the
first time since it was written. The remaining (Compose/Android) changes are
verified by the exhaustive call-site sweep + a line-by-line diff review; CI's
assembleDebug remains the only compiler that can see those.

## 8. Blast radius

CsSourceListUi (chips + signatures) · CsResolveSheet (pick + onDownload) · CsPlayerSheets
(onLinkSelect) · CsWatchScreen (trigger + wiring) · CsWatchViewModel (seed/state/
requestPlay/selectLink) · CsPlayerEngine (pending height + one-shot apply) · MainActivity
(handleCsDownloadPick + lambda) · CsDownloadRequestBuilder (chosenHeight) ·
DashManifestPlanner (two-pass + spans) · DashDownloader (validation + logs) · 1 new test
set. Streaming ABR behavior, RAW mode, sub/dub grouping, the player's own quality
section, offline loaders, queue/retry, notifications, storage, scanner, data.json
schema: untouched.
