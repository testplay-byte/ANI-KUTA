import { TASKS, type Task, type TaskPriority, type TaskStatus } from "@/lib/data";

/**
 * TaskBoard — 3-column Kanban (DESIGN.md §5.16).
 * Columns: To Do | In Progress | Done.
 * Task cards: title, priority dot, tag pill, assignee initials.
 *
 * Priority dots: Rose (high), Amber (med), Border-gray (low).
 */
export function TaskBoard({ className = "" }: { className?: string }) {
  const columns: { key: TaskStatus; label: string; accent: string }[] = [
    { key: "todo", label: "To Do", accent: "var(--c-text-secondary)" },
    { key: "in-progress", label: "In Progress", accent: "var(--c-warning)" },
    { key: "done", label: "Done", accent: "var(--c-success)" },
  ];

  return (
    <div className={`grid grid-cols-1 md:grid-cols-3 gap-3 ${className}`}>
      {columns.map((col) => {
        const colTasks = TASKS.filter((t) => t.status === col.key);
        return (
          <div
            key={col.key}
            className="rounded-[16px] border border-border bg-surface-alt/50 p-3"
          >
            {/* Column header */}
            <div className="flex items-center justify-between gap-2 mb-3 px-1">
              <div className="flex items-center gap-2">
                <span
                  className="w-2 h-2 rounded-full"
                  style={{ backgroundColor: col.accent }}
                  aria-hidden="true"
                />
                <span className="text-[12px] font-semibold text-text-primary">
                  {col.label}
                </span>
              </div>
              <span className="text-[11px] font-mono text-text-secondary tabular-nums">
                {colTasks.length}
              </span>
            </div>

            {/* Task cards */}
            <div className="space-y-2">
              {colTasks.length === 0 ? (
                <div className="text-[12px] text-text-secondary text-center py-6 italic">
                  No tasks
                </div>
              ) : (
                colTasks.map((task) => <TaskCard key={task.id} task={task} />)
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}

function TaskCard({ task }: { task: Task }) {
  const priorityColor = (p: TaskPriority) => {
    switch (p) {
      case "high":
        return "var(--c-danger)";
      case "med":
        return "var(--c-warning)";
      default:
        return "var(--c-text-secondary)";
    }
  };

  const priorityLabel = (p: TaskPriority) =>
    p === "high" ? "High" : p === "med" ? "Medium" : "Low";

  return (
    <div className="rounded-[12px] border border-border bg-surface p-3 transition-all duration-200 hover:-translate-y-[1px] hover:shadow-[0_4px_16px_rgba(0,0,0,0.06)]">
      <div className="flex items-start gap-2 mb-1.5">
        <span
          className="mt-1 w-2 h-2 rounded-full shrink-0"
          style={{ backgroundColor: priorityColor(task.priority) }}
          title={`Priority: ${priorityLabel(task.priority)}`}
          aria-hidden="true"
        />
        <span className="text-[12.5px] font-medium text-text-primary leading-snug flex-1">
          {task.title}
        </span>
      </div>
      <p className="text-[11.5px] text-text-secondary leading-relaxed mb-2 pl-4">
        {task.desc}
      </p>
      <div className="flex items-center justify-between gap-2 pl-4">
        <span className="inline-flex items-center h-5 px-2 rounded-full text-[9.5px] font-medium bg-chip border border-border text-text-secondary uppercase tracking-wide">
          {task.tag}
        </span>
        <span
          className="w-5 h-5 rounded-full bg-chip border border-border flex items-center justify-center text-[9px] font-bold text-text-primary"
          title={`Assigned to ${task.assignee}`}
        >
          {task.assignee}
        </span>
      </div>
    </div>
  );
}
