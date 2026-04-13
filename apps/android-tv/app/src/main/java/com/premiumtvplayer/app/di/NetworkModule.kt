package com.premiumtvplayer.app.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.premiumtvplayer.app.BuildConfig
import com.premiumtvplayer.app.data.api.PremiumPlayerApi
import com.premiumtvplayer.app.data.auth.FirebaseTokenSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenSource: FirebaseTokenSource): Interceptor = Interceptor { chain ->
        val request = chain.request()
        // Skip the unauth-required auth endpoints. Everything else needs Bearer.
        val skip = request.url.encodedPath.contains("/auth/")
        val withAuth = if (skip) {
            request
        } else {
            // runBlocking is acceptable here — OkHttp interceptors must be
            // synchronous, and `getIdToken` resolves in a few ms.
            val token = runBlocking { tokenSource.current() }
            if (token == null) request
            else request.newBuilder().addHeader("Authorization", "Bearer $token").build()
        }
        chain.proceed(withAuth)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val mediaType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(mediaType))
            .build()
    }

    @Provides
    @Singleton
    fun providePremiumPlayerApi(retrofit: Retrofit): PremiumPlayerApi =
        retrofit.create(PremiumPlayerApi::class.java)
}
