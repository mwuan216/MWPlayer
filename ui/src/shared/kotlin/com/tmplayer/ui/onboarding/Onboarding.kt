package com.tmplayer.ui.onboarding

import com.tmplayer.i18n.Languages
import com.tmplayer.i18n.Translator

/**
 * What is left of the tour before sign in, which CP67 replaced with the setup after it ([Setup]):
 * the language list that Settings' pickers draw, and where "Help translate" points.
 */
object Onboarding {

    /** Where "Help translate" points, from Settings' language picker and from About. */
    const val TRANSLATE_URL = "https://tmplayer.org/translate/"

    /**
     * The language picker's choices: the system's own first (tag ""), then each language by its own
     * name. Every shipped language is offered, machine translated or reviewed. A debug build adds
     * the en-XA pseudo-locale last, for checking that text fits and comes from the catalog.
     */
    fun languages(pseudo: Boolean = Translator.pseudoEnabled): List<LanguageChoice> =
        listOf(LanguageChoice("", null)) + Languages.all.map { LanguageChoice(it.tag, it.name) } +
            if (pseudo) listOf(LanguageChoice(Languages.PSEUDO, PSEUDO_NAME)) else emptyList()

    /** The pseudo-locale's row, debug builds only, so not in the catalog. */
    const val PSEUDO_NAME = "Pseudo-locale (en-XA)"
}

/** One row of the language picker. [name] is null for "System default". */
data class LanguageChoice(val tag: String, val name: String?)
