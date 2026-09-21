# 55 — RELEASE 1.1.26 BRANCH POINT (round 64)

`release/1.1.26` cut from the round-64 feature head `f036f855`
(`feature/round-57-cloudstream-downloads`, CI **GREEN** on the FIRST
implementation run — 35612188522). Version bump rides THIS branch as its
first commit: **1.1.26 / 10126**.

## What v1.1.26 carries (the v1.1.25 device round — the grouping CONFIRMED,
the three new demands)

| Decision | What shipped |
| --- | --- |
| **D-552 (pick targets)** | The probed resolutions become CLICKABLE. A single-DASH probed version renders ONE `CsQualityChip` per height (1080p · 720p · 480p, descending) — the declared chip AND the D-551 "Available:" text are REPLACED (the declared max rep was a lie about the version: "the actual thing was that 1080p resolution was available for the original audio too"). The pick target is **(link, height)**. Probe failure = the exact pre-D-551 rendering, never worse. |
| **D-552 (play)** | Clicking a resolution PLAYS it: `CsPlayerEngine.start/switchLink` gain `initialVideoHeight` → a one-shot `pendingVideoHeight` applied at READY via `selectVideoTrack` — the SAME `TrackSelectionOverride` the player's "Quality for this stream" section uses (a selector, not the round-62 cap). Not offered by the stream → cleared, ABR continues. `PreResolvedSeed.initialVideoHeight` + `playInitialVideoHeight` carry it end-to-end; every other play path defaults null. |
| **D-552 (download)** | Clicking a resolution DOWNLOADS it: the height rides `DownloadRequest.videoQuality` ("720p") — the pipeline's EXISTING quality carrier (queue label, DB column, data.json `quality`, `preferredHeightOf` → the planner's closest-rep pick) with ZERO schema change. Sibling audio variants stay resolution-independent — "both audio versions will be downloaded with the resolution of the main one" by construction. |
| **D-552 (the snippet fix)** | "audio 1 … was just a random small snippet" — ROOT CAUSE: the planner's SegmentTimeline branch collapsed every `r="-1"` entry to ONE segment when the MPD declares no parseable duration (`periodTicks = -1 → r = 0`); audio packagers emit the whole track as a single negative-r entry, and nothing validated. THE PLANNER borrows the video timeline's span as the period clock (two passes per Period; `videoSpanSec`/`audioGroupSpansSec`/`audioSetsPresent` on the plan). THE DOWNLOADER validates: primary audio known-short (<50% video span) = hard honest `DownloadException`; known-short siblings SKIPPED; declared-but-unplannable audio REFUSED (a silent video is garbage too); spans logged as evidence. In passing: `SegmentTemplate@duration` is already in timescale ticks (ISO 23009-1 §5.3.9.2) — the `duration * timescale` double-scaling fixed to spec. |
| **D-552 (test infra)** | VERIFICATION MILESTONE: the planner is pure JVM — the sandbox compiled AND EXECUTED the tests for the first time (kotlinc + junit). `DashManifestPlannerTest` (NEW, 8 locks incl. the snippet lock) **8/8 green executed**. The D-551 `DashManifestHeightsTest` had NEVER compiled (`$Number%05d$` is a Kotlin string template; CI runs assembleDebug only) AND its fixture broke the XML prolog — both repaired, **6/6 green executed**. 14/14 total. |

## The CI ledger (this cycle)

- Implementation: run 35612188522 **GREEN on the FIRST run** (f036f855) —
  the sandbox test execution + the exhaustive call-site sweep paid off.
- The release: the tag build (this branch).
- Budget: 2 runs planned, 1 used before the tag.

## The device-round checklist (on v1.1.26)

1. The MovieBox dual-audio episode in formatted mode → ONE "MovieBox" card,
   Hindi + Original rows, EACH showing its own clickable 1080p/720p/480p chips.
2. Tap **480p under Original** → playback starts AT 480p (the player's quality
   section shows 480p selected) — and the same tap in download mode queues a
   480p download of the Original audio.
3. Download any (version, resolution) → BOTH audio versions land in `audio/`
   at full length (audio1 is NOT a snippet; spans logged in logcat), the video
   at the chosen resolution, offline audio switching intact.
4. Regression sweep: sub/dub providers unchanged, RAW mode unchanged, the
   player's own quality section still switches live, legacy episodes play.

Full record: `AGENT-CONTEXT/download-research/24-CS-RESOLUTION-PICK-TARGETS-AUDIO-SPANS.md`.
The handoff: `AGENT-CONTEXT/HANDOFF-POSTER-NOTIFICATIONS.md` §20.
