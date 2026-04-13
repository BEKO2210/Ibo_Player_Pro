package com.premiumtvplayer.app.data.entitlement

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.premiumtvplayer.app.data.api.EntitlementDto
import com.premiumtvplayer.app.data.util.Clock
import com.premiumtvplayer.app.di.EntitlementCacheStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local fallback for [EntitlementRepository.status] so the app survives
 * short backend outages without the user being kicked out of paid
 * features.
 *
 * Design rules:
 *  - **Network-failure-only fallback.** Cache is consulted *only* when
 *    the API call fails with [com.premiumtvplayer.app.data.api.ApiException.Network].
 *    Server errors (`401`, `403`, `5xx` with envelope) MUST surface — they
 *    reflect actual state changes (revoked, unauthorized) the cache may not mask.
 *  - **24-hour TTL.** Long enough that a multi-hour outage doesn't lock paying
 *    users out, short enough that a revoked entitlement stops working within a day.
 *  - **Per-Firebase-uid scoped.** The cached payload carries the uid it was
 *    written under. A different signed-in user (or a signed-out caller) gets
 *    a `null` read — no leakage across accounts.
 */
interface EntitlementCache {
    /** Persist the latest authoritative entitlement for [firebaseUid]. */
    suspend fun put(firebaseUid: String, dto: EntitlementDto)

    /**
     * Returns the cached entitlement only if all of these hold:
     *  - a payload exists
     *  - it was written for the same [firebaseUid]
     *  - it is fresh (age < TTL, age >= 0 i.e. clock didn't jump back)
     *  - it deserializes cleanly
     * Otherwise returns null.
     */
    suspend fun read(firebaseUid: String): CachedEntitlement?

    /** Drop the cache. Call on sign-out for hygiene (uid scoping already
     *  prevents cross-user reads, but clearing avoids stale storage). */
    suspend fun clear()
}

/** Exposes the cached DTO + how old it is at read time. UI may surface the
 *  age ("Last verified 2 minutes ago") if it wants to. */
data class CachedEntitlement(val dto: EntitlementDto, val ageMs: Long)

// ─────────────────────────────────────────────────────────────────────
//  Pure helper, no Android, no DataStore — fully unit-testable on JVM.
// ─────────────────────────────────────────────────────────────────────

internal object EntitlementCacheCodec {
    /** Bump the suffix and the [KEY] when the wire format changes
     *  incompatibly. Old payloads will then be invisible (decode → null)
     *  and the cache self-heals on the next successful API call. */
    const val WIRE_VERSION = 1
    const val TTL_MS: Long = 24L * 60 * 60 * 1000  // 24 hours

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Payload(
        val firebaseUid: String,
        val entitlement: EntitlementDto,
        val cachedAtMs: Long,
        val wireVersion: Int = WIRE_VERSION,
    )

    fun encode(uid: String, dto: EntitlementDto, nowMs: Long): String =
        json.encodeToString(Payload(firebaseUid = uid, entitlement = dto, cachedAtMs = nowMs))

    fun decodeOrNull(blob: String?): Payload? {
        if (blob.isNullOrEmpty()) return null
        val payload = runCatching { json.decodeFromString<Payload>(blob) }.getOrNull() ?: return null
        if (payload.wireVersion != WIRE_VERSION) return null
        return payload
    }

    /** True iff [savedAtMs] is in the inclusive past and within [ttlMs]. */
    fun isFresh(savedAtMs: Long, nowMs: Long, ttlMs: Long): Boolean {
        val age = nowMs - savedAtMs
        return age in 0 until ttlMs
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Production implementation: persists via DataStore Preferences.
// ─────────────────────────────────────────────────────────────────────

@Singleton
class DataStoreEntitlementCache @Inject constructor(
    @EntitlementCacheStore private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) : EntitlementCache {

    override suspend fun put(firebaseUid: String, dto: EntitlementDto) {
        val blob = EntitlementCacheCodec.encode(firebaseUid, dto, clock.nowMillis())
        dataStore.edit { it[KEY] = blob }
    }

    override suspend fun read(firebaseUid: String): CachedEntitlement? {
        val blob = dataStore.data.first()[KEY] ?: return null
        val payload = EntitlementCacheCodec.decodeOrNull(blob) ?: return null
        if (payload.firebaseUid != firebaseUid) return null
        val now = clock.nowMillis()
        if (!EntitlementCacheCodec.isFresh(payload.cachedAtMs, now, EntitlementCacheCodec.TTL_MS)) return null
        return CachedEntitlement(payload.entitlement, ageMs = now - payload.cachedAtMs)
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    companion object {
        val KEY = stringPreferencesKey("entitlement_payload_v${EntitlementCacheCodec.WIRE_VERSION}")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EntitlementCacheBindings {
    @Binds
    @Singleton
    abstract fun bindEntitlementCache(impl: DataStoreEntitlementCache): EntitlementCache
}
