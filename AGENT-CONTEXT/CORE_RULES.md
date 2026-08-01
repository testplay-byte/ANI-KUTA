# CORE RULES — Non-Negotiable

> These rules apply at ALL times during development. They supersede the former `rules/` folder.
> If a rule here conflicts with anything else, **this file wins.**

---

## 1. Development Flow

Every task follows this cognitive sequence — in order:

1. **Analyze** — Understand the user's request, intentions, and context. What do they want? How do they want it done? No blind guesses.
2. **Research** — Investigate the relevant topic/code before acting. Understand what already exists, what touches what. Look before you write.
3. **Comprehend** — Confirm the whole task is understood. If anything is unclear, ask directly — no hesitation.
4. **Confirm** — For non-trivial changes, confirm your understanding with the user before building. State what you'll do in one line.
5. **No Assumptions** — Never guess. If unclear: ask the user or verify in the codebase. Assumptions are bugs you ship early.
6. **Modular Complexity** — Long/complex task? Split it across multiple files and multiple workflow steps. Keep each piece manageable, documented, and independently understandable.

> The concrete step-by-step task loop lives in `workflow.md`. This section is the **mindset**; `workflow.md` is the **procedure**.

---

## 2. Communication & Honesty

- **Ask as many questions as needed.** Clarify anything unclear directly with the user.
- **Proactively highlight** concerns, limitations, and future risks — before the user discovers them.
- **Guide the user** through problems and constraints plainly.
- **Never sugarcoat.** If a request has an issue, say so directly. Do not blindly agree. Do not follow requests that you can see are flawed without flagging the flaw first.
- **Be honest at all times.** A correct uncomfortable truth beats a polite wrong answer.
- Keep wording **short, simple, to the point**. Tell as much as needed — no more.

---

## 3. Summary After Completion

After completing a task, give the user a **short summary**:

- **Do not exaggerate.** Do not leave out key details.
- Use **proper formatting**: headings, highlights, emojis for emphasis and spacers.
- Use **multiple empty lines** for spacing where one line isn't enough.
- Lead with the **key outcome**. Then what changed. Then what's next.
- Reference file paths, not file contents (the user opens files if they want detail).

---

## 4. Project Structure

- Keep the project **easy to handle and manage**. Well-documented, well-understood.
- **AGENT-CONTEXT stays updated after every task** so any future AI agent can pick up immediately.
- Build so that **editing one part** only requires understanding that part + its immediate context — not the whole project.
- **All things link together.** Document the relations (comments in code, notes in knowledge files).

### Folder Layout (canonical)
```
ANIKUTA-PROJECT/
├── AGENT-CONTEXT/   # agent memory + rules (versioned in repo)
├── APP/ani-kuta/    # Android app (Gradle + Kotlin + Compose)
├── DASHBOARD/webpage/  # Next.js dashboard (→ GitHub Pages)
└── .github/workflows/  # CI
```

---

## 5. Code Rules

- **Split code into multiple files** for development, maintenance, and reuse. Fewest files that make sense — not one giant file, not a file per function.
- **Document with comments**: what lives where, what the relations are. Comments explain *why*, not *what*.
- **One module = one responsibility.**
- Reuse before you write. Look a few files over before implementing.
- No unrequested abstractions (no interface with one impl, no factory for one product).
- Mark deliberate simplifications with a `ponytail:` comment naming the ceiling + upgrade path.

> See `skills/ponytail.md` for the full lazy-senior-dev decision ladder.

---

## 6. Documentation Rules

- **Verify before writing.** Confirm the change is real, understood, and actually needed before documenting it.
- **If the project changes, the docs reflect it** — same session, not "later".
- Do not over-document file structure. Document what's non-obvious.
- No generic advice. Specific, actionable rules only.

---

## 7. Architecture

- **Highly modular.** Multiple things → multiple modules.
- **UI and backend logic are separate per screen.** A screen's UI and its data/logic live in different files/modules. The UI either calls for data or receives it pre-loaded.
- **Frontend (UI)** renders data, handles input only. No data-fetching logic baked in.
- **Backend (data)** fetches, processes, stores. Exposes clean interfaces to the UI.
- They communicate via **defined contracts** (interfaces/repositories), so UI can be customized without touching data logic.

> Concept diagrams + module graph live in `knowledge/architecture.md`. This section is the **rule**; that file is the **design**.

---

## 8. GitHub Actions & Branching

- **Always use GitHub Actions** for builds (APK + dashboard). Never build locally.
- **Create a branch** for each feature/fix: `feature/<name>`, `fix/<name>`, `docs/<name>`.
- **Merge to `main` only after** the feature is verified working and satisfactory. Not before.
- Commit messages: Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, `refactor:`).
- Never force-push to `main`.

### Build Rules (APK)
- **NEVER** build the APK locally. GitHub Actions only.
- **ONLY** `arm64-v8a` + `armeabi-v7a` ABIs. No x86/x86_64.
  - Set in `APP/ani-kuta/app/build.gradle.kts` (`ndk.abiFilters`).
  - Verified post-build in CI (fails on any forbidden `lib/<abi>/`).
- App ID: `com.confused.anikuta`.

---

## 9. Self-Learning

- **When the user corrects you, or you catch your own mistake** → log it immediately in `memory/lessons-learned.md`.
- Format: `- [TAG] lesson (source: <task-id or "self">, <date>)`
- Tags: `MISTAKE` (you did wrong), `CORRECTION` (user fixed you), `INSIGHT` (you realized), `PATTERN` (recurring).
- **Dedup**: grep existing entries for the keyword before adding. Don't log the same lesson twice.
- **Review**: at task start, grep `lessons-learned.md` for tags matching the current task type.
- **Stale**: mark `~~strikethrough~~` with `→ superseded by <ref>` when a newer lesson contradicts.
- If a lesson is a recurring pattern → also add a **one-line rule** to the relevant section of this file.

---

## 10. Patterns to Avoid

- ❌ **Dependencies between skills.** Each skill in `skills/` is standalone. One skill must not require another to run.
- ❌ **Complex build systems or test frameworks.** Maintain simplicity. One runnable self-check for non-trivial logic is enough. No frameworks unless explicitly requested.
- ❌ **Generic advice.** Every rule must be specific and actionable. "Write clean code" = useless. "Function ≤ 30 lines or split" = useful.
- ❌ **Over-documenting file structure.** Document what's non-obvious. Don't narrate every folder.
- ❌ **Boilerplate "for later".** Later can scaffold for itself.
- ❌ **Deletion disguised as addition.** Don't add prose that defends a simplification — delete the prose.

---

## 11. Task Notification

- **After completing every task**, send a notification via `ntfy.sh`:
  ```bash
  curl -fsSL -H "Title: ANI-KUTA Agent" -d "<short result, one line>" https://ntfy.sh/TASKISDONE
  ```
- Topic: `TASKISDONE` (user-specified).
- ⚠️ **Note**: ntfy.sh topics are public. Anyone who guesses `TASKISDONE` can read/spoof messages. Don't put secrets in the message body. If this becomes a problem, switch to a long random topic stored in a GitHub secret.

---

## 12. Skill Management

- Skills live in `skills/`. Each is a standalone markdown file.
- **To add a skill**: (1) understand it fully, (2) verify it's reliable + useful, (3) sub-agent review if non-trivial, (4) write it with concrete examples (no generic philosophy), (5) add to `skills/README.md` index.
- **To create a new skill yourself**: must have a solid reason + solid backing. Use sub-agents to verify. If unsure, don't add it.
- Skills are **reference material**, not dependencies. The agent reads them on demand.

---

## 13. User Uses Speech-to-Text

- The user often dictates messages via speech-to-text. Transcription errors happen (misheard words, dropped words, odd phrasing).
- **If a request feels off or ambiguous**: try to correct obvious transcription errors from context. If still unclear → **stop and highlight it with the user** before proceeding. Do not move in the wrong direction on a misheard instruction.
- Common tells: homophones ("their/there"), numbers spelled out, slightly wrong technical terms. Use project context to disambiguate.
- When in doubt: ask. A 10-second clarification beats an hour of wrong work.

---

## 14. Sub-Agent Delegation Scope

- The main agent delegates webpage work to **sub-agents** (analysis, documentation, page creation).
- **Sub-agents working on the webpage work ONLY inside `DASHBOARD/webpage/`.** They must NOT touch `AGENT-CONTEXT/` — no random documentation, no rule edits, no memory updates.
- Sub-agents do: webpage creation, webpage analysis, webpage documentation (inside `DASHBOARD/webpage/` only).
- The **main agent** is responsible for all `AGENT-CONTEXT/` updates (progress, decisions, lessons, rules) after sub-agent work completes.
- When launching a webpage sub-agent: tell it explicitly "work only in `DASHBOARD/webpage/`, do not modify `AGENT-CONTEXT/`."

---

## 15. Session-End Backup (Push to GitHub)

- ⚠️ **This environment can clear out randomly.** Work not pushed to GitHub can be lost.
- **Every session MUST end with all changes committed and pushed to GitHub.** No exceptions.
- Before declaring a session done: `git status` must be clean, `git push` must be done.
- If the environment was cleared and re-cloned at session start: read `memory/progress.md` first to know where things stand, then continue.
- This rule exists because the environment is ephemeral; GitHub is the source of truth.

---

## 16. Web Dashboard Design Language

- The dashboard's design language is defined in **`DASHBOARD/webpage/DESIGN.md`**.
- It is **strictly followed** on all pages, all components, all parts of the dashboard. No deviations.
- Includes a **dark mode toggle** at the top of every page.
- To modify the design language: edit `DESIGN.md`, get user confirmation for non-trivial changes, keep it flexible for future improvement.
- See `knowledge/dashboard.md` for the full dashboard approach (purpose, content, deployment, update process).
