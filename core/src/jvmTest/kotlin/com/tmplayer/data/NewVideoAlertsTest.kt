package com.tmplayer.data

import com.tmplayer.i18n.L
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * New videos in starred chats: per chat marks of the newest video seen, a first look that only
 * sets the mark, failed chats keeping theirs, unstarred chats losing theirs, the size limits, and
 * the notification's words with and without the app lock.
 */
class NewVideoAlertsTest {

    private fun video(chat: Long, id: Long, size: Long = 500_000_000L, title: String = "Episode $id") = MediaItem(
        chatId = chat, messageId = id, fileId = id.toInt(), title = title, sizeBytes = size,
        durationSec = 0, mimeType = "video/mp4", thumbnailFileId = 0, miniThumbnail = null, date = 0,
    )

    private val titles = mapOf(1L to "Video Club", 2L to "Weekend series")

    @Test
    fun `a chat seen for the first time only sets its mark`() {
        val diff = NewVideoAlerts.diff(listOf(1L), emptyMap(), mapOf(1L to listOf(video(1, 30), video(1, 20))), titles)
        assertTrue(diff.news.isEmpty())
        assertEquals(mapOf(1L to 30L), diff.seen)
    }

    @Test
    fun `only videos past the mark are news, newest first`() {
        val diff = NewVideoAlerts.diff(
            listOf(1L),
            mapOf(1L to 20L),
            mapOf(1L to listOf(video(1, 22), video(1, 40), video(1, 20), video(1, 10))),
            titles,
        )
        val news = diff.news.single()
        assertEquals(2, news.count)
        assertEquals(listOf("Episode 40", "Episode 22"), news.titles)
        assertEquals(mapOf(1L to 40L), diff.seen)
    }

    @Test
    fun `a chat that failed keeps its mark, an unstarred one loses it`() {
        val diff = NewVideoAlerts.diff(
            starred = listOf(1L, 2L),
            seen = mapOf(1L to 20L, 2L to 50L, 3L to 70L),
            latest = mapOf(1L to listOf(video(1, 20))),
            titles = titles,
        )
        assertTrue(diff.news.isEmpty())
        assertEquals(mapOf(1L to 20L, 2L to 50L), diff.seen)
    }

    @Test
    fun `a video the size limits hide is not news but moves the mark`() {
        val small = 10_000_000L
        val diff = NewVideoAlerts.diff(
            listOf(1L),
            mapOf(1L to 20L),
            mapOf(1L to listOf(video(1, 25, size = small), video(1, 24))),
            titles,
            keep = { SizeFilter.matches(it.sizeBytes, 100_000_000L, SizeFilter.DEFAULT_MAX) },
        )
        assertEquals(1, diff.news.single().count)
        assertEquals(mapOf(1L to 25L), diff.seen)
        // Next time nothing is new: the hidden one is behind the mark.
        val again = NewVideoAlerts.diff(listOf(1L), diff.seen, mapOf(1L to listOf(video(1, 25, size = small))), titles)
        assertTrue(again.news.isEmpty())
    }

    @Test
    fun `a chat with no videos at all is marked at zero and reports its first`() {
        val first = NewVideoAlerts.diff(listOf(2L), emptyMap(), mapOf(2L to emptyList()), titles)
        assertEquals(mapOf(2L to 0L), first.seen)
        val then = NewVideoAlerts.diff(listOf(2L), first.seen, mapOf(2L to listOf(video(2, 5))), titles)
        assertEquals(1, then.news.single().count)
    }

    @Test
    fun `the notification names one chat, sums several, and names nothing under the app lock`() {
        val one = listOf(NewVideoAlerts.ChatNews(1L, "Video Club", 3, listOf("A", "B")))
        val single = NewVideoAlerts.alert(one, private = false)!!
        assertEquals(L.notifyNewVideosInChat(count = 3, chat = "Video Club"), single.title)
        assertEquals("A, B", single.body)
        assertEquals(1L, single.chatId)

        val two = one + NewVideoAlerts.ChatNews(2L, "Weekend series", 4, emptyList())
        val summary = NewVideoAlerts.alert(two, private = false)!!
        assertEquals(L.notifyNewVideosInChats(count = 7), summary.title)
        assertEquals("Video Club, Weekend series", summary.body)
        assertNull(summary.chatId)

        val locked = NewVideoAlerts.alert(two, private = true)!!
        assertEquals(L.notifyNewVideosPrivate, locked.title)
        assertNull(locked.body)
        assertNull(locked.chatId)
        assertTrue(!locked.title.contains("Video Club"))

        assertNull(NewVideoAlerts.alert(emptyList(), private = false))
    }

    @Test
    fun `the marks survive their own encoding`() {
        val seen = mapOf(-1001234L to 99L, 7L to 0L)
        assertEquals(seen, NewVideoAlerts.decode(NewVideoAlerts.encode(seen)))
        assertEquals(emptyMap<Long, Long>(), NewVideoAlerts.decode(null))
        assertEquals(emptyMap<Long, Long>(), NewVideoAlerts.decode("rubbish;1:x"))
    }

    @Test
    fun `turning the switch on forgets old marks, and it is off by default`() = runBlocking {
        val dir = Files.createTempDirectory("tm-newvideos").toFile()
        val settings = SettingsStore(SettingsStore.openDataStore(dir.resolve(SettingsStore.FILE_NAME)))
        assertTrue(!settings.newVideoAlertsNow())
        assertTrue(!settings.newVideoOfferedNow())
        settings.setNewVideoSeen(mapOf(1L to 5L))
        settings.setNewVideoAlerts(true)
        assertTrue(settings.newVideoAlertsNow())
        assertTrue(settings.newVideoOfferedNow())
        assertEquals(emptyMap<Long, Long>(), settings.newVideoSeenNow())
    }
}
