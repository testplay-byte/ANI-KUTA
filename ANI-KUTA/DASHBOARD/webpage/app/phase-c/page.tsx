"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
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
  PHASE_C_SUMMARY,
  PHASE_C_ER_NODES,
  PHASE_C_ER_EDGES,
  PHASE_C_DECISIONS,
  PHASE_C_MILESTONES,
  type PhaseCGroup,
  type PhaseCTable,
  type PhaseCERNode,
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
    const out: { group: PhaseCGroup; tables: PhaseCTable[] }[] = [];
    for (const g of PHASE_C_GROUPS) {
      const tables = filtered.filter((t) => t.group === g.name);
      if (tables.length > 0) out.push({ group: g.name, tables });
    }
    return out;
  }, [filtered]);

  return (
    <div className="space-y-6">
      {/* ---- 1. Hero ---- */}
      <Card className="!p-6 md:!p-8">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[11px] font-medium uppercase tracking-widest text-text-secondary">
              Phase C · Planning
            </span>
            <StatusDot color={PHASE_C_HERO.statusColor} size="sm" />
            <span
              className="inline-flex items-center h-6 px-2.5 rounded-full text-[10.5px] font-semibold uppercase tracking-wider"
              style={{
                backgroundColor:
                  "color-mix(in srgb, var(--c-warning) 14%, transparent)",
                color: "var(--c-warning)",
                border:
                  "1px solid color-mix(in srgb, var(--c-warning) 30%, transparent)",
              }}
            >
              {PHASE_C_HERO.status}
            </span>
          </div>
          <h2 className="text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
            {PHASE_C_HERO.title.split("—")[0].trim()}
            <span className="text-text-secondary font-medium">
              {" "}
              — {PHASE_C_HERO.title.split("—")[1]?.trim()}
            </span>
          </h2>
          <p className="text-[13.5px] text-text-secondary leading-relaxed max-w-2xl">
            {PHASE_C_HERO.subtitle}
          </p>
          <p className="text-[13px] text-text-secondary leading-relaxed max-w-3xl">
            {PHASE_C_HERO.summary}
          </p>

          {/* Summary stat strip */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1">
            <SummaryStat
              label="Tables"
              value={String(PHASE_C_SUMMARY.totalTables)}
              accent="var(--c-primary)"
            />
            <SummaryStat
              label="Columns"
              value={String(PHASE_C_SUMMARY.totalColumns)}
              accent="var(--c-secondary)"
            />
            <SummaryStat
              label="New tables"
              value={String(PHASE_C_SUMMARY.newTables)}
              accent="var(--c-success)"
            />
            <SummaryStat
              label="Groups"
              value={String(PHASE_C_SUMMARY.totalGroups)}
              accent="var(--c-warning)"
            />
          </div>
        </div>
      </Card>

      {/* ---- 2. The Two-ID System ---- */}
      <section className="space-y-3">
        <SectionHeading
          kicker="The Two-ID System"
          title="One record, two identifiers"
          right={
            <span className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium bg-chip border border-border text-text-secondary">
              <StatusDot color="var(--c-primary)" size="sm" />
              stable + changing
            </span>
          }
        />
        <p className="text-[12.5px] text-text-secondary leading-relaxed max-w-3xl px-1">
          Every content record carries two IDs with opposite lifecycles. The
          <span className="text-text-primary font-medium"> Main ID </span> never
          changes and is the key every other table points at; the
          <span className="text-text-primary font-medium"> Content ID </span>
          changes whenever the user switches sources and is used to detect
          overlaps. Neither is shown in the UI — they are pure infrastructure.
        </p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {TWO_ID_SYSTEM.map((card) => (
            <TwoIdCardView key={card.key} card={card} />
          ))}
        </div>
      </section>

      {/* ---- 3. Content ID Format ---- */}
      <Card>
        <div className="flex items-start justify-between gap-3 mb-3 flex-wrap">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Content ID Format
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              Five colon-separated segments
            </h3>
          </div>
          <span className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium bg-chip border border-border text-text-secondary">
            <StatusDot color="var(--c-secondary)" size="sm" />
            deterministic
          </span>
        </div>
        <p className="text-[12.5px] text-text-secondary leading-relaxed mb-4 max-w-3xl">
          The Content ID is built by joining five segments with colons. Any
          segment that does not apply is set to the literal string{" "}
          <code className="font-mono text-text-primary">none</code>. Same inputs
          always produce the same Content ID — so two records with the same
          Content ID are duplicates.
        </p>

        {/* Format line */}
        <div className="rounded-[12px] border border-border bg-surface-alt/60 p-4 mb-4 overflow-x-auto">
          <div className="font-mono text-[12.5px] sm:text-[14px] text-text-primary whitespace-nowrap">
            {renderFormatLine(CONTENT_ID_FORMAT)}
          </div>
        </div>

        {/* Segment breakdown */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2 mb-5">
          {CONTENT_ID_SEGMENTS.map((seg) => (
            <div
              key={seg.key}
              className="rounded-[10px] border border-border bg-surface-alt/40 p-3"
            >
              <div className="flex items-center gap-2 mb-1">
                <span
                  className="w-5 h-5 rounded-[6px] flex items-center justify-center font-mono text-[10px] font-bold shrink-0"
                  style={{
                    backgroundColor:
                      "color-mix(in srgb, var(--c-secondary) 14%, transparent)",
                    color: "var(--c-secondary)",
                  }}
                >
                  {seg.index}
                </span>
                <code className="font-mono text-[12px] font-semibold text-text-primary">
                  {seg.key}
                </code>
              </div>
              <p className="text-[11.5px] text-text-secondary leading-relaxed mb-1.5">
                {seg.description}
              </p>
              <code className="font-mono text-[10.5px] text-text-secondary break-all">
                e.g. {seg.example}
              </code>
            </div>
          ))}
        </div>

        {/* Examples code block */}
        <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-2">
          Example Content IDs
        </div>
        <div className="rounded-[12px] border border-border bg-surface-alt/60 overflow-hidden">
          <div className="overflow-x-auto">
            <pre className="font-mono text-[11.5px] sm:text-[12.5px] leading-[1.7] text-text-primary p-4 whitespace-pre">
{CONTENT_ID_EXAMPLES.map((ex) => ex.parts.join(":")).join("\n")}
            </pre>
          </div>
          {/* Per-example notes */}
          <div className="border-t border-border/60 divide-y divide-border/60">
            {CONTENT_ID_EXAMPLES.map((ex) => (
              <div
                key={ex.id}
                className="flex items-start gap-3 px-4 py-2.5"
              >
                <span
                  className="inline-flex items-center h-5 px-1.5 rounded-[6px] text-[9px] font-mono font-bold uppercase tracking-wider shrink-0 mt-[1px]"
                  style={{
                    backgroundColor:
                      "color-mix(in srgb, var(--c-secondary) 12%, transparent)",
                    color: "var(--c-secondary)",
                  }}
                >
                  {ex.id}
                </span>
                <code className="font-mono text-[10.5px] text-text-primary break-all min-w-0 flex-1">
                  {ex.parts.join(":")}
                </code>
                <span className="text-[11px] text-text-secondary leading-relaxed shrink-0 max-w-[280px] text-right hidden sm:block">
                  {ex.note}
                </span>
              </div>
            ))}
          </div>
        </div>
      </Card>

      {/* ---- 4. Database Schema ---- */}
      <section className="space-y-3">
        <SectionHeading
          kicker="Database Schema"
          title="9 tables across 3 groups"
          right={
            <span className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium bg-chip border border-border text-text-secondary">
              <StatusDot color="var(--c-primary)" size="sm" />
              {PHASE_C_SUMMARY.totalTables} tables · {PHASE_C_SUMMARY.totalColumns} columns
            </span>
          }
        />
        <p className="text-[12.5px] text-text-secondary leading-relaxed max-w-3xl px-1">
          The <code className="font-mono text-text-primary">content</code> table
          is the hub — every tracking table (library, watch_progress,
          watch_history) and every link table (content_source_link) FKs to its
          <code className="font-mono text-text-primary"> mainId</code>. Source
          tables (data_sources, systems, extension_repos, extensions) feed into
          the content record&apos;s metadata + extension columns.
        </p>

        {/* Filter pills */}
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

        {/* Tables grouped */}
        {grouped.map(({ group, tables }) => {
          const meta = PHASE_C_GROUPS.find((g) => g.name === group)!;
          return (
            <div key={group} className="space-y-3">
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
              <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
                {tables.map((t) => (
                  <TableCard key={t.name} table={t} />
                ))}
              </div>
            </div>
          );
        })}
      </section>

      {/* ---- 5. ER Diagram ---- */}
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
            {PHASE_C_ER_NODES.length} entities · {PHASE_C_ER_EDGES.length} edges
          </span>
        </div>
        <p className="text-[12.5px] text-text-secondary leading-relaxed mb-4 max-w-3xl">
          <code className="font-mono text-text-primary">content</code> sits in
          the center. Source tables feed in from the left; tracking tables hang
          off to the right. Every edge is a foreign key — and every FK that
          targets <code className="font-mono text-text-primary">content.mainId</code>{" "}
          uses <code className="font-mono text-text-primary">ON DELETE CASCADE</code>,
          so deleting a content record cleans up all its history, progress, and
          library entries automatically.
        </p>
        <ERDiagram />
        <div className="mt-4 flex flex-wrap gap-3">
          {PHASE_C_GROUPS.map((g) => (
            <div
              key={g.name}
              className="inline-flex items-center gap-1.5 text-[11px] text-text-secondary"
            >
              <span
                className="w-2 h-2 rounded-full"
                style={{ backgroundColor: g.color }}
                aria-hidden="true"
              />
              <span>{g.label}</span>
            </div>
          ))}
        </div>

        {/* Relationship legend list */}
        <div className="mt-5 pt-4 border-t border-border/60">
          <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-3">
            Relationships
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
            {PHASE_C_ER_EDGES.map((e, i) => {
              const fromNode = PHASE_C_ER_NODES.find((n) => n.id === e.from)!;
              const toNode = PHASE_C_ER_NODES.find((n) => n.id === e.to)!;
              const fromColor = PHASE_C_GROUP_COLOR[fromNode.group];
              const toColor = PHASE_C_GROUP_COLOR[toNode.group];
              return (
                <div
                  key={`rel-${i}`}
                  className="flex items-center gap-2 rounded-[10px] border border-border bg-surface-alt/40 px-3 py-2"
                >
                  <span
                    className="w-1.5 h-1.5 rounded-full shrink-0"
                    style={{ backgroundColor: fromColor }}
                    aria-hidden="true"
                  />
                  <code className="font-mono text-[10.5px] text-text-primary truncate">
                    {e.from}
                  </code>
                  <span className="font-mono text-[10px] text-text-secondary shrink-0 mx-0.5">
                    {e.cardinality === "1-1" ? "1───1" : "1───N"}
                  </span>
                  <span
                    className="w-1.5 h-1.5 rounded-full shrink-0"
                    style={{ backgroundColor: toColor }}
                    aria-hidden="true"
                  />
                  <code className="font-mono text-[10.5px] text-text-primary truncate">
                    {e.to}
                  </code>
                </div>
              );
            })}
          </div>
        </div>
      </Card>

      {/* ---- 6. Confirmed Decisions ---- */}
      <Card>
        <div className="flex items-start justify-between gap-3 mb-3 flex-wrap">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Confirmed Decisions
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              Q-001 through Q-006
            </h3>
          </div>
          <span
            className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium shrink-0"
            style={{
              backgroundColor:
                "color-mix(in srgb, var(--c-success) 14%, transparent)",
              color: "var(--c-success)",
            }}
          >
            <StatusDot color="var(--c-success)" size="sm" />
            {PHASE_C_DECISIONS.length}/{PHASE_C_DECISIONS.length} confirmed
          </span>
        </div>
        <p className="text-[12.5px] text-text-secondary leading-relaxed mb-4 max-w-3xl">
          The six questions that shaped the Phase C design — all confirmed. The
          headline decision (Q-001) is the two-ID split; the rest follow from
          it.
        </p>

        {/* Decisions table */}
        <div className="rounded-[12px] border border-border overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-left">
              <thead>
                <tr className="bg-surface-alt/60 border-b border-border">
                  <th className="font-mono text-[10px] font-bold uppercase tracking-widest text-text-secondary px-3 py-2.5 w-[72px]">
                    #
                  </th>
                  <th className="text-[11px] font-bold uppercase tracking-widest text-text-secondary px-3 py-2.5 min-w-[180px]">
                    Question
                  </th>
                  <th className="text-[11px] font-bold uppercase tracking-widest text-text-secondary px-3 py-2.5 min-w-[260px]">
                    Answer
                  </th>
                </tr>
              </thead>
              <tbody>
                {PHASE_C_DECISIONS.map((d, i) => (
                  <tr
                    key={d.id}
                    className={`border-b border-border/60 last:border-b-0 hover:bg-canvas/50 transition-colors duration-150 ${
                      i % 2 === 1 ? "bg-surface-alt/20" : ""
                    }`}
                  >
                    <td className="px-3 py-3 align-top">
                      <span
                        className="inline-flex items-center h-5 px-1.5 rounded-[6px] font-mono text-[10px] font-bold"
                        style={{
                          backgroundColor:
                            "color-mix(in srgb, var(--c-primary) 12%, transparent)",
                          color: "var(--c-primary)",
                        }}
                      >
                        {d.id}
                      </span>
                    </td>
                    <td className="px-3 py-3 align-top">
                      <span className="text-[12.5px] font-medium text-text-primary">
                        {d.question}
                      </span>
                    </td>
                    <td className="px-3 py-3 align-top">
                      <span className="text-[12px] text-text-secondary leading-relaxed">
                        {d.answer}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </Card>

      {/* ---- 7. Implementation Phases ---- */}
      <section className="space-y-3">
        <SectionHeading
          kicker="Implementation Phases"
          title="C.1 → C.5 — schema first, tracking last"
          right={
            <span className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium bg-chip border border-border text-text-secondary">
              <StatusDot color="var(--c-warning)" size="sm" />
              {PHASE_C_MILESTONES.length} phases
            </span>
          }
        />
        <p className="text-[12.5px] text-text-secondary leading-relaxed max-w-3xl px-1">
          The build order is deliberate: lay down the schema and content module
          first (C.1), wire it into the details screen (C.2), then layer the
          three tracking systems on top — watch progress (C.3), library (C.4),
          and history (C.5). All three tracking phases consume the same{" "}
          <code className="font-mono text-text-primary">mainId</code>, so once
          C.1 + C.2 are done, C.3–C.5 are independent and can ship in any order.
        </p>
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {PHASE_C_MILESTONES.map((m) => (
            <PhaseMilestoneCard key={m.id} milestone={m} />
          ))}
        </div>
      </section>

      {/* ---- Footer links ---- */}
      <Card>
        <div className="flex flex-wrap items-center gap-3 text-[11.5px]">
          <Link
            href="/database/"
            className="inline-flex items-center gap-1.5 text-text-primary font-medium hover:underline"
          >
            → Compare with the Phase 3 schema (21 production tables)
          </Link>
          <Link
            href="/architecture/"
            className="inline-flex items-center gap-1.5 text-text-secondary hover:text-text-primary hover:underline"
          >
            → Architecture plan
          </Link>
          <Link
            href="/decisions/"
            className="inline-flex items-center gap-1.5 text-text-secondary hover:text-text-primary hover:underline"
          >
            → All decisions D-027..D-054
          </Link>
        </div>
      </Card>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * Helpers — format line renderer (color-codes each segment).
 * ------------------------------------------------------------------------- */
function renderFormatLine(format: string) {
  const parts = format.split(":");
  const segmentColors = [
    "var(--c-primary)",
    "var(--c-secondary)",
    "#0EA5E9",
    "var(--c-success)",
    "var(--c-warning)",
  ];
  return (
    <>
      {parts.map((p, i) => (
        <span key={i}>
          {i > 0 && <span className="text-text-secondary">:</span>}
          <span style={{ color: segmentColors[i] }}>{p}</span>
        </span>
      ))}
    </>
  );
}

/* ---------------------------------------------------------------------------
 * SectionHeading — kicker + title row with optional right action.
 * ------------------------------------------------------------------------- */
function SectionHeading({
  kicker,
  title,
  right,
}: {
  kicker: string;
  title: string;
  right?: React.ReactNode;
}) {
  return (
    <div className="flex items-end justify-between gap-3 flex-wrap px-1">
      <div>
        <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
          {kicker}
        </div>
        <h3 className="text-[20px] font-bold tracking-extra-tight text-text-primary leading-tight">
          {title}
        </h3>
      </div>
      {right && <div className="shrink-0">{right}</div>}
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * TwoIdCardView — a single two-ID card.
 * ------------------------------------------------------------------------- */
function TwoIdCardView({
  card,
}: {
  card: (typeof TWO_ID_SYSTEM)[number];
}) {
  return (
    <div
      className="flex flex-col rounded-[16px] border bg-surface overflow-hidden transition-all duration-200 hover:shadow-[0_12px_40px_rgba(0,0,0,0.08)] hover:-translate-y-[1px]"
      style={{ borderColor: "var(--c-border)" }}
    >
      {/* Top accent bar */}
      <div
        className="h-1 w-full"
        style={{ backgroundColor: card.color }}
        aria-hidden="true"
      />
      {/* Header */}
      <div
        className="flex items-center gap-2.5 px-5 pt-4 pb-3"
        style={{
          backgroundColor: `color-mix(in srgb, ${card.color} 6%, transparent)`,
        }}
      >
        <span
          className="w-2.5 h-2.5 rounded-full shrink-0"
          style={{ backgroundColor: card.color }}
          aria-hidden="true"
        />
        <div className="min-w-0">
          <div className="flex items-baseline gap-2">
            <h4 className="text-[16px] font-bold tracking-extra-tight text-text-primary">
              {card.title}
            </h4>
            <span className="text-[11px] text-text-secondary truncate">
              {card.tagline}
            </span>
          </div>
        </div>
        {card.notShownInUi && (
          <span className="ml-auto inline-flex items-center h-5 px-1.5 rounded-[6px] text-[9px] font-medium bg-chip border border-border text-text-secondary uppercase tracking-wider shrink-0">
            not in UI
          </span>
        )}
      </div>
      {/* Body */}
      <div className="flex-1 px-5 py-4 space-y-3">
        <div>
          <div className="text-[10px] font-medium uppercase tracking-widest text-text-secondary mb-1">
            Format
          </div>
          <div className="text-[12.5px] text-text-primary font-medium">
            {card.format}
          </div>
          <code className="font-mono text-[11px] text-text-secondary break-all block mt-1">
            {card.formatMono}
          </code>
        </div>
        <div>
          <div className="text-[10px] font-medium uppercase tracking-widest text-text-secondary mb-1.5">
            Properties
          </div>
          <ul className="space-y-1.5">
            {card.bullets.map((b, i) => (
              <li
                key={i}
                className="flex items-start gap-2 text-[12px] text-text-secondary leading-relaxed"
              >
                <StatusDot color={card.color} size="sm" className="mt-[6px]" />
                <span>{b}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * TableCard — schema definition table + demo rows.
 * ------------------------------------------------------------------------- */
function TableCard({ table }: { table: PhaseCTable }) {
  const color = PHASE_C_GROUP_COLOR[table.group];
  const groupLabel = PHASE_C_GROUP_LABEL[table.group];

  return (
    <div
      className={`flex flex-col rounded-[14px] border bg-surface overflow-hidden transition-all duration-200 hover:shadow-[0_12px_40px_rgba(0,0,0,0.08)] hover:-translate-y-[1px] ${table.isMain ? "ring-1 ring-offset-0" : ""}`}
      style={{
        borderColor: table.isMain
          ? `color-mix(in srgb, ${color} 40%, var(--c-border))`
          : "var(--c-border)",
      }}
    >
      {/* Header */}
      <div
        className="flex items-center gap-2 px-4 py-3 border-b"
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
          className="font-mono text-[13.5px] font-semibold text-text-primary truncate flex-1"
          title={table.name}
        >
          {table.name}
        </code>
        {table.isMain && (
          <span
            className="inline-flex items-center h-5 px-1.5 rounded-[6px] text-[9px] font-bold uppercase tracking-wider shrink-0"
            style={{
              backgroundColor:
                "color-mix(in srgb, var(--c-primary) 14%, transparent)",
              color: "var(--c-primary)",
            }}
          >
            main
          </span>
        )}
        {table.isNew && (
          <span
            className="inline-flex items-center h-5 px-1.5 rounded-[6px] text-[9px] font-bold uppercase tracking-wider shrink-0"
            style={{
              backgroundColor:
                "color-mix(in srgb, var(--c-success) 14%, transparent)",
              color: "var(--c-success)",
            }}
          >
            new
          </span>
        )}
      </div>

      {/* Body */}
      <div className="flex-1 px-4 py-3 space-y-3">
        <div className="text-[9.5px] font-medium uppercase tracking-widest text-text-secondary">
          {groupLabel}
        </div>
        <p className="text-[11.5px] text-text-secondary leading-relaxed">
          {table.description}
        </p>

        {/* Composite PK note */}
        {table.compositePK && table.compositePK.length > 0 && (
          <div className="flex items-center gap-2 rounded-[8px] border border-border bg-surface-alt/40 px-2.5 py-1.5">
            <span
              className="font-mono text-[9px] font-bold uppercase tracking-wider"
              style={{ color: "var(--c-primary)" }}
            >
              PK
            </span>
            <code className="font-mono text-[10.5px] text-text-secondary break-all">
              ({table.compositePK.join(", ")})
            </code>
          </div>
        )}

        {/* Column definitions table */}
        <div className="rounded-[10px] border border-border overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-left">
              <thead>
                <tr className="bg-surface-alt/60 border-b border-border">
                  <th className="font-mono text-[9px] font-bold uppercase tracking-widest text-text-secondary px-2.5 py-1.5 min-w-[90px]">
                    Column
                  </th>
                  <th className="font-mono text-[9px] font-bold uppercase tracking-widest text-text-secondary px-2.5 py-1.5 w-[64px]">
                    Type
                  </th>
                  <th className="font-mono text-[9px] font-bold uppercase tracking-widest text-text-secondary px-2.5 py-1.5 min-w-[110px]">
                    Constraints
                  </th>
                  <th className="font-mono text-[9px] font-bold uppercase tracking-widest text-text-secondary px-2.5 py-1.5 min-w-[120px]">
                    Description
                  </th>
                </tr>
              </thead>
              <tbody>
                {table.columns.map((col) => {
                  const isPK = /PK/i.test(col.constraints);
                  const isFK = /FK/i.test(col.constraints);
                  return (
                    <tr
                      key={col.name}
                      className="border-b border-border/50 last:border-b-0 hover:bg-canvas/40 transition-colors duration-150"
                    >
                      <td className="px-2.5 py-1.5 align-top">
                        <div className="flex items-center gap-1.5">
                          <span
                            className="font-mono text-[8px] font-bold uppercase tracking-wider w-5 shrink-0 text-center"
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
                                  ? "Foreign Key"
                                  : undefined
                            }
                          >
                            {isPK ? "PK" : isFK ? "FK" : "·"}
                          </span>
                          <code
                            className={`font-mono text-[11px] ${
                              isPK
                                ? "text-text-primary font-semibold"
                                : "text-text-primary"
                            }`}
                          >
                            {col.name}
                          </code>
                        </div>
                      </td>
                      <td className="px-2.5 py-1.5 align-top">
                        <code className="font-mono text-[10px] text-text-secondary lowercase">
                          {col.type.toLowerCase()}
                        </code>
                      </td>
                      <td className="px-2.5 py-1.5 align-top">
                        {col.constraints ? (
                          <code className="font-mono text-[10px] text-text-secondary break-all">
                            {col.constraints}
                          </code>
                        ) : (
                          <span className="text-text-secondary text-[10px] opacity-50">
                            —
                          </span>
                        )}
                      </td>
                      <td className="px-2.5 py-1.5 align-top">
                        {col.description ? (
                          <code className="font-mono text-[10px] text-text-secondary break-all">
                            {col.description}
                          </code>
                        ) : (
                          <span className="text-text-secondary text-[10px] opacity-50">
                            —
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>

        {/* Demo rows */}
        <div>
          <div className="text-[9.5px] font-medium uppercase tracking-widest text-text-secondary mb-1.5">
            Demo rows
          </div>
          <div className="rounded-[10px] border border-border overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-left">
                <thead>
                  <tr className="bg-surface-alt/60 border-b border-border">
                    {table.columns.map((col) => (
                      <th
                        key={col.name}
                        className="font-mono text-[8.5px] font-bold uppercase tracking-wider text-text-secondary px-2 py-1.5 whitespace-nowrap"
                      >
                        {col.name}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {table.demoRows.map((row, ri) => (
                    <tr
                      key={ri}
                      className="border-b border-border/50 last:border-b-0 hover:bg-canvas/40 transition-colors duration-150"
                    >
                      {row.map((val, ci) => (
                        <td
                          key={ci}
                          className="px-2 py-1.5 align-top"
                        >
                          <code
                            className={`font-mono text-[10px] ${
                              val === "null"
                                ? "text-[var(--c-danger)] opacity-70"
                                : val === "—"
                                  ? "text-text-secondary opacity-40"
                                  : "text-text-primary"
                            } break-all whitespace-nowrap`}
                          >
                            {val}
                          </code>
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * ERDiagram — SVG overlay on a CSS grid (same strategy as /database page).
 * ------------------------------------------------------------------------- */
function ERDiagram() {
  const COLS = 12;
  const ROWS = 6;
  const cellW = 100;
  const cellH = 60;
  const svgW = COLS * cellW;
  const svgH = ROWS * cellH;

  const nodeCenter = (n: PhaseCERNode) => ({
    cx: (n.col - 0.5) * cellW,
    cy: (n.row - 0.5) * cellH,
  });

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
              id="phasec-er-arrow"
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
          {PHASE_C_ER_EDGES.map((e, i) => {
            const from = PHASE_C_ER_NODES.find((n) => n.id === e.from);
            const to = PHASE_C_ER_NODES.find((n) => n.id === e.to);
            if (!from || !to) return null;
            const a = nodeCenter(from);
            const b = nodeCenter(to);
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
                  strokeOpacity="0.4"
                  strokeWidth="1.3"
                  markerEnd="url(#phasec-er-arrow)"
                />
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
                  {e.cardinality === "1-1" ? "1—1" : "1—N"}
                </text>
              </g>
            );
          })}
        </svg>

        {/* Nodes layer */}
        {PHASE_C_ER_NODES.map((n) => {
          const color = PHASE_C_GROUP_COLOR[n.group];
          const leftPct = ((n.col - 1) / COLS) * 100;
          const topPct = ((n.row - 1) / ROWS) * 100;
          const widthPct = (1 / COLS) * 100;
          const heightPct = (1 / ROWS) * 100;
          const isHub = n.id === "content";
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
                style={{
                  borderColor: color,
                  boxShadow: isHub
                    ? `0 0 0 2px color-mix(in srgb, ${color} 40%, transparent)`
                    : undefined,
                }}
                title={`${n.label} (${PHASE_C_GROUP_LABEL[n.group]})`}
              >
                <span
                  className="w-1.5 h-1.5 rounded-full mb-0.5 shrink-0"
                  style={{ backgroundColor: color }}
                  aria-hidden="true"
                />
                <span
                  className="font-mono leading-tight text-text-primary truncate w-full"
                  style={{
                    fontSize: "clamp(7px, 0.7vw, 9.5px)",
                    fontWeight: isHub ? 700 : 500,
                  }}
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
 * PhaseMilestoneCard — a single implementation-phase card.
 * ------------------------------------------------------------------------- */
function PhaseMilestoneCard({
  milestone,
}: {
  milestone: (typeof PHASE_C_MILESTONES)[number];
}) {
  const statusMeta: Record<
    string,
    { label: string; color: string; bg: string }
  > = {
    planning: {
      label: "Planning",
      color: "var(--c-warning)",
      bg: "color-mix(in srgb, var(--c-warning) 14%, transparent)",
    },
    todo: {
      label: "To do",
      color: "var(--c-text-secondary)",
      bg: "var(--c-chip)",
    },
    doing: {
      label: "In progress",
      color: "var(--c-primary)",
      bg: "color-mix(in srgb, var(--c-primary) 14%, transparent)",
    },
    done: {
      label: "Done",
      color: "var(--c-success)",
      bg: "color-mix(in srgb, var(--c-success) 14%, transparent)",
    },
  };
  const meta = statusMeta[milestone.status];

  return (
    <div
      className="flex flex-col rounded-[16px] border bg-surface overflow-hidden transition-all duration-200 hover:shadow-[0_12px_40px_rgba(0,0,0,0.08)] hover:-translate-y-[1px]"
      style={{ borderColor: "var(--c-border)" }}
    >
      {/* Top accent + phase ID */}
      <div
        className="flex items-center gap-2.5 px-4 py-3 border-b"
        style={{
          backgroundColor:
            "color-mix(in srgb, var(--c-primary) 5%, transparent)",
          borderColor: "var(--c-border)",
        }}
      >
        <span
          className="inline-flex items-center justify-center h-7 px-2 rounded-[8px] font-mono text-[12px] font-bold shrink-0"
          style={{
            backgroundColor:
              "color-mix(in srgb, var(--c-primary) 14%, transparent)",
            color: "var(--c-primary)",
          }}
        >
          {milestone.id}
        </span>
        <span
          className="inline-flex items-center gap-1.5 h-5 px-1.5 rounded-[6px] text-[9px] font-medium uppercase tracking-wider shrink-0 ml-auto"
          style={{ backgroundColor: meta.bg, color: meta.color }}
        >
          <StatusDot color={meta.color} size="sm" />
          {meta.label}
        </span>
      </div>
      {/* Body */}
      <div className="flex-1 px-4 py-3.5 space-y-2.5">
        <h4 className="text-[14.5px] font-bold tracking-extra-tight text-text-primary leading-tight">
          {milestone.title}
        </h4>
        <p className="text-[12px] text-text-secondary leading-relaxed">
          {milestone.description}
        </p>
        <div className="pt-2 border-t border-border/60">
          <div className="text-[9.5px] font-medium uppercase tracking-widest text-text-secondary mb-1">
            Deliverable
          </div>
          <code className="font-mono text-[11px] text-text-primary break-all">
            {milestone.deliverable}
          </code>
        </div>
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
