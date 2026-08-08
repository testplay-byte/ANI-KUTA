# NAVIGATION — File Index

> Map of every file in AGENT-CONTEXT. Start here to find what you need.

## 🔝 Top-Level (read every session)
| File | Purpose |
|------|---------|
| `SESSION.md` | 60-second session bootstrap: key rules + the loop + end-of-session checklist. **Read first.** |
| `master.md` | Project orientation: what ANI-KUTA is, folder layout, what to read. |
| `CORE_RULES.md` | Non-negotiable rules (**29 sections**). Wins over everything. |
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
| `knowledge/project-overview.md` | What ANI-KUTA is, goals, scope. |
| `knowledge/tech-stack.md` | Chosen technologies + versions. |
| `knowledge/architecture.md` | Architecture **design/concept**: layer diagrams, module graph, UI/backend separation. |
| `APP/ani-kuta/DESIGN-LANGUAGE.md` | The app's UI design language (canonical, ~140 lines — colors, typography, floating pill nav, translucent cards, scroll blur). NOTE: an older 1882-line version was deleted + rebuilt fresh; references to `knowledge/app-design-language.md` are stale (that file does not exist). |
| `knowledge/module-map.md` | Every module: name, job, dependencies. |
| `knowledge/ui-customization.md` | How UI customization works. |
| `knowledge/dashboard.md` | Dashboard approach: purpose, content, deployment, update process, sub-agent rules. |
| `knowledge/old-vs-new.md` | Comparison with the old project (when available). |

## 📁 REFERENCES/ (old project)
| Path | Purpose |
|------|---------|
| `REFERENCES/old-kuta/ANIKUTA/` | Old project source (read-only reference). |
| `REFERENCES/old-kuta/DOCUMENTATION/` | Old project analysis docs (`01-09` + README). |

## 📁 APP/ani-kuta/ (new project)
| Path | Purpose |
|------|---------|
| `APP/ani-kuta/DESIGN-LANGUAGE.md` | The app's design language (canonical, ~140 lines — see note above). |
| `APP/ani-kuta/DOCUMENTATION/` | New project architecture/research docs (`10-20` + README + `planning/` subfolders). Includes phase plans (Phase 1, 3, 5, 5c-watch, PHASE-D), research (db/di/nav/ads/backup), + database schema docs. |

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
7. (Optional) `/home/z/my-project/worklog.md` for sub-agent execution detail.
