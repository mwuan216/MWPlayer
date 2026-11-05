package com.tmplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What "Go to a time" reads out of what was typed. */
class TimeEntryTest {

    private val hourAndHalf = 90 * 60_000L

    private fun parse(text: String, duration: Long = hourAndHalf, position: Long = 0) = TimeEntry.parse(text, duration, position)

    @Test
    fun clocks() {
        assertEquals(((1 * 60 + 23) * 60 + 45) * 1000L, parse("1:23:45"))
        assertEquals((83 * 60 + 10) * 1000L, parse("83:10"))
        assertEquals(45_000L, parse("0:45"))
        assertEquals(45_000L, parse(" 0 : 45 "))
        assertNull(parse("1:75"))
        assertNull(parse("1:61:00"))
        assertNull(parse("1:2:3:4"))
        assertNull(parse("1:"))
    }

    @Test
    fun `a bare number is minutes`() {
        assertEquals(45 * 60_000L, parse("45"))
        assertEquals(0L, parse("0"))
    }

    @Test
    fun units() {
        assertEquals(80 * 60_000L, parse("1h20m"))
        assertEquals(80 * 60_000L + 5_000, parse("1h 20m 5s"))
        assertEquals(90_000L, parse("90s"))
        assertEquals(45 * 60_000L, parse("45 min"))
        assertEquals(60 * 60_000L, parse("1H"))
        assertNull(parse("1h1h"))
        assertNull(parse("5ms"))
        assertNull(parse("ten"))
    }

    @Test
    fun percentages() {
        assertEquals(45 * 60_000L, parse("50%"))
        assertEquals(hourAndHalf / 8, parse("12.5%"))
        assertEquals(hourAndHalf / 8, parse("12,5 %"))
        assertNull(parse("120%"))
        assertNull(parse("50%", duration = 0))
    }

    @Test
    fun `steps from where the video is, kept inside it`() {
        assertEquals(15 * 60_000L, parse("+5m", position = 10 * 60_000L))
        assertEquals(9 * 60_000L + 30_000, parse("-30s", position = 10 * 60_000L))
        assertEquals(0L, parse("-20", position = 10 * 60_000L))
        assertEquals(hourAndHalf, parse("+2h", position = 10 * 60_000L))
    }

    @Test
    fun `past the end is refused`() {
        assertNull(parse("91"))
        assertNull(parse("2:00:00"))
        // An unknown length takes any place.
        assertEquals(2 * 3_600_000L, parse("2:00:00", duration = 0))
    }

    @Test
    fun `digits and signs from other keyboards`() {
        // Arabic-Indic, Persian, Devanagari and full width digits; the full width colon.
        assertEquals((12 * 60 + 30) * 1000L, parse("١٢:٣٠"))
        assertEquals((12 * 60 + 30) * 1000L, parse("۱۲:۳۰"))
        assertEquals(45 * 60_000L, parse("०४५").let { it })
        assertEquals((12 * 60 + 30) * 1000L, parse("１２：３０"))
        assertEquals(45 * 60_000L, parse("٥٠٪"))
    }

    @Test
    fun `nothing reads as nothing`() {
        assertNull(parse(""))
        assertNull(parse("   "))
        assertNull(parse("+"))
        assertNull(parse("12:3x"))
    }
}
