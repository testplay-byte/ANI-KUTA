import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import { TreeView } from "@/components/TreeView";
import { MODULES, MODULE_TREE, type ModuleInfo } from "@/lib/data";

/**
 * Modules page (v2) — proposed module hierarchy.
 *
 * Shows:
 *  1. File-tree visualization (DESIGN.md §5.9) — interactive.
 *  2. Grid of all module detail cards with file counts.
 *  3. Module dependency rules.
 */
export default function ModulesPage() {
  const coreCount = MODULES.filter((m) => m.layer === "core").length;
  const featureCount = MODULES.filter((m) => m.layer === "feature").length;
  const totalFiles = MODULES.reduce((s, m) => s + m.files, 0);

  return (
    <div className="space-y-6">
      {/* Header card */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-3">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Module Map
            </div>
            <h2 className="text-[22px] font-bold tracking-extra-tight text-text-primary">
              Proposed Module Hierarchy
            </h2>
          </div>
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-warning)" size="sm" />
            Draft — finalized in Phase 1
          </span>
        </div>
        <p className="text-[13px] text-text-secondary leading-relaxed max-w-2xl">
          The app is split into independent modules, each with one
          responsibility + README.{" "}
          <span className="text-text-primary font-medium">
            Feature modules never depend on each other
          </span>{" "}
          — they communicate via <code className="font-mono">:core</code>{" "}
          contracts or navigation. Core modules may depend on other core
          modules, but no cycles.
        </p>

        {/* Quick stats */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4 pt-4 border-t border-border/60">
          <MiniStat label="Total modules" value={String(MODULES.length)} accent="var(--c-primary)" />
          <MiniStat label="Core modules" value={String(coreCount)} accent="var(--c-secondary)" />
          <MiniStat label="Feature modules" value={String(featureCount)} accent="var(--c-success)" />
          <MiniStat label="Total files" value={String(totalFiles)} accent="var(--c-warning)" />
        </div>
      </Card>

      {/* Tree view */}
      <Card>
        <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-4">
          File Tree
        </div>
        <div className="rounded-[12px] border border-border bg-surface-alt/40 p-4 overflow-x-auto">
          <TreeView nodes={MODULE_TREE} modules={MODULES} />
        </div>
        <div className="flex flex-wrap gap-4 mt-4 text-[11.5px] text-text-secondary">
          <LegendItem color="var(--c-primary)" label=":app" />
          <LegendItem color="var(--c-secondary)" label=":core:*" />
          <LegendItem color="var(--c-success)" label=":feature:*" />
          <span className="text-text-secondary ml-auto">
            Click a leaf module to expand its detail.
          </span>
        </div>
      </Card>

      {/* Module detail cards grid */}
      <Card>
        <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-4">
          Module Details — {MODULES.length} modules
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {MODULES.map((m) => (
            <ModuleDetailCard key={m.name} module={m} />
          ))}
        </div>
      </Card>

      {/* Rules */}
      <Card>
        <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-4">
          Rules — Module dependency rules
        </div>
        <div className="space-y-3">
          <RuleRow color="var(--c-success)" text="Feature modules never depend on each other. They communicate via :core contracts or navigation." />
          <RuleRow color="var(--c-success)" text="Core modules may depend on other core modules, but no cycles." />
          <RuleRow color="var(--c-success)" text="Every module has a README.md describing its job, inputs, outputs, and dependencies." />
          <RuleRow color="var(--c-primary)" text="UI layer and data layer are independent per screen — they communicate via contracts only." />
        </div>
      </Card>
    </div>
  );
}

function ModuleDetailCard({ module }: { module: ModuleInfo }) {
  const accent =
    module.layer === "app"
      ? "var(--c-primary)"
      : module.layer === "core"
        ? "var(--c-secondary)"
        : "var(--c-success)";

  return (
    <div className="p-4 rounded-[14px] border border-border bg-surface-alt/40 transition-all duration-200 hover:-translate-y-[1px] hover:bg-surface">
      <div className="flex items-center gap-2 mb-2">
        <StatusDot color={accent} size="md" />
        <span className="font-mono text-[13px] font-semibold text-text-primary flex-1 truncate">
          {module.name}
        </span>
        <span className="font-mono text-[10.5px] text-text-secondary bg-chip border border-border h-5 px-2 rounded-full inline-flex items-center shrink-0">
          {module.files}f
        </span>
      </div>
      <p className="text-[12.5px] text-text-secondary leading-relaxed mb-3">
        {module.job}
      </p>
      <div className="pt-3 border-t border-border/60">
        <div className="text-[10px] font-medium uppercase tracking-widest text-text-secondary mb-1.5">
          Depends on
        </div>
        {module.dependsOn.length === 0 ? (
          <span className="text-[12px] text-text-secondary italic">
            none (leaf module)
          </span>
        ) : (
          <div className="flex flex-wrap gap-1.5">
            {module.dependsOn.map((dep) => (
              <span
                key={dep}
                className="inline-flex items-center h-5 px-2 rounded-full text-[10.5px] font-mono bg-chip border border-border text-text-primary"
              >
                {dep}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function MiniStat({ label, value, accent }: { label: string; value: string; accent: string }) {
  return (
    <div>
      <div className="flex items-center gap-1.5 mb-1">
        <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: accent }} aria-hidden="true" />
        <span className="text-[10px] font-medium uppercase tracking-widest text-text-secondary">{label}</span>
      </div>
      <div className="text-[20px] font-bold tracking-extra-tight text-text-primary leading-none">{value}</div>
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

function RuleRow({ color, text }: { color: string; text: string }) {
  return (
    <div className="flex items-start gap-3">
      <StatusDot color={color} size="sm" className="mt-[7px]" />
      <span className="text-[13px] text-text-primary leading-relaxed">{text}</span>
    </div>
  );
}
