package com.premiumtvplayer.app.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Premium TV Player V1 API contract — mirrors `packages/api-contracts/openapi.yaml`.
 *
 * All routes go through `/v1` (set as the suffix in `BuildConfig.API_BASE_URL`).
 * Auth (Firebase ID token) is attached by an OkHttp interceptor when present;
 * the `/auth/*` routes are explicitly unauth-required (so the interceptor
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

    // ── Profiles (read-only here; full CRUD lands in Run 14/18) ────────
    @GET("profiles")
    suspend fun listProfiles(): ProfileListResponse
}
