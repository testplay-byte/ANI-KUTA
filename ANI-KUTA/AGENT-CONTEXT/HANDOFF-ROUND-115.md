# HANDOFF — Round 115 (2026-10-02): the documentation & handoff round

> Written by the round-115 agent **for the NEXT agent taking over this project** (the user's explicit instruction: "a new agent is going to be working on it. You are not going to work on it anymore, most probably").
> Read this AFTER `SESSION.md` + `master.md` + `CORE_RULES.md`, BEFORE any work. It supersedes `HANDOFF-POSTER-NOTIFICATIONS.md` (historical).
> Everything here is verified against the repo as of the round-115 head. If reality disagrees with this file, trust reality + fix the docs.

---

## 1. Where the project stands

- **The app:** ANI-KUTA — Android anime streaming/downloading (Kotlin 2.2.0, Compose 1.10.4-line pins, MPV + a Media3/ExoPlayer CS stack, SQLDelight 2.0.2, Koin 4.2.2). 57 Gradle modules, 634 .kt files, 25 tables / 17 .sq. All original build phases done; ~115 rounds of device-feedback polish on top.
- **The repos:** dev/mainline = `testplay-byte/ANI-KUTA` (branch `feature/round-57-cloudstream-downloads` — the default AND the remote's only branch; `main` was deleted D-552). Official/release repo = `Confused-Creature-180/ANI-KUTA` (professional releases + the GitHub Pages website).
- **Latest LIVE releases** (both round 114): **debug v1.1.71/10171** (testplay-byte) + **professional v1.1.14/10114** (Confused-Creature-180 — 11 assets: 5 release-signed APKs + 5 ZIPs + SHA256SUMS; the website chip + the official README links point at it).
- **Round 114's work (the current device-test surface):** D-737 — the Elements' honest grid (the RowScope weight rides the Row's DIRECT child via the anchor wrapper; the D-729 column flip retired; fixed 2 per row; 44dp segments). The device checklist lives in doc 96 §5. The user has NOT yet given the v1.1.71/v1.1.14 device round.

## 2. ⚠ THE STANDING ORDER (D-738) — read this twice

**The user's round-115 words:** "now we are going to do the debug builds from now on. Just keep that in mind, and you are not going to do any of the release versions until I tell you."

**What it means in practice:**
- Every round ends at **CI green** — the `Build APK` workflow's debug run and its `anikuta-apk` artifact (arm64-v8a debug APK). That artifact IS the deliverable; hand the user the run link.
- **NO releases of any kind — debug OR professional** — until the user explicitly orders one. No release branches, no version bumps (D-425/D-430 unchanged), no tags, no `Release APK` runs, no `release-build-once` dispatches, no official re-host, no website/README bumps.
- The interpretation is deliberately conservative ("not going to do ANY of the release versions" is emphatic) and is banked in D-738 with the disclosed alternative reading (only the professional line paused) + the recovery path: one explicit order from the user restores either line. If the user ever says "just do a debug release like before" — that's the old loop, documented in `APP/ani-kuta/DOCUMENTATION/release/RELEASE-PLAYBOOK.md`.
- **Practical wrinkle to know:** the mainline artifact carries versionCode 10120 (bumps ride release branches, D-430), so it will NOT install over the v1.1.71 debug release (10171) without `adb install -d` or an uninstall. When the user wants a fresh on-device build, either they order a debug release (the old loop) or install the artifact manually. Don't "fix" the mainline version — that's the doctrine.

## 3. How a round works now (the D-738 loop)

```
the user's device feedback (or a direct order)
  → RESEARCH (read the touched code + the round records; the Explore sub-agent pattern works well)
  → PLAN + TodoWrite
  → IMPLEMENT on the mainline (feature/round-57-cloudstream-downloads)
  → SUB-AGENT VERIFICATION (compile-risk audit + semantic audit — the standing order since ~round 108;
     READ-ONLY auditors, they report defects, the main agent fixes)
  → COMMIT (Conventional Commits, the why in the body) → PUSH
  → CI: Build APK debug run → poll the GitHub Actions API → read failures → fix → repeat (≤2 runs/cycle, D-472)
  → STOP. Hand the user: the artifact link + the test checklist + a short summary
  → DOC UPDATE (same session, §26): doc NN in DOCUMENTATION/cloudstream-v2/ (next: 98) + memory files + SESSION stamps
  → ntfy (topic per SESSION.md's current note; 429s happen — retry)
```

- Docs-only pushes (AGENT-CONTEXT/**, DASHBOARD/**, USER-UPLOADS/**, **.md, .github/**) build NOTHING (D-472 paths-ignore). Code pushes build the debug APK (~5-8 min).
- `DASHBOARD/webpage/**` pushes DO trigger the Pages deploy (~10 min).
- The version NEVER moves without the user's explicit order (D-425). Mainline stays 1.1.20/10120.

## 4. The environment (the sandbox facts that bite)

- **CI is the ONLY build machine** (CORE_RULES §8, D-281): never build locally, never install Android SDK/JDK/Gradle, never run Gradle. The pre-push verification loop = read-only sub-agent audits; the compile gate = the Build APK run.
- **Push auth:** the dev PAT lives at `/home/z/.secrets/github-credentials` (git-credential format; for API calls extract the `password=` line). git credential-store does NOT work in fresh sandboxes — the remote URL carries the embedded PAT in `.git/config`. If the file/remote is lost, ask the user.
- **The official-repo (release) PAT lives WITH THE USER.** Round 114's re-host used a PAT the user pasted in-session. `/home/z/.secrets/official-repo-token` no longer exists. Re-ask when a professional release is ordered.
- **Sandbox resets happen** (rounds 80, 108-110 lost work/files). Push early, push often. The round-80 lesson: the release playbook was kept "repo-external" and was LOST — that's why it now lives IN the repo (`DOCUMENTATION/release/RELEASE-PLAYBOOK.md`, rebuilt round 115).
- **This clone's quirks (verify after any re-clone):** local tags may be incomplete (this sandbox showed 3 of the remote's 139 — `git fetch --tags` fixes it); local-only release branches (release/1.1.14/69/70/71) exist; `bun`/node tooling works for the dashboard (the dashboard has its own package.json — `bun install && bunx tsc --noEmit` verifies data edits).
- **ntfy** topics 429 under load (rounds 111/113/114/115) — retry with backoff, keep messages short.
- The dev.log at `/home/z/my-project/dev.log` + the worklog at `/home/z/my-project/worklog.md` belong to the Z.ai sandbox shell, not this repo — the worklog is the shared sub-agent execution log (append-only; every sub-agent must read the tail first + append its section after).

## 5. The project's own rules that matter most (full set in CORE_RULES.md — 31 sections)

1. **No assumptions** — unsure → ask the user. **Don't sugarcoat** — flag issues directly.
2. **The user uses speech-to-text** — correct obvious transcription slips from context; stop and ask if unclear.
3. **CI-only builds** (§8) + **push path = debug arm64-v8a only** (D-445) + **shipped releases = all-ABI, release-signed in CI** (D-423) — but under D-738 nothing ships without an explicit order.
4. **No R8/minification on the release line** (D-436 — it broke the extension system on device).
5. **The updater follows the build type** (D-440): debug → testplay-byte, release → Confused-Creature-180.
6. **No "sponsor"/"sponsored" words anywhere** (the user's standing order).
7. **Debug builds = schema freedom** (§30): no migrations needed; stale dev DBs get wiped.
8. **Sub-agents:** read-only research/audit = fine (and expected for verification); webpage work = full-stack-dev in `DASHBOARD/webpage/` ONLY; `AGENT-CONTEXT/` = main agent ONLY (§14/§19).
9. **Docs update in the same session** (§6/§26) — progress.md, decisions.md, changelog.md, lessons-learned.md + the round record. The numbering: next round record = **doc 98** (DOCUMENTATION/cloudstream-v2/), next decision = **D-740**.
10. **Quality over speed** (§18). Frozen surfaces stay byte-identical unless ordered.
11. **The debug line is NON-DEBUGGABLE** (D-674): no `adb run-as`, no debugger attach — by design. The dev-repo gate / DEBUG logging / Developer-tools are keyed on IS_DEBUG_LINE + the `.debug` suffix, NOT BuildConfig.DEBUG.

## 6. What's open right now (as of the round-115 close)

- **The user's v1.1.71 / v1.1.14 device round** — the next input. Checklist: doc 96 §5 (the Elements grids on both pages, the 44dp heights, tap stability, the search landings, the v1.1.14 over-the-update install from v1.1.3).
- **Standing code-level items** (doc 96 §6 + earlier NOT-APPLIED sets — unchanged by round 115): the "Hard Sub" spaced-label edge; the token-matching semantics living in five places (consolidation candidate); the badge dims with its watched row; the InFlight tap no-op during resolve; ExtensionInstaller's two OS-fallback toasts; the AppToast id-counter race; the dead AutoLinkPopup.kt cleanup; a future Coil 3.1.x upgrade must add `respectCacheHeaders(false)` to list-icon requests (doc 81 §11.2); the D-557 momentum-handoff direction note (doc 87 §6).
- **Deferred concerns (the live ones):** WatchKey 17-field god-object; main-thread runBlocking in the Downloads→Watch SAF scan; dead download code (DownloadVideoPickerSheet, setRetryingStatus); 4 god-class .kt files >2000 lines; DB migrations on `onOpen` (needs `.sqm` before production). The round-54-era 22-entry table in progress.md is HISTORICAL (marked as such) — many items were resolved long ago (release signing, the updates engine, notifications, the AniList tracker).
- **Dashboard deep debt (disclosed, D-565):** deep-history pages carry representative data (status facts were refreshed at round 115). The user said (round 89) to leave the dashboard as-is until they call the focus.

## 7. The documentation map (where everything lives)

| What | Where |
|------|-------|
| Session bootstrap | `AGENT-CONTEXT/SESSION.md` (refreshed round 115) |
| Project orientation | `AGENT-CONTEXT/master.md` (Current Status refreshed round 115) |
| The rules | `AGENT-CONTEXT/CORE_RULES.md` (31 sections — the constitution) |
| The task loop | `AGENT-CONTEXT/workflow.md` (D-738 form) |
| Live status | `AGENT-CONTEXT/memory/progress.md` (top CURRENT STATUS block + Round sections at the bottom; the middle is historical) |
| Decisions | `AGENT-CONTEXT/memory/decisions.md` (newest at top; through D-739) |
| Round records | `APP/ani-kuta/DOCUMENTATION/cloudstream-v2/` (docs 72-97 = rounds 90-115; next: 98) |
| Release routines | `APP/ani-kuta/DOCUMENTATION/release/RELEASE-PLAYBOOK.md` (DORMANT under D-738) + `BUILD-AND-BRANCH-GUIDE.md` |
| Module map / architecture | `AGENT-CONTEXT/knowledge/module-map.md` (57 modules, re-verified round 115) + `architecture.md` |
| Dashboard | `DASHBOARD/webpage/` → https://testplay-byte.github.io/ANI-KUTA/ |
| The new agent's entry | repo root `NEW_AGENT_SETUP.md` (the user's file — refreshed round 115) |

## 8. Parting notes from the round-115 agent

- The user is **happy with the current state** ("you have done everything properly, and I am quite satisfied with the overall results") — rounds 111-114's UI work (the episode-list settings unification, the classic-row unification, the Elements grids) landed well. Protect that surface; it was hard-won through many device rounds.
- The user's asks are usually speech-to-text streams: read them twice, map complaints to the exact UI vocabulary (the round records' "verdicts" sections show how), fix at the ROOT cause (the D-737 weight lesson is the latest example — see lessons-learned).
- **When a release IS ordered:** follow `RELEASE-PLAYBOOK.md` exactly — it encodes rounds 37-114 of release discipline (the debug-release loop, the professional release + re-host, assets-only to the official repo, the website fallback). The round-114 execution (doc 96 §7) is the freshest worked example.
- Keep the docs honest. This round existed because counts drifted (56 vs 57 modules, stale versions in master.md/README, a lost playbook). The lesson: verify numbers against `settings.gradle.kts` / `git ls-files` at every phase boundary — never copy a count forward.
- The ntfy TASK808DONE 429s: keep the final message under ~200 chars if possible.

---
*Round 115, 2026-10-02. Good luck — the project is in good shape, the user is fair and precise, and the docs are now current. Keep them that way.*
