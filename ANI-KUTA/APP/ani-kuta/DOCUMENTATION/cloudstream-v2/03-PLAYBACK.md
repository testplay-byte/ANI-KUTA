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

Paste into Android Studio's Logcat filter bar (or use `adb logcat -v time |
 grep -E "Anikuta:CS:"`):

```
tag:Anikuta:CS:Resolver | tag:Anikuta:CS:Player | tag:Anikuta:CS:Subs | tag:Anikuta:CS:Watch | tag:Anikuta:CS:M3u8 | tag:Anikuta:CS:Sheet | tag:Anikuta:CS:WebView
```

What each tag answers:
- **Anikuta:CS:Resolver** — "why didn't it resolve?" Provider lookup, every
  link/sub as it arrives (with type/referer/HEADER VALUES truncated/quality),
  dedup skips, hidden torrent counts, the honored total timeout at START,
  TOTAL TIMEOUT / Cloudflare / crash events with stacks, cache hits, provider
  crashes.
- **Anikuta:CS:M3u8** — "which playlist died?" (Task 53) Every
  `M3u8Helper.generateM3u8` fetch: ok + variant count, or the failure with
  **http status + first 90 bytes of the body** — a CDN 403 is now obvious
  instead of silently zeroing a provider's links.
- **Anikuta:CS:Player** — "why didn't it play?" `start[profile=upstream|clean]`
  logs the FULL outgoing request profile (UA + header values truncated),
  buffer states, the READY track census (video/audio/text group counts),
  track picks, CLEAN RETRY decisions, and the upstream-format error line:
  `playerError: url=…, type=…, code=…, http=…, referer=…, headers=…,
  position=…, duration=…, linkName=…`.
- **Anikuta:CS:Subs** — "what about subtitles?" Sidecar attachment, per-sub
  mime, drops, selection on/off, reattach flow.
- **Anikuta:CS:Watch** — the screen/ViewModel layer: entry (resolve/SEEDED),
  generation bumps, play requests + the generation-lock ACCEPT/REJECT verdicts
  (Task 53 / RC-3), engine resets, link fallback decisions with per-link
  failure reasons, episode switches, progress saves.
- **Anikuta:CS:Sheet** — the resolve sheet (Task 53): open, progressive
  arrivals, remembered-server / single-link auto-selects, the pick, cancel.
- **Anikuta:CS:WebView** — the (still stubbed) WebViewResolver degrading
  gracefully instead of throwing.

The in-app console (Settings → Console logs) has a matching **"CS Playback"**
chip (prefix `Anikuta:CS:`) for device-side triage without Android Studio.

### The clean-capture recipe (for device reports)

```
adb logcat -c && adb logcat -v time -d > cs-playback.log 2>&1; grep -E "Anikuta:CS:" cs-playback.log
```

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
- **WebViewResolver** (Task 53): still a stub — but it now RETURNS
  `null to emptyList()` (upstream's failure shape) instead of throwing, so
  providers that call it degrade to their fallback path. A real
  WebView-backed implementation is future work (doc 04 §6).

## 5. Round-13 behavioral deltas (Task 53 — the v0.4.0 device findings)

Every delta is the fix of a root cause with a source/empirical proof chain
(the full register lives in doc 04 §1):

- **m3u8 fetch = headers-only** (upstream parity): the playlist request no
  longer passes `referer = the stream URL` (nicehttp lets the referer param
  REPLACE the caller's Referer header — CDNs like cdn.kryntal.top then 403,
  and the plugin's runCatching swallowed it into 0 links + 0 subs).
- **Total loadLinks budget**: `withTimeout(provider.loadLinksTimeoutMs ?: 120s,
  clamped 5–480s)` around the provider call (upstream APIRepository parity);
  the 30 s first-link watchdog stays.
- **Player request profiles**: attempt 1 = upstream semantics (UA from the
  link's headers, else the desktop-Chrome default — byte-parity with
  upstream's USER_AGENT); on a 4xx at open time (never reached READY) the
  engine does ONE automatic **clean retry** (client-default UA, referer
  dropped) — empirically resurrectes CDNs that reject browser UAs (hcdn3:
  browser UA → 428, referer → 429, clean → 206).
- **The generation lock**: play requests carry `playGeneration`; the screen's
  engine trigger reads the LIVE StateFlow and only touches the engine while
  `playGeneration == resolveGeneration` — the collectAsState one-dispatch lag
  can no longer replay the previous episode's link on a fresh engine. Every
  new resolution also hard-resets the engine (`engineResetTick`).
- **The AnymeX-pattern entry**: episode taps on the details page open
  `CsResolveSheet` (a bottom sheet over the still-visible details page) with
  progressive source rows + CC badges; selection seeds the watch ViewModel
  with the FULL pre-resolved list (instant playback, no re-resolve);
  dismissal cancels the resolution. A per-anime remembered server
  (CsSourceMemory) auto-selects on arrival; single-link results auto-select.
  Early picks hand off a partial list — a quiet same-generation **top-up
  walk** (R13-REVIEW F4) then saturates the Sources sheet + the fallback
  chain append-only without ever touching playback.
- **Sheets**: "Streams" → **Sources** (quality-desc sort, subtitle-track
  count, type badges, failed markers); the subs sheet becomes **Audio &
  Subtitles** when the stream exposes >1 embedded audio track (DASH
  multi-audio).
- **All-links-exhausted messages** now aggregate the per-link failure reasons
  ("All 3 stream(s) failed — HTTP 428, HTTP 403").

## 6. Round-14 UI-parity deltas (Task 54 — the v0.4.1 device feedback)

Streams resolved correctly on v0.4.1 (the round-13 fixes held); the gap was
VISUAL. Round 14 makes the CS stack render in the aniyomi stack's exact
design language (doc 05) with ZERO aniyomi code imports — replicated design
tokens (RobotoFamily, MaterialTheme, identical paddings/typography/shapes):

- **Resolve sheet = ResolverSheet parity**: server accordion (one open at a
  time), quality chips with the PlayArrow prefix, "Episode N" 18sp ExtraBold
  header + circle close, RobotoFamily throughout, the aniyomi
  Loading/Error/Empty card wording. CS links group by `link.name` for the
  server cards; grouping is presentation-only (the seed hand-off stays flat).
- **The CS watch screen is now a real two-mode watch PAGE**:
  - MINIMIZED (portrait, default) — pill top bar (collapses on scroll), 16:9
    rounded player, "Currently playing episode N" + title + star-rating bar +
    quality/sub-dub pills + synopsis (show more/less), Episodes header +
    lazy episode rows (thumbnail w/ EP tag or ep-number box, current-row
    highlight with primary border).
  - FULLSCREEN (landscape, edge-to-edge) — lock, title + EP/quality pills,
    frosted action row (subs/quality/audio/more), -10s/play/+10s, canvas
    seekbar w/ buffer-ahead + scrub tooltip, speed sheet (presets + slider).
  - Window choreography copies the aniyomi screen (portrait minimized /
    sensor-landscape fullscreen / restore on dispose; minimized never flips
    setDecorFitsSystemWindows — the double-top-padding lesson).
  - RESOLVING/FAILED/NO_LINKS render INSIDE the 16:9 player box — the page
    (description + episodes) stays visible while a new episode resolves.
- **Per-episode metadata pipeline** (the "description and details show
  properly" requirement): `CsWatchKey.episodeMetadataSerialized` (SAME wire
  format as the aniyomi WatchKey field) built at the DetailsScreen CS
  click-site — the bridge already maps CS `Episode.description/posterUrl`
  onto SEpisode `summary/preview_url`, so CS rows get real titles, thumbs,
  air dates, descriptions, and sub/dub labels.
- **Player sheets in the aniyomi sheet language**: Qualities-and-Servers
  accordion (selected chip highlighted, failed chips struck + reason footer,
  long-press copy), Subtitles TrackRows (Off-first, check marks, provider/
  embedded/needs-reload sections + embedded-audio section), Episodes sheet
  with current highlight, Speed sheet (presets + custom slider).
- **The single PlayerView re-parents** between the minimized box and the
  fullscreen surface (the aniyomi PlayerSurface pattern) — no surface
  teardown on mode switches.

## 7. Test coverage (CI-gated, `:core:cs-player` + `:data:cloudstream`)

- `CsMediaTypesTest` — the upstream mime maps (video + subtitle + URL fix).
- `CsQualityTest` — quality int ↔ label formatting (the ABI `Qualities` semantics).
- `CsVideoLinkHeadersTest` — referer merge / UA extraction / display labels.
- `CloudstreamLinkResolverTest` — 9 end-to-end locks with a fake provider
  registered into APIHolder: progressive snapshots, dedup, torrent hiding,
  subtitle unique-ifying, DASH first-class, missing provider, plugin crash →
  descriptive failure, first-link timeout, cache hit without provider.
