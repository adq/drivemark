package com.drivemark.app.data.remote

import android.accounts.Account
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import com.drivemark.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val credentialManager = CredentialManager.create(context)
    private var currentEmail: String? = null

    suspend fun signIn(activityContext: Context): GoogleIdTokenCredential {
        Log.d(TAG, "Starting sign-in with client ID: ${BuildConfig.WEB_CLIENT_ID.take(20)}...")
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.WEB_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        try {
            val result = credentialManager.getCredential(activityContext, request)
            val idCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            currentEmail = idCredential.id
            return idCredential
        } catch (e: NoCredentialException) {
            Log.e(TAG, "No credential available", e)
            throw Exception(
                "Google Sign-In not available. Verify that:\n" +
                "1. An Android OAuth client exists in GCP with your debug SHA-1\n" +
                "2. The package name matches com.drivemark.app\n" +
                "3. Google Play Services is up to date"
            )
        } catch (e: GetCredentialCancellationException) {
            throw Exception("Sign-in was cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed: ${e.javaClass.simpleName}", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "GoogleAuth"
    }

    suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
        currentEmail = null
    }

    fun getAccountCredential(): GoogleAccountCredential? {
        val email = currentEmail ?: return null
        return buildCredential(email)
    }

    fun getAccountCredentialForEmail(email: String): GoogleAccountCredential {
        currentEmail = email
        return buildCredential(email)
    }

    private fun buildCredential(email: String): GoogleAccountCredential {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = Account(email, "com.google")
        return credential
    }

    fun getCurrentEmail(): String? = currentEmail
}
