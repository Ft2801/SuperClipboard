// src/main/java/com/superclipboard/ui/MainViewModel.kt
package com.superclipboard.ui

import android.app.Application
import android.util.Log
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.superclipboard.SuperClipboardApp
import com.superclipboard.data.repository.TextRepository
import com.superclipboard.domain.model.MassiveText
import com.superclipboard.domain.model.toDomain
import com.superclipboard.util.FileExportHelper
import com.superclipboard.util.FileImportHelper
import com.superclipboard.util.ShareHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview

/**
 * ViewModel for the main application UI.
 * Manages state for the notes list, editor, and export operations.
 *
 * WHITESPACE PRESERVATION:
 * All text operations preserve whitespace exactly as provided.
 * No trimming, normalizing, or stripping of \n, \r\n, \t, or spaces
 * is performed at any point in the data flow.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TextRepository(
        (application as SuperClipboardApp).database
    )

    // ==================== UI State ====================

    /** Current navigation screen */
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    /** All text previews */
    val allTexts: StateFlow<List<MassiveText>> = repository.observeAllPreviews()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Search query */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Filtered texts based on search */
    @OptIn(FlowPreview::class)
    val filteredTexts: StateFlow<List<MassiveText>> = _searchQuery
        .debounce(300L)
        .combine(allTexts) { query, texts ->
            if (query.isBlank()) texts
            else texts.filter { it.title.contains(query, ignoreCase = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Currently editing text */
    private val _editingText = MutableStateFlow<MassiveText?>(null)
    val editingText: StateFlow<MassiveText?> = _editingText.asStateFlow()

    /** Editor title */
    private val _editorTitle = MutableStateFlow("")
    val editorTitle: StateFlow<String> = _editorTitle.asStateFlow()

    /** Editor content */
    private val _editorContent = MutableStateFlow("")
    val editorContent: StateFlow<String> = _editorContent.asStateFlow()

    /** Loading state */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Snackbar messages */
    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    /** Export dialog visibility */
    private val _showExportDialog = MutableStateFlow(false)
    val showExportDialog: StateFlow<Boolean> = _showExportDialog.asStateFlow()

    /** ID of text to export */
    private val _exportTextId = MutableStateFlow<Long?>(null)
    val exportTextId: StateFlow<Long?> = _exportTextId.asStateFlow()

    /** Delete confirmation dialog */
    private val _showDeleteDialog = MutableStateFlow<Long?>(null)
    val showDeleteDialog: StateFlow<Long?> = _showDeleteDialog.asStateFlow()

    /** Selected notes for bulk actions */
    private val _selectedTextIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTextIds: StateFlow<Set<Long>> = _selectedTextIds.asStateFlow()

    /** Onboarding completed state */
    private val _onboardingComplete = MutableStateFlow(false)
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete.asStateFlow()

    // ==================== Navigation ====================

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun navigateToEditor(textId: Long? = null) {
        viewModelScope.launch {
            if (textId != null) {
                _isLoading.value = true
                val text = repository.getById(textId)
                if (text != null) {
                    _editingText.value = text.toDomain()
                    _editorTitle.value = text.title
                    _editorContent.value = text.content
                } else {
                    _snackbarMessage.emit("Text not found")
                    return@launch
                }
                _isLoading.value = false
            } else {
                _editingText.value = null
                _editorTitle.value = ""
                _editorContent.value = ""
            }
            _currentScreen.value = Screen.Editor
        }
    }

    fun navigateBack() {
        _currentScreen.value = Screen.Home
    }

    // ==================== Multi Select ====================

    fun toggleSelection(id: Long) {
        _selectedTextIds.update { current ->
            if (id in current) current - id else current + id
        }
    }

    fun clearSelection() {
        _selectedTextIds.value = emptySet()
    }

    fun selectAll(ids: Set<Long>) {
        _selectedTextIds.value = ids
    }

    // ==================== Editor Operations ====================

    fun updateEditorTitle(title: String) {
        _editorTitle.value = title
    }

    fun updateEditorContent(content: String) {
        _editorContent.value = content
    }

    /**
     * Save the current editor state to Room.
     * CRITICAL: The content parameter is passed EXACTLY as received
     * from the EditText, preserving all whitespace (\n, \t, \r\n, spaces).
     * No trimming or normalization is performed on the content.
     */
    fun saveText(
        titleInput: String = _editorTitle.value,
        contentInput: String = _editorContent.value
    ) {
        viewModelScope.launch {
            val title = titleInput.ifBlank { "Untitled" }
            // CRITICAL: content is stored EXACTLY as received — no trim, no replace.
            val content = contentInput

            // Debug logging
            val lineCount = content.count { it == '\n' } + 1
            val tabCount = content.count { it == '\t' }
            Log.i("MainViewModel", "Saving: ${content.length} chars, $lineCount lines, $tabCount tabs")
            if (content.length < 500) {
                Log.d("MainViewModel", "FULL CONTENT: [${content.replace("\n", "⏎").replace("\t", "⇥")}]")
            } else {
                Log.d("MainViewModel", "FIRST 200: [${content.take(200).replace("\n", "⏎").replace("\t", "⇥")}]")
            }

            if (content.isEmpty()) {
                _snackbarMessage.emit("Content cannot be empty")
                return@launch
            }

            _isLoading.value = true
            try {
                val existing = _editingText.value
                if (existing != null && existing.id > 0) {
                    repository.update(existing.id, title, content)
                    _snackbarMessage.emit("Text updated successfully")
                } else {
                    repository.insert(title, content)
                    _snackbarMessage.emit("Text saved successfully")
                }
                navigateBack()
            } catch (e: Exception) {
                _snackbarMessage.emit("Error saving text: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ==================== Delete Operations ====================

    fun requestDelete(id: Long) {
        _showDeleteDialog.value = id
    }

    fun dismissDeleteDialog() {
        _showDeleteDialog.value = null
    }

    fun confirmDelete(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteById(id)
                _selectedTextIds.update { it - id }
                _snackbarMessage.emit("Text deleted")
            } catch (e: Exception) {
                _snackbarMessage.emit("Error deleting: ${e.message}")
            }
            _showDeleteDialog.value = null
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val selected = _selectedTextIds.value
            if (selected.isEmpty()) return@launch

            _isLoading.value = true
            try {
                selected.forEach { id ->
                    repository.deleteById(id)
                }
                _selectedTextIds.value = emptySet()
                _snackbarMessage.emit("${selected.size} notes deleted")
            } catch (e: Exception) {
                _snackbarMessage.emit("Bulk delete error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ==================== Search ====================

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    // ==================== Import ====================

    /** Import a file from a content URI */
    fun importFile(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val title = FileImportHelper.extractFileName(contentResolver, uri)
                val id = FileImportHelper.importTextFile(contentResolver, uri, repository, title)
                if (id > 0) {
                    _snackbarMessage.emit("File imported successfully")
                } else {
                    _snackbarMessage.emit("Failed to import file - it may be too large for available memory")
                }
            } catch (e: Exception) {
                _snackbarMessage.emit("Import error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Import plain text shared via Intent - preserves all whitespace */
    fun importSharedText(text: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Generate title from first non-empty line, preserving the actual content
                val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.take(30) ?: "Shared"
                val title = "Shared: $firstLine..."
                val id = FileImportHelper.importSharedText(text, repository, title)
                if (id > 0) {
                    _snackbarMessage.emit("Shared text saved")
                } else {
                    _snackbarMessage.emit("Failed to save shared text")
                }
            } catch (e: Exception) {
                _snackbarMessage.emit("Error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Import text directly from the system clipboard.
     * Called when user taps "Paste from Clipboard" button.
     * Preserves ALL whitespace: newlines, tabs, indentation.
     */
    fun importFromClipboard(clipboardText: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (clipboardText.isBlank()) {
                    _snackbarMessage.emit("Clipboard is empty")
                    return@launch
                }
                val firstLine = clipboardText.lineSequence().firstOrNull { it.isNotBlank() }?.take(30) ?: "Clipboard"
                val title = "Clipboard: $firstLine..."
                val id = FileImportHelper.importClipboardText(clipboardText, repository, title)
                if (id > 0) {
                    _snackbarMessage.emit("Clipboard text saved")
                } else {
                    _snackbarMessage.emit("Failed to save clipboard text")
                }
            } catch (e: Exception) {
                _snackbarMessage.emit("Error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ==================== Export ====================

    fun requestExport(id: Long) {
        _exportTextId.value = id
        _showExportDialog.value = true
    }

    fun dismissExportDialog() {
        _showExportDialog.value = false
        _exportTextId.value = null
    }

    /** Write content to a URI from SAF */
    fun exportToUri(contentResolver: ContentResolver, uri: Uri, textId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val content = repository.getContentById(textId)
                if (content != null) {
                    val success = FileExportHelper.writeToUri(contentResolver, uri, content)
                    if (success) {
                        _snackbarMessage.emit("File exported successfully")
                    } else {
                        _snackbarMessage.emit("Failed to export file")
                    }
                } else {
                    _snackbarMessage.emit("Text not found")
                }
            } catch (e: Exception) {
                _snackbarMessage.emit("Export error: ${e.message}")
            } finally {
                _isLoading.value = false
                dismissExportDialog()
            }
        }
    }

    // ==================== Share ====================

    fun shareText(id: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val text = repository.getById(id)
                if (text != null) {
                    ShareHelper.shareText(
                        getApplication(),
                        text.title,
                        text.content
                    )
                } else {
                    _snackbarMessage.emit("Text not found")
                }
            } catch (e: Exception) {
                _snackbarMessage.emit("Share error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ==================== Onboarding ====================

    fun completeOnboarding() {
        _onboardingComplete.value = true
        viewModelScope.launch {
            val context = getApplication<SuperClipboardApp>()
            val prefs = context.getSharedPreferences(
                com.superclipboard.util.Constants.PREFS_NAME,
                android.content.Context.MODE_PRIVATE
            )
            prefs.edit().putBoolean(
                com.superclipboard.util.Constants.KEY_ONBOARDING_COMPLETE,
                true
            ).apply()
        }
    }

    fun checkOnboardingState() {
        val context = getApplication<SuperClipboardApp>()
        val prefs = context.getSharedPreferences(
            com.superclipboard.util.Constants.PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )
        _onboardingComplete.value = prefs.getBoolean(
            com.superclipboard.util.Constants.KEY_ONBOARDING_COMPLETE,
            false
        )
    }

    /** Sealed class representing navigation destinations */
    sealed class Screen {
        data object Home : Screen()
        data object Editor : Screen()
        data object Onboarding : Screen()
    }
}