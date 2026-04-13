package com.premiumtvplayer.app.data.home

import com.premiumtvplayer.app.data.api.SourceDto

/**
 * Domain-level models for the Home screen. Stable even as the underlying
 * endpoints (continue-watching, favorites, suggested) evolve across Runs
 * 15-16. These shapes are what [HomeRepository] returns and what
 * [HomeViewModel] renders.
 */

/** A single focusable tile — the unit of content on the Home screen. */
data class HomeTile(
    val id: String,
    val title: String,
    val subtitle: String?,
    /** When true, the card paints a "LIVE" chip over the artwork. */
    val isLive: Boolean = false,
    /** Optional artwork URL; null renders the default gradient backdrop. */
    val artworkUrl: String? = null,
    /** Stable deep-link identifier (source id + item id + kind). */
    val deeplink: HomeDeeplink,
)

sealed interface HomeDeeplink {
    data class Source(val sourceId: String) : HomeDeeplink
    data class VodItem(val sourceId: String, val itemId: String) : HomeDeeplink
    data class LiveChannel(val sourceId: String, val channelId: String) : HomeDeeplink
    data object AddSource : HomeDeeplink
}

/** A single horizontal row on the home screen. */
data class HomeRow(
    val key: RowKey,
    val title: String,
    val tiles: List<HomeTile>,
) {
    enum class RowKey { ContinueWatching, Favorites, Suggested, Sources }
}

/** Hero carousel entry — the top band of the home screen. */
data class HomeHero(
    val id: String,
    val title: String,
    val subtitle: String,
    val chips: List<String>,
    val deeplink: HomeDeeplink,
    val backgroundAccent: BackgroundAccent,
) {
    enum class BackgroundAccent { Blue, Cyan, Violet, Neutral }
}

/**
 * Top-level snapshot returned by [HomeRepository]. `sources.isEmpty()`
 * drives the UI into the EmptySource state; otherwise the screen renders
 * the hero + rows stack.
 */
data class HomeSnapshot(
    val sources: List<SourceDto>,
    val heroes: List<HomeHero>,
    val rows: List<HomeRow>,
)
