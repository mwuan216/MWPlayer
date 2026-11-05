package com.tmplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseProgressTest {

    private var nextId = 1L

    private fun video(fileName: String) = MediaItem(
        chatId = 7,
        messageId = nextId++,
        fileId = nextId.toInt(),
        title = fileName,
        sizeBytes = 1,
        durationSec = 600,
        mimeType = "video/mp4",
        thumbnailFileId = 0,
        miniThumbnail = null,
        date = nextId.toInt(),
        fileName = fileName,
    )

    private fun course(count: Int) = (1..count).map { video("Compilers Lecture $it.mp4") }

    private fun numberOf(item: MediaItem) = LessonSteps.lessonOf(item)!!.number

    private fun progress(
        items: List<MediaItem>,
        watched: Set<Int> = emptySet(),
        points: Map<Int, WatchPoint> = emptyMap(),
    ) = CourseProgress.of(
        items,
        onList = { LessonSteps.lessonOf(it)?.number in watched },
        point = { item -> LessonSteps.lessonOf(item)?.number?.let { points[it] } },
    )

    @Test
    fun `a fresh course has none watched and starts at the first lesson`() {
        val p = progress(course(40))!!
        assertEquals(0, p.watched)
        assertEquals(40, p.total)
        assertEquals(1, numberOf(p.next!!))
        assertEquals("Lecture 1", p.nextCode)
        assertFalse(p.nextStarted)
        assertEquals("Compilers", p.course)
    }

    @Test
    fun `watched lessons are counted and the first unwatched one is next`() {
        val p = progress(course(40), watched = (1..12).toSet() + 14)!!
        assertEquals(13, p.watched)
        assertEquals(13, numberOf(p.next!!))
    }

    @Test
    fun `a position past the watched mark counts as watched`() {
        val p = progress(course(5), watched = setOf(1), points = mapOf(2 to WatchPoint(570_000, 600_000), 3 to WatchPoint(200_000, 600_000)))!!
        assertEquals(2, p.watched)
        assertEquals(3, numberOf(p.next!!))
        assertTrue(p.nextStarted)
    }

    @Test
    fun `a lecture posted twice counts once, and the copy partway through is the one to play`() {
        val items = course(4)
        val again = video("Compilers Lecture 2.mp4")
        val p = CourseProgress.of(
            items + again,
            onList = { numberOf(it) == 1 },
            point = { if (it.id == items[1].id) WatchPoint(300_000, 600_000) else null },
        )!!
        assertEquals(4, p.total)
        assertEquals(items[1].id, p.next!!.id)
        val fresh = CourseProgress.of(items + again, onList = { numberOf(it) == 1 }, point = { null })!!
        assertEquals(again.id, fresh.next!!.id)
        assertEquals(1, CourseProgress.of(items + again, onList = { it.id == again.id }, point = { null })!!.watched)
    }

    @Test
    fun `every lesson watched leaves nothing next`() {
        val p = progress(course(3), watched = setOf(1, 2, 3))!!
        assertTrue(p.finished)
        assertNull(p.next)
        assertNull(p.nextCode)
    }

    @Test
    fun `a listing that is not mostly one run is not a course`() {
        val films = (1..6).map { video("Night Train ${2000 + it} 1080p.mkv") }
        assertNull(progress(films + course(3)))
        assertNull(progress(course(2)))
        assertTrue(progress(films.take(2) + course(3)) != null)
    }

    @Test
    fun `the longest run is the course, short forms of its word included`() {
        val items = course(3) + video("Compilers Lec 4.mp4") + (1..2).map { video("Physics Part $it.mp4") }
        val p = progress(items)!!
        assertEquals(4, p.total)
    }

    @Test
    fun `the watched rule is the Watched list or a finished position`() {
        assertTrue(Watching.counts(onList = true, point = null))
        assertTrue(Watching.counts(onList = false, point = WatchPoint(95, 100)))
        assertFalse(Watching.counts(onList = false, point = WatchPoint(50, 100)))
        assertFalse(Watching.counts(onList = false, point = WatchPoint(50, 0)))
    }
}
