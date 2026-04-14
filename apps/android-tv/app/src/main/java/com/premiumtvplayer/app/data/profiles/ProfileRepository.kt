package com.premiumtvplayer.app.data.profiles

import com.premiumtvplayer.app.data.api.ApiErrorMapper
import com.premiumtvplayer.app.data.api.CreateProfileRequest
import com.premiumtvplayer.app.data.api.PremiumPlayerApi
import com.premiumtvplayer.app.data.api.ProfileDto
import com.premiumtvplayer.app.data.api.UpdateProfileRequest
import com.premiumtvplayer.app.data.api.VerifyPinRequest
import com.premiumtvplayer.app.data.api.VerifyPinResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val api: PremiumPlayerApi,
) {
    suspend fun list(): List<ProfileDto> = wrap { api.listProfiles().profiles }

    suspend fun create(
        name: String,
        isKids: Boolean,
        ageLimit: Int? = null,
        pin: String? = null,
        isDefault: Boolean? = null,
    ): ProfileDto = wrap {
        api.createProfile(
            CreateProfileRequest(
                name = name,
                isKids = isKids,
                ageLimit = ageLimit,
                pin = pin,
                isDefault = isDefault,
            ),
        ).profile
    }

    suspend fun update(
        id: String,
        name: String? = null,
        ageLimit: Int? = null,
        pin: String? = null,
        clearPin: Boolean? = null,
        isDefault: Boolean? = null,
    ): ProfileDto = wrap {
        api.updateProfile(
            id,
            UpdateProfileRequest(
                name = name,
                ageLimit = ageLimit,
                pin = pin,
                clearPin = clearPin,
                isDefault = isDefault,
            ),
        ).profile
    }

    suspend fun delete(id: String): Unit = wrap {
        val response = api.deleteProfile(id)
        if (!response.isSuccessful) {
            throw retrofit2.HttpException(response)
        }
    }

    suspend fun verifyPin(id: String, pin: String): VerifyPinResponse = wrap {
        api.verifyProfilePin(id, VerifyPinRequest(pin))
    }

    private suspend inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}
