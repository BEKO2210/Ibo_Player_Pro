package com.premiumtvplayer.app.ui.parental

import androidx.lifecycle.SavedStateHandle
import com.premiumtvplayer.app.data.api.VerifyPinResponse
import com.premiumtvplayer.app.data.profiles.ProfileRepository
import com.premiumtvplayer.app.ui.nav.Routes
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
class PinGateViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun savedState() = SavedStateHandle(mapOf(Routes.ProfileIdArg to "p1"))

    @Test
    fun `initial state is Editing with empty pin`() {
        val repo = mockk<ProfileRepository>()
        val vm = PinGateViewModel(savedState(), repo)
        val state = vm.uiState.value as PinGateUiState.Editing
        assertEquals("", state.pin)
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun `onPinChange filters non-digits and caps at 10 characters`() {
        val repo = mockk<ProfileRepository>()
        val vm = PinGateViewModel(savedState(), repo)
        vm.onPinChange("12ab34cd-5678901234")
        val state = vm.uiState.value as PinGateUiState.Editing
        assertEquals("1234567890", state.pin)
    }

    @Test
    fun `submit with short pin sets errorMessage`() {
        val repo = mockk<ProfileRepository>()
        val vm = PinGateViewModel(savedState(), repo)
        vm.onPinChange("12")
        vm.submit()
        val state = vm.uiState.value as PinGateUiState.Editing
        assertTrue(state.errorMessage?.contains("at least 4") == true)
    }

    @Test
    fun `successful verify transitions to Unlocked`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<ProfileRepository>()
        coEvery { repo.verifyPin("p1", "1234") } returns VerifyPinResponse(ok = true)

        val vm = PinGateViewModel(savedState(), repo)
        vm.onPinChange("1234")
        vm.submit()

        assertTrue(vm.uiState.value is PinGateUiState.Unlocked)
    }

    @Test
    fun `mismatch updates failedAttemptCount and clears pin`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<ProfileRepository>()
        coEvery { repo.verifyPin("p1", "9999") } returns VerifyPinResponse(
            ok = false,
            reason = "mismatch",
            failedAttemptCount = 2,
            lockedUntil = null,
        )

        val vm = PinGateViewModel(savedState(), repo)
        vm.onPinChange("9999")
        vm.submit()

        val state = vm.uiState.value as PinGateUiState.Editing
        assertEquals("", state.pin)
        assertEquals(2, state.failedAttemptCount)
        assertTrue(state.errorMessage?.contains("Wrong PIN") == true)
    }

    @Test
    fun `locked response carries lockedUntilIso and preserves lock state`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<ProfileRepository>()
        coEvery { repo.verifyPin("p1", "0000") } returns VerifyPinResponse(
            ok = false,
            reason = "locked",
            lockedUntil = "2099-01-01T00:00:00.000Z",
        )
        val vm = PinGateViewModel(savedState(), repo)
        vm.onPinChange("0000")
        vm.submit()

        val state = vm.uiState.value as PinGateUiState.Editing
        assertEquals("2099-01-01T00:00:00.000Z", state.lockedUntilIso)
        assertTrue(state.errorMessage?.contains("locked", ignoreCase = true) == true)
    }

    @Test
    fun `no_pin response treats as Unlocked defensively`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<ProfileRepository>()
        coEvery { repo.verifyPin("p1", "1234") } returns VerifyPinResponse(
            ok = false,
            reason = "no_pin",
        )
        val vm = PinGateViewModel(savedState(), repo)
        vm.onPinChange("1234")
        vm.submit()
        assertTrue(vm.uiState.value is PinGateUiState.Unlocked)
    }
}
