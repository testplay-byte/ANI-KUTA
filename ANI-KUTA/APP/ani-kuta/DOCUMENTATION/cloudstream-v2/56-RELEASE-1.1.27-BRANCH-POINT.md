# 56 — RELEASE 1.1.27 BRANCH POINT (round 65)

`release/1.1.27` cut from the round-65 feature head `942dc28b`
(`feature/round-57-cloudstream-downloads`, CI **GREEN** on the FIRST
implementation run — 35625463149). Version bump rides THIS branch as its
first commit: **1.1.27 / 10127**.

## What v1.1.27 carries (the v1.1.26 device round — the selection-TRUTH round)

The device logcat (Moon Knight EP 2, MovieBox, 4 audio versions all probed
1080/720/480) PROVED the D-552 chain exact — `picked 480p → start-height pin:
480p → video track selected: 480p (g0/t2)` → decoder 856x480. **480p genuinely
played**, yet the user still "had concerns that maybe it was playing in 1080p".
That sentence was the diagnosis: the truth was UNOBSERVABLE. Three defects hid
it; all three died in D-553:

| Fix | What shipped |
| --- | --- |
| **D-553 (the height memory)** | `CsSourceMemory` grew its second half: the picked RESOLUTION rides WITH the server per mainId. Written by BOTH pick sites (`CsResolveSheet.pick`, `selectLink`; a null-height pick CLEARS — a declared chip cannot pin, so it must not fake a preference). Read by BOTH lost paths: the same-key re-entry and `autoStart` (when the remembered server matched) now pin the recalled height. Pre-D-553 these paths re-requested play with NO height → ABR climbed to 1080p over the user's 480p pick. A stream lacking the remembered height self-heals through the D-552 "not offered → ABR" semantics — never worse, never broken playback. |
| **D-553 (the honest markers)** | `selectedTrackLabel` was computed from `group.isSelected` — true for the WHOLE group when ANY track plays — so it always named the FIRST rep (the TOP quality): the marker would have LIED HIGH. Now `group.isTrackSelected(track.trackIndex)` — the ACTUALLY-playing rep — and the open-effect keys carry `engineState.videoHeight` so the sheet refreshes LIVE while ABR moves. In the accordion, the D-552 "no marking at all" compromise DIES: exactly ONE probed chip lights (current link × live decoder height), the playing audio version's label tints primary (header + expanded rows). |
| **D-553 (the reachable section)** | "It would not show me the options for qualities for this stream" — a LAYOUT clipping: `CsLinksSheet` was a non-scrolling Column; one tall MovieBox card (1 server × 4 versions × 3 chips) pushed "Quality for this stream" out of the clipped bottom — composed but invisible. The sheet body now scrolls as ONE unit (`verticalScroll`; accordion/raw-list/variants convert LazyColumn→Column — tiny lists; a LazyColumn under verticalScroll would measure with infinite height constraints and crash). The resolve sheet gets the same one-body scroll (parity). Plus the "Now playing: <audio> · <label>" line under the hint — the answer at a glance. |
| **D-553 (the short forms)** | "If there is not enough space to show the full names of the available audio versions then their simplified or minimified versions should show." At 3+ versions the collapsed header renders `shortAudioLabel` — deterministic (Hindi→HIN, Original→ORIG, Malayalam→MAL, Tamil→TAM, curated map + 3-letter fallback, SUB/DUB pass through), no text measuring, no layout loops; ≤2 versions keep full names; maxLines=1+Ellipsis as the tail. 6 new unit tests lock it (sandbox-executed green via kotlinc pre-push). |
| **D-553 (the decoder truth log)** | `onVideoSizeChanged` now emits `video size: WxH — playing <label>` (deduped on size) — the decoder's own verdict, making the playback resolution permanently diagnosable from logcat. The follow-up captures no longer need inference. |

## The CI ledger (this cycle)

- Implementation: run 35625463149 **GREEN on the FIRST run** (942dc28b).
- The release: run 35626504172 **RED** — the workflow titles the GitHub
  Release from the TAG ANNOTATION'S SUBJECT (`git tag -l
  --format='%(contents:subject)'`), and the tag was cut carrying the FULL
  release-commit message (1,900+ chars): `HTTP 422: name is too long (maximum
  is 256 characters)`. The build, the APK gates and the asset staging were all
  green — only the release-create call failed.
- The re-cut tag (concise subject, the v1.1.26 convention): run 35627061143
  **GREEN** — v1.1.27 LIVE with `ani-kuta-v1.1.27-debug-arm64-v8a.apk` +
  `SHA256SUMS.txt`.
- Budget: **3 runs used vs the ≤2 budget — 1 RED, disclosed** (the round-63
  pattern: the honest ledger beats a quiet overage). LESSON locked in
  memory: **the tag annotation's subject is the release title — keep it ≤256
  chars; the long why belongs in the COMMIT message, never the tag.**

## The device-round checklist (on v1.1.27)

1. The MovieBox multi-audio episode (4 versions) → ONE card, header chips show
   HIN · ORIG · MAL · TAM (short forms), nothing truncates.
2. Tap **480p** → playback starts AT 480p; open Qualities → the top line reads
   "Now playing: <audio> · 480p", the 480p chip is LIT in the version list,
   the version label is tinted, and "Quality for this stream" is REACHABLE
   (scroll) with 480p marked there too.
3. Back out → tap the SAME episode again (no sheet) → playback resumes AT 480p
   (the remembered height; logcat: `play request: … startAt=480p` on re-entry,
   `start-height pin: 480p → …`).
4. Next episode auto-advance → starts at 480p when the remembered server
   matched (`autoStart: … (remembered server) at 480p (remembered height)`).
5. Regression sweep: sub/dub providers unchanged, RAW mode unchanged, download
   mode unchanged (both audio versions at the chosen resolution), legacy
   episodes play.

Full record: `AGENT-CONTEXT/download-research/25-CS-SELECTION-TRUTH-AND-HEIGHT-MEMORY.md`.
The handoff: `AGENT-CONTEXT/HANDOFF-POSTER-NOTIFICATIONS.md` §21.
