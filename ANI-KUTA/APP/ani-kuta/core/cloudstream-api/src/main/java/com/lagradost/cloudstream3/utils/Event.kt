// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
package com.lagradost.cloudstream3.utils

/** Minimal observer-set event (app-side utility shape). */
class Event<T> {
    private val observers = mutableSetOf<(T) -> Unit>()

    val size: Int get() = observers.size

    operator fun plusAssign(observer: (T) -> Unit) {
        observers.add(observer)
    }

    operator fun minusAssign(observer: (T) -> Unit) {
        observers.remove(observer)
    }

    operator fun invoke(value: T) {
        observers.toList().forEach { it(value) }
    }
}

/** Parameterless variant of [Event]. */
class EmptyEvent {
    private val observers = mutableSetOf<Runnable>()

    val size: Int get() = observers.size

    operator fun plusAssign(observer: Runnable) {
        observers.add(observer)
    }

    operator fun minusAssign(observer: Runnable) {
        observers.remove(observer)
    }

    operator fun invoke() {
        observers.toList().forEach { it.run() }
    }
}
