# 17 — Integration Data Layer (CS3 Adoption Plan)

> **Mission (B4-b)**: THE data-layer plan for adding CloudStream (CS3) as a second extension
> ecosystem. Doc 15 §8 ranked the **5 hardest schema problems**; this doc **solves each of them**
> (§2–§6), then covers library (§7), downloads/cache (§8), the concrete schema-change list
> (§9), scope cuts (§10), and open questions (§11). Companion docs: **16** (integration
> architecture — module layout, provider bridge), **18** (Cloud Screen UI), **19** (playback/downloads
> runtime), **20** (roadmap). Everything here is a **plan** — no code has been written.
>
> **Ground truth inputs**: doc 15 (our schema, verified in full — all `.sq` citations below were
> re-read for this doc), doc 05 §3–4/§10–11 (CS3 models + our-model gaps), doc 13 §4–6 (CS3
> persistence), doc 14 §6/§8/§9 (our provider seam + constraints), doc 09 §5 (CS3 downloads),
> doc 08 §4.6 (extractorData), doc 07 §5/§7 (CS3 metadata persistence + gaps), doc 10 (TvType
> taxonomy), doc 12 §9 (field-usage census). `CORE_RULES.md:491-507` (§30) governs migration
> policy. Markers: **[design]** = proposed schema/code (sketch), **[recommendation]** = chosen
> option among alternatives, **[open-question]** = needs the user, **[verified]/[inferred]**
> inherit doc-15 verification unless a fresh file:line is given.

---

## 0. Executive summary — 5 problems, 5 solutions

| # | Problem (doc 15 §8) | Solution (this doc) | Section |
|---|---|---|---|
| P1 | Single-slot extension axis — one ext link per content | NEW `content_source_link` table (N links, one ACTIVE); `content_details.ext_*` stays as the active-source projection | §2 |
| P2 | Global-numbering assumptions break CS3 seasons | **Season-qualified canonical episode key**: `"mainId\|%05d"` (season-0/global, byte-compatible today) + `"mainId\|S02E00005"` (seasoned); `data_cache_episode` UNIQUE gains `season_number` | §3 |
| P3 | Aniyomi-shaped `ResolveContext`/`ReResolver`/cache ids | Polymorphic sealed `ResolveContext` with `ecosystem` discriminator; CS3 variant carries `(providerKey, contentUrl, episodeData, linkLabel, quality)`; `source_key TEXT` replaces Long `source_id` | §4 |
| P4 | Aux engines keyed to the AniList axis | CS3 metadata in the ext axis + `ext_extra_json` (one new column: `ext_poster_headers`); NEW `cs3_subscription_state` poll table feeding the shared `episode_update` feed; AniList-only engines get `hasAnilistLink` guards | §5 |
| P5 | Long-typed source ids everywhere | Ecosystem-qualified string keys `"cloudstream:<providerName>"` (= provider-api `Source.key`, doc 14 §6.3) through schema + prefs + registry; the two live episode-key regimes unify on the canonical key | §6 |

**The one-line spine**: keep `main_id` as the only identity everything hangs off (doc 15 §2 —
it already guarantees progress never orphans), make every *source-shaped* slot
ecosystem-qualified TEXT, and make every *episode-shaped* slot season-qualified. §30 gives us
destructive freedom to do it in one shot (doc 15 §7).

---

## 1. Design goals

1. **Content identity must survive source switches.** Non-negotiable, already true today:
   every user table FKs `main_id` (the stable UUID PK), and `content_id` is regenerated — never
   migrated — on switch (doc 15 §2.1, `ContentResolver.kt:13-14,67,157`). The CS3 design must
   *add* links without touching that invariant. `watch_progress`, `library_item`,
   `downloaded_episode`, ratings, `episode_update` rows must never orphan when a user moves a
   title between providers or ecosystems.
2. **Progress must never orphan — and must never collapse.** The D-313 lesson
   (`AGENT-CONTEXT/memory/decisions.md:2412-2440`): `data_cache_episode` keyed
   `(main_id, episode_number)` with INSERT OR REPLACE silently **deleted data** when an
   extension returned duplicate numbers. Any new key scheme must make that collapse
   structurally impossible (§3.4), not just guarded-in-code.
3. **CS3 content (movies / TV series / Asian drama) coexists with anime as a first-class
   citizen.** Doc 15 §8.2/§8.3: the rows are already type-agnostic (`main_entry.content_type`
   free TEXT; AniList id nullable; `resolveOrCreateForExtension` creates extension-only rows
   today, `ContentResolver.kt:173-189`). The plan keeps ONE schema for both — no parallel
   "cloud table" world.
4. **Debug-build schema freedom — destructive migrations are OK.** CORE_RULES §30
   (`CORE_RULES.md:491-507`): debug-only, no production users, "complete schema changes
   without migration concerns… Existing dev-install data will be wiped — that's acceptable";
   doc 15 §7 verified the mechanism (no `.sqm`, version pinned at 1, idempotent
   `hasColumn`-guarded `onOpen` shim that is explicitly "NOT a migration system"). **This doc
   assumes we exercise that freedom once, wholesale** (§9) rather than stacking compat shims.
   Only hygiene: idempotent guards so old dev installs don't crash on open, §24 doc updates,
   §25 dashboard updates (doc 15 §7 "Implication for CS3 work").
5. **Lean by default.** Doc 12 §9.2's census: the median real provider uses ~30% of the CS3
   field surface (actors 1/58 providers, trailers 1/58, `nextAiring` 0/58, `seasonNames` 0/58).
   We store what at least one deep-dived provider actually emits + what our UI renders in v1
   (§10 lists the cuts).
6. **Don't adopt CS3's persistence weaknesses.** CS3 keys everything by a 32-bit
   `String.hashCode()` of the provider-relative URL (`doc 05 §10.4`, `doc 13 §4.4`) and stores
   full DTO snapshots that go stale (`doc 13 §5.1`, `[gap → doc 15]` note). We keep our
   normalized identity layer and add provider-scoped caches only where they earn their keep
   (doc 15 §8.9/§8.11 verdicts: [fits] / we reject theirs).

---

## 2. P1 — Multi-source identity: `content_source_link`

### 2.1 The problem restated

`content_details` holds exactly ONE extension axis (`extension_type, extension_id TEXT,
source_id INTEGER, anime_url`, `content.sq:110-122`) and `main_entry` denormalizes the same
link for the hot `getMainEntryByExtension(extension_id, anime_url)` lookup
(`content.sq:45-47,191-194`). Doc 15 §8.1 verdict: rows are ready, the **single slot is the
gap** — "CS3 users expect 'open in another provider' for the same show". The Phase-2
`external_reference(uid, ecosystem, source_id, external_id, confidence, is_user_confirmed)`
design was never built (`17-database-schema.md:103-148` plan-only; `18-phase3-plan.md:28`
admits it; doc 15 §2.5).

### 2.2 The chosen design — link registry + active-axis projection

**[design] New table `content_source_link`** (in a new `contentSourceLink.sq` or appended to
`content.sq`):

```sql
-- [design] CREATE TABLE sketch — one row per (content × provider page).
CREATE TABLE IF NOT EXISTS content_source_link (
    id                INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    main_id           TEXT NOT NULL,
    ecosystem         TEXT NOT NULL,            -- 'aniyomi' | 'cloudstream' | 'sora' | 'mangayomi'
    source_key        TEXT NOT NULL,            -- ecosystem-qualified: 'cloudstream:Uakino' / 'aniyomi:4697393375201558791' (§6)
    external_url      TEXT NOT NULL,            -- the provider page url (CS3 url / aniyomi anime_url)
    display_name      TEXT NOT NULL,            -- provider display name for the source-switch sheet
    is_active         INTEGER NOT NULL DEFAULT 0,   -- exactly ONE active row per main_id (partial unique index)
    is_user_confirmed INTEGER NOT NULL DEFAULT 0,   -- manual links are authoritative (from the Phase-2 plan, doc 15 §2.5)
    link_origin       TEXT NOT NULL DEFAULT 'search', -- 'search' | 'manual' | 'syncdata' | 'recommendation'
    confidence        REAL,                     -- future fuzzy-match score (unused v1)
    metadata_json     TEXT,                     -- per-link display snapshot: posterHeaders, posterUrl, lang (§5.2)
    linked_at         INTEGER NOT NULL,
    last_verified_at  INTEGER,
    FOREIGN KEY (main_id) REFERENCES main_entry(main_id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_csl_unique
    ON content_source_link(ecosystem, source_key, external_url);   -- same provider page links at most once
CREATE UNIQUE INDEX IF NOT EXISTS idx_csl_one_active
    ON content_source_link(main_id) WHERE is_active = 1;           -- DB-enforced single active source
CREATE INDEX IF NOT EXISTS idx_csl_main ON content_source_link(main_id);
```

**How the two-axis `content_details` survives** [design]: unchanged in role — the `ext_*`
columns remain the **projection of the ACTIVE link** (the metadata the details screen shows
today). Adding a link = INSERT `content_source_link` (is_active=0). *Switching* the active
source = `UPDATE … SET is_active=0 WHERE main_id=?` + `SET is_active=1 WHERE id=?` +
re-run the existing `updateExtensionAxis` + `updateMainEntrySources` + `contentId`
regeneration in one transaction (the exact `linkExtensionToExisting` flow, doc 15 §2.3 — we
reuse it, we just call it N times). Unlink = deactivate the row + `clearExtensionAxis`
(`content.sq:320-334`). This keeps **every existing code path working** (hot lookup, axis
switch/unlink, D-285 batch reads) and adds only the registry underneath.

Why a child table instead of widening the axis into an M:N `content_details` row set: doc 15
§8.1 already framed the two options; a child table leaves the 1:1 `content_details` contract
(integral to ~20 queries, doc 15 §1.1) untouched, and the single-active partial unique index
enforces the "one displayed source" invariant in the DB rather than in code.
**[recommendation]** revive-as-`content_source_link` over "accept single-active-source for v1"
— the single-source option pushes the N-links problem into the UI layer with no schema
backing, and the table is small.

### 2.3 Linking for CS3 content with no AniList id

Doc 15 §8.3 [fits]: `resolveOrCreateForExtension` (`ContentResolver.kt:123-219`) already
creates extension-only rows (data axis NULL). For CS3, the same function gains a CS3 entry
point: **[design] `resolveOrCreateForCloudStream(providerKey, contentUrl, title)`** → INSERT
`main_entry` (`system_id` = the pre-seeded `cloudstream` row, `ContentRepository.kt:59-66`) +
empty `content_details` + first `content_source_link` row (is_active=1). The AniList axis is
linked later, exactly like today, if ever. Note the free enrichment path: a CS3
`LoadResponse.syncData` that carries an AniList id (helpers `getAniListId` etc., doc 05 §3.4)
can drive `linkAniList` retroactively — doc 15 §8.12 [fits].

### 2.4 The same show on two CS3 providers — matching strategy

CS3 has **no canonical cross-provider id**: content identity per provider is `url + apiName`
(doc 05 §10.4 — `getLoadResponseIdFromUrl` is a 32-bit hash, deliberately rejected by doc 15
§8.11). Options considered:

| Strategy | Mechanism | Verdict |
|---|---|---|
| (a) Manual link only | user picks "Link another source" on the details page (our `ManualLinkSheet` precedent, doc 14 §7.3) | **[recommendation] v1 behavior** — zero false merges |
| (b) Exact external-id match | compare `syncData` TMDb/IMDb/MAL/AniList ids across candidate rows (CS3's own `checkAndWarnDuplicates` does this — `ResultViewModel2.kt:999-1079`, doc 13 §5.1) | **[design] v1.5 assist** — surface "Possible match: same TMDb id" as a *suggestion chip*, never auto-merge |
| (c) Fuzzy title+year | normalized-name + year-compat matching | **rejected for auto-merge** — doc 12 §9.3 #4 shows years are missing or *faked* (UAK:132 hardcodes 2023); CS3 itself makes this a user-confirmation dialog, not an automatic merge (doc 13 §5.1) |

Even CS3 — the ecosystem whose UX this feature comes from — implements cross-provider dedup as
a **user-driven dialog**. **[recommendation]** We match that: manual primary, exact-id assist
later, no silent fuzzy merging. The `confidence`/`is_user_confirmed` columns are carried from
the Phase-2 plan (doc 15 §2.5) so (b) can land without another schema pass.

### 2.5 Anime spanning both ecosystems

An anime found via an aniyomi source AND a CS3 provider is just two `content_source_link`
rows with different `ecosystem` values. Switching ecosystems re-runs the same axis projection.
The AniList data axis is shared by both (that is the point of the two-axis design, doc 15
§1.1). Watch progress keys off `main_id` and is therefore ecosystem-agnostic by construction
(doc 15 §2.4 walkthrough). **[open-question]** whether an *episode-level* mapping between an
aniyomi global-numbered list and a CS3 season-qualified list should ever be attempted — v1
says no (progress keys are per-source-format; see §3.5 caveat).

---

## 3. P2 — Season-aware episode identity

### 3.1 The four places global numbering is baked in

1. `watch_progress.episode_key` PK = `"${mainId}|%05d"` (watch.sq:110-113 comment; written by
   `WatchScreen.kt:2115-2125`, `DetailsViewModel.kt:3051`; doc 15 §1.4).
2. `data_cache_episode` UNIQUE `(main_id, episode_number)` (`dataCache.sq:44`) — **the exact
   D-313 data-loss bug site** (INSERT OR REPLACE collapsed duplicate numbers,
   `EpisodeListNormalizer.kt:23-26` documents it).
3. Downloads: `download_queue` / `downloaded_episode` UNIQUE `(main_id, episode_key)` where
   `episode_key = SEpisode.url` (doc 15 §1.5, real data in `DATABASE.json` shows the full
   composite URL, doc 15 §6.1) — **regime #2, already divergent from watch keys**.
4. File naming `"${title} - E00001.mp4"` (`DownloadStorageProvider.kt:172-176`,
   `:772-776` fractional variant), re-derived by the reinstall scanner
   (`DownloadScanner.kt:189`, `deriveEpisodeKey :542`); plus the playback-cache identity hash
   `sha256("$mainId\u001F$episodeNumber\u001F$sourceId\u001F$serverKey")` (`playbackCache.sq:4-7`).

What CS3 brings (doc 05 §4.1-4.2): flat `List<Episode>` with **nullable** `season: Int?` and
`episode: Int?`; null season → grouped as season 0 (`ResultViewModel2.kt:2225`), null episode
→ list position +1 (`:2186,2243`); seasons may be "something random to fit multiple seasons
into one" (`MA:2370-2378`); the `SeasonData` overlay adds `displaySeason` (naming only). And
doc 15 §8.5: two seasons' "episode 1" cannot both live under a `(main_id, episode_number)`
UNIQUE — the D-313 collapse, guaranteed, on every multi-season show.

### 3.2 Options considered

| Option | Description | Verdict |
|---|---|---|
| A. Re-key everything `(main_id, season, episode)` — prefix anime too (`"mainId\|S01E00001"`) | uniform, but churns every anime key/parse (AniList relay SQL, history parser, prefs `playback_state_<key>`, data.json) for zero anime benefit | rejected — gratuitous churn (§30 makes it *possible*, not wise) |
| B. Global numbering only (flatten CS3 seasons like D-317 does) | keeps all constraints; but the global is OUR fabrication — positional globals shift when a provider inserts a season earlier (identity churn = silent progress orphaning); CS3's own positional id trick (`season*100_000+episode`, doc 05 §4.2) leaks into filenames (`E100001`) | rejected as primary |
| C. **Two-format canonical key, dispatched by season presence** | `"${mainId}\|%05d"` when the episode lives in season 0 / global mode (byte-compatible with ALL existing anime rows); `"${mainId}\|S%02dE%05d"` when season ≥ 1 (season + episode-in-season = the provider-native, stable identity) | **[recommendation]** |

### 3.3 The recommended scheme, precisely

**[design] One key builder** (`EpisodeKeys`, to live in `:core:content`):

```kotlin
// [design sketch]
object EpisodeKeys {
    /** Season 0 / global (anime, movies, single-season CS3): "mainId|00001" — today's exact format. */
    fun global(mainId: String, number: Int): String = "$mainId|${"%05d".format(number)}"
    /** Seasoned (CS3 multi-season): "mainId|S02E00005". Season ≥ 1, number = episode WITHIN season. */
    fun seasoned(mainId: String, season: Int, epInSeason: Int): String =
        "$mainId|S${"%02d".format(season)}E${"%05d".format(epInSeason)}"
    /** Parse: segment after '|'. Starts with 'S' → seasoned (season, epInSeason); else global number. */
    fun parse(key: String): ParsedEpisodeKey
}
```

Properties that make it work:

- **Back-compat for free**: every existing anime `watch_progress` /
  `playback_state_<key>` / `data.json` key is already in global format — zero migration of
  *data semantics* (§30 covers the destructive column changes in §9).
- **The AniList relay SQL cannot misparse**: `getHighestWatchedEpisodeNumber` does
  `MAX(CAST(SUBSTR(episode_key, INSTR(episode_key,'|')+1) AS INTEGER))` (watch.sq:114-121) —
  SQLite `CAST('S02E00005' AS INTEGER)` = **0** (non-numeric leading char), so seasoned keys
  never pollute the anime progress relay. We still add an explicit
  `WHERE … data axis is anilist` guard at the caller (§5.4) — defense in depth.
- **Uniqueness is structural**: within one season bucket, D-317's `analyzeEpisodeSeasons`
  already guarantees "per-season display numbers, guaranteed unique within a bucket"
  (decisions.md D-317); CS3's null-episode fallback (list position +1, doc 05 §4.1) is applied
  per-season before keying.
- **SeasonDetector interplay — the anime trap**: our `SeasonDetector`
  (`core/seasons/SeasonDetector.kt`, D-312) infers *anime* "seasons" from **name tags** for
  display grouping — anime seasons are cour/arc groupings over a **global** AniList-absolute
  numbering (the AniList relay and metadata enrichment both speak absolute numbers, doc 15
  §3). **[recommendation]** SeasonDetector stays display-only for aniyomi content and is
  NEVER consulted for identity; for CS3 content the **provider's `Episode.season`** is the
  identity input (it comes structured from the provider, doc 05 §4.1 — no inference needed).
  Identity source is thus ecosystem-dispatched, like everything else in this doc.
- **Display seasons ≠ identity seasons**: `SeasonData.displaySeason` renames seasons for UI
  only (doc 05 §4.2 "To be mapped with episode season, not shown in UI if displaySeason is
  defined"). We key on the **raw** `Episode.season` and store the overlay as metadata
  (§5.2). CS3's own stable episode ids do the same (`mainId + season*10_000 + episode`,
  doc 05 §4.2).

### 3.4 The constraint changes that kill the D-313 class of bug

**[design]**

```sql
-- data_cache_episode: UNIQUE gains the season dimension. Anime rows: season_number = 0
-- (NOT NULL DEFAULT 0) ⇒ (main_id, 0, episode_number) ≡ today's semantics, byte-for-byte.
CREATE UNIQUE INDEX idx_data_cache_episode_pk
    ON data_cache_episode(main_id, season_number, episode_number);
-- season_number changes to NOT NULL DEFAULT 0 (currently nullable, D-190, dataCache.sq:38)
```

Doc 15 §8.5 asked for exactly this ("key/unique need a season dimension"). With
`(main_id, season, ep-in-season)` unique **and** per-season distinctness guaranteed upstream
(§3.3), two seasons' "episode 1" coexist; a duplicate-within-season still collapses via
INSERT OR REPLACE, so we additionally **[design]** switch the episode-cache write to plain
`INSERT … ON CONFLICT DO UPDATE` with the normalizer's dedupe-by-URL in front
(`EpisodeListNormalizer.kt:62-67` already dedupes by URL first-wins).

### 3.5 Unifying the second key regime (downloads)

**[recommendation]** Kill the `SEpisode.url`-as-episode-key regime: `download_queue`,
`downloaded_episode`, `user_episode_rating`, `episode_update` all move to the canonical
`EpisodeKeys` output. The raw provider episode URL survives *inside* `resolve_context`
(§4) and `data_cache_episode.episode_url` (`dataCache.sq:30`) where it belongs — as payload,
not identity. Doc 15 called the two-regime split "live data, not theory" (§6.1); CS3 would
make it three regimes (url, global number, season-qualified) — the moment to unify is now.
Consequences: `data.json` episode entries carry the canonical key + url
(`ContentDataJson.kt:10-39`); the reinstall scanner's `deriveEpisodeKey`
(`DownloadScanner.kt:542`) learns the `SxxExx` filename pattern; **[open-question]** confirm
the user accepts a one-time dev-data wipe for downloads (§30 says yes by default).

Filename scheme **[design]**: global mode keeps `"${title} - E00001.mp4"`
(`DownloadStorageProvider.kt:172`); seasoned mode: `"${title} - S02E05.mp4"`
(%02d/%02d — no zero-padding to 5, matching how humans name TV files). The scanner derives
the key back from the filename (same reverse-derivation contract as today,
`DownloadScanner.kt:159-189`).

---

## 4. P3 — Ecosystem-typed resolve context

### 4.1 The problem restated

`ResolveContext(sourceId: Long, episodeUrl, serverName, audioLabel, quality, mainId,
episodeKey)` (`ResolveContext.kt:24-33`) is serialized into `download_queue.resolve_context`
(`downloadQueue.sq:56-57`) and consumed by `ReResolver.reResolve` — a DIRECT lookup pinned on
(server, audio, quality), never re-running AutoDownloadEngine (REVIEW-5 M17, doc 15 §4.3).
Everything is aniyomi-shaped: `sourceId: Long`, `getHosterList` re-resolve. Doc 15 §8.4
[gap]: "CS3 needs provider name + `Episode.data` + `loadLinks` instead".
`playback_cache_entry.source_id INTEGER NOT NULL` + `server_key` share the same assumption
(`playbackCache.sq:27-28`).

### 4.2 The discriminator design

**[design] Polymorphic sealed context** (replaces the flat data class; kotlinx sealed
serialization with `classDiscriminator = "ecosystem"` into the same TEXT column):

```kotlin
// [design sketch]
@Serializable
sealed interface ResolveContext {
    val ecosystem: String          // "aniyomi" | "cloudstream"
    val mainId: String
    val episodeKey: String         // canonical key (§3)
}
@Serializable data class AniyomiResolveContext(
    val sourceId: Long, val episodeUrl: String,          // SEpisode.url — payload, not key
    val serverName: String, val audioLabel: String, val quality: String,
    override val mainId: String, override val episodeKey: String,
) : ResolveContext { override val ecosystem get() = "aniyomi" }
@Serializable data class CloudStreamResolveContext(
    val providerKey: String,        // "cloudstream:<providerName>" (§6)
    val contentUrl: String,         // LoadResponse.url — the load() round-trip handle (doc 05 §3.1)
    val episodeData: String,        // Episode.data — the opaque loadLinks payload (doc 05 §4.1, §10.5)
    val linkLabel: String,          // pinned ExtractorLink.name (server label)
    val linkSource: String,         // pinned ExtractorLink.source (extractor name — 2nd-tier match key)
    val quality: String,            // pinned quality label (Qualities.getStringByInt, doc 05 §11.1)
    val extractorData: String? = null,  // keep-alive token (doc 08 §4.6) — best-effort, §4.4
    override val mainId: String, override val episodeKey: String,
) : ResolveContext { override val ecosystem get() = "cloudstream" }
```

Why a sealed type rather than one flat class with nullable CS3 fields: the two variants have
**different match keys** (aniyomi: server+audio+quality; CS3: label+source+quality) and the
re-resolver dispatch is total (no "null sourceId means CS3?" guessing). A
`when (ctx)` at the resolver entry point is the whole dispatch — the persistence-side twin of
doc 14 §9 seam 4 ("provider-typed dispatch"). Old rows (7-field flat JSON) are simply wiped
(§30) — **[recommendation]** no legacy decode.

### 4.3 ReResolver for CS3 — which link?

**[design]** On IOException (the D-149-fix trigger, doc 15 §4.3), the CS3 branch:
1. resolve provider by `providerKey` (registry lookup, §6);
2. re-run `loadLinks(contentUrl | episodeData)` (the CS3 contract, doc 08 §1 — `Episode.data`
   is the input channel, doc 05 §4.1);
3. **match the pinned link**, in tiers: exact `(linkLabel, quality)` → same `linkSource` +
   nearest quality → fail with ERROR state (user re-picks). Label matching is inherently
   fuzzy (provider may rename mirrors between sessions); tier 2 keeps a dead pinned label
   from killing an otherwise-fine re-resolve. This mirrors aniyomi's pinned
   (server, audio, quality) direct lookup (doc 15 §4.3) with CS3's flatter link list;
4. `updateDownloadVideoUrl` persists the fresh URL + headers (`downloadQueue.sq:114-121`
   pattern), exactly like the aniyomi proxy-churn fix.

Streaming-side: `ResolvedVideosRegistry` stays an in-memory string-keyed handoff (doc 14 §9.2
"already agnostic") — CS3 link lists enter it through the Cloud Screen resolver (doc 16/19
scope). No DB change.

### 4.4 Resolved-URL persistence, expiry, and extractorData

- **Resolved URL + headers** are persisted per download row (`video_url`, `video_headers`)
  today; same for CS3. Expiry is detected, not predicted: CS3 hosts mint short-TTL URLs
  (sflix-class, doc 08 §4.6) — on 403/IOException the §4.3 re-resolve runs. **[design]** add
  `resolved_url_expires_at INTEGER` (best-effort hint from the provider when knowable, else
  NULL) to skip doomed plays early; v1 may omit — CS3 itself doesn't persist TTLs.
- **extractorData keep-alive**: doc 08 §4.6 verified the player
  (`loadExtractorJob`, GP:256-268) and downloader (DM:1487-1495) start
  `extractorVerifierJob(extractorData)` for the duration of playback/download, and that CS3
  does NOT persist it across restarts (only the 20-min RepoLinkGenerator cache +
  queue-item links, doc 08 §4.6 "Persistence"). **[design]** we persist it in
  `CloudStreamResolveContext.extractorData` so a *resumed* download can restart the verifier
  immediately, but treat it as best-effort: on re-resolve it is regenerated from the fresh
  link. If stale, the verifier throws `NotImplementedError`-class failures that are already
  swallowed (`ioSafe`, doc 08 §4.6) — worst case is the pre-existing CS3 behavior (link dies
  mid-download → re-resolve).

### 4.5 Playback cache identity

**[design]** `playback_cache_entry.source_id INTEGER NOT NULL` → `source_key TEXT NOT NULL`
(`"cloudstream:Uakino"` / `"aniyomi:4697393375201558791"`); the identity hash input becomes
`sha256("$mainId\u001F$canonicalEpisodeKey\u001F$sourceKey\u001F$serverKey")` — the
stable-identity-hash pattern is explicitly the one doc 15 §4.4 calls "the good pattern to
copy": identity from (content, episode, source, server), never volatile URLs
(`playbackCache.sq:3-7`). For seasoned episodes the canonical key embeds season — no extra
dimension needed. `server_key` for CS3 = the ExtractorLink label minus volatile segments
(same "minus urlHash" treatment as aniyomi's videoTitle, `playbackCache.sq:5-6`).

---

## 5. P4 — Metadata without AniList

### 5.1 What CS3 gives us and where each field lands

CS3 `LoadResponse` field inventory (doc 05 §3.1) → our storage:

| CS3 field | Destination | Mechanism |
|---|---|---|
| `name` | `main_entry.title` | existing |
| `plot` | `content_details.ext_description` | existing column (`content.sq:115`) |
| `tags` | `content_details.ext_genres` (joined ", ") | existing column; **NOT** `content_genre` M:N (that table is AniList-genre-vocabulary-keyed, `genres.sq:39-50`; unmapped CS3 tags would need vocabulary inserts — v1 keeps tags ext-axis-only) **[design]** |
| `showStatus` | `ext_status` ("Ongoing"/"Completed", 2-value enum, doc 05 §4.4) | existing column, string convention |
| `posterUrl` / `backgroundPosterUrl` | `ext_thumbnail_url` (+ `ext_extraJson.bannerUrl`) | existing + extras |
| `posterHeaders` | **NEW column `ext_poster_headers TEXT`** (JSON map) | doc 15 §8.6: "nowhere to live; image loading needs it" — doc 07 §3.3 (hotlink protection, 403s without it). A real column because Coil needs it synchronously on every list/details render |
| `year` / `score` / `duration` / `contentRating` / `logoUrl` / `comingSoon` / `nextAiring` / `syncData` | `ext_extra_json` via extended `ExtensionExtras` | additive typed JSON accessor (`ignoreUnknownKeys`, `ContentModels.kt:149-154`) — doc 15 §8.6 [adaptable] |
| `type: TvType` | `main_entry.content_type` | existing free-TEXT column (doc 15 §8.2 [adaptable]); v1 mapping: Movie→`'movie'`, TvSeries→`'series'`, AsianDrama→`'drama'`, Anime/OVA/AnimeMovie→`'anime'`, others→`'other'` **[design]** (TvType's 18 values, doc 05 §6.1, collapse to a coarse set; chips/filtering use the doc 10 §8.13 grouping) |
| `episodes` (flat + SeasonData) | `data_cache_episode` rows + `ext_extra_json.seasonNames` | §3 keys; SeasonData overlay stored as metadata (display names) |

**[design] `ExtensionExtras` grows CS3 fields** (all optional, additive):

```kotlin
// [design sketch] — additive to ContentModels.kt:149-154 (ignoreUnknownKeys ⇒ old rows fine)
@Serializable data class ExtensionExtras(
    val scanlatorGroup: String? = null, val chapterCount: Int? = null, val volumeCount: Int? = null,
    // CS3:
    val year: Int? = null, val score: String? = null,        // Score serialized scale-aware (doc 05 §2.4)
    val durationMinutes: Int? = null, val contentRating: String? = null,
    val bannerUrl: String? = null, val logoUrl: String? = null,
    val comingSoon: Boolean? = null,
    val nextAiringEpisode: Int? = null, val nextAiringAt: Long? = null,   // unix seconds (doc 05 §4.3)
    val seasonNames: List<SeasonNameJson>? = null,            // SeasonData overlay (doc 05 §4.2)
    val syncData: Map<String, String>? = null,                // exact-id assist (§2.4b) + future tracker relay
)
```

Doc 15 §8.6's alternative ("add nullable columns") is rejected for everything except
`poster_headers`: JSON extras are additive with zero schema cost and the details screen parses
once per load (the D-285 batch reads never touch extras). `year` stays in JSON in v1 even
though doc 12 §9.3 #4 warns year drives library sort — CS3 years are unreliable
(missing/faked, doc 12 §9.3 #4), so **[open-question]** whether year-sort for CS3 content is
even wanted.

### 5.2 Per-link snapshots

`content_source_link.metadata_json` **[design]** holds the *link-scoped* display snapshot
(posterUrl, posterHeaders, provider lang, TvType badge) so the source-switch sheet renders
without re-fetching, and a freshly-activated link immediately has art. This is a *cache*, not
truth — deliberately NOT CS3's full-DTO favorites model (doc 13 §5.1's stale-snapshot trap);
the snapshot is refreshed on every details load of that link, mirroring CS3's
`updateSubscribedData`-only-refreshes-subscriptions caveat (doc 13 §5.1 `[gap]` note).

### 5.3 Update checking for CS3 content — subscriptions model

`anime_update_state` is AniList-schedule-shaped end to end: `status='RELEASING'`,
`next_airing_at`, `total_episodes` (AniList), learned airing offset (doc 15 §1.6). CS3 shows
have no airingAt; CS3's own mechanism is a **6-hour WorkManager that re-`load()`s each
subscribed show and diffs `getLatestEpisodes()` vs stored `lastSeenEpisodeCount`** (doc 13
§6.2, SWM:134-167). Doc 15 §8.10 [gap] offers "nullable columns + CS3 poll mode or a parallel
table". **[recommendation] parallel table** — `anime_update_state`'s semantics (airingAt
scheduling, CF4 backoff tied to airing offsets, dub counts) would all be dead weight:

```sql
-- [design] new cs3_subscription_state.sq — poll-mode sibling of anime_update_state.
CREATE TABLE IF NOT EXISTS cs3_subscription_state (
    main_id                 TEXT NOT NULL PRIMARY KEY,   -- FK main_entry CASCADE
    active_source_key       TEXT NOT NULL,               -- poll through THIS link (§2)
    last_seen_episode_count INTEGER NOT NULL DEFAULT 0,  -- CS3 getLatestEpisodes() equivalent (doc 05 §4.4)
    last_check_at           INTEGER,
    next_check_at           INTEGER NOT NULL,            -- CS3: fixed 6h (doc 13 §6.2); we reuse CF4-style backoff
    consecutive_failures    INTEGER NOT NULL DEFAULT 0,
    auto_update_enabled     INTEGER NOT NULL DEFAULT 1,  -- auto-off after 3 failures (M3 precedent, doc 15 §1.6)
    updated_at              INTEGER NOT NULL,
    FOREIGN KEY (main_id) REFERENCES main_entry(main_id) ON DELETE CASCADE
);
```

The **`episode_update` feed is shared** — its columns are already generic enough (doc 15
§8.10: `main_id, episode_key, episode_number, source_id, audio_variant, discovered_at,
acknowledged, batch_type…`): a CS3 poll discovering S02E06 inserts a feed row keyed on the
seasoned episode key, `source_id`→`source_key` carrying `cloudstream:<provider>` (§6). The
feed UI, "New" badge expiry (D-193), and acknowledge flows are reused as-is.

### 5.4 Schedule / notifications / tracker relay — the AniList guards

- **`episode_schedule`**: stays AniList-only in v1 (UNIQUE `(main_id, episode_number,
  audio_variant)` is airingAt-shaped, doc 15 §1.6). CS3 `nextAiring` (when a provider sends
  it — 0/58 in the census, doc 12 §9.2) renders a details-page countdown from
  `ext_extra_json` only (CS3 does the same, doc 05 §4.3). **[open-question]** should CS3
  nextAiring ever feed the Schedule tab?
- **Notifications**: `notification_config` is per-`main_id` — works for CS3. `notification_sent`
  PK is `(main_id, episode_number, audio_variant, trigger_type)` (doc 15 §1.6) —
  season-scoped numbers repeat across seasons ⇒ **[design]** PK becomes `(main_id,
  episode_key, trigger_type)` (the same unification as §3.5).
- **Tracker relay**: `relayWatchProgressIfNeeded` → `getHighestWatchedEpisodeNumber` →
  AniList progress (doc 15 §3) gets a `hasAnilistLink` guard (the parsed-global-number
  property from §3.3 already makes seasoned keys invisible to the SQL). `track_entry`
  requires an AniList link by construction (tracker_id = AniList mediaId, doc 15 §1.8) —
  CS3-only content simply has no tracker row. **v1 does NOT build a CS3↔MAL/Kitsu tracker
  relay** (CS3's SyncAPI world, doc 13 §8, is out of scope); the `syncData` map in extras is
  the future hook.
- **`details_source_link:<anilistId>` pref** (doc 15 §2.2 — meaningless for CS3-only content):
  **[design]** re-key to `details_source_link:<mainId>` = `"<sourceKey>:<externalUrl>"` —
  fixes both the AniList-id dependency and the Long-typed source id in one stroke (§6).

---

## 6. P5 — Ecosystem-qualified keys everywhere

### 6.1 The convention

**`sourceKey = "<ecosystemId>:<sourceId>"`** — already the provider-api discipline
(`Source.kt:19-23`, "Used as a prefix for content_key values in the database", doc 14 §6.3).
For CS3, `<sourceId>` = the provider **name** (`MainAPI.name` — the only stable per-provider
handle in CS3; `apiName` is how every CS3 API re-resolves a provider, doc 05 §3.1/§10.4), so
keys look like `"cloudstream:Uakino"`. Aniyomi keys keep the Long: `"aniyomi:4697393375201558791"`.
**Never** adopt CS3's 32-bit url-hash ids as identity (doc 15 §8.11 [fits — we reject theirs]).

### 6.2 The Long → TEXT sweep (schema + prefs + registry + UI)

| Slot | Today | Change [design] |
|---|---|---|
| `main_entry.extension_id INTEGER` + `source_id INTEGER` (content.sq:57-58) | Aniyomi Long | replace with `source_key TEXT` (single column; `extension_id`/`source_id` dropped — §30 destructive) |
| `content_details.source_id INTEGER` (content.sq:113) | Long | `source_key TEXT` |
| `download_queue.source_id INTEGER` (downloadQueue.sq:29) | Long | `source_key TEXT` |
| `downloaded_episode.source_id INTEGER` | Long | `source_key TEXT` |
| `playback_cache_entry.source_id INTEGER NOT NULL` (playbackCache.sq:27) | Long | `source_key TEXT NOT NULL` (§4.5) |
| `episode_update.source_id INTEGER?` | Long | `source_key TEXT?` |
| pref `search_selected_extension_source_id` (Long, doc 15 §5) | Long | string sourceKey value |
| pref `details_source_link:<anilistId>` = `"$sourceId:$animeUrl"` (doc 15 §5) | Long | `details_source_link:<mainId>` = `"<sourceKey>:<externalUrl>"` (§5.4) |
| `ExtensionManager` `Map<Long, AnimeSource>` registry | Long | stays aniyomi-internal; the provider registry layer (doc 16) keys by sourceKey string (doc 14 §8.4/§9 seam 3) |
| feature models `ExtensionAnime(sourceId: Long…)` (doc 14 §7.1) | Long | `sourceKey: String` |
| `ResolveContext.sourceId: Long` | Long | polymorphic contexts (§4.2) |

### 6.3 Migrating the two live episode-key regimes

Doc 15's surprise #5 / §1.4: watch keys `"mainId|%05d"` vs download keys `SEpisode.url` —
"the two-regime split is live data, not theory" (§6.1). §3.5 unifies them on
`EpisodeKeys`. Because §30 permits the wipe, the "migration" is: fresh schema + dev-data
reset; the `data.json` reinstall records on disk are re-keyed by the scanner's
filename-derivation (`DownloadScanner.kt:529-542` extended with the SxxExx pattern) — worst
case the scanner logs orphans (it already does, `DownloadScanner.kt:176`). **[design]** the
`onOpen` shim gains `hasColumn`-guarded CREATEs for the new tables/indexes only (doc 15 §7
pattern) so a *same-version* reinstall never crashes.

---

## 7. Favorites / library for CS3 content

**[recommendation] Our library model wins — CS3 content enters `library_item` via
`main_entry`, not a parallel favorites store.** Doc 15 §8.9: [fits], "ours is the better
model"; CS3's own favorites = full `SearchResponse` DTO JSON snapshots in prefs that go stale
(doc 13 §5.1) — rejected. Concretely:

- **Entry shape**: `main_entry` with `content_type ∈ {movie, series, drama, anime, other}`
  (§5.1), ext-axis metadata, ≥1 `content_source_link`. `library_item` row as usual
  (UNIQUE `(main_id, category_id)`, doc 15 §1.2).
- **Category**: **[recommendation]** auto-create a **"Cloud" `library_category`**
  (is_permanent=0, like "COW"/"TEST" in real data, doc 15 §1.2) on first CS3 library-add;
  CS3 titles land there by default, the user moves/keeps them anywhere. Rationale: library
  sorting/filtering by content kind is then one JOIN away; a tag-based approach would abuse
  `content_genre` (AniList vocabulary, §5.1); no-category would mix 43 anime with movies in
  the Default view. **[open-question]** user preference: separate category vs mixed Default?
- **Features that apply**: progress tracking **YES** (watch_progress is content-agnostic,
  doc 15 §8.7); continue-watching, history, per-episode ratings, downloads, updates feed
  (§5.3) — all key off main_id/canonical keys. **AniList tracking NO** — tracker relay and
  `track_entry` gated on `hasAnilistLink` (§5.4); the Track sheet hides for CS3-only titles.
  **[open-question]** if a CS3 title later gains an AniList link via syncData (§2.3), should
  the relay *backfill* historical progress (potentially hundreds of episodes)?
- **Sorting/filtering by TvType**: v1 filter = `main_entry.content_type` (coarse set §5.1) +
  the "Cloud" category. The full chip→TvType grouping map (doc 10 §8.3, `getPairList`) is a
  Cloud Screen browse concern (doc 18), not a library-schema concern — no schema support
  needed beyond `content_type`.

---

## 8. Downloads & playback cache for CS3 content

### 8.1 Entity mapping

`download_queue` / `downloaded_episode` rows for CS3 content: `main_id` + canonical
`episode_key` (§3.5), `source_key = "cloudstream:<provider>"` (§6), display metadata
denormalized as today (doc 15 §1.5), `resolve_context` = `CloudStreamResolveContext` (§4.2).
The 7-state machine, retry/backoff, crash recovery (`resetDownloadingToQueued`), purge flows
are content-agnostic — reused unchanged (doc 15 §4.2).

### 8.2 m3u8 — reuse OUR machinery

**[recommendation] Yes — reuse our own download pipeline.** We already have the full CS3
equivalent: `HttpDownloader` routes HLS URLs to `HlsDownloader` ("parallel mode + AES",
`HttpDownloader.kt:13,231-244`), with playlist re-detection for lying content-types
(`:99-106`) and `m3u8 → .ts` extension mapping (`:380-386`). CS3's own downloader does the
same trick — segments concatenated **without remux**, output named `.mp4`, playable because
ExoPlayer sniffs TS (doc 09 §5.5) — and our player is mpv-based with TS support via the same
downloaded-file path. The one CS3 behavior worth copying later: segment-granular resume via
`DownloadedFileInfo.extraInfo` (doc 09 §5.5) — **[open-question]** whether v1 needs
HLS-download resume or current whole-file restart suffices (doc 19 owns the runtime detail).
DRM (`DrmExtractorLink`) and torrent/magnet links: rejected for download in v1 (CS3 itself
rejects MAGNET/TORRENT/DASH for downloads, doc 09 §5.4).

### 8.3 Persisted URL expiry

Same policy as §4.4: persist `video_url` + `video_headers` (referer/UA from `ExtractorLink`,
doc 05 §7.3) per row; on expiry → `CloudStreamResolveContext` re-resolve →
`updateDownloadVideoUrl` (the D-149-fix pattern, doc 15 §4.3). The `extractorData`
keep-alive job runs for the download's duration (CS3: DM:1487-1495, doc 08 §4.6) — our
download orchestrator gains the equivalent hook (runtime detail → doc 19).

### 8.4 File naming

Season-qualified: `"${title} - S02E05.mp4"` + subtitles `"${title} - S02E05.<lang>.<i>.<ext>"`
(extends `DownloadStorageProvider.kt:18-19,154-155`); folder layout stays
`<root>/video/<title>/…` with a TvType-prefix option (CS3's `getFolderPrefix` puts movies in
`Movie/…`, series in `TV Series/…`, doc 09 §5.7) — **[open-question]** flat vs
type-prefixed folders for CS3 content.

---

## 9. Schema migration plan

### 9.1 Table-by-table change list (the `.sq` edits)

| File | Table | Change | Type |
|---|---|---|---|
| `content.sq` | `main_entry` | drop `extension_id`, `source_id`, `anime_url`, `extension_repo_id` (dangling, doc 15 §10) → add `source_key TEXT`, `active_external_url TEXT` (active-link denormalization keeps the hot lookup, `content.sq:45-47`) | **changed (destructive)** |
| `content.sq` | `content_details` | `source_id INTEGER` → `source_key TEXT`; add `ext_poster_headers TEXT` (§5.1); axes otherwise unchanged | **changed** |
| `content.sq` | `content_source_link` | NEW (§2.2) | **new** |
| `watch.sq` | `watch_progress` | no DDL change (key format is data, §3.3); `getHighestWatchedEpisodeNumber` gains a doc note + caller guard | unchanged (query-adjacent) |
| `dataCache.sq` | `data_cache_episode` | `season_number` → NOT NULL DEFAULT 0; UNIQUE → `(main_id, season_number, episode_number)`; write switches to upsert-on-conflict (§3.4) | **changed (destructive index)** |
| `downloadQueue.sq` | `download_queue` | `source_id` → `source_key TEXT`; `episode_key` semantics = canonical key; `resolve_context` = polymorphic JSON (§4.2); optional `resolved_url_expires_at` | **changed (destructive)** |
| `downloadedEpisode.sq` | `downloaded_episode` | `source_id` → `source_key`; canonical keys; `video_file_name` season format (§8.4) | **changed (destructive)** |
| `playbackCache.sq` | `playback_cache_entry` | `source_id` → `source_key TEXT NOT NULL`; identity hash input change (§4.5) | **changed (destructive — cache is disposable anyway, doc 15 §1.5)** |
| `episodeUpdate.sq` | `episode_update` | `source_id` → `source_key TEXT?`; `episode_key` canonical | **changed (light)** |
| `animeUpdateState.sq` | `anime_update_state` | UNCHANGED (AniList-only by design, §5.3) | unchanged |
| NEW `cs3SubscriptionState.sq` | `cs3_subscription_state` | NEW (§5.3) | **new** |
| `episodeSchedule.sq` | `episode_schedule` | UNCHANGED (AniList-only v1, §5.4) | unchanged |
| `notifications.sq` | `notification_sent` | PK → `(main_id, episode_key, trigger_type)` (§5.4) | **changed (destructive; ephemeral table, doc 15 §1.6)** |
| `library.sq` / `genres.sq` / `track.sq` / `ratings.sq` / `appSettings.sq` / `tracking.sq` / `app.sq` | — | UNCHANGED (`user_episode_rating` keys on canonical episode_key — semantics only) | unchanged |
| NEW `cs3ResultCache.sq` (optional) | `cs3_result_cache` | browse/search section cache for the Cloud Screen, modeled on `browse_cache` (6h TTL; doc 15 §8.9 "if a raw-DTO cache is wanted… modeled on browse_cache"; doc 13 §7 has NO disk result cache — we add one because our browse is SQL-first) | **new (v1 optional)** |

Schema version stays **1** (no `.sqm` ever — doc 15 §7); the dev workflow is
uninstall→reinstall (CORE_RULES §30 user clarification).

### 9.2 Driver hygiene (the only "migration" work)

**[design]** `DatabaseDriverFactory.onOpen` gains: (a) CREATE TABLE IF NOT EXISTS for
`content_source_link` + `cs3_subscription_state` (+ `cs3_result_cache`); (b) hasColumn-guarded
ALTERs for `ext_poster_headers`, `source_key` columns (nullable-add only, so old installs
open); (c) the destructive re-keys are NOT shimmed — doc 15 §7 precedent: the
content→main_entry rebuild re-ran `onCreate` wholesale. Keep the shim's stated nature:
"make sure columns exist", not a migration system (`CORE_RULES.md:507`).

### 9.3 DATABASE.json + dashboard obligations (CORE_RULES §24/§25)

- **`DATABASE.json` export needs zero changes** — `exportAsJson()` iterates `sqlite_master`
  (doc 15 §9.2); new tables flow automatically. Its 4KB truncation means CS3 JSON-blob
  payloads (`metadata_json`, extras) debug poorly — doc 15 §9.2 suggests raising
  `renderCell`'s limit or a per-table raw mode; **[recommendation]** bump to 16KB when CS3
  tables land (one constant, `DebugDatabaseBrowser.kt:169-182`).
- **`lib/schema.ts` is stale TODAY** (26 pre-D-198 tables vs 24 real, doc 15 §6.3 — §25
  violation) and Batch 4 adds ~3 more tables. **[recommendation]** the schema.ts regeneration
  pass is a **blocking subtask of the first B4 implementation PR**, transcribing all `.sq`
  files (the method its own header documents, `schema.ts:2-5`); no generator script exists
  (doc 15 §9.1 [gap]) — **[open-question]** worth scripting one? (~1 day, kills the drift
  class permanently).
- **§24 docs**: new tables get `DOCUMENTATION/database/` entries + README migration-changelog
  rows; this doc (17) is the design source; the `/database-review`-style CS3-era section can
  be derived from doc 15 §8's verdict table + §9.1 above (doc 15 §9.4).

---

## 10. What we deliberately DON'T store (v1 scope cuts)

Justification baseline: doc 12 §9.2 census — **median provider uses ~30% of the field
surface**; the table below cites per-field usage. Store only what our v1 UI renders.

| Cut | CS3 surface | Usage evidence (doc 12 §9.2) | Why cut |
|---|---|---|---|
| Actors / people | `actors: List<ActorData>` | 1/58 providers (names only) | no cast row UI in v1; revisit with a details-screen cast rail |
| Recommendations | `recommendations: List<SearchResponse>` | 3/58 | no cross-provider rec rail in v1 (doc 07 §6.1 shows the CS3 rendering; doc 18 may revive) |
| Trailers | `trailers: MutableList<TrailerData>` | 1/58 | extraction pipeline + player surface not worth it for 1 provider |
| Per-episode `score`/`description`/`runTime` | `Episode` fields (doc 05 §4.1) | sparse | `data_cache_episode` already has description/thumbnail/runtime columns — we MAP the ones we have columns for, add nothing |
| Raw `syncData` beyond ids | `MutableMap<String,String>` | 0/58 set it; injected by CS3's tracker layer (doc 13 §8.1) | we keep ids only (§2.4b, §5.1) |
| `uniqueUrl` separate from `url` | storage-key override (doc 05 §3.1) | 0/58 | no provider rotates urls in the census; revisit if one does |
| CS3 quality profiles / source priority | DataStore `video_source_priority` (doc 13 §4.6) | n/a | our ResolverServer/quality model already covers it (doc 14 §7.3) |
| Download DTO caches | `download_header_cache`/`download_episode_cache` (doc 13 §4.7) | n/a | our `download_queue`/`downloaded_episode` denormalization already carries display metadata (doc 15 §1.5) |
| CS3 watch-type 5-state bookmarks | `WatchType` (doc 13 §5.1) | n/a | our categories + completed flag cover it (doc 13 §11: "library_category already covers it, richer") |
| Subtitle-search metadata | year filters into OpenSubtitles etc. | n/a | no CS3 online-subtitle integration in v1 |

**Kept despite low usage** (because cheap or structural): `posterHeaders` (1/58 but 403s
without it, doc 07 §3.3), `nextAiring` (0/58 but one details-page countdown), `seasonNames`
overlay (0/58 but one JSON list). Every one of these rides `ext_extra_json` — zero schema
cost (§5.1).

---

## 11. Open questions for the user

1. **[open-question] Cross-provider matching ceiling**: manual-only forever (§2.4), or build
   the exact-syncData-id suggestion chip in v1? (v1 = manual-only as recommended.)
2. **[open-question] Library placement**: auto-"Cloud" category (§7) vs mixed into Default?
3. **[open-question] Dev-data wipe confirmation**: §9 re-keys downloads/cache/notifications —
   confirm the one-time uninstall-reinstall is acceptable for the CS3 rollout build (§30
   default is yes).
4. **[open-question] Seasoned key format**: `"mainId|S02E00005"` (§3.3) — aesthetically
   `S02E05` in filenames vs zero-padded `S02E00005` in keys; any preference?
5. **[open-question] AniList-link backfill**: when a CS3 title gains an AniList link
   retroactively (syncData), relay all historical progress or start from now (§7)?
6. **[open-question] Year/score library sorting for CS3 content**: wanted despite census-level
   data dirtiness (doc 12 §9.3 #4)? If yes, promote year/score to real columns (§5.1).
7. **[open-question] CS3 nextAiring → Schedule tab**: keep it details-page-only (§5.4) or
   feed `episode_schedule` later?
8. **[open-question] schema.ts generator script**: build one (~1 day) or accept manual
   transcription discipline (§9.3)?
9. **[open-question] HLS download resume granularity**: whole-file restart (current) vs CS3's
   segment-granular resume (§8.2)? Runtime detail owned by doc 19 — data layer only needs to
   know if an `extraInfo`-style column is needed on `download_queue`.
10. **[open-question] Download folder layout for CS3 content**: flat `<title>/` vs TvType
    prefixes (`Movie/`, `TV Series/`, §8.4)?
11. **[open-question] Anime-on-CS3**: a CS3 provider serving `TvType.Anime` — treat as anime
    (attempt AniList auto-link via syncData/SmartMatcher) or as plain CS3 content? Affects
    which aux engines activate (§5.3/§5.4).
12. **[open-question] NSFW gate**: single persisted master toggle for CS3 content (doc 10 §8.5
    recommends one toggle, default OFF) — confirm, and whether it should also suppress watch
    -progress writes like CS3 does (`GP:1728-1729`).

---

## 12. Verification status

- All claims about OUR schema were re-verified against the `.sq` sources for this doc:
  `content.sq` (full), `watch.sq:1-158`, `dataCache.sq:1-103`, `downloadQueue.sq:1-80`,
  `playbackCache.sq:1-60`, plus `ResolveContext.kt:1-33`, `EpisodeListNormalizer.kt:1-112`,
  `HttpDownloader.kt` (grep-verified HLS routing), `DownloadStorageProvider.kt` (filename
  sites), `ContentModels.kt:100-160` (extras accessors), `CORE_RULES.md:491-507` (§30),
  `AGENT-CONTEXT/memory/decisions.md` D-313/D-317 records.
- CS3-side claims inherit docs 01–15's verification (cited per claim); nothing new was claimed
  about CS3 sources beyond those docs.
- All SQL/Kotlin blocks are **[design sketches]** — none exist in code. `downloadedEpisode.sq`
  and `episodeUpdate.sq` were read via doc 15 §1 (verified there) + targeted greps, not
  re-read in full here.

*End of doc 17. Next consumers: doc 16 (architecture — consumes §2/§4/§6 key conventions),
doc 18 (Cloud Screen UI — consumes §5/§7), doc 19 (playback/downloads — consumes §3.5/§4/§8),
doc 20 (roadmap — §9 is the schema work package).*

---
## ✔ B5-b Verification Note (2026-08-29)
Checked: 14 claims sampled → 14 verified, 0 corrected, 0 flagged-stale. Consistency: ok.
Corrections: none.
Samples re-verified against the `.sq`/Kotlin sources: content.sq:110-122 ext-axis columns; content.sq:57-58 INTEGER extension_id/source_id vs :112 TEXT extension_id; dataCache.sq:30 episode_url / :38 nullable season_number (D-190) / :44 UNIQUE (main_id, episode_number); watch.sq:110-121 `getHighestWatchedEpisodeNumber` SQL (the CAST-to-INTEGER parse behavior §3.3 relies on is real); downloadQueue.sq:29 source_id / :56-57 resolve_context ("JSON: ResolveContext (7 fields)"); playbackCache.sq:27-28 source_id INTEGER NOT NULL + server_key; ResolveContext.kt:24-33 = exactly the 7 fields quoted; the D-313 record at decisions.md:2412-2418 (INSERT OR REPLACE collapse of the `(main_id, episode_number)` PK — the §1.2/§3.1 lesson quote is accurate); D-317 at :2442 ("per-season display numbers, guaranteed unique within a bucket" verbatim); EpisodeListNormalizer.kt:23-26 collapse doc + :61-67 URL dedupe first-wins; HttpDownloader.kt:13/99-106/231-244 (HLS routing, playlist re-detection, "parallel mode + AES") and :380-386 (m3u8→ts); DownloadStorageProvider.kt:18-19 filename scheme + :172-176 `%05d`; DownloadScanner.kt:189/:542; ContentModels.kt:137-154 ignoreUnknownKeys + ExtensionExtras(scanlatorGroup/chapterCount/volumeCount). The proposed `content_source_link`/`cs3_subscription_state`/key-scheme blocks are correctly marked [design] — none exist in code.
