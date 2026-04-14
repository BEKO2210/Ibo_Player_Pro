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

sealed interface LoginUiState {
    data class Editing(
        val email: String = "",
        val password: String = "",
        val submitting: Boolean = false,
        val errorMessage: String? = null,
    ) : LoginUiState

    data class Done(val snapshot: AccountSnapshotResponse) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Editing())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = update { it.copy(password = value, errorMessage = null) }

    fun submit() {
        val current = _uiState.value as? LoginUiState.Editing ?: return
        if (current.submitting) return
        if (!SignupViewModel.isEmailValid(current.email)) {
            update { it.copy(errorMessage = "Please enter a valid email.") }
            return
        }
        if (current.password.isEmpty()) {
            update { it.copy(errorMessage = "Enter your password.") }
            return
        }
        update { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val snap = auth.login(current.email.trim(), current.password)
                _uiState.value = LoginUiState.Done(snap)
            } catch (t: Throwable) {
                val message = (t as? ApiException.Server)?.let {
                    ApiErrorCopy.forCode(it.code, it.message)
                } ?: t.message ?: "Login failed."
                update { it.copy(submitting = false, errorMessage = message) }
            }
        }
    }

    private fun update(block: (LoginUiState.Editing) -> LoginUiState.Editing) {
        _uiState.update { state ->
            (state as? LoginUiState.Editing)?.let(block) ?: state
        }
    }
}
