# SESSION — Read This At The Start Of Every Session

> A 60-second orientation. Read this FIRST, every time, before any work.
> Refreshed in **Round 91 (2026-09-27)**. If `memory/progress.md` shows a NEWER round than this file's stamps, trust progress.md — and refresh this file at the phase boundary (the Round-77 lesson).

---

## ⚡ Who You Are
You are the AI agent for **ANI-KUTA** — an Android multi-content streaming/downloading app (anime + movies + series today, manga and novels planned; Kotlin 2.2 + Jetpack Compose 1.10.4 + MPV + SQLDelight 2.0.2 + Koin 4.2.2 + Injekt + a CloudStream plugin system) — plus its companion web dashboard (Next.js → GitHub Pages).

**GitHub repo:** `testplay-byte/ANI-KUTA`. Repo root = the single wrapper folder `ANI-KUTA/` (per CORE_RULES §4) + `.github/` at root (a GitHub platform constraint) + the user's own `README.md` / `NEW_AGENT_SETUP.md` / `USER-UPLOADS/` (established).

---

## 📍 Current State (refreshed Round 91, 2026-09-27)

- **Mainline branch:** `feature/round-57-cloudstream-downloads` — this IS the default branch. `main` was DELETED (D-552 — it had 0 unique commits). Other live branches: `release/1.1.3` (the professional release branch), `feature/test-controller-v5` (dormant, kept by user order).
- **Versions:** the mainline carries **1.1.20 / 10120** (D-430: version bumps ride the RELEASE branch only, never the mainline). Latest **debug** release: **v1.1.48 / 10148** (round 91 — D-628 the sheet's five-row wheels + the extension-side card, D-629..D-633 the testing depth pass: the legend's pass/fail depth sections, the home's icon-tinted chips + solid rectangle buttons + confirmation + the split live banner/footer, the list's bare headings + manifest-until-results, the full-details collapse + equal-by-default stage bar + centered timeline + solid scrimmed banner + the settings gear, and the run screen's free back; header blur on all five testing pages) — **LIVE**: `release/1.1.48` cut from the green round-91 ledger head (implementation a41e4b4e + two compile-fix commits 1fde0f98/196cfbc4 — Build APK run 36258963397 GREEN on the third run, over the D-472 budget by one, disclosed in doc 73 §7), Release APK run GREEN (published 2026-09-26, arm64-v8a debug APK + SHA256SUMS.txt) — verified via the API (assets, latest flag, body). The in-app updater (debug checks this repo, D-440) picks it up. The NEXT debug release is **v1.1.49 / 10149**. Latest **professional** release: **v1.1.3 / 10103** (`professional-v1.1.3`, 5 release-signed ABIs + universal — device-verified).
- **Latest records:** Round 91 implemented AND SHIPPED as v1.1.48 (D-628..D-633 — doc **73**); `DOCUMENTATION/cloudstream-v2/` numbering is sequential — the next record is **74**; the next decision is **D-634**.
- **THE CURRENT PHASE (D-565) — DEBUG-FIRST:** the user directed that from now on ALL new features/QoL work lands on the mainline and ships via per-round **DEBUG** releases (the latest: **v1.1.48/10148**; the next is v1.1.49/10149). **Professional releases PAUSE** until the user explicitly orders the next one (D-425 version discipline unchanged — no bumps without the user's order).
- **The per-round loop is unchanged:** device feedback → implement on the mainline → CI green (≤2 runs/cycle, disclosed ledger, D-472) → `release/1.1.3N` cut from the green head → the bump rides that branch → tag `v1.1.3N` → the debug release publishes → LIVE-mirror docs → ntfy → the user's device round.
- **CI paths-ignore (D-472):** docs-only / AGENT-CONTEXT / DASHBOARD / USER-UPLOADS / `.github/**` pushes build NOTHING.

---

## 📂 If The Environment Was Just Cloned
1. Clone `https://github.com/testplay-byte/ANI-KUTA.git` (public — read needs no token). **PUSH** uses the credential helper that reads the PAT from `/home/z/.secrets/github-credentials` (repo-external, git-credential FORMAT — for raw API calls extract the `password=` line; NEVER commit it, NEVER paste it). If the sandbox lost the file, ask the user.
2. Checkout the mainline `feature/round-57-cloudstream-downloads` (the default branch).
3. Read `AGENT-CONTEXT/memory/progress.md` — the TOP **CURRENT STATUS** block first, then the newest `## Round NN` sections at the BOTTOM (the file grows downward; the middle "Historical session" paragraphs are old).
4. Read `AGENT-CONTEXT/memory/decisions.md` — the newest entries sit at the TOP; latest = **D-633** (round 91).
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

## 🚧 Open Items / Blocked (refreshed Round 90)
- **Round 91 SHIPPED v1.1.48 (the wheels + the depth pass):** the user's v1.1.47 verdict split two ways — the Link Sources sheet was "not handled properly" (rebuilt: the 60% cap, five-row snap wheels with centered selection, gray+blur on the rest, accent-band headings, the column highlight, real spacing, and the card now renders the EXTENSION side — extensionBase exposed as a flow, the episode count from the linked source's own episode list, "No source linked yet" for AniList-only entries); the testing section was "very good" and got its polish list (D-629 the legend's pass/fail depth sections; D-630 the home's icon-tinted rectangular chips via the new rememberIconTint, glyphed system cards, the solid muted-accent rounded-rectangle Run-all behind a CONFIRMATION dialog, and the SPLIT live experience — the top banner = extension + stage with a ≥1s verdict-linger queue, the footer = live elapsed + n-of-m + Stop; D-631 the list's bare "Aniyomi"/"CloudStream" headings, no subtitles, the manifest-until-results expansion; D-632 the full-details collapse + the equal-by-default animated stage bar in its own card + the solid "Run Tests" pill + the settings gear (SourcePreferences via MainActivity) + the centered timeline with compact bright never-run sections + smooth expansion + the solid scrimmed verdict banner; D-633 the run screen's leave guard deleted — back is free, the run continues, the prompt lives on the home screen only; header blur on all five testing pages). FUTURE (ordered, NOT yet): the Run-all target picker. The user's device round on v1.1.48 is the next input; the next debug release is **v1.1.49/10149**.
- **The dev PAT** is stored repo-external at `/home/z/.secrets/github-credentials` (0600, account `testplay-byte`, admin, git-credential FORMAT — extract `password=` for API calls). The `official-repo-token` is NOT needed for current work (the user, round 89: debug versions only — the real release-repo token arrives when an official release is actually ordered).
- **The user's uploads folder:** `USER-UPLOADS/` contents may be cleared if cleanup is ever wanted, but the FOLDER itself stays (the user, round 89: "don't remove the folder, only the things in it").
- **release/1.1.38…release/1.1.48 branches stay on the remote** (the tags hold the code; branch deletion is user-gated).
- **The official repo is LIVE (resolved round 79):** Confused-Creature-180/ANI-KUTA carries professional v1.1.3 (11 assets: 5 APKs + 5 ZIPs + SHA256SUMS.txt) + the Pages download site with the APK|ZIP option selector. The process is documented: `ANI-KUTA-RELEASE-PLAYBOOK.md` (repo-external) + the official repo's `RELEASES.md`. The official-repo token lives at `/home/z/.secrets/official-repo-token`.
- **Dashboard deep debt (disclosed, D-565):** the dashboard carries REPRESENTATIVE data — decisions D-277..D-633 are not individually listed, and the per-table DB transcription is the D-192-era snapshot (current truth: 25 tables / 17 .sq files). Status-level facts were refreshed Round 77. A full backfill is available on the user's request. (The user, round 89: leave the dashboard as-is for now — focus comes later.)
- **`feature/test-controller-v5`** stays dormant (kept by explicit user order — do not delete).
- **Version bookkeeping quirk (D-430):** the mainline says 1.1.20/10120 while the shipped debug line is at v1.1.47 — this is CORRECT by doctrine (bumps ride release branches). Don't "fix" it.

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
│   │   └── DOCUMENTATION/cloudstream-v2/  # the round records (next: 73)
│   ├── DASHBOARD/webpage/       # Next.js dashboard (→ GitHub Pages; sub-agents build it)
│   └── REFERENCES/              # old-kuta + animiru (read-only)
└── .github/workflows/           # CI — build-apk / release-apk / release-build-once / deploy-dashboard
```

---
*This file is the quick-start. For everything else, see `navigation.md`. History: the pre-Round-77 version of this file described the round-29 era (v0.4.17, `streaming/CLOUDSTREAM-V2`) — it was fully rewritten in Round 77 after the drift was caught.*
