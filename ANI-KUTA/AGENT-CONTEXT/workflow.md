# WORKFLOW — Task Execution Loop

> The canonical procedure for doing any task. `master.md` and `CORE_RULES.md` point here.
> If a task is trivial (one-line fix, typo), compress the loop mentally — but don't skip verify.

---

## The Loop

```
1. UNDERSTAND  →  2. VERIFY  →  3. IMPLEMENT  →  4. VERIFY  →  5. MOVE ON
```

### 1. UNDERSTAND
- Read the user's request fully. What do they want? How do they want it?
- Read `master.md` + `memory/progress.md` to know current state.
- Read `memory/lessons-learned.md` (grep for tags matching this task type).
- Identify the **goal** in one sentence.
- Identify **assumptions** — flag any that need user confirmation.

### 2. VERIFY (before building)
- **Research**: read the code/docs the task touches. Trace the flow end-to-end.
- **Comprehend**: confirm the whole task is understood. If anything is unclear → ask the user. No assumptions.
- **Confirm**: for non-trivial changes, state your plan to the user in 1–3 lines and get a yes.
- **Sub-agent review**: for big/complex tasks, launch a Plan sub-agent to find flaws in your plan. Verify their findings yourself before acting. Don't loop endlessly — one review pass, fix real flaws, discard false ones, proceed.

### 3. IMPLEMENT
- Build **frontend first** (so the user can see progress), then backend.
- Follow `CORE_RULES.md` §5 (Code Rules) and §7 (Architecture).
- Apply `skills/ponytail.md`: simplest solution that works. Stdlib/native before new deps.
- **Modular complexity**: split big work across multiple files. Document as you go.
- Update `memory/progress.md` as items complete (don't batch at the end).

### 4. VERIFY (after building)
- **Lint / type-check / build** — whatever the project has.
- **Agent-browser** for UI work — confirm it renders and interacts.
- **Cross-check** against the original goal: did you build what was asked?
- **Sub-agent review** the result if it's a big change.
- If broken: fix root cause, not symptom. (See `skills/ponytail.md`.)

### 5. MOVE ON
- **Update docs**: `memory/progress.md`, `memory/decisions.md` (if a decision was made), `memory/changelog.md` (if a phase advanced), relevant `knowledge/` files.
- **Log lessons**: if you made/corrected a mistake → `memory/lessons-learned.md`.
- **Notify**: send `ntfy.sh` notification (topic `TASKISDONE`) — see `CORE_RULES.md` §11.
- **Summarize**: short, formatted summary to the user (see `CORE_RULES.md` §3).
- Only then: move to the next task.

---

## Project Phases

> High-level roadmap. Updated as scope clarifies.

| # | Phase | Status |
|---|-------|--------|
| 0 | Environment & Rules Setup | ✅ done (demo CI green) |
| 1 | App Architecture Planning | ⏳ pending (blocked: need old project ref) |
| 2 | Core Modules (ui, data, network, storage) | ⏳ pending |
| 3 | Feature Modules (one per feature) | ⏳ pending |
| 4 | Customization System (theme engine) | ⏳ pending |
| 5 | Web Dashboard (Next.js → GitHub Pages) | ⏳ pending |
| 6 | Polish, Testing, Release | ⏳ pending |

### Per-Phase Template (when starting a new phase)
For any non-trivial phase, write a short plan note (in chat or `memory/progress.md`):
- **Goal** — one sentence.
- **Steps** — bullet list.
- **Assumptions** — flag any needing user confirmation.
- **Risks** — what could go wrong.
- **Sub-agent review** — run for big phases; record outcome.

---

## When to Ask the User vs. Decide Yourself

| Situation | Action |
|-----------|--------|
| Subjective choice (UI style, naming, scope) | ❓ Ask |
| Multiple valid technical approaches | ❓ Ask with a recommendation |
| Clear best practice, no user preference needed | ✅ Decide, document in `decisions.md` |
| Something is broken and the fix is obvious | ✅ Fix it, mention it |
| You're unsure | ❓ Ask — never guess |
