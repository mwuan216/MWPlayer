package com.tmplayer.cast

import java.nio.ByteBuffer

/** The samples of fragmented MP4 read back, for the tests: decode and presentation times, flags, bytes. */
internal object Fmp4Samples {

    class Sample(val track: Int, val dts: Long, val duration: Long, val cts: Long, val sync: Boolean, val data: ByteArray) {
        val pts: Long get() = dts + cts
    }

    class Box(val type: String, val start: Int, val end: Int) {
        val boxStart: Int get() = start - 8
    }

    fun boxes(b: ByteArray, from: Int = 0, to: Int = b.size): List<Box> =
        Mp4Probe.children(b, from, to).map { Box(it.first, it.second, it.third) }

    fun child(b: ByteArray, parent: Box, type: String): Box? = boxes(b, parent.start, parent.end).firstOrNull { it.type == type }

    fun read(b: ByteArray): List<Sample> {
        val out = ArrayList<Sample>()
        for (moof in boxes(b).filter { it.type == "moof" }) {
            for (traf in boxes(b, moof.start, moof.end).filter { it.type == "traf" }) {
                val tfhd = child(b, traf, "tfhd")!!
                val track = ByteBuffer.wrap(b, tfhd.start + 4, 4).int
                val tfdt = child(b, traf, "tfdt")!!
                var dts = ByteBuffer.wrap(b, tfdt.start + 4, 8).long
                val trun = child(b, traf, "trun")!!
                val flags = ByteBuffer.wrap(b, trun.start, 4).int and 0xFFFFFF
                val count = ByteBuffer.wrap(b, trun.start + 4, 4).int
                var p = trun.start + 8
                var dataAt = moof.boxStart
                if (flags and 0x1 != 0) {
                    dataAt += ByteBuffer.wrap(b, p, 4).int
                    p += 4
                }
                repeat(count) {
                    val duration = ByteBuffer.wrap(b, p, 4).int.toLong() and 0xFFFFFFFFL
                    val size = ByteBuffer.wrap(b, p + 4, 4).int
                    val sampleFlags = ByteBuffer.wrap(b, p + 8, 4).int
                    p += 12
                    var cts = 0L
                    if (flags and 0x800 != 0) {
                        cts = ByteBuffer.wrap(b, p, 4).int.toLong()
                        p += 4
                    }
                    out += Sample(track, dts, duration, cts, sampleFlags and 0x00010000 == 0, b.copyOfRange(dataAt, dataAt + size))
                    dataAt += size
                    dts += duration
                }
            }
        }
        return out
    }
}
