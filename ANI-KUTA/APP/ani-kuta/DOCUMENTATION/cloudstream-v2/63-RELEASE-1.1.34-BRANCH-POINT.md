# 63 — RELEASE v1.1.34 BRANCH POINT (Round 72 / D-560) + THE ONE-TIME ACTUAL RELEASE BUILD

**Date:** 2026-09-23 · **Branch point:** `release/1.1.34` cut from the round-72 head on `feature/round-57-cloudstream-downloads` · **Feature CI:** the implementation run on cad75ef3 (see the D-560 record)

## What ships in v1.1.34 (10134)

The v1.1.33 device round's directives — D-560, one release, the verified-good carry untouched:

- **The sponsor popup, un-cramped** — the card grew air instead of furniture: a 64dp tinted hero bubble behind each state's glyph, a quiet SPONSORED eyebrow, ONE short line of copy per state (the two-sentence paragraphs are gone), a measured rhythm (28dp padding, 28dp corners), "Not now" as the unified escape. The ad-gate machinery is byte-identical — the same states, the same back-cancels, the same return-pill wiring.
- **The "draw over other apps" option, exactly as specified** — a single clean row on the popup's two sitting states, present ONLY while the consent is missing; the moment the user grants it (from the row, in the system's toggle screen) and returns, the row is gone ("it won't even show anything"). The consent is what makes the floating return pill work over the browser.
- **THE ONE-TIME ACTUAL RELEASE BUILD** — a new dispatch-only workflow (`release-build-once.yml`) builds the release-signed all-ABI line: `ani-kuta-v1.1.34-arm64-v8a.apk`, `-armeabi-v7a.apk`, `-x86.apk`, `-x86_64.apk`, `-universal.apk`, plus `SHA256SUMS.txt` and the `ANI-KUTA-v1.1.34-RELEASE.zip`, every APK gated (existence, per-APK lib/ ABI audit, apksigner + cert-DN) and delivered as a WORKFLOW ARTIFACT — deliberately NOT a GitHub Release on this repo (the D-447 policy is mechanically forced: the in-app updater reads all releases INCLUDING prereleases per D-251, so any release-signed asset here would be offered to the DEBUG app and fail to install). The Confused-Creature-180 re-host stays blocked on the release-agent token (round-39).
- Untouched on purpose: the scroll collapse, the episode-list layouts (classic/grid/timeline/cinema), the search system, the sheets, the swipe/download/preview behaviors.

## Release mechanics

- `release/1.1.34` cut from the round-72 head; ONE bump commit: `versionName 1.1.34 / versionCode 10134` (the D-430 doctrine: the bump rides the release branch exclusively; the feature line stays 1.1.20/10120; the record comment rides the bump).
- Tag `v1.1.34` (message file repo-external) → the Release APK workflow (the debug arm64-v8a release — run #2 of the cycle).
- Then the ONE-TIME dispatch of `release-build-once.yml` on `v1.1.34` → the all-ABI artifact set (run #3 — the user-ordered D-560 exception to the ≤2 budget, disclosed).
- LIVE verification (debug release): stable, --latest, not draft/prerelease, `ani-kuta-v1.1.34-debug-arm64-v8a.apk` + `SHA256SUMS.txt`; 10134 > 10133 → in-app update.
- Artifact verification (one-time build): the workflow's own gates + the artifact listing (five APKs + the sums + the zip).

## The disclosed CI ledger (v1.1.34 cycle)

1. Implementation run (cad75ef3) — the sponsor popup + the overlay option + the one-time workflow file.
2. Release run (tag v1.1.34) — the debug arm64-v8a release, as every cycle.
3. The one-time all-ABI build (dispatch on v1.1.34) — the user's explicit one-time order; the ≤2 budget is superseded by instruction and disclosed here.
