"use client";

import { Card, CardHeader } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import {
  DEBUG_BUBBLE_HERO,
  GOALS,
  NON_GOALS,
  MODULES,
  INTEGRATION,
  REMOVAL_LAYERS,
  REMOVAL_STEPS,
  BUBBLE_SPECS,
  PANEL_SPECS,
  TABS,
  DATA_SOURCES,
  PHASES,
  PHASE_TOTAL,
  REVIEW_SUMMARY,
  OPEN_QUESTIONS,
} from "@/lib/debugBubble";

/* ---------------------------------------------------------------------------
 * Page — Debug Bubble Implementation Plan
 * ------------------------------------------------------------------------- */
export default function DebugBubblePage() {
  return (
    <div className="space-y-10">
      {/* ── Hero ── */}
      <section>
        <div className="flex flex-wrap items-center gap-3 mb-3">
          <span
            className="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold uppercase tracking-widest"
            style={{
              backgroundColor: `color-mix(in srgb, ${DEBUG_BUBBLE_HERO.statusColor} 15%, transparent)`,
              color: DEBUG_BUBBLE_HERO.statusColor,
            }}
          >
            <span
              className="inline-block h-1.5 w-1.5 rounded-full"
              style={{ backgroundColor: DEBUG_BUBBLE_HERO.statusColor }}
            />
            {DEBUG_BUBBLE_HERO.status}
          </span>
          <span className="text-xs text-text-secondary">{DEBUG_BUBBLE_HERO.meta}</span>
        </div>
        <h1 className="text-3xl font-bold tracking-tight text-text-primary sm:text-4xl mb-3">
          {DEBUG_BUBBLE_HERO.title}
        </h1>
        <p className="max-w-3xl text-[15px] leading-relaxed text-text-secondary">
          {DEBUG_BUBBLE_HERO.subtitle}
        </p>
      </section>

      {/* ── UI Mockup ── */}
      <BubbleMockup />

      {/* ── Goals + Non-Goals ── */}
      <section className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <Card>
            <CardHeader kicker="What it does" title="Goals" />
            <div className="grid gap-4 sm:grid-cols-2">
              {GOALS.map((g) => (
                <div key={g.title} className="rounded-[12px] border border-border bg-canvas p-4">
                  <div className="text-[14px] font-bold text-text-primary mb-1">{g.title}</div>
                  <div className="text-[13px] leading-relaxed text-text-secondary">{g.desc}</div>
                </div>
              ))}
            </div>
          </Card>
        </div>
        <Card>
          <CardHeader kicker="Out of scope" title="Non-Goals" />
          <ul className="space-y-3">
            {NON_GOALS.map((ng) => (
              <li key={ng} className="flex items-start gap-2 text-[13px] text-text-secondary">
                <span className="mt-1 inline-block h-1.5 w-1.5 rounded-full bg-text-secondary/40 shrink-0" />
                <span>{ng}</span>
              </li>
            ))}
          </ul>
        </Card>
      </section>

      {/* ── Architecture: module split ── */}
      <section>
        <Card>
          <CardHeader
            kicker="Architecture · D-162 C4 fix"
            title="Two-module split (correct dependency direction)"
          />
          <p className="text-[14px] text-text-secondary mb-5 max-w-3xl">
            Feature modules need to reference <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px]">DebugContext</code> +{" "}
            <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px]">LocalDebugContext</code> to opt in — but they
            can&apos;t import from a <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px]">debugImplementation</code> module (release builds won&apos;t compile).
            So the types live in an always-available <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px]">:core:debug-api</code>, and the bubble UI lives in{" "}
            <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px]">:feature:debug-bubble</code> (debug-only).
          </p>
          <div className="grid gap-5 lg:grid-cols-2">
            {MODULES.map((m) => (
              <div key={m.name} className="rounded-[16px] border border-border bg-canvas p-5">
                <div className="flex items-center gap-2 mb-2">
                  <code className="text-[15px] font-bold text-text-primary">{m.name}</code>
                  <span className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider"
                    style={{
                      backgroundColor: m.type.includes("debug") ? "color-mix(in srgb, var(--c-warning, #f59e0b) 15%, transparent)" : "color-mix(in srgb, var(--c-success, #22c55e) 15%, transparent)",
                      color: m.type.includes("debug") ? "var(--c-warning, #f59e0b)" : "var(--c-success, #22c55e)",
                    }}>
                    {m.type.includes("debug") ? "DEBUG-ONLY" : "ALWAYS"}
                  </span>
                </div>
                <div className="text-[12px] font-medium text-text-secondary mb-1">{m.type}</div>
                <div className="text-[12px] font-medium text-text-secondary mb-3">{m.scope}</div>
                <p className="text-[13px] leading-relaxed text-text-secondary mb-4">{m.desc}</p>
                <div className="flex flex-wrap gap-1.5">
                  {m.files.map((f) => (
                    <code key={f} className="rounded-md border border-border bg-surface px-2 py-1 text-[11px] text-text-secondary">
                      {f}
                    </code>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </Card>
      </section>

      {/* ── Integration point ── */}
      <section>
        <Card>
          <CardHeader kicker="The ONLY app-side change" title="Integration point" />
          <p className="text-[14px] text-text-secondary mb-3">{INTEGRATION.location}</p>
          <pre className="overflow-x-auto rounded-[12px] border border-border bg-[#0d0d0d] dark:bg-[#0d0d0d] p-4 text-[12.5px] leading-relaxed text-[#e8e8e8] font-mono">
            <code>{INTEGRATION.snippet}</code>
          </pre>
          <div className="mt-3 rounded-[10px] border border-warning/30 bg-warning/5 p-3">
            <p className="text-[12.5px] leading-relaxed text-text-secondary">
              <span className="font-bold text-text-primary">Critical (D-162 C1):</span> {INTEGRATION.note}
            </p>
          </div>
        </Card>
      </section>

      {/* ── Removal strategy ── */}
      <section className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader kicker="Three layers of defense" title="Easy-removal strategy" />
          <div className="space-y-4">
            {REMOVAL_LAYERS.map((l) => (
              <div key={l.layer} className="rounded-[12px] border border-border bg-canvas p-4">
                <div className="text-[13px] font-bold text-text-primary mb-1">{l.layer}</div>
                <div className="text-[12.5px] leading-relaxed text-text-secondary">{l.desc}</div>
              </div>
            ))}
          </div>
        </Card>
        <Card>
          <CardHeader kicker="Honest edit list (D-162 I8)" title="To fully remove" />
          <ol className="space-y-2.5">
            {REMOVAL_STEPS.map((s, i) => (
              <li key={i} className="flex items-start gap-3 text-[13px]">
                <span className={`mt-0.5 inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-md text-[11px] font-bold ${i < 5 ? "bg-warning/15 text-warning" : "bg-canvas text-text-secondary"}`}>
                  {i + 1}
                </span>
                <span className={i < 5 ? "text-text-primary" : "text-text-secondary"}>{s}</span>
              </li>
            ))}
          </ol>
          <div className="mt-4 rounded-[10px] border border-border bg-canvas p-3">
            <p className="text-[12px] text-text-secondary">
              <span className="font-bold text-text-primary">~5 mandatory edits</span> + 2 optional. No deep refactoring — the app&apos;s nav content, screens, and data flows are untouched.
            </p>
          </div>
        </Card>
      </section>

      {/* ── Bubble + Panel specs ── */}
      <section className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader kicker="The bubble" title="Visual + drag specs" />
          <dl className="space-y-2">
            {BUBBLE_SPECS.map((s) => (
              <div key={s.label} className="flex items-start gap-3 py-1.5 border-b border-border/50 last:border-0">
                <dt className="w-28 shrink-0 text-[12px] font-bold uppercase tracking-wide text-text-secondary">{s.label}</dt>
                <dd className="text-[13px] text-text-primary font-mono">{s.value}</dd>
              </div>
            ))}
          </dl>
        </Card>
        <Card>
          <CardHeader kicker="The panel" title="Layout specs" />
          <dl className="space-y-2">
            {PANEL_SPECS.map((s) => (
              <div key={s.label} className="flex items-start gap-3 py-1.5 border-b border-border/50 last:border-0">
                <dt className="w-32 shrink-0 text-[12px] font-bold uppercase tracking-wide text-text-secondary">{s.label}</dt>
                <dd className="text-[13px] text-text-primary font-mono">{s.value}</dd>
              </div>
            ))}
          </dl>
        </Card>
      </section>

      {/* ── Panel tabs ── */}
      <section>
        <Card>
          <CardHeader kicker="5 tabs" title="Panel content" />
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {TABS.map((t) => (
              <div key={t.name} className="rounded-[14px] border border-border bg-canvas p-4 flex flex-col">
                <div className="flex items-center gap-2 mb-2">
                  <span className="text-xl">{t.icon}</span>
                  <span className="text-[14px] font-bold text-text-primary">{t.name}</span>
                  {t.conditional && (
                    <span className="ml-auto inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[9px] font-bold uppercase tracking-wider bg-warning/15 text-warning">
                      conditional
                    </span>
                  )}
                </div>
                <p className="text-[12.5px] leading-relaxed text-text-secondary mb-3">{t.desc}</p>
                <ul className="space-y-1.5 mt-auto">
                  {t.features.map((f) => (
                    <li key={f} className="flex items-start gap-2 text-[12px] text-text-secondary">
                      <span className="mt-1 inline-block h-1 w-1 rounded-full bg-text-secondary/50 shrink-0" />
                      <span>{f}</span>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </Card>
      </section>

      {/* ── Data sources ── */}
      <section>
        <Card>
          <CardHeader kicker="How the bubble gets its data" title="Data sources & integration patterns" />
          <div className="space-y-4">
            {DATA_SOURCES.map((d) => (
              <div key={d.name} className="rounded-[14px] border border-border bg-canvas p-5">
                <div className="flex items-baseline gap-2 mb-2">
                  <span className="text-[15px] font-bold text-text-primary">{d.name}</span>
                  <code className="text-[12px] text-text-secondary">{d.pattern}</code>
                </div>
                <p className="text-[13px] leading-relaxed text-text-secondary mb-3">{d.desc}</p>
                <div className="rounded-[10px] border border-warning/30 bg-warning/5 p-3">
                  <p className="text-[12px] leading-relaxed text-text-secondary">
                    <span className="font-bold text-text-primary">Why this pattern:</span> {d.keyFix}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </Card>
      </section>

      {/* ── Sub-agent review ── */}
      <section>
        <Card>
          <CardHeader kicker="D-162 · Sub-agent review" title="Critical issues caught + fixed" />
          <p className="text-[14px] text-text-secondary mb-5 max-w-3xl">
            The plan was reviewed by a sub-agent. The main agent critically evaluated each finding — all CRITICAL issues
            were verified as real and incorporated into the plan above.
          </p>
          <div className="grid gap-4 lg:grid-cols-2 mb-5">
            <div>
              <div className="flex items-center gap-2 mb-3">
                <StatusDot color="var(--c-danger, #ef4444)" />
                <span className="text-[13px] font-bold uppercase tracking-wide text-text-primary">
                  Critical ({REVIEW_SUMMARY.critical.length})
                </span>
              </div>
              <div className="space-y-2">
                {REVIEW_SUMMARY.critical.map((c) => (
                  <div key={c.id} className="rounded-[10px] border border-danger/30 bg-danger/5 p-3">
                    <div className="text-[12.5px] font-bold text-text-primary mb-1">
                      <code className="text-danger">{c.id}</code> — {c.issue}
                    </div>
                    <div className="text-[12px] text-text-secondary">
                      <span className="font-bold">Fix:</span> {c.fix}
                    </div>
                  </div>
                ))}
              </div>
            </div>
            <div>
              <div className="flex items-center gap-2 mb-3">
                <StatusDot color="var(--c-warning, #f59e0b)" />
                <span className="text-[13px] font-bold uppercase tracking-wide text-text-primary">
                  Important ({REVIEW_SUMMARY.important.length})
                </span>
              </div>
              <div className="space-y-2">
                {REVIEW_SUMMARY.important.map((i) => (
                  <div key={i.id} className="rounded-[10px] border border-warning/30 bg-warning/5 p-3">
                    <div className="text-[12.5px] font-bold text-text-primary mb-1">
                      <code className="text-warning">{i.id}</code> — {i.issue}
                    </div>
                    <div className="text-[12px] text-text-secondary">
                      <span className="font-bold">Fix:</span> {i.fix}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
          <div className="rounded-[12px] border border-border bg-canvas p-4">
            <p className="text-[13px] leading-relaxed text-text-secondary">
              <span className="font-bold text-text-primary">Main agent&apos;s assessment:</span> {REVIEW_SUMMARY.assessment}
            </p>
          </div>
        </Card>
      </section>

      {/* ── Implementation phases ── */}
      <section>
        <Card>
          <CardHeader kicker="Roadmap" title="Implementation phases" />
          <div className="space-y-2">
            {PHASES.map((p) => (
              <div key={p.id} className="flex items-center gap-4 rounded-[12px] border border-border bg-canvas p-3.5">
                <code className="shrink-0 rounded-md bg-surface px-2.5 py-1.5 text-[12px] font-bold text-text-primary border border-border">
                  {p.id}
                </code>
                <span className="flex-1 text-[13.5px] text-text-primary">{p.scope}</span>
                <span className="shrink-0 text-[12px] font-medium text-text-secondary">{p.est}</span>
              </div>
            ))}
          </div>
          <div className="mt-4 rounded-[10px] border border-border bg-canvas p-3">
            <p className="text-[12.5px] text-text-secondary">
              <span className="font-bold text-text-primary">Total:</span> {PHASE_TOTAL}
            </p>
          </div>
        </Card>
      </section>

      {/* ── Open questions ── */}
      <section>
        <Card>
          <CardHeader kicker="For user review" title="Open questions" />
          <div className="space-y-3">
            {OPEN_QUESTIONS.map((o, i) => (
              <div key={i} className="rounded-[12px] border border-border bg-canvas p-4">
                <div className="text-[14px] font-bold text-text-primary mb-1.5">{o.q}</div>
                <div className="text-[12.5px] leading-relaxed text-text-secondary">
                  <span className="font-bold text-text-primary">Recommendation:</span> {o.recommendation}
                </div>
              </div>
            ))}
          </div>
        </Card>
      </section>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * BubbleMockup — a visual representation of the bubble + expanded panel.
 * ------------------------------------------------------------------------- */
function BubbleMockup() {
  return (
    <Card>
      <CardHeader kicker="Visual mockup" title="The bubble + expanded panel" />
      <div className="rounded-[16px] border border-border bg-[#1a1a1a] dark:bg-[#0d0d0d] p-6 overflow-hidden">
        {/* Faux app screen */}
        <div className="relative h-[340px] rounded-[12px] bg-[#222] overflow-hidden">
          {/* Faux screen content (dimmed) */}
          <div className="absolute inset-0 p-4 opacity-30">
            <div className="h-3 w-24 rounded bg-[#444] mb-2" />
            <div className="h-2 w-48 rounded bg-[#333] mb-4" />
            <div className="grid grid-cols-3 gap-2">
              {[...Array(6)].map((_, i) => (
                <div key={i} className="aspect-[2/3] rounded bg-[#333]" />
              ))}
            </div>
          </div>

          {/* The expanded panel (right side) */}
          <div className="absolute top-12 right-3 w-[200px] rounded-[12px] bg-[#1a1a1a]/95 backdrop-blur border border-[#444] shadow-xl overflow-hidden">
            {/* Panel header */}
            <div className="flex items-center justify-between px-3 py-2 border-b border-[#333]">
              <span className="text-[11px] font-bold text-[#e8e8e8]">Debug</span>
              <span className="text-[11px] text-[#888]">✕</span>
            </div>
            {/* Tab strip */}
            <div className="flex gap-1 px-2 py-1.5 border-b border-[#333] overflow-x-auto">
              {["Screen", "DB", "Log", "Net", "App"].map((t, i) => (
                <span
                  key={t}
                  className={`shrink-0 rounded-md px-1.5 py-0.5 text-[9px] font-bold ${
                    i === 1 ? "bg-[#e8e8e8] text-[#1a1a1a]" : "text-[#888]"
                  }`}
                >
                  {t}
                </span>
              ))}
            </div>
            {/* Panel content (DB tab) */}
            <div className="p-2.5 space-y-1.5">
              <div className="text-[9px] text-[#888] uppercase tracking-wide">downloaded_episode · 42 rows</div>
              {[["main_id", "uuid-abc1…"], ["episode_key", "12345|00001"], ["status", "COMPLETED"], ["video_uri", "content://…"], ["subtitle_uris", "[3 files]"]].map(
                ([k, v]) => (
                  <div key={k} className="flex items-center gap-2">
                    <span className="text-[9px] font-bold text-[#aaa] w-20 shrink-0">{k}</span>
                    <span className="text-[9px] text-[#e8e8e8] truncate">{v}</span>
                  </div>
                ),
              )}
              <div className="pt-1.5 border-t border-[#333] mt-1.5">
                <span className="text-[9px] text-[#666]">↻ Refresh</span>
              </div>
            </div>
          </div>

          {/* The bubble (bottom-right, with a drag indicator) */}
          <div className="absolute bottom-4 right-4 flex items-center gap-2">
            <span className="text-[9px] text-[#666] hidden sm:inline">← drag me</span>
            <div className="h-10 w-10 rounded-full bg-[#e8e8e8]/90 border border-[#444] flex items-center justify-center shadow-lg">
              {/* Bug icon */}
              <svg viewBox="0 0 24 24" className="h-5 w-5 text-[#1a1a1a]" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="8" y="6" width="8" height="12" rx="4" />
                <path d="M8 10H4M16 10h4M8 14H4M16 14h4M9 6l-2-2M15 6l2-2M9 18l-2 2M15 18l2 2" />
              </svg>
            </div>
          </div>

          {/* Labels */}
          <div className="absolute top-3 left-3">
            <span className="rounded-md bg-warning/15 px-2 py-1 text-[10px] font-bold text-warning">
              Faux app screen (dimmed)
            </span>
          </div>
        </div>
        <p className="mt-3 text-[12px] text-text-secondary leading-relaxed">
          The bubble (bottom-right) floats on top of every screen. Tap to expand the panel — it opens on the side with
          more space (here: right). The panel has 5 tabs; the Database tab is shown here browsing the{" "}
          <code className="text-[11px]">downloaded_episode</code> table. The bubble is draggable anywhere on screen.
        </p>
      </div>
    </Card>
  );
}
