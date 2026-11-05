package com.tmplayer.data

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.SearchMessagesFilterVideoNote
import dev.g000sha256.tdl.dto.TextEntityTypeTextUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * More places videos live in a chat (CP81): round video messages, the comments under a channel
 * post, and the chat's pinned post. Each is found through an entry of its own beside the chat's
 * videos, not mixed into the grid, so the grid's paging and its counts stay what they were.
 */

/** One page of videos found somewhere other than the chat's own video search. */
data class VideoPage(val items: List<MediaItem>, val next: Long, val done: Boolean)

/** A chat's round video messages (Telegram's video notes), newest first. */
object ChatVideoNotes {

    /** How many round videos [chatId] holds, from TDLib's count; 0 when unknown. */
    suspend fun count(td: TdlClient, chatId: Long): Int = withContext(Dispatchers.IO) {
        val local = td.getChatMessageCount(chatId, null, SearchMessagesFilterVideoNote(), true).valueOrNull?.count
        if (local != null && local >= 0) return@withContext local
        td.getChatMessageCount(chatId, null, SearchMessagesFilterVideoNote(), false).valueOrNull?.count?.coerceAtLeast(0) ?: 0
    }

    /** The page after [from] (0 for the first). A failure is thrown, for the list to say. */
    suspend fun page(td: TdlClient, chatId: Long, from: Long, limit: Int = PAGE): VideoPage = withContext(Dispatchers.IO) {
        val chatProtected = td.getChat(chatId).valueOrNull?.hasProtectedContent ?: false
        val found = td.searchChatMessages(chatId, null, "", null, from, 0, limit, SearchMessagesFilterVideoNote()).value()
        val items = MediaMapper.screen(found.messages.toList(), chatProtected).items
        VideoPage(items, found.nextFromMessageId, found.messages.isEmpty() || found.nextFromMessageId == 0L)
    }

    const val PAGE = 50
}

/**
 * The comments under a channel post, which live in the channel's discussion group as a thread
 * of their own: the videos posted there, played like any other.
 */
object PostComments {

    /** Where a post's comments are: the discussion group and the thread in it. */
    data class Thread(val chatId: Long, val threadId: Long)

    /** How many comments [message] has, 0 for a post without any (or not a channel post). */
    fun countOf(message: Message): Int =
        if (!message.isChannelPost) 0 else message.interactionInfo?.replyInfo?.replyCount?.coerceAtLeast(0) ?: 0

    /** How many comments the post has, asked of TDLib (from memory for a post it has seen). */
    suspend fun count(td: TdlClient, chatId: Long, messageId: Long): Int = withContext(Dispatchers.IO) {
        val message = td.getMessage(chatId, messageId).valueOrNull ?: return@withContext 0
        countOf(message)
    }

    /**
     * Whether the comments may be listed: their group is a chat of its own, so the kids lock has
     * to allow it as well as the channel.
     */
    fun allowed(kidsAllowed: Set<Long>?, channelId: Long, discussionId: Long): Boolean =
        KidsLock.allows(kidsAllowed, channelId) && KidsLock.allows(kidsAllowed, discussionId)

    /** The thread behind a post's comments, or null when it has none Telegram will show. */
    suspend fun thread(td: TdlClient, chatId: Long, messageId: Long): Thread? = withContext(Dispatchers.IO) {
        val info = td.getMessageThread(chatId, messageId).valueOrNull ?: return@withContext null
        Thread(info.chatId, info.messageThreadId)
    }

    /**
     * The videos among the next [SCAN] comments from [from] (0: the newest), the thread read a
     * hundred at a time. A comment thread is mostly words, so a page may hold none and not be the end.
     */
    suspend fun page(td: TdlClient, chatId: Long, messageId: Long, discussionId: Long, from: Long): VideoPage = withContext(Dispatchers.IO) {
        val chatProtected = td.getChat(discussionId).valueOrNull?.hasProtectedContent ?: false
        var cursor = from
        val found = ArrayList<MediaItem>()
        var read = 0
        var done = false
        while (read < SCAN) {
            val batch = td.getMessageThreadHistory(chatId, messageId, cursor, 0, BATCH).value()
                .messages.filterNotNull().filter { it.id != cursor || cursor == 0L }
            if (batch.isEmpty()) {
                done = true
                break
            }
            read += batch.size
            found += MediaMapper.screen(batch, chatProtected).items
            cursor = batch.minOf { it.id }
        }
        VideoPage(found.distinctBy { it.id }.sortedByDescending { it.messageId }, cursor, done)
    }

    private const val BATCH = 100
    private const val SCAN = 500
}

/** A chat's pinned post, as the top of its videos shows it. */
data class PinnedPost(
    val messageId: Long,
    /** The pinned post's own video, played from the card. */
    val video: MediaItem?,
    /** Or a link in it to a post elsewhere, opened through the link flow (CP79). */
    val link: TgLink.Post?,
    /** The post's first line, for the card. */
    val text: String,
)

object PinnedPosts {

    /**
     * What a pinned post comes to: its own video, else the first post link in its words, else
     * nothing (a pinned rules post or a greeting is not shown over the videos).
     */
    fun decide(messageId: Long, video: MediaItem?, text: String, urls: List<String>): PinnedPost? {
        val firstLine = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty().take(TEXT_MAX)
        if (video != null) return PinnedPost(messageId, video, null, firstLine.ifBlank { video.title })
        val link = (urls.asSequence().mapNotNull { TelegramLinks.parse(it) } + sequenceOf(TelegramLinks.find(text)))
            .filterIsInstance<TgLink.Post>().firstOrNull() ?: return null
        return PinnedPost(messageId, null, link, firstLine)
    }

    /** [chatId]'s pinned post, or null for none, one that is neither, or a chat the lock hides. */
    suspend fun of(td: TdlClient, chatId: Long): PinnedPost? = withContext(Dispatchers.IO) {
        if (!KidsLock.allows(ChatGate.awaitReady(), chatId)) return@withContext null
        val message = td.getChatPinnedMessage(chatId).valueOrNull ?: return@withContext null
        val chatProtected = td.getChat(chatId).valueOrNull?.hasProtectedContent ?: false
        val video = MediaMapper.fromMessage(message, chatProtected)
        val words = wordsOf(message)
        val urls = words?.entities?.mapNotNull { (it.type as? TextEntityTypeTextUrl)?.url }.orEmpty()
        decide(message.id, video, words?.text.orEmpty(), urls)
    }

    private fun wordsOf(message: Message): FormattedText? = when (val content = message.content) {
        is MessageText -> content.text
        is MessagePhoto -> content.caption
        is dev.g000sha256.tdl.dto.MessageVideo -> content.caption
        is dev.g000sha256.tdl.dto.MessageDocument -> content.caption
        else -> null
    }

    private const val TEXT_MAX = 120
}
