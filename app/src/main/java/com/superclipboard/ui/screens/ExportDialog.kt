// src/main/java/com/superclipboard/ui/screens/ExportDialog.kt
package com.superclipboard.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.superclipboard.util.Constants

/**
 * Dialog for selecting the file extension when exporting a text.
 * Shows a grid of available extensions that the user can tap to select.
 */
@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onExtensionSelected: (String) -> Unit
) {
    var selectedExtension by remember { mutableStateOf(".txt") }
    var customExtension by remember { mutableStateOf("") }

    fun resolveExtension(): String {
        val normalizedCustom = customExtension.trim()
            .removePrefix(".")
            .lowercase()
            .replace(Regex("[^a-z0-9_-]"), "")
        if (normalizedCustom.isNotBlank()) {
            return ".${normalizedCustom.take(16)}"
        }
        return selectedExtension
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.FileDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Export as File", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Choose file extension:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = customExtension,
                    onValueChange = { customExtension = it },
                    label = { Text("Custom extension") },
                    placeholder = { Text("e.g.: log, cfg, conf") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("Leave empty to use one of the preset extensions below")
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Extension grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(Constants.EXPORT_EXTENSIONS) { ext ->
                        FilterChip(
                            selected = selectedExtension == ext && customExtension.isBlank(),
                            onClick = {
                                selectedExtension = ext
                                customExtension = "" // clear custom when preset is selected
                            },
                            label = {
                                Text(ext, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            },
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onExtensionSelected(resolveExtension()) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Export ${resolveExtension()}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
