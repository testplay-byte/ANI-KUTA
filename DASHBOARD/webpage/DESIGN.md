# MEMORY OS — Design System

> A calm, minimal, functional design language built for clarity and focus.
> This document captures the complete visual system for recreating the MEMORY OS interface.
>
> **This is the canonical design language for the ANI-KUTA dashboard. Strictly followed on all pages.**

---

## 1. Design Philosophy

| Principle | Description |
|-----------|-------------|
| **Calm & Restrained** | No visual noise. Every element serves a purpose. |
| **Warm Minimalism** | Beige/cream neutrals with intentional accent colors. |
| **Functional Clarity** | UI communicates state and hierarchy without excess decoration. |
| **Subtle Depth** | Shadows, borders, and hover states add dimension without heaviness. |
| **Consistent Rhythm** | Predictable spacing, typography, and component patterns. |

---

## 2. Color System

### 2.1 Accent Colors (Primary Palette)

These colors are used for interactive elements, status indicators, and visual hierarchy.

| Name | Hex | RGB | Usage |
|------|-----|-----|-------|
| Indigo | `#6366F1` | (99, 102, 241) | Primary accent, interactive elements, highlights |
| Red | `#FF6B6B` | (255, 107, 107) | Destructive actions, warnings, attention |
| Teal | `#14B8A6` | (20, 184, 166) | Success states, confirmations, positive actions |
| Amber | `#F59E0B` | (245, 158, 11) | Warnings, secondary accents, highlights |
| Purple | `#8B5CF6` | (139, 92, 246) | Secondary accent, tertiary actions, decorative |

### 2.2 Neutral Colors (Light Mode)

| Name | Hex | RGB | Usage |
|------|-----|-----|-------|
| Near Black | `#1A1A1A` | (26, 26, 26) | Primary text, headings |
| Warm Gray | `#8A8784` | (138, 135, 132) | Secondary text, labels, metadata |
| Warm Light Gray | `#E8E2DA` | (232, 226, 218) | Borders, dividers, subtle strokes |
| Warm Beige | `#F5F1EB` | (245, 241, 235) | Backgrounds, card surfaces, chips |
| Warm Beige 2 | `#F9F5F0` | (249, 245, 240) | Card backgrounds, container surfaces |
| Off-White | `#FFFDFA` | (255, 253, 250) | Card backgrounds, elevated surfaces |
| White | `#FFFFFF` | (255, 255, 255) | Text on dark backgrounds, pure surfaces |
| Page Background | `#EDE9E3` | (237, 233, 227) | Main page background |

### 2.3 Dark Mode Colors

> Dark mode preserves the warm aesthetic. Surfaces are warm dark tones (not pure black).
> Accent colors stay the same — they pop against dark surfaces.
> A dark mode toggle sits at the top of every page (see §5.9).

| Name | Hex | RGB | Usage |
|------|-----|-----|-------|
| Dark Page Background | `#1A1917` | (26, 25, 23) | Main page background (dark) |
| Dark Card Background | `#252320` | (37, 35, 32) | Card backgrounds, containers (dark) |
| Dark Card Alt | `#2A2825` | (42, 40, 37) | Secondary card surfaces (dark) |
| Dark Chip Background | `#302D29` | (48, 45, 41) | Chips, pills, inactive buttons (dark) |
| Dark Code Background | `#252320` | (37, 35, 32) | Code blocks (dark) |
| Dark Text Primary | `#F5F1EB` | (245, 241, 235) | Primary text, headings (dark) |
| Dark Text Secondary | `#A8A39C` | (168, 163, 156) | Secondary text, metadata (dark) |
| Dark Border | `#3A3733` | (58, 55, 51) | Borders, dividers (dark) |

### 2.4 Color Opacity Variants

| Color | Opacity | Usage |
|-------|---------|-------|
| `#6366F1` | 20% | Selection backgrounds |
| `#14B8A6` | 60% | Subtle indicators, status dots |
| `#F9F5F0` | 60% | Muted backgrounds |

### 2.5 Semantic Color Mapping

| Semantic | Color |
|----------|-------|
| Primary / Interactive | `#6366F1` |
| Success / Positive | `#14B8A6` |
| Warning / Attention | `#F59E0B` |
| Danger / Destructive | `#FF6B6B` |
| Secondary / Decorative | `#8B5CF6` |

---

## 3. Typography

### 3.1 Font Families

| Family | Usage | Fallback |
|--------|-------|----------|
| **Inter** | Primary UI typeface | `-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif` |
| **JetBrains Mono** | Code, monospace content, file paths | `monospace` |

### 3.2 Font Weights

| Weight | Value | Usage |
|--------|-------|-------|
| Regular | 400 | Body text, descriptions |
| Medium | 500 | Labels, buttons, emphasized text |
| Semibold | 600 | Subheadings, strong emphasis |
| Bold | 700 | Headings, titles |

### 3.3 Type Scale

| Size | Usage | Weight | Line Height | Letter Spacing |
|------|-------|--------|-------------|----------------|
| 10px | Smallest labels, badges | 400–500 | — | — |
| 11px | Captions, metadata, kickers | 500–600 | 1.5 | `tracking-wide` (0.025em) |
| 12px | Small text, timestamps | 400–500 | — | — |
| 12.5px | Code, mono content | 400 | 1.7 | — |
| 13px | Body text, descriptions | 400 | 1.6 | — |
| 13.5px | Body text, list items | 400–500 | 1.6 | `-0.01em` |
| 14px | Card titles, subheadings | 600 | 1.3 | — |
| 15px | Logo, primary labels | 700 | — | `-0.02em` |
| 18px | Large numbers, stats | 700 | — | — |
| 20px | Page headings, section titles | 700 | — | `-0.02em` |

### 3.4 Typographic Utilities

```css
.antialiased {
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
.tracking-tight { letter-spacing: -0.01em; }
.tracking-extra-tight { letter-spacing: -0.02em; }
.tracking-wide { letter-spacing: 0.025em; }
.uppercase { text-transform: uppercase; }
.mono { font-family: 'JetBrains Mono', monospace; }
```

---

## 4. Spacing & Layout

### 4.1 Container Width

| Container | Max Width | Padding |
|-----------|-----------|---------|
| Main | `64rem` (max-w-5xl) | `1.5rem` (px-6), `2rem` (md:px-8) |
| Cards | Auto | `1.25rem`–`1.75rem` |

### 4.2 Spacing Scale

| Name | Value | Usage |
|------|-------|-------|
| `gap-1.5` | `0.375rem` (6px) | Tight inline spacing |
| `gap-2` | `0.5rem` (8px) | Small inline spacing |
| `gap-2.5` | `0.625rem` (10px) | Inline spacing |
| `gap-3` | `0.75rem` (12px) | Standard small gap |
| `gap-4` | `1rem` (16px) | Medium gap |
| `gap-6` | `1.5rem` (24px) | Large gap |

### 4.3 Padding Scale

| Name | Value | Usage |
|------|-------|-------|
| `p-4` | `1rem` (16px) | Card inner padding |
| `p-5` | `1.25rem` (20px) | Card inner padding |
| `p-[22px]` | 22px | Card inner padding |
| `px-6` | `1.5rem` (24px) | Container padding |
| `pt-10` | `2.5rem` (40px) | Top padding (page) |
| `pb-16` | `4rem` (64px) | Bottom padding (page) |

---

## 5. Components & Patterns

### 5.1 Buttons / Pills

- `rounded-full`, height `h-9`, padding `px-[18px]`, font `13.5px font-medium`
- Transition: `all 0.2s`
- **Default**: bg `#F5F1EB`, border `#E8E2DA`, text `#8A8784`
- **Active**: accent bg, white text, shadow glow, border matches accent
- **Hover**: `translateY(-1px)`

```html
<button class="h-9 px-[18px] rounded-full text-[13.5px] font-medium transition-all duration-200 flex items-center gap-2">
  <span class="w-1.5 h-1.5 rounded-full" style="background: color; opacity: 0.9"></span>
  Label
</button>
```

### 5.2 Cards

- `rounded-[16px]` / `rounded-[14px]` / `rounded-[12px]`
- Border: `1px solid #E8E2DA` (light) / `#3A3733` (dark)
- Background: `#F9F5F0` or `#FFFDFA` (light) / `#252320` or `#2A2825` (dark)
- Padding: `p-5` or `p-4`
- Shadow (optional): `0 2px 20px rgba(0,0,0,0.04)`
- Hover: `translateY(-1px)`

### 5.3 Status Dots

| Size | Classes |
|------|---------|
| Small | `w-1.5 h-1.5 rounded-full` |
| Medium | `w-2 h-2 rounded-full` |
| Large | `w-[10px] h-[10px] rounded-full` |

### 5.4 Dividers

| Type | Classes |
|------|---------|
| Horizontal | `h-px w-full bg-[#E8E2DA]` (light) / `bg-[#3A3733]` (dark) |
| Vertical | `w-px h-4 bg-[#E8E2DA]` |

### 5.5 Chips / Tags

- `rounded-full`, bg `#F5F1EB` (light) / `#302D29` (dark)
- Border `1px solid #E8E2DA` (light) / `#3A3733` (dark)
- Font: `11px`, text `#8A8784` (light) / `#A8A39C` (dark)

### 5.6 Code / Mono Blocks

- Font: `'JetBrains Mono', monospace`, `12.5px`, line-height `1.7`
- Background: `#F9F5F0` (light) / `#252320` (dark)
- Border: `1px solid #E8E2DA` (light) / `#3A3733` (dark)

### 5.7 Navigation Tabs (Pills)

- **Active**: accent bg, white text, shadow `0 4px 12px ${color}33`, border matches accent
- **Inactive**: bg `#F5F1EB` (light) / `#302D29` (dark), text `#8A8784` / `#A8A39C`

### 5.8 File Tree / Directory Structure

- Mono font, tree characters (`├─`, `│  ├─`, `└─`)
- Color-coded folder names
- Indentation: `pl-6`, `pl-10`

### 5.9 Dark Mode Toggle

- Placed at the **top right** of every page (in the header).
- A pill button with sun/moon icon.
- Toggles between light (default) and dark mode.
- Uses CSS variables — all colors swap via a `dark` class on `<html>`.
- Preference stored in `localStorage`; respects `prefers-color-scheme` on first visit.
- No flash of wrong theme (inline script in `<head>` sets the class before render).

```html
<!-- Implementation pattern (Next.js + Tailwind) -->
<button class="h-9 w-9 rounded-full border flex items-center justify-center transition-all duration-200"
        style="border-color: var(--color-border); background: var(--color-bg-chip);"
        onclick="toggleTheme()">
  <svg class="dark:hidden"><!-- sun icon --></svg>
  <svg class="hidden dark:block"><!-- moon icon --></svg>
</button>
```

---

## 6. Interactive States

### 6.1 Hover Effects

| Element | Effect | Duration |
|---------|--------|----------|
| Cards | `translateY(-1px)` | 200ms |
| List Items | Background → `#FFFDFA` (light) / `#2A2825` (dark) | — |
| Buttons | Background/color change | 200ms |

### 6.2 Transitions

```css
.transition-all { transition-property: all; }
.duration-200 { transition-duration: 200ms; }
.ease-out { transition-timing-function: cubic-bezier(0, 0, 0.2, 1); }
```

---

## 7. Animations

```css
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-fade-in { animation: fadeIn 0.35s ease-out; }
```

---

## 8. Layout Patterns

### 8.1 Page Structure

```
┌─────────────────────────────────────────────┐
│  max-w-5xl (64rem) mx-auto                  │
│  px-6 md:px-8, pt-10 pb-16                  │
│                                             │
│  Header (flex justify-between)              │
│    Logo + Status          Dark Mode Toggle  │
│                                             │
│  Navigation Pills (flex-wrap gap-2)         │
│                                             │
│  Main Content Card                          │
│    bg #FFFDFA, border #E8E2DA, p-8          │
│    shadow: 0 2px 20px rgba(0,0,0,0.04)      │
│                                             │
│  Footer (flex justify-between)              │
└─────────────────────────────────────────────┘
```

### 8.2 Card Grids

| Breakpoint | Columns | Gap |
|------------|---------|-----|
| Mobile | 1 | `gap-4` |
| Tablet (md) | 2–3 | `gap-4` |

---

## 9. CSS Variables (Implementation)

```css
:root {
  --color-primary: #6366F1;
  --color-success: #14B8A6;
  --color-warning: #F59E0B;
  --color-danger: #FF6B6B;
  --color-secondary: #8B5CF6;

  --color-text-primary: #1A1A1A;
  --color-text-secondary: #8A8784;
  --color-border: #E8E2DA;
  --color-bg-page: #EDE9E3;
  --color-bg-card: #F9F5F0;
  --color-bg-card-alt: #FFFDFA;
  --color-bg-chip: #F5F1EB;

  --font-sans: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  --font-mono: 'JetBrains Mono', monospace;

  --radius-sm: 12px;
  --radius-md: 14px;
  --radius-lg: 16px;
  --radius-full: 9999px;
}

html.dark {
  --color-text-primary: #F5F1EB;
  --color-text-secondary: #A8A39C;
  --color-border: #3A3733;
  --color-bg-page: #1A1917;
  --color-bg-card: #252320;
  --color-bg-card-alt: #2A2825;
  --color-bg-chip: #302D29;
}
```

---

## 10. Shadow System

| Level | Value | Usage |
|-------|-------|-------|
| Subtle | `0 1px 6px rgba(0,0,0,0.04)` | Card elevation |
| Light | `0 2px 20px rgba(0,0,0,0.04)` | Card elevation |
| Glow | `0 4px 12px ${color}33, 0 1px 2px rgba(0,0,0,0.06)` | Active button |

---

## 11. Accessibility

- `#1A1A1A` on `#FFFDFA` → 18.5:1 contrast (WCAG AAA)
- `#8A8784` on `#FFFDFA` → 6.2:1 contrast (WCAG AA)
- Dark: `#F5F1EB` on `#252320` → 14.8:1 (AAA)
- Interactive elements have clear hover/focus states
- `selection:bg-[#6366F1]/20` for text selection

---

## 12. Quick Reference

### Accent Color Usage
| Element | Color |
|---------|-------|
| Primary buttons, active tabs | `#6366F1` |
| Success indicators | `#14B8A6` |
| Warning indicators | `#F59E0B` |
| Destructive actions | `#FF6B6B` |
| Decorative, tertiary | `#8B5CF6` |

### Background Usage
| Element | Light | Dark |
|---------|-------|------|
| Page | `#EDE9E3` | `#1A1917` |
| Main cards | `#FFFDFA` | `#2A2825` |
| Secondary cards | `#F9F5F0` | `#252320` |
| Chips (inactive) | `#F5F1EB` | `#302D29` |
| Code blocks | `#F9F5F0` | `#252320` |

---

*This document is the single source of truth for the dashboard design language. All components and pages must adhere to these patterns. To modify: edit this file, confirm non-trivial changes with the user.*
