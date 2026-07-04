package com.drivemark.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spreadsheets")
data class SpreadsheetEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "modified_time") val modifiedTime: String,
)
