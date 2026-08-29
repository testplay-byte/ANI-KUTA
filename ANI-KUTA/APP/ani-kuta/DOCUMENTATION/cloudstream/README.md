# CloudStream (CS3) Research & Integration — Master Index & Executive Summary

This is the **front door** to the complete CloudStream research + integration plan for ANI-KUTA's
**"Cloud Screen"** — a second extension ecosystem (movies / TV / Asian drama / live video) to run
alongside our existing aniyomi-based anime system. The set was produced 2026-08-29 on branch
`streaming/CLOUDSTREAM` (Task 40) by a 5-batch sub-agent program: docs 01–15 research the CloudStream
ecosystem and our own codebase from pinned source clones, docs 16–20 are the integration plans,
docs 21–22 are the consolidated decision list and verification log. **Verification pedigree**: 538+
sampled claims swept, 96.8% verified exactly as written, 26 crown-jewel facts independently
reproduced across 82 verification events — every number and method in
[`22-VERIFICATION-LOG.md`](./22-VERIFICATION-LOG.md).

---

## 1. Executive summary

**CloudStream (CS3)** is an open-source, extension-based media center: a Kotlin app
(`recloudstream/cloudstream`) whose entire catalog comes from community **plugins** (`.cs3` files)
distributed through **JSON repositories**, with ~**97 built-in extractors** resolving hosted-video
pages into playable links. Adopting its plugin API gives ANI-KUTA instant access to the ecosystem's
movie/TV/Asian-drama providers, its extractor swarm, and its TMDb-style metadata patterns — without
giving up our player, DB, or anime flows: **we adopt their links, not their app** (doc 19).

The recommended architecture (docs 16–19, all `[recommendation]`, none built yet):

- **Vendor the CS3 library source** as `:external:cloudstream3` (pinned @ `efc1915`) instead of the
  JitPack artifact — the only route that dodges the Kotlin-2.4 metadata trap with a reproducible
  classloader contract (doc 16 §1.7).
- **Extend `SourceVideo` in `:core:provider-api`** additively (label/source/referer/headers/type/
  extractorData/subtitle/audio) instead of introducing a CS3-only video type (doc 16 §4.2).
- **Introduce an `ExtensionProviderRegistry` facade** (named Koin bindings, multi-provider) consumed
  first by the new Cloud Screen; existing features keep `ExtensionManager` untouched in v1 (doc 16 §5).
- **Ship a separate, dynamic 5th "Cloud" bottom-nav tab** that appears only when ≥1 CS3 provider is
  installed + enabled — zero blast radius on anime flows (docs 16 §5.4, 18 §1.2).
- **Key cloud content by two-format episode keys** — `mainId|%05d` (global) + `mainId|S02E00005`
  (seasoned), byte-compatible with today's scheme — plus a `content_source_link` table for N-source
  identity (doc 17 §2–§3).

The implementation program is **6 phases (0–5), ~35–50 agent sessions ≈ 6–10 weeks** (doc 20 §0):
Phase 0 spike (toolchain risk retirement) → 1 foundation → 2 Cloud Screen v0 → 3 search/library →
4 downloads/cache → 5 polish. **None of it starts before the #1 blocking decision — G1: GPL-3.0
relicensing.** CS3's library is GPL-3.0 with no separate library license and ANI-KUTA currently has
no license at all; any route that ships CS3 library code creates GPL obligations (doc 16 §9).

---

## 2. ⚠ THE decision pointer — read this before any implementation

> ### Before any implementation → read [`21-risks-open-questions.md`](./21-risks-open-questions.md) (§2, the 17 gates) — **G1 GPL first.**
> One gate session (~30–45 min; every row has a ready recommendation) unblocks the whole program.
> Record one `D-NNN` per gate in `AGENT-CONTEXT/memory/decisions.md` (doc 20 §1).

**The top-5 gates** (of 17 — all with recommendations in doc 21 §2):

| Gate | Decision (one line) | Recommendation |
|---|---|---|
| **G1** | Relicense ANI-KUTA **GPL-3.0**? (gates *everything*; the only H×H risk with no engineering mitigation) | **Yes** |
| **G2** | Vendor the CS3 library source as `:external:cloudstream3` @ efc1915? | **Yes** |
| **G3** | Separate, **dynamic 5th "Cloud" tab** for CS3 content? | **Yes — option A** |
| **G4** | NSFW policy — one master switch (default OFF), badge-only, no NSFW progress writes? | **Confirm** |
| **G5** | Ship **zero default repositories** (user adds their own)? | **Yes** |

Then G6–G14 (dev-data wipe, jsoup, gson shim, trust model, plugin-settings skip, minSdk/desugaring,
library placement, v1 scope, branch discipline) + G15–G17 (folder layout, anime-on-CS3, AniList
backfill) — same session, each ~2 minutes (doc 21 §2.2–§2.3).

---

## 3. Reading guide

### 3.1 The doc map (00–22)

| Doc | What it is (one line) | Read it when |
|---|---|---|
| [00](./00-RESEARCH-TRACKER.md) | Master tracker: mission, source map, batch plan, progress + verification dashboard | You want the program's own live status |
| [01](./01-ecosystem-overview.md) | What CloudStream is; repo/project map; how app + library + plugins fit; legality posture | First orientation on CS3 |
| [02](./02-plugin-format.md) | `.cs3`/`.jar` binary forensics; plugin project layout; build/CI; signing; apiVersion | Implementing the loader/installer, or curious what a plugin *is* |
| [03](./03-mainapi-reference.md) | The complete `MainAPI` provider contract — every overridable property/method with signatures | Writing the provider bridge or host-compat layer |
| [04](./04-extension-repositories.md) | repo.json → plugins.json formats; add/browse/install/update/delete flow; community repo ecosystem | Implementing the repo client |
| [05](./05-data-models.md) | Every data class crossing the plugin boundary: SearchResponse/LoadResponse/Episode/ExtractorLink/enums | Mapping CS3 models to ours |
| [06](./06-search-and-mainpage.md) | Discovery flow: `mainPage`/`getMainPage` + `search`/`quickSearch`, provider-side AND app-side | Building Cloud browse/search |
| [07](./07-details-and-metadata.md) | `load()` details pipeline, poster/image loading (**posterHeaders**), metaproviders (TMDb/Trakt/MDL) | Building the cloud details screen |
| [08](./08-video-loading-extractors.md) | `loadLinks` contract + the extractor subsystem (registry, URL resolution, 97 built-ins, custom ones) | Building the resolve pipeline |
| [09](./09-video-playing.md) | CS3's player (Media3/ExoPlayer), quality selection, subtitles, resume, download pipeline | Understanding what we adopt vs replace |
| [10](./10-categories-and-provider-types.md) | The 3-layer categorization system, TvType taxonomy, NSFW gating, language system | Designing Cloud browse filters |
| [11](./11-plugin-settings.md) | Plugin settings reality: `openSettings` lambda only — **no settings DSL exists** | Deciding G10 / host-compat settings |
| [12](./12-real-plugin-examples.md) | Field guide: 6 full provider walkthroughs + 58-provider census; patterns & anti-patterns | Gauging plugin quality variance (R4) |
| [13](./13-cloudstream-app-internals.md) | How the CS3 app hangs together: startup, plugin orchestration, DataStore keys, favorites model | Designing our runtime scope/isolation |
| [14](./14-ani-kuta-current-state.md) | OUR extension architecture today — the integration surface (`VideoExtensionProvider`, ExtensionManager) | The "this side of the bridge" reference |
| [15](./15-ani-kuta-database.md) | OUR SQLDelight schema (24 tables), content identity, watch/download persistence, DATABASE.json tooling | Before any schema work (doc 17 §9) |
| [16](./16-integration-architecture.md) | **THE master plan**: dependency strategy, module layout, loader, provider bridge, registry, licensing, risks | The normative architecture doc — read in full |
| [17](./17-integration-data-layer.md) | Data-layer plan: the 5 hardest schema problems solved; episode keys; favorites; schema-change list | Before Phase 1 schema pass |
| [18](./18-integration-ui.md) | Cloud Screen UI/UX: nav IA, browse/search/details/watch flows, extension management UI | Before Phase 2 UI work |
| [19](./19-integration-playback.md) | Playback/downloads runtime: link bridge → MPV, resolve flow, subtitles, downloads, cache keying | Before Phase 2/4 playback work |
| [20](./20-implementation-roadmap.md) | The phased roadmap: gates → spike → phases 1–5, sizing, per-phase verification, non-goals | Sequencing any implementation work |
| [21](./21-risks-open-questions.md) | THE consolidated decision list: 17 gates, 20 risks + watch list, 31 open questions, 17 uncertainties | **Before any implementation — G1 first** |
| [22](./22-VERIFICATION-LOG.md) | The evidence record: methodology, tallies, 18 corrections, 26 crown-jewel facts, re-verification protocol | Verifying or trusting any claim in the set |

### 3.2 Reading order for a fresh implementer

**00 → 21 (gates) → 20 (roadmap) → 16 → 17 → 18 → 19 → then the research docs (01–15) as needed.**
Tracker first for orientation; the decision list before anything else (G1 gates all work); the
roadmap for sequence; the four plan docs (16–19) are normative for what gets built; the research
docs are reference material consulted per topic.

### 3.3 Paths by intent

- **"I'm implementing Phase N"** — Phase 0 spike: doc 20 §2 + doc 16 §1, §3. Phase 1 foundation:
  doc 16 §2–§8 + doc 17 §9 + doc 14. Phase 2 Cloud Screen v0: docs 18 + 19 + doc 16 §4–§5.
  Phase 3 search/library: doc 17 §7 + doc 18 §6 + doc 06. Phase 4 downloads/cache: doc 19 §6–§7 +
  doc 17 §8 + doc 09 §5. Phase 5 polish: doc 18 §7 + doc 10 §8.
- **"I need to understand CS3's X"** — plugin format → 02 · MainAPI contract → 03 · data models →
  05 · repositories → 04 · search/mainpage → 06 · details/metadata → 07 · video loading/extractors →
  08 · playback/downloads (CS3 side) → 09 · categories/NSFW → 10 · plugin settings → 11 · real
  provider patterns → 12 · app internals → 13. Our side: extension architecture → 14 · database → 15.
- **"I'm verifying a claim"** — doc 22 (methodology, correction register, crown jewels) + doc 00 §7
  (dashboard). If a doc contradicts source, follow doc 22 §7's protocol: fix inline, append to the
  correction register, re-check affected crown jewels.

---

## 4. Key facts card — the answers to the original research questions

| # | Fact | Source |
|---|---|---|
| 1 | A **`.cs3` is a custom ZIP** — `manifest.json` (entry class) + `classes.dex` (plugin classes only), plus `res/`+`resources.arsc` only when `requiresResources` — NOT an APK, NOT a plain JAR | doc 02 §1.1 |
| 2 | Plugins load via a **parent-first `PathClassLoader`** from `MainActivity` (all loading, no Application hook); entry class comes from `manifest.json.pluginClassName` — the `@CloudstreamPlugin` annotation is fieldless decoration | docs 02, 13 |
| 3 | **Repos are a two-level JSON index**: `repo.json` (repo metadata) → `plugins.json` (per-plugin: name, tvTypes, language, version, fileHash…) — every download sha256-verified; update rule `version > saved \|\| version == -1` | doc 04 |
| 4 | **Search = parallel fan-out** over all enabled providers (`a.search(query, 1)`, the paginated overload); `quickSearch` is dead in the main search UI | doc 06 |
| 5 | **Details = `load(url)`** returning a `LoadResponse`; richer metadata comes from CS3's metaproviders (TMDb, CrossTmdb, Trakt, MyDramaList) — the pattern to copy for cloud metadata | doc 07 |
| 6 | **Covers need headers**: `posterHeaders: Map<String,String>?` on `SearchResponse`/`LoadResponse` threads Referer/UA to every poster/background/logo load (hotlink protection); episode stills + actor images load *without* them | doc 07 §3.3 |
| 7 | **Video resolution = `loadLinks(data, isCasting, subCb, linkCb)`** — a streaming callback API; the extractor registry holds **321 instances / 97 base classes**; URL→extractor = reverse-order registry walk + Levenshtein>80 mirror pass | doc 08 |
| 8 | CS3's own player is **Media3/ExoPlayer 1.9.3 + Cronet** — we adopt only their *links*: our player is **MPV** (`aniyomi-mpv-lib 1.18.n`), and `http-header-fields` is set before `loadfile` at all our load sites, so Referer/UA-bearing CS3 links play today | docs 09, 19 §2 |
| 9 | **Categories are 3 metadata layers**: repo manifest (`tvTypes[]`, `language`) → provider declaration (`supportedTypes`) → per-response `TvType` (exactly 18 values); NSFW is CS3's dual-switch anti-pattern we collapse to one | doc 10 |
| 10 | **CS3 has NO settings DSL** — only the `Plugin.openSettings` lambda + a plugin-authored Fragment + app-wide SharedPreferences; 58/58 census providers expose no settings at all | doc 11 |
| 11 | **CS3 persists everything as JSON blobs in one SharedPreferences file** (favorites/subscriptions = flattened `SearchResponse` snapshots keyed by URL hash) — no database anywhere in their app | doc 13 |
| 12 | **Our DB is SQLDelight 2.0.2 (NOT Room)** — 24 `CREATE TABLE`s in 16 `.sq` files, 0 migrations; `external_reference` from old planning docs was never built | doc 15 |
| 13 | **Our HLS downloader already does AES-128-CBC** decrypt + parallel segments + resume sidecar (encrypted streams download fine); the *playback cache* has no `EXT-X-KEY` handling (downloads only) | doc 19 §6, §7.4 |
| 14 | **Episode keys go two-format**: `mainId\|%05d` (global, byte-compatible today) + `mainId\|S02E00005` (seasoned) — the fix for CS3 multi-season content | doc 17 §3.3 |
| 15 | **`VideoExtensionProvider` is a clean but zero-consumer seam** (one Koin binding, no feature call sites) — the registry refactor builds on it with zero blast radius | docs 14, 16 §5 |

*(Every row above traces to a doc; facts 2, 6–8, 12–15 overlap the 26 crown-jewel facts independently
reproduced ≥2 times — doc 22 §4.)*

---

## 5. Status & next step

- **Research: COMPLETE.** Docs 00–22 written, swept, and cross-checked (doc 22); the tracker
  ([00 §6–§7](./00-RESEARCH-TRACKER.md)) holds the batch-by-batch progress + verification dashboard.
- **Blocked on: the G1–G17 gate session** (doc 21 §2) — G1 GPL-3.0 relicensing first, then vendoring
  approval, then the shape decisions. Output: one `D-NNN` decision record per gate.
- **Then: Phase 0 spike** (doc 20 §2, ~1 week) — vendor the library on a `spike/cs3-port` branch,
  compile under our Kotlin 2.2.0/AGP 8.9.1, load ONE real `.cs3` (AllMovieLand, hash-verified
  fixture) end-to-end; exit = GO/NO-GO memo retiring risks R2/R3.
- **Nothing has been implemented.** Docs 16–20 are plans (`[recommendation]`/`[design]` markers
  throughout); no ANI-KUTA production code has been touched by this program.

---

## 6. Program stats

| Stat | Value |
|---|---|
| Documents | 23 numbered docs (00–22) + this README |
| Total size | ≈18,000 lines (17,552 through B4 + doc 21: 301 + doc 22: 428 — doc 22 §2.1) |
| Agents deployed | 25 sub-agent runs (B1–B5, 5 each) + the main agent (setup, per-batch review, commits) |
| Verification | 538 claims sampled in B5 sweeps → 521 verified (96.8%), 15 corrected (all cosmetic), 0 unresolvable; 16/16 main-agent gate spot-checks green; 18 post-publication corrections total |
| Crown jewels | 26 load-bearing facts independently reproduced across 82 verification events (doc 22 §4) |
| Source base | 11 read-only clones pinned at commit `efc1915` (CS3 master, 2026-08-28) + csdocs + 7 plugin repos (doc 00 §2) |
| Produced | 2026-08-29 · Task 40 · branch `streaming/CLOUDSTREAM` |

*Start next at [`21-risks-open-questions.md`](./21-risks-open-questions.md) §6 — the single-page summary.*
