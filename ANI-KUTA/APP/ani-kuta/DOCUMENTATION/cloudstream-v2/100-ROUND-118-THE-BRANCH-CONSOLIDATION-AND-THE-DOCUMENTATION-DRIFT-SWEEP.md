# Round 118 — The Branch Consolidation And The Documentation Drift Sweep

> **Status:** EXECUTED (2026-10-02). Decision: **D-743**. Docs-only round — no code changed, no CI APK build (the D-472 paths-ignore; the `deploy-dashboard.yml` edit triggers ONE Pages deploy as the live trigger verification).
> **The order (the user, round 118):** "optimize the branches… I have deleted them, so you might need to check them out and such… rename the main branch a bit better… removing this feature branch and only keeping a single main branch… the very key important thing which I want you to do for the current time being is the documentation drift fix… do not rush anything; take your time, look into the things properly and handle each and every single one of the things with proper care, with proper planning, with proper understanding and with proper management of things."

## 1. The order, traced before execution

Three work items, none touching app code:

1. **The branch cleanup** — the user had already deleted the 38 `release/*` branches on GitHub; the local clone needed the stale remote-tracking refs pruned.
2. **The mainline rename** — `feature/round-57-cloudstream-downloads` → **`main`**, so the repo carries a single properly-named mainline branch. (The user's ruling on the earlier audit findings: the plain-chat PAT exposure is accepted as-is; the stale CI test-gate comment is deferred; the v1.1.14 stranded installs are accepted — **v1.1.5 is the professional line's head**, full stop.)
3. **The documentation drift sweep** — every stale fact found in the round-118 audit fixed, with verification against reality before each edit.

## 2. The branch work (D-743)

1. **The prune:** `git fetch --prune` cleared 38 stale `origin/release/*` tracking refs. **The 140 tags and the GitHub releases SURVIVE** — old installs still see and download their updates. Future release branches are cut FRESH from `main` per D-442 (the rename restores that doctrine's literal form).
2. **The rename:** the GitHub branch-rename API (`POST /repos/testplay-byte/ANI-KUTA/branches/feature/round-57-cloudstream-downloads/rename`, `{"new_name":"main"}`) — history, tags, and the default-branch pointer follow automatically; NO force-push anywhere (the "never force-push main" rule stays intact). The local clone: `git fetch --prune` → `git switch main` → the stale local branch deleted → `git remote set-head origin -a`.
3. **The workflow reality:** `build-apk.yml` already triggered on `main` (the round-37/38 trigger list) — **zero change on the build path**. `deploy-dashboard.yml`'s hardcoded literal (the D-552 era) was the ONE live breakage the rename would have caused; its trigger is now `main` (+ the D-743 comment). Its own push fires ONE Pages deploy — the live verification that the new trigger works.
4. **Left untouched by standing order:** `feature/test-controller-v5` stays dormant (kept by explicit user order — flagged to the user in the round report, not deleted).

## 3. The drift sweep — findings and fixes

Every finding was verified against the code/repo BEFORE the edit. The LIVE docs only — the handoffs, `download-research/`, the round records ≤ 99, and the changelog's old entries stay AS WRITTEN (history is not rewritten).

| File | The stale fact | The fix |
|---|---|---|
| `master.md` | v1.1.71/v1.1.14 "LIVE", "next doc 98", "350 lessons", the `TASKISDONE` topic, the old branch reality | The status block refreshed to round 118 (v1.1.72/v1.1.5 LIVE, D-001..D-743, next doc 101, the topic line defers to SESSION.md's override) |
| `navigation.md` | `HANDOFF-ROUND-116.md` not indexed; "docs 72-97 = rounds 90-115; next 98"; the sandbox `worklog.md` unmarked | The index rows corrected (116 = the latest handoff, 115 = historical context); "docs 72-100 = rounds 90-118; next 101"; the worklog note marked sandbox-era |
| `knowledge/old-vs-new.md` | 46 modules / 331 .kt / 26 tables; the AniList tracker "placeholder"; release signing "pending" | 57 / 634 / 25 (the round-115-verified counts); the tracker DONE (`:core:tracker-anilist`, ~1471 lines); signing DONE (CI-side, D-423) |
| `knowledge/tech-stack.md` | kotlinx-coroutines 1.9.0 | 1.11.0 (+ the round-97 bump note — the D-740 class) |
| `knowledge/project-overview.md` | "14 pages"; the next-focus pointed at the retired v1.1.71/v1.1.14 device round; no branch reality | 20 pages; the round-116-artifact next-focus; the `main` bullet added |
| `knowledge/architecture.md` | The debt list said WatchKey "15 fields" (its own §body said 17) | **17** — re-verified by counting the `val` fields in `feature/watch/api/.../WatchKey.kt` |
| `memory/decisions.md` | D-742's status line said "the re-host PENDING the release PAT" | DONE (it executed the same day — release id 402016815); **D-743 prepended** |
| `NEW_AGENT_SETUP.md` | The blob URL + the note "`main` does not exist" | The `blob/main/...` URL + the round-118 rename note |
| `DOCUMENTATION/release/RELEASE-PLAYBOOK.md` + `BUILD-AND-BRANCH-GUIDE.md` | The mainline named `feature/round-57-cloudstream-downloads`; the release-branch examples | `main` + the round-118 revision paragraph + the fresh-cut-from-main release-branch row |
| `SESSION.md` | The Round-117 stamps + the old branch reality | Refreshed in place (the Round-118 stamps, the branch bullet, the clone instructions, the open-items header) |

**Disclosed, NOT fixed (by standing order):** the dashboard's data files (`lib/data.ts`, `lib/decisions.ts`, a few `app/*/page.tsx`) still carry old-branch-name strings — the round-89 standing instruction keeps the dashboard as-is until the user calls the focus.

## 4. CI history

- No APK build this round (docs-only + the `deploy-dashboard.yml` edit — the D-472 `paths-ignore` covers both; the last code CI stands GREEN: run 36925093812 @ eaf9445; the round-117 release runs 37036338129 + 37036448781 both GREEN first-try).
- `deploy-dashboard.yml`: ONE Pages deploy expected from the workflow-edit push (the live trigger verification).

## 5. The ledger updates this round

- `memory/progress.md` — the top CURRENT STATUS block (round 118) + the Round 118 section at the bottom + the What's-Next / Blockers / Known-doc-debt / Last-Updated sections refreshed.
- `memory/decisions.md` — D-743 prepended; D-742's status corrected.
- `memory/changelog.md` — the Round 118 entry appended.
- `memory/lessons-learned.md` — the hardcoded-branch-trigger lesson (INSIGHT) + the branch-rename-API note.
- `SESSION.md`, `master.md`, `navigation.md`, `workflow.md`, `knowledge/*`, `NEW_AGENT_SETUP.md`, the release playbooks — the round-118 state.
- Dashboard — untouched (the round-89 standing instruction); the branch-name debt disclosed.
