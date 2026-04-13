package com.premiumtvplayer.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Premium TV Player — spacing scale (4dp grid).
 *
 * TV layouts breathe more than mobile — generous gutters carry visual
 * authority. The scale is designed so:
 *   - `xs` / `s`        → tight inline layout (chip padding, icon padding)
 *   - `m` / `l`         → component padding (cards, list rows)
 *   - `xl` / `xxl`      → section gutters and row spacing
 *   - `huge` / `hero`   → page-level breathing room and hero blocks
 *
 * Edge gutter (page padding) is `xxl` (32dp) at minimum on TVs, expanding
 * to 48dp on larger surfaces.
 */
@Immutable
data class PremiumSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val m: Dp = 16.dp,
    val l: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val huge: Dp = 64.dp,
    val hero: Dp = 96.dp,

    /** Edge gutter for full-screen layouts (Sony Bravia / Apple TV reference). */
    val pageGutter: Dp = 48.dp,

    /** Default horizontal padding inside a focused row of tiles. */
    val rowGutter: Dp = 16.dp,
) {
    companion object {
        val Default = PremiumSpacing()
    }
}

/** Standard rounded-corner radii — soft, never sharp on premium UIs. */
@Immutable
data class PremiumShapeRadii(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 20.dp,
    val xl: Dp = 28.dp,
    /** Hero / poster art corner radius. */
    val poster: Dp = 16.dp,
) {
    companion object {
        val Default = PremiumShapeRadii()
    }
}
