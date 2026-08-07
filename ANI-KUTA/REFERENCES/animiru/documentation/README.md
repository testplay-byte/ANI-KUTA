# Animiru — Reference Documentation

> **What this is:** Reference documentation of the Animiru Android app (a fork of
> Aniyomi, itself a fork of Tachiyomi/Mihon), with a strong focus on its
> **video player**. This documentation is **read-only reference material** — no
> code from Animiru is copied into the ANI-KUTA app, no Animiru files were
> modified.
>
> The Animiru source tree lives at:
> `/home/z/my-project/ANIKUTA-PROJECT/REFERENCES/animiru/ANIMIRU/`
>
> All paths quoted in these docs are relative to that root unless stated
> otherwise. Line numbers reflect the state of the repo at the time of analysis.

## Why this exists

ANI-KUTA's player work is being built fresh, and Animiru is the most
up-to-date publicly maintained Aniyomi fork. Studying how Animiru wires MPV
into Android, structures its player UI in Compose, resolves video URLs through
hoster chains, and exposes settings to the user gives ANI-KUTA a concrete
reference implementation to learn from — and a list of anti-patterns to avoid.

The documentation was written by reading the Animiru source tree directly.
**Nothing was modified in the Animiru repo.** The
`REFERENCES/animiru/ANIMIRU/` directory is treated as read-only.

## Repo summary (one-liner)

- **Repo:** `https://github.com/Quickdesh/Animiru.git`
- **Upstream:** fork of [Aniyomi](https://github.com/aniyomiorg/aniyomi),
  which itself forks [Mihon](https://github.com/mihonapp/mihon) (Tachiyomi).
- **App ID:** `xyz.Quickdev.Animiru.mi` (release), `.dev` (debug), `.debug`
  (preview), `.benchmark` (benchmark). See `app/build.gradle.kts:20`.
- **Version:** `0.19.8.0` (versionCode `145`) at the time of analysis.
- **Min Android:** 8.0 (API 26) — `gradle/mihon.versions.toml:3`.
- **Target SDK:** 36 / Compile SDK 37 / NDK 29 — `gradle/mihon.versions.toml:3-6`.
- **License:** Apache 2.0 — `LICENSE`.

## Documentation index

| # | File | Scope |
|---|------|-------|
| — | `README.md` (this file) | Index + how to read these docs |
| 01 | [01-overview.md](01-overview.md) | Repo metadata, modules, build system, key deps |
| 02 | [02-player-architecture.md](02-player-architecture.md) | PlayerActivity / PlayerScreen / PlayerViewModel / MPVPlayer wiring + the video-loading pipeline overview |
| 03 | [03-mpv-initialization.md](03-mpv-initialization.md) | MPV init: mpv.conf, setOptionString, observeProperty, hwdec/vo/cache/net/subtitle config |
| 04 | [04-player-controls.md](04-player-controls.md) | PlayerControls layout, seekbar, double-tap, gestures, lock mode, auto-hide |
| 05 | [05-player-sheets.md](05-player-sheets.md) | Quality / SubtitleTracks / AudioTracks / PlaybackSpeed / More / Chapters / Screenshot sheets + dialogs |
| 06 | [06-video-resolution.md](06-video-resolution.md) | Video/Hoster data model, ext-lib 16 `getHosterList`/`getVideoList`, headers, resolver pipeline |
| 07 | [07-subtitle-management.md](07-subtitle-management.md) | Internal/external subs, sub-add command, ASS override, delay, encoding |
| 08 | [08-extension-system.md](08-extension-system.md) | Extension metadata, ClassLoader, trust, installer, repo system, comparison with ANI-KUTA |
| 09 | [09-player-settings.md](09-player-settings.md) | Player/Audio/Subtitle/Decoder/Gesture/Advanced preferences, persistence, init-vs-runtime application |
| 10 | [10-key-takeaways.md](10-key-takeaways.md) | Patterns to port to ANI-KUTA, anti-patterns, code excerpts to study |

## How to read these docs

- Each doc focuses on **one concern** (init, controls, sheets, etc.).
- Code excerpts include **file path + line numbers** in a fenced block —
  those line numbers are accurate against the commit analysed.
- Where a piece of code is interesting for ANI-KUTA's player development,
  the doc says so explicitly in a `> ANI-KUTA:` callout.
- ASCII diagrams are used where they help (class hierarchy, init pipeline).
- These docs are reference, not design. They describe what Animiru does,
  not what ANI-KUTA should do — that's `10-key-takeaways.md`.

## What was NOT done

- No code was modified in `REFERENCES/animiru/ANIMIRU/`.
- No code from Animiru was copied into `APP/ani-kuta/`.
- No build, install, or runtime test was performed on Animiru — analysis is
  static (reading source) only.
- The dashboard data files were not touched (per `CORE_RULES.md §21`,
  Animiru analysis lives here, in `REFERENCES/animiru/documentation/`).
- `AGENT-CONTEXT/` was not modified by this subagent (per `CORE_RULES.md §14`).
  Only the worklog at `/home/z/my-project/worklog.md` gets a new entry.

## Terminology conventions

| Term | Meaning |
|------|---------|
| **MPV** | The `libmpv` C library, accessed via JNI through the `is.xyz.mpv` package (mpv-android-lib). |
| **MPVLib / MPV class** | Animiru uses `is.xyz.mpv.MPV` directly (not a `MPVLib` wrapper). Older Aniyomi used `MPVLib`; Animiru renamed/refactored it. |
| **Hoster** | A streaming-mirror server. One episode has many hosters; each hoster has many `Video`s. |
| **Video** | A single resolvable video stream (URL + headers + tracks). |
| **Track** | Subtitle or audio track, either embedded (from MPV `track-list`) or external (`sub-add`/`audio-add`). |
| **Sheet** | Bottom-anchored Compose sheet (QualitySheet, SubtitlesSheet, …) — opened from the player controls. |
| **Panel** | Right-side-anchored Compose card stack (SubtitleSettingsPanel, VideoSettingsPanel, SubtitleDelayPanel, AudioDelayPanel) — opened via long-press on a control button. |
| **Dialog** | Centered modal (EpisodeListDialog, IntegerPickerDialog). |
| **ext-lib 16** | The 16th version of `extensions-lib`. New API: `getHosterList`/`getVideoList(hoster)`. Old API: `getVideoList(episode)`. |
