package com.tmplayer.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class BookmarkStoreTest {

    private val dir = Files.createTempDirectory("tm-bookmarks").toFile()
    private val store = BookmarkStore(BookmarkStore.openDataStore(dir.resolve(BookmarkStore.FILE_NAME)))

    @Test
    fun `bookmarks come back in the order of the video, each video its own`() = runBlocking {
        store.add(-100L, 1L, positionMs = 90_000, note = "Answer", now = 10)
        store.add(-100L, 1L, positionMs = 30_000, now = 20)
        store.add(-100L, 2L, positionMs = 5_000, now = 30)
        assertEquals(listOf(30_000L, 90_000L), store.bookmarks(-100L, 1L).first().map { it.positionMs })
        assertEquals(listOf("", "Answer"), store.bookmarksNow(-100L, 1L).map { it.note })
        assertEquals(1, store.bookmarksNow(-100L, 2L).size)
    }

    @Test
    fun `a second press at the same moment keeps one bookmark and takes the new note`() = runBlocking {
        val first = store.add(-100L, 1L, positionMs = 60_000, now = 10)
        val again = store.add(-100L, 1L, positionMs = 60_400, note = "Proof", now = 20)
        assertEquals(first.id, again.id)
        val only = store.bookmarksNow(-100L, 1L).single()
        assertEquals("Proof", only.note)
        assertEquals(60_000L, only.positionMs)
        // Pressed again without a note: the note written stays.
        store.add(-100L, 1L, positionMs = 60_200, now = 30)
        assertEquals("Proof", store.bookmarksNow(-100L, 1L).single().note)
    }

    @Test
    fun `rename and delete touch only the bookmark named`() = runBlocking {
        val a = store.add(-100L, 1L, positionMs = 10_000, now = 10)
        val b = store.add(-100L, 1L, positionMs = 20_000, note = "Keep", now = 20)
        store.rename(-100L, 1L, a.id, "  Lemma\n2  ")
        assertEquals(listOf("Lemma 2", "Keep"), store.bookmarksNow(-100L, 1L).map { it.note })
        store.delete(-100L, 1L, a.id)
        assertEquals(listOf(b.id), store.bookmarksNow(-100L, 1L).map { it.id })
        store.delete(-100L, 1L, b.id)
        assertTrue(store.bookmarksNow(-100L, 1L).isEmpty())
    }

    @Test
    fun `notes full of separators survive the round trip, cut to a line`() = runBlocking {
        val odd = "a\u001Eb\u001Fc " + "x".repeat(300)
        store.add(-100L, 1L, positionMs = 1_000, note = odd, now = 10)
        store.add(-100L, 1L, positionMs = 9_000, note = "next", now = 20)
        val list = store.bookmarksNow(-100L, 1L)
        assertEquals(2, list.size)
        assertTrue(list[0].note.startsWith("a b c x"))
        assertEquals(BookmarkStore.MAX_NOTE, list[0].note.length)
        assertEquals("next", list[1].note)
    }

    @Test
    fun `a full video drops its oldest bookmark`() = runBlocking {
        repeat(BookmarkStore.MAX_PER_VIDEO + 1) { i ->
            store.add(-100L, 1L, positionMs = i * 2_000L, now = 1_000L + i)
        }
        val list = store.bookmarksNow(-100L, 1L)
        assertEquals(BookmarkStore.MAX_PER_VIDEO, list.size)
        assertTrue(list.none { it.positionMs == 0L })
    }

    @Test
    fun `clear empties every video's list`() = runBlocking {
        store.add(-100L, 1L, positionMs = 1_000, now = 10)
        store.add(-200L, 5L, positionMs = 1_000, now = 10)
        store.clear()
        assertTrue(store.bookmarksNow(-100L, 1L).isEmpty())
        assertTrue(store.bookmarksNow(-200L, 5L).isEmpty())
    }

    @Test
    fun `a jump is taken once, for its own video only`() {
        BookmarkJump.request(-100L, 7L, 42_000)
        assertNull(BookmarkJump.take(-100L, 8L))
        assertEquals(42_000L, BookmarkJump.take(-100L, 7L))
        assertNull(BookmarkJump.take(-100L, 7L))
    }
}
