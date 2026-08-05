"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { Card } from "@/components/Card";
import {
  PHASE_C_HERO,
  TWO_ID_SYSTEM,
  CONTENT_ID_FORMAT,
  CONTENT_ID_SEGMENTS,
  CONTENT_ID_EXAMPLES,
  PHASE_C_TABLES,
  PHASE_C_GROUPS,
  PHASE_C_GROUP_COLOR,
  PHASE_C_GROUP_LABEL,
  ER_NODES,
  ER_EDGES,
  PHASE_C_DECISIONS,
  PHASE_C_MILESTONES,
  PHASE_C_DEFERRED,
  type PhaseCGroup,
  type PhaseCTable,
} from "@/lib/phaseC";

type FilterKey = "all" | PhaseCGroup;

/* ---------------------------------------------------------------------------
 * Filter pills config.
 * ------------------------------------------------------------------------- */
const FILTERS: { key: FilterKey; label: string; colorVar: string }[] = [
  { key: "all", label: "All", colorVar: "var(--c-primary)" },
  ...PHASE_C_GROUPS.map((g) => ({
    key: g.name as FilterKey,
    label: g.label,
    colorVar: g.color,
  })),
];

/* ---------------------------------------------------------------------------
 * Page
 * ------------------------------------------------------------------------- */
export default function PhaseCPage() {
  const [filter, setFilter] = useState<FilterKey>("all");

  const counts = useMemo(() => {
    const map: Record<string, number> = { all: PHASE_C_TABLES.length };
    for (const g of PHASE_C_GROUPS) {
      map[g.name] = PHASE_C_TABLES.filter((t) => t.group === g.name).length;
    }
    return map;
  }, []);

  const filtered = useMemo(() => {
    if (filter === "all") return PHASE_C_TABLES;
    return PHASE_C_TABLES.filter((t) => t.group === filter);
  }, [filter]);

  // Group filtered tables by their logical group (preserves group order).
  const grouped = useMemo(() => {
    const order: PhaseCGroup[] = PHASE_C_GROUPS.map((g) => g.name);
    const out: { group: PhaseCGroup; tables: PhaseCTable[] }[] = [];
    for (const g of order) {
      const tables = filtered.filter((t) => t.group === g);
      if (tables.length > 0) out.push({ group: g, tables });
    }
    return out;
  }, [filtered]);

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
      {/* ── Hero ── */}
      <section className="mb-10">
        <div className="flex items-center gap-3 mb-3">
          <span
            className="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold uppercase tracking-widest"
            style={{
              backgroundColor: `color-mix(in srgb, ${PHASE_C_HERO.statusColor} 15%, transparent)`,
              color: PHASE_C_HERO.statusColor,
            }}
          >
            <span
              className="inline-block h-1.5 w-1.5 rounded-full"
              style={{ backgroundColor: PHASE_C_HERO.statusColor }}
            />
            {PHASE_C_HERO.status}
          </span>
          <span className="text-xs text-[var(--c-text-secondary)]">
            Content Identity System
          </span>
        </div>
        <h1 className="text-3xl font-bold tracking-tight text-[var(--c-text-primary)] sm:text-4xl">
          {PHASE_C_HERO.title}
        </h1>
        <p className="mt-3 text-base text-[var(--c-text-secondary)] sm:text-lg">
          {PHASE_C_HERO.subtitle}
        </p>
        <p className="mt-4 max-w-3xl text-sm leading-relaxed text-[var(--c-text-secondary)]">
          {PHASE_C_HERO.summary}
        </p>
      </section>

      {/* ── Section 1: The Two-ID System ── */}
      <section className="mb-10">
        <h2 className="mb-4 text-xl font-bold tracking-tight text-[var(--c-text-primary)]">
          1. The Two-ID System
        </h2>
        <p className="mb-5 text-sm text-[var(--c-text-secondary)]">
          Both IDs live in the same <code className="rounded bg-[var(--c-surface-alt)] px-1.5 py-0.5 text-xs">content</code> table. The Main ID is the
          primary key (stable). The Content ID is a regular column (regenerated when sources change).
        </p>
        <div className="grid gap-4 md:grid-cols-2">
          {TWO_ID_SYSTEM.map((card) => (
            <Card key={card.key} className="p-5">
              <div className="mb-3 flex items-center justify-between">
                <h3 className="text-lg font-bold" style={{ color: card.color }}>
                  {card.title}
                </h3>
                <span className="text-xs font-medium text-[var(--c-text-secondary)]">
                  {card.tagline}
                </span>
              </div>
              <p className="mb-2 text-xs text-[var(--c-text-secondary)]">{card.format}</p>
              <code className="mb-4 block break-all rounded-lg bg-[var(--c-surface-alt)] px-3 py-2 text-xs text-[var(--c-text-primary)]">
                {card.formatMono}
              </code>
              <ul className="space-y-1.5">
                {card.bullets.map((b, i) => (
                  <li key={i} className="flex gap-2 text-xs text-[var(--c-text-secondary)]">
                    <span style={{ color: card.color }}>▸</span>
                    <span>{b}</span>
                  </li>
                ))}
              </ul>
              {card.notShownInUi && (
                <div className="mt-3 rounded-md bg-[var(--c-surface-alt)] px-2.5 py-1.5 text-xs text-[var(--c-text-secondary)]">
                  🔒 Not shown in UI — internal tracking only
                </div>
              )}
            </Card>
          ))}
        </div>
      </section>

      {/* ── Section 2: Content ID Format ── */}
      <section className="mb-10">
        <h2 className="mb-4 text-xl font-bold tracking-tight text-[var(--c-text-primary)]">
          2. Content ID Format
        </h2>
        <p className="mb-4 text-sm text-[var(--c-text-secondary)]">
          6 sections, colon-delimited. Deterministic — same inputs always produce the same ID.
        </p>
        <code className="mb-5 block break-all rounded-lg bg-[var(--c-surface-alt)] px-4 py-3 text-sm text-[var(--c-text-primary)]">
          {CONTENT_ID_FORMAT}
        </code>

        {/* Segment breakdown */}
        <div className="mb-6 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {CONTENT_ID_SEGMENTS.map((seg) => (
            <div
              key={seg.index}
              className="rounded-lg border border-[var(--c-border)] p-3"
            >
              <div className="mb-1 flex items-center gap-2">
                <span className="flex h-5 w-5 items-center justify-center rounded-full bg-[var(--c-primary)] text-xs font-bold text-white">
                  {seg.index}
                </span>
                <code className="text-xs font-bold text-[var(--c-text-primary)]">
                  {seg.key}
                </code>
              </div>
              <p className="text-xs text-[var(--c-text-secondary)]">{seg.description}</p>
              <p className="mt-1 text-xs">
                <span className="text-[var(--c-text-secondary)]">Example: </span>
                <code className="text-[var(--c-text-primary)]">{seg.example}</code>
              </p>
            </div>
          ))}
        </div>

        {/* Examples */}
        <h3 className="mb-2 text-sm font-bold text-[var(--c-text-primary)]">
          Example Content IDs
        </h3>
        <div className="space-y-2">
          {CONTENT_ID_EXAMPLES.map((ex) => (
            <div
              key={ex.id}
              className="rounded-lg border border-[var(--c-border)] p-3"
            >
              <code className="block break-all text-xs text-[var(--c-text-primary)]">
                {ex.parts.join(":")}
              </code>
              <p className="mt-1.5 text-xs text-[var(--c-text-secondary)]">{ex.note}</p>
            </div>
          ))}
        </div>
      </section>

      {/* ── Section 3: Database Schema ── */}
      <section className="mb-10">
        <h2 className="mb-4 text-xl font-bold tracking-tight text-[var(--c-text-primary)]">
          3. Database Schema
        </h2>
        <p className="mb-4 text-sm text-[var(--c-text-secondary)]">
          {PHASE_C_TABLES.length} tables across {PHASE_C_GROUPS.length} groups. One table per row.
        </p>

        {/* Filter pills */}
        <div className="mb-6 flex flex-wrap gap-2">
          {FILTERS.map((f) => {
            const active = filter === f.key;
            const count = counts[f.key] ?? 0;
            return (
              <button
                key={f.key}
                onClick={() => setFilter(f.key)}
                className="inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-xs font-medium transition-colors"
                style={{
                  backgroundColor: active
                    ? f.colorVar
                    : "var(--c-surface-alt)",
                  color: active ? "white" : "var(--c-text-secondary)",
                }}
              >
                {f.label}
                <span
                  className="rounded-full px-1.5 py-0.5 text-[10px] font-bold"
                  style={{
                    backgroundColor: active
                      ? "rgba(255,255,255,0.25)"
                      : "var(--c-border)",
                  }}
                >
                  {count}
                </span>
              </button>
            );
          })}
        </div>

        {/* Tables — ONE PER ROW */}
        <div className="space-y-6">
          {grouped.map(({ group, tables }) => (
            <div key={group}>
              {/* Group header */}
              <div className="mb-3 flex items-center gap-2">
                <span
                  className="inline-block h-3 w-3 rounded-full"
                  style={{ backgroundColor: PHASE_C_GROUP_COLOR[group] }}
                />
                <h3 className="text-sm font-bold text-[var(--c-text-primary)]">
                  {PHASE_C_GROUP_LABEL[group]}
                </h3>
                <span className="text-xs text-[var(--c-text-secondary)]">
                  ({tables.length} table{tables.length > 1 ? "s" : ""})
                </span>
              </div>

              {/* Tables — each takes full width */}
              <div className="space-y-4">
                {tables.map((table) => (
                  <TableCard key={table.name} table={table} />
                ))}
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* ── Section 4: ER Diagram ── */}
      <section className="mb-10">
        <h2 className="mb-4 text-xl font-bold tracking-tight text-[var(--c-text-primary)]">
          4. Entity Relationships
        </h2>
        <Card className="p-6">
          <ERDiagram />
        </Card>
      </section>

      {/* ── Section 5: Confirmed Decisions ── */}
      <section className="mb-10">
        <h2 className="mb-4 text-xl font-bold tracking-tight text-[var(--c-text-primary)]">
          5. Confirmed Decisions
        </h2>
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)]">
                <tr>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">#</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Question</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Answer</th>
                </tr>
              </thead>
              <tbody>
                {PHASE_C_DECISIONS.map((d) => (
                  <tr
                    key={d.id}
                    className="border-b border-[var(--c-border)] last:border-0"
                  >
                    <td className="px-4 py-2.5 font-mono font-bold text-[var(--c-primary)]">
                      {d.id}
                    </td>
                    <td className="px-4 py-2.5 text-[var(--c-text-primary)]">
                      {d.question}
                    </td>
                    <td className="px-4 py-2.5 text-[var(--c-text-secondary)]">
                      {d.answer}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      </section>

      {/* ── Section 6: Implementation Phases ── */}
      <section className="mb-10">
        <h2 className="mb-4 text-xl font-bold tracking-tight text-[var(--c-text-primary)]">
          6. Implementation Phases (This Session)
        </h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {PHASE_C_MILESTONES.map((m) => (
            <Card key={m.id} className="p-4">
              <div className="mb-2 flex items-center justify-between">
                <span className="rounded-md bg-[var(--c-primary)] px-2 py-0.5 text-xs font-bold text-white">
                  {m.id}
                </span>
                <span className="text-xs text-[var(--c-text-secondary)] capitalize">
                  {m.status}
                </span>
              </div>
              <h3 className="mb-1 text-sm font-bold text-[var(--c-text-primary)]">
                {m.title}
              </h3>
              <p className="text-xs text-[var(--c-text-secondary)]">{m.description}</p>
            </Card>
          ))}
        </div>
      </section>

      {/* ── Section 7: Deferred ── */}
      <section className="mb-10">
        <h2 className="mb-4 text-xl font-bold tracking-tight text-[var(--c-text-primary)]">
          7. Deferred to Later Sessions
        </h2>
        <Card className="p-5">
          <p className="mb-3 text-sm text-[var(--c-text-secondary)]">
            These features are NOT in this session's scope. They'll be built in future
            sessions after the content identity system is stable.
          </p>
          <ul className="space-y-1.5">
            {PHASE_C_DEFERRED.map((d, i) => (
              <li key={i} className="flex gap-2 text-xs text-[var(--c-text-secondary)]">
                <span className="text-[var(--c-text-secondary)]">○</span>
                <span>{d}</span>
              </li>
            ))}
          </ul>
        </Card>
      </section>

      {/* ── Footer nav ── */}
      <div className="mt-12 flex justify-between border-t border-[var(--c-border)] pt-6">
        <Link
          href="/planning/"
          className="text-xs text-[var(--c-text-secondary)] hover:text-[var(--c-primary)]"
        >
          ← Planning
        </Link>
        <Link
          href="/database/"
          className="text-xs text-[var(--c-text-secondary)] hover:text-[var(--c-primary)]"
        >
          Database →
        </Link>
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * TableCard — renders ONE table (full width).
 * ------------------------------------------------------------------------- */
function TableCard({ table }: { table: PhaseCTable }) {
  const groupColor = PHASE_C_GROUP_COLOR[table.group];
  return (
    <Card className="overflow-hidden p-0">
      {/* Header */}
      <div className="flex items-center gap-2 border-b border-[var(--c-border)] px-4 py-3">
        <span
          className="inline-block h-2.5 w-2.5 rounded-full"
          style={{ backgroundColor: groupColor }}
        />
        <code className="text-sm font-bold text-[var(--c-text-primary)]">{table.name}</code>
        {table.isMain && (
          <span className="rounded-md bg-[var(--c-primary)] px-2 py-0.5 text-[10px] font-bold uppercase text-white">
            Main
          </span>
        )}
        {table.isNew && (
          <span className="rounded-md bg-[var(--c-success)] px-2 py-0.5 text-[10px] font-bold uppercase text-white">
            New
          </span>
        )}
      </div>

      {/* Description */}
      <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2">
        <p className="text-xs text-[var(--c-text-secondary)]">{table.description}</p>
      </div>

      {/* Schema table */}
      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs">
          <thead className="border-b border-[var(--c-border)]">
            <tr className="bg-[var(--c-surface)]">
              <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Column</th>
              <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Type</th>
              <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Constraints</th>
              <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Description</th>
            </tr>
          </thead>
          <tbody>
            {table.columns.map((col) => (
              <tr
                key={col.name}
                className="border-b border-[var(--c-border)] last:border-0"
              >
                <td className="px-4 py-2">
                  <code className="font-bold text-[var(--c-text-primary)]">{col.name}</code>
                </td>
                <td className="px-4 py-2 font-mono text-[var(--c-text-secondary)]">
                  {col.type}
                </td>
                <td className="px-4 py-2 font-mono text-[var(--c-text-secondary)]">
                  {col.constraints || "—"}
                </td>
                <td className="px-4 py-2 text-[var(--c-text-secondary)]">
                  {col.description || "—"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Demo rows */}
      {table.demoRows.length > 0 && (
        <div className="border-t border-[var(--c-border)]">
          <div className="bg-[var(--c-surface-alt)] px-4 py-1.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Demo Rows
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <tbody>
                {table.demoRows.map((row, i) => (
                  <tr
                    key={i}
                    className="border-b border-[var(--c-border)] last:border-0"
                  >
                    {row.map((cell, j) => (
                      <td
                        key={j}
                        className="px-4 py-1.5 font-mono text-[var(--c-text-secondary)]"
                      >
                        {cell}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </Card>
  );
}

/* ---------------------------------------------------------------------------
 * ERDiagram — simple SVG visualization.
 * ------------------------------------------------------------------------- */
function ERDiagram() {
  const nodeColor: Record<PhaseCGroup, string> = {
    lookup: "#0EA5E9",
    main: "#6366F1",
    detail: "#8B5CF6",
  };

  // Simple layout: lookup on left, main in center, detail on right.
  const positions: Record<string, { x: number; y: number }> = {
    data_sources: { x: 20, y: 30 },
    systems: { x: 20, y: 110 },
    extension_repos: { x: 20, y: 190 },
    extensions: { x: 20, y: 270 },
    content: { x: 320, y: 150 },
    anilist_details: { x: 600, y: 60 },
    extension_details: { x: 600, y: 150 },
    other_source_details: { x: 600, y: 240 },
  };

  return (
    <div className="overflow-x-auto">
      <svg width="780" height="340" className="min-w-[780px]">
        {/* Edges */}
        {ER_EDGES.map((edge, i) => {
          const from = positions[edge.from];
          const to = positions[edge.to];
          if (!from || !to) return null;
          return (
            <g key={i}>
              <line
                x1={from.x + 120}
                y1={from.y + 20}
                x2={to.x}
                y2={to.y + 20}
                stroke="var(--c-border)"
                strokeWidth="1.5"
              />
              <text
                x={(from.x + 120 + to.x) / 2}
                y={(from.y + 20 + to.y + 20) / 2 - 4}
                fill="var(--c-text-secondary)"
                fontSize="9"
                textAnchor="middle"
              >
                {edge.label}
              </text>
            </g>
          );
        })}

        {/* Nodes */}
        {ER_NODES.map((node) => {
          const pos = positions[node.id];
          if (!pos) return null;
          const color = nodeColor[node.group];
          return (
            <g key={node.id}>
              <rect
                x={pos.x}
                y={pos.y}
                width="120"
                height="40"
                rx="8"
                fill={node.isMain ? color : `color-mix(in srgb, ${color} 15%, var(--c-surface))`}
                stroke={color}
                strokeWidth={node.isMain ? "2.5" : "1.5"}
              />
              <text
                x={pos.x + 60}
                y={pos.y + 25}
                fill={node.isMain ? "white" : color}
                fontSize="11"
                fontWeight="bold"
                textAnchor="middle"
              >
                {node.label}
              </text>
            </g>
          );
        })}
      </svg>

      {/* Legend */}
      <div className="mt-4 flex flex-wrap gap-4 text-xs">
        {PHASE_C_GROUPS.map((g) => (
          <div key={g.name} className="flex items-center gap-1.5">
            <span
              className="inline-block h-3 w-3 rounded"
              style={{ backgroundColor: nodeColor[g.name] }}
            />
            <span className="text-[var(--c-text-secondary)]">{g.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
