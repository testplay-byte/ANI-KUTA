import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import { DonutChart } from "@/components/DonutChart";
import { BarChart } from "@/components/BarChart";
import { AreaChart } from "@/components/AreaChart";
import {
  MODULE_SIZE_DISTRIBUTION,
  BUILD_TIMES,
  DOCS_COVERAGE,
  BUILD_HEALTH_TABLE,
  MODULES,
  QUICK_STATS,
  type BuildHealthRow,
} from "@/lib/data";

/**
 * Analytics page (v2) — charts and build health.
 *
 * 1. Donut chart — module size distribution (by layer).
 * 2. Horizontal bars — per-module build times.
 * 3. Area chart — docs coverage over 12 weeks.
 * 4. Build health table — per-module CI status.
 */
export default function AnalyticsPage() {
  const totalFiles = MODULE_SIZE_DISTRIBUTION.reduce((s, d) => s + d.value, 0);

  return (
    <div className="space-y-6">
      {/* Summary stat row */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <StatPill label="Total files" value={String(QUICK_STATS.totalFiles)} accent="var(--c-primary)" />
        <StatPill label="Avg build" value="19s" accent="var(--c-warning)" />
        <StatPill label="Docs coverage" value="95%" accent="var(--c-success)" />
        <StatPill label="Tests passing" value="128/130" accent="var(--c-secondary)" />
      </div>

      {/* Donut chart — module size distribution */}
      <Card>
        <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
          Module Size Distribution
        </div>
        <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary mb-4">
          File count by layer
        </h3>
        <DonutChart
          data={MODULE_SIZE_DISTRIBUTION}
          size={180}
          thickness={24}
          centerLabel={String(totalFiles)}
          centerSub="files"
        />
      </Card>

      {/* Two-column: build times + docs coverage */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Build times (horizontal bars) */}
        <Card>
          <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
            Build Times
          </div>
          <h3 className="text-[16px] font-bold tracking-extra-tight text-text-primary mb-4">
            Per-module build duration
          </h3>
          <BarChart
            data={BUILD_TIMES.map((b) => ({
              label: b.module,
              value: b.seconds,
              color: b.color,
              unit: "s",
            }))}
            unit="s"
          />
          <div className="mt-4 pt-3 border-t border-border/60 text-[11px] text-text-secondary">
            Top {BUILD_TIMES.length} slowest modules · CI average{" "}
            <span className="font-mono text-text-primary">
              {Math.round(BUILD_TIMES.reduce((s, b) => s + b.seconds, 0) / BUILD_TIMES.length)}s
            </span>
          </div>
        </Card>

        {/* Docs coverage (area chart) */}
        <Card>
          <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
            Docs Coverage
          </div>
          <h3 className="text-[16px] font-bold tracking-extra-tight text-text-primary mb-4">
            Coverage over 12 weeks
          </h3>
          <AreaChart
            data={DOCS_COVERAGE}
            height={200}
            max={100}
            unit="%"
            color="var(--c-primary)"
            lineColor="var(--c-success)"
          />
          <div className="mt-3 pt-3 border-t border-border/60 flex items-center justify-between text-[11px] text-text-secondary">
            <span>
              Started at{" "}
              <span className="font-mono text-text-primary">{DOCS_COVERAGE[0].value}%</span>
            </span>
            <span>
              Now at{" "}
              <span className="font-mono text-[var(--c-success)] font-medium">
                {DOCS_COVERAGE[DOCS_COVERAGE.length - 1].value}%
              </span>
            </span>
          </div>
        </Card>
      </div>

      {/* Build health table */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Build Health
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              Per-module CI status
            </h3>
          </div>
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-success)" size="sm" />
            {BUILD_HEALTH_TABLE.filter((r) => r.status === "passing").length}/
            {BUILD_HEALTH_TABLE.length} passing
          </span>
        </div>

        {/* Table (responsive: scrollable on mobile) */}
        <div className="overflow-x-auto">
          <table className="w-full text-[12.5px] min-w-[560px]">
            <thead>
              <tr className="border-b border-border text-left">
                <th className="font-medium uppercase tracking-widest text-text-secondary text-[10px] py-2 pr-4">
                  Module
                </th>
                <th className="font-medium uppercase tracking-widest text-text-secondary text-[10px] py-2 pr-4">
                  Status
                </th>
                <th className="font-medium uppercase tracking-widest text-text-secondary text-[10px] py-2 pr-4">
                  Last build
                </th>
                <th className="font-medium uppercase tracking-widest text-text-secondary text-[10px] py-2 pr-4">
                  Duration
                </th>
                <th className="font-medium uppercase tracking-widest text-text-secondary text-[10px] py-2">
                  Tests
                </th>
              </tr>
            </thead>
            <tbody>
              {BUILD_HEALTH_TABLE.map((row) => (
                <BuildHealthRowComp key={row.module} row={row} />
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Module file count summary */}
      <Card>
        <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
          Module File Counts
        </div>
        <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary mb-4">
          All {MODULES.length} modules by file count
        </h3>
        <BarChart
          data={[...MODULES]
            .sort((a, b) => b.files - a.files)
            .map((m) => ({
              label: m.name,
              value: m.files,
              color:
                m.layer === "app"
                  ? "var(--c-primary)"
                  : m.layer === "core"
                    ? "var(--c-secondary)"
                    : "var(--c-success)",
            }))}
        />
        <div className="flex flex-wrap gap-4 mt-4 pt-3 border-t border-border/60 text-[11.5px] text-text-secondary">
          <LegendItem color="var(--c-primary)" label=":app" />
          <LegendItem color="var(--c-secondary)" label=":core:*" />
          <LegendItem color="var(--c-success)" label=":feature:*" />
        </div>
      </Card>
    </div>
  );
}

function BuildHealthRowComp({ row }: { row: BuildHealthRow }) {
  const statusMeta = {
    passing: { color: "var(--c-success)", label: "Passing" },
    warning: { color: "var(--c-warning)", label: "Warning" },
    failed: { color: "var(--c-danger)", label: "Failed" },
  }[row.status];

  return (
    <tr className="border-b border-border/40 hover:bg-canvas/50 transition-colors duration-150">
      <td className="py-2.5 pr-4">
        <span className="font-mono text-[12px] text-text-primary">{row.module}</span>
      </td>
      <td className="py-2.5 pr-4">
        <span
          className="inline-flex items-center gap-1.5 h-5 px-2 rounded-full text-[10px] font-medium"
          style={{ backgroundColor: `${statusMeta.color}1a`, color: statusMeta.color }}
        >
          <StatusDot color={statusMeta.color} size="sm" />
          {statusMeta.label}
        </span>
      </td>
      <td className="py-2.5 pr-4 text-text-secondary font-mono text-[11.5px]">{row.lastBuild}</td>
      <td className="py-2.5 pr-4 text-text-secondary font-mono text-[11.5px]">{row.duration}</td>
      <td className="py-2.5 text-text-secondary font-mono text-[11.5px]">{row.tests}</td>
    </tr>
  );
}

function StatPill({ label, value, accent }: { label: string; value: string; accent: string }) {
  return (
    <div className="rounded-[16px] border border-border bg-surface p-4">
      <div className="flex items-center gap-2 mb-2">
        <span className="w-2 h-2 rounded-full" style={{ backgroundColor: accent }} aria-hidden="true" />
        <span className="text-[10px] font-medium uppercase tracking-widest text-text-secondary">{label}</span>
      </div>
      <div className="text-[24px] font-bold tracking-extra-tight text-text-primary leading-none">{value}</div>
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
