package com.drivemark.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["url"]),
        Index(value = ["folder"]),
        Index(value = ["spreadsheet_id"]),
    ]
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "spreadsheet_id") val spreadsheetId: String,
    val url: String,
    val title: String,
    val folder: String,
    @ColumnInfo(name = "date_added") val dateAdded: String,
    val notes: String = "",
    val excerpt: String = "",
    @ColumnInfo(name = "cover_url") val coverUrl: String = "",
    val modified: String = "",
)
