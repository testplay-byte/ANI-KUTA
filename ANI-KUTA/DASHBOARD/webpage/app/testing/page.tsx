"use client";

import { useCallback, useState } from "react";
import { Card } from "@/components/Card";
import { TestingChecklist } from "@/components/TestingChecklist";
import {
  TESTING_SECTIONS,
  LOGCAT_ALL_FILTER,
  LOGCAT_FILTERS,
  LOGCAT_HOWTO_STEPS,
  LOGCAT_LEVELS,
  LOGCAT_SHARING_TIPS,
  TESTING_CONCERNS,
  CONCERN_SEVERITY_META,
} from "@/lib/testingData";

/**
 * Testing page — device-testing checklist, logcat capture guide, and
 * open concerns.
 *
 * Layout (DESIGN.md §5.2):
 *  1. Hero card (title + description + quick-stats row).
 *  2. Testing checklist (5 sections, localStorage-persisted checkmarks).
 *  3. Logcat capture instructions (how-to + per-feature filters with copy
 *     buttons + "all at once" filter + level legend + sharing tips).
 *  4. Concerns + open questions (warning-accented cards at the bottom).
 *
 * The page is a client component because the TestingChecklist + the copy
 * buttons need client-side state. The static-export build still emits
 * pre-rendered HTML for the initial server paint.
 */

const STORAGE_KEY = "ani-kuta:testing-checklist:v1";

export default function TestingPage() {
  const totalSteps = TESTING_SECTIONS.reduce(
    (s, sec) => s + sec.steps.length,
    0,
  );

  return (
    <div className="space-y-6">
      {/* ────────────────────────────────────────────────────────────────── */}
      {/* 1. Hero                                                            */}
      {/* ────────────────────────────────────────────────────────────────── */}
      <Card className="!p-6 md:!p-8">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[11px] font-medium uppercase tracking-widest text-text-secondary">
              Device Testing
            </span>
            <span
              className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[10.5px] font-semibold uppercase tracking-widest border"
              style={{
                color: "var(--c-warning)",
                backgroundColor:
                  "color-mix(in srgb, var(--c-warning) 12%, transparent)",
                borderColor:
                  "color-mix(in srgb, var(--c-warning) 35%, transparent)",
              }}
            >
              <span
                className="inline-block w-1.5 h-1.5 rounded-full"
                style={{ backgroundColor: "var(--c-warning)" }}
              />
              Phase WP · HI · UP · SC · DL
            </span>
            <span className="text-[12px] text-text-secondary">
              {TESTING_SECTIONS.length} sections · {totalSteps} steps · checkmarks persist locally
            </span>
          </div>

          <h2 className="text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
            Testing{" "}
            <span className="text-text-secondary font-medium">
              — verify everything on a real device
            </span>
          </h2>

          <p className="text-[13.5px] text-text-secondary leading-relaxed max-w-2xl">
            A comprehensive, ordered testing guide for the features built on
            the{" "}
            <code className="font-mono text-text-primary">
              main
            </code>{" "}
            branch (all feature branches merged + deleted). Work through each section top-to-bottom — watch progress,
            history, updates, schedule, downloads. Checkmarks persist in your
            browser via <code className="font-mono">localStorage</code>, so you
            can close this tab and come back to it. When something doesn&apos;t
            behave as expected, jump to the{" "}
            <a
              href="#logcat"
              className="text-text-primary font-medium underline decoration-dotted underline-offset-2 hover:text-[var(--c-primary)]"
            >
              Logcat capture
            </a>{" "}
            section, grab the filtered logs for that feature, and paste them in
            chat. Open concerns and known limitations are listed at the{" "}
            <a
              href="#concerns"
              className="text-text-primary font-medium underline decoration-dotted underline-offset-2 hover:text-[var(--c-danger)]"
            >
              bottom
            </a>
            .
          </p>

          {/* Quick-jump chips */}
          <div className="flex flex-wrap gap-2 pt-1">
            {TESTING_SECTIONS.map((s) => (
              <a
                key={s.id}
                href={`#section-${s.id}`}
                className="no-underline inline-flex items-center gap-1.5 h-8 px-3 rounded-full text-[12px] font-medium border border-border bg-chip text-text-secondary hover:text-text-primary hover:bg-canvas hover:translate-y-[-1px] transition-all duration-200"
              >
                <span
                  className="inline-block w-1.5 h-1.5 rounded-full"
                  style={{ backgroundColor: s.accentColor }}
                />
                {s.phase}
              </a>
            ))}
          </div>
        </div>
      </Card>

      {/* ────────────────────────────────────────────────────────────────── */}
      {/* 2. Testing checklist                                              */}
      {/* ────────────────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between gap-3 px-1">
        <div>
          <h3 className="text-[15px] font-bold tracking-extra-tight text-text-primary">
            Testing checklist
          </h3>
          <p className="text-[12px] text-text-secondary mt-0.5">
            Tap each step as you verify it. Progress is saved locally.
          </p>
        </div>
      </div>

      <TestingChecklist storageKey={STORAGE_KEY} sections={TESTING_SECTIONS} />

      {/* ────────────────────────────────────────────────────────────────── */}
      {/* 3. Logcat capture                                                 */}
      {/* ────────────────────────────────────────────────────────────────── */}
      <div id="logcat" className="scroll-mt-6">
        <div className="px-1 mb-3">
          <h3 className="text-[15px] font-bold tracking-extra-tight text-text-primary">
            Logcat capture
          </h3>
          <p className="text-[12px] text-text-secondary mt-0.5">
            Filter logcat by feature tag, copy the relevant lines, and paste
            them in chat. Don&apos;t send raw unfiltered logs.
          </p>
        </div>

        <div className="space-y-4">
          {/* How to capture */}
          <Card className="!p-5">
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-3">
              How to capture logs in Android Studio
            </div>
            <ol className="space-y-2.5">
              {LOGCAT_HOWTO_STEPS.map((step, i) => (
                <li key={i} className="flex items-start gap-3">
                  <span className="shrink-0 w-5 h-5 rounded-full bg-chip border border-border text-[10.5px] font-bold text-text-secondary flex items-center justify-center tabular-nums">
                    {i + 1}
                  </span>
                  <span className="text-[13px] text-text-primary leading-relaxed pt-px">
                    {step}
                  </span>
                </li>
              ))}
            </ol>
          </Card>

          {/* Per-feature filters */}
          <Card className="!p-5">
            <div className="flex items-center justify-between gap-3 flex-wrap mb-3">
              <div>
                <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
                  Logcat filters by feature
                </div>
                <p className="text-[12.5px] text-text-secondary leading-relaxed max-w-2xl">
                  Pasteable into Android Studio&apos;s Logcat filter bar. Each
                  filter is scoped to one feature&apos;s tags.
                </p>
              </div>
            </div>

            <div className="space-y-2.5">
              {LOGCAT_FILTERS.map((f) => (
                <FilterRow
                  key={f.feature}
                  feature={f.feature}
                  filter={f.filter}
                  color={f.color}
                />
              ))}
            </div>

            {/* All-at-once filter — full width, monospace, copy button */}
            <div className="mt-4 pt-4 border-t border-border/60">
              <div className="flex items-center justify-between gap-3 mb-2">
                <div className="flex items-center gap-2">
                  <span className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary">
                    All at once
                  </span>
                  <span className="rounded-md px-1.5 py-0.5 text-[10px] font-medium bg-chip border border-border text-text-secondary">
                    every feature tag
                  </span>
                </div>
                <CopyButton text={LOGCAT_ALL_FILTER} />
              </div>
              <pre className="rounded-[10px] border border-border bg-surface-alt p-3.5 text-[11.5px] leading-relaxed font-mono text-text-primary whitespace-pre-wrap break-words overflow-x-auto">
                {LOGCAT_ALL_FILTER}
              </pre>
            </div>
          </Card>

          {/* What to look for + sharing tips — 2-column on lg */}
          <div className="grid gap-4 lg:grid-cols-2">
            <Card className="!p-5">
              <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-3">
                What to look for in the logs
              </div>
              <div className="space-y-2">
                {LOGCAT_LEVELS.map((l) => (
                  <div
                    key={l.level}
                    className="flex items-start gap-2.5 rounded-[10px] p-2 -m-2"
                  >
                    <span
                      className="shrink-0 inline-flex items-center justify-center rounded-[6px] px-1.5 py-0.5 text-[10px] font-bold font-mono text-white tabular-nums min-w-[52px]"
                      style={{ backgroundColor: l.color }}
                    >
                      {l.level}
                    </span>
                    <span className="text-[12.5px] text-text-primary leading-relaxed pt-px">
                      {l.meaning}
                    </span>
                  </div>
                ))}
              </div>
              <p className="text-[12px] text-text-secondary leading-relaxed mt-3 pt-3 border-t border-border/60">
                If something doesn&apos;t work → look for{" "}
                <span className="font-mono text-[var(--c-danger)]">ERROR</span>
                /{" "}
                <span className="font-mono text-[var(--c-warning)]">WARN</span>{" "}
                lines around that feature&apos;s tag.
              </p>
            </Card>

            <Card className="!p-5">
              <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-3">
                How to share logs with the agent
              </div>
              <ul className="space-y-2">
                {LOGCAT_SHARING_TIPS.map((tip, i) => (
                  <li key={i} className="flex items-start gap-2.5">
                    <span
                      className="shrink-0 mt-[6px] w-1.5 h-1.5 rounded-full"
                      style={{ backgroundColor: "var(--c-primary)" }}
                    />
                    <span className="text-[12.5px] text-text-primary leading-relaxed">
                      {tip}
                    </span>
                  </li>
                ))}
              </ul>
            </Card>
          </div>
        </div>
      </div>

      {/* ────────────────────────────────────────────────────────────────── */}
      {/* 4. Concerns + open questions                                      */}
      {/* ────────────────────────────────────────────────────────────────── */}
      <div id="concerns" className="scroll-mt-6">
        <div className="px-1 mb-3">
          <div className="flex items-center gap-2 flex-wrap">
            <h3 className="text-[15px] font-bold tracking-extra-tight text-text-primary">
              Concerns + open questions
            </h3>
            <span
              className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[10.5px] font-semibold uppercase tracking-widest border"
              style={{
                color: "var(--c-danger)",
                backgroundColor:
                  "color-mix(in srgb, var(--c-danger) 10%, transparent)",
                borderColor:
                  "color-mix(in srgb, var(--c-danger) 30%, transparent)",
              }}
            >
              <span
                className="inline-block w-1.5 h-1.5 rounded-full"
                style={{ backgroundColor: "var(--c-danger)" }}
              />
              {TESTING_CONCERNS.length} known limitations
            </span>
          </div>
          <p className="text-[12px] text-text-secondary mt-1">
            Deferred features, missing implementations, stale data, and doc
            debt. Read these before reporting a bug — the behavior may already
            be a known limitation.
          </p>
        </div>

        <div className="grid gap-3 md:grid-cols-2">
          {TESTING_CONCERNS.map((c) => {
            const meta = CONCERN_SEVERITY_META[c.severity];
            return (
              <div
                key={c.id}
                className="rounded-[14px] border border-border bg-bg-card-alt p-4 shadow-card hover:shadow-hover hover:translate-y-[-1px] transition-all duration-200"
                style={{
                  borderLeft: `3px solid ${meta.color}`,
                }}
              >
                <div className="flex items-start justify-between gap-2 mb-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="font-mono text-[11px] font-bold text-text-secondary shrink-0">
                      {c.id}
                    </span>
                    <span
                      className="inline-flex items-center gap-1 rounded-[6px] px-1.5 py-0.5 text-[9.5px] font-semibold uppercase tracking-widest"
                      style={{
                        color: meta.color,
                        backgroundColor: `color-mix(in srgb, ${meta.color} 14%, transparent)`,
                      }}
                    >
                      {meta.label}
                    </span>
                  </div>
                </div>
                <h4 className="text-[13.5px] font-bold tracking-extra-tight text-text-primary leading-tight mb-1.5">
                  {c.title}
                </h4>
                <p className="text-[12px] text-text-secondary leading-relaxed">
                  {c.body}
                </p>
              </div>
            );
          })}
        </div>

        {/* Severity legend */}
        <Card className="!p-4 mt-3">
          <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
            <span className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary">
              Severity legend
            </span>
            {(
              Object.keys(CONCERN_SEVERITY_META) as Array<
                keyof typeof CONCERN_SEVERITY_META
              >
            ).map((sev) => {
              const meta = CONCERN_SEVERITY_META[sev];
              const count = TESTING_CONCERNS.filter(
                (c) => c.severity === sev,
              ).length;
              return (
                <div key={sev} className="flex items-center gap-2">
                  <span
                    className="inline-block w-2 h-2 rounded-full"
                    style={{ backgroundColor: meta.color }}
                  />
                  <span className="text-[12px] text-text-primary font-medium">
                    {meta.label}
                  </span>
                  <span className="text-[11px] font-mono text-text-secondary tabular-nums">
                    {count}
                  </span>
                </div>
              );
            })}
          </div>
        </Card>
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * FilterRow — one feature's filter with a copy button.
 * ------------------------------------------------------------------------- */
function FilterRow({
  feature,
  filter,
  color,
}: {
  feature: string;
  filter: string;
  color: string;
}) {
  return (
    <div className="rounded-[10px] border border-border bg-surface-alt/60 p-3 hover:border-text-secondary/30 transition-colors">
      <div className="flex items-center justify-between gap-3 mb-1.5">
        <div className="flex items-center gap-2 min-w-0">
          <span
            className="inline-block w-2 h-2 rounded-full shrink-0"
            style={{ backgroundColor: color }}
          />
          <span className="text-[12.5px] font-semibold text-text-primary truncate">
            {feature}
          </span>
        </div>
        <CopyButton text={filter} />
      </div>
      <code className="block font-mono text-[11.5px] text-text-secondary leading-relaxed break-words">
        {filter}
      </code>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * CopyButton — small icon+label button that copies text to the clipboard.
 * Shows a transient "Copied!" state for 1.5s after success.
 * ------------------------------------------------------------------------- */
function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);

  const onCopy = useCallback(async () => {
    try {
      if (typeof navigator !== "undefined" && navigator.clipboard) {
        await navigator.clipboard.writeText(text);
      } else if (typeof document !== "undefined") {
        // Legacy fallback for non-secure contexts.
        const ta = document.createElement("textarea");
        ta.value = text;
        ta.style.position = "fixed";
        ta.style.opacity = "0";
        document.body.appendChild(ta);
        ta.select();
        document.execCommand("copy");
        document.body.removeChild(ta);
      }
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      /* clipboard unavailable — no-op */
    }
  }, [text]);

  return (
    <button
      type="button"
      onClick={onCopy}
      aria-label={copied ? "Copied to clipboard" : "Copy filter to clipboard"}
      className={`inline-flex items-center gap-1.5 h-7 px-2.5 rounded-[8px] text-[11px] font-medium border transition-all duration-200 hover:translate-y-[-1px] ${
        copied
          ? "border-[var(--c-success)] text-[var(--c-success)] bg-[color-mix(in_srgb,var(--c-success)_10%,transparent)]"
          : "border-border bg-surface text-text-secondary hover:text-text-primary hover:bg-canvas"
      }`}
    >
      {copied ? (
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.4"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="w-3.5 h-3.5"
          aria-hidden="true"
        >
          <path d="M5 13l4 4L19 7" />
        </svg>
      ) : (
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="w-3.5 h-3.5"
          aria-hidden="true"
        >
          <rect x="9" y="9" width="13" height="13" rx="2" />
          <path d="M5 15V5a2 2 0 012-2h10" />
        </svg>
      )}
      {copied ? "Copied" : "Copy"}
    </button>
  );
}
