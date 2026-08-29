# 01 — CloudStream (CS3) Ecosystem Overview

> **Research batch**: B1-a · **Agent**: 40-B1-a · **Status**: complete
> **Sources** (read-only clones, pinned at clone time 2026-08-29):
> `research/cloudstream/` = recloudstream/cloudstream master @ `efc1915` (2026-08-28, shallow clone — history not visible),
> `research/csdocs/` = recloudstream/csdocs (docs site source),
> `research/cs-repos/`, `research/extensions/`, `research/TestPlugins/`.
> All paths in this doc are relative to `/home/z/ANI-KUTA-WORK/research/` unless stated.
> Confidence markers: `[verified]` = read in source · `[docs]` = from csdocs · `[inferred]` = reasoned, needs verification.

---

## 1. What CloudStream is

### 1.1 Self-description

CloudStream's own README describes it as:

> "**CloudStream is a media center that prioritizes and emphasizes complete freedom and flexibility for users and developers.**" — `cloudstream/README.md:24`

> "CloudStream is an extension-based multimedia player with tracking support." — `cloudstream/README.md:26`

And immediately warns:

> "**⚠️ Warning: By default, this app doesn't provide any video sources; you have to install extensions to add functionality to the app.**" — `cloudstream/README.md:3`

The docs site (`csdocs/index.md:7`) frames it as: *"an Android application for streaming and downloading movies, TV series, anime, Asian content and livestreams. The app provides all this content ad-free, without any sign-up or subscription requirements."* `[docs]`

The fastlane store listing (`cloudstream/fastlane/metadata/android/en-US/full_description.txt`) says: *"CloudStream-3 lets you stream and download Movies, TV-Series and Anime"* — no ads, no analytics; bookmarks, subtitle downloads, Chromecast support. `[verified]`

Feature list from the README (`cloudstream/README.md:43-50`): AdFree, no tracking/analytics, bookmarks, phone **and TV** support, Chromecast, "extension system for personal customization". `[verified]`

Examples of content the README itself names for extensions (`cloudstream/README.md:28-33`): Librevox (audio-books), YouTube, Twitch, iptv-org IPTV channels, nginx — i.e. the ecosystem is general "video/audio/livestream source" plugins, not just pirate movie sites. `[verified]`

### 1.2 Platforms & form factors

- **Android phone + Android TV**: the app has a leanback launcher category and `android.software.leanback` uses-feature in `cloudstream/app/src/main/AndroidManifest.xml`; there are separate TV UI fragments (`app/src/main/java/com/lagradost/cloudstream3/ui/result/ResultFragmentTv.kt`, `ui/library/`, EPG permissions for "Android TV watch next") and a TV setup flow (`ui/setup/`). `[verified]`
- **Windows / Linux** are *not* native: csdocs describes running the Android app under WSA/MagiskonWSA on Windows (`csdocs/Other devices/Windows.md`) and Waydroid on Linux (`csdocs/Other devices/Linux.md`). `[docs]`
- Localization: `app/src/main/res/` has **61** `values-b+*` locale dirs and `fastlane/metadata/android/` has **50** locale dirs; translations run through Hosted Weblate (`cloudstream/README.md:109`). `[verified]`

### 1.3 License & governance

- **License: GNU GPL v3.0** (`cloudstream/LICENSE`). `[verified]`
- Maintained by the **reCloudStream GitHub organization** (`github.com/recloudstream`, referenced throughout: README:36, `app/build.gradle.kts:349` dokka source link, `discoverium.yml:3` `authors: recloudstream`). The package name `com.lagradost.cloudstream3` and library group `com.lagradost.api` (`library/build.gradle.kts:129`) preserve the original "LagradOst" author namespace. `[verified]`
- Legal posture: "Please don't create illegal extensions or use any that host any copyrighted media. For more details about our stance on the DMCA and EUCD, you can read about it on our organization" (`cloudstream/README.md:36`), plus a copyright-note disclaimer that recommended sources are "not officially moderated or endorsed by CloudStream" and a takedown contact offer (`README.md:38-40`). The app ships **zero built-in providers** (README:3 warning; also `RepositoryManager.kt:100-102` — `PREBUILT_REPOSITORIES` defaults to an empty array). `[verified]`
- The repo has an **AI-POLICY.md**: AI usage must be stated in PRs/issues, code must be tested, human reviewers over AI ("we do in-depth reviews and will reject low effort contributions"). Relevant to us if we ever upstream anything. `[verified]`

### 1.4 Project history — what is actually verifiable

The local sources contain **no written history** of the project. Verifiable history signals only:

- The "3" in the name: the app calls itself "CloudStream-3" in the fastlane description and "Cloudstream3" in the plugin-template READMEs (`TestPlugins/README.md`); "CS3" is the community shorthand used by csdocs ("CS3" in `csdocs/Other devices/tv.md`). `[inferred: the "3" implies earlier major iterations, but the repos do not document them — do NOT cite pre-recloudstream history as fact]`
- The plugin system "is **heavily based on Aliucord**" (a Discord client mod) — stated in both `extensions/README.md` and `TestPlugins/README.md`. `[verified]`
- The clone is a shallow clone pinned at `efc1915` ("Merge pull request #3111 from recloudstream/weblate", 2026-08-28) — no git history available for dating anything else. `[verified]`

> ⚠️ Anything else (origins, takedowns, forks timeline) is **not** in these sources and must not be written down as fact.

---

## 2. The three-layer architecture

### 2.1 Layer A — the app (`app/` gradle module)

The Android application itself: UI (browse/search/details/library/downloads/player/settings), the plugin loader, the repository manager, downloads, subtitles, sync accounts. `applicationId = "com.lagradost.cloudstream3"` (`app/build.gradle.kts:104`). It contains **no providers** — every content source is a plugin installed at runtime. `[verified]`

### 2.2 Layer B — the plugin API library (`library/` gradle module)

This is "the official API surface for all CloudStream plugins" (`cloudstream/library/README.md:3`). Key facts:

- **Kotlin Multiplatform** (`library/build.gradle.kts:10`, `kotlin.multiplatform` plugin; targets `android { … }` at lines 25-39 and `jvm()` at line 41). `[verified]`
- Source sets present on disk (`library/src/`): `commonMain`, `commonTest`, `jvmCommonMain`, `jvmMain`, `androidMain`, **`webMain`**, `webTest`. `[verified]` The build script only wires `commonMain` → `jvmCommonMain` → (`androidMain`, `jvmMain`) (`library/build.gradle.kts:76-85`); `webMain` is **not referenced by any build script** (grep across `*.kts`/`*.toml` = no hits) and contains only 5 small `.web.kt` files (`ContextHelper.web.kt`, `Log.kt`, `YoutubeExtractor.web.kt`, `ArchComponentExt.web.kt`, `Coroutines.web.kt`). The app also declares `app.cash.zipline:zipline-android` 1.27.0 in `gradle/libs.versions.toml:57,137` with **zero usages in app code** (grep = no hits). `[verified]` → A browser/Kotlin-JS plugin target appears to be work-in-progress. `[inferred]`
- `commonMain` carries the whole plugin API: `MainAPI.kt` (**2860 lines**, `abstract class MainAPI` at line 494), `extractors/` (**97 extractor classes** matching `ExtractorApi()` across 104 files), `metaproviders/` (Tmdb, Trakt, MyDramaList, CrossTmdb, SyncRedirector), `plugins/` (`BasePlugin`, `CloudstreamPlugin`), `syncproviders/SyncAPI.kt`, `utils/` (ExtractorApi, JsUnpacker/JsInterpreter/JsHunter, M3u8Helper, HlsPlaylistParser, SubtitleHelper, UnshortenUrl…), `network/WebViewResolver.kt`. `[verified]`
- **Binary compatibility is enforced, not runtime-versioned**: library README requires "binary compatibility on all changes"; new API must be annotated `@Prerelease` until in a stable release; `./gradlew checkKotlinAbi` validates against the dump `library/api/jvm/library.api` (`library/README.md:5-16`); PR CI runs `library:checkKotlinAbi` (`cloudstream/.github/workflows/pull_request.yml:29-33`); ABI validation excludes `@Prerelease`/`@InternalAPI`-annotated members (`library/build.gradle.kts:88-97`). The `@Prerelease` opt-in tells plugin devs an API is "only available on prerelease builds. Using it will cause CloudStream stable to crash with `NoSuchMethodException`" (`MainAPI.kt:52-58`). `[verified]`
- **Published for plugin authors via JitPack**: root `jitpack.yml` = `jdk: openjdk17`; the official extension repo depends on `"com.github.recloudstream.cloudstream:library:-SNAPSHOT"` (`extensions/build.gradle.kts:74`); the plugin template uses a dedicated *stub* configuration `cloudstream("com.lagradost:cloudstream3:pre-release")` described as "Stubs for all cloudstream classes" (`TestPlugins/build.gradle.kts:71-76`). Publishing config in-repo sets `groupId = "com.lagradost.api"` + `maven-publish` (`library/build.gradle.kts:9,126-132`) and `version = "1.0.1"` (`library/build.gradle.kts:21`). `[verified]`

### 2.3 Layer C — plugins + plugin repositories

- A **plugin** is a compiled archive (`.cs3` file — see `PluginManager.kt:754`; format forensics are doc `02-plugin-format.md`'s job). It contains a `manifest.json` resource (fields `name`, `pluginClassName`, `requiresResources`, `version` — `library/.../plugins/BasePlugin.kt:64-77`) and a class extending `BasePlugin` annotated `@CloudstreamPlugin`. At load the plugin registers its providers into the app: `registerMainAPI(element: MainAPI)` and `registerExtractorAPI(element: ExtractorApi)` (`BasePlugin.kt:20-35`). `[verified]`
- A **plugin repository** is just a `repo.json` URL: `{ name, description, manifestVersion, pluginLists: [url…] }` (`app/.../plugins/RepositoryManager.kt:33-40`), where each plugin list (`plugins.json`) is an array of `SitePlugin` entries pointing at `.cs3` files with `version` (any bump = auto-update trigger), `status`, `fileSize`/`fileHash` ("Automatically generated by the gradle plugin", `RepositoryManager.kt:73-75`), `tvTypes`, `language`, etc. (`RepositoryManager.kt:50-76`). Repos are added in-app by URL or `cloudstreamrepo://` deep link (scheme declared at `AndroidManifest.xml:201`). `[verified]`

### 2.4 How the layers connect (loading pipeline) `[verified]`

1. User adds a repo URL → app fetches `repo.json` → follows `pluginLists` → shows installable plugins (`RepositoryManager`, `ui/settings/extensions/ExtensionsFragment` + `PluginsFragment`).
2. User installs a plugin → `.cs3` downloaded into `filesDir/Extensions/<repo>/` (`RepositoryManager.kt:99` `ONLINE_PLUGINS_FOLDER = "Extensions"`).
3. On load (`PluginManager.kt:593-687`): file set read-only → **`PathClassLoader(filePath, context.classLoader)`** (line 611 — a *parent-first* classloader, no child-first shadowing) → reads `manifest.json` as a resource (lines 613-621) → `loader.loadClass(manifest.pluginClassName)` → `newInstance()` as `BasePlugin` (lines 631-634) → optional resource loading for `requiresResources` via reflection `AssetManager.addAssetPath` (lines 645-659) → `pluginInstance.load(context)` runs, which calls `registerMainAPI`/`registerExtractorAPI` → providers land in `APIHolder.allProviders` (`MainAPI.kt:109-115`) and `utils.extractorApis`.
4. If a plugin crashes the startup, a **safe mode** (a file named `safe` in the app folder, `PluginManager.kt:579-588`) skips extension loading.

### 2.5 Diagram

```
                    ┌────────────────────────────────────────────────┐
                    │  LAYER C — PLUGINS + REPOSITORIES (community)  │
                    │                                                │
                    │  repo.json ──► plugins.json ──► *.cs3 files    │
                    │  (recloudstream/extensions, phisher, storm,    │
                    │   CakesTwix-uk, …26 in cs-repos DB)            │
                    └───────────────────────┬────────────────────────┘
                                            │ install / auto-update (version bump)
                                            ▼
┌───────────────────────────────────────────────────────────────────────────┐
│  LAYER A — THE APP  (app/ module, com.lagradost.cloudstream3)             │
│                                                                           │
│   Settings►Extensions UI ── RepositoryManager (repo.json, SHA-256)        │
│            │                                                              │
│            ▼                                                              │
│   PluginManager: PathClassLoader(.cs3) ─► manifest.json ─► BasePlugin     │
│            │                                    │                         │
│            ▼                                    ▼                         │
│   APIHolder.allProviders ◄── registerMainAPI     extractorApis ◄──         │
│   (MainAPI instances)          registerExtractorAPI                        │
│                                                                           │
│   UI: home / search / result(details) / player / library / downloads      │
└───────────────────────────────────────────────────────────────────────────┘
                                            ▲ compiles against (JitPack)
                                            │
                    ┌───────────────────────┴────────────────────────┐
                    │  LAYER B — PLUGIN API LIBRARY (library/, KMP)  │
                    │  commonMain: MainAPI, ExtractorApi, data       │
                    │  models, extractors/ (97), metaproviders/      │
                    │  (TMDb/Trakt/MDL), BasePlugin, SyncAPI, utils  │
                    │  jvmCommonMain→androidMain|jvmMain (+webMain*) │
                    │  * webMain present but not wired into build    │
                    │  Published: com.github.recloudstream.cloudstream│
                    │  :library  (JitPack; stubs for plugin builds)  │
                    └────────────────────────────────────────────────┘
```

---

## 3. App technology stack

All from `cloudstream/gradle/libs.versions.toml` and `cloudstream/app/build.gradle.kts` unless noted. `[verified]`

| Aspect | What CloudStream uses | Evidence |
|---|---|---|
| Language | Kotlin 2.4.0 (JVM target **1.8**, JDK toolchain 17) | `libs.versions.toml:29,59,60`; `app/build.gradle.kts:187-193` |
| Android | AGP 9.1.1; minSdk **23**, targetSdk **36**, compileSdk 37 | `libs.versions.toml:5,61-63` |
| Version | versionName **4.8.0**, versionCode **68** | `libs.versions.toml:65-66` |
| UI toolkit | **Views + Fragments + Navigation Component + Material**, NOT Compose — `buildFeatures { viewBinding = true }`, no compose dependency anywhere in the catalog; deps: appcompat, material 1.14.0, constraintlayout, `navigation-fragment-ktx`/`navigation-ui-ktx`, preference-ktx, fragment-ktx | `app/build.gradle.kts:199-202,226-240`; `libs.versions.toml` (full read — no compose entries) |
| DI | **None.** No Koin/Dagger/Hilt/Injekt in the version catalog or app deps; wiring is manual singletons/objects (`object PluginManager`, `object RepositoryManager`, `object APIHolder`) | `libs.versions.toml` (absence), `app/.../plugins/PluginManager.kt:104` |
| Networking | **NiceHttp** (`com.github.Blatzar:NiceHttp` 0.4.18) as the HTTP lib — also in library commonMain; shared client `val app: Requests` defined in library `MainActivity.kt`; jsoup 1.22.1 + ksoup 0.2.6 (HTML), jackson-module-kotlin **2.13.1 pinned** ("Later versions don't support minSdk <26 — crashes on Android TVs and FireSticks"), kotlinx-serialization 1.11.0; Conscrypt (SSL on Android 9); ktor-http (URL types) | `libs.versions.toml:22,24,36,44,124`; `app/build.gradle.kts:289,275`; `library/build.gradle.kts:55-69` |
| Player | **Media3 / ExoPlayer 1.9.3** (exoplayer, hls, dash, cast, session, ui, datasource-okhttp/cronet) + **nextlib** `io.github.anilbeesetti:nextlib-media3ext/mediainfo` 1.9.3-0.12.0 (FFmpeg software decoding) + previewseekbar; updated Matroska/extractors factories in `ui/player/` | `libs.versions.toml:40,43,115-118,122-123,128`; `app/build.gradle.kts:245-247,250`; `ui/player/UpdatedMatroskaExtractor.kt` |
| Torrents | `com.github.recloudstream:torrentserver` (built-in torrent streaming; `ui/player/Torrent.kt`, live preview timebar) | `libs.versions.toml:53,133`; `app/build.gradle.kts:284-285` |
| JS engine | Mozilla **Rhino 1.8.1** (pinned; 1.9.0 needs minSdk 26) for executing JS in extractors (`utils/JsInterpreter`, `JsUnpacker`, `JsHunter` in library) | `libs.versions.toml:50,130`; `app/build.gradle.kts:272` |
| Image loading | **Coil 3** (`io.coil-kt.coil3:coil` + `coil-network-okhttp`, strictly 3.3.0) | `libs.versions.toml:11,74-75`; `app/build.gradle.kts:243` |
| Scraping extras | NewPipeExtractor v0.26.3 ("For Trailers"), SafeFile, juniversalchardet (subtitle encoding) | `app/build.gradle.kts:257-258,273` |
| Legacy shims | gson 2.11.0 + fuzzywuzzy 1.4.0 kept **only** "until extensions have time to migrate from using it" | `app/build.gradle.kts:279-282` |
| Crash reporting | ACRA (`AcraApplication.kt`) — despite "no analytics" branding | `app/src/main/java/com/lagradost/cloudstream3/AcraApplication.kt` |
| Zipline | `app.cash.zipline:zipline-android` 1.27.0 declared, **no code references found** — likely groundwork for the `webMain` plugin target | `libs.versions.toml:57,137` `[inferred]` |

**Flavors & build types** (`app/build.gradle.kts:143-179`): `stable` and `prerelease` product flavors (dimension "state"); prerelease gets `applicationIdSuffix = ".prerelease"`, `versionNameSuffix = "-PRE"` and a timestamp-based versionCode; debug adds `.debug` suffix. So the three app IDs are `com.lagradost.cloudstream3`, `…cloudstream3.prerelease`, `…cloudstream3.prerelease.debug` (also documented for plugin ADB testing in `TestPlugins/README.md`). Release build is **not minified** (`isMinifyEnabled = false`, line 146). `[verified]`

**`discoverium.yml`** (`cloudstream/discoverium.yml`): a small app-manifest file — `name: CloudStream`, `authors: recloudstream`, `category: entertainment`, `description: Android app for streaming and downloading media.`, an icon URL, and `releases.url: github.com/recloudstream/cloudstream/releases`. It is metadata for **Discoverium** (a third-party FOSS app-discovery/updater tool) so CloudStream can be tracked/updated through it. `[verified file contents; purpose inferred]`

---

## 4. Module map

### 4.1 Gradle modules (`cloudstream/settings.gradle.kts:20-21`)

```
CloudStream (rootProject.name)
├── app      — the Android application
├── library  — the KMP plugin-API library (publishable)
└── docs     — Dokka documentation aggregation only
              (docs/build.gradle.kts: dokka(project(":app:")), dokka(project(":library:")))
```

Repositories are locked: `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, google + mavenCentral + mavenLocal + `https://jitpack.io` (`settings.gradle.kts:10-18`). `[verified]`

The app additionally has a `makeJar` task that merges `app` classes + `library-jvm.jar` into a single `classes.jar` (`app/build.gradle.kts:299-322`) — this artifact is uploaded with pre-release builds and is what plugin stubs are built from. `[verified]`

### 4.2 `app/src/main/java/com/lagradost/cloudstream3/` — key packages `[verified]`

| Package | Contents (one-liner) |
|---|---|
| `plugins/` | `Plugin.kt` (BasePlugin subclass with Context/resources), `PluginManager.kt` (967 lines — loader, load/update/install flows, safe mode), `RepositoryManager.kt` (repo.json/plugins.json models + download, SHA-256 verification), `VotingApi.kt` |
| `ui/` | All screens: `home/` (main-page browse), `search/` (+ `quicksearch/`), `result/` (details; phone + TV variants, actors, episode lists), `player/` (CS3IPlayer, FullScreenPlayer, LinkGenerator + ExtractorLinkGenerator, source_priority quality profiles, live/, subtitles, gestures, previews), `library/`, `download/` (+ queue, fetch buttons), `subtitles/`, `settings/` (incl. `settings/extensions/` = repo & plugin management UI, `settings/testing/`), `setup/` (first-run wizard incl. provider-language & extension steps), `account/` |
| `syncproviders/` | Login/sync framework: `AuthAPI/AuthRepo`, `SyncAPI/SyncRepo`, `AccountManager`, `BackupAPI`, `SubtitleAPI/SubtitleRepo` + `providers/` (AniListApi, MALApi, SimklApi, KitsuApi, LocalList; subtitle sources OpenSubtitlesApi, Subdl, Addic7ed, SubSource) — i.e. tracker + subtitle *accounts*, separate from content providers |
| `subtitles/` | AbstractSubtitleEntities, AbstractSubProvider — subtitle model glue for the player |
| `services/` | Android services & workers: VideoDownloadService, DownloadQueueService, PackageInstallerService, SubscriptionWorkManager, BackupWorkManager |
| `network/` | App-side network helpers: CloudflareKiller, DdosGuardKiller, DohProviders (DNS-over-HTTPS), RequestsHelper |
| `actions/` | "Open with" actions for resolved links — external players (VlcPackage, MpvPackage, JustPlayerPackage, NextPlayer, WebVideoCast…), fcast, torrent clients (aria2, BiglyBT, LibreTorrent), CopyClipboard, PlayInBrowser |
| `utils/` | DataStore/DataStoreHelper (key-value persistence), BackupUtils, InAppUpdater, downloader/ (own download manager), videoskip/ (AniSkip, IntroDbSkip, AnimeSkip intro skipping), UIHelper, TestingUtils… |
| `widget/`, `receivers/`, `mvvm/` | TV/grid layout managers; download-restart receiver; small lifecycle+logging helpers |

### 4.3 `library/src/commonMain/kotlin/com/lagradost/cloudstream3/` — the plugin API `[verified]`

| Item | One-liner |
|---|---|
| `MainAPI.kt` (2860 lines) | The provider contract: `abstract class MainAPI` (line 494) with search/load/mainPage/loadLinks…; data models (SearchResponse/LoadResponse/Episode/Video…); `object APIHolder` (line 109) with `allProviders`; `TvType` enum (line 1120) with **18 values** — Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast, Video |
| `extractors/` | 97 built-in host extractors (megacloud, dood, streamtape, gogo, …) compiled *into the library*, so every plugin/app gets them for free |
| `metaproviders/` | TmdbProvider, TraktProvider, MyDramaList, CrossTmdbProvider, SyncRedirector — providers-of-metadata the app/plugin can use to fill in or map catalogs |
| `plugins/` | `BasePlugin` (plugin lifecycle + registration API + Manifest), `CloudstreamPlugin` (marker annotation) |
| `utils/` | `ExtractorApi` (custom-extractor base), `JsInterpreter`/`JsUnpacker`/`JsHunter` (JS challenge eval via Rhino), `M3u8Helper`, `HlsPlaylistParser`, `SubtitleHelper`, `UnshortenUrl`, `Levenshtein`, `AppUtils` |
| `network/` | `WebViewResolver` — cloudflare/JS-challenge solving via WebView |
| `syncproviders/SyncAPI.kt` | Sync/account API interfaces plugins could implement |
| `MainActivity.kt` (library!) | Misleading name: hosts the shared **`app` NiceHttp `Requests` client** all providers use |
| `ParCollections.kt` | `amap`/`apar` concurrent-collection helpers used by providers |

> Note: the app also has its own `syncproviders/` implementations — the library declares the *interfaces*, the app ships the *implementations* (MAL/AniList/Simkl/Kitsu accounts, subtitle sources). `[verified structure]`

---

## 5. Distribution & versions

### 5.1 App distribution `[verified]`

- **Two release channels, both GitHub Releases** (csdocs install step 1: "Stable" → `releases/latest` or "Pre-release" → `releases/pre-release`, `csdocs/index.md:13`): `[docs]`
  - **Pre-release is fully automated**: `.github/workflows/prerelease.yml` triggers on every push to `master` (code paths only), fetches a keystore from the private `recloudstream/secrets` repo via a GitHub App token, runs `./gradlew assemblePrereleaseRelease androidSourcesJar makeJar`, and publishes/overwrites a rolling GitHub release tagged **`pre-release`** containing the APK + `app-sources.jar` + `classes.jar` (`marvinpinto/action-automatic-releases`, `automatic_release_tag: "pre-release"`).
  - **Stable releases**: versionName 4.8.0 / versionCode 68 sit in `libs.versions.toml:65-66`; there is **no stable-release workflow in the repo** — stable cuts are made outside the visible CI (manual). `[inferred]`
  - PRs get artifact builds + `library:checkKotlinAbi` binary-compat gate (`pull_request.yml`).
- **fastlane/** metadata (50 locales) — standard F-Droid-style store metadata (title "CloudStream", short/full descriptions, `changelogs/2.txt`). Presence of changelog only for version 2 suggests the listing is not actively maintained. `[inferred]`
- **discoverium.yml** — machine-readable app metadata for the Discoverium updater (see §3).
- csdocs additionally documents installs via **URL shortener codes** for repo adding and warns that some ISPs block `raw.githubusercontent.com` (VPN advice, `csdocs/index.md:33-35`, `csdocs/troubleshooting.md`). `[docs]`
- The docs' repository list has moved OUT of csdocs to a wiki: Repositories.md is now just a link to `cloudstream.miraheze.org/wiki/List_of_extensions` (`csdocs/Repositories.md:7`), and the README admits the docs are "unmaintained and open to contributions" (`cloudstream/README.md:40`). `[docs/verified]`

### 5.2 app↔plugin compatibility: `apiVersion` & the real mechanism

- `apiVersion` appears **exactly once** in the app repo — as a field of `SitePlugin` (the plugins.json entry model):
  ```kotlin
  // Unused currently, used to make the api backwards compatible?
  // Set to 1
  @JsonProperty("apiVersion") @SerialName("apiVersion") val apiVersion: Int,
  ```
  `app/.../plugins/RepositoryManager.kt:57-59`. Local plugins hardcode it to `1` (`PluginManager.kt:92`, `toSitePlugin()`). **There is no runtime apiVersion gating anywhere** (grep across the repo: single hit). `[verified]`
- Actual compatibility control is **build-time binary compatibility** (§2.2): the `library.api` ABI dump + `@Prerelease`/`@InternalAPI` annotations + CI `checkKotlinAbi`. A plugin compiled against stable-public API keeps working; a plugin using prerelease API crashes on stable (`@Prerelease` annotation doc, `MainAPI.kt:52-58`, `annotation class Prerelease` at line 63).
- Plugin updates: any increase of `SitePlugin.version` "will trigger an auto update" (`RepositoryManager.kt:55-56`); `fileHash`/`fileSize` are gradle-plugin-generated integrity metadata (`RepositoryManager.kt:73-75`); downloaded plugins are SHA-256-verified (`RepositoryManager.kt:107-120`). `[verified]`

> For ANI-KUTA this is a **materially different compat model from aniyomi's `extVersion`/libVersion dance** we just reworked in D-297: CS3 trusts ABI discipline instead of a version gate.

---

## 6. The ecosystem actors

| Actor | Who / what | Evidence |
|---|---|---|
| **Plugin developers** | Anyone using the official template; toolchain = Gradle plugin `com.github.recloudstream:gradle:-SNAPSHOT` + `com.lagradost.cloudstream3.gradle` applied per provider module, with a `cloudstream {}` extension block (`description`, `authors`, `status` (0 Down/1 Ok/2 Slow/3 Beta-only), `tvTypes`, `requiresResources`, `language`, `iconUrl`) — see `TestPlugins/build.gradle.kts:14-19,34-42` and `TestPlugins/ExampleProvider/build.gradle.kts:9-31` | `[verified]` |
| **Official extensions repo** | `recloudstream/extensions` — only **5 providers** (Dailymotion, InternetArchive, Invidious, Twitch, Youtube), all "safe"/legal sources; `repo.json` = `{ name: "Cloudstream providers repository", manifestVersion: 1, pluginLists: [https://raw.githubusercontent.com/recloudstream/extensions/builds/plugins.json] }`. It is the *only* repo marked `verified: true` in the community DB | `extensions/` tree + `extensions/repo.json` `[verified]` |
| **Plugin repo template** | `recloudstream/TestPlugins` — "Cloudstream3 Plugin Repo Template", public-domain, with `ExampleProvider` (ExampleProvider.kt, ExamplePlugin.kt with settings, BlankFragment.kt), local dev via `./gradlew ExampleProvider:make` / `deployWithAdb` | `TestPlugins/README.md` `[verified]` |
| **Community repo registry** | `recloudstream/cs-repos`: README = "Community Cloudstream repositories" pointing at rentry.org/cs3-repos; `repos-db.json` lists **26 repo.json URLs, only 1 flagged verified**; `ci_check.py` validates each repo: repo.json must parse and have `name` + `manifestVersion`, every `pluginLists` URL must fetch, every plugin `url` must HEAD 200 | `cs-repos/README.md`, `repos-db.json`, `ci_check.py:23-40` `[verified]` |
| **Community repos** (examples in DB) | phisher98, Luna712, CakesTwix (uk), redblocker8/storm-ext, self-similarity/MegaRepo, language-specific repos (Italian, Vietnamese, German, Arabic, …) — mirrors the tracker's cloned sample set | `cs-repos/repos-db.json` `[verified]` |
| **Users** | Add repos by URL/shortcode/deep-link (`cloudstreamrepo://`), install/update plugins from Settings→Extensions; VPN guidance when GitHub raw is blocked | `csdocs/index.md`, `AndroidManifest.xml:201` `[docs/verified]` |
| **Cross-ecosystem bridge** | **Aniyomi extensions run inside CloudStream** via the community `CranberrySoup/AniyomiCompatExtension` repo (shortcode `anicompat`): the plugin downloads an internal compat APK and surfaces installed Aniyomi extensions as CS3 providers — "Not guaranteed to work perfectly" | `csdocs/Integrations/Aniyomi.md` `[docs]` |
| **Attribution** | "The gradle plugin and the whole plugin system is heavily based on Aliucord" | `extensions/README.md`, `TestPlugins/README.md` `[verified]` |

Ecosystem shape in one line: a tiny *official* (legal-sources-only) repo + a long tail of **community repos in 15+ languages**, discoverable via cs-repos/rentry/wiki, all speaking the same `repo.json → plugins.json → .cs3` protocol. `[verified/inferred mix as cited]`

---

## 7. Why this matters for ANI-KUTA

Takeaways for the "Cloud Screen" integration program (doc map: `00-RESEARCH-TRACKER.md` §3):

1. **Content-coverage win**: CS3's `TvType` taxonomy (18 types incl. Movie, TvSeries, AsianDrama, Documentary, Live, NSFW, AudioBook — `MainAPI.kt:1120-1142`) and the community repo sample (movies: Uakino/AllCalidad; Asian drama: DoramyWorld/DoramasFlix; anime: Coaninet/AnimeJl) directly fill ANI-KUTA's non-anime gaps. Provider semantics are catalogued in docs `03`, `05`, `10`.
2. **97 ready-made host extractors ship in the library itself** (`library/.../extractors/`, 97 `ExtractorApi()` classes) plus `JsInterpreter`/`JsUnpacker` (Rhino) and `WebViewResolver` — we would inherit an entire extractor/anti-bot stack for free; detailed in doc `08`.
3. **Second classloader system, already de-risked by evidence**: CS3 loads `.cs3` plugins with a **parent-first `PathClassLoader(filePath, context.classLoader)`** (`PluginManager.kt:611`) — the exact strategy we adopted for aniyomi extensions in D-294. Both ecosystems agree: parent-first is the load-bearing invariant. Our CS3 loader must be a *separate* loader family from the aniyomi one (different manifest format, different entry base class `BasePlugin` vs `AnimeSource`).
4. **New repo format**: `repo.json` (manifestVersion, pluginLists) → `plugins.json` (SitePlugin array) is a 2-hop indirection unlike aniyomi's index.json; adds per-plugin `status` remote-kill, `language`, `tvTypes`, and gradle-generated `fileSize`/`fileHash`. Format + flow is doc `04`; our existing update-check infrastructure (D-301) will need a parallel implementation.
5. **Compatibility model differs fundamentally**: no runtime `apiVersion` gate (field exists but "Unused currently… Set to 1", `RepositoryManager.kt:57-59`); compat = build-time ABI dumps + `@Prerelease` annotations. If we vendor the CS3 `library/` (likely — see next point), we should pin to a commit and track their `library.api`, not invent a version gate.
6. **The library is the integration surface, and it is KMP**: `commonMain` is pure Kotlin (NiceHttp/jsoup/jackson/Rhino deps), with `androidMain` only for context glue — and a dormant `webMain` + unused Zipline dependency hinting at a browser target. Vendoring `library/` (or depending on the JitPack artifact `com.github.recloudstream.cloudstream:library`, used by the official extensions repo) is the cleanest way to get type-safe plugin loading; module layout decision is doc `16`.
7. **App-stack deltas to plan for**: CS3 app is Views+Material+Navigation with **no DI and no Compose**; our Cloud Screen UI (doc `18`) will map CS3 models into our Compose stack — we consume the *library*, not their UI. Their player is Media3+nextlib like ours (doc `19` maps ExtractorLink→our player), and they also solve Cloudflare/DDoS-Guard (app-side `CloudflareKiller`, `DdosGuardKiller`, DoH) — worth studying for our own networking.
8. **Precedent for the reverse bridge**: the `AniyomiCompatExtension` proves a plugin can load *another ecosystem's* extensions inside CS3 (`csdocs/Integrations/Aniyomi.md`). This validates ANI-KUTA hosting two ecosystems side by side, and is a caution: plugin-level bridges are fragile ("not guaranteed to work perfectly") — a first-class integration (our plan) is the sturdier route.
9. **Trust & safety posture**: CS3 ships zero providers and disclaims endorsement of community repos (`README.md:3,36-40`); only 1/26 community repos is "verified" (`cs-repos/repos-db.json`). Our trust model for CS3 repos (doc `21` risks) should mirror this skepticism — per-repo opt-in, remote `status` kill-switch support, hash verification.
10. **Licensing**: the app+library are **GPL-3.0**. Vendoring/linking `library/` into ANI-KUTA has license implications that must be surfaced to the user before implementation (doc `21`).

---

## 8. Terminology cheat-sheet (for later docs)

Because B2–B4 docs will use these words constantly, fix them here from source:

| Term | Meaning in CS3 | Source |
|---|---|---|
| **CS3 / CloudStream-3** | The current app generation (fastlane title "CloudStream", description "CloudStream-3") | `fastlane/metadata/android/en-US/` `[verified]` |
| **Provider** | An instance of a `MainAPI` subclass registered by a plugin — one site's catalog (search/mainPage/load/loadLinks) | `BasePlugin.kt:20-25` |
| **Extractor** | An `ExtractorApi` instance that resolves one embed-host URL into `ExtractorLink`s; 97 ship built-in, plugins can register more | `BasePlugin.kt:31-35`, `library/.../extractors/` |
| **Plugin (`.cs3`)** | The distributable unit — may contain **multiple providers + extractors + settings UI**; declared by `manifest.json` + `@CloudstreamPlugin` class | `BasePlugin.kt:64-77`, `PluginManager.kt:611-634` |
| **Repository (repo)** | A `repo.json` (+ `pluginLists`) that distributes plugins; community-run | `RepositoryManager.kt:33-40` |
| **Metaprovider** | A `MainAPI` that wraps a metadata service (TMDb/Trakt/MDL) instead of a video site | `library/.../metaproviders/` |
| **Sync provider** | A login/sync account system (MAL/AniList/Simkl/Kitsu; subtitle accounts) — orthogonal to content providers | `app/.../syncproviders/` |
| **Safe mode** | Startup escape hatch (file named `safe`) that skips all extension loading | `PluginManager.kt:579-588` |
| **Prerelease** | Rolling `pre-release`-tagged build channel; new API lives here first behind `@Prerelease` | §5.1, `MainAPI.kt:52-63` |

> Note the vocabulary difference vs aniyomi: aniyomi's "extension ≈ one source" vs CS3's "plugin ⊇ many providers/extractors" — our UI and data model (docs `14`–`18`) must not conflate them.

---

## 9. Could-not-verify / low-confidence list

- **Stable-release mechanics** — no workflow or script in-repo cuts stable releases; manual process assumed. `[inferred]`
- **History of the project** (pre-recloudstream era, why "3", any takedowns) — NOT documented in any local source; deliberately excluded.
- **JitPack publishing pipeline** — `jitpack.yml` (openjdk17) and consumer coordinates exist, but I did not build via JitPack; exact artifact freshness of `com.lagradost:cloudstream3:pre-release` stubs vs `com.github.recloudstream.cloudstream:library:-SNAPSHOT` unverified.
- **What `webMain`/Zipline are for** — dormant source set + unreferenced dependency; "future web/JS plugin target" is inference only.
- **F-Droid / other store presence** — fastlane metadata exists; active listing not provable from the repo.
- **Community repo contents beyond our 11 clones** — claims about repo quality/size limited to `repos-db.json` + the cloned sample; counts (e.g. phisher "~100 plugins") come from the tracker, not re-verified here.
- **Discoverium's exact role** — file contents verified; what Discoverium does with it is outside our sources.
- **Whether `docs` module output is published anywhere** — generate_dokka.yml exists; deployment target not read.

*End of doc 01. Next: `02-plugin-format.md` (B1-b) does the `.cs3`/`.jar` forensics this doc deliberately deferred.*
