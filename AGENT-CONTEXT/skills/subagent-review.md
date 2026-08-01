# Skill: Sub-Agent Review

> How to use sub-agents to find flaws in plans. Per `CORE_RULES.md` + `workflow.md` step 2.

## Why
Plans have blind spots. A sub-agent reviewing the plan catches flaws the main agent misses. The main agent verifies findings before acting — no rash action, no endless looping.

## When
- Big/complex changes (restructures, new modules, architecture decisions).
- Before any change that's hard to reverse.
- NOT needed for trivial fixes (typos, one-line changes).

## Steps
1. **Write the plan down** — in chat, in `memory/progress.md`, or as a scratch note. The plan must exist in text form (not just in your head) so the sub-agent can read it.
2. **Launch a Plan sub-agent** with:
   - The full plan text.
   - The relevant `CORE_RULES.md` / `knowledge/` sections.
   - A clear instruction: *"Find flaws, gaps, risks, contradictions, and missing steps. Do NOT implement."*
3. **Collect findings**.
4. **Verify each finding yourself**:
   - Is it a real flaw? (some are false positives)
   - Is it in scope?
   - Does it need a user decision, or can you fix it?
5. **Fix real flaws** in the plan.
6. **Discard false positives** (note them so you don't re-litigate).
7. **Proceed** — one review pass is enough. Don't loop.

## Rules
- Never act on a sub-agent finding without verification.
- Never let a sub-agent make user-facing decisions.
- If a finding needs user input → note it in `memory/progress.md` under "Blockers / Open Questions".
- Sub-agents append their work record to `/home/z/my-project/worklog.md`.
