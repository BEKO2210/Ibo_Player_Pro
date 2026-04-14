package com.premiumtvplayer.app.data.devices

import com.premiumtvplayer.app.data.api.ApiErrorMapper
import com.premiumtvplayer.app.data.api.DeviceDto
import com.premiumtvplayer.app.data.api.PremiumPlayerApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DevicesRepository @Inject constructor(
    private val api: PremiumPlayerApi,
) {
    suspend fun list(): List<DeviceDto> = wrap { api.listDevices().devices }

    suspend fun revoke(id: String): DeviceDto = wrap { api.revokeDevice(id).device }

    private suspend inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}
