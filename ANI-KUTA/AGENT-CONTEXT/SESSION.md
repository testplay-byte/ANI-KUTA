# SESSION — Read This At The Start Of Every Session

> A 60-second orientation. Read this FIRST, every time, before any work.
> Refreshed in **Round 115 (2026-10-02)**. If `memory/progress.md` shows a NEWER round than this file's stamps, trust progress.md — and refresh this file at the phase boundary (the Round-77 lesson).

---

## ⚡ Who You Are
You are the AI agent for **ANI-KUTA** — an Android multi-content streaming/downloading app (anime + movies + series today, manga and novels planned; Kotlin 2.2 + Jetpack Compose 1.10.4 + MPV + SQLDelight 2.0.2 + Koin 4.2.2 + Injekt + a CloudStream plugin system) — plus its companion web dashboard (Next.js → GitHub Pages).

**GitHub repo:** `testplay-byte/ANI-KUTA`. Repo root = the single wrapper folder `ANI-KUTA/` (per CORE_RULES §4) + `.github/` at root (a GitHub platform constraint) + the user's own `README.md` / `NEW_AGENT_SETUP.md` / `USER-UPLOADS/` (established).

---

## 📍 Current State (refreshed Round 115, 2026-10-02)

- **Mainline branch:** `feature/round-57-cloudstream-downloads` — this IS the default branch and the remote's ONLY branch. `main` was DELETED (D-552 — it had 0 unique commits). Local-only release branches (release/1.1.14/69/70/71) exist in the sandbox; the remote carries 139 tags. `feature/test-controller-v5` (dormant, kept by user order).
- **Versions:** the mainline carries **1.1.20 / 10120** (D-430: version bumps ride the RELEASE branch only, never the mainline). Latest **debug** release: **v1.1.71 / 10171** (round 114 — LIVE on testplay-byte). Latest **professional** release: **v1.1.14 / 10114** (round 114 — LIVE on Confused-Creature-180/ANI-KUTA: 11 assets, stable latest, the website + README refreshed; the round-39/76 re-host blocker was cleared by the user's release-agent PAT — assets only, zero code pushed).
- **⚠ THE CURRENT PHASE (D-738, the user's round-115 standing order) — DEBUG BUILDS ONLY, ALL RELEASES SUSPENDED:** every round now ends at **CI green** (the Build APK debug run's `anikuta-apk` artifact — the user installs from the artifact). NO release branches, NO version bumps, NO tags, NO Release APK / release-build-once dispatches, NO re-hosts, NO website bumps — **debug AND professional alike — until the user's explicit order.** The interpretation is banked in D-738 with the recovery path: one explicit order restores either line; the release routines live warm in `APP/ani-kuta/DOCUMENTATION/release/RELEASE-PLAYBOOK.md` (the in-repo playbook rebuilt at round 115 after the repo-external one was lost to a sandbox reset).
- **Latest records:** Round 115 = the documentation & handoff round (doc **97**, D-738 + D-739; the staleness sweep + the in-repo release playbook + HANDOFF-ROUND-115.md + the dashboard status refresh). `DOCUMENTATION/cloudstream-v2/` numbering is sequential — the next record is **98**; the next decision is **D-740**.
- **The per-round loop (D-738 form):** device feedback → implement on the mainline → CI green (Build APK debug run — ≤2 runs/cycle, disclosed ledger, D-472; docs-only pushes build nothing) → **STOP — hand the user the artifact link + the test checklist** → LIVE-mirror docs → ntfy. No release steps.
- **CI paths-ignore (D-472):** docs-only / AGENT-CONTEXT / DASHBOARD / USER-UPLOADS / `.github/**` / `**.md` pushes build NOTHING. `DASHBOARD/webpage/**` pushes DO trigger the Pages deploy.
- **THIS SESSION'S NOTIFICATION:** ntfy topic **TASK808DONE** (the recent rounds' working topic — the §11 default THE-TASK-IS-DONE resumes if the user says so; the topic 429s under load — retry with backoff).

## 📂 If The Environment Was Just Cloned
1. Clone `https://github.com/testplay-byte/ANI-KUTA.git` (public — read needs no token). **PUSH** uses the credential helper that reads the PAT from `/home/z/.secrets/github-credentials` (repo-external, git-credential FORMAT — for raw API calls extract the `password=` line; NEVER commit it, NEVER paste it). If the sandbox lost the file, ask the user.
2. Checkout the mainline `feature/round-57-cloudstream-downloads` (the default branch).
3. Read `AGENT-CONTEXT/memory/progress.md` — the TOP **CURRENT STATUS** block first, then the newest `## Round NN` sections at the BOTTOM (the file grows downward; the middle "Historical session" paragraphs are old).
4. Read `AGENT-CONTEXT/memory/decisions.md` — the newest entries sit at the TOP; latest = **D-739** (round 115).
5. Read `AGENT-CONTEXT/HANDOFF-ROUND-115.md` — the current agent's handoff (written for the NEXT agent; supersedes the older HANDOFF-POSTER-NOTIFICATIONS.md, which is historical).
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

## 🚧 Open Items / Blocked (refreshed Round 115)
- **THE PHASE:** D-738 — debug builds only; ALL releases suspended until the user's explicit order (see Current State above). The user's next device round on **v1.1.71** (debug) / **v1.1.14** (professional) is the next input — the round-114 device checklist lives in doc 96 §5 (the details grid, the player grid + both heights, the stability, the search landings, the v1.1.14 over-the-update install).
- **THE HANDOFF:** `AGENT-CONTEXT/HANDOFF-ROUND-115.md` — the previous agent's full handoff (read it after this file). `HANDOFF-POSTER-NOTIFICATIONS.md` is HISTORICAL (rounds 46-47; resolved by the round-80 notification rework D-566..D-569).
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
│   │   ├── HANDOFF-ROUND-115.md # the round-115 handoff (the current one)
│   │   └── HANDOFF-POSTER-NOTIFICATIONS.md  # HISTORICAL (rounds 46-47)
│   ├── APP/ani-kuta/            # Android app — 57 Gradle modules (1 app + 33 core + 2 data + 21 feature)
│   │   └── DOCUMENTATION/       # technical docs; cloudstream-v2/ = the round records (next: 98); release/ = the build+release guides
│   ├── DASHBOARD/webpage/       # Next.js dashboard (→ GitHub Pages; sub-agents build it)
│   └── REFERENCES/              # old-kuta + animiru + webview-cloudflare-captcha (read-only)
└── .github/workflows/           # CI — build-apk / release-apk / release-build-once / deploy-dashboard
```

---
*This file is the quick-start. For everything else, see `navigation.md`. History: the pre-Round-77 version of this file described the round-29 era (v0.4.17, `streaming/CLOUDSTREAM-V2`) — it was fully rewritten in Round 77 after the drift was caught.*
