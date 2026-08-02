import Link from "next/link";
import { Card, CardHeader } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import {
  QUICK_STATS,
  OPEN_QUESTIONS,
  PHASES,
  STATUS_META,
} from "@/lib/data";

export default function OverviewPage() {
  const currentPhase = PHASES.find((p) => p.status === "in-progress" || p.status === "blocked") ?? PHASES[1];

  return (
    <div className="space-y-6">
      {/* Hero */}
      <Card className="!p-6 md:!p-8">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-2">
            <span className="text-[11px] font-medium uppercase tracking-wide text-text-secondary">
              Project Status
            </span>
            <StatusDot color="var(--c-warning)" size="sm" />
            <span className="text-[12px] text-text-secondary">
              Phase {currentPhase.id} — {currentPhase.name}
            </span>
          </div>
          <h1 className="text-[28px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
            ANI-KUTA{" "}
            <span className="text-text-secondary font-medium">
              — visual documentation dashboard
            </span>
          </h1>
          <p className="text-[13.5px] text-text-secondary leading-relaxed max-w-2xl">
            A calm, living view of the ANI-KUTA project: modules, decisions,
            progress, and architecture. Read-only documentation for the user —
            kept in sync with{" "}
            <code className="font-mono text-text-primary">AGENT-CONTEXT/</code>{" "}
            on every push.
          </p>
          <div className="flex flex-wrap gap-2 pt-1">
            <Link href="/modules/" className="no-underline">
              <span className="inline-flex items-center gap-2 h-9 px-[18px] rounded-full text-[13.5px] font-medium text-white transition-all duration-200 hover:translate-y-[-1px]"
                style={{
                  backgroundColor: "var(--c-primary)",
                  boxShadow: "0 4px 12px var(--c-primary)33, 0 1px 2px rgba(0,0,0,0.06)",
                }}>
                Browse Modules
              </span>
            </Link>
            <Link href="/progress/" className="no-underline">
              <span className="inline-flex items-center gap-2 h-9 px-[18px] rounded-full text-[13.5px] font-medium bg-bg-chip border border-border text-text-secondary transition-all duration-200 hover:translate-y-[-1px]">
                View Progress
              </span>
            </Link>
          </div>
        </div>
      </Card>

      {/* Quick stats grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard
          label="Modules"
          value={QUICK_STATS.modules}
          accent="var(--c-primary)"
          href="/modules/"
        />
        <StatCard
          label="Decisions"
          value={QUICK_STATS.decisions}
          accent="var(--c-secondary)"
          href="/decisions/"
          sublabel={`${QUICK_STATS.decisionsConfirmed} confirmed`}
        />
        <StatCard
          label="Phases"
          value={`${QUICK_STATS.phasesDone}/${QUICK_STATS.phases}`}
          accent="var(--c-success)"
          href="/progress/"
          sublabel="done"
        />
        <StatCard
          label="Open Questions"
          value={QUICK_STATS.openQuestions}
          accent="var(--c-warning)"
          href="#open-questions"
          sublabel="awaiting input"
        />
      </div>

      {/* Two-column: current phase + open questions */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Card id="current-phase">
          <CardHeader
            kicker={`Phase ${currentPhase.id}`}
            title={currentPhase.name}
            right={
              <StatusBadge
                color={phaseStatusColor(currentPhase.status)}
                label={phaseStatusLabel(currentPhase.status)}
              />
            }
          />
          <p className="text-[13px] text-text-secondary leading-relaxed mb-4">
            {currentPhase.summary}
          </p>
          {currentPhase.blockers.length > 0 && (
            <div className="space-y-2">
              <div className="text-[11px] font-medium uppercase tracking-wide text-text-secondary">
                Blockers
              </div>
              {currentPhase.blockers.map((b) => (
                <div
                  key={b}
                  className="flex items-start gap-2 text-[13px] text-text-primary"
                >
                  <StatusDot color="var(--c-danger)" size="sm" className="mt-[7px]" />
                  <span>{b}</span>
                </div>
              ))}
            </div>
          )}
          {currentPhase.next.length > 0 && (
            <div className="space-y-2 mt-4">
              <div className="text-[11px] font-medium uppercase tracking-wide text-text-secondary">
                Up Next
              </div>
              {currentPhase.next.map((n) => (
                <div
                  key={n}
                  className="flex items-start gap-2 text-[13px] text-text-primary"
                >
                  <StatusDot color="var(--c-warning)" size="sm" className="mt-[7px]" />
                  <span>{n}</span>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card id="open-questions">
          <CardHeader
            kicker="Open Questions"
            title="Awaiting user input"
            right={
              <StatusBadge
                color="var(--c-warning)"
                label={`${OPEN_QUESTIONS.length} open`}
              />
            }
          />
          <div className="space-y-3">
            {OPEN_QUESTIONS.map((q) => (
              <div
                key={q.id}
                className="p-3 rounded-[12px] border border-border bg-bg-card"
              >
                <div className="flex items-baseline gap-2 mb-1">
                  <span className="font-mono text-[12px] text-text-secondary">
                    {q.id}
                  </span>
                  <span className="text-[13.5px] font-semibold text-text-primary">
                    {q.question}
                  </span>
                </div>
                <p className="text-[12.5px] text-text-secondary leading-relaxed">
                  {q.detail}
                </p>
              </div>
            ))}
          </div>
        </Card>
      </div>

      {/* Section links */}
      <Card>
        <CardHeader kicker="Sections" title="Explore the dashboard" />
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <SectionLink href="/modules/" title="Modules" desc="Proposed module hierarchy + dependencies" />
          <SectionLink href="/decisions/" title="Decisions" desc="D-001 → D-021 decision log" />
          <SectionLink href="/progress/" title="Progress" desc="Phase 0–6 status + blockers" />
          <SectionLink href="/architecture/" title="Architecture" desc="UI ↔ backend separation + module graph" />
        </div>
      </Card>
    </div>
  );
}

/* ---------- Helpers / sub-components ---------- */

function StatCard({
  label,
  value,
  accent,
  href,
  sublabel,
}: {
  label: string;
  value: string | number;
  accent: string;
  href: string;
  sublabel?: string;
}) {
  const isAnchor = href.startsWith("#");
  const inner = (
    <div className="h-full">
      <div className="flex items-center gap-2 mb-3">
        <StatusDot color={accent} size="sm" />
        <span className="text-[11px] font-medium uppercase tracking-wide text-text-secondary">
          {label}
        </span>
      </div>
      <div className="text-[28px] font-bold tracking-extra-tight text-text-primary leading-none">
        {value}
      </div>
      {sublabel && (
        <div className="text-[12px] text-text-secondary mt-2">{sublabel}</div>
      )}
    </div>
  );

  if (isAnchor) {
    return (
      <Card className="h-full hover:translate-y-[-1px]">
        <a href={href} className="no-underline block h-full">
          {inner}
        </a>
      </Card>
    );
  }
  return (
    <Card className="h-full hover:translate-y-[-1px]">
      <Link href={href} className="no-underline block h-full">
        {inner}
      </Link>
    </Card>
  );
}

function SectionLink({
  href,
  title,
  desc,
}: {
  href: string;
  title: string;
  desc: string;
}) {
  return (
    <Link
      href={href}
      className="block p-4 rounded-[12px] border border-border bg-bg-card transition-all duration-200 hover:translate-y-[-1px] hover:bg-bg-card-alt no-underline"
    >
      <div className="flex items-center justify-between gap-2 mb-1">
        <span className="text-[14px] font-semibold text-text-primary">{title}</span>
        <span className="text-text-secondary text-[14px]" aria-hidden="true">→</span>
      </div>
      <div className="text-[12.5px] text-text-secondary leading-relaxed">
        {desc}
      </div>
    </Link>
  );
}

function StatusBadge({ color, label }: { color: string; label: string }) {
  return (
    <span
      className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border"
      style={{
        borderColor: color,
        backgroundColor: `${color}1a`,
        color: color,
      }}
    >
      <StatusDot color={color} size="sm" />
      {label}
    </span>
  );
}

function phaseStatusColor(status: string): string {
  switch (status) {
    case "done":
      return STATUS_META.confirmed.colorVar;
    case "in-progress":
      return STATUS_META.pending.colorVar;
    case "blocked":
      return STATUS_META.blocked.colorVar;
    default:
      return "var(--c-text-secondary)";
  }
}

function phaseStatusLabel(status: string): string {
  switch (status) {
    case "done":
      return "Done";
    case "in-progress":
      return "In progress";
    case "blocked":
      return "Blocked";
    default:
      return "Pending";
  }
}
