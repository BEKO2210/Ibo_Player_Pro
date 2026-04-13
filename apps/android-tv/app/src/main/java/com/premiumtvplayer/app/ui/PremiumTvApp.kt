package com.premiumtvplayer.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Text
import com.premiumtvplayer.app.ui.components.BootProgress
import com.premiumtvplayer.app.ui.components.BrandLogo
import com.premiumtvplayer.app.ui.components.BrandLogoSize
import com.premiumtvplayer.app.ui.home.HomeScreen
import com.premiumtvplayer.app.ui.nav.Routes
import com.premiumtvplayer.app.ui.onboarding.LoginScreen
import com.premiumtvplayer.app.ui.onboarding.ProfilePickerScreen
import com.premiumtvplayer.app.ui.onboarding.SignupScreen
import com.premiumtvplayer.app.ui.onboarding.TrialActivationScreen
import com.premiumtvplayer.app.ui.onboarding.WelcomeScreen
import com.premiumtvplayer.app.ui.theme.LocalPremiumDurations
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumEasing
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType
import kotlinx.coroutines.delay

/**
 * App root. Owns the NavHost for the V1 graph:
 *
 *   Boot ──► Welcome ──► Signup / Login ──► TrialActivation ──► ProfilePicker ──► Home (Run 14)
 *
 * Screen-to-screen transitions use the premium motion language: a soft
 * fade through `PremiumEasing.Premium` over `durations.short`. This
 * reads "editorial", not "jittery".
 */
@Composable
fun PremiumTvApp(navController: NavHostController = rememberNavController()) {
    val durations = LocalPremiumDurations.current

    NavHost(
        navController = navController,
        startDestination = Routes.Boot,
        enterTransition = {
            fadeIn(animationSpec = tween(durationMillis = durations.short, easing = PremiumEasing.Premium))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(durationMillis = durations.short, easing = PremiumEasing.Standard))
        },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(Routes.Boot) {
            BootScreen(onReady = {
                navController.navigate(Routes.Welcome) {
                    popUpTo(Routes.Boot) { inclusive = true }
                }
            })
        }
        composable(Routes.Welcome) {
            WelcomeScreen(
                onSignIn = { navController.navigate(Routes.Login) },
                onCreateAccount = { navController.navigate(Routes.Signup) },
            )
        }
        composable(Routes.Signup) {
            SignupScreen(
                onSuccess = { navController.navigate(Routes.TrialActivation) },
                onBack = { navController.popBackStack() },
                onSwitchToLogin = {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Welcome)
                    }
                },
            )
        }
        composable(Routes.Login) {
            LoginScreen(
                onSuccess = { navController.navigate(Routes.ProfilePicker) {
                    popUpTo(Routes.Welcome)
                } },
                onBack = { navController.popBackStack() },
                onSwitchToSignup = {
                    navController.navigate(Routes.Signup) {
                        popUpTo(Routes.Welcome)
                    }
                },
            )
        }
        composable(Routes.TrialActivation) {
            TrialActivationScreen(
                onActivated = {
                    navController.navigate(Routes.ProfilePicker) {
                        popUpTo(Routes.Welcome)
                    }
                },
                onSkip = {
                    navController.navigate(Routes.ProfilePicker) {
                        popUpTo(Routes.Welcome)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ProfilePicker) {
            ProfilePickerScreen(
                onProfileSelected = { profile ->
                    navController.navigate(Routes.home(profile.id)) {
                        popUpTo(Routes.ProfilePicker) { inclusive = true }
                    }
                },
                onAddProfile = { /* Run 18 — profile CRUD UI */ },
                onManageProfiles = { /* Run 18 */ },
            )
        }
        composable(
            route = Routes.HomePattern,
            arguments = listOf(
                androidx.navigation.navArgument(Routes.ProfileIdArg) {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            HomeScreen(
                onOpenDeeplink = { /* Run 15 — source management / player deep-links */ },
                onAddSource = { /* Run 15 — add source flow */ },
                onSignOut = {
                    // Drop auth state + return to Welcome.
                    navController.navigate(Routes.Welcome) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}

@Composable
private fun BootScreen(onReady: () -> Unit) {
    val spacing = LocalPremiumSpacing.current

    LaunchedEffect(Unit) {
        // Brief, deliberate boot pause — reads as "premium product settling
        // in", not as a "loading spinner". Run 13+ replaces this with
        // Firebase-auth-state check that can route authed users straight
        // to ProfilePicker.
        delay(1_200)
        onReady()
    }

    val backgroundBrush = Brush.radialGradient(
        colors = listOf(
            PremiumColors.AccentBlueDeep.copy(alpha = 0.18f),
            PremiumColors.BackgroundBase,
        ),
        center = Offset(x = 0f, y = 0f),
        radius = 1800f,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = spacing.pageGutter),
        ) {
            BrandLogo(size = BrandLogoSize.Splash)
            Spacer(modifier = Modifier.height(spacing.xxl))
            Text(
                text = "Premium TV Player",
                style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(spacing.m))
            Text(
                text = "Your sources. Your library. Beautifully.",
                style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(spacing.huge))
            BootProgress()
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(spacing.xxl),
        ) {
            Text(
                text = "v0.1.0 · build 1",
                style = PremiumType.LabelSmall.copy(color = PremiumColors.OnSurfaceDim),
            )
        }
    }
}

/** Temporary home placeholder. Run 14 replaces this. */
@Composable
private fun HomePlaceholder() {
    val spacing = LocalPremiumSpacing.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumColors.BackgroundBase),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            BrandLogo(size = BrandLogoSize.Hero)
            Text(
                text = "Home coming in Run 14",
                style = PremiumType.Headline.copy(color = PremiumColors.OnSurfaceHigh),
            )
        }
    }
}

@Preview(name = "BootScreen", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun BootScreenPreview() {
    PremiumTvTheme { BootScreen(onReady = {}) }
}
