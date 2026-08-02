import { type ReactNode } from "react";

/**
 * StatusDot — colored dot indicator (DESIGN.md §5.3).
 * Accepts any CSS color value (hex or var(--...)).
 */
export function StatusDot({
  color = "var(--c-success)",
  size = "sm",
  className = "",
}: {
  color?: string;
  size?: "sm" | "md" | "lg";
  className?: string;
}) {
  const sizeClass =
    size === "lg"
      ? "w-[10px] h-[10px]"
      : size === "md"
        ? "w-2 h-2"
        : "w-1.5 h-1.5";
  return (
    <span
      className={`inline-block rounded-full shrink-0 ${sizeClass} ${className}`}
      style={{ backgroundColor: color, opacity: 0.9 }}
      aria-hidden="true"
    />
  );
}

export function StatusDotLabel({
  color,
  label,
  size = "sm",
}: {
  color: string;
  label: ReactNode;
  size?: "sm" | "md" | "lg";
}) {
  return (
    <span className="inline-flex items-center gap-2">
      <StatusDot color={color} size={size} />
      <span className="text-text-secondary text-[12px]">{label}</span>
    </span>
  );
}
