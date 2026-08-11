import Link from "next/link";
import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import { TreeViewStatic } from "@/components/TreeView";
import {
  ARCH_PRINCIPLES,
  DEPENDENCY_RULES,
  DATA_FLOW_STEPS,
  IDENTITY_EXTERNAL_REFS,
  EXTENSION_PROVIDER_INTERFACES,
  CONTENT_TYPES,
  MODULE_TREE,
  ADRS,
  PHASE2_SCAFFOLD,
} from "@/lib/data";

/**
 * Architecture page (v3 — Phase 1 Architecture Plan).
 *
 * Source: APP/ani-kuta/DOCUMENTATION/16-phase1-architecture-plan.md
 * (reviewed by Plan sub-agent — 4 critical + 10 important + 16 minor flaws
 * found and fixed before this page was built).
 *
 * Sections:
 *  1. Plan summary + principles (9 principles).
 *  2. Full module tree (46 modules — TreeViewStatic, color-coded by layer).
 *  3. Dependency rules (6 strict rules).
 *  4. Data flow diagram (discovery → watch → track, with identity backbone).
 *  5. Identity system model (ContentUID + ExternalReference graph).
 *  6. Multi-extension architecture (ExtensionProvider → Video/Image/Text).
 *  7. Multi-content-type architecture (ContentType enum → per-type features).
 *  8. Phase 2 scaffold (12 modules to build first).
 *  9. ADR list + link to full plan doc.
 */
export default function ArchitecturePage() {
  return (
    <div className="space-y-6">
      {/* Hero — plan summary */}
      <Card className="!p-6 md:!p-8">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[11px] font-medium uppercase tracking-widest text-text-secondary">
              Phase 1 Architecture Plan (REALIZED)
            </span>
            <StatusDot color="var(--c-success)" size="sm" />
            <span className="text-[12px] text-text-secondary">
              All 46 planned modules built · all decisions D-001..D-186 confirmed · Nav3 REMOVED (D-150)
            </span>
          </div>
          <h2 className="text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
            ANI-KUTA{" "}
            <span className="text-text-secondary font-medium">
              — Phase 1 architecture blueprint
            </span>
          </h2>
          <p className="text-[13.5px] text-text-secondary leading-relaxed max-w-2xl">
            The full architecture plan for the ANI-KUTA rebuild: 46 modules
            built across :app (1), :core (26), :data (1), and :feature (18 —
            api/impl splits count as separate Gradle modules). ALL planned
            modules are built + CI verified GREEN on branch {`main`} (all
            feature branches merged + deleted). Phase 0–5 + Phase B/C/D/WP/HI/UP/SC/TR/NOTIF/CW/DL/DB
            all complete + Profile UI v1–v6. Nav3 REMOVED (D-150) — hand-rolled
            nav via {`mutableStateListOf<NavKey>`} + {`when(currentKey)`} dispatch
            (R7 process-death backstack survival accepted as known limitation).
            28 DB tables across 15 .sq files. Future-proof,
            modular, agent-friendly.
          </p>
          <div className="flex flex-wrap gap-2 pt-1">
            <span className="inline-flex items-center gap-1.5 h-6 px-2.5 rounded-full text-[10.5px] font-medium bg-chip border border-border text-text-secondary">
              <StatusDot color="var(--c-primary)" size="sm" />
              9 principles
            </span>
            <span className="inline-flex items-center gap-1.5 h-6 px-2.5 rounded-full text-[10.5px] font-medium bg-chip border border-border text-text-secondary">
              <StatusDot color="var(--c-warning)" size="sm" />
              6 strict dependency rules
            </span>
            <span className="inline-flex items-center gap-1.5 h-6 px-2.5 rounded-full text-[10.5px] font-medium bg-chip border border-border text-text-secondary">
              <StatusDot color="var(--c-success)" size="sm" />
              multi-extension (5 ecosystems)
            </span>
            <span className="inline-flex items-center gap-1.5 h-6 px-2.5 rounded-full text-[10.5px] font-medium bg-chip border border-border text-text-secondary">
              <StatusDot color="var(--c-secondary)" size="sm" />
              multi-content-type (VIDEO/IMAGE/TEXT)
            </span>
          </div>
        </div>
      </Card>

      {/* Architecture principles */}
      <Card>
        <SectionHeader
          eyebrow="§1 — Architecture Principles"
          title="9 principles guiding every module"
          desc="Each module is documented, single-responsibility, with clear boundaries. A new agent can work on one module without breaking others."
        />
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2.5">
          {ARCH_PRINCIPLES.map((p) => (
            <div
              key={p.n}
              className="p-4 rounded-[14px] border border-border bg-surface-alt/40 transition-all duration-200 hover:-translate-y-[1px]"
            >
              <div className="flex items-baseline gap-2 mb-1.5">
                <span
                  className="font-mono text-[12px] shrink-0"
                  style={{ color: "var(--c-primary)", minWidth: "24px" }}
                >
                  {String(p.n).padStart(2, "0")}
                </span>
                <span className="text-[13px] font-bold tracking-extra-tight text-text-primary">
                  {p.title}
                </span>
              </div>
              <p className="text-[11.5px] text-text-secondary leading-relaxed pl-[34px]">
                {p.desc}
              </p>
            </div>
          ))}
        </div>
      </Card>

      {/* Module tree */}
      <Card>
        <SectionHeader
          eyebrow="§3 — Full Module Tree"
          title="46 modules — all built ✓"
          desc=":app (1) · :core (26 infrastructure modules) · :data (1 repository impl) · :feature (18 — anime + extensions + download + watch + history + updates + debug-bubble, split api/impl per navigable feature). All 46 built + CI verified GREEN on `main`. Nav3 REMOVED (D-150) — hand-rolled nav via `mutableStateListOf<NavKey>`."
        />

        <div className="rounded-[14px] border border-border bg-surface-alt/40 p-4 overflow-x-auto">
          <TreeViewStatic nodes={MODULE_TREE} />
        </div>

        <div className="flex flex-wrap gap-4 mt-4 text-[11.5px] text-text-secondary">
          <LegendItem color="var(--c-primary)" label=":app" />
          <LegendItem color="var(--c-warning)" label=":build-logic" />
          <LegendItem color="var(--c-secondary)" label=":core:*" />
          <LegendItem color="var(--c-danger)" label=":data:*" />
          <LegendItem color="var(--c-success)" label=":feature:*" />
        </div>
      </Card>

      {/* Dependency rules */}
      <Card>
        <SectionHeader
          eyebrow="§3 — Dependency Rules (STRICT)"
          title="6 strict rules enforced by Detekt"
          desc="These rules are non-negotiable. Injekt is isolated to 2 modules + 1 :app bootstrap file (path + filename based rule, enforceable)."
        />
        <div className="space-y-2">
          {DEPENDENCY_RULES.map((r) => (
            <div
              key={r.n}
              className="flex items-start gap-3 p-3.5 rounded-[12px] border border-border bg-surface-alt/40"
            >
              <span
                className="inline-flex items-center justify-center w-7 h-7 rounded-[10px] font-mono text-[12px] font-bold shrink-0"
                style={{
                  backgroundColor: "var(--c-danger)1a",
                  color: "var(--c-danger)",
                  border: "1.5px solid var(--c-danger)",
                }}
              >
                {r.n}
              </span>
              <div className="flex-1 min-w-0">
                <div className="text-[12.5px] text-text-primary leading-relaxed font-mono">
                  {r.rule}
                </div>
              </div>
              <span
                className="inline-flex items-center h-5 px-2 rounded-full text-[9.5px] font-medium uppercase tracking-wide shrink-0"
                style={{
                  backgroundColor: "var(--c-danger)1a",
                  color: "var(--c-danger)",
                }}
              >
                {r.severity}
              </span>
            </div>
          ))}
        </div>
      </Card>

      {/* Data flow */}
      <Card>
        <SectionHeader
          eyebrow="§4 — Data Flow"
          title="Discovery → Watch → Track"
          desc="Identity is the backbone — ContentUID survives source switches. Watch progress, downloads, metadata, and tracking are all keyed by ContentUID."
        />

        {/* Vertical flow diagram */}
        <div className="rounded-[14px] border border-border bg-surface-alt/40 p-4">
          <div className="flex flex-col gap-0">
            {DATA_FLOW_STEPS.map((step, i) => (
              <div key={step.n}>
                <FlowStep step={step} />
                {i < DATA_FLOW_STEPS.length - 1 && (
                  <div className="flex items-center gap-2 pl-[28px] py-1.5">
                    <div className="w-px h-4 bg-border" aria-hidden="true" />
                    <svg width="10" height="8" viewBox="0 0 10 8" className="text-text-secondary" aria-hidden="true">
                      <path d="M5 8 L0 0 L10 0 Z" fill="currentColor" />
                    </svg>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>

        {/* Watch progress layering sub-diagram */}
        <div className="mt-6 pt-6 border-t border-border/60">
          <SectionHeader
            eyebrow="§4 — Watch Progress Layering"
            title="No reverse deps (D-038)"
            desc=":core:player needs to write progress, but cannot depend on :data:* (would create a reverse dep). Solution: :core:watch-progress contract module holds the interface; impl lives in :data:history."
            compact
          />
          <div className="rounded-[14px] border border-border bg-surface-alt/40 p-5">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 items-center text-center">
              <LayerBox
                title=":core:player"
                subtitle="MPV wrapper"
                accent="var(--c-secondary)"
                note="writes progress"
              />
              <div className="flex flex-col items-center gap-2">
                <div
                  className="px-3 py-2 rounded-[10px] border-2 font-mono text-[11.5px] font-semibold"
                  style={{
                    borderColor: "var(--c-primary)",
                    color: "var(--c-primary)",
                    backgroundColor: "var(--c-primary)0d",
                  }}
                >
                  :core:watch-progress
                  <div className="text-[9.5px] font-medium uppercase tracking-widest mt-0.5">
                    interface (contract)
                  </div>
                </div>
                <div className="text-[10.5px] text-text-secondary italic">
                  ▲ writes &nbsp;|&nbsp; ▼ reads + implements
                </div>
              </div>
              <LayerBox
                title=":data:history"
                subtitle="HistoryRepositoryImpl"
                accent="var(--c-danger)"
                note="impls the interface + reads"
              />
            </div>
            <div className="text-[11px] text-text-secondary text-center mt-4">
              No reverse dependency. <code className="font-mono text-text-primary">:core:player</code> never depends on{" "}
              <code className="font-mono text-text-primary">:data:*</code>.
            </div>
          </div>
        </div>
      </Card>

      {/* Identity system */}
      <Card>
        <SectionHeader
          eyebrow="§6 — Identity System"
          title="ContentUID + ExternalReference (graph-based, flexible, switchable)"
          desc="The app's UUID (ContentUID) sits at the center. ExternalReference nodes link it to external ecosystems (Aniyomi, AniList, MAL, Mangayomi, Shikimori, Cloudstream). Confidence levels (HIGH/MEDIUM/LOW) + user merge/split operations."
        />

        {/* Identity graph SVG */}
        <div className="rounded-[14px] border border-border bg-surface-alt/40 p-4 overflow-x-auto">
          <IdentityGraph />
        </div>

        {/* Identity details */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
          <div className="p-4 rounded-[12px] border border-border bg-surface">
            <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-2">
              ContentUID (app UUID)
            </div>
            <ul className="space-y-1.5 text-[12px] text-text-primary">
              <li><code className="font-mono text-text-secondary">uid</code>: String (UUID, app-generated, stable forever)</li>
              <li><code className="font-mono text-text-secondary">contentType</code>: ContentType (VIDEO | IMAGE | TEXT)</li>
              <li><code className="font-mono text-text-secondary">title</code>: String (canonical, best-known)</li>
              <li><code className="font-mono text-text-secondary">matchKey</code>: String (normalized title + year + type)</li>
              <li><code className="font-mono text-text-secondary">createdAt</code>: Long</li>
            </ul>
          </div>
          <div className="p-4 rounded-[12px] border border-border bg-surface">
            <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-2">
              ExternalReference
            </div>
            <ul className="space-y-1.5 text-[12px] text-text-primary">
              <li><code className="font-mono text-text-secondary">uid</code>: String (FK → ContentUID)</li>
              <li><code className="font-mono text-text-secondary">ecosystem</code>: String (aniyomi | anilist | mal | …)</li>
              <li><code className="font-mono text-text-secondary">sourceId</code>: String? (null for trackers)</li>
              <li><code className="font-mono text-text-secondary">externalId</code>: String</li>
              <li><code className="font-mono text-text-secondary">confidence</code>: HIGH | MEDIUM | LOW</li>
              <li><code className="font-mono text-text-secondary">UNIQUE(ecosystem, sourceId, externalId)</code></li>
            </ul>
          </div>
        </div>

        <div className="mt-4 p-4 rounded-[12px] border border-border bg-surface">
          <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-2">
            IdentityResolver — resolveOrCreate() algorithm
          </div>
          <ol className="space-y-1.5 text-[12px] text-text-primary">
            <li><strong>1. Exact match:</strong> ExternalReference(ecosystem, sourceId, externalId) exists → return its uid.</li>
            <li><strong>2. Tracker bridge:</strong> if caller provides trackerIds (e.g. {"{AniList → 16498}"}), find ContentUIDs with matching tracker ExternalReferences → return that uid (confidence HIGH).</li>
            <li><strong>3. Fuzzy match:</strong> matchKey matches an existing ContentUID → create new ExternalReference (confidence MEDIUM), return uid.</li>
            <li><strong>4. No match:</strong> create new ContentUID + ExternalReference (confidence HIGH for first sighting).</li>
          </ol>
          <div className="text-[11px] text-text-secondary mt-3 pt-3 border-t border-border/60">
            <strong className="text-text-primary">merge(uidA, uidB)</strong> — user-initiated merge. <strong className="text-text-primary">split(uid, refId)</strong> — splits an ExternalReference into a new ContentUID (undoable). <strong className="text-text-primary">suggestMerges()</strong> — Flow of potential matches for review.
          </div>
        </div>
      </Card>

      {/* Multi-extension architecture */}
      <Card>
        <SectionHeader
          eyebrow="§8 — Multi-Extension Architecture"
          title="ExtensionProvider → Video / Image / Text sub-interfaces"
          desc="The interface is split per content type. A provider implements whichever types it supports. Mangayomi can implement Video + Image. The UI filters providers by active ContentMode. Type-safe — can't call fetchVideoList on a manga source."
        />

        <div className="rounded-[14px] border border-border bg-surface-alt/40 p-5">
          {/* Diagram: ExtensionProvider base + 3 sub-interfaces */}
          <div className="flex flex-col items-center gap-4">
            <div
              className="px-4 py-2.5 rounded-[12px] border-2 font-mono text-[13px] font-bold text-center"
              style={{
                borderColor: "var(--c-primary)",
                color: "var(--c-primary)",
                backgroundColor: "var(--c-primary)0d",
                minWidth: "280px",
              }}
            >
              sealed interface ExtensionProvider
              <div className="text-[10px] font-medium uppercase tracking-widest mt-0.5 opacity-80">
                ecosystemId · displayName · supportedContentTypes · observeInstalledSources()
              </div>
            </div>

            <svg width="14" height="10" viewBox="0 0 14 10" className="text-text-secondary" aria-hidden="true">
              <path d="M7 10 L0 0 L14 0 Z" fill="currentColor" />
            </svg>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 w-full">
              {EXTENSION_PROVIDER_INTERFACES.map((iface) => (
                <div
                  key={iface.name}
                  className="p-3.5 rounded-[12px] border bg-surface"
                  style={{
                    borderColor: contentTypeColor(iface.contentType),
                  }}
                >
                  <div className="flex items-center gap-2 mb-2">
                    <span
                      className="w-2 h-2 rounded-full"
                      style={{ backgroundColor: contentTypeColor(iface.contentType) }}
                      aria-hidden="true"
                    />
                    <span className="font-mono text-[12px] font-semibold text-text-primary">
                      {iface.name}
                    </span>
                  </div>
                  <div
                    className="inline-flex items-center h-5 px-2 rounded-full text-[9.5px] font-bold uppercase tracking-wide mb-2.5"
                    style={{
                      backgroundColor: `${contentTypeColor(iface.contentType)}1a`,
                      color: contentTypeColor(iface.contentType),
                    }}
                  >
                    {iface.contentType}
                  </div>
                  <ul className="space-y-1 mb-2.5">
                    {iface.methods.map((m) => (
                      <li key={m} className="font-mono text-[10.5px] text-text-secondary leading-snug">
                        · {m}()
                      </li>
                    ))}
                  </ul>
                  <div className="pt-2 border-t border-border/60">
                    <div className="text-[9.5px] font-medium uppercase tracking-widest text-text-secondary mb-1">
                      Examples
                    </div>
                    <div className="text-[11px] text-text-primary leading-snug">
                      {iface.examples.join(" · ")}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="mt-4 p-4 rounded-[12px] border border-border bg-surface">
          <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-2">
            Registration (in :app)
          </div>
          <pre className="font-mono text-[11px] text-text-primary bg-canvas border border-border rounded-[8px] p-2.5 overflow-x-auto leading-relaxed">
{`single<List<ExtensionProvider>>(named("extensionProviders")) {
    listOf(
        aniyomiExtensionProvider(get(), get()),
        // mangayomiExtensionProvider(get(), get()),  // future
    )
}`}
          </pre>
          <div className="text-[11px] text-text-secondary mt-2">
            Source identity = <code className="font-mono text-text-primary">(ecosystemId, sourceId)</code>. Maps directly to ExternalReference.ecosystem + ExternalReference.sourceId. Adding a new ecosystem = one module + one Koin line.
          </div>
        </div>
      </Card>

      {/* Multi-content-type architecture */}
      <Card>
        <SectionHeader
          eyebrow="§9 — Multi-Content-Type Architecture"
          title="ContentType enum → per-type feature modules"
          desc="Three content types are planned: VIDEO (anime), IMAGE (manga), TEXT (novels). Anime ships now; manga and novels come later as modular feature modules. Per-content-type customization."
        />

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {CONTENT_TYPES.map((ct) => (
            <div
              key={ct.type}
              className="p-4 rounded-[14px] border-2 transition-all duration-200 hover:-translate-y-[1px]"
              style={{
                borderColor: ct.color,
                backgroundColor: `${ct.color}0d`,
              }}
            >
              <div className="flex items-baseline justify-between mb-2">
                <span
                  className="font-mono text-[14px] font-bold"
                  style={{ color: ct.color }}
                >
                  {ct.type}
                </span>
                <span
                  className="inline-flex items-center h-5 px-2 rounded-full text-[9.5px] font-medium uppercase tracking-wide"
                  style={{
                    backgroundColor: `${ct.color}1a`,
                    color: ct.color,
                  }}
                >
                  {ct.status === "now" ? "● Now" : "○ Future"}
                </span>
              </div>
              <div className="text-[15px] font-bold tracking-extra-tight text-text-primary mb-1">
                {ct.label}
              </div>
              <div className="font-mono text-[11px] text-text-secondary mb-2">
                {ct.featurePrefix}
              </div>
              <p className="text-[11.5px] text-text-secondary leading-relaxed">
                {ct.type === "VIDEO" && "Current focus. Anime browse/details/watch/library/history/updates/my screens. Aniyomi extensions provide sources."}
                {ct.type === "IMAGE" && "Future — Phase 7. Manga browse/details/read. Mangayomi + Cloudstream + Kotatsu extensions provide sources."}
                {ct.type === "TEXT" && "Future — Phase 8. Novel browse/details/read. TextExtensionProvider + chapter-based reader."}
              </p>
            </div>
          ))}
        </div>

        <div className="mt-4 p-4 rounded-[12px] border border-border bg-surface">
          <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-2">
            ContentMode (navigation)
          </div>
          <pre className="font-mono text-[11px] text-text-primary bg-canvas border border-border rounded-[8px] p-2.5 overflow-x-auto leading-relaxed">
{`sealed interface ContentMode {
    data object Anime : ContentMode   // VIDEO
    data object Manga : ContentMode   // IMAGE (future)
    data object Novel : ContentMode   // TEXT  (future)
}`}
          </pre>
          <div className="text-[11px] text-text-secondary mt-2">
            The bottom nav tabs render different features based on the active ContentMode. Mode switch replaces the root List&lt;NavKey&gt; for the current tab. Each mode has its own set of feature modules — no cross-mode coupling.
          </div>
        </div>
      </Card>

      {/* Phase 2 scaffold */}
      <Card>
        <SectionHeader
          eyebrow="§13 — Phase 2 Scaffold ✓"
          title="12 modules built (Phase 2 complete)"
          desc="Minimal viable structure that validated the architecture. All 12 modules built and exercised — no dead code (Ponytail). Deferred modules (:core:identity, :data:anime, :core:player, etc.) entered in Phase 3 — now also complete."
        />

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

      {/* ADR list */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Decision Records
            </div>
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              Architecture Decision Records (ADRs)
            </h3>
          </div>
          <span className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium border bg-chip border-border text-text-secondary">
            {ADRS.length} records
          </span>
        </div>

        <div className="space-y-1.5">
          {ADRS.map((adr) => {
            const statusColor =
              adr.status === "accepted"
                ? "var(--c-success)"
                : adr.status === "proposed"
                  ? "var(--c-warning)"
                  : "var(--c-text-secondary)";
            return (
              <div
                key={adr.id}
                className="flex items-start gap-3 p-3 rounded-[12px] border border-border bg-surface-alt/40 hover:bg-canvas transition-colors duration-150"
              >
                <span className="font-mono text-[11.5px] text-text-secondary shrink-0 w-16 mt-[1px]">
                  {adr.id}
                </span>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap mb-0.5">
                    <span className="text-[13px] font-semibold text-text-primary">
                      {adr.title}
                    </span>
                    <span
                      className="inline-flex items-center gap-1 h-5 px-2 rounded-full text-[9.5px] font-medium uppercase tracking-wide"
                      style={{
                        backgroundColor: `${statusColor}1a`,
                        color: statusColor,
                      }}
                    >
                      {adr.status}
                    </span>
                  </div>
                  <p className="text-[12px] text-text-secondary leading-relaxed">
                    {adr.summary}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      </Card>

      {/* Link to full plan doc */}
      <Card className="!p-6">
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div className="flex-1 min-w-0">
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Source Document
            </div>
            <h3 className="text-[16px] font-bold tracking-extra-tight text-text-primary mb-1.5">
              APP/ani-kuta/DOCUMENTATION/16-phase1-architecture-plan.md
            </h3>
            <p className="text-[12.5px] text-text-secondary leading-relaxed max-w-2xl">
              ~790 lines covering: architecture principles, full module tree
              (46 modules), dependency rules, data flow, screen map
              (originally Nav3, now hand-rolled per D-150), identity system
              design, backup/restore architecture (with §7.5 merge semantics),
              multi-extension architecture, multi-content-type architecture,
              customizable UI system, ad system (deferred), console logging,
              Phase 2 scaffold (12 modules), open questions, sub-agent review notes.
            </p>
            <p className="text-[11px] text-text-secondary mt-2">
              Reviewed by Plan sub-agent (Task 5-REVIEW): 4 critical + 10
              important + 16 minor flaws found and fixed before the plan was
              marked complete.
            </p>
          </div>
          <Link
            href="/progress/"
            className="inline-flex items-center gap-2 h-9 px-[18px] rounded-[12px] text-[13.5px] font-medium bg-chip border border-border text-text-secondary transition-all duration-200 hover:translate-y-[-1px] hover:text-text-primary no-underline shrink-0"
          >
            View progress →
          </Link>
        </div>
      </Card>
    </div>
  );
}

/* ---------- Sub-components ---------- */

function SectionHeader({
  eyebrow,
  title,
  desc,
  compact = false,
}: {
  eyebrow: string;
  title: string;
  desc: string;
  compact?: boolean;
}) {
  return (
    <div className={compact ? "mb-3" : "mb-4"}>
      <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
        {eyebrow}
      </div>
      <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary mb-1.5">
        {title}
      </h3>
      <p className="text-[12.5px] text-text-secondary leading-relaxed max-w-3xl">
        {desc}
      </p>
    </div>
  );
}

function LegendItem({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <StatusDot color={color} size="sm" />
      <span className="font-mono">{label}</span>
    </span>
  );
}

function FlowStep({
  step,
}: {
  step: { n: number; module: string; desc: string; isBackbone?: boolean };
}) {
  const accent = step.isBackbone ? "var(--c-primary)" : "var(--c-secondary)";
  return (
    <div
      className={`flex items-start gap-3 p-3 rounded-[12px] border ${
        step.isBackbone ? "border-[var(--c-primary)]/40 bg-[var(--c-primary)]/5" : "border-border bg-surface"
      }`}
    >
      <span
        className="inline-flex items-center justify-center w-7 h-7 rounded-[10px] font-mono text-[12px] font-bold shrink-0"
        style={{
          backgroundColor: `${accent}1a`,
          color: accent,
          border: `1.5px solid ${accent}`,
        }}
      >
        {step.n}
      </span>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="font-mono text-[12px] font-semibold text-text-primary">
            {step.module}
          </span>
          {step.isBackbone && (
            <span
              className="inline-flex items-center h-5 px-2 rounded-full text-[9.5px] font-medium uppercase tracking-wide"
              style={{
                backgroundColor: "var(--c-primary)1a",
                color: "var(--c-primary)",
              }}
            >
              ★ identity backbone
            </span>
          )}
        </div>
        <div className="text-[11.5px] text-text-secondary leading-snug mt-0.5">
          {step.desc}
        </div>
      </div>
    </div>
  );
}

function LayerBox({
  title,
  subtitle,
  accent,
  note,
}: {
  title: string;
  subtitle: string;
  accent: string;
  note: string;
}) {
  return (
    <div
      className="p-4 rounded-[12px] border-2"
      style={{
        borderColor: accent,
        backgroundColor: `${accent}0d`,
      }}
    >
      <div className="font-mono text-[13px] font-bold text-text-primary mb-1">
        {title}
      </div>
      <div className="text-[11px] text-text-secondary mb-2">{subtitle}</div>
      <div
        className="text-[10px] font-medium uppercase tracking-widest"
        style={{ color: accent }}
      >
        {note}
      </div>
    </div>
  );
}

function contentTypeColor(type: "VIDEO" | "IMAGE" | "TEXT"): string {
  switch (type) {
    case "VIDEO":
      return "var(--c-success)";
    case "IMAGE":
      return "var(--c-warning)";
    case "TEXT":
      return "var(--c-secondary)";
  }
}

/**
 * Identity graph SVG — ContentUID at the center, ExternalReference nodes
 * around it. Edges colored by confidence level.
 */
function IdentityGraph() {
  const cx = 200;
  const cy = 180;
  const radius = 130;

  const confColor = (c: "HIGH" | "MEDIUM" | "LOW") => {
    switch (c) {
      case "HIGH":
        return "var(--c-success)";
      case "MEDIUM":
        return "var(--c-warning)";
      case "LOW":
        return "var(--c-danger)";
    }
  };

  return (
    <div className="overflow-x-auto">
      <svg
        width="400"
        height="360"
        viewBox="0 0 400 360"
        role="img"
        aria-label="ContentUID + ExternalReference identity graph"
        className="min-w-[400px] mx-auto"
      >
        {/* Edges */}
        {IDENTITY_EXTERNAL_REFS.map((ref, i) => {
          const rad = (ref.angle * Math.PI) / 180;
          const x = cx + radius * Math.cos(rad);
          const y = cy + radius * Math.sin(rad);
          const color = confColor(ref.confidence);
          return (
            <line
              key={`edge-${i}`}
              x1={cx}
              y1={cy}
              x2={x}
              y2={y}
              stroke={color}
              strokeWidth="1.5"
              strokeDasharray={ref.confidence === "HIGH" ? "0" : ref.confidence === "MEDIUM" ? "4 3" : "2 4"}
              opacity="0.7"
            />
          );
        })}

        {/* ExternalReference nodes */}
        {IDENTITY_EXTERNAL_REFS.map((ref, i) => {
          const rad = (ref.angle * Math.PI) / 180;
          const x = cx + radius * Math.cos(rad);
          const y = cy + radius * Math.sin(rad);
          const color = confColor(ref.confidence);
          return (
            <g key={`node-${i}`}>
              <rect
                x={x - 56}
                y={y - 22}
                width="112"
                height="44"
                rx="8"
                fill="var(--c-surface)"
                stroke={color}
                strokeWidth="1.5"
              />
              <text
                x={x}
                y={y - 4}
                textAnchor="middle"
                className="font-mono"
                fontSize="10.5"
                fontWeight="700"
                fill="var(--c-text-primary)"
              >
                {ref.ecosystem}
              </text>
              <text
                x={x}
                y={y + 9}
                textAnchor="middle"
                className="font-mono"
                fontSize="9"
                fill="var(--c-text-secondary)"
              >
                {ref.externalId}
              </text>
              <text
                x={x}
                y={y + 19}
                textAnchor="middle"
                className="font-mono"
                fontSize="8"
                fontWeight="700"
                fill={color}
              >
                {ref.confidence}
              </text>
            </g>
          );
        })}

        {/* Center: ContentUID */}
        <circle
          cx={cx}
          cy={cy}
          r="56"
          fill="var(--c-primary)1a"
          stroke="var(--c-primary)"
          strokeWidth="2.5"
        />
        <text
          x={cx}
          y={cy - 8}
          textAnchor="middle"
          className="font-mono"
          fontSize="13"
          fontWeight="700"
          fill="var(--c-primary)"
        >
          ContentUID
        </text>
        <text
          x={cx}
          y={cy + 6}
          textAnchor="middle"
          className="font-mono"
          fontSize="9"
          fill="var(--c-text-secondary)"
        >
          uid: UUID
        </text>
        <text
          x={cx}
          y={cy + 18}
          textAnchor="middle"
          className="font-mono"
          fontSize="9"
          fill="var(--c-text-secondary)"
        >
          stable forever
        </text>
      </svg>

      {/* Legend */}
      <div className="flex flex-wrap justify-center gap-4 mt-3 text-[11px] text-text-secondary">
        <span className="inline-flex items-center gap-1.5">
          <span className="w-3 h-px bg-[var(--c-success)]" />
          HIGH confidence (solid)
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="w-3 h-px border-t border-dashed border-[var(--c-warning)]" />
          MEDIUM (dashed)
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="w-3 h-px border-t border-dotted border-[var(--c-danger)]" />
          LOW (dotted)
        </span>
      </div>
    </div>
  );
}
