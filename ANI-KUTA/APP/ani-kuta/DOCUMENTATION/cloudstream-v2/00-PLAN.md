# CloudStream V2 — The Rebuild Plan

> **Task 51 (round 11).** The definitive plan for re-implementing CloudStream extension
> support on a clean branch, built from the lessons of ten device rounds on the
> `streaming/CLOUDSTREAM` reference branch.
>
> **Branch:** `streaming/CLOUDSTREAM-V2` (forked from `main` @ 1962faff, v0.2.63)
> **Reference:** `streaming/CLOUDSTREAM` (kept read-only, never deleted)
> **Scope:** plugin system + repositories + trust + install UI + search + memory/cache +
> details page + episodes/seasons. **NO playback this phase.** The Aniyomi system is
> untouched (one additive binary-safe seam only, documented below).

---

## 1. Why a rebuild

The reference branch accumulated ten device rounds of fixes layered on top of each
other. Its CloudStream *modules* (the clean-room API, loader, manager, installer,
repositories, browse cache, extensions UI) are battle-tested and census-verified —
they are **ported, not rewritten**. Its *integration seams* (search view-model,
details view-model, resolver entanglement) are where the mess lived: CloudStream was
forced through aniyomi-shaped pipelines. V2 keeps the two ecosystems **separate by
construction**:

- CloudStream providers are bridged into the app's source registry (needed so the
  standard search/details screens can consume them), but **no video-resolution code
  is wired** — episode taps hit an honest "playback arrives with the playback port"
  boundary instead of a half-broken resolver.
- The aniyomi `ExtensionLoader` / classloader is **not touched** (the reference's
  child-first experiment stays on the reference branch).

## 2. What is ported vs rebuilt vs skipped

| Zone | Verdict | Reason |
|---|---|---|
| `core/cloudstream-api` (41 files) | **PORT verbatim** (git checkout) | Clean-room binary-compat ABI sized by the 80-plugin census; zero app-coupling; cherry-picking risks `NoClassDefFoundError` at plugin load |
| `data/cloudstream` loader / manager / installer / store / repos / content-repo / browse-cache / model / DI | **PORT verbatim** + fixes | Device-round-proven; fixes = the BrowseCache L102 corruption + the sequential install queue (see §4) |
| `data/cloudstream` bridge | **PORT + STRIP** | Keep identity/catalogue/details/episodes; strip ALL playback internals (getVideoList/loadLinks/HLS/DASH expansion); add an honest not-yet boundary |
| ExtensionManager seam | **REBUILD** (2 small hunks) | `setExternalSources()` + `loadAll` re-merge — the only aniyomi-manager change, additive |
| Search feature integration | **REBUILD carefully** (port the proven CS branches) | The D-348 memory/heal logic + SWR cache ported EXACTLY (every prior simplification was a regression) |
| Details feature integration | **REBUILD** (year/score channel + CS-tolerant paths only) | No resolver changes, no watch-page changes |
| Extensions settings UI | **PORT** (3 new files + 2 modified) | The user validated this UX explicitly ("just like how it currently does") |
| VideoResolver / WatchScreen / WatchKey / player / playback-cache / downloads-of-content | **SKIP entirely** | Playback is explicitly out of scope this phase |
| `data/extension` loader changes (ChildFirstPathClassLoader) | **SKIP** | Aniyomi system must stay untouched; CS has its own loader |

## 3. Architecture invariants (from ten rounds of lessons)

1. **The plugin ABI surface is sacred.** File names (`MainAPI.kt`, `MainActivity.kt`,
   `ExtractorApi.kt`) are binary-compat surface (`MainAPIKt` facades); upstream's
   "weird" branches (`newEpisode` String routing, `fixUrl` strictness) are
   load-bearing. Jackson stays **strictly 2.13.1** (minSdk 24), gson 2.11.0.
2. **Trust gates code execution.** Untrusted plugins are listed but never
   classloaded. `CsPluginRecord.isTrusted` defaults true (legacy decode-safety);
   only fresh installs write `false`.
3. **The loader is idempotent** — repeat loads are Success cache-hits (the manager
   reloads after every mutation; "already loaded" as an error broke every install).
4. **Source identity:** `CsSourceIds` = bit-62 flag | 32-bit name hash — stable
   across restarts, collision-proof against aniyomi MD5 ids.
5. **The search memory uses the D-348-correct heal** (`awaitCsSource`: raw-flow
   fast-path → wait `sourcesLoaded` ≤20s → wait non-empty raw list ≤3s). Never
   validate a persisted selection against a derived flow's initial `emptyList()`.
6. **SWR cache never blanks a shown feed** — cached renders instantly, fresh
   results skip the network, background refresh keeps the old feed on failure
   AND on empty.
7. **Honest boundaries beat dead affordances** — a stripped capability throws a
   descriptive `IllegalStateException` instead of silently doing nothing.
8. **CloudflareBlockedException stays `: IOException`** (the round-8 process-death
   fix) and the CF error card routes on exception TYPE, with UA-bound WebView
   re-entry.
9. **Every episode row keys on URL** — the shared-dub-handle label-neutralization
   (not a dedup-key change) keeps EpisodeListNormalizer / LazyColumn / the episode
   cache consistent.
10. **DB needs zero schema changes** — `content_details` was designed
    provider-agnostic (`extension_type` discriminator enumerates `'cloudstream'`,
    `ext_extra_json` typed extras). CS content rides the existing tables.

## 4. Deliberate changes vs the reference (documented decisions)

1. **Sequential install queue (user requirement, new):** the user requires
   multi-select installs that execute **one by one**. The reference installs in
   parallel. V2 adds a single-flight install queue in the manager: pending installs
   are dequeued one at a time (download → verify → install → load), each with its
   own progress state; queue order = tap order.
2. **Honest playback boundary (new):** the bridge's `getVideoList` throws
   `IllegalStateException("CloudStream playback arrives with the playback port —
   episodes and details are available now")`. No resolver changes, no watch-page
   changes, no half-wired playback.
3. **BrowseCache corruption fix (bug fix):** reference
   `CloudstreamBrowseCache.kt:102` has a corrupted expression
   (`memoryemKey(providerName)]`); restored to `memory[memKey(providerName)]`.
4. **Console logging tool ported (user requirement):** `RingLogBuffer` +
   `com.lagradost.api.Log.sink` + `ConsoleLogsScreen` (Settings → Developer tools →
   Console logs) come along — the user explicitly asked for thorough, maintainable
   debug logging, and this was built for exactly that.
5. **Version line:** `0.3.0` / versionCode 64 — a new era marker; the release
   workflow's tag==versionName contract is respected (`v0.3.0`).

## 5. Phased delivery (each phase ends with a CI-verified build)

- **Phase A** — Foundation: plan doc (this file), gradle wiring (settings +
  libs.versions.toml), CI workflow (branch trigger + unit-test step), version.
- **Phase B** — `core/cloudstream-api` ported whole (41 files + 5 test suites).
- **Phase C** — Core seams: InstallStep move; ExtensionManager external-sources
  merge; SAnime year/score; AnimeHttpSource.isCloudStreamBridged;
  AnimeDetailsProvider year; ExtensionExtras; cloudstreamShowNsfw.
- **Phase D** — `data/cloudstream` ported (+ fixes): model/store/repos/installer/
  loader/manager(+sequential queue)/browse-cache(+fix)/content-repo/bridge(+strip)/
  DI/tests.
- **Phase E** — App shell: build.gradle + AppCompat theme; AnikutaApp wiring;
  MainActivity (AppCompatActivity, CommonActivity, nav keys, CF WebView UA).
- **Phase F** — Details chain: AnimeDetailsKey.year; ExtensionDetailsProvider seed;
  DetailsViewModel year + extras persist; DetailsScreen year row;
  ManualSearchSheet ecosystem sectioning.
- **Phase G** — Extensions settings UI: shared chrome; CloudStream section (4
  sub-sections); plugin detail screen; settings tabs; dual-format repo screen.
- **Phase H** — Search integration: ExtensionAnime fields; SearchViewModel CS
  branches + memory + heal; SearchScreen browse sections + states; sectioned
  source picker.
- **Phase I** — Console logging tool (ring buffer, sink, screen, settings entry).
- **Phase J** — Documentation (this doc zone), AGENT-CONTEXT updates, final CI,
  tag `v0.3.0`, release, notification, device test checklist.

## 6. The Aniyomi-safety contract

The ONLY aniyomi-file changes on this branch:

| File | Change | Risk |
|---|---|---|
| `data/extension/manager/ExtensionManager.kt` | +`setExternalSources()` (diff-replace external entries in `_sources`) + `loadAll` re-merge (`sourceMap + externalSources`) | Additive; without the re-merge every aniyomi reload wipes CS sources |
| `core/source-api/.../SAnime.kt` + `SAnimeImpl.kt` | +`year`/`score` interface props WITH default accessor bodies | Binary-safe for DexClassLoader-loaded extensions (additive members only) |
| `core/source-api/.../AnimeHttpSource.kt` | +`isCloudStreamBridged` open val (default false) | Additive |
| `core/provider-api` + 2 import fixes | `InstallStep` file move (package change) | Mechanical |
| `core/common/model/AnimeDetailsProvider.kt` + AniListDetailsProvider | +`year` param (defaulted) | Additive |
| `feature/anime-details/*`, `feature/anime-search/*` | CS-aware branches alongside existing aniyomi paths | The aniyomi paths keep their exact main-branch behavior |
| `app/*` | Wiring (Koin, nav keys, AppCompat base) | Required for CS plugin PreferenceFragments |

Everything else in the aniyomi system — loader, installer, resolver, watch — is
byte-identical to main.

## 7. Device test checklist (delivered at the end)

1. **Repos:** Settings → Extensions → Repositories → add a CloudStream repo URL →
   verify dialog shows the repo name → repo appears with the CloudStream badge.
2. **Available:** the CloudStream tab lists the repo's plugins with icons; tapping
   one opens its detail page (description, version, TV types, provider list).
3. **Install + trust:** install 2–3 plugins → downloads run one-by-one → each moves
   from Available to Untrusted → open detail → Trust → appears under Trusted.
4. **Untrust/uninstall:** untrust moves back to Untrusted (file kept); uninstall
   removes file + record.
5. **Multi-download:** queue several installs → they execute sequentially with
   per-plugin progress → all land installed.
6. **Search:** pick a CloudStream source in the picker (Aniyomi / CloudStream
   sections) → browse shelves render as categories → search returns results →
   restart app → same source + section selected (memory) → browse is instant
   (cache).
7. **Details:** open a CS result → the standard details page (poster, description,
   tags, year, score, status) → episodes listed with titles/synopsis/thumbnails →
   multi-season shows group by season.
8. **Episode tap:** shows the honest "playback arrives with the playback port"
   message (no crash, no dead spinner).
9. **Aniyomi regression sweep:** aniyomi extensions still browse/search/resolve/
   play exactly as on main.
