package com.premiumtvplayer.app.ui.sources

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.premiumtvplayer.app.data.api.SourceDto
import com.premiumtvplayer.app.data.sources.SourceKind
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
fun SourceManagementScreen(
    onAddSource: () -> Unit,
    onOpenEpg: (SourceDto) -> Unit,
    onBack: () -> Unit,
    viewModel: SourceManagementViewModel = hiltViewModel(),
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
            SourceManagementUiState.Loading -> LoadingState()
            is SourceManagementUiState.Ready -> ReadyList(
                state = s,
                onAddSource = onAddSource,
                onOpenEpg = onOpenEpg,
                onBack = onBack,
                onTogglePaused = viewModel::togglePaused,
                onRequestDelete = { viewModel.requestDelete(it.id) },
                onCancelDelete = viewModel::cancelDelete,
                onConfirmDelete = viewModel::confirmDelete,
            )
            is SourceManagementUiState.Error -> ErrorState(s.message, viewModel::refresh, onBack)
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BootProgress()
    }
}

@Composable
private fun ReadyList(
    state: SourceManagementUiState.Ready,
    onAddSource: () -> Unit,
    onOpenEpg: (SourceDto) -> Unit,
    onBack: () -> Unit,
    onTogglePaused: (SourceDto) -> Unit,
    onRequestDelete: (SourceDto) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    Column(
        modifier = Modifier.widthIn(max = 1080.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.l),
    ) {
        Text(
            text = "Your sources",
            style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
        )
        Text(
            text = "Add, rename, pause, or remove the providers that power your home.",
            style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PremiumButton(text = "Add Source", onClick = onAddSource)
            PremiumButton(text = "Back", onClick = onBack, variant = ButtonVariant.Ghost)
        }
        if (state.lastError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PremiumColors.DangerRed.copy(alpha = 0.12f))
                    .padding(horizontal = spacing.m, vertical = spacing.s),
            ) {
                Text(
                    text = state.lastError,
                    style = PremiumType.BodySmall.copy(color = PremiumColors.DangerRed),
                )
            }
        }
        Spacer(modifier = Modifier.height(spacing.s))
        if (state.sources.isEmpty()) {
            EmptyState(onAddSource)
        } else {
            state.sources.forEach { source ->
                SourceRow(
                    source = source,
                    busy = state.busyId == source.id,
                    onOpenEpg = { onOpenEpg(source) },
                    onTogglePaused = { onTogglePaused(source) },
                    onRequestDelete = { onRequestDelete(source) },
                )
            }
        }
    }

    if (state.confirmingDeleteId != null) {
        val target = state.sources.firstOrNull { it.id == state.confirmingDeleteId }
        if (target != null) {
            ConfirmDeleteOverlay(
                source = target,
                onCancel = onCancelDelete,
                onConfirm = onConfirmDelete,
            )
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceDto,
    busy: Boolean,
    onOpenEpg: () -> Unit,
    onTogglePaused: () -> Unit,
    onRequestDelete: () -> Unit,
) {
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
            Text(
                text = source.name,
                style = PremiumType.Title.copy(color = PremiumColors.OnSurfaceHigh),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                PremiumChip(
                    label = SourceKind.fromApi(source.kind)?.displayName ?: source.kind,
                    style = ChipStyle.Outline,
                    accent = PremiumColors.AccentCyan,
                )
                PremiumChip(
                    label = if (source.isActive) "Active" else "Paused",
                    style = ChipStyle.Outline,
                    accent = if (source.isActive) PremiumColors.SuccessGreen else PremiumColors.WarningAmber,
                )
                if (source.validationStatus != "valid") {
                    PremiumChip(
                        label = source.validationStatus,
                        style = ChipStyle.Outline,
                        accent = PremiumColors.OnSurfaceMuted,
                    )
                }
                source.itemCountEstimate?.let {
                    PremiumChip(
                        label = "$it items",
                        style = ChipStyle.Outline,
                        accent = PremiumColors.OnSurfaceMuted,
                    )
                }
            }
        }
        PremiumButton(text = "EPG", onClick = onOpenEpg, variant = ButtonVariant.Secondary, enabled = !busy)
        PremiumButton(
            text = if (source.isActive) "Pause" else "Resume",
            onClick = onTogglePaused,
            variant = ButtonVariant.Ghost,
            enabled = !busy,
        )
        PremiumButton(
            text = "Delete",
            onClick = onRequestDelete,
            variant = ButtonVariant.Ghost,
            enabled = !busy,
        )
    }
}

@Composable
private fun EmptyState(onAddSource: () -> Unit) {
    val spacing = LocalPremiumSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PremiumColors.SurfaceElevated)
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Text(
            text = "You haven't added any sources yet.",
            style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
        )
        PremiumButton(text = "Add Source", onClick = onAddSource)
    }
}

@Composable
private fun ConfirmDeleteOverlay(
    source: SourceDto,
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
                text = "Delete \"${source.name}\"?",
                style = PremiumType.TitleLarge.copy(color = PremiumColors.OnSurfaceHigh),
            )
            Text(
                text = "This removes the source from your account across every device. " +
                    "You can re-add it later — but any Continue-Watching rows linked to it " +
                    "will disappear.",
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
private fun ErrorState(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val spacing = LocalPremiumSpacing.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.l),
        ) {
            Text(
                text = message,
                style = PremiumType.Body.copy(color = PremiumColors.DangerRed),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                PremiumButton(text = "Try Again", onClick = onRetry)
                PremiumButton(text = "Back", onClick = onBack, variant = ButtonVariant.Ghost)
            }
        }
    }
}

@Preview(name = "SourceManagement · ready", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun SourceManagementPreview() {
    PremiumTvTheme {
        val sources = listOf(
            SourceDto(id = "s1", name = "World TV Plus", kind = "m3u_plus_epg", isActive = true, validationStatus = "valid", itemCountEstimate = 480, createdAt = "2026-04-13T12:00:00.000Z"),
            SourceDto(id = "s2", name = "Sports Pro", kind = "m3u", isActive = false, validationStatus = "valid", itemCountEstimate = 210, createdAt = "2026-04-13T12:00:00.000Z"),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumColors.BackgroundBase)
                .padding(LocalPremiumSpacing.current.pageGutter),
        ) {
            ReadyList(
                state = SourceManagementUiState.Ready(sources = sources),
                onAddSource = {},
                onOpenEpg = {},
                onBack = {},
                onTogglePaused = {},
                onRequestDelete = {},
                onCancelDelete = {},
                onConfirmDelete = {},
            )
        }
    }
}
