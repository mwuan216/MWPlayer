package com.tmplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sender following the receiver: its state, its clock, a press shown at once, and the receiver's track ids. */
class CastPlaybackTest {

    private fun media(state: ReceiverState, positionMs: Long, atMs: Long, reason: IdleReason? = null, rate: Double = 1.0) =
        ReceiverMedia(state, reason, positionMs, atMs, rate)

    @Test
    fun `the clock runs on only while the tv plays`() {
        val m = CastStateMapper()
        val playing = m.map(media(ReceiverState.Playing, 60_000, atMs = 1_000), loading = false, nowMs = 4_000, targetMs = 0)
        assertEquals(CastPhase.Playing, playing.phase)
        assertEquals(63_000, playing.positionMs)
        assertTrue(playing.showsPause)
        // The stall seen on the stick: the TV paused at 69.7 s; the phone must not count on to 2:00.
        val paused = m.map(media(ReceiverState.Paused, 69_700, atMs = 10_000), loading = false, nowMs = 60_000, targetMs = 0)
        assertEquals(CastPhase.Paused, paused.phase)
        assertEquals(69_700, paused.positionMs)
        assertFalse(paused.showsPause)
    }

    @Test
    fun `buffering keeps the clock still and the pause button up`() {
        val m = CastStateMapper()
        m.map(media(ReceiverState.Playing, 1_000, 0), false, 0, 0)
        val b = m.map(media(ReceiverState.Buffering, 5_000, atMs = 0), loading = false, nowMs = 9_000, targetMs = 0)
        assertEquals(CastPhase.Buffering, b.phase)
        assertEquals(5_000, b.positionMs)
        assertTrue(b.showsPause)
        assertTrue(b.busy)
    }

    @Test
    fun `playback rate scales the run on, and the duration caps it`() {
        val m = CastStateMapper()
        assertEquals(13_000, m.map(media(ReceiverState.Playing, 10_000, 0, rate = 1.5), false, 2_000, 0).positionMs)
        assertEquals(11_000, m.map(media(ReceiverState.Playing, 10_000, 0), false, 5_000, 0, durationMs = 11_000).positionMs)
    }

    @Test
    fun `idle is told apart by its reason`() {
        val m = CastStateMapper()
        m.loaded(nowMs = 0, autoplay = true)
        assertEquals(CastPhase.Ended, m.map(media(ReceiverState.Idle, 0, 0, IdleReason.Finished), false, 20_000, 0).phase)
        assertEquals(CastPhase.Failed, m.map(media(ReceiverState.Idle, 0, 0, IdleReason.Error), false, 20_000, 0).phase)
        assertEquals(CastPhase.Stopped, m.map(media(ReceiverState.Idle, 0, 0, IdleReason.Cancelled), false, 20_000, 0).phase)
        assertEquals(CastPhase.Stopped, m.map(media(ReceiverState.Idle, 0, 0, IdleReason.Interrupted), false, 20_000, 0).phase)
        // Just after a LOAD, an idle with no reason is the load getting going.
        m.loaded(nowMs = 30_000, autoplay = true)
        assertEquals(CastPhase.Loading, m.map(media(ReceiverState.Idle, 0, 30_100), false, 31_000, 0).phase)
    }

    @Test
    fun `a load on its way shows where it starts`() {
        val m = CastStateMapper()
        val v = m.map(media(ReceiverState.Playing, 1_000, 0), loading = true, nowMs = 5_000, targetMs = 42_000)
        assertEquals(CastPhase.Loading, v.phase)
        assertEquals(42_000, v.positionMs)
        assertEquals(CastPhase.Starting, m.map(null, false, 0, 7_000).phase)
    }

    @Test
    fun `a press is shown at once and the receiver has the last word`() {
        val m = CastStateMapper()
        val playing = media(ReceiverState.Playing, 10_000, atMs = 0)
        m.requested(play = false, nowMs = 1_000)
        val pressed = m.map(playing, false, nowMs = 1_100, targetMs = 0)
        assertEquals(CastPhase.Paused, pressed.phase)
        assertFalse(pressed.showsPause)
        // The clock stops with the press.
        assertEquals(10_000, pressed.positionMs)
        // The TV never paused (another phone pressed play): after a while its state stands.
        val later = m.map(playing, false, nowMs = 1_000 + CastStateMapper.INTENT_MS + 1, targetMs = 0)
        assertEquals(CastPhase.Playing, later.phase)
        // Play pressed on a paused TV reads as getting going until it plays.
        m.requested(play = true, nowMs = 10_000)
        val resuming = m.map(media(ReceiverState.Paused, 20_000, 9_000), false, 10_100, 0)
        assertEquals(CastPhase.Buffering, resuming.phase)
        assertTrue(resuming.showsPause)
    }

    @Test
    fun `receiver states and reasons are read from the protocol's words`() {
        assertEquals(ReceiverState.Buffering, ReceiverMedia.state("BUFFERING"))
        assertEquals(ReceiverState.Idle, ReceiverMedia.state(null))
        assertEquals(IdleReason.Cancelled, ReceiverMedia.idleReason("CANCELLED"))
        assertEquals(null, ReceiverMedia.idleReason(""))
    }

    // ---- track ids ---------------------------------------------------------------------------

    private val renditions = listOf(
        HlsRendition(HlsRendition.Kind.Audio, "a2.m3u8", "Japanese", "ja", 2, null, true),
        HlsRendition(HlsRendition.Kind.Audio, "a4.m3u8", "English", "en", 4, null, false),
        HlsRendition(HlsRendition.Kind.Text, "s3.m3u8", "English", "en", 3, null, false),
        HlsRendition(HlsRendition.Kind.Text, "f1000.m3u8", "Mine", null, null, 1000, false),
    )

    @Test
    fun `renditions are found by their playlist where the receiver keeps it`() {
        val remote = listOf(
            RemoteTrack(1, RemoteTrack.Type.Video, null, null, "http://x/t/hls1/v.m3u8"),
            RemoteTrack(2, RemoteTrack.Type.Audio, "", "en", "http://x/t/hls1/a4.m3u8"),
            RemoteTrack(3, RemoteTrack.Type.Audio, "", "ja", "http://x/t/hls1/a2.m3u8"),
            RemoteTrack(4, RemoteTrack.Type.Text, null, "en", "http://x/t/hls1/s3.m3u8"),
            RemoteTrack(5, RemoteTrack.Type.Text, null, null, "http://x/t/hls1/f1000.m3u8"),
        )
        val map = HlsTrackMatch.match(renditions, remote)
        assertEquals(listOf(3L, 2L, 4L, 5L), renditions.map { map[it] })
        assertEquals(listOf(1L, 2L, 5L), HlsTrackMatch.activeIds(map, remote, current = listOf(1, 3), audio = renditions[1], text = renditions[3]))
        // Subtitles off: the picture and the sound only.
        assertEquals(listOf(1L, 3L), HlsTrackMatch.activeIds(map, remote, current = listOf(1, 3, 4), audio = renditions[0], text = null))
    }

    @Test
    fun `without urls the names, the languages and the order find them`() {
        val remote = listOf(
            RemoteTrack(10, RemoteTrack.Type.Audio, "English", "en-US", null),
            RemoteTrack(11, RemoteTrack.Type.Audio, "Japanese", "ja", null),
            RemoteTrack(20, RemoteTrack.Type.Text, null, "eng", null),
            RemoteTrack(21, RemoteTrack.Type.Text, null, null, null),
        )
        val map = HlsTrackMatch.match(renditions, remote)
        assertEquals(listOf(11L, 10L, 20L, 21L), renditions.map { map[it] })
    }

    // ---- the place --------------------------------------------------------------------------

    private fun view(phase: CastPhase, positionMs: Long) = CastView(phase, positionMs, phase == CastPhase.Playing)

    @Test
    fun `a failed tv saying zero does not lose the place, so try again and the fallback start there`() {
        // The device test: playing at 1:23:30, the TV fails and reports 0, the rewrap started at 0.
        val place = CastPlace(25 * 60_000L)
        place.heard(view(CastPhase.Playing, 5_010_000), 1_000)
        assertEquals(5_010_000, place.ms)
        assertEquals(5_010_000, place.heard(view(CastPhase.Failed, 0), 2_000))
        assertEquals(5_010_000, place.heard(view(CastPhase.Stopped, 0), 2_100))
        assertEquals(5_010_000, place.heard(view(CastPhase.Loading, 0), 2_200))
        assertEquals(5_010_000, place.heard(view(CastPhase.Starting, 0), 2_300))
    }

    @Test
    fun `after a load the place is the start asked for until the tv gets there`() {
        val place = CastPlace()
        place.expect(5_010_000, nowMs = 10_000)
        assertTrue(place.settling)
        // A receiver handed a LOAD buffers at 0 before it has seeked to the start.
        assertEquals(5_010_000, place.heard(view(CastPhase.Buffering, 0), 10_500))
        assertEquals(5_010_000, place.heard(view(CastPhase.Playing, 0), 11_000))
        // There at last, a segment's length before the start: taken.
        assertEquals(5_004_000, place.heard(view(CastPhase.Playing, 5_004_000), 12_000))
        assertFalse(place.settling)
        assertEquals(5_006_000, place.heard(view(CastPhase.Playing, 5_006_000), 14_000))
        // A pause on the TV remote is the TV's place too.
        assertEquals(5_007_000, place.heard(view(CastPhase.Paused, 5_007_000), 15_000))
    }

    @Test
    fun `a tv that plays on from somewhere else is believed after a while`() {
        val place = CastPlace()
        place.expect(600_000, nowMs = 0)
        assertEquals(600_000, place.heard(view(CastPhase.Playing, 3_000), 5_000))
        assertEquals(20_000, place.heard(view(CastPhase.Playing, 20_000), CastPlace.GIVE_UP_MS + 1))
    }

    // ---- the soundtrack picker ---------------------------------------------------------------

    /** The device test's file: E-AC-3 5.1 as track 2 (and its AAC copy), AAC 2.0 as track 3. */
    private val dolby = listOf(
        HlsRendition(HlsRendition.Kind.Audio, "a2.m3u8", "DD+ 5.1", "ml", 2, null, true, converted = false),
        HlsRendition(HlsRendition.Kind.Audio, "a2c.m3u8", "DD+ 5.1 (AAC)", "ml", 2, null, true, converted = true),
        HlsRendition(HlsRendition.Kind.Audio, "a3.m3u8", "AAC 2.0", "ml", 3, null, false),
    )

    @Test
    fun `the picker lists only what the tv kept and marks what it plays`() {
        // Shaka dropped the E-AC-3 variant: the receiver lists the AAC copy and the AAC track.
        val remote = listOf(
            RemoteTrack(1, RemoteTrack.Type.Audio, "DD+ 5.1 (AAC)", "ml", null),
            RemoteTrack(2, RemoteTrack.Type.Audio, "AAC 2.0", "ml", null),
        )
        val offers = HlsTrackMatch.audioOffers(dolby, remote, active = listOf(2), wanted = dolby[0])
        assertEquals(listOf("a2c.m3u8", "a3.m3u8"), offers.map { it.rendition.uri })
        assertEquals(listOf(false, true), offers.map { it.selected })
        // The viewer wanted the Dolby track: the copy the TV kept is asked for instead.
        assertEquals(dolby[1], HlsTrackMatch.settle(dolby[0], dolby, remote, active = listOf(2)))
        assertEquals(dolby[1], HlsTrackMatch.renditionFor(2, dolby, remote))
        assertEquals(dolby[2], HlsTrackMatch.playing(dolby, remote, listOf(2)))
    }

    @Test
    fun `where the tv takes dolby the track is offered once, as it is`() {
        val remote = listOf(
            RemoteTrack(1, RemoteTrack.Type.Audio, "DD+ 5.1", "ml", null),
            RemoteTrack(2, RemoteTrack.Type.Audio, "DD+ 5.1 (AAC)", "ml", null),
            RemoteTrack(3, RemoteTrack.Type.Audio, "AAC 2.0", "ml", null),
        )
        // Shaka started on the stereo copy; the picker offers track 2 once, marked, by the copy that is on.
        val offers = HlsTrackMatch.audioOffers(dolby, remote, active = listOf(2), wanted = dolby[0])
        assertEquals(listOf("a2c.m3u8", "a3.m3u8"), offers.map { it.rendition.uri })
        assertEquals(listOf(true, false), offers.map { it.selected })
        assertEquals(dolby[0], HlsTrackMatch.settle(dolby[0], dolby, remote, active = listOf(2)))
        assertEquals(dolby[0], HlsTrackMatch.renditionFor(2, dolby, remote))
    }

    @Test
    fun `a tv that kept nothing it was asked for shows the track it plays`() {
        val only = dolby.filter { !it.converted }
        val remote = listOf(RemoteTrack(1, RemoteTrack.Type.Audio, "AAC 2.0", "ml", null))
        assertEquals(only[1], HlsTrackMatch.settle(only[0], only, remote, active = listOf(1)))
        val offers = HlsTrackMatch.audioOffers(only, remote, listOf(1), only[0])
        assertEquals(listOf("a3.m3u8"), offers.map { it.rendition.uri })
        assertTrue(offers.single().selected)
        // Before the receiver's list is in, everything, the wanted one marked.
        assertEquals(listOf(true, false), HlsTrackMatch.audioOffers(only, emptyList(), emptyList(), only[0]).map { it.selected })
    }

    @Test
    fun `a soundtrack not found keeps the one on`() {
        val remote = listOf(RemoteTrack(7, RemoteTrack.Type.Audio, "x", null, null), RemoteTrack(8, RemoteTrack.Type.Audio, "y", null, null), RemoteTrack(9, RemoteTrack.Type.Audio, "z", null, null))
        val map = HlsTrackMatch.match(renditions.take(2), remote)
        assertTrue(map.isEmpty())
        assertEquals(listOf(8L), HlsTrackMatch.activeIds(map, remote, current = listOf(8), audio = renditions[1], text = null))
    }
}
