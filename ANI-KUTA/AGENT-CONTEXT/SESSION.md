# SESSION — Read This At The Start Of Every Session

> A 60-second orientation. Read this FIRST, every time, before any work.
> It reminds you of the key rules and the session loop. For detail, follow the links.

---

## ⚡ Who You Are
You are the AI agent for **ANI-KUTA** (Android app rebuild + companion web dashboard).
GitHub: `testplay-byte/ANI-KUTA`. Repo root contains a single wrapper folder `ANI-KUTA/` (per CORE_RULES §4). Active line: `main` (v0.2.63, the trunk). Active WORK branch: **`streaming/CLOUDSTREAM`** — Tasks 40-47 (research + clean-room compat + runtime + unified UI + six device-feedback rounds + Task 47's playback session: trust flow, provider EXECUTION, the source BRIDGE, the 8KB-truncation + search-memory root causes, the 35-extractor runtime, the P.A.C.K.E.R. unpacker + M3u8Helper) + Task 48 (round 7 / D-351..D-356: the newEpisode toJson-quoting loadLinks root cause; instant search cache; the 403 recovery ladder; per-track subtitle headers; CS downloads with rotating-link self-heal; playback haptics + header year; v0.2.69) + **Task 48.1 (round 8 / D-357..D-360: THE CRASH — CloudflareBlockedException escaped OkHttp's AsyncCall (Exception, not IOException) and killed the process from the dispatcher thread — fixed with IOException parentage + a terminal CsInterceptorSafetyNet interceptor + headless-solver fast-fail; THE 428 — two comma-truncation bugs mangled every User-Agent (the proxy's naive csv split + mpv's own list-option splitting; verified against mpv m_option.c that ONLY backslash escapes work) — fixed with the canonical MpvHeaderFields (gluing parse + boundary escaping) and 428/429 in the recovery ladder; the watch-page episode-metadata UNION fix (player-page description/thumbnails/synopses for CS content); plugin-loader self-heal after crash-restart; the SAF "w"-doesn't-truncate .data.json corruption fix ("rwt" + per-folder mutex); v0.2.70)** + **Task 49 (round 9 / D-361..D-363: THE DEAD-DISPATCH BUG — loadExtractor normalized the embed URL but NOT the extractor mainUrl, so dispatch never matched and 53/80 census plugins silently resolved 0 links since v0.2.68 — both sides normalized + no-match WARN + CF-rethrow + dispatch tests; error visibility — VideoResolver's blanket catch ate every real reason into "No videos available", now captured+rethrown so the sheet shows the actual cause; THE CONSOLE LOGGING TOOL — release-available Settings → Developer tools → Console logs over RingLogBuffer in ALL builds + plugin-facade sink + export w/ logcat dump via share sheet; HLS quality selection — bridge expands unlabeled m3u8 masters into per-quality variants (pure parser + tests, fail-open, bounded); DASH surfacing — MpdParser (XXE-hardened) turns static single-file .mpd into playable links (separate audio → mpv audio-add), dynamic/multi-segment hidden but logged; CF hardening — ≥2 markers for 200s, solver loads the challenged path, WebView attaches to the live activity; v0.2.71)** — pushed, CI pending. NEXT: user device round 10 on v0.2.71 — verify (a) any extension's episode → servers/qualities listed or an HONEST reason in the sheet, (b) console logs → reproduce → Export & Share → send the report (THE new debugging loop), (c) MovieBox .mpd surfacing or "mpd: hidden …" lines, (d) aniyomi extensions unchanged → then extractor hardening driven by console data.

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
