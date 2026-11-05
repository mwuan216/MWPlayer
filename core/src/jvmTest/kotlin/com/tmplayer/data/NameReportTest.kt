package com.tmplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class NameReportTest {

    private var nextId = 1L

    private fun video(fileName: String, caption: String = "") = MediaItem(
        chatId = 4,
        messageId = nextId++,
        fileId = nextId.toInt(),
        title = fileName,
        sizeBytes = 900L * 1024 * 1024,
        durationSec = 0,
        mimeType = "video/mp4",
        thumbnailFileId = 0,
        miniThumbnail = null,
        date = 1,
        fileName = fileName,
        caption = caption,
    )

    @Test
    fun `one tab separated line per file, under the column names`() {
        val items = listOf(
            video("Night Lantern (2022) Tamil 720p.mkv", caption = "First line\twith a tab\nsecond line"),
            video("Night.Lantern.2022.1080p.mkv"),
            video("Harbour.Notes.S01E02.mkv"),
        )
        val lines = NameReport.text(items, emptyMap()).trimEnd().lines()
        assertEquals(NameReport.COLUMNS.joinToString("\t"), lines[0])
        assertEquals(4, lines.size)
        val first = lines[1].split('\t')
        assertEquals(NameReport.COLUMNS.size, first.size)
        assertEquals("First line with a tab", first[1])
        assertEquals("Night Lantern", first[2])
        assertEquals("2022", first[3])
        assertEquals("ta", first[9])
        assertEquals("night lantern|2022", first[10])
        // The two copies share a group; the lone episode has none.
        assertEquals(first[11], lines[2].split('\t')[11])
        assertTrue(first[11].isNotEmpty())
        assertEquals("", lines[3].split('\t')[11])
    }

    @Test
    fun `the file is dated in the viewer's time zone`() {
        assertEquals("tmplayer-names-1970-01-01-0000.txt", NameReport.fileName(0, ZoneOffset.UTC))
    }
}
