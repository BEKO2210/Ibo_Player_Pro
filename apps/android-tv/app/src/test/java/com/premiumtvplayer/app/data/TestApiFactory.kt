package com.premiumtvplayer.app.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.premiumtvplayer.app.data.api.PremiumPlayerApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit

/**
 * Plain-old-unit-test helper. Wires a [PremiumPlayerApi] against an
 * in-process [MockWebServer] so we can exercise the full
 * request/response pipeline without a network stack or Hilt graph.
 */
object TestApiFactory {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun build(server: MockWebServer): PremiumPlayerApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(PremiumPlayerApi::class.java)
    }
}
