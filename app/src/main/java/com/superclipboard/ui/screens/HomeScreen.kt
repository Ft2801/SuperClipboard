// src/main/java/com/superclipboard/ui/screens/HomeScreen.kt
package com.superclipboard.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.superclipboard.domain.model.MassiveText
import com.superclipboard.service.FloatingViewManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    texts: List<MassiveText>,
    selectedTextIds: Set<Long>,
    searchQuery: String,
    isLoading: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onCreateNew: () -> Unit,
    onTextClick: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAllVisible: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDeleteClick: (Long) -> Unit,
    onShareClick: (Long) -> Unit,
    onExportClick: (Long) -> Unit,
    onToggleFloating: () -> Unit,
    onPasteFromClipboard: ((String) -> Unit)? = null,
    showDeleteDialog: Long?,
    onConfirmDelete: (Long) -> Unit,
    onDismissDelete: () -> Unit
) {
    val context = LocalContext.current
    var isSearchActive by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    val isSelectionMode = selectedTextIds.isNotEmpty()

    var isFloatingEnabled by remember {
        mutableStateOf(FloatingViewManager.getInstance(context).isShowing())
    }

    // Colors for native EditText
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedTextIds.size} selected")
                    } else if (isSearchActive) {
                        // NATIVE SEARCH BAR - no Compose TextField lag
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            factory = { ctx ->
                                EditText(ctx).apply {
                                    id = android.R.id.inputExtractEditText
                                    hint = "Search notes..."
                                    textSize = 16f
                                    setTextColor(onSurfaceColor)
                                    setHintTextColor(hintColor)
                                    isSingleLine = true
                                    maxLines = 1
                                    imeOptions = EditorInfo.IME_ACTION_SEARCH
                                    inputType = android.text.InputType.TYPE_CLASS_TEXT
                                    setBackgroundResource(0)
                                    setPadding(
                                        dpToPx(ctx, 8), dpToPx(ctx, 4),
                                        dpToPx(ctx, 8), dpToPx(ctx, 4)
                                    )

                                    // Set initial value
                                    setText(searchQuery)
                                    setSelection(text?.length ?: 0)

                                    // Debounce via Handler - no coroutines needed
                                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                                    var pendingSearch: Runnable? = null

                                    addTextChangedListener(object : TextWatcher {
                                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                            pendingSearch?.let { handler.removeCallbacks(it) }
                                            val query = s?.toString() ?: ""
                                            val runnable = Runnable { onSearchQueryChange(query) }
                                            pendingSearch = runnable
                                            handler.postDelayed(runnable, 300L)
                                        }
                                        override fun afterTextChanged(s: Editable?) = Unit
                                    })

                                    // Search action on keyboard
                                    setOnEditorActionListener { _, actionId, _ ->
                                        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                                            pendingSearch?.let { handler.removeCallbacks(it) }
                                            onSearchQueryChange(text?.toString() ?: "")
                                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                            imm.hideSoftInputFromWindow(windowToken, 0)
                                            clearFocus()
                                            true
                                        } else false
                                    }

                                    // Auto-focus and show keyboard
                                    // Auto-focus with delay to ensure the view is fully attached before requesting keyboard
                                    postDelayed({
                                        if (isAttachedToWindow) {
                                            requestFocus()
                                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                                        }
                                    }, 100L)

                                    // Hide keyboard when focus is lost
                                    setOnFocusChangeListener { view, hasFocus ->
                                        if (!hasFocus) {
                                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                            imm.hideSoftInputFromWindow(view.windowToken, 0)
                                        }
                                    }
                                }
                            },
                            update = { editText ->
                                editText.setTextColor(onSurfaceColor)
                                editText.setHintTextColor(hintColor)
                            }
                        )
                    } else {
                        Column {
                            Text("SuperClipboard", fontWeight = FontWeight.Bold)
                            Text(
                                "${texts.size} saved texts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = onSelectAllVisible) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(onClick = { showBulkDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        return@TopAppBar
                    }

                    // Paste from clipboard button
                    if (onPasteFromClipboard != null) {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val item = clip.getItemAt(0)
                                // Use shared helper — prioritizes item.text for perfect fidelity
                                val clipText = com.superclipboard.util.FileImportHelper.extractTextFromClipItem(context, item)
                                if (!clipText.isNullOrBlank()) {
                                    onPasteFromClipboard(clipText)
                                }
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Save clipboard content")
                        }
                    }

                    // Search toggle
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) {
                            onSearchQueryChange("")
                            // Hide keyboard when closing search
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(
                                (context as? android.app.Activity)?.currentFocus?.windowToken,
                                0
                            )
                        }
                    }) {
                        Icon(
                            if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }

                    // Floating button toggle
                    IconButton(onClick = {
                        if (Settings.canDrawOverlays(context)) {
                            val manager = FloatingViewManager.getInstance(context)
                            if (manager.isShowing()) {
                                manager.hideFloatingButton()
                                isFloatingEnabled = false
                            } else {
                                manager.showFloatingButton()
                                isFloatingEnabled = true
                            }
                        } else {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    }) {
                        Icon(
                            if (isFloatingEnabled) Icons.Default.ToggleOn else Icons.Outlined.ToggleOff,
                            contentDescription = "Toggle Floating Button",
                            tint = if (isFloatingEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onCreateNew,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New Note") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (texts.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No saved texts yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create a new note, paste text, or share a file to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                // Note list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = 8.dp, bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = texts,
                        key = { it.id }
                    ) { text ->
                        NoteCard(
                            text = text,
                            isSelectionMode = isSelectionMode,
                            isSelected = text.id in selectedTextIds,
                            onClick = {
                                if (isSelectionMode) onToggleSelection(text.id)
                                else onTextClick(text.id)
                            },
                            onLongPress = { onToggleSelection(text.id) },
                            onDelete = { onDeleteClick(text.id) },
                            onShare = { onShareClick(text.id) },
                            onExport = { onExportClick(text.id) }
                        )
                    }
                }
            }
        }
    }

    // Single delete confirmation
    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete Text") },
            text = { Text("Are you sure you want to delete this text? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { onConfirmDelete(showDeleteDialog) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) { Text("Cancel") }
            }
        )
    }

    // Bulk delete confirmation
    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text("Delete Selected Notes") },
            text = { Text("Delete ${selectedTextIds.size} selected notes?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkDeleteDialog = false
                        onDeleteSelected()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Card displaying a single note with action buttons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    text: MassiveText,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Selection checkbox
            if (isSelectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                }
            }

            // Title
            Text(
                text = text.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Preview
            Text(
                text = text.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Size and date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = { },
                        label = { Text(text.formattedSize, style = MaterialTheme.typography.labelSmall) },
                        icon = {
                            Icon(Icons.Outlined.Storage, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        modifier = Modifier.height(28.dp)
                    )
                    Text(
                        text = dateFormat.format(Date(text.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Action buttons (hidden in selection mode)
                if (!isSelectionMode) {
                    Row {
                        IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onExport, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = "Export", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Outlined.Delete, contentDescription = "Delete",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Convert dp to pixels */
private fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}

                