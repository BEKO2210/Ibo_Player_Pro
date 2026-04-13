package com.premiumtvplayer.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

/**
 * Premium TV Player — typography hierarchy.
 *
 * Sizing follows a 10-foot-UI scale (TV viewing distance ~2-3m). Proportions
 * are inspired by Apple TV's San Francisco hierarchy:
 *   - large hero displays use a LIGHT weight at big sizes for editorial feel,
 *   - body sits at 18sp (vs 14-16sp on phones) for readability across the
 *     room,
 *   - line-height is generous (~1.25-1.35) so ten-foot text never feels
 *     cramped.
 *
 * `FontFamily.Default` → ships with the system. The Android TV reference
 * font stack on Google TV resolves this to a Roboto-derived family that
 * already supports the weight range we use.  When we ship a branded font
 * (Run 12), it will be wired here through a single FontFamily definition.
 */
object PremiumType {
    private val DefaultFamily = FontFamily.Default

    val DisplayHero = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.Light,
        fontSize = 64.sp,
        lineHeight = 72.sp,
        letterSpacing = (-0.5).sp,
    )

    val DisplayLarge = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.Light,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.25).sp,
    )

    val Headline = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    )

    val TitleLarge = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.1.sp,
    )

    val Title = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.1.sp,
    )

    val Body = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.15.sp,
    )

    val BodySmall = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp,
    )

    val Label = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp,
    )

    /** Small all-caps style for chips / metadata badges. */
    val LabelSmall = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp,
    )

    /**
     * Maps the Premium type stack into Compose-TV's `Typography` so we can
     * wire it into `MaterialTheme(typography = ...)`. Roles that don't have a
     * direct premium equivalent fall back to the closest premium token.
     */
    fun toTvTypography(): Typography = Typography(
        displayLarge = DisplayHero,
        displayMedium = DisplayLarge,
        displaySmall = Headline,
        headlineLarge = Headline,
        headlineMedium = TitleLarge,
        headlineSmall = Title,
        titleLarge = TitleLarge,
        titleMedium = Title,
        titleSmall = Label,
        bodyLarge = Body,
        bodyMedium = BodySmall,
        bodySmall = Label,
        labelLarge = Label,
        labelMedium = Label,
        labelSmall = LabelSmall,
    )
}
