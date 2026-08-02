# 12 — Navigation Research

> Sub-agent research (Task 2-NAV). Compares navigation libraries for the ANI-KUTA
> rebuild and recommends one. All facts verified via web search (Aug 2026 snapshot)
> with source URLs in footnotes.

---

## Project Requirements

The navigation library for the ANI-KUTA rebuild must satisfy **all nine** of the
following constraints (consolidated from the task brief + the old project's
known pain points documented in `02-architecture.md` §6 and §10):

| #  | Requirement                                                                              | Why it matters                                                                                       |
|----|------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| R1 | Multi-content-type "modes" (anime / manga / novel)                                       | Each mode has its own browse / details / watch-or-read / library screen set; nav must switch cleanly. |
| R2 | Modular Gradle architecture (`:app`, `:core:*`, `:data:*`, `:feature:*`)                  | Feature modules **must not** depend on each other. Cross-feature navigation goes through contracts.   |
| R3 | Dynamic / reorderable bottom-nav tabs driven by user preferences                          | Tabs are not a compile-time constant; they are a user-editable list.                                  |
| R4 | Bottom nav + modal overlay sheets (driven by state, **not** navigated screens)            | Old project's 6 overlays (`VideoResolverSheet`, `ExtensionLinkingSheet`, etc.) must keep working.     |
| R5 | Single-Activity Compose, `minSdk 24`                                                      | No Fragment-based nav; no KMP requirement.                                                           |
| R6 | Deep linking (open anime/manga/novel from URL) — future, but must be possible             | Must not be a dead-end architecture.                                                                 |
| R7 | **Back stack reliability across Activity recreate / process death**                       | Old Voyager 1.0.1 lost the back stack on recreate — **this MUST NOT happen again.**                  |
| R8 | Type-safe routes (compile-time checked)                                                   | Prevent the runtime `IllegalArgumentException` class of nav crashes.                                 |
| R9 | Agent-friendly — a new AI agent can add a screen without understanding the whole nav tree | Local, self-contained, conventional pattern per feature.                                             |

These nine requirements are the evaluation rubric used below.

---

## Voyager: Pros & Cons

**Library:** `cafe.adriel.voyager:*` ([github.com/adrielcafe/voyager](https://github.com/adrielcafe/voyager)).

### Current version + maturity

| Tag                  | Date              | Notes                                                                                              |
|----------------------|-------------------|----------------------------------------------------------------------------------------------------|
| `1.0.0`              | 10 Dec 2023       | First stable, declared "API stability".                                                            |
| `1.0.1`              | (early 2024)      | What the **old ANIKUTA project used** — the source of the back-stack-recreate bug.                |
| `1.1.0-beta01`       | 21 May 2024       | First beta of the 1.1 line.                                                                        |
| `1.1.0-beta03`       | **8 Oct 2024**    | **Last 1.x release.** No `1.1.0` stable ever shipped.                                              |
| `2.0.0-alpha01` …    | 2025              | New versioning scheme tied to Compose-Multiplatform releases (e.g. `2.2.21-1.10.3`, 8 Jun 2026).   |

**Open maintenance-status issue #556** ("Voyager roadmap & long-term
maintenance", opened 22 Oct 2025) — author's reply on 1 Feb 2026:
> "Yes, the core is stable. Compose hasn't changed its core too and Voyager
> still compatible. Look at issues and you'll see a lot not related [to
> nav-internal problems]."

Translation: the project is in **slow maintenance mode**, not active
development. The 1.1 line never reached stable. The 2.x alphas are
incompatible-version-coupled to CMP releases and have no production track
record yet.

### R7 — Does the `rememberNavigator()` bug still exist?

- **Voyager 1.0.x** (what old ANIKUTA used): no `rememberNavigator()` Saver.
  Back stack **is lost on Activity recreate** — confirmed in the old project's
  `AnikutaRoot.kt` TODO and documented in `02-architecture.md` §10 item 8.
- **Voyager 1.1.0-beta03**: A `Saver` exists (per
  [discussion #4](https://github.com/adrielcafe/voyager/discussions/4):
  "Voyager uses `rememberSaveable` with a custom Saver"), but it has **never
  shipped in a stable release**. To use it today you must pin to a
  **beta from Oct 2024** — almost 2 years stale — or to an even-less-tested
  2.x alpha.
- **Voyager 2.x alphas**: unproven, coupled to specific CMP versions.

**Verdict on R7: not satisfiable with a stable Voyager release.** The bug class
is fixable in principle but requires betting on a beta/alpha.

### Other requirements

| Req | Voyager support                                                                                                                                       |
|-----|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1  | ✅ Works — `Screen` subclasses carry arbitrary args in their constructor. Mode-switch = push a new root `Screen`.                                       |
| R2  | ⚠️ Partial. Each feature module can declare its own `Screen` classes, but cross-feature navigation requires either a shared `:core:navigation` module exposing a sealed `Destination` type (what old ANIKUTA did — see the 623-line `Destinations.kt`) or an `AppController` indirection. There is no built-in "feature nav-graph" abstraction. |
| R3  | ✅ `TabNavigator` supports dynamic tab lists. Per-tab nested `Navigator`s are documented ([voyager.adriel.cafe/navigation/tab-navigation](https://voyager.adriel.cafe/navigation/tab-navigation)). |
| R4  | ✅ Excellent — this is Voyager's strongest fit. Modal sheets are just composables drawn in a `Box` over `Navigator { }`, driven by separate state (exactly the pattern old ANIKUTA used). |
| R5  | ✅ Single-Activity Compose, minSdk 21+.                                                                                                                |
| R6  | ⚠️ Weak. No first-class deep-link API. You must hand-roll parsing of `Intent.data` and map it to a `Screen` push (what old ANIKUTA does for OAuth callbacks — `MainActivity.onNewIntent`). |
| R8  | ⚠️ Partial. `Screen` subclasses are Kotlin objects/data classes, so **construction args** are type-checked at compile time. But there is no schema for routes — any `Screen` can be pushed at any time, and there is no compile-time guarantee that the nav graph is closed. |
| R9  | ✅ Good — adding a screen = add a `data class FooDestination(...) : Screen` + `@Composable override fun Content()`. Local and self-contained.          |

### Community + learning curve

- Friendly API, smallest learning curve of the three options.
- Strong KMP / CMP community presence; less strong on pure-Android.
- Documentation site is good but **changelog has been quiet since late 2024**.
- Stack Overflow / Reddit activity has dropped noticeably through 2025–2026 as
  attention shifted to Jetpack Navigation 3.

---

## Jetpack Compose Navigation: Pros & Cons

There are now **two** Google-backed Compose navigation libraries. Both are
evaluated under this heading because the project must pick one.

### Option A — "Nav2" (`androidx.navigation:navigation-compose`)

| Tag              | Date           | Notes                                                                                       |
|------------------|----------------|---------------------------------------------------------------------------------------------|
| `2.8.0` stable   | Aug 2024       | First stable with **type-safe routes** (kotlinx-serialization based).                       |
| `2.8.x` / `2.9.x`| through 2025   | Bug fixes; feature-frozen. Google has stated Nav2 "will gradually be deprecated" once Nav3 is adopted. |
| Type-safe routes | since 2.8.0-a08| `@Serializable object FooRoute` / `@Serializable data class BarRoute(val id: String)`.      |

### Option B — "Nav3" (`androidx.navigation3:*`) — **stable since 19 Nov 2025**

| Tag             | Date             | Notes                                                                                       |
|-----------------|------------------|---------------------------------------------------------------------------------------------|
| `1.0.0` stable  | **19 Nov 2025**  | ["Jetpack Navigation 3 is stable" — Android Developers Blog](https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html). Already in production at JetBrains (Kompose). |
| KMP support     | 7 Jul 2026       | Compose Multiplatform support added (not required by ANI-KUTA but signals active investment). |
| Recipes repo    | ongoing          | [github.com/android/nav3-recipes](https://github.com/android/nav3-recipes) — bottom-sheet, deep links, multi-module, etc. |

### R7 — back-stack reliability

- **Nav2**: back stack is saved via the usual `rememberSaveable` mechanism; the
  `NavController` is wired to the Activity's `SavedStateRegistry`. This **works**
  but has well-known edge cases around nested `NavHost`s.
- **Nav3**: back stack is **literally a `StateFlow<List<NavKey>>` that you own**.
  You persist it via `rememberSaveable` (or any `Saver` you choose). The Activity
  recreate scenario is **trivially correct** because the back stack is just state
  — there is no hidden framework magic to lose. Deep links compose into the same
  `List<NavKey>` via `DeepLinkMatcher.withBackStack`.

### Other requirements (Nav3)

| Req | Nav3 support                                                                                                                                                                                                       |
|-----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1  | ✅ Modes = top-level `NavKey` objects (`AnimeBrowse`, `MangaBrowse`, `NovelBrowse`). Switching modes = `navDisplay.backStack = listOf(newRoot)`. Type-safe, no string concatenation.                                  |
| R2  | ✅ **Official multi-module pattern** ([developer.android.com/guide/navigation/navigation-3/modularize](https://developer.android.com/guide/navigation/navigation-3/modularize)) — split each feature into `:feature:foo:api` (declares `@Serializable FooRoute` + extension fns on `NavDisplay`/`BackStack`) and `:feature:foo:impl` (the screen composable). The `:app` module wires all features together. Features don't see each other. |
| R3  | ✅ Bottom-nav tabs are an ordinary `List<NavKey>` driven by a preference Flow. Reordering is a one-line list edit. The official [Now in Android](https://github.com/android/nowinandroid) sample (migrating to Nav3) demonstrates this. |
| R4  | ✅ `BottomSheetSceneStrategy` + `DialogSceneStrategy` are first-class — see [Bottom Sheet Recipe](https://developer.android.com/guide/navigation/navigation-3/recipes/bottomsheet) (16 Mar 2026). Modal sheets can either be **first-class destinations** (cleaner than the old Voyager pattern) **or** remain separate composables driven by a `StateFlow<Overlay?>` in `:app` — both patterns are documented. |
| R5  | ✅ Single-Activity Compose, minSdk 21+.                                                                                                                                                                              |
| R6  | ✅ First-class. `DeepLinkMatcher` + `withBackStack` synth backstack for inbound links. Recipe in [nav3-recipes/SyntheticBackStack](https://github.com/android/nav3-recipes).                                          |
| R8  | ✅ Best-in-class. Routes are `@Serializable` `NavKey`s. Mismatched argument types are **compile errors**, not runtime `IllegalArgumentException`s. The `@Serializable` + `NavKey` contract makes nav state **type-safe and persistable** ([blog.devgenius.io, 25 Dec 2025](https://blog.devgenius.io/navigation-3-in-jetpack-compose-type-safety-and-the-architecture-of-scale-5e9c670bf05b)). |
| R9  | ✅ Adding a screen = (1) declare `@Serializable object FooRoute : NavKey` in `:feature:foo:api`, (2) write `@Composable FooScreen()` in `:feature:foo:impl`, (3) wire it in the `NavDisplay` declaration in `:app`. The pattern is uniform and discoverable. |

### Nav2 vs Nav3 within the "Jetpack Compose Navigation" umbrella

| Axis                          | Nav2 (2.9.x)                               | Nav3 (1.0.0+)                                                       |
|-------------------------------|--------------------------------------------|---------------------------------------------------------------------|
| Stable                        | Yes, mature                                | Yes, since Nov 2025                                                 |
| Google recommendation         | "Will gradually be deprecated"             | "Go ahead and use it in production"                                 |
| Type-safe routes              | `@Serializable` (since 2.8.0)              | `@Serializable NavKey` — same idea, cleaner                         |
| Back stack ownership          | `NavController` (opaque)                   | `List<NavKey>` you own — a `StateFlow`                              |
| Multi-module                  | Documented, well-trodden                   | Documented, designed-in (`api`/`impl` split)                        |
| Modal sheets as destinations  | Possible via accompanist / 3rd-party       | First-class `BottomSheetSceneStrategy`                              |
| Deep links                    | Built-in `<deepLink>`                      | Built-in `DeepLinkMatcher.withBackStack`                            |
| Learning resources            | Massive (4+ years of content)              | Growing fast; Google + community recipes                            |
| Risk                          | Will need migration to Nav3 eventually     | Newer, smaller (but Google + JetBrains + Livefront already onboard) |

For a **greenfield project starting in 2026**, Nav3 wins on every axis that
matters for this project. Nav2 would be the conservative choice only if the
team were migrating a large existing Nav2 codebase.

---

## What Animiru Uses

**Animiru** ([github.com/Quickdesh/Animiru](https://github.com/Quickdesh/Animiru))
is a fork of **Aniyomi** ([github.com/aniyomiorg/aniyomi](https://github.com/aniyomiorg/aniyomi)),
itself a fork of Mihon (formerly Tachiyomi). Aniyomi's `app/build.gradle.kts`
declares a dependency on `cafe.adriel.voyager:voyager-navigator` and the codebase
uses `Screen` subclasses throughout (the JetBrains `git_good` dataset even
contains a Voyager source path `cafe/adriel/voyager/navigator/internal/NavigatorBackHandler.kt`
from Aniyomi's tree). **Animiru inherits this — it uses Voyager.**

Implications:

- Animiru shares the **same Voyager 1.0.x back-stack-recreate bug** as old
  ANIKUTA. The old ANIKUTA project was built on top of the Aniyomi/Animiru
  lineage, which is why it hit the same bug.
- Sticking with Voyager would minimize porting effort for any Animiru-derived
  player/UI code we reuse. **However**, the rebuild is explicitly moving away
  from the old codebase (see `09-rebuild-notes.md`), and the player/extension
  layers are the main reusable parts — navigation is not.
- **The base app's nav choice is therefore informative but not binding.** The
  reusable parts (ExoPlayer/Media3 wiring, extension system, source API) are
  navigation-agnostic.

---

## Modular Navigation Pattern

Three viable patterns for letting feature modules contribute screens without
depending on each other. All three are evaluated; **Pattern B is recommended**.

### Pattern A — Central `Destinations` sealed class (what old ANIKUTA did)

```kotlin
// :app/navigation/Destinations.kt — 623 lines
sealed class Destination : cafe.adriel.voyager.core.Screen {
    data class AnimeDetail(val anilistId: Int) : Destination()
    data class Watch(val video: Video) : Destination()
    // ... 30+ entries
}
```

- ❌ Every feature must depend on `:app:navigation`, breaking layering.
- ❌ Becomes a god-file (the old one was 623 lines).
- ❌ Adding a screen = touching this central file (violates R9 for agents).

### Pattern B — `:feature:foo:api` + `:feature:foo:impl` split (Nav3 official)

```
:feature:anime-browse:api      — @Serializable AnimeBrowseRoute : NavKey
                                  + fun NavDisplay.registerAnimeBrowse()
:feature:anime-browse:impl     — @Composable AnimeBrowseScreen() + ScreenModel
:feature:anime-details:api     — @Serializable AnimeDetailRoute(id: ContentId) : NavKey
:feature:anime-details:impl    — @Composable AnimeDetailScreen()
...
:app                           — wires every feature's register*() into one NavDisplay
```

- ✅ Features see only their own `api` module + `:core:navigation-api`
  (which exposes `NavKey`, `NavDisplay`, the `BackStack` extension point).
- ✅ `:app` is the **only** module that depends on every feature — this is
  the existing `:app` orchestration role, no new layering violation.
- ✅ Adding a screen is **purely local** to the feature module pair → satisfies R9.
- ✅ Type-safe routes (R8) live in `:api`, so cross-feature navigation is
  compile-checked.
- ✅ Maps cleanly to Gradle's `api`/`impl` convention already used in the old
  project's `:core:provider-api` / `:data:*` split.

### Pattern C — Per-feature `NavGraph` (Nav2 idiomatic)

Each feature module exposes a `fun NavGraphBuilder.fooGraph(navController)` extension.
The `:app` module's `NavHost` calls each extension.

- ✅ Standard Nav2 pattern, well-documented.
- ❌ Less clean for type-safe routes across modules (route classes leak into a
  shared `:core:navigation` module or get duplicated).
- ❌ Doesn't compose as cleanly with dynamic tab reordering (R3) — Nav2's
  `NavigationBar` is straightforward, but per-tab `NavController` juggling is
  fiddly.

**Pattern B (Nav3 + api/impl split) is the recommended modular pattern** because
it pairs the cleanest module boundaries with the cleanest type-safety story and
the cleanest dynamic-tab story.

### Sketch of the content-type mode switch (R1) under Pattern B

```kotlin
// :core:navigation-api
sealed interface ContentMode { object Anime : ContentMode; object Manga : ContentMode; object Novel : ContentMode }

// :app/AppRoot.kt
@Composable
fun AppRoot() {
    val mode by modeFlow.collectAsStateWithLifecycle()  // from :core:preferences
    val tabs by tabsFlow(mode).collectAsStateWithLifecycle()  // user-reorderable
    var backStack by rememberSaveable(stateSaver = backStackSaver) {
        mutableStateOf(listOf<NavKey>(tabs.first().rootRoute))
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack = backStack.dropLast(1) },
        sceneStrategy = groupByContentMode(mode) + BottomSheetSceneStrategy(),
    ) {
        // Each feature registers its routes; :app is the only module that sees all features
        registerAnimeScreens()
        registerMangaScreens()
        registerNovelScreens()
        registerLibraryScreens()
        registerSettingsScreens()
    }

    if (showBottomNav) {
        DynamicBottomNavBar(
            tabs = tabs,
            onTabSelected = { tab -> backStack = listOf(tab.rootRoute) },
        )
    }

    AppOverlays(appController)   // modal sheets, same pattern as old ANIKUTA — NOT nav destinations
}
```

This satisfies R1 (modes), R3 (dynamic tabs), R4 (overlays), R7 (back stack is
just state, saved by `rememberSaveable`), R8 (every route is a `NavKey`), and
R9 (features register themselves).

---

## Alternatives Considered (brief)

### Decompose (`com.arkivanov.decompose`)

- **Pros**: Most architecturally rigorous of the third-party options. Lifecycle-aware
  components ("instances") survive process death naturally. Excellent KMP story.
- **Cons**: Heaviest setup of any option. The `ComponentContext` + router mental
  model is a steep learning curve for AI agents (R9 risk). Overkill for an
  Android-only app. Community praise is real but adoption is a fraction of
  Voyager's, let alone Google's libraries.
- **Verdict**: Not recommended. Nav3 gives you Decompose's state-ownership
  benefits with a much simpler API and Google backing.

### Appyx (`com.bumble.appyx`)

- **Pros**: Model-driven navigation — you define your own nav state machine.
  Excellent for non-standard UIs (cards, carousels, custom transitions).
- **Cons**: Niche. Largest learning curve. Smallest community. The "define your
  own nav model" power is wasted on a standard bottom-nav + stack app.
- **Verdict**: Not recommended. Would only make sense if the app had a
  genuinely novel navigation surface (it doesn't).

### compose-destinations (`raamcosta/compose-destinations`)

- KSP annotation processor on top of Nav2. Generates the routing boilerplate.
- Not a separate navigation library — it **wraps Nav2**. If we picked Nav2 this
  would be worth considering for boilerplate reduction. With Nav3 (which has
  no boilerplate to reduce) it is unnecessary.

---

## Recommendation

### **Use Jetpack Navigation 3 (`androidx.navigation3:*` 1.0.0+).**

Adopt Pattern B (`:feature:foo:api` + `:feature:foo:impl`) for modular
navigation, with a thin `:core:navigation-api` module that declares the `NavKey`
contract and any shared nav helpers.

### Reasoning, mapped to the rubric

| Req | Why Nav3 wins                                                                                                                                |
|-----|----------------------------------------------------------------------------------------------------------------------------------------------|
| R1  | Content-mode switching = replacing the root of a `List<NavKey>`. Type-safe `@Serializable` route objects carry the mode.                      |
| R2  | **Official** multi-module guide exists for Nav3 (the `api`/`impl` split). Features never depend on each other.                                |
| R3  | Tabs are an ordinary `List<NavKey>` driven by a preference `Flow`. Reordering is a list operation, no nav-graph recompilation.                |
| R4  | `BottomSheetSceneStrategy` makes modal sheets first-class **or** keeps them as state-driven overlays (we'll keep the old pattern for parity). |
| R5  | Single-Activity Compose, minSdk 21+.                                                                                                         |
| R6  | `DeepLinkMatcher.withBackStack` is first-class, recipe provided.                                                                             |
| R7  | **The back stack is `StateFlow<List<NavKey>>` saved via `rememberSaveable`.** The Voyager bug class is **structurally impossible.**            |
| R8  | `@Serializable NavKey` routes = compile-time-checked. No string concatenation, no runtime `IllegalArgumentException`.                          |
| R9  | Adding a screen = 3 local steps inside the feature module pair. No central registry to touch. Documented in the official modularization guide. |

### Why not Voyager

- The 1.0.x back-stack bug that bit old ANIKUTA is only fixed in a beta that is
  **two years stale** (1.1.0-beta03, Oct 2024). No 1.1.0 stable ever shipped.
- The 2.x alphas are coupled to specific Compose-Multiplatform versions and
  have no production track record.
- Author has confirmed the project is in slow-maintenance mode (issue #556).
- Sticking with Voyager would mean inheriting the same bug class the rebuild
  is explicitly trying to escape (per `02-architecture.md` §10 item 8).

### Why not Nav2

- Google has explicitly stated Nav2 "will gradually be deprecated" in favor of
  Nav3. Starting a greenfield project on Nav2 in 2026 means signing up for a
  future migration that Nav3 already eliminates.
- Nav3's state-owned back stack is a strictly better fit for the
  dynamic-tabs + mode-switching requirements (R1, R3) than Nav2's opaque
  `NavController`.

### Why not Decompose / Appyx

- Both are capable but add complexity the project doesn't need. Nav3 captures
  Decompose's best idea (state-owned back stack) in a Google-backed,
  Compose-native, agent-friendly API.

### Migration cost from Animiru's Voyager

- Negligible. The reusable parts of Animiru (player, extension system, source
  API) are navigation-agnostic. The Voyager `Screen` subclasses in Animiru's UI
  layer would not be ported — the rebuild rewrites UI screens in ANI-KUTA's own
  multi-content-type architecture anyway (per `09-rebuild-notes.md`).
- Estimated effort: zero direct migration; one new `:core:navigation-api`
  module (~150 lines) + a `:app/AppRoot.kt` (~100 lines) + per-feature
  `:api`/`:impl` split (one `@Serializable` route object per screen).

### Concrete first-step artifacts (for the next agent)

1. **`:core:navigation-api` module** — declares `interface NavKey` (re-export
   from `androidx.navigation3`), `ContentMode` sealed interface, and any shared
   `Saver`/`SceneStrategy` composition helpers.
2. **Convention plugin** `:build-logic:convention.android.feature` — auto-wires
   the `:api` + `:impl` split + Nav3 dependencies for every `:feature:*` module.
3. **`:app/AppRoot.kt`** — wires the bottom-nav `StateFlow` + `NavDisplay` +
   `AppOverlays` (port the old `AnikutaRoot.kt` pattern, swap Voyager → Nav3).
4. **One vertical slice** (`:feature:anime-browse:{api,impl}`) as the
   reference implementation that future agents copy.

---

## Sources

- Voyager: [github.com/adrielcafe/voyager](https://github.com/adrielcafe/voyager) · [voyager.adriel.cafe](https://voyager.adriel.cafe) · [Maven Central voyager-navigator](https://mvnrepository.com/artifact/cafe.adriel.voyager/voyager-navigator) · [issue #556 (maintenance status)](https://github.com/adrielcafe/voyager/issues/556) · [discussion #4 (Saver)](https://github.com/adrielcafe/voyager/discussions/4)
- Jetpack Navigation 3 (stable): ["Jetpack Navigation 3 is stable" — Android Developers Blog, 19 Nov 2025](https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html) · [androidx.navigation3 releases](https://developer.android.com/jetpack/androidx/releases/navigation3) · [Navigation 3 guide](https://developer.android.com/guide/navigation/navigation-3) · [Modularize navigation code](https://developer.android.com/guide/navigation/navigation-3/modularize) · [Bottom Sheet Recipe](https://developer.android.com/guide/navigation/navigation-3/recipes/bottomsheet) · [nav3-recipes](https://github.com/android/nav3-recipes) · [Migration guide Nav2 → Nav3](https://developer.android.com/guide/navigation/navigation-3/migration-guide)
- Nav2 type-safe routes: [Type safety in Compose Nav](https://developer.android.com/guide/navigation/design/type-safety) · [Multi-module best practices](https://developer.android.com/guide/navigation/integrations/multi-module)
- Nav3 analysis articles: [ProAndroidDev "Production-Ready Navigation 3"](https://proandroiddev.com/production-ready-navigation-3-in-jetpack-compose-0ff709d527e4) · [Livefront "Charting a Course from Nav2 to Nav3"](https://livefront.com/writing/charting-a-course-from-android-compose-navigation-2-to-navigation-3) · [Atomic Robot "Nav 3 for CMP"](https://atomicrobot.com/blog/navigation3-for-cmp)
- Animiru / Aniyomi: [github.com/Quickdesh/Animiru](https://github.com/Quickdesh/Animiru) · [aniyomi.org/forks/Animiru](https://aniyomi.org/forks/Animiru) · [github.com/aniyomiorg/aniyomi](https://github.com/aniyomiorg/aniyomi)
- Alternatives: [Decompose vs Voyager (mvpfactory.io, 1 Mar 2026)](https://mvpfactory.io/blog/compose-multiplatform-navigation-in-2026-decompose-vs-voyage) · [Appyx (bumble-tech.github.io/appyx)](https://bumble-tech.github.io/appyx) · [compose-destinations (github.com/raamcosta/compose-destinations)](https://github.com/raamcosta/compose-destinations)
