# 02 — The CS3 Plugin Format & Plugin Project Structure

> **Research batch**: B1-b · **Agent**: 40-B1-b · **Status**: complete
> **Sources** (read-only clones, pinned at clone time 2026-08-29; all paths relative to `/home/z/ANI-KUTA-WORK/research/` unless stated):
> `TestPlugins/` (recloudstream/TestPlugins — official plugin template), `extensions/` (recloudstream/extensions — official extensions repo),
> `phisher-builds/` (phisher98/cloudstream-extensions-phisher @ `builds` branch — **80 compiled `.cs3` + 47 `.jar` files** for binary forensics),
> `cloudstream/library/.../plugins/` (the plugin API), `cloudstream/app/.../plugins/PluginManager.kt` (the loader),
> `csdocs/devs/` (official developer docs), `CakesTwix-ext/`, `storm-ext/` (community multi-provider repos).
> Confidence markers: `[verified]` = read in source / reproduced by command · `[docs]` = from csdocs · `[inferred]` = reasoned, needs verification.

---

## 1. What a CS3 plugin IS — binary forensics of `.cs3`

### 1.1 Executive answer

A **`.cs3` file is NOT an APK and NOT a plain JAR. It is a small custom ZIP package** containing:

1. `manifest.json` — a tiny JSON manifest naming the entry class (this replaces the Android `AndroidManifest.xml`),
2. `classes.dex` — a standard Dalvik dex file (version 035) with **only the plugin's own classes** (no bundled kotlin/okhttp/jsoup),
3. *(only when the plugin declares `requiresResources = true`)* `res/…` + a compiled `resources.arsc` — standard Android resource table.

No `AndroidManifest.xml`, no `META-INF/` (no signature files at all), no `assets/`. `[verified]` — census over all **80** `.cs3` files in `phisher-builds/`:

```
$ for f in *.cs3; do unzip -l "$f" | awk 'NR>3 && $4 != "" {print $4}' | grep -v '^$'; done | sed 's|/.*|/|' | sort | uniq -c | sort -rn
    189 res/            ← resource files/dirs (16 plugins only)
     80 manifest.json   ← every plugin
     80 classes.dex     ← every plugin
     16 resources.arsc  ← exactly the 16 plugins with requiresResources=true
$ for f in *.cs3; do unzip -l "$f" | grep -q 'META-INF' && echo "$f HAS META-INF"; done   # → no output
```

### 1.2 A minimal plugin, unzipped

`AllMovieLandProvider.cs3` (57,618 bytes, sha256 `938e5d6b…c65c9` — matches `fileHash` in `plugins.json` exactly, see §1.5):

```
$ unzip -l AllMovieLandProvider.cs3
Archive:  AllMovieLandProvider.cs3
  Length      Date    Time    Name
---------  ---------- -----   ----
      131  1980-02-01 00:00   manifest.json
   147816  1980-02-01 00:00   classes.dex
---------                     -------
   147947                     2 files

$ unzip -p AllMovieLandProvider.cs3 manifest.json
{"pluginClassName":"com.phisher98.AllMovieLandProviderPlugin","name":"AllMovieLandProvider","version":23,"requiresResources":false}
```

`AniKoto.cs3` (41,595 bytes) — same shape, 102-byte manifest:
`{"pluginClassName":"com.anikoto.AnikotoPlugin","name":"AniKoto","version":1,"requiresResources":false}` `[verified]`

### 1.3 A resource-carrying plugin

`AniDb.cs3` (`requiresResources: true` in its manifest) — note it is still **not** an APK (no `AndroidManifest.xml`), it just adds the resource half of an APK:

```
$ unzip -l AniDb.cs3
      96  manifest.json
  125688  classes.dex
       0  res/
       0  res/drawable/
    1076  res/drawable/outline.xml          ← compiled binary AXML (file(1) misreports it as "Targa")
     904  res/drawable/save_icon.xml
       0  res/layout/
    3560  res/layout/bottom_sheet_layout.xml
    1184  resources.arsc                    ← "Android package resource table (ARSC), 3 string(s), utf8"
```

`resources.arsc` header starts `02 00 0c 00 …` = standard arsc table. `[verified]`

### 1.4 The dex

- `classes.dex` is a real Dalvik dex, magic `dex\n035\0` (`od` of first 8 bytes: `64 65 78 0a 30 33 35 00`). `[verified]`
- Header parse of AllMovieLandProvider's dex: **58 class definitions, 1,087 strings, 680 methods, file_size 147,816**. The same plugin's `.jar` contains **53 `.class` files** — i.e. the dex holds only the plugin's own classes (the ~5 delta is d8 synthetics), **zero bundled kotlin-stdlib / okhttp / jsoup**: the only `kotlin/…` strings in the dex are *type references* (`Lkotlin/jvm/internal/Intrinsics;`, `Lkotlin/coroutines/Continuation;` …) resolved against the **host app** at runtime. `[verified]`
  - This is the single biggest structural difference from aniyomi/tachiyomi extension APKs, which routinely bundle a partial kotlin-stdlib (the exact thing that caused ANI-KUTA's child-first classloader disaster, see §8).
- The dex *does* retain the `@CloudstreamPlugin` annotation on the entry class: the type `Lcom/lagradost/cloudstream3/plugins/CloudstreamPlugin;` appears in the dex type table — Kotlin's default annotation retention is RUNTIME — but the app never reads it (§2).

### 1.5 `.cs3` vs `.jar` side by side

The same plugin is published twice when it is *cross-platform* (`isCrossPlatform = true` in gradle, see §3.3). In `phisher-builds/`: 80 `.cs3`, 47 `.jar`; the 47 with `.jar` are exactly the cross-platform builds, and `plugins.json` carries parallel `jarUrl`/`jarHash`/`jarFileSize` fields for them (all 5 official `extensions/` providers set `isCrossPlatform = true` — `extensions/*/build.gradle.kts:22`). `[verified]`

| | `AllMovieLandProvider.cs3` | `AllMovieLandProvider.jar` |
|---|---|---|
| Size | **57,618 B** | **297,020 B** (~5×) |
| Contents | `manifest.json` + `classes.dex` | 53 `.class` files + `META-INF/AllMovieLandProvider.kotlin_module` |
| Entry metadata | JSON manifest names the class | **no `MANIFEST.MF` at all** (checked all 47 jars — none has one), no manifest.json, no dex |
| Code format | Dalvik dex 035 (Android/ART) | JVM `.class` bytecode (desktop/other-platform ports) |
| Zip timestamps | `1980-02-01 00:00:00` (normalized → reproducible) | `1981-01-01 01:01` (normalized) |
| Resources | only if `requiresResources` | never (AniDb etc. have **no** `.jar` published) |
| sha256 | matches `fileHash` in plugins.json | matches `jarHash` in plugins.json |

```
$ sha256sum AllMovieLandProvider.cs3 AllMovieLandProvider.jar
938e5d6b1a41f3f625384a9cd559811b63528da0c3cc26c8adca2efbf0cc65c9  AllMovieLandProvider.cs3
c75e73bd21d30fa6a2edff462c91c3884e475c16b866261a52c2cc1b98a7f609  AllMovieLandProvider.jar
   ↑ equals "fileHash" / "jarHash" in plugins.json, byte-for-byte  [verified]
```

More size pairs (cs3 → jar): AllWish 42,513 → 167,670 · Animeav1 18,762 → 82,240 · Animexin 14,993 → 60,540 · BanglaPlex 21,092 → 89,867. The `.jar` exists for non-Android CloudStream clients; the **Android app never downloads or parses it** — the app-side `SitePlugin` model has no `jarUrl` field at all (`cloudstream/app/.../plugins/RepositoryManager.kt:50-76`, fields: `url, status, version, apiVersion, name, internalName, authors, description, repositoryUrl, tvTypes, language, iconUrl, fileSize, fileHash`). `[verified]`

### 1.6 `manifest.json` — the complete schema

Union of keys across all 80 manifests = exactly 4 keys, matching the `BasePlugin.Manifest` class one-for-one (`cloudstream/library/src/commonMain/kotlin/com/lagradost/cloudstream3/plugins/BasePlugin.kt:64-77`):

```kotlin
@Serializable
class Manifest {
    @JsonProperty("name") @SerialName("name")
    var name: String? = null

    @JsonProperty("pluginClassName") @SerialName("pluginClassName")
    var pluginClassName: String? = null

    @JsonProperty("requiresResources") @SerialName("requiresResources")
    var requiresResources: Boolean = false

    @JsonProperty("version") @SerialName("version")
    var version: Int? = null
}
```

- `pluginClassName` — fully-qualified class (e.g. `com.phisher98.AllMovieLandProviderPlugin`) of the `@CloudstreamPlugin`-annotated entry class. Always present (80/80). The app `loadClass`es **this** name directly — there is no annotation scan at load time (§5.3).
- `name` — the plugin's internalName (== module dir name == file stem, see §7). 80/80.
- `version` — plain `Int` (e.g. 23, 661 is the max seen — StreamPlay). 80/80.
- `requiresResources` — bool; when `true` the app wires up an `AssetManager.addAssetPath` `Resources` object for the plugin (§5.4). 16/80 true.

All richer metadata (authors, description, tvTypes, language, iconUrl, status, repositoryUrl, fileHash…) lives **outside** the file, in the repository's `plugins.json` (doc 04 covers that format).

---

## 2. The `@CloudstreamPlugin` annotation

### 2.1 The real declaration — it has NO fields

The entire annotation, quoted in full (`cloudstream/library/src/commonMain/kotlin/com/lagradost/cloudstream3/plugins/CloudstreamPlugin.kt:1-5`):

```kotlin
package com.lagradost.cloudstream3.plugins

@Suppress("unused")
@Target(AnnotationTarget.CLASS)
annotation class CloudstreamPlugin
```

**There is no `name`, `version`, `authors`, `description`, `apiVersion`, `tvTypes`, or `repositoryUrl` on the annotation.** Those are all gradle-extension settings (§3.3) that the gradle plugin serializes into `manifest.json` + `plugins.json`. Any doc claiming the annotation carries metadata is wrong. `[verified]`

### 2.2 What the annotation is actually for

- It is a **build-time marker**: the CloudStream gradle plugin (`com.github.recloudstream:gradle:-SNAPSHOT`, plugin id `com.lagradost.cloudstream3.gradle`) uses it to find the plugin's entry class in the compiled output and write `pluginClassName` into `manifest.json`. `[inferred]` — the gradle plugin's source is NOT in the local workspace, but: (a) the annotation is class-target-only with no runtime consumer, (b) `grep CloudstreamPlugin` over the whole `cloudstream/` app+library matches **only the declaration** — the app never references it, (c) every `manifest.json` `pluginClassName` in phisher-builds matches the annotated class of the corresponding plugin source (e.g. `DailymotionPlugin.kt:6-7` in `extensions/` would produce `recloudstream.DailymotionPlugin`).
- Runtime retention (Kotlin default) means the annotation *survives into the dex* (`Lcom/lagradost/cloudstream3/plugins/CloudstreamPlugin;` is present in the compiled `classes.dex` type table) — so a loader *could* use it, but CloudStream's does not. `[verified]` (presence in dex) / `[inferred]` (purpose)
- Usage convention — every plugin repo follows this exactly (`TestPlugins/ExampleProvider/src/main/kotlin/com/example/ExamplePlugin.kt:8-9`):

```kotlin
@CloudstreamPlugin
class ExamplePlugin: Plugin() { … }
```

- Multiple annotated classes per project are theoretically possible but **one entry class per plugin module** is the universal convention (1 plugin class + 1..N `MainAPI` provider classes per module; the app instantiates only the one named in `manifest.json`). `[inferred]` from 80/80 observed builds.

---

## 3. Plugin project structure

### 3.1 The official template (`TestPlugins/`) — file layout

```
TestPlugins/                          ← fork this repo (csdocs/devs/using-plugin-template.md)
├── .github/workflows/build.yml       ← CI: builds & force-pushes to a `builds` branch (§4.2)
├── build.gradle.kts                  ← ROOT build file: applies ALL plugins to ALL subprojects
├── settings.gradle.kts               ← auto-includes every subdir that has a build.gradle.kts
├── gradle.properties                 ← org.gradle.jvmargs, android.useAndroidX=true, enableJetifier=true
├── gradlew / gradlew.bat / gradle/wrapper/  ← Gradle 8.12 (gradle-wrapper.properties distributionUrl)
├── README.md                         ← dev workflow + adb "All Files Access" instructions
└── ExampleProvider/                  ← ONE GRADLE MODULE PER PLUGIN (module name == internalName)
    ├── build.gradle.kts              ← plugin metadata (cloudstream {} block) + version
    └── src/main/
        ├── AndroidManifest.xml       ← literally `<?xml version="1.0" encoding="utf-8"?> <manifest />`
        │                                (empty placeholder — AGP library requires the file;
        │                                nothing from it ends up in the .cs3)
        ├── kotlin/com/example/       ← Kotlin sources (src/main/kotlin, NOT src/main/java)
        │   ├── ExamplePlugin.kt      ← @CloudstreamPlugin entry class (extends Plugin)
        │   ├── ExampleProvider.kt    ← the MainAPI provider
        │   └── BlankFragment.kt      ← optional settings UI fragment (§6.3)
        └── res/                      ← optional resources (only used when requiresResources=true)
            ├── layout/fragment_blank.xml
            ├── drawable/ic_android_24dp.xml
            └── values/strings.xml, values-pl/strings.xml   ← plugin can ship its own translations
```

`[verified]` — every file read; `ExampleProvider/src/main/AndroidManifest.xml` is 2 lines (xml decl + `<manifest />`).

### 3.2 The root `build.gradle.kts` (quoted in full — this is THE canonical plugin build config)

`TestPlugins/build.gradle.kts:1-91` (comments preserved; the `clean` task at the end is trivial):

```kotlin
import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        // Shitpack repo which contains our tools and dependencies
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        // Cloudstream gradle plugin which makes everything work and builds plugins
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        // when running through github workflow, GITHUB_REPOSITORY should contain current repository name
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "user/repo")
    }

    android {
        namespace = "com.example"

        defaultConfig {
            minSdk = 21
            compileSdkVersion(35)
            targetSdk = 35
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8) // Required
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions"
                )
            }
        }
    }

    dependencies {
        val cloudstream by configurations
        val implementation by configurations

        // Stubs for all cloudstream classes
        cloudstream("com.lagradost:cloudstream3:pre-release")

        // These dependencies can include any of those which are added by the app,
        // but you don't need to include any of them if you don't need them.
        // https://github.com/recloudstream/cloudstream/blob/master/app/build.gradle.kts
        implementation(kotlin("stdlib")) // Adds Standard Kotlin Features
        implementation("com.github.Blatzar:NiceHttp:0.4.11") // HTTP Lib
        implementation("org.jsoup:jsoup:1.18.3") // HTML Parser
        // IMPORTANT: Do not bump Jackson above 2.13.1, as newer versions will
        // break compatibility on older Android devices.
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1") // JSON Parser
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
```

Key facts encoded above:

- **Gradle plugin id**: `com.lagradost.cloudstream3.gradle`, resolved via **JitPack** from maven coordinate **`com.github.recloudstream:gradle:-SNAPSHOT`** (the "-SNAPSHOT" here is a JitPack-style floating tag). Applied to **every subproject** (`subprojects { apply(plugin = …) }`). `[verified]`
- Plugins are built as **`com.android.library`** modules (not applications!) + `kotlin-android`. minSdk 21, compile/target 35, JVM target 1.8 "Required". `[verified]`
- **How the plugin API dependency is declared — two patterns exist**:
  1. **Template ("stubs") pattern**: a dedicated `cloudstream` configuration (registered by the gradle plugin) — `cloudstream("com.lagradost:cloudstream3:pre-release")` — described as *"Stubs for all cloudstream classes"* (`TestPlugins/build.gradle.kts:74-75`), i.e. compile-only; nothing is packaged. `[verified]`
  2. **Real-library pattern** (official extensions repo): `implementation("com.github.recloudstream.cloudstream:library:-SNAPSHOT")` — the actual KMP library from JitPack (`extensions/build.gradle.kts:73` (line no. corrected by B5-a)). CakesTwix catalogs both (`gradle/libs.versions.toml`: `cloudstream3 = { module = "com.lagradost:cloudstream3", version.ref = "pre-release" }` + `cloudstreamapi = { module = "com.github.Blatzar:CloudstreamApi", version.ref = "0.1.6" }`) with the comment *"If the task is specifically to compile the app then use the stubs, otherwise use the library"* (`CakesTwix-ext/build.gradle.kts:69-72`). `[verified]`
- Either way the compiled `.cs3` contains **only the plugin's own classes** (§1.4) — the library/stubs are provided by the host app at runtime through the classloader parent. `[verified]`
- Extra runtime deps (NiceHttp, jsoup, Jackson ≤ 2.13.1) are also expected to be **already in the app** (comment points at the app's build.gradle.kts) — do **not** bump Jackson past 2.13.1 or older devices break. `[verified]`
- The template README notes the whole plugin system is "heavily based on **Aliucord**" (`TestPlugins/README.md:57`). `[verified]`

### 3.3 The per-plugin module `build.gradle.kts` — the `cloudstream {}` extension

`TestPlugins/ExampleProvider/build.gradle.kts` (entire file):

```kotlin
dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

// Use an integer for version numbers
version = 1

cloudstream {
    // All of these properties are optional, you can safely remove any of them.

    description = "Lorem ipsum"
    authors = listOf("Cloudburst", "Luna712")

    /**
    * Status int as one of the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta-only
    **/
    status = 1 // Will be 3 if unspecified

    tvTypes = listOf("Movie")

    requiresResources = true

    language = "en"

    // Random CC logo I found
    iconUrl = "https://upload.wikimedia.org/wikipedia/commons/2/2f/Korduene_Logo.png"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
```

A real provider — `extensions/DailymotionProvider/build.gradle.kts` (the only additional knob seen in the wild, `isCrossPlatform`):

```kotlin
// Use an integer for version numbers
version = 4

cloudstream {
    description = "Watch content from Dailymotion"
    authors = listOf("Luna712")
    status = 1 // Will be 3 if unspecified
    tvTypes = listOf("Others")
    iconUrl = "https://www.google.com/s2/favicons?domain=www.dailymotion.com&sz=%size%"

    isCrossPlatform = true
}
```

**Observed `CloudstreamExtension` settings** (definition lives in the gradle-plugin repo, not in our workspace — this list is from usage across 4 repos): `setRepo(repo)` / `setRepo(user, repo, host)` (CakesTwix root build uses the 3-arg form), `description`, `authors`, `status` (0 Down / 1 Ok / 2 Slow / 3 Beta-only; default 3), `tvTypes` (list of `TvType`-name strings), `requiresResources`, `language` (BCP-47-ish tag: "en", "uk", "mx"…), `iconUrl` (supports `%size%` placeholder for favicon services), `isCrossPlatform` (emits the extra `.jar`). Gradle's built-in per-project `version` (Int) becomes the plugin `version`. `[verified]` (each field seen in at least one build file)

### 3.4 `settings.gradle.kts` — auto-include

Every CS3 plugin repo uses the same auto-include trick (`TestPlugins/settings.gradle.kts:1-19`, identical in `extensions/`, `storm-ext/`, CakesTwix):

```kotlin
rootProject.name = "CloudstreamPlugins"

val disabled = listOf<String>()

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
```

→ **A new plugin = a new top-level directory with a `build.gradle.kts`.** Nothing else to register. `[verified]`

### 3.5 Module source layout & package conventions

- Sources at `src/main/kotlin/…` (never `src/main/java`), one package per repo, e.g.:
  - template: `com.example` (namespace `com.example`, root `build.gradle.kts:45`)
  - official extensions: package `recloudstream`, namespace `recloudstream` (`extensions/build.gradle.kts:46`; sources `DailymotionProvider/src/main/kotlin/recloudstream/DailymotionPlugin.kt:1`)
  - storm-ext: `com.stormunblessed`; CakesTwix: `com.lagradost`(!) with namespace `ua.CakesTwix`; phisher: `com.phisher98`, `com.anikoto`, `com.MovieBox`… (from the compiled `pluginClassName`s)
  - Package names are completely free — the app locates the entry class via `manifest.json`, never via package conventions. `[verified]`
- **One module = one plugin** with typically: `<Name>Plugin.kt` (entry) + `<Name>Provider.kt` (MainAPI) (+ `models/`, `extractors/` subpackages in bigger plugins, e.g. `storm-ext/AnimeJlProvider/.../extractors/StreamWishExtractor.kt`). `[verified]`
- Tests exist in some repos (`CakesTwix-ext/UakinoProvider/src/test/kotlin/...UakinoParsingTest.kt`) — unit-testable because the library dependency is real. `[verified]`

### 3.6 Comparison: community multi-provider repos

Both `CakesTwix-ext/` and `storm-ext/` are the same architecture as the template with cosmetic drift:

| | TestPlugins (template) | extensions (official) | CakesTwix-ext | storm-ext |
|---|---|---|---|---|
| Gradle plugin | `com.github.recloudstream:gradle:-SNAPSHOT` (buildscript classpath) | same | same, via version catalog `libs.recloudstream.gradle` | same (buildscript) |
| AGP | 8.7.3 | 8.7.3 | 8.13.2 (catalog) | — |
| Kotlin | 2.1.0 | 2.3.0 | 2.3.0 (catalog) | — |
| API dep | `cloudstream("com.lagradost:cloudstream3:pre-release")` (stubs) | `implementation("com.github.recloudstream.cloudstream:library:-SNAPSHOT")` | catalog: both stubs + CloudstreamApi 0.1.6 | — |
| `cloudstream{}` defaults | — | `setRepo(env)` | `setRepo(env)`, `authors = listOf("CakesTwix")` | — |
| CI | `.github/workflows/build.yml` (§4.2) | same + jar handling | — | same as template, setup-java@v5 temurin |
| settings.gradle | auto-include | identical | identical + `pluginManagement`/`dependencyResolutionManagement` blocks (`FAIL_ON_PROJECT_REPOS`) | identical |

`[verified]` from the respective root/module build files; CakesTwix `libs.versions.toml` quoted in §3.2.

---

## 4. How plugins get built

### 4.1 The gradle tasks

From the CI workflows and the template README:

```
./gradlew make                 # per-module: compiles the module and produces <module>/build/<Name>.cs3
./gradlew makePluginsJson      # aggregates all modules' metadata into root build/plugins.json
./gradlew ensureJarCompatibility   # (official extensions only) validates the cross-platform .jar builds
./gradlew <Module>:make        # build one plugin (README syntax: `./gradlew ExampleProvider:make`)
./gradlew <Module>:deployWithAdb  # build + hot-deploy to a connected device running the app (README:17-18)
```

`[verified]` for task names & artifact paths (workflow `cp **/build/*.cs3` + `cp build/plugins.json`, and `TestPlugins/README.md:16-18`); the task *implementations* live in the gradle-plugin repo (not local). `deployWithAdb` pairs with `___DO_NOT_CALL_FROM_A_PLUGIN_hotReloadAllLocalPlugins` in the app (`PluginManager.kt:485-494`) — local plugins are re-scanned from disk, enabling the hot-reload loop. `[verified]` (app side) / `[docs]` (dev workflow).

`makePluginsJson` output shape (from the real `phisher-builds/plugins.json`, 80 entries; keys are the union seen — optional keys omitted when absent): `url, status, version, apiVersion, name, internalName, authors, description, repositoryUrl, tvTypes, language, iconUrl, fileSize, fileHash, jarUrl, jarHash, jarFileSize`. `fileHash`/`jarHash` are `"sha256-<hex>"` — matching the app's `RepositoryManager.sha256()` format (`RepositoryManager.kt:107-122`). `repositoryUrl` comes from `setRepo(...)` in gradle. Entry example (trimmed):

```json
{
  "url": "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/AllMovieLandProvider.cs3",
  "status": 1, "version": 23, "name": "AllMovieLandProvider", "internalName": "AllMovieLandProvider",
  "authors": ["Phisher98"], "description": "Indian MultiLanguage Provider (Mostly Hindi)",
  "fileSize": 57618,
  "repositoryUrl": "https://github.com/phisher98/cloudstream-extensions-phisher",
  "language": "hi", "tvTypes": ["Movie", "TvSeries", "Cartoon"],
  "iconUrl": "https://…/AllMovieLandProvider/icon.png",
  "apiVersion": 1,
  "fileHash": "sha256-938e5d6b1a41f3f625384a9cd559811b63528da0c3cc26c8adca2efbf0cc65c9",
  "jarFileSize": 297020,
  "jarUrl": "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/AllMovieLandProvider.jar",
  "jarHash": "sha256-c75e73bd21d30fa6a2edff462c91c3884e475c16b866261a52c2cc1b98a7f609"
}
```

`apiVersion` here is the **plugin-list schema version** — the app model comments it as *"Unused currently, used to make the api backwards compatible? Set to 1"* (`RepositoryManager.kt:57-59`). It is NOT a per-plugin API level. Every entry in the wild says `1`. `[verified]`

### 4.2 The CI pattern — quote from the official template workflow

`.github/workflows/build.yml` (TestPlugins) — the canonical "builds branch" pattern; official `extensions/` workflow is identical **plus** `ensureJarCompatibility` + `cp **/build/*.jar` (and `workflow_dispatch:`):

```yaml
- name: Checkout
  uses: actions/checkout@master
  with: { path: "src" }

- name: Checkout builds                       # the repo has a second long-lived branch "builds"
  uses: actions/checkout@master
  with: { ref: "builds", path: "builds" }

- name: Clean old builds
  run: rm $GITHUB_WORKSPACE/builds/*.cs3

- name: Setup JDK 17
  uses: actions/setup-java@v1
  with: { java-version: 17 }

- name: Setup Android SDK
  uses: android-actions/setup-android@v2

- name: Build Plugins
  run: |
    cd $GITHUB_WORKSPACE/src
    chmod +x gradlew
    ./gradlew make makePluginsJson
    cp **/build/*.cs3 $GITHUB_WORKSPACE/builds
    cp build/plugins.json $GITHUB_WORKSPACE/builds

- name: Push builds
  run: |
    cd $GITHUB_WORKSPACE/builds
    git config --local user.email "actions@github.com"
    git config --local user.name "GitHub Actions"
    git add .
    git commit --amend -m "Build $GITHUB_SHA" || exit 0   # do not error if nothing to commit
    git push --force
```

So: **artifacts land in each module's `build/` dir; CI force-pushes them (single amended commit) to a dedicated `builds` branch**, which the repo's `repo.json` points at (`pluginLists: ["https://raw.githubusercontent.com/<org>/<repo>/builds/plugins.json"]`). `repo.json` itself (the repo index users add in-app) is written **manually** — 4-5 lines of JSON (`extensions/repo.json`: `name, description, manifestVersion: 1, pluginLists[...]`; phisher's adds `iconUrl`). The full add-browse-update flow is doc 04's subject. `[verified]`

Developer workflow per csdocs (`csdocs/devs/using-plugin-template.md:7-18`): fork TestPlugins → enable Actions ("Allow all actions") → grant workflows "Read and write permissions" → push code, plugins build automatically. Local testing needs "All Files Access" on the app (Android 11+) because side-load plugins live in external storage (`TestPlugins/README.md:21-47`). `[docs]`

---

## 5. How the app loads a plugin (PluginManager.kt)

All from `cloudstream/app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt` unless noted.

### 5.1 File placement (two sources of plugins)

- **Online (repo-installed)**: `context.filesDir/Extensions/<sanitized-repo-url-hash>/<sanitized-internalName>.cs3`
  - `ONLINE_PLUGINS_FOLDER = "Extensions"` (`RepositoryManager.kt:99`); path built by `getPluginPath(context, internalName, repositoryUrl)` (`PluginManager.kt:747-755`) → `getPluginSanitizedFileName(name) = sanitizeFilename(name, true) + "." + name.hashCode()` (`PluginManager.kt:737-742`). The repo-hash folder is what allows two repos to ship same-named plugins ("salted with the repository url hash", `PluginManager.kt:779`). This path is also the **installed check** ("This should not be changed as it is used to also detect if a plugin is installed!", `PluginManager.kt:745-746`). `[verified]`
- **Local (side-loaded)**: user drops `.cs3`/`.zip` files into `<ExternalStorage>/Cloudstream3/plugins/` (`CLOUD_STREAM_FOLDER` + `LOCAL_PLUGINS_PATH`, `PluginManager.kt:186-189`). At startup the app **copies each into `context.getExternalFilesDir(null)/plugins/`** before loading, because *"on Android 14+ it otherwise gives SecurityException due to dex files and setReadOnly seems to have no effect unless it is here"* (`PluginManager.kt:527-556`); the key is wiped + rebuilt every start (`removeKey(PLUGINS_KEY_LOCAL)`, line 536). `[verified]`
- `maybeLoadPlugin` accepts only `.zip` and `.cs3` extensions, everything else is skipped with a log (`PluginManager.kt:210-221`). Note: the app will happily load a file *named* `.zip` with the same internal layout — the extension check is cosmetic. `[verified]`

### 5.2 Startup sequencing

`MainActivity.kt:1350-1400`: the home provider is loaded first (`loadSinglePlugin`, `PluginManager.kt:244-259` — matches `internalName` against the apiName with `"provider"` stripped, ignore-case), then in parallel IO coroutines: `updateAllOnlinePluginsAndLoadThem` (or plain `loadAllOnlinePlugins` if auto-update setting off) → optional `downloadNotExistingPluginsAndLoad` (auto-download modes) → `loadAllLocalPlugins`. If the previous run crashed (`lastError != null`) **or** a file named `safe` exists in the Cloudstream3 folder → **safe mode**: nothing loads and a dialog shows (`isSafeMode`/`checkSafeModeFile`, `PluginManager.kt:570-588`). `[verified]`

### 5.3 The load sequence — classloader & entry-point discovery (THE important part for ANI-KUTA)

`loadPlugin(context, file, data)` (`PluginManager.kt:593-687`), in order:

1. `file.setReadOnly()` — required for loading dex from writable storage on newer Android (lines 601-609); related maintenance: `deleteAllOatFiles` deletes `<plugins>/oat` dirs to fix SIGSEGV after app updates (lines 164-175).
2. **`val loader = PathClassLoader(filePath, context.classLoader)`** (line 611) — plain `dalvik.system.PathClassLoader` over the `.cs3` zip, **parent = the app's classloader**. Standard delegation on Android is **parent-first** (the parent/app classloader is consulted before the plugin's own dex — `PathClassLoader`/`BaseDexClassLoader` do not reverse delegation). Exactly the loader policy real Aniyomi uses and the one ANI-KUTA switched to in D-294. It also means any classes the dex doesn't define (kotlin-stdlib, the entire `com.lagradost.cloudstream3.*` API, NiceHttp, jsoup, Jackson) resolve against the **host app** — which works because CS3 plugins ship no such classes (§1.4). `[verified]` (constructor + parent) — parent-first is the ART default, not overridden anywhere in the app.
3. **Manifest read as a classpath resource**: `loader.getResourceAsStream("manifest.json")` → parsed with `parseJson<BasePlugin.Manifest>` (lines 612-621). Missing manifest → `"Failed to load plugin …: No manifest found"`, return false.
4. **Entry class from the manifest — NO annotation scan, NO dex walking**: `loader.loadClass(manifest.pluginClassName)` then `getDeclaredConstructor().newInstance()` cast to `BasePlugin` (lines 630-634). This is why `manifest.json` exists and why the `@CloudstreamPlugin` annotation is irrelevant at runtime (§2).
5. `pluginInstance.filename = file.absolutePath` (line 644) — used later as the ownership key for unload and as `sourcePlugin` on every registered provider/extractor.
6. **Resources (only if `manifest.requiresResources`)** (lines 645-659): reflectively construct a hidden `android.content.res.AssetManager`, invoke its hidden `addAssetPath(file.absolutePath)` (the classic dynamic-APK-resources trick, SO link in the comment), wrap it in `new Resources(assets, context.resources.displayMetrics, context.resources.configuration)` and assign to `(pluginInstance as? Plugin)?.resources`.
7. Registration bookkeeping: `plugins[filePath]`, `classLoaders[loader]`, `urlPlugins[url ?: filePath]` maps (lines 660-668) — note the **loader instances are kept forever** (no unloading of dex is possible on ART; "unload" only drops registrations).
8. **Lifecycle dispatch** (lines 669-673): `if (pluginInstance is Plugin) pluginInstance.load(context) else pluginInstance.load()` — the Android `Plugin` base class gets the `Context`, cross-platform `BasePlugin` plugins don't (§6.1).
9. **Error path** (lines 677-686): any Throwable → `Log.e("Failed to load $file: …")` + **Toast** `R.string.plugin_load_fail` (formatted with the file name) + return `false`. Nothing else — no retry, no per-plugin error state, no UI list of failed plugins (plugins just silently don't appear). Contrast with ANI-KUTA's D-295/D-296 error surfacing.
10. Duplicate check: if `plugins.containsKey(filePath)` → log "already exists" and return true (lines 639-642).

`PluginData` (the persisted record, `PluginManager.kt:79-85`): `internalName, url, isOnline, filePath, version` — stored in DataStore under `PLUGINS_KEY` (online) / `PLUGINS_KEY_LOCAL` (local). Local plugins get `version = PLUGIN_VERSION_NOT_SET` and **no hash** ("Local plugins have no use for the hash, and it's expensive to compute", lines 102-103). `[verified]`

### 5.4 Trust / security model

- **No signature verification of any kind.** No APK signature blocks exist in `.cs3` (§1.1), the loader is not wrapped in any `SecureClassLoader`, and nothing checks certificates. `[verified]`
- **Integrity = one SHA-256 comparison at download time**, from the repo's `plugins.json`: `downloadPluginToFile` streams to a temp file in cacheDir, computes `sha256()` and throws `IllegalStateException("Extension hash mismatch…")` if it differs from the expected `fileHash`, else atomically moves into place (`RepositoryManager.kt:193-240`, check at 214-220). Hash format `"sha256-<64 hex>"` (`RepositoryManager.kt:107-122`).
- After download, updates are decided purely by integer `version` comparison (`OnlinePluginData.isOutdated = onlineData.version > savedData.version || == PLUGIN_VERSION_ALWAYS_UPDATE(-1)`, `PluginManager.kt:229-230`), and remote `status == 0` (Down) remotely **uninstalls/disables** the plugin (`isDisabled`, lines 231, 306-308).
- Side-loaded local plugins bypass ALL of this — no hash, no repo — by design (that's the hot-reload/dev path). The user's only protection is the safe-mode file. **The effective security posture: a plugin repo you add is fully trusted code execution; plugins run inside the app process with full app permissions.** `[inferred]` from the above verified mechanics.

### 5.5 Unload

`unloadPlugin(absolutePath)` (`PluginManager.kt:689-731`): calls `beforeUnload()`, then removes every `MainAPI`/`ExtractorApi`/`VideoClickAction` whose `sourcePlugin == plugin.filename`, removes loader/plugin/url registrations. Classloader itself stays alive (unavoidable on ART) — so hot-reload of an unchanged class set relies on new instances shadowing old ones. `[verified]`

---

## 6. Plugin capabilities surface (what a plugin instance gets)

### 6.1 The two base classes

**`BasePlugin`** — cross-platform, in the shared library (`cloudstream/library/src/commonMain/kotlin/com/lagradost/cloudstream3/plugins/BasePlugin.kt:14-78`). A plugin gets:

```kotlin
abstract class BasePlugin {
    fun registerMainAPI(element: MainAPI)            // adds to APIHolder.allProviders + addPluginMapping; sets element.sourcePlugin = filename
    fun registerExtractorAPI(element: ExtractorApi)  // adds to global extractorApis; sets sourcePlugin
    open fun beforeUnload()                          // called on unload
    open fun load()                                  // called on load (cross-platform entry)
    var filename: String?                            // absolute path of the .cs3 (deprecated alias __filename)
    class Manifest { name, pluginClassName, requiresResources, version }  // the manifest.json model (§1.6)
}
```

- `registerMainAPI` (lines 20-25) — **call it once per provider; there is no enforced limit** — a plugin can register several `MainAPI` providers (the official pattern registers exactly one; "All providers should be added in this manner. Please don't edit the providers list directly." — `extensions/DailymotionProvider/.../DailymotionPlugin.kt:9-10`). Providers land in the global `APIHolder.allProviders` atomic list and `apis` mapping (`MainAPI.kt:115, 134-139`). `[verified]`
- `registerExtractorAPI` (lines 31-35) — same for custom video-host extractors (`ExtractorApi` instances; see doc 08).
- **No `Context`, no preferences, no resources** at this level — deliberately platform-neutral (the library is KMP: `commonMain`, with `jvmMain`/`webMain`/`androidMain` source sets).

**`Plugin`** — Android-side, in the app (`cloudstream/app/src/main/java/com/lagradost/cloudstream3/plugins/Plugin.kt:10-40`). Adds:

```kotlin
abstract class Plugin : BasePlugin() {
    open fun load(context: Context) { load() }                 // context-aware entry; falls back to BasePlugin.load()
    fun registerVideoClickAction(element: VideoClickAction)    // adds long-press video actions
    var resources: Resources? = null                           // non-null only when requiresResources was set (§5.3 step 6)
    var openSettings: ((context: Context) -> Unit)? = null     // settings-button hook (§6.3)
}
```

Also available to plugin code by import (not via the base class): the library's global surface — `app` (NiceHttp request helper), `com.lagradost.cloudstream3.*` data-model builders (`newMovieLoadResponse`, …), `utils.loadExtractor`, `MainAPI.Companion.settingsForProvider` (`MainAPI.kt:497` — per-provider settings JSON), etc. Full inventory = doc 03's job. Providers themselves carry `sourcePlugin: String?` (`MainAPI.kt:561`) back-linking to the owning plugin file. `[verified]`

### 6.2 Which base class to extend

- Template (`ExamplePlugin.kt:9`): `class ExamplePlugin: Plugin()` — Android-only, gets `load(context)` + UI powers.
- Official extensions (`DailymotionPlugin.kt:7`): `class DailymotionPlugin: BasePlugin()` — cross-platform (`isCrossPlatform = true` builds the `.jar`); the loader branch `pluginInstance.load()` handles them (`PluginManager.kt:669-673`). `[verified]`

### 6.3 Contributing UI — `BlankFragment.kt` and `openSettings`

`openSettings` is the *only* UI extension point a plugin has. The app shows a settings action wherever a *downloaded* plugin instance is found with `openSettings != null` — in the extensions settings screen (`ui/settings/extensions/PluginDetailsFragment.kt:100-104` and `PluginAdapter.kt:125-129`, both wrapped in try/catch that logs "Failed to open … settings") and even on the home provider pin dialog (`ui/home/HomeFragment.kt:450-458`, hidden on TV layouts). The lambda receives a `Context` and typically does:

```kotlin
openSettings = {
    val frag = BlankFragment(this)
    activity?.let { frag.show(it.supportFragmentManager, "Frag") }
}
```
(`ExamplePlugin.kt:15-20` — line range corrected by B5-a — the plugin stashed the `AppCompatActivity` from `load(context)`.)

`BlankFragment.kt` (`TestPlugins/ExampleProvider/src/main/kotlin/com/example/BlankFragment.kt:24-93`) is a **`BottomSheetDialogFragment` that demonstrates the whole resource dance**: because plugin resources live in the plugin zip, not the app, it resolves everything by name through `plugin.resources`:

```kotlin
val layoutId = plugin.resources?.getIdentifier("fragment_blank", "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
return layoutId?.let { inflater.inflate(plugin.resources?.getLayout(it), container, false) }
```

with sibling helpers `getDrawable(name)`, `getString(name)`, `View.findViewByName(name)` (lines 27-47) — all using `Resources.getIdentifier` (hence the `@SuppressLint("DiscouragedApi")`). It can still reference **app** resources (`R.style.ResultInfoText`, `R.string.legal_notice_text`, `colorFromAttribute`) side by side (lines 78-92) because the app classes are on the parent classloader. Requires `requiresResources = true` + `viewBinding`/`buildConfig` buildFeatures (`ExampleProvider/build.gradle.kts:33-37`). Fragment-based UI ⇒ the app must host plugin `Fragment`s in its own `FragmentManager` — an architectural consequence for ANI-KUTA (§8). `[verified]`

---

## 7. Format quick-reference table

For future implementers — everything needed to produce/consume a `.cs3`:

| Aspect | Rule | Evidence |
|---|---|---|
| Container | ZIP (deflate), timestamps normalized `1980-02-01 00:00:00` | §1.2 |
| Required entries | `manifest.json` + `classes.dex` | 80/80 files |
| Optional entries | `res/**`, `resources.arsc` — only when `requiresResources=true` | 16/80 |
| Forbidden/absent | no `AndroidManifest.xml`, no `META-INF/**`, no signatures, no `assets/` | §1.1 census |
| dex | Dalvik 035, plugin classes ONLY (deps compileOnly against host) | §1.4 |
| `manifest.json.pluginClassName` | FQN of the `@CloudstreamPlugin`-annotated class extending `Plugin`/`BasePlugin`, public no-arg ctor | §5.3.4 |
| `manifest.json.name` | internalName == gradle module dir name == file stem == `plugins.json.internalName` | §1.2, §3.4 |
| `manifest.json.version` | integer, from gradle `version = N`; bump ⇒ app auto-updates (or `-1` = always update) | §3.3, §5.4 |
| `manifest.json.requiresResources` | bool; true ⇒ app builds `Resources` via `AssetManager.addAssetPath` and you must ship `res/` + `resources.arsc` | §5.3.6 |
| internalName convention | module dir, typically `<Site>Provider` (the app strips "provider" case-insensitively when matching api names) | `PluginManager.kt:247,250` |
| File name on install | `sanitizeFilename(internalName) + "." + internalName.hashCode() + ".cs3"` under `filesDir/Extensions/<repoUrlHash>/` | §5.1 |
| Gradle essentials | plugins `com.android.library` + `kotlin-android` + `com.lagradost.cloudstream3.gradle` (JitPack `com.github.recloudstream:gradle:-SNAPSHOT`); minSdk 21; jvmTarget 1.8; API via `cloudstream("com.lagradost:cloudstream3:pre-release")` stubs **or** `implementation("com.github.recloudstream.cloudstream:library:-SNAPSHOT")` | §3.2 |
| Build tasks | `make` → `<module>/build/<Name>.cs3` · `makePluginsJson` → root `build/plugins.json` · `ensureJarCompatibility` (cross-platform) · `deployWithAdb` (hot reload) | §4.1 |
| `.jar` twin | emitted only when `isCrossPlatform = true`; plain JVM classes + `META-INF/<Name>.kotlin_module`, **no MANIFEST.MF**; Android app ignores it | §1.5 |
| Hash | `plugins.json.fileHash` = `"sha256-" + hex(sha256(file))`, checked once at download | §5.4 |
| `apiVersion` | plugin-list schema field, always `1`, unused by the app — NOT a plugin API level | §4.1 |
| Trust | none beyond download-time sha256; local side-loads unchecked; safe-mode file (`Cloudstream3/safe`) aborts all loading | §5.4 |

---

## 8. ANI-KUTA implications

Our current (aniyomi) system, for contrast `[worklog — Task 31, verified there against our repo]`: extensions are **signed APKs** discovered via binary `AndroidManifest.xml` meta-data (feature `tachiyomi.animeextension.*` + `class` name + `extVersion`), loaded with **parent-first `PathClassLoader`** (our D-294 fix after the child-first kotlin-shadowing disaster), with a user **trust** gate, `libVersion` range checks, and per-source load-error surfacing (D-295/D-296).

What maps cleanly:

- **Classloader: identical philosophy.** CS3 uses `PathClassLoader(filePath, context.classLoader)` — parent-first, host-API classes resolved from the app. Our D-294 loader work is directly reusable; **do NOT introduce child-first for CS3 either.** But note the dependency-inversion difference: CS3 plugins bundle *nothing* (compileOnly stubs — §1.4), so the class-shadowing hazard that bit us with aniyomi extensions structurally **cannot occur** with well-formed CS3 plugins. Any CS3 plugin that did smuggle in a partial stdlib would be inert under parent-first (same as aniyomi now).
- **No PackageManager at all.** `.cs3` has no `AndroidManifest.xml`, so there is no `PackageInfo`, no signature info, no versionName — everything comes from `manifest.json` + `plugins.json`. We must NOT reuse our aniyomi AXML/manifest-parsing pipeline for CS3; instead: open zip → read `manifest.json` (one JSON parse) → `loadClass(pluginClassName)`. Much simpler than aniyomi's meta-data extraction.
- **Entry-point discovery is manifest-driven** (no annotation scan, no receiver/service scanning like aniyomi's extensions). If we ever build CS3 plugins ourselves, our build tooling must emit `manifest.json` with the right `pluginClassName` — i.e. we'd want the `com.lagradost.cloudstream3.gradle` plugin (JitPack) or replicate its `make` task output.
- **Trust model gap.** CS3's only integrity check is download-time sha256 vs `plugins.json`. Our aniyomi flow has an explicit user trust gate; for the Cloud Screen we should keep our gate (repo-add = consent) AND implement the sha256 check on download (it's cheap and is what repos actually verify). No signature checks to port — there is nothing to verify.
- **File layout to replicate:** store installed plugins under our own `filesDir/<something>/<repoKey>/<internalName>.<hash>.cs3` with the repo-key salt (CS3's answer to same-name plugins across repos, §5.1) — our current single install dir would collide. Also copy-in-then-setReadOnly before loading (Android 14+ SecurityException, §5.3.1) and consider oat-dir cleanup.
- **Resources are optional and runtime-wired.** If we support `requiresResources=true` plugins we must reproduce the `AssetManager.addAssetPath` reflection trick (hidden API — works but is grey-list territory; CS3 has shipped it for years) and expose `Resources` on our plugin wrapper. Anime providers (our primary target) mostly don't need it; the ones with settings Fragments do.
- **UI surface:** CS3 plugins can contribute a settings **Fragment** (`openSettings`) that the host shows in its own FragmentManager. ANI-KUTA is Compose — hosting an AndroidX `Fragment` (a `BottomSheetDialogFragment` at that) is possible (ComposeView inverse = `AndroidView`/fragment container) but a real integration cost; v1 could simply not surface `openSettings`.
- **Two base classes = two lifecycles.** Support `load()` (BasePlugin/cross-platform, most community plugins) at minimum; `load(context: Context)` (Plugin) for Android-native ones like the template. Our loader should dispatch the same way (`if (is Plugin) load(context) else load()`).
- **Multiple providers per plugin** (registerMainAPI has no limit) — our data model must key content by provider (`MainAPI.name`/`mainUrl`), with the *plugin* (file) as a second-level grouping for install/uninstall/update. Aniyomi's "1 APK = 1..N sources" actually matches this shape well.
- **Versioning is a bare int** (no semver, no libVersion-style compatibility range). Update = bigger int. `apiVersion: 1` in plugins.json is inert. Our lib-version range-check UI logic has no CS3 equivalent — simpler.
- **Silent failures:** upstream CS3 swallows load errors (toast only). We already learned this lesson the hard way (D-295/D-296) — keep our per-plugin error state + Failed-to-Load section for CS3 plugins too.
- **Hot reload exists** (`deployWithAdb` + `hotReloadAllLocalPlugins`) — nice-to-have for our own future CS3 plugin development; not needed for MVP.

Unverified / open items for later batches:

- The **gradle plugin source** (`recloudstream/gradle`) was NOT cloned — the exact behavior of `make`/`makePluginsJson`/`ensureJarCompatibility`, how `internalName`/`manifest.json` are derived (project name assumption), and whether `@CloudstreamPlugin` scanning happens there are `[inferred]` from outputs (§2.2, §4.1). B1-d or B5 could clone `recloudstream/gradle` to close this.
- Whether the Android app ever reads the dex-embedded `@CloudstreamPlugin` annotation anywhere outside `PluginManager` — grep says no (`grep -r CloudstreamPlugin cloudstream/app cloudstream/library` → only the declaration), but the gradle-plugin repo would settle intent. `[verified within local sources]`
- `Plugin`/`BasePlugin` split and `resources`/`openSettings` are app-version-specific (clone pinned @ `efc1915`, 2026-08-28); plugins in the wild built against older API may use `__filename` (deprecated ERROR-level alias, `BasePlugin.kt:52-61`).

---

*End of doc 02. Cross-references: repo/plugin-list JSON formats → doc 04; MainAPI surface → doc 03; app load internals (RepositoryManager, update checker, DataStore keys) → doc 13; our current aniyomi architecture → doc 14.*

---
## ✔ B5-a Verification Note (2026-08-29)
Checked: 36 claims sampled → 34 verified, 2 corrected (both trivial line-number fixes), 0 wrong.
Corrections:
1. §3.2: `extensions/build.gradle.kts:74` → `:73` (dependency line; claim correct).
2. §6.3: `ExamplePlugin.kt:18-23` → `:15-20` (openSettings block; claim correct).
Independently reproduced/confirmed: full `.cs3` census over phisher-builds (80 .cs3 / 47 .jar; 80 manifest.json + 80 classes.dex + 16 resources.arsc + 189 res/ entries; zero META-INF/AndroidManifest.xml in all 80), AllMovieLandProvider.cs3/jar byte sizes (57,618 / 297,020) + sha256s matching plugins.json `fileHash`/`jarHash` exactly, manifest.json content byte-identical, dex header parse (magic `dex\n035\0`, file_size 147,816, 58 class defs, 1,087 strings, `@CloudstreamPlugin` type present in dex, kotlin refs only), 53 .class files + `META-INF/AllMovieLandProvider.kotlin_module` + no MANIFEST.MF in any of the 47 jars, size pairs (AllWish/Animeav1/Animexin/BanglaPlex all exact), phisher plugins.json (80 entries, key union incl. jarUrl/jarHash/jarFileSize, apiVersion always 1, max version 661), all PluginManager.kt line citations (611 PathClassLoader, 593-687, 485-494 hot reload, 737-755 sanitized paths, 229-230 isOutdated, 210-221 maybeLoadPlugin, 79-85/102-103 PluginData), RepositoryManager downloadPluginToFile hash check, `TestPlugins/build.gradle.kts` full quote matches file exactly, ExampleProvider + DailymotionProvider gradle files, all 5 official extensions `isCrossPlatform = true` at :22, TestPlugins build.yml quote, settings.gradle.kts auto-include, Gradle 8.12 wrapper, CakesTwix catalog entries + 69-72 comment, BasePlugin/Plugin class shapes, MainAPI.kt 115/134-139/497/561, MainActivity.kt 1350-1400 startup order, csdocs workflow steps.
