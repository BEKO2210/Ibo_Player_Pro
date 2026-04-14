package com.premiumtvplayer.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.premiumtvplayer.app.ui.theme.LocalPremiumDurations
import com.premiumtvplayer.app.ui.theme.LocalPremiumShapes
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumEasing
import com.premiumtvplayer.app.ui.theme.PremiumFocusScale
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType

/**
 * Premium focusable card primitive. The fundamental building block of
 * every TV row, grid, and gallery.
 *
 * Focus behaviour (matches Sony Bravia / Apple TV / Samsung Premium):
 *   - 1.06× scale on focus (`PremiumFocusScale`)
 *   - subtle glow ring (2dp accent border) appears
 *   - non-focused siblings get dimmed by `RowOfTiles` at the row level
 *   - press → 0.97 scale for haptic confirmation
 *
 * `aspectRatio` defaults to 16:9 (the canonical hero/poster ratio for
 * TV). Pass a different ratio for portrait posters (2:3) or wide
 * cinematic banners (21:9).
 *
 * `backdrop` is a slot for any composable: an `Image` from a coil
 * loader (Run 14), a colored placeholder, or a vector. The card crops
 * to `radii.poster` automatically.
 *
 * `unfocusedDim` is intended to be set by `RowOfTiles` when SOME OTHER
 * card in the row is focused (the focus-veil pattern). Standalone use
 * leaves this at 0f.
 */
@Composable
fun PremiumCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 280.dp,
    aspectRatio: Float = 16f / 9f,
    title: String? = null,
    subtitle: String? = null,
    unfocusedDim: Float = 0f,
    backdrop: @Composable () -> Unit = { GradientBackdropPlaceholder() },
) {
    val durations = LocalPremiumDurations.current
    val shapes = LocalPremiumShapes.current
    val spacing = LocalPremiumSpacing.current
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    val targetScale = if (isFocused) PremiumFocusScale else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = durations.short,
            easing = PremiumEasing.Premium,
        ),
        label = "card-scale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) PremiumColors.FocusAccent else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = durations.short,
            easing = PremiumEasing.Premium,
        ),
        label = "card-border",
    )
    // Veil alpha: when this card is the focused one, the veil disappears.
    // When another card in the row is focused, the row passes a non-zero
    // unfocusedDim and the veil materializes to push the card "behind".
    val veilAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0f else unfocusedDim,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = durations.short,
            easing = PremiumEasing.Standard,
        ),
        label = "card-veil",
    )
    val shape = RoundedCornerShape(shapes.poster)

    Column(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(width = width, height = width / aspectRatio)
                .clip(shape)
                .background(PremiumColors.SurfaceElevated)
                .border(width = if (isFocused) 2.dp else 0.dp, color = borderColor, shape = shape),
        ) {
            backdrop()
            // Focus veil — covers the whole card when another sibling is focused.
            if (veilAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .size(width = width, height = width / aspectRatio)
                        .background(PremiumColors.UnfocusedVeil.copy(alpha = veilAlpha))
                )
            }
            // Subtle bottom-up scrim for legibility when a title sits inside the artwork.
            if (title != null || subtitle != null) {
                Box(
                    modifier = Modifier
                        .size(width = width, height = width / aspectRatio)
                        .background(
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.65f),
                            )
                        ),
                )
                Column(
                    modifier = Modifier
                        .padding(PaddingValues(horizontal = spacing.m, vertical = spacing.sm))
                        .align(Alignment.BottomStart),
                ) {
                    if (subtitle != null) {
                        Text(
                            text = subtitle.uppercase(),
                            style = PremiumType.LabelSmall.copy(color = PremiumColors.AccentCyan),
                        )
                    }
                    if (title != null) {
                        Text(
                            text = title,
                            style = PremiumType.Title.copy(color = PremiumColors.OnSurfaceHigh),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Default backdrop when no artwork is provided. */
@Composable
private fun GradientBackdropPlaceholder() {
    Box(
        modifier = Modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        PremiumColors.SurfaceFloating,
                        PremiumColors.SurfaceElevated,
                        PremiumColors.SurfaceBase,
                    )
                )
            )
            .size(width = 320.dp, height = 200.dp),
    )
}

@Preview(name = "PremiumCard · standalone", widthDp = 360, heightDp = 240, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun PremiumCardPreview() {
    PremiumTvTheme {
        Box(modifier = Modifier.padding(LocalPremiumSpacing.current.l)) {
            PremiumCard(
                onClick = {},
                title = "Cinematic Originals",
                subtitle = "4K HDR · Dolby Vision",
            )
        }
    }
}
