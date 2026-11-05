package com.tmplayer.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * The Buy Me a Coffee window and its notification: real use first (five finished videos, or three
 * days with one), never the first day, then one window every 14 days at most; a notification at
 * most every 30 days and never within a week of a window; "Don't show again" and the old
 * "I already support" for good, the old ladder's last promise kept, counters that never count a
 * re-open within the hour twice, and the one switch that silences it all.
 */
class SupportReminderTest {

    private val dir = Files.createTempDirectory("tm-support").toFile()
    private val settings = SettingsStore(SettingsStore.openDataStore(dir.resolve(SettingsStore.FILE_NAME)))

    private val day = 24L * 60 * 60 * 1000
    private val hour = 60L * 60 * 1000
    private val start = 1_800_000_000_000L

    @Before
    fun fresh() {
        SupportReminder.resetSession()
    }

    @After
    fun reset() {
        SupportReminder.enabled = true
        SupportReminder.forcedRung = 0
        SupportReminder.resetSession()
    }

    private fun counters(
        days: Int = 20,
        watches: Int = 9,
        asked: Int = 0,
        snoozedUntil: Long = 0L,
        never: Boolean = false,
        supporter: Boolean = false,
        askedAt: Long = 0L,
        nudgedAt: Long = 0L,
    ) = SupportReminder.Counters(
        firstSeenAt = start - days * day,
        completedWatches = watches,
        asked = asked,
        snoozedUntil = snoozedUntil,
        neverAgain = never,
        supporter = supporter,
        askedAt = askedAt,
        nudgedAt = nudgedAt,
    )

    @Test
    fun `the first window waits for five finished videos or three days of use`() {
        // Five videos on the second day: real use.
        assertTrue(SupportReminder.due(counters(days = 1, watches = 5), start))
        assertFalse(SupportReminder.due(counters(days = 2, watches = 0), start))
        // Three days with at least one video watched to the end.
        assertTrue(SupportReminder.due(counters(days = 3, watches = 1), start))
        assertFalse(SupportReminder.due(counters(days = 2, watches = 4), start))
        // Three days of the app open and nothing watched is not use.
        assertFalse(SupportReminder.due(counters(days = 30, watches = 0), start))
        // Never counted at all: no first day, no window.
        assertFalse(SupportReminder.due(SupportReminder.Counters(completedWatches = 99), start))
    }

    @Test
    fun `never on the first day, however much was watched`() {
        val firstLaunch = SupportReminder.Counters(firstSeenAt = start - 2 * hour, completedWatches = 40)
        assertFalse(SupportReminder.due(firstLaunch, start))
        assertFalse(SupportReminder.realUse(firstLaunch, start))
        assertTrue(SupportReminder.due(firstLaunch, start - 2 * hour + day))
    }

    @Test
    fun `later windows wait fourteen days each, for as long as it takes`() {
        val shownAt = start
        val after = counters(days = 60, snoozedUntil = SupportReminder.snoozedUntil(shownAt), askedAt = shownAt)
        assertEquals(shownAt + 14 * day, after.snoozedUntil)
        assertFalse(SupportReminder.due(after, shownAt + 14 * day - 1))
        assertTrue(SupportReminder.due(after, shownAt + 14 * day))
    }

    @Test
    fun `don't show again, i already support and the old ladder's end are permanent`() {
        assertFalse(SupportReminder.due(counters(days = 400, watches = 400, supporter = true), start))
        assertFalse(SupportReminder.due(counters(days = 400, watches = 400, never = true), start))
        // The ladder's third card said it was the last ask.
        assertFalse(SupportReminder.due(counters(days = 900, asked = 3), start + 900 * day))
        // One or two cards of the ladder seen: the window takes over.
        assertTrue(SupportReminder.due(counters(days = 900, asked = 2), start + 900 * day))
        assertFalse(SupportReminder.nudgeDue(counters(days = 400, never = true), start, switchOn = true))
        assertFalse(SupportReminder.nudgeDue(counters(days = 400, asked = 3), start, switchOn = true))
    }

    @Test
    fun `the switch turns it off, even when forced`() {
        SupportReminder.forcedRung = 2
        assertTrue(SupportReminder.due(SupportReminder.Counters(), start))
        SupportReminder.enabled = false
        assertFalse(SupportReminder.due(SupportReminder.Counters(), start))
        assertFalse(SupportReminder.due(counters(days = 100, watches = 100), start))
        assertFalse(SupportReminder.nudgeDue(counters(days = 100, watches = 100), start, switchOn = true))
    }

    @Test
    fun `never twice in one session`() {
        assertTrue(SupportReminder.due(counters(), start))
        assertFalse(SupportReminder.due(counters(), start, sessionShown = true))
    }

    @Test
    fun `the notification waits for real use, thirty days between, a week after a window`() {
        assertFalse(SupportReminder.nudgeDue(counters(days = 2, watches = 0), start, switchOn = true))
        assertTrue(SupportReminder.nudgeDue(counters(), start, switchOn = true))
        // The Sponsor reminders switch off.
        assertFalse(SupportReminder.nudgeDue(counters(), start, switchOn = false))
        // Thirty days after the last one, not a moment sooner.
        assertFalse(SupportReminder.nudgeDue(counters(nudgedAt = start - 30 * day + 1), start, switchOn = true))
        assertTrue(SupportReminder.nudgeDue(counters(nudgedAt = start - 30 * day), start, switchOn = true))
        // Not hard on the heels of the window.
        assertFalse(SupportReminder.nudgeDue(counters(askedAt = start - 6 * day), start, switchOn = true))
        assertTrue(SupportReminder.nudgeDue(counters(askedAt = start - 7 * day), start, switchOn = true))
    }

    @Test
    fun `a walk with a fake clock`() = runBlocking {
        settings.setSponsorReminders(true)
        settings.noteSupportFirstSeen(start)
        // The first day: four finished videos, nothing yet.
        repeat(4) { settings.noteSupportCompleted("1_$it", start + it * 2 * hour) }
        assertFalse(SupportReminder.claim(settings, start + 20 * hour))
        // The fifth on day 2: the window. Shown means asked, and the next waits 14 days.
        settings.noteSupportCompleted("1_9", start + day + hour)
        val first = start + day + 2 * hour
        assertTrue(SupportReminder.claim(settings, first))
        val after = settings.supportCountersNow()
        assertEquals(1, after.windows)
        assertEquals(first, after.askedAt)
        assertEquals(first + 14 * day, after.snoozedUntil)
        // Not again in the same run of the app, whatever the day.
        assertFalse(SupportReminder.claim(settings, first + 60 * day))

        // A new run inside the gap: no. Fourteen days on: the second window.
        SupportReminder.resetSession()
        assertFalse(SupportReminder.claim(settings, first + 14 * day - 1))
        assertTrue(SupportReminder.claim(settings, first + 14 * day))
        assertEquals(2, settings.supportCountersNow().windows)

        // The notification: not within a week of that window, then once, then not for 30 days.
        val second = first + 14 * day
        assertFalse(SupportReminder.claimNudge(settings, second + 6 * day))
        assertTrue(SupportReminder.claimNudge(settings, second + 7 * day))
        assertFalse(SupportReminder.claimNudge(settings, second + 7 * day + 29 * day))
        assertTrue(SupportReminder.claimNudge(settings, second + 7 * day + 30 * day))

        // Don't show again: nothing more of either, ever.
        settings.markSupportNever()
        SupportReminder.resetSession()
        assertFalse(SupportReminder.claim(settings, second + 2000 * day))
        assertFalse(SupportReminder.claimNudge(settings, second + 2000 * day))
    }

    @Test
    fun `the sponsor reminders switch is on by default and kept through sign out`() = runBlocking {
        assertTrue(settings.sponsorRemindersNow())
        settings.setSponsorReminders(true)
        settings.clearEverything()
        assertTrue(settings.sponsorRemindersNow())
        settings.setSponsorReminders(false)
        settings.clearEverything()
        assertFalse(settings.sponsorRemindersNow())
        assertFalse(SupportReminder.claimNudge(settings, start + 400 * day))
    }

    @Test
    fun `i already support stops it for good and survives sign out`() = runBlocking {
        settings.noteSupportFirstSeen(start)
        repeat(6) { settings.noteSupportCompleted("1_$it", start + it * 2 * hour) }
        assertTrue(SupportReminder.claim(settings, start + 8 * day))
        settings.markSupporter()
        settings.clearEverything()
        val kept = settings.supportCountersNow()
        assertTrue(kept.supporter)
        assertEquals(start, kept.firstSeenAt)
        assertEquals(6, kept.completedWatches)
        assertEquals(1, kept.windows)
        assertEquals(start + 8 * day, kept.askedAt)
        SupportReminder.resetSession()
        assertFalse(SupportReminder.claim(settings, start + 1000 * day))
    }

    @Test
    fun `a forced window leaves the counters alone`() = runBlocking {
        SupportReminder.forcedRung = 3
        assertTrue(SupportReminder.claim(settings, start))
        assertTrue(SupportReminder.claim(settings, start + 1))
        assertEquals(SupportReminder.Counters(), settings.supportCountersNow())
    }

    @Test
    fun `a press on the notification is one open request`() {
        assertFalse(SupportReminder.takeOpenRequest())
        SupportReminder.requestOpen()
        assertTrue(SupportReminder.openRequested.value)
        assertTrue(SupportReminder.takeOpenRequest())
        assertFalse(SupportReminder.takeOpenRequest())
    }

    @Test
    fun `a finished video counts once within the hour`() = runBlocking {
        settings.noteSupportCompleted("5_9", start)
        // Re-opened and finished again 30 minutes later, and again after 59: still one.
        settings.noteSupportCompleted("5_9", start + hour / 2)
        settings.noteSupportCompleted("5_9", start + hour - 1)
        assertEquals(1, settings.supportCountersNow().completedWatches)
        // An hour after the first finish it is a new watch.
        settings.noteSupportCompleted("5_9", start + hour)
        assertEquals(2, settings.supportCountersNow().completedWatches)
        // Another video is its own count.
        settings.noteSupportCompleted("5_10", start + hour + 1)
        assertEquals(3, settings.supportCountersNow().completedWatches)
    }

    @Test
    fun `watch time follows the position and ignores seeks, pauses and reopens`() = runBlocking {
        val key = SettingsStore.progressKey(7, 42)
        val minute = 60_000L
        // A first report only starts the clock: no time yet.
        settings.saveResumePosition(7, 42, 10 * minute, 60 * minute)
        assertEquals(0L, settings.supportCountersNow().watchTimeMs)
        // Ten seconds of playing, reported ten seconds later.
        settings.saveResumePosition(7, 42, 10 * minute + 10_000, 60 * minute)
        assertTrue(settings.supportCountersNow().watchTimeMs < 1_000L)
        // The pure step: wall clock bounds what a report may add.
        var recent = emptyList<SupportReminder.Recent>()
        var added = 0L
        recent = SupportReminder.stepTime(recent, key, 10 * minute, start).first
        SupportReminder.stepTime(recent, key, 10 * minute + 10_000, start + 10_000).let { (r, ms) -> recent = r; added += ms }
        assertEquals(10_000L, added)
        // A jump forward of 30 minutes (a seek) in 10 seconds of wall time adds only 10 seconds.
        SupportReminder.stepTime(recent, key, 40 * minute, start + 20_000).let { (r, ms) -> recent = r; added += ms }
        assertEquals(20_000L, added)
        // Backwards (a seek back) adds nothing.
        SupportReminder.stepTime(recent, key, 5 * minute, start + 30_000).let { (r, ms) -> recent = r; added += ms }
        assertEquals(20_000L, added)
        // After the video is finished, a re-open within the hour adds no time at all.
        recent = SupportReminder.stepCompleted(recent, key, start + 40_000).first
        SupportReminder.stepTime(recent, key, 6 * minute, start + 50_000).let { (r, ms) -> recent = r; added += ms }
        assertEquals(20_000L, added)
        // More than a minute of wall time still adds at most a minute.
        SupportReminder.stepTime(emptyList(), key, 0L, start).first.let {
            assertEquals(60_000L, SupportReminder.stepTime(it, key, 30 * minute, start + 40 * 60_000L).second)
        }
    }

    @Test
    fun `the recent list survives its own encoding`() {
        val list = listOf(
            SupportReminder.Recent("1_2", 5L, 6L, true),
            SupportReminder.Recent("-3_4", 7L, 0L, false),
        )
        assertEquals(list, SupportReminder.decodeRecent(SupportReminder.encodeRecent(list)))
        assertEquals(emptyList<SupportReminder.Recent>(), SupportReminder.decodeRecent(null))
        assertEquals(emptyList<SupportReminder.Recent>(), SupportReminder.decodeRecent("garbage"))
    }

    @Test
    fun `hours are rounded and never below one`() {
        assertEquals(1, SupportReminder.hoursWatched(SupportReminder.Counters(watchTimeMs = 10 * 60_000L)))
        assertEquals(3, SupportReminder.hoursWatched(SupportReminder.Counters(watchTimeMs = 2 * hour + 40 * 60_000L)))
    }

    @Test
    fun `a good moment is taken once and goes stale`() {
        SupportReminder.watchFinished("1_1", now = start)
        assertTrue(SupportReminder.takeMoment(start + 1000))
        assertFalse(SupportReminder.takeMoment(start + 2000))
        SupportReminder.downloadFinished(now = start)
        assertFalse(SupportReminder.takeMoment(start + SupportReminder.MOMENT_LIFETIME_MS + 1))
    }

    @Test
    fun `the watched list reports a finished watch, a manual mark does not`() = runBlocking {
        val watched = WatchedStore(WatchedStore.openDataStore(dir.resolve("watched.preferences_pb")))
        val record = WatchedRecord(1, 2, 3, "Film", "Chat", 100, 60, start, manual = false)
        SupportReminder.resetSession()
        watched.markWatched(record.copy(manual = true))
        assertFalse(SupportReminder.takeMoment(System.currentTimeMillis()))
        watched.markWatched(record)
        assertTrue(SupportReminder.takeMoment(System.currentTimeMillis()))
    }
}
