package com.tmplayer.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tmplayer.data.CourseProgress
import com.tmplayer.data.MediaItem
import com.tmplayer.ui.components.isTouch
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.theme.Tone

/**
 * The line over a chat or a forum topic that is one course of study videos: its name, "12 of 40
 * watched", a thin bar, and a button to the first lesson not yet watched. The phone, the television
 * and the desktop each put it at the top of the listing; see [CourseProgress.of] for when a listing
 * counts as a course.
 *
 * [title] is the course's own name where its lessons give one, else the chat's or the topic's.
 */
@Composable
fun CourseHeader(
    progress: CourseProgress,
    title: String,
    onPlay: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val tv = !isTouch()
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                progress.course.ifBlank { title },
                style = if (tv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                color = Tone.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (progress.finished) s.seriesAllWatched else s.seriesProgress(progress.watched, progress.total),
                style = MaterialTheme.typography.labelMedium,
                color = if (progress.watched > 0) Tone.accent else Tone.muted,
                maxLines = 1,
            )
            ProgressLine(
                if (progress.total == 0) 0f else progress.watched.toFloat() / progress.total,
                Modifier.padding(top = 2.dp),
            )
        }
        val next = progress.next
        val code = progress.nextCode
        if (next != null && code != null) {
            PlayButton(
                label = when {
                    progress.nextStarted -> s.seriesResume(code)
                    progress.watched > 0 -> s.seriesContinue(code)
                    else -> s.seriesPlay(code)
                },
                onClick = { onPlay(next) },
            )
        }
    }
}
