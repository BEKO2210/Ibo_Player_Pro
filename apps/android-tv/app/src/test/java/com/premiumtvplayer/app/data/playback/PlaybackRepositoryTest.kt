package com.premiumtvplayer.app.data.playback

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

class PlaybackRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: PlaybackRepository

    @Before
    fun setup() {
        server = MockWebServer().apply { start() }
        repo = PlaybackRepository(TestApiFactory.build(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `start POSTs body and parses session`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"session":{
                      "id":"sess1",
                      "profileId":"p1",
                      "sourceId":"s1",
                      "itemId":"vod-1",
                      "itemType":"vod",
                      "state":"starting",
                      "latestPositionSeconds":0,
                      "sessionStartedAt":"2026-04-13T12:00:00.000Z"
                    }}
                    """.trimIndent(),
                ),
        )
        val session = repo.start("p1", "s1", "vod-1", PlaybackItemType.Vod)
        assertEquals("sess1", session.id)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/playback/start", req.path)
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"profileId\":\"p1\""))
        assertTrue(body.contains("\"itemType\":\"vod\""))
    }

    @Test
    fun `heartbeat POSTs body and returns updated session`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"session":{
                      "id":"sess1","profileId":"p1","sourceId":"s1","itemId":"vod-1",
                      "itemType":"vod","state":"playing","latestPositionSeconds":300,
                      "sessionStartedAt":"2026-04-13T12:00:00.000Z"
                    }}
                    """.trimIndent(),
                ),
        )
        val session = repo.heartbeat("sess1", 300, PlaybackStateValue.Playing, 3600)
        assertEquals(300, session.latestPositionSeconds)
        assertEquals("playing", session.state)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"sessionId\":\"sess1\""))
        assertTrue(body.contains("\"positionSeconds\":300"))
        assertTrue(body.contains("\"state\":\"playing\""))
    }

    @Test
    fun `stop POSTs final position and completed flag`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"session":{
                      "id":"sess1","profileId":"p1","sourceId":"s1","itemId":"vod-1",
                      "itemType":"vod","state":"stopped","latestPositionSeconds":3600,
                      "sessionStartedAt":"2026-04-13T12:00:00.000Z",
                      "stoppedAt":"2026-04-13T13:00:00.000Z"
                    }}
                    """.trimIndent(),
                ),
        )
        val session = repo.stop("sess1", finalPositionSeconds = 3600, completed = true)
        assertEquals("stopped", session.state)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"completed\":true"))
    }

    @Test
    fun `start maps 402 to ENTITLEMENT_REQUIRED`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(402)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"ENTITLEMENT_REQUIRED","message":"Playback requires an active entitlement."}}"""),
        )
        val thrown = runCatching {
            repo.start("p1", "s1", "vod-1", PlaybackItemType.Vod)
        }.exceptionOrNull()
        assertTrue(thrown is ApiException.Server)
        assertEquals("ENTITLEMENT_REQUIRED", (thrown as ApiException.Server).code)
    }

    @Test
    fun `heartbeat maps 404 to VALIDATION_ERROR via ErrorEnvelope`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"VALIDATION_ERROR","message":"Playback session not found for this account."}}"""),
        )
        val thrown = runCatching {
            repo.heartbeat("bogus", 10, PlaybackStateValue.Playing)
        }.exceptionOrNull()
        assertTrue(thrown is ApiException.Server)
        assertEquals(404, (thrown as ApiException.Server).httpStatus)
    }
}
