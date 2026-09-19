package com.confused.anikuta.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.confused.anikuta.core.notifications.InAppBannerController
import com.confused.anikuta.core.notifications.InAppBannerEvent
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * D-500: the IN-APP heads-up episode banner — the overlay the device round
 * asked for ("the application [should] show the banner whenever the user is
 * on any screen, rather than just silently giving a notification").
 *
 * # What it shows
 *
 * The EXACT composed banner bitmap the system notification renders (the
 * event carries it — no re-composition), as a rounded, shadowed card
 * sliding in from the top. Events without a bitmap (the text-style
 * fallback, or poster style disabled) render the compact title/text card —
 * the point is that a post is never fully silent inside the app.
 *
 * # Delivery semantics (why this is foreground-only by construction)
 *
 * The host collects [InAppBannerController.events] under
 * repeatOnLifecycle(STARTED): an event emitted while the app is BACKGROUND
 * has no collector and is dropped — the system notification already covers
 * that case, and a stale banner greeting the user on the next app-open
 * would be wrong. Foreground → the card shows on WHATEVER screen the user
 * is on (this host is a sibling of the nav content in AppRoot's overlay
 * stack — the same layer as the debug bubble and the ad interstitial).
 *
 * Auto-dismisses after 5s; a tap dismisses immediately (the system
 * notification remains for the deep-link — the card's job is to SHOW the
 * banner, not to duplicate the notification's navigation).
 */
@Composable
fun InAppBannerHost(controller: InAppBannerController = koinInject()) {
    // [current] holds the LAST event; [visible] drives the enter/exit. They
    // are separate on purpose: dismissal only flips [visible], so the card
    // stays composed WHILE the exit animation plays (the review round caught
    // the null-before-exit variant rendering an empty Box — the slide-out
    // never appeared).
    var current by remember { mutableStateOf<InAppBannerEvent?>(null) }
    var visible by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Foreground-only delivery: no STARTED collector → background emissions drop.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            controller.events.collect {
                current = it
                visible = true
            }
        }
    }

    // Auto-dismiss — restarted per event (a new post replaces the old card
    // and gets its own full 5s).
    LaunchedEffect(current) {
        if (current != null) {
            delay(BANNER_VISIBLE_MS)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val event = current
            // Local capture for the null check: `event.bitmap` is a public
            // property declared in :core:notifications — Kotlin cannot smart
            // cast across modules, so the bitmap must be captured into a
            // LOCAL val before the width/height guards (CI round 1's lesson).
            val bannerBitmap = event?.bitmap
            when {
                event == null -> {}
                bannerBitmap != null && bannerBitmap.width > 0 && bannerBitmap.height > 0 -> {
                    // The poster card: the notification's own banner, 1:1.
                    Image(
                        bitmap = bannerBitmap.asImageBitmap(),
                        contentDescription = "New episode banner — ${event.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            // The composer's TRUE canvas proportions.
                            .aspectRatio(
                                EpisodeBannerComposer.CANVAS_WIDTH.toFloat() /
                                    EpisodeBannerComposer.CANVAS_HEIGHT.toFloat(),
                            )
                            .shadow(12.dp, RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { visible = false },
                    )
                }
                else -> {
                    // The text card: a post without a composed banner still
                    // surfaces in-app (never fully silent).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(12.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { visible = false }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = event.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** D-500: the card's on-screen lifetime (ms). */
private const val BANNER_VISIBLE_MS = 5_000L
