package com.premiumtvplayer.app.data.sources

import com.premiumtvplayer.app.data.api.ApiErrorMapper
import com.premiumtvplayer.app.data.api.CreateSourceRequest
import com.premiumtvplayer.app.data.api.PremiumPlayerApi
import com.premiumtvplayer.app.data.api.SourceDto
import com.premiumtvplayer.app.data.api.UpdateSourceRequest
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

    suspend fun create(input: CreateSourceInput): SourceDto = wrap {
        api.createSource(
            CreateSourceRequest(
                profileId = input.profileId,
                name = input.name,
                kind = input.kind.apiValue,
                url = input.url,
                username = input.username?.ifBlank { null },
                password = input.password?.ifBlank { null },
                headers = input.headers?.takeIf { it.isNotEmpty() },
            ),
        ).source
    }

    suspend fun rename(id: String, name: String): SourceDto = wrap {
        api.updateSource(id, UpdateSourceRequest(name = name)).source
    }

    suspend fun setActive(id: String, isActive: Boolean): SourceDto = wrap {
        api.updateSource(id, UpdateSourceRequest(isActive = isActive)).source
    }

    suspend fun delete(id: String): Unit = wrap {
        val response = api.deleteSource(id)
        if (!response.isSuccessful) {
            // Re-throw as HttpException so ApiErrorMapper can decode the
            // stable ErrorEnvelope uniformly with all other paths.
            throw retrofit2.HttpException(response)
        }
    }

    private suspend inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}

/** V1 source kind enum mirroring the backend's `source_kind`. */
enum class SourceKind(val apiValue: String, val displayName: String, val description: String) {
    M3U("m3u", "M3U Playlist", "Channel list only — no programme guide."),
    XMLTV("xmltv", "XMLTV (EPG only)", "A programme guide you pair with an existing M3U source."),
    M3uPlusEpg("m3u_plus_epg", "M3U + EPG", "Channels and programme guide in a single provider.");

    companion object {
        fun fromApi(value: String): SourceKind? = entries.firstOrNull { it.apiValue == value }
    }
}

/** Normalized input for [SourceRepository.create]. */
data class CreateSourceInput(
    val profileId: String? = null,
    val name: String,
    val kind: SourceKind,
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val headers: Map<String, String>? = null,
)
