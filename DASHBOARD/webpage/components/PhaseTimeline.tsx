import { PHASES } from "@/lib/data";

/**
 * PhaseTimeline — progress bar + phase status cards (DESIGN.md §5.11).
 *
 * - Done: teal border, filled, checkmark.
 * - Active (in-progress/blocked): indigo/rose border, glow.
 * - Todo: neutral border.
 */
export function PhaseTimeline({ className = "" }: { className?: string }) {
  const totalDays = PHASES.reduce((s, p) => s + p.days, 0);
  const doneDays = PHASES.filter((p) => p.status === "done").reduce(
    (s, p) => s + p.days,
    0,
  );

  return (
    <div className={className}>
      {/* Title row */}
      <div className="flex items-center justify-between gap-3 mb-3">
        <div className="flex items-baseline gap-2">
          <span className="text-[11px] font-medium uppercase tracking-widest text-text-secondary">
            Phase timeline
          </span>
          <span className="text-[12px] text-text-secondary font-mono">
            P0→P6 · {totalDays} days
          </span>
        </div>
        <span className="text-[12px] text-text-secondary">
          <span className="font-semibold text-text-primary">{doneDays}</span> / {totalDays} days done
        </span>
      </div>

      {/* Progress bar — segments proportional to phase duration */}
      <div className="flex h-2.5 rounded-full overflow-hidden bg-canvas border border-border mb-4">
        {PHASES.map((p) => (
          <div
            key={p.id}
            className="h-full transition-all duration-500"
            style={{
              width: `${(p.days / totalDays) * 100}%`,
              backgroundColor: p.status === "done" ? p.color : "transparent",
              borderRight: "1px solid var(--c-border)",
            }}
            title={`P${p.id} — ${p.name} (${p.days}d)`}
          />
        ))}
      </div>

      {/* Phase cards row */}
      <div className="grid grid-cols-7 gap-1.5">
        {PHASES.map((p) => {
          const isDone = p.status === "done";
          const isActive = p.status === "in-progress" || p.status === "blocked";
          return (
            <div
              key={p.id}
              className={`flex flex-col items-center gap-1.5 rounded-[12px] border p-2 transition-all duration-200 ${
                isDone
                  ? "border-[var(--c-success)] bg-[var(--c-success)]/10"
                  : isActive
                    ? "border-[var(--c-danger)] shadow-[0_0_0_3px_rgba(255,107,107,0.1)]"
                    : "border-border"
              }`}
              title={`Phase ${p.id} — ${p.name}`}
            >
              <div
                className={`w-6 h-6 rounded-full flex items-center justify-center text-[11px] font-bold ${
                  isDone
                    ? "bg-[var(--c-success)] text-white"
                    : isActive
                      ? "text-[var(--c-danger)] border border-[var(--c-danger)]"
                      : "text-text-secondary border border-border"
                }`}
              >
                {isDone ? "✓" : p.id}
              </div>
              <span className="text-[10px] text-text-secondary text-center leading-tight hidden sm:block truncate w-full">
                {p.name.split(" ")[0]}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
