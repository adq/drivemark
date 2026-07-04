package com.drivemark.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpreadsheetDao {
    @Query("SELECT * FROM spreadsheets ORDER BY modified_time DESC")
    fun observeAll(): Flow<List<SpreadsheetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sheets: List<SpreadsheetEntity>)

    @Query("DELETE FROM spreadsheets")
    suspend fun deleteAll()
}
