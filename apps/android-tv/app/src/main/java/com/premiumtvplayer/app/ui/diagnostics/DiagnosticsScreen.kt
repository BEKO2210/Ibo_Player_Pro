package com.premiumtvplayer.app.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.premiumtvplayer.app.R
import com.premiumtvplayer.app.data.diagnostics.ErrorLogBuffer
import com.premiumtvplayer.app.data.diagnostics.HealthSnapshot
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
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
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
            DiagnosticsUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BootProgress() }
            is DiagnosticsUiState.Ready -> Ready(state = s, onBack = onBack, onRefresh = viewModel::refresh)
        }
    }
}

@Composable
private fun Ready(
    state: DiagnosticsUiState.Ready,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .widthIn(max = 1080.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(spacing.l),
    ) {
        Text(
            text = stringResource(R.string.diagnostics_title),
            style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
        )
        Text(
            text = stringResource(R.string.diagnostics_subtitle),
            style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            PremiumButton(
                text = if (state.refreshing) stringResource(R.string.common_loading) else stringResource(R.string.diagnostics_refresh),
                onClick = onRefresh,
                enabled = !state.refreshing,
            )
            PremiumButton(
                text = stringResource(R.string.common_back),
                onClick = onBack,
                variant = ButtonVariant.Ghost,
            )
        }

        InfoCard(state = state)
        HealthCard(snapshot = state.health)
        ErrorsCard(entries = state.errors)
    }
}

@Composable
private fun InfoCard(state: DiagnosticsUiState.Ready) {
    val spacing = LocalPremiumSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PremiumColors.SurfaceElevated)
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            PremiumChip(label = "${state.info.versionName} · build ${state.info.versionCode}", accent = PremiumColors.AccentCyan)
        }
        DiagnosticsRow(stringResource(R.string.diagnostics_build_label), "${state.info.versionName} (${state.info.versionCode})")
        DiagnosticsRow(stringResource(R.string.diagnostics_api_base), state.info.apiBaseUrl)
        DiagnosticsRow(stringResource(R.string.diagnostics_firebase_project), state.info.firebaseProjectId)
        DiagnosticsRow("Application id", state.info.firebaseApplicationId)
    }
}

@Composable
private fun HealthCard(snapshot: HealthSnapshot?) {
    val spacing = LocalPremiumSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PremiumColors.SurfaceElevated)
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.diagnostics_health),
                style = PremiumType.TitleLarge.copy(color = PremiumColors.OnSurfaceHigh),
                modifier = Modifier.weight(1f),
            )
            val ok = snapshot?.ok == true
            PremiumChip(
                label = if (ok) stringResource(R.string.diagnostics_health_ok)
                else stringResource(R.string.diagnostics_health_down),
                style = ChipStyle.Outline,
                accent = if (ok) PremiumColors.SuccessGreen else PremiumColors.DangerRed,
            )
        }
        DiagnosticsRow("status", snapshot?.status ?: "—")
        DiagnosticsRow("database", snapshot?.database ?: "—")
        DiagnosticsRow("redis", snapshot?.redis ?: "—")
        DiagnosticsRow("service", snapshot?.service ?: "—")
        if (snapshot?.ok == false && snapshot.rawBody.isNotBlank()) {
            Text(
                text = snapshot.rawBody.take(280),
                style = PremiumType.LabelSmall.copy(color = PremiumColors.OnSurfaceDim),
            )
        }
    }
}

@Composable
private fun ErrorsCard(entries: List<ErrorLogBuffer.Entry>) {
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
            text = stringResource(R.string.diagnostics_recent_errors),
            style = PremiumType.TitleLarge.copy(color = PremiumColors.OnSurfaceHigh),
        )
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.diagnostics_empty_errors),
                style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
            )
        } else {
            entries.forEach { entry ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalAlignment = Alignment.Top) {
                    PremiumChip(
                        label = entry.code ?: entry.source,
                        style = ChipStyle.Outline,
                        accent = PremiumColors.OnSurfaceMuted,
                    )
                    Column {
                        Text(
                            text = entry.message,
                            style = PremiumType.BodySmall.copy(color = PremiumColors.OnSurface),
                        )
                        Text(
                            text = entry.at.toString(),
                            style = PremiumType.LabelSmall.copy(color = PremiumColors.OnSurfaceDim),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsRow(label: String, value: String) {
    val spacing = LocalPremiumSpacing.current
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
        Text(
            text = label.uppercase(),
            style = PremiumType.LabelSmall.copy(color = PremiumColors.OnSurfaceMuted),
            modifier = Modifier.widthIn(min = 220.dp),
        )
        Text(
            text = value,
            style = PremiumType.Body.copy(color = PremiumColors.OnSurface),
        )
    }
}

@Preview(name = "DiagnosticsScreen · ready", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun DiagnosticsPreview() {
    PremiumTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumColors.BackgroundBase)
                .padding(LocalPremiumSpacing.current.pageGutter),
        ) {
            Ready(
                state = DiagnosticsUiState.Ready(
                    info = DiagnosticsInfo(
                        versionName = "0.1.0",
                        versionCode = 1,
                        apiBaseUrl = "http://10.0.2.2:3000/v1/",
                        firebaseProjectId = "premium-tv-player-prod",
                        firebaseApplicationId = "1:0:android:0",
                    ),
                    health = HealthSnapshot(ok = true, status = "ok", database = "up", redis = "up", service = "premium-player-api", rawBody = ""),
                    errors = emptyList(),
                ),
                onBack = {},
                onRefresh = {},
            )
        }
    }
}
