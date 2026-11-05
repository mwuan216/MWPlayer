package com.tmplayer.data

import com.tmplayer.i18n.L
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * The notifications that help (CP76): when each one fires and when it stays quiet. Finished
 * downloads grouped by season and by the pieces of a cut video, new episodes of shows under way
 * (once each, past the furthest watched, never from a chat the kids lock hides), low storage once
 * per low spell, the missing piece of a cut video arriving, the update said once per version
 * while the app is away, the support ask's star rung before money, and the switches' defaults.
 */
class NoticeRulesTest {

    private val dir = Files.createTempDirectory("tm-notices").toFile()
    private val settings = SettingsStore(SettingsStore.openDataStore(dir.resolve(SettingsStore.FILE_NAME)))
    private val day = 24L * 60 * 60 * 1000
    private val start = 1_800_000_000_000L

    @Before
    fun fresh() {
        DownloadResults.reset()
        SupportReminder.resetSession()
    }

    @After
    fun reset() {
        DownloadResults.reset()
        SupportReminder.enabled = true
        Updates.enabled = true
    }

    private fun request(id: Int, fileName: String, title: String = fileName, chat: Long = 1L) = DownloadRequest(
        fileId = id, title = title, sizeBytes = 100, chatId = chat, messageId = id.toLong() * 10,
        chatTitle = "Video Club", durationSec = 0, mimeType = "video/x-matroska", fileName = fileName,
    )

    private fun video(chat: Long, id: Long, fileName: String, size: Long = 500_000_000L) = MediaItem(
        chatId = chat, messageId = id, fileId = id.toInt(), title = fileName.substringBeforeLast('.'), sizeBytes = size,
        durationSec = 0, mimeType = "video/x-matroska", thumbnailFileId = 0, miniThumbnail = null, date = 0, fileName = fileName,
    )

    // ---- finished downloads ----------------------------------------------------------------------

    @Test
    fun `a lone download is said at once, with Play, and a failure with Try again`() {
        val one = request(1, "Harbour.Notes.2026.1080p.mkv", title = "Harbour Notes")
        val result = DownloadResults.record(one, ok = true, outstanding = emptyList())!!
        val words = DownloadResults.words(result, private = false)
        assertEquals("Harbour Notes", words.title)
        assertEquals(one, words.play)
        assertTrue(words.retry.isEmpty())

        val two = request(2, "Night.Train.mkv", title = "Night Train")
        val failed = DownloadResults.words(DownloadResults.record(two, ok = false, outstanding = emptyList())!!, private = false, reason = "No room")
        assertEquals("No room", failed.body)
        assertNull(failed.play)
        assertEquals(listOf(two), failed.retry)
    }

    @Test
    fun `a season is one notification once its last episode settles`() {
        val episodes = (1..3).map { request(it, "Harbour.Notes.S02E0$it.1080p.mkv") }
        val other = request(9, "Night.Train.mkv")
        // Two still to come, and an unrelated video on the queue that does not hold it up.
        assertNull(DownloadResults.record(episodes[1], ok = true, outstanding = listOf(episodes[0], episodes[2], other)))
        assertNull(DownloadResults.record(episodes[0], ok = false, outstanding = listOf(episodes[2], other)))
        val result = DownloadResults.record(episodes[2], ok = true, outstanding = listOf(other))!!
        assertEquals(DownloadResults.Kind.Season, result.group.kind)
        assertEquals(2, result.done.size)
        assertEquals(1, result.failed.size)
        val words = DownloadResults.words(result, private = false)
        assertEquals(L.notifySeasonTitle(season = 2, show = "Harbour Notes"), words.title)
        assertEquals(L.notifySeasonPartial(done = 2, total = 3), words.body)
        // Play opens the earliest episode that is here; Try again asks for the one that is not.
        assertEquals(episodes[1], words.play)
        assertEquals(listOf(episodes[0]), words.retry)
    }

    @Test
    fun `the pieces of a cut video are one notification, never one per piece`() {
        val pieces = (1..3).map { request(it, "Night.Train.2025.mkv.00$it") }
        assertNull(DownloadResults.record(pieces[0], ok = true, outstanding = pieces.drop(1)))
        assertNull(DownloadResults.record(pieces[2], ok = true, outstanding = listOf(pieces[1])))
        val result = DownloadResults.record(pieces[1], ok = true, outstanding = emptyList())!!
        assertEquals(DownloadResults.Kind.Pieces, result.group.kind)
        val words = DownloadResults.words(result, private = false)
        assertEquals(L.notifyPiecesDone, words.body)
        assertEquals(pieces[0], words.play)
    }

    @Test
    fun `a hidden chat or the app lock names nothing`() {
        val one = request(1, "Harbour.Notes.mkv", chat = 7L)
        val result = DownloadResults.record(one, ok = true, outstanding = emptyList())!!
        assertTrue(DownloadResults.isPrivate(7L, allowed = setOf(1L), AppLockSettings()))
        assertFalse(DownloadResults.isPrivate(7L, allowed = null, AppLockSettings()))
        assertTrue(DownloadResults.isPrivate(7L, allowed = null, AppLockSettings(pinSet = true, onOpen = true)))
        val words = DownloadResults.words(result, private = true)
        assertEquals(L.notifyDownloadDonePrivate, words.title)
        assertNull(words.body)
        assertNull(words.play)
    }

    // ---- new episodes ----------------------------------------------------------------------------

    private val watching = listOf(
        EpisodeAlerts.Watching(1L, "Harbour.Notes.S01E04.1080p.mkv", "Harbour Notes", null),
        EpisodeAlerts.Watching(1L, "Harbour.Notes.S01E02.1080p.mkv", "Harbour Notes", null),
    )

    @Test
    fun `a show under way is the furthest episode watched in each season`() {
        val show = EpisodeAlerts.shows(watching).getValue("harbour notes")
        assertEquals(mapOf(1 to 4), show.furthest)
        assertTrue(show.isNext(1, 5))
        assertFalse(show.isNext(1, 3))
        assertTrue(show.isNext(2, 1))
    }

    @Test
    fun `the next episode is news once, in whichever chat posts it`() {
        val shows = EpisodeAlerts.shows(watching)
        val titles = mapOf(1L to "Video Club", 2L to "Weekend series")
        // First look: marks only.
        val first = EpisodeAlerts.diff(listOf(1L, 2L), shows, emptyMap(), mapOf(1L to listOf(video(1, 10, "Harbour.Notes.S01E04.mkv"))), titles, emptySet())
        assertTrue(first.found.isEmpty())
        val latest = mapOf(
            1L to listOf(video(1, 11, "Harbour.Notes.S01E05.mkv"), video(1, 12, "Harbour.Notes.S01E03.mkv"), video(1, 13, "Night.Train.mkv")),
            2L to listOf(video(2, 31, "Harbour.Notes.S01E05.720p.mkv")),
        )
        val diff = EpisodeAlerts.diff(listOf(1L, 2L), shows, mapOf(1L to 10L, 2L to 30L), latest, titles, emptySet())
        val found = diff.found.single()
        assertEquals(5, found.episode)
        assertEquals("Video Club", found.chat)
        val alert = EpisodeAlerts.alert(diff.found, private = false)!!
        assertEquals(L.notifyEpisodeNew(episode = 5, show = "Harbour Notes", chat = "Video Club"), alert.title)
        assertEquals(11L, alert.play?.messageId)
        // Announced before: quiet the next time it turns up.
        val again = EpisodeAlerts.diff(listOf(2L), shows, mapOf(2L to 30L), latest, titles, setOf(EpisodeAlerts.toldKey("harbour notes", 1, 5)))
        assertTrue(again.found.isEmpty())
    }

    @Test
    fun `the app lock hides the names and Play`() {
        val shows = EpisodeAlerts.shows(watching)
        val diff = EpisodeAlerts.diff(listOf(1L), shows, mapOf(1L to 1L), mapOf(1L to listOf(video(1, 11, "Harbour.Notes.S01E05.mkv"))), emptyMap(), emptySet())
        val alert = EpisodeAlerts.alert(diff.found, private = true)!!
        assertEquals(L.notifyEpisodesPrivate, alert.title)
        assertNull(alert.play)
    }

    @Test
    fun `the episode switch is on by default and turning it on again starts afresh`() = runBlocking {
        assertTrue(settings.noticeOnNow(Notice.NewEpisodes))
        settings.setNoticeOn(Notice.NewEpisodes, false)
        settings.setEpisodeSeen(mapOf(1L to 5L))
        settings.setNoticeOn(Notice.NewEpisodes, true)
        assertTrue(settings.noticeOnNow(Notice.NewEpisodes))
        assertTrue(settings.episodeSeenNow().isEmpty())
    }

    // ---- low storage -----------------------------------------------------------------------------

    @Test
    fun `low storage is said once per low spell, and again after it recovers`() {
        val gb = 1024L * 1024 * 1024
        assertEquals(StorageAlerts.Reason.DeviceLow, StorageAlerts.reason(freeBytes = gb / 2, totalBytes = 64 * gb, cacheBytes = 0))
        assertEquals(StorageAlerts.Reason.CacheOver, StorageAlerts.reason(freeBytes = 30 * gb, totalBytes = 64 * gb, cacheBytes = 5 * gb))
        assertNull(StorageAlerts.reason(freeBytes = 30 * gb, totalBytes = 64 * gb, cacheBytes = gb))
        assertNull(StorageAlerts.reason(freeBytes = 0, totalBytes = 0, cacheBytes = 0))

        var state = StorageAlerts.State()
        val low = StorageAlerts.Reason.DeviceLow
        var (post, next) = StorageAlerts.decide(low, state, start)
        assertTrue(post)
        state = next
        // Still low, days later: quiet.
        StorageAlerts.decide(low, state, start + 10 * day).let { (p, n) -> assertFalse(p); state = n }
        // Recovered, then low again within the gap: quiet; after it: said.
        StorageAlerts.decide(null, state, start + day).let { (p, n) -> assertFalse(p); state = n }
        StorageAlerts.decide(low, state, start + 2 * day).let { (p, n) -> assertFalse(p); state = n }
        StorageAlerts.decide(low, state, start + 3 * day).let { (p, n) -> assertTrue(p); state = n }
        assertEquals(state, StorageAlerts.State.decode(state.encode()))
    }

    // ---- missing pieces --------------------------------------------------------------------------

    @Test
    fun `a missing piece arriving is said, and a complete video forgotten`() {
        val p1 = video(1, 1, "Night.Train.mkv.001")
        val p2 = video(1, 2, "Night.Train.mkv.002")
        val p3 = video(1, 3, "Night.Train.mkv.003")
        val p4 = video(1, 4, "Night.Train.mkv.004")
        val gaps = PieceAlerts.remember(emptyList(), listOf(p1, p3, p4), "Video Club", start)
        val gap = gaps.single()
        assertEquals(listOf(2), gap.missing)
        assertEquals(gaps, PieceAlerts.decode(PieceAlerts.encode(gaps)))
        // Nothing new: no notification, still remembered.
        val (quiet, kept) = PieceAlerts.compare(gap, listOf(p1, p3, p4), private = false, now = start + day)
        assertNull(quiet)
        assertNotNull(kept)
        // The piece is here: said, with Play, and forgotten.
        val (alert, after) = PieceAlerts.compare(gap, listOf(p1, p2, p3, p4), private = false, now = start + 2 * day)
        assertEquals(L.notifyPiecesComplete(title = gap.title, chat = "Video Club"), alert!!.title)
        assertEquals(1L, alert.play?.messageId)
        assertNull(after)
        // A video played whole is forgotten too.
        assertTrue(PieceAlerts.remember(gaps, listOf(p1, p2, p3, p4), "Video Club", start).isEmpty())
    }

    @Test
    fun `a missing piece names nothing behind the app lock`() {
        val gap = PieceAlerts.remember(emptyList(), listOf(video(1, 1, "A.mkv.001"), video(1, 3, "A.mkv.003")), "Club", start).single()
        val (alert, _) = PieceAlerts.compare(gap, listOf(video(1, 1, "A.mkv.001"), video(1, 2, "A.mkv.002"), video(1, 3, "A.mkv.003")), private = true, now = start)
        assertEquals(L.notifyPiecesPrivate, alert!!.title)
        assertNull(alert.play)
    }

    // ---- update ready ----------------------------------------------------------------------------

    @Test
    fun `an update is said once per version, only while the app is away`() {
        val release = Release("9.9.9", "https://example.org")
        val available = UpdateState.Available(release)
        assertEquals(release, UpdateAlerts.due(available, alertedFor = "", skipped = "", switchOn = true, inFront = false))
        assertNull(UpdateAlerts.due(available, alertedFor = "", skipped = "", switchOn = true, inFront = true))
        assertNull(UpdateAlerts.due(available, alertedFor = "9.9.9", skipped = "", switchOn = true, inFront = false))
        assertNull(UpdateAlerts.due(available, alertedFor = "", skipped = "9.9.9", switchOn = true, inFront = false))
        assertNull(UpdateAlerts.due(available, alertedFor = "", skipped = "", switchOn = false, inFront = false))
        assertNull(UpdateAlerts.due(UpdateState.Idle, alertedFor = "", skipped = "", switchOn = true, inFront = false))
    }

    // ---- the support ask -------------------------------------------------------------------------

    @Test
    fun `the support ask asks for a star first, then a coffee, and never a star once given`() = runBlocking {
        settings.setSponsorReminders(true)
        settings.noteSupportFirstSeen(start)
        repeat(6) { settings.noteSupportCompleted("1_$it", start + it * 1000L) }
        val first = start + 10 * day
        assertEquals(SupportReminder.NudgeRung.Star, SupportReminder.claimNudgeRung(settings, first))
        // The gap holds between rungs.
        assertNull(SupportReminder.claimNudgeRung(settings, first + 29 * day))
        assertEquals(SupportReminder.NudgeRung.Coffee, SupportReminder.claimNudgeRung(settings, first + 30 * day))
        assertTrue(SupportReminder.offersStar(settings.supportCountersNow(), SupportReminder.NudgeRung.Coffee))
        settings.markSupportStarred()
        assertFalse(SupportReminder.offersStar(settings.supportCountersNow(), SupportReminder.NudgeRung.Coffee))
        // A star given survives sign out.
        settings.clearEverything()
        assertTrue(settings.supportCountersNow().starred)
        assertEquals(SupportReminder.NudgeRung.Coffee, SupportReminder.nudgeRung(settings.supportCountersNow()))
        // Don't ask again: nothing more.
        settings.markSupportNever()
        assertNull(SupportReminder.claimNudgeRung(settings, first + 400 * day))
    }

    @Test
    fun `a starred viewer never meets the star rung`() {
        val counters = SupportReminder.Counters(firstSeenAt = start, completedWatches = 9, starred = true)
        assertEquals(SupportReminder.NudgeRung.Coffee, SupportReminder.nudgeRung(counters))
    }

    @Test
    fun `every switch is on by default but new videos in starred chats`() = runBlocking {
        for (notice in Notice.entries) {
            assertEquals(notice.name, notice != Notice.NewVideos, settings.noticeOnNow(notice))
        }
        settings.setNoticeOn(Notice.Downloads, false)
        assertFalse(settings.noticeOnNow(Notice.Downloads))
        settings.setNoticeOn(Notice.Support, true)
        assertTrue(settings.sponsorRemindersNow())
    }
}
