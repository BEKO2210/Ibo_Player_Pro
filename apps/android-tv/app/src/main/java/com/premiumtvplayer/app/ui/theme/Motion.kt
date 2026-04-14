package com.premiumtvplayer.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * Premium TV Player — motion language.
 *
 * Three curves cover everything we need:
 *   - `Standard`: Material standard easing — for utility transitions where
 *     the UI just needs to get out of the way.
 *   - `Premium`:  Apple-style spring-ish curve. Confident, slightly
 *     overshoots-feeling without actually overshooting; perfect for focus
 *     scale / card lift transitions.
 *   - `Cinematic`: a slow-in/slow-out used for hero crossfades and ambient
 *     parallax — never for direct user-driven motion.
 *
 * Durations follow the 60-90-200-400 rule:
 *   - 60ms  micro feedback (focus-ring pop-in)
 *   - 200ms short transitions (focus scale, hover overlay)
 *   - 400ms medium transitions (card flip, drawer slide)
 *   - 800ms long transitions (hero crossfade, page transitions)
 *
 * No transition should be < 60ms (looks instant + jittery) or > 800ms
 * (looks sluggish on TV).
 */
object PremiumEasing {
    /** Material standard — for utility transitions. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Apple-style "premium" curve — for focus + hover. */
    val Premium: Easing = CubicBezierEasing(0.32f, 0.72f, 0.0f, 1.0f)

    /** Cinematic — for hero crossfades and ambient motion. */
    val Cinematic: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
}

@Immutable
data class PremiumDurations(
    val micro: Int = 60,
    val short: Int = 200,
    val medium: Int = 400,
    val long: Int = 800,
) {
    companion object {
        val Default = PremiumDurations()
    }
}

/** Pre-baked tween specs for the most common premium transitions. */
object PremiumTransitions {
    val FocusScale = tween<Float>(durationMillis = 200, easing = PremiumEasing.Premium)
    val FocusElevation = tween<Float>(durationMillis = 200, easing = PremiumEasing.Premium)
    val HoverOverlay = tween<Float>(durationMillis = 200, easing = PremiumEasing.Standard)
    val HeroCrossfade = tween<Float>(durationMillis = 800, easing = PremiumEasing.Cinematic)
    val DrawerSlide = tween<Float>(durationMillis = 400, easing = PremiumEasing.Premium)
}

/**
 * Focus scale factor — when a tile becomes focused on TV, it grows by this
 * factor. 1.06 is the sweet spot: large enough to read across a room, not
 * so large that adjacent tiles reflow.
 */
const val PremiumFocusScale: Float = 1.06f
