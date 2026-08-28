# WORKFLOW — The ANI-KUTA Agent Execution Process

> This file documents the **proven workflow** the agent follows for every task.
> It is based on the successful 4-phase session (DB optimization + ratings + continue-watching + watch-progress fixes).
> **Read this before starting any work. Follow it every time.**

---

## The Workflow (10 Steps)

```
1. REFLECT  →  2. RESEARCH  →  3. PLAN  →  4. TODO LIST  →  5. EXECUTE
                                                              ↓
10. NOTIFY  ←  9. DOC UPDATE  ←  8. VERIFY (CI)  ←  7. VERIFY (REVIEW)  ←  6. COMMIT
```

### Step 1 — REFLECT (before executing anything)

**Before writing a single line of code, reflect back what you understood.**

- Summarize the user's request in your own words.
- List what's working (don't break it), what's broken (fix it), what's new (build it).
- Identify assumptions — flag any that need user confirmation.
- If the request is ambiguous or feels off (speech-to-text error?): **stop and ask**. Don't guess.

**Output:** A plain-text summary in the chat, so the user can confirm you understood correctly.

> **Why this step exists:** Rushing into code without confirming understanding leads to wrong work. A 2-minute reflection saves hours of rework.

### Step 2 — RESEARCH (understand before acting)

**Read the code/docs the task touches. Trace flows end-to-end. Never guess.**

- Read `memory/progress.md` (current state) + `memory/lessons-learned.md` (grep for tags matching the task type).
- Read the specific files the task touches — trace the full code path (UI → ViewModel → Store → SQL).
- Use **Explore sub-agents** for large investigations (read-only, parallelizable). They can read 10+ files and return a detailed report with exact line numbers.
- If the task involves a third-party system (Aniyomi extensions, MPV, etc.), read the reference code in `REFERENCES/` + the relevant documentation.
- Cross-reference against the old project (`REFERENCES/old-kuta/`) which compiles successfully — it's the proven pattern source.

**Output:** A mental model of what exists, what touches what, and where the changes need to go. Don't write code yet.

> **Why this step exists:** "Look before you write." The `lessons-learned.md` file exists because past sessions skipped this step and invented custom metadata keys, used wrong OkHttp versions, etc.

### Step 3 — PLAN (design before building)

**Design the solution. Split complex work into phases. Identify dependencies.**

- For non-trivial work: split into **phases** with clear boundaries (Phase 1, Phase 2, etc.).
- Order phases by **dependency** (if Phase 3 needs Phase 2's output, do Phase 2 first).
- For each phase: identify the files to touch, the approach, the risks.
- For big/complex phases: launch a **Plan sub-agent** to find flaws in your plan. Verify their findings yourself before acting. Don't loop endlessly — one review pass, fix real flaws, discard false ones, proceed.
- Decide: can this phase be verified by CI alone, or does it need device testing?

**Output:** A phase-by-phase plan, stated in the chat so the user can confirm before you execute.

> **Why this step exists:** The 4-phase session succeeded because each phase had a clear scope + verification gate. No phase touched another phase's files unexpectedly.

### Step 4 — TODO LIST (comprehensive, not random)

**Create a comprehensive todo list AFTER research + planning. NOT at the start.**

- Use `TodoWrite` to create the todo list.
- Each todo should be a specific, actionable, verifiable step.
- Include: code changes, sub-agent reviews, CI verification, doc updates, notifications.
- Order todos by dependency (research before code, code before review, review before push, push before CI, CI before notify).
- **Update the todo list after completing each task** — mark items `completed` as you go, add new items discovered during work.
- Keep only ONE item `in_progress` at a time.

**Output:** A todo list that covers every step from research to notification. The user can follow along by reading it.

> **Why this step exists:** A random todo list created before research is guesswork. A comprehensive todo list created after research + planning is a roadmap. The 29-entry todo list from the 4-phase session was built this way — it covered every file, every review, every CI check, every notification.

### Step 5 — EXECUTE (build, one phase at a time)

**Build the code. Follow CORE_RULES §5 (Code Rules) + §7 (Architecture).**

- **Frontend first** (so the user can see progress), then backend (CORE_RULES workflow.md step 3).
- **Modular complexity**: split big work across multiple files. Document as you go.
- Use sub-agents for **read-only research** (Explore type). **Do NOT use sub-agents for compile-error review** (removed by user instruction, D-281 — CI is the compiler of record; see Step 7).
- **Do NOT use sub-agents for code changes** to `AGENT-CONTEXT/` — only the main agent touches AGENT-CONTEXT (CORE_RULES §14).
- Sub-agents for webpage work: work ONLY in `DASHBOARD/webpage/`, never `AGENT-CONTEXT/` (CORE_RULES §14, §19).
- Apply `skills/ponytail.md`: simplest solution that works. Stdlib/native before new deps.
- **No assumptions.** If something is unclear after research: ask the user. Don't guess.
- **No rash decisions.** Think through each change. A wrong edit costs more than a slow edit.
- **No random edits.** Every edit should be deliberate, verified, and documented.

**Output:** Code changes on the feature branch, ready for review.

### Step 6 — COMMIT (when a phase is complete)

**Commit each phase as a separate commit with a clear message.**

- Commit message format: `feat: Phase N — <description>` or `fix: <description>`.
- Include a summary of what changed, why, and any notable decisions.
- Reference decision IDs (D-NNN) if applicable.
- Do NOT commit broken code. Verify (Step 7) before committing if possible.

**Output:** A clean commit on the feature branch.

### Step 7 — VERIFY (CI — the primary gate)

**Commit + push, then let GitHub Actions build. Do NOT dispatch sub-agents to pre-review for compile errors (user instruction, D-281).**

- The change was written against the researched call sites; CI compiles it for real.
- The pre-push loop is: fix → push → read CI results → fix → push again. Straight to CI, no sub-agent detour.
- **You CANNOT build locally** (CORE_RULES §8 — CI-only builds). CI IS the verification gate.

**Output:** A pushed commit whose build result is read from the GitHub Actions API — not assumed.

### Step 8 — VERIFY (CI — reading the result)

**Push to the feature branch. Wait for CI to build. Read the result.**

- Push: `git push origin <branch>`.
- Poll the GitHub Actions API: `curl -s -H "Authorization: token $TOKEN" "https://api.github.com/repos/{owner}/{repo}/actions/runs?per_page=3"`.
- Wait for the run to complete (typically 3-5 min for the build step).
- If CI **GREEN** ✅: proceed to the next phase. Send a notification (Step 10).
- If CI **RED** ❌: download the logs (`/actions/runs/{id}/logs` → unzip → grep for `^e:` or `error:`). Find the exact compile error. Fix it. Push again. Repeat until green.
- **Never claim "CI green" without polling the API** (lessons-learned.md: D-156 — a previous session claimed green but CI had actually failed).

**Output:** CI-verified code on the feature branch. APK artifact available for device testing.

### Step 9 — DOC UPDATE (keep docs in sync — CORE_RULES §6, §26)

**Update documentation in the SAME session as the work. Not "later."**

- `memory/progress.md` — live status (current phase, what's done, what's next, blockers).
- `memory/decisions.md` — new D-NNN entries for each significant decision.
- `memory/changelog.md` — high-level history entry for the session.
- `memory/lessons-learned.md` — new entries for mistakes, corrections, insights, patterns.
- `SESSION.md` + `master.md` — update if the project state changed (branch, phase, open items).
- `CORE_RULES.md` — promote a recurring lesson to a rule if it's a pattern.
- **Dashboard data** (`DASHBOARD/webpage/lib/data.ts`) — update module count, decisions, phases if they changed (delegate to a full-stack-dev sub-agent per §19).

**Output:** Docs that match reality. No drift. The next session can pick up immediately.

### Step 10 — NOTIFY (close the loop — CORE_RULES §11)

**Send a notification via `ntfy.sh` after each phase + at the end.**

- Per-phase: `curl -fsSL -H "Title: ANI-KUTA Agent" -d "<short result>" https://ntfy.sh/TASKISDONE`.
- At the end (if the user requested multiple notifications): send them one after another.
- Topic: `TASKISDONE` (user-specified). No secrets in the message body.
- **Never skip this.** The user relies on it to know when to check.

**Output:** The user is notified. The feedback loop is closed.

---

## Workflow Rules (Non-Negotiable)

1. **Reflect before executing.** Always summarize your understanding first.
2. **Research before planning.** Never plan based on assumptions.
3. **Plan before building.** Split into phases. Identify dependencies.
4. **Build a comprehensive todo list AFTER research.** Not a random one at the start.
5. **Execute one phase at a time.** Verify each before moving to the next.
6. **Push straight to CI.** No sub-agent compile pre-review (D-281) — GitHub Actions builds are the verification.
7. **CI is the final judge.** Poll the API. Read failures. Fix. Repeat.
8. **Update docs in the same session.** No drift.
9. **Notify after each phase.** Close the loop.
10. **Quality over speed.** Take as much time as needed. Don't rush. Don't skip steps.

---

## Branch Discipline

- **Create a feature branch** for each significant work session: `feature/<name>`.
- **Stay on the branch** for the entire session. All commits go there.
- **Do NOT merge to `main`** until the user explicitly says to. The user verifies on device first.
- **Do NOT make changes to `main` directly.** Ever.
- **Push frequently** — the sandbox is ephemeral (CORE_RULES §15). Unpushed work is lost.

---

## Session-End Checklist (CORE_RULES §15)

- [ ] All work committed on the feature branch.
- [ ] Pushed to GitHub.
- [ ] `git status` is clean.
- [ ] CI is green (verified via API, not assumed).
- [ ] Docs updated (progress.md, decisions.md, changelog.md, lessons-learned.md).
- [ ] ntfy.sh notification(s) sent.
- [ ] Short formatted summary given to the user with a test checklist.

---

## When to Ask the User vs. Decide Yourself

| Situation | Action |
|-----------|--------|
| Subjective choice (UI style, naming, scope) | ❓ Ask |
| Multiple valid technical approaches | ❓ Ask with a recommendation |
| Clear best practice, no user preference needed | ✅ Decide, document in `decisions.md` |
| Something is broken and the fix is obvious | ✅ Fix it, mention it |
| You're unsure | ❓ Ask — never guess |
| Speech-to-text ambiguity | ❓ Stop and clarify |
| Debug-build schema changes | ✅ Decide freely (see CORE_RULES §30) |

---

*This workflow is the proven process. Follow it. It produced 4 CI-green phases in one session without stopping. It will produce the same result again.*
