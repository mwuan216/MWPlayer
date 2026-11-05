package com.tmplayer.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/** The rewind on resume choice is a setting, not a resume point, and the history sweeps leave it be. */
class ResumeRewindSettingTest {

    private fun store() = SettingsStore(
        SettingsStore.openDataStore(Files.createTempDirectory("tm-rewind").resolve(SettingsStore.FILE_NAME).toFile()),
    )

    @Test
    fun `the launch prune does not take the rewind choice for a broken resume point`() = runBlocking {
        val settings = store()
        settings.setResumeRewindMs(10_000L)
        assertEquals(0, settings.pruneBrokenHistory())
        assertEquals(10_000L, settings.resumeRewindMs.first())
    }

    @Test
    fun `opening and closing a resumed video does not walk its place back`() = runBlocking {
        val settings = store()
        settings.setResumeRewindMs(5_000L)
        settings.saveResumePosition(1, 2, 600_000L, 3_600_000L)
        repeat(3) {
            val start = settings.resumeStart(1, 2)
            assertEquals(595_000L, start)
            // Closed straight away, or a key frame before the start.
            settings.saveResumePosition(1, 2, start - 500L, 3_600_000L)
        }
        assertEquals(600_000L, settings.resumePosition(1, 2))
        // Played on past the old place, the new one is saved; sought well back, that is too.
        settings.saveResumePosition(1, 2, 610_000L, 3_600_000L)
        assertEquals(610_000L, settings.resumePosition(1, 2))
        settings.saveResumePosition(1, 2, 400_000L, 3_600_000L)
        assertEquals(400_000L, settings.resumePosition(1, 2))
    }

    @Test
    fun `clearing the watch history keeps the rewind choice`() = runBlocking {
        val settings = store()
        settings.setResumeRewindMs(0L)
        settings.clearWatchHistory()
        assertEquals(0L, settings.resumeRewindNow())
        assertEquals(emptyMap<String, Any>(), settings.watchProgress.first())
    }
}
