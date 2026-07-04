package com.drivemark.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE spreadsheet_id = :sheetId ORDER BY date_added DESC")
    fun observeAll(sheetId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE spreadsheet_id = :sheetId ORDER BY date_added DESC")
    suspend fun getAll(sheetId: String): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun getById(id: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE spreadsheet_id = :sheetId AND url = :url LIMIT 1")
    suspend fun findByUrl(sheetId: String, url: String): BookmarkEntity?

    @Query("""
        SELECT * FROM bookmarks
        WHERE spreadsheet_id = :sheetId
        AND (title LIKE '%' || :query || '%'
             OR url LIKE '%' || :query || '%'
             OR folder LIKE '%' || :query || '%'
             OR notes LIKE '%' || :query || '%'
             OR excerpt LIKE '%' || :query || '%')
        ORDER BY date_added DESC
    """)
    fun search(sheetId: String, query: String): Flow<List<BookmarkEntity>>

    @Query("SELECT DISTINCT folder FROM bookmarks WHERE spreadsheet_id = :sheetId AND folder != '' ORDER BY folder")
    fun observeFolders(sheetId: String): Flow<List<String>>

    @Query("SELECT DISTINCT folder FROM bookmarks WHERE spreadsheet_id = :sheetId AND folder != '' ORDER BY folder")
    suspend fun getFolders(sheetId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bookmarks: List<BookmarkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM bookmarks WHERE spreadsheet_id = :sheetId")
    suspend fun deleteAllForSheet(sheetId: String)
}
