## You are about to work on ANI-KUTA

**ANI-KUTA** is an Android anime streaming/downloading app built with Kotlin + Jetpack Compose + MPV + SQLDelight + Koin. There is also a companion web dashboard (Next.js) that visualizes the project's architecture, decisions, and progress.

**GitHub repository:** `https://github.com/testplay-byte/ANI-KUTA`

---

## Your instructions — follow these steps IN ORDER

### Task 1 (your very first task): download and read CORE_RULES.md

**Before you do anything else — before setting up your environment, before opening any code, before reading any other documentation — read the core rules. https://github.com/testplay-byte/ANI-KUTA/blob/main/ANI-KUTA/AGENT-CONTEXT/CORE_RULES.md** (note: the default branch IS `main` — renamed from `feature/round-57-cloudstream-downloads` in Round 118, D-743)

This file contains non-negotiable rules that supersede everything else in the project. Pay special attention to:

- **§4 (Project Structure)** — folder organization, the single-wrapper-folder rule
- **§8 (GitHub Actions & Branching)** — builds are CI-ONLY. Never build locally. Never install the Android SDK/JDK locally. Never run Gradle locally. This is critical.
- **§5 (Code Rules)** — coding standards
- **§6 (Documentation Rules)** — documentation expectations
- **§7 (Architecture)** — the modular "Lego" architecture

**Do NOT skip this file. Do NOT skim it. Read every section before proceeding **

after reading the file clone the whole complete GitHub repo inside a newly created folder as in the rules (the default branch is `main` — renamed from `feature/round-57-cloudstream-downloads` in Round 118, D-743)

### Task 2: Read the rest of AGENT-CONTEXT

After CORE_RULES.md, read these files in order:

1. **`AGENT-CONTEXT/navigation.md`** — file index, tells you what every file is for
2. **`AGENT-CONTEXT/master.md`** — project orientation: what ANI-KUTA is, folder layout, tech stack
3. **`AGENT-CONTEXT/workflow.md`** — the task execution loop + project phases
4. **`AGENT-CONTEXT/SESSION.md`** — per-session bootstrap checklist (the current phase + standing orders live here — including the round-115 D-738 order: DEBUG BUILDS ONLY, no releases of any kind until the user's explicit order)
5. **`AGENT-CONTEXT/memory/progress.md`** — what's done, what's next, blockers (read the top CURRENT STATUS block + the Round sections at the BOTTOM first — the middle is historical)
6. **`AGENT-CONTEXT/memory/decisions.md`** — all architecture decisions (D-001 through D-741, newest at the top). Read the "Pending Decisions" section + the latest decisions.
7. **`AGENT-CONTEXT/memory/changelog.md`** — high-level change history
8. **`AGENT-CONTEXT/memory/lessons-learned.md`** — mistakes, corrections, insights, patterns (grep for tags matching your task type)
9. **`AGENT-CONTEXT/HANDOFF-ROUND-116.md`** — the round-116 delta handoff, written for YOU (the gate rebuild, the lost-predecessor story, the environment refresh) + **`AGENT-CONTEXT/HANDOFF-ROUND-115.md`** — the round-115 full original (the phase, the loop, the environment gotchas, the open items)
10. **`AGENT-CONTEXT/knowledge/`** — read every file in this folder:
   - `architecture.md` — the 57-module architecture
   - `tech-stack.md` — technologies used
   - `module-map.md` — all 57 modules (name, job, deps, key files — re-verified round 115)
   - `project-overview.md`
   - `dashboard.md`
   - `old-vs-new.md`
   - `ui-customization.md`
   - `emulator-testing.md` (before any emulator/adb work)

Take your time. Read thoroughly. The goal is to build a complete mental model of:

- What the app does
- How it's structured (modules, layers, DI, navigation, database)
- What's already built and what's pending
- What decisions have been made and why
- What lessons have been learned
- How the development workflow works

---

### Task 3: Understand the codebase structure

After reading the documentation, explore the actual code structure:

- `ANI-KUTA/APP/ani-kuta/` — look at `settings.gradle.kts` to see all modules (57: 1 app + 33 core + 2 data + 21 feature)
- `ANI-KUTA/APP/ani-kuta/core/` — the core modules (database, network, common, etc.)
- `ANI-KUTA/APP/ani-kuta/feature/` — the feature modules (12 features: anime-details, anime-browse, anime-library, anime-search, anime-history, watch, cs-watch, extensions-settings, updates, download, onboarding, debug-bubble)
- `ANI-KUTA/APP/ani-kuta/app/` — the app module (wiring, MainActivity, navigation)
- `ANI-KUTA/APP/ani-kuta/core/database/src/main/sqldelight/` — the SQLDelight `.sq` files (database schema — 25 tables / 17 files)
- `ANI-KUTA/APP/ani-kuta/DOCUMENTATION/cloudstream-v2/` — the round records (docs 72-97 = rounds 90-115; the newest = the current state)
- `ANI-KUTA/APP/ani-kuta/DOCUMENTATION/release/` — the build & release guides (RELEASE-PLAYBOOK.md + BUILD-AND-BRANCH-GUIDE.md — dormant under D-738 until the user orders a release)

You don't need to read every Kotlin file — just understand the module layout and how things connect. you can use multiple sub agents for this too

---

## IMPORTANT — Do NOT do any of these

- **Do NOT make any changes to any file.** No code changes, no documentation changes, no config changes.
- **Do NOT create any branches.**
- **Do NOT start any work or tasks.**
- **Do NOT install the Android SDK, JDK, or any Android build tooling.** (CORE_RULES §8)
- **Do NOT run any Gradle commands.** (CORE_RULES §8)
- **Do NOT build the APK locally.** (CORE_RULES §8)
- **Do NOT guess or assume anything.** If something is unclear after reading the docs, note your question and wait.

---

## When you're done

Once you have:

- Read CORE_RULES.md in full
- Read all of AGENT-CONTEXT (navigation → master → workflow → SESSION → memory/* → knowledge/*)
- Explored the codebase structure
- Built a complete understanding of the project

**Stop.** Tell the user you've finished reading and understanding the project. Summarize what you've learned in a few sentences so the user can confirm you understand correctly. Then **wait** — the user will tell you what your task is and how they want it done. Do not start any work until given explicit instructions.




and lastly here are the credentials.
