// src/main/java/com/superclipboard/data/local/AppDatabase.kt
package com.superclipboard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room Database singleton.
 * Using a singleton pattern ensures only one database instance exists
 * across the entire app lifecycle, preventing resource leaks.
 */
@Database(
    entities = [MassiveTextEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun massiveTextDao(): MassiveTextDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get or create the database instance.
         * Uses double-checked locking for thread safety.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "superclipboard.db"
                )
                    // SQLite journal mode WAL for better concurrent read/write performance
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}