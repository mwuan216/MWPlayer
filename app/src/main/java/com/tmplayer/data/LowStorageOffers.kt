package com.tmplayer.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Switch to Low storage mode?", asked from wherever a video was refused for want of room.
 *
 * A download is refused in the queueing code, which has no screen of its own to ask on, so it
 * leaves the question here and the main screen asks it. Asked every time, by the viewer's wish,
 * not once per install.
 */
object LowStorageOffers {

    /**
     * One question. [keep] is true when the viewer asked to keep the video (a download), which
     * still needs room for all of it: the offer is to stream it instead.
     */
    data class Ask(val item: MediaItem, val chatTitle: String, val keep: Boolean)

    private val _pending = MutableStateFlow<Ask?>(null)
    val pending: StateFlow<Ask?> = _pending.asStateFlow()

    fun ask(ask: Ask) {
        _pending.value = ask
    }

    /** Takes the question off the queue, once it is on screen. */
    fun take(): Ask? = _pending.value.also { _pending.value = null }
}
