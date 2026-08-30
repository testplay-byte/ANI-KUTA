// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
package com.lagradost.cloudstream3.utils

/**
 * Minimal monitor primitive for the atomic collections (our own implementation —
 * the upstream one came from a dependency we do not ship).
 */
open class SynchronizedObject {
    private val lock = Any()

    fun <R> synchronized(block: () -> R): R = kotlin.synchronized(lock, block)
}

/**
 * A thread-safe list. For iteration, wrap the block in [withLock] to hold the lock
 * for the whole iteration.
 */
open class AtomicList<T>(
    private val delegate: List<T> = emptyList(),
) : List<T>, SynchronizedObject() {

    fun <R> withLock(block: () -> R): R = synchronized(block)

    fun filter(predicate: (T) -> Boolean): AtomicList<T> =
        AtomicList(synchronized { delegate.filter(predicate) })

    fun distinctBy(selector: (T) -> Any?): AtomicList<T> =
        AtomicList(synchronized { delegate.distinctBy(selector) })

    override val size: Int get() = synchronized { delegate.size }
    override fun isEmpty(): Boolean = synchronized { delegate.isEmpty() }
    override fun contains(element: T): Boolean = synchronized { delegate.contains(element) }
    override fun containsAll(elements: Collection<T>): Boolean = synchronized { delegate.containsAll(elements) }
    override fun get(index: Int): T = synchronized { delegate[index] }
    override fun indexOf(element: T): Int = synchronized { delegate.indexOf(element) }
    override fun lastIndexOf(element: T): Int = synchronized { delegate.lastIndexOf(element) }

    // Iterators intentionally NOT synchronized — callers must use withLock { } for safe iteration.
    override fun iterator(): Iterator<T> = synchronized { delegate.iterator() }
    override fun listIterator(): ListIterator<T> = synchronized { delegate.listIterator() }
    override fun listIterator(index: Int): ListIterator<T> = synchronized { delegate.listIterator(index) }
    override fun subList(fromIndex: Int, toIndex: Int): List<T> = synchronized { delegate.subList(fromIndex, toIndex) }

    operator fun plus(element: T): AtomicList<T> = AtomicList(synchronized { delegate + element })
    operator fun plus(elements: Collection<T>): AtomicList<T> = AtomicList(synchronized { delegate + elements })
}

class AtomicMutableList<T>(
    private val mutableDelegate: MutableList<T> = mutableListOf(),
) : AtomicList<T>(mutableDelegate), MutableList<T> {

    override fun add(element: T): Boolean = synchronized { mutableDelegate.add(element) }
    override fun add(index: Int, element: T) = synchronized { mutableDelegate.add(index, element) }
    override fun addAll(elements: Collection<T>): Boolean = synchronized { mutableDelegate.addAll(elements) }
    override fun addAll(index: Int, elements: Collection<T>): Boolean = synchronized { mutableDelegate.addAll(index, elements) }
    override fun remove(element: T): Boolean = synchronized { mutableDelegate.remove(element) }
    override fun removeAt(index: Int): T = synchronized { mutableDelegate.removeAt(index) }
    override fun removeAll(elements: Collection<T>): Boolean = synchronized { mutableDelegate.removeAll(elements) }
    override fun retainAll(elements: Collection<T>): Boolean = synchronized { mutableDelegate.retainAll(elements) }
    override fun set(index: Int, element: T): T = synchronized { mutableDelegate.set(index, element) }
    override fun clear() = synchronized { mutableDelegate.clear() }

    // Iterators intentionally NOT synchronized — callers must use withLock { } for safe iteration.
    override fun iterator(): MutableIterator<T> = synchronized { mutableDelegate.iterator() }
    override fun listIterator(): MutableListIterator<T> = synchronized { mutableDelegate.listIterator() }
    override fun listIterator(index: Int): MutableListIterator<T> = synchronized { mutableDelegate.listIterator(index) }
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> = synchronized { mutableDelegate.subList(fromIndex, toIndex) }
}
