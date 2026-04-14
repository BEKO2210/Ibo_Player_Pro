package com.premiumtvplayer.app.data.entitlement

import com.premiumtvplayer.app.data.TestApiFactory
import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.api.EntitlementDto
import com.premiumtvplayer.app.data.auth.FirebaseTokenSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the cache-fallback policy on [EntitlementRepository.status].
 * Five scenarios:
 *  1. happy path — successful API call writes through to cache
 *  2. network failure + fresh cache — cached value served, no throw
 *  3. network failure + empty cache — Network error rethrows
 *  4. server error (5xx) + fresh cache — server error rethrows, cache *ignored*
 *  5. unauthorized (401) + fresh cache — Unauthorized rethrows, cache ignored
 *  6. signed-out (uid=null) — no cache read, no cache write
 *  7. startTrial success — refreshes the cache
 */
class EntitlementRepositoryCacheTest {

    private lateinit var server: MockWebServer
    private lateinit var cache: FakeEntitlementCache
    private lateinit var tokens: FirebaseTokenSource
    private lateinit var repo: EntitlementRepository

    private val cachedDto = EntitlementDto(
        state = "lifetime_family",
        activatedAt = "2026-04-01T00:00:00Z",
    )

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        cache = FakeEntitlementCache()
        tokens = mockk<FirebaseTokenSource>().also { every { it.currentUid() } returns "uid-acme" }
        repo = EntitlementRepository(TestApiFactory.build(server), cache, tokens)
    }

    @After fun tearDown() { server.shutdown() }

    // ── 1 ──────────────────────────────────────────────────────────
    @Test
    fun `successful status writes through to the cache`() = runTest {
        server.enqueue(jsonOk("""{"entitlement":{"state":"trial"}}"""))

        val ent = repo.status()

        assertEquals("trial", ent.state)
        assertEquals(1, cache.putCount)
    }

    // ── 2 ──────────────────────────────────────────────────────────
    @Test
    fun `network failure with fresh cache returns cached entitlement`() = runTest {
        cache.seed(firebaseUid = "uid-acme", dto = cachedDto, ageMs = 60_000L)
        server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.DISCONNECT_AFTER_REQUEST })

        val ent = repo.status()

        assertEquals("lifetime_family", ent.state)
        assertEquals(0, cache.putCount)  // network failed, nothing to write through
    }

    // ── 3 ──────────────────────────────────────────────────────────
    @Test
    fun `network failure with empty cache rethrows Network error`() = runTest {
        server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.DISCONNECT_AT_START })

        val thrown = runCatching { repo.status() }.exceptionOrNull()
        assertNotNull(thrown)
        assertTrue("expected ApiException.Network, got $thrown", thrown is ApiException.Network)
    }

    // ── 4 ──────────────────────────────────────────────────────────
    @Test
    fun `server error 503 with fresh cache rethrows — never serves stale on server-state changes`() = runTest {
        cache.seed(firebaseUid = "uid-acme", dto = cachedDto, ageMs = 60_000L)
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"VALIDATION_ERROR","message":"down"}}""")
        )

        val thrown = runCatching { repo.status() }.exceptionOrNull()
        assertNotNull(thrown)
        assertTrue("expected ApiException.Server, got $thrown", thrown is ApiException.Server)
        assertEquals(503, (thrown as ApiException.Server).httpStatus)
    }

    // ── 5 ──────────────────────────────────────────────────────────
    @Test
    fun `unauthorized 401 with fresh cache rethrows — revoked tokens must not be hidden`() = runTest {
        cache.seed(firebaseUid = "uid-acme", dto = cachedDto, ageMs = 60_000L)
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"UNAUTHORIZED","message":"expired"}}""")
        )

        val thrown = runCatching { repo.status() }.exceptionOrNull()
        assertTrue("expected ApiException.Server, got $thrown", thrown is ApiException.Server)
        assertEquals("UNAUTHORIZED", (thrown as ApiException.Server).code)
    }

    // ── 6 ──────────────────────────────────────────────────────────
    @Test
    fun `cache for a different uid is ignored on network failure`() = runTest {
        cache.seed(firebaseUid = "uid-OTHER", dto = cachedDto, ageMs = 60_000L)
        server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.DISCONNECT_AT_START })

        val thrown = runCatching { repo.status() }.exceptionOrNull()
        assertTrue("expected ApiException.Network, got $thrown", thrown is ApiException.Network)
    }

    // ── 7 ──────────────────────────────────────────────────────────
    @Test
    fun `signed out user does not read or write cache`() = runTest {
        every { tokens.currentUid() } returns null
        cache.seed(firebaseUid = "uid-acme", dto = cachedDto, ageMs = 60_000L)
        server.enqueue(jsonOk("""{"entitlement":{"state":"none"}}"""))

        val ent = repo.status()

        assertEquals("none", ent.state)
        assertEquals(0, cache.putCount)  // uid null → never persists
    }

    // ── 8 ──────────────────────────────────────────────────────────
    @Test
    fun `startTrial refreshes cache on success`() = runTest {
        server.enqueue(jsonOk("""{"entitlement":{"state":"trial","trialEndsAt":"2026-04-27T12:00:00Z"}}"""))

        val ent = repo.startTrial()

        assertEquals("trial", ent.state)
        assertEquals(1, cache.putCount)
    }

    private fun jsonOk(body: String) =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
}
