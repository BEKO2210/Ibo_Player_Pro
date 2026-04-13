package com.premiumtvplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Text
import com.premiumtvplayer.app.ui.components.BootProgress
import com.premiumtvplayer.app.ui.components.BrandLogo
import com.premiumtvplayer.app.ui.components.BrandLogoSize
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType

/**
 * Root composable. Run 12 splash is composed entirely from design-system
 * primitives (`BrandLogo`, `BootProgress`) on top of tokenized colors /
 * typography / spacing — no hard-coded literals outside the cinematic
 * background gradient (which is itself derived from
 * `PremiumColors.BackgroundBase` plus a brand-tinted lift).
 *
 * Subsequent runs replace the body with a `NavHost` (Run 13) and the
 * home screen (Run 14).
 */
@Composable
fun PremiumTvApp() {
    val spacing = LocalPremiumSpacing.current

    val backgroundBrush = Brush.radialGradient(
        colors = listOf(
            PremiumColors.AccentBlueDeep.copy(alpha = 0.18f),
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
            BrandLogo(size = BrandLogoSize.Splash)
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
            BootProgress()
        }

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

@Preview(name = "PremiumTvApp · splash", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun PremiumTvAppPreview() {
    PremiumTvTheme { PremiumTvApp() }
}
