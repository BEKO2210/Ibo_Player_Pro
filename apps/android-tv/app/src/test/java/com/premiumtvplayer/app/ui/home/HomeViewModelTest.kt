package com.premiumtvplayer.app.ui.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.api.ProfileDto
import com.premiumtvplayer.app.data.api.SourceDto
import com.premiumtvplayer.app.data.home.HomeHero
import com.premiumtvplayer.app.data.home.HomeRepository
import com.premiumtvplayer.app.data.home.HomeRow
import com.premiumtvplayer.app.data.home.HomeSnapshot
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
class HomeViewModelTest {
    private val source = SourceDto(
        id = "s1",
        name = "World TV Plus",
        kind = "m3u_plus_epg",
        isActive = true,
        validationStatus = "valid",
        itemCountEstimate = 480,
        createdAt = "2026-04-13T12:00:00.000Z",
    )
    private val profile = ProfileDto(
        id = "p1",
        name = "Alex",
        isKids = false,
        ageLimit = null,
        isDefault = true,
        hasPin = false,
        createdAt = "2026-04-13T12:00:00.000Z",
    )

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `snapshot with sources - Populated with profile resolved from nav arg`() = runTest(UnconfinedTestDispatcher()) {
        val home = mockk<HomeRepository>()
        val profiles = mockk<ProfileRepository>()
        coEvery { home.snapshot("p1") } returns HomeSnapshot(
            sources = listOf(source),
            heroes = listOf(
                HomeHero(
                    id = "h1",
                    title = source.name,
                    subtitle = "",
                    chips = emptyList(),
                    deeplink = com.premiumtvplayer.app.data.home.HomeDeeplink.Source(source.id),
                    backgroundAccent = HomeHero.BackgroundAccent.Blue,
                ),
            ),
            rows = listOf(
                HomeRow(key = HomeRow.RowKey.ContinueWatching, title = "Continue Watching", tiles = emptyList()),
            ),
        )
        coEvery { profiles.list() } returns listOf(profile)

        val savedState = SavedStateHandle(mapOf(Routes.ProfileIdArg to "p1"))
        val vm = HomeViewModel(savedState, home, profiles)

        vm.uiState.test {
            val first = awaitItem()
            // VM starts refresh in init; we may see Loading first or go straight to Populated under UnconfinedTestDispatcher.
            val terminal = if (first is HomeUiState.Loading) awaitItem() else first
            assertTrue(terminal is HomeUiState.Populated)
            val populated = terminal as HomeUiState.Populated
            assertEquals("Alex", populated.profile?.name)
            assertEquals(1, populated.snapshot.sources.size)
            assertEquals(1, populated.snapshot.rows.size)
        }
    }

    @Test
    fun `snapshot with zero sources - EmptySource`() = runTest(UnconfinedTestDispatcher()) {
        val home = mockk<HomeRepository>()
        val profiles = mockk<ProfileRepository>()
        coEvery { home.snapshot(any()) } returns HomeSnapshot(
            sources = emptyList(),
            heroes = listOf(
                HomeHero(
                    id = "onboarding",
                    title = "Add your first source",
                    subtitle = "",
                    chips = emptyList(),
                    deeplink = com.premiumtvplayer.app.data.home.HomeDeeplink.AddSource,
                    backgroundAccent = HomeHero.BackgroundAccent.Blue,
                ),
            ),
            rows = emptyList(),
        )
        coEvery { profiles.list() } returns emptyList()

        val vm = HomeViewModel(SavedStateHandle(), home, profiles)

        vm.uiState.test {
            val first = awaitItem()
            val terminal = if (first is HomeUiState.Loading) awaitItem() else first
            assertTrue(terminal is HomeUiState.EmptySource)
        }
    }

    @Test
    fun `snapshot failure - Error with mapped message`() = runTest(UnconfinedTestDispatcher()) {
        val home = mockk<HomeRepository>()
        val profiles = mockk<ProfileRepository>()
        coEvery { home.snapshot(any()) } throws ApiException.Server(
            httpStatus = 401,
            code = "UNAUTHORIZED",
            message = "Missing Authorization: Bearer token",
        )
        coEvery { profiles.list() } returns emptyList()

        val vm = HomeViewModel(SavedStateHandle(), home, profiles)

        vm.uiState.test {
            val first = awaitItem()
            val terminal = if (first is HomeUiState.Loading) awaitItem() else first
            assertTrue(terminal is HomeUiState.Error)
            val err = terminal as HomeUiState.Error
            // ApiErrorCopy maps UNAUTHORIZED -> "Your session expired..."
            assertTrue(err.message.contains("session expired") || err.message.contains("sign in"))
        }
    }
}
