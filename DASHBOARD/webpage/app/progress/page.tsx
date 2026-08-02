import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import { Checklist } from "@/components/Checklist";
import { PhaseTimeline } from "@/components/PhaseTimeline";
import {
  PHASES,
  PHASE_CHECKLISTS,
  PHASE2_SCAFFOLD,
  type Phase,
} from "@/lib/data";

/**
 * Progress page (v3) — Phase 0 done, Phase 1 done (Architecture Plan +
 * Design Language doc), Phase 2 next (scaffold — 12 modules).
 *
 * Sections:
 *  1. Header card + legend.
 *  2. Phase timeline (compact, at top).
 *  3. Current Phase (Phase 2) checklist — scaffold modules.
 *  4. Phase 2 scaffold module list (12 modules from §13 of the plan).
 *  5. Full phase list (detailed, with done/next/blockers per phase).
 */
export default function ProgressPage() {
  const currentPhase =
    PHASES.find((p) => p.status === "in-progress" || p.status === "blocked") ??
    PHASES[2];
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
            {doneCount}/{PHASES.length} done · Phase 2 next
          </span>
        </div>
        <p className="text-[13px] text-text-secondary leading-relaxed max-w-2xl">
          The project advances phase-by-phase. Phase 0 (setup) and Phase 1
          (architecture plan + design language) are complete. Phase 2 (scaffold —
          12 modules) is next. Live status — kept in sync with{" "}
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

      {/* Phase 2 scaffold — module list */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              §13 — Phase 2 Scaffold
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              12 modules to build first
            </h3>
            <p className="text-[12.5px] text-text-secondary leading-relaxed mt-1.5 max-w-2xl">
              Minimal viable structure to validate the architecture. Trimmed to
              exercise every module — no dead code (Ponytail). Deferred modules
              enter in Phase 3.
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
            Next phase
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
          {PHASE2_SCAFFOLD.map((m) => (
            <div
              key={m.n}
              className="flex items-start gap-3 p-3 rounded-[12px] border border-border bg-surface-alt/40"
            >
              <span
                className="inline-flex items-center justify-center w-7 h-7 rounded-[10px] font-mono text-[12px] font-bold shrink-0"
                style={{
                  backgroundColor: "var(--c-warning)1a",
                  color: "var(--c-warning)",
                  border: "1.5px solid var(--c-warning)",
                }}
              >
                {m.n}
              </span>
              <div className="flex-1 min-w-0">
                <div className="font-mono text-[12px] font-semibold text-text-primary mb-0.5">
                  {m.name}
                </div>
                <div className="text-[11.5px] text-text-secondary leading-snug">
                  {m.job}
                </div>
              </div>
            </div>
          ))}
        </div>

        <div className="mt-4 p-4 rounded-[12px] border border-border bg-surface">
          <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-2">
            Phase 2 Deliverable
          </div>
          <ul className="space-y-1 text-[12px] text-text-primary">
            <li>· App builds via CI (GitHub Actions, arm64-v8a + armeabi-v7a).</li>
            <li>· App launches → Browse screen (AniList trending) → tap → Details screen.</li>
            <li>· Nav3 navigation works (back stack survives recreate).</li>
            <li>· Koin DI wired.</li>
            <li>· SQLDelight DB initialized (empty schema — ready for Phase 3).</li>
            <li>· Logger working (debug builds show logs, lambda-based, zero overhead when off).</li>
            <li>· Theme engine working (light/dark).</li>
            <li>· <strong>Every module is exercised — no dead code.</strong></li>
          </ul>
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
