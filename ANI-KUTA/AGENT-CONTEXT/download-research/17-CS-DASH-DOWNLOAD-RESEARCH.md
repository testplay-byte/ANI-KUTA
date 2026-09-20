# 17 — CS Downloads: the "Only DASH streams were found" failure (research, round 56)

> Status: RESEARCH ONLY (the user's explicit scope for this session: "you are only going to
> understand, analyze, find the issues … get ready for the next session in which we will be
> working on this"). No code changed for this. This doc is the next session's launch pad.
>
> The device report: Details page loads, episodes load, episodes PLAY fine — but tapping a
> download button resolves and then dies with: **"No downloadable sources. Only DASH streams
> were found for this episode: 3 DASH stream(s) — stream them instead."**

---

## 1. Where the message comes from (exact chain)

```
DetailsScreen (feature/anime-details) — download button
  └─ CsResolveSheet(downloadMode = true)          feature/cs-watch/impl/.../CsResolveSheet.kt
       ├─ resolves links (CsWatchViewModel → plugin extractors → CsVideoLink(type = VIDEO | M3U8 | DASH))
       ├─ line ~404:  val pickableLinks = if (downloadMode) links.filter { it.type != CsLinkType.DASH } else links
       ├─ pickableLinks empty + completed  →  the "No downloadable sources" empty card
       │    ├─ "Only DASH streams were found for this episode"   (downloadMode && dashOnlyCount > 0)
       │    └─ "$dashOnlyCount DASH stream(s) — stream them instead"
       └─ pick(non-DASH link) → onDownload(key, link, subtitles)
            └─ MainActivity.handleCsDownloadPick (line ~2041)
                 └─ CsDownloadRequestBuilder.build → DownloadRequest → downloadManager.enqueue
                      └─ HttpDownloader (progressive) / HlsDownloader (m3u8) → SAF publish → downloaded_episode DB
```

**The download engine itself NEVER sees a DASH link.** `core/download` has zero DASH
handling (VideoTypeDetector's "DASH_STREAM → reject" note in doc 05 is historical — the
current file has no DASH branch). The block is the UI-layer filter above. It exists because
the engine genuinely cannot download a DASH manifest: its two paths are
single-file HTTP (HttpDownloader) and HLS (HlsDownloader, segment → .ts).

## 2. Why playback works but download doesn't

CS **playback** runs on **ExoPlayer/Media3** (`core/cs-player/CsPlayerEngine.kt`,
`MediaItem(uri).setMimeType(mime)` → `DashMediaSource` via `media3-exoplayer-dash`, which is
ALREADY a dependency — gradle/libs.versions.toml lines 154-159). Media3 plays `.mpd`
natively: it fetches the manifest, picks video/audio Representations, streams segments with
ABR, handles multi-audio (SUB/DUB as separate audio tracks).

CS **download** runs on the engine's own HTTP/HLS fetchers, which understand neither `.mpd`
nor segment addressing → DASH links were filtered instead of downloading garbage.

Modern CS providers increasingly serve DASH-ONLY (Sora/Rive-class CDNs; the device round hit
one with exactly 3 DASH streams and nothing else). So the filter that used to be a harmless
convenience now blocks downloads on a growing share of content.

## 3. What a DASH download actually requires

A DASH `.mpd` references separate **video** and **audio** Representations ( AdaptationSets)
made of init + media segments. "Downloading" it = fetch manifest → pick one video REP (the
quality) + one audio REP (the language) → download their segment sequences → **remux the
two tracks into one playable file/stream** (a DASH download that only stores segments is not
a file the rest of the app can use). Plus: the provider's headers/referer/interceptor must
ride every segment request, and ClearKey vs Widevine must be distinguished (Widevine = dead
end without a license server; several anime CDNs are cenc:passthrough/plain — but some
encrypt).

## 4. Solution options

### Option A — Media3 DownloadManager (`DashDownloader`) — the maintained path
- Deps: **already present** (media3-exoplayer-dash, datasource-okhttp). Add nothing.
- `DownloadManager` + `DownloadService` + `DownloadHelper` (track selection: pick video
  quality + audio language the way the player does) → downloads manifest + segments into a
  `SimpleCache` offline store; built-in resume, progress, pause.
- Headers/interceptor: reuse `CsHttpDataSourceFactory` (OkHttp + the provider's
  `getVideoInterceptor`) as the download `DataSource.Factory`.
- **THE CATCH — playback:** the result is NOT a file. Offline playback must go through
  ExoPlayer + `CacheDataSource` against the download store. Today, downloaded episodes play
  via a **file path into the MPV watch screen** (DetailsScreen.kt ~654
  `getDownloadedEpisodeUri` → `onNavigateToWatch(localUri)`). DASH-offline episodes need a
  new playback route (ExoPlayer-backed screen or an MPV screen that can't exist for this
  format) + a different `downloaded_episode` row shape (no single URI; a cache key).
  Storage/SAF publishing (a real file in the user's folder) does not apply — the store lives
  in app-private cache; "move to SD/user folder" becomes impossible for these.

### Option B — libmpv `--record-file` remux spike — the cheap, file-true path (TRY FIRST)
- The app already embeds libmpv (`core/player-mpv-lib`, `core/player/AnikutaMPVView`).
  mpv opens DASH through ffmpeg's dash demuxer and can **stream-copy record** the played
  stream to a real `.mkv` (`--record-file=out.mkv` + `--record-file-format=mkv`, headers via
  `http-header-fields`). If the bundled mpv build ships the dash demuxer AND an mkv muxer
  (libmpv builds often strip muxers — must verify on device), this yields an ordinary file:
  **the ENTIRE existing pipeline (SAF publish, DB, MPV offline playback, downloads page,
  subtitles sidecars) works unchanged.**
- Headless: run a hidden/offscreen mpv core (no view) with `--vo=null --ao=null`, seek to
  end / play through at max speed? — NO: recording must play in real time, which is
  unacceptable for downloads... **unless** `--stream-dump` (raw demuxer dump, no playback
  clock) works for DASH: `--stream-dump` dumps the selected stream byte-exactly but for
  multi-track DASH it may only take one track. The honest spike answers three questions:
  1. does the bundled mpv demux the provider's mpd at all (headers + multi-audio)?
  2. is there a muxer (`--record-file`/`--o`) in the build?
  3. can the dump run faster than real time (`--cache-pause`/`--speed` probes, or
     `--stream-dump` which is clock-free)?
- If any answer is no → Option A.

### Option C — hand-rolled segment fetch + CacheWriter pre-cache
Variant of A without DownloadManager: parse the mpd (media3-dash parsers are public),
select reps, `CacheWriter` + `CacheDataSource` pin every segment into SimpleCache. Same
playback constraint as A, more code, more control (per-rep progress). Only worth it if A's
DownloadService lifecycle fights the app's existing queue/service model.

## 5. Recommendation for the next session

**Spike B on a real device first** (one debug session, a real DASH-only provider, a real
mpd URL + headers): `adb shell` + a tiny probe activity that loads the mpd in libmpv and
tries `--stream-dump` then `--record-file`. Three outcomes:
- B works end-to-end → implement the CS DASH download path as: resolve → mpd → mpv dump →
  validate (magic bytes, duration ≈ episode) → publish through the EXISTING
  HttpDownloader post-pipeline. Small diff, file-true, MPV offline playback unchanged.
- B half-works (demuxes but no muxer/no clock-free dump) → Option A: Media3
  DownloadManager for DASH (+ optionally route the existing M3U8 CS downloads through it
  too for uniformity), ExoPlayer offline playback route for CS downloads, `downloaded_episode`
  gains a cache-key variant, honest Widevine detection (`ContentProtection` in the mpd →
  "This source's streams are protected — cannot be downloaded" instead of a silent fail).
- Keep the existing UI filter as the last resort ONLY for genuinely undownloadable
  manifests (Widevine), not for all DASH.

**Open questions to answer during the spike / implementation:**
1. Which muxers/demuxers does `core/player-mpv-lib`'s build carry? (probe `mpv --version`
   style info or feature-detect via `mpv_set_option_string` failures)
2. Are the 3 DASH streams in the user's provider ClearKey or Widevine? (read the mpd's
   ContentProtection tags)
3. Multi-audio DASH: how does CsPlayerEngine currently surface SUB/DUB audio tracks, and
   can the download selection reuse the same track-selection logic?
4. Does the existing download QUEUE (foreground service, concurrency=1, notifications)
   wrap a Media3 DownloadService cleanly, or should the DASH download be one task type the
   queue schedules while Media3 does the fetching?
5. Storage: DASH at chosen quality can be larger than the ABR estimate the queue shows —
   the UI should show the mpd's declared bandwidth, not guess.

## 6. Files the next session will touch (predicted)

| Concern | File |
| --- | --- |
| The filter to lift/replace | `feature/cs-watch/impl/.../CsResolveSheet.kt` (~line 404) + the empty-card copy |
| New DASH download task type | `core/download/.../DownloadModels.kt` (task kind), `HttpDownloader.kt` router |
| Option B executor (if spike wins) | new `core/download/.../MpvDashDumper.kt` (headless libmpv) |
| Option A executor (if A wins) | new `core/download/.../Media3DashDownloader.kt` + DI in `DownloadModule.kt` |
| Offline playback (A only) | CS watch route: cache-key → ExoPlayer path; `downloaded_episode` schema |
| Request builder | `app/.../download/CsDownloadRequestBuilder.kt` (carry type + rep selection) |
| Docs | 03-state-machine.md + 05-downloaders.md updates after implementation |

*End of research doc — nothing here changes behavior until the next session implements it.*
