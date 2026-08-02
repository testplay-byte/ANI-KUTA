import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import { DependencyGraph } from "@/components/DependencyGraph";
import { ADRS, MODULES } from "@/lib/data";

/**
 * Architecture page (v2).
 *
 * 1. UI ↔ Backend layer diagram (frontend → contracts → backend).
 * 2. Module dependency graph (SVG).
 * 3. ADR list (Architecture Decision Records).
 */
export default function ArchitecturePage() {
  return (
    <div className="space-y-6">
      {/* Intro */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-3">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Architecture
            </div>
            <h2 className="text-[22px] font-bold tracking-extra-tight text-text-primary">
              UI ↔ Backend Separation
            </h2>
          </div>
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-primary)" size="sm" />
            Core principle
          </span>
        </div>
        <p className="text-[13px] text-text-secondary leading-relaxed max-w-2xl">
          The app is split into two independent layers per screen. The UI can be
          customized without touching data logic, and data logic can be reworked
          without breaking the UI. The{" "}
          <span className="text-text-primary font-medium">contract</span>{" "}
          (interface) is what matters — the UI never knows{" "}
          <em>how</em> data arrives, only <em>what</em> it provides.
        </p>
      </Card>

      {/* Layer diagram */}
      <Card>
        <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-4">
          Layer Diagram
        </div>
        <div className="space-y-0">
          <LayerBox
            title="FRONTEND"
            subtitle="UI Layer"
            accent="var(--c-primary)"
            items={[
              "Renders data via Jetpack Compose",
              "Handles user input + navigation",
              "Customizable: themes, layouts, behavior toggles",
              "Talks to backend ONLY via contracts",
            ]}
          />

          {/* Arrow + contract label */}
          <div className="flex items-center justify-center py-3">
            <div className="flex flex-col items-center gap-2">
              <div className="w-px h-6 bg-border" aria-hidden="true" />
              <div className="inline-flex items-center gap-2 px-4 h-8 rounded-full border border-border bg-chip">
                <span className="text-[10px] font-medium uppercase tracking-widest text-text-secondary">
                  contracts
                </span>
                <span className="font-mono text-[11px] text-text-primary">
                  interfaces / repositories
                </span>
              </div>
              <div className="w-px h-6 bg-border" aria-hidden="true" />
              <svg width="14" height="10" viewBox="0 0 14 10" className="text-text-secondary" aria-hidden="true">
                <path d="M7 10 L0 0 L14 0 Z" fill="currentColor" />
              </svg>
            </div>
          </div>

          <LayerBox
            title="BACKEND"
            subtitle="Data Layer"
            accent="var(--c-success)"
            items={[
              "Fetches data (storage / network / extensions)",
              "Processes + transforms data",
              "Persists state (Room / SQLDelight)",
              "Exposes clean repository interfaces",
            ]}
          />
        </div>
      </Card>

      {/* Module dependency graph (SVG) */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Module Graph
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              Module dependency graph
            </h3>
          </div>
          <span className="text-[11px] text-text-secondary">
            {MODULES.length} modules · proposed
          </span>
        </div>

        <div className="rounded-[14px] border border-border bg-surface-alt/40 p-4">
          <DependencyGraph />
        </div>

        <div className="flex flex-wrap gap-4 mt-4 text-[11.5px] text-text-secondary">
          <LegendItem color="var(--c-primary)" label=":app" />
          <LegendItem color="var(--c-secondary)" label=":core:*" />
          <LegendItem color="var(--c-success)" label=":feature:*" />
          <span className="text-text-secondary ml-auto">
            Edges show compile-time dependencies (top → bottom).
          </span>
        </div>
      </Card>

      {/* Customization hooks */}
      <Card>
        <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
          Customization Hooks
        </div>
        <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary mb-4">
          Four independent customization surfaces
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          <HookCard n="1" module=":core:design" title="Theme tokens" desc="Colors, typography, shapes, motion. Swap-able via presets." />
          <HookCard n="2" module=":core:ui" title="Component variants" desc="Configurable components — variants + props per use case." />
          <HookCard n="3" module=":core:config" title="Layout options" desc="User-tunable density, grid vs list, spacing presets." />
          <HookCard n="4" module=":core:config" title="Behavior toggles" desc="Feature flags — toggle features on/off at runtime." />
        </div>
      </Card>

      {/* ADR list */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Decision Records
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              Architecture Decision Records (ADRs)
            </h3>
          </div>
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            {ADRS.length} records
          </span>
        </div>

        <div className="space-y-1.5">
          {ADRS.map((adr) => {
            const statusColor =
              adr.status === "accepted"
                ? "var(--c-success)"
                : adr.status === "proposed"
                  ? "var(--c-warning)"
                  : "var(--c-text-secondary)";
            return (
              <div
                key={adr.id}
                className="flex items-start gap-3 p-3 rounded-[12px] border border-border bg-surface-alt/40 hover:bg-canvas transition-colors duration-150"
              >
                <span className="font-mono text-[11.5px] text-text-secondary shrink-0 w-16 mt-[1px]">
                  {adr.id}
                </span>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap mb-0.5">
                    <span className="text-[13px] font-semibold text-text-primary">
                      {adr.title}
                    </span>
                    <span
                      className="inline-flex items-center gap-1 h-5 px-2 rounded-full text-[9.5px] font-medium uppercase tracking-wide"
                      style={{
                        backgroundColor: `${statusColor}1a`,
                        color: statusColor,
                      }}
                    >
                      {adr.status}
                    </span>
                  </div>
                  <p className="text-[12px] text-text-secondary leading-relaxed">
                    {adr.summary}
                  </p>
                </div>
              </div>
            );
          })}
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
          <div className="text-[10px] font-medium uppercase tracking-widest text-text-secondary">
            {subtitle}
          </div>
          <div className="text-[18px] font-bold tracking-extra-tight" style={{ color: accent }}>
            {title}
          </div>
        </div>
      </div>
      <ul className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {items.map((item) => (
          <li key={item} className="flex items-start gap-2 text-[12.5px] text-text-primary leading-relaxed">
            <span className="font-mono text-[11px] shrink-0 mt-[2px]" style={{ color: accent }} aria-hidden="true">
              •
            </span>
            <span>{item}</span>
          </li>
        ))}
      </ul>
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
    <div className="p-4 rounded-[14px] border border-border bg-surface-alt/40 transition-all duration-200 hover:-translate-y-[1px]">
      <div className="flex items-center justify-between gap-2 mb-2">
        <span className="text-[10px] font-medium uppercase tracking-widest text-text-secondary">
          Hook {n}
        </span>
        <span className="font-mono text-[10.5px] text-text-secondary bg-chip border border-border h-5 px-2 rounded-full inline-flex items-center">
          {module}
        </span>
      </div>
      <h4 className="text-[13px] font-semibold text-text-primary mb-1.5">{title}</h4>
      <p className="text-[12px] text-text-secondary leading-relaxed">{desc}</p>
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
