package com.premiumtvplayer.app.ui.parental

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.premiumtvplayer.app.data.api.ApiErrorCopy
import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.api.DeviceDto
import com.premiumtvplayer.app.data.devices.DevicesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DeviceManagementUiState {
    data object Loading : DeviceManagementUiState
    data class Ready(
        val devices: List<DeviceDto>,
        val busyId: String? = null,
        val confirmingRevokeId: String? = null,
        val errorMessage: String? = null,
    ) : DeviceManagementUiState
    data class Error(val message: String) : DeviceManagementUiState
}

@HiltViewModel
class DeviceManagementViewModel @Inject constructor(
    private val devices: DevicesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DeviceManagementUiState>(DeviceManagementUiState.Loading)
    val uiState: StateFlow<DeviceManagementUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = DeviceManagementUiState.Loading
        viewModelScope.launch {
            try {
                _uiState.value = DeviceManagementUiState.Ready(devices.list())
            } catch (t: Throwable) {
                _uiState.value = DeviceManagementUiState.Error(mapError(t, "Could not load devices."))
            }
        }
    }

    fun requestRevoke(id: String) {
        val ready = _uiState.value as? DeviceManagementUiState.Ready ?: return
        _uiState.value = ready.copy(confirmingRevokeId = id, errorMessage = null)
    }

    fun cancelRevoke() {
        val ready = _uiState.value as? DeviceManagementUiState.Ready ?: return
        _uiState.value = ready.copy(confirmingRevokeId = null)
    }

    fun confirmRevoke() {
        val ready = _uiState.value as? DeviceManagementUiState.Ready ?: return
        val id = ready.confirmingRevokeId ?: return
        _uiState.value = ready.copy(busyId = id, confirmingRevokeId = null, errorMessage = null)
        viewModelScope.launch {
            try {
                val updated = devices.revoke(id)
                val replaced = ready.devices.map { if (it.id == updated.id) updated else it }
                _uiState.value = ready.copy(devices = replaced, busyId = null, confirmingRevokeId = null)
            } catch (t: Throwable) {
                _uiState.value = ready.copy(busyId = null, errorMessage = mapError(t, "Could not revoke device."))
            }
        }
    }

    private fun mapError(t: Throwable, fallback: String): String =
        (t as? ApiException.Server)?.let { ApiErrorCopy.forCode(it.code, it.message) }
            ?: t.message ?: fallback
}
