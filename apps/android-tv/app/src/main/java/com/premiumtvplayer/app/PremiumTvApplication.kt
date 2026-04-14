package com.premiumtvplayer.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Single Application class — Hilt entry point for the entire DI graph.
 *
 * Real bootstrap work (Firebase init, Crash reporter, etc.) lands in
 * Run 13 onboarding. For Run 11 we keep this empty so a fresh build is
 * guaranteed to start cleanly.
 */
@HiltAndroidApp
class PremiumTvApplication : Application()
