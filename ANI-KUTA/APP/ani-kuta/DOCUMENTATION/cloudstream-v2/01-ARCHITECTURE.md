# CloudStream V2 — Architecture

> How the CloudStream extension system is built on `streaming/CLOUDSTREAM-V2`.
> The plan lives in [00-PLAN.md](00-PLAN.md); this document describes what
> actually shipped. Task 51 (round 11).

## 1. The two-ecosystem principle

The app supports TWO extension systems, kept separate by construction:

| | Aniyomi | CloudStream |
|---|---|---|
| Package format | .apk (PackageInstaller) | .cs3 (zip + dex, app-managed files) |
| API surface | `eu.kanade.tachiyomi.animesource.*` (:core:source-api) | `com.lagradost.cloudstream3.*` (:core:cloudstream-api, clean-room) |
| Loader | `ExtensionLoader` (data/extension) | `CloudstreamPluginLoader` (data/cloudstream) |
| Manager | `ExtensionManager` | `CloudstreamPluginManager` |
| Trust | signature-based (TrustService) | per-plugin record (`CsPluginRecord.isTrusted`) |
| Source ids | MD5(name/lang/versionId), sign bit cleared | bit-62 flag \| name hash (`CsSourceIds`) — collision-proof |
| Repos | index.min.json | repo.json → pluginLists[] → plugins.json |

They MEET in exactly two places, both additive:

1. **The source registry** — `ExtensionManager.setExternalSources()` merges
   bridged CS sources into the shared `sources` map (the re-merge in `loadAll`
   keeps them alive across aniyomi reloads). This is what makes CS results open
   the STANDARD screens.
2. **The ecosystem marker** — `AnimeHttpSource.isCloudStreamBridged` (default
   false): UI layers (source pickers, link sheets) section by ecosystem without
   instanceof checks against bridge classes.

Everything else — install flows, trust flows, repo management, settings UI —
is per-ecosystem. The aniyomi system is byte-identical to `main` except the 7
additive seams documented in 00-PLAN.md §6.

## 2. Module map

```
core/cloudstream-api/          ← the clean-room plugin ABI (41 files)
  com.lagradost.cloudstream3   MainAPI (provider contract), LoadResponses,
                               SearchResponses, ExtractorApi + 43 built-ins,
                               plugins/ (BasePlugin, manifest models),
                               network/ (WebViewResolver CF solver, safety net),
                               nicehttp/ (Requests HTTP client)
  com.lagradost.api            Log facade (plugin logging → the ring buffer)
  tests/                       CompatSurface, JsUnpacker, LoadExtractorDispatch,
                               M3u8ParseMaster, MpdParser

data/cloudstream/              ← the extension system runtime
  loader/CloudstreamPluginLoader    DexClassLoader (parent-first, read-only dex),
                                    manifest-as-resource, activity-context,
                                    idempotent, collision WARN
  CloudstreamPluginManager          StateFlows (installed/untrusted/errored/
                                    available/installStates/loadedOnce),
                                    activity-gated first load + self-heal,
                                    trust gates execution, SEQUENTIAL install
                                    queue (installQueueMutex — user requirement)
  installer/CloudstreamPluginInstaller  sha256 streaming download → atomic
                                    move; repo-salted paths; progress beats
  repo/CloudstreamRepoApi           repo.json → pluginLists → plugins.json,
                                    5-min cache, parallel fetch, structural
                                    repo detection (parseRepositoryOrNull)
  repo/CloudstreamRepoRepository    repos persist in SharedPreferences
                                    (anikuta_cloudstream_repos/repos_json)
  repo/CloudstreamPluginStore       CsPluginRecord store (trust + install-time
                                    metadata)
  content/CloudstreamContentRepository  sources/sourcesLoaded flows, parallel-
                                    shelf browse (≤20/row, per-shelf fault
                                    tolerance, CF-all → error), search, load
  content/CloudstreamBrowseCache    memory+disk SWR browse cache (10-min TTL,
                                    never-blank invariant)
  content/CloudstreamAnimeSourceBridge  THE BRIDGE (see §3)
  model/CloudstreamExtension        sealed UI model (Installed/Untrusted/
                                    Available/Errored + CsProviderInfo)
  di/CloudstreamExtensionModule     Koin wiring
  tests/                            repo parsing + episode mapping (22 cases)
```

## 3. The bridge (CloudstreamAnimeSourceBridge)

One CloudStream `MainAPI` provider exposed as an aniyomi `AnimeHttpSource` —
resolved BY NAME on every call (survives plugin updates). On this branch the
bridge is deliberately DETAILS/EPISODES-ONLY:

- **Catalogue**: getPopularAnime / getSearchAnime / getLatestUpdates over the
  provider's mainPage/search.
- **Details**: `load(url)` → `applyOnto` (title, description=plot, genre=tags,
  status mapping, poster/background absolutized only-when-resolvable,
  `year = load.year ?: seed`, score).
- **Episodes**: `episodesOrComingSoon()` — honest coming-soon errors; then
  `toEpisodes()`: TvSeries lists, Anime dub-map (Sub/Dub labels; handles
  shared across dub tracks → ONE label-neutral row — identity-key discipline:
  every downstream consumer keys on URL), Movie/LiveStream/Torrent single
  rows, season encoded as leading "Season N - Episode M" name tags (the
  aniyomi SeasonDetector-native format), per-season counters, scanlator=dub
  label, preview_url absolutized, `distinctBy { url }`.
- **Playback boundary**: `getVideoList` throws
  `"CloudStream playback arrives with the playback port — episodes and details
  are available now"`. The resolver contract is kept intact (getHosterList
  ISE → fast fallback → the honest not-yet error renders in the resolver
  sheet). The playback port (loadLinks + extractors + watch page) is its own
  future phase, per the user's directive.

## 4. The search integration

- **Source picker**: sectioned Aniyomi / CloudStream (headers only when both
  ecosystems are installed). CS rows carry plugin icons.
- **Memory (persisted)**: top tab (`search_selected_source`), source kind
  (`search_selected_source_kind`), CS provider name
  (`search_selected_cs_provider`), legacy aniyomi source id. The
  `awaitCsSource` heal (raw-flow fast-path → wait `sourcesLoaded` ≤20s → wait
  non-empty raw list ≤3s) makes persisted selections survive cold starts
  without false "uninstalled" verdicts.
- **Categories**: the provider's mainPage shelves render as titled LazyRows
  (≤20 cards each, deduped per row). Search-only providers (no mainPage) show
  the honest "type a query" prompt card.
- **Caching**: `CloudstreamBrowseCache` — memory ConcurrentHashMap + per-
  provider disk JSON snapshots (`files/cloudstream/browse/`), 10-min fresh
  TTL. SWR: cached renders instantly (even before plugins finish loading),
  fresh results skip the network, background refresh keeps the shown feed on
  failure AND on empty (never-blank).
- **Cloudflare**: blocks route on exception TYPE to the CF card with the CS
  client's pinned UA; "Open in WebView" solves with the UA-bound
  cf_clearance.

## 5. Install + trust flows

1. **Repo add**: Settings → Extensions → Repositories → paste URL → the add
   flow tries CloudStream repo.json FIRST (structural check: name +
   manifestVersion + pluginLists), aniyomi index.min.json second, honest
   dual-format errors otherwise.
2. **Install (sequential queue)**: tapping Download on N plugins queues them
   ALL instantly (each row shows its own Pending state); installs execute
   strictly ONE BY ONE (download → sha256 verify → atomic move → record →
   load) under `installQueueMutex`. New installs land UNTRUSTED.
3. **Trust gates execution**: untrusted plugins are LISTED but never
   classloaded. Trust → loads + moves to Trusted Sources. Untrust → unloads +
   demotes (file kept). Uninstall → deletes file + record.
4. **Self-heal**: the manager's first load waits ≤15s for a live Activity
   (published by MainActivity via CommonActivity); a crash-restart that missed
   it re-runs loadAll when an activity appears (the loader is idempotent).

## 6. Database usage — ZERO schema changes

The DB was designed provider-agnostic (`DOCUMENTATION/database/`):
`content_details` has an `extension_type` discriminator that enumerates
`'cloudstream'` + `ext_extra_json` typed extras. CS content rides the
existing tables through the standard `ContentRepository` paths; year/score
persist in `ExtensionExtras` (additive JSON — old rows decode without them).

## 7. Logging (the debug stage's contract)

Every CS module logs through `Logger` with CORE_RULES §20 tags
(`Anikuta:Data:Cloudstream:*`). The `RingLogBuffer` (10k ring) runs in ALL
builds; the plugin facade (`com.lagradost.api.Log`) sinks into the same ring;
Settings → Developer tools → Console logs gives the on-device view + export
(version/device header + ring snapshot + own-process logcat → share sheet).

## 8. What is NOT on this branch (the playback port)

Video resolution, link extraction at playback time, the watch page's CS
paths, downloads of CS content, and the aniyomi classloader experiment. All
deliberately deferred per the user's round-11 directive. The bridge's honest
boundary marks every entry point.
