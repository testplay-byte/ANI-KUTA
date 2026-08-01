# Sub-Agent Review Procedure

> How to use sub-agents to find flaws in a plan. (Per user requirement.)

## Why
The user explicitly wants plans stress-tested by sub-agents before execution, and the main agent must verify the findings (not act rashly).

## Steps
1. **Write the plan** into a `planning/phase-<n>-<name>.md` file first.
2. **Launch a sub-agent** (use the `Plan` agent type) with:
   - The full plan text.
   - The relevant `rules/` and `knowledge/` files.
   - A clear instruction: *"Find flaws, gaps, risks, contradictions, and missing steps in this plan. Do not implement anything."*
3. **Collect findings** from the sub-agent.
4. **Verify each finding yourself**:
   - Is it a real flaw? (some may be false positives)
   - Is it in scope for this phase?
   - Does it need a user decision, or can the agent fix it?
5. **Fix real flaws** in the plan.
6. **Discard false positives** (note them in the plan file so we don't re-litigate).
7. **Only then** proceed to execution.

## Rules
- Never act on a sub-agent finding without verification.
- Never let a sub-agent make user-facing decisions.
- If a finding needs user input, add it to `questions/open-questions.md`.
