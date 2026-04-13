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
import com.premiumtvplayer.app.data.home.HomeDeeplink
import com.premiumtvplayer.app.ui.home.HomeScreen
import com.premiumtvplayer.app.ui.billing.PaywallScreen
import com.premiumtvplayer.app.ui.nav.Routes
import com.premiumtvplayer.app.ui.onboarding.LoginScreen
import com.premiumtvplayer.app.ui.onboarding.ProfilePickerScreen
import com.premiumtvplayer.app.ui.player.PlayerScreen
import com.premiumtvplayer.app.ui.sources.AddSourceWizardScreen
import com.premiumtvplayer.app.ui.sources.EpgBrowseScreen
import com.premiumtvplayer.app.ui.sources.SourceManagementScreen
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
            // profileId is read out of the back-stack entry rather than
            // plumbed through HomeScreen, so deep-link handling has access
            // to it without a signature change.
            val profileId = it.arguments?.getString(Routes.ProfileIdArg)
            HomeScreen(
                onOpenDeeplink = { deeplink ->
                    when (deeplink) {
                        HomeDeeplink.AddSource -> navController.navigate(Routes.AddSource)
                        is HomeDeeplink.Source -> navController.navigate(Routes.Sources)
                        is HomeDeeplink.LiveChannel -> {
                            if (profileId != null) {
                                navController.navigate(
                                    Routes.play(
                                        profileId = profileId,
                                        sourceId = deeplink.sourceId,
                                        itemId = deeplink.channelId,
                                        itemType = "live",
                                        // Placeholder stream URL — a server-side
                                        // resolver that decrypts source creds
                                        // + signs temporary playback URLs is a
                                        // follow-up (logged in Parking Lot).
                                        // Apple's public BipBop HLS test feed.
                                        mediaUrl = DEMO_LIVE_HLS,
                                        title = "Live channel",
                                    ),
                                )
                            }
                        }
                        is HomeDeeplink.VodItem -> {
                            if (profileId != null) {
                                navController.navigate(
                                    Routes.play(
                                        profileId = profileId,
                                        sourceId = deeplink.sourceId,
                                        itemId = deeplink.itemId,
                                        itemType = "vod",
                                        mediaUrl = DEMO_VOD_MP4,
                                        title = "On-demand title",
                                    ),
                                )
                            }
                        }
                    }
                },
                onAddSource = { navController.navigate(Routes.AddSource) },
                onSignOut = {
                    navController.navigate(Routes.Welcome) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenPaywall = { navController.navigate(Routes.Paywall) },
                onOpenProfileSettings = { navController.navigate(Routes.ProfileManagement) },
                onOpenDeviceSettings = { navController.navigate(Routes.DeviceManagement) },
            )
        }
        composable(Routes.Sources) {
            SourceManagementScreen(
                onAddSource = { navController.navigate(Routes.AddSource) },
                onOpenEpg = { source -> navController.navigate(Routes.epgBrowse(source.id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.AddSource) {
            AddSourceWizardScreen(
                onDone = {
                    // Pop back to the sources list so the fresh source appears
                    // at the top of the rail.
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.EpgBrowsePattern,
            arguments = listOf(
                androidx.navigation.navArgument(Routes.SourceIdArg) {
                    type = androidx.navigation.NavType.StringType
                    nullable = false
                },
            ),
        ) {
            EpgBrowseScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.Paywall) {
            PaywallScreen(
                onPurchased = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.PlayerPattern,
            arguments = listOf(
                androidx.navigation.navArgument(Routes.SourceIdArg) {
                    type = androidx.navigation.NavType.StringType
                    nullable = false
                },
                androidx.navigation.navArgument(Routes.ItemIdArg) {
                    type = androidx.navigation.NavType.StringType
                    nullable = false
                },
                androidx.navigation.navArgument(Routes.ItemTypeArg) {
                    type = androidx.navigation.NavType.StringType
                    nullable = false
                },
                androidx.navigation.navArgument(Routes.ProfileIdArg) {
                    type = androidx.navigation.NavType.StringType
                    nullable = false
                },
                androidx.navigation.navArgument(Routes.MediaUrlArg) {
                    type = androidx.navigation.NavType.StringType
                    nullable = false
                },
                androidx.navigation.navArgument(Routes.ItemTitleArg) {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val mediaUrl = entry.arguments?.getString(Routes.MediaUrlArg).orEmpty()
            val title = entry.arguments?.getString(Routes.ItemTitleArg).orEmpty()
            PlayerScreen(
                mediaUrl = mediaUrl,
                itemTitle = title,
                onExit = { navController.popBackStack() },
            )
        }
        composable(Routes.ProfileManagement) {
            com.premiumtvplayer.app.ui.parental.ProfileManagementScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DeviceManagement) {
            com.premiumtvplayer.app.ui.parental.DeviceManagementScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.PinGatePattern,
            arguments = listOf(
                androidx.navigation.navArgument(Routes.ProfileIdArg) {
                    type = androidx.navigation.NavType.StringType
                    nullable = false
                },
                androidx.navigation.navArgument(Routes.ProfileNameArg) {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val profileName = entry.arguments?.getString(Routes.ProfileNameArg)
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                .orEmpty()
            com.premiumtvplayer.app.ui.parental.PinGateScreen(
                profileName = profileName,
                onUnlocked = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

// Public test streams — placeholders until a proper server-side
// media-URL resolver lands. See CLAUDE.md Parking Lot "Playback URL
// resolver".
private const val DEMO_LIVE_HLS =
    "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"
private const val DEMO_VOD_MP4 =
    "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

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
