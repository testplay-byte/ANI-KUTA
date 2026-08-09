"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent,
  type ReactNode,
} from "react";
import { Card } from "@/components/Card";

/**
 * DB Viewer page (DESIGN.md §5.2 hero + content).
 *
 * Lets a user upload a `DB.json` export and inspect every table as a
 * searchable, paginated grid. Everything happens client-side — no server
 * upload, no API calls (except the optional "load sample" fetch).
 *
 * Layout:
 *  1. Hero card (title + description + load-state pill).
 *  2. Empty state — drag-and-drop + file picker + "try sample" button.
 *  3. Loaded state — stats row, search bar, table selector sidebar
 *     (desktop) / dropdown (mobile), data grid with sticky headers,
 *     pagination footer.
 *
 * Design tokens (DESIGN.md §8) — all colors come from CSS variables so the
 * page automatically follows the dashboard's dark-mode toggle.
 */

/* ---------------------------------------------------------------------------
 * Types
 * ------------------------------------------------------------------------- */

type Row = Record<string, unknown>;
type DB = Record<string, Row[]>;

interface TableMeta {
  name: string;
  rows: Row[];
  count: number;
  columns: string[];
}

/* ---------------------------------------------------------------------------
 * Constants
 * ------------------------------------------------------------------------- */

const ROWS_PER_PAGE = 50;
const SAMPLE_DB_URL =
  "https://raw.githubusercontent.com/testplay-byte/ANI-KUTA/main/DB.json";

/** Column-name patterns that should render an image preview. */
const IMAGE_COL_PATTERNS: { test: RegExp; shape: "portrait" | "square" }[] = [
  { test: /cover|poster|thumbnail/i, shape: "portrait" }, // 40×56
  { test: /image|url/i, shape: "square" }, // 40×40
];

const MAX_CELL_PREVIEW = 120; // chars before truncation kicks in

/* ---------------------------------------------------------------------------
 * Helpers
 * ------------------------------------------------------------------------- */

/** Convert any cell value to a stable string for display + search. */
function stringifyCell(v: unknown): string {
  if (v === null || v === undefined) return "";
  if (typeof v === "string") return v;
  if (typeof v === "number" || typeof v === "boolean") return String(v);
  if (typeof v === "bigint") return v.toString();
  if (v instanceof Date) return v.toISOString();
  try {
    return JSON.stringify(v);
  } catch {
    return String(v);
  }
}

/** Null-ish cells render as a muted "null" badge. */
function isNullish(v: unknown): boolean {
  return v === null || v === undefined;
}

/** Does the value look like an HTTP(S) URL? */
function looksLikeUrl(v: unknown): v is string {
  return typeof v === "string" && /^https?:\/\//i.test(v);
}

/** Decide whether a column should render image previews + which shape. */
function imageShapeForColumn(col: string): "portrait" | "square" | null {
  for (const { test, shape } of IMAGE_COL_PATTERNS) {
    if (test.test(col)) return shape;
  }
  return null;
}

/** Format bytes as a human-readable size. */
function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(2)} MB`;
}

/** Build table metadata from the parsed DB object. */
function buildTables(db: DB): TableMeta[] {
  return Object.entries(db)
    .filter(([, rows]) => Array.isArray(rows))
    .map(([name, rows]) => {
      const safeRows = rows as Row[];
      const colSet = new Set<string>();
      for (const r of safeRows.slice(0, 200)) {
        if (r && typeof r === "object") {
          for (const k of Object.keys(r)) colSet.add(k);
        }
      }
      return {
        name,
        rows: safeRows,
        count: safeRows.length,
        columns: Array.from(colSet),
      };
    });
}

/** Parse + validate the uploaded JSON. Throws on invalid shapes. */
function parseDB(text: string): DB {
  const data = JSON.parse(text);
  if (typeof data !== "object" || data === null || Array.isArray(data)) {
    throw new Error("Top-level JSON must be an object whose keys are table names.");
  }
  // Allow tables that are arrays of row objects. Drop anything else with a
  // warning rather than crashing — keeps the viewer resilient.
  const cleaned: DB = {};
  for (const [k, v] of Object.entries(data as Record<string, unknown>)) {
    if (Array.isArray(v)) {
      cleaned[k] = v.filter((r) => r && typeof r === "object") as Row[];
    }
  }
  if (Object.keys(cleaned).length === 0) {
    throw new Error("No tables found — expected an object like { tableName: [{ ...row }] }.");
  }
  return cleaned;
}

/**
 * Highlight every case-insensitive occurrence of `query` inside `text`.
 * Returns a React node with `<mark>` wrappers for matches.
 */
function highlightMatch(text: string, query: string): ReactNode {
  if (!query) return text;
  const lowerText = text.toLowerCase();
  const lowerQuery = query.toLowerCase();
  if (!lowerQuery) return text;

  const parts: ReactNode[] = [];
  let i = 0;
  let key = 0;
  while (i < text.length) {
    const idx = lowerText.indexOf(lowerQuery, i);
    if (idx === -1) {
      parts.push(text.slice(i));
      break;
    }
    if (idx > i) parts.push(text.slice(i, idx));
    parts.push(
      <mark
        key={`m-${key++}`}
        className="rounded-[3px] px-0.5 bg-[var(--c-warning)]/30 text-inherit"
      >
        {text.slice(idx, idx + query.length)}
      </mark>,
    );
    i = idx + query.length;
  }
  return <>{parts}</>;
}

/* ---------------------------------------------------------------------------
 * Page
 * ------------------------------------------------------------------------- */

export default function DBViewerPage() {
  const [fileName, setFileName] = useState<string | null>(null);
  const [fileSize, setFileSize] = useState<number>(0);
  const [tables, setTables] = useState<TableMeta[]>([]);
  const [parseError, setParseError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [dragging, setDragging] = useState(false);

  const [selected, setSelected] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(1);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [sidebarFilter, setSidebarFilter] = useState("");

  const fileInputRef = useRef<HTMLInputElement>(null);

  /* ----- file intake ----- */

  const ingestFile = useCallback(async (file: File) => {
    setLoading(true);
    setParseError(null);
    try {
      const text = await file.text();
      const db = parseDB(text);
      const built = buildTables(db);
      setTables(built);
      setFileName(file.name);
      setFileSize(file.size);
      setSelected(built[0]?.name ?? null);
      setQuery("");
      setPage(1);
      setExpanded(new Set());
      setSidebarFilter("");
    } catch (err) {
      setParseError(err instanceof Error ? err.message : String(err));
      setTables([]);
      setFileName(null);
      setSelected(null);
    } finally {
      setLoading(false);
    }
  }, []);

  const onFilePicked = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      const f = e.target.files?.[0];
      if (f) void ingestFile(f);
      // reset input so the same file can be re-selected
      e.target.value = "";
    },
    [ingestFile],
  );

  const onDrop = useCallback(
    (e: DragEvent<HTMLDivElement>) => {
      e.preventDefault();
      setDragging(false);
      const f = e.dataTransfer.files?.[0];
      if (f) void ingestFile(f);
    },
    [ingestFile],
  );

  const onDragOver = useCallback((e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragging(true);
  }, []);

  const onDragLeave = useCallback((e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragging(false);
  }, []);

  const loadSample = useCallback(async () => {
    setLoading(true);
    setParseError(null);
    try {
      const res = await fetch(SAMPLE_DB_URL, { cache: "no-store" });
      if (!res.ok) throw new Error(`HTTP ${res.status} fetching sample DB`);
      const text = await res.text();
      const db = parseDB(text);
      const built = buildTables(db);
      setTables(built);
      setFileName("DB.json (sample)");
      setFileSize(new Blob([text]).size);
      setSelected(built[0]?.name ?? null);
      setQuery("");
      setPage(1);
      setExpanded(new Set());
      setSidebarFilter("");
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setParseError(
        `Couldn't load the sample DB (${msg}). CORS or network may be blocking the request — download it manually and drop the file here instead.`,
      );
    } finally {
      setLoading(false);
    }
  }, []);

  const reset = useCallback(() => {
    setTables([]);
    setFileName(null);
    setFileSize(0);
    setSelected(null);
    setQuery("");
    setPage(1);
    setExpanded(new Set());
    setParseError(null);
    setSidebarFilter("");
  }, []);

  /* ----- derived data ----- */

  const currentTable = useMemo(
    () => tables.find((t) => t.name === selected) ?? null,
    [tables, selected],
  );

  // Reset page when search query or table changes.
  useEffect(() => {
    setPage(1);
  }, [query, selected]);

  const filteredRows = useMemo(() => {
    if (!currentTable) return [];
    const q = query.trim().toLowerCase();
    if (!q) return currentTable.rows;
    return currentTable.rows.filter((row) => {
      for (const col of currentTable.columns) {
        if (stringifyCell(row[col]).toLowerCase().includes(q)) return true;
      }
      return false;
    });
  }, [currentTable, query]);

  const totalPages = Math.max(
    1,
    Math.ceil(filteredRows.length / ROWS_PER_PAGE),
  );
  const safePage = Math.min(page, totalPages);
  const pageRows = useMemo(
    () =>
      filteredRows.slice(
        (safePage - 1) * ROWS_PER_PAGE,
        safePage * ROWS_PER_PAGE,
      ),
    [filteredRows, safePage],
  );

  const totalRows = useMemo(
    () => tables.reduce((sum, t) => sum + t.count, 0),
    [tables],
  );

  const filteredTables = useMemo(() => {
    const q = sidebarFilter.trim().toLowerCase();
    if (!q) return tables;
    return tables.filter((t) => t.name.toLowerCase().includes(q));
  }, [tables, sidebarFilter]);

  /* ----- cell expand toggle ----- */

  const toggleCell = useCallback((key: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }, []);

  /* =========================================================================
   * Render
   * ======================================================================= */

  return (
    <div className="space-y-6">
      {/* ---- Hero ---- */}
      <Card className="!p-6 md:!p-8">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[11px] font-medium uppercase tracking-widest text-text-secondary">
              DB Viewer
            </span>
            <span
              className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[10.5px] font-semibold uppercase tracking-widest border"
              style={{
                color: "var(--c-primary)",
                backgroundColor:
                  "color-mix(in srgb, var(--c-primary) 12%, transparent)",
                borderColor:
                  "color-mix(in srgb, var(--c-primary) 35%, transparent)",
              }}
            >
              <span
                className="inline-block w-1.5 h-1.5 rounded-full"
                style={{ backgroundColor: "var(--c-primary)" }}
              />
              client-side · no upload
            </span>
            {fileName && (
              <span className="text-[12px] text-text-secondary">
                <span className="font-mono">{fileName}</span>
                {fileSize > 0 && <> · {formatBytes(fileSize)}</>}
              </span>
            )}
          </div>

          <h2 className="text-[26px] md:text-[32px] font-bold tracking-extra-tight text-text-primary leading-tight">
            DB Viewer{" "}
            <span className="text-text-secondary font-medium">
              — inspect any database JSON export
            </span>
          </h2>

          <p className="text-[13.5px] text-text-secondary leading-relaxed max-w-2xl">
            Drop a <span className="font-mono text-text-primary">DB.json</span>{" "}
            file and browse its tables as searchable, paginated grids. Images
            auto-preview when a column looks like a cover/poster/URL. Everything
            is parsed in your browser — nothing leaves the page.
          </p>

          {fileName && (
            <div className="flex flex-wrap items-center gap-2 pt-1">
              <button
                type="button"
                onClick={reset}
                className="h-9 px-[18px] rounded-full text-[13.5px] font-medium bg-bg-chip text-text-secondary hover:translate-y-[-1px] hover:text-text-primary border border-border transition-all duration-200"
              >
                Load a different file
              </button>
              <span className="text-[11px] text-text-secondary">
                {tables.length} tables · {totalRows.toLocaleString()} rows total
              </span>
            </div>
          )}
        </div>
      </Card>

      {/* ---- Empty state OR viewer ---- */}
      {!fileName ? (
        <EmptyState
          dragging={dragging}
          loading={loading}
          parseError={parseError}
          onDrop={onDrop}
          onDragOver={onDragOver}
          onDragLeave={onDragLeave}
          onPickClick={() => fileInputRef.current?.click()}
          onLoadSample={loadSample}
        />
      ) : (
        <Card className="!p-0 overflow-hidden">
          {/* Stats + search bar */}
          <div className="flex flex-col gap-3 p-4 md:p-5 border-b border-border">
            <div className="flex flex-col md:flex-row md:items-center gap-3 md:gap-4">
              <div className="flex items-center gap-2 min-w-0 flex-1">
                <div className="relative flex-1 min-w-0">
                  <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-secondary pointer-events-none" />
                  <input
                    type="text"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder={
                      currentTable
                        ? `Search across all ${currentTable.columns.length} columns of "${currentTable.name}"…`
                        : "Search…"
                    }
                    className="w-full h-10 pl-9 pr-9 rounded-[10px] bg-surface-alt border border-border text-[13.5px] text-text-primary placeholder:text-text-secondary focus:outline-none focus:border-[var(--c-primary)] focus:shadow-[0_0_0_4px_rgba(99,102,241,0.15)] transition-all duration-200"
                    aria-label="Filter rows"
                  />
                  {query && (
                    <button
                      type="button"
                      onClick={() => setQuery("")}
                      aria-label="Clear search"
                      className="absolute right-2 top-1/2 -translate-y-1/2 w-6 h-6 rounded-md text-text-secondary hover:text-text-primary hover:bg-canvas flex items-center justify-center"
                    >
                      <CloseIcon className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              </div>

              {currentTable && (
                <div className="flex items-center gap-2 text-[12px] text-text-secondary shrink-0">
                  <span className="font-mono text-text-primary">
                    {currentTable.name}
                  </span>
                  <span>·</span>
                  <span>{currentTable.columns.length} cols</span>
                  <span>·</span>
                  <span>
                    {filteredRows.length.toLocaleString()} /{" "}
                    {currentTable.count.toLocaleString()} rows
                  </span>
                </div>
              )}
            </div>
          </div>

          {/* Body: table selector + grid */}
          <div className="flex flex-col lg:flex-row min-h-[420px]">
            {/* Sidebar (desktop) */}
            <aside className="hidden lg:flex flex-col w-[260px] shrink-0 border-r border-border bg-surface-alt/50">
              <div className="p-3 border-b border-border">
                <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-2">
                  Tables ({tables.length})
                </div>
                <input
                  type="text"
                  value={sidebarFilter}
                  onChange={(e) => setSidebarFilter(e.target.value)}
                  placeholder="Filter tables…"
                  className="w-full h-8 px-2.5 rounded-md bg-surface border border-border text-[12.5px] text-text-primary placeholder:text-text-secondary focus:outline-none focus:border-[var(--c-primary)]"
                  aria-label="Filter tables"
                />
              </div>
              <nav
                className="flex-1 overflow-y-auto p-2 space-y-0.5 max-h-[60vh]"
                aria-label="Table selector"
              >
                {filteredTables.map((t) => (
                  <TableListItem
                    key={t.name}
                    name={t.name}
                    count={t.count}
                    active={t.name === selected}
                    onClick={() => setSelected(t.name)}
                  />
                ))}
                {filteredTables.length === 0 && (
                  <div className="text-[12px] text-text-secondary px-2 py-4 text-center">
                    No tables match &ldquo;{sidebarFilter}&rdquo;.
                  </div>
                )}
              </nav>
            </aside>

            {/* Mobile table dropdown */}
            <div className="lg:hidden p-3 border-b border-border bg-surface-alt/50">
              <label className="block text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-1.5">
                Table ({tables.length})
              </label>
              <div className="relative">
                <select
                  value={selected ?? ""}
                  onChange={(e) => setSelected(e.target.value || null)}
                  className="w-full h-10 pl-3 pr-8 rounded-[10px] bg-surface border border-border text-[13px] text-text-primary font-mono appearance-none focus:outline-none focus:border-[var(--c-primary)]"
                  aria-label="Select table"
                >
                  {tables.map((t) => (
                    <option key={t.name} value={t.name}>
                      {t.name} ({t.count})
                    </option>
                  ))}
                </select>
                <ChevronDownIcon className="absolute right-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-text-secondary pointer-events-none" />
              </div>
            </div>

            {/* Data grid */}
            <div className="flex-1 min-w-0 flex flex-col">
              {currentTable ? (
                currentTable.columns.length === 0 ? (
                  <div className="p-8 text-center text-[13px] text-text-secondary">
                    Table <span className="font-mono">{currentTable.name}</span>{" "}
                    has no rows.
                  </div>
                ) : (
                  <>
                    <DataGrid
                      columns={currentTable.columns}
                      rows={pageRows}
                      query={query}
                      pageStart={(safePage - 1) * ROWS_PER_PAGE}
                      expanded={expanded}
                      onToggleCell={toggleCell}
                    />
                    <Pagination
                      page={safePage}
                      totalPages={totalPages}
                      totalRows={filteredRows.length}
                      pageSize={ROWS_PER_PAGE}
                      onPrev={() => setPage((p) => Math.max(1, p - 1))}
                      onNext={() =>
                        setPage((p) => Math.min(totalPages, p + 1))
                      }
                      onFirst={() => setPage(1)}
                      onLast={() => setPage(totalPages)}
                    />
                  </>
                )
              ) : (
                <div className="p-8 text-center text-[13px] text-text-secondary">
                  Select a table to view its rows.
                </div>
              )}
            </div>
          </div>
        </Card>
      )}

      {/* Hidden file input — opened by the dropzone click handler */}
      <input
        ref={fileInputRef}
        type="file"
        accept="application/json,.json"
        onChange={onFilePicked}
        className="hidden"
        aria-hidden="true"
        tabIndex={-1}
      />
    </div>
  );
}

/* ===========================================================================
 * Sub-components
 * ======================================================================= */

function EmptyState({
  dragging,
  loading,
  parseError,
  onDrop,
  onDragOver,
  onDragLeave,
  onPickClick,
  onLoadSample,
}: {
  dragging: boolean;
  loading: boolean;
  parseError: string | null;
  onDrop: (e: DragEvent<HTMLDivElement>) => void;
  onDragOver: (e: DragEvent<HTMLDivElement>) => void;
  onDragLeave: (e: DragEvent<HTMLDivElement>) => void;
  onPickClick: () => void;
  onLoadSample: () => void;
}) {
  return (
    <Card className="!p-0 overflow-hidden">
      <div
        onDrop={onDrop}
        onDragOver={onDragOver}
        onDragLeave={onDragLeave}
        className={`
          relative p-8 md:p-12 lg:p-16 text-center transition-all duration-200
          ${dragging
            ? "bg-[color-mix(in_srgb,var(--c-primary)_8%,transparent)] ring-2 ring-[var(--c-primary)] ring-inset"
            : "bg-surface-alt/40"
          }
        `}
      >
        <div className="flex flex-col items-center gap-5 max-w-xl mx-auto">
          {/* Icon */}
          <div
            className="w-14 h-14 rounded-[16px] bg-bg-chip border border-border flex items-center justify-center text-text-secondary"
            aria-hidden="true"
          >
            <UploadIcon className="w-6 h-6" />
          </div>

          <div className="space-y-1.5">
            <h3 className="text-[18px] font-bold tracking-extra-tight text-text-primary">
              {loading ? "Loading…" : "Drop your DB.json here"}
            </h3>
            <p className="text-[13px] text-text-secondary leading-relaxed">
              Drag-and-drop a{" "}
              <span className="font-mono text-text-primary">DB.json</span> file,
              or click to browse. The file is parsed entirely in your browser —
              nothing is uploaded.
            </p>
          </div>

          <div className="flex flex-wrap items-center justify-center gap-2 pt-1">
            <button
              type="button"
              onClick={onPickClick}
              disabled={loading}
              className="h-9 px-[18px] rounded-full text-[13.5px] font-medium bg-[#1a1a1a] dark:bg-[#E8E8E8] text-white dark:text-[#1a1a1a] hover:translate-y-[-1px] transition-all duration-200 disabled:opacity-60 disabled:hover:translate-y-0 shadow-[0_4px_16px_rgba(0,0,0,0.12)]"
            >
              Choose file
            </button>
            <button
              type="button"
              onClick={onLoadSample}
              disabled={loading}
              className="h-9 px-[18px] rounded-full text-[13.5px] font-medium bg-bg-chip text-text-secondary hover:translate-y-[-1px] hover:text-text-primary border border-border transition-all duration-200 disabled:opacity-60 disabled:hover:translate-y-0"
            >
              Try sample DB
            </button>
          </div>

          {parseError && (
            <div
              className="w-full mt-3 p-3 rounded-[10px] border text-left text-[12.5px] leading-relaxed"
              style={{
                color: "var(--c-danger)",
                backgroundColor:
                  "color-mix(in srgb, var(--c-danger) 8%, transparent)",
                borderColor:
                  "color-mix(in srgb, var(--c-danger) 30%, transparent)",
              }}
              role="alert"
            >
              <strong className="font-semibold">
                Couldn&apos;t parse file:
              </strong>{" "}
              {parseError}
            </div>
          )}

          <div className="mt-2 grid grid-cols-1 sm:grid-cols-3 gap-2 w-full text-left">
            <FeatureNote
              title="Tables"
              body="Sidebar lists every table with its row count."
            />
            <FeatureNote
              title="Search"
              body="Filters across all columns; matches are highlighted."
            />
            <FeatureNote
              title="Images"
              body="Cover / poster / URL cells auto-preview."
            />
          </div>
        </div>
      </div>
    </Card>
  );
}

function FeatureNote({ title, body }: { title: string; body: string }) {
  return (
    <div className="p-3 rounded-[10px] bg-surface border border-border">
      <div className="text-[11px] font-semibold uppercase tracking-widest text-text-secondary mb-1">
        {title}
      </div>
      <div className="text-[12px] text-text-primary leading-snug">{body}</div>
    </div>
  );
}

function TableListItem({
  name,
  count,
  active,
  onClick,
}: {
  name: string;
  count: number;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-current={active ? "true" : undefined}
      className={`
        w-full flex items-center gap-2 px-2.5 py-2 rounded-[8px] text-left
        text-[12.5px] transition-all duration-150
        ${active
          ? "bg-[#1a1a1a] dark:bg-[#E8E8E8] text-white dark:text-[#1a1a1a] shadow-[0_4px_16px_rgba(0,0,0,0.12)]"
          : "text-text-secondary hover:text-text-primary hover:bg-canvas"
        }
      `}
    >
      <span className="font-mono truncate flex-1 min-w-0">{name}</span>
      <span
        className={`
          shrink-0 inline-flex items-center justify-center min-w-[24px] h-5 px-1.5
          rounded-full text-[10.5px] font-semibold tabular-nums
          ${active
            ? "bg-white/15 dark:bg-black/15"
            : "bg-bg-chip border border-border text-text-secondary"
          }
        `}
      >
        {count > 999 ? `${(count / 1000).toFixed(1)}k` : count}
      </span>
    </button>
  );
}

/* ----------- Data grid ----------- */

function DataGrid({
  columns,
  rows,
  query,
  pageStart,
  expanded,
  onToggleCell,
}: {
  columns: string[];
  rows: Row[];
  query: string;
  pageStart: number;
  expanded: Set<string>;
  onToggleCell: (key: string) => void;
}) {
  if (rows.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center p-10 text-center">
        <div className="space-y-1.5">
          <div className="text-[14px] font-medium text-text-primary">
            No rows found
          </div>
          <div className="text-[12.5px] text-text-secondary">
            {query
              ? `No rows match “${query}” in this table.`
              : "This table is empty."}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-auto max-h-[70vh]">
      <table className="min-w-full border-collapse text-[12.5px]">
        <thead className="sticky top-0 z-10">
          <tr>
            <th
              scope="col"
              className="sticky left-0 z-20 bg-surface-alt border-b border-r border-border px-2.5 py-2 text-left text-[10.5px] font-semibold uppercase tracking-widest text-text-secondary w-[44px] min-w-[44px]"
            >
              #
            </th>
            {columns.map((col) => (
              <th
                key={col}
                scope="col"
                className="bg-surface-alt border-b border-r last:border-r-0 border-border px-3 py-2 text-left text-[10.5px] font-semibold uppercase tracking-widest text-text-secondary whitespace-nowrap min-w-[140px]"
              >
                <span className="inline-flex items-center gap-1.5">
                  {imageShapeForColumn(col) && (
                    <span
                      className="inline-block w-1.5 h-1.5 rounded-full"
                      style={{ backgroundColor: "var(--c-secondary)" }}
                      aria-hidden="true"
                      title="Image preview column"
                    />
                  )}
                  <span className="font-mono normal-case tracking-normal text-[11.5px] text-text-primary">
                    {col}
                  </span>
                </span>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => {
            const rowIdx = pageStart + i + 1;
            return (
              <tr
                key={rowIdx}
                className="group hover:bg-canvas/60 transition-colors duration-100"
              >
                <td className="sticky left-0 z-10 bg-surface group-hover:bg-canvas/60 transition-colors duration-100 border-b border-r border-border px-2.5 py-2 text-[11px] font-mono text-text-secondary text-right tabular-nums align-top">
                  {rowIdx}
                </td>
                {columns.map((col) => {
                  const value = row[col];
                  const cellKey = `${rowIdx}:${col}`;
                  const isExpanded = expanded.has(cellKey);
                  return (
                    <td
                      key={col}
                      className="border-b border-r last:border-r-0 border-border px-3 py-2 align-top text-text-primary"
                    >
                      <Cell
                        value={value}
                        col={col}
                        query={query}
                        expanded={isExpanded}
                        onToggle={() => onToggleCell(cellKey)}
                      />
                    </td>
                  );
                })}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

/** A single cell — handles nulls, image previews, truncation + highlighting. */
function Cell({
  value,
  col,
  query,
  expanded,
  onToggle,
}: {
  value: unknown;
  col: string;
  query: string;
  expanded: boolean;
  onToggle: () => void;
}) {
  if (isNullish(value)) {
    return (
      <span className="inline-flex items-center px-1.5 py-0.5 rounded-[5px] bg-bg-chip border border-border text-[10.5px] font-mono text-text-secondary">
        null
      </span>
    );
  }

  const text = stringifyCell(value);
  const shape = imageShapeForColumn(col);
  const isImg = shape !== null && looksLikeUrl(value);

  // Long-text threshold: show "click to expand" affordance when truncated.
  const isLong = text.length > MAX_CELL_PREVIEW;

  return (
    <div className={`flex items-start gap-2 ${isImg ? "" : "min-w-0"}`}>
      {isImg && <CellImage src={value} shape={shape} />}

      <div
        className={`min-w-0 flex-1 ${expanded ? "whitespace-pre-wrap break-words" : "truncate"}`}
        onClick={isLong ? onToggle : undefined}
        role={isLong ? "button" : undefined}
        tabIndex={isLong ? 0 : undefined}
        onKeyDown={
          isLong
            ? (e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  onToggle();
                }
              }
            : undefined
        }
        title={isLong && !expanded ? "Click to expand" : undefined}
        style={isLong ? { cursor: "pointer" } : undefined}
      >
        {query ? highlightMatch(text, query) : text}
      </div>

      {isLong && !expanded && (
        <button
          type="button"
          onClick={onToggle}
          className="shrink-0 text-[10px] font-medium uppercase tracking-widest text-[var(--c-primary)] hover:underline"
          aria-label="Expand cell"
        >
          more
        </button>
      )}
      {isLong && expanded && (
        <button
          type="button"
          onClick={onToggle}
          className="shrink-0 text-[10px] font-medium uppercase tracking-widest text-text-secondary hover:text-text-primary hover:underline"
          aria-label="Collapse cell"
        >
          less
        </button>
      )}
    </div>
  );
}

/** Image preview cell — falls back to a placeholder on error. */
function CellImage({
  src,
  shape,
}: {
  src: string;
  shape: "portrait" | "square";
}) {
  const [errored, setErrored] = useState(false);

  useEffect(() => {
    setErrored(false);
  }, [src]);

  const sizeClass = shape === "portrait" ? "w-10 h-14" : "w-10 h-10";

  if (errored) {
    return (
      <div
        className={`${sizeClass} shrink-0 rounded-[6px] border border-border bg-bg-chip flex items-center justify-center text-text-secondary`}
        aria-label="Image failed to load"
        title="Image failed to load"
      >
        <ImageOffIcon className="w-4 h-4" />
      </div>
    );
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      alt=""
      loading="lazy"
      onError={() => setErrored(true)}
      className={`${sizeClass} shrink-0 rounded-[6px] border border-border object-cover bg-bg-chip`}
    />
  );
}

/* ----------- Pagination ----------- */

function Pagination({
  page,
  totalPages,
  totalRows,
  pageSize,
  onPrev,
  onNext,
  onFirst,
  onLast,
}: {
  page: number;
  totalPages: number;
  totalRows: number;
  pageSize: number;
  onPrev: () => void;
  onNext: () => void;
  onFirst: () => void;
  onLast: () => void;
}) {
  const start = totalRows === 0 ? 0 : (page - 1) * pageSize + 1;
  const end = Math.min(page * pageSize, totalRows);

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 border-t border-border bg-surface-alt/50">
      <div className="text-[12px] text-text-secondary tabular-nums">
        Showing{" "}
        <span className="font-medium text-text-primary">{start}</span>–
        <span className="font-medium text-text-primary">{end}</span> of{" "}
        <span className="font-medium text-text-primary">
          {totalRows.toLocaleString()}
        </span>{" "}
        rows
      </div>
      <div className="flex items-center gap-1">
        <PageButton onClick={onFirst} disabled={page <= 1} label="First">
          «
        </PageButton>
        <PageButton onClick={onPrev} disabled={page <= 1} label="Previous">
          ‹
        </PageButton>
        <div className="px-3 text-[12px] text-text-secondary tabular-nums">
          Page <span className="font-medium text-text-primary">{page}</span> /{" "}
          {totalPages}
        </div>
        <PageButton onClick={onNext} disabled={page >= totalPages} label="Next">
          ›
        </PageButton>
        <PageButton onClick={onLast} disabled={page >= totalPages} label="Last">
          »
        </PageButton>
      </div>
    </div>
  );
}

function PageButton({
  children,
  onClick,
  disabled,
  label,
}: {
  children: ReactNode;
  onClick: () => void;
  disabled: boolean;
  label: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      title={label}
      className="w-8 h-8 rounded-[8px] border border-border bg-surface text-[14px] leading-none text-text-secondary hover:text-text-primary hover:bg-canvas hover:translate-y-[-1px] transition-all duration-150 disabled:opacity-40 disabled:hover:translate-y-0 disabled:hover:bg-surface disabled:hover:text-text-secondary flex items-center justify-center"
    >
      {children}
    </button>
  );
}

/* ===========================================================================
 * Inline icons (24×24, stroke-based — matches Sidebar's NavIcon set)
 * ======================================================================= */

function UploadIcon({ className = "" }: { className?: string }) {
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
      <path d="M12 16V4" />
      <path d="M6 10l6-6 6 6" />
      <path d="M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
    </svg>
  );
}

function SearchIcon({ className = "" }: { className?: string }) {
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
      <circle cx="11" cy="11" r="7" />
      <path d="M21 21l-4.3-4.3" />
    </svg>
  );
}

function CloseIcon({ className = "" }: { className?: string }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <path d="M18 6L6 18M6 6l12 12" />
    </svg>
  );
}

function ChevronDownIcon({ className = "" }: { className?: string }) {
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
      <path d="M6 9l6 6 6-6" />
    </svg>
  );
}

function ImageOffIcon({ className = "" }: { className?: string }) {
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
      <path d="M3 3l18 18" />
      <path d="M21 15v4a2 2 0 0 1-2 2H7l5-5" />
      <path d="M3 5a2 2 0 0 1 2-2h14" />
      <path d="M3 9v10a2 2 0 0 0 2 2h2" />
    </svg>
  );
}
