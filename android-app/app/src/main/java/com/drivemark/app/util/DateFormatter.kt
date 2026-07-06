package com.drivemark.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object DateFormatter {
    // Always emit exactly 3 fractional digits + 'Z', byte-identical to JS Date.toISOString().
    // Instant.toString()/ISO_INSTANT drop the fraction when millis are zero, which breaks the
    // lexical timestamp comparison shared with the Chrome extension across the same sheet.
    private val ISO_MILLIS = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)

    fun nowIso(): String = ISO_MILLIS.format(Instant.now())

    /**
     * Parse an ISO-8601 instant to epoch millis for ordering. Unparseable/blank → Long.MIN_VALUE
     * (treated as oldest). Compare parsed instants rather than raw strings so mixed-precision
     * timestamps (legacy zero-millis rows vs 3-digit rows) order correctly.
     */
    fun toEpochMillis(iso: String): Long = try {
        Instant.parse(iso).toEpochMilli()
    } catch (_: Exception) {
        Long.MIN_VALUE
    }

    fun formatForDisplay(isoDate: String): String {
        return try {
            val instant = Instant.parse(isoDate)
            val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (_: Exception) {
            isoDate
        }
    }
}
