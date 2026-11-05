package com.tmplayer.player

import com.tmplayer.data.DownloadRequest
import com.tmplayer.data.OfflineDownloads
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Save mobile data": the bounded request on a metered connection, and who it applies to. */
class SaveDataStreamsTest {

    private val mb = 1024L * 1024
    private val film = 2_000 * mb
    private val w = SaveDataWindow.WINDOW_BYTES
    private val margin = SaveDataWindow.EXTEND_MARGIN_BYTES
    private fun r(start: Long, end: Long) = LowStorageWindow.Range(start, end)

    private val fileId = 7301
    private var metered = true

    @After
    fun cleanUp() {
        SaveDataStreams.revoke(fileId)
        LowStorageStreams.revoke(fileId)
        OfflineDownloads.forget(fileId)
    }

    // ---- the plan ------------------------------------------------------------------------------

    @Test
    fun `on mobile data a request reaches the window ahead and no further`() {
        val request = SaveDataWindow.plan(emptyList(), 300 * mb, film, metered = true)
        assertEquals(w, request.limit)
        assertTrue(request.bounded)
        assertEquals(listOf(r(300 * mb, 300 * mb + w)), request.ranges)
    }

    @Test
    fun `near the end of the file it asks for what is left, never for the rest unbounded`() {
        val request = SaveDataWindow.plan(emptyList(), film - 30 * mb, film, metered = true)
        assertEquals(30 * mb, request.limit)
        assertEquals(1L, SaveDataWindow.plan(emptyList(), film, film, metered = true).limit)
    }

    @Test
    fun `on wifi the request goes to the end of the file, as without the setting`() {
        val held = listOf(r(0, w))
        val request = SaveDataWindow.plan(held, 300 * mb, film, metered = false)
        assertEquals(0L, request.limit)
        assertFalse(request.bounded)
        assertEquals("what was asked for before is still known", held, request.ranges)
    }

    @Test
    fun `an extension falls due only near the end of the stretch asked for`() {
        val ranges = listOf(r(0, w))
        assertFalse(SaveDataWindow.extendDue(10 * mb, ranges, film, metered = true, lastBounded = true))
        assertFalse(SaveDataWindow.extendDue(w - margin - 1, ranges, film, metered = true, lastBounded = true))
        assertTrue(SaveDataWindow.extendDue(w - margin, ranges, film, metered = true, lastBounded = true))
        assertTrue(SaveDataWindow.extendDue(w - 1, ranges, film, metered = true, lastBounded = true))
    }

    @Test
    fun `a stretch that reaches the end of the file has nothing to extend`() {
        val ranges = listOf(r(film - w, film))
        assertFalse(SaveDataWindow.extendDue(film - 1, ranges, film, metered = true, lastBounded = true))
    }

    @Test
    fun `a read outside every stretch is a seek, left to the seek path`() {
        assertFalse(SaveDataWindow.extendDue(900 * mb, listOf(r(0, w)), film, metered = true, lastBounded = true))
    }

    @Test
    fun `a change of connection asks again at the next read`() {
        val ranges = listOf(r(0, w))
        // Wi-Fi after a bounded request: the bound lifts at once.
        assertTrue(SaveDataWindow.extendDue(10 * mb, ranges, film, metered = false, lastBounded = true))
        // Still Wi-Fi after an unbounded one: nothing to do.
        assertFalse(SaveDataWindow.extendDue(10 * mb, ranges, film, metered = false, lastBounded = false))
        // Mobile data after an unbounded one: TDLib would go on to the end, so it is bounded now.
        assertTrue(SaveDataWindow.extendDue(10 * mb, emptyList(), film, metered = true, lastBounded = false))
    }

    @Test
    fun `an extension starts at the read position on a part boundary`() {
        val from = SaveDataWindow.extendStart(w - margin + 12_345)
        assertEquals(0, from % SaveDataWindow.ALIGN_BYTES)
        assertTrue(from <= w - margin + 12_345)
        assertTrue(w - margin + 12_345 - from < SaveDataWindow.ALIGN_BYTES)
    }

    @Test
    fun `watching to the end extends the stretch step by step and never gives anything back`() {
        var ranges = SaveDataWindow.plan(emptyList(), 0, film, metered = true).ranges
        var requests = 1
        var position = 0L
        while (position < film) {
            if (SaveDataWindow.extendDue(position, ranges, film, metered = true, lastBounded = true)) {
                val before = ranges
                ranges = SaveDataWindow.plan(ranges, SaveDataWindow.extendStart(position), film, metered = true).ranges
                requests++
                // Nothing already fetched drops out of what is known to be here.
                for (old in before) assertTrue(ranges.any { it.start <= old.start && it.end >= old.end })
            }
            // Never a read beyond what was asked for: the extension came first.
            assertTrue(ranges.any { position >= it.start && position < it.end })
            position += 1 * mb
        }
        assertEquals(listOf(r(0, film)), ranges)
        assertTrue("about one request per window, not one per read", requests <= film / (w - margin) + 2)
    }

    @Test
    fun `a seek back and a rewatch ask for nothing more`() {
        var ranges = SaveDataWindow.plan(emptyList(), 0, film, metered = true).ranges
        ranges = SaveDataWindow.plan(ranges, SaveDataWindow.extendStart(w - margin), film, metered = true).ranges
        // Back to the start: inside what was fetched, far from its end.
        assertFalse(SaveDataWindow.extendDue(5 * mb, ranges, film, metered = true, lastBounded = true))
        assertFalse(SaveDataWindow.extendDue(w, ranges, film, metered = true, lastBounded = true))
    }

    // ---- who it applies to ---------------------------------------------------------------------

    @Test
    fun `a file nobody allowed is not bounded`() {
        assertNull(SaveDataStreams.plan(fileId, 0, film))
        assertFalse(SaveDataStreams.extendDue(fileId, 0, film))
        assertFalse(SaveDataStreams.isActive(fileId))
    }

    @Test
    fun `an allowed stream is bounded on mobile data and unbounded on wifi`() {
        SaveDataStreams.allow(fileId) { metered }
        assertTrue(SaveDataStreams.isActive(fileId))
        assertFalse("nothing to extend before the first request", SaveDataStreams.extendDue(fileId, 0, film))
        assertEquals(w, SaveDataStreams.plan(fileId, 0, film))
        assertTrue(SaveDataStreams.extendDue(fileId, w - margin, film))
        assertEquals(w, SaveDataStreams.plan(fileId, SaveDataWindow.extendStart(w - margin), film))

        metered = false
        assertTrue("Wi-Fi lifts the bound at the next read", SaveDataStreams.extendDue(fileId, 10 * mb, film))
        assertEquals(0L, SaveDataStreams.plan(fileId, 10 * mb, film))
        assertFalse(SaveDataStreams.extendDue(fileId, 20 * mb, film))

        metered = true
        assertTrue("mobile data bounds it again", SaveDataStreams.extendDue(fileId, 20 * mb, film))
        assertEquals(w, SaveDataStreams.plan(fileId, 20 * mb, film))
    }

    @Test
    fun `a download the viewer queued is never bounded`() {
        SaveDataStreams.allow(fileId) { metered }
        OfflineDownloads.note(
            OfflineDownloads.Progress(
                DownloadRequest(
                    fileId = fileId, title = "Film", sizeBytes = film, chatId = 1, messageId = 2,
                    chatTitle = "Chat", durationSec = 6_000, mimeType = "video/mp4", fileName = "f.mp4",
                ),
                0, film, OfflineDownloads.Stage.Running,
            ),
        )
        assertFalse(SaveDataStreams.isActive(fileId))
        assertNull(SaveDataStreams.plan(fileId, 0, film))
        assertFalse(SaveDataStreams.extendDue(fileId, 0, film))
    }

    @Test
    fun `low storage mode keeps its own rules where both apply`() {
        SaveDataStreams.allow(fileId) { metered }
        LowStorageStreams.allow(fileId, LowStorageWindow.WINDOW_BYTES)
        assertFalse(SaveDataStreams.isActive(fileId))
        assertNull(SaveDataStreams.plan(fileId, 0, film))
        assertFalse(SaveDataStreams.extendDue(fileId, 0, film))
    }

    @Test
    fun `revoking goes back to the whole file`() {
        SaveDataStreams.allow(fileId) { metered }
        SaveDataStreams.plan(fileId, 0, film)
        SaveDataStreams.revoke(fileId)
        assertNull(SaveDataStreams.plan(fileId, 0, film))
    }

    // ---- a player takes back only its own decision ----------------------------------------------

    @Test
    fun `an old player of the same file leaving does not unbound the new one in low storage mode`() {
        val old = LowStorageStreams.allow(fileId, 40 * mb)
        // The same video opened again before the first player has gone: it decides afresh.
        LowStorageStreams.revoke(fileId)
        val new = LowStorageStreams.allow(fileId, 40 * mb)
        LowStorageStreams.revoke(fileId, old)
        assertTrue(LowStorageStreams.isRolling(fileId))
        val request = LowStorageStreams.plan(fileId, 0, film)
        assertEquals("still bounded to the window, never the whole video", 40 * mb, request?.limit)
        LowStorageStreams.revoke(fileId, new)
        assertFalse(LowStorageStreams.isRolling(fileId))
        assertNull(LowStorageStreams.plan(fileId, 0, film))
    }

    @Test
    fun `an old player of the same file leaving does not unbound the new one on mobile data`() {
        val old = SaveDataStreams.allow(fileId) { metered }
        val new = SaveDataStreams.allow(fileId) { metered }
        SaveDataStreams.revoke(fileId, old)
        assertEquals(w, SaveDataStreams.plan(fileId, 0, film))
        SaveDataStreams.revoke(fileId, new)
        assertNull(SaveDataStreams.plan(fileId, 0, film))
    }
}
