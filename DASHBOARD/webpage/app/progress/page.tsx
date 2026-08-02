import { Card, CardHeader } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import { PHASES, type Phase } from "@/lib/data";

/**
 * Progress page — phase list (Phase 0–6) with status indicators.
 * Shows what's done (checked), what's next, current blockers.
 */
export default function ProgressPage() {
  return (
    <div className="space-y-6">
      {/* Header card */}
      <Card>
        <CardHeader
          kicker="Progress"
          title="Project Phases"
          right={
            <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-bg-chip border-border text-text-secondary">
              <StatusDot color="var(--c-success)" size="sm" />
              {PHASES.filter((p) => p.status === "done").length}/{PHASES.length}{" "}
              done
            </span>
          }
        />
        <p className="text-[13px] text-text-secondary leading-relaxed max-w-2xl">
          The project advances phase-by-phase. Each phase has its own done
          checklist, upcoming work, and blockers (if any). Live status — kept
          in sync with{" "}
          <code className="font-mono text-text-primary">memory/progress.md</code>.
        </p>
        <div className="flex flex-wrap gap-4 mt-4 text-[11.5px] text-text-secondary">
          <LegendItem color="var(--c-success)" label="Done" />
          <LegendItem color="var(--c-warning)" label="In progress" />
          <LegendItem color="var(--c-danger)" label="Blocked" />
          <LegendItem color="var(--c-text-secondary)" label="Pending" />
        </div>
      </Card>

      {/* Phase timeline */}
      <div className="relative">
        {/* Vertical line for timeline effect */}
        <div
          className="absolute left-[18px] top-2 bottom-2 w-px bg-border hidden sm:block"
          aria-hidden="true"
        />
        <div className="space-y-4">
          {PHASES.map((phase) => (
            <PhaseCard key={phase.id} phase={phase} />
          ))}
        </div>
      </div>
    </div>
  );
}

function PhaseCard({ phase }: { phase: Phase }) {
  const { color, label } = phaseStatusMeta(phase.status);

  return (
    <Card className="!p-5 hover:translate-y-[-1px]">
      <div className="flex items-start gap-4">
        {/* Phase number / status indicator */}
        <div className="relative shrink-0">
          <div
            className="w-9 h-9 rounded-full flex items-center justify-center text-[14px] font-bold z-10 relative"
            style={{
              backgroundColor: `${color}1a`,
              border: `1.5px solid ${color}`,
              color: color,
            }}
          >
            {phase.id}
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          {/* Header row */}
          <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1 mb-1.5">
            <h3 className="text-[15px] font-bold tracking-extra-tight text-text-primary">
              Phase {phase.id} — {phase.name}
            </h3>
            <span
              className="inline-flex items-center gap-1.5 h-5 px-2 rounded-full text-[10.5px] font-medium"
              style={{
                backgroundColor: `${color}1a`,
                color: color,
              }}
            >
              <StatusDot color={color} size="sm" />
              {label}
            </span>
          </div>
          <p className="text-[13px] text-text-secondary leading-relaxed mb-4">
            {phase.summary}
          </p>

          {/* Done list */}
          {phase.done.length > 0 && (
            <Section
              title="Done"
              accent="var(--c-success)"
              items={phase.done.map((d) => ({ text: d, glyph: "✓" }))}
            />
          )}

          {/* Next list */}
          {phase.next.length > 0 && (
            <Section
              title={phase.status === "done" ? "Final state" : "Up next"}
              accent="var(--c-warning)"
              items={phase.next.map((n) => ({ text: n, glyph: "→" }))}
            />
          )}

          {/* Blockers */}
          {phase.blockers.length > 0 && (
            <Section
              title="Blockers"
              accent="var(--c-danger)"
              items={phase.blockers.map((b) => ({ text: b, glyph: "⚠" }))}
            />
          )}
        </div>
      </div>
    </Card>
  );
}

function Section({
  title,
  accent,
  items,
}: {
  title: string;
  accent: string;
  items: { text: string; glyph: string }[];
}) {
  return (
    <div className="mb-3 last:mb-0">
      <div className="text-[10.5px] font-medium uppercase tracking-wide text-text-secondary mb-2">
        {title}
      </div>
      <div className="space-y-1.5">
        {items.map((item, i) => (
          <div
            key={i}
            className="flex items-start gap-2 text-[13px] text-text-primary leading-relaxed"
          >
            <span
              className="font-mono text-[12px] shrink-0 mt-[1px]"
              style={{ color: accent }}
              aria-hidden="true"
            >
              {item.glyph}
            </span>
            <span>{item.text}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function LegendItem({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <StatusDot color={color} size="sm" />
      <span>{label}</span>
    </span>
  );
}

function phaseStatusMeta(status: Phase["status"]): {
  color: string;
  label: string;
} {
  switch (status) {
    case "done":
      return { color: "var(--c-success)", label: "Done" };
    case "in-progress":
      return { color: "var(--c-warning)", label: "In progress" };
    case "blocked":
      return { color: "var(--c-danger)", label: "Blocked" };
    default:
      return { color: "var(--c-text-secondary)", label: "Pending" };
  }
}
