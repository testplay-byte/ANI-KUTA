# CloudStream V2 — The Playback Port Plan

> **Task 52 (round 12).** The plan for wiring CloudStream video playback: episode
> stream resolution, quality selection, subtitles, and the watch page — as a
> **fully separate playback stack** that mirrors the watch UX without touching a
> single aniyomi playback file.
>
> **Branch:** `streaming/CLOUDSTREAM-V2` (continues from v0.3.0 @ baab1c64)
> **Research:** R12-A (upstream CloudStream player pipeline — see worklog) +
> R12-B (local watch-stack seam map — see worklog)
> **Scope:** CS link resolution + CS player + CS watch page + details seam + CS
> playback logging. **NOT in scope:** CS downloads, sub/dub row combination,
> extra plugin metadata (covers), removing the debug tooling (user: keep until
> CS playback is verified, then remove), the 428-era PlaybackCache fixes (CS
> bypasses the proxy entirely — per-link OkHttp DataSource, the upstream pattern).

---

## 1. The one-sentence architecture

CloudStream episodes resolve through **`data/cloudstream`'s own loadLinks
orchestration** and play on a **dedicated Media3 ExoPlayer screen**
(`feature:cs-watch`) fed by a **dedicated engine module** (`core:cs-player`) —
the same layering CloudStream itself uses, sharing only the provider-agnostic
`WatchProgressStore` with the aniyomi side.

### Why ExoPlayer and not MPV (the round-10 lesson)

The reference branch forced CS links into the aniyomi MPV pipeline and it broke
exactly where the two ecosystems differ: MPV cannot play DASH manifests (so 5
`.mpd` streams per episode were hidden), the PlaybackCache proxy mangled CS
header/referer/redirect flows (HTTP 428-class failures), and the callback-based
`loadLinks` API had to be faked into a synchronous `getVideoList` shape.
CloudStream's own player is Media3 ExoPlayer — DASH, HLS, sidecar subtitles,
per-request headers and track selection are native there. We follow the
upstream architecture instead of fighting it.

### Module map (all NEW, aniyomi modules untouched)

| Module | Responsibility | Depends on |
|---|---|---|
| `core:cs-player` (NEW) | Media3 engine host: link → MediaSource, per-link OkHttp `DataSource.Factory` (UA + referer + headers + provider interceptor), sidecar subtitle sources, external audio merge, track selection, error mapping, progress ticker. App-side `CsVideoLink`/`CsSubtitle` models — **zero plugin classes leak** into the player. | media3, okhttp, core:common |
| `data/cloudstream` `playback/` (NEW pkg) | `CloudstreamLinkResolver`: provider lookup by name, `loadLinks` orchestration, URL dedup, progressive Flow, first-link timeout, 20-min link cache, torrent/DRM filtering, `Anikuta:CS:Resolver` logging. `CsResolvedLinksRegistry` (in-memory key → resolved set, mirrors `ResolvedVideosRegistry`). | core:cloudstream-api (already a dep), core:common |
| `feature:cs-watch:api` (NEW) | `CsWatchKey` (Nav3 serializable — provider, episode data, episode list, mainId, resume info). | core:navigation-api |
| `feature:cs-watch:impl` (NEW) | `CsWatchScreen`: Compose player surface + controls (design-language parity), links/quality sheet (type badges, live-updating, switch-preserving-position), subtitle sheet (sidecar + embedded tracks), episode sheet (switch → re-resolve), error fallback (auto next link), progress save/resume. | core:cs-player, data:cloudstream, core:watch-progress, core:designsystem, core:common, feature:cs-watch:api |
| `app` (seams only) | MainActivity `CsWatchKey` nav branch + `onNavigateToCsWatch` callback wiring; gradle includes; Koin module; version bump. | everything above |

### The playback pipeline (ported from upstream, adapted)

```
Episode tap (DetailsScreen, CS-bridged source)
  → onNavigateToCsWatch(CsWatchKey(providerName, epData, epNum, epTitle, epList, mainId, animeTitle, thumbUrl))
  → CsWatchScreen
      → CloudstreamLinkResolver.resolve(providerName, epData)   [progressive Flow]
          → provider.loadLinks(data, isCasting=false, subCb, linkCb)
          → dedup by URL · filter TORRENT/MAGNET (+DRM) · map to CsVideoLink/CsSubtitle
          → first link arrives → engine.start(link)   [collection continues in background]
      → CsPlayerEngine(link, subs)
          → MediaItem(url, mime: VIDEO→video/mp4 · M3U8→application/x-mpegURL · DASH→application/dash+xml)
          → OkHttpDataSource.Factory(app.baseClient + interceptor).setUserAgent(ua).setDefaultRequestProperties(referer + headers)
          → sidecar subs: SubtitleConfiguration + SingleSampleMediaSource (mime by extension)
          → external audio: merged MediaSources
          → DefaultTrackSelector (quality cap pref) · DefaultMediaSourceFactory
          → ExoPlayer.prepare() · resume seek · progress ticker → WatchProgressStore
      → player error → mark link bad → auto-advance to next link (position preserved)
      → exhausted → error overlay (retry / re-resolve / report counts incl. hidden)
```

## 2. Architecture invariants (this round)

1. **Aniyomi is byte-untouched.** `VideoResolver`, `WatchScreen`, `WatchKey`,
   `core:player`, `core:playback-cache`, `feature:watch` — zero diff. Verified by
   the R12-REVIEW adversarial audit before release (same md5 discipline as R11).
2. **No plugin classes in the player.** `ExtractorLink`/`SubtitleFile` are mapped
   to app-side `CsVideoLink`/`CsSubtitle` at the resolver boundary; `core:cs-player`
   never imports `com.lagradost.*`.
3. **DASH is a first-class citizen** — never hidden. TORRENT/MAGNET are hidden +
   counted + logged (the honest-hidden pattern); DRM links surface an
   "unsupported (DRM)" row.
4. **No proxy.** CS streams never touch PlaybackCache; headers/referer/UA ride the
   per-link OkHttp DataSource (upstream pattern — redirects follow natively).
5. **One progress store.** CS watch progress rides the provider-agnostic
   `WatchProgressStore` (`episodeKey = mainId|paddedEp`) — history, continue
   watching and resume work with zero schema changes.
6. **The debug tooling stays.** The user explicitly said keep the debug loop /
   console tooling until CS playback is verified; removal is a later round.
7. **CI is the compiler.** Every phase = commit → push → build-apk.yml green
   (incl. unit tests) before the next phase starts.

## 3. The phase plan (~40 steps)

**Phase A — Gradle/CI groundwork (5 steps)**
- A1. `libs.versions.toml`: pin `androidx-media3 = "1.9.3"` (upstream-proven) +
  artifacts `media3-exoplayer`, `media3-exoplayer-hls`, `media3-exoplayer-dash`,
  `media3-datasource-okhttp`, `media3-ui`, `media3-common`.
- A2. `settings.gradle.kts`: include `:core:cs-player`, `:feature:cs-watch:api`,
  `:feature:cs-watch:impl`.
- A3. Module skeletons (build.gradle.kts + namespace + manifest if needed) so the
  build stays green with empty packages.
- A4. `AndroidConfig`: versionCode 65, versionName `0.4.0` (minor-bump precedent
  for headline features; playback completes the CS system).
- A5. CI: add `:core:cs-player:testDebugUnitTest` to the unit-test gate step.
  → commit `feat(task52/phase-a)` → CI green.

**Phase B — `core:cs-player` engine (7 steps)**
- B1. Models: `CsVideoLink` (name, url, quality Int, type enum VIDEO/M3U8/DASH,
  referer, headers, origin/source label), `CsSubtitle` (name, url, headers,
  mime, languageTag), `CsAudioTrack`; quality-label helper (`Qualities`-style
  formatting duplicated app-side — no plugin import).
- B2. `CsMediaTypes`: mime mapping per type (upstream map) + subtitle mime by
  extension (vtt/srt/xml/ttml + default) + `//`-URL fix. Unit tests.
- B3. `CsHttpDataSourceFactory`: OkHttp factory builder — UA from link headers or
  default app UA; `setDefaultRequestProperties(referer + headers)`; optional
  provider `Interceptor`; optional sidecar-sub factory (per-sub headers).
- B4. `CsPlayerEngine`: ExoPlayer instance lifecycle; `start(link, subs, audio,
  startPositionMs)`; MediaItem + media-source assembly (video + sidecar subs via
  SubtitleConfiguration/SingleSampleMediaSource + merged external audio);
  DefaultTrackSelector with quality cap; live-stream position nuance
  (`TIME_UNSET` for M3U8/DASH at 0).
- B5. Engine state: StateFlow (isPlaying, position, duration, buffered,
  bufferState, videoSize, error) + progress ticker callback for the store.
- B6. Track APIs: enumerate video formats (HLS/DASH variants) + text tracks;
  `setPreferredVideoHeight(h)` / `setMaxVideoHeight` selection; subtitle
  activation by track id (sidecar + embedded).
- B7. Error mapping: ExoPlayer `PlaybackException` → `CsPlaybackError`
  (classifying http 403/4xx/timeout/unplayable) with the upstream diagnostic
  fields (url, headers, referer, position, duration) in the log line. Unit
  tests for the pure parts. → commit `feat(task52/phase-b)` → CI green.

**Phase C — `data/cloudstream` playback resolver (6 steps)**
- C1. `CsResolvedLinksRegistry` (in-memory, key → links+subs+meta, LRU/TTL).
- C2. `CloudstreamLinkResolver.resolve(providerName, data): Flow<CsResolveEvent>`
  — progressive (LinksUpdated/SubtitlesUpdated/Done/Error), provider lookup via
  `APIHolder.getApiFromNameNull` (same live-provider discipline as the bridge).
- C3. Callback discipline: URL-dedup (concurrent set), first-link timeout
  (30 s → honest timeout error with count of what DID arrive), Cancellation +
  CloudflareBlockedException pass-through, plugin Throwable → descriptive ISE
  (the bridge `guard()` pattern).
- C4. Link filtering: TORRENT/MAGNET hidden+counted; DRM flagged unsupported;
  blank-URL guard; per-link origin label = provider name + link name.
- C5. Link cache: 20-min TTL keyed (providerName, data); explicit invalidate on
  error-exhausted (upstream forceClearCache pattern).
- C6. Logging pass: `Anikuta:CS:Resolver` — every step (provider resolve, Nth
  link, sub, dedup skip, hidden torrent, timeout, cache hit/miss) with context.
  Unit tests (fake provider). → commit `feat(task52/phase-c)` → CI green.

**Phase D — `feature:cs-watch` screen core (8 steps)**
- D1. `CsWatchKey` (api module) — serializable, mirrors the aniyomi key shape:
  providerName, animeTitle, thumbnailUrl, episodeData (url), episodeNumber,
  episodeTitle, episodeListSerialized (`\u001F` lines), mainId, sourceId,
  startPosition.
- D2. Screen shell: full-screen surface, `AndroidView(PlayerView, useController
  = false)` + Compose control layer; keep-screen-on, immersive mode, back
  handling (the §5 player lifecycle scaffolding list — load-bearing).
- D3. `CsWatchViewModel`: holds engine + resolver state; entry flow = resume
  lookup (`WatchProgressStore`) → resolver flow → first link → engine.start;
  background collection continues into the links sheet.
- D4. Core controls: play/pause, seekbar (position/duration/buffered), title row
  (anime + ep), back, auto-hide + tap-to-toggle, loading overlay (resolving
  state with provider + episode context), error overlay (retry / re-resolve /
  links list / hidden counts).
- D5. Progress saving: ticker → `WatchProgressStore.save(mainId|paddedEp, …)` —
  identical semantics to the aniyomi screen (resume, auto-mark, suppression).
- D6. Error fallback: engine error → resolver.markBad(link) → next usable link
  with position preserved; exhausted → error overlay with full context.
- D7. Link switching (from the sheet, phase E) groundwork: engine.restart(link,
  keep position) — same MediaSource rebuild as start.
- D8. Logging pass: `Anikuta:CS:Player` (engine events, states, errors) +
  `Anikuta:CS:Watch` (screen lifecycle, resume, progress saves). → commit
  `feat(task52/phase-d)` → CI green.

**Phase E — sheets: links / subtitles / episodes (5 steps)**
- E1. Links & quality sheet: live-updating list (name + quality label + type
  badge VIDEO/HLS/DASH + unsupported markers), current-link highlight,
  switch-preserving-position, hidden-torrent count footer, long-press copy URL.
- E2. Quality rows for the CURRENT link: enumerate engine video tracks (HLS/DASH
  variants) + "Auto"; selection via track parameters.
- E3. Subtitle sheet: Off + sidecar subs (from resolver) + embedded text tracks
  (from engine); activation + offset basics; `Anikuta:CS:Subs` logging.
- E4. Episodes sheet: episode list (from `episodeListSerialized`), current
  highlight, switch → re-resolve (fresh resolver flow, progress lookup).
- E5. Next/prev episode affordances (gestures/buttons mirroring the aniyomi
  screen) + auto-advance on episode end. → commit `feat(task52/phase-e)` → CI
  green.

**Phase F — the details + app seams (5 steps)**
- F1. `DetailsViewModel`: additive `isLinkedSourceCloudStream` helper; keep the
  resolver-state short-circuit as a safety net (defense in depth) but reroute
  the tap before it.
- F2. `DetailsScreen`: episode-tap branch — CS source → `onNavigateToCsWatch(...)`
  building `CsWatchKey` (episode list + metadata + mainId, mirroring the aniyomi
  nav block); CS download taps → honest "CS downloads arrive later" message.
- F3. MainActivity: `onNavigateToCsWatch` callback plumbing (both nav sites),
  `is CsWatchKey -> CsWatchScreen(...)` branch, back-stack behavior parity.
- F4. Verify continue-watching / history entry path for CS content (details
  autoplay → episode tap → CS branch) — fix if the auto-play flow assumes
  ResolverState.
- F5. Koin wiring (`csPlayerModule`, resolver single) + app build.gradle deps.
  → commit `feat(task52/phase-f)` → CI green.

**Phase G — logging + console integration (3 steps)**
- G1. Full `Anikuta:CS:*` audit: every decision point logs with context; the
  single Android Studio filter (§5 below) verified against every emitted line.
- G2. Console tool: add the CS-playback category/label to the console screen's
  filter set (the R11 Phase-I tool).
- G3. Log hygiene: no secrets/URL keys in logs beyond what §20 allows; level
  discipline (VERBOSE tracing behind Logger levels). → commit
  `feat(task52/phase-g)` → CI green.

**Phase H — documentation (4 steps)**
- H1. `DOCUMENTATION/cloudstream-v2/02-PLAYBACK.md` (this plan's as-built
  companion: pipeline, module contracts, the logcat filter, limits).
- H2. AGENT-CONTEXT updates: decisions (D-374+), progress, SESSION, changelog,
  lessons — same-session discipline (CORE_RULES §26).
- H3. The user-requested **AI-agent long-task method doc**
  (`AGENT-CONTEXT/skills/long-task-execution.md`): the research→plan→phase→CI→
  review→release workflow that produced R11/R12, written so any agent can
  replicate it.
- H4. `01-ARCHITECTURE.md` cross-links (playback layer added to the map). →
  commit `docs(task52/phase-h)`.

**Phase I — adversarial review + release (5 steps)**
- I1. R12-REVIEW subagent: aniyomi byte-safety audit (the §1 invariant), seam
  sweep, playback correctness vs upstream, scope walk, release readiness.
- I2. Fix findings; re-verify CI.
- I3. Tag `v0.4.0`; verify the Release workflow green + artifact renamed with a
  descriptive body (R11 pattern).
- I4. ntfy.sh TASKISDONE notification.
- I5. Device test checklist delivered with the closing message (CORE_RULES §31).

## 4. Known limits (honest, documented)

- **Torrent/magnet links**: hidden + counted (upstream plays them via a torrent
  server we are not porting).
- **DRM links**: surfaced as unsupported rows (upstream clearkey/widevine flow
  not ported).
- **Subtitle charset**: streamed sidecars assume UTF-8/UTF-8-BOM (upstream
  parity; charset conversion only exists in their download path).
- **AI HLS quality preference**: the auto-pick orders by quality Int; a smarter
  AI-driven preference remains future work.
- **CS downloads**: not wired (later round, per the user).

## 5. The one-filter logcat recipe (delivered at close)

```
tag:Anikuta:CS:Resolver | tag:Anikuta:CS:Player | tag:Anikuta:CS:Subs | tag:Anikuta:CS:Watch
```

Pasteable straight into Android Studio's Logcat filter bar — it shows the ENTIRE
CS playback pipeline (resolution, engine, subtitles, screen) with nothing else.
