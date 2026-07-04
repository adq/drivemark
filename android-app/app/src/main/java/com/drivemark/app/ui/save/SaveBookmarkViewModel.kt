package com.drivemark.app.ui.save

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemark.app.data.remote.MetadataExtractor
import com.drivemark.app.data.repository.BookmarkRepository
import com.drivemark.app.data.repository.SpreadsheetRepository
import com.drivemark.app.domain.model.Bookmark
import com.drivemark.app.util.DateFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SaveResult {
    data object Saved : SaveResult()
    data object Updated : SaveResult()
    data class Error(val message: String) : SaveResult()
}

@HiltViewModel
class SaveBookmarkViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val spreadsheetRepository: SpreadsheetRepository,
    private val metadataExtractor: MetadataExtractor,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val url: String = "",
        val title: String = "",
        val excerpt: String = "",
        val coverUrl: String = "",
        val folder: String = "",
        val notes: String = "",
        val availableFolders: List<String> = emptyList(),
        val isExistingBookmark: Boolean = false,
        val existingBookmarkId: String? = null,
        val existingDateAdded: String? = null,
        val isSaving: Boolean = false,
        val isExtractingMetadata: Boolean = false,
        val saveResult: SaveResult? = null,
        val duplicateWarning: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var spreadsheetId: String? = null

    init {
        val url = savedStateHandle.get<String>("url") ?: ""
        val title = savedStateHandle.get<String>("title") ?: ""
        _uiState.value = _uiState.value.copy(url = url, title = title)

        viewModelScope.launch {
            spreadsheetId = spreadsheetRepository.getSelectedSpreadsheetId().first()
            val sheetId = spreadsheetId ?: return@launch

            // Load available folders
            val folders = bookmarkRepository.getFolders(sheetId)
            _uiState.value = _uiState.value.copy(availableFolders = folders)

            // Check for existing bookmark
            if (url.isNotBlank()) {
                checkForExisting(sheetId, url)
                if (!_uiState.value.isExistingBookmark) {
                    extractMetadata(url)
                }
            }
        }
    }

    private suspend fun checkForExisting(sheetId: String, url: String) {
        val existing = bookmarkRepository.findByUrl(sheetId, url)
        if (existing != null) {
            _uiState.value = _uiState.value.copy(
                isExistingBookmark = true,
                existingBookmarkId = existing.id,
                existingDateAdded = existing.dateAdded,
                title = existing.title,
                excerpt = existing.excerpt,
                coverUrl = existing.coverUrl,
                folder = existing.folder,
                notes = existing.notes,
                duplicateWarning = false,
            )
        }
    }

    private suspend fun extractMetadata(url: String, overwrite: Boolean = false) {
        _uiState.value = _uiState.value.copy(isExtractingMetadata = true)
        val metadata = metadataExtractor.extract(url)
        val current = _uiState.value
        _uiState.value = current.copy(
            title = if (overwrite || current.title.isBlank()) metadata.title.ifBlank { current.title } else current.title,
            excerpt = if (overwrite || current.excerpt.isBlank()) metadata.excerpt.ifBlank { current.excerpt } else current.excerpt,
            coverUrl = if (overwrite || current.coverUrl.isBlank()) metadata.coverUrl.ifBlank { current.coverUrl } else current.coverUrl,
            isExtractingMetadata = false,
        )
    }

    fun fetchMetadata() {
        val url = _uiState.value.url
        if (url.isBlank()) return
        viewModelScope.launch { extractMetadata(url, overwrite = true) }
    }

    fun onUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(url = url, duplicateWarning = false)
    }

    fun onUrlFocusLost() {
        val url = _uiState.value.url
        val sheetId = spreadsheetId ?: return
        if (url.isBlank()) return
        viewModelScope.launch {
            val existing = bookmarkRepository.findByUrl(sheetId, url)
            if (existing != null && !_uiState.value.isExistingBookmark) {
                _uiState.value = _uiState.value.copy(duplicateWarning = true)
            }
            val s = _uiState.value
            if (s.title.isBlank() || s.excerpt.isBlank() || s.coverUrl.isBlank()) {
                extractMetadata(url)
            }
        }
    }

    fun onTitleChanged(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun onExcerptChanged(excerpt: String) {
        _uiState.value = _uiState.value.copy(excerpt = excerpt)
    }

    fun onCoverUrlChanged(coverUrl: String) {
        _uiState.value = _uiState.value.copy(coverUrl = coverUrl)
    }

    fun onFolderChanged(folder: String) {
        _uiState.value = _uiState.value.copy(folder = folder)
    }

    fun onNotesChanged(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    fun onSave() {
        val state = _uiState.value
        val sheetId = spreadsheetId ?: return
        if (state.folder.isBlank()) return

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)

            if (state.isExistingBookmark && state.existingBookmarkId != null) {
                val bookmark = Bookmark(
                    id = state.existingBookmarkId,
                    url = state.url,
                    title = state.title,
                    folder = state.folder,
                    dateAdded = state.existingDateAdded ?: DateFormatter.nowIso(),
                    notes = state.notes,
                    excerpt = state.excerpt,
                    coverUrl = state.coverUrl,
                )
                val result = bookmarkRepository.updateBookmark(sheetId, bookmark)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveResult = result.fold(
                        onSuccess = { SaveResult.Updated },
                        onFailure = { SaveResult.Error(it.message ?: "Update failed") },
                    ),
                )
            } else {
                val bookmark = Bookmark(
                    id = "",
                    url = state.url,
                    title = state.title,
                    folder = state.folder,
                    dateAdded = DateFormatter.nowIso(),
                    notes = state.notes,
                    excerpt = state.excerpt,
                    coverUrl = state.coverUrl,
                )
                val result = bookmarkRepository.addBookmark(sheetId, bookmark)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveResult = result.fold(
                        onSuccess = { SaveResult.Saved },
                        onFailure = { SaveResult.Error(it.message ?: "Save failed") },
                    ),
                )
            }
        }
    }

    fun onResultConsumed() {
        _uiState.value = _uiState.value.copy(saveResult = null)
    }
}
