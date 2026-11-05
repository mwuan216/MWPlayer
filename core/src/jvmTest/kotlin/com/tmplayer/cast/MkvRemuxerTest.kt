package com.tmplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * The Matroska reader and the fragmented MP4 it is rewrapped into, over a small file built here:
 * H.264 with B-frames (the frames stored out of presentation order) and AAC, four clusters of two
 * seconds, cues at each.
 */
class MkvRemuxerTest {

    // ---- a tiny Matroska writer ------------------------------------------------------------

    private fun id(v: Long): ByteArray {
        val n = when {
            v < 0x100 -> 1
            v < 0x10000 -> 2
            v < 0x1000000 -> 3
            else -> 4
        }
        return ByteArray(n) { i -> (v shr (8 * (n - 1 - i))).toByte() }
    }

    private fun size(v: Long): ByteArray = ByteBuffer.allocate(8).putLong(v or (1L shl 56)).array()

    private fun el(id: Long, vararg body: ByteArray): ByteArray {
        val b = body.fold(ByteArray(0)) { a, x -> a + x }
        return id(id) + size(b.size.toLong()) + b
    }

    private fun uint(id: Long, v: Long) = el(id, ByteBuffer.allocate(8).putLong(v).array())
    private fun str(id: Long, s: String) = el(id, s.toByteArray())
    private fun float(id: Long, v: Double) = el(id, ByteBuffer.allocate(8).putDouble(v).array())

    private fun simpleBlock(track: Int, relative: Int, key: Boolean, data: ByteArray): ByteArray =
        el(Matroska.SIMPLE_BLOCK, byteArrayOf((0x80 or track).toByte(), (relative shr 8).toByte(), relative.toByte(), (if (key) 0x80 else 0).toByte()), data)

    private val avcC = byteArrayOf(1, 0x64, 0, 0x1F, 0xFF.toByte(), 0xE1.toByte(), 0, 4, 0x67, 0x64, 0, 0x1F, 1, 0, 4, 0x68, 0xEE.toByte(), 0x3C, 0x80.toByte())
    private val asc = byteArrayOf(0x11, 0x90.toByte())

    /** Frame n of the picture, so a test can tell which landed where. */
    private fun frame(n: Int) = ByteArray(100) { n.toByte() }

    private fun file(): ByteArray {
        val frameMs = 40 // 25 fps
        val clusters = ArrayList<ByteArray>()
        val clusterOffsets = ArrayList<Long>()
        for (c in 0 until 4) {
            val start = c * 2000L
            val blocks = ArrayList<ByteArray>()
            // Decode order I P B B P B B ... with presentation times I=0, P=3, B=1, B=2.
            var n = 0
            while (n < 50) {
                if (n == 0) {
                    blocks += simpleBlock(1, 0, true, frame(c * 50))
                    n++
                    continue
                }
                val p = n + 2
                if (p < 50) blocks += simpleBlock(1, p * frameMs, false, frame(c * 50 + p))
                blocks += simpleBlock(1, n * frameMs, false, frame(c * 50 + n))
                if (n + 1 < 50) blocks += simpleBlock(1, (n + 1) * frameMs, false, frame(c * 50 + n + 1))
                n += 3
            }
            // Sound: 21 ms AAC frames over the cluster.
            var t = 0
            while (t < 2000) {
                blocks += simpleBlock(2, t, true, ByteArray(20) { 0x55 })
                t += 21
            }
            clusters += el(Matroska.CLUSTER, uint(Matroska.TIMECODE, start), *blocks.toTypedArray())
        }
        val info = el(Matroska.INFO, uint(Matroska.TIMECODE_SCALE, 1_000_000), float(Matroska.DURATION, 8000.0))
        val tracks = el(
            Matroska.TRACKS,
            el(
                Matroska.TRACK_ENTRY,
                uint(Matroska.TRACK_NUMBER, 1), uint(Matroska.TRACK_TYPE, 1), str(Matroska.CODEC_ID, "V_MPEG4/ISO/AVC"),
                el(Matroska.CODEC_PRIVATE, avcC), uint(Matroska.DEFAULT_DURATION, 40_000_000),
                el(Matroska.VIDEO, uint(Matroska.PIXEL_WIDTH, 1280), uint(Matroska.PIXEL_HEIGHT, 720)),
            ),
            el(
                Matroska.TRACK_ENTRY,
                uint(Matroska.TRACK_NUMBER, 2), uint(Matroska.TRACK_TYPE, 2), str(Matroska.CODEC_ID, "A_AAC"),
                el(Matroska.CODEC_PRIVATE, asc), str(Matroska.LANGUAGE, "jpn"),
                el(Matroska.AUDIO, float(Matroska.SAMPLING_FREQUENCY, 48000.0), uint(Matroska.CHANNELS, 2)),
            ),
            el(
                Matroska.TRACK_ENTRY,
                uint(Matroska.TRACK_NUMBER, 3), uint(Matroska.TRACK_TYPE, 17), str(Matroska.CODEC_ID, "S_TEXT/ASS"), str(Matroska.LANGUAGE, "eng"),
            ),
        )
        // Segment children: Info, Tracks, the clusters, then Cues pointing back at them.
        val headPart = info + tracks
        var at = headPart.size.toLong()
        for (c in clusters) {
            clusterOffsets += at
            at += c.size
        }
        val cues = el(
            Matroska.CUES,
            *clusterOffsets.mapIndexed { i, pos ->
                el(Matroska.CUE_POINT, uint(Matroska.CUE_TIME, i * 2000L), el(Matroska.CUE_TRACK_POSITIONS, uint(0xF7, 1), uint(Matroska.CUE_CLUSTER_POSITION, pos)))
            }.toTypedArray(),
        )
        val cuesAt = at
        val seekHead = el(Matroska.SEEK_HEAD, el(Matroska.SEEK, el(Matroska.SEEK_ID, id(Matroska.CUES)), uint(Matroska.SEEK_POSITION, cuesAt + 0)))
        // The SeekHead goes first, so every offset after it moves by its size.
        val shift = seekHead.size.toLong()
        val seekHeadFixed = el(Matroska.SEEK_HEAD, el(Matroska.SEEK, el(Matroska.SEEK_ID, id(Matroska.CUES)), uint(Matroska.SEEK_POSITION, cuesAt + shift)))
        val cuesFixed = el(
            Matroska.CUES,
            *clusterOffsets.mapIndexed { i, pos ->
                el(Matroska.CUE_POINT, uint(Matroska.CUE_TIME, i * 2000L), el(Matroska.CUE_TRACK_POSITIONS, uint(0xF7, 1), uint(Matroska.CUE_CLUSTER_POSITION, pos + shift)))
            }.toTypedArray(),
        )
        check(seekHeadFixed.size == seekHead.size && cuesFixed.size == cues.size)
        val segment = el(Matroska.SEGMENT, seekHeadFixed, headPart, *clusters.toTypedArray(), cuesFixed)
        val ebml = el(Matroska.EBML, str(Matroska.DOC_TYPE, "matroska"))
        return ebml + segment
    }

    // ---- reading the MP4 back --------------------------------------------------------------

    private class Box(val type: String, val start: Int, val end: Int)

    private fun boxes(b: ByteArray, from: Int = 0, to: Int = b.size): List<Box> =
        Mp4Probe.children(b, from, to).map { Box(it.first, it.second, it.third) }

    private fun child(b: ByteArray, parent: Box, type: String, skip: Int = 0) = boxes(b, parent.start + skip, parent.end).first { it.type == type }

    private fun remux(startMs: Long = 0): ByteArray {
        val source = BytesCastSource(file())
        val probe = MediaProbe.of(source, "video.mkv")
        val out = ByteArrayOutputStream()
        MkvRemuxer(source, probe, audioNumber = 2).write(startMs, out)
        return out.toByteArray()
    }

    @Test
    fun `probes the tracks of a matroska file`() {
        val probe = MediaProbe.of(BytesCastSource(file()), "video.mkv")
        assertEquals(Container.Matroska, probe.container)
        assertEquals(8000L, probe.durationMs)
        assertEquals(Codecs.H264, probe.video!!.codec)
        assertEquals(1280, probe.video!!.width)
        assertEquals(listOf(Codecs.AAC), probe.audio.map { it.codec })
        assertEquals("jpn", probe.audio[0].language)
        assertEquals(48000, probe.audio[0].sampleRate)
        assertEquals(Codecs.ASS, probe.text[0].codec)
        assertTrue(probe.text[0].isText)
    }

    @Test
    fun `writes an init segment with one picture and one sound track`() {
        val mp4 = remux()
        val top = boxes(mp4)
        assertEquals("ftyp", top[0].type)
        assertEquals("moov", top[1].type)
        val moov = top[1]
        val traks = boxes(mp4, moov.start, moov.end).filter { it.type == "trak" }
        assertEquals(2, traks.size)
        val entries = traks.map { trak ->
            val mdia = child(mp4, trak, "mdia")
            val minf = child(mp4, mdia, "minf")
            val stbl = child(mp4, minf, "stbl")
            val stsd = child(mp4, stbl, "stsd")
            boxes(mp4, stsd.start + 8, stsd.end).first().type
        }
        assertEquals(listOf("avc1", "mp4a"), entries)
        assertTrue(boxes(mp4, moov.start, moov.end).any { it.type == "mvex" })
        // And the probe reads its own output as MP4 with the same codecs.
        val probe = Mp4Probe.parseMoov(mp4.copyOfRange(moov.start, moov.end))
        assertEquals(Codecs.H264, probe.video!!.codec)
        assertEquals(Codecs.AAC, probe.audio.single().codec)
        assertEquals(8000L, probe.durationMs)
    }

    @Test
    fun `fragments follow keyframes and keep every frame`() {
        val mp4 = remux()
        val moofs = boxes(mp4).filter { it.type == "moof" }
        assertEquals(4, moofs.size)
        assertEquals(4, boxes(mp4).count { it.type == "mdat" })
        var videoSamples = 0
        var audioSamples = 0
        for (moof in moofs) {
            for (traf in boxes(mp4, moof.start, moof.end).filter { it.type == "traf" }) {
                val tfhd = child(mp4, traf, "tfhd")
                val track = ByteBuffer.wrap(mp4, tfhd.start + 4, 4).int
                val trun = child(mp4, traf, "trun")
                val count = ByteBuffer.wrap(mp4, trun.start + 4, 4).int
                if (track == 1) videoSamples += count else audioSamples += count
            }
        }
        assertEquals(200, videoSamples)
        assertEquals(4 * 96, audioSamples)
    }

    @Test
    fun `decode times run in order and composition offsets restore the presentation order`() {
        val mp4 = remux()
        val moof = boxes(mp4).first { it.type == "moof" }
        val traf = boxes(mp4, moof.start, moof.end).first { it.type == "traf" }
        val tfdt = child(mp4, traf, "tfdt")
        assertEquals(0L, ByteBuffer.wrap(mp4, tfdt.start + 4, 8).long)
        val trun = child(mp4, traf, "trun")
        val count = ByteBuffer.wrap(mp4, trun.start + 4, 4).int
        var p = trun.start + 12
        var dts = 0L
        val pts = ArrayList<Long>()
        repeat(count) {
            val duration = ByteBuffer.wrap(mp4, p, 4).int.toLong()
            val cts = ByteBuffer.wrap(mp4, p + 12, 4).int.toLong()
            pts += dts + cts
            assertEquals(3600L, duration) // 40 ms at 90 kHz
            dts += duration
            p += 16
        }
        // In decode order: I0, P3, B1, B2, P6, B4, B5 ..., shown one frame (the reorder depth) late.
        assertEquals(listOf(0L, 3L, 1L, 2L, 6L, 4L, 5L).map { (it + 1) * 3600 }, pts.take(7))
        assertTrue(pts.indices.all { i -> pts[i] >= i * 3600L })
    }

    @Test
    fun `a seek starts at the cue before it with time from zero`() {
        val mp4 = remux(startMs = 5000)
        val moofs = boxes(mp4).filter { it.type == "moof" }
        // Cue at 4 s: the third and fourth clusters.
        assertEquals(2, moofs.size)
        val mdat = boxes(mp4).first { it.type == "mdat" }
        // The first video sample is frame 100, the keyframe of the third cluster.
        assertEquals(100.toByte(), mp4[mdat.start])
        val source = BytesCastSource(file())
        val remuxer = MkvRemuxer(source, MediaProbe.of(source, "video.mkv"), 2)
        assertEquals(4000L, remuxer.actualStartMs(5000))
        val mvhd = child(mp4, boxes(mp4).first { it.type == "moov" }, "mvhd")
        assertEquals(4000L, ByteBuffer.wrap(mp4, mvhd.start + 16, 4).int.toLong())
    }

    @Test
    fun `the picture alone when no soundtrack is asked for`() {
        val source = BytesCastSource(file())
        val out = ByteArrayOutputStream()
        MkvRemuxer(source, MediaProbe.of(source, "video.mkv"), audioNumber = null).write(0, out)
        val mp4 = out.toByteArray()
        val moov = boxes(mp4).first { it.type == "moov" }
        assertEquals(1, boxes(mp4, moov.start, moov.end).count { it.type == "trak" })
    }

    @Test
    fun `ac3 and eac3 boxes are read from the first frame`() {
        // A 48 kHz, 384 kb/s, 5.1 AC-3 syncframe header: fscod 0, frmsizecod 0x18, bsid 8, acmod 7, lfe.
        val ac3 = byteArrayOf(0x0B, 0x77, 0, 0, 0x18, 0x40, 0xE1.toByte(), 0xF0.toByte())
        val dac3 = AudioFrames.dac3(ac3)!!
        val r = AudioFrames.BitReader(dac3, 0)
        assertEquals(0, r.bits(2))
        assertEquals(8, r.bits(5))
        assertEquals(0, r.bits(3))
        assertEquals(7, r.bits(3))
        assertEquals(1, r.bits(1))
        assertEquals(0x18 shr 1, r.bits(5))
        assertEquals(1536, AudioFrames.eac3Samples(byteArrayOf(0x0B, 0x77, 0x00, 0xFF.toByte(), 0x3F, 0x70)))
    }

    @Test
    fun `opus packet lengths come from the toc`() {
        assertEquals(960, AudioFrames.opusSamples(byteArrayOf((31 shl 3).toByte())))
        assertEquals(1920, AudioFrames.opusSamples(byteArrayOf(((31 shl 3) or 1).toByte())))
    }
}
