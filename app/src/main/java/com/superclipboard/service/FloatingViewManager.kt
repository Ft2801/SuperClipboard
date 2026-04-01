// src/main/java/com/superclipboard/service/FloatingViewManager.kt
package com.superclipboard.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.superclipboard.R
import com.superclipboard.data.local.AppDatabase
import com.superclipboard.data.local.MassiveTextEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Manages the floating overlay UI that appears on top of other apps.
 *
 * Components:
 * 1. A small floating action button (FAB) that can be dragged around the screen.
 * 2. A panel/list that opens when the FAB is tapped, showing saved texts.
 * 3. When a text is selected, it triggers the Accessibility Service to inject it.
 *
 * Uses WindowManager to add views directly to the system window layer.
 * This code uses XML layouts (not Compose) because WindowManager overlays
 * work more reliably with traditional View-based layouts.
 */
class FloatingViewManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FloatingViewManager"

        @Volatile
        private var INSTANCE: FloatingViewManager? = null

        fun getInstance(context: Context): FloatingViewManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FloatingViewManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val layoutInflater: LayoutInflater =
        LayoutInflater.from(context)

    private val dao = AppDatabase.getInstance(context).massiveTextDao()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Views
    private var floatingButton: View? = null
    private var floatingPanel: View? = null

    // State
    private var isButtonShowing = false
    private var isPanelShowing = false

    // Cached texts for the panel list
    private var cachedTexts: List<MassiveTextEntity> = emptyList()
    private var textsJob: Job? = null

    /**
     * Show the floating action button on screen.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun showFloatingButton() {
        if (isButtonShowing) return

        try {
            val view = layoutInflater.inflate(R.layout.floating_button, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 50
                y = 300
            }

            // Drag handling for the FAB
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isDragging = false

            view.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY

                        if (dx * dx + dy * dy > 25) { // 5px threshold
                            isDragging = true
                        }

                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (_: Exception) { }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // Click - toggle the panel
                            if (isPanelShowing) {
                                hidePanel()
                            } else {
                                showPanel()
                            }
                        }
                        true
                    }

                    else -> false
                }
            }

            windowManager.addView(view, params)
            floatingButton = view
            isButtonShowing = true

            // Start observing texts from DB
            startObservingTexts()

            Log.i(TAG, "Floating button shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing floating button", e)
        }
    }

    /**
     * Hide the floating action button.
     */
    fun hideFloatingButton() {
        hidePanel()
        stopObservingTexts()

        floatingButton?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) { }
        }
        floatingButton = null
        isButtonShowing = false
        Log.i(TAG, "Floating button hidden")
    }

    /**
     * Show the panel listing saved texts.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showPanel() {
        if (isPanelShowing) return

        try {
            val view = layoutInflater.inflate(R.layout.floating_panel, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                width = dpToPx(320)
                height = WindowManager.LayoutParams.WRAP_CONTENT
            }

            // Setup RecyclerView
            val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_saved_texts)
            val emptyState = view.findViewById<TextView>(R.id.tv_empty_state)
            val closeButton = view.findViewById<ImageView>(R.id.btn_close_panel)
            val progressContainer = view.findViewById<LinearLayout>(R.id.injection_progress_container)
            val progressBar = view.findViewById<ProgressBar>(R.id.progress_injection)
            val statusText = view.findViewById<TextView>(R.id.tv_injection_status)
            val detailText = view.findViewById<TextView>(R.id.tv_injection_detail)

            val adapter = FloatingTextAdapter(cachedTexts) { selectedText ->
                // User selected a text to inject
                onTextSelected(selectedText, progressContainer, progressBar, statusText, detailText)
            }

            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = adapter

            // Update visibility
            if (cachedTexts.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
            }

            // Close button
            closeButton.setOnClickListener { hidePanel() }

            // Touch outside to dismiss
            view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    hidePanel()
                    true
                } else false
            }

            windowManager.addView(view, params)
            floatingPanel = view
            isPanelShowing = true

            // Refresh the adapter with latest data
            refreshPanelData()

            Log.i(TAG, "Panel shown with ${cachedTexts.size} texts")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing panel", e)
        }
    }

    /**
     * Hide the floating panel.
     */
    private fun hidePanel() {
        floatingPanel?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) { }
        }
        floatingPanel = null
        isPanelShowing = false
    }

    /**
     * Handle text selection from the panel.
     * Triggers the Accessibility Service to inject the selected text.
     */
    private fun onTextSelected(
        text: MassiveTextEntity,
        progressContainer: LinearLayout,
        progressBar: ProgressBar,
        statusText: TextView,
        detailText: TextView
    ) {
        val service = PasteAccessibilityService.instance
        if (service == null) {
            Toast.makeText(
                context,
                "Accessibility Service not enabled. Please enable it in Settings.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Check if there's a focused editable field
        val targetNode = service.findFocusedEditableNode()
        if (targetNode == null) {
            Toast.makeText(
                context,
                "No text field focused. Tap a text field in the target app first, then try again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Show progress UI
        progressContainer.visibility = View.VISIBLE
        progressBar.progress = 0
        statusText.text = "Injecting: ${text.title}"
        detailText.text = "Starting..."

        // Start injection
        service.injectText(
            textId = text.id,
            onProgress = { progress, status ->
                scope.launch(Dispatchers.Main) {
                    progressBar.progress = (progress * 100).toInt()
                    detailText.text = status
                }
            },
            onComplete = {
                scope.launch(Dispatchers.Main) {
                    progressContainer.visibility = View.GONE
                    Toast.makeText(context, "✅ Text injected successfully!", Toast.LENGTH_SHORT).show()
                    hidePanel()
                }
            },
            onError = { error ->
                scope.launch(Dispatchers.Main) {
                    progressContainer.visibility = View.GONE
                    Toast.makeText(context, "❌ $error", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    /**
     * Start observing texts from the database.
     */
    private fun startObservingTexts() {
        textsJob?.cancel()
        textsJob = scope.launch {
            dao.observeAllPreviews().collectLatest { texts ->
                cachedTexts = texts
                refreshPanelData()
            }
        }
    }

    /**
     * Stop observing texts.
     */
    private fun stopObservingTexts() {
        textsJob?.cancel()
        textsJob = null
    }

    /**
     * Refresh the panel's RecyclerView with latest data.
     */
    private fun refreshPanelData() {
        val panel = floatingPanel ?: return
        val recyclerView = panel.findViewById<RecyclerView>(R.id.recycler_saved_texts) ?: return
        val emptyState = panel.findViewById<TextView>(R.id.tv_empty_state) ?: return

        val adapter = recyclerView.adapter as? FloatingTextAdapter ?: return
        adapter.updateData(cachedTexts)

        if (cachedTexts.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        }
    }

    /**
     * Check if the floating button is currently visible.
     */
    fun isShowing(): Boolean = isButtonShowing

    /**
     * Convert dp to pixels.
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /**
     * Clean up all resources.
     */
    fun destroy() {
        hideFloatingButton()
        scope.cancel()
        INSTANCE = null
    }

    // ==================== RecyclerView Adapter ====================

    /**
     * Adapter for the floating panel's text list.
     */
    private inner class FloatingTextAdapter(
        private var texts: List<MassiveTextEntity>,
        private val onItemClick: (MassiveTextEntity) -> Unit
    ) : RecyclerView.Adapter<FloatingTextAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tv_item_title)
            val preview: TextView = view.findViewById(R.id.tv_item_preview)
            val size: TextView = view.findViewById(R.id.tv_item_size)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_floating_text, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val text = texts[position]
            holder.title.text = text.title
            holder.preview.text = if (text.content.length > 100)
                text.content.take(100) + "…" else text.content

            val sizeStr = when {
                text.sizeBytes < 1024 -> "${text.sizeBytes} B"
                text.sizeBytes < 1024 * 1024 -> "${text.sizeBytes / 1024} KB"
                else -> String.format("%.2f MB", text.sizeBytes / (1024.0 * 1024.0))
            }
            holder.size.text = "Size: $sizeStr • Tap to inject"

            holder.itemView.setOnClickListener { onItemClick(text) }
        }

        override fun getItemCount(): Int = texts.size

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newTexts: List<MassiveTextEntity>) {
            texts = newTexts
            notifyDataSetChanged()
        }
    }
}