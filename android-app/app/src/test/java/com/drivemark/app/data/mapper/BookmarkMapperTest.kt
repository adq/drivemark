package com.drivemark.app.data.mapper

import com.drivemark.app.data.SheetColumns
import com.drivemark.app.data.local.BookmarkEntity
import com.drivemark.app.domain.model.Bookmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkMapperTest {

    private val sampleBookmark = Bookmark(
        id = "test-id-123",
        url = "https://example.com",
        title = "Example",
        folder = "Tech",
        dateAdded = "2024-06-15T10:00:00Z",
        notes = "Some notes",
        excerpt = "An excerpt",
        coverUrl = "https://example.com/cover.jpg",
        modified = "2024-06-15T12:00:00Z",
    )

    private val sampleEntity = BookmarkEntity(
        id = "test-id-123",
        spreadsheetId = "sheet-456",
        url = "https://example.com",
        title = "Example",
        folder = "Tech",
        dateAdded = "2024-06-15T10:00:00Z",
        notes = "Some notes",
        excerpt = "An excerpt",
        coverUrl = "https://example.com/cover.jpg",
        modified = "2024-06-15T12:00:00Z",
    )

    @Test
    fun `entityToDomain maps all fields correctly`() {
        val result = BookmarkMapper.entityToDomain(sampleEntity)
        assertEquals(sampleEntity.id, result.id)
        assertEquals(sampleEntity.url, result.url)
        assertEquals(sampleEntity.title, result.title)
        assertEquals(sampleEntity.folder, result.folder)
        assertEquals(sampleEntity.dateAdded, result.dateAdded)
        assertEquals(sampleEntity.notes, result.notes)
        assertEquals(sampleEntity.excerpt, result.excerpt)
        assertEquals(sampleEntity.coverUrl, result.coverUrl)
        assertEquals(sampleEntity.modified, result.modified)
    }

    @Test
    fun `domainToEntity maps all fields including spreadsheetId`() {
        val result = BookmarkMapper.domainToEntity(sampleBookmark, "sheet-456")
        assertEquals(sampleBookmark.id, result.id)
        assertEquals("sheet-456", result.spreadsheetId)
        assertEquals(sampleBookmark.url, result.url)
        assertEquals(sampleBookmark.title, result.title)
        assertEquals(sampleBookmark.folder, result.folder)
        assertEquals(sampleBookmark.dateAdded, result.dateAdded)
        assertEquals(sampleBookmark.notes, result.notes)
        assertEquals(sampleBookmark.excerpt, result.excerpt)
        assertEquals(sampleBookmark.coverUrl, result.coverUrl)
        assertEquals(sampleBookmark.modified, result.modified)
    }

    @Test
    fun `domainToSheetRow produces correct 9-element list in column order`() {
        val row = BookmarkMapper.domainToSheetRow(sampleBookmark)
        assertEquals(9, row.size)
        assertEquals("https://example.com", row[SheetColumns.COL_URL])
        assertEquals("Example", row[SheetColumns.COL_TITLE])
        assertEquals("Tech", row[SheetColumns.COL_FOLDER])
        assertEquals("2024-06-15T10:00:00Z", row[SheetColumns.COL_DATE_ADDED])
        assertEquals("Some notes", row[SheetColumns.COL_NOTES])
        assertEquals("An excerpt", row[SheetColumns.COL_EXCERPT])
        assertEquals("https://example.com/cover.jpg", row[SheetColumns.COL_COVER])
        assertEquals("test-id-123", row[SheetColumns.COL_ID])
        assertEquals("2024-06-15T12:00:00Z", row[SheetColumns.COL_MODIFIED])
    }

    @Test
    fun `sheetRowToDomain with full 9-column row`() {
        val row: List<Any> = listOf(
            "https://example.com", "Example", "Tech",
            "2024-06-15T10:00:00Z", "Some notes", "An excerpt",
            "https://example.com/cover.jpg", "test-id-123",
            "2024-06-15T12:00:00Z",
        )
        val result = BookmarkMapper.sheetRowToDomain(row)
        assertEquals("test-id-123", result.id)
        assertEquals("https://example.com", result.url)
        assertEquals("Example", result.title)
        assertEquals("Tech", result.folder)
        assertEquals("2024-06-15T10:00:00Z", result.dateAdded)
        assertEquals("Some notes", result.notes)
        assertEquals("An excerpt", result.excerpt)
        assertEquals("https://example.com/cover.jpg", result.coverUrl)
        assertEquals("2024-06-15T12:00:00Z", result.modified)
    }

    @Test
    fun `sheetRowToDomain with short row defaults missing columns to empty`() {
        val row: List<Any> = listOf("https://example.com", "Title Only")
        val result = BookmarkMapper.sheetRowToDomain(row)
        assertEquals("https://example.com", result.url)
        assertEquals("Title Only", result.title)
        assertEquals("", result.folder)
        assertEquals("", result.dateAdded)
        assertEquals("", result.notes)
        assertEquals("", result.excerpt)
        assertEquals("", result.coverUrl)
        assertEquals("", result.modified)
    }

    @Test
    fun `sheetRowToDomain with blank ID generates UUID`() {
        val row: List<Any> = listOf("https://example.com", "Title", "Folder",
            "2024-01-01T00:00:00Z", "", "", "", "", "")
        val result = BookmarkMapper.sheetRowToDomain(row)
        assertTrue("Generated ID should not be blank", result.id.isNotBlank())
        assertNotEquals("", result.id)
    }

    @Test
    fun `sheetRowToDomain with missing ID column generates UUID`() {
        val row: List<Any> = listOf("https://example.com", "Title", "Folder",
            "2024-01-01T00:00:00Z", "", "", "")
        val result = BookmarkMapper.sheetRowToDomain(row)
        assertTrue("Generated ID should be a valid UUID format", result.id.isNotBlank())
    }

    @Test
    fun `round-trip domain to sheetRow to domain preserves data`() {
        val row = BookmarkMapper.domainToSheetRow(sampleBookmark)
        val roundTripped = BookmarkMapper.sheetRowToDomain(row)
        assertEquals(sampleBookmark, roundTripped)
    }

    @Test
    fun `entityToDomain and domainToEntity round-trip preserves data`() {
        val domain = BookmarkMapper.entityToDomain(sampleEntity)
        val entity = BookmarkMapper.domainToEntity(domain, sampleEntity.spreadsheetId)
        assertEquals(sampleEntity, entity)
    }
}
