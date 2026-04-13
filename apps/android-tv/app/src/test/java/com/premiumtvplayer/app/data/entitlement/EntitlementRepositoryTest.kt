package com.premiumtvplayer.app.data.entitlement

import com.premiumtvplayer.app.data.TestApiFactory
import com.premiumtvplayer.app.data.api.ApiException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EntitlementRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: EntitlementRepository

    @Before
    fun setup() {
        server = MockWebServer().apply { start() }
        repo = EntitlementRepository(TestApiFactory.build(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `status returns EntitlementDto from V1 response envelope`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "entitlement": {
                        "state": "trial",
                        "trialEndsAt": "2026-04-27T12:00:00.000Z"
                      }
                    }
                    """.trimIndent()
                )
        )

        val ent = repo.status()

        assertEquals("trial", ent.state)
        assertEquals("2026-04-27T12:00:00.000Z", ent.trialEndsAt)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/v1/entitlement/status", recorded.path)
    }

    @Test
    fun `startTrial maps stable ErrorEnvelope to ApiException_Server`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(402)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "error": {
                        "code": "ENTITLEMENT_REQUIRED",
                        "message": "Trial has already been consumed for this account.",
                        "details": { "code": "TRIAL_ALREADY_CONSUMED" }
                      }
                    }
                    """.trimIndent()
                )
        )

        val thrown = runCatching { repo.startTrial() }.exceptionOrNull()
        assertNotNull(thrown)
        assertTrue(thrown is ApiException.Server)
        val server = thrown as ApiException.Server
        assertEquals(402, server.httpStatus)
        assertEquals("ENTITLEMENT_REQUIRED", server.code)
        assertTrue(server.message.contains("already been consumed"))
    }

    @Test
    fun `startTrial returns entitlement on success`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "entitlement": {
                        "state": "trial",
                        "trialStartedAt": "2026-04-13T12:00:00.000Z",
                        "trialEndsAt": "2026-04-27T12:00:00.000Z"
                      }
                    }
                    """.trimIndent()
                )
        )

        val ent = repo.startTrial()
        assertEquals("trial", ent.state)
        assertEquals("2026-04-13T12:00:00.000Z", ent.trialStartedAt)
    }
}
