package com.tmplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeRewindTest {

    @Test
    fun `a resume starts the chosen seconds earlier`() {
        assertEquals(1_197_000L, ResumeRewind.start(1_200_000, 3_000))
        assertEquals(1_190_000L, ResumeRewind.start(1_200_000, 10_000))
        assertEquals(1_200_000L, ResumeRewind.start(1_200_000, 0))
    }

    @Test
    fun `a start from the beginning, or a position under ten seconds, is left alone`() {
        assertEquals(0L, ResumeRewind.start(0, 3_000))
        assertEquals(9_999L, ResumeRewind.start(9_999, 5_000))
        assertEquals(0L, ResumeRewind.start(-5, 3_000))
    }

    @Test
    fun `never below zero`() {
        assertEquals(0L, ResumeRewind.start(10_000, 10_000))
        assertEquals(2_000L, ResumeRewind.start(12_000, 10_000))
    }

    @Test
    fun `a stored value off the list reads as the default`() {
        assertEquals(3_000L, ResumeRewind.sanitise(null))
        assertEquals(3_000L, ResumeRewind.sanitise(7_000))
        assertEquals(0L, ResumeRewind.sanitise(0))
        assertEquals(10_000L, ResumeRewind.sanitise(10_000))
    }
}
