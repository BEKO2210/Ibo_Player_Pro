package com.premiumtvplayer.app.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.premiumtvplayer.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Firebase Admin is initialized **programmatically** from BuildConfig
 * fields rather than from a `google-services.json` + the
 * `com.google.gms.google-services` Gradle plugin. This keeps the project
 * importable without a real Firebase JSON in the repo, and makes
 * per-flavor / per-env config trivial later.
 *
 * To wire your real Firebase project, set in `local.properties`:
 *   firebaseApiKey=AIza...
 *   firebaseProjectId=premium-player-prod
 *   firebaseApplicationId=1:000:android:abc...
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseApp(@ApplicationContext context: Context): FirebaseApp {
        val existing = runCatching { FirebaseApp.getInstance() }.getOrNull()
        if (existing != null) return existing

        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .build()
        return FirebaseApp.initializeApp(context, options)
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(firebaseApp: FirebaseApp): FirebaseAuth =
        Firebase.auth(firebaseApp)
}
