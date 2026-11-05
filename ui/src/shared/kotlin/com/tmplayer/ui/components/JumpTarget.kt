package com.tmplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.onPlaced
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.delay

/**
 * A Settings row another screen can send the viewer to, as Setup choices' "Also in" does: brought
 * into view, given the focus on a remote or a keyboard, and lit for a moment so the eye finds it.
 * Hand it to the row with [jumpTarget] and go there with [arrive].
 */
@Stable
class JumpTarget {
    internal val focus = FocusRequester()
    internal val bring = BringIntoViewRequester()
    internal var placed by mutableStateOf(false)
    internal var flashes by mutableIntStateOf(0)

    /**
     * Brings the row into view, focuses it when [focus] and lights it. In a lazy [list] the row
     * may not be composed yet, so the list is walked down a screen at a time until it is.
     */
    suspend fun arrive(list: LazyListState? = null, focus: Boolean = true) {
        repeat(MAX_TRIES) {
            withFrameNanos { }
            if (placed) {
                runCatching { bring.bringIntoView() }
                if (focus) runCatching { this.focus.requestFocus() }
                flashes++
                return
            }
            val lazy = list ?: return@repeat
            if (!lazy.canScrollForward) return@repeat
            lazy.scrollBy(lazy.layoutInfo.viewportSize.height * 0.7f)
        }
    }

    private companion object {
        const val MAX_TRIES = 40
    }
}

/** Marks this row as [target]'s; null leaves the row as it is. */
fun Modifier.jumpTarget(target: JumpTarget?): Modifier = if (target == null) {
    this
} else {
    composed {
        DisposableEffect(target) { onDispose { target.placed = false } }
        val glow = remember { Animatable(0f) }
        LaunchedEffect(target.flashes) {
            if (target.flashes == 0) return@LaunchedEffect
            glow.snapTo(FLASH_ALPHA)
            delay(FLASH_HOLD_MS)
            glow.animateTo(0f, tween(FLASH_FADE_MS))
        }
        val color = Tone.accent
        this
            .onPlaced { target.placed = true }
            .bringIntoViewRequester(target.bring)
            .focusRequester(target.focus)
            .drawWithContent {
                drawContent()
                val alpha = glow.value
                if (alpha > 0f) {
                    val r = 12f * density
                    drawRoundRect(color.copy(alpha = alpha), cornerRadius = CornerRadius(r, r))
                }
            }
    }
}

private const val FLASH_ALPHA = 0.28f
private const val FLASH_HOLD_MS = 500L
private const val FLASH_FADE_MS = 900
