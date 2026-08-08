"use client";

import { useEffect, useState, useCallback } from "react";

/**
 * TestingChecklist — persistent interactive checklist (build 234ea15 testing).
 *
 * Differs from the generic Checklist component:
 *  - State is persisted to localStorage (`ani-kuta:testing:${id}`).
 *  - Each item has an optional `detail` line (smaller, secondary).
 *  - Collapsible section — collapse state also persists.
 *  - Includes a "Reset" button (with confirm) for re-running a checklist.
 *
 * Used by /testing page to track on-device verification of the
 * METADATA-FIX-v2 build.
 */

export interface TestingChecklistItem {
  text: string;
  detail?: string;
}

interface TestingChecklistProps {
  id: string;
  title: string;
  items: TestingChecklistItem[];
  /** Optional kicker shown above the title (e.g. "Section 1 · Fix 1"). */
  kicker?: string;
  className?: string;
}

const STORAGE_PREFIX = "ani-kuta:testing:";
const COLLAPSE_SUFFIX = ":collapsed";

export function TestingChecklist({
  id,
  title,
  items,
  kicker,
  className = "",
}: TestingChecklistProps) {
  const storageKey = `${STORAGE_PREFIX}${id}`;
  const collapseKey = `${STORAGE_PREFIX}${id}${COLLAPSE_SUFFIX}`;

  // SSR-safe initial state (empty = unchecked, expanded). After mount we
  // hydrate from localStorage so the rendered UI matches the user's saved
  // progress without a flash.
  const [checked, setChecked] = useState<boolean[]>(() =>
    items.map(() => false),
  );
  const [collapsed, setCollapsed] = useState(false);
  const [mounted, setMounted] = useState(false);

  // Hydrate from localStorage on mount.
  useEffect(() => {
    try {
      const raw = localStorage.getItem(storageKey);
      if (raw) {
        const parsed: boolean[] = JSON.parse(raw);
        // Align parsed length with items length (in case items changed).
        const next = items.map((_, i) => Boolean(parsed[i]));
        setChecked(next);
      }
      const col = localStorage.getItem(collapseKey);
      setCollapsed(col === "1");
    } catch {
      /* localStorage unavailable / corrupt — ignore */
    }
    setMounted(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  // Persist checked state.
  useEffect(() => {
    if (!mounted) return;
    try {
      localStorage.setItem(storageKey, JSON.stringify(checked));
    } catch {
      /* ignore */
    }
  }, [checked, mounted, storageKey]);

  // Persist collapse state.
  useEffect(() => {
    if (!mounted) return;
    try {
      localStorage.setItem(collapseKey, collapsed ? "1" : "0");
    } catch {
      /* ignore */
    }
  }, [collapsed, mounted, collapseKey]);

  const toggle = useCallback((i: number) => {
    setChecked((prev) => prev.map((v, idx) => (idx === i ? !v : v)));
  }, []);

  const reset = useCallback(() => {
    if (typeof window !== "undefined") {
      const ok = window.confirm(
        `Reset all checkboxes in "${title}"?\n\nThis cannot be undone.`,
      );
      if (!ok) return;
    }
    setChecked(items.map(() => false));
  }, [items, title]);

  const toggleCollapse = useCallback(() => {
    setCollapsed((c) => !c);
  }, []);

  const doneCount = checked.filter(Boolean).length;
  const total = items.length;
  const pct = total === 0 ? 0 : Math.round((doneCount / total) * 100);
  const allDone = doneCount === total && total > 0;

  return (
    <div
      className={`rounded-[16px] border border-border bg-surface shadow-card transition-all duration-200 ${className}`}
    >
      {/* Header (clickable for collapse) */}
      <div className="p-4 sm:p-5">
        <div className="flex items-start justify-between gap-3">
          <button
            type="button"
            onClick={toggleCollapse}
            aria-expanded={!collapsed}
            aria-controls={`testing-checklist-body-${id}`}
            className="flex items-start gap-2.5 min-w-0 text-left flex-1 group"
          >
            <span
              className="mt-[3px] shrink-0 text-text-secondary group-hover:text-text-primary transition-colors duration-200"
              aria-hidden="true"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className={`w-4 h-4 transition-transform duration-200 ${collapsed ? "" : "rotate-90"}`}
              >
                <path d="M9 6l6 6-6 6" />
              </svg>
            </span>
            <span className="min-w-0">
              {kicker && (
                <span className="block text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
                  {kicker}
                </span>
              )}
              <span className="block text-[15px] font-semibold tracking-tight text-text-primary leading-tight">
                {title}
              </span>
            </span>
          </button>

          <div className="flex items-center gap-2 shrink-0">
            <span
              className={`text-[11px] font-mono tabular-nums px-2 py-0.5 rounded-full border transition-colors duration-200 ${
                allDone
                  ? "bg-[color-mix(in_srgb,var(--c-success)_12%,transparent)] border-[color-mix(in_srgb,var(--c-success)_30%,transparent)] text-[var(--c-success)]"
                  : "bg-canvas border-border text-text-secondary"
              }`}
            >
              {doneCount}/{total}
            </span>
            <button
              type="button"
              onClick={reset}
              aria-label={`Reset ${title}`}
              title="Reset all checkboxes"
              className="text-[11px] font-medium text-text-secondary hover:text-[var(--c-danger)] transition-colors duration-200 px-2 py-1 rounded-[8px] hover:bg-canvas"
            >
              Reset
            </button>
          </div>
        </div>

        {/* Progress bar (always visible — even when collapsed) */}
        <div className="mt-3 h-1.5 rounded-full bg-canvas overflow-hidden">
          <div
            className="h-full rounded-full bg-[var(--c-success)] transition-all duration-500 ease-out"
            style={{ width: `${pct}%` }}
          />
        </div>
      </div>

      {/* Items */}
      {!collapsed && (
        <div
          id={`testing-checklist-body-${id}`}
          className="px-4 sm:px-5 pb-4 sm:pb-5 pt-1 space-y-1"
        >
          {items.map((item, i) => {
            const isChecked = mounted ? checked[i] : false;
            return (
              <button
                key={i}
                type="button"
                onClick={() => toggle(i)}
                className="flex items-start gap-2.5 w-full text-left rounded-[10px] p-2 -m-2 hover:bg-canvas transition-colors duration-150"
              >
                <span
                  className={`mt-[2px] w-4 h-4 rounded-[5px] border flex items-center justify-center shrink-0 transition-all duration-150 ${
                    isChecked
                      ? "bg-[var(--c-success)] border-[var(--c-success)]"
                      : "border-border bg-surface"
                  }`}
                  aria-hidden="true"
                >
                  {isChecked && (
                    <svg
                      width="10"
                      height="10"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="white"
                      strokeWidth="3.5"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <path d="M5 13l4 4L19 7" />
                    </svg>
                  )}
                </span>
                <span className="min-w-0 flex-1">
                  <span
                    className={`block text-[12.5px] leading-relaxed transition-colors duration-150 ${
                      isChecked
                        ? "text-text-secondary line-through opacity-70"
                        : "text-text-primary"
                    }`}
                  >
                    {item.text}
                  </span>
                  {item.detail && (
                    <span className="block text-[11.5px] leading-relaxed text-text-secondary mt-0.5">
                      {item.detail}
                    </span>
                  )}
                </span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
