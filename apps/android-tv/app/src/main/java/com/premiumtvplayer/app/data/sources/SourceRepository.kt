package com.premiumtvplayer.app.data.sources

import com.premiumtvplayer.app.data.api.ApiErrorMapper
import com.premiumtvplayer.app.data.api.PremiumPlayerApi
import com.premiumtvplayer.app.data.api.SourceDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceRepository @Inject constructor(
    private val api: PremiumPlayerApi,
) {
    /**
     * List sources for the given profile (null → account-scoped, i.e. all
     * sources not yet pinned to a profile). Used by the Home screen to
     * decide between the empty-source rail and a populated grid.
     */
    suspend fun list(profileId: String? = null): List<SourceDto> = wrap {
        api.listSources(profileId).sources
    }

    private suspend inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}
