// Sheet schema constants — single source of truth for column layout.
// Columns A–I: URL | Title | Folder | Date Added | Notes | Excerpt | Cover | ID | Modified

export const SHEET_NAME = 'Sheet1';

// Numeric column indices (0-based, matching the row arrays from the Sheets API)
export const COL_URL = 0;
export const COL_TITLE = 1;
export const COL_FOLDER = 2;
export const COL_DATE_ADDED = 3;
export const COL_NOTES = 4;
export const COL_EXCERPT = 5;
export const COL_COVER = 6;
export const COL_ID = 7;
export const COL_MODIFIED = 8;

// Column letter for the ID column (used in single-cell updates like backfill)
export const LETTER_ID = 'H';

// Range strings for the Sheets API
export const RANGE_ALL = `${SHEET_NAME}!A:I`;
export const RANGE_HEADER = `${SHEET_NAME}!A1:I1`;

// Header row written when initialising a new sheet
export const HEADERS = ['URL', 'Title', 'Folder', 'Date Added', 'Notes', 'Excerpt', 'Cover', 'ID', 'Modified'];
