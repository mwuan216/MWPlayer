package com.tmplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp

/**
 * [style] for [text], without the tight tracking a display title is set in when that would break
 * it: in a right-to-left layout, or where [text] is in a script whose letters join.
 *
 * Android's text engine does not space joined letters at all, but Compose still takes the
 * negative tracking off the width it asks for, so the box comes out a little narrower than the
 * word and a short Arabic title such as "لوكي" wraps onto a second line. Latin titles keep their
 * tracking; it is only taken off where it would do harm.
 */
@Composable
@ReadOnlyComposable
fun TextStyle.trackedFor(text: String): TextStyle =
    untracked(this, text, rtl = LocalLayoutDirection.current == LayoutDirection.Rtl)

/** The rule behind [trackedFor], away from composition so it can be tested. */
fun untracked(style: TextStyle, text: String, rtl: Boolean): TextStyle {
    val spacing = style.letterSpacing
    val tight = spacing.type == TextUnitType.Sp && spacing.value < 0f
    return if (tight && (rtl || joinsLetters(text))) style.copy(letterSpacing = 0.sp) else style
}

/**
 * True when [text] has a letter of a script written joined up: Arabic and the languages written in
 * it (Persian, Urdu), Syriac, N'Ko and Mongolian.
 */
fun joinsLetters(text: String): Boolean = text.any { c ->
    c in '؀'..'ۿ' || c in '܀'..'ݏ' || c in 'ݐ'..'ݿ' ||
        c in '߀'..'߿' || c in 'ࡠ'..'࡯' || c in 'ࡰ'..'ࣿ' ||
        c in '᠀'..'᢯' || c in 'ﭐ'..'﷿' || c in 'ﹰ'..'﻿'
}
