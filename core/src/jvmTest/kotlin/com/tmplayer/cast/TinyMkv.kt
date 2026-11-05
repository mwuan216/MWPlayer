package com.tmplayer.cast

import java.nio.ByteBuffer

/**
 * A small Matroska file for the HLS tests: H.264 with B-frames (stored out of presentation order),
 * AAC in 21 ms frames, a second AAC soundtrack, and SubRip and ASS subtitle tracks in BlockGroups
 * with durations; [clusters] clusters of [clusterMs], a cue at each.
 *
 * Every frame of the picture carries its own presentation time in its first two bytes (in frames
 * of 40 ms) and the sound frames carry theirs in milliseconds, so a test can tell which landed where.
 */
internal object TinyMkv {

    const val FRAME_MS = 40
    const val AUDIO_MS = 21

    class Subtitle(val track: Int, val startMs: Long, val durationMs: Long, val text: String)

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

    fun el(id: Long, vararg body: ByteArray): ByteArray {
        val b = body.fold(ByteArray(0)) { a, x -> a + x }
        return id(id) + size(b.size.toLong()) + b
    }

    private fun uint(id: Long, v: Long) = el(id, ByteBuffer.allocate(8).putLong(v).array())
    private fun str(id: Long, s: String) = el(id, s.toByteArray())
    private fun float(id: Long, v: Double) = el(id, ByteBuffer.allocate(8).putDouble(v).array())

    private fun blockBody(track: Int, relative: Int, flags: Int, data: ByteArray) =
        byteArrayOf((0x80 or track).toByte(), (relative shr 8).toByte(), relative.toByte(), flags.toByte()) + data

    private fun simpleBlock(track: Int, relative: Int, key: Boolean, data: ByteArray): ByteArray =
        el(Matroska.SIMPLE_BLOCK, blockBody(track, relative, if (key) 0x80 else 0, data))

    private fun blockGroup(track: Int, relative: Int, durationMs: Long, data: ByteArray): ByteArray =
        el(Matroska.BLOCK_GROUP, el(Matroska.BLOCK, blockBody(track, relative, 0, data)), uint(Matroska.BLOCK_DURATION, durationMs))

    val avcC = byteArrayOf(1, 0x64, 0, 0x1F, 0xFF.toByte(), 0xE1.toByte(), 0, 4, 0x67, 0x64, 0, 0x1F, 1, 0, 4, 0x68, 0xEE.toByte(), 0x3C, 0x80.toByte())
    private val asc = byteArrayOf(0x11, 0x90.toByte())

    /** A picture frame whose first two bytes say its presentation time, in frames. */
    fun videoFrame(ptsFrames: Int) = ByteArray(100).also {
        it[0] = (ptsFrames shr 8).toByte()
        it[1] = ptsFrames.toByte()
    }

    fun ptsOf(frame: ByteArray): Int = ((frame[0].toInt() and 0xFF) shl 8) or (frame[1].toInt() and 0xFF)

    fun audioFrame(ms: Int, track: Int) = ByteArray(20).also {
        it[0] = (ms shr 8).toByte()
        it[1] = ms.toByte()
        it[2] = track.toByte()
    }

    fun msOf(frame: ByteArray): Int = ((frame[0].toInt() and 0xFF) shl 8) or (frame[1].toInt() and 0xFF)

    fun file(
        clusters: Int = 4,
        clusterMs: Int = 2000,
        subtitles: List<Subtitle> = emptyList(),
        withCues: Boolean = true,
    ): ByteArray {
        val perCluster = clusterMs / FRAME_MS
        val clusterBytes = ArrayList<ByteArray>()
        for (c in 0 until clusters) {
            val start = c * clusterMs
            val base = c * perCluster
            val blocks = ArrayList<ByteArray>()
            // Decode order I P B B P B B ... with presentation I=0, P=3, B=1, B=2 (closed GOPs).
            var n = 0
            while (n < perCluster) {
                if (n == 0) {
                    blocks += simpleBlock(1, 0, true, videoFrame(base))
                    n++
                    continue
                }
                val p = n + 2
                if (p < perCluster) blocks += simpleBlock(1, p * FRAME_MS, false, videoFrame(base + p))
                blocks += simpleBlock(1, n * FRAME_MS, false, videoFrame(base + n))
                if (n + 1 < perCluster) blocks += simpleBlock(1, (n + 1) * FRAME_MS, false, videoFrame(base + n + 1))
                n += 3
            }
            var t = 0
            while (t < clusterMs) {
                blocks += simpleBlock(2, t, true, audioFrame(start + t, 2))
                blocks += simpleBlock(4, t, true, audioFrame(start + t, 4))
                t += AUDIO_MS
            }
            for (s in subtitles.filter { it.startMs >= start && it.startMs < start + clusterMs }) {
                val data = if (s.track == 5) "0,0,Default,,0,0,0,,{\\i1}${s.text}{\\i0}".toByteArray() else s.text.toByteArray()
                blocks += blockGroup(s.track, (s.startMs - start).toInt(), s.durationMs, data)
            }
            clusterBytes += el(Matroska.CLUSTER, uint(Matroska.TIMECODE, start.toLong()), *blocks.toTypedArray())
        }
        val info = el(Matroska.INFO, uint(Matroska.TIMECODE_SCALE, 1_000_000), float(Matroska.DURATION, (clusters * clusterMs).toDouble()))
        val tracks = el(
            Matroska.TRACKS,
            el(
                Matroska.TRACK_ENTRY,
                uint(Matroska.TRACK_NUMBER, 1), uint(Matroska.TRACK_TYPE, 1), str(Matroska.CODEC_ID, "V_MPEG4/ISO/AVC"),
                el(Matroska.CODEC_PRIVATE, avcC), uint(Matroska.DEFAULT_DURATION, FRAME_MS * 1_000_000L),
                el(Matroska.VIDEO, uint(Matroska.PIXEL_WIDTH, 1280), uint(Matroska.PIXEL_HEIGHT, 720)),
            ),
            el(
                Matroska.TRACK_ENTRY,
                uint(Matroska.TRACK_NUMBER, 2), uint(Matroska.TRACK_TYPE, 2), str(Matroska.CODEC_ID, "A_AAC"),
                el(Matroska.CODEC_PRIVATE, asc), str(Matroska.LANGUAGE, "jpn"), str(Matroska.NAME, "Japanese"),
                el(Matroska.AUDIO, float(Matroska.SAMPLING_FREQUENCY, 48000.0), uint(Matroska.CHANNELS, 2)),
            ),
            el(
                Matroska.TRACK_ENTRY,
                uint(Matroska.TRACK_NUMBER, 4), uint(Matroska.TRACK_TYPE, 2), str(Matroska.CODEC_ID, "A_AAC"),
                el(Matroska.CODEC_PRIVATE, asc), str(Matroska.LANGUAGE, "eng"), uint(Matroska.FLAG_DEFAULT, 0),
                el(Matroska.AUDIO, float(Matroska.SAMPLING_FREQUENCY, 48000.0), uint(Matroska.CHANNELS, 2)),
            ),
            el(
                Matroska.TRACK_ENTRY,
                uint(Matroska.TRACK_NUMBER, 3), uint(Matroska.TRACK_TYPE, 17), str(Matroska.CODEC_ID, "S_TEXT/UTF8"), str(Matroska.LANGUAGE, "eng"),
            ),
            el(
                Matroska.TRACK_ENTRY,
                uint(Matroska.TRACK_NUMBER, 5), uint(Matroska.TRACK_TYPE, 17), str(Matroska.CODEC_ID, "S_TEXT/ASS"), str(Matroska.LANGUAGE, "fre"), str(Matroska.NAME, "Signs"),
            ),
        )
        val headPart = info + tracks
        val offsets = ArrayList<Long>()
        fun build(shift: Long): Pair<ByteArray, ByteArray> {
            var at = headPart.size.toLong()
            offsets.clear()
            for (c in clusterBytes) {
                offsets += at
                at += c.size
            }
            val cues = el(
                Matroska.CUES,
                *offsets.mapIndexed { i, pos ->
                    el(Matroska.CUE_POINT, uint(Matroska.CUE_TIME, i.toLong() * clusterMs), el(Matroska.CUE_TRACK_POSITIONS, uint(0xF7, 1), uint(Matroska.CUE_CLUSTER_POSITION, pos + shift)))
                }.toTypedArray(),
            )
            val seekHead = el(Matroska.SEEK_HEAD, el(Matroska.SEEK, el(Matroska.SEEK_ID, id(Matroska.CUES)), uint(Matroska.SEEK_POSITION, at + shift)))
            return seekHead to cues
        }
        val segmentBody = if (withCues) {
            val (probeHead, _) = build(0)
            val (seekHead, cues) = build(probeHead.size.toLong())
            check(seekHead.size == probeHead.size)
            arrayOf(seekHead, headPart, *clusterBytes.toTypedArray(), cues)
        } else {
            arrayOf(headPart, *clusterBytes.toTypedArray())
        }
        val segment = el(Matroska.SEGMENT, *segmentBody)
        val ebml = el(Matroska.EBML, str(Matroska.DOC_TYPE, "matroska"))
        return ebml + segment
    }
}
