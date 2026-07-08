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
    private companion object {
        // Canonical header names, normalized, used to detect a header row.
        val HEADER_NAME_SET = SheetColumns.HEADERS.map { it.trim().lowercase() }.toSet()
    }

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
        val hasHeader = rows.isNotEmpty() && isHeaderRow(rows[0])
        val dataRows = if (hasHeader) rows.drop(1) else rows
        // Resolve columns from the header row; a legacy no-header sheet falls back to the
        // canonical A–I layout to preserve existing behaviour.
        val colmap = if (hasHeader) {
            SheetColumns.resolveColumns(rows[0])
        } else {
            SheetColumns.resolveColumns(SheetColumns.HEADERS)
        }
        if (dataRows.isNotEmpty()) SheetColumns.requireEssentialColumns(colmap)
        val allBookmarks = dataRows.map { row -> BookmarkMapper.sheetRowToDomain(row, colmap) }
        val bookmarks = deduplicateRows(allBookmarks)

        bookmarkDao.deleteAllForSheet(spreadsheetId)
        bookmarkDao.insertAll(bookmarks.map { BookmarkMapper.domainToEntity(it, spreadsheetId) })

        // Backfill UUIDs for rows missing IDs
        backfillIds(spreadsheetId, rows, allBookmarks, colmap)
    }

    // A header row is recognised by ≥2 cells matching canonical header names
    // (case-insensitive), in any column — so a reordered or partially-renamed header is
    // still detected (letting the essential-column guard fire), while data rows, which
    // don't contain two literal header words, are not mistaken for headers.
    private fun isHeaderRow(row: List<Any>): Boolean {
        val matches = row.count { it.toString().trim().lowercase() in HEADER_NAME_SET }
        return matches >= 2
    }

    private fun deduplicateRows(allBookmarks: List<Bookmark>): List<Bookmark> {
        return allBookmarks
            .groupBy { it.id }
            .mapNotNull { (_, versions) ->
                // Keep later-encountered (lower in sheet) row on equal timestamps, matching the
                // Chrome extension's `>=` tie-break so both clients pick the same surviving version.
                val latest = versions.reduceOrNull { acc, v ->
                    if (DateFormatter.toEpochMillis(v.modified.ifBlank { v.dateAdded }) >=
                        DateFormatter.toEpochMillis(acc.modified.ifBlank { acc.dateAdded })) v else acc
                } ?: return@mapNotNull null
                // Tombstone check: if URL is blank, bookmark was deleted
                if (latest.url.isBlank()) null else latest
            }
    }

    private suspend fun backfillIds(
        spreadsheetId: String,
        originalRows: List<List<Any>>,
        allBookmarks: List<Bookmark>,
        colmap: Map<SheetColumns.Field, Int>,
    ) {
        val idIndex = colmap[SheetColumns.Field.ID] ?: -1
        if (idIndex < 0) return
        val idLetter = SheetColumns.columnLetter(idIndex)
        val updates = mutableListOf<ValueRange>()
        val startOffset = if (originalRows.isNotEmpty() && isHeaderRow(originalRows[0])) 1 else 0

        for ((i, bookmark) in allBookmarks.withIndex()) {
            val originalRow = originalRows.getOrNull(i + startOffset) ?: continue
            val originalId = originalRow.getOrNull(idIndex)?.toString() ?: ""
            if (originalId.isBlank()) {
                val sheetRow = i + startOffset + 1 // 1-based sheet row
                updates.add(
                    ValueRange()
                        .setRange("${SheetColumns.SHEET_NAME}!$idLetter$sheetRow")
                        .setValues(listOf(listOf(bookmark.id)))
                )
            }
        }

        if (updates.isNotEmpty()) {
            sheetsService.batchUpdateCells(spreadsheetId, updates)
        }
    }

    // Resolve the live column layout for a write, falling back to the canonical layout if
    // the sheet has no header row yet. Throws if an essential column is absent.
    private suspend fun resolveWriteLayout(spreadsheetId: String): Pair<Map<SheetColumns.Field, Int>, Int> {
        val headerRow = sheetsService.fetchHeaderRow(spreadsheetId)
        val effective: List<Any> = if (headerRow.isEmpty()) SheetColumns.HEADERS else headerRow
        val colmap = SheetColumns.resolveColumns(effective)
        SheetColumns.requireEssentialColumns(colmap)
        return colmap to effective.size
    }

    suspend fun addBookmark(spreadsheetId: String, bookmark: Bookmark): Result<Bookmark> = try {
        val headerRow = sheetsService.ensureHeaders(spreadsheetId)
        val colmap = SheetColumns.resolveColumns(headerRow)
        SheetColumns.requireEssentialColumns(colmap)
        val now = DateFormatter.nowIso()
        val newBookmark = bookmark.copy(
            id = UUID.randomUUID().toString(),
            dateAdded = now,
            modified = now,
        )
        val row = BookmarkMapper.domainToSheetRow(newBookmark, colmap, headerRow.size)
        sheetsService.appendRow(spreadsheetId, row)
        bookmarkDao.insert(BookmarkMapper.domainToEntity(newBookmark, spreadsheetId))
        Result.success(newBookmark)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateBookmark(spreadsheetId: String, bookmark: Bookmark): Result<Bookmark> = try {
        val (colmap, width) = resolveWriteLayout(spreadsheetId)
        val updated = bookmark.copy(modified = DateFormatter.nowIso())
        val row = BookmarkMapper.domainToSheetRow(updated, colmap, width)
        sheetsService.appendRow(spreadsheetId, row)
        bookmarkDao.update(BookmarkMapper.domainToEntity(updated, spreadsheetId))
        Result.success(updated)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteBookmark(spreadsheetId: String, bookmark: Bookmark): Result<Unit> = try {
        val (colmap, width) = resolveWriteLayout(spreadsheetId)
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
        val row = BookmarkMapper.domainToSheetRow(tombstone, colmap, width)
        sheetsService.appendRow(spreadsheetId, row)
        bookmarkDao.deleteById(bookmark.id)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun cleanupSheet(spreadsheetId: String): Result<Int> = try {
        val rows = sheetsService.fetchAllRows(spreadsheetId)
        val hasHeader = rows.isNotEmpty() && isHeaderRow(rows[0])
        val dataRows = if (hasHeader) rows.drop(1) else rows
        val startOffset = if (hasHeader) 1 else 0
        val colmap = if (hasHeader) {
            SheetColumns.resolveColumns(rows[0])
        } else {
            SheetColumns.resolveColumns(SheetColumns.HEADERS)
        }
        if (dataRows.isNotEmpty()) SheetColumns.requireEssentialColumns(colmap)
        val idIndex = colmap.getValue(SheetColumns.Field.ID)
        val modifiedIndex = colmap.getValue(SheetColumns.Field.MODIFIED)
        val dateAddedIndex = colmap.getValue(SheetColumns.Field.DATE_ADDED)
        val urlIndex = colmap.getValue(SheetColumns.Field.URL)

        data class RowVersion(val sheetRow: Int, val ts: String, val url: String)

        val byId = mutableMapOf<String, MutableList<RowVersion>>()
        for ((i, row) in dataRows.withIndex()) {
            val id = row.getOrNull(idIndex)?.toString() ?: continue
            if (id.isBlank()) continue
            val modified = row.getOrNull(modifiedIndex)?.toString() ?: ""
            val dateAdded = row.getOrNull(dateAddedIndex)?.toString() ?: ""
            val ts = modified.ifBlank { dateAdded }
            val url = row.getOrNull(urlIndex)?.toString() ?: ""
            val sheetRow = i + startOffset + 1 // 1-based sheet row
            byId.getOrPut(id) { mutableListOf() }.add(RowVersion(sheetRow, ts, url))
        }

        val rowsToDelete = mutableListOf<Int>()
        for ((_, versions) in byId) {
            if (versions.size == 1) {
                if (versions[0].url.isBlank()) rowsToDelete.add(versions[0].sheetRow)
                continue
            }
            // Later-encountered row wins ties (matches Chrome cleanupSheet `>=`).
            val best = versions.reduce { acc, v ->
                if (DateFormatter.toEpochMillis(v.ts) >= DateFormatter.toEpochMillis(acc.ts)) v else acc
            }
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
