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
| `download-device-testing-checklist.md` | The download-system device checklist. |
| `planning/` | The per-feature plan folders (data-management, debug-bubble, extension-details-page, watch-history-updates). |
| `cloudstream-v2/` | **THE ROUND RECORDS** — docs 72-97 = rounds 90-115 (strictly sequential; next: 98) + the earlier CS-V2 era plans (00-71). Every modern round's full record: the orders, the implementation, the audits, the release record. |
| `release/` | **The build & release guides** — `BUILD-AND-BRANCH-GUIDE.md` (the dev/release line split) + `RELEASE-PLAYBOOK.md` (the full release routines; rebuilt in-repo at round 115 after the round-80 repo-external original was lost; ⛔ DORMANT under D-738 — debug builds only until the user's explicit order). |

## Also in APP/ani-kuta/
- `DESIGN-LANGUAGE.md` — the app's design language (colors, typography, components, UI patterns extracted from the old project).

## How to Use
- Start with `16-phase1-architecture-plan.md` for the full blueprint.
- Use `10-13-*.md` for research backing each decision.
- Use `14-architecture-recommendations.md` for the synthesis.
- Use `DESIGN-LANGUAGE.md` when building UI.
