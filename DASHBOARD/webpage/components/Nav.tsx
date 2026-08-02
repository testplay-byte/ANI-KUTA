"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { NAV_ITEMS } from "@/lib/data";
import { Pill } from "@/components/Pill";

/**
 * Nav — navigation pills row (DESIGN.md §5.7).
 * Highlights the active route. Uses basePath-aware Link.
 */
export function Nav() {
  const pathname = usePathname();

  // Normalize: strip trailing slash, strip basePath (e.g. "/ANI-KUTA").
  // Next.js usePathname returns the path WITHOUT basePath in App Router,
  // but we strip defensively in case the deployment differs.
  const normalize = (p: string) => {
    if (!p) return "/";
    let s = p.replace(/\/+$/, "") || "/";
    s = s.replace(/^\/ANI-KUTA/i, "") || "/";
    return s;
  };
  const current = normalize(pathname);

  return (
    <nav
      aria-label="Primary"
      className="flex flex-wrap items-center gap-2 mt-6"
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
            className="no-underline"
            aria-current={isActive ? "page" : undefined}
          >
            <Pill as="a" href={item.href} active={isActive}>
              {item.label}
            </Pill>
          </Link>
        );
      })}
    </nav>
  );
}
