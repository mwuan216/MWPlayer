package com.tmplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.tmplayer.ui.theme.FloatingTone
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.floatingBorder

/**
 * Where a tooltip stands against the control it names: over it, under it (for a control near the
 * top of the screen), or past its end edge (a folded rail's item).
 */
enum class TipSide { Above, Below, End }

/**
 * The name of an icon-only control, shown where the viewer is looking for it: on a hover on the
 * desktop, on focus on a television, on a long press on a phone. [IconTip] (one per platform, in
 * androidMain and jvmMain) picks the trigger; this is the words, in the floating look, with the
 * key that does the same beside them where there is one.
 *
 * It fades in over [Motion.Fast], and appears at once under reduced motion.
 */
@Composable
fun TipBubble(label: String, shortcut: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .tipFadeIn()
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(FloatingTone.dialog)
            .border(floatingBorder(), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Tone.text, maxLines = 1)
        if (shortcut != null) Text(shortcut, style = MaterialTheme.typography.labelMedium, color = Tone.muted, maxLines = 1)
    }
}

/**
 * A tooltip's fade as it arrives: from clear to solid over [Motion.Fast], or solid at once under
 * reduced motion. For a tooltip whose host shows and hides it without an animation of its own.
 */
@Composable
fun Modifier.tipFadeIn(): Modifier {
    val still = rememberLessMotion()
    val alpha = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(still) {
        if (still) alpha.snapTo(1f) else alpha.animateTo(1f, tween(Motion.Fast, easing = Motion.Standard))
    }
    return graphicsLayer { this.alpha = alpha.value }
}

/**
 * The television's tooltip: [label] (and [badge], a count or a version, where there is one) in a
 * pill beside the control the remote is on, on the [side] given. It never takes focus, so the
 * remote stays where it was, and it fades in (sliding a little from the control, so the eye
 * follows it there) and fades out. Reduced motion keeps the fade and drops the slide.
 *
 * Place it inside the box of the control it names: the popup is anchored to its parent.
 */
@Composable
fun FocusTip(visible: Boolean, label: String, badge: String? = null, side: TipSide = TipSide.Below) {
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = visible
    if (!shown.currentState && !shown.targetState && shown.isIdle) return
    val gap = with(LocalDensity.current) { (if (side == TipSide.End) FOCUS_TIP_SIDE_GAP else FOCUS_TIP_GAP).roundToPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Popup(
        popupPositionProvider = remember(gap, rtl, side) { FocusTipPosition(side, gap, rtl) },
        properties = PopupProperties(focusable = false),
    ) {
        val still = rememberLessMotion()
        val slide = when {
            still -> EnterTransition.None
            side == TipSide.End -> slideInHorizontally(Motion.standard(Motion.Fast)) { if (rtl) it / 4 else -it / 4 }
            side == TipSide.Below -> slideInVertically(Motion.standard(Motion.Fast)) { -it / 4 }
            else -> slideInVertically(Motion.standard(Motion.Fast)) { it / 4 }
        }
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(tween(if (still) 0 else Motion.Fast)) + slide,
            exit = fadeOut(tween(if (still) 0 else Motion.Fast)),
        ) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Tone.surfaceHigh)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = Tone.text, maxLines = 1)
                if (badge != null) Text(badge, style = MaterialTheme.typography.bodyMedium, color = Tone.muted, maxLines = 1)
            }
        }
    }
}

/**
 * [content] with a [FocusTip] naming it while focus is anywhere inside it: for a control that
 * does not show its own name. [modifier] is the control's place in its parent (an `align`, a
 * padding), since the tip's box is what the parent lays out.
 */
@Composable
fun FocusTipBox(
    label: String,
    modifier: Modifier = Modifier,
    side: TipSide = TipSide.Below,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.onFocusChanged { focused = it.hasFocus }) {
        content()
        FocusTip(visible = focused, label = label, side = side)
    }
}

/**
 * Centred over or under the anchor and kept inside the window, flipped to the other side when
 * there is no room; or level with it past its end edge.
 */
private class FocusTipPosition(private val side: TipSide, private val gap: Int, private val rtl: Boolean) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        if (side == TipSide.End) {
            val x = if (rtl) anchorBounds.left - gap - popupContentSize.width else anchorBounds.right + gap
            return IntOffset(x, anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2)
        }
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2).coerceIn(0, maxX)
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - popupContentSize.height
        val y = when (side) {
            TipSide.Below -> if (below + popupContentSize.height > windowSize.height && above >= 0) above else below
            else -> if (above < 0) below else above
        }
        return IntOffset(x, y)
    }
}

private val FOCUS_TIP_GAP = 10.dp
private val FOCUS_TIP_SIDE_GAP = 20.dp
