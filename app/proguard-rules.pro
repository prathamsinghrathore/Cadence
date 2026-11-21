# Add project specific ProGuard / R8 rules here.
# Keep stack trace line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------------------
# kotlinx.serialization (API DTOs & Type-Safe Navigation Compose routes)
# ------------------------------------------------------------------------------
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.data.remote.**$$serializer { *; }
-keepclassmembers class com.example.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.ui.navigation.**$$serializer { *; }
-keepclassmembers class com.example.ui.navigation.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.ui.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.example.** { *; }

# ------------------------------------------------------------------------------
# Retrofit & OkHttp
# ------------------------------------------------------------------------------
-keep interface com.example.data.remote.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ------------------------------------------------------------------------------
# Room Database, DAOs, and Entities
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class com.example.data.** { *; }
-keep @androidx.room.Dao interface com.example.data.** { *; }
-keep class com.example.data.AppDatabase_Impl { *; }
-keep class com.example.data.SongDao_Impl { *; }

# ------------------------------------------------------------------------------
# WorkManager Workers
# ------------------------------------------------------------------------------
-keep class com.example.data.download.SongDownloadWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ------------------------------------------------------------------------------
# AndroidX Media3 / ExoPlayer Service
# ------------------------------------------------------------------------------
-keep class com.example.player.MusicService { *; }
-dontwarn androidx.media3.**
