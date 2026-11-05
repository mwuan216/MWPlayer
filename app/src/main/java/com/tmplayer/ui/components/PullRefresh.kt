package com.tmplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Material's pull to refresh, with two small answers to the hand: the list under it follows the
 * pull a little way (a quarter of the indicator's travel, so it reads as being drawn down, not
 * dragged), and it ticks once as the pull passes the point where letting go refreshes, the way
 * a switch clicks into place. The tick follows "Vibrate on tap"; with less motion asked for the
 * list stays where it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TouchPullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    /** Material's spinner at the top centre unless a page places its own (Home, under the status bar). */
    indicator: @Composable BoxScope.() -> Unit = {
        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    },
    content: @Composable BoxScope.() -> Unit,
) {
    val still = rememberLessMotion()
    val tick = rememberStepTick()
    val busy by rememberUpdatedState(isRefreshing)
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction >= 1f }
            .distinctUntilChanged()
            .collect { past -> if (past && !busy) tick() }
    }
    val follow = with(LocalDensity.current) { FOLLOW.toPx() }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = indicator,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!still) translationY = state.distanceFraction.coerceIn(0f, 1.5f) * follow
                },
            content = content,
        )
    }
}

/** How far the list follows a full pull. */
private val FOLLOW = 20.dp
