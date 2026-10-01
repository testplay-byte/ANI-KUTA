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
│   ├── APP/ani-kuta/            # Android app — 57 Gradle modules (Gradle + Kotlin + Jetpack Compose)
│   ├── DASHBOARD/webpage/       # Next.js visualization dashboard → GitHub Pages
│   └── REFERENCES/              # Read-only references (old-kuta + animiru + webview-cloudflare-captcha)
└── .github/workflows/           # CI: build APK + release workflows + deploy dashboard (repo-root level)
```

> New here? Read `ANI-KUTA/AGENT-CONTEXT/SESSION.md` first, then `ANI-KUTA/AGENT-CONTEXT/CORE_RULES.md`.

## Build
- APKs are built **only** via GitHub Actions (`.github/workflows/build-apk.yml`) — never locally.
- The push path builds the **debug** APK, `arm64-v8a` only (D-445). Shipped releases are all-ABI (arm64-v8a / armeabi-v7a / x86 / x86_64 + universal), release-signed in CI (D-423).
- App ID: `com.confused.anikuta` (debug builds carry the `.debug` suffix — co-installable).

## Status
All original build phases complete (0-5 + B/C/D/DL/WP/HI/UP/SC/TR/NOTIF/CW) + a long device-feedback polish loop — debug line at **v1.1.71**, professional line at **v1.1.14** (the official repo: Confused-Creature-180/ANI-KUTA + the website). Since round 115: **debug builds only — all releases paused until the user's explicit order** (D-738).
See `ANI-KUTA/AGENT-CONTEXT/memory/progress.md` (top block) + `ANI-KUTA/AGENT-CONTEXT/HANDOFF-ROUND-115.md` for the live state.
