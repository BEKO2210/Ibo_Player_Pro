package com.premiumtvplayer.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.premiumtvplayer.app.ui.components.BrandLogo
import com.premiumtvplayer.app.ui.components.BrandLogoSize
import com.premiumtvplayer.app.ui.components.ButtonVariant
import com.premiumtvplayer.app.ui.components.PremiumButton
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType

/**
 * First screen after boot. Two primary CTAs: "Sign in" and
 * "Create account". Both lead into a PremiumTextField-driven
 * email/password flow. Quality bar: Apple TV / Netflix first-run screen.
 */
@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    val spacing = LocalPremiumSpacing.current

    val backgroundBrush = Brush.radialGradient(
        colors = listOf(
            PremiumColors.AccentBlueDeep.copy(alpha = 0.22f),
            PremiumColors.BackgroundBase,
        ),
        center = Offset(x = 0f, y = 0f),
        radius = 2400f,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(horizontal = spacing.pageGutter, vertical = spacing.huge),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.l),
        ) {
            BrandLogo(size = BrandLogoSize.Hero)
            Spacer(modifier = Modifier.height(spacing.s))
            Text(
                text = "Welcome to Premium TV Player",
                style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
            )
            Text(
                text = "Bring your own sources. We give them a home worthy of your TV.",
                style = PremiumType.Body.copy(color = PremiumColors.OnSurface),
            )
            Spacer(modifier = Modifier.height(spacing.m))
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PremiumButton(text = "Sign In", onClick = onSignIn)
                PremiumButton(
                    text = "Create Account",
                    onClick = onCreateAccount,
                    variant = ButtonVariant.Secondary,
                )
            }
        }

        Text(
            text = "v0.1.0 · build 1",
            style = PremiumType.LabelSmall.copy(color = PremiumColors.OnSurfaceDim),
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Preview(name = "WelcomeScreen", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun WelcomeScreenPreview() {
    PremiumTvTheme { WelcomeScreen(onSignIn = {}, onCreateAccount = {}) }
}
