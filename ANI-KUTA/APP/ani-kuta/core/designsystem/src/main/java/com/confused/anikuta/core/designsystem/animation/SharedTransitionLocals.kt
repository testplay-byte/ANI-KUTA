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
 * ## Key rules
 *
 * - Keys must be UNIQUE within one screen composition. Browse has overlapping
 *   sections (the same anime can be in Trending AND Top Rated), so browse keys
 *   are section-qualified: `"cover:trending:<url>"`. Search/Library grids are
 *   unique per URL: `"cover:<url>"`.
 * - A null/blank key disables the shared element for that instance (used to
 *   gate the feature behind the `coverTransitionEnabled` preference and for
 *   covers that are null).
 * - The default bounds transform uses the app's Motion tokens — the D-324
 *   container duration (450ms, emphasized easing) shared with the nav
 *   crossfade so the flying cover and the fading screens accelerate and
 *   settle in lockstep (mismatched velocity profiles were the device-reported
 *   "jitter").
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
            // D-324: 450ms emphasized morph — same token + easing the nav
            // crossfade runs on, so the cover and the screens move in
            // lockstep (no relative jitter between the two curves).
            boundsTransform = BoundsTransform { _, _ ->
                tween(Motion.DurationContainer, easing = Motion.EasingEmphasized)
            },
            // D-324: rounded corners for the whole flight (the default
            // ParentClip is the parent's plain rectangle — rounded cards flew
            // square and snapped back to rounded on landing).
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}
