import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import {
  CONCERNS_ACCEPTED,
  CONCERNS_DASHBOARD,
  CONCERNS_OPEN,
  CONCERNS_RESOLVED,
  CONCERNS_VERIFIED_FACTS,
  DOC_DRIFT,
  DOC_DRIFT_INTRO,
  DOC_DRIFT_ROOT_CAUSE,
  FEATURES_REMAINING,
  FOOTER_NOTE_BULLETS,
  FORWARD_DIRECTION,
  HEALTH,
  REVIEW_META,
  SEVERITY_META,
  SNAPSHOT,
  TOP_RISKS,
  WHAT_BUILT,
  type Severity,
  type BacklogGroup,
} from "@/lib/projectReview";

/**
 * /project-review/ — Live Project Review.
 *
 * Renders the 9-section review findings (verified against the actual codebase
 * on 2026-08-13) per DESIGN.md (MEMORY OS v3). Static Server Component — no
 * interactivity needed, no "use client".
 *
 * Sections:
 *  1. Snapshot (hero + verified metrics table + tech stack)
 *  2. Project Health Verdict (verdict + health indicators table)
 *  3. What's Built (feature areas grid)
 *  4. Concerns & Issues (Open · Accepted · Resolved · Dashboard debt)
 *  5. Doc Drift Caught (table)
 *  6. Features Remaining / Backlog (Phase 6+)
 *  7. Forward Direction / Recommendations (4 prioritized steps)
 *  8. Top Risks (table)
 *  9. Footer Note (temporary section notice)
 */
export default function ProjectReviewPage() {
  return (
    <div className="space-y-6">
      {/* ───────────────────────────────────────────────────────────────
       *  HERO
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-3">
          <div className="min-w-0">
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1.5">
              Live Project Review
            </div>
            <h1 className="text-[22px] sm:text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
              Project Review
            </h1>
          </div>
          <div className="flex items-center gap-2 flex-wrap max-w-full">
            <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
              <StatusDot color="var(--c-success)" size="sm" />
              Reviewed {REVIEW_META.reviewDate}
            </span>
            <span
              className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border"
              style={{
                backgroundColor: "color-mix(in srgb, var(--c-warning) 12%, transparent)",
                borderColor: "color-mix(in srgb, var(--c-warning) 35%, transparent)",
                color: "var(--c-warning)",
              }}
              title="Temporary section — remove when no longer needed"
            >
              <span
                className="inline-block w-1.5 h-1.5 rounded-full"
                style={{ backgroundColor: "var(--c-warning)" }}
                aria-hidden="true"
              />
              Temporary
            </span>
          </div>
        </div>
        <p className="text-[12.5px] sm:text-[13.5px] text-text-secondary leading-[1.5] max-w-2xl">
          A fresh, simplified read of where ANI-KUTA stands today — verified
          against the actual codebase (not docs). All key findings in one
          scannable place: snapshot, health verdict, what&apos;s built, concerns,
          doc drift, backlog, recommendations, and top risks.
        </p>
        <p className="text-[11.5px] text-text-secondary leading-relaxed mt-3 pt-3 border-t border-border/60 break-words">
          <span className="font-medium text-text-primary">Reviewer:</span>{" "}
          {REVIEW_META.reviewer}
          <span className="mx-2 text-border">·</span>
          <span className="font-medium text-text-primary">Repo state:</span>{" "}
          <span className="font-mono break-all">{REVIEW_META.repoState}</span>
        </p>
      </Card>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 1 — SNAPSHOT
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§1 — Snapshot"
        title="Project at a glance"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-success)" size="sm" />
            CI green
          </span>
        }
      >
        <div className="space-y-2 mb-5">
          <KVRow label="Project" value={SNAPSHOT.project} mono={false} />
          <KVRow label="App ID" value={SNAPSHOT.appId} />
          <KVRow label="GitHub" value={SNAPSHOT.github} />
          <KVRow label="Dashboard" value={SNAPSHOT.dashboard} />
        </div>

        <SubLabel>{SNAPSHOT.metricsIntro}</SubLabel>
        <div className="overflow-x-auto -mx-1 px-1">
          <table className="w-full min-w-[480px] text-left border-collapse">
            <thead>
              <tr className="text-[10.5px] uppercase tracking-widest text-text-secondary">
                <Th>Metric</Th>
                <Th>Value</Th>
                <Th>Note</Th>
              </tr>
            </thead>
            <tbody>
              {SNAPSHOT.metrics.map((m) => (
                <tr
                  key={m.metric}
                  className="border-t border-border hover:bg-canvas/50 transition-colors"
                >
                  <Td className="font-medium text-text-primary">{m.metric}</Td>
                  <Td className="font-mono font-semibold text-text-primary whitespace-nowrap">
                    {m.value}
                  </Td>
                  <Td className="text-text-secondary">{m.note}</Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <SubLabel className="mt-6">{SNAPSHOT.techStackIntro}</SubLabel>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
          {SNAPSHOT.techStack.map((t) => (
            <div
              key={t.label}
              className="rounded-[12px] border border-border bg-surface-alt/40 p-3"
            >
              <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-1">
                {t.label}
              </div>
              <div className="font-mono text-[11.5px] text-text-primary leading-snug break-words">
                {t.value}
              </div>
            </div>
          ))}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 2 — PROJECT HEALTH VERDICT
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§2 — Project Health Verdict"
        title="Overall health"
      >
        <div
          className="rounded-[14px] border p-4 mb-5"
          style={{
            backgroundColor: "color-mix(in srgb, var(--c-success) 8%, transparent)",
            borderColor: "color-mix(in srgb, var(--c-success) 30%, transparent)",
          }}
        >
          <div className="flex items-baseline gap-2 mb-2">
            <span className="text-[10.5px] font-medium uppercase tracking-widest text-[var(--c-success)]">
              Verdict
            </span>
          </div>
          <div className="text-[18px] font-bold tracking-extra-tight text-text-primary mb-3">
            {HEALTH.verdict}
          </div>
          <ul className="space-y-1.5">
            {HEALTH.bullets.map((b, i) => (
              <li
                key={i}
                className="flex items-start gap-2 text-[12.5px] text-text-primary leading-relaxed"
              >
                <span
                  className="font-mono text-[12px] shrink-0 mt-[1px] text-[var(--c-success)]"
                  aria-hidden="true"
                >
                  ●
                </span>
                <span>{b}</span>
              </li>
            ))}
          </ul>
        </div>

        <SubLabel>Health indicators</SubLabel>
        <div className="overflow-x-auto -mx-1 px-1">
          <table className="w-full min-w-[480px] text-left border-collapse">
            <thead>
              <tr className="text-[10.5px] uppercase tracking-widest text-text-secondary">
                <Th>Area</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {HEALTH.indicators.map((ind) => (
                <tr
                  key={ind.area}
                  className="border-t border-border hover:bg-canvas/50 transition-colors"
                >
                  <Td className="font-medium text-text-primary whitespace-nowrap">
                    {ind.area}
                  </Td>
                  <Td>
                    <span className="inline-flex items-center gap-2 text-[12.5px] text-text-primary">
                      <StatusDot
                        color={toneColor(ind.tone)}
                        size="sm"
                      />
                      <span>{ind.status}</span>
                    </span>
                  </Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 3 — WHAT'S BUILT
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§3 — What's Built"
        title="Feature areas shipped"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-success)" size="sm" />
            {WHAT_BUILT.length} areas
          </span>
        }
      >
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {WHAT_BUILT.map((area) => (
            <div
              key={area.title}
              className="rounded-[14px] border border-border bg-surface-alt/40 p-4 hover:bg-canvas/40 transition-colors"
            >
              <div className="flex items-start gap-2 mb-1.5">
                <StatusDot color={accentColor(area.accent)} size="md" />
                <div className="min-w-0">
                  <div className="text-[13.5px] font-semibold text-text-primary leading-tight">
                    {area.title}
                  </div>
                  <div className="text-[11.5px] text-text-secondary leading-snug mt-0.5">
                    {area.summary}
                  </div>
                </div>
              </div>
              <ul className="space-y-1 mt-2 pl-4">
                {area.items.map((it, i) => (
                  <li
                    key={i}
                    className="text-[12px] text-text-primary leading-relaxed relative before:content-['·'] before:absolute before:left-[-12px] before:text-text-secondary"
                  >
                    {it}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 4 — CONCERNS & ISSUES
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§4 — Concerns & Issues"
        title="The core of the review"
        right={
          <div className="flex items-center gap-1.5 flex-wrap max-w-full">
            <CountPill
              color="var(--c-danger)"
              count={CONCERNS_OPEN.length}
              label="open"
            />
            <CountPill
              color="var(--c-secondary)"
              count={CONCERNS_ACCEPTED.length}
              label="accepted"
            />
            <CountPill
              color="var(--c-success)"
              count={CONCERNS_RESOLVED.length}
              label="resolved"
            />
            <CountPill
              color="var(--c-warning)"
              count={CONCERNS_DASHBOARD.length}
              label="dashboard"
            />
          </div>
        }
      >
        {/* Verified facts */}
        <SubLabel>Verified facts (this session, against actual code)</SubLabel>
        <ul className="space-y-1.5 mb-6">
          {CONCERNS_VERIFIED_FACTS.map((fact, i) => (
            <li
              key={i}
              className="flex items-start gap-2 rounded-[10px] border border-border bg-surface-alt/40 p-2.5"
            >
              <span
                className="font-mono text-[12px] shrink-0 mt-[1px] text-[var(--c-primary)]"
                aria-hidden="true"
              >
                ✓
              </span>
              <span className="text-[12px] text-text-primary leading-relaxed">
                {fact}
              </span>
            </li>
          ))}
        </ul>

        {/* Open Concerns */}
        <div className="flex items-baseline justify-between gap-3 flex-wrap mb-2">
          <SubLabel className="mb-0">
            Open Concerns — need work
          </SubLabel>
          <span className="text-[11px] text-text-secondary">
            {CONCERNS_OPEN.length} items · sorted by severity
          </span>
        </div>
        <div className="space-y-2">
          {CONCERNS_OPEN.map((c) => (
            <ConcernRow
              key={c.id}
              id={c.id}
              concern={c.concern}
              severity={c.severity}
              effort={c.effort}
              howToFix={c.howToFix}
            />
          ))}
        </div>

        {/* Accepted / Low-Priority */}
        <SubLabel className="mt-6">
          Accepted / Low-Priority
        </SubLabel>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
          {CONCERNS_ACCEPTED.map((c) => (
            <SimpleConcernCard
              key={c.id}
              id={c.id}
              concern={c.concern}
              severity={c.severity}
              note={c.note}
            />
          ))}
        </div>

        {/* Recently Resolved */}
        <SubLabel className="mt-6">
          Recently Resolved (D-192 / D-193) — ✅ DONE
        </SubLabel>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
          {CONCERNS_RESOLVED.map((c) => (
            <div
              key={c.id}
              className="flex items-start gap-3 rounded-[12px] border bg-surface-alt/40 p-3"
              style={{
                borderColor: "color-mix(in srgb, var(--c-success) 30%, var(--c-border))",
              }}
            >
              <span
                className="inline-flex items-center justify-center w-7 h-7 rounded-[8px] shrink-0 text-[13px] font-bold"
                style={{
                  backgroundColor: "color-mix(in srgb, var(--c-success) 15%, transparent)",
                  color: "var(--c-success)",
                  border: "1.5px solid var(--c-success)",
                }}
                aria-hidden="true"
              >
                ✓
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline gap-2 mb-0.5">
                  <span className="font-mono text-[11px] font-semibold text-text-secondary">
                    #{c.id}
                  </span>
                  <span className="text-[12.5px] font-medium text-text-primary">
                    {c.concern}
                  </span>
                </div>
                <div className="text-[11.5px] text-text-secondary leading-snug">
                  <span className="font-medium text-[var(--c-success)]">Resolved by:</span>{" "}
                  {c.resolvedBy}
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Dashboard debt */}
        <SubLabel className="mt-6">Dashboard debt</SubLabel>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
          {CONCERNS_DASHBOARD.map((c) => (
            <SimpleConcernCard
              key={c.id}
              id={c.id}
              concern={c.concern}
              severity={c.severity}
              note={c.note}
            />
          ))}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 5 — DOC DRIFT CAUGHT
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§5 — Doc Drift Caught"
        title="Documentation vs reality"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-danger)" size="sm" />
            {DOC_DRIFT.length} discrepancies
          </span>
        }
      >
        <p className="text-[12px] text-text-secondary leading-relaxed mb-4 max-w-2xl">
          {DOC_DRIFT_INTRO}
        </p>
        <div className="overflow-x-auto -mx-1 px-1">
          <table className="w-full min-w-[640px] text-left border-collapse">
            <thead>
              <tr className="text-[10.5px] uppercase tracking-widest text-text-secondary">
                <Th>What docs say</Th>
                <Th>Actual (verified)</Th>
                <Th>Files affected</Th>
              </tr>
            </thead>
            <tbody>
              {DOC_DRIFT.map((row, i) => (
                <tr
                  key={i}
                  className="border-t border-border hover:bg-canvas/50 transition-colors align-top"
                >
                  <Td className="text-text-secondary whitespace-nowrap">
                    <span className="line-through opacity-70">
                      {row.whatDocsSay}
                    </span>
                  </Td>
                  <Td className="font-mono font-semibold text-[var(--c-danger)] whitespace-nowrap">
                    {row.actual}
                  </Td>
                  <Td className="text-text-secondary">{row.filesAffected}</Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="mt-4 p-3 rounded-[12px] border border-border bg-surface-alt/40 text-[11.5px] text-text-secondary leading-relaxed">
          <span className="font-medium text-text-primary">Root cause:</span>{" "}
          {DOC_DRIFT_ROOT_CAUSE}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 6 — FEATURES REMAINING / BACKLOG
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§6 — Features Remaining / Backlog"
        title="Phase 6+ items"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-warning)" size="sm" />
            {FEATURES_REMAINING.length} groups
          </span>
        }
      >
        <div className="space-y-5">
          {FEATURES_REMAINING.map((group) => (
            <BacklogGroupView key={group.title} group={group} />
          ))}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 7 — FORWARD DIRECTION / RECOMMENDATIONS
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§7 — Forward Direction"
        title="Recommendations (prioritized)"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-primary)" size="sm" />
            {FORWARD_DIRECTION.length} steps
          </span>
        }
      >
        <div className="space-y-3">
          {FORWARD_DIRECTION.map((step) => (
            <div
              key={step.step}
              className="flex items-start gap-4 rounded-[14px] border border-border bg-surface-alt/40 p-4"
            >
              <div
                className="inline-flex items-center justify-center w-9 h-9 rounded-full shrink-0 font-mono text-[14px] font-bold"
                style={{
                  backgroundColor: "color-mix(in srgb, var(--c-primary) 15%, transparent)",
                  color: "var(--c-primary)",
                  border: "1.5px solid var(--c-primary)",
                }}
                aria-hidden="true"
              >
                {step.step}
              </div>
              <div className="min-w-0 flex-1">
                <h3 className="text-[14px] font-bold tracking-extra-tight text-text-primary leading-tight mb-1">
                  {step.title}
                </h3>
                {step.body && (
                  <p className="text-[12.5px] text-text-secondary leading-relaxed mb-2">
                    {step.body}
                  </p>
                )}
                {step.bullets && (
                  <ul className="space-y-1.5">
                    {step.bullets.map((b, i) => (
                      <li
                        key={i}
                        className="flex items-start gap-2 text-[12.5px] text-text-primary leading-relaxed"
                      >
                        <span
                          className="font-mono text-[12px] shrink-0 mt-[1px] text-[var(--c-primary)]"
                          aria-hidden="true"
                        >
                          →
                        </span>
                        <span>{b}</span>
                      </li>
                    ))}
                  </ul>
                )}
                {step.why && (
                  <div className="mt-2.5 pt-2.5 border-t border-border/60 text-[11.5px] text-text-secondary leading-relaxed">
                    <span className="font-medium text-[var(--c-warning)]">Why:</span>{" "}
                    {step.why}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 8 — TOP RISKS
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§8 — Top Risks"
        title="What could bite us"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-danger)" size="sm" />
            {TOP_RISKS.length} risks
          </span>
        }
      >
        <div className="overflow-x-auto -mx-1 px-1">
          <table className="w-full min-w-[640px] text-left border-collapse">
            <thead>
              <tr className="text-[10.5px] uppercase tracking-widest text-text-secondary">
                <Th>Risk</Th>
                <Th>Why it matters</Th>
                <Th>Mitigation</Th>
              </tr>
            </thead>
            <tbody>
              {TOP_RISKS.map((r) => (
                <tr
                  key={r.risk}
                  className="border-t border-border hover:bg-canvas/50 transition-colors align-top"
                >
                  <Td className="whitespace-nowrap">
                    <span className="inline-flex items-center gap-2">
                      <StatusDot color={toneColor(r.tone)} size="sm" />
                      <span className="font-semibold text-text-primary">
                        {r.risk}
                      </span>
                    </span>
                  </Td>
                  <Td className="text-text-secondary">{r.whyItMatters}</Td>
                  <Td className="text-text-primary">{r.mitigation}</Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 9 — FOOTER NOTE
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <div className="flex items-start gap-3 mb-2">
          <span
            className="inline-flex items-center justify-center w-7 h-7 rounded-[8px] shrink-0 text-[12px] font-bold"
            style={{
              backgroundColor: "color-mix(in srgb, var(--c-warning) 15%, transparent)",
              color: "var(--c-warning)",
              border: "1.5px solid var(--c-warning)",
            }}
            aria-hidden="true"
          >
            !
          </span>
          <div>
            <div className="text-[10.5px] font-medium uppercase tracking-widest text-[var(--c-warning)] mb-0.5">
              §9 — Footer Note
            </div>
            <h2 className="text-[16px] font-bold tracking-extra-tight text-text-primary leading-tight">
              Temporary section notice
            </h2>
          </div>
        </div>
        <ul className="space-y-1.5 mt-3">
          {FOOTER_NOTE_BULLETS.map((b, i) => (
            <li
              key={i}
              className="flex items-start gap-2 text-[12px] text-text-secondary leading-relaxed"
            >
              <span
                className="font-mono text-[12px] shrink-0 mt-[1px] text-text-secondary"
                aria-hidden="true"
              >
                ·
              </span>
              <span>{b}</span>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * Sub-components
 * ------------------------------------------------------------------------- */

function SectionCard({
  kicker,
  title,
  right,
  children,
}: {
  kicker: string;
  title: string;
  right?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <Card>
      <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
        <div className="min-w-0">
          <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
            {kicker}
          </div>
          <h2 className="text-[18px] sm:text-[20px] font-bold tracking-extra-tight text-text-primary leading-tight">
            {title}
          </h2>
        </div>
        {right && <div className="max-w-full">{right}</div>}
      </div>
      {children}
    </Card>
  );
}

function SubLabel({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={`text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-2 ${className}`}
    >
      {children}
    </div>
  );
}

function KVRow({
  label,
  value,
  mono = true,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div className="flex items-baseline gap-3">
      <span className="text-[11px] font-medium uppercase tracking-widest text-text-secondary w-[88px] shrink-0">
        {label}
      </span>
      <span
        className={`text-[12.5px] text-text-primary leading-snug break-words ${
          mono ? "font-mono" : ""
        }`}
      >
        {value}
      </span>
    </div>
  );
}

function Th({ children }: { children: React.ReactNode }) {
  return (
    <th className="py-2 pr-3 font-medium text-text-secondary text-[10.5px] uppercase tracking-widest first:pl-0 last:pr-0">
      {children}
    </th>
  );
}

function Td({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <td className={`py-2.5 pr-3 text-[12.5px] leading-snug first:pl-0 last:pr-0 ${className}`}>
      {children}
    </td>
  );
}

function CountPill({
  color,
  count,
  label,
}: {
  color: string;
  count: number;
  label: string;
}) {
  return (
    <span
      className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[11px] font-medium border whitespace-nowrap"
      style={{
        backgroundColor: `color-mix(in srgb, ${color} 10%, transparent)`,
        borderColor: `color-mix(in srgb, ${color} 30%, transparent)`,
        color: color,
      }}
    >
      <StatusDot color={color} size="sm" />
      <span className="font-mono font-semibold tabular-nums">{count}</span>
      <span className="opacity-80">{label}</span>
    </span>
  );
}

function SeverityBadge({ severity }: { severity: Severity }) {
  const meta = SEVERITY_META[severity];
  return (
    <span
      className="inline-flex items-center gap-1.5 h-6 px-2.5 rounded-full text-[10.5px] font-medium border whitespace-nowrap"
      style={{
        backgroundColor: `color-mix(in srgb, ${meta.colorVar} 12%, transparent)`,
        borderColor: `color-mix(in srgb, ${meta.colorVar} 35%, transparent)`,
        color: meta.colorVar,
      }}
    >
      <span
        className="inline-block w-1.5 h-1.5 rounded-full"
        style={{ backgroundColor: meta.colorVar }}
        aria-hidden="true"
      />
      {meta.label}
    </span>
  );
}

function ConcernRow({
  id,
  concern,
  severity,
  effort,
  howToFix,
}: {
  id: number;
  concern: string;
  severity: Severity;
  effort: string;
  howToFix: string;
}) {
  return (
    <div className="rounded-[12px] border border-border bg-surface-alt/40 p-3.5 hover:bg-canvas/40 transition-colors">
      <div className="flex items-start justify-between gap-3 flex-wrap mb-2">
        <div className="flex items-start gap-2.5 min-w-0 flex-1">
          <span className="font-mono text-[12px] font-semibold text-text-secondary shrink-0 mt-[1px]">
            #{id}
          </span>
          <span className="text-[13px] font-semibold text-text-primary leading-snug">
            {concern}
          </span>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <SeverityBadge severity={severity} />
          <span className="inline-flex items-center gap-1 h-6 px-2 rounded-[8px] text-[10.5px] font-medium border bg-surface border-border text-text-secondary whitespace-nowrap">
            <span className="font-mono">⏱</span>
            <span className="font-mono">{effort}</span>
          </span>
        </div>
      </div>
      <div className="pl-7 text-[12px] text-text-secondary leading-relaxed">
        <span className="font-medium text-[var(--c-primary)]">Fix:</span>{" "}
        {howToFix}
      </div>
    </div>
  );
}

function SimpleConcernCard({
  id,
  concern,
  severity,
  note,
}: {
  id: number;
  concern: string;
  severity: Severity;
  note: string;
}) {
  const meta = SEVERITY_META[severity];
  return (
    <div
      className="rounded-[12px] border bg-surface-alt/40 p-3"
      style={{
        borderColor: `color-mix(in srgb, ${meta.colorVar} 25%, var(--c-border))`,
      }}
    >
      <div className="flex items-start gap-2 mb-1.5">
        <span className="font-mono text-[11px] font-semibold text-text-secondary shrink-0 mt-[1px]">
          #{id}
        </span>
        <span className="text-[12.5px] font-medium text-text-primary leading-snug flex-1 min-w-0">
          {concern}
        </span>
        <SeverityBadge severity={severity} />
      </div>
      <div className="pl-5 text-[11.5px] text-text-secondary leading-relaxed">
        {note}
      </div>
    </div>
  );
}

function BacklogGroupView({ group }: { group: BacklogGroup }) {
  return (
    <div className="rounded-[14px] border border-border bg-surface-alt/30 p-4">
      <div className="flex items-baseline justify-between gap-3 flex-wrap mb-3">
        <div>
          <h3 className="text-[14px] font-bold tracking-extra-tight text-text-primary leading-tight">
            {group.title}
          </h3>
          {group.subtitle && (
            <p className="text-[11.5px] text-text-secondary leading-snug mt-0.5">
              {group.subtitle}
            </p>
          )}
        </div>
      </div>

      {group.rows && group.rows.length > 0 && (
        <div className="overflow-x-auto -mx-1 px-1">
          <table className="w-full min-w-[560px] text-left border-collapse">
            <thead>
              <tr className="text-[10px] uppercase tracking-widest text-text-secondary">
                <Th>Feature</Th>
                <Th>Decision</Th>
                <Th>Effort</Th>
                <Th>How to do it</Th>
              </tr>
            </thead>
            <tbody>
              {group.rows.map((r) => (
                <tr
                  key={r.feature}
                  className="border-t border-border hover:bg-canvas/50 transition-colors align-top"
                >
                  <Td className="font-semibold text-text-primary whitespace-nowrap">
                    {r.feature}
                  </Td>
                  <Td className="font-mono text-text-secondary whitespace-nowrap">
                    {r.decision}
                  </Td>
                  <Td className="font-mono text-[var(--c-warning)] whitespace-nowrap">
                    {r.effort}
                  </Td>
                  <Td className="text-text-secondary">{r.howToDoIt}</Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {group.bullets && group.bullets.length > 0 && (
        <ul className="space-y-1.5">
          {group.bullets.map((b, i) => (
            <li
              key={i}
              className="flex items-start gap-2 text-[12.5px] text-text-primary leading-relaxed"
            >
              <span
                className="font-mono text-[12px] shrink-0 mt-[1px] text-[var(--c-warning)]"
                aria-hidden="true"
              >
                ·
              </span>
              <span>
                <span className="font-medium">{b.label}</span>
                {b.note && (
                  <span className="text-text-secondary">
                    {" "}
                    — {b.note}
                  </span>
                )}
              </span>
            </li>
          ))}
        </ul>
      )}

      {group.numbered && group.numbered.length > 0 && (
        <ol className="space-y-1.5">
          {group.numbered.map((n, i) => (
            <li
              key={i}
              className="flex items-start gap-2.5 text-[12.5px] text-text-primary leading-relaxed"
            >
              <span
                className="inline-flex items-center justify-center w-5 h-5 rounded-[6px] font-mono text-[11px] font-bold shrink-0"
                style={{
                  backgroundColor: "color-mix(in srgb, var(--c-primary) 15%, transparent)",
                  color: "var(--c-primary)",
                  border: "1px solid color-mix(in srgb, var(--c-primary) 35%, transparent)",
                }}
                aria-hidden="true"
              >
                {i + 1}
              </span>
              <span>
                <span className="font-medium">{n.label}</span>
                {n.note && (
                  <span className="text-text-secondary">
                    {" "}
                    — {n.note}
                  </span>
                )}
              </span>
            </li>
          ))}
        </ol>
      )}

      {group.footer && (
        <div className="mt-3 pt-2.5 border-t border-border/60 text-[11.5px] text-text-secondary">
          {group.footer}
        </div>
      )}
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * Helpers
 * ------------------------------------------------------------------------- */

function toneColor(
  tone: "good" | "warning" | "danger" | "secondary",
): string {
  switch (tone) {
    case "good":
      return "var(--c-success)";
    case "warning":
      return "var(--c-warning)";
    case "danger":
      return "var(--c-danger)";
    case "secondary":
      return "var(--c-secondary)";
  }
}

function accentColor(
  accent: "primary" | "success" | "warning" | "secondary" | "danger",
): string {
  switch (accent) {
    case "primary":
      return "var(--c-primary)";
    case "success":
      return "var(--c-success)";
    case "warning":
      return "var(--c-warning)";
    case "secondary":
      return "var(--c-secondary)";
    case "danger":
      return "var(--c-danger)";
  }
}
