package com.premiumtvplayer.app.ui.parental

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.premiumtvplayer.app.data.api.ApiErrorCopy
import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.api.ProfileDto
import com.premiumtvplayer.app.data.profiles.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProfileManagementUiState {
    data object Loading : ProfileManagementUiState
    data class Ready(
        val profiles: List<ProfileDto>,
        val busyId: String? = null,
        val confirmingDeleteId: String? = null,
        val errorMessage: String? = null,
    ) : ProfileManagementUiState
    data class Error(val message: String) : ProfileManagementUiState
}

@HiltViewModel
class ProfileManagementViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileManagementUiState>(ProfileManagementUiState.Loading)
    val uiState: StateFlow<ProfileManagementUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = ProfileManagementUiState.Loading
        viewModelScope.launch {
            try {
                _uiState.value = ProfileManagementUiState.Ready(profiles.list())
            } catch (t: Throwable) {
                _uiState.value = ProfileManagementUiState.Error(mapError(t, "Could not load profiles."))
            }
        }
    }

    fun createProfile(name: String, isKids: Boolean, ageLimit: Int? = null, pin: String? = null) {
        val ready = _uiState.value as? ProfileManagementUiState.Ready ?: return
        if (ready.busyId != null) return
        _uiState.value = ready.copy(busyId = "__create", errorMessage = null)
        viewModelScope.launch {
            try {
                profiles.create(name = name, isKids = isKids, ageLimit = ageLimit, pin = pin)
                refresh()
            } catch (t: Throwable) {
                _uiState.value = ready.copy(busyId = null, errorMessage = mapError(t, "Could not create profile."))
            }
        }
    }

    fun rename(id: String, newName: String) = mutate(id) { profiles.update(id, name = newName) }
    fun setAgeLimit(id: String, ageLimit: Int?) = mutate(id) { profiles.update(id, ageLimit = ageLimit) }
    fun setPin(id: String, pin: String) = mutate(id) { profiles.update(id, pin = pin) }
    fun clearPin(id: String) = mutate(id) { profiles.update(id, clearPin = true) }
    fun makeDefault(id: String) = mutate(id) { profiles.update(id, isDefault = true) }

    fun requestDelete(id: String) {
        val ready = _uiState.value as? ProfileManagementUiState.Ready ?: return
        _uiState.value = ready.copy(confirmingDeleteId = id, errorMessage = null)
    }

    fun cancelDelete() {
        val ready = _uiState.value as? ProfileManagementUiState.Ready ?: return
        _uiState.value = ready.copy(confirmingDeleteId = null)
    }

    fun confirmDelete() {
        val ready = _uiState.value as? ProfileManagementUiState.Ready ?: return
        val id = ready.confirmingDeleteId ?: return
        _uiState.value = ready.copy(busyId = id, confirmingDeleteId = null, errorMessage = null)
        viewModelScope.launch {
            try {
                profiles.delete(id)
                refresh()
            } catch (t: Throwable) {
                _uiState.value = ready.copy(busyId = null, errorMessage = mapError(t, "Could not delete profile."))
            }
        }
    }

    private fun mutate(id: String, block: suspend () -> ProfileDto) {
        val ready = _uiState.value as? ProfileManagementUiState.Ready ?: return
        if (ready.busyId != null) return
        _uiState.value = ready.copy(busyId = id, errorMessage = null)
        viewModelScope.launch {
            try {
                block()
                refresh()
            } catch (t: Throwable) {
                _uiState.value = ready.copy(busyId = null, errorMessage = mapError(t, "Could not update profile."))
            }
        }
    }

    private fun mapError(t: Throwable, fallback: String): String =
        (t as? ApiException.Server)?.let { ApiErrorCopy.forCode(it.code, it.message) }
            ?: t.message ?: fallback
}
