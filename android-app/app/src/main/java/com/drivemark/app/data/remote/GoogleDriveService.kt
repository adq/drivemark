package com.drivemark.app.data.remote

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class DriveFile(
    val id: String,
    val name: String,
    val modifiedTime: String,
)

@Singleton
class GoogleDriveService @Inject constructor(
    private val authManager: GoogleAuthManager,
) {
    private fun buildService(): Drive {
        val credential = authManager.getAccountCredential()
            ?: throw IllegalStateException("Not authenticated")
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        ).setApplicationName("DriveMark").build()
    }

    suspend fun listSpreadsheets(): List<DriveFile> = withContext(Dispatchers.IO) {
        val result = buildService().files().list()
            .setQ("mimeType='application/vnd.google-apps.spreadsheet'")
            .setFields("files(id,name,modifiedTime)")
            .setOrderBy("modifiedTime desc")
            .setPageSize(50)
            .execute()
        result.files?.map { file ->
            DriveFile(
                id = file.id,
                name = file.name,
                modifiedTime = file.modifiedTime?.toStringRfc3339() ?: "",
            )
        } ?: emptyList()
    }

    suspend fun getModifiedTime(fileId: String): String = withContext(Dispatchers.IO) {
        val file = buildService().files().get(fileId)
            .setFields("modifiedTime")
            .execute()
        file.modifiedTime?.toStringRfc3339() ?: ""
    }
}
