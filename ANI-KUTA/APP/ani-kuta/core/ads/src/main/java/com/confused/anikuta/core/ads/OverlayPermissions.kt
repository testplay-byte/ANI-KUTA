package com.confused.anikuta.core.ads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * D-560 (round 72): the "draw over other apps" consent behind the sponsor
 * popup's option row.
 *
 * # Why this lives here
 *
 * The smart-link ad's floating return pill (D-443/D-448/D-454) is a
 * TYPE_APPLICATION_OVERLAY window over the user's browser — it needs this
 * special consent, and [SmartLinkReturnPillController.show] is a silent
 * no-op without it. The onboarding wizard already asks once (D-449), but a
 * user who skipped that ask would never learn the pill exists. The user's
 * round-72 instruction: the sponsor popup itself offers the option — "if the
 * user has not turned on… draw over other apps, then the app will give the
 * user an option there to enable it. But if the user has enabled it, then it
 * will not give the user that option, or it won't even show anything." So
 * the row renders ONLY while [hasAccess] is false, and the interstitial
 * re-reads it on every ON_RESUME (the user returns from the system's toggle
 * screen and the row vanishes the moment consent is granted).
 *
 * # Why a copy, not a shared helper
 *
 * The identical intent pair lives in `:feature:onboarding`'s
 * OnboardingPermissions (D-449). `:core:ads` deliberately depends on NO
 * feature module (CORE_RULES §5/§7 — the ads system stays a self-contained
 * gate), so the small, stable pair of calls is duplicated here rather than
 * wiring a cross-module dependency for ten lines.
 *
 * Main-thread-safe: both calls are local reads / intent launches — no IPC,
 * no blocking (the same property the wizard's checks document).
 */
internal object OverlayPermissions {

    /**
     * The overlay consent is granted. No SDK_INT guard: [Settings.canDrawOverlays]
     * exists on every API level the app supports (minSdk 24).
     */
    fun hasAccess(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Opens this app's page on the system's "Display over other apps" screen
     * (the single toggle the user flips), with the general overlay-settings
     * list as the fallback for devices that refuse the per-package URI.
     * Returns whether ANY intent resolved (the row is a no-op UI-wise when
     * both fail — the ad flow never breaks over the option).
     */
    fun openSettings(context: Context): Boolean {
        val direct = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        if (runCatching { context.startActivity(direct) }.isSuccess) return true
        val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        return runCatching { context.startActivity(fallback) }.isSuccess
    }
}
