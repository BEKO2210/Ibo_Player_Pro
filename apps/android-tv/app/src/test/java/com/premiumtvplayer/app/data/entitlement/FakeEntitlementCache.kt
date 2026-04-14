package com.premiumtvplayer.app.data.entitlement

import com.premiumtvplayer.app.data.api.EntitlementDto

/**
 * Test fake — in-memory, uid-scoped, externally controllable age.
 * Production code goes through [DataStoreEntitlementCache]; tests use
 * this so they don't need a real DataStore + real coroutine context for
 * file I/O.
 */
class FakeEntitlementCache : EntitlementCache {
    private var storedUid: String? = null
    private var storedDto: EntitlementDto? = null

    /** Set by the test before calling read; defaults to "fresh". */
    var ageMsForRead: Long = 0L

    var putCount: Int = 0
        private set
    var clearCount: Int = 0
        private set

    override suspend fun put(firebaseUid: String, dto: EntitlementDto) {
        storedUid = firebaseUid
        storedDto = dto
        putCount++
    }

    override suspend fun read(firebaseUid: String): CachedEntitlement? {
        val dto = storedDto ?: return null
        if (storedUid != firebaseUid) return null
        return CachedEntitlement(dto, ageMsForRead)
    }

    override suspend fun clear() {
        storedUid = null
        storedDto = null
        clearCount++
    }

    /** Convenience for tests: pre-seed the cache without going through put. */
    fun seed(firebaseUid: String, dto: EntitlementDto, ageMs: Long = 0L) {
        storedUid = firebaseUid
        storedDto = dto
        ageMsForRead = ageMs
    }
}
