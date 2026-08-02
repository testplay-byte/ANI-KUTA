package com.confused.anikuta.data.extension.trust

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.PreferenceStore

/**
 * Manages the trust set for extension signatures.
 *
 * Extensions must have their signing certificate's SHA-256 fingerprint
 * trusted by the user before their sources can be loaded.
 *
 * When an extension is first detected, the user is prompted to trust it
 * (showing the fingerprint). Once trusted, the fingerprint is stored
 * and the extension loads automatically on subsequent launches.
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Data:Extension:Trust".
 */
class TrustService(
    private val store: PreferenceStore,
) {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:Trust"
        private const val KEY_TRUSTED_FINGERPRINTS = "trusted_extension_fingerprints"
        private const val SEPARATOR = ","
    }

    private val trustedFingerprints: MutableSet<String> by lazy {
        store.getString(KEY_TRUSTED_FINGERPRINTS, "")
            .split(SEPARATOR)
            .filter { it.isNotEmpty() }
            .toMutableSet()
    }

    /**
     * Check if an extension's fingerprint is trusted.
     */
    fun isTrusted(fingerprint: String?): Boolean {
        if (fingerprint == null) return false
        val trusted = fingerprint in trustedFingerprints
        Logger.d(TAG) { "isTrusted($fingerprint) = $trusted" }
        return trusted
    }

    /**
     * Trust an extension's fingerprint. The user has confirmed they trust it.
     */
    fun trust(fingerprint: String) {
        Logger.i(TAG) { "Trusting fingerprint: $fingerprint" }
        trustedFingerprints.add(fingerprint)
        persist()
    }

    /**
     * Revoke trust for a fingerprint. The extension will need to be re-trusted.
     */
    fun revoke(fingerprint: String) {
        Logger.i(TAG) { "Revoking trust for: $fingerprint" }
        trustedFingerprints.remove(fingerprint)
        persist()
    }

    /**
     * Get all trusted fingerprints (for display in settings).
     */
    fun getTrustedFingerprints(): Set<String> = trustedFingerprints.toSet()

    private fun persist() {
        store.putString(KEY_TRUSTED_FINGERPRINTS, trustedFingerprints.joinToString(SEPARATOR))
    }
}
