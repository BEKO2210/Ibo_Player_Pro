package com.premiumtvplayer.app.data.epg

import java.time.Instant

/**
 * Domain-level EPG models used by the `EpgBrowseScreen`. Deliberately
 * platform-free so the data source can swap between the Run 15 fixture
 * and the Run 16 `/v1/epg/*` endpoints without any screen-side change.
 */
data class EpgChannel(
    val id: String,
    val sourceId: String,
    val displayName: String,
    val iconUrl: String? = null,
)

data class EpgProgramme(
    val id: String,
    val channelId: String,
    val sourceId: String,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val category: String? = null,
    val startsAt: Instant,
    val endsAt: Instant,
)

/** Snapshot returned by `EpgRepository.browse(...)`. */
data class EpgBrowseSnapshot(
    val channels: List<EpgChannel>,
    /** Keyed by `channelId` — parallel structure to [channels] for O(1) lookup. */
    val programmesByChannel: Map<String, List<EpgProgramme>>,
    /** UTC window covered by the snapshot. */
    val windowStart: Instant,
    val windowEnd: Instant,
)
