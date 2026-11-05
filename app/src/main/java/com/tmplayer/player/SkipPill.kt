package com.tmplayer.player

import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.tmplayer.R
import com.tmplayer.i18n.L
import com.tmplayer.online.SkipKind

/**
 * The "Skip intro" pill: raised while the playhead is inside a stretch somebody has timed (a
 * recap, an intro or opening, credits, an ending, a preview), and gone once it leaves.
 *
 * It sits where the next-up card sits, bottom right and clear of the transport row, so it stays
 * where the eye left it whether the row is up or down; the two never show together, since a
 * stretch that runs to the end of an episode with a successor is the card's to cover.
 *
 * A phone taps it. A television never focuses it: focus moving to something that appeared on its
 * own would turn the next arrow press into a walk across the screen rather than the jump the
 * viewer meant. The activity hands it OK instead while the row is down, which the second line,
 * "OK to skip", says out loud.
 *
 * Built in code and added to [root] under [below], the overlay container, the same way as
 * [NextUpCard], so the gesture HUD and the phone's flashes still draw over it. The activity owns
 * when it shows and what pressing does.
 */
class SkipPill(
    root: FrameLayout,
    below: View,
    private val tv: Boolean,
    onSkip: () -> Unit,
) {
    private val context = root.context
    private val density = context.resources.displayMetrics.density
    private fun px(dp: Int) = (dp * density).toInt()

    private val label = TextView(context).apply {
        setTextColor(context.getColor(R.color.text_primary))
        textSize = if (tv) 18f else 15f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    /** The remote's half of the offer; a phone's pill is its own button and needs no instruction. */
    private val hint = TextView(context).apply {
        setTextColor(context.getColor(R.color.accent))
        textSize = 13f
        maxLines = 1
        text = L.playerSkipOkHint
        visibility = if (tv) View.VISIBLE else View.GONE
    }

    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = context.getDrawable(R.drawable.bg_player_chip)
        setPadding(px(22), px(if (tv) 12 else 11), px(22), px(if (tv) 10 else 11))
        visibility = View.GONE
        isFocusable = false
        if (tv) {
            isClickable = false
        } else {
            isClickable = true
            setOnClickListener { onSkip() }
        }
        addView(label)
        addView(hint)
    }

    init {
        root.addView(
            view,
            root.indexOfChild(below),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.BOTTOM,
            ).apply {
                rightMargin = NextUpCard.marginPx(root)
                bottomMargin = NextUpCard.bottomPx(root)
            },
        )
    }

    val isShown: Boolean get() = view.visibility == View.VISIBLE

    /** Writes the words for [kind] and shows the pill. */
    fun show(kind: SkipKind) {
        label.text = when (kind) {
            SkipKind.Recap -> L.playerSkipRecap
            SkipKind.Intro -> L.playerSkipIntro
            SkipKind.Opening -> L.playerSkipOpening
            SkipKind.Credits -> L.playerSkipCredits
            SkipKind.Ending -> L.playerSkipEnding
            SkipKind.Preview -> L.playerSkipPreview
        }
        view.visibility = View.VISIBLE
    }

    fun hide() {
        view.visibility = View.GONE
    }
}
