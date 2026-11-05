package com.tmplayer.ui.browse

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Search all chats" from a place that has no search of its own to open: a chat whose listing
 * matched nothing, or the chat list's filter. The asking screen leaves the text here and goes back
 * to the browser, which opens its search (the phone's page, the television's rail page, the
 * desktop's palette) on that text and takes it, so it is asked once.
 */
object SearchRequest {
    private val _pending = MutableStateFlow<String?>(null)

    /** The text waiting to be searched for, or null. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun ask(text: String) {
        if (text.isNotBlank()) _pending.value = text.trim()
    }

    /** The waiting text, now no longer waiting. */
    fun take(): String? = _pending.value.also { _pending.value = null }
}
