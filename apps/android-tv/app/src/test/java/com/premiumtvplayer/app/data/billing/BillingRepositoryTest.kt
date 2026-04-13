package com.premiumtvplayer.app.data.billing

import com.premiumtvplayer.app.data.TestApiFactory
import com.premiumtvplayer.app.data.api.ApiException
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BillingRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: BillingRepository

    @Before
    fun setup() {
        server = MockWebServer().apply { start() }
        // querySkus + launchPurchase use the Play Billing wrapper; those
        // paths need an emulator with the Play Services stack and are not
        // exercised in JVM unit tests. The verify/restore paths are pure
        // HTTP and cover the rest.
        val wrapper = mockk<BillingClientWrapper>(relaxed = true)
        repo = BillingRepository(TestApiFactory.build(server), wrapper)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `acknowledgeAndVerify posts token + productId and parses entitlement`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"entitlement":{
                      "state":"lifetime_family",
                      "activatedAt":"2026-04-13T12:00:00.000Z"
                    }}
                    """.trimIndent(),
                ),
        )

        val ent = repo.acknowledgeAndVerify("token-abc", "premium_player_family")
        assertEquals("lifetime_family", ent.state)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/billing/verify", req.path)
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"purchaseToken\":\"token-abc\""))
        assertTrue(body.contains("\"productId\":\"premium_player_family\""))
    }

    @Test
    fun `acknowledgeAndVerify maps 402 to ApiException_Server`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(402)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"ENTITLEMENT_REQUIRED","message":"Trial already consumed"}}"""),
        )
        val thrown = runCatching {
            repo.acknowledgeAndVerify("token", "premium_player_single")
        }.exceptionOrNull()
        assertTrue(thrown is ApiException.Server)
        assertEquals("ENTITLEMENT_REQUIRED", (thrown as ApiException.Server).code)
    }

    @Test
    fun `restore POSTs empty body and parses entitlement`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"entitlement":{
                      "state":"lifetime_single"
                    }}
                    """.trimIndent(),
                ),
        )
        val ent = repo.restore()
        assertEquals("lifetime_single", ent.state)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/billing/restore", req.path)
    }

    @Test
    fun `restore maps 401 to ApiException_Server with UNAUTHORIZED`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"UNAUTHORIZED","message":"Missing Authorization: Bearer token"}}"""),
        )
        val thrown = runCatching { repo.restore() }.exceptionOrNull()
        assertTrue(thrown is ApiException.Server)
        assertEquals("UNAUTHORIZED", (thrown as ApiException.Server).code)
    }
}
