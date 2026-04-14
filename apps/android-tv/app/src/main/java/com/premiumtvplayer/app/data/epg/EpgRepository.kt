package com.premiumtvplayer.app.data.epg

import com.premiumtvplayer.app.data.api.ApiErrorMapper
import com.premiumtvplayer.app.data.api.EpgChannelDto
import com.premiumtvplayer.app.data.api.EpgProgrammeDto
import com.premiumtvplayer.app.data.api.PremiumPlayerApi
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EPG data source — Run 16 wires this to the real API. The EPG worker
 * fills `epg_channels` + `epg_programs` from XMLTV; this repo reads that
 * data back through `/v1/epg/` and exposes the same `EpgBrowseSnapshot`
 * shape the Run 15 fixture used, so `EpgBrowseScreen` needs no change.
 */
@Singleton
class EpgRepository @Inject constructor(
    private val api: PremiumPlayerApi,
) {
    suspend fun browse(sourceId: String, now: Instant = Instant.now()): EpgBrowseSnapshot = try {
        val windowStart = now.truncatedTo(ChronoUnit.HOURS).minus(1, ChronoUnit.HOURS)
        val windowEnd = windowStart.plus(6, ChronoUnit.HOURS)

        val channels = api.listEpgChannels(sourceId).channels.map { it.toDomain() }
        val programmes = channels.associate { channel ->
            channel.id to api
                .listEpgProgrammes(
                    channelId = channel.id,
                    from = windowStart.toString(),
                    to = windowEnd.toString(),
                )
                .programmes
                .map { it.toDomain() }
        }
        EpgBrowseSnapshot(
            channels = channels,
            programmesByChannel = programmes,
            windowStart = windowStart,
            windowEnd = windowEnd,
        )
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}

private fun EpgChannelDto.toDomain(): EpgChannel = EpgChannel(
    id = id,
    sourceId = sourceId,
    displayName = displayName,
    iconUrl = iconUrl,
)

private fun EpgProgrammeDto.toDomain(): EpgProgramme = EpgProgramme(
    id = id,
    channelId = channelId,
    sourceId = sourceId,
    title = title,
    subtitle = subtitle,
    description = description,
    category = category,
    startsAt = Instant.parse(startsAt),
    endsAt = Instant.parse(endsAt),
)
