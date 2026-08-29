# 19 — Integration Playback & Downloads (CS3 Adoption Plan)

> **Scope**: THE runtime plan for playing and downloading CloudStream-resolved content in
> ANI-KUTA — the link-model bridge (`ExtractorLink` → extended `SourceVideo`), MPV playback
> of CS3 links (headers/referer verified against our player code), the resolve flow
> (episode click → link picker → player handoff), `extractorData` keep-alive, the subtitle
> pipeline, downloads through our `DownloadOrchestrator`/`HttpDownloader`/`HlsDownloader`,
> playback-cache keying, error handling, v1 non-goals, and the verification plan.
>
> **Position in the doc set**: doc 16 (architecture — §4 bridge design) and doc 17
> (data layer — §4 resolve contexts, §8 downloads) set the *shape*; this doc owns the
> *runtime*: everything between "provider bridge emitted a `SourceVideo`" and "pixels /
> bytes on disk". Doc 18 (Cloud Screen UI) owns the browse/details surface. Design debts
> explicitly assigned here by doc 17 §8.2/§8.3: HLS resume granularity (OQ#9), the download
> verifier hook, playback-side re-resolution.
>
> **Playback truth (from prior batches — cited, not re-derived)**: OUR player is **MPV**
> (aniyomi-mpv-lib 1.18.n — doc 09 §8 correction of the task brief; `AK/gradle/libs.versions.toml:74`).
> CS3's app uses ExoPlayer/Cronet (doc 09 §1.1) but **we adopt only their LINKS, not their
> player** — their `ExtractorLink` model is the payload we must render playable in MPV.
>
> **Citation keys**
> - `NN §x` = docs 01–17 in this folder
> - `AK/<path>:line` = ANI-KUTA app tree (`/home/z/ANI-KUTA-WORK/ANI-KUTA/ANI-KUTA/APP/ani-kuta/…`)
> - `EMU` = `AGENT-CONTEXT/knowledge/emulator-testing.md`
> - Markers: `[verified]` read in source this batch · `[docs]` from a prior doc, spot-checked ·
>   `[design]` this doc's proposal · `[recommendation]` · `[open-question]` · `[gap]`.

---

## 0. Executive summary — the playback plan in 10 decisions

| # | Question | Decision | Where |
|---|---|---|---|
| D1 | Can MPV play CS3 links with referer/headers? | **Yes — verified**: `MPVLib.setOptionString("http-header-fields", …)` before `loadfile`, the exact path aniyomi streams already use (`AK/feature/watch/impl/…/WatchScreen.kt:663-706`) | §2.1 |
| D2 | Where do the 9 `ExtractorLink` fields go? | Extended `SourceVideo` (doc 16 §4.2) verbatim + `qualityHeight: Int` sort key; referer folded into the MPV header string the same way CS3 folds it into its DataSource (`getAllHeaders()`, 05 §7.3) | §1 |
| D3 | M3U8 in MPV? | Native (ffmpeg HLS demuxer) — same path as aniyomi m3u8 streams; optionally through our `CacheProxyServer` playlist rewriter | §2.2 |
| D4 | DASH / DRM / torrent / live? | **All skipped in v1**: MPV has no DASH demuxer / Widevine; TorrServer is a Go-AAR we won't vendor; our watch flow is VOD-shaped | §2.5, §9 |
| D5 | Resolve flow UX? | Cloud link picker sheet reusing the `DownloadVideoPickerSheet`/`ResolverSheet` accordion, fed by a **streaming** `Flow<SourceVideo>` (callback→Flow adaptation per doc 16 §4.3); links render as they arrive; CS3 timeout clamp 5 s–8 min (default 120 s) | §3 |
| D6 | `extractorData` keep-alive? | `CloudStreamRuntime`-scoped verifier job at the two CS3 call sites (player link-select, download start), cancelled on switch/completion; persisted best-effort in `CloudStreamResolveContext` (doc 17 §4.4) | §4 |
| D7 | Subtitles? | `SubtitleFile.headers` flows into our `SubtitleEngine` (already downloads subs with headers to temp files → `sub-add` local path — `AK/core/player/subtitles/SubtitleEngine.kt:14-31`); MPV renders SRT/VTT/ASS/SSA natively | §5 |
| D8 | Downloads? | **Reuse our machinery** (doc 17 §8.2 confirmed): VIDEO → `HttpDownloader` parallel/single fetcher; M3U8 → `HlsDownloader` (AES-128, `.ts` honest naming, sidecar resume); CS3's TS-concat-as-`.mp4` NOT copied | §6 |
| D9 | Playback cache for CS3? | Same proxy, ecosystem-qualified keys (`source_key TEXT` — doc 17 §4.5); cache HIT ≠ link freshness — short-TTL upstreams need a 403→re-resolve ladder; encrypted HLS bypasses caching | §7 |
| D10 | Playback E2E on the emulator? | **No** — sandbox IP fails Cloudflare (`EMU` ❌-list) **and** MPV `.so` is arm-only (`AK/core/player-mpv-lib/…/PlayerMpvLib.kt:16-18`); emulator covers install→browse→resolve→picker + download queue states; real playback stays on the user's device | §10 |

The one-line spine: **CS3 gives us `ExtractorLink`s; we already have a headers-aware MPV
pipeline, a headers-aware subtitle engine, a type-routing downloader and an identity-keyed
cache — the work is bridge plumbing (model mapping + flow adaptation + verifier hook +
ecosystem-qualified identities), not a new playback stack.**

---

## 1. The link model bridge — `ExtractorLink` ↔ extended `SourceVideo`

### 1.1 The mapping table (every field → where it goes)

CS3 `ExtractorLink` (9 fields + `audioTracks`, 05 §7.3) → extended `SourceVideo`
(doc 16 §4.2 — additive fields with defaults, binary-compatible) → our runtime models:

| `ExtractorLink` field (05 §7.3) | Extended `SourceVideo` (doc 16 §4.2) | Our runtime consumer | Notes |
|---|---|---|---|
| `url: String` | `url` | `ResolverVideo.url` → MPV `loadfile` | Page-embed resolution already done by the extractor (05 §7.3). |
| `name: String` | `label: String?` | picker row label | Quality/entry label ("DoodStream 720p"); CS3 renders rows as `"$name ${Qualities.getStringByInt(link.quality)}"` (09 §2.1, GP:1139-1143). |
| `source: String` | `source: String?` | `ResolverServer.name` (tier-1) + re-resolve 2nd key (doc 17 §4.3) | Hosting-site label ("DoodStream"); how CS3 maps a link back to its provider (`getApiFromNameNull(link.source)`, 08 §4.6). |
| `referer: String` | `referer: String?` | folded into the MPV header string | CS3's own merge rule: `getAllHeaders()` merges referer into headers *unless a referer key exists* (05 §7.3, EA:738-746) — we copy that exact rule (§2.1). |
| `headers: Map<String,String>` | `headers: Map<String,String>` | MPV `http-header-fields` string + `DownloadTrack.headers` + sub-engine fallback | UA/cookies. Note the MPV string format has a comma hazard — §2.1b. |
| `quality: Int` (px height) | `quality: String` label **+ `[design]` `qualityHeight: Int?`** | picker sort / `videoTitle` | `Qualities.getStringByInt(1080) = "1080p"`, `Unknown(400) → "Unknown"` (05 §6.5). Our `ResolverVideo.quality` is a display String and our AutoDownloadEngine matches on it — keep the Int as an additive sort key so CS3 ordering (`sortedBy { -it.quality }`, 08 §4.5) survives translation. |
| `type: ExtractorLinkType` | `type: SourceVideoType` (VIDEO/M3U8/DASH/TORRENT/MAGNET) | download routing + playability filter | 05 §6.6; drives §2.5 filtering and §6 routing. |
| `extractorData: String?` | `extractorData: String?` | verifier job (§4) + `CloudStreamResolveContext` (doc 17 §4.2) | Keep-alive token; "don't set unless the provider implements `extractorVerifierJob`" (03 §2.10). |
| `audioTracks: List<AudioFile>` | `audioTracks: List<SourceAudioTrack>` | MPV `audio-add` (§5.4) | `AudioFile(url, headers)` — headers ride the same fallback rule as subs (`AK/app/…/download/DownloadOrchestrator.kt:201-225`, D-FIX-SUB). |
| *(subclass)* `DrmExtractorLink` | not mapped | — | v1 skip (§9). |
| *(subclass)* `ExtractorLinkPlayList` | not mapped | — | v1 skip (§9); rare multi-slice concat format (05 §7.5). |

**Dedup + labeling rules `[design]`** — port CS3's central layer (08 §4.4) into the bridge,
because plugin quality is exactly the place where it earns its keep (doc 12 §9.3 #5, #9):

1. **Dedup by URL only** — `if (link.url.isBlank() || !seen.add(link.url)) drop`
   (RLG:137-139). Same URL from two mirrors collapses; the app never sees doubles.
2. **Subtitle name uniquification** — HTML-decode `lang`, then per-name counter suffix
   ("English", "English 2") so duplicate langs stay selectable (RLG:119-126; surfaced as UX
   in CS3's dialog, 09 §2.1). We need this: CS3 providers genuinely emit duplicate langs
   (UAFlix per-dub-group subs, 08 §6.3(d)).
3. **Type filter sets** — our analogue of `LOADTYPE_INAPP` / `LOADTYPE_INAPP_DOWNLOAD`
   (08 §4.4, IGenerator.kt:6-25): the bridge filters `SourceVideoType` per consumer —
   player set = {VIDEO, M3U8} (DASH/TORRENT/MAGNET unplayable in MPV, §2.5), download set
   = {VIDEO, M3U8} (identical to CS3's `LOADTYPE_INAPP_DOWNLOAD`, 08 §4.4). Filtered-out
   links are counted and surfaced as "N links hidden (unsupported type)" — CS3's italic
   footer pattern (09 §2.1) — rather than silently dropped, per our D-295/D-296 visibility
   standard (doc 14 §2.5).

### 1.2 `SubtitleFile` → our subtitle handling

CS3 `SubtitleFile(lang, url, headers?)` (05 §7.1 — note: real shape has **no** `label`/
`source`; `langTag` is derived). Mapping `[design]`:

```
SubtitleFile(lang, url, headers)
  → SourceSubtitleTrack(url, lang, headers)          // NEW field on the track type (doc 16 §4.2
                                                     // lists SourceSubtitleTrack; add headers)
  → ResolverSubtitleTrack(url, lang, headers)        // [design] extend AK type (ResolverTypes.kt:76-79)
  → WatchKey wire: "url\u001Flang\u001FheadersCsv"   // [design] 3rd field, additive (parse keeps
                                                     // 2-field entries working — WatchKey.kt:111-118)
  → SubtitleEngine.downloadSubtitles(SubtitleDownloadRequest(url, lang, headers))
```

Our engine is already headers-aware (`AK/core/player/subtitles/SubtitleEngine.kt:162-184`
parses the header string and injects it into the OkHttp request + default UA) — the CS3
`headers` field slots straight into `SubtitleDownloadRequest.headers`. **Per-track headers
finally get a producer**: today the resolver side has none (doc 08 §8.2 gap "Subtitles
second-class") and the orchestrator falls back to video headers
(`DownloadOrchestrator.kt:201-208`); CS3's `SubtitleFile.headers` populates the new field,
with the video-header fallback retained for aniyomi.

### 1.3 Labeling: CS3 conventions → our 3-tier sheet

Our picker hierarchy is Server → AudioVersion → Video (`AK/core/video-resolver/…/ResolverTypes.kt:29-70`).
CS3 has a *flat* link list, so the bridge synthesizes the tiers `[design]`:

| Tier | CS3 input | Example |
|---|---|---|
| Server (`ResolverServer.name`) | `ExtractorLink.source` | "DoodStream", "VidHide" |
| Audio (`ResolverAudioVersion.label`) | constant `"Default"` (CS3 audio multiplexes as `audioTracks`, not as separate links — 05 §7.2) | "Default" |
| Video (`ResolverVideo.quality`) | `Qualities.getStringByInt(quality)` | "720p" |

Row label = `"$source $quality"` — identical to CS3's own row format (09 §2.1) so the
Cloud picker reads the same as a CS3 user expects. `videoTitle` (our stable-identity
string, `"server|audio|quality|$urlHash"` — `AK/core/playback-cache/…/PlaybackCacheModels.kt:50`)
is built by the cloud resolver as `"$source|Default|$qualityLabel|${sha256(url).take(8)}"`;
`serverKeyFromVideoTitle` then drops the volatile hash → `"DoodStream|Default|720p"`,
matching doc 17 §4.5's "label minus volatile segments" rule.

---

## 2. MPV playback of CS3 links

### 2.1 THE verified mechanism — headers + referer in MPV

**Yes, MPV takes headers — this is the exact, verified path our player already uses for
aniyomi streams, and CS3 links ride it unchanged** `[verified]`:

```kotlin
// AK/feature/watch/impl/src/main/java/com/confused/anikuta/feature/watch/WatchScreen.kt:686-694
// D-199: ALWAYS set http-header-fields, even for localhost proxy URLs. …
if (!isContentUri) {
    val headers = if (currentVideoHeaders.isNotBlank()) currentVideoHeaders
        else "User-Agent: Mozilla/5.0 (Linux; Android 14) … Mobile Safari/537.36"
    MPVLib.setOptionString("http-header-fields", headers)
    …
}
// then, at :731:
MPVLib.command(arrayOf("loadfile", cachedUrl(loadUrl, loadHeaders), "replace"))
```

Facts pinned by source:

- **Order matters**: headers are set BEFORE `loadfile`, at every load site — initial load
  (`WatchScreen.kt:663-734`), auto-retry (`:583-597`), manual retry (`:840-850`), quality
  switch (`:897-902`), episode switch (`:1143-1151`).
- **Format**: a single comma-separated string `"Key: Value,Key2: Value2"`
  (`AK/core/video-resolver/…/ResolverState.kt:44-46`; wire format documented on
  `WatchKey.videoHeaders`, `WatchKey.kt:28-33`). Referer is not a special MPV option —
  it's just another header line.
- **Every URL-flavor handled**: `content://` downloaded files convert to `fd://`
  ParcelFileDescriptor (`WatchScreen.kt:672-684`), network URLs optionally wrap through the
  cache proxy (`cachedUrl(...)`, `:546-553`) which itself re-applies headers upstream
  (`PlaybackCacheManager.playbackUrlFor(id, upstreamUrl, headers, …)`,
  `AK/core/playback-cache/…/PlaybackCacheManager.kt:147-186`).

**CS3 mapping `[design]`**: build the MPV header string at the bridge:

```kotlin
// [design sketch] — CS3 referer/headers → MPV string (mirrors CS3's own fold rule,
// createVideoSource at 09 §1.4: refererMap + link.headers unless a referer key exists)
fun ExtractorLink.toMpvHeaders(): String {
    val map = buildMap {
        if (!referer.isBlank() && headers.keys.none { it.equals("referer", true) })
            put("Referer", referer)
        putAll(headers)
        if (keys.none { it.equals("User-Agent", true) })
            put("User-Agent", DEFAULT_UA)   // CS3 does the same: UA ?: app default (09 §1.4)
    }
    return formatMpvHeaderString(map)        // §2.1b
}
```

**(b) The comma hazard `[gap]` — must fix before CS3 ships.** MPV's `http-header-fields`
is a *string-list* option: unquoted commas separate entries. CS3 headers are
provider-controlled maps whose values may contain commas (UA strings like
`"…(KHTML, like Gecko) Chrome/…"` — the exact D-207 lesson class documented on our own
parser, `PlaybackCacheModels.kt:158-164`). Today the default-UA fallback string already
contains commas and is passed unquoted — the read-side parser was hardened (regex per
entry) but the **write side never was**. `[design]` `formatMpvHeaderString` escapes
commas/backslashes inside *values* using mpv's list quoting, and `MpvHeaderParser`
strips the escaping on read — one writer + one reader, both sides ours. Verification of
the escape round-trip belongs on the device test checklist (§10.5); `[open-question]` Q1
if libmpv's parser rejects the quoting we pick.

### 2.2 M3U8 (HLS) in MPV — same path as aniyomi

MPV plays HLS natively (ffmpeg `hls` demuxer); we pass m3u8 URLs to `loadfile` untouched
today for aniyomi streams, and CS3 `M3U8` links take the identical path `[verified — code
path; MPV demuxer is upstream behavior]`. Two CS3-specific notes:

- **CS3 link URLs are usually already variant playlists**: `M3u8Helper.generateM3u8(...)`
  fans out one `ExtractorLink` *per quality variant* (08 §1.3 #3, 08 §6.3(b)) — so the
  master-vs-variant question that CS3's player answers with `setMaxVideoSize` (09 §2.3)
  is mostly pre-answered for us: our link *picker* is the variant chooser.
- **Cache proxy path works for HLS too** — our `CacheProxyServer` rewrites master/media
  playlists so segments route through the proxy (variant URIs → `/p/<key>/<i>`, segment
  URIs → `/s/<key>/<i>`, `EXT-X-MAP` init → `/s/<key>/init`), caching per-segment
  (`PlaybackCacheManager.kt:650-754`). Caveat: no `#EXT-X-KEY` handling (verified absence
  in the module) — see §7.4 for the encrypted-HLS policy.

### 2.3 Quality / link switching mid-playback

Current UX: the in-player `QualitySheet` lists servers from `ResolvedVideosRegistry`
(`WatchScreen.kt:465-485`); picking a video calls `onQualitySelected` →
`MPVLib.setOptionString("http-header-fields", …)` + `loadfile(…, "replace")`
(`:865-907`) — a **full reload**. For CS3 this is the mirror-switch flow, and CS3's
discipline is worth copying: their switch passes `sameEpisode=true` so **position
survives** (09 §1.6, §2.3). `[gap]` our `onQualitySelected` does NOT re-seek after the
reload (resume-seek only fires on the initial load, `if (!hasResumed)`,
`WatchScreen.kt:449-462`) — a quality switch mid-episode restarts at 0 today.
`[design]` add a `pendingSeekPosition` on the observer: on switch, capture
`position.value`, and after the next FILE_LOADED seek to it. This is a *both-ecosystems*
improvement, cheap, and directly needed for CS3's many-mirror reality. `[recommendation]`

### 2.4 MPV has no per-request interceptor — and doesn't need one

CS3's `getVideoInterceptor(link)` hook exists because ExoPlayer needs an OkHttp
interceptor for exotic per-link request rewriting (08 §4.7). MPV has no interceptor
concept — headers + URL is the whole channel (09 §8 mapping bullet makes the same
observation). The hook is dead weight for us: **0 plugin usages in the census**
(doc 16 §4.4, doc 03 §2.11) → the bridge simply ignores it. No action.

### 2.5 DRM, live, torrent, DASH — v1 dispositions

| Link/link-class | v1? | Reason (cited) |
|---|---|---|
| `DrmExtractorLink` (Widevine/PlayReady/ClearKey) | **Skip** | MPV has no MediaDrm/Widevine integration; CS3's own DRM path is DASH-only through Media3 (09 §1.5) — meaningless without a DASH demuxer (next row). DRM'd links are filtered out of the playable set with the §1.1 "N hidden" affordance. |
| `ExtractorLinkType.DASH` | **Skip playback** | MPV/libmpv has no DASH demuxer (ffmpeg does not demux dynamic MPDs). Note even CS3 excludes DASH from downloads ("no support at the moment", 05 §6.6; `LOADTYPE_INAPP_DOWNLOAD` = {VIDEO, M3U8}, 08 §4.4). `[open-question]` Q10 — count DASH prevalence in the real-plugin corpus before deciding on a premux path. |
| `TORRENT` / `MAGNET` | **Skip** | CS3 plays torrents via an embedded Go TorrServer (gomobile AAR) rewriting the link to a local HTTP stream (09 §1.7) — vendoring a Go runtime for a link type our target providers barely emit (magnets: 1/58 census providers, 12 §9.2) is not worth the binary + legal surface. Filtered out with "N hidden". |
| `TvType.Live` / `newLiveStreamLoadResponse` | **Skip** | Our watch flow is VOD-shaped (episode list, resume, watch-progress); CS3's live handling is itself only half-documented (09 §9 "LiveManager only glanced at"). Live providers simply won't be installable/useful in v1 — their episode lists are single pseudo-episodes; degrade to a details page with a play button is possible later. |
| `ExtractorLinkPlayList` | **Skip** | Multi-slice concatenation (05 §7.5); MPV could model it as a playlist but the duration bookkeeping CS3 does (09 §1.4 slices) has no analog on our side. Rare in the wild (no census provider emits one, 12 §9.2). |

---

## 3. The resolve flow — episode click → links → picker → player

### 3.1 Flow design

```
 User        Cloud Screen (doc 18)         CloudStreamExtensionProvider (doc 16 §4)     CloudStreamRuntime (doc 16 §8)      WatchScreen (MPV)
  │                │                                │                                       │                              │
  │ tap episode ──▶│ build playback inputs          │                                       │                              │
  │                │  mainId, canonical episodeKey  │                                       │                              │
  │                │  providerKey "cloudstream:X"   │                                       │                              │
  │                │  episodeData (Episode.data)    │                                       │                              │
  │                │ fetchVideoList(episode) ──────▶│ runtime.call(provider, loadLinksTimeoutMs) ─▶│                          │
  │                │                                │  loadLinks(data, isCasting=false,          │                            │
  │                │                                │    subtitleCb, linkCb)                    │                            │
  │                │                                │  ── per link: dedup(URL) → emit ───────────│                            │
  │                │  ◀── Flow<SourceVideo> emissions (streaming; first link often <2s) ──────────│                            │
  │                │ CloudLinkPickerSheet renders rows as they arrive ("2/… links")               │                            │
  │                │ [skip-loading button / cancel → Flow collection cancelled]                   │                            │
  │                │                                │  ── terminal Boolean + collected set ──────│                            │
  │                │  map → ResolverServer(source)/"Default"/quality (§1.3)                       │                            │
  │                │  ResolvedVideosRegistry.put(servers) → resolvedVideosKey                     │                            │
  │ tap link ─────▶│ WatchKey(videoUrl, mpvHeaders, quality, tracks incl. headers,                │                            │
  │                │        resolvedVideosKey, sourceKey, mainId, episodeKey) ───────────────────────────────────────────▶ │
  │                │                                │                                       │   setOptionString("http-header-fields") │
  │                │                                │                                       │   loadfile(url | cachedUrl(url))        │
  │                │                                │  verifier: extractorData != null? ────▶ runtime.launchVerifier(§4)      │
  │                │                                │                                       │   FILE_LOADED → sub-add(temp files)      │
```

### 3.2 The streaming adaptation (what the bridge does)

Doc 16 §4.3 already fixed the API shape: `loadLinks`'s callback-streaming contract (08 §1.3
— links arrive *before* the call returns; the app's wrapper can be cancelled mid-stream and
already-emitted links survive) maps to a `callbackFlow`/`channelFlow` emitting
`SourceVideo`s, with a terminal mapping `Boolean + collected-links → Success |
ErrorLoadingException` (CS3's own user-visible convention, 03 §2.4).

Runtime details owned HERE `[design]`:

1. **Timeout**: `CloudStreamRuntime.call(provider, hint = loadLinksTimeoutMs)` with CS3's
   clamp `(hint ?: 120_000).coerceIn(5_000, 480_000)` (doc 16 §8; verified numbers 08 §1.4:
   default 120 s, min 5 s, max 8 min). Any throwable — including
   `TimeoutCancellationException` — becomes a soft failure: **streamed links are kept**
   (CS3 semantics, 08 §1.4: "streamed links are NOT rolled back").
2. **`isInvalidData` guard**: reject `""`/`"[]"`/`"about:blank"` episodeData up-front
   (08 §1.4, AR:55-58) — cheap provider-hygiene win.
3. **Cancellation**: sheet dismissed / "skip loading" → cancel Flow collection → the
   `withTimeout`/suspend machinery cancels the plugin coroutine (suspend-cooperative;
   CPU-spinners bounded by the timeout — doc 16 §8). The picker keeps whatever arrived.
4. **WebView resolver runtime**: some extractors fall back to `WebViewResolver` — doc 16 §8
   explicitly deferred "main-thread hop + headless WebView lifetime" to this doc.
   `[design]` one app-owned `WebViewResolverHost` (a single headless `WebView` created on
   the main thread via `withContext(Dispatchers.Main)`, kept in an LruCache(2) keyed by
   the resolving job, destroyed on job completion or 30 s idle). We already ship a WebView
   for the aniyomi CF shortcut (doc 14 §7.4) so no new capability class. Failure = the
   extractor's normal exception path (logged, that host yields no links).

### 3.3 The link picker sheet `[design]`

Reuse, not new UI: the sheet is `DownloadVideoPickerSheet`'s accordion
(`AK/feature/download/…/DownloadVideoPickerSheet.kt:63-127` — ModalBottomSheet,
`dragHandle = null`, single-expand server cards, quality FlowRow chips) with three CS3
additions:

1. **Streaming state**: instead of waiting for a terminal list (our `ResolverSheet`
   spinner-till-done pattern, doc 08 §8), rows appear as emissions land; header shows
   `"Loading… N links"` with a running count; a **Skip-loading** action appears after the
   first link (CS3's skip, 09 §1.6) that cancels resolution and plays/plays-later from
   what's there.
2. **Type affordance**: chips carry a small format glyph (M3U8/MP4) from
   `SourceVideoType`; filtered DASH/torrent links counted in a footer ("3 links hidden —
   unsupported in v1"), per D-295/D-296 visibility (doc 14 §2.5).
3. **Subtitle preview**: incoming `SourceSubtitleTrack`s listed under a collapsed
   "Subtitles (N)" header (they ride the picked video regardless; CS3 shows them in the
   same dialog, 09 §2.1).

The same sheet component serves the download-mirror pick (CS3's `acquireSingleLink`
pattern, 08 §4.3 trigger #2) — one picker, two call sites, consistent with how
`DownloadVideoPickerSheet` is already reused by `EnqueueResult.ShowPicker`.

### 3.4 The player handoff payload

`WatchKey` (nav wire, `AK/feature/watch/api/…/WatchKey.kt:11-86`) gains CS3 fields
`[design]` (all additive with defaults, nav-safe):

| New field | Content | Why |
|---|---|---|
| `sourceKey: String = ""` | `"cloudstream:<providerName>"` (doc 17 §6.1) | Replaces `sourceId: Long` for cloud content; `sourceId` stays for aniyomi (both carried; exactly one non-default). |
| `mpvHeaders` — reuse `videoHeaders: String` | §2.1 `toMpvHeaders()` output | No new field; the format is already the documented wire format. |
| `subtitleTracksSerialized` — 3rd field | `"url\u001Flang\u001FheadersCsv"` per line | Per-track headers (§1.2); parser stays backward-compatible (limit-split, 2-field lines still parse). |
| `episodeKey: String = ""` | canonical season-qualified key (doc 17 §3.3) | Watch-progress + cache identity; today WatchScreen rebuilds `"mainId|%05d"` from a Float episode number (WatchScreen.kt:454) — CS3 seasons need the canonical form carried explicitly. |
| `linkSource: String = ""` | `ExtractorLink.source` | 2nd-tier re-resolve key (doc 17 §4.3) + verifier lookups. |

Episode switching inside WatchScreen currently resolves via
`ExtensionManager`+`VideoResolver` (aniyomi-shaped, `WatchScreen.kt:930-1151`). For cloud
content `[design]` the switch handler dispatches on `sourceKey` prefix to the provider
registry (doc 16 §5) → `fetchVideoList(episode)` → same registry-put + reload dance. This
is the single WatchScreen extension point CS3 playback actually needs; everything else
(headers, tracks, cache, error overlay) is shared code.

### 3.5 The 20-minute link cache — port CS3's saturated short-circuit `[recommendation]`

CS3 caches resolved links per `(apiName, episodeId)` for **20 min** with a `saturated`
flag: a completed run with ≥1 link short-circuits future calls entirely, making replay
and "skip loading" instant (08 §4.4, RLG:15-104). Our `ResolvedVideosRegistry` is
process-lifetime with no TTL (doc 08 §8; `ResolvedVideosRegistry.kt:23-26` "NOT
automatically cleared"). `[design]` the cloud resolver wraps the registry with a
TTL'd `(providerKey, episodeKey) → (servers, timestamp, saturated)` map (20 min, CS3
empirics): within TTL a re-open serves instantly; past TTL the registry entry is replaced
on next resolve. Downloads already get durable re-resolve via `resolve_context` (§6.4),
so the TTL cache is playback-only and intentionally memory-resident — CS3 itself never
persists links (08 §9).

---

## 4. `extractorData` keep-alive — our ReResolver analog for CS3

### 4.1 What CS3 does (verified, 08 §4.6)

- Providers set `ExtractorLink.extractorData` when the link needs active keep-alive
  (short-TTL hosts, sflix-class). Default `extractorVerifierJob` **throws
  NotImplementedError**, swallowed by `ioSafe` at both call sites.
- Exactly two call sites: player `loadExtractorJob` (GP:256-268, started on link select,
  cancelled on switch) and downloader (DM:1487-1495, runs for the download's duration).
- Not persisted across restarts — only the 20-min cache and queue-item links carry it.

### 4.2 Our design `[design]`

```kotlin
// [design sketch] — lives in :data:cloudstream next to CloudStreamRuntime (doc 16 §2)
class CloudStreamVerifier(private val runtime: CloudStreamRuntime, private val manager: CloudStreamPluginManager) {
    private val jobs = ConcurrentHashMap<String, Job>()   // key: playback cacheKey / download taskId

    /** Playback site — mirror of GP:256-268. Call on link select; cancel on switch/dispose. */
    fun startForPlayback(key: String, sourceKey: String, extractorData: String?) {
        cancel(key)
        if (extractorData == null) return
        jobs[key] = runtime.scope.launch {
            runCatching {           // NotImplementedError + anything else: log, never crash (08 §4.6)
                manager.providerBy(sourceKey)?.extractorVerifierJob(extractorData)
            }.onFailure { Logger.w(TAG) { "verifier($sourceKey) ended: ${it.message}" } }
        }
    }
    /** Download site — mirror of DM:1487-1495. Hook owned by DownloadService (doc 17 §8.3). */
    fun startForDownload(taskId: Long, …) { … }
    fun cancel(key: String) { jobs.remove(key)?.cancel() }
}
```

**When we re-verify (the ladder):**

| Trigger | Action | Rationale |
|---|---|---|
| Link selected for playback | start verifier; cancel on link/episode switch or screen dispose | CS3's exact placement (GP:256-268). |
| Download starts | start verifier (if `resolve_context` carries `extractorData`); cancel on complete/fail/cancel | Doc 17 §4.4: persisted best-effort so a *resumed* download restarts it immediately. |
| HTTP 403 / stream error mid-playback | **not** a verifier concern — verifier is preventive. Recovery is the §8 ladder (retry → re-resolve → next link). | CS3 has no 403-triggered recovery either (08 §4.6 "no automatic recovery" is the documented failure mode); our ladder is deliberately better. |
| Periodic re-resolution while idle | **No.** | Verifier jobs are the provider's own keep-alive; duplicating with blind re-resolves would double host load (§8.4 ethics) for no benefit — the pinned context re-resolve is the fallback, not a heartbeat. |

**What we persist**: `CloudStreamResolveContext.extractorData` (doc 17 §4.2/§4.4) — best
effort, regenerated on every re-resolve; if stale the verifier throws into `runCatching`
and the worst case equals stock CS3 behavior (link dies → error → ladder).

**Failure UX**: silent by design (background job). If the verifier dies *and* the link
subsequently expires, the user sees the standard `PlayerErrorOverlay`/error banner with
the real reason (our D-295 discipline — error messages carry the actual failure, doc 14
§2.5) plus a **"Try another link"** action (§8.2 step 3) — this is where CS3's
"no automatic recovery" gap (08 §4.6) becomes our improvement.

---

## 5. Subtitle pipeline

### 5.1 The verified path (ours)

External subs are downloaded to temp files and `sub-add`ed as **local paths** — because
MPV's `sub-add` with URLs "doesn't support custom HTTP headers" and CDNs 403 without them
(`AK/core/player/…/PlayerObserver.kt:342-372`; rationale block
`SubtitleEngine.kt:14-31`):

```
subtitleCallback(SubtitleFile)                     // CS3 side (08 §5)
  → SourceSubtitleTrack(url, lang, headers)         // bridge (§1.2)
  → WatchKey.subtitleTracksSerialized               // wire (3-field lines)
  → PlayerObserver.pendingSubtitleTracks + trackHeaders   // set BEFORE loadfile (WatchScreen.kt:615-634)
  → on FILE_LOADED: SubtitleEngine.downloadSubtitles(     // PlayerObserver.kt:169-221
        SubtitleDownloadRequest(url, lang, headers))      // headers = track headers ?: video headers
  → MPVLib.command(arrayOf("sub-add", localPath, "auto", "", lang))  // PlayerObserver.kt:359
```

CS3's `SubtitleFile.headers` slots into `SubtitleDownloadRequest.headers` with zero engine
changes — the engine already parses "K: V,…" headers, adds a default UA, and tolerates
individual failures (failed tracks are skipped, `SubtitleEngine.kt:75-88`).

### 5.2 Format support

- **MPV renders SRT, VTT, ASS, SSA natively** (libass; doc 09 §8's verdict: our ASS
  rendering with the embedded `subfont.ttf` is "arguably better for fansub ASS" than
  CS3's cue-based pipeline). Our engine's extension guessing covers `.vtt/.srt/.ass/.ssa/.sub`,
  defaulting to `.vtt` (`SubtitleEngine.kt:216-226`).
- **TTML/`.xml`** (CS3's third mime, 08 §5): MPV/ffmpeg do not demux external TTML.
  Rare in the corpus (census: subtitles at all = 1/58 providers, 12 §9.2). `[design]`
  pass through anyway — the engine's failure path (HTTP fail / sub-add fail → logged,
  skipped, PlayerObserver.kt:363) degrades gracefully; add a mime sniff later if a
  provider we care about emits TTML. `[open-question]` Q8.
- **Charset**: CS3 needs juniversalchardet for windows-1256-class subs (09 §3.2). MPV
  sniffs subtitle charset itself (`--sub-codepage`/auto) — no action v1; note as a device-test
  assertion for Arabic/Cyrillic subs.

### 5.3 Selection UX in the player

No new surface: `sub-add` registers external tracks in MPV's track-list; the existing
subtitle sheet selects via `MPVLib.setPropertyInt("sid", trackId)` /
`"sid" = "no"` (`WatchScreen.kt:909-920`), and embedded tracks are discovered the same
way. CS3's `REQUIRES_RELOAD` dance (sub arriving after playback start forces a source
reload, 09 §2.1) does not apply — our subs are attached at FILE_LOADED before playback
begins; a link switch re-attaches automatically (`pendingSubtitleTracks` re-set at every
switch site, `WatchScreen.kt:873-880`).

### 5.4 Audio tracks

CS3 `AudioFile`s ride `audioTracks` (05 §7.2) → `audio-add` with URLs + track headers as
`trackHeaders` (`PlayerObserver.kt:381-391`). Note asymmetry: **audio-add keeps URLs**
(no temp-file download) because audio segments are large and headers matter less — for
CS3, if an audio URL 403s, the add fails logged-and-skipped (same tolerance as subs).
`[open-question]` Q9 if a real provider needs headered audio badly enough to warrant the
temp-file treatment.

### 5.5 Subtitle downloads (with the video)

Our pipeline already downloads subs alongside videos with per-language filenames
(`HttpDownloader.downloadSubtitlesToCache` + publish, `AK/core/download/…/HttpDownloader.kt:115-140`,
D-FIX-SUB) using video headers as fallback (`DownloadOrchestrator.kt:201-225`). For CS3:
`DownloadTrack(url, lang, headers = subFile.headers ?: videoHeaders)` — per-track headers
finally used when present. CS3 caps download-subs at 3 + language filter (09 §5.6);
`[design]` v1 keeps our "download all, best-effort" and adds the cap + an IETF language
filter as a settings follow-up (only 1/58 census providers emit subs at all — 12 §9.2 —
so this is not urgent). Naming per doc 17 §8.4:
`"${title} - S02E05.<lang>.<i>.<ext>"`.

---

## 6. Downloads of CS3 content

### 6.1 What our machinery supports today (the verified answer)

| Capability | Status | Evidence |
|---|---|---|
| Direct VIDEO (mp4/mkv/…) over HTTP | ✅ `HttpDownloader` facade routes non-HLS through pluggable fetchers: `ParallelHttpFetcher` (multi-connection Range engine) or `SingleConnectionFetcher` | `AK/core/download/…/HttpDownloader.kt:9-56` |
| m3u8 (HLS) | ✅ `HlsDownloader`: playlist fetch → master→**first variant** (`pickFirstVariant`, :569-590) → parallel segment workers (connection-budget-capped) → ordered concat into honest `.ts` → **AES-128-CBC decrypt** (single key; rotating keys/SAMPLE-AES rejected, :310-341) → per-sequence IV → PNG-header strip → append-state sidecar for pause/resume | `HlsDownloader.kt:28-55, 123-132, 175-179` |
| Lying content-types | ✅ playlist re-detection (small file starting `#EXTM3U` → switch to HlsDownloader mid-download) + `m3u8 → .ts` extension mapping | `HttpDownloader.kt:99-113, 380-386` (doc 17 §8.2) |
| Parallel chunking | ✅ ours is connection-budget-capped per-task (CS3's is fixed 10 MiB chunks + 3 workers + 50 MB RAM cap, 09 §5.5) | `HttpDownloader.kt:15-17`; doc 09 §8 comparison |
| Resume | progressive: range-based; HLS: sidecar append-state (pause/resume). **Whole-file restart** after process death — no segment-index persistence (CS3's `extraInfo` scheme, 09 §5.5) | `HlsDownloader.kt:40-41`; doc 17 §8.2 OQ#9 |
| Subtitles | ✅ best-effort alongside video | `HttpDownloader.kt:115-140` |

**So: yes — CS3 content downloads on our existing paths without new fetchers.** What
changes is the *front* (resolve + context) and two targeted patches below.

### 6.2 Design — the cloud download entry path `[design]`

`DownloadOrchestrator` is aniyomi-shaped at its entry (`AnimeHttpSource`, `SEpisode`,
`AK/app/…/download/DownloadOrchestrator.kt:56-64, 143-164`). Rather than fork it:
`[recommendation]` generalize the two entry points behind the provider seam —
`enqueueDownload(providerHandle, episodeRef, content, episodeInfo)` /
`enqueueSpecific(...)` where the aniyomi impl resolves via `VideoResolver` and the CS3
impl resolves via `CloudStreamExtensionProvider.fetchVideoList` + maps links into
`ResolverServer`/`ResolverVideo` (§1.3 — same mapping the picker uses). Everything
downstream is already ecosystem-agnostic: `AutoDownloadEngine` consumes
`ResolverServer`s; `DownloadManager`/`DownloadQueue`/`DownloadService` consume
`DownloadRequest`s; the 7-state machine + retry/backoff are content-agnostic (doc 17 §8.1).

`buildRequest` deltas for CS3 `[design]`:

1. **`videoUrl` = `ExtractorLink.url`** (CS3 links are direct CDN URLs — no proxy, so the
   `directUrl ?: url` preference at `DownloadOrchestrator.kt:198-199` is a no-op; keep the
   logic, it's harmless).
2. **`videoHeaders`** = §2.1 `toMpvHeaders()` string (the download side parses the same
   format — `DownloadHeaderParser`).
3. **`resolveContext` = `CloudStreamResolveContext` ALWAYS** (not the localhost-only
   condition at `DownloadOrchestrator.kt:226-240`): CS3 URLs are short-TTL regardless of
   host — an expired URL must re-resolve exactly like proxy churn. Fields per doc 17 §4.2
   (`providerKey, contentUrl, episodeData, linkLabel, linkSource, quality, extractorData,
   mainId, episodeKey`).
4. **`sourceKey`** string instead of Long `sourceId` (doc 17 §6.2 sweep — the
   `download_queue.source_key` column change rides the doc 17 §9 migration).

### 6.3 M3U8 downloads — reuse + one adopted lesson

Doc 17 §8.2 already decided: **reuse our machinery**; do NOT copy CS3's
TS-concat-named-`.mp4` (ours honestly names `.ts`, and MPV plays TS fine). One CS3
behavior worth adopting `[recommendation]`:

- **Variant selection**: ours takes the FIRST variant (`pickFirstVariant`,
  `HlsDownloader.kt:574-590`); CS3 picks the best *standalone-playable* variant with audio
  muxed (`hslLazy`, 09 §5.5 — "m3u8 files can include separate tracks for dubs/subs").
  In practice CS3 `ExtractorLink`s are usually already per-variant (08 §1.3 #3), so the
  gap is narrow — but when a master URL sneaks through, first-variant can pick a
  video-only variant. `[design]` small patch: prefer the highest-bandwidth variant whose
  rendition has no `AUDIO` group attribute, falling back to first. ~20 lines, shares
  CS3's rationale without their code.

**HLS resume granularity (doc 17 OQ#9 — answered here)**: `[recommendation]` v1 keeps the
current sidecar pause/resume (in-session, already shipped) and does NOT persist
segment-index resume across process restarts. CS3's `extraInfo` scheme buys resume-after-
kill; our crash-recovery already resets `DOWNLOADING→QUEUED` (doc 17 §8.1) and a re-queue
re-resolves fresh links anyway (short-TTL URLs make a days-old partial resume mostly
pointless — the resolved URL will have expired before the restart). Revisit only if device
testing shows real mid-download kills. **No `extraInfo`-style column needed on
`download_queue`** (doc 17 §9.1 was awaiting this answer).

### 6.4 Queue entries, resume semantics, naming

- **Rows**: `download_queue` with `source_key = "cloudstream:<provider>"`, canonical
  season-qualified `episode_key` (doc 17 §3.3/§3.5 — download keys unify on the canonical
  regime, ending the `SEpisode.url` second regime), `resolve_context` = polymorphic JSON
  (`ecosystem` discriminator, doc 17 §4.2), display metadata denormalized as today
  (doc 15 §1.5). Old aniyomi rows: wiped per §30 dev-data freedom (doc 17 §9).
- **Resume semantics**: on `IOException`/403 mid-download → `ReResolver` CS3 branch
  (doc 17 §4.3): resolve provider by `providerKey` → re-run `loadLinks(contentUrl |
  episodeData)` → tier-match `(linkLabel, quality)` → same `linkSource` + nearest quality
  → ERROR (user re-picks). Cap stays **1 re-resolve** (`ReResolver.kt:21-22` — D.2/M15),
  then the row errors visibly (D-296 discipline). `updateDownloadVideoUrl` persists the
  fresh URL + headers (doc 17 §4.3 step 4).
- **Verifier hook**: `DownloadService` gains the `CloudStreamVerifier.startForDownload/
  cancel` calls at task start/terminal transitions (§4.2) — the hook doc 17 §8.3 assigned
  to this doc.
- **Naming/folders**: `"${title} - S02E05.mp4"` (+ `.ts` for HLS) and
  `"${title} - S02E05.<lang>.<i>.<ext>"` subs (doc 17 §8.4; folder-layout open question
  Q7). The `DownloadScanner` re-key pattern (SxxExx filename derivation) rides doc 17 §6.3.

### 6.5 What we do NOT adopt from CS3's downloader

- Their raw-TS-as-`.mp4` naming (ours is honest; some players/Scanners sniff anyway —
  ours documents reality).
- Their `DOWNLOAD_PARTIAL_SUCCESS` (≥50 MB but incomplete → retry-same-only) — our state
  machine has no such state; a partial file + error row + re-resolve covers it. Keep.
- Their auto-next-mirror on failure (`DownloadStatus` retry ladder, 09 §5.5): ours pins
  and re-resolves; auto-falling-through mirrors would fight the user's explicit mirror
  choice. `[open-question]` Q6 for an opt-in.
- `MIN video size 10 MiB` dead-link heuristic (09 §5.5): tempting; ours validates magic
  bytes + size already (`HttpDownloader.validateDownloadedFile`, :95-97). No change.

---

## 7. Playback cache implications

### 7.1 How CS3 links key into the cache

The cache identity is `PlaybackVideoId(mainId, animeTitle, episodeNumber, sourceId,
serverKey, quality)` → `sha256("mainId\u001FepisodeNumber\u001FsourceId\u001FserverKey")`
(`PlaybackCacheModels.kt:25-45`). Doc 17 §4.5 already fixed the CS3 form:
`source_id INTEGER` → **`source_key TEXT`** everywhere and the hash input becomes
`sha256("$mainId\u001F$canonicalEpisodeKey\u001F$sourceKey\u001F$serverKey")` — canonical
episode key (season-qualified) instead of the Float episode number. Concretely for a CS3
link: `sourceKey = "cloudstream:Uakino"`, `serverKey = "DoodStream|Default|720p"` (§1.3 —
the label minus volatile segments, same "minus urlHash" treatment as aniyomi's
`videoTitle`, doc 17 §4.5). `PlaybackVideoId.sourceId: Long` becomes `sourceKey: String`
(additive constructor default keeps aniyomi call sites compiling until the sweep).

`[gap]` note: `buildCacheId` (`WatchScreen.kt:2139-2157`) derives `serverKey` from the
aniyomi `videoTitle` string; the cloud watch path constructs the same string explicitly
(§1.3) so the existing derivation path is reused verbatim — no cache-manager changes
beyond the key type.

### 7.2 What a cache HIT means for re-resolution

A hit means **bytes are cached under a stable identity — it says nothing about link
freshness.** The proxy stores the *session's* upstream URL in its descriptor
(`descriptors[cacheKey] = PlayDescriptor(id, upstreamUrl, …)`,
`PlaybackCacheManager.kt:172`) and the last-known URL in the store for restarts
(`upstreamUrlFor`, `:188-190`). For aniyomi that URL is a localhost proxy (stable enough);
for CS3 the stored upstream is a **short-TTL CDN URL** — a next-day replay of a fully
cached episode will hit a 403 upstream. `[design]` the ladder:

1. Proxy fail-opens per byte-range miss / redirects upstream (existing behavior,
   `PlaybackCacheManager.kt:400, 505-514`) — MPV gets the 403 through the proxy.
2. WatchScreen auto-retry (same URL, 1.5 s, banner stays — `WatchScreen.kt:575-604`)
   handles transient blips only.
3. **NEW (CS3): cache-origin 403 → re-resolve via the persisted context.** When playback
   of a cached entry fails with an HTTP error and the entry's identity maps to a
   `CloudStreamResolveContext` (the entry carries the serialized tracks + we add
   `sourceKey`/`linkSource`), run the §6.4 tier-match re-resolve, then re-`loadfile` with
   the fresh URL under the SAME cacheKey (bytes are still valid — identity-keyed). This is
   the playback-side twin of the download ReResolver and the single most valuable
   resilience add for CS3. `[recommendation]`
4. Exhausted → error banner + "choose another link" (§8.2).

`[open-question]` Q4: do we ALSO opportunistically re-resolve before first-frame when a
cached entry is older than N hours (skip doomed plays early)? CS3 doesn't (no TTL
persistence — 08 §4.6); doc 17 §4.4's optional `resolved_url_expires_at` column exists for
it. Defer until device data shows frequent ladder-3 hits.

### 7.3 Eviction

Oldest-first over `maxCacheBytes`, active entries protected, throttled checks
(`PlaybackCacheManager.kt:1622-1644`) — purely size/age-based, ecosystem-agnostic. CS3
entries change nothing except key width (TEXT vs INTEGER in `playback_cache_entry`,
doc 17 §9.1 — cache is disposable anyway, doc 15 §1.5). No work here beyond the schema swap.

### 7.4 Encrypted HLS + the cache proxy

Verified absence: the proxy rewrites playlists and caches **segments** but has no
`#EXT-X-KEY` handling (grep — no `EXT-X-KEY`/AES in `core/playback-cache`; contrast
`HlsDownloader.kt:97-179` which does full AES-128 for downloads). Consequence: for an
encrypted playlist, MPV would fetch the key directly (its own HTTP stack honors
`http-header-fields`) — playback works, but cached segments are ciphertext keyed to a
short-TTL key URL: a replay after key expiry fails even though "cached". `[design]` at
playlist-learn time, if the media playlist contains `#EXT-X-KEY`, mark the entry
no-cache: serve-through without tee (fail-open stays). Cheap, honest; revisit with
transparent key-proxying if encrypted CS3 sources prove common. `[open-question]` Q3.

---

## 8. Error handling & resilience

### 8.1 The reality: sparse, dying mirror hosts

57 one-liner mirror extractors registered by a single plugin (doc 12 §6.5), fuzzy
Levenshtein>80 fallback matching for unregistered mirrors (08 §2.3), dead domains shipped
in released plugins (doc 12 §9.3 #5). A link list is a *menu of maybes*: some extractors
throw (logged, `loadExtractor` returns true — 08 §2.3 "a broken extractor still returns
true"), some hosts 403. The pipeline must treat per-link failure as normal.

### 8.2 The retry ladder `[design]` (player side)

| Step | Action | Existing basis |
|---|---|---|
| 1. Auto-retry same URL (once, 1.5 s, banner stays visible) | transient network/TLS | `WatchScreen.kt:564-604` — unchanged |
| 2. Cache-bypass direct retry | corrupt cached bytes | D-247 `bypassCacheNextRetry` (`WatchScreen.kt:591-597, 844-850`) — unchanged |
| 3. **Re-resolve pinned link** (CS3): tier-match via `CloudStreamResolveContext`, `loadfile` fresh URL, same cacheKey | expired/rotated URL | §7.2 step 3 — NEW; mirrors CS3's `forceClearCache`+next-mirror intent (09 §1.6) with our pinned-context discipline |
| 4. **Next link, position preserved**: present "Try another link" sheet from the registry list (or auto-advance if the user enabled it) | dead host | CS3 `nextMirror` (`:1706-1714`, position kept via `sameEpisode`) — our §2.3 position-preservation makes this correct; `[open-question]` Q5 auto vs manual |
| 5. Other providers via `content_source_link` (doc 17 §2) — "This source died; N other linked sources" affordance | provider-level death | doc 17 P1's whole point; the Cloud Screen (doc 18) owns the UI entry |
| 6. Terminal error state with the REAL reason (D-295 discipline: exception class + message, never "Something went wrong") | everything failed | `PlayerErrorOverlay` + `setSwitchingError` pattern (`WatchScreen.kt:851-856`) |

Download side: ladder = existing IOException→ReResolver (1 cap) → visible ERROR row
(D-296) → user re-picks. See §6.4; auto-next-mirror deferred (Q6).

### 8.3 User-facing errors — the visibility standard

D-295/D-296 (doc 14 §2.5): failures carry their real per-source reason and are never
silently dropped. Applications here: per-extractor exceptions during `loadLinks` surface
as "N hosts failed during resolution" in the picker footer (not just vanishing links);
a provider that yields zero links shows `ErrorLoadingException`-derived text (03 §2.4);
the download row shows the failing host label (`linkSource`) in its error message.

### 8.4 Rate-limit / backoff ethics

- **Be gentler than CS3's app**: our link resolution is on-demand (episode click), never
  speculative — we adopt CS3's *no-preload-on-details-open* discipline (08 §4.3 "no
  preload on details-page open — resolution starts at click") and skip their next-episode
  cache-warming at 80% for v1 (`[open-question]` Q11 — it doubles provider traffic; our
  20-min TTL cache already makes the common next-episode case cheap when the user
  binges within TTL).
- Segment/chunk fetches: ours already retry with backoff per chunk/segment
  (`ParallelHttpFetcher` per-chunk retry; CS3's own `resolveLinkSafe` = 3 tries/3 s,
  09 §5.5 #3 — same school).
- HTTP 429 from a provider: honor `Retry-After` when present, else 30 s cooldown per
  provider key (the pattern CS3 uses for OpenSubtitles 429s, 09 §3.3) — enforced in
  `CloudStreamRuntime.call` as a pre-check. `[design]`
- Never parallel-resolve the same provider for the same episode twice concurrently: the
  bridge dedups in-flight `(providerKey, episodeKey)` resolves (single SharedFlow
  multicasting to late collectors) — mirrors D-305 request-generation discipline (doc 16 §8).

---

## 9. What v1 skips (explicit non-goals)

| Cut | Reason | Revival trigger |
|---|---|---|
| DRM (`DrmExtractorLink`) | MPV has no Widevine/ClearKey path; CS3's DRM is DASH-only (09 §1.5) which MPV can't demux anyway | a Media3-based `IPlayer` variant (doc 09 §8's strategic note) |
| DASH playback + downloads | no DASH demuxer in libmpv; CS3 itself excludes DASH from downloads (05 §6.6, 08 §4.4) | same as above, or premux via a bundled ffmpeg step |
| TORRENT/MAGNET playback + downloads | TorrServer = Go gomobile AAR (09 §1.7); CS3 rejects them for downloads too (09 §5.4); 1/58 census usage (12 §9.2) | user demand + legal review |
| Live (`TvType.Live`) | VOD-shaped watch flow; CS3's live support itself marginal (09 §9) | dedicated live surface |
| Chromecast | we have none; MPV makes it hard (09 §8 Chromecast gap) | Media3 player variant |
| `ExtractorLinkPlayList` multi-slice | rare (05 §7.5); needs slice bookkeeping MPV-side | a provider we want ships it |
| `getVideoInterceptor` | 0 census usages (doc 16 §4.4); MPV has no interceptor concept | n/a |
| CS3 online-subtitle providers (OpenSubtitles et al.) | separate subsystem (09 §3.3); doc 09 §8 already flagged porting "auto-download best sub" as high-value — but it's a *feature*, not an integration dependency | post-v1 feature ticket |
| Audio-only background playback | no audio-only `ExtractorLinkType` exists (audio rides `audioTracks`); MPV background audio is a player-level concern, ecosystem-agnostic | player roadmap, not CS3 scope |
| Quality profiles / source-priority DataStore (CS3's) | our `AutoDownloadEngine` preferences already cover selection (doc 17 §10 cut table) | n/a |
| Preview thumbnails (CS3 `Mp4PreviewGenerator`/`M3u8PreviewGenerator`) | none today in our player (doc 09 §8 gap); orthogonal to CS3 links | player roadmap |

---

## 10. Verification plan

### 10.1 The honest constraint (cited twice over)

- **Sandbox**: `EMU` states under "What this buys you": "❌ **Video playback / cache-hit /
  download-from-extension testing** — the sandbox's datacenter IP cannot pass Cloudflare
  challenges… Those tests stay on the user's device." (`EMU` ❌ block + Part 5 "Cannot be
  tested here").
- **Harder still for CS3**: MPV is **arm-only** in our builds — `abiFilters` filters x86
  `.so` out (`AK/core/player-mpv-lib/…/PlayerMpvLib.kt:16-18`) — so the x86_64 emulator
  APK has **no libmpv at all**. Watch-screen entry on the emulator can only ever validate
  up to the handoff, not MPV itself. This is structural, not a Cloudflare artifact.

Therefore the plan splits: **emulator = everything up to (not including) `loadfile` +
download queue state machine; device = playback, cache, download bytes.**

### 10.2 Emulator-verifiable (with logcat + UI dumps, `EMU` Part 3/4 workflow)

1. Plugin install → provider listed (doc 16 §3 pipeline; assert via the extensions screen
   UI + `Anikuta:Data:Cloudstream`-style logs — tags per CORE_RULES §20).
2. Browse → details → episodes render (doc 18's surface; `EMU` 4.3-style repo injection
   for the CS3 repo URL — typing is unreliable).
3. **Episode click → link picker streams**: rows appear incrementally; assert the running
   count grows across two UI dumps 15–30 s apart (`EMU` 2.5 pacing) and the
   "N links hidden (unsupported)" footer computes correctly when the provider emits
   DASH/torrent links.
4. Timeout + cancel: pick a provider whose hosts are unreachable from the sandbox (the
   norm — `EMU` 4.5 reachability check with `getent hosts`) → assert the soft-failure
   path: emitted links kept, terminal error state with the real reason, no crash.
5. Download enqueue: picker → download → `download_queue` row appears (DATABASE.json /
   dashboard — doc 15 §9) with `source_key = "cloudstream:<name>"`, canonical episode key,
   polymorphic `resolve_context` JSON; QUEUED→DOWNLOADING→ERROR transitions visible
   (expected: CF-blocked bytes) — the *state machine* is what we're testing, not bytes.

### 10.3 Provider choice for testing (doc 12 census)

`[recommendation]` a two-provider fixture set:

- **DoramyWorld (CakesTwix)** — the minimal smoke: `loadLinks` is a direct-m3u8 one-liner
  (`M3u8Helper.generateM3u8(...).forEach(callback)`, doc 12 §3.4) — no extractors, fewest
  moving parts; exercises M3U8 typing, label/quality mapping, picker streaming.
- **Uakino (CakesTwix)** — the rich path: dual-path loadLinks (series AJAX vs movie
  iframe) with an in-provider player crack (doc 12 §2.6) AND the census's ONLY
  `subtitleCallback` user (UAK:328-336, doc 12 §9.2) — exercises subtitles, headers,
  multi-link fan-out.
- **AnimeJl (storm-ext)** — the stress path: the 57-extractor swarm (doc 12 §6.5),
  exercising the registry, mirror rewriting, and per-extractor failure tolerance. Optional
  third; use when testing §8's ladder.

Sandbox reachability must be probed first (`EMU` 4.5: `getent hosts <domain>` — CakesTwix
hosts are Ukrainian CDNs, not necessarily CF-walled; if all are blocked, emulator testing
stops at the picker-shell level and everything else moves to device).

### 10.4 Device-only assertions (user's checklist — extend
`DOCUMENTATION/download-device-testing-checklist.md`)

- `loadfile` logged with the folded headers (`WatchScreen.kt:695-698` logs URL + headers)
  → FILE_LOADED → frames; **assert Referer-required hosts play** (the §2.1 fold rule).
- MPV header comma-escaping round-trip (§2.1b): a UA header containing `", "` survives —
  check via a header-echoing debug host or the cache proxy's parsed-pair log.
- `sub-add sent OK` per CS3 subtitle (`PlayerObserver.kt:361`) + visible track in the
  subtitle sheet; ASS styling renders.
- M3U8 download produces growing `.ts`, completes, plays offline via `fd://`
  (`WatchScreen.kt:962-1024` offline path); VIDEO download via parallel fetcher.
- Kill-link ladder: play → wait for TTL expiry (or revoke via Charles/proxy 403) → step-3
  re-resolve fires → playback resumes at same cacheKey (§7.2).
- Verifier: pick an `extractorData` link (AllCalidad/DoramasFlix copy tokens blindly —
  doc 12 §9.2) → verifier logs start/cancel at the right lifecycle edges.

### 10.5 What "done" means

Emulator suite green (§10.2 items 1-5) + device checklist signed off on the two fixture
providers (§10.3) + one deliberately-broken mirror set (AnimeJl) showing the ladder and
error visibility. MPV performance is explicitly out of scope (arm-only, device-owned).

---

## 11. Open questions for the user

1. **[open-question] MPV list-option comma semantics** (§2.1b): confirm on-device that
   quoted/escaped commas in `http-header-fields` values survive libmpv's parser (and that
   today's unescaped default-UA path is/isn't silently truncating). Pick the escape
   syntax; if MPV mangles regardless, fall back to comma-stripping in values with a log.
2. **[open-question] Position-preserving link switch** (§2.3): the small
   `pendingSeekPosition` fix benefits BOTH ecosystems — ship it inside the CS3 PR or as a
   separate player PR first?
3. **[open-question] Encrypted-HLS caching** (§7.4): no-cache-until-key-proxy (recommended
   v1) vs implement transparent key proxying now?
4. **[open-question] Pre-play freshness check** (§7.2): add doc 17 §4.4's optional
   `resolved_url_expires_at` + skip-doomed-plays, or stay ladder-only (recommended v1)?
5. **[open-question] Auto next-mirror on playback error** (§8.2 step 4): auto-advance
   (CS3-style) or always manual "Try another link" (safer, recommended v1)?
6. **[open-question] Download auto-next-mirror** (§6.5): keep pinned re-resolve only
   (recommended) or opt-in CS3-style mirror fall-through for auto-downloads?
7. **[open-question] Download folder layout for CS3 content** (doc 17 §8.4 open question,
   restated because downloads are implemented here): flat `<title>/` vs TvType prefixes
   (`Movie/`, `TV Series/`)?
8. **[open-question] TTML subtitles** (§5.2): pass-through-and-fail-gracefully
   (recommended) or add a TTML→SRT/VTT converter to the subtitle engine?
9. **[open-question] Headered audio tracks** (§5.4): is URL-based `audio-add` enough, or
   should `AudioFile.headers` get the temp-file treatment when set?
10. **[open-question] DASH prevalence**: worth a one-off scan of the 80-plugin corpus
    (`research/phisher-builds`, doc 16 §1.4) counting `DASH`-typed links to size the
    future DASH gap before anyone asks for it?
11. **[open-question] Next-episode link pre-warm** (§8.4): CS3 warms the next episode's
    links at 80% watched (09 §4.2). Our 20-min TTL cache covers binges; add pre-warm
    (doubles provider traffic) or not?
12. **[open-question] Verifier ownership**: `CloudStreamVerifier` in `:data:cloudstream`
    (recommended — plugin-API-adjacent) vs a neutral hook interface in `:core:download`/
    player so aniyomi could later grow an analog?

---

## 12. Verification status (this doc's own evidence)

- **Read fully (docs)**: 08 (963 L), 09 (995 L); targeted sections of 03 (via 08 §1.4
  quotes), 05 (§6.5-§6.6, §7.1-§7.5), 12 (§1, §6.5, §9.1-§9.3, §10), 15 (§4 headers,
  §8 cross-refs via 17), 16 (§0, §4, §8), 17 (§0-§4, §6, §8-§9, §11).
- **Read in OUR source (fresh, cited with line numbers)**:
  `feature/watch/impl/…/WatchScreen.kt` (85-204 structure, 440-734 loadfile/headers/
  retry/fd://, 840-920 retry+quality+subtitle switch, 2139-2157 buildCacheId),
  `feature/watch/api/…/WatchKey.kt` (full), `core/player/PlayerObserver.kt`
  (sub-add/audio-add mechanism), `core/player/subtitles/SubtitleEngine.kt` (full),
  `core/player-mpv-lib/…/PlayerMpvLib.kt` (full — ABI note),
  `core/video-resolver/` (ResolverTypes, ResolverState, ResolvedVideosRegistry full;
  VideoResolver via doc 08 §8), `app/…/download/` (DownloadOrchestrator, ReResolver,
  ResolveContext full), `core/download/` (HttpDownloader head+routes, HlsDownloader
  head+AES+variant logic), `core/playback-cache/` (PlaybackCacheModels full,
  PlaybackCacheManager playbackUrlFor/HLS/eviction, CacheProxyServer via manager),
  `feature/download/DownloadVideoPickerSheet.kt` (full).
- **Verified absences**: no `EXT-X-KEY`/AES handling in `core/playback-cache` (grep);
  no DASH/torrent handling in our download stack (implicit — routed set is {http, m3u8});
  `directUrl` proxy-churn logic read at `DownloadOrchestrator.kt:198-199`.
- **Not read this batch** (deferred, cited from prior docs): `ParallelHttpFetcher`/
  `SingleConnectionFetcher` internals (doc 09 §8 comparison sufficient), `DownloadQueue`/
  `DownloadService` internals (doc 15 §4 + doc 17 §8.1), aniyomi `VideoResolver` internals
  (doc 08 §8, doc 14 §7.3).
- Cross-doc consistency: the extended `SourceVideo`, `CloudStreamResolveContext`,
  `source_key` sweep, §4.3 re-resolve tiers, §8.1 entity mapping, §8.4 naming are quoted
  from doc 16 §4 and doc 17 §4/§6/§8 and are NOT re-decided here — this doc only adds the
  runtime mechanics (flow adaptation, verifier hook, ladder, cache keys' runtime shape,
  HLS resume answer).

---
## ✔ B5-b Verification Note (2026-08-29)
Checked: 15 claims sampled → 15 verified, 0 corrected, 0 flagged-stale. Consistency: ok.
Corrections: none.
High-value targets re-verified in our code: **MPV http-header-fields** — `MPVLib.setOptionString("http-header-fields", headers)` at WatchScreen.kt:586 (and :694 inside the D-199 "ALWAYS set … even for localhost proxy URLs" block :686-694; also :843), headers-before-`loadfile` ordering (loadfile at :731); **HlsDownloader AES-128/parallel** — head comment :28-55 documents parallel mode + in-memory AES-128 (legacy mode hard-rejects encryption), `parseEncryptionKey` :310-341 rejects rotating keys + non-AES-128 (SAMPLE-AES) exactly as §6.1 states, `pickFirstVariant` :574-590 first-variant policy, PNG-header strip before decryption, append-state sidecar; playback-cache — no `#EXT-X-KEY`/AES handling anywhere in `core/playback-cache` (grep-confirmed absence, §7.4), PlaybackCacheModels.kt PlaybackVideoId :25+ / videoTitle `"$server|$audio|$quality|$urlHash"` :50 / D-207 comma-safe regex parser :158-164, PlaybackCacheManager descriptors[cacheKey]=PlayDescriptor :172 + upstreamUrlFor :188-190; PlayerMpvLib.kt:16-18 arm-only abiFilters (emulator no-libmpv claim holds); ResolvedVideosRegistry.kt:23-26 "NOT automatically cleared"; ReResolver 1-re-resolve cap (:21-22 area); SubtitleEngine.kt:14-31 sub-add-local-path rationale; ResolverTypes.kt ResolverServer :29+ / ResolverSubtitleTrack(url, lang) :76-79; WatchScreen hasResumed-only-on-initial-load :449-462 (the §2.3 gap is real — quality switch does not re-seek), cachedUrl :546-553, auto-retry 1.5 s :575-580, buildCacheId :2139-2157; WatchKey.kt:28-33 videoHeaders wire format + :111-118 2-field-limit subtitle parse (the §3.4 additive-3rd-field design is compatible).
