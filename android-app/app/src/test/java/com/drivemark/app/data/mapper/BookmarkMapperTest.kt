package com.drivemark.app.data.mapper

import com.drivemark.app.data.SheetColumns
import com.drivemark.app.data.local.BookmarkEntity
import com.drivemark.app.domain.model.Bookmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkMapperTest {

    // Canonical A–I column map, resolved from the canonical headers.
    private val canonical = SheetColumns.resolveColumns(SheetColumns.HEADERS)
    private val canonicalWidth = SheetColumns.HEADERS.size

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
    fun `domainToSheetRow produces correct 9-element list in canonical order`() {
        val row = BookmarkMapper.domainToSheetRow(sampleBookmark, canonical, canonicalWidth)
        assertEquals(9, row.size)
        assertEquals("https://example.com", row[0])
        assertEquals("Example", row[1])
        assertEquals("Tech", row[2])
        assertEquals("2024-06-15T10:00:00Z", row[3])
        assertEquals("Some notes", row[4])
        assertEquals("An excerpt", row[5])
        assertEquals("https://example.com/cover.jpg", row[6])
        assertEquals("test-id-123", row[7])
        assertEquals("2024-06-15T12:00:00Z", row[8])
    }

    @Test
    fun `domainToSheetRow places fields at resolved indexes for a reordered sheet`() {
        val header = listOf("ID", "URL", "Modified")
        val colmap = SheetColumns.resolveColumns(header)
        val row = BookmarkMapper.domainToSheetRow(sampleBookmark, colmap, header.size)
        assertEquals(listOf("test-id-123", "https://example.com", "2024-06-15T12:00:00Z"), row)
    }

    @Test
    fun `domainToSheetRow omits fields whose header is absent`() {
        val header = listOf("URL", "Date Added", "ID", "Modified") // no Title/Folder/Notes/...
        val colmap = SheetColumns.resolveColumns(header)
        val row = BookmarkMapper.domainToSheetRow(sampleBookmark, colmap, header.size)
        assertEquals(4, row.size)
        assertEquals("https://example.com", row[0])
        assertEquals("2024-06-15T10:00:00Z", row[1])
        assertEquals("test-id-123", row[2])
        assertEquals("2024-06-15T12:00:00Z", row[3])
        assertTrue("Title must not appear", !row.contains("Example"))
    }

    @Test
    fun `sheetRowToDomain with full 9-column row`() {
        val row: List<Any> = listOf(
            "https://example.com", "Example", "Tech",
            "2024-06-15T10:00:00Z", "Some notes", "An excerpt",
            "https://example.com/cover.jpg", "test-id-123",
            "2024-06-15T12:00:00Z",
        )
        val result = BookmarkMapper.sheetRowToDomain(row, canonical)
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
    fun `sheetRowToDomain locates columns by header for a reordered row`() {
        val header = listOf("ID", "URL", "Modified", "Date Added", "Title", "Folder", "Notes", "Excerpt", "Cover")
        val colmap = SheetColumns.resolveColumns(header)
        val row: List<Any> = listOf(
            "test-id-123", "https://example.com", "2024-06-15T12:00:00Z", "2024-06-15T10:00:00Z",
            "Example", "Tech", "Some notes", "An excerpt", "https://example.com/cover.jpg",
        )
        val result = BookmarkMapper.sheetRowToDomain(row, colmap)
        assertEquals(sampleBookmark, result)
    }

    @Test
    fun `sheetRowToDomain reads absent display column as empty`() {
        val header = listOf("URL", "ID", "Date Added", "Modified") // no Title
        val colmap = SheetColumns.resolveColumns(header)
        val row: List<Any> = listOf("https://example.com", "id-1", "d", "m")
        val result = BookmarkMapper.sheetRowToDomain(row, colmap)
        assertEquals("https://example.com", result.url)
        assertEquals("id-1", result.id)
        assertEquals("", result.title)
        assertEquals("", result.folder)
    }

    @Test
    fun `sheetRowToDomain with short row defaults missing cells to empty`() {
        val row: List<Any> = listOf("https://example.com", "Title Only")
        val result = BookmarkMapper.sheetRowToDomain(row, canonical)
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
        val result = BookmarkMapper.sheetRowToDomain(row, canonical)
        assertTrue("Generated ID should not be blank", result.id.isNotBlank())
        assertNotEquals("", result.id)
    }

    @Test
    fun `sheetRowToDomain with missing ID cell generates UUID`() {
        val row: List<Any> = listOf("https://example.com", "Title", "Folder",
            "2024-01-01T00:00:00Z", "", "", "")
        val result = BookmarkMapper.sheetRowToDomain(row, canonical)
        assertTrue("Generated ID should be a valid UUID format", result.id.isNotBlank())
    }

    @Test
    fun `round-trip domain to sheetRow to domain preserves data`() {
        val row = BookmarkMapper.domainToSheetRow(sampleBookmark, canonical, canonicalWidth)
        val roundTripped = BookmarkMapper.sheetRowToDomain(row, canonical)
        assertEquals(sampleBookmark, roundTripped)
    }

    @Test
    fun `entityToDomain and domainToEntity round-trip preserves data`() {
        val domain = BookmarkMapper.entityToDomain(sampleEntity)
        val entity = BookmarkMapper.domainToEntity(domain, sampleEntity.spreadsheetId)
        assertEquals(sampleEntity, entity)
    }
}
