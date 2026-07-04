package com.drivemark.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object DateFormatter {
    fun nowIso(): String = Instant.now().toString()

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
