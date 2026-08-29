# 16 — Integration Architecture (THE master CloudStream adoption plan)

> **Mission (B4-a)**: the top-level architecture for adding CloudStream (CS3) as ANI-KUTA's second
> extension ecosystem — the dependency strategy, module layout, the `.cs3` loading pipeline, the
> provider bridge, the provider-registry refactor, repo/settings coexistence, threading/isolation,
> licensing, and the risk register. This doc OWNS the runtime architecture; **doc 17** (B4-b) owns
> the data layer (its P1–P5 solutions are normative here — cited, not re-derived); **doc 18** will own
> Cloud Screen UI; **doc 19** playback/downloads runtime; **doc 20** the roadmap.
>
> **Ground truth inputs**: doc 02 (format + loader, read in full), doc 03 (MainAPI contract, read in
> full), doc 14 (our extension architecture, read in full), doc 15 (our data layer), doc 17 (data-layer
> plan, read in full), with citations from docs 01, 04, 05, 08, 11, 12, 13. CS3 sources at
> `/home/z/ANI-KUTA-WORK/research/cloudstream/` (master @ `efc1915`, 2026-08-28); our app at
> `ANI-KUTA/APP/ani-kuta/`.
>
> **Markers**: **[recommendation]** = chosen option among alternatives · **[design]** = proposed
> code/structure (sketches are PLANS, not compilable truth) · **[open-question]** = needs the user ·
> `[verified]`/`[inferred]` for fresh facts. §1.4 and parts of §9 contain **fresh forensics performed
> for this doc** (binary census over the 80 `.cs3` plugins in `research/phisher-builds/`).

---

## 0. Executive summary — the architecture in 10 decisions

| # | Decision | Where |
|---|---|---|
| 1 | **Vendor the CS3 library source** (`:external:cloudstream3`, pinned @ efc1915) instead of the JitPack artifact — Kotlin-metadata + reproducibility + GPL-provenance reasons | §1.7 |
| 2 | Ship a small **host-compat layer** (`com.lagradost.cloudstream3.*` shims for `utils.DataStore`, `CommonActivity`, gson) — fresh census: 7–16 of 80 real plugins import app-module classes | §1.4, §2.3 |
| 3 | New modules: `:external:cloudstream3` (vendored GPL library) + **`:data:cloudstream`** (loader/installer/repo client/manager/provider bridge + compat shims), mirroring `:data:extension`'s shape | §2 |
| 4 | `.cs3` loading = **direct file download + SHA-256 + parent-first `PathClassLoader`** (same loader philosophy as our D-294 aniyomi loader; NO PackageInstaller — `.cs3` is not an APK, doc 02 §1.1) | §3 |
| 5 | **Extend `SourceVideo` in `:core:provider-api`** (additive fields: label/source/referer/headers/type/extractorData/subtitle/audio) instead of a CS3-only video type — fixes the aniyomi facade's own playback-incompleteness at the same time | §4.2 |
| 6 | **`ExtensionProviderRegistry` facade + named Koin bindings** (multi-provider), consumed first by the new Cloud Screen; SearchViewModel/DetailsViewModel migration is explicitly *not* v1 | §5 |
| 7 | **Separate repo managers per ecosystem, one settings surface** — `CloudStreamRepoApi`/`CloudStreamRepoRepository` (repo.json/plugins.json) beside the aniyomi `index.json` classes; no shared storage | §6 |
| 8 | **v1 does NOT host CS3 plugin settings UI** (no `openSettings` surfacing — 58/58 census providers have none; doc 11 §6); hybrid Fragment host is a v2 option | §7 |
| 9 | Provider calls run in a supervisor-owned **`CloudStreamRuntime` scope** with CS3's own timeout-clamp model (5 s–8 min) and per-plugin/per-call isolation — D-295/D-296 error visibility, not CS3's toast-and-forget | §8 |
| 10 | **GPL-3.0 is the gating risk** — CS3's library is GPL-3.0 (single root LICENSE, verified), ANI-KUTA has NO license file; the user must decide relicensing before implementation | §9, §10-R1 |

```
 [CS3 repos]                [install]                 [load]                    [bridge]                 [features]
 repo.json ─► CloudStreamRepoApi ─► download .cs3 ─► CloudStreamPluginManager ─► CloudStreamExtension ─► Cloud Screen
 plugins.json   (sha256, filesDir/     (PathClassLoader     (mirrors APIHolder into    Provider            (new, registry-first)
 (doc 04)        Extensions/<salt>/)    parent-first)        our registry, sourceKey     :VideoExtension    Existing features keep
                                        manifest.json        = "cloudstream:<name>")     Provider           ExtensionManager (v1)
                                        → BasePlugin.load()
```

---

## 1. The dependency decision — how ANI-KUTA gets the CS3 runtime

### 1.1 What we must host (the runtime contract, restated)

A `.cs3` contains **only the plugin's own classes** — every other class resolves against the host app
through the parent-first classloader (doc 02 §1.4, §5.3 step 2). Hosting CS3 plugins therefore means
our APK must provide, at runtime, everything plugin code can touch:

1. **The whole plugin API library** — `MainAPI` + models + `new*` builders + `utils` (JsUnpacker,
   M3u8Helper, `loadExtractor`) + **~97 built-in extractors** + metaproviders (doc 01 §2.2; doc 03 §5).
   This is not a stub-able surface: `loadExtractor` dispatches over the built-in registry
   (`utils/ExtractorApi.kt:985`, doc 03 §6), and half of real providers resolve streams through those
   built-ins (doc 12 §10 "Assume extractor dependence").
2. **kotlin-stdlib** (ours), **NiceHttp** (the global `app` client — `MainActivity.kt:28-39` in the
   library, doc 03 §5.3), **jsoup** (`.document`), **Jackson ≤ 2.13.1** (`tryParseJson`/`parseJson`,
   `AppUtils`), **Rhino** (`JsUnpacker`/`evalJs`), kotlinx-serialization — doc 02 §3.2's template
   comment block is the authoritative "these are already in the app" list.
3. **A slice of CS3's *app* module** — plugins legally import app-module classes through the parent
   classloader (`utils.DataStore`, `CommonActivity`, `CloudStreamApp`, `MainActivity` — doc 11 §1.2,
   §3.1). A host that ships only the library gets `NoClassDefFoundError`s at those call sites.
   Quantified fresh in §1.4.

### 1.2 What the library pulls transitively — and what collides with our stack

CS3 library `commonMain` dependencies (all `implementation` scope — runtime-transitive, not
compile-transitive): `library/build.gradle.kts:55-69` — androidx annotation, **jackson-module-kotlin**,
**jsoup**, kotlinx-atomicfu/coroutines/datetime/io/serialization-json, **ksoup**, ktor-http,
**NiceHttp**, **Rhino**, whyoleg cryptography; `jvmCommonMain` adds **kotlin-reflect** and
**NewPipeExtractor** (`library/build.gradle.kts:76-82`). Versions from
`research/cloudstream/gradle/libs.versions.toml` vs ours (`APP/ani-kuta/gradle/libs.versions.toml`):

| Dependency | CS3 library wants | ANI-KUTA has today | Collision verdict |
|---|---|---|---|
| Kotlin stdlib/metadata | **2.4.0** (`toml:29`) | **2.2.0** (`toml:4`) | **HARD** — see §1.3 |
| AGP (build-time) | 9.1.1 (`toml:5`) | 8.9.1 (`toml:3`) | build-time only; relevant to vendoring |
| kotlinx-coroutines | 1.11.0 (`toml:32`) | 1.9.0 (`toml:52`) | soft — Gradle max-wins → 1.11.0; coroutines keeps binary compat; low risk `[inferred]` |
| kotlinx-serialization-json | 1.11.0 (`toml:35`) | 1.7.3 (`toml:53`) | soft — max-wins → 1.11.0; serialization is format-stable; regression-test aniyomi prefs/cache JSON `[inferred]` |
| jsoup | 1.22.1 (`toml:24`) | **1.19.1 pinned "Source API (binary compat for extensions)"** (`toml:65`) | **real risk** — aniyomi extension APKs compile against our 1.19.1; jsoup is API-stable but 1.19→1.22 spans removals; needs an aniyomi-extension regression pass before accepting 1.22.x `[inferred]` |
| OkHttp (via NiceHttp 0.4.18) | unspecified here | **5.0.0-alpha.14 pinned for aniyomi ext compat** (`toml:46-48`) | unknown — NiceHttp's own OkHttp requirement not in our clones (doc 03 §11 same caveat); resolution must be verified in a spike `[open-question]` |
| Jackson | **strictly 2.13.1** (`toml:22` — "Later versions don't support minSdk <26") | **none** | clean add — our minSdk is 24 (`build-logic/.../AndroidConfig.kt:10`), 2.13.1 satisfies it |
| Rhino / ksoup / ktor-http / whyoleg-crypto / NewPipeExtractor / kotlin-reflect | 1.8.1 / 0.2.6 / 3.5.0 / 0.6.0 / v0.26.3 / — | none | clean adds — but real DEX/method cost (§10-R10); NewPipeExtractor needs **core-library desugaring (NIO)** — CS3 app sets `coreLibraryDesugaring(libs.desugar.jdk.libs.nio)` (`app/build.gradle.kts:275`); we must enable it too |
| Coil / Compose / Koin / SQLDelight | library uses none of ours | — | no collision — the library is deliberately UI-free (KMP `commonMain`, doc 01 §2.2) |

**The pre-release vs `-SNAPSHOT` artifact question.** Two Maven coordinates exist in the wild
(doc 02 §3.2): (1) `cloudstream("com.lagradost:cloudstream3:pre-release")` — the template's
**compile-only stubs** configuration ("Stubs for all cloudstream classes",
`TestPlugins/build.gradle.kts:74-75`); (2) `implementation("com.github.recloudstream.cloudstream:library:-SNAPSHOT")`
— the **real KMP library** the official extensions repo builds against (`extensions/build.gradle.kts:74`).
As a HOST we need the real implementation, so coordinate (2) is the only artifact candidate; the
`pre-release` stub jar is for plugin authors and would explode at first `MainAPI` subclass
instantiation. Problems with (2): **`-SNAPSHOT` is a JitPack floating tag** — every master commit can
change the published bytes (JitPack builds on demand, `jitpack.yml` = `jdk: openjdk17`); there is no
released, versioned, stable artifact (the in-repo `maven-publish` config is `com.lagradost.api:1.0.1`,
`library/build.gradle.kts:21,126-132` — not what JitPack serves); and the exact upstream repo/tag
behind `com.lagradost:cloudstream3` was **not verifiable from our clones** `[open-question — needs a
live JitPack fetch before any artifact route]`. Upstream compat policy is compile-time ABI validation
only (doc 03 §8) — nothing pins the runtime a plugin talks to.

### 1.3 The toolchain collision (why the artifact route is expensive)

The library at master is built with **Kotlin 2.4.0** (`toml:29`) and AGP 9.1.1; our app builds with
**Kotlin 2.2.0** (`toml:4`) and AGP 8.9.1. Kotlin compilers only read metadata one minor version ahead
`[inferred — standard Kotlin policy]`: a library compiled with 2.4.x metadata cannot be consumed by
our 2.2.0 compiler ("class was compiled with an incompatible version of Kotlin"). We write host code
against `MainAPI`/`BasePlugin`/models, so we *compile against* the library — the artifact route forces
either (a) bumping the whole app to Kotlin 2.4.x (Compose compiler plugin, KSP, every module — an
app-wide destabilization we just went through with the 1.10.4 Compose pinning, `toml:17-34` history),
or (b) finding/holding an older library snapshot with compatible metadata — which JitPack's floating
`-SNAPSHOT` makes exactly the wrong tool for. Note the plugins themselves are compiled with Kotlin
2.1–2.3 (doc 02 §3.6 table) — plugin dexes target JVM 1.8 and are far more tolerant than host-compile
metadata.

### 1.4 Fresh forensics: how much of CS3's *app* module do real plugins need?

Census performed for this doc over all **80 compiled `.cs3` files** in `research/phisher-builds/`
(`unzip -p <f> classes.dex | grep -a <class-ref>`, binary dex string match — same method as doc 02 §1.4):

| Host class referenced by plugin dex | Module | Plugins affected |
|---|---|---|
| `Lcom/lagradost/cloudstream3/MainActivityKt;` (the library facade holding the global `app` HTTP client, doc 03 §5.3) | library | **78/80** — comes with the library |
| `com.lagradost.nicehttp.*` | external dep | **80/80** |
| `org.jsoup.*` | external dep | 64/80 |
| `com.fasterxml.jackson.*` | external dep | 61/80 |
| `Lcom/lagradost/cloudstream3/CommonActivity;` (toast/context helpers) | **app** | **16/80 (20%)** |
| `com.google.gson.*` | **app legacy shim** (deprecated "until extensions have time to migrate", `app/build.gradle.kts:279-282`) | **13/80 (16%)** |
| `com.lagradost.cloudstream3.utils.DataStore` (plugin settings storage, doc 11 §3.1) | **app** | **7/80** (AniDb, AnimePahe, Cinemacity, DoraBash, StremioX, Ultima, XDMovies) |
| `Lcom/lagradost/cloudstream3/MainActivity;` (the real Activity — e.g. to show Fragments) | **app** | 3/80 |
| `CloudStreamApp` | **app** | 7/80 |
| `fuzzywuzzy` | app legacy shim | **0/80 — can be skipped entirely** |

Reading: the *library + its external deps* are the load-bearing surface; the app-module surface is a
real but bounded slice (~16–20% of plugins touch `CommonActivity`/gson, 7/80 settings storage). On
ART, missing-class references typically fail at first *invocation*, not at class load
`[inferred]` — so a plugin that only uses `CommonActivity.showToast` in a settings path still
searches/loads/plays fine without the shim. **[recommendation]** ship a minimal
**host-compat layer** (§2.3): `com.lagradost.cloudstream3.utils.DataStore`,
`com.lagradost.cloudstream3.CommonActivity` (toast + context helpers only), `CloudStreamApp`
(companion getKey/setKey façade — doc 11 §3.1), and the gson dependency; skip fuzzywuzzy; skip
`MainActivity` (3/80, and those plugins want the CS3 UI anyway).

### 1.5 Licensing facts (short form — full note in §9)

CS3's repo has **one root LICENSE = GPL-3.0** (read in full for this doc; doc 01 §1.3 concur); the
library has **no separate LICENSE file** (verified: no `library/LICENSE`), so the library is GPL-3.0.
ANI-KUTA has **no LICENSE anywhere** in its tree (verified: no LICENSE at repo root, `ANI-KUTA/`, or
`APP/ani-kuta/`). Any route that ships CS3 library code in our APK (artifact *or* vendored source)
distributes GPL-3.0 code combined with ours. This is decision-grade; see §9. It does not differ
between options (a) and (b).

### 1.6 The three options

| | (a) JitPack artifact `com.github.recloudstream.cloudstream:library:-SNAPSHOT` | (b) vendor the library source as a Gradle module | (c) reimplement a minimal MainAPI host |
|---|---|---|---|
| What it is | depend on the published KMP library as extensions do (`extensions/build.gradle.kts:74`, doc 02 §3.2) | copy `library/` (174 Kotlin files, ~1.6 MB source — counted for this doc) into `:external:cloudstream3`, pin @ efc1915 | write our own `MainAPI`/models/builders/extractor-host with matching FQCNs + metadata |
| Kotlin metadata | **blocks** unless we bump app Kotlin to 2.4.x or gamble on snapshot vintage (§1.3) | none — compiles with OUR Kotlin 2.2.0 (library uses only KMP + `-Xexpect-actual-classes`, `library/build.gradle.kts:44`; compile-ability at 2.2 to be verified in the port spike) | ours by construction |
| Reproducibility | poor — floating `-SNAPSHOT`, JitPack on-demand builds, no release artifacts (§1.2) | exact — a commit hash in our repo; CI offline-safe (no jitpack.io in the dependency graph) | exact |
| Upstream sync | implicit + unpredictable (a rebuild can land mid-release) | explicit manual re-vendor (extractor fixes from upstream are the main want) | manual forever — we own ~97 extractors' fate |
| Patchability | none (would fork anyway) | full — e.g. tame global state, swap `USER_AGENT`, wire `DataStore` shims | full |
| Build integration | zero Gradle work | moderate — must build the KMP module under AGP 8.9.1 (the `com.android.kotlin.multiplatform.library` plugin exists in AGP 8.x), retarget compileSdk 37→36 (`toml:62` vs our `AndroidConfig.kt:9`), min JDK/`jvmTarget=1.8` matches | n/a |
| Runtime fidelity | binary-identical to what extension repos test against | source-identical @ efc1915 (our research snapshot) | **no** — plugins exercise long-tail API surface (all `new*` builders, `Score`, `M3u8Helper`, `JsUnpacker`, 97 extractors, `metaproviders` subclasses — docs 03 §5.4/§5.5, 05 §11.1); matching Kotlin metadata signatures for all of it is a huge, brittle surface |
| Effort | spike: days | port: ~1 week incl. build adaptation + smoke tests | v1-blocking months; essentially rebuild the ecosystem's value |
| GPL | ships GPL code | ships GPL code (provenance explicit in-tree) | avoids distributing GPL code; loading plugins still executes their (variously-licensed) code under a re-implemented GPL-designed API — legally grey (§9) |

### 1.7 `[recommendation]` — vendor the library source (`:external:cloudstream3`), pinned @ efc1915

**Option (b).** Rationale, ranked: (1) it is the only option that avoids the Kotlin-metadata trap
without an app-wide Kotlin 2.4 bump; (2) `-SNAPSHOT` is the wrong dependency kind for a runtime other
code *reflectively binds to* — the classloader contract between plugin dexes and host classes must be
reproducible across our releases, and only a pinned source tree gives that; (3) vendoring makes the
GPL provenance explicit and auditable (§9) instead of hiding it in a POM; (4) the library is small
(174 files / ~1.6 MB source) and deliberately dependency-light; (5) we retain the option to patch
global-state warts (APIHolder statics, doc 13 §3.1) without forking the ecosystem. Costs we accept:
an explicit upstream-sync chore (mainly to pull extractor fixes), and AGP/compileSdk build adaptation
(§1.6). Option (a) remains the **fallback quick-start for a two-day spike** (accept the Kotlin bump
temporarily on a branch) if vendoring's build adaptation stalls; option (c) is **rejected for v1** —
the 97 built-in extractors and the builder surface ARE the ecosystem; a reimplementation is a
multi-month project with binary-compat risk on every plugin. `[open-question]` to the user: approve
vendoring GPL source into the repo (§9 must be answered first — if the answer is "stay proprietary",
the entire program changes shape).

---

## 2. Module layout

### 2.1 New modules **[design]**

```
:external:cloudstream3     ← vendored CS3 library (GPL-3.0, pinned @ efc1915, PROVENANCE.md + LICENSE)
                             KMP module: commonMain + jvmCommonMain + androidMain only
                             (drop webMain — unwired upstream anyway, doc 01 §2.2)
:data:cloudstream          ← the CS3 ecosystem implementation, mirroring :data:extension's role (doc 14 §1):
   loader/     CloudStreamPluginLoader      (§3.3 — dex load, manifest, registration mirroring)
   installer/  CloudStreamPluginInstaller   (download + sha256 + file store; InstallStep reuse)
   repo/       CloudStreamRepoApi           (repo.json → plugins.json client, doc 04 §1)
               CloudStreamRepoRepository    (persisted repo list, StateFlow)
   manager/    CloudStreamPluginManager     (state machine §3.4, enable/disable, updates)
               CloudStreamRuntime           (scope + timeout + isolation wrapper, §8)
   provider/   CloudStreamExtensionProvider (the VideoExtensionProvider bridge, §4)
               Cs3Mappers.kt                (MainAPI → provider-api model mapping, §4.2)
   hostcompat/ com.lagradost.cloudstream3.utils.DataStore     (app-module shims, §1.4)
               com.lagradost.cloudstream3.CommonActivity
               com.lagradost.cloudstream3.CloudStreamApp
:core:provider-api         ← EXTENDED, not new: SourceVideo gains playback fields; SourceEpisode
                             gains season fields; optional sectioned-browse default methods (§4.2, §4.3)
```

No `:core:cloudstream-bridge` module: the bridge needs both provider-api and CS3 types, which is
exactly `:data:cloudstream`'s dependency set — same one-module-per-ecosystem shape `:data:extension`
established (doc 14 §1) so the two ecosystems stay symmetric and deletable.

### 2.2 Dependency graph **[design]**

```
:app ──► :data:cloudstream ──► :external:cloudstream3   (vendored library)
   │                        ──► :core:provider-api      (implements VideoExtensionProvider)
   │                        ──► :core:common, :core:preferences, :core:network, :core:database
   │                        ──► :core:content           (ContentResolver CS3 entry, doc 17 §2.3)
   │
   ├─► :data:extension ──► :core:provider-api           (unchanged; aniyomi ecosystem)
   └─► :feature:cloud-screen (doc 18) ──► :core:provider-api ONLY  (registry-first consumer)
```

Invariants **[design]**: `:external:cloudstream3` depends on NOTHING of ours (upstream tree kept
pristine apart from build config — minimizes re-vendor diffs); `:data:cloudstream` is the ONLY module
that imports `com.lagradost.*`; the provider-api seam stays free of third-party types (doc 14 §6.1
keeps holding — that was its whole point); `:core:smart-matcher`/`:core:video-resolver` remain
aniyomi-typed and untouched (doc 14 §1's layering warning is not made worse by this plan — the Cloud
Screen does not route through them, §4.4).

### 2.3 What lives where (responsibility table)

| Concern | Module | Notes |
|---|---|---|
| `.cs3` download/verify/store | `:data:cloudstream:installer` | sha256 vs `plugins.json.fileHash` (doc 04 §5.1); `filesDir/Extensions/<repoSalt>/<nameSalt>.cs3` (doc 02 §5.1) |
| dex load, manifest parse, classloader | `:data:cloudstream:loader` | parent-first `PathClassLoader` (§3.3) |
| Provider/extractor registry | `:external:cloudstream3` (library's `APIHolder`/`extractorApis`) + `:data:cloudstream:manager` mirror | we LET the library's registration machinery run (zero fork risk) and mirror into our own `CloudStreamProviderRegistry` keyed by `sourceKey` (§5.2) |
| repo.json/plugins.json client | `:data:cloudstream:repo` | OkHttp client, 5-min cache policy optional (doc 04 §1) |
| MainAPI↔provider-api mapping | `:data:cloudstream:provider` | §4.2 table |
| App-module compat shims | `:data:cloudstream:hostcompat` | §1.4 census; gson as a normal dependency |
| Playback/downloads runtime | doc 19's scope | data layer owned by doc 17 |
| Cloud Screen UI | doc 18's scope | consumes `ExtensionProviderRegistry` |

### 2.4 Koin wiring — following the ExtensionModule pattern **[design]**

Doc 14 §6.5's pattern (one module per ecosystem, explicit singles, `ExtensionModule.kt:31-43`)
extended with the multi-provider seam (§5):

```kotlin
// [design sketch] data/cloudstream/.../CloudStreamModule.kt
val cloudstreamModule = module {
    single(named("cs3Repo")) { /* OkHttpClient for repo/plugin downloads */ }
    single { CloudStreamRepoRepository(get()) }
    single { CloudStreamRepoApi(get(named("cs3Repo"))) }
    single { CloudStreamPluginLoader(get()) }
    single { CloudStreamPluginInstaller(get(), get(named("cs3Repo"))) }
    single { CloudStreamRuntime() }                                  // §8
    single { CloudStreamPluginManager(get(), get(), get(), get()) }
    single<VideoExtensionProvider>(named("cloudstream")) {
        CloudStreamExtensionProvider(get(), get())
    }
}
// app module assembly — the registry aggregates named providers explicitly (no reflection):
val providerRegistryModule = module {
    single { ExtensionProviderRegistry(
        providers = listOf(
            get<VideoExtensionProvider>(named("aniyomi")),      // existing binding, re-qualified
            get<VideoExtensionProvider>(named("cloudstream")),
        )
    ) }
}
```

The existing unqualified `single<VideoExtensionProvider>` in `ExtensionModule.kt:33-35` becomes
`named("aniyomi")` — safe precisely because it has **zero consumers today** (doc 14 §6.5, repo-wide
grep found only the impl + the registration).

---

## 3. The `.cs3` loading pipeline

### 3.1 What we reuse from `data/extension` — and what we cannot

| Piece | Reuse? | Why |
|---|---|---|
| Parent-first `PathClassLoader` philosophy (D-294) | **yes — verbatim idea** | CS3 uses the identical loader policy (`PluginManager.kt:611`, doc 02 §5.3 step 2); D-294's hard-won lesson transfers 1:1. Because well-formed `.cs3` files bundle NO dependencies (doc 02 §1.4), the class-shadowing hazard that bit us with aniyomi extensions structurally cannot occur — doc 02 §8 makes exactly this point |
| `ExtensionInstaller` / `PackageInstallerBackend` / `ExtensionInstallService` | **no** | `.cs3` is not an APK — no `PackageInstaller`, no package broadcasts, no PackageManager metadata (doc 02 §1.1, §8); install = plain file download |
| `InstallStep` UX (Downloading(progress)/Installing/Installed/Error), install `Mutex`, D-309 progress throttle, D-311 post-install re-scan trigger | **yes — pattern reuse** | the pipeline shape (download → terminal state → registry refresh) is ecosystem-agnostic; clone the sealed states |
| Trust gate (TrustService, per-package) | **adapt** | there is no signature to hash — `.cs3` has no META-INF at all (doc 02 §1.1). CS3's model: repo-add = consent + sha256 at download (doc 04 §5). **[recommendation]** keep our repo-verification UX (`verifyRepo` analog — doc 14 §4.4) as the consent gate, add the sha256 check (cheap, and it's what CS3 repos actually publish), skip a per-plugin trust gate (nothing to verify it against — doc 02 §8's "No signature checks to port") |
| `LoadResult` 4-state + D-295/D-296 error visibility | **yes — pattern reuse** | per-plugin Errored state with the real exception message; CS3's own loader toast-and-forgets (doc 02 §5.3 step 9) — documented upstream anti-pattern, we do better |
| AXML/manifest meta-data parsing | **no** | entry point discovery = read `manifest.json` from the classloader and `loadClass(pluginClassName)` — one JSON parse, no annotation scan (doc 02 §5.3 steps 3–4) |

### 3.2 Install & storage **[design]**

Follows CS3's own layout (doc 02 §5.1) so path-shape bugs can't surprise us:

- **Install**: `CloudStreamPluginInstaller.install(onlinePlugin)` → stream to `cacheDir` temp →
  compute sha256 → compare to `plugins.json.fileHash` (`"sha256-<hex>"`, doc 04 §5.1 — hash is
  integrity, not authenticity) → atomically move to
  `filesDir/Extensions/<sanitized(repoUrl)-hash>/<sanitizeFilename(internalName)>.<internalName.hashCode()>.cs3`
  — the repo-salt folder is CS3's answer to same-named plugins in two repos (doc 02 §5.1) and doubles
  as the installed-check. **Copy-in + `setReadOnly()` before loading** (Android 14+ rejects writable
  dex — `PluginManager.kt:527-556,601-609`, doc 02 §5.3 step 1); on app update, wipe `oat/` dirs
  (`deleteAllOatFiles` analog, doc 02 §5.3).
- **Update**: integer `version` comparison only (`online.version > installed.version`, or `-1`
  always-update — doc 02 §5.4); remote `status == 0` (Down) → uninstall/disable on next check
  (doc 04 §5.4 remote kill-switch). No libVersion-range analog — CS3 `apiVersion` is dead at runtime
  (doc 03 §8); do NOT port our 12.0–17.0 split-brain (doc 14 §4.3) into a second system.
- **Uninstall**: delete file → deregister (§3.4) → remove persisted record.

### 3.3 The loader — sketch

Grounded in doc 02 §5.3 (verified step numbers cited inline):

```kotlin
// [design sketch] — NOT compilable truth
class CloudStreamPluginLoader(private val context: Context) {

    fun loadPlugin(file: File): Cs3LoadResult {
        // 1. file.setReadOnly() — Android 14+ dex rule (doc 02 §5.3 step 1)
        // 2. parent-first PathClassLoader — THE shared invariant with D-294:
        val loader = PathClassLoader(file.absolutePath, context.classLoader)
        // 3. manifest as a classpath resource (NOT a zip re-open):
        val manifest = loader.getResourceAsStream("manifest.json")
            ?.use { parseJson<BasePlugin.Manifest>(it.readBytes()) }
            ?: return Cs3LoadResult.Error(file, "No manifest found")          // doc 02 §5.3 step 3
        // 4. entry class by name — no annotation scan (doc 02 §5.3 step 4):
        val plugin = runCatching {
            loader.loadClass(manifest.pluginClassName!!)
                .getDeclaredConstructor().newInstance() as BasePlugin
        }.getOrElse { return Cs3LoadResult.Error(file, it.message ?: it::class.simpleName) }
        // 5. ownership key (doc 02 §5.3 step 5):
        plugin.filename = file.absolutePath
        // 6. resources ONLY when requiresResources — hidden-API AssetManager.addAssetPath
        //    reflection, grey-list but shipped by CS3 for years (doc 02 §5.3 step 6 + §8):
        if (manifest.requiresResources) attachResources(plugin, file)
        // 7. lifecycle dispatch — two base classes, two entry points (doc 02 §6.1):
        //    if (plugin is Plugin) plugin.load(context) else plugin.load()
        //    registerMainAPI/registerExtractorAPI run inside load() and populate the
        //    LIBRARY's APIHolder.allProviders + extractorApis (BasePlugin.kt:20-35, doc 02 §6.1).
        //    We then MIRROR those registrations into CloudStreamProviderRegistry (§5.2).
        ...
    }
}
```

Notes: (a) we pass our host `Context` to `Plugin.load(context)` — plugins that stash it as
`AppCompatActivity` (template pattern, doc 02 §6.3) will get a ClassCastException at *their* cast
site if our host is a `ComponentActivity` — contained by per-plugin try/catch, and those plugins are
exactly the 3/80 `MainActivity`-touchers of §1.4; `(b)` keep loader instances alive forever like CS3
does (ART can't unload dex; "unload" = deregistration only — doc 02 §5.3 step 7, §5.5).

### 3.4 Plugin lifecycle (load / register / unload) **[design]**

State machine mirrors doc 13 §2.2 (CS3's own), with our two additions (enable flag, error states):

```
repo.json/plugins.json ──install──► DOWNLOADED ──load()──► LOADED ──register──► REGISTERED
                                        │  (manifest parse fail / ctor throw / load throw)
                                        ▼                        │ unload() (beforeUnload → deregister)
                                   ERRORED  ◄────────────────────┘        (update: unload old → load new;
                                   (D-295/D-296-style visible                 status==0: uninstall)
                                    reason + Retry / Uninstall)
   REGISTERED ⇄ DISABLED   ← OUR addition: CS3 has no per-plugin enable (doc 13 §0 #4);
                              we reuse our per-package isEnabled precedent (doc 14 §2.5)
   any ──delete file + deregister + drop record──► DELETED
```

- **Load ordering**: sequential per repo (alphabetical like CS3's local pass, doc 13 §2.4) inside one
  manager-owned coroutine — we do NOT need CS3's home-provider-first trick (that's startup UX for
  their app; our Cloud Screen can show a loading state, doc 18).
- **Unload** = `beforeUnload()` (try/catch) → remove this plugin's `MainAPI`s from `APIHolder`,
  its `ExtractorApi`s from `extractorApis` (doc 13 §2.2 unload semantics) → drop our registry mirror
  rows. Classloader leak is accepted (upstream reality, doc 02 §5.5).
- **Duplicate providers**: CS3 dedupes by `lang+name+mainUrl+qualifiedClass` silently dropping one
  (doc 03 §1) — **[design]** we surface a warning state instead (two providers, same name → the
  second gets a suffixed `sourceKey` `cloudstream:<name>#2`, never silently dropped).
- **Persisted record**: our own `Cs3PluginRecord(internalName, repoKey, filePath, version, isEnabled)`
  in SharedPreferences via `PreferenceStore` (mirroring our extension persistence inventory, doc 15 §5
  — extension state lives in prefs, not SQLDelight; we keep that convention for CS3).

### 3.5 Safe-mode analog **[design]**

Adopt CS3's crash-loop escape (doc 13 §0 #10) mapped onto our stack: the loader writes
`filesDir/cs3_last_error` *before* each plugin load and clears it after the batch; if a prior run's
marker exists at startup (or the user sets a `cs3_safe` debug flag), skip ALL CS3 loading for that
session, surface a "Cloud extensions disabled after crash — re-enable?" banner. Our app already has
per-source load-error surfacing discipline (D-295/D-296, doc 14 §2.5); this is the belt to that
suspenders because a *startup-crash-looping* plugin is the one error path per-plugin states can't
reach.

---

## 4. The provider bridge — `CloudStreamExtensionProvider`

### 4.1 The shape

Doc 14 §6.2 quotes the contract verbatim; the bridge implements it exactly like
`AniyomiExtensionProvider` does (~190 lines, doc 14 §6.4 — our reference implementation):

```kotlin
// [design sketch]
class CloudStreamExtensionProvider(
    private val manager: CloudStreamPluginManager,   // registry mirror + runtime
    private val runtime: CloudStreamRuntime,          // §8 wrapper (timeout + isolation)
) : VideoExtensionProvider {
    override val ecosystemId = "cloudstream"
    override val displayName = "CloudStream plugins"
    override val supportedContentTypes = setOf(ContentType.VIDEO)
    // key discipline: Source.key = "cloudstream:<MainAPI.name>"  — doc 17 §6.1 convention
}
```

Doc 14 §9.3 Q1 (`install(pkgName)` presupposes an APK-package-shaped ecosystem) — resolved by
**reinterpretation + rename**: **[design]** add a neutral `install(handle: String)` /
`uninstall(handle: String)` to the interface (source-compatible default methods; for the aniyomi impl
`handle = pkgName`, for CS3 `handle = internalName`). This is the interface's own growth point doing
its job (D-031/D-302 intent, doc 14 §6).

### 4.2 Model mapping (MainAPI → provider-api), including the `SourceVideo` fix

Base map follows doc 05 §11.1 with the gaps closed **our way** (provider-api is our code — additive
fields are the cheapest correct fix, and they fix the *aniyomi* facade's gaps simultaneously):

| Provider-api call | CS3 call(s) | Mapping notes |
|---|---|---|
| `observeInstalledSources()` | registry mirror of `APIHolder.allProviders` | `MainAPI` → `Source(ecosystemId="cloudstream", sourceId=api.name, name, lang)`; `isNsfw` = `TvType.NSFW ∈ supportedTypes`; dedupe handling §3.4 |
| `fetchContentList(source, page, query)` | `query != null` → `search(query, page)` (paginated overload, doc 03 §2.5); `query == null` → `getMainPage(page, request)` | CS3 `SearchResponseList.hasNext` → our "empty page = end" convention is NOT enough (doc 05 §11.1 pagination gap) — **[design]** `fetchContentList` returns `Flow<SourcePage>` (items + hasNext) in the extended provider-api (§4.3) |
| `fetchContentDetails(content)` | `load(url)` | `LoadResponse` → `SourceContentDetails` per doc 05 §11.1 + doc 17 §5.1 field destinations (poster → ext axis, `posterHeaders` → new `ext_poster_headers` column — doc 17 §5.1; extras → `ext_extra_json`). **CS3 fuses details+episodes** in one `load()` (doc 03 §2.8): the never-populated `SourceContentDetails.episodes` (doc 14 §9.3 Q6) becomes the CS3 single-fetch path — `fetchEpisodeList` re-serves the same list |
| `fetchEpisodeList(content)` | (from cached `load()`) | `Episode` → `SourceEpisode(contentKey, externalId = episode.data, name, thumbnailUrl, dateUpload)` — `externalId` = the opaque `Episode.data` blob verbatim (doc 05 §11.1: "the only stable handle for loadLinks"; doc 12 §10: "persist them verbatim… never parse them in UI code"). **[design]** `SourceEpisode` gains `seasonNumber: Int?` + `numberInSeason: Int?` (additive) — the identity inputs for doc 17 §3.3's `EpisodeKeys` |
| `fetchVideoList(episode)` | `loadLinks(data, isCasting=false, subCb, cb)` | streaming-callback → Flow adaptation, §4.3; `ExtractorLink` → **extended `SourceVideo`**, below |
| `install/uninstall/setEnabled/checkForUpdates` | manager ops | `handle` = internalName (§4.1) |

**The `SourceVideo` extension (the doc 14 §6.4 gap #1 / doc 05 §11.2 #1 fix) — [design]:**

```kotlin
// [design sketch] — additive fields with defaults = binary-compatible; aniyomi impl fills them too
data class SourceVideo(
    val url: String,
    val quality: String = "Default",
    val videoUrl: String? = null,
    // NEW — the ExtractorLink payload (doc 05 §7.3) CS3 playback cannot live without:
    val label: String? = null,              // ExtractorLink.name  (user-facing mirror label)
    val source: String? = null,             // ExtractorLink.source (extractor name — 2nd-tier re-resolve key, doc 17 §4.3)
    val referer: String? = null,            // hotlink protection (doc 05 §11.2 #1)
    val headers: Map<String, String> = emptyMap(),
    val type: SourceVideoType = SourceVideoType.VIDEO,   // M3U8 / DASH / TORRENT / MAGNET / VIDEO (ExtractorLinkType, doc 05 §6.6)
    val extractorData: String? = null,      // keep-alive token (doc 08 §4.6)
    val subtitleTracks: List<SourceSubtitleTrack> = emptyList(),
    val audioTracks: List<SourceAudioTrack> = emptyList(),
)
```

**Why extend in place rather than a CS3-side video type**: (1) doc 14 §6.4 records the aniyomi facade
has the *same* holes (`Video` carries headers/subtitles/audio but the facade drops them —
`VideoResolver` uses them downstream, `VideoResolver.kt:83-90`); one fix covers both ecosystems and
finally makes the provider seam playback-grade (doc 14 §9.3 Q2 answered); (2) a parallel
`CloudStreamVideo` type would fork the resolver handoff (`ResolvedVideosRegistry` is string-keyed and
type-agnostic — doc 14 §9.2 — feeding it two model families doubles the watch-path surface for zero
benefit); (3) consistency: `CloudStreamResolveContext` (doc 17 §4.2) already specifies exactly these
fields (`linkLabel`, `linkSource`, `quality`, `extractorData`) as what must survive into re-resolve —
the extended `SourceVideo` is the in-memory twin of that persistence shape. `[recommendation]`
confirmed.

### 4.3 `loadLinks` → Flow, and sectioned browse

Two CS3 semantics don't fit the current provider-api (doc 05 §11.1 gaps #1/#5) — **[design]** close
both with additive default methods on `VideoExtensionProvider` (source-compatible; aniyomi impl keeps
defaults):

```kotlin
// [design sketch]
interface VideoExtensionProvider : ExtensionProvider {
    // existing members unchanged…
    /** Sectioned browse (CS3 mainPage rows); default = one unnamed section, query-based only. */
    fun fetchSections(source: Source): Flow<List<SourceSection>> = flowOf(listOf(SourceSection.DEFAULT))
    fun fetchSectionContent(source: Source, section: SourceSection, page: Int): Flow<SourcePage> =
        fetchContentList(source, page, section.data)   // delegate
    /** hasNext-aware page (CS3 SearchResponseList.hasNext / HomePageResponse.hasNext). */
    fun fetchContentListPaged(source: Source, page: Int, query: String?): Flow<SourcePage> =
        fetchContentList(source, page, query).map { SourcePage(it, hasNext = it.isNotEmpty()) }
}
data class SourceSection(val key: String, val title: String?, val data: String?, val isHorizontal: Boolean = false)
data class SourcePage(val items: List<SourceContent>, val hasNext: Boolean)
```

`loadLinks` adaptation: the provider runs the CS3 call inside `runtime.call(provider)` (§8) and
**emits a first page as soon as early links arrive** (CS3's streaming model — links surface while
extraction continues, doc 03 §2.9) using a callbackFlow/channelFlow; dedupe by URL upstream-style
(doc 03 §2.9 pitfall); terminal `Boolean` + collected links decide success vs `ErrorLoadingException`
mapping (CS3's user-visible error convention — doc 03 §2.4 pitfall). `isCasting` is always `false`
for us (no Chromecast surface — our player is mpv-based, doc 14 §1).

### 4.4 What the bridge deliberately does NOT do

- No metaprovider support (TmdbProvider/TraktProvider subclasses — doc 03 §7): they are `MainAPI`s
  like any other and will *load*, but our v1 UI treats every provider as a direct provider; the
  "MetaProvider" label/delegation flow (doc 03 §2.1 `providerType`) is out of scope.
- No `getVideoInterceptor` wiring (0 plugin usages in census, doc 03 §2.11), no
  `extractorVerifierJob` scheduling from the bridge (that's the player/download runtime's job —
  doc 19; the `extractorData` *payload* is carried through `SourceVideo`).
- No `getLoadUrl`/`supportedSyncNames` (sync-ID deep launch — doc 03 §2.12; metaprovider facility,
  ignore initially).
- No routing through `:core:video-resolver`/`:core:smart-matcher` (aniyomi-typed, doc 14 §1) — the
  Cloud Screen resolves via its own path feeding `ResolvedVideosRegistry` (doc 17 §4.3 keeps it the
  agnostic handoff).

---

## 5. The provider registry refactor

### 5.1 The problem (doc 14 §8.1–8.3, restated)

(1) Koin wiring is single-provider — one `single<VideoExtensionProvider>` (`ExtensionModule.kt:33-35`),
no list/set binding anywhere; (2) **zero consumers of the seam** — all feature code injects
`ExtensionManager` + Aniyomi types (`SearchViewModel.kt:50`, `DetailsViewModel.kt:67`,
`ExtensionsSettingsScreen.kt:126`, `ReverseAutoLinkService.kt:37` — doc 14 §8.2); (3) Aniyomi types
leak through the UI layer (`SEpisode` nav keys, `AnimeCatalogueSource` pickers, `AnimeHttpSource`
resolver signatures — doc 14 §8.3). A second provider *impl* can register today only by clashing.

### 5.2 `ExtensionProviderRegistry` — the aggregator facade **[design]**

```kotlin
// [design sketch] :core:provider-api (or a tiny :core:provider-registry)
class ExtensionProviderRegistry(private val providers: List<VideoExtensionProvider>) {
    fun all(): List<ExtensionProvider> = providers
    fun byEcosystem(id: String): VideoExtensionProvider?           // "aniyomi" | "cloudstream"
    fun observeAllSources(): Flow<List<Source>> =                   // merged, ecosystem-tagged,
        combine(providers.map { it.observeInstalledSources() }) { it.toList().flatten() }
    fun dispatch(sourceKey: String): VideoExtensionProvider? =
        byEcosystem(sourceKey.substringBefore(':'))                 // key discipline doc 14 §6.3
}
```

Every persisted/source-shaped id in the new world is the ecosystem-prefixed `sourceKey` string
(`"cloudstream:Uakino"`, `"aniyomi:4697…"`) — doc 17 §6 made this the schema convention
(`source_key TEXT` sweep, prefs re-keying); the registry is the runtime twin of that convention
(doc 17 §6.2 table's registry row anticipates exactly this class). The aniyomi provider's
`observeInstalledSources` already emits `Source.key = "aniyomi:<id>"` (doc 14 §6.4) — no change
needed there.

### 5.3 Migration path — minimal-touch, ordered by blast radius

| Step | Seam | Change | Blast radius |
|---|---|---|---|
| 1 | Koin | unqualified binding → `named("aniyomi")`; add `named("cloudstream")`; add `providerRegistryModule` (§2.4) | **zero** — zero consumers exist (doc 14 §6.5); one-line-ish, exactly as doc 14 §9 seam 2 predicts |
| 2 | provider-api | additive `SourceVideo`/`SourceEpisode` fields + default methods (§4.2/§4.3); `install(handle)` rename | zero for existing callers (defaults; the aniyomi facade optionally backfills headers/subs — improves it) |
| 3 | extensions settings UI | one flat ecosystem list → **ecosystem sections/tabs** in `ExtensionsSettingsScreen`; CS3 rows reuse trust/available/installed/errored sections + lifecycle row actions (doc 14 §9 seam 5) — CS3 rows add settings gear = hidden v1 (§7) | small, contained screen (no ViewModel — doc 14 §7.4) |
| 4 | Cloud Screen (new) | consumes `ExtensionProviderRegistry` from day one — the seam's first load-bearing consumer, proving it end-to-end (doc 14 §9 seam 3 option b) | none (new code) |
| 5 | Details dispatch | `DetailsViewModel.loadFromExtension` gains a `sourceKey: String` entry overload dispatching via registry (Long overload kept); `resolveEpisode` keeps `AnimeHttpSource` for aniyomi, gains a CS3 branch that calls the CS3 bridge (§4.3) and feeds `ResolvedVideosRegistry` | medium — one ViewModel, additive entry point |
| 6 | Search unification (search picker pref Long→sourceKey, merged results) | **deferred** — the picker pref is Long-typed today (doc 14 §5.2) and EXTENSION-mode search stays aniyomi-only in v1 | explicitly out (doc 14 §9.3 Q4 — "prove the seam first") |

**[recommendation]** this order: steps 1–4 land with the first CS3 PR; step 5 with Cloud Screen
playback; step 6 only after the Cloud Screen has validated the registry UX (could be never, if
unified search is rejected — §11).

### 5.4 UI-routing consequence (preview — doc 18 owns the detail)

The registry-first path makes the **Cloud Screen a separate flow** (own tab/browse surface, own
search-over-providers) the natural v1: CS3 content is movies/series/dramas (doc 10 taxonomy) whose
browse model is sectioned shelves (`mainPage` rows — doc 03 §2.3), not our anime-shaped browse;
unified search would force the step-6 migration AND AniList-vs-CS3 result mixing the current search
screen deliberately avoids (doc 14 §7.1 "alternative modes, never merged"). Consequences for doc 18:
entry point + tab placement, sectioned-browse UI, CS3 source picker (by provider name + lang), and
the details screen for `content_type ∈ {movie, series, drama}` (doc 17 §5.1's coarse map).
`[open-question]` §11-1.

---

## 6. Repo management coexistence

Facts: our repo layer is aniyomi-`index.json`-shaped (`ExtensionRepoApi`/`ExtensionRepoRepository`,
SharedPreferences `anikuta_extension_repos`, no hash verification — doc 14 §4); CS3 is two-level
`repo.json → pluginLists[] → plugins.json` with absolute URLs + sha256 (doc 04 §1, §5.1); the
filename `repo.json` collides with our *optional* repo metadata file of the same name and totally
different schema (doc 14 §4.1, doc 04 §8) — a parsing accident waiting to happen if one client ever
sees the other's URL.

**[recommendation] Separate managers per ecosystem under one settings surface:**

- `CloudStreamRepoApi` + `CloudStreamRepoRepository` in `:data:cloudstream` (§2.3) — separate code,
  separate SharedPreferences key (`anikuta_cs3_repos`), own `OkHttpClient` (`named("cs3Repo")`).
  No shared client/store: the two index formats, verification rules, and update semantics (libVersion
  range vs bare Int version — doc 04 §8) share NOTHING but the UX pattern.
- **One "Repositories" settings surface** with an ecosystem selector (Aniyomi / CloudStream sections),
  reusing our add-verify-delete UX (`verifyRepo` pattern, doc 14 §4.4) and CS3's
  `cloudstreamrepo://` deep link *not* adopted v1 (no benefit without external integrations).
- CS3 add-repo verification = fetch repo.json + follow pluginLists + require ≥1 parseable entry
  (mirrors CS3's own "did it parse" bar, doc 04 §8) **plus** our explicit "this repo can run
  arbitrary code in-app" consent text (the CS3 community registry's own warning, doc 04 §5.2).
- Updates: CS3 update checks piggyback on the extensions-screen entry throttle pattern (D-301,
  30-min — doc 14 §4.4) via `CloudStreamPluginManager.checkForUpdates()`.
- No default repos either way (both ecosystems ship zero — D-043 our side, `PREBUILT_REPOSITORIES`
  empty CS3-side, doc 04 §8). `[open-question]` §11-3.

---

## 7. Settings & plugin UI hosting

Facts (doc 11): CS3 plugin settings = **imperative** — `openSettings: ((Context) -> Unit)?` on the
app-side `Plugin` class, showing a plugin-authored `Fragment`/`BottomSheetDialogFragment`
(`BlankFragment` pattern), storage via the app-module `utils.DataStore` (flat
`rebuild_preference` SharedPreferences, no plugin isolation — doc 11 §3.1, §7.2). Our app is
Compose-only and renders aniyomi settings by **tree-walking AndroidX `PreferenceScreen` objects**
(`SourcePreferencesScreen`, doc 14 §7.4) — that works because aniyomi settings are declarative
trees; **the trick cannot transfer to imperative lambdas** (doc 14 §7.4, doc 11 §8).

Options: (a) `FragmentContainerView` host — full compat, but requires fragment-ktx +
`FragmentActivity` (our activities are ComponentActivities), plugin theming gaps
(`R.style.ResultInfoText` etc. — doc 11 §8), `requiresResources` wiring, fragment-outlives-loader
leaks (doc 11 §7.3); (b) Compose-only subset — simply don't surface `openSettings` v1; (c) hybrid
later.

**[recommendation] (b) for v1, (c) deferred until demanded.** Evidence: **58/58 census providers
expose no settings at all** (doc 11 §6, doc 12 §9.2); settings exist only at plugin level in a
minority (the census says providers never; plugin-level is the template + SyncPlugin — and
SyncPlugin is app-bundled, runs against app-module classes, and won't load on our host anyway —
§1.4). The 7/80 plugins using `DataStore` (§1.4) still *browse and play* without the settings UI;
their settings affordance is hidden, exactly like CS3 hides the gear on TV (doc 11 §4.3). When (c)
comes, ship it behind a flag with the `requiresResources` + theming caveats from doc 11 §8 as its
design checklist. The `hostcompat` DataStore shim (§2.3) is still included in v1 so those 7 plugins'
*storage reads inside provider code* (not their UI) keep working.

---

## 8. Threading, scopes & lifecycle

**CS3's model** (docs 03 §2.2, 12 §10, 13 §3.2): plugin calls are `suspend` but run wherever the
caller puts them; the app wraps every call in `safeApiCall` + `withTimeout(getTimeout(hint))` with a
**5 s–8 min clamp, default 120 s** (`APIRepository.kt:28-64`); everything else runs in global
`ioSafe` coroutines that swallow all throwables (silent-failure anti-pattern — doc 02 §5.3 step 9,
doc 12 §10). **Our model**: coroutines-native flows, request-generation identity (D-305), per-source
error states (D-295/D-296), no global scopes.

**[design] `CloudStreamRuntime`** — one Koin-owned supervisor scope, the single place plugin code runs:

```kotlin
// [design sketch]
class CloudStreamRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Every MainAPI call goes through here: timeout clamp (CS3's own numbers, doc 03 §2.2),
     *  Throwable capture, cancellation propagation. */
    suspend fun <T> call(provider: MainAPI, hint: Long?, block: suspend () -> T): Result<T> =
        runCatching { withTimeout(clamp(hint)) { block() } }   // clamp = (hint ?: 120_000).coerceIn(5_000, 480_000)
    // loadLinks streaming variant (§4.3) uses the same clamp with loadLinksTimeoutMs.
    // Verifier job (extractorData, doc 08 §4.6) runs in this scope, cancelled by the caller's job.
}
```

- **Timeouts**: adopt CS3's per-method provider hints + clamp wholesale (doc 03 takeaway #5: "cheap,
  effective anti-hang pattern worth copying for BOTH extension systems") — per-method hints from
  `MainAPI` (`loadLinksTimeoutMs` etc., doc 03 §2.2), clamped 5 s–8 min. Our VideoResolver already
  uses a 30 s hoster timeout (doc 14 §7.3) — aniyomi keeps its own; no cross-ecosystem unification v1.
- **Cancellation**: our Flow collection cancels → `withTimeout`/suspend machinery cancels the plugin
  coroutine; CS3 providers are suspend-cooperative, so cancellation is honored except for
  CPU-spinning/blocked plugins — bounded by the timeout. (D-305-style generation guards belong to the
  Cloud Screen ViewModel, doc 18.)
- **One bad plugin must not kill the app** — three isolation rings: (1) *load-time*: per-plugin
  try/catch → Errored state (D-295/D-296 pattern — CS3 toast-only is the documented anti-pattern);
  (2) *call-time*: `runtime.call` captures every Throwable into `Result` → user-visible error UI;
  (3) *startup crash-loop*: safe-mode analog (§3.5). Honest limit: plugins run **in-process with full
  app permissions, no sandbox** (doc 04 §5.2 preamble) — a native crash or OOM in a plugin kills the
  process; safe mode is the recovery, not prevention.
- **Global mutable state**: the vendored library keeps its statics (`APIHolder.allProviders`,
  `extractorApis`, the `app`/`insecureApp` Requests singletons — doc 13 §3.1, doc 03 §5.3).
  **[design]** `CloudStreamPluginManager` is the ONLY writer (load/unload) and
  `CloudStreamRuntime` the only caller; the registry mirror (§5.2) is the read model everything else
  uses. Extractor dispatch order must be preserved (reverse-registration priority — doc 03 §4) so
  plugin extractors legitimately shadow built-ins; we never reorder `extractorApis`.
- **WebView**: some extractors fall back to `WebViewResolver` (library `network/WebViewResolver.kt`;
  doc 12 §10 notes WebView fallbacks "can take seconds and can fail wholesale"). Runtime wiring
  (main-thread hop + headless WebView lifetime) is doc 19 scope; the architecture cost: our app must
  be willing to instantiate WebViews — we already ship one for the aniyomi WebView shortcut
  (doc 14 §7.4), so no new capability class.

---

## 9. Legal / licensing note (factual + flags — decisions are the user's)

1. **CS3 is GPL-3.0.** Single root LICENSE (`research/cloudstream/LICENSE` — full GPL-3.0 text, read
   for this doc); no separate library LICENSE (verified absent), so the plugin API library we would
   ship is GPL-3.0 (doc 01 §1.3 concurs: "License: GNU GPL v3.0").
2. **ANI-KUTA currently has no license at all** (verified: no LICENSE file at repo root, `ANI-KUTA/`,
   or `APP/ani-kuta/`). Default copyright = all rights reserved; the project is developed in a public
   GitHub repo (README + dashboard links) — i.e. source is *published* even though no license is
   granted.
3. **Consequences of §1's recommended route (vendored library in our APK):** distributing an APK
   containing GPL-3.0 code combined with our code makes the combined work subject to GPL-3.0
   obligations (§4/§5: source availability of the whole combined work under GPL terms, for anyone we
   distribute binaries to). Same is true of the artifact route — GPL doesn't care how the code got in.
   Vendoring merely makes the fact visible in-tree (a compliance plus, not a legal difference).
4. **The "GPL plugin host" grey zone:** re-implementing the API (option §1.6-c) would avoid
   *distributing* GPL code, but (a) plugins in the wild are individually licensed (many unlicensed,
   several GPL — phisher's repo has no LICENSE file `[verified absent]`), and running them remains
   user-directed execution of third-party code; (b) API reimplementation vs. GPL is unsettled
   (Google v. Oracle addressed Java's API under fair use; GPL contractual analysis differs). Not
   legal advice — a flag, not a clearance.
5. **Neighboring facts:** our existing aniyomi stack is not GPL-triggering (aniyomi/mihon source API
   is Apache-2.0; our vendored `:core:source-api` mirrors that ecosystem's terms) — CS3 is the first
   strong-copyleft dependency in the program. CS3's own posture (zero providers, DMCA statement,
   doc 01 §1.3) matches our zero-default-repos stance (§6), which also keeps *content* liability
   posture symmetric with the aniyomi system.
6. **Required decision (blocking):** before any implementation — (i) relicense ANI-KUTA GPL-3.0
   (clean, cheap while the project is personal, and consistent with the FOSS ecosystem we're
   joining), or (ii) keep ANI-KUTA proprietary and drop/suspend CS3 hosting (option (c) with legal
   review, or a "bring-your-own-runtime" non-distribution design — architecturally ugly), or
   (iii) take legal advice on the plugin-host question. **This is the program's gating decision —
   see R1.** `[open-question]` §11-0.

---

## 10. Risk register (top 10, ranked)

| # | Risk | L×I | Mitigation |
|---|---|---|---|
| R1 | **GPL-3.0 obligations on ANI-KUTA** (§9) | H×H | blocking user decision BEFORE implementation; if relicensing: add LICENSE + vendored-tree LICENSE + PROVENANCE; if not: program halts or reshapes |
| R2 | **Kotlin/toolchain drift** (library Kotlin 2.4 / AGP 9.1 vs ours 2.2 / 8.9 — §1.3) | H×H on artifact route; L×M vendored | vendor + compile with our Kotlin (§1.7); port spike is the first milestone; if artifact fallback chosen, Kotlin bump becomes an app-wide project |
| R3 | **Transitive version collisions** (jsoup 1.19.1→1.22.1 with aniyomi-ext binary compat; coroutines/serialization max-wins; OkHttp alpha vs NiceHttp — §1.2) | M×H | resolve-and-regress: dependency-analysis CI gate (we already run `checkDependencyAlignment` per `libs.versions.toml:17-34` note), aniyomi-extension smoke suite after bump; keep jsoup at 1.19.1 and force-library-compat if 1.22 breaks extensions `[open-question]` |
| R4 | **Plugin quality variance** (sparse providers, hasNext liars, missing/faked years, blocking ops — doc 12 §9–10) | H×M | null-tolerant Cloud Screen UI (doc 12 §10 checklist), URL-dedupe + capped pagination (D-304 analog), timeouts (§8), opaque label rendering |
| R5 | **App-module class gaps** (16/80 `CommonActivity`, 13/80 gson, 7/80 DataStore — §1.4) | M×M | hostcompat layer (§2.3) sized by the census; graceful degradation (fail-at-invocation, not at load); re-run census before v1 freeze |
| R6 | **Silent-failure inheritance** (CS3 toast-and-forget load errors — doc 02 §5.3 step 9) | M×M | D-295/D-296 per-plugin Errored states + Retry/Uninstall from day one (§3.4) |
| R7 | **Global mutable state in the library** (APIHolder statics, single `app` client, no unload of dex — doc 13 §3, doc 02 §5.3 step 7) | M×M | single-writer discipline (§8), registry mirror as read model, accept classloader leak, re-vendor if a global needs surgery |
| R8 | **In-process crash / OOM kills the app** (no sandbox — doc 04 §5.2) | L×H | safe-mode analog (§3.5), per-plugin disable, crash-loop detection; document the limit honestly |
| R9 | **Upstream ecosystem volatility** (extractors break as sites change; `@Prerelease` APIs crash stable hosts — doc 03 §8; pre-release drift) | M×M | pinned vendored library (no auto-bumps), user-driven plugin updates only, monitor upstream releases on re-vendor |
| R10 | **APK size / method count** (Rhino + Jackson + NewPipeExtractor + ksoup + ktor + 97 extractors; both apps ship unminified — ours `anikuta.android.application.gradle.kts:36`, CS3 `isMinifyEnabled = false`) | M×L | measure in the port spike; enabling R8/ProGuard on a reflection-heavy GPL tree is its own risk — defer; report delta to the user |

Also tracked, below top-10: WebView-dependent extractors (runtime, doc 19); repo.json filename
collision (§6 — separated clients kill it); duplicate provider names (§3.4 — suffixed keys);
JitPack availability if fallback route taken; desugaring requirement for NewPipeExtractor (§1.2).

---

## 11. Open questions for the user

0. **`[open-question]` THE licensing decision (§9, R1)**: relicense ANI-KUTA GPL-3.0 to enable CS3
   hosting? Without yes (or an explicit informed alternative), B5 implementation should not start.
1. **`[open-question]` Unified search vs separate Cloud Screen** (§5.4): recommend separate flow v1,
   unify later only if wanted — confirm.
2. **`[open-question]` Vendoring approval**: OK to add `:external:cloudstream3` (~1.6 MB GPL source,
   174 files) to the repo? (Artifact fallback needs a Kotlin 2.4 app bump — appetite?)
3. **`[open-question]` Bundled repos**: zero defaults (recommended, matches D-043 + CS3 posture) or
   pre-seed the official `recloudstream/extensions` repo? If pre-seeded: NSFW filter default?
4. **`[open-question]` NSFW policy** (doc 10 §8.5, doc 17 §11-12): single master toggle default OFF
   — confirm, and whether it should also suppress watch-progress writes like CS3 does.
5. **`[open-question]` jsoup version strategy** (R3): bump to 1.22.1 for the library and regression-test
   aniyomi extensions, or force 1.19.1 and verify the vendored library tolerates it?
6. **`[open-question]` Plugin settings UI** (§7): v1 skip confirmed? (58/58 providers have none.)
7. **`[open-question]` gson shim**: ship gson for the 13/80 plugins (16%) or accept their load failure?
   (Recommend ship — it's one dependency.)
8. **`[open-question]` Trust model confirmation** (§3.1): repo-add = consent + sha256 (no per-plugin
   trust gate, CS3-style) — confirm this is acceptable for the Cloud Screen.
9. **`[open-question]` Hot-reload tooling**: defer the `deployWithAdb`-style dev loop for our own
   future CS3 plugins? (Recommended: defer — doc 02 §8 calls it nice-to-have.)
10. **`[open-question]` minSdk guard**: Jackson 2.13.1 is minSdk-24-safe, but confirm we never intend
    minSdk <24, and that enabling core-library desugaring app-wide (NewPipeExtractor) is acceptable.

---

## 12. Verification status

- Fresh verifications performed for this doc: `research/cloudstream/library/build.gradle.kts` (full),
  `research/cloudstream/gradle/libs.versions.toml` (full), `research/cloudstream/app/build.gradle.kts`
  (deps blocks), `research/cloudstream/settings.gradle.kts`, `research/cloudstream/LICENSE` (GPL-3.0
  header + text), absence of `library/LICENSE` / `app/LICENSE`; our `APP/ani-kuta/gradle/libs.versions.toml`
  (full), `build-logic/.../AndroidConfig.kt`, `anikuta.android.application.gradle.kts:36`,
  `ExtensionModule.kt` (full), `settings.gradle.kts` module list; **the 80-`.cs3` binary census of
  §1.4** (reproducible: `unzip -p <f>.cs3 classes.dex | grep -a <ref>` over `research/phisher-builds/`);
  absence of any LICENSE in the ANI-KUTA tree.
- All CS3-behavior claims inherit docs 01–15 verification (cited per claim); all our-code claims cite
  doc 14/15 (which verified them in-source) or the fresh reads above.
- **Not verified** (flagged inline): the upstream repo/tag behind the `com.lagradost:cloudstream3:
  pre-release` coordinate (needs a live JitPack fetch); NiceHttp 0.4.18's OkHttp requirement;
  compile-ability of the library source under Kotlin 2.2.0/AGP 8.9.1 (the port spike's first task);
  Kotlin one-minor metadata tolerance as stated (standard policy, check on first artifact build);
  exact size/method-count delta (R10 measurement pending).
- All Kotlin/SQL blocks are **[design sketches]** — nothing here exists in code. No commits; only
  this doc + the worklog were modified.

*End of doc 16. Companion plans: doc 17 (data layer — normative for schema/keys/contexts), doc 18
(Cloud Screen UI), doc 19 (playback/downloads runtime), doc 20 (roadmap — §1.7's port spike and §5.3's
step order are its first work packages).*
