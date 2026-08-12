"use client";

import Link from "next/link";
import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import {
  UPDATES_HERO,
  VISION_SUMMARY,
  ARCH_BOXES,
  INTERFACE_PATTERN,
  CURRENT_STATE,
  ARCH_DECISIONS,
  SCHEMA_ANIME_UPDATE_STATE_SQL,
  SCHEMA_ANIME_UPDATE_STATE_NOTES,
  SCHEMA_NEW_QUERY_DUB,
  SCHEMA_NEW_INDEX_DUB,
  SCHEMA_EPISODE_UPDATE_SQL,
  SCHEMA_EPISODE_UPDATE_NOTES,
  SCHEMA_INDEX_EPISODE_UPDATE,
  SCHEMA_QUERY_UPDATES,
  SCHEMA_MIGRATION_NOTE,
  SETTINGS_TREE,
  SETTINGS_GENERAL_ITEMS,
  TOGGLE_FIX_ROOT_CAUSE,
  TOGGLE_FIX_SOLUTION,
  STATE_MIGRATION_NOTES,
  AUTO_UPDATE_INTERVAL_NOTES,
  WORKER_FLOW_CODE,
  MANUAL_MODE_NOTES,
  CHECK_PROGRESS_CODE,
  LIVE_PROGRESS_NOTES,
  SMART_RELEASE_CHAIN,
  SMART_RELEASE_NOTES,
  SUBDUB_DETECTION_NOTES,
  CHECK_SINGLE_ANIME_CODE,
  NOTIFICATION_AUDIO_FILTER,
  NOTIFICATION_TRIGGERS,
  NOTIFICATION_CONTENT,
  EXTENSION_ONLY_NOTES,
  TRIGGER_COMPARISON_INTRO,
  TRIGGER_COMPARISON,
  TRIGGER_TIMELINE_NOTE,
  TEST_NOTIFICATION_NOTES,
  DEDUP_RETENTION_NOTES,
  IMPLEMENTATION_PHASES,
  IMPLEMENTATION_TOTAL_ESTIMATE,
  USER_DECISIONS,
  USER_DECISIONS_ADDITIONAL,
  SETTINGS_TABLES,
  SETTINGS_TABLES_INTRO,
  FUTURE_PROOFING,
  UPDATES_PLAN_NAV_FOOTER,
  type BuildStatus,
  type SettingsTreeNode,
  type SmartReleaseStep,
  type TriggerComparison,
  type UserDecision,
  type SettingsTableSection,
} from "@/lib/updatesPlan";

/* ---------------------------------------------------------------------------
 * Page — Updates + Notifications Architecture Plan (D-193)
 * ------------------------------------------------------------------------- */
export default function UpdatesNotificationsPlanPage() {
  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
      {/* ── Sticky mini-header (in-page TOC + status) ── */}
      <StickyMiniHeader />

      {/* ── 1. Hero ── */}
      <section className="mb-10 mt-6">
        <div className="flex flex-wrap items-center gap-3 mb-3">
          <span
            className="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold uppercase tracking-widest"
            style={{
              backgroundColor: `color-mix(in srgb, ${UPDATES_HERO.statusColor} 15%, transparent)`,
              color: UPDATES_HERO.statusColor,
            }}
          >
            <span
              className="inline-block h-1.5 w-1.5 rounded-full"
              style={{ backgroundColor: UPDATES_HERO.statusColor }}
            />
            {UPDATES_HERO.status}
          </span>
          <span className="font-mono text-[11px] text-text-secondary">
            branch: <span className="text-text-primary">{UPDATES_HERO.branch}</span>
          </span>
          <span className="text-[11px] text-text-secondary">·</span>
          <span className="text-[11px] text-text-secondary">{UPDATES_HERO.date}</span>
          <span className="text-[11px] text-text-secondary">·</span>
          <span className="text-[11px] text-text-secondary">
            ~{UPDATES_HERO.totalHours}h across 10 phases
          </span>
        </div>
        <h1 className="text-3xl font-bold tracking-tight text-text-primary sm:text-4xl">
          {UPDATES_HERO.title}
        </h1>
        <p className="mt-3 text-base text-text-secondary sm:text-lg">{UPDATES_HERO.subtitle}</p>
        <p className="mt-4 max-w-3xl text-sm leading-relaxed text-text-secondary">
          {UPDATES_HERO.reviews}
        </p>
      </section>

      {/* ── 2. Vision ── */}
      <SectionHeader
        number={2}
        title="Vision"
        subtitle="What the system does — interlinked Updates + Notifications communicating via interface contracts."
      />
      <div className="mb-10 space-y-4">
        <Card className="p-5">
          <p className="text-sm leading-relaxed text-text-primary">{VISION_SUMMARY.lede}</p>
        </Card>
        <div className="grid gap-3 sm:grid-cols-3">
          {VISION_SUMMARY.principles.map((p) => (
            <Card key={p.title} className="p-4">
              <div className="mb-1.5 flex items-center gap-2">
                <StatusDot color="var(--c-primary)" size="md" />
                <h3 className="text-[13px] font-bold text-text-primary">{p.title}</h3>
              </div>
              <p className="text-xs leading-relaxed text-text-secondary">{p.body}</p>
            </Card>
          ))}
        </div>
      </div>

      {/* ── 3. Architecture Diagram ── */}
      <SectionHeader
        number={3}
        title="Architecture — Interlinked System"
        subtitle="Settings → WorkManager → Updates Feed. The three layers communicate via the ScheduleRefresher + NotificationSender interfaces (bound in :app via Koin) to avoid circular deps."
      />
      <div className="mb-10 space-y-4">
        <Card className="p-4 sm:p-5">
          <div className="space-y-3">
            {ARCH_BOXES.map((box, idx) => (
              <div key={box.id}>
                <ArchBoxRender box={box} />
                {idx < ARCH_BOXES.length - 1 && <ArrowDown />}
              </div>
            ))}
          </div>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-secondary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              Interface pattern — avoids circular deps
            </span>
          </div>
          <PreBlock>{INTERFACE_PATTERN}</PreBlock>
        </Card>
      </div>

      {/* ── 4. Current State table ── */}
      <SectionHeader
        number={4}
        title="Current State"
        subtitle="What's already built vs what's missing. The system is mostly scaffolded — the plan is wiring + bug fixes, not greenfield."
      />
      <div className="mb-10">
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-border bg-surface-alt">
                <tr>
                  <th className="px-4 py-2 font-bold text-text-primary">Component</th>
                  <th className="px-4 py-2 font-bold text-text-primary">Status</th>
                  <th className="px-4 py-2 font-bold text-text-primary">What&apos;s there</th>
                  <th className="px-4 py-2 font-bold text-text-primary">What&apos;s missing</th>
                </tr>
              </thead>
              <tbody>
                {CURRENT_STATE.map((r) => (
                  <tr key={r.component} className="border-b border-border last:border-0 align-top">
                    <td className="px-4 py-2.5">
                      <code className="font-bold text-primary">{r.component}</code>
                    </td>
                    <td className="px-4 py-2.5">
                      <StatusBadge status={r.status} />
                    </td>
                    <td className="px-4 py-2.5 text-text-secondary">{r.there}</td>
                    <td className="px-4 py-2.5 text-text-secondary">{r.missing}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      </div>

      {/* ── 5. Known Architectural Decisions ── */}
      <SectionHeader
        number={5}
        title="Known Architectural Decisions"
        subtitle="12 blocking items surfaced across 5 review sessions (architecture · smart-release · settings UI · DB schema · final consolidated) — every one now has a resolution in this v2 plan."
        critical
      />
      <div className="mb-10 grid gap-3 sm:grid-cols-2">
        {ARCH_DECISIONS.map((d) => (
          <Card key={d.num} className="p-4">
            <div className="mb-2 flex items-center gap-2">
              <span
                className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md text-[11px] font-bold text-white"
                style={{ backgroundColor: "var(--c-success)" }}
              >
                {d.num}
              </span>
              <span className="rounded-md bg-surface-alt px-2 py-0.5 text-[9.5px] font-bold uppercase tracking-wider text-text-secondary">
                Resolved by review
              </span>
            </div>
            <h3 className="mb-1.5 text-[13px] font-bold leading-tight text-text-primary">
              {d.issue}
            </h3>
            <p className="text-xs leading-relaxed text-text-secondary">{d.resolution}</p>
          </Card>
        ))}
      </div>

      {/* ── 6. DB Schema Changes ── */}
      <SectionHeader
        number={6}
        title="DB Schema Changes"
        subtitle="5 new columns + 4 query updates + 1 new query + 2 indexes. All ALTER TABLEs go through the established hasColumn-guarded migration pattern (idempotent)."
      />
      <div className="mb-10 space-y-4">
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-primary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              4a · anime_update_state — add dub tracking + total_episodes (3 columns)
            </span>
          </div>
          <PreBlock>{SCHEMA_ANIME_UPDATE_STATE_SQL}</PreBlock>
          <div className="border-t border-border bg-surface px-4 py-3 space-y-1">
            {SCHEMA_ANIME_UPDATE_STATE_NOTES.map((n, i) => (
              <p key={i} className="text-xs leading-relaxed text-text-secondary">
                <span className="font-mono text-text-primary">•</span> {n}
              </p>
            ))}
          </div>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-secondary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              4a · New query — getDueDubAnime (for dub checking on FINISHED anime)
            </span>
          </div>
          <PreBlock>{SCHEMA_NEW_QUERY_DUB}</PreBlock>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-secondary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              4a · New index — idx_anime_update_due_dub (partial)
            </span>
          </div>
          <PreBlock>{SCHEMA_NEW_INDEX_DUB}</PreBlock>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-primary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              4b · episode_update — add &quot;new&quot; expiry (1 column)
            </span>
          </div>
          <PreBlock>{SCHEMA_EPISODE_UPDATE_SQL}</PreBlock>
          <div className="border-t border-border bg-surface px-4 py-3 space-y-1">
            {SCHEMA_EPISODE_UPDATE_NOTES.map((n, i) => (
              <p key={i} className="text-xs leading-relaxed text-text-secondary">
                <span className="font-mono text-text-primary">•</span> {n}
              </p>
            ))}
          </div>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-warning)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              4b · Index update — drop + recreate idx_episode_update_unack
            </span>
          </div>
          <PreBlock>{SCHEMA_INDEX_EPISODE_UPDATE}</PreBlock>
        </Card>

        <Card className="overflow-hidden p-0">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              4 · 4 existing queries to update
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-border">
                <tr className="bg-surface">
                  <th className="px-4 py-2 font-bold text-text-primary">Query</th>
                  <th className="px-4 py-2 font-bold text-text-primary">Change</th>
                </tr>
              </thead>
              <tbody>
                {SCHEMA_QUERY_UPDATES.map((q) => (
                  <tr key={q.query} className="border-b border-border last:border-0 align-top">
                    <td className="px-4 py-2.5">
                      <code className="font-bold text-primary">{q.query}</code>
                    </td>
                    <td className="px-4 py-2.5 text-text-secondary">{q.change}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>

        <Card className="p-4">
          <div
            className="rounded-lg p-3"
            style={{
              backgroundColor: "color-mix(in srgb, var(--c-secondary) 8%, transparent)",
              borderLeft: "3px solid var(--c-secondary)",
            }}
          >
            <div className="mb-1.5 flex items-center gap-2">
              <span className="inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-widest text-white bg-[var(--c-secondary)]">
                4c · Migration wiring
              </span>
            </div>
            <p className="text-xs leading-relaxed text-text-secondary">{SCHEMA_MIGRATION_NOTE}</p>
          </div>
        </Card>
      </div>

      {/* ── 7. Settings UI ── */}
      <SectionHeader
        number={7}
        title="Settings UI Redesign"
        subtitle="Combined Updates & Notifications section + the 3-way toggle bug fix + the masterEnabled → update_mode state migration."
      />
      <div className="mb-10 space-y-4">
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-primary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              5a · Combined section structure (tree view)
            </span>
          </div>
          <div className="p-4">
            <SettingsTreeRenderer nodes={SETTINGS_TREE} />
          </div>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              5b · General screen — {SETTINGS_GENERAL_ITEMS.length} items
            </span>
          </div>
          <div className="divide-y divide-border">
            {SETTINGS_GENERAL_ITEMS.map((g, i) => (
              <div key={i} className="px-4 py-3">
                <h3 className="text-[13px] font-bold text-text-primary">{g.title}</h3>
                <p className="mt-1 text-xs leading-relaxed text-text-secondary">{g.body}</p>
              </div>
            ))}
          </div>
        </Card>

        <Card className="p-4">
          <div
            className="rounded-lg p-4"
            style={{
              backgroundColor: "color-mix(in srgb, var(--c-danger) 8%, transparent)",
              borderLeft: "3px solid var(--c-danger)",
            }}
          >
            <div className="mb-2 flex items-center gap-2">
              <span className="inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-widest text-white bg-[var(--c-danger)]">
                5c · 3-way toggle bug fix
              </span>
              <span className="text-[11px] font-bold text-text-primary">8 call sites · ~10 lines</span>
            </div>
            <div className="mb-3">
              <div className="mb-0.5 text-[10px] font-bold uppercase tracking-widest text-[var(--c-danger)]">
                Root cause
              </div>
              <p className="text-xs leading-relaxed text-text-secondary">{TOGGLE_FIX_ROOT_CAUSE}</p>
            </div>
            <div>
              <div className="mb-0.5 text-[10px] font-bold uppercase tracking-widest text-[var(--c-success)]">
                Fix
              </div>
              <p className="text-xs leading-relaxed text-text-secondary">{TOGGLE_FIX_SOLUTION}</p>
            </div>
          </div>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              5d · State migration — masterEnabled → update_mode
            </span>
          </div>
          <div className="p-4 space-y-1">
            {STATE_MIGRATION_NOTES.map((n, i) => (
              <p key={i} className="text-xs leading-relaxed text-text-secondary">
                <span className="font-mono text-text-primary">•</span> {n}
              </p>
            ))}
          </div>
        </Card>
      </div>

      {/* ── 8. Auto-Update System ── */}
      <SectionHeader
        number={8}
        title="Auto-Update System"
        subtitle="Configurable WorkManager interval (replaces the hard-coded 1h) + manual per-category mode + live-progress SharedFlow."
      />
      <div className="mb-10 space-y-4">
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              6a · Configurable WorkManager interval
            </span>
          </div>
          <div className="p-4 space-y-1">
            {AUTO_UPDATE_INTERVAL_NOTES.map((n, i) => (
              <p key={i} className="text-xs leading-relaxed text-text-secondary">
                <span className="font-mono text-text-primary">•</span> {n}
              </p>
            ))}
          </div>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-secondary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              6b · Worker flow (expanded)
            </span>
          </div>
          <PreBlock>{WORKER_FLOW_CODE}</PreBlock>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              6c · Manual mode (per-category)
            </span>
          </div>
          <div className="p-4 space-y-1">
            {MANUAL_MODE_NOTES.map((n, i) => (
              <p key={i} className="text-xs leading-relaxed text-text-secondary">
                <span className="font-mono text-text-primary">•</span> {n}
              </p>
            ))}
          </div>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-primary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              6d · Live-progress — CheckProgress (SharedFlow)
            </span>
          </div>
          <PreBlock>{CHECK_PROGRESS_CODE}</PreBlock>
          <div className="border-t border-border bg-surface px-4 py-3 space-y-1">
            {LIVE_PROGRESS_NOTES.map((n, i) => (
              <p key={i} className="text-xs leading-relaxed text-text-secondary">
                <span className="font-mono text-text-primary">•</span> {n}
              </p>
            ))}
          </div>
        </Card>
      </div>

      {/* ── 9. Smart Release Detection ── */}
      <SectionHeader
        number={9}
        title="Smart Release Detection"
        subtitle="OneTimeWorkRequest chaining with setInitialDelay. For each anime airing within ±1h: try at +10, +20, +30 min — then give up (skip-after-3). Includes the v3 completed-anime dub-checking logic."
      />
      <div className="mb-10 space-y-4">
        <Card className="p-5">
          <SmartReleaseChain steps={SMART_RELEASE_CHAIN} />
        </Card>

        <div className="grid gap-3 sm:grid-cols-2">
          {SMART_RELEASE_NOTES.map((n, i) => {
            const isCompletedDubNote =
              n.title.startsWith("Completed anime — dub checking") ||
              n.title.startsWith("Completed anime — disable logic");
            return (
              <Card key={i} className="p-4">
                <div className="mb-1.5 flex items-center gap-2">
                  <StatusDot
                    color={isCompletedDubNote ? "var(--c-primary)" : "var(--c-secondary)"}
                    size="md"
                  />
                  <h3 className="text-[13px] font-bold text-text-primary">{n.title}</h3>
                  {isCompletedDubNote && (
                    <span className="rounded-md bg-[var(--c-primary)] px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider text-white">
                      v3
                    </span>
                  )}
                </div>
                <p className="text-xs leading-relaxed text-text-secondary">{n.body}</p>
              </Card>
            );
          })}
        </div>
      </div>

      {/* ── 10. Sub/Dub Tracking ── */}
      <SectionHeader
        number={10}
        title="Sub/Dub Tracking"
        subtitle="The checkSingleAnime rewrite — partition by audio_variant, compute max-sub/max-dub separately, update both counts."
      />
      <div className="mb-10 space-y-4">
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              8a · Audio variant detection
            </span>
          </div>
          <div className="p-4 space-y-1">
            {SUBDUB_DETECTION_NOTES.map((n, i) => (
              <p key={i} className="text-xs leading-relaxed text-text-secondary">
                <span className="font-mono text-text-primary">•</span> {n}
              </p>
            ))}
          </div>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-primary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              8b · checkSingleAnime rewrite (variant-aware)
            </span>
          </div>
          <PreBlock>{CHECK_SINGLE_ANIME_CODE}</PreBlock>
        </Card>

        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              8c · Notification audio filtering
            </span>
          </div>
          <div className="p-4 space-y-1">
            {NOTIFICATION_AUDIO_FILTER.map((n, i) => (
              <p key={i} className="text-xs leading-relaxed text-text-secondary">
                <span className="font-mono text-text-primary">•</span> {n}
              </p>
            ))}
          </div>
        </Card>
      </div>

      {/* ── 11. Notification System ── */}
      <SectionHeader
        number={11}
        title="Notification System"
        subtitle="3 trigger types + the on_schedule vs on_watchable comparison (user asked for this) + content spec + extension-only handling + test notification + dedup/retention."
      />
      <div className="mb-10 space-y-4">
        <Card className="overflow-hidden p-0">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              9a · Three trigger types
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-border">
                <tr className="bg-surface">
                  <th className="px-4 py-2 font-bold text-text-primary">Trigger</th>
                  <th className="px-4 py-2 font-bold text-text-primary">When</th>
                  <th className="px-4 py-2 font-bold text-text-primary">Who fires it</th>
                  <th className="px-4 py-2 font-bold text-text-primary">Wiring</th>
                </tr>
              </thead>
              <tbody>
                {NOTIFICATION_TRIGGERS.map((t) => (
                  <tr key={t.trigger} className="border-b border-border last:border-0 align-top">
                    <td className="px-4 py-2.5">
                      <code className="font-bold text-primary">{t.trigger}</code>
                    </td>
                    <td className="px-4 py-2.5 text-text-secondary">{t.when}</td>
                    <td className="px-4 py-2.5 text-text-secondary">{t.whoFires}</td>
                    <td className="px-4 py-2.5 text-text-secondary">{t.wiring}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>

        {/* 9a · on_schedule vs on_watchable comparison (user-requested) */}
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-primary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              9a · on_schedule vs on_watchable — user-requested clarification
            </span>
            <span className="rounded-md bg-[var(--c-primary)] px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider text-white">
              v3
            </span>
          </div>
          <div className="p-4">
            <p className="text-xs leading-relaxed text-text-secondary mb-3">
              {TRIGGER_COMPARISON_INTRO}
            </p>
            <TriggerComparisonTable rows={TRIGGER_COMPARISON} />
            <div
              className="mt-4 rounded-lg p-3"
              style={{
                backgroundColor: "color-mix(in srgb, var(--c-secondary) 8%, transparent)",
                borderLeft: "3px solid var(--c-secondary)",
              }}
            >
              <div className="mb-0.5 text-[10px] font-bold uppercase tracking-widest text-[var(--c-secondary)]">
                Typical timeline
              </div>
              <p className="text-xs leading-relaxed text-text-secondary">{TRIGGER_TIMELINE_NOTE}</p>
            </div>
          </div>
        </Card>

        <Card className="overflow-hidden p-0">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              9b · Notification content
            </span>
          </div>
          <div className="divide-y divide-border">
            {NOTIFICATION_CONTENT.map((c, i) => (
              <div key={i} className="px-4 py-2.5 flex flex-col sm:flex-row sm:items-start gap-1 sm:gap-3">
                <span className="text-[11px] font-bold uppercase tracking-wider text-text-secondary sm:w-32 shrink-0">
                  {c.label}
                </span>
                <span className="text-xs leading-relaxed text-text-primary">{c.value}</span>
              </div>
            ))}
          </div>
        </Card>

        {/* 9b.1 · Extension-only anime handling */}
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-secondary)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              9b.1 · Extension-only anime handling (no AniList ID)
            </span>
            <span className="rounded-md bg-[var(--c-secondary)] px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider text-white">
              v3
            </span>
          </div>
          <div className="p-4 space-y-1">
            {EXTENSION_ONLY_NOTES.map((n, i) => (
              <p key={i} className="text-xs leading-relaxed text-text-secondary">
                <span className="font-mono text-text-primary">•</span> {n}
              </p>
            ))}
          </div>
        </Card>

        <div className="grid gap-3 sm:grid-cols-2">
          <Card className="p-0 overflow-hidden">
            <div className="border-b border-border bg-surface-alt px-4 py-2.5">
              <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
                9c · Test notification
              </span>
            </div>
            <div className="p-4 space-y-1">
              {TEST_NOTIFICATION_NOTES.map((n, i) => (
                <p key={i} className="text-xs leading-relaxed text-text-secondary">
                  <span className="font-mono text-text-primary">•</span> {n}
                </p>
              ))}
            </div>
          </Card>

          <Card className="p-0 overflow-hidden">
            <div className="border-b border-border bg-surface-alt px-4 py-2.5">
              <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
                9d · Dedup + retention
              </span>
            </div>
            <div className="p-4 space-y-1">
              {DEDUP_RETENTION_NOTES.map((n, i) => (
                <p key={i} className="text-xs leading-relaxed text-text-secondary">
                  <span className="font-mono text-text-primary">•</span> {n}
                </p>
              ))}
            </div>
          </Card>
        </div>
      </div>

      {/* ── 12. Implementation Phases ── */}
      <SectionHeader
        number={12}
        title="Implementation Phases"
        subtitle={`10 phases · total ~${IMPLEMENTATION_TOTAL_ESTIMATE}h (revised up from ~24h after the 5 reviews surfaced hidden work in DB schema + smart-release + notification wiring).`}
      />
      <div className="mb-10 space-y-3">
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-border bg-surface-alt">
                <tr>
                  <th className="px-4 py-2 font-bold text-text-primary">Phase</th>
                  <th className="px-4 py-2 font-bold text-text-primary">Task</th>
                  <th className="px-4 py-2 font-bold text-text-primary text-right">Est.</th>
                  <th className="px-4 py-2 font-bold text-text-primary w-32">Cumulative</th>
                </tr>
              </thead>
              <tbody>
                {IMPLEMENTATION_PHASES.map((p, i) => {
                  const cumulative = IMPLEMENTATION_PHASES.slice(0, i + 1).reduce(
                    (acc, x) => acc + x.hours,
                    0,
                  );
                  const cumPct = (cumulative / IMPLEMENTATION_TOTAL_ESTIMATE) * 100;
                  return (
                    <tr key={p.phase} className="border-b border-border last:border-0 align-top">
                      <td className="px-4 py-2.5">
                        <span
                          className="flex h-7 w-7 items-center justify-center rounded-md text-[11px] font-bold text-white"
                          style={{ backgroundColor: "var(--c-primary)" }}
                        >
                          {p.phase}
                        </span>
                      </td>
                      <td className="px-4 py-2.5 text-text-primary">{p.task}</td>
                      <td className="px-4 py-2.5 text-right font-mono font-bold text-text-primary">
                        ~{p.hours}h
                      </td>
                      <td className="px-4 py-2.5">
                        <div className="flex items-center gap-2">
                          <div className="flex-1 h-1.5 rounded-full bg-canvas overflow-hidden">
                            <div
                              className="h-full rounded-full bg-[var(--c-primary)]"
                              style={{ width: `${cumPct}%` }}
                            />
                          </div>
                          <span className="font-mono text-[10px] text-text-secondary tabular-nums shrink-0">
                            {cumulative}h
                          </span>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
              <tfoot>
                <tr className="bg-surface-alt">
                  <td colSpan={2} className="px-4 py-3 font-bold text-text-primary">
                    Total
                  </td>
                  <td className="px-4 py-3 text-right font-mono font-bold text-[var(--c-primary)]">
                    ~{IMPLEMENTATION_TOTAL_ESTIMATE}h
                  </td>
                  <td className="px-4 py-3" />
                </tr>
              </tfoot>
            </table>
          </div>
        </Card>
      </div>

      {/* ── 13. User Decisions (all 8 questions answered) ── */}
      <SectionHeader
        number={13}
        title="User Decisions (All Answered)"
        subtitle="All 8 open questions have been answered by the user in v3 — these are now DECISIONS, not questions. Each shows the user's answer with a ✅ badge."
        critical
      />
      <div className="mb-10 space-y-3">
        {USER_DECISIONS.map((d) => (
          <UserDecisionCard key={d.num} decision={d} />
        ))}

        {/* Additional user clarifications */}
        <Card className="p-0 overflow-hidden">
          <div className="border-b border-border bg-surface-alt px-4 py-2.5 flex items-center gap-2">
            <StatusDot color="var(--c-success)" size="md" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
              Additional user clarifications
            </span>
            <span className="rounded-md bg-[var(--c-success)] px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider text-white">
              v3
            </span>
          </div>
          <div className="divide-y divide-border">
            {USER_DECISIONS_ADDITIONAL.map((c, i) => (
              <div key={i} className="px-4 py-3">
                <h3 className="text-[13px] font-bold text-text-primary">{c.title}</h3>
                <p className="mt-1 text-xs leading-relaxed text-text-secondary">{c.body}</p>
              </div>
            ))}
          </div>
        </Card>
      </div>

      {/* ── 14. Future-Proofing ── */}
      <SectionHeader
        number={14}
        title="Future-Proofing"
        subtitle="The architecture is built to extend — multi-source, multi-content-type, configurable intervals, per-anime override, backup/restore, plus the user-requested future enhancements."
      />
      <div className="mb-10 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {FUTURE_PROOFING.map((f) => (
          <Card key={f.title} className="p-4">
            <div className="mb-1.5 flex items-center gap-2">
              <StatusDot color="var(--c-secondary)" size="md" />
              <h3 className="text-[13px] font-bold text-text-primary">{f.title}</h3>
            </div>
            <p className="text-xs leading-relaxed text-text-secondary">{f.body}</p>
          </Card>
        ))}
      </div>

      {/* ── 15. Settings That Will Be in the App (detailed list) ── */}
      <SectionHeader
        number={15}
        title="Settings That Will Be in the App"
        subtitle="Every setting that will ship in the Updates & Notifications section — with type, default, and description. The user explicitly asked for this — see §13 additional clarification #4."
        critical
      />
      <div className="mb-10 space-y-4">
        <Card className="p-4">
          <div
            className="rounded-lg p-3"
            style={{
              backgroundColor: "color-mix(in srgb, var(--c-primary) 8%, transparent)",
              borderLeft: "3px solid var(--c-primary)",
            }}
          >
            <p className="text-xs leading-relaxed text-text-secondary">{SETTINGS_TABLES_INTRO}</p>
          </div>
        </Card>

        {SETTINGS_TABLES.map((section) => (
          <SettingsTableRenderer key={section.id} section={section} />
        ))}
      </div>

      {/* ── Footer nav ── */}
      <div className="mt-12 flex justify-between border-t border-border pt-6">
        <Link
          href={UPDATES_PLAN_NAV_FOOTER.prev.href}
          className="text-xs text-text-secondary hover:text-primary"
        >
          {UPDATES_PLAN_NAV_FOOTER.prev.label}
        </Link>
        <Link
          href={UPDATES_PLAN_NAV_FOOTER.next.href}
          className="text-xs text-text-secondary hover:text-primary"
        >
          {UPDATES_PLAN_NAV_FOOTER.next.label}
        </Link>
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * StickyMiniHeader — sticky page-level mini-header (status + quick TOC).
 * Sticks to the top of the viewport when scrolling.
 * ------------------------------------------------------------------------- */
function StickyMiniHeader() {
  const sections = [
    { n: 3, label: "Architecture" },
    { n: 4, label: "Current State" },
    { n: 6, label: "DB Schema" },
    { n: 7, label: "Settings UI" },
    { n: 8, label: "Auto-Update" },
    { n: 9, label: "Smart Release" },
    { n: 11, label: "Notifications" },
    { n: 12, label: "Phases" },
    { n: 13, label: "Decisions" },
    { n: 15, label: "Settings List" },
  ];
  return (
    <div className="sticky top-0 z-30 -mx-4 sm:-mx-6 lg:-mx-10 px-4 sm:px-6 lg:px-10 py-2.5 bg-canvas/85 backdrop-blur-xl border-b border-border">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
        <span
          className="inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-widest"
          style={{
            backgroundColor: `color-mix(in srgb, ${UPDATES_HERO.statusColor} 15%, transparent)`,
            color: UPDATES_HERO.statusColor,
          }}
        >
          <span
            className="inline-block h-1.5 w-1.5 rounded-full"
            style={{ backgroundColor: UPDATES_HERO.statusColor }}
          />
          D-193
        </span>
        <span className="font-mono text-[11px] text-text-secondary hidden sm:inline">
          {UPDATES_HERO.branch}
        </span>
        <div className="ml-auto flex flex-wrap items-center gap-x-2 gap-y-1">
          {sections.map((s) => (
            <a
              key={s.n}
              href={`#sec-${s.n}`}
              className="text-[10.5px] font-medium text-text-secondary hover:text-primary transition-colors"
            >
              <span className="font-mono text-text-secondary/70">{s.n}.</span> {s.label}
            </a>
          ))}
          <span className="text-text-secondary/40 mx-1">·</span>
          <span className="text-[10.5px] font-mono text-text-secondary">
            ~{IMPLEMENTATION_TOTAL_ESTIMATE}h
          </span>
        </div>
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * SectionHeader — numbered section header (matches phase-d / downloads-plan pattern).
 * Wraps each section in an id="sec-N" so the sticky TOC can jump to it.
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
    <section className="mb-5 scroll-mt-16" id={`sec-${number}`}>
      <div className="flex items-center gap-2.5 mb-1.5">
        <span
          className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-xs font-bold text-white"
          style={{ backgroundColor: critical ? "var(--c-danger)" : "var(--c-primary)" }}
        >
          {number}
        </span>
        <h2 className="text-xl font-bold tracking-tight text-text-primary sm:text-2xl">{title}</h2>
        {critical && (
          <span className="rounded-md bg-[var(--c-danger)] px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-white">
            Needs review
          </span>
        )}
      </div>
      {subtitle && <p className="ml-9 text-sm text-text-secondary">{subtitle}</p>}
    </section>
  );
}

/* ---------------------------------------------------------------------------
 * PreBlock — <pre> with horizontal scroll on mobile, monospace + background.
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
      className={`overflow-x-auto font-mono text-[11px] leading-relaxed text-text-primary bg-surface ${
        compact ? "px-3 py-2" : "p-4"
      } ${className}`}
    >
      {children}
    </pre>
  );
}

/* ---------------------------------------------------------------------------
 * StatusBadge — for the Current State table.
 * ------------------------------------------------------------------------- */
function StatusBadge({ status }: { status: BuildStatus }) {
  const map: Record<BuildStatus, { label: string; color: string }> = {
    built: { label: "Built", color: "var(--c-success)" },
    missing: { label: "Missing", color: "var(--c-danger)" },
    buggy: { label: "Buggy", color: "var(--c-warning)" },
  };
  const m = map[status];
  return (
    <span
      className="inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider"
      style={{
        backgroundColor: `color-mix(in srgb, ${m.color} 15%, transparent)`,
        color: m.color,
      }}
    >
      <span className="inline-block h-1.5 w-1.5 rounded-full" style={{ backgroundColor: m.color }} />
      {m.label}
    </span>
  );
}

/* ---------------------------------------------------------------------------
 * ArchBoxRender — renders an architecture box (§3) as a styled HTML card
 * instead of raw ASCII. Boxes use accent-tinted backgrounds + borders.
 * ------------------------------------------------------------------------- */
function ArchBoxRender({
  box,
}: {
  box: (typeof ARCH_BOXES)[number];
}) {
  return (
    <div
      className="rounded-[14px] border p-4"
      style={{
        borderColor: `color-mix(in srgb, ${box.colorVar} 30%, var(--c-border))`,
        backgroundColor: `color-mix(in srgb, ${box.colorVar} 4%, var(--c-surface))`,
      }}
    >
      <div className="mb-2 flex items-center gap-2">
        <span
          className="inline-block h-2 w-2 rounded-full"
          style={{ backgroundColor: box.colorVar }}
        />
        <h3 className="text-[13px] font-bold tracking-wide text-text-primary">{box.label}</h3>
        {box.subtitle && (
          <span className="text-[10.5px] text-text-secondary">— {box.subtitle}</span>
        )}
      </div>
      <ul className="space-y-1">
        {box.items.map((item, i) => {
          const indent = item.indent ?? 0;
          return (
            <li
              key={i}
              className="text-xs leading-relaxed text-text-primary"
              style={{ paddingLeft: `${indent * 1.25}rem` }}
            >
              <span className="font-mono text-text-secondary/70">
                {indent === 0 ? "•" : indent === 1 ? "└" : "├"}
              </span>{" "}
              <span className="text-text-secondary">
                {item.text}
                {item.branch && (
                  <span className="ml-1 text-[10.5px] italic text-text-secondary/70">
                    {" "}
                    ({item.branch})
                  </span>
                )}
              </span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function ArrowDown() {
  return (
    <div className="flex justify-center py-1" aria-hidden="true">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="var(--c-text-secondary)"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="w-4 h-4 opacity-60"
      >
        <path d="M12 5v14M19 12l-7 7-7-7" />
      </svg>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * SettingsTreeRenderer — renders the combined-settings tree (§5a).
 * ------------------------------------------------------------------------- */
function SettingsTreeRenderer({ nodes }: { nodes: SettingsTreeNode[] }) {
  return (
    <div className="font-mono text-[12.5px] leading-[1.8] text-text-primary">
      <div>Settings</div>
      {nodes.map((node, i) => (
        <SettingsTreeBranch key={node.label + i} node={node} isLast={i === nodes.length - 1} />
      ))}
    </div>
  );
}

function SettingsTreeBranch({
  node,
  isLast,
}: {
  node: SettingsTreeNode;
  isLast: boolean;
}) {
  const glyph = isLast ? "└─ " : "├─ ";
  const childPrefix = isLast ? "   " : "│  ";
  const hasChildren = !!node.children?.length;
  const color = node.highlight ? "var(--c-primary)" : "var(--c-text-primary)";
  return (
    <div>
      <div className="flex items-start gap-1">
        <span className="text-text-secondary whitespace-pre">{glyph}</span>
        <span style={{ color }} className={node.highlight ? "font-bold" : "font-medium"}>
          {node.label}
        </span>
        {node.note && (
          <span className="text-text-secondary text-[11px] ml-2">
            ←{" "}
            {node.highlight ? (
              <span className="rounded-md bg-surface-alt px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider text-[var(--c-primary)]">
                {node.note}
              </span>
            ) : (
              <span className="italic text-text-secondary/80">{node.note}</span>
            )}
          </span>
        )}
      </div>
      {hasChildren && (
        <div>
          {node.children!.map((child, i) => (
            <div key={child.label + i} className="flex items-start gap-1">
              <span className="text-text-secondary whitespace-pre">{childPrefix}</span>
              <SettingsTreeBranch node={child} isLast={i === node.children!.length - 1} />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * SmartReleaseChain — visual: 10min → check → 20min → check → 30min → check → skip.
 * ------------------------------------------------------------------------- */
function SmartReleaseChain({ steps }: { steps: SmartReleaseStep[] }) {
  const outcomeColor: Record<SmartReleaseStep["outcome"], string> = {
    found: "var(--c-success)",
    retry: "var(--c-warning)",
    skip: "var(--c-danger)",
  };
  const outcomeLabel: Record<SmartReleaseStep["outcome"], string> = {
    found: "Found",
    retry: "Retry",
    skip: "Skip",
  };
  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-2 text-[10.5px]">
        <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
          Polling timeline:
        </span>
        {steps.map((s, i) => (
          <div key={s.num} className="flex items-center gap-2">
            <span
              className="inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-[11px] font-bold"
              style={{
                backgroundColor: `color-mix(in srgb, ${outcomeColor[s.outcome]} 12%, transparent)`,
                color: outcomeColor[s.outcome],
              }}
            >
              <span
                className="inline-block h-1.5 w-1.5 rounded-full"
                style={{ backgroundColor: outcomeColor[s.outcome] }}
              />
              {s.label}
            </span>
            {i < steps.length - 1 && (
              <span className="text-text-secondary/60" aria-hidden="true">
                →
              </span>
            )}
          </div>
        ))}
      </div>
      <div className="space-y-2.5">
        {steps.map((s) => (
          <div key={s.num} className="flex items-start gap-3">
            <span
              className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-[11px] font-bold text-white"
              style={{ backgroundColor: outcomeColor[s.outcome] }}
            >
              {s.num}
            </span>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-[13px] font-bold text-text-primary">{s.label}</h3>
                <span
                  className="rounded-md px-1.5 py-0.5 text-[9.5px] font-bold uppercase tracking-wider"
                  style={{
                    backgroundColor: `color-mix(in srgb, ${outcomeColor[s.outcome]} 15%, transparent)`,
                    color: outcomeColor[s.outcome],
                  }}
                >
                  {outcomeLabel[s.outcome]}
                </span>
              </div>
              <p className="mt-0.5 text-xs leading-relaxed text-text-secondary">{s.detail}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * UserDecisionCard — renders one user decision (§13) with a ✅ badge + the
 * user's answer. Replaces the old "Open Questions" card with a positive
 * "this is now decided" treatment.
 * ------------------------------------------------------------------------- */
function UserDecisionCard({ decision }: { decision: UserDecision }) {
  return (
    <Card className="p-0 overflow-hidden">
      <div
        className="px-4 py-3"
        style={{
          backgroundColor: "color-mix(in srgb, var(--c-success) 6%, transparent)",
          borderLeft: "3px solid var(--c-success)",
        }}
      >
        <div className="flex items-start gap-3">
          <span
            className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md text-[11px] font-bold text-white"
            style={{ backgroundColor: "var(--c-success)" }}
            aria-label="Decided"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="3"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="w-3.5 h-3.5"
            >
              <path d="M20 6 9 17l-5-5" />
            </svg>
          </span>
          <div className="min-w-0 flex-1">
            <div className="mb-1.5 flex flex-wrap items-center gap-2">
              <span className="rounded-md bg-surface-alt px-2 py-0.5 text-[9.5px] font-bold uppercase tracking-wider text-text-secondary">
                Decision {decision.num}
              </span>
              <h3 className="text-[13px] font-bold leading-tight text-text-primary">
                {decision.question}
              </h3>
            </div>
            <div className="rounded-md bg-surface px-3 py-2 mt-2">
              <div className="mb-0.5 flex items-center gap-1.5">
                <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--c-success)]">
                  User&apos;s answer
                </span>
              </div>
              <p className="text-xs leading-relaxed text-text-secondary">{decision.decision}</p>
            </div>
          </div>
        </div>
      </div>
    </Card>
  );
}

/* ---------------------------------------------------------------------------
 * TriggerComparisonTable — side-by-side comparison of on_schedule vs
 * on_watchable. The user asked for this clarification (§9a).
 * ------------------------------------------------------------------------- */
function TriggerComparisonTable({ rows }: { rows: TriggerComparison[] }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-border">
      <table className="w-full text-left text-xs">
        <thead className="border-b border-border bg-surface-alt">
          <tr>
            <th className="px-3 py-2 font-bold text-text-primary w-28 shrink-0">Axis</th>
            <th className="px-3 py-2 font-bold text-[var(--c-secondary)]">
              <code className="font-bold">on_schedule</code>
              <span className="ml-1.5 text-[10px] font-normal text-text-secondary">(time-based reminder)</span>
            </th>
            <th className="px-3 py-2 font-bold text-[var(--c-success)]">
              <code className="font-bold">on_watchable</code>
              <span className="ml-1.5 text-[10px] font-normal text-text-secondary">(availability-based confirmation)</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={r.axis} className="border-b border-border last:border-0 align-top">
              <td className="px-3 py-2.5">
                <span className="text-[11px] font-bold uppercase tracking-wider text-text-secondary">
                  {r.axis}
                </span>
              </td>
              <td className="px-3 py-2.5 text-text-secondary">{r.onSchedule}</td>
              <td className="px-3 py-2.5 text-text-secondary">{r.onWatchable}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * SettingsTableRenderer — renders one of the §15 settings tables (Setting |
 * Type | Default | Description). Prominent because the user explicitly asked
 * for every setting to be explained.
 * ------------------------------------------------------------------------- */
function SettingsTableRenderer({ section }: { section: SettingsTableSection }) {
  return (
    <Card className="p-0 overflow-hidden">
      <div className="border-b border-border bg-surface-alt px-4 py-2.5 flex items-center gap-2">
        <StatusDot color="var(--c-primary)" size="md" />
        <span className="text-[10px] font-bold uppercase tracking-widest text-text-secondary">
          {section.path}
        </span>
        <span className="ml-auto rounded-md bg-[var(--c-primary)] px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider text-white">
          {section.rows.length} {section.rows.length === 1 ? "setting" : "settings"}
        </span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs">
          <thead className="border-b border-border bg-surface">
            <tr>
              <th className="px-4 py-2 font-bold text-text-primary min-w-[140px]">Setting</th>
              <th className="px-4 py-2 font-bold text-text-primary min-w-[160px]">Type</th>
              <th className="px-4 py-2 font-bold text-text-primary min-w-[100px]">Default</th>
              <th className="px-4 py-2 font-bold text-text-primary">Description</th>
            </tr>
          </thead>
          <tbody>
            {section.rows.map((r, i) => (
              <tr key={r.setting + i} className="border-b border-border last:border-0 align-top">
                <td className="px-4 py-2.5">
                  <span className="font-bold text-text-primary">{r.setting}</span>
                </td>
                <td className="px-4 py-2.5">
                  <code className="text-[11px] text-[var(--c-secondary)]">{r.type}</code>
                </td>
                <td className="px-4 py-2.5">
                  <DefaultBadge value={r.default} />
                </td>
                <td className="px-4 py-2.5 text-text-secondary leading-relaxed">{r.description}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

/* ---------------------------------------------------------------------------
 * DefaultBadge — visually distinguishes ON / OFF / — / selector defaults.
 * ------------------------------------------------------------------------- */
function DefaultBadge({ value }: { value: string }) {
  const v = value.trim();
  let color = "var(--c-text-secondary)";
  let bg = "var(--c-surface-alt)";
  if (v === "ON" || v === "On") {
    color = "var(--c-success)";
    bg = "color-mix(in srgb, var(--c-success) 12%, transparent)";
  } else if (v === "OFF" || v === "Off") {
    color = "var(--c-danger)";
    bg = "color-mix(in srgb, var(--c-danger) 12%, transparent)";
  } else if (v === "—" || v === "-") {
    color = "var(--c-text-secondary)";
    bg = "transparent";
  } else {
    // Selector / mixed defaults — primary tint
    color = "var(--c-primary)";
    bg = "color-mix(in srgb, var(--c-primary) 12%, transparent)";
  }
  return (
    <span
      className="inline-flex items-center rounded-md px-2 py-0.5 text-[10.5px] font-bold uppercase tracking-wider"
      style={{ backgroundColor: bg, color }}
    >
      {value}
    </span>
  );
}
