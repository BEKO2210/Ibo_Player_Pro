package com.premiumtvplayer.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.premiumtvplayer.app.data.api.ApiErrorCopy
import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.api.EntitlementDto
import com.premiumtvplayer.app.data.entitlement.EntitlementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TrialUiState {
    data object Idle : TrialUiState
    data object Submitting : TrialUiState
    data class Activated(val entitlement: EntitlementDto) : TrialUiState
    data class AlreadyConsumed(val entitlement: EntitlementDto?) : TrialUiState
    data class Error(val message: String) : TrialUiState
}

@HiltViewModel
class TrialActivationViewModel @Inject constructor(
    private val entitlements: EntitlementRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TrialUiState>(TrialUiState.Idle)
    val uiState: StateFlow<TrialUiState> = _uiState.asStateFlow()

    fun activate() {
        if (_uiState.value is TrialUiState.Submitting) return
        _uiState.value = TrialUiState.Submitting
        viewModelScope.launch {
            try {
                val ent = entitlements.startTrial()
                _uiState.value = TrialUiState.Activated(ent)
            } catch (t: Throwable) {
                _uiState.value = handleError(t)
            }
        }
    }

    fun reset() {
        _uiState.value = TrialUiState.Idle
    }

    private suspend fun handleError(t: Throwable): TrialUiState {
        val server = t as? ApiException.Server
        if (server?.code == "ENTITLEMENT_REQUIRED") {
            // Backend is signalling "trial already consumed" or "no eligible
            // state". Surface a friendlier UI; expose the live entitlement
            // so the screen can route the user to purchase/login if needed.
            val current = runCatching { entitlements.status() }.getOrNull()
            return TrialUiState.AlreadyConsumed(current)
        }
        val msg = server?.let { ApiErrorCopy.forCode(it.code, it.message) }
            ?: t.message ?: "Could not start your trial."
        return TrialUiState.Error(msg)
    }
}
