package com.premiumtvplayer.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme

/**
 * Three-bar pulse used during boot / async-loading states.
 *
 * The bars swap brightness on a 1.2s loop to suggest activity without
 * being aggressive enough to read as a "spinner". Cinematic, not
 * frenetic — at home on a Sony Bravia or Apple TV.
 */
@Composable
fun BootProgress(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = PremiumColors.AccentCyan,
) {
    val transition = rememberInfiniteTransition(label = "boot-progress")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val spacing = LocalPremiumSpacing.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        repeat(3) { idx ->
            // Distance from the "active" position (0..3) gives a smooth
            // brightness falloff that wraps around the row.
            val active = phase % 3f
            val delta = kotlin.math.abs(active - idx).coerceAtMost(3f - kotlin.math.abs(active - idx))
            val alpha = (1f - (delta * 0.6f)).coerceIn(0.2f, 1f)
            Box(
                modifier = Modifier
                    .size(width = 24.dp, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

@Preview(name = "BootProgress", widthDp = 200, heightDp = 80, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun BootProgressPreview() {
    PremiumTvTheme { BootProgress() }
}
