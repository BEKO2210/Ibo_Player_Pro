package com.premiumtvplayer.app.ui.parental

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.premiumtvplayer.app.data.api.DeviceDto
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
fun DeviceManagementScreen(
    onBack: () -> Unit,
    viewModel: DeviceManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalPremiumSpacing.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumColors.BackgroundBase)
            .padding(horizontal = spacing.pageGutter, vertical = spacing.huge),
    ) {
        when (val s = state) {
            DeviceManagementUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BootProgress() }
            is DeviceManagementUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::refresh, onBack = onBack)
            is DeviceManagementUiState.Ready -> Ready(
                state = s,
                onBack = onBack,
                onRevoke = viewModel::requestRevoke,
                onCancelRevoke = viewModel::cancelRevoke,
                onConfirmRevoke = viewModel::confirmRevoke,
            )
        }
    }
}

@Composable
private fun Ready(
    state: DeviceManagementUiState.Ready,
    onBack: () -> Unit,
    onRevoke: (String) -> Unit,
    onCancelRevoke: () -> Unit,
    onConfirmRevoke: () -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    Column(
        modifier = Modifier.widthIn(max = 1080.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.l),
    ) {
        Text(
            text = "Your devices",
            style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
        )
        Text(
            text = "Revoke any device to immediately sign it out across the network.",
            style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
        )
        if (state.errorMessage != null) ErrorBanner(state.errorMessage)
        if (state.devices.isEmpty()) {
            EmptyState()
        } else {
            state.devices.forEach { device ->
                DeviceRow(
                    device = device,
                    busy = state.busyId == device.id,
                    onRevoke = { onRevoke(device.id) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            PremiumButton(text = "Back", onClick = onBack, variant = ButtonVariant.Ghost)
        }
    }

    if (state.confirmingRevokeId != null) {
        val target = state.devices.firstOrNull { it.id == state.confirmingRevokeId } ?: return
        ConfirmRevokeOverlay(device = target, onCancel = onCancelRevoke, onConfirm = onConfirmRevoke)
    }
}

@Composable
private fun DeviceRow(device: DeviceDto, busy: Boolean, onRevoke: () -> Unit) {
    val spacing = LocalPremiumSpacing.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PremiumColors.SurfaceElevated)
            .padding(horizontal = spacing.l, vertical = spacing.m),
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = device.name, style = PremiumType.Title.copy(color = PremiumColors.OnSurfaceHigh))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                PremiumChip(label = device.platform, style = ChipStyle.Outline, accent = PremiumColors.AccentCyan)
                if (device.isCurrent) {
                    PremiumChip(label = "This device", style = ChipStyle.Outline, accent = PremiumColors.SuccessGreen)
                }
                if (device.isRevoked) {
                    PremiumChip(label = "Revoked", style = ChipStyle.Outline, accent = PremiumColors.DangerRed)
                }
                device.appVersion?.let {
                    PremiumChip(label = "v$it", style = ChipStyle.Outline, accent = PremiumColors.OnSurfaceMuted)
                }
            }
            device.lastSeenAt?.let {
                Text(
                    text = "Last seen: $it",
                    style = PremiumType.LabelSmall.copy(color = PremiumColors.OnSurfaceDim),
                )
            }
        }
        if (!device.isRevoked) {
            PremiumButton(
                text = if (device.isCurrent) "Sign Out This Device" else "Revoke",
                onClick = onRevoke,
                enabled = !busy,
                variant = if (device.isCurrent) ButtonVariant.Secondary else ButtonVariant.Ghost,
            )
        }
    }
}

@Composable
private fun ConfirmRevokeOverlay(
    device: DeviceDto,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumColors.BackgroundBase.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PremiumColors.SurfaceFloating)
                .padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Text(
                text = "Revoke \"${device.name}\"?",
                style = PremiumType.TitleLarge.copy(color = PremiumColors.OnSurfaceHigh),
            )
            Text(
                text = if (device.isCurrent) {
                    "Signing out this device will end any playback session and return you to the sign-in screen."
                } else {
                    "The device will be signed out immediately. It won't appear on the EPG or consume a device slot."
                },
                style = PremiumType.Body.copy(color = PremiumColors.OnSurface),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                PremiumButton(text = "Revoke", onClick = onConfirm)
                PremiumButton(text = "Cancel", onClick = onCancel, variant = ButtonVariant.Ghost)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    val spacing = LocalPremiumSpacing.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PremiumColors.SurfaceElevated)
            .padding(spacing.l),
    ) {
        Text(
            text = "No devices yet. When you sign in on a TV or phone, it'll show up here.",
            style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    val spacing = LocalPremiumSpacing.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PremiumColors.DangerRed.copy(alpha = 0.12f))
            .padding(horizontal = spacing.m, vertical = spacing.s),
    ) {
        Text(text = message, style = PremiumType.BodySmall.copy(color = PremiumColors.DangerRed))
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val spacing = LocalPremiumSpacing.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.l),
        ) {
            Text(text = message, style = PremiumType.Body.copy(color = PremiumColors.DangerRed))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                PremiumButton(text = "Try Again", onClick = onRetry)
                PremiumButton(text = "Back", onClick = onBack, variant = ButtonVariant.Ghost)
            }
        }
    }
}

@Preview(name = "DeviceManagementScreen · ready", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun DeviceManagementPreview() {
    PremiumTvTheme {
        val devices = listOf(
            DeviceDto(
                id = "d1", name = "Living Room TV", platform = "android_tv",
                appVersion = "0.1.0", osVersion = "Android 13",
                lastSeenAt = "2026-04-13T12:00:00.000Z", revokedAt = null,
                createdAt = "2026-04-12T08:00:00.000Z", isCurrent = true, isRevoked = false,
            ),
            DeviceDto(
                id = "d2", name = "Bedroom Shield", platform = "android_tv",
                appVersion = "0.1.0", osVersion = null,
                lastSeenAt = "2026-04-10T09:00:00.000Z", revokedAt = null,
                createdAt = "2026-04-09T18:00:00.000Z", isCurrent = false, isRevoked = false,
            ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumColors.BackgroundBase)
                .padding(LocalPremiumSpacing.current.pageGutter),
        ) {
            Ready(
                state = DeviceManagementUiState.Ready(devices = devices),
                onBack = {},
                onRevoke = {},
                onCancelRevoke = {},
                onConfirmRevoke = {},
            )
        }
    }
}
