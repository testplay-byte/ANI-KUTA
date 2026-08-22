# SESSION — Read This At The Start Of Every Session

> A 60-second orientation. Read this FIRST, every time, before any work.
> It reminds you of the key rules and the session loop. For detail, follow the links.

---

## ⚡ Who You Are
You are the AI agent for **ANI-KUTA** (Android app rebuild + companion web dashboard).
GitHub: `testplay-byte/ANI-KUTA`. Repo root contains a single wrapper folder `ANI-KUTA/` (per CORE_RULES §4). Active branch: `main` (all feature branches merged + deleted).

## 📂 If The Environment Was Just Cloned
1. `cd /home/z/my-project/ANI-KUTA` (if missing → re-clone from GitHub; the wrapper folder is `ANI-KUTA/ANI-KUTA/` inside).
2. Read `AGENT-CONTEXT/memory/progress.md` → know what's done, what's next, blockers + **Deferred Concerns** (read the top sections first).
3. Read `AGENT-CONTEXT/memory/decisions.md` → "Pending Decisions" section (latest = D-193; all pending items answered).
4. Read `AGENT-CONTEXT/memory/lessons-learned.md` → grep for tags matching your task.

## 🔑 Key Rules (full detail in `CORE_RULES.md` — 30 sections)
- **No assumptions.** Unsure → ask the user. Never guess.
- **Don't sugarcoat.** If a request has an issue, flag it directly. Don't blindly agree.
- **User uses speech-to-text.** If a request feels off, correct obvious errors from context; if still unclear → stop and ask.
- **APK builds: GitHub Actions only.** ABIs: `arm64-v8a` + `armeabi-v7a` only. Never local. Never install Android SDK/JDK locally (CORE_RULES §8).
- **Debug builds = schema freedom** (CORE_RULES §30). No migration scripts needed. Old DBs get deleted + recreated. Don't worry about preserving existing dev data.
- **Sub-agents for webpage work** → they work ONLY in `DASHBOARD/webpage/`, never `AGENT-CONTEXT/` (CORE_RULES §14, §19).
- **Keep it simple.** Stdlib/native before new deps. No over-engineering. (See `skills/ponytail.md`.)
- **Be honest.** Short, simple, to the point. Use emojis + formatting for clarity.
- **Quality over speed.** Take as much time as needed. Don't rush. Don't skip steps (CORE_RULES §18).

## 🔄 The Task Loop (full detail in `workflow.md`)
```
REFLECT → RESEARCH → PLAN → TODO LIST → EXECUTE → COMMIT → VERIFY (REVIEW) → VERIFY (CI) → DOC UPDATE → NOTIFY
```
1. **Reflect** — summarize what you understood before executing.
2. **Research** — read the code/docs the task touches. Use Explore sub-agents.
3. **Plan** — split into phases. Identify dependencies.
4. **Todo list** — build a comprehensive todo list AFTER research (not random at start).
5. **Execute** — build one phase at a time. Frontend first, then backend.
6. **Commit** — each phase as a separate commit.
7. **Verify (review)** — sub-agent review for compile errors before push.
8. **Verify (CI)** — push, poll GitHub Actions API, read failures, fix, repeat until green.
9. **Doc update** — progress.md, decisions.md, changelog.md, lessons-learned.md in the SAME session.
10. **Notify** — ntfy.sh after each phase + at the end.

## 📝 After Every Task (Update These)
- `memory/progress.md` — live status.
- `memory/decisions.md` — if a decision was made.
- `memory/lessons-learned.md` — if you made/corrected a mistake.
- `memory/changelog.md` — if a phase advanced.
- Relevant `knowledge/` files — if project knowledge changed.
- Dashboard data (`DASHBOARD/webpage/lib/`) — delegate to full-stack-dev sub-agent (§19).

## 🚨 Session-End Checklist (NON-NEGOTIABLE)
- [ ] All work committed (`git add -A && git commit`).
- [ ] Pushed to GitHub (`git push`). **The environment can clear randomly — unpushed work is lost.**
- [ ] `git status` is clean.
- [ ] CI is green (verified via API, not assumed — lesson D-156).
- [ ] ntfy.sh notification sent (topic `TASKISDONE`).
- [ ] Short formatted summary given to the user.

## 📦 Project Folders
```
ANI-KUTA/                        ← repo root (git)
├── ANI-KUTA/                    ← wrapper folder (all zones inside)
│   ├── AGENT-CONTEXT/           # YOUR memory + rules (you maintain this)
│   ├── APP/ani-kuta/            # Android app (46 Gradle modules: 1 app + 26 core + 1 data + 18 feature)
│   ├── DASHBOARD/webpage/       # Next.js dashboard (14 pages → GitHub Pages; sub-agents build this)
│   └── REFERENCES/              # old-kuta + animiru (read-only)
└── .github/workflows/          # CI
```

## ❓ Currently Blocked On / Open Items
- **Library badge customization system** (D-242, fix9–fix14) — ✅ IMPLEMENTED + CI GREEN on `functionality/improvements` (commit `b4c75ba3`, version 0.2.38). APK artifact built (55.3 MB). **Ready for device testing.** Advanced RELEASED options (sub/dub/both + unwatched + SVG icons) + scroll-to-minimize header.
- **Database management + quality** (next focus after library badge testing) — user will provide a fresh DB export after a clean-install test run. Agent will analyze for flaws.
- **Download system device testing** (Phase DL.0-DL.8 implemented; needs on-device verification).
- **Download system future-phase gaps** (D-149, D-151) — proxy-churn re-resolve wiring + 2 re-resolve bugs + outer retry loop + DownloadVideoPickerSheet cleanup. All DEFERRED per user. Full plan in `download-research/FUTURE-PHASE-DL-GAPS.md`.
- **Nav3** — ✅ DECIDED (D-150): keep hand-rolled nav; Nav3 fully REMOVED from all build files. R7 (process-death backstack recreation) accepted as known limitation.
- **Doc-debt sweep** — ✅ DONE (all knowledge/*, master.md, SESSION.md, navigation.md, dashboard data updated; code comments cleaned).
- See `memory/progress.md` → "Deferred Concerns" + "What's Next" for the full list.

---
*This file is the quick-start. For everything else, see `navigation.md`.*
