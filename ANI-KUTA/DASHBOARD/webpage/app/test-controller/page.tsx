import { Card, CardHeader } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import {
  TC_HERO,
  TC_METRICS,
  ARCH_COMPONENTS,
  FLOW_STEPS,
  MESSAGE_KINDS,
  COMMAND_CATEGORIES,
  COMMANDS,
  APP_CHANGES,
  UNTOUCHED_AREAS,
  CF_RATIONALE,
  HIBERNATION_POINTS,
  TESTING_RESULTS,
  FILE_MAP,
  DECISIONS,
  D198_EVOLUTION,
  RELAY_INFO,
  ROOT_CAUSE_FIX,
} from "@/lib/testController";

/**
 * /test-controller/ — Test Controller section.
 *
 * Documents the autonomous remote-UI-testing system: Cloudflare Workers relay
 * + Android AccessibilityService executor + Python one-shot agent client.
 *
 * Static Server Component — no interactivity needed, no "use client".
 *
 * Sections:
 *   1. Hero + metrics
 *   2. Architecture diagram (Agent ↔ CF Worker ↔ DO ↔ Phone)
 *   3. How it works (6-step communication flow)
 *   4. Key insight — no app code changed
 *   5. Command reference (29 commands across 5 categories)
 *   6. Communication protocol (message kinds + Hibernation API)
 *   7. Cloudflare Workers — why we chose it
 *   8. Testing results — what works + known limitations
 *   9. File map — 4 components + their files
 *  10. Decisions — D-197..D-202 + D-198 v4 evolution
 */
export default function TestControllerPage() {
  return (
    <div className="space-y-6">
      {/* ───────────────────────────────────────────────────────────────
       *  1. HERO
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-3">
          <div className="min-w-0">
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1.5">
              Autonomous UI testing · D-198 v4
            </div>
            <h1 className="text-[22px] sm:text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
              {TC_HERO.title}
            </h1>
          </div>
          <div className="flex items-center gap-2 flex-wrap max-w-full">
            <span
              className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border"
              style={{
                backgroundColor: `color-mix(in srgb, ${TC_HERO.statusColor} 12%, transparent)`,
                borderColor: `color-mix(in srgb, ${TC_HERO.statusColor} 35%, transparent)`,
                color: TC_HERO.statusColor,
              }}
              title="Live — Cloudflare Worker deployed + CI green"
            >
              <span
                className="inline-block w-1.5 h-1.5 rounded-full animate-pulse"
                style={{ backgroundColor: TC_HERO.statusColor }}
                aria-hidden="true"
              />
              {TC_HERO.status}
            </span>
            <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
              <StatusDot color="var(--c-success)" size="sm" />
              CI green
            </span>
          </div>
        </div>
        <p className="text-[12.5px] sm:text-[13.5px] text-text-secondary leading-[1.5] max-w-3xl">
          {TC_HERO.subtitle}
        </p>
        <p className="text-[11.5px] text-text-secondary leading-relaxed mt-3 pt-3 border-t border-border/60 break-words">
          <span className="font-medium text-text-primary">Branch:</span>{" "}
          TEST_BETA_FEATURE
          <span className="mx-2 text-border">·</span>
          <span className="font-medium text-text-primary">Latest:</span>{" "}
          <span className="font-mono break-all">{RELAY_INFO.latestCommit}</span>
          <span className="mx-2 text-border">·</span>
          <span className="font-medium text-text-primary">Deployed:</span>{" "}
          <span className="font-mono break-all">{RELAY_INFO.deployedCommit}</span>
        </p>

        {/* Metrics row */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mt-5">
          {TC_METRICS.map((m) => (
            <div
              key={m.label}
              className="rounded-[14px] border border-border bg-canvas p-4"
            >
              <div className="text-[10px] font-medium uppercase tracking-widest text-text-secondary mb-1.5">
                {m.label}
              </div>
              <div
                className="text-[28px] font-bold tracking-extra-tight leading-none"
                style={{ color: m.accent }}
              >
                {m.value}
              </div>
              <div className="text-[11.5px] text-text-secondary leading-snug mt-2">
                {m.hint}
              </div>
            </div>
          ))}
        </div>
      </Card>

      {/* ───────────────────────────────────────────────────────────────
       *  2. ARCHITECTURE DIAGRAM
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <CardHeader
          kicker="Three nodes, one WebSocket"
          title="Architecture diagram"
        />
        <p className="text-[13px] text-text-secondary mb-5 max-w-3xl leading-relaxed">
          The agent dials the Cloudflare Worker over <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px] font-mono">wss://</code> on port 443. The Worker routes the upgrade to a Durable Object named <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px] font-mono">&quot;main&quot;</code>, which holds both connections. The phone keeps its WebSocket open persistently (Hibernation API = $0 idle); the agent opens a fresh one per command batch.
        </p>
        <ArchitectureDiagram />
        <div className="mt-5 rounded-[10px] border border-border bg-canvas p-3">
          <p className="text-[12px] leading-relaxed text-text-secondary">
            <span className="font-bold text-text-primary">Relay URL (stable):</span>{" "}
            <code className="font-mono text-[11.5px] break-all">{RELAY_INFO.wsUrl}</code>
          </p>
        </div>
      </Card>

      {/* ───────────────────────────────────────────────────────────────
       *  3. HOW IT WORKS — 6-step flow
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <CardHeader
          kicker="One command's round trip"
          title="How it works"
        />
        <p className="text-[13px] text-text-secondary mb-5 max-w-3xl leading-relaxed">
          A single command makes 6 hops: agent → worker → DO → phone → DO → agent. The DO is the only stateful piece — it forwards messages by their <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px] font-mono">kind</code> field.
        </p>
        <div className="grid gap-3">
          {FLOW_STEPS.map((s) => {
            const actorMeta = ACTOR_META[s.actor];
            return (
              <div
                key={s.step}
                className="rounded-[14px] border border-border bg-canvas p-4 flex flex-col sm:flex-row gap-4"
              >
                <div className="flex items-start gap-3 sm:flex-col sm:items-center sm:w-20 sm:shrink-0">
                  <span
                    className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[12px] text-[13px] font-bold text-white"
                    style={{ backgroundColor: actorMeta.color }}
                    aria-hidden="true"
                  >
                    {s.step}
                  </span>
                  <span className="text-[10.5px] font-bold uppercase tracking-widest" style={{ color: actorMeta.color }}>
                    {actorMeta.label}
                  </span>
                </div>
                <div className="min-w-0 flex-1">
                  <div className="text-[14px] font-bold text-text-primary mb-1.5">
                    {s.title}
                  </div>
                  <p className="text-[12.5px] leading-relaxed text-text-secondary">
                    {s.desc}
                  </p>
                  {s.message && (
                    <pre className="mt-3 overflow-x-auto rounded-[10px] border border-border bg-[#0d0d0d] dark:bg-[#0d0d0d] p-3 text-[11.5px] leading-relaxed text-[#e8e8e8] font-mono">
                      <code>{s.message.payload}</code>
                    </pre>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </Card>

      {/* ───────────────────────────────────────────────────────────────
       *  4. KEY INSIGHT — NO APP CODE CHANGED
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <CardHeader
          kicker="The headline"
          title="Key insight — no app code changed"
        />
        <p className="text-[13px] text-text-secondary mb-5 max-w-3xl leading-relaxed">
          The test-controller is a <span className="font-bold text-text-primary">completely separate module</span>. The app&apos;s screens (Browse, Library, Search, Details, Watch) were not modified. The only app-side changes are listed below — all of them debug-build-only, all trivially reversible.
        </p>
        <div className="grid gap-4 lg:grid-cols-3 mb-5">
          {APP_CHANGES.map((c) => (
            <div
              key={c.file}
              className="rounded-[14px] border border-border bg-canvas p-4 flex flex-col"
            >
              <div className="flex items-center gap-2 mb-2 flex-wrap">
                <span
                  className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[9.5px] font-bold uppercase tracking-wider"
                  style={{
                    backgroundColor:
                      c.severity === "trivial"
                        ? "color-mix(in srgb, var(--c-success, #14b8a6) 15%, transparent)"
                        : "color-mix(in srgb, var(--c-warning, #f59e0b) 15%, transparent)",
                    color:
                      c.severity === "trivial"
                        ? "var(--c-success, #14b8a6)"
                        : "var(--c-warning, #f59e0b)",
                  }}
                >
                  {c.severity}
                </span>
                <span className="text-[10.5px] font-mono text-text-secondary">{c.lines}</span>
              </div>
              <code className="text-[12px] font-mono text-text-primary mb-2 break-all">
                {c.file}
              </code>
              <p className="text-[12.5px] leading-relaxed text-text-secondary">{c.change}</p>
            </div>
          ))}
        </div>
        <div className="rounded-[12px] border border-success/30 bg-success/5 p-4">
          <div className="flex items-center gap-2 mb-3">
            <StatusDot color="var(--c-success)" size="md" />
            <span className="text-[13px] font-bold uppercase tracking-wide text-text-primary">
              Untouched ({UNTOUCHED_AREAS.length})
            </span>
          </div>
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {UNTOUCHED_AREAS.map((u) => (
              <div
                key={u}
                className="flex items-start gap-2 text-[12px] text-text-secondary"
              >
                <span className="mt-1.5 inline-block h-1 w-1 rounded-full bg-success/60 shrink-0" />
                <span>{u}</span>
              </div>
            ))}
          </div>
        </div>
      </Card>

      {/* ───────────────────────────────────────────────────────────────
       *  5. COMMAND REFERENCE
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <CardHeader
          kicker="29 TestCommand subtypes · 5 categories"
          title="Command reference"
          right={
            <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
              <StatusDot color="var(--c-secondary)" size="sm" />
              {COMMANDS.length} commands
            </span>
          }
        />
        <p className="text-[13px] text-text-secondary mb-5 max-w-3xl leading-relaxed">
          Every command is a JSON object with <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px] font-mono">type</code>, <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px] font-mono">id</code>, and type-specific fields. They are sealed-class subtypes in Kotlin (exhaustive <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px] font-mono">when</code> dispatch in the executor), and opaque JSON to the relay.
        </p>
        <div className="space-y-5">
          {COMMAND_CATEGORIES.map((cat) => {
            const catCommands = COMMANDS.filter((c) => c.category === cat.id);
            return (
              <div key={cat.id}>
                <div className="flex items-center gap-2 mb-3 flex-wrap">
                  <span
                    className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-bold uppercase tracking-wider text-white"
                    style={{ backgroundColor: cat.color }}
                  >
                    {cat.label}
                  </span>
                  <span className="text-[11px] text-text-secondary">
                    {catCommands.length} · {cat.desc}
                  </span>
                </div>
                <div className="grid gap-3 md:grid-cols-2">
                  {catCommands.map((cmd) => (
                    <div
                      key={cmd.type}
                      className="rounded-[12px] border border-border bg-canvas p-3.5 flex flex-col"
                    >
                      <div className="flex items-baseline justify-between gap-2 mb-2">
                        <code
                          className="text-[13px] font-mono font-bold"
                          style={{ color: cat.color }}
                        >
                          {cmd.type}
                        </code>
                      </div>
                      <pre className="overflow-x-auto rounded-[8px] border border-border bg-[#0d0d0d] dark:bg-[#0d0d0d] p-2 text-[10.5px] leading-relaxed text-[#e8e8e8] font-mono mb-2">
                        <code>{cmd.example}</code>
                      </pre>
                      <p className="text-[12px] leading-relaxed text-text-secondary mt-auto">
                        <span className="font-bold text-text-primary">Returns:</span>{" "}
                        {cmd.returns}
                      </p>
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      </Card>

      {/* ───────────────────────────────────────────────────────────────
       *  6. COMMUNICATION PROTOCOL
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <CardHeader
          kicker="Wire format + routing"
          title="Communication protocol"
        />
        <p className="text-[13px] text-text-secondary mb-5 max-w-3xl leading-relaxed">
          Every WebSocket message is a JSON object with a <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px] font-mono">kind</code> field. The Durable Object dispatches on it — a tiny switch statement, ~30 lines. Screenshots travel as a separate <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px] font-mono">screenshot</code> message so the result stays small.
        </p>

        {/* Routing table */}
        <div className="overflow-x-auto rounded-[12px] border border-border mb-6">
          <table className="w-full text-[12.5px]">
            <thead className="bg-canvas border-b border-border">
              <tr>
                <th className="text-left font-bold uppercase tracking-wider text-text-secondary text-[10.5px] px-3 py-2.5">kind</th>
                <th className="text-left font-bold uppercase tracking-wider text-text-secondary text-[10.5px] px-3 py-2.5">direction</th>
                <th className="text-left font-bold uppercase tracking-wider text-text-secondary text-[10.5px] px-3 py-2.5">payload</th>
                <th className="text-left font-bold uppercase tracking-wider text-text-secondary text-[10.5px] px-3 py-2.5">description</th>
              </tr>
            </thead>
            <tbody>
              {MESSAGE_KINDS.map((m, i) => (
                <tr
                  key={m.kind}
                  className={`border-b border-border/60 last:border-0 hover:bg-canvas/50 ${i % 2 === 1 ? "bg-canvas/30" : ""}`}
                >
                  <td className="px-3 py-2.5">
                    <code className="font-mono text-[12px] font-bold text-text-primary">{m.kind}</code>
                  </td>
                  <td className="px-3 py-2.5">
                    <span className="inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[10px] font-medium border bg-surface border-border text-text-secondary font-mono">
                      {m.direction}
                    </span>
                  </td>
                  <td className="px-3 py-2.5">
                    <code className="font-mono text-[11px] text-text-secondary break-all">{m.payload}</code>
                  </td>
                  <td className="px-3 py-2.5 text-text-secondary leading-relaxed">{m.desc}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Hibernation API explanation */}
        <div className="rounded-[14px] border border-border bg-canvas p-4 mb-2">
          <div className="flex items-center gap-2 mb-3">
            <span
              className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10.5px] font-bold uppercase tracking-wider text-white"
              style={{ backgroundColor: "var(--c-secondary)" }}
            >
              WebSocket Hibernation API
            </span>
            <span className="text-[11px] text-text-secondary">— the key to $0/month</span>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            {HIBERNATION_POINTS.map((h) => (
              <div key={h.title} className="rounded-[10px] border border-border bg-surface p-3">
                <div className="text-[13px] font-bold text-text-primary mb-1">{h.title}</div>
                <p className="text-[12px] leading-relaxed text-text-secondary">{h.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </Card>

      {/* ───────────────────────────────────────────────────────────────
       *  7. CLOUDFLARE WORKERS — WHY
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <CardHeader
          kicker="D-198 v4 · 4 iterations to land here"
          title="Why Cloudflare Workers"
        />
        <p className="text-[13px] text-text-secondary mb-5 max-w-3xl leading-relaxed">
          We evaluated 4 transports (ntfy.sh, MQTT, raw WebSocket, Cloudflare Workers) across cost, cold-start, URL stability, always-on, payload limit, and port/firewall survival. CF Workers won on every axis. The Apr-7-2025 unblock of free-tier Durable Objects was the trigger that made this viable.
        </p>
        <div className="overflow-x-auto rounded-[12px] border border-border">
          <table className="w-full text-[12.5px]">
            <thead className="bg-canvas border-b border-border">
              <tr>
                <th className="text-left font-bold uppercase tracking-wider text-text-secondary text-[10.5px] px-3 py-2.5">criterion</th>
                <th className="text-left font-bold uppercase tracking-wider text-text-secondary text-[10.5px] px-3 py-2.5">Cloudflare Workers</th>
                <th className="text-left font-bold uppercase tracking-wider text-text-secondary text-[10.5px] px-3 py-2.5">alternatives</th>
              </tr>
            </thead>
            <tbody>
              {CF_RATIONALE.map((r, i) => (
                <tr
                  key={r.criterion}
                  className={`border-b border-border/60 last:border-0 hover:bg-canvas/50 ${i % 2 === 1 ? "bg-canvas/30" : ""}`}
                >
                  <td className="px-3 py-2.5 font-bold text-text-primary">{r.criterion}</td>
                  <td className="px-3 py-2.5 text-text-secondary leading-relaxed">
                    <span className="inline-flex items-center gap-1.5">
                      {r.winner === "cf" && <StatusDot color="var(--c-success)" size="sm" />}
                      {r.cf}
                    </span>
                  </td>
                  <td className="px-3 py-2.5 text-text-secondary leading-relaxed">
                    {r.winner === "alt" && <StatusDot color="var(--c-success)" size="sm" />}
                    {r.alt}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {/* ───────────────────────────────────────────────────────────────
       *  8. TESTING RESULTS
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <CardHeader
          kicker="Live verification"
          title="Testing results"
        />
        <p className="text-[13px] text-text-secondary mb-5 max-w-3xl leading-relaxed">
          Verified against the real OnePlus KB2001 device + Cloudflare relay. The green list is what works today; the amber list is what&apos;s known to be flaky or limited — with the workaround noted.
        </p>
        <div className="grid gap-3 md:grid-cols-2">
          {TESTING_RESULTS.map((r) => (
            <div
              key={r.label}
              className={`rounded-[12px] border p-3.5 flex items-start gap-3 ${
                r.status === "works"
                  ? "border-success/30 bg-success/5"
                  : "border-warning/30 bg-warning/5"
              }`}
            >
              <span className="mt-0.5 shrink-0">
                <StatusDot
                  color={r.status === "works" ? "var(--c-success)" : "var(--c-warning)"}
                  size="md"
                />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2 mb-1 flex-wrap">
                  <span className="text-[13px] font-bold text-text-primary">{r.label}</span>
                  <span
                    className="inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider"
                    style={{
                      backgroundColor:
                        r.status === "works"
                          ? "color-mix(in srgb, var(--c-success, #14b8a6) 15%, transparent)"
                          : "color-mix(in srgb, var(--c-warning, #f59e0b) 15%, transparent)",
                      color:
                        r.status === "works"
                          ? "var(--c-success, #14b8a6)"
                          : "var(--c-warning, #f59e0b)",
                    }}
                  >
                    {r.status === "works" ? "works" : "limitation"}
                  </span>
                </div>
                <p className="text-[12px] leading-relaxed text-text-secondary">{r.detail}</p>
              </div>
            </div>
          ))}
        </div>

        {/* Root-cause callout (FIX-A11Y-ROOT-CAUSE) */}
        <div className="mt-5 rounded-[14px] border border-primary/30 bg-primary/5 p-4">
          <div className="flex items-center gap-2 mb-2">
            <span
              className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10.5px] font-bold uppercase tracking-wider text-white"
              style={{ backgroundColor: "var(--c-primary)" }}
            >
              FIX-A11Y-ROOT-CAUSE
            </span>
            <span className="text-[11px] text-text-secondary">commit 82f29128</span>
          </div>
          <div className="text-[14px] font-bold text-text-primary mb-2">{ROOT_CAUSE_FIX.bug}</div>
          <p className="text-[12.5px] leading-relaxed text-text-secondary mb-3">{ROOT_CAUSE_FIX.impact}</p>
          <div className="grid gap-2 sm:grid-cols-2">
            <div className="rounded-[10px] border border-danger/30 bg-danger/5 p-3">
              <div className="text-[10.5px] font-bold uppercase tracking-wider text-danger mb-1">was</div>
              <code className="font-mono text-[11px] text-text-primary break-all">{ROOT_CAUSE_FIX.wrong}</code>
            </div>
            <div className="rounded-[10px] border border-success/30 bg-success/5 p-3">
              <div className="text-[10.5px] font-bold uppercase tracking-wider text-success mb-1">now</div>
              <code className="font-mono text-[11px] text-text-primary break-all">{ROOT_CAUSE_FIX.correct}</code>
            </div>
          </div>
          <p className="text-[11.5px] text-text-secondary mt-2">
            <span className="font-bold text-text-primary">File:</span>{" "}
            <code className="font-mono text-[11px]">{ROOT_CAUSE_FIX.file}</code>
          </p>
        </div>
      </Card>

      {/* ───────────────────────────────────────────────────────────────
       *  9. FILE MAP
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <CardHeader
          kicker="4 components · 30+ files"
          title="File map"
        />
        <p className="text-[13px] text-text-secondary mb-5 max-w-3xl leading-relaxed">
          The full system lives in 3 directories: the agent bridge (Python), the Cloudflare relay (TypeScript), and two Gradle modules under <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px] font-mono">core/</code>. The Android side splits into <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px] font-mono">:core:test-api</code> (always-on types) + <code className="px-1.5 py-0.5 rounded bg-canvas text-[12px] font-mono">:core:test-controller</code> (debug-only executor).
        </p>
        <div className="grid gap-4 lg:grid-cols-2">
          {FILE_MAP.map((m) => (
            <div
              key={m.component}
              className="rounded-[14px] border border-border bg-canvas p-4 flex flex-col"
            >
              <div className="flex items-center gap-2 mb-2 flex-wrap">
                <span
                  className="inline-block h-2.5 w-2.5 rounded-full shrink-0"
                  style={{ backgroundColor: m.color }}
                  aria-hidden="true"
                />
                <span className="text-[14px] font-bold text-text-primary">{m.component}</span>
              </div>
              <code className="text-[11px] font-mono text-text-secondary mb-3 break-all">{m.path}</code>
              <div className="space-y-1.5 flex-1">
                {m.files.map((f) => (
                  <div key={f.name} className="flex flex-col sm:flex-row gap-1 sm:gap-3 py-1 border-b border-border/40 last:border-0">
                    <code className="text-[11.5px] font-mono font-bold text-text-primary shrink-0 sm:w-44 break-all">
                      {f.name}
                    </code>
                    <span className="text-[11.5px] leading-relaxed text-text-secondary flex-1">{f.role}</span>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </Card>

      {/* ───────────────────────────────────────────────────────────────
       *  10. DECISIONS — D-197..D-202 + D-198 v4 evolution
       * ─────────────────────────────────────────────────────────────── */}
      <Card>
        <CardHeader
          kicker="6 confirmed decisions + 4 rejected alternatives"
          title="Decisions"
        />
        <p className="text-[13px] text-text-secondary mb-5 max-w-3xl leading-relaxed">
          The test-controller system was specified across 6 architecture decisions, all confirmed. D-198 went through 4 iterations before landing on Cloudflare Workers — the rejected alternatives are documented below for future readers.
        </p>

        {/* Decisions list */}
        <div className="space-y-3 mb-6">
          {DECISIONS.map((d) => (
            <div
              key={d.id}
              className="rounded-[14px] border border-border bg-canvas p-4"
            >
              <div className="flex items-center gap-2 mb-2 flex-wrap">
                <code className="rounded-md bg-surface border border-border px-2 py-1 text-[12px] font-bold text-text-primary font-mono">
                  {d.id}
                </code>
                <span className="text-[14px] font-bold text-text-primary">{d.title}</span>
                <span
                  className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[9.5px] font-bold uppercase tracking-wider"
                  style={{
                    backgroundColor: "color-mix(in srgb, var(--c-success, #14b8a6) 15%, transparent)",
                    color: "var(--c-success, #14b8a6)",
                  }}
                >
                  {d.status}
                </span>
              </div>
              <p className="text-[12.5px] leading-relaxed text-text-secondary mb-2">{d.summary}</p>
              <p className="text-[12px] leading-relaxed text-text-secondary">
                <span className="font-bold text-text-primary">Rationale:</span> {d.rationale}
              </p>
            </div>
          ))}
        </div>

        {/* D-198 v4 evolution timeline */}
        <div className="rounded-[14px] border border-border bg-canvas p-4">
          <div className="flex items-center gap-2 mb-4">
            <span
              className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10.5px] font-bold uppercase tracking-wider text-white"
              style={{ backgroundColor: "var(--c-secondary)" }}
            >
              D-198 evolution
            </span>
            <span className="text-[11.5px] text-text-secondary">— 4 iterations, 3 rejected, 1 current</span>
          </div>
          <div className="relative pl-5">
            {/* Vertical timeline line */}
            <div className="absolute left-[7px] top-2 bottom-2 w-px bg-border" aria-hidden="true" />
            <div className="space-y-4">
              {D198_EVOLUTION.map((e) => {
                const isCurrent = e.status === "current";
                return (
                  <div key={e.version} className="relative">
                    <span
                      className="absolute -left-5 top-1.5 inline-block w-3.5 h-3.5 rounded-full border-2 border-surface"
                      style={{
                        backgroundColor: isCurrent ? "var(--c-success)" : "var(--c-text-secondary)",
                      }}
                      aria-hidden="true"
                    />
                    <div className="flex items-center gap-2 mb-1 flex-wrap">
                      <span className="text-[12px] font-bold text-text-primary">{e.version}</span>
                      <span
                        className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[9.5px] font-bold uppercase tracking-wider"
                        style={{
                          backgroundColor: isCurrent
                            ? "color-mix(in srgb, var(--c-success, #14b8a6) 15%, transparent)"
                            : "color-mix(in srgb, var(--c-danger, #ff6b6b) 15%, transparent)",
                          color: isCurrent ? "var(--c-success, #14b8a6)" : "var(--c-danger, #ff6b6b)",
                        }}
                      >
                        {e.status}
                      </span>
                    </div>
                    <div className="text-[13px] font-bold text-text-primary mb-1">{e.choice}</div>
                    <p className="text-[12px] leading-relaxed text-text-secondary">{e.reason}</p>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </Card>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * Actor metadata — colors + labels for the 4 architecture components.
 * ------------------------------------------------------------------------- */
const ACTOR_META: Record<
  "agent" | "worker" | "do" | "phone",
  { label: string; color: string }
> = {
  agent: { label: "Agent", color: "var(--c-primary, #6366f1)" },
  worker: { label: "Worker", color: "var(--c-warning, #f59e0b)" },
  do: { label: "Durable Object", color: "var(--c-secondary, #8b5cf6)" },
  phone: { label: "Phone", color: "var(--c-success, #14b8a6)" },
};

/* ---------------------------------------------------------------------------
 * ArchitectureDiagram — visual flow built with CSS/HTML (no images).
 *
 * Layout (desktop):
 *   ┌──────────┐      ┌─────────────┐      ┌──────────┐
 *   │  Agent   │ ───► │ CF Worker   │ ───► │  Phone   │
 *   │ (Python) │      │ + Durable   │      │ (Kotlin) │
 *   │          │ ◄─── │  Object     │ ◄─── │          │
 *   └──────────┘      └─────────────┘      └──────────┘
 *
 * Mobile: stacked vertically with vertical arrows.
 * ------------------------------------------------------------------------- */
function ArchitectureDiagram() {
  const nodes = [
    {
      comp: ARCH_COMPONENTS.find((c) => c.id === "agent")!,
      meta: ACTOR_META.agent,
    },
    {
      comp: ARCH_COMPONENTS.find((c) => c.id === "worker")!,
      meta: ACTOR_META.worker,
      nested: ARCH_COMPONENTS.find((c) => c.id === "do")!,
    },
    {
      comp: ARCH_COMPONENTS.find((c) => c.id === "phone")!,
      meta: ACTOR_META.phone,
    },
  ];

  return (
    <div className="rounded-[16px] border border-border bg-surface-alt p-4 sm:p-6">
      {/* Desktop / tablet: horizontal flow */}
      <div className="hidden md:grid md:grid-cols-[1fr_auto_1.4fr_auto_1fr] md:items-stretch md:gap-3">
        {nodes.map((node, i) => (
          <DiagramFragment key={node.comp.id} node={node} position={i === 1 ? "middle" : i === 0 ? "left" : "right"} />
        ))}
      </div>

      {/* Mobile: vertical flow */}
      <div className="md:hidden space-y-3">
        {nodes.map((node, i) => (
          <div key={node.comp.id}>
            <DiagramCard node={node} />
            {i < nodes.length - 1 && (
              <div className="flex flex-col items-center py-2" aria-hidden="true">
                <svg viewBox="0 0 24 24" className="w-5 h-5 text-text-secondary" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M12 5v14M5 12l7 7 7-7" />
                </svg>
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Caption */}
      <p className="mt-4 text-[11.5px] text-text-secondary leading-relaxed">
        The <span className="font-bold text-text-primary">agent</span> dials in per-command (transient). The <span className="font-bold text-text-primary">phone</span> holds a persistent WebSocket (Hibernation = $0 idle). The <span className="font-bold text-text-primary">Worker</span> is stateless; the <span className="font-bold text-text-primary">Durable Object</span> holds the live sessions map (rebuilt from <code className="font-mono text-[11px]">ctx.getWebSockets()</code> on wake).
      </p>
    </div>
  );
}

interface DiagramNode {
  comp: typeof ARCH_COMPONENTS[number];
  meta: { label: string; color: string };
  nested?: typeof ARCH_COMPONENTS[number];
}

function DiagramFragment({
  node,
  position,
}: {
  node: DiagramNode;
  position: "left" | "middle" | "right";
}) {
  return (
    <>
      <DiagramCard node={node} />
      {position !== "right" && (
        <div className="flex flex-col items-center justify-center gap-1 self-center min-w-[60px]">
          {/* Forward arrow (agent → phone) */}
          <ArrowRow
            label="command"
            color="var(--c-primary)"
            direction="right"
          />
          {/* Reverse arrow (phone → agent) */}
          <ArrowRow
            label="result + screenshot"
            color="var(--c-success)"
            direction="left"
          />
        </div>
      )}
    </>
  );
}

function DiagramCard({ node }: { node: DiagramNode }) {
  return (
    <div
      className="rounded-[14px] border bg-surface p-4 flex flex-col"
      style={{ borderColor: `color-mix(in srgb, ${node.meta.color} 40%, var(--c-border))` }}
    >
      <div className="flex items-center gap-2 mb-2">
        <NodeIcon kind={node.comp.icon as "agent" | "worker" | "do" | "phone"} color={node.meta.color} />
        <span
          className="text-[10.5px] font-bold uppercase tracking-widest"
          style={{ color: node.meta.color }}
        >
          {node.meta.label}
        </span>
      </div>
      <div className="text-[14px] font-bold text-text-primary mb-1">{node.comp.name}</div>
      <div className="text-[11.5px] text-text-secondary mb-2">{node.comp.role}</div>
      <code className="text-[10.5px] font-mono text-text-secondary mb-2 break-all">{node.comp.tech}</code>
      <p className="text-[11.5px] leading-relaxed text-text-secondary mb-2">{node.comp.desc}</p>
      <div className="text-[10.5px] font-mono text-text-secondary break-all border-t border-border/60 pt-2 mt-auto">
        {node.comp.location}
      </div>

      {/* Nested DO (inside the Worker) */}
      {node.nested && (
        <div
          className="mt-3 rounded-[10px] border border-dashed p-2.5"
          style={{ borderColor: `color-mix(in srgb, ${ACTOR_META.do.color} 50%, transparent)` }}
        >
          <div className="flex items-center gap-1.5 mb-1">
            <NodeIcon kind="do" color={ACTOR_META.do.color} />
            <span
              className="text-[9.5px] font-bold uppercase tracking-widest"
              style={{ color: ACTOR_META.do.color }}
            >
              nested
            </span>
          </div>
          <div className="text-[12px] font-bold text-text-primary">{node.nested.name}</div>
          <div className="text-[10.5px] text-text-secondary">{node.nested.role}</div>
        </div>
      )}
    </div>
  );
}

function ArrowRow({
  label,
  color,
  direction,
}: {
  label: string;
  color: string;
  direction: "left" | "right";
}) {
  return (
    <div className="flex items-center gap-1.5 w-full">
      <span className="text-[9.5px] font-bold uppercase tracking-widest text-text-secondary whitespace-nowrap">
        {label}
      </span>
      <svg
        viewBox="0 0 60 12"
        className="w-full h-3"
        fill="none"
        stroke={color}
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        {direction === "right" ? (
          <>
            <path d="M2 6 L54 6" />
            <path d="M48 2 L54 6 L48 10" />
          </>
        ) : (
          <>
            <path d="M58 6 L6 6" />
            <path d="M12 2 L6 6 L12 10" />
          </>
        )}
      </svg>
    </div>
  );
}

function NodeIcon({
  kind,
  color,
}: {
  kind: "agent" | "worker" | "do" | "phone";
  color: string;
}) {
  const stroke = color;
  return (
    <svg
      viewBox="0 0 24 24"
      className="w-4 h-4 shrink-0"
      fill="none"
      stroke={stroke}
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {kind === "agent" && (
        <>
          {/* Terminal prompt — the one-shot Python script */}
          <rect x="3" y="5" width="18" height="14" rx="2" />
          <path d="M7 9l3 3-3 3M13 15h4" />
        </>
      )}
      {kind === "worker" && (
        <>
          {/* Cloud — Cloudflare Worker */}
          <path d="M17 18a4 4 0 0 0 0-8 5 5 0 0 0-9.78 1.5A3.5 3.5 0 0 0 7 18h10z" />
        </>
      )}
      {kind === "do" && (
        <>
          {/* Hexagon — Durable Object */}
          <path d="M12 2l9 5v10l-9 5-9-5V7l9-5z" />
          <circle cx="12" cy="12" r="2.5" />
        </>
      )}
      {kind === "phone" && (
        <>
          {/* Phone — Android device */}
          <rect x="6" y="3" width="12" height="18" rx="2" />
          <path d="M10 18h4" />
        </>
      )}
    </svg>
  );
}
