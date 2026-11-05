package com.tmplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbLoopTest {

    @Test
    fun `only a whole loop jumps, and only on playing into its end`() {
        assertNull(AbLoop(30_000).wrap(90_000))
        val loop = AbLoop(30_000, 90_000)
        assertNull(loop.wrap(89_900))
        assertEquals(30_000L, loop.wrap(90_000))
        assertEquals(30_000L, loop.wrap(90_400))
        // A seek well past the end was meant, and is left alone.
        assertNull(loop.wrap(200_000))
        assertNull(loop.wrap(10_000))
    }

    @Test
    fun `a frame is one over the rate, with a fallback for a file that does not say`() {
        assertEquals(41L, FrameStep.frameMs(23.976f))
        assertEquals(40L, FrameStep.frameMs(0f))
        assertEquals(40L, FrameStep.frameMs(-1f))
        assertEquals(40L, FrameStep.frameMs(Float.NaN))
        assertEquals(16L, FrameStep.frameMs(60f))
    }

    @Test
    fun `a step stays inside the video`() {
        assertEquals(10_040L, FrameStep.target(10_000, 60_000, 25f, forward = true))
        assertEquals(9_960L, FrameStep.target(10_000, 60_000, 25f, forward = false))
        assertEquals(0L, FrameStep.target(20, 60_000, 25f, forward = false))
        assertEquals(60_000L, FrameStep.target(59_990, 60_000, 25f, forward = true))
        assertEquals(10_040L, FrameStep.target(10_000, 0, 25f, forward = true))
    }
}
