import { type ReactNode } from "react";

/**
 * Pill — button/filter pill (DESIGN.md §5.1, §5.7).
 * Rounded-full, h-9, supports active/inactive state.
 */
export function Pill({
  children,
  active = false,
  accentColor = "var(--c-primary)",
  onClick,
  as: Tag = "button",
  href,
  className = "",
}: {
  children: ReactNode;
  active?: boolean;
  accentColor?: string;
  onClick?: () => void;
  as?: "button" | "a";
  href?: string;
  className?: string;
}) {
  const base =
    "h-9 px-[18px] rounded-full text-[13.5px] font-medium transition-all duration-200 flex items-center gap-2 border";
  const stateStyle = active
    ? "text-white"
    : "bg-bg-chip text-text-secondary hover:translate-y-[-1px]";

  const style: React.CSSProperties = active
    ? {
        backgroundColor: accentColor,
        borderColor: accentColor,
        boxShadow: `0 4px 12px ${accentColor}33, 0 1px 2px rgba(0,0,0,0.06)`,
      }
    : {
        borderColor: "var(--c-border)",
      };

  if (Tag === "a") {
    return (
      <a
        href={href}
        className={`${base} ${stateStyle} ${className}`}
        style={style}
      >
        {children}
      </a>
    );
  }
  return (
    <button
      type="button"
      onClick={onClick}
      className={`${base} ${stateStyle} ${className}`}
      style={style}
    >
      {children}
    </button>
  );
}
