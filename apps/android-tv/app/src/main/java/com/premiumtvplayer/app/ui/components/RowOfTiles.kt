package com.premiumtvplayer.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Text
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType

/**
 * A focus-aware horizontal row of [PremiumCard]s.
 *
 * Implements the **focus-veil pattern** that defines premium TV UX:
 * when ANY tile in the row is focused, every other tile gets a 40%
 * dim veil. As focus moves left/right, the dim swaps fluidly. This is
 * the visual cue that lifts a Sony Bravia or Apple TV row above a
 * generic horizontal carousel.
 *
 * Generic over `T` so callers can render channels, VOD posters,
 * profiles, sources — anything that fits in a card.
 */
@Composable
fun <T> RowOfTiles(
    title: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    tileWidth: Dp = 280.dp,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = LocalPremiumSpacing.current.pageGutter,
    ),
    onItemClick: (index: Int, item: T) -> Unit = { _, _ -> },
    tile: @Composable (item: T, isAnyFocused: Boolean, isThisFocused: Boolean) -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    var focusedIndex by remember { mutableIntStateOf(-1) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = PremiumType.TitleLarge.copy(color = PremiumColors.OnSurface),
            modifier = Modifier.padding(horizontal = spacing.pageGutter),
        )
        Spacer(modifier = Modifier.height(spacing.m))

        TvLazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(spacing.rowGutter),
        ) {
            itemsIndexed(items) { index, item ->
                val isAnyFocused = focusedIndex >= 0
                val isThisFocused = index == focusedIndex
                Column(
                    modifier = Modifier
                        .onFocusChanged { state ->
                            if (state.isFocused || state.hasFocus) {
                                focusedIndex = index
                            } else if (focusedIndex == index) {
                                // Only clear if WE were the focused one and we
                                // lost focus — prevents flicker as focus moves
                                // between sibling tiles.
                                focusedIndex = -1
                            }
                        }
                ) {
                    @Suppress("UNUSED_PARAMETER")
                    val ignored = tileWidth // exposed for callers that want it
                    tile(item, isAnyFocused, isThisFocused)
                }
            }
        }
    }
}

@Preview(name = "RowOfTiles · with focus-veil", widthDp = 1280, heightDp = 360, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun RowOfTilesPreview() {
    PremiumTvTheme {
        Column(
            modifier = Modifier.padding(top = LocalPremiumSpacing.current.xl),
            verticalArrangement = Arrangement.spacedBy(LocalPremiumSpacing.current.l),
        ) {
            RowOfTiles(
                title = "Continue Watching",
                items = listOf("Hero One", "Cinematic Two", "Live Three", "Series Four", "Match Five"),
            ) { item, isAnyFocused, isThisFocused ->
                val veil = if (isAnyFocused && !isThisFocused) 0.4f else 0f
                PremiumCard(
                    onClick = {},
                    title = item,
                    subtitle = "Live · 4K HDR",
                    unfocusedDim = veil,
                )
            }
        }
    }
}
