package com.drivemark.app.data.repository

import com.drivemark.app.data.SheetColumns
import com.drivemark.app.data.local.BookmarkDao
import com.drivemark.app.data.local.PreferencesManager
import com.drivemark.app.data.mapper.BookmarkMapper
import com.drivemark.app.data.remote.GoogleDriveService
import com.drivemark.app.data.remote.GoogleSheetsService
import com.drivemark.app.domain.model.Bookmark
import com.drivemark.app.util.DateFormatter
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val sheetsService: GoogleSheetsService,
    private val driveService: GoogleDriveService,
    private val prefsManager: PreferencesManager,
) {
    fun observeBookmarks(spreadsheetId: String): Flow<List<Bookmark>> =
        bookmarkDao.observeAll(spreadsheetId).map { entities ->
            entities.map(BookmarkMapper::entityToDomain)
        }

    fun searchBookmarks(spreadsheetId: String, query: String): Flow<List<Bookmark>> =
        bookmarkDao.search(spreadsheetId, query).map { entities ->
            entities.map(BookmarkMapper::entityToDomain)
        }

    fun observeFolders(spreadsheetId: String): Flow<List<String>> =
        bookmarkDao.observeFolders(spreadsheetId)

    suspend fun syncBookmarks(spreadsheetId: String): Result<Unit> = try {
        val cachedModified = prefsManager.getLastModifiedTime()
        val remoteModified = driveService.getModifiedTime(spreadsheetId)

        if (cachedModified != null && cachedModified == remoteModified) {
            Result.success(Unit)
        } else {
            fetchAndCacheBookmarks(spreadsheetId)
            prefsManager.setLastModifiedTime(remoteModified)
            Result.success(Unit)
        }
    } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
        if (e.statusCode == 403 || e.statusCode == 404) {
            prefsManager.clearSelectedSpreadsheet()
        }
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun forceSync(spreadsheetId: String): Result<Unit> = try {
        fetchAndCacheBookmarks(spreadsheetId)
        val remoteModified = driveService.getModifiedTime(spreadsheetId)
        prefsManager.setLastModifiedTime(remoteModified)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun fetchAndCacheBookmarks(spreadsheetId: String) {
        val rows = sheetsService.fetchAllRows(spreadsheetId)
        val dataRows = if (rows.isNotEmpty() && isHeaderRow(rows[0])) rows.drop(1) else rows
        val allBookmarks = dataRows.map { row -> BookmarkMapper.sheetRowToDomain(row) }
        val bookmarks = deduplicateRows(allBookmarks)

        bookmarkDao.deleteAllForSheet(spreadsheetId)
        bookmarkDao.insertAll(bookmarks.map { BookmarkMapper.domainToEntity(it, spreadsheetId) })

        // Backfill UUIDs for rows missing IDs
        backfillIds(spreadsheetId, rows, allBookmarks)
    }

    private fun isHeaderRow(row: List<Any>): Boolean {
        val first = row.firstOrNull()?.toString()?.lowercase() ?: return false
        return first == "url"
    }

    private fun deduplicateRows(allBookmarks: List<Bookmark>): List<Bookmark> {
        return allBookmarks
            .groupBy { it.id }
            .mapNotNull { (_, versions) ->
                val latest = versions.maxByOrNull { it.modified.ifBlank { it.dateAdded } }
                    ?: return@mapNotNull null
                // Tombstone check: if URL is blank, bookmark was deleted
                if (latest.url.isBlank()) null else latest
            }
    }

    private suspend fun backfillIds(
        spreadsheetId: String,
        originalRows: List<List<Any>>,
        allBookmarks: List<Bookmark>,
    ) {
        val updates = mutableListOf<ValueRange>()
        val startOffset = if (originalRows.isNotEmpty() && isHeaderRow(originalRows[0])) 1 else 0

        for ((i, bookmark) in allBookmarks.withIndex()) {
            val originalRow = originalRows.getOrNull(i + startOffset) ?: continue
            val originalId = originalRow.getOrNull(SheetColumns.COL_ID)?.toString() ?: ""
            if (originalId.isBlank()) {
                val sheetRow = i + startOffset + 1 // 1-based sheet row
                updates.add(
                    ValueRange()
                        .setRange("${SheetColumns.SHEET_NAME}!${SheetColumns.LETTER_ID}$sheetRow")
                        .setValues(listOf(listOf(bookmark.id)))
                )
            }
        }

        if (updates.isNotEmpty()) {
            sheetsService.batchUpdateCells(spreadsheetId, updates)
        }
    }

    suspend fun addBookmark(spreadsheetId: String, bookmark: Bookmark): Result<Bookmark> = try {
        sheetsService.ensureHeaders(spreadsheetId)
        val now = DateFormatter.nowIso()
        val newBookmark = bookmark.copy(
            id = UUID.randomUUID().toString(),
            dateAdded = now,
            modified = now,
        )
        val row = BookmarkMapper.domainToSheetRow(newBookmark)
        sheetsService.appendRow(spreadsheetId, row)
        bookmarkDao.insert(BookmarkMapper.domainToEntity(newBookmark, spreadsheetId))
        Result.success(newBookmark)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateBookmark(spreadsheetId: String, bookmark: Bookmark): Result<Bookmark> = try {
        val updated = bookmark.copy(modified = DateFormatter.nowIso())
        val row = BookmarkMapper.domainToSheetRow(updated)
        sheetsService.appendRow(spreadsheetId, row)
        bookmarkDao.update(BookmarkMapper.domainToEntity(updated, spreadsheetId))
        Result.success(updated)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteBookmark(spreadsheetId: String, bookmark: Bookmark): Result<Unit> = try {
        // Append a tombstone row (empty URL signals deletion)
        val tombstone = bookmark.copy(
            url = "",
            title = "",
            folder = "",
            notes = "",
            excerpt = "",
            coverUrl = "",
            modified = DateFormatter.nowIso(),
        )
        val row = BookmarkMapper.domainToSheetRow(tombstone)
        sheetsService.appendRow(spreadsheetId, row)
        bookmarkDao.deleteById(bookmark.id)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun cleanupSheet(spreadsheetId: String): Result<Int> = try {
        val rows = sheetsService.fetchAllRows(spreadsheetId)
        val dataRows = if (rows.isNotEmpty() && isHeaderRow(rows[0])) rows.drop(1) else rows
        val startOffset = if (rows.isNotEmpty() && isHeaderRow(rows[0])) 1 else 0

        data class RowVersion(val sheetRow: Int, val ts: String, val url: String)

        val byId = mutableMapOf<String, MutableList<RowVersion>>()
        for ((i, row) in dataRows.withIndex()) {
            val id = row.getOrNull(SheetColumns.COL_ID)?.toString() ?: continue
            if (id.isBlank()) continue
            val modified = row.getOrNull(SheetColumns.COL_MODIFIED)?.toString() ?: ""
            val dateAdded = row.getOrNull(SheetColumns.COL_DATE_ADDED)?.toString() ?: ""
            val ts = modified.ifBlank { dateAdded }
            val url = row.getOrNull(SheetColumns.COL_URL)?.toString() ?: ""
            val sheetRow = i + startOffset + 1 // 1-based sheet row
            byId.getOrPut(id) { mutableListOf() }.add(RowVersion(sheetRow, ts, url))
        }

        val rowsToDelete = mutableListOf<Int>()
        for ((_, versions) in byId) {
            if (versions.size == 1) {
                if (versions[0].url.isBlank()) rowsToDelete.add(versions[0].sheetRow)
                continue
            }
            val best = versions.maxByOrNull { it.ts }!!
            for (v in versions) {
                if (v.sheetRow != best.sheetRow) {
                    rowsToDelete.add(v.sheetRow)
                }
            }
            if (best.url.isBlank()) rowsToDelete.add(best.sheetRow)
        }

        sheetsService.deleteRows(spreadsheetId, rowsToDelete)
        fetchAndCacheBookmarks(spreadsheetId)
        Result.success(rowsToDelete.size)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun findByUrl(spreadsheetId: String, url: String): Bookmark? {
        return bookmarkDao.findByUrl(spreadsheetId, url)?.let(BookmarkMapper::entityToDomain)
    }

    suspend fun getById(id: String): Bookmark? {
        return bookmarkDao.getById(id)?.let(BookmarkMapper::entityToDomain)
    }

    suspend fun getFolders(spreadsheetId: String): List<String> {
        return bookmarkDao.getFolders(spreadsheetId)
    }
}
