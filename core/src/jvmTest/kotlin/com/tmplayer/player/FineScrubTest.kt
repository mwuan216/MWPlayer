package com.tmplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FineScrubTest {

    @Test
    fun `the rate halves and then quarters as the finger goes further away, either side`() {
        assertEquals(1f, FineScrub.rate(0f))
        assertEquals(1f, FineScrub.rate(40f))
        assertEquals(0.5f, FineScrub.rate(-60f))
        assertEquals(0.5f, FineScrub.rate(100f))
        assertEquals(0.25f, FineScrub.rate(-200f))
    }

    @Test
    fun `slowing part way keeps what was travelled at full speed`() {
        val t = FineScrub.Tracker(100f)
        assertEquals(200f, t.move(200f, 0f))
        // Up past the half band: the next 100 px count as 50.
        assertEquals(250f, t.move(300f, -80f))
        // Further up: a quarter.
        assertEquals(275f, t.move(400f, -200f))
        assertEquals(0.25f, t.rate)
        // Back on the line: full speed again, from where the slow part left it.
        assertEquals(375f, t.move(500f, 0f))
    }

    @Test
    fun `travel past an end is not banked`() {
        val t = FineScrub.Tracker(50f)
        t.move(-500f, 0f)
        t.clamp(0f, 1000f)
        assertEquals(0f, t.x)
        assertEquals(100f, t.move(-400f, 0f))
    }

    @Test
    fun `only a slowed rate has a hint of its own`() {
        assertNull(FineScrub.hint(1f))
        assertEquals("Half speed scrubbing", FineScrub.hint(0.5f))
        assertEquals("Quarter speed scrubbing", FineScrub.hint(0.25f))
    }
}
