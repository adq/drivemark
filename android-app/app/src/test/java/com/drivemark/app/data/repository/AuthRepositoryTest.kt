package com.drivemark.app.data.repository

import android.content.Context
import com.drivemark.app.data.local.PreferencesManager
import com.drivemark.app.data.remote.GoogleAuthManager
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {

    private lateinit var authManager: GoogleAuthManager
    private lateinit var prefsManager: PreferencesManager
    private lateinit var repository: AuthRepository

    @Before
    fun setup() {
        authManager = mockk(relaxed = true)
        prefsManager = mockk(relaxed = true)
        repository = AuthRepository(authManager, prefsManager)
    }

    // --- trySilentSignIn ---

    @Test
    fun `trySilentSignIn sets Authenticated when email exists in prefs`() = runTest {
        every { prefsManager.userEmail } returns flowOf("user@example.com")
        every { prefsManager.userDisplayName } returns flowOf("Test User")

        repository.trySilentSignIn()

        val state = repository.authState.value
        assertTrue(state is AuthState.Authenticated)
        assertEquals("user@example.com", (state as AuthState.Authenticated).email)
        assertEquals("Test User", state.displayName)
    }

    @Test
    fun `trySilentSignIn sets NotAuthenticated when no email in prefs`() = runTest {
        every { prefsManager.userEmail } returns flowOf(null)

        repository.trySilentSignIn()

        assertEquals(AuthState.NotAuthenticated, repository.authState.value)
    }

    // --- signIn ---

    @Test
    fun `signIn success saves credentials and sets Authenticated`() = runTest {
        val context = mockk<Context>()
        val credential = mockk<GoogleIdTokenCredential>()
        every { credential.id } returns "user@example.com"
        every { credential.displayName } returns "Test User"
        coEvery { authManager.signIn(context) } returns credential

        val result = repository.signIn(context)

        assertTrue(result.isSuccess)
        coVerify { prefsManager.setUserInfo("user@example.com", "Test User") }
        val state = repository.authState.value
        assertTrue(state is AuthState.Authenticated)
        assertEquals("user@example.com", (state as AuthState.Authenticated).email)
    }

    @Test
    fun `signIn with null displayName defaults to empty string`() = runTest {
        val context = mockk<Context>()
        val credential = mockk<GoogleIdTokenCredential>()
        every { credential.id } returns "user@example.com"
        every { credential.displayName } returns null
        coEvery { authManager.signIn(context) } returns credential

        val result = repository.signIn(context)

        assertTrue(result.isSuccess)
        coVerify { prefsManager.setUserInfo("user@example.com", "") }
    }

    @Test
    fun `signIn failure sets Error state`() = runTest {
        val context = mockk<Context>()
        coEvery { authManager.signIn(context) } throws RuntimeException("auth failed")

        val result = repository.signIn(context)

        assertTrue(result.isFailure)
        val state = repository.authState.value
        assertTrue(state is AuthState.Error)
        assertEquals("auth failed", (state as AuthState.Error).message)
    }

    @Test
    fun `signIn failure with null message uses fallback`() = runTest {
        val context = mockk<Context>()
        coEvery { authManager.signIn(context) } throws RuntimeException(null as String?)

        val result = repository.signIn(context)

        assertTrue(result.isFailure)
        val state = repository.authState.value
        assertTrue(state is AuthState.Error)
        assertEquals("Sign-in failed", (state as AuthState.Error).message)
    }

    // --- signOut ---

    @Test
    fun `signOut clears prefs and sets NotAuthenticated`() = runTest {
        // First authenticate
        every { prefsManager.userEmail } returns flowOf("user@example.com")
        every { prefsManager.userDisplayName } returns flowOf("Test User")
        repository.trySilentSignIn()
        assertTrue(repository.authState.value is AuthState.Authenticated)

        // Then sign out
        repository.signOut()

        assertEquals(AuthState.NotAuthenticated, repository.authState.value)
        coVerify { authManager.signOut() }
        coVerify { prefsManager.clearAll() }
    }

    // --- isAuthenticated / getCurrentEmail ---

    @Test
    fun `isAuthenticated returns false initially`() {
        assertFalse(repository.isAuthenticated())
    }

    @Test
    fun `isAuthenticated returns true after sign-in`() = runTest {
        every { prefsManager.userEmail } returns flowOf("user@example.com")
        every { prefsManager.userDisplayName } returns flowOf("User")
        repository.trySilentSignIn()

        assertTrue(repository.isAuthenticated())
    }

    @Test
    fun `getCurrentEmail returns null when not authenticated`() {
        assertNull(repository.getCurrentEmail())
    }

    @Test
    fun `getCurrentEmail returns email when authenticated`() = runTest {
        every { prefsManager.userEmail } returns flowOf("user@example.com")
        every { prefsManager.userDisplayName } returns flowOf(null)
        repository.trySilentSignIn()

        assertEquals("user@example.com", repository.getCurrentEmail())
    }

    // --- initial state ---

    @Test
    fun `initial state is Loading`() {
        assertTrue(repository.authState.value is AuthState.Loading)
    }
}
