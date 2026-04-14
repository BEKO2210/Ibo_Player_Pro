package com.premiumtvplayer.app.ui.parental

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.premiumtvplayer.app.data.api.ProfileDto
import com.premiumtvplayer.app.ui.components.BootProgress
import com.premiumtvplayer.app.ui.components.ButtonVariant
import com.premiumtvplayer.app.ui.components.ChipStyle
import com.premiumtvplayer.app.ui.components.PremiumButton
import com.premiumtvplayer.app.ui.components.PremiumChip
import com.premiumtvplayer.app.ui.components.PremiumTextField
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType

@Composable
fun ProfileManagementScreen(
    onBack: () -> Unit,
    viewModel: ProfileManagementViewModel = hiltViewModel(),
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
            ProfileManagementUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BootProgress() }
            is ProfileManagementUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::refresh, onBack = onBack)
            is ProfileManagementUiState.Ready -> Ready(
                state = s,
                onBack = onBack,
                onRename = viewModel::rename,
                onSetAgeLimit = viewModel::setAgeLimit,
                onSetPin = viewModel::setPin,
                onClearPin = viewModel::clearPin,
                onMakeDefault = viewModel::makeDefault,
                onRequestDelete = viewModel::requestDelete,
                onCancelDelete = viewModel::cancelDelete,
                onConfirmDelete = viewModel::confirmDelete,
                onCreate = viewModel::createProfile,
            )
        }
    }
}

@Composable
private fun Ready(
    state: ProfileManagementUiState.Ready,
    onBack: () -> Unit,
    onRename: (id: String, newName: String) -> Unit,
    onSetAgeLimit: (id: String, ageLimit: Int?) -> Unit,
    onSetPin: (id: String, pin: String) -> Unit,
    onClearPin: (id: String) -> Unit,
    onMakeDefault: (id: String) -> Unit,
    onRequestDelete: (id: String) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCreate: (name: String, isKids: Boolean, ageLimit: Int?, pin: String?) -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    Column(
        modifier = Modifier.widthIn(max = 1080.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.l),
    ) {
        Text(
            text = "Profile settings",
            style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
        )
        if (state.errorMessage != null) {
            ErrorBanner(state.errorMessage)
        }

        state.profiles.forEach { profile ->
            ProfileRowEditor(
                profile = profile,
                busy = state.busyId == profile.id,
                onRename = { onRename(profile.id, it) },
                onSetAgeLimit = { onSetAgeLimit(profile.id, it) },
                onSetPin = { onSetPin(profile.id, it) },
                onClearPin = { onClearPin(profile.id) },
                onMakeDefault = { onMakeDefault(profile.id) },
                onRequestDelete = { onRequestDelete(profile.id) },
            )
        }

        Spacer(modifier = Modifier.height(spacing.m))
        AddProfileForm(busy = state.busyId == "__create", onCreate = onCreate)

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            PremiumButton(text = "Back", onClick = onBack, variant = ButtonVariant.Ghost)
        }
    }

    if (state.confirmingDeleteId != null) {
        val target = state.profiles.firstOrNull { it.id == state.confirmingDeleteId } ?: return
        ConfirmDeleteOverlay(profile = target, onCancel = onCancelDelete, onConfirm = onConfirmDelete)
    }
}

@Composable
private fun ProfileRowEditor(
    profile: ProfileDto,
    busy: Boolean,
    onRename: (String) -> Unit,
    onSetAgeLimit: (Int?) -> Unit,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
    onMakeDefault: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    var editedName by remember(profile.id, profile.name) { mutableStateOf(profile.name) }
    var editedAgeLimit by remember(profile.id, profile.ageLimit) {
        mutableStateOf(profile.ageLimit?.toString() ?: "")
    }
    var editedPin by remember(profile.id) { mutableStateOf("") }

    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PremiumColors.SurfaceElevated)
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = profile.name,
                style = PremiumType.TitleLarge.copy(color = PremiumColors.OnSurfaceHigh),
                modifier = Modifier.weight(1f),
            )
            if (profile.isDefault) PremiumChip(label = "Default", style = ChipStyle.Filled)
            PremiumChip(
                label = if (profile.isKids) "Kids" else "Adult",
                style = ChipStyle.Outline,
                accent = if (profile.isKids) PremiumColors.AccentCyan else PremiumColors.OnSurfaceMuted,
            )
            if (profile.hasPin) PremiumChip(label = "PIN", style = ChipStyle.Outline, accent = PremiumColors.AccentCyan)
        }

        PremiumTextField(
            value = editedName,
            onValueChange = { editedName = it },
            label = "Name",
            enabled = !busy,
        )
        PremiumTextField(
            value = editedAgeLimit,
            onValueChange = { v -> editedAgeLimit = v.filter { it.isDigit() }.take(2) },
            label = "Age limit (0–21) — leave empty for adult",
            keyboardType = KeyboardType.Number,
            enabled = !busy,
        )
        PremiumTextField(
            value = editedPin,
            onValueChange = { v -> editedPin = v.filter { it.isDigit() }.take(10) },
            label = if (profile.hasPin) "New PIN (4–10 digits)" else "Set PIN (4–10 digits)",
            isPassword = true,
            keyboardType = KeyboardType.NumberPassword,
            enabled = !busy,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            PremiumButton(
                text = "Save Name",
                onClick = { onRename(editedName.trim()) },
                enabled = !busy && editedName.trim().isNotEmpty() && editedName.trim() != profile.name,
                variant = ButtonVariant.Secondary,
            )
            PremiumButton(
                text = "Save Age Limit",
                onClick = { onSetAgeLimit(editedAgeLimit.toIntOrNull()) },
                enabled = !busy,
                variant = ButtonVariant.Secondary,
            )
            PremiumButton(
                text = if (profile.hasPin) "Change PIN" else "Set PIN",
                onClick = { onSetPin(editedPin) },
                enabled = !busy && editedPin.length >= 4,
                variant = ButtonVariant.Secondary,
            )
            if (profile.hasPin) {
                PremiumButton(
                    text = "Clear PIN",
                    onClick = onClearPin,
                    enabled = !busy,
                    variant = ButtonVariant.Ghost,
                )
            }
            if (!profile.isDefault) {
                PremiumButton(
                    text = "Make Default",
                    onClick = onMakeDefault,
                    enabled = !busy,
                    variant = ButtonVariant.Ghost,
                )
            }
            PremiumButton(
                text = "Delete",
                onClick = onRequestDelete,
                enabled = !busy,
                variant = ButtonVariant.Ghost,
            )
        }
    }
}

@Composable
private fun AddProfileForm(
    busy: Boolean,
    onCreate: (name: String, isKids: Boolean, ageLimit: Int?, pin: String?) -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    var name by remember { mutableStateOf("") }
    var isKids by remember { mutableStateOf(false) }
    var ageLimit by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PremiumColors.SurfaceElevated)
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Text(
            text = "Add profile",
            style = PremiumType.TitleLarge.copy(color = PremiumColors.OnSurfaceHigh),
        )
        PremiumTextField(value = name, onValueChange = { name = it }, label = "Name", enabled = !busy)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            PremiumButton(
                text = if (isKids) "Kids ✓" else "Kids",
                onClick = { isKids = !isKids },
                variant = if (isKids) ButtonVariant.Primary else ButtonVariant.Secondary,
                enabled = !busy,
            )
            PremiumButton(
                text = if (!isKids) "Adult ✓" else "Adult",
                onClick = { isKids = false },
                variant = if (!isKids) ButtonVariant.Primary else ButtonVariant.Secondary,
                enabled = !busy,
            )
        }
        PremiumTextField(
            value = ageLimit,
            onValueChange = { v -> ageLimit = v.filter { it.isDigit() }.take(2) },
            label = "Age limit (0–21, optional)",
            keyboardType = KeyboardType.Number,
            enabled = !busy,
        )
        PremiumTextField(
            value = pin,
            onValueChange = { v -> pin = v.filter { it.isDigit() }.take(10) },
            label = "PIN (optional, 4–10 digits)",
            isPassword = true,
            keyboardType = KeyboardType.NumberPassword,
            enabled = !busy,
        )
        PremiumButton(
            text = "Add Profile",
            onClick = {
                if (name.isNotBlank()) {
                    onCreate(
                        name.trim(),
                        isKids,
                        ageLimit.toIntOrNull(),
                        pin.ifBlank { null },
                    )
                    name = ""
                    ageLimit = ""
                    pin = ""
                }
            },
            enabled = !busy && name.isNotBlank(),
        )
    }
}

@Composable
private fun ConfirmDeleteOverlay(
    profile: ProfileDto,
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
                text = "Delete \"${profile.name}\"?",
                style = PremiumType.TitleLarge.copy(color = PremiumColors.OnSurfaceHigh),
            )
            Text(
                text = "This removes the profile from every device on this account. " +
                    "Watch history, favorites, and Continue Watching tied to it will be lost.",
                style = PremiumType.Body.copy(color = PremiumColors.OnSurface),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                PremiumButton(text = "Delete", onClick = onConfirm)
                PremiumButton(text = "Cancel", onClick = onCancel, variant = ButtonVariant.Ghost)
            }
        }
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

@Preview(name = "ProfileManagementScreen · ready", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun ProfileManagementPreview() {
    PremiumTvTheme {
        val profiles = listOf(
            ProfileDto("p1", "Alex", isKids = false, ageLimit = null, isDefault = true, hasPin = true, createdAt = "2026-04-13T12:00:00.000Z"),
            ProfileDto("p2", "Lily", isKids = true, ageLimit = 12, isDefault = false, hasPin = false, createdAt = "2026-04-13T12:00:00.000Z"),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumColors.BackgroundBase)
                .padding(LocalPremiumSpacing.current.pageGutter),
        ) {
            Ready(
                state = ProfileManagementUiState.Ready(profiles = profiles),
                onBack = {},
                onRename = { _, _ -> },
                onSetAgeLimit = { _, _ -> },
                onSetPin = { _, _ -> },
                onClearPin = {},
                onMakeDefault = {},
                onRequestDelete = {},
                onCancelDelete = {},
                onConfirmDelete = {},
                onCreate = { _, _, _, _ -> },
            )
        }
    }
}
