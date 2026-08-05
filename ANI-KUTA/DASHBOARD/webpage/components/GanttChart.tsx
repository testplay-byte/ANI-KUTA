import { PHASES } from "@/lib/data";

/**
 * GanttChart — phase timeline bars (DESIGN.md §5.17).
 * Grid: 120px label | 1fr timeline. Bars colored by phase, positioned
 * by start day + duration.
 *
 * Responsive: horizontal scroll on small screens.
 */
export function GanttChart({ className = "" }: { className?: string }) {
  const totalDays = PHASES.reduce((s, p) => Math.max(s, p.startDay + p.days), 0);
  const weeks = Math.ceil(totalDays / 7);

  // Generate week markers (every 2 weeks to avoid clutter)
  const weekMarkers: number[] = [];
  for (let w = 0; w <= weeks; w += 2) {
    weekMarkers.push(w * 7);
  }

  return (
    <div className={`overflow-x-auto ${className}`}>
      <div className="min-w-[640px]">
        {/* Header: week markers */}
        <div className="flex items-center gap-2 mb-2">
          <div className="w-[120px] shrink-0 text-[11px] font-medium uppercase tracking-widest text-text-secondary">
            Phase
          </div>
          <div className="flex-1 relative h-5">
            {weekMarkers.map((day) => (
              <span
                key={day}
                className="absolute top-0 text-[10px] font-mono text-text-secondary -translate-x-1/2"
                style={{ left: `${(day / totalDays) * 100}%` }}
              >
                d{day}
              </span>
            ))}
          </div>
        </div>

        {/* Phase rows */}
        <div className="space-y-1.5">
          {PHASES.map((p) => {
            const left = (p.startDay / totalDays) * 100;
            const width = (p.days / totalDays) * 100;
            return (
              <div key={p.id} className="flex items-center gap-2">
                <div className="w-[120px] shrink-0 flex items-center gap-1.5">
                  <span
                    className="w-1.5 h-1.5 rounded-full shrink-0"
                    style={{ backgroundColor: p.color }}
                    aria-hidden="true"
                  />
                  <span className="text-[11.5px] font-medium text-text-primary truncate">
                    P{p.id}
                  </span>
                </div>
                <div className="flex-1 relative h-7 rounded-[8px] bg-canvas border border-border overflow-hidden">
                  {/* Gridlines */}
                  {weekMarkers.map((day) => (
                    <div
                      key={day}
                      className="absolute top-0 bottom-0 w-px bg-border/50"
                      style={{ left: `${(day / totalDays) * 100}%` }}
                      aria-hidden="true"
                    />
                  ))}
                  {/* Bar */}
                  <div
                    className="absolute top-1 bottom-1 rounded-[6px] flex items-center px-2 group transition-all duration-200 hover:brightness-110"
                    style={{
                      left: `${left}%`,
                      width: `${width}%`,
                      backgroundColor: `${p.color}26`,
                      border: `1px solid ${p.color}`,
                    }}
                    title={`P${p.id} — ${p.name} (day ${p.startDay}→${p.startDay + p.days})`}
                  >
                    <span
                      className="text-[10.5px] font-medium truncate"
                      style={{ color: p.color }}
                    >
                      {p.name}
                    </span>
                    <span className="ml-auto text-[10px] font-mono text-text-secondary shrink-0 pl-2">
                      {p.days}d
                    </span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Footer: today marker */}
        <div className="flex items-center gap-2 mt-3 pt-3 border-t border-border">
          <div className="w-[120px] shrink-0 text-[10px] font-mono text-text-secondary">
            Today: day 14
          </div>
          <div className="flex-1 relative h-4">
            <div
              className="absolute top-0 bottom-0 w-px bg-[var(--c-danger)]"
              style={{ left: `${(14 / totalDays) * 100}%` }}
            >
              <span className="absolute -top-0 -translate-x-1/2 left-0 text-[9px] font-mono text-[var(--c-danger)] whitespace-nowrap">
                ●
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
