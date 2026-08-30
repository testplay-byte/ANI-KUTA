# 04 — CS Playback Fixes Plan (Round 13)

> Status: EXECUTING. Research-driven plan for the v0.4.0 device-test findings.
> Research artifacts: R13-A (upstream CloudStream, /home/z/research/R13-A-cloudstream-upstream-findings.md),
> R13-B (AnymeX, /home/z/research/R13-B-anymex-findings.md), plus live empirical CDN testing
> (this session) and raw .cs3 decompilation (AniKoto, MovieBoxProvider — jadx, /home/z/research/decompiled).

## §0 The device-test findings (v0.4.0, user logcat)

| # | Symptom | Log evidence |
|---|---------|--------------|
| F-1 | AniKoto episode resolves 0 links, 0 subs after ~19s, no error | `resolve: DONE 'AniKoto' providerReturned=false links=0 subs=0 in 19120ms` |
| F-2 | MovieBox esla-720p mp4 fails HTTP 428, auto-advance plays the DASH | `playerError: … code=ERROR_CODE_IO_BAD_HTTP_STATUS, http=428 … headers=[Referer]` |
| F-3 | Tapping a new episode replays the PREVIOUS show's link; stale provider/link in logs | `open: 'Sakamoto Days' EP 5.0 …` immediately followed by `switchLink → MovieBox (Hindi Audio) 1080p` (a Loki URL) |
| F-4 | Subtitle sheet empty; sources vs tracks vs quality not separated in player UI | user report |
| F-5 | Fullscreen "Resolving streams" page instead of a bottom sheet | user report (wants the AnymeX pattern) |
| F-6 | Logging too minimal to diagnose any of the above | user report |

## §1 Root-cause register (all source- or empirically verified)

### RC-1 — AniKoto 0 links: vendored M3u8Helper overrides the plugin's Referer
- AniKoto's `extractMegaPlayUrl` (decompiled): embed page → `#megaplay-player` → `getSources` → `M3u8Helper.generateM3u8(source, m3u8, referer, headers=map10)` where map10 contains `Referer: https://megaplay.buzz/…` → links + subtitle tracks are emitted ONLY AFTER generateM3u8 returns.
- Upstream `M3u8Helper2.m3u8Generation` (M3u8Helper.kt:130): `app.get(m3u8.streamUrl, headers = m3u8.headers, verify = false)` — **no referer parameter**.
- Our vendored copy: `app.get(m3u8.streamUrl, referer = m3u8.streamUrl, headers = m3u8.headers)` — an invented `referer` param.
- Nicehttp (upstream AND ours): the `referer` param **replaces** the headers map's Referer (`headers + cookieMap + refererMap`).
- Empirical (this session): `cdn.kryntal.top/…/master.m3u8` returns **403 Cloudflare** unless the request carries `Referer: https://megaplay.buzz/` (200 with it, 403 without — any UA).
- Chain: our referer param replaces the plugin's megaplay Referer → 403 HTML → `ErrorLoadingException("Not m3u8")` → the plugin's `runCatching` swallows it → links AND tracks never emitted → `providerReturned=false, links=0, subs=0` after the full ~19-request server walk (~19s). Exactly the user's log.
- **FIX A1**: `m3u8Generation` → exact upstream call (headers only, `verify = false`). Plus loud failure logging (status code + body preview) under `Anikuta:CS:M3u8`.

### RC-2 — HTTP 428 class: player DataSource UA/referer policy
- Empirical (hcdn3.hakunaymatata.com, MovieBox CDN): **any browser UA → 428** (Chrome mobile/desktop, Firefox), **any Referer header → 429**, plain okhttp/curl UA + no referer → **206**.
- Our `CsHttpDataSourceFactory.forLink` forces `setUserAgent(link.userAgent ?: CsPlayerDefaults.USER_AGENT)` — a Mobile-Chrome default → deterministic 428 on this CDN class.
- Upstream `CS3IPlayer.createVideoSource`: UA = link headers' UA **else the Chrome/149 desktop default**; referer only when non-blank. (Upstream would also 428 on hcdn3 — the esla link is broken upstream too; our auto-advance handled it.)
- **FIX B1**: attempt 1 = upstream-exact semantics (UA from link headers else desktop Chrome/149 — the vendored `USER_AGENT` constant, injected as a string to keep `core:cs-player` lagradost-free).
- **FIX B2 (ANI-KUTA extension over upstream, empirically motivated)**: on `ERROR_CODE_IO_BAD_HTTP_STATUS` with HTTP 4xx at open, ONE automatic retry with a "clean" request profile — no UA override (OkHttp default) + referer dropped, other link headers kept. hcdn3 goes 428→206. Heavily logged, position-preserving.

### RC-3 — Stale-link playback on new episode (collectAsState one-dispatch lag)
- `CsWatchScreen`'s play trigger is `LaunchedEffect(uiState.playRequestId) { … uiState.playLink … }` where `uiState` is the **collectAsState State object**, which lags the StateFlow by one dispatch.
- On a new episode, `LaunchedEffect(key) { viewModel.initialize(key) }` (an earlier effect in the same apply pass) resets the StateFlow synchronously; the play-trigger coroutine then reads the STILL-OLD state object (playRequestId>0, playLink = previous episode's link, playKeepPosition=true) → `engine.switchLink(OLD LINK)` on the fresh engine. The collector resumes only afterwards.
- Matches log F-3 exactly (stale Loki S2E1 DASH started 1 ms after the reset).
- **FIX C1**: the trigger reads `viewModel.uiState.value` (authoritative StateFlow) and validates a **generation token**: every `startResolution` bumps `resolveGeneration`; every play request stamps `playGeneration`; the engine is touched only when `playGeneration == current resolveGeneration` and `playRequestId > 0`.
- **FIX C2**: `engine.reset()` (stop + clear media items + clear currentLinkUrl) whenever a new episode's resolution starts — driven by an `engineResetTick` in the UiState. Structurally impossible for stale content to keep playing under the resolving overlay.

### RC-4 — Resolver timeout policy drift
- Upstream `APIRepository.loadLinks`: `withTimeout(api.loadLinksTimeoutMs ?: 120s)` (clamped 5–480s), `catch(Throwable) → false`; the returned Boolean is ignored — UX is driven by the empty link list.
- Ours: 30s first-link watchdog only. **FIX A2**: honor `loadLinksTimeoutMs` (field already in the vendored MainAPI) with the same clamp; TimeoutCancellation → Failed(timedOut=true) keeping partial links; watchdog unchanged.

### RC-5 — WebViewResolver stub throws instead of upstream's null-pair
- Our stub `resolveUsingWebView` throws `NotImplementedError`. Upstream NEVER throws — it returns `null to emptyList()` after the timeout (and AnymeX stubs it the same way).
- Plugins that call it directly (not AniKoto/MovieBox) die with an exception instead of degrading gracefully. **FIX A3**: log + return `null to emptyList()` (documented limitation until a real implementation lands).

### RC-6 — Resolver UX: fullscreen page instead of AnymeX-style bottom sheet
- AnymeX (R13-B): episode tap → floating bottom sheet over the details page — header (episode info), morphing spinner + "Scanning for video streams…", progressive source rows as links stream in (quality label + CC-count badge), "Scanning for more…" footer, remembered-server auto-select on arrival, single-link auto-select on completion, selection → player with the full pre-resolved list (no re-resolve), dismissal cancels resolution.
- **FIX D**: `CsResolveSheet` at the MainActivity seam (episode tap no longer navigates directly); pre-resolved seeding via the activity-scoped CsWatchViewModel; remembered last source per anime (mainId) with a badge in the sheet; smart auto-pick (remembered name > quality-desc).

### RC-7 — Player options UI: sources vs quality vs audio vs subtitles
- User: bottom row had speed/subtitles/streams/episodes; subtitles empty; sources vs tracks conflated.
- **FIX E**: Streams sheet → **Sources** (flat, quality-sorted, type badges, current + failed markers, CC badge, remembered badge); **Quality** section (embedded ABR variants) clearly labeled; **Audio** section (embedded audio tracks, only when >1) in the subs sheet renamed **Audio & Subtitles**; subtitle button shows a CC count badge.

### RC-8 — Logging (the user's top diagnostic complaint)
- **FIX F**: phase-tagged, high-context logging across the whole pipeline (see §3), exceptions with stack traces, outgoing request headers (values truncated) at every DataSource build, m3u8 failure forensics, engine retry decisions, generation-guard rejections, and the one-filter logcat recipe (§4) in the as-built doc.

## §2 Implementation phases

### Phase A — vendored API parity (core/cloudstream-api + data/cloudstream)
1. `M3u8Helper.m3u8Generation`: drop the invented referer param → `app.get(streamUrl, headers = m3u8.headers, verify = false)`; add `Anikuta:CS:M3u8` failure logs (status, body preview) and a success log (variant count).
2. `CloudstreamLinkResolver`: wrap `provider.loadLinks` in `withTimeout(timeoutMs)` honoring `loadLinksTimeoutMs` (120s default, 5–480s clamp); TimeoutCancellation → `Failed(timedOut)` with partial links; log the honored timeout at START.
3. Resolver logging richness: link/sub lines now include header VALUES (truncated to 32 chars) and quality label; DONE line includes cache decision.
4. `WebViewResolver` stubs: return `null to emptyList()` with a one-shot warning log (never throw).
5. Unit tests: m3u8Generation referer-drop (fake app capture), resolver timeout paths.

### Phase B — engine + DataSource (core/cs-player)
6. `CsHttpDataSourceFactory.forLink`: attempt-1 semantics = UA from link headers else the injected desktop-Chrome default; `forLinkClean(link)`: no UA override, referer stripped (retry profile).
7. `CsPlayerEngine`: default UA switches to the vendored USER_AGENT string (injected via DI from data:cloudstream — module boundary stays lagradost-free).
8. Engine clean-retry: on first-open `IO_BAD_HTTP_STATUS` 4xx → one automatic rebuild with the clean factory (position preserved), logged; second failure → normal PlaybackError event.
9. `CsPlayerEngine.reset()`: stop + clearMediaItems + state reset.
10. Engine logging: every `start`/`switchLink` logs the outgoing header set (truncated values) + UA + which factory profile; post-READY track census (video/audio/text counts) under `Anikuta:CS:Subs`/`Anikuta:CS:Player`.
11. `audioTracks()` + `selectAudioTrack()` APIs (parity with text/video track APIs).

### Phase C — watch state hardening (feature/cs-watch)
12. UiState: add `resolveGeneration`, `playGeneration`, `engineResetTick`.
13. `startResolution`: bump `resolveGeneration` + `engineResetTick`; every `requestPlay` stamps the current generation.
14. Play trigger (screen): read `viewModel.uiState.value` LIVE; require `playGeneration == resolveGeneration && playRequestId > 0`; log the guard's decision; call `engine.reset()` on `engineResetTick` changes.
15. `onEngineError`: error message aggregates the HTTP codes tried; auto-advance keeps position (unchanged semantics).
16. ViewModelTest updates for the generation lock.

### Phase D — bottom-sheet resolver entry path
17. `CsResolveSheet` composable (feature:cs-watch impl): header, resolving state, progressive rows, CC badges, footer spinner, error/empty states, cancel = resolution cancel.
18. Remembered source: per-mainId last-selected link name (SettingsRepository-scoped tiny store or resolver cache — decide at implementation; simplest: a small Koin singleton with a SharedPreferences backing).
19. MainActivity seam: both `onNavigateToCsWatch` sites route to the sheet state instead of direct `backstack.add`; sheet selection seeds the activity-scoped VM (`seedPreResolved`) then navigates; CsWatchScreen `initialize` consumes the seed (no re-resolve).
20. Auto-select rules: remembered match (on arrival) → single-link (on completion) → user tap. Logged.
21. Details page keeps its current episode-tap UX (no visual change there beyond the sheet appearing).

### Phase E — player sheets reorganization
22. Streams sheet → "Sources": section labels, sorted quality-desc, current/failed/remembered markers, type badges, long-press copy (keep), hidden-count footer (keep).
23. Quality rows relabeled ("Quality — this stream's variants") inside Sources sheet.
24. Subtitles sheet → "Audio & Subtitles": Off + sidecar + embedded sections (existing) + new Audio section when `audioTracks().size > 1`.
25. Controls row: subtitle button CC badge; button order: Sources / Subs / Speed / Episodes.

### Phase F — docs + logging polish
26. 03-PLAYBACK.md as-built updates (pipeline diagram delta, header semantics, retry profile, generation lock, sheet flow).
27. §3/§4 below become the canonical logging + logcat recipe (also surfaced in the in-app console chip docs).
28. AGENT-CONTEXT: D-decisions entries, changelog v0.4.1, lessons (vendored-API drift class: invented defaults must be diffed against upstream call sites; collectAsState lag in play triggers).
29. Device test checklist v2 (§5).

### Phase G — quality gate + release
30. Adversarial review sub-agent (aniyomi-safety byte-check + upstream-parity diff + race analysis on the new guards).
31. CI green (unit tests + assembleDebug + ABI check), AndroidConfig 0.4.1/66.
32. Tag v0.4.1 → release APK workflow GREEN → ntfy notification → worklog + user report (incl. the MPV-vs-Media3 answer).

## §3 Logging contract (tags)

| Tag | Owner | What |
|-----|-------|------|
| `Anikuta:CS:Resolver` | CloudstreamLinkResolver | START (provider, data, timeout), every link/sub (name, quality, type, header VALUES truncated, url), DONE (boolean, counts, duration), timeouts, provider throwables with stack |
| `Anikuta:CS:M3u8` | M3u8Helper | m3u8 fetch outcomes: status code, body preview on non-m3u8, variant counts |
| `Anikuta:CS:Watch` | CsWatchViewModel / Screen | open/re-entry with key parity, generation bumps, play requests (id, generation, link, keepPosition), guard REJECTIONS, engine resets, fallback decisions, progress saves |
| `Anikuta:CS:Player` | CsPlayerEngine | start/switchLink with the FULL outgoing request profile (UA, headers, factory profile), clean-retry decisions, prepared + track census, playerError diagnostics (existing), release |
| `Anikuta:CS:Subs` | engine + sheets | sidecar attach counts, track selections, reattach flow |
| `Anikuta:CS:Sheet` | CsResolveSheet | sheet open, progressive arrivals, auto-select decisions, selection, cancel |

One filter: `adb logcat -v time | grep -E "Anikuta:CS:"` — every phase of the pipeline on one screen.

## §4 Logcat recipes (device debugging)

```
# everything CS (resolve → play):
adb logcat -v time | grep -E "Anikuta:CS:"

# failures only:
adb logcat -v time *:W | grep -E "Anikuta:CS:"

# a clean capture for a report:
adb logcat -c && adb logcat -v time -d > cs-playback.log 2>&1; grep -E "Anikuta:CS:" cs-playback.log
```

## §5 Device test checklist (v0.4.1)

1. AniKoto → Sakamoto Days EP 5 → sheet appears over details → 6 sources + subs stream in; play → subs selectable.
2. Same episode again (cache hit) → sheet rows instant.
3. MovieBox → Loki EP 3 → esla 720p: watch logs — expect 428 then `clean retry` then either play or auto-advance (both valid; log tells which).
4. Watch something, back out, tap a DIFFERENT show's episode → NO stale content: `engineReset` + generation guard lines in log, resolving state clean.
5. Same episode re-entry → resume position honored.
6. Sources sheet: switch link → position kept; failed links marked.
7. Subtitles: sidecar subs appear; Off works; embedded (if any) sectioned.
8. Episode switch from the player's episodes sheet → re-resolve under the overlay, no stale playback.
9. Log capture with §4 recipe → no unexplained silent gaps.

## §6 Explicitly out of scope (deferred, per user)

- Real WebViewResolver implementation (stub now degrades gracefully — documented).
- Plugin extra metadata (covers etc.), sub/dub row merging, CS downloads, search-page caching, debug-tooling removal, versionCode automation beyond the manual bump.
- MPV for CS streams: Media3 stays the engine (see the final report's rationale — DASH + per-link headers + ABR + track selection are native there; AnymeX's mpv choice costs them DASH entirely).
