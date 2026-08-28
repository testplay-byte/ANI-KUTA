# NAVIGATION — File Index

> Map of every file in AGENT-CONTEXT. Start here to find what you need.

## 🔝 Top-Level (read every session)
| File | Purpose |
|------|---------|
| `SESSION.md` | 60-second session bootstrap: key rules + the loop + end-of-session checklist. **Read first.** |
| `master.md` | Project orientation: what ANI-KUTA is, folder layout, what to read. |
| `CORE_RULES.md` | Non-negotiable rules (**30 sections**). Wins over everything. |
| `workflow.md` | The task execution loop (Understand→Verify→Implement→Verify→Move On) + project phases. |

## 🌐 Workspace-Level
| File | Purpose |
|------|---------|
| `/home/z/my-project/worklog.md` | Append-only sub-agent execution log. Read for raw detail beyond `memory/progress.md`. |

## 🧠 memory/
| File | Purpose |
|------|---------|
| `memory/progress.md` | Live status: what's done, what's next, blockers. **Update every session.** |
| `memory/decisions.md` | Decision log (confirmed + pending). |
| `memory/lessons-learned.md` | Self-learning: one-line lessons from mistakes/corrections. |
| `memory/changelog.md` | Immutable high-level history per phase. |

## 📚 knowledge/
| File | Purpose |
|------|---------|
| `knowledge/project-overview.md` | What ANI-KUTA is, goals, scope, current status. |
| `knowledge/tech-stack.md` | Actual technologies + versions (verified against `libs.versions.toml`). |
| `knowledge/architecture.md` | Architecture **design/concept**: layer diagrams, actual 50-module graph, nav, DB, DI wiring, known debt. |
| `APP/ani-kuta/DESIGN-LANGUAGE.md` | The app's UI design language (canonical ~140 lines — lime accent, warm darks, translucent cards, floating pill nav, scroll blur). |
| `knowledge/module-map.md` | All 50 modules: name, job, dependencies, key files. |
| `knowledge/ui-customization.md` | How UI customization works (theme tokens, component variants, layout, behavior toggles, subtitle settings). |
| `knowledge/dashboard.md` | Dashboard approach: 14 pages, data files, deployment, update process, sub-agent rules. |
| `knowledge/old-vs-new.md` | Old project (REFERENCES/old-kuta/) vs new project comparison + migration notes. |
| `knowledge/emulator-testing.md` | **The sandbox Android emulator environment**: setup from scratch, sandbox rules (double-fork detach, timeout-wrapped adb, input-text limits, 4GB cgroup), daily workflow commands, app testing tricks (prefs injection, extension repo injection), smoke-test checklist, troubleshooting table. Read BEFORE any emulator work. |

## 📁 REFERENCES/ (read-only references)
| Path | Purpose |
|------|---------|
| `REFERENCES/old-kuta/ANIKUTA/` | Old project source (~643 files, 36 modules, package `app.confused.anikuta`). |
| `REFERENCES/old-kuta/DOCUMENTATION/` | Old project analysis docs (`01-09` + README — 5326 lines). |
| `REFERENCES/animiru/` | Animiru reference repo (~1553 files — anime-only Aniyomi fork, player + ext patterns). |
| `REFERENCES/animiru/documentation/` | Animiru documentation (8,101 lines — read-only reference, D-065). |
| `REFERENCES/webview-cloudflare-captcha/` | Small reference (README only). |

## 📁 AGENT-CONTEXT/download-research/ (download system design)
| Path | Purpose |
|------|---------|
| `download-research/00-16` | 17 download-system research docs (workflow, queue, state machine, storage, downloaders, notifications, settings, UI, player, DB, DI, implementation plan, auto-download, UI/bug analysis, QoL). |
| `download-research/REVIEW-1..5` | 5 review rounds with 72 MUST-FIX items. |
| `download-research/REVIEW-D0.md` | Foundations review. |
| `download-research/FUTURE-PHASE-DL-GAPS.md` | Consolidated deferred download gaps (D-149, D-151) + RetryPolicy sketch. |

## 📁 APP/ani-kuta/ (new project)
| Path | Purpose |
|------|---------|
| `APP/ani-kuta/DESIGN-LANGUAGE.md` | The app's design language (canonical ~140 lines — see above). |
| `APP/ani-kuta/DOCUMENTATION/` | New project architecture/research docs (`10-20` + README + `planning/` subfolders + `download-device-testing-checklist.md`). Historical: research (10-15), Phase 1 plan (16), DB schema (17 — note: says "21 tables", actual 28), Phase 3/5 plans (18-20). Planning: data-management/PHASE-D-PLAN, debug-bubble/PLAN, extension-details-page/{ARCHITECTURE-PLAN,FLOW-DIAGRAM,PHASE-C-PLAN}, watch-history-updates/PLAN. |

## 🛠️ skills/
| File | Purpose |
|------|---------|
| `skills/README.md` | Skills index + rules for creating new skills. |
| `skills/ponytail.md` | Lazy senior dev: simplest solution that works. YAGNI, stdlib-first, root-cause. |
| `skills/subagent-review.md` | How to use sub-agents to find plan flaws. |

---

## Reading Order for a New Agent
1. `SESSION.md` (quick-start)
2. `master.md` (project orientation)
3. `CORE_RULES.md` (the rules)
4. `memory/progress.md` (live status)
5. `workflow.md` (when starting a task)
6. The specific `knowledge/` or `skills/` file relevant to the task.
7. `knowledge/emulator-testing.md` (when testing on the sandbox emulator — BEFORE touching adb).
8. (Optional) `/home/z/my-project/worklog.md` for sub-agent execution detail.
