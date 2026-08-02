# New Project Documentation — APP/ani-kuta/DOCUMENTATION/

> Architecture plans, research, and design decisions for the NEW ANI-KUTA app.
> (Old project analysis lives in `REFERENCES/old-kuta/DOCUMENTATION/`.)

## What's Here

| File | Content |
|------|---------|
| `10-db-research.md` | Room vs SQLDelight → SQLDelight (with reasoning). |
| `11-di-research.md` | Hilt vs Koin → Koin + Injekt (isolated). |
| `12-nav-research.md` | Voyager vs Compose Nav → Nav3. |
| `13-ads-research.md` | Ad system + activity tracking design. |
| `14-architecture-recommendations.md` | Full synthesis + identity system redesign. |
| `15-backup-research.md` | Backup/restore formats (Aniyomi, Mangayomi) + import strategy. |
| `16-phase1-architecture-plan.md` | **The Phase 1 Architecture Plan** — full module tree, data flow, screen map, identity, backup, multi-extension, multi-content-type. |

## Also in APP/ani-kuta/
- `DESIGN-LANGUAGE.md` — the app's design language (colors, typography, components, UI patterns extracted from the old project).

## How to Use
- Start with `16-phase1-architecture-plan.md` for the full blueprint.
- Use `10-13-*.md` for research backing each decision.
- Use `14-architecture-recommendations.md` for the synthesis.
- Use `DESIGN-LANGUAGE.md` when building UI.
