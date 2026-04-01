// src/main/java/com/superclipboard/util/ShareHelper.kt
package com.superclipboard.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * Helper for sharing text content to other apps.
 * For small texts, uses a plain text Intent.
 * For large texts, writes to a temp cache file and shares via FileProvider
 * to avoid TransactionTooLargeException on the Intent.
 */
object ShareHelper {

    /**
     * Share text content to other apps.
     * Automatically chooses between plain Intent and FileProvider based on text size.
     *
     * @param context Application context
     * @param title Title of the text
     * @param content Full text content
     */
    suspend fun shareText(context: Context, title: String, content: String) {
        if (content.length <= Constants.MAX_INTENT_TEXT_LENGTH) {
            // Small enough for a plain Intent
            shareViaIntent(context, title, content)
        } else {
            // Too large - share via FileProvider
            shareViaFile(context, title, content)
        }
    }

    /**
     * Share small text via plain ACTION_SEND Intent.
     */
    private fun shareViaIntent(context: Context, title: String, content: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /**
     * Share large text by writing to a temp file and sharing via FileProvider.
     * This bypasses the Binder transaction limit on Intents.
     */
    private suspend fun shareViaFile(context: Context, title: String, content: String) {
        withContext(Dispatchers.IO) {
            try {
                // Create the cache directory if it doesn't exist
                val shareDir = File(context.cacheDir, "shared_texts")
                shareDir.mkdirs()

                // Clean up old temp files
                shareDir.listFiles()?.forEach { it.delete() }

                // Write content to a temp file using buffered I/O
                val fileName = FileExportHelper.suggestFileName(title, ".txt")
                val file = File(shareDir, fileName)

                BufferedWriter(FileWriter(file)).use { writer ->
                    val chunkSize = 64 * 1024
                    var offset = 0
                    while (offset < content.length) {
                        val end = minOf(offset + chunkSize, content.length)
                        writer.write(content, offset, end - offset)
                        offset = end
                    }
                    writer.flush()
                }

                // Get a content URI via FileProvider
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                // Create share intent with the file URI
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(intent, "Share via").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            } catch (e: Exception) {
                android.util.Log.e("ShareHelper", "Error sharing via file", e)
            }
        }
    }
}