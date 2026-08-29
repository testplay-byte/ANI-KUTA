# 23 — Implementation Phase 1: Clean-Room Extension System (Design Record)

> **Session**: Task 41 (first implementation session, 2026-08-29) · **Branch**: `streaming/CLOUDSTREAM`
> **Status**: ✅ phase 1 COMPLETE + CI GREEN (2026-08-29) — 10 commits `565052f…cb00e65` on `streaming/CLOUDSTREAM`
> **Read after**: `21-risks-open-questions.md` (gates), `02-plugin-format.md`, `03-mainapi-reference.md`,
> `04-extension-repositories.md`, `05-data-models.md`.
> **This doc records**: (1) the user's gate decisions, (2) the resulting architecture pivot from
> doc 16, (3) the clean-room protocol, (4) the module design for the extension-management session,
> (5) what is deliberately OUT of scope.

---

## 1. Gate decisions (user session 2026-08-29)

The user answered the gate session directly. Verbatim-recorded consequences:

| Gate | Decision | Consequence |
|---|---|---|
| **G1** (GPL-3.0) | **No GPL license. No relicensing.** ANI-KUTA stays unlicensed/proprietary. The CS3 library's *code* is not copied at all — the compatibility surface is **rewritten from scratch** (clean-room). The user explicitly owns this decision: *"we are not going to copy the code snippets or use that. What we are going to do is rewrite the whole logic, the codes and such, from scratch."* | Doc 16's vendoring plan (`:external:cloudstream3`) is **dead**. R1 (GPL obligations) is resolved by not shipping any GPL code. The doc-16 caution that re-implementation is "months + binary-compat risk" is acknowledged and accepted by the user; the risk is retired incrementally by loading real plugins early (§4.4). |
| **G2** (Vendoring) | **No vendoring.** Recreate only what we need, optimized for our app, well-tested, maintainable. | The compat surface is sized by the **binary census** (§4), not by the whole library. |
| **G3-adjacent** (extension *management* UI — not the content-nav G3) | **Unified Extensions page with source tabs.** An `Aniyomi` / `CloudStream` tab row at the top of the extensions settings screen; a tab appears when that system has installed extensions or saved repositories; repo-add flow is shared (paste URL → auto-detect type); browse/download/manage from the same place. | Doc 18's separate-repo-store plan is superseded for management UI. Content navigation (the original G3: 5th nav tab) remains open for the content phase. |
| **G4** (NSFW) | **Universal NSFW toggle**, linked together with the app-wide setting, highly customizable later. | Session 1 ships a persisted CS NSFW gate (default OFF) used to filter NSFW catalog entries; the app-wide unification lands when the app-wide toggle exists (deferred, recorded). |
| **G5** (Default repos) | (User did not override; recommendation stands.) | **Zero default repos** — repos are user-pasted only, matching D-043 and CS3's own empty prebuilts. |
| **G6** (Dev wipe) | User delegated: *"You can decide."* Agent decision: **accept CORE_RULES §30** (debug = schema freedom). | Note: this session does **not** touch SQLDelight at all (§5.2), so no wipe is needed yet; the doc-17 destructive re-key is deferred to the content phase where it belongs. |
| **G14** (Branch) | **Stay on `streaming/CLOUDSTREAM`.** No throwaway branch; Phase 0+ work lands on this branch. | CI workflow branch pattern extended to `streaming/**` (this session, §6). |

Secondary gates G7–G13, G15–G17 remain open with their recommendations; deadlines unchanged
(content/playback phases). G7 (jsoup) resolves trivially under clean-room: we keep our pinned 1.19.1
(plugins compiled against 1.18.3 run fine against it; the 1.22.1 pressure existed only to satisfy the
vendored library's own code). G8 (gson shim) is folded into the compat module's dependency set.

---

## 2. The architecture pivot (supersedes doc 16 §1)

```
doc 16 plan (DEAD):                    This session's plan (LIVE):
vendor :external:cloudstream3          :core:cloudstream-api   ← clean-room compat surface
(GPL source in-tree, compile           (our own Kotlin, com.lagradost.* package names,
 with our toolchain)                    binary-compatible declarations only)
        ↓                                      ↓
ANI-KUTA calls CS3 library API         plugins load against OUR classes via
directly                                parent-first PathClassLoader
```

What does NOT change from doc 16: the plugin-loading philosophy (parent-first `PathClassLoader`,
manifest.json entry-class discovery, repo-salted install paths, per-plugin error surfacing
D-295/D-296, zero default repos, repo-add = consent + sha256-at-download).

Why the pivot is safe *for us*: the census (§4) shows the actually-referenced API surface is a small,
stable subset of the library. The parts of CS3 we skip (97 built-in extractor implementations, the
app's UI, sync providers, Rhino JS machinery, NewPipeExtractor) are only needed when providers
*execute* — and providers are not executed in this session (management only). Where a plugin
references a class we have not provided, the failure surfaces as an honest per-plugin `Errored` row.

---

## 3. Clean-room protocol (binding for all contributors)

1. **Declarations** (class/interface/enum names, package names, member signatures, enum value names,
   well-known string constants like `USER_AGENT`) are **interop facts** — they are mirrored exactly,
   because plugin bytecode references them.
2. **Implementations are always written from scratch.** Never translated from GPL source. Where a
   function's behavior is documented (in docs 02–05), our implementation satisfies the same
   *contract* with our own code.
3. The research clone at `/home/z/ANI-KUTA-WORK/research/cloudstream/` is consulted for
   *signatures only* (the declarations digest, `/tmp/cs3-declarations-digest.md`, is the working
   extraction). Function bodies there are never read for translation.
4. Unlicensed helper libraries (NiceHttp, CloudstreamApi — **no LICENSE file on GitHub**, verified
   2026-08-29) get the same treatment: clean-room equivalents in the compat module, backed by our
   own OkHttp 5 client. `com.lagradost.nicehttp.Requests` / `NiceResponse` / `ResponseParser` and
   `com.lagradost.api.Log` are re-created.
5. Third-party deps that ARE properly licensed are added as real dependencies:
   Jackson (`jackson-module-kotlin` **2.13.1**, Apache-2.0 — the version the plugin ecosystem pins,
   "do not bump above 2.13.1" per the official template) and Gson (Apache-2.0, for the 13/80 plugins
   that import it). kotlinx-serialization/jsoup/okhttp already ship in the app.
6. Every compat-module file carries this header:
   > `// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
   > compatibility (interop facts only). All implementations are original ANI-KUTA code.
   > No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.`

---

## 4. The binary census (what the compat surface must contain)

Method: string-scan of `classes.dex` in all **80 real `.cs3` plugins** from
`research/phisher-builds/` for `Lcom/lagradost/...;` references (2026-08-29, this session).
Plugin count = how many plugins reference the class (class must exist for those plugins to LOAD):

- **80/80**: `MainAPI`, `MainAPIKt` (the `new*` builders + helpers), `MainActivityKt` (the `app`
  global), `TvType`, `LoadResponse`, `HomePageResponse`, `MainPageRequest`, `plugins.CloudstreamPlugin`,
  `nicehttp.Requests/NiceResponse/ResponseParser`
- **70–79**: `SubtitleFile`, `utils.ExtractorApiKt` (`newExtractorLink`/`loadExtractor`),
  `utils.ExtractorLink`, `SearchResponse`, `MainPageData`, `Episode`, `utils.ExtractorLinkType`
- **50–69**: `ParCollectionsKt` (61! — the `amap` family), `MovieLoadResponse`, `MovieSearchResponse`,
  `plugins.BasePlugin` (60), `TvSeriesLoadResponse`, `utils.AppUtils` (57), `HomePageList`,
  `mvvm.ArchComponentExtKt` (53 — `safeApiCall`/`logError`/`Resource`), `utils.Qualities`,
  `api.Log` (48 — separate unlicensed lib, clean-roomed), `utils.ExtractorApi` (48)
- **15–49**: `Score`, `SearchResponseList`, `Actor`, `SearchQuality`, `ActorRole`,
  `ErrorLoadingException` (22), `ActorData`, `AnimeSearchResponse`, `plugins.Plugin` (20),
  `utils.M3u8Helper` (20), `AnimeLoadResponse`, `DubStatus`, `extractors.StreamWishExtractor` (17!),
  `CommonActivity` (16), `extractors.VidStack` (16), `ShowStatus`, `APIHolder` (14),
  `extractors.Filesim` (12), `extractors.VidHidePro`/`VidhideExtractor` (11), `utils.JsUnpacker` (11),
  `SettingsJson` (10), `TvSeriesSearchResponse`
- **4–9**: built-in extractor classes (`StreamSB` 7, `PixelDrain` 6, `StreamTape` 6, `DoodLaExtractor` 5,
  `MixDrop` 5, `Voe` 5, `FileMoon` 4, `Vidmoly` 4), `CloudStreamApp` (7), `utils.DataStore` (7),
  `utils.SubtitleHelper` (7), `metaproviders.TmdbProvider` (4), `syncproviders.SyncIdName` (4),
  `utils.StringUtils` (4), `network.WebViewResolver` (5), `LiveSearchResponse`/`LiveStreamLoadResponse` (4)
- **≤3** (skeletons or honest-fail): `mvvm.Resource` (3), `plugins.PluginData` (3),
  `plugins.PluginManager` (3), `utils.DrmExtractorLink` (3), `utils.Event` (3), `network.CloudflareKiller` (3),
  `metaproviders.TraktProvider` (2), syncprovider internals (1–2), `ui.*` app classes (1 —
  honest-fail), `MainActivity` (3 — app class, honest-fail), 1-off extractor mirrors (1 each).

**Session-1 tiering** (what loads vs what executes):
- **Full compat surface** (types + builders + enums + helpers): everything ≥4 plugins.
- **Skeletons** (correct declarations; method bodies throw a clear "not implemented in this build"
  error and are only invoked during provider *execution* — later sessions): the 16 built-in
  extractor classes, `M3u8Helper`, `JsUnpacker`, `WebViewResolver`, `CloudflareKiller`,
  `TmdbProvider`/`TraktProvider` open surfaces, `SubtitleHelper` runtime parts.
- **Honest-fail** (not provided; referencing plugins get an `Errored` row naming the missing class):
  `MainActivity`, `ui.home.HomeViewModel`, `ui.settings.extensions.RepositoryData`,
  `plugins.RepositoryManager`/`SitePlugin`/`PluginWrapper`, syncprovider internals beyond
  `SyncIdName`, 1-off extractors (`ByseVepoin`, `Krakenfiles`, …). Total affected: ≤10/80 plugins,
  each still *installable* — they fail at load with a visible reason (D-295/D-296 pattern).

---

## 5. Module design (this session)

### 5.1 New Gradle modules

```
:core:cloudstream-api   ← the clean-room surface (package com.lagradost.cloudstream3[.plugins|.utils|
                            .mvvm|.network|.metaproviders|.syncproviders] + com.lagradost.nicehttp +
                            com.lagradost.api). No dependency on ANY anikuta module. Pure
                            binary-compat layer, mirrored CS3 file layout where practical.
:data:cloudstream       ← our runtime: repo client, plugin loader, manager, installer, persistence.
                            Depends on :core:cloudstream-api (+ Koin). Follows :data:extension
                            conventions exactly.
```

UI lands in the existing `:feature:extensions-settings:impl` (tab row + CloudStream sections) —
the user's unified-extensions-page decision. `:app` wires the new Koin module.

### 5.2 Persistence (follows the aniyomi precedent — NO SQLDelight this session)

`data:extension` deliberately persists repos in SharedPreferences to stay DB-free; the CloudStream
system mirrors that:

| Store | Prefs file | Key | Content |
|---|---|---|---|
| CS repositories | `anikuta_cloudstream_repos` | `repos_json` | JSON `List<CloudstreamRepo>` (url, name, iconUrl?, description?) |
| CS installed plugins | `anikuta_cloudstream_plugins` | `plugins_json` | JSON `List<CsPluginData>` (internalName, url, filePath, version, repoUrl, fileHash) |
| NSFW gate | via `AppPreferences` | `cloudstream_show_nsfw` | Boolean, default **false** (G4: universal-toggle direction; unification deferred) |

W2 (repo.json filename collision between ecosystems) is killed by the separate prefs file. The
`system`/`content_details` SQLDelight axes (doc 15/17) are untouched — they light up in the content
phase. **No schema change, no dev wipe this session.**

### 5.3 The runtime pieces (`:data:cloudstream`)

- **`CloudstreamRepoApi`** — fetch `repo.json` (name/description/iconUrl?/manifestVersion/pluginLists),
  fetch each plugins.json in parallel, flatten, `distinctBy { url }`; `verifyRepo(url)` for the add
  dialog (parse + count). 5-minute in-memory cache (mirrors CS3's HTTP cache contract).
- **`CloudstreamRepoRepository`** — SharedPreferences CRUD + `StateFlow<List<CloudstreamRepo>>`.
- **`CloudstreamPluginInstaller`** — download `.cs3` to `cacheDir` temp (streamed, throttled
  progress), sha256-verify vs `fileHash` (`"sha256-<hex>"` format; null hash → unverified download,
  logged), atomic move to
  `filesDir/CloudstreamExtensions/<sanitize(repoUrl)>.<repoUrl.hashCode()>/<sanitize(internalName)>.<internalName.hashCode()>.cs3`
  (repo-salted — the CS3 answer to same-name plugins across repos), then load.
- **`CloudstreamPluginLoader`** — `file.setReadOnly()` (Android 14+ dex requirement) →
  `PathClassLoader(filePath, context.classLoader)` (parent-first, D-294 invariant) → read
  `manifest.json` **as a classloader resource** → `loadClass(pluginClassName).newInstance()` →
  cast to `BasePlugin` → `filename = path` → `requiresResources` → `AssetManager.addAssetPath`
  reflection (identical to CS3's documented mechanism) → dispatch `load(context)`/`load()` →
  providers/extractors land in the compat module's `APIHolder`-equivalent registry → per-plugin
  try/catch → `Errored` rows with the real exception (never silent).
- **`CloudstreamPluginManager`** — the hub (StateFlows: installed/available/errored/providers,
  install states, update-check state; enable/disable per plugin persisted; Mutex-serialized
  installs; 30-min-throttled update check `version > saved || == -1`; kill-switch handling
  `status == 0` → unload + disabled-by-repo state; uninstall = delete file + unload + drop record;
  repo delete = uninstall all its plugins + confirm dialog).
- **Enable/disable model**: CS3 has no per-plugin enabled state of its own (the kill-switch is
  repo-side) — we add an `enabled` flag on our records (default true). Disabled plugins are not
  loaded at startup (skipped, not deleted). This gives users the same control they have over
  aniyomi extensions and is the G4 "highly customizable later" direction.

### 5.4 UI (`:feature:extensions-settings:impl`)

- **Extensions screen** — tab row (`Aniyomi` | `CloudStream`) directly under the header, chips
  right-aligned to the row edge (session 2). Visibility: both tabs when both systems have content
  (installed extensions/plugins OR saved repos — session 2: installed plugins alone keep the
  CloudStream tab alive even with zero repos); single system → its content without tabs; none →
  current empty state. Aniyomi tab content = existing screen code. CloudStream tab =
  `CloudstreamExtensionsSection` rendering from the SHARED `ExtensionListChrome.kt` (session 2) —
  the identical section pattern: **Trusted Sources** (installed plugins: icon, name, one metadata
  line `v{version} · language · NSFW · Update available`, shared Update pill, uninstall), Failed
  to Load (conditional; retry/uninstall + real reason), **Available Extensions** (icon, name,
  `v{version} · language · NSFW` — NO file size, NO description, the shared normal Download
  install control). No per-row toggles and no per-section NSFW pill (session 2): the ONE shared
  filters bar drives both tabs (search/sort/language/NSFW; the CS NSFW toggle = the persisted G4
  gate, default OFF).
- **Repositories screen** — add dialog now auto-detects: fetch pasted URL → parses as CS
  `repo.json` (name + manifestVersion + pluginLists non-null)? → CS repo; else aniyomi
  `index.min.json`/`index.json` (existing flow); else explicit error naming both expected formats.
  Rows show a type badge (Aniyomi/CloudStream) right-aligned at the row's trailing edge (session 2).
  Deleting a CS repo removes ONLY the repository entry — its installed plugins stay on disk and
  keep working (session 2 device round; they display via install-time-captured metadata and are
  uninstalled individually from the Extensions page; updates from the deleted repo stop).
- **Strings**: hardcoded English (house convention). **Icons**: Coil `AsyncImage` (iconUrl).

### 5.5 Shared `InstallStep`

`:data:extension`'s `InstallStep` (Idle/Pending/Downloading/Installing/Installed/Error) moves to
`:core:provider-api` (`core.providerapi.InstallStep`) so both systems share one progress model in
the unified UI. Mechanical move; aniyomi imports updated; no behavior change.

---

## 6. CI + verification

- `build-apk.yml` branch pattern gains `streaming/**` (SESSION.md's documented "revisit when
  implementation code lands"). A unit-test step runs the new modules' JVM tests
  (`:data:cloudstream:testDebugUnitTest`, `:core:cloudstream-api:testDebugUnitTest`) on every push —
  the project's first unit tests, covering repo.json/plugins.json parsing (fixtures from the REAL
  phisher/official repos), version-comparison logic, sha256 format, path sanitization/salting, and
  manifest.json parsing.
- **Emulator E2E** (the real spike): fresh AOSP-30 x86_64 AVD (per `knowledge/emulator-testing.md`),
  install the CI x86_64 artifact, add the phisher repo URL, install `AllMovieLandProvider.cs3`,
  verify it loads (provider registered, visible in the list with a provider count), toggle
  enable/disable, uninstall. This retires the "binary-compat risk" for the management scope before
  any content work.

---

## 7. Out of scope (recorded, not forgotten)

**Session 3 delivered the browse half of provider execution** — mainPage/search/load + the
search-page integration + the CS content details screen (see §8). STILL out of scope:

- **loadLinks + video execution** — the `loadLinks` surface, built-in extractor implementations
  (real scraping logic for StreamWish/Dood/VidHide/…), `M3u8Helper`/`JsUnpacker` real
  implementations — THE next session (the content details screen labels this boundary explicitly).
- **G3 proper (the Cloud content tab decision)** — session 3 integrated CS into the EXISTING search
  page per the user's explicit direction (a divergence from docs 16 §5.3.6/18 §3.2 — recorded in
  D-334); whether a dedicated Cloud tab/browse home ALSO materializes stays open.
- Data-layer integration (content tables, episode keys — doc 17), playback integration (doc 19),
  favorites/library (doc 18), plugin settings UI hosting (G10 skip), the jsDelivr proxy, deep links
  (`cloudstreamrepo://`), auto-download modes, NSFW app-wide unification (G4 full form), and result
  pagination beyond page 1 (the aniyomi search flow is page-1 too — parity kept).

---

## 8. Session log

- 2026-08-29 (session 1): doc written; census run (§4); declarations digest extracted
  (`/tmp/cs3-declarations-digest.md`, 3,943 lines); NiceHttp/CloudstreamApi confirmed unlicensed →
  clean-roomed; emulator SDK setup launched; implementation started.
- 2026-08-29 (session 1, close): **PHASE 1 COMPLETE — CI GREEN (cb00e65).** Committed: foundations
  565052f → compat b3e4da2 (5,351 lines, 24 files) → runtime 29272ed (loader/installer/manager/stores
  + 11 fixture tests) → UI bef6cfa (tabs + dual-type repos) → 4 CI-fix commits (a9eb913, 300e023,
  bec1f33, 030ecf9 — opt-ins, package FQNs, Uuid bridge, companion access, api() visibility,
  sanitizer trim) → cb00e65 (api deps). Unit tests: 20 interop locks + 11 protocol tests, all green
  in CI. InstallStep moved to :core:provider-api. Emulator E2E was attempted but the sandbox disk
  constraint (emulator userdata-create preflight demands ~7.4GB free regardless of config) made the
  guest unusable under TCG — verification = CI green + the user's device loop (test checklist in the
  session summary). Deferred to next sessions (§7): provider execution, real extractors, content nav,
  data layer, playback.

- 2026-08-29 (session 2, device-feedback round 1): the user tested on-device and every finding was
  addressed. **CRITICAL root cause**: the loader rejected repeat loads with
  `Failure("Plugin already loaded")`, and `installPlugin` → `loadAll()` re-loads every record — so
  EVERY fresh install landed in "Failed to load", retry was a lottery (unload-then-load race vs.
  concurrent refreshes), and enabling a second plugin evicted the first. Fix: **the loader is now
  idempotent** (repeat load of an active path = `Success` with live registry state; updates unload
  the stale instance AFTER the verified download replaces the file), and **all manager mutations
  funnel through one mutex-serialized `refreshLocked()`** (loadAll + rebuildLists atomically).
  UI consistency round (the CloudStream tab now renders from the SAME chrome as the aniyomi tab —
  new shared `ExtensionListChrome.kt`: SectionHeader cards, ActionIconButton, the D-309/D-311
  progress machines, icon placeholders): sections renamed/restructured to **Trusted Sources /
  Failed to Load (conditional) / Available Extensions**; cloud-shaped install button → the shared
  normal Download control; file size + description dropped from available rows; per-row
  enable/disable toggles and the section-header NSFW pill removed (the ONE shared filters bar now
  drives BOTH tabs — search/sort/language/NSFW; CS NSFW = the persisted G4 gate, default OFF);
  source-tab chips + repo-row type badges right-aligned to the row edge (device report). Install
  animation: installer explicitly emits `Downloading(100)` + 300ms beat before `Installing`, the
  ring fill is animated (`animateFloatAsState`), and the manager holds `Installed` for 700ms before
  the lists reshuffle — the fill visibly completes and the "Done" check plays out. **Repo deletion
  no longer cascades**: deleting a CS repo removes only the entry; its plugins stay installed and
  fully usable (records now persist `language`/`iconUrl`/`isNsfw` captured at install so rows
  render aniyomi-parity even post-repo-delete; legacy session-1 records decode via
  `ignoreUnknownKeys` defaults — covered by a new unit test). `isEnabled` removed from
  `CsPluginRecord`/`CloudstreamExtension.Installed`; `setEnabled`/`deleteRepoPlugins` deleted.
  Verified locally (Temurin JDK 21 + constrained-memory Gradle): `:data:cloudstream` +
  `:core:cloudstream-api` unit tests (12 + 20) and full `assembleDebug` all green before push.

- 2026-08-29 (session 3): **DEVICE-FEEDBACK ROUND 2 + PROVIDER EXECUTION PHASE 1.** Round-2 report: UI "consistent… proper, perfect, beautiful"; fixes = version discipline (v0.2.64 — never ship two builds on one version), tabs LEFT-aligned (reversing round-1's right-edge request), repo badge on the TITLE line, parallel installs (Pending+download moved OUTSIDE the install mutex — the second download previously blocked silently; only swap/load/refresh serialize; double-tap guard), and the TRUST FLOW: `CsPluginRecord.isTrusted` gates code execution — new installs land in a new Untrusted section (listed, never classloaded — aniyomi TrustService parity); Trust loads+promotes, Untrust unloads+demotes, updates preserve trust, legacy records decode trusted (grandfathered, unit-locked). Every CS row is now clickable → NEW `CloudstreamPluginDetailScreen` (authors/description/version/status/size-disk-or-catalog/Supported-Modes-tvTypes/language/repo + live provider list + per-state actions; catalog metadata captured at install survives repo deletion). **Execution (D-334):** NEW `CloudstreamContentRepository` (sources()/mainPage/search/load; TAG `Anikuta:Data:Cloudstream:Exec` with browse:/search:/load:/resolve: prefixes + durations — one logcat filter = whole pipeline, second = one operation; failures throw precise reasons); Search-page integration per the user's explicit direction ("browse using these extensions properly on the search page") — a DIVERGENCE from docs 16 §5.3.6/18 §3.2 (which deferred search-tab unification to a 5th Cloud tab): the aniyomi flow stays byte-identical and CS rides alongside via string `sourceKey` ("cloudstream:<providerName>", doc 16 §5.2 identity discipline) on `ExtensionAnime` + the grid keys + the details-nav branch; picker sheet = Anime Extensions + CloudStream sections; `ExtensionNoBrowse` honest state for search-only providers; NSFW-gated picker (persisted G4 gate); selection healing. NEW `:feature:cloudstream-content` module — `CloudstreamContentDetailsScreen`/VM (poster hero + type/year/score/status/duration, tags, description, season-grouped episodes w/ Dub/Sub labels, single Movie entry, Loading/Error/Retry, explicit "playback arrives in the next update" note — loadLinks + the 16 real extractors remain §7's NEXT session; no dead buttons). Rule-8 ruling recorded (CI = primary verification, used freely; local only when cheap — CI-only this session). CI compile-fix chain: DubStatus `id` (not `value`), cross-module smart-cast, sealed-base elvis chain, Errored-has-no-repoUrl, navigateToDetails widened to NavKey. v0.2.64 tagged + released after green.

- 2026-08-29 (session 4): **DEVICE-FEEDBACK ROUND 3 — the execution phase actually works on-device.**
  Round-3 report: the trust flow + detail page + untrusted section were validated ("perfect… no
  issues"), and two REAL execution bugs surfaced with clean root causes. **(A) ClassCastException on
  trust (MovieBoxProvider)** — the plugin's `load(context)` does `context as AppCompatActivity`
  (stash-the-activity pattern, doc 02 §6.3) and our loader passed the APPLICATION context; upstream
  passes `this@MainActivity` (PluginManager call sites verified: `loadSinglePlugin(this@MainActivity)`
  etc.). Fix: MainActivity now extends **AppCompatActivity** (theme parent →
  `Theme.AppCompat.NoActionBar`, all visual attributes pinned — AppCompat themes resolve to the same
  platform Material theme on API 21+; the app's other two activities are ComponentActivity and
  unaffected), registers itself with `CommonActivity` (identity-guarded clear in onDestroy), and the
  loader passes the LIVE activity to `Plugin.load()` (app-context fallback only when no activity is
  alive). `appcompat:1.7.1` added to :app — also satisfies the `androidx.appcompat.*` references
  present in real plugin dexes (parent-first resolution). `AnikutaApp` now extends
  **CloudStreamApp** (its onCreate publishes `CloudStreamApp.context` — the getKey/setKey surface +
  the CF solver's context fallback). **(B) browse/search → 0 items (AniKoto)** — the site is
  Cloudflare-fronted (server: cloudflare); the sandbox gets cached 200s but the device gets a
  CHALLENGE page (403/200 interstitial) which jsoup parses into 0 items with no exception — exactly
  the log signature. Verified the site + selectors work with plain GETs (10 items, `div.ani.items >
  div.item`). Fix: **CloudflareKiller is real** (WebViewResolver.kt): challenge detection
  (`cf-mitigated` header + body markers on 403/429/503 + cheap 16KB 200-HTML scan), headless WebView
  solve on the main thread (UA pinned to USER_AGENT so the clearance cookie binds, cf_clearance
  capture via CookieManager, 20s watchdog, main-frame-error early-out), per-host cookie cache,
  **per-host solve serialization** (the sectioned browse fires N parallel shelf requests — later
  callers reuse the winner's cookies), 60s failed-solve cooldown (fast-fail instead of 20s hangs),
  and `CloudflareBlockedException` (friendly `userMessage`) when un-bypassable — a challenge page is
  NEVER silently parsed as "no results" (D-295/D-296). Wired into the plugin `app`/`insecureApp`
  shared base client; logs under `Anikuta:Data:Cloudstream:Net` with `cf:` prefixes. **(C) Sectioned
  browse (the round-3 feature request)** — `browseSections()` replaces `mainPage()`: EVERY provider
  shelf fetched (page 1, parallel, per-shelf failure tolerated + logged, all-CF-blocked surfaces the
  block); `ExtensionBrowseSuccess` renders titled horizontal rows ("Latest Updated", "Most Popular",
  …) reusing the flat-grid card at fixed 110dp width — search results stay the flat grid.
  **(D) Detail-page buttons per the round-3 spec:** available → Install at the VERY BOTTOM, full
  width (the shared progress machine centered while in-flight); untrusted → [Trust Plugin][Uninstall]
  side-by-side under the header (Uninstall moved off the bottom row); trusted → [Untrust][Uninstall]
  bottom row UNCHANGED (explicitly approved). **(E) Retry animation:** manager `retrying` StateFlow;
  spinner on the Failed-to-Load row's retry icon + the detail Retry button (round-3: "no animation
  while it was reloading"). CI chain: 2 red rounds (extension fns can't be called fully-qualified —
  `kotlinx.coroutines.async {}` must be imported; retry spinner takes a Boolean membership check, not
  the Set) → GREEN d2748114 → v0.2.65 tagged + released.

- 2026-08-29 (session 5 / Task 45): **DEVICE-FEEDBACK ROUND 4 — the 8KB truncation root cause + the source bridge (STANDARD details screen) + untrust-in-list + the closed CF manual-solve loop.** Round-4 report: trust flow + extensions section validated ("everything else was working properly"); CS search/browse returned 0/empty results across providers (AniKoto "0 item(s)" in 150ms, Anikage `JsonEOFException` at col 8083, AllMovieLand 6 results — size-dependent!); the custom CS details page rejected ("It should utilize the exact same one. Nothing should be changed. No custom new details page"); no untrust option in Trusted Sources; a Cloudflare message referencing a WebView that couldn't be opened. **(D-340) Root cause:** `NiceResponse.readBody()` called okio's `Source.read(sink, byteCount)` ONCE — a single underlying read returning at most ONE ~8KB segment — silently truncating every plugin HTTP body > 8KB (jsoup → "0 items, no error" on HTML; Jackson EOF mid-token on JSON; the one provider whose payload fit one segment "worked"). Fixed with a read-until-EOF loop; added the demanded diagnostics — `CsNetLoggingInterceptor` + per-body-read logging under `Anikuta:Data:Cloudstream:Net` (`http: →/←`, `body: read N chars … first="…"`, error-body snippets on non-2xx). **(D-341) Source bridge:** every trusted provider is exposed as an `AnimeHttpSource` (`CloudstreamAnimeSourceBridge`, stable id `0x4000000000000000 | hash(name)` — collision-impossible, restart-stable) and merged into ExtensionManager (`setExternalSources`, preserved across loadAll rebuilds; wired in AnikutaApp). CS results navigate `AnimeDetailsKey.Extension` → the standard DetailsScreen resolves details/episodes/save/tags/background/auto-link through the provider; SEpisode.url = the provider's opaque `Episode.data` (the future loadLinks key); Dub/Sub labels via scanlator; movies/Live as single entries; plugin Errors → IllegalStateException (the aniyomi catch sites catch Exception); `getVideoList` = honest "playback arrives in the next update". `:feature:cloudstream-content` DELETED. **(D-342) CF loop + untrust:** CloudflareBlocked card (with WebView action) for CS blocks; ExtensionEmpty carries provider mainUrl; CloudflareKiller merges the system WebView CookieManager per host (manual solves now reach the plugin client); the manual WebView pins the CS USER_AGENT (clearance is UA-bound); more 200-JS-disabled challenge markers; RemoveModerator untrust icon + confirm dialog on every Trusted Sources row. v0.2.66.

- 2026-08-29 (session 6 / Task 46): **DEVICE-FEEDBACK ROUND 5 — activity-gated initial plugin load (the MovieBox restart fix), source-picker selection state repaired (single checkmark + remembered CloudStream selection), details-page enrichment (year/rating/seasons/thumbnails), trust-UX parity (shield icon + one-tap untrust).** Round-5 report: execution VALIDATED end-to-end ("Every single extension or plugin is working perfectly… results exactly how I hoped"); the sectioned browse + categories validated; the standard details page validated as "perfect"; remaining findings were quality/polish items. **(D-344) MovieBox "Failed to load" after every restart:** root cause = `CloudstreamPluginManager.init{}` ran `loadAll()` synchronously inside Application.onCreate — BEFORE MainActivity exists — so `Plugin.load(context)` received the APPLICATION context and every `context as AppCompatActivity` plugin (the Task-44 pattern) failed at every cold start while interactive trust (activity alive) worked. Fix: `CommonActivity.activityFlow` (the activity ref also exposed as a StateFlow — ANI-KUTA addition to the mirrored surface) + the manager's initial load SUSPENDS on `activityFlow.first { it != null }` (15s timeout → app-context fallback for background process starts; loadAll stays off the installMutex — the repos collector holds it across network fetches and loadAll is suspension-free on the Main dispatcher, so it executes atomically wrt other main-thread coroutines). `manager.loadedOnce` StateFlow set at the end of every loadAll. **(D-345) Source picker:** (a) the double-checkmark bug — `selectedSourceId` was passed to the sheet unconditionally while the CS provider was kind-gated, so both the stale aniyomi row AND the CS row showed checkmarks; both params are now kind-gated symmetrically (the not-selected ecosystem's remembered id stays persisted for switching back, it just isn't checked). (b) the heading says "Aniyomi" (user request) instead of "Anime Extensions". (c) the "forgets my CloudStream source after restart" bug — the CS-selection heal collector fired on the FIRST (still-empty) csSources emission at cold start and reset a persisted CloudStream selection to aniyomi; it now WAITS for `sourcesLoaded` (the deferred first load's completion signal; 20s bounded timeout so a stuck pipeline can never freeze the fallback). **(D-346) Details enrichment:** (a) `SAnime.year: Int?` + `SAnime.score: Double?` — a binary-safe optional enrichment channel (interface properties with default no-op accessors; only the CS bridge populates them today) → `toUnifiedAnime` maps them to `seasonYear`/`averageScore` (0..10 → 0..100), so CS details pages now render Year + Score ("★ N%", "Score N / 100") like AniList entries. (b) SEASONS: the bridge previously DROPPED `Episode.season` → a 2-season series rendered as one flat list; the season is now encoded INTO the episode name as a leading "Season N - Episode M" tag (the exact SeasonDetector pattern the whole season UI — detector, EpisodeTitleParser title stripping, chip selector, per-season display numbers, D-317 renumber ordering — already understands); per-season fallback numbering when the provider omits Episode.episode. (c) IMAGE URL RESOLUTION: many providers return RELATIVE poster paths ("/poster/x.jpg") or protocol-relative ("//cdn…") which Coil silently fails on — every image-shaped URL (search cards via repository.absolutize, details poster/background, episode preview_url) is now absolutized against the provider mainUrl; details poster only OVERWRITES the incoming thumbnail when resolvable; `Episode.posterUrl` finally flows to `SEpisode.preview_url` (episode-row thumbnails). (d) DIAGNOSTICS (explicitly requested): `details:` line per load (type/year/score/status/tags/plot-length/poster resolution outcome/background) + `episodes:` line (count, per-season histogram, sample names) under `Anikuta:Data:Cloudstream:Bridge`. **(D-347) Trust-UX parity:** the Untrusted rows' Trust icon is the SHIELD glyph (`VerifiedUser`) instead of the checkmark-badge (`Verified`); untrust from the Trusted Sources list is a ONE-TAP action (confirmation dialog removed — aniyomi parity, explicitly requested; the plugin-detail screen's Untrust was already direct). v0.2.67.

- 2026-08-29 (session 7 / Task 47): **DEVICE-FEEDBACK ROUND 6 + THE PLAYBACK SESSION.** Round-6 report: MovieBox restart fix + single-checkmark switching + rating/seasons/thumbnails + trust UX ALL validated; remaining: the CS selection still forgotten after restart, the top tab resetting to AniList, and the year row missing. **(D-348) Search memory root cause:** the Task-46 heal gated on `loadedOnce` (the MANAGER) but validated against the derived `csSources` — a TWO-layer `stateIn(WhileSubscribed)` chain whose initial value is `emptyList()`; the chain only carries real providers several dispatch hops AFTER its first subscriber attaches (the heal collector was that subscriber) → "provider gone" → the persisted kind was destroyed on every cold start. Fix: `awaitCsSource` (raw-flow fast path → sourcesLoaded gate → bounded NON-EMPTY wait; loadedOnce makes `installed` final so a short post-load wait suffices) fronts the heal + both CS loaders; the top tab is persisted (`search_selected_source`). **(D-350) Year:** score rendering proved the Task-46 channel correct — the provider's load() simply omits year while its SEARCH responses carry it; the search-time year now seeds the stub SAnime (load().year wins, seed fills nulls; enrich/refresh paths re-seed) and year+score persist in the additive ext-extras JSON (cache-first reopens keep both). **(D-349) PLAYBACK (doc 23 §7 executed; doc 19 §1–3 implemented):** DEX string census over all 80 phisher .cs3 files first (53/80 loadExtractor; family subclass counts; 20/80 M3u8Helper; 19 missing variant classes that would NoClassDefFoundError lazily inside loadLinks). Bridge getVideoList = loadLinks with CS3 streaming semantics (concurrent queues; partial results survive mid-call failure; URL dedup; DRM/DASH/TORRENT/MAGNET filtered+counted; CS3 header fold + UA default; episode-level subs + audioTracks → Track) → Video with resolver-parsable "Source - label 720p" titles + per-source "Mirror N" for unlabeled links; getHosterList throws for instant fallback; `videoListTimeoutMs` (CS clamp 5s–8min via the provider's loadLinksTimeoutMs) replaces the fixed 30s. REAL extractor runtime: one shared `extractJwPlayerLinks` engine (multi-pass unpack → sources/file/bare-URL + caption tracks) for the jwplayer families + dedicated Dood (pass_md5 + "30jofjgmm3" filler + token/expiry), StreamTape (robotlink concat), MixDrop (MDCore.wurl), StreamSB (sources-API probe), Voe, Dailymotion (metadata API), PixelDrain, Ok.ru, Streamlare + the 19 variants — 35 built-ins registered at manager init BEFORE plugins (reverse-order dispatch → plugin mirrors win). REAL P.A.C.K.E.R. unpacker (escape-aware args regex, balanced-paren replacement, multi-pass; JsUnpackerTest). REAL M3u8Helper (#EXTM3U validation, master→variant fan-out, RESOLUTION heights, URI absolutization, quality dedupe, returnThis passthrough). ZERO downstream watch-flow changes (resolver/picker/WatchKey/WatchScreen/MPV/episode-switch/progress/apply are Video consumers). links: diagnostics under Anikuta:Data:Cloudstream:Bridge. v0.2.68 (versionCode 68); remaining for the next session: CS downloads (doc 19 §6), the 403 re-resolve ladder (§7.2 step 3), per-track subtitle headers (§1.2), extractorData verifier (§4).

- 2026-08-30 (session 8 / Task 48): **DEVICE-FEEDBACK ROUND 7 — PLAYBACK ROOT-CAUSED AND FIXED + the resilience session.** Round-7 report: search-selection memory validated ✓ (the request became "keep the whole page cached so it is instantaneous"); the year missing NEXT TO THE TITLE (the bottom InfoSection row worked — the header meta row simply never rendered seasonYear); **ALL playback failed** — AniKoto's resolver failed with ZERO network IO, MovieBox called `get?subjectId=%224977819452529168144` (a URL-encoded leading QUOTE) → data:null → play-info 400. **(D-351) THE ROOT CAUSE — one line, every provider:** the resolver log format contains no literal quotes yet the output showed them, so the quotes were IN the episode URL — our clean-room generic `newEpisode(data: T)` did `data.toJson()` unconditionally, JSON-quoting every String episode handle, while UPSTREAM special-cases `data is String` → the url overload ("just in case java is wack" — and both plugins' smali call exactly the Object-typed overload, so the branch is LOAD-BEARING). AniKoto's loadLinks startsWith checks failed on the leading quote → instant return false; MovieBox's split("|")[0] carried the quote into subjectId. Fixes: the generic routes String → url overload like upstream; `MainAPI.fixUrl` + `ExtractorApi.fixUrl` aligned verbatim to upstream (`startsWith("http")` pass-through — our lenient `contains("://")` was a task-41 invention; `//x`→`https://x`; opaque handles get the mainUrl prefix that providers' loadLinks PARSE — AniKoto checks startsWith("$mainUrl/anikoto|"), MovieBox uses substringAfterLast('/')); the bridge strips `removeSurrounding("\"")` so v0.2.68-CACHED quoted episodes still resolve; CompatSurfaceTest pins String-not-quoted/Object-serialized/handle-prefixing/`//`→https. **(D-352) Instant search page:** `CloudstreamBrowseCache` — ConcurrentHashMap memory + one JSON snapshot per provider under files/cloudstream/browse/ (async write after every successful browse; 10-min freshness TTL) — and `loadCloudstreamPopular` is now stale-while-revalidate: a cached feed (ANY age) renders BEFORE the plugin manager finishes loading; fresh snapshots skip the network entirely; a failed/empty refresh NEVER blanks a shown page (errors only surface when there is no cache); provider-gone invalidates its snapshot. **(D-353) The 403/expired-link recovery ladder (doc 19 §7.2 step 3 + §8.2 steps 3-4 — implemented):** a bounded 3-recovery state machine in WatchScreen keyed on `(errorMessage, errorGeneration)` (a new PlayerStateHolder counter so identical error STRINGS still re-trigger): error #1 → same-URL retry after 1.5s with D-247 cache-bypass (SKIPPED when the HTTP context already says 401/403/410 — retrying a dead URL is pointless); error #2 → re-resolve the PINNED link (fresh VideoResolver run, tier-matched server/audio/quality, loadfile swap under NonCancellable); error #3 → NEXT MIRROR; #4 → terminal banner. The watch position is captured pre-swap and restored post-FILE_LOADED via `pendingLadderSeek`; the ladder re-arms on every READY. Plus **deferred switch-error surfacing**: updateError during isSwitching used to DROP the error — a swapped-in dead link then waited the full 30s switching-timeout; errors are now deferred (8s grace — switch completes → dropped as teardown noise; still switching → surfaced → ladder advances). **(D-354) Per-track subtitle headers (doc 19 §1.2 — implemented):** CS `SubtitleFile.headers` existed in the shim but was DROPPED at the bridge's Track fold; headers now flow Track(url,lang,headers) → ResolverSubtitleTrack.headers (MPV csv) → WatchKey's optional THIRD \u001F field (2-field lines keep working — backward compatible both directions) → `PlayerObserver.pendingSubtitleTracks: List<PendingExternalTrack>` (per-track headers WIN, the video's headers stay the fallback) → SubtitleDownloadRequest per-request headers (SubtitleEngine already supported them); the ladder/quality-switch/episode-switch/cache-proxy serializations all carry the field; the download side folds per-track headers into DownloadTrack + `applyTrackHeaders` uses the comma-SMART DownloadHeaderParser (the naive split(',') truncated every User-Agent). **(D-355) CS downloads (doc 19 §6 — implemented):** the R7-B pipeline map proved the chain bridge-compatible (synthetic ids through ExtensionManager, headers→parser, SAF storage, '|' URLs safe in TEXT keys + sanitized filenames, HLS support) EXCEPT the re-resolve gate — `buildRequest` minted a ResolveContext only for localhost URLs, so an expired CS link 403'd into PERMANENT error. `ResolveContext.linkRotates` (additive, default false) is minted for bit-62 CS sources; both fetchers widened `isLocalhost` → `isLocalhost || linkRotates` (read via a minimal ResolveContextPeek mirror in :core:download — the module boundary preserved like HttpDownloader.ReResolver) → expired links self-heal through the EXISTING ReResolver machinery. The silent mainId-race no-op in handleDownloadSpecificVideo now toasts. Known limitation documented: multi-variant HLS masters download the FIRST variant (quality choice ignored for m3u8). **(D-356) Playback haptics + the header year:** `PlayerHaptic` (SEEK_TICK/PLAY_PAUSE_TOGGLE/SEEK_RELEASE via the existing HapticHelper — VibratorManager-direct, immune to OEM toggles) at 6 gesture sites (fullscreen double-tap + ±10s + play/pause + seekbar release; minimized double-tap zones + play/pause + minimal seekbar), gated by the new `player_haptic_feedback` pref (default ON); the details header meta row now renders seasonYear (`★ 82% · 2018 · Completed · 36 eps`). v0.2.69 (versionCode 69); remaining for the next session: extractor hardening driven by round-8 device data (each census family is an isolated fix), HLS variant-quality selection (doc 19 Q), the extractorData verifier (§4).
