package com.premiumtvplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType

/**
 * Premium metadata chip — used for filter rows, content metadata
 * (resolution, language, age-rating), and inline labels.
 *
 * Two visual styles:
 *   - [ChipStyle.Filled]   — solid background, full-contrast label
 *   - [ChipStyle.Outline]  — thin border, transparent fill (good on
 *                            top of hero artwork)
 *
 * Typography is `LabelSmall` (12sp SemiBold, 1.2sp tracking) →
 * gives the all-caps editorial feel of an Apple TV / Bravia chip.
 */
enum class ChipStyle { Filled, Outline }

@Composable
fun PremiumChip(
    label: String,
    modifier: Modifier = Modifier,
    style: ChipStyle = ChipStyle.Filled,
    accent: Color = PremiumColors.OnSurfaceMuted,
) {
    val shape = RoundedCornerShape(8.dp)
    val container = when (style) {
        ChipStyle.Filled -> PremiumColors.SurfaceHigh
        ChipStyle.Outline -> Color.Transparent
    }
    val labelColor = when (style) {
        ChipStyle.Filled -> PremiumColors.OnSurface
        ChipStyle.Outline -> accent
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(container)
            .then(
                if (style == ChipStyle.Outline) Modifier.border(1.dp, accent, shape)
                else Modifier
            )
            .padding(PaddingValues(horizontal = 10.dp, vertical = 4.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label.uppercase(),
            style = PremiumType.LabelSmall.copy(color = labelColor),
        )
    }
}

@Preview(name = "PremiumChip · Filled + Outline", widthDp = 360, heightDp = 80, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun PremiumChipPreview() {
    PremiumTvTheme {
        Row(
            modifier = Modifier.padding(LocalPremiumSpacing.current.l),
            horizontalArrangement = Arrangement.spacedBy(LocalPremiumSpacing.current.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PremiumChip(label = "4K HDR")
            PremiumChip(label = "Dolby Atmos", accent = PremiumColors.AccentCyan)
            PremiumChip(label = "Live", style = ChipStyle.Outline, accent = PremiumColors.DangerRed)
            PremiumChip(label = "PG-13", style = ChipStyle.Outline, accent = PremiumColors.OnSurfaceMuted)
        }
    }
}
