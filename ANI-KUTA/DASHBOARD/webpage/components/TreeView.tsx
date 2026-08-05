"use client";

import { Fragment, useState } from "react";
import type { TreeNode, ModuleInfo } from "@/lib/data";

/**
 * TreeView — file-tree component (DESIGN.md §5.8).
 * Mono font, tree characters (├─ │  └─), color-coded labels.
 *
 * The component renders nested rows. Each child row gets a prefix
 * built from tree glyphs based on whether it's the last sibling.
 *
 * `nodes` are the top-level entries (usually just `:app`).
 * `modules` is the flat module list — leaves are resolved by full path
 * (e.g. ":core:ui") to find the matching ModuleInfo.
 */
export function TreeView({
  nodes,
  modules,
}: {
  nodes: TreeNode[];
  modules: ModuleInfo[];
}) {
  const lookup = (path: string): ModuleInfo | undefined =>
    modules.find((m) => m.name === path);

  return (
    <div className="font-mono text-[12.5px] leading-[1.7] text-text-primary select-none">
      {nodes.map((node, i) => (
        <TreeBranch
          key={node.label + i}
          node={node}
          prefix=""
          isLast={i === nodes.length - 1}
          path={node.label}
          moduleLookup={lookup}
        />
      ))}
    </div>
  );
}

function TreeBranch({
  node,
  prefix,
  isLast,
  path,
  moduleLookup,
}: {
  node: TreeNode;
  prefix: string;
  isLast: boolean;
  path: string;
  moduleLookup: (path: string) => ModuleInfo | undefined;
}) {
  const glyph = isLast ? "└─ " : "├─ ";
  const childPrefix = prefix + (isLast ? "   " : "│  ");
  const hasChildren = !!node.children?.length;

  // Build full path for module lookup (e.g. :core:ui).
  // node.label may be ":app", ":core", ":feature", or a leaf name like "ui".
  const fullPath = path;
  const module = moduleLookup(fullPath);
  const isLeaf = !hasChildren;
  const color = layerColor(node.layer);

  const [selected, setSelected] = useState(false);

  // For container nodes (`:app`, `:core`, `:feature`) render as folder.
  // For leaf nodes (`ui`, `design`, ...) render as module.
  return (
    <div>
      <div
        className={`flex items-start gap-2 cursor-pointer rounded-md transition-colors duration-150 ${
          selected ? "bg-bg-chip" : "hover:bg-bg-chip/60"
        }`}
        onClick={() => {
          if (!hasChildren) setSelected((s) => !s);
        }}
        role={isLeaf ? "button" : undefined}
        tabIndex={isLeaf ? 0 : undefined}
        onKeyDown={(e) => {
          if (isLeaf && (e.key === "Enter" || e.key === " ")) {
            e.preventDefault();
            setSelected((s) => !s);
          }
        }}
      >
        <span className="text-text-secondary whitespace-pre">{prefix}{glyph}</span>
        <span style={{ color }} className="font-medium">
          {node.label}
        </span>
        {module && (
          <span className="text-text-secondary text-[11.5px] truncate">
            {"  — "}{module.job}
          </span>
        )}
      </div>

      {/* Expanded detail for leaf modules */}
      {isLeaf && selected && module && (
        <div className="ml-10 pl-4 mt-1 mb-2 p-3 rounded-[12px] border border-border bg-bg-card text-[12px] text-text-secondary leading-relaxed">
          <div className="font-mono text-text-primary text-[12.5px] mb-1">
            {fullPath}
          </div>
          <div className="mb-2">{module.job}</div>
          <div>
            <span className="font-medium text-text-primary">Depends on: </span>
            {module.dependsOn.length === 0 ? (
              <span className="text-text-secondary">none</span>
            ) : (
              <span className="font-mono text-text-primary">
                {module.dependsOn.join(", ")}
              </span>
            )}
          </div>
        </div>
      )}

      {hasChildren && (
        <div>
          {node.children!.map((child, i) => {
            // Build child path
            const childPath = buildPath(fullPath, child.label);
            return (
              <TreeBranch
                key={child.label + i}
                node={child}
                prefix={childPrefix}
                isLast={i === node.children!.length - 1}
                path={childPath}
                moduleLookup={moduleLookup}
              />
            );
          })}
        </div>
      )}
    </div>
  );
}

/**
 * Build a path string. Root labels already start with `:` (e.g. ":app", ":core").
 * Children append with `:` separator (`:core` + `ui` → `:core:ui`).
 */
function buildPath(parent: string, child: string): string {
  if (parent.startsWith(":")) {
    return `${parent}:${child}`;
  }
  // Fallback — shouldn't happen for this dataset.
  return `${parent}/${child}`;
}

function layerColor(layer: TreeNode["layer"]): string {
  switch (layer) {
    case "app":
      return "var(--c-primary)";
    case "core":
      return "var(--c-secondary)";
    case "feature":
      return "var(--c-success)";
    default:
      // Container folder (:core, :feature)
      return "var(--c-text-primary)";
  }
}

/**
 * TreeViewStatic — non-interactive variant for read-only display.
 * Renders the same tree glyphs without click handlers or detail expansion.
 */
export function TreeViewStatic({
  nodes,
}: {
  nodes: TreeNode[];
}) {
  return (
    <pre className="font-mono text-[12.5px] leading-[1.7] text-text-primary whitespace-pre overflow-x-auto">
      {nodes.map((node, i) => (
        <Fragment key={node.label + i}>
          {renderStatic(node, "", i === nodes.length - 1)}
        </Fragment>
      ))}
    </pre>
  );
}

function renderStatic(node: TreeNode, prefix: string, isLast: boolean): string {
  const glyph = isLast ? "└─ " : "├─ ";
  const childPrefix = prefix + (isLast ? "   " : "│  ");
  let out = `${prefix}${glyph}${node.label}\n`;
  if (node.children?.length) {
    node.children.forEach((child, i) => {
      out += renderStatic(child, childPrefix, i === node.children!.length - 1);
    });
  }
  return out;
}
