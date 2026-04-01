// src/main/java/com/superclipboard/data/local/MassiveTextEntity.kt
package com.superclipboard.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a massive text entry.
 * SQLite handles large text blobs efficiently - the content column
 * can store texts of virtually unlimited size without OOM since
 * Room/SQLite uses cursors and doesn't load everything into RAM at once.
 */
@Entity(tableName = "massive_texts")
data class MassiveTextEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long = content.toByteArray(Charsets.UTF_8).size.toLong()
)