package com.tmplayer.online

import com.tmplayer.data.MediaName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaConfidenceTest {

    private fun film(year: Int?) = MetaInfo(MetaProvider.Tmdb, "1", MetaKind.Film, "Night Lantern", year)
    private val show = MetaInfo(MetaProvider.TvMaze, "2", MetaKind.Show, "Harbour Notes", 2019)

    private fun name(text: String) = MediaName.parse(text, maxYear = 2027)

    @Test
    fun `a film needs its year to agree`() {
        assertEquals(MatchBasis.TitleAndYear, MetaConfidence.basis(name("Night Lantern (2022) 720p.mkv"), film(2022)))
        assertEquals(MatchBasis.TitleAndYear, MetaConfidence.basis(name("Night Lantern (2023) 720p.mkv"), film(2022)))
        assertNull(MetaConfidence.basis(name("Night Lantern (1998) 720p.mkv"), film(2022)))
        assertNull(MetaConfidence.basis(name("Night Lantern 720p.mkv"), film(2022)))
    }

    @Test
    fun `without a year the runtime can vouch for it`() {
        val noYear = name("Night Lantern 720p.mkv")
        assertEquals(MatchBasis.Runtime, MetaConfidence.basis(noYear, film(2022), durationSec = 7_150, runtimeMin = 120))
        assertNull(MetaConfidence.basis(noYear, film(2022), durationSec = 5_400, runtimeMin = 120))
        assertTrue(MetaConfidence.needsRuntime(noYear, film(2022), 7_150))
        assertFalse(MetaConfidence.needsRuntime(noYear, film(2022), 0))
    }

    @Test
    fun `the runtime agrees within five minutes or five per cent`() {
        assertTrue(MetaConfidence.runtimeAgrees(120 * 60 + 299, 120))
        assertFalse(MetaConfidence.runtimeAgrees(120 * 60 + 500, 120))
        // Five per cent of three hours is nine minutes.
        assertTrue(MetaConfidence.runtimeAgrees(180 * 60 + 500, 180))
        assertFalse(MetaConfidence.runtimeAgrees(0, 120))
        assertFalse(MetaConfidence.runtimeAgrees(7200, null))
    }

    @Test
    fun `a show is trusted for a file that names an episode`() {
        assertEquals(MatchBasis.ShowCode, MetaConfidence.basis(name("Harbour.Notes.S02E03.mkv"), show))
        assertEquals(MatchBasis.TitleAndYear, MetaConfidence.basis(name("Harbour Notes 2023 S02E03.mkv"), show))
        assertNull(MetaConfidence.basis(name("Harbour Notes 720p.mkv"), show))
    }
}
