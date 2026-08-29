# 20 — Implementation Roadmap (THE phased CloudStream adoption roadmap)

> **Mission (B4-e)**: the phased implementation roadmap for adding CloudStream (CS3) as ANI-KUTA's
> second extension ecosystem — the "Cloud Screen". This doc sequences everything docs 16–19
> planned: pre-flight user decisions, the port/toolchain spike, foundation plumbing, the first
> user-visible slice, search/library, downloads/cache, and polish — plus dependencies, per-phase
> verification, sizing, the accumulated non-goals, and program milestones. **Nothing here has been
> built; this doc inherits every plan decision from docs 16–19 and only adds sequencing.**
>
> **Ground truth inputs**: docs 16 (integration architecture — normative for modules/registry/
> licensing/risk), 17 (data layer — normative for schema/keys/favorites), 18 (Cloud Screen UI —
> normative for IA/screens/phased UI rollout §10), 19 (playback/downloads — normative for resolve
> pipeline/picker/downloads/verification plan §10); constraints skimmed from docs 02 (`.cs3`
> format), 04 (repos), 10 (categories/NSFW), 12 (provider quality spectrum + fixtures), 14 (our
> extension architecture), 15 (our data layer). Workflow norms from `AGENT-CONTEXT/SESSION.md` +
> `workflow.md` (task loop, CI-verify rule D-281/D-156, branch discipline) +
> `AGENT-CONTEXT/knowledge/emulator-testing.md` (`EMU`). Fresh forensics performed for this doc:
> spike-candidate binary checks over `research/phisher-builds/` (§2.3, marked `[verified]`).
>
> **Markers**: **[plan]** = this doc's own sequencing/estimates (not inherited) ·
> **[recommendation]** = chosen option among alternatives (inherited unless marked) ·
> **[open-question]** = needs the user · `[verified]`/`[inferred]` for fresh facts.

---

## 0. Executive summary — the program on one page

| Phase | Name | What it proves | Effort `[plan]` |
|---|---|---|---|
| — | **Pre-flight gates** (§1) | the user's blocking decisions (GPL first) | user time, zero code |
| 0 | **Spike** (§2) | the vendored CS3 library compiles under OUR toolchain and ONE real `.cs3` loads + resolves | M (~1 week; doc 16 §1.6) |
| 1 | **Foundation** (§3) | zero-UI plumbing: modules, loader/installer/repo client, provider-api extension, registry, doc 17 §9 schema, crude extensions UI | L (8–12 sessions) |
| 2 | **Cloud Screen v0** (§4) | first user-visible slice: Cloud tab → one provider browse → details → link picker → MPV playback with headers | L (8–12 sessions) |
| 3 | **Search + Library + Favorites** (§5) | cloud search fan-out; favorites/progress/resume/history for cloud content | M (5–8 sessions) |
| 4 | **Downloads + cache** (§6) | cloud downloads through our orchestrator, CS3 re-resolve, ecosystem-keyed playback cache, subtitle downloads | M (5–8 sessions) |
| 5 | **Polish + parity** (§7) | extension-management parity, filters/NSFW, settings screen, subscriptions, dashboard/docs | M (5–10 sessions) |

**Critical path** (§8): `G1 GPL → G2 vendoring → Phase 0 → Phase 1 → Phase 2 → {Phase 3, Phase 4}
→ Phase 5 → v1`. **v1 = Phases 0–5 complete** with doc 19 §10.5's two-tier verification signed
off (§12).

**Phase-name mapping note** `[plan]`: doc 18 §10 defines UI-only phases 0–4. This roadmap's
engineering phases map onto them: doc 18 UI Phase 0 = our Phase 1; UI Phase 1 = our Phase 2;
UI Phases 2+3 = our Phase 3; UI Phase 4 = our Phase 5. Doc 16 §5.3's registry-refactor steps:
steps 1–3 land in our Phase 1, step 4 (Cloud Screen as first registry consumer) + step 5
(Details dispatch) in our Phase 2, step 6 (unified search) is deferred/possibly-never (doc 16
§5.3 explicit). This split is `[plan]` sequencing of doc 16's "[recommendation] steps 1–4 land
with the first CS3 PR" — same content, finer-grained shipping.

---

## 1. Pre-flight gates (before ANY code)

These are the blocking decisions only the user can make, consolidated from the open questions of
docs 16–19 (their §11s). **No Phase 0 work starts until G1 is answered** (doc 16 §11-0: "without
yes (or an explicit informed alternative), B5 implementation should not start").

### 1.1 The decision checklist

| # | Decision | Options | `[recommendation]` | Blocks | Source |
|---|---|---|---|---|---|
| **G1** | **THE GPL-3.0 decision** — CS3's library is GPL-3.0 (single root LICENSE, no separate library LICENSE `[verified absent]`, doc 16 §1.5); ANI-KUTA has **no LICENSE at all**; any route that ships CS3 library code (artifact or vendored) distributes GPL code combined with ours | (i) relicense ANI-KUTA GPL-3.0; (ii) stay proprietary → halt/reshape the program; (iii) take legal advice on the plugin-host question (option (c) re-implementation is legally grey, doc 16 §9.4) | **(i) relicense** — clean and cheap while the project is personal, consistent with the FOSS ecosystem we're joining (doc 16 §9.6) | **everything** (all phases) | doc 16 §9, §10-R1, §11-0 |
| **G2** | **Vendoring approval** — add `:external:cloudstream3` (~1.6 MB GPL source, 174 Kotlin files, pinned @ efc1915) to the repo? | vendor (option b) / JitPack artifact (needs app-wide Kotlin 2.4 bump — §1.3 trap) / re-implement (rejected, doc 16 §1.6) | **vendor** (doc 16 §1.7: only option avoiding the Kotlin-metadata trap with reproducible classloader contract; explicit GPL provenance in-tree) | Phase 0 onward | doc 16 §1.6–§1.7, §11-2 |
| **G3** | **Separate vs unified IA** — separate Cloud Screen (own tab, own search) or unified into anime flows? | A. 5th bottom-nav "Cloud" tab (dynamic) / B. Browse section / C. More-screen entry | **A, dynamic** (appears only when ≥1 CS3 provider installed+enabled; zero blast radius on anime flows; fallback kept cheap — every Cloud composable is nav-agnostic) | Phase 2 shape | doc 16 §5.4, §11-1; doc 18 §1, §11 Q1–Q3 |
| **G4** | **NSFW policy** — single master toggle (default OFF)? Suppress NSFW watch-progress writes like CS3 does? Badge-only inside mixed providers or filter? | one merged switch (default OFF) / CS3's two-switch model (the documented anti-pattern) | **one master switch, default OFF**; badge-only for mixed-provider items; keep the "don't save NSFW progress" player rule | Phase 2 (picker gating), Phase 5 (settings) | doc 10 §8.5; doc 16 §11-4; doc 17 §11-12; doc 18 §2.2 #8, §11 Q5 |
| **G5** | **Default repos** — ship zero CS3 repos or pre-seed one? | zero defaults / pre-seed `recloudstream/extensions` / pre-seed a community repo | **zero defaults** — matches our D-043 aniyomi posture AND CS3's own empty `PREBUILT_REPOSITORIES` (doc 04 §8); keeps content-liability posture symmetric (doc 16 §9.5) | Phase 1 (repo client ships empty) | doc 16 §6, §11-3; doc 18 §7.3, §11 Q6 |
| **G6** | **Dev-data wipe confirmation** — doc 17 §9 re-keys downloads/cache/notifications/content axes destructively (one-time uninstall→reinstall) | accept / defer schema pass | **accept** — CORE_RULES §30's default for debug builds is yes; the wipe happens once, in Phase 1 | Phase 1 (schema pass) | doc 17 §9, §11-3; CORE_RULES §30 |

### 1.2 Secondary gates (fold into the same decision session — each has a recommendation, none reshape the program)

| # | Decision | `[recommendation]` | Source |
|---|---|---|---|
| G7 | jsoup version strategy (CS3 wants 1.22.1; we pin 1.19.1 for aniyomi-ext binary compat) | resolve-and-regress: bump to 1.22.1 in the Phase 0 spike, run an aniyomi-extension regression pass; if broken, force 1.19.1 and verify the vendored library tolerates it (Phase 0 exit criterion) | doc 16 §1.2, §10-R3, §11-5 |
| G8 | gson shim for the 13/80 plugins | **ship it** (one dependency, 16% of real plugins) | doc 16 §1.4, §11-7 |
| G9 | Trust model: repo-add = consent + sha256, no per-plugin trust gate | **confirm CS3-style** (nothing to verify a plugin hash against — `.cs3` has no signature; doc 02 §1.1) | doc 16 §3.1, §11-8 |
| G10 | Plugin settings UI in v1 | **skip** — 58/58 census providers expose none; gear hidden (hostcompat `DataStore` shim still ships so the 7/80 DataStore users keep browsing) | doc 16 §7, §11-6; doc 11 §6; doc 18 §7.4, §11 Q10 |
| G11 | minSdk guard + core-library desugaring (NewPipeExtractor needs NIO desugaring app-wide) | confirm we never intend minSdk < 24 and accept desugaring | doc 16 §1.2, §11-10 |
| G12 | Library placement for cloud favorites | auto-created **"Cloud" category** as default landing (user can move anywhere) | doc 17 §7, §11-2; doc 18 §6, §11 Q9 |
| G13 | Light vs full v1 — is Phase 3 (library) mandatory for v1 or can it trail Phase 2? | this roadmap treats **Phase 3 as in-v1** (favorites are core to how the user uses the app — but doc 18 §11 Q4 asks the user explicitly) | doc 18 §11 Q4 |
| G14 | Branch: the `streaming/CLOUDSTREAM` branch exists on `main` awaiting instructions (SESSION.md) — Phase 0+ work lands there (or a `spike/*` scratch branch for Phase 0) once the user green-lights | ask at gate time; do not start until instructed (SESSION.md explicit) | SESSION.md; workflow.md branch discipline |

**Output of this gate session** `[plan]`: a short decision record appended to
`AGENT-CONTEXT/memory/decisions.md` (one D-NNN per gate) — the workflow's "ask with a
recommendation" pattern (workflow.md "When to Ask the User").

---

## 2. Phase 0 — Spike (risk retirement)

Purpose: retire doc 16 §10's **R2 (toolchain drift)** and **R3 (transitive collisions)** — the two
H×H/M×H risks that can reshape the whole program — *before* the real build-out. Doc 16 §12 lists
"compile-ability of the library source under Kotlin 2.2.0/AGP 8.9.1" as **the port spike's first
task**; §1.6 sizes the port at "~1 week incl. build adaptation + smoke tests".

### 2.1 Scope `[plan]` (sequencing inherited from doc 16 §1.6–§1.7, §2.1)

1. **Vendor the library into a scratch branch** (G14: not `main`; ideally a throwaway
   `spike/cs3-port` branch so failure costs nothing): copy `library/` (174 Kotlin files,
   ~1.6 MB source, doc 16 §1.6) into `:external:cloudstream3` as a KMP module with
   `commonMain` + `jvmCommonMain` + `androidMain` only (drop `webMain` — unwired upstream,
   doc 01 §2.2), with PROVENANCE.md + LICENSE (doc 16 §2.1). Build adaptation per doc 16 §1.6:
   retarget compileSdk 37→36, build under AGP 8.9.1 via the
   `com.android.kotlin.multiplatform.library` plugin, `jvmTarget=1.8`, enable
   `coreLibraryDesugaring(libs.desugar.jdk.libs.nio)` (NewPipeExtractor requirement, doc 16 §1.2).
2. **Compile with our Kotlin 2.2.0** — the spike's headline question (library is built at
   Kotlin 2.4.0 upstream; metadata is the trap, doc 16 §1.3). Fall back per doc 16 §1.7 if build
   adaptation stalls: the JitPack artifact route on a throwaway branch ("two-day spike" with a
   temporary Kotlin bump) just to get runtime answers while vendoring problems are debugged.
3. **Load ONE real `.cs3`** through a minimal parent-first `PathClassLoader` harness (doc 16 §3.3
   sketch) and call **`search(query)` → `load(url)` → `loadLinks(data, false, subCb, linkCb)`**
   (the three load-bearing MainAPI calls — doc 03 §2.5/§2.8, doc 08 §1).
4. **Verify no collisions**: jsoup 1.19.1-pinned vs library's 1.22.1 want (G7/R3 — run Gradle
   dependency resolution + our aniyomi-extension smoke suite), NiceHttp↔OkHttp alpha resolution
   (doc 16 §1.2's `[open-question]` — unanswerable without a real build), coroutines/serialization
   max-wins regression (prefs/cache JSON round-trip), and measure APK size + method-count delta
   (R10: Rhino + Jackson + NewPipeExtractor + ksoup + ktor + ~97 extractors, both apps ship
   unminified — doc 16 §10-R10).

### 2.2 Which `.cs3` to load — `[recommendation]` AllMovieLandProvider.cs3

From `research/phisher-builds/` (80 compiled plugins — the only compiled `.cs3` corpus in our
workspace; CakesTwix-ext/storm-ext are source-only). Fresh forensics performed for this doc
`[verified]`:

| Candidate | Why / why not |
|---|---|
| **`AllMovieLandProvider.cs3`** ✅ **pick** | the single best-documented binary in the corpus: doc 02 §1.2 verified it end-to-end — manifest `{"pluginClassName":"com.phisher98.AllMovieLandProviderPlugin","name":"AllMovieLandProvider","version":23,"requiresResources":false}` `[re-verified fresh]`, 57,618 bytes, sha256 `938e5d6b…c65c9` **matching `plugins.json.fileHash` exactly** — so the Phase-1 sha256-verify path is testable against a known-good hash from day one. `status: 1` (stable), `tvTypes [Movie, TvSeries, Cartoon]`, lang `hi` `[verified fresh from phisher-builds/plugins.json]`. **No app-module class dependencies** — fresh binary census (doc 16 §1.4's method: `unzip -p <f> classes.dex | grep -a <ref>`): `CommonActivity` 0, gson 0, `utils/DataStore` 0 → loads on a library-only host, zero hostcompat needed for the spike `[verified fresh]`. |
| `Animeav1.cs3` / `Latanime.cs3` (alternates) | the only doc 12 census providers whose compiled builds sit in phisher-builds (storm-ext's AnimeAV1/LatAnime, both `status: 1` `[verified fresh]`) — usable as second loads; both are 0-`mainPage`-row providers (doc 12 §1.1) so they exercise less than AllMovieLand. |
| DoramyWorld / Uakino / AnimeJl (doc 19 §10.3 fixtures) | the Phase-2+ E2E fixture trio — but they live only as **source** in our workspace (`CakesTwix-ext/`, `storm-ext/`); their compiled `.cs3` must be fetched from those repos' build branches or built during Phase 2 prep (flagged in §4.5) `[verified: no .cs3 under either clone]`. |

Note honestly: doc 12's deep-dives are source-repo studies; the phisher corpus was binary-censused
by doc 16 §1.4, not deep-dived. AllMovieLand is therefore "stable" in the *verified-integrity*
sense (hash chain + status + no app-module deps), not the *code-read* sense — for a compile/load
smoke that's the right trade.

### 2.3 Exit criteria checklist

- [ ] `:external:cloudstream3` compiles under **our** Kotlin 2.2.0 / AGP 8.9.1, in CI (CI is the
      compiler of record — workflow.md step 7, D-281)
- [ ] resolved-dependency report recorded: jsoup / OkHttp / coroutines / serialization / Jackson
      final versions + the aniyomi-extension smoke suite result (G7 verdict)
- [ ] AllMovieLandProvider.cs3 loads via parent-first `PathClassLoader`; manifest parsed;
      `registerMainAPI` populates the library's `APIHolder` (doc 16 §3.3 steps 2–7)
- [ ] `search()` returns ≥1 `SearchResponse`; `load()` returns a `LoadResponse`; `loadLinks`
      emits ≥1 `ExtractorLink` **or** a documented real reason (sandbox IP may fail the target
      host — probe reachability first with `getent hosts` per `EMU` 4.5; the *call machinery*
      working is the criterion, link bytes are not)
- [ ] APK size + DEX method-count delta measured and reported to the user (R10)
- [ ] **GO / NO-GO memo** written into the worklog + decisions.md: vendoring stands, or fallback
      route (artifact + Kotlin bump) or program reshape is proposed

### 2.4 Timebox `[plan]`

**3–5 working sessions (~1 week)**, consistent with doc 16 §1.6's port estimate. Hard stop at 2×
the timebox → escalate to the user with the fallback options rather than grinding (doc 16 §1.7
keeps the artifact route as the named fallback).

---

## 3. Phase 1 — Foundation (zero-UI plumbing)

Goal: everything doc 16 planned with **no Cloud Screen yet** — the extension system exists, a
plugin can be installed from a real CS3 repo and its providers are visible in the registry; the
schema is CS3-ready. "No UI beyond debug assertions" + the *crude* extensions-screen bootstrap
(doc 18 §10 UI Phase 0: "Extensions screen CS3 toggle + repo add + install/errored rows — the
crude version (no filters, no badges beyond version)").

### 3.1 Work packages (all inherited; grouping is `[plan]`)

**WP 1.1 — Modules** (doc 16 §2): land `:external:cloudstream3` (from the spike branch, now with
PROVENANCE/LICENSE) and `:data:cloudstream` mirroring `:data:extension`'s shape — `loader/`
(`CloudStreamPluginLoader`), `installer/` (`CloudStreamPluginInstaller`), `repo/`
(`CloudStreamRepoApi` + `CloudStreamRepoRepository`), `manager/` (`CloudStreamPluginManager` +
`CloudStreamRuntime`), `provider/` (`CloudStreamExtensionProvider` + `Cs3Mappers`), `hostcompat/`
(`utils.DataStore`, `CommonActivity`, `CloudStreamApp` shims + gson dep — sized by the §1.4
census). Invariants: `:external:cloudstream3` depends on nothing of ours; `:data:cloudstream` is
the only `com.lagradost.*` importer; provider-api stays third-party-free (doc 16 §2.2).

**WP 1.2 — provider-api extension** (doc 16 §4.2/§4.3): additive `SourceVideo` fields
(label/source/referer/headers/type/extractorData/subtitles/audio + `qualityHeight` sort key per
doc 19 §1.1), `SourceEpisode` season fields, `SourceSection`/`SourcePage` + default methods,
`install(handle)` rename — binary-compatible, aniyomi facade optionally backfills
headers/subtitles (fixes its own gap, doc 16 §4.2).

**WP 1.3 — Registry + Koin refactor steps 1–3** (doc 16 §5.3, blast-radius order): (1) unqualified
`single<VideoExtensionProvider>` → `named("aniyomi")` — safe, **zero consumers today** (doc 14
§6.5); add `named("cloudstream")` + `providerRegistryModule` (`ExtensionProviderRegistry`,
doc 16 §5.2). (2) the provider-api additions above. (3) `ExtensionsSettingsScreen` gains the
ecosystem SegmentedToggle + CS3 rows (install/update/uninstall/errored — crude; the full §7
parity is Phase 5). Step 4 (Cloud Screen, the seam's first load-bearing consumer) opens Phase 2.

**WP 1.4 — `.cs3` pipeline** (doc 16 §3): install = download stream → sha256 vs
`plugins.json.fileHash` → `filesDir/Extensions/<repoSalt>/<nameSalt>.cs3` → `setReadOnly()` →
load (parent-first `PathClassLoader`, manifest-as-resource, entry-class-by-name, two-base-class
lifecycle dispatch); state machine with **our additions** — per-plugin `Errored` with real reason
(D-295/D-296, NOT CS3's toast-and-forget), `REGISTERED ⇄ DISABLED` enable flag; update =
integer version compare + `status==0` remote kill-switch; safe-mode analog
(`cs3_last_error` marker + crash-loop skip banner — doc 16 §3.5). Duplicate providers get
suffixed `sourceKey`s, never silently dropped (doc 16 §3.4).

**WP 1.5 — CS3 repo management** (doc 16 §6; doc 04): add repo.json URL → fetch repo.json →
follow `pluginLists[]` → parse plugins.json → require ≥1 parseable entry + **"this repository can
run arbitrary code in-app" consent dialog** → persisted in separate prefs
(`anikuta_cs3_repos`) under one "Repositories" settings surface (segmented toggle, doc 18 §7.3).
Zero default repos (G5). Update checks piggyback the D-301 30-min entry throttle (doc 16 §6).

**WP 1.6 — Data-layer schema pass** (doc 17 §9 — **lands here, once, destructively**):
`content_source_link` new table (P1), `EpisodeKeys` canonical keys + `data_cache_episode`
season-unique (P2), polymorphic `ResolveContext` (P3), `ext_poster_headers` + `ExtensionExtras`
CS3 fields (P4), the `source_id INTEGER → source_key TEXT` sweep (P5), `cs3_subscription_state`
table (created now, polled in Phase 5), `notification_sent` PK re-key, driver `onOpen` guards,
and the **§24/§25 obligations**: `DOCUMENTATION/database/` entries, README changelog,
DATABASE.json auto-covers new tables, **`lib/schema.ts` regeneration is a blocking subtask of the
first B4 implementation PR** (doc 17 §9.3) + the recommended 16KB `renderCell` bump. Dev data
wipes once here (G6) — not again in later phases.

### 3.2 Exit criteria

- One `.cs3` **installs from a real repo URL** (spike fixture: the phisher repo
  `repo.json → plugins.json` chain `[verified present in research/phisher-builds]`), passes
  sha256 verify (corrupted file → visible error), loads, and **its providers register** —
  visible via `ExtensionProviderRegistry.observeAllSources()` with
  `sourceKey = "cloudstream:<name>"` (debug assertion only; doc 18 §10 UI Phase 0 exit: "one
  .cs3 installs, loads, lists in the registry; errored rows show real reasons")
- Errored plugin row shows the real exception message + Retry/Uninstall (D-295/D-296)
- Aniyomi regression: existing extension install/load/trust flows unchanged (step-1 rename is
  zero-consumer — doc 16 §5.3)
- Schema pass merged; schema.ts matches `.sq` files; dashboard DB pages open on the emulator
- CI green on the feature branch

### 3.3 Verification

CI (build + unit tests for installer hash/mgr state machine/mappers), emulator per doc 19 §10.2
item 1 (install → provider listed; `Anikuta:*` logcat tags), device-feedback ask: none yet beyond
a smoke install if the user wants an early look. Rollback: feature-branch revert; vendored module
removable from `settings.gradle.kts`; schema revert = dev wipe (accepted in G6).

---

## 4. Phase 2 — Cloud Screen v0 (browse + details + watch)

The first user-visible slice (doc 18 §10 UI Phase 1) — the program's demo moment. Scope per doc 18
§2/§4/§5 + doc 19 §2–§5:

### 4.1 Work packages

**WP 2.1 — Cloud tab (dynamic 5th pill)** (doc 18 §1.2): `navItems`/`rootTabKeys`/`startTab`
sanitize (D-282 set)/`allowedUpdateSheetKeys` derived over registry state; tab appears only when
≥1 CS3 provider is installed+enabled; `:feature:cloud-screen` module depending on
`:core:provider-api` ONLY (doc 16 §2.2/§5.3 step 4 — the seam's first load-bearing consumer).
Pre-check flagged by doc 18 §12: 5-pill fit on 360dp devices (device spike before merge).

**WP 2.2 — One-provider browse** (doc 18 §2): provider picker sheet (our
`ExtensionSourcePickerSheet` pattern + in-sheet TvType chips), named `mainPage` section rows with
**lazy per-row loading** (not CS3's fetch-all), per-row `hasNext` pagination with URL-dedupe +
3-consecutive-empty-page cap (hasNext-liar containment, doc 12 §10), per-row inline errors,
see-all grids, `sequentialMainPage` honored, NSFW provider gating (G4), onboarding empty state.

**WP 2.3 — Cloud details variant** (doc 18 §4): shared layout language (banner/genres/synopsis/
`EpisodeRow`/D-308 season chips/shared-element cover morph via new `cloudCoverKey` namespace —
the D-328 rule); **movie** (single Play CTA) + **series** (season selector, verbatim episode
names) variants + the sparse-provider floor (name+poster+type — doc 12 §10); no AniList
machinery; provider meta row; `posterHeaders` threaded into every Coil request (doc 07 §3.3).

**WP 2.4 — Resolve flow + player handoff** (doc 19 §2–§5): `loadLinks` → streaming
`Flow<SourceVideo>` (callbackFlow, dedup-by-URL, `isInvalidData` guard, timeout clamp 5 s–8 min
default 120 s, cancellation keeps arrived links); `CloudLinkPickerSheet` (accordion, rows stream
in, skip-loading, "N links hidden" footer, subtitle preview); `WatchKey` additive fields
(`sourceKey`, `mpvHeaders` via the `toMpvHeaders()` fold rule, 3-field subtitle lines with
per-track headers, canonical `episodeKey`, `linkSource`); **MPV unchanged** except the
`pendingSeekPosition` position-preserving switch fix (doc 19 §2.3 — benefits both ecosystems;
ship-order is doc 19 §11 Q2); the **20-min saturated link cache** (doc 19 §3.5);
`WebViewResolverHost` (single headless WebView, LruCache(2), doc 19 §3.2 #4);
`CloudStreamVerifier` playback site (doc 19 §4); the MPV header **comma-escaping fix** (doc 19
§2.1b — pre-existing bug class, must land before CS3 playback); episode-switch dispatch via the
registry (doc 16 §5.3 step 5's CS3 branch).

### 4.2 Exit criteria (E2E, two-tier per doc 19 §10)

- **Emulator** (doc 19 §10.2 items 2–4): install plugin from a real repo → Cloud tab appears →
  browse renders section rows → details (movie + series) → episode click → **picker streams**
  (running link count grows across two UI dumps 15–30 s apart) → timeout/soft-failure path shows
  real reason, no crash. Structural note: the x86_64 emulator APK ships no libmpv (arm-only
  `abiFilters`, doc 19 §10.1) — emulator asserts **up to the handoff**, never MPV itself.
- **Device** (doc 19 §10.4, the user's checklist): `loadfile` with folded headers plays a
  Referer-required host; comma-escaping round-trip; `sub-add` per CS3 subtitle (visible track);
  quality/mirror switch preserves position; 20-min cache re-open is instant.
- Fixture: **Uakino** (doc 18 §10 Phase 1's named fixture — 6 rows, movie+series, dual-path
  loadLinks, the census's only `subtitleCallback` user, doc 12 §2) so "subtitles visible" is a
  real assertion.
- CI green; `:feature:cloud-screen` has no dependency on aniyomi modules (invariant asserted in
  the module graph).

### 4.3 Fixture prep note `[plan]`

Uakino/DoramyWorld/AnimeJl exist only as source in our workspace (§2.2) — Phase 2 prep includes
sourcing their compiled `.cs3` (fetch from the CakesTwix/storm-ext build branches or compile from
the research clones; the CakesTwix remote is
`github.com/CakesTwix/cloudstream-extensions-uk` `[verified from the clone's git remote]`, exact
repo.json URL to confirm at prep time). The phisher repo remains the install-flow fixture.

Rollback: the Cloud Screen is a new module — deleting it (or the dynamic tab collapsing when no
providers are enabled) removes the feature; anime flows untouched (doc 18 §1.2 rationale).

---

## 5. Phase 3 — Search + Library + Favorites

Covers doc 18 §10 UI Phases 2+3. Backend: `fetchContentListPaged` search path (doc 16 §4.3) +
doc 17 §7 favorites (schema already landed in WP 1.6).

**WP 3.1 — Cloud search** (doc 18 §3): fan-out over all enabled providers with **live
per-provider sections** (advanced_search model, doc 06 §3.2–§3.3), D-305 request-generation
staleness guard, per-provider failure isolation + D-209 Cloudflare "Open in WebView" rows,
provider-subset picker + TvType chips with empty-set fallback, badges (quality/score/year/DUB-SUB/
18+/type) on the `ExtensionResultCard` pattern, **no cross-provider dedup** (provider badge
instead), anime search stays aniyomi-only (doc 16 §5.3 step 6 deferred).

**WP 3.2 — Favorites + progress** (doc 17 §7; doc 18 §6): details ☆ writes `main_entry` +
`content_source_link` via `resolveOrCreateForCloudStream`; auto-created **"Cloud" category**
(G12); season-qualified progress display ("S02E05 · 14/38"; movie watched-check); Track/schedule
UI hidden (`hasAnilistLink` guards, doc 17 §5.4).

**WP 3.3 — Resume + history**: continue-watching rows + History entries with the provider badge,
resuming through the 20-min cache or fresh `loadLinks` (doc 18 §6.2–6.3).

**Exit criteria**: a drama favorited → partially watched → app restart → resume row returns to
the same episode at position; search across ≥3 providers with live incremental fill and one
deliberately-failing provider section showing its own retry row (doc 18 §10 UI Phase 2 exit).
Emulator-verifiable end-to-end (no playback needed for the library assertions; one device pass
for a resume-into-playback check). Rollback: hide the Cloud category + details bookmark action;
data (main_entry/library rows) is harmless if stranded (FK-clean).

---

## 6. Phase 4 — Downloads + cache

Doc 19 §6–§7 + doc 17 §8 (schema landed in WP 1.6; this phase is runtime):

- **WP 4.1 — Orchestrator generalization** (doc 19 §6.2): `enqueueDownload`/`enqueueSpecific`
  behind the provider seam (aniyomi impl resolves via `VideoResolver`; CS3 impl via
  `fetchVideoList` + the §1.3 tier mapping — same mapping the picker uses);
  `resolveContext = CloudStreamResolveContext` **always** for CS3 (short-TTL URLs re-resolve
  exactly like proxy churn); `buildRequest` deltas (headers string, source_key).
- **WP 4.2 — ReResolver CS3 branch** (doc 17 §4.3; doc 19 §6.4): on IOException/403 →
  `loadLinks(contentUrl | episodeData)` → tier-match `(linkLabel, quality)` → same `linkSource`
  + nearest quality → visible ERROR; 1-re-resolve cap (D-149-fix pattern); `updateDownloadVideoUrl`.
- **WP 4.3 — Playback cache** (doc 19 §7): ecosystem-qualified identity
  (`sha256(mainId | canonicalEpisodeKey | sourceKey | serverKey)`, doc 17 §4.5);
  **cache-origin 403 → re-resolve under the same cacheKey** (doc 19 §7.2 step 3 — the single
  most valuable resilience add); encrypted-HLS no-cache marking (doc 19 §7.4 recommended v1).
- **WP 4.4 — Subtitle downloads + verifier download site** (doc 19 §5.5, §4.2):
  `DownloadTrack` headers per sub-file, `"${title} - S02E05.<lang>.<i>.<ext>"` naming,
  `CloudStreamVerifier.startForDownload` hooks in `DownloadService`.
- **WP 4.5 — HLS variant-selection patch** (doc 19 §6.3): prefer highest-bandwidth variant with
  no `AUDIO` group (~20 lines); HLS resume stays sidecar-only (doc 19 §6.3 answers doc 17 OQ#9:
  no `extraInfo` column needed).

**Exit criteria** (doc 19 §10.2 item 5 + §10.4): emulator — picker→download enqueues a
`download_queue` row with `source_key = "cloudstream:<name>"`, canonical episode key, polymorphic
`resolve_context` JSON, QUEUED→DOWNLOADING→ERROR transitions visible (state machine is the test,
not bytes); device — M3U8 download grows an honest `.ts` and plays offline via `fd://`; kill-link
ladder: TTL-expired URL → step-3 re-resolve → playback resumes at same cacheKey; verifier logs
start/cancel at the right lifecycle edges. Rollback: disable the cloud enqueue entry points;
queue rows strand harmlessly (visible + deletable).

---

## 7. Phase 5 — Polish + parity

Doc 18 §10 UI Phase 4 + the deferred surfaces:

- **WP 5.1 — Extensions management full parity** (doc 18 §7): row data from `plugins.json`
  (`iconUrl %size%`, tvTypes chips, language flag, status Slow/Beta badges, version/fileSize);
  D-301-style update pills + batch "Check updates" on the 30-min throttle; `status==0`
  kill-switch row states; **per-provider enable/disable** (our addition); NSFW rows hidden under
  the master toggle.
- **WP 5.2 — Settings "Cloud sources" screen** (doc 18 §8): one merged NSFW master switch
  (default OFF), provider languages (default All), preferred content types (default
  all-minus-NSFW), show-cast toggle; persisted chip/filter selections (doc 10 §8.6).
- **WP 5.3 — Category/language filters** (doc 10 §8 model, doc 18 §2.2 #1): TvType chip grouping
  map in browse/search, `filterProviderByPreferredMedia` shape, dirty-lang tolerance.
- **WP 5.4 — NSFW gating** (G4 verdict): extension-store visibility, provider pickers,
  browse/search results, "N NSFW providers hidden" lock-rows, 18+ item badges.
- **WP 5.5 — Subscriptions/notifications** `[plan — scoped, defer-able]`: the
  `cs3_subscription_state` 6-hour poll (CS3's model, doc 13 §6.2; table landed in WP 1.6) feeding
  the shared `episode_update` feed + "New" badges; **recommendation: ship the poll + feed in v1
  only if the user wants cloud-title update notifications; otherwise defer to v1.x** (0/58
  census providers send `nextAiring`; doc 17 §5.4 keeps schedule AniList-only). Doc 13 is the
  reference; this is the one Phase-5 item with a real defer option.
- **WP 5.6 — Richness + onboarding + safe-mode** (doc 18 §4.2/§10 Phase 4): actors row (images
  gate), nextAiring countdown, recommendations grid with provider filter, onboarding empty
  states, safe-mode crash-loop banner (doc 16 §3.5).
- **WP 5.7 — Dashboards/docs debt** (doc 15 §9; doc 17 §9.3): schema.ts already regenerated in
  Phase 1 — here it's the *verification* pass (drift check against shipped `.sq`), DATABASE.json
  16KB cell bump if not already landed, `/database-review`-style CS3-era section, and the
  optional schema.ts generator script (doc 17 §11-8 — ~1 day, kills the drift class).

**Plugin settings hosting** — per doc 16 §7's decision (G10): **stays hidden in v1** (58/58
providers have none); the hybrid Fragment host (`FragmentContainerView`, requires-resources
wiring, theming caveats from doc 11 §8) is a **v2 option only if demanded** — not in this phase's
scope beyond keeping the `hostcompat` DataStore shim alive.

**Exit criteria**: empty app → add repo → install → browse → play with zero dead ends (doc 18
§10 Phase 4 exit); full §7/§8 surfaces; per-provider disable visibly removes a provider from
Cloud browse/search; update pill appears when a fixture repo bumps a version. Rollback:
individual WPs are UI/prefs — trivially revertible; subscriptions table simply stops polling.

---

## 8. Cross-phase dependencies map

```
            ┌──────────────────────────────────────────────────────────────┐
            │ PRE-FLIGHT GATES (§1) — user decisions                      │
            │   G1 GPL ──► G2 vendoring ──► {G3..G14 same session}        │
            └───────────────┬──────────────────────────────────────────────┘
                            ▼
            ┌──────────────────────────────────────────────────────────────┐
            │ PHASE 0 — SPIKE (§2)  vendored compile + 1 .cs3 + collisions│
            │   R2/R3 retirement · GO/NO-GO memo                          │
            └───────────────┬──────────────────────────────────────────────┘
                     GO │            ╲ NO-GO ╲→ halt / artifact-fallback / reshape
                        ▼
            ┌──────────────────────────────────────────────────────────────┐
            │ PHASE 1 — FOUNDATION (§3)                                   │
            │  WP1.1 modules ─┬─ WP1.4 .cs3 pipeline ── WP1.5 repo client  │
            │  WP1.2/1.3 provider-api + registry (steps 1–3)              │
            │  WP1.6 schema pass (doc 17 §9)  ←── parallel track          │
            │  crude extensions UI (doc 18 UI Phase 0)                    │
            └───────┬──────────────────────────────┬──────────────────────┘
                    ▼                              │ (registry + schema ready)
            ┌──────────────────────────────────────────────────────────────┐
            │ PHASE 2 — CLOUD SCREEN v0 (§4)   ← doc 16 §5.3 step 4 + 5    │
            │  WP2.1 tab ─ WP2.2 browse ─ WP2.3 details ─ WP2.4 resolve/  │
            │  MPV handoff (+comma-escape fix, +pendingSeek fix)          │
            └───────┬───────────────────────┬──────────────────────────────┘
                    ▼                       ▼
        ┌────────────────────┐   ┌────────────────────────┐   either order;
        │ PHASE 3 — SEARCH + │   │ PHASE 4 — DOWNLOADS +  │   recommended
        │ LIBRARY (§5)       │   │ CACHE (§6)             │   3 → 4; can
        │ (needs WP1.6 P1/P2 │   │ (needs WP1.6 P3/P5 +   │   interleave
        │  + Phase 2 watch)  │   │  Phase 2 resolve flow) │
        └─────────┬──────────┘   └──────────┬─────────────┘
                  └───────────┬─────────────┘
                              ▼
            ┌──────────────────────────────────────────────────────────────┐
            │ PHASE 5 — POLISH + PARITY (§7)                              │
            │  §7 parity needs P1 repo client; filters need P2/P3 UI;     │
            │  subscriptions need P3 library rows · dashboards last      │
            └───────────────┬──────────────────────────────────────────────┘
                            ▼
                     MILESTONE — v1 shipped (§12)
```

**Critical path**: `G1 → G2 → Phase 0 → Phase 1 (WP1.3 registry + WP1.4 loader) → Phase 2 →
Phase 3 → Phase 5 → v1` (Phase 4 sits off the critical path if downloads are deferred a release —
`[plan]`; G13's light-vs-full answer can also pull Phase 3 off it).

**Parallelization opportunities** `[plan]`: Phase 1's WP1.6 schema pass is independent of
WP1.1–1.5 (different files, same PR window); Phase 2's player fixes (comma-escape,
pendingSeek) are separable PRs benefiting both ecosystems (doc 19 §11 Q2); Phase 3 and Phase 4
share no files after WP1.6 and can interleave; most of Phase 5 (settings screen, filters,
onboarding) only needs Phase 2 and can start early. Device-feedback latency (the user's loop)
overlaps every phase — each phase ships an APK for device passes while the next is built.

---

## 9. Per-phase verification protocol

Our established loop (SESSION.md + workflow.md): **CI is the compiler of record** (GitHub Actions
builds; poll the API, read failures, never claim green without polling — D-156/D-281), the
**emulator** covers everything up to Cloudflare-gated bytes (`EMU` ✅/❌ lists; install/launch/
AniList/extensions-trust/search verified E2E there; playback/cache/download bytes ❌ — datacenter
IP fails CF), and **real playback stays on the user's OnePlus device** (their established
device-feedback loop: every release tested, reported back, next session = fix batch + bump).
Doc 19 §10 adds the CS3-specific split: the x86_64 emulator APK ships **no libmpv at all**
(arm-only `abiFilters`) — emulator covers install→browse→details→picker→download-queue states
ONLY; `loadfile` and beyond is device-only.

| Phase | CI asserts (green build) | Emulator asserts (`EMU` workflow: CI-built x86_64 APK, logcat + UI dumps) | Device feedback we ask of the user | Rollback |
|---|---|---|---|---|
| **0 Spike** | vendored module compiles under Kotlin 2.2/AGP 8.9; dependency-resolution report artifact; aniyomi smoke suite | (optional) harness run of load/search/loadLinks against AllMovieLand with reachability pre-probe (`getent hosts`, `EMU` 4.5) | none (memo only) | scratch branch deleted; zero main impact |
| **1 Foundation** | unit tests: installer sha256/state machine/mappers/EpisodeKeys/registry dispatch; schema compiles; schema.ts lint | doc 19 §10.2 #1: plugin install → provider listed (extensions screen + `Anikuta:Data:Cloudstream`-style logs); errored rows show real reasons; DATABASE.json export shows new tables (doc 15 §9 tooling) | optional early smoke: add phisher repo, install one plugin, confirm it lists | revert feature branch; drop module from settings; dev wipe re-runs (accepted G6) |
| **2 Cloud Screen v0** | module-graph invariant (`:feature:cloud-screen` → provider-api only); mapping unit tests; `WatchKey` serialization round-trip (3-field subtitle lines) | doc 19 §10.2 #2–4: browse→details render; picker streams (count grows across dumps 15–30 s apart); timeout/soft-failure keeps links, no crash | **the demo pass**: install Uakino → browse → details → play an episode and a movie; Referer host plays; subtitles visible; mirror-switch keeps position; 20-min re-open instant (doc 19 §10.4 checklist) | delete/hide Cloud tab (dynamic pill collapses); anime flows untouched |
| **3 Search + Library** | search fan-out ViewModel unit tests (D-305 generations, per-provider failure isolation); favorites/progress store tests | search ≥3 providers live-fill; one failing provider section → own retry row; favorite → "Cloud" category; partial watch → restart → resume row | resume-into-playback on device; category placement feel (G12) | hide Cloud category + bookmark action; stranded rows FK-clean |
| **4 Downloads + cache** | ReResolver tier-match unit tests; cache key derivation tests | doc 19 §10.2 #5: enqueue → queue row w/ `source_key`, canonical key, polymorphic `resolve_context`; QUEUED→DOWNLOADING→ERROR visible | M3U8 `.ts` download plays offline; TTL-expiry → re-resolve resumes at same cacheKey; verifier lifecycle logs | disable cloud enqueue; stranded queue rows deletable |
| **5 Polish + parity** | prefs/filters unit tests; subscription poll scheduling tests | full empty-app→repo→install→browse→play walk (doc 18 §10 Phase 4 exit); update pill on fixture version bump; NSFW hidden under OFF | full v1 acceptance sweep (§12) + the §10.4 ladder against a deliberately-broken mirror set (AnimeJl) | per-WP UI reverts; subscriptions stop polling |

Per-phase commit cadence follows workflow.md step 6 (one phase = separate commits), notify via
ntfy.sh per phase, docs updated in-session (progress/decisions/changelog/lessons) — no drift.

---

## 10. Estimates & sizing

**Honesty first** `[plan]`: this program's own research phase (docs 00–19) ran ~24 documents
across 5 batches; every prior estimate in this repo is a **planning aid, not a promise** — the
device-feedback loop inserts real latency between phases, and plugin-ecosystem surprises
(doc 12's quality spectrum) have historically reshaped scopes mid-phase. Sizes below are
agent-session units (one session ≈ one focused work batch ending in CI-green commits), derived
from the docs' module inventories (doc 16 §2's ~10 new classes + doc 17 §9's ~11-table schema +
doc 18's 4 screens + doc 19's 5 runtime patches):

| Phase | Size | Sessions `[plan]` | Basis |
|---|---|---|---|
| 0 Spike | **M** | 3–5 (~1 week) | doc 16 §1.6's own "~1 week incl. build adaptation + smoke tests" |
| 1 Foundation | **L** | 8–12 | ~10 new classes across 2 modules + 6 hostcompat shims + registry refactor steps 1–3 + doc 17 §9's 8-changed/3-new-table schema + schema.ts + §24 docs |
| 2 Cloud Screen v0 | **L** | 8–12 | 1 new feature module, 3 screens (browse/details/picker), resolve flow + 5 player-adjacent patches (comma-escape, pendingSeek, verifier, WebView host, 20-min cache) |
| 3 Search + Library | **M** | 5–8 | 1 search screen (fan-out + isolation) + favorites/progress/resume wiring (schema pre-landed) |
| 4 Downloads + cache | **M** | 5–8 | orchestrator entry generalization + ReResolver branch + cache keys + 403 ladder + sub downloads |
| 5 Polish + parity | **M** | 5–10 | extensions parity + settings screen + filters + onboarding + optional subscriptions + dashboard debt |
| **Program total** | — | **~35–50 sessions ≈ 6–10 weeks of focused agent work** | excludes user-decision latency (§1) and device-feedback rounds (at least one per phase 2–5) |

Largest schedule unknowns, in order: Phase 0's vendored-build adaptation (could be 1 day or the
whole week — that's why it's a spike), jsoup/OkHttp regression fallout (G7), and Phase 2's
player-handoff edge cases (the comma hazard is a pre-existing write-side bug we must fix under
load, doc 19 §2.1b).

---

## 11. The "do not do" list (accumulated v1 non-goals)

Everything docs 16–19 explicitly cut or deferred — **these are commitments, not TODOs**. Each
entry names its revival trigger where the docs gave one.

**Playback/link classes** (doc 19 §9, §2.5):
1. DRM (`DrmExtractorLink`) — no Widevine/MediaDrm in MPV; CS3's DRM is DASH-only anyway. (Revive: a Media3-based `IPlayer` variant.)
2. DASH playback + downloads — no DASH demuxer in libmpv; CS3 itself excludes DASH from downloads. (Revive: same, or premux via bundled ffmpeg; doc 19 §11 Q10's prevalence scan is optional homework.)
3. TORRENT/MAGNET playback + downloads — TorrServer is a Go gomobile AAR we won't vendor; 1/58 census usage. (Revive: user demand + legal review.)
4. Live (`TvType.Live`) — our watch flow is VOD-shaped; CS3's live support is itself marginal (doc 09 §9).
5. Chromecast — no surface; MPV makes it hard (doc 09 §8).
6. `ExtractorLinkPlayList` multi-slice concat — rare; needs slice bookkeeping. (Revive: a provider we want ships it.)
7. `getVideoInterceptor` wiring — 0 census usages; MPV has no interceptor concept (doc 16 §4.4).
8. Auto next-mirror on playback/download error — pinned re-resolve + manual "Try another link" instead (doc 19 §11 Q5/Q6 recommend manual v1).
9. Next-episode link pre-warm at 80% watched — doubles provider traffic; 20-min cache covers binges (doc 19 §8.4, §11 Q11).
10. Periodic idle re-resolution — the verifier is preventive; blind re-resolves double host load (doc 19 §4.2).

**Architecture/integration** (doc 16 §4.4, §5.3, §6, §7):
11. Unified search (doc 16 §5.3 step 6) — EXTENSION-mode search stays aniyomi-only in v1; possibly never.
12. Metaprovider support (TmdbProvider/TraktProvider delegation flows) — every provider renders as a direct provider in v1.
13. `getLoadUrl`/`supportedSyncNames` sync-ID deep launch.
14. Routing Cloud content through `:core:video-resolver`/`:core:smart-matcher` (aniyomi-typed; the Cloud Screen has its own resolve path).
15. Plugin settings UI hosting (`openSettings` Fragments) — hidden v1, 58/58 providers have none; hybrid host is v2 (doc 16 §7).
16. `cloudstreamrepo://` deep links — no benefit without external integrations (doc 16 §6).
17. Hot-reload tooling (`deployWithAdb`-style dev loop) — nice-to-have, deferred (doc 16 §11-9; doc 02 §8).
18. Porting our 12.0–17.0 libVersion split-brain or an apiVersion-range analog into CS3 management — `apiVersion` is dead at runtime (doc 16 §3.2).

**Data layer** (doc 17 §2.4, §5.4, §10):
19. Auto cross-provider merging / fuzzy title+year matching — manual link only in v1 (CS3 itself makes this a user dialog; doc 12 §9.3 #4 shows years are faked); exact-syncData-id assist chips are v1.5.
20. AniList tracking / tracker relay for cloud-only content — `hasAnilistLink`-gated; no CS3↔MAL/Kitsu relay (CS3's SyncAPI world is out of scope, doc 13 §8).
21. CS3's stale full-DTO favorites snapshots and 32-bit url-hash ids — our `main_entry`/`library_item` model wins (doc 17 §7; doc 15 §8.11).
22. CS3 watch-type 5-state bookmarks, quality profiles / video-source-priority DataStore — our categories + ResolverServer model cover them (doc 17 §10).
23. Actors/trailers/recommendations *storage* beyond what our v1 UI renders — median provider uses ~30% of the field surface (doc 17 §10 census cuts; recommendations/actors are UI-rendered in Phase 5 but stay ext-axis JSON, no new tables).
24. Segment-granular HLS download resume across process restarts — sidecar pause/resume only (doc 19 §6.3).
25. `episode_schedule`/Schedule-tab feed from CS3 `nextAiring` — details-page countdown only (doc 17 §5.4; doc 12 §9.2: 0/58 providers send it).

**UI** (doc 18):
26. Unified flat merged search results (round-robin) — per-provider sections with visible duplicates instead (doc 18 §3.2).
27. quickSearch — nearly dead upstream; skipped (doc 06 §4/§7.1 #2).
28. "None"/"Random" pseudo-providers in the provider picker (doc 18 §2.2 #1).
29. TvType color coding — text pills only until runtime-type drift is measured (doc 18 §9.3).
30. Cross-world links inside screens (anime⇄cloud details deep-links) — bottom bar is the only switch in v1 (doc 18 §1.2; "Search this anime in Cloud" is a v2 idea).
31. CS3 online-subtitle providers (OpenSubtitles et al.) — separate subsystem, post-v1 feature (doc 09 §3.3; doc 19 §9).

**Build/legal** (doc 16):
32. No JitPack `-SNAPSHOT` dependency (floating bytes under a reflective classloader contract).
33. No minSdk < 24 ever (Jackson 2.13.1 constraint — G11).
34. No R8/ProGuard on the reflection-heavy GPL tree in v1 — measure first (doc 16 §10-R10).
35. No pre-seeded repos — zero defaults (G5; D-043 symmetry).

---

## 12. Milestone definitions

**M0 — Spike GO** (end Phase 0): vendored library compiles under our toolchain; one real plugin
loads and resolves; collision verdicts recorded; user says go.

**M1 — Lights on** (end Phase 1): a `.cs3` installs from a real repo, passes sha256, loads, and
its providers appear in `ExtensionProviderRegistry` — the second ecosystem exists under the app,
invisible to users who never install one.

**M2 — First watch** (end Phase 2): the demo moment — on the user's device: install Uakino →
Cloud tab → browse → details → picker → **MPV plays with headers and subtitles**. The program's
core bet is proven at this milestone; everything after is breadth.

**M3 — Daily driver** (end Phase 3): cloud titles are searchable, favorite-able, and resumable
like anime — mixed Library with provider badges, "S02E05 · 14/38" progress.

**M4 — Offline** (end Phase 4): cloud downloads run through the shared queue with re-resolve
resilience and ecosystem-keyed cache.

**M5 — v1 shipped** (end Phase 5) = **the program's "done"**:
- Phases 0–5 complete with §3–§7 exit criteria met;
- doc 19 §10.5's definition verbatim: *"emulator suite green (items 1–5) + device checklist
  signed off on the two fixture providers + one deliberately-broken mirror set showing the ladder
  and error visibility"* (Uakino rich-path + DoramyWorld minimal-smoke fixtures, AnimeJl as the
  broken-mirror set — doc 19 §10.3);
- extension-management parity (doc 18 §7) and the "Cloud sources" settings screen live;
- dashboards/docs obligations closed (§24 database docs, schema.ts in sync, DATABASE.json
  browsing CS3 tables — doc 15 §9, doc 17 §9.3);
- the "do not do" list (§11) still true — v1 scope discipline held.

**Post-v1 candidates** (not commitments, from the docs' revival triggers): exact-id
cross-provider suggestion chips (v1.5, doc 17 §2.4b), plugin-settings Fragment host (doc 16 §7c),
CS3 subtitle providers (doc 09 §8), live/DASH/DRM via a Media3 player variant (doc 09 §8),
subscriptions/notifications if deferred at WP 5.5, unified search (step 6) only if the user asks
after living with the Cloud tab.

---

## 13. Verification status (this doc's own evidence)

- **Inherited plan content**: every architectural/data/UI/playback statement above is cited to
  docs 16–19 (which own its verification); this doc re-derived nothing about either codebase.
- **Fresh verifications performed for this doc** (methods inline): `research/phisher-builds/`
  contains exactly **80 `.cs3`** + `repo.json` + `plugins.json` (ls); `AllMovieLandProvider.cs3`
  manifest re-read (`version 23, requiresResources false`) and its `plugins.json` entry re-checked
  (`status 1`, `fileHash sha256-938e5d6b…c65c9` matching doc 02 §1.2, `tvTypes [Movie, TvSeries,
  Cartoon]`, lang `hi`); `Animeav1.cs3`/`Latanime.cs3` manifests + `status 1` entries re-checked;
  binary class-ref census on the three spike candidates (`CommonActivity`/gson/`DataStore` =
  0/3 — library-only dependencies; note the `MainActivity`-grep ambiguity was resolved as the
  library-facade `MainActivityKt` per doc 16 §1.4's census); CakesTwix-ext/storm-ext clones
  contain **no compiled `.cs3`** (ls); CakesTwix remote URL read from its `.git` config.
- **Not verified / flagged**: the exact CakesTwix build-branch `repo.json` URL for Phase-2 fixture
  sourcing (§4.3); whether the phisher repo's host domains are emulator-reachable (probe at
  Phase 0/1 per `EMU` 4.5); all effort numbers are `[plan]` estimates, not measurements.
- This doc made **no code changes, no commits** — only this file + the worklog entry.

*End of doc 20 — the roadmap. Inputs: docs 00–19 (research + plans 16–19 normative).
Next consumer: the B5 implementation sessions, which should start at §1's gate table and §2's
spike checklist.*
