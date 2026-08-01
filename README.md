# ANIKUTA-PROJECT

Android application **ANI-KUTA**, rebuilt from scratch with proper planning, documentation, and a modular, customizable, future-proof architecture.

## Repository layout
```
ANIKUTA-PROJECT/
├── AGENT-CONTEXT/      # Agent memory, rules, knowledge, planning (versioned here)
├── android/            # Android app (Gradle + Kotlin + Jetpack Compose)
├── dashboard/          # Next.js visualization dashboard (planned → GitHub Pages)
└── .github/workflows/  # CI: build APK (+ deploy dashboard, planned)
```

> New here? Read `AGENT-CONTEXT/master.md` first, then `AGENT-CONTEXT/navigation.md`.

## Build
- APKs are built **only** via GitHub Actions (`.github/workflows/build-apk.yml`).
- Target ABIs: `arm64-v8a` and `armeabi-v7a` only (enforced in `app/build.gradle.kts` + verified in CI).
- App ID: `com.confused.anikuta`

## Status
Phase 0 — Environment & Rules Setup (demo build to verify CI).
See `AGENT-CONTEXT/memory/progress.md` for live status.
