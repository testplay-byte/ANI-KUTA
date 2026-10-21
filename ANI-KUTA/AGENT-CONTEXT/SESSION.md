# SESSION — Read This At The Start Of Every Session

> A 60-second orientation. Read this FIRST, every time, before any work.
> Refreshed in **Round 119 (2026-10-02)**. If `memory/progress.md` shows a NEWER round than this file's stamps, trust progress.md — and refresh this file at the phase boundary (the Round-77 lesson).

---

## ⚡ Who You Are
You are the AI agent for **ANI-KUTA** — an Android multi-content streaming/downloading app (anime + movies + series today, manga and novels planned; Kotlin 2.2 + Jetpack Compose 1.10.4 + MPV + SQLDelight 2.0.2 + Koin 4.2.2 + Injekt + a CloudStream plugin system) — plus its companion web dashboard (Next.js → GitHub Pages).

**GitHub repo:** `testplay-byte/ANI-KUTA`. Repo root = the single wrapper folder `ANI-KUTA/` (per CORE_RULES §4) + `.github/` at root (a GitHub platform constraint) + the user's own `README.md` / `NEW_AGENT_SETUP.md` / `USER-UPLOADS/` (established).

---

## 📍 Current State (refreshed Round 120, 2026-10-19 — THE IMPROVEMENT-EXECUTION PROGRAM, W0 + H1; the CI dex root cause found and fixed; CI verdict: verify the run for commit 90c9f170 via the API)

- **Mainline branch:** `main` — this IS the default branch, renamed from `feature/round-57-cloudstream-downloads` in Round 118 (D-743 — the GitHub branch-rename API; history + tags preserved, no force-push). The 38 historical `release/*` branches were deleted by the user (the tags + GitHub releases survive — old installs still update); future release branches get cut FRESH from `main` per D-442. `feature/test-controller-v5` stays dormant (kept by explicit user order).
- **Versions:** the mainline carries **1.1.20 / 10120** (D-430: version bumps ride the RELEASE branch only, never the mainline). Latest **debug** release: **v1.1.74 / 10174** (round 130 — LIVE on testplay-byte, stable + latest, published 2026-10-19T21:34:06Z; asset `ani-kuta-v1.1.74-debug-arm64-v8a.apk` 60.7 MB; the SAME signing certificate as v1.1.73 → the updater installs IN PLACE). Latest **professional** release: **v1.1.5 / 10105 — LIVE on the official repo** (Confused-Creature-180/ANI-KUTA). **Rounds 121-129 changed NO mainline version; round 130 cut the debug release v1.1.74 on its release branch** (D-766 — the release branch carries the shipped number, the mainline stays the dev line ✓).
- **⚠ THE PHASE (D-738):** every round ends at **CI green** (the Build APK debug run's `anikuta-apk` artifact); releases of either kind need the user's explicit order; the routines live warm in `APP/ani-kuta/DOCUMENTATION/release/RELEASE-PLAYBOOK.md`.
- **🆕 ROUNDS 121-130 — the reliability program is LANDED and RELEASED.** (1) **D-746** — the plan is authoritative + the HTML report-companion convention. (2) **D-747 (H1)** — the download service owns its lifecycle (the manager's `stop()` is gone; it killed the D-246 auto-resume callback); user intent clears the network-pause marker; the marker is `NetworkPauseRegistry` mutated under the queue mutex; queued work restarts at process start; a review pass hardened it (the marker only marks a really-paused task, an offline error pauses instead of dying, the kick runs off the main thread, the wording is honest — process start, not boot). (3) **D-757** — the CI dex root cause: AGP 8.9.1 bundles R8 8.9.32 while Kotlin 2.2 needs R8 ≥ 8.10.21, and the daemon heap was 4 GB → **AGP 8.10.1** (R8 8.10.24; Gradle 8.11.1 = the wrapper we ship; API 36) + an **8 GB** heap. (4) **D-748 (H2)** — the nav route identity: the provider key is identity + the key's value hash (the "Key AniList was used multiple times" crash cannot happen) and the deep-link guard compares the identity only. (5) **D-749 (H3)** — the evidence-driven MPV watchdog (no more false "server is not responding" on a pause or at an episode's end; the real stall is caught). (6) **D-758 (M1)** — the progress writer is closed on every path. (7) **D-759 (M2)** — the OAuth login is an event (no 200 ms polling, the live stack decides) and every pending request carries an ID. (8) **D-760 (M3)** — the progress-completion contract: the final save outlives the screen, the EOF position is saved, the completion write is atomic, the bridge stops swallowing cancellations. (9) **D-761 (M4)** — the end-of-episode + resume contract (a completed episode starts at 0 with a five-second "Resume from mm:ss" offer; the next-episode action at 95 %/outro with the auto-advance preference; the resume position is milliseconds everywhere). (10) **D-762 (M5)** — one `firstWatchedAt` contract + a paced startup sweep. (11) **D-763/D-764 (M6)** — the CS error is no longer dropped, a buffering stream with no progress is a stall, a changed upstream file resets the cached ranges. (12) **D-765 (M7)** — the database is VERSIONED (a data-preserving 1 → 2 migration) + the episode-cache writes off the main thread. (13) **D-766 (round 130)** — the debug release **v1.1.74** (Routine A; the release branch carries the number; the signing verified against the v1.1.73 build on the device → an in-place install). **THE REPORT + THE TEST GUIDE + THE CHECKLISTS: `ROUNDS-120-129.html` (workspace root).** CI ledger: 39299023955 RED → 39304046376/39305662332 GREEN → … → **39322176393 GREEN (the final unit-test gate: every test source set compiles, every test task runs — 94 new test methods — and the APK builds)** → **39328048109 GREEN (the release)**. The release line stays UNMINIFIED (D-436).
- **Round 131 (2026-10-21) - the v1.1.74 device round is LANDED on `main` (030cb1de) and CI GREEN (run 39570066068, `run_unit_tests=true`):** downloads gain the persisted `WAITING_FOR_NETWORK` state + true auto-resume (default-network callback, INTERNET + VALIDATED, queued work promoted when connectivity returns, persisted rows recovered after a process restart); a relink replaces the old source base and persists the enriched extension axis; a completed episode starts at 0 with no resume offer, a partial one resumes, skip/auto-advance pick the numerically next episode (E10 -> E11), and the one-shot banner is a compact bottom-right pill with a 28 dp X. Decisions **D-767..D-769**; record doc **106**; deliverables `ROUND-131.html` / `.md` + `NAVIGATION-TEST-GUIDE.html` / `.md` (workspace root). No version bump, no tag, no release (D-738); the mainline stays 1.1.20/10120 and the latest debug release is v1.1.74/10174.
- **Latest records:** Round 130 = the debug release v1.1.74 (doc **105**, D-766); rounds 121-129 = the reliability program (doc **104**, D-748…D-765). `DOCUMENTATION/cloudstream-v2/` numbering is sequential — the next record is **107**; the next decision is **D-770**. The next round is **M8 — the extension trust work**.
- **The per-round loop (D-738 form):** device feedback → implement on the mainline → CI green (Build APK debug run — ≤2 runs/cycle, disclosed ledger, D-472; docs-only pushes build nothing) → **STOP — hand the user the artifact link + the test checklist** → LIVE-mirror docs → ntfy. No release steps.
- **CI paths-ignore (D-472):** docs-only / AGENT-CONTEXT / DASHBOARD / USER-UPLOADS / `.github/**` / `**.md` pushes build NOTHING. `DASHBOARD/webpage/**` pushes DO trigger the Pages deploy.
- **THIS SESSION'S NOTIFICATION:** ntfy topic **TASK808DONE** (the recent rounds' working topic — the §11 default THE-TASK-IS-DONE resumes if the user says so; the topic 429s under load — retry with backoff).

## 📂 If The Environment Was Just Cloned
1. Clone `https://github.com/testplay-byte/ANI-KUTA.git` (public — read needs no token). **PUSH** uses the credential helper that reads the PAT from `/home/z/.secrets/github-credentials` (repo-external, git-credential FORMAT — for raw API calls extract the `password=` line; NEVER commit it, NEVER paste it). If the sandbox lost the file, ask the user.
2. Checkout the mainline `main` (the default branch — renamed from `feature/round-57-cloudstream-downloads` in Round 118, D-743).
3. Read `AGENT-CONTEXT/memory/progress.md` — the TOP **CURRENT STATUS** block first, then the newest `## Round NN` sections at the BOTTOM (the file grows downward; the middle "Historical session" paragraphs are old).
4. Read `AGENT-CONTEXT/memory/decisions.md` — the newest entries sit at the TOP; latest = **D-757** (round 120, the CI dex root cause), then D-747 (the download service owns its lifecycle) and D-746 (the plan is authoritative).
5. Read `AGENT-CONTEXT/HANDOFF-ROUND-116.md` — the round-116 handoff (+ the round-117 addendum; historical context now) + doc 99 §7 (the round-117 re-host record). The latest round records: doc 100 (the branch consolidation) + doc 101 (the icon tile).
6. Read `AGENT-CONTEXT/knowledge/` files on demand (architecture, module-map, tech-stack, ui-customization, emulator-testing…).

---

## 🔑 Key Rules (full detail in `CORE_RULES.md` — 31 sections; that file WINS)
- **No assumptions.** Unsure → ask the user. Never guess.
- **Don't sugarcoat.** If a request has an issue, flag it directly. Never blindly agree.
- **User uses speech-to-text.** Correct obvious transcription errors from context; if still unclear → stop and ask.
- **APK builds: GitHub Actions ONLY.** Never build locally; never install the Android SDK/JDK/Gradle locally; never run Gradle (§8, D-281 — CI is the compiler of record: push → poll the run → read the logs → fix → repeat).
- **Push path = DEBUG, arm64-v8a only** (D-445). **Shipped releases = ALL ABIs + universal, release-signed in CI** (D-423) — but under **D-738 nothing ships without the user's explicit order**: rounds end at the CI debug artifact.
- **The version NEVER moves without the user's explicit order** (D-425). Bumps ride the release branch only (D-430).
- **No R8/minification on the release line** (D-436).
- **The update repo follows the build type** (D-440): debug builds check `testplay-byte/ANI-KUTA` releases; release builds check `Confused-Creature-180/ANI-KUTA` (the official repo — LIVE since the repo-external round 79: professional v1.1.3 is published there with the APK+ZIP set and the Pages site).
- **Merges / main-branch operations are USER-GATED.** The user's explicit instruction is required (and note `main` no longer exists — "merge to main" orders need interpretation at the moment they are given).
- **No "sponsor"/"sponsored" words anywhere** — code, UI, copy, docs (the user's standing order).
- **Debug builds = schema freedom** (§30): no migrations needed; stale dev DBs get wiped. Proper migrations return only when the user signals production.
- **Sub-agents for webpage work** work ONLY in `DASHBOARD/webpage/`, never `AGENT-CONTEXT/` (§14, §19). The main agent owns all AGENT-CONTEXT updates.
- **Task notification:** ntfy.sh topic **`THE-TASK-IS-DONE`** (§11).
- **Reports the user reads ship as HTML companions.** Backlogs, audits, analyses, and round summaries get a self-contained `.html` beside the `.md` source (same facts, same session). Copy `templates/html-report-template.html`; rules in `knowledge/reporting-format.md`. Charts/diagrams welcome; no external deps. (§6)
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

## 🚧 Open Items / Blocked (refreshed Round 119)
- **THE PHASE:** D-738 — debug builds only by default; ALL releases need the user's explicit order (the round-119 debug release v1.1.73 was the recovery path; the default resumed). The user's next device round on **v1.1.73** (the icon tile — checklist doc 101 §5 + the in-place update) is the next input; a revert is one commit if the verdict is no.
- **THE HANDOFF:** `AGENT-CONTEXT/HANDOFF-ROUND-116.md` — the round-116 delta handoff (+ the round-117 addendum; HISTORICAL context now). `HANDOFF-ROUND-115.md` (the full original) remains valid context; `HANDOFF-POSTER-NOTIFICATIONS.md` is HISTORICAL (rounds 46-47; resolved by the round-80 notification rework D-566..D-569). This SESSION.md + doc 100 carry the round-118 state.
- **Standing code-level items (doc 96 §6 + the earlier rounds' NOT-APPLIED set, unchanged):** the "Hard Sub" spaced-label edge (consistent with the MPV parse); the token-matching semantics living in five places repo-wide (a future consolidation candidate — designsystem already depends on core:common); the badge dims with its watched row; the InFlight tap no-op during the resolve window; ExtensionInstaller's two OS-fallback toasts; the AppToast id-counter race; the dead AutoLinkPopup.kt cleanup candidate; a FUTURE Coil 3.1.x upgrade must ADD respectCacheHeaders(false) to list-icon requests (doc 81 §11.2); the D-557 momentum-handoff direction note (doc 87 §6).
- **THE DEBUG LINE IS NON-DEBUGGABLE now (D-674):** if any future work needs `adb run-as` or a debugger on the debug build, that path is GONE by design (the revert = the one gradle flag + the three annotated re-keys). The updater's dev-repo gate + the Logger's DEBUG level + the Developer-tools section are keyed on IS_DEBUG_LINE / the .debug suffix — NOT on BuildConfig.DEBUG / FLAG_DEBUGGABLE (both read false on the dev line now).
- **Credentials:** the dev PAT lives repo-external at `/home/z/.secrets/github-credentials` (0600, account `testplay-byte`, admin). NOTE: git credential-store is NON-FUNCTIONAL in fresh sandboxes (approve writes nothing) — push auth uses the remote-URL-embedded PAT (repo-local `.git/config`; never committed). The **official-repo (release) PAT lives WITH THE USER** — re-ask when a professional release is ordered (the round-114 re-host used the user-provided PAT in-session; `/home/z/.secrets/official-repo-token` no longer exists).
- **The user's uploads folder:** `USER-UPLOADS/` contents may be cleared if cleanup is ever wanted, but the FOLDER itself stays (the user, round 89).
- **Dashboard deep debt (disclosed, D-565):** the dashboard's deep-history pages carry REPRESENTATIVE data (status facts refreshed at Round 115). (The user, round 89: leave the dashboard as-is for now — focus comes later.)
- **`feature/test-controller-v5`** stays dormant (kept by explicit user order — do not delete).
- **Version bookkeeping quirk (D-430):** the mainline says 1.1.20/10120 while the shipped debug line is at v1.1.71 — this is CORRECT by doctrine (bumps ride release branches). Don't "fix" it.

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
│   │   ├── download-research/   # the early round records + download research (next: 39)
│   │   ├── memory/              # progress / decisions / changelog / lessons-learned
│   │   ├── knowledge/           # quick-reference summaries (read on demand)
│   │   ├── HANDOFF-ROUND-116.md # the round-116 handoff + the round-117 addendum (HISTORICAL)
│   │   ├── HANDOFF-ROUND-115.md # the round-115 full original (valid context)
│   │   └── HANDOFF-POSTER-NOTIFICATIONS.md  # HISTORICAL (rounds 46-47)
│   ├── APP/ani-kuta/            # Android app — 57 Gradle modules (1 app + 33 core + 2 data + 21 feature)
│   │   └── DOCUMENTATION/       # technical docs; cloudstream-v2/ = the round records (next: 99); release/ = the build+release guides
│   ├── DASHBOARD/webpage/       # Next.js dashboard (→ GitHub Pages; sub-agents build it)
│   └── REFERENCES/              # old-kuta + animiru + webview-cloudflare-captcha (read-only)
└── .github/workflows/           # CI — build-apk / release-apk / release-build-once / deploy-dashboard
```

---
*This file is the quick-start. For everything else, see `navigation.md`. History: the pre-Round-77 version of this file described the round-29 era (v0.4.17, `streaming/CLOUDSTREAM-V2`) — it was fully rewritten in Round 77 after the drift was caught; refreshed rounds 115-116.*
