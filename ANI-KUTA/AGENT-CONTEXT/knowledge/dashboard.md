# Dashboard — Approach & Handling

> The companion web dashboard. Visual documentation FOR THE USER.
> Design language lives in `DASHBOARD/webpage/DESIGN.md`. Rules in `CORE_RULES.md` §16.

---

## Purpose
A visual documentation site the **user** reads to understand the system: modules, screens, workflows, decisions, plans, progress. Not for the agent — for the user.

## Location
`DASHBOARD/webpage/` — a full Next.js project (static export → GitHub Pages).

## Deployment
- GitHub Actions builds it on every push to `main`.
- Publishes to **GitHub Pages** at `https://testplay-byte.github.io/ANI-KUTA/`.
- Static export (`output: export` in next.config) + `basePath: /ANI-KUTA`.
- Workflow: `.github/workflows/deploy-dashboard.yml`.

## Design Language
- Defined in `DASHBOARD/webpage/DESIGN.md` (user-provided, tested, reliable).
- **Strictly followed** on all pages, all components. No deviations.
- Look: cream tones, rounded corners, good colors.
- **Dark mode toggle** at the top of every page.
- Flexible for future improvement — edit `DESIGN.md` to evolve it; confirm non-trivial changes with the user.

## Content (what the dashboard shows)
Organized into sections, filterable/sortable by the user:
- **Modules** — each module: what it does, how it works, its workflow (with tree/graph visuals).
- **Screens** — per-screen breakdown: UI vs backend, data flow.
- **Plans** — current + past plans, with arrows/workflow visuals.
- **Decisions** — decision log with context + reasoning.
- **Progress** — live project status, phase advancement.
- **Architecture** — module graph, dependency links, data-flow diagram.
- **Open questions / blockers** — what's pending.

## Visual Techniques
- **Trees** — module hierarchy, folder structure.
- **Graphs** — module dependencies, data flow.
- **Workflow diagrams** — screen flows, decision arrows.
- Modular components so sections can be added/edited independently.

## Update Process
1. Project changes (new module, decision, screen) → main agent updates `AGENT-CONTEXT/`.
2. Main agent delegates a **webpage sub-agent** to reflect the change in `DASHBOARD/webpage/`.
3. Sub-agent works **only** in `DASHBOARD/webpage/` — never touches `AGENT-CONTEXT/`.
4. Push → GitHub Actions rebuilds + deploys to Pages automatically.
5. Dashboard stays a **living view** of the project.

## Sub-Agent Rules (see `CORE_RULES.md` §14)
- Webpage sub-agents: `DASHBOARD/webpage/` only.
- No `AGENT-CONTEXT/` edits by sub-agents.
- Main agent does all AGENT-CONTEXT updates.

## Status
- ✅ **Design language (`DESIGN.md`)**: saved (MEMORY OS + dark mode section).
- ✅ **Demo webpage**: built (Next.js 16 static export, Tailwind 4, MEMORY OS design, dark mode toggle, 5 pages: Overview, Modules, Decisions, Progress, Architecture).
- ✅ **Pages workflow**: `.github/workflows/deploy-dashboard.yml` created.
- ✅ **GitHub Pages**: activated (source = GitHub Actions). URL: `https://testplay-byte.github.io/ANI-KUTA/`.
- 🔄 **Next**: keep the dashboard updated as the project evolves. Sub-agents build page updates; main agent owns AGENT-CONTEXT.
