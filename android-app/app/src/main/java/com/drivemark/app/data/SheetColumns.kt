package com.drivemark.app.data

/**
 * Sheet schema — single source of truth for column layout.
 * Columns A–I: URL | Title | Folder | Date Added | Notes | Excerpt | Cover | ID | Modified
 *
 * Column positions are resolved at runtime from the sheet's header row by
 * case-insensitive, trimmed name match (see [resolveColumns]) rather than hardcoded
 * indices, so a user may reorder or re-case columns without breaking reads or writes.
 */
object SheetColumns {
    const val SHEET_NAME = "Sheet1"

    /** A field of a bookmark row, paired with its canonical header text. */
    enum class Field(val header: String) {
        URL("URL"),
        TITLE("Title"),
        FOLDER("Folder"),
        DATE_ADDED("Date Added"),
        NOTES("Notes"),
        EXCERPT("Excerpt"),
        COVER("Cover"),
        ID("ID"),
        MODIFIED("Modified"),
    }

    // Header row written when initialising a new sheet
    val HEADERS: List<String> = Field.values().map { it.header }

    // Columns the append-only/tombstone sync model depends on. If any is absent from
    // a non-empty sheet's header row we refuse to read/write rather than corrupt data.
    val ESSENTIAL_FIELDS = listOf(Field.URL, Field.DATE_ADDED, Field.ID, Field.MODIFIED)

    // Range strings for the Sheets API. A:I spans the canonical layout; the resolver
    // handles any order/casing within it.
    val RANGE_ALL = "$SHEET_NAME!A:I"
    val RANGE_HEADER = "$SHEET_NAME!A1:I1"

    private fun normalize(text: Any?): String = text?.toString()?.trim()?.lowercase() ?: ""

    /**
     * Resolve each field to its 0-based column index by case-insensitive, trimmed
     * match of the header cell text against the canonical header name. Absent fields
     * map to -1.
     */
    fun resolveColumns(headerRow: List<Any>): Map<Field, Int> {
        val normalized = headerRow.map { normalize(it) }
        return Field.values().associateWith { field -> normalized.indexOf(normalize(field.header)) }
    }

    /** Essential fields whose header is missing from a resolved column map. */
    fun missingEssentialFields(colmap: Map<Field, Int>): List<Field> =
        ESSENTIAL_FIELDS.filter { (colmap[it] ?: -1) < 0 }

    /**
     * Throw if any essential column is absent, so a malformed sheet surfaces an error
     * instead of silently corrupting rows.
     */
    fun requireEssentialColumns(colmap: Map<Field, Int>) {
        val missing = missingEssentialFields(colmap)
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "Sheet is missing required column(s): ${missing.joinToString(", ") { it.header }}"
            )
        }
    }

    /** Convert a 0-based column index to its A1 column letter (0 → A, 25 → Z, 26 → AA). */
    fun columnLetter(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, 'A' + (n % 26))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }
}
