package com.drivemark.app.ui.bookmarks

import com.drivemark.app.data.repository.AuthRepository
import com.drivemark.app.data.repository.AuthState
import com.drivemark.app.data.repository.BookmarkRepository
import com.drivemark.app.data.repository.SpreadsheetRepository
import com.drivemark.app.domain.model.Bookmark
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class BookmarkListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var spreadsheetRepository: SpreadsheetRepository
    private lateinit var authRepository: AuthRepository

    private val sheetId = "sheet-1"
    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.Authenticated("user@test.com", "User"))

    private val sampleBookmark = Bookmark(
        id = "bm-1", url = "https://example.com", title = "Example",
        folder = "Tech", dateAdded = "2024-01-01T00:00:00Z",
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        bookmarkRepository = mockk(relaxed = true)
        spreadsheetRepository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)

        every { authRepository.authState } returns authStateFlow
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf(sheetId)
        coEvery { spreadsheetRepository.getSelectedSpreadsheetName() } returns flowOf("My Sheet")
        coEvery { bookmarkRepository.syncBookmarks(sheetId) } returns Result.success(Unit)
        coEvery { bookmarkRepository.observeBookmarks(sheetId) } returns flowOf(emptyList())
        coEvery { bookmarkRepository.searchBookmarks(sheetId, any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): BookmarkListViewModel {
        return BookmarkListViewModel(bookmarkRepository, spreadsheetRepository, authRepository)
    }

    @Test
    fun `init loads spreadsheet ID and name`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(sheetId, vm.uiState.value.spreadsheetId)
        assertEquals("My Sheet", vm.uiState.value.spreadsheetName)
    }

    @Test
    fun `init triggers sync when sheetId exists`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        coVerify { bookmarkRepository.syncBookmarks(sheetId) }
    }

    @Test
    fun `init does not sync when no sheetId`() = runTest {
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf(null)

        val vm = createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { bookmarkRepository.syncBookmarks(any()) }
    }

    @Test
    fun `onSearchQueryChanged updates search query in state`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSearchQueryChanged("kotlin")

        assertEquals("kotlin", vm.uiState.value.searchQuery)
    }

    @Test
    fun `onToggleFolder adds folder to expanded set`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onToggleFolder("Tech")

        assertTrue("Tech" in vm.uiState.value.expandedFolders)
    }

    @Test
    fun `onToggleFolder removes folder when already expanded`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onToggleFolder("Tech")
        assertTrue("Tech" in vm.uiState.value.expandedFolders)

        vm.onToggleFolder("Tech")
        assertFalse("Tech" in vm.uiState.value.expandedFolders)
    }

    @Test
    fun `onRefresh calls forceSync and toggles isRefreshing`() = runTest {
        coEvery { bookmarkRepository.forceSync(sheetId) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onRefresh()
        advanceUntilIdle()

        coVerify { bookmarkRepository.forceSync(sheetId) }
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `onDeleteBookmark success sets deletedBookmark in state`() = runTest {
        coEvery { bookmarkRepository.deleteBookmark(sheetId, sampleBookmark) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onDeleteBookmark(sampleBookmark)
        advanceUntilIdle()

        assertEquals(sampleBookmark, vm.uiState.value.deletedBookmark)
    }

    @Test
    fun `onDeleteBookmark failure sets error`() = runTest {
        coEvery { bookmarkRepository.deleteBookmark(sheetId, sampleBookmark) } returns Result.failure(RuntimeException("Delete failed"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onDeleteBookmark(sampleBookmark)
        advanceUntilIdle()

        assertEquals("Delete failed", vm.uiState.value.error)
    }

    @Test
    fun `onUndoDelete re-adds bookmark and clears deletedBookmark`() = runTest {
        coEvery { bookmarkRepository.deleteBookmark(sheetId, sampleBookmark) } returns Result.success(Unit)
        coEvery { bookmarkRepository.addBookmark(sheetId, sampleBookmark) } returns Result.success(sampleBookmark)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onDeleteBookmark(sampleBookmark)
        advanceUntilIdle()
        assertEquals(sampleBookmark, vm.uiState.value.deletedBookmark)

        vm.onUndoDelete()
        advanceUntilIdle()

        assertNull(vm.uiState.value.deletedBookmark)
        coVerify { bookmarkRepository.addBookmark(sheetId, sampleBookmark) }
    }

    @Test
    fun `onDismissSnackbar clears deletedBookmark and error`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onDismissSnackbar()

        assertNull(vm.uiState.value.deletedBookmark)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `onChangeSheet calls clearSelection`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onChangeSheet()
        advanceUntilIdle()

        coVerify { spreadsheetRepository.clearSelection() }
    }

    @Test
    fun `onCleanup success with count greater than 0 shows removed message`() = runTest {
        coEvery { bookmarkRepository.cleanupSheet(sheetId) } returns Result.success(5)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCleanup()
        advanceUntilIdle()

        assertEquals("Sync complete — removed 5 old rows", vm.uiState.value.cleanupResult)
        assertFalse(vm.uiState.value.isCleaningUp)
    }

    @Test
    fun `onCleanup success with count 0 shows already clean`() = runTest {
        coEvery { bookmarkRepository.cleanupSheet(sheetId) } returns Result.success(0)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCleanup()
        advanceUntilIdle()

        assertEquals("Bookmarks are already in sync", vm.uiState.value.cleanupResult)
    }

    @Test
    fun `onCleanup failure sets error`() = runTest {
        coEvery { bookmarkRepository.cleanupSheet(sheetId) } returns Result.failure(RuntimeException("API error"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCleanup()
        advanceUntilIdle()

        assertEquals("Sync failed: API error", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isCleaningUp)
    }

    @Test
    fun `onDismissCleanupResult clears cleanupResult`() = runTest {
        coEvery { bookmarkRepository.cleanupSheet(sheetId) } returns Result.success(3)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCleanup()
        advanceUntilIdle()
        assertEquals("Sync complete — removed 3 old rows", vm.uiState.value.cleanupResult)

        vm.onDismissCleanupResult()
        assertNull(vm.uiState.value.cleanupResult)
    }

    @Test
    fun `onSignOut delegates to authRepository`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSignOut()
        advanceUntilIdle()

        coVerify { authRepository.signOut() }
    }

    @Test
    fun `onConsentResult false sets permission error`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onConsentResult(false)

        assertEquals("Google Drive permission is required to sync bookmarks", vm.uiState.value.error)
        assertNull(vm.uiState.value.consentIntent)
    }

    @Test
    fun `bookmarks flow groups by folder with Uncategorized for blank folders`() = runTest {
        val bookmarks = listOf(
            sampleBookmark,
            sampleBookmark.copy(id = "bm-2", folder = ""),
        )
        coEvery { bookmarkRepository.observeBookmarks(sheetId) } returns flowOf(bookmarks)

        val vm = createViewModel()
        advanceUntilIdle()

        val folders = vm.uiState.value.folders
        assertTrue(folders.isNotEmpty())
        val folderNames = folders.map { it.name }
        assertTrue("Tech" in folderNames)
        assertTrue("Uncategorized" in folderNames)
    }

    @Test
    fun `bookmarks flow builds nested folder tree`() = runTest {
        val bookmarks = listOf(
            sampleBookmark.copy(id = "bm-1", folder = "Tech/Android"),
            sampleBookmark.copy(id = "bm-2", folder = "Tech/iOS"),
            sampleBookmark.copy(id = "bm-3", folder = "News"),
        )
        coEvery { bookmarkRepository.observeBookmarks(sheetId) } returns flowOf(bookmarks)

        val vm = createViewModel()
        advanceUntilIdle()

        val folders = vm.uiState.value.folders
        val techNode = folders.find { it.name == "Tech" }
        assertTrue("Tech node should exist", techNode != null)
        assertEquals(2, techNode!!.children.size)
        assertEquals(2, techNode.totalCount)

        val androidNode = techNode.children.find { it.name == "Android" }
        assertEquals("Tech/Android", androidNode!!.fullPath)
        assertEquals(1, androidNode.bookmarks.size)
    }
}
