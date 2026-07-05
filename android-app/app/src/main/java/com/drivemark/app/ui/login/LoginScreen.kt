package com.drivemark.app.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.drivemark.app.R
import com.drivemark.app.data.repository.AuthState
import com.drivemark.app.ui.navigation.Screen
import com.drivemark.app.ui.theme.BrandBlue

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                navController.navigate(Screen.SpreadsheetPicker.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "DriveMark",
            style = MaterialTheme.typography.headlineLarge,
            color = BrandBlue,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.sign_in_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { viewModel.signIn(context) },
            enabled = !isSigningIn,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.sign_in_with_google))
            }
        }

        if (authState is AuthState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = (authState as AuthState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
