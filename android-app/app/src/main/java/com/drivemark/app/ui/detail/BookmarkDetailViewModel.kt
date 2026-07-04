package com.drivemark.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemark.app.data.repository.BookmarkRepository
import com.drivemark.app.data.repository.SpreadsheetRepository
import com.drivemark.app.domain.model.Bookmark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarkDetailViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val spreadsheetRepository: SpreadsheetRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val bookmark: Bookmark? = null,
        val isLoading: Boolean = true,
        val deleted: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val bookmarkId = savedStateHandle.get<String>("bookmarkId") ?: ""
        viewModelScope.launch {
            val bookmark = bookmarkRepository.getById(bookmarkId)
            _uiState.value = UiState(bookmark = bookmark, isLoading = false)
        }
    }

    fun onDeleteBookmark() {
        val bookmark = _uiState.value.bookmark ?: return
        viewModelScope.launch {
            val sheetId = spreadsheetRepository.getSelectedSpreadsheetId().first() ?: return@launch
            val result = bookmarkRepository.deleteBookmark(sheetId, bookmark)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(deleted = true)
            }
            result.onFailure { e ->
                // Error is non-critical here; user stays on screen
            }
        }
    }
}
