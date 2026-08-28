# Tech Stack

> Actual technologies + versions used in the ANI-KUTA app (verified against
> `gradle/libs.versions.toml` + `build-logic/.../AndroidConfig.kt`).
> Research docs: `APP/ani-kuta/DOCUMENTATION/10-14-*.md`.

## Android App (`APP/ani-kuta/`)
| Layer | Technology | Version | Status |
|-------|-----------|---------|--------|
| Language | Kotlin | 2.2.0 | ✅ in use |
| UI | Jetpack Compose (explicit pins — BOM removed D-322) | 1.10.4 line (material3 1.3.1, icons 1.7.8) | ✅ in use |
| DI (primary) | Koin + Koin Annotations | 4.2.2 | ✅ in use (D-034) |
| DI (ext compat) | Injekt (isolated) | commit `91edab2317` | ✅ in use (Aniyomi ext binary-compat only) |
| Persistence | SQLDelight | 2.0.2 | ✅ in use (D-035 — NOT Room) |
| Navigation | Hand-rolled (`mutableStateListOf<NavKey>` + `when` dispatch) | — | ✅ in use (D-150 — Nav3 REMOVED) |
| Player | MPV (aniyomi-mpv-lib) | 1.18.n | ✅ in use (D-044) |
| Player wrapper | FFmpegKit + Seeker + MediaSession | 1.18 / 1.2.2 / 1.4.1 | ✅ in use |
| Networking | OkHttp | 5.0.0-alpha.14 | ✅ in use (MUST match Aniyomi ext — D-092 lesson) |
| Image loading | Coil 3 | 3.0.4 | ✅ in use (500MB disk cache — D.4) |
| Coroutines | kotlinx-coroutines | 1.9.0 | ✅ in use |
| Serialization | kotlinx-serialization-json | 1.7.3 | ✅ in use |
| WorkManager | androidx.work | 2.10.0 | ✅ in use (Phase UP — smart update engine) |
| Extensions | Aniyomi-compat (now); Mangayomi/Cloudstream/Kotatsu (future) | — | ✅ Aniyomi compat done (D-027, D-031) |
| Logging | logcat (Logger wrapper) | 0.1 | ✅ in use (CORE_RULES §20) |
| Source API deps | jsoup 1.19.1, rxjava 1.3.8, nanohttpd 2.3.1, androidx-preference 1.2.1 | — | ✅ in use (Aniyomi binary-compat) |

## Build & SDK
| Setting | Value | Source |
|---------|-------|--------|
| AGP | 8.9.1 | `libs.versions.toml` |
| Gradle | 8.11.1 | `libs.versions.toml` |
| JDK | 17 | CI (`setup-java@v4`) |
| compileSdk | 36 | `AndroidConfig.kt` (kept at 36 for the compose 1.10 line + future-proofing; was originally for Nav3, Nav3 removed D-150) |
| targetSdk | 36 | `AndroidConfig.kt` |
| minSdk | 24 (Android 7.0) | `AndroidConfig.kt` |
| ABIs | `arm64-v8a` ONLY (test-only x86_64 emulator builds never ship) | `AndroidConfig.abiFilters` (CORE_RULES §8, D-251 — CI-verified) |
| App ID | `com.confused.anikuta` | `AndroidConfig.kt` |
| Build system | `:build-logic` composite build (4 convention plugins) | `settings.gradle.kts` |
| Convention plugins | `anikuta.android.application`, `anikuta.android.application.compose`, `anikuta.library`, `anikuta.library.compose` | `build-logic/src/main/kotlin/` |

## SQLite Version Constraint (important)
- minSdk 24 → API 24-28 ships SQLite **3.9-3.22** (system SQLite via `AndroidSqliteDriver`).
- `INSERT ... ON CONFLICT DO UPDATE` (UPSERT) requires SQLite **3.24+** → **cannot use** on API 24-28.
- `INSERT OR REPLACE` is the workaround (delete + reinsert; callers read-then-write). See D-166.
- CHECK constraints can't be added via `ALTER TABLE` on existing installs — would need table rebuild. Deferred (D-166).
- Debug builds can rebuild the schema freely (CORE_RULES §30) — no migration scripts needed until production approach.

## Ad System (D-033 — deferred, designed)
| Component | Technology |
|-----------|-----------|
| Ad formats | `AdFormat` interface + Koin `List<AdFormat>` registry |
| Ad placements | JSON config (`assets/ad_placements.json`) + `AdPlacementRegistry` |
| Ad source | `AdSource` interface (`LocalAdSource` default) |
| State | Per-interaction `StateFlow<AdInteractionState>` + `SharedFlow<AdEvent>` |
| Activity tracking | SQLDelight event-log (`:core:activity-tracker` — BUILT) |
| Active detection | `ActivityDetector` (ProcessLifecycle + onUserInteraction + PowerManager) |

## Identity System (D-032 → simplified in Phase C, D-135)
The original graph-based `ContentUID + ExternalReference` design was simplified in Phase C to a pragmatic **two-ID system** (Main ID + Content ID) with lookup tables. See `APP/ani-kuta/DOCUMENTATION/planning/extension-details-page/PHASE-C-PLAN.md` + D-135.
| Component | Technology |
|-----------|-----------|
| Main ID | Stable UUID per content (stored in `content` table) |
| Content ID | 6-section composite (sourceId + system + repo + ext + animeUrl + ...) |
| Detail tables | `anilist_detail`, `extension_detail`, `other_source_detail` (per source type) |
| Lookup tables | `data_source`, `system`, `content_ext_repo`, `content_ext` (seeded) |
| Cross-source dedup | `ContentRepository.resolveContentForExtension` checks auto-link cache (D-137) |

## Web Dashboard (`DASHBOARD/webpage/`)
| Tool | Use |
|------|-----|
| Next.js 16 | Dashboard app (static export → GitHub Pages). |
| Tailwind CSS 4 | Styling. |
| TypeScript 5 | Language. |
| GitHub Pages | Hosting (`https://testplay-byte.github.io/ANI-KUTA/`). |
| GitHub Actions | Build + deploy on push to `main`. |

## CI
| Workflow | Purpose | File |
|----------|---------|------|
| `build-apk.yml` | Build debug APK + verify ABIs (arm64-v8a ONLY — D-251). Triggers on `main` + `feature/**` + tags. | `.github/workflows/build-apk.yml` |
| `deploy-dashboard.yml` | Build Next.js dashboard + deploy to GitHub Pages. Triggers on `main`. | `.github/workflows/deploy-dashboard.yml` |

## Research Documents (historical — in `APP/ani-kuta/DOCUMENTATION/`)
- `10-db-research.md` — Room vs SQLDelight → SQLDelight (D-035).
- `11-di-research.md` — Hilt vs Koin → Koin + Injekt isolated (D-034).
- `12-nav-research.md` — Voyager vs Compose Nav → Nav3 recommended. **RESOLVED (D-150):** Nav3 removed; hand-rolled nav kept.
- `13-ads-research.md` — Ad system + activity tracking design (D-033, deferred).
- `14-architecture-recommendations.md` — Full synthesis + identity system redesign.
- `15-backup-research.md` — Backup/restore formats (Aniyomi, Mangayomi) — deferred (D-047).
- `16-phase1-architecture-plan.md` — Phase 1 architecture plan (43 proposed modules; actual grew to 46).
- `17-database-schema.md` — Original DB schema design (21 tables proposed; actual is 28 — see note in file).
