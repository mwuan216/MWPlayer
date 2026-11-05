package com.tmplayer.ui.browse

import com.tmplayer.ui.theme.collectIsFocusVisibleAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tmplayer.data.ForumTopicSummary
import com.tmplayer.ui.components.isTouch
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.focusRing

/**
 * The topics of a group that has Telegram's topics turned on, as a row of tabs over its videos:
 * "All topics" first, then the topics in Telegram's own order, pinned ones marked.
 *
 * Drawn like the season tabs so a viewer who has met one has met both. Unlike those, the remote
 * does not switch topic just by passing over one: each topic is a fresh search of the group, and
 * walking from the first tab to the eighth should not set off seven of them. OK picks.
 *
 * The chosen tab is scrolled into view when the row appears, so a group remembered on its
 * twentieth topic does not open with the chosen one off the edge of the screen. Coming back up
 * into the row from the grid lands on the tab last left, which is the chosen one after a pick.
 */
@Composable
fun TopicTabs(
    bar: TopicBar,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** The screen's side margin, so the first tab lines up with the first tile. */
    edge: Dp = 0.dp,
) {
    val s = LocalStrings.current
    val tv = !isTouch()
    val entries = remember(bar.topics) { listOf<ForumTopicSummary?>(null) + bar.topics }
    val chosenAt = entries.indexOfFirst { (it?.id ?: 0) == bar.selected }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (chosenAt - 1).coerceAtLeast(0))
    val chosenFocus = remember { FocusRequester() }
    LaunchedEffect(chosenAt) { listState.animateScrollToItem((chosenAt - 1).coerceAtLeast(0)) }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = s.gridTopicsLabel }
            .then(if (tv) Modifier.focusRestorer(chosenFocus) else Modifier),
        contentPadding = PaddingValues(horizontal = edge),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(entries, key = { _, topic -> topic?.id ?: 0 }) { index, topic ->
            TopicTab(
                topic = topic,
                chosen = index == chosenAt,
                tv = tv,
                onClick = { onSelect(topic?.id ?: 0) },
                modifier = if (index == chosenAt) Modifier.focusRequester(chosenFocus) else Modifier,
            )
        }
    }
}

@Composable
private fun TopicTab(
    topic: ForumTopicSummary?,
    chosen: Boolean,
    tv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusVisibleAsState()
    // Quiet pills: the plain surface at rest, and the focus fill for the chosen topic and for the
    // one under the remote. The two are told apart by the ring, which only focus draws, and by the
    // chosen one's weight.
    val shape = CircleShape
    val lit = focused || chosen
    val fill = if (lit) Tone.focusFill else Tone.surface
    val ink = if (lit) Tone.onFocusFill else Tone.text
    val name = topic?.name ?: s.gridTopicsAll
    val described = buildList {
        add(name)
        if (topic?.isPinned == true) add(s.gridTopicPinned)
        if (topic?.isClosed == true) add(s.gridTopicClosed)
        if ((topic?.unreadCount ?: 0) > 0) add(s.gridTopicUnread(topic!!.unreadCount))
    }.joinToString(", ")
    Row(
        modifier
            .heightIn(min = if (tv) TV_HEIGHT else TOUCH_HEIGHT)
            .clip(shape)
            .background(fill)
            .focusRing(focused, shape)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = chosen
                contentDescription = described
            }
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .padding(horizontal = if (tv) 18.dp else 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val glyph = if (tv) 18.dp else 16.dp
        if (topic?.isPinned == true) {
            Icon(TmIcons.Pin, contentDescription = null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(glyph))
        }
        Text(
            name,
            style = if (tv) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // A topic called a whole sentence still leaves room for its neighbours.
            modifier = Modifier.widthIn(max = if (tv) 260.dp else 200.dp),
        )
        if (topic?.isClosed == true) {
            Icon(TmIcons.Lock, contentDescription = null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(glyph))
        }
        val unread = topic?.unreadCount ?: 0
        if (unread > 0) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (lit) Tone.onFocusFill.copy(alpha = 0.2f) else Tone.accent.copy(alpha = 0.22f))
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            ) {
                Text(
                    if (unread > 999) "999+" else unread.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ink,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A topic pill's height: a touch target's on a phone, and the size of the TV's other pills. */
private val TOUCH_HEIGHT = 36.dp
private val TV_HEIGHT = 44.dp
