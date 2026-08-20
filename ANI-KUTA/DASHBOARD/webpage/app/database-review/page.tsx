import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import {
  ASSESSMENT,
  DB_REVIEW_META,
  FOOTER_NOTE_BULLETS,
  MERGE_CANDIDATES,
  RECOMMENDATION_META,
  RISK_META,
  SCHEMA_INVENTORY,
  SNAPSHOT,
  TOP_IMPROVEMENTS,
  type Recommendation,
  type RiskLevel,
} from "@/lib/databaseReview";

/**
 * /database-review/ — Database Review + Optimization Plan.
 *
 * Renders the 6-section database review findings (verified against the actual
 * codebase on 2026-08-13 via the Task 2-d-retry Explore sub-agent) per
 * DESIGN.md (MEMORY OS v3). Static Server Component — no interactivity
 * needed, no "use client".
 *
 * Sections:
 *  1. Snapshot (verified metrics)
 *  2. Schema Inventory (26 tables grouped, with caller counts)
 *  3. Merge Candidates (8 analyzed groups + recommendation badges)
 *  4. Top 3 Improvements (ranked by impact, with risk levels)
 *  5. Overall Assessment (strengths + weaknesses + bottom line)
 *  6. Footer Note (proposal notice — this is not implemented)
 */
export default function DatabaseReviewPage() {
  return (
    <div className="space-y-6">
      {/* ───────────────────────────────────────────────────────────────
       *  HERO
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-3">
          <div className="min-w-0">
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1.5">
              Database Review + Optimization Plan
            </div>
            <h1 className="text-[22px] sm:text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
              Database Review
            </h1>
          </div>
          <div className="flex items-center gap-2 flex-wrap max-w-full">
            <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
              <StatusDot color="var(--c-success)" size="sm" />
              Reviewed {DB_REVIEW_META.reviewDate}
            </span>
            <span
              className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border"
              style={{
                backgroundColor: "color-mix(in srgb, var(--c-secondary) 12%, transparent)",
                borderColor: "color-mix(in srgb, var(--c-secondary) 35%, transparent)",
                color: "var(--c-secondary)",
              }}
              title="Proposal — not implemented"
            >
              <span
                className="inline-block w-1.5 h-1.5 rounded-full"
                style={{ backgroundColor: "var(--c-secondary)" }}
                aria-hidden="true"
              />
              Proposal
            </span>
          </div>
        </div>
        <p className="text-[12.5px] sm:text-[13.5px] text-text-secondary leading-[1.5] max-w-2xl">
          A fresh, codebase-verified read of the 26-table SQLDelight schema —
          merge candidates, top improvements ranked by impact, and an overall
          assessment of where the wins are. All counts below were derived from
          the actual <code className="font-mono text-text-primary">.sq</code>{" "}
          files + greping the codebase for query callers — not from docs.
        </p>
        <p className="text-[11.5px] text-text-secondary leading-relaxed mt-3 pt-3 border-t border-border/60 break-words">
          <span className="font-medium text-text-primary">Reviewer:</span>{" "}
          {DB_REVIEW_META.reviewer}
          <span className="mx-2 text-border">·</span>
          <span className="font-medium text-text-primary">Source:</span>{" "}
          <span className="font-mono break-all">{DB_REVIEW_META.sourceRepo}</span>
        </p>
      </Card>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 1 — SNAPSHOT
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§1 — Snapshot"
        title="Schema at a glance"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-success)" size="sm" />
            {SNAPSHOT.metrics.length} metrics
          </span>
        }
      >
        <SubLabel>{SNAPSHOT.intro}</SubLabel>
        <div className="overflow-x-auto -mx-1 px-1">
          <table className="w-full min-w-[520px] text-left border-collapse">
            <thead>
              <tr className="text-[10.5px] uppercase tracking-widest text-text-secondary">
                <Th>Metric</Th>
                <Th>Value</Th>
                <Th>Note</Th>
              </tr>
            </thead>
            <tbody>
              {SNAPSHOT.metrics.map((m) => (
                <tr
                  key={m.metric}
                  className="border-t border-border hover:bg-canvas/50 transition-colors align-top"
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
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 2 — SCHEMA INVENTORY
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§2 — Schema Inventory"
        title="All 26 tables, grouped"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-primary)" size="sm" />
            26 tables · 15 .sq files
          </span>
        }
      >
        <p className="text-[12.5px] text-text-secondary leading-relaxed mb-4">
          For each table: source <code className="font-mono text-text-primary">.sq</code>{" "}
          file, column count, primary key, named-query count, and distinct
          caller sites in the codebase. Tables with zero callers are flagged{" "}
          <span className="font-mono text-[var(--c-danger)]">dead</span>.
        </p>

        <div className="space-y-5">
          {SCHEMA_INVENTORY.map((group) => (
            <div
              key={group.group}
              className="rounded-[14px] border border-border bg-surface-alt/30 p-4"
            >
              <div className="flex items-baseline justify-between gap-3 flex-wrap mb-3">
                <div>
                  <h3 className="text-[14px] font-bold tracking-extra-tight text-text-primary leading-tight">
                    {group.group}
                  </h3>
                  <p className="text-[11.5px] text-text-secondary leading-snug mt-0.5">
                    {group.purpose}
                  </p>
                </div>
                <span className="text-[11px] text-text-secondary tabular-nums">
                  {group.rows.length} {group.rows.length === 1 ? "table" : "tables"}
                </span>
              </div>

              <div className="overflow-x-auto -mx-1 px-1">
                <table className="w-full min-w-[640px] text-left border-collapse">
                  <thead>
                    <tr className="text-[10px] uppercase tracking-widest text-text-secondary">
                      <Th>Table</Th>
                      <Th>.sq file</Th>
                      <Th>Cols</Th>
                      <Th>Primary key</Th>
                      <Th>Queries</Th>
                      <Th>Callers</Th>
                      <Th>Note</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {group.rows.map((r) => (
                      <tr
                        key={r.table}
                        className="border-t border-border hover:bg-canvas/50 transition-colors align-top"
                      >
                        <Td className="font-mono font-semibold text-text-primary whitespace-nowrap">
                          {r.table}
                        </Td>
                        <Td className="font-mono text-text-secondary whitespace-nowrap">
                          {r.sqFile}
                        </Td>
                        <Td className="font-mono text-text-primary tabular-nums">
                          {r.columns}
                        </Td>
                        <Td className="font-mono text-text-secondary whitespace-nowrap">
                          {r.pk}
                        </Td>
                        <Td className="font-mono text-text-primary tabular-nums">
                          {r.queries}
                        </Td>
                        <Td className="font-mono tabular-nums">
                          {r.callers === "dead" ? (
                            <span className="text-[var(--c-danger)] font-semibold">
                              dead
                            </span>
                          ) : (
                            <span className="text-text-primary">{r.callers}</span>
                          )}
                        </Td>
                        <Td className="text-text-secondary">{r.note}</Td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ))}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 3 — MERGE CANDIDATES
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§3 — Merge Candidates"
        title="8 groups analyzed"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-warning)" size="sm" />
            {MERGE_CANDIDATES.length} groups
          </span>
        }
      >
        <p className="text-[12.5px] text-text-secondary leading-relaxed mb-4">
          Each candidate shows: current state, merge proposal, pros, cons, and
          a recommendation badge.{" "}
          <RecommendationBadge recommendation="DROP" /> = delete (dead code).{" "}
          <RecommendationBadge recommendation="MERGE" /> = combine (low risk).{" "}
          <RecommendationBadge recommendation="KEEP_SEPARATE" /> = leave as-is.{" "}
          <RecommendationBadge recommendation="INVESTIGATE" /> = needs more analysis.
        </p>

        <div className="space-y-3">
          {MERGE_CANDIDATES.map((c) => (
            <MergeCandidateCard key={c.id} candidate={c} />
          ))}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 4 — TOP 3 IMPROVEMENTS
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§4 — Top 3 Improvements"
        title="Ranked by impact"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-primary)" size="sm" />
            26 → 23 tables
          </span>
        }
      >
        <p className="text-[12.5px] text-text-secondary leading-relaxed mb-4">
          The 3 highest-impact optimizations, ranked. Each shows the action,
          detail, risk level, and cumulative table count after applying it.
          Apply in order — improvement #2 assumes #1 is done; #3 assumes #1 + #2.
        </p>

        <div className="space-y-3">
          {TOP_IMPROVEMENTS.map((imp) => {
            const risk = RISK_META[imp.risk];
            return (
              <div
                key={imp.rank}
                className="flex items-start gap-4 rounded-[14px] border border-border bg-surface-alt/40 p-4"
              >
                <div
                  className="inline-flex items-center justify-center w-9 h-9 rounded-full shrink-0 font-mono text-[14px] font-bold"
                  style={{
                    backgroundColor: "color-mix(in srgb, var(--c-primary) 15%, transparent)",
                    color: "var(--c-primary)",
                    border: "1.5px solid var(--c-primary)",
                  }}
                  aria-hidden="true"
                >
                  {imp.rank}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-start justify-between gap-3 flex-wrap mb-1">
                    <h3 className="text-[14px] font-bold tracking-extra-tight text-text-primary leading-tight">
                      {imp.title}
                    </h3>
                    <div className="flex items-center gap-2 shrink-0">
                      <RiskBadge risk={imp.risk} />
                      <span
                        className="inline-flex items-center gap-1.5 h-6 px-2.5 rounded-full text-[10.5px] font-medium border whitespace-nowrap"
                        style={{
                          backgroundColor: `color-mix(in srgb, ${risk.colorVar} 12%, transparent)`,
                          borderColor: `color-mix(in srgb, ${risk.colorVar} 35%, transparent)`,
                          color: risk.colorVar,
                        }}
                      >
                        <span className="font-mono">→</span>
                        <span className="font-mono font-semibold tabular-nums">
                          {imp.tableCountAfter}
                        </span>
                        <span className="opacity-80">tables</span>
                      </span>
                    </div>
                  </div>
                  <p className="text-[12.5px] text-text-primary leading-relaxed mb-2">
                    <span className="font-medium">Action:</span> {imp.action}
                  </p>
                  <p className="text-[12.5px] text-text-secondary leading-relaxed mb-2">
                    {imp.detail}
                  </p>
                  <div className="flex flex-wrap items-center gap-3 mt-2.5 pt-2.5 border-t border-border/60 text-[11.5px]">
                    <span className="inline-flex items-center gap-1.5 text-text-secondary">
                      <span className="font-mono text-text-secondary">📂</span>
                      <span className="font-medium">Files touched:</span>
                      <span className="font-mono text-text-primary tabular-nums">
                        {imp.filesTouched}
                      </span>
                    </span>
                    <span className="text-text-secondary">·</span>
                    <span className="text-text-secondary">
                      <span className="font-medium text-text-primary">Why:</span>{" "}
                      {imp.rationale}
                    </span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 5 — OVERALL ASSESSMENT
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§5 — Overall Assessment"
        title="Strengths + weaknesses"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-success)" size="sm" />
            {ASSESSMENT.currentTableCount} → {ASSESSMENT.idealTableCount} tables
          </span>
        }
      >
        <div
          className="rounded-[14px] border p-4 mb-5"
          style={{
            backgroundColor: "color-mix(in srgb, var(--c-success) 8%, transparent)",
            borderColor: "color-mix(in srgb, var(--c-success) 30%, transparent)",
          }}
        >
          <div className="flex items-baseline gap-2 mb-2">
            <span className="text-[10.5px] font-medium uppercase tracking-widest text-[var(--c-success)]">
              Verdict
            </span>
          </div>
          <div className="text-[16px] sm:text-[18px] font-bold tracking-extra-tight text-text-primary mb-3 leading-tight">
            {ASSESSMENT.verdict}
          </div>
          <div className="text-[12px] text-text-secondary leading-relaxed">
            Ideal table count after all 3 optimizations:{" "}
            <span className="font-mono font-semibold text-text-primary">
              {ASSESSMENT.idealTableCount}
            </span>{" "}
            (from current{" "}
            <span className="font-mono font-semibold text-text-primary">
              {ASSESSMENT.currentTableCount}
            </span>
            ). Bottom line: {ASSESSMENT.bottomLine}
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div className="rounded-[14px] border border-border bg-surface-alt/40 p-4">
            <div className="flex items-center gap-2 mb-3">
              <span
                className="inline-flex items-center justify-center w-6 h-6 rounded-[8px] text-[12px] font-bold"
                style={{
                  backgroundColor: "color-mix(in srgb, var(--c-success) 15%, transparent)",
                  color: "var(--c-success)",
                  border: "1.5px solid var(--c-success)",
                }}
                aria-hidden="true"
              >
                ✓
              </span>
              <h3 className="text-[13px] font-bold tracking-extra-tight text-text-primary">
                Strengths ({ASSESSMENT.strengths.length})
              </h3>
            </div>
            <ul className="space-y-2">
              {ASSESSMENT.strengths.map((s, i) => (
                <li
                  key={i}
                  className="flex items-start gap-2 text-[12px] text-text-primary leading-relaxed"
                >
                  <span
                    className="font-mono text-[12px] shrink-0 mt-[1px] text-[var(--c-success)]"
                    aria-hidden="true"
                  >
                    ●
                  </span>
                  <span className="min-w-0 break-words">{s}</span>
                </li>
              ))}
            </ul>
          </div>

          <div className="rounded-[14px] border border-border bg-surface-alt/40 p-4">
            <div className="flex items-center gap-2 mb-3">
              <span
                className="inline-flex items-center justify-center w-6 h-6 rounded-[8px] text-[12px] font-bold"
                style={{
                  backgroundColor: "color-mix(in srgb, var(--c-warning) 15%, transparent)",
                  color: "var(--c-warning)",
                  border: "1.5px solid var(--c-warning)",
                }}
                aria-hidden="true"
              >
                ▲
              </span>
              <h3 className="text-[13px] font-bold tracking-extra-tight text-text-primary">
                Weaknesses ({ASSESSMENT.weaknesses.length})
              </h3>
            </div>
            <ul className="space-y-2">
              {ASSESSMENT.weaknesses.map((w, i) => (
                <li
                  key={i}
                  className="flex items-start gap-2 text-[12px] text-text-primary leading-relaxed"
                >
                  <span
                    className="font-mono text-[12px] shrink-0 mt-[1px] text-[var(--c-warning)]"
                    aria-hidden="true"
                  >
                    ▲
                  </span>
                  <span className="min-w-0 break-words">{w}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 6 — FOOTER NOTE
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <div className="flex items-start gap-3 mb-2">
          <span
            className="inline-flex items-center justify-center w-7 h-7 rounded-[8px] shrink-0 text-[12px] font-bold"
            style={{
              backgroundColor: "color-mix(in srgb, var(--c-secondary) 15%, transparent)",
              color: "var(--c-secondary)",
              border: "1.5px solid var(--c-secondary)",
            }}
            aria-hidden="true"
          >
            !
          </span>
          <div>
            <div className="text-[10.5px] font-medium uppercase tracking-widest text-[var(--c-secondary)] mb-0.5">
              §6 — Footer Note
            </div>
            <h2 className="text-[16px] font-bold tracking-extra-tight text-text-primary leading-tight">
              Proposal — not implemented
            </h2>
          </div>
        </div>
        <ul className="space-y-1.5 mt-3">
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
    <th className="py-2 pr-3 font-medium text-text-secondary text-[10px] uppercase tracking-widest first:pl-0 last:pr-0">
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
    <td className={`py-2.5 pr-3 text-[12px] leading-snug first:pl-0 last:pr-0 ${className}`}>
      {children}
    </td>
  );
}

function RecommendationBadge({
  recommendation,
}: {
  recommendation: Recommendation;
}) {
  const meta = RECOMMENDATION_META[recommendation];
  return (
    <span
      className="inline-flex items-center gap-1.5 h-6 px-2.5 rounded-full text-[10.5px] font-medium border whitespace-nowrap align-middle"
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
  );
}

function RiskBadge({ risk }: { risk: RiskLevel }) {
  const meta = RISK_META[risk];
  return (
    <span
      className="inline-flex items-center gap-1.5 h-6 px-2.5 rounded-full text-[10.5px] font-medium border whitespace-nowrap"
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
  );
}

function MergeCandidateCard({
  candidate,
}: {
  candidate: (typeof MERGE_CANDIDATES)[number];
}) {
  const meta = RECOMMENDATION_META[candidate.recommendation];
  return (
    <div
      className="rounded-[14px] border bg-surface-alt/40 p-4"
      style={{
        borderColor: `color-mix(in srgb, ${meta.colorVar} 25%, var(--c-border))`,
      }}
    >
      <div className="flex items-start justify-between gap-3 flex-wrap mb-3">
        <div className="flex items-start gap-2.5 min-w-0 flex-1">
          <span className="font-mono text-[12px] font-semibold text-text-secondary shrink-0 mt-[1px]">
            #{candidate.id}
          </span>
          <div className="min-w-0">
            <h3 className="text-[14px] font-bold tracking-extra-tight text-text-primary leading-tight">
              {candidate.group}
            </h3>
            <div className="flex flex-wrap gap-1 mt-1.5">
              {candidate.tables.map((t) => (
                <span
                  key={t}
                  className="inline-flex items-center h-5 px-2 rounded-[6px] text-[10.5px] font-mono bg-chip border border-border text-text-secondary"
                >
                  {t}
                </span>
              ))}
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <RecommendationBadge recommendation={candidate.recommendation} />
          {candidate.netChange !== 0 && (
            <span
              className="inline-flex items-center gap-1 h-6 px-2 rounded-[8px] text-[10.5px] font-medium border whitespace-nowrap"
              style={{
                backgroundColor: `color-mix(in srgb, ${meta.colorVar} 12%, transparent)`,
                borderColor: `color-mix(in srgb, ${meta.colorVar} 35%, transparent)`,
                color: meta.colorVar,
              }}
            >
              <span className="font-mono font-semibold tabular-nums">
                {candidate.netChange > 0 ? "+" : ""}
                {candidate.netChange}
              </span>
              <span className="opacity-80">tables</span>
            </span>
          )}
        </div>
      </div>

      <div className="space-y-2.5 pl-7">
        <div className="text-[12px] text-text-primary leading-relaxed">
          <span className="font-medium text-[var(--c-secondary)]">
            Current:
          </span>{" "}
          {candidate.currentState}
        </div>
        <div className="text-[12px] text-text-primary leading-relaxed">
          <span className="font-medium text-[var(--c-primary)]">Proposal:</span>{" "}
          {candidate.proposal}
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-2">
          <div>
            <div className="text-[10px] font-medium uppercase tracking-widest text-[var(--c-success)] mb-1.5">
              Pros
            </div>
            <ul className="space-y-1">
              {candidate.pros.map((p, i) => (
                <li
                  key={i}
                  className="flex items-start gap-1.5 text-[11.5px] text-text-secondary leading-relaxed"
                >
                  <span
                    className="font-mono text-[11px] shrink-0 mt-[1px] text-[var(--c-success)]"
                    aria-hidden="true"
                  >
                    +
                  </span>
                  <span className="min-w-0 break-words">{p}</span>
                </li>
              ))}
            </ul>
          </div>
          <div>
            <div className="text-[10px] font-medium uppercase tracking-widest text-[var(--c-danger)] mb-1.5">
              Cons
            </div>
            <ul className="space-y-1">
              {candidate.cons.map((c, i) => (
                <li
                  key={i}
                  className="flex items-start gap-1.5 text-[11.5px] text-text-secondary leading-relaxed"
                >
                  <span
                    className="font-mono text-[11px] shrink-0 mt-[1px] text-[var(--c-danger)]"
                    aria-hidden="true"
                  >
                    −
                  </span>
                  <span className="min-w-0 break-words">{c}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}
