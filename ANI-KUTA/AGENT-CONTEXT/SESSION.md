# SESSION — Read This At The Start Of Every Session

> A 60-second orientation. Read this FIRST, every time, before any work.
> It reminds you of the key rules and the session loop. For detail, follow the links.

---

## ⚡ Who You Are
You are the AI agent for **ANI-KUTA** (Android app rebuild + companion web dashboard).
GitHub: `testplay-byte/ANI-KUTA`. Repo root contains a single wrapper folder `ANI-KUTA/` (per CORE_RULES §4). Active line: `main` (v0.2.63). Active WORK branch: **`streaming/CLOUDSTREAM-V2`** — Task 56 / round 16: THE DEVICE-FEEDBACK-FIXES RELEASE (v0.4.4/69): the v0.4.3 device round confirmed the formatting toggle + old-system behavior; this round fixed the five findings (doc cloudstream-v2/07) — F1 NO auto-open from the resolve sheet (remembered-server + single-link auto-selects removed), F2 quality chips highest-leftmost (Unknown/Auto rightmost, BOTH stacks — the aniyomi accordion had no sort), F3 sub/dub per-flavor ORDINALS (Dub restarts at EP 1; the global-number uniqueness contract stays — display-layer ordinals only) + tag-stripped names, F4 COMBINED mode really merges (ordinal pairing; a tap resolves BOTH flavors), F5 the LazyColumn duplicate-key crash (index-suffixed keys). Auto-advance stays within the current flavor. Aniyomi-side changes display-layer-only (chip sort + raw keys). Logic machine-verified + reviewer-compiled (Kotlin 2.2.0, tests GREEN); 3 Int?:Float elvis blockers fixed pre-push. NEXT: user device round on v0.4.4. Historical round-15 state: CS playback + the watch page are CONFIRMED WORKING (v0.4.2 device round); this round fixed what the user flagged NEXT: the resolve sheet's noise lines are GONE and it now renders the aniyomi 3-tier (Server → AudioVersion → Quality with SUB/DUB chips), a source-FORMATTING on/off toggle (small popup ABOVE the "Episode N" title, shared pref, BOTH stacks, raw flat list when OFF), the subtitle pipeline (language names instead of URLs, live-index track selection, content-sniffed mimes, preferred-language auto-select, a full CS SubtitleSettingsSheet styling the Media3 view), and sub/dub episode display modes (Separate = chip switcher / Combined = merged rows resolving BOTH flavors — set in Episode list settings → Display). Per-episode metadata (title/thumb/date/desc/sub-dub) rides the SAME serialized wire format as the aniyomi WatchKey field — ONE DetailsScreen builder feeds both stacks. Aniyomi stack byte-untouched (parity via REPLICATED design tokens, zero code imports). The v0.4.3 push initially FAILED CI (two local-scoped declaration bugs — see D-380); the completion round fixed them + a statically-caught BLOCKER in the aniyomi ResolverSheet raw branch and gated the sub/dub display on CS-bridged sources. NEXT: user device round on v0.4.3 (the round-15 feature set: formatted/raw resolve sheet, subtitle names + customization, sub/dub display modes; aniyomi regression sweep again). Doc zone: DOCUMENTATION/cloudstream-v2/ (02 plan + 03 as-built + 04 fixes + 05 UI-parity plan). Debugging: the ONE logcat filter in doc 03 §3.

## 📂 If The Environment Was Just Cloned
1. Clone to `/home/z/ANI-KUTA-WORK/ANI-KUTA` (the repo is public — read access works without a token; PUSH needs the GitHub token in the remote URL — **ask the user for repo URL + token AT THE START of the session**, they may have been lost in a sandbox wipe). Checkout `main` (or `streaming/CLOUDSTREAM` if the user directs the new work there).
2. Read `AGENT-CONTEXT/memory/progress.md` → know what's done, what's next, blockers + **Deferred Concerns** (read the top sections first).
3. Read `AGENT-CONTEXT/memory/decisions.md` → latest = D-329 (v0.2.63 merged to main).
4. Read `AGENT-CONTEXT/memory/lessons-learned.md` → grep for tags matching your task.

## 🔑 Key Rules (full detail in `CORE_RULES.md` — 30 sections)
- **No assumptions.** Unsure → ask the user. Never guess.
- **Don't sugarcoat.** If a request has an issue, flag it directly. Don't blindly agree.
- **User uses speech-to-text.** If a request feels off, correct obvious errors from context; if still unclear → stop and ask.
- **APK builds: GitHub Actions only.** ABIs: `arm64-v8a` ONLY in shipped APKs (D-251; test-only x86_64 emulator builds via `-PemulatorX64Build=true`, never shipped). Never local. Never install Android SDK/JDK locally (CORE_RULES §8).
- **Debug builds = schema freedom** (CORE_RULES §30). No migration scripts needed. Old DBs get deleted + recreated. Don't worry about preserving existing dev data.
- **Sub-agents for webpage work** → they work ONLY in `DASHBOARD/webpage/`, never `AGENT-CONTEXT/` (CORE_RULES §14, §19).
- **Keep it simple.** Stdlib/native before new deps. No over-engineering. (See `skills/ponytail.md`.)
- **Be honest.** Short, simple, to the point. Use emojis + formatting for clarity.
- **Quality over speed.** Take as much time as needed. Don't rush. Don't skip steps (CORE_RULES §18).

## 🔄 The Task Loop (full detail in `workflow.md`)
```
REFLECT → RESEARCH → PLAN → TODO LIST → EXECUTE → COMMIT → VERIFY (CI) → DOC UPDATE → NOTIFY
```
1. **Reflect** — summarize what you understood before executing.
2. **Research** — read the code/docs the task touches. Use Explore sub-agents.
3. **Plan** — split into phases. Identify dependencies.
4. **Todo list** — build a comprehensive todo list AFTER research (not random at start).
5. **Execute** — build one phase at a time. Frontend first, then backend.
6. **Commit** — each phase as a separate commit.
7. **Verify (CI)** — push, poll GitHub Actions API, read build results/failures, fix, repeat until green. **Do NOT dispatch sub-agents to pre-review for compile errors (D-281) — CI is the compiler of record.**
8. **Doc update** — progress.md, decisions.md, changelog.md, lessons-learned.md in the SAME session.
9. **Notify** — ntfy.sh after each phase + at the end.

## 📝 After Every Task (Update These)
- `memory/progress.md` — live status.
- `memory/decisions.md` — if a decision was made.
- `memory/lessons-learned.md` — if you made/corrected a mistake.
- `memory/changelog.md` — if a phase advanced.
- Relevant `knowledge/` files — if project knowledge changed.
- Dashboard data (`DASHBOARD/webpage/lib/`) — delegate to full-stack-dev sub-agent (§19).

## 🚨 Session-End Checklist (NON-NEGOTIABLE)
- [ ] All work committed (`git add -A && git commit`).
- [ ] Pushed to GitHub (`git push`). **The environment can clear randomly — unpushed work is lost.**
- [ ] `git status` is clean.
- [ ] CI is green (verified via API, not assumed — lesson D-156).
- [ ] ntfy.sh notification sent (topic `TASKISDONE`).
- [ ] Short formatted summary given to the user.

## 🧪 Testing on the Emulator
The sandbox can run the app on an Android emulator (user-authorized, CORE_RULES §8
exceptions) — install/launch/AniList/extensions/trust/search all verified E2E.
**Before ANY emulator/adb work, read `knowledge/emulator-testing.md`** — it has the
sandbox rules that will otherwise cost hours (double-fork detach, timeout-wrapped
adb, input-text limits, the 4GB memory ceiling) + full setup + workflow + tricks.

## 📦 Project Folders
```
ANI-KUTA/                        ← repo root (git)
├── ANI-KUTA/                    ← wrapper folder (all zones inside)
│   ├── AGENT-CONTEXT/           # YOUR memory + rules (you maintain this)
│   ├── APP/ani-kuta/            # Android app (50 Gradle modules: 1 app + 30 core + 1 data + 18 feature)
│   ├── DASHBOARD/webpage/       # Next.js dashboard (14 pages → GitHub Pages; sub-agents build this)
│   └── REFERENCES/              # old-kuta + animiru (read-only)
└── .github/workflows/          # CI
```

## ❓ Currently Blocked On / Open Items
- **Nothing blocked** — v0.2.63 shipped 2026-08-29 and `test-feature/video-cache-new-download` was MERGED into `main` (user-gated gate opened; --no-ff merge; tag v0.2.63 + release built FROM the merge commit; APK verified ~59.4MB arm64-v8a). `streaming/CLOUDSTREAM` branch created from the new main — AWAITING the user's instructions for its purpose (explicitly no work started on it).
- **Ongoing device-feedback loop:** the user tests every release on a real OnePlus device and reports back; each session = fix/polish batch + version bump + release. v0.2.63 (D-327 calmer 600ms cover flight + D-328 Library⇄Search ghost-morph fix) awaiting device feedback; v0.2.62 verified working + satisfactory on device.
- **Branch hygiene note:** the merge commit on main also carried the 2 user web-UI "Add files via upload" commits (moviebox v16.1139 APK + an empty commit) — no conflicts (disjoint paths). The old feature branch still exists remotely (kept for history).
- See `memory/progress.md` → "Deferred Concerns" + "What's Next" for the full list (older items like library-badge testing, download-system device testing, Nav3, doc-debt are all resolved/historical — see decisions.md).

---
*This file is the quick-start. For everything else, see `navigation.md`.*
