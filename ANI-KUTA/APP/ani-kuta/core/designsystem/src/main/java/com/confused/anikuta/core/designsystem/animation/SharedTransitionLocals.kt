package com.confused.anikuta.core.designsystem.animation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.confused.anikuta.core.designsystem.theme.Motion

/**
 * D-320: shared-element cover transition infrastructure (EXPERIMENTAL).
 *
 * User spec (2026-08-28): tapping an entry on Browse / Search / Library should
 * morph its cover smoothly INTO the details page's banner-cover position, and
 * pressing back should morph it back to the card — "the exact same cover image
 * feature… its cover will smoothly move to the location where the cover is
 * supposed to be on the details page".
 *
 * ## How it is wired
 *
 * `MainActivity` hosts a `SharedTransitionLayout` + `AnimatedContent`-based
 * screen switch and provides both scopes through these CompositionLocals.
 * Source cards (Browse/Search/Library) and the Details banner cover then call
 * [coverSharedElement] with the SAME key — the key is carried through the nav
 * key (`AnimeDetailsKey.transitionKey`) so both sides agree even though they
 * compose in different screens.
 *
 * ## Key rules (D-328)
 *
 * - Keys are NAMESPACED PER SCREEN. During a screen switch BOTH screens
 *   compose simultaneously inside AnimatedContent, and any two matching
 *   keys morph — even when the screens themselves switch instantly (snap).
 *   Library and Search both used to build `"cover:<url>"`, so an anime
 *   present on BOTH pages made its cover fly between the two pages when
 *   switching (device-reported on v0.2.62). Canonical builders:
 *   [libraryCoverKey] → `cover:library:<url>` (all library layouts),
 *   [searchCoverKey] → `cover:search:<url>` (AniList + extension results),
 *   [browseCoverKey] → `cover:browse:<section>:<url>` (section-qualified
 *   since browse can show the same anime in Trending AND Top Rated).
 *   With distinct namespaces, list ⇄ list switches can never match — only
 *   list ⇄ Details (the intended morph) does.
 * - The Details side never constructs a key — it carries whichever key the
 *   SOURCE card built (`AnimeDetailsKey.transitionKey` through the nav
 *   key), so list → details (and the reverse on back) morphs are unaffected
 *   by the namespacing.
 * - A null/blank key disables the shared element for that instance (used to
 *   gate the feature behind the `coverTransitionEnabled` preference and for
 *   covers that are null).
 * - The bounds morph runs [Motion.DurationSharedFlight] (600ms, D-327) on
 *   the SAME [Motion.EasingEmphasized] curve as the 450ms nav crossfade:
 *   the easing FAMILY must stay in sync (mismatched curves at equal
 *   duration were the D-324 "jitter"), but the duration is deliberately
 *   LONGER than the page crossfade — the page settles early while the cover
 *   keeps gliding (user spec: "the details page can open up early but the
 *   image will move slowly").
 * - [shape] keeps the corners ROUNDED for the whole flight: the shared
 *   element's default overlay clip is the parent's plain rectangle, so a
 *   card whose rounding comes from a parent Box clip would fly with square
 *   corners and snap back to rounded on landing (device feedback: "keep the
 *   corners rounded if they were rounded while the animation plays"). All
 *   call sites use the 12dp cover language, which is also the destination
 *   banner's shape — the corners read as one continuous rounded surface.
 *
 * ## D-322 note
 *
 * The project compiles against compose 1.10.4 (explicitly pinned in
 * gradle/libs.versions.toml — the BOM was removed; see D-322 for the
 * v0.2.60 NoSuchMethodError crash story). On the 1.10 line the first
 * parameter of `sharedElement` is named `sharedContentState` (it was `state`
 * on the 1.7 line) and the enum is `PlaceholderSize` (was `PlaceHolderSize`).
 * Named arguments keep this file explicit about which line it targets.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

// ── D-328: canonical, screen-namespaced cover keys ─────────────────────────
// ONE place defines each screen's key format. Every construction site — the
// card's modifier key AND the nav-arg key built in MainActivity — goes
// through these builders, so the two ends of a morph can never drift apart
// (and two different screens can never accidentally share a format).

/** Library card cover key (`cover:library:<url>`). Null when the url is blank. */
fun libraryCoverKey(url: String?): String? =
    url?.takeIf { it.isNotBlank() }?.let { "cover:library:$it" }

/** Search result cover key (`cover:search:<url>`) — AniList + extension results. */
fun searchCoverKey(url: String?): String? =
    url?.takeIf { it.isNotBlank() }?.let { "cover:search:$it" }

/** Browse carousel cover key (`cover:browse:<section>:<url>`) — section-qualified. */
fun browseCoverKey(section: String, url: String?): String? =
    url?.takeIf { it.isNotBlank() }?.let { "cover:browse:$section:$it" }

/**
 * Applies a shared-element modifier bound to [key] (no-op when the key is
 * null/blank or the scopes are absent — e.g. previews, or the feature toggle
 * turned off).
 *
 * @param shape The shape the shared element is clipped to WHILE morphing in
 *        the overlay ([SharedTransitionScope.OverlayClip]). Rounded by
 *        default so rounded covers stay rounded for the whole flight.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.coverSharedElement(
    key: String?,
    shape: Shape = RoundedCornerShape(12.dp),
): Modifier {
    if (key.isNullOrBlank()) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@coverSharedElement.sharedElement(
            // D-322: compose 1.10 names this parameter `sharedContentState`
            // (the 1.7 line called it `state` — the rename is exactly what
            // crashed v0.2.60 when compile and runtime lines disagreed).
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedScope,
            // D-327: the cover's OWN flight — 600ms emphasized, deliberately
            // longer than the 450ms nav crossfade (the page settles early
            // while the cover glides the rest of the way). The easing curve
            // stays identical to the crossfade's — mismatched curves at equal
            // duration were the D-324 jitter; decoupled durations on the SAME
            // curve are safe (only one thing still moves after 450ms).
            boundsTransform = BoundsTransform { _, _ ->
                tween(Motion.DurationSharedFlight, easing = Motion.EasingEmphasized)
            },
            // D-324: rounded corners for the whole flight (the default
            // ParentClip is the parent's plain rectangle — rounded cards flew
            // square and snapped back to rounded on landing).
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}
