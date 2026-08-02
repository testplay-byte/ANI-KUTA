import Link from "next/link";
import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import { MetricCard } from "@/components/MetricCard";
import { PhaseTimeline } from "@/components/PhaseTimeline";
import { WorkflowLoop } from "@/components/WorkflowLoop";
import { METRIC_CARDS, QUICK_STATS, PHASES } from "@/lib/data";
import { decisions } from "@/lib/decisions";

export default function OverviewPage() {
  const currentPhase =
    PHASES.find((p) => p.status === "in-progress" || p.status === "blocked") ??
    PHASES[2];
  const confirmedCount = decisions.filter(
    (d) => d.status === "confirmed",
  ).length;
  const needsInputCount = decisions.filter(
    (d) => d.status === "needs-input",
  ).length;

  return (
    <div className="space-y-6">
      {/* Hero — project summary */}
      <Card className="!p-6 md:!p-8">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[11px] font-medium uppercase tracking-widest text-text-secondary">
              Project Status
            </span>
            <StatusDot color="var(--c-warning)" size="sm" />
            <span className="text-[12px] text-text-secondary">
              Phase {currentPhase.id} — {currentPhase.name} · {confirmedCount}/{decisions.length} decisions confirmed
            </span>
          </div>
          <h2 className="text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
            ANI-KUTA{" "}
            <span className="text-text-secondary font-medium">
              — anime streaming app rebuild
            </span>
          </h2>
          <p className="text-[13.5px] text-text-secondary leading-relaxed max-w-2xl">
            A calm, living dashboard for the ANI-KUTA project: 43 modules
            planned, Phase 1 Architecture Plan + Design Language doc complete,
            all decisions D-027..D-041 confirmed. Phase 2 (scaffold — 12
            modules) is next. Kept in sync with{" "}
            <code className="font-mono text-text-primary">AGENT-CONTEXT/</code>{" "}
            on every push.
          </p>
          <div className="flex flex-wrap gap-2 pt-1">
            <Link href="/architecture/" className="no-underline">
              <span
                className="inline-flex items-center gap-2 h-9 px-[18px] rounded-[12px] text-[13.5px] font-medium text-white transition-all duration-200 hover:translate-y-[-1px]"
                style={{
                  backgroundColor: "var(--c-primary)",
                  boxShadow: "0 4px 12px var(--c-primary)33, 0 1px 2px rgba(0,0,0,0.06)",
                }}
              >
                View Architecture Plan
              </span>
            </Link>
            <Link href="/design/" className="no-underline">
              <span className="inline-flex items-center gap-2 h-9 px-[18px] rounded-[12px] text-[13.5px] font-medium bg-chip border border-border text-text-secondary transition-all duration-200 hover:translate-y-[-1px] hover:text-text-primary">
                Design Language
              </span>
            </Link>
            <Link href="/progress/" className="no-underline">
              <span className="inline-flex items-center gap-2 h-9 px-[18px] rounded-[12px] text-[13.5px] font-medium bg-chip border border-border text-text-secondary transition-all duration-200 hover:translate-y-[-1px] hover:text-text-primary">
                Phase 2 Scaffold →
              </span>
            </Link>
          </div>
        </div>
      </Card>

      {/* Metric cards with sparklines */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {METRIC_CARDS.map((m) => (
          <MetricCard key={m.label} metric={m} />
        ))}
      </div>

      {/* Phase timeline */}
      <Card>
        <PhaseTimeline />
      </Card>

      {/* Workflow loop */}
      <Card>
        <WorkflowLoop />
      </Card>

      {/* Two-column: current phase + design language */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Current phase */}
        <Card>
          <div className="flex items-start justify-between gap-3 mb-3">
            <div>
              <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
                Current Phase
              </div>
              <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
                Phase {currentPhase.id} — {currentPhase.name}
              </h3>
            </div>
            <span
              className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium shrink-0"
              style={{
                backgroundColor: "var(--c-warning)1a",
                color: "var(--c-warning)",
              }}
            >
              <StatusDot color="var(--c-warning)" size="sm" />
              Next
            </span>
          </div>
          <p className="text-[13px] text-text-secondary leading-relaxed mb-4">
            {currentPhase.summary}
          </p>

          {currentPhase.next.length > 0 && (
            <div className="space-y-2 mb-4">
              <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary">
                Up Next — {PHASE2_SCAFFOLD_COUNT} modules
              </div>
              {currentPhase.next.slice(0, 6).map((n) => (
                <div
                  key={n}
                  className="flex items-start gap-2 text-[12.5px] text-text-primary"
                >
                  <StatusDot
                    color="var(--c-warning)"
                    size="sm"
                    className="mt-[7px]"
                  />
                  <span className="font-mono text-[11.5px]">{n}</span>
                </div>
              ))}
              {currentPhase.next.length > 6 && (
                <div className="text-[11px] text-text-secondary pl-[14px]">
                  + {currentPhase.next.length - 6} more — see Progress page.
                </div>
              )}
            </div>
          )}

          <Link
            href="/progress/"
            className="inline-flex items-center gap-1 text-[12px] font-medium text-[var(--c-primary)] mt-2 hover:underline"
          >
            View all phases →
          </Link>
        </Card>

        {/* Design Language */}
        <Card className="!p-0 overflow-hidden">
          {/* Mini hero swatch — lime on dark */}
          <div
            className="h-[120px] p-5 flex flex-col justify-between"
            style={{
              background:
                "linear-gradient(135deg, #14111F 0%, #2A2540 60%, #4A6B1A 100%)",
            }}
          >
            <div className="flex items-start justify-between gap-3">
              <div>
                <div
                  className="text-[10px] font-medium uppercase tracking-widest"
                  style={{ color: "#A89EC0" }}
                >
                  App Design Language
                </div>
                <div
                  className="text-[18px] font-bold tracking-extra-tight mt-0.5"
                  style={{ color: "#ECE6F5", letterSpacing: "-0.02em" }}
                >
                  Dark-first, lime-accented
                </div>
              </div>
              <div
                className="h-10 w-10 rounded-[12px] shadow-lg shrink-0"
                style={{ backgroundColor: "#B1F256" }}
                aria-hidden="true"
              />
            </div>
            <div className="flex gap-1.5">
              {["#14111F", "#1B1729", "#2A2540", "#332D4C", "#4A6B1A", "#B1F256"].map((hex) => (
                <span
                  key={hex}
                  className="h-4 flex-1 rounded-[4px] border"
                  style={{ backgroundColor: hex, borderColor: "rgba(255,255,255,0.08)" }}
                  aria-hidden="true"
                />
              ))}
            </div>
          </div>

          {/* Body */}
          <div className="p-5">
            <p className="text-[13px] text-text-secondary leading-relaxed mb-3">
              The new app&apos;s design language — lime green identity (#B1F256)
              on warm-purple-tinted dark surfaces, ExtraBold Roboto headings,
              translucent surfaceVariant cards, floating pill bottom nav,
              cover-color dynamic theming. Documented in{" "}
              <code className="font-mono text-text-primary text-[12px]">
                DESIGN-LANGUAGE.md
              </code>{" "}
              (~1150 lines, every value quoted from source).
            </p>

            <div className="grid grid-cols-3 gap-2 mb-3">
              <MiniStat label="Themes" value="3" />
              <MiniStat label="Accents" value="16" />
              <MiniStat label="Components" value="9+" />
            </div>

            <Link
              href="/design/"
              className="inline-flex items-center gap-1 text-[12px] font-medium text-[var(--c-primary)] hover:underline"
            >
              Explore design language →
            </Link>
          </div>
        </Card>
      </div>

      {/* Decisions summary — all confirmed */}
      <Card>
        <div className="flex items-start justify-between gap-3 mb-3">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Decisions
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              All {decisions.length} decisions confirmed
            </h3>
          </div>
          <span
            className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium shrink-0"
            style={{
              backgroundColor: "var(--c-success)1a",
              color: "var(--c-success)",
            }}
          >
            <StatusDot color="var(--c-success)" size="sm" />
            {confirmedCount}/{decisions.length}
          </span>
        </div>

        <p className="text-[12.5px] text-text-secondary leading-relaxed mb-4">
          D-027 through D-041 — covering extension compat, base app,
          notifications, manga plan, multi-extension + multi-content-type,
          identity system, ads (deferred), DI, DB, navigation, backup format,
          watch-progress layering, activity tracking, console logging, and
          backup/restore multi-app compat.
        </p>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
          {decisions.slice(0, 6).map((d) => (
            <Link
              key={d.id}
              href="/decisions/"
              className="flex items-center gap-3 p-2.5 rounded-[10px] border border-border bg-surface-alt/40 hover:bg-canvas transition-colors duration-150 no-underline"
            >
              <span className="font-mono text-[11px] text-text-secondary shrink-0 w-14">
                {d.id}
              </span>
              <span className="text-[12.5px] font-medium text-text-primary truncate flex-1">
                {d.title}
              </span>
              <span className="w-2 h-2 rounded-full bg-[var(--c-success)] shrink-0" />
            </Link>
          ))}
        </div>

        {decisions.length > 6 && (
          <div className="text-[11px] text-text-secondary mt-2">
            + {decisions.length - 6} more — see Decisions page.
          </div>
        )}

        <Link
          href="/decisions/"
          className="inline-flex items-center gap-1 text-[12px] font-medium text-[var(--c-primary)] mt-4 hover:underline"
        >
          Review all decisions →
        </Link>
      </Card>

      {/* Quick stats footer row */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <QuickStat label="Modules planned" value={String(QUICK_STATS.modules)} accent="var(--c-primary)" />
        <QuickStat label="Phase 2 scaffold" value={String(QUICK_STATS.scaffoldModules)} accent="var(--c-warning)" />
        <QuickStat label="Research docs" value={String(QUICK_STATS.researchDocs)} accent="var(--c-secondary)" />
        <QuickStat label="Design language" value={String(QUICK_STATS.designLanguageDoc)} accent="var(--c-success)" />
      </div>
    </div>
  );
}

const PHASE2_SCAFFOLD_COUNT = 12;

function MiniStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="text-center p-2 rounded-[8px] bg-surface-alt/60">
      <div className="text-[16px] font-bold tracking-extra-tight text-text-primary leading-none">
        {value}
      </div>
      <div className="text-[9.5px] font-medium uppercase tracking-widest text-text-secondary mt-1">
        {label}
      </div>
    </div>
  );
}

function QuickStat({
  label,
  value,
  accent,
}: {
  label: string;
  value: string;
  accent: string;
}) {
  return (
    <div className="rounded-[14px] border border-border bg-surface p-3.5">
      <div className="flex items-center gap-2 mb-1.5">
        <span
          className="w-1.5 h-1.5 rounded-full"
          style={{ backgroundColor: accent }}
          aria-hidden="true"
        />
        <span className="text-[10px] font-medium uppercase tracking-widest text-text-secondary">
          {label}
        </span>
      </div>
      <div className="text-[20px] font-bold tracking-extra-tight text-text-primary leading-none">
        {value}
      </div>
    </div>
  );
}
