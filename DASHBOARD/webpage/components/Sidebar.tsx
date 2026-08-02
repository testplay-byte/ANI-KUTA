"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { NAV_ITEMS, QUICK_STATS } from "@/lib/data";

/**
 * Sidebar — primary navigation (DESIGN.md §5.1).
 *
 * v2 features:
 *  - Floating: rounded-2xl on all corners, margin from viewport edges (desktop).
 *  - Shrinkable: toggles between 240px (expanded) and 64px (icon-only).
 *    Preference persists in localStorage (`sidebar-shrink`).
 *  - Sticky on desktop (lg:sticky lg:top-3 lg:h-[calc(100vh-1.5rem)]).
 *  - Translucent surface with backdrop blur.
 *  - On mobile: hidden by default; a hamburger (rendered in Header) opens
 *    the sidebar as a full-screen overlay.
 *
 * Sections: Brand → Nav pills → Build Health widget → User Profile.
 */

const MOBILE_NAV_EVENT = "ani-kuta:toggle-mobile-nav";

/** Dispatched by the Header's hamburger button on mobile. */
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

  // Read shrink preference on mount (set by inline script for no-flash).
  useEffect(() => {
    setMounted(true);
    const stored = document.documentElement.getAttribute("data-sidebar-shrink");
    setShrink(stored === "1");
  }, []);

  // Listen for hamburger toggle from Header (mobile only).
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
      document.documentElement.setAttribute("data-sidebar-shrink", next ? "1" : "0");
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
            <span className="w-9 h-9 rounded-[12px] bg-[#1a1a1a] dark:bg-[#f5f1eb] text-white dark:text-[#1a1a1a] flex items-center justify-center font-bold text-[16px] shrink-0">
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
        <nav className="flex-1 overflow-y-auto px-2.5 py-3 space-y-1" aria-label="Sections">
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
                    ? "bg-[#1a1a1a] dark:bg-[#f5f1eb] text-white dark:text-[#1a1a1a] shadow-[0_4px_16px_rgba(0,0,0,0.12)]"
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
          <div className={`flex items-center gap-2 text-[11px] text-text-secondary ${shrink ? "justify-center" : ""}`}>
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
                <span className="block text-[10px] text-text-secondary">active now</span>
              </span>
            )}
          </div>
        </div>

        {/* Shrink toggle */}
        <button
          type="button"
          onClick={toggleShrink}
          aria-label={shrink ? "Expand sidebar" : "Collapse sidebar"}
          title={shrink ? "Expand sidebar" : "Collapse sidebar"}
          className="hidden lg:flex items-center justify-center w-8 h-8 mx-auto mb-3 rounded-[10px] border border-border bg-surface text-text-secondary hover:text-text-primary hover:bg-canvas transition-all duration-200 shrink-0"
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
