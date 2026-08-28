# SESSION — Read This At The Start Of Every Session

> A 60-second orientation. Read this FIRST, every time, before any work.
> It reminds you of the key rules and the session loop. For detail, follow the links.

---

## ⚡ Who You Are
You are the AI agent for **ANI-KUTA** (Android app rebuild + companion web dashboard).
GitHub: `testplay-byte/ANI-KUTA`. Repo root contains a single wrapper folder `ANI-KUTA/` (per CORE_RULES §4). Active branch: `test-feature/video-cache-new-download` (long-lived; all shipped versions since v0.2.48 built here; NOT merged into `main` — user gate per CORE_RULES §8).

## 📂 If The Environment Was Just Cloned
1. Clone to `/home/z/ANI-KUTA-WORK/ANI-KUTA` and `git checkout test-feature/video-cache-new-download` (the repo is public — read access works without a token; PUSH needs the GitHub token in the remote URL, ask the user if it was lost in a sandbox wipe).
2. Read `AGENT-CONTEXT/memory/progress.md` → know what's done, what's next, blockers + **Deferred Concerns** (read the top sections first).
3. Read `AGENT-CONTEXT/memory/decisions.md` → latest = D-326 (v0.2.62 batch; all pending items answered).
4. Read `AGENT-CONTEXT/memory/lessons-learned.md` → grep for tags matching your task.

## 🔑 Key Rules (full detail in `CORE_RULES.md` — 30 sections)
- **No assumptions.** Unsure → ask the user. Never guess.
- **Don't sugarcoat.** If a request has an issue, flag it directly. Don't blindly agree.
- **User uses speech-to-text.** If a request feels off, correct obvious errors from context; if still unclear → stop and ask.
- **APK builds: GitHub Actions only.** ABIs: `arm64-v8a` ONLY in shipped APKs (D-251; test-only x86_64 emulator builds via `-PemulatorX64Build=true`, never shipped). Never local. Never install Android SDK/JDK locally (CORE_RULES §8).
- **Debug builds = schema freedom** (CORE_RULES §30). No migration scripts needed. Old DBs get deleted + recreated. Don't worry about preserving existing dev data.
- **Sub-agents for webpage work** → they work ONLY in `DASHBOARD/webpage/`, never `AGENT-CONTEXT/` (CORE_RULES §14, §19).
- **Keep it simple.** Stdlib/native before new deps. No over-engineering. (See `skills/ponytail.md`.)
- **Be honest.** Short, simple, to the point. Use emojis + formatting for clarity.
- **Quality over speed.** Take as much time as needed. Don't rush. Don't skip steps (CORE_RULES §18).

## 🔄 The Task Loop (full detail in `workflow.md`)
```
REFLECT → RESEARCH → PLAN → TODO LIST → EXECUTE → COMMIT → VERIFY (CI) → DOC UPDATE → NOTIFY
```
1. **Reflect** — summarize what you understood before executing.
2. **Research** — read the code/docs the task touches. Use Explore sub-agents.
3. **Plan** — split into phases. Identify dependencies.
4. **Todo list** — build a comprehensive todo list AFTER research (not random at start).
5. **Execute** — build one phase at a time. Frontend first, then backend.
6. **Commit** — each phase as a separate commit.
7. **Verify (CI)** — push, poll GitHub Actions API, read build results/failures, fix, repeat until green. **Do NOT dispatch sub-agents to pre-review for compile errors (D-281) — CI is the compiler of record.**
8. **Doc update** — progress.md, decisions.md, changelog.md, lessons-learned.md in the SAME session.
9. **Notify** — ntfy.sh after each phase + at the end.

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

## 🧪 Testing on the Emulator
The sandbox can run the app on an Android emulator (user-authorized, CORE_RULES §8
exceptions) — install/launch/AniList/extensions/trust/search all verified E2E.
**Before ANY emulator/adb work, read `knowledge/emulator-testing.md`** — it has the
sandbox rules that will otherwise cost hours (double-fork detach, timeout-wrapped
adb, input-text limits, the 4GB memory ceiling) + full setup + workflow + tricks.

## 📦 Project Folders
```
ANI-KUTA/                        ← repo root (git)
├── ANI-KUTA/                    ← wrapper folder (all zones inside)
│   ├── AGENT-CONTEXT/           # YOUR memory + rules (you maintain this)
│   ├── APP/ani-kuta/            # Android app (50 Gradle modules: 1 app + 30 core + 1 data + 18 feature)
│   ├── DASHBOARD/webpage/       # Next.js dashboard (14 pages → GitHub Pages; sub-agents build this)
│   └── REFERENCES/              # old-kuta + animiru (read-only)
└── .github/workflows/          # CI
```

## ❓ Currently Blocked On / Open Items
- **v0.2.62 push (2026-08-29):** the sandbox was wiped (3rd time) and the GitHub token — which lived only in the old clone's remote URL — was lost. The complete v0.2.62 work (D-324 smoother shared-element morph + D-325 multi-season-only episode tags) is committed locally on `test-feature/video-cache-new-download`; push + CI + tag + release resume the moment the token is re-provided.
- **Ongoing device-feedback loop:** the user tests every release on a real OnePlus device and reports back; each session = fix/polish batch + version bump + release. v0.2.61 (compose 1.10.4 alignment — D-322) verified working on device.
- **Branch merge into `main`:** user-gated (CORE_RULES §8); the long-lived branch is 100+ commits ahead.
- See `memory/progress.md` → "Deferred Concerns" + "What's Next" for the full list (older items like library-badge testing, download-system device testing, Nav3, doc-debt are all resolved/historical — see decisions.md).

---
*This file is the quick-start. For everything else, see `navigation.md`.*
