"use client";

import { useMemo, useState } from "react";
import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import { DecisionCard } from "@/components/DecisionCard";
import {
  decisions,
  DECISION_STATUS_META,
  type DecisionStatus,
} from "@/lib/decisions";

type FilterKey = "all" | DecisionStatus;

const FILTERS: { key: FilterKey; label: string; accent: string }[] = [
  { key: "all", label: "All", accent: "var(--c-primary)" },
  { key: "needs-input", label: "Needs Input", accent: "var(--c-danger)" },
  { key: "pending", label: "Pending", accent: "var(--c-warning)" },
  { key: "confirmed", label: "Confirmed", accent: "var(--c-success)" },
];

export default function DecisionsPage() {
  const [filter, setFilter] = useState<FilterKey>("all");

  const counts = useMemo(
    () => ({
      all: decisions.length,
      confirmed: decisions.filter((d) => d.status === "confirmed").length,
      pending: decisions.filter((d) => d.status === "pending").length,
      "needs-input": decisions.filter((d) => d.status === "needs-input").length,
    }),
    [],
  );

  const filtered = useMemo(() => {
    if (filter === "all") return decisions;
    return decisions.filter((d) => d.status === filter);
  }, [filter]);

  return (
    <div className="space-y-6">
      {/* Header card — summary */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-3">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Decision Log
            </div>
            <h2 className="text-[22px] font-bold tracking-extra-tight text-text-primary">
              Architecture Decisions
            </h2>
          </div>
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-success)" size="sm" />
            {counts["confirmed"]}/{decisions.length} confirmed · D-001..D-186
          </span>
        </div>
        <p className="text-[13px] text-text-secondary leading-relaxed max-w-2xl">
          Each decision below needs your review. Every option shows its{" "}
          <span className="text-[var(--c-success)] font-medium">pros</span> (teal){" "}
          and{" "}
          <span className="text-[var(--c-danger)] font-medium">cons</span> (rose),
          plus a recommendation badge. Filter by status to focus on what needs
          your attention.
        </p>
      </Card>

      {/* Filter pills + legend */}
      <Card className="!p-3.5">
        <div className="flex flex-wrap items-center gap-2 justify-between">
          <div className="flex flex-wrap items-center gap-1.5">
            {FILTERS.map((f) => {
              const active = filter === f.key;
              return (
                <button
                  key={f.key}
                  type="button"
                  onClick={() => setFilter(f.key)}
                  className="h-8 px-3 rounded-[12px] text-[12.5px] font-medium transition-all duration-200 flex items-center gap-2 border"
                  style={
                    active
                      ? {
                          backgroundColor: f.accent,
                          borderColor: f.accent,
                          color: "#fff",
                          boxShadow: `0 4px 12px ${f.accent}33`,
                        }
                      : {
                          backgroundColor: "transparent",
                          borderColor: "var(--c-border)",
                          color: "var(--c-text-secondary)",
                        }
                  }
                >
                  {f.label}
                  <span
                    className={`text-[11px] tabular-nums ${
                      active ? "opacity-80" : ""
                    }`}
                  >
                    {counts[f.key]}
                  </span>
                </button>
              );
            })}
          </div>
          <div className="flex flex-wrap items-center gap-3 text-[11px] text-text-secondary">
            {(Object.keys(DECISION_STATUS_META) as DecisionStatus[]).map((s) => (
              <span key={s} className="inline-flex items-center gap-1.5">
                <StatusDot color={DECISION_STATUS_META[s].colorVar} size="sm" />
                <span>{DECISION_STATUS_META[s].symbol} {DECISION_STATUS_META[s].label}</span>
              </span>
            ))}
          </div>
        </div>
      </Card>

      {/* Decision list */}
      {filtered.length === 0 ? (
        <Card>
          <div className="text-center py-8 text-text-secondary text-[13px]">
            No decisions match this filter.
          </div>
        </Card>
      ) : (
        <div className="space-y-4">
          {filtered.map((d) => (
            <DecisionCard key={d.id} decision={d} />
          ))}
        </div>
      )}
    </div>
  );
}
