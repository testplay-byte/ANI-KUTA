# 22 — CS DASH OFFLINE: the sidecar manifest is composed, not original; audio versions switch offline; the audio/ folder

**D-550 · Round 61/62 · device feedback on v1.1.23**

## 1. What the v1.1.23 device round said

Two verdicts on the D-548 release (the download itself worked — files appeared in
the user's folder exactly as designed):

1. **"currently I am unable to play the video which has been downloaded."**
   The published episode would not play, and the user's file manager showed the
   episode as THREE files (video + `…audio1.mp4` + `…mp4.dashmeta`) and asked
   for a proper plan for all three — plus the ability to switch between audio
   versions offline, a dedicated `audio/` folder "just like the episodes folder
   and the subtitles folder", and data.json kept well-managed with minimal
   change.

## 2. The playback root cause — the sidecar's manifest was too honest

The D-548 sidecar stores the ORIGINAL manifest bytes as the playback index.
The aoneroom manifest's video AdaptationSet lists **three** representations
(1080p h265 @1.6 Mbps, 720p @0.8 Mbps, 480p @0.35 Mbps) — but the downloader
saves exactly ONE (the picked one). Playback parsed that original manifest as a
sideloaded `DashMediaSource`, and ExoPlayer's `AdaptiveTrackSelection` boots on
`DefaultBandwidthMeter`'s INITIAL bandwidth estimate (1 Mbps) — **below** the
1080p rep — so its very first pick was the 720p representation, whose segments
(`chunk-stream1-*.m4s`) were never downloaded. `LocalDashDataSource`'s index
miss threw the honest IOException and the episode failed before the first
frame. Any ABR down-switch would have killed playback mid-stream even with a
luckier start.

The D-548 `maxVideoHeight` pin cannot help in this direction: it CAPS the
height (`setMaxVideoSize(MAX, height)`) but still allows every SMALLER
(undownloaded) rep. It guarded against the manifest listing BIGGER reps than
were cached, never smaller ones.

## 3. The fix — both ends of the pipeline

### 3.1 READ side (fixes the v1.1.23 episodes ALREADY on disk)

`CsPlayerEngine.startOfflineDashLocal` now prunes the sidecar's manifest to
the representations the range index actually covers, before parsing it:

- `DashManifestPruner.coveredRepIds(bytes, manifestUrl, recordedUrls)`
  (new, `:core:common`, shared — `:core:download` and `:core:cs-player` must
  not depend on each other) derives each representation's FIRST planned URL
  exactly the way the planner does (rep → set → period → MPD BaseURL
  inheritance; the same `$RepresentationID$/$Bandwidth$/$Number%0Nd$/$Time$`
  substitution; the same `URI.resolve` absolutization) and checks it against
  the sidecar's recorded range URLs. SegmentTemplate → init URL (else first
  segment URL); SegmentList → init sourceURL/range; BaseURL-only → repBase.
  A rep whose derived URL is absent was never downloaded.
- `DashManifestPruner.pruneRepresentations(bytes, keepIds)` removes every
  non-covered Representation (+ AdaptationSets left empty). Kept reps' XML is
  untouched — every recorded URL still matches byte-for-byte. Parse failure →
  the input bytes unchanged (a pruner failure never takes playback down).
- Covered derivation matching NOTHING → the manifest is left untouched
  (today's behavior, not a worse one).

After the prune, ABR has nothing to switch to: the manifest offers exactly the
downloaded video rep + the downloaded audio sets. The v1.1.23 episodes play
without re-downloading.

The `maxVideoHeight` pin is NO LONGER applied for local-file (content://)
episodes (`CsWatchViewModel.startOfflinePlayback`): post-prune it is
redundant, and a stale DB quality label would cap the manifest's single
(correct) rep into unplayability. The legacy cache path keeps the pin (its
manifest is the original document and cache misses fall through to network —
the height cap is the only offline guard there).

### 3.2 WRITE side (new sidecars are born honest)

`DashOfflineManifestComposer.compose(...)` (new, `:core:download`) produces
the manifest bytes the sidecar stores:

1. **Prune** the primary manifest to what the downloader actually downloaded
   (`plan.videoRepIds` — one chosen rep per video set per Period — +
   `plan.audioRepIds`, the best rep of every audio set).
2. **Import** each sibling audio variant's covered audio AdaptationSets (see
   §4): only sets with at least one covered (downloaded) rep; the imported
   set's own non-covered reps are dropped; every relative addressable
   attribute (`SegmentTemplate@media/initialization`, `SegmentURL@media`,
   `Initialization@sourceURL`, `BaseURL` text) is ABSOLUTIZED against the
   sibling's own base chain, so media3's generated segment URLs stay
   byte-identical to the recorded placements regardless of which manifest URL
   the composed document resolves against.
3. **Label** every audio AdaptationSet with its audio-version label
   (`<Label>` → media3 `Format.label`): the primary variant's label on the
   primary's sets, the sibling's label on the imported sets (`<Label>` goes
   first; existing Label children are replaced).

Kept reps' XML is untouched — the sidecar's `files[]`/`ranges[]` contract is
unchanged (index 0 = video, 1..n = the audio sets in publish order).

## 4. Offline audio-version switching

The source exposes each audio version as a SEPARATE DASH manifest ("MovieBox
(Original Audio)" vs "MovieBox (English sub)" — one audio AdaptationSet each;
that is why v1.1.23 published exactly one `audio1.mp4`). Switching offline
therefore needs the OTHER variants' audio bytes.

- **Enqueue**: the resolve sheet's download pick now carries the FULL resolved
  link list (`CsResolveSheet.onDownload` + `handleCsDownloadPick` +
  `CsDownloadRequestBuilder.build(allLinks)`); the aniyomi path's
  `DownloadOrchestrator` collects the same shape from its server list.
  Siblings = same server name, DIFFERENT audio-version label, DASH URL
  (`.mpd`), deduped by (label, url). They ride the request/task as
  `DownloadTrack(kind = AUDIO_VARIANT, url = <manifest URL>, lang = <label>,
  headers = <the variant's own headers>)` — through the EXISTING
  `audio_tracks` JSON column, so the queue schema needs NO migration and no
  other downloader behavior changes (none of them reads audio_tracks).
- **Download**: `DashDownloader` fetches each sibling manifest (the same
  patient client + the variant's own headers), plans it for its AUDIO sets
  only, and the groups join the download as extra audio groups (temps
  `audio-<n>.fmp4`, file indices continue; per-group headers; the resume
  sidecar's SHA-1 pins primary + sibling manifests TOGETHER so a CDN
  regeneration of any of them restarts the episode honestly). A sibling that
  fails is skipped (best-effort — the picked variant must never pay for a
  sibling). The progress total-hint adds the siblings' audio-only estimates
  (`plan.audioEstimatedBytes`, newly exposed).
- **Playback**: the composed manifest carries every downloaded audio set as a
  LABELED AdaptationSet → the player's existing audio track selector (the one
  that already works online) lists the variants OFFLINE.

## 5. The audio/ folder

`publishDashEpisode` now publishes the audio sets into
`<content>/audio/<base>.audio<N>.mp4` — the user's requested layout (video in
`episodes/`, audio in `audio/`, subtitles in `subtitles/`). The sidecar's
`files[]` URIs point wherever the audio actually is, so playback is
layout-agnostic. The v1.1.23 episodes' audio (in `episodes/`) keeps working —
the sidecar is self-describing; re-publishing an episode deletes the
legacy-location copies (the stale-copy sweep) so no silent duplicates
accumulate; the delete sweep + `deleteFileByName` walk `audio/` too; the
scanner's sibling accounting (`audioSiblingBytes` → `audioSiblingFiles`)
checks BOTH locations and rebuilds the new `audioUris` from disk truth.

## 6. The three files, planned

| File | What it is | Stays? |
|---|---|---|
| `<title> - E00003.mp4` | the video representation's init + segments (a valid single-track fMP4 — video only by construction) | yes — `episodes/` |
| `<title> - E00003.audio1.mp4` (+ `audio2…`) | one audio set per file (two language tracks in ONE file would interleave into garbage); now in `audio/` | yes — `audio/` |
| `<title> - E00003.mp4.dashmeta` | the playback sidecar: the COMPOSED manifest bytes + the URL→(file, offset, length) index. The manifest IS the index media3 cannot synthesize over an unindexed fMP4 (unseekable — the Android docs' own item); it also carries the multi-audio track list. Small (KBs). | yes — beside the video in `episodes/` |

Deleting the sidecar breaks offline playback + seek; the scanner falls back to
the legacy `csdash:<manifestUrl>` cache payload only for v1.1.20–22 episodes.

data.json: `videoUri` = the real video file (D-548), NEW optional
`audioUris[]` = the published audio files (additive; `ignoreUnknownKeys = true`
keeps every older reader safe), `audioVariant` stays the picked variant's
label. Everything else untouched — per the user: "it does not need to be
changed that much."

## 7. Notes

- The E00003/E00004 sidecar/file-name mix the user saw is not a code mismatch:
  within one publish the video, audio and sidecar names derive from ONE
  `episodeFileName` call. Two episodes' files sat side by side (two downloads)
  and the pasted content was from the sibling file. The real residue risk is
  RENUMBERING (an extension renumbers an episode between downloads → the old
  set orphans); the scanner already refuses orphan files without a data.json
  entry, and the delete sweep only removes token matches.
- media3's `DashManifestParser` reads AdaptationSet-level `<Label>` into
  `Format.label`; if a future media3 regressed, the tracks would still switch
  (labels are display-only) — degradation, not breakage.
- Blast radius: DashDownloader (sibling planning + composition + publish
  wiring), the planner (two additive plan fields), the composer + pruner (new),
  publishDashEpisode (audio/ folder + stale sweep + audioUris), the scanner
  (dual-location siblings + audioUris), the delete sweeps (audio/ walk), the
  CS resolve sheet + MainActivity + the request builder + the orchestrator
  (sibling collection), CsPlayerEngine (read-side prune), CsWatchViewModel
  (pin scope). Streaming, the queue/retry policy, notifications, the legacy
  cache path, progressive/HLS pipelines: untouched.
