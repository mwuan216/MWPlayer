package com.tmplayer.ui.browse

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.tmplayer.ui.components.rememberLessMotion
import com.tmplayer.ui.theme.Motion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The one pill behind a row of [TouchFilterChip]s that slides from the chip left to the chip
 * chosen, rather than one chip's fill going out as another's comes on: the row shows where the
 * choice went. The chosen chip reports where it is ([place]); the row ([ChipSliderBox]) draws the
 * pill there, sprung across, its width following the chip's.
 */
@Stable
internal class ChipSlider {
    private var box: LayoutCoordinates? = null
    private var chip: LayoutCoordinates? = null
    private var owner: Any? = null

    /** The chosen chip's bounds within the row, or null while none is chosen. */
    var target by mutableStateOf<Rect?>(null)
        private set

    fun attach(row: LayoutCoordinates) {
        box = row
        update()
    }

    fun place(token: Any, coordinates: LayoutCoordinates) {
        owner = token
        chip = coordinates
        update()
    }

    fun release(token: Any) {
        if (owner !== token) return
        owner = null
        chip = null
        target = null
    }

    private fun update() {
        val row = box ?: return
        val at = chip ?: return
        if (!row.isAttached || !at.isAttached) return
        target = row.localBoundingBoxOf(at, clipBounds = false)
    }
}

internal val LocalChipSlider = staticCompositionLocalOf<ChipSlider?> { null }

/**
 * Reports this chip's place to the row's [ChipSlider] while [selected], and lets it go when the
 * choice moves on or the chip leaves. Nothing outside a [ChipSliderBox].
 */
@Composable
internal fun Modifier.chipSlot(selected: Boolean): Modifier {
    val slider = LocalChipSlider.current ?: return this
    val token = remember { Any() }
    DisposableEffect(slider, selected) { onDispose { slider.release(token) } }
    return if (selected) onGloballyPositioned { slider.place(token, it) } else this
}

/**
 * A row of chips with the sliding pill behind them. [content] is the row itself; it goes inside
 * whatever scrolls, so the pill scrolls with the chips.
 */
@Composable
internal fun ChipSliderBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val slider = remember { ChipSlider() }
    val still = rememberLessMotion()
    val x = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    val shown = remember { Animatable(0f) }
    var middle by remember { mutableStateOf(0f) }
    LaunchedEffect(slider, still) {
        var placed = false
        snapshotFlow { slider.target }.collectLatest { rect ->
            if (rect == null) {
                placed = false
                shown.animateTo(0f, Motion.standard(Motion.Fast))
                return@collectLatest
            }
            middle = rect.center.y
            if (!placed || still) {
                // The first chip chosen (or any, with less motion): the pill is simply there.
                placed = true
                x.snapTo(rect.left)
                width.snapTo(rect.width)
                shown.animateTo(1f, Motion.standard(Motion.Fast))
            } else {
                coroutineScope {
                    launch { x.animateTo(rect.left, SLIDE) }
                    launch { width.animateTo(rect.width, SLIDE) }
                    launch { shown.animateTo(1f, Motion.standard(Motion.Fast)) }
                }
            }
        }
    }
    val fill = MaterialTheme.colorScheme.secondaryContainer
    Box(
        modifier
            .onGloballyPositioned { slider.attach(it) }
            .drawBehind {
                val alpha = shown.value
                if (alpha <= 0f || width.value <= 0f) return@drawBehind
                // The chip's own height, not its touch target's, centred where the chip is.
                val height = FilterChipDefaults.Height.toPx()
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(x.value, middle - height / 2),
                    size = Size(width.value, height),
                    cornerRadius = CornerRadius(height / 2),
                    alpha = alpha,
                )
            },
    ) {
        CompositionLocalProvider(LocalChipSlider provides slider) { content() }
    }
}

/** A touch of give as the pill arrives, settled quickly. */
private val SLIDE = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
