package com.drivemark.app.ui.picker

import android.content.Intent
import com.drivemark.app.data.repository.SpreadsheetRepository
import com.drivemark.app.domain.model.Spreadsheet
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpreadsheetPickerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var spreadsheetRepository: SpreadsheetRepository

    private val sampleSheets = listOf(
        Spreadsheet("id-1", "Sheet One", "2024-06-15T10:00:00Z"),
        Spreadsheet("id-2", "Sheet Two", "2024-06-14T10:00:00Z"),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        spreadsheetRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init with no selectedId calls loadSpreadsheets`() = runTest {
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf(null)
        coEvery { spreadsheetRepository.refreshSpreadsheets() } returns Result.success(sampleSheets)

        val vm = SpreadsheetPickerViewModel(spreadsheetRepository)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.spreadsheets.size)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `init with selectedId sets it in state and skips load`() = runTest {
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf("existing-id")

        val vm = SpreadsheetPickerViewModel(spreadsheetRepository)
        advanceUntilIdle()

        assertEquals("existing-id", vm.uiState.value.selectedId)
        coVerify(exactly = 0) { spreadsheetRepository.refreshSpreadsheets() }
    }

    @Test
    fun `loadSpreadsheets success populates list`() = runTest {
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf(null)
        coEvery { spreadsheetRepository.refreshSpreadsheets() } returns Result.success(sampleSheets)

        val vm = SpreadsheetPickerViewModel(spreadsheetRepository)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.spreadsheets.size)
        assertEquals("Sheet One", vm.uiState.value.spreadsheets[0].name)
    }

    @Test
    fun `loadSpreadsheets on UserRecoverableAuthIOException sets consentIntent`() = runTest {
        val intent = mockk<Intent>()
        val exception = mockk<UserRecoverableAuthIOException>()
        every { exception.intent } returns intent
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf(null)
        coEvery { spreadsheetRepository.refreshSpreadsheets() } returns Result.failure(exception)

        val vm = SpreadsheetPickerViewModel(spreadsheetRepository)
        advanceUntilIdle()

        assertEquals(intent, vm.uiState.value.consentIntent)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `loadSpreadsheets on other error sets error message`() = runTest {
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf(null)
        coEvery { spreadsheetRepository.refreshSpreadsheets() } returns Result.failure(RuntimeException("Network error"))

        val vm = SpreadsheetPickerViewModel(spreadsheetRepository)
        advanceUntilIdle()

        assertEquals("Network error", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `onNewSheetNameChanged updates state`() = runTest {
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf("id")

        val vm = SpreadsheetPickerViewModel(spreadsheetRepository)
        advanceUntilIdle()

        vm.onNewSheetNameChanged("My New Sheet")
        assertEquals("My New Sheet", vm.uiState.value.newSheetName)
    }

    @Test
    fun `createSpreadsheet with blank name returns early`() = runTest {
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf("id")

        val vm = SpreadsheetPickerViewModel(spreadsheetRepository)
        advanceUntilIdle()

        vm.onNewSheetNameChanged("   ")
        vm.createSpreadsheet()
        advanceUntilIdle()

        coVerify(exactly = 0) { spreadsheetRepository.createSpreadsheet(any()) }
    }

    @Test
    fun `createSpreadsheet success sets selectedId`() = runTest {
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf("id")
        coEvery { spreadsheetRepository.createSpreadsheet("New Sheet") } returns Result.success("new-id")

        val vm = SpreadsheetPickerViewModel(spreadsheetRepository)
        advanceUntilIdle()

        vm.onNewSheetNameChanged("New Sheet")
        vm.createSpreadsheet()
        advanceUntilIdle()

        assertEquals("new-id", vm.uiState.value.selectedId)
        assertFalse(vm.uiState.value.isCreating)
    }

    @Test
    fun `createSpreadsheet on UserRecoverableAuthIOException sets consentIntent`() = runTest {
        val intent = mockk<Intent>()
        val exception = mockk<UserRecoverableAuthIOException>()
        every { exception.intent } returns intent
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf("id")
        coEvery { spreadsheetRepository.createSpreadsheet("Sheet") } returns Result.failure(exception)

        val vm = SpreadsheetPickerViewModel(spreadsheetRepository)
        advanceUntilIdle()

        vm.onNewSheetNameChanged("Sheet")
        vm.createSpreadsheet()
        advanceUntilIdle()

        assertEquals(intent, vm.uiState.value.consentIntent)
    }

    @Test
    fun `onConsentResult true retries LOAD action`() = runTest {
        val intent = mockk<Intent>()
        val exception = mockk<UserRecoverableAuthIOException>()
        every { exception.intent } returns intent
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf(null)
        coEvery { spreadsheetRepository.refreshSpreadsheets() } returns Result.failure(exception) andThen Result.success(sampleSheets)

        val vm = SpreadsheetPickerViewModel(spreadsheetRepository)
        advanceUntilIdle()

        // First call failed with consent needed, now consent granted
        vm.onConsentResult(true)
        advanceUntilIdle()

        // refreshSpreadsheets called twice (init + retry)
        coVerify(atLeast = 2) { spreadsheetRepository.refreshSpreadsheets() }
    }

    @Test
    fun `onConsentResult false sets permission error`() = runTest {
        val intent = mockk<Intent>()
        val exception = mockk<UserRecoverableAuthIOException>()
        every { exception.intent } returns intent
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf(null)
        coEvery { spreadsheetRepository.refreshSpreadsheets() } returns Result.failure(exception)

        val vm = SpreadsheetPickerViewModel(spreadsheetRepository)
        advanceUntilIdle()

        vm.onConsentResult(false)
        advanceUntilIdle()

        assertEquals("Google Drive permission is required to manage spreadsheets", vm.uiState.value.error)
        assertNull(vm.uiState.value.consentIntent)
    }

    @Test
    fun `selectSpreadsheet persists and sets selectedId`() = runTest {
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf("id")

        val vm = SpreadsheetPickerViewModel(spreadsheetRepository)
        advanceUntilIdle()

        vm.selectSpreadsheet("new-id", "New Sheet")
        advanceUntilIdle()

        assertEquals("new-id", vm.uiState.value.selectedId)
        coVerify { spreadsheetRepository.selectSpreadsheet("new-id", "New Sheet") }
    }
}
