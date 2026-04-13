package com.premiumtvplayer.app.data.entitlement

import com.premiumtvplayer.app.data.api.EntitlementDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the codec helper. The codec is the only piece of
 * cache logic that contains real branches (TTL math, version gate,
 * round-trip), so it carries the test weight while [DataStoreEntitlementCache]
 * stays a thin plumbing wrapper.
 */
class EntitlementCacheCodecTest {

    private val sample = EntitlementDto(
        state = "trial",
        trialStartedAt = "2026-04-01T00:00:00Z",
        trialEndsAt = "2026-04-15T00:00:00Z",
    )

    @Test
    fun `encode then decode returns the same payload`() {
        val blob = EntitlementCacheCodec.encode("uid-1", sample, nowMs = 1_700_000_000_000L)
        val parsed = EntitlementCacheCodec.decodeOrNull(blob)
        assertNotNull(parsed)
        assertEquals("uid-1", parsed!!.firebaseUid)
        assertEquals("trial", parsed.entitlement.state)
        assertEquals(1_700_000_000_000L, parsed.cachedAtMs)
        assertEquals(EntitlementCacheCodec.WIRE_VERSION, parsed.wireVersion)
    }

    @Test
    fun `decodeOrNull returns null on null or empty blob`() {
        assertNull(EntitlementCacheCodec.decodeOrNull(null))
        assertNull(EntitlementCacheCodec.decodeOrNull(""))
    }

    @Test
    fun `decodeOrNull returns null on garbage`() {
        assertNull(EntitlementCacheCodec.decodeOrNull("not json {{{"))
        assertNull(EntitlementCacheCodec.decodeOrNull("""{"unexpected":true}"""))
    }

    @Test
    fun `decodeOrNull rejects payloads with wrong wire version`() {
        // Hand-craft a payload pinned to wireVersion + 99.
        val futureBlob = """
            {
              "firebaseUid": "uid-1",
              "entitlement": {"state": "trial"},
              "cachedAtMs": 0,
              "wireVersion": ${EntitlementCacheCodec.WIRE_VERSION + 99}
            }
        """.trimIndent()
        assertNull(EntitlementCacheCodec.decodeOrNull(futureBlob))
    }

    @Test
    fun `isFresh accepts age within TTL`() {
        val saved = 1_000L
        val now = saved + EntitlementCacheCodec.TTL_MS - 1
        assertTrue(EntitlementCacheCodec.isFresh(saved, now, EntitlementCacheCodec.TTL_MS))
    }

    @Test
    fun `isFresh accepts age zero`() {
        assertTrue(EntitlementCacheCodec.isFresh(savedAtMs = 1L, nowMs = 1L, ttlMs = 1000L))
    }

    @Test
    fun `isFresh rejects expired age`() {
        val saved = 1_000L
        val now = saved + EntitlementCacheCodec.TTL_MS
        assertFalse(EntitlementCacheCodec.isFresh(saved, now, EntitlementCacheCodec.TTL_MS))
    }

    @Test
    fun `isFresh rejects negative age (clock jumped back)`() {
        assertFalse(EntitlementCacheCodec.isFresh(savedAtMs = 100L, nowMs = 50L, ttlMs = 1000L))
    }
}
