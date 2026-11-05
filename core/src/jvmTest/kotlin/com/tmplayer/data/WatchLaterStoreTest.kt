package com.tmplayer.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class WatchLaterStoreTest {

    private val dir = Files.createTempDirectory("tm-later").toFile()
    private val watched = WatchedStore(WatchedStore.openDataStore(dir.resolve(WatchedStore.FILE_NAME)))
    private val later = watched.watchLater

    private fun video(messageId: Long, title: String = "Film $messageId") = MediaItem(
        chatId = -100L, messageId = messageId, fileId = 7, title = title, sizeBytes = 2_000_000_000,
        durationSec = 6_300, mimeType = "video/x-matroska", thumbnailFileId = 9, miniThumbnail = null,
        date = 1_700_000_000, fileName = "Film.$messageId.2024.1080p.mkv",
    )

    private fun record(messageId: Long, at: Long, title: String = "Film $messageId") =
        WatchLaterRecord.of(video(messageId, title), chatTitle = "Cinema", addedAt = at)

    @Test
    fun `saved videos come back newest first with what a tile needs`() = runBlocking {
        later.add(record(1, at = 10))
        later.add(record(2, at = 20))
        val items = later.items.first()
        assertEquals(listOf(2L, 1L), items.map { it.messageId })
        val item = items.last().toMediaItem()
        assertEquals("Film 1", item.title)
        assertEquals("Film.1.2024.1080p.mkv", item.fileName)
        assertEquals(2_000_000_000L, item.sizeBytes)
        assertEquals(6_300, item.durationSec)
        assertEquals("Cinema", items.last().chatTitle)
        // Another TDLib instance's picture would be somebody else's: the tile asks again.
        assertEquals(0, item.thumbnailFileId)
        assertTrue(later.contains(-100L, 1L))
        assertEquals(setOf(SettingsStore.progressKey(-100L, 1L), SettingsStore.progressKey(-100L, 2L)), later.keys.first())
    }

    @Test
    fun `the kids lock keeps the chats it leaves out off the list`() = runBlocking {
        later.add(record(1, at = 10))
        try {
            ChatGate.set(setOf(42L))
            assertTrue(later.items.first().isEmpty())
            ChatGate.set(setOf(-100L))
            assertEquals(listOf(1L), later.items.first().map { it.messageId })
        } finally {
            ChatGate.set(null)
        }
    }

    @Test
    fun `adding again moves it to the top rather than listing it twice`() = runBlocking {
        later.add(record(1, at = 10))
        later.add(record(2, at = 20))
        later.add(record(1, at = 30))
        assertEquals(listOf(1L, 2L), later.items.first().map { it.messageId })
    }

    @Test
    fun `removing takes only that video off`() = runBlocking {
        later.add(record(1, at = 10))
        later.add(record(2, at = 20))
        later.remove(-100L, 1L)
        assertFalse(later.contains(-100L, 1L))
        assertEquals(listOf(2L), later.items.first().map { it.messageId })
    }

    @Test
    fun `finishing a video or marking it watched crosses it off`() = runBlocking {
        later.add(record(1, at = 10))
        later.add(record(2, at = 20))
        watched.markWatched(WatchedRecord.of(video(1), "Cinema", watchedAt = 30, manual = false))
        watched.markWatched(WatchedRecord.of(video(2), "Cinema", watchedAt = 40, manual = true))
        assertTrue(later.items.first().isEmpty())
    }

    @Test
    fun `the two lists in one file never read each other's lines`() = runBlocking {
        later.add(record(1, at = 10))
        watched.markWatched(WatchedRecord.of(video(2), "Cinema", watchedAt = 30, manual = true))
        assertEquals(listOf(1L), later.items.first().map { it.messageId })
        assertEquals(listOf(2L), watched.history.first().map { it.messageId })
        // "Clear watched" is about the ticks; Watch later is a plan for the future.
        watched.clear()
        assertEquals(listOf(1L), later.items.first().map { it.messageId })
        later.clear()
        assertTrue(later.items.first().isEmpty())
    }

    @Test
    fun `titles full of separators survive the round trip`() = runBlocking {
        val odd = "Show.S01E09.1080p [x265] - \u001F part"
        later.add(record(9, at = 10, title = odd))
        assertEquals(odd.replace('\u001F', ' '), later.items.first().single().title)
    }

    @Test
    fun `the oldest drop off past the cap`() = runBlocking {
        val over = WatchLaterStore.MAX_WATCH_LATER + 2
        for (id in 1..over) later.add(record(id.toLong(), at = id.toLong()))
        val items = later.items.first()
        assertEquals(WatchLaterStore.MAX_WATCH_LATER, items.size)
        assertFalse(later.contains(-100L, 1L))
        assertFalse(later.contains(-100L, 2L))
        assertTrue(later.contains(-100L, over.toLong()))
    }
}
