package com.confused.anikuta

/**
 * D-759 (round 124 — the navigation reliability round): the ONE-SLOT request
 * mailbox with request IDs.
 *
 * ## The problem it solves
 *
 * The pending-navigation intake used to be a bare `MutableStateFlow<Request?>`
 * slot:
 *
 * ```
 * _pending.value = request      // a new delivery OVERWRITES the slot
 * ...
 * consume() { _pending.value = null }   // the handler clears THE SLOT
 * ```
 *
 * Two failures lived in that shape:
 *  - **The conflation.** A `StateFlow` only emits when the value CHANGES. Two
 *    back-to-back deliveries with the SAME payload (the user taps the same
 *    notification twice) compared EQUAL, so the second one never reached the
 *    composition — the tap was silently swallowed.
 *  - **The clear race.** A delivery that arrived WHILE the composition was
 *    handling the previous one overwrote the slot; the previous handler's
 *    `consume()` then cleared the NEW request — the new delivery was lost.
 *
 * ## The shape now
 *
 * Every offer gets a fresh, monotonically increasing ID:
 *  - two identical payloads are two DIFFERENT entries (the flow emits, because
 *    the values differ) → no swallowed tap;
 *  - `consume(id)` only clears the entry the handler actually handled → a new
 *    request can never be cleared by an old handler.
 *
 * Pure JVM (no Android imports): the identity contract is unit-tested.
 */
internal class PendingRequestMailbox<T> {

    /** One offered request: a payload plus the ID that names it. */
    data class Entry<T>(val id: Long, val payload: T)

    private var counter = 0L
    private var current: Entry<T>? = null

    /** The entry awaiting handling (null = nothing pending). */
    val pending: Entry<T>?
        get() = current

    /**
     * Offer a payload. ALWAYS produces a new entry with a new ID — even for a
     * payload equal to the pending one (that equality is exactly what used to
     * swallow a repeated delivery).
     */
    fun offer(payload: T): Entry<T> {
        val entry = Entry(id = counter++, payload = payload)
        current = entry
        return entry
    }

    /**
     * Clear the pending entry — but ONLY when it is the one the handler
     * actually handled. A newer request survives an older handler's consume.
     *
     * @return true when the pending entry was cleared.
     */
    fun consume(id: Long): Boolean {
        if (current?.id != id) return false
        current = null
        return true
    }
}
