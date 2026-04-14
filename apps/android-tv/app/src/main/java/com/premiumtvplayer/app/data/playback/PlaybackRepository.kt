package com.premiumtvplayer.app.data.playback

import com.premiumtvplayer.app.data.api.ApiErrorMapper
import com.premiumtvplayer.app.data.api.HeartbeatRequest
import com.premiumtvplayer.app.data.api.PlaybackSessionDto
import com.premiumtvplayer.app.data.api.PremiumPlayerApi
import com.premiumtvplayer.app.data.api.StartPlaybackRequest
import com.premiumtvplayer.app.data.api.StopPlaybackRequest
import javax.inject.Inject
import javax.inject.Singleton

/** Client-side mirror of the Prisma `PlaybackState` enum. */
enum class PlaybackStateValue(val apiValue: String) {
    Starting("starting"),
    Playing("playing"),
    Paused("paused"),
    Buffering("buffering"),
    Stopped("stopped"),
    Error("error"),
    ;
}

/** Client-side mirror of the `item_type` union. */
enum class PlaybackItemType(val apiValue: String) {
    Live("live"),
    Vod("vod"),
    SeriesEpisode("series_episode"),
}

@Singleton
class PlaybackRepository @Inject constructor(
    private val api: PremiumPlayerApi,
) {
    suspend fun start(
        profileId: String,
        sourceId: String,
        itemId: String,
        itemType: PlaybackItemType,
        deviceId: String? = null,
    ): PlaybackSessionDto = wrap {
        api.startPlayback(
            StartPlaybackRequest(
                profileId = profileId,
                sourceId = sourceId,
                itemId = itemId,
                itemType = itemType.apiValue,
                deviceId = deviceId,
            ),
        ).session
    }

    suspend fun heartbeat(
        sessionId: String,
        positionSeconds: Int,
        state: PlaybackStateValue,
        durationSeconds: Int? = null,
    ): PlaybackSessionDto = wrap {
        api.heartbeat(
            HeartbeatRequest(
                sessionId = sessionId,
                positionSeconds = positionSeconds,
                state = state.apiValue,
                durationSeconds = durationSeconds,
            ),
        ).session
    }

    suspend fun stop(
        sessionId: String,
        finalPositionSeconds: Int,
        durationSeconds: Int? = null,
        completed: Boolean = false,
    ): PlaybackSessionDto = wrap {
        api.stopPlayback(
            StopPlaybackRequest(
                sessionId = sessionId,
                finalPositionSeconds = finalPositionSeconds,
                durationSeconds = durationSeconds,
                completed = completed,
            ),
        ).session
    }

    private suspend inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}
