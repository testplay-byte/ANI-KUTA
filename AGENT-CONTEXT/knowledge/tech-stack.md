# Tech Stack

> Updated for Phase 1 (supersedes D-009's tentative Hilt+Room decision).
> Research-backed recommendations in `REFERENCES/old-kuta/DOCUMENTATION/10-14-*.md`.

## Android App (`APP/ani-kuta/`)
| Layer | Technology | Version | Status |
|-------|-----------|---------|--------|
| Language | Kotlin | 2.2.0+ | ✅ confirmed |
| UI | Jetpack Compose (BOM) | 2025.03.00+ | ✅ confirmed |
| **DI** | **Koin + Koin Annotations** | 4.x + 2.x | 🚧 recommended (D-034) |
| **DI (ext compat)** | **Injekt (isolated)** | Aniyomi ext only | 🚧 recommended (D-034) |
| **Persistence** | **SQLDelight** | 2.x | 🚧 recommended (D-035) |
| **Navigation** | **Jetpack Navigation 3** | `androidx.navigation3` 1.0.0+ | 🚧 recommended (D-036) |
| Player | MPV (aniyomi-mpv-lib) | — | ✅ confirmed |
| Networking | OkHttp + ktor-client | — | TBD |
| Coroutines | kotlinx-coroutines | — | ✅ confirmed |
| Serialization | kotlinx-serialization | 2.2.0 | ✅ confirmed |
| Extensions | Aniyomi-compat (now), Mangayomi/Cloudstream/Kotatsu (future) | — | ✅ confirmed (D-027, D-031) |
| Build | AGP | 8.7+ | ✅ confirmed |
| Build | Gradle | 8.11+ | ✅ confirmed |
| Build | JDK | 17 | ✅ confirmed |
| SDK | compile/target | 35 (Android 15) | ✅ confirmed |
| SDK | min | 24 (Android 7.0) | ✅ confirmed |

## Ad System (D-033)
| Component | Technology |
|-----------|-----------|
| Ad formats | `AdFormat` interface + Koin `List<AdFormat>` registry |
| Ad placements | JSON config (`assets/ad_placements.json`) + `AdPlacementRegistry` |
| Ad source | `AdSource` interface (`LocalAdSource` default) |
| State | Per-interaction `StateFlow<AdInteractionState>` + `SharedFlow<AdEvent>` |
| Activity tracking | SQLDelight event-log (`:core:activity-tracker`) |
| Active detection | `ActivityDetector` (ProcessLifecycle + onUserInteraction + PowerManager) |

## Identity System (D-032)
| Component | Technology |
|-----------|-----------|
| Model | Graph-based: `ContentUID` + `ExternalReference` |
| Storage | SQLDelight (content_uid, external_reference, episode_uid, episode_external_ref tables) |
| Matching | Tracker bridge (high) + fuzzy title/year/type (medium) + user merge (high) |
| Confidence | HIGH / MEDIUM / LOW per ExternalReference |

## Build & CI
| Tool | Use |
|------|-----|
| Gradle (Kotlin DSL) | Build system. |
| Convention plugins | `:build-logic` (anikuta.library, anikuta.library.compose, etc.) |
| GitHub Actions | APK builds (ARM only) + dashboard deploy. |
| JDK 17 | CI. |

## Web Dashboard (`DASHBOARD/webpage/`)
| Tool | Use |
|------|-----|
| Next.js 16 | Dashboard app (static export). |
| Tailwind CSS 4 | Styling. |
| GitHub Pages | Hosting. |

## Research Documents
Detailed research + architecture docs live in `APP/ani-kuta/DOCUMENTATION/`:
- `10-db-research.md` — Room vs SQLDelight → SQLDelight
- `11-di-research.md` — Hilt vs Koin → Koin + Injekt (isolated)
- `12-nav-research.md` — Voyager vs Compose Nav → Nav3
- `13-ads-research.md` — Ad system + activity tracking design
- `14-architecture-recommendations.md` — Full synthesis + identity system redesign
- `15-backup-research.md` — Backup/restore formats (Aniyomi, Mangayomi)
- `16-phase1-architecture-plan.md` — Full Phase 1 architecture plan
