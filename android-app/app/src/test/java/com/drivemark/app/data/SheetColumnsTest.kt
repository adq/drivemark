package com.drivemark.app.data

import com.drivemark.app.data.SheetColumns.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetColumnsTest {

    @Test
    fun `resolveColumns maps canonical headers to A-I order`() {
        val map = SheetColumns.resolveColumns(SheetColumns.HEADERS)
        assertEquals(0, map[Field.URL])
        assertEquals(1, map[Field.TITLE])
        assertEquals(2, map[Field.FOLDER])
        assertEquals(3, map[Field.DATE_ADDED])
        assertEquals(4, map[Field.NOTES])
        assertEquals(5, map[Field.EXCERPT])
        assertEquals(6, map[Field.COVER])
        assertEquals(7, map[Field.ID])
        assertEquals(8, map[Field.MODIFIED])
    }

    @Test
    fun `resolveColumns handles reordered columns`() {
        val header = listOf("ID", "URL", "Modified", "Date Added", "Title", "Folder", "Notes", "Excerpt", "Cover")
        val map = SheetColumns.resolveColumns(header)
        assertEquals(0, map[Field.ID])
        assertEquals(1, map[Field.URL])
        assertEquals(2, map[Field.MODIFIED])
        assertEquals(3, map[Field.DATE_ADDED])
        assertEquals(4, map[Field.TITLE])
    }

    @Test
    fun `resolveColumns matches case- and whitespace-insensitively`() {
        val header = listOf("url", " Title ", "FOLDER", "date added", "NOTES", "excerpt", "Cover", "id", "MODIFIED")
        val map = SheetColumns.resolveColumns(header)
        assertEquals(0, map[Field.URL])
        assertEquals(1, map[Field.TITLE])
        assertEquals(2, map[Field.FOLDER])
        assertEquals(3, map[Field.DATE_ADDED])
        assertEquals(7, map[Field.ID])
        assertEquals(8, map[Field.MODIFIED])
    }

    @Test
    fun `resolveColumns marks absent headers as -1`() {
        val header = listOf("URL", "Title", "ID", "Date Added", "Modified")
        val map = SheetColumns.resolveColumns(header)
        assertEquals(-1, map[Field.FOLDER])
        assertEquals(-1, map[Field.NOTES])
        assertEquals(0, map[Field.URL])
        assertEquals(2, map[Field.ID])
    }

    @Test
    fun `resolveColumns handles empty header row`() {
        val map = SheetColumns.resolveColumns(emptyList())
        Field.values().forEach { assertEquals(-1, map[it]) }
    }

    @Test
    fun `requireEssentialColumns passes for canonical headers`() {
        SheetColumns.requireEssentialColumns(SheetColumns.resolveColumns(SheetColumns.HEADERS))
    }

    @Test
    fun `requireEssentialColumns passes when only display columns are absent`() {
        val header = listOf("URL", "ID", "Date Added", "Modified")
        SheetColumns.requireEssentialColumns(SheetColumns.resolveColumns(header))
    }

    @Test
    fun `requireEssentialColumns throws when an essential column is absent`() {
        val header = listOf("URL", "Title", "Date Added", "Modified") // no ID
        val ex = assertThrows(IllegalStateException::class.java) {
            SheetColumns.requireEssentialColumns(SheetColumns.resolveColumns(header))
        }
        assertTrue(ex.message!!.contains("ID"))
    }

    @Test
    fun `missingEssentialFields lists absent essentials`() {
        val header = listOf("Title", "Folder") // no url/id/dateAdded/modified
        val missing = SheetColumns.missingEssentialFields(SheetColumns.resolveColumns(header)).toSet()
        assertEquals(setOf(Field.URL, Field.DATE_ADDED, Field.ID, Field.MODIFIED), missing)
    }

    @Test
    fun `columnLetter maps indexes to A1 letters`() {
        assertEquals("A", SheetColumns.columnLetter(0))
        assertEquals("H", SheetColumns.columnLetter(7))
        assertEquals("Z", SheetColumns.columnLetter(25))
        assertEquals("AA", SheetColumns.columnLetter(26))
        assertEquals("AB", SheetColumns.columnLetter(27))
        assertEquals("AZ", SheetColumns.columnLetter(51))
    }
}
