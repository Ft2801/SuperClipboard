// src/main/java/com/superclipboard/util/Constants.kt
package com.superclipboard.util

/**
 * Application-wide constants.
 */
object Constants {

    // Chunk size for text injection (characters per chunk)
    // 500K chars ≈ ~1MB in UTF-16 which is well under the Binder limit
    const val INJECTION_CHUNK_SIZE = 500_000

    // Delay between injection chunks (ms) to let the target app render
    const val INJECTION_CHUNK_DELAY_MS = 150L

    // Buffer size for streaming file reads (8KB)
    const val FILE_READ_BUFFER_SIZE = 8192

    // Maximum text length to share via plain Intent (before switching to FileProvider)
    const val MAX_INTENT_TEXT_LENGTH = 100_000

    // Supported export extensions
    val EXPORT_EXTENSIONS = listOf(
        ".txt", ".json", ".xml", ".csv", ".md", ".html", ".css",
        ".c", ".cpp", ".h", ".java", ".kt", ".js", ".ts",
        ".py", ".bat", ".ps1", ".sh", ".sql"
    )

    // MIME types mapped to extensions for SAF
    val EXTENSION_MIME_MAP = mapOf(
        ".txt" to "text/plain",
        ".json" to "application/json",
        ".xml" to "text/xml",
        ".csv" to "text/csv",
        ".md" to "text/markdown",
        ".html" to "text/html",
        ".css" to "text/css",
        ".c" to "text/x-csrc",
        ".cpp" to "text/x-c++src",
        ".h" to "text/x-chdr",
        ".java" to "text/x-java-source",
        ".kt" to "text/x-kotlin",
        ".js" to "application/javascript",
        ".ts" to "application/typescript",
        ".py" to "text/x-python",
        ".bat" to "application/x-bat",
        ".ps1" to "application/x-powershell",
        ".sh" to "application/x-sh",
        ".sql" to "application/sql"
    )

    // DataStore keys
    const val PREFS_NAME = "superclipboard_prefs"
    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
}