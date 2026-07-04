package com.drivemark.app.ui.detail

import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var spreadsheetRepository: SpreadsheetRepository

    private val sampleBookmark = Bookmark(
        id = "bm-1", url = "https://example.com", title = "Example",
        folder = "Tech", dateAdded = "2024-01-01T00:00:00Z",
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        bookmarkRepository = mockk(relaxed = true)
        spreadsheetRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(bookmarkId: String = "bm-1"): BookmarkDetailViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("bookmarkId" to bookmarkId))
        return BookmarkDetailViewModel(bookmarkRepository, spreadsheetRepository, savedStateHandle)
    }

    @Test
    fun `init loads bookmark by ID and sets isLoading false`() = runTest {
        coEvery { bookmarkRepository.getById("bm-1") } returns sampleBookmark

        val vm = createViewModel("bm-1")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.bookmark)
        assertEquals("bm-1", state.bookmark!!.id)
        assertEquals("https://example.com", state.bookmark!!.url)
    }

    @Test
    fun `init with empty bookmarkId results in null bookmark`() = runTest {
        coEvery { bookmarkRepository.getById("") } returns null

        val vm = createViewModel("")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.bookmark)
    }

    @Test
    fun `onDeleteBookmark success sets deleted true`() = runTest {
        coEvery { bookmarkRepository.getById("bm-1") } returns sampleBookmark
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf("sheet-1")
        coEvery { bookmarkRepository.deleteBookmark("sheet-1", sampleBookmark) } returns Result.success(Unit)

        val vm = createViewModel("bm-1")
        advanceUntilIdle()

        vm.onDeleteBookmark()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.deleted)
    }

    @Test
    fun `onDeleteBookmark with null bookmark returns early`() = runTest {
        coEvery { bookmarkRepository.getById("missing") } returns null

        val vm = createViewModel("missing")
        advanceUntilIdle()

        vm.onDeleteBookmark()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.deleted)
    }

    @Test
    fun `onDeleteBookmark with null sheet ID returns early`() = runTest {
        coEvery { bookmarkRepository.getById("bm-1") } returns sampleBookmark
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf(null)

        val vm = createViewModel("bm-1")
        advanceUntilIdle()

        vm.onDeleteBookmark()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.deleted)
    }

    @Test
    fun `onDeleteBookmark failure does not set deleted`() = runTest {
        coEvery { bookmarkRepository.getById("bm-1") } returns sampleBookmark
        coEvery { spreadsheetRepository.getSelectedSpreadsheetId() } returns flowOf("sheet-1")
        coEvery { bookmarkRepository.deleteBookmark("sheet-1", sampleBookmark) } returns Result.failure(RuntimeException("fail"))

        val vm = createViewModel("bm-1")
        advanceUntilIdle()

        vm.onDeleteBookmark()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.deleted)
    }
}
