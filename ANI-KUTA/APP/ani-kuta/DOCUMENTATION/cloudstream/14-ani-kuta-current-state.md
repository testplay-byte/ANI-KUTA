# 14 — ANI-KUTA Current Extension Architecture (the "this side of the bridge" reference)

> **Scope**: the ANI-KUTA extension system as it exists in code TODAY — the exact surface a second
> (CloudStream) extension system must integrate with. Docs 01–13 covered the CS3 side; this doc covers
> OUR side, read from source. Written for **Batch 4 integration planning**.
> **Method**: every claim about our code cites `file:line` (paths relative to
> `ANI-KUTA/APP/ani-kuta/`, all `[verified]` by direct read during this task unless marked
> `[inferred]` / `[open-question]`). CS3-side facts are cited to their docs (01–13), not re-derived.
> **Companion docs**: repo-format contrast → doc 04 §8; DB deep-dive → doc 15 (B3-e, planned);
> plugin-settings CS3 side → doc 11.

---

## 0. Executive summary

ANI-KUTA runs ONE extension ecosystem: **Aniyomi-compatible APK extensions**, loaded via a
parent-first `PathClassLoader`, gated by a per-package trust check, sourced from user-added
`index.json` repos, and surfaced to the UI as live `AnimeSource` instances inside a single
reactive `ExtensionManager`.

Two parallel abstractions exist over that ecosystem:

1. **The de-facto path** — features (`anime-search`, `anime-details`, `extensions-settings`,
   `core:smart-matcher`) inject `ExtensionManager` directly and call Aniyomi types
   (`AnimeCatalogueSource.getPopularAnime/getSearchAnime/getEpisodeList`, `VideoResolver.resolve`)
   themselves. This is how the app actually works today.
2. **The designed path** — `:core:provider-api` (D-031 scaffold → D-302 "made real") defines an
   app-owned `VideoExtensionProvider` interface + `Source/SourceContent/SourceContentDetails/
   SourceEpisode/SourceVideo` models, implemented once by `AniyomiExtensionProvider` and registered
   in Koin as a **single binding**. **Zero feature code consumes it yet** `[verified]` — a repo-wide
   search finds `com.confused.anikuta.core.providerapi` referenced only by
   `AniyomiExtensionProvider.kt:4-9` (the implementation) and `ExtensionModule.kt:33` (the Koin
   registration).

So the honest answer to "are we structured for multiple providers?" is: **the interface seam exists
and is clean (no third-party types cross it), but the wiring is single-provider** — one Koin
`single<VideoExtensionProvider>`, no list/set multi-binding, and all live consumers still talk
Aniyomi directly. Section 9 maps exactly where a `CloudStreamProvider` would plug in.

### The VideoExtensionProvider contract in 5 bullets (the designed second-provider seam)

1. **Identity** — `ecosystemId: String` + `displayName: String` + `supportedContentTypes:
   Set<ContentType>` on the sealed `ExtensionProvider` base (`ExtensionProvider.kt:19-29`); the
   Aniyomi impl reports `"aniyomi"` / `"Aniyomi extensions"` / `{VIDEO}`
   (`AniyomiExtensionProvider.kt:40-42`).
2. **Content queries** — `observeInstalledSources(): Flow<List<Source>>`, `fetchContentList(source,
   page, query)`, `fetchContentDetails(content)`, `fetchEpisodeList(content)`,
   `fetchVideoList(episode)` — all returning cold `Flow`s of app-owned models
   (`VideoExtensionProvider.kt:28-54`).
3. **Lifecycle management (D-302)** — `install(pkgName)`, `uninstall(pkgName)`,
   `setEnabled(pkgName, enabled)`, `checkForUpdates()`; terminal install state arrives
   asynchronously via `observeInstalledSources` (`VideoExtensionProvider.kt:63-72`).
4. **Keying discipline** — `Source.key = "<ecosystemId>:<sourceId>"`,
   `SourceContent.contentKey = "<sourceKey>:<externalId>"`, `SourceEpisode.episodeKey =
   "<contentKey>:<externalId>"` (`Source.kt:23`, `SourceContent.kt:23`, `SourceEpisode.kt:27`) —
   ecosystem-prefixed composite keys designed to be unique across ecosystems.
5. **No third-party types cross the boundary** — the interface and all five models live in
   `:core:provider-api` (deps: only `:core:common` + coroutines, `provider-api/build.gradle.kts`) —
   an implementation contract any ecosystem facade can satisfy. Caveat: `SourceVideo` is currently
   too thin for real playback (no headers / subtitle / audio tracks — see §6.4).

---

## 1. Module map

Extension-relevant modules (roles + dependency direction), all under `ANI-KUTA/APP/ani-kuta/`:

| Module | Role | Key files |
|---|---|---|
| `:core:provider-api` | The app-owned provider abstraction (D-031/D-302). 8 files: `ExtensionProvider`, `VideoExtensionProvider`, `FutureProviders`, 5 data models. Deps: `:core:common` + coroutines only | `core/provider-api/src/main/java/com/confused/anikuta/core/providerapi/` |
| `:core:source-api` | The vendored **Aniyomi source API** (`eu.kanade.tachiyomi.animesource.*`) — `AnimeSource`, `AnimeCatalogueSource`, `AnimeHttpSource`, `SAnime`/`SEpisode`/`Video`/`Hoster`, OkHttp network stack + Cloudflare handling. This is what extension APKs compile against and what the host resolves their classes against | `core/source-api/src/main/kotlin/eu/kanade/tachiyomi/…` |
| `:data:extension` | The Aniyomi ecosystem implementation: loader, installer, trust, repos, manager, and the two provider facades (`AniyomiExtensionProvider` → `VideoExtensionProvider`; `ExtensionDetailsProvider` → `AnimeDetailsProvider`) | `data/extension/src/main/java/com/confused/anikuta/data/extension/` |
| `:core:video-resolver` | Turns a resolved episode into playable videos: `VideoResolver` (hoster-list → flat videos + 3-tier Server/Audio/Quality tree), `ResolvedVideosRegistry` (in-memory handoff to the watch screen) | `core/video-resolver/…/VideoResolver.kt`, `ResolverTypes.kt`, `ResolvedVideosRegistry.kt` |
| `:core:smart-matcher` | Title matching for auto-link. **Depends on `:data:extension`** (reverse auto-link searches extension sources) | `core/smart-matcher/…/ReverseAutoLinkService.kt:37` |
| `:core:preferences` | `AppPreferences.enabledExtensions` (per-package enable set), `PreferenceStore` (trust storage, source links, search prefs) | `core/preferences/…/AppPreferences.kt:42-53` |
| `:feature:anime-search:impl` | Search screen; ANILIST + EXTENSION modes; source picker | `SearchViewModel.kt`, `ExtensionSourcePickerSheet.kt` |
| `:feature:anime-details:impl` | Details screen; metadata merge, episode list, resolver sheet, auto-link, downloads, tracking | `DetailsViewModel.kt` (3,712 lines), `DetailsScreen.kt` (3,634), `ResolverSheet.kt` |
| `:feature:extensions-settings:impl` | Extensions list screen (trust/available/installed/errored), repo settings, extension detail, source preferences | `ExtensionsSettingsScreen.kt` (1,297), `ExtensionDetailScreen.kt` (487), `SourcePreferencesScreen.kt` (772) |
| `:feature:anime-browse:impl` | Browse screen — **AniList-only**; consumes no extensions at all `[verified]` (`BrowseViewModel.kt:40-43` injects only `AniListApi` + `DataCacheRepository`) |
| `:feature:watch:impl` | Player screen; consumes the resolver output via `ResolvedVideosRegistry` + a 14-arg nav key — does not touch `:data:extension` | `WatchScreen.kt:478-480` |

Dependency graph (arrows = "depends on", from `build.gradle.kts` of each module `[verified]`):

```
 :app ──────────────────────────────── (Koin assembly: extensionModule, feature modules)
   │
   ├─► :feature:anime-search:impl ──────► :data:extension, :core:source-api, :core:anilist, …
   ├─► :feature:anime-details:impl ─────► :data:extension, :core:source-api, :core:video-resolver,
   │                                      :core:smart-matcher, :core:content, :core:metadata, …
   ├─► :feature:extensions-settings:impl► :data:extension, :core:source-api, :core:preferences
   └─► :feature:watch:impl ─────────────► :core:video-resolver (registry only)

 :data:extension ──► :core:provider-api        (facade implements the interface)
                 ──► :core:source-api          (Aniyomi API types)
                 ──► :core:common, :core:network, :core:preferences, :core:database

 :core:provider-api ──► :core:common + coroutines   (NO Aniyomi deps — clean seam)

 :core:smart-matcher ──► :data:extension + :core:source-api   ← ⚠ a :core: module depending on
                                                               :data: (layering inversion,
                                                               couples matcher to Aniyomi)
 :core:video-resolver ──► :core:source-api      (AnimeHttpSource + SEpisode in its signatures)
```

Two structural observations for Batch 4:

- **`:core:provider-api` is dependency-clean** (no Aniyomi types) — a CloudStream facade module
  could depend on it without dragging in the Aniyomi stack `[verified]`.
- **`:core:smart-matcher` and `:core:video-resolver` are Aniyomi-typed** — a second ecosystem cannot
  reuse them as-is; their signatures take `AnimeCatalogueSource` / `AnimeHttpSource` / `SEpisode`
  (`ReverseAutoLinkService.kt:96,115`, `VideoResolver.kt:51-53`) `[verified]`.

---

## 2. Extension file & loading pipeline

### 2.1 What an extension IS (the APK contract)

An extension is an ordinary installed APK whose manifest declares
(`ExtensionLoader.kt:19-25,56-69`):

- `<uses-feature android:name="tachiyomi.animeextension"/>` — the feature flag that makes
  `PackageManager` queries find it (`EXTENSION_FEATURE`, checked in `isPackageAnExtension`,
  `ExtensionLoader.kt:256-258`).
- `<meta-data android:name="tachiyomi.animeextension.class" android:value="FQCN;FQCN…"/>` —
  semicolon-separated source class names (`METADATA_SOURCE_CLASS`, `ExtensionLoader.kt:185-189`).
- `<meta-data android:name="tachiyomi.animeextension.nsfw"/>` (1 = NSFW) and
  `tachiyomi.animeextension.torrent` (1 = torrent) (`ExtensionLoader.kt:66-69,183-184`).

The keys MUST match the Aniyomi convention exactly — wrong keys make extensions invisible (D-027,
noted `ExtensionLoader.kt:23-25`).

### 2.2 The pipeline

```
 [Repo index]            [Download]              [OS Install]                [Load]                  [Use]
 index.json ──parse──► Available ──install──► cacheDir/ext-<pkg>-<apk> ──► PackageInstaller ──► PackageManager scan ──► PathClassLoader ──► AnimeSource instances
 (§4)        ExtensionRepoApi      (OkHttp,   (foreground service +        (loadAll on start /      (trust gate,           (registered into
                                    D-309 %)   PackageInstallerBackend)     package broadcast)       lib-version note)      ExtensionManager._sources)
```

**Install flow** (D-300 single canonical path):

1. `ExtensionManager.installExtension(Available)` → resolves the APK URL via
   `AnimeExtensionApi.getApkUrl` (`ExtensionManager.kt:387-388`) and serializes installs with an
   app-wide `Mutex` (`installMutex`, `ExtensionManager.kt:410`).
2. `ExtensionInstaller.downloadAndInstall` streams the APK with OkHttp into
   `cacheDir/ext-<pkgName>-<apkName>` emitting `InstallStep.Downloading(progress)` at most every
   200 ms (progress 0–100, or −1 for unknown size) (`ExtensionInstaller.kt:59-90,125-170`; D-309).
3. It dispatches `ExtensionInstallService` (a `dataSync` foreground service,
   `ExtensionInstallService.kt:29,69,125-138`), which hosts `PackageInstallerBackend`:
   one `PackageInstaller` session (`MODE_FULL_INSTALL`, `USER_ACTION_NOT_REQUIRED` on S+), APK
   streamed in, committed with a `PendingIntent` broadcast receiver
   (`PackageInstallerBackend.kt:83-110`). `STATUS_PENDING_USER_ACTION` launches the OS confirm
   dialog; user-abort maps to `InstallStep.Idle`, other failures to `InstallStep.Error`
   (`PackageInstallerBackend.kt:47-69`).
4. Terminal results are reported back to the manager via Koin `GlobalContext`
   (`ExtensionInstallService.kt:85-91`) → `ExtensionManager.onInstallResult`, which triggers an
   immediate `loadAll()` re-scan on success (D-311) and unsticks frozen "Installing" rows on
   deny/fail (D-309 review fix) (`ExtensionManager.kt:429-437`). The OS `PACKAGE_ADDED/
   REPLACED/REMOVED` broadcast (`ExtensionInstallReceiver`, registered dynamically in the manager's
   init, `ExtensionManager.kt:88-94`, `ExtensionInstallReceiver.kt:31-63`) is the other re-scan
   trigger; the receiver suppresses the spurious REMOVED+ADDED pair during replaces
   (`ExtensionInstallReceiver.kt:54-57`).
5. Cancellation (user leaves screen mid-download) resets the row to `Idle`
   (`ExtensionManager.kt:398-405`).

`InstallStep` is a sealed interface: `Idle / Pending / Downloading(progress) / Installing /
Installed / Error` (`InstallStep.kt:22-33`), tracked per-package in `_installStates`
(`ExtensionManager.kt:85-86`).

**Uninstall** = system `ACTION_DELETE` intent, with an `ACTION_APPLICATION_DETAILS_SETTINGS`
fallback on Android 11+ package-visibility quirks (`ExtensionInstaller.kt:93-118`).

### 2.3 The trust gate

Per-package, NOT per-signer (Phase 3 fix — the old by-signer model auto-propagated trust to every
same-signer extension; `TrustService.kt:9-18`):

- The loader SHA-256-hashes the signing certificate (must be signed at all, else
  `LoadResult.Error("Package not signed")`, `ExtensionLoader.kt:145-149,299-317`).
- `TrustService.isTrusted(pkgName)` checks a comma-joined package-name set persisted in
  `PreferenceStore` key `trusted_extension_packages` (`TrustService.kt:28-47,73-75`).
- Untrusted → `LoadResult.Untrusted` with the signature hash carried for display
  (`ExtensionLoader.kt:154-168`). `trustExtension` grants trust, enables the package, and re-loads
  off the main thread (`ExtensionManager.kt:223-239`); `untrustExtension` accepts any
  `AnimeExtension` (including `Errored`, D-296) and re-scans (`ExtensionManager.kt:301-317`).
- Security trade-off is documented in-code: the user trusts each package **by name**; a same-name
  re-signed replacement would still pass (`TrustService.kt:14-18`) `[verified — known limitation]`.

### 2.4 The loader (D-294 parent-first classloader; D-297 lib version)

`ExtensionLoader.loadAll()` queries all installed packages for the extension feature and maps each
to a `LoadResult` (`ExtensionLoader.kt:83-100`). Per extension
(`loadExtensionInternal`, `ExtensionLoader.kt:128-251`):

1. Strip `"Aniyomi: "` / `"Animiru: "` label prefixes for the display name (`:134`).
2. `libVersion = versionName.substringBeforeLast('.').toDoubleOrNull() ?: 0.0` (`:142`) —
   `"1.4.3"` → `1.4`.
3. Trust gate (§2.3).
4. **Lib-version range is informational only**: `LIB_VERSION_MIN = 12.0`, `LIB_VERSION_MAX = 17.0`
   (`ExtensionLoader.kt:75-76`); out-of-range versions log "attempting load anyway"
   (`:175-180`) — D-297: the range is documentation, not a gate; failure surfaces as a visible
   Errored row.
5. **Parent-first `PathClassLoader`** (D-294 root fix for "extensions disappear after trust"):

   ```kotlin
   // ExtensionLoader.kt:196-201
   val classLoader = try {
       PathClassLoader(appInfo.sourceDir, appInfo.nativeLibraryDir, context.classLoader)
   } catch (e: Exception) { … }
   ```

   The old child-first loader let an extension's PARTIAL bundled kotlin-stdlib shadow the host's
   complete stdlib → mixed-stdlib class-identity breakage at source instantiation
   (`ExtensionLoader.kt:40-47`). Parent-first means the extension APK only supplies classes the
   host lacks (its own sources, bundled extractors, multisrc themes…).
6. Each declared FQCN is instantiated per-class (`instantiateSource`, `:271-294`): `AnimeSourceFactory`
   → `createSources()`, plain `AnimeSource` → no-arg constructor, anything else → Failure. Failures
   are collected with **exception class + message** (D-295), catching `Throwable` because binary
   incompatibility throws `NoClassDefFoundError` (`:287-293`). If no source instantiated:
   `LoadResult.Error(pkgName, reason, extName)` with the joined real reasons (`:216-224`).
7. `Installed.lang` = most common non-blank source lang, fallback first source lang (D-298)
   (`:229-234`).

### 2.5 LoadResult states → UI states (D-295/D-296)

`LoadResult` (`LoadResult.kt:8-27`): `Success(extension: Installed)` / `Untrusted(extension:
Untrusted)` / `Error(packageName, message, name)` / `UnrecognizedExtension`.

`ExtensionManager.loadAll` partitions results into four reactive `StateFlow`s
(`ExtensionManager.kt:127-165`):

- `Success` → `_installedExtensions` (marked with `isEnabled` from `AppPreferences`; only enabled
  extensions' sources are registered into `_sources: Map<Long, AnimeSource>` — `:134-144`);
  on first launch after the per-package-enable upgrade, the enabled set is seeded with all trusted
  pkgNames (backward compat, `:109-121`).
- `Untrusted` → `_untrustedExtensions`.
- `Error` → `_erroredExtensions` **— never silently dropped** (D-296): the extensions screen shows
  a "Failed to Load" section with the reason + Retry / Untrust / Uninstall
  (`ExtensionsSettingsScreen.kt:335-353`). `trustExtension`/`retryExtension` share
  `applyLoadResult` which routes every branch (`ExtensionManager.kt:242-273`).
- `UnrecognizedExtension` → skipped (`:156-158`).

Other manager surface: `enableExtension`/`disableExtension` (per-package, sources in/out of
`_sources` without unloading, `:326-355`), `enableSource`/`disableSource` (per-source, Phase 4,
`:360-375`), `getSource(id)` / `getAllSources()` (`:481-483`), `reload()` (`:487-490`).

---

## 3. The AnimeExtension model

Sealed hierarchy (`model/AnimeExtension.kt:23-125`); abstract fields on the base: `name`,
`pkgName`, `versionName`, `versionCode: Long`, `libVersion: Double`, `lang: String?`, `isNsfw`,
`isTorrent`.

| State | Meaning | Extra fields | Produced by |
|---|---|---|---|
| `Installed` (`:44-60`) | trusted + loaded, live sources | `sources: List<AnimeSource>`, `icon: Drawable?`, `signatureHash`, `hasUpdate`, `isObsolete`, `repoUrl`, **`isEnabled: Boolean = true`** | loader Success (`ExtensionLoader.kt:235-247`); `hasUpdate`/`isObsolete` recomputed by `updateInstalledStatuses` against the repo index (`ExtensionManager.kt:199-212`); `isEnabled` stamped from prefs (`:134-135`) |
| `Available` (`:63-84`) | listed in a repo, not installed | `sources: List<AnimeSourceMetadata>` (lightweight `id/lang/name/baseUrl`, `:78-83` — no live instance), `apkName`, `iconUrl`, `repoUrl` | repo index parse (`ExtensionRepoApi.kt:109-129`) |
| `Untrusted` (`:87-98`) | installed, signature not yet trusted | `signatureHash`, `icon` | loader trust-gate (`ExtensionLoader.kt:157-167`) |
| `Errored` (`:107-118`) | installed + trusted but failed to LOAD (D-296) | `message` (real failure reason), `icon` | `LoadResult.Error.toErrored()` (`ExtensionManager.kt:287-294`) — version fields are zeroed (`versionName = ""`, `versionCode = 0`, `libVersion = 0.0`) because the loader failed before reading them |

Notes:

- `isEnabled` is the per-package user control, independent of trust (trust = can load at all;
  enabled = sources appear in pickers) — the model doc comment spells out the two-axis design
  (`AnimeExtension.kt:33-43`).
- `companion.parseLibVersion(versionName)` (`:120-124`) duplicates the repo-side lib parsing with a
  different null fallback (−1.0 vs 0.0) — cosmetic inconsistency `[verified]`.
- The four states map 1:1 to the four sections of the extensions screen (§7.4).

---

## 4. The repo system

### 4.1 The repo URL contract (ours)

`ExtensionRepo` (`repo/ExtensionRepo.kt:20-34`):

- `<baseUrl>/index.json` — the extension list (`indexUrl`, `:26-27`)
- `<baseUrl>/apk/<apkName>` — APKs (`apkUrl`, `:29-30`)
- `<baseUrl>/icon/<pkg>.png` — icons (`iconUrlFor`, `:32-33`)
- `<baseUrl>/repo.json` — **optional** repo metadata (`{ "meta": { "name", "website" } }`,
  `ExtensionRepoApi.kt:164-171`) — ⚠ same filename as CS3's primary manifest, completely different
  schema (doc 04 §8 notes the collision).
- **No default repos** (D-043) — user adds their own (`ExtensionRepo.kt:14`).

### 4.2 index.json entry format (ours) vs CS3

Our entry DTO (`ExtensionRepoApi.kt:139-152`):

```kotlin
@Serializable
internal data class RepoIndexEntry(
    val name: String,      // "Aniyomi: <name>" prefix stripped on parse (:115)
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,        // numeric version code — update comparison
    val version: String,   // "<libMajor.libMinor.patch>" — lib version parsed from this
    val nsfw: Int = 0,
    val torrent: Int = 0,
    val sources: List<RepoIndexSource>? = null,  // optional source metadata (id/lang/name/baseUrl)
)
```

Contrast with CS3's two-level `repo.json` → `pluginLists[]` → `plugins.json` (absolute URLs,
`internalName` keying, single Int `version`, `apiVersion` dead at runtime): full comparison in
**doc 04 §8** (`04-extension-repositories.md:584-625`) — not duplicated here. Two corrections /
updates to doc 04 §8 from this pass:

- doc 04 says our update comparison happens "in the installer layer (doc 14)": actually it's in the
  **manager** — `updateInstalledStatuses` compares `av.versionCode > installed.versionCode` and sets
  `isObsolete = av == null` (`ExtensionManager.kt:203-210`) `[verified]`.
- doc 04's "lib version range 12.0–16.0" is correct **for the repo filter** but the **loader** now
  accepts 12.0–17.0 (D-297). See the discrepancy note below.

### 4.3 ⚠ The lib-version double gate (a real inconsistency)

- **Repo side (hard filter)**: `ExtensionRepoApi` keeps `LIB_MIN = 12.0`, `LIB_MAX = 16.0`
  (`ExtensionRepoApi.kt:29-31`) and **filters entries out** in `parseIndex`
  (`:112`) and `verifyRepo` (`:88-91`). A lib-17 extension in a repo index is invisible in the
  Available list.
- **Loader side (soft note)**: `LIB_VERSION_MIN = 12.0`, `LIB_VERSION_MAX = 17.0`, out-of-range
  still attempted (`ExtensionLoader.kt:75-76,175-180`, D-297).

Net effect: a lib-17 extension **loads fine if sideloaded** but **cannot be installed from any
repo** — the repo filter was not updated alongside D-297 `[verified]`. Batch 4 relevance: when a
CloudStream repo layer is added, do not replicate this split-brain; keep compat gating in ONE
place. `[open-question]` should ExtensionRepoApi's LIB_MAX be bumped to 17.0 to match D-297?

### 4.4 Verification, storage, aggregation, updates

- **Add-repo verification** (`verifyRepo`, `ExtensionRepoApi.kt:60-106`): normalize URL (strip
  trailing `/index.json` variants), fetch `index.min.json` then `index.json`, parse as
  `List<RepoIndexEntry>`, require non-empty, count lib-compatible entries, optionally fetch
  `repo.json` for name/website → `RepoVerificationResult.Success(cleanUrl, repoName, website,
  extensionCount)` or `Error(message)` (`RepoVerificationResult.kt:9-18`). No APK hash verification
  anywhere in the repo layer (doc 04 §8 documents the CS3 contrast — CS3 SHA-256-verifies every
  download).
- **Storage**: `ExtensionRepoRepository` persists the repo list as JSON in SharedPreferences
  `anikuta_extension_repos`/`repos_json` (deliberately NOT SQLDelight — keeps `:data:extension`
  free of `:core:database`; `ExtensionRepoRepository.kt:11-15,26-27`), exposed as `StateFlow`
  (`:33-34`), insert/delete dedup by `baseUrl` (`:41-58`).
- **Aggregation**: `AnimeExtensionApi.findAvailableExtensions` fetches all repos in parallel
  (`async/awaitAll`), **dedupes by pkgName, first repo wins** (`AnimeExtensionApi.kt:32-48`);
  `getApkUrl` rebuilds `<repo>/apk/<apkName>` (`:51-52`). (Note the class name is misleading —
  `AnimeExtensionApi` is the repo orchestrator only; it exposes no source/content methods
  `[verified]`.)
- **Update checks (D-301)**: entering the extensions screen calls
  `ExtensionManager.checkForUpdates()` (`ExtensionsSettingsScreen.kt:150-154`), throttled to one
  check / 30 min (`UPDATE_CHECK_THROTTLE_MS`, `ExtensionManager.kt:52-53,462-477`), non-blocking,
  with `UpdateCheckState { Idle, Checking }` for a subtle indicator (`:449-452`); repo-set changes
  force a fresh check (`ExtensionsSettingsScreen.kt:157-161`). `hasUpdate` then surfaces as an
  Update pill on installed rows (D-301/D-309, `ExtensionsSettingsScreen.kt:892`).

---

## 5. The aniyomi source surface

### 5.1 What a loaded source exposes (`:core:source-api`)

The Aniyomi API our host ships (the same API extension APKs compile against — parent-first loading
guarantees host classes win, §2.4):

| Type | Members (video-relevant) | Citation |
|---|---|---|
| `AnimeSource` (base) | `id: Long`, `name: String`, `lang: String` (default ""); suspend `getAnimeDetails(SAnime): SAnime`, `getEpisodeList(SAnime): List<SEpisode>`; **lib-16+** `getSeasonList(SAnime)`, `getHosterList(SEpisode): List<Hoster>`, `getVideoList(Hoster): List<Video>` (default: throw "Not used") | `AnimeSource.kt:13-78` |
| `AnimeCatalogueSource` | `lang` (abstract), `supportsLatest: Boolean`; suspend `getPopularAnime(page): AnimesPage`, `getSearchAnime(page, query, filters): AnimesPage`, `getLatestUpdates(page): AnimesPage`; `getFilterList(): AnimeFilterList` | `AnimeCatalogueSource.kt:8-58` |
| `AnimeHttpSource` | + `baseUrl`, OkHttp client, Cloudflare interception (`CloudflareException`), WebView helpers | `core/source-api/…/online/AnimeHttpSource.kt` |
| `ConfigurableAnimeSource` | + `setupPreferenceScreen(screen)` — aniyomi's declarative settings tree | imported at `ExtensionDetailScreen.kt:57`, rendered by `SourcePreferencesScreen` (§7.4) |

**What our code actually calls** (the full surface a second provider would have to match for
feature parity):

- `getPopularAnime(1)` + `getSearchAnime(1, q, AnimeFilterList())` — SearchViewModel
  (`SearchViewModel.kt:512,586`), AniyomiExtensionProvider (`AniyomiExtensionProvider.kt:57-61`),
  DetailsViewModel manual search (`DetailsViewModel.kt:3427`), ReverseAutoLinkService
  (`ReverseAutoLinkService.kt:115`).
- `getAnimeDetails(SAnime)` — AniyomiExtensionProvider (`:76`), ExtensionDetailsProvider
  (`:51`).
- `getEpisodeList(SAnime)` — DetailsViewModel (`:1329,3089`), AniyomiExtensionProvider (`:96`).
- `getHosterList(episode)` then per-hoster `getVideoList(hoster)`, falling back to
  `getVideoList(episode)` (lib < 16) — VideoResolver (`VideoResolver.kt:133-187`).
- `getLatestUpdates` / `getSeasonList` / `getFilterList` — **declared but never called by our
  code** `[verified]` (grep finds only declarations/overrides inside `:core:source-api` itself:
  `AnimeCatalogueSource.kt:51,58`, `AnimeSource.kt:59`, `AnimeHttpSource.kt:329,721`).

### 5.2 How sources reach the UI

- Single source of truth: `ExtensionManager.sources: StateFlow<Map<Long, AnimeSource>>` — only
  enabled extensions' sources are registered (`ExtensionManager.kt:74-75,134-144`).
- **Search source picker**: `SearchViewModel.trustedSources` filters the map to
  `AnimeCatalogueSource`s (`SearchViewModel.kt:117-120`); the picker sheet lists name + extension
  icon only (`ExtensionSourcePickerSheet.kt:96-107`); the selection is persisted as a **raw Long
  source id** in pref `search_selected_extension_source_id` (`SearchViewModel.kt:57,141-144,316-324`)
  and auto-selects the first source when none chosen (`:153-175`). ⚠ Long ids are Aniyomi-ecosystem
  ids; a second ecosystem needs its own keying (§9).
- **Language filter (D-298)**: the extensions screen derives distinct langs across all four
  sections and applies one `langFilter` to every section
  (`ExtensionsSettingsScreen.kt:171-205`); `Installed.lang` is populated by the loader (§2.4). The
  SEARCH picker has no language filter (rows show name only, per user spec,
  `ExtensionSourcePickerSheet.kt:45`) `[verified]`.

---

## 6. The provider-api contract (D-031 / D-302) — THE KEY SECTION

`:core:provider-api` was scaffolded by D-031 ("the app must support multiple extension ecosystems…
Each ecosystem = one provider impl. Database stores which provider a source came from",
`AGENT-CONTEXT/memory/decisions.md:204-210`) and made real by D-302 (`decisions.md:2335-2340`).

### 6.1 `ExtensionProvider` (the base) — full quote

```kotlin
// core/provider-api/.../ExtensionProvider.kt:19-29
sealed interface ExtensionProvider {
    /** Unique identifier for this ecosystem (e.g., "aniyomi", "mangayomi"). */
    val ecosystemId: String
    /** Human-readable name for display in settings. */
    val displayName: String
    /** Which content types this provider supports. */
    val supportedContentTypes: Set<ContentType>
}
```

(`ContentType` currently has a single value `VIDEO // anime, movies, series`,
`core/common/.../ContentType.kt:11-12` — the enum is the declared growth point for image/text.)

### 6.2 `VideoExtensionProvider` — full quote (the designed second-provider seam)

```kotlin
// core/provider-api/.../VideoExtensionProvider.kt:22-73
interface VideoExtensionProvider : ExtensionProvider {

    /**
     * Observe the list of installed sources for this ecosystem.
     * Reactive — emits whenever the installed sources change (CORE_RULES §23).
     */
    fun observeInstalledSources(): Flow<List<Source>>

    /**
     * Fetch a page of content from a source (browse/search).
     *
     * @param source The source to fetch from.
     * @param page Page number (1-based).
     * @param query Optional search query. Null = browse mode.
     * @return A list of content items from this page.
     */
    fun fetchContentList(source: Source, page: Int, query: String? = null): Flow<List<SourceContent>>

    /**
     * Fetch detailed metadata for a specific content item.
     */
    fun fetchContentDetails(content: SourceContent): Flow<SourceContentDetails>

    /**
     * Fetch the episode list for a content item.
     */
    fun fetchEpisodeList(content: SourceContent): Flow<List<SourceEpisode>>

    /**
     * Fetch the list of playable videos for an episode.
     * Multiple videos = multiple quality options or hosting sources.
     */
    fun fetchVideoList(episode: SourceEpisode): Flow<List<SourceVideo>>

    // ── Lifecycle management (D-302) ──────────────────────────────────────────

    /**
     * Install (or update) an extension by package name from its repository.
     * Implementations handle download + installer dispatch; the terminal state
     * arrives asynchronously through [observeInstalledSources].
     */
    fun install(pkgName: String)

    /** Uninstall an extension by package name. */
    fun uninstall(pkgName: String)

    /** Enable/disable a package's sources without uninstalling. */
    fun setEnabled(pkgName: String, enabled: Boolean)

    /** Trigger an update check against the configured repositories. */
    fun checkForUpdates()
}
```

Design notes: (a) content queries are cold `Flow`s that emit once and complete — no pagination
cursor, no error channel (exceptions propagate through the flow) `[verified]`; (b) lifecycle
methods are fire-and-forget — `install` triggers the async pipeline and state lands via
`observeInstalledSources`; (c) `install` takes a **package name**, presupposing an
APK-package-shaped ecosystem (a CloudStream `.cs3`/jar plugin is NOT an Android package — a CS3
facade would have to reinterpret this as plugin-name, or the interface needs a generic handle
`[open-question]`).

### 6.3 The data models (full field lists)

| Model | Fields | Computed key | Citation |
|---|---|---|---|
| `Source` | `ecosystemId: String`, `sourceId: String`, `name: String`, `lang: String = "en"`, `isNsfw: Boolean = false` | `key = "$ecosystemId:$sourceId"` | `Source.kt:12-24` |
| `SourceContent` | `sourceKey: String`, `externalId: String`, `title: String`, `thumbnailUrl: String?`, `url: String?` | `contentKey = "$sourceKey:$externalId"` ("temporary content_key used in the database until the identity system is built") | `SourceContent.kt:12-24` |
| `SourceContentDetails` | `sourceKey`, `externalId`, `title`, `description?`, `genres: List<String>?`, `status: String?`, `thumbnailUrl?`, `bannerUrl?`, `year: Int?`, `author?`, `artist?`, `episodes: List<SourceEpisode> = emptyList()` | `contentKey` | `SourceContentDetails.kt:6-21` |
| `SourceEpisode` | `contentKey: String`, `externalId: String`, `number: Double` (decimals for specials "5.5"), `name: String`, `url: String?`, `thumbnailUrl: String?`, `dateUpload: Long?` (epoch millis) | `episodeKey = "$contentKey:$externalId"` | `SourceEpisode.kt:14-28` |
| `SourceVideo` | `url: String`, `quality: String = "Default"`, `videoUrl: String?` ("direct video URL — some sources need resolution") | — | `SourceVideo.kt:10-14` |
| (future) `SourceChapter` / `SourcePage` | declared for the Image provider; also `ImageExtensionProvider` / `TextExtensionProvider` interfaces — "Not implemented in Phase 3 — defined here so the architecture is ready" | — | `FutureProviders.kt:14-47` |

Key-discipline quotes (`Source.kt:19-23`):

```kotlin
/**
 * The content key for this source: "<ecosystemId>:<sourceId>".
 * Used as a prefix for content_key values in the database.
 */
val key: String get() = "$ecosystemId:$sourceId"
```

### 6.4 The bridge: `AniyomiExtensionProvider` (AnimeSource → provider models)

`AniyomiExtensionProvider(manager: ExtensionManager) : VideoExtensionProvider`
(`AniyomiExtensionProvider.kt:36-38`), `ecosystemId = "aniyomi"`. Mapping table:

| Provider call | Aniyomi calls | Mapping notes |
|---|---|---|
| `observeInstalledSources()` | `manager.sources.map { … }` | every `AnimeSource` → `Source(ecosystemId, sourceId = id.toString(), name, lang = lang.ifBlank { "all" })`, sorted by name (`:46-51,169-174`) |
| `fetchContentList(source, page, query)` | `AnimeCatalogueSource.getPopularAnime(page)` / `getSearchAnime(page, query, AnimeFilterList())` | query blank → popular; result `AnimesPage.animes` → `SourceContent(sourceKey = source.key, externalId = anime.url, title, thumbnailUrl, url)` (`:53-71`) |
| `fetchContentDetails(content)` | `getAnimeDetails(SAnime{url = externalId})` | `genre.split(", ")`; `status` Int → display string ("Ongoing"/"Completed"/… via `toDisplayString`, `:183-192`); `background_url` → bannerUrl; **`year` and `episodes` never populated** `[verified]` |
| `fetchEpisodeList(content)` | `getEpisodeList(SAnime{url})` | `SEpisode` → `SourceEpisode(contentKey, externalId = url, number = episode_number.toDouble(), name, url, thumbnailUrl = preview_url, dateUpload = date_upload.takeIf { it > 0 })` (`:93-110`) |
| `fetchVideoList(episode)` | `source.getVideoList(SEpisode{url})` — the **legacy** episode-level API only, NOT the lib-16 hoster API | source key recovered by `episode.contentKey.split(':').take(2)` ("ecosystem ids contain no ':', source ids are numeric", `:113-115`); `Video` → `SourceVideo(url = videoUrl, quality = videoTitle, videoUrl = videoUrl.takeIf { isNotBlank })` (`:112-128`) |
| `install(pkgName)` | `manager.installExtension(available)` | resolves the newest `Available` from `manager.availableExtensions`; if absent just triggers `manager.checkForUpdates()` (install can be retried after refresh); collects the flow in a private `installScope(Dispatchers.Default)` (`:132-144,167`) |
| `uninstall(pkgName)` | `manager.uninstallExtension(installed)` | falls back to `manager.installer.uninstallApk(pkgName)` when not in the installed list (`:146-155`) |
| `setEnabled(pkgName, enabled)` | `manager.enableExtension` / `disableExtension` | (`:157-159`) |
| `checkForUpdates()` | `manager.checkForUpdates(force = true)` | (`:161-163`) |
| (helper) `resolveSource(sourceKey)` | `manager.getSource(id)` | `sourceKey.substringAfterLast(':').toLongOrNull()` (`:177-180`) |

Two contract gaps a second provider will expose `[verified]`:

1. **`SourceVideo` cannot drive real playback.** It carries no HTTP headers, no subtitle tracks,
   no audio tracks — but `Video` has all three and the REAL playback path uses them
   (`VideoResolver` maps `video.headers`, `video.subtitleTracks`, `video.audioTracks` into
   `ResolvedVideo`, `VideoResolver.kt:83-90,249-260`). `fetchVideoList`'s output is a browsing-grade
   list; the de-facto resolver path (§7.3) is richer. A CloudStream facade must either extend
   `SourceVideo` or route playback through an equivalent of `VideoResolver`
   `[open-question for Batch 4]`.
2. **`fetchVideoList` uses only the legacy episode-level `getVideoList`** — the hoster-based
   lib-16 flow (which the de-facto path prefers) is not represented in the provider interface; a
   CloudStream facade's `loadLinks`-equivalent has no natural home in the current method set.

### 6.5 Koin registration — single binding, zero consumers

```kotlin
// data/extension/.../ExtensionModule.kt:31-35
// D-302: the provider-api facade — the app-owned abstraction over the Aniyomi
// ecosystem. New consumers depend on VideoExtensionProvider, not the manager.
single<com.confused.anikuta.core.providerapi.VideoExtensionProvider> {
    com.confused.anikuta.data.extension.provider.AniyomiExtensionProvider(get())
}
```

- It is a **single binding** — not `List<VideoExtensionProvider>` (D-034 records that "Koin's
  `List<T>` multi-binding is cleaner" as an aspiration, `decisions.md:~218`, but no list binding
  exists anywhere yet `[verified]`).
- **No feature code injects it.** A repo-wide search for `com.confused.anikuta.core.providerapi`
  (imports + fully-qualified usages) matches exactly two files: `AniyomiExtensionProvider.kt:4-9`
  (the implementation) and `ExtensionModule.kt:33` (the Koin registration itself) `[verified]`.
  `VideoExtensionProvider` / `Source` / etc. appear in no ViewModel, no screen. The D-302 text
  itself says "Existing consumers unchanged (they keep using the manager directly)"
  (`decisions.md:2337`) — i.e. the interface is the *designated* seam, not yet the *load-bearing*
  one.
- The second registration in the same module is a different abstraction:
  `ExtensionDetailsProvider` (an `AnimeDetailsProvider` from `:core:common`, §7.3) registered both
  bare and under qualifier `named("extension")` (`ExtensionModule.kt:38-43`).

### 6.6 DB-side readiness (pointer, deep dive is doc 15)

> ⚠ **CORRECTION (B3-e, verified)**: the paragraph below describes the **Phase-2 PLAN, not the built schema**.
> `external_reference` has **zero occurrences in the actual `.sq` files** (`core/database/`, grep-verified);
> `18-phase3-plan.md:28` itself admits the identity tables "were NOT built". The REAL ecosystem seam is
> the two-axis `content_details` table (nullable AniList axis + nullable extension axis with
> `extension_type` discriminator that already enumerates `'cloudstream'`) — see doc 15 §2/§8.

The database schema is already ecosystem-keyed: `external_reference(ecosystem, source_id,
external_id, …)` and `episode_external_ref(…)` with partial unique indexes
(`17-database-schema.md:111-148,186-212`), and a `sources` table keyed `PRIMARY KEY (ecosystem,
source_id)` (`:515-535`). The provider-api key strings (§6.3) were designed to feed exactly this
("Used as a prefix for content_key values in the database", `Source.kt:21-22`). Doc 15 (B3-e)
owns the column-level detail.

---

## 7. How features consume the ecosystem (the de-facto wiring)

### 7.1 Search (`feature/anime-search/impl`)

`SearchViewModel` injects **`ExtensionManager` directly** (`SearchViewModel.kt:50`) — not the
provider. Two modes (`SearchSource { ANILIST, EXTENSION }`, `:688-691`):

- **EXTENSION mode**: one selected source at a time (persisted Long id, §5.2). Blank query →
  `loadExtensionPopular()` (`:486-557`): `extensionManager.getSource(id) as? AnimeCatalogueSource`
  → `source.getPopularAnime(1)` on `Dispatchers.IO` → dedupe by URL (D-304: moviebox-style
  carousels return duplicate entries; LazyGrid keys on `"sourceId:url"` and crashes on dupes,
  `:513-520`) → `ExtensionSuccess(List<ExtensionAnime>)`. With a query → `searchExtension(q)`
  (`:562-627`): `getSearchAnime(1, q, AnimeFilterList())`, same dedupe. Errors distinguish
  `CloudflareBlocked(url, sourceName)` (D-209, with "Open in WebView" + auto-refresh on resume
  D-210), `ExtensionError(message)`, `ExtensionEmpty(sourceName, sourceUrl)`
  (`:653-686`). Request identity via `requestGeneration` (D-305) kills stale cross-source
  overwrites (`:90-114`).
- **Result model**: `ExtensionAnime(sourceId: Long, sourceName, url, title, thumbnailUrl)` — a
  feature-api-module model, deliberately free of `:core:source-api` so navigation keys can carry it
  (`feature/anime-search/api/.../ExtensionAnime.kt:1-24`); `SAnime` → `ExtensionAnime` conversion
  lives in `:impl` (`SAnimeMapper.kt:10-17`).
- **No AniList merge at search time** — extension results and AniList results are alternative
  modes, never merged in this screen `[verified]`. Merging happens on the Details screen.

### 7.2 Browse (`feature/anime-browse/impl`)

`BrowseViewModel` is **AniList-only**: constructor takes `AniListApi` + `DataCacheRepository`
(`BrowseViewModel.kt:40-43`); sections Trending/Popular/TopRated from AniList with a cache-first
browse_cache table (D-249/D-278/D-279). Extensions are NOT browsed here — the extension "browse"
surface lives in Search's EXTENSION mode (§7.1). For Batch 4: a CloudStream "Cloud Screen" browse
(doc 12 §10) has no existing extension-browse plumbing to piggyback on beyond the search-mode
pattern `[verified]`.

### 7.3 Details + resolution (`feature/anime-details/impl`)

`DetailsViewModel` (3,712 lines) injects BOTH `ExtensionManager` (`:67`) **and**
`ExtensionDetailsProvider` (`:71`) plus `AniListDetailsProvider`, `AutoLinkService`,
`VideoResolver`, `ContentResolver/ContentRepository`, downloads, tracking, etc. (`:65-97`).

**Entry points** (what Batch 4 would need to parallel):

- `loadFromAniList(animeId)` (`:1042`) — AniList metadata; then `loadLinkedSource` restores a
  persisted source link `details_source_link:<anilistId>` = `"<sourceId>:<animeUrl>"`
  (`:101,2765-2848`) and fetches episodes.
- `loadFromExtension(sourceId, animeUrl, title, thumbnailUrl)` (`:1708`) — cache-first via
  `contentRepository.getMainEntryByExtension` + silent background refresh through
  `extensionProvider.fetchFromExtension` (`:1757-1801`), then **auto-link (Phase B)**:
  `performAutoLink` searches AniList by title and merges via `AniListDetailsProvider.mergeInto`
  (`:2420`); on NoMatch the UI shows a ManualLinkSheet. This is where extension data and AniList
  metadata merge — the `UnifiedAnime` model with `extensionBase`/`anilistBase` data-source
  selector (`EntryMode.EXTENSION` vs AniList).
- **`ExtensionDetailsProvider`** (`data/extension/provider/ExtensionDetailsProvider.kt:14-71`) is a
  separate, coarser seam: an `AnimeDetailsProvider` (`id = "extension"`) whose
  `fetchFromExtension(sourceId, animeUrl, title, thumbnailUrl)` calls `getAnimeDetails` on the raw
  source and maps `SAnime → UnifiedAnime` (status codes → AniList-style strings, `:73-96`),
  with the D-199 thumbnail fallback (many extensions only populate thumbnail_url in search parse,
  `:60-62`). `fetchFromAniList` returns null; `mergeInto` is identity (merge logic lives in the
  ViewModel, `:67-70`).

**Episode list**: `fetchEpisodes(source, animeUrl, title)` (`:3000`) — cache-first from
`dataCacheRepository.getEpisodeMetadata(mainId)` (reconstructing `SEpisode`s from cached rows,
`:3017-3043`), background refresh via `source.getEpisodeList(SAnime{url})` (`:3088-3089`) with a
generation guard (D-313) and skip-if-unchanged cache policy. Episodes remain **`SEpisode`
(Aniyomi type) all the way into the UI** (`EpisodeState.Loaded(episodes: List<SEpisode>)`; the
episode rows and download buttons pass `SEpisode` around — `DetailsScreen.kt:597,1023,1874`)
`[verified]`.

**Resolver flow (episode tap → watch)**:

```
EpisodeRow onClick (DetailsScreen.kt:597-649)
  └─ viewModel.resolveEpisode(SEpisode)            DetailsViewModel.kt:3450-3506
       └─ source = extensionManager.getSource(linked.sourceId) as? AnimeHttpSource   :3465
       └─ videoResolver.resolve(source, episode)   :3478  (core/video-resolver)
            ├─ getHosterList(episode)  (lib-16+, 30s timeout)   VideoResolver.kt:137-147
            ├─ per-hoster getVideoList(hoster) (lazy hosters)   :149-174
            └─ fallback getVideoList(episode)  (lib < 16)       :176-187
            → ResolvedVideo{url, quality, headers, subtitleTracks, audioTracks}
              + 3-tier ResolverServer{audioVersions{videos}}    :75-98, 228-266
       └─ videoResolver.buildServers(s.rawEntries, source.name) :3489  (same resolve — NO double call)
       └─ ResolvedVideosRegistry.put(servers) → key             :3490-3493
  Then EITHER auto-play (tryAutoSelect → onNavigateToWatch(..., resolvedVideosKey, ...),
  DetailsScreen.kt:398-456) OR ResolverSheet (server accordion + audio chips + quality chips,
  ResolverSheet.kt:80-93) → onPickVideo → same nav call.
WatchScreen reads ResolvedVideosRegistry.get(watchKey.resolvedVideosKey)  WatchScreen.kt:478-480
  (server/quality switching + re-resolve inside the player).
```

The `onNavigateToWatch` callback takes **14 positional args** including serialized episode list,
subtitle/audio track strings, and `sourceId: Long` (`DetailsScreen.kt:140`) — the player pipeline
is deeply shaped by Aniyomi types (`SEpisode`) and resolver structures `[verified]`.

**Manual source linking (AniList entries)**: `availableSources` (all `AnimeCatalogueSource`s,
`:135-138`) + `searchSource(source, query)` (`:3421-3438`) → user picks an `SAnime` →
`linkSource` persists `details_source_link:<anilistId>` = `"<sourceId>:<animeUrl>"`
(`:2857-2864`).

**Reverse auto-link**: `ReverseAutoLinkService` (in `:core:smart-matcher`, depends on
`:data:extension`) searches up to 5 enabled, user-ordered sources via `getSearchAnime` and
SmartMatcher-scores the results to auto-pick a source for an AniList anime
(`ReverseAutoLinkService.kt:37-115`). ⚠ Aniyomi-coupled core module (§1).

### 7.4 Extensions settings UI (`feature/extensions-settings/impl`)

`ExtensionsSettingsScreen` has **no ViewModel** — it collects `ExtensionManager`'s StateFlows
directly via `koinInject` (`ExtensionsSettingsScreen.kt:126-135`) and renders four sections:
Trusted Sources (installed; reorder mode; D-301 Update pill `:892`; per-row enable toggle),
Failed to Load (D-296 Errored rows: reason + Retry/Untrust/Uninstall, `:335-353`), Untrusted
(trust/delete, `:354-371`), Available Extensions (repo listings minus installed/untrusted,
`:373-387`). Global filters: search, sort (name/lang/…), NSFW toggle, and the D-298 language
filter (globe icon; All + distinct langs across every section, `:171-179,468-478`). D-299 full
virtualization (each header/row its own LazyColumn item). Supporting screens:
`ExtensionRepoSettingsScreen` (add/verify/delete repos via `verifyRepo`),
`ExtensionDetailScreen` (enable/uninstall, per-source enable toggles via
`enableSource/disableSource` — `ExtensionDetailScreen.kt:246-263` — WebView shortcut, app-info),
and `SourcePreferencesScreen` — a **Compose-native renderer for aniyomi
`ConfigurableAnimeSource.setupPreferenceScreen` trees**: it walks the AndroidX
`PreferenceScreen`/`SwitchPreferenceCompat`/`ListPreference`/… tree and re-renders each node as
styled Compose rows (`SourcePreferencesScreen.kt:65-81`). This is a strong precedent for Batch 4:
CS3 plugin settings are imperative UI lambdas (doc 11), NOT declarative preference trees, so the
same trick cannot host them — a CS3 settings host needs a different mechanism (Fragment host or
our own DSL) `[inferred from doc 11 §8]`.

---

## 8. Current constraints & known limits (code + decisions)

1. **Single-provider wiring.** One `single<VideoExtensionProvider>` Koin binding
   (`ExtensionModule.kt:33-35`); no list/set multi-binding anywhere; registering a second
   implementation today would clash or override `[verified]`. The multi-ecosystem *shape* exists in
   the interface and DB, not in the DI wiring.
2. **Zero consumers of the provider seam.** All feature code binds `ExtensionManager` + Aniyomi
   types (`SearchViewModel.kt:50`, `DetailsViewModel.kt:67`, `ExtensionsSettingsScreen.kt:126`,
   `ReverseAutoLinkService.kt:37`) `[verified]`.
3. **Aniyomi types leak through the UI layer.** `AnimeCatalogueSource` in the search picker
   (`ExtensionSourcePickerSheet.kt:55`), `SEpisode` in details rows/nav keys/download flows
   (`DetailsScreen.kt:597,140,1874`), `AnimeHttpSource` in resolver signatures
   (`VideoResolver.kt:51-53`). A second ecosystem cannot slot under these call sites without either
   adapting to them (impossible for CS3) or refactoring the call sites to provider-api models
   `[verified]`.
4. **Source identity is a bare `Long`** (`AnimeSource.id`, `Map<Long, AnimeSource>` registry,
   persisted prefs `search_selected_extension_source_id`, `details_source_link:<anilistId>`).
   Aniyomi source ids are stable within that ecosystem but a second ecosystem would collide in the
   same namespace; provider-api's `"<ecosystemId>:<sourceId>"` keys and the DB's
   `(ecosystem, source_id)` keys anticipate this, but the preference/registry layer does NOT
   `[verified]` `[open-question for Batch 4: who owns source-id namespacing at the UI layer]`.
5. **`SourceVideo` is playback-incomplete** (no headers/subs/audio) and `fetchVideoList` uses only
   the legacy episode API — §6.4 gaps `[verified]`.
6. **Lib-version split brain**: repo filter hard 12.0–16.0 vs loader soft 12.0–17.0 (§4.3)
   `[verified]`.
7. **Trust model**: per-package by name; signature must exist but is not compared against the
   trust record on re-install (`TrustService.kt:14-18`) — a re-signed same-name APK re-trusted
   blindly. No APK hash verification from repos (§4.4). Known trade-off, documented in code.
8. **Extension state persistence inventory** (all SharedPreferences-backed `PreferenceStore`
   except repos): trusted packages (`trusted_extension_packages`, `TrustService.kt:28`), enabled
   packages (`AppPreferences.enabledExtensions`, `AppPreferences.kt:42-53`), repos
   (`anikuta_extension_repos`, `ExtensionRepoRepository.kt:26-27`), selected search source (Long),
   per-anime source links (`details_source_link:<anilistId>`), auto-link prefs. Nothing is scoped
   per-ecosystem — a second ecosystem needs namespaced keys `[verified]`.
9. **Install pipeline is APK/Android-package-shaped end to end** (PackageInstaller, package
   broadcasts, pkgName keys) — CS3's `.cs3`/jar plugin files will need their own installer +
   "package changed" equivalent `[verified]` (CS3 side: docs 02/04).
10. **Browse has no extension surface** (§7.2) `[verified]`.

### D-294..D-303 behavior inventory (plus adjacent), for quick reference

| Decision | Behavior (one line) | Anchor |
|---|---|---|
| D-294 | Parent-first `PathClassLoader`; child-first loader deleted | `ExtensionLoader.kt:191-201` |
| D-295 | `LoadResult.Error` carries real per-class failure reason + name | `ExtensionLoader.kt:208-224`, `LoadResult.kt:19-23` |
| D-296 | Errored extensions visible ("Failed to Load" section, Retry/Untrust/Uninstall); `applyLoadResult` never drops | `ExtensionManager.kt:242-273`, `ExtensionsSettingsScreen.kt:335-353` |
| D-297 | Lib range 12.0–17.0 known-good, everything attempted | `ExtensionLoader.kt:75-76,175-180` |
| D-298 | `Installed.lang` populated from sources; extensions-screen language filter | `ExtensionLoader.kt:229-234`, `ExtensionsSettingsScreen.kt:171-179` |
| D-299 | Fully virtualized extensions list | `ExtensionsSettingsScreen.kt:270-272` |
| D-300 | Single canonical install path (manager delegates to installer) | `ExtensionManager.kt:379-408` |
| D-301 | Auto update-check on page entry, 30-min throttle, UpdateCheckState, Update pill | `ExtensionManager.kt:52-53,446-477` |
| D-302 | provider-api made real (interface + Aniyomi facade + Koin single) | `VideoExtensionProvider.kt`, `ExtensionModule.kt:33-35` |
| D-303 | Release 0.2.57 = D-294..D-302 batch | `decisions.md:2342-2347` |
| (adjacent) D-304/305 | Search dedupe by URL; request-generation identity | `SearchViewModel.kt:513-520,90-114` |
| (adjacent) D-309/D-311 | Download progress animation + terminal-result reporting; post-install immediate re-scan | `InstallStep.kt:22-33`, `ExtensionManager.kt:429-437` |

---

## 9. The integration surface summary (for Batch 4)

### 9.1 The seams where a CloudStreamProvider plugs in

1. **Implement `VideoExtensionProvider`** (or a CS3-tailored sibling) in a new module — the
   interface is dependency-clean (`provider-api/build.gradle.kts`: only `:core:common` +
   coroutines) and `AniyomiExtensionProvider` is a complete reference implementation (~190 lines).
   The `ecosystemId`/key discipline means CS3 sources/content can coexist by key
   (`"cloudstream:<pluginName>:<url>"`) `[verified design intent]`.
2. **Koin registration** — the current `single<VideoExtensionProvider>` must become a
   multi-binding (`List<VideoExtensionProvider>` or per-ecosystem qualifiers) before a second impl
   can register. Nothing else consumes the binding today, so this is a one-line-ish change with
   zero blast radius `[verified]`.
3. **Source aggregation for search** — either (a) migrate `SearchViewModel` to
   `observeInstalledSources()` (merge providers, namespace the picker by ecosystem), or (b) build
   the planned "Cloud Screen" as a separate surface that uses the provider seam from day one
   (doc 12 §10 sketches the Cloud Screen). The persisted Long source id pref must become
   ecosystem-qualified either way.
4. **Details/resolution dispatch** — `DetailsViewModel.resolveEpisode` hard-codes
   `AnimeHttpSource` + `VideoResolver`; a CS3 path needs either a provider-typed dispatch (content
   key prefix → provider) or a parallel entry point. `loadFromExtension(sourceId: Long, …)` +
   `ExtensionDetailsProvider.fetchFromExtension(sourceId: Long, …)` are equally Long-typed
   `[verified]`.
5. **Extensions settings UI** — the screen is one flat ecosystem list; a second ecosystem needs
   section-per-ecosystem (or an ecosystem tab) and the lifecycle methods
   (`install/uninstall/setEnabled/checkForUpdates`) map 1:1 to the existing row actions. CS3
   settings hosting needs its own mechanism (doc 11 §8; our `SourcePreferencesScreen`
   tree-walking trick does NOT transfer to imperative CS3 settings lambdas) `[inferred]`.
6. **Repo management** — `ExtensionRepoRepository`/`ExtensionRepoApi` are Aniyomi-index-shaped; a
   CS3 repo layer (repo.json/plugins.json, doc 04) is additive — new classes, same SharedPreferences
   pattern or its own store. Watch the `repo.json` filename collision (§4.1).

### 9.2 What is provider-agnostic already vs anime/Aniyomi-specific

| Already agnostic | Still Aniyomi/anime-specific |
|---|---|
| `:core:provider-api` models + interface (§6) | All feature ViewModels/screens binding `ExtensionManager` + Aniyomi types (§8.3) |
| Composite keys `"<ecosystemId>:<sourceId>:<externalId>"` (§6.3) | Source registry `Map<Long, AnimeSource>` + Long-typed prefs (§8.4) |
| DB `external_reference(ecosystem, source_id, …)` + `sources` table (§6.6, doc 15) | `UnifiedAnime`/`AnimeDetailsProvider` details contract (AniList-shaped status strings, anime episode model) |
| `ContentType` enum (growth point) | `VideoResolver`/`ResolverServer` pipeline (AnimeHttpSource-shaped; though ResolverServer/ResolverVideo themselves are plain data) |
| `ResolvedVideosRegistry` handoff (string-keyed, type-agnostic) | `SEpisode` in nav keys, download flows, watch args |
| Installer/download UX patterns (InstallStep flow, progress, mutex) | The installer itself (PackageInstaller/APK-only) |

### 9.3 Open questions for Batch 4 `[open-question]`

1. Does the CloudStream facade implement `VideoExtensionProvider` as-is (with
   `install(pkgName)` reinterpreted as plugin name) or does the interface get a generic
   `install(handle: String)` / ecosystem capability descriptor first?
2. `SourceVideo` needs headers/subtitle/audio tracks (or a `ResolvedVideo`-grade successor) before
   it can be the single playback contract — extend in place or add a resolver-level provider
   method?
3. Who owns per-ecosystem source-id namespacing at the UI/persistence layer (prefs are bare Longs
   today)?
4. Do existing features migrate to the provider seam (big refactor of Search/Details) or does the
   Cloud Screen ship as a parallel consumer that proves the seam first?
5. Should `ExtensionRepoApi`'s `LIB_MAX` be aligned to 17.0 (§4.3), and where should compat gating
   live for the CS3 system (CS3 `apiVersion` is dead at runtime per doc 04 §3.2)?
6. Is the `episodes` field on `SourceContentDetails` (never populated) meant to become the
   details+episodes single-fetch path, or do CS3's two-step `load()`/`loadLinks` semantics map to
   `fetchContentDetails`/`fetchVideoList` cleanly?

---

## 10. Could not verify / not covered

- **Runtime behavior**: everything here is from static source reading; no device run, no logcat
  evidence gathered in this task. The D-294/D-296/D-301 behavior descriptions are backed by
  in-code comments + decisions.md, not re-tested.
- **`:app` module wiring** (navigation graph entries for the settings screens, Koin module list
  assembly) — not read; cited only via the feature modules' own Koin usage (`koinInject`).
- **Doc 13** (`13-cloudstream-app-internals.md`, B3-c) landed in
  `DOCUMENTATION/cloudstream/` while this doc was being written (parallel batch agents) — it was
  not read in time to cross-reference here; no claims in this doc depend on it.
- **DB column-level detail** deliberately deferred to doc 15 (B3-e); §6.6 is a pointer only.
- The full 3,634-line `DetailsScreen.kt` and 3,712-line `DetailsViewModel.kt` were sampled at the
  entry points (load/link/search/episodes/resolve/navigation), not read exhaustively; download
  orchestration, tracking, seasons UI were out of scope.
