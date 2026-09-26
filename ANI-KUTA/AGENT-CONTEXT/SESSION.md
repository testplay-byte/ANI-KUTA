# SESSION — Read This At The Start Of Every Session

> A 60-second orientation. Read this FIRST, every time, before any work.
> Refreshed in **Round 89 (2026-09-26)**. If `memory/progress.md` shows a NEWER round than this file's stamps, trust progress.md — and refresh this file at the phase boundary (the Round-77 lesson).

---

## ⚡ Who You Are
You are the AI agent for **ANI-KUTA** — an Android anime streaming/downloading app (Kotlin 2.2 + Jetpack Compose 1.10.4 + MPV + SQLDelight 2.0.2 + Koin 4.2.2 + Injekt + a CloudStream plugin system) — plus its companion web dashboard (Next.js → GitHub Pages).

**GitHub repo:** `testplay-byte/ANI-KUTA`. Repo root = the single wrapper folder `ANI-KUTA/` (per CORE_RULES §4) + `.github/` at root (a GitHub platform constraint) + the user's own `README.md` / `NEW_AGENT_SETUP.md` / `USER-UPLOADS/` (established).

---

## 📍 Current State (refreshed Round 89, 2026-09-26)

- **Mainline branch:** `feature/round-57-cloudstream-downloads` — this IS the default branch. `main` was DELETED (D-552 — it had 0 unique commits). Other live branches: `release/1.1.3` (the professional release branch), `feature/test-controller-v5` (dormant, kept by user order).
- **Versions:** the mainline carries **1.1.20 / 10120** (D-430: version bumps ride the RELEASE branch only, never the mainline). Latest **debug** release: **v1.1.46 / 10146** (round 89 — D-625: THE LINK SOURCES REVERT — the sheet restored byte-identical to v1.1.43's single alphabetical list; the D-612/D-613/D-614 changes undone; every round-87/88 extension-testing change KEPT) — **LIVE**: `release/1.1.46` cut from the green head 4cf6acbe (the round-89 docs ledger, above the revert d87b8d02 whose Build APK run 36243004630 went GREEN), Release APK run 36243440742 GREEN FIRST-TRY (published 2026-09-26T12:58Z), the stable latest release carries the arm64-v8a debug APK 68.3 MB + SHA256SUMS.txt and the honest 4-bullet revert tag body (D-466) — the in-app updater (debug checks this repo, D-440) picks it up. The NEXT debug release is **v1.1.47 / 10147**. Latest **professional** release: **v1.1.3 / 10103** (`professional-v1.1.3`, 5 release-signed ABIs + universal — device-verified).
- **Latest records:** Round 89 implemented AND SHIPPED as v1.1.46 (D-625 — doc **71**); `DOCUMENTATION/cloudstream-v2/` numbering is sequential — the next record is **72**.
- **THE CURRENT PHASE (D-565) — DEBUG-FIRST:** the user directed that from now on ALL new features/QoL work lands on the mainline and ships via per-round **DEBUG** releases (the latest: **v1.1.46/10146** — the 1.1.3N debug series continues; the next is v1.1.47/10147). **Professional releases PAUSE** until the user explicitly orders the next one (D-425 version discipline unchanged — no bumps without the user's order).
- **The per-round loop is unchanged:** device feedback → implement on the mainline → CI green (≤2 runs/cycle, disclosed ledger, D-472) → `release/1.1.3N` cut from the green head → the bump rides that branch → tag `v1.1.3N` → the debug release publishes → LIVE-mirror docs → ntfy → the user's device round.
- **CI paths-ignore (D-472):** docs-only / AGENT-CONTEXT / DASHBOARD / USER-UPLOADS / `.github/**` pushes build NOTHING.

---

## 📂 If The Environment Was Just Cloned
1. Clone `https://github.com/testplay-byte/ANI-KUTA.git` (public — read needs no token). **PUSH** uses the credential helper that reads the PAT from `/home/z/.secrets/github-credentials` (repo-external — NEVER commit it, NEVER paste it). If the sandbox lost the file, ask the user.
2. Checkout the mainline `feature/round-57-cloudstream-downloads` (the default branch).
3. Read `AGENT-CONTEXT/memory/progress.md` — the TOP **CURRENT STATUS** block first, then the newest `## Round NN` sections at the BOTTOM (the file grows downward; the middle "Historical session" paragraphs are old).
4. Read `AGENT-CONTEXT/memory/decisions.md` — the newest entries sit at the TOP; latest = **D-625** (round 89).
5. Read `AGENT-CONTEXT/memory/lessons-learned.md` → grep for tags matching your task type.
6. Read `AGENT-CONTEXT/knowledge/` files on demand (architecture, module-map, tech-stack, ui-customization, emulator-testing…).

---

## 🔑 Key Rules (full detail in `CORE_RULES.md` — 31 sections; that file WINS)
- **No assumptions.** Unsure → ask the user. Never guess.
- **Don't sugarcoat.** If a request has an issue, flag it directly. Never blindly agree.
- **User uses speech-to-text.** Correct obvious transcription errors from context; if still unclear → stop and ask.
- **APK builds: GitHub Actions ONLY.** Never build locally; never install the Android SDK/JDK/Gradle locally; never run Gradle (§8, D-281 — CI is the compiler of record: push → poll the run → read the logs → fix → repeat).
- **Push path = DEBUG, arm64-v8a only** (D-445). **Shipped releases = ALL ABIs + universal, release-signed in CI** (D-423).
- **The version NEVER moves without the user's explicit order** (D-425). Bumps ride the release branch only (D-430).
- **No R8/minification on the release line** (D-436).
- **The update repo follows the build type** (D-440): debug builds check `testplay-byte/ANI-KUTA` releases; release builds check `Confused-Creature-180/ANI-KUTA` (the official repo — LIVE since the repo-external round 79: professional v1.1.3 is published there with the APK+ZIP set and the Pages site).
- **Merges / main-branch operations are USER-GATED.** The user's explicit instruction is required (and note `main` no longer exists — "merge to main" orders need interpretation at the moment they are given).
- **No "sponsor"/"sponsored" words anywhere** — code, UI, copy, docs (the user's standing order).
- **Debug builds = schema freedom** (§30): no migrations needed; stale dev DBs get wiped. Proper migrations return only when the user signals production.
- **Sub-agents for webpage work** work ONLY in `DASHBOARD/webpage/`, never `AGENT-CONTEXT/` (§14, §19). The main agent owns all AGENT-CONTEXT updates.
- **Task notification:** ntfy.sh topic **`THE-TASK-IS-DONE`** (§11).
- **Quality over speed.** Don't rush. Don't skip steps (§18). Frozen/accepted surfaces stay byte-identical unless the user orders a change.

---

## 🔄 The Task Loop (full detail in `workflow.md`)
```
REFLECT → RESEARCH → PLAN → TODO LIST → EXECUTE → COMMIT → VERIFY (CI) → DOC UPDATE → NOTIFY
```
1. **Reflect** — summarize what you understood before executing.
2. **Research** — read the code/docs the task touches. Look before you write.
3. **Plan** — split into phases; build the todo list AFTER research, not before.
4. **Execute** — one phase at a time.
5. **Commit** — Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`), each phase separately, with the **why** in the body.
6. **Verify (CI)** — push → poll the GitHub Actions API → read failures → fix → repeat until green. Do NOT dispatch sub-agents to pre-review for compile errors (D-281).
7. **Doc update** — progress.md + decisions.md + changelog.md + lessons-learned.md + the round's `download-research/NN` record — in the SAME session (§26; no "later").
8. **Notify** — ntfy after each phase + at the end.

## 📝 After Every Task (Update These)
- `memory/progress.md` — the top CURRENT STATUS block + a new `## Round NN` section at the BOTTOM.
- `memory/decisions.md` — a new D-NNN PREPENDS at the top (right after `## Decisions`).
- `memory/changelog.md` — append the round's section at the bottom (append-only narrative).
- `memory/lessons-learned.md` — any new lesson (dedup-check first).
- `AGENT-CONTEXT/download-research/NN-ROUND-<slug>.md` — the round record; numbering strictly sequential.
- Dashboard data (`DASHBOARD/webpage/lib/`) — delegate to a full-stack-dev sub-agent (§19) when the project's status facts changed; disclose any debt rather than silently skipping.

## 🚨 Session-End Checklist (NON-NEGOTIABLE)
- [ ] All work committed (`git add -A && git commit`).
- [ ] Pushed to GitHub (`git push`). **The environment can clear randomly — unpushed work is lost.**
- [ ] `git status` is clean.
- [ ] CI green — verified via the API, never assumed (lesson D-156).
- [ ] ntfy notification sent (topic `THE-TASK-IS-DONE`).
- [ ] Short formatted summary + test checklist given to the user (§3 + §31).

---

## 🚧 Open Items / Blocked (refreshed Round 89)
- **Round 89 SHIPPED v1.1.46 (the LINK SOURCES REVERT):** the user's session order — the v1.1.45 state was "not satisfactory"; the link-sources changes (D-612/D-613/D-614, all inside ManualSearchSheet.kt) reverted byte-identically to v1.1.43 (+ the round-89 annotation comment); every extension-testing change from rounds 87-88 KEPT. Revert commit d87b8d02, Build APK run 36243004630 GREEN. KNOWN CONSEQUENCE (disclosed): the D-612 squish fix rode the same lines — the search bar can again clip when keyboard + full list exceed the screen (the v1.1.43 behavior); a one-line cap change re-applies it on the old layout if ordered. The D-614 two-column implementation stays recoverable at tag v1.1.45 (commit 05a79f80). The user's device round on v1.1.46 — AND the promised process/workflow instructions ("I'll give you some more instructions on how to work in the codebase properly… the current implementation is most definitely lacking in a lot of areas") — are the next input; the next debug release is **v1.1.47/10147**.
- **The dev PAT** is stored repo-external at `/home/z/.secrets/github-credentials` (0600, account `testplay-byte`, admin). The `official-repo-token` is NOT needed for current work (the user, round 89: debug versions only — the real release-repo token arrives when an official release is actually ordered).
- **The user's uploads folder:** `USER-UPLOADS/` contents may be cleared if cleanup is ever wanted, but the FOLDER itself stays (the user, round 89: "don't remove the folder, only the things in it").
- **release/1.1.38…release/1.1.46 branches stay on the remote** (the tags hold the code; branch deletion is user-gated).
- **The official repo is LIVE (resolved round 79):** Confused-Creature-180/ANI-KUTA carries professional v1.1.3 (11 assets: 5 APKs + 5 ZIPs + SHA256SUMS.txt) + the Pages download site with the APK|ZIP option selector. The process is documented: `ANI-KUTA-RELEASE-PLAYBOOK.md` (repo-external) + the official repo's `RELEASES.md`. The official-repo token lives at `/home/z/.secrets/official-repo-token`.
- **Dashboard deep debt (disclosed, D-565):** the dashboard carries REPRESENTATIVE data — decisions D-277..D-625 are not individually listed, and the per-table DB transcription is the D-192-era snapshot (current truth: 25 tables / 17 .sq files). Status-level facts were refreshed Round 77. A full backfill is available on the user's request. (The user, round 89: leave the dashboard as-is for now — focus comes later.)
- **`feature/test-controller-v5`** stays dormant (kept by explicit user order — do not delete).
- **Version bookkeeping quirk (D-430):** the mainline says 1.1.20/10120 while the shipped debug line is at v1.1.46 — this is CORRECT by doctrine (bumps ride release branches). Don't "fix" it.

## 🧪 Testing on the Emulator
The sandbox CAN run the app on an Android emulator (user-authorized §8 exception) — but read
`knowledge/emulator-testing.md` BEFORE any emulator/adb work (double-fork detach, timeout-wrapped
adb, input-text limits, the 4GB memory ceiling). Note D-445: debug CI builds are arm64-v8a only —
an x86_64 emulator image cannot install the debug APKs anymore; device rounds are the real loop.

## 📦 Project Folders
```
repo-root/
├── ANI-KUTA/                    ← wrapper folder (all zones inside)
│   ├── AGENT-CONTEXT/           # YOUR memory + rules (you maintain this)
│   │   ├── download-research/   # the round records (numbering strictly sequential — next: 37)
│   │   ├── memory/              # progress / decisions / changelog / lessons-learned
│   │   └── knowledge/           # quick-reference summaries (read on demand)
│   ├── APP/ani-kuta/            # Android app — 56 Gradle modules (1 app + 32 core + 2 data + 21 feature)
│   ├── DASHBOARD/webpage/       # Next.js dashboard (→ GitHub Pages; sub-agents build it)
│   └── REFERENCES/              # old-kuta + animiru (read-only)
└── .github/workflows/           # CI — build-apk / release-apk / release-build-once / deploy-dashboard
```

---
*This file is the quick-start. For everything else, see `navigation.md`. History: the pre-Round-77 version of this file described the round-29 era (v0.4.17, `streaming/CLOUDSTREAM-V2`) — it was fully rewritten in Round 77 after the drift was caught.*
