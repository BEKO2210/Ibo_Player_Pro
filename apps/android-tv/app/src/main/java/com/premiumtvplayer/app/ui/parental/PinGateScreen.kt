package com.premiumtvplayer.app.ui.parental

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.premiumtvplayer.app.ui.components.ButtonVariant
import com.premiumtvplayer.app.ui.components.ChipStyle
import com.premiumtvplayer.app.ui.components.PremiumButton
import com.premiumtvplayer.app.ui.components.PremiumChip
import com.premiumtvplayer.app.ui.components.PremiumTextField
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Composable
fun PinGateScreen(
    profileName: String,
    onUnlocked: () -> Unit,
    onBack: () -> Unit,
    viewModel: PinGateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is PinGateUiState.Unlocked) onUnlocked()
    }

    val spacing = LocalPremiumSpacing.current
    val background = Brush.radialGradient(
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
            .background(background),
    ) {
        val editing = state as? PinGateUiState.Editing ?: PinGateUiState.Editing()
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 560.dp)
                .padding(horizontal = spacing.pageGutter),
            verticalArrangement = Arrangement.spacedBy(spacing.l),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                PremiumChip(
                    label = "Protected Profile",
                    style = ChipStyle.Outline,
                    accent = PremiumColors.AccentCyan,
                )
                editing.lockedUntilIso?.let { iso ->
                    LockedCountdownChip(lockedUntilIso = iso)
                }
            }
            Text(
                text = profileName,
                style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
            )
            Text(
                text = "Enter the PIN for this profile. Five wrong tries lock it for 15 minutes.",
                style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
            )
            Spacer(modifier = Modifier.height(spacing.s))
            PremiumTextField(
                value = editing.pin,
                onValueChange = viewModel::onPinChange,
                label = "PIN",
                placeholder = "4–10 digits",
                isPassword = true,
                keyboardType = KeyboardType.NumberPassword,
                enabled = !editing.submitting,
                errorText = editing.errorMessage,
            )
            if (editing.failedAttemptCount > 0) {
                Text(
                    text = "${editing.failedAttemptCount} wrong attempt${if (editing.failedAttemptCount == 1) "" else "s"}.",
                    style = PremiumType.LabelSmall.copy(color = PremiumColors.WarningAmber),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                PremiumButton(
                    text = if (editing.submitting) "Checking…" else "Unlock",
                    onClick = viewModel::submit,
                    enabled = !editing.submitting,
                )
                PremiumButton(
                    text = "Back",
                    onClick = onBack,
                    variant = ButtonVariant.Ghost,
                    enabled = !editing.submitting,
                )
            }
        }
    }
}

@Composable
private fun LockedCountdownChip(lockedUntilIso: String) {
    val target = remember(lockedUntilIso) {
        runCatching { Instant.parse(lockedUntilIso) }.getOrNull()
    } ?: return
    var remaining by remember { mutableStateOf(Duration.between(Instant.now(), target)) }
    LaunchedEffect(lockedUntilIso) {
        while (!remaining.isNegative && !remaining.isZero) {
            delay(1_000)
            remaining = Duration.between(Instant.now(), target)
        }
    }
    val label = if (remaining.isNegative || remaining.isZero) {
        "Unlocked"
    } else {
        val mins = remaining.toMinutes()
        val secs = remaining.minusMinutes(mins).seconds
        "Locked · %d:%02d".format(mins, secs)
    }
    PremiumChip(label = label, style = ChipStyle.Outline, accent = PremiumColors.DangerRed)
}

@Preview(name = "PinGateScreen · editing", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun PinGateScreenPreview() {
    PremiumTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumColors.BackgroundBase)
                .padding(LocalPremiumSpacing.current.pageGutter),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(LocalPremiumSpacing.current.l)) {
                PremiumChip(label = "Protected Profile", style = ChipStyle.Outline, accent = PremiumColors.AccentCyan)
                Text(
                    text = "Alex",
                    style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
                )
                PremiumTextField(
                    value = "",
                    onValueChange = {},
                    label = "PIN",
                    placeholder = "4–10 digits",
                    isPassword = true,
                    keyboardType = KeyboardType.NumberPassword,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(LocalPremiumSpacing.current.m)) {
                    PremiumButton(text = "Unlock", onClick = {})
                    PremiumButton(text = "Back", onClick = {}, variant = ButtonVariant.Ghost)
                }
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
private fun shapePlaceholder() = RoundedCornerShape(12.dp)
