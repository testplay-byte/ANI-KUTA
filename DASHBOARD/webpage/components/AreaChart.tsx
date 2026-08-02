/**
 * AreaChart — SVG area + line chart (DESIGN.md §5.14).
 * Indigo fill (area) + teal dashed stroke (line).
 *
 * No external deps — pure SVG path math.
 */
export interface AreaPoint {
  label: string;
  value: number;
}

export function AreaChart({
  data,
  width = 640,
  height = 200,
  color = "var(--c-primary)",
  lineColor = "var(--c-success)",
  max,
  min = 0,
  unit = "",
  className = "",
}: {
  data: AreaPoint[];
  width?: number;
  height?: number;
  color?: string;
  lineColor?: string;
  max?: number;
  min?: number;
  unit?: string;
  className?: string;
}) {
  if (!data || data.length < 2) {
    return <svg width={width} height={height} className={className} aria-hidden="true" />;
  }

  const padX = 32;
  const padTop = 16;
  const padBottom = 28;
  const innerW = width - padX * 2;
  const innerH = height - padTop - padBottom;

  const yMax = max ?? Math.max(...data.map((d) => d.value));
  const yMin = min;
  const yRange = yMax - yMin || 1;

  const points = data.map((d, i) => {
    const x = padX + (i / (data.length - 1)) * innerW;
    const y = padTop + innerH - ((d.value - yMin) / yRange) * innerH;
    return [x, y] as const;
  });

  const linePath = points
    .map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(2)},${y.toFixed(2)}`)
    .join(" ");

  const areaPath =
    `${linePath} L${points[points.length - 1][0].toFixed(2)},${(padTop + innerH).toFixed(2)} ` +
    `L${points[0][0].toFixed(2)},${(padTop + innerH).toFixed(2)} Z`;

  const gradId = "area-grad";

  // Y-axis gridlines (4 lines)
  const gridLines = [0, 0.25, 0.5, 0.75, 1].map((f) => {
    const y = padTop + innerH - f * innerH;
    const val = Math.round(yMin + f * yRange);
    return { y, val };
  });

  return (
    <div className={className}>
      <svg
        width="100%"
        height={height}
        viewBox={`0 0 ${width} ${height}`}
        preserveAspectRatio="none"
        role="img"
        aria-label={`Area chart with ${data.length} points`}
      >
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity="0.28" />
            <stop offset="100%" stopColor={color} stopOpacity="0" />
          </linearGradient>
        </defs>

        {/* Gridlines */}
        {gridLines.map((g, i) => (
          <g key={i}>
            <line
              x1={padX}
              y1={g.y}
              x2={width - padX}
              y2={g.y}
              stroke="var(--c-border)"
              strokeWidth="1"
              strokeDasharray="2 4"
            />
            <text
              x={padX - 6}
              y={g.y + 3}
              textAnchor="end"
              className="font-mono"
              fontSize="9"
              fill="var(--c-text-secondary)"
            >
              {g.val}{unit}
            </text>
          </g>
        ))}

        {/* Area fill */}
        <path d={areaPath} fill={`url(#${gradId})`} stroke="none" />

        {/* Line (dashed teal) */}
        <path
          d={linePath}
          fill="none"
          stroke={lineColor}
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeDasharray="5 3"
        />

        {/* Data points */}
        {points.map(([x, y], i) => (
          <circle
            key={i}
            cx={x}
            cy={y}
            r="2.5"
            fill="var(--c-surface)"
            stroke={lineColor}
            strokeWidth="1.5"
          />
        ))}

        {/* X-axis labels */}
        {data.map((d, i) => {
          const showLabel = data.length <= 12 || i % 2 === 0;
          if (!showLabel) return null;
          const x = padX + (i / (data.length - 1)) * innerW;
          return (
            <text
              key={i}
              x={x}
              y={height - 8}
              textAnchor="middle"
              className="font-mono"
              fontSize="9"
              fill="var(--c-text-secondary)"
            >
              {d.label}
            </text>
          );
        })}
      </svg>
    </div>
  );
}
