# 54 — RELEASE 1.1.25 BRANCH POINT (round 63)

`release/1.1.25` cut from the round-63 feature head `392c79b3`
(`feature/round-57-cloudstream-downloads`, CI **GREEN** on the FIRST
implementation run — run 35592484357). Version bump rides THIS branch as its
first commit: **1.1.25 / 10125**.

## What v1.1.25 carries (the v1.1.24 device round — the user's verdict: "quite
satisfied with the overall results" — the whole D-548/D-550 offline chain
confirmed; the two demands from the MovieBox dual-audio episode)

| Decision | What shipped |
| --- | --- |
| **D-551 (grouping)** | The link-name vocabulary learns LANGUAGE-AUDIO. "MovieBox (Hindi Audio)" + "MovieBox (Original Audio)" used to render as TWO separate server cards; formatted mode now shows ONE "MovieBox" card whose audio versions are **Hindi** and **Original** (the user's spec: "it should not show two options when formatted source is turned on... one option and in that option the audio version options as Hindi and original"). `CsAudioTag` gains the language-audio pass (bracketed "(Hindi Audio)" + whole-segment "MovieBox - Hindi Audio" forms, a ~35-entry abbreviation map, conservative exclusions — multi-audio stays a decoration, sub/dub keep word-pass priority, no free-form matching). |
| **D-551 (download)** | The D-550 sibling pipeline finally ENGAGES for this provider shape: `siblingAudioVariants` matched siblings by RAW name equality (never equal → zero AUDIO_VARIANT tracks → only one audio version downloaded). It now matches on the derived SERVER (`CsServerNames.of` — the SAME function the sheets group with, moved to :core:cs-player because a third consumer needs the identical derivation) → the sibling manifest's audio sets download as extra groups, the composed sidecar labels them "Hindi"/"Original", and the OFFLINE audio selector offers both exactly like the streaming one. `.data.json`'s `videoAudio` records "Hindi"/"Original" instead of "Default" (same field, richer truth). |
| **D-551 (resolutions)** | "If it is possible to show all the available video resolutions, then I would be quite happy with it, like 1080p, 720p, and 480p": `DashManifestHeights` (:core:common, pure — the pruner's hardened-factory discipline, audio sets guarded) + `CsDashQualityProbe` (the LINK'S OWN headers — the MovieBox manifest needs its CloudFront cookie; bounded 5s/8s/2MiB; silent by contract) + additive `CsVideoLink.availableQualities` → the formatted server card renders **"Available: 1080p · 720p · 480p"** under a version whose single DASH link was probed — the same list the player's per-stream quality section shows. The chip stays the pick target (ABR serves every listed rep). The probe runs OFF the resolve path; the resolver flow is byte-identical. |

## The CI ledger (this cycle)

- Implementation: run 35592484357 **GREEN on the FIRST run** (392c79b3).
- The release costs exactly ONE more run (the tag-driven release-apk.yml).

## The device-round checklist (on v1.1.25)

1. Open the MovieBox dual-audio episode → formatted mode shows ONE "MovieBox" card with the **Hindi** and **Original** version rows (and the raw mode unchanged).
2. Each version's row shows its declared-quality chip and — once probed — the "Available: …" resolution line (1080p/720p/480p for the Hindi manifest).
3. Download either version → the file manager shows the audio files of BOTH versions (`audio/`), the `.data.json` records the picked label.
4. Airplane-mode playback + seek + switch audio versions offline (Hindi ↔ Original).
5. Regression sweep: sub/dub providers render exactly as before; the legacy v1.1.23/v1.1.24 episodes still play + delete.

Full record: `AGENT-CONTEXT/download-research/23-CS-LINK-FORMATTING-AUDIO-VERSIONS.md`.
The handoff: `AGENT-CONTEXT/HANDOFF-POSTER-NOTIFICATIONS.md` §19.
