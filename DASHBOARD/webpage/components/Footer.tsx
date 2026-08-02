import { StatusDot } from "@/components/StatusDot";

/**
 * Footer — sticky footer with status + living-project note.
 * Layout §8.1: flex justify-between.
 */
export function Footer() {
  return (
    <footer className="mt-auto pt-10 pb-2">
      <div className="flex flex-wrap items-center justify-between gap-3 text-[12px] text-text-secondary">
        <div className="flex items-center gap-2">
          <StatusDot color="var(--c-success)" size="sm" />
          <span>Living project — kept in sync with AGENT-CONTEXT.</span>
        </div>
        <div className="font-mono text-[11px] tracking-wide">
          ANI-KUTA · MEMORY OS
        </div>
      </div>
    </footer>
  );
}
