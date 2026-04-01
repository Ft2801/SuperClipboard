// src/main/java/com/superclipboard/ui/screens/OnboardingScreen.kt
package com.superclipboard.ui.screens

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superclipboard.service.PasteAccessibilityService

/**
 * Onboarding screen redesigned to fit entirely on screen without scrolling.
 * The "Continue" button is always visible at the bottom.
 *
 * Layout structure:
 *   - Top: branding (compact)
 *   - Middle: permission cards (compact, no scroll needed)
 *   - Bottom: pinned Continue button (always visible)
 *
 * Accessibility fix: uses Settings.ACTION_ACCESSIBILITY_SETTINGS with a
 * direct component name extra where supported, falling back to the generic
 * accessibility settings page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    // Poll permission states every second while this screen is visible
    LaunchedEffect(Unit) {
        while (true) {
            overlayGranted = Settings.canDrawOverlays(context)
            accessibilityEnabled = isAccessibilityServiceEnabled(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    val allGranted = overlayGranted && accessibilityEnabled

    // Root: Column with pinned bottom button
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        // ── TOP: Branding ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SuperClipboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Two permissions required to unlock all features",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // ── MIDDLE: Permission cards ───────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Permission 1: Overlay
            CompactPermissionCard(
                number = 1,
                icon = Icons.Default.Layers,
                title = "Draw Over Other Apps",
                description = "Shows the floating paste button on top of any app.",
                isGranted = overlayGranted,
                onAction = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            )

            // Permission 2: Accessibility
            CompactPermissionCard(
                number = 2,
                icon = Icons.Default.Accessibility,
                title = "Accessibility Service",
                description = "Injects text into other apps without clipboard limits.",
                isGranted = accessibilityEnabled,
                onAction = {
                    openAccessibilitySettings(context)
                }
            )

            // Android 13+ notification (optional, shown inline, no card)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Notification permission will be requested separately (optional).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // ── BOTTOM: Continue button (always visible) ───────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (allGranted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(
                    text = if (allGranted) "Get Started" else "Continue Without All Permissions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!allGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Some features will not work until permissions are granted. " +
                            "You can enable them later from the app settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Compact permission card that fits two cards + button on a single screen
 * without requiring any scrolling.
 */
@Composable
fun CompactPermissionCard(
    number: Int,
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Action button
            if (!isGranted) {
                FilledTonalButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Enable", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * Opens the Accessibility Settings page.
 *
 * Strategy:
 * 1. Try to open the exact accessibility settings page for this app
 *    using a direct component Intent (works on stock Android).
 * 2. If that fails (some OEM skins block it), fall back to the generic
 *    ACTION_ACCESSIBILITY_SETTINGS page.
 *
 * The direct component approach avoids the issue where the generic page
 * opens but the user cannot find SuperClipboard in the list.
 */
fun openAccessibilitySettings(context: Context) {
    // Attempt 1: direct deep link to this app's accessibility entry (Android 9+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val componentName =
                    "${context.packageName}/${PasteAccessibilityService::class.java.name}"
                putExtra(":settings:fragment_args_key", componentName)
                putExtra(
                    ":settings:show_fragment_args",
                    android.os.Bundle().also { b ->
                        b.putString(":settings:fragment_args_key", componentName)
                    }
                )
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // Fall through to attempt 2
        }
    }

    // Attempt 2: generic accessibility settings page
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // Attempt 3: last resort - open general settings
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) { }
    }
}

/**
 * Check if the SuperClipboard Accessibility Service is currently enabled.
 * Uses the secure settings string directly as a fallback because
 * getEnabledAccessibilityServiceList can return stale results on some devices.
 */
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    // Method 1: AccessibilityManager API
    try {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        val found = enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name == PasteAccessibilityService::class.java.name
        }
        if (found) return true
    } catch (_: Exception) { }

    // Method 2: Read the secure settings string directly (more reliable on some OEMs)
    try {
        val enabledServicesString = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val serviceName =
            "${context.packageName}/${PasteAccessibilityService::class.java.name}"
        val serviceNameFlat =
            "${context.packageName}/.service.PasteAccessibilityService"

        return enabledServicesString.contains(serviceName, ignoreCase = true) ||
                enabledServicesString.contains(serviceNameFlat, ignoreCase = true)
    } catch (_: Exception) {
        return false
    }
}