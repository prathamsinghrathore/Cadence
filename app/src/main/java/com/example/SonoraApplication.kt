package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.data.remote.NetworkModule
import kotlin.system.exitProcess

class SonoraApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = buildString {
                    appendLine("Thread: ${thread.name}")
                    appendLine("Exception: ${throwable::class.java.name}: ${throwable.message}")
                    appendLine()
                    append(Log.getStackTraceString(throwable))
                }
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CRASH_TRACE, stackTrace)
                    .commit()
            } catch (_: Throwable) {
                // Best-effort crash persistence
            }

            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(1)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { NetworkModule.okHttpClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("sonora_image_cache"))
                    .maxSizeBytes(128L * 1024L * 1024L)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .crossfade(150)
            .build()
    }

    companion object {
        private const val PREFS_NAME = "sonora_crash_prefs"
        private const val KEY_CRASH_TRACE = "last_crash_stacktrace"

        fun getSavedCrashTrace(context: Context): String? {
            return context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_CRASH_TRACE, null)
                ?.takeIf { it.isNotBlank() }
        }

        fun clearSavedCrashTrace(context: Context) {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_CRASH_TRACE)
                .apply()
        }
    }
}
