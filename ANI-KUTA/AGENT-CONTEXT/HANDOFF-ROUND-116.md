# HANDOFF — Round 116 (2026-10-02): the gate rebuild + the About retirement

> **⚠ ROUND-117 ADDENDUM (2026-10-02, read this first):** round 117 (doc 99, D-742) executed the user's both-lines release order — the **debug v1.1.72/10172 is LIVE** on testplay-byte (stable + latest, 2026-10-02T16:52:19Z, run 37036338129 GREEN first-try; 10172 > 10171 → in-place updates work) and the **professional v1.1.5/10105 is BUILT + VERIFIED + STAGED** (run 37036448781 GREEN first-try; the artifact id 11241580958; the re-host set at `/home/z/r117-release/stage` — or re-derived from the artifact if the sandbox reset). **v1.1.14 was THE MISTAKE (the user's correction): the professional line's head is v1.1.5.** THE OPEN ITEM — the re-host + the v1.1.14→v1.1.5 correction on the official repo (Confused-Creature-180/ANI-KUTA) are **PENDING THE RELEASE PAT** (the dev PAT is pull-only there; the token lives with the user — the ask went out with the round-117 report). **THE FIRST ACTION when the token arrives: execute doc 99 §7 exactly** (delete the mistaken v1.1.14 release id 401364896 + its tag — functionally required, the updater picks the highest version; create v1.1.5 stable + latest with the 11 staged assets; re-point the website chip + the official README's five download links; the updater simulation). The wrinkle to disclose: a device holding the mistaken v1.1.14/10114 needs a one-time uninstall (Android blocks versionCode downgrades); v1.1.3 installs update cleanly. D-738's default resumed after the round (releases need the explicit order). Numbering: next record = **doc 100**, next decision = **D-743**. The rest of this file is the round-116 handoff (still valid).

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
