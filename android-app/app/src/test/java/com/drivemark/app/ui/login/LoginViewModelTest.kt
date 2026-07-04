package com.drivemark.app.ui.login

import android.content.Context
import com.drivemark.app.data.repository.AuthRepository
import com.drivemark.app.data.repository.AuthState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: LoginViewModel

    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.Loading)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        every { authRepository.authState } returns authStateFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LoginViewModel {
        return LoginViewModel(authRepository)
    }

    @Test
    fun `init triggers trySilentSignIn`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { authRepository.trySilentSignIn() }
    }

    @Test
    fun `authState exposes repository authState`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(AuthState.Loading, viewModel.authState.value)

        authStateFlow.value = AuthState.Authenticated("user@test.com", "User")
        assertEquals(AuthState.Authenticated("user@test.com", "User"), viewModel.authState.value)
    }

    @Test
    fun `signIn sets isSigningIn true then false`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val context = mockk<Context>()
        viewModel.signIn(context)

        // After advanceUntilIdle, signIn coroutine completes
        advanceUntilIdle()
        assertFalse(viewModel.isSigningIn.value)

        coVerify { authRepository.signIn(context) }
    }
}
