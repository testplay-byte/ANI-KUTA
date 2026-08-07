import { Card } from "@/components/Card";
import { StatusDot } from "@/components/StatusDot";
import {
  APP_DARK_SURFACE_RAMP,
  APP_DARK_TEXT_TIERS,
  APP_DARK_ACCENT_ROLES,
  APP_LIGHT_SURFACE_RAMP,
  APP_AMOLED_RAMP,
  APP_ACCENT_PRESETS,
  APP_TYPE_SCALE,
  APP_KEY_COMPONENTS,
  APP_THEMES,
  APP_DESIGN_PRINCIPLES,
  type ColorSwatch,
  type AccentPreset,
} from "@/lib/data";

/**
 * Design Language page.
 *
 * This page is ABOUT the ANI-KUTA app's design language (lime accent,
 * dark warm surfaces, AMOLED, accent presets, Roboto typography, key
 * components). The colors shown here are CONTENT (swatches) — the dashboard's
 * own UI stays MEMORY OS (warm canvas, sidebar, indigo accents) per DESIGN.md.
 *
 * Source: APP/ani-kuta/DESIGN-LANGUAGE.md (~1150 lines, every value quoted
 * directly from the old project's source code in REFERENCES/old-kuta/ANIKUTA/).
 */
export default function DesignPage() {
  return (
    <div className="space-y-6">
      {/* Hero — design language summary */}
      <Card className="!p-6 md:!p-8">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[11px] font-medium uppercase tracking-widest text-text-secondary">
              App Design Language
            </span>
            <StatusDot color="var(--c-success)" size="sm" />
            <span className="text-[12px] text-text-secondary">
              Canonical reference · APP/ani-kuta/DESIGN-LANGUAGE.md (~1150 lines)
            </span>
          </div>
          <h2 className="text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
            ANI-KUTA{" "}
            <span className="text-text-secondary font-medium">
              — dark-first, anime-focused, lime-accented
            </span>
          </h2>
          <p className="text-[13.5px] text-text-secondary leading-relaxed max-w-2xl">
            The new app&apos;s design language:{" "}
            <strong className="text-text-primary">
              lime green identity (#B1F256)
            </strong>{" "}
            on warm-purple-tinted dark surfaces (#14111F → #3D3656 5-tier
            ramp). ExtraBold Roboto headings with tight letter-spacing.
            Translucent surfaceVariant cards at 40–50% alpha. Floating pill
            bottom nav. Cover-color dynamic theming on details + watch pages.
            Owner quote:{" "}
            <em>
              &ldquo;The old project&apos;s design language is perfect — there
              are no issues.&rdquo;
            </em>
          </p>

          {/* Five signature tells */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-2 pt-1">
            {SIGNATURE_TELLS.map((t, i) => (
              <div
                key={i}
                className="p-3 rounded-[12px] border border-border bg-surface-alt/40"
              >
                <div className="text-[10px] font-medium uppercase tracking-widest text-text-secondary mb-1.5">
                  {i + 1}
                </div>
                <div className="text-[12px] font-semibold text-text-primary leading-snug">
                  {t}
                </div>
              </div>
            ))}
          </div>
        </div>
      </Card>

      {/* Three themes overview */}
      <Card>
        <SectionHeader
          eyebrow="Themes"
          title="Three theme modes"
          desc="Dark is the default + most polished. Light is warm-neutral (no purple tint, cards darker than bg). AMOLED is pure black with subtle grey cards."
        />
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {APP_THEMES.map((theme) => (
            <ThemeCard key={theme.id} theme={theme} />
          ))}
        </div>
      </Card>

      {/* Dark surface ramp */}
      <Card>
        <SectionHeader
          eyebrow="Dark Theme · Surfaces"
          title="5-tier tonal surface ramp"
          desc="The dark palette uses a 5-step tonal ramp. Each tier is progressively lighter, giving clear elevation hierarchy without resorting to drop shadows."
        />
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2">
          {APP_DARK_SURFACE_RAMP.map((sw) => (
            <SwatchCard key={sw.token} sw={sw} />
          ))}
        </div>

        <div className="mt-6 pt-6 border-t border-border/60">
          <SectionHeader
            eyebrow="Dark Theme · Text Tiers"
            title="3-tier text ramp"
            desc="Lavender-tinted white for warmth, not pure #FFFFFF."
            compact
          />
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
            {APP_DARK_TEXT_TIERS.map((sw) => (
              <SwatchCard key={sw.token} sw={sw} />
            ))}
          </div>
        </div>

        <div className="mt-6 pt-6 border-t border-border/60">
          <SectionHeader
            eyebrow="Dark Theme · Accent Roles"
            title="Lime accent — M3 color roles"
            desc="The ANI-KUTA lime green #B1F256 is the primary. onPrimary auto-contrasts to dark text (luminance ~0.83)."
            compact
          />
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            {APP_DARK_ACCENT_ROLES.map((sw) => (
              <SwatchCard key={sw.token} sw={sw} />
            ))}
          </div>
        </div>
      </Card>

      {/* Light + AMOLED ramps */}
      <Card>
        <SectionHeader
          eyebrow="Light Theme · Warm-Neutral Surfaces"
          title="Light surface ramp (cards darker than bg)"
          desc="Rebuilt to drop the default M3 purple tint. Warm off-white #FAF9F6 background, cards darker than bg for clear hierarchy."
        />
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2">
          {APP_LIGHT_SURFACE_RAMP.map((sw) => (
            <SwatchCard key={sw.token} sw={sw} />
          ))}
        </div>

        <div className="mt-6 pt-6 border-t border-border/60">
          <SectionHeader
            eyebrow="AMOLED Theme · Pure Black + Grey"
            title="AMOLED surface ramp"
            desc="Pure #000000 background (stays pure for OLED). Subtle grey tints make cards visible without being obviously grey."
            compact
          />
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            {APP_AMOLED_RAMP.map((sw) => (
              <SwatchCard key={sw.token} sw={sw} />
            ))}
          </div>
        </div>
      </Card>

      {/* Accent presets */}
      <Card>
        <SectionHeader
          eyebrow="Accent System"
          title="10 accent-only + 5 full-palette + 1 custom"
          desc="The accent is a separate axis from theme mode. 10 accent-only presets override just the primary-family M3 roles; 5 full-palette presets override background + card + text + accent. Custom slot opens a color picker sheet."
        />
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2.5">
          {APP_ACCENT_PRESETS.map((p) => (
            <AccentPresetCard key={p.name} preset={p} />
          ))}
        </div>
      </Card>

      {/* Typography */}
      <Card>
        <SectionHeader
          eyebrow="Typography"
          title="Bundled Roboto · ExtraBold everywhere"
          desc="Roboto is bundled (regular, medium, bold, black) so ExtraBold (800) and Black (900) render correctly on all devices — many Android skins ship without ExtraBold. Every 'bold' usage uses ExtraBold (800), not Bold (700), for visibility on subpixel rendering."
        />

        {/* Type specimen — show actual Roboto-like renderings using weights */}
        <div className="rounded-[14px] border border-border bg-surface-alt/40 p-5 mb-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <TypeSpecimen
              label="Display Large · 36sp ExtraBold · -0.02sp"
              sample="Browse"
              sizePx={36}
              weight={800}
              letterSpacing="-0.02em"
            />
            <TypeSpecimen
              label="Headline Medium · 26sp ExtraBold · -0.01sp"
              sample="New Update Available"
              sizePx={26}
              weight={800}
              letterSpacing="-0.01em"
            />
            <TypeSpecimen
              label="Title Large · 16sp ExtraBold"
              sample="More Row Title"
              sizePx={16}
              weight={800}
            />
            <TypeSpecimen
              label="Body Medium · 14sp Medium · 0.25sp"
              sample="Synopsis body, slider descriptions, secondary content."
              sizePx={14}
              weight={500}
              letterSpacing="0.25px"
            />
          </div>
        </div>

        {/* Full type scale table */}
        <div className="rounded-[14px] border border-border overflow-hidden">
          <table className="w-full text-[12px]">
            <thead>
              <tr className="bg-canvas border-b border-border">
                <th className="text-left font-medium uppercase tracking-widest text-text-secondary px-3 py-2 text-[10px]">Style</th>
                <th className="text-left font-medium uppercase tracking-widest text-text-secondary px-3 py-2 text-[10px]">Size</th>
                <th className="text-left font-medium uppercase tracking-widest text-text-secondary px-3 py-2 text-[10px]">Weight</th>
                <th className="text-left font-medium uppercase tracking-widest text-text-secondary px-3 py-2 text-[10px]">Used for</th>
              </tr>
            </thead>
            <tbody>
              {APP_TYPE_SCALE.map((row, i) => (
                <tr
                  key={row.style}
                  className={i % 2 === 0 ? "bg-surface/40" : ""}
                >
                  <td className="px-3 py-2 font-mono text-text-primary">{row.style}</td>
                  <td className="px-3 py-2 font-mono text-text-secondary">{row.size}</td>
                  <td className="px-3 py-2 text-text-secondary">{row.weight}</td>
                  <td className="px-3 py-2 text-text-secondary">{row.usedFor}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Key components */}
      <Card>
        <SectionHeader
          eyebrow="Key Components"
          title="9 signature components"
          desc="Each component below is documented in DESIGN-LANGUAGE.md with its full spec (radius, padding, colors, motion) and code snippet. Together they form the ANI-KUTA visual signature."
        />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {APP_KEY_COMPONENTS.map((c, i) => (
            <ComponentCard key={i} n={i + 1} name={c.name} spec={c.spec} note={c.note} />
          ))}
        </div>
      </Card>

      {/* Floating pill bottom nav visual demo */}
      <Card>
        <SectionHeader
          eyebrow="Component Demo"
          title="Floating pill bottom nav"
          desc="28dp radius · 8dp shadow · 58dp outer / 42dp pill · content scrolls BEHIND. The active pill expands (content-sized, no weight); inactive items share remaining space. NOT in Scaffold.bottomBar."
        />

        {/* Simulated dark phone screen showing the pill nav */}
        <div className="rounded-[18px] overflow-hidden border border-border" style={{ background: "#14111F" }}>
          {/* Simulated content */}
          <div className="p-4 sm:p-6">
            <div className="text-[10px] font-medium uppercase tracking-widest mb-1" style={{ color: "#A89EC0" }}>
              Library
            </div>
            <div className="text-[24px] font-bold mb-3" style={{ color: "#ECE6F5", letterSpacing: "-0.02em" }}>
              Recently Watched
            </div>
            <div className="grid grid-cols-3 gap-2 mb-4">
              {[0, 1, 2].map((i) => (
                <div
                  key={i}
                  className="aspect-[2/3] rounded-[10px] flex items-end p-1.5"
                  style={{ background: i === 0 ? "#4A6B1A" : i === 1 ? "#332D4C" : "#2A2540" }}
                >
                  <span
                    className="text-[8px] font-bold leading-tight"
                    style={{ color: i === 0 ? "#D4F5A0" : "#ECE6F5" }}
                  >
                    Title {i + 1}
                  </span>
                </div>
              ))}
            </div>
            <div
              className="h-2 rounded-full mb-1"
              style={{ background: "#2A2540" }}
            />
            <div
              className="h-2 rounded-full w-3/4 mb-1"
              style={{ background: "#2A2540" }}
            />
            <div
              className="h-2 rounded-full w-1/2"
              style={{ background: "#2A2540" }}
            />
          </div>

          {/* The floating pill nav */}
          <div className="px-3 pb-3 -mt-2 relative">
            <div
              className="flex items-center gap-1 h-[42px] px-1.5 rounded-[28px] shadow-[0_8px_24px_rgba(0,0,0,0.5)]"
              style={{ background: "#2A2540" }}
            >
              {/* Active item — expanded with label */}
              <div
                className="flex items-center gap-1.5 h-[34px] px-3 rounded-full"
                style={{ background: "#4A6B1A" }}
              >
                <PillIcon name="home" active />
                <span
                  className="text-[11px] font-semibold"
                  style={{ color: "#D4F5A0" }}
                >
                  Browse
                </span>
              </div>
              {/* Inactive items */}
              <div className="flex-1 flex items-center justify-around">
                <PillIcon name="library" />
                <PillIcon name="search" />
                <PillIcon name="person" />
              </div>
            </div>
          </div>
        </div>

        <div className="text-[11px] text-text-secondary mt-3">
          Live demo with the lime accent on dark warm surfaces. Active pill uses primaryContainer (#4A6B1A) + onPrimaryContainer (#D4F5A0) text.
        </div>
      </Card>

      {/* ScrollBlurOverlay demo */}
      <Card>
        <SectionHeader
          eyebrow="Special Effect"
          title="ScrollBlurOverlay — the owner-praised top blur"
          desc="NOT a real RenderEffect blur — a 6-stop vertical gradient scrim whose color matches the screen background. As scrolling content passes beneath, the solid-to-transparent fade creates an optical illusion of frosted glass. GPU-cheap (one drawRect per frame), never causes recomposition."
        />

        {/* Simulated screen showing the blur effect */}
        <div className="rounded-[18px] overflow-hidden border border-border" style={{ background: "#14111F" }}>
          <div className="relative h-[260px] overflow-hidden">
            {/* Pinned header */}
            <div
              className="absolute top-0 left-0 right-0 z-20 px-4 pt-4 pb-2"
              style={{ background: "#14111F" }}
            >
              <div className="text-[10px] font-medium uppercase tracking-widest mb-0.5" style={{ color: "#A89EC0" }}>
                Settings
              </div>
              <div className="text-[20px] font-bold" style={{ color: "#ECE6F5", letterSpacing: "-0.02em" }}>
                Appearance
              </div>
            </div>

            {/* Scroll content (simulated) */}
            <div className="absolute inset-0 pt-[68px] z-10">
              <div className="px-4 space-y-2">
                {/* Faded top row (representing content under blur) */}
                <div
                  className="rounded-[12px] p-3 opacity-30"
                  style={{ background: "#2A2540" }}
                >
                  <div className="h-2 rounded-full w-1/3 mb-1.5" style={{ background: "#6E6688" }} />
                  <div className="h-2 rounded-full w-2/3" style={{ background: "#6E6688" }} />
                </div>
                {/* The 6-stop gradient scrim (visible portion) */}
                <div
                  className="h-6 -mx-4"
                  style={{
                    background:
                      "linear-gradient(to bottom, #14111F 0%, rgba(20,17,31,0.92) 15%, rgba(20,17,31,0.70) 35%, rgba(20,17,31,0.42) 55%, rgba(20,17,31,0.18) 75%, rgba(20,17,31,0.05) 90%, transparent 100%)",
                  }}
                />
                {/* Fully visible rows */}
                {[0, 1, 2].map((i) => (
                  <div
                    key={i}
                    className="rounded-[12px] p-3 flex items-center gap-3"
                    style={{ background: "rgba(42, 37, 64, 0.4)" }}
                  >
                    <div
                      className="w-5 h-5 rounded-full shrink-0"
                      style={{ background: "#B1F256" }}
                    />
                    <div className="flex-1">
                      <div className="h-2 rounded-full w-1/2 mb-1" style={{ background: "#ECE6F5" }} />
                      <div className="h-2 rounded-full w-1/3" style={{ background: "#A89EC0" }} />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

        <div className="text-[11px] text-text-secondary mt-3">
          The gradient scrim (the colored strip below the pinned header) creates the optical illusion of blur. Same technique as iOS navigation bars + M3 top app bars.
        </div>
      </Card>

      {/* Design principles */}
      <Card>
        <SectionHeader
          eyebrow="Codified Principles"
          title="12 design principles (verified in code)"
          desc="Every screen ships with all 12. If a new screen ships without one of these, it&apos;s off-brand."
        />
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2.5">
          {APP_DESIGN_PRINCIPLES.map((p) => (
            <div
              key={p.n}
              className="p-3.5 rounded-[12px] border border-border bg-surface-alt/40 transition-all duration-200 hover:-translate-y-[1px]"
            >
              <div className="flex items-baseline gap-2 mb-1.5">
                <span
                  className="font-mono text-[11px] text-text-secondary shrink-0"
                  style={{ minWidth: "20px" }}
                >
                  {String(p.n).padStart(2, "0")}
                </span>
                <span className="text-[12.5px] font-semibold text-text-primary leading-snug">
                  {p.title}
                </span>
              </div>
              <p className="text-[11.5px] text-text-secondary leading-relaxed pl-[28px]">
                {p.desc}
              </p>
            </div>
          ))}
        </div>
      </Card>

      {/* What ANI-KUTA does NOT do */}
      <Card>
        <SectionHeader
          eyebrow="Anti-patterns"
          title="What ANI-KUTA deliberately does NOT do"
          desc="A short list of common Material 3 / Compose patterns that are forbidden in the ANI-KUTA codebase."
        />
        <div className="space-y-1.5">
          {DOES_NOT.map((d, i) => (
            <div
              key={i}
              className="flex items-start gap-2.5 p-2.5 rounded-[10px] border border-border bg-surface-alt/40"
            >
              <span
                className="inline-flex items-center justify-center w-5 h-5 rounded-full shrink-0 text-[10px] font-bold"
                style={{ background: "var(--c-danger)1a", color: "var(--c-danger)" }}
                aria-hidden="true"
              >
                ✕
              </span>
              <span className="text-[12.5px] text-text-primary leading-snug">{d}</span>
            </div>
          ))}
        </div>
      </Card>

      {/* Footer link to source doc */}
      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Source Document
            </div>
            <h3 className="text-[16px] font-bold tracking-extra-tight text-text-primary mb-1">
              APP/ani-kuta/DESIGN-LANGUAGE.md
            </h3>
            <p className="text-[12.5px] text-text-secondary leading-relaxed max-w-2xl">
              ~1150 lines covering design philosophy, color system, typography,
              spacing &amp; layout, shapes &amp; radii, shadows &amp; elevation,
              motion, 16 components, 10 screen patterns, 9 special effects,
              iconography, and a file-to-feature cross-reference appendix. Every
              color value, dimension, duration, and easing is quoted directly
              from the old project&apos;s source code.
            </p>
          </div>
          <span
            className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full text-[11px] font-medium shrink-0"
            style={{ background: "var(--c-success)1a", color: "var(--c-success)" }}
          >
            <StatusDot color="var(--c-success)" size="sm" />
            Complete
          </span>
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

function SwatchCard({ sw }: { sw: ColorSwatch }) {
  const textIsDark = sw.textOn === "dark";
  return (
    <div className="rounded-[12px] border border-border overflow-hidden transition-all duration-200 hover:-translate-y-[1px]">
      {/* Color block */}
      <div
        className="h-[68px] flex items-end p-2.5"
        style={{ backgroundColor: sw.hex }}
      >
        <span
          className="font-mono text-[11px] font-semibold"
          style={{ color: textIsDark ? "#1a1a1a" : "#ffffff" }}
        >
          {sw.hex}
        </span>
      </div>
      {/* Token + role */}
      <div className="p-2.5 bg-surface">
        <div className="font-mono text-[11px] font-semibold text-text-primary truncate">
          {sw.token}
        </div>
        <div className="text-[10.5px] text-text-secondary leading-snug mt-0.5 line-clamp-2">
          {sw.role}
        </div>
      </div>
    </div>
  );
}

function AccentPresetCard({ preset }: { preset: AccentPreset }) {
  const isCustom = preset.kind === "custom";
  return (
    <div
      className={`rounded-[14px] border overflow-hidden transition-all duration-200 hover:-translate-y-[1px] ${
        isCustom ? "border-dashed" : ""
      }`}
      style={{ borderColor: isCustom ? "var(--c-border)" : "var(--c-border)" }}
    >
      {/* Color block(s) */}
      <div className="h-[64px] flex">
        {preset.kind === "accent" && (
          <div
            className="flex-1 flex items-end p-2"
            style={{ backgroundColor: preset.hex }}
          >
            <span
              className="font-mono text-[10px] font-semibold"
              style={{ color: luminance(preset.hex) > 0.5 ? "#1a1a1a" : "#ffffff" }}
            >
              {preset.hex}
            </span>
          </div>
        )}
        {preset.kind === "palette" && (
          <>
            <div className="flex-1" style={{ backgroundColor: preset.bg }} />
            <div className="flex-1" style={{ backgroundColor: preset.card }} />
            <div className="flex-[1.5]" style={{ backgroundColor: preset.hex }} />
          </>
        )}
        {preset.kind === "custom" && (
          <div
            className="flex-1 flex items-center justify-center"
            style={{
              background:
                "conic-gradient(from 0deg, #FF5252, #FFC107, #B1F256, #00BCD4, #9C27B0, #FF5252)",
            }}
          >
            <span className="text-[14px] font-bold text-white drop-shadow">
              ?
            </span>
          </div>
        )}
      </div>
      {/* Name + kind */}
      <div className="p-2.5 bg-surface">
        <div className="text-[11.5px] font-semibold text-text-primary truncate">
          {preset.name}
        </div>
        <div className="text-[10px] text-text-secondary uppercase tracking-wide mt-0.5">
          {preset.kind}
        </div>
      </div>
    </div>
  );
}

function luminance(hex: string): number {
  // Quick sRGB luminance estimate (used for auto-contrast text).
  const h = hex.replace("#", "");
  if (h.length !== 6) return 0.5;
  const r = parseInt(h.slice(0, 2), 16) / 255;
  const g = parseInt(h.slice(2, 4), 16) / 255;
  const b = parseInt(h.slice(4, 6), 16) / 255;
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function TypeSpecimen({
  label,
  sample,
  sizePx,
  weight,
  letterSpacing,
}: {
  label: string;
  sample: string;
  sizePx: number;
  weight: number;
  letterSpacing?: string;
}) {
  return (
    <div className="p-3 rounded-[10px] border border-border bg-surface">
      <div className="text-[10px] font-medium uppercase tracking-widest text-text-secondary mb-2">
        {label}
      </div>
      <div
        style={{
          fontSize: `${sizePx}px`,
          fontWeight: weight,
          letterSpacing: letterSpacing ?? "normal",
          lineHeight: 1.1,
          color: "var(--c-text-primary)",
          fontFamily: "var(--font-inter), system-ui, sans-serif",
        }}
      >
        {sample}
      </div>
    </div>
  );
}

function ComponentCard({
  n,
  name,
  spec,
  note,
}: {
  n: number;
  name: string;
  spec: string;
  note: string;
}) {
  return (
    <div className="p-4 rounded-[14px] border border-border bg-surface-alt/40 transition-all duration-200 hover:-translate-y-[1px]">
      <div className="flex items-center justify-between gap-2 mb-2">
        <span className="text-[10px] font-medium uppercase tracking-widest text-text-secondary">
          Component {String(n).padStart(2, "0")}
        </span>
      </div>
      <h4 className="text-[14px] font-bold tracking-extra-tight text-text-primary mb-2">
        {name}
      </h4>
      <div className="font-mono text-[11.5px] text-text-primary bg-canvas border border-border rounded-[8px] p-2 mb-2 leading-relaxed">
        {spec}
      </div>
      <p className="text-[11.5px] text-text-secondary leading-relaxed">
        {note}
      </p>
    </div>
  );
}

function ThemeCard({
  theme,
}: {
  theme: { id: string; name: string; bg: string; surface: string; accent: string; desc: string };
}) {
  return (
    <div className="rounded-[16px] border border-border overflow-hidden transition-all duration-200 hover:-translate-y-[1px]">
      {/* Mini preview */}
      <div
        className="p-4 h-[120px] flex flex-col gap-2"
        style={{ backgroundColor: theme.bg }}
      >
        {/* Header row */}
        <div className="flex items-center justify-between">
          <span
            className="text-[10px] font-medium uppercase tracking-widest"
            style={{ color: theme.bg === "#FAF9F6" ? "#5C5A54" : "#A89EC0" }}
          >
            Preview
          </span>
          <div
            className="h-5 px-2 rounded-full flex items-center text-[9px] font-bold"
            style={{
              backgroundColor: theme.accent,
              color: luminance(theme.accent) > 0.5 ? "#1a1a1a" : "#ffffff",
            }}
          >
            PRIMARY
          </div>
        </div>
        {/* Cards */}
        <div className="flex gap-2 flex-1">
          <div
            className="flex-1 rounded-[10px] p-2"
            style={{ backgroundColor: theme.surface }}
          >
            <div
              className="h-1.5 rounded-full w-1/2 mb-1"
              style={{ backgroundColor: theme.accent, opacity: 0.7 }}
            />
            <div
              className="h-1.5 rounded-full w-3/4"
              style={{
                backgroundColor: theme.bg === "#FAF9F6" ? "#5C5A54" : "#ECE6F5",
                opacity: 0.5,
              }}
            />
          </div>
          <div
            className="flex-1 rounded-[10px] p-2"
            style={{ backgroundColor: theme.surface }}
          >
            <div
              className="h-1.5 rounded-full w-2/3 mb-1"
              style={{ backgroundColor: theme.accent, opacity: 0.7 }}
            />
            <div
              className="h-1.5 rounded-full w-1/2"
              style={{
                backgroundColor: theme.bg === "#FAF9F6" ? "#5C5A54" : "#ECE6F5",
                opacity: 0.5,
              }}
            />
          </div>
        </div>
      </div>
      {/* Info */}
      <div className="p-3.5 bg-surface">
        <div className="flex items-center gap-2 mb-1">
          <h4 className="text-[13.5px] font-bold tracking-extra-tight text-text-primary">
            {theme.name}
          </h4>
        </div>
        <p className="text-[11.5px] text-text-secondary leading-relaxed mb-2.5">
          {theme.desc}
        </p>
        <div className="flex gap-1.5 flex-wrap">
          <MiniHex label="bg" hex={theme.bg} />
          <MiniHex label="surface" hex={theme.surface} />
          <MiniHex label="accent" hex={theme.accent} />
        </div>
      </div>
    </div>
  );
}

function MiniHex({ label, hex }: { label: string; hex: string }) {
  return (
    <div className="flex items-center gap-1">
      <span
        className="w-3 h-3 rounded-full border border-border"
        style={{ backgroundColor: hex }}
        aria-hidden="true"
      />
      <span className="font-mono text-[10px] text-text-secondary">{hex}</span>
    </div>
  );
}

function PillIcon({
  name,
  active = false,
}: {
  name: "home" | "library" | "search" | "person";
  active?: boolean;
}) {
  const color = active ? "#D4F5A0" : "#A89EC0";
  const icons: Record<string, React.ReactNode> = {
    home: <path d="M3 11l9-7 9 7v8a2 2 0 01-2 2h-3v-6H8v6H5a2 2 0 01-2-2v-8z" />,
    library: (
      <>
        <rect x="4" y="4" width="6" height="16" rx="1" />
        <rect x="14" y="4" width="6" height="10" rx="1" />
      </>
    ),
    search: (
      <>
        <circle cx="11" cy="11" r="6" />
        <path d="M21 21l-4.35-4.35" />
      </>
    ),
    person: (
      <>
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21v-1a8 8 0 0116 0v1" />
      </>
    ),
  };
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="w-[18px] h-[18px]"
      aria-hidden="true"
    >
      {icons[name]}
    </svg>
  );
}

const SIGNATURE_TELLS = [
  "Lime accent + dark warm surfaces (#B1F256 on #14111F-family darks)",
  "ExtraBold Roboto headings with -0.02sp letter-spacing",
  "Translucent surfaceVariant cards @ 0.4–0.5 alpha, 12–16dp corners",
  "Floating pill bottom nav (28dp radius, 8dp shadow, content scrolls behind)",
  "Accent-colored left-aligned section labels + scroll-blur gradient",
];

const DOES_NOT = [
  "No emojis anywhere. Material vector icons only.",
  "No Material 3 Card component with default elevation. All cards are Surface with explicit translucent color + corner shape.",
  "No Scaffold.bottomBar. The bottom nav floats over scrolling content.",
  "No bottom-sheet drag handles. A custom header replaces it.",
  "No Modifier.blur() on rounded surfaces — it muddies the corners. Use a themed translucent color or a gradient scrim instead.",
  "No bouncy / spring animations. Everything is tween(300, FastOutSlowInEasing).",
  "No full-screen bottom sheets (except CustomColorSheet which needs the room for 4 color pickers). Sheets are partial-height.",
  "No hardcoded Color.White text on primary buttons. Always onPrimary so it adapts to the accent's luminance.",
  "No purple-tinted light backgrounds. Warm-neutral only.",
  "No RenderEffect blur on scrolling content. Too expensive; gradient scrim instead.",
];
