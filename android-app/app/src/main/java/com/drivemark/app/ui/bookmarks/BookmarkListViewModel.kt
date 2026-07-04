package com.drivemark.app.ui.bookmarks

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.drivemark.app.data.repository.AuthRepository
import com.drivemark.app.data.repository.AuthState
import com.drivemark.app.data.repository.BookmarkRepository
import com.drivemark.app.data.repository.SpreadsheetRepository
import com.drivemark.app.domain.model.Bookmark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderNode(
    val name: String,
    val fullPath: String,
    val bookmarks: List<Bookmark>,
    val children: List<FolderNode>,
    val totalCount: Int,
)

private fun buildFolderTree(grouped: Map<String, List<Bookmark>>): List<FolderNode> {
    data class MutableNode(
        val children: MutableMap<String, MutableNode> = sortedMapOf(),
        var bookmarks: List<Bookmark> = emptyList(),
    )

    val root = MutableNode()
    for ((path, bookmarks) in grouped) {
        val parts = path.split("/")
        var node = root
        for (part in parts) {
            node = node.children.getOrPut(part) { MutableNode() }
        }
        node.bookmarks = bookmarks
    }

    fun toFolderNodes(node: MutableNode, prefix: String): List<FolderNode> {
        return node.children.map { (name, child) ->
            val fullPath = if (prefix.isEmpty()) name else "$prefix/$name"
            val childNodes = toFolderNodes(child, fullPath)
            val totalCount = child.bookmarks.size + childNodes.sumOf { it.totalCount }
            FolderNode(
                name = name,
                fullPath = fullPath,
                bookmarks = child.bookmarks,
                children = childNodes,
                totalCount = totalCount,
            )
        }
    }

    return toFolderNodes(root, "")
}

@HiltViewModel
class BookmarkListViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val spreadsheetRepository: SpreadsheetRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val folders: List<FolderNode> = emptyList(),
        val expandedFolders: Set<String> = emptySet(),
        val searchQuery: String = "",
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val spreadsheetId: String? = null,
        val spreadsheetName: String = "",
        val deletedBookmark: Bookmark? = null,
        val sheetCleared: Boolean = false,
        val consentIntent: Intent? = null,
        val isCleaningUp: Boolean = false,
        val cleanupResult: String? = null,
    )

    val authState: StateFlow<AuthState> = authRepository.authState

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private var currentSpreadsheetId: String? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val bookmarks: StateFlow<List<Bookmark>> = spreadsheetRepository.getSelectedSpreadsheetId()
        .filterNotNull()
        .flatMapLatest { sheetId ->
            _searchQuery.flatMapLatest { query ->
                if (query.isBlank()) {
                    bookmarkRepository.observeBookmarks(sheetId)
                } else {
                    bookmarkRepository.searchBookmarks(sheetId, query)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val sheetId = spreadsheetRepository.getSelectedSpreadsheetId().first()
            val sheetName = spreadsheetRepository.getSelectedSpreadsheetName().first() ?: ""
            currentSpreadsheetId = sheetId
            _uiState.value = _uiState.value.copy(
                spreadsheetId = sheetId,
                spreadsheetName = sheetName,
            )
            if (sheetId != null) {
                syncBookmarks(sheetId)
            }
        }

        viewModelScope.launch {
            bookmarks.collect { list ->
                val grouped = list.groupBy { it.folder.ifBlank { "Uncategorized" } }
                    .toSortedMap()
                _uiState.value = _uiState.value.copy(
                    folders = buildFolderTree(grouped),
                    isLoading = false,
                )
            }
        }
    }

    private suspend fun syncBookmarks(spreadsheetId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val result = bookmarkRepository.syncBookmarks(spreadsheetId)
        result.onFailure { e ->
            if (e is UserRecoverableAuthIOException) {
                _uiState.value = _uiState.value.copy(consentIntent = e.intent)
            } else {
                val isForbidden = e is com.google.api.client.googleapis.json.GoogleJsonResponseException &&
                    (e.statusCode == 403 || e.statusCode == 404)
                if (isForbidden) {
                    _uiState.value = _uiState.value.copy(sheetCleared = true)
                } else {
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            }
        }
        _uiState.value = _uiState.value.copy(isLoading = false)
    }

    fun onConsentResult(success: Boolean) {
        _uiState.value = _uiState.value.copy(consentIntent = null)
        if (success) {
            val sheetId = currentSpreadsheetId ?: return
            viewModelScope.launch { syncBookmarks(sheetId) }
        } else {
            _uiState.value = _uiState.value.copy(
                error = "Google Drive permission is required to sync bookmarks",
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun onToggleFolder(folder: String) {
        val current = _uiState.value.expandedFolders
        _uiState.value = _uiState.value.copy(
            expandedFolders = if (folder in current) current - folder else current + folder,
        )
    }

    fun onRefresh() {
        val sheetId = currentSpreadsheetId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            bookmarkRepository.forceSync(sheetId)
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun onDeleteBookmark(bookmark: Bookmark) {
        val sheetId = currentSpreadsheetId ?: return
        viewModelScope.launch {
            val result = bookmarkRepository.deleteBookmark(sheetId, bookmark)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(deletedBookmark = bookmark)
            }
            result.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun onUndoDelete() {
        val bookmark = _uiState.value.deletedBookmark ?: return
        val sheetId = currentSpreadsheetId ?: return
        viewModelScope.launch {
            bookmarkRepository.addBookmark(sheetId, bookmark)
            _uiState.value = _uiState.value.copy(deletedBookmark = null)
        }
    }

    fun onDismissSnackbar() {
        _uiState.value = _uiState.value.copy(deletedBookmark = null, error = null)
    }

    fun onChangeSheet() {
        viewModelScope.launch {
            spreadsheetRepository.clearSelection()
        }
    }

    fun onCleanup() {
        val sheetId = currentSpreadsheetId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCleaningUp = true)
            val result = bookmarkRepository.cleanupSheet(sheetId)
            result.onSuccess { count ->
                _uiState.value = _uiState.value.copy(
                    cleanupResult = if (count > 0) "Sync complete — removed $count old rows" else "Bookmarks are already in sync",
                )
            }
            result.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = "Sync failed: ${e.message}")
            }
            _uiState.value = _uiState.value.copy(isCleaningUp = false)
        }
    }

    fun onDismissCleanupResult() {
        _uiState.value = _uiState.value.copy(cleanupResult = null)
    }

    fun onSignOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
