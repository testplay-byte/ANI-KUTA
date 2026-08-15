package com.confused.anikuta.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.confused.anikuta.core.designsystem.theme.AnikutaTheme
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import eu.kanade.tachiyomi.util.system.setDefaultSettings

/**
 * CloudflareWebViewActivity — the user-triggered manual Cloudflare solver.
 *
 * **Two launch contexts (D-209):**
 * 1. **From the Extension Details page** (per the user's request): the user
 *    taps "Open in WebView" on an extension's detail screen → this Activity
 *    opens with the extension's source `baseUrl`. The user can browse the
 *    source, solve any Cloudflare challenge, and cookies are saved for that
 *    extension automatically.
 * 2. **From the Search/Details error card** (when Cloudflare auto-bypass fails):
 *    the headless [eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor]
 *    failed (interactive Turnstile challenge, timeout, etc.) → throws
 *    [eu.kanade.tachiyomi.network.CloudflareException] → the UI shows a
 *    "Cloudflare protection" card with an "Open in WebView" button → launches
 *    this Activity.
 *
 * **Cookie persistence:** the WebView uses the system `CookieManager.getInstance()`
 * singleton (the SAME one the OkHttp [eu.kanade.tachiyomi.network.AndroidCookieJar]
 * reads from). So once the user solves the challenge here, the `cf_clearance`
 * cookie is persisted to disk + replayed on the next OkHttp request to that host.
 * No extra wiring needed — the cookie just works on the retry.
 *
 * **Flow:**
 *  1. Activity launches with the URL → WebView loads it.
 *  2. Cloudflare challenge renders (if any) → user solves it manually (tap the
 *     Turnstile checkbox, wait for the JS challenge, etc.).
 *  3. `onPageFinished` calls `CookieManager.flush()` to persist cookies to disk.
 *  4. User taps the Done FAB (or back) → Activity finishes.
 *  5. Back in Search/Details, the user taps Refresh → the request now carries
 *     the fresh `cf_clearance` cookie → succeeds.
 *
 * @param url the URL to load (the source's baseUrl OR the blocked request URL).
 * @param sourceName optional display name for the top app bar title.
 * @param userAgent the User-Agent to use (defaults to the same UA NetworkHelper
 *   uses, so cf_clearance stays valid for the OkHttp retry).
 */
class CloudflareWebViewActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val sourceName = intent.getStringExtra(EXTRA_SOURCE_NAME)
        val userAgent = intent.getStringExtra(EXTRA_USER_AGENT) ?: DEFAULT_USER_AGENT
        val title = sourceName?.takeIf { it.isNotBlank() }
            ?: runCatching { android.net.Uri.parse(url).host }.getOrNull()
            ?: url

        setContent {
            AnikutaTheme {
                CloudflareWebViewScreen(
                    url = url,
                    title = title,
                    userAgent = userAgent,
                    onDone = { finish() },
                    onClearCookies = { clearCookiesForUrl(url) },
                )
            }
        }
    }

    /** Expires ALL cookies for [url] via the CookieManager (shared with the OkHttp jar). */
    private fun clearCookiesForUrl(url: String) {
        try {
            val cm = CookieManager.getInstance()
            val host = runCatching { android.net.Uri.parse(url).host }.getOrNull() ?: return
            listOf("https://$host/", "http://$host/", url).forEach { u ->
                cm.getCookie(u)?.split(";")?.forEach { cookie ->
                    val name = cookie.trim().substringBefore("=")
                    if (name.isNotBlank()) cm.setCookie(u, "$name=;Max-Age=0")
                }
            }
            cm.flush()
        } catch (e: Exception) {
            // Best-effort — don't crash the Activity.
        }
    }

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_SOURCE_NAME = "extra_source_name"
        private const val EXTRA_USER_AGENT = "extra_user_agent"

        /**
         * The default User-Agent — matches [eu.kanade.tachiyomi.network.NetworkHelper.defaultUserAgent]
         * so the cf_clearance cookie captured here stays valid for the OkHttp retry.
         */
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        /** Builds the launch Intent. [sourceName] is shown as the top app bar title. */
        fun newIntent(
            context: Context,
            url: String,
            sourceName: String? = null,
            userAgent: String? = null,
        ): Intent = Intent(context, CloudflareWebViewActivity::class.java).apply {
            putExtra(EXTRA_URL, url)
            sourceName?.let { putExtra(EXTRA_SOURCE_NAME, it) }
            userAgent?.let { putExtra(EXTRA_USER_AGENT, it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CloudflareWebViewScreen(
    url: String,
    title: String,
    userAgent: String,
    onDone: () -> Unit,
    onClearCookies: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadingProgress by remember { mutableStateOf(0) }
    var showCloudflareHelp by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            fontFamily = RobotoFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = url,
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = {
                        onClearCookies()
                        webView?.reload()
                    }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear cookies")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onDone, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loadingProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadingProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (showCloudflareHelp) {
                CloudflareHelpBanner()
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setDefaultSettings()
                        settings.userAgentString = userAgent
                        CookieManager.getInstance().acceptThirdPartyCookies(this)
                        CookieManager.getInstance().setAcceptCookie(true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                loadingProgress = 1
                                showCloudflareHelp = false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loadingProgress = 100
                                // D-209: inspect the rendered page for Cloudflare challenge
                                // markers (the headless interceptor only sees the raw HTTP
                                // response — here we can see the post-JS-rendered DOM).
                                view?.evaluateJavascript(
                                    "(function(){return document.documentElement.outerHTML;})()",
                                ) { html ->
                                    val body = html?.toString() ?: ""
                                    if (body.contains("_cf_chl_opt") ||
                                        body.contains("cf-turnstile") ||
                                        body.contains("challenge-platform") ||
                                        body.contains("Just a moment", ignoreCase = true) ||
                                        body.contains("Ray ID is")
                                    ) {
                                        showCloudflareHelp = true
                                    } else {
                                        showCloudflareHelp = false
                                    }
                                }
                                // D-209: persist cookies to disk so the OkHttp jar picks them up.
                                CookieManager.getInstance().flush()
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?,
                            ) {
                                // D-209 FIX: explicit null guard — errorResponse?.statusCode
                                // is Int? (safe-call on platform type), but `in List<Int>`
                                // requires non-null Int.
                                val code = errorResponse?.statusCode
                                if (request?.isForMainFrame == true && code != null && code in listOf(403, 503)) {
                                    showCloudflareHelp = true
                                }
                            }
                        }

                        loadUrl(url)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { /* no-op — settings applied at factory time */ },
                // D-209 FIX: destroy the WebView when the AndroidView leaves the
                // composition (Activity finished) — prevents the known WebView-
                // holds-Activity-context leak. Cookies are already flushed in
                // onPageFinished, so teardown is safe.
                onRelease = { it.stopLoading(); it.destroy() },
            )
        }
    }
}

/**
 * A banner telling the user this is a Cloudflare challenge + they may need to
 * tap a checkbox to solve it. Mirrors AniMiru's `WebViewScreenContent` banner.
 */
@Composable
private fun CloudflareHelpBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Cloudflare challenge detected. Solve it here (tap any checkbox if needed), " +
                    "then tap the check button. Cookies are saved automatically for this source.",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
