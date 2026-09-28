# SESSION — Read This At The Start Of Every Session

> A 60-second orientation. Read this FIRST, every time, before any work.
> Refreshed in **Round 101 (2026-09-28)**. If `memory/progress.md` shows a NEWER round than this file's stamps, trust progress.md — and refresh this file at the phase boundary (the Round-77 lesson).

---

## ⚡ Who You Are
You are the AI agent for **ANI-KUTA** — an Android multi-content streaming/downloading app (anime + movies + series today, manga and novels planned; Kotlin 2.2 + Jetpack Compose 1.10.4 + MPV + SQLDelight 2.0.2 + Koin 4.2.2 + Injekt + a CloudStream plugin system) — plus its companion web dashboard (Next.js → GitHub Pages).

**GitHub repo:** `testplay-byte/ANI-KUTA`. Repo root = the single wrapper folder `ANI-KUTA/` (per CORE_RULES §4) + `.github/` at root (a GitHub platform constraint) + the user's own `README.md` / `NEW_AGENT_SETUP.md` / `USER-UPLOADS/` (established).

---

## 📍 Current State (refreshed Round 101, 2026-09-28)

- **Mainline branch:** `feature/round-57-cloudstream-downloads` — this IS the default branch. `main` was DELETED (D-552 — it had 0 unique commits). Other live branches: `release/1.1.3` (the professional release branch), `feature/test-controller-v5` (dormant, kept by user order).
- **Versions:** the mainline carries **1.1.20 / 10120** (D-430: version bumps ride the RELEASE branch only, never the mainline). Latest **debug** release: **v1.1.58 / 10158** (round 101 — D-684 the untrusted filter truth + the exact-spec no-results state, D-685 the details contract (the both-axes refresh, priority respect, the PERMANENT unlink), D-686 the share system (:core:share — three targets + the anikuta://content deep link + View in WebView) + the DetailsActionSheet, D-687 the adaptive-accent completeness + the WHOLE player page themed via defaulted nav-key fields, D-688 the player episode-list customization (both stacks) + in-player search, D-689 the sub-agent logical-testing discipline (caught one compile break + the 40-round-old CS sub/dub watched-identity bug). CI run 3 of 3 GREEN 36442686461 (runs 1-2 failed on new-code compile errors, disclosed doc 83 §9); precedes it: v1.1.57 (round 100 — D-674 the non-debuggable debug line (the input-latency fix: debuggable=true put ART in JIT-only mode with optimizations off and background dexopt excluded — isDebuggable = false now, with THREE re-keys preserving behavior: the Logger level + the Developer-tools section on the new IS_DEBUG_LINE buildConfigField, the updater repo on packageName.endsWith(".debug"); the line keeps its identity/bubble/keystore/verbose logs, gives up debugger attach / layout inspector / run-as, none used), D-675 placement-only animateItem (the default appearance spring fired for EVERY row scrolling into view — overlapping fades the whole duration of every scroll; fadeInSpec/fadeOutSpec null on all 8 row sites, the gap-closing placement spring + the D-580/D-645 exit choreographies preserved; plus DebugSettingsScreen's derivedStateOf — a D-673 miss), D-676 the list-icon request policy (the app-wide 200ms crossfade applies to every NON-memory-cache icon load — a storm of overlapping animated painters at fling speed; the shared buildListIconRequest(.crossfade(false) — an EXTENSION function needing an import, run 1's lesson) rides CsPluginIcon + the aniyomi available rows (which finally gain the never-blank letter tile) + the testing list, with a module-local twin on the manual-search wheel; CORRECTED THEORY, disclosed: the initial respectCacheHeaders/max-age theory was WRONG for the pinned Coil 3.0.4 — DefaultCacheStrategy serves disk entries indefinitely, no cache option exists or is needed, a future Coil 3.1.x upgrade must ADD respectCacheHeaders(false) to keep it), D-677 the CS rebuildLists instance-stability guard (the D-673 completion — the CS twin minted ~90 fresh Installed instances on every repo refresh/update-check; emit-on-change only now), D-678 the wheel + testing-list subcomposition retirement (the D-673 treatment on two more scroll surfaces — identical visuals)) — **LIVE**: `release/1.1.56` cut from the green head (the CI-fix commit 95c2b0bd; the implementation 87816700 — Build APK run **36363916114 GREEN**, run 2 of 2 after run 1's two crossfade-import errors, disclosed); the bump 8c4fa378 rides the branch (D-430); tag v1.1.56 → Release APK run **36364501952 GREEN** → **LIVE** (published 2026-09-28T01:09:06Z, stable latest, arm64-v8a debug APK **59.7 MB** — −11.2 MB vs v1.1.55, the non-debuggable release-style D8 pipeline — + SHA256SUMS.txt; verified via the API). The in-app updater (debug checks this repo, D-440 — the gate is NOW the .debug applicationId suffix, not FLAG_DEBUGGABLE) picks it up. The NEXT debug release is **v1.1.59 / 10159**. Latest **professional** release: **v1.1.3 / 10103** (`professional-v1.1.3`, 5 release-signed ABIs + universal — device-verified).
- **Latest records:** Round 101 implemented (D-684..D-689 — doc **83**, commits df14fbe7 → e2dbed2a; Build APK run 36442686461 GREEN on run 3 (disclosed overage); Release APK run 36443714057 for v1.1.58); `DOCUMENTATION/cloudstream-v2/` numbering is sequential — the next record is **84**; the next decision is **D-690**.
- **THE CURRENT PHASE (D-565) — DEBUG-FIRST:** the user directed that from now on ALL new features/QoL work lands on the mainline and ships via per-round **DEBUG** releases (the latest: **v1.1.58/10158**; the next is v1.1.59/10159). **Professional releases PAUSE** until the user explicitly orders the next one (D-425 version discipline unchanged — no bumps without the user's order).
- **The per-round loop is unchanged:** device feedback → implement on the mainline → CI green (≤2 runs/cycle, disclosed ledger, D-472 — round 93 honestly used 3) → `release/1.1.3N` cut from the green head → the bump rides that branch → tag `v1.1.3N` → the debug release publishes → LIVE-mirror docs → ntfy → the user's device round.
- **CI paths-ignore (D-472):** docs-only / AGENT-CONTEXT / DASHBOARD / USER-UPLOADS / `.github/**` pushes build NOTHING.

---

## 📂 If The Environment Was Just Cloned
1. Clone `https://github.com/testplay-byte/ANI-KUTA.git` (public — read needs no token). **PUSH** uses the credential helper that reads the PAT from `/home/z/.secrets/github-credentials` (repo-external, git-credential FORMAT — for raw API calls extract the `password=` line; NEVER commit it, NEVER paste it). If the sandbox lost the file, ask the user.
2. Checkout the mainline `feature/round-57-cloudstream-downloads` (the default branch).
3. Read `AGENT-CONTEXT/memory/progress.md` — the TOP **CURRENT STATUS** block first, then the newest `## Round NN` sections at the BOTTOM (the file grows downward; the middle "Historical session" paragraphs are old).
4. Read `AGENT-CONTEXT/memory/decisions.md` — the newest entries sit at the TOP; latest = **D-673** (round 98).
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
- Dashboard data (`DASHBOARD/webpage/lib/`) — delegate to a full-stack-dev sub-agent (§19) when the project's status facts changed; disclose any debt rather than silently skipping. (Standing round-89 instruction: the dashboard is left as-is until the user calls the focus.)

## 🚨 Session-End Checklist (NON-NEGOTIABLE)
- [ ] All work committed (`git add -A && git commit`).
- [ ] Pushed to GitHub (`git push`). **The environment can clear randomly — unpushed work is lost.**
- [ ] `git status` is clean.
- [ ] CI green — verified via the API, never assumed (lesson D-156).
- [ ] ntfy notification sent (topic `THE-TASK-IS-DONE`).
- [ ] Short formatted summary + test checklist given to the user (§3 + §31).

---

## 🚧 Open Items / Blocked (refreshed Round 101)
- **Round 101 SHIPPED v1.1.58 (D-684..D-689 — doc 83):** the six-stream round — the untrusted filter truth, the details contract (both-axes refresh + the PERMANENT unlink), the :core:share system + the DetailsActionSheet + View in WebView, the adaptive-accent completeness (the WHOLE player page themed via defaulted nav-key fields), the player episode-list customization (both stacks + search), and the sub-agent testing discipline (two guided audits, lead-verified — caught a remember-composable compile break pre-CI AND the 40-round-old CS sub/dub watched-identity corruption under active view filters). The user's device round on v1.1.58 is the next input (checklist doc 83 §10). NEXT: v1.1.59/10159, doc 84, D-690. NOT-APPLIED + DOCUMENTED: the dead AutoLinkPopup.kt (zero call sites — a cleanup-round candidate alongside ExtensionReorderList); the deep-link singleTask hardening (F4); downloaded-episode plays carry no accent (no DB churn by order). A FUTURE Coil 3.1.x upgrade must ADD respectCacheHeaders(false) to list-icon requests (doc 81 §11.2 — still standing).
- **Historical — round 99/100 shipped v1.1.56/v1.1.57 (D-674..D-678 / D-679..D-683 — docs 81/82):** the non-debuggable debug line (the input-latency fix — the debug line keeps its identity/bubble/verbose-logs/dev-repo updater; no debugger attach/layout inspector/run-as, none used), placement-only animateItem, the no-crossfade list-icon policy (+ the never-blank tile on the aniyomi available rows), the CS rebuildLists instance-stability guard, and the wheel + testing-list subcomposition retirement. The user's device round on v1.1.56 is the next input (checklist in doc 81 §10 — expect the FIRST session after install still JIT; the AOT win compounds over dexopt cycles); the next debug release after it is **v1.1.57/10157**.
- **THE DEBUG LINE IS NON-DEBUGGABLE now (D-674):** if any future work needs `adb run-as` or a debugger on the debug build, that path is GONE by design (the revert = the one gradle flag + the three annotated re-keys). The updater's dev-repo gate + the Logger's DEBUG level + the Developer-tools section are keyed on IS_DEBUG_LINE / the .debug suffix — NOT on BuildConfig.DEBUG / FLAG_DEBUGGABLE (both read false on the dev line now).
- **A future Coil 3.1.x upgrade changes the default cache behavior:** the pinned 3.0.4 serves disk-cache entries indefinitely (no header expiry); 3.1.x respects cache headers by default — the upgrade round must ADD per-request .respectCacheHeaders(false) to the list-icon requests (buildListIconRequest + rememberWheelIconRequest) to keep permanent icon caching (doc 81 §11.2).
- **The dev PAT** is stored repo-external at `/home/z/.secrets/github-credentials` (0600, account `testplay-byte`, admin, git-credential URL FORMAT `https://testplay-byte:<token>@github.com` — extract the token between ':' and '@' for API calls). The `official-repo-token` is NOT needed for current work (the user, round 89: debug versions only — the real release-repo token arrives when an official release is actually ordered).
- **The user's uploads folder:** `USER-UPLOADS/` contents may be cleared if cleanup is ever wanted, but the FOLDER itself stays (the user, round 89: "don't remove the folder, only the things in it").
- **release/1.1.38…release/1.1.52 branches stay on the remote** (the tags hold the code; branch deletion is user-gated).
- **The official repo is LIVE (resolved round 79):** Confused-Creature-180/ANI-KUTA carries professional v1.1.3 (11 assets: 5 APKs + 5 ZIPs + SHA256SUMS.txt) + the Pages download site with the APK|ZIP option selector. The process is documented: `ANI-KUTA-RELEASE-PLAYBOOK.md` (repo-external) + the official repo's `RELEASES.md`. The official-repo token lives at `/home/z/.secrets/official-repo-token`.
- **Dashboard deep debt (disclosed, D-565):** the dashboard carries REPRESENTATIVE data — decisions D-277..D-661 are not individually listed, and the per-table DB transcription is the D-192-era snapshot (current truth: 25 tables / 17 .sq files). Status-level facts were refreshed Round 77. A full backfill is available on the user's request. (The user, round 89: leave the dashboard as-is for now — focus comes later.)
- **`feature/test-controller-v5`** stays dormant (kept by explicit user order — do not delete).
- **Version bookkeeping quirk (D-430):** the mainline says 1.1.20/10120 while the shipped debug line is at v1.1.52 — this is CORRECT by doctrine (bumps ride release branches). Don't "fix" it.

## 🧪 Testing on the Emulator
The sandbox CAN run the app on an Android emulator (user-authorized §8 exception) — but read
`knowledge/emulator-testing.md` BEFORE any emulator/adb work (double-fork detach, timeout-wrapped
adb, input-text limits, the 4GB memory ceiling). Note D-445: debug CI builds are arm64-v8a only —
an x86_64 emulator image cannot install the debug APKs anymore; device rounds are the real loop. (Round 99 made the debug line NON-DEBUGGABLE — the old prefs-injection `run-as` trick would not work either.)

## 📦 Project Folders
```
repo-root/
├── ANI-KUTA/                    ← wrapper folder (all zones inside)
│   ├── AGENT-CONTEXT/           # YOUR memory + rules (you maintain this)
│   │   ├── download-research/   # the round records (numbering strictly sequential — next: 37)
│   │   ├── memory/              # progress / decisions / changelog / lessons-learned
│   │   └── knowledge/           # quick-reference summaries (read on demand)
│   ├── APP/ani-kuta/            # Android app — 56 Gradle modules (1 app + 32 core + 2 data + 21 feature)
│   │   └── DOCUMENTATION/cloudstream-v2/  # the round records (next: 81)
│   ├── DASHBOARD/webpage/       # Next.js dashboard (→ GitHub Pages; sub-agents build it)
│   └── REFERENCES/              # old-kuta + animiru (read-only)
└── .github/workflows/           # CI — build-apk / release-apk / release-build-once / deploy-dashboard
```

---
*This file is the quick-start. For everything else, see `navigation.md`. History: the pre-Round-77 version of this file described the round-29 era (v0.4.17, `streaming/CLOUDSTREAM-V2`) — it was fully rewritten in Round 77 after the drift was caught.*
