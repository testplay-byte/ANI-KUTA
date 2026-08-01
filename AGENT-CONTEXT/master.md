# MASTER FILE — Read This First

> This is the single source of truth for how the AI agent must behave on the ANI-KUTA project.
> Every agent (current or future) MUST read this file before doing any work.

---

## 1. What This Project Is

**ANI-KUTA** is an Android application, rebuilt from scratch.

- There is an older working version (not in this repo) that functioned but lacked planning, documentation, and structure.
- This new version will be: well-documented, modular, highly customizable, future-proof, and manageable.
- The GitHub repo (`https://github.com/testplay-byte/ANI-KUTA`) starts empty. We build everything here.

The companion **web dashboard** is a full Next.js project at `ANIKUTA-PROJECT/dashboard/`. GitHub Actions builds and publishes it to **GitHub Pages** on every push. It visualizes: logic flow, module map, progress, and decisions. It is **not yet built** — scheduled for Phase 6 (see `planning/README.md`).

---

## 2. Workspace Layout

```
/home/z/my-project/                    <- dev sandbox root (Next.js dashboard app lives here separately)
├── ANIKUTA-PROJECT/                   <- THE project (Git repo, pushed to github.com/testplay-byte/ANI-KUTA)
│   ├── .github/workflows/             <- CI: build APK (+ deploy dashboard, planned)
│   ├── .gitignore  .gitattributes
│   ├── README.md
│   ├── AGENT-CONTEXT/                 <- agent memory + rules (VERSIONED in the repo)
│   │   ├── master.md                  <- you are here
│   │   ├── navigation.md              <- index of every file
│   │   ├── rules/                     <- operating rules
│   │   ├── memory/                    <- progress + decisions log
│   │   ├── knowledge/                 <- project knowledge base
│   │   ├── skills/                    <- reusable skills/checklists
│   │   ├── planning/                  <- phase plans
│   │   └── questions/                 <- open questions for the user
│   ├── android/                       <- Android app (Gradle + Kotlin + Jetpack Compose)
│   │   ├── settings.gradle.kts, build.gradle.kts, gradle.properties
│   │   ├── gradle/ (wrapper + libs.versions.toml)
│   │   ├── gradlew, gradlew.bat
│   │   └── app/                        <- the app module (applicationId: com.confused.anikuta)
│   └── dashboard/                     <- Next.js visualization dashboard (planned → GitHub Pages)
└── worklog.md                         <- workspace-level sub-agent execution log
```

---

## 3. Non-Negotiable Rules

### Build Rules
- **NEVER** build the Android APK locally. Always use **GitHub Actions**.
- Only build **arm64-v8a** (ARM 64-bit) and **armeabi-v7a** (ARM 32-bit) ABIs. No x86, no x86_64.
  - Enforced in `android/app/build.gradle.kts` (`ndk.abiFilters`) AND verified post-build in CI.
- App ID: `com.confused.anikuta`.
- The GitHub token is scoped to this repo only; safe to use in Actions.
- The companion **web dashboard** is a full Next.js project (`ANIKUTA-PROJECT/dashboard/`) that GitHub Actions builds and publishes to **GitHub Pages** on every push.

### Communication Rules
- Keep responses **short, simple, easy to understand**.
- The user reads only the **highlighted key points**. Lead with those.
- **Ask questions** when unsure. Never blind-guess.
- Use `❓` for questions and `✅` for confirmed decisions.

### Workflow Rules
- **Plan first, build second.** No random decisions.
- Use **sub-agents** to find flaws in plans. Verify their findings before acting.
- **Always read** `AGENT-CONTEXT/navigation.md` and `memory/progress.md` before starting work.
- **Always append** to `memory/progress.md` and `memory/decisions.md` after finishing work.
- Keep documentation **up to date** at all times.

### Architecture Rules
- **Frontend and backend must be separable.** UI customization independent of data logic.
- Split app logic into **independent modules**. Each module owns one responsibility.
- Every module must be **documented** with its purpose, inputs, outputs, and dependencies.

---

## 4. How To Behave (Agent Operating Loop)

1. **Read** `navigation.md` + `memory/progress.md` to know current state. Optionally consult `worklog.md` (workspace-level sub-agent execution log) for detail.
2. **Check** `questions/open-questions.md` for anything blocking.
3. **Plan** the next step. Write the plan into `planning/`.
4. **Stress-test** the plan with a sub-agent (find flaws). Verify findings yourself.
5. **Execute** the plan. Build frontend first, then backend.
6. **Document** what changed: update `memory/progress.md`, `memory/decisions.md`, and relevant `knowledge/` files. Sub-agents append to `worklog.md`.
7. **Verify** with the agent-browser / lint / tests before declaring done.
8. **Report** to the user with short, highlighted summary.

---

## 5. Golden Principles

- **Clarity over cleverness.** Simple code wins.
- **Document as you go.** Never "later."
- **One module, one responsibility.**
- **Customizability is a feature, not an afterthought.**
- **Future agents should be able to pick up from where you stopped.**

---

## 6. Status

- **Phase:** 0 — Environment & Rules Setup
- **Next step:** Answer open questions, then begin Phase 1 (architecture planning).

See `memory/progress.md` for the live status.
