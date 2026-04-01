// src/main/java/com/superclipboard/util/FileImportHelper.kt
package com.superclipboard.util

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.superclipboard.data.repository.TextRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

/**
 * Helper class for importing text files into the Room database.
 *
 * CRITICAL: Uses raw character-buffer reading (NOT readLine()) to preserve
 * ALL whitespace: newlines (\n, \r\n, \r), tabs (\t), spaces, indentation.
 * readLine() strips line terminators which destroys code formatting.
 */
object FileImportHelper {

    /**
     * Import a text file from a content URI into the Room database.
     * Reads using a raw char buffer to preserve ALL whitespace exactly.
     */
    suspend fun importTextFile(
        contentResolver: ContentResolver,
        uri: Uri,
        repository: TextRepository,
        title: String
    ): Long = withContext(Dispatchers.IO) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
                ?: return@withContext -1L

            val builder = StringBuilder()

            // CRITICAL: Read with char buffer, NOT readLine().
            // readLine() strips \n, \r, \r\n which destroys code formatting.
            // Char buffer preserves EVERY character exactly as in the file.
            InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                val buffer = CharArray(Constants.FILE_READ_BUFFER_SIZE)
                var charsRead: Int
                while (reader.read(buffer).also { charsRead = it } != -1) {
                    builder.append(buffer, 0, charsRead)
                }
            }

            val content = builder.toString()
            repository.insert(title, content)
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("FileImportHelper", "OOM while importing file", e)
            -1L
        } catch (e: Exception) {
            android.util.Log.e("FileImportHelper", "Error importing file", e)
            -1L
        }
    }

    /**
     * Import plain text shared via ACTION_SEND intent.
     * Preserves all whitespace exactly as received.
     */
    suspend fun importSharedText(
        text: String,
        repository: TextRepository,
        title: String = "Shared Text"
    ): Long = withContext(Dispatchers.IO) {
        try {
            repository.insert(title, text)
        } catch (e: Exception) {
            android.util.Log.e("FileImportHelper", "Error importing shared text", e)
            -1L
        }
    }

    /**
     * Import text from the system clipboard.
     * Preserves all whitespace exactly as copied.
     */
    suspend fun importClipboardText(
        text: String,
        repository: TextRepository,
        title: String = "Clipboard"
    ): Long = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) return@withContext -1L
            repository.insert(title, text)
        } catch (e: Exception) {
            android.util.Log.e("FileImportHelper", "Error importing clipboard text", e)
            -1L
        }
    }

    /**
     * Extract a reasonable filename from a URI for the title.
     */
    fun extractFileName(contentResolver: ContentResolver, uri: Uri): String {
        var name = "Imported File"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex) ?: name
                }
            }
        } catch (_: Exception) { }
        return name
    }

        /**
     * Safely extract text from a ClipData.Item, preserving ALL whitespace.
     * Priority: plain text first (preserves exact formatting), HTML fallback only
     * when plain text is unavailable, coerceToText as last resort.
     */
    fun extractTextFromClipItem(context: Context, item: android.content.ClipData.Item): String? {
        // PRIORITY 1: Plain text — this is the EXACT text as copied, with all
        // newlines, tabs, spaces, indentation perfectly preserved.
        // This is what native Notes apps read.
        val text = item.text
        if (text != null && text.isNotEmpty()) return text.toString()

        // PRIORITY 2: If no plain text but HTML is available (e.g., web content
        // that only provided HTML), convert HTML to plain text preserving structure.
        val html = item.htmlText
        if (html != null) {
            try {
                val preProcessed = html
                    .replace(Regex("(?i)<br[^>]*>"), "\n")
                    .replace(Regex("(?i)</p>"), "\n\n")
                    .replace(Regex("(?i)</div>"), "\n")
                    .replace(Regex("(?i)</li>"), "\n")
                    .replace(Regex("(?i)<li[^>]*>"), " * ")
                    .replace(Regex("(?i)</h[1-6]>"), "\n\n")
                    .replace(Regex("(?i)<tr[^>]*>"), "\n")
                    .replace(Regex("(?i)</td>|</th>"), "\t")
                val stripped = preProcessed.replace(Regex("<[^>]*>"), "")
                // Decode HTML entities (&amp; &lt; &nbsp; etc.)
                val decoded = android.text.Html.fromHtml(
                    stripped, android.text.Html.FROM_HTML_MODE_LEGACY
                ).toString()
                if (decoded.isNotBlank()) return decoded
            } catch (_: Exception) { }
        }

        // PRIORITY 3: Absolute fallback — may lose some formatting
        return try {
            item.coerceToText(context)?.toString()
        } catch (_: Exception) {
            null
        }
    }
}