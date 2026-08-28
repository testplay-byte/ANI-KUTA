package com.confused.anikuta.core.designsystem.animation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
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
 * - The default bounds transform uses the app's Motion tokens (emphasized
 *   easing, 300ms — aligned with the nav fade duration) instead of the
 *   library's default spring.
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
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.coverSharedElement(key: String?): Modifier {
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
            boundsTransform = BoundsTransform { _, _ ->
                tween(Motion.DurationStandard, easing = Motion.EasingEmphasized)
            },
        )
    }
}
