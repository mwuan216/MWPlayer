package com.tmplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tmplayer.ui.icons.FilledIcon
import com.tmplayer.ui.icons.TmIcons
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * A bar's or a rail's icon that fills in when its place is chosen, as Telegram's and WhatsApp's
 * tabs do: a drop lands (the glyph dips and springs back a touch past its size) and the colour
 * rises from the foot of the outline to the top on a wave that calms as it fills, with the inner
 * detail (a door, a speech bubble's lines) left cut out of it. Letting go of the place fades the
 * fill back to the outline, quicker than it came.
 *
 * The filled glyph is [TmIcons.selectedOf] the outline; an icon with no twin keeps its outline and
 * only takes the colour. The first draw does not animate, only a change does, and with less
 * motion asked for ([rememberLessMotion]) it lands at once. Mirrored icons stay mirrored, since
 * both glyphs are drawn through their own painters in this layout's direction.
 *
 * @param tint the outline's colour, which the caller animates as it already did.
 * @param fill the colour the glyph fills with; the tint by default, so both land in the accent.
 */
@Composable
fun SelectableIcon(
    icon: ImageVector,
    selected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    fill: Color = tint,
    filled: FilledIcon? = TmIcons.selectedOf(icon),
) {
    val reduced = rememberLessMotion()
    val outline = rememberVectorPainter(icon)
    val body = filled?.let { rememberVectorPainter(it.body) }
    val cut = filled?.cut?.let { rememberVectorPainter(it) }
    // How far the fill has risen, and how strongly it shows: the second lets it fade on the way out
    // without draining back down.
    val level = remember { Animatable(if (selected) 1f else 0f) }
    val shown = remember { Animatable(if (selected) 1f else 0f) }
    val scale = remember { Animatable(1f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(selected, reduced) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        if (reduced || body == null) {
            level.snapTo(if (selected) 1f else 0f)
            shown.snapTo(if (selected) 1f else 0f)
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        if (selected) {
            coroutineScope {
                shown.snapTo(1f)
                level.snapTo(0f)
                launch { level.animateTo(1f, tween(Drop.RISE_MS, easing = Drop.RiseEasing)) }
                launch {
                    scale.animateTo(Drop.DIP, tween(Drop.DIP_MS))
                    scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium))
                }
            }
        } else {
            scale.snapTo(1f)
            shown.animateTo(0f, tween(Drop.DRAIN_MS))
            level.snapTo(0f)
        }
    }
    Spacer(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .drawBehind {
                val tinted = ColorFilter.tint(tint)
                val a = shown.value
                val l = level.value
                if (body == null || a <= 0f || l <= 0f) {
                    draw(outline, tinted)
                    return@drawBehind
                }
                val liquid = liquid(this.size, l)
                // Above the surface the outline as it was; under it the outline fades out as the
                // fill fades in, so a fill that is leaving never leaves a hole behind it.
                clipPath(liquid, ClipOp.Difference) { draw(outline, tinted) }
                clipPath(liquid) {
                    if (a < 1f) draw(outline, tinted, alpha = 1f - a)
                    val bounds = Rect(Offset.Zero, this.size)
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(bounds, Paint().apply { alpha = a })
                        draw(body, ColorFilter.tint(fill))
                        if (cut != null) {
                            // The detail is knocked out of the fill, not painted over it, so it
                            // reads in the bar's own colour, whatever that is.
                            canvas.saveLayer(bounds, Paint().apply { blendMode = BlendMode.DstOut })
                            draw(cut, ColorFilter.tint(Color.Black))
                            canvas.restore()
                        }
                        canvas.restore()
                    }
                }
            },
    )
}

private fun DrawScope.draw(painter: VectorPainter, filter: ColorFilter, alpha: Float = 1f) {
    with(painter) { draw(this@draw.size, alpha = alpha, colorFilter = filter) }
}

/**
 * The filled part of the glyph at [level] (0 empty, 1 full): everything under a surface that rises
 * from below the foot to above the top, waving gently while it moves and flat as it lands.
 */
private fun liquid(size: Size, level: Float): Path {
    val unit = size.width / 24f
    val swell = Drop.SWELL * unit * (1f - level)
    val top = size.height + swell - level * (size.height + 2f * swell + unit)
    val phase = level * Drop.WAVES * 2f * PI.toFloat()
    val left = -2f * unit
    val right = size.width + 2f * unit
    val steps = 16
    return Path().apply {
        moveTo(left, size.height + 2f * unit)
        for (i in 0..steps) {
            val x = left + (right - left) * i / steps
            val y = top + swell * sin(x / size.width * 2f * PI.toFloat() * 1.2f + phase)
            lineTo(x, y)
        }
        lineTo(right, size.height + 2f * unit)
        close()
    }
}

/** The figures of [SelectableIcon]'s drop. */
private object Drop {
    /** The fill rising from the foot to the top. */
    const val RISE_MS = 360

    /** How far the glyph dips as the drop lands, before it springs back. */
    const val DIP = 0.86f
    const val DIP_MS = 90

    /** The fill fading back to the outline when the place is left. */
    const val DRAIN_MS = 140

    /** How high the surface's wave stands at the start, in units of the 24 unit grid. */
    const val SWELL = 1.4f

    /** How many times the wave rolls over on the way up. */
    const val WAVES = 1.25f

    /** Off the foot briskly, slowing as it reaches the top, like water finding its level. */
    val RiseEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
}
