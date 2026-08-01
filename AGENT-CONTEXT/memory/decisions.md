# Decisions Log

> Record of key decisions. Each entry: what, why, when, status.

## Decisions

### D-001 — Build APKs via GitHub Actions only
- **What:** Never build APK locally. Always via GitHub Actions.
- **Why:** User requirement. Reproducible, no local Android toolchain needed.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0.

### D-002 — Build only ARM64-v8a and armeabi-v7a
- **What:** Restrict ABIs to these two. No x86/x86_64.
- **Why:** User requirement. Matches target devices, keeps APK small.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0.

### D-003 — AGENT-CONTEXT lives INSIDE the project repo (versioned)
- **What:** `AGENT-CONTEXT/` lives inside `ANIKUTA-PROJECT/` and is **versioned in the GitHub repo** so any future AI agent can clone and pick up immediately.
- **Why:** User requirement — the whole project folder (including agent context) is pushed to GitHub.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0 (updated).

### D-004 — Frontend/backend separation as core architecture
- **What:** UI layer and data layer are independent, communicating via contracts.
- **Why:** User wants highly customizable UI independent of backend.
- **Status:** ✅ Confirmed by user (stated as core idea).
- **Date:** Phase 0.

### D-005 — Modular app structure
- **What:** App logic split into independent modules, each with one responsibility + README.
- **Why:** User requirement for manageability and future-proofing.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0.

### D-006 — Companion web dashboard (full Next.js project → GitHub Pages)
- **What:** A full Next.js project at `ANIKUTA-PROJECT/dashboard/`. GitHub Actions builds and publishes it to **GitHub Pages** on every push.
- **Why:** User wants a visual representation of project logic, modules, progress, decisions — managed and kept up to date.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0.

### D-007 — App ID = com.confused.anikuta
- **What:** Android applicationId / namespace = `com.confused.anikuta`.
- **Why:** User-chosen.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0.

### D-008 — SDK levels: minSdk 24, targetSdk 35, compileSdk 35, JDK 17
- **What:** minSdk 24 (Android 7.0), targetSdk/compileSdk 35 (Android 15), JDK 17 for CI.
- **Why:** User-approved recommendations.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0.

### D-009 — Tech stack: Kotlin + Compose + Hilt + Room + Retrofit, latest stable
- **What:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.10.00), AGP 8.7.2, Gradle 8.11.1. Hilt/Room/Retrofit to be added in Phase 1.
- **Why:** User-approved; use latest stable versions.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0.

### D-010 — Project folder structure: ANIKUTA-PROJECT/
- **What:** Single root folder `ANIKUTA-PROJECT/` containing `AGENT-CONTEXT/`, `android/`, `dashboard/`, `.github/workflows/`. The whole folder is the git repo pushed to `testplay-byte/ANI-KUTA`.
- **Why:** User requirement — one project folder holding everything.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0.

## Pending Decisions (need user input)
All open items live in **`questions/open-questions.md`** (single source of truth). See that file.
