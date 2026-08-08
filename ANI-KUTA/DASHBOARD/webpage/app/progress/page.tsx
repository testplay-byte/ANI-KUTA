import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import { Checklist } from "@/components/Checklist";
import { PhaseTimeline } from "@/components/PhaseTimeline";
import {
  PHASES,
  PHASE_CHECKLISTS,
  type Phase,
} from "@/lib/data";

/**
 * Progress page (v7) — ALL PHASES DONE. Phase 0–5 + B/C/D/WP/HI/UP/SC/TR/NOTIF/CW/DL
 * complete + CI verified GREEN on branch feature/watch-progress-history-updates.
 * 44 modules built, 28 DB tables, 152 decisions confirmed.
 *
 * Sections:
 *  1. Header card + legend.
 *  2. Phase timeline (compact, at top).
 *  3. Phase 3 wrap-up (15 modules across 4 sub-phases — all built).
 *  4. Phase 4 wrap-up (feature screens + accent palette — COMPLETE).
 *  5. Phase 5 wrap-up (5a–5e DONE — 5f deferred).
 *  6. Phase 10 wrap-up (B/C/D/WP/HI/UP/SC/TR/NOTIF/CW/DL — all DONE).
 *  7. Current phase checklist.
 *  8. Full phase list (detailed, with done/next/blockers per phase).
 */
export default function ProgressPage() {
  // All phases are done — show the most recent "done" phase (the post-Phase-5 batch).
  const currentPhase =
    PHASES.find((p) => p.status === "in-progress" || p.status === "blocked") ??
    PHASES.find((p) => p.id === 10) ??
    PHASES[PHASES.length - 1];
  const currentChecklist = PHASE_CHECKLISTS.find(
    (c) => c.phaseId === currentPhase.id,
  );
  const doneCount = PHASES.filter((p) => p.status === "done").length;

  return (
    <div className="space-y-6">
      {/* Header card */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-3">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Progress
            </div>
            <h2 className="text-[22px] font-bold tracking-extra-tight text-text-primary">
              Project Phases
            </h2>
          </div>
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-success)" size="sm" />
            {doneCount}/{PHASES.length} done · ALL PHASES COMPLETE ✓
          </span>
        </div>
        <p className="text-[13px] text-text-secondary leading-relaxed max-w-2xl">
          ALL PHASES COMPLETE + CI verified GREEN on branch feature/watch-progress-history-updates.
          Phase 0 (setup), Phase 1 (architecture plan + design language), Phase 2
          (scaffold — 12 modules), Phase 3 (15 core modules across 4 sub-phases),
          Phase 4 (feature screens + accent palette), Phase 5 (5a–5e — 5f deferred),
          and Phase 10 (post-Phase-5 work: B/C/D/WP/HI/UP/SC/TR/NOTIF/CW/DL) are
          all done. 44 modules built (1 app + 25 core + 1 data + 17 feature),
          28 DB tables (26 active + 2 deferred), 152 decisions confirmed
          (D-001..D-152). Nav3 REMOVED (D-150) — hand-rolled NavigationController.
          Live status — kept in sync with{" "}
          <code className="font-mono text-text-primary">memory/progress.md</code>.
        </p>
        <div className="flex flex-wrap gap-4 mt-4 text-[11.5px] text-text-secondary">
          <LegendItem color="var(--c-success)" label="Done" />
          <LegendItem color="var(--c-warning)" label="In progress / Next" />
          <LegendItem color="var(--c-danger)" label="Blocked" />
          <LegendItem color="var(--c-text-secondary)" label="Pending" />
        </div>
      </Card>

      {/* Phase timeline (compact) */}
      <Card>
        <PhaseTimeline />
      </Card>

      {/* Phase 3 wrap-up — 15 modules built across 4 sub-phases */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              §3 — Phase 3 Complete
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              15 core modules built — all 4 sub-phases ✓
            </h3>
            <p className="text-[12.5px] text-text-secondary leading-relaxed mt-1.5 max-w-2xl">
              Identity system, extension loader, MPV playback pipeline,
              download manager, trackers, and episode metadata — all live.
              Phase 3 was split into 4 sub-phases, each independently testable.
            </p>
          </div>
          <span
            className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium shrink-0"
            style={{
              backgroundColor: "var(--c-success)1a",
              color: "var(--c-success)",
            }}
          >
            <StatusDot color="var(--c-success)" size="sm" />
            Done
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-2 mb-4">
          <Phase3SubPhaseRow id="3a" name="Foundation" count={4} modules=":core:database · :core:watch-progress · :core:activity-tracker · :core:preferences" />
          <Phase3SubPhaseRow id="3b" name="Extensions" count={4} modules=":core:provider-api · :core:source-api · :data:extension · JitPack repo" />
          <Phase3SubPhaseRow id="3c" name="Playback" count={4} modules="player-mpv-lib · :core:player · :core:video-resolver · :core:download" />
          <Phase3SubPhaseRow id="3d" name="Supporting" count={3} modules=":core:episode-metadata · :core:tracker-api · :core:tracker-anilist" />
        </div>

        <div className="mt-4 p-4 rounded-[12px] border border-border bg-surface">
          <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-2">
            Phase 3 Deliverable
          </div>
          <ul className="space-y-1 text-[12px] text-text-primary">
            <li>· Identity system (ContentUID + ExternalReference + matching engine) live.</li>
            <li>· Aniyomi extensions loadable — install + browse sources end-to-end.</li>
            <li>· Video pipeline working — resolve URL → play via MPV → save progress.</li>
            <li>· Download manager (HTTP + HLS + resume) operational.</li>
            <li>· AniList tracker sync wired (tracker-api + tracker-anilist).</li>
            <li>· <strong>CI green across all 44 modules (incl. Phase B/C/D/WP/HI/UP/SC/TR/NOTIF/CW/DL additions).</strong></li>
          </ul>
        </div>
      </Card>

      {/* Phase 4 DONE — feature screens + accent palette COMPLETE */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              §4 — Phase 4 COMPLETE ✓
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              Library, Search, More, Settings, Appearance built · accent palette live
            </h3>
            <p className="text-[12.5px] text-text-secondary leading-relaxed mt-1.5 max-w-2xl">
              The user-facing UI layer was built on top of the Phase 3 core.
              Five feature screens shipped, the accent palette system (D-053)
              is functional with live apply, and bottom-up sheets are capped
              at 70% of device screen height (D-052). STATUS: COMPLETE — later
              phases (5 + 10) absorbed the remaining watch/history/my/backup/
              trackers/setup-wizard work.
            </p>
          </div>
          <span
            className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium shrink-0"
            style={{
              backgroundColor: "var(--c-success)1a",
              color: "var(--c-success)",
            }}
          >
            <StatusDot color="var(--c-success)" size="sm" />
            Done ✓
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-2 mb-4">
          <Phase4DoneRow label="Library" desc=":feature:anime-library:{api,impl} — grid + list + categories + sort + continue-watching rail" />
          <Phase4DoneRow label="Search" desc=":feature:anime-search:{api,impl} — AniList + extension sources + filters" />
          <Phase4DoneRow label="More" desc=":feature:anime-more:{api,impl} — extensions / trackers / backup / downloads entry points" />
          <Phase4DoneRow label="Settings" desc=":feature:settings:{api,impl} — General / Player / About / Logging" />
          <Phase4DoneRow label="Appearance" desc=":feature:appearance — theme + accent palette + UI toggles" />
          <Phase4DoneRow label="Accent palette (D-053)" desc="10 presets + CUSTOM, lerp-derived containers, live apply via MainActivity" />
          <Phase4DoneRow label="Sheets 70% cap (D-052)" desc="ModalBottomSheet root Column capped at 70% device screen height" />
          <Phase4DoneRow label="UI polish" desc="Browse heading, translucency, component refinements" />
        </div>

        <div className="mt-4 p-4 rounded-[12px] border border-border bg-surface">
          <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-2">
            Phase 4 — STATUS
          </div>
          <ul className="space-y-1 text-[12px] text-text-primary">
            <li>· STATUS: COMPLETE ✓ — later phases absorbed the remaining work.</li>
            <li>· :feature:watch:{`{api,impl}`} shipped in Phase 5c (DONE).</li>
            <li>· :feature:anime-history, :feature:updates shipped in Phase HI + Phase UP (DONE).</li>
            <li>· :feature:extensions-settings, :feature:download shipped in Phase 5a + Phase DL D.5/D.6 (DONE).</li>
            <li>· :feature:backup, :trackers, :setup-wizard, :episode-settings — deferred (lower priority).</li>
          </ul>
        </div>
      </Card>

      {/* Phase 5 DONE (5a–5e) — 5f deferred (D-054) */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-3">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              §5 — Phase 5 DONE (5a–5e) · 5f deferred (D-054)
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              Extensions → Details → Watch → Identity → History → Backup
            </h3>
            <p className="text-[12.5px] text-text-secondary leading-relaxed mt-1.5 max-w-2xl">
              The Phase 5 plan was written (APP/ani-kuta/DOCUMENTATION/19-phase5-plan.md)
              + re-ordered per user directive (D-054): functional first,
              refinements second. The watch flow used a minimal source_link
              (a single row pointing an episode at a source for playback) —
              it did NOT need the full identity graph. 5a–5c shipped the watchable
              app with minimal linking, 5d upgraded linking to the full identity
              graph (via Phase B auto-link + Phase C content identity), 5e shipped
              History + Updates + Schedule. 5f (Backup + Color-picker) is deferred.
              STATUS: 5a–5e COMPLETE ✓.
            </p>
          </div>
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium shrink-0" style={{ backgroundColor: "var(--c-success)1a", color: "var(--c-success)" }}>
            <StatusDot color="var(--c-success)" size="sm" />
            Done ✓
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-2 mt-4">
          <Phase5SubPhaseRow
            id="5a"
            name="Extension Management ✓"
            desc="DONE — :feature:extensions-settings:{api,impl} + :data:extension. Install extensions, add/manage repos, trust flow, source browser (D-031)."
          />
          <Phase5SubPhaseRow
            id="5b"
            name="Details Page Overhaul ✓"
            desc="DONE — :feature:anime-details:impl + EpisodeDownloadControl (Phase DL D.6). Banner, info, episodes list, source linking, resolver bottom sheet → watch."
          />
          <Phase5SubPhaseRow
            id="5c"
            name="Watch Screen ✓"
            desc="DONE — :feature:watch:{api,impl}. MPV via AndroidView, resume position. The testable milestone — app became watchable."
            highlight
          />
          <Phase5SubPhaseRow
            id="5d"
            name="Identity System ✓"
            desc="DONE — migrated minimal source_link to Phase B auto-link (:core:smart-matcher) + Phase C content identity (:core:content)."
          />
          <Phase5SubPhaseRow
            id="5e"
            name="History + Updates ✓"
            desc="DONE — :feature:anime-history (Phase HI) + :core:updates + :feature:updates (Phase UP) + :core:schedule (Phase SC). Notifications shipped in Phase NOTIF (not deferred to Phase 6)."
          />
          <Phase5SubPhaseRow
            id="5f"
            name="Backup/Restore + Color Picker (deferred)"
            desc="DEFERRED — lower priority post-watchable-app. Multi-format import (Aniyomi .tachibk, Mangayomi), export .anikuta v2, custom accent color picker (D-053 CUSTOM editor)."
          />
        </div>
      </Card>

      {/* Phase 10 — post-Phase-5 work (B/C/D/WP/HI/UP/SC/TR/NOTIF/CW/DL) all DONE */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              §10 — Post-Phase-5 Work COMPLETE ✓
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              Phase B/C/D/WP/HI/UP/SC/TR/NOTIF/CW/DL — all shipped + CI GREEN
            </h3>
            <p className="text-[12.5px] text-text-secondary leading-relaxed mt-1.5 max-w-2xl">
              The post-Phase-5 work shipped on branch feature/watch-progress-history-updates.
              Auto-link system (B), content identity (C), data management + caching (D),
              watch progress + watched status (WP), history page (HI), updates + WorkManager
              smart engine (UP), schedule + actual-release (SC), ratings (TR), notifications
              (NOTIF), continue watching (CW), download system (DL — all 9 phases D.0–D.8).
              Nav3 REMOVED (D-150) — hand-rolled NavigationController. 7 new DB tables.
              All CI verified GREEN.
            </p>
          </div>
          <span
            className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium shrink-0"
            style={{
              backgroundColor: "var(--c-success)1a",
              color: "var(--c-success)",
            }}
          >
            <StatusDot color="var(--c-success)" size="sm" />
            Done ✓
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-2 mb-4">
          <Phase4DoneRow label="Phase B — Auto-link" desc=":core:smart-matcher — fuzzy match_key-based matching engine" />
          <Phase4DoneRow label="Phase C — Content identity" desc=":core:content — ContentRecord, mainId, ContentRepository, AnilistDetailRepository" />
          <Phase4DoneRow label="Phase D — Data caching" desc=":core:metadata + :core:data-cache + 3 new tables (D.1–D.5 all done)" />
          <Phase4DoneRow label="Phase WP — Watch progress" desc="SqlDelightWatchProgressStore · episode_key · 85% auto-mark · two-flag state machine" />
          <Phase4DoneRow label="Phase HI — History" desc=":feature:anime-history:{api,impl} — day-grouped LazyColumn, swipe-delete, Clear all" />
          <Phase4DoneRow label="Phase UP — Updates" desc=":core:updates + :feature:updates:{api,impl} + WorkManager smart engine + 2 new tables" />
          <Phase4DoneRow label="Phase SC — Schedule" desc=":core:schedule — AniList airing API, live countdown, ActualReleaseUpdater (SC-2) + 1 new table" />
          <Phase4DoneRow label="Phase TR — Ratings" desc=":core:ratings + RatingStore + 2 new tables (per-anime + per-episode user ratings 0-100)" />
          <Phase4DoneRow label="Phase NOTIF — Notifications" desc=":core:notifications + 2 new tables (notification_config, notification_sent) · 4 channels · dedup" />
          <Phase4DoneRow label="Phase CW — Continue Watching" desc="getContinueWatching query + observeContinueWatching Flow (UI deferred)" />
          <Phase4DoneRow label="Phase DL — Download system" desc="ALL 9 phases D.0–D.8 implemented + CI GREEN · D-148..D-152 · 7-state machine · SAF/data.json · AutoDownloadEngine" />
          <Phase4DoneRow label="D-150 — Nav3 REMOVED" desc="Hand-rolled NavigationController + sealed-class NavKeys (replaces Jetpack Nav3 from D-036)" />
        </div>
      </Card>

      {/* Current phase checklist */}
      {currentChecklist && (
        <Card>
          <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
            Current Phase Checklist
          </div>
          <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary mb-4">
            Phase {currentChecklist.phaseId} — {currentChecklist.phaseName}
          </h3>
          <Checklist
            title={currentChecklist.phaseName}
            items={currentChecklist.items}
          />
        </Card>
      )}

      {/* Phase timeline (detailed list) */}
      <div className="relative">
        <div
          className="absolute left-[18px] top-2 bottom-2 w-px bg-border hidden sm:block"
          aria-hidden="true"
        />
        <div className="space-y-4">
          {PHASES.map((phase) => (
            <PhaseCard key={phase.id} phase={phase} />
          ))}
        </div>
      </div>
    </div>
  );
}

function PhaseCard({ phase }: { phase: Phase }) {
  const { color, label } = phaseStatusMeta(phase.status);

  return (
    <Card className="!p-5 hover:-translate-y-[1px]">
      <div className="flex items-start gap-4">
        {/* Phase number / status indicator */}
        <div className="relative shrink-0">
          <div
            className="w-9 h-9 rounded-full flex items-center justify-center text-[14px] font-bold z-10 relative"
            style={{
              backgroundColor: `${color}1a`,
              border: `1.5px solid ${color}`,
              color: color,
            }}
          >
            {phase.id}
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1 mb-1.5">
            <h3 className="text-[15px] font-bold tracking-extra-tight text-text-primary">
              Phase {phase.id} — {phase.name}
            </h3>
            <span
              className="inline-flex items-center gap-1.5 h-5 px-2 rounded-full text-[10.5px] font-medium"
              style={{ backgroundColor: `${color}1a`, color: color }}
            >
              <StatusDot color={color} size="sm" />
              {label}
            </span>
            <span className="text-[11px] font-mono text-text-secondary">
              {phase.days}d · day {phase.startDay}→{phase.startDay + phase.days}
            </span>
          </div>
          <p className="text-[13px] text-text-secondary leading-relaxed mb-4">
            {phase.summary}
          </p>

          {phase.done.length > 0 && (
            <Section
              title="Done"
              accent="var(--c-success)"
              items={phase.done.map((d) => ({ text: d, glyph: "✓" }))}
            />
          )}

          {phase.next.length > 0 && (
            <Section
              title={phase.status === "done" ? "Final state" : "Up next"}
              accent="var(--c-warning)"
              items={phase.next.map((n) => ({ text: n, glyph: "→" }))}
            />
          )}

          {phase.blockers.length > 0 && (
            <Section
              title="Blockers"
              accent="var(--c-danger)"
              items={phase.blockers.map((b) => ({ text: b, glyph: "!" }))}
            />
          )}
        </div>
      </div>
    </Card>
  );
}

function Section({
  title,
  accent,
  items,
}: {
  title: string;
  accent: string;
  items: { text: string; glyph: string }[];
}) {
  return (
    <div className="mb-3 last:mb-0">
      <div className="text-[10px] font-medium uppercase tracking-widest text-text-secondary mb-2">
        {title}
      </div>
      <div className="space-y-1.5">
        {items.map((item, i) => (
          <div key={i} className="flex items-start gap-2 text-[13px] text-text-primary leading-relaxed">
            <span className="font-mono text-[12px] shrink-0 mt-[1px]" style={{ color: accent }} aria-hidden="true">
              {item.glyph}
            </span>
            <span>{item.text}</span>
          </div>
        ))}
      </div>
    </div>
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

function phaseStatusMeta(status: Phase["status"]): { color: string; label: string } {
  switch (status) {
    case "done":
      return { color: "var(--c-success)", label: "Done" };
    case "in-progress":
      return { color: "var(--c-warning)", label: "In progress" };
    case "blocked":
      return { color: "var(--c-danger)", label: "Blocked" };
    default:
      return { color: "var(--c-text-secondary)", label: "Pending" };
  }
}

function Phase3SubPhaseRow({
  id,
  name,
  count,
  modules,
}: {
  id: string;
  name: string;
  count: number;
  modules: string;
}) {
  return (
    <div className="flex items-start gap-3 p-3 rounded-[12px] border border-border bg-surface-alt/40">
      <span
        className="inline-flex items-center justify-center w-9 h-9 rounded-[10px] font-mono text-[12px] font-bold shrink-0"
        style={{
          backgroundColor: "var(--c-success)1a",
          color: "var(--c-success)",
          border: "1.5px solid var(--c-success)",
        }}
      >
        {id}
      </span>
      <div className="flex-1 min-w-0">
        <div className="flex items-baseline gap-2 mb-0.5">
          <span className="text-[13px] font-semibold text-text-primary">
            {name}
          </span>
          <span className="text-[11px] text-text-secondary">
            · {count} modules ✓
          </span>
        </div>
        <div className="font-mono text-[11px] text-text-secondary leading-snug break-words">
          {modules}
        </div>
      </div>
    </div>
  );
}

function Phase4DoneRow({ label, desc }: { label: string; desc: string }) {
  return (
    <div className="flex items-start gap-3 p-3 rounded-[12px] border border-border bg-surface-alt/40">
      <span
        className="inline-flex items-center justify-center w-9 h-9 rounded-[10px] font-mono text-[14px] shrink-0"
        style={{
          backgroundColor: "var(--c-success)1a",
          color: "var(--c-success)",
          border: "1.5px solid var(--c-success)",
        }}
        aria-hidden="true"
      >
        ✓
      </span>
      <div className="flex-1 min-w-0">
        <div className="text-[13px] font-semibold text-text-primary mb-0.5">
          {label}
        </div>
        <div className="font-mono text-[11px] text-text-secondary leading-snug break-words">
          {desc}
        </div>
      </div>
    </div>
  );
}

function Phase5SubPhaseRow({
  id,
  name,
  desc,
  highlight = false,
}: {
  id: string;
  name: string;
  desc: string;
  highlight?: boolean;
}) {
  const accent = highlight ? "var(--c-primary)" : "var(--c-secondary)";
  return (
    <div
      className={`flex items-start gap-3 p-3 rounded-[12px] border bg-surface-alt/40 ${highlight ? "border-[var(--c-primary)]/40" : "border-border"}`}
    >
      <span
        className="inline-flex items-center justify-center w-9 h-9 rounded-[10px] font-mono text-[12px] font-bold shrink-0"
        style={{
          backgroundColor: `${accent}1a`,
          color: accent,
          border: `1.5px solid ${accent}`,
        }}
      >
        {id}
      </span>
      <div className="flex-1 min-w-0">
        <div className="flex items-baseline gap-2 mb-0.5">
          <span className="text-[13px] font-semibold text-text-primary">
            {name}
          </span>
          {highlight && (
            <span className="text-[10px] font-medium uppercase tracking-widest text-[var(--c-primary)]">
              milestone
            </span>
          )}
        </div>
        <div className="font-mono text-[11px] text-text-secondary leading-snug break-words">
          {desc}
        </div>
      </div>
    </div>
  );
}
