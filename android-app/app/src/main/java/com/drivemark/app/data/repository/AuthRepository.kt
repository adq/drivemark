package com.drivemark.app.data.repository

import android.content.Context
import com.drivemark.app.data.local.PreferencesManager
import com.drivemark.app.data.remote.GoogleAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthState {
    data object Loading : AuthState()
    data object NotAuthenticated : AuthState()
    data class Authenticated(val email: String, val displayName: String?) : AuthState()
    data class Error(val message: String) : AuthState()
}

@Singleton
class AuthRepository @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val prefsManager: PreferencesManager,
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    suspend fun trySilentSignIn() {
        val email = prefsManager.userEmail.first()
        if (email != null) {
            authManager.getAccountCredentialForEmail(email)
            val displayName = prefsManager.userDisplayName.first()
            _authState.value = AuthState.Authenticated(email, displayName)
        } else {
            _authState.value = AuthState.NotAuthenticated
        }
    }

    suspend fun signIn(activityContext: Context): Result<Unit> {
        return try {
            val idCredential = authManager.signIn(activityContext)
            val email = idCredential.id
            val displayName = idCredential.displayName ?: ""
            authManager.getAccountCredentialForEmail(email)
            prefsManager.setUserInfo(email, displayName)
            _authState.value = AuthState.Authenticated(email, displayName)
            Result.success(Unit)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Sign-in failed")
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        authManager.signOut()
        prefsManager.clearAll()
        _authState.value = AuthState.NotAuthenticated
    }

    fun isAuthenticated(): Boolean = _authState.value is AuthState.Authenticated

    fun getCurrentEmail(): String? {
        return (_authState.value as? AuthState.Authenticated)?.email
    }
}
