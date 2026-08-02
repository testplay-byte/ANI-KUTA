# MEMORY OS — Design System (v2)

> **Canonical design language for the ANI-KUTA dashboard. Strictly followed on all pages.**
> A living document — improved regularly based on user preferences.
>
> **v2 changes**: Sidebar layout, charts/graphs, checklists, rounded sidebar, shrinkable sidebar,
> combined with v1's dark mode system.

---

## 1. Visual Language Overview

### 1.1 Core Philosophy
- **"Warm Canvas, Sharp Data"** — A warm, editorial aesthetic (beige/cream backgrounds) with crisp, data-rich components that feel tangible and architectural.
- **Memory as Infrastructure** — Design language inspired by operating systems, file trees, and modular architecture. It should feel structured, linkable, and mapped.
- **Minimal Editorial** — Clean typography, generous spacing, subtle shadows, and a focus on content hierarchy. Inspired by Linear, Vercel, and Notion design languages.

### 1.2 Design Principles
| Principle | Description |
|-----------|-------------|
| **Calm & Restrained** | No visual noise. Every element serves a purpose. |
| **Warm Minimalism** | Beige/cream neutrals with intentional accent colors. |
| **Functional Clarity** | UI communicates state and hierarchy without excess decoration. |
| **Subtle Depth** | Shadows, borders, and hover states add dimension without heaviness. |
| **Consistent Rhythm** | Predictable spacing, typography, and component patterns. |
| **Modular** | Components are self-contained, reusable, and consistent. |
| **Responsive** | Adapts gracefully from mobile to desktop. |
| **Interactive** | Clear hover/active states, smooth animations. |

---

## 2. Color System

### 2.1 Accent Colors (Primary Palette)

| Name | Hex | Usage |
|------|-----|-------|
| Indigo | `#6366F1` | Primary actions, highlights, active states |
| Teal | `#14B8A6` | Success/Sync, done states, health metrics, positive indicators |
| Amber | `#F59E0B` | Warning/Pending, in-progress, warnings |
| Rose | `#FF6B6B` | Error/Attention, critical paths, errors, high-priority |
| Violet | `#8B5CF6` | Secondary accent, tertiary elements, alternative branding |

### 2.2 Neutral Colors (Light Mode)

| Name | Hex | Usage |
|------|-----|-------|
| Warm Canvas | `#F2EEE8` | Page background, creates warmth |
| Warm White | `#FFFDFA` | Cards, sidebars, elevated surfaces |
| Warm Beige | `#F5F1EB` | Chips, inactive buttons, secondary surfaces |
| Warm Beige 2 | `#F9F5F0` | Code blocks, secondary cards |
| Warm Border | `#E8E2DA` | Dividers, borders, subtle separators |
| Warm Gray | `#8A8784` | Secondary text, labels, metadata |
| Almost Black | `#1A1A1A` | Primary text, headings |
| White | `#FFFFFF` | Text on dark backgrounds, pure surfaces |

### 2.3 Dark Mode Colors

> Dark mode uses a GREY theme (not brown/warm). No pure black — use grey tones.
> Accent colors stay the same — they pop against grey surfaces.
> A dark mode toggle sits at the top of every page (see §5.10).

| Name | Hex | Usage |
|------|-----|-------|
| Dark Canvas | `#1E1E1E` | Page background (dark grey, NOT pure black) |
| Dark Surface | `#252525` | Cards, sidebars (dark grey) |
| Dark Surface Alt | `#2D2D2D` | Secondary card surfaces (dark grey) |
| Dark Chip | `#333333` | Chips, inactive buttons (dark grey) |
| Dark Border | `#404040` | Borders, dividers (dark grey) |
| Dark Text Primary | `#E8E8E8` | Primary text, headings (light grey) |
| Dark Text Secondary | `#A0A0A0` | Secondary text, metadata (medium grey) |

### 2.4 Color Opacity Variants

| Color | Opacity | Usage |
|-------|---------|-------|
| `#6366F1` | 20% | Selection backgrounds, focus glow |
| `#14B8A6` | 60% | Subtle indicators, status dots |
| `#F9F5F0` | 60% | Muted backgrounds |

### 2.5 Semantic Color Mapping

| Semantic | Color |
|----------|-------|
| Primary / Interactive | `#6366F1` |
| Success / Positive | `#14B8A6` |
| Warning / Pending | `#F59E0B` |
| Danger / Critical | `#FF6B6B` |
| Secondary / Decorative | `#8B5CF6` |

---

## 3. Typography

### 3.1 Font Families

| Family | Usage | Fallback |
|--------|-------|----------|
| **Inter** | Primary UI typeface | `ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif` |
| **JetBrains Mono** | Code, monospace content, file paths, module names | `monospace` |

### 3.2 Text Scale

| Usage | Size | Weight | Letter Spacing |
|-------|------|--------|----------------|
| Page Title | 26px → 32px (sm) | 700 | -0.02em |
| Card Value | 28px | 700 | -0.02em |
| Section Title | 14px | 600 | normal |
| Body Text | 13px → 14px (sm) | 400 | normal |
| Label | 11px → 12px | 500–600 | normal |
| Metadata | 10px → 11px | 400–500 | 0.1em (uppercase) |
| Small Badge | 9px → 10px | 500 | normal |

### 3.3 Typography Rules
- Use `tracking-tight` (-0.025em) for headings.
- Use `tracking-widest` (0.1em) for small uppercase labels.
- Use `.mono` class for file paths, module names, and code.
- Maintain high contrast between text sizes for clear hierarchy.

---

## 4. Spacing & Layout Grid

### 4.1 Base Units
- **Spacing**: 4px increments (0.25rem)
- **Padding**: 16px (1rem) to 32px (2rem) for content containers
- **Gaps**: 4px, 8px, 12px, 16px, 24px

### 4.2 Layout Structure

```
┌─────────┬──────────────────────────────────────┐
│ Sidebar │  Main Content (flex-1)               │
│ 240px   │  max-w-[1280px] mx-auto              │
│ sticky  │  px-4 sm:px-6 lg:px-10 py-6 lg:py-10 │
│ shrink- │                                      │
│ able    │  [Header] [Content] [Footer]         │
└─────────┴──────────────────────────────────────┘
```

- **Sidebar**: 240px on desktop, **shrinkable** (collapses to icon-only ~64px), sticky, scrollable, **rounded on all corners**.
- **Main**: `max-w-[1280px]`, centered with `mx-auto`.
- **Content Padding**: 16px (mobile) → 24px (tablet) → 40px (desktop).

### 4.3 Border Radius System

| Corner | Value | Usage |
|--------|-------|-------|
| Rounded Small | 6px | Small badges, tiny buttons |
| Rounded Medium | 8px | Inputs, checkboxes, small boxes |
| Rounded Standard | 10px | File tree items, small cards |
| Rounded Comfortable | 12px | Buttons, navigation pills, small panels |
| Rounded Medium-Large | 14px | Card headers, module boxes |
| Rounded Large | 16px | Cards, panels, standard containers |
| Rounded XL | 20px | Metric cards, large panels |
| Rounded 2XL | 24px | Main cards, section containers, **sidebar** |
| Rounded Full | 9999px | Avatars, badges, pills |

### 4.4 Shadow System

| Level | Value | Usage |
|-------|-------|-------|
| Sm | `0 1px 2px 0 rgba(0,0,0,0.05)` | Subtle elevation |
| Card | `0 8px 40px rgba(0,0,0,0.04)` | Cards, panels |
| Elevated | `0 4px 16px rgba(0,0,0,0.12)` | Dropdowns, modals |
| Hover | `0 12px 40px rgba(0,0,0,0.08)` | Card hover |
| Focus | `0 0 0 4px rgba(99,102,241,0.15)` | Focus (indigo glow) |
| Accent Glow | `0 4px 12px ${color}33` | Active buttons |

---

## 5. Components & Patterns

### 5.1 Sidebar (Navigation)

**Purpose**: Primary navigation hub, system status, and user context.

#### Structure:
```
[Logo + Brand]
[Primary Navigation]
[Build Health Widget]
[User Profile]
```

#### Details:
- **Background**: `bg-[#FFFDFA]/80 backdrop-blur-xl` (translucent white with blur). Dark: `bg-[#252320]/80`.
- **Border**: Right border `border-r border-[#E8E2DA]` on large screens.
- **Width**: 240px expanded, ~64px collapsed (icon-only). **Shrinkable** via a toggle button.
- **Rounded**: All corners rounded (`rounded-2xl` on desktop, giving a floating panel look).
- **Sticky**: `lg:sticky lg:top-0 lg:h-screen`.
- **Margin**: Small margin from viewport edges on desktop (floating sidebar, not flush to edge).

#### Brand Area:
- **Logo**: Square badge with "A" (9x9, rounded-12, bg-[#1A1A1A], white text).
- **Title**: "ANI-KUTA" (semibold, 14px).
- **Subtitle**: "Project Dashboard" (10px, uppercase, tracked, #8A8784).
- **Version**: Small badge "v0.1" (10px, rounded-full, bg-[#E8E2DA]).
- When **collapsed**: show only the logo badge.

#### Navigation Items:
```
Pills with icon + label
- Dashboard: "◫"
- Architecture: "◈"
- Decisions: "◉"  ← NEW
- Modules: "⬙"
- Progress: "◍"
- Analytics: "◬"
- Planning: "📋"
```
- **Active State**: `bg-[#1A1A1A] text-white shadow-[0_4px_16px_rgba(0,0,0,0.12)]`
- **Hover State**: `hover:text-[#1A1A1A] hover:bg-[#F2EEE8]`
- **Spacing**: `gap-1.5`, padding `px-3.5 py-2.5`
- **Rounded**: `rounded-[12px]`
- When **collapsed**: show only the icon, centered.

#### Shrink Toggle:
- A small button at the sidebar's bottom or top-right corner.
- Icon: `«` (expand) / `»` (collapse) or chevron icons.
- Toggles sidebar width between 240px and 64px.
- Preference stored in `localStorage`.

#### Build Health Widget:
- **Container**: `bg-[#F2EEE8] rounded-[16px] border border-[#E8E2DA]`
- **Label**: "Build Health" (11px, uppercase, tracking-widest, #8A8784)
- **Value**: "100%" (22px, bold, tracking-tight)
- **Status**: "● live" (11px, #14B8A6)
- **Progress Bar**: `h-1.5 rounded-full bg-white overflow-hidden`, fill `bg-[#14B8A6]`.
- **Footer**: "36 modules · 0 failures" (11px, #8A8784)
- When **collapsed**: hide this widget.

#### User Profile:
- **Container**: `flex items-center gap-2 text-[11px] text-[#8A8784]`
- **Avatar**: `w-6 h-6 rounded-full bg-[#E8E2DA]`
- **Name**: "ANI-KUTA Agent" (font-medium, #1A1A1A)
- **Status**: `w-2 h-2 rounded-full bg-[#14B8A6] animate-pulse`
- When **collapsed**: show only the avatar.

### 5.2 Main Content Area

#### Structure:
```
[Header: Title + Description + Actions (incl. dark mode toggle)]
[Content: Animated fade-in container]
[Footer: Meta information]
```

#### Header:
- **Title**: `text-[26px] sm:text-[32px] font-[700] tracking-[-0.02em] leading-[0.95]`
- **Description**: `text-[13px] sm:text-[14px] text-[#8A8784] max-w-[560px] leading-[1.5]`
- **Dark Mode Toggle**: Top-right (sun/moon icon, pill button).
- **Action Buttons**: Pills (bg-[#1A1A1A] text-white rounded-[12px]).

#### Content Container:
- **Animation**: `animate-[fadeIn_0.3s_ease]`
- **Padding**: `px-4 sm:px-6 lg:px-10 py-6 lg:py-10`
- **Max Width**: `max-w-[1280px] mx-auto`

#### Footer:
```
ANI-KUTA · Project Dashboard · warm canvas #F2EEE8 · 36 modules · 100% health
```
- **Border**: `border-t border-[#E8E2DA]`, **Spacing**: `mt-12 pt-6`, **Text**: `text-[11px] text-[#8A8784]`.

### 5.3 Cards

- **Background**: `bg-[#FFFDFA]` (light) / `bg-[#2A2825]` (dark)
- **Border**: `border border-[#E8E2DA]` (light) / `border-[#3A3733]` (dark)
- **Radius**: `rounded-[16px]` to `rounded-[24px]` depending on size
- **Shadow**: `shadow-[0_8px_40px_rgba(0,0,0,0.04)]`
- **Hover**: `hover:shadow-[0_12px_40px_rgba(0,0,0,0.08)] hover:-translate-y-[1px]`
- **Transition**: `transition-all duration-200`

### 5.4 Buttons / Pills

- `rounded-full` or `rounded-[12px]`, height `h-9`, padding `px-[18px]`, font `13.5px font-medium`.
- **Primary**: bg `#1A1A1A`, text white. Hover: `hover:bg-[#2A2A2A]`.
- **Secondary**: bg `#F5F1EB`, border `#E8E2DA`, text `#8A8784`. Hover: `hover:bg-[#F2EEE8]`.
- **Active (accent)**: accent bg, white text, shadow glow.
- **Hover**: `translateY(-1px)`.

### 5.5 Status Dots

| Size | Classes |
|------|---------|
| Small | `w-1.5 h-1.5 rounded-full` |
| Medium | `w-2 h-2 rounded-full` |
| Large | `w-[10px] h-[10px] rounded-full` |
| Live | `w-2 h-2 rounded-full bg-[#14B8A6] animate-pulse` |

### 5.6 Chips / Tags / Badges

- `rounded-full`, bg `#F5F1EB` (light) / `#302D29` (dark), border `1px solid #E8E2DA` / `#3A3733`.
- Font: `11px`, text `#8A8784` / `#A8A39C`.
- Small badges: `9px-10px`, `rounded-[6px]`.

### 5.7 Dividers

| Type | Classes |
|------|---------|
| Horizontal | `h-px w-full bg-[#E8E2DA]` / `bg-[#3A3733]` |
| Vertical | `w-px h-4 bg-[#E8E2DA]` |

### 5.8 Code / Mono Blocks

- Font: `'JetBrains Mono', monospace`, `12.5px`, line-height `1.7`.
- Background: `#F9F5F0` (light) / `#252320` (dark).
- Border: `1px solid #E8E2DA` / `#3A3733`.
- `whitespace-pre-wrap`, `break-words`.

### 5.9 File Tree / Directory Structure

- Mono font, tree characters (`├─`, `│  ├─`, `└─`).
- Color-coded folder names.
- Indentation: `18px` per level via `paddingLeft`.
- **Hover**: `hover:bg-[#F2EEE8]`.
- **Selected**: `bg-[#6366F1] text-white`.
- **Folder Toggle**: "−" / "+" in `w-4 h-4 bg-[#E8E2DA] rounded`.
- **File Indicator**: "·" dot.

### 5.10 Dark Mode Toggle

- Placed at the **top-right** of the main content header (next to action buttons).
- A pill button with sun/moon icon.
- Toggles between light (default) and dark mode via `dark` class on `<html>`.
- Preference stored in `localStorage`; respects `prefers-color-scheme` on first visit.
- No flash of wrong theme (inline script in `<head>` sets the class before render).

### 5.11 Phase Timeline

```
[Title: "Phase timeline" + "P0→P5 · total days"]
[Progress Bar]
[Phase Cards: P0-P5 with status (done/active/todo)]
```

- **Done**: `border-[#14B8A6] bg-[#14B8A6] text-white` (checkmark "✓")
- **Active**: `border-[#6366F1] text-[#6366F1] shadow-[0_0_0_4px_rgba(99,102,241,0.15)]`
- **Todo**: `border-[#E8E2DA] text-[#8A8784]`

### 5.12 Workflow Loop

6-step cycle: Analyze → Research → Comprehend → Confirm → Build → Verify.
- Cards with colored top bar, icon, label, description.
- Connected by arrows (SVG).

### 5.13 Metric Cards (with Sparklines)

```
[Card] → [Metric Icon] [Label] [Value (28px bold)] [Subtext] [Sparkline SVG]
```
- **Radius**: `rounded-[20px]`
- **Shadow**: `shadow-[0_8px_40px_rgba(0,0,0,0.04)]`
- **Hover**: `hover:shadow-[0_12px_40px_rgba(0,0,0,0.08)] hover:-translate-y-[1px]`
- **Sparkline**: 70x20 SVG polyline, stroke = metric color.

### 5.14 Charts

#### Donut Chart (module size distribution):
- SVG arcs with `stroke-dasharray`.
- Center label: total count + "modules".

#### Horizontal Bars (build times):
- Colored bars, labels on left, values on right.

#### Area/Line Chart (progress over time):
- SVG path, indigo fill (area), teal dashed stroke (line).

#### Dependency Graph (architecture):
- SVG nodes (circles/rects) + edges (lines).
- Hover highlights node + dependencies.
- Color-coded by module type.

### 5.15 Checklists

- **Container**: `bg-[#FFFDFA] border-[#E8E2DA] rounded-[16px]`.
- **Items**: Checkbox + label. Checked: strikethrough + `opacity-70`.
- **Progress Bar**: `h-1.5 rounded-full bg-[#F2EEE8]`, fill `bg-[#14B8A6]`.

### 5.16 Task Board (Kanban)

```
3 Columns: To Do | In Progress | Done
  └── Task Cards: title, priority dot, tag pill, assignee initials
```
- Priority dots: Rose (high), Amber (med), Border-gray (low).

### 5.17 Gantt Chart

- Grid: `120px label | 1fr timeline`.
- Bars colored by phase, positioned by start/days.

### 5.18 Decisions View (NEW)

> Shows architecture decisions with pros/cons for user review.
- **Decision Card**: ID (D-XXX), title, status badge, question, options with pros/cons.
- **Option Card**: option name, pros (teal), cons (rose), recommendation badge.
- **Status**: ✅ Confirmed / 🚧 Pending / ⏳ Needs Input.
- Filterable by status (pills at top).

---

## 6. Interaction Patterns

### 6.1 Navigation Transitions
- **Fade In**: `animate-[fadeIn_0.3s_ease]` on content change.
- **Active State**: Dark background pill `bg-[#1A1A1A] text-white`.

### 6.2 Hover States
- **Cards**: `hover:shadow-[0_12px_40px_rgba(0,0,0,0.08)] hover:-translate-y-[1px]`
- **Buttons**: bg change.
- **Tree Items**: `hover:bg-[#F2EEE8]`.
- **Table Rows**: `hover:bg-[#F2EEE8]/50`.

### 6.3 Transitions
```css
.transition-all { transition-property: all; }
.transition-colors { transition-property: color, background-color, border-color; }
.duration-200 { transition-duration: 200ms; }
.ease-out { transition-timing-function: cubic-bezier(0, 0, 0.2, 1); }
```

### 6.4 Animations
```css
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-fade-in { animation: fadeIn 0.3s ease-out; }

@keyframes pulse {
  50% { opacity: 0.5; }
}
.animate-pulse { animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite; }
```

---

## 7. Responsive Behavior

| Breakpoint | Width | Behavior |
|------------|-------|----------|
| Mobile | <640px | Single column, sidebar collapses to overlay/hamburger |
| Tablet (sm) | ≥640px | 2-3 column grids, sidebar hidden or collapsed |
| Desktop (lg) | ≥1024px | Sidebar visible (shrinkable), multi-column layouts |

### Mobile Adaptations:
- Sidebar → hamburger menu or bottom nav.
- Phase timeline → stacked list.
- Gantt chart → phase list.
- Metric cards: 2 cols → 1 col.
- Charts adapt to smaller viewports.

---

## 8. CSS Variables (Implementation)

```css
:root {
  /* Accents */
  --color-primary: #6366F1;
  --color-success: #14B8A6;
  --color-warning: #F59E0B;
  --color-danger: #FF6B6B;
  --color-secondary: #8B5CF6;

  /* Light mode neutrals */
  --color-canvas: #F2EEE8;
  --color-surface: #FFFDFA;
  --color-surface-alt: #F9F5F0;
  --color-chip: #F5F1EB;
  --color-text-primary: #1A1A1A;
  --color-text-secondary: #8A8784;
  --color-border: #E8E2DA;

  /* Fonts */
  --font-sans: 'Inter', ui-sans-serif, system-ui, -apple-system, sans-serif;
  --font-mono: 'JetBrains Mono', monospace;

  /* Radius */
  --radius-sm: 6px;
  --radius-md: 8px;
  --radius-std: 10px;
  --radius-comfortable: 12px;
  --radius-ml: 14px;
  --radius-lg: 16px;
  --radius-xl: 20px;
  --radius-2xl: 24px;
  --radius-full: 9999px;

  /* Shadows */
  --shadow-sm: 0 1px 2px 0 rgba(0,0,0,0.05);
  --shadow-card: 0 8px 40px rgba(0,0,0,0.04);
  --shadow-elevated: 0 4px 16px rgba(0,0,0,0.12);
  --shadow-hover: 0 12px 40px rgba(0,0,0,0.08);
  --shadow-focus: 0 0 0 4px rgba(99,102,241,0.15);
}

html.dark {
  --color-canvas: #1E1E1E;
  --color-surface: #252525;
  --color-surface-alt: #2D2D2D;
  --color-chip: #333333;
  --color-text-primary: #E8E8E8;
  --color-text-secondary: #A0A0A0;
  --color-border: #404040;
}
```

---

## 9. Accessibility

- `#1A1A1A` on `#FFFDFA` → 18.5:1 contrast (WCAG AAA).
- `#8A8784` on `#FFFDFA` → 6.2:1 contrast (WCAG AA).
- Dark: `#F5F1EB` on `#252320` → 14.8:1 (AAA).
- Interactive elements have clear hover/focus states.
- `selection:bg-[#6366F1]/20` for text selection.
- Focus glow: `0 0 0 4px rgba(99,102,241,0.15)`.

---

## 10. Summary of Principles

1. **Warm Canvas**: `#F2EEE8` background creates warmth.
2. **Sharp Data**: Clean typography, consistent spacing, clear hierarchy.
3. **Subtle Elevation**: Minimal shadows, organic rounded corners.
4. **Color Coded**: Accent colors signal status, type, and priority.
5. **Modular**: Self-contained, reusable components.
6. **Responsive**: Mobile to desktop.
7. **Interactive**: Clear hover/active states, smooth animations.
8. **Editorial**: Content-first, generous whitespace, high readability.
9. **Living Document**: This design language is constantly updated based on user preferences.

---

*This document is the single source of truth for the dashboard design language. All components and pages must adhere to these specifications. To modify: edit this file, confirm non-trivial changes with the user.*
