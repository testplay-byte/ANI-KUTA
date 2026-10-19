package com.confused.anikuta.core.navigation

/**
 * D-748 (round 121 — the navigation identity): a nav key that can describe its
 * OWN stable route identity.
 *
 * WHY THIS EXISTS: the app's nav shell keys its `SaveableStateProvider` by the
 * key's CLASS simple name (`currentKey::class.simpleName`). Two entries of the
 * same class that are alive at the same time therefore share a provider key —
 * and Compose throws `Key <name> was used multiple times`. The details screen
 * is the live example: replacing one `AnimeDetailsKey.AniList` with another
 * composes BOTH during the 450 ms transition, and both were keyed "AniList".
 *
 * A key that implements this interface states WHICH THING it routes to. The
 * identity deliberately covers the CONTENT and NEVER the presentation fields
 * (cover URL, title, transition key, auto-play episode): two keys that show
 * the same content are the same route, and treating them as different is what
 * forced the pointless replacement (and the duplicate provider) in the first
 * place.
 *
 * Keys that do NOT implement it keep the class-name behavior — the helper in
 * [NavKeys] falls back to it, so adopting this contract is opt-in and
 * incremental.
 */
interface NavKeyIdentity : NavKey {

    /**
     * The stable identity of the ROUTE this key points at — the content
     * identity, not the payload. Implementations must be equal for two keys
     * that show the same thing, and different otherwise.
     */
    val routeIdentity: String
}

/**
 * D-748: the nav-identity helpers — ONE place that answers "which route is
 * this?" and "are these two keys the same route?".
 *
 * Three questions, three answers:
 *  - [identityOf] — the route identity (the content identity for an
 *    identity-aware key, the class name for every other key).
 *  - [isSameRoute] — the comparison the nav shell uses to decide "the user is
 *    ALREADY on this route" (so a repeated deep link does not re-navigate) and
 *    "these two entries are the same page" (so a repeat push reveals the
 *    existing entry instead of stacking a second one).
 *  - [stateKeyOf] — the `SaveableStateProvider` key. It is the identity PLUS
 *    the key's own value hash, which is the per-instance suffix the plan
 *    asks for: two DIFFERENT entries of the same class (the details
 *    replacement) get different provider keys, so both can be composed during
 *    a transition; the SAME entry re-composed keeps its key, so its
 *    `rememberSaveable` state (scroll positions, list state) is preserved.
 */
object NavKeys {

    /**
     * The route identity of [key]: its declared identity when it declares one,
     * otherwise its class simple name (the historical behavior).
     */
    fun identityOf(key: Any): String = when (key) {
        is NavKeyIdentity -> key.routeIdentity
        else -> key::class.simpleName ?: "screen"
    }

    /**
     * Whether [a] and [b] point at the SAME route.
     *
     * For identity-aware keys this compares the IDENTITY ONLY — never the full
     * payload. That is the D-748 fix for the deep-link case: the old code
     * compared whole data classes, so a deep link to the content the user was
     * already looking at (with a different cover URL or title) compared
     * unequal, the shell treated it as a different destination, and it
     * REPLACED the page — which both reloaded the content needlessly and put
     * two same-class providers on screen at once.
     *
     * Every other key keeps full equality, so nothing changes for them.
     *
     * NULLABLE on purpose: the nav shell compares the TOP of the back stack
     * (which is null on an empty stack) with the target key.
     */
    fun isSameRoute(a: Any?, b: Any?): Boolean {
        if (a == null || b == null) return a == null && b == null
        return when {
            a is NavKeyIdentity && b is NavKeyIdentity -> a.routeIdentity == b.routeIdentity
            else -> a == b
        }
    }

    /**
     * The `SaveableStateProvider` key for [key]: the route identity plus the
     * key's own value hash (the per-instance suffix).
     *
     * The hash is value-based for the data-class keys (the app's nav keys all
     * are), so the same entry always produces the same provider key — the
     * saved state is restored when the user comes back to it — while two
     * DIFFERENT entries of the same class produce different keys, which is
     * what keeps the "Key … was used multiple times" crash away during a
     * transition.
     */
    fun stateKeyOf(key: Any): String = "${identityOf(key)}#${key.hashCode()}"
}
