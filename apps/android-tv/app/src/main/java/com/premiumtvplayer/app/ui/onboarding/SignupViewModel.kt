package com.premiumtvplayer.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.premiumtvplayer.app.data.api.AccountSnapshotResponse
import com.premiumtvplayer.app.data.api.ApiErrorCopy
import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SignupUiState {
    data class Editing(
        val email: String = "",
        val password: String = "",
        val submitting: Boolean = false,
        val errorMessage: String? = null,
    ) : SignupUiState

    data class Done(val snapshot: AccountSnapshotResponse) : SignupUiState
}

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SignupUiState>(SignupUiState.Editing())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = updateEditing { it.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = updateEditing { it.copy(password = value, errorMessage = null) }

    fun submit() {
        val current = _uiState.value as? SignupUiState.Editing ?: return
        if (current.submitting) return
        if (!isEmailValid(current.email)) {
            updateEditing { it.copy(errorMessage = "Please enter a valid email.") }
            return
        }
        if (current.password.length < 8) {
            updateEditing { it.copy(errorMessage = "Password must be at least 8 characters.") }
            return
        }
        updateEditing { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val snap = auth.register(current.email.trim(), current.password)
                _uiState.value = SignupUiState.Done(snap)
            } catch (t: Throwable) {
                val message = (t as? ApiException.Server)?.let {
                    ApiErrorCopy.forCode(it.code, it.message)
                } ?: t.message ?: "Sign-up failed."
                updateEditing { it.copy(submitting = false, errorMessage = message) }
            }
        }
    }

    private fun updateEditing(block: (SignupUiState.Editing) -> SignupUiState.Editing) {
        _uiState.update { state ->
            (state as? SignupUiState.Editing)?.let(block) ?: state
        }
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        fun isEmailValid(email: String): Boolean = EMAIL_REGEX.matches(email.trim())
    }
}
