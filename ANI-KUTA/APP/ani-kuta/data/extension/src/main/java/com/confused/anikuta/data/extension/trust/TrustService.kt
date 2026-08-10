package com.confused.anikuta.data.extension.trust

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.PreferenceStore

/**
 * Manages the trust set for extensions.
 *
 * Phase 3 fix: trust is now **per-package** (stored by pkgName), NOT by
 * signing-certificate-fingerprint. The old by-signer model caused auto-propagation:
 * trusting one extension auto-trusted ALL extensions from the same signer on the
 * next loadAll(). The per-package model gives the user explicit control over each
 * extension independently.
 *
 * The signature fingerprint is still verified (the extension must be signed),
 * but trust is granted per-package. This is a security trade-off: the user
 * explicitly trusts each package by name, not by signer. For a debug-build app
 * with no production users, this is acceptable.
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Data:Extension:Trust".
 */
class TrustService(
    private val store: PreferenceStore,
) {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:Trust"
        private const val KEY_TRUSTED_PACKAGES = "trusted_extension_packages"
        private const val SEPARATOR = ","
    }

    private val trustedPackages: MutableSet<String> by lazy {
        store.getString(KEY_TRUSTED_PACKAGES, "")
            .split(SEPARATOR)
            .filter { it.isNotEmpty() }
            .toMutableSet()
    }

    /**
     * Check if an extension package is trusted.
     */
    fun isTrusted(pkgName: String?): Boolean {
        if (pkgName == null) return false
        val trusted = pkgName in trustedPackages
        Logger.d(TAG) { "isTrusted($pkgName) = $trusted" }
        return trusted
    }

    /**
     * Trust an extension package. The user has confirmed they trust it.
     */
    fun trust(pkgName: String) {
        Logger.i(TAG) { "Trusting package: $pkgName" }
        trustedPackages.add(pkgName)
        persist()
    }

    /**
     * Revoke trust for a package. The extension will need to be re-trusted.
     */
    fun revoke(pkgName: String) {
        Logger.i(TAG) { "Revoking trust for: $pkgName" }
        trustedPackages.remove(pkgName)
        persist()
    }

    /**
     * Get all trusted package names (for display in settings).
     * Returns a snapshot copy — mutations don't affect the internal set.
     */
    fun getAllTrusted(): Set<String> = trustedPackages.toSet()

    private fun persist() {
        store.putString(KEY_TRUSTED_PACKAGES, trustedPackages.joinToString(SEPARATOR))
    }
}
