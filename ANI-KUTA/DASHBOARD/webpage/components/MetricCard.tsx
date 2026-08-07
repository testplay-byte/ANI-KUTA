import Link from "next/link";
import { Sparkline } from "@/components/Sparkline";
import type { MetricCardData } from "@/lib/data";

/**
 * MetricCard — large metric with sparkline (DESIGN.md §5.13).
 * rounded-[20px], subtle shadow, hover lift.
 * Links to the relevant page.
 */
export function MetricCard({ metric }: { metric: MetricCardData }) {
  return (
    <Link href={metric.href} className="no-underline block h-full">
      <div className="h-full rounded-[20px] border border-border bg-surface p-5 shadow-[0_8px_40px_rgba(0,0,0,0.04)] transition-all duration-200 hover:shadow-[0_12px_40px_rgba(0,0,0,0.08)] hover:-translate-y-[1px]">
        <div className="flex items-start justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <span
              className="w-2 h-2 rounded-full shrink-0"
              style={{ backgroundColor: metric.accent }}
              aria-hidden="true"
            />
            <span className="text-[11px] font-medium uppercase tracking-widest text-text-secondary">
              {metric.label}
            </span>
          </div>
          <TrendArrow trend={metric.trend} color={metric.accent} />
        </div>
        <div className="flex items-end justify-between gap-2">
          <div className="min-w-0">
            <div className="text-[28px] font-bold tracking-extra-tight text-text-primary leading-none">
              {metric.value}
            </div>
            <div className="text-[12px] text-text-secondary mt-2 truncate">
              {metric.sublabel}
            </div>
          </div>
          <Sparkline
            data={metric.sparkline}
            color={metric.accent}
            width={70}
            height={28}
            className="shrink-0"
          />
        </div>
      </div>
    </Link>
  );
}

function TrendArrow({ trend, color }: { trend: "up" | "down" | "flat"; color: string }) {
  if (trend === "flat") {
    return (
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M5 12h14" />
      </svg>
    );
  }
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"
      style={{ transform: trend === "down" ? "rotate(180deg)" : "none" }}>
      <path d="M7 17l5-5 5 5" />
      <path d="M7 12l5-5 5 5" />
    </svg>
  );
}
