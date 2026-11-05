package com.tmplayer.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResumeSyncDocTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_800_000_000_000L

    private fun entry(message: Long, at: Long, position: Long = 600_000) = ResumeSyncDoc.Entry(
        chatId = -100L,
        messageId = message,
        positionMs = position,
        durationMs = 3_600_000,
        updatedAt = at,
        title = "Episode $message",
        chatTitle = "Study Hub",
        sizeBytes = 1_000_000,
        durationSec = 3600,
    )

    @Test
    fun `the newest place for a video wins, whichever device has it`() {
        val phone = ResumeSyncDoc.Doc(listOf(entry(1, at = now - 10, position = 900_000)))
        val tv = ResumeSyncDoc.Doc(listOf(entry(1, at = now - 500, position = 300_000), entry(2, at = now - 20)))
        val merged = ResumeSyncDoc.merge(tv, phone, now)
        assertEquals(listOf(1L, 2L), merged.entries.map { it.messageId })
        assertEquals(900_000L, merged.entries.first().positionMs)
    }

    @Test
    fun `a video finished on one device does not come back from another's older place`() {
        val tv = ResumeSyncDoc.Doc(listOf(entry(1, at = now - 1_000)))
        val phone = ResumeSyncDoc.Doc(tombs = listOf(ResumeSyncDoc.Tomb(-100L, 1, at = now)))
        val merged = ResumeSyncDoc.merge(tv, phone, now)
        assertTrue(merged.entries.isEmpty())
        assertEquals(1, merged.tombs.size)
    }

    @Test
    fun `watching it again after the removal brings it back`() {
        val again = ResumeSyncDoc.Doc(listOf(entry(1, at = now)))
        val removed = ResumeSyncDoc.Doc(tombs = listOf(ResumeSyncDoc.Tomb(-100L, 1, at = now - 1_000)))
        val merged = ResumeSyncDoc.merge(removed, again, now)
        assertEquals(1, merged.entries.size)
        assertTrue("the removal is spent", merged.tombs.isEmpty())
    }

    @Test
    fun `removals are forgotten after a month`() {
        val old = ResumeSyncDoc.Doc(tombs = listOf(ResumeSyncDoc.Tomb(-100L, 1, at = now - 31L * 24 * 3600 * 1000)))
        assertTrue(ResumeSyncDoc.merge(old, ResumeSyncDoc.Doc(), now).tombs.isEmpty())
    }

    @Test
    fun `the message reads back as it was written`() {
        val doc = ResumeSyncDoc.Doc(listOf(entry(1, now), entry(2, now - 5)), listOf(ResumeSyncDoc.Tomb(-100L, 3, now)))
        val text = ResumeSyncDoc.encode(doc, "TMPlayer keeps the places here.")
        assertTrue(text.startsWith(ResumeSyncDoc.TAG))
        assertEquals(doc, ResumeSyncDoc.decode(text))
    }

    @Test
    fun `a long history is cut to fit one Telegram message, oldest first`() {
        val many = ResumeSyncDoc.Doc((1L..40L).map { entry(it, now - it * 1000).copy(title = "A rather long title for episode number $it of the series") })
        val text = ResumeSyncDoc.encode(many, "intro")
        assertTrue(text.length <= ResumeSyncDoc.MAX_CHARS)
        val back = ResumeSyncDoc.decode(text)!!
        assertEquals(1L, back.entries.first().messageId)
        assertFalse(back.entries.any { it.messageId == 40L })
    }

    @Test
    fun `somebody else's message is not ours`() {
        assertNull(ResumeSyncDoc.decode("Just a note to myself"))
        assertNull(ResumeSyncDoc.decode(null))
    }

    @Test
    fun `a newer place from another device is taken in, an older one is not`() = runBlocking {
        val settings = SettingsStore(SettingsStore.openDataStore(folder.root.resolve(SettingsStore.FILE_NAME)))
        assertTrue(settings.importResume(entry(1, at = now), fileId = 42))
        val record = settings.continueWatching.first().single()
        assertEquals(600_000L, record.positionMs)
        assertEquals(42, record.fileId)
        assertFalse(settings.importResume(entry(1, at = now - 1, position = 100_000), fileId = 42))
        assertEquals(600_000L, settings.continueWatching.first().single().positionMs)
        settings.dropSyncedResume(ResumeSyncDoc.Tomb(-100L, 1, at = now + 1))
        assertTrue(settings.continueWatching.first().isEmpty())
    }
}
