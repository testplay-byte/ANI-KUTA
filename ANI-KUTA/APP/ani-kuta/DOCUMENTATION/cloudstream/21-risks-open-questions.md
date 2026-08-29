# 21 — Risks & Open Questions (THE consolidated decision list)

> **Mission (B5-c)**: one document holding EVERYTHING the user must decide or know about before and
> during the CloudStream (CS3) adoption program — every gate, risk, and open question from docs
> 00–20, deduplicated, each with a recommendation and a pointer to where the detail lives. This doc
> invents no new positions; it consolidates. If a question is genuinely missing from the corpus it
> is marked `[new — B5-c]` (there are two, both minor).
>
> **Ground truth inputs**: doc 16 §10–§11 (risk register R1–R10 + open questions 0–10), doc 17
> §11 (Q1–Q12), doc 18 §11 (Q1–Q12), doc 19 §11 (Q1–Q12), doc 20 §1 (gates G1–G14) — read in full;
> plus the `[open-question]` markers inline across docs 10–14, 16–19, the "unverified" sections of
> docs 10, 11, 13, 16, 18, 20, and the B5-a/B5-b verification notes (worklog 2026-08-29) for
> cautions on ephemeral evidence.
>
> **Markers**: **[recommendation]** = the docs' chosen option (inherited, not re-decided here) ·
> BLOCKING = no code starts until answered · `→` = where the detail lives · `[new — B5-c]` = added
> by this consolidation, not in the corpus.

---

## 1. How to use this doc

This is **THE decision list**. The program (doc 20) has ~35–50 agent sessions of build work behind
it; almost none of it can start until §2's gates are answered.

- **Start at §6** (single-page summary) for the five decisions that matter most.
- **Then run one gate session** over §2 (all 17 rows, G1 first — it gates everything). Every row
  already has a recommendation; the user's job is confirm/override, not research. Per doc 20 §1,
  the output is a short decision record appended to `AGENT-CONTEXT/memory/decisions.md` (one D-NNN
  per gate) — the workflow's "ask with a recommendation" pattern.
- **§3 (risk register)** is what the program already plans to mitigate — read it once so surprises
  are recognized as anticipated, not new.
- **§4 (open questions by theme)** are the decisions that can wait — each row names the phase by
  which it must be answered. Do not re-litigate them at gate time.
- **§5 (unverified/uncertain knowledge)** is what we know we don't know — the Phase 0 spike's
  re-verification list. Cite it when a plan claim depends on evidence we never had.

Nothing in this doc is new engineering; every row cites its source docs.

**Gate-session flow** (one sitting, ~30–45 min with the recommendations):
1. G1 first, alone — everything else is moot if the answer is "stay proprietary".
2. G2 — vendoring approval (consequence of G1-yes; the fallback is named if the spike stalls).
3. G3–G6 — the shape decisions (IA, NSFW, repos, dev-wipe).
4. G7–G14 in one pass — each is confirm-the-recommendation.
5. Optionally pre-answer G15–G17 (they have phase deadlines, not gate deadlines).
6. Record one D-NNN per answered gate in `AGENT-CONTEXT/memory/decisions.md`; state the branch
   instruction (G14) for Phase 0.

**Critical path reminder** (doc 20 §8): `G1 → G2 → Phase 0 (spike) → Phase 1 (foundation) →
Phase 2 (Cloud Screen v0) → {Phase 3, Phase 4} → Phase 5 → v1`. Phase 0 exists to retire R2/R3
before real code; its exit is a GO/NO-GO memo — if NO-GO, the fallback is the artifact route or a
program reshape, back to the user.

---

## 2. THE decision checklist (gates)

### 2.1 Primary gates (BLOCKING — no Phase 0 work until G1; G1–G6 before Phase 1)

**G1 first and prominent — it gates everything** (doc 16 §11-0: "without yes (or an explicit
informed alternative), B5 implementation should not start").

| ID | Decision | Options | `[recommendation]` | Blocking? | Source |
|---|---|---|---|---|---|
| **G1** | **THE GPL-3.0 decision.** CS3's library is GPL-3.0 (single root LICENSE, **no separate library LICENSE** — verified absent); ANI-KUTA has **no LICENSE at all** (source published on GitHub, no grant). ANY route that ships CS3 library code (artifact or vendored) distributes GPL code combined with ours → combined work under GPL-3.0 obligations (source availability). The re-implementation escape (option c) is legally grey, not a clearance. | (i) relicense ANI-KUTA GPL-3.0; (ii) stay proprietary → halt/reshape program (or "bring-your-own-runtime", architecturally ugly); (iii) take legal advice on the plugin-host question | **(i) relicense** — clean and cheap while the project is personal; consistent with the FOSS ecosystem we're joining. Our aniyomi stack is Apache-2.0 and NOT GPL-triggering — CS3 is the first strong-copyleft dependency | **YES — everything (all phases)** | doc 16 §9, §10-R1, §11-0; doc 20 G1 |
| **G2** | **Vendoring approval.** Add `:external:cloudstream3` (~1.6 MB GPL source, 174 Kotlin files, pinned @ efc1915) to the repo? | (a) JitPack artifact (needs app-wide Kotlin 2.4 bump — metadata trap; floating `-SNAPSHOT` under a reflective classloader contract); (b) **vendor source**; (c) re-implement MainAPI host (rejected: the 97 extractors + builder surface ARE the ecosystem; months + binary-compat risk) | **(b) vendor** — only option avoiding the Kotlin-metadata trap with a reproducible classloader contract; GPL provenance explicit in-tree; (a) stays the named 2-day-spike fallback if build adaptation stalls | YES — Phase 0 onward | doc 16 §1.3, §1.6–§1.7, §11-2; doc 20 G2 |
| **G3** | **Separate vs unified IA** — where does CS3 content live in our nav? | A. 5th bottom-nav "Cloud" tab, **dynamic** (appears only when ≥1 CS3 provider installed+enabled) / B. Browse section / C. More-screen entry | **A, dynamic** — zero blast radius on anime flows; every Cloud composable nav-agnostic so fallback (B/C) stays cheap. Unified search stays deferred (doc 16 §5.3 step 6, possibly never) | YES — Phase 2 shape | doc 16 §5.4, §11-1; doc 18 §1, §11 Q1, Q3; doc 20 G3 |
| **G4** | **NSFW policy.** Switch model + display + progress writes. | one merged master switch (default OFF) vs CS3's two-switch model (provider-level + master — the documented anti-pattern). Sub-decisions: badge-only vs filter for NSFW items inside mixed providers; suppress NSFW watch-progress writes? | **one master switch, default OFF; badge-only inside mixed providers; keep the "don't save NSFW progress" player rule** (CS3 does, `GP:1728-1729`) | YES — Phase 2 (picker gating) + Phase 5 (settings) | doc 10 §8.5; doc 16 §11-4; doc 17 §11-12; doc 18 §2.2 #8, §11 Q5; doc 20 G4 |
| **G5** | **Default repos.** Ship zero CS3 repos or pre-seed one? | zero defaults / pre-seed official `recloudstream/extensions` / pre-seed a community repo (phisher? — unlicensed, GPL-mixed). If pre-seeded: NSFW filter default? | **zero defaults** — matches our D-043 aniyomi posture AND CS3's own empty `PREBUILT_REPOSITORIES`; keeps content-liability posture symmetric | YES — Phase 1 (repo client ships empty) | doc 16 §6, §11-3; doc 18 §7.3, §11 Q6; doc 04 §8; doc 20 G5 |
| **G6** | **Dev-data wipe confirmation.** Doc 17 §9 re-keys downloads/cache/notifications/content axes destructively (one-time uninstall→reinstall; no `.sqm` ever). | accept the one-time wipe / defer the schema pass | **accept** — CORE_RULES §30's default for debug builds is yes; wipe happens ONCE, in Phase 1 | YES — Phase 1 (schema pass) | doc 17 §9, §11-3; CORE_RULES §30; doc 20 G6 |

### 2.2 Secondary gates (same decision session; each has a recommendation; none reshape the program)

| ID | Decision | `[recommendation]` | Blocking? | Source |
|---|---|---|---|---|
| G7 | **jsoup version strategy** — CS3 wants 1.22.1; we pin 1.19.1 for aniyomi-ext binary compat | resolve-and-regress: bump to 1.22.1 in the Phase 0 spike, run an aniyomi-extension regression pass; if broken, force 1.19.1 and verify the vendored library tolerates it (Phase 0 exit criterion) | no (Phase 0 exit) | doc 16 §1.2, §10-R3, §11-5; doc 20 G7 |
| G8 | **gson shim** for the 13/80 plugins that import gson | **ship it** — one dependency, unblocks 16% of real plugins | no (Phase 1 hostcompat) | doc 16 §1.4, §11-7; doc 20 G8 |
| G9 | **Trust model** — repo-add = consent + sha256, no per-plugin trust gate | **confirm CS3-style** — nothing to verify a plugin hash against: `.cs3` has no signature | no (Phase 1) | doc 16 §3.1, §11-8; doc 02 §1.1; doc 20 G9 |
| G10 | **Plugin settings UI in v1** | **skip** — 58/58 census providers expose none; gear hidden; hostcompat `DataStore` shim still ships so the 7/80 DataStore users keep browsing; hybrid Fragment host is v2-only if demanded | no (Phase 5 confirm) | doc 16 §7, §11-6; doc 11 §6, §8; doc 18 §7.4, §11 Q10; doc 20 G10 |
| G11 | **minSdk guard + desugaring** — NewPipeExtractor needs NIO desugaring app-wide; Jackson 2.13.1 is minSdk-24-safe | **confirm we never intend minSdk < 24 and accept app-wide core-library desugaring** | no (Phase 0/1) | doc 16 §1.2, §11-10; doc 20 G11 |
| G12 | **Library placement for cloud favorites** | auto-created **"Cloud" category** as default landing (user can move anywhere) vs mixed into Default | no (Phase 3) | doc 17 §7, §11-2; doc 18 §6, §11 Q9; doc 20 G12 |
| G13 | **Light vs full v1** — is Phase 3 (search/library/favorites) mandatory for v1 or can it trail Phase 2? | roadmap treats **Phase 3 as in-v1** (favorites are core to how the user uses the app) — but doc 18 asks the user explicitly | no (scope) | doc 18 §11 Q4; doc 20 G13 |
| G14 | **Branch discipline** — the `streaming/CLOUDSTREAM` branch exists on `main` awaiting instructions; Phase 0+ lands there (or a throwaway `spike/cs3-port` for Phase 0) | **ask at gate time; do not start until instructed** (SESSION.md explicit; workflow.md branch discipline) | YES — first commit | SESSION.md; workflow.md; doc 20 G14 |

### 2.3 Additional user decisions promoted from docs 16–19 §11s (non-blocking; each asked in ≥1 doc but never gated — answer by the named phase)

| ID | Decision | Options | `[recommendation]` | Answer by | Source |
|---|---|---|---|---|---|
| G15 | **Download folder layout for CS3 content** (asked twice — one row) | flat `<title>/` (our current scheme) vs TvType prefixes (`Movie/…`, `TV Series/…`, CS3's `getFolderPrefix`) | flat `<title>/` — consistent with existing downloads; TvType prefixes add a dimension users must know | Phase 4 | doc 17 §8.4, §11-10; doc 19 §11-7 |
| G16 | **Anime-on-CS3** — a CS3 provider serving `TvType.Anime`: treat as anime (attempt AniList auto-link via syncData/SmartMatcher, activate aux engines) or as plain CS3 content? | anime treatment / plain CS3 content | plain CS3 content in v1 (aux engines stay AniList-gated; revisit if a wanted provider serves anime) | Phase 3 (ideally at gate session — affects provider mapping) | doc 17 §5.3–§5.4, §11-11 |
| G17 | **AniList-link backfill** — when a CS3 title gains an AniList link retroactively (syncData), relay all historical progress or start from now? | relay history / start from now | start from now (relay only watch-progress written after linking; historical relay risks wrong-episode mappings on dirty data) | Phase 3 | doc 17 §7, §11-5 |

---

## 3. Risk register (consolidated)

R1–R10 are doc 16 §10's register verbatim (IDs preserved), owners added. R11–R20 surface from docs
17–20. L×I = likelihood × impact.

| ID | Risk | L×I | Mitigation (as planned in the docs) | Owner | Source |
|---|---|---|---|---|---|
| R1 | **GPL-3.0 obligations on ANI-KUTA** | H×H | blocking user decision (G1) BEFORE implementation; if relicensing: LICENSE + vendored-tree LICENSE + PROVENANCE; if not: program halts or reshapes | **user** | doc 16 §9, §10-R1; doc 20 §1 |
| R2 | **Kotlin/toolchain drift** (library Kotlin 2.4/AGP 9.1 vs ours 2.2/8.9) | H×H on artifact route; L×M vendored | vendor + compile with our Kotlin (G2); Phase 0 spike is the first milestone; artifact fallback makes the Kotlin bump an app-wide project | spike (Phase 0) | doc 16 §1.3, §10-R2; doc 20 §2 |
| R3 | **Transitive version collisions** (jsoup 1.19.1↔1.22.1; coroutines/serialization max-wins; OkHttp alpha vs NiceHttp) | M×H | resolve-and-regress (G7): dependency-analysis CI gate + aniyomi smoke suite after bump; force 1.19.1 if 1.22 breaks extensions | spike (Phase 0) | doc 16 §1.2, §10-R3; doc 20 §2.1 #4 |
| R4 | **Plugin quality variance** (sparse providers, hasNext liars, missing/faked years, blocking ops) | H×M | null-tolerant Cloud Screen UI (doc 12 §10 checklist), URL-dedupe + capped pagination, timeouts, opaque label rendering | phase (2–3) | doc 16 §10-R4; doc 12 §9–§10 |
| R5 | **App-module class gaps** (16/80 `CommonActivity`, 13/80 gson, 7/80 `utils.DataStore`) | M×M | hostcompat layer sized by the census; fail-at-invocation not at load; **re-run the binary census before v1 freeze** | architecture (Phase 1) | doc 16 §1.4, §10-R5 |
| R6 | **Silent-failure inheritance** (CS3 toast-and-forget load errors) | M×M | D-295/D-296 per-plugin `Errored` with real reason + Retry/Uninstall from day one | architecture (Phase 1) | doc 16 §3.4, §10-R6; doc 02 §5.3 |
| R7 | **Global mutable state in the library** (APIHolder statics, single `app` client, no dex unload) | M×M | single-writer discipline (doc 16 §8), registry mirror as read model, accept classloader leak, re-vendor if a global needs surgery | architecture | doc 16 §8, §10-R7; doc 13 §3 |
| R8 | **In-process crash/OOM kills the app** (no sandbox — plugins run in-process) | L×H | safe-mode analog (`cs3_last_error` + crash-loop skip banner), per-plugin disable; document the limit honestly | phase (1/5) | doc 16 §3.5, §10-R8; doc 04 §5.2 |
| R9 | **Upstream ecosystem volatility** (extractors break as sites change; `@Prerelease` API drift) | M×M | pinned vendored library (no auto-bumps), user-driven plugin updates only, monitor upstream on re-vendor | user + architecture | doc 16 §10-R9; doc 03 §8 |
| R10 | **APK size / method count** (Rhino + Jackson + NewPipeExtractor + ksoup + ktor + ~97 extractors; both apps ship unminified) | M×L | measure in the Phase 0 spike; R8/ProGuard on the reflection-heavy GPL tree is its own risk — defer; report delta to the user | spike (Phase 0) | doc 16 §10-R10; doc 20 §2.3 |
| R11 | **Destructive schema re-key** — doc 17 §9 re-keys downloads/cache/notifications/content axes; a botched sweep strands dev data | L×H | one-time dev wipe accepted at G6, lands ONCE in Phase 1; `lib/schema.ts` regeneration is a blocking subtask of the first implementation PR; DATABASE.json/dashboard obligations (§24/§25) | phase (1) + user (G6) | doc 17 §9; doc 15 §9; doc 20 WP1.6 |
| R12 | **`source_id` Long→TEXT sweep blast radius** — prefs (bare Longs today), registry, UI all carry the old regime | M×M | single coordinated sweep in WP1.6; schema.ts match asserted in CI; aniyomi regression pass in Phase 1 exit criteria | phase (1) | doc 17 §6.2; doc 14 §8.4, §9.3-Q3 |
| R13 | **MPV header comma-truncation** — `http-header-fields` is a comma-separated list option; a header value containing `, ` (e.g. UA strings) may silently truncate — a PRE-EXISTING bug we must fix before CS3 playback (CS3 links carry Referer/UA headers routinely) | H×M | comma-escaping fix in WP2.4 (before CS3 playback); on-device round-trip assertion (doc 19 §10.4); fallback = comma-strip values + log | phase (2) | doc 19 §2.1b, §11-Q1; doc 20 WP2.4 |
| R14 | **E2E fixture sourcing** — DoramyWorld/Uakino/AnimeJl (the doc 19 §10.3 fixture trio) exist only as SOURCE in our workspace; their compiled `.cs3` must be fetched from build branches or compiled | M×M | Phase 2 prep work-package (CakesTwix remote verified: `github.com/CakesTwix/cloudstream-extensions-uk`); phisher repo remains the install-flow fixture (AllMovieLand verified-integrity) | phase (2 prep) | doc 20 §2.2, §4.3, §13 |
| R15 | **Verification blind spots** — x86_64 emulator ships no libmpv (arm-only `abiFilters`) and the sandbox datacenter IP fails Cloudflare; every playback/cache/download-bytes assertion is device-only | H×M (schedule) | two-tier verification plan (emulator = install→picker→queue states; device = `loadfile` and beyond); user's established device-feedback loop per phase | user + per-phase | doc 19 §10.1–§10.4; `EMU`; doc 20 §9 |
| R16 | **Encrypted-HLS (AES) sources won't cache** — no `EXT-X-KEY` handling in `core/playback-cache` (grep-verified absence) | M×L | no-cache marking for encrypted sources in v1 (recommended); transparent key proxying only if encrypted sources prove common | phase (4) | doc 19 §7.4, §11-Q3 |
| R17 | **Unsupported link classes** — DASH (no demuxer in libmpv), TORRENT/MAGNET, LIVE, DRM; CS3 itself excludes DASH from downloads | M×M (expectation) | "N links hidden (unsupported)" picker footer; explicit non-goals with revival triggers (Media3 player variant); optional DASH prevalence scan (§4b) | architecture | doc 19 §2.5, §9; doc 20 §11-1..5 |
| R18 | **5th bottom-nav pill overflow on 360dp devices** — unmeasured at current pill metrics | M×L | device spike before the Cloud tab merges (doc 18 §12 pre-check); fallback IA options (B/C) kept cheap by design | phase (1/2 UI spike) | doc 18 §1.2, §12 |
| R19 | **Dying mirror hosts are the norm** — sparse, TTL-expiring, CF-walled hosts make playback failure the default case, not the exception | H×M | retry ladder (doc 19 §8.2), pinned re-resolve + manual "Try another link" (v1), cache-origin 403 → re-resolve under same cacheKey, honest error visibility standard | phase (2/4) | doc 19 §8.1–§8.3; doc 12 §9 |
| R20 | **Estimate/scope risk** — 35–50 sessions is a planning aid, not a promise; plugin-ecosystem surprises have historically reshaped scopes mid-phase | M×M | per-phase exit criteria + GO/NO-GO memo (Phase 0), timebox with hard stop at 2× and escalate; non-goals list (doc 20 §11) is a commitment, not a backlog | user + program | doc 20 §2.4, §10 |

**Watch list** (tracked below doc 16's top-10, plus doc 18 §12 pre-checks — small, already-mitigated
or fallback-only risks kept visible so they are recognized if they fire):

| ID | Watch item | Status / planned containment | Owner | Source |
|---|---|---|---|---|
| W1 | WebView-dependent extractors at runtime (headless WebView needed inside the resolve flow) | single `WebViewResolverHost` with LruCache(2) in WP2.4 | phase (2) | doc 16 §10 tail; doc 19 §3.2 #4 |
| W2 | repo.json filename collision between the two ecosystems' repo stores | killed by separated repo clients (`anikuta_cs3_repos` vs `anikuta_extension_repos`) | architecture (Phase 1) | doc 16 §6 |
| W3 | Duplicate provider names across plugins | suffixed `sourceKey`s, never silently dropped | architecture (Phase 1) | doc 16 §3.4 |
| W4 | JitPack availability if the artifact fallback route is ever taken | fallback-only; un-verifiable from clones (→ u4) | spike (conditional) | doc 16 §1.2, §12 |
| W5 | Core-library desugaring requirement (NewPipeExtractor NIO) | accepted at G11 — confirm, don't rediscover | user (G11) | doc 16 §1.2, §11-10 |
| W6 | Coil 3 `NetworkHeaders` exact API shape on our version (poster-headers threading) | port-spike confirmation (→ u10) | spike (Phase 0) | doc 07 §3.5; doc 18 §12 |
| W7 | Shared-element ghost-morph class (D-328: keys must be namespaced or covers morph wrongly) | `cloudCoverKey` namespace from day one in Cloud composables | phase (2) | doc 18 §9.2 |
| W8 | `hasNext`-liar providers looping pagination | URL-dedupe + 3-consecutive-empty-page cap (D-304 analog) | phase (2) | doc 12 §10; doc 20 WP2.2 |
| W9 | Classloader leak on plugin uninstall (no dex unload in the library) | accepted (R7 scope); safe-mode + disable cover the user-facing symptom | architecture | doc 16 §10-R7; doc 13 §3 |

---

## 4. Open questions by theme (the remaining non-gate questions)

Each row: question | options | `[recommendation]` | **must be answered by**. Gate-covered
neighbors (NSFW display → G4, jsoup → G7, plugin settings → G10, subscriptions → 4c) are not
repeated here.

### 4a. Product / UX decisions

| # | Question | Options | `[recommendation]` | By phase | Source |
|---|---|---|---|---|---|
| a1 | **Cloud tab naming + icon** — "Cloud" assumed throughout | "Cloud" / "Stream" / "Discover" / "Video"; cloud glyph vs play-style glyph | keep "Cloud" + cloud glyph (neutral, matches docs) | Phase 2 | doc 18 §11 Q2 |
| a2 | **Search duplicates UX** | per-provider sections with visible duplicates (provider badge) / grouped-by-title merged view (v2) / CS3's round-robin flat merge | per-provider sections — dedup impossible on dirty data (years faked); flat merge NOT recommended | Phase 3 | doc 18 §3.2, §11 Q7; doc 12 §9.3 #4; doc 20 §11-26 |
| a3 | **Drama season grouping default** | flat list (CS3 default — most dramas single-"season") vs always group when `season` ints exist | flat default; D-308 chip selector already handles both | Phase 2 | doc 18 §11 Q12 |
| a4 | **Cloud as cold-start restore tab** — is "cloud" valid in the D-282 `startTab` sanitize set? | allow / deny (falls back to browse) | deny in v1 (dynamic tab may not exist at cold start) | Phase 2 | doc 18 §11 Q3 |
| a5 | **Year/score library sorting for CS3 content** — wanted despite census-level data dirtiness? | want (promote year/score to real columns) / don't (ext-axis JSON only) | don't in v1 — data too dirty to sort honestly; **note: if wanted, columns must land in the Phase 1 schema pass** | **Phase 1 (schema deadline)** | doc 17 §5.1, §11-6; doc 12 §9.3 #4 |
| a6 | **Auto next-mirror on playback error** | auto-advance (CS3-style) / always manual "Try another link" | manual v1 (safer, honest); auto = opt-in later | Phase 2 | doc 19 §8.2 step 4, §11 Q5; doc 20 §11-8 |
| a7 | **Download auto-next-mirror** (auto-downloads falling through mirrors) | pinned re-resolve only / opt-in CS3-style mirror fall-through | pinned only | Phase 4 | doc 19 §6.5, §11 Q6 |
| a8 | **Seasoned key format aesthetics** — `mainId\|S02E00005` in keys vs `S02E05` in filenames | zero-padded keys (sortability) / pretty keys | zero-padded in keys, pretty in filenames (as designed) — confirm only | Phase 1 | doc 17 §3.3, §11-4 |

### 4b. Technical decisions deferred to the spike / device passes

| # | Question | Options | `[recommendation]` | By phase | Source |
|---|---|---|---|---|---|
| b1 | **MPV list-option comma semantics** — do escaped commas in `http-header-fields` survive libmpv's parser? Is today's unescaped default-UA path silently truncating? | pick escape syntax / comma-strip values + log (fallback) | confirm on-device; escape fix must land BEFORE CS3 playback (WP2.4); fallback = strip + log | Phase 0 (device probe) / Phase 2 (fix) | doc 19 §2.1b, §11 Q1; doc 20 WP2.4 |
| b2 | **NiceHttp↔OkHttp alpha resolution** — NiceHttp 0.4.18's OkHttp requirement is unknown from our clones; unanswerable without a real build | resolution strategy emerges from the dependency report | verify in the Phase 0 spike (Gradle resolution + smoke suite) | Phase 0 | doc 16 §1.2; doc 03 §11; doc 20 §2.1 #4 |
| b3 | **Position-preserving link switch ship order** — the `pendingSeekPosition` fix benefits BOTH ecosystems | inside the CS3 PR / separate player PR first | separate PR first (small, benefits anime playback immediately) | Phase 2 | doc 19 §2.3, §11 Q2 |
| b4 | **Encrypted-HLS caching** | no-cache-until-key-proxy / implement transparent key proxying now | no-cache in v1; proxy later only if common | Phase 4 | doc 19 §7.4, §11 Q3 |
| b5 | **Pre-play freshness check** — optional `resolved_url_expires_at` + skip-doomed-plays | add column + check / stay retry-ladder-only | ladder-only in v1 | Phase 4 | doc 17 §4.4; doc 19 §7.2, §11 Q4 |
| b6 | **TTML subtitles** — MPV can't render TTML | pass-through-and-fail-gracefully / add TTML→SRT/VTT converter | pass-through; converter only when a provider we care about emits TTML | Phase 2+ (as encountered) | doc 19 §5.2, §11 Q8 |
| b7 | **Headered audio tracks** — is URL-based `audio-add` enough for `AudioFile.headers`? | URL-based / temp-file treatment when headers set | URL-based until a real provider needs headered audio badly | Phase 2+ | doc 19 §5.4, §11 Q9 |
| b8 | **DASH prevalence scan** — one-off count of DASH-typed links in the 80-plugin corpus to size the future DASH gap | scan (~hours) / skip until asked | optional homework before anyone asks for DASH | any time pre-v1.x | doc 19 §2.5, §11 Q10; doc 20 §11-2 |
| b9 | **Next-episode link pre-warm at 80% watched** (CS3 does this) | add / rely on 20-min cache | no — doubles provider traffic; the 20-min saturated cache covers binges | Phase 5 (confirm) | doc 09 §4.2; doc 19 §8.4, §11 Q11; doc 20 §11-9 |
| b10 | **`CloudStreamVerifier` ownership** | `:data:cloudstream` (plugin-API-adjacent) / neutral hook interface in `:core:download` so aniyomi could grow an analog | `:data:cloudstream` | Phase 4 | doc 19 §4.2, §11 Q12 |
| b11 | **schema.ts generator script** — build one or accept manual transcription discipline? | build (~1 day, kills the drift class) / manual | build, in Phase 5 debt pass (schema.ts regen itself is Phase 1-blocking regardless) | Phase 5 | doc 17 §9.3, §11-8; doc 15 §9.1; doc 20 WP5.7 |

### 4c. Scope decisions

| # | Question | Options | `[recommendation]` | By phase | Source |
|---|---|---|---|---|---|
| c1 | **Subscriptions/notifications in v1?** — `cs3_subscription_state` table lands in Phase 1; the 6-hour poll + `episode_update` feed + "New" badges are Phase 5 | ship poll + feed in v1 / defer to v1.x | defer unless the user wants cloud-title update notifications — 0/58 census providers send `nextAiring`; schedule stays AniList-only | Phase 5 (WP5.5 is defer-able by design) | doc 17 §5.3–§5.4; doc 13 §6.2; doc 20 WP5.5 |
| c2 | **Cross-provider matching ceiling** | manual link only / build exact-syncData-id suggestion chips (v1.5) | manual-only v1; chips v1.5 (fuzzy title+year matching rejected — years are faked in the wild) | v1.5 | doc 17 §2.4, §11-1; doc 20 §11-19 |
| c3 | **CS3 `nextAiring` → Schedule tab?** | details-page countdown only / feed `episode_schedule` | details-only in v1 (schedule tab is AniList-only by design) | Phase 5 | doc 17 §5.4, §11-7; doc 20 §11-25 |
| c4 | **Aniyomi-side `LIB_MAX` alignment** — `ExtensionRepoApi` LIB_MAX=16.0 vs `ExtensionLoader` 17.0 (D-297 split-brain). CS3 side is settled (no libVersion analog — `apiVersion` dead at runtime), but the aniyomi-side bump was never decided | bump repo filter to 17.0 / leave split | bump to 17.0 in Phase 1 (one line + regression pass) — outside CS3 scope, same PR window `[new — B5-c: promoted from doc 14's Batch-4 leftovers]` | Phase 1 (or independently) | doc 14 §4.3, §9.3-Q5; doc 16 §3.2 |
| c5 | **Hot-reload dev tooling** (`deployWithAdb`-style loop for our own future CS3 plugins) | defer / build | defer — nice-to-have | post-v1 | doc 16 §11-9; doc 02 §8; doc 20 §11-17 |

### 4d. Nice-to-haves / deferred (revival triggers live in doc 20 §11)

| # | Deferred item | Revival trigger | Source |
|---|---|---|---|
| d1 | **Unified search** (anime + cloud in one query; registry step 6) | user asks after living with the Cloud tab — "possibly never" | doc 16 §5.3 step 6; doc 20 §11-11 |
| d2 | **Cross-world actions** — "Search this anime in Cloud" on anime details; anime⇄cloud deep links | v2 idea; bottom bar is the only switch in v1 | doc 18 §1.2, §11 Q8; doc 20 §11-30 |
| d3 | **TvType color-coded accents** on cards/pills | after runtime-type drift is measured | doc 18 §9.3, §11 Q11; doc 20 §11-29 |
| d4 | **Chromecast / live TV / DASH / DRM / torrent playback** | DASH+DRM+live: a Media3-based `IPlayer` variant; torrent: user demand + legal review | doc 19 §2.5, §9; doc 09 §8–§9; doc 20 §11-1..5 |
| d5 | **CS3 online-subtitle providers** (OpenSubtitles et al.) | post-v1 feature, separate subsystem | doc 09 §3.3; doc 19 §9; doc 20 §11-31 |
| d6 | **Plugin-settings Fragment host** (hybrid DSL/Fragment, `requiresResources` wiring, theming compat) | v2 only if a provider the user cares about ships settings | doc 11 §8; doc 16 §7; doc 18 §7.4; doc 20 §11-15 |
| d7 | **quickSearch, metaproviders, `getVideoInterceptor`, deep links, hot-reload** | as named in the non-goals list | doc 20 §11 (items 12–13, 17, 27) |

**Already answered by later docs — do not re-ask** (consolidation note): doc 17 §11-9 (HLS resume
granularity) → **answered** by doc 19 §6.3: sidecar pause/resume only, no `extraInfo` column needed
(doc 20 WP4.5). Doc 14 §9.3 Q1–Q4, Q6 (Batch-4 seam questions) → **answered** by doc 16 §4–§5 +
doc 17 §6: additive `SourceVideo` fields + `install(handle)` rename, `source_key TEXT`
ecosystem-qualified keys, Cloud Screen ships as the seam's first consumer (steps 1–4), two-step
`load()`/`loadLinks` maps to `fetchContentDetails`/`fetchVideoList`. Only doc 14 Q5's aniyomi-side
half survives (→ 4c-c4).

### 4e. Traceability — where every source question landed

Nothing dropped: every `[open-question]` from the corpus maps to exactly one row above (or is
marked resolved). G## = §2 gate · a#/b#/c#/d# = §4 theme row · u# = §5.

| Source | → Destination |
|---|---|
| doc 16 §11-0…10 | 0→**G1** · 1→**G3** · 2→**G2** · 3→**G5** · 4→**G4** · 5→**G7** · 6→**G10** · 7→**G8** · 8→**G9** · 9→c5 · 10→**G11** |
| doc 17 §11-1…12 | 1→c2 · 2→**G12** · 3→**G6** · 4→a8 · 5→**G17** · 6→a5 · 7→c3 · 8→b11 · 9→**resolved** (doc 19 §6.3) · 10→**G15** · 11→**G16** · 12→**G4** |
| doc 18 §11 Q1…Q12 | Q1→**G3** · Q2→a1 · Q3→**G3** + a4 · Q4→**G13** · Q5→**G4** · Q6→**G5** · Q7→a2 · Q8→d2 · Q9→**G12** · Q10→**G10** · Q11→d3 · Q12→a3 |
| doc 19 §11 Q1…Q12 | Q1→b1 (+R13) · Q2→b3 · Q3→b4 (+R16) · Q4→b5 · Q5→a6 · Q6→a7 · Q7→**G15** · Q8→b6 · Q9→b7 · Q10→b8 · Q11→b9 · Q12→b10 |
| doc 14 §9.3 Q1–Q6 | Q1–Q4, Q6→**resolved** (docs 16–17) · Q5→c4 |
| doc 11 §8 (3 inline) | settings host / DSL-vs-Fragments / openSettings shape → **G10** + d6 |
| doc 10 §9, doc 13 §12 | inference-only items → §5 (u14) |
| doc 12 §9–§10 | not questions but risk evidence → R4, R19, W8, a5, a2 |

---

## 5. Unverified / uncertain knowledge (what we know we don't know)

From the docs' "unverified" sections + B5-a/B5-b verification cautions. None of this blocks the
gate session; ALL of the Phase 0 column must be re-verified at spike time.

| # | What we don't know | Why it's uncertain | Re-verify at | Source |
|---|---|---|---|---|
| u1 | **`/tmp/cs-gradle` citations are ephemeral** — doc 03/04's gradle-plugin claims cite a temp-dir clone that may vanish | temp directory outside the repo; may already be gone | Phase 0 (re-fetch `recloudstream/gradle` if citations are needed) | B5-a verification note (worklog 2026-08-29) |
| u2 | **Phisher `plugins.json` censuses drift** — all 80-plugin counts (status, tvTypes, languages, fileHash) are correct only as of the 2026-08-29 clone | the repo updates continuously | Phase 0/1 fixture prep (re-pull before relying on counts) | B5-a note; doc 16 §1.4; doc 20 §2.2 |
| u3 | **Census frame: "58" vs 61** — docs 16–20 use 58 census providers; repo-wide (incl. extensions' YouTube/Invidious/InternetArchive) it is 61; doc 12 §1's `[inferred]` rows were spot- not exhaustively re-counted | annotation added by B5-b; substance unaffected | v1 freeze (re-run census, also covers R5) | B5-b note (doc 12); doc 16 §10-R5 |
| u4 | **Upstream repo/tag behind `com.lagradost:cloudstream3:pre-release`** — not verifiable from our clones; needs a live JitPack fetch | snapshot-only workspace | Phase 0 (only matters if the artifact fallback is taken) | doc 16 §1.2, §12 |
| u5 | **NiceHttp 0.4.18's OkHttp requirement** — unknown from clones | NiceHttp sources not mirrored | Phase 0 spike (dependency-resolution report) | doc 16 §1.2, §12; doc 03 §11 |
| u6 | **Compile-ability of the vendored library under OUR Kotlin 2.2.0/AGP 8.9.1** — THE spike headline question; the whole vendoring recommendation rests on it | never built; metadata trap is real (library built at Kotlin 2.4) | **Phase 0 — first task** (GO/NO-GO) | doc 16 §1.3, §12; doc 20 §2.1 |
| u7 | **Kotlin one-minor metadata tolerance** as stated (standard policy, unconfirmed for our exact pair) | policy claim, not measured | Phase 0 (or first artifact build) | doc 16 §12 |
| u8 | **APK size + method-count delta** (R10) — unmeasured | no build exists | Phase 0 spike (report to user) | doc 16 §10-R10; doc 20 §2.3 |
| u9 | **5-pill fit on 360dp devices** at current pill metrics | never rendered | Phase 1 UI device spike (before Cloud tab merge) | doc 18 §12 |
| u10 | **Coil 3 `NetworkHeaders` exact API shape** on our version (poster headers threading) | API shape assumed 1:1 from doc 07 | Phase 0 port spike | doc 07 §3.5; doc 18 §12 |
| u11 | **MPV comma-escaping behavior** — never observed on device; today's default-UA path may already be silently truncating | runtime-only question | Phase 0/2 device pass (b1) | doc 19 §2.1b, §11 Q1 |
| u12 | **CakesTwix build-branch `repo.json` URL** for fixture sourcing — exact URL unconfirmed | remote verified, build-branch layout not | Phase 2 prep | doc 20 §4.3, §13 |
| u13 | **Sandbox/emulator reachability of fixture hosts** (phisher hosts; CakesTwix Ukrainian CDNs — CF-wall status unknown) | datacenter IP constraints | Phase 0/1 (`getent hosts` probe per `EMU` 4.5) | doc 19 §10.3; doc 20 §13 |
| u14 | **All plan-doc runtime behavior is inferred, not observed** — docs 16–19 are `[design]`/`[recommendation]` sketches: nothing built, rendered, or device-tested; doc 11's on-device settings behavior (fragment crashes after unload, gear on TV) is reasoned from source; doc 13's vestigial keys (`KEY_RESULT_SORT`), cloned-site order-dependence, and plugin-writes-to-watch-keys are `[inferred]`; doc 10's edge behaviors (NsfwOnly+enableAdult interplay, "All" tvTypes sentinel, zero-provider chip dialog) reasoned not run | static source reading only | every phase's exit criteria (the two-tier plan is the answer) | docs 10 §9, 11 §9, 13 §12, 16–19 §12 |
| u15 | **Historical preference DSL** (doc 03 §5.1: a 4.0-era plugin prefs API was removed) — cannot date or quote it: our clone is shallow (`git rev-list --count HEAD` = 1) | no history in snapshot | only if plugin-settings hosting revives (d6) | doc 11 §9 |
| u16 | **Upstream repos we don't mirror may expose settings plugins** — our 6-repo × 57/58-plugin census found none, but that's not the whole ecosystem | census bounded to mirrored repos | d6's revival trigger (re-check then) | doc 11 §9 `[gap]` |
| u17 | **All effort numbers (35–50 sessions, per-phase sizes) are `[plan]` estimates**, not measurements | no build data | each phase's actuals vs. estimate (R20) | doc 20 §10 |

---

## 6. The single-page summary (if you read nothing else)

**The five decisions that matter most, in order — all blocking, all with a ready recommendation:**

1. **G1 — Relicense ANI-KUTA GPL-3.0?** → **Yes.** CS3's library is GPL-3.0 with no separate
   library license; any route ships GPL code. Relicense now while it's cheap; without a yes, the
   program does not start. (doc 16 §9)
2. **G2 — Vendor the CS3 library source into our repo?** → **Yes** (`:external:cloudstream3`,
   ~1.6 MB, pinned @ efc1915) — the only route that dodges the Kotlin 2.4 metadata trap with a
   reproducible classloader contract. (doc 16 §1.7)
3. **G3 — Separate, dynamic "Cloud" tab?** → **Yes** — 5th bottom-nav pill appearing only when a
   CS3 provider is installed; zero blast radius on anime flows. (doc 18 §1.2)
4. **G4 — NSFW policy?** → **One master switch, default OFF**, badge-only inside mixed providers,
   and don't save NSFW watch progress. (doc 10 §8.5)
5. **G5 — Zero default repositories?** → **Yes** — matches our aniyomi posture, CS3's own posture,
   and keeps content liability symmetric. (doc 16 §6)

Then: run one gate session over §2 (14 doc-20 gates + 3 promoted = 17 rows, each ~2 minutes with
the recommendations), record D-NNNs in decisions.md, green-light the `spike/cs3-port` branch (G14),
and let Phase 0 retire R2/R3 (the two risks that could reshape everything) before a line of real
code exists.

**And the three risks to keep in mind while it's built** (§3):

1. **R1 GPL-3.0** — the only H×H risk with no engineering mitigation; it is decided, not managed,
   at G1.
2. **R2/R3 toolchain + collisions** — retired (or escalated) by the Phase 0 spike; that is the
   spike's entire purpose.
3. **R15 verification blind spots** — the emulator can never play video (arm-only libmpv) and the
   sandbox IP fails Cloudflare; expect every playback claim to wait on the user's device passes —
   this is the program's pacing reality, not a defect.

---

## 7. Verification status (this doc's own evidence)

- **Read in full**: doc 20 §1 (G1–G14), doc 16 §9–§11, doc 17 §11, doc 18 §11, doc 19 §11, plus
  the inline `[open-question]` markers of docs 10–14 and 16–19 (grep: 111 occurrences across 10
  files) and the "unverified/open questions" sections of docs 10, 11, 13, 16, 18, 20.
- **Consolidation method**: doc 20's G1–G14 kept verbatim-in-substance with source citations
  expanded to the originating §11 items; every doc 16–19 §11 question mapped to a gate, a theme
  row, a "resolved by later docs" note, or doc 20 §11's non-goals; doc 14 §9.3's Batch-4 questions
  checked against docs 16–17 for resolution (5 of 6 resolved; the aniyomi-side LIB_MAX half
  promoted as 4c-c4, marked `[new — B5-c]`). Risks R1–R10 copied from doc 16 §10 with IDs
  preserved; R11–R20 extracted from docs 17–19 §design/§8/§10 and doc 20 §2/§10.
- **Additions marked**: exactly two `[new — B5-c]` items (4c-c4 LIB_MAX promotion; the §5
  re-verification framing). No other new questions, risks, or recommendations were invented.
- B5-a/B5-b cautions (u1–u3) are quoted from the worklog verification entries of 2026-08-29.
- This doc made **no code changes, no commits** — only this file + the worklog entry.

*End of doc 21. Inputs: docs 00–20 (research + plans 16–20 normative, verification notes B5-a/B5-b).
Next consumer: the user's gate session (§2) and the Phase 0 spike's re-verification list (§5).*
