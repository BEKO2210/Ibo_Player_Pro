package com.premiumtvplayer.app.data.profiles

import com.premiumtvplayer.app.data.TestApiFactory
import com.premiumtvplayer.app.data.api.ApiException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProfileRepositoryCrudTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: ProfileRepository

    @Before
    fun setup() {
        server = MockWebServer().apply { start() }
        repo = ProfileRepository(TestApiFactory.build(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `create POSTs JSON body and parses returned profile`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"profile":{
                      "id":"new-id",
                      "name":"Kid",
                      "isKids":true,
                      "ageLimit":12,
                      "isDefault":false,
                      "hasPin":true,
                      "createdAt":"2026-04-13T12:00:00.000Z"
                    }}
                    """.trimIndent(),
                ),
        )
        val created = repo.create(name = "Kid", isKids = true, ageLimit = 12, pin = "1234")
        assertEquals("new-id", created.id)
        assertEquals(true, created.isKids)
        assertEquals(12, created.ageLimit)
        assertEquals(true, created.hasPin)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/profiles", req.path)
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"isKids\":true"))
        assertTrue(body.contains("\"ageLimit\":12"))
        assertTrue(body.contains("\"pin\":\"1234\""))
    }

    @Test
    fun `update PUTs partial body`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody(
                    """{"profile":{"id":"p1","name":"Renamed","isKids":false,"isDefault":true,"hasPin":false,"createdAt":"2026-04-13T12:00:00.000Z"}}""",
                ),
        )
        val updated = repo.update(id = "p1", name = "Renamed")
        assertEquals("Renamed", updated.name)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"name\":\"Renamed\""))
        // Omitted optional fields should NOT appear (explicitNulls=false on Json).
        assertFalseContaining(body, "ageLimit")
    }

    @Test
    fun `update with clearPin sends the flag`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody(
                    """{"profile":{"id":"p1","name":"X","isKids":false,"isDefault":false,"hasPin":false,"createdAt":"2026-04-13T12:00:00.000Z"}}""",
                ),
        )
        repo.update(id = "p1", clearPin = true)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"clearPin\":true"))
    }

    @Test
    fun `create maps 409 SLOT_FULL`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"SLOT_FULL","message":"Profile cap reached"}}"""),
        )
        val thrown = runCatching {
            repo.create(name = "x", isKids = false)
        }.exceptionOrNull()
        assertTrue(thrown is ApiException.Server)
        assertEquals("SLOT_FULL", (thrown as ApiException.Server).code)
    }

    @Test
    fun `delete issues DELETE and succeeds on 204`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        repo.delete("p1")
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/v1/profiles/p1", req.path)
    }

    @Test
    fun `delete maps 409 on last-profile to VALIDATION_ERROR`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"VALIDATION_ERROR","message":"Cannot delete the last remaining profile."}}"""),
        )
        val thrown = runCatching { repo.delete("last") }.exceptionOrNull()
        assertTrue(thrown is ApiException.Server)
        assertEquals("VALIDATION_ERROR", (thrown as ApiException.Server).code)
    }

    @Test
    fun `verifyPin returns result dto`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"ok":false,"reason":"mismatch","failedAttemptCount":3,"lockedUntil":null}"""),
        )
        val result = repo.verifyPin("p1", "9999")
        assertEquals(false, result.ok)
        assertEquals("mismatch", result.reason)
        assertEquals(3, result.failedAttemptCount)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/profiles/p1/verify-pin", req.path)
    }

    private fun assertFalseContaining(haystack: String, needle: String) {
        org.junit.Assert.assertFalse(
            "expected \"$haystack\" to NOT contain \"$needle\"",
            haystack.contains(needle),
        )
    }
}
