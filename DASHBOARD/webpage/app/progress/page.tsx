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
 * Progress page (v5) — Phase 0–3 done (Setup + Plan + Scaffold + Core modules),
 * Phase 4 (feature screens) in progress, Phase 5 plan written.
 *
 * Sections:
 *  1. Header card + legend.
 *  2. Phase timeline (compact, at top).
 *  3. Phase 3 wrap-up (15 modules across 4 sub-phases — all built).
 *  4. Phase 4 progress (feature screens built, accent palette live, sheets capped).
 *  5. Phase 5 plan written note.
 *  6. Current phase checklist.
 *  7. Full phase list (detailed, with done/next/blockers per phase).
 */
export default function ProgressPage() {
  const currentPhase =
    PHASES.find((p) => p.status === "in-progress" || p.status === "blocked") ??
    PHASES[3];
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
            <StatusDot color="var(--c-warning)" size="sm" />
            {doneCount}/{PHASES.length} done · Phase 4 in progress
          </span>
        </div>
        <p className="text-[13px] text-text-secondary leading-relaxed max-w-2xl">
          The project advances phase-by-phase. Phase 0 (setup), Phase 1
          (architecture plan + design language), Phase 2 (scaffold — 12
          modules), and Phase 3 (15 core modules across 4 sub-phases) are
          complete. Phase 4 (feature screens — Library, Search, More,
          Settings, Appearance) is in progress: those screens are built,
          the accent palette system (D-053) is live, and bottom-up sheets
          are capped at 70% of screen height (D-052). Phase 5 plan
          (identity, watch screen, history/updates, backup/restore,
          extension repos) is written. Live status — kept in sync with{" "}
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
            <li>· <strong>CI green across all 31 modules.</strong></li>
          </ul>
        </div>
      </Card>

      {/* Phase 4 in-progress — feature screens built, accent palette live */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              §4 — Phase 4 In Progress
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              Library, Search, More, Settings, Appearance built · accent palette live
            </h3>
            <p className="text-[12.5px] text-text-secondary leading-relaxed mt-1.5 max-w-2xl">
              The user-facing UI layer is taking shape on top of the Phase 3
              core. Five feature screens are built, the accent palette system
              (D-053) is functional with live apply, and bottom-up sheets are
              capped at 70% of device screen height (D-052). Watch, history,
              my, backup, trackers, and setup-wizard remain.
            </p>
          </div>
          <span
            className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium shrink-0"
            style={{
              backgroundColor: "var(--c-warning)1a",
              color: "var(--c-warning)",
            }}
          >
            <StatusDot color="var(--c-warning)" size="sm" />
            In progress
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
            Phase 4 — Remaining
          </div>
          <ul className="space-y-1 text-[12px] text-text-primary">
            <li>· :feature:anime-watch:{`{api,impl}`} — player host screen (deferred to Phase 5).</li>
            <li>· :feature:anime-history, :anime-updates, :anime-my — profile + stats.</li>
            <li>· :feature:backup, :trackers, :extensions-settings, :download, :setup-wizard, :episode-settings.</li>
            <li>· Custom color-picker UI for CUSTOM accent (deferred to Phase 5d).</li>
          </ul>
        </div>
      </Card>

      {/* Phase 5 plan written note */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-3">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              §5 — Phase 5 Plan Written
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              Plan ready — identity, watch, history/updates, backup/restore, extension repos
            </h3>
            <p className="text-[12.5px] text-text-secondary leading-relaxed mt-1.5 max-w-2xl">
              The Phase 5 plan is written (APP/ani-kuta/DOCUMENTATION/19-phase5-plan.md).
              Scope: identity system completion, watch screen (player host),
              history + updates, backup/restore multi-app compat, extension
              repo management, and the custom color-picker UI for the CUSTOM
              accent (Phase 5d). Pending — starts when Phase 4 wraps.
            </p>
          </div>
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium shrink-0 border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-secondary)" size="sm" />
            Planned
          </span>
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
