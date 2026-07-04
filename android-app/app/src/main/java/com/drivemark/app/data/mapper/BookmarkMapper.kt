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

    fun domainToSheetRow(bookmark: Bookmark): List<Any> = listOf(
        bookmark.url,
        bookmark.title,
        bookmark.folder,
        bookmark.dateAdded,
        bookmark.notes,
        bookmark.excerpt,
        bookmark.coverUrl,
        bookmark.id,
        bookmark.modified,
    )

    fun sheetRowToDomain(row: List<Any>): Bookmark {
        fun col(i: Int): String = row.getOrNull(i)?.toString() ?: ""
        val id = col(SheetColumns.COL_ID).ifBlank { UUID.randomUUID().toString() }
        return Bookmark(
            url = col(SheetColumns.COL_URL),
            title = col(SheetColumns.COL_TITLE),
            folder = col(SheetColumns.COL_FOLDER),
            dateAdded = col(SheetColumns.COL_DATE_ADDED),
            notes = col(SheetColumns.COL_NOTES),
            excerpt = col(SheetColumns.COL_EXCERPT),
            coverUrl = col(SheetColumns.COL_COVER),
            id = id,
            modified = col(SheetColumns.COL_MODIFIED),
        )
    }
}
