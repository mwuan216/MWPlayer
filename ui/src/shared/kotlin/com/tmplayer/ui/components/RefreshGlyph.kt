package com.tmplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.rememberReducedMotion

/** Asks a [RefreshGlyph] for one turn, from the press that starts a refresh. */
@Stable
class RefreshSpin {
    internal var kicks by mutableIntStateOf(0)

    /** One turn now, and more for as long as the glyph is told the refresh is running. */
    fun kick() {
        kicks++
    }
}

@Composable
fun rememberRefreshSpin(): RefreshSpin = remember { RefreshSpin() }

/**
 * The refresh arrow that answers its press: it turns once when [spin] is kicked, keeps turning
 * while [spinning] (the list is still being fetched), and always comes to rest upright, at the end
 * of a whole turn, never stopped part way round. Reduced motion leaves it still.
 */
@Composable
fun RefreshGlyph(
    spin: RefreshSpin,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    spinning: Boolean = false,
    tint: Color = androidx.compose.material3.LocalContentColor.current,
) {
    val reduced = rememberReducedMotion() || Motion.reduced
    val angle = remember { Animatable(0f) }
    val stillRunning by rememberUpdatedState(spinning)
    LaunchedEffect(spin.kicks, spinning) {
        if (reduced || (spin.kicks == 0 && !spinning)) return@LaunchedEffect
        var first = true
        do {
            // The first turn eases in and out, a press's answer; the ones after it run evenly, a
            // wheel that is still going.
            val more = stillRunning
            angle.animateTo(
                targetValue = (angle.value / TURN).toInt() * TURN + TURN,
                animationSpec = tween(
                    durationMillis = if (more && !first) LOOP_MS else TURN_MS,
                    easing = if (more && !first) LinearEasing else FastOutSlowInEasing,
                ),
            )
            first = false
        } while (stillRunning)
        angle.snapTo(0f)
    }
    Icon(
        TmIcons.Refresh,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayer { rotationZ = angle.value },
    )
}

private const val TURN = 360f
private const val TURN_MS = 650
private const val LOOP_MS = 800
