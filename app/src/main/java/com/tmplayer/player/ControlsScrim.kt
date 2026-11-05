package com.tmplayer.player

import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.PaintDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape

/**
 * The controls' two scrims on five stops each, eased so the fade has no line to see.
 *
 * A gradient in XML carries three stops at most, and two of them stacked meet in a seam: at a
 * fractional density their bounds round into one shared row, painted twice, a dark hairline
 * across the picture. One shader with every stop has neither problem, and GradientDrawable's own
 * multi-stop call needs API 29. bg_controls_scrim_top and bg_controls_scrim_bottom stay as the
 * layout's three-stop stand-ins, for the preview and for anything that inflates it bare.
 *
 * The figures are the desktop overlay's: 70 per cent black at the top edge, 80 at the bottom, and
 * 60 per cent or more at half height of the bottom one, where the times and the television's
 * title sit, so white text keeps AA contrast on a snow scene.
 */
internal object ControlsScrim {

    /** Top edge first, clear at the foot. */
    fun top(): Drawable = vertical(
        intArrayOf(black(0.70f), black(0.64f), black(0.45f), black(0.18f), 0),
        floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    )

    /** Clear at the top, darkest at the bottom edge. */
    fun bottom(): Drawable = vertical(
        intArrayOf(0, black(0.28f), black(0.60f), black(0.74f), black(0.80f)),
        floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    )

    private fun black(alpha: Float): Int = (alpha * 255 + 0.5f).toInt() shl 24

    private fun vertical(colors: IntArray, stops: FloatArray): Drawable = PaintDrawable().apply {
        shape = RectShape()
        shaderFactory = object : ShapeDrawable.ShaderFactory() {
            override fun resize(width: Int, height: Int): Shader =
                LinearGradient(0f, 0f, 0f, height.toFloat(), colors, stops, Shader.TileMode.CLAMP)
        }
    }
}
