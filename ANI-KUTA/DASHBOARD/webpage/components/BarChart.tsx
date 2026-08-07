/**
 * BarChart — horizontal bars (DESIGN.md §5.14).
 * Uses HTML divs for the bar fill (colored rectangles with width %).
 * Labels on left, values on right — responsive and clean.
 *
 * Not an external chart library — pure CSS/SVG-free.
 */
export interface BarEntry {
  label: string;
  value: number;
  color: string;
  unit?: string;
}

export function BarChart({
  data,
  unit = "",
  className = "",
}: {
  data: BarEntry[];
  unit?: string;
  className?: string;
}) {
  const max = Math.max(...data.map((d) => d.value), 1);

  return (
    <div className={`space-y-2.5 ${className}`}>
      {data.map((d) => {
        const pct = (d.value / max) * 100;
        return (
          <div key={d.label} className="group">
            <div className="flex items-center justify-between gap-3 mb-1">
              <span className="font-mono text-[12px] text-text-primary truncate">
                {d.label}
              </span>
              <span className="text-[12px] text-text-secondary tabular-nums shrink-0">
                {d.value}
                {unit}
              </span>
            </div>
            <div className="h-2 rounded-full bg-canvas overflow-hidden">
              <div
                className="h-full rounded-full transition-all duration-500 ease-out group-hover:brightness-110"
                style={{
                  width: `${pct}%`,
                  backgroundColor: d.color,
                }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}
