# Round 115 — The Debug-Builds-Only Order, The Staleness Sweep, The In-Repo Release Playbook, And The Next Agent's Handoff

**Round type:** documentation & handoff round (no code changes) ·
**Work orders:** D-738 (the phase order) + D-739 (the round's doc decisions) · **Branch:** `feature/round-57-cloudstream-downloads`

> The user's round-115 order: "you have done everything properly, and I am
> quite satisfied with the overall results. Now… just document all the
> things, update any stale documentation… and make sure that everything is
> up to date and well handled and properly managed… now we are going to do
> the debug builds from now on… you are not going to do any of the release
> versions until I tell you… make sure that the handoff and our current
> progress and everything like that is properly documented and properly
> updated. Because afterwards, on this project, a new agent is going to be
> working on it."

## 1. D-738 — the debug-builds-only phase

The order's two clauses: "debug builds from now on" + "not going to do ANY
of the release versions until I tell you." THE INTERPRETATION (conservative,
banked with the recovery path): every round ends at **CI green** — the Build
APK debug run's `anikuta-apk` artifact is the round's deliverable. NO release
branches, NO version bumps (D-425/D-430 unchanged — the mainline stays
1.1.20/10120), NO tags, NO Release APK / release-build-once runs, NO re-hosts,
NO website bumps — debug AND professional alike — until the explicit order.

- **Why conservative:** "ANY of the release versions" is emphatic; the
  alternative reading (per-round debug releases continue, only the
  professional line pauses) risks publishing releases the user forbade. The
  conservative reading can never violate the order, and one explicit order
  restores either line.
- **The disclosed wrinkle:** the mainline artifact carries versionCode 10120 —
  it will NOT install over the v1.1.71 debug release (10171) without
  `adb install -d` or an uninstall. In-place device updates require an
  ordered debug release (the playbook's Routine A).
- **Propagated to:** SESSION.md (Current State + the loop + Key Rules),
  workflow.md (the loop's D-738 stop + branch discipline), master.md,
  project-overview.md, the repo-root README + NEW_AGENT_SETUP, the playbook's
  dormancy banner, the dashboard's phase text.

## 2. The staleness audit — three eras of drift, coexisting

| Claim | Reality (verified) | Where it was wrong |
|---|---|---|
| Modules "50 / 46 / 56 (32 core)" | **57 = 1 app + 33 core + 2 data + 21 feature (12 features: 9 api/impl splits + 3 singles)** | module-map, architecture, project-overview, navigation, SESSION, master, dashboard |
| .kt files "408 / 569" | **634 in APP/ani-kuta** (2112 repo-wide incl. 1478 read-only REFERENCES copies) | master ×2, project-overview, dashboard |
| Tables "26 / 41 .sq" (and "28", "21") | **25 tables / 17 .sq** (app.sq empty since D-198; the 41-file repo-wide count includes 24 REFERENCES .sq — a FALSE number this round itself briefly propagated before the 2-a audit caught it; one of the 26 CREATE TABLE hits is a comment in watch.sq) | architecture, module-map, dashboard ×10 sites |
| tracker-anilist "placeholder / OAuth stub" | **Fully implemented** (AniListTracker ~580 lines, OAuth, GraphQL sync, TrackSyncManager + 4 more classes, 1471 lines total) | module-map, architecture, dashboard ×2, progress.md's historical table |
| Koin "22 modules" | **30 + debugKoinModules() (2 debug)** | architecture ×2 |
| "24 NavKey branches" | **44** (MainActivity.kt `when(currentKey)`) | architecture |
| WatchKey "15 fields" | **17 fields** (5 serialized strings unchanged) | architecture ×2 |
| Dashboard "14 pages" | **20** | architecture, dashboard knowledge, master |
| Latest releases "v1.1.37 / v1.1.3" | **v1.1.71 / v1.1.14** (both round 114, LIVE) | master's Current Status (Round-77 era), dashboard data.ts |
| "The re-host stays blocked on a token" | **Cleared round 114** (the user's PAT; v1.1.14 LIVE on the official repo) | master |
| "release/1.1.38…58 stay on the remote" | **The remote carries ONLY the mainline**; the release branches are local-only sandbox artifacts (remote tags: 139) | SESSION |
| "official-repo-token at /home/z/.secrets/" | **Gone** — the release PAT lives with the user | SESSION |
| The release playbook | **LOST** — the round-80 original was "repo-external" and died in a sandbox reset | SESSION's dangling reference |

The module-map carried all three count-eras at once (header 50 / core section
26 / summary 30). The dependency rule was also wrong: feature IMPLs DO depend
on sibling APIS (anime-details:api ← browse/library/search/history/updates;
watch:api ← history) — the accurate rule is *impl→sibling-api yes,
impl→sibling-impl never, only :app depends on impls*.

## 3. The sweep (what was written)

1. **`knowledge/module-map.md`** — fully rewritten from the 2-a research
   sub-agent's module-by-module verification (every module's
   `build.gradle.kts` read; deps + key files per module; the corrected rules;
   the corrected count summary; the 25/17 database line).
2. **`knowledge/architecture.md`** — 19 stale claims fixed (the graph header,
   the layer table, the dependency rule, the nav count, the DI count, the
   full table-groups table re-derived, the subtitle-prefs count, 20 pages,
   the WatchKey count, the tracker resolution).
3. **`knowledge/project-overview.md` + `knowledge/dashboard.md`** — the scale
   lines + the current status + the page/data-file tables current.
4. **`SESSION.md`** — Current State (Round 115), the clone steps (the
   HANDOFF read + the tag-fetch note), the Open Items (rewritten from the
   Round-107 era), the folder tree (the round-record locations + 57 modules),
   the credentials lines (the official PAT lives with the user).
5. **`master.md`** — the Round-77-era Current Status rewritten; the folder
   counts; the root-pollution note resolved (the repo root is clean);
   31 sections; the handoff pointer.
6. **`navigation.md`** — the HANDOFF entries (current + historical), the
   knowledge descriptions, the DOCUMENTATION rows (cloudstream-v2 = THE round
   records: docs 72-97 = rounds 90-115, next 98; release/ = the guides), the
   download-research rows (17-25 research + 26-38 records through round 80).
7. **`workflow.md`** — the loop's D-738 stop (the round ends at the CI
   artifact + the checklist), the ntfy topics (THE-TASK-IS-DONE default +
   the TASK808DONE override + the 429 note), the branch discipline (the
   mainline IS the work branch; release branches are ordered-only).
8. **The repo root** — README.md (the build line, the status, the layout
   tree) + NEW_AGENT_SETUP.md (the reading list + the codebase map + the
   D-738 note; the user's instruction structure preserved).
9. **`HANDOFF-ROUND-115.md`** (new) — the next agent's complete handoff: the
   state, the D-738 order ("read this twice"), the loop, the environment
   gotchas (CI-only builds, the secrets, the sandbox-reset history, the
   clone quirks, the 429s), the rules that matter most, the open items, the
   documentation map, the parting notes. **`HANDOFF-POSTER-NOTIFICATIONS.md`**
   — marked HISTORICAL at the header (resolved by the round-80 rework).
10. **`DOCUMENTATION/release/RELEASE-PLAYBOOK.md`** (new) — the in-repo
    rebuild of the lost round-80 playbook: the invariants, Routine A (the
    debug release), Routine B (the professional release), Routine C (the
    official re-host — assets only), Routine D (the website + README + the
    updater simulation), the post-release ledger, the failure playbook
    (D-436 R8, D-435 push-path, D-564 tag collision, the round-76 token
    block, the 429s, the lost playbook itself).
11. **`DOCUMENTATION/release/BUILD-AND-BRANCH-GUIDE.md`** — the Round-115
    revision block (the mainline rename, D-738's dormancy, the playbook
    pointer), the branch table (the current release branches), D-444's
    mascot icon, D-674, the current version examples, the lost companion
    guides re-pointed, the D-738 install wrinkle.
12. **`DOCUMENTATION/README.md`** — the release/ + cloudstream-v2/ rows.
13. **The memory ledger** — D-738 + D-739 prepended; progress.md's CURRENT
    STATUS + What's Next/Blockers/Known-doc-debt/Last-Updated rewritten
    (the round-54-era versions retired; the Deferred Concerns table marked
    HISTORICAL with the resolution notes); the Round 115 sections; the
    changelog entry; three new lessons (counts-drift, repo-external-is-
    undocumented, the ambiguity asymmetry).
14. **The dashboard** (sub-agent 2-b + the main agent's corrections) —
    data.ts v10 + decisions.ts v10 (the representative D-727/D-737/D-738
    entries) + the status pages + Footer: 57 modules, 634 .kt, 25/17,
    D-001..D-739, v1.1.71 + v1.1.14, the D-738 phase, the remote-branch
    truth. `bunx tsc --noEmit` clean. Deep history stays representative
    (D-565).

## 4. The verification

- **2-a (read-only research sub-agent):** the module-map data + the
  architecture audit — every number in §2 grounded in files it read
  (settings.gradle.kts, every build.gradle.kts, the .sq files, AnikutaApp.kt,
  MainActivity.kt, DebugInit.kt). Its decisive catch: the app's schema is
  25/17, not the 26/41 the round's own research had derived repo-wide.
- **2-b (full-stack-dev sub-agent):** the dashboard refresh — hit its turn
  limit mid-flight; the main agent audited its full diff, corrected the
  propagated 26/41 + 2112-as-app-count claims to 25/17 + 634, fixed the
  tracker-anilist entries, finished the sweep, and typechecked clean
  (`bunx tsc --noEmit` exit 0).
- **CI:** docs-only push — no Build APK run by the D-472 design; the
  DASHBOARD paths trigger the Pages deploy (verified in the round's close-out).
  The last code build stands GREEN (run 36925093812 @ eaf9445 — D-737).

## 5. The state at close

- **On device:** debug v1.1.71/10171 + professional v1.1.14/10114 (both
  round 114). The user's device round on them is the NEXT INPUT (checklist
  doc 96 §5) — delivered by the NEXT agent.
- **The handoff:** HANDOFF-ROUND-115.md + SESSION.md current + this record.
  The next record is **98**; the next decision is **D-740**.
- **No releases were performed this round** (D-738 in force from the order's
  receipt).

## 6. NOT-APPLIED + documented (unchanged from doc 96 §6)

The "Hard Sub" spaced-label edge (consistent with the MPV parse); the
token-matching semantics living in five places repo-wide (a future
consolidation candidate — designsystem already depends on core:common); the
badge dims with its watched row; the InFlight tap no-op during the resolve
window; ExtensionInstaller's two OS-fallback toasts; the AppToast id-counter
race; the dead AutoLinkPopup.kt cleanup candidate; a FUTURE Coil 3.1.x
upgrade must ADD respectCacheHeaders(false) to list-icon requests (doc 81
§11.2); the D-557 momentum-handoff direction note (doc 87 §6).

## 7. The round's ledger

- Commits: the documentation sweep (this record + the memory ledger ride the
  same docs-only push; see the git log for the exact split).
- CI: no APK runs (docs-only, D-472); the dashboard deploy runs on the
  DASHBOARD paths.
- The worklog: tasks 0 / 2-a / 2-b(+the correction addendum) / 3 —
  `/home/z/my-project/worklog.md`.
- ntfy: TASK808DONE (short message; retry on 429).
