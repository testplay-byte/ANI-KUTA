import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import {
  HERO,
  HEALTH_ROWS,
  BUILT_ITEMS,
  CONCERNS,
  DOC_DRIFT_ITEMS,
  FEATURES_DESIGNED,
  FEATURES_NEEDS_VERIFICATION,
  FEATURES_DEFERRED_DOWNLOAD_GAPS,
  RECOMMENDATIONS,
  RISKS,
  FOOTER_NOTE,
  SEVERITY_META,
  CONCERN_STATUS_META,
  type Severity,
  type Concern,
} from "@/lib/projectReview";

/* ---------------------------------------------------------------------------
 * Project Review page (temporary, additively-added).
 *
 * Renders the live codebase review findings collected this session.
 * Uses Card + StatusDot components + Tailwind utilities only —
 * no new shared components. Follows DESIGN.md §5.2 hero + §5.3 cards.
 * ------------------------------------------------------------------------- */
export default function ProjectReviewPage() {
  return (
    <div className="space-y-6">
      {/* ── Section 1 — Hero / snapshot ────────────────────────────────── */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-3">
          <div className="min-w-0">
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1.5">
              {HERO.kicker}
            </div>
            <h1 className="text-[22px] sm:text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
              {HERO.title}
            </h1>
          </div>
          <span
            className="inline-flex items-center gap-1.5 py-1.5 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary max-w-full"
            title={`Latest commit on main: ${HERO.commitHash}`}
          >
            <StatusDot color="var(--c-success)" size="sm" />
            {HERO.reviewedBadge} · {HERO.commitLabel}
            <span className="font-mono text-text-secondary ml-1">
              {HERO.commitHash}
            </span>
          </span>
        </div>
        <p className="text-[12.5px] sm:text-[13.5px] text-text-secondary leading-[1.5] max-w-2xl">
          {HERO.description}
        </p>

        {/* Metric pills row */}
        <div className="mt-5 flex flex-wrap gap-2">
          {HERO.metrics.map((m) => (
            <span
              key={m.label}
              className="inline-flex items-baseline gap-1.5 h-7 px-3 rounded-full text-[11.5px] border bg-surface border-border"
            >
              <span className="font-mono font-semibold text-text-primary">
                {m.value}
              </span>
              <span className="text-text-secondary">{m.label}</span>
            </span>
          ))}
        </div>
      </Card>

      {/* ── Section 2 — Project health summary ─────────────────────────── */}
      <Card>
        <SectionHeader
          kicker="Health Summary"
          title="Project Health"
          right={
            <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
              <StatusDot color="var(--c-success)" size="sm" />
              2 green · 1 amber · 1 red
            </span>
          }
        />
        <ul className="space-y-3 mt-1">
          {HEALTH_ROWS.map((row, i) => (
            <li key={i} className="flex items-start gap-3">
              <span className="mt-1.5">
                <StatusDot color={row.colorVar} size="md" />
              </span>
              <div className="min-w-0 flex-1">
                <div className="text-[13.5px] font-semibold text-text-primary leading-snug">
                  {row.title}
                </div>
                <div className="text-[12px] text-text-secondary leading-relaxed mt-0.5">
                  {row.detail}
                </div>
              </div>
            </li>
          ))}
        </ul>
      </Card>

      {/* ── Section 3 — What's built (compact grid) ─────────────────────── */}
      <Card>
        <SectionHeader
          kicker="What's Built"
          title="Systems Already Shipped"
          right={
            <span className="text-[11px] text-text-secondary">
              {BUILT_ITEMS.length} systems · all merged to main
            </span>
          }
        />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mt-1">
          {BUILT_ITEMS.map((item) => (
            <div
              key={item.group}
              className="rounded-[12px] border border-border bg-surface p-3.5"
            >
              <div className="flex items-center gap-2 mb-1">
                <StatusDot color="var(--c-success)" size="sm" />
                <h3 className="text-[13px] font-semibold text-text-primary">
                  {item.group}
                </h3>
              </div>
              <p className="text-[12px] text-text-secondary leading-relaxed">
                {item.detail}
              </p>
            </div>
          ))}
        </div>
      </Card>

      {/* ── Section 4 — Concerns & issues (severity-grouped) ───────────── */}
      <Card>
        <SectionHeader
          kicker="Concerns & Issues"
          title="Deferred Concerns"
          right={
            <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
              <StatusDot color="var(--c-danger)" size="sm" />
              {CONCERNS.length} tracked · {countByStatus("open")} open ·{" "}
              {countByStatus("partially-fixed")} partial
            </span>
          }
        />
        <p className="text-[12.5px] text-text-secondary leading-relaxed max-w-3xl mb-5">
          {CONCERNS.length} deferred concerns tracked in{" "}
          <code className="font-mono text-[11.5px] px-1 py-0.5 rounded bg-surface-alt border border-border">
            progress.md
          </code>
          . Grouped by severity below. Several were partially addressed by{" "}
          <span className="font-mono">D-192</span> +{" "}
          <span className="font-mono">D-193 v2</span> — status noted.
        </p>

        {/* Severity legend */}
        <div className="flex flex-wrap items-center gap-3 mb-4 text-[11px] text-text-secondary">
          {(Object.keys(SEVERITY_META) as Severity[]).map((s) => {
            const count = CONCERNS.filter((c) => c.severity === s).length;
            const meta = SEVERITY_META[s];
            return (
              <span key={s} className="inline-flex items-center gap-1.5">
                <StatusDot color={meta.colorVar} size="sm" />
                <span className="uppercase tracking-wide font-medium">
                  {meta.label}
                </span>
                <span className="font-mono tabular-nums">({count})</span>
              </span>
            );
          })}
        </div>

        <div className="space-y-6">
          {(Object.keys(SEVERITY_META) as Severity[]).map((sev) => {
            const items = CONCERNS.filter((c) => c.severity === sev);
            if (items.length === 0) return null;
            const meta = SEVERITY_META[sev];
            return (
              <div key={sev}>
                <div className="flex items-center gap-2 mb-2.5">
                  <span
                    className="inline-block w-1 h-4 rounded-full"
                    style={{ backgroundColor: meta.colorVar }}
                    aria-hidden="true"
                  />
                  <h3
                    className="text-[11px] font-bold uppercase tracking-widest"
                    style={{ color: meta.colorVar }}
                  >
                    {meta.label}
                  </h3>
                  <span className="text-[11px] text-text-secondary">
                    — {severityBlurb(sev)}
                  </span>
                </div>
                <div className="space-y-2">
                  {items.map((c) => (
                    <ConcernRow key={c.id} concern={c} />
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      </Card>

      {/* ── Section 5 — Doc-drift callout (amber accent) ───────────────── */}
      <Card className="!border-[var(--c-warning)]/40">
        <SectionHeader
          kicker="Doc-Drift"
          title="Doc-Drift Caught This Session"
          right={
            <span
              className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border"
              style={{
                backgroundColor:
                  "color-mix(in srgb, var(--c-warning) 12%, transparent)",
                borderColor:
                  "color-mix(in srgb, var(--c-warning) 40%, transparent)",
                color: "var(--c-warning)",
              }}
            >
              {DOC_DRIFT_ITEMS.length} mismatches
            </span>
          }
        />
        <p className="text-[12.5px] text-text-secondary leading-relaxed max-w-3xl mb-4">
          The codebase review found several doc/reality mismatches. These are{" "}
          <span className="text-text-primary font-medium">NOT code bugs</span>{" "}
          — they're doc-staleness. Flagging so the user knows the docs overstate
          by a small margin.
        </p>
        <ul className="space-y-2">
          {DOC_DRIFT_ITEMS.map((item, i) => (
            <li
              key={i}
              className="flex items-start gap-3 rounded-[10px] border border-border bg-surface-alt px-3.5 py-2.5"
            >
              <span
                className="mt-1 inline-flex items-center justify-center w-5 h-5 rounded-[6px] text-[10px] font-bold shrink-0"
                style={{
                  backgroundColor:
                    "color-mix(in srgb, var(--c-warning) 18%, transparent)",
                  color: "var(--c-warning)",
                }}
                aria-hidden="true"
              >
                !
              </span>
              <p className="text-[12.5px] text-text-secondary leading-relaxed flex-1">
                {renderMonoSpans(item.text, item.mono)}
              </p>
            </li>
          ))}
        </ul>
      </Card>

      {/* ── Section 6 — Features remaining ─────────────────────────────── */}
      <Card>
        <SectionHeader
          kicker="Features Remaining"
          title="Phase 6+ Backlog"
          right={
            <span className="text-[11px] text-text-secondary">
              {FEATURES_DESIGNED.length} designed ·{" "}
              {FEATURES_NEEDS_VERIFICATION.length} need device test ·{" "}
              {FEATURES_DEFERRED_DOWNLOAD_GAPS.length} download gaps
            </span>
          }
        />

        <FeatureSubGroup
          label="A"
          title="Designed but not built"
          items={FEATURES_DESIGNED}
          accent="var(--c-primary)"
        />
        <FeatureSubGroup
          label="B"
          title="Built but needs on-device verification"
          items={FEATURES_NEEDS_VERIFICATION}
          accent="var(--c-warning)"
        />
        <FeatureSubGroup
          label="C"
          title="Deferred download gaps"
          items={FEATURES_DEFERRED_DOWNLOAD_GAPS}
          accent="var(--c-danger)"
          footnote="Full plan in download-research/FUTURE-PHASE-DL-GAPS.md"
        />
      </Card>

      {/* ── Section 7 — Recommended forward direction ──────────────────── */}
      <Card>
        <SectionHeader
          kicker="Forward Direction"
          title="Recommended Next Steps"
          right={
            <span className="text-[11px] text-text-secondary">
              {RECOMMENDATIONS.length} ordered priorities
            </span>
          }
        />
        <ol className="space-y-3 mt-1">
          {RECOMMENDATIONS.map((rec, i) => (
            <li key={i} className="flex items-start gap-3">
              <span
                className="inline-flex items-center justify-center w-6 h-6 rounded-full text-[12px] font-bold text-white shrink-0 mt-0.5"
                style={{
                  backgroundColor: "var(--c-primary)",
                  boxShadow:
                    "0 2px 8px color-mix(in srgb, var(--c-primary) 40%, transparent)",
                }}
                aria-hidden="true"
              >
                {i + 1}
              </span>
              <div className="min-w-0 flex-1">
                <div className="text-[13.5px] font-semibold text-text-primary leading-snug">
                  {rec.title}
                </div>
                <div className="text-[12px] text-text-secondary leading-relaxed mt-0.5">
                  {rec.detail}
                </div>
              </div>
            </li>
          ))}
        </ol>
      </Card>

      {/* ── Section 8 — Top risks ───────────────────────────────────────── */}
      <Card>
        <SectionHeader
          kicker="Top Risks"
          title="Risks to Watch"
          right={
            <span className="text-[11px] text-text-secondary">
              from lessons-learned.md
            </span>
          }
        />
        <ul className="space-y-2.5 mt-1">
          {RISKS.map((risk, i) => (
            <li
              key={i}
              className="rounded-[12px] border border-border bg-surface p-3.5"
            >
              <div className="flex items-start gap-2.5">
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="var(--c-warning)"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="shrink-0 mt-0.5"
                  aria-hidden="true"
                >
                  <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                  <line x1="12" y1="9" x2="12" y2="13" />
                  <line x1="12" y1="17" x2="12.01" y2="17" />
                </svg>
                <div className="min-w-0 flex-1">
                  <div className="text-[13px] font-semibold text-text-primary leading-snug">
                    {risk.title}
                  </div>
                  <div className="text-[12px] text-text-secondary leading-relaxed mt-0.5">
                    {risk.detail}
                  </div>
                  <div className="text-[11.5px] text-text-secondary leading-relaxed mt-1.5 pl-2.5 border-l-2 border-border">
                    <span className="font-medium text-text-primary">
                      Mitigation:{" "}
                    </span>
                    {risk.mitigation}
                  </div>
                </div>
              </div>
            </li>
          ))}
        </ul>
      </Card>

      {/* ── Section 9 — Page footer note ───────────────────────────────── */}
      <p className="text-[11.5px] text-text-secondary leading-relaxed text-center px-4 py-2">
        {FOOTER_NOTE}
      </p>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * Helpers
 * ------------------------------------------------------------------------- */

function countByStatus(
  status: keyof typeof CONCERN_STATUS_META,
): number {
  return CONCERNS.filter((c) => c.status === status).length;
}

function severityBlurb(sev: Severity): string {
  switch (sev) {
    case "high":
      return "functional gaps";
    case "medium":
      return "architectural debt";
    case "low":
      return "debug-acceptable";
    case "expected":
      return "placeholder by design";
  }
}

function ConcernRow({ concern }: { concern: Concern }) {
  const sevMeta = SEVERITY_META[concern.severity];
  const statusMeta = CONCERN_STATUS_META[concern.status];
  return (
    <div className="flex items-start gap-3 rounded-[12px] border border-border bg-surface p-3.5">
      <span className="mt-1.5">
        <StatusDot color={sevMeta.colorVar} size="md" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-3 flex-wrap">
          <div className="min-w-0 flex-1">
            <div className="text-[13px] font-semibold text-text-primary leading-snug">
              <span className="font-mono text-text-secondary mr-1.5">
                #{concern.id}
              </span>
              {concern.title}
            </div>
            <div className="text-[12px] text-text-secondary leading-relaxed mt-0.5">
              {concern.detail}
            </div>
            {(concern.statusNote || concern.estEffort) && (
              <div className="flex flex-wrap items-center gap-2 mt-1.5 text-[11px] text-text-secondary">
                {concern.statusNote && (
                  <span className="font-mono">
                    <span className="text-text-secondary">ref:</span>{" "}
                    <span className="text-text-primary">
                      {concern.statusNote}
                    </span>
                  </span>
                )}
                {concern.estEffort && (
                  <span className="inline-flex items-center gap-1">
                    <span className="text-text-secondary">est.:</span>
                    <span className="font-mono text-text-primary">
                      {concern.estEffort}
                    </span>
                  </span>
                )}
              </div>
            )}
          </div>
          <span
            className="inline-flex items-center gap-1.5 h-6 px-2.5 rounded-full text-[10.5px] font-medium border whitespace-nowrap shrink-0"
            style={{
              backgroundColor: `color-mix(in srgb, ${statusMeta.colorVar} 12%, transparent)`,
              borderColor: `color-mix(in srgb, ${statusMeta.colorVar} 35%, transparent)`,
              color: statusMeta.colorVar,
            }}
          >
            <span
              className="inline-block w-1.5 h-1.5 rounded-full"
              style={{ backgroundColor: statusMeta.colorVar }}
              aria-hidden="true"
            />
            {statusMeta.label}
          </span>
        </div>
      </div>
    </div>
  );
}

function FeatureSubGroup({
  label,
  title,
  items,
  accent,
  footnote,
}: {
  label: string;
  title: string;
  items: { name: string; ref?: string; how: string }[];
  accent: string;
  footnote?: string;
}) {
  return (
    <div className="mb-5 last:mb-0">
      <div className="flex items-center gap-2 mb-2.5">
        <span
          className="inline-flex items-center justify-center w-5 h-5 rounded-[6px] text-[11px] font-bold text-white shrink-0"
          style={{ backgroundColor: accent }}
          aria-hidden="true"
        >
          {label}
        </span>
        <h3 className="text-[13px] font-semibold text-text-primary">{title}</h3>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5">
        {items.map((item, i) => (
          <div
            key={i}
            className="rounded-[10px] border border-border bg-surface px-3.5 py-2.5"
          >
            <div className="flex items-center gap-2 flex-wrap mb-0.5">
              <span className="text-[12.5px] font-semibold text-text-primary">
                {item.name}
              </span>
              {item.ref && (
                <span className="font-mono text-[10.5px] px-1.5 py-0.5 rounded-[6px] bg-surface-alt border border-border text-text-secondary">
                  {item.ref}
                </span>
              )}
            </div>
            <p className="text-[11.5px] text-text-secondary leading-relaxed">
              {item.how}
            </p>
          </div>
        ))}
      </div>
      {footnote && (
        <p className="text-[11px] text-text-secondary mt-2 font-mono">
          ↳ {footnote}
        </p>
      )}
    </div>
  );
}

function SectionHeader({
  kicker,
  title,
  right,
}: {
  kicker: string;
  title: string;
  right?: React.ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
      <div className="min-w-0">
        <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1.5">
          {kicker}
        </div>
        <h2 className="text-[20px] font-bold tracking-extra-tight text-text-primary leading-tight">
          {title}
        </h2>
      </div>
      {right && <div className="shrink-0">{right}</div>}
    </div>
  );
}

/**
 * Render substrings in `text` as monospace spans (for file paths, line
 * numbers, decision IDs, commit hashes). Naive non-overlapping replacement.
 */
function renderMonoSpans(
  text: string,
  monos: string[],
): React.ReactNode {
  if (monos.length === 0) return text;
  // Build a regex of all mono substrings (escape regex metachars).
  const escaped = monos
    .map((m) => m.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
    .sort((a, b) => b.length - a.length); // longer first
  const re = new RegExp(`(${escaped.join("|")})`, "g");
  const parts = text.split(re);
  return parts.map((part, i) =>
    monos.includes(part) ? (
      <code
        key={i}
        className="font-mono text-[11.5px] px-1 py-0.5 rounded bg-surface-alt border border-border text-text-primary"
      >
        {part}
      </code>
    ) : (
      <span key={i}>{part}</span>
    ),
  );
}
