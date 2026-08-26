package com.confused.anikuta.core.designsystem.color

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader

/**
 * D-277: Compose-side accessor for the cover's dominant accent color.
 *
 * Wraps [CoverColorExtractor.extract] in a [produceState] so a Composable can
 * reactively receive the extracted color (a single mid-tone, theme-agnostic —
 * saturation ≥ 0.40, lightness ∈ [0.40, 0.65]). Re-extracts only when
 * [coverUrl] changes; Coil's memory cache makes repeat reads instant.
 *
 * Returns `null` on blank URL, load failure, or when no suitable swatch is
 * available. Callers should provide a fallback color (e.g.
 * `MaterialTheme.colorScheme.surfaceVariant`) so the UI degrades gracefully
 * rather than blanking out.
 *
 * **Implementation note:** constructs [CoverColorExtractor] inline via
 * `remember { CoverColorExtractor(context, context.imageLoader) }` rather than
 * DI — this keeps `:core:designsystem` framework-light (no Koin dependency in
 * a low-level core module) and mirrors the library feature's proven
 * `rememberCoverAccentColor` pattern. The `context.imageLoader` resolves the
 * app's singleton ImageLoader (registered with the 500MB OkHttp disk cache),
 * so extraction hits Coil's disk cache when the cover was previously fetched.
 *
 * **When to use this vs the library's `rememberCoverAccentColor`:**
 * - This helper returns a single mid-tone — use for gradients, tints, and
 *   backdrops where you then darken/blend the color yourself (e.g. the Browse
 *   hero's palette gradient, D-277).
 * - The library's `rememberCoverAccentColor` (in `:feature:anime-library:impl`)
 *   is theme-adaptive (lighter in dark theme, darker in light theme) — use for
 *   border/tint accents that must read against the theme background.
 *
 * `ponytail:` both helpers share the same Coil+Palette extraction core. When a
 * THIRD consumer appears, consolidate into one parameterized helper here in
 * `:core:designsystem` (CORE_RULES §5 — no unrequested abstractions; two
 * callers don't yet justify the generalization, so both are kept specialized).
 *
 * CORE_RULES §20: extraction is logged under `CoverColorExtractor.TAG`
 * (`Anikuta:Core:DesignSystem:Color`).
 */
@Composable
fun rememberCoverDominantColor(coverUrl: String?): Color? {
    val context = LocalContext.current
    // remember so the CoverColorExtractor instance is cached across
    // recompositions (it's stateless; safe to retain).
    val extractor = remember { CoverColorExtractor(context, context.imageLoader) }
    val key = coverUrl.orEmpty()
    val state = produceState<Color?>(initialValue = null, key) {
        value = if (coverUrl.isNullOrBlank()) {
            null
        } else {
            runCatching { extractor.extract(coverUrl) }
                .getOrNull()
                ?.let { Color(it.toLong() and 0xFFFFFFFFL) }
        }
    }
    return state.value
}
