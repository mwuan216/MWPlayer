package com.tmplayer.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.tmplayer.data.VideoDurationFilter
import com.tmplayer.data.VideoFilter
import com.tmplayer.data.VideoSort
import com.tmplayer.i18n.Messages
import com.tmplayer.ui.components.ChoiceGroup
import com.tmplayer.ui.components.GroupedChoice
import com.tmplayer.ui.components.GroupedChoicePopover
import com.tmplayer.ui.components.PopoverAnchor
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons

/** What [sort] is called in a chat's Sort menu, on every device. */
fun videoSortLabel(sort: VideoSort, s: Messages): String = when (sort) {
    VideoSort.Newest -> s.gridSortNewest
    VideoSort.Oldest -> s.gridSortOldest
    VideoSort.Name -> s.gridSortName
    VideoSort.NameDesc -> s.gridSortNameDesc
    VideoSort.Year -> s.gridSortYear
    VideoSort.YearAsc -> s.gridSortYearAsc
    VideoSort.Size -> s.gridSortSize
    VideoSort.SizeAsc -> s.gridSortSizeAsc
    VideoSort.Longest -> s.gridSortLongest
    VideoSort.Shortest -> s.gridSortShortest
    VideoSort.Unwatched -> s.gridSortUnwatched
    VideoSort.WatchedFirst -> s.gridSortWatched
}

/** The glyph beside [sort] in the menu. */
fun videoSortIcon(sort: VideoSort): ImageVector = when (sort) {
    VideoSort.Newest -> TmIcons.Clock
    VideoSort.Oldest -> TmIcons.History
    VideoSort.Name, VideoSort.NameDesc -> TmIcons.TextSize
    VideoSort.Year, VideoSort.YearAsc -> TmIcons.Trailer
    VideoSort.Size, VideoSort.SizeAsc -> TmIcons.Download
    VideoSort.Longest, VideoSort.Shortest -> TmIcons.Frames
    VideoSort.Unwatched, VideoSort.WatchedFirst -> TmIcons.Play
}

/** What [filter] is called in the Show group, and in the "Showing ..." line over the grid. */
fun videoFilterLabel(filter: VideoFilter, s: Messages): String = when (filter) {
    VideoFilter.All -> s.gridFilterAll
    VideoFilter.Unwatched -> s.gridFilterUnwatched
    VideoFilter.InProgress -> s.gridFilterInProgress
    VideoFilter.Watched -> s.gridFilterWatched
    VideoFilter.OnDevice -> s.gridFilterOnDevice
    VideoFilter.Shows -> s.gridFilterShows
    VideoFilter.Singles -> s.gridFilterSingles
    VideoFilter.HighDefinition -> s.gridFilterHd
}

/** The glyph beside [filter] in the menu. */
fun videoFilterIcon(filter: VideoFilter): ImageVector = when (filter) {
    VideoFilter.All -> TmIcons.Grid
    VideoFilter.Unwatched -> TmIcons.PlayCircle
    VideoFilter.InProgress -> TmIcons.Pause
    VideoFilter.Watched -> TmIcons.CheckCircle
    VideoFilter.OnDevice -> TmIcons.Folder
    VideoFilter.Shows -> TmIcons.Episodes
    VideoFilter.Singles -> TmIcons.PlaySolid
    VideoFilter.HighDefinition -> TmIcons.AspectRatio
}

/** What [duration] is called in the Duration group, and in the "Showing ..." line over the grid. */
fun videoDurationFilterLabel(duration: VideoDurationFilter, s: Messages): String = when (duration) {
    VideoDurationFilter.All -> s.gridDurationAll
    VideoDurationFilter.Under5Min -> s.gridDurationUnder5m
    VideoDurationFilter.Min5To20 -> s.gridDurationShort
    VideoDurationFilter.Min20To60 -> s.gridDurationMedium
    VideoDurationFilter.Over60Min -> s.gridDurationLong
}

/** The glyph beside [duration] in the menu. */
fun videoDurationFilterIcon(duration: VideoDurationFilter): ImageVector = when (duration) {
    VideoDurationFilter.All -> TmIcons.Grid
    VideoDurationFilter.Under5Min,
    VideoDurationFilter.Min5To20,
    VideoDurationFilter.Min20To60,
    VideoDurationFilter.Over60Min,
    -> TmIcons.Clock
}

/**
 * What a chat's Sort menu holds, the same on a phone, a television and a computer: the order
 * ([VideoSort]), which videos show ([VideoFilter]), and the duration filter ([VideoDurationFilter]).
 * Each pick applies at once.
 */
class VideoMenuState(
    val sort: VideoSort,
    val filter: VideoFilter,
    val durationFilter: VideoDurationFilter = VideoDurationFilter.All,
    val onSort: (VideoSort) -> Unit,
    val onFilter: (VideoFilter) -> Unit,
    val onDurationFilter: (VideoDurationFilter) -> Unit = {},
)

/** The menu's groups, as [GroupedChoicePopover] draws them and the television's sheet lists them. */
fun videoMenuGroups(state: VideoMenuState, s: Messages): List<ChoiceGroup> = buildList {
    add(
        ChoiceGroup(
            s.gridSortLabel,
            VideoSort.menuEntries(state.sort).map { sort ->
                GroupedChoice(videoSortLabel(sort, s), videoSortIcon(sort), sort == state.sort) { state.onSort(sort) }
            },
        ),
    )
    add(
        ChoiceGroup(
            s.gridFilterLabel,
            VideoFilter.entries.map { filter ->
                GroupedChoice(videoFilterLabel(filter, s), videoFilterIcon(filter), filter == state.filter) { state.onFilter(filter) }
            },
        ),
    )
    add(
        ChoiceGroup(
            s.gridDurationLabel,
            VideoDurationFilter.entries.map { duration ->
                GroupedChoice(
                    videoDurationFilterLabel(duration, s),
                    videoDurationFilterIcon(duration),
                    duration == state.durationFilter,
                ) { state.onDurationFilter(duration) }
            },
        ),
    )
}

/**
 * A chat's Sort and filter menu on a phone and a computer: one card grown out of the Sort button
 * ([GroupedChoicePopover]) with the order and the Show group. A pick applies and
 * closes the card, so the grid is seen to change under it.
 */
@Composable
fun VideoMenuPopover(open: Boolean, anchor: PopoverAnchor, state: VideoMenuState, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    GroupedChoicePopover(open = open, anchor = anchor, groups = videoMenuGroups(state, s), onDismiss = onDismiss)
}
