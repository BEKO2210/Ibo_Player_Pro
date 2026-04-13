package com.premiumtvplayer.app.data.playback

import com.premiumtvplayer.app.data.api.ApiErrorMapper
import com.premiumtvplayer.app.data.api.ContinueWatchingRowDto
import com.premiumtvplayer.app.data.api.PremiumPlayerApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContinueWatchingRepository @Inject constructor(
    private val api: PremiumPlayerApi,
) {
    suspend fun list(profileId: String, limit: Int = 20): List<ContinueWatchingRowDto> = try {
        api.listContinueWatching(profileId, limit).items
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}
