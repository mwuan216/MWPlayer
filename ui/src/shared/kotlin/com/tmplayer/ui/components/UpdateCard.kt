package com.tmplayer.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.tmplayer.i18n.L
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.launch

/** How far a download has come, as the update card draws it. */
sealed interface UpdateCardProgress {
    data object None : UpdateCardProgress

    /** Under way, with no length to measure it against: the moving bar. */
    data object Unknown : UpdateCardProgress

    data class Part(val fraction: Float) : UpdateCardProgress
}

/**
 * The update popup's card, the same on the phone, the TV and the desktop: the logo and "TMPlayer
 * 1.28.0 is out" with the version installed under it, then What's new as one point per change in
 * a box of its own that scrolls, then how the update reaches this device (or how far it has got),
 * then [actions].
 *
 * The list is the one part that grows with the release, so it is the one part that gives way: it
 * stops at [listMax] and shrinks further when the window is short, while the header and the
 * buttons always stay in sight. When there is more than fits, the edge with more behind it fades
 * and a thin thumb on the side says how much. On a remote the box takes focus, Down and Up scroll
 * it, and at the end Down moves on to the buttons as usual.
 *
 * [split] is for a window too short to stack it all (a phone on its side): the header and the
 * list take the start side at the full height, and the install line and the buttons the end side,
 * so the list never shrinks to nothing under the rest.
 *
 * Laid out with the caller's width: the phone and the desktop put it in a dialog, the TV in its
 * floating window. [tv] sets the larger type a television is read with from the sofa.
 */
@Composable
fun UpdateCard(
    title: String,
    installed: String?,
    points: List<String>,
    status: String?,
    modifier: Modifier = Modifier,
    statusIsError: Boolean = false,
    progress: UpdateCardProgress = UpdateCardProgress.None,
    tv: Boolean = false,
    split: Boolean = false,
    listMax: Dp = if (tv) 300.dp else 320.dp,
    actions: @Composable ColumnScope.() -> Unit,
) {
    val pad = if (tv) 32.dp else 24.dp
    if (split) {
        Row(modifier.padding(pad)) {
            Column(Modifier.weight(1f)) {
                CardHeader(title, installed, tv)
                WhatsNew(points, tv, Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.width(SPLIT_SIDE)) {
                CardStatus(status, statusIsError, progress, tv)
                Spacer(Modifier.height(20.dp).weight(1f, fill = false))
                actions()
            }
        }
        return
    }
    Column(modifier.padding(pad)) {
        CardHeader(title, installed, tv)
        WhatsNew(points, tv, Modifier.weight(1f, fill = false).heightIn(max = listMax))
        if (!status.isNullOrBlank() || progress != UpdateCardProgress.None) {
            Spacer(Modifier.height(if (tv) 20.dp else 16.dp))
            CardStatus(status, statusIsError, progress, tv)
        }
        Spacer(Modifier.height(if (tv) 28.dp else 24.dp))
        actions()
    }
}

/** The end side's width in [UpdateCard]'s split layout: the stacked buttons at a thumb's width. */
private val SPLIT_SIDE = 280.dp

@Composable
private fun CardHeader(title: String, installed: String?, tv: Boolean) {
    val type = MaterialTheme.typography
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoMark(if (tv) 48.dp else 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = if (tv) type.headlineSmall else type.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Tone.text,
            )
            if (!installed.isNullOrBlank()) {
                Text(installed, style = if (tv) type.bodyLarge else type.bodyMedium, color = Tone.muted)
            }
        }
    }
}

/** The heading and the list, or nothing for a release with no notes. [listModifier] sizes the list. */
@Composable
private fun ColumnScope.WhatsNew(points: List<String>, tv: Boolean, listModifier: Modifier) {
    if (points.isEmpty()) return
    val type = MaterialTheme.typography
    Spacer(Modifier.height(if (tv) 24.dp else 20.dp))
    Text(
        L.settingsWhatsNew,
        style = if (tv) type.titleMedium else type.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = Tone.text,
    )
    Spacer(Modifier.height(10.dp))
    ChangeList(points, tv = tv, modifier = listModifier)
}

/** How the update reaches this device, or how far it has got, with the bar under it. */
@Composable
private fun CardStatus(status: String?, statusIsError: Boolean, progress: UpdateCardProgress, tv: Boolean) {
    if (status.isNullOrBlank() && progress == UpdateCardProgress.None) return
    val type = MaterialTheme.typography
    Column(Modifier.animateContentSize(Motion.standard()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!status.isNullOrBlank()) {
            Text(
                status,
                style = if (tv) type.bodyLarge else type.bodyMedium,
                color = if (statusIsError) Tone.danger else Tone.muted,
            )
        }
        when (progress) {
            UpdateCardProgress.None -> Unit
            UpdateCardProgress.Unknown -> LinearProgressIndicator(
                Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = Tone.accent,
                trackColor = Tone.track,
                gapSize = 0.dp,
            )
            is UpdateCardProgress.Part -> LinearProgressIndicator(
                progress = { progress.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = Tone.accent,
                trackColor = Tone.track,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

/**
 * One point per change, in a tinted box that scrolls. A change that opens with a short name and a
 * colon ("Player: chapters, ...") has the name in bold, so the list can be skimmed by area.
 */
@Composable
private fun ChangeList(points: List<String>, tv: Boolean, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scrolls = scroll.maxValue > 0
    val shape = RoundedCornerShape(16.dp)
    val fade = Tone.text
    val thumb = Tone.muted.copy(alpha = 0.45f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val step = with(LocalDensity.current) { (if (tv) 120.dp else 80.dp).toPx() }
    val text = if (tv) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
    val accent = Tone.accent

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tone.surface)
            .border(if (focused) 2.dp else 1.dp, if (focused) Tone.accent else Tone.outline, shape)
            // A remote has no wheel: Down and Up move the list while there is more of it that
            // way, and only then let focus leave for the buttons or the header.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> scroll.canScrollForward.also { more ->
                        if (more) scope.launch { scroll.animateScrollBy(step, Motion.standard()) }
                    }
                    Key.DirectionUp -> scroll.canScrollBackward.also { more ->
                        if (more) scope.launch { scroll.animateScrollBy(-step, Motion.standard()) }
                    }
                    else -> false
                }
            }
            .focusable(enabled = scrolls, interactionSource = interaction),
    ) {
        Column(
            Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // The edge with more behind it melts away, so a cut line reads as "there is more".
                    val edge = 28.dp.toPx().coerceAtMost(size.height / 3f)
                    if (scroll.canScrollBackward) {
                        drawRect(
                            Brush.verticalGradient(0f to Color.Transparent, 1f to fade, startY = 0f, endY = edge),
                            size = Size(size.width, edge),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    if (scroll.canScrollForward) {
                        drawRect(
                            Brush.verticalGradient(0f to fade, 1f to Color.Transparent, startY = size.height - edge, endY = size.height),
                            topLeft = Offset(0f, size.height - edge),
                            size = Size(size.width, edge),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }
                .verticalScroll(scroll)
                .padding(horizontal = if (tv) 20.dp else 16.dp, vertical = if (tv) 16.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (tv) 12.dp else 10.dp),
        ) {
            points.forEach { point -> ChangePoint(point, text, accent) }
        }

        // How much of the list is in sight, and where: a thumb on the end side, only while it scrolls.
        if (scrolls) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawWithContent {
                        val viewport = size.height
                        val content = viewport + scroll.maxValue
                        val inset = 6.dp.toPx()
                        val track = viewport - inset * 2
                        val length = (track * viewport / content).coerceAtLeast(24.dp.toPx())
                        val top = inset + (track - length) * (scroll.value.toFloat() / scroll.maxValue)
                        val width = 3.dp.toPx()
                        val x = if (rtl) inset else size.width - inset - width
                        drawRoundRect(thumb, Offset(x, top), Size(width, length), CornerRadius(width / 2))
                    },
            )
        }
    }
}

@Composable
private fun ChangePoint(point: String, style: TextStyle, accent: Color) {
    val lead = point.substringBefore(": ", missingDelimiterValue = "").takeIf { it.isNotEmpty() && it.length <= LEAD_MAX }
    Row {
        // The dot sits on the first line's middle, whatever the text size.
        val lineHeight = with(LocalDensity.current) { (if (style.lineHeight.isSpecified) style.lineHeight else style.fontSize * 1.4f).toDp() }
        Box(Modifier.height(lineHeight), contentAlignment = Alignment.Center) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            buildAnnotatedString {
                if (lead != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Tone.text)) { append("$lead: ") }
                    append(point.removePrefix("$lead: "))
                } else {
                    append(point)
                }
            },
            style = style,
            color = Tone.text.copy(alpha = 0.86f),
        )
    }
}

/** A colon later than this is part of the sentence, not a name for the area it is about. */
private const val LEAD_MAX = 24
