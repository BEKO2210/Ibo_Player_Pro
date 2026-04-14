package com.premiumtvplayer.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.premiumtvplayer.app.data.util.Clock
import com.premiumtvplayer.app.data.util.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** Tags the DataStore<Preferences> instance dedicated to the
 *  entitlement cache. Future caches should declare their own qualifier
 *  + their own preferencesDataStore name to avoid file contention. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EntitlementCacheStore

// Property delegate must live at top level (preferencesDataStore is an
// extension on Context). This file is its only call site, so the
// delegate name + the file name on disk are managed in one place.
private val Context.entitlementDataStoreDelegate
        : DataStore<Preferences> by preferencesDataStore(name = "entitlement_cache")

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    @EntitlementCacheStore
    fun provideEntitlementDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.entitlementDataStoreDelegate

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock
}
