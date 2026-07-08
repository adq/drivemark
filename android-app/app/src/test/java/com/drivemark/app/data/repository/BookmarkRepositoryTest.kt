package com.drivemark.app.data.repository

import com.drivemark.app.data.SheetColumns
import com.drivemark.app.data.local.BookmarkDao
import com.drivemark.app.data.local.BookmarkEntity
import com.drivemark.app.data.local.PreferencesManager
import com.drivemark.app.data.remote.GoogleDriveService
import com.drivemark.app.data.remote.GoogleSheetsService
import com.drivemark.app.domain.model.Bookmark
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.google.api.services.sheets.v4.model.ValueRange
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BookmarkRepositoryTest {

    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var sheetsService: GoogleSheetsService
    private lateinit var driveService: GoogleDriveService
    private lateinit var prefsManager: PreferencesManager
    private lateinit var repository: BookmarkRepository

    private val sheetId = "test-sheet-id"

    @Before
    fun setup() {
        bookmarkDao = mockk(relaxed = true)
        sheetsService = mockk(relaxed = true)
        driveService = mockk(relaxed = true)
        prefsManager = mockk(relaxed = true)
        repository = BookmarkRepository(bookmarkDao, sheetsService, driveService, prefsManager)
    }

    // --- syncBookmarks ---

    @Test
    fun `syncBookmarks skips fetch when cached modifiedTime matches remote`() = runTest {
        coEvery { prefsManager.getLastModifiedTime() } returns "2024-06-15T10:00:00Z"
        coEvery { driveService.getModifiedTime(sheetId) } returns "2024-06-15T10:00:00Z"

        val result = repository.syncBookmarks(sheetId)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { sheetsService.fetchAllRows(any()) }
    }

    @Test
    fun `syncBookmarks fetches when timestamps differ`() = runTest {
        coEvery { prefsManager.getLastModifiedTime() } returns "2024-06-15T09:00:00Z"
        coEvery { driveService.getModifiedTime(sheetId) } returns "2024-06-15T10:00:00Z"
        coEvery { sheetsService.fetchAllRows(sheetId) } returns listOf(
            listOf("URL", "Title", "Folder", "Date Added", "Notes", "Excerpt", "Cover", "ID", "Modified"),
            listOf("https://example.com", "Example", "", "2024-01-01T00:00:00Z", "", "", "", "id-1", "2024-01-01T00:00:00Z"),
        )

        val result = repository.syncBookmarks(sheetId)

        assertTrue(result.isSuccess)
        coVerify { sheetsService.fetchAllRows(sheetId) }
        coVerify { prefsManager.setLastModifiedTime("2024-06-15T10:00:00Z") }
    }

    @Test
    fun `syncBookmarks fetches when no cached time exists`() = runTest {
        coEvery { prefsManager.getLastModifiedTime() } returns null
        coEvery { driveService.getModifiedTime(sheetId) } returns "2024-06-15T10:00:00Z"
        coEvery { sheetsService.fetchAllRows(sheetId) } returns emptyList()

        val result = repository.syncBookmarks(sheetId)

        assertTrue(result.isSuccess)
        coVerify { sheetsService.fetchAllRows(sheetId) }
    }

    @Test
    fun `syncBookmarks clears selection on 403 GoogleJsonResponseException`() = runTest {
        val exception = GoogleJsonResponseException(
            HttpResponseException.Builder(403, "Forbidden", HttpHeaders()),
            null,
        )
        coEvery { driveService.getModifiedTime(sheetId) } throws exception

        val result = repository.syncBookmarks(sheetId)

        assertTrue(result.isFailure)
        coVerify { prefsManager.clearSelectedSpreadsheet() }
    }

    @Test
    fun `syncBookmarks clears selection on 404 GoogleJsonResponseException`() = runTest {
        val exception = GoogleJsonResponseException(
            HttpResponseException.Builder(404, "Not Found", HttpHeaders()),
            null,
        )
        coEvery { driveService.getModifiedTime(sheetId) } throws exception

        val result = repository.syncBookmarks(sheetId)

        assertTrue(result.isFailure)
        coVerify { prefsManager.clearSelectedSpreadsheet() }
    }

    @Test
    fun `syncBookmarks does not clear selection on other exceptions`() = runTest {
        coEvery { driveService.getModifiedTime(sheetId) } throws RuntimeException("network error")

        val result = repository.syncBookmarks(sheetId)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { prefsManager.clearSelectedSpreadsheet() }
    }

    // --- forceSync ---

    @Test
    fun `forceSync always fetches regardless of cache`() = runTest {
        coEvery { sheetsService.fetchAllRows(sheetId) } returns emptyList()
        coEvery { driveService.getModifiedTime(sheetId) } returns "2024-06-15T10:00:00Z"

        val result = repository.forceSync(sheetId)

        assertTrue(result.isSuccess)
        coVerify { sheetsService.fetchAllRows(sheetId) }
        coVerify { prefsManager.setLastModifiedTime("2024-06-15T10:00:00Z") }
    }

    @Test
    fun `forceSync returns failure on exception`() = runTest {
        coEvery { sheetsService.fetchAllRows(sheetId) } throws RuntimeException("fail")

        val result = repository.forceSync(sheetId)

        assertTrue(result.isFailure)
    }

    // --- fetchAndCacheBookmarks (tested via sync) - deduplication ---

    @Test
    fun `sync deduplicates rows keeping latest by modified timestamp`() = runTest {
        coEvery { prefsManager.getLastModifiedTime() } returns null
        coEvery { driveService.getModifiedTime(sheetId) } returns "now"
        coEvery { sheetsService.fetchAllRows(sheetId) } returns listOf(
            listOf("https://old.com", "Old", "", "2024-01-01T00:00:00Z", "", "", "", "dup-id", "2024-01-01T00:00:00Z"),
            listOf("https://new.com", "New", "", "2024-01-01T00:00:00Z", "", "", "", "dup-id", "2024-06-01T00:00:00Z"),
        )

        repository.syncBookmarks(sheetId)

        val slot = slot<List<BookmarkEntity>>()
        coVerify { bookmarkDao.insertAll(capture(slot)) }
        assertEquals(1, slot.captured.size)
        assertEquals("https://new.com", slot.captured[0].url)
    }

    @Test
    fun `sync removes tombstones with blank URL`() = runTest {
        coEvery { prefsManager.getLastModifiedTime() } returns null
        coEvery { driveService.getModifiedTime(sheetId) } returns "now"
        coEvery { sheetsService.fetchAllRows(sheetId) } returns listOf(
            listOf("https://example.com", "Live", "", "2024-01-01T00:00:00Z", "", "", "", "live-id", "2024-01-01T00:00:00Z"),
            listOf("", "", "", "2024-01-01T00:00:00Z", "", "", "", "dead-id", "2024-06-01T00:00:00Z"),
        )

        repository.syncBookmarks(sheetId)

        val slot = slot<List<BookmarkEntity>>()
        coVerify { bookmarkDao.insertAll(capture(slot)) }
        assertEquals(1, slot.captured.size)
        assertEquals("live-id", slot.captured[0].id)
    }

    @Test
    fun `sync skips header row where first cell is url`() = runTest {
        coEvery { prefsManager.getLastModifiedTime() } returns null
        coEvery { driveService.getModifiedTime(sheetId) } returns "now"
        coEvery { sheetsService.fetchAllRows(sheetId) } returns listOf(
            listOf("URL", "Title", "Folder", "Date Added", "Notes", "Excerpt", "Cover", "ID", "Modified"),
            listOf("https://example.com", "Test", "", "2024-01-01T00:00:00Z", "", "", "", "id-1", ""),
        )

        repository.syncBookmarks(sheetId)

        val slot = slot<List<BookmarkEntity>>()
        coVerify { bookmarkDao.insertAll(capture(slot)) }
        assertEquals(1, slot.captured.size)
        assertEquals("https://example.com", slot.captured[0].url)
    }

    // --- backfillIds ---

    @Test
    fun `sync backfills IDs for rows missing column H`() = runTest {
        coEvery { prefsManager.getLastModifiedTime() } returns null
        coEvery { driveService.getModifiedTime(sheetId) } returns "now"
        coEvery { sheetsService.fetchAllRows(sheetId) } returns listOf(
            listOf("URL", "Title", "Folder", "Date Added", "Notes", "Excerpt", "Cover", "ID", "Modified"),
            listOf("https://example.com", "Test", "", "2024-01-01T00:00:00Z", "", "", "", "", ""),
        )

        repository.syncBookmarks(sheetId)

        val slot = slot<List<ValueRange>>()
        coVerify { sheetsService.batchUpdateCells(sheetId, capture(slot)) }
        assertEquals(1, slot.captured.size)
        assertEquals("${SheetColumns.SHEET_NAME}!${SheetColumns.columnLetter(7)}2", slot.captured[0].range)
    }

    @Test
    fun `sync does not backfill when all rows have IDs`() = runTest {
        coEvery { prefsManager.getLastModifiedTime() } returns null
        coEvery { driveService.getModifiedTime(sheetId) } returns "now"
        coEvery { sheetsService.fetchAllRows(sheetId) } returns listOf(
            listOf("https://example.com", "Test", "", "2024-01-01T00:00:00Z", "", "", "", "has-id", ""),
        )

        repository.syncBookmarks(sheetId)

        coVerify(exactly = 0) { sheetsService.batchUpdateCells(any(), any()) }
    }

    // --- addBookmark ---

    @Test
    fun `addBookmark generates UUID and timestamps then appends to sheet and DB`() = runTest {
        val input = Bookmark(
            id = "", url = "https://example.com", title = "Test",
            folder = "Tech", dateAdded = "", notes = "", excerpt = "", coverUrl = "",
        )
        coEvery { sheetsService.ensureHeaders(sheetId) } returns SheetColumns.HEADERS

        val result = repository.addBookmark(sheetId, input)

        assertTrue(result.isSuccess)
        val added = result.getOrThrow()
        assertTrue(added.id.isNotBlank())
        assertTrue(added.dateAdded.isNotBlank())
        assertTrue(added.modified.isNotBlank())

        coVerify { sheetsService.ensureHeaders(sheetId) }
        coVerify { sheetsService.appendRow(sheetId, any()) }
        coVerify { bookmarkDao.insert(any()) }
    }

    @Test
    fun `addBookmark returns failure on exception`() = runTest {
        coEvery { sheetsService.ensureHeaders(any()) } throws RuntimeException("fail")

        val result = repository.addBookmark(sheetId, Bookmark(
            id = "", url = "https://example.com", title = "Test",
            folder = "", dateAdded = "",
        ))

        assertTrue(result.isFailure)
    }

    // --- updateBookmark ---

    @Test
    fun `updateBookmark appends new row and updates DB entity`() = runTest {
        val bookmark = Bookmark(
            id = "existing-id", url = "https://example.com", title = "Updated",
            folder = "Tech", dateAdded = "2024-01-01T00:00:00Z",
        )

        val result = repository.updateBookmark(sheetId, bookmark)

        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()
        assertEquals("existing-id", updated.id)
        assertTrue("modified should be set", updated.modified.isNotBlank())

        coVerify { sheetsService.appendRow(sheetId, any()) }
        coVerify { bookmarkDao.update(any()) }
    }

    // --- deleteBookmark ---

    @Test
    fun `deleteBookmark appends tombstone with empty URL and deletes from DB`() = runTest {
        val bookmark = Bookmark(
            id = "del-id", url = "https://example.com", title = "To Delete",
            folder = "", dateAdded = "2024-01-01T00:00:00Z",
        )

        val result = repository.deleteBookmark(sheetId, bookmark)

        assertTrue(result.isSuccess)

        val rowSlot = slot<List<Any>>()
        coVerify { sheetsService.appendRow(sheetId, capture(rowSlot)) }
        assertEquals("", rowSlot.captured[0]) // URL column is empty (tombstone)
        assertEquals("del-id", rowSlot.captured[7]) // ID preserved

        coVerify { bookmarkDao.deleteById("del-id") }
    }

    // --- cleanupSheet ---

    @Test
    fun `cleanupSheet identifies and deletes superseded rows`() = runTest {
        coEvery { sheetsService.fetchAllRows(sheetId) } returns listOf(
            listOf("URL", "Title", "Folder", "Date Added", "Notes", "Excerpt", "Cover", "ID", "Modified"),
            listOf("https://old.com", "Old", "", "2024-01-01T00:00:00Z", "", "", "", "dup-id", "2024-01-01T00:00:00Z"),
            listOf("https://new.com", "New", "", "2024-01-01T00:00:00Z", "", "", "", "dup-id", "2024-06-01T00:00:00Z"),
        )

        val result = repository.cleanupSheet(sheetId)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow()) // 1 superseded row deleted

        val rowsSlot = slot<List<Int>>()
        coVerify { sheetsService.deleteRows(sheetId, capture(rowsSlot)) }
        assertEquals(listOf(2), rowsSlot.captured) // row 2 (1-based) is the old one
    }

    @Test
    fun `cleanupSheet deletes tombstone when it is the only version`() = runTest {
        coEvery { sheetsService.fetchAllRows(sheetId) } returns listOf(
            listOf("URL", "Title", "Folder", "Date Added", "Notes", "Excerpt", "Cover", "ID", "Modified"),
            listOf("", "", "", "2024-01-01T00:00:00Z", "", "", "", "tomb-id", "2024-06-01T00:00:00Z"),
        )

        val result = repository.cleanupSheet(sheetId)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow())
    }

    @Test
    fun `cleanupSheet deletes all versions when best is a tombstone`() = runTest {
        coEvery { sheetsService.fetchAllRows(sheetId) } returns listOf(
            listOf("URL", "Title", "Folder", "Date Added", "Notes", "Excerpt", "Cover", "ID", "Modified"),
            listOf("https://example.com", "Title", "", "2024-01-01T00:00:00Z", "", "", "", "id-1", "2024-01-01T00:00:00Z"),
            listOf("", "", "", "2024-01-01T00:00:00Z", "", "", "", "id-1", "2024-06-01T00:00:00Z"),
        )

        val result = repository.cleanupSheet(sheetId)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow()) // both rows deleted
    }

    @Test
    fun `cleanupSheet returns 0 when nothing to clean`() = runTest {
        coEvery { sheetsService.fetchAllRows(sheetId) } returns listOf(
            listOf("URL", "Title", "Folder", "Date Added", "Notes", "Excerpt", "Cover", "ID", "Modified"),
            listOf("https://example.com", "Title", "", "2024-01-01T00:00:00Z", "", "", "", "id-1", "2024-01-01T00:00:00Z"),
        )

        val result = repository.cleanupSheet(sheetId)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow())
    }

    // --- case-insensitive / reordered column resolution ---

    @Test
    fun `sync resolves reordered columns by header name`() = runTest {
        coEvery { prefsManager.getLastModifiedTime() } returns null
        coEvery { driveService.getModifiedTime(sheetId) } returns "now"
        coEvery { sheetsService.fetchAllRows(sheetId) } returns listOf(
            listOf("ID", "URL", "Modified", "Date Added", "Title", "Folder", "Notes", "Excerpt", "Cover"),
            listOf("id-1", "https://example.com", "2024-06-01T00:00:00Z", "2024-01-01T00:00:00Z", "Example", "Tech", "", "", ""),
        )

        repository.syncBookmarks(sheetId)

        val slot = slot<List<BookmarkEntity>>()
        coVerify { bookmarkDao.insertAll(capture(slot)) }
        assertEquals(1, slot.captured.size)
        assertEquals("id-1", slot.captured[0].id)
        assertEquals("https://example.com", slot.captured[0].url)
        assertEquals("Example", slot.captured[0].title)
        assertEquals("Tech", slot.captured[0].folder)
    }

    @Test
    fun `sync matches headers case-insensitively`() = runTest {
        coEvery { prefsManager.getLastModifiedTime() } returns null
        coEvery { driveService.getModifiedTime(sheetId) } returns "now"
        coEvery { sheetsService.fetchAllRows(sheetId) } returns listOf(
            listOf("url", " Title ", "folder", "date added", "notes", "excerpt", "cover", "ID", "modified"),
            listOf("https://example.com", "Example", "", "2024-01-01T00:00:00Z", "", "", "", "id-1", ""),
        )

        repository.syncBookmarks(sheetId)

        val slot = slot<List<BookmarkEntity>>()
        coVerify { bookmarkDao.insertAll(capture(slot)) }
        assertEquals(1, slot.captured.size)
        assertEquals("https://example.com", slot.captured[0].url)
        assertEquals("id-1", slot.captured[0].id)
    }

    @Test
    fun `sync backfills ID at its resolved column for a reordered sheet`() = runTest {
        coEvery { prefsManager.getLastModifiedTime() } returns null
        coEvery { driveService.getModifiedTime(sheetId) } returns "now"
        coEvery { sheetsService.fetchAllRows(sheetId) } returns listOf(
            listOf("ID", "URL", "Modified", "Date Added", "Title", "Folder", "Notes", "Excerpt", "Cover"),
            listOf("", "https://example.com", "", "2024-01-01T00:00:00Z", "Example", "", "", "", ""),
        )

        repository.syncBookmarks(sheetId)

        val slot = slot<List<ValueRange>>()
        coVerify { sheetsService.batchUpdateCells(sheetId, capture(slot)) }
        assertEquals(1, slot.captured.size)
        // ID is column A (index 0) in this layout, data row is sheet row 2
        assertEquals("${SheetColumns.SHEET_NAME}!${SheetColumns.columnLetter(0)}2", slot.captured[0].range)
    }

    @Test
    fun `forceSync fails when an essential column is missing`() = runTest {
        coEvery { driveService.getModifiedTime(sheetId) } returns "now"
        coEvery { sheetsService.fetchAllRows(sheetId) } returns listOf(
            listOf("URL", "Title", "Date Added", "Modified"), // no ID header
            listOf("https://example.com", "Example", "2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z"),
        )

        val result = repository.forceSync(sheetId)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { bookmarkDao.insertAll(any()) }
    }

    @Test
    fun `addBookmark writes row matching a reordered header`() = runTest {
        coEvery { sheetsService.ensureHeaders(sheetId) } returns
            listOf("ID", "URL", "Modified", "Date Added", "Title", "Folder", "Notes", "Excerpt", "Cover")

        val input = Bookmark(
            id = "", url = "https://example.com", title = "Test", folder = "Tech", dateAdded = "",
        )
        val result = repository.addBookmark(sheetId, input)

        assertTrue(result.isSuccess)
        val rowSlot = slot<List<Any>>()
        coVerify { sheetsService.appendRow(sheetId, capture(rowSlot)) }
        val row = rowSlot.captured
        assertEquals(result.getOrThrow().id, row[0]) // ID at column A
        assertEquals("https://example.com", row[1]) // URL at column B
        assertEquals("Test", row[4]) // Title at column E
    }

    @Test
    fun `updateBookmark writes row matching the fetched header layout`() = runTest {
        coEvery { sheetsService.fetchHeaderRow(sheetId) } returns
            listOf("ID", "URL", "Modified", "Date Added", "Title", "Folder", "Notes", "Excerpt", "Cover")

        val bookmark = Bookmark(
            id = "existing-id", url = "https://example.com", title = "Updated",
            folder = "Tech", dateAdded = "2024-01-01T00:00:00Z",
        )
        val result = repository.updateBookmark(sheetId, bookmark)

        assertTrue(result.isSuccess)
        val rowSlot = slot<List<Any>>()
        coVerify { sheetsService.appendRow(sheetId, capture(rowSlot)) }
        assertEquals("existing-id", rowSlot.captured[0]) // ID at column A
        assertEquals("https://example.com", rowSlot.captured[1]) // URL at column B
    }

    // --- findByUrl / getById ---

    @Test
    fun `findByUrl delegates to DAO and maps result`() = runTest {
        val entity = BookmarkEntity(
            id = "id-1", spreadsheetId = sheetId, url = "https://example.com",
            title = "Test", folder = "", dateAdded = "2024-01-01T00:00:00Z",
        )
        coEvery { bookmarkDao.findByUrl(sheetId, "https://example.com") } returns entity

        val result = repository.findByUrl(sheetId, "https://example.com")

        assertNotNull(result)
        assertEquals("id-1", result!!.id)
        assertEquals("https://example.com", result.url)
    }

    @Test
    fun `getById delegates to DAO and maps result`() = runTest {
        val entity = BookmarkEntity(
            id = "id-1", spreadsheetId = sheetId, url = "https://example.com",
            title = "Test", folder = "", dateAdded = "2024-01-01T00:00:00Z",
        )
        coEvery { bookmarkDao.getById("id-1") } returns entity

        val result = repository.getById("id-1")

        assertNotNull(result)
        assertEquals("id-1", result!!.id)
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        coEvery { bookmarkDao.getById("missing") } returns null

        val result = repository.getById("missing")

        assertEquals(null, result)
    }

    // --- observeBookmarks ---

    @Test
    fun `observeBookmarks maps entities to domain models`() = runTest {
        val entities = listOf(
            BookmarkEntity(
                id = "id-1", spreadsheetId = sheetId, url = "https://example.com",
                title = "Test", folder = "Folder", dateAdded = "2024-01-01T00:00:00Z",
            )
        )
        coEvery { bookmarkDao.observeAll(sheetId) } returns flowOf(entities)

        val flow = repository.observeBookmarks(sheetId)
        flow.collect { bookmarks ->
            assertEquals(1, bookmarks.size)
            assertEquals("id-1", bookmarks[0].id)
            assertEquals("https://example.com", bookmarks[0].url)
        }
    }
}
