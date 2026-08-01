# Open Questions for the User

> Questions that block real progress. Answer these to unblock the next phase.
> Each has a ⭐ recommended option so you can just confirm.

---

## Q1 — What does the app actually do? ⏳ STILL OPEN
❓ What is ANI-KUTA? What are its main features and screens?
- You said you'll share the older GitHub project so we can analyze it. Waiting on that link/path.

---

## Q2 — Where is the old project? ⏳ STILL OPEN
❓ Share the repo link or path to the previous (working) version.
- We'll use it only as a reference for features/logic, not copy code.

---

## ✅ Answered (locked in)
- **Tech stack:** Kotlin + Jetpack Compose + Hilt + Room + Retrofit, latest stable. (Q3)
- **App ID:** `com.confused.anikuta`. (Q4)
- **SDK:** minSdk 24, targetSdk 35, compileSdk 35, JDK 17. (Q5/Q6)
- **AGENT-CONTEXT:** lives inside the repo, versioned on GitHub. (Q7 — settled by restructure)
- **Web dashboard:** full Next.js project → GitHub Pages, auto-deployed on push. Scope rules to be defined. (Q8)
- **Release signing:** to be set up via GitHub Actions secrets in Phase 2. (Q9)

---

## Q10 — Dashboard scope rules
❓ Now that the dashboard is confirmed (full Next.js → GitHub Pages), what should it show?
- ⭐ Recommended starter scope:
  - Module map (visual graph of all modules + dependencies)
  - Progress (read from `AGENT-CONTEXT/memory/progress.md`)
  - Decisions log (read from `AGENT-CONTEXT/memory/decisions.md`)
  - Open questions (read from `AGENT-CONTEXT/questions/open-questions.md`)
  - Logic/data flow diagram
- Read-only to start; interactive editing later. Confirm or adjust.
