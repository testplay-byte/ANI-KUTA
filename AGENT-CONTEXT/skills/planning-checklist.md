# Planning Checklist

> Run through this before starting any phase or large task.

## Before You Build
- [ ] Read `master.md` + `navigation.md` + `memory/progress.md`.
- [ ] Check `questions/open-questions.md` for blockers.
- [ ] Define the **goal** of this phase in one sentence.
- [ ] List the **steps** to reach it.
- [ ] List **assumptions** — flag any that need user confirmation.
- [ ] List **risks** — what could go wrong.
- [ ] Write the plan into `planning/phase-<n>-<name>.md`.
- [ ] Run `skills/subagent-review.md` on the plan.
- [ ] Verify sub-agent findings yourself. Fix real flaws, discard false ones.
- [ ] Only then: start building.

## While You Build
- [ ] Frontend first (so user can see progress), then backend.
- [ ] Update `memory/progress.md` as items complete.
- [ ] Record any decision in `memory/decisions.md`.

## After You Build
- [ ] Verify with lint / build / agent-browser.
- [ ] Update `memory/changelog.md`.
- [ ] Update relevant `knowledge/` files.
- [ ] Report to user: short summary + what's next.
