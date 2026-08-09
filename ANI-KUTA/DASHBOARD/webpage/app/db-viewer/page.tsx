"use client";

import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent,
  type MouseEvent as ReactMouseEvent,
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
 * Feature set (v2):
 *  1. Column drag-to-resize — drag the right border of any column header.
 *  2. Fullscreen mode — hide hero + sidebar, focus on the data grid.
 *  3. Collapsible table sidebar — toggle between full + icon-only.
 *  4. Image fullscreen viewer — click any preview to open a full-screen overlay.
 *  5. Column max-width — default cap of 200px (overridable via resize).
 *  6. Cell click popup — modal showing the full value of any cell.
 *  7. Row-number popup — modal showing all columns of a row.
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

/** Info for the cell-click popup (#6). */
interface CellPopup {
  column: string;
  value: unknown;
  rowIndex: number;
}

/** Info for the row-click popup (#7). */
interface RowPopup {
  row: Row;
  columns: string[];
  rowIndex: number;
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

/** Default column width bounds (features #1 + #5). */
const COL_DEFAULT_MIN = 60;
const COL_DEFAULT_MAX = 130; // ~15 chars at 12.5px mono
const COL_RESIZE_MAX = 600; // hard cap when dragging
const ROW_NUM_COL_WIDTH = 56;

/** CSS storage key for the sidebar collapsed state (#3). */
const SIDEBAR_COLLAPSED_KEY = "db-viewer:sidebar-collapsed";

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

  /* ----- feature state ----- */

  // #1 — column widths keyed by `${tableName}::${columnName}`
  const [colWidths, setColWidths] = useState<Record<string, number>>({});

  // #2 — fullscreen viewer
  const [isFullscreen, setIsFullscreen] = useState(false);

  // #3 — sidebar collapsed (persisted to localStorage)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  // #4 — image fullscreen overlay
  const [imageViewer, setImageViewer] = useState<{
    src: string;
    alt: string;
  } | null>(null);

  // #6 — cell-click popup
  const [cellPopup, setCellPopup] = useState<CellPopup | null>(null);

  // #7 — row-number popup
  const [rowPopup, setRowPopup] = useState<RowPopup | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  /* ----- hydrate persisted sidebar state ----- */
  useEffect(() => {
    try {
      const stored = localStorage.getItem(SIDEBAR_COLLAPSED_KEY);
      if (stored === "1") setSidebarCollapsed(true);
    } catch {
      /* ignore — storage may be blocked */
    }
  }, []);

  const toggleSidebar = useCallback(() => {
    setSidebarCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem(SIDEBAR_COLLAPSED_KEY, next ? "1" : "0");
      } catch {
        /* ignore */
      }
      return next;
    });
  }, []);

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
      setColWidths({});
      setImageViewer(null);
      setCellPopup(null);
      setRowPopup(null);
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
      setColWidths({});
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
    setColWidths({});
    setIsFullscreen(false);
    setImageViewer(null);
    setCellPopup(null);
    setRowPopup(null);
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

  /* ----- column resize handlers (#1) ----- */

  const colWidthKey = useCallback(
    (col: string) => `${selected ?? ""}::${col}`,
    [selected],
  );

  const getColWidth = useCallback(
    (col: string): number => {
      const k = colWidthKey(col);
      const w = colWidths[k];
      if (w && w > 0) return w;
      // Default ~15 chars (COL_DEFAULT_MAX=130). Image cols slightly different.
      const shape = imageShapeForColumn(col);
      return shape === "portrait" ? 90 : shape === "square" ? 90 : 130;
    },
    [colWidths, colWidthKey],
  );

  const handleColResize = useCallback(
    (col: string, newWidth: number) => {
      const clamped = Math.max(
        COL_DEFAULT_MIN,
        Math.min(COL_RESIZE_MAX, Math.round(newWidth)),
      );
      setColWidths((prev) => ({
        ...prev,
        [colWidthKey(col)]: clamped,
      }));
    },
    [colWidthKey],
  );

  /* ----- Escape key for popups / fullscreen ----- */
  useEffect(() => {
    if (
      !imageViewer &&
      !cellPopup &&
      !rowPopup &&
      !isFullscreen
    ) {
      return;
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      e.preventDefault();
      // close in priority order: popups first, then fullscreen
      if (imageViewer) setImageViewer(null);
      else if (cellPopup) setCellPopup(null);
      else if (rowPopup) setRowPopup(null);
      else if (isFullscreen) setIsFullscreen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [imageViewer, cellPopup, rowPopup, isFullscreen]);

  /* ----- Sync isFullscreen with browser native fullscreen changes ----- */
  useEffect(() => {
    const onFsChange = () => {
      if (!document.fullscreenElement) {
        setIsFullscreen(false);
      }
    };
    document.addEventListener("fullscreenchange", onFsChange);
    document.addEventListener("webkitfullscreenchange", onFsChange);
    return () => {
      document.removeEventListener("fullscreenchange", onFsChange);
      document.removeEventListener("webkitfullscreenchange", onFsChange);
    };
  }, []);

  /* ----- body scroll lock while any overlay is open ----- */
  useEffect(() => {
    const anyOverlayOpen = !!imageViewer || !!cellPopup || !!rowPopup;
    if (typeof document === "undefined") return;
    if (anyOverlayOpen) {
      const prev = document.body.style.overflow;
      document.body.style.overflow = "hidden";
      return () => {
        document.body.style.overflow = prev;
      };
    }
  }, [imageViewer, cellPopup, rowPopup]);

  /* =========================================================================
   * Render
   * ======================================================================= */

  return (
    <div
      className={isFullscreen ? "fixed inset-0 z-[200] bg-background overflow-hidden flex flex-col p-4 space-y-3" : "space-y-6"}
    >
      {/* ---- Hero (hidden in fullscreen) ---- */}
      {!isFullscreen && (
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
      )}

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
          {/* Stats + search bar (with fullscreen toggle) */}
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

              {/* #2 — Fullscreen toggle (browser native + UI fullscreen) */}
              <button
                type="button"
                onClick={() => {
                  const next = !isFullscreen;
                  setIsFullscreen(next);
                  if (next) {
                    // Request browser native fullscreen on the document element.
                    const el = document.documentElement;
                    if (el.requestFullscreen) el.requestFullscreen();
                    else if ((el as any).webkitRequestFullscreen) (el as any).webkitRequestFullscreen();
                  } else {
                    if (document.fullscreenElement) {
                      if (document.exitFullscreen) document.exitFullscreen();
                      else if ((document as any).webkitExitFullscreen) (document as any).webkitExitFullscreen();
                    }
                  }
                }}
                aria-label={isFullscreen ? "Exit fullscreen" : "Enter fullscreen"}
                title={isFullscreen ? "Exit fullscreen (Esc)" : "Fullscreen"}
                className="shrink-0 inline-flex items-center justify-center gap-1.5 h-9 px-3 rounded-[10px] border border-border bg-surface text-[12px] font-medium text-text-secondary hover:text-text-primary hover:bg-canvas hover:translate-y-[-1px] transition-all duration-150"
              >
                {isFullscreen ? (
                  <ExitFullscreenIcon className="w-4 h-4" />
                ) : (
                  <FullscreenIcon className="w-4 h-4" />
                )}
                <span className="hidden sm:inline">
                  {isFullscreen ? "Exit" : "Fullscreen"}
                </span>
              </button>
            </div>
          </div>

          {/* Body: table selector + grid */}
          {/* In fullscreen: no page scroll (overflow hidden), only table scrolls. */}
          <div className={`flex flex-col lg:flex-row min-h-[420px] ${isFullscreen ? "h-[calc(100vh-60px)]" : ""}`}>
            {/* Sidebar (desktop) — feature #3 collapsible. Kept visible in fullscreen. */}
            <aside
                className={`hidden lg:flex flex-col shrink-0 border-r border-border bg-surface-alt/50 transition-[width] duration-200 ${
                  sidebarCollapsed ? "w-[56px]" : "w-[260px]"
                }`}
              >
                <div
                  className={`p-3 border-b border-border ${
                    sidebarCollapsed ? "px-2" : ""
                  }`}
                >
                  {!sidebarCollapsed && (
                    <>
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
                    </>
                  )}
                  <button
                    type="button"
                    onClick={toggleSidebar}
                    aria-label={
                      sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"
                    }
                    title={
                      sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"
                    }
                    className={`mt-2 w-full h-7 inline-flex items-center justify-center gap-1.5 rounded-md border border-border bg-surface text-[11px] font-medium text-text-secondary hover:text-text-primary hover:bg-canvas transition-colors ${
                      sidebarCollapsed ? "mt-0" : ""
                    }`}
                  >
                    {sidebarCollapsed ? (
                      <ChevronRightIcon className="w-3.5 h-3.5" />
                    ) : (
                      <>
                        <ChevronLeftIcon className="w-3.5 h-3.5" />
                        <span>Collapse</span>
                      </>
                    )}
                  </button>
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
                      collapsed={sidebarCollapsed}
                      onClick={() => setSelected(t.name)}
                    />
                  ))}
                  {filteredTables.length === 0 && !sidebarCollapsed && (
                    <div className="text-[12px] text-text-secondary px-2 py-4 text-center">
                      No tables match &ldquo;{sidebarFilter}&rdquo;.
                    </div>
                  )}
                </nav>
              </aside>

            {/* Mobile table dropdown (hidden in fullscreen — keep mobile UX) */}
            {!isFullscreen && (
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
            )}

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
                      tableName={currentTable.name}
                      columns={currentTable.columns}
                      rows={pageRows}
                      query={query}
                      pageStart={(safePage - 1) * ROWS_PER_PAGE}
                      expanded={expanded}
                      onToggleCell={toggleCell}
                      getColWidth={getColWidth}
                      onColResize={handleColResize}
                      onCellClick={(column, value, rowIndex) =>
                        setCellPopup({ column, value, rowIndex })
                      }
                      onRowNumClick={(row, columns, rowIndex) =>
                        setRowPopup({ row, columns, rowIndex })
                      }
                      onImageClick={(src) =>
                        setImageViewer({ src, alt: "Image preview" })
                      }
                      isFullscreen={isFullscreen}
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

      {/* #4 — Image fullscreen overlay */}
      {imageViewer && (
        <ImageFullscreenOverlay
          src={imageViewer.src}
          alt={imageViewer.alt}
          onClose={() => setImageViewer(null)}
        />
      )}

      {/* #6 — Cell click popup */}
      {cellPopup && (
        <CellPopupModal
          column={cellPopup.column}
          value={cellPopup.value}
          rowIndex={cellPopup.rowIndex}
          onClose={() => setCellPopup(null)}
          onImageClick={(src) =>
            setImageViewer({ src, alt: "Image preview" })
          }
        />
      )}

      {/* #7 — Row click popup */}
      {rowPopup && (
        <RowPopupModal
          row={rowPopup.row}
          columns={rowPopup.columns}
          rowIndex={rowPopup.rowIndex}
          onClose={() => setRowPopup(null)}
          onImageClick={(src) =>
            setImageViewer({ src, alt: "Image preview" })
          }
        />
      )}
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
  collapsed,
  onClick,
}: {
  name: string;
  count: number;
  active: boolean;
  collapsed: boolean;
  onClick: () => void;
}) {
  if (collapsed) {
    // Icon-only mode: show first 2 letters as a tile, full name on hover.
    return (
      <button
        type="button"
        onClick={onClick}
        aria-label={`${name} — ${count} rows`}
        title={`${name} (${count})`}
        aria-current={active ? "true" : undefined}
        className={`
          w-full flex items-center justify-center px-1 py-2 rounded-[8px] text-center
          text-[11px] font-mono transition-all duration-150
          ${active
            ? "bg-[#1a1a1a] dark:bg-[#E8E8E8] text-white dark:text-[#1a1a1a] shadow-[0_4px_16px_rgba(0,0,0,0.12)]"
            : "text-text-secondary hover:text-text-primary hover:bg-canvas"
          }
        `}
      >
        <span className="uppercase tracking-tight">
          {name.slice(0, 2)}
        </span>
      </button>
    );
  }
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
  tableName,
  columns,
  rows,
  query,
  pageStart,
  expanded,
  onToggleCell,
  getColWidth,
  onColResize,
  onCellClick,
  onRowNumClick,
  onImageClick,
  isFullscreen,
}: {
  tableName: string;
  columns: string[];
  rows: Row[];
  query: string;
  pageStart: number;
  expanded: Set<string>;
  onToggleCell: (key: string) => void;
  getColWidth: (col: string) => number;
  onColResize: (col: string, newWidth: number) => void;
  onCellClick: (column: string, value: unknown, rowIndex: number) => void;
  onRowNumClick: (row: Row, columns: string[], rowIndex: number) => void;
  onImageClick: (src: string) => void;
  isFullscreen?: boolean;
}) {
  // Reference to the active "drag" so the global mousemove/mouseup listeners
  // know which column is being resized.
  const dragStateRef = useRef<{
    col: string;
    startX: number;
    startWidth: number;
  } | null>(null);

  // We register global listeners ONCE (empty deps); they read from dragStateRef
  // + onColResizeRef to avoid stale closures + re-registration issues.
  const onColResizeRef = useRef(onColResize);
  onColResizeRef.current = onColResize;

  useEffect(() => {
    const onMove = (e: globalThis.MouseEvent) => {
      const ds = dragStateRef.current;
      if (!ds) return;
      e.preventDefault();
      e.stopPropagation();
      const delta = e.clientX - ds.startX;
      onColResizeRef.current(ds.col, ds.startWidth + delta);
    };
    const onUp = (e: globalThis.MouseEvent) => {
      if (dragStateRef.current) {
        e.preventDefault();
        e.stopPropagation();
        dragStateRef.current = null;
        document.body.style.cursor = "";
        document.body.style.userSelect = "";
      }
    };
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
    return () => {
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
    };
  }, []);

  const startResize = useCallback(
    (e: ReactMouseEvent, col: string) => {
      // Don't select text or trigger click handlers on the header.
      e.preventDefault();
      e.stopPropagation();
      const currentWidth = getColWidth(col);
      dragStateRef.current = {
        col,
        startX: e.clientX,
        startWidth: currentWidth,
      };
      document.body.style.cursor = "col-resize";
      document.body.style.userSelect = "none";
    },
    [getColWidth],
  );

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

  // Compute the total table width = sum of all <col> widths.
  // With tableLayout: "fixed" + an explicit width, <col> widths are AUTHORITATIVE
  // (not proportional hints). The table won't stretch to fill the container, and
  // cells won't push columns wider than their <col> width — content truncates.
  const totalTableWidth = ROW_NUM_COL_WIDTH + columns.reduce((sum, col) => sum + getColWidth(col), 0);

  return (
    <div className={`flex-1 overflow-auto min-h-0 ${isFullscreen ? "" : "max-h-[70vh]"}`}>
      <table
        className="border-collapse text-[12.5px]"
        style={{ tableLayout: "fixed", width: totalTableWidth }}
      >
        <colgroup>
          <col style={{ width: ROW_NUM_COL_WIDTH }} />
          {columns.map((col) => (
            <col key={col} style={{ width: getColWidth(col) }} />
          ))}
        </colgroup>
        <thead className="sticky top-0 z-40">
          <tr>
            <th
              scope="col"
              className="sticky left-0 z-30 bg-canvas border-b border-r border-border px-2.5 py-2 text-left text-[10.5px] font-semibold uppercase tracking-widest text-text-secondary"
              style={{ width: ROW_NUM_COL_WIDTH, minWidth: ROW_NUM_COL_WIDTH }}
            >
              #
            </th>
            {columns.map((col) => (
              <th
                key={col}
                scope="col"
                className="relative bg-surface-alt border-b border-r last:border-r-0 border-border px-3 py-2 text-left text-[10.5px] font-semibold uppercase tracking-widest text-text-secondary"
              >
                <span className="flex items-center gap-1.5 min-w-0 pr-3">
                  {imageShapeForColumn(col) && (
                    <span
                      className="inline-block w-1.5 h-1.5 rounded-full shrink-0"
                      style={{ backgroundColor: "var(--c-secondary)" }}
                      aria-hidden="true"
                      title="Image preview column"
                    />
                  )}
                  <span
                    className="font-mono normal-case tracking-normal text-[11.5px] text-text-primary truncate min-w-0"
                    title={col}
                  >
                    {col}
                  </span>
                </span>
                {/* #1 — resize handle */}
                <ColumnResizeHandle
                  onMouseDown={(e) => startResize(e, col)}
                  tableName={tableName}
                  colName={col}
                />
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
                <td
                  className="sticky left-0 z-20 bg-canvas group-hover:bg-surface-alt/80 transition-colors duration-100 border-b border-r border-border px-2.5 py-2 text-[11px] font-mono text-text-secondary text-right tabular-nums align-top"
                >
                  <button
                    type="button"
                    onClick={() => onRowNumClick(row, columns, rowIdx)}
                    aria-label={`View full row ${rowIdx}`}
                    title="Click to view full row"
                    className="w-full h-full text-right hover:text-[var(--c-primary)] hover:underline cursor-pointer"
                  >
                    {rowIdx}
                  </button>
                </td>
                {columns.map((col) => {
                  const value = row[col];
                  const cellKey = `${rowIdx}:${col}`;
                  const isExpanded = expanded.has(cellKey);
                  return (
                    <td
                      key={col}
                      className="border-b border-r last:border-r-0 border-border px-3 py-2 align-top text-text-primary"
                      style={{ overflow: "hidden" }}
                    >
                      <Cell
                        value={value}
                        col={col}
                        query={query}
                        expanded={isExpanded}
                        onToggle={() => onToggleCell(cellKey)}
                        onCellClick={() =>
                          onCellClick(col, value, rowIdx)
                        }
                        onImageClick={onImageClick}
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

/** Draggable column resize handle (#1). */
function ColumnResizeHandle({
  onMouseDown,
  tableName,
  colName,
}: {
  onMouseDown: (e: ReactMouseEvent) => void;
  tableName: string;
  colName: string;
}) {
  // Visually hidden tooltip explaining the handle. The actual interaction is
  // captured via onMouseDown so a click doesn't accidentally trigger header
  // sorting (none exists, but it's safer this way).
  const title = `Drag to resize ${tableName} → ${colName}`;
  return (
    <div
      role="separator"
      aria-orientation="vertical"
      aria-label={title}
      title={title}
      onMouseDown={onMouseDown}
      onDoubleClick={(e) => {
        // Optional convenience: double-click resets to default — handled by
        // the parent state. We don't implement reset here (would need an
        // onReset prop), so just stop propagation.
        e.stopPropagation();
      }}
      className="absolute top-0 right-0 h-full w-[7px] cursor-col-resize select-none z-30 group/rs"
      style={{ marginRight: "-3.5px" }}
    >
      {/* The visible bar appears on hover, grows wider near the cursor. */}
      <span
        className="absolute inset-y-0 right-[3px] w-[2px] bg-transparent group-hover/rs:bg-[var(--c-primary)] transition-colors duration-150"
        aria-hidden="true"
      />
      {/* Wider invisible hit area to make grabbing easier. */}
      <span
        className="absolute inset-y-0 right-0 w-[7px]"
        aria-hidden="true"
      />
    </div>
  );
}

/**
 * A single cell — handles nulls, image previews, truncation + highlighting.
 * Clicking opens the cell popup (#6); clicking an image opens the image
 * overlay (#4).
 */
function Cell({
  value,
  col,
  query,
  expanded,
  onToggle,
  onCellClick,
  onImageClick,
}: {
  value: unknown;
  col: string;
  query: string;
  expanded: boolean;
  onToggle: () => void;
  onCellClick: () => void;
  onImageClick: (src: string) => void;
}) {
  if (isNullish(value)) {
    return (
      <button
        type="button"
        onClick={onCellClick}
        title="Click to view cell value"
        className="inline-flex items-center px-1.5 py-0.5 rounded-[5px] bg-bg-chip border border-border text-[10.5px] font-mono text-text-secondary hover:border-[var(--c-primary)] hover:text-text-primary transition-colors cursor-pointer"
      >
        null
      </button>
    );
  }

  const text = stringifyCell(value);
  const shape = imageShapeForColumn(col);
  const isImg = shape !== null && looksLikeUrl(value);

  // Long-text threshold: show "click to expand" affordance when truncated.
  const isLong = text.length > MAX_CELL_PREVIEW;

  return (
    <div
      className={`flex items-start gap-2 min-w-0 overflow-hidden`}
      onClick={onCellClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onCellClick();
        }
      }}
      title="Click to view full value"
      style={{ cursor: "pointer" }}
    >
      {isImg && (
        <CellImage
          src={value}
          shape={shape}
          onClick={() => onImageClick(value)}
        />
      )}

      <div className={`min-w-0 flex-1 ${expanded ? "whitespace-pre-wrap break-words" : "truncate"}`}>
        {query ? highlightMatch(text, query) : text}
      </div>

      {isLong && !expanded && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onToggle();
          }}
          className="shrink-0 text-[10px] font-medium uppercase tracking-widest text-[var(--c-primary)] hover:underline"
          aria-label="Expand cell inline"
        >
          more
        </button>
      )}
      {isLong && expanded && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onToggle();
          }}
          className="shrink-0 text-[10px] font-medium uppercase tracking-widest text-text-secondary hover:text-text-primary hover:underline"
          aria-label="Collapse cell inline"
        >
          less
        </button>
      )}
    </div>
  );
}

/** Image preview cell — falls back to a placeholder on error. Clickable (#4). */
function CellImage({
  src,
  shape,
  onClick,
}: {
  src: string;
  shape: "portrait" | "square";
  onClick: () => void;
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
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation();
        onClick();
      }}
      aria-label="Open image in fullscreen"
      title="Click to open image"
      className={`${sizeClass} shrink-0 rounded-[6px] border border-border bg-bg-chip overflow-hidden relative group/img`}
    >
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={src}
        alt=""
        loading="lazy"
        onError={() => setErrored(true)}
        className="w-full h-full object-cover transition-transform duration-200 group-hover/img:scale-[1.04]"
      />
      <span
        className="absolute inset-0 bg-black/0 group-hover/img:bg-black/20 transition-colors flex items-center justify-center"
        aria-hidden="true"
      >
        <ExpandIcon className="w-3.5 h-3.5 text-white opacity-0 group-hover/img:opacity-100 transition-opacity" />
      </span>
    </button>
  );
}

/* ----------- #4 — Image fullscreen overlay ----------- */

function ImageFullscreenOverlay({
  src,
  alt,
  onClose,
}: {
  src: string;
  alt: string;
  onClose: () => void;
}) {
  // Trigger fade-in on mount.
  const [visible, setVisible] = useState(false);
  useLayoutEffect(() => {
    // Defer to next frame so the transition kicks in.
    const raf = requestAnimationFrame(() => setVisible(true));
    return () => cancelAnimationFrame(raf);
  }, []);

  const handleClose = useCallback(() => {
    setVisible(false);
    // Wait for fade-out before unmounting.
    setTimeout(onClose, 200);
  }, [onClose]);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Image fullscreen viewer"
      onClick={handleClose}
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black"
      style={{
        opacity: visible ? 1 : 0,
        transition: "opacity 200ms ease-out",
      }}
    >
      {/* Close button */}
      <button
        type="button"
        onClick={handleClose}
        aria-label="Close image viewer"
        title="Close (Esc)"
        className="absolute top-4 right-4 w-10 h-10 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center justify-center transition-colors z-10"
      >
        <CloseIcon className="w-5 h-5" />
      </button>

      {/* Image — object-contain in a flex container so it touches all edges. */}
      {/* Tall images touch top+bottom; wide images touch left+right. */}
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={src}
        alt={alt}
        onClick={(e) => {
          e.stopPropagation();
          handleClose();
        }}
        className="w-full h-full object-contain select-none"
        draggable={false}
        style={{
          opacity: visible ? 1 : 0,
          transform: visible ? "scale(1)" : "scale(0.97)",
          transition:
            "opacity 250ms ease-out 50ms, transform 250ms ease-out 50ms",
        }}
      />

      <div className="absolute bottom-4 left-1/2 -translate-x-1/2 text-white/60 text-[12px] pointer-events-none">
        Click anywhere or press Esc to close
      </div>
    </div>
  );
}

/* ----------- #6 — Cell click popup ----------- */

function CellPopupModal({
  column,
  value,
  rowIndex,
  onClose,
  onImageClick,
}: {
  column: string;
  value: unknown;
  rowIndex: number;
  onClose: () => void;
  onImageClick: (src: string) => void;
}) {
  const [visible, setVisible] = useState(false);
  useLayoutEffect(() => {
    const raf = requestAnimationFrame(() => setVisible(true));
    return () => cancelAnimationFrame(raf);
  }, []);

  const handleClose = useCallback(() => {
    setVisible(false);
    setTimeout(onClose, 180);
  }, [onClose]);

  const text = stringifyCell(value);
  const isUrl = looksLikeUrl(value);
  const isImage = imageShapeForColumn(column) !== null && isUrl;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={`Cell value: ${column}`}
      onClick={handleClose}
      className="fixed inset-0 z-[90] flex items-center justify-center p-4"
      style={{
        backgroundColor: "rgba(0, 0, 0, 0.6)",
        opacity: visible ? 1 : 0,
        transition: "opacity 180ms ease-out",
        backdropFilter: "blur(2px)",
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        role="document"
        className="w-full max-w-2xl max-h-[80vh] flex flex-col rounded-[16px] bg-surface border border-border shadow-hover overflow-hidden"
        style={{
          transform: visible ? "translateY(0) scale(1)" : "translateY(8px) scale(0.98)",
          transition: "transform 200ms ease-out, opacity 180ms ease-out",
          opacity: visible ? 1 : 0,
        }}
      >
        {/* Header */}
        <div className="flex items-start justify-between gap-3 px-5 py-4 border-b border-border">
          <div className="min-w-0">
            <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Cell · row #{rowIndex}
            </div>
            <h3 className="text-[15px] font-bold tracking-tight text-text-primary font-mono truncate">
              {column}
            </h3>
          </div>
          <button
            type="button"
            onClick={handleClose}
            aria-label="Close popup"
            className="shrink-0 w-8 h-8 rounded-md text-text-secondary hover:text-text-primary hover:bg-canvas flex items-center justify-center transition-colors"
          >
            <CloseIcon className="w-4 h-4" />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-5 py-4">
          {isNullish(value) ? (
            <div className="inline-flex items-center px-2 py-1 rounded-[6px] bg-bg-chip border border-border text-[12px] font-mono text-text-secondary">
              null
            </div>
          ) : isImage ? (
            <div className="flex flex-col items-start gap-3">
              <button
                type="button"
                onClick={() => onImageClick(value as string)}
                className="block max-w-full max-h-[60vh] rounded-[10px] overflow-hidden border border-border hover:opacity-90 transition-opacity"
              >
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={value as string}
                  alt={column}
                  className="max-w-full max-h-[60vh] object-contain bg-black/5"
                />
              </button>
              <a
                href={value as string}
                target="_blank"
                rel="noopener noreferrer"
                className="text-[12.5px] text-[var(--c-primary)] hover:underline break-all"
              >
                {value as string}
              </a>
            </div>
          ) : isUrl ? (
            <a
              href={value as string}
              target="_blank"
              rel="noopener noreferrer"
              className="text-[13px] text-[var(--c-primary)] hover:underline break-all"
            >
              {value as string}
            </a>
          ) : (
            <pre
              className="text-[12.5px] text-text-primary font-mono leading-relaxed"
              style={{
                whiteSpace: "pre-wrap",
                wordBreak: "break-word",
                margin: 0,
                fontFamily: "inherit",
              }}
            >
              {text}
            </pre>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between gap-3 px-5 py-3 border-t border-border bg-surface-alt/50">
          <span className="text-[11px] text-text-secondary">
            {text.length.toLocaleString()} chars
            {text.includes("\n") && " · multi-line"}
          </span>
          <button
            type="button"
            onClick={handleClose}
            className="h-8 px-3 rounded-[8px] text-[12.5px] font-medium bg-bg-chip text-text-secondary hover:text-text-primary border border-border transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

/* ----------- #7 — Row popup (two-column layout) ----------- */

function RowPopupModal({
  row,
  columns,
  rowIndex,
  onClose,
  onImageClick,
}: {
  row: Row;
  columns: string[];
  rowIndex: number;
  onClose: () => void;
  onImageClick: (src: string) => void;
}) {
  const [visible, setVisible] = useState(false);
  useLayoutEffect(() => {
    const raf = requestAnimationFrame(() => setVisible(true));
    return () => cancelAnimationFrame(raf);
  }, []);

  const handleClose = useCallback(() => {
    setVisible(false);
    setTimeout(onClose, 180);
  }, [onClose]);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={`Row #${rowIndex} details`}
      onClick={handleClose}
      className="fixed inset-0 z-[90] flex items-center justify-center p-4"
      style={{
        backgroundColor: "rgba(0, 0, 0, 0.6)",
        opacity: visible ? 1 : 0,
        transition: "opacity 180ms ease-out",
        backdropFilter: "blur(2px)",
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        role="document"
        className="w-full max-w-3xl max-h-[85vh] flex flex-col rounded-[16px] bg-surface border border-border shadow-hover overflow-hidden"
        style={{
          transform: visible ? "translateY(0) scale(1)" : "translateY(8px) scale(0.98)",
          transition: "transform 200ms ease-out, opacity 180ms ease-out",
          opacity: visible ? 1 : 0,
        }}
      >
        {/* Header */}
        <div className="flex items-start justify-between gap-3 px-5 py-4 border-b border-border">
          <div className="min-w-0">
            <div className="text-[10.5px] font-medium uppercase tracking-widest text-text-secondary mb-1">
              Row details
            </div>
            <h3 className="text-[15px] font-bold tracking-tight text-text-primary">
              Row <span className="font-mono">#{rowIndex}</span>{" "}
              <span className="text-text-secondary font-medium">
                · {columns.length} columns
              </span>
            </h3>
          </div>
          <button
            type="button"
            onClick={handleClose}
            aria-label="Close popup"
            className="shrink-0 w-8 h-8 rounded-md text-text-secondary hover:text-text-primary hover:bg-canvas flex items-center justify-center transition-colors"
          >
            <CloseIcon className="w-4 h-4" />
          </button>
        </div>

        {/* Body — two-column key/value layout */}
        <div className="flex-1 overflow-y-auto p-2">
          <div className="grid grid-cols-[minmax(120px,200px)_1fr] gap-px bg-border rounded-[8px] overflow-hidden">
            {columns.map((col) => {
              const value = row[col];
              const shape = imageShapeForColumn(col);
              const isUrl = looksLikeUrl(value);
              const isImage = shape !== null && isUrl;
              const text = stringifyCell(value);
              return (
                <FragmentRow key={col}>
                  {/* Column name (left) */}
                  <div className="bg-surface-alt px-3 py-2 text-[11px] font-mono uppercase tracking-wider text-text-secondary break-all">
                    {col}
                  </div>
                  {/* Value (right) */}
                  <div className="bg-surface px-3 py-2 text-[12.5px] text-text-primary">
                    {isNullish(value) ? (
                      <span className="inline-flex items-center px-1.5 py-0.5 rounded-[5px] bg-bg-chip border border-border text-[10.5px] font-mono text-text-secondary">
                        null
                      </span>
                    ) : isImage ? (
                      <div className="flex flex-col items-start gap-2">
                        <button
                          type="button"
                          onClick={() => onImageClick(value as string)}
                          className="block max-h-[160px] rounded-[8px] overflow-hidden border border-border hover:opacity-90 transition-opacity"
                        >
                          {/* eslint-disable-next-line @next/next/no-img-element */}
                          <img
                            src={value as string}
                            alt={col}
                            className="max-h-[160px] w-auto object-contain"
                          />
                        </button>
                        <a
                          href={value as string}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-[11.5px] text-[var(--c-primary)] hover:underline break-all"
                        >
                          {value as string}
                        </a>
                      </div>
                    ) : isUrl ? (
                      <a
                        href={value as string}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-[var(--c-primary)] hover:underline break-all"
                      >
                        {value as string}
                      </a>
                    ) : (
                      <div
                        style={{
                          whiteSpace: "pre-wrap",
                          wordBreak: "break-word",
                        }}
                      >
                        {text}
                      </div>
                    )}
                  </div>
                </FragmentRow>
              );
            })}
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between gap-3 px-5 py-3 border-t border-border bg-surface-alt/50">
          <span className="text-[11px] text-text-secondary">
            {columns.length} fields
          </span>
          <button
            type="button"
            onClick={handleClose}
            className="h-8 px-3 rounded-[8px] text-[12.5px] font-medium bg-bg-chip text-text-secondary hover:text-text-primary border border-border transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

/** Tiny helper — React fragments can't take className, so we just render two children. */
function FragmentRow({ children }: { children: ReactNode }) {
  return <>{children}</>;
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

function ChevronLeftIcon({ className = "" }: { className?: string }) {
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
      <path d="M15 6l-6 6 6 6" />
    </svg>
  );
}

function ChevronRightIcon({ className = "" }: { className?: string }) {
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
      <path d="M9 6l6 6-6 6" />
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

function FullscreenIcon({ className = "" }: { className?: string }) {
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
      <path d="M4 9V5a1 1 0 0 1 1-1h4" />
      <path d="M20 9V5a1 1 0 0 0-1-1h-4" />
      <path d="M4 15v4a1 1 0 0 0 1 1h4" />
      <path d="M20 15v4a1 1 0 0 1-1 1h-4" />
    </svg>
  );
}

function ExitFullscreenIcon({ className = "" }: { className?: string }) {
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
      <path d="M9 4v3a2 2 0 0 1-2 2H4" />
      <path d="M15 4v3a2 2 0 0 0 2 2h3" />
      <path d="M9 20v-3a2 2 0 0 0-2-2H4" />
      <path d="M15 20v-3a2 2 0 0 1 2-2h3" />
    </svg>
  );
}

function ExpandIcon({ className = "" }: { className?: string }) {
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
      <path d="M15 3h6v6" />
      <path d="M9 21H3v-6" />
      <path d="M21 3l-7 7" />
      <path d="M3 21l7-7" />
    </svg>
  );
}
