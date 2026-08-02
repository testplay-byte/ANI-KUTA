"use client";

import { useEffect, useState } from "react";
import { StatusDot } from "@/components/StatusDot";

/**
 * Header — page header (DESIGN.md §8.1).
 * Left: logo "ANI-KUTA" + project status dot.
 * Right: dark mode toggle (sun/moon icon button, DESIGN.md §5.9).
 *
 * The toggle adds/removes the `dark` class on <html>. Initial state is
 * set by an inline script in layout.tsx to avoid flash of wrong theme.
 */
export function Header() {
  return (
    <header className="flex items-center justify-between gap-4">
      {/* Logo + status */}
      <div className="flex items-center gap-2.5">
        <StatusDot color="var(--c-success)" size="md" />
        <span className="text-[15px] font-bold tracking-extra-tight text-text-primary">
          ANI-KUTA
        </span>
        <span className="hidden sm:inline text-[11px] font-medium uppercase tracking-wide text-text-secondary ml-1">
          · Visual Documentation
        </span>
      </div>

      {/* Dark mode toggle */}
      <DarkModeToggle />
    </header>
  );
}

/**
 * DarkModeToggle — pill button with sun/moon icon (DESIGN.md §5.9).
 * Toggles the `dark` class on <html>. Preference persists in localStorage.
 * Initial state is set by the inline script in layout.tsx (no flash).
 */
function DarkModeToggle() {
  const [isDark, setIsDark] = useState(false);

  // Sync state with current document state on mount.
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
      /* localStorage unavailable — ignore */
    }
  };

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
      title={isDark ? "Light mode" : "Dark mode"}
      className="h-9 w-9 rounded-full border flex items-center justify-center transition-all duration-200 hover:translate-y-[-1px] bg-bg-chip border-border text-text-primary"
    >
      {/* Sun icon (shown in dark mode — click to go light) */}
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
      {/* Moon icon (shown in light mode — click to go dark) */}
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
    </button>
  );
}
