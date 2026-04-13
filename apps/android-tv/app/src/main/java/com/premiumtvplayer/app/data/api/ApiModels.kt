package com.premiumtvplayer.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Request bodies ─────────────────────────────────────────────────────

@Serializable
data class FirebaseTokenRequest(
    val firebaseIdToken: String,
    val locale: String? = null,
)

// ── Account + Entitlement ──────────────────────────────────────────────

@Serializable
data class AccountDto(
    val id: String,
    val email: String,
    val emailVerified: Boolean,
    val locale: String,
    val createdAt: String,
)

@Serializable
data class EntitlementDto(
    /** One of: none, trial, lifetime_single, lifetime_family, expired, revoked. */
    val state: String,
    val trialStartedAt: String? = null,
    val trialEndsAt: String? = null,
    val activatedAt: String? = null,
    val expiresAt: String? = null,
    val revokedAt: String? = null,
)

@Serializable
data class AccountSnapshotResponse(
    val account: AccountDto,
    val entitlement: EntitlementDto,
)

@Serializable
data class EntitlementStatusResponse(
    val entitlement: EntitlementDto,
)

// ── Profiles ───────────────────────────────────────────────────────────

@Serializable
data class ProfileDto(
    val id: String,
    val name: String,
    val isKids: Boolean,
    val ageLimit: Int? = null,
    val isDefault: Boolean,
    val hasPin: Boolean,
    val createdAt: String,
)

@Serializable
data class ProfileListResponse(
    val profiles: List<ProfileDto>,
)

// ── Sources ────────────────────────────────────────────────────────────

@Serializable
data class SourceDto(
    val id: String,
    val profileId: String? = null,
    val name: String,
    /** One of: m3u, xmltv, m3u_plus_epg. */
    val kind: String,
    val isActive: Boolean,
    val validationStatus: String,
    val itemCountEstimate: Int? = null,
    val createdAt: String,
)

@Serializable
data class SourceListResponse(
    val sources: List<SourceDto>,
)

// ── Stable error envelope (matches packages/api-contracts ErrorEnvelope) ──

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val details: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("requestId") val requestId: String? = null,
)

@Serializable
data class ApiErrorEnvelope(
    val error: ApiErrorBody,
)
