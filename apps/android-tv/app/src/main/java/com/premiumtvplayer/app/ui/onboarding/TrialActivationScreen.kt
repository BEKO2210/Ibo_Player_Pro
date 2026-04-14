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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.premiumtvplayer.app.ui.components.BootProgress
import com.premiumtvplayer.app.ui.components.ButtonVariant
import com.premiumtvplayer.app.ui.components.ChipStyle
import com.premiumtvplayer.app.ui.components.PremiumButton
import com.premiumtvplayer.app.ui.components.PremiumChip
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType

@Composable
fun TrialActivationScreen(
    onActivated: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    viewModel: TrialActivationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is TrialUiState.Activated || state is TrialUiState.AlreadyConsumed) {
            // "Already consumed" still forwards — the user just isn't on
            // a fresh trial, but the onboarding flow should continue to
            // profile picker (Run 14 will route non-entitled accounts to
            // a purchase flow separately).
            onActivated()
        }
    }

    val spacing = LocalPremiumSpacing.current

    val background = Brush.radialGradient(
        colors = listOf(
            PremiumColors.AccentCyan.copy(alpha = 0.14f),
            PremiumColors.BackgroundBase,
        ),
        center = Offset(x = 200f, y = 300f),
        radius = 2200f,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = spacing.pageGutter, vertical = spacing.huge),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.l),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                PremiumChip(label = "14-day trial", style = ChipStyle.Outline, accent = PremiumColors.AccentCyan)
                PremiumChip(label = "No payment", style = ChipStyle.Outline, accent = PremiumColors.SuccessGreen)
            }
            Text(
                text = "Start your 14-day trial",
                style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
            )
            Text(
                text = "Full access to every feature. Cancel anytime — or let it roll into a one-time lifetime plan when you're ready.",
                style = PremiumType.Body.copy(color = PremiumColors.OnSurface),
            )

            when (val s = state) {
                TrialUiState.Idle, TrialUiState.Submitting -> {
                    if (s is TrialUiState.Submitting) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                            BootProgress()
                            Text(
                                text = "Starting your trial…",
                                style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
                            )
                        }
                    }
                }
                is TrialUiState.Activated -> {
                    Text(
                        text = "You're in. Trial active until ${s.entitlement.trialEndsAt ?: "—"}.",
                        style = PremiumType.Body.copy(color = PremiumColors.SuccessGreen),
                    )
                }
                is TrialUiState.AlreadyConsumed -> {
                    Text(
                        text = "This account has already used its trial. You can still pick a profile and continue.",
                        style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
                    )
                }
                is TrialUiState.Error -> {
                    Text(
                        text = s.message,
                        style = PremiumType.Body.copy(color = PremiumColors.DangerRed),
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.s))

            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val submitting = state is TrialUiState.Submitting
                PremiumButton(
                    text = if (submitting) "Starting…" else "Start 14-day Trial",
                    onClick = viewModel::activate,
                    enabled = !submitting && state !is TrialUiState.Activated && state !is TrialUiState.AlreadyConsumed,
                )
                PremiumButton(
                    text = "Skip",
                    onClick = onSkip,
                    variant = ButtonVariant.Secondary,
                )
                PremiumButton(
                    text = "Back",
                    onClick = onBack,
                    variant = ButtonVariant.Ghost,
                )
            }
        }
    }
}

@Preview(name = "TrialActivationScreen", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun TrialActivationScreenPreview() {
    PremiumTvTheme {
        TrialActivationScreen(onActivated = {}, onSkip = {}, onBack = {})
    }
}
