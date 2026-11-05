package com.tmplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSpeedTest {

    @Test
    fun `stepping runs up the list and wraps`() {
        assertEquals(1.25f, PlaybackSpeed.next(1f))
        assertEquals(PlaybackSpeed.CHOICES.first(), PlaybackSpeed.next(PlaybackSpeed.CHOICES.last()))
    }

    @Test
    fun `a stored speed lands on the nearest fine mark inside the range`() {
        assertEquals(1.3f, PlaybackSpeed.sanitise(1.3f))
        assertEquals(1.35f, PlaybackSpeed.sanitise(1.33f))
        assertEquals(1.1f, PlaybackSpeed.sanitise(1.1f))
        assertEquals(PlaybackSpeed.MAX, PlaybackSpeed.sanitise(16f))
        assertEquals(PlaybackSpeed.MIN, PlaybackSpeed.sanitise(0.1f))
        assertEquals(PlaybackSpeed.DEFAULT, PlaybackSpeed.sanitise(0f))
        assertEquals(PlaybackSpeed.DEFAULT, PlaybackSpeed.sanitise(-2f))
        assertEquals(PlaybackSpeed.DEFAULT, PlaybackSpeed.sanitise(Float.NaN))
    }

    @Test
    fun `a fine step moves five hundredths without float drift`() {
        assertEquals(1.05f, PlaybackSpeed.fine(1f, up = true))
        assertEquals(0.95f, PlaybackSpeed.fine(1f, up = false))
        var speed = 1f
        repeat(7) { speed = PlaybackSpeed.fine(speed, up = true) }
        assertEquals(1.35f, speed)
        repeat(7) { speed = PlaybackSpeed.fine(speed, up = false) }
        assertEquals(1f, speed)
    }

    @Test
    fun `a speed between marks steps onto the next mark in that direction`() {
        assertEquals(1.35f, PlaybackSpeed.fine(1.33f, up = true))
        assertEquals(1.3f, PlaybackSpeed.fine(1.33f, up = false))
        assertEquals(1.15f, PlaybackSpeed.fine(1.1f, up = true))
    }

    @Test
    fun `the fine steps stop at a quarter and at four times`() {
        assertEquals(0.25f, PlaybackSpeed.fine(0.25f, up = false))
        assertEquals(0.25f, PlaybackSpeed.fine(0.3f, up = false))
        assertEquals(4f, PlaybackSpeed.fine(4f, up = true))
        assertEquals(4f, PlaybackSpeed.fine(3.95f, up = true))
        assertEquals(4f, PlaybackSpeed.fine(9f, up = true))
        assertEquals(0.25f, PlaybackSpeed.fine(0.01f, up = false))
        assertFalse(PlaybackSpeed.canStep(0.25f, up = false))
        assertTrue(PlaybackSpeed.canStep(0.25f, up = true))
        assertFalse(PlaybackSpeed.canStep(4f, up = true))
        assertTrue(PlaybackSpeed.canStep(4f, up = false))
    }

    @Test
    fun `presets are told apart from fine speeds`() {
        assertTrue(PlaybackSpeed.isPreset(1.25f))
        assertFalse(PlaybackSpeed.isPreset(1.3f))
    }

    @Test
    fun `labels drop the noise`() {
        assertEquals("1x", PlaybackSpeed.label(1f))
        assertEquals("2x", PlaybackSpeed.label(2f))
        assertEquals("1.25x", PlaybackSpeed.label(1.25f))
        assertEquals("0.5x", PlaybackSpeed.label(0.5f))
        assertEquals("1.35x", PlaybackSpeed.label(1.35f))
        assertEquals("0.25x", PlaybackSpeed.label(0.25f))
        assertEquals("0.05x", PlaybackSpeed.label(PlaybackSpeed.FINE_STEP))
    }
}
