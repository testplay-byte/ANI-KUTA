import { StatusDot } from "@/components/StatusDot";
import {
  type Decision,
  DECISION_STATUS_META,
} from "@/lib/decisions";

/**
 * DecisionCard — architecture decision with pros/cons (DESIGN.md §5.18).
 *
 * Layout:
 *  - Header: ID (mono), title, status badge.
 *  - Question (bold) + context (secondary).
 *  - Options grid: each option has name, recommended badge, pros (teal),
 *    cons (rose).
 *
 * This is the key component for the /decisions page.
 */
export function DecisionCard({ decision }: { decision: Decision }) {
  const meta = DECISION_STATUS_META[decision.status];

  return (
    <article className="rounded-[20px] border border-border bg-surface shadow-[0_8px_40px_rgba(0,0,0,0.04)] overflow-hidden transition-all duration-200 hover:shadow-[0_12px_40px_rgba(0,0,0,0.08)]">
      {/* Header strip */}
      <div className="flex items-center gap-3 px-5 py-4 border-b border-border/60">
        <div
          className="shrink-0 w-9 h-9 rounded-[12px] flex items-center justify-center text-[14px] font-bold"
          style={{
            backgroundColor: `${meta.colorVar}1a`,
            border: `1.5px solid ${meta.colorVar}`,
          }}
          aria-hidden="true"
        >
          <span style={{ color: meta.colorVar }}>{meta.symbol}</span>
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-baseline gap-x-2.5 gap-y-1">
            <span className="font-mono text-[12px] text-text-secondary">
              {decision.id}
            </span>
            <h3 className="text-[15px] font-bold tracking-extra-tight text-text-primary leading-tight">
              {decision.title}
            </h3>
          </div>
        </div>
        <span
          className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium shrink-0"
          style={{
            backgroundColor: `${meta.colorVar}1a`,
            color: meta.colorVar,
          }}
        >
          <StatusDot color={meta.colorVar} size="sm" />
          {meta.label}
        </span>
      </div>

      {/* Question + context */}
      <div className="px-5 py-4 space-y-2">
        <div className="flex items-start gap-2">
          <span className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mt-[3px] shrink-0">
            Q
          </span>
          <p className="text-[14px] font-semibold text-text-primary leading-snug">
            {decision.question}
          </p>
        </div>
        <div className="flex items-start gap-2">
          <span className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mt-[3px] shrink-0">
            Ctx
          </span>
          <p className="text-[12.5px] text-text-secondary leading-relaxed whitespace-pre-line">
            {decision.context}
          </p>
        </div>
      </div>

      {/* Options */}
      <div className="px-5 pb-5">
        <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-2.5">
          {decision.options.length} option{decision.options.length === 1 ? "" : "s"}
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-2.5">
          {decision.options.map((opt, i) => (
            <div
              key={i}
              className={`rounded-[14px] border p-3.5 transition-all duration-200 hover:-translate-y-[1px] ${
                opt.recommended
                  ? "border-[var(--c-primary)] bg-[var(--c-primary)]/5 shadow-[0_0_0_3px_rgba(99,102,241,0.08)]"
                  : "border-border bg-surface-alt/40"
              }`}
            >
              {/* Option name + recommended badge */}
              <div className="flex items-start justify-between gap-2 mb-3">
                <h4 className="text-[12.5px] font-semibold text-text-primary leading-snug">
                  {opt.name}
                </h4>
                {opt.recommended && (
                  <span
                    className="inline-flex items-center gap-1 h-5 px-2 rounded-full text-[9.5px] font-medium shrink-0"
                    style={{
                      backgroundColor: "var(--c-primary)1a",
                      color: "var(--c-primary)",
                    }}
                  >
                    <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="M5 13l4 4L19 7" />
                    </svg>
                    Recommended
                  </span>
                )}
              </div>

              {/* Pros */}
              {opt.pros.length > 0 && (
                <div className="mb-2.5">
                  <div className="text-[10px] font-medium uppercase tracking-widest text-[var(--c-success)] mb-1.5 flex items-center gap-1">
                    <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="M5 13l4 4L19 7" />
                    </svg>
                    Pros
                  </div>
                  <ul className="space-y-1">
                    {opt.pros.map((pro, j) => (
                      <li
                        key={j}
                        className="flex items-start gap-1.5 text-[11.5px] text-text-primary leading-snug"
                      >
                        <span className="text-[var(--c-success)] mt-[2px] shrink-0" aria-hidden="true">+</span>
                        <span>{pro}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Cons */}
              {opt.cons.length > 0 && (
                <div>
                  <div className="text-[10px] font-medium uppercase tracking-widest text-[var(--c-danger)] mb-1.5 flex items-center gap-1">
                    <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="M6 6l12 12M18 6L6 18" />
                    </svg>
                    Cons
                  </div>
                  <ul className="space-y-1">
                    {opt.cons.map((con, j) => (
                      <li
                        key={j}
                        className="flex items-start gap-1.5 text-[11.5px] text-text-primary leading-snug"
                      >
                        <span className="text-[var(--c-danger)] mt-[2px] shrink-0" aria-hidden="true">−</span>
                        <span>{con}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </article>
  );
}
