package com.confused.anikuta.core.testcontroller

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.confused.anikuta.core.testapi.NodeBounds
import com.confused.anikuta.core.testapi.NodeInfo
import java.util.concurrent.atomic.AtomicInteger

/**
 * Serializes the accessibility tree to [NodeInfo] (D-199).
 *
 * Assigns short-lived integer [nodeId]s during each [serialize] call and stores the
 * `nodeId → AccessibilityNodeInfo` mapping in [currentMap]. The [GestureExecutor] reads
 * [lookup] to resolve a nodeId back to a node for `performAction`. The map is REPLACED
 * (not merged) on every [serialize] call — nodeIds from a prior snapshot are invalid.
 *
 * Self-filters to our own package (D-199 gotcha): nodes whose `packageName != "com.confused.anikuta"`
 * are pruned from the tree (their children too). This prevents the agent from inspecting
 * system dialogs or other apps overlaying ours.
 *
 * Recycles nodes after serialization on API 24-32 (pre-API 33 pool leak — D-199). On API 33+
 * `recycle()` is a no-op. We hold the nodeId→node map WITHOUT recycling those nodes (so the
 * gesture executor can still act on them); they're recycled on the NEXT [serialize] call.
 *
 * Thread-safety: [serialize] + [lookup] + [currentMap] are all main-thread-affine (the
 * accessibility tree must be read on the main thread per Android docs). The executor dispatches
 * all commands to `Dispatchers.Main` before calling this.
 */
class AccessibilityTreeSerializer(
    private val targetPackage: String,
) {

    @Volatile
    private var map: Map<Int, AccessibilityNodeInfo> = emptyMap()

    /** The nodeId→node map from the last [serialize] call. Read by [GestureExecutor]. */
    val currentMap: Map<Int, AccessibilityNodeInfo> get() = map

    /** Counter for the next nodeId. Reset on each [serialize]. */
    private val counter = AtomicInteger(0)

    /**
     * Serialize the tree rooted at [root]. Returns the root [NodeInfo] (with children).
     * Replaces [currentMap] with a fresh mapping. If [root] is null or belongs to another
     * package, returns a synthetic empty root (nodeId 0, no children).
     */
    fun serialize(root: AccessibilityNodeInfo?): NodeInfo {
        // Recycle the previous map's nodes (pre-API 33 leak prevention).
        val previous = map
        previous.values.forEach { runCatching { it.recycle() } }
        counter.set(0)
        val freshMap = HashMap<Int, AccessibilityNodeInfo>()
        val result = if (root != null && root.packageName?.toString() == targetPackage) {
            serializeNode(root, freshMap)
        } else {
            // No window, or a different-package window overlaying ours. Return a synthetic root
            // so the agent gets a stable structure (rather than null).
            NodeInfo(
                nodeId = 0,
                bounds = NodeBounds(0, 0, 0, 0),
                packageName = root?.packageName?.toString(),
                children = emptyList(),
                isVisibleToUser = false,
            )
        }
        map = freshMap
        return result
    }

    /** Look up a node by its [nodeId] from the last snapshot. Returns null if stale. */
    fun lookup(nodeId: Int): AccessibilityNodeInfo? = map[nodeId]

    private fun serializeNode(node: AccessibilityNodeInfo, into: HashMap<Int, AccessibilityNodeInfo>): NodeInfo {
        val id = counter.getAndIncrement()
        // Don't recycle `node` here — we store it in the map for the gesture executor.
        // It'll be recycled on the next serialize() call (see above).
        into[id] = node
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val actions = node.actionList?.map { it.id.toString() } ?: emptyList()
        val children = (0 until node.childCount).mapNotNull { i ->
            val child = runCatching { node.getChild(i) }.getOrNull()
            if (child != null && child.packageName?.toString() == targetPackage) serializeNode(child, into)
            else { runCatching { child?.recycle() } ; null }
        }
        return NodeInfo(
            nodeId = id,
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            className = node.className?.toString(),
            packageName = node.packageName?.toString(),
            viewIdResourceName = node.viewIdResourceName,
            bounds = NodeBounds(rect.left, rect.top, rect.right, rect.bottom),
            isClickable = node.isClickable,
            isScrollable = node.isScrollable,
            isCheckable = node.isCheckable,
            isChecked = node.isChecked,
            isEnabled = node.isEnabled,
            isVisibleToUser = node.isVisibleToUser,
            actions = actions,
            children = children,
        )
    }

    /** Find nodes by text / resourceId / className query (D-199 by-attribute addressing). */
    fun findNodes(
        root: AccessibilityNodeInfo?,
        text: String?,
        resourceId: String?,
        className: String?,
        limit: Int,
    ): List<NodeInfo> {
        if (root == null || root.packageName?.toString() != targetPackage) return emptyList()
        // Recycle the previous map first (we're about to rebuild it).
        map.values.forEach { runCatching { it.recycle() } }
        counter.set(0)
        val freshMap = HashMap<Int, AccessibilityNodeInfo>()
        val results = mutableListOf<NodeInfo>()
        findNodesRecursive(root, freshMap, text, resourceId, className, limit, results)
        map = freshMap
        return results
    }

    private fun findNodesRecursive(
        node: AccessibilityNodeInfo,
        into: HashMap<Int, AccessibilityNodeInfo>,
        text: String?,
        resourceId: String?,
        className: String?,
        limit: Int,
        results: MutableList<NodeInfo>,
    ) {
        if (results.size >= limit) return
        // Pre-register the node so its nodeId is stable if the agent acts on it.
        val id = counter.getAndIncrement()
        into[id] = node
        val matches = buildList {
            text?.let { t -> node.text?.toString()?.contains(t, ignoreCase = true)?.let { if (it) add(true) } }
            text?.let { t -> node.contentDescription?.toString()?.contains(t, ignoreCase = true)?.let { if (it) add(true) } }
            resourceId?.let { r -> node.viewIdResourceName?.let { if (it == r || it.endsWith(":id/$r")) add(true) } }
            className?.let { c -> node.className?.toString()?.let { if (it.contains(c, ignoreCase = true)) add(true) } }
        }
        if (matches.isNotEmpty()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            results.add(
                NodeInfo(
                    nodeId = id,
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    className = node.className?.toString(),
                    packageName = node.packageName?.toString(),
                    viewIdResourceName = node.viewIdResourceName,
                    bounds = NodeBounds(rect.left, rect.top, rect.right, rect.bottom),
                    isClickable = node.isClickable,
                    isScrollable = node.isScrollable,
                    isCheckable = node.isCheckable,
                    isChecked = node.isChecked,
                    isEnabled = node.isEnabled,
                    isVisibleToUser = node.isVisibleToUser,
                    actions = node.actionList?.map { it.id.toString() } ?: emptyList(),
                    children = emptyList(), // Don't recurse into matches (keeps payload small).
                )
            )
        }
        // Recurse regardless of match (matches may have interactive children).
        for (i in 0 until node.childCount) {
            if (results.size >= limit) return
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            if (child.packageName?.toString() == targetPackage) {
                findNodesRecursive(child, into, text, resourceId, className, limit, results)
            } else {
                runCatching { child.recycle() }
            }
        }
    }
}
