"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { NAV_ITEMS, QUICK_STATS } from "@/lib/data";

/**
 * Sidebar — primary navigation (DESIGN.md §5.1).
 *
 * v3 features:
 *  - Floating: rounded-2xl on all corners, margin from viewport edges (desktop).
 *  - Shrinkable: toggles between 240px (expanded) and 64px (icon-only).
 *    Preference persists in localStorage (`sidebar-shrink`).
 *  - Sticky on desktop (lg:sticky lg:top-3 lg:h-[calc(100vh-1.5rem)]).
 *  - Translucent surface with backdrop blur.
 *  - On mobile: hidden by default; a floating hamburger button (rendered by
 *    this same component, lg:hidden, fixed top-left) opens the sidebar as a
 *    full-screen overlay. The button hides itself while the overlay is open.
 *  - Dark-mode toggle in the footer row (next to the shrink toggle). Icon-only
 *    when shrunk. Persists to localStorage('theme') — same key the inline
 *    theme-init script in layout.tsx reads on next paint.
 *
 * Sections: Brand → Nav pills → Build Health widget → User Profile →
 *           Footer (dark-mode toggle + shrink toggle).
 *
 * Note: the page-level `<Header>` was removed in v3 (DESIGN.md §5.2 deleted).
 * Each page now renders its own hero/title at the top of its content.
 */

const MOBILE_NAV_EVENT = "ani-kuta:toggle-mobile-nav";

/** Kept for backward-compat — external callers can still dispatch the event. */
export function toggleMobileNav() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent(MOBILE_NAV_EVENT));
  }
}

export function Sidebar() {
  const pathname = usePathname();
  const [shrink, setShrink] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [mounted, setMounted] = useState(false);
  const [isDark, setIsDark] = useState(false);

  // Read shrink preference on mount (set by inline script for no-flash).
  useEffect(() => {
    setMounted(true);
    const stored = document.documentElement.getAttribute("data-sidebar-shrink");
    setShrink(stored === "1");
    setIsDark(document.documentElement.classList.contains("dark"));
  }, []);

  // Listen for external hamburger toggle requests (mobile only).
  useEffect(() => {
    const handler = () => setMobileOpen((o) => !o);
    window.addEventListener(MOBILE_NAV_EVENT, handler);
    return () => window.removeEventListener(MOBILE_NAV_EVENT, handler);
  }, []);

  // Close mobile overlay on route change.
  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  const toggleShrink = () => {
    const next = !shrink;
    setShrink(next);
    try {
      localStorage.setItem("sidebar-shrink", next ? "1" : "0");
      document.documentElement.setAttribute(
        "data-sidebar-shrink",
        next ? "1" : "0",
      );
    } catch {
      /* localStorage unavailable */
    }
  };

  const toggleDark = () => {
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

  return (
    <>
      {/* Floating mobile hamburger button (lg:hidden only).
          Hides itself while the overlay is open. */}
      {!mobileOpen && (
        <button
          type="button"
          onClick={() => setMobileOpen(true)}
          aria-label="Open navigation menu"
          className="lg:hidden fixed top-3 left-3 z-40 h-10 w-10 rounded-[12px] border border-border bg-surface/95 backdrop-blur-xl flex items-center justify-center text-text-primary shadow-card-subtle"
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
      )}

      {/* Mobile overlay backdrop */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/30 backdrop-blur-sm lg:hidden"
          onClick={() => setMobileOpen(false)}
          aria-hidden="true"
        />
      )}

      <aside
        className={`
          z-50 flex flex-col
          bg-surface/80 backdrop-blur-xl border border-border
          rounded-2xl
          transition-[width,transform] duration-300 ease-out
          shrink-0
          ${shrink ? "lg:w-[68px]" : "lg:w-[240px]"}
          w-[260px]
          ${mobileOpen ? "translate-x-0" : "-translate-x-full"}
          lg:translate-x-0
          fixed lg:sticky top-0 left-0 bottom-0
          lg:top-3 lg:bottom-3 lg:h-[calc(100vh-1.5rem)]
        `}
        aria-label="Primary navigation"
      >
        {/* Brand area */}
        <div className="flex items-center gap-2.5 px-4 pt-4 pb-3 border-b border-border/60 shrink-0">
          <Link
            href="/"
            className="flex items-center gap-2.5 min-w-0 no-underline"
            aria-label="ANI-KUTA dashboard home"
          >
            <span className="w-9 h-9 rounded-[12px] bg-[#1a1a1a] dark:bg-[#E8E8E8] text-white dark:text-[#1a1a1a] flex items-center justify-center font-bold text-[16px] shrink-0">
              A
            </span>
            {!shrink && (
              <span className="min-w-0">
                <span className="block text-[14px] font-bold tracking-extra-tight text-text-primary leading-tight">
                  ANI-KUTA
                </span>
                <span className="block text-[9.5px] font-medium uppercase tracking-widest text-text-secondary">
                  Project Dashboard
                </span>
              </span>
            )}
          </Link>
          {!shrink && (
            <span className="ml-auto inline-flex items-center h-5 px-2 rounded-full text-[9.5px] font-medium bg-chip text-text-secondary border border-border shrink-0">
              v0.1
            </span>
          )}
        </div>

        {/* Navigation pills */}
        <nav
          className="flex-1 overflow-y-auto px-2.5 py-3 space-y-1"
          aria-label="Sections"
        >
          {NAV_ITEMS.map((item) => {
            const itemPath = normalize(item.href);
            const isActive =
              itemPath === "/"
                ? current === "/" || current === ""
                : current === itemPath || current.startsWith(itemPath + "/");

            return (
              <Link
                key={item.href}
                href={item.href}
                title={shrink ? item.label : undefined}
                aria-label={item.label}
                aria-current={isActive ? "page" : undefined}
                className={`
                  group flex items-center gap-2.5 rounded-[12px] px-3 py-2.5
                  text-[13px] font-medium transition-all duration-200
                  no-underline
                  ${shrink ? "justify-center px-2" : ""}
                  ${isActive
                    ? "bg-[#1a1a1a] dark:bg-[#E8E8E8] text-white dark:text-[#1a1a1a] shadow-[0_4px_16px_rgba(0,0,0,0.12)]"
                    : "text-text-secondary hover:text-text-primary hover:bg-canvas"
                  }
                `}
              >
                <NavIcon
                  name={item.icon}
                  className={`w-[18px] h-[18px] shrink-0 ${isActive ? "" : "text-text-secondary group-hover:text-text-primary"}`}
                />
                {!shrink && <span className="truncate">{item.label}</span>}
              </Link>
            );
          })}
        </nav>

        {/* Build Health widget */}
        {!shrink && (
          <div className="mx-2.5 mb-2.5 rounded-[16px] border border-border bg-canvas p-3.5 shrink-0">
            <div className="text-[10px] font-medium uppercase tracking-widest text-text-secondary mb-2">
              Build Health
            </div>
            <div className="flex items-baseline gap-2 mb-2">
              <span className="text-[22px] font-bold tracking-extra-tight text-text-primary leading-none">
                100%
              </span>
              <span className="inline-flex items-center gap-1 text-[11px] font-medium text-[var(--c-success)]">
                <span className="w-2 h-2 rounded-full bg-[var(--c-success)] animate-pulse" />
                live
              </span>
            </div>
            <div className="h-1.5 rounded-full bg-surface overflow-hidden">
              <div
                className="h-full rounded-full bg-[var(--c-success)]"
                style={{ width: "100%" }}
              />
            </div>
            <div className="text-[11px] text-text-secondary mt-2">
              {QUICK_STATS.modules} modules · 0 failures
            </div>
          </div>
        )}

        {/* User profile */}
        <div className="px-3 py-3 border-t border-border/60 shrink-0">
          <div
            className={`flex items-center gap-2 text-[11px] text-text-secondary ${shrink ? "justify-center" : ""}`}
          >
            <span className="relative shrink-0">
              <span className="w-6 h-6 rounded-full bg-chip border border-border flex items-center justify-center text-[10px] font-bold text-text-primary">
                AK
              </span>
              <span className="absolute -bottom-0.5 -right-0.5 w-2 h-2 rounded-full bg-[var(--c-success)] border border-surface" />
            </span>
            {!shrink && (
              <span className="min-w-0">
                <span className="block text-[12px] font-medium text-text-primary truncate">
                  ANI-KUTA Agent
                </span>
                <span className="block text-[10px] text-text-secondary">
                  active now
                </span>
              </span>
            )}
          </div>
        </div>

        {/* Footer row — dark-mode toggle + shrink toggle (desktop). */}
        <div
          className={`px-3 py-3 border-t border-border/60 shrink-0 ${shrink ? "flex flex-col items-center gap-2" : "flex items-center gap-2"}`}
        >
          <button
            type="button"
            onClick={toggleDark}
            aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
            title={isDark ? "Light mode" : "Dark mode"}
            className={`flex items-center gap-2 h-9 rounded-[10px] border border-border bg-surface text-text-primary hover:bg-canvas hover:translate-y-[-1px] transition-all duration-200 ${shrink ? "w-9 justify-center px-0" : "px-3 flex-1"}`}
          >
            {/* Sun icon (shown in dark mode → click for light) */}
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className={`w-4 h-4 ${isDark ? "block" : "hidden"}`}
              aria-hidden="true"
            >
              <circle cx="12" cy="12" r="4" />
              <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
            </svg>
            {/* Moon icon (shown in light mode → click for dark) */}
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className={`w-4 h-4 ${isDark ? "hidden" : "block"}`}
              aria-hidden="true"
            >
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
            </svg>
            {!shrink && (
              <span className="text-[12px] font-medium">
                {isDark ? "Light" : "Dark"}
              </span>
            )}
          </button>

          {/* Shrink toggle (desktop only) */}
          <button
            type="button"
            onClick={toggleShrink}
            aria-label={shrink ? "Expand sidebar" : "Collapse sidebar"}
            title={shrink ? "Expand sidebar" : "Collapse sidebar"}
            className="hidden lg:flex items-center justify-center w-9 h-9 rounded-[10px] border border-border bg-surface text-text-secondary hover:text-text-primary hover:bg-canvas transition-all duration-200 shrink-0"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className={`w-4 h-4 transition-transform duration-300 ${shrink ? "rotate-180" : ""}`}
              aria-hidden="true"
            >
              <path d="M15 18l-6-6 6-6" />
            </svg>
          </button>
        </div>
      </aside>
    </>
  );
}

/* ---------------------------------------------------------------------------
 * NavIcon — inline SVG icon set (DESIGN.md §5.1 symbols).
 * Kept minimal: stroke-based 18×18 icons, no external deps.
 * ------------------------------------------------------------------------- */
function NavIcon({ name, className = "" }: { name: string; className?: string }) {
  const icons: Record<string, React.ReactNode> = {
    dashboard: (
      <>
        <rect x="3" y="3" width="7" height="9" rx="1" />
        <rect x="14" y="3" width="7" height="5" rx="1" />
        <rect x="14" y="12" width="7" height="9" rx="1" />
        <rect x="3" y="16" width="7" height="5" rx="1" />
      </>
    ),
    architecture: (
      <>
        <path d="M12 2l9 5v10l-9 5-9-5V7l9-5z" />
        <path d="M12 22V12" />
        <path d="M3 7l9 5 9-5" />
      </>
    ),
    decisions: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v5l3 3" />
      </>
    ),
    modules: (
      <>
        <path d="M4 4h6v6H4z" />
        <path d="M14 4h6v6h-6z" />
        <path d="M4 14h6v6H4z" />
        <path d="M14 14h6v6h-6z" />
      </>
    ),
    progress: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 3a9 9 0 019 9" strokeWidth="3" fill="none" />
      </>
    ),
    analytics: (
      <>
        <path d="M3 3v18h18" />
        <path d="M7 14l4-4 4 4 5-6" />
      </>
    ),
    planning: (
      <>
        <rect x="3" y="4" width="18" height="16" rx="2" />
        <path d="M3 9h18" />
        <path d="M8 4v5" />
        <path d="M16 4v5" />
      </>
    ),
    design: (
      <>
        <circle cx="12" cy="12" r="9" />
        <circle cx="9" cy="10" r="1.2" fill="currentColor" stroke="none" />
        <circle cx="14" cy="9" r="1.2" fill="currentColor" stroke="none" />
        <circle cx="15" cy="13" r="1.2" fill="currentColor" stroke="none" />
        <circle cx="11" cy="15" r="1.2" fill="currentColor" stroke="none" />
      </>
    ),
    database: (
      <>
        <ellipse cx="12" cy="5" rx="8" ry="2.5" />
        <path d="M4 5v6c0 1.4 3.6 2.5 8 2.5s8-1.1 8-2.5V5" />
        <path d="M4 11v6c0 1.4 3.6 2.5 8 2.5s8-1.1 8-2.5v-6" />
      </>
    ),
    testing: (
      <>
        {/* Clipboard + check — "device testing checklist" */}
        <rect x="5" y="4" width="14" height="17" rx="2" />
        <rect x="9" y="2.5" width="6" height="3" rx="1" />
        <path d="M8.5 13l2.2 2.2L15.5 10.5" />
      </>
    ),
    debug: (
      <>
        {/* Bug — debug bubble */}
        <rect x="8" y="6" width="8" height="12" rx="4" />
        <path d="M8 10H4M16 10h4M8 14H4M16 14h4M9 6l-2-2M15 6l2-2M9 18l-2 2M15 18l2 2" />
      </>
    ),
    bell: (
      <>
        {/* Bell — Updates + Notifications */}
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </>
    ),
    review: (
      <>
        {/* Clipboard-check — project review */}
        <rect x="5" y="4" width="14" height="17" rx="2" />
        <rect x="9" y="2.5" width="6" height="3" rx="1" />
        <path d="M8.5 13l2.2 2.2L15.5 10.5" />
      </>
    ),
    dbreview: (
      <>
        {/* Database + magnifier — database review */}
        <ellipse cx="11" cy="6" rx="6" ry="2" />
        <path d="M5 6v4c0 1.1 2.7 2 6 2s6-.9 6-2V6" />
        <path d="M8 12.5v3c0 1.1 1.3 2 3 2" />
        <circle cx="17.5" cy="15.5" r="3.5" />
        <path d="M20 18l2 2" />
      </>
    ),
    testcontroller: (
      <>
        {/* Satellite dish — remote-control via Cloudflare relay (D-198 v4).
            The dish represents the outbound WebSocket to the relay;
            the dot represents the radio signal. */}
        <path d="M4 20a8 8 0 0 1 8-8" />
        <path d="M4 20a4 4 0 0 1 4-4" />
        <circle cx="4" cy="20" r="1.4" fill="currentColor" stroke="none" />
        <path d="M12 12l5-5" />
        <path d="M17 3l4 4-4 4-4-4z" />
      </>
    ),
  };

  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      {icons[name] ?? icons.dashboard}
    </svg>
  );
}
