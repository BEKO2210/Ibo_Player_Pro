package com.premiumtvplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType

/**
 * Full-bleed hero section. The opening act of the home screen.
 *
 * Layout:
 *   ┌──────────────────────────────────────────────────────────┐
 *   │  [ backdrop slot — full-bleed image / video poster ]     │
 *   │                                                          │
 *   │   ┌──────────────────────────────────────────────────┐   │
 *   │   │ chips                                            │   │
 *   │   │ DISPLAY HERO TITLE                               │   │
 *   │   │ subtitle                                         │   │
 *   │   │ [ Watch Now ]  [ Add to Library ]                │   │
 *   │   └──────────────────────────────────────────────────┘   │
 *   │  ── scrim from BackgroundBase (bottom) → transparent (60%)│
 *   └──────────────────────────────────────────────────────────┘
 *
 * The scrim guarantees text legibility regardless of the artwork. The
 * content stack sits in the lower-left third — the canonical premium
 * TV hero composition.
 */
@Composable
fun HeroSection(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    chips: List<String> = emptyList(),
    backdrop: @Composable () -> Unit = { DefaultHeroBackdrop() },
    primaryAction: (@Composable () -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    val spacing = LocalPremiumSpacing.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 540.dp),
    ) {
        // Layer 1 — full-bleed backdrop.
        Box(modifier = Modifier.fillMaxSize()) {
            backdrop()
        }

        // Layer 2 — bottom-up scrim for legibility.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.6f to PremiumColors.BackgroundBase.copy(alpha = 0.5f),
                        1.0f to PremiumColors.BackgroundBase,
                    )
                )
        )

        // Layer 3 — content stack (lower-left).
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = spacing.pageGutter,
                    end = spacing.pageGutter,
                    bottom = spacing.huge,
                )
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            if (chips.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    chips.forEach { chip ->
                        PremiumChip(label = chip, style = ChipStyle.Outline, accent = PremiumColors.AccentCyan)
                    }
                }
            }
            Text(
                text = title,
                style = PremiumType.DisplayHero.copy(color = PremiumColors.OnSurfaceHigh),
                maxLines = 2,
            )
            Text(
                text = subtitle,
                style = PremiumType.Body.copy(color = PremiumColors.OnSurface),
                maxLines = 3,
            )
            if (primaryAction != null || secondaryAction != null) {
                Spacer(modifier = Modifier.height(spacing.s))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    primaryAction?.invoke()
                    secondaryAction?.invoke()
                }
            }
        }
    }
}

@Composable
private fun DefaultHeroBackdrop() {
    // Cinematic placeholder backdrop until real artwork lands in Run 14.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        PremiumColors.AccentBlueDeep.copy(alpha = 0.35f),
                        PremiumColors.SurfaceBase,
                        PremiumColors.BackgroundBase,
                    )
                )
            )
    )
}

@Preview(name = "HeroSection", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun HeroSectionPreview() {
    PremiumTvTheme {
        HeroSection(
            title = "Tonight's headliner",
            subtitle = "A premium player for the sources you trust. " +
                "Live, on-demand, and EPG — beautifully unified across every device.",
            chips = listOf("4K HDR", "Live", "Dolby Atmos"),
            primaryAction = { PremiumButton(text = "Watch Now", onClick = {}) },
            secondaryAction = { PremiumButton(text = "Add to Library", onClick = {}, variant = ButtonVariant.Secondary) },
        )
    }
}
