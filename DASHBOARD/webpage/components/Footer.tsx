/**
 * Footer — bottom of main content (DESIGN.md §5.2).
 * Sticky footer: pushed to bottom by flex-col layout, never overlays content.
 */
export function Footer() {
  return (
    <footer className="mt-auto px-4 sm:px-6 lg:px-10 pb-6 pt-6">
      <div className="max-w-[1280px] mx-auto">
        <div className="border-t border-border pt-5">
          <div className="flex flex-wrap items-center justify-between gap-3 text-[11px] text-text-secondary">
            <span>
              ANI-KUTA · Project Dashboard · grey dark mode{" "}
              <span className="font-mono">#1E1E1E</span> ·{" "}
              <span className="text-text-primary font-medium">27 modules built</span> ·{" "}
              <span className="text-[var(--c-success)] font-medium">15/15 decisions confirmed</span> ·{" "}
              <span className="text-text-primary font-medium">Phase 3 complete</span>
            </span>
            <span className="font-mono tracking-wide">
              MEMORY OS · v4
            </span>
          </div>
        </div>
      </div>
    </footer>
  );
}
