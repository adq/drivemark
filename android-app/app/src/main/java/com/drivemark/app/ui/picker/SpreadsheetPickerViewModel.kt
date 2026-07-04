package com.drivemark.app.ui.picker

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.drivemark.app.data.repository.SpreadsheetRepository
import com.drivemark.app.domain.model.Spreadsheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SpreadsheetPickerViewModel @Inject constructor(
    private val spreadsheetRepository: SpreadsheetRepository,
) : ViewModel() {

    private enum class PendingAction { LOAD, CREATE }

    data class UiState(
        val spreadsheets: List<Spreadsheet> = emptyList(),
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val selectedId: String? = null,
        val newSheetName: String = "",
        val isCreating: Boolean = false,
        val consentIntent: Intent? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private var pendingAction: PendingAction? = null

    init {
        viewModelScope.launch {
            val selectedId = spreadsheetRepository.getSelectedSpreadsheetId().first()
            if (selectedId != null) {
                _uiState.value = _uiState.value.copy(selectedId = selectedId)
            } else {
                loadSpreadsheets()
            }
        }
    }

    fun loadSpreadsheets() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, consentIntent = null)
            val result = spreadsheetRepository.refreshSpreadsheets()
            result.fold(
                onSuccess = { sheets ->
                    _uiState.value = _uiState.value.copy(
                        spreadsheets = sheets,
                        isLoading = false,
                    )
                },
                onFailure = { e ->
                    if (e is UserRecoverableAuthIOException) {
                        pendingAction = PendingAction.LOAD
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            consentIntent = e.intent,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load spreadsheets",
                        )
                    }
                },
            )
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            spreadsheetRepository.refreshSpreadsheets()
            val sheets = spreadsheetRepository.observeSpreadsheets().first()
            _uiState.value = _uiState.value.copy(
                spreadsheets = sheets,
                isRefreshing = false,
            )
        }
    }

    fun onNewSheetNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(newSheetName = name)
    }

    fun createSpreadsheet() {
        val name = _uiState.value.newSheetName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null, consentIntent = null)
            spreadsheetRepository.createSpreadsheet(name).fold(
                onSuccess = { id ->
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        selectedId = id,
                    )
                },
                onFailure = { e ->
                    if (e is UserRecoverableAuthIOException) {
                        pendingAction = PendingAction.CREATE
                        _uiState.value = _uiState.value.copy(
                            isCreating = false,
                            consentIntent = e.intent,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isCreating = false,
                            error = e.message ?: "Failed to create spreadsheet",
                        )
                    }
                },
            )
        }
    }

    fun onConsentResult(success: Boolean) {
        _uiState.value = _uiState.value.copy(consentIntent = null)
        if (success) {
            when (pendingAction) {
                PendingAction.LOAD -> loadSpreadsheets()
                PendingAction.CREATE -> createSpreadsheet()
                null -> {}
            }
        } else {
            _uiState.value = _uiState.value.copy(
                error = "Google Drive permission is required to manage spreadsheets",
            )
        }
        pendingAction = null
    }

    fun selectSpreadsheet(id: String, name: String) {
        viewModelScope.launch {
            spreadsheetRepository.selectSpreadsheet(id, name)
            _uiState.value = _uiState.value.copy(selectedId = id)
        }
    }
}
