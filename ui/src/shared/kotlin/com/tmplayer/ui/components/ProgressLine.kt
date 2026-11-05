package com.tmplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tmplayer.ui.theme.LocalDarkTheme
import com.tmplayer.ui.theme.Tone

/**
 * How far through a video the viewer is, drawn across the foot of its art: a thin line in the
 * accent on a [Tone.track], with square ends, inset from the art's edges so it reads as
 * laid on the picture rather than as a band under it.
 *
 * 3 dp on a phone or a desktop and 4 dp on a television, where it is seen from further away.
 * [fraction] is clamped to 0..1, and the fill grows from the start edge, so from the right in a
 * right to left layout. Place it with `Modifier.align(Alignment.BottomCenter)` inside the art's
 * box; [inset] is its margin from the sides and the bottom.
 */
@Composable
fun ProgressLine(
    fraction: Float,
    modifier: Modifier = Modifier,
    inset: Dp = ProgressLineInset,
) {
    val thickness = if (isTouch()) 3.dp else 4.dp
    val track = Tone.track
    // The art under the line is dark in both themes, and daylight's accent is a dark tone made
    // for a white page, which all but vanishes on a picture. The scheme's own light tone of the
    // same hue (the dark theme's accent) is the one that reads there.
    val fill = if (LocalDarkTheme.current) Tone.accent else MaterialTheme.colorScheme.inversePrimary
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val done = fraction.coerceIn(0f, 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .padding(PaddingValues(start = inset, end = inset, bottom = inset))
            .height(thickness),
    ) {
        drawRect(track)
        val width = size.width * done
        if (width > 0f) {
            drawRect(fill, topLeft = Offset(if (rtl) size.width - width else 0f, 0f), size = Size(width, size.height))
        }
    }
}

/** The margin a [ProgressLine] keeps from the edges of the art it sits on. */
val ProgressLineInset = 8.dp
