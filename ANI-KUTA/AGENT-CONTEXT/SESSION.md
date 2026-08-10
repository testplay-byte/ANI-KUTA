# SESSION — Read This At The Start Of Every Session

> A 60-second orientation. Read this FIRST, every time, before any work.
> It reminds you of the key rules and the session loop. For detail, follow the links.

---

## ⚡ Who You Are
You are the AI agent for **ANI-KUTA** (Android app rebuild + companion web dashboard).
GitHub: `testplay-byte/ANI-KUTA`. Repo root contains a single wrapper folder `ANI-KUTA/` (per CORE_RULES §4). Active branch: `main` (all feature branches — `download-system-plan`, `feature/watch-progress-history-updates`, `feature/debug-bubble` — have been merged + deleted).

## 📂 If The Environment Was Just Cloned
1. `cd /home/z/my-project/ANI-KUTA` (if missing → re-clone from GitHub; the wrapper folder is `ANI-KUTA/ANI-KUTA/` inside).
2. Read `AGENT-CONTEXT/memory/progress.md` → know what's done, what's next, blockers (read the top "Current Phase" + "Known doc debt" sections first).
3. Read `AGENT-CONTEXT/memory/decisions.md` → "Pending Decisions" section (latest = D-165; all pending items answered).
4. Read `AGENT-CONTEXT/memory/lessons-learned.md` → grep for tags matching your task.

## 🔑 Key Rules (full detail in `CORE_RULES.md`)
- **No assumptions.** Unsure → ask the user. Never guess.
- **Don't sugarcoat.** If a request has an issue, flag it directly. Don't blindly agree.
- **User uses speech-to-text.** If a request feels off, correct obvious errors from context; if still unclear → stop and ask.
- **APK builds: GitHub Actions only.** ABIs: `arm64-v8a` + `armeabi-v7a` only. Never local.
- **Sub-agents for webpage work** → they work ONLY in `DASHBOARD/webpage/`, never `AGENT-CONTEXT/`.
- **Keep it simple.** Stdlib/native before new deps. No over-engineering. (See `skills/ponytail.md`.)
- **Be honest.** Short, simple, to the point. Use emojis + formatting for clarity.

## 🔄 The Task Loop (full detail in `workflow.md`)
```
UNDERSTAND → VERIFY → IMPLEMENT → VERIFY → MOVE ON
```
1. Understand the request. Read progress + lessons.
2. Verify: research, comprehend, confirm with user for non-trivial changes. Sub-agent review for big tasks.
3. Implement: frontend first, then backend. Modular. Document as you go.
4. Verify: lint/build/agent-browser. Root-cause any bugs.
5. Move on: update `progress.md`, `decisions.md`, `lessons-learned.md`. Notify via ntfy. Summarize to user.

## 📝 After Every Task (Update These)
- `memory/progress.md` — live status.
- `memory/decisions.md` — if a decision was made.
- `memory/lessons-learned.md` — if you made/corrected a mistake.
- `memory/changelog.md` — if a phase advanced.
- Relevant `knowledge/` files — if project knowledge changed.

## 🚨 Session-End Checklist (NON-NEGOTIABLE)
- [ ] All work committed (`git add -A && git commit`).
- [ ] Pushed to GitHub (`git push`). **The environment can clear randomly — unpushed work is lost.**
- [ ] `git status` is clean.
- [ ] ntfy.sh notification sent (topic `TASKISDONE`).
- [ ] Short formatted summary given to the user.

## 📦 Project Folders
```
ANI-KUTA/                        ← repo root (git)
├── ANI-KUTA/                    ← wrapper folder (all zones inside)
│   ├── AGENT-CONTEXT/           # YOUR memory + rules (you maintain this)
│   ├── APP/ani-kuta/            # Android app (46 Gradle modules: 1 app + 26 core + 1 data + 18 feature)
│   ├── DASHBOARD/webpage/       # Next.js dashboard → GitHub Pages (sub-agents build this)
│   └── REFERENCES/              # old-kuta + animiru (read-only)
└── .github/workflows/          # CI
```

## ❓ Currently Blocked On / Open Items
- **Database optimization** (current focus) — dead tables (`extensions.sq`, `metadata.sq`), redundant indexes, missing indexes, FK enforcement off, `INSERT OR REPLACE` fragility, watch-progress bugs. Plan in progress.
- **Ratings UI** — backend (`RatingStore` + schema + Koin) fully built but ZERO UI. Needs feature deps + `RatingSheet` + VM wiring.
- **Continue Watching UI** — SQL query + store method exist but ZERO callers. Dead "Show continue watching" toggle in Library. Needs Browse carousel.
- **Watch-progress bugs** — (1) `setAutoMarkSuppressed` doesn't clear `completed_at` (stale data); (2) `resetAutoMarkSuppressed` never called on FILE_LOADED (CF1 re-arm broken); (3) no resume-seek (users start at position 0); (4) no save on episode switch.
- **Download system device testing** (Phase DL.0-DL.8 implemented; needs on-device verification).
- **Download system future-phase gaps** (D-149, D-151) — proxy-churn re-resolve wiring + 2 re-resolve bugs + outer retry loop + DownloadVideoPickerSheet cleanup. All DEFERRED per user. Full plan in `download-research/FUTURE-PHASE-DL-GAPS.md`.
- **Nav3** — ✅ DECIDED (D-150): keep hand-rolled nav. R7 (process-death backstack recreation) accepted as known limitation. Nav3 1.1.5 dep unused (future cleanup option).
- **Doc-debt sweep** (discrepancy D005) — `knowledge/*` stale (describe 8 proposed modules; actual 46). Deferred.
- See `memory/progress.md` → "What's Next" for the full list.

---
*This file is the quick-start. For everything else, see `navigation.md`.*
