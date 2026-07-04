package com.drivemark.app.ui.save

import androidx.lifecycle.SavedStateHandle
import com.drivemark.app.data.remote.MetadataExtractor
import com.drivemark.app.data.remote.PageMetadata
import com.drivemark.app.data.repository.BookmarkRepository
import com.drivemark.app.data.repository.SpreadsheetRepository
import com.drivemark.app.domain.model.Bookmark
import io.mockk.coEvery
import io.mockk.coVerify
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
class SaveBookmarkViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var spreadsheetRepository: SpreadsheetRepository
    private lateinit var metadataExtractor: MetadataExtractor

    private val sheetId = "sheet-1"

    private val existingBookmark = Bookmark(
        id = "existing-id", url = "https://example.com", title = "Existing Title",
        folder = "Tech", dateAdded = "2024-01-01T00:00:00Z", notes = "existing notes",
        excerpt = "existing excerpt", coverUrl = "https://example.com/cover.jpg",
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        bookmarkRepository = mockk(relaxed = true)
        spreadsheetRepository = mockk(relaxed = true)
        metadataExtractor = mockk(relaxed = true)

        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf(sheetId)
        coEvery { bookmarkRepository.getFolders(sheetId) } returns listOf("Tech", "News")
        coEvery { metadataExtractor.extract(any()) } returns PageMetadata()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(url: String = "", title: String = ""): SaveBookmarkViewModel {
        val savedStateHandle = SavedStateHandle(
            mapOf("url" to url, "title" to title),
        )
        return SaveBookmarkViewModel(bookmarkRepository, spreadsheetRepository, metadataExtractor, savedStateHandle)
    }

    @Test
    fun `init with URL and title from SavedStateHandle populates state`() = runTest {
        coEvery { bookmarkRepository.findByUrl(sheetId, "https://test.com") } returns null

        val vm = createViewModel(url = "https://test.com", title = "Test")
        advanceUntilIdle()

        assertEquals("https://test.com", vm.uiState.value.url)
        assertEquals("Test", vm.uiState.value.title)
    }

    @Test
    fun `init loads available folders`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf("Tech", "News"), vm.uiState.value.availableFolders)
    }

    @Test
    fun `init with URL checks for existing bookmark and loads fields`() = runTest {
        coEvery { bookmarkRepository.findByUrl(sheetId, "https://example.com") } returns existingBookmark

        val vm = createViewModel(url = "https://example.com")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isExistingBookmark)
        assertEquals("existing-id", state.existingBookmarkId)
        assertEquals("Existing Title", state.title)
        assertEquals("Tech", state.folder)
        assertEquals("existing notes", state.notes)
        assertEquals("existing excerpt", state.excerpt)
    }

    @Test
    fun `init with URL and no existing bookmark triggers metadata extraction`() = runTest {
        coEvery { bookmarkRepository.findByUrl(sheetId, "https://new.com") } returns null
        coEvery { metadataExtractor.extract("https://new.com") } returns PageMetadata(
            title = "Extracted Title", excerpt = "Extracted excerpt", coverUrl = "https://img.com/cover.jpg",
        )

        val vm = createViewModel(url = "https://new.com")
        advanceUntilIdle()

        assertEquals("Extracted Title", vm.uiState.value.title)
        assertEquals("Extracted excerpt", vm.uiState.value.excerpt)
        assertEquals("https://img.com/cover.jpg", vm.uiState.value.coverUrl)
    }

    @Test
    fun `init without URL skips extraction`() = runTest {
        val vm = createViewModel(url = "")
        advanceUntilIdle()

        coVerify(exactly = 0) { metadataExtractor.extract(any()) }
    }

    @Test
    fun `onUrlChanged updates URL and clears duplicateWarning`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onUrlChanged("https://new-url.com")

        assertEquals("https://new-url.com", vm.uiState.value.url)
        assertFalse(vm.uiState.value.duplicateWarning)
    }

    @Test
    fun `onUrlFocusLost sets duplicateWarning when URL exists`() = runTest {
        coEvery { bookmarkRepository.findByUrl(sheetId, "https://dupe.com") } returns existingBookmark

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onUrlChanged("https://dupe.com")
        vm.onUrlFocusLost()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.duplicateWarning)
    }

    @Test
    fun `onUrlFocusLost with blank URL returns early`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onUrlChanged("")
        vm.onUrlFocusLost()
        advanceUntilIdle()

        coVerify(exactly = 0) { bookmarkRepository.findByUrl(any(), any()) }
    }

    @Test
    fun `onUrlFocusLost triggers extractMetadata when fields are missing`() = runTest {
        coEvery { bookmarkRepository.findByUrl(sheetId, "https://new.com") } returns null
        coEvery { metadataExtractor.extract("https://new.com") } returns PageMetadata(
            title = "Auto Title", excerpt = "Auto excerpt", coverUrl = "",
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onUrlChanged("https://new.com")
        vm.onUrlFocusLost()
        advanceUntilIdle()

        // Metadata extraction was triggered since title/excerpt/coverUrl are blank
        coVerify { metadataExtractor.extract("https://new.com") }
    }

    @Test
    fun `fetchMetadata with overwrite replaces filled fields`() = runTest {
        coEvery { bookmarkRepository.findByUrl(sheetId, "https://example.com") } returns existingBookmark
        coEvery { metadataExtractor.extract("https://example.com") } returns PageMetadata(
            title = "New Title", excerpt = "New excerpt", coverUrl = "https://new-cover.jpg",
        )

        val vm = createViewModel(url = "https://example.com")
        advanceUntilIdle()

        // Fields are populated from existing bookmark
        assertEquals("Existing Title", vm.uiState.value.title)

        // fetchMetadata with overwrite=true replaces them
        vm.fetchMetadata()
        advanceUntilIdle()

        assertEquals("New Title", vm.uiState.value.title)
        assertEquals("New excerpt", vm.uiState.value.excerpt)
        assertEquals("https://new-cover.jpg", vm.uiState.value.coverUrl)
    }

    @Test
    fun `metadata extraction only fills blank fields by default`() = runTest {
        coEvery { bookmarkRepository.findByUrl(sheetId, "https://test.com") } returns null
        coEvery { metadataExtractor.extract("https://test.com") } returns PageMetadata(
            title = "Extracted", excerpt = "Extracted excerpt", coverUrl = "https://cover.jpg",
        )

        // Init with title already set from SavedStateHandle
        val vm = createViewModel(url = "https://test.com", title = "Manual Title")
        advanceUntilIdle()

        // title was already set, so extraction should NOT overwrite it
        assertEquals("Manual Title", vm.uiState.value.title)
        // excerpt and coverUrl were blank, so they get filled
        assertEquals("Extracted excerpt", vm.uiState.value.excerpt)
        assertEquals("https://cover.jpg", vm.uiState.value.coverUrl)
    }

    @Test
    fun `onSave with new bookmark calls addBookmark and returns Saved`() = runTest {
        coEvery { bookmarkRepository.findByUrl(sheetId, "https://new.com") } returns null
        coEvery { bookmarkRepository.addBookmark(eq(sheetId), any()) } returns Result.success(
            Bookmark(id = "new-id", url = "https://new.com", title = "T", folder = "Tech", dateAdded = "now"),
        )

        val vm = createViewModel(url = "https://new.com")
        advanceUntilIdle()

        vm.onFolderChanged("Tech")
        vm.onSave()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saveResult is SaveResult.Saved)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `onSave with existing bookmark calls updateBookmark and returns Updated`() = runTest {
        coEvery { bookmarkRepository.findByUrl(sheetId, "https://example.com") } returns existingBookmark
        coEvery { bookmarkRepository.updateBookmark(eq(sheetId), any()) } returns Result.success(existingBookmark)

        val vm = createViewModel(url = "https://example.com")
        advanceUntilIdle()

        vm.onSave()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saveResult is SaveResult.Updated)
    }

    @Test
    fun `onSave failure returns SaveResult Error`() = runTest {
        coEvery { bookmarkRepository.findByUrl(sheetId, "https://fail.com") } returns null
        coEvery { bookmarkRepository.addBookmark(eq(sheetId), any()) } returns Result.failure(RuntimeException("Network error"))

        val vm = createViewModel(url = "https://fail.com")
        advanceUntilIdle()

        vm.onFolderChanged("Folder")
        vm.onSave()
        advanceUntilIdle()

        val result = vm.uiState.value.saveResult
        assertTrue(result is SaveResult.Error)
        assertEquals("Network error", (result as SaveResult.Error).message)
    }

    @Test
    fun `onSave with blank folder returns early`() = runTest {
        val vm = createViewModel(url = "https://test.com")
        advanceUntilIdle()

        // folder is blank by default
        vm.onSave()
        advanceUntilIdle()

        assertNull(vm.uiState.value.saveResult)
        coVerify(exactly = 0) { bookmarkRepository.addBookmark(any(), any()) }
    }

    @Test
    fun `onResultConsumed clears saveResult`() = runTest {
        coEvery { bookmarkRepository.findByUrl(sheetId, "https://new.com") } returns null
        coEvery { bookmarkRepository.addBookmark(eq(sheetId), any()) } returns Result.success(
            Bookmark(id = "id", url = "https://new.com", title = "", folder = "F", dateAdded = ""),
        )

        val vm = createViewModel(url = "https://new.com")
        advanceUntilIdle()

        vm.onFolderChanged("F")
        vm.onSave()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.saveResult is SaveResult.Saved)

        vm.onResultConsumed()
        assertNull(vm.uiState.value.saveResult)
    }
}
