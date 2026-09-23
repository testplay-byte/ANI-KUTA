# Record 37 — Round 77 (D-565): the project reset — the debug-first phase begins

The professional v1.1.3 came back FULLY verified: "I downloaded the version 1.1.3, and it
updated, showed me that it will update over the old one, which was perfect… after updating it,
it successfully got updated… everything was looking proper, good, clean, and beautiful… in the
release version everything was proper, and there were no debug features… fully satisfactory."

With the release chapter closed, the user redirected the project: **"from now on forward, we are
going to be working on the debug releases and we are going to implement features there… and
afterwards, we will do a proper release again when everything has been finalized and properly
managed."** This round's order: *set everything up, manage the project better, get everything
sorted out — the environment, the GitHub repository, every part in its proper state — so the
next tasks can move smoothly.*

## 1. THE DOCTRINE (D-565)

1. **DEBUG-FIRST:** all new features/QoL land on the mainline
   `feature/round-57-cloudstream-downloads` and ship via the per-round DEBUG releases — the next
   one is **v1.1.38/10138** (the 1.1.3N debug series continues). The mainline itself stays
   1.1.20/10120 (D-430 — the bump rides the release branch).
2. **THE PROFESSIONAL LINE PAUSES:** no `release/1.1.x` professional cuts, `professional-vN`
   tags, or published releases until the user explicitly orders the next one (D-425 unchanged).
3. **The rest of the loop is unchanged:** the ≤2 CI budget (D-472), the docs set, ntfy, the
   frozen-carry discipline.

## 2. THE BOOTSTRAP LAYER WAS ROTTEN — found and rebuilt

The files an agent reads FIRST still described the round-29 era (v0.4.17,
`streaming/CLOUDSTREAM-V2`, "latest decision D-329") while the project sits at Round 76/D-564.
Root cause: the per-round doc ritual updates progress/decisions/changelog but never touches the
bootstrap layer — so it drifts silently across ~47 rounds and every phase boundary.

Fixed in this round:

| File | What changed |
|---|---|
| `AGENT-CONTEXT/SESSION.md` | FULLY rewritten: the mainline identity (the default branch IS `feature/round-57-cloudstream-downloads`; `main` deleted per D-552), both live releases (debug v1.1.37/10137, professional v1.1.3/10103), the debug-first protocol, the D-425/D-430/D-436/D-440/D-445 digest, correct clone/push instructions, the open items (the re-host token, the dashboard debt), 56-module folder map. |
| `AGENT-CONTEXT/memory/progress.md` | A **CURRENT STATUS** block added at the very top; the twelve stale "**Latest session**" paragraphs relabeled **Historical** (they were actively misleading — none of them was "latest"). |
| `AGENT-CONTEXT/master.md` | The status block refreshed: the mainline branch, the phase, 56 modules (1 app + 32 core + 2 data + 21 feature), 25 tables / 17 .sq, 569 Kotlin files, D-001..D-565. |
| `AGENT-CONTEXT/navigation.md` | CORE_RULES row corrected to **31 sections**. |
| `NEW_AGENT_SETUP.md` (repo root) | The `blob/main/…` CORE_RULES link FIXED — `main` does not exist; the link now targets the default branch. |
| `CORE_RULES.md` §11 | The ntfy topic aligned with actual practice: **`THE-TASK-IS-DONE`** (the file said `TASKISDONE` while every recent round sent `THE-TASK-IS-DONE`). |
| `memory/lessons-learned.md` | Two new entries (the phase-boundary rot pattern; the Pages API/environment-policy insight). |

## 3. THE GITHUB REPOSITORY SETUP — the silent Pages breakage found and repaired

**Finding:** the web dashboard (DASHBOARD/webpage → GitHub Pages) has been OFFLINE-DEPLOYING
since `main` was deleted (D-552). The evidence chain:

- `GET /repos/…/pages` **unauthenticated** → 404 (misleading — see the lesson below);
- authenticated → 200, `build_type: "workflow"`, site configured;
- the last `deploy-dashboard` run (35615314575, 2026-09-21): the **build job GREEN**, the
  **deploy job FAILED** — `actions/deploy-pages` is gated by the `github-pages` ENVIRONMENT,
  whose **custom branch policy** listed only DEAD branches (`main`, `feature/debug-bubble`,
  `functionality/improvements`, `test-feature/video-cache-new-download`). The current mainline
  was never in the list → every deploy rejected since D-552.
- the live site (testplay-byte.github.io/ANI-KUTA) still serves the August content — the last
  successful deploy was from `main` on 2026-08-28 (the v0.2.63 era).

**Repairs:**
1. The mainline ADDED to the environment's deployment-branch policy (environments API).
2. The three dead-branch policies DELETED (`main` kept for the eventual future).
3. The dashboard's STATUS DATA refreshed by the full-stack-dev sub-agent (13 files,
   status-level only — the design, routes, and historical pages untouched): 56 modules (9
   modules the dashboard had never recorded were added to the module list), 25 tables / 17 .sq
   files, 569 Kotlin files, D-001..D-565 with representative entries for D-563/D-564/D-565, the
   hero status line (Round 76 closed — both releases live — the debug-first phase), NAV_ITEMS
   descriptions corrected, ADR-002 marked superseded (releases now ship all ABIs + universal).
   Local build verified: 22/22 pages exported.
4. Disclosed debt: the per-table DB transcription stays the D-192-era snapshot and decisions
   D-277..D-562 remain representative — a full backfill available on the user's request.

## 4. THE ENVIRONMENT / LEDGER

- Local clone verified clean and in sync (mainline @ ea7a1d18 = origin; `release/1.1.3`
  @ c95bb163 = origin; `test-controller-v5` dormant). `git fetch --prune` clean.
- PAT verified working (push credentials via the repo-external credential helper).
- GitHub repo state verified: 0 open issues, releases healthy (v1.1.37 debug, professional-v1.1.3
  stable --latest), recent CI runs all green (the one red run 35811351187 is round-74's disclosed
  and fixed failure).
- Repo-external staging files in `/home/z/.secrets/` (tag messages, the v1.1.3 release body)
  kept — they are the shipped-tags' message records, outside the repo by rule.
- **CI ledger: zero APK runs** (docs-only + dashboard-only push; the D-472 paths-ignore covers
  it) + **ONE deploy-dashboard run** (disclosed) expected green on push.

## 5. WHAT'S NEXT (the debug-first era)

The user brings the next feature/QoL list → implement on the mainline → CI green (≤2 runs) →
`release/1.1.38` cut → bump 1.1.38/10138 → tag `v1.1.38` → the debug release → docs → ntfy →
the device round. Professional releases resume only on the user's explicit order.
