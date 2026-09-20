# 18 — CS DASH downloads: the implementation record (round 57, D-539/D-540)

> Status: SHIPPED (the round-57 implementation of doc 17's plan). This doc is the
> durable design + operations record: what landed, where, and the rules future
> work must keep. The research/analysis lives in doc 17.

## 1. The artifact model

| Question | Answer |
| --- | --- |
| What IS a DASH download? | The manifest + the chosen video Representation's + ALL audio reps' init/media segments, cached in the app-private SimpleCache. NOT a file (fMP4 needs a muxer the app doesn't ship). |
| Where? | `filesDir/cs_dash_cache` — `SimpleCache(folder, NoOpCacheEvictor, StandaloneDatabaseProvider)`. ONE instance per process (Koin `single<Cache>` via `DashCacheStore.provide`). NoOp eviction: an evicted "download" = silent data loss; growth is user-managed via the Downloads page. |
| Keys? | `csdash|<manifestUrl>|<resourceUrl>` (core/common `DashCacheKeys` — the ONE contract shared by writer/reader/purger; the two modules never depend on each other). The manifest itself caches under `csdash|<manifest>|<manifest>` so offline playback's first read is a cache hit. |
| The SAF folder? | STILL created: `.data.json` (episode entries carry `dashManifestUrl`), `.cover.jpg`, `.nomedia`, `subtitles/` — the durable metadata story is identical to file downloads; only the video file is absent. |
| The DB row? | `downloaded_episode` UNCHANGED schema: `video_uri`/`file_path` carry the `csdash:<manifestUrl>` marker, `file_size` = real cached bytes, folder/file-name columns hold inert derivations. NO schema change (no migration chain — a constraint change crashes updated installs). |
| Quality selection? | The picked link's quality label → preferred height (closest rep wins, tie → bandwidth); ALL audio AdaptationSets cached (one best rep each — SUB/DUB sets both survive). |
| DRM? | ANY `ContentProtection` element → refused honestly: "This stream is DRM-protected — it cannot be downloaded. Stream it instead." Conservative = no broken downloads. |

## 2. The pipeline (one queue task, the SAME machinery)

```
CsResolveSheet (download mode) — DASH links are PICKABLE now (the filter is gone)
  → handleCsDownloadPick → CsDownloadRequestBuilder (unchanged; providerName rides content info)
  → enqueueDownload → DownloadQueue → HttpDownloader.download
      ├─ VideoTypeDetector == DASH → DashDownloader.download          [primary route]
      └─ small file sniffed as an MPD → DashDownloader.download       [extension-less fallback]
  DashDownloader:
    1. fetch manifest (flattened MPV-format headers via DownloadHeaderParser)
    2. DashManifestPlanner.parse → DashSegmentPlan (parts, estimate, DRM/live/unsupported verdicts)
    3. per part: CacheWriter over CacheDataSource.createDataSourceForDownloading()
       (DataSpec carries the link headers; CacheKeyFactory = DashCacheKeys.build;
       writer.cancel() on coroutine cancellation = prompt pause; cached spans skip = free resume)
    4. subtitles → temp (D-FIX-SUB header rules) → storage.publishDashEpisode
       (.data.json + cover + .nomedia + subtitles/, NO video file) → .data.json upsert
    5. task COMPLETED, videoUri = "csdash:<manifestUrl>"
```

Upstream client: the CS runtime's base client (`named("cloudstreamPlayback")`) when on the
Koin graph — the resolve just succeeded so its cookies/interceptors are live — else the
DOWNLOAD client (tests).

## 3. Offline playback

```
Details page (isDownloadedEpisodeDash) / Downloads page (buildWatchKeyForDownloadedEpisode)
  → CsWatchKey(offlineManifestUrl = manifestUrl)   [pushed DIRECTLY to the backstack —
                                                    NEVER via csResolveRequest (opens the sheet)]
  → CsWatchViewModel.initialize → startOfflinePlayback (phase straight to PLAYING, no resolve)
  → engine.startOfflineDash(manifestUrl, resumeMs, maxVideoHeight)
      = DashMediaSource over CacheDataSource(cache, OkHttp upstream, shared key factory)
      + a maxVideoHeight TRACK PIN (the manifest may list more video reps than were cached;
        startInternal CLEARS the pin so online playback never inherits it)
  → switching: selectEpisode prefers the offline cache for downloaded targets (generation-
    guarded); non-downloaded targets fall back to ONLINE resolution via the provider name.
  → subtitles: sidecars resolve via the D-407 chain (real SAF files) and read through
    CsSubtitleFetcher's content:// branch; embedded DASH text tracks ride the engine.
```

## 4. The lifecycle rules (what future work MUST keep)

1. **dashManifestUrl survives every .data.json rewrite** — DataJsonRepair.rebuildEpisodesAfterDelete
   propagates it; the scanner's DASH pass re-registers cache episodes from it (no file walk is
   possible); losing it = anti-shrink churn on every scan + an unrecoverable download after reinstall.
2. **Delete = cache purge + SAF cleanup**: DefaultDownloadManager's Phase 3a branches on the
   csdash marker — DashCacheStore.purgeEpisode sweeps every key under the episode's manifest
   prefix; the video-URI DocumentsContract delete is SKIPPED; subtitles/.data.json/series-folder
   logic runs as usual.
3. **The planner's addressing lookups are DIRECT-CHILDREN** — a rep-level SegmentTemplate/BaseURL
   must never leak into a parent level's resolution (sibling reps would inherit the wrong URLs).
4. **Locale.ROOT for `$Number%0Nd$`** — the default locale renders Eastern-Arabic digits on
   ar/fa/bn devices; CDN URLs would 404.
5. **The offline play request clears on every online requestPlay** (and vice versa) — the screen's
   trigger gives playOfflineManifestUrl priority; a stale field plays the WRONG episode.
6. **Offline keys go to the backstack, never csResolveRequest** — the sheet would re-resolve a
   downloaded episode online (dead end or online playback; the offline branch unreachable).
7. **DRM/dynamic/>20k-segment manifests fail HONESTLY** in the queue (the task's lastError shows
   the reason) — never download garbage, never fail silently.
8. **The episode keys stay source-specific (SEpisode.url) across source switches** — the D-540
   translation moves identity metadata, not episode handles; cross-source episode re-matching is
   the smart-matcher's future domain.

## 5. The D-540 data layer (the companion half of this round)

- Ecosystem truth: `CLOUDSTREAM_SOURCE_ID_FLAG` (bit 62, core/content — the documented twin of
  data:cloudstream's `CsSourceIds.CS_SOURCE_ID_FLAG`) + `Long?.isCloudstreamBridgedId()`.
- New CS content records system/extension_type **"cloudstream"** (was hardcoded "aniyomi");
  `healCloudstreamEcosystem()` (AnikutaApp startup, BEFORE the download scan, idempotent) relabels
  existing rows (main_entry.system_id → the seeded cloudstream row; content_details.extension_type).
- `ContentIdentitySync` (core/content, NON-suspend — the manager owns its threading) fired by
  `linkExtensionToExisting` after the transaction → `DefaultDownloadManager.onContentIdentityChanged`:
  (1) the two previously-DEAD .sq queries sync `downloaded_episode`/`download_queue`.content_id;
  (2) the durable `.data.json` identity block rewrites through the VERIFIED 3-attempt ladder
  (rewriteDataJsonEpisodes takes contentId from the caller now).
- `providerName` rides DownloadContentInfo + ContentDataJson — the durable store self-describes
  its ecosystem (the future-provider base).
- Downloads SURVIVE source switches (files are mainId-keyed); only metadata translates.

## 6. Doc drift fixed (the round's sweep)

- 04-storage-paths.md §3.1/§4.5/§5: the real layout is `.data.json`/`.cover.jpg`/`.nomedia`
  (dot-prefixed) + `episodes/` + `subtitles/` subfolders; subtitle names are
  `subtitle_E{5}_{lang}_{idx}.{ext}`; the example json now matches ContentDataJson (FK fields,
  videoUri/subtitleUris). **Note: DASH adds a new folder shape — the same tree with NO episode
  file and `dashManifestUrl` entries (this doc + 18 are the record).**
- 05-downloaders.md: VideoTypeDetector has {HTTP, HLS, DASH} (the "DASH_STREAM reject" note is
  historical); there is no common Downloader interface — HttpDownloader is the facade;
  DashDownloader is the third pipeline (this doc).
- 11-db-schema.md: episode_key = SEpisode.url (NOT "$mainId|$epNumPadded"); the index set is
  {idx_download_queue_main_episode, idx_download_queue_state}; the contentId-sync queries now
  HAVE a caller (the D-540 hook).
- 17: status header → implemented (this doc is the record).
