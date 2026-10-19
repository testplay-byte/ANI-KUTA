package com.confused.anikuta.core.download

/**
 * The set of tasks that were paused **by the network watcher**, not by the user.
 *
 * Why this exists (D-246 + D-747): the download service stays alive while this
 * set is non-empty so its `NetworkCallback` can resume the tasks when
 * connectivity returns. The distinction between "paused by us" and "paused by
 * the user" is the whole contract:
 *
 *  - [add] is called only by the network-loss / Wi-Fi-only paths.
 *  - [remove] is called by every user-initiated resume, retry, pause, or
 *    cancel. User intent wins; a stale marker would keep the service alive
 *    forever waiting on a task the user already restarted.
 *  - [snapshotAndClear] is the atomic hand-off used by the auto-resume path.
 *
 * Thread-safety: the queue mutates this set from its sync path and from
 * coroutines. A synchronized wrapper is deliberate — the set is tiny and the
 * operations are O(1), so a lock-free structure would add complexity without
 * removing contention.
 */
internal class NetworkPauseRegistry {

    private val ids: MutableSet<Long> = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    fun add(taskId: Long) {
        ids.add(taskId)
    }

    fun remove(taskId: Long) {
        ids.remove(taskId)
    }

    fun clear() {
        ids.clear()
    }

    fun isEmpty(): Boolean = ids.isEmpty()

    /** Atomically returns the current IDs and empties the registry. */
    fun snapshotAndClear(): List<Long> = synchronized(ids) {
        val snapshot = ids.toList()
        ids.clear()
        snapshot
    }
}
