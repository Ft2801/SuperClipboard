// src/main/java/com/superclipboard/service/PasteAccessibilityService.kt
package com.superclipboard.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.superclipboard.R
import com.superclipboard.SuperClipboardApp
import com.superclipboard.data.local.AppDatabase
import com.superclipboard.ui.MainActivity
import com.superclipboard.util.Constants
import kotlinx.coroutines.*

/**
 * Accessibility Service that handles the core text injection logic.
 *
 * HOW IT WORKS:
 * 1. When the floating UI triggers injection, this service finds the currently
 *    focused EditText in the active window.
 * 2. It reads the massive text from Room DB in chunks (using SQLite SUBSTR).
 * 3. It injects each chunk using AccessibilityNodeInfo.ACTION_SET_TEXT.
 * 4. Each new chunk appends to the previous text already in the field.
 * 5. This bypasses the 1MB Binder transaction limit because each individual
 *    ACTION_SET_TEXT call stays well under the limit.
 *
 * IMPORTANT: ACTION_SET_TEXT replaces all text in the field. To append,
 * we must build up the full text incrementally. For very large texts,
 * we use ACTION_SET_TEXT with the accumulated text up to the current chunk.
 * However, this means each SET_TEXT call sends increasingly more data.
 *
 * ALTERNATIVE APPROACH (used here for truly massive texts):
 * We use ClipboardManager to set each chunk and then perform ACTION_PASTE.
 * This way, each transaction is small and the text accumulates naturally.
 */
class PasteAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PasteAccessibility"

        // Singleton reference for the floating UI to communicate with
        @Volatile
        var instance: PasteAccessibilityService? = null
            private set

        /**
         * Check if the service is currently running and available.
         */
        fun isRunning(): Boolean = instance != null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val dao by lazy { AppDatabase.getInstance(applicationContext).massiveTextDao() }
    private var lastFocusedEditableNode: AccessibilityNodeInfo? = null

    // Currently running injection job (if any)
    private var injectionJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // Configure the service
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.DEFAULT
            notificationTimeout = 100
        }

        Log.i(TAG, "Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val source = event?.source ?: return

        if (source.isEditable && (source.isFocused || source.isFocused)) {
            updateLastFocusedEditableNode(source)
            return
        }

        val focused = source.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            updateLastFocusedEditableNode(focused)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted")
        cancelInjection()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        lastFocusedEditableNode?.recycle()
        lastFocusedEditableNode = null
        Log.i(TAG, "Accessibility Service destroyed")
    }

    private fun updateLastFocusedEditableNode(node: AccessibilityNodeInfo) {
        try {
            lastFocusedEditableNode?.recycle()
            lastFocusedEditableNode = AccessibilityNodeInfo.obtain(node)
        } catch (_: Exception) { }
    }

    /**
     * Find the currently focused editable node in the active window.
     * Traverses the accessibility tree to find an EditText or similar
     * editable view that has focus.
     */
    fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        // First, try to get the directly focused input node
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            updateLastFocusedEditableNode(focusedNode)
            return focusedNode
        }

        // Fallback: search the entire tree for an editable focused node
        val root = rootInActiveWindow ?: return null
        val fromTree = findEditableNode(root)
        if (fromTree != null) {
            updateLastFocusedEditableNode(fromTree)
            return fromTree
        }

        // Last fallback: reuse the most recent editable node captured from events
        val cached = lastFocusedEditableNode ?: return null
        return try {
            if (cached.refresh() && cached.isEditable) cached else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Recursively search the accessibility tree for an editable node.
     */
    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocused) {
            return node
        }

        // If this node is editable but not focused, remember it as a fallback
        if (node.isEditable && node.isVisibleToUser) {
            // Still try children first
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) return found
        }

        // As last resort, return any editable node
        if (node.isEditable && node.isVisibleToUser) {
            return node
        }

        return null
    }

    /**
     * PRIMARY INJECTION METHOD.
     * Injects massive text from Room DB into the focused EditText in chunks.
     *
     * Strategy:
     * - For texts under the chunk size: single ACTION_SET_TEXT
     * - For texts over the chunk size: use clipboard paste approach
     *   where each chunk is copied to clipboard and pasted via ACTION_PASTE
     *
     * @param textId The Room database ID of the text to inject
     * @param onProgress Callback for progress updates (0.0 to 1.0)
     * @param onComplete Callback when injection is finished
     * @param onError Callback when an error occurs
     */
    fun injectText(
        textId: Long,
        onProgress: (Float, String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        // Cancel any previous injection
        cancelInjection()

        injectionJob = serviceScope.launch {
            try {
                // Get total content length from DB (no large memory allocation)
                val totalLength = withContext(Dispatchers.IO) {
                    dao.getContentLength(textId)
                } ?: run {
                    onError("Text not found in database")
                    return@launch
                }

                if (totalLength <= 0) {
                    onError("Text is empty")
                    return@launch
                }

                // Find the target editable node
                val targetNode = findFocusedEditableNode()
                if (targetNode == null) {
                    onError("No editable text field found. Tap a text field in the target app first.")
                    return@launch
                }

                val chunkSize = Constants.INJECTION_CHUNK_SIZE
                val totalChunks = (totalLength + chunkSize - 1) / chunkSize

                // Check a sample of the text to detect if it's formatted (multiline, tabs, or indentation)
                // This determines the best injection strategy.
                val sampleContent = withContext(Dispatchers.IO) {
                    dao.getContentChunk(textId, 0, minOf(totalLength, 2000))
                } ?: ""
                
                // CRITICAL: Any text with tabs, newlines, or multiple consecutive spaces 
                // is considered "formatted" and MUST use the robust clipboard paste path.
                // ACTION_SET_TEXT is only safe for simple, non-formatted single lines.
                val isFormatted = sampleContent.contains("\n") || 
                                 sampleContent.contains("\r") || 
                                 sampleContent.contains("\t") ||
                                 sampleContent.contains("  ") // Detects simple space indentation

                Log.i(TAG, "Starting injection: $totalLength chars. Strategy: ${if (isFormatted) "Clipboard (Robust Formatting)" else "SetText (Fast)"}")
                
                showInjectionNotification("Starting injection...", 0)

                var offset = 0
                var chunkIndex = 0

                while (offset < totalLength && isActive) {
                    val currentChunkSize = minOf(chunkSize, totalLength - offset)
                    val chunk = withContext(Dispatchers.IO) {
                        dao.getContentChunk(textId, offset, currentChunkSize)
                    } ?: break

                    chunkIndex++
                    val progress = offset.toFloat() / totalLength
                    val statusMsg = "Injecting chunk $chunkIndex/$totalChunks (${(progress * 100).toInt()}%)"
                    
                    // Re-find the node in case it changed during the delay
                    val currentNode = findFocusedEditableNode() ?: targetNode

                    val success = if (isFormatted) {
                        // FOR FORMATTED: Always use the clipboard-based Paste action.
                        // This is the ONLY reliable way to preserve tabs and indentation.
                        injectViaClipboard(currentNode, chunk, offset == 0)
                    } else {
                        // FOR SIMPLE TEXT: Fast path via SetText, fallback to Paste.
                        injectViaSetText(currentNode, chunk) || injectViaClipboard(currentNode, chunk, offset == 0)
                    }

                    if (!success) {
                        Log.e(TAG, "Injection failed at chunk $chunkIndex")
                        onError("Target app rejected the injection. Make sure the text field is still focused.")
                        cancelInjectionNotification()
                        return@launch
                    }

                    onProgress(progress, statusMsg)
                    showInjectionNotification(statusMsg, (progress * 100).toInt())

                    offset += chunk.length
                    
                    // Small delay to let the target app process the input
                    // Formatted paste needs a bit more time to render indentation correctly
                    delay(if (isFormatted) 200 else Constants.INJECTION_CHUNK_DELAY_MS)
                }

                cancelInjectionNotification()
                onProgress(1f, "Injection Complete")
                onComplete()

                Log.i(TAG, "Injection complete: $totalLength chars in $chunkIndex chunks")

            } catch (e: CancellationException) {
                Log.i(TAG, "Injection cancelled")
                cancelInjectionNotification()
                onError("Injection cancelled")
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM during injection", e)
                cancelInjectionNotification()
                onError("Out of memory. The text is too large for the target app to handle.")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during injection", e)
                cancelInjectionNotification()
                onError("Error: ${e.message}")
            }
        }
    }

    /**
     * Helper to perform ACTION_SET_TEXT for single-line content.
     */
    private fun injectViaSetText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /**
     * Alternative injection method using the system clipboard.
     * Sets the chunk on the clipboard and performs ACTION_PASTE.
     * EXTREMELY ROBUST: This method is used when ACTION_SET_TEXT would collapse 
     * whitespace, as the target app's native paste handler is used instead.
     */
    private suspend fun injectViaClipboard(
        node: AccessibilityNodeInfo,
        text: String,
        clearFirst: Boolean
    ): Boolean {
        return try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("chunk", text)
            clipboard.setPrimaryClip(clip)

            // CRITICAL FIX: The ClipboardService is an IPC (Inter-Process) service.
            // If we trigger ACTION_PASTE immediately on the next clock cycle, the target
            // application might read the OLD clipboard contents (pre-sync).
            // We MUST wait for the IPC to propagate the new text.
            delay(100)

            if (clearFirst) {
                // Clear existing text first
                val selectArgs = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, Int.MAX_VALUE)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectArgs)
            } else {
                // Move cursor to end
                val currentText = node.text?.length ?: 0
                val selectArgs = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, currentText)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, currentText)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectArgs)
            }

            // Paste
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard paste failed", e)
            false
        }
    }

    /**
     * Cancel any currently running injection.
     */
    fun cancelInjection() {
        injectionJob?.cancel()
        injectionJob = null
        cancelInjectionNotification()
    }

    /**
     * Show a persistent notification with injection progress.
     */
    private fun showInjectionNotification(status: String, progress: Int) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, SuperClipboardApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clipboard)
            .setContentTitle("SuperClipboard - Injecting Text")
            .setContentText(status)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(SuperClipboardApp.NOTIFICATION_ID, notification)
    }

    /**
     * Cancel the injection progress notification.
     */
    private fun cancelInjectionNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(SuperClipboardApp.NOTIFICATION_ID)
    }
}