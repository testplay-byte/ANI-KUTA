# 03 — Tech Stack

> Analysis of every library, plugin, version, and build setting used by the
> old ANIKUTA project. Source: `REFERENCES/old-kuta/ANIKUTA/`.

---

## 1. Summary (cheat sheet)

| Layer | Technology | Version |
|---|---|---|
| **Language** | Kotlin | **2.2.0** |
| **UI** | Jetpack Compose (BOM) | **2025.03.00** |
| **Compose Compiler plugin** | `org.jetbrains.kotlin.plugin.compose` | 2.2.0 |
| **DI (host)** | Koin (BOM) | **4.0.0** |
| **DI (extensions)** | Injekt (JitPack fork) | `91edab2317` |
| **Persistence** | SQLDelight | **2.0.2** |
| **SQLite engine** | requery sqlite-android | 3.45.0 |
| **Player** | aniyomi-mpv-lib (MPV) | **1.18.n** |
| **Player support libs** | ffmpeg-kit, smart-exception, NanoHTTPD, Media session, Seeker, TrueTypeParser | various |
| **Networking** | OkHttp | **5.0.0-alpha.14** |
| **HTML parsing** | Jsoup | 1.19.1 |
| **TLS** | Conscrypt | 2.5.3 |
| **JS engine** | QuickJS (for extension JS) | 0.9.2 |
| **Image loading** | Coil3 (BOM) | **3.1.0** |
| **Navigation** | Voyager | **1.0.1** |
| **Coroutines** | kotlinx-coroutines (BOM) | **1.10.1** |
| **Serialization** | kotlinx-serialization-json | **1.9.0** |
| **XML serialization** | pdvrieze xmlutil | 0.90.3 |
| **Immutable collections** | kotlinx-collections-immutable | 0.3.8 |
| **Lifecycle** | androidx.lifecycle | 2.8.7 |
| **Activity Compose** | androidx.activity-compose | 1.10.1 |
| **Paging** | androidx.paging | 3.3.6 |
| **WorkManager** | androidx.work | 2.10.0 |
| **Splashscreen** | androidx.core-splashscreen | 1.0.1 |
| **i18n (planned)** | Moko Resources | 0.24.5 |
| **AboutLibraries** | com.mikepenz | 11.6.3 |
| **Shizuku** | dev.rikka.shizuku | 13.1.0 |
| **LeakCanary** | com.squareup.leakcanary | 2.14 |
| **Logging** | com.squareup.logcat | 0.1 |
| **Spotless (formatter)** | com.diffplug.spotless | 7.0.2 |
| **ktlint** | com.pinterest.ktlint | 1.5.0 |
| **Testing** | JUnit Jupiter + Kotest + MockK | 5.11.4 / 5.9.1 / 1.13.17 |
| **Build** | Gradle | **8.13** |
| **AGP** | Android Gradle Plugin | **8.9.1** |
| **JDK** | Java | **17** |
| **Compile/Target SDK** | Android | **36** / 36 |
| **Min SDK** | Android | **26** |
| **NDK** | 27.1.12297006 | |
| **Build-tools** | 35.0.1 | |
| **Toolchain resolver** | foojay-resolver-convention | 0.9.0 |

---

## 2. Build tooling

### 2.1 Gradle

- **Gradle version: 8.13** — from `gradle/wrapper/gradle-wrapper.properties`
  (`distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip`).
- `networkTimeout=10000`, `validateDistributionUrl=true`.
- The `gradlew` / `gradlew.bat` wrapper scripts are committed; the
  `gradle-wrapper.jar` is committed too (43504 bytes).
- `settings.gradle.kts` enables the **foojay-resolver-convention** plugin
  (`0.9.0`) for JDK auto-provisioning via Gradle toolchains.

### 2.2 Android Gradle Plugin

- **AGP version: 8.9.1** — declared as `agp_version` in
  `gradle/androidx.versions.toml` and consumed by the convention plugins via
  `androidx.gradle` (`com.android.tools.build:gradle:8.9.1`).
- AGP 8.9 requires Gradle ≥ 8.11 — the project uses 8.13 (compatible).

### 2.3 JDK

- **JDK 17** — pinned in `buildSrc/build.gradle.kts`
  (`kotlin { jvmToolchain(17) }`) and in `AndroidConfig.kt`
  (`JavaVersion.VERSION_17` + `JvmTarget.JVM_17`).
- All Kotlin compilation targets JVM 17 bytecode.
- Core library desugaring enabled (`com.android.tools:desugar_jdk_libs:2.1.5`)
  so java.time APIs work on minSdk 26 (technically not needed at 26+, but
  retained for forward-compat with newer JDK APIs).

### 2.4 Kotlin

- **Kotlin 2.2.0** — declared as `kotlin_version` in
  `gradle/kotlinx.versions.toml`. Used for:
  - The Kotlin Gradle plugin (`org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0`)
  - The Compose Compiler Gradle plugin
    (`org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.2.0`)
  - Kotlin reflect (`org.jetbrains.kotlin:kotlin-reflect:2.2.0`)
  - The `org.jetbrains.kotlin.android` plugin
  - The `org.jetbrains.kotlin.plugin.compose` plugin
  - The `org.jetbrains.kotlin.plugin.serialization` plugin
- Kotlin code style: `official` (from `gradle.properties`).
- `kotlin.mpp.androidSourceSetLayoutVersion=2` — the modern KMP source-set
  layout (required for some Kotlin multiplatform features used by SQLDelight).

### 2.5 Compose Compiler

- The Compose Compiler is provided by **`org.jetbrains.kotlin.plugin.compose`**
  version `2.2.0` (matches the Kotlin version — required since Kotlin 2.0
  unified the compiler and Compose Compiler versions).
- Applied via the convention plugins `anikuta.library.compose` and
  `anikuta.android.application.compose`.

### 2.6 Spotless + ktlint (formatting)

- **Spotless 7.0.2** (`com.diffplug.spotless:spotless-plugin-gradle:7.0.2`) —
  applied at the buildSrc level (the buildSrc `build.gradle.kts` depends on
  `libs.spotless.gradle`).
- **ktlint 1.5.0** (`com.pinterest.ktlint:ktlint-cli:1.5.0`) — used as the
  Spotless rule set.
- The root `build.gradle.kts` does NOT apply Spotless explicitly; it appears
  to be wired in buildSrc but not yet enforced across modules (no
  `spotless { … }` block visible in the root or convention plugins). This is
  a gap.

---

## 3. UI stack

### 3.1 Jetpack Compose

| Artifact | Version | Source catalog |
|---|---|---|
| `androidx.compose:compose-bom` | **2025.03.00** | `compose.bom` (compose.versions.toml) + hardcoded in convention plugins |
| `androidx.compose.foundation:foundation` | (BOM-managed) | compose.versions.toml |
| `androidx.compose.animation:animation` | (BOM-managed) | compose.versions.toml |
| `androidx.compose.animation:animation-graphics` | (BOM-managed) | compose.versions.toml |
| `androidx.compose.runtime:runtime` | (BOM-managed) | compose.versions.toml |
| `androidx.compose.ui:ui-tooling` | (BOM-managed) | compose.versions.toml (debug only) |
| `androidx.compose.ui:ui-tooling-preview` | (BOM-managed) | compose.versions.toml |
| `androidx.compose.ui:ui-util` | (BOM-managed) | compose.versions.toml |
| `androidx.compose.material3:material3` | (BOM-managed) | compose.versions.toml |
| `androidx.compose.material:material-icons-extended` | (BOM-managed) | compose.versions.toml |
| `androidx.activity:activity-compose` | **1.10.1** | compose.versions.toml |
| `androidx.constraintlayout:constraintlayout-compose` | 1.1.0 | anikuta.versions.toml |

The convention plugins apply the Compose BOM as a platform dependency and
bring the standard set of Compose artifacts. Individual modules add
extras (e.g., `:feature:anime-details` brings coil-compose + coil-network-okhttp
for image loading).

### 3.2 Material Design

- **Material 3** (`androidx.compose.material3:material3`) is the primary
  design system. The app's theme (`AnikutaTheme` in `:core:designsystem`)
  is built on top of M3 `colorScheme`, `typography`, and `shapes`.
- **Material Icons Extended** (`androidx.compose.material:material-icons-extended`)
  for the full icon set (used heavily in `MoreScreen`, settings rows, player
  controls).

### 3.3 Image loading

- **Coil3** (the new Coroutine-based rewrite) — version **3.1.0** via BOM.
- Artifacts used:
  - `io.coil-kt.coil3:coil` (core)
  - `io.coil-kt.coil3:coil-gif` (GIF support)
  - `io.coil-kt.coil3:coil-compose` (Compose integration)
  - `io.coil-kt.coil3:coil-network-okhttp` (OkHttp fetcher)
- Bundled in `libs.versions.toml` as a bundle (`coil`).

### 3.4 Navigation

- **Voyager 1.0.1** — single-Navigator architecture.
- Artifacts (all bundled in `libs.versions.toml` as `voyager` bundle):
  - `cafe.adriel.voyager:voyager-core` — `Screen`, `Navigator` base types
  - `cafe.adriel.voyager:voyager-navigator` — the `Navigator` composable
  - `cafe.adriel.voyager:voyager-screenmodel` — `ScreenModel` (Voyager's ViewModel)
  - `cafe.adriel.voyager:voyager-tab-navigator` — `TabNavigator` (unused in this
    app — the bottom-nav uses `navigator.replace()` instead)
  - `cafe.adriel.voyager:voyager-transitions` — `FadeTransition` (used in `AnikutaRoot`)

### 3.5 Custom UI libraries

| Library | Version | Purpose |
|---|---|---|
| `io.github.2307vivek:seeker` | 1.2.2 | Compose seekbar with thumbnails (used in player) |
| `androidx.constraintlayout:constraintlayout-compose` | 1.1.0 | ConstraintLayout for Compose (used in player UI) |

---

## 4. Persistence stack

### 4.1 SQLDelight

- **SQLDelight 2.0.2** — declared in `gradle/libs.versions.toml` and applied
  via `app.cash.sqldelight` plugin in the root `build.gradle.kts`.
- Artifacts:
  - `app.cash.sqldelight:android-driver` — Android driver
  - `app.cash.sqldelight:coroutines-extensions-jvm` — Flow/async helpers
  - `app.cash.sqldelight:androidx-paging3-extensions` — Paging 3 integration
  - `app.cash.sqldelight:sqlite-3-38-dialect` — SQL dialect (SQLite 3.38+)
- Schema files live in `:core:database/src/main/sqldelight/app/confused/anikuta/core/database/`:
  - `animes.sq` — anime entries (with `local_id`, `content_id`, `anilist_id`,
    `source_id`, `url`, `title`, `description`, `genre`, `cover_url`,
    `cover_color`, `status`, `artist`, `author`, `favorite`, `date_added`, …)
  - `episodes.sq` — cached episode lists per anime
  - `categories.sq` — library categories (with a seeded "Default")
  - `anime_category.sq` — many-to-many link table
  - `animehistory.sq` — watch history
  - `animetrack.sq` — tracker entries (AniList + MAL)
  - `1.sqm` — first migration (seeds "Default" category on existing installs)
  - `2.sqm` — adds `local_id` + `content_id` columns (nullable, backfilled
    at runtime by `App.kt` Phase 1)
- The driver factory is `:core:database/DatabaseDriverFactory.kt`, registered
  in Koin via `:app/di/DatabaseModule.kt`.

### 4.2 SQLite engine

- **`com.github.requery:sqlite-android:3.45.0`** — bundled SQLite (newer than
  the Android system SQLite, supports modern SQL features).
- Plus `androidx.sqlite:sqlite-framework:2.4.0` + `androidx.sqlite:sqlite-ktx:2.4.0`
  (the framework SQLite bindings).

### 4.3 Preferences (SharedPreferences abstraction)

- **`androidx.preference:preference-ktx:1.2.1`** — the AndroidX Preferences
  backport.
- The app wraps this in its own `PreferenceStore` interface
  (`:core:preferences/PreferenceStore.kt`) with `AndroidPreferenceStore` as
  the impl. Every typed preference (theme, ads, downloads, setup-wizard,
  content-id, episode-display, linking, details-view) is a `Preference<T>`
  with `get()`, `set(value)`, and `changes(): Flow<T>` (reactive).
- This abstraction means storage could be swapped to DataStore without
  touching call sites.

### 4.4 Disk cache

- **`com.jakewharton:disklrucache:2.0.2`** — Disk LRU cache (used by image
  cache + AniList response cache).
- **`com.github.tachiyomiorg:unifile:e0def6b3dc`** — Tachiyomi's UnifiedFile
  abstraction (handles SAF + plain files + content URIs uniformly). Used by
  the download manager + backup system.

---

## 5. Player stack

The player is built on **MPV** (the same engine Aniyomi uses), wrapped in a
single Compose-friendly AndroidView.

### 5.1 MPV

- **`com.github.aniyomiorg:aniyomi-mpv-lib:1.18.n`** — Aniyomi's fork of
  mpv-android. Provides `MPVLib` (JNI bindings) + `BaseMPVView`.
- Declared as `api` (not `implementation`) in `:core:player/build.gradle.kts`
  because `AnikutaMPVView` extends `is.xyz.mpv.BaseMPVView` — consumers need
  to see the supertype.
- The native `.so` files (libmpv.so + dependencies) are bundled in the AAR.

### 5.2 Player support libraries

| Library | Version | Purpose |
|---|---|---|
| `com.github.jmir1:ffmpeg-kit` | 1.18 | FFmpeg (libmpv.so is dynamically linked against it) |
| `com.arthenica:smart-exception-java` | 0.2.1 | Smart exception handling for native crashes |
| `org.nanohttpd:nanohttpd` | 2.3.1 | Localhost HTTP proxy for proxied video URLs |
| `androidx.media:media` | 1.7.0 | Media session (background media controls) |
| `io.github.yubyf:truetypeparser-light` | 2.1.4 | Subtitle font parsing (for SSA/ASS subtitle styling) |
| `io.github.2307vivek:seeker` | 1.2.2 | Compose seekbar with thumbnails |
| `androidx.constraintlayout:constraintlayout-compose` | 1.1.0 | ConstraintLayout for player UI |

### 5.3 Player assets

- `:core:player/src/main/assets/subfont.ttf` — the default subtitle font
  bundled into the APK (loaded by MPV at runtime).

### 5.4 Subtitles

- `SubtitleTrackFormatter.kt` parses SSA/ASS + SRT subtitle streams.
- `truetypeparser-light` extracts font metadata for styling.
- `ColorPickerSheet.kt` lets the user customize subtitle colors.

---

## 6. Networking stack

### 6.1 OkHttp

- **OkHttp 5.0.0-alpha.14** — declared as `okhttp_version` in
  `gradle/libs.versions.toml`.
- Artifacts (bundled as `okhttp` bundle):
  - `com.squareup.okhttp3:okhttp` — core
  - `com.squareup.okhttp3:logging-interceptor` — HTTP logging
  - `com.squareup.okhttp3:okhttp-brotli` — Brotli compression
  - `com.squareup.okhttp3:okhttp-dnsoverhttps` — DNS-over-HTTPS
- Used by:
  - AniList GraphQL client (`:core:anilist/AniListApi.kt`)
  - Extension sources (via `NetworkHelper` in `:core:source-api/network/`)
  - Image fetcher (Coil3 `coil-network-okhttp`)
  - Download manager (`:core:download/HttpDownloader.kt`,
    `AdvancedHttpDownloader.kt`)
  - App update downloader (`:core:app-update/UpdateDownloader.kt`)

### 6.2 OkHttp interceptors (in `:core:source-api/network/interceptor/`)

| Interceptor | Purpose |
|---|---|
| `UserAgentInterceptor` | Sets a configurable User-Agent on every request |
| `RateLimitInterceptor` | Global per-host rate limiter (configurable) |
| `SpecificHostRateLimitInterceptor` | Per-host rate limit override |
| `IgnoreGzipInterceptor` | Strips `Accept-Encoding: gzip` for sources that misbehave |
| `UncaughtExceptionInterceptor` | Catches IOExceptions and rethrows as a typed SourceException |

### 6.3 TLS

- **`org.conscrypt:conscrypt-android:2.5.3`** — Conscrypt security provider
  (faster TLS + modern cipher suites on older Android versions).
- Initialized by `NetworkHelper` before the OkHttpClient is built.

### 6.4 HTML parsing

- **Jsoup 1.19.1** (`org.jsoup:jsoup:1.19.1`) — used by Aniyomi-compatible
  extensions (`ParsedAnimeHttpSource` extends with Jsoup CSS-selector helpers)
  + the app's own scraping where needed.

### 6.5 QuickJS (JavaScript engine)

- **`app.cash.quickjs:quickjs-android:0.9.2`** — used by extensions that
  evaluate JavaScript (e.g., to decrypt video URLs from cloudflare-protected
  sources). Loaded on demand by `AnimeExtensionManager`.

### 6.6 No Retrofit

The project does NOT use Retrofit. The AniList API client in `:core:anilist/AniListApi.kt`
is a hand-rolled GraphQL-over-OkHttp client (builds the POST body, parses the
JSON response manually with kotlinx.serialization). This matches the
Aniyomi-family pattern (extensions also bypass Retrofit).

---

## 7. Coroutines + serialization

### 7.1 Coroutines

- **kotlinx-coroutines 1.10.1** — managed via BOM
  (`org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.1`), brought in by the
  convention plugins.
- Artifacts used:
  - `kotlinx-coroutines-core`
  - `kotlinx-coroutines-android` (Dispatchers.Main + AndroidExceptionPreHandler)
  - `kotlinx-coroutines-guava` (ListenableFuture bridging, used by some
    AndroidX libs that expect Guava futures)
  - `kotlinx-coroutines-test` (test scope + virtual time)
- `:core:common/di/DispatcherProvider.kt` wraps the standard Dispatchers so
  tests can swap them.

### 7.2 Serialization

- **kotlinx-serialization 1.9.0** — declared as `serialization_version` in
  `gradle/kotlinx.versions.toml`. Plugin applied at the root
  (`org.jetbrains.kotlin.plugin.serialization` version `2.2.0`).
- Artifacts:
  - `kotlinx-serialization-json` — JSON
  - `kotlinx-serialization-json-okio` — JSON parsing with Okio `BufferedSource`
    (used for streaming large JSON responses)
  - `kotlinx-serialization-protobuf` — Protocol Buffers (used by the Aniyomi
    backup format reader in `:core:backup/format/AniyomiBackupFormat.kt`)
  - `io.github.pdvrieze.xmlutil:core-android` + `serialization-android`
    (version **0.90.3**) — XML serialization (used for AniList XML responses
    + MAL API XML format)

### 7.3 Immutable collections

- **kotlinx-collections-immutable 0.3.8** — used in Compose state holders to
  avoid unnecessary recompositions (immutable lists/maps are stable).

---

## 8. AndroidX libraries

From `gradle/androidx.versions.toml`:

| Artifact | Version | Purpose |
|---|---|---|
| `androidx.annotation:annotation` | 1.9.1 | Annotations (`@IntDef`, etc.) |
| `androidx.appcompat:appcompat` | 1.7.0 | AppCompat backport (used by ErrorActivity + ExtensionInstallService) |
| `androidx.biometric:biometric-ktx` | 1.2.0-alpha05 | Biometric prompt (app-lock feature, planned) |
| `androidx.constraintlayout:constraintlayout` | 2.2.1 | View-based ConstraintLayout (legacy, used by some player layouts) |
| `androidx.core:core-ktx` | 1.15.0 | Kotlin extensions for Android core |
| `androidx.core:core-splashscreen` | 1.0.1 | Splash screen API |
| `androidx.localbroadcastmanager:localbroadcastmanager` | 1.1.0 | Local broadcasts (legacy, used by extension install receiver) |
| `androidx.recyclerview:recyclerview` | 1.4.0 | RecyclerView (legacy, used by some non-Compose UI) |
| `androidx.viewpager:viewpager` | 1.1.0 | ViewPager (legacy, used by setup wizard) |
| `androidx.profileinstaller:profileinstaller` | 1.4.1 | Baseline profile installer |
| `androidx.lifecycle:lifecycle-*` | **2.8.7** | Lifecycle, ProcessLifecycleOwner, ViewModel, viewModel-compose |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.7 | `collectAsStateWithLifecycle` (used everywhere) |
| `androidx.work:work-runtime` | 2.10.0 | WorkManager (auto-backup scheduler) |
| `androidx.paging:paging-runtime` + `paging-compose` | 3.3.6 | Paging 3 (used by SQLDelight paging extensions) |
| `androidx.interpolator:interpolator` | 1.0.0 | Custom interpolators (player animations) |

### Bundles

- `lifecycle` bundle = `lifecycle-common` + `lifecycle-process` + `lifecycle-runtime-ktx`.

---

## 9. Extension compatibility (Aniyomi source-api)

The `:core:source-api` module ships the **Aniyomi-compatible** source
contracts, so unmodified Aniyomi/Keiyoushi-family extensions can be loaded
as APKs at runtime.

### Package layout

```
core/source-api/src/main/kotlin/eu/kanade/tachiyomi/
├── animesource/
│   ├── AnimeSource.kt                   ← base interface
│   ├── AnimeCatalogueSource.kt          ← catalogue (searchable) source
│   ├── AnimeSourceFactory.kt            ← multi-source factory
│   ├── ConfigurableAnimeSource.kt       ← source with a preference screen
│   ├── UnmeteredSource.kt               ← marker for unmetered sources
│   ├── online/
│   │   ├── AnimeHttpSource.kt           ← HTTP-based source (uses NetworkHelper)
│   │   ├── ParsedAnimeHttpSource.kt     ← Jsoup CSS-selector helpers
│   │   └── ResolvableAnimeSource.kt     ← source that can resolve URLs to video
│   ├── PreferenceScreen.kt              ← extension preference UI DSL
│   └── model/
│       ├── SAnime.kt, SAnimeImpl.kt     ← series
│       ├── SEpisode.kt, SEpisodeImpl.kt ← episode
│       ├── Video.kt                     ← playable video (URL + quality + audio)
│       ├── AnimesPage.kt                ← search result page
│       ├── AnimeFilter.kt, AnimeFilterList.kt ← filter DSL
│       ├── AnimeUpdateStrategy.kt       ← ALWAYS/NEVER update
│       ├── FetchType.kt                 ← EPISODE vs OTHER
│       ├── Hoster.kt                    ← video hoster abstraction
│       ├── HttpServer.kt                ← embedded HTTP server abstraction
│       └── ThumbnailInfo.kt             ← thumbnail URL + size
├── network/
│   ├── NetworkHelper.kt                 ← OkHttpClient factory (Injekt singleton)
│   ├── Requests.kt                      ← Request builders
│   ├── ProgressListener.kt              ← download progress callback
│   ├── ProgressResponseBody.kt          ← ResponseBody that emits progress
│   ├── OkHttpExtensions.kt              ← convenience extension functions
│   └── interceptor/
│       ├── UserAgentInterceptor.kt
│       ├── RateLimitInterceptor.kt
│       ├── SpecificHostRateLimitInterceptor.kt
│       ├── IgnoreGzipInterceptor.kt
│       └── UncaughtExceptionInterceptor.kt
└── util/
    ├── JsoupExtensions.kt               ← Jsoup helpers
    ├── JsonExtensions.kt                ← kotlinx.serialization helpers
    ├── RxExtension.kt                   ← RxJava backport (unused but kept for compat)
    └── VideoInfo.kt                     ← video metadata
```

### Host-provided Injekt singletons

Extensions expect these to be registered in Injekt at startup (see
`App.kt` §7 of `02-architecture.md`):

- `Application` + `Context`
- `NetworkHelper` (with a configured `OkHttpClient`)
- `Json` (kotlinx.serialization)

The app complies (ADR-029). Without these, any Keiyoushi-family extension
crashes with `InjektionException` on first call.

### Extension manager

`:data:extension/` ships:
- `AnimeExtensionManager` — main entry point; loads installed extensions,
  exposes them as `AnimeCatalogueSource` instances.
- `AnimeExtensionLoader` — uses a `ChildFirstPathClassLoader` to load
  extension APKs in an isolated classloader (so extensions can ship their
  own dependency versions without conflict).
- `AnimeExtensionApi` — fetches the extension catalog from extension repos
  (Tachiyomi/Aniyomi repo format).
- `AnimeExtensionInstaller` + `ExtensionInstallService` + `PackageInstallerBackend`
  + `ExtensionInstallReceiver` + `InstallStep` — the install pipeline
  (foreground service + system PackageInstaller + broadcast receiver).
- `SourceMatcher` — fuzzy-matches an extension SAnime to an AniList entry
  (used by the auto-link feature).
- `TrustExtension` — trust-on-first-use for extension signatures.

---

## 10. Other libraries

### 10.1 Logging

- **`com.squareup.logcat:logcat:0.1`** — tiny wrapper around `android.util.Log`
  that lets you write `Log.d { "message $variable" }` (the lambda isn't
  evaluated unless the log level passes — avoids string concatenation in
  release builds). Used across all modules.

### 10.2 LeakCanary

- **`com.squareup.leakcanary:leakcanary-android:2.14`** — memory leak
  detection. Auto-attach in debug builds.
- **`com.squareup.leakcanary:plumber-android:2.14`** — pre-emptive fixes for
  known Android framework leaks.

### 10.3 AboutLibraries

- **`com.mikepenz:aboutlibraries-compose-m3:11.6.3`** — the OSS licenses
  screen. Renders an M3-styled list of every library the app uses.
- The `com.mikepenz.aboutlibraries.plugin` Gradle plugin generates the
  licenses JSON at build time.

### 10.4 Shizuku

- **`dev.rikka.shizuku:api:13.1.0`** + **`dev.rikka.shizuku:provider:13.1.0`** —
  Shizuku integration for privileged operations without root (used by the
  extension installer to install APKs without the system installer dialog).
  Currently wired but not yet used (gated by user opt-in).

### 10.5 Moko Resources (i18n)

- **`dev.icerock.moko:resources:0.24.5`** — Moko Resources for type-safe
  string resources. The `:i18n` module was a phantom (declared but no
  directory existed); removed in Phase 9. ADR-027 plans to re-add it when
  the app needs proper i18n (currently all strings are hardcoded English).

---

## 11. Testing stack

From `gradle/libs.versions.toml`:

| Library | Version | Purpose |
|---|---|---|
| `org.junit.jupiter:junit-jupiter` | 5.11.4 | JUnit 5 (Jupiter) — the platform is configured via `useJUnitPlatform()` in convention plugins |
| `io.kotest:kotest-assertions-core` | 5.9.1 | Fluent assertions (`shouldBe`, `shouldContain`, etc.) |
| `io.mockk:mockk` | 1.13.17 | Mocking framework (Kotlin-native) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | (BOM 1.10.1) | `runTest`, `TestScope`, `TestDispatcher` |

Existing tests (sparse — most modules have no tests):
- `:core:common` — `LocalIdTest.kt`, `ContentIdTest.kt`, `SourceProvenanceTest.kt`
- `:core:player` — `WatchProgressStoreKeyTest.kt`
- `:core:episode-metadata` — `EpisodeMetadataCacheKeyTest.kt`
- `:core:provider-api` — `MetadataProviderRegistryTest.kt`
- `:data:anime` — `AnimeMapperTest.kt`
- `:app` — `ContentIdMigratorTest.kt`

The convention plugins configure `tasks.withType<Test> { useJUnitPlatform() }`
so any JUnit 5 test will run.

---

## 12. Version catalog breakdown

The project uses **5 separate version catalogs** instead of the conventional
single `libs.versions.toml`. This is a stylistic choice — the split makes it
easier to see which libraries belong to which ecosystem at a glance.

### 12.1 `gradle/libs.versions.toml` — the "main" catalog

The default catalog (auto-loaded by Gradle as `libs`). Contains everything
that doesn't fit in the four specialized catalogs:

- **Build tooling**: Spotless (`7.0.2`), ktlint (`1.5.0`), desugar (`2.1.5`)
- **Networking**: OkHttp (`5.0.0-alpha.14`), Okio (`3.10.2`), Conscrypt
  (`2.5.3`), QuickJS (`0.9.2`), Jsoup (`1.19.1`), DiskLRUCache (`2.0.2`),
  Unifile (`e0def6b3dc`)
- **Persistence**: SQLDelight (`2.0.2`), SQLite (`2.4.0` + requery `3.45.0`),
  Preference-KTX (`1.2.1`)
- **DI**: Koin BOM (`4.0.0`) + koin-core / koin-android / koin-androidx-compose
- **Image loading**: Coil3 BOM (`3.1.0`) + coil-core / coil-gif / coil-compose
  / coil-network-okhttp
- **Logging**: logcat (`0.1`), LeakCanary (`2.14`)
- **Navigation**: Voyager (`1.0.1`) — 5 artifacts
- **i18n**: Moko Resources (`0.24.5`)
- **UI extras**: AboutLibraries (`11.6.3`)
- **Shizuku** (`13.1.0`) — api + provider
- **Testing**: JUnit Jupiter (`5.11.4`), Kotest (`5.9.1`), MockK (`1.13.17`)
- **Bundles**: `okhttp`, `sqlite`, `koin`, `coil`, `sqldelight`, `voyager`, `test`
- **Plugins**: `aboutLibraries`, `sqldelight`, `moko`

### 12.2 `gradle/androidx.versions.toml` — AndroidX libraries

- **AGP** version (`8.9.1`) — referenced by buildSrc
- **AndroidX**: annotation, appcompat, biometric-ktx, constraintlayout,
  core-ktx, splashscreen, recyclerview, viewpager, localbroadcastmanager,
  profileinstaller
- **Lifecycle** (`2.8.7`) — common, process, runtime-ktx, viewmodel,
  viewmodel-compose
- **WorkManager** (`2.10.0`)
- **Paging** (`3.3.6`) — runtime + compose
- **Interpolator** (`1.0.0`)
- **Bundles**: `lifecycle`
- **Plugins**: `application`, `library` (both at AGP version)

### 12.3 `gradle/compose.versions.toml` — Jetpack Compose

- **Compose BOM** (`2025.03.00`)
- Activity-compose (`1.10.1`)
- All Compose artifacts (foundation, animation, animation-graphics, runtime,
  ui-tooling, ui-tooling-preview, ui-util, material3, material-icons-extended)
- No plugins (the Compose Compiler plugin comes from kotlinx.versions.toml
  since it's tied to the Kotlin version, not the Compose BOM version)

### 12.4 `gradle/kotlinx.versions.toml` — Kotlin + KotlinX

- **Kotlin** (`2.2.0`) — `kotlin-reflect`, `kotlin-gradle-plugin`,
  `compose-compiler-gradle-plugin`
- **kotlinx-collections-immutable** (`0.3.8`)
- **Coroutines** BOM (`1.10.1`) — core, android, guava, test
- **Serialization** (`1.9.0`) — json, json-okio, protobuf
- **XML serialization** (`0.90.3`) — pdvrieze xmlutil core + serialization
- **Bundles**: `coroutines`, `serialization`
- **Plugins**: `android`, `compose-compiler`, `serialization` (all at
  Kotlin version `2.2.0`)

### 12.5 `gradle/anikuta.versions.toml` — ANIKUTA-specific libraries

The custom name (`anikutaLibs` — assigned in `settings.gradle.kts`) holds
libraries that are specific to the streaming/player domain:

- **aniyomi-mpv-lib** (`1.18.n`) — the MPV player
- **ffmpeg-kit** (`1.18`) — FFmpeg for libmpv
- **arthenica-smartexceptions** (`0.2.1`) — native exception handling
- **constraint-layout** (`1.1.0`) — Compose ConstraintLayout
- **media** (`1.7.0`) — Media session
- **nanohttpd** (`2.3.1`) — localhost proxy
- **seeker** (`1.2.2`) — Compose seekbar
- **torrserver** (`0.1.0`) — TorrServer integration (planned, currently unused)
- **truetypeparser** (`2.1.4`) — subtitle font parsing

### Catalog access in build files

- `libs.foo` — from `libs.versions.toml` (default catalog)
- `androidx.foo` — from `androidx.versions.toml`
- `compose.foo` — from `compose.versions.toml`
- `kotlinx.foo` — from `kotlinx.versions.toml`
- `anikutaLibs.foo` — from `anikuta.versions.toml`

buildSrc has access to all 5 (via `buildSrc/settings.gradle.kts` which
re-imports the same 4 catalogs — note: buildSrc does NOT import
`anikuta.versions.toml`; convention plugins don't reference anikutaLibs).

### `TYPESAFE_PROJECT_ACCESSORS`

`settings.gradle.kts` enables `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`,
which lets modules write `projects.core.common` instead of `project(":core:common")`
— type-safe, IDE-completable, and catches typos at compile time.

---

## 13. Build configuration

### 13.1 `gradle.properties`

```properties
# Android
android.nonTransitiveRClass=false
android.useAndroidX=true

# Kotlin
kotlin.code.style=official
kotlin.mpp.androidSourceSetLayoutVersion=2

# Gradle performance
org.gradle.caching=true
org.gradle.configureondemand=true
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
```

**Key takeaways:**
- `android.nonTransitiveRClass=false` — the project uses **transitive R
  classes** (the default since AGP 8.0+). This means each module's `R` class
  includes resources from its dependencies — slightly larger, but no need
  to qualify resource references.
- `org.gradle.parallel=true` + `org.gradle.caching=true` +
  `org.gradle.configureondemand=true` — full performance mode.
- `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8` — 4 GB Gradle heap
  (necessary for 36 modules with Compose).

Notably absent:
- No `org.gradle.configuration-cache=true` — configuration cache is NOT
  enabled (likely because of the Voyager/Koin quirks).
- No `android.defaults.buildfeatures.buildconfig=true` — `buildConfig` is
  opted-in per-module via `buildFeatures { buildConfig = true }` (only `:app`
  does this, for the `BETA_BUILD` flag).

### 13.2 `AndroidConfig.kt` (recap)

```kotlin
object AndroidConfig {
    const val COMPILE_SDK = 36
    const val TARGET_SDK = 36
    const val MIN_SDK = 26
    const val NDK = "27.1.12297006"
    const val BUILD_TOOLS = "35.0.1"

    val JavaVersion = GradleJavaVersion.VERSION_17
    val JvmTarget = KotlinJvmTarget.JVM_17

    const val APPLICATION_ID = "app.confused.anikuta"
    const val VERSION_CODE = 100
    const val VERSION_NAME = "1.0.0"
}
```

Every convention plugin reads from this object, so changing the SDK or
application ID is a one-line edit.

### 13.3 `:app/build.gradle.kts` specifics

Beyond the convention plugin defaults, `:app/build.gradle.kts` configures:

- **ABI splits**: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` + universal APK
  (all four ABIs in one APK). This is broader than the ADR-032 recommendation
  of "arm64-v8a only" — likely a regression or an intentional broadening for
  emulator testing.
- **Debug signing**: a committed debug keystore
  (`app/anikuta-debug.keystore`, password `android`, alias `anikuta-debug`)
  signs debug builds so CI builds are consistently signed and users can
  update without uninstalling.
- **`BETA_BUILD` BuildConfig field**: `true` for debug, `false` for release
  (see `02-architecture.md` §8.5).
- **Release minify**: `isMinifyEnabled = false` (no R8 in release builds —
  a gap for production).
- **ProGuard rules**: `proguard-android-optimize.txt` + `app/proguard-rules.pro`
  are referenced but unused since minify is off.

### 13.4 `.gitignore`

The committed `.gitignore` covers:
- Build outputs (`**/build/`, `**/.gradle/`, `**/captures/`)
- IDE files (`*.iml`, `.idea/`, `.vscode/` — except `.vscode/extensions.json`)
- Local config (`local.properties`)
- OS files (`.DS_Store`, `Thumbs.db`)
- Secrets (`*.keystore`, `*.jks`, `*.p12`, `.env`, `google-services.json`)
- Logs (`*.log`)
- **Exception**: `!anikuta-debug.keystore` — the debug keystore IS committed
  (for CI build consistency), but release keystores never are.

### 13.5 `AndroidManifest.xml` (`:app`)

Permissions declared:
- `INTERNET` — basic networking
- `REQUEST_INSTALL_PACKAGES` — extension + app-update APK installs
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` — extension install
  service + download service
- `POST_NOTIFICATIONS` — Android 13+ notification permission (downloads +
  extension install)
- `QUERY_ALL_PACKAGES` — required to detect installed extensions on Android 14+
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — Setup Wizard battery-permission flow
- `MANAGE_EXTERNAL_STORAGE` — download folder feature + Setup Wizard all-files toggle

Components declared:
- `MainActivity` — `singleTask` launch mode (for OAuth callback intent
  handling), `MAIN`/`LAUNCHER` intent filter, two `aniyomi://` OAuth
  intent-filters (`anilist-auth` + `myanimelist-auth`)
- `ErrorActivity` — not exported, `taskAffinity=""`, `excludeFromRecents=true`
- `ExtensionInstallService` — `foregroundServiceType="dataSync"`, not exported
- `FileProvider` — for sharing downloaded APKs with the system installer

Application attributes:
- `android:largeHeap="true"` — needed for image caching + MPV
- `android:networkSecurityConfig="@xml/network_security_config"` — explicit
  cleartext traffic allowed for some extension sources
- `android:usesCleartextTraffic="true"` — same reason
- `android:allowBackup="true"`, `android:supportsRtl="true"`

---

## 14. Observations for the rebuild

(Expanded in `09-rebuild-notes.md`.)

1. **Kotlin 2.2.0 is bleeding edge** — the rebuild can pin to a more
   conservative Kotlin (2.0.x or 2.1.x) for stability, but the Compose
   Compiler plugin version MUST match the Kotlin version.
2. **OkHttp 5.0.0-alpha.14 is risky** — alpha APIs can change. The rebuild
   should evaluate OkHttp 4.12.0 (stable) vs 5.x (alpha but newer features).
3. **compileSdk = 36 is bleeding edge** — the rebuild can downgrade to 35
   (Android 15) for stability. minSdk 26 is fine.
4. **5 version catalogs is overkill** — collapsing to 2 (`libs` + `anikuta`)
   or even 1 would be simpler. But the current split is not actively harmful.
5. **R8 is off in release** — must be turned on + ProGuard rules written
   before any production release.
6. **No `proguard-rules.pro` content of note** — the file exists but is
   minimal/empty. Real rules needed for: SQLDelight generated code,
   kotlinx-serialization, Koin, MPV JNI, extension classloaders.
7. **Injekt + Koin dual-DI is intentional** — keep both. Document the reason
   (Aniyomi extension compatibility, ADR-029) prominently.
8. **Voyager 1.0.1 has a known state-restoration gap** — `AnikutaRoot.kt`
   has a TODO about back-stack loss on Activity recreate. The rebuild
   should pin to a newer Voyager when Saver support lands, or build a
   custom Saver.
9. **The `BETA_BUILD` flag is wired but half-used** — it gates the update
   source but doesn't gate anything else. The rebuild should formalize the
   beta-vs-stable split (different app IDs, different update sources,
   different icons).
10. **No `configuration-cache`** — Gradle config cache is disabled. Enabling
    it would speed up cold builds significantly, but requires every plugin
    and build script to be config-cache compliant (Koin + Voyager have
    historically had issues).
11. **No baseline profile** — `androidx.profileinstaller:profileinstaller`
    is included but no `baselineprofile` module exists. The rebuild should
    add a `:baselineprofile` module + macrobenchmark to generate the profile.
12. **Transitive R class** — `android.nonTransitiveRClass=false` is the
    default but increases APK size. Setting it to `true` (the modern
    recommendation) would slightly shrink the APK and speed up builds.
