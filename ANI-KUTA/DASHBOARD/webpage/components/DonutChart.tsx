/**
 * DonutChart — SVG donut with arc segments (DESIGN.md §5.14).
 * Uses stroke-dasharray on circles to create arc segments.
 *
 * No external deps — pure SVG.
 */
export interface DonutSliceData {
  label: string;
  value: number;
  color: string;
}

export function DonutChart({
  data,
  size = 180,
  thickness = 22,
  centerLabel,
  centerSub,
  className = "",
}: {
  data: DonutSliceData[];
  size?: number;
  thickness?: number;
  centerLabel?: string;
  centerSub?: string;
  className?: string;
}) {
  const total = data.reduce((s, d) => s + d.value, 0) || 1;
  const radius = (size - thickness) / 2;
  const circumference = 2 * Math.PI * radius;
  const center = size / 2;

  let offset = 0;
  const segments = data.map((d) => {
    const fraction = d.value / total;
    const dash = fraction * circumference;
    const seg = {
      color: d.color,
      label: d.label,
      value: d.value,
      pct: Math.round(fraction * 100),
      dash,
      gap: circumference - dash,
      offset: -offset,
    };
    offset += dash;
    return seg;
  });

  return (
    <div className={`flex flex-col sm:flex-row items-center gap-4 ${className}`}>
      <div className="relative shrink-0" style={{ width: size, height: size }}>
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true">
          {/* Background ring */}
          <circle
            cx={center}
            cy={center}
            r={radius}
            fill="none"
            stroke="var(--c-chip)"
            strokeWidth={thickness}
          />
          {segments.map((s, i) => (
            <circle
              key={i}
              cx={center}
              cy={center}
              r={radius}
              fill="none"
              stroke={s.color}
              strokeWidth={thickness}
              strokeDasharray={`${s.dash} ${s.gap}`}
              strokeDashoffset={s.offset}
              strokeLinecap="butt"
              transform={`rotate(-90 ${center} ${center})`}
            />
          ))}
        </svg>
        {centerLabel && (
          <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
            <span className="text-[26px] font-bold tracking-extra-tight text-text-primary leading-none">
              {centerLabel}
            </span>
            {centerSub && (
              <span className="text-[10px] font-medium uppercase tracking-widest text-text-secondary mt-1">
                {centerSub}
              </span>
            )}
          </div>
        )}
      </div>

      {/* Legend */}
      <div className="flex-1 space-y-2 w-full">
        {data.map((d) => {
          const pct = Math.round((d.value / total) * 100);
          return (
            <div key={d.label} className="flex items-center gap-2.5 text-[12.5px]">
              <span
                className="w-2.5 h-2.5 rounded-full shrink-0"
                style={{ backgroundColor: d.color }}
                aria-hidden="true"
              />
              <span className="font-mono text-text-primary flex-1 truncate">{d.label}</span>
              <span className="text-text-secondary tabular-nums">{d.value}</span>
              <span className="text-text-secondary tabular-nums w-9 text-right">{pct}%</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
