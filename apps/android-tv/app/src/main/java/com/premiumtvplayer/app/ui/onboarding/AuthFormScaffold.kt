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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.premiumtvplayer.app.ui.components.ButtonVariant
import com.premiumtvplayer.app.ui.components.PremiumButton
import com.premiumtvplayer.app.ui.components.PremiumTextField
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumType

/**
 * Shared layout for the Signup and Login screens. Keeps the two flows
 * visually identical (a conscious premium-UX choice — users shouldn't
 * be surprised by layout differences between "Create account" and "Sign
 * in"), with only copy + the callback differing.
 */
@Composable
fun AuthFormScaffold(
    title: String,
    subtitle: String,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    submitting: Boolean,
    errorMessage: String?,
    primaryCta: String,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    switchCta: String,
    onSwitch: () -> Unit,
) {
    val spacing = LocalPremiumSpacing.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumColors.BackgroundBase)
            .padding(horizontal = spacing.pageGutter, vertical = spacing.huge),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.l),
        ) {
            Text(
                text = title,
                style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
            )
            Text(
                text = subtitle,
                style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
            )
            Spacer(modifier = Modifier.height(spacing.s))

            PremiumTextField(
                value = email,
                onValueChange = onEmailChange,
                label = "Email",
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
                enabled = !submitting,
            )
            PremiumTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = "Password",
                placeholder = "At least 8 characters",
                isPassword = true,
                keyboardType = KeyboardType.Password,
                enabled = !submitting,
            )

            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(PremiumColors.DangerRed.copy(alpha = 0.12f))
                        .padding(horizontal = spacing.m, vertical = spacing.s),
                ) {
                    Text(
                        text = errorMessage,
                        style = PremiumType.BodySmall.copy(color = PremiumColors.DangerRed),
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.s))

            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PremiumButton(
                    text = if (submitting) "Working…" else primaryCta,
                    onClick = onSubmit,
                    enabled = !submitting,
                )
                PremiumButton(
                    text = "Back",
                    onClick = onBack,
                    variant = ButtonVariant.Ghost,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "—",
                    style = PremiumType.Label.copy(color = PremiumColors.OnSurfaceDim),
                )
                PremiumButton(
                    text = switchCta,
                    onClick = onSwitch,
                    variant = ButtonVariant.Ghost,
                )
            }
        }
    }
}
