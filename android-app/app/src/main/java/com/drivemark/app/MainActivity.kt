package com.drivemark.app

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.drivemark.app.ui.navigation.DriveMarkNavGraph
import com.drivemark.app.ui.navigation.Screen
import com.drivemark.app.ui.theme.DriveMarkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingShareUrl by mutableStateOf<String?>(null)
    private var pendingShareTitle by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleShareIntent(intent)

        setContent {
            DriveMarkTheme {
                val navController = rememberNavController()
                val currentEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentEntry?.destination?.route

                LaunchedEffect(pendingShareUrl, currentRoute) {
                    val url = pendingShareUrl ?: return@LaunchedEffect
                    if (currentRoute != null &&
                        currentRoute != Screen.Login.route &&
                        currentRoute != Screen.SpreadsheetPicker.route
                    ) {
                        val title = pendingShareTitle ?: ""
                        pendingShareUrl = null
                        pendingShareTitle = null
                        navController.navigate(Screen.SaveBookmark.createRoute(url, title)) {
                            popUpTo(Screen.BookmarkList.route)
                        }
                    }
                }

                DriveMarkNavGraph(navController = navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            pendingShareUrl = extractUrl(sharedText)
            pendingShareTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
        }
    }

    private fun extractUrl(text: String): String {
        val matcher = Patterns.WEB_URL.matcher(text)
        return if (matcher.find()) matcher.group() ?: text else text
    }
}
