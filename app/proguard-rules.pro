# app/proguard-rules.pro

# SuperClipboard ProGuard Rules

# Keep Room entities (they use reflection for column mapping)
-keep class com.superclipboard.data.local.** { *; }

# Keep Accessibility Service (referenced by name in manifest)
-keep class com.superclipboard.service.PasteAccessibilityService { *; }

# Keep Application class
-keep class com.superclipboard.SuperClipboardApp { *; }

# Room
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Compose
-dontwarn androidx.compose.**

# Keep enum classes used in serialization
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# General Android
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile