package com.tmplayer.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [MetaRules.poster]: which of a title's TMDB posters a tile shows, from fixtures shaped like TMDB's. */
class MetaPosterTest {

    private fun poster(path: String, language: String?, votes: Double = 5.0, count: Int = 3, aspect: Double = 0.667, width: Int = 1000) =
        MetaPoster(path, language, aspect, width, votes, count)

    /** Amélie's posters as TMDB lists them, cut down: French original, English, wordless. */
    private val amelie = listOf(
        poster("/fr-top.jpg", "fr", votes = 8.36, count = 6, width = 2000),
        poster("/en-top.jpg", "en", votes = 6.72, count = 9, width = 2000),
        poster("/en-two.jpg", "en", votes = 4.88, count = 14, width = 1280),
        poster("/none.jpg", null, votes = 3.33, count = 1, width = 1280),
        poster("/de.jpg", "de", votes = 9.9, count = 40),
    )

    @Test
    fun theUiLanguageComesFirst() {
        val withArabic = amelie + poster("/ar.jpg", "ar", votes = 0.0, count = 0)
        assertEquals("/ar.jpg", MetaRules.poster(withArabic, "ar", "fr")?.path)
        assertEquals("/fr-top.jpg", MetaRules.poster(amelie, "fr-FR", "fr")?.path)
    }

    @Test
    fun thenEnglishOrWordlessByVotes() {
        assertEquals("/en-top.jpg", MetaRules.poster(amelie, "ar", "fr")?.path)
        val wordlessBest = amelie + poster("/none-best.jpg", null, votes = 7.5, count = 2)
        assertEquals("/none-best.jpg", MetaRules.poster(wordlessBest, "ar", "fr")?.path)
    }

    @Test
    fun thenTheOriginalLanguage() {
        val onlyOriginal = listOf(poster("/ml.jpg", "ml", votes = 5.2), poster("/ml-low.jpg", "ml", votes = 1.0), poster("/ta.jpg", "ta", votes = 9.0))
        assertEquals("/ml.jpg", MetaRules.poster(onlyOriginal, "ar", "ml")?.path)
        assertNull("a language that is neither the viewer's, English nor the original", MetaRules.poster(listOf(poster("/ta.jpg", "ta")), "ar", "ml"))
    }

    @Test
    fun whatIsNotAPosterIsPassedOver() {
        val logos = listOf(
            poster("/badge.jpg", "ar", votes = 10.0, aspect = 1.0),
            poster("/banner.jpg", "ar", votes = 10.0, aspect = 1.778, width = 1920),
            poster("/tiny.jpg", "ar", votes = 10.0, width = 185),
            poster("/no-path.jpg".removePrefix("/"), "ar", votes = 10.0),
        )
        assertNull(MetaRules.poster(logos, "ar", "fr"))
        assertEquals("/en-top.jpg", MetaRules.poster(logos + amelie, "ar", "fr")?.path)
    }

    @Test
    fun tiesGoToMoreVotesThenTheWiderPicture() {
        val tied = listOf(poster("/a.jpg", "en", votes = 5.0, count = 2), poster("/b.jpg", "en", votes = 5.0, count = 7), poster("/c.jpg", "en", votes = 5.0, count = 7, width = 2000))
        assertEquals("/c.jpg", MetaRules.poster(tied, "en-US", null)?.path)
    }

    @Test
    fun nothingToChooseFrom() {
        assertNull(MetaRules.poster(emptyList(), "ar", "fr"))
    }
}
