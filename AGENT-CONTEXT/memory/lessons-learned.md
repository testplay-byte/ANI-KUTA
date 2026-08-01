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
