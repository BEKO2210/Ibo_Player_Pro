package com.premiumtvplayer.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.premiumtvplayer.app.ui.theme.LocalPremiumDurations
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumEasing
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType

/**
 * Primary action button on the TV.
 *
 * Three variants:
 *   - [ButtonVariant.Primary]   — filled brand-blue; the canonical CTA
 *   - [ButtonVariant.Secondary] — high-contrast outline on dark surface
 *   - [ButtonVariant.Ghost]     — transparent fill, focus-only border;
 *                                  for tertiary actions inside cards
 *
 * Focus + press states use the premium motion language:
 *   - focus → 1.04 scale, accent border, brightness lift
 *   - press → 0.97 scale (200ms premium ease)
 *
 * Optional `leadingIcon`/`trailingIcon` slots accept any composable
 * (typically `Icon { ... }` from `material-icons-extended`).
 */
enum class ButtonVariant { Primary, Secondary, Ghost }

@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val durations = LocalPremiumDurations.current
    val spacing = LocalPremiumSpacing.current
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val isPressed by interaction.collectIsPressedAsState()

    val targetScale = when {
        !enabled -> 1f
        isPressed -> 0.97f
        isFocused -> 1.04f
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = durations.short,
            easing = PremiumEasing.Premium,
        ),
        label = "button-scale",
    )

    val baseContainer = when (variant) {
        ButtonVariant.Primary -> PremiumColors.AccentBlue
        ButtonVariant.Secondary -> PremiumColors.SurfaceElevated
        ButtonVariant.Ghost -> Color.Transparent
    }
    val focusContainer = when (variant) {
        ButtonVariant.Primary -> PremiumColors.AccentCyan
        ButtonVariant.Secondary -> PremiumColors.SurfaceFloating
        ButtonVariant.Ghost -> PremiumColors.SurfaceElevated
    }
    val container by animateColorAsState(
        targetValue = if (!enabled) baseContainer.copy(alpha = 0.5f)
        else if (isFocused) focusContainer
        else baseContainer,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = durations.short,
            easing = PremiumEasing.Standard,
        ),
        label = "button-container",
    )
    val labelColor = when (variant) {
        ButtonVariant.Primary -> PremiumColors.OnSurfaceHigh
        ButtonVariant.Secondary -> PremiumColors.OnSurface
        ButtonVariant.Ghost -> if (isFocused) PremiumColors.OnSurfaceHigh else PremiumColors.OnSurfaceMuted
    }
    val borderColor = when {
        variant == ButtonVariant.Secondary -> PremiumColors.SurfaceHigh
        isFocused -> PremiumColors.FocusAccent
        else -> Color.Transparent
    }

    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(container)
            .border(width = if (isFocused) 2.dp else 1.dp, color = borderColor, shape = shape)
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .onFocusChanged { /* keep slot for future analytics */ }
            .padding(PaddingValues(horizontal = spacing.l, vertical = spacing.sm)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s, Alignment.CenterHorizontally),
    ) {
        if (leadingIcon != null) {
            Box(contentAlignment = Alignment.Center) { leadingIcon() }
        }
        Text(
            text = text,
            style = PremiumType.Label.copy(color = labelColor),
        )
        if (trailingIcon != null) {
            Box(contentAlignment = Alignment.Center) { trailingIcon() }
        }
    }
}

@Preview(name = "PremiumButton · all variants", widthDp = 720, heightDp = 200, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun PremiumButtonPreview() {
    PremiumTvTheme {
        Row(
            modifier = Modifier.padding(LocalPremiumSpacing.current.xl),
            horizontalArrangement = Arrangement.spacedBy(LocalPremiumSpacing.current.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PremiumButton(text = "Watch Now", onClick = {})
            PremiumButton(text = "Add to Library", onClick = {}, variant = ButtonVariant.Secondary)
            PremiumButton(text = "More Info", onClick = {}, variant = ButtonVariant.Ghost)
        }
    }
}
