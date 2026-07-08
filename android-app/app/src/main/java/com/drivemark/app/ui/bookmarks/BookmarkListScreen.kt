package com.drivemark.app.ui.bookmarks

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.drivemark.app.R
import com.drivemark.app.data.repository.AuthState
import com.drivemark.app.ui.bookmarks.components.FolderGroup
import com.drivemark.app.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkListScreen(
    navController: NavController,
    viewModel: BookmarkListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(authState) {
        if (authState is AuthState.NotAuthenticated) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onConsentResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.consentIntent) {
        uiState.consentIntent?.let { consentLauncher.launch(it) }
    }

    // Handle delete undo snackbar
    LaunchedEffect(uiState.deletedBookmark) {
        val deleted = uiState.deletedBookmark ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Deleted \"${deleted.title}\"",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.onUndoDelete()
        } else {
            viewModel.onDismissSnackbar()
        }
    }

    // Handle sheet no longer accessible (scope migration)
    LaunchedEffect(uiState.sheetCleared) {
        if (uiState.sheetCleared) {
            navController.navigate(Screen.SpreadsheetPicker.route) {
                popUpTo(Screen.BookmarkList.route) { inclusive = true }
            }
        }
    }

    // Handle cleanup result snackbar
    LaunchedEffect(uiState.cleanupResult) {
        val msg = uiState.cleanupResult ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = msg)
        viewModel.onDismissCleanupResult()
    }

    // Handle error snackbar
    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = error)
        viewModel.onDismissSnackbar()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.spreadsheetName.ifBlank { "DriveMark" }) },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cleanup_spreadsheet)) },
                            enabled = !uiState.isCleaningUp,
                            onClick = {
                                showMenu = false
                                viewModel.onCleanup()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.change_sheet)) },
                            onClick = {
                                showMenu = false
                                viewModel.onChangeSheet()
                                navController.navigate(Screen.SpreadsheetPicker.route) {
                                    popUpTo(Screen.BookmarkList.route) { inclusive = true }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sign_out)) },
                            onClick = {
                                showMenu = false
                                viewModel.onSignOut()
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.SaveBookmark.createRoute()) },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.save_bookmark))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Search bar
            TextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text(stringResource(R.string.search_bookmarks)) },
                singleLine = true,
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.folders.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.no_bookmarks),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(Modifier.fillMaxSize()) {
                            fun renderNodes(nodes: List<FolderNode>, depth: Int) {
                                nodes.forEach { node ->
                                    item(key = "folder_${node.fullPath}") {
                                        FolderGroup(
                                            folderName = node.name,
                                            totalCount = node.totalCount,
                                            bookmarks = node.bookmarks,
                                            children = node.children,
                                            isExpanded = node.fullPath in uiState.expandedFolders,
                                            expandedFolders = uiState.expandedFolders,
                                            depth = depth,
                                            onToggle = { viewModel.onToggleFolder(node.fullPath) },
                                            onToggleChild = { path -> viewModel.onToggleFolder(path) },
                                            onBookmarkClick = { bookmark ->
                                                navController.navigate(
                                                    Screen.BookmarkDetail.createRoute(bookmark.id)
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                            renderNodes(uiState.folders, 0)
                        }
                    }
                }
            }
        }
    }
}
