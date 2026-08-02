# :core:source-api

> This module ships the `eu.kanade.tachiyomi.animesource.*` package — a binary
> compatibility contract for loading extension APKs. The package name is a
> technical requirement, not branding. Our own modules use
> `com.confused.anikuta.*` naming.

## Why this module exists

ANI-KUTA loads third-party anime source extensions packaged as standalone
APKs. These extensions are compiled against the Aniyomi/Keiyoushi
`eu.kanade.tachiyomi.animesource.*` API surface and expect to find those
exact classes — same package, same names, same method signatures — on the
host classpath at runtime. If the package name or any public symbol diverges
from what the extension was compiled against, the extension throws
`ClassNotFoundException`, `NoSuchMethodError`, or
`IncompatibleClassChangeError` at load time.

This module therefore exists to **re-publish that exact API surface** from
the ANI-KUTA host so that off-the-shelf Aniyomi/Keiyoushi extensions load
without recompilation (ADR-029 extension compat).

## What's inside

| Path | Purpose |
| --- | --- |
| `eu/kanade/tachiyomi/animesource/` | `AnimeSource`, `AnimeCatalogueSource`, `AnimeSourceFactory`, `ConfigurableAnimeSource`, `UnmeteredSource`, `PreferenceScreen`, `ExtensionAppHolder` |
| `eu/kanade/tachiyomi/animesource/online/` | `AnimeHttpSource`, `ParsedAnimeHttpSource`, `ResolvableAnimeSource` |
| `eu/kanade/tachiyomi/animesource/model/` | `SAnime`, `SEpisode`, `Video`, `Hoster`, `AnimeFilter*`, `AnimesPage`, `HttpServer`, `ThumbnailInfo`, `FetchType`, `AnimeUpdateStrategy`, plus `SerializableVideo` / `SerializableHoster` round-trip helpers |
| `eu/kanade/tachiyomi/network/` | `NetworkHelper`, `Requests`, `OkHttpExtensions`, `ProgressListener`, `ProgressResponseBody` |
| `eu/kanade/tachiyomi/network/interceptor/` | `UserAgentInterceptor`, `RateLimitInterceptor`, `SpecificHostRateLimitInterceptor`, `IgnoreGzipInterceptor`, `UncaughtExceptionInterceptor` |
| `eu/kanade/tachiyomi/util/` | `JsoupExtensions`, `JsonExtensions`, `RxExtension`, `VideoInfo` |
| `eu/kanade/tachiyomi/animesource/injekt/` | `SourceApiInjekt` — host-side bootstrap that registers `Application`, `Context`, `NetworkHelper`, `Json` in Injekt before any extension loads |

Total: **36** Kotlin files copied verbatim from the reference project + 1
bootstrap file added by this module.

## CRITICAL — do not rename, modernize, or "improve"

Every file under `eu/kanade/tachiyomi/` is a binary compatibility surface.
Touching any of the following will break extension loading:

- Package name (`eu.kanade.tachiyomi.*` — never `com.confused.anikuta.*`)
- Class / interface / object names
- Method names and signatures (parameter types, return types, nullability)
- Field names and types (especially `Video.headers: okhttp3.Headers?`)
- Whether a type is a `class` vs `interface` (extension bytecode uses
  `invokevirtual` for classes and `invokeinterface` for interfaces —
  swapping them throws `IncompatibleClassChangeError`)
- File names of top-level function containers — extensions call e.g.
  `OkHttpExtensionsKt.asObservableSuccess(...)` by file-class name

Comments, KDoc, and `@Suppress` annotations are safe to edit. Behavior of
default method bodies is safe to refine as long as the signature stays
identical.

## Host integration

The host Application must call `SourceApiInjekt.bootstrap(this)` early in
`onCreate()`, **before** the extension loader runs:

```kotlin
class AnikutaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Register host singletons in Injekt for extension compat (ADR-029).
        // MUST happen before any extension source is loaded.
        SourceApiInjekt.bootstrap(this)

        // ...start Koin, load extensions, etc.
    }
}
```

`SourceApiInjekt.bootstrap` registers:

1. `Application` — Keiyoushi extensions call `Injekt.get<Application>()`.
2. `Context` — extensions resolve the app context for SharedPreferences, etc.
3. `NetworkHelper` — `AnimeHttpSource` resolves it via `by injectLazy()`.
4. `Json` — extensions call `Injekt.get<Json>()` in static initializers.

## Dependencies

This module depends on third-party libraries that extensions expect on the
host classpath. The most notable:

- **OkHttp** (`api`) — `Video.headers` is a public field of type
  `okhttp3.Headers`; consumers must see the type.
- **Jsoup** — `ParsedAnimeHttpSource` and `JsoupExtensions`.
- **kotlinx-serialization-json** + **-okio** — `@Serializable` models and
  `Response.parseAs<T>()` (uses context receivers; see build script for
  `-Xcontext-receivers` flag).
- **kotlinx-coroutines-core** — `suspendCancellableCoroutine` bridges.
- **RxJava 1.x** + **RxAndroid** — deprecated `fetch*` API that extensions
  still call; `RxExtension.kt` bridges `Observable` → suspend.
- **NanoHTTPD** — `HttpServer` model used by some resolver-style sources.
- **Injekt** (`api`) — extensions resolve `NetworkHelper` and friends via
  `Injekt.get<T>()`; `AnimeHttpSource` uses `by injectLazy()`.
- **AndroidX Preference** — `PreferenceScreen` typealias target.

See `build.gradle.kts` for the full dependency list with versions.

## Module wiring

Add to `settings.gradle.kts`:

```kotlin
include(":core:source-api")
```

Consume from other modules:

```kotlin
dependencies {
    implementation(project(":core:source-api"))
}
```

## Notes on the `eu.kanade.*` package naming

The `eu.kanade.tachiyomi.*` package is a historical artifact from the
Tachiyomi/Aniyomi lineage. ANI-KUTA is **not** affiliated with Tachiyomi or
Aniyomi; we use this package name solely so that existing extension APKs
(whose bytecode references `eu.kanade.tachiyomi.animesource.*` types) load
without recompilation. All ANI-KUTA-original code lives under
`com.confused.anikuta.*`.

If, in the future, ANI-KUTA ships its own first-party extension API, that
API will live under `com.confused.anikuta.*` and will not be subject to the
binary-compat constraints of this module.
