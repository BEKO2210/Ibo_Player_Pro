package com.premiumtvplayer.app.data.auth

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a fresh Firebase ID token for the currently signed-in user,
 * exposed as a suspend function. Used by the OkHttp `AuthInterceptor` and
 * by the auth-call paths in `AuthRepository`.
 */
@Singleton
class FirebaseTokenSource @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) {
    /**
     * Returns the cached ID token if not yet expired; otherwise forces a
     * refresh. Returns null when no Firebase user is signed in (e.g. on
     * the Welcome / Sign-up screens before auth happens).
     */
    suspend fun current(forceRefresh: Boolean = false): String? {
        val user = firebaseAuth.currentUser ?: return null
        return user.getIdToken(forceRefresh).await().token
    }
}
