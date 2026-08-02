import { Card, CardHeader } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import { TreeView } from "@/components/TreeView";
import { MODULES, MODULE_TREE, type ModuleInfo } from "@/lib/data";

/**
 * Modules page — proposed module hierarchy (finalized in Phase 1).
 *
 * Shows:
 *  1. The file-tree visualization (DESIGN.md §5.8) — interactive:
 *     click a leaf module to expand its detail inline.
 *  2. A grid of all module detail cards (always visible).
 */
export default function ModulesPage() {
  return (
    <div className="space-y-6">
      {/* Header card */}
      <Card>
        <CardHeader
          kicker="Module Map"
          title="Proposed Module Hierarchy"
          right={
            <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-bg-chip border-border text-text-secondary">
              <StatusDot color="var(--c-warning)" size="sm" />
              Draft — finalized in Phase 1
            </span>
          }
        />
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
      </Card>

      {/* Tree view */}
      <Card>
        <CardHeader
          kicker="File Tree"
          title="Module tree"
        />
        <div className="rounded-[12px] border border-border bg-bg-card p-4 overflow-x-auto">
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
        <CardHeader
          kicker="Module Details"
          title={`${MODULES.length} proposed modules`}
        />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {MODULES.map((m) => (
            <ModuleDetailCard key={m.name} module={m} />
          ))}
        </div>
      </Card>

      {/* Rules */}
      <Card>
        <CardHeader kicker="Rules" title="Module dependency rules" />
        <div className="space-y-3">
          <RuleRow
            color="var(--c-success)"
            text="Feature modules never depend on each other. They communicate via :core contracts or navigation."
          />
          <RuleRow
            color="var(--c-success)"
            text="Core modules may depend on other core modules, but no cycles."
          />
          <RuleRow
            color="var(--c-success)"
            text="Every module has a README.md describing its job, inputs, outputs, and dependencies."
          />
          <RuleRow
            color="var(--c-primary)"
            text="UI layer and data layer are independent per screen — they communicate via contracts only."
          />
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
    <div className="p-4 rounded-[14px] border border-border bg-bg-card transition-all duration-200 hover:translate-y-[-1px]">
      <div className="flex items-center gap-2 mb-2">
        <StatusDot color={accent} size="md" />
        <span className="font-mono text-[13.5px] font-semibold text-text-primary">
          {module.name}
        </span>
      </div>
      <p className="text-[12.5px] text-text-secondary leading-relaxed mb-3">
        {module.job}
      </p>
      <div className="pt-3 border-t border-border">
        <div className="text-[10.5px] font-medium uppercase tracking-wide text-text-secondary mb-1.5">
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
                className="inline-flex items-center h-6 px-2.5 rounded-full text-[11px] font-mono bg-bg-chip border border-border text-text-primary"
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
      <span className="text-[13px] text-text-primary leading-relaxed">
        {text}
      </span>
    </div>
  );
}
