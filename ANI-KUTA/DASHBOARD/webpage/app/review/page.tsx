import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import {
  DOC_DRIFT,
  FEATURE_STATUS_META,
  FEATURES_REMAINING,
  FOOTER_NOTE_BULLETS,
  HEALTH_STATUS_META,
  OPEN_CONCERNS,
  PROJECT_HEALTH,
  REVIEW_META,
  SEVERITIES,
  SEVERITY_META,
  SNAPSHOT,
  STATUS_TONE_META,
  TOP_RISKS,
  VERIFIED_FIXED,
  WHATS_BUILT,
  type FeatureStatus,
  type HealthStatus,
  type Severity,
} from "@/lib/reviewData";

/**
 * /review/ — Review & Roadmap (full project review).
 *
 * Renders the 2026-08-25 full-project review #3 (main agent + 5 read-only
 * research sub-agents, every metric re-derived from source) for the
 * test-feature/video-cache-new-download branch @ be743679 / v0.2.51,
 * per DESIGN.md (MEMORY OS v3). Static Server Component — no
 * interactivity needed, no "use client".
 *
 * TEMPORARY SECTION — replaces the deleted /key-findings/ page
 * (review #2, 2026-08-24). See §9 Footer Note.
 *
 * Sections (counts are dynamic — driven by .length on the data arrays):
 *  1. Snapshot (verified metrics)
 *  2. Project Health (verdict + 6 indicators)
 *  3. What's Built (13 branch highlights)
 *  4. Open Concerns (15 items grouped by severity)
 *  5. Verified Fixed (14 resolved concerns — balance)
 *  6. Doc Drift Caught (top 12 of ~60 stale claims)
 *  7. Features Remaining (NOW / NEXT / LATER — 29 total)
 *  8. Top Risks (8 rows)
 *  9. Footer Note (temporary-section notice)
 */

/** Impact-level → semantic colour (for the §8 risk table). */
const IMPACT_COLOR: Record<string, string> = {
  High: "var(--c-warning)",
  Medium: "var(--c-secondary)",
  Low: "var(--c-text-secondary)",
};

export default function ReviewPage() {
  const totalRemaining =
    FEATURES_REMAINING.now.items.length +
    FEATURES_REMAINING.next.items.length +
    FEATURES_REMAINING.later.items.length;

  return (
    <div className="space-y-6">
      {/* ───────────────────────────────────────────────────────────────
       *  HERO
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-3">
          <div className="min-w-0">
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1.5">
              Agent Review · {REVIEW_META.reviewDate}
            </div>
            <h1 className="text-[22px] sm:text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
              {REVIEW_META.title}
            </h1>
          </div>
          <div className="flex items-center gap-2 flex-wrap max-w-full">
            {REVIEW_META.statusPills.map((pill, i) => {
              const tone = STATUS_TONE_META[pill.tone];
              return (
                <span
                  key={pill.label}
                  className={`inline-flex items-center gap-1.5 min-h-7 py-1 px-3 rounded-full text-[11px] font-semibold border ${i === 0 ? "font-mono" : ""}`}
                  style={{
                    backgroundColor: `color-mix(in srgb, ${tone.colorVar} 10%, transparent)`,
                    borderColor: `color-mix(in srgb, ${tone.colorVar} 35%, transparent)`,
                    color: tone.colorVar,
                  }}
                >
                  <StatusDot color={tone.colorVar} size="sm" />
                  {pill.label}
                </span>
              );
            })}
          </div>
        </div>
        <p className="text-[12.5px] sm:text-[13.5px] text-text-secondary leading-[1.5] max-w-2xl">
          {REVIEW_META.description}
        </p>
        <p className="text-[11.5px] text-text-secondary leading-relaxed mt-3 pt-3 border-t border-border/60 break-words">
          <span className="font-medium text-text-primary">Reviewer:</span>{" "}
          {REVIEW_META.reviewer}
          <span className="mx-2 text-border">·</span>
          <span className="font-medium text-text-primary">Method:</span>{" "}
          {REVIEW_META.method}
        </p>
      </Card>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 1 — SNAPSHOT
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§1 — Snapshot"
        title="Verified metrics"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-success)" size="sm" />
            {SNAPSHOT.metrics.length} metrics · source-verified
          </span>
        }
      >
        <SubLabel>Every value below was re-derived from the repo, not the docs</SubLabel>
        <div className="overflow-x-auto -mx-1 px-1">
          <table className="w-full min-w-[560px] text-left border-collapse">
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
                  className="border-t border-border hover:bg-canvas/50 transition-colors align-top"
                >
                  <Td className="font-medium text-text-primary">{m.metric}</Td>
                  <Td className="font-mono font-semibold text-text-primary">
                    {m.value}
                  </Td>
                  <Td className="text-text-secondary">{m.note}</Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 2 — PROJECT HEALTH
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§2 — Project Health"
        title={`Verdict + ${PROJECT_HEALTH.indicators.length} indicators`}
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-warning)" size="sm" />
            {PROJECT_HEALTH.indicators.length} indicators
          </span>
        }
      >
        <div
          className="rounded-[14px] border p-4 mb-4"
          style={{
            backgroundColor: "color-mix(in srgb, var(--c-warning) 8%, transparent)",
            borderColor: "color-mix(in srgb, var(--c-warning) 30%, transparent)",
          }}
        >
          <div className="text-[10.5px] font-medium uppercase tracking-widest text-[var(--c-warning)] mb-2">
            Verdict
          </div>
          <div className="text-[16px] sm:text-[18px] font-bold tracking-extra-tight text-text-primary mb-2 leading-tight">
            {PROJECT_HEALTH.verdictHeadline}
          </div>
          <div className="text-[12px] text-text-secondary leading-relaxed">
            {PROJECT_HEALTH.verdictBody}
          </div>
        </div>

        <div className="space-y-2.5">
          {PROJECT_HEALTH.indicators.map((h) => (
            <div
              key={h.area}
              className="flex items-start justify-between gap-3 flex-wrap rounded-[14px] border border-border bg-surface-alt/40 p-3.5"
            >
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2 mb-0.5">
                  <StatusDot
                    color={HEALTH_STATUS_META[h.status].colorVar}
                    size="md"
                  />
                  <span className="text-[13px] font-bold tracking-extra-tight text-text-primary">
                    {h.area}
                  </span>
                </div>
                <p className="text-[12px] text-text-secondary leading-relaxed min-w-0 break-words">
                  {h.line}
                </p>
              </div>
              <StatusPill status={h.status} />
            </div>
          ))}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 3 — WHAT'S BUILT (BRANCH HIGHLIGHTS)
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§3 — What's Built"
        title={`${WHATS_BUILT.length} branch highlights`}
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-success)" size="sm" />
            {WHATS_BUILT.length} highlights
          </span>
        }
      >
        <SubLabel>Everything landed on this branch since it forked from main</SubLabel>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {WHATS_BUILT.map((a, i) => (
            <div
              key={a.area}
              className="rounded-[14px] border border-border bg-surface-alt/40 p-3.5"
            >
              <div className="flex items-baseline gap-2 mb-1.5">
                <span className="font-mono text-[11px] font-semibold text-text-secondary tabular-nums shrink-0">
                  {String(i + 1).padStart(2, "0")}
                </span>
                <span className="text-[13px] font-bold tracking-extra-tight text-text-primary min-w-0 break-words">
                  {a.area}
                </span>
              </div>
              <div className="mb-2">
                <span className="inline-flex items-center min-h-6 py-0.5 px-2.5 rounded-full text-[10.5px] font-mono font-medium bg-chip border border-border text-text-secondary">
                  {a.ref}
                </span>
              </div>
              <p className="text-[11.5px] text-text-secondary leading-relaxed min-w-0 break-words">
                {a.detail}
              </p>
            </div>
          ))}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 4 — OPEN CONCERNS
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§4 — Open Concerns"
        title={`${OPEN_CONCERNS.length} items, grouped by severity`}
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-danger)" size="sm" />
            {OPEN_CONCERNS.length} concerns
          </span>
        }
      >
        <p className="text-[12.5px] text-text-secondary leading-relaxed mb-4">
          Every concern carries verified evidence (file:line where relevant)
          plus an area tag. Low-severity items are accepted limitations or
          deferred work — listed for completeness, no action required now.
        </p>

        {SEVERITIES.map((sev) => {
          const group = OPEN_CONCERNS.map((c, i) => ({
            ...c,
            n: i + 1,
          })).filter((c) => c.severity === sev);
          if (group.length === 0) return null;
          return (
            <div key={sev} className="space-y-3 mb-5 last:mb-0">
              <div className="flex items-center gap-2.5 flex-wrap">
                <SeverityPill severity={sev} />
                <span className="text-[11.5px] text-text-secondary">
                  {group.length} {group.length === 1 ? "item" : "items"}
                </span>
              </div>
              {group.map((c) => (
                <ConcernCard key={c.n} concern={c} />
              ))}
            </div>
          );
        })}
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 5 — VERIFIED FIXED
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§5 — Verified Fixed"
        title={`Balance — ${VERIFIED_FIXED.length} concerns resolved`}
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-success)" size="sm" />
            {VERIFIED_FIXED.length} verified
          </span>
        }
      >
        <p className="text-[12.5px] text-text-secondary leading-relaxed mb-4">
          For balance: concerns from earlier reviews that have since been
          verified as fixed in code — swept since the docs were last updated.
        </p>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          {VERIFIED_FIXED.map((f) => (
            <div
              key={f.concern}
              className="rounded-[14px] border border-border bg-surface-alt/40 p-3.5"
            >
              <div className="flex items-start gap-2.5">
                <span
                  className="inline-flex items-center justify-center w-6 h-6 rounded-[8px] text-[12px] font-bold shrink-0"
                  style={{
                    backgroundColor: "color-mix(in srgb, var(--c-success) 15%, transparent)",
                    color: "var(--c-success)",
                    border: "1.5px solid var(--c-success)",
                  }}
                  aria-hidden="true"
                >
                  ✓
                </span>
                <div className="min-w-0">
                  <div className="text-[12.5px] font-semibold text-text-primary leading-snug min-w-0 break-words">
                    {f.concern}
                  </div>
                  <div className="text-[11.5px] text-text-secondary leading-relaxed mt-0.5 min-w-0 break-words">
                    {f.evidence}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 6 — DOC DRIFT CAUGHT
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§6 — Doc Drift Caught"
        title={`Top ${DOC_DRIFT.rows.length} of ${DOC_DRIFT.totalStaleClaims} stale claims vs verified reality`}
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-warning)" size="sm" />
            {DOC_DRIFT.rows.length} of {DOC_DRIFT.totalStaleClaims} · {DOC_DRIFT.filesAffected} files
          </span>
        }
      >
        <div className="overflow-x-auto -mx-1 px-1">
          <table className="w-full min-w-[560px] text-left border-collapse">
            <thead>
              <tr className="text-[10.5px] uppercase tracking-widest text-text-secondary">
                <Th>File</Th>
                <Th>Stale claim</Th>
                <Th>Verified reality</Th>
              </tr>
            </thead>
            <tbody>
              {DOC_DRIFT.rows.map((d) => (
                <tr
                  key={d.claim}
                  className="border-t border-border hover:bg-canvas/50 transition-colors align-top"
                >
                  <Td className="font-mono text-[11px] text-text-secondary break-words">
                    {d.file}
                  </Td>
                  <Td className="text-text-secondary">{d.claim}</Td>
                  <Td className="font-medium text-text-primary">
                    {d.reality}
                  </Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 7 — FEATURES REMAINING
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§7 — Features Remaining"
        title="Forward direction — NOW / NEXT / LATER"
        right={
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            <StatusDot color="var(--c-primary)" size="sm" />
            {totalRemaining} items
          </span>
        }
      >
        {(
          [
            {
              group: FEATURES_REMAINING.now,
              colorVar: "var(--c-primary)",
              startAt: 1,
            },
            {
              group: FEATURES_REMAINING.next,
              colorVar: "var(--c-secondary)",
              startAt: 1 + FEATURES_REMAINING.now.items.length,
            },
            {
              group: FEATURES_REMAINING.later,
              colorVar: "var(--c-text-secondary)",
              startAt:
                1 +
                FEATURES_REMAINING.now.items.length +
                FEATURES_REMAINING.next.items.length,
            },
          ] as const
        ).map(({ group, colorVar, startAt }) => (
          <div
            key={group.label}
            className="rounded-[14px] border border-border bg-surface-alt/30 p-4 mb-4 last:mb-0"
          >
            <div className="flex items-center justify-between gap-3 flex-wrap mb-3">
              <div className="flex items-baseline gap-2.5 min-w-0 flex-wrap">
                <h3
                  className="text-[14px] font-bold tracking-extra-tight leading-tight"
                  style={{ color: colorVar }}
                >
                  {group.label}
                </h3>
                <span className="inline-flex items-center min-h-6 py-1 px-2.5 rounded-full text-[10.5px] font-medium bg-chip border border-border text-text-secondary">
                  {group.timeframe}
                </span>
              </div>
              <span className="text-[11px] text-text-secondary tabular-nums shrink-0">
                {group.items.length} {group.items.length === 1 ? "item" : "items"}
              </span>
            </div>

            <div className="space-y-3">
              {group.items.map((f, idx) => (
                <div
                  key={f.name}
                  className="flex items-start justify-between gap-3 flex-wrap"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-baseline gap-2 min-w-0 flex-wrap">
                      <span className="font-mono text-[11px] font-semibold text-text-secondary tabular-nums shrink-0">
                        {String(startAt + idx).padStart(2, "0")}
                      </span>
                      <span className="text-[12.5px] font-semibold text-text-primary leading-snug min-w-0 break-words">
                        {f.name}
                      </span>
                      {f.status && <FeatureStatusPill status={f.status} />}
                    </div>
                    <p className="text-[11.5px] text-text-secondary leading-relaxed mt-0.5 min-w-0 break-words">
                      {f.how}
                    </p>
                  </div>
                  {f.effort && (
                    <span className="inline-flex items-center min-h-6 py-1 px-2.5 rounded-full text-[10.5px] font-mono font-medium bg-chip border border-border text-text-secondary shrink-0">
                      {f.effort}
                    </span>
                  )}
                </div>
              ))}
            </div>
          </div>
        ))}
      </SectionCard>

      {/* ───────────────────────────────────────────────────────────────
       *  SECTION 8 — TOP RISKS
       * ─────────────────────────────────────────────────────────────── */}
      <SectionCard
        kicker="§8 — Top Risks"
        title={`${TOP_RISKS.length} risks worth tracking`}
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
                <Th>Impact</Th>
                <Th>Likelihood</Th>
                <Th>Mitigation</Th>
              </tr>
            </thead>
            <tbody>
              {TOP_RISKS.map((r) => {
                const impactColor =
                  IMPACT_COLOR[r.impact] ?? "var(--c-text-secondary)";
                return (
                  <tr
                    key={r.risk}
                    className="border-t border-border hover:bg-canvas/50 transition-colors align-top"
                  >
                    <Td className="font-medium text-text-primary">{r.risk}</Td>
                    <Td>
                      <span
                        className="inline-flex items-center gap-1.5 font-semibold"
                        style={{ color: impactColor }}
                      >
                        <span
                          className="inline-block w-1.5 h-1.5 rounded-full shrink-0"
                          style={{ backgroundColor: impactColor }}
                          aria-hidden="true"
                        />
                        {r.impact}
                      </span>
                    </Td>
                    <Td className="text-text-secondary">{r.likelihood}</Td>
                    <Td className="text-text-secondary min-w-0 break-words">
                      {r.mitigation}
                    </Td>
                  </tr>
                );
              })}
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
              backgroundColor: "color-mix(in srgb, var(--c-secondary) 15%, transparent)",
              color: "var(--c-secondary)",
              border: "1.5px solid var(--c-secondary)",
            }}
            aria-hidden="true"
          >
            !
          </span>
          <div className="min-w-0">
            <div className="text-[10.5px] font-medium uppercase tracking-widest text-[var(--c-secondary)] mb-0.5">
              §9 — Footer Note
            </div>
            <h2 className="text-[16px] font-bold tracking-extra-tight text-text-primary leading-tight">
              Temporary section
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
              <span className="min-w-0 break-words">{b}</span>
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

function Th({ children }: { children: React.ReactNode }) {
  return (
    <th className="py-2 pr-3 font-medium text-text-secondary text-[10px] uppercase tracking-widest first:pl-0 last:pr-0">
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
    <td className={`py-2.5 pr-3 text-[12px] leading-snug first:pl-0 last:pr-0 ${className}`}>
      {children}
    </td>
  );
}

/** Colored pill for a health indicator status (§2). */
function StatusPill({ status }: { status: HealthStatus }) {
  const meta = HEALTH_STATUS_META[status];
  return (
    <span
      className="inline-flex items-center gap-1.5 min-h-7 py-1 px-3 rounded-full text-[10.5px] font-semibold uppercase tracking-wide border shrink-0"
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
      {status}
    </span>
  );
}

/** Colored pill for a concern severity (§4). */
function SeverityPill({ severity }: { severity: Severity }) {
  const meta = SEVERITY_META[severity];
  return (
    <span
      className="inline-flex items-center gap-1.5 min-h-7 py-1 px-3 rounded-full text-[10.5px] font-semibold uppercase tracking-wide border"
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

/** Colored pill for a remaining-feature status (§7). */
function FeatureStatusPill({ status }: { status: FeatureStatus }) {
  const meta = FEATURE_STATUS_META[status];
  return (
    <span
      className="inline-flex items-center gap-1.5 min-h-6 py-0.5 px-2.5 rounded-full text-[10px] font-semibold uppercase tracking-wide border shrink-0"
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
      {status}
    </span>
  );
}

/** One open-concern card (§4) — severity-tinted border + detail + area tag. */
function ConcernCard({
  concern,
}: {
  concern: {
    n: number;
    severity: Severity;
    title: string;
    detail: string;
    area: string;
  };
}) {
  const meta = SEVERITY_META[concern.severity];
  return (
    <div
      className="rounded-[14px] border bg-surface-alt/40 p-4"
      style={{
        borderColor: `color-mix(in srgb, ${meta.colorVar} 25%, var(--c-border))`,
      }}
    >
      <div className="flex items-start justify-between gap-3 flex-wrap mb-1.5">
        <div className="flex items-start gap-2.5 min-w-0">
          <span className="font-mono text-[12px] font-semibold text-text-secondary shrink-0 mt-[3px] tabular-nums">
            #{concern.n}
          </span>
          <h3 className="text-[14px] font-bold tracking-extra-tight text-text-primary leading-tight min-w-0 break-words">
            {concern.title}
          </h3>
        </div>
        <SeverityPill severity={concern.severity} />
      </div>
      <p className="text-[12.5px] text-text-secondary leading-relaxed min-w-0 break-words">
        {concern.detail}
      </p>
      <div className="mt-2.5 pt-2.5 border-t border-border/60">
        <span className="inline-flex items-center min-h-6 py-0.5 px-2.5 rounded-full text-[10.5px] font-mono font-medium bg-chip border border-border text-text-secondary">
          {concern.area}
        </span>
      </div>
    </div>
  );
}
