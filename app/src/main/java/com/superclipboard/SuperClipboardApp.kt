// src/main/java/com/superclipboard/SuperClipboardApp.kt
package com.superclipboard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.superclipboard.data.local.AppDatabase

/**
 * Application class for SuperClipboard.
 * Initializes the Room database singleton and notification channels.
 */
class SuperClipboardApp : Application() {

    // Lazy initialization of Room database - created once, lives for the entire app lifecycle
    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    /**
     * Creates notification channels required for Android 8.0+.
     * Used for injection progress feedback.
     */
    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "injection_channel"
        const val NOTIFICATION_ID = 1001

        lateinit var instance: SuperClipboardApp
            private set
    }
}