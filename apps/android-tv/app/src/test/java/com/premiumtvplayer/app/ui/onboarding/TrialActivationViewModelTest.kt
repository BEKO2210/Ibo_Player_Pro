package com.premiumtvplayer.app.ui.onboarding

import app.cash.turbine.test
import com.premiumtvplayer.app.data.api.EntitlementDto
import com.premiumtvplayer.app.data.entitlement.EntitlementRepository
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
class TrialActivationViewModelTest {

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `activate happy path transitions Idle - Submitting - Activated`() = runTest(
        UnconfinedTestDispatcher(),
    ) {
        val repo = mockk<EntitlementRepository>()
        val ent = EntitlementDto(
            state = "trial",
            trialStartedAt = "2026-04-13T12:00:00.000Z",
            trialEndsAt = "2026-04-27T12:00:00.000Z",
        )
        coEvery { repo.startTrial() } returns ent

        val vm = TrialActivationViewModel(repo)

        vm.uiState.test {
            // Initial
            assertEquals(TrialUiState.Idle, awaitItem())

            vm.activate()

            val submitting = awaitItem()
            assertTrue(submitting is TrialUiState.Submitting)

            val activated = awaitItem()
            assertTrue(activated is TrialUiState.Activated)
            assertEquals("trial", (activated as TrialUiState.Activated).entitlement.state)
        }
    }

    @Test
    fun `activate returns AlreadyConsumed when backend says ENTITLEMENT_REQUIRED`() = runTest(
        UnconfinedTestDispatcher(),
    ) {
        val repo = mockk<EntitlementRepository>()
        val ent = EntitlementDto(state = "expired")
        coEvery { repo.startTrial() } throws com.premiumtvplayer.app.data.api.ApiException.Server(
            httpStatus = 402,
            code = "ENTITLEMENT_REQUIRED",
            message = "Trial already consumed",
        )
        coEvery { repo.status() } returns ent

        val vm = TrialActivationViewModel(repo)

        vm.uiState.test {
            assertEquals(TrialUiState.Idle, awaitItem())
            vm.activate()

            // Skip through Submitting to terminal state
            var state = awaitItem()
            if (state is TrialUiState.Submitting) state = awaitItem()
            assertTrue(state is TrialUiState.AlreadyConsumed)
            assertEquals("expired", (state as TrialUiState.AlreadyConsumed).entitlement?.state)
        }
    }
}
