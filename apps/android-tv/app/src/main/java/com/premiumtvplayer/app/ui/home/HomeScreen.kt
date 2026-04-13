package com.premiumtvplayer.app.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Text
import com.premiumtvplayer.app.data.api.ProfileDto
import com.premiumtvplayer.app.data.api.SourceDto
import com.premiumtvplayer.app.data.home.HomeDeeplink
import com.premiumtvplayer.app.data.home.HomeHero
import com.premiumtvplayer.app.data.home.HomeRow
import com.premiumtvplayer.app.data.home.HomeSnapshot
import com.premiumtvplayer.app.data.home.HomeTile
import com.premiumtvplayer.app.ui.components.BootProgress
import com.premiumtvplayer.app.ui.components.ButtonVariant
import com.premiumtvplayer.app.ui.components.ChipStyle
import com.premiumtvplayer.app.ui.components.PremiumButton
import com.premiumtvplayer.app.ui.components.PremiumCard
import com.premiumtvplayer.app.ui.components.PremiumChip
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType

/**
 * Home. The app's brand ambassador — first thing a returning user sees.
 *
 * Visual composition:
 *   ┌──────────────────────────────────────────────────┐
 *   │ [logo]                                 [profile] │  HomeHeader
 *   ├──────────────────────────────────────────────────┤
 *   │ HERO CAROUSEL (3 cards, focus-veil between)      │
 *   │   chips · DISPLAY TITLE · subtitle · CTA         │
 *   ├──────────────────────────────────────────────────┤
 *   │ Continue Watching   ▸ card card card card …     │
 *   │ Favorites           ▸ card card card card …     │
 *   │ Suggested for you   ▸ card card card card …     │
 *   │ Your Sources        ▸ card card card …          │
 *   └──────────────────────────────────────────────────┘
 *
 * Empty-state swaps the hero + rows stack for [SourcePickerRail].
 *
 * D-pad behaviour: the first hero auto-receives focus on first
 * composition; vertical focus moves cleanly from hero to the first row
 * because each row is a TvLazyRow and Compose-TV's focus search handles
 * vertical traversal for us.
 */
@Composable
fun HomeScreen(
    onOpenDeeplink: (HomeDeeplink) -> Unit,
    onAddSource: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumColors.BackgroundBase),
    ) {
        when (val s = state) {
            HomeUiState.Loading -> LoadingState()
            is HomeUiState.EmptySource -> Column {
                HomeHeader(profile = s.profile)
                SourcePickerRail(
                    onAddSource = onAddSource,
                    onSignOut = onSignOut,
                )
            }
            is HomeUiState.Populated -> PopulatedHome(
                profile = s.profile,
                snapshot = s.snapshot,
                onOpenDeeplink = onOpenDeeplink,
            )
            is HomeUiState.Error -> ErrorState(
                message = s.message,
                onRetry = viewModel::refresh,
                onSignOut = onSignOut,
            )
        }
    }
}

@Composable
private fun PopulatedHome(
    profile: ProfileDto?,
    snapshot: HomeSnapshot,
    onOpenDeeplink: (HomeDeeplink) -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        HomeHeader(profile = profile)
        HeroCarousel(
            heroes = snapshot.heroes,
            onOpenDeeplink = onOpenDeeplink,
        )
        Spacer(modifier = Modifier.height(spacing.xl))
        snapshot.rows.forEachIndexed { index, row ->
            HomeRowSection(
                row = row,
                onOpenDeeplink = onOpenDeeplink,
            )
            if (index != snapshot.rows.lastIndex) {
                Spacer(modifier = Modifier.height(spacing.xl))
            }
        }
        Spacer(modifier = Modifier.height(spacing.huge))
    }
}

@Composable
private fun HeroCarousel(
    heroes: List<HomeHero>,
    onOpenDeeplink: (HomeDeeplink) -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    var focusedIndex by remember { mutableIntStateOf(-1) }

    // Auto-focus the first hero on first composition so the user lands
    // with focus already in the page — Apple TV / Bravia convention.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(heroes.firstOrNull()?.id) {
        if (heroes.isNotEmpty()) {
            runCatching { firstFocus.requestFocus() }
        }
    }

    TvLazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = spacing.pageGutter,
            vertical = spacing.m,
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing.l),
    ) {
        itemsIndexed(heroes) { index, hero ->
            val isAnyFocused = focusedIndex >= 0
            val isThisFocused = index == focusedIndex
            val veil = if (isAnyFocused && !isThisFocused) 0.4f else 0f
            Box(
                modifier = (if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                    .onFocusChanged { state ->
                        if (state.isFocused || state.hasFocus) focusedIndex = index
                        else if (focusedIndex == index) focusedIndex = -1
                    },
            ) {
                HeroCard(
                    hero = hero,
                    unfocusedDim = veil,
                    onClick = { onOpenDeeplink(hero.deeplink) },
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    hero: HomeHero,
    unfocusedDim: Float,
    onClick: () -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    val accent = when (hero.backgroundAccent) {
        HomeHero.BackgroundAccent.Blue -> PremiumColors.AccentBlueDeep
        HomeHero.BackgroundAccent.Cyan -> PremiumColors.AccentCyan
        HomeHero.BackgroundAccent.Violet -> PremiumColors.AccentViolet
        HomeHero.BackgroundAccent.Neutral -> PremiumColors.SurfaceFloating
    }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        PremiumCard(
            onClick = onClick,
            width = 760.dp,
            aspectRatio = 21f / 9f,
            unfocusedDim = unfocusedDim,
            backdrop = {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.35f),
                                    PremiumColors.SurfaceBase,
                                    PremiumColors.BackgroundBase,
                                ),
                            ),
                        )
                        .fillMaxSize(),
                )
            },
        )
        // Bravia / Apple TV–style caption sits directly under the card.
        HeroCaption(hero = hero)
    }
}

@Composable
private fun HeroCaption(hero: HomeHero) {
    val spacing = LocalPremiumSpacing.current
    Column(
        modifier = Modifier.padding(top = spacing.s),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            hero.chips.forEach { chip ->
                PremiumChip(
                    label = chip,
                    style = ChipStyle.Outline,
                    accent = PremiumColors.AccentCyan,
                )
            }
        }
        Text(
            text = hero.title,
            style = PremiumType.Headline.copy(color = PremiumColors.OnSurfaceHigh),
            maxLines = 1,
        )
        Text(
            text = hero.subtitle,
            style = PremiumType.BodySmall.copy(color = PremiumColors.OnSurfaceMuted),
            maxLines = 1,
        )
    }
}

@Composable
private fun HomeRowSection(
    row: HomeRow,
    onOpenDeeplink: (HomeDeeplink) -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    var focusedIndex by remember { mutableIntStateOf(-1) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = row.title,
            style = PremiumType.TitleLarge.copy(color = PremiumColors.OnSurface),
            modifier = Modifier.padding(horizontal = spacing.pageGutter),
        )
        Spacer(modifier = Modifier.height(spacing.m))
        TvLazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = spacing.pageGutter,
            ),
            horizontalArrangement = Arrangement.spacedBy(spacing.rowGutter),
        ) {
            itemsIndexed(row.tiles) { index, tile ->
                val isAnyFocused = focusedIndex >= 0
                val isThisFocused = index == focusedIndex
                val veil = if (isAnyFocused && !isThisFocused) 0.4f else 0f
                Box(
                    modifier = Modifier.onFocusChanged { state ->
                        if (state.isFocused || state.hasFocus) focusedIndex = index
                        else if (focusedIndex == index) focusedIndex = -1
                    },
                ) {
                    PremiumCard(
                        onClick = { onOpenDeeplink(tile.deeplink) },
                        title = tile.title,
                        subtitle = buildString {
                            if (tile.isLive) append("LIVE · ")
                            tile.subtitle?.let { append(it) }
                        }.ifBlank { tile.subtitle },
                        unfocusedDim = veil,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    val spacing = LocalPremiumSpacing.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            BootProgress()
            Text(
                text = "Loading your home…",
                style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
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
                PremiumButton(
                    text = "Sign Out",
                    onClick = onSignOut,
                    variant = ButtonVariant.Ghost,
                )
            }
        }
    }
}

// ── Previews ───────────────────────────────────────────────────────────

private val fixtureProfile = ProfileDto(
    id = "p1",
    name = "Alex",
    isKids = false,
    ageLimit = null,
    isDefault = true,
    hasPin = false,
    createdAt = "2026-04-13T12:00:00.000Z",
)

private fun fixtureSources() = listOf(
    SourceDto(
        id = "s1",
        name = "World TV Plus",
        kind = "m3u_plus_epg",
        isActive = true,
        validationStatus = "valid",
        itemCountEstimate = 480,
        createdAt = "2026-04-13T12:00:00.000Z",
    ),
    SourceDto(
        id = "s2",
        name = "Sports Pro",
        kind = "m3u",
        isActive = true,
        validationStatus = "valid",
        itemCountEstimate = 210,
        createdAt = "2026-04-13T12:00:00.000Z",
    ),
)

@Preview(name = "HomeScreen · populated", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun HomeScreenPopulatedPreview() {
    PremiumTvTheme {
        val sources = fixtureSources()
        val snapshot = HomeSnapshot(
            sources = sources,
            heroes = listOf(
                HomeHero(
                    id = "h1",
                    title = "World TV Plus",
                    subtitle = "Live TV · On Demand · EPG",
                    chips = listOf("M3U + EPG", "Active"),
                    deeplink = HomeDeeplink.Source("s1"),
                    backgroundAccent = HomeHero.BackgroundAccent.Blue,
                ),
                HomeHero(
                    id = "h2",
                    title = "Sports Pro",
                    subtitle = "Live TV · On Demand",
                    chips = listOf("M3U", "Active"),
                    deeplink = HomeDeeplink.Source("s2"),
                    backgroundAccent = HomeHero.BackgroundAccent.Cyan,
                ),
            ),
            rows = listOf(
                HomeRow(
                    key = HomeRow.RowKey.ContinueWatching,
                    title = "Continue Watching",
                    tiles = listOf(
                        HomeTile("t1", "Last night's highlight", "World TV Plus", deeplink = HomeDeeplink.VodItem("s1", "v1")),
                        HomeTile("t2", "Match Day · Live", "Sports Pro", isLive = true, deeplink = HomeDeeplink.LiveChannel("s2", "ch1")),
                        HomeTile("t3", "Season Finale", "World TV Plus", deeplink = HomeDeeplink.VodItem("s1", "v2")),
                    ),
                ),
                HomeRow(
                    key = HomeRow.RowKey.Suggested,
                    title = "Suggested for you",
                    tiles = listOf(
                        HomeTile("s1", "Tonight's headliner", "World TV Plus", deeplink = HomeDeeplink.VodItem("s1", "h1")),
                        HomeTile("s2", "Trending now", "Sports Pro", deeplink = HomeDeeplink.VodItem("s2", "h2")),
                    ),
                ),
            ),
        )
        Column(modifier = Modifier.fillMaxSize().background(PremiumColors.BackgroundBase)) {
            PopulatedHome(
                profile = fixtureProfile,
                snapshot = snapshot,
                onOpenDeeplink = {},
            )
        }
    }
}

@Preview(name = "HomeScreen · empty source", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun HomeScreenEmptyPreview() {
    PremiumTvTheme {
        Column(modifier = Modifier.fillMaxSize().background(PremiumColors.BackgroundBase)) {
            HomeHeader(profile = fixtureProfile)
            SourcePickerRail(onAddSource = {})
        }
    }
}

