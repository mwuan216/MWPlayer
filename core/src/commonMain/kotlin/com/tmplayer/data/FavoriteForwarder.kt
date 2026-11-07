package com.tmplayer.data

import dev.g000sha256.tdl.dto.MessageTopicForum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Tự động chuyển tiếp (Forward) các video yêu thích về kho tin nhắn chỉ định (drop author = true).
 */
object FavoriteForwarder {

    suspend fun forwardIfEnabled(settings: SettingsStore, item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val enabled = runCatching { settings.favoriteForwardEnabled.first() }.getOrDefault(false)
        if (!enabled) return@withContext false
        val targetChatId = runCatching { settings.favoriteForwardChatId.first() }.getOrDefault(0L)
        if (targetChatId == 0L) return@withContext false
        val topicId = runCatching { settings.favoriteForwardTopicId.first() }.getOrDefault(0)
        forwardMessage(item.chatId, item.messageId, targetChatId, topicId)
    }

    suspend fun forwardAll(
        favorites: List<FavoriteVideoRecord>,
        targetChatId: Long,
        topicId: Int,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        var success = 0
        val total = favorites.size
        for ((idx, fav) in favorites.withIndex()) {
            val ok = forwardMessage(fav.chatId, fav.messageId, targetChatId, topicId)
            if (ok) success++
            onProgress?.invoke(idx + 1, total)
            if (idx < total - 1) delay(150)
        }
        success
    }

    suspend fun forwardMessage(
        fromChatId: Long,
        messageId: Long,
        targetChatId: Long,
        topicId: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val td = Td.client
            val topic = if (topicId != 0) MessageTopicForum(topicId) else null
            val res = td.forwardMessages(
                chatId = targetChatId,
                topicId = topic,
                fromChatId = fromChatId,
                messageIds = longArrayOf(messageId),
                options = null,
                sendCopy = true, // drop author = true
                removeCaption = false
            )
            res.valueOrNull != null
        } catch (_: Exception) {
            false
        }
    }
}
