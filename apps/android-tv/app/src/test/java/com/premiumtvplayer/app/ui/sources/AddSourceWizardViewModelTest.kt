package com.premiumtvplayer.app.ui.sources

import app.cash.turbine.test
import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.api.SourceDto
import com.premiumtvplayer.app.data.sources.CreateSourceInput
import com.premiumtvplayer.app.data.sources.SourceKind
import com.premiumtvplayer.app.data.sources.SourceRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddSourceWizardViewModelTest {

    @Before
    fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private val created = SourceDto(
        id = "s1",
        name = "World TV Plus",
        kind = "m3u_plus_epg",
        isActive = true,
        validationStatus = "pending",
        createdAt = "2026-04-13T12:00:00.000Z",
    )

    @Test
    fun `initial state is Editing at Kind step`() {
        val repo = mockk<SourceRepository>()
        val vm = AddSourceWizardViewModel(repo)
        val state = vm.uiState.value
        assertTrue(state is WizardUiState.Editing)
        assertEquals(WizardStep.Kind, (state as WizardUiState.Editing).step)
    }

    @Test
    fun `next from Kind without pickKind sets errorMessage and stays on Kind`() {
        val repo = mockk<SourceRepository>()
        val vm = AddSourceWizardViewModel(repo)
        vm.next()
        val state = vm.uiState.value as WizardUiState.Editing
        assertEquals(WizardStep.Kind, state.step)
        assertTrue(state.errorMessage?.contains("kind") == true)
    }

    @Test
    fun `pickKind then next moves to Endpoint`() {
        val repo = mockk<SourceRepository>()
        val vm = AddSourceWizardViewModel(repo)
        vm.pickKind(SourceKind.M3uPlusEpg)
        vm.next()
        val state = vm.uiState.value as WizardUiState.Editing
        assertEquals(WizardStep.Endpoint, state.step)
        assertEquals(SourceKind.M3uPlusEpg, state.draft.kind)
        assertNull(state.errorMessage)
    }

    @Test
    fun `next from Endpoint without URL surfaces error`() {
        val repo = mockk<SourceRepository>()
        val vm = AddSourceWizardViewModel(repo)
        vm.pickKind(SourceKind.M3U)
        vm.next() // Kind -> Endpoint
        vm.onNameChange("My Source")
        // URL missing
        vm.next()
        val state = vm.uiState.value as WizardUiState.Editing
        assertEquals(WizardStep.Endpoint, state.step)
        assertTrue(state.errorMessage?.contains("URL") == true)
    }

    @Test
    fun `Endpoint to Preview generates PreviewResult`() {
        val repo = mockk<SourceRepository>()
        val vm = AddSourceWizardViewModel(repo)
        vm.pickKind(SourceKind.M3uPlusEpg)
        vm.next()
        vm.onNameChange("X")
        vm.onUrlChange("https://provider/playlist.m3u")
        vm.next()
        val state = vm.uiState.value as WizardUiState.Editing
        assertEquals(WizardStep.Preview, state.step)
        assertTrue(state.preview != null)
        assertTrue(state.preview!!.estimatedChannels > 0)
        // M3U+EPG => programmes populated
        assertTrue(state.preview!!.estimatedProgrammes > 0)
    }

    @Test
    fun `Preview for plain M3U returns zero estimated programmes`() {
        val repo = mockk<SourceRepository>()
        val vm = AddSourceWizardViewModel(repo)
        vm.pickKind(SourceKind.M3U)
        vm.next()
        vm.onNameChange("X")
        vm.onUrlChange("https://x/p")
        vm.next()
        val state = vm.uiState.value as WizardUiState.Editing
        assertEquals(0, state.preview?.estimatedProgrammes)
    }

    @Test
    fun `Confirm submit happy path lands on Done`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<SourceRepository>()
        coEvery { repo.create(any()) } returns created

        val vm = AddSourceWizardViewModel(repo)

        vm.uiState.test {
            assertTrue(awaitItem() is WizardUiState.Editing) // initial

            vm.pickKind(SourceKind.M3uPlusEpg)
            // pickKind updates state
            assertTrue(awaitItem() is WizardUiState.Editing)
            vm.next()
            assertEquals(WizardStep.Endpoint, (awaitItem() as WizardUiState.Editing).step)
            vm.onNameChange("Provider")
            awaitItem()
            vm.onUrlChange("https://provider/playlist.m3u")
            awaitItem()
            vm.next()
            assertEquals(WizardStep.Preview, (awaitItem() as WizardUiState.Editing).step)
            vm.next()
            assertEquals(WizardStep.Confirm, (awaitItem() as WizardUiState.Editing).step)

            vm.next() // submit

            // Submitting
            val submitting = awaitItem()
            assertTrue(submitting is WizardUiState.Editing && submitting.submitting)

            // Done
            val done = awaitItem()
            assertTrue(done is WizardUiState.Done)
            assertEquals("s1", (done as WizardUiState.Done).source.id)
        }
    }

    @Test
    fun `Confirm submit ENTITLEMENT_REQUIRED surfaces friendly error and stays Editing`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<SourceRepository>()
        coEvery { repo.create(any<CreateSourceInput>()) } throws ApiException.Server(
            httpStatus = 402,
            code = "ENTITLEMENT_REQUIRED",
            message = "Source creation requires an active entitlement.",
        )
        val vm = AddSourceWizardViewModel(repo)
        vm.pickKind(SourceKind.M3U)
        vm.next(); vm.onNameChange("X"); vm.onUrlChange("https://x/p"); vm.next(); vm.next()
        // Now on Confirm
        vm.next() // submit -> error
        // Drain until we land on non-submitting Editing with an error
        var last = vm.uiState.value
        repeat(4) {
            val candidate = vm.uiState.value
            if (candidate is WizardUiState.Editing && !candidate.submitting && candidate.errorMessage != null) {
                last = candidate
                return@repeat
            }
        }
        assertTrue(last is WizardUiState.Editing)
        val editing = last as WizardUiState.Editing
        assertTrue(editing.errorMessage != null)
        // ApiErrorCopy maps ENTITLEMENT_REQUIRED to the premium user copy.
        assertTrue(editing.errorMessage!!.contains("entitlement", ignoreCase = true))
    }

    @Test
    fun `back from Endpoint returns to Kind`() {
        val repo = mockk<SourceRepository>()
        val vm = AddSourceWizardViewModel(repo)
        vm.pickKind(SourceKind.M3U)
        vm.next()
        vm.back()
        assertEquals(WizardStep.Kind, (vm.uiState.value as WizardUiState.Editing).step)
    }

    @Test
    fun `cancel resets wizard to Kind with empty draft`() {
        val repo = mockk<SourceRepository>()
        val vm = AddSourceWizardViewModel(repo)
        vm.pickKind(SourceKind.M3uPlusEpg)
        vm.next()
        vm.onNameChange("X")
        vm.cancel()
        val state = vm.uiState.value as WizardUiState.Editing
        assertEquals(WizardStep.Kind, state.step)
        assertEquals("", state.draft.name)
        assertNull(state.draft.kind)
    }
}
