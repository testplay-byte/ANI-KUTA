"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { Card } from "@/components/Card";
import {
  PHASE_D_HERO,
  PROBLEM_STATEMENT,
  GOALS,
  PHASE_D_TABLES,
  PHASE_D_GROUPS,
  PHASE_D_GROUP_COLOR,
  PHASE_D_GROUP_LABEL,
  ER_NODES,
  ER_EDGES,
  REFRESH_STRATEGY,
  PHASE_D_DECISIONS,
  PHASE_D_ADDITIONAL_DECISIONS,
  PHASE_D_MILESTONES,
  PHASE_D_FUTURE,
  type PhaseDGroup,
  type PhaseDTable,
  type ERGroup,
} from "@/lib/phaseD";

type FilterKey = "all" | PhaseDGroup;

/* ---------------------------------------------------------------------------
 * Filter pills config.
 * ------------------------------------------------------------------------- */
const FILTERS: { key: FilterKey; label: string; colorVar: string }[] = [
  { key: "all", label: "All", colorVar: "var(--c-primary)" },
  ...PHASE_D_GROUPS.map((g) => ({
    key: g.name as FilterKey,
    label: g.label,
    colorVar: g.color,
  })),
];

/* ---------------------------------------------------------------------------
 * Page
 * ------------------------------------------------------------------------- */
export default function PhaseDPage() {
  const [filter, setFilter] = useState<FilterKey>("all");

  const counts = useMemo(() => {
    const map: Record<string, number> = { all: PHASE_D_TABLES.length };
    for (const g of PHASE_D_GROUPS) {
      map[g.name] = PHASE_D_TABLES.filter((t) => t.group === g.name).length;
    }
    return map;
  }, []);

  const filtered = useMemo(() => {
    if (filter === "all") return PHASE_D_TABLES;
    return PHASE_D_TABLES.filter((t) => t.group === filter);
  }, [filter]);

  // Group filtered tables by their logical group (preserves group order).
  const grouped = useMemo(() => {
    const order: PhaseDGroup[] = PHASE_D_GROUPS.map((g) => g.name);
    const out: { group: PhaseDGroup; tables: PhaseDTable[] }[] = [];
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
              backgroundColor: `color-mix(in srgb, ${PHASE_D_HERO.statusColor} 15%, transparent)`,
              color: PHASE_D_HERO.statusColor,
            }}
          >
            <span
              className="inline-block h-1.5 w-1.5 rounded-full"
              style={{ backgroundColor: PHASE_D_HERO.statusColor }}
            />
            {PHASE_D_HERO.status}
          </span>
          <span className="text-xs text-[var(--c-text-secondary)]">
            Data Management &amp; Caching
          </span>
        </div>
        <h1 className="text-3xl font-bold tracking-tight text-[var(--c-text-primary)] sm:text-4xl">
          {PHASE_D_HERO.title}
        </h1>
        <p className="mt-3 text-base text-[var(--c-text-secondary)] sm:text-lg">
          {PHASE_D_HERO.subtitle}
        </p>
        <p className="mt-4 max-w-3xl text-sm leading-relaxed text-[var(--c-text-secondary)]">
          {PHASE_D_HERO.summary}
        </p>
      </section>

      {/* ── Section 1: Problem Statement ── */}
      <section className="mb-10">
        <h2 className="mb-2 text-xl font-bold tracking-tight text-[var(--c-text-primary)]">
          1. Problem Statement
        </h2>
        <p className="mb-5 text-sm text-[var(--c-text-secondary)]">
          The app currently fetches data from the network on EVERY screen load.
          Browse, details, library, and episode metadata all trigger fresh
          network calls — even when the data hasn&apos;t changed. This causes
          the four issues below.
        </p>
        <div className="grid gap-4 sm:grid-cols-2">
          {PROBLEM_STATEMENT.map((card) => (
            <Card key={card.key} className="p-5">
              <div className="mb-3 flex items-start gap-3">
                <span
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-lg"
                  style={{
                    backgroundColor: `color-mix(in srgb, ${card.color} 15%, transparent)`,
                  }}
                >
                  {card.icon}
                </span>
                <div className="min-w-0">
                  <h3
                    className="text-base font-bold leading-tight"
                    style={{ color: card.color }}
                  >
                    {card.title}
                  </h3>
                  <p className="mt-0.5 text-xs font-medium text-[var(--c-text-secondary)]">
                    {card.impact}
                  </p>
                </div>
              </div>
              <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">
                {card.description}
              </p>
            </Card>
          ))}
        </div>
      </section>

      {/* ── Section 2: Goals ── */}
      <section className="mb-10">
        <h2 className="mb-2 text-xl font-bold tracking-tight text-[var(--c-text-primary)]">
          2. Goals
        </h2>
        <p className="mb-5 text-sm text-[var(--c-text-secondary)]">
          Six concrete goals for Phase D — local-first storage, smart refresh,
          image caching, solid caching, performance, and proper handling of two
          source types.
        </p>
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {GOALS.map((goal) => (
            <Card key={goal.key} className="flex flex-col p-5">
              <div className="mb-3 flex items-start justify-between">
                <span
                  className="flex h-8 w-8 items-center justify-center rounded-lg text-sm font-bold text-white"
                  style={{ backgroundColor: goal.color }}
                >
                  {goal.number}
                </span>
                <span className="text-xs font-medium text-[var(--c-text-secondary)]">
                  {goal.tagline}
                </span>
              </div>
              <h3
                className="mb-2 text-base font-bold leading-tight"
                style={{ color: goal.color }}
              >
                {goal.title}
              </h3>
              <p className="mb-3 text-xs leading-relaxed text-[var(--c-text-secondary)]">
                {goal.description}
              </p>
              <ul className="mt-auto space-y-1.5">
                {goal.bullets.map((b, i) => (
                  <li
                    key={i}
                    className="flex gap-2 text-xs text-[var(--c-text-secondary)]"
                  >
                    <span style={{ color: goal.color }}>▸</span>
                    <span>{b}</span>
                  </li>
                ))}
              </ul>
            </Card>
          ))}
        </div>
      </section>

      {/* ── Section 3: Database Schema ── */}
      <section className="mb-10">
        <h2 className="mb-2 text-xl font-bold tracking-tight text-[var(--c-text-primary)]">
          3. Database Schema
        </h2>
        <p className="mb-4 text-sm text-[var(--c-text-secondary)]">
          {PHASE_D_TABLES.length} new tables across {PHASE_D_GROUPS.length}{" "}
          groups. One table per row.
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
                  style={{ backgroundColor: PHASE_D_GROUP_COLOR[group] }}
                />
                <h3 className="text-sm font-bold text-[var(--c-text-primary)]">
                  {PHASE_D_GROUP_LABEL[group]}
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

        {/* ER diagram (inside Section 3) */}
        <div className="mt-8">
          <h3 className="mb-3 text-base font-bold text-[var(--c-text-primary)]">
            Entity Relationships
          </h3>
          <Card className="p-6">
            <ERDiagram />
          </Card>
        </div>
      </section>

      {/* ── Section 4: Refresh Strategy ── */}
      <section className="mb-10">
        <h2 className="mb-2 text-xl font-bold tracking-tight text-[var(--c-text-primary)]">
          4. Refresh Strategy
        </h2>
        <p className="mb-5 text-sm text-[var(--c-text-secondary)]">
          Three pages, three strategies. The browse page uses pull-to-refresh +
          6-hour auto-update (homepage only). The details page uses a
          multi-stage refresh (episodes → metadata → all) with vibration. The
          library page loads entirely from cache.
        </p>
        <div className="grid gap-4 lg:grid-cols-3">
          {REFRESH_STRATEGY.map((card) => (
            <Card key={card.key} className="flex flex-col p-5">
              <div className="mb-3 flex items-start gap-3">
                <span
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-lg"
                  style={{
                    backgroundColor: `color-mix(in srgb, ${card.color} 15%, transparent)`,
                  }}
                >
                  {card.icon}
                </span>
                <div className="min-w-0">
                  <h3
                    className="text-sm font-bold leading-tight"
                    style={{ color: card.color }}
                  >
                    {card.page}
                  </h3>
                  <p className="mt-0.5 text-xs text-[var(--c-text-secondary)]">
                    {card.tagline}
                  </p>
                </div>
              </div>
              <ul className="mb-3 space-y-2">
                {card.triggers.map((t, i) => (
                  <li
                    key={i}
                    className="flex gap-2 text-xs leading-relaxed text-[var(--c-text-secondary)]"
                  >
                    <span style={{ color: card.color }}>▸</span>
                    <span>{t}</span>
                  </li>
                ))}
              </ul>
              <div
                className="mt-auto rounded-md px-2.5 py-1.5 text-xs text-[var(--c-text-secondary)]"
                style={{
                  backgroundColor: `color-mix(in srgb, ${card.color} 8%, var(--c-surface-alt))`,
                }}
              >
                <span className="font-medium">Note:</span> {card.notes}
              </div>
            </Card>
          ))}
        </div>
      </section>

      {/* ── Section 5: Confirmed Decisions ── */}
      <section className="mb-10">
        <h2 className="mb-2 text-xl font-bold tracking-tight text-[var(--c-text-primary)]">
          5. Confirmed Decisions
        </h2>
        <p className="mb-4 text-sm text-[var(--c-text-secondary)]">
          Q-001 through Q-005, plus five additional confirmed decisions.
        </p>
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)]">
                <tr>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">
                    #
                  </th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">
                    Question
                  </th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">
                    Answer
                  </th>
                </tr>
              </thead>
              <tbody>
                {PHASE_D_DECISIONS.map((d) => (
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

        {/* Additional decisions */}
        <Card className="mt-4 p-5">
          <h3 className="mb-3 text-sm font-bold text-[var(--c-text-primary)]">
            Additional Confirmed Decisions
          </h3>
          <ul className="space-y-1.5">
            {PHASE_D_ADDITIONAL_DECISIONS.map((d, i) => (
              <li
                key={i}
                className="flex gap-2 text-xs text-[var(--c-text-secondary)]"
              >
                <span className="text-[var(--c-primary)]">✓</span>
                <span>{d}</span>
              </li>
            ))}
          </ul>
        </Card>
      </section>

      {/* ── Section 6: Implementation Phases ── */}
      <section className="mb-10">
        <h2 className="mb-2 text-xl font-bold tracking-tight text-[var(--c-text-primary)]">
          6. Implementation Phases
        </h2>
        <p className="mb-5 text-sm text-[var(--c-text-secondary)]">
          Five milestones (D.1–D.5) to be implemented in the next session. Each
          builds on the previous one — D.1 (metadata cache) is foundational.
        </p>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {PHASE_D_MILESTONES.map((m) => (
            <Card key={m.id} className="flex flex-col p-4">
              <div className="mb-2 flex items-center justify-between">
                <span className="rounded-md bg-[var(--c-primary)] px-2 py-0.5 text-xs font-bold text-white">
                  {m.id}
                </span>
                <span className="text-xs text-[var(--c-text-secondary)] capitalize">
                  {m.status}
                </span>
              </div>
              <h3 className="mb-2 text-sm font-bold text-[var(--c-text-primary)]">
                {m.title}
              </h3>
              <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">
                {m.description}
              </p>
            </Card>
          ))}
        </div>
      </section>

      {/* ── Section 7: Future Considerations (NOT in Phase D) ── */}
      <section className="mb-10">
        <h2 className="mb-2 text-xl font-bold tracking-tight text-[var(--c-text-primary)]">
          7. Future Considerations{" "}
          <span className="text-sm font-medium text-[var(--c-text-secondary)]">
            (NOT in Phase D)
          </span>
        </h2>
        <p className="mb-5 text-sm text-[var(--c-text-secondary)]">
          These features are explicitly out of Phase D&apos;s scope. They&apos;ll
          be revisited in future phases once the data management + caching
          foundation is solid.
        </p>
        <div className="grid gap-3 sm:grid-cols-2">
          {PHASE_D_FUTURE.map((f) => (
            <Card key={f.title} className="p-4">
              <div className="mb-1.5 flex items-center gap-2">
                <span className="text-[var(--c-text-secondary)]">○</span>
                <h3 className="text-sm font-bold text-[var(--c-text-primary)]">
                  {f.title}
                </h3>
              </div>
              <p className="pl-5 text-xs leading-relaxed text-[var(--c-text-secondary)]">
                {f.description}
              </p>
            </Card>
          ))}
        </div>
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
function TableCard({ table }: { table: PhaseDTable }) {
  const groupColor = PHASE_D_GROUP_COLOR[table.group];
  return (
    <Card className="overflow-hidden p-0">
      {/* Header */}
      <div className="flex items-center gap-2 border-b border-[var(--c-border)] px-4 py-3">
        <span
          className="inline-block h-2.5 w-2.5 rounded-full"
          style={{ backgroundColor: groupColor }}
        />
        <code className="text-sm font-bold text-[var(--c-text-primary)]">
          {table.name}
        </code>
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
        {table.compositePK && (
          <span className="rounded-md bg-[var(--c-secondary)] px-2 py-0.5 text-[10px] font-bold uppercase text-white">
            Composite PK
          </span>
        )}
      </div>

      {/* Description */}
      <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2">
        <p className="text-xs text-[var(--c-text-secondary)]">
          {table.description}
        </p>
      </div>

      {/* Schema table */}
      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs">
          <thead className="border-b border-[var(--c-border)]">
            <tr className="bg-[var(--c-surface)]">
              <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">
                Column
              </th>
              <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">
                Type
              </th>
              <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">
                Constraints
              </th>
              <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">
                Description
              </th>
            </tr>
          </thead>
          <tbody>
            {table.columns.map((col) => (
              <tr
                key={col.name}
                className="border-b border-[var(--c-border)] last:border-0"
              >
                <td className="px-4 py-2">
                  <code className="font-bold text-[var(--c-text-primary)]">
                    {col.name}
                  </code>
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
  const nodeColor: Record<ERGroup, string> = {
    content: "#6366F1",
    metadata: "#8B5CF6",
    browse: "#F59E0B",
  };

  // Layout: content on left center, metadata caches stacked middle, browse standalone right.
  const positions: Record<string, { x: number; y: number }> = {
    content: { x: 40, y: 130 },
    anime_metadata_cache: { x: 280, y: 60 },
    episode_metadata_cache: { x: 280, y: 200 },
    browse_cache: { x: 560, y: 130 },
  };

  const nodeWidth = 200;
  const nodeHeight = 50;

  return (
    <div className="overflow-x-auto">
      <svg
        width="800"
        height="320"
        className="min-w-[800px]"
        viewBox="0 0 800 320"
      >
        {/* Edges */}
        {ER_EDGES.map((edge, i) => {
          const from = positions[edge.from];
          const to = positions[edge.to];
          if (!from || !to) return null;
          return (
            <g key={i}>
              <line
                x1={from.x + nodeWidth}
                y1={from.y + nodeHeight / 2}
                x2={to.x}
                y2={to.y + nodeHeight / 2}
                stroke="var(--c-border)"
                strokeWidth="1.5"
              />
              <text
                x={(from.x + nodeWidth + to.x) / 2}
                y={(from.y + nodeHeight / 2 + to.y + nodeHeight / 2) / 2 - 6}
                fill="var(--c-text-secondary)"
                fontSize="10"
                textAnchor="middle"
              >
                {edge.label}
              </text>
            </g>
          );
        })}

        {/* Standalone note for browse_cache (no FK) */}
        <text
          x={positions.browse_cache.x + nodeWidth / 2}
          y={positions.browse_cache.y - 10}
          fill="var(--c-text-secondary)"
          fontSize="10"
          textAnchor="middle"
          fontStyle="italic"
        >
          standalone (no FK — keyed by section_key)
        </text>

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
                width={nodeWidth}
                height={nodeHeight}
                rx="8"
                fill={
                  node.isMain
                    ? color
                    : `color-mix(in srgb, ${color} 15%, var(--c-surface))`
                }
                stroke={color}
                strokeWidth={node.isMain ? "2.5" : "1.5"}
              />
              <text
                x={pos.x + nodeWidth / 2}
                y={pos.y + nodeHeight / 2 + 4}
                fill={node.isMain ? "white" : color}
                fontSize="12"
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
        <div className="flex items-center gap-1.5">
          <span
            className="inline-block h-3 w-3 rounded"
            style={{ backgroundColor: nodeColor.content }}
          />
          <span className="text-[var(--c-text-secondary)]">
            Existing table (Phase C)
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          <span
            className="inline-block h-3 w-3 rounded"
            style={{ backgroundColor: nodeColor.metadata }}
          />
          <span className="text-[var(--c-text-secondary)]">
            Metadata cache (never expires)
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          <span
            className="inline-block h-3 w-3 rounded"
            style={{ backgroundColor: nodeColor.browse }}
          />
          <span className="text-[var(--c-text-secondary)]">
            Browse cache (expires 6h, homepage only)
          </span>
        </div>
      </div>
    </div>
  );
}
