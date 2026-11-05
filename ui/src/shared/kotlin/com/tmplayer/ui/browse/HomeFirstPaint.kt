package com.tmplayer.ui.browse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether Home has drawn its first whole screen since the app started: its rows, and the
 * carousel's first picture when it has a carousel. Until then the brand loader stays over the
 * window (MainActivity on Android, Shell on the desktop), so a cold start goes from the loader straight to a finished Home
 * rather than a skeleton filling in piece by piece and the rows jumping down when the carousel
 * lands (user, 2026-10-19). [HOLD_MAX_MS] bounds the wait, so a slow chat never keeps the loader
 * up; whatever is still on its way then arrives under its own placeholders.
 *
 * Set once per process: coming back to Home later never brings the loader back.
 */
object HomeFirstPaint {
    var ready by mutableStateOf(false)
        private set

    fun markReady() {
        if (!ready) ready = true
    }

    /** The longest the loader waits for Home after Telegram is reached. */
    const val HOLD_MAX_MS = 6_000L
}
