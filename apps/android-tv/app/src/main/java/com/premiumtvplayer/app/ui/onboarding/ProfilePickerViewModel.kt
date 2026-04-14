package com.premiumtvplayer.app.ui.onboarding

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

sealed interface ProfilePickerUiState {
    data object Loading : ProfilePickerUiState
    data class Ready(val profiles: List<ProfileDto>, val canAddMore: Boolean) : ProfilePickerUiState
    data class Error(val message: String) : ProfilePickerUiState
}

@HiltViewModel
class ProfilePickerViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfilePickerUiState>(ProfilePickerUiState.Loading)
    val uiState: StateFlow<ProfilePickerUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = ProfilePickerUiState.Loading
        viewModelScope.launch {
            try {
                val items = profiles.list()
                // The backend enforces the cap itself (Run 10 ProfileService);
                // we show an "Add" tile optimistically unless we're already
                // at the family limit (5) locally. Real cap check happens
                // on POST /v1/profiles and surfaces as 409 SLOT_FULL.
                val canAddMore = items.size < 5
                _uiState.value = ProfilePickerUiState.Ready(items, canAddMore)
            } catch (t: Throwable) {
                val msg = (t as? ApiException.Server)?.let {
                    ApiErrorCopy.forCode(it.code, it.message)
                } ?: t.message ?: "Could not load profiles."
                _uiState.value = ProfilePickerUiState.Error(msg)
            }
        }
    }
}
