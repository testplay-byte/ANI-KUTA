# 01 — Project Overview

> Repo metadata, module structure, build configuration, and key dependencies
> for the Animiru Android app.

## 1. Repo metadata

| Field | Value | Source |
|-------|-------|--------|
| Author / org | Quickdesh | `README.md:7` |
| Upstream | Fork of Aniyomi (which forks Mihon / Tachiyomi) | `README.md:8` |
| License | Apache 2.0 | `LICENSE` |
| App ID (release) | `xyz.Quickdev.Animiru.mi` | `app/build.gradle.kts:20` |
| App ID suffixes | `.dev` (debug), `.debug` (preview), `.benchmark` (benchmark) | `app/build.gradle.kts:34-73` |
| versionCode | 145 | `app/build.gradle.kts:22` |
| versionName | 0.19.8.0 | `app/build.gradle.kts:23` |
| Min Android | 8.0 (API 26) | `gradle/mihon.versions.toml:3` |
| Target SDK | 36 | `gradle/mihon.versions.toml:5` |
| Compile SDK | 37 | `gradle/mihon.versions.toml:4` |
| NDK | 29.0.14206865 | `gradle/mihon.versions.toml:6` |
| Java | 17 | `gradle/mihon.versions.toml:7` |
| Kotlin | 2.4.0 | `gradle/libs.versions.toml:45` |
| Android Gradle Plugin | 9.2.1 | `gradle/libs.versions.toml:4` |
| Compose BOM | 2026.05.01 (alpha) | `gradle/libs.versions.toml:10` |

The README explicitly states: *"A configurable player built on mpv-android
with multiple options and settings"* — `README.md:33`. The whole video
player is built around MPV, not ExoPlayer.

## 2. Module structure

From `settings.gradle.kts`:

```
:app                  ← Android application module (player, UI, extensions, DI)
:core:archive         ← Archive extraction (libarchive-jni wrapper)
:core:common          ← Shared utilities (preferences, coroutines, logging)
:core-metadata        ← Manga/anime metadata parsing
:data                 ← SQLDelight database, repositories, DataStore-backed prefs
:domain               ← Interactors / domain models (no Android deps in clean parts)
:i18n                 ← Moko resources — base Tachiyomi strings (Tachiyomi+Mihon)
:i18n-aniyomi         ← Moko resources — Aniyomi-specific strings
:i18n-animiru         ← Moko resources — Animiru-specific strings (AM* markers)
:macrobenchmark       ← Baseline profile + startup benchmarks
:presentation-core    ← Shared Compose components / theme / util
:presentation-widget  ← Glance app widgets
:source-api           ← Extension API (AnimeSource, AnimeHttpSource, Hoster, Video, etc.)
:source-local         ← Local-source filesystem (SAF-backed)
```

There are three "i18n" modules, layered:
- `:i18n` carries the Mihon/Tachiyomi strings (`MR.strings.*`).
- `:i18n-aniyomi` carries Aniyomi strings (`AYMR.strings.*`).
- `:i18n-animiru` carries Animiru-only strings (`AMMR.strings.*`).

This layering is visible throughout the player code, e.g. in
`PlayerEnums.kt`:
```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerEnums.kt:97-101
enum class Debanding(val stringRes: StringResource) {
    None(AMMR.strings.player_sheets_deband_none),
    CPU(AMMR.strings.player_sheets_deband_cpu),
    GPU(AMMR.strings.player_sheets_deband_gpu),
}
```

### Build-logic

Build logic is split into a composite build (`gradle/build-logic/`) with
convention plugins:

| Plugin | Purpose |
|--------|---------|
| `mihon.plugins.android.application` | Android Application + base config |
| `mihon.plugins.android.library` | Android Library + base config |
| `mihon.plugins.android.base` | Shared Android config (SDK, Java, etc.) |
| `mihon.plugins.compose.android` | Compose + Kotlin compiler plugin |
| `mihon.plugins.kotlin.multiplatform` | KMP setup (used by `:i18n`, `:source-api`) |
| `mihon.plugins.spotless` | KtLint formatting |

These plugins read SDK/NDK versions from `gradle/mihon.versions.toml`.

### Animiru-specific markers in code

Throughout the codebase you'll see these comment pairs:
```kotlin
// AM (FEATURE_NAME) -->
... code ...
// <-- AM (FEATURE_NAME)
```
These are Animiru-specific additions over Aniyomi. Similarly `// AY -->` /
`// <-- AY` marks Aniyomi-specific additions over Mihon/Tachiyomi. This
convention makes it easy to see what's a fork-specific feature.

Examples in the player code:
- `// AM (SYNC_DRIVE) -->` — Google Drive sync (build.gradle deps).
- `// AM (DISCORD_RPC) -->` — Discord rich presence (commented out in PlayerActivity).
- `// AM (CUSTOM_INFORMATION) -->` — Custom anime title handling.
- `// AM (SYNC) -->` — Sync preferences.

## 3. Build configuration

### ABIs

```kotlin
// app/build.gradle.kts:81-88
splits {
    abi {
        isEnable = true
        isUniversalApk = true
        reset()
        include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    }
}
```
All four major ABIs are supported — universal APK is enabled.

> ANI-KUTA: ANI-KUTA restricts to `arm64-v8a` + `armeabi-v7a` only
> (`CORE_RULES.md §8`). Animiru ships x86 too.

### Native libs preserved (debug symbols)

`app/build.gradle.kts:90-115` keeps debug symbols for these native libs:
- `libmpv.so` — MPV core (the player itself)
- `libplayer.so` — custom player JNI glue
- `libavcodec/avformat/avfilter/avutil/swresample/swscale/postproc.so` — FFmpeg
- `libffmpegkit.so` + `libffmpegkit_abidetect.so` — ffmpeg-kit
- `libarchive-jni.so` — libarchive wrapper (for CBZ/EPUB archives)
- `libimage-decoder.so` — image decoder
- `libsqlite3x.so` — SQLite native
- `libquickjs.so` — QuickJS engine (extension scripting)
- `libconscrypt_jni.so` — TLS 1.3 support
- `libxml2.so` — XML parsing

### Build types

| Build type | ApplicationId suffix | VersionName suffix | Notes |
|------------|----------------------|---------------------|-------|
| `debug` | `.dev` | `-${commitCount}` | pseudo-locales enabled |
| `release` | (none) | (none) | R8 minify + resource shrink |
| `preview` | `.debug` | (debug's) | init from release, debug-signed |
| `benchmark` | `.benchmark` | `-benchmark` | profileable, not debuggable |

### Compose opt-ins

`app/build.gradle.kts:149-167` declares a large set of `@OptIn` annotations
as free compiler args, including `ExperimentalMaterial3ExpressiveApi`,
`ExperimentalFoundationApi`, `ExperimentalAnimationApi`. This is the standard
Mihon setup.

## 4. Key dependencies (player-relevant only)

From `gradle/libs.versions.toml` and `gradle/aniyomi.versions.toml`:

### Player stack
| Dep | Version | Purpose |
|-----|---------|---------|
| `io.github.secozzi:mpv-android-lib` | 0.1.14 | MPV JNI binding (`is.xyz.mpv.*`). Source: `Secozzi/mpv-android` fork, see `README.md:53`. |
| `com.github.jmir1:ffmpeg-kit` | 1.18 | FFmpeg binaries (for streaming features). |
| `com.arthenica:smart-exception-java` | 0.2.1 | Required by ffmpeg-kit. |
| `io.github.2307vivek:seeker` | 1.2.2 | Compose Seekbar with chapter segments. |
| `io.github.yubyf:truetypeparser-light` | 2.1.4 | Parse TTF/OTF font families for subtitle font picker. |
| `androidx.media:media` | 1.7.0 | MediaSession for headset controls / lock screen. |

### Compose
| Dep | Version |
|-----|---------|
| `androidx.compose:compose-bom-alpha` | 2026.05.01 |
| `androidx.compose.material3:material3` | (BOM-managed) |
| `androidx.compose.material:material-icons-extended` | (BOM-managed) |
| `androidx.constraintlayout:constraintlayout-compose` | 1.1.0 (aniyomi catalog) |
| `io.github.fornewid:material-motion-compose-core` | 2.0.1 |

### Networking / data
| Dep | Version |
|-----|---------|
| `com.squareup.okhttp3:okhttp` | 5.3.2 (with brotli + DNS-over-HTTPS) |
| `org.jsoup:jsoup` | 1.22.2 |
| `app.cash.sqldelight` | 2.3.2 (async + coroutines + androidx driver) |
| `androidx.sqlite:sqlite-bundled` | 2.6.2 |
| `dev.icerock.moko:resources` | 0.26.4 (i18n) |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 |

### DI / Preferences
| Dep | Version | Notes |
|-----|---------|-------|
| `com.github.mihonapp:injekt` | 91edab2317 | Injekt for DI (Tachiyomi heritage). |
| `androidx.preference:preference-ktx` | 1.2.1 | SharedPreferences backing for Injekt `PreferenceStore`. |

> ANI-KUTA: ANI-KUTA uses Hilt + DataStore. Animiru uses **Injekt +
> SharedPreferences wrapped in a `PreferenceStore` interface**. This is a
> big architectural difference; see `09-player-settings.md` for how this
> shapes the player prefs.

### Image loading
- `io.coil-kt.coil3` 3.4.0 (core + gif + compose + network-okhttp)
- `com.github.tachiyomiorg:subsampling-scale-image-view` (commit hash)
- (Mihon image-decoder is **commented out** in Animiru: `// AM (REMOVE_LIBRARIES)`)

### Other notable
- Shizuku 13.1.5 (root-less extension installer)
- Voyager 1.1.0-beta03 (navigation; player does NOT use Voyager — PlayerActivity is a plain `BaseActivity` with `setContent`)
- RxJava 1.3.8 (legacy — sources still use `Observable`)
- Conscrypt 2.5.3 (TLS 1.3 on Android < 10)
- `me.zhanghai.android.libarchive:library` 1.1.6 (archive extraction for local source)

## 5. Player-specific native libraries

The `libmpv.so` and `libplayer.so` are bundled via `mpv-android-lib` AAR
from `io.github.secozzi`. Secozzi maintains a fork of `mpv-android` that
exposes the MPV JNI bindings as a consumable AAR rather than as an Activity
(like the original `mpv-android` project does).

This is the crucial architectural choice: **Animiru does not launch the
external `mpv-android` app**. Instead, it embeds MPV via JNI inside its own
`PlayerActivity`, using `SurfaceView` + `mpv.attachSurface(holder.surface)`.

The full surface hookup is only 51 lines:
```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/components/MpvSurface.kt:1-51
@Composable
fun MpvSurface(
    modifier: Modifier = Modifier,
    mpv: MPV,
    videoOutput: String,
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceChanged(...) {
                            mpv.setPropertyString("android-surface-size", "${width}x$height")
                        }
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            mpv.attachSurface(holder.surface)
                            mpv.setOptionString("force-window", "yes")
                            mpv.setPropertyString("vo", videoOutput)
                            mpv.setOptionString("vid", "auto")
                        }
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            mpv.setPropertyString("vid", "no")
                            mpv.setPropertyString("vo", "null")
                            mpv.setPropertyString("force-window", "no")
                            mpv.detachSurface()
                        }
                    },
                )
            }
        },
    )
}
```

This is one of the most directly-portable pieces of Animiru for ANI-KUTA —
the entire surface lifecycle is 51 lines. See `02-player-architecture.md`
for the broader wiring.

## 6. Manifest declaration of PlayerActivity

```xml
<!-- app/src/main/AndroidManifest.xml:140-148 -->
<activity
    android:name=".ui.player.PlayerActivity"
    android:autoRemoveFromRecents="true"
    android:configChanges="orientation|screenLayout|screenSize|smallestScreenSize|keyboardHidden|keyboard|uiMode"
    android:exported="false"
    android:launchMode="singleTask"
    android:resizeableActivity="true"
    android:supportsPictureInPicture="true"
    android:theme="@style/Theme.Tachiyomi">
```

Notable points:
- `configChanges` includes `uiMode` → theme changes don't recreate the
  Activity (relevant for ANI-KUTA — previous worklog entry `ANIMIRU-CLONE`
  noted that ANI-KUTA was missing `uiMode` and theme toggle was recreating).
- `supportsPictureInPicture="true"` + `resizeableActivity="true"` → enables
  PiP without further setup.
- `launchMode="singleTask"` → only one PlayerActivity instance; new intents
  come via `onNewIntent`. This is how "play next episode" works without
  stacking Activities.
- S-Pen remote actions are also wired (Samsung-specific):
  `intent-filter` for `com.samsung.android.support.REMOTE_ACTION` +
  `meta-data` pointing to `@xml/s_pen_actions`.
