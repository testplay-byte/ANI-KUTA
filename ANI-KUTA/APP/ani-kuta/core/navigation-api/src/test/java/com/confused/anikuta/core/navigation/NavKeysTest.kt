package com.confused.anikuta.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-748 (round 121 — the navigation identity): the nav-identity helpers.
 *
 * Pure JVM (the module has no Android deps) — the locks are the contract the
 * nav shell relies on:
 *  - an identity-aware key compares by IDENTITY, never by payload;
 *  - a plain key keeps full equality (nothing changes for it);
 *  - the provider key is per-INSTANCE, so two entries of the same class can
 *    be composed at once (the "Key … was used multiple times" crash) while
 *    the same entry keeps its saved state.
 */
class NavKeysTest {

    /** A key that declares its identity (the details-page shape). */
    private data class Identified(
        val id: Int,
        val cover: String? = null,
    ) : NavKeyIdentity {
        override val routeIdentity: String get() = "identified:$id"
    }

    /** A key that does NOT declare one (the historical shape). */
    private data class Plain(
        val id: Int,
        val cover: String? = null,
    ) : NavKey

    @Test
    fun `identity comes from the key when it declares one`() {
        assertEquals("identified:7", NavKeys.identityOf(Identified(7)))
    }

    @Test
    fun `identity falls back to the class name`() {
        assertEquals("Plain", NavKeys.identityOf(Plain(7)))
    }

    @Test
    fun `two identified keys with the same content are the same route`() {
        assertTrue(NavKeys.isSameRoute(Identified(7), Identified(7)))
    }

    @Test
    fun `an identified key ignores the presentation fields`() {
        // Different cover, same content -> the SAME route (the D-748 deep-link
        // fix: the old full-equality check said "different" and replaced the
        // page the user was already on).
        assertTrue(NavKeys.isSameRoute(Identified(7, cover = "a"), Identified(7, cover = "b")))
    }

    @Test
    fun `two identified keys with different content are different routes`() {
        assertFalse(NavKeys.isSameRoute(Identified(7), Identified(8)))
    }

    @Test
    fun `a plain key keeps full equality`() {
        assertTrue(NavKeys.isSameRoute(Plain(7), Plain(7)))
        assertFalse(NavKeys.isSameRoute(Plain(7, cover = "a"), Plain(7, cover = "b")))
    }

    @Test
    fun `a null never matches a route`() {
        assertFalse(NavKeys.isSameRoute(null, Plain(7)))
        assertFalse(NavKeys.isSameRoute(Plain(7), null))
        assertTrue(NavKeys.isSameRoute(null, null))
    }

    @Test
    fun `an identified key never equals a plain key by identity`() {
        // The identity namespaces differ, and the types differ -> not the same
        // route. (Guards against a future plain key that happens to use the
        // same identity string.)
        assertFalse(NavKeys.isSameRoute(Identified(7), Plain(7)))
    }

    @Test
    fun `the provider key differs for two entries of the same class`() {
        // The crash guard: two same-class entries composed at once must not
        // share a provider key.
        assertNotEquals(
            NavKeys.stateKeyOf(Identified(7, cover = "a")),
            NavKeys.stateKeyOf(Identified(8, cover = "b")),
        )
    }

    @Test
    fun `the provider key is stable for the same entry`() {
        // The state guard: re-composing the same entry must reuse the key, or
        // its rememberSaveable state (scroll positions) would be lost.
        assertEquals(
            NavKeys.stateKeyOf(Identified(7, cover = "a")),
            NavKeys.stateKeyOf(Identified(7, cover = "a")),
        )
    }

    @Test
    fun `the provider key carries the identity`() {
        assertTrue(NavKeys.stateKeyOf(Identified(7)).startsWith("identified:7#"))
    }
}
