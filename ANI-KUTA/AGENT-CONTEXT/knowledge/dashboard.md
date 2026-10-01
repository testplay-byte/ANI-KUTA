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

## Pages (20 total)
| Page | Route | Content |
|------|-------|---------|
| Overview | `/` | Project summary, metrics (see lib/ data files for the CURRENT counts — 57 modules, D-001..D-739, 25 tables), phase timeline |
| Architecture | `/architecture/` | Module tree, dependency rules, data flow, identity, multi-extension (D-150: Nav3 removed) |
| Modules | `/modules/` | 57-module hierarchy + tree view |
| Database | `/database/` | 25 tables (17 .sq files, current) over the D-192-era transcription, ER diagram, indexes, FK relationships |
| DB Review | `/database-review/` | Schema review — merge candidates, optimization plan (dated snapshot + current-status note) |
| Design | `/design/` | App design language — lime/dark surfaces, accent presets, components |
| Progress | `/progress/` | All phases done (0–5 + B/C/D/WP/HI/UP/SC/TR/NOTIF/CW/DB) |
| Analytics | `/analytics/` | Module size distribution, build times, docs coverage |
| Planning | `/planning/` | Gantt chart, task board, phase checklists |
| Decisions | `/decisions/` | Decision log (representative entries + range; the dashboard's lib data was refreshed at Round 115 — canonical range D-001..D-739, representative subset incl. D-565/D-727/D-737/D-738) |
| Downloads-Plan | `/downloads-plan/` | Download system research + implementation plan |
| Phase-D | `/phase-d/` | Data-management phase plan |
| Debug-Bubble | `/debug-bubble/` | Debug bubble feature plan + implementation |
| Testing | `/testing/` | Device-testing checklists |
| DB Viewer | `/db-viewer/` | Upload + view database JSON exports (from debug bubble) |
| Test-Controller | `/test-controller/` | Autonomous remote UI testing — the relay architecture (D-198 v4) |
| Updates-Plan | `/updates-plan/` | Updates + notifications system plan (round 80) |
| Database-Plan | `/database-plan/` | Database management phase plan |
| D-240-Improvements | `/d-240-improvements/` | The D-240-era improvement set |
| Review | `/review/` | Review & roadmap (dated snapshot + the Round-115 status note) |

## Data Files (lib/)
| File | Content |
|------|---------|
| `lib/data.ts` | NAV_ITEMS, MODULES, MODULE_TREE, DATA_FLOW_STEPS, phases, metrics, tasks, ADRs |
| `lib/decisions.ts` | Decision entries (representative subset — refreshed at Round 115; the canonical record is AGENT-CONTEXT/memory/decisions.md) |
| `lib/schema.ts` | Database schema tables (D-192-era transcription, 26 tables — current schema: 25 tables / 17 .sq, disclosed in-header) + summary stats |
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
- `lib/schema.ts` `SCHEMA_TABLES` array still carries the D-192-era transcription, not the ACTUAL current schema (the two-ID content system: main_entry, content_details, data_source, system + the round-57+ adds). A full re-transcription would change the database page UI — deferred (the in-header + page-banner current-schema notes disclose it).

## Status
- ✅ **Design language (`DESIGN.md`)**: saved (MEMORY OS + dark mode section).
- ✅ **20 pages**: all built + deployed.
- ✅ **Status facts refreshed at Round 115** (2026-10-02): 57 modules / 1+33+2+21, 25 tables / 17 .sq, 634 app .kt files, D-001..D-739, debug v1.1.71 + professional v1.1.14 LIVE, the D-738 debug-builds-only phase — across the status pages (Dashboard / Architecture / Modules / Database / Progress / Decisions / Review) + Footer. The deep-history pages (plans, reviews, snapshots) stay REPRESENTATIVE by design (D-565's disclosed debt — a dedicated dashboard session can re-transcribe them if the user ever calls that focus).
- ✅ **GitHub Pages**: live at `https://testplay-byte.github.io/ANI-KUTA/`.
- 🔄 **Next**: keep the dashboard updated as the project evolves. Sub-agents build page updates; main agent owns AGENT-CONTEXT.
