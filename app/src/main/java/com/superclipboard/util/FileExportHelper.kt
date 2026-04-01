// src/main/java/com/superclipboard/util/FileExportHelper.kt
package com.superclipboard.util

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter

/**
 * Helper for exporting text content to files using the Storage Access Framework.
 * Writes content using buffered streams to handle massive texts without OOM.
 */
object FileExportHelper {

    /**
     * Write text content to a URI obtained from SAF's ACTION_CREATE_DOCUMENT.
     * Uses buffered writing to handle massive texts efficiently.
     *
     * @param contentResolver System content resolver
     * @param uri Target file URI from SAF
     * @param content The text content to write
     * @return true if successful, false otherwise
     */
    suspend fun writeToUri(
        contentResolver: ContentResolver,
        uri: Uri,
        content: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val outputStream = contentResolver.openOutputStream(uri)
                ?: return@withContext false

            BufferedWriter(
                OutputStreamWriter(outputStream, Charsets.UTF_8),
                Constants.FILE_READ_BUFFER_SIZE
            ).use { writer ->
                // Write in chunks to avoid allocating a huge byte array
                val chunkSize = 64 * 1024 // 64KB chunks
                var offset = 0
                while (offset < content.length) {
                    val end = minOf(offset + chunkSize, content.length)
                    writer.write(content, offset, end - offset)
                    offset = end
                }
                writer.flush()
            }

            true
        } catch (e: Exception) {
            android.util.Log.e("FileExportHelper", "Error writing to URI", e)
            false
        }
    }

    /**
     * Get the MIME type for a given file extension.
     */
    fun getMimeType(extension: String): String {
        return Constants.EXTENSION_MIME_MAP[extension] ?: "text/plain"
    }

    /**
     * Suggest a filename with the given extension.
     */
    fun suggestFileName(title: String, extension: String): String {
        // Clean the title to create a valid filename
        val cleanTitle = title
            .replace(Regex("[^a-zA-Z0-9\\s\\-_]"), "")
            .replace(Regex("\\s+"), "_")
            .take(50) // Limit filename length
            .ifEmpty { "superclipboard_export" }

        return "$cleanTitle$extension"
    }
}