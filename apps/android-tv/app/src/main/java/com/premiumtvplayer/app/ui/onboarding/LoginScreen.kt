package com.premiumtvplayer.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme

@Composable
fun LoginScreen(
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    onSwitchToSignup: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is LoginUiState.Done) {
            onSuccess()
        }
    }

    val editing = state as? LoginUiState.Editing ?: LoginUiState.Editing()

    AuthFormScaffold(
        title = "Sign in",
        subtitle = "Welcome back. Continue where you left off.",
        email = editing.email,
        onEmailChange = viewModel::onEmailChange,
        password = editing.password,
        onPasswordChange = viewModel::onPasswordChange,
        submitting = editing.submitting,
        errorMessage = editing.errorMessage,
        primaryCta = "Sign In",
        onSubmit = viewModel::submit,
        onBack = onBack,
        switchCta = "Create a new account",
        onSwitch = onSwitchToSignup,
    )
}

@Preview(name = "LoginScreen · editing", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun LoginScreenPreview() {
    PremiumTvTheme {
        AuthFormScaffold(
            title = "Sign in",
            subtitle = "Welcome back. Continue where you left off.",
            email = "",
            onEmailChange = {},
            password = "",
            onPasswordChange = {},
            submitting = false,
            errorMessage = null,
            primaryCta = "Sign In",
            onSubmit = {},
            onBack = {},
            switchCta = "Create a new account",
            onSwitch = {},
        )
    }
}
