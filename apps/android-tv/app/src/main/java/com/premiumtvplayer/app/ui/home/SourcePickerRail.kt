package com.premiumtvplayer.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.premiumtvplayer.app.ui.components.ButtonVariant
import com.premiumtvplayer.app.ui.components.ChipStyle
import com.premiumtvplayer.app.ui.components.PremiumButton
import com.premiumtvplayer.app.ui.components.PremiumChip
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType

/**
 * Empty-state hero for a fresh account. When the user has zero sources
 * added, Home becomes an inviting "add your first source" prompt.
 * Visual target: the Apple TV onboarding hero — editorial, never
 * apologetic.
 */
@Composable
fun SourcePickerRail(
    onAddSource: () -> Unit,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = LocalPremiumSpacing.current

    val background = Brush.radialGradient(
        colors = listOf(
            PremiumColors.AccentBlueDeep.copy(alpha = 0.30f),
            PremiumColors.BackgroundBase,
        ),
        center = Offset(x = 0f, y = 0f),
        radius = 2400f,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(max = 820.dp)
                .padding(horizontal = spacing.pageGutter),
            verticalArrangement = Arrangement.spacedBy(spacing.l),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                PremiumChip(
                    label = "Step 1 of 1",
                    style = ChipStyle.Outline,
                    accent = PremiumColors.AccentCyan,
                )
                PremiumChip(
                    label = "M3U · XMLTV · M3U + EPG",
                    style = ChipStyle.Outline,
                    accent = PremiumColors.OnSurfaceMuted,
                )
            }
            Text(
                text = "Add your first source",
                style = PremiumType.DisplayHero.copy(color = PremiumColors.OnSurfaceHigh),
            )
            Text(
                text = "Premium TV Player ships empty — you bring the content. Paste a " +
                    "playlist or EPG URL and we'll turn it into a beautifully " +
                    "navigable library in seconds.",
                style = PremiumType.Body.copy(color = PremiumColors.OnSurface),
            )
            Spacer(modifier = Modifier.height(spacing.s))
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PremiumButton(text = "Add Source", onClick = onAddSource)
                PremiumButton(
                    text = "Sign Out",
                    onClick = onSignOut,
                    variant = ButtonVariant.Ghost,
                )
            }
        }

        Text(
            text = "Your sources stay on your account — not tied to any one device.",
            style = PremiumType.LabelSmall.copy(color = PremiumColors.OnSurfaceDim),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(spacing.pageGutter),
        )
    }
}

@Preview(name = "SourcePickerRail", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun SourcePickerRailPreview() {
    PremiumTvTheme { SourcePickerRail(onAddSource = {}) }
}
