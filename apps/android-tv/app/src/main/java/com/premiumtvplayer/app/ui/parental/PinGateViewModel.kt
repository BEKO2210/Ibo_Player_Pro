package com.premiumtvplayer.app.ui.parental

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.premiumtvplayer.app.data.api.ApiErrorCopy
import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.profiles.ProfileRepository
import com.premiumtvplayer.app.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Full-screen PIN gate. Shown before switching into a PIN-protected
 * profile. The server is the source of truth — we never validate the
 * PIN locally. Lockout state is surfaced with a countdown chip.
 */
sealed interface PinGateUiState {
    data class Editing(
        val pin: String = "",
        val submitting: Boolean = false,
        val errorMessage: String? = null,
        val failedAttemptCount: Int = 0,
        /** ISO timestamp; present while locked. */
        val lockedUntilIso: String? = null,
    ) : PinGateUiState

    data object Unlocked : PinGateUiState
}

@HiltViewModel
class PinGateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val profileId: String = requireNotNull(savedStateHandle[Routes.ProfileIdArg]) {
        "PinGate requires profileId nav arg"
    }

    private val _uiState = MutableStateFlow<PinGateUiState>(PinGateUiState.Editing())
    val uiState: StateFlow<PinGateUiState> = _uiState.asStateFlow()

    fun onPinChange(value: String) {
        val editing = (_uiState.value as? PinGateUiState.Editing) ?: return
        // Only accept digits; cap at 10 per backend contract.
        val clean = value.filter { it.isDigit() }.take(10)
        _uiState.value = editing.copy(pin = clean, errorMessage = null)
    }

    fun submit() {
        val editing = (_uiState.value as? PinGateUiState.Editing) ?: return
        if (editing.submitting) return
        if (editing.pin.length < 4) {
            _uiState.value = editing.copy(errorMessage = "PIN must be at least 4 digits.")
            return
        }
        // Local guard against obvious lock state.
        val lockedUntil = editing.lockedUntilIso?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
            _uiState.value = editing.copy(errorMessage = "Profile is locked. Try again later.")
            return
        }
        _uiState.value = editing.copy(submitting = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val result = profiles.verifyPin(profileId, editing.pin)
                if (result.ok) {
                    _uiState.value = PinGateUiState.Unlocked
                } else when (result.reason) {
                    "locked" -> _uiState.value = editing.copy(
                        submitting = false,
                        pin = "",
                        errorMessage = "Too many wrong tries. Try again later.",
                        lockedUntilIso = result.lockedUntil,
                    )
                    "no_pin" -> {
                        // Defensive: someone removed the PIN while we had
                        // the gate open. Let the caller in.
                        _uiState.value = PinGateUiState.Unlocked
                    }
                    else -> _uiState.value = editing.copy(
                        submitting = false,
                        pin = "",
                        errorMessage = "Wrong PIN.",
                        failedAttemptCount = result.failedAttemptCount ?: (editing.failedAttemptCount + 1),
                        lockedUntilIso = result.lockedUntil,
                    )
                }
            } catch (t: Throwable) {
                val msg = (t as? ApiException.Server)?.let {
                    ApiErrorCopy.forCode(it.code, it.message)
                } ?: t.message ?: "Could not verify PIN."
                _uiState.value = editing.copy(submitting = false, errorMessage = msg)
            }
        }
    }
}
