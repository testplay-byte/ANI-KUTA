# NAVIGATION — File Index

> Map of every file in AGENT-CONTEXT. Start here to find what you need.

## 🔝 Top-Level
| File | Purpose |
|------|---------|
| `master.md` | Agent behavior + operating rules. **Read first.** |
| `navigation.md` | This file. Index of everything. |

## 🌐 Workspace-Level
| File | Purpose |
|------|---------|
| `/home/z/my-project/worklog.md` | Append-only sub-agent execution log. Consult for raw detail beyond `memory/progress.md`. |

## 📜 rules/
| File | Purpose |
|------|---------|
| `rules/communication-rules.md` | How to talk to the user (tone, length, format). |
| `rules/coding-rules.md` | Code style, language, structure standards. |
| `rules/build-rules.md` | APK build rules (GitHub Actions, ABI rules). |
| `rules/architecture-rules.md` | Module design, frontend/backend separation. |
| `rules/git-rules.md` | Commit, branch, push conventions. |

## 🧠 memory/
| File | Purpose |
|------|---------|
| `memory/progress.md` | Live status: what's done, what's next. **Update every session.** |
| `memory/decisions.md` | Log of key decisions (with reason + date). |
| `memory/changelog.md` | High-level change history per phase. |

## 📚 knowledge/
| File | Purpose |
|------|---------|
| `knowledge/project-overview.md` | What ANI-KUTA is, goals, scope. |
| `knowledge/tech-stack.md` | Chosen technologies + rationale. |
| `knowledge/module-map.md` | Every module: name, job, dependencies. |
| `knowledge/ui-customization.md` | How UI customization works. |
| `knowledge/old-vs-new.md` | Comparison with the old project (when available). |

## 🛠️ skills/
| File | Purpose |
|------|---------|
| `skills/README.md` | Index of reusable skills/checklists. |
| `skills/planning-checklist.md` | Steps to follow before starting any phase. |
| `skills/subagent-review.md` | How to use sub-agents to find plan flaws. |

## 📋 planning/
| File | Purpose |
|------|---------|
| `planning/README.md` | Planning approach + phase list. |
| `planning/phase-0-setup.md` | Environment + rules setup (current). |
| `planning/phase-1-architecture.md` | App architecture plan (pending). |

## ❓ questions/
| File | Purpose |
|------|---------|
| `questions/open-questions.md` | Unanswered questions needing user input. **Check before every session.** |

---

## Reading Order for a New Agent
1. `master.md`
2. `navigation.md` (this file)
3. `memory/progress.md`
4. `questions/open-questions.md`
5. The specific `rules/` or `planning/` file for the current task.
6. (Optional) `/home/z/my-project/worklog.md` for sub-agent execution detail.
