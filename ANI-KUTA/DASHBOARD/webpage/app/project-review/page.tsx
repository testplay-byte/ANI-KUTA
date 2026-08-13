"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import {
  CHECKLIST_HERO,
  FOOTER_NOTE,
  STATUS_META,
  TEST_CHECKLIST,
  groupByCategory,
  type ChecklistStatus,
  type TestChecklistItem,
} from "@/lib/projectReview";

/* ---------------------------------------------------------------------------
 * Project Review → Test Checklist page.
 *
 * Replaces the former project-review content (concerns, features, risks) with
 * an interactive TEST CHECKLIST for on-device verification of the DC1–DC5
 * fixes. State persists to localStorage so progress isn't lost on refresh.
 *
 * Design follows DESIGN.md (MEMORY OS v3):
 *  - Warm canvas / sharp data
 *  - Hero Card with kicker + title + description + progress summary
 *  - Overall progress bar at top (teal fill per §5.15)
 *  - Per-category Cards grouping checklist rows
 *  - Each row: checkbox + title + description + status chip
 *  - Status cycle: pending → pass → fail → n/a → pending (click chip)
 *  - Checkbox: toggles between pending ↔ pass quickly
 *  - "Reset all" button in hero
 *  - Light + dark mode via CSS variables (no hardcoded colors)
 * ------------------------------------------------------------------------- */

const STORAGE_KEY = "ani-kuta:test-checklist:v1";

/** localStorage persisted shape — only the user-edited fields. */
type StoredEntry = {
  status: ChecklistStatus;
  notes?: string;
};

type StoredState = Record<string, StoredEntry>;

const STATUS_CYCLE: ChecklistStatus[] = ["pending", "pass", "fail", "n/a"];

/* ---------------------------------------------------------------------------
 * Page
 * ------------------------------------------------------------------------- */

export default function ProjectReviewPage() {
  const grouped = useMemo(() => groupByCategory(TEST_CHECKLIST), []);

  /* ---------------------------------------------------------------------
   * State — kept in a single Record keyed by item.id.
   * Initial render uses the defaults from TEST_CHECKLIST (status:"pending").
   * On mount we hydrate from localStorage. Subsequent updates write back.
   * ------------------------------------------------------------------ */
  const [overrides, setOverrides] = useState<StoredState>({});
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw) as StoredState;
        if (parsed && typeof parsed === "object") {
          setOverrides(parsed);
        }
      }
    } catch {
      /* localStorage unavailable or payload corrupt → ignore */
    } finally {
      setHydrated(true);
    }
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(overrides));
    } catch {
      /* storage full or unavailable → ignore */
    }
  }, [overrides, hydrated]);

  /** Effective state of an item = override ?? default. */
  const getStatus = useCallback(
    (item: TestChecklistItem): ChecklistStatus =>
      overrides[item.id]?.status ?? item.status,
    [overrides],
  );

  const setStatus = useCallback(
    (id: string, status: ChecklistStatus) => {
      setOverrides((prev) => {
        const next: StoredState = { ...prev };
        const current = next[id];
        // If the new status matches the default, drop the override entry
        // (keeps storage small + lets future default changes show through).
        const defaultStatus = TEST_CHECKLIST.find((it) => it.id === id)?.status;
        if (status === defaultStatus) {
          delete next[id];
        } else {
          next[id] = { ...(current ?? {}), status };
        }
        return next;
      });
    },
    [],
  );

  /** Checkbox click: toggle between pending ↔ pass. */
  const toggleCheckbox = useCallback(
    (item: TestChecklistItem) => {
      const current = getStatus(item);
      const next: ChecklistStatus = current === "pass" ? "pending" : "pass";
      setStatus(item.id, next);
    },
    [getStatus, setStatus],
  );

  /** Status chip click: cycle pending → pass → fail → n/a → pending. */
  const cycleStatus = useCallback(
    (item: TestChecklistItem) => {
      const current = getStatus(item);
      const idx = STATUS_CYCLE.indexOf(current);
      const next = STATUS_CYCLE[(idx + 1) % STATUS_CYCLE.length];
      setStatus(item.id, next);
    },
    [getStatus, setStatus],
  );

  /** Reset all overrides — wipes localStorage + returns every item to default. */
  const resetAll = useCallback(() => {
    if (
      typeof window !== "undefined" &&
      !window.confirm(
        "Reset all checklist progress? This will mark every item as Pending.",
      )
    ) {
      return;
    }
    setOverrides({});
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
  }, []);

  /* ---------------------------------------------------------------------
   * Derived metrics for the hero + per-category progress.
   * ------------------------------------------------------------------ */
  const allStatuses = useMemo(
    () => TEST_CHECKLIST.map((it) => getStatus(it)),
    [getStatus],
  );
  const total = TEST_CHECKLIST.length;
  const passCount = allStatuses.filter((s) => s === "pass").length;
  const failCount = allStatuses.filter((s) => s === "fail").length;
  const naCount = allStatuses.filter((s) => s === "n/a").length;
  const pendingCount = allStatuses.filter((s) => s === "pending").length;
  const verifiedCount = passCount + failCount + naCount;
  const overallPct =
    total === 0 ? 0 : Math.round((verifiedCount / total) * 100);

  return (
    <div className="space-y-6">
      {/* ── Hero ─────────────────────────────────────────────────────── */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-3">
          <div className="min-w-0">
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1.5">
              {CHECKLIST_HERO.kicker}
            </div>
            <h1 className="text-[22px] sm:text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
              {CHECKLIST_HERO.title}
            </h1>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <span
              className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary"
              title={`Reference commit: ${CHECKLIST_HERO.commitRef}`}
            >
              <StatusDot color="var(--c-success)" size="sm" />
              {CHECKLIST_HERO.sessionRef} · {CHECKLIST_HERO.commitRef}
            </span>
            <button
              type="button"
              onClick={resetAll}
              className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border border-border bg-surface text-text-secondary hover:text-text-primary hover:bg-canvas hover:-translate-y-[1px] transition-all duration-200"
              title="Reset all checklist progress"
            >
              <svg
                width="12"
                height="12"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
                <path d="M3 3v5h5" />
              </svg>
              Reset all
            </button>
          </div>
        </div>
        <p className="text-[12.5px] sm:text-[13.5px] text-text-secondary leading-[1.5] max-w-2xl">
          {CHECKLIST_HERO.description}
        </p>

        {/* Overall progress bar */}
        <div className="mt-5">
          <div className="flex items-baseline justify-between mb-1.5">
            <div className="flex items-baseline gap-2">
              <span className="text-[13px] font-semibold text-text-primary">
                Overall progress
              </span>
              <span className="text-[11px] text-text-secondary">
                {verifiedCount} of {total} items verified
              </span>
            </div>
            <span className="font-mono text-[12.5px] tabular-nums text-text-primary">
              {overallPct}%
            </span>
          </div>
          <div className="h-2 rounded-full bg-canvas overflow-hidden">
            <div
              className="h-full rounded-full bg-[var(--c-success)] transition-all duration-500 ease-out"
              style={{ width: `${overallPct}%` }}
            />
          </div>
        </div>

        {/* Status metric pills */}
        <div className="mt-4 flex flex-wrap gap-2">
          <MetricPill
            color="var(--c-success)"
            value={passCount}
            label="pass"
          />
          <MetricPill
            color="var(--c-danger)"
            value={failCount}
            label="fail"
          />
          <MetricPill
            color="var(--c-text-secondary)"
            value={naCount}
            label="n/a"
          />
          <MetricPill
            color="var(--c-warning)"
            value={pendingCount}
            label="pending"
          />
        </div>

        {/* Status legend / how-to */}
        <div className="mt-4 pt-3 border-t border-border/60 flex items-start gap-2 flex-wrap text-[11px] text-text-secondary">
          <span className="font-medium text-text-primary">Tip:</span>
          <span>
            Click the checkbox to mark{" "}
            <span className="text-[var(--c-success)] font-medium">pass</span>.
          </span>
          <span className="text-border">·</span>
          <span>
            Click the status chip on the right to cycle{" "}
            <span className="text-[var(--c-warning)]">pending</span> →{" "}
            <span className="text-[var(--c-success)]">pass</span> →{" "}
            <span className="text-[var(--c-danger)]">fail</span> →{" "}
            <span className="text-text-secondary">n/a</span>.
          </span>
        </div>
      </Card>

      {/* ── Category cards ────────────────────────────────────────────── */}
      {grouped.map((group) => {
        const groupStatuses = group.items.map((it) => getStatus(it));
        const groupPass = groupStatuses.filter((s) => s === "pass").length;
        const groupTotal = group.items.length;
        const groupPct =
          groupTotal === 0 ? 0 : Math.round((groupPass / groupTotal) * 100);
        return (
          <Card key={group.category}>
            <CategoryHeader
              title={group.category}
              passCount={groupPass}
              totalCount={groupTotal}
              pct={groupPct}
            />
            <ul className="space-y-1.5 mt-1">
              {group.items.map((item) => (
                <ChecklistRow
                  key={item.id}
                  item={item}
                  status={getStatus(item)}
                  onToggleCheckbox={() => toggleCheckbox(item)}
                  onCycleStatus={() => cycleStatus(item)}
                  hydrated={hydrated}
                />
              ))}
            </ul>
          </Card>
        );
      })}

      {/* ── Footer note ────────────────────────────────────────────────── */}
      <p className="text-[11.5px] text-text-secondary leading-relaxed text-center px-4 py-2">
        {FOOTER_NOTE}
      </p>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * Sub-components
 * ------------------------------------------------------------------------- */

function MetricPill({
  color,
  value,
  label,
}: {
  color: string;
  value: number;
  label: string;
}) {
  return (
    <span
      className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11.5px] border bg-surface border-border"
      title={`${value} ${label}`}
    >
      <StatusDot color={color} size="sm" />
      <span className="font-mono font-semibold tabular-nums text-text-primary">
        {value}
      </span>
      <span className="text-text-secondary">{label}</span>
    </span>
  );
}

function CategoryHeader({
  title,
  passCount,
  totalCount,
  pct,
}: {
  title: string;
  passCount: number;
  totalCount: number;
  pct: number;
}) {
  return (
    <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
      <div className="min-w-0">
        <h2 className="text-[18px] font-bold tracking-extra-tight text-text-primary leading-tight">
          {title}
        </h2>
        <div className="text-[11px] text-text-secondary mt-1">
          {passCount} of {totalCount} verified
        </div>
      </div>
      <div className="shrink-0 flex items-center gap-3">
        <div className="w-32 h-1.5 rounded-full bg-canvas overflow-hidden">
          <div
            className="h-full rounded-full bg-[var(--c-success)] transition-all duration-500 ease-out"
            style={{ width: `${pct}%` }}
          />
        </div>
        <span className="font-mono text-[11.5px] tabular-nums text-text-secondary w-9 text-right">
          {pct}%
        </span>
      </div>
    </div>
  );
}

function ChecklistRow({
  item,
  status,
  onToggleCheckbox,
  onCycleStatus,
  hydrated,
}: {
  item: TestChecklistItem;
  status: ChecklistStatus;
  onToggleCheckbox: () => void;
  onCycleStatus: () => void;
  hydrated: boolean;
}) {
  const meta = STATUS_META[status];
  const isPass = status === "pass";
  // Avoid hydration mismatch: render as pending on the server + first client
  // paint, then reveal the persisted state after mount.
  const effectiveStatus = hydrated ? status : "pending";
  const effectiveMeta = STATUS_META[effectiveStatus];
  const effectiveIsPass = hydrated && isPass;

  return (
    <li
      className="group flex items-start gap-3 rounded-[12px] border border-transparent hover:border-border hover:bg-canvas/60 px-2 -mx-2 py-2 transition-all duration-150"
      data-status={effectiveStatus}
    >
      {/* Checkbox */}
      <button
        type="button"
        onClick={onToggleCheckbox}
        aria-pressed={effectiveIsPass}
        aria-label={
          effectiveIsPass
            ? `Mark "${item.title}" as pending`
            : `Mark "${item.title}" as pass`
        }
        className={`mt-[2px] w-[18px] h-[18px] rounded-[6px] border flex items-center justify-center shrink-0 transition-all duration-150 ${
          effectiveIsPass
            ? "bg-[var(--c-success)] border-[var(--c-success)]"
            : "border-border bg-surface hover:border-text-secondary"
        }`}
      >
        {effectiveIsPass && (
          <svg
            width="11"
            height="11"
            viewBox="0 0 24 24"
            fill="none"
            stroke="white"
            strokeWidth="3.5"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M5 13l4 4L19 7" />
          </svg>
        )}
      </button>

      {/* Title + description */}
      <div className="min-w-0 flex-1">
        <div
          className={`text-[13px] font-semibold leading-snug ${
            effectiveIsPass
              ? "text-text-secondary line-through opacity-70"
              : "text-text-primary"
          }`}
        >
          {item.title}
        </div>
        <div className="text-[12px] text-text-secondary leading-relaxed mt-0.5">
          {item.description}
        </div>
        {item.notes && (
          <div className="text-[11px] text-text-secondary leading-relaxed mt-1.5 pl-2.5 border-l-2 border-border">
            <span className="font-medium text-text-primary">Note: </span>
            {item.notes}
          </div>
        )}
      </div>

      {/* Status chip */}
      <button
        type="button"
        onClick={onCycleStatus}
        title={`Click to cycle status (current: ${effectiveMeta.label})`}
        aria-label={`Status: ${effectiveMeta.label}. Click to change.`}
        className="inline-flex items-center gap-1.5 h-6 px-2.5 rounded-full text-[10.5px] font-medium border whitespace-nowrap shrink-0 transition-all duration-200 hover:-translate-y-[1px]"
        style={{
          backgroundColor: `color-mix(in srgb, ${effectiveMeta.colorVar} 12%, transparent)`,
          borderColor: `color-mix(in srgb, ${effectiveMeta.colorVar} 35%, transparent)`,
          color: effectiveMeta.colorVar,
        }}
      >
        <span
          className="inline-block w-1.5 h-1.5 rounded-full"
          style={{ backgroundColor: effectiveMeta.colorVar }}
          aria-hidden="true"
        />
        {effectiveMeta.label}
      </button>
    </li>
  );
}
