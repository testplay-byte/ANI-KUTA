# 53 — RELEASE 1.1.24 BRANCH POINT (round 61/62)

`release/1.1.24` cut from the round-61/62 feature head `784a9525`
(`feature/round-57-cloudstream-downloads`, CI **GREEN** on the FIRST
implementation run — run 35554707595). Version bump rides THIS branch as its
first commit: **1.1.24 / 10124**.

## What v1.1.24 carries (the v1.1.23 device round — the user's verdict: "the
video gets saved properly to the appropriate folder as needed"; the two
demands: the episode must PLAY, the audio versions must switch OFFLINE)

| Decision | What shipped |
| --- | --- |
| **D-550 (playback)** | The sidecar manifest is COMPOSED, not original. Root cause: the original manifest listed three video reps while one is on disk → ExoPlayer's ABR (1 Mbps initial estimate) picked the undownloaded 720p rep → first local-index miss → no playback; the height pin cannot prevent SMALLER picks. READ side: `startOfflineDashLocal` prunes the sideloaded manifest to the covered reps (`DashManifestPruner`, `:core:common`) — the v1.1.23 episodes already on the device play after the update, no re-download; the pin is dropped for local-file payloads (kept for legacy cache). WRITE side: `DashOfflineManifestComposer` composes the sidecar manifest (pruned + sibling imports + labels). |
| **D-550 (audio)** | Offline audio-version switching: the enqueue plumbing collects the sibling DASH audio-variant manifests (same server, different label) as `AUDIO_VARIANT` tracks through the existing `audio_tracks` JSON column (no schema migration); `DashDownloader` downloads their audio sets as extra groups (best-effort, per-variant headers, all-manifest resume pin) and the composed manifest carries them as LABELED sets → the existing audio track selector works OFFLINE. |
| **D-550 (layout)** | The `audio/` folder — audio sets publish to `<content>/audio/<base>.audio<N>.mp4` (like `episodes/` + `subtitles/`); re-publish sweeps the v1.1.23 legacy-location copies; the delete sweeps + the scanner's sibling accounting know BOTH locations; `.data.json` gains the additive `audioUris` (rebuilt from disk truth; every older reader safe). |

## The three published files, planned (the user's ask)

- `<title> - E00003.mp4` — the video rep's init+segments (video-only fMP4) → `episodes/`.
- `<title> - E00003.audio1.mp4` (+ `audio2…`) — one audio set per file → `audio/`.
- `<title> - E00003.mp4.dashmeta` — the playback sidecar (the COMPOSED manifest + the URL→file-range index; the index media3 cannot synthesize over an unindexed fMP4) → beside the video in `episodes/`.

## The CI ledger (this cycle)

- Implementation: run 35554707595 **GREEN on the FIRST run** (784a9525).
- The release costs exactly ONE more run (the tag-driven release-apk.yml).

## The device-round checklist (on v1.1.24)

1. WITHOUT re-downloading: play one of the v1.1.23 episodes (EP 3 / EP 4) — it must play + seek offline (the read-side prune fixes the existing sidecars).
2. Download a fresh MovieBox episode → video in `episodes/`, audio file(s) in `audio/`, the `.dashmeta` beside the video.
3. Airplane-mode: play it, seek across the middle, switch audio versions in the track sheet.
4. Delete → the whole file set vanishes (video + audio/ files + sidecar).
5. Regression sweep: streaming, the downloads page, the legacy cache episodes.

Full record: `AGENT-CONTEXT/download-research/22-CS-DASH-OFFLINE-PLAYBACK-AUDIO.md`.
The handoff: `AGENT-CONTEXT/HANDOFF-POSTER-NOTIFICATIONS.md` §18.
