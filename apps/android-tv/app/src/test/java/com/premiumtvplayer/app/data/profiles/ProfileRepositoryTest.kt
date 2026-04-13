package com.premiumtvplayer.app.data.profiles

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

class ProfileRepositoryTest {
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
    fun `list returns profiles in received order`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "profiles": [
                        {"id":"p1","name":"Main","isKids":false,"isDefault":true,"hasPin":false,"createdAt":"2026-04-13T12:00:00.000Z"},
                        {"id":"p2","name":"Kids","isKids":true,"ageLimit":12,"isDefault":false,"hasPin":true,"createdAt":"2026-04-13T12:05:00.000Z"}
                      ]
                    }
                    """.trimIndent()
                )
        )
        val items = repo.list()
        assertEquals(2, items.size)
        assertEquals("Main", items[0].name)
        assertEquals(true, items[0].isDefault)
        assertEquals(true, items[1].isKids)
        assertEquals(12, items[1].ageLimit)
        assertEquals(true, items[1].hasPin)
    }

    @Test
    fun `list maps 401 to UNAUTHORIZED ApiException_Server`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"UNAUTHORIZED","message":"Missing Authorization: Bearer token"}}""")
        )
        val thrown = runCatching { repo.list() }.exceptionOrNull()
        assertNotNull(thrown)
        assertTrue(thrown is ApiException.Server)
        assertEquals("UNAUTHORIZED", (thrown as ApiException.Server).code)
    }
}
