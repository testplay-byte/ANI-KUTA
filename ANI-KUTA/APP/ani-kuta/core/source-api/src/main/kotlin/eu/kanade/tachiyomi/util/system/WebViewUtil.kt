package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * WebView utilities — ported from the AniMiru/Aniyomi reference
 * (`core/common/.../util/system/WebViewUtil.kt`).
 *
 * Used by [eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor] +
 * the user-facing `CloudflareWebViewActivity` to:
 *  - detect whether the device has a usable WebView ([supportsWebView])
 *  - detect whether the installed WebView is too old to run CF challenges ([isOutdated])
 *  - apply a sane default WebSettings config ([setDefaultSettings])
 *
 * Adapted for ANI-KUTA: removed the Moko `logcat` dep (uses `android.util.Log`
 * directly) + the `WebView.getHtml()` suspend helper (not needed for CF bypass).
 *
 * CORE_RULES: lives in `:core:source-api` (not `:core:common`) because the
 * Cloudflare interceptor that consumes it is in this module + the Aniyomi
 * binary-compat surface expects `eu.kanade.tachiyomi.util.system.WebViewUtil`
 * at this exact package.
 */
object WebViewUtil {

    private const val TAG = "Anikuta:SourceApi:WebViewUtil"

    /** Minimum WebView major version that can reliably run Cloudflare JS challenges. */
    const val MINIMUM_WEBVIEW_VERSION = 118

    /**
     * @return `true` if the device has a WebView package installed + the
     *   CookieManager singleton can be obtained. If WebView is missing (some
     *   emulators / de-Googled ROMs), the Cloudflare interceptor + the manual
     *   WebView UI are both skipped — the request just fails with the original
     *   Cloudflare 403/503.
     */
    fun supportsWebView(context: Context): Boolean {
        return try {
            // CookieManager.getInstance() throws MissingWebViewPackageException
            // when no WebView provider is installed.
            CookieManager.getInstance()
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
        } catch (e: Throwable) {
            Log.e(TAG, "WebView not supported on this device", e)
            false
        }
    }
}

/** @return `true` if this WebView instance is older than [WebViewUtil.MINIMUM_WEBVIEW_VERSION]. */
fun WebView.isOutdated(): Boolean {
    val ua = try {
        getDefaultUserAgentString()
    } catch (e: Throwable) {
        return true // can't tell → assume outdated so the UI prompts the user to update.
    }
    val match = """.*Chrome/(\d+)\..*""".toRegex().matchEntire(ua)
    val major = match?.let { it.groupValues.getOrNull(1)?.toIntOrNull() } ?: 0
    return major < WebViewUtil.MINIMUM_WEBVIEW_VERSION
}

@SuppressLint("SetJavaScriptEnabled")
fun WebView.setDefaultSettings() {
    with(settings) {
        // Cloudflare JS challenges require JS + DOM storage enabled.
        javaScriptEnabled = true
        domStorageEnabled = true

        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT

        // Handle popups (some CF challenges open a new window).
        setSupportMultipleWindows(true)

        // Allow zooming.
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }
    // cf_clearance is sometimes set with a third-party cookie scope.
    CookieManager.getInstance().acceptThirdPartyCookies(this)
}

/**
 * Reads the WebView's default (system) User-Agent string without permanently
 * mutating the receiver's UA. Based on https://stackoverflow.com/a/29218966.
 */
private fun WebView.getDefaultUserAgentString(): String {
    val originalUA: String = settings.userAgentString ?: ""
    // Setting UA to null makes the next getUserAgentString() call return the
    // default system WebView UA.
    settings.userAgentString = null
    val defaultUA = settings.userAgentString
    // Restore.
    settings.userAgentString = originalUA
    return defaultUA ?: ""
}
