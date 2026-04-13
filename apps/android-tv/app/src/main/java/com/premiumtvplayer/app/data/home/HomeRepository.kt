package com.premiumtvplayer.app.data.home

import com.premiumtvplayer.app.data.api.SourceDto
import com.premiumtvplayer.app.data.playback.ContinueWatchingRepository
import com.premiumtvplayer.app.data.sources.SourceRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates the data needed for the Home screen.
 *
 * Live in Run 16:
 *  - `sources`           via SourceRepository            → `/v1/sources`
 *  - `continue-watching` via ContinueWatchingRepository  → `/v1/continue-watching`
 *
 * Stubbed until their endpoints land:
 *  - `favorites`  → `/v1/favorites` (future run)
 *  - `suggested`  → editorial / algorithmic (future run)
 */
@Singleton
class HomeRepository @Inject constructor(
    private val sources: SourceRepository,
    private val continueWatching: ContinueWatchingRepository,
) {
    suspend fun snapshot(profileId: String?): HomeSnapshot {
        val live = sources.list(profileId)

        // Continue-watching row is per-profile. When no profile is selected
        // (null), the row is simply omitted — matches premium-TV behaviour
        // where the rail only appears after you pick a profile.
        val cwTiles: List<HomeTile> = if (profileId == null) {
            emptyList()
        } else {
            runCatching { continueWatching.list(profileId, limit = 10) }
                .getOrDefault(emptyList())
                .map { row ->
                    val source = live.firstOrNull { it.id == row.sourceId }
                    HomeTile(
                        id = "cw-${row.id}",
                        title = row.itemId,
                        subtitle = source?.name ?: row.itemType,
                        isLive = row.itemType == "live",
                        deeplink = if (row.itemType == "live" && row.sourceId != null) {
                            HomeDeeplink.LiveChannel(row.sourceId, row.itemId)
                        } else if (row.sourceId != null) {
                            HomeDeeplink.VodItem(row.sourceId, row.itemId)
                        } else {
                            HomeDeeplink.AddSource
                        },
                    )
                }
        }

        return HomeSnapshot(
            sources = live,
            heroes = heroes(live),
            rows = rows(live, cwTiles),
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

    private fun rows(sources: List<SourceDto>, cwTiles: List<HomeTile>): List<HomeRow> {
        if (sources.isEmpty()) return emptyList()
        val out = mutableListOf<HomeRow>()
        if (cwTiles.isNotEmpty()) {
            out += HomeRow(
                key = HomeRow.RowKey.ContinueWatching,
                title = "Continue Watching",
                tiles = cwTiles,
            )
        }
        out += HomeRow(
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
        )
        out += HomeRow(
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
        )
        return out
    }

    private fun kindChip(kind: String): String = when (kind) {
        "m3u" -> "M3U"
        "xmltv" -> "EPG"
        "m3u_plus_epg" -> "M3U + EPG"
        else -> kind.uppercase()
    }
}
