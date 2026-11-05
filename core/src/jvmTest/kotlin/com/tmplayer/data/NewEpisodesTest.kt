package com.tmplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewEpisodesTest {

    private var nextId = 1L

    /** Watched at this moment, in milliseconds; posts dated before it are old, after it new. */
    private val visit = 1_000_000L * 1000L
    private val before = 900_000
    private val after = 1_100_000

    private fun video(name: String, date: Int, chatId: Long = 1, sizeBytes: Long = 500_000_000, caption: String = "") = MediaItem(
        chatId = chatId,
        messageId = nextId++,
        fileId = 1,
        title = name,
        sizeBytes = sizeBytes,
        durationSec = 1_400,
        mimeType = "video/x-matroska",
        thumbnailFileId = 0,
        miniThumbnail = null,
        date = date,
        fileName = name,
        caption = caption,
    )

    private fun watched(item: MediaItem, at: Long = visit) = WatchedRecord.of(item, "Chat", watchedAt = at, manual = false)

    private fun resume(item: MediaItem, at: Long = visit) = ResumeRecord(
        chatId = item.chatId,
        messageId = item.messageId,
        fileId = item.fileId,
        title = item.title,
        chatTitle = "Chat",
        sizeBytes = item.sizeBytes,
        durationSec = item.durationSec,
        positionMs = 300_000,
        durationMs = 1_400_000,
        updatedAt = at,
    )

    /** What Home would list for the viewer's [resume] and [watched] records, the chat being [pool]. */
    private fun fresh(pool: List<MediaItem>, resume: List<ResumeRecord> = emptyList(), watched: List<WatchedRecord> = emptyList()): List<NewEpisode> {
        val shows = NewEpisodes.inProgress(resume, watched)
        val keys = NewEpisodes.touchTimes(resume, watched).keys
        return NewEpisodes.collect(shows, shows.associate { it.key to pool }) { SettingsStore.progressKey(it.chatId, it.messageId) in keys }
    }

    @Test
    fun `S01E05 shape, what came after the last watched episode and since the visit`() {
        val one = video("Harbour.Notes.S01E01.1080p.WEB.x264.mkv", before)
        val two = video("Harbour.Notes.S01E02.1080p.WEB.x264.mkv", before)
        val three = video("Harbour.Notes.S01E03.1080p.WEB.x264.mkv", after)
        val four = video("Harbour.Notes.S01E04.1080p.WEB.x264.mkv", after)
        val found = fresh(listOf(four, three, two, one), watched = listOf(watched(one), watched(two)))
        assertEquals(listOf("S01E03", "S01E04"), found.map { it.code })
        assertEquals(listOf(three.id, four.id), found.map { it.item.id })
        assertEquals("Harbour Notes", found.first().show)
    }

    @Test
    fun `1x05 shape, from a video still in progress`() {
        val four = video("Harbour Notes 1x04 720p.mkv", before)
        val five = video("Harbour Notes 1x05 720p.mkv", after)
        val found = fresh(listOf(five, four), resume = listOf(resume(four)))
        assertEquals(listOf("S01E05"), found.map { it.code })
    }

    @Test
    fun `Episode 12 shape, and a new season counts as after`() {
        val eleven = video("Family Full House Episode 11.mp4", before)
        val twelve = video("Family Full House Episode 12.mp4", after)
        assertEquals(listOf("S01E12"), fresh(listOf(twelve, eleven), watched = listOf(watched(eleven))).map { it.code })

        val finale = video("Kerala Crime Files S01E06.mkv", before)
        val next = video("Kerala Crime Files S02E01.mkv", after)
        assertEquals(listOf("S02E01"), fresh(listOf(next, finale), watched = listOf(watched(finale))).map { it.code })
    }

    @Test
    fun `anime absolute numbers stay one long season`() {
        val seen = video("[SubsPlease] Sky Garden - 1084 (1080p) [A1B2C3D4].mkv", before)
        val new = video("[SubsPlease] Sky Garden - 1085 (1080p) [E5F6A7B8].mkv", after)
        val found = fresh(listOf(new, seen), watched = listOf(watched(seen)))
        assertEquals(listOf(new.id), found.map { it.item.id })
        assertEquals("S01E1085", found.single().code)
    }

    @Test
    fun `a later lecture of the same course, and only that course`() {
        val eleven = video("Physics Class 12 Lecture 11.mp4", before)
        val twelve = video("Physics Class 12 Lecture 12.mp4", after)
        val thirteen = video("Physics Class 12 Lec-13.mp4", after)
        val chemistry = video("Chemistry Class 12 Lecture 12.mp4", after)
        val part = video("Physics Class 12 Part 14.mp4", after)
        val found = fresh(listOf(part, chemistry, thirteen, twelve, eleven), watched = listOf(watched(eleven)))
        assertEquals(listOf("Lecture 12", "Lecture 13"), found.map { it.code })
        assertEquals("Physics Class 12", found.first().show)
    }

    @Test
    fun `episodes posted before the last visit are next, not new`() {
        val one = video("Harbour.Notes.S01E01.mkv", before)
        val two = video("Harbour.Notes.S01E02.mkv", before)
        val three = video("Harbour.Notes.S01E03.mkv", before)
        assertTrue(fresh(listOf(three, two, one), watched = listOf(watched(one))).isEmpty())
    }

    @Test
    fun `a watched or started episode is not new, nor anything before the furthest reached`() {
        val one = video("Harbour.Notes.S01E01.mkv", after)
        val two = video("Harbour.Notes.S01E02.mkv", after)
        val three = video("Harbour.Notes.S01E03.mkv", after)
        val four = video("Harbour.Notes.S01E04.mkv", after)
        // Three is in progress, one finished long ago: two was skipped, four is the only new one.
        val found = fresh(listOf(four, three, two, one), resume = listOf(resume(three)), watched = listOf(watched(one, at = visit - 1)))
        assertEquals(listOf(four.id), found.map { it.item.id })
    }

    @Test
    fun `copies of a new episode are one, the copy like the one watched`() {
        val seen = video("Harbour.Notes.S01E01.720p.WEB.x264.mkv", before, sizeBytes = 400_000_000)
        val small = video("Harbour.Notes.S01E02.720p.WEB.x264.mkv", after, sizeBytes = 410_000_000)
        val big = video("Harbour.Notes.S01E02.2160p.WEB.x265.mkv", after, sizeBytes = 4_000_000_000)
        val found = fresh(listOf(big, small, seen), watched = listOf(watched(seen)))
        assertEquals(listOf(small.id), found.map { it.item.id })
    }

    @Test
    fun `another chat's episodes are not this show's`() {
        val seen = video("Harbour.Notes.S01E01.mkv", before, chatId = 1)
        val elsewhere = video("Harbour.Notes.S01E02.mkv", after, chatId = 2)
        assertTrue(fresh(listOf(elsewhere, seen), watched = listOf(watched(seen))).isEmpty())
    }

    @Test
    fun `shows in progress are the most recently watched, films left out, furthest episode kept`() {
        val film = video("Night Train (2019) 1080p.mkv", before)
        val old = video("Old Show S01E01.mkv", before)
        val two = video("Harbour.Notes.S01E02.mkv", before)
        val five = video("Harbour.Notes.S01E05.mkv", before)
        val shows = NewEpisodes.inProgress(
            resume = listOf(resume(film, at = visit + 3)),
            watched = listOf(watched(old, at = visit - 5), watched(five, at = visit), watched(two, at = visit + 2)),
        )
        assertEquals(listOf("Harbour Notes", "Old Show"), shows.map { it.title })
        assertEquals(5, shows.first().episode)
        assertEquals(visit + 2, shows.first().at)
        assertEquals(1, NewEpisodes.inProgress(emptyList(), listOf(watched(old), watched(two)), limit = 1).size)
    }

    @Test
    fun `a show's tile counts its new episodes only once started and only with a time`() {
        val one = video("Harbour.Notes.S01E01.mkv", before)
        val two = video("Harbour.Notes.S01E02.mkv", after)
        val three = video("Harbour.Notes.S01E03.mkv", after)
        val series = (SeriesShelf.arrange(listOf(three, two, one)).single() as ShelfEntry.Show).series
        val times = mapOf(one.id to visit)
        assertEquals(2, NewEpisodes.inSeries(series, { it.id in times }, { times[it.id] }))
        assertEquals(0, NewEpisodes.inSeries(series, { it.id in times }, { null }))
        assertEquals(0, NewEpisodes.inSeries(series, { false }, { times[it.id] }))
    }

    @Test
    fun `Home lists new episodes after Continue watching and Watch later`() {
        val one = video("Harbour.Notes.S01E01.mkv", before)
        val two = video("Harbour.Notes.S01E02.mkv", after)
        val other = video("Lecture notes.mp4", before, chatId = 5)
        val saved = video("Night Train (2019) 1080p.mkv", before, chatId = 6)
        val rows = HomeRows.build(
            continueWatching = listOf(resume(other)),
            favourites = listOf(ChatSummary(1, "Chat", null, 0, ChatKind.Channel)),
            loaded = mapOf(1L to listOf(two, one)),
            recent = null,
            watchLater = listOf(WatchLaterRecord.of(saved, "Films", addedAt = visit)),
            fresh = listOf(NewEpisode(two, "Harbour Notes", "S01E02")),
        )
        assertEquals(listOf("continue", "watch-later", "new-episodes", "chat-1"), rows.map { it.key })
    }
}
