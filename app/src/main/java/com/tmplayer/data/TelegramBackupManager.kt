package com.tmplayer.data

import android.content.Context
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.InputDocument
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.InputMessageDocument
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.SearchMessagesFilterDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TelegramBackupItem(
    val messageId: Long,
    val dateMs: Long,
    val fileName: String,
    val fileSize: Long,
    val fileId: Int,
    val caption: String,
    val localPath: String,
    val isDownloaded: Boolean,
) {
    val displayDate: String get() {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(dateMs))
    }

    val displaySize: String get() {
        return when {
            fileSize >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", fileSize / (1024.0 * 1024.0))
            fileSize >= 1024 -> String.format(Locale.US, "%.1f KB", fileSize / 1024.0)
            else -> "$fileSize B"
        }
    }
}

object TelegramBackupManager {

    suspend fun isTelegramSignedIn(): Boolean {
        return runCatching { Td.myId() > 0L }.getOrDefault(false)
    }

    suspend fun getSavedMessagesChatId(): Long? {
        val myId = Td.myId()
        if (myId <= 0L) return null
        val chat = runCatching { Td.client.createPrivateChat(myId, force = false).valueOrNull }.getOrNull()
        return chat?.id ?: myId
    }

    suspend fun backupToTelegram(context: Context, backupJson: String, fileName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val chatId = getSavedMessagesChatId() ?: error("Chưa đăng nhập tài khoản Telegram")

            // Ensure temp directory exists
            val tempDir = File(context.cacheDir, "telegram_backup")
            if (!tempDir.exists()) tempDir.mkdirs()
            val tempFile = File(tempDir, fileName)
            tempFile.writeText(backupJson)

            val inputFile = InputFileLocal(tempFile.absolutePath)
            val inputDoc = InputDocument(
                document = inputFile,
                thumbnail = null,
                disableContentTypeDetection = true,
            )
            val inputMessageContent = InputMessageDocument(
                document = inputDoc,
                caption = null,
            )

            val result = Td.client.sendMessage(
                chatId = chatId,
                topicId = null,
                replyTo = null,
                options = null,
                replyMarkup = null,
                inputMessageContent = inputMessageContent,
            )

            val sentMessage = result.valueOrNull
            if (sentMessage == null) {
                val failure = result as? TdlResult.Failure
                error(failure?.message ?: "Lỗi khi gửi bản sao lưu lên Telegram")
            }

            "Đã sao lưu thành công lên Saved Messages"
        }
    }

    suspend fun listBackupsFromTelegram(): Result<List<TelegramBackupItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val chatId = getSavedMessagesChatId() ?: error("Chưa đăng nhập tài khoản Telegram")

            val found = Td.client.searchChatMessages(
                chatId = chatId,
                topicId = null,
                query = "",
                senderId = null,
                fromMessageId = 0L,
                offset = 0,
                limit = 100,
                filter = SearchMessagesFilterDocument(),
            ).valueOrNull ?: error("Không thể tìm kiếm tin nhắn trong Saved Messages")

            val items = mutableListOf<TelegramBackupItem>()
            for (message in found.messages) {
                val msgDoc = message.content as? MessageDocument ?: continue
                val doc = msgDoc.document
                val name = doc.fileName
                val isBackup = name.endsWith(".json", ignoreCase = true) &&
                    (name.contains("tmplayer", ignoreCase = true) || name.contains("backup", ignoreCase = true))
                if (isBackup) {
                    items.add(
                        TelegramBackupItem(
                            messageId = message.id,
                            dateMs = message.date.toLong() * 1000L,
                            fileName = name,
                            fileSize = doc.document.size,
                            fileId = doc.document.id,
                            caption = msgDoc.caption.text,
                            localPath = doc.document.local.path,
                            isDownloaded = doc.document.local.isDownloadingCompleted,
                        )
                    )
                }
            }
            items.sortedByDescending { it.dateMs }
        }
    }

    suspend fun downloadBackup(item: TelegramBackupItem): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            var filePath = item.localPath
            if (!item.isDownloaded || filePath.isBlank() || !File(filePath).exists()) {
                val res = Td.client.downloadFile(
                    fileId = item.fileId,
                    priority = 32,
                    offset = 0L,
                    limit = 0L,
                    synchronous = true,
                )
                val file = res.valueOrNull ?: error((res as? TdlResult.Failure)?.message ?: "Không thể tải tệp sao lưu từ Telegram")
                filePath = file.local.path
            }

            val file = File(filePath)
            if (!file.exists()) {
                error("Tệp sao lưu không tồn tại trên thiết bị")
            }
            file.readText()
        }
    }
}
