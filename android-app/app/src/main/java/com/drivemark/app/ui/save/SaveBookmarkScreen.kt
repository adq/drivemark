package com.drivemark.app.ui.save

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.drivemark.app.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SaveBookmarkScreen(
    navController: NavController,
    viewModel: SaveBookmarkViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var folderFocused by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saveResult) {
        when (val result = uiState.saveResult) {
            is SaveResult.Saved -> {
                snackbarHostState.showSnackbar("Bookmark saved!")
                viewModel.onResultConsumed()
                navController.popBackStack()
            }
            is SaveResult.Updated -> {
                snackbarHostState.showSnackbar("Bookmark updated!")
                viewModel.onResultConsumed()
                navController.popBackStack()
            }
            is SaveResult.Error -> {
                snackbarHostState.showSnackbar(result.message)
                viewModel.onResultConsumed()
            }
            null -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isExistingBookmark) stringResource(R.string.update_bookmark)
                        else stringResource(R.string.save_bookmark)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.url,
                onValueChange = viewModel::onUrlChanged,
                label = { Text(stringResource(R.string.url)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) viewModel.onUrlFocusLost() },
            )

            if (uiState.duplicateWarning) {
                Text(
                    text = stringResource(R.string.duplicate_url_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
            }

            TextButton(
                onClick = viewModel::fetchMetadata,
                enabled = !uiState.isExtractingMetadata && uiState.url.isNotBlank(),
            ) {
                if (uiState.isExtractingMetadata) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.fetch_metadata))
            }

            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChanged,
                label = { Text(stringResource(R.string.title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.excerpt,
                onValueChange = viewModel::onExcerptChanged,
                label = { Text(stringResource(R.string.excerpt)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.coverUrl,
                onValueChange = viewModel::onCoverUrlChanged,
                label = { Text(stringResource(R.string.cover_image_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Folder with inline suggestion chips
            OutlinedTextField(
                value = uiState.folder,
                onValueChange = viewModel::onFolderChanged,
                label = { Text(stringResource(R.string.folder)) },
                singleLine = true,
                isError = uiState.folder.isBlank() && uiState.saveResult is SaveResult.Error,
                supportingText = if (uiState.folder.isBlank()) {
                    { Text(stringResource(R.string.folder_required)) }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { folderFocused = it.isFocused },
            )

            val filteredFolders = uiState.availableFolders.filter {
                it.contains(uiState.folder, ignoreCase = true) && it != uiState.folder
            }
            if (folderFocused && filteredFolders.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    filteredFolders.forEach { folder ->
                        SuggestionChip(
                            onClick = { viewModel.onFolderChanged(folder) },
                            label = { Text(folder) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = viewModel::onNotesChanged,
                label = { Text(stringResource(R.string.notes)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = viewModel::onSave,
                enabled = !uiState.isSaving && uiState.url.isNotBlank() && uiState.folder.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        if (uiState.isExistingBookmark) stringResource(R.string.update_bookmark)
                        else stringResource(R.string.save_bookmark)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
