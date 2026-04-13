package com.premiumtvplayer.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Premium TV Player — color tokens.
 *
 * Design philosophy: cinematic dark interface in the spirit of high-end TV
 * platforms (Sony Bravia, Samsung Tizen Premium, Apple TV). The base layer
 * is a deep, slightly cool black that gives heroes maximum contrast; surfaces
 * step up by 4-8 lightness units at a time so the focus ring always has
 * something to "lift" off of. The accent is the brand blue from the logo
 * (linear blue gradient toward cyan on signal-wave highlights).
 *
 * Token naming follows Material 3 roles so we can interop with `tv-material`
 * `darkColorScheme(...)`, but the actual palette is bespoke — NOT the
 * Material default purple.
 */
object PremiumColors {

    // ── Base surface stack ───────────────────────────────────────────────
    /** Deepest layer — immersive background behind heroes and full-bleed art. */
    val BackgroundBase = Color(0xFF050608)

    /** First lift — page surface, row backgrounds. */
    val SurfaceBase = Color(0xFF0B0D11)

    /** Second lift — elevated cards, panels. */
    val SurfaceElevated = Color(0xFF14171C)

    /** Third lift — overlays, modals, popovers. */
    val SurfaceFloating = Color(0xFF1D2128)

    /** Fourth lift — inline chips on dark cards, focus highlight backplate. */
    val SurfaceHigh = Color(0xFF272C35)

    // ── Foreground (text + iconography) ──────────────────────────────────
    /** Primary text on dark — pure white, used sparingly for emphasis. */
    val OnSurfaceHigh = Color(0xFFFFFFFF)

    /** Default body text — softened white that reads premium, not harsh. */
    val OnSurface = Color(0xFFE8EAEE)

    /** Secondary text — captions, metadata, helper strings. */
    val OnSurfaceMuted = Color(0xFF9CA3AF)

    /** Tertiary text / disabled. */
    val OnSurfaceDim = Color(0xFF5C6471)

    // ── Brand accent (matches the play-button gradient in the logo) ──────
    /** Primary accent — interactive surfaces, primary CTAs. */
    val AccentBlue = Color(0xFF3B82F6)

    /** Hover / pressed darker step. */
    val AccentBlueDeep = Color(0xFF2563EB)

    /** Bright cyan top of the logo gradient — used for focus glows. */
    val AccentCyan = Color(0xFF60A5FA)

    /** A subtle violet that pairs with the blue without competing for attention. */
    val AccentViolet = Color(0xFF8B5CF6)

    // ── Semantic states ──────────────────────────────────────────────────
    val SuccessGreen = Color(0xFF10B981)
    val WarningAmber = Color(0xFFF59E0B)
    val DangerRed = Color(0xFFEF4444)
    val InfoBlue = AccentCyan

    // ── Focus + selection ────────────────────────────────────────────────
    /** Border / ring color when an item gains TV focus. */
    val FocusBorder = OnSurfaceHigh

    /** Accent ring used when an actionable surface is focused. */
    val FocusAccent = AccentCyan

    /** Subtle veil used to dim non-focused tiles in a focused row. */
    val UnfocusedVeil = Color(0x66000000) // 40% black
}
