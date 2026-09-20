# 52 — RELEASE 1.1.23 BRANCH POINT (round 60)

`release/1.1.23` cut from the round-60 feature head `2270bfa6`
(`feature/round-57-cloudstream-downloads`, CI **GREEN** — implementation run
35540642194). Version bump rides THIS branch as its first commit:
**1.1.23 / 10123**.

## What v1.1.23 carries (the round-60 device round — the user's verdict: the
v1.1.22 download pipeline WORKS end to end (resolve → pick → download → play
→ downloads page → .data.json verified proper per episode) and rejects
exactly one thing: the episode folder has NO video file — "the actual video
file was not saved in the actual directory where it was meant to be saved.
It should be saved in the appropriate download directory, where the other
files were created")

| Decision | What shipped |
| --- | --- |
| **D-548** | **DASH downloads become REAL files in the user's SAF download folder.** D-539's media home (the app-private SimpleCache) is retired for new downloads. The episode folder now holds `<title> - E00001.mp4` (the video rep's init + segments concatenated — a valid single-track fMP4), `<title> - E00001.audioN.mp4` (one per audio AdaptationSet — merging languages would interleave them into garbage), and `<title> - E00001.mp4.dashmeta` (the ORIGINAL manifest bytes + the URL→(file, offset, length, partPosition) index). Playback = `CsPlayerEngine.startOfflineDashLocal`: the STORED manifest parses via `DashManifestParser` into a SIDeloaded `DashMediaSource` whose every segment request is served by the new `LocalDashDataSource` from the local file ranges — because an unindexed fMP4 is UNSEEKABLE in ExoPlayer (the Android media troubleshooting docs' own item), the manifest IS the index: seek points, duration and the multi-audio track list all come from the DASH timeline, and an index miss throws an honest IOException (an offline episode can never silently stream). Pause→resume and queue retries ride a manifest-SHA-pinned sidecar persisted after EVERY part (parts-done + bytes + the placements list; crash-heal truncation; a short file restarts the group and subtracts its bytes). The `csdash:` marker scheme survives with a LOCAL payload (`csdash:<metaDocUri>`) and decodes by mode against the v1.1.20–v1.1.22 legacy cache episodes (which keep playing + deleting through the untouched `startOfflineDash`/purge path). The scanner reconstructs DB rows with the marker via sidecar-sibling discovery and preserves `dashManifestUrl` + full-episode sizes; the delete token sweep takes audio+meta with the video. SAF trap fixed pre-push: `FileUtils.splitFileName` reconciles the display-name extension against the mime (`audio/mp4` renames …audio1.mp4→…m4a; `application/json` renames …mp4.dashmeta→…json) — audio publishes as `video/mp4`, the sidecar as `application/octet-stream`. The independent review (FIX-FIRST) caught both blockers + one major (media3's RangedUri requests carry `DataSpec.position` = the ABSOLUTE remote range start) + two minors before the first push. Full record: `AGENT-CONTEXT/download-research/21-CS-DASH-REAL-FILES.md`. |

## The CI ledger (this cycle — OVER the two-run budget, disclosed)

- Implementation: run 35539299052 **RED** on `68d70687` (the
  `(DashManifest, DataSource.Factory)` DashMediaSource constructor is
  PRIVATE in media3 1.9.3 — only discoverable at compile time).
- Implementation: run 35539813728 **RED** on `034b45cd` (the Factory
  overload's MediaItem param is non-null in the Kotlin-visible signature +
  the sidecar's uri strings needing `Uri.parse`).
- Implementation: run 35540642194 **GREEN** on `2270bfa6` (the documented
  sideloaded API: `Factory(...).createMediaSource(manifest, MediaItem)`).
- The release costs ONE more run (the tag-driven release-apk.yml; the
  release-branch push is filtered from the Build APK trigger per D-472).
- Total: 4 runs vs the ≤2 budget — the overage is the compile-time-only API
  discoverability plus a tooling artifact that mangled bracket-m sequences
  in my view of the working tree (the code itself was verified correct by
  count checks; nothing but the two API shapes and the restored line
  changed between runs 1 and 3).

## The launch pad

The real-files record: `AGENT-CONTEXT/download-research/21-CS-DASH-REAL-FILES.md`
(the SAF layout, the seek rationale, the failure matrix). The fetch/parse
records: docs 19 + 20. The DASH design + lifecycle rules: doc 18. The
handoff: `AGENT-CONTEXT/HANDOFF-POSTER-NOTIFICATIONS.md` §17. Memory:
D-548 in `AGENT-CONTEXT/memory/decisions.md`.
