package com.premiumtvplayer.app.ui.diagnostics

import com.premiumtvplayer.app.data.diagnostics.ErrorLogBuffer
import com.premiumtvplayer.app.data.diagnostics.HealthClient
import com.premiumtvplayer.app.data.diagnostics.HealthSnapshot
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `init transitions to Ready with health snapshot`() = runTest(UnconfinedTestDispatcher()) {
        val client = mockk<HealthClient>()
        val log = ErrorLogBuffer()
        coEvery { client.fetch() } returns HealthSnapshot(
            ok = true, status = "ok", database = "up", redis = "up",
            service = "premium-player-api", rawBody = "",
        )
        val vm = DiagnosticsViewModel(client, log)

        val state = vm.uiState.value
        assertTrue(state is DiagnosticsUiState.Ready)
        val ready = state as DiagnosticsUiState.Ready
        assertEquals(true, ready.health?.ok)
        assertEquals("up", ready.health?.database)
        assertTrue(ready.errors.isEmpty())
    }

    @Test
    fun `init handles unreachable health gracefully`() = runTest(UnconfinedTestDispatcher()) {
        val client = mockk<HealthClient>()
        val log = ErrorLogBuffer()
        coEvery { client.fetch() } throws RuntimeException("connection refused")
        val vm = DiagnosticsViewModel(client, log)

        val state = vm.uiState.value as DiagnosticsUiState.Ready
        assertEquals(false, state.health?.ok)
    }

    @Test
    fun `error log entries flow into Ready state`() = runTest(UnconfinedTestDispatcher()) {
        val client = mockk<HealthClient>()
        val log = ErrorLogBuffer()
        coEvery { client.fetch() } returns HealthSnapshot(
            ok = true, status = "ok", database = "up", redis = "up",
            service = "premium-player-api", rawBody = "",
        )
        val vm = DiagnosticsViewModel(client, log)

        log.record("AuthRepository", "Bearer token expired", code = "UNAUTHORIZED")

        val state = vm.uiState.value as DiagnosticsUiState.Ready
        assertEquals(1, state.errors.size)
        assertEquals("UNAUTHORIZED", state.errors[0].code)
    }

    @Test
    fun `refresh sets refreshing flag and reloads`() = runTest(UnconfinedTestDispatcher()) {
        val client = mockk<HealthClient>()
        val log = ErrorLogBuffer()
        coEvery { client.fetch() } returns HealthSnapshot(
            ok = true, status = "ok", database = null, redis = null, service = null, rawBody = "",
        )
        val vm = DiagnosticsViewModel(client, log)

        vm.refresh()

        val state = vm.uiState.value as DiagnosticsUiState.Ready
        assertEquals(false, state.refreshing)
    }
}
