package com.tmplayer.ui.theme

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Whether focus is drawn at all: the web's `:focus-visible` for the whole app.
 *
 * A remote and a keyboard need to see where they are, so the ring, the focus fill and the lift
 * are always on for them. A mouse does not: on the desktop a click also gives the control focus,
 * and a ring left round the button just clicked reads as a stuck highlight. The desktop turns
 * this off on every mouse press and back on at the first key that moves focus (Tab, the arrows,
 * Home, End, Page Up and Page Down). The phone and the television never touch it, so for them
 * focus is shown exactly as it always was.
 */
object FocusVisibility {
    var visible by mutableStateOf(true)
}

/**
 * [collectIsFocusedAsState], but true only while focus is being shown (see [FocusVisibility]):
 * what every ring, focus fill and focus lift reads, so a mouse click never leaves one behind.
 */
@Composable
fun InteractionSource.collectIsFocusVisibleAsState(): State<Boolean> {
    val focused = collectIsFocusedAsState()
    return remember(focused) { derivedStateOf { focused.value && FocusVisibility.visible } }
}
