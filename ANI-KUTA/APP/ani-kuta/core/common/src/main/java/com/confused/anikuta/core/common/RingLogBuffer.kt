package com.confused.anikuta.core.common

/**
 * Task 49 (round 9 — the console logging tool): a RELEASE-AVAILABLE in-memory
 * ring buffer capturing every [Logger] entry app-wide.
 *
 * Why a second buffer when `:feature:debug-bubble` already ships DebugLogBuffer:
 * that module is `debugImplementation`-only — the user-facing console
 * (Settings → Developer tools → Console logs) must exist in EVERY build so a
 * release-APK user can still capture, filter and EXPORT logs. This buffer
 * lives in `:core:common` (always on the classpath) and is registered from
 * [com.confused.anikuta.AnikutaApp] in all builds; debug builds additionally
 * attach the bubble's DebugLogBuffer via a composite appender (DebugInit).
 *
 * Rules (learned from the debug bubble's implementation):
 *  • thread-safe (Logger is called from network/MPV/DB threads);
 *  • O(1) append with eviction at [CAPACITY];
 *  • NEVER logs itself (recursion → StackOverflow);
 *  • entry strings are capped so a hostile message can't blow the heap
 *    (10k × ~≤6KB worst case ≈ bounded tens of MB, typical far below).
 */
object RingLogBuffer : LogAppender {

    /** Ring capacity — ~the last 10k entries, mirroring the debug bubble. */
    const val CAPACITY = 10_000

    private const val MAX_MESSAGE_CHARS = 4_000
    private const val MAX_THROWABLE_CHARS = 2_000

    data class Entry(
        val timestampMillis: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwableString: String?,
    )

    private val lock = Any()
    private val ring = ArrayDeque<Entry>(CAPACITY)

    override fun append(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        // Keep the hot path allocation-light; runCatching guards against any
        // pathological toString() so logging can never take the app down.
        runCatching {
            val entry = Entry(
                timestampMillis = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = if (message.length > MAX_MESSAGE_CHARS) message.take(MAX_MESSAGE_CHARS) else message,
                throwableString = throwable?.let { t ->
                    val text = "${t::class.java.name}: ${t.message}"
                    if (text.length > MAX_THROWABLE_CHARS) text.take(MAX_THROWABLE_CHARS) else text
                },
            )
            synchronized(lock) {
                if (ring.size >= CAPACITY) {
                    ring.removeFirst()
                }
                ring.addLast(entry)
            }
        }
    }

    /** Current entries, oldest first. Cheap copy for UI snapshots. */
    fun snapshot(): List<Entry> = synchronized(lock) { ring.toList() }

    /** Number of buffered entries. */
    fun size(): Int = synchronized(lock) { ring.size }

    /** Clears the buffer (the console's Clear button). */
    fun clear() = synchronized(lock) { ring.clear() }
}
