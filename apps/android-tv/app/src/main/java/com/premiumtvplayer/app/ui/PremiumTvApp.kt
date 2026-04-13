package com.premiumtvplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumType

/**
 * Root composable. For Run 11 this is intentionally a single-screen
 * cinematic splash that exercises the premium tokens (color stack,
 * gradients, type hierarchy, spacing) so we have a concrete design proof
 * before feature work starts in Run 13.
 *
 * Subsequent runs replace the body with a `NavHost` (Run 13) and the home
 * screen (Run 14).
 */
@Composable
fun PremiumTvApp() {
    val spacing = LocalPremiumSpacing.current

    // Subtle dark cinematic gradient: deep base in the corners with a soft
    // brand-accent breath in the upper-left. Mirrors the look of premium
    // TV home screens before any hero art has loaded.
    val backgroundBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xFF0E1322), // brand-tinted lift in the corner
            PremiumColors.BackgroundBase,
        ),
        center = Offset(x = 0f, y = 0f),
        radius = 1800f,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = spacing.pageGutter),
        ) {
            BrandMark()
            Spacer(modifier = Modifier.height(spacing.xxl))
            Text(
                text = "Premium TV Player",
                style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(spacing.m))
            Text(
                text = "Your sources. Your library. Beautifully.",
                style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(spacing.huge))
            BootingIndicator()
        }

        // Build-info pill, bottom-right — premium products always have
        // *somewhere* visible to read the build version.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(spacing.xxl)
        ) {
            Text(
                text = "v0.1.0 · build 1",
                style = PremiumType.LabelSmall.copy(color = PremiumColors.OnSurfaceDim),
            )
        }
    }
}

/**
 * Brand mark placeholder. The real logo (`assets/logo/logo-no_background.png`)
 * is wired into the splash + launcher in Run 14. For now we render a
 * gradient play-button glyph that shares the brand color so the design
 * language is already on screen.
 */
@Composable
private fun BrandMark() {
    val gradient = Brush.linearGradient(
        colors = listOf(PremiumColors.AccentCyan, PremiumColors.AccentBlueDeep),
    )
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 36.dp)
                .background(MaterialTheme.colorScheme.onPrimary)
        )
    }
}

/** Three-bar pulse placeholder; replaced by `BootProgress` in Run 12. */
@Composable
private fun BootingIndicator() {
    val color = PremiumColors.AccentCyan
    val spacing = LocalPremiumSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        repeat(3) { idx ->
            val alpha = when (idx) {
                0 -> 1f
                1 -> 0.6f
                else -> 0.3f
            }
            Box(
                modifier = Modifier
                    .size(width = 24.dp, height = 8.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}
