# HANDOFF — Round 116 (2026-10-02): the gate rebuild + the About retirement

> The round-116 DELTA handoff, written for the NEXT agent. `HANDOFF-ROUND-115.md` (the full original — the environment, the loop, the rules, the open items) remains valid context; this file covers what changed ON TOP of it. Read SESSION.md + CORE_RULES.md first.

---

## 1. What this round did (doc 98, D-740 + D-741)

- **D-740 — the extension-testing gate is ALIVE again.** The user reported the ten-second hold on Settings' "Debug options" row opens nothing on EITHER build line. Root cause (traced against the v1.1.53-verified baseline, NOT guessed): the detector's `withTimeoutOrNull` inside `awaitEachGesture` resolved to the `AwaitPointerEventScope` MEMBER (Kotlin member-beats-extension — the kotlinx import was dead weight), whose Compose-internal timer (a `Modifier.Node.coroutineScope.launch { delay() }` + cross-thread `resumeWithException` into the restricted-suspension awaiter) broke when round 97 bumped coroutines 1.9.0 → 1.11.0. Every build v1.1.54 → v1.1.71 carried the dead gate; the code was byte-identical to the verified v1.1.53 build throughout. The rebuild (`MoreListRow.kt`): the armed row rides the plain platform `clickable`, and a composition-scoped `LaunchedEffect` watches the interaction stream — Press starts a main-thread kotlinx `withTimeoutOrNull` window, Release/Cancel kills it, the full window fires `onLongClick` while the finger is down + cancels the press visual, and a `holdFired` flag (reset at the next Press) swallows the trailing tap. Public API unchanged.
- **D-741 — the Settings hub's About & Updates row is RETIRED.** The More page owns the single door (with the update dot). The settings search still routes to the live About page (about.* entries kept as "related things", breadcrumb → "More → About & Updates", the `about_downloaded` landing anchor registered).

## 2. The round's wrinkle (the lost predecessor)

A prior round-116 session had already implemented both fixes and committed them locally — but the push was blocked (the dev PAT had died with a sandbox reset) and ANOTHER reset destroyed the commits. This round re-derived the root cause from the git evidence and shipped a DIFFERENT (simpler, better-rooted) gate design on the user's fresh admin PAT. **The lesson is banked (lessons-learned): push at the earliest green moment; if the push is blocked, say so immediately — local commits are worth nothing.**

## 3. What the next agent must know beyond the round-115 handoff

1. **D-738 stands unchanged** — debug builds only, all releases suspended until the user's explicit order. The round's deliverable is the Build APK debug artifact on commit 53cbc0ef (the run link goes to the user with the doc-98-§6 checklist). The artifact carries versionCode 10120 — it will NOT install over the v1.1.71 release without `adb install -d`/uninstall; if the user wants in-place updates they order a debug release (RELEASE-PLAYBOOK Routine A).
2. **The design doctrine this round set (D-740's banked lesson):** never hand-roll timeouts inside the pointer-input restricted-suspension scope — the `AwaitPointerEventScope` member `withTimeoutOrNull`/`withTimeout` silently shadows the kotlinx imports, and their implementations are internal seams that a coroutines bump can shift. Measure gesture windows from the INTERACTION STREAM with a composition-scoped timer. `MoreListRow.kt` is the reference implementation.
3. **The next inputs:** the user's device round on the artifact (the checklist: doc 98 §6 — the hold, the ordinary tap, the robbed hold, the end-to-end extension-testing flow, the About retirement, the regression sweep). If drift-robbery (finger micro-movement past touch slop killing a 10s hold) shows up in the device round, the deliberately-not-carried "lock" concept (consuming moves after a grace window) is the designed follow-up — it was in the LOST draft, needs a fresh spec, and must be user-ordered first.
4. **Numbering:** next round record = **doc 99**, next decision = **D-742**.
5. **The dashboard was NOT touched** (the round-89 standing instruction — status facts wait for the user's focus call; the docs carry the round-116 state).

## 4. The environment, refreshed

- The fresh admin PAT (round 116) is embedded in the clone's remote URL (`/home/z/ANI-KUTA/.git/config` — never committed) and stored at `/home/z/.secrets/github-credentials` (git-credential format). Push verified working (commit 53cbc0ef).
- The clone is at `/home/z/ANI-KUTA`, branch `feature/round-57-cloudstream-downloads`, full history + all 139 tags fetched.
- If the sandbox resets again: re-clone, re-fetch `--tags`, re-embed the PAT (ask the user if it died), read progress.md's CURRENT STATUS block, then continue.

---
*Round 116, 2026-10-02. The gate is alive, the hub is honest, the ledger is current. Keep it that way.*
