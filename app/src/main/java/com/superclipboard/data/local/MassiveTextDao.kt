// src/main/java/com/superclipboard/data/local/MassiveTextDao.kt
package com.superclipboard.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for MassiveText operations.
 * All operations use suspend functions (coroutine-backed) to prevent
 * blocking the main thread during large text I/O.
 */
@Dao
interface MassiveTextDao {

    /**
     * Observe all saved texts ordered by most recent first.
     * Returns a Flow for reactive UI updates.
     */
    @Query("SELECT id, title, timestamp, size_bytes, SUBSTR(content, 1, 200) AS content FROM massive_texts ORDER BY timestamp DESC")
    fun observeAllPreviews(): Flow<List<MassiveTextEntity>>

    /**
     * Get all texts with full content (use cautiously - only for specific operations)
     */
    @Query("SELECT * FROM massive_texts ORDER BY timestamp DESC")
    suspend fun getAllTexts(): List<MassiveTextEntity>

    /**
     * Retrieve a single text by ID with full content.
     */
    @Query("SELECT * FROM massive_texts WHERE id = :id")
    suspend fun getById(id: Long): MassiveTextEntity?

    /**
     * Get only the content of a text by ID.
     * This is crucial for injection - we only need the content string.
     */
    @Query("SELECT content FROM massive_texts WHERE id = :id")
    suspend fun getContentById(id: Long): String?

    /**
     * Get content in a specific range (for chunked reading if needed).
     * SQLite SUBSTR is 1-based.
     */
    @Query("SELECT SUBSTR(content, :start + 1, :length) FROM massive_texts WHERE id = :id")
    suspend fun getContentChunk(id: Long, start: Int, length: Int): String?

    /**
     * Get the total length of content for a specific text.
     */
    @Query("SELECT LENGTH(content) FROM massive_texts WHERE id = :id")
    suspend fun getContentLength(id: Long): Int?

    /**
     * Insert a new massive text entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MassiveTextEntity): Long

    /**
     * Update an existing text entry.
     */
    @Update
    suspend fun update(entity: MassiveTextEntity)

    /**
     * Delete a text entry.
     */
    @Delete
    suspend fun delete(entity: MassiveTextEntity)

    /**
     * Delete by ID.
     */
    @Query("DELETE FROM massive_texts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Get count of all texts.
     */
    @Query("SELECT COUNT(*) FROM massive_texts")
    suspend fun getCount(): Int

    /**
     * Search texts by title.
     */
    @Query("SELECT id, title, timestamp, size_bytes, SUBSTR(content, 1, 200) AS content FROM massive_texts WHERE title LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchByTitle(query: String): Flow<List<MassiveTextEntity>>
}