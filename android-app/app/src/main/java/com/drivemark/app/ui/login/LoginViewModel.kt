package com.drivemark.app.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemark.app.data.repository.AuthRepository
import com.drivemark.app.data.repository.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.trySilentSignIn()
        }
    }

    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            _isSigningIn.value = true
            authRepository.signIn(activityContext)
            _isSigningIn.value = false
        }
    }
}
