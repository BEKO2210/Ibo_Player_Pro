package com.premiumtvplayer.app.data.diagnostics

import com.premiumtvplayer.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class HealthSnapshot(
    val ok: Boolean,
    val status: String?,
    val database: String?,
    val redis: String?,
    val service: String?,
    val rawBody: String,
)

/**
 * Probes `GET /health` on the backend. Not part of the Retrofit service
 * because `/health` lives at the service root (not under `/v1`), and we
 * want it to bypass the Bearer interceptor so it works even when the
 * Firebase token is stale.
 */
@Singleton
class HealthClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns a [HealthSnapshot] describing the server's `/health` output.
     * Base URL is derived from `BuildConfig.API_BASE_URL` by stripping the
     * `/v1/` suffix — the endpoint is intentionally unversioned.
     */
    suspend fun fetch(): HealthSnapshot = withContext(Dispatchers.IO) {
        val healthUrl = BuildConfig.API_BASE_URL
            .trimEnd('/')
            .removeSuffix("/v1")
            .trimEnd('/') + "/health"
        val request = Request.Builder().url(healthUrl).get().build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val parsed = runCatching { json.parseToJsonElement(body).jsonObject() }.getOrNull()
                HealthSnapshot(
                    ok = response.isSuccessful,
                    status = parsed?.stringOrNull("status"),
                    database = parsed?.objectOrNull("info")?.objectOrNull("database")?.stringOrNull("status"),
                    redis = parsed?.objectOrNull("info")?.objectOrNull("redis")?.stringOrNull("status"),
                    service = parsed?.objectOrNull("info")?.objectOrNull("service")?.stringOrNull("name"),
                    rawBody = body,
                )
            }
        } catch (t: Throwable) {
            HealthSnapshot(
                ok = false,
                status = null,
                database = null,
                redis = null,
                service = null,
                rawBody = t.message ?: "connection failed",
            )
        }
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObject(): JsonObject =
        this as? JsonObject ?: JsonObject(emptyMap())

    private fun JsonObject.objectOrNull(key: String): JsonObject? =
        (this[key] as? JsonObject)

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
}
