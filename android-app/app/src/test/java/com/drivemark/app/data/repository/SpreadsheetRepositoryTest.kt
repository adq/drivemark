package com.drivemark.app.data.repository

import com.drivemark.app.data.local.PreferencesManager
import com.drivemark.app.data.local.SpreadsheetDao
import com.drivemark.app.data.local.SpreadsheetEntity
import com.drivemark.app.data.remote.DriveFile
import com.drivemark.app.data.remote.GoogleDriveService
import com.drivemark.app.data.remote.GoogleSheetsService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpreadsheetRepositoryTest {

    private lateinit var spreadsheetDao: SpreadsheetDao
    private lateinit var driveService: GoogleDriveService
    private lateinit var sheetsService: GoogleSheetsService
    private lateinit var prefsManager: PreferencesManager
    private lateinit var repository: SpreadsheetRepository

    @Before
    fun setup() {
        spreadsheetDao = mockk(relaxed = true)
        driveService = mockk(relaxed = true)
        sheetsService = mockk(relaxed = true)
        prefsManager = mockk(relaxed = true)
        repository = SpreadsheetRepository(spreadsheetDao, driveService, sheetsService, prefsManager)
    }

    @Test
    fun `refreshSpreadsheets clears DB and inserts fresh from Drive`() = runTest {
        val driveFiles = listOf(
            DriveFile("id-1", "Sheet One", "2024-06-15T10:00:00Z"),
            DriveFile("id-2", "Sheet Two", "2024-06-14T10:00:00Z"),
        )
        coEvery { driveService.listSpreadsheets() } returns driveFiles

        val result = repository.refreshSpreadsheets()

        assertTrue(result.isSuccess)
        val spreadsheets = result.getOrThrow()
        assertEquals(2, spreadsheets.size)
        assertEquals("Sheet One", spreadsheets[0].name)

        coVerify { spreadsheetDao.deleteAll() }
        val slot = slot<List<SpreadsheetEntity>>()
        coVerify { spreadsheetDao.insertAll(capture(slot)) }
        assertEquals(2, slot.captured.size)
        assertEquals("id-1", slot.captured[0].id)
    }

    @Test
    fun `refreshSpreadsheets returns failure on exception`() = runTest {
        coEvery { driveService.listSpreadsheets() } throws RuntimeException("network error")

        val result = repository.refreshSpreadsheets()

        assertTrue(result.isFailure)
    }

    @Test
    fun `refreshSpreadsheets handles empty Drive result`() = runTest {
        coEvery { driveService.listSpreadsheets() } returns emptyList()

        val result = repository.refreshSpreadsheets()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
        coVerify { spreadsheetDao.deleteAll() }
        val slot = slot<List<SpreadsheetEntity>>()
        coVerify { spreadsheetDao.insertAll(capture(slot)) }
        assertEquals(0, slot.captured.size)
    }

    @Test
    fun `createSpreadsheet creates via service and auto-selects`() = runTest {
        coEvery { sheetsService.createSpreadsheet("My Sheet") } returns "new-id"

        val result = repository.createSpreadsheet("My Sheet")

        assertTrue(result.isSuccess)
        assertEquals("new-id", result.getOrThrow())
        coVerify { prefsManager.setSelectedSpreadsheet("new-id", "My Sheet") }
    }

    @Test
    fun `createSpreadsheet returns failure on exception`() = runTest {
        coEvery { sheetsService.createSpreadsheet(any()) } throws RuntimeException("fail")

        val result = repository.createSpreadsheet("My Sheet")

        assertTrue(result.isFailure)
    }

    @Test
    fun `selectSpreadsheet saves to preferences`() = runTest {
        repository.selectSpreadsheet("id-1", "Sheet One")

        coVerify { prefsManager.setSelectedSpreadsheet("id-1", "Sheet One") }
    }

    @Test
    fun `clearSelection clears preferences`() = runTest {
        repository.clearSelection()

        coVerify { prefsManager.clearSelectedSpreadsheet() }
    }

    @Test
    fun `observeSpreadsheets maps entities to domain models`() = runTest {
        val entities = listOf(
            SpreadsheetEntity("id-1", "Sheet One", "2024-06-15T10:00:00Z"),
        )
        coEvery { spreadsheetDao.observeAll() } returns flowOf(entities)

        val flow = repository.observeSpreadsheets()
        flow.collect { spreadsheets ->
            assertEquals(1, spreadsheets.size)
            assertEquals("id-1", spreadsheets[0].id)
            assertEquals("Sheet One", spreadsheets[0].name)
        }
    }
}
