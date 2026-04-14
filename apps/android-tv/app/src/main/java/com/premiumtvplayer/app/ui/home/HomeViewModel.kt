package com.premiumtvplayer.app.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.premiumtvplayer.app.data.api.ApiErrorCopy
import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.api.ProfileDto
import com.premiumtvplayer.app.data.home.HomeRepository
import com.premiumtvplayer.app.data.home.HomeSnapshot
import com.premiumtvplayer.app.data.profiles.ProfileRepository
import com.premiumtvplayer.app.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** Fresh account, or one whose sources were all removed. */
    data class EmptySource(
        val profile: ProfileDto?,
    ) : HomeUiState

    /** Normal state — hero carousel + rows. */
    data class Populated(
        val profile: ProfileDto?,
        val snapshot: HomeSnapshot,
    ) : HomeUiState

    data class Error(val message: String, val isEntitlementGated: Boolean = false) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val home: HomeRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    /** Profile id taken from the nav argument, or null (account-scoped). */
    private val profileId: String? = savedStateHandle[Routes.ProfileIdArg]

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                val snap = home.snapshot(profileId)
                val profile = resolveProfile(profileId)
                _uiState.value = if (snap.sources.isEmpty()) {
                    HomeUiState.EmptySource(profile)
                } else {
                    HomeUiState.Populated(profile, snap)
                }
            } catch (t: Throwable) {
                val server = t as? ApiException.Server
                val msg = server?.let { ApiErrorCopy.forCode(it.code, it.message) }
                    ?: t.message ?: "Could not load your home."
                _uiState.value = HomeUiState.Error(
                    message = msg,
                    isEntitlementGated = server?.code == "ENTITLEMENT_REQUIRED",
                )
            }
        }
    }

    private suspend fun resolveProfile(profileId: String?): ProfileDto? {
        if (profileId == null) return null
        return runCatching { profiles.list().firstOrNull { it.id == profileId } }.getOrNull()
    }
}
