# ANIKUTA-PROJECT

Android application **ANI-KUTA**, rebuilt from scratch with proper planning, documentation, and a modular, customizable, future-proof architecture.

## 🌐 Live Dashboard
**https://testplay-byte.github.io/ANI-KUTA/** — visual documentation of the project (modules, decisions, progress, architecture). Auto-deployed to GitHub Pages on every push.

## Repository layout

**Per CORE_RULES §4:** The repo root contains exactly ONE wrapper folder (`ANI-KUTA/`). All four project zones live inside it. The `.github/` folder stays at repo root (GitHub Actions platform constraint — workflows must be at `<repo-root>/.github/workflows/`).

```
repo-root/
├── ANI-KUTA/                    ← single wrapper folder (all project zones inside)
│   ├── AGENT-CONTEXT/           # Agent memory + rules (read SESSION.md first)
│   ├── APP/ani-kuta/            # Android app (Gradle + Kotlin + Jetpack Compose)
│   ├── DASHBOARD/webpage/       # Next.js visualization dashboard → GitHub Pages
│   └── REFERENCES/old-kuta/     # Old project reference + analysis docs
└── .github/workflows/           # CI: build APK + deploy dashboard (repo-root level)
```

> New here? Read `ANI-KUTA/AGENT-CONTEXT/SESSION.md` first, then `ANI-KUTA/AGENT-CONTEXT/CORE_RULES.md`.

## Build
- APKs are built **only** via GitHub Actions (`.github/workflows/build-apk.yml`).
- Target ABIs: `arm64-v8a` and `armeabi-v7a` only (enforced in `ANI-KUTA/APP/ani-kuta/app/build.gradle.kts` + verified in CI).
- App ID: `com.confused.anikuta`

## Status
Phase 0 — Environment, rules, dashboard demo all done. Phase 5d (extension details + auto-link) complete.
See `ANI-KUTA/AGENT-CONTEXT/memory/progress.md` for live status.
