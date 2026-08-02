import { DEP_GRAPH_NODES, DEP_GRAPH_EDGES, type GraphNode } from "@/lib/data";

/**
 * DependencyGraph — SVG module dependency graph (DESIGN.md §5.14).
 *
 * Nodes are rounded rects colored by layer (:app = primary,
 * :core:* = secondary, :feature:* = success). Edges are lines
 * connecting the bottom-center of the source to the top-center of the
 * target. Curved Bézier paths for a cleaner look.
 *
 * No external deps — pure SVG.
 */
export function DependencyGraph({
  width = 760,
  height = 460,
  className = "",
}: {
  width?: number;
  height?: number;
  className?: string;
}) {
  const nodeMap = new Map(DEP_GRAPH_NODES.map((n) => [n.id, n]));

  const layerColor = (layer: GraphNode["layer"]) => {
    switch (layer) {
      case "app":
        return "var(--c-primary)";
      case "core":
        return "var(--c-secondary)";
      case "feature":
        return "var(--c-success)";
      default:
        return "var(--c-text-secondary)";
    }
  };

  const edgePath = (from: GraphNode, to: GraphNode) => {
    const fw = from.w ?? 100;
    const fh = from.h ?? 36;
    const tw = to.w ?? 100;
    const th = to.h ?? 36;
    const x1 = from.x + fw / 2;
    const y1 = from.y + fh;
    const x2 = to.x + tw / 2;
    const y2 = to.y;
    const midY = (y1 + y2) / 2;
    return `M${x1},${y1} C${x1},${midY} ${x2},${midY} ${x2},${y2}`;
  };

  return (
    <div className={`overflow-x-auto ${className}`}>
      <svg
        width={width}
        height={height}
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label="Module dependency graph"
        className="min-w-[640px]"
      >
        {/* Edges */}
        {DEP_GRAPH_EDGES.map((e, i) => {
          const from = nodeMap.get(e.from);
          const to = nodeMap.get(e.to);
          if (!from || !to) return null;
          return (
            <path
              key={i}
              d={edgePath(from, to)}
              fill="none"
              stroke="var(--c-border)"
              strokeWidth="1.5"
              strokeDasharray="3 3"
            />
          );
        })}

        {/* Nodes */}
        {DEP_GRAPH_NODES.map((n) => {
          const w = n.w ?? 100;
          const h = n.h ?? 36;
          const color = layerColor(n.layer);
          return (
            <g key={n.id}>
              <rect
                x={n.x}
                y={n.y}
                width={w}
                height={h}
                rx="8"
                fill="var(--c-surface)"
                stroke={color}
                strokeWidth="1.5"
              />
              <rect
                x={n.x}
                y={n.y}
                width={4}
                height={h}
                rx="2"
                fill={color}
              />
              <text
                x={n.x + w / 2 + 2}
                y={n.y + h / 2 + 4}
                textAnchor="middle"
                className="font-mono"
                fontSize="11"
                fontWeight="600"
                fill="var(--c-text-primary)"
              >
                {n.label}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}
