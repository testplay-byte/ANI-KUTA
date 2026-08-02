import Link from "next/link";
import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import { MetricCard } from "@/components/MetricCard";
import { PhaseTimeline } from "@/components/PhaseTimeline";
import { WorkflowLoop } from "@/components/WorkflowLoop";
import { METRIC_CARDS, QUICK_STATS, PHASES } from "@/lib/data";
import { decisions } from "@/lib/decisions";

export default function OverviewPage() {
  const currentPhase =
    PHASES.find((p) => p.status === "in-progress" || p.status === "blocked") ??
    PHASES[1];
  const needsInputCount = decisions.filter(
    (d) => d.status === "needs-input",
  ).length;

  return (
    <div className="space-y-6">
      {/* Hero — project summary */}
      <Card className="!p-6 md:!p-8">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[11px] font-medium uppercase tracking-widest text-text-secondary">
              Project Status
            </span>
            <StatusDot color="var(--c-danger)" size="sm" />
            <span className="text-[12px] text-text-secondary">
              Phase {currentPhase.id} — {currentPhase.name} · blocked on {needsInputCount} decisions
            </span>
          </div>
          <h2 className="text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
            ANI-KUTA{" "}
            <span className="text-text-secondary font-medium">
              — anime streaming app rebuild
            </span>
          </h2>
          <p className="text-[13.5px] text-text-secondary leading-relaxed max-w-2xl">
            A calm, living dashboard for the ANI-KUTA project: modules, decisions,
            progress, architecture, analytics, and planning. Kept in sync with{" "}
            <code className="font-mono text-text-primary">AGENT-CONTEXT/</code>{" "}
            on every push.
          </p>
          <div className="flex flex-wrap gap-2 pt-1">
            <Link href="/decisions/" className="no-underline">
              <span
                className="inline-flex items-center gap-2 h-9 px-[18px] rounded-[12px] text-[13.5px] font-medium text-white transition-all duration-200 hover:translate-y-[-1px]"
                style={{
                  backgroundColor: "var(--c-primary)",
                  boxShadow: "0 4px 12px var(--c-primary)33, 0 1px 2px rgba(0,0,0,0.06)",
                }}
              >
                Review Decisions
                <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-white/20 text-[10px] font-bold">
                  {needsInputCount}
                </span>
              </span>
            </Link>
            <Link href="/architecture/" className="no-underline">
              <span className="inline-flex items-center gap-2 h-9 px-[18px] rounded-[12px] text-[13.5px] font-medium bg-chip border border-border text-text-secondary transition-all duration-200 hover:translate-y-[-1px] hover:text-text-primary">
                View Architecture
              </span>
            </Link>
          </div>
        </div>
      </Card>

      {/* Metric cards with sparklines */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {METRIC_CARDS.map((m) => (
          <MetricCard key={m.label} metric={m} />
        ))}
      </div>

      {/* Phase timeline */}
      <Card>
        <PhaseTimeline />
      </Card>

      {/* Workflow loop */}
      <Card>
        <WorkflowLoop />
      </Card>

      {/* Two-column: current phase + decisions summary */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Current phase */}
        <Card>
          <div className="flex items-start justify-between gap-3 mb-3">
            <div>
              <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
                Current Phase
              </div>
              <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
                Phase {currentPhase.id} — {currentPhase.name}
              </h3>
            </div>
            <span
              className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium shrink-0"
              style={{
                backgroundColor: "var(--c-danger)1a",
                color: "var(--c-danger)",
              }}
            >
              <StatusDot color="var(--c-danger)" size="sm" />
              Blocked
            </span>
          </div>
          <p className="text-[13px] text-text-secondary leading-relaxed mb-4">
            {currentPhase.summary}
          </p>

          {currentPhase.blockers.length > 0 && (
            <div className="space-y-2 mb-4">
              <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary">
                Blockers
              </div>
              {currentPhase.blockers.map((b) => (
                <div
                  key={b}
                  className="flex items-start gap-2 text-[12.5px] text-text-primary"
                >
                  <StatusDot
                    color="var(--c-danger)"
                    size="sm"
                    className="mt-[7px]"
                  />
                  <span>{b}</span>
                </div>
              ))}
            </div>
          )}

          {currentPhase.next.length > 0 && (
            <div className="space-y-2">
              <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary">
                Up Next
              </div>
              {currentPhase.next.map((n) => (
                <div
                  key={n}
                  className="flex items-start gap-2 text-[12.5px] text-text-primary"
                >
                  <StatusDot
                    color="var(--c-warning)"
                    size="sm"
                    className="mt-[7px]"
                  />
                  <span>{n}</span>
                </div>
              ))}
            </div>
          )}

          <Link
            href="/progress/"
            className="inline-flex items-center gap-1 text-[12px] font-medium text-[var(--c-primary)] mt-4 hover:underline"
          >
            View all phases →
          </Link>
        </Card>

        {/* Decisions summary */}
        <Card>
          <div className="flex items-start justify-between gap-3 mb-3">
            <div>
              <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
                Decisions
              </div>
              <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
                {needsInputCount} need your input
              </h3>
            </div>
            <span className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium bg-chip border border-border text-text-secondary shrink-0">
              {decisions.length} total
            </span>
          </div>

          <div className="space-y-2">
            {decisions
              .filter((d) => d.status === "needs-input")
              .slice(0, 5)
              .map((d) => (
                <Link
                  key={d.id}
                  href="/decisions/"
                  className="flex items-center gap-3 p-2.5 rounded-[10px] border border-border bg-surface-alt/40 hover:bg-canvas transition-colors duration-150 no-underline"
                >
                  <span className="font-mono text-[11px] text-text-secondary shrink-0 w-14">
                    {d.id}
                  </span>
                  <span className="text-[12.5px] font-medium text-text-primary truncate flex-1">
                    {d.title}
                  </span>
                  <span className="w-2 h-2 rounded-full bg-[var(--c-danger)] shrink-0" />
                </Link>
              ))}
          </div>

          <Link
            href="/decisions/"
            className="inline-flex items-center gap-1 text-[12px] font-medium text-[var(--c-primary)] mt-4 hover:underline"
          >
            Review all decisions →
          </Link>
        </Card>
      </div>

      {/* Quick stats footer row */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <QuickStat label="Total files" value={String(QUICK_STATS.totalFiles)} accent="var(--c-primary)" />
        <QuickStat label="Total phases" value={String(QUICK_STATS.phases)} accent="var(--c-secondary)" />
        <QuickStat label="Timeline" value={`${QUICK_STATS.totalDays}d`} accent="var(--c-success)" />
        <QuickStat label="Blockers" value={String(QUICK_STATS.blockers)} accent="var(--c-danger)" />
      </div>
    </div>
  );
}

function QuickStat({
  label,
  value,
  accent,
}: {
  label: string;
  value: string;
  accent: string;
}) {
  return (
    <div className="rounded-[14px] border border-border bg-surface p-3.5">
      <div className="flex items-center gap-2 mb-1.5">
        <span
          className="w-1.5 h-1.5 rounded-full"
          style={{ backgroundColor: accent }}
          aria-hidden="true"
        />
        <span className="text-[10px] font-medium uppercase tracking-widest text-text-secondary">
          {label}
        </span>
      </div>
      <div className="text-[20px] font-bold tracking-extra-tight text-text-primary leading-none">
        {value}
      </div>
    </div>
  );
}
