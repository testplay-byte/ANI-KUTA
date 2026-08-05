import { WORKFLOW_STEPS } from "@/lib/data";

/**
 * WorkflowLoop — 6-step cycle (DESIGN.md §5.12).
 * Analyze → Research → Comprehend → Confirm → Build → Verify.
 *
 * Cards with colored top bar, icon, label, description.
 * Connected by arrows (SVG) on desktop, stacked on mobile.
 */
export function WorkflowLoop({ className = "" }: { className?: string }) {
  return (
    <div className={className}>
      <div className="flex items-baseline gap-2 mb-4">
        <span className="text-[11px] font-medium uppercase tracking-widest text-text-secondary">
          Workflow loop
        </span>
        <span className="text-[12px] text-text-secondary">
          6-step cycle · repeated per task
        </span>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-6 gap-2.5">
        {WORKFLOW_STEPS.map((step, i) => (
          <div key={step.step} className="flex items-stretch">
            <div className="flex-1 rounded-[14px] border border-border bg-surface overflow-hidden transition-all duration-200 hover:-translate-y-[1px] hover:shadow-[0_8px_40px_rgba(0,0,0,0.04)]">
              {/* Colored top bar */}
              <div className="h-1" style={{ backgroundColor: step.color }} />
              <div className="p-3">
                <div className="flex items-center gap-2 mb-2">
                  <span
                    className="w-7 h-7 rounded-[8px] flex items-center justify-center shrink-0"
                    style={{
                      backgroundColor: `${step.color}1a`,
                      border: `1px solid ${step.color}`,
                    }}
                  >
                    <svg
                      width="14"
                      height="14"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke={step.color}
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      aria-hidden="true"
                    >
                      <path d={step.icon} />
                    </svg>
                  </span>
                  <span className="text-[10px] font-mono text-text-secondary">
                    {String(step.step).padStart(2, "0")}
                  </span>
                </div>
                <div className="text-[13px] font-semibold text-text-primary mb-1">
                  {step.label}
                </div>
                <div className="text-[11.5px] text-text-secondary leading-relaxed">
                  {step.desc}
                </div>
              </div>
            </div>

            {/* Arrow between steps (desktop) */}
            {i < WORKFLOW_STEPS.length - 1 && (
              <div className="hidden lg:flex items-center px-1 shrink-0" aria-hidden="true">
                <svg
                  width="14"
                  height="14"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="var(--c-text-secondary)"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M5 12h14M13 5l7 7-7 7" />
                </svg>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
