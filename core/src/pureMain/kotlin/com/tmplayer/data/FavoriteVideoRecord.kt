package com.tmplayer.data

/**
 * Bản ghi video yêu thích trong kho phim Telegram (Favorite Video).
 * Thay thế cho việc đánh dấu chat yêu thích trước đây, cho phép người dùng đánh dấu sao (*) trực tiếp từng video.
 */
data class FavoriteVideoRecord(
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val title: String,
    val fileName: String,
    val chatTitle: String,
    val sizeBytes: Long,
    val durationSec: Int,
    val mimeType: String,
    val addedAt: Long = System.currentTimeMillis()
) {
    val key: String get() = "${chatId}_$messageId"

    fun toMediaItem(): MediaItem = MediaItem(
        chatId = chatId,
        messageId = messageId,
        fileId = fileId,
        title = title,
        sizeBytes = sizeBytes,
        durationSec = durationSec,
        mimeType = mimeType,
        thumbnailFileId = 0,
        miniThumbnail = null,
        date = (addedAt / 1000).toInt(),
        fileName = fileName,
        onDevice = false,
        canBeSaved = true,
        caption = ""
    )

    companion object {
        fun of(item: MediaItem, chatTitle: String = "", addedAt: Long = System.currentTimeMillis()): FavoriteVideoRecord =
            FavoriteVideoRecord(
                chatId = item.chatId,
                messageId = item.messageId,
                fileId = item.fileId,
                title = item.title,
                fileName = item.fileName,
                chatTitle = chatTitle,
                sizeBytes = item.sizeBytes,
                durationSec = item.durationSec,
                mimeType = item.mimeType,
                addedAt = addedAt
            )
    }
}
