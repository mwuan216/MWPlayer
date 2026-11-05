package com.tmplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A row that belongs to the one above it and is only there while that one allows it, such as the
 * Pure black switch under the theme: it unfolds downward and fades in, and folds back the same
 * way, so the rows below move rather than jump. Under Reduce motion it is simply there or not.
 */
@Composable
fun RevealUnder(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Reduce motion: there or not, at once.
    if (rememberLessMotion()) {
        if (visible) Column(modifier) { content() }
        return
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column { content() }
    }
}
