package com.tmplayer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object AppCacheManager {
    private const val PREFS_NAME = "app_cache_settings"
    private const val KEY_AUTO_CLEAR_DAYS = "auto_clear_cache_days"
    private const val KEY_LAST_AUTO_CLEAR = "last_auto_clear_timestamp"

    /** Supported auto clear options (in days): 0 (Tắt), 1 (1 ngày), 3 (3 ngày), 7 (1 tuần), 30 (1 tháng) */
    val AUTO_CLEAR_OPTIONS = listOf(0, 1, 3, 7, 30)

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAutoClearDays(context: Context): Int {
        return getPrefs(context).getInt(KEY_AUTO_CLEAR_DAYS, 7)
    }

    fun setAutoClearDays(context: Context, days: Int) {
        getPrefs(context).edit().putInt(KEY_AUTO_CLEAR_DAYS, days).apply()
    }

    fun getAutoClearOptionLabel(days: Int): String {
        return when (days) {
            0 -> "Tắt (Không tự động xóa)"
            1 -> "Sau 1 ngày"
            3 -> "Sau 3 ngày"
            7 -> "Sau 1 tuần (Khuyên dùng)"
            30 -> "Sau 1 tháng"
            else -> "Sau $days ngày"
        }
    }

    private fun getCacheDirectories(context: Context): List<File> {
        val app = context.applicationContext
        return listOfNotNull(
            app.cacheDir,
            app.codeCacheDir,
            app.externalCacheDir
        ).filter { it.exists() }
    }

    suspend fun getCacheSizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        var total = 0L
        for (dir in getCacheDirectories(context)) {
            runCatching {
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    total += file.length()
                }
            }
        }
        total
    }

    suspend fun clearAllCache(context: Context): Long = withContext(Dispatchers.IO) {
        var bytesFreed = 0L
        for (dir in getCacheDirectories(context)) {
            runCatching {
                dir.listFiles()?.forEach { file ->
                    bytesFreed += deleteRecursivelyAndCount(file)
                }
            }
        }
        bytesFreed
    }

    private fun deleteRecursivelyAndCount(file: File): Long {
        var size = 0L
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                size += deleteRecursivelyAndCount(child)
            }
        } else {
            size = file.length()
        }
        file.delete()
        return size
    }

    suspend fun cleanExpiredCache(context: Context): Long = withContext(Dispatchers.IO) {
        val days = getAutoClearDays(context)
        if (days <= 0) return@withContext 0L
        val maxAgeMs = days * 24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        var freed = 0L

        for (dir in getCacheDirectories(context)) {
            runCatching {
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    if (now - file.lastModified() > maxAgeMs) {
                        val len = file.length()
                        if (file.delete()) {
                            freed += len
                        }
                    }
                }
            }
        }
        getPrefs(context).edit().putLong(KEY_LAST_AUTO_CLEAR, now).apply()
        freed
    }

    suspend fun checkAutoClearOnLaunch(context: Context) = withContext(Dispatchers.IO) {
        val days = getAutoClearDays(context)
        if (days <= 0) return@withContext
        val lastCheck = getPrefs(context).getLong(KEY_LAST_AUTO_CLEAR, 0L)
        val now = System.currentTimeMillis()
        if (now - lastCheck > 6L * 3600L * 1000L) {
            cleanExpiredCache(context)
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}
