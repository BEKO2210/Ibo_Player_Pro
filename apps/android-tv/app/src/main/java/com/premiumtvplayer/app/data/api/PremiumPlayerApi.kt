package com.premiumtvplayer.app.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Premium TV Player V1 API contract — mirrors `packages/api-contracts/openapi.yaml`.
 *
 * All routes go through `/v1` (set as the suffix in `BuildConfig.API_BASE_URL`).
 * Auth (Firebase ID token) is attached by an OkHttp interceptor when present;
 * the `/auth/` routes are explicitly unauth-required (so the interceptor
 * skips them).
 */
interface PremiumPlayerApi {
    // ── Auth ───────────────────────────────────────────────────────────
    @POST("auth/register")
    suspend fun register(@Body body: FirebaseTokenRequest): AccountSnapshotResponse

    @POST("auth/login")
    suspend fun login(@Body body: FirebaseTokenRequest): AccountSnapshotResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: FirebaseTokenRequest): AccountSnapshotResponse

    // ── Entitlement ────────────────────────────────────────────────────
    @GET("entitlement/status")
    suspend fun entitlementStatus(): EntitlementStatusResponse

    @POST("entitlement/trial/start")
    suspend fun startTrial(): EntitlementStatusResponse

    // ── Profiles (Run 18: full CRUD + verify-pin) ─────────────────────
    @GET("profiles")
    suspend fun listProfiles(): ProfileListResponse

    @POST("profiles")
    suspend fun createProfile(@Body body: CreateProfileRequest): SingleProfileResponse

    @PUT("profiles/{id}")
    suspend fun updateProfile(
        @Path("id") id: String,
        @Body body: UpdateProfileRequest,
    ): SingleProfileResponse

    @DELETE("profiles/{id}")
    suspend fun deleteProfile(@Path("id") id: String): Response<Unit>

    @POST("profiles/{id}/verify-pin")
    suspend fun verifyProfilePin(
        @Path("id") id: String,
        @Body body: VerifyPinRequest,
    ): VerifyPinResponse

    // ── Devices (Run 8 endpoints; client-side CRUD in Run 18) ─────
    @GET("devices")
    suspend fun listDevices(): DeviceListResponse

    @POST("devices/{id}/revoke")
    suspend fun revokeDevice(@Path("id") id: String): RevokeDeviceResponse

    // ── Sources (full CRUD — Run 15) ───────────────────────────────────
    @GET("sources")
    suspend fun listSources(
        @Query("profileId") profileId: String? = null,
    ): SourceListResponse

    @POST("sources")
    suspend fun createSource(@Body body: CreateSourceRequest): SingleSourceResponse

    @PUT("sources/{id}")
    suspend fun updateSource(
        @Path("id") id: String,
        @Body body: UpdateSourceRequest,
    ): SingleSourceResponse

    @DELETE("sources/{id}")
    suspend fun deleteSource(@Path("id") id: String): Response<Unit>

    // ── Playback (Run 16) ──────────────────────────────────────────────
    @POST("playback/start")
    suspend fun startPlayback(@Body body: StartPlaybackRequest): PlaybackSessionResponse

    @POST("playback/heartbeat")
    suspend fun heartbeat(@Body body: HeartbeatRequest): PlaybackSessionResponse

    @POST("playback/stop")
    suspend fun stopPlayback(@Body body: StopPlaybackRequest): PlaybackSessionResponse

    @GET("continue-watching")
    suspend fun listContinueWatching(
        @Query("profileId") profileId: String,
        @Query("limit") limit: Int? = null,
    ): ContinueWatchingResponse

    // ── EPG (Run 16) ───────────────────────────────────────────────────
    @GET("epg/channels")
    suspend fun listEpgChannels(@Query("sourceId") sourceId: String): EpgChannelsResponse

    @GET("epg/programmes")
    suspend fun listEpgProgrammes(
        @Query("channelId") channelId: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): EpgProgrammesResponse

    // ── Billing (Run 17) ───────────────────────────────────────────────
    @POST("billing/verify")
    suspend fun verifyPurchase(@Body body: BillingVerifyRequest): EntitlementStatusResponse

    @POST("billing/restore")
    suspend fun restorePurchases(): EntitlementStatusResponse

    // ── Diagnostics (Run 19) ───────────────────────────────────────────
    // Note: `/health` is hosted at the service root (no `/v1` prefix), so
    // callers must use a dedicated baseUrl or absolute URL. The
    // DiagnosticsViewModel uses OkHttp directly for this.
}


