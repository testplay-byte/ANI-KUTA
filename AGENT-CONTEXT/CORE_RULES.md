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

---

## 17. Naming Consistency

- Keep naming schemes **consistent** across the project so searching is fast and reliable.
- **Files**: `kebab-case` for markdown/data files (`lessons-learned.md`, `open-questions.md`). `PascalCase` for Kotlin/TS classes. `camelCase` for functions/variables.
- **Folders**: `kebab-case` (`old-kuta`, `ani-kuta`). Uppercase for top-level project zones (`APP/`, `DASHBOARD/`, `REFERENCES/`, `AGENT-CONTEXT/`).
- **Modules**: `:lower:case:colon` in Gradle (`:core:ui`). `com.confused.anikuta.<module>` for packages.
- **Decisions**: `D-NNN` (zero-padded, sequential). **Questions**: `Q-NNN`. **Tasks**: `Task NN`.
- **Commits**: Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, `refactor:`).
- If you need to rename something: update **all** references. Grep before and after.
- When creating a new file/folder/module: check existing naming patterns first. Match them.

---

## 18. Take As Much Time As Needed

- **Quality over speed.** Take as much time as a task needs to be done properly.
- Do not rush through steps to "finish faster." A rushed job creates rework.
- If a task is taking longer than expected: that's OK. Communicate progress to the user.
- **Do not skip steps** in the workflow (Understand → Verify → Implement → Verify → Move On) to save time.
- Sub-agent reviews, verification, documentation — all take time. They are not optional.
- "Done fast but wrong" is worse than "done slow but right."
- The only deadline is: **push to GitHub at session end** (§15). Everything else is quality-bound.

---

## 19. Webpage Work Uses Full-Stack-Dev Agent

- Whenever a **change is required on the dashboard webpage** (`DASHBOARD/webpage/`), delegate it to a **`full-stack-developer` sub-agent**.
- The full-stack-dev agent handles: building pages, adding components, updating styling, wiring data, fixing build issues — all inside `DASHBOARD/webpage/`.
- The main agent defines the task, gives the sub-agent the DESIGN.md context, verifies the result, then updates `AGENT-CONTEXT/` (the sub-agent never touches AGENT-CONTEXT).
- This produced excellent results for the initial dashboard build; it is now the standard for all webpage work.

---

## 20. Filtered Console Logging

- **Proper console logging for everything.** Every significant action, state change, error, and network call must be logged with enough context to understand what happened and where.
- **Filtered**: Use log levels (VERBOSE / DEBUG / INFO / WARN / ERROR). Logcat tags per module (`Anikuta:Core:Database`, `Anikuta:Feature:Watch`, etc.). The user/developer can filter by tag + level.
- **Toggleable**: Logging can be turned OFF for performance (release builds). Controlled by a build config flag (`BuildConfig.DEBUG` default) + a runtime toggle in Settings for beta/debug builds.
- **What to log**:
  - ✅ INFO: screen navigation, user actions (tap, search), feature start/end.
  - ✅ DEBUG: repository queries, cache hits/misses, state transitions, DI module init.
  - ✅ WARN: recoverable errors (retry, fallback), deprecated API usage.
  - ✅ ERROR: exceptions, failed network calls, DB errors, with stack traces.
  - ✅ VERBOSE: detailed flow tracing (only when debugging a specific issue).
- **What NOT to log**: user credentials, tokens, personal data, full request/response bodies (log URLs + status codes only).
- **Implementation**: Use a central `Logger` wrapper (in `:core:common`) that respects the level + tag + toggle. Never call `Log.d()` directly — always go through `Logger`.
- **Performance**: When logging is OFF, the Logger is a no-op (zero overhead). Use `if (Logger.isEnabled)` guards around expensive log message construction.

---

## 21. Documentation Folder Organization (STRICT)

> Where documentation lives. Read this before writing ANY doc. Getting this wrong mixes old-project analysis with new-project plans — a real source of confusion.

### Three documentation zones — NEVER mix them:

| Zone | Path | What goes here |
|------|------|----------------|
| **Old project analysis** | `REFERENCES/old-kuta/DOCUMENTATION/` | Analysis of the OLD ANIKUTA app. Read-only reference. Docs `01-09` (overview, architecture, tech-stack, modules, data-flow, features, rebuild-notes). NOTHING about the NEW project goes here. |
| **New project docs** | `APP/ani-kuta/DOCUMENTATION/` | Architecture plans, research, design decisions for the NEW app. Docs like `10-db-research`, `11-di-research`, `12-nav-research`, `13-ads-research`, `14-architecture-recommendations`, `15-backup-research`, `16-phase1-architecture-plan`, `DESIGN-LANGUAGE.md`. |
| **Agent knowledge** | `AGENT-CONTEXT/knowledge/` | Quick-reference summaries the agent reads on demand. NOT detailed research — that goes in `APP/ani-kuta/DOCUMENTATION/`. The knowledge files link to the detailed docs. |

### Rules
1. **Old project analysis stays in `REFERENCES/old-kuta/DOCUMENTATION/`.** It describes the existing app. Never put new-project plans here.
2. **New project architecture/research/design goes in `APP/ani-kuta/DOCUMENTATION/`.** This is the new app's technical documentation.
3. **Agent-facing summaries go in `AGENT-CONTEXT/knowledge/`.** Short, cross-reference the detailed docs.
4. **The app's design language** lives at `APP/ani-kuta/DESIGN-LANGUAGE.md` (one file, canonical).
5. **The dashboard's design language** lives at `DASHBOARD/webpage/DESIGN.md` (separate — the dashboard is a different product).
6. **Before writing a doc**: ask "is this about the OLD app, the NEW app, or agent memory?" → put it in the right zone.
7. **When in doubt**: ask the user. Don't guess the location.

### Verification
- After writing a doc, verify its location matches the table above.
- If you find a doc in the wrong zone: move it + update all cross-references (grep for the old path).

---

## 22. UI / UX Quality — Buttery Smooth Animations

> The user is a great fan of rich, buttery-smooth animations and beautiful, minimalistic UI. This is a quality bar, not an afterthought.

### Animation Requirements
- **Scrolling**: smooth scroll effects (parallax, fade-in on scroll, collapsible headers that animate). Never janky.
- **Screen transitions**: animated screen switches (fade, slide, shared element transitions where appropriate). Never instant cuts.
- **Button clicks**: MUST give user feedback — ripple, scale-down on press, color change, haptic. Never a dead tap.
- **Loading states**: smooth skeletons / shimmer, not jarring spinners where possible.
- **State changes**: animate UI state changes (expand/collapse, appear/disappear) — never pop in/out.

### Design Aesthetic
- **Simple, minimalistic, good-looking.** Not cluttered. Every element earns its place.
- **Follow `APP/ani-kuta/DESIGN-LANGUAGE.md` strictly.** It captures the old project's proven aesthetic (lime accent, warm darks, translucent cards, floating pill nav, scroll blur).
- **The design language is a living document.** When the user mentions UI improvements, update `DESIGN-LANGUAGE.md` AND propagate to the code.

### Performance
- **60fps target.** Animations must not drop frames. Use Compose's animation APIs correctly (`AnimatedVisibility`, `animateContentSize`, `SharedTransitionLayout`).
- **No heavy work on the main thread** during animation. Offload to IO/background.
- **Test on low-end devices** (not just emulators).

---

## 23. Live Data Verification

> When the user makes a change, it must be verified AND reflected live on screen. No "change + manual refresh."

### Rules
1. **Every user action has immediate visual feedback.** If the user taps "Add to Library," the UI updates instantly (optimistic update), then confirms with the backend.
2. **Data changes propagate live.** Use `Flow`/`StateFlow` throughout. The UI observes state and recomposes automatically. Never poll.
3. **Verify changes persisted.** After a write, verify it landed (read-back or DB callback). If it failed, roll back the optimistic update + show an error.
4. **No silent failures.** If a save fails, the user MUST know. Toast/snackbar with the error + retry option.
5. **Cross-screen consistency.** If the user adds an anime to their library on the Details screen, the Library screen must reflect it without a manual refresh (shared state via Flow).

### Implementation
- Repositories return `Flow<T>` for reads (reactive).
- Writes return `Result<T>` (success/failure) — handle both in the ViewModel.
- ViewModels expose `StateFlow<UiState>` — UI collects and renders.
- Optimistic updates: update the UI state immediately, then confirm with the backend. Roll back on failure.

---

## 24. Database Documentation — Always Up to Date

> The database is a crucial part of the app. Its structure must be documented and kept in sync with the code at all times.

### Rules
1. **Dedicated documentation**: All database schema documentation lives in `APP/ani-kuta/DOCUMENTATION/database/`. One file per table group, plus a README index.
2. **Update on every change**: Whenever a table is added, modified, or removed (including columns, indexes, constraints), the corresponding documentation file MUST be updated in the SAME commit. No "document it later."
3. **Document what + why**: Each table documents its columns (name, type, constraints, description) AND why it exists (what problem it solves, what queries it supports).
4. **Migration log**: Every schema migration (`.sqm` file) must have a corresponding entry in `APP/ani-kuta/DOCUMENTATION/database/changelog.md` — what changed, why, when.
5. **ER diagram**: Keep the entity relationship diagram in `APP/ani-kuta/DOCUMENTATION/database/er-diagram.md` updated when relationships change.
6. **Verify before commit**: Before committing a DB change, verify the docs match the `.sq` files. If they don't match, the commit is incomplete.

### File Structure
```
APP/ani-kuta/DOCUMENTATION/database/
├── README.md              — index of all tables + groups
├── er-diagram.md          — entity relationship diagram
├── changelog.md           — migration history (version, date, what changed)
├── identity.md            — identity group tables (Phase 4+)
├── library.md             — library group tables (Phase 4+)
├── watch.md               — watch progress + history tables
├── downloads.md           — download queue + downloaded files tables
├── extensions.md          — installed sources + extension repos tables
├── metadata.md            — content + episode metadata cache tables
├── tracking.md            — activity event table (internal tracking)
├── customization.md       — user customization table
└── app.md                 — app_metadata table (existing)
```

---

## 25. Dashboard Must Be Kept Up to Date

> The web dashboard is the visual representation of the project. It MUST reflect the current state at all times.

### Rules
1. **After every phase or significant change**: update the dashboard data (`lib/data.ts`, `lib/decisions.ts`, `lib/schema.ts`, `lib/phase3.ts`) to reflect the new state.
2. **Module count, phase status, decisions, database schema** — all must match the actual project state.
3. **When new modules are added**: update the module count, architecture page, and progress page.
4. **When decisions are confirmed**: update the decisions page.
5. **When DB schema changes**: update the database page.
6. **When a phase completes**: update the progress page + overview.
7. **Design language updates**: if the user requests UI changes to the dashboard, update `DASHBOARD/webpage/DESIGN.md` in the same commit.
8. **Verification**: after updating, verify the build passes (`bun run build`) and the dashboard is live on GitHub Pages.
9. **Don't let it drift**: the dashboard is the user's primary way to understand the project. If it's stale, the user loses trust.
