package com.drivemark.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        val SELECTED_SPREADSHEET_ID = stringPreferencesKey("selected_spreadsheet_id")
        val SELECTED_SPREADSHEET_NAME = stringPreferencesKey("selected_spreadsheet_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        val LAST_MODIFIED_TIME = stringPreferencesKey("last_modified_time")
    }

    val selectedSpreadsheetId: Flow<String?> = dataStore.data.map { it[SELECTED_SPREADSHEET_ID] }
    val selectedSpreadsheetName: Flow<String?> = dataStore.data.map { it[SELECTED_SPREADSHEET_NAME] }
    val userEmail: Flow<String?> = dataStore.data.map { it[USER_EMAIL] }
    val userDisplayName: Flow<String?> = dataStore.data.map { it[USER_DISPLAY_NAME] }

    suspend fun setSelectedSpreadsheet(id: String, name: String) {
        dataStore.edit {
            it[SELECTED_SPREADSHEET_ID] = id
            it[SELECTED_SPREADSHEET_NAME] = name
        }
    }

    suspend fun clearSelectedSpreadsheet() {
        dataStore.edit {
            it.remove(SELECTED_SPREADSHEET_ID)
            it.remove(SELECTED_SPREADSHEET_NAME)
            it.remove(LAST_MODIFIED_TIME)
        }
    }

    suspend fun setUserInfo(email: String, displayName: String) {
        dataStore.edit {
            it[USER_EMAIL] = email
            it[USER_DISPLAY_NAME] = displayName
        }
    }

    suspend fun setLastModifiedTime(time: String) {
        dataStore.edit { it[LAST_MODIFIED_TIME] = time }
    }

    suspend fun getLastModifiedTime(): String? {
        return dataStore.data.first()[LAST_MODIFIED_TIME]
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
