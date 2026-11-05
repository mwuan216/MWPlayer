package com.tmplayer.cast

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The stereo fold-down the cast's sound conversion feeds the AAC encoder with. */
@UnstableApi
class CastAudioTest {

    private fun pcm(vararg frames: IntArray): ByteArray {
        val out = ByteBuffer.allocate(frames.sumOf { it.size } * 2).order(ByteOrder.LITTLE_ENDIAN)
        frames.forEach { f -> f.forEach { out.putShort(it.toShort()) } }
        return out.array()
    }

    private fun samples(bytes: ByteArray): List<Int> {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return List(b.remaining()) { b.get(it).toInt() }
    }

    @Test
    fun `stereo passes through`() {
        assertEquals(listOf(100, -200, 300, -400), samples(AndroidAudioConverter.downmix(pcm(intArrayOf(100, -200), intArrayOf(300, -400)), 2)))
    }

    @Test
    fun `mono goes to both sides`() {
        assertEquals(listOf(500, 500), samples(AndroidAudioConverter.downmix(pcm(intArrayOf(500)), 1)))
    }

    @Test
    fun `five one keeps the front, centre and surrounds and drops the lfe`() {
        // FL FR C LFE SL SR
        val out = samples(AndroidAudioConverter.downmix(pcm(intArrayOf(1000, 0, 0, 30000, 0, 0)), 6))
        assertEquals(700, out[0])
        assertEquals(0, out[1])
        val centre = samples(AndroidAudioConverter.downmix(pcm(intArrayOf(0, 0, 1000, 0, 0, 0)), 6))
        assertEquals(centre[0], centre[1])
        assertEquals(495, centre[0])
    }

    @Test
    fun `loud surround sound is clipped rather than wrapped`() {
        val out = samples(AndroidAudioConverter.downmix(pcm(intArrayOf(32767, 32767, 32767, 0, 32767, 32767)), 6))
        assertEquals(32767, out[0])
        assertEquals(32767, out[1])
    }

    @Test
    fun `a 96 kHz source is halved`() {
        val out = samples(AndroidAudioConverter.downmix(pcm(intArrayOf(100, 100), intArrayOf(300, 300), intArrayOf(5, 5), intArrayOf(7, 7)), 2, halve = true))
        assertEquals(listOf(200, 200, 6, 6), out)
    }
}
