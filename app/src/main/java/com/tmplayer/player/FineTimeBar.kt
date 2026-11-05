package com.tmplayer.player

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar

/**
 * Media3's bar, with YouTube's fine scrubbing under a finger: drag along it as usual, and take the
 * finger up (or down) away from it to travel at a half and then a quarter of the speed
 * ([FineScrub]), so a few seconds can be found on a long video without letting go. [onRate] is told
 * the rate as it changes, and null when the finger lifts, for the hint over the bar.
 *
 * The bar is handed every move at its own height, so Media3's own fine scrub (a third, past 50 dp
 * above it) never stacks on top of this one; and at the position the [FineScrub.Tracker] has
 * reached rather than where the finger is. A remote's keys never come here.
 */
@UnstableApi
class FineTimeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : DefaultTimeBar(context, attrs, defStyleAttr) {

    var onRate: ((Float?) -> Unit)? = null

    private var tracker: FineScrub.Tracker? = null
    private var lastRate: Float? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return super.onTouchEvent(event)
        val density = resources.displayMetrics.density
        val middle = height / 2f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracker = FineScrub.Tracker(event.x)
                lastRate = null
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val t = tracker ?: return super.onTouchEvent(event)
                t.move(event.x, (event.y - middle) / density)
                t.clamp(0f, width.toFloat())
                if (event.actionMasked == MotionEvent.ACTION_MOVE) report(t.rate)
                val handled = passOn(event, t.x, middle)
                if (event.actionMasked == MotionEvent.ACTION_UP) finish()
                return handled
            }
            MotionEvent.ACTION_CANCEL -> {
                val handled = super.onTouchEvent(event)
                finish()
                return handled
            }
        }
        return super.onTouchEvent(event)
    }

    private fun passOn(event: MotionEvent, x: Float, y: Float): Boolean {
        val moved = MotionEvent.obtain(event)
        moved.setLocation(x, y)
        return try {
            super.onTouchEvent(moved)
        } finally {
            moved.recycle()
        }
    }

    private fun report(rate: Float) {
        if (rate == lastRate) return
        lastRate = rate
        onRate?.invoke(rate)
    }

    private fun finish() {
        tracker = null
        if (lastRate != null) onRate?.invoke(null)
        lastRate = null
    }
}
