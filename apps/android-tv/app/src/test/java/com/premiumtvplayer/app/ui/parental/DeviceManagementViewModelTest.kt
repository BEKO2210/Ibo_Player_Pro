package com.premiumtvplayer.app.ui.parental

import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.api.DeviceDto
import com.premiumtvplayer.app.data.devices.DevicesRepository
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
class DeviceManagementViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun device(id: String, revoked: Boolean = false) = DeviceDto(
        id = id,
        name = "Device-$id",
        platform = "android_tv",
        appVersion = "0.1.0",
        osVersion = null,
        lastSeenAt = null,
        revokedAt = if (revoked) "2026-04-13T12:00:00.000Z" else null,
        createdAt = "2026-04-13T12:00:00.000Z",
        isCurrent = false,
        isRevoked = revoked,
    )

    @Test
    fun `init loads devices into Ready state`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<DevicesRepository>()
        coEvery { repo.list() } returns listOf(device("d1"), device("d2"))

        val vm = DeviceManagementViewModel(repo)
        val state = vm.uiState.value
        assertTrue(state is DeviceManagementUiState.Ready)
        assertEquals(2, (state as DeviceManagementUiState.Ready).devices.size)
    }

    @Test
    fun `init surfaces Error on failure`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<DevicesRepository>()
        coEvery { repo.list() } throws ApiException.Server(
            httpStatus = 401,
            code = "UNAUTHORIZED",
            message = "Missing Authorization: Bearer token",
        )
        val vm = DeviceManagementViewModel(repo)
        val state = vm.uiState.value
        assertTrue(state is DeviceManagementUiState.Error)
    }

    @Test
    fun `requestRevoke + confirmRevoke updates the list and clears busy`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<DevicesRepository>()
        coEvery { repo.list() } returns listOf(device("d1"), device("d2"))
        coEvery { repo.revoke("d1") } returns device("d1", revoked = true)

        val vm = DeviceManagementViewModel(repo)
        vm.requestRevoke("d1")
        vm.confirmRevoke()

        val state = vm.uiState.value as DeviceManagementUiState.Ready
        assertEquals(null, state.busyId)
        assertEquals(null, state.confirmingRevokeId)
        assertTrue(state.devices.first { it.id == "d1" }.isRevoked)
        assertTrue(!state.devices.first { it.id == "d2" }.isRevoked)
    }

    @Test
    fun `confirmRevoke without prior requestRevoke is a no-op`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<DevicesRepository>()
        coEvery { repo.list() } returns listOf(device("d1"))
        val vm = DeviceManagementViewModel(repo)
        vm.confirmRevoke()
        val state = vm.uiState.value as DeviceManagementUiState.Ready
        assertEquals(null, state.busyId)
    }

    @Test
    fun `revoke server error surfaces errorMessage and clears busy`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<DevicesRepository>()
        coEvery { repo.list() } returns listOf(device("d1"))
        coEvery { repo.revoke("d1") } throws ApiException.Server(
            httpStatus = 404,
            code = "VALIDATION_ERROR",
            message = "Device not found for this account.",
        )

        val vm = DeviceManagementViewModel(repo)
        vm.requestRevoke("d1")
        vm.confirmRevoke()

        val state = vm.uiState.value as DeviceManagementUiState.Ready
        assertEquals(null, state.busyId)
        assertTrue(state.errorMessage != null)
    }
}
