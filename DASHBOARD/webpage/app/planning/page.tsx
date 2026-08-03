import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import { GanttChart } from "@/components/GanttChart";
import { TaskBoard } from "@/components/TaskBoard";
import { Checklist } from "@/components/Checklist";
import { PHASES, PHASE_CHECKLISTS, TASKS, QUICK_STATS } from "@/lib/data";

/**
 * Planning page (v2) — Gantt chart, Kanban task board, phase checklists.
 *
 * 1. Gantt chart — phase timeline bars.
 * 2. Task board — 3-column Kanban (To Do / In Progress / Done).
 * 3. Phase checklists — per-phase task lists with progress bars.
 */
export default function PlanningPage() {
  const todoCount = TASKS.filter((t) => t.status === "todo").length;
  const inProgressCount = TASKS.filter((t) => t.status === "in-progress").length;
  const doneCount = TASKS.filter((t) => t.status === "done").length;

  return (
    <div className="space-y-6">
      {/* Header card */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-1">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Planning
            </div>
            <h2 className="text-[22px] font-bold tracking-extra-tight text-text-primary">
              Gantt, Kanban, per-phase checklists
            </h2>
            <p className="text-[12.5px] text-text-secondary leading-relaxed mt-1.5 max-w-2xl">
              Static snapshot of project planning — phase timeline (Gantt),
              task board (Kanban: To Do / In Progress / Done), and per-phase
              checklists with progress bars.
            </p>
          </div>
        </div>
      </Card>

      {/* Summary stat row */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <StatPill label="Timeline" value={`${QUICK_STATS.totalDays}d`} accent="var(--c-primary)" />
        <StatPill label="To Do" value={String(todoCount)} accent="var(--c-warning)" />
        <StatPill label="In Progress" value={String(inProgressCount)} accent="var(--c-primary)" />
        <StatPill label="Done" value={String(doneCount)} accent="var(--c-success)" />
      </div>

      {/* Gantt chart */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Gantt Chart
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              Phase timeline
            </h3>
          </div>
          <span className="text-[11px] text-text-secondary">
            P0 → P6 · {QUICK_STATS.totalDays} days total
          </span>
        </div>
        <GanttChart />
        <div className="flex flex-wrap gap-4 mt-4 pt-3 border-t border-border/60 text-[11.5px] text-text-secondary">
          {PHASES.map((p) => (
            <span key={p.id} className="inline-flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full" style={{ backgroundColor: p.color }} aria-hidden="true" />
              <span className="font-mono">P{p.id} · {p.name}</span>
            </span>
          ))}
        </div>
      </Card>

      {/* Task board (Kanban) */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Task Board
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              Kanban — To Do · In Progress · Done
            </h3>
          </div>
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-text-secondary)" size="sm" />
            {TASKS.length} tasks
          </span>
        </div>
        <TaskBoard />
      </Card>

      {/* Phase checklists */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Phase Checklists
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              Per-phase task lists
            </h3>
          </div>
          <span className="text-[11px] text-text-secondary">
            Tap items to toggle (local state only)
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {PHASE_CHECKLISTS.map((pc) => {
            const phase = PHASES.find((p) => p.id === pc.phaseId);
            return (
              <Checklist
                key={pc.phaseId}
                title={`Phase ${pc.phaseId} — ${pc.phaseName}`}
                items={pc.items}
                className={phase?.status === "done" ? "border-[var(--c-success)]/40" : ""}
              />
            );
          })}
        </div>
      </Card>
    </div>
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
