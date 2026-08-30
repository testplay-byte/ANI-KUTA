# CloudStream V2 — The Playback Architecture (as built)

> **Task 52 (round 12).** The as-built companion to `02-PLAYBACK-PLAN.md` — what
> shipped, where it lives, how to debug it, and what's deliberately not here.
> Written same-session with the code it describes (CORE_RULES §26).

---

## 1. The pipeline (one screen of truth)

```
Episode tap (DetailsScreen, CS-bridged source — DetailsScreen.onEpisodeClick)
  → onNavigateToCsWatch(providerName, animeTitle, episodeData, epNum, epTitle, epList, mainId, sourceId)
  → MainActivity: backstack.add(CsWatchKey(...))
  → CsWatchScreen (feature:cs-watch:impl)
      ├─ CsWatchViewModel.initialize(key)
      │    └─ CloudstreamLinkResolver.resolve(providerName, episodeData)     [data:cloudstream]
      │         └─ provider.loadLinks(data, isCasting=false, subCb, linkCb) [core:cloudstream-api ABI]
      │              ├─ dedup by URL · TORRENT/MAGNET hidden+counted · DRM flagged
      │              ├─ ExtractorLink → CsVideoLink · SubtitleFile → CsSubtitle   (the app-side boundary)
      │              └─ progressive snapshots: LinksSnapshot / SubtitlesSnapshot / Completed / Failed
      │    └─ first link → auto-pick (quality desc) → playRequestId++ (resume-aware)
      ├─ CsPlayerEngine.start(link, subtitles, resumeMs)                   [core:cs-player]
      │    ├─ MediaItem(url, mime: VIDEO→video/mp4 · M3U8→application/x-mpegURL · DASH→application/dash+xml)
      │    ├─ DefaultMediaSourceFactory(OkHttpDataSource(app.baseClient + interceptor)
      │    │        .setUserAgent(ua).setDefaultRequestProperties(referer + headers))
      │    ├─ sidecar subs → SubtitleConfiguration + SingleSampleMediaSource (per-sub headers)
      │    ├─ external audio → merged MediaSources (MergingMediaSource)
      │    └─ ExoPlayer.setMediaSource(merged, position).prepare()   [live default position nuance]
      ├─ engine error → ViewModel.onEngineError → mark bad → auto-next link (position preserved)
      ├─ episode ended → next episode (re-resolve) · progress saved every 10 s + on exit
      └─ sheets: Streams (+ per-stream quality rows via track selection) · Subtitles (sidecar + embedded) · Episodes
```

## 2. Module contracts

| Module | Owns | Never touches |
|---|---|---|
| `core:cs-player` | `CsVideoLink`/`CsSubtitle`/`CsAudioTrack` models, `CsMediaTypes` (mime maps), `CsHttpDataSourceFactory` (per-link OkHttp), `CsPlayerEngine` (ExoPlayer host, track selection, error classification, `CsEngineState`/`CsEngineEvent`) | `com.lagradost.*` (zero plugin classes), any aniyomi module |
| `data/cloudstream` `playback/` | `CloudstreamLinkResolver` — the loadLinks orchestration (progressive snapshots, dedup, filtering, 20-min cache, 30-s first-link watchdog); DI: the `cloudstreamPlayback` OkHttp client (= the CS runtime's `app.baseClient`) | UI, ExoPlayer |
| `feature:cs-watch:api` | `CsWatchKey` (serializable Nav3 key; `episodeData` = the CS data handle = `SEpisode.url` for bridged content) | — |
| `feature:cs-watch:impl` | `CsWatchScreen` (surface + lifecycle), `CsWatchViewModel` (resolution state, link fallback, progress), `CsControlsOverlay`, `CsOverlays`, `CsPlayerSheets` | the aniyomi `:feature:watch` (zero shared code) |

**Seams (the only aniyomi-side edits, all additive):**
- `DetailsViewModel.isLinkedSourceCloudStream()` + the `resolveEpisode` CS
  short-circuit (defense in depth — only the download path can reach it now).
- `DetailsScreen.onEpisodeClick`: the CS branch routes to `onNavigateToCsWatch`
  BEFORE the classic resolver; the continue-watching autoplay effect now fires
  the same hoisted handler.
- `MainActivity`: the `onNavigateToCsWatch` callbacks (both DetailsScreen call
  sites) + the `is CsWatchKey -> CsWatchScreen(...)` nav branch.
- `AnikutaApp`: `csWatchModule` registered.

**Untouched (verified):** `VideoResolver`, `ResolvedVideosRegistry`,
`feature:watch` (WatchScreen/WatchKey), `core:player`, `core:player-mpv-lib`,
`core:playback-cache` — the MPV stack is byte-identical to v0.3.0.

## 3. The one-filter logcat recipe (the debugging contract)

Paste into Android Studio's Logcat filter bar:

```
tag:Anikuta:CS:Resolver | tag:Anikuta:CS:Player | tag:Anikuta:CS:Subs | tag:Anikuta:CS:Watch
```

What each tag answers:
- **Anikuta:CS:Resolver** — "why didn't it resolve?" Provider lookup, every
  link/sub as it arrives (with type/referer/headers/quality), dedup skips,
  hidden torrent counts, timeouts, cache hits, provider crashes.
- **Anikuta:CS:Player** — "why didn't it play?" Engine start/switch (mime,
  subs, audio, resume), buffer states, track picks, and the upstream-format
  error line: `playerError: url=…, type=…, code=…, http=…, referer=…,
  headers=…, position=…, duration=…, linkName=…`.
- **Anikuta:CS:Subs** — "what about subtitles?" Sidecar attachment, per-sub
  mime, drops, selection on/off.
- **Anikuta:CS:Watch** — the screen/ViewModel layer: entry, resume position,
  play requests, link fallback decisions, episode switches, progress saves.

The in-app console (Settings → Console logs) has a matching **"CS Playback"**
chip (prefix `Anikuta:CS:`) for device-side triage without Android Studio.

## 4. Deliberate limits (documented, not accidental)

- **Torrent/magnet links**: hidden + counted (upstream plays them via a torrent
  server we don't port). The links sheet's footer shows the hidden count.
- **DRM links** (`DrmExtractorLink`): hidden + counted as unsupported.
- **Subtitle charset**: streamed sidecars are UTF-8 (upstream parity; their
  charset conversion only exists in the download path).
- **CS downloads**: not wired — the episode-row download path surfaces the
  honest "downloads arrive with the downloads port" message.
- **Casting**: `isCasting=false` always (no cast support in this port).
- **PlaybackCache**: CS never touches the proxy — per-link OkHttp DataSources
  carry headers/referer natively, so the old 428-on-redirect class of failure
  is structurally impossible on this path.

## 5. Test coverage (CI-gated, `:core:cs-player` + `:data:cloudstream`)

- `CsMediaTypesTest` — the upstream mime maps (video + subtitle + URL fix).
- `CsQualityTest` — quality int ↔ label formatting (the ABI `Qualities` semantics).
- `CsVideoLinkHeadersTest` — referer merge / UA extraction / display labels.
- `CloudstreamLinkResolverTest` — 9 end-to-end locks with a fake provider
  registered into APIHolder: progressive snapshots, dedup, torrent hiding,
  subtitle unique-ifying, DASH first-class, missing provider, plugin crash →
  descriptive failure, first-link timeout, cache hit without provider.
