package com.drivemark.app.data

/**
 * Sheet schema constants — single source of truth for column layout.
 * Columns A–I: URL | Title | Folder | Date Added | Notes | Excerpt | Cover | ID | Modified
 */
object SheetColumns {
    const val SHEET_NAME = "Sheet1"

    // Numeric column indices (0-based, matching the row lists from the Sheets API)
    const val COL_URL = 0
    const val COL_TITLE = 1
    const val COL_FOLDER = 2
    const val COL_DATE_ADDED = 3
    const val COL_NOTES = 4
    const val COL_EXCERPT = 5
    const val COL_COVER = 6
    const val COL_ID = 7
    const val COL_MODIFIED = 8

    // Column letter for the ID column (used in single-cell updates like backfill)
    const val LETTER_ID = "H"

    // Range strings for the Sheets API
    val RANGE_ALL = "$SHEET_NAME!A:I"
    val RANGE_HEADER = "$SHEET_NAME!A1:I1"

    // Header row written when initialising a new sheet
    val HEADERS = listOf("URL", "Title", "Folder", "Date Added", "Notes", "Excerpt", "Cover", "ID", "Modified")
}
