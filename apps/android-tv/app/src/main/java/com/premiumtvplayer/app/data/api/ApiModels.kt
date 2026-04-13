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

@Serializable
data class CreateSourceRequest(
    val profileId: String? = null,
    val name: String,
    /** One of: m3u, xmltv, m3u_plus_epg. */
    val kind: String,
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val headers: Map<String, String>? = null,
)

@Serializable
data class UpdateSourceRequest(
    val name: String? = null,
    val isActive: Boolean? = null,
)

@Serializable
data class SingleSourceResponse(
    val source: SourceDto,
)

// ── Playback ───────────────────────────────────────────────────────────

@Serializable
data class StartPlaybackRequest(
    val profileId: String,
    val sourceId: String,
    val itemId: String,
    val itemType: String,
    val deviceId: String? = null,
)

@Serializable
data class HeartbeatRequest(
    val sessionId: String,
    val positionSeconds: Int,
    /** starting / playing / paused / buffering / stopped / error */
    val state: String,
    val durationSeconds: Int? = null,
)

@Serializable
data class StopPlaybackRequest(
    val sessionId: String,
    val finalPositionSeconds: Int,
    val durationSeconds: Int? = null,
    val completed: Boolean? = null,
)

@Serializable
data class PlaybackSessionDto(
    val id: String,
    val profileId: String,
    val sourceId: String? = null,
    val itemId: String,
    val itemType: String,
    val state: String,
    val latestPositionSeconds: Int,
    val sessionStartedAt: String,
    val lastHeartbeatAt: String? = null,
    val stoppedAt: String? = null,
)

@Serializable
data class PlaybackSessionResponse(
    val session: PlaybackSessionDto,
)

// ── Billing (Run 17) ───────────────────────────────────────────────────

@Serializable
data class BillingVerifyRequest(
    val purchaseToken: String,
    val productId: String,
)

// Backend /v1/billing/verify + /restore both return EntitlementStatusResponse.

// ── Continue watching ──────────────────────────────────────────────────

@Serializable
data class ContinueWatchingRowDto(
    val id: String,
    val sourceId: String? = null,
    val itemId: String,
    val itemType: String,
    val resumePositionSeconds: Int,
    val durationSeconds: Int? = null,
    val lastPlayedAt: String,
)

@Serializable
data class ContinueWatchingResponse(
    val items: List<ContinueWatchingRowDto>,
)

// ── EPG ────────────────────────────────────────────────────────────────

@Serializable
data class EpgChannelDto(
    val id: String,
    val sourceId: String,
    val externalChannelId: String,
    val displayName: String,
    val iconUrl: String? = null,
)

@Serializable
data class EpgChannelsResponse(
    val channels: List<EpgChannelDto>,
)

@Serializable
data class EpgProgrammeDto(
    val id: String,
    val channelId: String,
    val sourceId: String,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val category: String? = null,
    val startsAt: String,
    val endsAt: String,
)

@Serializable
data class EpgProgrammesResponse(
    val programmes: List<EpgProgrammeDto>,
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
