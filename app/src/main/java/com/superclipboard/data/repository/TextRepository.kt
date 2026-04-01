// src/main/java/com/superclipboard/data/repository/TextRepository.kt
package com.superclipboard.data.repository

import com.superclipboard.data.local.AppDatabase
import com.superclipboard.data.local.MassiveTextEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository layer abstracting data access from the ViewModel.
 * All heavy I/O operations are dispatched to Dispatchers.IO.
 */
class TextRepository(private val database: AppDatabase) {

    private val dao = database.massiveTextDao()

    /** Observe all text previews reactively */
    fun observeAllPreviews(): Flow<List<MassiveTextEntity>> = dao.observeAllPreviews()

    /** Search texts by title */
    fun searchByTitle(query: String): Flow<List<MassiveTextEntity>> = dao.searchByTitle(query)

    /** Get a single text with full content */
    suspend fun getById(id: Long): MassiveTextEntity? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    /** Get only the content string */
    suspend fun getContentById(id: Long): String? = withContext(Dispatchers.IO) {
        dao.getContentById(id)
    }

    /** Get content chunk for chunked injection */
    suspend fun getContentChunk(id: Long, start: Int, length: Int): String? =
        withContext(Dispatchers.IO) {
            dao.getContentChunk(id, start, length)
        }

    /** Get total content length */
    suspend fun getContentLength(id: Long): Int? = withContext(Dispatchers.IO) {
        dao.getContentLength(id)
    }

    /** Insert a new text and return its ID */
    suspend fun insert(title: String, content: String): Long = withContext(Dispatchers.IO) {
        val entity = MassiveTextEntity(
            title = title,
            content = content,
            timestamp = System.currentTimeMillis(),
            sizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong()
        )
        dao.insert(entity)
    }

    /** Update an existing text */
    suspend fun update(id: Long, title: String, content: String) = withContext(Dispatchers.IO) {
        val entity = MassiveTextEntity(
            id = id,
            title = title,
            content = content,
            timestamp = System.currentTimeMillis(),
            sizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong()
        )
        dao.update(entity)
    }

    /** Delete a text by ID */
    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    /** Get count of all entries */
    suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        dao.getCount()
    }
}