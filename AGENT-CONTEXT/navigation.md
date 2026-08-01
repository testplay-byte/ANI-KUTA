# NAVIGATION — File Index

> Map of every file in AGENT-CONTEXT. Start here to find what you need.

## 🔝 Top-Level (read every session)
| File | Purpose |
|------|---------|
| `master.md` | Entry point. Project summary + what to read. **Read first.** |
| `CORE_RULES.md` | Non-negotiable rules. Wins over everything. |
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
| `knowledge/module-map.md` | Every module: name, job, dependencies. |
| `knowledge/ui-customization.md` | How UI customization works. |
| `knowledge/old-vs-new.md` | Comparison with the old project (when available). |

## 🛠️ skills/
| File | Purpose |
|------|---------|
| `skills/README.md` | Skills index + rules for creating new skills. |
| `skills/ponytail.md` | Lazy senior dev: simplest solution that works. YAGNI, stdlib-first, root-cause. |
| `skills/subagent-review.md` | How to use sub-agents to find plan flaws. |

---

## Reading Order for a New Agent
1. `master.md`
2. `CORE_RULES.md`
3. `memory/progress.md`
4. `workflow.md` (when starting a task)
5. The specific `knowledge/` or `skills/` file relevant to the task.
6. (Optional) `/home/z/my-project/worklog.md` for sub-agent execution detail.
