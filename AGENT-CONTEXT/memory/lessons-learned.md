# Lessons Learned — Self-Learning Log

> One-line lessons from mistakes, corrections, and insights.
> Read at task start (grep for tags matching your task type). Append when you learn something.

## How to Use

- **Trigger**: log an entry when (a) the user corrects you, OR (b) you catch your own mistake before/after continuing, OR (c) you have an insight worth remembering.
- **Format**: `- [TAG] lesson (source: <task-id or "self">, <YYYY-MM-DD>)`
- **Tags**: `MISTAKE` (you did wrong), `CORRECTION` (user fixed you), `INSIGHT` (realized), `PATTERN` (recurring).
- **Dedup**: grep existing entries for the keyword before adding. Don't log the same lesson twice.
- **Stale**: mark `~~strikethrough~~` with `→ superseded by <ref>` when a newer entry contradicts.
- **Promote**: if a lesson is a recurring `PATTERN`, also add a one-line rule to `CORE_RULES.md`.

---

## Entries

- [MISTAKE] Don't reference `@android:color/system_accent1_500` (API 31+) when minSdk is 24 — AAPT2 fails the build. Use a static color + `values-v31/` override. (source: Task 7, 2026-08-01)
- [MISTAKE] The Gradle `-PabiFilters=...` flag does nothing unless `build.gradle.kts` reads it. ABI config lives in `ndk.abiFilters` only. (source: Task 9, 2026-08-01)
- [PATTERN] Sub-agent plan reviews catch real flaws — always run one before big changes, and verify findings before acting. (source: Tasks 7, 9, 2026-08-01)
- [MISTAKE] GitHub tokens embedded in git remote URLs leak via `.git/config`. Use credential store instead. (source: Task 10, 2026-08-01)
- [INSIGHT] When consolidating docs, grep for stale cross-references that point to removed files — they break silently. (source: Task 3 review, 2026-08-01)
- [MISTAKE] `kotlinOptions { }` inside `android { }` is deprecated in Kotlin 2.0+. Use top-level `kotlin { compilerOptions { } }`. (source: Task 7, 2026-08-01)
- [INSIGHT] When the user says "I provided X file" but it's not in the upload folder, STOP and flag it — never fabricate the missing content. Do the independent work; pause the dependent work. (source: self, 2026-08-01, design.md missing)
- [MISTAKE] Don't mix old-project analysis docs with new-project architecture docs. `REFERENCES/old-kuta/DOCUMENTATION/` is for OLD project analysis only. New project research/plans go in `APP/ani-kuta/DOCUMENTATION/`. Always check the documentation zone (CORE_RULES.md §21) before writing a doc. (source: self, 2026-08-02, docs 10-16 placed in wrong folder)
- [PATTERN] Don't blindly trust sub-agent output — verify findings by reading the source files yourself before acting. Sub-agents find real flaws, but some may be false positives. (source: user feedback, 2026-08-02)
- [MISTAKE] Build-logic convention plugins need Maven artifact coordinates (e.g. `com.android.tools.build:gradle:8.9.1`), NOT plugin IDs (e.g. `com.android.application:8.9.1`). Use `libs.versions.X.get()` to reference the version. (source: Phase 2 CI iteration 1, 2026-08-02)
- [MISTAKE] Nav3 1.1.5 requires compileSdk 36, not 35. Always check if new deps require a higher SDK. (source: Phase 2 CI iteration 2, 2026-08-02)
- [MISTAKE] SQLDelight 2.3.2 has compatibility issues with Kotlin 2.2.0 — use 2.0.2 (the old project's proven version). Don't always use the "latest" without verifying compatibility. (source: Phase 2 CI iteration 2, 2026-08-02)
- [PATTERN] When a module exposes a third-party type in its public API (return type, constructor parameter), use `api(libs.X)` not `implementation(libs.X)`. Otherwise consumers get "Cannot access class" errors. (source: Phase 2 CI iteration 4, 2026-08-02)
- [INSIGHT] CI `timeout-minutes` includes queue wait time. Set it to 30+ min for GitHub-hosted runners (queue delays of 10-15 min are common). (source: Phase 2 CI iteration 3, 2026-08-02)
- [MISTAKE] `count` is a reserved keyword in SQLDelight's SQL grammar — can't use as a column alias. Use `event_count` or another non-reserved name. (source: Phase 3a CI iteration 1, 2026-08-02)
- [MISTAKE] Kotlin property + function with same name cause JVM signature clash. A `val trustedFingerprints` generates `getTrustedFingerprints()`, which clashes with `fun getTrustedFingerprints()`. Rename the function (e.g., `getAllTrusted()`). (source: Phase 3b CI iteration 3, 2026-08-02)
- [MISTAKE] SQLDelight generates query property names from .sq filename — does NOT convert snake_case to camelCase. Use camelCase filenames (e.g., `downloadQueue.sq` not `download_queue.sq`). (source: Phase 3c CI iteration 3, 2026-08-02)
- [MISTAKE] `AnimeHttpSource.fetchVideoList` returns `Observable<List<Video>>` (RxJava), not `List<Video>`. Use `.awaitSingle()` to convert to suspend. (source: Phase 3c CI iteration 2, 2026-08-02)
- [MISTAKE] `MPVLib.observeProperty` takes Int format constants (`MPVLib.mpvFormat.MPV_FORMAT_INT64`), not String labels. (source: Phase 3c CI iteration 2, 2026-08-02)
