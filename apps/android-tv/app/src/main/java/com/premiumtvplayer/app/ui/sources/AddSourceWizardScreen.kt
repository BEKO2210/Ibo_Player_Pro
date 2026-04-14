package com.premiumtvplayer.app.ui.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.premiumtvplayer.app.data.sources.SourceKind
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
fun AddSourceWizardScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: AddSourceWizardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is WizardUiState.Done) onDone()
    }

    val editing = state as? WizardUiState.Editing ?: return
    val spacing = LocalPremiumSpacing.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumColors.BackgroundBase)
            .padding(horizontal = spacing.pageGutter, vertical = spacing.huge),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .widthIn(max = 960.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.l),
        ) {
            StepIndicator(current = editing.step)
            when (editing.step) {
                WizardStep.Kind -> KindStep(editing, viewModel)
                WizardStep.Endpoint -> EndpointStep(editing, viewModel)
                WizardStep.Preview -> PreviewStep(editing)
                WizardStep.Confirm -> ConfirmStep(editing)
            }
            if (editing.errorMessage != null) {
                ErrorBanner(editing.errorMessage!!)
            }
            Spacer(modifier = Modifier.height(spacing.s))
            WizardFooter(
                step = editing.step,
                submitting = editing.submitting,
                onNext = viewModel::next,
                onBack = viewModel::back,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun StepIndicator(current: WizardStep) {
    val spacing = LocalPremiumSpacing.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WizardStep.entries.forEachIndexed { index, step ->
            val active = step == current
            val passed = step.ordinal < current.ordinal
            val accent = when {
                active -> PremiumColors.AccentCyan
                passed -> PremiumColors.SuccessGreen
                else -> PremiumColors.OnSurfaceDim
            }
            PremiumChip(
                label = "${index + 1}. ${step.name}",
                style = if (active) ChipStyle.Filled else ChipStyle.Outline,
                accent = accent,
            )
        }
    }
}

@Composable
private fun KindStep(state: WizardUiState.Editing, vm: AddSourceWizardViewModel) {
    val spacing = LocalPremiumSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        Text(
            text = "What kind of source?",
            style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
        )
        Text(
            text = "Premium TV Player stays neutral — pick the provider format you've got.",
            style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
        )
        Spacer(modifier = Modifier.height(spacing.s))
        SourceKind.entries.forEach { kind ->
            KindRadioCard(
                kind = kind,
                selected = state.draft.kind == kind,
                onSelect = { vm.pickKind(kind) },
            )
        }
    }
}

@Composable
private fun KindRadioCard(
    kind: SourceKind,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    val borderColor = when {
        selected -> PremiumColors.AccentCyan
        focused -> PremiumColors.FocusBorder
        else -> PremiumColors.SurfaceHigh
    }
    val background = if (selected) PremiumColors.SurfaceFloating else PremiumColors.SurfaceElevated

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(width = if (focused || selected) 2.dp else 1.dp, color = borderColor, shape = shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onSelect)
            .padding(horizontal = spacing.l, vertical = spacing.m),
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = kind.displayName,
                style = PremiumType.Title.copy(color = PremiumColors.OnSurfaceHigh),
            )
            Text(
                text = kind.description,
                style = PremiumType.BodySmall.copy(color = PremiumColors.OnSurfaceMuted),
            )
        }
        if (selected) {
            PremiumChip(label = "Selected", accent = PremiumColors.AccentCyan)
        }
    }
}

@Composable
private fun EndpointStep(state: WizardUiState.Editing, vm: AddSourceWizardViewModel) {
    val spacing = LocalPremiumSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        Text(
            text = "Where does it live?",
            style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
        )
        Text(
            text = "Paste the playlist or EPG URL. Credentials are encrypted at rest (AES-256-GCM).",
            style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
        )
        Spacer(modifier = Modifier.height(spacing.s))
        PremiumTextField(
            value = state.draft.name,
            onValueChange = vm::onNameChange,
            label = "Source name",
            placeholder = "World TV Plus",
        )
        PremiumTextField(
            value = state.draft.url,
            onValueChange = vm::onUrlChange,
            label = "URL",
            placeholder = "https://provider.example/playlist.m3u",
            keyboardType = KeyboardType.Uri,
        )
        PremiumTextField(
            value = state.draft.username,
            onValueChange = vm::onUsernameChange,
            label = "Username (optional)",
            placeholder = "Optional — if your provider requires it",
        )
        PremiumTextField(
            value = state.draft.password,
            onValueChange = vm::onPasswordChange,
            label = "Password (optional)",
            placeholder = "Hidden once saved",
            isPassword = true,
            keyboardType = KeyboardType.Password,
        )
    }
}

@Composable
private fun PreviewStep(state: WizardUiState.Editing) {
    val spacing = LocalPremiumSpacing.current
    val preview = state.preview
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        Text(
            text = "Review",
            style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            state.draft.kind?.let {
                PremiumChip(label = it.displayName, style = ChipStyle.Outline, accent = PremiumColors.AccentCyan)
            }
            PremiumChip(label = "Encrypted", style = ChipStyle.Outline, accent = PremiumColors.SuccessGreen)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PremiumColors.SurfaceElevated)
                .padding(spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            PreviewRow("Name", state.draft.name)
            PreviewRow("URL", state.draft.url)
            if (state.draft.username.isNotBlank()) {
                PreviewRow("Username", state.draft.username)
            }
            PreviewRow("Password", if (state.draft.password.isBlank()) "—" else "•".repeat(state.draft.password.length.coerceAtMost(10)))
        }
        if (preview != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PremiumColors.SurfaceFloating)
                    .padding(spacing.l),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                PreviewRow("Estimated channels", preview.estimatedChannels.toString())
                PreviewRow("Estimated programmes", preview.estimatedProgrammes.toString())
                if (preview.warnings.isNotEmpty()) {
                    Text(
                        text = "Warnings:\n" + preview.warnings.joinToString(separator = "\n") { "• $it" },
                        style = PremiumType.BodySmall.copy(color = PremiumColors.WarningAmber),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    val spacing = LocalPremiumSpacing.current
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
        Text(
            text = label.uppercase(),
            style = PremiumType.LabelSmall.copy(color = PremiumColors.OnSurfaceMuted),
            modifier = Modifier.widthIn(min = 180.dp),
        )
        Text(
            text = value,
            style = PremiumType.Body.copy(color = PremiumColors.OnSurface),
        )
    }
}

@Composable
private fun ConfirmStep(state: WizardUiState.Editing) {
    val spacing = LocalPremiumSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        Text(
            text = "Ready to save?",
            style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
        )
        Text(
            text = "We'll persist \"${state.draft.name}\" to your account. " +
                "Credentials are encrypted before they reach disk.",
            style = PremiumType.Body.copy(color = PremiumColors.OnSurface),
        )
        if (state.submitting) {
            Text(
                text = "Saving…",
                style = PremiumType.Body.copy(color = PremiumColors.AccentCyan),
            )
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
        Text(
            text = message,
            style = PremiumType.BodySmall.copy(color = PremiumColors.DangerRed),
        )
    }
}

@Composable
private fun WizardFooter(
    step: WizardStep,
    submitting: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val nextLabel = when (step) {
            WizardStep.Confirm -> if (submitting) "Saving…" else "Save Source"
            else -> "Continue"
        }
        PremiumButton(text = nextLabel, onClick = onNext, enabled = !submitting)
        if (step != WizardStep.Kind) {
            PremiumButton(text = "Back", onClick = onBack, variant = ButtonVariant.Secondary, enabled = !submitting)
        }
        PremiumButton(text = "Cancel", onClick = onCancel, variant = ButtonVariant.Ghost, enabled = !submitting)
    }
}

@Preview(name = "AddSourceWizard · Kind", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun AddSourceWizardKindPreview() {
    PremiumTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumColors.BackgroundBase)
                .padding(LocalPremiumSpacing.current.pageGutter),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(LocalPremiumSpacing.current.l)) {
                StepIndicator(current = WizardStep.Kind)
                Text(
                    text = "What kind of source?",
                    style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
                )
                SourceKind.entries.forEach {
                    KindRadioCard(kind = it, selected = it == SourceKind.M3uPlusEpg, onSelect = {})
                }
            }
        }
    }
}

@Preview(name = "AddSourceWizard · Endpoint", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun AddSourceWizardEndpointPreview() {
    PremiumTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumColors.BackgroundBase)
                .padding(LocalPremiumSpacing.current.pageGutter),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(LocalPremiumSpacing.current.l)) {
                StepIndicator(current = WizardStep.Endpoint)
                PremiumTextField(value = "World TV Plus", onValueChange = {}, label = "Source name")
                PremiumTextField(
                    value = "https://provider.example/playlist.m3u",
                    onValueChange = {},
                    label = "URL",
                )
            }
        }
    }
}
