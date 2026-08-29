# SESSION — Read This At The Start Of Every Session

> A 60-second orientation. Read this FIRST, every time, before any work.
> It reminds you of the key rules and the session loop. For detail, follow the links.

---

## ⚡ Who You Are
You are the AI agent for **ANI-KUTA** (Android app rebuild + companion web dashboard).
GitHub: `testplay-byte/ANI-KUTA`. Repo root contains a single wrapper folder `ANI-KUTA/` (per CORE_RULES §4). Active line: `main` (v0.2.63, the trunk). Active WORK branch: **`streaming/CLOUDSTREAM`** — Tasks 40-46 (research + clean-room compat + runtime + unified UI + six device-feedback rounds: trust flow, parallel installs, plugin detail page, provider EXECUTION — browse/search/load, the 8KB-truncation root cause, the source BRIDGE — CS results open the STANDARD details screen, Cloudflare bypass + manual-solve loop, activity-gated plugin loading, picker state machine, details enrichment, trust-UX parity) + **Task 47 (round 6 + PLAYBACK / D-348..D-350: search-page memory repaired for real — the two-layer WhileSubscribed chain's empty INITIAL value was resetting the persisted kind on every cold start; `awaitCsSource` awaits a NON-EMPTY raw provider list before any "gone" verdict + the top AniList/Extension tab is persisted; the search-time YEAR threads to the details page (seeded stub SAnime; load().year wins) + year/score persist in the ext-extras JSON; THE PLAYBACK SESSION — the bridge's getVideoList runs provider.loadLinks with CS3 streaming semantics → Video (partial results survive mid-call failures, URL dedup, DRM/DASH/torrent filtering, referer/UA folding, subs + audio), the REAL extractor runtime (35 built-ins: shared jwplayer packed-JS engine for the StreamWish/VidStack/Filesim/VidHide/Filemoon/Vidmoly families + dedicated Dood pass_md5 / StreamTape robotlink / MixDrop wurl / StreamSB sources-API / Voe / Dailymotion / PixelDrain / Ok.ru / Streamlare + 19 census variant classes, registered at manager init so plugin mirrors win dispatch), the REAL P.A.C.K.E.R. unpacker (unit-tested) and the REAL M3u8Helper (master→variant fan-out); per-source video-list timeouts (CS 5s–8min); getHosterList fast-fallback; v0.2.68)** — pushed 01c3be5c, CI pending. NEXT: user device round 7 on v0.2.68 (pick a CS episode → resolver sheet lists hoster servers/qualities → MPV plays; episode switch re-resolves; continue-watching applies) → then CS DOWNLOADS + the 403 re-resolve ladder (doc 19 §6–§7).

## 📂 If The Environment Was Just Cloned
1. Clone to `/home/z/ANI-KUTA-WORK/ANI-KUTA` (the repo is public — read access works without a token; PUSH needs the GitHub token in the remote URL — **ask the user for repo URL + token AT THE START of the session**, they may have been lost in a sandbox wipe). Checkout `streaming/CLOUDSTREAM` (the active work branch) or `main` as directed.
2. Read `AGENT-CONTEXT/memory/progress.md` → know what's done, what's next, blockers + **Deferred Concerns** (read the top sections first).
3. Read `AGENT-CONTEXT/memory/decisions.md` → latest = D-330 (Task 40 CloudStream research program complete; implementation gated on G1-G17).
4. Read `AGENT-CONTEXT/memory/lessons-learned.md` → grep for tags matching your task.
5. **For any CloudStream work**: start at `APP/ani-kuta/DOCUMENTATION/cloudstream/README.md` (master index) — reading order: 00 → 21 (gates) → 20 (roadmap) → 16-19 (plans) → research docs 01-15 as needed. The research workspace clones live OUTSIDE the repo at `/home/z/ANI-KUTA-WORK/research/` (re-clonable if wiped; sources listed in doc 00 §2).

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
- **CloudStream phase 1 (extension MANAGEMENT) is DONE + CI green — awaiting user device feedback** on: repo add (auto-detect), plugin install/load (provider count visible), enable/disable, uninstall, NSFW gate, repo delete cascade. Test checklist in the Task 41 session summary.
- **CloudStream phase 2+ (provider EXECUTION) not started** — mainPage/search/load/loadLinks, the 16 built-in extractor scrapers (currently load-time skeletons that throw when invoked), content nav (G3 proper), data-layer integration (doc 17), playback (doc 19). Scope/order per doc 23 §7 + the user's direction.
- **Ongoing device-feedback loop:** the user tests every release on a real OnePlus device and reports back; each session = fix/polish batch + version bump + release. v0.2.63 (D-327 calmer 600ms cover flight + D-328 Library⇄Search ghost-morph fix) awaiting device feedback; v0.2.62 verified working + satisfactory on device.
- **Branch hygiene note:** `main` = v0.2.63 trunk (merge carried the 2 user web-UI uploads — preserved); `streaming/CLOUDSTREAM` = active work branch (5 docs-only commits ahead of main, tip 4f528eb); the old `test-feature/video-cache-new-download` branch still exists remotely (history). CI does NOT trigger for `streaming/**` pushes (workflow branch pattern: main/feature/functionality/test-feature) — docs-only pushes are build-free by design; revisit the pattern when implementation code lands on this branch.
- **Known doc-debt flagged by research (not urgent):** the dashboard's `lib/schema.ts` is stale vs the real SQLDelight schema (26 listed vs 24 real — pre-D-198 drift; doc 15 §6) and root `DATABASE.json` is a manual pre-D-198 device export. Both are Phase-5 roadmap items (doc 20 §7).
- See `memory/progress.md` → "Deferred Concerns" + "What's Next" for the full list.

---
*This file is the quick-start. For everything else, see `navigation.md`.*
