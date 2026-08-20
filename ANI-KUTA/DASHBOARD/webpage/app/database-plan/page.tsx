import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import {
  COLUMN_STATUS_META,
  CONS_RISKS,
  CONTENT_DETAILS_SCHEMA,
  CORE_CHANGES,
  DEFERRED_ITEMS,
  DROPPED_TABLES,
  FINAL_TABLES,
  FOOTER_NOTE,
  FOOTER_NOTE_BULLETS,
  FUTURE_PROOFING,
  HERO,
  INDEPENDENT_IMPROVEMENTS,
  MAIN_ENTRY_SCHEMA,
  PLAN_META,
  QUERIES,
  QUERY_STATUS_META,
  REVIEW_ITERATIONS,
  RISK_SEVERITY_META,
  TABLE_STATUS_META,
  type ColumnStatus,
  type QueryStatus,
  type RiskSeverity,
  type TableStatus,
} from "@/lib/databasePlan";

/**
 * /database-plan/ — Database Restructuring Plan v2.
 *
 * Renders the 11-section plan (transcribed in full from
 * APP/ani-kuta/DOCUMENTATION/planning/database-restructuring/PLAN.md, v2)
 * per DESIGN.md (MEMORY OS v3). Static Server Component — no interactivity
 * needed, no "use client".
 *
 * The user reviews this page to decide whether to APPROVE the restructuring.
 * Completeness matters: every table column, every query, every con, every
 * deferred item is shown.
 *
 * v2 deltas (per PLAN.md v2 header note):
 *  - ONE wide content_details table (Option A — 26 cols, data_* + ext_* prefixes)
 *    — NOT two tables (the v1 Option C decision was reversed)
 *  - 26 → 22 tables (was 26 → 24 in v1)
 *  - 4 core changes (was 3): drop app_metadata is now a core change
 *  - 10-group presentation (was a flat list in v1)
 *  - Keep extension_repo_id, keep display_source as single UX column
 *
 * Sections:
 *  1.  Hero / Snapshot (badges + reviewer + date + verified metrics)
 *  2.  The 4 Core Changes (cards)
 *  3.  New Table Schemas (main_entry + content_details — the centerpiece — +
 *      4 dropped tables with where-their-data-goes)
 *  4.  Queries (new / changed / renamed, grouped by table)
 *  5.  Independent Improvements (11 bundled items)
 *  6.  Final Tables (22 tables, 10 groups, with status)
 *  7.  Cons + Risks (severity color-coded: HIGH / MEDIUM / LOW / RESOLVED)
 *  8.  Deferred / Skipped (10 items)
 *  9.  Future-Proofing (4 scenarios)
 *  10. Review Process (4 iterations — what each found + fixed)
 *  11. Footer Note (PROPOSAL v2 — awaiting approval)
 */
export default function DatabasePlanPage() {
  return (
    <div className="space-y-6">
      {/* ───────────────────────────────────────────────────────────────
       *  HERO
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-3">
          <div className="min-w-0">
            <div className="text-[11px] font-medium uppercase tracking-widest text-[var(--c-warning)] mb-1.5">
              {HERO.kicker}
            </div>
            <h1 className="text-[22px] sm:text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
              {HERO.title}
            </h1>
          </div>
          <div className="flex items-center gap-2 flex-wrap max-w-full">
            {HERO.badges.map((b) => {
              const color = toneToColor(b.tone);
              return (
                <span
                  key={b.label}
                  className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border whitespace-nowrap"
                  style={{
                    backgroundColor: `color-mix(in srgb, ${color} 12%, transparent)`,
                    borderColor: `color-mix(in srgb, ${color} 35%, transparent)`,
                    color: color,
                  }}
                >
                  <StatusDot color={color} size="sm" />
                  {b.label}
                </span>
              );
            })}
          </div>
        </div>
        <p className="text-[12.5px] sm:text-[13.5px] text-text-secondary leading-[1.5] max-w-2xl">
          {HERO.description}
        </p>
        <p className="text-[11.5px] text-text-secondary leading-relaxed mt-3 pt-3 border-t border-border/60 break-words">
          <span className="font-medium text-text-primary">Reviewer:</span>{" "}
          {PLAN_META.author}
          <span className="mx-2 text-border">·</span>
          <span className="font-medium text-text-primary">Date:</span>{" "}
          <span className="font-mono">{PLAN_META.date}</span>
          <span className="mx-2 text-border">·</span>
          <span className="font-medium text-text-primary">Source:</span>{" "}
          <span className="font-mono break-all">{PLAN_META.sourceOfTruth}</span>
        </p>

        <SubLabel className="mt-5">Verified snapshot metrics</SubLabel>
        <div className="overflow-x-auto -mx-1 px-1">
          <table className="w-full min-w-[560px] text-left border-collapse">
            <thead>
              <tr className="text-[10.5px] uppercase tracking-widest text-text-secondary">
                <Th>Metric</Th>
                <Th>Value</Th>
                <Th>Note</Th>
              </tr>
            </thead>
            <tbody>
              {HERO.snapshotMetrics.map((m) => (
                <tr
                  key={m.metric}
                  className="border-t border-border hover:bg-canvas/50 transition-colors"
                >
                  <Td className="font-medium text-text-primary whitespace-nowrap">
                    {m.metric}
                  </Td>
                  <Td className="font-mono font-semibold text-text-primary whitespace-nowrap">
                    {m.value}
                  </Td>
                  <Td className="text-text-secondary">{m.note}</Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 2 — THE 4 CORE CHANGES (v2 — was 3 in v1)
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§2 — The 4 Core Changes (v2)"
        title="What's actually changing"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-primary)" size="sm" />
            {CORE_CHANGES.length} changes
          </span>
        }
      >
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          {CORE_CHANGES.map((c) => {
            const color = accentColor(c.accent);
            return (
              <div
                key={c.num}
                className="rounded-[14px] border bg-surface-alt/40 p-4 flex flex-col"
                style={{
                  borderColor: `color-mix(in srgb, ${color} 25%, var(--c-border))`,
                }}
              >
                <div className="flex items-center gap-2.5 mb-2">
                  <span
                    className="inline-flex items-center justify-center w-8 h-8 rounded-[10px] shrink-0 font-mono text-[14px] font-bold"
                    style={{
                      backgroundColor: `color-mix(in srgb, ${color} 15%, transparent)`,
                      color: color,
                      border: `1.5px solid ${color}`,
                    }}
                    aria-hidden="true"
                  >
                    {c.num}
                  </span>
                  <span
                    className="inline-flex items-center gap-1.5 h-6 px-2 rounded-full text-[10.5px] font-medium border whitespace-nowrap"
                    style={{
                      backgroundColor: `color-mix(in srgb, ${color} 12%, transparent)`,
                      borderColor: `color-mix(in srgb, ${color} 35%, transparent)`,
                      color: color,
                    }}
                  >
                    {c.kind}
                  </span>
                </div>
                <h3 className="text-[14px] font-bold tracking-extra-tight text-text-primary leading-tight mb-2">
                  {c.title}
                </h3>
                <p className="text-[12px] text-text-primary leading-relaxed mb-2.5">
                  {c.what}
                </p>
                <div className="rounded-[10px] border border-border bg-surface/60 p-2.5 mb-2.5">
                  <div className="text-[10px] font-medium uppercase tracking-widest text-text-secondary mb-1">
                    Why
                  </div>
                  <p className="text-[11.5px] text-text-secondary leading-relaxed">
                    {c.why}
                  </p>
                </div>
                <div className="mt-auto">
                  <div className="text-[10px] font-medium uppercase tracking-widest text-text-secondary mb-1.5">
                    Impact
                  </div>
                  <ul className="space-y-1">
                    {c.impact.map((it, i) => (
                      <li
                        key={i}
                        className="flex items-start gap-1.5 text-[11.5px] text-text-primary leading-snug"
                      >
                        <span
                          className="font-mono text-[11px] shrink-0 mt-[1px]"
                          style={{ color: color }}
                          aria-hidden="true"
                        >
                          →
                        </span>
                        <span className="min-w-0 break-words">{it}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            );
          })}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 3 — NEW TABLE SCHEMAS (every column, color-coded)
       *  v2: main_entry + content_details (the centerpiece) + 4 dropped tables
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§3 — New Table Schemas (v2)"
        title="Every column, every constraint"
        right={<ColumnLegend />}
      >
        <p className="text-[12px] text-text-secondary leading-relaxed mb-5 max-w-2xl">
          The 2 most consequential tables — <span className="font-mono font-semibold text-text-primary">main_entry</span>{" "}
          (renamed from <span className="font-mono">content</span>, with v2 changes: keep{" "}
          <span className="font-mono">extension_repo_id</span>, keep{" "}
          <span className="font-mono">display_source</span> as a single UX column, drop{" "}
          <span className="font-mono">description</span>) +{" "}
          <span className="font-mono font-semibold text-text-primary">content_details</span>{" "}
          (NEW — the centerpiece — ONE wide table that merges 4 old tables via
          Option A: 26 cols, <span className="font-mono">data_*</span> +{" "}
          <span className="font-mono">ext_*</span> prefixes, 2 indexes, 11 queries).
          Plus the 4 dropped tables with where-their-data-goes. Every column is
          listed with its type, constraints, and a description. Color-coded by
          change status so you can scan what&apos;s new, what&apos;s modified,
          and what&apos;s dropped.
        </p>

        {/* main_entry */}
        <SchemaTable
          title={MAIN_ENTRY_SCHEMA.tableName}
          subtitle={`RENAMED from \`${MAIN_ENTRY_SCHEMA.renameFrom}\` · ${MAIN_ENTRY_SCHEMA.sqFile} · v2 keeps extension_repo_id + display_source (NOT split)`}
          purpose={MAIN_ENTRY_SCHEMA.purpose}
          columns={MAIN_ENTRY_SCHEMA.columns}
          indexes={MAIN_ENTRY_SCHEMA.indexes}
          queries={MAIN_ENTRY_SCHEMA.queries}
          accent="primary"
        />

        {/* content_details — THE CENTERPIECE */}
        <div className="mt-5">
          <SchemaTable
            title={CONTENT_DETAILS_SCHEMA.tableName}
            subtitle={`NEW · THE CENTERPIECE · merges ${CONTENT_DETAILS_SCHEMA.replaces.length} tables (${CONTENT_DETAILS_SCHEMA.replaces.join(
              " + ",
            )}) · ${CONTENT_DETAILS_SCHEMA.sqFile}`}
            purpose={CONTENT_DETAILS_SCHEMA.purpose}
            columns={CONTENT_DETAILS_SCHEMA.columns}
            indexes={CONTENT_DETAILS_SCHEMA.indexes}
            queries={CONTENT_DETAILS_SCHEMA.queries}
            accent="success"
            emphasize
          />
        </div>

        {/* Dropped tables — where their data goes */}
        <div className="mt-5">
          <DroppedTablesCard />
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 4 — QUERIES (new / changed / renamed)
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§4 — Queries"
        title="New, renamed, and changed queries"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-secondary)" size="sm" />
            {QUERIES.reduce((n, g) => n + g.queries.length, 0)} queries ·{" "}
            {QUERIES.length} groups
          </span>
        }
      >
        <div className="space-y-5">
          {QUERIES.map((g) => {
            const counts = countByStatus(g.queries.map((q) => q.status));
            return (
              <div
                key={g.group}
                className="rounded-[14px] border border-border bg-surface-alt/30 p-4"
              >
                <div className="flex items-baseline justify-between gap-3 flex-wrap mb-1">
                  <h3 className="text-[14px] font-bold tracking-extra-tight text-text-primary leading-tight">
                    <span className="font-mono">{g.group}</span>
                  </h3>
                  <div className="flex items-center gap-1 flex-wrap">
                    {counts.map((c) => {
                      const meta = QUERY_STATUS_META[c.status as QueryStatus];
                      if (!meta || c.count === 0) return null;
                      return (
                        <span
                          key={c.status}
                          className="inline-flex items-center gap-1 h-5 px-1.5 rounded-[6px] text-[10px] font-medium border whitespace-nowrap"
                          style={{
                            backgroundColor: `color-mix(in srgb, ${meta.colorVar} 10%, transparent)`,
                            borderColor: `color-mix(in srgb, ${meta.colorVar} 30%, transparent)`,
                            color: meta.colorVar,
                          }}
                        >
                          <span className="font-mono tabular-nums">
                            {c.count}
                          </span>
                          {meta.label}
                        </span>
                      );
                    })}
                  </div>
                </div>
                <p className="text-[11.5px] text-text-secondary leading-snug mb-3">
                  {g.subtitle}
                </p>
                <div className="space-y-1.5">
                  {g.queries.map((q) => {
                    const meta = QUERY_STATUS_META[q.status];
                    return (
                      <div
                        key={q.name}
                        className="flex items-start gap-2.5 rounded-[10px] border border-border bg-surface/60 p-2.5"
                      >
                        <span
                          className="inline-flex items-center justify-center w-5 h-5 rounded-[6px] font-mono text-[11px] font-bold shrink-0"
                          style={{
                            backgroundColor: `color-mix(in srgb, ${meta.colorVar} 15%, transparent)`,
                            color: meta.colorVar,
                            border: `1px solid color-mix(in srgb, ${meta.colorVar} 35%, transparent)`,
                          }}
                          aria-hidden="true"
                          title={meta.label}
                        >
                          {meta.symbol}
                        </span>
                        <div className="min-w-0 flex-1">
                          <div className="flex items-baseline gap-2 flex-wrap">
                            <span
                              className={`font-mono text-[12.5px] font-semibold text-text-primary ${
                                q.status === "dropped" ? "line-through opacity-70" : ""
                              }`}
                            >
                              {q.name}
                            </span>
                            <span className="font-mono text-[11px] text-text-secondary">
                              {q.signature}
                            </span>
                            <span
                              className="text-[9.5px] font-medium uppercase tracking-wider px-1.5 py-0.5 rounded-[5px] border whitespace-nowrap"
                              style={{
                                backgroundColor: `color-mix(in srgb, ${meta.colorVar} 10%, transparent)`,
                                borderColor: `color-mix(in srgb, ${meta.colorVar} 30%, transparent)`,
                                color: meta.colorVar,
                              }}
                            >
                              {meta.label}
                            </span>
                          </div>
                          <p className="text-[11.5px] text-text-secondary leading-snug mt-0.5">
                            {q.description}
                          </p>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 5 — INDEPENDENT IMPROVEMENTS (bundled)
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§5 — Independent Improvements"
        title="Bundled improvements (no merge required)"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-warning)" size="sm" />
            {INDEPENDENT_IMPROVEMENTS.length} items
          </span>
        }
      >
        <p className="text-[12px] text-text-secondary leading-relaxed mb-4 max-w-2xl">
          Improvements the research surfaced. They&apos;re independent of the 3
          core changes but make sense to bundle into the same restructuring
          session.
        </p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {INDEPENDENT_IMPROVEMENTS.map((it) => {
            const color = accentColor(it.accent);
            return (
              <div
                key={it.id}
                className="rounded-[12px] border bg-surface-alt/40 p-3.5 hover:bg-canvas/40 transition-colors"
                style={{
                  borderColor: `color-mix(in srgb, ${color} 22%, var(--c-border))`,
                }}
              >
                <div className="flex items-baseline gap-2 mb-1.5">
                  <span
                    className="font-mono text-[11px] font-bold shrink-0"
                    style={{ color: color }}
                  >
                    §{it.id}
                  </span>
                  <h4 className="text-[13px] font-semibold text-text-primary leading-tight">
                    {it.title}
                  </h4>
                </div>
                <p className="text-[12px] text-text-primary leading-relaxed mb-2">
                  {it.body}
                </p>
                {it.detail && (
                  <div className="mt-2 pt-2 border-t border-border/60 text-[11.5px] text-text-secondary leading-relaxed">
                    {it.detail}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 6 — FINAL TABLES (22 tables, 10 groups) — v2
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§6 — Final Tables (22, 10 groups)"
        title="Every table, grouped by function"
        right={<TableStatusLegend />}
      >
        <p className="text-[12px] text-text-secondary leading-relaxed mb-4 max-w-2xl">
          All 22 final tables (down from 26 — dropped anilist_detail +
          extension_detail + other_source_detail + anime_metadata_cache +
          app_metadata; added content_details; renamed content → main_entry).
          Organized in 10 functional groups per R-2 research. Most are
          UNCHANGED; some get minor bundled improvements (rename FK to
          main_entry, add CHECKs, fix episode_number type, add missing FKs).
        </p>
        <div className="space-y-3">
          {FINAL_TABLES.map((g) => (
            <div
              key={g.group}
              className="rounded-[14px] border border-border bg-surface-alt/30 p-3.5"
            >
              <div className="mb-2">
                <h3 className="text-[13.5px] font-bold tracking-extra-tight text-text-primary leading-tight">
                  {g.group}
                </h3>
                <p className="text-[11.5px] text-text-secondary leading-snug mt-0.5">
                  {g.subtitle}
                </p>
              </div>
              <div className="overflow-x-auto -mx-1 px-1">
                <table className="w-full min-w-[560px] text-left border-collapse">
                  <thead>
                    <tr className="text-[10px] uppercase tracking-widest text-text-secondary">
                      <Th>Table</Th>
                      <Th>Status</Th>
                      <Th>Why / improvements</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {g.rows.map((r) => {
                      const meta = TABLE_STATUS_META[r.status as TableStatus];
                      return (
                        <tr
                          key={r.table}
                          className="border-t border-border hover:bg-canvas/50 transition-colors align-top"
                        >
                          <Td className="font-mono font-semibold text-text-primary whitespace-nowrap">
                            {r.table}
                          </Td>
                          <Td className="whitespace-nowrap">
                            <span
                              className="inline-flex items-center gap-1.5 h-6 px-2 rounded-full text-[10.5px] font-medium border whitespace-nowrap"
                              style={{
                                backgroundColor: `color-mix(in srgb, ${meta.colorVar} 12%, transparent)`,
                                borderColor: `color-mix(in srgb, ${meta.colorVar} 35%, transparent)`,
                                color: meta.colorVar,
                              }}
                            >
                              <span
                                className="inline-block w-1.5 h-1.5 rounded-full"
                                style={{ backgroundColor: meta.colorVar }}
                                aria-hidden="true"
                              />
                              {meta.label}
                            </span>
                          </Td>
                          <Td className="text-text-secondary">
                            <span>{r.why}</span>
                            {r.improvements && (
                              <span className="block mt-0.5 text-[11px] font-medium text-[var(--c-primary)]">
                                Bundled: {r.improvements}
                              </span>
                            )}
                          </Td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          ))}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 7 — CONS + RISKS (severity color-coded)
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§7 — Cons + Risks"
        title="What could bite us, by change"
        right={<RiskSeverityLegend />}
      >
        <div className="space-y-4">
          {CONS_RISKS.map((g) => (
            <div
              key={g.group}
              className="rounded-[14px] border border-border bg-surface-alt/30 p-4"
            >
              <h3 className="text-[13.5px] font-bold tracking-extra-tight text-text-primary leading-tight mb-3">
                {g.group}
              </h3>
              <div className="space-y-1.5">
                {g.items.map((it, i) => {
                  const meta = RISK_SEVERITY_META[it.severity as RiskSeverity];
                  return (
                    <div
                      key={i}
                      className="flex items-start gap-2.5 rounded-[10px] border border-border bg-surface/60 p-2.5"
                      style={{
                        borderColor: `color-mix(in srgb, ${meta.colorVar} 22%, var(--c-border))`,
                      }}
                    >
                      <span
                        className="inline-flex items-center justify-center w-5 h-5 rounded-[6px] font-mono text-[11px] font-bold shrink-0"
                        style={{
                          backgroundColor: `color-mix(in srgb, ${meta.colorVar} 15%, transparent)`,
                          color: meta.colorVar,
                          border: `1px solid color-mix(in srgb, ${meta.colorVar} 35%, transparent)`,
                        }}
                        aria-hidden="true"
                        title={meta.label}
                      >
                        {meta.symbol}
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-baseline gap-2 flex-wrap mb-0.5">
                          <span
                            className="text-[9.5px] font-medium uppercase tracking-wider px-1.5 py-0.5 rounded-[5px] border whitespace-nowrap"
                            style={{
                              backgroundColor: `color-mix(in srgb, ${meta.colorVar} 10%, transparent)`,
                              borderColor: `color-mix(in srgb, ${meta.colorVar} 30%, transparent)`,
                              color: meta.colorVar,
                            }}
                          >
                            {meta.label}
                          </span>
                        </div>
                        <p className="text-[12px] text-text-primary leading-relaxed">
                          {it.text}
                        </p>
                        {it.resolvedBy && (
                          <div className="mt-1.5 pt-1.5 border-t border-border/60 text-[11.5px] text-text-secondary leading-relaxed">
                            <span className="font-medium text-[var(--c-success)]">
                              Resolved by:
                            </span>{" "}
                            {it.resolvedBy}
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 8 — DEFERRED / SKIPPED
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§8 — Deferred / Skipped"
        title="What's NOT in this plan"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-warning)" size="sm" />
            {DEFERRED_ITEMS.length} items
          </span>
        }
      >
        <p className="text-[12px] text-text-secondary leading-relaxed mb-4 max-w-2xl">
          Explicitly deferred to separate sessions — so you know what
          you&apos;re NOT approving. These are out of scope for this
          restructuring pass.
        </p>
        <div className="space-y-2">
          {DEFERRED_ITEMS.map((d) => (
            <div
              key={d.num}
              className="flex items-start gap-3 rounded-[12px] border border-border bg-surface-alt/40 p-3.5 hover:bg-canvas/40 transition-colors"
            >
              <span
                className="inline-flex items-center justify-center w-7 h-7 rounded-[8px] shrink-0 font-mono text-[13px] font-bold"
                style={{
                  backgroundColor: `color-mix(in srgb, var(--c-warning) 15%, transparent)`,
                  color: "var(--c-warning)",
                  border: "1.5px solid var(--c-warning)",
                }}
                aria-hidden="true"
              >
                {d.num}
              </span>
              <div className="min-w-0 flex-1">
                <h4 className="text-[13px] font-semibold text-text-primary leading-tight mb-1">
                  {d.title}
                </h4>
                <p className="text-[12px] text-text-primary leading-relaxed mb-1.5">
                  {d.body}
                </p>
                <div className="text-[11.5px] text-text-secondary leading-relaxed">
                  <span className="font-medium text-[var(--c-warning)]">
                    Deferred because:
                  </span>{" "}
                  {d.reason}
                </div>
              </div>
            </div>
          ))}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 9 — FUTURE-PROOFING
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§9 — Future-Proofing"
        title="How this handles the multi-source + multi-extension vision"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-success)" size="sm" />
            {FUTURE_PROOFING.length} scenarios
          </span>
        }
      >
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-3">
          {FUTURE_PROOFING.map((s) => {
            const color = accentColor(s.accent);
            return (
              <div
                key={s.title}
                className="rounded-[14px] border bg-surface-alt/40 p-4 flex flex-col"
                style={{
                  borderColor: `color-mix(in srgb, ${color} 22%, var(--c-border))`,
                }}
              >
                <div className="flex items-center gap-2 mb-2">
                  <StatusDot color={color} size="md" />
                  <h3 className="text-[13.5px] font-bold tracking-extra-tight text-text-primary leading-tight">
                    {s.title}
                  </h3>
                </div>
                <ol className="space-y-1.5 mb-3">
                  {s.steps.map((step, i) => (
                    <li
                      key={i}
                      className="flex items-start gap-2 text-[11.5px] text-text-primary leading-relaxed"
                    >
                      <span
                        className="inline-flex items-center justify-center w-4 h-4 rounded-[5px] font-mono text-[10px] font-bold shrink-0 mt-[1px]"
                        style={{
                          backgroundColor: `color-mix(in srgb, ${color} 15%, transparent)`,
                          color: color,
                          border: `1px solid color-mix(in srgb, ${color} 35%, transparent)`,
                        }}
                        aria-hidden="true"
                      >
                        {i + 1}
                      </span>
                      <span className="min-w-0 break-words">{step}</span>
                    </li>
                  ))}
                </ol>
                <div
                  className="mt-auto pt-2.5 border-t border-border/60 text-[11px] font-medium leading-relaxed"
                  style={{ color: color }}
                >
                  {s.footer}
                </div>
              </div>
            );
          })}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 10 — REVIEW PROCESS (4 iterations — v2: 1, 2A, 2B, 3+4)
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§10 — Review Process (v2)"
        title="4 review iterations — what each found + fixed"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-secondary)" size="sm" />
            {REVIEW_ITERATIONS.length} iterations
          </span>
        }
      >
        <p className="text-[12px] text-text-secondary leading-relaxed mb-4 max-w-2xl">
          The v2 plan was reviewed by 4 sub-agent iterations before being marked
          ready for the dashboard. Each iteration found issues; the next
          iteration verified they were fixed. This demonstrates the rigor
          behind the plan — nothing was rubber-stamped. Final verdict:
          APPROVED WITH MINOR FIXES — ready for dashboard.
        </p>
        <div className="space-y-3">
          {REVIEW_ITERATIONS.map((it) => {
            const color = accentColor(it.accent);
            return (
              <div
                key={it.num}
                className="rounded-[14px] border bg-surface-alt/40 p-4"
                style={{
                  borderColor: `color-mix(in srgb, ${color} 22%, var(--c-border))`,
                }}
              >
                <div className="flex items-start gap-3 mb-3">
                  <span
                    className="inline-flex items-center justify-center w-9 h-9 rounded-[10px] shrink-0 font-mono text-[14px] font-bold"
                    style={{
                      backgroundColor: `color-mix(in srgb, ${color} 15%, transparent)`,
                      color: color,
                      border: `1.5px solid ${color}`,
                    }}
                    aria-hidden="true"
                  >
                    {it.num}
                  </span>
                  <div className="min-w-0 flex-1">
                    <h3 className="text-[14px] font-bold tracking-extra-tight text-text-primary leading-tight">
                      {it.title}
                    </h3>
                    <p className="text-[11.5px] text-text-secondary leading-snug mt-0.5">
                      {it.subtitle}
                    </p>
                  </div>
                  <div className="flex items-center gap-1.5 flex-wrap shrink-0 max-w-full">
                    {it.counts.map((c, i) => {
                      const tone =
                        c.label === "FLAW"
                          ? "var(--c-danger)"
                          : c.label === "CONCERN"
                            ? "var(--c-warning)"
                            : c.label === "CONFIRMED"
                              ? "var(--c-success)"
                              : "var(--c-secondary)";
                      return (
                        <span
                          key={i}
                          className="inline-flex items-center gap-1 h-6 px-2 rounded-[8px] text-[10.5px] font-medium border whitespace-nowrap"
                          style={{
                            backgroundColor: `color-mix(in srgb, ${tone} 10%, transparent)`,
                            borderColor: `color-mix(in srgb, ${tone} 30%, transparent)`,
                            color: tone,
                          }}
                        >
                          <span className="font-mono tabular-nums font-semibold">
                            {c.value}
                          </span>
                          {c.label}
                        </span>
                      );
                    })}
                  </div>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <div>
                    <SubLabel className="mb-1.5">Found</SubLabel>
                    <ul className="space-y-1">
                      {it.found.map((f, i) => (
                        <li
                          key={i}
                          className="flex items-start gap-2 text-[11.5px] text-text-primary leading-snug"
                        >
                          <span
                            className="font-mono text-[11px] shrink-0 mt-[1px] text-[var(--c-danger)]"
                            aria-hidden="true"
                          >
                            ×
                          </span>
                          <span className="min-w-0 break-words">{f}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                  <div>
                    <SubLabel className="mb-1.5">Fixed / outcome</SubLabel>
                    <ul className="space-y-1">
                      {it.fixed.map((f, i) => (
                        <li
                          key={i}
                          className="flex items-start gap-2 text-[11.5px] text-text-primary leading-snug"
                        >
                          <span
                            className="font-mono text-[11px] shrink-0 mt-[1px] text-[var(--c-success)]"
                            aria-hidden="true"
                          >
                            ✓
                          </span>
                          <span className="min-w-0 break-words">{f}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 11 — FOOTER NOTE
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <div className="flex items-start gap-3 mb-2">
          <span
            className="inline-flex items-center justify-center w-7 h-7 rounded-[8px] shrink-0 text-[12px] font-bold"
            style={{
              backgroundColor: "color-mix(in srgb, var(--c-warning) 15%, transparent)",
              color: "var(--c-warning)",
              border: "1.5px solid var(--c-warning)",
            }}
            aria-hidden="true"
          >
            !
          </span>
          <div>
            <div className="text-[10.5px] font-medium uppercase tracking-widest text-[var(--c-warning)] mb-0.5">
              §11 — Footer Note
            </div>
            <h2 className="text-[16px] font-bold tracking-extra-tight text-text-primary leading-tight">
              Proposal v2 — awaiting your approval
            </h2>
          </div>
        </div>
        <p className="text-[12.5px] text-text-primary leading-relaxed mb-3">
          {FOOTER_NOTE}
        </p>
        <ul className="space-y-1.5">
          {FOOTER_NOTE_BULLETS.map((b, i) => (
            <li
              key={i}
              className="flex items-start gap-2 text-[12px] text-text-secondary leading-relaxed"
            >
              <span
                className="font-mono text-[12px] shrink-0 mt-[1px] text-text-secondary"
                aria-hidden="true"
              >
                ·
              </span>
              <span className="min-w-0 break-words">{b}</span>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * Sub-components
 * ------------------------------------------------------------------------- */

function SectionCard({
  kicker,
  title,
  right,
  children,
}: {
  kicker: string;
  title: string;
  right?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <Card>
      <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
        <div className="min-w-0">
          <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
            {kicker}
          </div>
          <h2 className="text-[18px] sm:text-[20px] font-bold tracking-extra-tight text-text-primary leading-tight">
            {title}
          </h2>
        </div>
        {right && <div className="max-w-full">{right}</div>}
      </div>
      {children}
    </Card>
  );
}

function SubLabel({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={`text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-2 ${className}`}
    >
      {children}
    </div>
  );
}

function Th({ children }: { children: React.ReactNode }) {
  return (
    <th className="py-2 pr-3 font-medium text-text-secondary text-[10.5px] uppercase tracking-widest first:pl-0 last:pr-0">
      {children}
    </th>
  );
}

function Td({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <td className={`py-2.5 pr-3 text-[12.5px] leading-snug first:pl-0 last:pr-0 ${className}`}>
      {children}
    </td>
  );
}

function ColumnLegend() {
  const statuses: ColumnStatus[] = [
    "new",
    "modified",
    "dropped",
    "renamed",
    "axis",
    "unchanged",
  ];
  return (
    <div className="flex items-center gap-1 flex-wrap max-w-full">
      {statuses.map((s) => {
        const meta = COLUMN_STATUS_META[s];
        return (
          <span
            key={s}
            className="inline-flex items-center gap-1 h-6 px-2 rounded-[8px] text-[10px] font-medium border whitespace-nowrap"
            style={{
              backgroundColor: `color-mix(in srgb, ${meta.colorVar} 10%, transparent)`,
              borderColor: `color-mix(in srgb, ${meta.colorVar} 30%, transparent)`,
              color: meta.colorVar,
            }}
          >
            <span
              className="inline-block w-1.5 h-1.5 rounded-full"
              style={{ backgroundColor: meta.colorVar }}
              aria-hidden="true"
            />
            {meta.label}
          </span>
        );
      })}
    </div>
  );
}

function TableStatusLegend() {
  const statuses: TableStatus[] = [
    "RENAMED",
    "NEW",
    "UPDATED",
    "UNCHANGED",
    "DROPPED",
    "ABSORBED",
  ];
  return (
    <div className="flex items-center gap-1 flex-wrap max-w-full">
      {statuses.map((s) => {
        const meta = TABLE_STATUS_META[s];
        return (
          <span
            key={s}
            className="inline-flex items-center gap-1 h-6 px-2 rounded-[8px] text-[10px] font-medium border whitespace-nowrap"
            style={{
              backgroundColor: `color-mix(in srgb, ${meta.colorVar} 10%, transparent)`,
              borderColor: `color-mix(in srgb, ${meta.colorVar} 30%, transparent)`,
              color: meta.colorVar,
            }}
          >
            <span
              className="inline-block w-1.5 h-1.5 rounded-full"
              style={{ backgroundColor: meta.colorVar }}
              aria-hidden="true"
            />
            {meta.label}
          </span>
        );
      })}
    </div>
  );
}

function RiskSeverityLegend() {
  const severities: RiskSeverity[] = ["HIGH", "MEDIUM", "LOW", "RESOLVED"];
  return (
    <div className="flex items-center gap-1 flex-wrap max-w-full">
      {severities.map((s) => {
        const meta = RISK_SEVERITY_META[s];
        return (
          <span
            key={s}
            className="inline-flex items-center gap-1 h-6 px-2 rounded-[8px] text-[10px] font-medium border whitespace-nowrap"
            style={{
              backgroundColor: `color-mix(in srgb, ${meta.colorVar} 10%, transparent)`,
              borderColor: `color-mix(in srgb, ${meta.colorVar} 30%, transparent)`,
              color: meta.colorVar,
            }}
          >
            <span
              className="inline-block w-1.5 h-1.5 rounded-full"
              style={{ backgroundColor: meta.colorVar }}
              aria-hidden="true"
            />
            {meta.label}
          </span>
        );
      })}
    </div>
  );
}

function SchemaTable({
  title,
  subtitle,
  purpose,
  columns,
  indexes,
  queries,
  accent,
  emphasize = false,
}: {
  title: string;
  subtitle: string;
  purpose: string;
  columns: {
    name: string;
    type: string;
    constraints: string;
    description: string;
    status: ColumnStatus;
  }[];
  indexes?: {
    name: string;
    status: ColumnStatus;
    def: string;
  }[];
  queries?: string[];
  accent: "primary" | "success" | "warning" | "secondary" | "danger";
  emphasize?: boolean;
}) {
  const color = accentColor(accent);
  const axisColor = "var(--c-secondary)";
  return (
    <div
      className="rounded-[14px] border bg-surface/60 overflow-hidden"
      style={{
        borderColor: emphasize
          ? color
          : `color-mix(in srgb, ${color} 25%, var(--c-border))`,
        boxShadow: emphasize
          ? `0 0 0 1px color-mix(in srgb, ${color} 25%, transparent), 0 4px 24px color-mix(in srgb, ${color} 8%, transparent)`
          : undefined,
      }}
    >
      {/* Header */}
      <div
        className="px-4 py-3 border-b"
        style={{
          backgroundColor: emphasize
            ? `color-mix(in srgb, ${color} 10%, transparent)`
            : `color-mix(in srgb, ${color} 6%, transparent)`,
          borderColor: `color-mix(in srgb, ${color} 25%, var(--c-border))`,
        }}
      >
        <div className="flex items-center gap-2 mb-1 flex-wrap">
          <span
            className="inline-flex items-center justify-center w-6 h-6 rounded-[8px] font-mono text-[11px] font-bold shrink-0"
            style={{
              backgroundColor: `color-mix(in srgb, ${color} 15%, transparent)`,
              color: color,
              border: `1px solid ${color}`,
            }}
            aria-hidden="true"
          >
            ⊨
          </span>
          <h3 className="font-mono text-[15px] font-bold tracking-extra-tight text-text-primary leading-tight">
            {title}
          </h3>
          {emphasize && (
            <span
              className="inline-flex items-center gap-1 h-5 px-2 rounded-[6px] text-[10px] font-medium border whitespace-nowrap"
              style={{
                backgroundColor: `color-mix(in srgb, ${color} 12%, transparent)`,
                borderColor: `color-mix(in srgb, ${color} 35%, transparent)`,
                color: color,
              }}
            >
              ★ CENTERPIECE
            </span>
          )}
        </div>
        <div className="text-[11px] font-medium uppercase tracking-wider text-text-secondary mb-1.5 break-words">
          {subtitle}
        </div>
        <p className="text-[12px] text-text-secondary leading-relaxed">
          {purpose}
        </p>
      </div>

      {/* Columns table */}
      <div className="overflow-x-auto">
        <table className="w-full min-w-[680px] text-left border-collapse">
          <thead>
            <tr
              className="text-[10px] uppercase tracking-widest"
              style={{ color: "var(--c-text-secondary)" }}
            >
              <th className="py-2 px-3 font-medium text-left">Column</th>
              <th className="py-2 px-3 font-medium text-left">Type</th>
              <th className="py-2 px-3 font-medium text-left">Constraints</th>
              <th className="py-2 px-3 font-medium text-left">Description</th>
            </tr>
          </thead>
          <tbody>
            {columns.map((c) => {
              const meta = COLUMN_STATUS_META[c.status];
              const isDropped = c.status === "dropped";
              const isAxis = c.status === "axis";
              // Axis divider row — full-width banner spanning all 4 cols
              if (isAxis) {
                return (
                  <tr
                    key={c.name}
                    className="border-t"
                    style={{
                      borderColor: "var(--c-border)",
                      backgroundColor: `color-mix(in srgb, ${axisColor} 10%, transparent)`,
                    }}
                  >
                    <td
                      colSpan={4}
                      className="py-2.5 px-3"
                      style={{
                        borderTop: `2px solid color-mix(in srgb, ${axisColor} 40%, transparent)`,
                      }}
                    >
                      <div className="flex items-center gap-2 flex-wrap">
                        <span
                          className="inline-flex items-center justify-center w-4 h-4 rounded-[5px] font-mono text-[9px] font-bold shrink-0"
                          style={{
                            backgroundColor: `color-mix(in srgb, ${axisColor} 15%, transparent)`,
                            color: axisColor,
                            border: `1px solid color-mix(in srgb, ${axisColor} 35%, transparent)`,
                          }}
                          aria-hidden="true"
                        >
                          {meta.symbol}
                        </span>
                        <span
                          className="font-mono text-[12px] font-bold tracking-wider"
                          style={{ color: axisColor }}
                        >
                          {c.name}
                        </span>
                      </div>
                      <p className="text-[11.5px] text-text-secondary leading-snug mt-1 pl-6">
                        {c.description}
                      </p>
                    </td>
                  </tr>
                );
              }
              return (
                <tr
                  key={c.name}
                  className="border-t align-top hover:bg-canvas/50 transition-colors"
                  style={{
                    borderColor: "var(--c-border)",
                    backgroundColor: isDropped
                      ? "color-mix(in srgb, var(--c-danger) 4%, transparent)"
                      : c.status === "new"
                        ? "color-mix(in srgb, var(--c-success) 4%, transparent)"
                        : c.status === "modified"
                          ? "color-mix(in srgb, var(--c-warning) 4%, transparent)"
                          : "transparent",
                  }}
                >
                  <td className="py-2.5 px-3">
                    <div className="flex items-center gap-2">
                      <span
                        className="inline-flex items-center justify-center w-4 h-4 rounded-[5px] font-mono text-[9px] font-bold shrink-0"
                        style={{
                          backgroundColor: `color-mix(in srgb, ${meta.colorVar} 15%, transparent)`,
                          color: meta.colorVar,
                          border: `1px solid color-mix(in srgb, ${meta.colorVar} 35%, transparent)`,
                        }}
                        aria-hidden="true"
                        title={meta.label}
                      >
                        {meta.symbol}
                      </span>
                      <span
                        className={`font-mono text-[12px] font-semibold text-text-primary ${
                          isDropped ? "line-through opacity-70" : ""
                        }`}
                      >
                        {c.name}
                      </span>
                    </div>
                  </td>
                  <td className="py-2.5 px-3">
                    <span
                      className={`font-mono text-[11.5px] text-text-primary ${
                        isDropped ? "line-through opacity-70" : ""
                      }`}
                    >
                      {c.type}
                    </span>
                  </td>
                  <td className="py-2.5 px-3">
                    <span
                      className={`font-mono text-[11px] text-text-secondary ${
                        isDropped ? "line-through opacity-70" : ""
                      }`}
                    >
                      {c.constraints}
                    </span>
                  </td>
                  <td className="py-2.5 px-3 text-[12px] text-text-secondary leading-snug">
                    {c.description}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Indexes (optional) */}
      {indexes && indexes.length > 0 && (
        <div className="border-t" style={{ borderColor: "var(--c-border)" }}>
          <div className="px-4 py-2 text-[10.5px] font-medium uppercase tracking-widest text-text-secondary bg-surface-alt/40">
            Indexes
          </div>
          <div className="px-4 py-2 space-y-1">
            {indexes.map((idx) => {
              const meta = COLUMN_STATUS_META[idx.status];
              return (
                <div
                  key={idx.name}
                  className="flex items-start gap-2 text-[11.5px] leading-snug min-w-0"
                >
                  <span
                    className="inline-flex items-center justify-center w-4 h-4 rounded-[5px] font-mono text-[9px] font-bold shrink-0 mt-[1px]"
                    style={{
                      backgroundColor: `color-mix(in srgb, ${meta.colorVar} 15%, transparent)`,
                      color: meta.colorVar,
                      border: `1px solid color-mix(in srgb, ${meta.colorVar} 35%, transparent)`,
                    }}
                    aria-hidden="true"
                  >
                    {meta.symbol}
                  </span>
                  <span
                    className={`font-mono text-[12px] font-semibold text-text-primary shrink-0 ${
                      idx.status === "dropped" ? "line-through opacity-70" : ""
                    }`}
                  >
                    {idx.name}
                  </span>
                  <span className="text-text-secondary min-w-0 break-words">{idx.def}</span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Queries (optional) */}
      {queries && queries.length > 0 && (
        <div className="border-t" style={{ borderColor: "var(--c-border)" }}>
          <div className="px-4 py-2 text-[10.5px] font-medium uppercase tracking-widest text-text-secondary bg-surface-alt/40">
            Queries ({queries.length})
          </div>
          <ul className="px-4 py-2 space-y-1">
            {queries.map((q, i) => {
              const parenIdx = q.indexOf("(");
              const name = parenIdx >= 0 ? q.substring(0, parenIdx) : q;
              const args = parenIdx >= 0 ? q.substring(parenIdx) : "";
              return (
                <li
                  key={i}
                  className="flex items-start gap-2 text-[11.5px] text-text-primary leading-snug"
                >
                  <span
                    className="font-mono text-[11px] shrink-0 mt-[1px]"
                    style={{ color: color }}
                    aria-hidden="true"
                  >
                    ›
                  </span>
                  <span className="min-w-0 break-words">
                    <span className="font-mono font-semibold">{name}</span>
                    <span className="font-mono text-text-secondary">
                      {args}
                    </span>
                  </span>
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * Dropped tables card — what they were + where their data goes
 * ------------------------------------------------------------------------- */

function DroppedTablesCard() {
  return (
    <div
      className="rounded-[14px] border bg-surface/60 overflow-hidden"
      style={{
        borderColor: `color-mix(in srgb, var(--c-danger) 25%, var(--c-border))`,
      }}
    >
      <div
        className="px-4 py-3 border-b"
        style={{
          backgroundColor: `color-mix(in srgb, var(--c-danger) 6%, transparent)`,
          borderColor: `color-mix(in srgb, var(--c-danger) 25%, var(--c-border))`,
        }}
      >
        <div className="flex items-center gap-2 mb-1 flex-wrap">
          <span
            className="inline-flex items-center justify-center w-6 h-6 rounded-[8px] font-mono text-[11px] font-bold shrink-0"
            style={{
              backgroundColor: `color-mix(in srgb, var(--c-danger) 15%, transparent)`,
              color: "var(--c-danger)",
              border: `1px solid var(--c-danger)`,
            }}
            aria-hidden="true"
          >
            ×
          </span>
          <h3 className="font-mono text-[15px] font-bold tracking-extra-tight text-text-primary leading-tight">
            {DROPPED_TABLES.length} dropped tables
          </h3>
          <span
            className="inline-flex items-center gap-1.5 h-5 px-2 rounded-[6px] text-[10px] font-medium border whitespace-nowrap"
            style={{
              backgroundColor: `color-mix(in srgb, var(--c-danger) 10%, transparent)`,
              borderColor: `color-mix(in srgb, var(--c-danger) 30%, transparent)`,
              color: "var(--c-danger)",
            }}
          >
            merged into content_details + app_settings
          </span>
        </div>
        <p className="text-[12px] text-text-secondary leading-relaxed">
          Every dropped table is either DROPPED (dead code, 0 callers, never
          written) or ABSORBED (columns duplicated elsewhere or explicitly
          migrated). Verified by 7 research sub-agents (5 prior session + 2
          this session). Zero data loss.
        </p>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[680px] text-left border-collapse">
          <thead>
            <tr
              className="text-[10px] uppercase tracking-widest"
              style={{ color: "var(--c-text-secondary)" }}
            >
              <th className="py-2 px-3 font-medium text-left">Table</th>
              <th className="py-2 px-3 font-medium text-left">Status</th>
              <th className="py-2 px-3 font-medium text-left">What it was</th>
              <th className="py-2 px-3 font-medium text-left">Where data goes</th>
            </tr>
          </thead>
          <tbody>
            {DROPPED_TABLES.map((t) => {
              const isAbsorbed = t.status === "ABSORBED";
              const color = "var(--c-danger)";
              return (
                <tr
                  key={t.table}
                  className="border-t align-top hover:bg-canvas/50 transition-colors"
                  style={{
                    borderColor: "var(--c-border)",
                    backgroundColor:
                      "color-mix(in srgb, var(--c-danger) 3%, transparent)",
                  }}
                >
                  <td className="py-2.5 px-3">
                    <div className="flex flex-col gap-1">
                      <span className="font-mono text-[12.5px] font-semibold text-text-primary line-through opacity-80">
                        {t.table}
                      </span>
                      <span className="font-mono text-[10.5px] text-text-secondary">
                        {t.sqFile}
                      </span>
                    </div>
                  </td>
                  <td className="py-2.5 px-3 whitespace-nowrap">
                    <span
                      className="inline-flex items-center gap-1.5 h-6 px-2 rounded-full text-[10.5px] font-medium border whitespace-nowrap"
                      style={{
                        backgroundColor: `color-mix(in srgb, ${color} 12%, transparent)`,
                        borderColor: `color-mix(in srgb, ${color} 35%, transparent)`,
                        color: color,
                      }}
                    >
                      <span
                        className="inline-block w-1.5 h-1.5 rounded-full"
                        style={{ backgroundColor: color }}
                        aria-hidden="true"
                      />
                      {isAbsorbed ? "ABSORBED" : "DROPPED"}
                    </span>
                  </td>
                  <td className="py-2.5 px-3 text-[11.5px] text-text-secondary leading-snug">
                    {t.whatItWas}
                  </td>
                  <td className="py-2.5 px-3 text-[11.5px] text-text-secondary leading-snug">
                    {t.whereDataGoes}
                    <span className="block mt-1.5 pt-1.5 border-t border-border/60 text-[11px]">
                      <span className="font-medium text-text-primary">
                        Callers:
                      </span>{" "}
                      {t.callers}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * Helpers
 * ------------------------------------------------------------------------- */

function toneToColor(
  tone: "primary" | "secondary" | "success" | "warning" | "danger",
): string {
  switch (tone) {
    case "primary":
      return "var(--c-primary)";
    case "secondary":
      return "var(--c-secondary)";
    case "success":
      return "var(--c-success)";
    case "warning":
      return "var(--c-warning)";
    case "danger":
      return "var(--c-danger)";
  }
}

function accentColor(
  accent: "primary" | "success" | "warning" | "secondary" | "danger",
): string {
  switch (accent) {
    case "primary":
      return "var(--c-primary)";
    case "success":
      return "var(--c-success)";
    case "warning":
      return "var(--c-warning)";
    case "secondary":
      return "var(--c-secondary)";
    case "danger":
      return "var(--c-danger)";
  }
}

function countByStatus<T extends string>(
  statuses: T[],
): { status: T; count: number }[] {
  const map = new Map<T, number>();
  for (const s of statuses) {
    map.set(s, (map.get(s) ?? 0) + 1);
  }
  return Array.from(map.entries()).map(([status, count]) => ({
    status,
    count,
  }));
}
