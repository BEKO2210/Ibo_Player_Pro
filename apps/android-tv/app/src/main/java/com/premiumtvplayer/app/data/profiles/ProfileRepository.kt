package com.premiumtvplayer.app.data.profiles

import com.premiumtvplayer.app.data.api.ApiErrorMapper
import com.premiumtvplayer.app.data.api.PremiumPlayerApi
import com.premiumtvplayer.app.data.api.ProfileDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val api: PremiumPlayerApi,
) {
    suspend fun list(): List<ProfileDto> = wrap { api.listProfiles().profiles }

    private suspend inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}
