package com.premiumtvplayer.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
 * TV-friendly text input. Larger hit area and a clear focus border so
 * users can navigate from the D-pad without losing the field. Used by
 * onboarding (Run 13) and source-add (Run 15).
 *
 * The internal `BasicTextField` keeps us free from `material3` text-field
 * defaults (which collide with the bespoke premium dark surface). When
 * the focus state flips, the border switches to `PremiumColors.FocusAccent`.
 *
 * For PIN entry use `keyboardType = KeyboardType.NumberPassword` and
 * `isPassword = true`.
 */
@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    errorText: String? = null,
) {
    val durations = LocalPremiumDurations.current
    val spacing = LocalPremiumSpacing.current
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            errorText != null -> PremiumColors.DangerRed
            isFocused -> PremiumColors.FocusAccent
            else -> PremiumColors.SurfaceHigh
        },
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = durations.short,
            easing = PremiumEasing.Standard,
        ),
        label = "textfield-border",
    )
    val labelColor = if (isFocused) PremiumColors.OnSurfaceHigh else PremiumColors.OnSurfaceMuted
    val shape = RoundedCornerShape(12.dp)
    val visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            style = PremiumType.LabelSmall.copy(color = labelColor),
            modifier = Modifier.padding(start = spacing.xs, bottom = spacing.s),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .background(PremiumColors.SurfaceElevated)
                .border(width = if (isFocused) 2.dp else 1.dp, color = borderColor, shape = shape)
                .padding(PaddingValues(horizontal = spacing.m, vertical = spacing.sm)),
            enabled = enabled,
            singleLine = true,
            interactionSource = interaction,
            textStyle = PremiumType.Body.copy(color = PremiumColors.OnSurfaceHigh),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(PremiumColors.AccentCyan),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceDim),
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (errorText != null) {
            Text(
                text = errorText,
                style = PremiumType.LabelSmall.copy(color = PremiumColors.DangerRed),
                modifier = Modifier.padding(start = spacing.xs, top = spacing.s),
            )
        }
    }
}

@Preview(name = "PremiumTextField · default + error", widthDp = 540, heightDp = 320, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun PremiumTextFieldPreview() {
    PremiumTvTheme {
        Column(
            modifier = Modifier.padding(LocalPremiumSpacing.current.xl),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                LocalPremiumSpacing.current.l
            ),
        ) {
            PremiumTextField(
                value = "you@example.com",
                onValueChange = {},
                label = "Email",
            )
            PremiumTextField(
                value = "",
                onValueChange = {},
                label = "Password",
                placeholder = "Enter password",
                isPassword = true,
                keyboardType = KeyboardType.Password,
            )
            PremiumTextField(
                value = "wrong",
                onValueChange = {},
                label = "PIN",
                isPassword = true,
                keyboardType = KeyboardType.NumberPassword,
                errorText = "PIN doesn't match",
            )
        }
    }
}
