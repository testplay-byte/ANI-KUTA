# Reporting Format — HTML Deliverables

> Standing user order (2026-10-19): **reports the user reads are delivered as self-contained HTML**, in the same neumorphic dark style as the reference backlog. Markdown remains the text source; HTML is the visual companion. Charts, graphs, and diagrams are welcome when they add signal.

## When this applies

Use the HTML companion for anything substantial the user will read and revisit:

- improvement backlogs / audit findings
- round summaries and release notes
- architecture or flow analyses
- comparisons, research digests, test matrices
- any Markdown report longer than a screenful

Small one-off chat answers, commit messages, and internal memory files stay Markdown only.

## Pairing rule (source of truth)

| File | Role |
|------|------|
| `<Name>.md` | canonical text: items, evidence, line refs, decisions |
| `<Name>.html` | visual companion: same facts, filterable/sortable/collapsible |

- Create/refresh **both in the same change**. Never let the HTML drift from the Markdown.
- The HTML footer names the Markdown it mirrors.
- If the Markdown lives outside the clone (e.g. workspace root), the HTML sits **beside it**.
- Re-confirm line references before editing code; HTML line refs are documentary, not executable.

## Reference implementation

- `C:\Users\khurr\Desktop\ANI-KUTA\IMPROVEMENTS.md` + `IMPROVEMENTS.html` (the user's example).
- Starter: `AGENT-CONTEXT/templates/html-report-template.html` — copy it, keep the CSS, replace the body.

## Hard requirements

1. **One file, zero dependencies.** No CDN, no external fonts, no build step. Must open correctly from `file://`.
2. **Dark neumorphic theme** using the CSS variables at the top of the template (`--bg`, `--bg-raised`, `--bg-card`, per-severity colors). Do not invent a second palette.
3. **Hero** — eyebrow, title, one-paragraph lede, meta chips (date, commit, branch, version, phase).
4. **Stats row** — total + per-severity counts. Counts are **derived by JS from the DOM**; never hardcode numbers.
5. **Sticky filter bar** — All / High / Med-High / Medium / Low / Info + Collapse-all. Filtering hides items and empty batches.
6. **Collapsible groups** (`<details class="batch" data-batch="A">`) with letter badge, title, subtitle, live count, chevron.
7. **Item cards** (`<article class="item" data-sev="high|med-high|medium|low|info|large">`) with:
   - stable ID (`DL-1`, `NAV-3`, …)
   - short title
   - severity badge
   - optional evidence block with `file.kt:line-line`
   - Problem / Proposed fix / Verification paragraphs
8. **Panels** for verified facts, intentional behavior ("do not fix"), open decisions, suggested order.
9. **Footer** naming the source Markdown + the audit commit/date.
10. **Accessible + responsive** — native `<details>/<summary>`, real `<button>`s, semantic headings, visible focus, `prefers-reduced-motion`, mobile breakpoint.

## Severity vocabulary

| `data-sev` | Meaning | Treatment |
|-----------|---------|-----------|
| `high` | crash / data loss / security | red |
| `med-high` | user-visible wrong behavior | orange |
| `medium` | reliability or correctness debt | amber |
| `low` | cleanup / drift | green |
| `info` | informational | blue |
| `large` | large refactor (grouped with Info) | purple |

Keep the existing mapping in the JS (`SEV_GROUPS`) in sync if you add a level.

## Charts and diagrams

- Prefer **inline SVG** or **CSS bars generated from the DOM** — still zero dependencies.
- Charts must read from the same data as the cards (counts, severity, batch totals). No hand-maintained chart numbers.
- Give every chart a heading, units, and an accessible name (`role="img"` + `<title>`, or a visible caption).
- Use palette colors only; add a subtle glow, not gradients-on-gradients.
- Diagrams: top-down flows, 3–7 nodes, labelled arrows, no pseudo-3D. A small inline SVG in a panel is enough.
- Do not add Chart.js/D3/Mermaid CDNs. If an interactive chart is ever genuinely needed, ask the user first.

## Editing rules (keep it easy to change)

- **One item = one `<article>`.** Add/remove/reorder cards; counts and filters update themselves.
- **One group = one `<details>`.** Copy the whole block; change `data-batch` and the letter theme.
- **New batch color:** add `.batch[data-batch="J"]{--batch:#…;--batch-bg:#…;--batch-deep:#…}`.
- **Do not minify.** Keep one selector per line and section comments intact.
- **Never number items manually in prose** (no "57 items"); let the stats render it.
- When an item is fixed, mark it visually (e.g. add `data-sev="info"` + a "Resolved" badge) rather than deleting history, unless the user asks for removal.
- Update the Markdown first, then mirror the same wording into the HTML.

## Naming and locations

- In-repo reports follow CORE_RULES §21 zoning (`APP/ani-kuta/DOCUMENTATION/…`, `AGENT-CONTEXT/knowledge/…`).
- Session-level deliverables the user asked for at the workspace root stay at the workspace root (e.g. `IMPROVEMENTS.md` + `IMPROVEMENTS.html`).
- Never place report HTML in `DASHBOARD/webpage/` — that is the Next.js product, not a report.

## Pre-delivery checklist

- [ ] Opens offline from `file://` with no console errors.
- [ ] Stats match the number of `.item` cards.
- [ ] Every filter hides the right cards and empty batches.
- [ ] Collapse-all label flips correctly.
- [ ] Every item has an ID, severity, and a `data-sev` value the JS recognizes.
- [ ] Footer names the Markdown source + commit/date.
- [ ] Mobile width (≤640px) is readable; no horizontal scroll.
- [ ] The Markdown and HTML say the same thing.
