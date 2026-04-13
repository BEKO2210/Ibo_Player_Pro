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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Text
import com.premiumtvplayer.app.data.api.ProfileDto
import com.premiumtvplayer.app.ui.components.BootProgress
import com.premiumtvplayer.app.ui.components.ButtonVariant
import com.premiumtvplayer.app.ui.components.PremiumButton
import com.premiumtvplayer.app.ui.components.PremiumCard
import com.premiumtvplayer.app.ui.components.PremiumChip
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType

@Composable
fun ProfilePickerScreen(
    onProfileSelected: (ProfileDto) -> Unit,
    onAddProfile: () -> Unit,
    onManageProfiles: () -> Unit,
    viewModel: ProfilePickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalPremiumSpacing.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumColors.BackgroundBase)
            .padding(vertical = spacing.huge),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xl),
        ) {
            Text(
                text = "Who's watching?",
                style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
            )
            Text(
                text = "Choose a profile to continue.",
                style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
            )
            Spacer(modifier = Modifier.height(spacing.m))

            when (val s = state) {
                ProfilePickerUiState.Loading -> {
                    BootProgress()
                }
                is ProfilePickerUiState.Error -> {
                    Text(
                        text = s.message,
                        style = PremiumType.Body.copy(color = PremiumColors.DangerRed),
                    )
                    PremiumButton(text = "Try Again", onClick = viewModel::refresh)
                }
                is ProfilePickerUiState.Ready -> {
                    ProfileRow(
                        profiles = s.profiles,
                        canAddMore = s.canAddMore,
                        onProfileSelected = onProfileSelected,
                        onAddProfile = onAddProfile,
                    )
                    PremiumButton(
                        text = "Manage Profiles",
                        onClick = onManageProfiles,
                        variant = ButtonVariant.Ghost,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profiles: List<ProfileDto>,
    canAddMore: Boolean,
    onProfileSelected: (ProfileDto) -> Unit,
    onAddProfile: () -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    var focusedIndex by remember { mutableIntStateOf(-1) }
    val tileCount = profiles.size + if (canAddMore) 1 else 0

    TvLazyRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.rowGutter),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = spacing.pageGutter,
        ),
    ) {
        itemsIndexed(profiles) { index, profile ->
            val isAnyFocused = focusedIndex >= 0
            val isThisFocused = index == focusedIndex
            val veil = if (isAnyFocused && !isThisFocused) 0.4f else 0f
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.s),
                modifier = Modifier.onFocusChanged { state ->
                    if (state.isFocused || state.hasFocus) focusedIndex = index
                    else if (focusedIndex == index) focusedIndex = -1
                },
            ) {
                PremiumCard(
                    onClick = { onProfileSelected(profile) },
                    title = profile.name,
                    subtitle = profileSubtitle(profile),
                    unfocusedDim = veil,
                    aspectRatio = 1f,
                )
                if (profile.hasPin) {
                    PremiumChip(label = "PIN")
                }
            }
        }
        if (canAddMore) {
            val addIndex = profiles.size
            item {
                val isAnyFocused = focusedIndex >= 0
                val isThisFocused = addIndex == focusedIndex
                val veil = if (isAnyFocused && !isThisFocused) 0.4f else 0f
                Box(
                    modifier = Modifier.onFocusChanged { state ->
                        if (state.isFocused || state.hasFocus) focusedIndex = addIndex
                        else if (focusedIndex == addIndex) focusedIndex = -1
                    },
                ) {
                    PremiumCard(
                        onClick = onAddProfile,
                        title = "Add Profile",
                        subtitle = "+",
                        unfocusedDim = veil,
                        aspectRatio = 1f,
                    )
                }
            }
        }
        // Filler so the row never feels empty on a fresh account.
        if (tileCount == 0) {
            item {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(PremiumColors.SurfaceElevated)
                        .padding(spacing.xl),
                ) {
                    Text(
                        text = "No profiles yet. Add one to start watching.",
                        style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
                    )
                }
            }
        }
    }
}

private fun profileSubtitle(profile: ProfileDto): String = buildString {
    append(if (profile.isKids) "Kids" else "Adult")
    if (profile.isDefault) append(" · Default")
}

@Preview(name = "ProfilePickerScreen · empty", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun ProfilePickerEmptyPreview() {
    PremiumTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumColors.BackgroundBase),
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LocalPremiumSpacing.current.l),
            ) {
                Text(
                    text = "Who's watching?",
                    style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(LocalPremiumSpacing.current.m)) {
                    PremiumCard(onClick = {}, title = "Main", subtitle = "Adult · Default", aspectRatio = 1f)
                    PremiumCard(onClick = {}, title = "Kids", subtitle = "Kids · PG-12", aspectRatio = 1f)
                    PremiumCard(onClick = {}, title = "Add Profile", subtitle = "+", aspectRatio = 1f)
                }
            }
        }
    }
}
