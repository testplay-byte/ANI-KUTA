# Git Rules

> Version control conventions for ANI-KUTA.

## Branching
- `main` — stable, always buildable. PRs merge here.
- `dev` — integration branch for in-progress work (optional, decide in Phase 1).
- `feature/<short-name>` — one per feature/task.
- `fix/<short-name>` — bug fixes.
- `docs/<short-name>` — documentation only.

## Commits
- Use **Conventional Commits**:
  - `feat: add login screen`
  - `fix: correct token refresh timing`
  - `docs: update module map`
  - `chore: bump gradle`
  - `refactor: extract repository interface`
- Keep commits **small and focused**. One logical change per commit.
- Write a clear subject line (≤72 chars). Add body for *why* if non-obvious.

## Pushing
- Push to the feature branch, then open a PR to `main`.
- GitHub Actions builds the APK on push to `main` and on tags.
- Never force-push to `main`.

## The AGENT-CONTEXT Folder
- Lives **outside** the ANI-KUTA repo (in `/home/z/my-project/AGENT-CONTEXT`).
- It is **not** pushed to the ANI-KUTA GitHub repo.
- It is the agent's private workspace memory.
- Both `AGENT-CONTEXT/` and `ANI-KUTA/` are listed in the **parent workspace** `.gitignore` (`/home/z/my-project/.gitignore`), so an accidental `git add .` at the workspace root will never commit private memory or nest the ANI-KUTA repo as a gitlink.
- (Optional: we could version AGENT-CONTEXT separately later — see Q7 in `questions/open-questions.md`.)

## Secrets
- The GitHub token is scoped to `testplay-byte/ANI-KUTA` only.
- Use it for clone/push in CI. Store any new secrets as GitHub Actions secrets, never in code.
