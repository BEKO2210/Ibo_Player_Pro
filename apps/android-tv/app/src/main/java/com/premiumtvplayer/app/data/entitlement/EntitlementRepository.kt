package com.premiumtvplayer.app.data.entitlement

import com.premiumtvplayer.app.data.api.ApiErrorMapper
import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.api.EntitlementDto
import com.premiumtvplayer.app.data.api.PremiumPlayerApi
import com.premiumtvplayer.app.data.auth.FirebaseTokenSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Account-entitlement read/write surface used by every layer that needs
 * to know "what's this account allowed to do right now?".
 *
 * Resilience policy:
 *  - [status] is read-mostly and resilient: on a successful API call we
 *    refresh [EntitlementCache]; on a *network-only* failure we hand
 *    back the cached value if it's still fresh (24h TTL, uid-scoped).
 *    Any HTTP-level failure (`401`, `403`, `5xx` with envelope) bypasses
 *    the cache and propagates up, because those reflect actual server
 *    state changes the client must respect.
 *  - [startTrial] is a write and is **never** served from cache. Success
 *    refreshes the cache so subsequent [status] reads stay coherent
 *    even if the network goes down right after.
 */
@Singleton
class EntitlementRepository @Inject constructor(
    private val api: PremiumPlayerApi,
    private val cache: EntitlementCache,
    private val tokenSource: FirebaseTokenSource,
) {
    suspend fun status(): EntitlementDto {
        val uid = tokenSource.currentUid()
        return try {
            val fresh = api.entitlementStatus().entitlement
            if (uid != null) cache.put(uid, fresh)
            fresh
        } catch (t: Throwable) {
            val mapped = ApiErrorMapper.map(t)
            if (mapped is ApiException.Network && uid != null) {
                val cached = cache.read(uid)
                if (cached != null) return cached.dto
            }
            throw mapped
        }
    }

    suspend fun startTrial(): EntitlementDto = wrap {
        val ent = api.startTrial().entitlement
        tokenSource.currentUid()?.let { uid -> cache.put(uid, ent) }
        ent
    }

    private suspend inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}
