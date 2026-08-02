"use client";

import { useState } from "react";
import type { ChecklistItem } from "@/lib/data";

/**
 * Checklist — interactive checklist with progress bar (DESIGN.md §5.15).
 * Items: checkbox + label. Checked: strikethrough + opacity-70.
 * Progress bar: h-1.5 rounded-full, teal fill.
 *
 * State is local (not persisted) — demo only.
 */
export function Checklist({
  title,
  items: initialItems,
  className = "",
}: {
  title: string;
  items: ChecklistItem[];
  className?: string;
}) {
  const [items, setItems] = useState(initialItems);

  const toggle = (i: number) => {
    setItems((prev) =>
      prev.map((item, idx) =>
        idx === i ? { ...item, done: !item.done } : item,
      ),
    );
  };

  const doneCount = items.filter((i) => i.done).length;
  const pct = items.length === 0 ? 0 : Math.round((doneCount / items.length) * 100);

  return (
    <div
      className={`rounded-[16px] border border-border bg-surface p-4 ${className}`}
    >
      {/* Header */}
      <div className="flex items-center justify-between gap-2 mb-3">
        <h4 className="text-[13px] font-semibold text-text-primary">{title}</h4>
        <span className="text-[11px] font-mono text-text-secondary tabular-nums">
          {doneCount}/{items.length}
        </span>
      </div>

      {/* Progress bar */}
      <div className="h-1.5 rounded-full bg-canvas overflow-hidden mb-3">
        <div
          className="h-full rounded-full bg-[var(--c-success)] transition-all duration-500 ease-out"
          style={{ width: `${pct}%` }}
        />
      </div>

      {/* Items */}
      <div className="space-y-1.5">
        {items.map((item, i) => (
          <button
            key={i}
            type="button"
            onClick={() => toggle(i)}
            className="flex items-start gap-2.5 w-full text-left rounded-[8px] p-1.5 -m-1.5 hover:bg-canvas transition-colors duration-150"
          >
            <span
              className={`mt-[1px] w-4 h-4 rounded-[5px] border flex items-center justify-center shrink-0 transition-all duration-150 ${
                item.done
                  ? "bg-[var(--c-success)] border-[var(--c-success)]"
                  : "border-border bg-surface"
              }`}
              aria-hidden="true"
            >
              {item.done && (
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
            <span
              className={`text-[12.5px] leading-relaxed ${
                item.done
                  ? "text-text-secondary line-through opacity-70"
                  : "text-text-primary"
              }`}
            >
              {item.text}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
