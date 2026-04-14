package com.premiumtvplayer.app.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.premiumtvplayer.app.data.api.AccountSnapshotResponse
import com.premiumtvplayer.app.data.api.ApiErrorMapper
import com.premiumtvplayer.app.data.api.FirebaseTokenRequest
import com.premiumtvplayer.app.data.api.PremiumPlayerApi
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single auth surface used by ViewModels.
 *
 * Each method does the same shape:
 *   1. obtain a Firebase ID token (via Firebase SDK call)
 *   2. POST it to our backend (/v1/auth/{register,login,refresh})
 *   3. return the AccountSnapshot or throw a normalized [ApiException]
 *
 * The backend's account-sync semantics are idempotent, so it's safe to
 * call register on an existing account or login on a fresh one — the
 * server will upsert as appropriate.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val api: PremiumPlayerApi,
) {
    suspend fun register(
        email: String,
        password: String,
        locale: String? = null,
    ): AccountSnapshotResponse = wrap {
        val cred = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        val token = cred.user?.getIdToken(false)?.await()?.token
            ?: error("Firebase did not return an ID token after registration")
        api.register(FirebaseTokenRequest(firebaseIdToken = token, locale = locale))
    }

    suspend fun login(
        email: String,
        password: String,
        locale: String? = null,
    ): AccountSnapshotResponse = wrap {
        val cred = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        val token = cred.user?.getIdToken(false)?.await()?.token
            ?: error("Firebase did not return an ID token after login")
        api.login(FirebaseTokenRequest(firebaseIdToken = token, locale = locale))
    }

    suspend fun refresh(): AccountSnapshotResponse = wrap {
        val user = firebaseAuth.currentUser ?: error("No signed-in Firebase user")
        val token = user.getIdToken(true).await().token
            ?: error("Firebase did not return a refreshed ID token")
        api.refresh(FirebaseTokenRequest(firebaseIdToken = token))
    }

    fun signOut() {
        firebaseAuth.signOut()
    }

    fun isSignedIn(): Boolean = firebaseAuth.currentUser != null

    private suspend inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}
