package com.premiumtvplayer.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * Premium TV Player root theme.
 *
 * - V1 is dark-only (cinematic surface stack); a future light variant lives
 *   under the same composable shape so the call site never changes.
 * - We pipe the bespoke palette through `tv-material`'s `darkColorScheme(...)`
 *   so framework components (TabRow, Button, ListItem, etc.) inherit the
 *   right colors without each call site needing to know.
 * - Spacing + Motion live behind their own CompositionLocals so call sites
 *   read e.g. `LocalPremiumSpacing.current.l` and never hard-code dp values.
 */
@Composable
fun PremiumTvTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = PremiumColors.AccentBlue,
        onPrimary = PremiumColors.OnSurfaceHigh,
        primaryContainer = PremiumColors.AccentBlueDeep,
        onPrimaryContainer = PremiumColors.OnSurfaceHigh,
        secondary = PremiumColors.AccentCyan,
        onSecondary = PremiumColors.BackgroundBase,
        tertiary = PremiumColors.AccentViolet,
        onTertiary = PremiumColors.OnSurfaceHigh,
        background = PremiumColors.BackgroundBase,
        onBackground = PremiumColors.OnSurface,
        surface = PremiumColors.SurfaceBase,
        onSurface = PremiumColors.OnSurface,
        surfaceVariant = PremiumColors.SurfaceElevated,
        onSurfaceVariant = PremiumColors.OnSurfaceMuted,
        error = PremiumColors.DangerRed,
        onError = PremiumColors.OnSurfaceHigh,
        border = PremiumColors.SurfaceHigh,
        borderVariant = PremiumColors.SurfaceElevated,
    )

    CompositionLocalProvider(
        LocalPremiumSpacing provides PremiumSpacing.Default,
        LocalPremiumShapes provides PremiumShapeRadii.Default,
        LocalPremiumDurations provides PremiumDurations.Default,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PremiumType.toTvTypography(),
            content = content,
        )
    }
}

val LocalPremiumSpacing = staticCompositionLocalOf { PremiumSpacing.Default }
val LocalPremiumShapes = staticCompositionLocalOf { PremiumShapeRadii.Default }
val LocalPremiumDurations = staticCompositionLocalOf { PremiumDurations.Default }
