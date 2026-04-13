package com.premiumtvplayer.app.ui.player

import androidx.lifecycle.SavedStateHandle
import com.premiumtvplayer.app.data.api.PlaybackSessionDto
import com.premiumtvplayer.app.data.playback.PlaybackItemType
import com.premiumtvplayer.app.data.playback.PlaybackRepository
import com.premiumtvplayer.app.data.playback.PlaybackStateValue
import com.premiumtvplayer.app.ui.nav.Routes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
class PlayerViewModelTest {

    private val session = PlaybackSessionDto(
        id = "sess1",
        profileId = "p1",
        sourceId = "s1",
        itemId = "vod-1",
        itemType = "vod",
        state = "starting",
        latestPositionSeconds = 0,
        sessionStartedAt = "2026-04-13T12:00:00.000Z",
    )

    private fun savedState() = SavedStateHandle(
        mapOf(
            Routes.ProfileIdArg to "p1",
            Routes.SourceIdArg to "s1",
            Routes.ItemIdArg to "vod-1",
            Routes.ItemTypeArg to "vod",
        ),
    )

    /** Clock that blocks forever on sleep — stops the heartbeat loop
     *  from actually ticking in unit tests. */
    private object NoTickClock : PlayerClock {
        override val heartbeatIntervalMs: Long = 10_000L
        override suspend fun sleep(millis: Long) {
            // Hang forever; tests that want to fire a heartbeat call
            // replace this clock with one that completes once.
            CompletableDeferred<Unit>().await()
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init starts session and transitions to Buffering`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<PlaybackRepository>()
        coEvery { repo.start("p1", "s1", "vod-1", PlaybackItemType.Vod, any()) } returns session

        val vm = PlayerViewModel(savedState(), repo).also { it.clock = NoTickClock }

        val state = vm.uiState.value
        assertTrue(state is PlayerUiState.Buffering)
    }

    @Test
    fun `onProgress updates Playing state with position and duration`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<PlaybackRepository>()
        coEvery { repo.start(any(), any(), any(), any(), any()) } returns session

        val vm = PlayerViewModel(savedState(), repo).also { it.clock = NoTickClock }
        vm.onPlayingStateChanged(true)
        vm.onProgress(positionSeconds = 42, durationSeconds = 3600)

        val state = vm.uiState.value
        assertTrue(state is PlayerUiState.Playing)
        val playing = state as PlayerUiState.Playing
        assertEquals(42, playing.positionSeconds)
        assertEquals(3600, playing.durationSeconds)
    }

    @Test
    fun `onPlayingStateChanged false transitions to Paused`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<PlaybackRepository>()
        coEvery { repo.start(any(), any(), any(), any(), any()) } returns session
        val vm = PlayerViewModel(savedState(), repo).also { it.clock = NoTickClock }
        vm.onPlayingStateChanged(true)
        vm.onPlayingStateChanged(false)
        assertTrue(vm.uiState.value is PlayerUiState.Paused)
    }

    @Test
    fun `heartbeat fires exactly once when clock completes one sleep`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<PlaybackRepository>()
        coEvery { repo.start(any(), any(), any(), any(), any()) } returns session
        coEvery { repo.heartbeat(any(), any(), any(), any()) } returns session.copy(state = "playing")

        // Clock that completes one sleep, then hangs forever — so the
        // loop fires one heartbeat then blocks.
        val oneShotClock = object : PlayerClock {
            override val heartbeatIntervalMs: Long = 10L
            private var first = true
            override suspend fun sleep(millis: Long) {
                if (first) {
                    first = false
                    return
                }
                CompletableDeferred<Unit>().await()
            }
        }

        val vm = PlayerViewModel(savedState(), repo).also { it.clock = oneShotClock }
        vm.onPlayingStateChanged(true)
        vm.onProgress(positionSeconds = 100, durationSeconds = 500)

        // Give the scheduler one turn to flush.
        kotlinx.coroutines.yield()

        coVerify(atLeast = 1) {
            repo.heartbeat("sess1", 100, PlaybackStateValue.Playing, 500)
        }
    }

    @Test
    fun `stop posts final position and emits Stopped`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<PlaybackRepository>()
        coEvery { repo.start(any(), any(), any(), any(), any()) } returns session
        coEvery { repo.stop(any(), any(), any(), any()) } returns session.copy(state = "stopped")

        val vm = PlayerViewModel(savedState(), repo).also { it.clock = NoTickClock }
        vm.onPlayingStateChanged(true)
        vm.onProgress(positionSeconds = 1200, durationSeconds = 3600)
        vm.stop(completed = false)
        kotlinx.coroutines.yield()

        coVerify { repo.stop("sess1", 1200, 3600, false) }
        assertTrue(vm.uiState.value is PlayerUiState.Stopped)
    }
}
