package com.premiumtvplayer.app.data.entitlement

import com.premiumtvplayer.app.data.api.ApiErrorMapper
import com.premiumtvplayer.app.data.api.EntitlementDto
import com.premiumtvplayer.app.data.api.PremiumPlayerApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntitlementRepository @Inject constructor(
    private val api: PremiumPlayerApi,
) {
    suspend fun status(): EntitlementDto = wrap { api.entitlementStatus().entitlement }

    suspend fun startTrial(): EntitlementDto = wrap { api.startTrial().entitlement }

    private suspend inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}
