package com.tmplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.DialogWindowProvider
import com.tmplayer.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Where the last finger came down on the app's window, as a fraction of it: the point a page
 * opened by that tap grows out of ([PageMotion]), so a poster or a row tapped in a grid seems to
 * open into its page and Back folds it back towards the same place. Read only within
 * [FRESH_MS] of the touch; anything older (a page opened by a remote, or from a menu in a window
 * of its own) grows from the middle.
 */
internal object TouchOrigin {
    @Volatile private var x = 0.5f
    @Volatile private var y = 0.5f
    @Volatile private var at = 0L

    fun note(position: Offset, size: IntSize) {
        if (size.width <= 0 || size.height <= 0) return
        x = (position.x / size.width).coerceIn(0f, 1f)
        y = (position.y / size.height).coerceIn(0f, 1f)
        at = System.currentTimeMillis()
    }

    fun recent(now: Long = System.currentTimeMillis()): TransformOrigin =
        if (now - at <= FRESH_MS) TransformOrigin(x, y) else TransformOrigin.Center

    private const val FRESH_MS = 1_500L
}

/**
 * Notes where each finger comes down, for [TouchOrigin], without taking the touch from anything
 * under it: it only watches the first pass of the event and consumes nothing.
 */
internal fun Modifier.noteTouchOrigin(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.firstOrNull { it.changedToDown() }?.let { TouchOrigin.note(it.position, size) }
        }
    }
}

/**
 * How a page in a window of its own (a video's details, a show's page) opens and closes on a
 * touch screen: it grows from where the finger was ([TouchOrigin]) as it fades in over a dimming
 * screen, and on Back or its own close it shrinks a little towards there and fades before the
 * window goes. One continuous movement from the tile to its page and back, in place of the
 * window's stock pop. With less motion asked for, or on a television, the window opens and
 * closes as it always did.
 */
@Stable
internal class PageMotion(
    val animated: Boolean,
    private val origin: TransformOrigin,
    private val scope: CoroutineScope,
    private val dismiss: () -> Unit,
) {
    val progress = Animatable(if (animated) 0f else 1f)
    private var closing = false

    /** Closes the page: the fold back first, when there is one, then the window. */
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            if (!animated) {
                dismiss()
            } else {
                scope.launch {
                    progress.animateTo(0f, tween(Motion.Fast, easing = Motion.Standard))
                    dismiss()
                }
            }
        }
    }

    /** The page itself, inside the window's content, with its scrim behind it. */
    @Composable
    fun Frame(content: @Composable () -> Unit) {
        if (!animated) {
            content()
            return
        }
        // The window's own animation and dim would play under this one, so both are turned off
        // and the scrim below does the dimming, in step with the page.
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setWindowAnimations(0)
                window.setDimAmount(0f)
            }
        }
        LaunchedEffect(Unit) {
            if (!closing) progress.animateTo(1f, tween(Motion.Normal, easing = Motion.Standard))
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value }
                    .background(Color.Black.copy(alpha = SCRIM)),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        alpha = p
                        val scale = START_SCALE + (1f - START_SCALE) * p
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = origin
                    },
            ) { content() }
        }
    }

    private companion object {
        /** How small the page starts, from the tapped point: enough to read as growing, no more. */
        const val START_SCALE = 0.86f

        /** The dim over the screen behind while the page arrives. */
        const val SCRIM = 0.5f
    }
}

/** A [PageMotion] for a page that [onDismiss] takes away; animated only on a touch screen with motion on. */
@Composable
internal fun rememberPageMotion(onDismiss: () -> Unit): PageMotion {
    val dismiss by rememberUpdatedState(onDismiss)
    val animated = isTouch() && !rememberLessMotion()
    val scope = rememberCoroutineScope()
    return remember { PageMotion(animated, TouchOrigin.recent(), scope) { dismiss() } }
}
