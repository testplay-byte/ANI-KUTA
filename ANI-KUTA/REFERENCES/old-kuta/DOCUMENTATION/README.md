# Old Project Documentation — REFERENCES/old-kuta/

> Structured analysis of the old ANIKUTA project (reimagined Aniyomi).
> Purpose: serve as a guiding reference for rebuilding the app from scratch.

## What's Here

| File | Content |
|------|---------|
| `01-overview.md` | What the app is, goals, current feature state. |
| `02-architecture.md` | Module tree, layering, dependency rules, build system. |
| `03-tech-stack.md` | Technologies, versions, libraries (from version catalogs). |
| `04-core-modules.md` | Deep analysis of each `:core:*` module. |
| `05-data-modules.md` | Deep analysis of each `:data:*` module. |
| `06-feature-modules.md` | Deep analysis of each `:feature:*` module. |
| `07-data-flow.md` | How data moves: source → extension → resolver → player. |
| `08-features-breakdown.md` | Per-feature deep dive: browse, search, watch, library, etc. |
| `09-rebuild-notes.md` | What to carry over / redesign / drop for the new project. |

## Source Location
The old project source lives at `REFERENCES/old-kuta/ANIKUTA/`.
These docs analyze that source — they don't modify it.

## How to Use
- Start with `01-overview.md` for the big picture.
- Use `04/05/06-*.md` to understand specific modules.
- Use `07-data-flow.md` to understand how the app works end-to-end.
- Use `09-rebuild-notes.md` when planning the new architecture.

> **Note**: New project architecture/research docs (DB, DI, Nav, Ads, identity, backup, Phase 1 plan) live in `APP/ani-kuta/DOCUMENTATION/`, NOT here. This folder is for old project analysis only. See `AGENT-CONTEXT/CORE_RULES.md` §21.
