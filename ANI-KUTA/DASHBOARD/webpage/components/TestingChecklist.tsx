"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Card } from "@/components/Card";
import type { TestingSection } from "@/lib/testingData";

/**
 * TestingChecklist — reusable, localStorage-persisted checklist
 * (DESIGN.md §5.15 + §5.3 Cards + §5.6 Chips).
 *
 * Props:
 *  - storageKey: localStorage key the checked-state map is persisted under.
 *  - sections:   ordered list of TestingSection (each with its own steps).
 *
 * State:
 *  - A single `Record<stepId, boolean>` kept in localStorage. Toggling a
 *    checkbox writes immediately (debounced via microtask batching not
 *    needed — single-key writes are cheap).
 *  - The component is SSR-safe: it renders an "empty" state on the server
 *    and hydrates from localStorage in `useEffect`, so there is no flash
 *    of incorrectly-checked items.
 *
 * Layout:
 *  - Top summary row: overall X/Y + a "Reset all" button.
 *  - One Card per section, each with: phase chip, title, description,
 *    per-section X/Y + progress bar, then the step rows.
 *
 * Visual language per DESIGN.md:
 *  - Cards: `bg-bg-card-alt` surface, 16px radius, 1px border, subtle shadow.
 *  - Progress bar: h-1.5 rounded-full, fill colored with the section's
 *    accentColor (DESIGN.md §5.15 uses teal by default — we vary per section).
 *  - Checked step: strikethrough + opacity-70, accent-colored check.
 *  - Hover: row picks up the canvas background.
 */

interface TestingChecklistProps {
  storageKey: string;
  sections: TestingSection[];
}

type CheckedMap = Record<string, boolean>;

/** Read the saved checkmarks from localStorage. SSR-safe. */
function readStored(storageKey: string): CheckedMap {
  if (typeof window === "undefined") return {};
  try {
    const raw = window.localStorage.getItem(storageKey);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed as CheckedMap;
    }
    return {};
  } catch {
    return {};
  }
}

export function TestingChecklist({
  storageKey,
  sections,
}: TestingChecklistProps) {
  // Start empty so SSR + first client paint match; hydrate in effect.
  const [checked, setChecked] = useState<CheckedMap>({});
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    setChecked(readStored(storageKey));
    setHydrated(true);
  }, [storageKey]);

  const toggle = useCallback(
    (stepId: string) => {
      setChecked((prev) => {
        const next = { ...prev, [stepId]: !prev[stepId] };
        try {
          window.localStorage.setItem(storageKey, JSON.stringify(next));
        } catch {
          /* localStorage unavailable — state still updates in-memory. */
        }
        return next;
      });
    },
    [storageKey],
  );

  const resetAll = useCallback(() => {
    setChecked({});
    try {
      window.localStorage.removeItem(storageKey);
    } catch {
      /* localStorage unavailable. */
    }
  }, [storageKey]);

  /* Per-section progress (memoized — recomputes when `checked` changes). */
  const sectionProgress = useMemo(() => {
    return sections.map((s) => {
      const total = s.steps.length;
      const done = s.steps.filter((step) => checked[step.id]).length;
      const pct = total === 0 ? 0 : Math.round((done / total) * 100);
      return { id: s.id, total, done, pct };
    });
  }, [sections, checked]);

  const grandTotal = sectionProgress.reduce((s, p) => s + p.total, 0);
  const grandDone = sectionProgress.reduce((s, p) => s + p.done, 0);
  const grandPct =
    grandTotal === 0 ? 0 : Math.round((grandDone / grandTotal) * 100);

  return (
    <div className="space-y-4">
      {/* ── Overall progress + reset ── */}
      <Card className="!p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="min-w-0">
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Overall Progress
            </div>
            <div className="flex items-baseline gap-2">
              <span className="text-[24px] font-bold tracking-extra-tight text-text-primary tabular-nums leading-none">
                {hydrated ? grandDone : 0}
                <span className="text-text-secondary font-medium">
                  /{grandTotal}
                </span>
              </span>
              <span className="text-[12px] text-text-secondary tabular-nums">
                {hydrated ? grandPct : 0}% complete
              </span>
            </div>
          </div>
          <button
            type="button"
            onClick={resetAll}
            disabled={!hydrated || grandDone === 0}
            className="inline-flex items-center gap-1.5 h-9 px-3.5 rounded-[12px] text-[12.5px] font-medium border border-border bg-surface text-text-secondary hover:text-text-primary hover:bg-canvas hover:translate-y-[-1px] transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0"
            aria-label="Reset all checkmarks"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="w-4 h-4"
              aria-hidden="true"
            >
              <path d="M3 6h18M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6" />
            </svg>
            Reset all
          </button>
        </div>
        <div className="mt-3 h-2 rounded-full bg-canvas overflow-hidden">
          <div
            className="h-full rounded-full bg-[var(--c-success)] transition-all duration-500 ease-out"
            style={{ width: `${hydrated ? grandPct : 0}%` }}
          />
        </div>
      </Card>

      {/* ── Section cards ── */}
      {sections.map((section, idx) => {
        const progress = sectionProgress.find((p) => p.id === section.id)!;
        const allDone = progress.done === progress.total && progress.total > 0;

        return (
          <Card key={section.id} id={`section-${section.id}`} className="!p-5">
            {/* Section header */}
            <div className="flex items-start justify-between gap-3 flex-wrap mb-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2 mb-1.5">
                  <span
                    className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[10.5px] font-semibold uppercase tracking-widest border"
                    style={{
                      color: section.accentColor,
                      backgroundColor: `color-mix(in srgb, ${section.accentColor} 12%, transparent)`,
                      borderColor: `color-mix(in srgb, ${section.accentColor} 35%, transparent)`,
                    }}
                  >
                    <span
                      className="inline-block w-1.5 h-1.5 rounded-full"
                      style={{ backgroundColor: section.accentColor }}
                    />
                    {section.phase}
                  </span>
                  <span className="text-[10.5px] font-mono text-text-secondary">
                    §{idx + 1}
                  </span>
                </div>
                <h3 className="text-[17px] font-bold tracking-extra-tight text-text-primary leading-tight">
                  {section.title}
                </h3>
                <p className="mt-1 text-[12.5px] text-text-secondary leading-relaxed max-w-2xl">
                  {section.description}
                </p>
              </div>
              <div className="shrink-0 text-right">
                <div
                  className="text-[18px] font-bold tabular-nums leading-none"
                  style={{ color: allDone ? "var(--c-success)" : "var(--c-text-primary)" }}
                >
                  {hydrated ? progress.done : 0}
                  <span className="text-text-secondary font-medium">
                    /{progress.total}
                  </span>
                </div>
                <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mt-1">
                  {allDone ? "Complete" : "In progress"}
                </div>
              </div>
            </div>

            {/* Section progress bar */}
            <div className="h-1.5 rounded-full bg-canvas overflow-hidden mb-4">
              <div
                className="h-full rounded-full transition-all duration-500 ease-out"
                style={{
                  width: `${hydrated ? progress.pct : 0}%`,
                  backgroundColor: section.accentColor,
                }}
              />
            </div>

            {/* Steps */}
            <ol className="space-y-1.5">
              {section.steps.map((step, stepIdx) => {
                const isChecked = !!checked[step.id];
                return (
                  <li key={step.id}>
                    <button
                      type="button"
                      onClick={() => toggle(step.id)}
                      aria-pressed={isChecked}
                      className="flex items-start gap-3 w-full text-left rounded-[10px] p-2 -m-2 hover:bg-canvas transition-colors duration-150"
                    >
                      {/* Checkbox */}
                      <span
                        className={`mt-[1px] w-[18px] h-[18px] rounded-[6px] border flex items-center justify-center shrink-0 transition-all duration-150 ${
                          isChecked
                            ? "border-transparent"
                            : "border-border bg-surface"
                        }`}
                        style={
                          isChecked
                            ? {
                                backgroundColor: section.accentColor,
                                boxShadow: `0 0 0 3px color-mix(in srgb, ${section.accentColor} 20%, transparent)`,
                              }
                            : undefined
                        }
                        aria-hidden="true"
                      >
                        {isChecked && (
                          <svg
                            width="12"
                            height="12"
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
                      {/* Step number + text */}
                      <span className="min-w-0 flex-1">
                        <span
                          className={`text-[12.5px] leading-relaxed block ${
                            isChecked
                              ? "text-text-secondary line-through opacity-70"
                              : "text-text-primary"
                          }`}
                        >
                          <span className="text-text-secondary font-mono mr-1.5 tabular-nums">
                            {String(stepIdx + 1).padStart(2, "0")}
                          </span>
                          {step.text}
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ol>
          </Card>
        );
      })}
    </div>
  );
}
