package com.drivemark.app.data.remote

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest
import com.google.api.services.sheets.v4.model.DimensionRange
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import com.google.api.services.sheets.v4.model.Spreadsheet as GoogleSpreadsheet
import kotlinx.coroutines.withContext
import com.drivemark.app.data.SheetColumns
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSheetsService @Inject constructor(
    private val authManager: GoogleAuthManager,
) {

    private fun buildService(): Sheets {
        val credential = authManager.getAccountCredential()
            ?: throw IllegalStateException("Not authenticated")
        return Sheets.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        ).setApplicationName("DriveMark").build()
    }

    suspend fun fetchAllRows(spreadsheetId: String): List<List<Any>> = withContext(Dispatchers.IO) {
        buildService().spreadsheets().values()
            .get(spreadsheetId, SheetColumns.RANGE_ALL)
            .execute()
            .getValues() ?: emptyList()
    }

    suspend fun appendRow(spreadsheetId: String, values: List<Any>) = withContext(Dispatchers.IO) {
        val body = ValueRange().setValues(listOf(values))
        buildService().spreadsheets().values()
            .append(spreadsheetId, SheetColumns.RANGE_ALL, body)
            .setValueInputOption("USER_ENTERED")
            .setInsertDataOption("INSERT_ROWS")
            .execute()
    }

    suspend fun deleteRows(spreadsheetId: String, rowIndexes: List<Int>) = withContext(Dispatchers.IO) {
        if (rowIndexes.isEmpty()) return@withContext
        val sheetId = getSheetId(spreadsheetId)
        val sorted = rowIndexes.sortedDescending()
        val requests = sorted.map { rowIdx ->
            Request().setDeleteDimension(
                DeleteDimensionRequest().setRange(
                    DimensionRange()
                        .setSheetId(sheetId)
                        .setDimension("ROWS")
                        .setStartIndex(rowIdx - 1)
                        .setEndIndex(rowIdx)
                )
            )
        }
        val batchRequest = BatchUpdateSpreadsheetRequest().setRequests(requests)
        buildService().spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute()
    }

    /**
     * Ensure the sheet has a header row (writes the canonical HEADERS to an empty sheet),
     * then return the live header row so callers can resolve the column layout.
     */
    suspend fun ensureHeaders(spreadsheetId: String): List<Any> = withContext(Dispatchers.IO) {
        val existing = buildService().spreadsheets().values()
            .get(spreadsheetId, SheetColumns.RANGE_HEADER)
            .execute()
            .getValues()
        val headerRow = existing?.firstOrNull()
        if (headerRow == null || headerRow.isEmpty()) {
            val headers = ValueRange().setValues(listOf(SheetColumns.HEADERS))
            buildService().spreadsheets().values()
                .update(spreadsheetId, SheetColumns.RANGE_HEADER, headers)
                .setValueInputOption("RAW")
                .execute()
            SheetColumns.HEADERS
        } else {
            headerRow
        }
    }

    /** Read the current header row without creating one. Empty list if the sheet has none. */
    suspend fun fetchHeaderRow(spreadsheetId: String): List<Any> = withContext(Dispatchers.IO) {
        buildService().spreadsheets().values()
            .get(spreadsheetId, SheetColumns.RANGE_HEADER)
            .execute()
            .getValues()?.firstOrNull() ?: emptyList()
    }

    suspend fun batchUpdateCells(
        spreadsheetId: String,
        updates: List<ValueRange>,
    ) = withContext(Dispatchers.IO) {
        if (updates.isEmpty()) return@withContext
        val body = BatchUpdateValuesRequest()
            .setValueInputOption("RAW")
            .setData(updates)
        buildService().spreadsheets().values()
            .batchUpdate(spreadsheetId, body)
            .execute()
    }

    suspend fun createSpreadsheet(title: String): String = withContext(Dispatchers.IO) {
        val spreadsheet = GoogleSpreadsheet().apply {
            properties = SpreadsheetProperties().setTitle(title)
        }
        val created = buildService().spreadsheets().create(spreadsheet).execute()
        val id = created.spreadsheetId
        ensureHeaders(id)
        id
    }

    private fun getSheetId(spreadsheetId: String): Int {
        val meta = buildService().spreadsheets().get(spreadsheetId)
            .setFields("sheets(properties(sheetId,title))")
            .execute()
        return meta.sheets.first { it.properties.title == SheetColumns.SHEET_NAME }.properties.sheetId
    }
}
