package com.premiumtvplayer.app.ui.sources

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.premiumtvplayer.app.data.api.ApiErrorCopy
import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.epg.EpgBrowseSnapshot
import com.premiumtvplayer.app.data.epg.EpgRepository
import com.premiumtvplayer.app.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EpgUiState {
    data object Loading : EpgUiState
    data class Ready(val snapshot: EpgBrowseSnapshot, val focusedProgrammeId: String? = null) : EpgUiState
    data class Error(val message: String) : EpgUiState
}

@HiltViewModel
class EpgBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val epg: EpgRepository,
) : ViewModel() {

    private val sourceId: String = requireNotNull(savedStateHandle[Routes.SourceIdArg]) {
        "EpgBrowseScreen requires a sourceId nav argument."
    }

    private val _uiState = MutableStateFlow<EpgUiState>(EpgUiState.Loading)
    val uiState: StateFlow<EpgUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = EpgUiState.Loading
        viewModelScope.launch {
            try {
                val snapshot = epg.browse(sourceId)
                _uiState.value = EpgUiState.Ready(snapshot)
            } catch (t: Throwable) {
                val msg = (t as? ApiException.Server)?.let {
                    ApiErrorCopy.forCode(it.code, it.message)
                } ?: t.message ?: "Could not load the programme guide."
                _uiState.value = EpgUiState.Error(msg)
            }
        }
    }

    fun onFocusProgramme(id: String?) {
        val ready = _uiState.value as? EpgUiState.Ready ?: return
        _uiState.value = ready.copy(focusedProgrammeId = id)
    }
}
