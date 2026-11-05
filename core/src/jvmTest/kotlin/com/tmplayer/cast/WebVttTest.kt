package com.tmplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebVttTest {

    @Test
    fun `subrip becomes webvtt with dotted times and its tags kept`() {
        val srt = "1\r\n00:00:01,500 --> 00:00:03,000\r\n<i>Hello</i> there\r\n\r\n2\r\n00:01:02,005 --> 00:01:04,250\r\n<font color=\"red\">Two</font>\r\nlines & more\r\n"
        val vtt = WebVtt.convert(srt, "srt")
        assertEquals(
            "WEBVTT\n\n" +
                "00:00:01.500 --> 00:00:03.000\n<i>Hello</i> there\n\n" +
                "00:01:02.005 --> 00:01:04.250\nTwo\nlines &amp; more\n\n",
            vtt,
        )
    }

    @Test
    fun `styled ass is sent as plain text`() {
        val ass = """
            [Script Info]
            Title: test

            [V4+ Styles]
            Format: Name, Fontname
            Style: Default,Arial

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.50,Default,,0,0,0,,{\an8\b1}Top line\Nsecond, with a comma
            Dialogue: 0,0:00:03.00,0:00:04.00,Default,,0,0,0,,{\p1}m 0 0 l 100 0 100 100
            Comment: 0,0:00:05.00,0:00:06.00,Default,,0,0,0,,not shown
        """.trimIndent()
        val cues = WebVtt.parseAss(ass)
        assertEquals(1, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(2500L, cues[0].endMs)
        assertEquals("Top line\nsecond, with a comma", cues[0].text)
        assertTrue(WebVtt.convert(ass, "ass").contains("00:00:01.000 --> 00:00:02.500\nTop line\nsecond, with a comma"))
    }

    @Test
    fun `an ass file named srt is still read as ass`() {
        val ass = "[Script Info]\n\n[Events]\nFormat: Layer, Start, End, Style, Text\nDialogue: 0,0:00:01.00,0:00:02.00,Default,Hi\n"
        assertTrue(WebVtt.convert(ass, "srt").contains("00:00:01.000 --> 00:00:02.000\nHi"))
    }

    @Test
    fun `webvtt passes through with a header`() {
        assertEquals("WEBVTT\n\n00:00.000 --> 00:01.000\nx", WebVtt.convert("WEBVTT\n\n00:00.000 --> 00:01.000\nx", "vtt"))
        assertTrue(WebVtt.convert("00:00.000 --> 00:01.000\nx", "vtt").startsWith("WEBVTT\n\n"))
    }

    @Test
    fun `stamps hours past ten`() {
        assertEquals("10:00:00.001", WebVtt.stamp(36_000_001))
    }
}
