"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { NAV_ITEMS } from "@/lib/data";
import { toggleMobileNav } from "@/components/Sidebar";

/**
 * Header — page-level header (DESIGN.md §5.2).
 *
 * Left: contextual title + description (derived from current route).
 * Right: dark mode toggle (sun/moon pill button).
 *
 * On mobile (below lg), a hamburger button on the far left opens the
 * Sidebar overlay.
 *
 * The dark-mode toggle adds/removes the `dark` class on <html>. Initial
 * state is set by an inline script in layout.tsx to avoid flash.
 */

const ROUTE_META: Record<string, { title: string; desc: string }> = {
  "/": {
    title: "Dashboard",
    desc: "Project summary — modules, decisions, build health, and the current phase at a glance.",
  },
  "/architecture": {
    title: "Architecture",
    desc: "Module dependency graph, UI ↔ backend layer separation, and architecture decision records.",
  },
  "/decisions": {
    title: "Decisions",
    desc: "Architecture decisions that need your input. Each option shows pros (teal) and cons (rose).",
  },
  "/modules": {
    title: "Modules",
    desc: "Proposed module hierarchy — independent modules, one responsibility each, contracts between layers.",
  },
  "/progress": {
    title: "Progress",
    desc: "Phase-by-phase status — what's done, what's next, and current blockers.",
  },
  "/design": {
    title: "Design Language",
    desc: "The ANI-KUTA app's design language — lime accent, dark warm surfaces, accent presets, key components. (Distinct from the dashboard's MEMORY OS design.)",
  },
  "/analytics": {
    title: "Analytics",
    desc: "Module size distribution, build times, docs coverage over time, and build health table.",
  },
  "/planning": {
    title: "Planning",
    desc: "Gantt chart timeline, Kanban task board, and per-phase checklists.",
  },
};

export function Header() {
  const pathname = usePathname();
  const [isDark, setIsDark] = useState(false);

  useEffect(() => {
    setIsDark(document.documentElement.classList.contains("dark"));
  }, []);

  const toggle = () => {
    const next = !isDark;
    setIsDark(next);
    document.documentElement.classList.toggle("dark", next);
    try {
      localStorage.setItem("theme", next ? "dark" : "light");
    } catch {
      /* localStorage unavailable */
    }
  };

  const normalize = (p: string) => {
    if (!p) return "/";
    let s = p.replace(/\/+$/, "") || "/";
    s = s.replace(/^\/ANI-KUTA/i, "") || "/";
    return s;
  };
  const current = normalize(pathname);
  const meta =
    ROUTE_META[current] ??
    (current.startsWith("/modules")
      ? ROUTE_META["/modules"]
      : current.startsWith("/design")
        ? ROUTE_META["/design"]
        : ROUTE_META["/"]);

  return (
    <header className="sticky top-0 z-30 bg-canvas/80 backdrop-blur-xl border-b border-border/60">
      <div className="px-4 sm:px-6 lg:px-10 py-3 lg:py-4 max-w-[1280px] mx-auto">
        <div className="flex items-center gap-3">
          {/* Mobile hamburger */}
          <button
            type="button"
            onClick={toggleMobileNav}
            aria-label="Open navigation menu"
            className="lg:hidden h-9 w-9 rounded-[12px] border border-border bg-surface flex items-center justify-center text-text-primary shrink-0"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="w-5 h-5"
              aria-hidden="true"
            >
              <path d="M3 6h18M3 12h18M3 18h18" />
            </svg>
          </button>

          {/* Title + description */}
          <div className="min-w-0 flex-1">
            <h1 className="text-[22px] sm:text-[28px] lg:text-[32px] font-bold tracking-extra-tight text-text-primary leading-[0.95]">
              {meta.title}
            </h1>
            <p className="hidden sm:block text-[12px] sm:text-[13px] lg:text-[14px] text-text-secondary leading-[1.5] max-w-[560px] mt-1">
              {meta.desc}
            </p>
          </div>

          {/* Dark mode toggle */}
          <button
            type="button"
            onClick={toggle}
            aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
            title={isDark ? "Light mode" : "Dark mode"}
            className="h-9 px-3 rounded-[12px] border flex items-center gap-2 transition-all duration-200 hover:translate-y-[-1px] bg-surface border-border text-text-primary shrink-0"
          >
            {/* Sun icon (shown in dark mode) */}
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className={`w-[16px] h-[16px] ${isDark ? "block" : "hidden"}`}
              aria-hidden="true"
            >
              <circle cx="12" cy="12" r="4" />
              <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
            </svg>
            {/* Moon icon (shown in light mode) */}
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className={`w-[16px] h-[16px] ${isDark ? "hidden" : "block"}`}
              aria-hidden="true"
            >
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
            </svg>
            <span className="hidden sm:inline text-[12.5px] font-medium">
              {isDark ? "Light" : "Dark"}
            </span>
          </button>
        </div>
      </div>
    </header>
  );
}
