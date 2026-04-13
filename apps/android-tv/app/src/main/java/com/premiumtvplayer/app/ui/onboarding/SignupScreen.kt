package com.premiumtvplayer.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme

@Composable
fun SignupScreen(
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    onSwitchToLogin: () -> Unit,
    viewModel: SignupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is SignupUiState.Done) {
            onSuccess()
        }
    }

    val editing = state as? SignupUiState.Editing ?: SignupUiState.Editing()

    AuthFormScaffold(
        title = "Create your account",
        subtitle = "A 14-day trial starts right after sign-up. No payment needed.",
        email = editing.email,
        onEmailChange = viewModel::onEmailChange,
        password = editing.password,
        onPasswordChange = viewModel::onPasswordChange,
        submitting = editing.submitting,
        errorMessage = editing.errorMessage,
        primaryCta = "Create Account",
        onSubmit = viewModel::submit,
        onBack = onBack,
        switchCta = "I already have an account",
        onSwitch = onSwitchToLogin,
    )
}

@Preview(name = "SignupScreen · editing", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun SignupScreenPreview() {
    PremiumTvTheme {
        AuthFormScaffold(
            title = "Create your account",
            subtitle = "A 14-day trial starts right after sign-up. No payment needed.",
            email = "alex@example.com",
            onEmailChange = {},
            password = "",
            onPasswordChange = {},
            submitting = false,
            errorMessage = null,
            primaryCta = "Create Account",
            onSubmit = {},
            onBack = {},
            switchCta = "I already have an account",
            onSwitch = {},
        )
    }
}
