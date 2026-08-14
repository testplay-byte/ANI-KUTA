package com.confused.anikuta

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.SettingsRepository
import com.confused.anikuta.core.testcontroller.TestControllerStatus
import com.confused.anikuta.settings.SettingsSectionLabel
import org.koin.core.context.GlobalContext

/**
 * Debug-only Test Controller settings screen (Task ID 7-UI, D-198 v3+).
 *
 * Reached via More → Settings → Test Controller (debug builds only). Lets the
 * user configure the WebSocket relay URL used by [TestControllerStatus] /
 * `WsRelayClient` to talk to the sandbox-side Next.js relay (:3030 via Caddy
 * `XTransformPort=3030` query).
 *
 * Layout (top → bottom):
 *  1. **Status card** — colored dot (green = connected, red = disconnected but
 *     URL set, yellow = no URL configured) + status text + the URL.
 *  2. **Relay URL** — `OutlinedTextField` with the sandbox preview URL as
 *     placeholder. Save button writes to [SettingsRepository] under
 *     `debug.test.relay_url` + triggers [TestControllerStatus.ensureConnected].
 *  3. **Diagnostics** — Copy Info button that puts a multi-line summary on the
 *     clipboard (status, URL, AccessibilityService state).
 *  4. **Instructions** — three numbered steps explaining the setup flow.
 *
 * Mirrors the design language of [SettingsScreen] / [AppearanceGeneralScreen]
 * — same `CollapsingHeader`, `ScrollBlurOverlay`, `Surface` cards, `RobotoFamily`
 * typography, `surfaceVariant.copy(alpha = 0.4f)` card background, and a 36dp
 * circular `BackAction` in the header's actions slot.
 *
 * @param onBack Pops this screen off the backstack.
 */
@Composable
fun TestControllerSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { GlobalContext.get().get<SettingsRepository>() }

    // Local editable state — initialized from the persisted setting (or the default Cloudflare URL).
    // D-198 v4: the default URL is hardcoded in WsRelayClient.DEFAULT_RELAY_URL.
    var relayUrl by remember {
        mutableStateOf(settings.getSetting(RELAY_URL_KEY)?.trim()?.ifBlank { null } ?: com.confused.anikuta.core.testcontroller.WsRelayClient.DEFAULT_RELAY_URL)
    }

    // Snapshot of the live connection state — read on screen entry. Re-entering
    // the screen (popping + pushing back) recomputes this. We don't poll
    // continuously — the toast from WsRelayClient already tells the user when
    // the connection succeeds/fails.
    val connected = remember { TestControllerStatus.isConnected() }
    val configuredUrl = remember {
        settings.getSetting(RELAY_URL_KEY)?.trim()?.ifBlank { null } ?: com.confused.anikuta.core.testcontroller.WsRelayClient.DEFAULT_RELAY_URL
    }
    val accessibilityEnabled = remember { isAccessibilityServiceEnabled(context) }

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Test Controller",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    // ── Status ──
                    item {
                        SettingsSectionLabel("Status")
                        StatusCard(
                            connected = connected,
                            configuredUrl = configuredUrl,
                            accessibilityEnabled = accessibilityEnabled,
                        )
                    }

                    // ── Relay URL input ──
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSectionLabel("Configuration")
                        SettingsCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                OutlinedTextField(
                                    value = relayUrl,
                                    onValueChange = { relayUrl = it },
                                    label = {
                                        Text(
                                            text = "Relay URL",
                                            fontFamily = RobotoFamily,
                                            fontSize = 13.sp,
                                        )
                                    },
                                    placeholder = {
                                        Text(
                                            text = "wss://your-sandbox-url/?XTransformPort=3030",
                                            fontFamily = RobotoFamily,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontFamily = RobotoFamily,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        settings.upsertSetting(
                                            RELAY_URL_KEY,
                                            relayUrl.trim(),
                                            "string",
                                            "debug",
                                        )
                                        showToast(context, "Saved — reconnecting…")
                                        // ensureConnected() reads the new URL from
                                        // SettingsRepository on its next start() call
                                        // (WsRelayClient re-reads on every connect attempt).
                                        // Note: 10s cooldown inside TestControllerStatus
                                        // may skip this call if the app-open health-check
                                        // just ran; the WsRelayClient's auto-retry (5s)
                                        // will pick up the new URL regardless.
                                        TestControllerStatus.ensureConnected()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Save",
                                        fontFamily = RobotoFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }
                        }
                    }

                    // ── Diagnostics: Copy Info ──
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSectionLabel("Diagnostics")
                        SettingsCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val statusLabel = if (connected) "Connected" else "Disconnected"
                                        val urlLabel = configuredUrl ?: "(none)"
                                        val a11yLabel =
                                            if (accessibilityEnabled) "Enabled" else "Disabled"
                                        val payload = buildString {
                                            appendLine("ANI-KUTA Test Controller")
                                            appendLine("Status: $statusLabel")
                                            appendLine("URL: $urlLabel")
                                            appendLine("AccessibilityService: $a11yLabel")
                                        }
                                        val clipboard = context
                                            .getSystemService(Context.CLIPBOARD_SERVICE)
                                            as ClipboardManager
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText(
                                                "ANI-KUTA Test Controller",
                                                payload,
                                            ),
                                        )
                                        showToast(context, "Copied to clipboard")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Copy Info",
                                        fontFamily = RobotoFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }
                        }
                    }

                    // ── Instructions ──
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSectionLabel("Instructions")
                        SettingsCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                Text(
                                    text = "1. Enable the Test Controller in Settings → Accessibility.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "2. Enter the sandbox preview URL above " +
                                        "(the URL you see in your browser, with " +
                                        "?XTransformPort=3030 appended).",
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "3. Tap Save. The agent can now send commands to your phone.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                ScrollBlurOverlay(
                    scrollOffset = {
                        if (lazyListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else lazyListState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Status card — colored dot + status text + URL
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun StatusCard(
    connected: Boolean,
    configuredUrl: String?,
    accessibilityEnabled: Boolean,
) {
    // Dot color: green = connected, red = disconnected but URL set,
    // yellow = no URL configured.
    val dotColor = when {
        connected -> Color(0xFF34D399)        // emerald-400
        configuredUrl != null -> Color(0xFFEF4444) // red-500
        else -> Color(0xFFF59E0B)             // amber-500
    }
    val statusText = when {
        connected -> "Connected"
        configuredUrl != null -> "Disconnected"
        else -> "No URL configured"
    }
    // If connected, the WsRelayClient knows the URL it actually connected to
    // (same as configured in our case — we don't expose connectedUrl() from
    // TestControllerStatus). If disconnected but configured, show the
    // configured URL. If neither, omit the URL line.
    val urlLabel = configuredUrl

    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = statusText,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (!urlLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = urlLabel,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "AccessibilityService: " +
                    if (accessibilityEnabled) "Enabled" else "Disabled — see Settings → Accessibility",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Shared UI helpers (mirror of AppearanceGeneralScreen.kt's private helpers)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        content()
    }
}

@Composable
private fun BackAction(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Android helpers (clipboard, toast, a11y service check)
// ════════════════════════════════════════════════════════════════════════════

/**
 * The SettingsRepository key for the WebSocket relay URL. Mirrors
 * `WsRelayClient.SETTING_RELAY_URL` so we don't pull the constant across the
 * module boundary (keeping this file's surface minimal).
 */
private const val RELAY_URL_KEY = "debug.test.relay_url"

/**
 * Show a short toast. Always posted to the main looper — safe to call from any
 * thread (e.g. a coroutine completion handler). We use `Toast.makeText`
 * directly because `TestToaster` is `internal` to `:core:test-controller` and
 * thus not visible from `:app`.
 */
private fun showToast(context: Context, message: String) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Whether our TestAccessibilityService is enabled in the system Accessibility
 * settings. Uses [android.view.accessibility.AccessibilityManager.
 * getEnabledAccessibilityServiceList] — the canonical Android API for this
 * check (the task spec hinted there's "no direct API", but this list IS the
 * official way; it's a cached, synchronous system call that's safe on the main
 * thread).
 */
private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
        as? android.view.accessibility.AccessibilityManager ?: return false
    val enabled = am.getEnabledAccessibilityServiceList(
        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
    )
    return enabled.any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
}
