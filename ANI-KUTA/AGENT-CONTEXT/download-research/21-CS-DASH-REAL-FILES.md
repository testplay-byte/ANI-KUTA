# 21 — D-548: DASH downloads become REAL files in the user's SAF folder (the media home moves out of the app)

> **Task ID:** Round 60 (Task 3)
> **Trigger:** the v1.1.22 device round — the download pipeline finally ran clean (the D-546 factory fix + D-543 byte transport held), but the user's file manager showed the episode folder WITHOUT the video file: "the actual video file was not saved in the actual directory where it was meant to be saved. It should be saved in the appropriate download directory, where the other files were created."
> **Cross-references:** `17-CS-DASH-DOWNLOAD-RESEARCH.md` (why the SimpleCache was chosen — D-539's Option C) · `04-storage-paths.md` (the SAF contract: the download folder holds the downloads) · `19`/`20` (the fetch/parse fixes that made this round possible) · `18` §7 (the pipeline overview — §2 of it is now superseded).

---

## 1. The root cause of the user's complaint: a DESIGN decision, not a bug

D-539 chose the app-private media3 SimpleCache (`filesDir/cs_dash_cache`) as the DASH media home because "fMP4 DASH cannot be [one file] without a muxer the app doesn't ship". The SAF folder got `.data.json` + `.cover.jpg` + `subtitles/` only; `data.json` recorded `videoUri: null` and the DB row carried the `csdash:<manifestUrl>` marker; playback rode `startOfflineDash` over a CacheDataSource.

Everything worked — except the user-visible contract. The user's download folder is WHERE THEIR FILES LIVE; a 40 MB "download" they cannot find in their file manager is not a download. D-548 re-homes the media into the SAF folder while KEEPING every playback property the cache path had.

## 2. The design problem hiding behind the move: SEEKING

Concatenating a representation's init + media segments produces a valid single-track fMP4 (the yt-dlp-concat artifact) — but with NO index (no `sidx`). **ExoPlayer cannot seek an unindexed fragmented MP4** (the Android media troubleshooting docs' own item: seeking is unsupported when the only seek method would be accurate-but-unindexed; media3 emits `SeekMap.UNSEEABLE`). A dead seekbar in offline playback would trade one rejected behavior for another.

The manifest IS the index. So D-548 keeps the DASH stack in charge of playback:

- the `.mp4.dashmeta` sidecar stores the ORIGINAL manifest bytes (base64) + the `url → (file, offset, length, partPosition)` index of every downloaded part;
- `CsPlayerEngine.startOfflineDashLocal` parses those bytes with `DashManifestParser` and plays a **SIDeloaded DashMediaSource** whose every segment request is served by `LocalDashDataSource` **from the local file ranges**;
- seek points + duration + multi-audio tracks all come from the DASH timeline (media3's segment-URL substitution is deterministic — the same template/inputs the planner used — so the requested URLs are exactly the index keys);
- a segment the index cannot satisfy throws an honest `IOException` — an offline episode can never silently stream.

The SimpleCache stays ONLY for the v1.1.20–v1.1.22 legacy episodes (`startOfflineDash` + the delete-path purge); new downloads never touch it.

## 3. What ships where (the SAF episode folder)

```
<root>/video/<title>/
├── .data.json                    (videoUri = the REAL video file uri; dashManifestUrl = the manifest URL)
├── .cover.jpg  .nomedia
├── episodes/
│   ├── <title> - E00001.mp4            ← the video rep's init + segments concatenated (fMP4)
│   ├── <title> - E00001.audio1.mp4     ← ONE file per audio AdaptationSet (merging languages = garbage)
│   └── <title> - E00001.mp4.dashmeta   ← the playback sidecar (manifest bytes + range index)
└── subtitles/ …
```

Naming invariants (enforced by matching regexes in the sweep AND the scanner):
- the video regex ` - E(\d{5}(?:\.\d+)?)\.[^.]+$` must NOT match the audio/meta tails (`[^.]+$` cannot span a second dot) — so the scanner's file walk never resurrects phantom episode rows;
- the sweep's `AUDIO_NAME_REGEX` / `META_NAME_REGEX` must match them so episode deletion takes the whole set;
- the DB row's `video_uri` = `csdash:<metaDocUri>` — the SAME marker scheme as D-539, now with a LOCAL payload; `DashCacheKeys.payloadFromUri` decodes either mode (`offlineMetaUriFromUri` for `content://`, `manifestUrlFromUri` for legacy http(s)).

## 4. SAF trap found by the review: the provider RENAMES files

AOSP `FileSystemProvider.createDocument` → `FileUtils.splitFileName` reconciles the display-name extension against the mime type: `createFile("audio/mp4", "…audio1.mp4")` renames the file to `…audio1.m4a` (the mime's canonical ext), and `createFile("application/json", "…mp4.dashmeta")` renames it to `…mp4.json`. Both would silently break the entire name contract. Fix: the audio file publishes as `video/mp4` (exact match → name kept) and the sidecar as `application/octet-stream` (octet-stream bypasses reconciliation — the same trick the subtitle publish has always used).

## 5. The download pipeline (DashDownloader)

1. fetch the manifest AS BYTES (D-543/D-546 heritage unchanged) → plan (`videoParts` + `audioGroups` now exposed alongside the flat `parts`);
2. download each part with plain OkHttp — `Range: bytes=…` for positioned/length parts, honest 200-vs-206 slicing, short-read = raw `IOException` (retryable) — and APPEND into per-representation temp files, recording each part's final `(file, offset, length, partPosition)`;
3. resume sidecar `dash-resume.json` (temp dir): `{manifestSha, videoPartsDone, videoBytes, audioSets[], placements[]}` persisted after EVERY part — pause/resume and queue retries skip completed parts; the manifest SHA-1 invalidates a sidecar from a different manifest (a changed plan resumed into the same file would be corrupt); a crash between append and sidecar write is healed by truncating back to the recorded length; a file SHORTER than recorded restarts that group (and subtracts its recorded bytes from the running total);
4. publish (§3) → `.data.json` upsert (real video uri + `dashManifestUrl`) → task `videoUri = csdash:<metaDocUri>`.

## 6. Playback routing (both modes)

`isDownloadedEpisodeDash` → `isOfflineDashUri` (either marker payload) → the CS watch screen; the screen's play trigger branches on the payload's scheme: `content://…dashmeta` → `startOfflineDashLocal` (SAF files), else → `startOfflineDash` (legacy cache). Episode switching in the watch screen carries the raw payload the same way (`CsWatchKey.offlineMediaUri` — renamed from `offlineManifestUrl`). The scanner reconstructs DB rows with `csdash:<metaUri>` (sibling discovery in the already-listed episodes dir) and falls back to `csdash:<manifestUrl>` when the user deleted the sidecar; the delete path deletes the video file by URI for local-file episodes (siblings die in the token sweep) and purges the cache for legacy ones.

## 7. Failure matrix

| Case | Before D-548 | After D-548 |
|---|---|---|
| Download completes | media invisible in the file manager (app-private cache) | `E00001.mp4` + `audioN.mp4` + `dashmeta` in the SAF folder |
| Playback (offline) | CacheDataSource over the cache | sideloaded manifest + LocalDashDataSource over the files — same seek/duration/multi-audio behavior |
| Pause → resume | spans skipped by the cache | parts skipped by the sidecar (manifest-hash-pinned) |
| Queue retry | spans skipped | parts skipped (placements ride the sidecar) |
| Delete | folder delete + cache purge | video URI delete + token sweep (audio + meta) ; legacy rows still purge the cache |
| Reinstall/scan | cache rows reconstructed from data.json | file-walk rows reconstructed with the `csdash:<metaUri>` marker; legacy rows unchanged |
| Segment not in index | (cache miss → network) | honest IOException (never a silent stream) |
