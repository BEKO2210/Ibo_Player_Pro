package com.premiumtvplayer.app.data.sources

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

class SourceRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: SourceRepository

    @Before
    fun setup() {
        server = MockWebServer().apply { start() }
        repo = SourceRepository(TestApiFactory.build(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `create posts JSON body and parses returned source`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "source": {
                        "id": "s-new",
                        "name": "World TV Plus",
                        "kind": "m3u_plus_epg",
                        "isActive": true,
                        "validationStatus": "pending",
                        "createdAt": "2026-04-13T12:00:00.000Z"
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val created = repo.create(
            CreateSourceInput(
                name = "World TV Plus",
                kind = SourceKind.M3uPlusEpg,
                url = "https://provider/playlist.m3u",
                username = "alice",
                password = "SuperSecret",
            ),
        )

        assertEquals("s-new", created.id)
        assertEquals("m3u_plus_epg", created.kind)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/sources", recorded.path)
        val body = recorded.body.readUtf8()
        // Key envelope checks: JSON contains kind, url, and credentials are
        // not accidentally swapped or omitted.
        assertTrue(body.contains("\"kind\":\"m3u_plus_epg\""))
        assertTrue(body.contains("\"url\":\"https://provider/playlist.m3u\""))
        assertTrue(body.contains("\"username\":\"alice\""))
    }

    @Test
    fun `create maps 402 ENTITLEMENT_REQUIRED to ApiException_Server`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(402)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"ENTITLEMENT_REQUIRED","message":"Source creation requires an active entitlement."}}"""),
        )
        val thrown = runCatching {
            repo.create(
                CreateSourceInput(
                    name = "X",
                    kind = SourceKind.M3U,
                    url = "https://x/p",
                ),
            )
        }.exceptionOrNull()
        assertTrue(thrown is ApiException.Server)
        assertEquals("ENTITLEMENT_REQUIRED", (thrown as ApiException.Server).code)
        assertEquals(402, thrown.httpStatus)
    }

    @Test
    fun `rename hits PUT endpoint with name only`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody(
                    """{"source":{"id":"s1","name":"Renamed","kind":"m3u","isActive":true,"validationStatus":"valid","createdAt":"2026-04-13T12:00:00.000Z"}}""",
                ),
        )
        val updated = repo.rename("s1", "Renamed")
        assertEquals("Renamed", updated.name)
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/v1/sources/s1", req.path)
        assertTrue(req.body.readUtf8().contains("\"name\":\"Renamed\""))
    }

    @Test
    fun `setActive hits PUT endpoint with isActive only`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody(
                    """{"source":{"id":"s1","name":"X","kind":"m3u","isActive":false,"validationStatus":"valid","createdAt":"2026-04-13T12:00:00.000Z"}}""",
                ),
        )
        val updated = repo.setActive("s1", false)
        assertEquals(false, updated.isActive)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"isActive\":false"))
    }

    @Test
    fun `delete issues DELETE and succeeds on 204`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        repo.delete("s1")
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/v1/sources/s1", req.path)
    }

    @Test
    fun `delete maps 404 to ApiException_Server`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"VALIDATION_ERROR","message":"Source not found for this account."}}"""),
        )
        val thrown = runCatching { repo.delete("missing") }.exceptionOrNull()
        assertNotNull(thrown)
        assertTrue(thrown is ApiException.Server)
        assertEquals(404, (thrown as ApiException.Server).httpStatus)
    }

    @Test
    fun `create maps 409 SLOT_FULL correctly`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"SLOT_FULL","message":"Device cap reached."}}"""),
        )
        val thrown = runCatching {
            repo.create(
                CreateSourceInput(name = "x", kind = SourceKind.M3U, url = "https://x/p"),
            )
        }.exceptionOrNull()
        assertTrue(thrown is ApiException.Server)
        assertEquals("SLOT_FULL", (thrown as ApiException.Server).code)
    }
}
