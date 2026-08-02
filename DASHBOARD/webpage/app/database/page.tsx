"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import {
  SCHEMA_TABLES,
  SCHEMA_GROUPS,
  SCHEMA_SUMMARY,
  ER_NODES,
  ER_EDGES,
  type SchemaGroup,
  type SchemaTable,
  type ERNode,
} from "@/lib/schema";

type FilterKey = "all" | SchemaGroup | "Deferred";

/* ---------------------------------------------------------------------------
 * Filter config — pills at the top.
 * ------------------------------------------------------------------------- */
const FILTERS: { key: FilterKey; label: string; colorVar: string }[] = [
  { key: "all", label: "All", colorVar: "var(--c-primary)" },
  { key: "Identity", label: "Identity", colorVar: "var(--c-primary)" },
  { key: "Library", label: "Library", colorVar: "var(--c-success)" },
  { key: "Watch", label: "Watch", colorVar: "var(--c-warning)" },
  { key: "Downloads", label: "Downloads", colorVar: "var(--c-secondary)" },
  { key: "Trackers", label: "Trackers", colorVar: "var(--c-danger)" },
  { key: "Extensions", label: "Extensions", colorVar: "#0EA5E9" },
  { key: "Metadata", label: "Metadata", colorVar: "#EC4899" },
  { key: "App", label: "App", colorVar: "#22C55E" },
  { key: "Deferred", label: "Deferred", colorVar: "#A8A29E" },
];

/* ---------------------------------------------------------------------------
 * Group color lookup (for table-card header dot).
 * ------------------------------------------------------------------------- */
const GROUP_COLOR: Record<SchemaGroup, string> = SCHEMA_GROUPS.reduce(
  (acc, g) => ({ ...acc, [g.name]: g.color }),
  {} as Record<SchemaGroup, string>,
);

const GROUP_LABEL: Record<SchemaGroup, string> = SCHEMA_GROUPS.reduce(
  (acc, g) => ({ ...acc, [g.name]: g.label }),
  {} as Record<SchemaGroup, string>,
);

/* ---------------------------------------------------------------------------
 * Page
 * ------------------------------------------------------------------------- */
export default function DatabasePage() {
  const [filter, setFilter] = useState<FilterKey>("all");

  const counts = useMemo(() => {
    const map: Record<string, number> = { all: SCHEMA_TABLES.length };
    for (const g of SCHEMA_GROUPS) {
      map[g.name] = SCHEMA_TABLES.filter((t) => t.group === g.name).length;
    }
    map["Deferred"] = SCHEMA_TABLES.filter((t) => t.deferred).length;
    return map;
  }, []);

  const filtered = useMemo(() => {
    if (filter === "all") return SCHEMA_TABLES;
    if (filter === "Deferred") return SCHEMA_TABLES.filter((t) => t.deferred);
    return SCHEMA_TABLES.filter((t) => t.group === filter);
  }, [filter]);

  // Group filtered tables by their logical group (preserves order).
  const grouped = useMemo(() => {
    const order: SchemaGroup[] = SCHEMA_GROUPS.map((g) => g.name);
    const out: { group: SchemaGroup; tables: SchemaTable[] }[] = [];
    for (const g of order) {
      const tables = filtered.filter((t) => t.group === g);
      if (tables.length > 0) out.push({ group: g, tables });
    }
    return out;
  }, [filtered]);

  return (
    <div className="space-y-6">
      {/* ---- Hero / summary card ---- */}
      <Card className="!p-6 md:!p-8">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[11px] font-medium uppercase tracking-widest text-text-secondary">
              Phase 3 Foundation
            </span>
            <StatusDot color="var(--c-primary)" size="sm" />
            <span className="text-[12px] text-text-secondary">
              SQLDelight schema · the engine room every Phase 3+ module depends on
            </span>
          </div>
          <h2 className="text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
            Database Schema{" "}
            <span className="text-text-secondary font-medium">
              — 21 tables, 10 groups
            </span>
          </h2>
          <p className="text-[13.5px] text-text-secondary leading-relaxed max-w-2xl">
            The complete SQL schema for the ANI-KUTA app — ContentUID backbone,
            library + watch + downloads + trackers + extensions + metadata +
            app_metadata, plus 2 deferred (activity + ads). Documented in{" "}
            <code className="font-mono text-text-primary">
              17-database-schema.md
            </code>
            . Filter by group or focus on the deferred tables.
          </p>

          {/* Summary stat strip */}
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 gap-2 pt-1">
            <SummaryStat
              label="Tables"
              value={String(SCHEMA_SUMMARY.totalTables)}
              accent="var(--c-primary)"
            />
            <SummaryStat
              label="Active"
              value={String(SCHEMA_SUMMARY.activeTables)}
              accent="var(--c-success)"
            />
            <SummaryStat
              label="Deferred"
              value={String(SCHEMA_SUMMARY.deferredTables)}
              accent="var(--c-text-secondary)"
            />
            <SummaryStat
              label="Groups"
              value={String(SCHEMA_SUMMARY.totalGroups)}
              accent="var(--c-warning)"
            />
            <SummaryStat
              label="Columns"
              value={String(SCHEMA_SUMMARY.totalColumns)}
              accent="var(--c-secondary)"
            />
            <SummaryStat
              label="Indexes"
              value={String(SCHEMA_SUMMARY.totalIndexes)}
              accent="var(--c-danger)"
            />
          </div>
        </div>
      </Card>

      {/* ---- ER diagram ---- */}
      <Card>
        <div className="flex items-start justify-between gap-3 mb-3 flex-wrap">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Entity Relationships
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              How the tables connect
            </h3>
          </div>
          <span className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium bg-chip border border-border text-text-secondary">
            <StatusDot color="var(--c-primary)" size="sm" />
            {ER_NODES.length} entities · {ER_EDGES.length} edges
          </span>
        </div>
        <p className="text-[12.5px] text-text-secondary leading-relaxed mb-4">
          <code className="font-mono text-text-primary">content_uid</code> is the
          backbone — every watch, download, tracker link, and metadata entry
          hangs off it.{" "}
          <code className="font-mono text-text-primary">episode_uid</code>{" "}
          branches from content for per-episode progress, downloads, and history.
        </p>
        <ERDiagram />
        <div className="mt-4 flex flex-wrap gap-3">
          {SCHEMA_GROUPS.map((g) => (
            <div
              key={g.name}
              className="inline-flex items-center gap-1.5 text-[11px] text-text-secondary"
            >
              <span
                className="w-2 h-2 rounded-full"
                style={{ backgroundColor: g.color }}
                aria-hidden="true"
              />
              <span>{g.label.replace(" (deferred)", "")}</span>
            </div>
          ))}
        </div>
      </Card>

      {/* ---- Filter pills ---- */}
      <Card className="!p-3.5">
        <div className="flex flex-wrap items-center gap-1.5">
          {FILTERS.map((f) => {
            const active = filter === f.key;
            const count = counts[f.key] ?? 0;
            return (
              <button
                key={f.key}
                type="button"
                onClick={() => setFilter(f.key)}
                className="h-8 px-3 rounded-[12px] text-[12.5px] font-medium transition-all duration-200 flex items-center gap-2 border"
                style={
                  active
                    ? {
                        backgroundColor: f.colorVar,
                        borderColor: f.colorVar,
                        color: "#fff",
                        boxShadow: `0 4px 12px ${f.colorVar}33`,
                      }
                    : {
                        backgroundColor: "transparent",
                        borderColor: "var(--c-border)",
                        color: "var(--c-text-secondary)",
                      }
                }
              >
                {f.label}
                <span
                  className={`text-[11px] tabular-nums ${
                    active ? "opacity-80" : "opacity-60"
                  }`}
                >
                  {count}
                </span>
              </button>
            );
          })}
        </div>
      </Card>

      {/* ---- Tables (grouped) ---- */}
      {grouped.map(({ group, tables }) => {
        const meta = SCHEMA_GROUPS.find((g) => g.name === group)!;
        return (
          <section key={group} className="space-y-3">
            <div className="flex items-center gap-2 px-1">
              <span
                className="w-2.5 h-2.5 rounded-full"
                style={{ backgroundColor: meta.color }}
                aria-hidden="true"
              />
              <h3 className="text-[14px] font-bold tracking-extra-tight text-text-primary">
                {meta.label}
              </h3>
              <span className="text-[12px] text-text-secondary">·</span>
              <span className="text-[12px] text-text-secondary">
                {meta.purpose}
              </span>
              <span className="ml-auto text-[11px] text-text-secondary tabular-nums">
                {tables.length} {tables.length === 1 ? "table" : "tables"}
              </span>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
              {tables.map((t) => (
                <TableCard key={t.name} table={t} />
              ))}
            </div>
          </section>
        );
      })}

      {/* ---- Footer card — design notes ---- */}
      <Card>
        <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-2">
          Design Notes
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-[12.5px] text-text-secondary leading-relaxed">
          <div>
            <span className="text-text-primary font-medium">
              ContentUID is a String UUID
            </span>{" "}
            — stable forever, survives source switches. Auto-increment IDs only
            for log tables (history, identity_event, activity_event,
            ad_impression).
          </div>
          <div>
            <span className="text-text-primary font-medium">
              ON DELETE CASCADE
            </span>{" "}
            on all FKs — deleting a content_uid cleans up all related data.
          </div>
          <div>
            <span className="text-text-primary font-medium">
              No boolean type
            </span>{" "}
            — SQLite has none. Use INTEGER (0/1). Timestamps are epoch millis
            (INTEGER).
          </div>
          <div>
            <span className="text-text-primary font-medium">
              Partial unique indexes
            </span>{" "}
            — used where SQLite&apos;s UNIQUE treats NULL as distinct
            (external_reference, episode_external_ref).
          </div>
        </div>
        <div className="mt-4 pt-4 border-t border-border/60 flex flex-wrap gap-3 text-[11.5px]">
          <Link
            href="/phase3/"
            className="inline-flex items-center gap-1.5 text-text-primary font-medium hover:underline"
          >
            → View Phase 3 plan (14 modules that build on this schema)
          </Link>
          <Link
            href="/architecture/"
            className="inline-flex items-center gap-1.5 text-text-secondary hover:text-text-primary hover:underline"
          >
            → Architecture plan
          </Link>
        </div>
      </Card>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * ERDiagram — SVG overlay on a CSS grid.
 *
 * Strategy: render nodes as absolute-positioned divs in a 12-col × 5-row grid
 * (matching ER_NODES col/row). Then overlay an SVG layer for the edges.
 * Each edge is a straight line (or simple curve) between node centers.
 * ------------------------------------------------------------------------- */

function ERDiagram() {
  const COLS = 12;
  const ROWS = 5;

  // Render in a fixed-aspect container; SVG uses viewBox = COLS×100 × ROWS×60.
  const cellW = 100;
  const cellH = 60;
  const svgW = COLS * cellW;
  const svgH = ROWS * cellH;

  // Map node id → center (in SVG coords).
  const nodeCenter = (n: ERNode) => {
    const cx = (n.col - 0.5) * cellW;
    const cy = (n.row - 0.5) * cellH;
    return { cx, cy };
  };

  return (
    <div
      className="relative w-full overflow-x-auto rounded-[12px] border border-border bg-surface-alt/40 p-2"
      style={{ minHeight: 360 }}
    >
      <div
        className="relative mx-auto"
        style={{
          width: "100%",
          maxWidth: svgW,
          aspectRatio: `${svgW} / ${svgH}`,
        }}
      >
        {/* SVG edges layer */}
        <svg
          viewBox={`0 0 ${svgW} ${svgH}`}
          preserveAspectRatio="xMidYMid meet"
          className="absolute inset-0 w-full h-full"
          aria-hidden="true"
        >
          <defs>
            <marker
              id="er-arrow"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="6"
              markerHeight="6"
              orient="auto-start-reverse"
            >
              <path
                d="M 0 0 L 10 5 L 0 10 z"
                fill="var(--c-text-secondary)"
                opacity="0.6"
              />
            </marker>
          </defs>
          {ER_EDGES.map((e, i) => {
            const from = ER_NODES.find((n) => n.id === e.from);
            const to = ER_NODES.find((n) => n.id === e.to);
            if (!from || !to) return null;
            const a = nodeCenter(from);
            const b = nodeCenter(to);

            // Mid-point curve control — slight S-curve for non-aligned nodes.
            const mx = (a.cx + b.cx) / 2;
            const my = (a.cy + b.cy) / 2;
            const dx = b.cx - a.cx;
            const dy = b.cy - a.cy;
            const curve = Math.abs(dx) > Math.abs(dy) ? dy * 0.2 : dx * 0.2;

            return (
              <g key={`edge-${i}`}>
                <path
                  d={`M ${a.cx} ${a.cy} Q ${mx + curve} ${my - curve} ${b.cx} ${b.cy}`}
                  fill="none"
                  stroke="var(--c-text-secondary)"
                  strokeOpacity="0.35"
                  strokeWidth="1.2"
                  markerEnd="url(#er-arrow)"
                />
                {e.label && (
                  <text
                    x={mx}
                    y={my - 4}
                    textAnchor="middle"
                    className="font-mono"
                    style={{
                      fontSize: 7,
                      fill: "var(--c-text-secondary)",
                      opacity: 0.7,
                    }}
                  >
                    {e.label}
                  </text>
                )}
              </g>
            );
          })}
        </svg>

        {/* Nodes layer */}
        {ER_NODES.map((n) => {
          const color = GROUP_COLOR[n.group];
          const leftPct = ((n.col - 1) / COLS) * 100;
          const topPct = ((n.row - 1) / ROWS) * 100;
          const widthPct = (1 / COLS) * 100;
          const heightPct = (1 / ROWS) * 100;
          return (
            <div
              key={n.id}
              className="absolute flex items-center justify-center p-[3px]"
              style={{
                left: `${leftPct}%`,
                top: `${topPct}%`,
                width: `${widthPct}%`,
                height: `${heightPct}%`,
              }}
            >
              <div
                className="w-full h-full rounded-[8px] border bg-surface px-1.5 flex flex-col items-center justify-center text-center shadow-sm transition-all duration-200 hover:shadow-md hover:-translate-y-[1px]"
                style={{ borderColor: color }}
                title={`${n.label} (${GROUP_LABEL[n.group]})`}
              >
                <span
                  className="w-1.5 h-1.5 rounded-full mb-0.5 shrink-0"
                  style={{ backgroundColor: color }}
                  aria-hidden="true"
                />
                <span
                  className="font-mono leading-tight text-text-primary truncate w-full"
                  style={{ fontSize: "clamp(7px, 0.7vw, 9.5px)" }}
                >
                  {n.label}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * TableCard — visual table diagram card (header + body + footer).
 * ------------------------------------------------------------------------- */

function TableCard({ table }: { table: SchemaTable }) {
  const color = GROUP_COLOR[table.group];
  const groupLabel = GROUP_LABEL[table.group];

  return (
    <div
      className="flex flex-col rounded-[14px] border bg-surface overflow-hidden transition-all duration-200 hover:shadow-[0_12px_40px_rgba(0,0,0,0.08)] hover:-translate-y-[1px]"
      style={{ borderColor: "var(--c-border)" }}
    >
      {/* Header — table name + group dot */}
      <div
        className="flex items-center gap-2 px-3.5 py-2.5 border-b"
        style={{
          backgroundColor: `color-mix(in srgb, ${color} 8%, transparent)`,
          borderColor: "var(--c-border)",
        }}
      >
        <span
          className="w-2.5 h-2.5 rounded-full shrink-0"
          style={{ backgroundColor: color }}
          aria-hidden="true"
        />
        <code
          className="font-mono text-[13px] font-semibold text-text-primary truncate flex-1"
          title={table.name}
        >
          {table.name}
        </code>
        {table.deferred && (
          <span className="inline-flex items-center h-5 px-1.5 rounded-[6px] text-[9px] font-medium bg-chip border border-border text-text-secondary uppercase tracking-wider shrink-0">
            deferred
          </span>
        )}
      </div>

      {/* Body — columns */}
      <div className="flex-1 px-3.5 py-2.5">
        {/* Group label kicker */}
        <div className="text-[9.5px] font-medium uppercase tracking-widest text-text-secondary mb-2">
          {groupLabel}
        </div>

        <ul className="space-y-1.5">
          {table.columns.map((col) => {
            const isPK = col.isPK;
            const isFK = col.isFK;
            return (
              <li
                key={col.name}
                className="flex items-baseline gap-2 text-[11.5px]"
              >
                {/* PK / FK badge */}
                <span
                  className="font-mono text-[8.5px] font-bold uppercase tracking-wider w-5 shrink-0 text-center"
                  style={{
                    color: isPK
                      ? "var(--c-primary)"
                      : isFK
                        ? "var(--c-warning)"
                        : "transparent",
                  }}
                  title={
                    isPK
                      ? "Primary Key"
                      : isFK
                        ? `Foreign Key → ${col.fkTarget ?? ""}`
                        : undefined
                  }
                >
                  {isPK ? "PK" : isFK ? "FK" : "·"}
                </span>
                <code
                  className={`font-mono ${
                    isPK
                      ? "text-text-primary font-semibold"
                      : "text-text-primary"
                  }`}
                  style={{ minWidth: 0 }}
                >
                  {col.name}
                </code>
                <span className="font-mono text-[10.5px] text-text-secondary ml-auto shrink-0">
                  {col.type.toLowerCase()}
                </span>
                {isFK && (
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="var(--c-warning)"
                    strokeWidth="2.2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className="w-[10px] h-[10px] shrink-0"
                    aria-hidden="true"
                  >
                    <path d="M10 13a5 5 0 007 0l3-3a5 5 0 00-7-7l-1 1" />
                    <path d="M14 11a5 5 0 00-7 0l-3 3a5 5 0 007 7l1-1" />
                  </svg>
                )}
              </li>
            );
          })}
        </ul>

        {/* Description */}
        <p className="text-[11px] text-text-secondary leading-relaxed mt-2.5 pt-2.5 border-t border-border/60">
          {table.description}
        </p>

        {/* Column constraints (PK / FK / CHECK / DEFAULT) summary */}
        <div className="flex flex-wrap gap-1 mt-2">
          {table.columns
            .filter((c) => c.constraints)
            .filter((c) =>
              /NOT NULL|CHECK|DEFAULT|AUTOINCREMENT|UNIQUE/i.test(
                c.constraints ?? "",
              ),
            )
            .slice(0, 3)
            .map((c) => (
              <span
                key={`c-${c.name}`}
                className="inline-flex items-center h-4 px-1.5 rounded-[4px] text-[9px] font-mono bg-chip border border-border text-text-secondary"
              >
                {c.name}: {c.constraints?.split(" ").slice(0, 2).join(" ")}
              </span>
            ))}
        </div>
      </div>

      {/* Footer — indexes + composite PK + uniques */}
      <div className="px-3.5 py-2.5 border-t border-border/60 bg-surface-alt/40 space-y-2">
        {table.compositePK && table.compositePK.length > 0 && (
          <div className="flex items-start gap-2">
            <span className="font-mono text-[9px] font-bold uppercase tracking-wider text-[var(--c-primary)] shrink-0 mt-[1px]">
              PK
            </span>
            <code className="font-mono text-[10.5px] text-text-secondary break-all">
              ({table.compositePK.join(", ")})
            </code>
          </div>
        )}
        {table.uniques && table.uniques.length > 0 && (
          <div className="space-y-1">
            {table.uniques.map((u, i) => (
              <div key={`uniq-${i}`} className="flex items-start gap-2">
                <span className="font-mono text-[9px] font-bold uppercase tracking-wider text-[var(--c-success)] shrink-0 mt-[1px]">
                  UQ
                </span>
                <span className="font-mono text-[10.5px] text-text-secondary break-all">
                  {u}
                </span>
              </div>
            ))}
          </div>
        )}
        {table.indexes && table.indexes.length > 0 && (
          <div className="space-y-1">
            <div className="font-mono text-[9px] font-bold uppercase tracking-wider text-text-secondary">
              Indexes
            </div>
            {table.indexes.map((idx) => (
              <div key={idx.name} className="flex items-start gap-2">
                <span
                  className="font-mono text-[8.5px] uppercase tracking-wider shrink-0 mt-[1px] px-1 rounded-[3px]"
                  style={{
                    backgroundColor: idx.unique
                      ? "color-mix(in srgb, var(--c-success) 12%, transparent)"
                      : "var(--c-chip)",
                    color: idx.unique
                      ? "var(--c-success)"
                      : "var(--c-text-secondary)",
                  }}
                >
                  {idx.unique ? "UNQ" : "IDX"}
                </span>
                <code className="font-mono text-[10px] text-text-secondary break-all">
                  {idx.name}
                  <span className="opacity-60"> ON {idx.on}</span>
                  {idx.partial && (
                    <span className="text-[var(--c-warning)]"> · partial</span>
                  )}
                </code>
              </div>
            ))}
          </div>
        )}
        {table.fixNote && (
          <div className="pt-2 border-t border-border/60">
            <span className="font-mono text-[9.5px] text-text-secondary leading-relaxed">
              <span className="font-bold text-text-primary">Fix:</span>{" "}
              {table.fixNote}
            </span>
          </div>
        )}
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * SummaryStat — small stat tile for the hero card.
 * ------------------------------------------------------------------------- */
function SummaryStat({
  label,
  value,
  accent,
}: {
  label: string;
  value: string;
  accent: string;
}) {
  return (
    <div className="rounded-[10px] border border-border bg-surface-alt/60 p-2.5">
      <div className="flex items-center gap-1.5 mb-1">
        <span
          className="w-1.5 h-1.5 rounded-full"
          style={{ backgroundColor: accent }}
          aria-hidden="true"
        />
        <span className="text-[9.5px] font-medium uppercase tracking-widest text-text-secondary">
          {label}
        </span>
      </div>
      <div className="text-[18px] font-bold tracking-extra-tight text-text-primary leading-none">
        {value}
      </div>
    </div>
  );
}
