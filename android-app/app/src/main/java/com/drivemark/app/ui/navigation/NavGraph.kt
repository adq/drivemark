package com.drivemark.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.drivemark.app.ui.bookmarks.BookmarkListScreen
import com.drivemark.app.ui.detail.BookmarkDetailScreen
import com.drivemark.app.ui.login.LoginScreen
import com.drivemark.app.ui.picker.SpreadsheetPickerScreen
import com.drivemark.app.ui.save.SaveBookmarkScreen

@Composable
fun DriveMarkNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            LoginScreen(navController = navController)
        }
        composable(Screen.SpreadsheetPicker.route) {
            SpreadsheetPickerScreen(navController = navController)
        }
        composable(Screen.BookmarkList.route) {
            BookmarkListScreen(navController = navController)
        }
        composable(
            route = Screen.BookmarkDetail.route,
            arguments = listOf(navArgument("bookmarkId") { type = NavType.StringType }),
        ) {
            BookmarkDetailScreen(navController = navController)
        }
        composable(
            route = Screen.SaveBookmark.route,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            SaveBookmarkScreen(navController = navController)
        }
    }
}
