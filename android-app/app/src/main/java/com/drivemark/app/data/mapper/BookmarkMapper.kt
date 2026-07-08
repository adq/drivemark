package com.drivemark.app.data.mapper

import com.drivemark.app.data.SheetColumns
import com.drivemark.app.data.local.BookmarkEntity
import com.drivemark.app.domain.model.Bookmark
import java.util.UUID

object BookmarkMapper {

    fun entityToDomain(entity: BookmarkEntity): Bookmark = Bookmark(
        id = entity.id,
        url = entity.url,
        title = entity.title,
        folder = entity.folder,
        dateAdded = entity.dateAdded,
        notes = entity.notes,
        excerpt = entity.excerpt,
        coverUrl = entity.coverUrl,
        modified = entity.modified,
    )

    fun domainToEntity(bookmark: Bookmark, spreadsheetId: String): BookmarkEntity = BookmarkEntity(
        id = bookmark.id,
        spreadsheetId = spreadsheetId,
        url = bookmark.url,
        title = bookmark.title,
        folder = bookmark.folder,
        dateAdded = bookmark.dateAdded,
        notes = bookmark.notes,
        excerpt = bookmark.excerpt,
        coverUrl = bookmark.coverUrl,
        modified = bookmark.modified,
    )

    private fun fieldValue(bookmark: Bookmark, field: SheetColumns.Field): String = when (field) {
        SheetColumns.Field.URL -> bookmark.url
        SheetColumns.Field.TITLE -> bookmark.title
        SheetColumns.Field.FOLDER -> bookmark.folder
        SheetColumns.Field.DATE_ADDED -> bookmark.dateAdded
        SheetColumns.Field.NOTES -> bookmark.notes
        SheetColumns.Field.EXCERPT -> bookmark.excerpt
        SheetColumns.Field.COVER -> bookmark.coverUrl
        SheetColumns.Field.ID -> bookmark.id
        SheetColumns.Field.MODIFIED -> bookmark.modified
    }

    /**
     * Build an append row, placing each field at its resolved column index. The row is
     * sized to the header width so mapped columns land correctly; unmapped positions are
     * filled with "". Fields whose header is absent (index -1) are omitted.
     */
    fun domainToSheetRow(
        bookmark: Bookmark,
        colmap: Map<SheetColumns.Field, Int>,
        width: Int,
    ): List<Any> {
        val maxIndex = colmap.values.filter { it >= 0 }.maxOrNull() ?: -1
        val size = maxOf(width, maxIndex + 1)
        val row = MutableList<Any>(size) { "" }
        for (field in SheetColumns.Field.values()) {
            val idx = colmap[field] ?: -1
            if (idx >= 0) row[idx] = fieldValue(bookmark, field)
        }
        return row
    }

    /** Read a row into a domain object using the resolved column map; absent fields → "". */
    fun sheetRowToDomain(row: List<Any>, colmap: Map<SheetColumns.Field, Int>): Bookmark {
        fun col(field: SheetColumns.Field): String {
            val idx = colmap[field] ?: -1
            return if (idx >= 0) row.getOrNull(idx)?.toString() ?: "" else ""
        }
        val id = col(SheetColumns.Field.ID).ifBlank { UUID.randomUUID().toString() }
        return Bookmark(
            url = col(SheetColumns.Field.URL),
            title = col(SheetColumns.Field.TITLE),
            folder = col(SheetColumns.Field.FOLDER),
            dateAdded = col(SheetColumns.Field.DATE_ADDED),
            notes = col(SheetColumns.Field.NOTES),
            excerpt = col(SheetColumns.Field.EXCERPT),
            coverUrl = col(SheetColumns.Field.COVER),
            id = id,
            modified = col(SheetColumns.Field.MODIFIED),
        )
    }
}
