package com.premiumtvplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.premiumtvplayer.app.ui.PremiumTvApp
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-Activity entry point. All navigation happens via `PremiumTvApp`'s
 * NavHost (Run 13+).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PremiumTvTheme {
                PremiumTvApp()
            }
        }
    }
}
