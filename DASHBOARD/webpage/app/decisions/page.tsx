"use client";

import { useMemo, useState } from "react";
import { Card, CardHeader } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import { Pill } from "@/components/Pill";
import {
  DECISIONS,
  STATUS_META,
  type Decision,
  type StatusKey,
} from "@/lib/data";

type FilterKey = "all" | StatusKey;

const FILTERS: { key: FilterKey; label: string; accent: string }[] = [
  { key: "all", label: "All", accent: "var(--c-primary)" },
  { key: "confirmed", label: "Confirmed", accent: "var(--c-success)" },
  { key: "pending", label: "Pending", accent: "var(--c-warning)" },
];

export default function DecisionsPage() {
  const [filter, setFilter] = useState<FilterKey>("all");

  const filtered = useMemo(() => {
    if (filter === "all") return DECISIONS;
    return DECISIONS.filter((d) => d.status === filter);
  }, [filter]);

  const counts = useMemo(
    () => ({
      all: DECISIONS.length,
      confirmed: DECISIONS.filter((d) => d.status === "confirmed").length,
      pending: DECISIONS.filter((d) => d.status !== "confirmed").length,
      blocked: DECISIONS.filter((d) => d.status === "blocked").length,
    }),
    [],
  );

  return (
    <div className="space-y-6">
      {/* Header card */}
      <Card>
        <CardHeader
          kicker="Decision Log"
          title="Project Decisions"
          right={
            <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-bg-chip border-border text-text-secondary">
              <StatusDot color="var(--c-success)" size="sm" />
              {counts.confirmed}/{counts.all} confirmed
            </span>
          }
        />
        <p className="text-[13px] text-text-secondary leading-relaxed max-w-2xl">
          Key decisions made during the project — what, why, when, status. Each
          entry is a single source of truth for a design choice. Filter by
          status to focus on what&apos;s settled vs. what&apos;s still open.
        </p>
      </Card>

      {/* Filter pills + legend */}
      <Card className="!p-4">
        <div className="flex flex-wrap items-center gap-2 justify-between">
          <div className="flex flex-wrap items-center gap-2">
            {FILTERS.map((f) => (
              <Pill
                key={f.key}
                active={filter === f.key}
                accentColor={f.accent}
                onClick={() => setFilter(f.key)}
              >
                {f.label}
                <span
                  className={`ml-1 text-[11px] ${
                    filter === f.key ? "opacity-80" : "text-text-secondary"
                  }`}
                >
                  {counts[f.key]}
                </span>
              </Pill>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-4 text-[11.5px] text-text-secondary">
            <LegendItem color={STATUS_META.confirmed.colorVar} label="✅ Confirmed" />
            <LegendItem color={STATUS_META.pending.colorVar} label="⏳ Pending" />
            <LegendItem color={STATUS_META.blocked.colorVar} label="🚧 Blocked" />
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
        <div className="space-y-3">
          {filtered.map((d) => (
            <DecisionCard key={d.id} decision={d} />
          ))}
        </div>
      )}
    </div>
  );
}

function DecisionCard({ decision }: { decision: Decision }) {
  const meta = STATUS_META[decision.status];
  return (
    <Card className="!p-5 hover:translate-y-[-1px]">
      <div className="flex items-start gap-4">
        {/* Left: status icon */}
        <div
          className="shrink-0 w-9 h-9 rounded-full flex items-center justify-center text-[14px]"
          style={{
            backgroundColor: `${meta.colorVar}1a`,
            border: `1px solid ${meta.colorVar}`,
          }}
          aria-hidden="true"
        >
          {meta.symbol}
        </div>

        {/* Right: content */}
        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1 mb-1.5">
            <span className="font-mono text-[12.5px] text-text-secondary">
              {decision.id}
            </span>
            <h3 className="text-[14px] font-semibold text-text-primary leading-tight">
              {decision.title}
            </h3>
            <span
              className="inline-flex items-center gap-1.5 h-5 px-2 rounded-full text-[10.5px] font-medium"
              style={{
                backgroundColor: `${meta.colorVar}1a`,
                color: meta.colorVar,
              }}
            >
              <StatusDot color={meta.colorVar} size="sm" />
              {meta.label}
            </span>
          </div>
          <p className="text-[13px] text-text-secondary leading-relaxed">
            {decision.description}
          </p>
          <div className="mt-2 text-[11px] text-text-secondary font-mono">
            {decision.date}
          </div>
        </div>
      </div>
    </Card>
  );
}

function LegendItem({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <StatusDot color={color} size="sm" />
      <span>{label}</span>
    </span>
  );
}
