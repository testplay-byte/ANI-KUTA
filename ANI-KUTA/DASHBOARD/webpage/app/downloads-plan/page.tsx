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
            14 research docs · 6 phases · 7 design decisions
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

      {/* ── 2. Architecture Overview ── */}
      <SectionHeader
        number={2}
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

      {/* ── 3. Workflow: Click → Queue ── */}
      <SectionHeader
        number={3}
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

      {/* ── 4. State Machine ── */}
      <SectionHeader
        number={4}
        title="State Machine"
        subtitle="6 states (QUEUED / DOWNLOADING / PAUSED / COMPLETED / ERROR / CANCELLED) + 13 transitions. Source: 03-state-machine.md."
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

      {/* ── 5. Storage Paths (CRITICAL) ── */}
      <SectionHeader
        number={5}
        title="Storage Paths"
        subtitle="CRITICAL — where files are saved, the folder structure, the naming convention, the SAF-vs-internal-cache decision, and the FileProvider config. Source: 04-storage-paths.md."
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
              Internal temp cache (TempDownloadCache.kt) — internal-cache-first pipeline
            </span>
          </div>
          <PreBlock>{STORAGE_TEMP_CACHE}</PreBlock>
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

      {/* ── 6. Download Engines ── */}
      <SectionHeader
        number={6}
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

      {/* ── 7. Queue Management ── */}
      <SectionHeader
        number={7}
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

      {/* ── 8. Notifications & Foreground Service ── */}
      <SectionHeader
        number={8}
        title="Notifications & Foreground Service"
        subtitle="The plan + the CRITICAL finding: old project has NO foreground service; new project MUST add one. Source: 06-notifications-foreground-service.md."
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

      {/* ── 9. Settings ── */}
      <SectionHeader
        number={9}
        title="Settings"
        subtitle="All 15 download settings (general + auto-download + preference lists + fallback strategies + advanced). Source: 07-settings-preferences.md."
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

      {/* ── 10. Downloads Page UI ── */}
      <SectionHeader
        number={10}
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

      {/* ── 11. Details Page Download Control ── */}
      <SectionHeader
        number={11}
        title="Details Page Download Control"
        subtitle="Per-episode UI on the anime-details page. 7-state sealed interface (EpisodeDownloadState) + the host mapping. Source: 09-details-page-download-ui.md."
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

      {/* ── 12. Player Integration ── */}
      <SectionHeader
        number={12}
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

      {/* ── 13. Database Schema ── */}
      <SectionHeader
        number={13}
        title="Database Schema"
        subtitle="The new SQLDelight tables (replacing JSON-in-SharedPrefs). Decision D1: Option B1 (separate columns). Source: 11-db-schema.md + 13-implementation-plan.md."
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

      {/* ── 14. DI Wiring ── */}
      <SectionHeader
        number={14}
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

      {/* ── 15. Implementation Phases ── */}
      <SectionHeader
        number={15}
        title="Implementation Phases"
        subtitle="6 phases (D.0–D.6) totaling 12–18 days. Each builds on the previous — D.0 (foundations) is foundational, D.1 (engine) is the biggest. Source: 13-implementation-plan.md §5."
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

      {/* ── 16. Design Decisions ── */}
      <SectionHeader
        number={16}
        title="Design Decisions"
        subtitle="7 confirmed design decisions (D1–D7) covering persistence, storage, foreground service, reactive prefs, episode-key format, HLS support, and the Advanced method. Source: 13-implementation-plan.md §4."
      />
      <div className="mb-10 space-y-3">
        {DESIGN_DECISIONS.map((d) => (
          <DecisionCard key={d.id} decision={d} />
        ))}
      </div>

      {/* ── 17. Risks ── */}
      <SectionHeader
        number={17}
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

      {/* ── 18. Old-Project Bugs to Avoid ── */}
      <SectionHeader
        number={18}
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
