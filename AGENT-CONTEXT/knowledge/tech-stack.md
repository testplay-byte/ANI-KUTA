# Tech Stack

> Confirmed by user. Using latest stable versions.

## Android App (`APP/ani-kuta/`)
| Layer | Technology | Version | Notes |
|-------|-----------|---------|-------|
| Language | Kotlin | 2.0.21 | Modern, official. |
| UI | Jetpack Compose | BOM 2024.10.00 | Declarative, customizable. |
| Build | Android Gradle Plugin | 8.7.2 | |
| Build | Gradle | 8.11.1 | |
| Min SDK | Android 7.0 | API 24 | ~98% device coverage. |
| Target/Compile SDK | Android 15 | API 35 | |
| DI | Hilt | TBD Phase 1 | To be added. |
| Async | Coroutines + Flow | (via Kotlin) | |
| Network | Retrofit + OkHttp | TBD Phase 1 | To be added. |
| Storage | Room | TBD Phase 1 | To be added. |
| Navigation | Compose Navigation | TBD Phase 1 | |
| Image Loading | Coil | TBD Phase 1 | Compose-friendly. |

## Build & CI
| Tool | Use |
|------|-----|
| Gradle (Kotlin DSL) | Build system. |
| GitHub Actions | APK builds (ARM only) + dashboard deploy. |
| JDK 17 | CI. |

## Web Dashboard (`DASHBOARD/webpage/`)
| Tool | Use |
|------|-----|
| Next.js | Dashboard app (planned). |
| Tailwind CSS | Styling (planned). |
| GitHub Pages | Hosting (planned). |
