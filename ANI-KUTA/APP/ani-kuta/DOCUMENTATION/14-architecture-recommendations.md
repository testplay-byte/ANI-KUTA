# 14 — Architecture Recommendations (Phase 1 Synthesis)

> Synthesis of all Phase 1 research. Includes the 4 research items (DB, DI, NAV, ADS) + the identity system redesign + multi-extension/multi-content-type architecture.
>
> This document drives the Phase 1 architecture plan.

---

## Summary of Recommendations

| Decision | Recommendation | Confidence |
|----------|---------------|------------|
| **D-034 DI** | Koin 4.x + Koin Annotations 2.x + Injekt (isolated to Aniyomi ext) | ✅ High |
| **D-035 DB** | SQLDelight 2.x (stay, NOT Room) | ✅ High |
| **D-036 Nav** | Jetpack Navigation 3 (`androidx.navigation3` 1.0.0+) | ✅ High |
| **D-033 Ads** | `:core:ads` (AdFormat interface + placement registry) + `:core:activity-tracker` (event-log) | ✅ High |
| **D-032 Identity** | Graph-based: `ContentUID` + `ExternalReference` (see §5 below) | 🚧 Design proposed |
| **D-031 Multi-ext** | `ExtensionProvider` abstraction, one impl per ecosystem | ✅ High |
| **D-030 Multi-content** | `ContentType` enum + per-type feature modules | ✅ High |

> ⚠️ **These supersede D-009** (tentative "Hilt + Room" from tech-stack.md). Tech-stack doc to be updated.

---

## 1. Database: SQLDelight 2.x (stay)

**Verdict: Stay on SQLDelight. Do NOT switch to Room.**

### Why
1. **Animiru (our base), Aniyomi, and old ANIKUTA ALL use SQLDelight.** Switching = 2-3 weeks refactor for zero functional gain.
2. **Partial unique indexes** (`CREATE UNIQUE INDEX ... WHERE col IS NOT NULL`) — needed for the identity system. Room's `@Index` doesn't support `WHERE` clauses.
3. **Data-transforming migrations** (dedup before unique index) — can't be expressed in Room's `autoMigration`. SQLDelight's `.sqm` files handle these as one-line SQL.
4. **Build performance** — SQLDelight uses a Kotlin compiler plugin (no KSP/APT overhead). Faster incremental builds on a 25+ module project.
5. **KMP-ready** — SQLDelight is KMP-first. Room only reached KMP stable in 2.7.0 (Apr 2025). If we go KMP later, SQLDelight is the safer bet.
6. **Backup/restore is DB-agnostic** — the backup architecture uses serializable `BackupEntry` models, not DB rows. DB choice doesn't drive backup.

### Migration effort
- Stay on SQLDelight: ~3-5 days (copy `.sq` + `.sqm` files, port repository impls).
- Switch to Room: ~2-3 weeks (translate 6 tables → `@Entity`, ~70 queries → `@Dao`, rewrite 6 repos + 10 backup mappers). High risk, no gain.

Full research: `10-db-research.md`.

---

## 2. Dependency Injection: Koin 4.x + Koin Annotations 2.x + Injekt (isolated)

**Verdict: Koin for the host app. Injekt isolated to Aniyomi extension compat only. Do NOT use Hilt.**

### Why
1. **Injekt is Aniyomi-only, NOT universal.** Research confirmed:
   - Aniyomi extensions → use Injekt.
   - Mangayomi → Flutter + JS (no Injekt).
   - Cloudstream → uses its own `MainAPI` (no DI).
   - Kotatsu → compile-time Kotlin parsers (no DI).
   - Sora → iOS-only (irrelevant).
   - Only Aniyomi-family extensions need Injekt. The host DI choice is unconstrained.
2. **KMP future-proofing.** Koin is KMP-ready. Hilt is Android-only — forecloses KMP later. Cloudstream itself is migrating to KMP, validating the direction.
3. **Pluggable `List<T>` registries** — Koin's `single<List<T>>(named("...")) { listOf(...) }` is cleaner than Hilt's `Set<T>` + `@IntoSet` (no ordering). The old project shipped 4 registries this way with zero incidents.
4. **Compile-time safety (Hilt's only advantage) is now matched.** Koin Annotations 2.x (mature Nov 2025) provides KSP-based graph verification equivalent to Hilt's.
5. **Agent-friendliness** — Koin DSL reads in 30 seconds. Hilt's component hierarchy does not. Matters for AI-agent-maintained codebase.
6. **Proven pattern** — old project shipped Koin+Injekt across 24 modules, 4 pluggable registries, zero DI incidents.

### Injekt isolation rule
- Injekt (`uy.kohesive.injekt`) imports allowed ONLY in: `App.onCreate()`, `:core:source-api`, `:data:extension-aniyomi`.
- A Detekt rule forbids `uy.kohesive.injekt` imports elsewhere.
- Injekt registers 4 singletons (`Application`, `Context`, `NetworkHelper`, `Json`) before the extension loader runs.

Full research: `11-di-research.md`.

---

## 3. Navigation: Jetpack Navigation 3 (Nav3)

**Verdict: Use `androidx.navigation3` 1.0.0+ (stable Nov 2025). Do NOT use Voyager.**

### Why
1. **R7 back-stack reliability** (the old project's fatal Voyager bug): Nav3's back stack is `StateFlow<List<NavKey>>` saved via `rememberSaveable`. The Voyager bug class (back stack lost on Activity recreate) is **structurally impossible** in Nav3.
2. **Type-safe routes** — `@Serializable NavKey` objects. Mismatched arg types are compile errors, not runtime `IllegalArgumentException`.
3. **Modular `api`/`impl` split** — official Pattern B from Google's Nav3 modularization guide. Each feature = `:feature:foo:api` (declares NavKey) + `:feature:foo:impl` (the screen). Features never see each other. Only `:app` wires them.
4. **Dynamic tabs** — tabs are an ordinary `List<NavKey>` driven by a preference `Flow`. Reordering tabs = list edit.
5. **Modal overlays** — `BottomSheetSceneStrategy` is first-class. Or keep the old project's state-driven overlay pattern.
6. **Deep linking** — `DeepLinkMatcher.withBackStack` is first-class.
7. **Agent-friendly** — add a screen = 3 local steps inside the feature module pair. No central registry to touch.

### Why NOT Voyager
- The 1.0.x back-stack bug is only fixed in 1.1.0-beta03 (Oct 2024) — never reached stable.
- 2.x alphas exist but are coupled to specific Compose-Multiplatform versions, no production track record.
- Issue #556 (Oct 2025): project in slow-maintenance mode.
- Sticking with Voyager = inheriting the same bug class the rebuild is trying to escape.

### Why NOT Nav2
- Google has stated Nav2 "will gradually be deprecated." Starting a greenfield 2026 project on Nav2 = signing up for a future migration Nav3 already eliminates.

Full research: `12-nav-research.md`.

---

## 4. Ad System + Activity Tracking

**Verdict: Two modules — `:core:ads` (ad system) + `:core:activity-tracker` (user activity tracking).**

### Architecture
```
:core:activity-tracker  ← sink-only event log (SQLDelight)
        ▲
        │ publishes ad events
:core:ads               ← AdFormat interface + AdPlacement registry + AdManager
        ▲
        │ uses
:feature:*              ← screens request ads via AdManager
```

### Key design decisions
1. **`AdFormat` interface** (NOT sealed class) + Koin `single<List<AdFormat>>` registry. The format set is open-ended. Adding a new ad type = one class + one Koin line. Zero edits to `:core:ads`.
   - Formats: `RedirectAdFormat`, `VideoAdFormat`, `InterstitialAdFormat`, future formats.
2. **`AdPlacement` data class + JSON config** (`assets/ad_placements.json`) + `AdPlacementRegistry`. The rule engine. Drives which screens, content types, formats, frequency caps apply. No code changes to add/remove a placement.
3. **`AdSource` interface** with `LocalAdSource` default. Future-proofs against moving ad serving to a backend. Swapping local → remote = one Koin binding.
4. **Per-interaction state** — `StateFlow<AdInteractionState>` + `SharedFlow<AdEvent>`. The old design's single process-wide StateFlow meant only one ad at a time (banners + interstitials couldn't coexist). New design returns a fresh `AdInteraction` per `AdManager.evaluate()` call → concurrent formats work.
5. **Generalized `TooEarlyReason`** — the old `ReturnedTooEarly` was redirect-specific. New design supports `MinStayNotMet`, `VideoWatchedInsufficient`, `SkippedBeforeStart` — video ads reuse the same anti-cheat pattern.
6. **`ActivityDetector`** — combines `ProcessLifecycleOwner` + `Activity.onUserInteraction()` + `PowerManager.isInteractive()`. Distinguishes "foreground + active" from "foreground + idle" from "background". 60s default idle threshold.
7. **SQLDelight event-log schema** (per-event, not aggregates). `activity_event` table (id, ts, type, session_id, route, content_type, content_id, duration_ms, payload JSON). Per-event lets us add new stats later without migrating the schema. 90-day rolling retention via WorkManager prune worker.
8. **`ContentType` enum** in placements/events, NOWHERE in the `AdFormat` interface. Adding a new content type = 1 enum entry + JSON placement entries. Zero code changes to `AdManager`, `AdFormat`, state machine, or existing placements.

### Old project flaws fixed
- `AdTiming` enum was stored but never read by `AdManager.shouldShowAd()` — placement was hardcoded in `AppController`. New placement-registry fixes this.
- `Cancelled`/`Completed` states were set then immediately overwritten with `Idle` in the same tick — Compose never saw them. New `SharedFlow<AdEvent>` for one-shot events fixes this.
- `cancelAd()` did no state validation. New `transition(expected, new)` helper logs on invalid transitions.

Full research: `13-ads-research.md`.

---

## 5. Identity System Redesign (D-032)

**The old `ContentId`/`LocalId` system is replaced with a graph-based identity model.**

### Problem with the old system
- `ContentId` was provider-prefixed (`"al:12345"`, `"ext:42:https://..."`). It mixed the app's identity with the provider's identity.
- Only supported one extension ecosystem (Aniyomi). No way to link "Attack on Titan" across Aniyomi + Mangayomi + Cloudstream.
- Relied on AniList as the canonical reference. Content not on AniList had synthetic IDs (`"ext:42:https://..."`) that broke on source switches.

### New model: ContentUID + ExternalReference

```
┌──────────────┐         ┌─────────────────────┐
│ ContentUID   │────1:N─→│ ExternalReference   │
│ (app's ID)   │         │ (link to external)  │
│ - uid (UUID) │         │ - uid (FK)          │
│ - contentType│         │ - ecosystem         │
│ - title      │         │ - sourceId          │
│ - createdAt  │         │ - externalId        │
│ - matchKey   │         │ - confidence        │
└──────────────┘         └─────────────────────┘
```

#### ContentUID (the app's own stable ID)
- `uid`: UUID generated when content is first seen. Stable forever. Never changes.
- `contentType`: VIDEO | IMAGE | TEXT (anime | manga | novel).
- `title`: canonical title (best-known).
- `createdAt`: when first seen.
- `matchKey`: normalized title + year + type, for fuzzy matching.

#### ExternalReference (a link to an external system)
- `uid`: FK to ContentUID.
- `ecosystem`: which extension ecosystem or tracker. `"aniyomi"`, `"mangayomi"`, `"cloudstream"`, `"kotatsu"`, `"anilist"`, `"mal"`, `"shikimori"`.
- `sourceId`: within the ecosystem, which source (e.g., Aniyomi source 42, Mangayomi "gogoanime").
- `externalId`: the external system's ID for this content (e.g., `"12345"` for AniList, `"https://gogoanime/..."` for an extension).
- `confidence`: how confident the match is (HIGH = user-confirmed or tracker-confirmed; MEDIUM = fuzzy title match; LOW = auto-guessed).

### How it works

#### Discovery (user opens content from a source)
1. User opens "Attack on Titan" from Aniyomi source 42.
2. System checks: does an `ExternalReference(ecosystem="aniyomi", sourceId="42", externalId="https://gogoanime/aot")` exist?
3. If yes → get the `ContentUID`. Done.
4. If no → try fuzzy match by `matchKey` (normalized title + year + type).
   - If a match is found → create a new `ExternalReference` linking to the existing `ContentUID`. (confidence=MEDIUM)
   - If no match → create a new `ContentUID` + the `ExternalReference`. (confidence=HIGH for first sighting)

#### Source switching (user switches from Aniyomi source 42 to Mangayomi)
1. User is watching "Attack on Titan" via Aniyomi source 42. ContentUID = X.
2. User switches to a Mangayomi source.
3. System searches for an `ExternalReference(ecosystem="mangayomi", sourceId=..., externalId=...)`.
4. If found → use ContentUID X. Watch progress carries over. ✅
5. If not found → fuzzy match by title. If match → create ExternalReference, use ContentUID X. Watch progress carries over. ✅
6. If no match → offer user "is this the same show?" → manual merge.

#### Tracker integration (AniList/MAL)
- Trackers are just another ecosystem in `ExternalReference`.
- `ExternalReference(ecosystem="anilist", sourceId=nil, externalId="12345")`.
- If content is on AniList, it gets an AniList ExternalReference. This becomes a high-confidence bridge — if both an Aniyomi source and a Mangayomi source match the same AniList entry, they're the same ContentUID.
- If content is NOT on any tracker, it still has its ContentUID + extension ExternalReferences. No reliance on trackers.

#### Cross-ecosystem matching (the hard part)
How do we know Aniyomi's "Attack on Titan" = Mangayomi's "Attack on Titan" = Cloudstream's "Attack on Titan"?
1. **Tracker bridge** (highest confidence): both match the same AniList/MAL entry → same ContentUID.
2. **Fuzzy title + year + type match** (medium confidence): normalized title + year + type (TV/movie/OVA) match → auto-link with MEDIUM confidence.
3. **User manual merge** (highest confidence): user says "these are the same" → merge ContentUIDs, confidence=HIGH.

#### User merge / split
- Users can merge two ContentUIDs they believe are the same.
- Users can split a ContentUID if the auto-matcher made a mistake.
- All merges/splits are logged (for undo).

### Why this is better than the old system
| Old | New |
|-----|-----|
| One ecosystem (Aniyomi) | 5+ ecosystems (Aniyomi, Mangayomi, Cloudstream, Kotatsu, Sora) |
| AniList-reliant | Tracker-optional (trackers are just another ecosystem) |
| Provider-prefixed ID (mixed concerns) | Clean separation: ContentUID (app) vs ExternalReference (external) |
| No match confidence | Explicit confidence levels (HIGH/MEDIUM/LOW) |
| No user merge/split | User can merge/split, logged for undo |
| Single content type | ContentUID has contentType (video/image/text) |

### Database schema (SQLDelight)
```sql
CREATE TABLE content_uid (
  uid TEXT PRIMARY KEY,
  content_type TEXT NOT NULL,  -- VIDEO | IMAGE | TEXT
  title TEXT NOT NULL,
  match_key TEXT NOT NULL,     -- normalized title + year + type
  created_at INTEGER NOT NULL
);

CREATE TABLE external_reference (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  uid TEXT NOT NULL REFERENCES content_uid(uid),
  ecosystem TEXT NOT NULL,     -- aniyomi | mangayomi | cloudstream | kotatsu | anilist | mal | shikimori
  source_id TEXT,              -- null for trackers
  external_id TEXT NOT NULL,
  confidence TEXT NOT NULL,    -- HIGH | MEDIUM | LOW
  created_at INTEGER NOT NULL,
  UNIQUE(ecosystem, source_id, external_id)
);

CREATE INDEX idx_ext_ref_uid ON external_reference(uid);
CREATE INDEX idx_content_match_key ON content_uid(match_key);

-- Episode-level identity (for watch progress, downloads)
CREATE TABLE episode_uid (
  uid TEXT PRIMARY KEY,
  content_uid TEXT NOT NULL REFERENCES content_uid(uid),
  episode_number REAL NOT NULL,
  match_key TEXT NOT NULL,     -- normalized title + number
  UNIQUE(content_uid, episode_number)
);

CREATE TABLE episode_external_ref (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  episode_uid TEXT NOT NULL REFERENCES episode_uid(uid),
  ecosystem TEXT NOT NULL,
  source_id TEXT,
  external_id TEXT NOT NULL,
  confidence TEXT NOT NULL,
  UNIQUE(ecosystem, source_id, external_id)
);
```

---

## 6. Multi-Extension Architecture (D-031)

### ExtensionProvider abstraction
```
interface ExtensionProvider {
  val ecosystemId: String                    // "aniyomi", "mangayomi", etc.
  val displayName: String                     // "Aniyomi", "Mangayomi"
  fun discoverSources(): Flow<List<Source>>
  fun installSource(source: Source): Flow<InstallState>
  fun fetchContentList(source: Source, page: Int): Flow<List<Content>>
  fun fetchContentDetails(content: Content): Flow<ContentDetails>
  fun fetchEpisodeList(content: Content): Flow<List<Episode>>
  fun fetchVideoList(episode: Episode): Flow<List<Video>>
}
```

### One impl per ecosystem
- `:data:extension-aniyomi` — loads Aniyomi APK extensions (Injekt compat).
- `:data:extension-mangayomi` — loads Mangayomi extensions (future).
- `:data:extension-cloudstream` — loads Cloudstream plugins (future).
- `:data:extension-kotatsu` — wraps Kotatsu parsers (future).
- `:data:extension-sora` — (future, if relevant).

### Registration
All providers registered in Koin as `single<List<ExtensionProvider>>`. Adding a new ecosystem = one module + one Koin binding. The `ExtensionManager` queries all providers.

### Source identity
Each source is identified by `(ecosystem, sourceId)`. This maps directly to `ExternalReference.ecosystem` + `ExternalReference.sourceId` in the identity system.

---

## 7. Multi-Content-Type Architecture (D-030)

### ContentType enum
```kotlin
enum class ContentType { VIDEO, IMAGE, TEXT }
```
- VIDEO = anime, movies, series (current focus).
- IMAGE = manga (later, modular).
- TEXT = novels (later, modular).

### Module structure per content type
```
:feature:anime-browse      ← VIDEO content type
:feature:anime-details
:feature:anime-watch
:feature:manga-browse      ← IMAGE content type (future)
:feature:manga-details
:feature:manga-read
:feature:novel-browse      ← TEXT content type (future)
:feature:novel-details
:feature:novel-read
```

### Nav3 integration
- `ContentMode` sealed interface in `:core:navigation-api`. `AnimeMode`, `MangaMode`, `NovelMode`.
- Mode switch = replace root of `List<NavKey>`.
- Tabs are mode-specific (anime has Watch tab, manga has Read tab).

### Data model
- `ContentUID.contentType` tags each content with its type.
- Repositories are content-type-aware (`AnimeRepository`, `MangaRepository`, `NovelRepository`).
- The identity system links content across ecosystems WITHIN a content type (an anime in Aniyomi = an anime in Mangayomi). Cross-type linking (an anime ↔ its manga adaptation) is a future feature, not needed now.

---

## 8. Updated Tech Stack (supersedes D-009)

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.2.0+ |
| UI | Jetpack Compose (BOM) | 2025.03.00+ |
| **DI** | **Koin 4.x + Koin Annotations 2.x** | (NOT Hilt) |
| **DI (ext compat)** | **Injekt (isolated)** | Aniyomi extensions only |
| **Persistence** | **SQLDelight 2.x** | (NOT Room) |
| **Navigation** | **Jetpack Navigation 3** | `androidx.navigation3` 1.0.0+ |
| Player | MPV (aniyomi-mpv-lib) | |
| Networking | OkHttp + ktor-client | |
| Coroutines | kotlinx-coroutines | |
| Serialization | kotlinx-serialization | |
| Extensions | Aniyomi-compatible (now), Mangayomi/Cloudstream/Kotatsu (future) | |
| Build | AGP 8.7+ / Gradle 8.11+ / JDK 17 | |
| SDK | compile/target 35, min 24 | |

---

## 9. Next Steps

1. **User reviews these recommendations** → confirms or adjusts.
2. If confirmed → write the **Phase 1 Architecture Plan** (full module tree, data flow, screen map).
3. Sub-agent review of the architecture plan.
4. Begin Phase 2 (project scaffold + core modules).

### Open questions for the user
- ❓ Confirm the identity system design (ContentUID + ExternalReference)? Or adjust?
- ❓ Confirm Koin over Hilt? (The research is strong, but this supersedes the earlier D-009 tentative Hilt decision.)
- ❓ Confirm Nav3 (it's very new, stable Nov 2025 — are you comfortable with a cutting-edge library)?
- ❓ Ad system: any ad formats beyond redirect/video/interstitial you want from the start?
- ❓ Activity tracking: 90-day retention OK? Or different?
