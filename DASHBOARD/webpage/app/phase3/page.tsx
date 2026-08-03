"use client";

import Link from "next/link";
import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import {
  SUB_PHASES,
  PHASE3_MODULES,
  PHASE3_OPEN_QUESTIONS,
  PHASE3_RISKS,
  PHASE3_DELIVERABLES,
  PHASE3_SUMMARY,
  DEP_GRAPH_NODES,
  DEP_GRAPH_EDGES,
  type SubPhase,
  type Phase3Module,
  type SubPhaseId,
} from "@/lib/phase3";

/* ---------------------------------------------------------------------------
 * Sub-phase color lookup.
 * ------------------------------------------------------------------------- */
const SUB_PHASE_COLOR: Record<SubPhaseId, string> = SUB_PHASES.reduce(
  (acc, s) => ({ ...acc, [s.id]: s.color }),
  {} as Record<SubPhaseId, string>,
);

const SUB_PHASE_META: Record<SubPhaseId, SubPhase> = SUB_PHASES.reduce(
  (acc, s) => ({ ...acc, [s.id]: s }),
  {} as Record<SubPhaseId, SubPhase>,
);

/* ---------------------------------------------------------------------------
 * Page
 * ------------------------------------------------------------------------- */
export default function Phase3Page() {
  return (
    <div className="space-y-6">
      {/* ---- Hero / summary ---- */}
      <Card className="!p-6 md:!p-8">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[11px] font-medium uppercase tracking-widest text-text-secondary">
              Core Modules Plan
            </span>
            <StatusDot color="var(--c-success)" size="sm" />
            <span className="text-[12px] text-text-secondary">
              ✓ Complete — 15 modules built across 4 sub-phases
            </span>
          </div>
          <h2 className="text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
            Phase 3 Plan{" "}
            <span className="text-text-secondary font-medium">
              — 15 modules · 4 sub-phases · ✓ done
            </span>
          </h2>
          <p className="text-[13.5px] text-text-secondary leading-relaxed max-w-2xl">
            Phase 3 built the 15 core infrastructure modules in 4 sub-phases
            (3a Foundation, 3b Extensions, 3c Playback, 3d Supporting). All
            complete — the app can now resolve video → watch (MPV) → track
            progress. Library + history screens come in Phase 4. Documented in{" "}
            <code className="font-mono text-text-primary">
              18-phase3-plan.md
            </code>
            .
          </p>

          {/* Summary stat strip */}
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 gap-2 pt-1">
            <SummaryStat
              label="Modules"
              value={String(PHASE3_SUMMARY.totalModules)}
              accent="var(--c-primary)"
            />
            <SummaryStat
              label="Sub-phases"
              value={String(PHASE3_SUMMARY.totalSubPhases)}
              accent="var(--c-secondary)"
            />
            <SummaryStat
              label="Dependencies"
              value={String(PHASE3_SUMMARY.totalDependencies)}
              accent="var(--c-warning)"
            />
            <SummaryStat
              label="Open questions"
              value={String(PHASE3_SUMMARY.totalOpenQuestions)}
              accent="var(--c-danger)"
            />
            <SummaryStat
              label="Risks"
              value={String(PHASE3_SUMMARY.totalRisks)}
              accent="var(--c-warning)"
            />
            <SummaryStat
              label="Deliverables"
              value={String(PHASE3_SUMMARY.totalDeliverables)}
              accent="var(--c-success)"
            />
          </div>
        </div>
      </Card>

      {/* ---- Sub-phase timeline ---- */}
      <Card>
        <div className="flex items-start justify-between gap-3 mb-4 flex-wrap">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Sub-phase Timeline
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              4 sub-phases, each independently testable
            </h3>
          </div>
          <span className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium bg-chip border border-border text-text-secondary">
            <StatusDot color="var(--c-secondary)" size="sm" />
            3a → 3b → 3c → 3d
          </span>
        </div>

        <SubPhaseTimeline />
      </Card>

      {/* ---- Modules (grouped by sub-phase) ---- */}
      <Card>
        <div className="flex items-start justify-between gap-3 mb-4 flex-wrap">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Modules by Sub-phase
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              All 14 modules
            </h3>
          </div>
          <span className="text-[11px] text-text-secondary">
            colored by sub-phase · dependency pills below each module
          </span>
        </div>

        <div className="space-y-6">
          {SUB_PHASES.map((sp) => {
            const modules = PHASE3_MODULES.filter((m) => m.subPhase === sp.id);
            return (
              <section key={sp.id} className="space-y-3">
                <SubPhaseHeader subPhase={sp} count={modules.length} />
                <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                  {modules.map((m) => (
                    <ModuleCard key={m.name} module={m} />
                  ))}
                </div>
              </section>
            );
          })}
        </div>
      </Card>

      {/* ---- Build order ---- */}
      <Card>
        <div className="flex items-start justify-between gap-3 mb-4 flex-wrap">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Recommended Build Order
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              14-step build sequence
            </h3>
          </div>
          <span className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium bg-chip border border-border text-text-secondary">
            <StatusDot color="var(--c-primary)" size="sm" />
            dependency-ordered
          </span>
        </div>
        <p className="text-[12.5px] text-text-secondary leading-relaxed mb-4">
          Each step builds on what&apos;s already done. Note that{" "}
          <code className="font-mono text-text-primary">:core:watch-progress</code>{" "}
          (step 4) is built out-of-sequence — it&apos;s the contract interface
          needed before <code className="font-mono text-text-primary">:data:history</code>{" "}
          (step 5) can be implemented.
        </p>

        <ol className="space-y-2">
          {[...PHASE3_MODULES]
            .sort((a, b) => a.step - b.step)
            .map((m) => {
              const sp = SUB_PHASE_META[m.subPhase];
              return (
                <li
                  key={m.name}
                  className="flex items-start gap-3 p-3 rounded-[12px] border border-border bg-surface-alt/40 hover:bg-surface-alt transition-colors duration-150"
                >
                  <div
                    className="flex items-center justify-center w-7 h-7 rounded-full shrink-0 font-mono text-[12px] font-bold text-white"
                    style={{
                      backgroundColor: sp.color,
                      boxShadow: `0 4px 12px ${sp.color}33`,
                    }}
                  >
                    {m.step}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-baseline gap-2 flex-wrap">
                      <code className="font-mono text-[13px] font-semibold text-text-primary">
                        {m.name}
                      </code>
                      <span
                        className="inline-flex items-center h-5 px-1.5 rounded-[6px] text-[9.5px] font-medium uppercase tracking-wider"
                        style={{
                          backgroundColor: sp.softBg,
                          color: sp.color,
                        }}
                      >
                        {sp.label} · {sp.name}
                      </span>
                    </div>
                    <p className="text-[12px] text-text-secondary mt-1 leading-relaxed">
                      {m.purpose}
                    </p>
                  </div>
                </li>
              );
            })}
        </ol>
      </Card>

      {/* ---- Dependency graph ---- */}
      <Card>
        <div className="flex items-start justify-between gap-3 mb-3 flex-wrap">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Dependency Graph
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              How the 14 modules depend on each other
            </h3>
          </div>
          <span className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium bg-chip border border-border text-text-secondary">
            <StatusDot color="var(--c-secondary)" size="sm" />
            {DEP_GRAPH_NODES.length} nodes · {DEP_GRAPH_EDGES.length} edges
          </span>
        </div>
        <p className="text-[12.5px] text-text-secondary leading-relaxed mb-4">
          Arrows point from a module to its dependency.{" "}
          <code className="font-mono text-text-primary">:core:common</code> sits
          at the left as the foundation everyone depends on;{" "}
          <code className="font-mono text-text-primary">:core:backup</code>{" "}
          (step 14) is at the right — it ties everything together.
        </p>
        <DepGraph />

        <div className="mt-4 flex flex-wrap gap-3">
          {SUB_PHASES.map((sp) => (
            <div
              key={sp.id}
              className="inline-flex items-center gap-1.5 text-[11px] text-text-secondary"
            >
              <span
                className="w-2 h-2 rounded-full"
                style={{ backgroundColor: sp.color }}
                aria-hidden="true"
              />
              <span>
                {sp.label} · {sp.name}
              </span>
            </div>
          ))}
        </div>
      </Card>

      {/* ---- Open questions + Risks (two columns) ---- */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Open questions */}
        <Card>
          <div className="flex items-start justify-between gap-3 mb-3">
            <div>
              <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
                Open Questions
              </div>
              <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
                Need user input
              </h3>
            </div>
            <span className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium bg-chip border border-border text-text-secondary shrink-0">
              <StatusDot color="var(--c-danger)" size="sm" />
              {PHASE3_OPEN_QUESTIONS.length} questions
            </span>
          </div>

          <ul className="space-y-3">
            {PHASE3_OPEN_QUESTIONS.map((q) => (
              <li
                key={q.id}
                className="rounded-[12px] border border-border bg-surface-alt/40 p-3.5"
              >
                <div className="flex items-baseline gap-2 mb-2">
                  <span className="font-mono text-[12px] font-bold text-[var(--c-danger)]">
                    Q{q.id}
                  </span>
                  <span className="text-[12.5px] font-medium text-text-primary">
                    {q.topic}
                  </span>
                </div>
                <p className="text-[12.5px] text-text-primary leading-relaxed mb-2">
                  {q.question}
                </p>
                <div className="flex items-start gap-1.5 text-[11.5px] leading-relaxed">
                  <span className="text-[var(--c-success)] font-semibold shrink-0">
                    Recommend:
                  </span>
                  <span className="text-text-secondary">{q.recommendation}</span>
                </div>
                <div className="flex items-start gap-1.5 text-[11.5px] leading-relaxed mt-1">
                  <span className="text-text-secondary font-semibold shrink-0">
                    Impact:
                  </span>
                  <span className="text-text-secondary">{q.impact}</span>
                </div>
              </li>
            ))}
          </ul>
        </Card>

        {/* Risks + Deliverables */}
        <div className="space-y-4">
          <Card>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Risk Assessment
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary mb-3">
              {PHASE3_RISKS.length} risks tracked
            </h3>
            <ul className="space-y-2">
              {PHASE3_RISKS.map((r, i) => (
                <li
                  key={i}
                  className="flex items-start gap-2 text-[12px] leading-relaxed"
                >
                  <span
                    className="w-1.5 h-1.5 rounded-full mt-[7px] shrink-0"
                    style={{
                      backgroundColor:
                        r.likelihood === "High"
                          ? "var(--c-danger)"
                          : r.likelihood === "Medium"
                            ? "var(--c-warning)"
                            : "var(--c-success)",
                    }}
                    aria-hidden="true"
                  />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-baseline gap-2 flex-wrap">
                      <span className="text-text-primary font-medium">
                        {r.risk}
                      </span>
                      <span className="font-mono text-[10.5px] text-text-secondary">
                        {r.likelihood} likelihood · {r.impact} impact
                      </span>
                    </div>
                    <p className="text-[11.5px] text-text-secondary mt-0.5">
                      <span className="font-medium">Mitigation:</span>{" "}
                      {r.mitigation}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          </Card>

          <Card>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Phase 3 Deliverables
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary mb-3">
              7 capabilities shipped
            </h3>
            <ul className="space-y-1.5">
              {PHASE3_DELIVERABLES.map((d) => (
                <li
                  key={d.id}
                  className="flex items-start gap-2 text-[12.5px] leading-relaxed"
                >
                  <span className="font-mono text-[11px] font-bold text-[var(--c-success)] shrink-0 w-5">
                    {String(d.id).padStart(2, "0")}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="text-text-primary font-medium">
                      {d.label}
                    </span>
                    <span className="text-text-secondary"> — {d.detail}</span>
                  </span>
                </li>
              ))}
            </ul>
          </Card>
        </div>
      </div>

      {/* ---- Cross-links ---- */}
      <Card>
        <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-2">
          Related
        </div>
        <div className="flex flex-wrap gap-3 text-[11.5px]">
          <Link
            href="/database/"
            className="inline-flex items-center gap-1.5 text-text-primary font-medium hover:underline"
          >
            → View the 21-table database schema these modules build on
          </Link>
          <Link
            href="/architecture/"
            className="inline-flex items-center gap-1.5 text-text-secondary hover:text-text-primary hover:underline"
          >
            → Architecture plan (43 modules)
          </Link>
          <Link
            href="/decisions/"
            className="inline-flex items-center gap-1.5 text-text-secondary hover:text-text-primary hover:underline"
          >
            → Architecture decisions (D-027..D-053)
          </Link>
        </div>
      </Card>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * SubPhaseTimeline — 4 connected cards showing 3a → 3b → 3c → 3d.
 * ------------------------------------------------------------------------- */
function SubPhaseTimeline() {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
      {SUB_PHASES.map((sp, i) => (
        <div key={sp.id} className="relative">
          {/* Connector arrow to next sub-phase (desktop only) */}
          {i < SUB_PHASES.length - 1 && (
            <div
              className="hidden lg:flex absolute top-1/2 -right-2.5 -translate-y-1/2 z-10 items-center justify-center w-5 h-5 rounded-full bg-surface border border-border"
              aria-hidden="true"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="var(--c-text-secondary)"
                strokeWidth="2.4"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="w-3 h-3"
              >
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </div>
          )}

          <div
            className="h-full rounded-[14px] border bg-surface overflow-hidden transition-all duration-200 hover:shadow-[0_12px_40px_rgba(0,0,0,0.08)] hover:-translate-y-[1px]"
            style={{
              borderColor: `color-mix(in srgb, ${sp.color} 30%, var(--c-border))`,
            }}
          >
            {/* Top color bar */}
            <div
              className="h-1.5"
              style={{ backgroundColor: sp.color }}
              aria-hidden="true"
            />
            <div className="p-3.5">
              <div className="flex items-baseline gap-2 mb-1">
                <span
                  className="font-mono text-[18px] font-bold tracking-extra-tight"
                  style={{ color: sp.color }}
                >
                  {sp.label}
                </span>
                <span className="text-[13px] font-bold tracking-extra-tight text-text-primary">
                  {sp.name}
                </span>
              </div>
              <p className="text-[11.5px] text-text-secondary leading-relaxed mb-3">
                {sp.delivers}
              </p>
              <div className="flex items-center justify-between text-[11px]">
                <span className="text-text-secondary">
                  <span
                    className="font-mono font-bold"
                    style={{ color: sp.color }}
                  >
                    {sp.moduleCount}
                  </span>{" "}
                  modules
                </span>
                <span
                  className="inline-flex items-center h-5 px-1.5 rounded-full text-[9.5px] font-medium uppercase tracking-wider"
                  style={{ backgroundColor: sp.softBg, color: sp.color }}
                >
                  sub-phase {i + 1}/4
                </span>
              </div>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * SubPhaseHeader — small group header above each sub-phase's module cards.
 * ------------------------------------------------------------------------- */
function SubPhaseHeader({
  subPhase,
  count,
}: {
  subPhase: SubPhase;
  count: number;
}) {
  return (
    <div className="flex items-center gap-2 px-1">
      <span
        className="w-2.5 h-2.5 rounded-full"
        style={{ backgroundColor: subPhase.color }}
        aria-hidden="true"
      />
      <span
        className="font-mono text-[14px] font-bold"
        style={{ color: subPhase.color }}
      >
        {subPhase.label}
      </span>
      <span className="text-[14px] font-bold tracking-extra-tight text-text-primary">
        {subPhase.name}
      </span>
      <span className="text-[12px] text-text-secondary">·</span>
      <span className="text-[12px] text-text-secondary">
        {subPhase.delivers}
      </span>
      <span className="ml-auto text-[11px] text-text-secondary tabular-nums">
        {count} {count === 1 ? "module" : "modules"}
      </span>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * ModuleCard — one module within a sub-phase.
 * ------------------------------------------------------------------------- */
function ModuleCard({ module: m }: { module: Phase3Module }) {
  const sp = SUB_PHASE_META[m.subPhase];

  return (
    <div
      className="flex flex-col rounded-[14px] border bg-surface overflow-hidden transition-all duration-200 hover:shadow-[0_12px_40px_rgba(0,0,0,0.08)] hover:-translate-y-[1px]"
      style={{
        borderColor: `color-mix(in srgb, ${sp.color} 25%, var(--c-border))`,
      }}
    >
      {/* Header — step number + module name + sub-phase pill */}
      <div
        className="flex items-center gap-2.5 px-3.5 py-2.5 border-b"
        style={{
          backgroundColor: sp.softBg,
          borderColor: `color-mix(in srgb, ${sp.color} 20%, var(--c-border))`,
        }}
      >
        <div
          className="flex items-center justify-center w-7 h-7 rounded-full shrink-0 font-mono text-[11px] font-bold text-white"
          style={{
            backgroundColor: sp.color,
            boxShadow: `0 4px 10px ${sp.color}33`,
          }}
          title={`Build order step ${m.step}`}
        >
          {m.step}
        </div>
        <code
          className="font-mono text-[12.5px] font-semibold text-text-primary truncate flex-1"
          title={m.name}
        >
          {m.name}
        </code>
      </div>

      {/* Body */}
      <div className="flex-1 px-3.5 py-2.5 space-y-2.5">
        {/* Sub-phase kicker */}
        <div className="text-[9.5px] font-medium uppercase tracking-widest text-text-secondary">
          {sp.label} · {sp.name}
        </div>

        {/* Purpose */}
        <p className="text-[12px] text-text-primary leading-relaxed">
          {m.purpose}
        </p>

        {/* Key files */}
        {m.keyFiles && m.keyFiles.length > 0 && (
          <div>
            <div className="text-[9px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Key files
            </div>
            <div className="flex flex-wrap gap-1">
              {m.keyFiles.slice(0, 5).map((f) => (
                <span
                  key={f}
                  className="inline-flex items-center h-4 px-1.5 rounded-[4px] text-[9px] font-mono bg-surface-alt border border-border text-text-secondary"
                >
                  {f}
                </span>
              ))}
              {m.keyFiles.length > 5 && (
                <span className="inline-flex items-center h-4 px-1 text-[9px] text-text-secondary">
                  +{m.keyFiles.length - 5} more
                </span>
              )}
            </div>
          </div>
        )}

        {/* Dependencies */}
        <div>
          <div className="text-[9px] font-medium uppercase tracking-widest text-text-secondary mb-1">
            Depends on
          </div>
          <div className="flex flex-wrap gap-1">
            {m.dependsOn.map((dep) => {
              // Internal ANI-KUTA modules get colored pills; external libs stay neutral.
              const isInternal = dep.startsWith(":");
              const depColor = isInternal ? sp.color : "var(--c-text-secondary)";
              return (
                <span
                  key={dep}
                  className="inline-flex items-center h-5 px-1.5 rounded-full text-[9.5px] font-mono border"
                  style={{
                    backgroundColor: isInternal
                      ? `color-mix(in srgb, ${depColor} 8%, transparent)`
                      : "var(--c-chip)",
                    borderColor: isInternal
                      ? `color-mix(in srgb, ${depColor} 30%, var(--c-border))`
                      : "var(--c-border)",
                    color: isInternal ? depColor : "var(--c-text-secondary)",
                  }}
                  title={dep}
                >
                  {dep}
                </span>
              );
            })}
          </div>
        </div>

        {/* Deliverable */}
        {m.deliverable && (
          <div className="pt-2 border-t border-border/60">
            <div className="text-[9px] font-medium uppercase tracking-widest text-[var(--c-success)] mb-1">
              Deliverable
            </div>
            <p className="text-[11.5px] text-text-secondary leading-relaxed">
              {m.deliverable}
            </p>
          </div>
        )}

        {/* Note (layering / design constraint) */}
        {m.note && (
          <div className="pt-1">
            <div className="text-[9px] font-medium uppercase tracking-widest text-[var(--c-warning)] mb-1">
              Note
            </div>
            <p className="text-[11px] text-text-secondary leading-relaxed italic">
              {m.note}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * DepGraph — SVG dependency graph.
 *
 * Layout: 8-column × 8-row grid. Each node positioned by col/row.
 * Edges drawn as quadratic Bézier curves between node centers.
 * ------------------------------------------------------------------------- */
function DepGraph() {
  const COLS = 8;
  const ROWS = 8;
  const cellW = 130;
  const cellH = 60;
  const svgW = COLS * cellW;
  const svgH = ROWS * cellH;

  const nodeCenter = (id: string) => {
    const n = DEP_GRAPH_NODES.find((x) => x.id === id);
    if (!n) return { cx: 0, cy: 0 };
    return {
      cx: (n.col - 0.5) * cellW,
      cy: (n.row - 0.5) * cellH,
    };
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
        <svg
          viewBox={`0 0 ${svgW} ${svgH}`}
          preserveAspectRatio="xMidYMid meet"
          className="absolute inset-0 w-full h-full"
          aria-hidden="true"
        >
          <defs>
            <marker
              id="dep-arrow"
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
                opacity="0.55"
              />
            </marker>
          </defs>

          {/* Edges */}
          {DEP_GRAPH_EDGES.map((e, i) => {
            const a = nodeCenter(e.from);
            const b = nodeCenter(e.to);
            const mx = (a.cx + b.cx) / 2;
            const my = (a.cy + b.cy) / 2;
            const dx = b.cx - a.cx;
            const dy = b.cy - a.cy;
            const curve = Math.abs(dx) > Math.abs(dy) ? dy * 0.15 : dx * 0.15;

            return (
              <g key={`dge-${i}`}>
                <path
                  d={`M ${a.cx} ${a.cy} Q ${mx + curve} ${my - curve} ${b.cx} ${b.cy}`}
                  fill="none"
                  stroke="var(--c-text-secondary)"
                  strokeOpacity="0.3"
                  strokeWidth="1.1"
                  markerEnd="url(#dep-arrow)"
                />
                {e.label && (
                  <text
                    x={mx}
                    y={my - 3}
                    textAnchor="middle"
                    className="font-mono"
                    style={{
                      fontSize: 7,
                      fill: "var(--c-text-secondary)",
                      opacity: 0.75,
                    }}
                  >
                    {e.label}
                  </text>
                )}
              </g>
            );
          })}

          {/* Nodes */}
          {DEP_GRAPH_NODES.map((n) => {
            const sp = SUB_PHASE_META[n.subPhase];
            const c = nodeCenter(n.id);
            const w = cellW * 0.85;
            const h = cellH * 0.55;
            return (
              <g key={n.id}>
                <rect
                  x={c.cx - w / 2}
                  y={c.cy - h / 2}
                  width={w}
                  height={h}
                  rx={6}
                  ry={6}
                  fill="var(--c-surface)"
                  stroke={sp.color}
                  strokeWidth={1.2}
                />
                {n.step > 0 && (
                  <circle
                    cx={c.cx - w / 2 + 6}
                    cy={c.cy - h / 2 + 6}
                    r={5}
                    fill={sp.color}
                  />
                )}
                <text
                  x={c.cx}
                  y={c.cy + 2}
                  textAnchor="middle"
                  className="font-mono"
                  style={{
                    fontSize: 7.5,
                    fill: "var(--c-text-primary)",
                    fontWeight: 600,
                  }}
                >
                  {n.label.length > 22
                    ? n.label.slice(0, 21) + "…"
                    : n.label}
                </text>
                {n.step > 0 && (
                  <text
                    x={c.cx}
                    y={c.cy + h / 2 - 4}
                    textAnchor="middle"
                    className="font-mono"
                    style={{
                      fontSize: 6,
                      fill: sp.color,
                      fontWeight: 700,
                    }}
                  >
                    step {n.step}
                  </text>
                )}
              </g>
            );
          })}
        </svg>
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
