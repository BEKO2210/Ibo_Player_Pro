package com.premiumtvplayer.app.data.home

import com.premiumtvplayer.app.data.api.SourceDto
import com.premiumtvplayer.app.data.sources.SourceRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates the data needed for the Home screen.
 *
 * Live endpoints today:
 *  - `sources` via [SourceRepository] → `/v1/sources`
 *
 * Stubbed until the matching endpoints land:
 *  - `continue-watching` → `/v1/continue-watching` (Run 16)
 *  - `favorites`         → `/v1/favorites` (Run 15)
 *  - `suggested`         → derived from sources + EPG worker (Run 15-16)
 *
 * The stub data is derived deterministically from the live source list
 * so that the UI always looks populated when sources exist, and cleanly
 * surfaces the empty-state when they don't — without a feature-flag
 * detour.
 */
@Singleton
class HomeRepository @Inject constructor(
    private val sources: SourceRepository,
) {
    suspend fun snapshot(profileId: String?): HomeSnapshot {
        val live = sources.list(profileId)
        return HomeSnapshot(
            sources = live,
            heroes = heroes(live),
            rows = rows(live),
        )
    }

    private fun heroes(sources: List<SourceDto>): List<HomeHero> {
        if (sources.isEmpty()) {
            return listOf(
                HomeHero(
                    id = "onboarding-hero",
                    title = "Your premium TV starts with a source",
                    subtitle = "Add your M3U or XMLTV provider to fill this home with " +
                        "live channels, on-demand titles, and a proper EPG.",
                    chips = listOf("Add Source"),
                    deeplink = HomeDeeplink.AddSource,
                    backgroundAccent = HomeHero.BackgroundAccent.Blue,
                ),
            )
        }
        // Build a 3-card hero deck from the first few sources. Real artwork
        // + curated heroes land in Run 15-16 via an editorial endpoint.
        val palette = listOf(
            HomeHero.BackgroundAccent.Blue,
            HomeHero.BackgroundAccent.Cyan,
            HomeHero.BackgroundAccent.Violet,
        )
        return sources.take(3).mapIndexed { index, src ->
            HomeHero(
                id = "hero-${src.id}",
                title = src.name,
                subtitle = "Live TV · On Demand · EPG",
                chips = listOf(kindChip(src.kind), "Active".takeIf { src.isActive } ?: "Paused"),
                deeplink = HomeDeeplink.Source(src.id),
                backgroundAccent = palette[index % palette.size],
            )
        }
    }

    private fun rows(sources: List<SourceDto>): List<HomeRow> {
        if (sources.isEmpty()) return emptyList()
        return listOf(
            HomeRow(
                key = HomeRow.RowKey.ContinueWatching,
                title = "Continue Watching",
                tiles = sources.flatMap { src ->
                    listOf(
                        HomeTile(
                            id = "${src.id}-cw1",
                            title = "Last night's highlight",
                            subtitle = src.name,
                            deeplink = HomeDeeplink.VodItem(src.id, "vod-1"),
                        ),
                        HomeTile(
                            id = "${src.id}-cw2",
                            title = "Live feed · Match Day",
                            subtitle = src.name,
                            isLive = true,
                            deeplink = HomeDeeplink.LiveChannel(src.id, "ch-1"),
                        ),
                    )
                }.take(6),
            ),
            HomeRow(
                key = HomeRow.RowKey.Favorites,
                title = "Favorites",
                tiles = sources.map { src ->
                    HomeTile(
                        id = "${src.id}-fav",
                        title = "Starred channel",
                        subtitle = src.name,
                        isLive = src.kind != "m3u_plus_epg",
                        deeplink = HomeDeeplink.LiveChannel(src.id, "fav"),
                    )
                },
            ),
            HomeRow(
                key = HomeRow.RowKey.Suggested,
                title = "Suggested for you",
                tiles = sources.flatMap { src ->
                    listOf(
                        HomeTile(
                            id = "${src.id}-sug1",
                            title = "Tonight's headliner",
                            subtitle = src.name,
                            deeplink = HomeDeeplink.VodItem(src.id, "sug-1"),
                        ),
                        HomeTile(
                            id = "${src.id}-sug2",
                            title = "Trending now",
                            subtitle = src.name,
                            deeplink = HomeDeeplink.VodItem(src.id, "sug-2"),
                        ),
                    )
                }.take(8),
            ),
            HomeRow(
                key = HomeRow.RowKey.Sources,
                title = "Your Sources",
                tiles = sources.map { src ->
                    HomeTile(
                        id = "source-${src.id}",
                        title = src.name,
                        subtitle = "${kindChip(src.kind)} · ${if (src.isActive) "Active" else "Paused"}",
                        deeplink = HomeDeeplink.Source(src.id),
                    )
                },
            ),
        )
    }

    private fun kindChip(kind: String): String = when (kind) {
        "m3u" -> "M3U"
        "xmltv" -> "EPG"
        "m3u_plus_epg" -> "M3U + EPG"
        else -> kind.uppercase()
    }
}
