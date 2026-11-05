package com.tmplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tmplayer.data.DeviceForm
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The picture over an empty page: [icon] large, in [tone], on a soft disc of the same colour. It
 * arrives once and then keeps still: the disc grows in on a spring with a little give, the glyph's
 * strokes draw themselves on from end to end while it floats up into place, and as the last stroke
 * closes one faint ring spreads from the disc and is gone. Nothing loops, so a page left open on a
 * television is not redrawn for nothing; with less motion asked for it is simply there.
 *
 * Every empty state on the phone, the tablet, the television and the desktop draws this one, so a
 * list with nothing in it looks the same everywhere. It is decoration only: no focus, no meaning
 * for a screen reader, which reads the words under it.
 *
 * @param size the disc; by default 80 dp on a phone, 88 on a desktop and 96 on a television.
 */
@Composable
fun EmptyArt(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = EmptyArtDefaults.size(),
    tone: Color = Tone.accent,
) {
    val still = rememberLessMotion()
    val arrive = remember(icon) { Animatable(if (still) 1f else 0f) }
    val stroke = remember(icon) { Animatable(if (still) 1f else 0f) }
    val ring = remember(icon) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(icon, still) {
        if (still) {
            arrive.snapTo(1f)
            stroke.snapTo(1f)
            ring.snapTo(1f)
            return@LaunchedEffect
        }
        coroutineScope {
            launch { arrive.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 260f)) }
            launch {
                // Waited on the frame clock, not with a delay, so the strokes keep time with the disc.
                stroke.animateTo(
                    1f,
                    tween(EmptyArtDefaults.DRAW_MS, delayMillis = EmptyArtDefaults.DRAW_DELAY_MS, easing = EmptyArtDefaults.DrawEasing),
                )
                ring.animateTo(1f, tween(EmptyArtDefaults.RING_MS, easing = LinearOutSlowInEasing))
            }
        }
    }
    val rise = with(LocalDensity.current) { EmptyArtDefaults.Float.toPx() }
    val parts = remember(icon) { GlyphPart.of(icon) }
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val p = arrive.value
                alpha = p.coerceIn(0f, 1f)
                val s = 0.8f + 0.2f * p
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                val r = this.size.minDimension / 2f
                drawCircle(tone.copy(alpha = 0.12f), radius = r)
                // The ring that spreads once the glyph is drawn; gone when it has finished.
                val q = ring.value
                if (q in 0.001f..0.999f) {
                    drawCircle(
                        tone.copy(alpha = 0.28f * (1f - q)),
                        radius = r * (1f + 0.3f * q),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            Modifier
                .size(size * 0.46f)
                .graphicsLayer { translationY = (1f - stroke.value) * rise }
                .drawBehind { drawGlyph(parts, icon, tone, stroke.value) },
        )
    }
}

/**
 * One path of a generated glyph, split at each move into the strokes a pen would draw one after
 * another, so each draws on along its own length rather than only the first of them.
 */
private class GlyphPart(val whole: Path, val strokes: List<Path>, val width: Float, val stroked: Boolean, val filled: Boolean) {
    companion object {
        fun of(icon: ImageVector): List<GlyphPart> = buildList {
            fun walk(group: VectorGroup) {
                for (node in group) when (node) {
                    is VectorGroup -> walk(node)
                    is VectorPath -> {
                        val runs = mutableListOf<MutableList<PathNode>>()
                        for (step in node.pathData) {
                            if (step is PathNode.MoveTo || runs.isEmpty()) runs += mutableListOf<PathNode>()
                            runs.last() += step
                        }
                        add(
                            GlyphPart(
                                whole = node.pathData.toPath(),
                                strokes = runs.map { it.toPath() },
                                width = node.strokeLineWidth,
                                stroked = node.stroke != null,
                                filled = node.fill != null,
                            ),
                        )
                    }
                }
            }
            walk(icon.root)
        }
    }
}

/**
 * [parts] in [tone], each stroke drawn on to [progress] of its length; a filled shape comes in as
 * the strokes close. Right to left it is mirrored, as the icon itself would be.
 */
private fun DrawScope.drawGlyph(parts: List<GlyphPart>, icon: ImageVector, tone: Color, progress: Float) {
    if (progress <= 0f) return
    val scale = size.width / icon.viewportWidth
    val mirror = icon.autoMirror && layoutDirection == LayoutDirection.Rtl
    // Round caps would put a dot at the start of every stroke before it has any length.
    val ink = (progress * 8f).coerceAtMost(1f)
    val measure = PathMeasure()
    val piece = Path()
    withTransform({
        if (mirror) scale(-1f, 1f, pivot = center)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        for (part in parts) {
            val line = Stroke(width = part.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (part.filled) drawPath(part.whole, tone, alpha = ((progress - 0.7f) / 0.3f).coerceIn(0f, 1f))
            if (!part.stroked) continue
            if (progress >= 1f) {
                drawPath(part.whole, tone, style = line)
                continue
            }
            for (run in part.strokes) {
                measure.setPath(run, false)
                piece.reset()
                if (measure.getSegment(0f, measure.length * progress, piece, true)) drawPath(piece, tone, alpha = ink, style = line)
            }
        }
    }
}

/** The figures [EmptyArt] is drawn and moved by. */
object EmptyArtDefaults {
    /** The disc on this device: larger on a television, read from across the room. */
    @Composable
    fun size(): Dp = when (deviceForm()) {
        DeviceForm.Tv -> 96.dp
        DeviceForm.Desktop -> 88.dp
        else -> 80.dp
    }

    /** How far the glyph floats up while it draws. */
    val Float = 6.dp

    /** The glyph waits for the disc to be half grown before its strokes start. */
    const val DRAW_DELAY_MS = 120

    /** The strokes drawing on, start to end. */
    const val DRAW_MS = 760

    /** The ring spreading from the disc once they have. */
    const val RING_MS = 700

    /** Slow at both ends, like a pen that sets down and lifts off. */
    val DrawEasing = CubicBezierEasing(0.55f, 0f, 0.25f, 1f)
}
