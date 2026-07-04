package com.drivemark.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookmarkEntity::class, SpreadsheetEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class DriveMarkDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun spreadsheetDao(): SpreadsheetDao
}
