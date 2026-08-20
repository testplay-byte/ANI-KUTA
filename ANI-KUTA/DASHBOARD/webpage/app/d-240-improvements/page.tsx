"use client";

import { useState } from "react";
import Link from "next/link";
import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import {
  D240_HERO,
  FIX_ITEMS,
  SCHEMA_EVOLUTION,
  DOWNLOAD_COMPLETE_FLOW,
  EPISODE_DELETE_FLOW,
  SCAN_REBUILD_FLOW,
  TRACKING_SYSTEMS,
  DATA_JSON_V3_EXAMPLE,
  FILE_CHANGES,
  VERIFICATION_CHECKLIST,
  D240_NAV_FOOTER,
  type FixStatus,
} from "@/lib/d240Plan";

/* ---------------------------------------------------------------------------
 * Page — D-240 / D-241 Improvements Plan
 * ------------------------------------------------------------------------- */
export default function D240ImprovementsPage() {
  const [activeFlow, setActiveFlow] = useState<
    "download" | "delete" | "scan"
  >("download");
  const [copied, setCopied] = useState(false);

  const copyExample = async () => {
    try {
      await navigator.clipboard.writeText(DATA_JSON_V3_EXAMPLE);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      /* clipboard unavailable */
    }
  };

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
      {/* ── 1. Hero ── */}
      <section className="mb-10">
        <div className="flex flex-wrap items-center gap-3 mb-3">
          <span
            className="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold uppercase tracking-widest"
            style={{
              backgroundColor: `color-mix(in srgb, ${D240_HERO.statusColor} 15%, transparent)`,
              color: D240_HERO.statusColor,
            }}
          >
            <span
              className="inline-block h-1.5 w-1.5 rounded-full"
              style={{ backgroundColor: D240_HERO.statusColor }}
            />
            {D240_HERO.status}
          </span>
          <span className="text-xs text-[var(--c-text-secondary)]">
            D-240 + D-241 · 10 files modified · 5 root-cause fixes · branch:{" "}
            <code className="font-mono">functionality/improvements</code>
          </span>
        </div>
        <h1 className="text-3xl font-bold tracking-tight text-[var(--c-text-primary)] sm:text-4xl">
          {D240_HERO.title}
        </h1>
        <p className="mt-3 text-base text-[var(--c-text-secondary)] sm:text-lg">
          {D240_HERO.subtitle}
        </p>
        <p className="mt-4 max-w-3xl text-sm leading-relaxed text-[var(--c-text-secondary)]">
          {D240_HERO.summary}
        </p>
      </section>

      {/* ── 2. The 5 user requirements + fixes ── */}
      <SectionHeader
        kicker="Root-cause fixes"
        title="The 5 requirements + their fixes"
        subtitle="Each row: the user requirement, the root cause, the fix, the files touched, and the verification status."
      />
      <div className="space-y-4 mb-10">
        {FIX_ITEMS.map((fix) => (
          <FixCard key={fix.id} fix={fix} />
        ))}
      </div>

      {/* ── 3. Schema evolution ── */}
      <SectionHeader
        kicker="data.json schema"
        title="Schema v1 → v2 → v3 evolution"
        subtitle="The .data.json file in each content folder. v3 is the current schema — backward-compat with v1 + v2 (parser is ignoreUnknownKeys=true, every new field has a default)."
      />
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-10">
        {SCHEMA_EVOLUTION.map((v) => (
          <Card key={v.version} className="!p-5">
            <div className="flex items-center justify-between mb-3">
              <span className="text-[11px] font-medium uppercase tracking-widest text-[var(--c-text-secondary)]">
                Schema
              </span>
              <span
                className={`inline-flex items-center gap-1.5 h-6 px-2 rounded-full text-[11px] font-bold ${
                  v.version === 3
                    ? "bg-[var(--c-success)]/15 text-[var(--c-success)]"
                    : "bg-[var(--c-text-secondary)]/15 text-[var(--c-text-secondary)]"
                }`}
              >
                <StatusDot
                  color={v.version === 3 ? "var(--c-success)" : "var(--c-text-secondary)"}
                  size="sm"
                />
                v{v.version}
              </span>
            </div>
            <div className="text-[12px] text-[var(--c-text-secondary)] mb-3 font-mono">
              {v.date}
            </div>
            <ul className="space-y-1.5 mb-3">
              {v.changes.map((c, i) => (
                <li
                  key={i}
                  className="text-[12.5px] text-[var(--c-text-primary)] leading-relaxed flex gap-1.5"
                >
                  <span className="text-[var(--c-text-secondary)] shrink-0">•</span>
                  <span>{c}</span>
                </li>
              ))}
            </ul>
            <div className="mt-3 pt-3 border-t border-[var(--c-border)]">
              <div className="text-[10px] font-medium uppercase tracking-widest text-[var(--c-text-secondary)] mb-1">
                episodes field
              </div>
              <pre className="text-[10.5px] font-mono text-[var(--c-text-secondary)] overflow-x-auto whitespace-pre-wrap break-all leading-relaxed">
                {v.exampleEpisodesField}
              </pre>
            </div>
          </Card>
        ))}
      </div>

      {/* ── 4. Data flow diagrams ── */}
      <SectionHeader
        kicker="Live data flow"
        title="How .data.json stays in sync"
        subtitle="Three flows: download complete (append), episode delete (remove), scan-on-startup (rebuild). All best-effort — a .data.json write failure never fails the parent operation."
      />
      <div className="mb-4">
        <div className="inline-flex rounded-[12px] border border-[var(--c-border)] bg-[var(--c-surface)] p-1 gap-1">
          {[
            { key: "download" as const, label: "Download complete", icon: "↓" },
            { key: "delete" as const, label: "Episode delete", icon: "×" },
            { key: "scan" as const, label: "Scan on startup", icon: "⟳" },
          ].map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveFlow(tab.key)}
              className={`px-4 py-2 rounded-[8px] text-[12.5px] font-medium transition-all duration-150 ${
                activeFlow === tab.key
                  ? "bg-[var(--c-primary)] text-white shadow-[0_2px_8px_var(--c-primary)40]"
                  : "text-[var(--c-text-secondary)] hover:text-[var(--c-text-primary)] hover:bg-[var(--c-canvas)]"
              }`}
            >
              <span className="mr-1.5">{tab.icon}</span>
              {tab.label}
            </button>
          ))}
        </div>
      </div>
      <Card className="!p-6 mb-10">
        <ol className="relative">
          {(
            activeFlow === "download"
              ? DOWNLOAD_COMPLETE_FLOW
              : activeFlow === "delete"
                ? EPISODE_DELETE_FLOW
                : SCAN_REBUILD_FLOW
          ).map((step, idx, arr) => (
            <li key={step.step} className="flex gap-4 pb-5 last:pb-0 relative">
              {/* Vertical connector line */}
              {idx < arr.length - 1 && (
                <div className="absolute left-[15px] top-8 bottom-0 w-px bg-[var(--c-border)]" />
              )}
              {/* Step number */}
              <div className="shrink-0 w-8 h-8 rounded-full bg-[var(--c-primary)] text-white text-[13px] font-bold flex items-center justify-center z-10">
                {step.step}
              </div>
              {/* Content */}
              <div className="flex-1 min-w-0 pt-0.5">
                <div className="flex items-center gap-2 mb-1 flex-wrap">
                  <code className="text-[11.5px] font-mono font-semibold text-[var(--c-primary)] bg-[var(--c-primary)]/10 px-1.5 py-0.5 rounded">
                    {step.actor}
                  </code>
                </div>
                <p className="text-[13px] text-[var(--c-text-primary)] leading-relaxed mb-1">
                  {step.action}
                </p>
                <p className="text-[12px] text-[var(--c-text-secondary)] leading-relaxed font-mono">
                  → {step.result}
                </p>
              </div>
            </li>
          ))}
        </ol>
      </Card>

      {/* ── 5. v3 .data.json example ── */}
      <SectionHeader
        kicker="Example file"
        title="The v3 .data.json (full)"
        subtitle="What the on-disk file looks like after downloading 2 episodes of Jujutsu Kaisen. The episodes array is the D-241 addition."
      />
      <Card className="!p-0 overflow-hidden mb-10">
        <div className="flex items-center justify-between px-4 py-2.5 border-b border-[var(--c-border)] bg-[var(--c-surface-alt)]">
          <div className="flex items-center gap-2">
            <span className="text-[11px] font-medium uppercase tracking-widest text-[var(--c-text-secondary)]">
              .data.json
            </span>
            <span className="text-[10.5px] font-mono text-[var(--c-text-secondary)]">
              schemaVersion: 3
            </span>
          </div>
          <button
            type="button"
            onClick={copyExample}
            className="text-[11px] font-medium text-[var(--c-primary)] hover:underline"
          >
            {copied ? "✓ Copied" : "Copy"}
          </button>
        </div>
        <pre className="p-4 text-[11.5px] font-mono text-[var(--c-text-primary)] overflow-x-auto leading-relaxed">
          <code>{DATA_JSON_V3_EXAMPLE}</code>
        </pre>
      </Card>

      {/* ── 6. Tracking functionality audit ── */}
      <SectionHeader
        kicker="Tracking audit"
        title="Tracking functionality in the app"
        subtitle="10 tracking systems across 7 modules. Activity Tracker is the PRIMARY (records everything); external trackers (AniList) are SECONDARY (sync via TrackSyncManager). D-241 added Download Tracking as a first-class tracking system."
      />
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3 mb-10">
        {TRACKING_SYSTEMS.map((sys) => (
          <Card key={sys.name} className="!p-4">
            <div className="flex items-start justify-between gap-2 mb-2">
              <h4 className="text-[14px] font-bold text-[var(--c-text-primary)] leading-tight">
                {sys.name}
              </h4>
              <span
                className={`shrink-0 inline-flex items-center gap-1 h-5 px-1.5 rounded-full text-[10px] font-medium ${
                  sys.status === "shipped"
                    ? "bg-[var(--c-success)]/15 text-[var(--c-success)]"
                    : sys.status === "partial"
                      ? "bg-[var(--c-warning)]/15 text-[var(--c-warning)]"
                      : "bg-[var(--c-text-secondary)]/15 text-[var(--c-text-secondary)]"
                }`}
              >
                <StatusDot
                  color={
                    sys.status === "shipped"
                      ? "var(--c-success)"
                      : sys.status === "partial"
                        ? "var(--c-warning)"
                        : "var(--c-text-secondary)"
                  }
                  size="sm"
                />
                {sys.status}
              </span>
            </div>
            <div className="text-[11px] font-mono text-[var(--c-text-secondary)] mb-2">
              {sys.module}
            </div>
            <p className="text-[12px] text-[var(--c-text-primary)] leading-relaxed mb-2">
              {sys.purpose}
            </p>
            <div className="text-[11px] text-[var(--c-text-secondary)] leading-relaxed mb-2">
              <span className="font-medium text-[var(--c-text-primary)]">Storage: </span>
              {sys.storage}
            </div>
            <p className="text-[11px] text-[var(--c-text-secondary)] leading-relaxed">
              {sys.notes}
            </p>
          </Card>
        ))}
      </div>

      {/* ── 7. Files modified ── */}
      <SectionHeader
        kicker="Diff stat"
        title="Files modified (D-240 + D-241)"
        subtitle="10 files across 5 core modules. All changes are on branch functionality/improvements."
      />
      <Card className="!p-0 overflow-hidden mb-10">
        <div className="overflow-x-auto">
          <table className="w-full text-[12.5px]">
            <thead className="bg-[var(--c-surface-alt)] border-b border-[var(--c-border)]">
              <tr>
                <th className="text-left px-4 py-2.5 font-medium text-[var(--c-text-secondary)] text-[11px] uppercase tracking-wider">
                  File
                </th>
                <th className="text-right px-3 py-2.5 font-medium text-[var(--c-text-secondary)] text-[11px] uppercase tracking-wider">
                  +/-
                </th>
                <th className="text-left px-3 py-2.5 font-medium text-[var(--c-text-secondary)] text-[11px] uppercase tracking-wider">
                  Summary
                </th>
                <th className="text-left px-3 py-2.5 font-medium text-[var(--c-text-secondary)] text-[11px] uppercase tracking-wider">
                  Decisions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--c-border)]">
              {FILE_CHANGES.map((fc) => (
                <tr key={fc.file} className="hover:bg-[var(--c-canvas)]">
                  <td className="px-4 py-2.5 font-mono text-[11.5px] text-[var(--c-text-primary)] align-top">
                    {fc.file}
                  </td>
                  <td className="px-3 py-2.5 font-mono text-[11.5px] text-right align-top whitespace-nowrap">
                    <span className="text-[var(--c-success)]">+{fc.linesAdded}</span>
                    {" "}
                    <span className="text-[var(--c-danger)]">-{fc.linesRemoved}</span>
                  </td>
                  <td className="px-3 py-2.5 text-[12px] text-[var(--c-text-primary)] leading-relaxed align-top">
                    {fc.summary}
                  </td>
                  <td className="px-3 py-2.5 align-top">
                    <div className="flex flex-wrap gap-1">
                      {fc.decisions.map((d) => (
                        <span
                          key={d}
                          className="inline-flex items-center h-5 px-1.5 rounded text-[10px] font-mono font-medium bg-[var(--c-primary)]/10 text-[var(--c-primary)]"
                        >
                          {d}
                        </span>
                      ))}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {/* ── 8. Verification checklist ── */}
      <SectionHeader
        kicker="Verification"
        title="Verification checklist"
        subtitle="8 verification items. V8 (code review) is done; V1–V7 require an APK build via GitHub Actions + manual device testing."
      />
      <div className="space-y-2 mb-10">
        {VERIFICATION_CHECKLIST.map((v) => (
          <div
            key={v.id}
            className="flex items-start gap-3 p-3 rounded-[10px] border border-[var(--c-border)] bg-[var(--c-surface-alt)]/40"
          >
            <span
              className={`shrink-0 w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold mt-0.5 ${
                v.status === "done"
                  ? "bg-[var(--c-success)] text-white"
                  : v.status === "partial"
                    ? "bg-[var(--c-warning)] text-white"
                    : "bg-[var(--c-surface)] border border-[var(--c-border)] text-[var(--c-text-secondary)]"
              }`}
            >
              {v.status === "done" ? "✓" : v.status === "partial" ? "~" : ""}
            </span>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1 flex-wrap">
                <code className="text-[11px] font-mono font-semibold text-[var(--c-text-secondary)]">
                  {v.id}
                </code>
                <span className="text-[12.5px] font-medium text-[var(--c-text-primary)]">
                  {v.description}
                </span>
              </div>
              <p className="text-[11.5px] text-[var(--c-text-secondary)] leading-relaxed mb-1">
                <span className="font-medium text-[var(--c-text-primary)]">Method: </span>
                {v.method}
              </p>
              <p className="text-[11px] text-[var(--c-text-secondary)] leading-relaxed">
                {v.notes}
              </p>
            </div>
          </div>
        ))}
      </div>

      {/* ── 9. Footer nav ── */}
      <div className="flex items-center justify-between pt-6 border-t border-[var(--c-border)]">
        <Link
          href={D240_NAV_FOOTER.prev.href}
          className="inline-flex items-center gap-1.5 text-[12.5px] font-medium text-[var(--c-text-secondary)] hover:text-[var(--c-text-primary)] no-underline"
        >
          <span>←</span> {D240_NAV_FOOTER.prev.label}
        </Link>
        <Link
          href={D240_NAV_FOOTER.next.href}
          className="inline-flex items-center gap-1.5 text-[12.5px] font-medium text-[var(--c-primary)] hover:underline no-underline"
        >
          {D240_NAV_FOOTER.next.label} <span>→</span>
        </Link>
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * Helpers
 * ------------------------------------------------------------------------- */

function SectionHeader({
  kicker,
  title,
  subtitle,
}: {
  kicker: string;
  title: string;
  subtitle?: string;
}) {
  return (
    <div className="mb-4">
      <div className="text-[11px] font-medium uppercase tracking-widest text-[var(--c-text-secondary)] mb-1.5">
        {kicker}
      </div>
      <h2 className="text-[22px] font-bold tracking-tight text-[var(--c-text-primary)] leading-tight mb-1.5">
        {title}
      </h2>
      {subtitle && (
        <p className="text-[13px] text-[var(--c-text-secondary)] leading-relaxed max-w-3xl">
          {subtitle}
        </p>
      )}
    </div>
  );
}

function FixCard({ fix }: { fix: (typeof FIX_ITEMS)[number] }) {
  const statusMeta: Record<FixStatus, { color: string; label: string }> = {
    done: { color: "var(--c-success)", label: "Done" },
    partial: { color: "var(--c-warning)", label: "Partial" },
    pending: { color: "var(--c-text-secondary)", label: "Pending" },
  };
  const s = statusMeta[fix.status];
  const priorityMeta = {
    blocking: { color: "var(--c-danger)", label: "BLOCKING" },
    high: { color: "var(--c-warning)", label: "HIGH" },
    medium: { color: "var(--c-primary)", label: "MED" },
    low: { color: "var(--c-text-secondary)", label: "LOW" },
  }[fix.priority];

  return (
    <Card>
      <div className="flex items-start justify-between gap-3 mb-3 flex-wrap">
        <div className="flex items-center gap-2 flex-wrap">
          <code className="text-[12px] font-mono font-bold text-[var(--c-primary)] bg-[var(--c-primary)]/10 px-2 py-0.5 rounded">
            {fix.id}
          </code>
          <span
            className="inline-flex items-center gap-1 h-5 px-1.5 rounded-full text-[10px] font-bold"
            style={{
              backgroundColor: `color-mix(in srgb, ${priorityMeta.color} 15%, transparent)`,
              color: priorityMeta.color,
            }}
          >
            {priorityMeta.label}
          </span>
        </div>
        <span
          className="inline-flex items-center gap-1.5 h-6 px-2 rounded-full text-[11px] font-medium"
          style={{
            backgroundColor: `color-mix(in srgb, ${s.color} 15%, transparent)`,
            color: s.color,
          }}
        >
          <StatusDot color={s.color} size="sm" />
          {s.label}
        </span>
      </div>
      <h3 className="text-[15px] font-bold text-[var(--c-text-primary)] leading-tight mb-3">
        {fix.requirement}
      </h3>
      <div className="space-y-2.5">
        <div>
          <div className="text-[10.5px] font-medium uppercase tracking-widest text-[var(--c-danger)] mb-0.5">
            Root cause
          </div>
          <p className="text-[12.5px] text-[var(--c-text-primary)] leading-relaxed">
            {fix.rootCause}
          </p>
        </div>
        <div>
          <div className="text-[10.5px] font-medium uppercase tracking-widest text-[var(--c-success)] mb-0.5">
            Fix
          </div>
          <p className="text-[12.5px] text-[var(--c-text-primary)] leading-relaxed">
            {fix.fix}
          </p>
        </div>
        <div>
          <div className="text-[10.5px] font-medium uppercase tracking-widest text-[var(--c-text-secondary)] mb-0.5">
            Files
          </div>
          <div className="flex flex-wrap gap-1.5">
            {fix.files.map((f) => (
              <code
                key={f}
                className="text-[10.5px] font-mono text-[var(--c-text-secondary)] bg-[var(--c-surface-alt)] px-1.5 py-0.5 rounded border border-[var(--c-border)]"
              >
                {f}
              </code>
            ))}
          </div>
        </div>
      </div>
    </Card>
  );
}
