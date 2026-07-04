package com.drivemark.app.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object SpreadsheetPicker : Screen("picker")
    data object BookmarkList : Screen("bookmarks")
    data object BookmarkDetail : Screen("bookmark/{bookmarkId}") {
        fun createRoute(bookmarkId: String) = "bookmark/$bookmarkId"
    }
    data object SaveBookmark : Screen("save?url={url}&title={title}") {
        fun createRoute(url: String = "", title: String = "") =
            "save?url=${Uri.encode(url)}&title=${Uri.encode(title)}"
    }
}
