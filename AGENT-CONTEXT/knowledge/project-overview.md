# Project Overview — ANI-KUTA

> What this project is.

## Summary
ANI-KUTA is an Android application, rebuilt from scratch from a previous working version.

## Goals
1. **Well-documented** — every module, decision, and change recorded.
2. **Modular** — logic split into independent modules; UI separate from backend per screen.
3. **Highly customizable UI** — frontend independent of backend.
4. **Future-proof** — clean architecture, easy to extend.
5. **Manageable** — a companion web dashboard visualizes the project.

## What We Know
- Old version works but was not planned/documented/structured properly.
- New version is a clean rebuild in a fresh GitHub repo.
- APKs built only via GitHub Actions, `arm64-v8a` + `armeabi-v7a` only.
- App ID: `com.confused.anikuta`.

## What We Don't Know Yet
- ❓ Q1: What does the app do? (need old project to analyze — see `memory/decisions.md` Pending)
- ❓ Q2: Where is the old project? (repo link/path)
- ❓ Q10: Dashboard scope confirmation.

## Scope
- Android app (primary) — `APP/ani-kuta/`.
- Companion web dashboard (Next.js → GitHub Pages) — `DASHBOARD/webpage/`.
- Agent context (versioned in repo) — `AGENT-CONTEXT/`.
