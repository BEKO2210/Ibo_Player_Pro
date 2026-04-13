package com.premiumtvplayer.app.ui.sources

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.premiumtvplayer.app.data.api.ApiErrorCopy
import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.api.SourceDto
import com.premiumtvplayer.app.data.sources.SourceRepository
import com.premiumtvplayer.app.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SourceManagementUiState {
    data object Loading : SourceManagementUiState
    data class Ready(
        val sources: List<SourceDto>,
        val confirmingDeleteId: String? = null,
        val lastError: String? = null,
        val busyId: String? = null,
    ) : SourceManagementUiState
    data class Error(val message: String) : SourceManagementUiState
}

@HiltViewModel
class SourceManagementViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sources: SourceRepository,
) : ViewModel() {

    private val profileId: String? = savedStateHandle[Routes.ProfileIdArg]

    private val _uiState = MutableStateFlow<SourceManagementUiState>(SourceManagementUiState.Loading)
    val uiState: StateFlow<SourceManagementUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = SourceManagementUiState.Loading
        viewModelScope.launch {
            try {
                val list = sources.list(profileId)
                _uiState.value = SourceManagementUiState.Ready(list)
            } catch (t: Throwable) {
                _uiState.value = SourceManagementUiState.Error(mapError(t, fallback = "Could not load sources."))
            }
        }
    }

    fun togglePaused(source: SourceDto) {
        val ready = _uiState.value as? SourceManagementUiState.Ready ?: return
        if (ready.busyId != null) return
        _uiState.value = ready.copy(busyId = source.id, lastError = null)
        viewModelScope.launch {
            try {
                val updated = sources.setActive(source.id, !source.isActive)
                val replaced = ready.sources.map { if (it.id == updated.id) updated else it }
                _uiState.value = ready.copy(sources = replaced, busyId = null)
            } catch (t: Throwable) {
                _uiState.value = ready.copy(
                    busyId = null,
                    lastError = mapError(t, fallback = "Could not update source."),
                )
            }
        }
    }

    fun requestDelete(sourceId: String) {
        val ready = _uiState.value as? SourceManagementUiState.Ready ?: return
        _uiState.value = ready.copy(confirmingDeleteId = sourceId, lastError = null)
    }

    fun cancelDelete() {
        val ready = _uiState.value as? SourceManagementUiState.Ready ?: return
        _uiState.value = ready.copy(confirmingDeleteId = null)
    }

    fun confirmDelete() {
        val ready = _uiState.value as? SourceManagementUiState.Ready ?: return
        val target = ready.confirmingDeleteId ?: return
        _uiState.value = ready.copy(busyId = target, confirmingDeleteId = null, lastError = null)
        viewModelScope.launch {
            try {
                sources.delete(target)
                val remaining = ready.sources.filterNot { it.id == target }
                _uiState.value = ready.copy(
                    sources = remaining,
                    busyId = null,
                    confirmingDeleteId = null,
                )
            } catch (t: Throwable) {
                _uiState.value = ready.copy(
                    busyId = null,
                    lastError = mapError(t, fallback = "Could not delete source."),
                )
            }
        }
    }

    private fun mapError(t: Throwable, fallback: String): String =
        (t as? ApiException.Server)?.let { ApiErrorCopy.forCode(it.code, it.message) }
            ?: t.message ?: fallback
}
