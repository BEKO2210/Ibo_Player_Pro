package com.premiumtvplayer.app.data.epg

import com.premiumtvplayer.app.data.api.SourceDto
import com.premiumtvplayer.app.data.sources.SourceRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EPG data source.
 *
 * **Run 15 scope:** returns deterministic fixture data derived from the
 * source list so the EpgBrowseScreen can be exercised end-to-end without
 * depending on a live EPG worker.
 *
 * **Run 16 replaces** the fixture with real `/v1/epg/channels` +
 * `/v1/epg/programmes` calls served by the EPG worker. The return shape
 * ([EpgBrowseSnapshot]) stays fixed so the UI layer never changes.
 */
@Singleton
class EpgRepository @Inject constructor(
    private val sources: SourceRepository,
) {
    suspend fun browse(sourceId: String, now: Instant = Instant.now()): EpgBrowseSnapshot {
        val source = sources.list().firstOrNull { it.id == sourceId }
            ?: error("Source $sourceId not found for EPG browse.")
        return fixture(source, now)
    }

    private fun fixture(source: SourceDto, now: Instant): EpgBrowseSnapshot {
        val start = now.truncatedTo(ChronoUnit.HOURS)
        val end = start.plus(6, ChronoUnit.HOURS)

        // 6 synthetic channels per source — enough to exercise the grid.
        val channels = (1..6).map { idx ->
            EpgChannel(
                id = "${source.id}-ch$idx",
                sourceId = source.id,
                displayName = "${source.name} · Channel $idx",
            )
        }
        val programmes = channels.associate { channel ->
            channel.id to (0 until 12).map { slot ->
                val slotStart = start.plus((slot * 30).toLong(), ChronoUnit.MINUTES)
                val slotEnd = slotStart.plus(30, ChronoUnit.MINUTES)
                EpgProgramme(
                    id = "${channel.id}-$slot",
                    channelId = channel.id,
                    sourceId = source.id,
                    title = programTitleFor(slot),
                    subtitle = if (slot % 2 == 0) null else "Episode ${slot + 1}",
                    description = "Premium-tier placeholder programme. " +
                        "Real XMLTV-backed data lands in Run 16.",
                    category = categoryFor(slot),
                    startsAt = slotStart,
                    endsAt = slotEnd,
                )
            }
        }
        return EpgBrowseSnapshot(
            channels = channels,
            programmesByChannel = programmes,
            windowStart = start,
            windowEnd = end,
        )
    }

    private fun programTitleFor(slot: Int): String = when (slot % 6) {
        0 -> "Morning News"
        1 -> "Cinematic Feature"
        2 -> "Live Match"
        3 -> "Documentary"
        4 -> "Prime Time"
        else -> "Late Night"
    }

    private fun categoryFor(slot: Int): String = when (slot % 6) {
        0 -> "News"
        1 -> "Movies"
        2 -> "Sport"
        3 -> "Documentary"
        4 -> "Entertainment"
        else -> "Late Night"
    }
}
