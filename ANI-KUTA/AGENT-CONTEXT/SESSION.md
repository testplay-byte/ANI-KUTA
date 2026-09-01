# SESSION — Read This At The Start Of Every Session

> A 60-second orientation. Read this FIRST, every time, before any work.
> It reminds you of the key rules and the session loop. For detail, follow the links.

---

## ⚡ Who You Are
You are the AI agent for **ANI-KUTA** (Android app rebuild + companion web dashboard).
GitHub: `testplay-byte/ANI-KUTA`. Repo root contains a single wrapper folder `ANI-KUTA/` (per CORE_RULES §4). Active line: `main` (v0.2.63). Active WORK branch: **`streaming/CLOUDSTREAM-V2`** — Task 60 / round 20: THE v0.4.7 DEVICE-ROUND FIXES (v0.4.8/73): the device round confirmed CS downloads end-to-end, the .WHITECAT share round-trip, the .bin import and the 1.5s added flow, and flagged nine things (doc cloudstream-v2/11) — (A) THE SUBTITLE LINE-GAP ROOT CAUSE (the overlay's Text set fontSize but NOT lineHeight, so Material3's ambient bodyLarge leaked a FIXED 24sp line box in: huge gap at 0.5x scale, overlapping glyphs at 2x+; now an EXPLICIT font-proportional lineHeight 1.2x via CsSubtitleGeometry.LINE_HEIGHT_RATIO + a BARE lineHeightStyle — constant ~20% gap at every scale), (B) BOLD DEFAULT ON (PlayerPreferences.boldSubtitles true — one pref drives MPV sub-bold + the CS overlay), (C) RESET CONFIRMATION (both sheets' Reset opens an AlertDialog first), (D) THE FORMATTING MENU ON THE HEADING (all four sheets — the round-19 pill deleted; tapping the TITLE TEXT ONLY pops a small flat menu with a DISTINCT 1dp outline border + a 'Formatted sources' Switch; the menu stays open while toggling), (E) STRICT .WHITECAT (the legacy .moviebox.WHITECAT double tail is an explicit REJECTION marker checked FIRST — it also ends with .WHITECAT, the offline test caught the shadowing again; the import CONFIRM page now shows the EMBEDDED icon: bytes → Image, iconUrl fallback, generic glyph last), (F) THE CLOUDSTREAM-TAB LANDING (ExtensionsSettingsKey object→data class with initialTab; the post-Add push sets 'cloudstream'; rememberSaveable(initialTab) seeds the tab), (G) SERVER-NAME ELLIPSIS on the download rows (both stacks, queue + downloaded — the server pill flexes with a trailing '…', the percentage pill sits outside the weighted row, always visible), (H) THE DOWNLOADED-PAGE CRASH (duplicate LazyColumn keys — the denormalized per-row content metadata split one anime into two same-contentId groups; group by the STABLE contentId now). Verification: 12/12 + 13/13 pure tests GREEN offline (kotlinc 2.2.0 + serialization plugin); full-diff static review caught 2 pre-push compile blockers (lineHeight needs .sp — TextUnit is a value class; the when-subject is currentKey) — both fixed; aniyomi stack additive/display-layer only. NEXT: user device round on v0.4.8. Historical round-19 state: THE v0.4.6 DEVICE-ROUND FIXES (v0.4.7/72) (v0.4.7/72): the device round confirmed the both-stacks debug toolkit (perfect), the paused live styling and the share/import round-trip, and flagged five things (doc cloudstream-v2/10) — (A) THE CS DOWNLOADS DEAD-CALLBACK FIX (round 18 wired a DEAD EpisodesSection param; the LIVE row-level download lambda kept the classic resolver, so CS-bridged downloads hit the CS-guard error — now gated identically: routeToCsDownload → the CS resolve sheet in DOWNLOAD mode), (B) THE SUBTITLE OVERLAY REWRITE (ONE multi-line Text; every decoration pass — per-line back-color rects, the shadow stroke, the border stroke — drawn via drawText/drawRect from the SAME TextLayoutResult in a drawBehind UNDER the fill; passes cannot detach, line spacing is the platform's natural one at every scale, 4% horizontal wrap inset), (C) THE NEW DEFAULTS + RESET (font MAX 100 / scale 0.5x / border 5 — the user's spec, MPV parity preserved since 100×0.5 ≈ the old 55 — + a Reset button on BOTH subtitle settings sheets through PlayerPreferences.resetSubtitleSettings; the aniyomi sheet additive-only), (D) THE FORMATTING TOGGLE REDESIGN (a distinct BORDED pill at the top of ALL FOUR resolve sheets, above the episode title; the title is plain text now), (E) THE .WHITECAT SHARE FORMAT V2 (extension just .WHITECAT, legacy .moviebox.WHITECAT still imports; the export REWRITES the zip with anikuta/export.json + anikuta/icon.png — the receiver keeps the icon as a LOCAL file + the source repository URL even repo-less; content-first import gate so .bin/renamed files analyze by their zip manifest; the confirm dialog titled with the plugin name; Add → a 1.5s 'Plugin added' → MainActivity → the EXTENSIONS page via the pending-nav note). Historical round-18 state: THE BOTH-STACKS-DEBUG + DOWNLOADS + SHARE RELEASE (v0.4.6/71): the v0.4.5 device round confirmed the round-17 work (sub/dub progress linking + COMBINED tags praised; CS subtitle settings + rendering while playing OK) and directed four things (doc cloudstream-v2/09) — (A) the debug toolkit now covers BOTH extension stacks (the aniyomi ResolverSheet + QualitySheet gain the gated copy-report/row-copy/raw-URL affordances via the NEW pure core:video-resolver ResolverDebugReport; the same DebugPreferences flags, default OFF), (B) the subtitle live-view + ASS-accuracy round (hoisted liveSubtitleStyle state — settings apply while PAUSED, root-caused to the non-reactive prefs read + the ticker's StateFlow equality-dedup; MPV-unit-parity LINEAR border math; per-line ASS BorderStyle=3 boxes hugging glyph bounds padded by the border width; shadow IN ADDITION to border; no maxLines truncation; fontScale scales the Media3 view too — CsSubtitleGeometry), (C) THE CLOUDSTREAM DOWNLOADS PORT (the details download button opens the CS resolve sheet in DOWNLOAD mode; a pick enqueues through the SAME source-agnostic engine via app/download CsDownloadRequestBuilder — queue/service/SAF/notifications/downloads screen/state chips + MPV offline playback ride mainId|episodeKey with ZERO engine changes; DASH filtered+counted), (D) the .moviebox.WHITECAT plugin share/import (Share on every plugin detail state → FileProvider ACTION_SEND; exported PluginImportActivity with MIME-based filters + ONE confirm dialog; importSharedPlugin lands UNTRUSTED with repo LINKAGE when the catalog matches; PendingCsPluginNav hand-off at cold-start + ON_RESUME). 24/24 new pure tests GREEN offline with the real compiler chain (kotlinc 2.2.0 + real kotlinx-serialization plugin); error-histogram count parity vs HEAD on all modified files. Historical NEXT (done): user device round on v0.4.6 → round 19 above. Historical round-17 state: THE LINKED-PROGRESS + OVERLAY-SUBS RELEASE (v0.4.5/70): the v0.4.4 device round confirmed the round-16 fixes (numbering, merged rows, no auto-open); that round fixed the six findings (doc cloudstream-v2/08) — P1 sub/dub share ONE progress identity (the flavor ORDINAL: sub-5 at 80% shows 80% on dub-5; ratings + mark-series + tracker sync ride the ordinal keys, CS-bridged gated), P2 COMBINED rows show SUB·DUB variant pills again (details audio pill + watch page + episodes sheet), P3 the COMBINED dual-resolve dedup keys on (url+audio) so a shared encode URL no longer erases a flavor's section, P4 the debug toolkit (Settings → Debug options page, all default-OFF: bubble / resolve-list sources / copy button; resolve lists gain per-row copy icons + a header report action when ON), P5 smarter server/audio/resolution parsing (bracket vocabulary + wider CsAudioTag), P6 the OVERLAY subtitle system (provider subs fetch→parse→render through OUR Compose overlay — NO more reloads/crashes/zero-subs; subs sheet 0.55 height; embedded picks crash-guarded with revert+retry; settings sheet at aniyomi structural parity with keypads), P7/P8 player hardening (30 s retained back-buffer — the backward-seek fix — live-state seeks, safe clamps, 100 ms ticker). 70/70 pure tests GREEN offline. NEXT: user device round on v0.4.5; downloads + polish deferred to the next session. Previous round-16 state: the v0.4.3 device round confirmed the formatting toggle + old-system behavior; this round fixed the five findings (doc cloudstream-v2/07) — F1 NO auto-open from the resolve sheet (remembered-server + single-link auto-selects removed), F2 quality chips highest-leftmost (Unknown/Auto rightmost, BOTH stacks — the aniyomi accordion had no sort), F3 sub/dub per-flavor ORDINALS (Dub restarts at EP 1; the global-number uniqueness contract stays — display-layer ordinals only) + tag-stripped names, F4 COMBINED mode really merges (ordinal pairing; a tap resolves BOTH flavors), F5 the LazyColumn duplicate-key crash (index-suffixed keys). Auto-advance stays within the current flavor. Aniyomi-side changes display-layer-only (chip sort + raw keys). Logic machine-verified + reviewer-compiled (Kotlin 2.2.0, tests GREEN); 3 Int?:Float elvis blockers fixed pre-push. NEXT: user device round on v0.4.4. Historical round-15 state: CS playback + the watch page are CONFIRMED WORKING (v0.4.2 device round); this round fixed what the user flagged NEXT: the resolve sheet's noise lines are GONE and it now renders the aniyomi 3-tier (Server → AudioVersion → Quality with SUB/DUB chips), a source-FORMATTING on/off toggle (small popup ABOVE the "Episode N" title, shared pref, BOTH stacks, raw flat list when OFF), the subtitle pipeline (language names instead of URLs, live-index track selection, content-sniffed mimes, preferred-language auto-select, a full CS SubtitleSettingsSheet styling the Media3 view), and sub/dub episode display modes (Separate = chip switcher / Combined = merged rows resolving BOTH flavors — set in Episode list settings → Display). Per-episode metadata (title/thumb/date/desc/sub-dub) rides the SAME serialized wire format as the aniyomi WatchKey field — ONE DetailsScreen builder feeds both stacks. Aniyomi stack byte-untouched (parity via REPLICATED design tokens, zero code imports). The v0.4.3 push initially FAILED CI (two local-scoped declaration bugs — see D-380); the completion round fixed them + a statically-caught BLOCKER in the aniyomi ResolverSheet raw branch and gated the sub/dub display on CS-bridged sources. NEXT: user device round on v0.4.3 (the round-15 feature set: formatted/raw resolve sheet, subtitle names + customization, sub/dub display modes; aniyomi regression sweep again). Doc zone: DOCUMENTATION/cloudstream-v2/ (02 plan + 03 as-built + 04 fixes + 05 UI-parity plan). Debugging: the ONE logcat filter in doc 03 §3.

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
