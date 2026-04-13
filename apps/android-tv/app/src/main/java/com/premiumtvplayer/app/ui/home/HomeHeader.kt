package com.premiumtvplayer.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Text
import com.premiumtvplayer.app.data.api.ProfileDto
import com.premiumtvplayer.app.ui.components.BrandLogo
import com.premiumtvplayer.app.ui.components.BrandLogoSize
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType

/**
 * Top bar on the Home screen. Brand mark on the left (inline size —
 * unobtrusive but always visible), profile indicator on the right
 * (initial-avatar + name). No D-pad focus — the header is always
 * context; focus belongs to the hero / rows below.
 */
@Composable
fun HomeHeader(
    profile: ProfileDto?,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalPremiumSpacing.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.pageGutter, vertical = spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandLogo(size = BrandLogoSize.Inline)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
        if (profile != null) {
            ProfileIndicator(profile = profile)
        }
    }
}

@Composable
private fun ProfileIndicator(profile: ProfileDto) {
    val spacing = LocalPremiumSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = profile.name,
                style = PremiumType.Label.copy(color = PremiumColors.OnSurface),
            )
            Text(
                text = if (profile.isKids) "Kids" else "Adult",
                style = PremiumType.LabelSmall.copy(color = PremiumColors.OnSurfaceDim),
            )
        }
        AvatarCircle(profile = profile)
    }
}

@Composable
private fun AvatarCircle(profile: ProfileDto) {
    val initial = profile.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val gradient = Brush.linearGradient(
        colors = listOf(PremiumColors.AccentCyan, PremiumColors.AccentBlueDeep),
    )
    Box(
        modifier = Modifier
            .size(44.dp())
            .clip(CircleShape)
            .background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = PremiumType.Title.copy(color = PremiumColors.OnSurfaceHigh),
        )
    }
}

// Small helper so we can write `44.dp()` at call sites without pulling the
// raw dp import into every helper.
@Suppress("NOTHING_TO_INLINE")
private inline fun Int.dp(): androidx.compose.ui.unit.Dp = with(androidx.compose.ui.unit.Dp.Companion) {
    androidx.compose.ui.unit.Dp(value = this@dp.toFloat())
}

@Preview(name = "HomeHeader · with profile", widthDp = 1280, heightDp = 120, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun HomeHeaderPreview() {
    PremiumTvTheme {
        HomeHeader(
            profile = ProfileDto(
                id = "p1",
                name = "Alex",
                isKids = false,
                ageLimit = null,
                isDefault = true,
                hasPin = false,
                createdAt = "2026-04-13T12:00:00.000Z",
            ),
        )
    }
}
