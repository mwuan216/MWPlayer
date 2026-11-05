package com.tmplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.nio.ByteBuffer

/**
 * Matroska served as HLS: the segments cut at the cues, the playlists, every segment made on its
 * own from the cluster of its cue, the soundtracks and subtitles as renditions, and the server
 * answering for the folder.
 */
class MkvHlsTest {

    private val subtitles = listOf(
        TinyMkv.Subtitle(3, 1_000, 2_000, "Hello <i>there</i>"),
        TinyMkv.Subtitle(3, 4_500, 1_500, "Second & last"),
        TinyMkv.Subtitle(5, 9_000, 1_000, "A sign"),
    )

    /** Six clusters of two seconds, cut at 3.5 s: segments [0, 4), [4, 8), [8, 12). */
    private fun hls(
        audio: List<HlsAudio> = listOf(HlsAudio(2, false), HlsAudio(4, false)),
        default: Int? = 2,
        files: List<HlsSubtitleFile> = emptyList(),
    ): MkvHls {
        val source = BytesCastSource(TinyMkv.file(clusters = 6, subtitles = subtitles))
        val probe = MediaProbe.of(source, "video.mkv")
        return MkvHls.prepare(source, probe, audio, default, files, targetNs = 3_500_000_000L)
    }

    private fun bytes(write: (ByteArrayOutputStream) -> Unit) = ByteArrayOutputStream().also(write).toByteArray()

    // ---- the cut --------------------------------------------------------------------------

    /**
     * The cues of a real file (mkvmerge's, one per keyframe, from an episode with scene cuts):
     * irregular, some a fraction of a second apart, some ten seconds.
     */
    private val realCueMs = listOf(
        0, 2_002, 4_004, 6_006, 8_008, 9_843, 11_845, 12_721, 14_723, 16_725, 18_727, 20_729, 21_396,
        23_398, 25_400, 35_410, 37_412, 38_079, 40_081, 42_083, 44_085, 46_087, 48_089, 50_091, 50_758,
    )

    @Test
    fun `segments are cut at the cues, about six seconds each, end to end`() {
        val cues = realCueMs.mapIndexed { i, ms -> MkvCue(ms * 1_000_000L, 1000L + i * 100_000L) }
        val durationNs = 52_093_000_000L
        val segments = HlsSegments.cut(cues, durationNs, firstCluster = 1000L)
        assertEquals(0L, segments.first().startNs)
        assertEquals(durationNs, segments.last().endNs)
        for (i in 1 until segments.size) {
            assertEquals(segments[i - 1].endNs, segments[i].startNs)
            // Every cut is at a cue, and starts from that cue's cluster.
            val cue = cues.first { it.timeNs == segments[i].startNs }
            assertEquals(cue.clusterPosition, segments[i].clusterPosition)
        }
        // Every segment but the last reaches the target; none runs past it by more than a cue gap.
        for (s in segments.dropLast(1)) {
            assertTrue("$s", s.durationNs >= HlsSegments.TARGET_NS)
            assertTrue("$s", s.durationNs <= HlsSegments.TARGET_NS + 10_010_000_000L)
        }
        assertEquals(listOf(0, 6_006, 12_721, 18_727, 25_400, 35_410, 42_083, 48_089), segments.map { (it.startNs / 1_000_000).toInt() })
    }

    @Test
    fun `a short last stretch joins the segment before it`() {
        val cues = listOf(0L, 6_000L, 12_000L).mapIndexed { i, ms -> MkvCue(ms * 1_000_000L, 100L * i) }
        val segments = HlsSegments.cut(cues, 13_000_000_000L, 0)
        assertEquals(2, segments.size)
        assertEquals(13_000_000_000L, segments.last().endNs)
    }

    // ---- playlists -------------------------------------------------------------------------

    @Test
    fun `media playlists are vod, end, and give every segment its length`() {
        val h = hls()
        val v = h.videoPlaylist()
        assertTrue(v.startsWith("#EXTM3U\n"))
        assertTrue("#EXT-X-PLAYLIST-TYPE:VOD" in v)
        assertTrue(v.trimEnd().endsWith("#EXT-X-ENDLIST"))
        assertTrue("#EXT-X-MAP:URI=\"v-init.mp4\"" in v)
        assertEquals(listOf("v-0.m4s", "v-1.m4s", "v-2.m4s"), v.lines().filter { it.endsWith(".m4s") })
        val extinf = v.lines().filter { it.startsWith("#EXTINF:") }.map { it.removePrefix("#EXTINF:").trimEnd(',').toDouble() }
        assertEquals(listOf(4.0, 4.0, 4.0), extinf)
        val target = v.lines().first { it.startsWith("#EXT-X-TARGETDURATION:") }.substringAfter(':').toInt()
        assertTrue(extinf.all { Math.round(it) <= target })
        assertEquals(12_000L, h.durationMs)
        assertTrue("#EXT-X-MAP:URI=\"a4-init.mp4\"" in h.audioPlaylist(4))
        val s = h.textPlaylist("s3")
        assertFalse("#EXT-X-MAP" in s)
        assertEquals(listOf("s3-0.vtt", "s3-1.vtt", "s3-2.vtt"), s.lines().filter { it.endsWith(".vtt") })
    }

    @Test
    fun `the master names every soundtrack and subtitle track, the default marked`() {
        val file = HlsSubtitleFile(1000, "Loaded subtitles", null, listOf(WebVtt.Cue(500, 1500, "From a file")))
        val h = hls(default = 4, files = listOf(file))
        val m = h.master()
        val audio = m.lines().filter { it.startsWith("#EXT-X-MEDIA:TYPE=AUDIO") }
        assertEquals(2, audio.size)
        assertTrue(audio[0], "NAME=\"Japanese\"" in audio[0] && "LANGUAGE=\"ja\"" in audio[0] && "DEFAULT=NO" in audio[0] && "URI=\"a2.m3u8\"" in audio[0])
        assertTrue(audio[1], "NAME=\"English\"" in audio[1] && "LANGUAGE=\"en\"" in audio[1] && "DEFAULT=YES" in audio[1])
        val subs = m.lines().filter { it.startsWith("#EXT-X-MEDIA:TYPE=SUBTITLES") }
        assertEquals(3, subs.size)
        assertTrue(subs[0], "NAME=\"English\"" in subs[0] && "URI=\"s3.m3u8\"" in subs[0])
        assertTrue(subs[1], "NAME=\"Signs\"" in subs[1] && "LANGUAGE=\"fr\"" in subs[1])
        assertTrue(subs[2], "NAME=\"Loaded subtitles\"" in subs[2] && "URI=\"f1000.m3u8\"" in subs[2])
        val inf = m.lines().filter { it.startsWith("#EXT-X-STREAM-INF:") }
        // Both soundtracks are AAC: one variant.
        assertEquals(1, inf.size)
        assertTrue(inf[0], "CODECS=\"avc1.64001F,mp4a.40.2\"" in inf[0])
        assertTrue(inf[0], "RESOLUTION=1280x720" in inf[0] && "AUDIO=\"aud0\"" in inf[0] && "SUBTITLES=\"subs\"" in inf[0])
        assertEquals("v.m3u8", m.lines()[m.lines().indexOf(inf[0]) + 1])
        assertEquals(listOf("a2.m3u8", "a4.m3u8", "s3.m3u8", "s5.m3u8", "f1000.m3u8"), h.renditions.map { it.uri })
        assertEquals(4, h.renditions.single { it.isDefault }.trackNumber)
    }

    // ---- the picture -----------------------------------------------------------------------

    @Test
    fun `video segments start on a keyframe and together hold every frame once, the times running on`() {
        val h = hls()
        val all = ArrayList<Fmp4Samples.Sample>()
        for (i in h.segments.indices) {
            val samples = Fmp4Samples.read(bytes { h.writeVideoSegment(i, it) })
            assertTrue(samples.first().sync)
            // The first frame shown is the segment's start, at 90 kHz.
            assertEquals(h.segments[i].startNs * 90 / 1_000_000, samples.minOf { it.pts })
            if (all.isNotEmpty()) assertEquals(all.last().dts + all.last().duration, samples.first().dts)
            all += samples
        }
        assertEquals((0 until 300).toList(), all.map { TinyMkv.ptsOf(it.data) }.sorted())
        // Decode times rise, and each frame is shown at its own time.
        assertTrue(all.zipWithNext().all { (a, b) -> b.dts > a.dts })
        for (s in all) assertEquals(TinyMkv.ptsOf(s.data) * 3600L, s.pts)
        assertEquals(6, all.count { it.sync })
    }

    @Test
    fun `a segment in the middle is made on its own from its cue`() {
        val h = hls()
        val samples = Fmp4Samples.read(bytes { h.writeVideoSegment(1, it) })
        assertEquals(100, samples.size)
        assertEquals(100, TinyMkv.ptsOf(samples.first().data))
        assertEquals(100 * 3600L, samples.first().dts)
        val init = h.videoInit()
        val moov = Fmp4Samples.boxes(init).first { it.type == "moov" }
        val mvhd = Fmp4Samples.child(init, moov, "mvhd")!!
        assertEquals(12_000, ByteBuffer.wrap(init, mvhd.start + 16, 4).int)
    }

    // ---- the sound -------------------------------------------------------------------------

    @Test
    fun `audio segments hold the frames that start inside them, each soundtrack its own`() {
        val h = hls()
        for (track in listOf(2, 4)) {
            val all = ArrayList<Int>()
            for ((i, seg) in h.segments.withIndex()) {
                val bytes = bytes { h.writeAudioSegment(track, i, it) }
                val samples = Fmp4Samples.read(bytes)
                val ms = samples.map { TinyMkv.msOf(it.data) }
                assertTrue(samples.all { it.data[2].toInt() == track })
                assertTrue(ms.all { it >= seg.startNs / 1_000_000 && it < seg.endNs / 1_000_000 })
                assertEquals(ms.first() * 48L, samples.first().dts)
                all += ms
            }
            val expected = (0 until 6).flatMap { c -> (0 until 2000 step TinyMkv.AUDIO_MS).map { c * 2000 + it } }
            assertEquals(expected, all)
        }
        assertNotNull(h.audioInit(2))
        assertNull(h.audioInit(7))
    }

    // ---- subtitles -------------------------------------------------------------------------

    @Test
    fun `subtitle tracks inside the file become webvtt segments at their own times`() {
        val h = hls()
        val first = h.textSegment("s3", 0)
        // RFC 8216's form, with colons: Shaka fails the whole video on a map it cannot read.
        assertTrue(first, first.startsWith("WEBVTT\nX-TIMESTAMP-MAP=MPEGTS:0,LOCAL:00:00:00.000\n\n"))
        assertTrue(Regex("""MPEGTS:(\d+)""").containsMatchIn(first) && Regex("""LOCAL:((?:(\d+):)?(\d{2}):(\d{2})\.(\d{3}))""").containsMatchIn(first))
        assertTrue(first, "00:00:01.000 --> 00:00:03.000\nHello <i>there</i>" in first)
        assertFalse("Second" in first)
        val second = h.textSegment("s3", 1)
        assertTrue(second, "00:00:04.500 --> 00:00:06.000\nSecond &amp; last" in second)
        assertFalse("A sign" in h.textSegment("s5", 1))
        val sign = h.textSegment("s5", 2)
        // ASS: the fields before the text and the override blocks go.
        assertTrue(sign, "00:00:09.000 --> 00:00:10.000\nA sign\n" in sign)
        assertFalse(sign, "Default" in sign || "\\i1" in sign)
    }

    @Test
    fun `a loaded subtitle file is cut into the same segments`() {
        val cues = WebVtt.cues("1\n00:00:01,000 --> 00:00:02,000\nOne\n\n2\n00:00:05,000 --> 00:00:06,000\nTwo\n".toByteArray(), "srt")
        val h = hls(files = listOf(HlsSubtitleFile(1000, "Mine", "en", cues)))
        assertTrue("One" in h.textSegment("f1000", 0) && "Two" !in h.textSegment("f1000", 0))
        assertTrue("00:00:05.000 --> 00:00:06.000\nTwo" in h.textSegment("f1000", 1))
    }

    @Test
    fun `webvtt files are read back as cues`() {
        val cues = WebVtt.parseVtt("WEBVTT\n\n01:02.500 --> 01:04.000 align:start\nShort hours\n\n00:01:10.000 --> 00:01:11.000\nTwo\nlines\n")
        assertEquals(listOf(WebVtt.Cue(62_500, 64_000, "Short hours"), WebVtt.Cue(70_000, 71_000, "Two\nlines")), cues)
    }

    // ---- the folder ------------------------------------------------------------------------

    @Test
    fun `paths resolve to playlists and segments and nothing else`() {
        val h = hls()
        assertTrue(h.resolve("master.m3u8") is CastResource.Text)
        assertTrue(h.resolve("v-2.m4s") is CastResource.Chunked)
        assertTrue(h.resolve("a4-1.m4s") is CastResource.Chunked)
        assertTrue(h.resolve("a2-init.mp4") is CastResource.Text)
        assertTrue(h.resolve("s5-0.vtt") is CastResource.Text)
        assertNull(h.resolve("v-3.m4s"))
        assertNull(h.resolve("a3-0.m4s"))
        assertNull(h.resolve("s2.m3u8"))
        assertNull(h.resolve("../secret"))
    }

    @Test
    fun `the server answers an hls folder with chunked segments over one connection`() {
        val h = hls()
        val server = CastServer(InetAddress.getLoopbackAddress(), allowed = { true })
        server.start()
        try {
            server.publish("hls1", CastResource.Tree(h::resolve))
            val base = server.urlFor("hls1")
            val master = URL("$base/master.m3u8").readText()
            assertTrue(master.startsWith("#EXTM3U"))
            val c = URL("$base/v-1.m4s").openConnection() as HttpURLConnection
            assertEquals(200, c.responseCode)
            assertEquals("chunked", c.getHeaderField("Transfer-Encoding"))
            val body = c.inputStream.readBytes()
            assertTrue(body.contentEquals(bytes { h.writeVideoSegment(1, it) }))
            assertEquals(404, (URL("$base/v-9.m4s").openConnection() as HttpURLConnection).responseCode)
            assertEquals(404, (URL(server.urlFor("hls1")).openConnection() as HttpURLConnection).responseCode)
        } finally {
            server.stop()
        }
    }

    @Test(expected = IOException::class)
    fun `a file without cues is not cut into segments`() {
        val source = BytesCastSource(TinyMkv.file(clusters = 6, withCues = false))
        MkvHls.prepare(source, MediaProbe.of(source, "video.mkv"), listOf(HlsAudio(2, false)), 2, targetNs = 3_500_000_000L)
    }

    @Test
    fun `codec names come from the configuration records`() {
        // HEVC Main, level 4.0 (120), as x265 writes it.
        val hvcC = byteArrayOf(1, 0x01, 0x60, 0, 0, 0, 0x90.toByte(), 0, 0, 0, 0, 0, 120)
        assertEquals("hev1.1.6.L120.90", Mp4Entries.hevcCodecs(hvcC))
        // Main 10, high tier.
        val main10 = byteArrayOf(1, 0x22, 0x20, 0, 0, 0, 0x90.toByte(), 0, 0, 0, 0, 0, 153.toByte())
        assertEquals("hev1.2.4.H153.90", Mp4Entries.hevcCodecs(main10))
        assertEquals("av01.0.08M.10", Mp4Entries.av1Codecs(byteArrayOf(0x81.toByte(), 0x08, 0x4C), 0))
    }

    // ---- a soundtrack sent twice -----------------------------------------------------------

    /** A converter that only says what it would make: enough for the playlists and the init segments. */
    private class FakeConverter : AudioConverter {
        override fun start(track: AudioTrack) = AacOutput(48_000, 2, byteArrayOf(0x11, 0x90.toByte()))
        override fun convert(frame: ByteArray, ptsUs: Long) = emptyList<ConvertedAudio>()
        override fun finish() = emptyList<ConvertedAudio>()
        override fun close() = Unit
    }

    @Test
    fun `a track sent as it is and as aac is two renditions, each the default of its own codec's group`() {
        val source = BytesCastSource(TinyMkv.file(clusters = 6, subtitles = subtitles))
        val probe = MediaProbe.of(source, "video.mkv")
        val h = MkvHls.prepare(
            source, probe, listOf(HlsAudio(2, false), HlsAudio(2, true), HlsAudio(4, false)), 2,
            converterFactory = { FakeConverter() }, targetNs = 3_500_000_000L,
        )
        val audio = h.renditions.filter { it.kind == HlsRendition.Kind.Audio }
        assertEquals(listOf("a2.m3u8", "a2c.m3u8", "a4.m3u8"), audio.map { it.uri })
        assertEquals(listOf(false, true, false), audio.map { it.converted })
        assertEquals(listOf(true, true, false), audio.map { it.isDefault })
        // Names stay unique, so the receiver's list can be matched back by them.
        assertEquals(audio.size, audio.map { it.name }.toSet().size)
        assertTrue(audio[1].name, audio[1].name.endsWith("(AAC)"))
        assertNotNull(h.resolve("a2c.m3u8"))
        assertTrue("#EXT-X-MAP:URI=\"a2c-init.mp4\"" in h.audioPlaylist("2c"))
        assertNotNull(h.resolve("a2c-init.mp4"))
        assertNotNull(h.resolve("a2c-0.m4s"))
        assertNull(h.resolve("a3c.m3u8"))
        assertNull(h.resolve("a4c.m3u8"))
    }

    @Test
    fun `without subtitles the master names no subtitle group`() {
        val source = BytesCastSource(TinyMkv.file(clusters = 6, subtitles = subtitles))
        val probe = MediaProbe.of(source, "video.mkv")
        val h = MkvHls.prepare(source, probe, listOf(HlsAudio(2, false)), 2, targetNs = 3_500_000_000L, subtitles = false)
        assertTrue(h.renditions.none { it.kind == HlsRendition.Kind.Text })
        val master = h.master()
        assertFalse(master, "SUBTITLES" in master)
        assertNull(h.resolve("s3.m3u8"))
    }
}
