// src/main/java/com/superclipboard/ui/MainActivity.kt
package com.superclipboard.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.superclipboard.ui.screens.EditorScreen
import com.superclipboard.ui.screens.ExportDialog
import com.superclipboard.ui.screens.HomeScreen
import com.superclipboard.ui.screens.OnboardingScreen
import com.superclipboard.ui.theme.SuperClipboardTheme
import com.superclipboard.util.FileExportHelper
import kotlinx.coroutines.launch

/**
 * Main Activity serving as the single entry point for the app.
 * Hosts the Jetpack Compose UI with navigation between screens.
 * Handles intent filters for receiving shared text/files.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var pendingExportExtension: String = ".txt"
    private var pendingExportTitle: String = "export"
    private var pendingExportTextId: Long = -1

    private val exportFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri != null && pendingExportTextId > 0) {
            viewModel.exportToUri(contentResolver, uri, pendingExportTextId)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Notifications disabled. You won't see injection progress.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevents Compose from resizing the layout when the keyboard appears.
        // This eliminates the keyboard open/close lag caused by Compose
        // recalculating the entire layout on every keyboard animation frame.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)

        viewModel.checkOnboardingState()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        handleIncomingIntent(intent)

        setContent {
            SuperClipboardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SuperClipboardApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Handle incoming ACTION_SEND intents for text/files.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                    viewModel.importSharedText(text)
                    return
                }

                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }

                uri?.let {
                    viewModel.importFile(contentResolver, it)
                }
            }

            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    viewModel.importFile(contentResolver, uri)
                }
            }
        }
    }

    /**
     * Root composable that manages navigation between screens.
     */
    @Composable
    fun SuperClipboardApp() {
        val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
        val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
        val texts by viewModel.filteredTexts.collectAsStateWithLifecycle()
        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
        val editorTitle by viewModel.editorTitle.collectAsStateWithLifecycle()
        val editorContent by viewModel.editorContent.collectAsStateWithLifecycle()
        val editingText by viewModel.editingText.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val showExportDialog by viewModel.showExportDialog.collectAsStateWithLifecycle()
        val exportTextId by viewModel.exportTextId.collectAsStateWithLifecycle()
        val showDeleteDialog by viewModel.showDeleteDialog.collectAsStateWithLifecycle()
        val selectedTextIds by viewModel.selectedTextIds.collectAsStateWithLifecycle()

        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            viewModel.snackbarMessage.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }

        val effectiveScreen = if (!onboardingComplete) {
            MainViewModel.Screen.Onboarding
        } else {
            currentScreen
        }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = effectiveScreen,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "screen_transition",
                modifier = Modifier.padding(innerPadding)
            ) { screen ->
                when (screen) {
                    MainViewModel.Screen.Onboarding -> {
                        OnboardingScreen(
                            onComplete = { viewModel.completeOnboarding() }
                        )
                    }

                    MainViewModel.Screen.Home -> {
                        HomeScreen(
                            texts = texts,
                            selectedTextIds = selectedTextIds,
                            searchQuery = searchQuery,
                            isLoading = isLoading,
                            onSearchQueryChange = viewModel::updateSearch,
                            onCreateNew = {
                                viewModel.clearSelection()
                                viewModel.navigateToEditor()
                            },
                            onTextClick = { id -> viewModel.navigateToEditor(id) },
                            onToggleSelection = viewModel::toggleSelection,
                            onClearSelection = viewModel::clearSelection,
                            onSelectAllVisible = {
                                viewModel.selectAll(texts.map { it.id }.toSet())
                            },
                            onDeleteSelected = viewModel::deleteSelected,
                            onDeleteClick = { id -> viewModel.requestDelete(id) },
                            onShareClick = { id -> viewModel.shareText(id) },
                            onExportClick = { id -> viewModel.requestExport(id) },
                            onToggleFloating = { },
                            onPasteFromClipboard = { text ->
                                viewModel.saveText("Clipboard Paste", text)
                            },
                            showDeleteDialog = showDeleteDialog,
                            onConfirmDelete = { id -> viewModel.confirmDelete(id) },
                            onDismissDelete = { viewModel.dismissDeleteDialog() }
                        )
                    }

                    MainViewModel.Screen.Editor -> {
                        EditorScreen(
                            noteId = editingText?.id,
                            initialTitle = editorTitle,
                            initialContent = editorContent,
                            isEditing = editingText != null,
                            isLoading = isLoading,
                            onSave = { title, content ->
                                viewModel.saveText(title, content)
                            },
                            onBack = viewModel::navigateBack
                        )
                    }
                }
            }
        }

        // Export dialog
        if (showExportDialog && exportTextId != null) {
            ExportDialog(
                onDismiss = { viewModel.dismissExportDialog() },
                onExtensionSelected = { extension ->
                    pendingExportExtension = extension
                    pendingExportTextId = exportTextId!!

                    val text = texts.find { it.id == exportTextId }
                    pendingExportTitle = text?.title ?: "export"

                    val suggestedName = FileExportHelper.suggestFileName(
                        pendingExportTitle,
                        extension
                    )

                    try {
                        exportFileLauncher.launch(suggestedName)
                    } catch (e: Exception) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Could not open file picker: ${e.message}"
                            )
                        }
                    }

                    viewModel.dismissExportDialog()
                }
            )
        }
    }
}
