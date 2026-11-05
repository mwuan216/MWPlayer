package com.tmplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * How far to turn a downward chevron so it points along the line, towards the line's end: a
 * quarter turn back to point right, or forward to point left in a right-to-left interface.
 *
 * The arrows in [com.tmplayer.ui.icons.TmIcons] mirror themselves, but a chevron turned by an
 * angle does not: the angle has to be chosen for the direction, which is what this is for.
 */
@Composable
@ReadOnlyComposable
fun chevronTowardsEnd(): Float = chevronTowardsEnd(LocalLayoutDirection.current)

/** [chevronTowardsEnd] for a given [direction]. */
fun chevronTowardsEnd(direction: LayoutDirection): Float = if (direction == LayoutDirection.Rtl) 90f else -90f
