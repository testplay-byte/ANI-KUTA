"use client";

import { useState } from "react";
import Link from "next/link";
import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import {
  DOWNLOADS_HERO,
  ARCHITECTURE_DIAGRAM,
  MODULE_MAP,
  WORKFLOW_STEPS,
  STATE_MACHINE_DIAGRAM,
  STATE_MACHINE_STATES,
  STATE_MACHINE_TRANSITIONS,
  STATE_DISALLOWED_NOTE,
  STORAGE_TREE,
  STORAGE_TEMP_CACHE,
  STORAGE_DATA_JSON_EXAMPLE,
  STORAGE_NAMING_RULES,
  STORAGE_DECISIONS,
  FILE_PROVIDER_CONFIG,
  DOWNLOADERS,
  DYNAMIC_PROGRESS_TRACKER,
  QUEUE_LOGIC,
  SETTINGS,
  ENUMS_REFERENCE,
  DOWNLOADS_PAGE_UI,
  EPISODE_DOWNLOAD_STATES,
  DETAILS_PAGE_NOTES,
  NOTIFICATIONS_FOREGROUND_CALLOUT,
  NOTIFICATION_PLAN,
  NOTIFICATION_CONSTANTS,
  PLAYER_INTEGRATION_DIAGRAM,
  PLAYER_INTEGRATION_NOTES,
  DB_SCHEMA_DECISION,
  DB_SCHEMA_TABLES,
  DB_OLD_PROJECT_NO_DOWNLOAD_TABLES,
  DI_MODULES,
  DI_GRAPH,
  IMPLEMENTATION_PHASES,
  IMPLEMENTATION_TOTAL_ESTIMATE,
  DESIGN_DECISIONS,
  RISKS,
  OLD_PROJECT_BUGS,
  DOWNLOADS_PLAN_NAV_FOOTER,
  REVIEW_ROUNDS,
  REVIEW_TOP_5_FIXES,
  REVIEW_FIX_BREAKDOWN,
  REVIEW_VERDICT,
  AUTO_DOWNLOAD_PIPELINE,
  AUTO_DOWNLOAD_SETTINGS,
  AUTO_DOWNLOAD_WORKED_EXAMPLE,
  AUTO_DOWNLOAD_CUSTOMIZABILITY,
  PROXY_CHURN_ROOT_CAUSE,
  PROXY_CHURN_4_LAYERS,
  PROXY_CHURN_RERESOLVER,
  PROXY_CHURN_ARCHITECTURAL_RULES,
  QOL_FEATURES,
  QOL_RETRY_POLICY_TABLE,
} from "@/lib/downloadsPlan";

/* ---------------------------------------------------------------------------
 * Page — Download System Implementation Plan
 * ------------------------------------------------------------------------- */
export default function DownloadsPlanPage() {
  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
      {/* ── 1. Hero ── */}
      <section className="mb-10">
        <div className="flex flex-wrap items-center gap-3 mb-3">
          <span
            className="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold uppercase tracking-widest"
            style={{
              backgroundColor: `color-mix(in srgb, ${DOWNLOADS_HERO.statusColor} 15%, transparent)`,
              color: DOWNLOADS_HERO.statusColor,
            }}
          >
            <span
              className="inline-block h-1.5 w-1.5 rounded-full"
              style={{ backgroundColor: DOWNLOADS_HERO.statusColor }}
            />
            {DOWNLOADS_HERO.status}
          </span>
          <span className="text-xs text-[var(--c-text-secondary)]">
            15 plan docs · 5 review rounds · 72 must-fix items · 9 phases (D.0→D.8) · 30-40 days
          </span>
        </div>
        <h1 className="text-3xl font-bold tracking-tight text-[var(--c-text-primary)] sm:text-4xl">
          {DOWNLOADS_HERO.title}
        </h1>
        <p className="mt-3 text-base text-[var(--c-text-secondary)] sm:text-lg">
          {DOWNLOADS_HERO.subtitle}
        </p>
        <p className="mt-4 max-w-3xl text-sm leading-relaxed text-[var(--c-text-secondary)]">
          {DOWNLOADS_HERO.summary}
        </p>
      </section>

      {/* ── 2. Review Findings (NEW — the 5 review rounds + 72-item fix pass) ── */}
      <SectionHeader
        number={2}
        title="Review Findings — 5 rounds, 72 must-fix items, all fixed"
        subtitle="The plan went through 5 senior review rounds (DL-REVIEW-1 → DL-REVIEW-5) + a consolidation pass (DL-PLAN-FIX). All 72 items are now explicit action items in 13-implementation-plan.md §6.1. Sources: REVIEW-1 through REVIEW-5 + REVIEW-5-final.md §8."
        critical
      />
      <div className="mb-10 space-y-4">
        {/* The amber callout — the headline number */}
        <Card className="p-4">
          <div
            className="rounded-lg p-4"
            style={{
              backgroundColor: `color-mix(in srgb, var(--c-warning) 10%, transparent)`,
              borderLeft: `4px solid var(--c-warning)`,
            }}
          >
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <span
                className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-bold uppercase tracking-widest text-white"
                style={{ backgroundColor: "var(--c-warning)" }}
              >
                Plan Hardened
              </span>
              <span className="text-sm font-bold text-[var(--c-text-primary)]">
                5 review rounds surfaced 72 must-fix items — every one is now an action item
              </span>
            </div>
            <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">
              The original plan draft was a clean rewrite of the old project's download system, but
              it carried <span className="font-bold text-[var(--c-text-primary)]">18 unresolved CRITICALs from Reviews 1-4</span> (none
              had been addressed) + <span className="font-bold text-[var(--c-text-primary)]">3 NEW CRITICALs + 51 IMPORTANTs in REVIEW-5</span>.
              An implementer following the draft verbatim would have shipped a non-compiling build
              (Coil 2 on Coil 3, <code>HttpException</code> unresolved, <code>notificationManager</code> undefined,
              <code>KoinComponent</code> missing), a <code>StackOverflowError</code> (unbounded re-resolve recursion),
              a <code>ForegroundServiceDidNotStartInTimeException</code> crash on Android 12+, corrupt HLS output
              on flaky CDNs, tasks stuck in RETRYING forever after a crash, the user's "progress bar jumps to 100%"
              complaint NOT actually fixed, + a <code>NoBeanDefFoundException</code> for <code>DownloadStorageProvider</code>.
              The DL-PLAN-FIX consolidation pass closed every one.
            </p>
          </div>
        </Card>

        {/* The 5 review rounds */}
        <div className="grid gap-3 lg:grid-cols-2">
          {REVIEW_ROUNDS.map((r) => (
            <Card key={r.id} className="p-4">
              <div className="mb-2 flex flex-wrap items-center gap-2">
                <span
                  className="rounded-md px-2 py-0.5 text-[10px] font-bold text-white"
                  style={{ backgroundColor: r.color }}
                >
                  {r.id}
                </span>
                <span className="rounded-md bg-[var(--c-surface-alt)] px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-[var(--c-text-secondary)]">
                  {r.criticals} CRITICAL · {r.importants} IMPORTANT
                </span>
              </div>
              <h3 className="mb-1.5 text-sm font-bold leading-tight text-[var(--c-text-primary)]">
                {r.focus}
              </h3>
              <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">{r.findings}</p>
            </Card>
          ))}
        </div>

        {/* Top 5 highest-impact fixes */}
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-danger)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Top 5 highest-impact fixes (in priority order)
            </span>
          </div>
          <div className="divide-y divide-[var(--c-border)]">
            {REVIEW_TOP_5_FIXES.map((f) => (
              <div key={f.rank} className="p-4">
                <div className="mb-2 flex flex-wrap items-center gap-2">
                  <span
                    className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-xs font-bold text-white"
                    style={{ backgroundColor: f.color }}
                  >
                    {f.rank}
                  </span>
                  <code
                    className="rounded-md bg-[var(--c-surface-alt)] px-2 py-0.5 text-[10px] font-bold tracking-wider"
                    style={{ color: f.color }}
                  >
                    {f.mNumber}
                  </code>
                  <h3 className="text-sm font-bold leading-tight text-[var(--c-text-primary)]">
                    {f.title}
                  </h3>
                </div>
                <div className="ml-9 space-y-2">
                  <div>
                    <div className="mb-0.5 text-[10px] font-bold uppercase tracking-widest text-[var(--c-danger)]">
                      Before
                    </div>
                    <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">{f.before}</p>
                  </div>
                  <div>
                    <div className="mb-0.5 text-[10px] font-bold uppercase tracking-widest text-[var(--c-success)]">
                      After
                    </div>
                    <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">{f.after}</p>
                  </div>
                  <div>
                    <div className="mb-0.5 text-[10px] font-bold uppercase tracking-widest text-[var(--c-warning)]">
                      Why it matters
                    </div>
                    <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">{f.why}</p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </Card>

        {/* The 72-item breakdown by phase */}
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-secondary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              The 72 MUST-FIX items — grouped by phase (DL-PLAN-FIX consolidation pass)
            </span>
          </div>
          <PreBlock>{REVIEW_FIX_BREAKDOWN}</PreBlock>
        </Card>

        {/* The verdict */}
        <Card className="p-4">
          <div
            className="rounded-lg p-4"
            style={{
              backgroundColor: `color-mix(in srgb, ${REVIEW_VERDICT.color} 8%, transparent)`,
              borderLeft: `3px solid ${REVIEW_VERDICT.color}`,
            }}
          >
            <div className="mb-1.5 flex items-center gap-2">
              <span
                className="inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-widest text-white"
                style={{ backgroundColor: REVIEW_VERDICT.color }}
              >
                Verdict
              </span>
              <span className="text-sm font-bold text-[var(--c-text-primary)]">All 72 items applied across 15 plan docs</span>
            </div>
            <p className="mb-2 text-xs leading-relaxed text-[var(--c-text-secondary)]">{REVIEW_VERDICT.verdict}</p>
            <p className="mb-2 text-xs leading-relaxed text-[var(--c-text-secondary)]">
              <span className="font-bold text-[var(--c-text-primary)]">Recommended next step:</span>{" "}
              {REVIEW_VERDICT.nextStep}
            </p>
            <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">
              <span className="font-bold text-[var(--c-text-primary)]">Single highest-impact fix:</span>{" "}
              {REVIEW_VERDICT.highestImpact}
            </p>
          </div>
        </Card>
      </div>

      {/* ── 3. Architecture Overview ── */}
      <SectionHeader
        number={3}
        title="Architecture Overview"
        subtitle="The module map + the click→queue→publish data-flow. Old project structure that the new project replicates."
      />
      <div className="mb-10 space-y-4">
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Old project — module + data-flow diagram (00-overview.md §2)
            </span>
          </div>
          <PreBlock>{ARCHITECTURE_DIAGRAM}</PreBlock>
        </Card>

        <Card className="overflow-hidden p-0">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Module map — 6 modules (00-overview.md §3)
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-[var(--c-border)]">
                <tr className="bg-[var(--c-surface)]">
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Module</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Role</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Key files</th>
                </tr>
              </thead>
              <tbody>
                {MODULE_MAP.map((m) => (
                  <tr key={m.module} className="border-b border-[var(--c-border)] last:border-0 align-top">
                    <td className="px-4 py-2.5">
                      <code className="font-bold text-[var(--c-primary)]">{m.module}</code>
                    </td>
                    <td className="px-4 py-2.5 text-[var(--c-text-primary)]">{m.role}</td>
                    <td className="px-4 py-2.5 font-mono text-[var(--c-text-secondary)]">{m.keyFiles}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      </div>

      {/* ── 4. Workflow: Click → Queue ── */}
      <SectionHeader
        number={4}
        title="Workflow: Click → Queue"
        subtitle="The 10-step trace from tapping the download icon on an episode row to the file landing on disk. Source: 01-workflow-click-to-queue.md."
      />
      <div className="mb-10 space-y-3">
        {WORKFLOW_STEPS.map((s) => (
          <Card key={s.step} className="p-4 sm:p-5">
            <div className="flex items-start gap-3">
              <span
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-sm font-bold text-white"
                style={{ backgroundColor: "var(--c-primary)" }}
              >
                {s.step}
              </span>
              <div className="min-w-0 flex-1">
                <h3 className="text-sm font-bold text-[var(--c-text-primary)]">{s.title}</h3>
                <p className="mt-1.5 text-xs leading-relaxed text-[var(--c-text-secondary)]">{s.description}</p>
                <p className="mt-2 font-mono text-[10px] text-[var(--c-text-secondary)]">
                  <span className="font-bold">ref:</span> {s.fileRef}
                </p>
                {s.codeSnippet && (
                  <PreBlock compact className="mt-3">
                    {s.codeSnippet}
                  </PreBlock>
                )}
              </div>
            </div>
          </Card>
        ))}
      </div>

      {/* ── 5. State Machine ── */}
      <SectionHeader
        number={5}
        title="State Machine"
        subtitle="7 states (incl. the NEW RETRYING — M9) + 19 transitions. Source: 03-state-machine.md + REVIEW-5 M6/M9/M10/M11/M12/M13/M14."
      />
      <div className="mb-10 space-y-4">
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              State diagram (KDoc on DownloadStatus.kt:7-14)
            </span>
          </div>
          <PreBlock>{STATE_MACHINE_DIAGRAM}</PreBlock>
        </Card>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {STATE_MACHINE_STATES.map((s) => (
            <Card key={s.name} className="p-4">
              <div className="mb-1.5 flex items-center gap-2">
                <StatusDot color={s.color} size="md" />
                <code className="text-sm font-bold text-[var(--c-text-primary)]">{s.name}</code>
                {s.terminal && (
                  <span className="rounded-md bg-[var(--c-surface-alt)] px-1.5 py-0.5 text-[9px] font-bold uppercase text-[var(--c-text-secondary)]">
                    terminal
                  </span>
                )}
              </div>
              <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">{s.meaning}</p>
            </Card>
          ))}
        </div>

        <Card className="overflow-hidden p-0">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Allowed transitions (reference table)
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-[var(--c-border)]">
                <tr className="bg-[var(--c-surface)]">
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">From</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Action</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">To</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Enforced by</th>
                </tr>
              </thead>
              <tbody>
                {STATE_MACHINE_TRANSITIONS.map((t, i) => (
                  <tr key={i} className="border-b border-[var(--c-border)] last:border-0">
                    <td className="px-4 py-2 font-mono text-[var(--c-text-primary)]">{t.from}</td>
                    <td className="px-4 py-2 font-mono text-[var(--c-secondary)]">{t.action}</td>
                    <td className="px-4 py-2 font-mono text-[var(--c-text-primary)]">{t.to}</td>
                    <td className="px-4 py-2 font-mono text-[var(--c-text-secondary)]">{t.enforcedBy}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>

        <Card className="p-4">
          <div className="flex items-start gap-2">
            <span style={{ color: "var(--c-warning)" }}>⚠</span>
            <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">{STATE_DISALLOWED_NOTE}</p>
          </div>
        </Card>
      </div>

      {/* ── 6. Storage Paths (CRITICAL — REWRITTEN) ── */}
      <SectionHeader
        number={6}
        title="Storage Paths (REWRITTEN — video/images/text + data.json + 5-digit padding)"
        subtitle="CRITICAL — the folder tree was rewritten (REVIEW-5: video/images/text format folders, 5-digit E00001 padding, NO AniList ID, ONE data.json per content with the 6-section contentId, .nomedia to prevent gallery pollution, scan-on-startup for reinstall recognition). Source: 04-storage-paths.md (rewritten in DL-PLAN-FIX)."
        critical
      />
      <div className="mb-10 space-y-4">
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-primary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Canonical SAF folder structure (DownloadStorageProvider.kt:18-28)
            </span>
          </div>
          <PreBlock>{STORAGE_TREE}</PreBlock>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-secondary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Internal temp cache (TempDownloadCache.kt) — internal-cache-first pipeline + M59 hasSpaceFor check
            </span>
          </div>
          <PreBlock>{STORAGE_TEMP_CACHE}</PreBlock>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-success)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              data.json — per-content source-of-truth (NEW — 6-section contentId M4, full FK set M5, .nomedia M54)
            </span>
          </div>
          <PreBlock>{STORAGE_DATA_JSON_EXAMPLE}</PreBlock>
        </Card>

        <Card className="overflow-hidden p-0">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Folder + file name builders (04-storage-paths.md §2)
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-[var(--c-border)]">
                <tr className="bg-[var(--c-surface)]">
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Kind</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Pattern</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Examples</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Notes</th>
                </tr>
              </thead>
              <tbody>
                {STORAGE_NAMING_RULES.map((r) => (
                  <tr key={r.kind} className="border-b border-[var(--c-border)] last:border-0 align-top">
                    <td className="px-4 py-2.5">
                      <code className="font-bold text-[var(--c-primary)]">{r.kind}</code>
                    </td>
                    <td className="px-4 py-2.5 font-mono text-[var(--c-text-primary)]">{r.pattern}</td>
                    <td className="px-4 py-2.5 font-mono text-[var(--c-text-secondary)]">{r.examples}</td>
                    <td className="px-4 py-2.5 text-[var(--c-text-secondary)]">{r.notes}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>

        <div className="grid gap-3 sm:grid-cols-2">
          {STORAGE_DECISIONS.map((d) => (
            <Card key={d.title} className="p-4">
              <div className="mb-2 flex items-start gap-2">
                <StatusDot color={d.color} size="md" />
                <h3 className="text-sm font-bold leading-tight text-[var(--c-text-primary)]">{d.title}</h3>
              </div>
              <p className="mb-2 text-xs font-medium text-[var(--c-text-primary)]">{d.recommendation}</p>
              <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">{d.rationale}</p>
            </Card>
          ))}
        </div>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              FileProvider config (app/src/main/res/xml/file_paths.xml + AndroidManifest.xml:90-98)
            </span>
          </div>
          <PreBlock>{FILE_PROVIDER_CONFIG}</PreBlock>
        </Card>
      </div>

      {/* ── 7. Auto-Download Engine (NEW) ── */}
      <SectionHeader
        number={7}
        title="Auto-Download Engine (NEW — 5-step pipeline + dimensionPriority)"
        subtitle="The new pure-function pipeline (flatten → rank → applyFallbacks → pick → globalFallback) replaces the OLD 4-step selectBestVideo. The user's 'which dimension matters most' gap is now configurable via the dimensionPriority pref (default [AUDIO, QUALITY, SERVER]). Source: 14-auto-download-engine.md §6 (REVIEW-5 M44/M45 fixes)."
      />
      <div className="mb-10 space-y-4">
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-primary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              The 5-step pure-function pipeline (14-auto-download-engine.md §6.2)
            </span>
          </div>
          <PreBlock>{AUTO_DOWNLOAD_PIPELINE}</PreBlock>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-secondary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              The NEW dimensionPriority + globalFallback preferences (14-auto-download-engine.md §6.1)
            </span>
          </div>
          <PreBlock>{AUTO_DOWNLOAD_SETTINGS}</PreBlock>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-success)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Worked example — dimensionPriority = [AUDIO, QUALITY, SERVER] (audio matters most)
            </span>
          </div>
          <PreBlock>{AUTO_DOWNLOAD_WORKED_EXAMPLE}</PreBlock>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-warning)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Why this stays "highly customizable for future changes" (14-auto-download-engine.md §6.4)
            </span>
          </div>
          <PreBlock>{AUTO_DOWNLOAD_CUSTOMIZABILITY}</PreBlock>
        </Card>
      </div>

      {/* ── 8. Download Engines ── */}
      <SectionHeader
        number={8}
        title="Download Engines"
        subtitle="HTTP (single-threaded) vs HLS (.m3u8 segment concatenator, no ffmpeg) vs Advanced (multi-threaded Range + resume). Source: 05-downloaders.md."
      />
      <div className="mb-10 space-y-4">
        <div className="grid gap-4 lg:grid-cols-3">
          {DOWNLOADERS.map((d) => (
            <Card key={d.name} className="flex flex-col p-4">
              <div className="mb-2 flex items-center justify-between gap-2">
                <span
                  className="rounded-md px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-white"
                  style={{ backgroundColor: d.badgeColor }}
                >
                  {d.badge}
                </span>
              </div>
              <h3 className="mb-1.5 text-sm font-bold leading-tight text-[var(--c-text-primary)]">{d.name}</h3>
              <p className="mb-2 text-xs font-medium text-[var(--c-text-primary)]">{d.supports}</p>
              <p className="mb-3 text-xs leading-relaxed text-[var(--c-text-secondary)]">{d.pipeline}</p>
              <div className="mt-auto">
                <div className="mb-1 text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
                  Honest notes
                </div>
                <ul className="space-y-1">
                  {d.honestNotes.map((n, i) => (
                    <li key={i} className="flex gap-1.5 text-xs text-[var(--c-text-secondary)]">
                      <span style={{ color: d.badgeColor }}>▸</span>
                      <span>{n}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </Card>
          ))}
        </div>

        <Card className="p-4">
          <div className="mb-2 flex items-center gap-2">
            <StatusDot color="var(--c-warning)" size="md" />
            <h3 className="text-sm font-bold text-[var(--c-text-primary)]">DynamicProgressTracker — smooth progress UI</h3>
          </div>
          <p className="mb-2 text-xs leading-relaxed text-[var(--c-text-secondary)]">{DYNAMIC_PROGRESS_TRACKER.problem}</p>
          <PreBlock compact className="mt-2">
            {DYNAMIC_PROGRESS_TRACKER.algorithm}
          </PreBlock>
          <p className="mt-2 text-xs leading-relaxed text-[var(--c-text-secondary)]">
            <span className="font-bold">Constants:</span> {DYNAMIC_PROGRESS_TRACKER.constants}
          </p>
        </Card>
      </div>

      {/* ── 9. Queue Management ── */}
      <SectionHeader
        number={9}
        title="Queue Management"
        subtitle="DownloadQueue internals — concurrency, FIFO ordering, persistence, dedup, threading. Source: 02-queue-management.md."
      />
      <div className="mb-10 grid gap-4 sm:grid-cols-2">
        {QUEUE_LOGIC.map((q) => (
          <Card key={q.title} className="p-4">
            <div className="mb-2 flex items-center gap-2">
              <StatusDot color={q.color} size="md" />
              <h3 className="text-sm font-bold leading-tight text-[var(--c-text-primary)]">{q.title}</h3>
            </div>
            <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">{q.body}</p>
          </Card>
        ))}
      </div>

      {/* ── 10. Proxy-Churn Bug Fix (NEW) ── */}
      <SectionHeader
        number={10}
        title="Proxy-Churn Bug Fix (NEW — root cause + 4-layer fix)"
        subtitle="The bug: a download is in-flight → user plays another episode from the same source → the extension's proxy server is killed → the download fails. The OLD project can't fix this. The new project has 4 layers (directUrl + re-resolve-on-IOException + ProxyLeaseCoordinator + foreground service). Sources: 15-ui-and-bug-analysis.md Part B + 10-player-integration.md §14 (REVIEW-5 M15/M16/M17/M19 fixes)."
        critical
      />
      <div className="mb-10 space-y-4">
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-danger)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              The root cause (15-ui-and-bug-analysis.md Part B)
            </span>
          </div>
          <PreBlock>{PROXY_CHURN_ROOT_CAUSE}</PreBlock>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-primary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              The 4-layer fix (10-player-integration.md §14.1) — Layer 1 (directUrl) + Layer 2 (re-resolve, M15) + Layer 3 (ProxyLeaseCoordinator, deferred) + Layer 4 (foreground service, M20)
            </span>
          </div>
          <PreBlock>{PROXY_CHURN_4_LAYERS}</PreBlock>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-secondary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              The ReResolver class (M17 — DIRECT lookup, NOT a re-run of AutoDownloadEngine) + ResolveContext (M64 — 7 fields)
            </span>
          </div>
          <PreBlock>{PROXY_CHURN_RERESOLVER}</PreBlock>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-warning)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              5 architectural rules to prevent the bug class (15-ui-and-bug-analysis.md §B.7)
            </span>
          </div>
          <PreBlock>{PROXY_CHURN_ARCHITECTURAL_RULES}</PreBlock>
        </Card>
      </div>

      {/* ── 11. Notifications & Foreground Service ── */}
      <SectionHeader
        number={11}
        title="Notifications & Foreground Service (REVIEW-5 M20-M30 fixes)"
        subtitle="The CRITICAL finding (now CLOSED): old project had NO foreground service + the new draft's startForeground was NOT synchronous (ForegroundServiceDidNotStartInTimeException). The fixes: synchronous startForeground (M20), Coil 3 thumbnails (M21+M22), KoinComponent (M25), onTimeout/onTaskRemoved (M27+M28). Source: 06-notifications-foreground-service.md."
      />
      <div className="mb-10 space-y-4">
        <Card className="p-4">
          <div
            className="rounded-lg p-3"
            style={{
              backgroundColor: `color-mix(in srgb, ${NOTIFICATIONS_FOREGROUND_CALLOUT.color} 10%, transparent)`,
              borderLeft: `3px solid ${NOTIFICATIONS_FOREGROUND_CALLOUT.color}`,
            }}
          >
            <div className="mb-1.5 flex items-center gap-2">
              <span
                className="inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-widest text-white"
                style={{ backgroundColor: NOTIFICATIONS_FOREGROUND_CALLOUT.color }}
              >
                Critical
              </span>
              <h3 className="text-sm font-bold text-[var(--c-text-primary)]">{NOTIFICATIONS_FOREGROUND_CALLOUT.title}</h3>
            </div>
            <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">{NOTIFICATIONS_FOREGROUND_CALLOUT.body}</p>
          </div>
        </Card>

        <Card className="overflow-hidden p-0">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Old vs new project — notification + service plan
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-[var(--c-border)]">
                <tr className="bg-[var(--c-surface)]">
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Aspect</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Old project</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">New project</th>
                </tr>
              </thead>
              <tbody>
                {NOTIFICATION_PLAN.map((p) => (
                  <tr key={p.aspect} className="border-b border-[var(--c-border)] last:border-0 align-top">
                    <td className="px-4 py-2.5 font-bold text-[var(--c-text-primary)]">{p.aspect}</td>
                    <td className="px-4 py-2.5 text-[var(--c-text-secondary)]">{p.oldProject}</td>
                    <td className="px-4 py-2.5 text-[var(--c-text-primary)]">{p.newProject}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Notification constants (DownloadNotificationManager.kt:182-187)
            </span>
          </div>
          <PreBlock>{NOTIFICATION_CONSTANTS}</PreBlock>
        </Card>
      </div>

      {/* ── 12. Settings ── */}
      <SectionHeader
        number={12}
        title="Settings"
        subtitle="All 15 OLD download settings (general + auto-download + preference lists + fallback strategies + advanced). The NEW dimensionPriority + globalFallback are documented in §7 (Auto-Download Engine). Source: 07-settings-preferences.md."
      />
      <div className="mb-10 space-y-4">
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)]">
                <tr>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Group</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Key</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Type</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Default</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">UI label</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Description</th>
                </tr>
              </thead>
              <tbody>
                {SETTINGS.map((s) => (
                  <tr key={s.key} className="border-b border-[var(--c-border)] last:border-0 align-top">
                    <td className="px-4 py-2.5 text-[var(--c-text-secondary)]">{s.group}</td>
                    <td className="px-4 py-2.5">
                      <code className="font-bold text-[var(--c-primary)]">{s.key}</code>
                    </td>
                    <td className="px-4 py-2.5 font-mono text-[var(--c-text-secondary)]">{s.type}</td>
                    <td className="px-4 py-2.5 font-mono text-[var(--c-text-primary)]">{s.default}</td>
                    <td className="px-4 py-2.5 text-[var(--c-text-primary)]">{s.uiLabel}</td>
                    <td className="px-4 py-2.5 text-[var(--c-text-secondary)]">{s.description ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Enums (DownloadPreferences.kt:183-204)
            </span>
          </div>
          <PreBlock>{ENUMS_REFERENCE}</PreBlock>
        </Card>
      </div>

      {/* ── 13. Downloads Page UI ── */}
      <SectionHeader
        number={13}
        title="Downloads Page UI"
        subtitle="Two separate pages (live queue + downloaded library) + settings + picker sheet + components. Source: 08-downloads-page-ui.md."
      />
      <div className="mb-10 space-y-3">
        {DOWNLOADS_PAGE_UI.map((section) => (
          <Card key={section.name} className="p-4 sm:p-5">
            <div className="mb-2 flex items-center gap-2">
              <StatusDot color={section.color} size="md" />
              <h3 className="text-sm font-bold leading-tight text-[var(--c-text-primary)]">{section.name}</h3>
            </div>
            <p className="mb-3 text-xs leading-relaxed text-[var(--c-text-secondary)]">{section.description}</p>
            <ul className="space-y-1.5">
              {section.details.map((d, i) => (
                <li key={i} className="flex gap-2 text-xs text-[var(--c-text-secondary)]">
                  <span style={{ color: section.color }}>▸</span>
                  <span>{d}</span>
                </li>
              ))}
            </ul>
          </Card>
        ))}
      </div>

      {/* ── 14. Details Page Download Control ── */}
      <SectionHeader
        number={14}
        title="Details Page Download Control"
        subtitle="Per-episode UI on the anime-details page. 7-state sealed interface (EpisodeDownloadState — now includes Retrying(attempt, maxAttempts, lastError) per M13) + the host mapping. Source: 09-details-page-download-ui.md."
      />
      <div className="mb-10 space-y-4">
        <Card className="overflow-hidden p-0">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              The 7 EpisodeDownloadState variants (EpisodeDownloadControl.kt:49-164)
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-[var(--c-border)]">
                <tr className="bg-[var(--c-surface)]">
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">State</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Visual</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Action</th>
                </tr>
              </thead>
              <tbody>
                {EPISODE_DOWNLOAD_STATES.map((s) => (
                  <tr key={s.state} className="border-b border-[var(--c-border)] last:border-0 align-top">
                    <td className="px-4 py-2.5">
                      <code className="font-bold" style={{ color: s.color }}>
                        {s.state}
                      </code>
                    </td>
                    <td className="px-4 py-2.5 font-mono text-[var(--c-text-secondary)]">{s.visual}</td>
                    <td className="px-4 py-2.5 text-[var(--c-text-secondary)]">{s.action}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>

        <div className="grid gap-3 sm:grid-cols-2">
          {DETAILS_PAGE_NOTES.map((n) => (
            <Card key={n.title} className="p-4">
              <div className="mb-1.5 flex items-center gap-2">
                <StatusDot color={n.color} size="md" />
                <h3 className="text-sm font-bold leading-tight text-[var(--c-text-primary)]">{n.title}</h3>
              </div>
              <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">{n.body}</p>
            </Card>
          ))}
        </div>
      </div>

      {/* ── 15. Player Integration ── */}
      <SectionHeader
        number={15}
        title="Player Integration"
        subtitle="Offline playback — the offline short-circuit, MPV content:// handling, episode switching, watch progress. Source: 10-player-integration.md."
      />
      <div className="mb-10 space-y-4">
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Offline short-circuit flow (AppController.kt:871-920)
            </span>
          </div>
          <PreBlock>{PLAYER_INTEGRATION_DIAGRAM}</PreBlock>
        </Card>

        <div className="grid gap-3 sm:grid-cols-2">
          {PLAYER_INTEGRATION_NOTES.map((n) => (
            <Card key={n.title} className="p-4">
              <div className="mb-1.5 flex items-center gap-2">
                <StatusDot color={n.color} size="md" />
                <h3 className="text-sm font-bold leading-tight text-[var(--c-text-primary)]">{n.title}</h3>
              </div>
              <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">{n.body}</p>
            </Card>
          ))}
        </div>
      </div>

      {/* ── 16. Database Schema (REVIEW-5 M1+M2 — direct .sq edit, NO .sqm migration) ── */}
      <SectionHeader
        number={16}
        title="Database Schema (REVIEW-5 M1+M2 — direct .sq edit, NO .sqm migration)"
        subtitle="The new SQLDelight tables (replacing JSON-in-SharedPrefs). Decision D1: Option B1 (separate columns). REVIEW-5 M1+M2: edit the .sq files DIRECTLY — do NOT add a 3.sqm migration file (the project has ZERO .sqm files). Source: 11-db-schema.md + 13-implementation-plan.md."
      />
      <div className="mb-10 space-y-4">
        <Card className="p-4">
          <div className="mb-2 flex items-center gap-2">
            <span
              className="rounded-md px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-white"
              style={{ backgroundColor: DB_SCHEMA_DECISION.color }}
            >
              Decision D1
            </span>
            <h3 className="text-sm font-bold text-[var(--c-text-primary)]">{DB_SCHEMA_DECISION.title}</h3>
          </div>
          <p className="mb-2 text-xs font-medium text-[var(--c-text-primary)]">
            <span className="font-bold">Recommendation:</span> {DB_SCHEMA_DECISION.recommendation}
          </p>
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <div className="mb-1 text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
                Old project
              </div>
              <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">{DB_SCHEMA_DECISION.oldProject}</p>
            </div>
            <div>
              <div className="mb-1 text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
                New project
              </div>
              <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">{DB_SCHEMA_DECISION.newProject}</p>
            </div>
          </div>
        </Card>

        {DB_SCHEMA_TABLES.map((t) => (
          <Card key={t.name} className="p-0 overflow-hidden">
            <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex flex-wrap items-center gap-2">
              <code className="text-sm font-bold text-[var(--c-text-primary)]">{t.name}</code>
              {t.isNew ? (
                <span className="rounded-md bg-[var(--c-success)] px-2 py-0.5 text-[10px] font-bold uppercase text-white">
                  Proposed
                </span>
              ) : (
                <span className="rounded-md bg-[var(--c-warning)] px-2 py-0.5 text-[10px] font-bold uppercase text-white">
                  Existing
                </span>
              )}
            </div>
            <div className="border-b border-[var(--c-border)] bg-[var(--c-surface)] px-4 py-2">
              <p className="text-xs text-[var(--c-text-secondary)]">{t.purpose}</p>
            </div>
            <PreBlock>{t.schema}</PreBlock>
          </Card>
        ))}

        <Card className="p-4">
          <div className="flex items-start gap-2">
            <span style={{ color: "var(--c-secondary)" }}>ⓘ</span>
            <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">
              {DB_OLD_PROJECT_NO_DOWNLOAD_TABLES}
            </p>
          </div>
        </Card>
      </div>

      {/* ── 17. DI Wiring ── */}
      <SectionHeader
        number={17}
        title="DI Wiring"
        subtitle="Three Koin modules (downloadModule in :core:download, downloadFeatureModule in :feature:download, downloadAppModule in :app). Source: 12-di-wiring.md."
      />
      <div className="mb-10 space-y-4">
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Complete Koin graph for downloads (12-di-wiring.md §7)
            </span>
          </div>
          <PreBlock>{DI_GRAPH}</PreBlock>
        </Card>

        {DI_MODULES.map((m) => (
          <Card key={m.module} className="p-0 overflow-hidden">
            <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex flex-wrap items-center gap-2">
              <StatusDot color={m.color} size="md" />
              <code className="text-sm font-bold text-[var(--c-text-primary)]">{m.module}</code>
              <span className="text-xs text-[var(--c-text-secondary)]">— {m.file}</span>
            </div>
            <div className="px-4 py-2 border-b border-[var(--c-border)] bg-[var(--c-surface)]">
              <p className="text-xs text-[var(--c-text-secondary)]">
                <span className="font-bold">Provides:</span> {m.provides}
              </p>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="border-b border-[var(--c-border)]">
                  <tr className="bg-[var(--c-surface)]">
                    <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Binding</th>
                    <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Scope</th>
                    <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Qualifier</th>
                    <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Notes</th>
                  </tr>
                </thead>
                <tbody>
                  {m.bindings.map((b) => (
                    <tr key={b.name + b.qualifier} className="border-b border-[var(--c-border)] last:border-0 align-top">
                      <td className="px-4 py-2.5">
                        <code className="font-bold text-[var(--c-primary)]">{b.name}</code>
                      </td>
                      <td className="px-4 py-2.5 font-mono text-[var(--c-text-secondary)]">{b.scope}</td>
                      <td className="px-4 py-2.5 font-mono text-[var(--c-text-secondary)]">{b.qualifier}</td>
                      <td className="px-4 py-2.5 text-[var(--c-text-secondary)]">{b.notes}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        ))}
      </div>

      {/* ── 18. Implementation Phases (UPDATED — D.0→D.8, 30-40 days) ── */}
      <SectionHeader
        number={18}
        title="Implementation Phases (UPDATED — 9 phases D.0→D.8, 30-40 days)"
        subtitle="9 phases (D.0–D.8) totaling 30-40 days (was 23-30 — grew by the REVIEW-5 consolidation pass + REVIEW-6 re-review + inevitable mid-implementation discoveries). D.0 (foundations) is foundational, D.1 (engine + NEW data.json storage) is the biggest. Source: 13-implementation-plan.md §5 + §6.1 Review Findings."
      />
      <div className="mb-10 space-y-3">
        {IMPLEMENTATION_PHASES.map((p) => (
          <Card key={p.id} className="p-4 sm:p-5">
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <span
                className="rounded-md px-2 py-0.5 text-xs font-bold text-white"
                style={{ backgroundColor: p.color }}
              >
                {p.id}
              </span>
              <h3 className="text-sm font-bold text-[var(--c-text-primary)]">{p.title}</h3>
              <span className="rounded-md bg-[var(--c-surface-alt)] px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-[var(--c-text-secondary)]">
                {p.status}
              </span>
              <span className="ml-auto text-xs font-medium text-[var(--c-text-secondary)]">{p.days}</span>
            </div>
            <p className="mb-3 text-xs leading-relaxed text-[var(--c-text-secondary)]">
              <span className="font-bold">Goal:</span> {p.goal}
            </p>
            <div className="mb-2 text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Tasks
            </div>
            <ul className="space-y-1.5">
              {p.tasks.map((t, i) => (
                <li key={i} className="flex gap-2 text-xs leading-relaxed text-[var(--c-text-secondary)]">
                  <span style={{ color: p.color }}>▸</span>
                  <span>{t}</span>
                </li>
              ))}
            </ul>
          </Card>
        ))}
        <Card className="p-4">
          <div className="flex items-center gap-2">
            <StatusDot color="var(--c-success)" size="md" />
            <p className="text-xs font-medium text-[var(--c-text-primary)]">{IMPLEMENTATION_TOTAL_ESTIMATE}</p>
          </div>
        </Card>
      </div>

      {/* ── 19. Quality of Life (NEW) ── */}
      <SectionHeader
        number={19}
        title="Quality of Life (NEW — auto-retry + auto-resume + auto-pause + orphan cleanup)"
        subtitle="The QoL features from 16-quality-of-life.md — the headline auto-retry (with RETRYING state M9), auto-resume on network change (M42), auto-pause on metered network, download verification, orphan-file cleanup, auto-clear completed entries after 10s."
      />
      <div className="mb-10 space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          {QOL_FEATURES.map((q) => (
            <Card key={q.id} className="p-4">
              <div className="mb-2 flex flex-wrap items-center gap-2">
                <span
                  className="rounded-md px-2 py-0.5 text-[10px] font-bold text-white"
                  style={{ backgroundColor: q.color }}
                >
                  {q.id}
                </span>
                <h3 className="text-sm font-bold leading-tight text-[var(--c-text-primary)]">{q.title}</h3>
              </div>
              <p className="mb-2 text-xs font-medium leading-relaxed text-[var(--c-text-primary)]">{q.headline}</p>
              <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">{q.details}</p>
            </Card>
          ))}
        </div>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)] px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-danger)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              The retry policy table (16-quality-of-life.md §1.1 — M48 type matching, M49 HttpException, M50 dead-branch removed)
            </span>
          </div>
          <PreBlock>{QOL_RETRY_POLICY_TABLE}</PreBlock>
        </Card>
      </div>

      {/* ── 20. Design Decisions ── */}
      <SectionHeader
        number={20}
        title="Design Decisions"
        subtitle="7 confirmed design decisions (D1–D7) covering persistence, storage, foreground service, reactive prefs, episode-key format, HLS support, and the Advanced method. Source: 13-implementation-plan.md §4."
      />
      <div className="mb-10 space-y-3">
        {DESIGN_DECISIONS.map((d) => (
          <DecisionCard key={d.id} decision={d} />
        ))}
      </div>

      {/* ── 21. Risks ── */}
      <SectionHeader
        number={21}
        title="Risks"
        subtitle="8-entry risk register with likelihood + mitigation. Source: 13-implementation-plan.md §8."
      />
      <div className="mb-10">
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-[var(--c-border)] bg-[var(--c-surface-alt)]">
                <tr>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Risk</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Likelihood</th>
                  <th className="px-4 py-2 font-bold text-[var(--c-text-primary)]">Mitigation</th>
                </tr>
              </thead>
              <tbody>
                {RISKS.map((r, i) => (
                  <tr key={i} className="border-b border-[var(--c-border)] last:border-0 align-top">
                    <td className="px-4 py-2.5 text-[var(--c-text-primary)]">{r.risk}</td>
                    <td className="px-4 py-2.5">
                      <span
                        className="rounded-md px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-white"
                        style={{
                          backgroundColor:
                            r.likelihood === "High"
                              ? "var(--c-danger)"
                              : r.likelihood === "Medium"
                                ? "var(--c-warning)"
                                : "var(--c-success)",
                        }}
                      >
                        {r.likelihood}
                      </span>
                    </td>
                    <td className="px-4 py-2.5 text-[var(--c-text-secondary)]">{r.mitigation}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      </div>

      {/* ── 22. Old-Project Bugs to Avoid ── */}
      <SectionHeader
        number={22}
        title="Old-Project Bugs to Avoid"
        subtitle="8 bugs / TODOs found in the old code that the new implementation must avoid. Source: 00-overview.md §6."
      />
      <div className="mb-10 grid gap-3 sm:grid-cols-2">
        {OLD_PROJECT_BUGS.map((b) => (
          <Card key={b.title} className="p-4">
            <div className="mb-2 flex items-start gap-2">
              <span style={{ color: b.color }}>⚠</span>
              <h3 className="text-sm font-bold leading-tight text-[var(--c-text-primary)]">{b.title}</h3>
            </div>
            <p className="mb-2 text-xs leading-relaxed text-[var(--c-text-secondary)]">{b.body}</p>
            <div
              className="rounded-md px-2.5 py-1.5 text-xs text-[var(--c-text-secondary)]"
              style={{ backgroundColor: `color-mix(in srgb, ${b.color} 8%, var(--c-surface-alt))` }}
            >
              <span className="font-bold">Fix in new project:</span> {b.fixInNewProject}
            </div>
          </Card>
        ))}
      </div>

      {/* ── 19. Footer nav ── */}
      <div className="mt-12 flex justify-between border-t border-[var(--c-border)] pt-6">
        <Link
          href={DOWNLOADS_PLAN_NAV_FOOTER.prev.href}
          className="text-xs text-[var(--c-text-secondary)] hover:text-[var(--c-primary)]"
        >
          {DOWNLOADS_PLAN_NAV_FOOTER.prev.label}
        </Link>
        <Link
          href={DOWNLOADS_PLAN_NAV_FOOTER.next.href}
          className="text-xs text-[var(--c-text-secondary)] hover:text-[var(--c-primary)]"
        >
          {DOWNLOADS_PLAN_NAV_FOOTER.next.label}
        </Link>
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * SectionHeader — numbered section header (matches phase-d pattern).
 * ------------------------------------------------------------------------- */
function SectionHeader({
  number,
  title,
  subtitle,
  critical = false,
}: {
  number: number;
  title: string;
  subtitle?: string;
  critical?: boolean;
}) {
  return (
    <section className="mb-5">
      <div className="flex items-center gap-2.5 mb-1.5">
        <span
          className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-xs font-bold text-white"
          style={{ backgroundColor: critical ? "var(--c-danger)" : "var(--c-primary)" }}
        >
          {number}
        </span>
        <h2 className="text-xl font-bold tracking-tight text-[var(--c-text-primary)] sm:text-2xl">{title}</h2>
        {critical && (
          <span className="rounded-md bg-[var(--c-danger)] px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-white">
            Critical
          </span>
        )}
      </div>
      {subtitle && <p className="ml-9 text-sm text-[var(--c-text-secondary)]">{subtitle}</p>}
    </section>
  );
}

/* ---------------------------------------------------------------------------
 * PreBlock — <pre> with horizontal scroll on mobile, dark surface styling.
 * ------------------------------------------------------------------------- */
function PreBlock({
  children,
  compact = false,
  className = "",
}: {
  children: React.ReactNode;
  compact?: boolean;
  className?: string;
}) {
  return (
    <pre
      className={`overflow-x-auto font-mono text-[11px] leading-relaxed text-[var(--c-text-primary)] bg-[var(--c-surface)] ${
        compact ? "px-3 py-2" : "p-4"
      } ${className}`}
    >
      {children}
    </pre>
  );
}

/* ---------------------------------------------------------------------------
 * DecisionCard — expandable card for a DesignDecision.
 * ------------------------------------------------------------------------- */
function DecisionCard({ decision }: { decision: (typeof DESIGN_DECISIONS)[number] }) {
  const [open, setOpen] = useState(false);
  return (
    <Card className="p-0 overflow-hidden">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-start gap-3 px-4 py-3 text-left hover:bg-[var(--c-surface-alt)] transition-colors"
      >
        <span className="rounded-md bg-[var(--c-primary)] px-2 py-0.5 text-[10px] font-bold text-white shrink-0">
          {decision.id}
        </span>
        <div className="min-w-0 flex-1">
          <div className="text-sm font-bold text-[var(--c-text-primary)]">{decision.question}</div>
          <div className="mt-0.5 text-xs text-[var(--c-secondary)] font-medium">{decision.recommendation}</div>
        </div>
        <span className="text-xs text-[var(--c-text-secondary)] shrink-0">{open ? "▲" : "▼"}</span>
      </button>
      {open && (
        <div className="border-t border-[var(--c-border)] px-4 py-3 space-y-3">
          <div>
            <div className="mb-1.5 text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Options considered
            </div>
            <ul className="space-y-1">
              {decision.options.map((opt, i) => (
                <li key={i} className="flex gap-2 text-xs text-[var(--c-text-secondary)]">
                  <span className="font-mono text-[var(--c-text-secondary)]">{String.fromCharCode(65 + i)}.</span>
                  <span>{opt}</span>
                </li>
              ))}
            </ul>
          </div>
          <div>
            <div className="mb-1.5 text-[10px] font-bold uppercase tracking-widest text-[var(--c-text-secondary)]">
              Rationale
            </div>
            <p className="text-xs leading-relaxed text-[var(--c-text-secondary)]">{decision.rationale}</p>
          </div>
        </div>
      )}
    </Card>
  );
}
