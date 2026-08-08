package com.confused.anikuta.feature.debugbubble

/**
 * The debug panel's tabs (Phase DB).
 *
 * The 5 default tabs. Tabs are flexible (D-163) — a screen's DebugContext can
 * declare additional screen-specific tabs or hide irrelevant ones in a future
 * phase. For DB-2, only [SCREEN] is implemented; the others are placeholders
 * that show "Coming in DB-X" until their phase.
 */
enum class DebugTab(val label: String, val icon: String) {
    SCREEN("Screen", "📱"),
    DATABASE("Database", "🗄️"),
    CONSOLE("Console", "📜"),
    NETWORK("Network", "🌐"),
    APP_INFO("App Info", "ℹ️"),
}

/**
 * The direction the panel expands from the bubble (D-163).
 *
 * Determined by the bubble's position: the panel opens toward the side with
 * more space AND extends vertically away from the nearest edge — so it's always
 * fully visible. See [expandDirectionFor].
 */
enum class ExpandDirection {
    /** Bubble in the top-left → panel opens right + extends down. */
    RIGHT_DOWN,
    /** Bubble in the top-right → panel opens left + extends down. */
    LEFT_DOWN,
    /** Bubble in the bottom-left → panel opens right + extends up. */
    RIGHT_UP,
    /** Bubble in the bottom-right → panel opens left + extends up. */
    LEFT_UP,
}

/**
 * Compute the expand direction from the bubble's position relative to screen center.
 *
 * - Horizontal: bubbleX < screenWidth/2 → RIGHT; else LEFT.
 * - Vertical: bubbleY < screenHeight/2 → DOWN (extends downward); else UP.
 */
fun expandDirectionFor(
    bubbleX: Float,
    bubbleY: Float,
    screenWidth: Float,
    screenHeight: Float,
): ExpandDirection {
    val right = bubbleX < screenWidth / 2f
    val down = bubbleY < screenHeight / 2f
    return when {
        right && down -> ExpandDirection.RIGHT_DOWN
        !right && down -> ExpandDirection.LEFT_DOWN
        right && !down -> ExpandDirection.RIGHT_UP
        else -> ExpandDirection.LEFT_UP
    }
}
