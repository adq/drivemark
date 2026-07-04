package com.drivemark.app.data.repository

import com.drivemark.app.data.local.PreferencesManager
import com.drivemark.app.data.local.SpreadsheetDao
import com.drivemark.app.data.local.SpreadsheetEntity
import com.drivemark.app.data.remote.GoogleDriveService
import com.drivemark.app.data.remote.GoogleSheetsService
import com.drivemark.app.domain.model.Spreadsheet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpreadsheetRepository @Inject constructor(
    private val spreadsheetDao: SpreadsheetDao,
    private val driveService: GoogleDriveService,
    private val sheetsService: GoogleSheetsService,
    private val prefsManager: PreferencesManager,
) {
    fun observeSpreadsheets(): Flow<List<Spreadsheet>> =
        spreadsheetDao.observeAll().map { entities ->
            entities.map { Spreadsheet(it.id, it.name, it.modifiedTime) }
        }

    suspend fun refreshSpreadsheets(): Result<List<Spreadsheet>> = try {
        val files = driveService.listSpreadsheets()
        val entities = files.map { SpreadsheetEntity(it.id, it.name, it.modifiedTime) }
        spreadsheetDao.deleteAll()
        spreadsheetDao.insertAll(entities)
        Result.success(files.map { Spreadsheet(it.id, it.name, it.modifiedTime) })
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun createSpreadsheet(title: String): Result<String> = try {
        val id = sheetsService.createSpreadsheet(title)
        selectSpreadsheet(id, title)
        Result.success(id)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun selectSpreadsheet(id: String, name: String) {
        prefsManager.setSelectedSpreadsheet(id, name)
    }

    suspend fun clearSelection() {
        prefsManager.clearSelectedSpreadsheet()
    }

    fun getSelectedSpreadsheetId(): Flow<String?> = prefsManager.selectedSpreadsheetId
    fun getSelectedSpreadsheetName(): Flow<String?> = prefsManager.selectedSpreadsheetName
}
