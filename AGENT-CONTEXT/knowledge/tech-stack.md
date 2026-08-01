# Tech Stack (Proposed)

> Pending user confirmation. Recommendations marked with ⭐.

## Android App
| Layer | Technology | Notes |
|-------|-----------|-------|
| Language | ⭐ Kotlin | Modern, official. |
| UI | ⭐ Jetpack Compose | Declarative, highly customizable. |
| DI | ⭐ Hilt (Dagger) | Standard, well-supported. |
| Async | Kotlin Coroutines + Flow | Default for modern Android. |
| Network | ⭐ Retrofit + OkHttp | Mature, widely used. |
| Local Storage | ⭐ Room | Jetpack standard. |
| Navigation | Compose Navigation | Matches UI stack. |
| JSON | kotlinx.serialization or Moshi | TBD. |
| Image Loading | Coil | Compose-friendly. |

## Build & CI
| Tool | Use |
|------|-----|
| Gradle (Kotlin DSL) | Build system. |
| GitHub Actions | APK builds (ARM64-v8a + armeabi-v7a only). |
| JDK 17 (or 21) | TBD. |

## Companion Web Dashboard
| Tool | Use |
|------|-----|
| Next.js 16 + TypeScript | Dashboard app. |
| Tailwind CSS + shadcn/ui | Styling. |

## Open Questions
- ❓ Confirm Kotlin + Compose?
- ❓ Confirm Hilt / Room / Retrofit?
- ❓ Min SDK / Target SDK?
- ❓ JDK version for CI?
