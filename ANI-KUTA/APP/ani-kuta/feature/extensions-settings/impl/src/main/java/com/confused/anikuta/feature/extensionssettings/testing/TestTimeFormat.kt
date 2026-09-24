package com.confused.anikuta.feature.extensionssettings.testing

/**
 * Human duration formatting (round 83, D-578).
 *
 * WHY: the round-82 UI rendered raw milliseconds everywhere ("10234 ms") —
 * the user asked for seconds-first ("instead of 1,000 milliseconds it should
 * show it in seconds") and minutes for long runs. The ladder:
 *
 *   < 1 000 ms        → "820 ms"
 *   ≥ 1 000 ms (<60s) → "1.5 s" / "12 s"
 *   ≥ 60 000 ms       → "2m 05s"
 *
 * Single source of truth — the test result messages (Ping / Stream play) and
 * every UI label (test rows, summary, batch overlay) share this, so a format
 * tweak never requires touching more than this file.
 */
object TestTimeFormat {

    /** Formats a duration for compact UI labels and result messages. */
    fun format(ms: Long): String = when {
        ms >= 60_000L -> {
            val minutes = ms / 60_000L
            val seconds = (ms % 60_000L) / 1000.0
            "%dm %02.0fs".format(minutes, seconds)
        }
        ms >= 1_000L -> {
            val seconds = ms / 1000.0
            if (seconds == Math.floor(seconds)) "%d s".format(seconds.toLong())
            else "%.1f s".format(seconds)
        }
        else -> "$ms ms"
    }
}
