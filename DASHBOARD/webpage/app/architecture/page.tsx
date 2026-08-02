import { Card, CardHeader } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";

/**
 * Architecture page — visual diagrams using styled divs/arrows (no images).
 *
 * 1. UI ↔ Backend separation diagram (frontend → contracts → backend).
 * 2. Module dependency graph (boxes + connecting lines via CSS).
 * 3. Customization hooks (theme tokens, components, layouts, behavior).
 */
export default function ArchitecturePage() {
  return (
    <div className="space-y-6">
      {/* Header */}
      <Card>
        <CardHeader
          kicker="Architecture"
          title="UI ↔ Backend Separation"
          right={
            <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-bg-chip border-border text-text-secondary">
              <StatusDot color="var(--c-primary)" size="sm" />
              Core principle
            </span>
          }
        />
        <p className="text-[13px] text-text-secondary leading-relaxed max-w-2xl">
          The app is split into two independent layers per screen/feature. The
          UI can be customized without touching data logic, and data logic can
          be reworked without breaking the UI. The{" "}
          <span className="text-text-primary font-medium">contract</span>{" "}
          (interface) is what matters — the UI never knows{" "}
          <em>how</em> data arrives, only <em>what</em> it provides.
        </p>
      </Card>

      {/* UI ↔ Backend diagram */}
      <Card>
        <CardHeader kicker="Layer Diagram" title="Two-layer architecture" />
        <div className="space-y-0">
          {/* Frontend layer */}
          <LayerBox
            title="FRONTEND"
            subtitle="UI Layer"
            accent="var(--c-primary)"
            items={[
              "Renders data",
              "Handles user input",
              "Customizable: themes, layouts, behavior toggles",
              "Talks to backend ONLY via contracts",
            ]}
          />

          {/* Arrow + label */}
          <div className="flex items-center justify-center py-4">
            <div className="flex flex-col items-center gap-2">
              <div className="w-px h-8 bg-border" aria-hidden="true" />
              <div className="inline-flex items-center gap-2 px-4 h-8 rounded-full border border-border bg-bg-chip">
                <span className="text-[11px] font-medium uppercase tracking-wide text-text-secondary">
                  contracts
                </span>
                <span className="font-mono text-[11.5px] text-text-primary">
                  interfaces / repositories
                </span>
              </div>
              <div className="w-px h-8 bg-border" aria-hidden="true" />
              <svg
                width="14"
                height="10"
                viewBox="0 0 14 10"
                className="text-text-secondary"
                aria-hidden="true"
              >
                <path d="M7 10 L0 0 L14 0 Z" fill="currentColor" />
              </svg>
            </div>
          </div>

          {/* Backend layer */}
          <LayerBox
            title="BACKEND"
            subtitle="Data Layer"
            accent="var(--c-success)"
            items={[
              "Fetches data (storage / network)",
              "Processes / transforms data",
              "Persists state",
              "Exposes clean repository interfaces",
            ]}
          />
        </div>
      </Card>

      {/* Two data patterns */}
      <Card>
        <CardHeader
          kicker="Data Flow"
          title="Two patterns for getting data into a screen"
        />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <PatternCard
            number="1"
            title="UI calls for data"
            desc="The screen calls a repository/ViewModel to fetch what it needs."
          />
          <PatternCard
            number="2"
            title="UI is provided data"
            desc="A parent/ViewModel pre-loads data and passes it down as state."
          />
        </div>
        <p className="text-[12.5px] text-text-secondary mt-4 leading-relaxed">
          Both are valid. The contract (interface) is what matters — the UI
          never knows <em>how</em> data arrives, only <em>what</em> it
          provides.
        </p>
      </Card>

      {/* Module dependency graph */}
      <Card>
        <CardHeader
          kicker="Module Graph"
          title="Module dependency graph"
          right={
            <span className="text-[11px] text-text-secondary">
              proposed — finalized in Phase 1
            </span>
          }
        />
        <div className="rounded-[12px] border border-border bg-bg-card p-4 md:p-6 overflow-x-auto">
          <div className="min-w-[640px] flex flex-col gap-5">
            {/* Row 1: :app → :feature:* → :core:data → :core:network */}
            <GraphRow
              nodes={[
                { label: ":app", color: "var(--c-primary)" },
                { label: ":feature:*", color: "var(--c-success)" },
                { label: ":core:data", color: "var(--c-secondary)" },
                { label: ":core:network", color: "var(--c-secondary)" },
              ]}
            />
            {/* Branch: :core:data → :core:storage */}
            <div className="flex items-center gap-3 pl-[calc(50%-32px)]">
              <span className="font-mono text-[12px] text-text-secondary">↓</span>
              <GraphNode
                label=":core:storage"
                color="var(--c-secondary)"
              />
            </div>
            {/* Row 2: :feature:* → :core:ui → :core:design */}
            <GraphRow
              nodes={[
                { label: ":feature:*", color: "var(--c-success)" },
                { label: ":core:ui", color: "var(--c-secondary)" },
                { label: ":core:design", color: "var(--c-secondary)" },
              ]}
            />
            {/* Row 3: :feature:* → :core:config */}
            <GraphRow
              nodes={[
                { label: ":feature:*", color: "var(--c-success)" },
                { label: ":core:config", color: "var(--c-secondary)" },
              ]}
            />
            {/* Row 4: all → :core:common */}
            <GraphRow
              nodes={[
                { label: "all", color: "var(--c-text-secondary)" },
                { label: ":core:common", color: "var(--c-secondary)" },
              ]}
            />
          </div>
        </div>
        <div className="flex flex-wrap gap-4 mt-4 text-[11.5px] text-text-secondary">
          <LegendItem color="var(--c-primary)" label=":app" />
          <LegendItem color="var(--c-secondary)" label=":core:*" />
          <LegendItem color="var(--c-success)" label=":feature:*" />
        </div>
      </Card>

      {/* Customization hooks */}
      <Card>
        <CardHeader
          kicker="Customization Hooks"
          title="Four independent customization surfaces"
        />
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <HookCard
            n="1"
            module=":core:design"
            title="Theme tokens"
            desc="Colors, typography, shapes, motion. Swap-able via presets."
          />
          <HookCard
            n="2"
            module=":core:ui"
            title="Component variants"
            desc="Configurable components — variants + props per use case."
          />
          <HookCard
            n="3"
            module=":core:config"
            title="Layout options"
            desc="User-tunable density, grid vs list, spacing presets."
          />
          <HookCard
            n="4"
            module=":core:config"
            title="Behavior toggles"
            desc="Feature flags — toggle features on/off at runtime."
          />
        </div>
      </Card>
    </div>
  );
}

/* ---------- Sub-components ---------- */

function LayerBox({
  title,
  subtitle,
  accent,
  items,
}: {
  title: string;
  subtitle: string;
  accent: string;
  items: string[];
}) {
  return (
    <div
      className="rounded-[16px] border-2 p-5 transition-all duration-200"
      style={{
        borderColor: accent,
        backgroundColor: `${accent}0d`,
      }}
    >
      <div className="flex items-center gap-3 mb-4">
        <StatusDot color={accent} size="lg" />
        <div>
          <div className="text-[11px] font-medium uppercase tracking-wide text-text-secondary">
            {subtitle}
          </div>
          <div
            className="text-[18px] font-bold tracking-extra-tight"
            style={{ color: accent }}
          >
            {title}
          </div>
        </div>
      </div>
      <ul className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {items.map((item) => (
          <li
            key={item}
            className="flex items-start gap-2 text-[12.5px] text-text-primary leading-relaxed"
          >
            <span
              className="font-mono text-[11px] shrink-0 mt-[2px]"
              style={{ color: accent }}
              aria-hidden="true"
            >
              •
            </span>
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function PatternCard({
  number,
  title,
  desc,
}: {
  number: string;
  title: string;
  desc: string;
}) {
  return (
    <div className="p-4 rounded-[14px] border border-border bg-bg-card transition-all duration-200 hover:translate-y-[-1px]">
      <div className="flex items-center gap-3 mb-2">
        <span
          className="w-7 h-7 rounded-full flex items-center justify-center text-[13px] font-bold"
          style={{
            backgroundColor: "var(--c-primary)1a",
            color: "var(--c-primary)",
            border: "1px solid var(--c-primary)",
          }}
        >
          {number}
        </span>
        <h3 className="text-[14px] font-semibold text-text-primary">{title}</h3>
      </div>
      <p className="text-[12.5px] text-text-secondary leading-relaxed">{desc}</p>
    </div>
  );
}

function GraphRow({
  nodes,
}: {
  nodes: { label: string; color: string }[];
}) {
  return (
    <div className="flex items-center gap-3 flex-wrap">
      {nodes.map((node, i) => (
        <div key={node.label + i} className="flex items-center gap-3">
          <GraphNode label={node.label} color={node.color} />
          {i < nodes.length - 1 && (
            <span className="font-mono text-[14px] text-text-secondary" aria-hidden="true">
              →
            </span>
          )}
        </div>
      ))}
    </div>
  );
}

function GraphNode({ label, color }: { label: string; color: string }) {
  return (
    <div
      className="inline-flex items-center h-9 px-3 rounded-full border font-mono text-[12.5px] font-medium"
      style={{
        borderColor: color,
        backgroundColor: `${color}1a`,
        color: color,
      }}
    >
      {label}
    </div>
  );
}

function HookCard({
  n,
  module,
  title,
  desc,
}: {
  n: string;
  module: string;
  title: string;
  desc: string;
}) {
  return (
    <div className="p-4 rounded-[14px] border border-border bg-bg-card transition-all duration-200 hover:translate-y-[-1px]">
      <div className="flex items-center justify-between gap-2 mb-2">
        <span className="text-[11px] font-medium uppercase tracking-wide text-text-secondary">
          Hook {n}
        </span>
        <span className="font-mono text-[11px] text-text-secondary bg-bg-chip border border-border h-6 px-2 rounded-full inline-flex items-center">
          {module}
        </span>
      </div>
      <h3 className="text-[14px] font-semibold text-text-primary mb-1.5">
        {title}
      </h3>
      <p className="text-[12.5px] text-text-secondary leading-relaxed">{desc}</p>
    </div>
  );
}

function LegendItem({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <StatusDot color={color} size="sm" />
      <span className="font-mono">{label}</span>
    </span>
  );
}
