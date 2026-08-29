# CloudStream Research — Master Tracker

> **Mission**: Understand the CloudStream (CS3) extension ecosystem end-to-end so we can implement
> a second extension system ("Cloud Screen") in ANI-KUTA alongside our existing aniyomi-based system.
> This tracker is the workflow backbone: every doc, every batch, every verification is recorded here.
> **Last updated**: Task 40, Batch 0 (setup).

---

## 1. Mission Brief (from the user, Task 40)

Research, understand, analyze, document, plan the CloudStream system:

1. **Extension system** — how CS3 plugins work: format, structure, repositories, management, settings, categories (movies / TV series / Asian drama / …), updates.
2. **Metadata fetching** — how CS3 fetches details for movies & series: cover images, thumbnails, metadata, actors, recommendations, TV/season structures.
3. **Search** — how search works through CS3 extensions.
4. **Video playback** — how to get resolved video lists from CS3 extensions and play them.
5. **Integration planning** — how ANI-KUTA adopts a second extension system cleanly (architecture, data layer, UI, playback, extension management).
6. **Method**: multi-batch sub-agent research (5 batches), every batch reviewed + verified + committed + pushed (backup discipline). Quality over speed. Nothing skipped.

## 2. Source of Truth (research workspace — NOT part of the repo)

Cloned 2026-08-29 into `/home/z/ANI-KUTA-WORK/research/` (shallow clones, pinned at clone-time commits):

| Path | What it is | Key contents |
|---|---|---|
| `research/cloudstream/` | **recloudstream/cloudstream** (master @ efc1915, 2026-08-28) | The app (`app/`) + **plugin API library** (`library/`, KMP) |
| `research/cloudstream/library/src/commonMain/kotlin/com/lagradost/cloudstream3/` | The **plugin API** plugins compile against | `MainAPI.kt` (2860 lines), `extractors/` (~60 extractors), `metaproviders/` (Tmdb/Trakt/MyDramaList/SyncRedirector/CrossTmdb), `plugins/` (BasePlugin, CloudstreamPlugin), `syncproviders/`, `utils/`, `network/` |
| `research/cloudstream/app/src/main/java/com/lagradost/cloudstream3/` | The app internals | `plugins/` (Plugin, PluginManager 967L, RepositoryManager, VotingApi), `ui/`, `services/`, `subtitles/`, `utils/`, `actions/`, `syncproviders/` |
| `research/csdocs/` | **recloudstream/csdocs** — official docs site source | `devs/` (plugin dev guides: gettingstarted, create-your-own-providers, using-plugin-template, create-your-own-json-repository, scraping/), `Repositories.md`, `Settings/` |
| `research/cs-repos/` | **recloudstream/cs-repos** — community repo DB | `repos-db.json` (list of community repo.json URLs), `ci_check.py` (repo validation rules!) |
| `research/extensions/` | **recloudstream/extensions** — official extensions repo | 5 provider sources (Dailymotion, InternetArchive, Invidious, Twitch, Youtube), `repo.json` (repo index format) |
| `research/phisher-builds/` | **phisher98/cloudstream-extensions-phisher @ builds** | ~100 compiled `.cs3` + `.jar` plugins + repo.json (format forensics material) |
| `research/TestPlugins/` | **recloudstream/TestPlugins** — the official plugin template | `ExampleProvider/` (ExampleProvider.kt, ExamplePlugin.kt with settings, BlankFragment.kt) |
| `research/Luna712-ext/` | Luna712 community repo | Dailymotion + InternetArchive providers (alt implementations) |
| `research/MegaRepo/` | self-similarity/MegaRepo | MegaProvider |
| `research/CakesTwix-ext/` | CakesTwix/cloudstream-extensions-uk | SerialnoProvider, UakinoProvider (movies/series), **DoramyWorldProvider (Asian drama!)**, CoaninetProvider (anime) |
| `research/storm-ext/` | redblocker8/storm-ext | AllCalidad/CineHdPlus/Pelispedia (movies), DoramasFlix/DoramasYT (**Asian drama**), AnimeJl (anime + **custom extractors**) |

> ⚠ Research workspace lives OUTSIDE the repo (sandbox-local). All conclusions must be written INTO
> the committed docs below — the docs are the durable artifact, the workspace is scaffolding.

## 3. Documentation Map (the deliverable)

All docs live in `ANI-KUTA/APP/ani-kuta/DOCUMENTATION/cloudstream/`. Numbered, read in order:

| # | Doc | Scope | Owner batch | Status |
|---|---|---|---|---|
| 00 | `00-RESEARCH-TRACKER.md` | this file — workflow + progress + verification log | main | ✅ living |
| 01 | `01-ecosystem-overview.md` | What CloudStream is; project/repo map; versions; how app+library+plugins fit together; legality/DMCA posture | B1-a | ✅ |
| 02 | `02-plugin-format.md` | `.cs3`/`.jar` format forensics; plugin project layout (gradle, manifest, template); build & CI; signing; apiVersion | B1-b | ✅ |
| 03 | `03-mainapi-reference.md` | Complete `MainAPI` reference — every property/method a plugin can override, with signatures + semantics | B1-c | ✅ |
| 04 | `04-extension-repositories.md` | repo.json + plugins.json formats; repo indexing; add-browse-update flow; community repo ecosystem; verification rules | B1-d | ✅ |
| 05 | `05-data-models.md` | Full data-model catalog: SearchResponse family, LoadResponse family, Episode, Video/ExtractorLink, subtitles,TvType, Quality — with field-by-field notes | B1-e | ✅ |
| 06 | `06-search-and-mainpage.md` | `mainPage`/`mainPageOf`, filters, `search`, `quickSearch`; how the app drives them | B2-a | ✅ |
| 07 | `07-details-and-metadata.md` | `load()` details+metadata: posters, headers, actors, recommendations, nextAiring, TV/seasons structure, metaproviders (TMDb/Trakt/MDL) | B2-b | ✅ |
| 08 | `08-video-loading-extractors.md` | `loadLinks`, ExtractorLink, VideoExtractor, `resolveLink`, built-in extractor inventory, custom extractors, subtitle loading | B2-c | ✅ |
| 09 | `09-video-playing.md` | How the app turns links into playback: ResultResolution, player infra, previews/thumbnails, download integration | B2-d | ✅ |
| 10 | `10-categories-and-provider-types.md` | TvType taxonomy (movie/TV/asian drama/anime/documentary/…), tvTypes in plugins.json, genre filters, language categorization, provider config | B2-e | ✅ |
| 11 | `11-plugin-settings.md` | Plugin settings DSL (ExamplePlugin), setting types, ProviderSettings, how the app renders plugin settings, providers config UI | B3-a | ✅ |
| 12 | `12-real-plugin-examples.md` | Deep-dives on real providers: movie (Uakino/AllCalidad), series (Serialno), Asian drama (DoramyWorld/DoramasFlix), anime (Coaninet/AnimeJl), video-sites (Dailymotion/Twitch) — patterns & anti-patterns | B3-b | ✅ |
| 13 | `13-cloudstream-app-internals.md` | How the app loads plugins (PluginManager/Plugin classes), repo management, update checker, DataStore keys, favorites/subscriptions/watched model, bookmaking | B3-c | ✅ |
| 14 | `14-ani-kuta-current-state.md` | Our current aniyomi-based extension architecture: manager/loader/installer/trust/repos/provider-api — the integration surface | B3-d | ✅ |
| 15 | `15-ani-kuta-database.md` | Our SQLDelight DB schema + DATABASE.json dashboard tooling; what content identity/history/library model we have; gaps for CS3 content | B3-e | ✅ |
| 16 | `16-integration-architecture.md` | The integration design: module layout, provider-api extension, dual loaders, coexistence rules | B4-a | ✅ |
| 17 | `17-integration-data-layer.md` | Data-layer plan: schema changes, content identity across 2 systems, metadata caching, images | B4-b | ✅ |
| 18 | `18-integration-ui.md` | "Cloud Screen" UI/UX plan: browse/search/details/watch flows for CS3 content; settings; extension management UI | B4-c | ✅ |
| 19 | `19-integration-playback.md` | Playback + downloads plan: resolved video lists → our player, quality/label mapping, subtitles, caching implications | B4-d | ✅ |
| 20 | `20-implementation-roadmap.md` | Phased implementation roadmap with milestones + per-phase verification | B4-e | ✅ |
| 21 | `21-risks-open-questions.md` | Risks, unknowns, open questions for the user | B4/B5 | ✅ (gates answered 2026-08-29 — see doc 23 §1) |
| 22 | `22-VERIFICATION-LOG.md` | Fact-check log: every verified fact (source file + line), corrections, confidence ratings | B5 | ✅ |
| 23 | `23-implementation-phase1-design.md` | **Implementation phase 1 design record**: user's gate decisions, clean-room pivot (supersedes doc 16's vendoring), binary census, module design, session log | impl-1 | 🔄 in flight |
| — | `README.md` | Master index + executive summary (written last) | B5 | ✅ |

**PROGRAM STATUS: research ✅ COMPLETE → 🔄 IMPLEMENTATION PHASE 1** (started 2026-08-29). Gates answered by the user (doc 23 §1): **G1 = NO GPL — clean-room rewrite** (vendoring plan dead), unified Extensions page with Aniyomi/CloudStream tabs, universal NSFW direction, stay on `streaming/CLOUDSTREAM`. Session 1 scope = extension-management system (compat module + repos + install/load/manage + UI tabs). See doc 23.

Status legend: ⬜ pending · 🔄 in flight · ✅ done+verified · ⚠ needs-fix

## 4. Batch Plan (5 batches, sequential; agents within a batch run in parallel)

| Batch | Agents | Focus | Gate before next batch |
|---|---|---|---|
| B0 | main | Workspace setup, skeleton, tracker (this file) | push OK |
| B1 | a–e (5) | Core system: overview, plugin format, MainAPI, repositories, data models | I review all 5 docs, spot-check claims vs source, fix/annotate, commit+push |
| B2 | a–e (5) | Functionality: search/mainPage, details/metadata, video loading, playing, categories | same |
| B3 | a–e (5) | Settings, real plugin examples, CS3 app internals, ANI-KUTA current state, ANI-KUTA DB | same |
| B4 | a–e (5) | Integration planning: architecture, data layer, UI, playback, roadmap | same |
| B5 | 4–5 | Verification sweep (fact-check pass), consistency reviews, README/exec summary, final polish | final review + commit + push |

Agent worklog protocol: every agent appends its entry to `/home/z/my-project/worklog.md`
(Task ID `40-B<n>-<letter>`), reads prior entries first, and NEVER commits (main agent reviews + commits per batch).

## 5. Research Rules (every agent follows these)

1. **Source or it didn't happen** — every factual claim cites `path:line` from the workspace or a URL. No memory-based Kotlin signatures.
2. **Quote real code** — signatures copied from source, not paraphrased.
3. **Mark confidence** — `[verified]` (read in source), `[docs]` (from csdocs), `[inferred]` (reasoned, needs verification).
4. **No guessing about ANI-KUTA** — our-app facts come from our repo source only.
5. **Write for the future** — docs will be read months later by agents implementing the Cloud Screen. Be explicit, structured, example-rich.
6. **Don't touch** anything outside the assigned doc file (+ worklog append).

## 6. Progress Log

| When | Batch | What happened |
|---|---|---|
| 2026-08-29 | B0 | Workspace cloned (11 sources, see §2). Skeleton + tracker created. Initial push (cccbcfd). |
| 2026-08-29 | B1 | 5 agents → docs 01-05 written (4,305 lines total). Main-agent review: 6 spot-checks against source all green (PathClassLoader parent-first @ PluginManager.kt:611; apiVersion dead @ RepositoryManager.kt:57-59; no login/resolveLink in current MainAPI; TvType = 18 values; @CloudstreamPlugin fieldless; manifest.json entry-class discovery). Cross-doc consistency confirmed. NOTE: 3 agents initially rate-limited (429) when 5 ran concurrently — subsequent batches launch in waves of ≤2. |
| 2026-08-29 | B2 | 5 agents → docs 06-10 (+4,534 lines; set total 8,841). Main-agent review: MPV correction VERIFIED (our player = aniyomi-mpv-lib 1.18.n — not ExoPlayer as assumed); loadExtractor resolution algorithm VERIFIED exact (unshorten → lowercase schema-strip → reverse-order mainUrl prefix → Levenshtein >80 mirror pass); phisher tvTypes census run — doc 04 CORRECTED (Cartoon IS a valid enum value; real anomaly = Megakino's un-split "Movie,Anime,Cartoon" string). 1 rate-limit retry needed (B2-d). |
| 2026-08-29 | B3 | 5 agents → docs 11-15 (+4,853 lines; set total 13,694). Major corrections established: CS3 has NO settings DSL (only `Plugin.openSettings` lambda @ Plugin.kt:39 — VERIFIED); our DB is **SQLDelight 2.0.2, NOT Room** (VERIFIED in libs.versions.toml — tracker's own description fixed); `external_reference` table NEVER implemented (0 grep hits in core/ — doc 14's §6.6 hint corrected by B3-e); our `VideoExtensionProvider` seam is clean but has ZERO load-bearing call sites (single Koin binding, features inject ExtensionManager directly). Main-agent spot-checks: 3/3 green. 0 rate-limit retries (waves of 2). |
| 2026-08-29 | B4 | 5 agents → docs 16-20 (+3,858 lines; set total 17,552). THE PLANS: dependency = VENDOR the CS3 library source as `:external:cloudstream3` @ efc1915 (Kotlin 2.4 metadata trap kills the artifact route); extend SourceVideo in place; ExtensionProviderRegistry + blast-radius-ordered migration; separate dynamic 5th "Cloud" tab; two-format episode keys (S02E00005 seasoned); MPV http-header-fields VERIFIED (WatchScreen.kt:586/694); our HLS downloader already supports AES-128 + segment concat. **BLOCKING FINDING: CS3 library is GPL-3.0, ANI-KUTA has NO license — user must decide (G1 gate)**. 14 pre-flight gates G1-G14 consolidated in doc 20. ~35-50 sessions / 6-10 weeks program estimate. Main-agent spot-checks: 3/3 green (GPL, MPV, jsoup dep). 2 rate-limit retries (B4-a, B4-c). |
| 2026-08-29 | impl-1 (Task 41) | **GATE SESSION ANSWERED** (user): G1 = NO GPL license, clean-room rewrite from scratch (doc 16 vendoring plan DEAD); G2 = no vendoring, recreate only what we need; unified Extensions settings page with Aniyomi/CloudStream source tabs (tab visible when that system has installed extensions OR saved repos); G4 = universal NSFW toggle direction (CS gate ships session 1, app-wide unification deferred); G5 = zero default repos (recommendation kept); G6 = agent decision: CORE_RULES §30 applies but NO schema change this session; G14 = stay on `streaming/CLOUDSTREAM`, no throwaway branch. **Architecture pivot recorded in doc 23** — clean-room compat module `:core:cloudstream-api` + runtime `:data:cloudstream`. Binary census run over all 80 phisher `.cs3` plugins (exact class-reference counts → compat surface tiering). Declarations digest extracted (3,943 lines). NiceHttp + CloudstreamApi confirmed UNLICENSED on GitHub → clean-roomed as well. CI workflow extended to `streaming/**` + first unit tests. Session 1 scope: extension management (repos → install → load → manage + UI tabs); provider execution deferred. |
| 2026-08-29 | B5 | 5 agents: B5-a swept docs 01-10 (355 claims → 344 verified, 10 corrections, 0 unresolvable); B5-b swept docs 11-20 (183 claims → 177 verified, 5 corrections, 6/6 cross-doc consistency checks PASS — the 6 plan docs are contradiction-free and safely buildable-on); B5-c wrote doc 21 (17 gates G1-G17, 20 risks R1-R20, 31 themed open questions, 17 unverified-knowledge items u1-u17); B5-d wrote doc 22 (master tally: 538 claims swept → 96.8% verified, 18 post-publication corrections — none architectural, 26 crown-jewel facts / 82 independent verification events, re-verification protocol for Phase 0); B5-e rewrote README.md (173 lines: exec summary + decision pointer + reading guide + key-facts card). **PROGRAM COMPLETE** — 23 docs / 17,920 lines, 25 sub-agent runs total. |

## 7. Verification Log Summary

(Details live in `22-VERIFICATION-LOG.md`; this is the dashboard.)

| Batch | Claims checked | Corrections made | Confidence |
|---|---|---|---|
| B1 | 6 spot-checks by main agent (classloader, apiVersion, MainAPI members, TvType, annotation, manifest flow) | 0 needed — agent claims all verified | HIGH |
| B2 | 4 spot-checks (MPV player claim, loadExtractor algorithm, phisher tvTypes census, doc-04 Cartoon claim) | 1 correction in doc 04 (Cartoon is valid enum; anomaly is Megakino's single-string tvType) | HIGH |
| B3 | 3 spot-checks (SQLDelight vs Room, external_reference absence, openSettings @ Plugin.kt:39) | 2 corrections: tracker's "Room DB" description fixed → SQLDelight; doc 14 §6.6 external_reference claim corrected by B3-e (plan-only, never built) | HIGH |
| B4 | 3 spot-checks (GPL-3.0 license + no ANI-KUTA LICENSE, MPV http-header-fields @ WatchScreen.kt:586/694, library jsoup dep) | 0 needed — agent claims verified | HIGH |
| B5 | Full independent sweeps: B5-a 355 claims (docs 01-10) + B5-b 183 claims (docs 11-20) + 6/6 cross-doc consistency | 15 corrections (all cosmetic: line numbers, counts, census gaps, 1 phantom API `registerSettingsAPI`, 1 false-absence) — zero architectural corrections; consolidated in doc 22 | HIGH (96.8% exact) |
