import { type ReactNode } from "react";

/**
 * Card — reusable surface (DESIGN.md §5.2).
 * bg-card-alt surface, 1px border, 16px radius, subtle shadow.
 */
export function Card({
  children,
  className = "",
  as: Tag = "div",
  id,
}: {
  children: ReactNode;
  className?: string;
  as?: React.ElementType;
  id?: string;
}) {
  return (
    <Tag
      id={id}
      className={`rounded-[16px] border border-border bg-bg-card-alt p-5 shadow-card transition-all duration-200 ${className}`}
    >
      {children}
    </Tag>
  );
}

/**
 * CardHeader — title row with optional kicker + right-aligned action.
 */
export function CardHeader({
  kicker,
  title,
  right,
}: {
  kicker?: string;
  title: ReactNode;
  right?: ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-4 mb-4">
      <div className="min-w-0">
        {kicker && (
          <div className="text-[11px] font-medium uppercase tracking-wide text-text-secondary mb-1.5">
            {kicker}
          </div>
        )}
        <h2 className="text-[20px] font-bold tracking-extra-tight text-text-primary leading-tight">
          {title}
        </h2>
      </div>
      {right && <div className="shrink-0">{right}</div>}
    </div>
  );
}
