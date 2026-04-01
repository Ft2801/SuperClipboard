// src/main/java/com/superclipboard/domain/model/MassiveText.kt
package com.superclipboard.domain.model

/**
 * Domain model for UI representation.
 * Separates the database entity from the UI layer.
 */
data class MassiveText(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val sizeBytes: Long = 0
) {
    /** Human-readable file size */
    val formattedSize: String
        get() = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0))
        }

    /** Preview of the content (first 200 chars) */
    val preview: String
        get() = if (content.length > 200) content.take(200) + "…" else content
}

/**
 * Extension function to convert Entity to Domain model
 */
fun com.superclipboard.data.local.MassiveTextEntity.toDomain(): MassiveText =
    MassiveText(
        id = id,
        title = title,
        content = content,
        timestamp = timestamp,
        sizeBytes = sizeBytes
    )