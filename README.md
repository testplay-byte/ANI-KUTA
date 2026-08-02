# ANIKUTA-PROJECT

Android application **ANI-KUTA**, rebuilt from scratch with proper planning, documentation, and a modular, customizable, future-proof architecture.

## 🌐 Live Dashboard
**[https://testplay-byte.github.io/ANI-KUTA/](https://testplay-byte.github.io/ANI-KUTA/)** — visual documentation of the project (modules, decisions, progress, architecture). Auto-deployed to GitHub Pages on every push.

## Repository layout
```
ANIKUTA-PROJECT/
├── AGENT-CONTEXT/      # Agent memory + rules (versioned here — read SESSION.md first)
├── APP/ani-kuta/       # Android app (Gradle + Kotlin + Jetpack Compose)
├── DASHBOARD/webpage/  # Next.js visualization dashboard → GitHub Pages
├── REFERENCES/old-kuta/  # Old project reference + analysis docs
└── .github/workflows/  # CI: build APK + deploy dashboard
```

> New here? Read `AGENT-CONTEXT/SESSION.md` first, then `AGENT-CONTEXT/CORE_RULES.md`.

## Build
- APKs are built **only** via GitHub Actions (`.github/workflows/build-apk.yml`).
- Target ABIs: `arm64-v8a` and `armeabi-v7a` only (enforced in `APP/ani-kuta/app/build.gradle.kts` + verified in CI).
- App ID: `com.confused.anikuta`

## Status
Phase 0 — Environment, rules, dashboard demo all done. Phase 1 (architecture) in progress.
See `AGENT-CONTEXT/memory/progress.md` for live status.
