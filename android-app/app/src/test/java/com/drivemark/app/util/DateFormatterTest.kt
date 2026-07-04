package com.drivemark.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

class DateFormatterTest {

    @Test
    fun `nowIso returns parseable ISO instant`() {
        val result = DateFormatter.nowIso()
        val parsed = Instant.parse(result)
        assertNotNull(parsed)
    }

    @Test
    fun `formatForDisplay with valid ISO instant returns formatted date`() {
        val result = DateFormatter.formatForDisplay("2024-06-15T10:30:00Z")
        // Should not return the raw ISO string — it should be a localized date
        assert(result != "2024-06-15T10:30:00Z") { "Expected formatted date, got raw ISO: $result" }
        assert(result.isNotBlank()) { "Expected non-blank formatted date" }
    }

    @Test
    fun `formatForDisplay with invalid string returns input unchanged`() {
        assertEquals("not-a-date", DateFormatter.formatForDisplay("not-a-date"))
    }

    @Test
    fun `formatForDisplay with empty string returns empty`() {
        assertEquals("", DateFormatter.formatForDisplay(""))
    }

    @Test
    fun `formatForDisplay with partial ISO date returns input unchanged`() {
        assertEquals("2024-06-15", DateFormatter.formatForDisplay("2024-06-15"))
    }
}
