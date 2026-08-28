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
| 01 | `01-ecosystem-overview.md` | What CloudStream is; project/repo map; versions; how app+library+plugins fit together; legality/DMCA posture | B1-a | ⬜ |
| 02 | `02-plugin-format.md` | `.cs3`/`.jar` format forensics; plugin project layout (gradle, manifest, template); build & CI; signing; apiVersion | B1-b | ⬜ |
| 03 | `03-mainapi-reference.md` | Complete `MainAPI` reference — every property/method a plugin can override, with signatures + semantics | B1-c | ⬜ |
| 04 | `04-extension-repositories.md` | repo.json + plugins.json formats; repo indexing; add-browse-update flow; community repo ecosystem; verification rules | B1-d | ⬜ |
| 05 | `05-data-models.md` | Full data-model catalog: SearchResponse family, LoadResponse family, Episode, Video/ExtractorLink, subtitles,TvType, Quality — with field-by-field notes | B1-e | ⬜ |
| 06 | `06-search-and-mainpage.md` | `mainPage`/`mainPageOf`, filters, `search`, `quickSearch`; how the app drives them | B2-a | ⬜ |
| 07 | `07-details-and-metadata.md` | `load()` details+metadata: posters, headers, actors, recommendations, nextAiring, TV/seasons structure, metaproviders (TMDb/Trakt/MDL) | B2-b | ⬜ |
| 08 | `08-video-loading-extractors.md` | `loadLinks`, ExtractorLink, VideoExtractor, `resolveLink`, built-in extractor inventory, custom extractors, subtitle loading | B2-c | ⬜ |
| 09 | `09-video-playing.md` | How the app turns links into playback: ResultResolution, player infra, previews/thumbnails, download integration | B2-d | ⬜ |
| 10 | `10-categories-and-provider-types.md` | TvType taxonomy (movie/TV/asian drama/anime/documentary/…), tvTypes in plugins.json, genre filters, language categorization, provider config | B2-e | ⬜ |
| 11 | `11-plugin-settings.md` | Plugin settings DSL (ExamplePlugin), setting types, ProviderSettings, how the app renders plugin settings, providers config UI | B3-a | ⬜ |
| 12 | `12-real-plugin-examples.md` | Deep-dives on real providers: movie (Uakino/AllCalidad), series (Serialno), Asian drama (DoramyWorld/DoramasFlix), anime (Coaninet/AnimeJl), video-sites (Dailymotion/Twitch) — patterns & anti-patterns | B3-b | ⬜ |
| 13 | `13-cloudstream-app-internals.md` | How the app loads plugins (PluginManager/Plugin classes), repo management, update checker, DataStore keys, favorites/subscriptions/watched model, bookmaking | B3-c | ⬜ |
| 14 | `14-ani-kuta-current-state.md` | Our current aniyomi-based extension architecture: manager/loader/installer/trust/repos/provider-api — the integration surface | B3-d | ⬜ |
| 15 | `15-ani-kuta-database.md` | Our Room DB schema + DATABASE.json dashboard tooling; what content identity/history/library model we have; gaps for CS3 content | B3-e | ⬜ |
| 16 | `16-integration-architecture.md` | The integration design: module layout, provider-api extension, dual loaders, coexistence rules | B4-a | ⬜ |
| 17 | `17-integration-data-layer.md` | Data-layer plan: schema changes, content identity across 2 systems, metadata caching, images | B4-b | ⬜ |
| 18 | `18-integration-ui.md` | "Cloud Screen" UI/UX plan: browse/search/details/watch flows for CS3 content; settings; extension management UI | B4-c | ⬜ |
| 19 | `19-integration-playback.md` | Playback + downloads plan: resolved video lists → our player, quality/label mapping, subtitles, caching implications | B4-d | ⬜ |
| 20 | `20-implementation-roadmap.md` | Phased implementation roadmap with milestones + per-phase verification | B4-e | ⬜ |
| 21 | `21-risks-open-questions.md` | Risks, unknowns, open questions for the user | B4/B5 | ⬜ |
| 22 | `22-VERIFICATION-LOG.md` | Fact-check log: every verified fact (source file + line), corrections, confidence ratings | B5 | ⬜ |
| — | `README.md` | Master index + executive summary (written last) | B5 | ⬜ |

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
| 2026-08-29 | B0 | Workspace cloned (11 sources, see §2). Skeleton + tracker created. Initial push. |

## 7. Verification Log Summary

(Details live in `22-VERIFICATION-LOG.md`; this is the dashboard.)

| Batch | Claims checked | Corrections made | Confidence |
|---|---|---|---|
| B1 | — | — | — |
| B2 | — | — | — |
| B3 | — | — | — |
| B4 | — | — | — |
| B5 | — | — | — |
