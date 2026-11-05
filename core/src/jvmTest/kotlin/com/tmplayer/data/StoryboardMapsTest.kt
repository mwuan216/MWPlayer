package com.tmplayer.data

import com.tmplayer.player.LineSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryboardMapsTest {

    @Test
    fun `a webvtt map gives each cue its box`() {
        val text = """
            WEBVTT

            00:00:00.000 --> 00:00:05.000
            sprite.jpg#xywh=0,0,160,90

            00:00:05.000 --> 00:00:10.000
            sprite.jpg#xywh=160,0,160,90
        """.trimIndent()
        val map = StoryboardMaps.parse(text, 160, 90, 1600, 900, 10_000)
        assertEquals(2, map.tiles.size)
        assertEquals(StoryboardTile(5_000, 160, 0, 160, 90), map.tileAt(7_000))
        assertEquals(0, map.tileAt(0)?.x)
    }

    @Test
    fun `lines of numbers are a moment in seconds and a corner`() {
        val text = "0 0 0\n2.5 160 0\n5 320 0\n"
        val map = StoryboardMaps.parse(text, 160, 90, 480, 90, 7_500)
        assertEquals(listOf(0L, 2_500L, 5_000L), map.tiles.map { it.atMs })
        assertEquals(320, map.tileAt(6_000)?.x)
        assertEquals(90, map.tileAt(6_000)?.height)
    }

    @Test
    fun `moments far past the length are read as milliseconds`() {
        val text = "0,0,0\n10000,160,0\n20000,320,0"
        val map = StoryboardMaps.parse(text, 160, 90, 480, 90, 30_000)
        assertEquals(10_000L, map.tiles[1].atMs)
    }

    @Test
    fun `with nothing readable the picture is a grid over the length`() {
        val map = StoryboardMaps.parse("", 100, 50, 300, 100, 60_000)
        // Three across, two down, ten seconds each.
        assertEquals(6, map.tiles.size)
        assertEquals(StoryboardTile(30_000, 0, 50, 100, 50), map.tiles[3])
        assertEquals(StoryboardTile(50_000, 200, 50, 100, 50), map.tileAt(59_000))
    }

    @Test
    fun `a header with a count and a step shapes the grid`() {
        val map = StoryboardMaps.parse("frame_count=4\nframe_duration=2.5", 100, 50, 300, 100, 60_000)
        assertEquals(4, map.tiles.size)
        assertEquals(7_500L, map.tiles[3].atMs)
        assertEquals(0, map.tiles[3].x)
        assertEquals(50, map.tiles[3].y)
    }

    @Test
    fun `tiles that do not fit give no map`() {
        assertTrue(StoryboardMaps.parse("", 100, 50, 50, 50, 60_000).isEmpty)
        assertNull(StoryboardMap(emptyList()).tileAt(5))
    }

    @Test
    fun `a jpeg's size is read from its frame header`() {
        val image = java.awt.image.BufferedImage(321, 123, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val out = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(image, "jpg", out)
        assertEquals(321 to 123, JpegSize.read(out.toByteArray()))
        assertNull(JpegSize.read(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun `the line speed is remembered for ten minutes`() {
        LineSpeed.forget()
        LineSpeed.note(200_000, nowMs = 1_000)
        assertEquals(200_000L, LineSpeed.recent(nowMs = 2_000))
        assertNull(LineSpeed.recent(nowMs = 1_000 + LineSpeed.FRESH_MS + 1))
        // Noise is not a speed.
        LineSpeed.note(10, nowMs = 3_000)
        assertEquals(200_000L, LineSpeed.recent(nowMs = 3_000))
        LineSpeed.forget()
    }
}
