package com.tmplayer.player

import com.tmplayer.data.MediaItem
import com.tmplayer.data.MediaName
import com.tmplayer.data.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class SpeedMemoryTest {

    private fun video(name: String, chatId: Long = -1001L, topicId: Int = 0, messageId: Long = 1L) = MediaItem(
        chatId = chatId, messageId = messageId, fileId = 1, title = name, sizeBytes = 1L, durationSec = 60,
        mimeType = "video/mp4", thumbnailFileId = 0, miniThumbnail = null, date = 0, fileName = name, topicId = topicId,
    )

    /** The key the track choice uses, worked out the way the players work it out. */
    private fun scopes(item: MediaItem) =
        SpeedMemory.scopesOf(item, MediaName.parse(item.fileName).title.ifBlank { item.fileName })

    @Test
    fun `the file's own speed first, then the series, then the chat, then 1x`() {
        assertEquals(1.25f, SpeedMemory.resolve(file = 1.25f, series = 1.5f, chat = 2f))
        assertEquals(1.5f, SpeedMemory.resolve(file = null, series = 1.5f, chat = 2f))
        assertEquals(2f, SpeedMemory.resolve(file = null, series = null, chat = 2f))
        assertEquals(1f, SpeedMemory.resolve(file = null, series = null, chat = null))
    }

    @Test
    fun `a speed that cannot be one is passed over`() {
        assertEquals(1.5f, SpeedMemory.resolve(file = Float.NaN, series = 0f, chat = 1.5f))
        assertEquals(4f, SpeedMemory.resolve(file = 16f, series = null, chat = null))
    }

    @Test
    fun `1x is kept as nothing`() {
        assertNull(SpeedMemory.toKeep(1f))
        assertNull(SpeedMemory.toKeep(1.0001f))
        assertEquals(1.5f, SpeedMemory.toKeep(1.5f))
        assertEquals(0.75f, SpeedMemory.toKeep(0.75f))
    }

    @Test
    fun `an episode is filed under its show, a lecture under its course, anything else under the chat`() {
        val episode = scopes(video("Severance.S01E04.1080p.mkv"))
        assertEquals("chat:-1001", episode.chat)
        assertEquals(episode.series, scopes(video("Severance.S01E05.720p.mkv", messageId = 2)).series)
        assertEquals(episode.series, episode.target)

        val lecture = scopes(video("Physics Chapter 3 Lecture 2.mp4"))
        assertEquals(lecture.series, scopes(video("Physics Chapter 3 Lecture 3.mp4", messageId = 2)).series)
        assertNotEquals(lecture.series, scopes(video("Chemistry Lecture 3.mp4")).series)

        val film = scopes(video("Night.Train.2019.1080p.mkv"))
        assertNull(film.series)
        assertEquals("chat:-1001", film.target)
    }

    @Test
    fun `a forum topic is its own chat, and a file from nowhere has none`() {
        assertEquals("chat:-1001:7", scopes(video("Intro.mp4", topicId = 7)).chat)
        assertNotEquals(SpeedMemory.chatScope(12, 34), SpeedMemory.chatScope(123, 4))
        assertNull(scopes(video("Intro.mp4", chatId = 0)).chat)
    }

    @Test
    fun `remembered through SettingsStore, read narrowest first, and 1x forgets it`() = runBlocking {
        val dir = Files.createTempDirectory("tm-speed").toFile()
        val settings = SettingsStore(SettingsStore.openDataStore(dir.resolve(SettingsStore.FILE_NAME)))
        val lecture = scopes(video("Physics Lecture 4.mp4", topicId = 7))
        val nextLecture = scopes(video("Physics Lecture 5.mp4", topicId = 7, messageId = 2))
        val film = scopes(video("Night.Train.2019.mkv", topicId = 7, messageId = 3))
        val otherTopic = scopes(video("Night.Train.2019.mkv", topicId = 8, messageId = 4))

        assertEquals(1f, settings.openingSpeed(null, nextLecture))

        settings.rememberSpeed(lecture, 1.5f)
        assertEquals(1.5f, settings.openingSpeed(null, nextLecture))
        // Kept for the course, not the topic: a film in the same topic is not sped up.
        assertEquals(1f, settings.openingSpeed(null, film))

        settings.rememberSpeed(film, 1.25f)
        assertEquals(1.25f, settings.openingSpeed(null, film))
        assertEquals(1f, settings.openingSpeed(null, otherTopic))
        // The course's own speed over the topic's, and the file's own over both.
        assertEquals(1.5f, settings.openingSpeed(null, nextLecture))
        assertEquals(2f, settings.openingSpeed(2f, nextLecture))

        // 1x on a lecture forgets the course's speed and the topic's under it.
        settings.rememberSpeed(nextLecture, 1f)
        assertEquals(1f, settings.openingSpeed(null, lecture))
        assertEquals(1f, settings.openingSpeed(null, film))
    }
}
