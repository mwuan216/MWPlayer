package com.tmplayer.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tmplayer.data.Bookmark
import com.tmplayer.data.BookmarkStore
import com.tmplayer.i18n.Translator
import com.tmplayer.ui.components.ChoiceLine
import com.tmplayer.ui.components.ChoiceList
import com.tmplayer.ui.components.ChoiceSheet
import com.tmplayer.ui.components.FocusOnOpen
import com.tmplayer.ui.components.isTouch
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.Corner
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.flow.StateFlow

/*
 * The player's bookmarks on the phone and the television, in the pickers' panel ([ChoiceSheet])
 * as everything else the player asks is: the list, what can be done with one, and the note.
 */

private fun clock(ms: Long): String = Translator.messages.formatter.clock(ms)

/**
 * What the bookmark sheets can do, all of it the player's: the sheets only show the list and
 * report the choice. [bookmarks] is the video's list as the store has it, so the sheet follows an
 * add or a delete without being opened again; [position] is the moment playing, read when asked.
 */
class BookmarkSheetActions(
    val bookmarks: StateFlow<List<Bookmark>>,
    val position: () -> Long,
    val add: (positionMs: Long, note: String) -> Unit,
    val rename: (Bookmark, String) -> Unit,
    val delete: (Bookmark) -> Unit,
    val jump: (Bookmark) -> Unit,
)

/**
 * This video's bookmarks, earliest first, under a line that adds one more. Choosing a bookmark
 * opens what can be done with it ([BookmarkActionsSheet]) rather than playing straight away, so
 * the remote and the finger reach its note and Delete the same way.
 */
@Composable
internal fun BookmarksSheet(
    bookmarks: List<Bookmark>,
    onAdd: () -> Unit,
    onPick: (Bookmark) -> Unit,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
) {
    val s = LocalStrings.current
    val touch = isTouch()
    val focus = remember { FocusRequester() }
    ChoiceSheet(
        title = s.playerBookmarks,
        note = if (bookmarks.isEmpty()) s.playerBookmarksEmpty else null,
        onDismiss = onDismiss,
        onClose = onClose,
        ignoreRelease = !touch,
        fullScreen = true,
    ) {
        ChoiceList {
            ChoiceLine(
                title = s.playerAddBookmark,
                icon = TmIcons.Add,
                modifier = if (bookmarks.isEmpty()) Modifier.focusRequester(focus) else Modifier,
            ) { onAdd() }
            bookmarks.forEachIndexed { index, mark ->
                ChoiceLine(
                    title = clock(mark.positionMs),
                    detail = mark.note.ifBlank { null },
                    icon = TmIcons.Bookmark,
                    modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
                ) { onPick(mark) }
            }
        }
        FocusOnOpen(focus)
    }
}

/** One bookmark: play from it, write or change its note, or delete it. Back returns to the list. */
@Composable
internal fun BookmarkActionsSheet(
    mark: Bookmark,
    onPlay: () -> Unit,
    onNote: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
) {
    val s = LocalStrings.current
    val touch = isTouch()
    val focus = remember { FocusRequester() }
    ChoiceSheet(
        title = s.playerBookmarkNoteTitle(clock(mark.positionMs)),
        note = mark.note.ifBlank { null },
        onDismiss = onDismiss,
        onClose = onClose,
        ignoreRelease = !touch,
        fullScreen = true,
    ) {
        ChoiceList {
            ChoiceLine(s.playerBookmarkPlay, icon = TmIcons.Play, modifier = Modifier.focusRequester(focus)) { onPlay() }
            ChoiceLine(
                if (mark.note.isBlank()) s.playerBookmarkAddNote else s.playerBookmarkEditNote,
                icon = TmIcons.Edit,
            ) { onNote() }
            ChoiceLine(s.playerBookmarkDelete, icon = TmIcons.Delete) { onDelete() }
        }
        FocusOnOpen(focus)
    }
}

/**
 * A short note for a bookmark, typed into a single line with the keyboard up from the start, and
 * a line under it that saves. Done on the keyboard saves as well; an empty note is allowed.
 *
 * On a remote the field behaves as the search box does: while the keyboard is down the arrows
 * leave it, so focus can never be trapped in an empty line.
 */
@Composable
internal fun BookmarkNoteSheet(
    title: String,
    initial: String,
    saveLabel: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    /** The grey words in the empty field; a bookmark's by default. Renaming a video passes its own. */
    hint: String? = null,
    maxLength: Int = BookmarkStore.MAX_NOTE,
) {
    val s = LocalStrings.current
    val touch = isTouch()
    var note by remember { mutableStateOf(initial) }
    var focused by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    ChoiceSheet(title = title, onDismiss = onDismiss, onClose = onClose, ignoreRelease = !touch) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .heightIn(min = 52.dp)
                .background(Tone.surfaceHigh, RoundedCornerShape(Corner.Medium))
                .border(if (focused) 2.dp else 1.dp, if (focused) Tone.accent else Tone.outline, RoundedCornerShape(Corner.Medium))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (note.isEmpty()) {
                Text(hint ?: s.playerBookmarkNoteHint, style = MaterialTheme.typography.bodyLarge, color = Tone.muted)
            }
            BasicTextField(
                value = note,
                onValueChange = { note = it.take(maxLength) },
                singleLine = true,
                // BasicTextField does not read LocalContentColor; without a colour it paints black.
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Tone.text),
                cursorBrush = SolidColor(Tone.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    onSave(note)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(field)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        val direction = when (event.key) {
                            Key.DirectionUp -> FocusDirection.Up
                            Key.DirectionDown -> FocusDirection.Down
                            else -> null
                        }
                        if (direction != null && !touch) {
                            if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
                            true
                        } else {
                            false
                        }
                    },
            )
        }
        ChoiceList {
            ChoiceLine(saveLabel, icon = TmIcons.Check) {
                keyboard?.hide()
                onSave(note)
            }
        }
    }
    // Straight into the field with the keyboard up: asking for a note is asking to type.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { field.requestFocus() }
        keyboard?.show()
    }
}
