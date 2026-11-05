package com.tmplayer.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The names a series list gives its episodes: the provider's, unless it only numbers them. */
class EpisodeNamesTest {

    private val loki = listOf(
        MetaEpisode(season = 1, number = 1, name = "Glorious Purpose"),
        MetaEpisode(season = 1, number = 2, name = "The Variant"),
        MetaEpisode(season = 1, number = 3, name = "Lamentis"),
        MetaEpisode(season = 1, number = 4, name = "  "),
        MetaEpisode(season = 1, number = 5, name = "Episode 5"),
        MetaEpisode(season = 1, number = 6, name = "الحلقة 6"),
    )

    @Test
    fun theProvidersNameIsTaken() {
        assertEquals("Lamentis", EpisodeNames.listName(1, 3, loki))
        assertEquals("Glorious Purpose", EpisodeNames.listName(1, 1, loki))
    }

    @Test
    fun nothingKnownKeepsTheRowsOwnLabel() {
        assertNull("lookups off or not answered yet", EpisodeNames.listName(1, 3, null))
        assertNull(EpisodeNames.listName(1, 3, emptyList()))
        assertNull("an episode the provider does not list", EpisodeNames.listName(1, 9, loki))
        assertNull("the same number in another season", EpisodeNames.listName(2, 3, loki))
        assertNull("a blank name", EpisodeNames.listName(1, 4, loki))
    }

    @Test
    fun aPlaceholderIsNotAName() {
        assertNull(EpisodeNames.listName(1, 5, loki))
        assertNull(EpisodeNames.listName(1, 6, loki))
        listOf("Episode 5", "Épisode 5", "Folge 5", "Episodio 5", "Эпизод 5", "第 5 集", "エピソード5", "5", "Chapter 5", "Ep. 5", "الحلقة ٥")
            .forEach { assertTrue(it, EpisodeNames.placeholder(it, 5)) }
    }

    @Test
    fun realNamesWithNumbersStay() {
        assertFalse(EpisodeNames.placeholder("Pilot", 1))
        assertFalse("another episode's number is a title", EpisodeNames.placeholder("Episode 5", 4))
        assertFalse(EpisodeNames.placeholder("The 4th Floor", 4))
        assertFalse(EpisodeNames.placeholder("Chapter 5: The Gunslinger", 5))
        assertFalse(EpisodeNames.placeholder("1984", 1))
        assertEquals("Chapter 5: The Gunslinger", EpisodeNames.listName(1, 5, listOf(MetaEpisode(1, 5, "Chapter 5: The Gunslinger"))))
    }
}
