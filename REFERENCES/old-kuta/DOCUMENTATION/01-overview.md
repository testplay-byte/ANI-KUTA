# 01 — Overview

> What the old ANIKUTA project is, its goals, and current feature state.
> Source: `REFERENCES/old-kuta/ANIKUTA/`.

---

## What ANIKUTA Is

ANI-KUTA is an **anime-first Android streaming app** — a reimagined, restructured version of [Aniyomi](https://github.com/aniyomiorg/aniyomi). It is NOT a fork; it's a clean-room rebuild that borrows Aniyomi's concepts (library, sources, trackers, player) but with a complete redesign and modular architecture.

- **App ID**: `app.confused.anikuta`
- **Display name**: ANIKUTA
- **Status**: Feature-complete (Phase 8+)
- **Modules**: 36 active Gradle modules (not 41 — 5 stubs removed in Phase 9)

---

## Core Features (all working)

| Feature | Description |
|---------|-------------|
| **Browse** | Home tab — AniList trending + seasonal anime. |
| **Search** | AniList + Extension sources, with filters. |
| **Anime Details** | Banner, episodes, source switcher (AniList ↔ Extension, in-place). |
| **Watch (MPV)** | Fullscreen video player via MPV, single instance. |
| **Library** | Grid + list views, categories, sort/filter. |
| **History** | Recently watched episodes. |
| **Updates** | Episode update checker (schedule + calendar + live-check). |
| **Profile (My)** | Stats + charts + recently-watched. |
| **Trackers** | AniList + MAL tracker sync (OAuth). |
| **Backup/Restore** | Anikuta format + Aniyomi format translator. |
| **Downloads** | Offline playback (HTTP + HLS, advanced resume). |
| **Episode Settings** | Display, layout, metadata settings hub. |
| **Extensions** | Aniyomi-compatible source extensions (APK install). |
| **Setup Wizard** | 15-screen animated onboarding (first-launch gate). |
| **Ads System** | On-device ad interstitials (modular, customizable). |
| **App Update** | Self-update via GitHub Releases + APK install. |

---

## Architecture at a Glance

```
:app  →  :feature:*  →  :core:* + :data:*
  (shell)    (UI screens)    (infrastructure + repositories)
```

- **Single Activity** + Voyager 1.0.1 navigation (4-tab bottom nav + modal overlays).
- **Dual DI**: Koin 4.0.0 (host app) + Injekt (extension compatibility).
- **Persistence**: SQLDelight with status-tracking columns.
- **Player**: MPV (aniyomi-mpv-lib), single instance per session.
- **Extensions**: Aniyomi-compatible source-api (loads APKs via DEX classloader).

See `02-architecture.md` for the full module tree + dependency rules.

---

## Tech Stack (key)

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.2.0 |
| UI | Jetpack Compose (BOM) | 2025.03.00 |
| DI | Koin + Injekt | 4.0.0 |
| Persistence | SQLDelight | 2.0.2 |
| Player | MPV (aniyomi-mpv-lib) | — |
| Navigation | Voyager | 1.0.1 |
| Build | AGP / Gradle | 8.9.1 / 8.13 |
| SDK | compile/target 36, min 26 | Android 16 / 8.0 |

See `03-tech-stack.md` for the full library list.

---

## Key Design Decisions

1. **Two-tier identity (ADR-050)** — `LocalId` (per-source) + `ContentId` (survives source switches). Cross-cutting stores use `"$contentId|$episodeNumber"` key.
2. **Pluggable registries** — every extension point is `List<T>` in Koin. Adding a provider = one class + one Koin line.
3. **Architectural inversion** — gateway interfaces declared in `:core:*`, implemented in `:data:*`. Core stays free of data deps.
4. **Aniyomi binary compat (ADR-029)** — `:core:source-api` ships exact `eu.kanade.tachiyomi.animesource.*` package.
5. **Anime-first (ADR-009)** — manga deferred. Reader not implemented.
6. **APK builds via CI only (ADR-003)** — arm64-v8a only (ADR-032). No local builds.

---

## What's Deferred / Not Implemented

- **Manga reader** (ADR-009) — manga modules are stubs.
- **Notifications** (ADR-014) — episode-release notifications not yet built.
- **Local files as source** — `:core:source-local` removed (stub).
- **R8/minify** — off in release builds.
- **Unit tests** — no feature module ships tests.
- **Voyager `rememberNavigator()`** — back stack lost on Activity recreate (known gap).

---

## Documentation Map

| Doc | Content |
|-----|---------|
| `02-architecture.md` | Module tree, layering, DI, nav, build conventions. |
| `03-tech-stack.md` | All libraries, versions, build config. |
| `04-core-modules.md` | Deep analysis of 16 active `:core:*` modules. |
| `05-data-modules.md` | Deep analysis of 3 active `:data:*` modules. |
| `06-feature-modules.md` | Deep analysis of 16 active `:feature:*` modules. |
| `07-data-flow.md` | End-to-end data flow: source → extension → resolver → player. |
| `08-features-breakdown.md` | Per-feature deep dive. |
| `09-rebuild-notes.md` | What to carry over / redesign / drop. |
