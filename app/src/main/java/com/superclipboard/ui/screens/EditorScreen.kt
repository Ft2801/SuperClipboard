// src/main/java/com/superclipboard/ui/screens/EditorScreen.kt
package com.superclipboard.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Editor screen rewritten for maximum scroll and input performance.
 *
 * KEY ARCHITECTURAL DECISIONS:
 * - Title field uses AndroidView(EditText) instead of Compose OutlinedTextField
 *   to avoid recomposition on every keystroke.
 * - Content field uses AndroidView(EditText) wrapped in ScrollView for native smooth scrolling.
 * - Read mode uses AndroidView(TextView) wrapped in ScrollView for native smooth scrolling.
 * - NO Compose state holds the content string. Content lives only inside the native EditText.
 *   We read it out via a lambda (contentProvider) only when the user presses Save or Back.
 * - Only the character count integer is tracked in Compose state (lightweight recomposition).
 *
 * WHITESPACE PRESERVATION:
 * - Content EditText uses TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_MULTI_LINE to preserve newlines.
 * - TYPE_TEXT_FLAG_NO_SUGGESTIONS prevents autocorrect from modifying code.
 * - Tabs (\t) and all whitespace are preserved natively by EditText.
 * - Paste from clipboard preserves all formatting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    noteId: Long?,
    initialTitle: String,
    initialContent: String,
    isEditing: Boolean,
    isLoading: Boolean,
    onSave: (title: String, content: String) -> Unit,
    onBack: () -> Unit
) {
    // Minimal Compose state - only primitives/flags, never large strings
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var isReadMode by remember(noteId) { mutableStateOf(noteId != null) }
    var contentLength by remember(noteId) { mutableIntStateOf(initialContent.length) }

    // Lambdas to read current values from native views without holding them in Compose state
    var titleProvider by remember { mutableStateOf<(() -> String)?>(null) }
    var contentProvider by remember { mutableStateOf<(() -> String)?>(null) }

    // Lambda to programmatically insert text into the content EditText
    var contentInserter by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val ctx = LocalContext.current

    // Theme colors extracted once, not during recomposition of text fields
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val outlineColor = MaterialTheme.colorScheme.outline.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()

    val contentSizeText = remember(contentLength) {
        val bytes = contentLength.toLong()
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    // Helper to get current text values from native views
    fun getCurrentTitle(): String = titleProvider?.invoke() ?: initialTitle
    fun getCurrentContent(): String {
        return if (isReadMode) initialContent
        else contentProvider?.invoke() ?: initialContent
    }

    fun isDirty(): Boolean {
        return getCurrentTitle() != initialTitle || getCurrentContent() != initialContent
    }

    /**
     * Read text from the system clipboard, preserving all whitespace.
     * Uses item.text (plain text) first for perfect fidelity, falls back
     * to HTML conversion only when plain text is unavailable.
     */
    fun getClipboardText(): String? {
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val item = clip.getItemAt(0)

        // Use the shared helper that prioritizes plain text
        return com.superclipboard.util.FileImportHelper.extractTextFromClipItem(ctx, item)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isEditing) "Edit Note" else "New Note")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        (ctx as? android.app.Activity)?.currentFocus?.let {
                            imm.hideSoftInputFromWindow(it.windowToken, 0)
                        }

                        if (!isReadMode && isDirty()) {
                            showUnsavedDialog = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Read -> Edit toggle
                    if (isReadMode && isEditing) {
                        FilledTonalButton(
                            onClick = { isReadMode = false },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Paste from clipboard button (edit mode only)
                    if (!isReadMode) {
                        IconButton(onClick = {
                            val clipText = getClipboardText()
                            if (clipText != null) {
                                contentInserter?.invoke(clipText)
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste from Clipboard")
                        }
                    }

                    // Counter
                    Text(
                        text = "$contentLength chars | $contentSizeText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    // Save button
                    if (!isReadMode) {
                        FilledTonalButton(
                            onClick = {
                                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                (ctx as? android.app.Activity)?.currentFocus?.let {
                                    imm.hideSoftInputFromWindow(it.windowToken, 0)
                                }
                                val t = getCurrentTitle().ifBlank { "Untitled" }
                                val c = getCurrentContent()
                                onSave(t, c)
                            },
                            enabled = !isLoading && contentLength > 0,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->

        if (isReadMode) {
            // ============ READ MODE ============
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                factory = { ctx ->
                    ScrollView(ctx).apply {
                        isFillViewport = true
                        isVerticalScrollBarEnabled = true
                        isScrollbarFadingEnabled = true
                        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                        isSmoothScrollingEnabled = true

                        val container = LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            val pad = dpToPx(ctx, 16)
                            setPadding(pad, pad, pad, pad)
                        }

                        // Title
                        val titleView = TextView(ctx).apply {
                            id = android.R.id.title
                            textSize = 24f
                            setTextColor(onSurfaceColor)
                            typeface = Typeface.DEFAULT_BOLD
                            text = initialTitle.ifBlank { "Untitled" }
                            setPadding(0, 0, 0, dpToPx(ctx, 16))
                        }
                        container.addView(titleView)

                        // Content - preserves all whitespace
                        val contentView = TextView(ctx).apply {
                            id = android.R.id.text1
                            textSize = 15f
                            typeface = Typeface.MONOSPACE
                            setTextColor(onSurfaceColor)
                            setLineSpacing(0f, 1.35f)
                            setTextIsSelectable(true)
                            text = initialContent
                            // Ensure tabs are visible and newlines are respected
                            setHorizontallyScrolling(true) // Allow horizontal scroll for long lines/tabs
                        }
                        container.addView(contentView)

                        addView(container, FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        ))
                    }
                },
                update = { scrollView ->
                    val container = scrollView.getChildAt(0) as? LinearLayout ?: return@AndroidView
                    (container.getChildAt(0) as? TextView)?.setTextColor(onSurfaceColor)
                    (container.getChildAt(1) as? TextView)?.setTextColor(onSurfaceColor)
                }
            )
        } else {
            // ============ EDIT MODE ============
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                factory = { ctx ->
                    val scrollView = ScrollView(ctx).apply {
                        isFillViewport = true
                        isVerticalScrollBarEnabled = true
                        isScrollbarFadingEnabled = true
                        isSmoothScrollingEnabled = true
                        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                        isFocusable = true
                        isFocusableInTouchMode = true
                        setOnClickListener {
                            clearFocus()
                            requestFocus()
                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(windowToken, 0)
                        }
                    }

                    val container = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        val pad = dpToPx(ctx, 16)
                        setPadding(pad, pad, pad, pad)
                    }

                    // ---- Title EditText ----
                    val titleEdit = EditText(ctx).apply {
                        id = android.R.id.title
                        hint = "Title"
                        textSize = 18f
                        setTextColor(onSurfaceColor)
                        setHintTextColor(onSurfaceVariantColor)
                        typeface = Typeface.DEFAULT_BOLD
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or
                                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        imeOptions = EditorInfo.IME_ACTION_NEXT
                        maxLines = 1
                        isSingleLine = true
                        setBackgroundResource(0)
                        setPadding(dpToPx(ctx, 4), dpToPx(ctx, 12), dpToPx(ctx, 4), dpToPx(ctx, 12))

                        setText(initialTitle)
                        setSelection(text?.length ?: 0)

                        setOnFocusChangeListener { view, hasFocus ->
                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            if (!hasFocus) {
                                imm.hideSoftInputFromWindow(view.windowToken, 0)
                            }
                        }
                    }

                    titleProvider = { titleEdit.text?.toString() ?: "" }

                    container.addView(titleEdit, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ))

                    // Divider
                    val divider = View(ctx).apply {
                        setBackgroundColor(outlineColor)
                    }
                    container.addView(divider, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(ctx, 1)
                    ).apply {
                        topMargin = dpToPx(ctx, 4)
                        bottomMargin = dpToPx(ctx, 12)
                    })

                    // ---- Content EditText ----
                    val contentEdit = EditText(ctx).apply {
                        id = android.R.id.text1
                        hint = "Start typing your content here..."
                        textSize = 15f
                        setTextColor(onSurfaceColor)
                        setHintTextColor(onSurfaceVariantColor)
                        typeface = Typeface.MONOSPACE
                        setLineSpacing(0f, 1.35f)

                        // CRITICAL: Force multiline behavior
                        isSingleLine = false
                        maxLines = Int.MAX_VALUE
                        setHorizontallyScrolling(false)

                        // CRITICAL: inputType must ONLY use MULTI_LINE + NO_SUGGESTIONS.
                        // Do NOT use TYPE_TEXT_VARIATION_LONG_MESSAGE — on some OEM ROMs
                        // (Samsung, Xiaomi) it triggers autocomplete that collapses whitespace.
                        // Do NOT use TYPE_TEXT_FLAG_CAP_SENTENCES — it can interfere with
                        // code/indentation on some keyboards.
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                            android.text.InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE

                        // Do not let IME replace Enter with a done/send action
                        imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION

                        gravity = Gravity.TOP or Gravity.START
                        setBackgroundResource(0)
                        setPadding(dpToPx(ctx, 4), dpToPx(ctx, 4), dpToPx(ctx, 4), dpToPx(ctx, 4))

                        // System scrollbars for visual feedback
                        isVerticalScrollBarEnabled = true
                        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY

                        // Set initial content - preserves all \n, \t, \r\n
                        setText(initialContent)

                        // Track length for the counter
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                contentLength = s?.length ?: 0
                            }
                            override fun afterTextChanged(s: Editable?) = Unit
                        })
                    }

                    // Register content provider - reads text exactly as-is
                    contentProvider = { contentEdit.text?.toString() ?: "" }

                    // Register content inserter - inserts text at cursor position
                    // preserving all whitespace from the source
                    contentInserter = { textToInsert ->
                        val start = contentEdit.selectionStart.coerceAtLeast(0)
                        val end = contentEdit.selectionEnd.coerceAtLeast(0)
                        contentEdit.text?.replace(
                            minOf(start, end),
                            maxOf(start, end),
                            textToInsert
                        )
                    }

                    container.addView(contentEdit, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        val displayMetrics = ctx.resources.displayMetrics
                        val screenHeight = displayMetrics.heightPixels
                        val minH = screenHeight - dpToPx(ctx, 200)
                        this.height = LinearLayout.LayoutParams.WRAP_CONTENT
                        contentEdit.minHeight = maxOf(minH, dpToPx(ctx, 300))
                    })

                    // Hint text at bottom
                    val hintView = TextView(ctx).apply {
                        text = "Edit mode uses native text input for smooth performance."
                        textSize = 12f
                        setTextColor(onSurfaceVariantColor)
                        setPadding(dpToPx(ctx, 4), dpToPx(ctx, 16), dpToPx(ctx, 4), dpToPx(ctx, 16))
                    }
                    container.addView(hintView, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ))

                    scrollView.addView(container, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ))

                    scrollView
                },
                update = { scrollView ->
                    val container = scrollView.getChildAt(0) as? LinearLayout ?: return@AndroidView
                    for (i in 0 until container.childCount) {
                        when (val child = container.getChildAt(i)) {
                            is EditText -> {
                                child.setTextColor(onSurfaceColor)
                                child.setHintTextColor(onSurfaceVariantColor)
                            }
                            is TextView -> {
                                child.setTextColor(onSurfaceVariantColor)
                            }
                        }
                    }
                }
            )
        }
    }

    // Unsaved changes dialog
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Do you want to discard them?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    onBack()
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }
}

/** Convert dp to pixels */
private fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}