# Project Overview — ANI-KUTA

> What this project is.

## Summary
ANI-KUTA is an Android anime streaming/downloading app, rebuilt from scratch from a previous working version ("old-kuta", now a read-only reference in `REFERENCES/old-kuta/`). Goals: modular, highly customizable UI (independent of backend), future-proof, well-documented. A companion Next.js dashboard visualizes the project.

## Goals
1. **Well-documented** — every module, decision, and change recorded. `AGENT-CONTEXT/` is the agent's memory; `APP/ani-kuta/DOCUMENTATION/` is the technical docs; the dashboard is the visual view.
2. **Modular** — 50 Gradle modules, each with one responsibility. UI separate from backend per screen (CORE_RULES §7).
3. **Highly customizable UI** — frontend independent of backend. 10 accent presets + CUSTOM, AMOLED, adaptive colors, subtitle settings, layout options.
4. **Future-proof** — multi-extension architecture (D-031), multi-content-type (D-030), flexible identity system (D-032), swappable player module (D-044).
5. **Manageable** — companion web dashboard + `AGENT-CONTEXT/` versioned in repo so any future AI agent can clone + pick up immediately.

## What We Know (all answered)
- **Q1 (what does the app do?)**: Android anime streaming/downloading app. Aniyomi-extension-compatible. MPV player. AniList metadata + tracking. Internal activity tracking. Library, history, schedule, downloads, notifications, ratings.
- **Q2 (where is the old project?)**: `REFERENCES/old-kuta/ANIKUTA/` — 36 modules, 643 files, 10-file analysis in `REFERENCES/old-kuta/DOCUMENTATION/`.
- **Q10 (dashboard scope?)**: Module map + progress + decisions + flow diagrams + analytics + planning + database + design + debug-bubble + testing. Read-only. 14 pages.

## Key Facts
- **GitHub**: `testplay-byte/ANI-KUTA`
- **App ID**: `com.confused.anikuta`
- **APK builds**: GitHub Actions only — `arm64-v8a` ONLY (CORE_RULES §8, D-251). Never local.
- **Tech**: Kotlin 2.2.0 + Compose (explicit 1.10.4-line pins — BOM removed D-322) + MPV (aniyomi-mpv-lib 1.18.n) + SQLDelight 2.0.2 + Koin 4.2.2 + Injekt (isolated) + Coil 3.0.4 + OkHttp 5.0.0-alpha.14. compileSdk 36, minSdk 24.
- **Scale**: 50 modules, 25 SQLDelight tables, 408 Kotlin files, D-001..D-326 decisions, 180+ lessons learned.

## Scope
- **Android app** (primary) — `APP/ani-kuta/`.
- **Companion web dashboard** (Next.js → GitHub Pages) — `DASHBOARD/webpage/`.
- **Agent context** (versioned in repo) — `AGENT-CONTEXT/`.
- **References** (read-only) — `REFERENCES/` (old-kuta + animiru).

## Current Status
- **ALL major phases complete** (0-4, 5a/5b/5c, B/C/D/DL/WP/HI/UP/SC/TR/NOTIF/CW, Debug Bubble, Profile UI v1-v6). On `main`, CI green.
- **Debug builds only** — no production users, no published APK. Schema can be rebuilt freely (CORE_RULES §30).
- **Next focus**: Database management + quality (user-directed this session).
- See `memory/progress.md` for live status + Deferred Concerns.
