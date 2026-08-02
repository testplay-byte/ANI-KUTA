# 11 — Dependency Injection Research

> Research + recommendation for the ANI-KUTA rebuild's DI strategy.
> Decision under research: **D-034** (supersedes the older D-009 "Hilt" placeholder).
> Sources: old project docs (`02-architecture.md` §7, `03-tech-stack.md` §9), `App.kt`,
> `ProviderApiModule.kt`, `DetailsModule.kt`, and web research (Nov 2025) on Aniyomi /
> Mangayomi / Cloudstream / Kotatsu / Sora extension ecosystems, Hilt, Koin 4, Koin
> Annotations 2.x, and Animiru.

---

## TL;DR Recommendation

> **Koin 4.x (DSL) + Koin Annotations 2.x (opt-in compile-time verification) for the host
> app, plus Injekt isolated to `:core:source-api` for Aniyomi-family extension compat.**
>
> Do **not** use Hilt. Do **not** use Hilt+Koin dual. Keep the old project's proven
> Koin+Injekt split, but harden Koin with Annotations 2.x for compile-time safety and
> isolate Injekt more strictly than the old project did.
>
> This supersedes D-009's "Hilt" placeholder. See §Recommendation for the full reasoning.

---

## The Extension Compat Constraint

### How Injekt works (Aniyomi family)

Aniyomi / Keiyoushi / Mihon-family extensions are standalone APKs loaded at runtime via
a `PathClassLoader` (the old project's `ChildFirstPathClassLoader`). Extension bytecode
was compiled against `uy.kohesive.injekt` and calls `Injekt.get<T>()` at runtime for a
small set of host-provided singletons. The host app **must** register these in Injekt's
global registry before any extension source is instantiated, or the extension throws
`InjektionException` (or `ExceptionInInitializerError` for static-init `Json` access).

The four singletons the old project registers (`App.kt` lines 50–79):

```kotlin
Injekt.addSingleton(fullType<Application>(), this)
Injekt.addSingleton(fullType<Context>(), this)
Injekt.addSingleton(fullType<NetworkHelper>(), networkHelper)   // MUST be a class, not interface
Injekt.addSingletonFactory(fullType<Json>()) { Json { ignoreUnknownKeys = true; explicitNulls = false } }
```

This is **ADR-029 in the old project** and is described as "non-negotiable for extension
compat." The rebuild notes (`09-rebuild-notes.md` §3.2) explicitly say: "Keep Injekt
because Aniyomi extensions require it. If we can isolate it to `:core:source-api` only,
do that. Don't let it spread to the host app."

### Does Injekt constrain the host app's own DI choice?

**No.** Injekt is a global mutable singleton registry (`uy.kohesive.injekt.Injekt` object).
It is completely independent of Hilt, Koin, or any other DI the host uses for its own
code. The host can use Hilt, Koin, kotlin-inject, or no DI at all — Injekt just needs
those 4 singletons registered in `App.onCreate()` before the extension loader runs.

This means:

- **Hilt + Injekt** coexist cleanly (Hilt's compile-time graph never sees Injekt; Injekt
  is just 4 lines in `App.onCreate()`).
- **Koin + Injekt** coexist cleanly (the old project's proven approach).
- **Koin + Injekt + a third DI for another ecosystem** also coexists, as long as each
  extension ecosystem's required host singletons are registered in its respective locator.

The constraint is **not** "host must use Injekt." The constraint is **"host must populate
Injekt's registry for Aniyomi-family extensions to find their singletons."** Those are
very different statements. The host's own code never needs to call `Injekt.get<T>()`.

### Which ecosystems actually use Injekt?

This is the critical finding from the research. **Only Aniyomi-family extensions use
Injekt.** The other four target ecosystems use entirely different patterns:

| Ecosystem | Host tech | Extension format | Extension DI / API contract | Injekt? |
|---|---|---|---|---|
| **Aniyomi / Keiyoushi / Mihon** | Kotlin/Android | APK, DEX classloader | `eu.kanade.tachiyomi.*` source-api + `Injekt.get<T>()` for `Application`/`Context`/`NetworkHelper`/`Json` | **Yes** (mandatory) |
| **Animiru** (D-028 base app) | Kotlin/Android (Aniyomi fork) | Same as Aniyomi | Same as Aniyomi (it's a fork) | **Yes** (it's Aniyomi-family) |
| **Mangayomi** | **Flutter (Dart) + Rust** | **JavaScript files** run in an embedded JS engine | JS bridge API exposed by the Dart host; no Kotlin DI involved | No (different language/runtime entirely) |
| **Cloudstream** | Kotlin/Android (KMP `commonMain` migration underway) | APK plugin, DEX classloader | `com.lagradost.cloudstream3.MainAPI` base class + `com.lagradost.cloudstream3.*` contracts; plugins extend `MainAPI` directly. No DI locator. | No |
| **Kotatsu parsers** | Kotlin **library** (JVM + Android) | **Compiled into the app at build time** (not runtime-loaded) | `MangaParser` / `LightNovelParser` interfaces; discovered via a generated index (`MangaParserSessionFactory`), no DI framework | No |
| **Sora (cranci1/Sora)** | **iOS/macOS (Swift)** | Swift "modules" | Native Swift module API | No (not even Android) |

**Implications:**

1. **Injekt is an Aniyomi-only constraint, not a universal one.** The rebuild must keep
   Injekt for Aniyomi-family compat (Animiru base = same), but should not design the host
   DI around Injekt.
2. **Each ecosystem needs its own adapter module**, not its own DI:
   - `:data:extension-aniyomi` — DEX loader + Injekt singletons + `AnimeSource` adapter.
   - `:data:extension-cloudstream` — DEX loader + `MainAPI` adapter (no Injekt).
   - `:data:extension-mangayomi` — embedded QuickJS (the old project already shipped
     QuickJS 0.9.2 for this exact purpose) + JS bridge to `MangaSource` adapter.
   - `:data:extension-kotatsu` — direct dependency on `kotatsu-parsers` library; a thin
     adapter wrapping `MangaParser` into the app's unified source contract.
   - `:data:extension-sora` — **out of scope for Android** (Sora is iOS-only). Revisit if
     a credible Android port appears.
3. Each adapter registers one `ExtensionProvider` impl into the host's
   `List<ExtensionProvider>` multi-binding. Adding an ecosystem = one module + one line.
   This is the **pluggable registry pattern** the rebuild requires (D-031 + D-038).

---

## Hilt: Pros & Cons

### Pros (for this project)

- **Compile-time graph verification.** A broken dependency graph fails the build, not the
  app at runtime. This is Hilt's single biggest advantage.
- **Google-blessed Android standard.** First-class integration with `ViewModel`
  (`@HiltViewModel`), `WorkManager` (`@HiltWorker`), navigation, Compose
  (`hiltViewModel()`), `@AndroidEntryPoint` for Activities/Fragments/Services. The app
  uses WorkManager (for the download service, auto-backup scheduler, update checker) —
  Hilt's `@HiltWorker` is genuinely nice here.
- **Multi-module support is native.** Each Gradle module contributes
  `@Module @InstallIn(SingletonComponent::class)` bindings; Hilt's KSP processor
  aggregates them at compile time. No central `modules(...)` list to maintain.
- **Multi-binding via `@IntoSet` / `@IntoMap`.** Clean for the pluggable registry pattern
  (see §The Pluggable Registry Pattern).
- **Mature tooling.** IDE navigation, KSP-based (fast), large community.

### Cons (for this project)

- **Compile-time only — runtime-loaded extensions cannot use Hilt.** Hilt generates the
  graph at compile time for the host app's classes only. Extension APKs loaded via DEX
  classloader at runtime cannot use `@Inject` — there is no Hilt component for them.
  This is **fine** for this project (extensions use Injekt / `MainAPI` / JS, not Hilt),
  but it means Hilt offers zero benefit *for the extension layer*. Hilt would only serve
  the host app's own code.
- **NOT Kotlin Multiplatform-ready.** Hilt is Android-only. If ANI-KUTA ever shares
  business logic to iOS/desktop via KMP (D-038 says "future-proof"; Cloudstream itself is
  migrating to KMP `commonMain`), Hilt cannot follow. Koin can. This is a real future
  risk — locking in Hilt now forecloses KMP later.
- **`Set<T>` multibinding, not `List<T>`.** Hilt's `@IntoSet` produces a `Set<T>` (no
  ordering). For ordered pluggable registries (e.g., provider priority), you must use
  `@IntoMap` with a `@IntKey` / `@ClassKey` / custom key — more boilerplate than Koin's
  `listOf(...)`. The old project's `List<MetadataProvider>` pattern would need rewriting.
- **Steeper learning curve.** Dagger concepts: components, subcomponents, scopes,
  qualifiers, multibindings, `@InstallIn` component hierarchies. The user has stated
  (CORE_RULES §1) the codebase must be "agent-friendly — new AI agents can jump into a
  specific part without full context." Hilt's component hierarchy works against this.
- **Build-time cost.** KSP annotation processing adds ~5–15s to clean builds for a
  36-module project. Not a blocker, but real.
- **Migration risk from old project.** The old project's 24 Koin modules + 4 `List<T>`
  registries would all need rewriting in Hilt's `@Module @InstallIn` + `@IntoSet` form.
  High effort, low value vs. keeping Koin.
- **D-009 said "Hilt" — but D-034 explicitly reopens the question.** D-009 was a
  placeholder decision ("To be added. Phase 1"). D-034 (Phase 1) supersedes it with
  "NEEDS RESEARCH." This research is that work.

### Bottom line on Hilt

Hilt is the right choice for a **pure-Android, Google-ecosystem, no-runtime-extensions**
app. It is **not** the right choice for an app that (a) wants KMP optionality, (b) has a
proven Koin codebase to inherit patterns from, (c) needs clean `List<T>` pluggable
registries, and (d) is built for "future-proofing" (D-034's words). Hilt's compile-time
safety — its only real advantage — is now matched by Koin Annotations 2.x (see Koin
section).

---

## Koin: Pros & Cons

### Pros (for this project)

- **Kotlin Multiplatform-ready.** Koin 4.0 (Nov 2023+, stable through 2025) runs on
  Android, iOS, JVM, JS, Wasm. If ANI-KUTA ever shares logic to iOS/desktop, Koin
  follows. Cloudstream's own KMP migration validates this direction.
- **`List<T>` multi-binding is clean and ordered.** The old project's
  `single<List<MetadataProvider>>(named("metadataProviders")) { listOf(get(), get()) }`
  pattern is idiomatic, preserves order, and reads naturally. Adding a provider = one
  class + one entry in `listOf(...)`. This is exactly the pluggable registry pattern the
  rebuild requires (D-031, D-038).
- **`getAll<T>()` for true auto-discovery multi-binding.** Koin also supports
  `single { AImpl() } bind A::class` repeated N times, then `getAll<A>()` returns all N.
  This is closer to Hilt's `@IntoSet` but still produces a `List<A>` (ordered by
  registration). Two patterns available, choose per use case.
- **Multi-module support is trivial.** Each Gradle module ships a `val fooModule = module
  { ... }`. The app assembles them in `startKoin { modules(fooModule, barModule, ...) }`.
  The old project did this across 24 modules cleanly.
- **Compile-time safety is now available.** Koin Annotations 2.x (March 2025, mature Nov
  2025) adds a KSP compiler plugin that verifies the dependency graph at compile time —
  the same guarantee Hilt offers. `@Single`, `@Factory`, `@KoinViewModel`, `@Module`
  annotations generate the DSL under the hood. You can mix DSL + annotations in the same
  project (DSL for hot-iteration modules, annotations for stable modules).
- **Lower learning curve.** No component hierarchies, no scopes, no `@InstallIn`. A new
  agent can read a Koin module and understand it in 30 seconds.
- **Proven in the old project.** 24 modules, 4 pluggable registries, 0 DI-related
  production incidents documented in the architecture doc. Keeping Koin = preserving a
  working pattern.
- **Works perfectly with runtime-loaded code.** If a future extension ecosystem ever
  wanted to use Koin (unlike Aniyomi which uses Injekt), the host could share its Koin
  context with extension classloaders. Hilt cannot do this.
- **`verify()` DSL function.** Even without annotations, Koin modules can be unit-tested
  with `module.verify()` to catch missing bindings at test time.

### Cons (for this project)

- **No compile-time safety by default.** Without Koin Annotations, a missing binding
  crashes at runtime, not compile time. **Mitigation**: adopt Koin Annotations 2.x
  (recommended) or at minimum add `verify()` calls in unit tests.
- **Slightly slower at runtime.** Hashmap lookups vs. Hilt's generated direct
  constructors. Negligible for an app of this size (the old project shipped fine). Koin
  4.0's perf is competitive — see Kotzilla benchmarks.
- **Less Google-blessed.** Google recommends Hilt. Koin is third-party. In practice this
  matters very little (Koin has 9k+ stars, massive adoption, JetBrains ecosystem
  alignment via KMP).
- **`List<T>` key collision pitfall.** The old project hit this: multiple
  `single<List<*>>` bindings share the same erased Koin key. The fix (named qualifiers)
  is documented in `DetailsModule.kt` lines 40–44. This is a known sharp edge — the
  rebuild must use named qualifiers from day one and document the rule.
- **Annotations 2.x migration friction.** Some users report rough migration from
  annotations 1.x → 2.x (Nov 2025 Slack thread). Since the rebuild starts fresh, this
  doesn't apply — adopt 2.x from day one.

### Bottom line on Koin

Koin 4.x + Koin Annotations 2.x gives the rebuild: KMP optionality, clean `List<T>`
pluggable registries, low learning curve, proven pattern from the old project, and
compile-time safety (closing Hilt's only real advantage). The cons are manageable with
discipline (named qualifiers) and Annotations 2.x.

---

## Dual DI Approaches

### Option A: Hilt (host) + Injekt (extensions only)

- **How it works.** Hilt manages all host app code via `@HiltAndroidApp`,
  `@HiltViewModel`, `@Module @InstallIn`. Injekt is reduced to 4 lines in `App.onCreate()`
  registering the Aniyomi-required singletons. Injekt never appears in any host class
  except `App.kt` and `:core:source-api`.
- **Pros.** Compile-time safety for host. Clean separation. Android-standard.
- **Cons.** Blocks KMP future. `Set<T>` multibinding less clean than `List<T>`. Steeper
  learning curve. Migration from old Koin codebase is high-effort. D-034's
  "future-proofing" requirement is not met (Hilt is Android-only).
- **Verdict.** ❌ **Not recommended.** The KMP-foreclosure alone disqualifies it given
  D-034's explicit future-proofing mandate and Cloudstream's own KMP migration proving
  the pattern is coming for this category of app.

### Option B: Koin (host) + Injekt (extensions only) — old project's approach

- **How it works.** Koin manages all host app code via `startKoin { modules(...) }`.
  Injekt is reduced to 4 lines in `App.onCreate()` for Aniyomi extension compat. Each
  feature/core module ships a `val xxxModule = module { ... }`.
- **Pros.** Proven (old project shipped this for 24 modules). KMP-ready. Clean `List<T>`
  pluggable registries. Low learning curve. Lowest migration risk.
- **Cons.** No compile-time safety by default. Sharp edge on `List<T>` key collision
  (mitigated by named qualifiers).
- **Verdict.** ✅ **Recommended baseline.** Add Koin Annotations 2.x (Option C) for the
  compile-time safety upgrade.

### Option C: Koin + Koin Annotations 2.x (host) + Injekt (extensions only) — RECOMMENDED

- **How it works.** Same as Option B, but stable modules use Koin Annotations
  (`@Single`, `@KoinViewModel`, `@Module`) which generate the DSL under the hood and
  verify the graph at compile time via KSP. Hot-iteration modules (UI experiments,
  feature prototypes) can still use the DSL directly. The two interop seamlessly.
- **Pros.** All of Option B's pros, PLUS compile-time safety (matching Hilt's main
  advantage), PLUS a migration path to pure-annotation DI later if desired.
- **Cons.** Two syntaxes in the codebase (DSL + annotations). Mitigated by a per-module
  convention (e.g., `:core:*` uses annotations, `:feature:*` uses DSL during active
  iteration).
- **Verdict.** ✅ **Best option.** This is the recommendation.

### Option D: Hilt (host) + Koin (extension bridge) + Injekt (Aniyomi extensions)

- **How it works.** Hilt for host. Koin as a "bridge" layer for extensions that need a
  DI locator. Injekt for Aniyomi-family extensions.
- **Pros.** None meaningful for this project. Aniyomi extensions use Injekt, not Koin —
  Koin buys nothing for them. Cloudstream extensions use `MainAPI`, not Koin. Mangayomi
  extensions are JS, not Kotlin.
- **Cons.** Three DI frameworks. Maximum confusion. Directly violates CORE_RULES §1
  ("agent-friendly"). The Kotlin Slack consensus (Aug 2022 thread): "They co-exist but it
  causes confusion to know who manages what. Temporarily in many cases become forever. I
  wouldn't risk it."
- **Verdict.** ❌ **Strongly not recommended.**

### Option E: Koin only (no Injekt) — fork all Aniyomi extensions

- **How it works.** Fork every Aniyomi/Keiyoushi extension to remove Injekt calls and
  use Koin instead. Ship forked extensions from a custom repo.
- **Pros.** Single DI framework.
- **Cons.** Defeats the entire purpose of Aniyomi extension compat (D-027). Massive
  ongoing maintenance burden — every upstream extension update must be re-forked. The
  Keiyoushi extension ecosystem is thousands of sources. This is a non-starter.
- **Verdict.** ❌ **Not feasible.** D-027 explicitly requires "unmodified Aniyomi
  extension compatibility."

---

## What Animiru Uses

Animiru (D-028 base app, github.com/quickdesh/Animiru) is a fork of Aniyomi. Aniyomi is a
fork of Tachiyomi (with anime support). The Tachiyomi/Aniyomi/Animiru family historically
uses **Injekt for everything** — both the host app's own DI AND the extension compat
layer. This is the Tachiyomi architectural inheritance: Injekt was bundled into the app
as a global service locator and used pervasively.

However, D-028 says: *"Use Animiru as the **reference** base, NOT as a fork to inherit
verbatim. Build own system as project grows."* The old ANIKUTA project deliberately broke
from the Tachiyomi tradition by using **Koin for host + Injekt for extensions** (ADR-023 +
ADR-029). This was a conscious modernization, and it worked — 24 modules, 4 pluggable
registries, zero DI incidents.

**Therefore: ANI-KUTA is NOT bound by Animiru's Injekt-everywhere choice.** Animiru is a
reference for *feature scope and extension compat behavior*, not for *host DI choice*.
The rebuild should keep the old project's Koin+Injekt split (Option C), not regress to
Injekt-everywhere.

---

## The Pluggable Registry Pattern

The old project uses `List<T>` multi-binding in Koin for every pluggable extension
point. The canonical example (`DetailsModule.kt`):

```kotlin
val detailsModule: Module = module {
    single<List<AnimeDetailsProvider>>(named("animeDetailsProviders")) {
        listOf(
            AniListDetailsProvider(...),
            ExtensionDetailsProvider(...),
        )
    }
    single { AnimeDetailsProviderRegistry(get<List<AnimeDetailsProvider>>(named("animeDetailsProviders"))) }
}
```

The pattern's rules (documented in old `DetailsModule.kt` comments):

1. **Always use a named qualifier** (`named("animeDetailsProviders")`) — without it, Koin's
   type erasure makes all `List<*>` bindings share the same key → `ClassCastException` at
   runtime. This is the one sharp edge; the rebuild must enforce named qualifiers from
   day one.
2. **Never refactor into multiple `single<T>` calls** — they overwrite each other (last
   wins). The `single<List<T>>` form preserves all impls.
3. **Adding a provider = one class + one entry in `listOf(...)`** — zero changes to the
   registry or its consumers.

The rebuild needs this pattern for at minimum:

- `List<MetadataProvider>` — AniList, MAL (future), TMDB (future).
- `List<AnimeDetailsProvider>` — AniList, Aniyomi-extension, Cloudstream (future).
- `List<BackupProvider>` — Tracker, Library, Extension links, Downloads (old project has
  4).
- **`List<ExtensionProvider>` (NEW for D-031)** — Aniyomi, Cloudstream, Mangayomi-JS,
  Kotatsu. One impl per ecosystem adapter module.

### How the pattern looks in each DI option

**Koin (DSL) — old project's pattern:**

```kotlin
single<List<MetadataProvider>>(named("metadataProviders")) {
    listOf(get<AniListMetadataProvider>(), get<MALMetadataProvider>())
}
```

Clean, ordered, one line per provider. ✅ Recommended.

**Koin Annotations 2.x:**

```kotlin
@Single(binds = [MetadataProvider::class])
class AniListMetadataProvider(...) : MetadataProvider { ... }

// In a module:
@Module
class MetadataProviderModule {
    @Single
    fun providers(
        anilist: AniListMetadataProvider,
        mal: MALMetadataProvider,
    ): List<MetadataProvider> = listOf(anilist, mal)
}
```

Slightly more verbose but compile-time-verified. Can mix with DSL.

**Koin `getAll<T>()` alternative:**

```kotlin
single { AniListMetadataProvider(...) } bind MetadataProvider::class
single { MALMetadataProvider(...) } bind MetadataProvider::class
// Consumer:
single { MetadataProviderRegistry(getAll<MetadataProvider>()) }
```

Auto-discovers all `MetadataProvider` bindings. Order = registration order. Cleaner for
"add a provider = one `single` line, no central list to edit." Tradeoff: order is less
explicit.

**Hilt `@IntoSet`:**

```kotlin
@Module @InstallIn(SingletonComponent::class)
abstract class MetadataProviderModule {
    @Binds @IntoSet
    abstract fun bindAnilist(impl: AniListMetadataProvider): MetadataProvider
    @Binds @IntoSet
    abstract fun bindMal(impl: MALMetadataProvider): MetadataProvider
}

// Consumer:
@Single
class MetadataProviderRegistry @Inject constructor(
    val providers: Set<MetadataProvider>  // NOTE: Set, not List — no order
)
```

Works, but: `Set` not `List` (no ordering), `@Binds @IntoSet` is more verbose per
provider, and for ordered priority you'd need `@IntoMap @IntKey`. Less clean than Koin
for this exact pattern.

### Recommendation for the rebuild

Use **Koin DSL with named `List<T>` qualifiers** for pluggable registries (proven,
clean, ordered). For stable core modules, optionally migrate to Koin Annotations
`@Single(binds = [...])` + `getAll<T>()` for compile-time verification. Document the
"always name your List qualifiers" rule in `CORE_RULES.md` to prevent the key-collision
bug from recurring.

---

## Recommendation

### Decision: Koin 4.x + Koin Annotations 2.x + Injekt (isolated)

**For the host app:** Koin 4.x (BOM) as the primary DI, with Koin Annotations 2.x
adopted per-module for compile-time graph verification. Start with DSL for fast
iteration in early phases; add annotations to `:core:*` modules once their APIs
stabilize.

**For Aniyomi-family extension compat:** Injekt (`com.github.mihonapp:injekt` JitPack
fork, same version as old project: `91edab2317`), strictly isolated to:
- `App.onCreate()` — 4 singleton registrations.
- `:core:source-api` — the `NetworkHelper`, `Json`, `ExtensionAppHolder` definitions.
- `:data:extension-aniyomi` — the loader that instantiates `AnimeSource` instances.

Injekt MUST NOT appear in any feature module, any ViewModel, any UI code, or any
non-extension data module. Enforce via a detekt rule or architectural test.

**For other extension ecosystems:** No Injekt involvement. Each gets its own adapter
module registering one `ExtensionProvider` impl into the host's
`List<ExtensionProvider>` Koin binding.

### Reasoning

1. **Future-proofing (D-034's primary criterion).** Koin is KMP-ready; Hilt is not.
   Cloudstream — a direct competitor in this exact app category — is migrating to KMP
   `commonMain`. Picking Hilt now forecloses KMP later. Koin keeps the door open at
   zero cost.
2. **Pluggable registries (D-031 + D-038).** Koin's `List<T>` + named qualifier is
   cleaner than Hilt's `Set<T>` + `@IntoSet` for the exact pluggable pattern the rebuild
   needs (4+ registries today, 5+ extension ecosystems tomorrow).
3. **Proven pattern (low risk).** The old project shipped Koin+Injekt across 24 modules
   with zero DI incidents. Reusing the pattern eliminates a category of migration risk.
4. **Compile-time safety (Hilt's main advantage) is now available in Koin.** Koin
   Annotations 2.x (mature Nov 2025) provides KSP-based graph verification equivalent to
   Hilt's. The one technical reason to prefer Hilt is gone.
5. **Agent-friendliness (CORE_RULES §1).** Koin DSL reads in 30 seconds. Hilt's
   component hierarchy does not. This matters for an AI-agent-maintained codebase.
6. **Aniyomi compat is preserved.** Injekt stays, isolated to its 3 allowed locations.
   Aniyomi extensions load unmodified (D-027 satisfied).
7. **Other ecosystems are not constrained.** Cloudstream (`MainAPI`), Mangayomi (JS),
   Kotatsu (library) each get a clean adapter module with no Injekt contamination.

### What this overrides

- **D-009** ("Tech stack: Hilt") — superseded. D-009 was a Phase-0 placeholder ("To be
  added. Phase 1"). D-034 explicitly reopened the DI question. This research is the
  resolution. Update `decisions.md` D-009 status to "→ superseded by D-034 (Koin + Koin
  Annotations + Injekt isolated)".
- **`knowledge/tech-stack.md`** line 14 ("DI | Hilt | TBD Phase 1") — update to "DI |
  Koin 4.x + Koin Annotations 2.x + Injekt (isolated)".

### Migration / adoption steps (for the implementation phase)

1. Add Koin BOM 4.x + `koin-android` + `koin-androidx-compose` to the version catalog.
2. Add `koin-annotations` 2.x + KSP plugin to `:core:*` modules first (stable APIs).
3. Add Injekt (`com.github.mihonapp:injekt:91edab2317` via JitPack) to `:core:source-api`
   and `:data:extension-aniyomi` ONLY. Add a detekt rule forbidding `uy.kohesive.injekt`
   imports outside these two modules.
4. Establish the pluggable-registry convention: every `List<T>` binding MUST have a
   `named("...")` qualifier. Document in `CORE_RULES.md`.
5. In `App.onCreate()`, register the 4 Injekt singletons FIRST (before `startKoin`),
   wrapped in try/catch — exactly as the old project does.
6. For `ViewModel`s, use `koinViewModel()` (Compose) — equivalent to Hilt's
   `hiltViewModel()`.
7. For `WorkManager` (download service, backup scheduler, update checker), use Koin's
   `WorkerScope` or a manual `by inject()` in `Worker.create()` — slightly more
   boilerplate than `@HiltWorker` but well-documented.
8. Add `koinModule.verify()` unit tests for every module to catch missing bindings at
   test time (defense in depth alongside Annotations' compile-time check).

### Risks & mitigations

| Risk | Mitigation |
|---|---|
| `List<T>` key collision (old project's bug) | Mandatory named qualifiers + detekt rule + documented in CORE_RULES. |
| Koin Annotations 2.x is "new" (2025) | Adopt incrementally: `:core:*` first, `:feature:*` later. DSL remains the fallback. |
| No `@HiltWorker` convenience | Use Koin's `workerDSL` / `by inject()` pattern. 3 workers in this app — low overhead. |
| "Google recommends Hilt" | Koin is widely adopted (9k+ stars), JetBrains-aligned, KMP-native. Recommendation is pragmatic, not dogmatic. |
| Injekt leaks into host code | Detekt rule + architectural test. Old project already enforces isolation informally. |

### Final

**Koin 4.x + Koin Annotations 2.x for host DI, Injekt isolated to `:core:source-api` +
`:data:extension-aniyomi` for Aniyomi-family extension compat.** This satisfies D-027
(Aniyomi compat), D-031 (multi-extension), D-034 (future-proof DI), D-038 (modular +
agent-friendly), and CORE_RULES §1 (clarity), while preserving the old project's proven
pattern and keeping the KMP door open.
