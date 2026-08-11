# Dashboard — Approach & Handling

> The companion web dashboard. Visual documentation FOR THE USER.
> Design language lives in `DASHBOARD/webpage/DESIGN.md`. Rules in `CORE_RULES.md` §16 + §25.

---

## Purpose
A visual documentation site the **user** reads to understand the system: modules, screens, workflows, decisions, plans, progress. Not for the agent — for the user.

## Location
`DASHBOARD/webpage/` — a full Next.js 16 project (static export → GitHub Pages).

## Deployment
- GitHub Actions builds it on every push to `main`.
- Publishes to **GitHub Pages** at `https://testplay-byte.github.io/ANI-KUTA/`.
- Static export (`output: export` in next.config) + `basePath: /ANI-KUTA`.
- Workflow: `.github/workflows/deploy-dashboard.yml`.

## Design Language
- Defined in `DASHBOARD/webpage/DESIGN.md` (user-provided "MEMORY OS" design system + dark mode).
- **Strictly followed** on all pages, all components. No deviations (CORE_RULES §16).
- Look: cream tones, rounded corners, good colors, dark mode toggle at top of every page.
- Flexible for future improvement — edit `DESIGN.md` to evolve it; confirm non-trivial changes with the user.

## Pages (14 total)
| Page | Route | Content |
|------|-------|---------|
| Overview | `/` | Project summary, metrics (46 modules, D-001..D-186, 28 tables), phase timeline |
| Architecture | `/architecture/` | Module tree, dependency rules, data flow, identity, multi-extension (D-150: Nav3 removed) |
| Modules | `/modules/` | 46-module hierarchy + tree view |
| Database | `/database/` | 28 tables (15 .sq files), ER diagram, indexes, FK relationships |
| DB Viewer | `/db-viewer/` | Upload + view database JSON exports (from debug bubble) |
| Design | `/design/` | App design language — lime/dark surfaces, accent presets, components |
| Progress | `/progress/` | All phases done (0–5 + B/C/D/WP/HI/UP/SC/TR/NOTIF/CW/DB) |
| Analytics | `/analytics/` | Module size distribution, build times, docs coverage |
| Planning | `/planning/` | Gantt chart, task board, phase checklists |
| Decisions | `/decisions/` | Decision log D-001..D-186 (representative entries + range) |
| Downloads-Plan | `/downloads-plan/` | Download system research + implementation plan |
| Phase-D | `/phase-d/` | Data-management phase plan |
| Debug-Bubble | `/debug-bubble/` | Debug bubble feature plan + implementation |
| Testing | `/testing/` | Device-testing checklists |

## Data Files (lib/)
| File | Content |
|------|---------|
| `lib/data.ts` | NAV_ITEMS, MODULES, MODULE_TREE, DATA_FLOW_STEPS, phases, metrics, tasks, ADRs |
| `lib/decisions.ts` | Decision entries (representative subset of D-001..D-186) |
| `lib/schema.ts` | Database schema tables (28) + summary stats |
| `lib/testingData.ts` | Device-testing checklist data |
| `lib/downloadsPlan.ts` | Download-system plan data |
| `lib/phaseD.ts` | Phase D (data-management) plan data |
| `lib/debugBubble.ts` | Debug bubble plan data |

## Update Process (CORE_RULES §25)
1. Project changes (new module, decision, screen) → main agent updates `AGENT-CONTEXT/`.
2. Main agent delegates a **full-stack-dev sub-agent** to reflect the change in `DASHBOARD/webpage/` (CORE_RULES §19).
3. Sub-agent works **only** in `DASHBOARD/webpage/` — never touches `AGENT-CONTEXT/` (CORE_RULES §14).
4. Push → GitHub Actions rebuilds + deploys to Pages automatically.
5. Dashboard stays a **living view** of the project. No drift (CORE_RULES §26).

## Sub-Agent Rules (CORE_RULES §14)
- Webpage sub-agents: `DASHBOARD/webpage/` only.
- No `AGENT-CONTEXT/` edits by sub-agents.
- Main agent does all AGENT-CONTEXT updates.
- After sub-agent finishes, main agent verifies the build passed + updates AGENT-CONTEXT memory.

## Known Dashboard Debt
- `lib/schema.ts` `SCHEMA_TABLES` array still lists PLANNED Phase-1 table names (`content_uid`, `external_reference`, etc.) not the ACTUAL current schema (`content`, `content_ext`, `anilist_detail`, etc.). A full rewrite would change the database page UI — deferred (flagged by sub-agent, tracked for future dashboard polish).

## Status
- ✅ **Design language (`DESIGN.md`)**: saved (MEMORY OS + dark mode section).
- ✅ **14 pages**: all built + deployed.
- ✅ **Data updated** (this session): 46 modules / 26 core / 18 feature / D-001..D-186 / 28 tables / 15 .sq files / Nav3 removed / `main` branch — across all 14 pages + Footer.
- ✅ **GitHub Pages**: live at `https://testplay-byte.github.io/ANI-KUTA/`.
- 🔄 **Next**: keep the dashboard updated as the project evolves. Sub-agents build page updates; main agent owns AGENT-CONTEXT.
