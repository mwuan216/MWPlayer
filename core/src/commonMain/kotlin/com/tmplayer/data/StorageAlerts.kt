package com.tmplayer.data

import com.tmplayer.i18n.L
import com.tmplayer.player.StreamStats

/**
 * "Low storage" (CP76): a notification when the device runs short of space, or the recently
 * played videos TMPlayer keeps take more than their share ([CacheShelf.ceiling]), with Free up
 * space, which opens Downloads with its Free up space sheet.
 *
 * Said once each time storage runs low, and at most every [GAP_MS]: after it goes out it is not
 * said again until storage has recovered (the check finds it fine once) and run low again, so a
 * phone that stays nearly full hears it once, not every day.
 */
object StorageAlerts {

    /** Short of space: below this many bytes free, or below [LOW_SHARE] of the volume. */
    const val LOW_FREE_BYTES = 1024L * 1024 * 1024
    const val LOW_SHARE = 0.05

    const val GAP_MS = 3L * 24 * 60 * 60 * 1000

    enum class Reason {
        /** The device itself is nearly full. */
        DeviceLow,

        /** The recently played videos are over their limit. */
        CacheOver,
    }

    /** What the check remembers: whether the next low may be said, and when the last one was. */
    data class State(val armed: Boolean = true, val lastAt: Long = 0L) {
        fun encode(): String = "${if (armed) 1 else 0}|$lastAt"

        companion object {
            fun decode(text: String?): State {
                val parts = text.orEmpty().split('|')
                if (parts.size != 2) return State()
                return State(armed = parts[0] != "0", lastAt = parts[1].toLongOrNull() ?: 0L)
            }
        }
    }

    /**
     * Why storage is low, or null when it is not. [cacheBytes] is what the recently played videos
     * hold. A disk that has not been measured ([totalBytes] 0) is never low.
     */
    fun reason(freeBytes: Long, totalBytes: Long, cacheBytes: Long): Reason? {
        if (totalBytes <= 0L) return null
        val floor = maxOf(LOW_FREE_BYTES, (totalBytes * LOW_SHARE).toLong())
        if (freeBytes < floor) return Reason.DeviceLow
        if (cacheBytes > CacheShelf.ceiling(totalBytes)) return Reason.CacheOver
        return null
    }

    /** Whether to post now, and the state to store. */
    fun decide(reason: Reason?, state: State, now: Long): Pair<Boolean, State> {
        if (reason == null) return false to state.copy(armed = true)
        if (!state.armed) return false to state
        if (state.lastAt > 0L && now - state.lastAt in 0 until GAP_MS) return false to state
        return true to State(armed = false, lastAt = now)
    }

    /** The notification's title and line. */
    fun words(reason: Reason, freeBytes: Long, cacheBytes: Long): Pair<String, String> = when (reason) {
        Reason.DeviceLow -> L.notifyLowStorageTitle to L.notifyLowStorageBody(free = StreamStats.formatBytes(freeBytes))
        Reason.CacheOver -> L.notifyCacheOverTitle to L.notifyCacheOverBody(size = StreamStats.formatBytes(cacheBytes))
    }

    /**
     * One check, behind its switch: measures with [disk] and the recently played records, stores
     * what it decided and returns the words to post, or null.
     */
    suspend fun check(settings: SettingsStore, disk: DiskInfo, now: Long): Pair<String, String>? {
        if (!settings.noticeOnNow(Notice.LowStorage)) return null
        val cache = runCatching { settings.cachedVideosNow() }.getOrDefault(emptyList())
            .distinctBy { it.fileId }
            .sumOf { it.sizeBytes.coerceAtLeast(0L) }
        val reason = reason(disk.freeBytes, disk.totalBytes, cache)
        val (post, next) = decide(reason, settings.lowStorageAlertNow(), now)
        settings.setLowStorageAlert(next)
        return if (post && reason != null) words(reason, disk.freeBytes, cache) else null
    }
}
